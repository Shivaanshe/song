package com.example.song

import android.app.Application
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.song.data.database.AppDatabase
import com.example.song.data.repository.SongRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

class SongApplication : Application(), ImageLoaderFactory {

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    private val _cachedKeys = MutableStateFlow<Set<String>>(emptySet())
    val cachedKeys = _cachedKeys.asStateFlow()

    @androidx.media3.common.util.UnstableApi
    lateinit var playerCache: SimpleCache

    lateinit var repository: SongRepository
        private set

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // Roughly 50MB on modern devices, or we can use fixed size
                    .maxSizeBytes(50L * 1024 * 1024) // Strictly 50MB
                    .build()
            }
            .okHttpClient { okHttpClient }
            .build()
    }

    companion object {
        private lateinit var instance: SongApplication
        fun getInstance(): SongApplication = instance
    }

    @androidx.media3.common.util.UnstableApi
    private fun initPlayerCache() {
        // 🔋 Strict 250MB limit for media files (Total Cache budget: 300MB = 250MB Media + 50MB Images)
        val cacheSize: Long = 250L * 1024 * 1024 
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize)
        val databaseProvider = StandaloneDatabaseProvider(this)
        
        playerCache = SimpleCache(
            File(cacheDir, "media_cache"), 
            cacheEvictor, 
            databaseProvider
        )
        
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val keys = try { playerCache.keys } catch (e: Exception) { emptySet() }
                if (_cachedKeys.value != keys) {
                    _cachedKeys.value = keys
                }
                delay(2000)
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    override fun onCreate() {
        super.onCreate()
        instance = this

        val database = AppDatabase.getDatabase(this)
        repository = SongRepository(
            database.songDao(),
            database.playlistDao(),
            database.streamingDao(),
            filesDir
        )

        initPlayerCache()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("SongApplication", "Starting engine initialization...")
                YoutubeDL.getInstance().init(this@SongApplication)
                FFmpeg.getInstance().init(this@SongApplication)
                
                try {
                    Log.d("SongApplication", "Checking for yt-dlp binary updates...")
                    val updateResult = YoutubeDL.getInstance().updateYoutubeDL(this@SongApplication)
                    Log.d("SongApplication", "yt-dlp update status: $updateResult")
                    com.example.song.util.PulseLogger.log("Engine updated: $updateResult")
                } catch (e: Exception) {
                    Log.e("SongApplication", "Failed to update yt-dlp binary", e)
                }
                
                _isReady.value = true
                Log.d("SongApplication", "YoutubeDL and FFmpeg initialized successfully")
                com.example.song.util.PulseLogger.log("Engine Ready")
            } catch (e: YoutubeDLException) {
                Log.e("SongApplication", "Failed to initialize YoutubeDL", e)
            } catch (e: Exception) {
                Log.e("SongApplication", "General initialization error", e)
            }
        }
    }
}
