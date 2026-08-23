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
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var httpDataSourceFactory: OkHttpDataSource.Factory
    private var currentQueue: List<Song> = emptyList()

    private val resolvedCache = ConcurrentHashMap<String, ResolvedData>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    data class ResolvedData(val url: String, val headers: Map<String, String>, val artwork: ByteArray?)

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
        
        httpDataSourceFactory = OkHttpDataSource.Factory(app.okHttpClient)
        val baseFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(baseFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            cacheDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uriStr = dataSpec.uri.toString()
                    
                    if (uriStr.contains("pulse.music/resolve")) {
                        val mediaId = dataSpec.uri.pathSegments.lastOrNull() ?: "unknown"
                        val query = dataSpec.uri.getQueryParameter("query") ?: return dataSpec
                        val artworkUrl = dataSpec.uri.getQueryParameter("artwork_url") ?: ""
                        
                        resolvedCache[query]?.let { cached ->
                            PulseLogger.log("JIT Cache Hit: Instant skip enabled.")
                            return dataSpec.buildUpon()
                                .setUri(Uri.parse(cached.url))
                                .setHttpRequestHeaders(cached.headers)
                                .setKey(mediaId)
                                .build()
                        }

                        PulseLogger.log("JIT Resolution started for: $query")
                        
                        return runBlocking(Dispatchers.IO) { 
                            val resolved = performResolution(query, artworkUrl, isPriority = true)
                            if (resolved != null) {
                                resolvedCache[query] = resolved
                                withContext(Dispatchers.Main) {
                                    updateActiveMetadata(query, resolved.artwork)
                                }

                                dataSpec.buildUpon()
                                    .setUri(Uri.parse(resolved.url))
                                    .setHttpRequestHeaders(resolved.headers)
                                    .setKey(mediaId)
                                    .build()
                            } else {
                                PulseLogger.log("JIT Resolution FAILED for: $query", isError = true)
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
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 🛡️ Avoid feedback loop: Ignore transitions triggered by metadata/playlist updates
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return

                // 🛡️ Cancel stale resolution jobs
                activeJobs.forEach { (key, job) ->
                    if (mediaItem?.localConfiguration?.uri?.getQueryParameter("query") != key) {
                        job.cancel()
                        activeJobs.remove(key)
                    }
                }

                if (mediaItem != null) {
                    val query = mediaItem.localConfiguration?.uri?.getQueryParameter("query")
                    query?.let { q ->
                        resolvedCache[q]?.let { cached ->
                            serviceScope.launch { updateActiveMetadata(q, cached.artwork) }
                        }
                    }
                }
                preResolveNextItems()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (player.currentPosition > 1000) player.seekToNext()
                }
                val stateName = when(playbackState) {
                    Player.STATE_READY -> "Ready"
                    Player.STATE_BUFFERING -> "Buffering"
                    Player.STATE_IDLE -> "Idle"
                    Player.STATE_ENDED -> "Ended"
                    else -> "Unknown"
                }
                PulseLogger.log("Player State: $stateName")
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PulseLogger.log("Playback: ${if(isPlaying) "Playing" else "Paused"}")
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

    private suspend fun performResolution(query: String, artworkUrl: String, isPriority: Boolean): ResolvedData? = withContext(Dispatchers.IO) {
        val processId = UUID.randomUUID().toString()
        val isLocalFile = query.startsWith("/") || query.startsWith("file://") || File(query).exists()
        
        if (isLocalFile) {
            val artworkBytes = fetchImageAsByteArray(artworkUrl)
            return@withContext ResolvedData(Uri.fromFile(File(query)).toString(), emptyMap(), artworkBytes)
        }

        val job = Job()
        activeJobs[query] = job
        
        try {
            YoutubeStreamHandler.ytDlMutex.withLock {
                if (!isPriority && !job.isActive) return@withLock null
                
                val streamDeferred = async { YoutubeStreamHandler.getStreamInfo(query, processId) }
                val artworkDeferred = async { fetchImageAsByteArray(artworkUrl) }
                
                val info = withTimeoutOrNull(35000L) { streamDeferred.await() }
                val art = artworkDeferred.await()
                
                if (info != null) {
                    ResolvedData(info.url, info.headers, art)
                } else {
                    null
                }
            }
        } catch (_: Exception) { null } finally {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(processId)
            activeJobs.remove(query)
            job.cancel()
        }
    }

    private fun preResolveNextItems() {
        serviceScope.launch(Dispatchers.IO) {
            val currentIndex = withContext(Dispatchers.Main) { player.currentMediaItemIndex }
            val count = withContext(Dispatchers.Main) { player.mediaItemCount }
            
            val indices = listOf(currentIndex + 1, currentIndex + 2, currentIndex - 1)
            for (i in indices) {
                if (i in 0 until count) {
                    val item = withContext(Dispatchers.Main) { player.getMediaItemAt(i) }
                    val query = item.localConfiguration?.uri?.getQueryParameter("query")
                    val artUrl = item.localConfiguration?.uri?.getQueryParameter("artwork_url")
                    
                    if (query != null && !resolvedCache.containsKey(query)) {
                        val resolved = performResolution(query, artUrl ?: "", isPriority = false)
                        if (resolved != null) {
                            resolvedCache[query] = resolved
                            withContext(Dispatchers.Main) {
                                updateMetadataInQueue(i, query, resolved.artwork)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateMetadataInQueue(index: Int, query: String, artworkBytes: ByteArray?) {
        if (index < 0 || index >= player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        
        // 🛡️ Optimization: If item already has artwork, skip update to prevent transition loop
        if (item.mediaMetadata.artworkData != null) return

        if (item.localConfiguration?.uri?.getQueryParameter("query") == query) {
            val updatedMetadata = item.mediaMetadata.buildUpon()
                .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            val updatedItem = item.buildUpon().setMediaMetadata(updatedMetadata).build()
            
            if (index == player.currentMediaItemIndex) {
                val pos = player.currentPosition
                player.replaceMediaItem(index, updatedItem)
                player.seekTo(index, pos)
            } else {
                player.replaceMediaItem(index, updatedItem)
            }
        }
    }

    private fun updateActiveMetadata(query: String, artworkBytes: ByteArray?) {
        updateMetadataInQueue(player.currentMediaItemIndex, query, artworkBytes)
        mediaSession?.setCustomLayout(emptyList()) 
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

        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == "PLAY_QUEUE") {
                val index = args.getInt("index", 0)
                val ids = args.getIntegerArrayList("ids") ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                
                serviceScope.launch {
                    val repository = SongApplication.getInstance().repository
                    
                    val streamingIds = ids.filter { it >= 1_000_000 }.map { it - 1_000_000 }
                    val localIds = ids.filter { it < 1_000_000 }
                    
                    val streamingItems = if (streamingIds.isNotEmpty()) repository.getStreamingItemsByIdsSync(streamingIds) else emptyList()
                    val localSongs = if (localIds.isNotEmpty()) repository.getSongsByIdsSync(localIds) else emptyList()
                    
                    val allItemsMap = mutableMapOf<Int, Song>()
                    localSongs.forEach { allItemsMap[it.id] = it }
                    streamingItems.forEach { item ->
                        val song = item.toSong().copy(id = 1_000_000 + item.id)
                        allItemsMap[1_000_000 + item.id] = song
                    }
                    
                    val songs = ids.mapNotNull { allItemsMap[it] }
                    
                    if (songs.isNotEmpty()) {
                        PulseLogger.log("Queue mapping: ${songs.size} items JIT-Ready")
                        val mediaItems = songs.map { song ->
                            val isLocal = song.audioUri.startsWith("/") || song.audioUri.startsWith("file://")
                            val uri = if (isLocal) Uri.fromFile(File(song.audioUri)) else {
                                val encodedQuery = Uri.encode(song.audioUri)
                                val encodedArt = Uri.encode(song.imageUrl ?: "")
                                Uri.parse("https://pulse.music/resolve/${song.id}?query=$encodedQuery&artwork_url=$encodedArt")
                            }

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
                                .setUri(uri)
                                .setCustomCacheKey(song.id.toString()) 
                                .setMediaMetadata(metadata)
                                .build()
                        }
                        
                        currentQueue = songs
                        withContext(Dispatchers.Main) {
                            player.setMediaItems(mediaItems, index, 0L)
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
            val request = ImageRequest.Builder(app).data(url).allowHardware(false).build()
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
