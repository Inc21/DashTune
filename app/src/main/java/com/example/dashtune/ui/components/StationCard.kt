package com.example.dashtune.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Thumbnail Image with overlays
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 8.dp)
            ) {
                StationImage(
                    imageUrl = station.imageUrl,
                    contentDescription = station.name,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Center overlay for playing/loading state (moved before station number so number stays on top)
                if (isPlaying || isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
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
                                tint = Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer { rotationZ = rotation }
                            )
                        } else if (isPlaying) {
                            AudioBarsAnimation(
                                modifier = Modifier.size(32.dp),
                                color = Color.White
                            )
                        }
                    }
                }
                
                // Station number badge (top-left corner, rendered last so it stays on top)
                stationNumber?.let { number ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(26.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = MaterialTheme.shapes.small
                            ),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = number.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Station Name
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
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
            
            // Favorite Button (centered)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = if (isSaved || isPlaying) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "Remove from favorites" else "Add to favorites",
                        tint = if (isSaved || isPlaying) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }
    }
}