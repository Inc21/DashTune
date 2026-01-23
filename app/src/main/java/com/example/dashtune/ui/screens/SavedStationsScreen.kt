package com.example.dashtune.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dashtune.ui.components.StationCard
import com.example.dashtune.ui.viewmodels.SavedStationsViewModel

@Composable
fun SavedStationsScreen(
    viewModel: SavedStationsViewModel = hiltViewModel()
) {
    val savedStations by viewModel.savedStations.collectAsState()
    val currentStation by viewModel.currentStation.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Saved Stations",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (savedStations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved stations yet",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = savedStations,
                    key = { it.id }
                ) { station ->
                    val isCurrentlyPlaying = currentStation?.id == station.id && isPlaying
                    
                    StationCard(
                        station = station,
                        isPlaying = isCurrentlyPlaying,
                        isLoading = false,
                        isSaved = true,
                        onPlayClick = { viewModel.togglePlayback(station) },
                        onSaveClick = { viewModel.deleteStation(station) }
                    )
                }
            }
        }
    }
} 