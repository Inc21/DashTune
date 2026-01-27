package com.example.dashtune.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dashtune.R
import com.example.dashtune.ui.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val volumeMultiplier by viewModel.volumeMultiplier.collectAsState()
    val openLinksInSpotify by viewModel.openLinksInSpotify.collectAsState()
    val eqPreset by viewModel.eqPreset.collectAsState()
    val isSleepTimerActive by viewModel.isSleepTimerActive.collectAsState()
    val sleepTimerRemainingMinutes by viewModel.sleepTimerRemainingMinutes.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Settings Section
            SettingsSection(
                volumeMultiplier = volumeMultiplier,
                onVolumeMultiplierChange = { viewModel.setVolumeMultiplier(it) },
                openLinksInSpotify = openLinksInSpotify,
                onOpenLinksInSpotifyChange = { viewModel.setOpenLinksInSpotify(it) },
                eqPreset = eqPreset,
                onEqPresetChange = { viewModel.setEqPreset(it) },
                isSleepTimerActive = isSleepTimerActive,
                sleepTimerRemainingMinutes = sleepTimerRemainingMinutes,
                onStartSleepTimer = { minutes -> viewModel.startSleepTimer(minutes) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() }
            )
            
            Divider()
            
            // About Developer Section
            AboutDeveloperSection(
                onWebsiteClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.inc21.dev/"))
                    context.startActivity(intent)
                },
                onBuyCoffeeClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/inc21"))
                    context.startActivity(intent)
                }
            )
            
            Divider()
            
            // About App Section
            AboutAppSection()
        }
    }
}

@Composable
private fun SettingsSection(
    volumeMultiplier: Float,
    onVolumeMultiplierChange: (Float) -> Unit,
    openLinksInSpotify: Boolean,
    onOpenLinksInSpotifyChange: (Boolean) -> Unit,
    eqPreset: String,
    onEqPresetChange: (String) -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerRemainingMinutes: Int,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    var showTimerDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Audio Leveling
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Audio Leveling",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Reduce loud stations by applying a volume multiplier",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Slider(
                    value = volumeMultiplier,
                    onValueChange = onVolumeMultiplierChange,
                    valueRange = 0.05f..1.0f
                )
                Text(
                    text = "Level: ${(volumeMultiplier * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        // Equalizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEqDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = eqPreset.ifBlank { "Off" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Links
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open track artist/title in Spotify",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "If installed: Spotify. If not: Google.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = openLinksInSpotify,
                    onCheckedChange = onOpenLinksInSpotifyChange
                )
            }
        }
        
        // Sleep Timer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Automatically stop playback after a set duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                if (isSleepTimerActive) {
                    Text(
                        text = "Timer active: $sleepTimerRemainingMinutes minutes remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = onCancelSleepTimer,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Cancel Timer")
                    }
                } else {
                    Button(
                        onClick = { showTimerDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set Sleep Timer")
                    }
                }
            }
        }
    }
    
    if (showTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showTimerDialog = false },
            onSetTimer = { minutes ->
                onStartSleepTimer(minutes)
                showTimerDialog = false
            }
        )
    }

    if (showEqDialog) {
        val presets = listOf(
            "Off",
            "Normal",
            "Bass Boost",
            "Treble Boost",
            "Vocal",
            "Rock",
            "Pop"
        )

        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            title = { Text("Equalizer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onEqPresetChange(preset)
                                    showEqDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = eqPreset == preset,
                                onClick = {
                                    onEqPresetChange(preset)
                                    showEqDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(preset)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEqDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit
) {
    val timerOptions = listOf(15, 30, 45, 60, 90, 120)
    var showCustomInput by remember { mutableStateOf(false) }
    var customHours by remember { mutableStateOf("") }
    var customMinutes by remember { mutableStateOf("") }
    
    if (showCustomInput) {
        val totalMinutes = (customHours.toIntOrNull() ?: 0) * 60 + (customMinutes.toIntOrNull() ?: 0)
        
        AlertDialog(
            onDismissRequest = { showCustomInput = false },
            title = { Text("Custom Duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter duration:")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customHours,
                            onValueChange = { customHours = it.filter { char -> char.isDigit() } },
                            label = { Text("Hours") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(":")
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { 
                                val filtered = it.filter { char -> char.isDigit() }
                                if (filtered.isEmpty() || filtered.toInt() < 60) {
                                    customMinutes = filtered
                                }
                            },
                            label = { Text("Minutes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (totalMinutes > 0) {
                        Text(
                            text = "Total: $totalMinutes minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (totalMinutes > 0) {
                            onSetTimer(totalMinutes)
                            showCustomInput = false
                        }
                    },
                    enabled = totalMinutes > 0
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomInput = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Set Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select duration:")
                    timerOptions.forEach { minutes ->
                        Button(
                            onClick = { onSetTimer(minutes) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$minutes minutes")
                        }
                    }
                    OutlinedButton(
                        onClick = { showCustomInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Custom duration...")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AboutDeveloperSection(
    onWebsiteClick: () -> Unit,
    onBuyCoffeeClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "About Developer",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Developer Logo
                Image(
                    painter = painterResource(id = R.drawable.logo_round),
                    contentDescription = "inc21 logo",
                    modifier = Modifier.size(80.dp)
                )
                
                Text(
                    text = "Developed by inc21",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                // Website Link
                Text(
                    text = "www.inc21.dev",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onWebsiteClick() }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "I'm a single developer creating these apps without any support. If you find DashTune useful, please consider supporting my efforts by buying me a coffee. Every little helps, and you can start with just €1!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Buy Me a Coffee Button
                Button(
                    onClick = onBuyCoffeeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("☕ Buy me a coffee")
                }
            }
        }
    }
}

@Composable
private fun AboutAppSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "About DashTune",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Version 1.0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "DashTune is completely free and designed to be simple and safer to use than other radio apps out there.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "Features:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "• Ad-free experience\n• No user tracking or data collection\n• Android Auto support\n• Access to thousands of radio stations worldwide",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Data Source:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "DashTune uses the radio-browser.info API, a user-generated database of radio stations. This means the station list is community-maintained and constantly growing.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Android Auto Limitations:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Please note that Android Auto has strict limitations that cannot be modified by third-party apps. Some features available in the phone app may not be accessible while using Android Auto.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Important Notice:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "While DashTune is ad-free, some radio stations may include ads in their streams. These are added by the stations themselves and are beyond our control.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Privacy:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "DashTune does not track, collect, or save any user data online. Your listening preferences and saved stations are stored locally on your device only.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
