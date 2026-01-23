package com.example.dashtune.data.local

import androidx.room.*
import com.example.dashtune.data.model.RadioStation
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM radio_stations")
    fun getSavedStations(): Flow<List<RadioStation>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RadioStation)
    
    @Delete
    suspend fun deleteStation(station: RadioStation)
} 