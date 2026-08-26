package com.example.song.data.model

import androidx.room.Entity

@Entity(primaryKeys = ["playlistId", "songId"], tableName = "playlist_song_cross_ref")
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: Int,
    val position: Int = 0
)
