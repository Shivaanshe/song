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
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UD1A.230805.019; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.144 Mobile Safari/537.36"

    suspend fun getMetadata(url: String): List<StreamingItem> = withContext(Dispatchers.IO) {
        try {
            PulseLogger.updateTask("Initializing Engine...")
            val isCollectionUrl = url.contains("list=") || url.contains("/playlist/") || url.contains("/album/")
            
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--flat-playlist")
                addOption("--no-check-certificate")
                addOption("--no-cache-dir")
                addOption("--user-agent", USER_AGENT)
                addOption("--extractor-args", "youtube:player_client=android,ios;web:visitor_data=random")
                
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
        val artist = json.optString("uploader").ifEmpty { 
            json.optString("artist").ifEmpty { 
                json.optString("channel").ifEmpty { "Unknown Artist" } 
            } 
        }
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
        val actualUrl = if (youtubeUrl.startsWith("ytsearch")) {
            PulseLogger.updateTask("Resolving Bridge...")
            val results = getMetadata(youtubeUrl)
            results.find { !it.isPlaylist }?.youtubeUrl
        } else {
            youtubeUrl
        }

        if (actualUrl == null || actualUrl.startsWith("ytsearch")) {
            PulseLogger.log("Resolution failed for: $youtubeUrl", isError = true)
            return@withContext null
        }

        // --- Multi-Persona Fallback Strategy ---
        val clientConfigs = listOf(
            "android,web",        // Standard mix
            "web,mweb",           // Browser mix
            "android_tv,web",     // TV client (often bypasses SABR)
            "android,ios"         // Mobile native mix
        )

        for ((index, clients) in clientConfigs.withIndex()) {
            try {
                PulseLogger.updateTask("Resolving ($clients)...")
                PulseLogger.log("Attempt ${index + 1}: Resolving with [$clients]")
                
                val request = YoutubeDLRequest(actualUrl).apply {
                    addOption("-f", "ba/ba*") // Relaxed format constraint
                    addOption("-g")
                    addOption("--no-check-certificate")
                    addOption("--no-cache-dir")
                    addOption("--no-playlist")
                    addOption("--user-agent", USER_AGENT)
                    addOption("--extractor-args", "youtube:player_client=$clients;web:visitor_data=random")
                }
                
                val response = YoutubeDL.getInstance().execute(request)
                val directUrl = response.out.trim()
                
                if (directUrl.isNotEmpty()) {
                    PulseLogger.log("Resolution Success using [$clients]")
                    PulseLogger.updateTask(null)
                    return@withContext directUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Attempt ${index + 1} [$clients] failed: ${e.message}")
                if (index == clientConfigs.lastIndex) {
                    PulseLogger.log("All resolution attempts failed.", isError = true)
                }
            }
        }

        PulseLogger.updateTask(null)
        return@withContext null
    }
}
