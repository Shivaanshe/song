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
import com.example.song.util.YoutubeStreamHandler
import kotlinx.coroutines.*

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
                    val uriString = it.localConfiguration?.uri.toString()
                    // Only resolve if it's a remote YouTube URL (http/https) and not already a resolved googlevideo URL
                    val isYoutubeRemote = (uriString.startsWith("http") && 
                        (uriString.contains("youtube.com") || uriString.contains("youtu.be"))) && 
                        !uriString.contains("googlevideo.com")
                    
                    if (isYoutubeRemote) {
                        resolveYoutubeUrl(it)
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

    private fun resolveYoutubeUrl(mediaItem: MediaItem) {
        serviceScope.launch {
            val youtubeUrl = mediaItem.mediaMetadata.extras?.getString("youtube_url") 
                ?: mediaItem.localConfiguration?.uri.toString()

            val directUrl = YoutubeStreamHandler.getDirectAudioUrl(youtubeUrl)
            
            if (directUrl != null) {
                // Find index of this item
                for (i in 0 until player.mediaItemCount) {
                    if (player.getMediaItemAt(i).mediaId == mediaItem.mediaId) {
                        val updatedItem = mediaItem.buildUpon()
                            .setUri(directUrl)
                            .build()
                        
                        player.replaceMediaItem(i, updatedItem)
                        if (player.currentMediaItemIndex == i) {
                            player.prepare()
                            player.play()
                        }
                        break
                    }
                }
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
