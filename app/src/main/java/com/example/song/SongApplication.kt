package com.example.song

import android.app.Application
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SongApplication : Application() {

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    @androidx.media3.common.util.UnstableApi
    lateinit var playerCache: SimpleCache

    companion object {
        private lateinit var instance: SongApplication
        fun getInstance(): SongApplication = instance
    }

    @androidx.media3.common.util.UnstableApi
    private fun initPlayerCache() {
        val evictor = LeastRecentlyUsedCacheEvictor(300 * 1024 * 1024L) // 300MB
        val databaseProvider = StandaloneDatabaseProvider(this)
        playerCache = SimpleCache(File(cacheDir, "media_cache"), evictor, databaseProvider)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        initPlayerCache()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("SongApplication", "Starting engine initialization...")
                
                // Initialize binaries
                YoutubeDL.getInstance().init(this@SongApplication)
                FFmpeg.getInstance().init(this@SongApplication)
                
                // 2. Make Update Blockable & 5. Optimization (Once per session in onCreate)
                // We await this call before setting _isReady to true
                try {
                    Log.d("SongApplication", "Checking for yt-dlp binary updates...")
                    val updateResult = YoutubeDL.getInstance().updateYoutubeDL(this@SongApplication)
                    Log.d("SongApplication", "yt-dlp update status: $updateResult")
                } catch (e: Exception) {
                    // 4. Error Resilience: Proceed with bundled version if update fails (e.g. no internet)
                    Log.e("SongApplication", "Failed to update yt-dlp binary, using bundled version", e)
                }
                
                // 1. Global Initialization State
                _isReady.value = true
                Log.d("SongApplication", "YoutubeDL and FFmpeg initialized successfully")
            } catch (e: YoutubeDLException) {
                Log.e("SongApplication", "Failed to initialize YoutubeDL", e)
            } catch (e: Exception) {
                Log.e("SongApplication", "General initialization error", e)
            }
        }
    }
}
