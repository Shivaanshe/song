package com.example.song.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streaming_items")
data class StreamingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val youtubeUrl: String,
    val title: String,
    val artist: String? = null,
    val thumbnailUrl: String?,
    val isPlaylist: Boolean = false,
    val parentPlaylistUrl: String? = null,
    val duration: Long = 0L
)
