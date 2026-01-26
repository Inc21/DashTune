package com.example.dashtune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.ui.components.SearchBar
import com.example.dashtune.ui.components.StationCard
import com.example.dashtune.ui.viewmodels.SearchViewModel
import com.example.dashtune.ui.viewmodels.SearchFilters
import com.example.dashtune.ui.viewmodels.SortOrder
import androidx.compose.material.icons.filled.ArrowDropDown
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.dashtune.data.model.StationTagHelper

private fun parseStationCount(value: String?): Int {
    val normalized = value?.replace(Regex("[^0-9]"), "").orEmpty()
    return normalized.toIntOrNull() ?: 0
}

// Whitelist of valid content tags (genres, formats, themes)
private val validContentTags = setOf(
    // Music genres
    "rock", "pop", "jazz", "blues", "classical", "country", "folk", "soul",
    "funk", "disco", "metal", "punk", "reggae", "hip hop", "rap", "rnb",
    "electronic", "techno", "house", "trance", "ambient", "chill", "lounge",
    "dance", "edm", "dubstep", "drum and bass", "indie", "alternative",
    "grunge", "emo", "hardcore", "progressive", "psychedelic", "acoustic",
    "instrumental", "vocal", "opera", "symphony", "orchestra", "piano",
    "guitar", "latin", "salsa", "merengue", "bachata", "cumbia", "tango",
    "flamenco", "world", "african", "asian", "celtic", "irish", "german",
    "french", "italian", "spanish", "brazilian", "caribbean", "tropical",
    "oldies", "retro", "vintage", "classic rock", "soft rock", "hard rock",
    "heavy metal", "death metal", "black metal", "thrash", "power metal",
    "smooth jazz", "acid jazz", "fusion", "swing", "bebop", "big band",
    "new age", "meditation", "relaxation", "sleep", "spa", "nature",
    "easy listening", "adult contemporary", "love songs", "romantic",
    "christmas", "holiday", "christian", "gospel", "religious", "spiritual",
    "worship", "catholic", "islamic", "jewish", "buddhist",
    "kids", "children", "family", "disney", "anime", "jpop", "kpop",
    "bollywood", "hindi", "arabic", "turkish", "russian", "polish",
    "ska", "rockabilly", "doo wop", "motown", "groove", "beats",
    // Content types
    "news", "talk", "sports", "comedy", "drama", "culture", "education",
    "science", "technology", "business", "politics", "health", "lifestyle",
    "food", "travel", "nature", "history", "documentary", "entertainment",
    "variety", "community", "local", "college", "university", "public",
    "npr", "bbc", "information", "weather", "traffic"
)

private fun cleanTagKey(value: String): String? {
    val normalized = value.trim().lowercase()
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    // Only return if it's a known valid content tag
    return if (normalized in validContentTags) normalized else null
}

