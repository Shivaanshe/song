package com.example.song

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SongApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@SongApplication)
                FFmpeg.getInstance().init(this@SongApplication)
                
                // Update yt-dlp binary to fix "Precondition check failed" / "400 Bad Request"
                try {
                    val updateResult = YoutubeDL.getInstance().updateYoutubeDL(this@SongApplication)
                    Log.d("SongApplication", "yt-dlp update status: $updateResult")
                } catch (e: Exception) {
                    Log.e("SongApplication", "Failed to update yt-dlp binary", e)
                }
                
                Log.d("SongApplication", "YoutubeDL and FFmpeg initialized successfully")
            } catch (e: YoutubeDLException) {
                Log.e("SongApplication", "Failed to initialize YoutubeDL", e)
            } catch (e: Exception) {
                Log.e("SongApplication", "General initialization error", e)
            }
        }
    }
}
