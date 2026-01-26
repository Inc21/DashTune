package com.example.dashtune.data.api

import com.example.dashtune.data.model.RadioBrowserStation
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface RadioBrowserService {
    @Headers(
        "User-Agent: DashTune/1.0",
        "Content-Type: application/json"
    )
    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String,
        @Query("countrycode") countryCode: String? = null,
        @Query("language") language: String? = null,
        @Query("tag") genre: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): List<RadioBrowserStation>

    @Headers(
        "User-Agent: DashTune/1.0",
        "Content-Type: application/json"
    )
    @GET("json/stations/topvote")
    suspend fun getTopStations(
        @Query("limit") limit: Int = 100
    ): List<RadioBrowserStation>
    
    @GET("json/countries")
    suspend fun getCountries(): List<Map<String, String>>
    
    @GET("json/languages")
    suspend fun getLanguages(): List<Map<String, String>>
    
    @GET("json/tags?order=stationcount&reverse=true&limit=500")
    suspend fun getGenres(): List<Map<String, String>>

    @Headers(
        "User-Agent: DashTune/1.0",
        "Content-Type: application/json"
    )
    @GET("json/stations/byuuid")
    suspend fun getStationByUuid(
        @Query("uuids") uuid: String
    ): List<RadioBrowserStation>

    @Headers(
        "User-Agent: DashTune/1.0",
        "Content-Type: application/json"
    )
    @GET("json/stations/bycountrycodeexact")
    suspend fun getStationsByCountryCode(
        @Query("countrycode") countryCode: String,
        @Query("limit") limit: Int = 100
    ): List<RadioBrowserStation>

    @Headers(
        "User-Agent: DashTune/1.0",
        "Content-Type: application/json"
    )
    @GET("json/url")
    suspend fun trackStationClick(
        @Query("stationuuid") stationUuid: String
    ): Map<String, String>
} 