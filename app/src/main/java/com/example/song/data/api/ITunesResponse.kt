package com.example.song.data.api

data class ITunesResponse(
    val resultCount: Int,
    val results: List<ITunesResult>
)

data class ITunesResult(
    val trackName: String?,
    val artistName: String?,
    val artworkUrl100: String?
)
