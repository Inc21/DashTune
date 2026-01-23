package com.example.dashtune.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.repository.RadioRepository
import com.example.dashtune.data.repository.RadioStationRepository
import com.example.dashtune.playback.PlaybackManager
import com.example.dashtune.data.model.StationStatus
import com.example.dashtune.playback.StationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchFilters(
    val country: String? = null,
    val language: String? = null,
    val genre: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val stationRepository: RadioStationRepository,
    private val playbackManager: PlaybackManager,
    private val stationValidator: StationValidator
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<RadioStation>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    
    private val _currentOffset = MutableStateFlow(0)
    private val _hasMoreResults = MutableStateFlow(false)
    val hasMoreResults = _hasMoreResults.asStateFlow()
    
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    
    private val _searchFilters = MutableStateFlow(SearchFilters())
    val currentFilters = _searchFilters.asStateFlow()
    
    private val _countries = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val countries = _countries.asStateFlow()
    
    private val _languages = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val languages = _languages.asStateFlow()
    
    private val _genres = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val genres = _genres.asStateFlow()

    val currentStation = playbackManager.currentStation
    val isPlaying = playbackManager.isPlaying
    val isBuffering = playbackManager.isBuffering

    private val _validatingStationId = MutableStateFlow<String?>(null)
    val validatingStationId = _validatingStationId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Track saved station IDs for immediate UI feedback
    private val _savedStationIds = MutableStateFlow<Set<String>>(emptySet())
    val savedStationIds: StateFlow<Set<String>> = _savedStationIds.asStateFlow()

    init {
        loadFilterOptions()
        
        // Load saved station IDs
        viewModelScope.launch {
            stationRepository.getSavedStations().collect { savedStations ->
                _savedStationIds.value = savedStations.map { it.id }.toSet()
            }
        }
        
        viewModelScope.launch {
            combine(_searchQuery, _searchFilters) { query, filters ->
                Pair(query, filters)
            }
                .debounce(300)
                .filter { (query, filters) -> 
                    // Allow empty query if any filter is selected
                    query.isBlank() && (filters.country != null || filters.language != null || filters.genre != null) || query.length >= 2 
                }
                .distinctUntilChanged()
                .flatMapLatest { (query, filters) ->
                    // Reset offset when query or filters change
                    _currentOffset.value = 0
                    when {
                        query.isBlank() && filters.country != null -> {
                            // Get stations by country - try using searchStations with countrycode parameter
                            val countryMap = _countries.value.find { it["name"] == filters.country }
                            val countryCode = countryMap?.get("iso_3166_1")
                            if (countryCode != null) {
                                radioRepository.searchStations(
                                    query = "",
                                    countryCode = countryCode,
                                    language = null,
                                    genre = null,
                                    offset = 0
                                )
                            } else {
                                flowOf(emptyList())
                            }
                        }
                        query.isBlank() && filters.language != null -> {
                            // Get stations by language
                            radioRepository.searchStations(
                                query = "",
                                countryCode = null,
                                language = filters.language,
                                genre = null,
                                offset = 0
                            )
                        }
                        query.isBlank() && filters.genre != null -> {
                            // Get stations by genre
                            radioRepository.searchStations(
                                query = "",
                                countryCode = null,
                                language = null,
                                genre = filters.genre,
                                offset = 0
                            )
                        }
                        query.isBlank() -> {
                            flowOf(emptyList())
                        }
                        else -> {
                            // Normal search with query and filters
                            radioRepository.searchStations(
                                query = query,
                                countryCode = filters.country,
                                language = filters.language,
                                genre = filters.genre,
                                offset = 0
                            )
                        }
                    }
                }
                .catch { /* Handle error */ }
                .collect { stations ->
                    _searchResults.value = stations
                    // If we got 100 results, there might be more
                    _hasMoreResults.value = stations.size >= 100
                    _currentOffset.value = stations.size
                }
        }
    }
    
    private fun loadFilterOptions() {
        viewModelScope.launch {
            radioRepository.getCountries().collect { countries ->
                _countries.value = countries
            }
        }
        
        viewModelScope.launch {
            radioRepository.getLanguages().collect { languages ->
                _languages.value = languages
            }
        }
        
        viewModelScope.launch {
            radioRepository.getGenres().collect { genres ->
                _genres.value = genres
            }
        }
    }

    private fun getStationsByCountry(countryName: String): Flow<List<RadioStation>> {
        // Find the country code from the country name
        val countryMap = _countries.value.find { it["name"] == countryName }
        val countryCode = countryMap?.get("iso_3166_1") ?: return flowOf(emptyList())
        
        return radioRepository.getStationsByCountryCode(countryCode)
    }

    fun searchStations(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = emptyList()
        }
    }
    
    fun updateFilters(newFilters: SearchFilters) {
        _searchFilters.value = newFilters
    }
    
    fun updateCountryFilter(country: String?) {
        _searchFilters.value = _searchFilters.value.copy(country = country)
    }
    
    fun updateLanguageFilter(language: String?) {
        _searchFilters.value = _searchFilters.value.copy(language = language)
    }
    
    fun updateGenreFilter(genre: String?) {
        _searchFilters.value = _searchFilters.value.copy(genre = genre)
    }
    
    fun clearFilters() {
        _searchFilters.value = SearchFilters()
    }

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
                    } else {
                        // Station failed validation
                        // Show a message to the user
                        _errorMessage.value = "Unable to play station: ${station.name}"
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
            val isSaved = stationRepository.isStationSaved(station.id)
            if (isSaved) {
                stationRepository.deleteStation(station)
            } else {
                stationRepository.saveStation(station)
            }
        }
    }

    fun isStationSaved(stationId: String): Flow<Boolean> = flow {
        emit(stationRepository.isStationSaved(stationId))
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    fun loadMoreStations() {
        if (_isLoadingMore.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val query = _searchQuery.value
                val filters = _searchFilters.value
                
                radioRepository.searchStations(
                    query = query,
                    countryCode = filters.country,
                    language = filters.language,
                    genre = filters.genre,
                    offset = _currentOffset.value
                ).collect { newStations ->
                    if (newStations.isNotEmpty()) {
                        _searchResults.value = _searchResults.value + newStations
                        _currentOffset.value = _searchResults.value.size
                        _hasMoreResults.value = newStations.size >= 100
                    } else {
                        _hasMoreResults.value = false
                    }
                }
            } catch (e: Exception) {
                _hasMoreResults.value = false
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release PlaybackManager here - it's a singleton shared across screens
        // It will be released when the service is destroyed
    }
}