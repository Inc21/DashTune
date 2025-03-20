package com.example.radioapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "radio_stations")
data class RadioStation(
    @PrimaryKey
    @SerializedName("stationuuid")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("url")
    val streamUrl: String,
    
    @SerializedName("favicon")
    val imageUrl: String,
    
    @SerializedName("tags")
    val tags: String,
    
    // Local properties
    var order: Int = 0,
    var isPlaying: Boolean = false,
    var isSaved: Boolean = false
) 