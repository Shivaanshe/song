package com.example.song.data.repository

import com.example.song.data.api.ITunesService
import com.example.song.data.dao.PlaylistDao
import com.example.song.data.dao.SongDao
import com.example.song.data.model.Playlist
import com.example.song.data.model.PlaylistSongCrossRef
import com.example.song.data.model.Song
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SongRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {
    private val iTunesService: ITunesService by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ITunesService::class.java)
    }

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    suspend fun insertSong(song: Song) {
        // Clean up title for better search results
        val cleanTitle = song.title
            .substringAfterLast("/") // Remove path
            .substringBeforeLast(".") // Remove extension (.mp3)
            .replace("_", " ")
            .replace("-", " ")
            .trim()

        val coverUrl = try {
            val response = iTunesService.searchSong(cleanTitle)
            response.results.firstOrNull()?.artworkUrl100?.replace("100x100bb", "500x500bb")
        } catch (e: Exception) {
            null
        }
        
        // Also update the song title to the clean version for better UI
        songDao.insertSong(song.copy(
            title = cleanTitle,
            imageUrl = coverUrl ?: song.imageUrl
        ))
    }

    suspend fun deleteSong(songId: Int) {
        songDao.deleteSong(songId)
    }

    suspend fun createPlaylist(name: String) {
        playlistDao.insertPlaylist(Playlist(name = name))
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
}
