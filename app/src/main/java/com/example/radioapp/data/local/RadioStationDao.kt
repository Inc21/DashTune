package com.example.radioapp.data.local

import androidx.room.*
import com.example.radioapp.data.model.RadioStation
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM radio_stations WHERE isSaved = 1 ORDER BY `order` ASC")
    fun getSavedStations(): Flow<List<RadioStation>>
    
    @Query("SELECT * FROM radio_stations WHERE isSaved = 1 ORDER BY `order` ASC LIMIT :pageSize OFFSET :offset")
    suspend fun getSavedStationsPaginated(pageSize: Int, offset: Int): List<RadioStation>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RadioStation)
    
    @Delete
    suspend fun deleteStation(station: RadioStation)
    
    @Query("UPDATE radio_stations SET `order` = :newOrder WHERE id = :stationId")
    suspend fun updateStationOrder(stationId: String, newOrder: Int)
    
    @Query("UPDATE radio_stations SET isPlaying = :isPlaying")
    suspend fun updateAllPlayingStatus(isPlaying: Boolean)
    
    @Query("UPDATE radio_stations SET isPlaying = :isPlaying WHERE id = :stationId")
    suspend fun updateStationPlayingStatus(stationId: String, isPlaying: Boolean)
} 