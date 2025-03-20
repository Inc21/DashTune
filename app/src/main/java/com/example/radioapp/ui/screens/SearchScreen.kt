package com.example.radioapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.radioapp.data.model.RadioStation
import com.example.radioapp.ui.components.StationCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchResults: List<RadioStation>,
    onSearch: (String) -> Unit,
    onPlayPause: (RadioStation) -> Unit,
    onSaveStation: (RadioStation) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { 
                searchQuery = it
                onSearch(it)
            },
            onSearch = { onSearch(it) },
            active = false,
            onActiveChange = {},
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            placeholder = { Text("Search radio stations...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {}
        
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = searchResults,
                key = { it.id }
            ) { station ->
                StationCard(
                    station = station,
                    onPlayPause = { onPlayPause(station) },
                    onSaveToggle = { onSaveStation(station) }
                )
            }
        }
    }
} 