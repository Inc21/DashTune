package com.example.dashtune.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RadioBrowserStation(
    @Json(name = "stationuuid")
    val stationUuid: String,
    @Json(name = "name")
    val name: String,
    @Json(name = "url")
    val streamUrl: String,
    @Json(name = "favicon")
    val imageUrl: String = "",
    @Json(name = "tags")
    val tags: String = "",
    @Json(name = "countrycode")
    val countryCode: String = "",
    @Json(name = "codec")
    val codec: String = "",
    @Json(name = "bitrate")
    val bitrate: Int = 0,
    @Json(name = "votes")
    val votes: Int = 0,
    @Json(name = "language")
    val language: String = ""
) {
    fun toRadioStation() = RadioStation(
        id = stationUuid,
        name = name,
        streamUrl = streamUrl,
        imageUrl = imageUrl,
        country = countryCode.trim(),
        codec = codec.trim(),
        bitrate = bitrate,
        language = language.trim(),
        tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        votes = votes,
        isPlaying = false
    )
} 