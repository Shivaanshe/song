package com.example.song.util

import android.util.Log
import com.example.song.data.model.StreamingItem
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YoutubeStreamHandler {
    private const val TAG = "YoutubeStreamHandler"

    suspend fun getMetadata(url: String): List<StreamingItem> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--flat-playlist")
            }
            
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out
            
            val lines = output.trim().split("\n").filter { it.isNotBlank() }
            val items = mutableListOf<StreamingItem>()
            var playlistItem: StreamingItem? = null

            for (line in lines) {
                try {
                    val json = JSONObject(line)
                    val type = json.optString("_type", "video")
                    
                    if (type == "playlist") {
                        playlistItem = StreamingItem(
                            youtubeUrl = json.optString("webpage_url", url),
                            title = json.optString("title", "Playlist"),
                            thumbnailUrl = if (json.isNull("thumbnail")) null else json.getString("thumbnail"),
                            isPlaylist = true
                        )
                    } else {
                        val id = json.optString("id")
                        if (id.isNotEmpty()) {
                            val title = json.optString("title", "Unknown Title")
                            val thumb = if (json.isNull("thumbnail")) null else json.getString("thumbnail")
                            val duration = json.optLong("duration", 0L)
                            
                            items.add(StreamingItem(
                                youtubeUrl = if (json.has("webpage_url")) json.getString("webpage_url") else "https://www.youtube.com/watch?v=$id",
                                title = title,
                                thumbnailUrl = thumb,
                                isPlaylist = false,
                                duration = duration * 1000L
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing line: $e")
                }
            }
            
            val result = mutableListOf<StreamingItem>()
            playlistItem?.let { result.add(it) }
            result.addAll(items)
            
            if (result.isEmpty() && lines.isNotEmpty()) {
                // Fallback for single video if not caught by loop
                try {
                    val json = JSONObject(lines[0])
                    val id = json.optString("id")
                    if (id.isNotEmpty()) {
                        result.add(StreamingItem(
                            youtubeUrl = json.optString("webpage_url", url),
                            title = json.optString("title", "Unknown Title"),
                            thumbnailUrl = if (json.isNull("thumbnail")) null else json.getString("thumbnail"),
                            isPlaylist = false,
                            duration = json.optLong("duration", 0L) * 1000L
                        ))
                    }
                } catch (e: Exception) {}
            }
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching metadata: ${e.message}", e)
            throw e
        }
    }

    suspend fun getDirectAudioUrl(youtubeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(youtubeUrl).apply {
                addOption("-f", "bestaudio")
                addOption("-g")
            }
            val response = YoutubeDL.getInstance().execute(request)
            return@withContext response.out.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting direct audio URL: ${e.message}", e)
            null
        }
    }
}