private fun formatGenreLabel(value: String?): String {
    val normalized = value
        ?.trim()
        ?.replace(Regex("[_-]+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase()
        ?: ""
    return normalized
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
        .ifBlank { value?.trim().orEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val currentStation by viewModel.currentStation.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val validatingStationId by viewModel.validatingStationId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentFilters by viewModel.currentFilters.collectAsState()
    val savedStationIds by viewModel.savedStationIds.collectAsState()
    val hasMoreResults by viewModel.hasMoreResults.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    val countries by viewModel.countries.collectAsState()
    val languages by viewModel.languages.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val context = LocalContext.current
    val stationForImagePick by viewModel.stationForImagePick.collectAsState()
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.setCustomImage(it.toString())
        } ?: viewModel.clearImagePickRequest()
    }
    
    LaunchedEffect(stationForImagePick) {
        if (stationForImagePick != null) {
            imagePickerLauncher.launch(arrayOf("image/*"))
        }
    }
    
    val tabs = listOf("Search", "Countries", "Languages", "Tags")

    val openStationSite: (RadioStation) -> Unit = { station ->
        if (station.websiteUrl.isNotBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(station.websiteUrl))
            context.startActivity(intent)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse Stations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            if (selectedTabIndex != index) {
                                selectedTabIndex = index
                                searchQuery = ""
                                viewModel.searchStations("")
                                viewModel.clearFilters()
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }
            
            // Tab Content
            when (selectedTabIndex) {
                0 -> SearchTab(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { 
                        searchQuery = it
                        viewModel.searchStations(it)
                    },
                    searchResults = searchResults,
                    currentStation = currentStation,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    validatingStationId = validatingStationId,
                    savedStationIds = savedStationIds,
                    errorMessage = errorMessage,
                    hasMoreResults = hasMoreResults,
                    isLoadingMore = isLoadingMore,
                    currentSortOrder = currentFilters.sortOrder,
                    onSortOrderChange = { viewModel.updateSortOrder(it) },
                    onPlayClick = { viewModel.togglePlayback(it) },
                    onSaveClick = { viewModel.toggleSave(it) },
                    onClearError = { viewModel.clearErrorMessage() },
                    onLoadMore = { viewModel.loadMoreStations() },
                    onVisitSite = openStationSite,
                    onUpdateIcon = { viewModel.updateStationIcon(it) },
                    onRevertIcon = { viewModel.revertStationIcon(it) },
                    onPickImage = { viewModel.requestImagePick(it) }
                )
                1 -> CountriesTab(
                    countries = countries,
                    onCountrySelected = { country ->
                        viewModel.updateCountryFilter(country["name"])
                        searchQuery = ""
                        viewModel.searchStations("")
                    },
                    searchResults = searchResults,
                    currentStation = currentStation,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    validatingStationId = validatingStationId,
                    savedStationIds = savedStationIds,
                    currentFilters = currentFilters,
                    currentSortOrder = currentFilters.sortOrder,
                    onSortOrderChange = { viewModel.updateSortOrder(it) },
                    onPlayClick = { viewModel.togglePlayback(it) },
                    onSaveClick = { viewModel.toggleSave(it) },
                    onClearSelection = { viewModel.clearFilters() },
                    hasMoreResults = hasMoreResults,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = { viewModel.loadMoreStations() },
                    onVisitSite = openStationSite,
                    onUpdateIcon = { viewModel.updateStationIcon(it) },
                    onRevertIcon = { viewModel.revertStationIcon(it) },
                    onPickImage = { viewModel.requestImagePick(it) }
                )
                2 -> LanguagesTab(
                    languages = languages,
                    onLanguageSelected = { language ->
                        viewModel.updateLanguageFilter(language["name"])
                    },
                    searchResults = searchResults,
                    currentStation = currentStation,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    validatingStationId = validatingStationId,
                    savedStationIds = savedStationIds,
                    currentFilters = currentFilters,
                    currentSortOrder = currentFilters.sortOrder,
                    onSortOrderChange = { viewModel.updateSortOrder(it) },
                    onPlayClick = { viewModel.togglePlayback(it) },
                    onSaveClick = { viewModel.toggleSave(it) },
                    onClearSelection = { viewModel.clearFilters() },
                    hasMoreResults = hasMoreResults,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = { viewModel.loadMoreStations() },
                    onVisitSite = openStationSite,
                    onUpdateIcon = { viewModel.updateStationIcon(it) },
                    onRevertIcon = { viewModel.revertStationIcon(it) },
                    onPickImage = { viewModel.requestImagePick(it) }
                )
                3 -> TagsTab(
                    genres = genres,
                    onGenreSelected = { genre ->
                        viewModel.updateGenreFilter(genre)
                    },
                    searchResults = searchResults,
                    currentStation = currentStation,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    validatingStationId = validatingStationId,
                    savedStationIds = savedStationIds,
                    currentFilters = currentFilters,
                    currentSortOrder = currentFilters.sortOrder,
                    onSortOrderChange = { viewModel.updateSortOrder(it) },
                    onPlayClick = { viewModel.togglePlayback(it) },
                    onSaveClick = { viewModel.toggleSave(it) },
                    onClearSelection = { viewModel.clearFilters() },
                    hasMoreResults = hasMoreResults,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = { viewModel.loadMoreStations() },
                    onVisitSite = openStationSite,
                    onUpdateIcon = { viewModel.updateStationIcon(it) },
                    onRevertIcon = { viewModel.revertStationIcon(it) },
                    onPickImage = { viewModel.requestImagePick(it) }
                )
            }
        }
    }
}

