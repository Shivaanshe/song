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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyResolver {
    private const val TAG = "SpotifyResolver"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(url: String, repository: SongRepository): List<StreamingItem> = withContext(Dispatchers.IO) {
        val isSingleTrack = url.contains("/track/")
        
        // Layer 1: Fetch clean metadata from Spotify API first
        val apiMeta = try {
            PulseLogger.updateTask("Fetching Spotify Meta...")
            repository.resolveSpotifyMetadata(url)
        } catch (e: Exception) {
            Log.e(TAG, "Spotify API failed", e)
            null
        }

        // Layer 2: Attempt high-performance Embed Scraper
        try {
            PulseLogger.updateTask("Scraping Spotify Embed...")
            PulseLogger.log("Opening Spotify Embed Bridge for: $url")

            val embedUrl = when {
                url.contains("/track/") -> url.replace("/track/", "/embed/track/")
                url.contains("/album/") -> url.replace("/album/", "/embed/album/")
                url.contains("/playlist/") -> url.replace("/playlist/", "/embed/playlist/")
                else -> url
            }

            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>")
            val matcher = pattern.matcher(html)
            
            if (matcher.find()) {
                try {
                    val jsonStr = matcher.group(1) ?: ""
                    val fullJson = JSONObject(jsonStr)
                    
                    val pageProps = fullJson.optJSONObject("props")?.optJSONObject("pageProps") ?: return@withContext fallbackWithMeta(url, apiMeta, isSingleTrack)
                    val entity = pageProps.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity") ?: return@withContext fallbackWithMeta(url, apiMeta, isSingleTrack)
                    
                    val collectionTitle = apiMeta?.title ?: entity.optString("title", "Spotify Collection")
                    val collectionThumb = apiMeta?.thumbnailUrl ?: extractSpotifyImage(entity) ?: ""
                    
                    val tracks = mutableListOf<StreamingItem>()
                    val trackList = entity.optJSONArray("trackList")
                    
                    if (trackList != null) {
                        for (i in 0 until trackList.length()) {
                            val trackJson = trackList.getJSONObject(i)
                            val trackTitle = trackJson.optString("title")
                            val trackArtist = trackJson.optString("subtitle").ifEmpty { 
                                trackJson.optString("artist").ifEmpty { apiMeta?.artist ?: "Unknown Artist" } 
                            }
                            
                            if (trackTitle.isNotEmpty()) {
                                var trackThumb = extractSpotifyImage(trackJson)
                                if (trackThumb == null || trackThumb.contains("spotifycdn.com/embed")) {
                                    trackThumb = repository.fetchArtwork(trackTitle, trackArtist)
                                }
                                
                                tracks.add(StreamingItem(
                                    youtubeUrl = "ytsearch1:$trackTitle $trackArtist",
                                    title = trackTitle,
                                    artist = trackArtist,
                                    thumbnailUrl = trackThumb ?: collectionThumb,
                                    isPlaylist = false,
                                    parentPlaylistUrl = if (trackList.length() > 1) url else null,
                                    duration = trackJson.optLong("duration", 0L)
                                ))
                            }
                        }
                    } else if (isSingleTrack) {
                        var trackTitle = apiMeta?.title ?: entity.optString("title")
                        
                        // Enhanced Artist Extraction
                        val artistsArray = entity.optJSONArray("artists")
                        val artistFromEntity = if (artistsArray != null && artistsArray.length() > 0) {
                            val names = mutableListOf<String>()
                            for (j in 0 until artistsArray.length()) {
                                names.add(artistsArray.getJSONObject(j).optString("name"))
                            }
                            names.joinToString(", ")
                        } else {
                            entity.optString("subtitle").ifEmpty { 
                                entity.optString("artist").ifEmpty { 
                                    entity.optJSONObject("album")?.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
                                }
                            }
                        }

                        var trackArtist = apiMeta?.artist ?: artistFromEntity
                        if (trackArtist.isEmpty()) trackArtist = "Unknown Artist"

                        // Clean Title: oEmbed often adds suffixes like " - song by Artist"
                        if (trackTitle.contains(" - song by ")) {
                            trackTitle = trackTitle.substringBefore(" - song by ")
                        } else if (trackTitle.contains(" - Single")) {
                            trackTitle = trackTitle.substringBefore(" - Single")
                        }

                        if (trackTitle.isNotEmpty()) {
                            PulseLogger.log("Extracted Track: $trackTitle by $trackArtist")
                            
                            var trackThumb = extractSpotifyImage(entity)
                            if (trackThumb == null || (trackThumb.contains("spotifycdn.com/embed"))) {
                                trackThumb = repository.fetchArtwork(trackTitle, trackArtist)
                            }
                            
                            tracks.add(StreamingItem(
                                youtubeUrl = "ytsearch1:$trackTitle $trackArtist",
                                title = trackTitle,
                                artist = trackArtist,
                                thumbnailUrl = trackThumb ?: collectionThumb,
                                isPlaylist = false,
                                parentPlaylistUrl = null,
                                duration = entity.optLong("duration", 0L)
                            ))
                        }
                    }

                    if (tracks.isEmpty()) return@withContext fallbackWithMeta(url, apiMeta, isSingleTrack)

                    val result = mutableListOf<StreamingItem>()
                    if (tracks.size > 1 || !isSingleTrack) {
                        result.add(StreamingItem(
                            youtubeUrl = url,
                            title = collectionTitle,
                            artist = apiMeta?.artist ?: "Spotify",
                            thumbnailUrl = collectionThumb,
                            isPlaylist = true
                        ))
                    }
                    result.addAll(tracks)

                    PulseLogger.log("Successfully extracted ${tracks.size} tracks with artwork.")
                    PulseLogger.updateTask(null)
                    return@withContext result
                } catch (e: Exception) {
                    PulseLogger.log("Scrape Parse Error. Falling back...", isError = true)
                    return@withContext fallbackWithMeta(url, apiMeta, isSingleTrack)
                }
            } else {
                return@withContext fallbackWithMeta(url, apiMeta, isSingleTrack)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embed Scrape Failed", e)
            return@withContext fallbackWithMeta(url, apiMeta, isSingleTrack)
        }
    }

    private suspend fun fallbackWithMeta(url: String, apiMeta: com.example.song.data.api.SpotifyResponse?, isSingleTrack: Boolean): List<StreamingItem> {
        PulseLogger.log("Scraper blocked. Using YouTube Music Bridge...", isError = true)
        return try {
            val query = if (apiMeta != null) {
                "${apiMeta.title} ${apiMeta.artist}"
            } else {
                url.substringAfterLast("/").substringBefore("?")
            }
            
            // Revert to ytsearch1: due to scheme constraints
            val results = YoutubeStreamHandler.getMetadata("ytsearch1:$query")
            
            if (apiMeta != null) {
                results.map { 
                    if (it.isPlaylist) it.copy(title = apiMeta.title, artist = apiMeta.artist, thumbnailUrl = apiMeta.thumbnailUrl)
                    else it.copy(artist = apiMeta.artist)
                }
            } else results
        } catch (e: Exception) {
            emptyList()
        } finally {
            PulseLogger.updateTask(null)
        }
    }

    private fun extractSpotifyImage(json: JSONObject): String? {
        val imgObj = json.optJSONObject("visual") 
            ?: json.optJSONObject("coverArt") 
            ?: json.optJSONObject("artwork")
        
        if (imgObj != null) {
            val sources = imgObj.optJSONArray("sources")
            if (sources != null && sources.length() > 0) {
                return sources.getJSONObject(sources.length() - 1).optString("url")
            }
        }
        return null
    }
}
