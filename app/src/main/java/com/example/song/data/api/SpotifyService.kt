package com.example.song.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface SpotifyService {
    @GET("oembed")
    suspend fun getMetadata(
        @Query("url") url: String
    ): SpotifyResponse
}
