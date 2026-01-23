package com.example.dashtune.data.dao

import androidx.room.*
import com.example.dashtune.data.model.RadioStation
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM radio_stations ORDER BY position ASC")
    fun getSavedStations(): Flow<List<RadioStation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RadioStation)

    @Delete
    suspend fun deleteStation(station: RadioStation)

    @Query("SELECT * FROM radio_stations WHERE id = :stationId")
    suspend fun getStationById(stationId: String): RadioStation?

    @Query("SELECT MAX(position) FROM radio_stations")
    suspend fun getMaxPosition(): Int?

    @Query("UPDATE radio_stations SET position = :newPosition WHERE id = :stationId")
    suspend fun updateStationPosition(stationId: String, newPosition: Int)

    @Query("UPDATE radio_stations SET country = :country WHERE id = :stationId")
    suspend fun updateStationCountry(stationId: String, country: String)

    @Update
    suspend fun updateStation(station: RadioStation)
} 