@Composable
fun SearchTab(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<RadioStation>,
    currentStation: RadioStation?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    validatingStationId: String?,
    savedStationIds: Set<String>,
    errorMessage: String?,
    hasMoreResults: Boolean,
    isLoadingMore: Boolean,
    currentSortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onPlayClick: (RadioStation) -> Unit,
    onSaveClick: (RadioStation) -> Unit,
    onClearError: () -> Unit,
    onLoadMore: () -> Unit,
    onVisitSite: (RadioStation) -> Unit,
    onUpdateIcon: (RadioStation) -> Unit,
    onRevertIcon: (RadioStation) -> Unit,
    onPickImage: (RadioStation) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showInfoTip by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.fillMaxWidth()) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = Icons.Default.Sort,
                onTrailingIconClick = { showSortMenu = true }
            )
            
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Most Liked") },
                    onClick = {
                        onSortOrderChange(SortOrder.MOST_LIKED)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Least Liked") },
                    onClick = {
                        onSortOrderChange(SortOrder.LEAST_LIKED)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Highest Bitrate") },
                    onClick = {
                        onSortOrderChange(SortOrder.BITRATE_DESC)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Lowest Bitrate") },
                    onClick = {
                        onSortOrderChange(SortOrder.BITRATE_ASC)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("A-Z") },
                    onClick = {
                        onSortOrderChange(SortOrder.NAME_ASC)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Z-A") },
                    onClick = {
                        onSortOrderChange(SortOrder.NAME_DESC)
                        showSortMenu = false
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Error message display
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClearError,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Dismiss error"
                        )
                    }
                }
            }
        }
        
        if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No stations found")
            }
        } else if (searchQuery.length >= 2) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info tip - dismissable and scrolls with content
                if (showInfoTip) {
                    item(span = { GridItemSpan(this.maxLineSpan) }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tip: Station listings may not always be accurate. Try searching by name if browsing doesn't find what you need.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                items(searchResults) { station ->
                    val isCurrentlyPlaying = currentStation?.id == station.id && isPlaying
                    val isValidating = validatingStationId == station.id
                    val isCurrentlyBuffering = currentStation?.id == station.id && isBuffering
                    val isSaved = savedStationIds.contains(station.id)
                    
                    StationCard(
                        station = station,
                        isPlaying = isCurrentlyPlaying,
                        isLoading = isValidating || isCurrentlyBuffering,
                        isSaved = isSaved,
                        onPlayClick = { onPlayClick(station) },
                        onSaveClick = { onSaveClick(station) },
                        allowIconActions = isSaved,
                        onVisitSite = onVisitSite,
                        onUpdateIcon = onUpdateIcon,
                        onRevertIcon = onRevertIcon,
                        onPickImage = onPickImage,
                        enableCardClick = true,
                        showExtendedInfo = true,
                        showBadgesInCard = true
                    )
                }
                
                if (hasMoreResults) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            enabled = !isLoadingMore
                        ) {
                            Text(if (isLoadingMore) "Loading..." else "Load More")
                        }
                    }
                }
            }
            
        }
    }
}

