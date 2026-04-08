package com.example.song.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val artist: String = "Unknown Artist",
    val audioUri: String,
    val imageUrl: String? = null,
    val isFavorite: Boolean = false,
    val duration: Long = 0L
)
