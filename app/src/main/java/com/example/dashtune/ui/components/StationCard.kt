package com.example.dashtune.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.model.StationTagHelper
import java.util.*

import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    showBadgesInCard: Boolean = false,
    stationNumber: Int? = null,
    showMenu: Boolean = true,
    allowIconActions: Boolean = true,
    onVisitSite: (RadioStation) -> Unit = {},
    onUpdateIcon: (RadioStation) -> Unit = {},
    onRevertIcon: (RadioStation) -> Unit = {},
    onPickImage: (RadioStation) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRevertConfirmation by remember { mutableStateOf(false) }
    val badges = if (showExtendedInfo) {
        val genres = StationTagHelper.extractGenres(station.tags)
        buildList {
            if (station.votes > 0) add("♥ ${station.votes}")
            if (station.bitrate > 0) add("${station.bitrate}kbps")
            addAll(genres.take(2))
        }.take(3)
    } else {
        emptyList()
    }

    Card(
        modifier = modifier
            .aspectRatio(if (showBadgesInCard) 0.75f else 1f)
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                ) {
                    Box(modifier = Modifier.size(80.dp)) {
                        StationImage(
                            imageUrl = station.imageUrl,
                            contentDescription = station.name,
                            modifier = Modifier.fillMaxSize()
                        )
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
                                } else {
                                    AudioBarsAnimation(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
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
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (station.country.isNotBlank()) {
                    Text(
                        text = when {
                            station.country.length == 2 -> {
                                try {
                                    val locale = Locale("", station.country.uppercase())
                                    val displayCountry = locale.getDisplayCountry(Locale.getDefault())
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
                if (showBadgesInCard && badges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        badges.forEach { label ->
                            StationInfoBadge(label = label)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onSaveClick) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isSaved) "Remove from favorites" else "Add to favorites",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            }
            if (showMenu) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Station options"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = station.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                enabled = false,
                                onClick = {}
                            )
                            Divider()
                            if (badges.isNotEmpty() && !showBadgesInCard) {
                                DropdownMenuItem(
                                    text = {
                                        FlowRow(
                                            modifier = Modifier.widthIn(max = 160.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            badges.forEach { label ->
                                                StationInfoBadge(label = label)
                                            }
                                        }
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                                Divider()
                            }
                            DropdownMenuItem(
                                text = { Text("Visit site") },
                                enabled = station.websiteUrl.isNotBlank(),
                                onClick = {
                                    menuExpanded = false
                                    onVisitSite(station)
                                }
                            )
                            if (isSaved) {
                                DropdownMenuItem(
                                    text = { Text("Choose image") },
                                    enabled = allowIconActions,
                                    onClick = {
                                        menuExpanded = false
                                        onPickImage(station)
                                    }
                                )
                                if (station.isIconOverridden) {
                                    DropdownMenuItem(
                                        text = { Text("Revert icon") },
                                        enabled = allowIconActions,
                                        onClick = {
                                            menuExpanded = false
                                            showRevertConfirmation = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Fetch favicon") },
                                        enabled = allowIconActions,
                                        onClick = {
                                            menuExpanded = false
                                            onUpdateIcon(station)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showRevertConfirmation) {
        AlertDialog(
            onDismissRequest = { showRevertConfirmation = false },
            title = { Text("Revert to original icon?") },
            text = { Text("This will delete your custom photo and restore the station's original icon.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevertConfirmation = false
                        onRevertIcon(station)
                    }
                ) {
                    Text("Revert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevertConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StationInfoBadge(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}