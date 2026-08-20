package com.example.song.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import androidx.media3.session.SessionResult
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
    
    // 🛡️ Principal Guard: Track the current active request to prevent ghost hijacking
    private var primaryResolutionJob: Job? = null
    private var activeSongId: String? = null
    
    // 🛡️ Loop Protection: Track auto-recovery attempts per track
    private val recoveryRetries = mutableMapOf<String, Int>()

    companion object {
        private const val CHANNEL_ID = "pulse_music_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        val loadingNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Pulse Music")
            .setContentText("Initializing player...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        startForeground(NOTIFICATION_ID, loadingNotification)

        val app = SongApplication.getInstance()
        val cache = app.playerCache
        
        // Base factory with standard mobile User-Agent
        val baseUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
            
        val httpDataSourceFactory = OkHttpDataSource.Factory(app.okHttpClient)
            .setUserAgent(baseUserAgent)
        
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(dataSourceFactory)
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

                // 🛡️ Auto-Advance Fix: If a song finishes playing, manually trigger next
                if (playbackState == Player.STATE_ENDED) {
                    val currentPos = player.currentPosition
                    if (currentPos > 1000) {
                        android.util.Log.d("MusicService", "Song ended naturally at $currentPos. Ready for next.")
                    } else {
                        android.util.Log.d("MusicService", "Player stopped or interrupted at $currentPos. Ignoring auto-skip.")
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let {
                    if (isYoutubePlaceholder(it)) {
                        android.util.Log.d("MusicService", "Hard Intercept: Blocking placeholder from ExoPlayer.")
                        
                        // Update active state to this new placeholder track
                        activeSongId = it.mediaId
                        primaryResolutionJob?.cancel()
                        
                        player.stop() 
                        player.clearMediaItems() // Ensure total cleanup
                        
                        resolveAndInjectRealStream(it, isPrimary = true)
                    } else {
                        resolveNearbyItems()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val currentItem = player.currentMediaItem
                
                // Task 1: Ignore errors from placeholder URLs
                if (currentItem != null && isYoutubePlaceholder(currentItem)) {
                    android.util.Log.d("MusicService", "Ignoring expected error from placeholder URL.")
                    player.stop()
                    return
                }

                PulseLogger.log("Player error: ${error.errorCodeName} - ${error.localizedMessage}", isError = true)
                
                val mediaId = currentItem?.mediaId ?: "unknown"
                val retryCount = recoveryRetries.getOrDefault(mediaId, 0)

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
                            
                            // If this was the active song, treat recovery as primary
                            val isCurrentActive = activeSongId == mediaId
                            resolveAndInjectRealStream(it, force = true, isPrimary = isCurrentActive)
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
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_GET_METADATA)
                .build()
            
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("RESOLVE_AND_PLAY", Bundle.EMPTY))
                .build()
            
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == "RESOLVE_AND_PLAY") {
                val songId = args.getInt("id")
                val title = args.getString("title") ?: ""
                val artist = args.getString("artist") ?: ""
                val uri = args.getString("uri") ?: ""
                val image = args.getString("image")
                val song = Song(songId, title, artist, uri, image)
                
                playResolvedSong(song)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val settableFuture = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val repository = SongApplication.getInstance().repository
                val firstSong = repository.getFirstSongSync()
                if (firstSong != null) {
                    settableFuture.set(MediaSession.MediaItemsWithStartPosition(listOf(firstSong.toMediaItem()), 0, 0L))
                } else {
                    settableFuture.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            return settableFuture
        }
    }

    private fun playResolvedSong(song: Song) {
        val songId = song.id.toString()
        activeSongId = songId
        
        // 1. Always stop any previous background primary job
        primaryResolutionJob?.cancel()

        // 2. HARD INTERCEPT: If it's a placeholder or search, DO NOT touch ExoPlayer with dummy URL.
        if (song.audioUri.contains("pulse.music/placeholder") || song.audioUri.startsWith("yt")) {
            android.util.Log.d("MusicService", "Hard Intercept: Blocking dummy URL. Stopping player.")
            
            // Forcibly clear player to ensure old audio stops immediately
            player.stop()
            player.clearMediaItems()
            
            primaryResolutionJob = serviceScope.launch {
                // Background resolution phase
                resolveAndInjectRealStream(song.toMediaItem(), isPrimary = true)
            }
            return
        }

        // 3. Normal Playback (Reached only for local files or already direct links)
        val mediaItem = song.toMediaItem()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun resolveAndInjectRealStream(mediaItem: MediaItem, force: Boolean = false, isPrimary: Boolean = false) {
        val mediaId = mediaItem.mediaId
        
        // If this is a secondary background pre-fetch, use the existing map logic
        if (!isPrimary) {
            if (!force && activeResolutionJobs.containsKey(mediaId)) return
            activeResolutionJobs[mediaId]?.cancel()
        }

        val resolutionTask = serviceScope.launch {
            try {
                val uriString = mediaItem.localConfiguration?.uri.toString()
                val youtubeUrl = mediaItem.mediaMetadata.extras?.getString("youtube_url") ?: uriString

                android.util.Log.d("MusicService", "Resolving URL: $youtubeUrl")
                
                var streamInfo: com.example.song.data.model.StreamInfo? = null
                var retryCount = 0
                val maxRetries = 2

                while (streamInfo == null && retryCount <= maxRetries && isActive) {
                    if (retryCount > 0) delay(2000L * retryCount)
                    streamInfo = YoutubeStreamHandler.getStreamInfo(youtubeUrl)
                    retryCount++
                }
                
                // 🛡️ State Guard: Verify this song is still the active one before injecting
                if (isPrimary && activeSongId != mediaId) {
                    android.util.Log.w("MusicService", "Resolution finished late for $mediaId, but active song is $activeSongId. Aborting.")
                    return@launch
                }

                if (streamInfo != null && streamInfo.url.startsWith("http")) {
                    withContext(Dispatchers.Main) {
                        if (!isActive) return@withContext

                        var targetIndex = -1
                        for (idx in 0 until player.mediaItemCount) {
                            if (player.getMediaItemAt(idx).mediaId == mediaId) {
                                targetIndex = idx
                                break
                            }
                        }

                        val app = SongApplication.getInstance()
                        val headers = streamInfo.headers.toMutableMap()
                        val actualUserAgent = headers["User-Agent"] ?: headers["user-agent"] ?: headers["User-agent"]
                            ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
                        
                        headers.remove("User-Agent")
                        headers.remove("user-agent")
                        headers.remove("User-agent")
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

                        if (targetIndex != -1) {
                            player.addMediaSource(targetIndex, mediaSource)
                            player.removeMediaItem(targetIndex + 1)
                            if (player.currentMediaItemIndex == targetIndex) {
                                player.prepare()
                                player.play()
                            }
                        } else if (isPrimary && activeSongId == mediaId) {
                            // Fresh start for primary request
                            player.setMediaSource(mediaSource)
                            player.prepare()
                            player.play()
                        }
                        PulseLogger.log("Handshake successful for ${mediaItem.mediaMetadata.title}")
                    }
                } else {
                    PulseLogger.log("Extraction failed for ${mediaItem.mediaMetadata.title}", isError = true)
                }
            } catch (e: Exception) {
                PulseLogger.log("Resolution crashed: ${e.message}", isError = true)
            } finally {
                if (isPrimary) {
                    if (activeSongId == mediaId) primaryResolutionJob = null
                } else {
                    activeResolutionJobs.remove(mediaId)
                }
            }
        }

        if (isPrimary) {
            primaryResolutionJob = resolutionTask
        } else {
            activeResolutionJobs[mediaId] = resolutionTask
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val uri = if (audioUri.startsWith("content://") || audioUri.startsWith("http")) {
            Uri.parse(audioUri)
        } else {
            Uri.fromFile(java.io.File(audioUri))
        }

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(imageUrl?.let { Uri.parse(it) })
                    .setExtras(Bundle().apply { putString("youtube_url", audioUri) })
                    .build()
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pulse Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for music playback"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
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
        val nextIndex = currentIndex + 1
        if (nextIndex < totalItems) {
            val item = player.getMediaItemAt(nextIndex)
            if (isYoutubePlaceholder(item)) {
                resolveAndInjectRealStream(item)
            }
        }
    }
}
