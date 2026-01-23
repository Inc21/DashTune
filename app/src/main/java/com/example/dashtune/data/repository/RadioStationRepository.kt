package com.example.dashtune.data.repository

import com.example.dashtune.data.dao.RadioStationDao
import com.example.dashtune.data.model.RadioStation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioStationRepository @Inject constructor(
    private val radioStationDao: RadioStationDao
) {
    fun getSavedStations(): Flow<List<RadioStation>> {
        return radioStationDao.getSavedStations()
    }

    suspend fun saveStation(station: RadioStation) {
        val maxPosition = radioStationDao.getMaxPosition() ?: -1
        station.position = maxPosition + 1
        radioStationDao.insertStation(station)
    }

    suspend fun deleteStation(station: RadioStation) {
        radioStationDao.deleteStation(station)
    }

    suspend fun isStationSaved(stationId: String): Boolean {
        return radioStationDao.getStationById(stationId) != null
    }

    suspend fun updateStationOrder(stationId: String, newPosition: Int) {
        radioStationDao.updateStationPosition(stationId, newPosition)
    }

    suspend fun updateStationCountry(stationId: String, country: String) {
        radioStationDao.updateStationCountry(stationId, country)
    }

    suspend fun updateStation(station: RadioStation) {
        radioStationDao.updateStation(station)
    }
} 