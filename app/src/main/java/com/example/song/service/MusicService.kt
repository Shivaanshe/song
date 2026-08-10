package com.example.song.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
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

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Track which items are currently being resolved to avoid duplicate work
    private val resolvingIds = mutableSetOf<String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Essential for Samsung Quick Panel and Playback Resumption
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        
        val app = SongApplication.getInstance()
        val cache = app.playerCache
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build()
        
        player.addListener(object : Player.Listener {
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
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED) {
                    
                    player.currentMediaItem?.let {
                        val uriString = it.localConfiguration?.uri.toString()
                        val isYoutubeSource = uriString.contains("youtube.com") || 
                                              uriString.contains("youtu.be") || 
                                              uriString.contains("googlevideo.com") ||
                                              uriString.startsWith("pulse_placeholder:")
                        
                        if (isYoutubeSource) {
                            android.util.Log.d("MusicService", "Source error detected for YouTube track. Attempting auto-recovery...")
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
                .build()
            
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
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
                    settableFuture.set(MediaSession.MediaItemsWithStartPosition(listOf(mediaItem), 0, 0L))
                } else {
                    settableFuture.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            
            return settableFuture
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
        return uriString.startsWith("pulse_placeholder:")
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
        if (!force && resolvingIds.contains(mediaItem.mediaId)) return
        
        resolvingIds.add(mediaItem.mediaId)
        
        serviceScope.launch {
            try {
                // If it's a direct URL already and we're not forcing, skip
                val currentUri = mediaItem.localConfiguration?.uri.toString()
                if (!force && currentUri.contains("googlevideo.com")) {
                    return@launch
                }

                val uriString = mediaItem.localConfiguration?.uri.toString()
                val youtubeUrl = if (uriString.startsWith("pulse_placeholder:")) {
                    uriString.substringAfter("pulse_placeholder:")
                } else {
                    mediaItem.mediaMetadata.extras?.getString("youtube_url") ?: uriString
                }

                android.util.Log.d("MusicService", "Resolving placeholder URL: $youtubeUrl")
                val directUrl = YoutubeStreamHandler.getDirectAudioUrl(youtubeUrl)
                
                if (directUrl != null) {
                    // Find index of this item in the current playlist
                    for (i in 0 until player.mediaItemCount) {
                        if (player.getMediaItemAt(i).mediaId == mediaItem.mediaId) {
                            val updatedItem = mediaItem.buildUpon()
                                .setUri(directUrl)
                                .setMediaMetadata(mediaItem.mediaMetadata) // Explicitly lock original metadata
                                .build()
                            
                            player.replaceMediaItem(i, updatedItem)
                            
                            // If this was the current item and we are in an error state or IDLE,
                            // we need to re-prepare.
                            if (player.currentMediaItemIndex == i && 
                                (player.playbackState == Player.STATE_IDLE || player.playerError != null)) {
                                player.prepare()
                                player.play()
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                resolvingIds.remove(mediaItem.mediaId)
            }
        }
    }
}
