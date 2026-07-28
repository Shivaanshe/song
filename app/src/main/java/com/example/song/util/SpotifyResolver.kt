package com.example.song.util

import android.util.Log
import com.example.song.data.model.StreamingItem
import com.example.song.data.repository.SongRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

object SpotifyResolver {
    private const val TAG = "SpotifyResolver"
    private val client = OkHttpClient()

    suspend fun resolve(url: String, repository: SongRepository): List<StreamingItem> = withContext(Dispatchers.IO) {
        try {
            PulseLogger.updateTask("Scraping Spotify Embed...")
            PulseLogger.log("Opening Spotify Embed Bridge for: $url")

            // Step 1: Convert to Embed URL
            val embedUrl = when {
                url.contains("/track/") -> url.replace("/track/", "/embed/track/")
                url.contains("/album/") -> url.replace("/album/", "/embed/album/")
                url.contains("/playlist/") -> url.replace("/playlist/", "/embed/playlist/")
                else -> url
            }

            // Step 2: Fetch HTML with Browser User-Agent
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // Step 3: Extract __NEXT_DATA__ JSON using Regex
            val pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>")
            val matcher = pattern.matcher(html)
            
            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: ""
                val fullJson = JSONObject(jsonStr)
                
                // Step 4: Parse the track list
                val pageProps = fullJson.optJSONObject("props")?.optJSONObject("pageProps") ?: return@withContext fallbackToYtDlp(url)
                val entity = pageProps.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity") ?: return@withContext fallbackToYtDlp(url)
                
                val title = entity.optString("title", "Spotify Collection")
                // Proper image extraction for collection
                val collectionThumb = extractSpotifyImage(entity) ?: ""
                
                val tracks = mutableListOf<StreamingItem>()
                val trackList = entity.optJSONArray("trackList")
                
                if (trackList != null) {
                    for (i in 0 until trackList.length()) {
                        val trackJson = trackList.getJSONObject(i)
                        val trackTitle = trackJson.optString("title")
                        val trackArtist = trackJson.optString("subtitle") ?: trackJson.optString("artist") ?: "Unknown Artist"
                        
                        if (trackTitle.isNotEmpty()) {
                            // First try to get artwork from Spotify, then iTunes fallback
                            var trackThumb = extractSpotifyImage(trackJson)
                            if (trackThumb == null || trackThumb.contains("spotifycdn.com/embed")) {
                                trackThumb = repository.fetchArtwork(trackTitle, trackArtist)
                            }
                            
                            tracks.add(StreamingItem(
                                youtubeUrl = "ytsearch1:$trackArtist - $trackTitle official audio",
                                title = trackTitle,
                                thumbnailUrl = trackThumb ?: collectionThumb,
                                isPlaylist = false,
                                parentPlaylistUrl = if (trackList.length() > 1) url else null,
                                duration = trackJson.optLong("duration", 0L)
                            ))
                        }
                    }
                }

                if (tracks.isEmpty()) return@withContext fallbackToYtDlp(url)

                val result = mutableListOf<StreamingItem>()
                if (tracks.size > 1) {
                    result.add(StreamingItem(
                        youtubeUrl = url,
                        title = title,
                        thumbnailUrl = collectionThumb,
                        isPlaylist = true
                    ))
                }
                result.addAll(tracks)

                PulseLogger.log("Successfully extracted ${tracks.size} tracks with artwork.")
                PulseLogger.updateTask(null)
                return@withContext result
            } else {
                PulseLogger.log("Embed JSON not found. Falling back to YtDlp...", isError = true)
                return@withContext fallbackToYtDlp(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embed Scrape Failed", e)
            PulseLogger.log("Embed Bridge Error: ${e.localizedMessage}", isError = true)
            return@withContext fallbackToYtDlp(url)
        }
    }

    private fun extractSpotifyImage(json: JSONObject): String? {
        // Spotify stores images in 'visual', 'coverArt', or 'artwork' objects
        val imgObj = json.optJSONObject("visual") 
            ?: json.optJSONObject("coverArt") 
            ?: json.optJSONObject("artwork")
        
        if (imgObj != null) {
            val sources = imgObj.optJSONArray("sources")
            if (sources != null && sources.length() > 0) {
                // Return the largest (usually the last) source URL
                return sources.getJSONObject(sources.length() - 1).optString("url")
            }
        }
        return null
    }

    private suspend fun fallbackToYtDlp(url: String): List<StreamingItem> = withContext(Dispatchers.IO) {
        PulseLogger.updateTask("Running YtDlp Fallback...")
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--flat-playlist")
                addOption("--no-check-certificate")
                addOption("--playlist-end", "50")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out
            
            val tracks = mutableListOf<StreamingItem>()
            var collectionItem: StreamingItem? = null

            output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                try {
                    val json = JSONObject(line)
                    val type = json.optString("_type")
                    if (type == "playlist") {
                         collectionItem = StreamingItem(
                            youtubeUrl = json.optString("webpage_url", url),
                            title = json.optString("title", "Spotify Collection"),
                            thumbnailUrl = json.optString("thumbnail"),
                            isPlaylist = true
                        )
                        val entries = json.optJSONArray("entries")
                        if (entries != null) {
                            for (i in 0 until entries.length()) {
                                val entry = entries.getJSONObject(i)
                                mapEntry(entry, collectionItem)?.let { tracks.add(it) }
                            }
                        }
                    } else {
                        mapEntry(json, collectionItem)?.let { tracks.add(it) }
                    }
                } catch (e: Exception) {}
            }
            
            val result = mutableListOf<StreamingItem>()
            collectionItem?.let { result.add(it) }
            result.addAll(tracks)
            result
        } catch (e: Exception) {
            emptyList()
        } finally {
            PulseLogger.updateTask(null)
        }
    }

    private fun mapEntry(json: JSONObject, col: StreamingItem?): StreamingItem? {
        val title = json.optString("title")
        val artist = json.optString("uploader") ?: json.optString("artist") ?: "Unknown Artist"
        if (title.isEmpty()) return null
        return StreamingItem(
            youtubeUrl = "ytsearch1:$artist - $title official audio",
            title = title,
            thumbnailUrl = json.optString("thumbnail") ?: col?.thumbnailUrl,
            isPlaylist = false,
            parentPlaylistUrl = col?.youtubeUrl
        )
    }
}
