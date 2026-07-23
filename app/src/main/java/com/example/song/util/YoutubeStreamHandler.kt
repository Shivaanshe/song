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
                addOption("--yes-playlist")
                addOption("--no-check-certificate")
                // For Mix/Radio URLs, we often need to limit the count or it might hang forever
                addOption("--playlist-end", "50") 
            }
            
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out
            
            val items = mutableListOf<StreamingItem>()
            var playlistItem: StreamingItem? = null

            // First pass: Find playlist metadata using sequence to save memory
            output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
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
                        return@forEach // Found playlist, move on
                    }
                } catch (e: Exception) {}
            }

            // If no playlist item found but URL has list=, try to infer it might be a playlist
            if (playlistItem == null && url.contains("list=")) {
                playlistItem = StreamingItem(
                    youtubeUrl = url,
                    title = "YouTube Mix",
                    thumbnailUrl = null,
                    isPlaylist = true
                )
            }

            // Second pass: Process videos using sequence
            var lineCount = 0
            output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                lineCount++
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
                            
                            // Update playlist metadata from the first valid video if needed
                            if (playlistItem != null) {
                                if (playlistItem.thumbnailUrl == null && thumb != null) {
                                    playlistItem = playlistItem.copy(thumbnailUrl = thumb)
                                }
                                if (playlistItem.title == "YouTube Mix" || playlistItem.title == "YouTube Collection") {
                                    val playlistTitle = json.optString("playlist_title")
                                    if (playlistTitle.isNotEmpty()) {
                                        playlistItem = playlistItem.copy(title = playlistTitle)
                                    }
                                }
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

            // Logic check: force a playlist container if there's more than 1 entry and no playlistItem yet
            if (playlistItem == null && items.size > 1) {
                playlistItem = StreamingItem(
                    youtubeUrl = url,
                    title = "YouTube Collection",
                    thumbnailUrl = items.firstOrNull()?.thumbnailUrl,
                    isPlaylist = true
                )
                // Update parent links for the items we already collected
                val finalPlaylistUrl = playlistItem.youtubeUrl
                val updatedItems = items.map { it.copy(parentPlaylistUrl = finalPlaylistUrl) }
                items.clear()
                items.addAll(updatedItems)
            }
            
            val result = mutableListOf<StreamingItem>()
            if (playlistItem != null) {
                result.add(playlistItem)
            }
            result.addAll(items)
            
            if (result.isEmpty() && lineCount > 0) {
                // Fallback for single video if not caught by loop
                try {
                    val firstLine = output.lineSequence().filter { it.isNotBlank() }.firstOrNull()
                    if (firstLine != null) {
                        val json = JSONObject(firstLine)
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
