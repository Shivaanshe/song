package com.example.song.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.song.MainActivity
import com.example.song.SongApplication
import com.example.song.data.model.Song
import com.example.song.util.PulseLogger
import com.example.song.util.YoutubeStreamHandler
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.UUID

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var currentQueue: List<Song> = emptyList()

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
            .setContentText("Initializing engine...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        startForeground(NOTIFICATION_ID, loadingNotification)

        val app = SongApplication.getInstance()
        val cache = app.playerCache
        
        val baseHttpFactory = object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                return OkHttpDataSource.Factory(app.okHttpClient).createDataSource()
            }
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(baseHttpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            cacheDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uriStr = dataSpec.uri.toString()
                    
                    if (uriStr.contains("pulse.music/resolve")) {
                        val query = dataSpec.uri.getQueryParameter("query") ?: return dataSpec
                        val artworkUrl = dataSpec.uri.getQueryParameter("artwork_url") ?: ""
                        
                        Log.d(TAG, "Resolving JIT for: $query")
                        
                        return runBlocking(Dispatchers.IO) { 
                            val processId = UUID.randomUUID().toString()
                            
                            // 🚀 Extraction and Artwork Fetch in Parallel
                            val streamDeferred = async { YoutubeStreamHandler.getStreamInfo(query, processId) }
                            val artworkDeferred = async { fetchImageAsByteArray(artworkUrl) }
                            
                            val streamInfo = withTimeoutOrNull(35000L) { streamDeferred.await() }
                            val artworkBytes = artworkDeferred.await()

                            if (streamInfo != null) {
                                Log.d(TAG, "JIT Resolution SUCCESS. Upgrading MediaItem with Artwork.")
                                
                                withContext(Dispatchers.Main) {
                                    val index = player.currentMediaItemIndex
                                    if (index >= 0 && index < player.mediaItemCount) {
                                        val currentItem = player.getMediaItemAt(index)
                                        
                                        // 🛡️ Identity Check to prevent race condition swaps
                                        if (currentItem.localConfiguration?.uri?.getQueryParameter("query") == query) {
                                            val realMetadata = currentItem.mediaMetadata.buildUpon()
                                                .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                                .build()
                                            
                                            val realMediaItem = MediaItem.Builder()
                                                .setMediaId(currentItem.mediaId)
                                                .setUri(Uri.parse(streamInfo.url)) // Upgrade to real URL
                                                .setCustomCacheKey(currentItem.mediaId)
                                                .setMediaMetadata(realMetadata)
                                                .build()
                                            
                                            // replaceMediaItem triggers an immediate session update & fresh load
                                            player.replaceMediaItem(index, realMediaItem)
                                            Log.d(TAG, "MediaItem upgraded successfully at index $index")
                                        }
                                    }
                                }

                                // We return the URL here too, though the player will restart the load 
                                // due to replaceMediaItem. This is the safest way to ensure the UI updates.
                                dataSpec.buildUpon()
                                    .setUri(Uri.parse(streamInfo.url))
                                    .setHttpRequestHeaders(streamInfo.headers)
                                    .build()
                            } else {
                                Log.e(TAG, "JIT Resolution FAILED.")
                                com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(processId)
                                dataSpec
                            }
                        }
                    }
                    return dataSpec
                }
            }
        )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSourceFactory))
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (player.currentPosition > 1000) player.seekToNext()
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
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("PLAY_QUEUE", Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(playerCommands)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == "PLAY_QUEUE") {
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
                        Log.d(TAG, "Queue mapping: O(1) JIT Strategy Enabled")
                        val dummyMediaItems = songs.map { song ->
                            val encodedQuery = Uri.encode(song.audioUri)
                            val encodedArt = Uri.encode(song.imageUrl ?: "")
                            val dummyUri = Uri.parse("https://pulse.music/resolve/${song.id}?query=$encodedQuery&artwork_url=$encodedArt")

                            val metadata = MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setExtras(Bundle().apply { 
                                    putString("custom_artwork_url", song.imageUrl)
                                    putString("search_query", song.audioUri)
                                })
                                .build()
                            
                            MediaItem.Builder()
                                .setMediaId(song.id.toString())
                                .setUri(dummyUri)
                                .setCustomCacheKey(song.id.toString())
                                .setMediaMetadata(metadata)
                                .build()
                        }
                        
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
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    private suspend fun fetchImageAsByteArray(url: String?): ByteArray? {
        if (url.isNullOrEmpty()) return null
        return try {
            val app = SongApplication.getInstance()
            val loader = ImageLoader(app)
            val request = ImageRequest.Builder(app)
                .data(url)
                .allowHardware(false) 
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    stream.toByteArray()
                } else null
            } else null
        } catch (_: Exception) { null }
    }

    private fun com.example.song.data.model.StreamingItem.toSong(): Song {
        return Song(id = id, title = title, artist = artist ?: "Unknown Artist", audioUri = youtubeUrl, imageUrl = thumbnailUrl, duration = duration)
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
