package com.example.song.util

import android.util.Log
import com.example.song.data.model.StreamingItem
import com.example.song.data.model.StreamInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YoutubeStreamHandler {
    private const val TAG = "YoutubeStreamHandler"

    suspend fun getMetadata(url: String): List<StreamingItem> = withContext(Dispatchers.IO) {
        try {
            val sanitizedUrl = url.removePrefix("pulse_placeholder:")
            PulseLogger.updateTask("Initializing Engine...")
            val isCollectionUrl = sanitizedUrl.contains("list=") || sanitizedUrl.contains("/playlist/") || sanitizedUrl.contains("/album/")
            
            val request = YoutubeDLRequest(sanitizedUrl).apply {
                addOption("--dump-json")
                addOption("--flat-playlist")
                addOption("--no-check-certificate")
                addOption("--rm-cache-dir") // Clear cache for fresh tokens
                // Use android_vr and mweb as primary personas to bypass new DRM and SABR restrictions
                addOption("--extractor-args", "youtube:player_client=android_vr,mweb;web:visitor_data=random")
                
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
        
        // 🕵️ Robust Artist Extraction (handles --flat-playlist and different clients)
        var artist = json.optString("uploader").ifEmpty { 
            json.optString("uploader_name").ifEmpty {
                json.optString("artist").ifEmpty { 
                    json.optString("channel").ifEmpty {
                        json.optString("creator").ifEmpty { "Unknown Artist" }
                    } 
                }
            }
        }
        
        // 🛡️ Sanitize "null" string from YouTube API
        if (artist.equals("null", ignoreCase = true) || artist.isBlank()) {
            artist = "Unknown Artist"
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

    suspend fun getStreamInfo(youtubeUrl: String): StreamInfo? = withContext(Dispatchers.IO) {
        val sanitizedUrl = youtubeUrl.removePrefix("pulse_placeholder:")
        
        val actualUrl = if (sanitizedUrl.startsWith("ytsearch")) {
            PulseLogger.updateTask("Resolving Bridge...")
            val results = getMetadata(sanitizedUrl)
            results.find { !it.isPlaylist }?.youtubeUrl
        } else {
            sanitizedUrl
        }

        if (actualUrl == null || actualUrl.startsWith("ytsearch")) {
            PulseLogger.log("Resolution failed for: $sanitizedUrl", isError = true)
            return@withContext null
        }

        // --- Ultra-Stealth Fallback Strategy ---
        // Prioritizing android_test and mweb to bypass 403 Forbidden and SABR segment blocks
        val clientConfigs = listOf(
            "youtube:player_client=android_test", // Currently the most resilient to segment-level 403s
            "youtube:player_client=mweb",         // Reliable mobile web fallback
            "youtube:player_client=android_vr",   // VR fallback
            "youtube:player_client=web"           // Final desktop resort
        )

        for ((index, clients) in clientConfigs.withIndex()) {
            try {
                PulseLogger.updateTask("Resolving ($clients)...")
                PulseLogger.log("Attempt ${index + 1}: Resolving with [$clients]")
                
                val request = YoutubeDLRequest(actualUrl).apply {
                    if (actualUrl.startsWith("ytsearch")) {
                        addOption("--default-search", "ytsearch1")
                        addOption("--no-playlist")
                        addOption("--extract-audio")
                        addOption("--audio-format", "m4a")
                        addOption("--extractor-args", "youtube:player_client=tv,mweb") 
                    } else {
                        addOption("-f", "ba/ba*")
                    }
                    
                    addOption("--no-check-certificate")
                    addOption("--rm-cache-dir") // Clear cache for fresh tokens
                    addOption("--no-playlist")
                    addOption("--extractor-args", "$clients;web:visitor_data=random")
                }
                
                // Use getInfo to retrieve both stream URL and HTTP headers (including Cookies)
                val videoInfo = YoutubeDL.getInstance().getInfo(request)
                val directUrl = videoInfo.url
                val headers = videoInfo.httpHeaders ?: emptyMap()
                
                if (!directUrl.isNullOrEmpty() && (directUrl.contains("googlevideo.com") || directUrl.startsWith("https://"))) {
                    PulseLogger.log("Resolution Success using [$clients]")
                    PulseLogger.updateTask(null)
                    return@withContext StreamInfo(
                        url = directUrl,
                        headers = headers,
                        videoId = videoInfo.id
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: ""
                Log.w(TAG, "Attempt ${index + 1} [$clients] failed: $errorMsg")
                
                if (errorMsg.contains("DRM protected") || 
                    errorMsg.contains("confirm you're not a bot") || 
                    errorMsg.contains("403") || 
                    errorMsg.contains("Forbidden")) {
                    PulseLogger.log("Client [$clients] blocked. Trying next...", isError = true)
                    continue 
                }

                if (index == clientConfigs.lastIndex) {
                    PulseLogger.log("All resolution attempts failed.", isError = true)
                }
            }
        }

        PulseLogger.updateTask(null)
        return@withContext null
    }

    @Deprecated("Use getStreamInfo for header-aware resolution", ReplaceWith("getStreamInfo(youtubeUrl)"))
    suspend fun getDirectAudioUrl(youtubeUrl: String): String? {
        return getStreamInfo(youtubeUrl)?.url
    }
}
