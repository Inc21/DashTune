package com.example.dashtune.data.remote

import com.example.dashtune.data.api.RadioBrowserService
import com.example.dashtune.data.model.RadioBrowserStation
import com.example.dashtune.utils.RadioBrowserHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient

class RetryableRadioBrowserService(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : RadioBrowserService {
    
    private var currentServiceIndex = 0
    private var availableServers: List<String> = emptyList()
    
    private suspend fun getAvailableServers(): List<String> {
        if (availableServers.isEmpty()) {
            availableServers = RadioBrowserHelper.getRadioBrowserServers().shuffled()
                .ifEmpty { listOf("de1.api.radio-browser.info") }
            currentServiceIndex = 0
        }
        return availableServers
    }
    
    private suspend fun <T> executeWithRetry(block: suspend (RadioBrowserService) -> T): T {
        val servers = getAvailableServers()
        var lastException: Exception = Exception("All servers failed")
        
        for (i in servers.indices) {
            try {
                val service = createServiceForServer(servers[i])
                return block(service)
            } catch (e: Exception) {
                lastException = e
                currentServiceIndex = (currentServiceIndex + 1) % servers.size
                if (i == servers.size - 1) {
                    throw lastException
                }
            }
        }
        
        throw lastException
    }
    
    private fun createServiceForServer(serverName: String): RadioBrowserService {
        val baseUrl = "https://$serverName/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RadioBrowserService::class.java)
    }
    
    override suspend fun searchStations(
        name: String,
        countryCode: String?,
        language: String?,
        genre: String?,
        limit: Int,
        offset: Int
    ): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.searchStations(name, countryCode, language, genre, limit, offset)
        }
    }
    
    override suspend fun getTopStations(limit: Int): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.getTopStations(limit)
        }
    }
    
    override suspend fun getCountries(): List<Map<String, String>> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.getCountries()
        }
    }
    
    override suspend fun getLanguages(): List<Map<String, String>> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.getLanguages()
        }
    }
    
    override suspend fun getGenres(): List<Map<String, String>> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.getGenres()
        }
    }
    
    override suspend fun getStationByUuid(uuid: String): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.getStationByUuid(uuid)
        }
    }
    
    override suspend fun getStationsByCountryCode(
        countryCode: String,
        limit: Int
    ): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.getStationsByCountryCode(countryCode, limit)
        }
    }
    
    override suspend fun trackStationClick(stationUuid: String): Map<String, String> = withContext(Dispatchers.IO) {
        executeWithRetry { service ->
            service.trackStationClick(stationUuid)
        }
    }
}
