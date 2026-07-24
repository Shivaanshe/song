package com.example.song.data.repository

import com.example.song.data.dao.StreamingDao
import com.example.song.data.model.StreamingItem
import com.example.song.data.api.ITunesService
import com.example.song.data.api.SpotifyService
import com.example.song.data.dao.PlaylistDao
import com.example.song.data.dao.SongDao
import com.example.song.data.model.Playlist
import com.example.song.data.model.PlaylistSongCrossRef
import com.example.song.data.model.Song
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.URL
import java.util.UUID
import android.util.Log
import com.example.song.util.YoutubeStreamHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SongRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val streamingDao: StreamingDao,
    private val baseDir: File
) {
    private val iTunesService: ITunesService by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ITunesService::class.java)
    }

    private val spotifyService: SpotifyService by lazy {
        Retrofit.Builder()
            .baseUrl("https://open.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyService::class.java)
    }

    private var downloadJob: Job? = null
    private var currentRequestId: String? = null

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs()
    val topLevelStreamingItems: Flow<List<StreamingItem>> = streamingDao.getAllTopLevelItems()
    val allSongIdsInPlaylists: Flow<List<Int>> = playlistDao.getAllSongIdsInPlaylists()

    suspend fun scanAndRestoreSongs() = withContext(Dispatchers.IO) {
        val folders = listOf(File(baseDir, "Music"), File(baseDir, "DownloadedMusic"))
        val existingUris = songDao.getAllSongsSync().map { it.audioUri }.toSet()

        folders.forEach { folder ->
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension in listOf("mp3", "m4a", "wav", "ogg", "opus")) {
                        if (!existingUris.contains(file.absolutePath)) {
                            val song = Song(
                                title = file.nameWithoutExtension.replace("_", " "),
                                artist = "Local",
                                audioUri = file.absolutePath
                            )
                            songDao.insertSong(song)
                        }
                    }
                }
            }
        }
    }

    fun getItemsForStreamingPlaylist(playlistUrl: String): Flow<List<StreamingItem>> {
        return streamingDao.getItemsForPlaylist(playlistUrl)
    }

    suspend fun insertStreamingItems(items: List<StreamingItem>) {
        streamingDao.insertItems(items)
    }

    suspend fun deleteStreamingItem(item: StreamingItem) {
        if (item.isPlaylist) {
            streamingDao.deletePlaylistItems(item.youtubeUrl)
        }
        streamingDao.deleteItem(item)
    }

    suspend fun deleteStreamingItemById(itemId: Int) {
        val item = streamingDao.getItemById(itemId)
        item?.let {
            if (it.isPlaylist) {
                streamingDao.deletePlaylistItems(it.youtubeUrl)
            }
            streamingDao.deleteItemById(itemId)
        }
    }

    suspend fun updateStreamingItemTitle(itemId: Int, newTitle: String) {
        streamingDao.updateTitle(itemId, newTitle)
    }

    suspend fun insertSong(song: Song): Int {
        val cleanTitle = song.title
            .substringAfterLast("/")
            .substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .trim()

        val coverUrl = try {
            val response = iTunesService.searchSong(cleanTitle)
            response.results.firstOrNull()?.artworkUrl100?.replace("100x100bb", "500x500bb")
        } catch (e: Exception) {
            null
        }
        
        return songDao.insertSong(song.copy(
            title = cleanTitle,
            imageUrl = coverUrl ?: song.imageUrl
        )).toInt()
    }

    suspend fun updateSong(song: Song) {
        songDao.updateSong(song)
    }

    suspend fun deleteSong(songId: Int) {
        val song = songDao.getSongByIdSync(songId)
        song?.let {
            val file = File(it.audioUri)
            if (file.exists()) {
                file.delete()
            }
        }
        songDao.deleteSong(songId)
    }

    suspend fun createPlaylist(name: String): Int {
        return playlistDao.insertPlaylist(Playlist(name = name)).toInt()
    }

    suspend fun deletePlaylist(playlistId: Int) {
        playlistDao.deletePlaylistWithCrossRefs(playlistId)
    }

    suspend fun addSongToPlaylist(songId: Int, playlistId: Int) {
        playlistDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(songId: Int, playlistId: Int) {
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    fun getSongsInPlaylist(playlistId: Int): Flow<List<Song>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }

    suspend fun resolveSpotifyMetadata(url: String) = withContext(Dispatchers.IO) {
        spotifyService.getMetadata(url)
    }

    fun cancelDownload() {
        currentRequestId?.let { 
            try {
                YoutubeDL.getInstance().destroyProcessById(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        downloadJob?.cancel()
        downloadJob = null
    }

    suspend fun downloadYouTubeAudio(
        url: String, 
        playlistId: Int? = null,
        progressCallback: (Float, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()
        currentRequestId = requestId
        
        val musicDir = File(baseDir, "DownloadedMusic")
        if (!musicDir.exists()) musicDir.mkdirs()

        try {
            Log.d("SongRepository", "Starting download for URL: $url")
            
            // 1. Create a separate request for getting info
            val infoRequest = YoutubeDLRequest(url).apply {
                addOption("--no-check-certificate")
                addOption("--yes-playlist")
                addOption("--playlist-items", "1")
                addOption("--no-cache-dir")
            }
            
            val videoInfo: VideoInfo = try {
                YoutubeDL.getInstance().getInfo(infoRequest)
            } catch (e: Exception) {
                Log.e("SongRepository", "Failed to get video info", e)
                throw Exception("Failed to get video info: ${e.message}")
            }

            if (videoInfo.duration > 1200) { // 20 mins limit
                throw Exception("Video is too long (> 20 mins)")
            }

            // 2. Create a fresh request for the actual download
            val downloadRequest = YoutubeDLRequest(url).apply {
                addOption("--extract-audio")
                addOption("--audio-format", "mp3")
                // Use requestId as filename to avoid issues with special characters in titles
                addOption("--output", "${musicDir.absolutePath}/$requestId.%(ext)s")
                addOption("--no-check-certificate")
                addOption("--yes-playlist")
                addOption("--playlist-items", "1")
                addOption("--no-cache-dir") // Disable cache to prevent old video data reuse
            }

            // Perform download
            try {
                val response = YoutubeDL.getInstance().execute(downloadRequest, requestId) { progress, eta, line ->
                    Log.d("SongRepository", "Progress: $progress, ETA: $eta, Line: $line")
                    progressCallback(progress, eta)
                }
                Log.d("SongRepository", "Download finished. Exit code: ${response.exitCode}")
            } catch (e: Exception) {
                Log.e("SongRepository", "Execution failed", e)
                throw Exception("Download failed: ${e.message}")
            }

            // 3. Fallback check for different extensions
            val possibleExtensions = listOf("mp3", "m4a", "webm", "opus", "wav")
            var downloadedFile: File? = null
            
            for (ext in possibleExtensions) {
                val file = File(musicDir, "$requestId.$ext")
                if (file.exists()) {
                    downloadedFile = file
                    break
                }
            }
            
            if (downloadedFile != null && downloadedFile.exists()) {
                Log.d("SongRepository", "Success! File found: ${downloadedFile.absolutePath}")
                
                val extension = downloadedFile.extension
                val safeTitle = (videoInfo.title ?: "Downloaded Song")
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .take(100) 
                var finalFile = File(musicDir, "$safeTitle.$extension")
                
                var counter = 1
                while (finalFile.exists()) {
                    finalFile = File(musicDir, "$safeTitle ($counter).$extension")
                    counter++
                }
                
                val songId = if (downloadedFile.renameTo(finalFile)) {
                    val song = Song(
                        title = videoInfo.title ?: finalFile.nameWithoutExtension,
                        artist = videoInfo.uploader ?: "YouTube",
                        audioUri = finalFile.absolutePath,
                        imageUrl = videoInfo.thumbnail,
                        duration = videoInfo.duration * 1000L
                    )
                    insertSong(song)
                } else {
                    val song = Song(
                        title = videoInfo.title ?: downloadedFile.nameWithoutExtension,
                        artist = videoInfo.uploader ?: "YouTube",
                        audioUri = downloadedFile.absolutePath,
                        imageUrl = videoInfo.thumbnail,
                        duration = videoInfo.duration * 1000L
                    )
                    insertSong(song)
                }

                // Link to playlist if requested
                if (playlistId != null) {
                    addSongToPlaylist(songId, playlistId)
                }
            } else {
                val existingFiles = musicDir.list()?.joinToString(", ") ?: "none"
                Log.e("SongRepository", "File $requestId.mp3 not found. Existing files: $existingFiles")
                throw Exception("Downloaded file not found in Music folder")
            }
        } catch (e: Exception) {
            Log.e("SongRepository", "General error during download", e)
            throw e
        } finally {
            currentRequestId = null
        }
    }
}
