package com.example.song.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "songs")
@Parcelize
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val artist: String = "Unknown Artist",
    val audioUri: String,
    val imageUrl: String? = null,
    val isFavorite: Boolean = false,
    val duration: Long = 0L
) : Parcelable
