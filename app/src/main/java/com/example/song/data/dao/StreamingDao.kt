package com.example.song.data.dao

import androidx.room.*
import com.example.song.data.model.StreamingItem
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamingDao {
    @Query("SELECT * FROM streaming_items WHERE parentPlaylistUrl IS NULL ORDER BY id DESC")
    fun getAllTopLevelItems(): Flow<List<StreamingItem>>

    @Query("SELECT * FROM streaming_items WHERE parentPlaylistUrl = :playlistUrl ORDER BY id ASC")
    fun getItemsForPlaylist(playlistUrl: String): Flow<List<StreamingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: StreamingItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<StreamingItem>)

    @Delete
    suspend fun deleteItem(item: StreamingItem)
    
    @Query("DELETE FROM streaming_items WHERE parentPlaylistUrl = :playlistUrl")
    suspend fun deletePlaylistItems(playlistUrl: String)
}
