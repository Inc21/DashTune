package com.example.dashtune.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dashtune.data.model.RadioStation

@Composable
fun NowPlayingBar(
    station: RadioStation?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isSaved: Boolean,
    metadata: Pair<String?, String?>?,
    onPlayPauseClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBarClick: () -> Unit,
    onMetadataClick: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = station != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        station?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Station thumbnail with playing indicator
                    Box(modifier = Modifier.size(48.dp)) {
                        StationImage(
                            imageUrl = station.imageUrl,
                            contentDescription = station.name,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isPlaying && !isBuffering) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AudioBarsAnimation(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Station info and metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Station name
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Live metadata (song - artist) or country
                        if (metadata?.first != null || metadata?.second != null) {
                            val metadataText = when {
                                metadata.first != null && metadata.second != null -> 
                                    "${metadata.first} - ${metadata.second}"
                                metadata.first != null -> metadata.first!!
                                else -> metadata.second!!
                            }
                            val hasAnyMetadata = !metadata.first.isNullOrBlank() || !metadata.second.isNullOrBlank()
                            Text(
                                text = metadataText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = if (hasAnyMetadata && onMetadataClick != null) {
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple()
                                    ) { 
                                        onMetadataClick(metadata.first.orEmpty(), metadata.second.orEmpty())
                                    }
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                            )
                        } else if (station.country.isNotBlank()) {
                            Text(
                                text = station.country,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Save + Play/Pause buttons
                    IconButton(onClick = onSaveClick) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isSaved) "Remove from favorites" else "Add to favorites",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isBuffering) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp)
                        ) {
                            AudioBarsAnimation(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (isPlaying) {
                        IconButton(onClick = onPlayPauseClick) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = onPlayPauseClick) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
