package com.example.dashtune.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.model.StationStatus
import com.example.dashtune.data.repository.RadioStationRepository
import com.example.dashtune.service.DashTuneMediaLibraryService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.MoreExecutors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stationRepository: RadioStationRepository
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var pendingStationId: String? = null

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentMetadata = MutableStateFlow<Pair<String?, String?>?>(null)
    val currentMetadata: StateFlow<Pair<String?, String?>?> = _currentMetadata.asStateFlow()

    // Derived state: atomically combine station + playing to prevent UI flicker
    val playingStationId: StateFlow<String?> = combine(
        _currentStation,
        _isPlaying
    ) { station, playing ->
        if (playing) station?.id else null
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    // Derived loading state: only false when actually playing
    // This prevents flicker from observing isBuffering and isPlaying separately
    val isLoadingStation: StateFlow<Boolean> = combine(
        _isBuffering,
        _isPlaying
    ) { buffering, playing ->
        // Simple: if playing, not loading. Otherwise, show loading if buffering.
        if (playing) false else buffering
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    private val _volumeMultiplier = MutableStateFlow(
        prefs.getFloat(KEY_VOLUME_MULTIPLIER, DEFAULT_VOLUME_MULTIPLIER)
    )
    val volumeMultiplier: StateFlow<Float> = _volumeMultiplier.asStateFlow()

    private val _openLinksInSpotify = MutableStateFlow(
        prefs.getBoolean(KEY_OPEN_LINKS_IN_SPOTIFY, DEFAULT_OPEN_LINKS_IN_SPOTIFY)
    )
    val openLinksInSpotify: StateFlow<Boolean> = _openLinksInSpotify.asStateFlow()

    private val sessionToken = SessionToken(
        context,
        android.content.ComponentName(context, DashTuneMediaLibraryService::class.java)
    )

    private val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
    private var controller: MediaController? = null

    init {
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get()
                    attachControllerListeners()
                    // Apply stored volume multiplier on connect
                    controller?.volume = _volumeMultiplier.value
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect MediaController", e)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun attachControllerListeners() {
        val c = controller ?: return
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying, pendingStationId=$pendingStationId, currentStation=${_currentStation.value?.name}")
                // During a station switch, Media3 can briefly emit isPlaying=true for the previous item.
                // Ignore interim callbacks until we have transitioned to the expected station.
                if (pendingStationId != null) {
                    Log.d(TAG, "  -> IGNORED (pending switch)")
                    return
                }

                _isPlaying.value = isPlaying
                if (isPlaying) {
                    _isBuffering.value = false
                }
                Log.d(TAG, "  -> Set _isPlaying=$isPlaying, _isBuffering=${_isBuffering.value}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "onPlaybackStateChanged: state=$stateName, pendingStationId=$pendingStationId")
                
                // Same idea as above: suppress transient buffering/ready flips during a pending switch.
                if (pendingStationId != null) {
                    Log.d(TAG, "  -> IGNORED (pending switch)")
                    return
                }

                when (playbackState) {
                    Player.STATE_BUFFERING -> _isBuffering.value = true
                    // Don't set isBuffering=false on READY - let onIsPlayingChanged handle it
                    // to avoid the brief moment where both isBuffering and isPlaying are false
                    Player.STATE_IDLE, Player.STATE_ENDED -> _isPlaying.value = false
                }
                Log.d(TAG, "  -> Set _isBuffering=${_isBuffering.value}, _isPlaying=${_isPlaying.value}")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _isPlaying.value = false
                _isBuffering.value = false
                Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
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

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                // Read stream metadata from extras (not from standard fields, which are reserved for AA)
                val extras = mediaMetadata.extras
                val title = extras?.getString("stream_song_title")
                val artist = extras?.getString("stream_song_artist")
                val allKeys = extras?.keySet()?.joinToString(", ") ?: "none"
                Log.d(TAG, "PlaybackManager.onMediaMetadataChanged: title=$title, artist=$artist, allKeys=[$allKeys]")
                if (title != null || artist != null) {
                    Log.d(TAG, "  -> Found metadata, setting to Pair($title, $artist)")
                } else {
                    Log.d(TAG, "  -> No metadata found in extras")
                }
                _currentMetadata.value = Pair(title, artist)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val reasonName = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    else -> "UNKNOWN($reason)"
                }
                Log.d(TAG, "onMediaItemTransition: reason=$reasonName, mediaId=${mediaItem?.mediaId}, pendingStationId=$pendingStationId")
                
                // Clear metadata when switching stations
                _currentMetadata.value = null
                
                // Keep currentStation in sync with queue changes (next/prev from AA or phone)
                val id = mediaItem?.mediaId?.removePrefix("station_") ?: return

                val pending = pendingStationId
                if (pending != null && pending != id) {
                    Log.d(TAG, "  -> IGNORED (wrong station, expected $pending)")
                    return
                }
                Log.d(TAG, "  -> Clearing pendingStationId, c.isPlaying=${c.isPlaying}")
                pendingStationId = null

                _isPlaying.value = c.isPlaying
                if (c.isPlaying) {
                    _isBuffering.value = false
                }

                scope.launch {
                    val stations = stationRepository.getSavedStations().first()
                    val station = stations.find { it.id == id } ?: return@launch
                    _currentStation.value = station
                }
            }
        })
    }

    fun togglePlayback(station: RadioStation) {
        val currentlyPlayingStation = _currentStation.value
        Log.d(TAG, "togglePlayback: station=${station.name}, currentStation=${currentlyPlayingStation?.name}, isPlaying=${_isPlaying.value}")

        if (currentlyPlayingStation?.id == station.id) {
            // Same station, toggle play/pause
            if (_isPlaying.value) {
                Log.d(TAG, "  -> Pausing current station")
                controller?.pause()
            } else {
                Log.d(TAG, "  -> Resuming current station")
                controller?.play()
            }
        } else {
            Log.d(TAG, "  -> Switching to new station: ${station.name}")
            // New station: prefer a playlist of saved stations (so next/prev stay in sync across AA + phone),
            // but if the tapped station isn't in saved stations (e.g. from search results), play it as a
            // single-item queue to avoid accidentally starting index 0.
            scope.launch {
                val stations = stationRepository.getSavedStations().first()
                val index = stations.indexOfFirst { it.id == station.id }

                Log.d(TAG, "  -> Setting pendingStationId=${station.id}, index=$index")
                pendingStationId = station.id
                _isBuffering.value = true
                _isPlaying.value = false
                _currentStation.value = station

                val c = controller
                if (c != null) {
                    if (index >= 0 && stations.isNotEmpty()) {
                        val items = stations.map {
                            MediaItem.Builder()
                                .setMediaId("station_${it.id}")
                                .setUri(it.streamUrl)
                                .build()
                        }
                        Log.d(TAG, "  -> Calling setMediaItems (playlist of ${items.size})")
                        c.setMediaItems(items, index, 0)
                    } else {
                        val item = MediaItem.Builder()
                            .setMediaId("station_${station.id}")
                            .setUri(station.streamUrl)
                            .build()
                        Log.d(TAG, "  -> Calling setMediaItem (single)")
                        c.setMediaItem(item)
                    }
                    Log.d(TAG, "  -> Calling prepare() and playWhenReady=true")
                    c.prepare()
                    c.playWhenReady = true
                } else {
                    Log.w(TAG, "MediaController not connected yet; cannot start playback")
                }
            }
        }
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun stopPlayback() {
        controller?.stop()
        _isPlaying.value = false
    }

    fun setVolumeMultiplier(value: Float) {
        val clamped = value.coerceIn(MIN_VOLUME_MULTIPLIER, MAX_VOLUME_MULTIPLIER)
        _volumeMultiplier.value = clamped
        prefs.edit().putFloat(KEY_VOLUME_MULTIPLIER, clamped).apply()
        controller?.volume = clamped
    }

    fun setOpenLinksInSpotify(enabled: Boolean) {
        _openLinksInSpotify.value = enabled
        prefs.edit().putBoolean(KEY_OPEN_LINKS_IN_SPOTIFY, enabled).apply()
    }

    fun release() {
        try {
            controller?.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "PlaybackManager"
        private const val PREFS_NAME = "dashtune_prefs"
        private const val KEY_VOLUME_MULTIPLIER = "volume_multiplier"
        private const val KEY_OPEN_LINKS_IN_SPOTIFY = "open_links_in_spotify"
        private const val DEFAULT_VOLUME_MULTIPLIER = 0.7f
        private const val DEFAULT_OPEN_LINKS_IN_SPOTIFY = false
        private const val MIN_VOLUME_MULTIPLIER = 0.05f
        private const val MAX_VOLUME_MULTIPLIER = 1.0f
    }
}