package com.example.song.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var httpDataSourceFactory: OkHttpDataSource.Factory
    private val resolvingIds = mutableSetOf<String>()
    private var currentQueue: List<Song> = emptyList()
    private val recoveryRetries = mutableMapOf<String, Int>()
    
    private var consecutiveFailures = 0

    companion object {
        private const val CHANNEL_ID = "pulse_music_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "PulseDebug"
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
        
        // 🛡️ Critical Fix: Do NOT call setUserAgent() on the factory.
        // Doing so locks the factory to a single User-Agent and ignores 
        // headers set via setDefaultRequestProperties.
        httpDataSourceFactory = OkHttpDataSource.Factory(app.okHttpClient)
        
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val currentPos = player.currentPosition
                    if (currentPos > 1000) {
                        Log.d(TAG, "Song ended naturally. Skipping to next.")
                        skipToNext()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                val uriString = mediaItem.localConfiguration?.uri?.toString() ?: ""
                Log.d(TAG, "onMediaItemTransition: mediaId=${mediaItem.mediaId}, uri=$uriString")
                
                if (uriString.contains("pulse_placeholder:")) {
                    resolveYoutubeUrl(mediaItem)
                }
                resolveNearbyItems()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val currentItem = player.currentMediaItem
                val uriString = currentItem?.localConfiguration?.uri?.toString() ?: ""
                
                if (uriString.contains("pulse_placeholder:")) {
                    Log.d(TAG, "onPlayerError caught Malformed URL. Triggering auto-recovery.")
                    player.pause()
                    serviceScope.launch(Dispatchers.Main) {
                        resolveYoutubeUrl(currentItem!!, force = false)
                    }
                    return
                }

                PulseLogger.log("Player error: ${error.errorCodeName}", isError = true)
                val mediaId = currentItem?.mediaId ?: "unknown"
                val retryCount = recoveryRetries.getOrDefault(mediaId, 0)

                if (retryCount < 1 && (
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)) {
                    
                    currentItem?.let {
                        recoveryRetries[mediaId] = retryCount + 1
                        serviceScope.launch(Dispatchers.Main) {
                            resolveYoutubeUrl(it, force = false)
                        }
                    }
                }
            }
        })

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Forcing yt-dlp binary update...")
                com.yausername.youtubedl_android.YoutubeDL.getInstance().updateYoutubeDL(applicationContext, com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.STABLE)
                Log.d(TAG, "yt-dlp update check finished.")
            } catch (_: Exception) {}
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_GET_METADATA)
                .build()
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("PLAY_QUEUE", Bundle.EMPTY))
                .add(SessionCommand("SKIP_TO_NEXT", Bundle.EMPTY))
                .add(SessionCommand("SKIP_TO_PREVIOUS", Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "PLAY_QUEUE" -> {
                    val index = args.getInt("index", 0)
                    val ids = args.getIntegerArrayList("ids") ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    val isStreaming = args.getBoolean("isStreaming", false)
                    
                    serviceScope.launch {
                        val repository = SongApplication.getInstance().repository
                        val songs = if (isStreaming) {
                            repository.getStreamingItemsByIdsSync(ids).map { it.toSong() }
                        } else {
                            repository.getSongsByIdsSync(ids)
                        }
                        
                        if (songs.isNotEmpty()) {
                            Log.d(TAG, "Queue mapping started for ${songs.size} items")
                            val dummyMediaItems = songs.map { song ->
                                val query = "ytsearch1:${song.title} ${song.artist}"
                                val metadata = MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setExtras(Bundle().apply { 
                                        putString("custom_artwork_url", song.imageUrl)
                                        putString("search_query", query)
                                    })
                                    .build()
                                
                                MediaItem.Builder()
                                    .setMediaId(song.id.toString())
                                    .setUri("pulse_placeholder:$query")
                                    .setMediaMetadata(metadata)
                                    .build()
                            }
                            Log.d(TAG, "Queue mapping finished.")
                            
                            currentQueue = songs
                            withContext(Dispatchers.Main) {
                                player.setMediaItems(dummyMediaItems, index, 0L)
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "SKIP_TO_NEXT" -> {
                    skipToNext()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "SKIP_TO_PREVIOUS" -> {
                    skipToPrevious()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
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

    private fun resolveYoutubeUrl(mediaItem: MediaItem, force: Boolean = false) {
        if (!force && resolvingIds.contains(mediaItem.mediaId)) return
        resolvingIds.add(mediaItem.mediaId)

        serviceScope.launch(Dispatchers.Main) {
            try {
                val isActiveTrack = player.currentMediaItem?.mediaId == mediaItem.mediaId
                
                withContext(Dispatchers.IO) {
                    if (isActiveTrack) {
                        Log.d(TAG, "Active track requesting Mutex: ${mediaItem.mediaMetadata.title}")
                    } else {
                        Log.d(TAG, "Background track delaying 1.5s: ${mediaItem.mediaMetadata.title}")
                        delay(1500)
                    }

                    val currentUri = mediaItem.localConfiguration?.uri.toString()
                    if (!force && currentUri.contains("googlevideo.com")) return@withContext

                    val query = mediaItem.mediaMetadata.extras?.getString("search_query") 
                        ?: currentUri.substringAfter("pulse_placeholder:")
                        
                    val cleanQuery = query.replace("official audio", "", ignoreCase = true).trim()
                    
                    val info = YoutubeStreamHandler.ytDlMutex.withLock {
                        try {
                            val processId = UUID.randomUUID().toString()
                            Log.d(TAG, "Burner thread launched for extraction.")
                            
                            val burnerJob = CoroutineScope(Dispatchers.IO).async {
                                try {
                                    YoutubeStreamHandler.getStreamInfo(cleanQuery, processId)
                                } catch (_: Exception) { null }
                            }

                            val result = withTimeoutOrNull(35000L) {
                                burnerJob.await()
                            }

                            if (result == null) {
                                Log.d(TAG, "FATAL: Extraction timed out after 35s. Abandoning zombie thread and releasing Mutex for: $cleanQuery")
                                com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(processId)
                                burnerJob.cancel()
                                
                                if (isActiveTrack) {
                                    consecutiveFailures++
                                    if (consecutiveFailures >= 10) {
                                        Log.e(TAG, "Circuit Breaker Tripped. Extraction Engine Offline.")
                                        withContext(Dispatchers.Main) {
                                            player.pause()
                                            resolvingIds.clear()
                                            Toast.makeText(this@MusicService, "Extraction Engine Offline. Check Network.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Log.d(TAG, "Extraction failed for ACTIVE track. Auto-skipping to next song.")
                                        withContext(Dispatchers.Main) {
                                            player.seekToNext()
                                            player.prepare()
                                            player.play()
                                        }
                                    }
                                } else {
                                    Log.d(TAG, "Extraction failed or timed out for background track. Abandoning silently.")
                                }
                                null
                            } else {
                                consecutiveFailures = 0
                                Log.d(TAG, "Extraction SUCCESS for: $cleanQuery")
                                Log.d(TAG, "Extraction successful. Switching to Main thread for ExoPlayer injection.")
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "Main thread acquired. Executing replaceMediaItem for: ${mediaItem.mediaMetadata.title}")
                                    
                                    // 🛡️ Triple-Lock Header Injection
                                    val headers = result.headers.toMutableMap()
                                    if (!headers.containsKey("Referer")) {
                                        headers["Referer"] = "https://www.youtube.com/watch?v=${result.videoId ?: ""}"
                                    }
                                    httpDataSourceFactory.setDefaultRequestProperties(headers)
                                    
                                    val updatedItem = mediaItem.buildUpon()
                                        .setUri(result.url)
                                        .setCustomCacheKey(mediaItem.mediaId)
                                        .setMediaMetadata(mediaItem.mediaMetadata)
                                        .build()

                                    for (i in 0 until player.mediaItemCount) {
                                        if (player.getMediaItemAt(i).mediaId == mediaItem.mediaId) {
                                            Log.d(TAG, "Executing replaceMediaItem at index $i")
                                            player.replaceMediaItem(i, updatedItem)

                                            if (player.currentMediaItemIndex == i) {
                                                Log.d(TAG, "Active track jumpstarted. Calling prepare() and play().")
                                                player.prepare()
                                                player.play()
                                            }
                                            resolveNearbyItems()
                                            break
                                        }
                                    }
                                }
                                result
                            }
                        } finally {
                            Log.d(TAG, "Lock released for: $cleanQuery")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Resolution failed: ${e.message}")
            } finally {
                resolvingIds.remove(mediaItem.mediaId)
            }
        }
    }

    private fun skipToNext() {
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex < player.mediaItemCount) {
            player.seekToDefaultPosition(nextIndex)
        }
    }

    private fun skipToPrevious() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }
        val prevIndex = player.currentMediaItemIndex - 1
        if (prevIndex >= 0) {
            player.seekToDefaultPosition(prevIndex)
        }
    }

    private fun resolveNearbyItems() {
        serviceScope.launch(Dispatchers.Main) {
            val currentIndex = player.currentMediaItemIndex
            val totalItems = player.mediaItemCount
            val indices = listOf(currentIndex + 1, currentIndex + 2, currentIndex - 1)
            for (i in indices) {
                if (i in 0 until totalItems) {
                    val item = player.getMediaItemAt(i)
                    if (item.localConfiguration?.uri?.toString()?.contains("pulse_placeholder:") == true) {
                        val query = item.mediaMetadata.extras?.getString("search_query") 
                            ?: item.localConfiguration?.uri?.toString()?.substringAfter("pulse_placeholder:") ?: "unknown"
                        Log.d(TAG, "Background track delaying 1.5s before requesting Mutex: $query")
                        resolveYoutubeUrl(item)
                    }
                }
            }
        }
    }

    private fun com.example.song.data.model.StreamingItem.toSong(): Song {
        return Song(id = id, title = title, artist = artist ?: "Unknown Artist", audioUri = youtubeUrl, imageUrl = thumbnailUrl, duration = duration)
    }

    private fun Song.toMediaItem(): MediaItem {
        val query = "ytsearch1:$title $artist"
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri("pulse_placeholder:$query")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setExtras(Bundle().apply { 
                    putString("custom_artwork_url", imageUrl)
                    putString("search_query", query)
                })
                .build())
            .build()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pulse Music", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        player.release()
        mediaSession?.release()
        super.onDestroy()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
}
