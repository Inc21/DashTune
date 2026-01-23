package com.example.dashtune.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import com.example.dashtune.data.model.RadioStation
import java.util.*
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationCard(
    station: RadioStation,
    isPlaying: Boolean,
    isLoading: Boolean,
    isSaved: Boolean,
    onPlayClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableCardClick: Boolean = false,
    showExtendedInfo: Boolean = false,
    stationNumber: Int? = null
) {
    Card(
        modifier = modifier
            .aspectRatio(if (showExtendedInfo) 0.85f else 1f)
            .then(
                if (isPlaying) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (enableCardClick) {
                    Modifier.clickable { onPlayClick() }
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPlaying) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail Image - now a small square that doesn't take the full width
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 8.dp)
            ) {
                // Station number badge (top-left corner)
                stationNumber?.let { number ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(24.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 4.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = number.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                StationImage(
                    imageUrl = station.imageUrl,
                    contentDescription = station.name,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Overlay indicator for active state
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Station Name
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Country information
            if (station.country.isNotBlank()) {
                Text(
                    text = when {
                        station.country.length == 2 -> {
                            try {
                                val locale = Locale("", station.country.uppercase())
                                val displayCountry = locale.getDisplayCountry(Locale.getDefault())
                                // If display country returns the same as the code, it's not recognized
                                if (displayCountry.equals(station.country, ignoreCase = true)) {
                                    station.country
                                } else {
                                    displayCountry
                                }
                            } catch (e: Exception) {
                                station.country
                            }
                        }
                        else -> station.country
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Extended information (language, genre, bitrate, votes)
            if (showExtendedInfo) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (station.language.isNotBlank()) {
                        Text(
                            text = "Lang: ${station.language}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (station.tags.isNotEmpty()) {
                        Text(
                            text = station.tags.take(2).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (station.bitrate > 0) {
                            Text(
                                text = "${station.bitrate}kbps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        if (station.votes > 0) {
                            Text(
                                text = "♥ ${station.votes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Play/Loading Button
                IconButton(onClick = onPlayClick) {
                    if (isLoading) {
                        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Loading",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "Remove from favorites" else "Add to favorites",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }
    }
} 