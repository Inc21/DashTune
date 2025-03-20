package com.example.radioapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.radioapp.data.model.RadioStation
import com.example.radioapp.ui.components.StationCard
import org.burnoutcrew.reorderable.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedStationsScreen(
    stations: List<RadioStation>,
    onPlayPause: (RadioStation) -> Unit,
    onRemoveStation: (RadioStation) -> Unit,
    onReorder: (List<RadioStation>) -> Unit
) {
    val state = rememberReorderableLazyGridState(
        onMove = { from, to ->
            val fromIndex = from.index
            val toIndex = to.index
            val newList = stations.toMutableList()
            newList[fromIndex] = stations[toIndex]
            newList[toIndex] = stations[fromIndex]
            onReorder(newList)
        }
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = state.gridState
    ) {
        items(
            items = stations,
            key = { it.id }
        ) { station ->
            ReorderableItem(state, key = station.id) { isDragging ->
                StationCard(
                    station = station,
                    onPlayPause = { onPlayPause(station) },
                    onSaveToggle = { onRemoveStation(station) },
                    modifier = Modifier
                        .reorderable(state)
                        .animateItemPlacement()
                )
            }
        }
    }
} 