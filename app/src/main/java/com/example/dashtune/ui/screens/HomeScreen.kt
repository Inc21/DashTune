package com.example.dashtune.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri

import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.ui.components.NowPlayingBar
import com.example.dashtune.ui.components.StationCard
import com.example.dashtune.ui.components.StationImage
import com.example.dashtune.ui.components.AudioBarsAnimation
import com.example.dashtune.ui.viewmodels.HomeViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import org.burnoutcrew.reorderable.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CompactTopBar(
    currentStation: RadioStation?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isSaved: Boolean,
    metadata: Pair<String?, String?>?,
    onPlayPauseClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSearchClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
    onDeveloperClick: () -> Unit,
    onBuyCoffeeClick: () -> Unit,
    onAudioLevelClick: () -> Unit
) {

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // DashTune branding
                Text(
                    text = "DashTune",
                    style = MaterialTheme.typography.titleLarge
                )
                
                // Now playing info (if station is active)
                if (currentStation != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box(modifier = Modifier.size(40.dp)) {
                        StationImage(
                            imageUrl = currentStation.imageUrl,
                            contentDescription = currentStation.name,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Show playing icon overlay when playing
                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AudioBarsAnimation(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = currentStation.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (metadata?.first != null || metadata?.second != null) {
                            val metadataText = when {
                                metadata.first != null && metadata.second != null -> 
                                    "${metadata.first} - ${metadata.second}"
                                metadata.first != null -> metadata.first!!
                                else -> metadata.second!!
                            }
                            Text(
                                text = metadataText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Play/Pause control
                    if (isBuffering) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp)
                        ) {
                            AudioBarsAnimation(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    } else {
                        IconButton(onClick = onPlayPauseClick) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        actions = {
            if (currentStation != null) {
                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "Remove from favorites" else "Add to favorites",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search stations"
                )
            }
            IconButton(onClick = { onMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
            OverflowMenu(
                expanded = menuExpanded,
                onExpandedChange = onMenuExpandedChange,
                onAboutClick = onAboutClick,
                onDeveloperClick = onDeveloperClick,
                onBuyCoffeeClick = onBuyCoffeeClick,
                onAudioLevelClick = onAudioLevelClick
            )
        }
    )
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
    onDeveloperClick: () -> Unit,
    onBuyCoffeeClick: () -> Unit,
    onAudioLevelClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) }
    ) {
        DropdownMenuItem(
            text = { Text("About DashTune") },
            onClick = {
                onExpandedChange(false)
                onAboutClick()
            }
        )
        DropdownMenuItem(
            text = { Text("About the Developer") },
            onClick = {
                onExpandedChange(false)
                onDeveloperClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Buy me a coffee") },
            onClick = {
                onExpandedChange(false)
                onBuyCoffeeClick()
            }
        )
        Divider()
        DropdownMenuItem(
            text = { Text("Audio leveling") },
            onClick = {
                onExpandedChange(false)
                onAudioLevelClick()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToSearch: () -> Unit
) {
    val savedStations by viewModel.savedStations.collectAsState()
    val currentStation by viewModel.currentStation.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val validatingStationId by viewModel.validatingStationId.collectAsState()
    val currentMetadata by viewModel.currentMetadata.collectAsState()
    val volumeMultiplier by viewModel.volumeMultiplier.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    
    var stationToRemove by remember { mutableStateOf<RadioStation?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    val openBuyCoffee = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/inc21"))
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            if (isLandscape) {
                // Landscape: Compact single-line top bar with DashTune, now playing, and search
                val isSaved = currentStation?.let { station ->
                    savedStations.any { it.id == station.id }
                } ?: false
                
                CompactTopBar(
                    currentStation = currentStation,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isSaved = isSaved,
                    metadata = currentMetadata,
                    onPlayPauseClick = {
                        currentStation?.let { viewModel.togglePlayback(it) }
                    },
                    onSaveClick = {
                        currentStation?.let { station ->
                            if (isSaved) {
                                stationToRemove = station
                            } else {
                                viewModel.toggleSave(station)
                            }
                        }
                    },
                    onSearchClick = onNavigateToSearch,
                    menuExpanded = menuExpanded,
                    onMenuExpandedChange = { menuExpanded = it },
                    onAboutClick = { showAboutDialog = true },
                    onDeveloperClick = { showDeveloperDialog = true },
                    onBuyCoffeeClick = openBuyCoffee,
                    onAudioLevelClick = { showAudioDialog = true }
                )
            } else {
                // Portrait: Standard top bar with DashTune and search
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    title = { Text("DashTune") },
                    actions = {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search stations"
                            )
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        OverflowMenu(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = it },
                            onAboutClick = { showAboutDialog = true },
                            onDeveloperClick = { showDeveloperDialog = true },
                            onBuyCoffeeClick = openBuyCoffee,
                            onAudioLevelClick = { showAudioDialog = true }
                        )
                    }
                )
            }
        },
        bottomBar = {
            // Portrait: Show now playing bar at bottom
            val station = currentStation
            val metadata = currentMetadata
            val isSaved = station?.let { current ->
                savedStations.any { it.id == current.id }
            } ?: false
            if (!isLandscape && station != null) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    NowPlayingBar(
                        station = station,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isSaved = isSaved,
                        metadata = metadata,
                        onPlayPauseClick = { viewModel.togglePlayback(station) },
                        onSaveClick = {
                            if (isSaved) {
                                stationToRemove = station
                            } else {
                                viewModel.toggleSave(station)
                            }
                        },
                        onBarClick = { viewModel.togglePlayback(station) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (savedStations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No saved stations yet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = onNavigateToSearch) {
                            Text("Search for stations")
                        }
                    }
                }
            } else {
                val reorderableState = rememberReorderableLazyGridState(
                    onMove = { from, to ->
                        // Create mutable list and swap items
                        val mutableList = savedStations.toMutableList()
                        val item = mutableList.removeAt(from.index)
                        mutableList.add(to.index, item)
                        // Update order in database
                        viewModel.updateStationOrder(mutableList)
                    }
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = reorderableState.gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.reorderable(reorderableState)
                ) {
                    items(
                        items = savedStations,
                        key = { it.id }
                    ) { station ->
                        val isCurrentlyPlaying = currentStation?.id == station.id && isPlaying
                        val isValidating = validatingStationId == station.id
                        val isCurrentlyBuffering = currentStation?.id == station.id && isBuffering
                        val stationIndex = savedStations.indexOf(station)
                        
                        ReorderableItem(reorderableState, key = station.id) { isDragging ->
                            StationCard(
                                station = station,
                                isPlaying = isCurrentlyPlaying,
                                isLoading = isValidating || isCurrentlyBuffering,
                                isSaved = true,
                                onPlayClick = { viewModel.togglePlayback(station) },
                                onSaveClick = { stationToRemove = station },
                                enableCardClick = true,
                                stationNumber = stationIndex + 1,
                                modifier = Modifier
                                    .detectReorderAfterLongPress(reorderableState)
                                    .graphicsLayer {
                                        scaleX = if (isDragging) 1.05f else 1f
                                        scaleY = if (isDragging) 1.05f else 1f
                                        alpha = if (isDragging) 0.8f else 1f
                                    }
                            )
                        }
                    }
                }
            }
        }
        
        // Confirmation dialog for removing favorite
        stationToRemove?.let { station ->
            AlertDialog(
                onDismissRequest = { stationToRemove = null },
                title = { Text("Remove from favorites?") },
                text = { Text("Are you sure you want to remove \"${station.name}\" from your favorites?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.toggleSave(station)
                            stationToRemove = null
                        }
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { stationToRemove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("About DashTune") },
                text = { Text("DashTune is an internet radio app for discovering and saving your favorite stations.") },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showDeveloperDialog) {
            AlertDialog(
                onDismissRequest = { showDeveloperDialog = false },
                title = { Text("About the Developer") },
                text = { Text("Built by an independent developer passionate about radio and great in-car experiences.") },
                confirmButton = {
                    TextButton(onClick = { showDeveloperDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Audio leveling") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Reduce loud stations by applying a volume multiplier.")
                        Slider(
                            value = volumeMultiplier,
                            onValueChange = { viewModel.setVolumeMultiplier(it) },
                            valueRange = 0.2f..1.0f
                        )
                        Text("Level: ${(volumeMultiplier * 100).toInt()}%")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}