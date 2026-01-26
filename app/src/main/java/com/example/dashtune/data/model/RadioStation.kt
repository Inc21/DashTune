package com.example.dashtune.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.jvm.Transient

@Entity(tableName = "radio_stations")
@JsonClass(generateAdapter = true)
data class RadioStation(
    @PrimaryKey
    @Json(name = "id")
    val id: String,
    
    @Json(name = "name")
    val name: String,
    
    @Json(name = "streamUrl")
    val streamUrl: String,
    
    @Json(name = "imageUrl")
    val imageUrl: String = "",
    
    @Json(name = "originalImageUrl")
    val originalImageUrl: String = "",
    
    @Json(name = "websiteUrl")
    val websiteUrl: String = "",
    
    @Json(name = "country")
    val country: String = "",
    
    @Json(name = "codec")
    val codec: String = "",
    
    @Json(name = "bitrate")
    val bitrate: Int = 0,
    
    @Json(name = "language")
    val language: String = "",
    
    @Json(name = "tags")
    val tags: List<String> = emptyList(),
    
    @Json(name = "votes")
    val votes: Int = 0,
    
    @Json(name = "isIconOverridden")
    val isIconOverridden: Boolean = false,
    
    @Ignore
    @Transient
    var isPlaying: Boolean = false,
    
    @Ignore
    @Transient
    var status: StationStatus = StationStatus.UNKNOWN,
    
    var position: Int = 0
) {
    // Secondary constructor for Room
    constructor(id: String, name: String, streamUrl: String, imageUrl: String, originalImageUrl: String, websiteUrl: String, country: String = "", codec: String = "", bitrate: Int = 0, language: String = "", tags: List<String> = emptyList(), votes: Int = 0, isIconOverridden: Boolean = false, position: Int = 0) : 
        this(id, name, streamUrl, imageUrl, originalImageUrl, websiteUrl, country, codec, bitrate, language, tags, votes, isIconOverridden, false, StationStatus.UNKNOWN, position)
        
    val isLikelyWorkingStation: Boolean
        get() = streamUrl.isNotBlank() && 
                (status == StationStatus.WORKING || 
                (status == StationStatus.UNKNOWN && bitrate > 0))
} 