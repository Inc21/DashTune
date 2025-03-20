package com.example.radioapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.radioapp.service.RadioPlayerService
import com.example.radioapp.ui.screens.SavedStationsScreen
import com.example.radioapp.ui.screens.SearchScreen
import com.example.radioapp.ui.theme.RadioAppTheme
import com.example.radioapp.ui.viewmodel.RadioViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var radioService: RadioPlayerService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            radioService = (service as? RadioPlayerService.LocalBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to the service
        bindService(
            Intent(this, RadioPlayerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        setContent {
            RadioAppTheme {
                val navController = rememberNavController()
                val viewModel: RadioViewModel = hiltViewModel()
                
                val savedStations by viewModel.savedStations.collectAsState()
                val searchResults by viewModel.searchResults.collectAsState()
                val currentlyPlaying by viewModel.currentlyPlaying.collectAsState()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Favorite, "Saved Stations") },
                                label = { Text("My Stations") },
                                selected = currentRoute == "saved",
                                onClick = { navController.navigate("saved") }
                            )
                            
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Search, "Search") },
                                label = { Text("Search") },
                                selected = currentRoute == "search",
                                onClick = { navController.navigate("search") }
                            )
                        }
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "saved",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("saved") {
                            SavedStationsScreen(
                                stations = savedStations,
                                onPlayPause = { station ->
                                    if (station.isPlaying) {
                                        viewModel.stopPlayback()
                                        stopService(Intent(this@MainActivity, RadioPlayerService::class.java))
                                    } else {
                                        viewModel.playStation(station)
                                        val intent = Intent(this@MainActivity, RadioPlayerService::class.java)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                        radioService?.playStation(station.streamUrl, station.name)
                                    }
                                },
                                onRemoveStation = viewModel::removeStation,
                                onReorder = viewModel::updateStationOrder
                            )
                        }
                        
                        composable("search") {
                            SearchScreen(
                                searchResults = searchResults,
                                onSearch = viewModel::searchStations,
                                onPlayPause = { station ->
                                    if (station.isPlaying) {
                                        viewModel.stopPlayback()
                                        stopService(Intent(this@MainActivity, RadioPlayerService::class.java))
                                    } else {
                                        viewModel.playStation(station)
                                        val intent = Intent(this@MainActivity, RadioPlayerService::class.java)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                        radioService?.playStation(station.streamUrl, station.name)
                                    }
                                },
                                onSaveStation = viewModel::saveStation
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        stopService(Intent(this, RadioPlayerService::class.java))
    }
}