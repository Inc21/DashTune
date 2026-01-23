package com.example.dashtune.data.repository

import com.example.dashtune.data.api.RadioBrowserService
import com.example.dashtune.data.local.RadioDatabase
import com.example.dashtune.data.model.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val radioBrowserService: RadioBrowserService,
    private val database: RadioDatabase
) {
    fun searchStations(
        query: String,
        countryCode: String? = null,
        language: String? = null,
        genre: String? = null,
        offset: Int = 0
    ): Flow<List<RadioStation>> = flow {
        try {
            val stations = radioBrowserService.searchStations(
                name = query,
                countryCode = countryCode,
                language = language,
                genre = genre,
                offset = offset
            )
                .filter { it.streamUrl.isNotBlank() && it.name.isNotBlank() }
                .map { it.toRadioStation() }
            emit(stations)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun getTopStations(): Flow<List<RadioStation>> = flow {
        try {
            val stations = radioBrowserService.getTopStations()
                .filter { it.streamUrl.isNotBlank() && it.name.isNotBlank() }
                .map { it.toRadioStation() }
            emit(stations)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveStation(station: RadioStation) {
        database.radioStationDao().insertStation(station)
    }

    suspend fun deleteStation(station: RadioStation) {
        database.radioStationDao().deleteStation(station)
    }

    fun getSavedStations(): Flow<List<RadioStation>> {
        return database.radioStationDao().getSavedStations()
    }
    
    fun getCountries(): Flow<List<Map<String, String>>> = flow {
        try {
            emit(radioBrowserService.getCountries())
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
    
    fun getLanguages(): Flow<List<Map<String, String>>> = flow {
        try {
            emit(radioBrowserService.getLanguages())
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
    
    fun getGenres(): Flow<List<Map<String, String>>> = flow {
        try {
            emit(radioBrowserService.getGenres())
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
    
    suspend fun getStationByUuid(uuid: String): RadioStation? {
        return try {
            val stations = radioBrowserService.getStationByUuid(uuid)
            if (stations.isNotEmpty()) {
                stations.first().toRadioStation()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun getStationsByCountryCode(countryCode: String): Flow<List<RadioStation>> = flow {
        try {
            val stations = radioBrowserService.getStationsByCountryCode(countryCode)
                .filter { it.streamUrl.isNotBlank() && it.name.isNotBlank() }
                .map { it.toRadioStation() }
            emit(stations)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
} 