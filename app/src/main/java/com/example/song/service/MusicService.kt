package com.example.song.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import com.example.song.MainActivity
import com.example.song.SongApplication
import com.example.song.data.model.Song
import com.example.song.util.PulseLogger
import com.example.song.util.YoutubeStreamHandler
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Track active resolution jobs to allow cancellation of stale ones
    private val activeResolutionJobs = mutableMapOf<String, Job>()
    
    // 🛡️ Loop Protection: Track auto-recovery attempts per track
    private val recoveryRetries = mutableMapOf<String, Int>()

    companion object {
        private const val CHANNEL_ID = "pulse_music_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Essential for Samsung Quick Panel and Playback Resumption
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        
        // Task: Fix ForegroundServiceDidNotStartInTimeException
        // We must call startForeground immediately to satisfy Android's 5s requirement,
        // especially since yt-dlp extraction can be slow.
        createNotificationChannel()
        val loadingNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play) // Use a system icon for now
            .setContentTitle("Pulse Music")
            .setContentText("Initializing player...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        startForeground(NOTIFICATION_ID, loadingNotification)

        val app = SongApplication.getInstance()
        val cache = app.playerCache
        
        // Task: Use OkHttp for base factory with a standard mobile User-Agent
        val baseUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
            
        val httpDataSourceFactory = OkHttpDataSource.Factory(app.okHttpClient)
            .setUserAgent(baseUserAgent)
        
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when(playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                android.util.Log.d("MusicService", "Player State Changed: $stateName")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let {
                    if (isYoutubePlaceholder(it)) {
                        resolveYoutubeUrl(it)
                    }
                    resolveNearbyItems()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                PulseLogger.log("Player error: ${error.errorCodeName} - ${error.localizedMessage}", isError = true)
                
                val currentItem = player.currentMediaItem
                val mediaId = currentItem?.mediaId ?: "unknown"
                val retryCount = recoveryRetries.getOrDefault(mediaId, 0)

                // 🛡️ Loop Protection: If it's a 403 and we've already tried to recover once, stop and notify user.
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && 
                    (error.localizedMessage?.contains("403") == true || error.localizedMessage?.contains("Forbidden") == true)) {
                    
                    if (retryCount >= 1) {
                        PulseLogger.log("Fatal 403 error for $mediaId. Aborting to prevent loop.", isError = true)
                        serviceScope.launch {
                            Toast.makeText(this@MusicService, "YouTube Access Denied (403). Try another song.", Toast.LENGTH_LONG).show()
                        }
                        player.pause()
                        return
                    }
                }

                // Generic recovery for network or IO errors (limit to 1 retry)
                if (retryCount < 1 && (
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED)) {
                    
                    currentItem?.let {
                        val uriString = it.localConfiguration?.uri.toString()
                        val isYoutubeSource = uriString.contains("youtube.com") || 
                                              uriString.contains("youtu.be") || 
                                              uriString.contains("googlevideo.com") ||
                                              uriString == "https://pulse.music/placeholder"
                        
                        if (isYoutubeSource) {
                            android.util.Log.d("MusicService", "Source error detected. Attempting recovery 1/1...")
                            recoveryRetries[mediaId] = retryCount + 1
                            resolveYoutubeUrl(it, force = true)
                        }
                    }
                }
            }
        })

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Support all default session and player commands to ensure OS integration
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_GET_METADATA)
                .build()
            
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
                .build()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            android.util.Log.d("MusicService", "Playback resumption requested by system")
            
            val settableFuture = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            
            serviceScope.launch {
                val repository = SongApplication.getInstance().repository
                val firstSong = repository.getFirstSongSync()
                
                if (firstSong != null) {
                    val mediaItem = firstSong.toMediaItem()
                    android.util.Log.d("MusicService", "Resuming with: ${firstSong.title}")
                    settableFuture.set(MediaSession.MediaItemsWithStartPosition(listOf(mediaItem), 0, 0L))
                } else {
                    settableFuture.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            
            return settableFuture
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            android.util.Log.d("MusicService", "Media button event: ${intent.action}")
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val uri = if (audioUri.startsWith("content://") || audioUri.startsWith("http")) {
            android.net.Uri.parse(audioUri)
        } else {
            android.net.Uri.fromFile(java.io.File(audioUri))
        }

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(imageUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun createNotificationChannel() {
        val name = "Pulse Music Playback"
        val descriptionText = "Controls for music playback"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    private fun isYoutubePlaceholder(mediaItem: MediaItem): Boolean {
        val uriString = mediaItem.localConfiguration?.uri.toString()
        return uriString == "https://pulse.music/placeholder"
    }

    private fun resolveNearbyItems() {
        val currentIndex = player.currentMediaItemIndex
        val totalItems = player.mediaItemCount
        if (totalItems == 0) return

        // Items to resolve: Current + 1, Current + 2, and Previous (Current - 1)
        val indicesToResolve = listOf(currentIndex + 1, currentIndex + 2, currentIndex - 1)
        
        for (index in indicesToResolve) {
            // Safe boundary check
            if (index in 0 until totalItems) {
                val item = player.getMediaItemAt(index)
                if (isYoutubePlaceholder(item)) {
                    resolveYoutubeUrl(item)
                }
            }
        }
    }

    private fun resolveYoutubeUrl(mediaItem: MediaItem, force: Boolean = false) {
        val mediaId = mediaItem.mediaId
        
        // 🛡️ Cancel existing resolution for this same track if one is already running
        if (!force && activeResolutionJobs.containsKey(mediaId)) {
            android.util.Log.d("MusicService", "Resolution already in progress for $mediaId. Skipping.")
            return
        }
        activeResolutionJobs[mediaId]?.cancel()

        activeResolutionJobs[mediaId] = serviceScope.launch {
            try {
                // If it's a direct URL already and we're not forcing, skip
                val currentUri = mediaItem.localConfiguration?.uri.toString()
                if (!force && currentUri.contains("googlevideo.com")) {
                    return@launch
                }

                val uriString = mediaItem.localConfiguration?.uri.toString()
                val youtubeUrl = mediaItem.mediaMetadata.extras?.getString("youtube_url") ?: uriString

                android.util.Log.d("MusicService", "Resolving URL: $youtubeUrl")
                
                // --- Retry Logic with Backoff ---
                var streamInfo: com.example.song.data.model.StreamInfo? = null
                var retryCount = 0
                val maxRetries = 2

                while (streamInfo == null && retryCount <= maxRetries && isActive) {
                    if (retryCount > 0) {
                        PulseLogger.log("Retry $retryCount for $youtubeUrl...")
                        delay(2000L * retryCount)
                    }
                    streamInfo = YoutubeStreamHandler.getStreamInfo(youtubeUrl)
                    retryCount++
                }
                
                if (streamInfo != null && streamInfo.url.startsWith("http")) {
                    withContext(Dispatchers.Main) {
                        if (!isActive) return@withContext

                        // 🛡️ Double-Injection Guard: Check if the item has already been resolved by another job
                        var targetIndex = -1
                        for (idx in 0 until player.mediaItemCount) {
                            val itemInPlayer = player.getMediaItemAt(idx)
                            if (itemInPlayer.mediaId == mediaId) {
                                // If the item at this index is NOT a placeholder, it means it was already resolved
                                if (!isYoutubePlaceholder(itemInPlayer)) {
                                    android.util.Log.d("MusicService", "Track $mediaId already resolved at index $idx. Skipping injection.")
                                    return@withContext
                                }
                                targetIndex = idx
                                break
                            }
                        }

                        if (targetIndex != -1) {
                            android.util.Log.d("MusicService", "Resolution Success. Injecting URL at index $targetIndex")
                            
                            val app = SongApplication.getInstance()
                            
                            // 🕵️ Principal Fix: Strict Header, User-Agent & Cookie Injection for OkHttp
                            val headers = streamInfo.headers.toMutableMap()
                            
                            // Extract the ACTUAL User-Agent yt-dlp used for the specific player client
                            val actualUserAgent = headers["User-Agent"] 
                                ?: headers["user-agent"]
                                ?: headers["User-agent"]
                                ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
                            
                            // Remove User-Agent from the map to avoid override conflicts in setDefaultRequestProperties
                            headers.remove("User-Agent")
                            headers.remove("user-agent")
                            headers.remove("User-agent")

                            // 🛡️ Bypassing 403: Force the Referer to the actual video page
                            headers["Referer"] = "https://www.youtube.com/watch?v=${streamInfo.videoId ?: mediaId}"
                            headers["Origin"] = "https://www.youtube.com"
                            
                            val httpFactory = OkHttpDataSource.Factory(app.okHttpClient)
                                .setUserAgent(actualUserAgent)
                                .setDefaultRequestProperties(headers)
                            
                            val cacheFactory = CacheDataSource.Factory()
                                .setCache(app.playerCache)
                                .setUpstreamDataSourceFactory(httpFactory)
                                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

                            val mediaItemWithHeaders = mediaItem.buildUpon()
                                .setUri(streamInfo.url)
                                .setCustomCacheKey(streamInfo.videoId ?: mediaId)
                                .build()
                            
                            val mediaSource = ProgressiveMediaSource.Factory(cacheFactory)
                                .createMediaSource(mediaItemWithHeaders)
                            
                            android.util.Log.d("MusicService", "Handshake: Injecting at index $targetIndex with Triple-Lock Headers")
                            PulseLogger.log("Handshake index $targetIndex with Stealth Sync")
                            
                            // Atomically swap the source
                            player.addMediaSource(targetIndex, mediaSource)
                            player.removeMediaItem(targetIndex + 1)
                            
                            if (player.currentMediaItemIndex == targetIndex) {
                                // 🛡️ Important: ONLY prepare if the player is in an error or idle state
                                if (player.playbackState == Player.STATE_IDLE || player.playerError != null) {
                                    player.prepare()
                                }
                                player.play()
                            }
                        }
                    }
                } else {
                    PulseLogger.log("Extraction failed: URL is null for $youtubeUrl", isError = true)
                    
                    withContext(Dispatchers.Main) {
                        for (i in 0 until player.mediaItemCount) {
                            if (player.getMediaItemAt(i).mediaId == mediaId) {
                                PulseLogger.log("Skipping track: Resolution failed for ${mediaItem.mediaMetadata.title}", isError = true)
                                if (player.currentMediaItemIndex == i) {
                                    player.seekToNext()
                                }
                                player.removeMediaItem(i)
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                PulseLogger.log("Resolution crashed: ${e.message}", isError = true)
            } finally {
                activeResolutionJobs.remove(mediaId)
            }
        }
    }
}
