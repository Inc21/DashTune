package com.example.dashtune.data.remote

import com.example.dashtune.data.model.RadioStation
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioBrowserApi {
    @GET("station/advancedsearch")
    suspend fun searchStations(
        @Query("name") name: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("has_extended_info") hasExtendedInfo: Boolean = true
    ): List<RadioStation>

    @GET("station/topclick")
    suspend fun getTopStations(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioStation>
} 