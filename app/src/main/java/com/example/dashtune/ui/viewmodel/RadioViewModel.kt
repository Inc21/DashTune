package com.example.dashtune.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.remote.RadioBrowserApi
import com.example.dashtune.data.repository.RadioStationRepository
import com.example.dashtune.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val api: RadioBrowserApi,
    private val repository: RadioStationRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _savedStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val savedStations = _savedStations.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RadioStation>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    val currentlyPlaying = playbackManager.currentStation
    val isPlaying = playbackManager.isPlaying

    init {
        loadSavedStations()
    }

    private fun loadSavedStations() {
        viewModelScope.launch {
            repository.getSavedStations().collect {
                _savedStations.value = it
            }
        }
    }

    fun searchStations(query: String) {
        viewModelScope.launch {
            try {
                _searchResults.value = api.searchStations(query)
            } catch (e: Exception) {
                // Handle error
                _searchResults.value = emptyList()
            }
        }
    }

    fun saveStation(station: RadioStation) {
        viewModelScope.launch {
            repository.saveStation(station)
        }
    }

    fun removeStation(station: RadioStation) {
        viewModelScope.launch {
            repository.deleteStation(station)
        }
    }

    fun updateStationsOrder(stations: List<RadioStation>) {
        viewModelScope.launch {
            stations.forEachIndexed { index, station ->
                repository.updateStationOrder(station.id, index)
            }
        }
    }

    fun playStation(station: RadioStation) {
        playbackManager.togglePlayback(station)
    }

    fun togglePlayback(station: RadioStation) {
        playbackManager.togglePlayback(station)
    }

    fun stopPlayback() {
        playbackManager.stopPlayback()
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
    }
} 