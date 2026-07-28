package com.example.song.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.song.MainActivity
import com.example.song.SongApplication
import com.example.song.util.PulseLogger
import com.example.song.util.YoutubeStreamHandler
import kotlinx.coroutines.*

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Track which items are currently being resolved to avoid duplicate work
    private val resolvingIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        
        val cache = SongApplication.getInstance().playerCache
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        
        // Use CacheDataSource only for HTTP(S) requests
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // DefaultDataSource will handle file://, content://, etc.
        // For other schemes (like http), it will fall back to cacheDataSourceFactory
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let {
                    // Resolve current item if needed
                    if (isYoutubePlaceholder(it)) {
                        resolveYoutubeUrl(it)
                    }
                    
                    // Proactively resolve nearby items in the queue
                    resolveNearbyItems()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                PulseLogger.log("Player error: ${error.errorCodeName} - ${error.localizedMessage}", isError = true)
                // If we hit a source error, try to re-resolve the current item
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED) {
                    
                    player.currentMediaItem?.let {
                        // Only force re-resolve if it's a YouTube-sourced item
                        val uriString = it.localConfiguration?.uri.toString()
                        val isYoutubeSource = uriString.contains("youtube.com") || 
                                              uriString.contains("youtu.be") || 
                                              uriString.contains("googlevideo.com")
                        
                        if (isYoutubeSource) {
                            android.util.Log.d("MusicService", "Source error detected for YouTube track. Attempting auto-recovery...")
                            resolveYoutubeUrl(it, force = true)
                        }
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun isYoutubePlaceholder(mediaItem: MediaItem): Boolean {
        val uriString = mediaItem.localConfiguration?.uri.toString()
        // Standard YouTube links
        val isStandardPlaceholder = (uriString.startsWith("http") && 
                (uriString.contains("youtube.com") || uriString.contains("youtu.be"))) && 
                !uriString.contains("googlevideo.com")
        
        // Spotify bridged search links
        val isSearchPlaceholder = uriString.startsWith("ytsearch")
        
        return isStandardPlaceholder || isSearchPlaceholder
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

                val youtubeUrl = mediaItem.mediaMetadata.extras?.getString("youtube_url") 
                    ?: mediaItem.localConfiguration?.uri.toString()

                android.util.Log.d("MusicService", "Resolving placeholder URL: $youtubeUrl")
                val directUrl = YoutubeStreamHandler.getDirectAudioUrl(youtubeUrl)
                
                if (directUrl != null) {
                    // Find index of this item in the current playlist
                    for (i in 0 until player.mediaItemCount) {
                        if (player.getMediaItemAt(i).mediaId == mediaItem.mediaId) {
                            val updatedItem = mediaItem.buildUpon()
                                .setUri(directUrl)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
