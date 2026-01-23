package com.example.dashtune.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.repository.RadioRepository
import com.example.dashtune.data.repository.RadioStationRepository
import com.example.dashtune.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedStationsViewModel @Inject constructor(
    private val repository: RadioStationRepository,
    private val radioRepository: RadioRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _savedStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val savedStations = _savedStations.asStateFlow()

    val isPlaying = playbackManager.isPlaying
    val currentStation = playbackManager.currentStation

    init {
        viewModelScope.launch {
            repository.getSavedStations().collect { stations ->
                _savedStations.value = stations
                
                // Refresh country information for stations without country data
                refreshStationCountries(stations)
            }
        }
    }
    
    private fun refreshStationCountries(stations: List<RadioStation>) {
        viewModelScope.launch {
            stations.forEach { station ->
                if (station.country.isBlank()) {
                    radioRepository.getStationByUuid(station.id)?.let { updatedStation ->
                        if (updatedStation.country.isNotBlank()) {
                            repository.updateStationCountry(station.id, updatedStation.country)
                        }
                    }
                }
            }
        }
    }

    fun togglePlayback(station: RadioStation) {
        playbackManager.togglePlayback(station)
    }

    fun deleteStation(station: RadioStation) {
        viewModelScope.launch {
            repository.deleteStation(station)
        }
    }

    fun reorderStations(fromIndex: Int, toIndex: Int) {
        val currentList = _savedStations.value.toMutableList()
        val station = currentList.removeAt(fromIndex)
        currentList.add(toIndex, station)
        _savedStations.value = currentList
        
        viewModelScope.launch {
            currentList.forEachIndexed { index, station ->
                repository.updateStationOrder(station.id, index)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
    }
} 