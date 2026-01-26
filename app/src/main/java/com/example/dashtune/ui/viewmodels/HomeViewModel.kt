package com.example.dashtune.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.model.StationIconHelper

import com.example.dashtune.data.repository.RadioRepository
import com.example.dashtune.data.repository.RadioStationRepository
import com.example.dashtune.playback.PlaybackManager
import com.example.dashtune.playback.StationValidator
import com.example.dashtune.playback.SleepTimerManager
import com.example.dashtune.data.model.StationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: RadioRepository,
    private val stationRepository: RadioStationRepository,
    private val playbackManager: PlaybackManager,
    private val stationValidator: StationValidator,
    private val sleepTimerManager: SleepTimerManager
) : ViewModel() {
    
    val savedStations: StateFlow<List<RadioStation>> = stationRepository.getSavedStations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isPlaying = playbackManager.isPlaying
    val currentStation = playbackManager.currentStation
    val isBuffering = playbackManager.isBuffering
    val currentMetadata = playbackManager.currentMetadata
    val volumeMultiplier = playbackManager.volumeMultiplier
    
    val isSleepTimerActive = sleepTimerManager.isTimerActive
    val sleepTimerRemainingMinutes = sleepTimerManager.remainingTimeMinutes
    
    private val _validatingStationId = MutableStateFlow<String?>(null)
    val validatingStationId = _validatingStationId.asStateFlow()

    private val _stationForImagePick = MutableStateFlow<RadioStation?>(null)
    val stationForImagePick = _stationForImagePick.asStateFlow()
    
    fun togglePlayback(station: RadioStation) {
        // Check if this is the currently playing station
        val isCurrentlyPlaying = currentStation.value?.id == station.id && isPlaying.value
        
        if (isCurrentlyPlaying) {
            // Already playing this station, just pause it
            playbackManager.togglePlayback(station)
        } else {
            // Different station, validate it first if needed
            viewModelScope.launch {
                if (station.status == StationStatus.UNKNOWN || station.status == StationStatus.FAILED) {
                    // Show some UI indication that we're validating
                    _validatingStationId.value = station.id
                    
                    val isValid = stationValidator.validateStation(station)
                    
                    // Clear the validating state
                    _validatingStationId.value = null
                    
                    if (isValid) {
                        playbackManager.togglePlayback(station)
                    }
                } else {
                    // Station already validated, just play it
                    playbackManager.togglePlayback(station)
                }
            }
        }
    }
    
    fun toggleSave(station: RadioStation) {
        viewModelScope.launch {
            try {
                val isCurrentlySaved = savedStations.value.any { it.id == station.id }
                if (isCurrentlySaved) {
                    repository.deleteStation(station)
                } else {
                    repository.saveStation(station)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updateStationOrder(stations: List<RadioStation>) {
        viewModelScope.launch {
            stations.forEachIndexed { index, station ->
                stationRepository.updateStationOrder(station.id, index)
            }
        }
    }

    fun requestImagePick(station: RadioStation) {
        viewModelScope.launch {
            val isSaved = stationRepository.isStationSaved(station.id)
            if (!isSaved) return@launch
            _stationForImagePick.value = station
        }
    }

    fun setCustomImage(imageUri: String) {
        val station = _stationForImagePick.value ?: return
        _stationForImagePick.value = null

        viewModelScope.launch {
            val updatedStation = station.copy(
                imageUrl = imageUri,
                isIconOverridden = true
            )
            stationRepository.updateStation(updatedStation)
        }
    }

    fun clearImagePickRequest() {
        _stationForImagePick.value = null
    }

    fun updateStationIcon(station: RadioStation) {
        viewModelScope.launch {
            val updatedStation = buildUpdatedIconStation(station) ?: return@launch
            stationRepository.updateStation(updatedStation)
        }
    }

    fun revertStationIcon(station: RadioStation) {
        viewModelScope.launch {
            val resolved = refreshStationMetadata(station)
            val originalImageUrl = station.originalImageUrl.ifBlank { resolved?.originalImageUrl.orEmpty() }
            val websiteUrl = station.websiteUrl.ifBlank { resolved?.websiteUrl.orEmpty() }
            val updatedStation = station.copy(
                imageUrl = originalImageUrl,
                originalImageUrl = originalImageUrl,
                websiteUrl = websiteUrl,
                isIconOverridden = false
            )
            stationRepository.updateStation(updatedStation)
        }
    }

    private suspend fun buildUpdatedIconStation(station: RadioStation): RadioStation? {
        val resolved = refreshStationMetadata(station)
        val websiteUrl = station.websiteUrl.ifBlank { resolved?.websiteUrl.orEmpty() }
        val originalImageUrl = station.originalImageUrl.ifBlank { resolved?.originalImageUrl.orEmpty() }
        val faviconUrl = StationIconHelper.buildFaviconUrl(websiteUrl)
        if (faviconUrl.isBlank()) return null

        return station.copy(
            imageUrl = faviconUrl,
            originalImageUrl = originalImageUrl,
            websiteUrl = websiteUrl,
            isIconOverridden = true
        )
    }

    private suspend fun refreshStationMetadata(station: RadioStation): RadioStation? {
        return if (station.websiteUrl.isBlank() || station.originalImageUrl.isBlank()) {
            repository.getStationByUuid(station.id)
        } else {
            null
        }
    }

    fun setVolumeMultiplier(value: Float) {
        playbackManager.setVolumeMultiplier(value)
    }
    
    fun startSleepTimer(durationMinutes: Int) {
        sleepTimerManager.startTimer(durationMinutes) {
            // Stop playback when timer completes
            currentStation.value?.let { station ->
                if (isPlaying.value) {
                    playbackManager.togglePlayback(station)
                }
            }
        }
    }
    
    fun cancelSleepTimer() {
        sleepTimerManager.cancelTimer()
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release PlaybackManager here - it's a singleton shared across screens
        // It will be released when the service is destroyed
    }
} 