package com.example.song.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesService {
    @GET("search")
    suspend fun searchSong(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("limit") limit: Int = 1
    ): ITunesResponse
}
