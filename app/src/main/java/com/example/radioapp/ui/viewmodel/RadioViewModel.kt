package com.example.radioapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.radioapp.data.local.RadioDatabase
import com.example.radioapp.data.model.RadioStation
import com.example.radioapp.data.remote.RadioBrowserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val api: RadioBrowserApi,
    private val db: RadioDatabase
) : ViewModel() {

    private val _savedStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val savedStations = _savedStations.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RadioStation>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _currentlyPlaying = MutableStateFlow<RadioStation?>(null)
    val currentlyPlaying = _currentlyPlaying.asStateFlow()

    init {
        loadSavedStations()
    }

    private fun loadSavedStations() {
        viewModelScope.launch {
            db.radioStationDao().getSavedStations().collect {
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
            val maxOrder = _savedStations.value.maxOfOrNull { it.order } ?: -1
            val stationToSave = station.copy(
                order = maxOrder + 1,
                isSaved = true
            )
            db.radioStationDao().insertStation(stationToSave)
        }
    }

    fun removeStation(station: RadioStation) {
        viewModelScope.launch {
            db.radioStationDao().deleteStation(station)
        }
    }

    fun updateStationOrder(stations: List<RadioStation>) {
        viewModelScope.launch {
            stations.forEachIndexed { index, station ->
                db.radioStationDao().updateStationOrder(station.id, index)
            }
        }
    }

    fun playStation(station: RadioStation) {
        viewModelScope.launch {
            _currentlyPlaying.value?.let {
                db.radioStationDao().updateStationPlayingStatus(it.id, false)
            }
            db.radioStationDao().updateStationPlayingStatus(station.id, true)
            _currentlyPlaying.value = station
        }
    }

    fun stopPlayback() {
        viewModelScope.launch {
            _currentlyPlaying.value?.let {
                db.radioStationDao().updateStationPlayingStatus(it.id, false)
            }
            _currentlyPlaying.value = null
        }
    }
} 