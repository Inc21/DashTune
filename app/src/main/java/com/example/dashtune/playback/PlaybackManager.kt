package com.example.dashtune.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.model.StationStatus
import com.example.dashtune.data.repository.RadioStationRepository
import com.example.dashtune.service.RadioPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stationRepository: RadioStationRepository
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentMetadata = MutableStateFlow<Pair<String?, String?>?>(null)
    val currentMetadata: StateFlow<Pair<String?, String?>?> = _currentMetadata.asStateFlow()

    private val _volumeMultiplier = MutableStateFlow(
        prefs.getFloat(KEY_VOLUME_MULTIPLIER, DEFAULT_VOLUME_MULTIPLIER)
    )
    val volumeMultiplier: StateFlow<Float> = _volumeMultiplier.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RadioPlayerService.RadioPlayerBinder
            playerService = binder.getService()

            // Apply saved volume multiplier when service connects
            playerService?.setVolumeMultiplier(_volumeMultiplier.value)

            // Set listeners for playback state changes
            playerService?.setPlaybackStateListener(object : RadioPlayerService.PlaybackStateListener {
                override fun onPlaybackStateChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        _isBuffering.value = false
                    }
                }

                override fun onBufferingStateChanged(isBuffering: Boolean) {
                    _isBuffering.value = isBuffering
                }

                override fun onError(errorMessage: String) {
                    // Handle playback errors
                    _isPlaying.value = false
                    _isBuffering.value = false
                    Log.e(TAG, "Playback error: $errorMessage")
                    // Update station status in repository if needed
                    _currentStation.value?.let { station ->
                        if (station.status != StationStatus.FAILED) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val updatedStation = station.copy(status = StationStatus.FAILED)
                                stationRepository.updateStation(updatedStation)
                                _currentStation.value = updatedStation
                            }
                        }
                    }
                }

                override fun onMetadataChanged(title: String?, artist: String?) {
                    _currentMetadata.value = Pair(title, artist)
                }
            })

            // If there's a pending station to play, play it now
            pendingStationToPlay?.let {
                playerService?.playStation(it)
                pendingStationToPlay = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
        }
    }

    private var playerService: RadioPlayerService? = null
    private var pendingStationToPlay: RadioStation? = null
    private var isBound = false

    init {
        bindToService()
    }

    private fun bindToService() {
        if (!isBound) {
            val intent = Intent(context, RadioPlayerService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isBound = true
        }
    }

    fun togglePlayback(station: RadioStation) {
        val currentlyPlayingStation = _currentStation.value

        if (currentlyPlayingStation?.id == station.id) {
            // Same station, toggle play/pause
            if (_isPlaying.value) {
                playerService?.pause()
            } else {
                playerService?.resume()
            }
        } else {
            // New station, stop current playback first
            if (currentlyPlayingStation != null) {
                playerService?.stop()
            }

            _currentStation.value = station
            _isBuffering.value = false

            if (playerService != null) {
                playerService?.playStation(station)
            } else {
                // Service not yet bound, store the station to play when bound
                pendingStationToPlay = station
                bindToService()
            }
        }
    }

    fun stopPlayback() {
        playerService?.stop()
        _isPlaying.value = false
    }

    fun setVolumeMultiplier(value: Float) {
        val clamped = value.coerceIn(MIN_VOLUME_MULTIPLIER, MAX_VOLUME_MULTIPLIER)
        _volumeMultiplier.value = clamped
        prefs.edit().putFloat(KEY_VOLUME_MULTIPLIER, clamped).apply()
        playerService?.setVolumeMultiplier(clamped)
    }

    fun release() {
        if (isBound) {
            playerService?.let {
                it.stop()
                context.unbindService(serviceConnection)
            }
            isBound = false
        }
    }

    companion object {
        private const val TAG = "PlaybackManager"
        private const val PREFS_NAME = "dashtune_prefs"
        private const val KEY_VOLUME_MULTIPLIER = "volume_multiplier"
        private const val DEFAULT_VOLUME_MULTIPLIER = 0.7f
        private const val MIN_VOLUME_MULTIPLIER = 0.2f
        private const val MAX_VOLUME_MULTIPLIER = 1.0f
    }
}