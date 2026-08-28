package com.example.song.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.song.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song)

    @Query("SELECT * FROM songs ORDER BY position ASC, id DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY position ASC, id DESC")
    suspend fun getAllSongsSync(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongByIdSync(songId: Int): Song?

    @Query("SELECT * FROM songs ORDER BY position ASC, id DESC LIMIT 1")
    suspend fun getFirstSongSync(): Song?

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY position ASC, id DESC")
    fun getFavoriteSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsByIdsSync(songIds: List<Int>): List<Song>

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: Int)
}
