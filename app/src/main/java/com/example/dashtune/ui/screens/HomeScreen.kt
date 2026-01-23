package com.example.dashtune.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.ui.components.StationCard
import com.example.dashtune.ui.viewmodels.HomeViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import org.burnoutcrew.reorderable.*

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
    
    var stationToRemove by remember { mutableStateOf<RadioStation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Stations") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search stations"
                        )
                    }
                }
            )
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = savedStations,
                        key = { it.id }
                    ) { station ->
                        val isCurrentlyPlaying = currentStation?.id == station.id && isPlaying
                        val isValidating = validatingStationId == station.id
                        val isCurrentlyBuffering = currentStation?.id == station.id && isBuffering
                        val stationIndex = savedStations.indexOf(station)
                        
                        StationCard(
                            station = station,
                            isPlaying = isCurrentlyPlaying,
                            isLoading = isValidating || isCurrentlyBuffering,
                            isSaved = true,
                            onPlayClick = { viewModel.togglePlayback(station) },
                            onSaveClick = { stationToRemove = station },
                            enableCardClick = true,
                            stationNumber = stationIndex + 1
                        )
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
    }
}