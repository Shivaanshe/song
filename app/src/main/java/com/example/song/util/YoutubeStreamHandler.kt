package com.example.song.util

import android.util.Log
import com.example.song.data.model.StreamingItem
import com.example.song.data.model.StreamInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.util.UUID

object YoutubeStreamHandler {
    private const val TAG = "YoutubeStreamHandler"
    val ytDlMutex = Mutex()

    suspend fun getMetadata(url: String): List<StreamingItem> = withContext(Dispatchers.IO) {
        try {
            val sanitizedUrl = url.removePrefix("pulse_placeholder:").trim()
            val isPlaylist = sanitizedUrl.contains("list=") || sanitizedUrl.contains("/playlist/")
            
            PulseLogger.updateTask("Initializing Engine...")
            
            val request = YoutubeDLRequest(sanitizedUrl).apply {
                addOption("--no-check-certificate")
                addOption("--rm-cache-dir") 
                addOption("--extractor-args", "youtube:player_client=android,mweb;web:visitor_data=random")
                addOption("--socket-timeout", "15")
                
                if (isPlaylist) {
                    addOption("--dump-single-json")
                    addOption("--flat-playlist")
                    addOption("--playlist-end", "50")
                } else {
                    addOption("--dump-json")
                    addOption("--no-playlist")
                }
            }
            
            val response = YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString())
            val output = response.out
            
            val items = mutableListOf<StreamingItem>()
            
            if (isPlaylist) {
                try {
                    val fullJson = JSONObject(output)
                    val playlistItem = StreamingItem(
                        youtubeUrl = sanitizedUrl,
                        title = fullJson.optString("title", "YouTube Playlist"),
                        artist = fullJson.optString("uploader", "YouTube"),
                        thumbnailUrl = fullJson.optString("thumbnail"),
                        isPlaylist = true
                    )
                    items.add(playlistItem)
                    
                    val entries = fullJson.optJSONArray("entries")
                    if (entries != null) {
                        for (i in 0 until entries.length()) {
                            parseJsonToStreamingItem(entries.getJSONObject(i), playlistItem)?.let { items.add(it) }
                        }
                    }

                    // Fallback: If playlist has no cover, use the first song's cover
                    if (playlistItem.thumbnailUrl.isNullOrEmpty()) {
                        val firstSongThumb = items.find { !it.isPlaylist }?.thumbnailUrl
                        if (!firstSongThumb.isNullOrEmpty()) {
                            items[0] = items[0].copy(thumbnailUrl = firstSongThumb)
                        }
                    }

                    PulseLogger.log("Extracted Collection: ${playlistItem.title} (${items.size - 1} items)")
                } catch (e: Exception) {
                    // Fallback if structured parse fails
                    output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                        try {
                            val json = JSONObject(line)
                            parseJsonToStreamingItem(json, null)?.let { items.add(it) }
                        } catch (_: Exception) {}
                    }
                }
            } else {
                output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                    try {
                        val json = JSONObject(line)
                        parseJsonToStreamingItem(json, null)?.let { items.add(it) }
                    } catch (_: Exception) {}
                }
            }
            return@withContext items
        } catch (e: Exception) {
            throw e
        }
    }

    private fun parseJsonToStreamingItem(json: JSONObject, playlist: StreamingItem?): StreamingItem? {
        val id = json.optString("id")
        val title = json.optString("title")
        val webUrl = json.optString("webpage_url")
        if (id.isEmpty() && title.isEmpty()) return null

        var thumb = json.optString("thumbnail")
        if (thumb.isEmpty()) {
            val thumbnails = json.optJSONArray("thumbnails")
            if (thumbnails != null && thumbnails.length() > 0) {
                thumb = thumbnails.getJSONObject(thumbnails.length() - 1).optString("url")
            }
        }
        
        // Final fallback for YouTube: construct high quality thumb from ID
        if (thumb.isEmpty() && id.isNotEmpty()) {
            thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        }

        return StreamingItem(
            youtubeUrl = if (webUrl.isNotEmpty()) webUrl else "https://www.youtube.com/watch?v=$id",
            title = if (title.isEmpty()) "Unknown Title" else title,
            artist = json.optString("uploader", playlist?.artist ?: "Unknown Artist"),
            thumbnailUrl = thumb.ifEmpty { playlist?.thumbnailUrl },
            isPlaylist = false,
            parentPlaylistUrl = playlist?.youtubeUrl,
            duration = json.optLong("duration", 0L) * 1000L
        )
    }

    private suspend fun resolveSearchToId(query: String, processId: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.removePrefix("pulse_placeholder:")
                                  .replace("ytsearch1:", "", ignoreCase = true)
                                  .replace("official audio", "", ignoreCase = true)
                                  .trim()
            
            PulseLogger.log("Searching YouTube: $cleanQuery")
            val finalQuery = "ytsearch1:$cleanQuery"
            val request = YoutubeDLRequest(finalQuery).apply {
                addOption("--get-id")
                addOption("--extractor-args", "youtube:player_client=android,mweb")
                addOption("--no-playlist")
                addOption("--no-check-certificate")
                addOption("--socket-timeout", "10")
            }
            
            val response = try {
                YoutubeDL.getInstance().execute(request, processId)
            } catch (e: Exception) {
                null
            }
            response?.out?.trim()?.lineSequence()?.firstOrNull { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getStreamInfo(youtubeUrl: String, processId: String): StreamInfo? = withContext(Dispatchers.IO) {
        val isSearch = youtubeUrl.contains("ytsearch1:") || youtubeUrl.startsWith("pulse_placeholder:")
        
        val actualUrl = if (isSearch) {
            val videoId = resolveSearchToId(youtubeUrl, processId)
            if (videoId != null) "https://www.youtube.com/watch?v=$videoId" else null
        } else {
            youtubeUrl
        }

        if (actualUrl == null) return@withContext null

        val clientConfigs = listOf(
            "android,mweb",
            "mweb",
            "android_vr"
        )

        for (clients in clientConfigs) {
            try {
                val request = YoutubeDLRequest(actualUrl).apply {
                    addOption("-f", "ba/ba*")
                    addOption("--dump-json")
                    addOption("--extractor-args", "youtube:player_client=$clients;web:visitor_data=random")
                    addOption("--socket-timeout", "10")
                }
                
                val response = try {
                    YoutubeDL.getInstance().execute(request, processId)
                } catch (e: Exception) {
                    null
                }

                val out = response?.out
                if (!out.isNullOrEmpty()) {
                    val json = JSONObject(out)
                    val directUrl = json.optString("url")
                    val videoId = json.optString("id")
                    val headers = mutableMapOf<String, String>()
                    
                    // Critical: YouTube requires specific User-Agent for certain clients
                    val jsonHeaders = json.optJSONObject("http_headers")
                    if (jsonHeaders != null) {
                        jsonHeaders.keys().forEach { key -> headers[key] = jsonHeaders.getString(key) }
                    }
                    
                    // Fallback User-Agent if missing
                    if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
                        headers["User-Agent"] = "com.google.android.youtube/19.29.37 (Linux; U; Android 14; en_US; Pixel 8 Build/AP2A.240705.004) gzip"
                    }
                    
                    if (directUrl.isNotEmpty()) {
                        return@withContext StreamInfo(url = directUrl, headers = headers, videoId = json.optString("id"))
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }
}
