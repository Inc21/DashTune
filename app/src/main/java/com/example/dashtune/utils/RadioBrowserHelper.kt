package com.example.dashtune.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

object RadioBrowserHelper {
    private const val API_ENDPOINT = "all.api.radio-browser.info"
    
    suspend fun getRadioBrowserServers(): List<String> = withContext(Dispatchers.IO) {
        try {
            InetAddress.getAllByName(API_ENDPOINT)
                .map { it.canonicalHostName }
                .filter { it.contains("radio-browser.info") }
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            // Fallback to a default server if DNS resolution fails
            listOf("de1.api.radio-browser.info")
        }
    }
} 