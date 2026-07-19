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

            // First pass: Find playlist item if it exists
            for (line in lines) {
                try {
                    val json = JSONObject(line)
                    if (json.optString("_type") == "playlist") {
                        val playlistEntries = json.optJSONArray("entries")
                        val firstEntryThumb = if (playlistEntries != null && playlistEntries.length() > 0) {
                            val first = playlistEntries.getJSONObject(0)
                            if (first.has("thumbnail")) {
                                first.getString("thumbnail")
                            } else if (first.has("thumbnails")) {
                                val thumbs = first.getJSONArray("thumbnails")
                                if (thumbs.length() > 0) thumbs.getJSONObject(0).getString("url") else null
                            } else null
                        } else null

                        playlistItem = StreamingItem(
                            youtubeUrl = json.optString("webpage_url", url),
                            title = json.optString("title", "Playlist"),
                            thumbnailUrl = if (json.has("thumbnail")) {
                                json.getString("thumbnail")
                            } else if (json.has("thumbnails")) {
                                val thumbnails = json.getJSONArray("thumbnails")
                                if (thumbnails.length() > 0) thumbnails.getJSONObject(0).getString("url") else firstEntryThumb
                            } else firstEntryThumb,
                            isPlaylist = true
                        )
                        break
                    }
                } catch (e: Exception) {}
            }

            // If no playlist item found but URL has &list=, try to infer it might be a playlist that didn't dump as _type: playlist
            if (playlistItem == null && url.contains("&list=")) {
                // We'll create a dummy playlist item that will be populated by the first video's thumb if needed
                playlistItem = StreamingItem(
                    youtubeUrl = url,
                    title = "YouTube Mix",
                    thumbnailUrl = null,
                    isPlaylist = true
                )
            }

            for (line in lines) {
                try {
                    val json = JSONObject(line)
                    val type = json.optString("_type", "video")
                    
                    if (type != "playlist") {
                        val id = json.optString("id")
                        if (id.isNotEmpty()) {
                            val title = json.optString("title", "Unknown Title")
                            val thumb = if (json.has("thumbnail")) {
                                json.getString("thumbnail")
                            } else if (json.has("thumbnails")) {
                                val thumbnails = json.getJSONArray("thumbnails")
                                if (thumbnails.length() > 0) thumbnails.getJSONObject(0).getString("url") else null
                            } else null
                            val duration = json.optLong("duration", 0L)
                            
                            // If this is the first video and playlist has no thumb, use this thumb
                            if (playlistItem != null && playlistItem.thumbnailUrl == null && thumb != null) {
                                playlistItem = playlistItem.copy(thumbnailUrl = thumb)
                            }

                            items.add(StreamingItem(
                                youtubeUrl = if (json.has("webpage_url")) json.getString("webpage_url") else "https://www.youtube.com/watch?v=$id",
                                title = title,
                                thumbnailUrl = thumb,
                                isPlaylist = false,
                                parentPlaylistUrl = playlistItem?.youtubeUrl,
                                duration = duration * 1000L
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing line: $e")
                }
            }
            
            val result = mutableListOf<StreamingItem>()
            if (playlistItem != null) {
                result.add(playlistItem)
            }
            result.addAll(items)
            
            if (result.isEmpty() && lines.isNotEmpty()) {
                // Fallback for single video if not caught by loop
                try {
                    val json = JSONObject(lines[0])
                    val id = json.optString("id")
                    if (id.isNotEmpty()) {
                        val thumb = if (json.has("thumbnail")) {
                            json.getString("thumbnail")
                        } else if (json.has("thumbnails")) {
                            val thumbnails = json.getJSONArray("thumbnails")
                            if (thumbnails.length() > 0) thumbnails.getJSONObject(0).getString("url") else null
                        } else null
                        
                        result.add(StreamingItem(
                            youtubeUrl = json.optString("webpage_url", url),
                            title = json.optString("title", "Unknown Title"),
                            thumbnailUrl = thumb,
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
