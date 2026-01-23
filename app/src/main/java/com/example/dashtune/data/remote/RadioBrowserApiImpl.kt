package com.example.dashtune.data.remote

import com.example.dashtune.data.api.RadioBrowserService
import com.example.dashtune.data.model.RadioStation
import javax.inject.Inject

class RadioBrowserApiImpl @Inject constructor(
    private val service: RadioBrowserService
) : RadioBrowserApi {
    override suspend fun searchStations(
        name: String,
        limit: Int,
        offset: Int,
        hideBroken: Boolean,
        hasExtendedInfo: Boolean
    ): List<RadioStation> {
        return service.searchStations(
            name = name,
            limit = limit,
            countryCode = null,
            language = null,
            genre = null
        ).map { station ->
            station.toRadioStation()
        }
    }

    override suspend fun getTopStations(
        limit: Int,
        offset: Int,
        hideBroken: Boolean
    ): List<RadioStation> {
        return service.getTopStations(limit).map { station ->
            station.toRadioStation()
        }
    }

    suspend fun trackStationClick(stationUuid: String) {
        try {
            service.trackStationClick(stationUuid)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}