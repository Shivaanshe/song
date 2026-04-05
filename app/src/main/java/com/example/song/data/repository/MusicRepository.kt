package com.example.song.data.repository

import com.example.song.data.dao.SongDao
import com.example.song.data.model.Song
import kotlinx.coroutines.flow.Flow

class MusicRepository(
    private val songDao: SongDao
) {

    fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAllSongs()
    }

    suspend fun addSong(song: Song) {
        songDao.insertSong(song)
    }

    suspend fun deleteSong(songId: Int) {
        songDao.deleteSong(songId)
    }
}
