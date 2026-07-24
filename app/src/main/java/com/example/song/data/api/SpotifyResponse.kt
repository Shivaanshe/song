package com.example.song.data.api

import com.google.gson.annotations.SerializedName

data class SpotifyResponse(
    @SerializedName("title") val title: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("author_name") val artist: String?
)
