package com.example.dashtune.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.model.StationIconHelper

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

enum class SortOrder {
    MOST_LIKED,
    LEAST_LIKED,
    BITRATE_DESC,
    BITRATE_ASC,
    NAME_ASC,
    NAME_DESC
}

data class SearchFilters(
    val country: String? = null,
    val language: String? = null,
    val genre: String? = null,
    val sortOrder: SortOrder = SortOrder.MOST_LIKED
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

    private fun parseStationCount(value: String?): Int {
        val normalized = value?.replace(Regex("[^0-9]"), "").orEmpty()
        return normalized.toIntOrNull() ?: 0
    }

    private fun normalizeName(value: String?): String {
        return value?.trim()?.lowercase().orEmpty()
    }

    private fun findEntry(items: List<Map<String, String>>, name: String?): Map<String, String>? {
        val target = normalizeName(name)
        if (target.isBlank()) return null
        return items.firstOrNull { normalizeName(it["name"]) == target }
    }

    private fun getTotalCount(filters: SearchFilters): Int? {
        return when {
            filters.country != null -> {
                findEntry(_countries.value, filters.country)
                    ?.get("stationcount")
                    ?.let { parseStationCount(it) }
            }
            filters.language != null -> {
                findEntry(_languages.value, filters.language)
                    ?.get("stationcount")
                    ?.let { parseStationCount(it) }
            }
            filters.genre != null -> {
                findEntry(_genres.value, filters.genre)
                    ?.get("stationcount")
                    ?.let { parseStationCount(it) }
            }
            else -> null
        }
    }

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
                            val countryMap = findEntry(_countries.value, filters.country)
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
                    val totalCount = getTotalCount(_searchFilters.value)
                    _searchResults.value = applySorting(stations, _searchFilters.value.sortOrder)
                    // If we got 100 results, there might be more
                    _hasMoreResults.value = totalCount?.let { _currentOffset.value < it }
                        ?: (stations.size >= 100)
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
                _languages.value = cleanupFilterList(languages)
            }
        }
        
        viewModelScope.launch {
            radioRepository.getGenres().collect { genres ->
                _genres.value = cleanupFilterList(genres)
            }
        }
    }
    
    private fun cleanupFilterList(items: List<Map<String, String>>): List<Map<String, String>> {
        return items
            .filter { item ->
                val name = item["name"]?.trim() ?: ""
                val stationCount = item["stationcount"]?.toIntOrNull() ?: 0
                // Filter out entries with less than 10 stations and empty names
                name.isNotBlank() && stationCount >= 10
            }
            .map { item ->
                // Trim whitespace from names
                item.toMutableMap().apply {
                    this["name"] = this["name"]?.trim() ?: ""
                }
            }
            .distinctBy { it["name"]?.lowercase() } // Remove case-insensitive duplicates
            .sortedByDescending { it["stationcount"]?.toIntOrNull() ?: 0 } // Sort by popularity
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
    
    fun updateSortOrder(sortOrder: SortOrder) {
        _searchFilters.value = _searchFilters.value.copy(sortOrder = sortOrder)
    }
    
    fun clearFilters() {
        _searchFilters.value = SearchFilters()
    }

    private fun applySorting(stations: List<RadioStation>, sortOrder: SortOrder): List<RadioStation> {
        return when (sortOrder) {
            SortOrder.MOST_LIKED -> stations.sortedByDescending { it.votes }
            SortOrder.LEAST_LIKED -> stations.sortedBy { it.votes }
            SortOrder.BITRATE_DESC -> stations.sortedByDescending { it.bitrate }
            SortOrder.BITRATE_ASC -> stations.sortedBy { it.bitrate }
            SortOrder.NAME_ASC -> {
                // Sort alphabetically, putting stations that start with letters first
                stations.sortedWith(compareBy(
                    { !(it.name.firstOrNull()?.isLetter() ?: false) },  // false (letters) come before true (non-letters)
                    { it.name.lowercase() }
                ))
            }
            SortOrder.NAME_DESC -> {
                // Sort reverse alphabetically, putting stations that start with letters first
                stations.sortedWith(compareBy(
                    { !(it.name.firstOrNull()?.isLetter() ?: false) },  // false (letters) come before true (non-letters)
                    { it.name.lowercase() }
                )).reversed()
            }
        }
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

    fun updateStationIcon(station: RadioStation) {
        viewModelScope.launch {
            val isSaved = stationRepository.isStationSaved(station.id)
            if (!isSaved) return@launch

            val updatedStation = buildUpdatedIconStation(station) ?: return@launch
            stationRepository.updateStation(updatedStation)
        }
    }

    fun revertStationIcon(station: RadioStation) {
        viewModelScope.launch {
            val isSaved = stationRepository.isStationSaved(station.id)
            if (!isSaved) return@launch

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

    // Track station awaiting image pick
    private val _stationForImagePick = MutableStateFlow<RadioStation?>(null)
    val stationForImagePick = _stationForImagePick.asStateFlow()

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
            radioRepository.getStationByUuid(station.id)
        } else {
            null
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
                val totalCount = getTotalCount(filters)

                // Get country code if country filter is set
                val countryCode = if (filters.country != null) {
                    val countryMap = findEntry(_countries.value, filters.country)
                    countryMap?.get("iso_3166_1")
                } else null
                
                radioRepository.searchStations(
                    query = query,
                    countryCode = countryCode,
                    language = filters.language,
                    genre = filters.genre,
                    offset = _currentOffset.value
                ).collect { newStations ->
                    if (newStations.isNotEmpty()) {
                        val sortedNewStations = applySorting(newStations, filters.sortOrder)
                        _searchResults.value = _searchResults.value + sortedNewStations
                        _currentOffset.value = _searchResults.value.size
                        _hasMoreResults.value = totalCount?.let { _currentOffset.value < it }
                            ?: (newStations.size >= 100)
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