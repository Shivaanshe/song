package com.example.song.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.song.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Query("SELECT * FROM songs ORDER BY id DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: Int)
}
