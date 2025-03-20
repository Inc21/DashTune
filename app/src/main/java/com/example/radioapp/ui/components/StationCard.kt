package com.example.radioapp.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.radioapp.data.model.RadioStation

@Composable
fun StationCard(
    station: RadioStation,
    onPlayPause: () -> Unit,
    onSaveToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .then(
                if (station.isPlaying) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                if (station.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = station.imageUrl,
                        contentDescription = "${station.name} logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            // Fallback for failed image loads
                            DefaultStationImage()
                        }
                    )
                } else {
                    // Default image when no URL is provided
                    DefaultStationImage()
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (station.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (station.isPlaying) "Pause" else "Play"
                    )
                }
                
                IconButton(onClick = onSaveToggle) {
                    Icon(
                        imageVector = if (station.isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (station.isSaved) "Remove from favorites" else "Add to favorites"
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultStationImage() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Icon(
            imageVector = Icons.Default.Radio,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
} 