@Composable
fun CountriesTab(
    countries: List<Map<String, String>>,
    onCountrySelected: (Map<String, String>) -> Unit,
    searchResults: List<RadioStation>,
    currentStation: RadioStation?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    validatingStationId: String?,
    savedStationIds: Set<String>,
    currentFilters: SearchFilters,
    currentSortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onPlayClick: (RadioStation) -> Unit,
    onSaveClick: (RadioStation) -> Unit,
    onClearSelection: () -> Unit,
    hasMoreResults: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onVisitSite: (RadioStation) -> Unit,
    onUpdateIcon: (RadioStation) -> Unit,
    onRevertIcon: (RadioStation) -> Unit,
    onPickImage: (RadioStation) -> Unit
) {
    var countrySearchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        if (currentFilters.country != null) {
            val totalStations = countries
                .firstOrNull { it["name"] == currentFilters.country }
                ?.get("stationcount")
                ?.let { parseStationCount(it) }
                ?: searchResults.size
            val showLoadMore = searchResults.isNotEmpty() &&
                (hasMoreResults || totalStations > searchResults.size)
            // Show stations for selected country
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentFilters.country} (${totalStations})",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onClearSelection) {
                    Text("Back to Countries")
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Sort", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Most Liked") },
                        onClick = {
                            onSortOrderChange(SortOrder.MOST_LIKED)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Least Liked") },
                        onClick = {
                            onSortOrderChange(SortOrder.LEAST_LIKED)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Highest Bitrate") },
                        onClick = {
                            onSortOrderChange(SortOrder.BITRATE_DESC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Lowest Bitrate") },
                        onClick = {
                            onSortOrderChange(SortOrder.BITRATE_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("A-Z") },
                        onClick = {
                            onSortOrderChange(SortOrder.NAME_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Z-A") },
                        onClick = {
                            onSortOrderChange(SortOrder.NAME_DESC)
                            showSortMenu = false
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(searchResults) { station ->
                    val isCurrentlyPlaying = currentStation?.id == station.id && isPlaying
                    val isValidating = validatingStationId == station.id
                    val isCurrentlyBuffering = currentStation?.id == station.id && isBuffering
                    val isSaved = savedStationIds.contains(station.id)
                    
                    StationCard(
                        station = station,
                        isPlaying = isCurrentlyPlaying,
                        isLoading = isValidating || isCurrentlyBuffering,
                        isSaved = isSaved,
                        onPlayClick = { onPlayClick(station) },
                        onSaveClick = { onSaveClick(station) },
                        allowIconActions = isSaved,
                        onVisitSite = onVisitSite,
                        onUpdateIcon = onUpdateIcon,
                        onRevertIcon = onRevertIcon,
                        onPickImage = onPickImage,
                        enableCardClick = true,
                        showExtendedInfo = true,
                        showBadgesInCard = true
                    )
                }

                if (showLoadMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            enabled = !isLoadingMore
                        ) {
                            Text(if (isLoadingMore) "Loading..." else "Load More")
                        }
                    }
                }
            }

        } else {
            // Show list of countries
            Text(
                text = "Select a Country",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Search bar for filtering countries
            SearchBar(
                query = countrySearchQuery,
                onQueryChange = { countrySearchQuery = it },
                placeholder = "Search for countries...",
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val filteredCountries = if (countrySearchQuery.isBlank()) {
                countries.sortedBy { it["name"] }
            } else {
                countries.filter { 
                    it["name"]?.contains(countrySearchQuery, ignoreCase = true) == true 
                }.sortedBy { it["name"] }
            }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCountries) { country ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCountrySelected(country) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = country["name"] ?: "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${country["stationcount"] ?: "0"} stations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguagesTab(
    languages: List<Map<String, String>>,
    onLanguageSelected: (Map<String, String>) -> Unit,
    searchResults: List<RadioStation>,
    currentStation: RadioStation?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    validatingStationId: String?,
    savedStationIds: Set<String>,
    currentFilters: SearchFilters,
    currentSortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onPlayClick: (RadioStation) -> Unit,
    onSaveClick: (RadioStation) -> Unit,
    onClearSelection: () -> Unit,
    hasMoreResults: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onVisitSite: (RadioStation) -> Unit,
    onUpdateIcon: (RadioStation) -> Unit,
    onRevertIcon: (RadioStation) -> Unit,
    onPickImage: (RadioStation) -> Unit
) {
    var languageSearchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        if (currentFilters.language != null) {
            val totalStations = languages
                .firstOrNull { it["name"] == currentFilters.language }
                ?.get("stationcount")
                ?.let { parseStationCount(it) }
                ?: searchResults.size
            val showLoadMore = searchResults.isNotEmpty() &&
                (hasMoreResults || totalStations > searchResults.size)
            // Show stations for selected language
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentFilters.language} (${totalStations})",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onClearSelection) {
                    Text("Back to Languages")
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Sort", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Most Liked") },
                        onClick = {
                            onSortOrderChange(SortOrder.MOST_LIKED)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Least Liked") },
                        onClick = {
                            onSortOrderChange(SortOrder.LEAST_LIKED)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Highest Bitrate") },
                        onClick = {
                            onSortOrderChange(SortOrder.BITRATE_DESC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Lowest Bitrate") },
                        onClick = {
                            onSortOrderChange(SortOrder.BITRATE_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("A-Z") },
                        onClick = {
                            onSortOrderChange(SortOrder.NAME_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Z-A") },
                        onClick = {
                            onSortOrderChange(SortOrder.NAME_DESC)
                            showSortMenu = false
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(searchResults) { station ->
                    val isCurrentlyPlaying = currentStation?.id == station.id && isPlaying
                    val isValidating = validatingStationId == station.id
                    val isCurrentlyBuffering = currentStation?.id == station.id && isBuffering
                    val isSaved = savedStationIds.contains(station.id)
                    
                    StationCard(
                        station = station,
                        isPlaying = isCurrentlyPlaying,
                        isLoading = isValidating || isCurrentlyBuffering,
                        isSaved = isSaved,
                        onPlayClick = { onPlayClick(station) },
                        onSaveClick = { onSaveClick(station) },
                        allowIconActions = isSaved,
                        onVisitSite = onVisitSite,
                        onUpdateIcon = onUpdateIcon,
                        onRevertIcon = onRevertIcon,
                        onPickImage = onPickImage,
                        enableCardClick = true,
                        showExtendedInfo = true,
                        showBadgesInCard = true
                    )
                }

                if (showLoadMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            enabled = !isLoadingMore
                        ) {
                            Text(if (isLoadingMore) "Loading..." else "Load More")
                        }
                    }
                }
            }
        } else {
            // Show list of languages
            Text(
                text = "Select a Language",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Search bar for filtering languages
            SearchBar(
                query = languageSearchQuery,
                onQueryChange = { languageSearchQuery = it },
                placeholder = "Search for languages...",
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val filteredLanguages = if (languageSearchQuery.isBlank()) {
                languages.sortedBy { it["name"] }
            } else {
                languages.filter { 
                    it["name"]?.contains(languageSearchQuery, ignoreCase = true) == true 
                }.sortedBy { it["name"] }
            }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLanguages) { language ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = language["name"] ?: "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${language["stationcount"] ?: "0"} stations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagsTab(
    genres: List<Map<String, String>>,
    onGenreSelected: (String) -> Unit,
    searchResults: List<RadioStation>,
    currentStation: RadioStation?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    validatingStationId: String?,
    savedStationIds: Set<String>,
    currentFilters: SearchFilters,
    currentSortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onPlayClick: (RadioStation) -> Unit,
    onSaveClick: (RadioStation) -> Unit,
    onClearSelection: () -> Unit,
    hasMoreResults: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onVisitSite: (RadioStation) -> Unit,
    onUpdateIcon: (RadioStation) -> Unit,
    onRevertIcon: (RadioStation) -> Unit,
    onPickImage: (RadioStation) -> Unit
) {
    var genreSearchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    val aggregatedGenres = remember(genres) {
        val totals = mutableMapOf<String, Int>()
        genres.forEach { genre ->
            val rawName = genre["name"].orEmpty()
            val stationCount = genre["stationcount"]?.let { parseStationCount(it) } ?: 0
            if (stationCount <= 0) return@forEach
            val cleaned = cleanTagKey(rawName)
            if (cleaned != null) {
                totals[cleaned] = (totals[cleaned] ?: 0) + stationCount
            }
        }
        totals
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (currentFilters.genre != null) {
            val displayGenre = formatGenreLabel(currentFilters.genre)
            val totalStations = aggregatedGenres[currentFilters.genre] ?: searchResults.size

            val showLoadMore = searchResults.isNotEmpty() &&
                (hasMoreResults || totalStations > searchResults.size)
            // Show stations for selected genre
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${displayGenre} (${totalStations})",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onClearSelection) {
                    Text("Back to Tags")
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Sort", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Most Liked") },
                        onClick = {
                            onSortOrderChange(SortOrder.MOST_LIKED)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Least Liked") },
                        onClick = {
                            onSortOrderChange(SortOrder.LEAST_LIKED)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Highest Bitrate") },
                        onClick = {
                            onSortOrderChange(SortOrder.BITRATE_DESC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Lowest Bitrate") },
                        onClick = {
                            onSortOrderChange(SortOrder.BITRATE_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("A-Z") },
                        onClick = {
                            onSortOrderChange(SortOrder.NAME_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Z-A") },
                        onClick = {
                            onSortOrderChange(SortOrder.NAME_DESC)
                            showSortMenu = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(searchResults) { station ->
                    val isCurrentlyPlaying = currentStation?.id == station.id && isPlaying
                    val isValidating = validatingStationId == station.id
                    val isCurrentlyBuffering = currentStation?.id == station.id && isBuffering
                    val isSaved = savedStationIds.contains(station.id)

                    StationCard(
                        station = station,
                        isPlaying = isCurrentlyPlaying,
                        isLoading = isValidating || isCurrentlyBuffering,
                        isSaved = isSaved,
                        onPlayClick = { onPlayClick(station) },
                        onSaveClick = { onSaveClick(station) },
                        allowIconActions = isSaved,
                        onVisitSite = onVisitSite,
                        onUpdateIcon = onUpdateIcon,
                        onRevertIcon = onRevertIcon,
                        onPickImage = onPickImage,
                        enableCardClick = true,
                        showExtendedInfo = true,
                        showBadgesInCard = true
                    )
                }

                if (showLoadMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            enabled = !isLoadingMore
                        ) {
                            Text(if (isLoadingMore) "Loading..." else "Load More")
                        }
                    }
                }
            }
        } else {
            // Show list of genres
            Text(
                text = "Select a Tag",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Search bar for filtering genres
            SearchBar(
                query = genreSearchQuery,
                onQueryChange = { genreSearchQuery = it },
                placeholder = "Search for tags...",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            val filteredGenres = aggregatedGenres
                .mapNotNull { (label, count) ->
                    if (count < 2) return@mapNotNull null
                    label to count
                }
                .let { entries ->
                    val filtered = if (genreSearchQuery.isBlank()) {
                        entries
                    } else {
                        entries.filter { (label, _) ->
                            label.contains(genreSearchQuery, ignoreCase = true)
                        }
                    }
                    filtered.sortedBy { it.first }
                }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredGenres) { (label, count) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGenreSelected(label) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatGenreLabel(label),
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Text(
                                text = "$count stations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}