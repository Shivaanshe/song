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
            PulseLogger.updateTask("Initializing Engine...")
            val isCollectionUrl = url.contains("list=") || url.contains("/playlist/") || url.contains("/album/")
            
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--flat-playlist")
                addOption("--no-check-certificate")
                addOption("--no-cache-dir")
                
                if (!isCollectionUrl && !url.startsWith("ytsearch")) {
                    addOption("--no-playlist")
                }
                
                addOption("--playlist-end", "50") 
            }
            
            PulseLogger.updateTask("Extracting Metadata...")
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out
            Log.d(TAG, "Fetched metadata. Output length: ${output.length}")
            PulseLogger.updateTask("Parsing Results...")
            
            val items = mutableListOf<StreamingItem>()
            var playlistItem: StreamingItem? = null

            output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                try {
                    val json = JSONObject(line)
                    val type = json.optString("_type")
                    
                    if (type == "playlist" || type == "multi_video") {
                        val playlistTitle = json.optString("title", "Collection")
                        val playlistUrl = json.optString("webpage_url", url)
                        val playlistThumb = extractThumbnail(json)

                        if (playlistItem == null) {
                            playlistItem = StreamingItem(
                                youtubeUrl = playlistUrl,
                                title = playlistTitle,
                                thumbnailUrl = playlistThumb,
                                isPlaylist = true
                            )
                        }

                        val entries = json.optJSONArray("entries")
                        if (entries != null) {
                            for (i in 0 until entries.length()) {
                                try {
                                    val entry = entries.getJSONObject(i)
                                    parseJsonToStreamingItem(entry, playlistItem)?.let { items.add(it) }
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        parseJsonToStreamingItem(json, playlistItem)?.let { entryItem ->
                            if (items.none { it.youtubeUrl == entryItem.youtubeUrl }) {
                                items.add(entryItem)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (playlistItem == null && isCollectionUrl) {
                playlistItem = StreamingItem(
                    youtubeUrl = url,
                    title = "YouTube Collection",
                    thumbnailUrl = items.firstOrNull()?.thumbnailUrl,
                    isPlaylist = true
                )
                val finalUrl = playlistItem.youtubeUrl
                val updated = items.map { it.copy(parentPlaylistUrl = finalUrl) }
                items.clear()
                items.addAll(updated)
            }

            val result = mutableListOf<StreamingItem>()
            if (playlistItem != null) {
                if (playlistItem.thumbnailUrl == null && items.isNotEmpty()) {
                    playlistItem = playlistItem.copy(thumbnailUrl = items.first().thumbnailUrl)
                }
                result.add(playlistItem)
            }
            result.addAll(items)
            
            PulseLogger.updateTask(null)
            return@withContext result
        } catch (e: Exception) {
            PulseLogger.updateTask(null)
            Log.e(TAG, "Error fetching metadata: ${e.message}", e)
            PulseLogger.log("Metadata error: ${e.localizedMessage}", isError = true)
            throw e
        }
    }

    private fun extractThumbnail(json: JSONObject): String? {
        if (json.has("thumbnail")) return json.getString("thumbnail")
        val thumbnails = json.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            return thumbnails.getJSONObject(thumbnails.length() - 1).optString("url")
        }
        return null
    }

    private fun parseJsonToStreamingItem(json: JSONObject, playlist: StreamingItem?): StreamingItem? {
        val id = json.optString("id")
        val title = json.optString("title")
        val webUrl = json.optString("webpage_url")
        val url = json.optString("url")
        
        if (id.isEmpty() && title.isEmpty() && webUrl.isEmpty() && url.isEmpty()) return null

        val finalUrl = when {
            id.isNotEmpty() -> if (webUrl.isNotEmpty()) webUrl else "https://www.youtube.com/watch?v=$id"
            webUrl.isNotEmpty() -> webUrl
            url.isNotEmpty() && url.startsWith("http") -> url
            else -> null
        } ?: return null

        val thumb = extractThumbnail(json) ?: playlist?.thumbnailUrl
        val artist = json.optString("uploader") ?: json.optString("artist") ?: json.optString("channel") ?: "Unknown Artist"
        val duration = json.optLong("duration", 0L)

        return StreamingItem(
            youtubeUrl = finalUrl,
            title = if (title.isEmpty()) "Unknown Title" else title,
            artist = artist,
            thumbnailUrl = thumb,
            isPlaylist = false,
            parentPlaylistUrl = playlist?.youtubeUrl,
            duration = duration * 1000L
        )
    }

    suspend fun getDirectAudioUrl(youtubeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            PulseLogger.updateTask("Resolving Bridge...")
            PulseLogger.log("Resolving audio URL for: $youtubeUrl")
            val actualUrl = if (youtubeUrl.startsWith("ytsearch")) {
                val results = getMetadata(youtubeUrl)
                // Filter out playlists to avoid "Source error" during bridge
                val bestMatch = results.find { !it.isPlaylist }
                bestMatch?.youtubeUrl
            } else {
                youtubeUrl
            }

            if (actualUrl == null || actualUrl.startsWith("ytsearch")) {
                PulseLogger.log("Resolution failed for: $youtubeUrl", isError = true)
                return@withContext null
            }

            val request = YoutubeDLRequest(actualUrl).apply {
                addOption("-f", "bestaudio")
                addOption("-g")
                addOption("--no-check-certificate")
                addOption("--no-cache-dir")
                addOption("--no-playlist")
            }
            val response = YoutubeDL.getInstance().execute(request)
            PulseLogger.updateTask(null)
            return@withContext response.out.trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            PulseLogger.updateTask(null)
            Log.e(TAG, "Error getting direct audio URL: ${e.message}", e)
            null
        }
    }
}
