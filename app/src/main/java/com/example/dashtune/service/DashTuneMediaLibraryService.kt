package com.example.dashtune.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommands
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.repository.RadioStationRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class DashTuneMediaLibraryService : MediaLibraryService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DashTuneMediaLibraryServiceEntryPoint {
        fun radioStationRepository(): RadioStationRepository
    }

    private lateinit var repository: RadioStationRepository
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var player: androidx.media3.exoplayer.ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var pausedByUserOrSystem: Boolean = false

    private lateinit var prefs: SharedPreferences
    private var equalizer: Equalizer? = null
    private var equalizerAudioSessionId: Int = 0

    private var volumeMultiplier: Float = 1.0f

    private var fadeInPending: Boolean = false
    private var fadeInToken: Int = 0
    private var fadeInTargetVolume: Float = 1.0f
    private var lastNonZeroPlayerVolume: Float = 1.0f

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_EQ_PRESET) {
            applyEqPreset(prefs.getString(KEY_EQ_PRESET, DEFAULT_EQ_PRESET).orEmpty())
        }

        if (key == KEY_VOLUME_MULTIPLIER) {
            volumeMultiplier = prefs.getFloat(KEY_VOLUME_MULTIPLIER, DEFAULT_VOLUME_MULTIPLIER)
            if (::player.isInitialized) {
                player.volume = volumeMultiplier
            }
            if (volumeMultiplier > 0f) {
                lastNonZeroPlayerVolume = volumeMultiplier
            }
        }
    }

    private fun armTransitionMute() {
        // Mute as early as possible to avoid pops/clicks when switching between live streams.
        // (The tear-down of the previous decoder/output can produce a short transient.)
        fadeInPending = true
        fadeInToken += 1

        fadeInTargetVolume = volumeMultiplier.coerceAtLeast(0f)
        // Mute volume FIRST, then pause
        player.volume = 0f
        player.playWhenReady = false
        player.pause()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MediaLibraryService onCreate() started")

        try {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)

            volumeMultiplier = prefs.getFloat(KEY_VOLUME_MULTIPLIER, DEFAULT_VOLUME_MULTIPLIER)
            lastNonZeroPlayerVolume = volumeMultiplier

            // Get repository using Hilt EntryPoint
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                DashTuneMediaLibraryServiceEntryPoint::class.java
            )
            repository = entryPoint.radioStationRepository()
            Log.d(TAG, "Repository initialized")

            // Create player
            player = createPlayer()
            Log.d(TAG, "Player created")

            player.volume = volumeMultiplier

            applyEqPreset(prefs.getString(KEY_EQ_PRESET, DEFAULT_EQ_PRESET).orEmpty())

            // Create MediaLibrarySession
            mediaLibrarySession = MediaLibrarySession.Builder(
                this,
                player,
                object : MediaLibrarySession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    Log.d(TAG, "onConnect called from ${controller.packageName} (uid=${controller.uid})")
                    
                    // Accept with default commands - simplest approach for Android Auto compatibility
                    Log.d(TAG, "Accepting connection with AcceptedResultBuilder")
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    Log.d(TAG, "onGetLibraryRoot caller=${browser.packageName}")
                    
                    // Set content style hints for grid display
                    val rootExtras = Bundle().apply {
                        putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
                        putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID)
                    }
                    val rootParams = LibraryParams.Builder()
                        .setExtras(rootExtras)
                        .build()
                    
                    return Futures.immediateFuture(
                        LibraryResult.ofItem(
                            MediaItem.Builder()
                                .setMediaId(ROOT_ID)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setTitle("DashTune")
                                        .setExtras(rootExtras)
                                        .build()
                                )
                                .build(),
                            rootParams
                        )
                    )
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    Log.d(TAG, "onGetChildren parentId=$parentId page=$page pageSize=$pageSize")
                    val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        try {
                            val result = when (parentId) {
                                ROOT_ID -> {
                                    // Return "My Stations" category with grid hint
                                    val categoryExtras = Bundle().apply {
                                        putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
                                        putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID)
                                    }
                                    val items = ImmutableList.of(
                                        MediaItem.Builder()
                                            .setMediaId(FAVORITES_ID)
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setIsBrowsable(true)
                                                    .setIsPlayable(false)
                                                    .setTitle("DashTune")
                                                    .setSubtitle("Your favorite radio stations")
                                                    .setExtras(categoryExtras)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    LibraryResult.ofItemList(items, params)
                                }
                                
                                FAVORITES_ID -> {
                                    // Return favorite stations
                                    val stations = repository.getSavedStations().first()
                                    Log.d(TAG, "Favorites count=${stations.size}")
                                    if (stations.isEmpty()) {
                                        val emptyItem = MediaItem.Builder()
                                            .setMediaId("no_stations")
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setIsBrowsable(false)
                                                    .setIsPlayable(false)
                                                    .setTitle("No stations saved")
                                                    .setSubtitle("Add stations on your phone to see them here")
                                                    .build()
                                            )
                                            .build()
                                        LibraryResult.ofItemList(ImmutableList.of(emptyItem), params)
                                    } else {
                                        val immutableItems: ImmutableList<MediaItem> =
                                            ImmutableList.copyOf(buildStationItems(stations))
                                        LibraryResult.ofItemList(immutableItems, params)
                                    }
                                }
                                
                                else -> {
                                    LibraryResult.ofItemList(ImmutableList.of(), params)
                                }
                            }
                            future.set(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onGetChildren", e)
                            future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                        }
                    }
                    return future
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    Log.d(TAG, "onGetItem mediaId=$mediaId")
                    val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<MediaItem>>()
                    serviceScope.launch {
                        try {
                            val result = if (mediaId.startsWith("station_")) {
                                val stationId = mediaId.removePrefix("station_")
                                val stations = repository.getSavedStations().first()
                                val station = stations.find { it.id == stationId }
                                
                                if (station != null) {
                                    val index = stations.indexOf(station)
                                    val stationNumber = index + 1
                                    val item = buildStationItem(station, stationNumber)
                                    LibraryResult.ofItem(item, null)
                                } else {
                                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                                }
                            } else {
                                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                            }
                            future.set(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onGetItem", e)
                            future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                        }
                    }
                    return future
                }

                override fun onSetMediaItems(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long
                ): ListenableFuture<MediaItemsWithStartPosition> {
                    Log.d(TAG, "onSetMediaItems called with ${mediaItems.size} items")
                    val future = com.google.common.util.concurrent.SettableFuture.create<MediaItemsWithStartPosition>()
                    serviceScope.launch {
                        try {
                            armTransitionMute()

                            if (controller.packageName == applicationContext.packageName) {
                                val resolvedIndex = startIndex.coerceIn(0, mediaItems.lastIndex.coerceAtLeast(0))
                                future.set(MediaItemsWithStartPosition(mediaItems, resolvedIndex, startPositionMs))
                                return@launch
                            }

                            val stations = repository.getSavedStations().first()
                            val items = buildStationItems(stations)
                            val requested = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
                            val requestedId = requested?.mediaId?.takeIf { it.isNotBlank() }
                            val requestedUri = requested?.localConfiguration?.uri

                            val resolvedIndex = when {
                                requestedId != null -> items.indexOfFirst { it.mediaId == requestedId }
                                requestedUri != null -> items.indexOfFirst { it.localConfiguration?.uri == requestedUri }
                                else -> startIndex
                            }.takeIf { it >= 0 } ?: startIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))

                            future.set(MediaItemsWithStartPosition(items, resolvedIndex, startPositionMs))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onSetMediaItems", e)
                            future.setException(e)
                        }
                    }
                    return future
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    // Track if we were paused by AA/car so we can rebuffer on resume.
                    // Some Media3 versions expose play/pause as a single COMMAND_PLAY_PAUSE.
                    if (playerCommand == Player.COMMAND_PLAY_PAUSE) {
                        if (player.isPlaying) {
                            pausedByUserOrSystem = true
                            return super.onPlayerCommandRequest(session, controller, playerCommand)
                        }

                        if (pausedByUserOrSystem) {
                            pausedByUserOrSystem = false
                            val index = player.currentMediaItemIndex
                            Log.d(TAG, "Rebuffering on resume (index=$index)")
                            player.seekToDefaultPosition(index)
                            player.prepare()
                            player.playWhenReady = true
                            return SessionResult.RESULT_SUCCESS
                        }
                    }

                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }
        ).build()
            
            Log.d(TAG, "MediaLibrarySession built successfully")
            
            // Start as foreground service to ensure Android Auto can connect
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Started as foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Failed to initialize MediaLibraryService", e)
            throw e
        }
    }
    
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DashTune")
            .setContentText("Media service running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "DashTune media playback service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createPlayer(): androidx.media3.exoplayer.ExoPlayer {
        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("DashTune/1.0")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Reduce buffer sizes for better performance
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // min buffer (default 50s, reduced to 15s)
                30000,  // max buffer (default 50s, reduced to 30s)
                2500,   // playback buffer (default 2.5s)
                5000    // playback after rebuffer (default 5s)
            )
            .build()

        return androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .build()
            .apply {
                // Avoid auto-advancing through the station list on transient stream issues.
                // Treat playback like a single live stream by repeating the current item only.
                repeatMode = Player.REPEAT_MODE_ONE

                // Listen for stream metadata changes to update now playing display
                addListener(object : Player.Listener {
                    override fun onVolumeChanged(volume: Float) {
                        if (volume > 0f) {
                            lastNonZeroPlayerVolume = volume
                        }
                    }
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        // Recreate EQ for the new audio session.
                        applyEqPreset(prefs.getString(KEY_EQ_PRESET, DEFAULT_EQ_PRESET).orEmpty())
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // Some streams transiently fail (timeouts / chunked transfer / 404 during ad markers).
                        // If AA treats this as end-of-item it may auto-advance to the next station.
                        // Retry the current station instead.
                        val index = currentMediaItemIndex
                        Log.w(TAG, "Player error; retrying current item (index=$index code=${error.errorCodeName})", error)

                        handler.postDelayed({
                            try {
                                seekToDefaultPosition(index)
                                prepare()
                                playWhenReady = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Retry failed (index=$index)", e)
                            }
                        }, 1500)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            // Live streams should not "end"; when they do, restart current item instead
                            // of letting the playlist advance.
                            val index = currentMediaItemIndex
                            Log.w(TAG, "Playback ended unexpectedly; restarting (index=$index)")
                            handler.post {
                                try {
                                    seekToDefaultPosition(index)
                                    prepare()
                                    playWhenReady = true
                                } catch (e: Exception) {
                                    Log.e(TAG, "Restart after ended failed (index=$index)", e)
                                }
                            }
                        }

                        if (playbackState == Player.STATE_READY && playWhenReady && fadeInPending) {
                            fadeInPending = false
                            startFadeIn(fadeInToken, fadeInTargetVolume)
                        }

                        if (playbackState == Player.STATE_READY && !playWhenReady && fadeInPending) {
                            // We paused during the transition to avoid pops. Resume once ready, then fade in.
                            fadeInPending = false
                            playWhenReady = true
                            startFadeIn(fadeInToken, fadeInTargetVolume)
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        Log.w(TAG, "Media item transition: reason=$reason mediaId=${mediaItem?.mediaId}")
                        // Muting is already armed in onSetMediaItems; keep this as a fallback.
                        if (!fadeInPending) {
                            armTransitionMute()
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        val streamTitle = mediaMetadata.title?.toString()?.trim()
                        val streamArtist = mediaMetadata.artist?.toString()?.trim()
                        Log.d(TAG, "Stream metadata: title=$streamTitle, artist=$streamArtist")
                        
                        val currentItem = currentMediaItem ?: return
                        val stationName = currentItem.mediaMetadata.extras?.getString("station_name")
                            ?: currentItem.mediaMetadata.title?.toString().orEmpty()
                        
                        // Parse song info - many streams use "Artist - Title" format
                        var songArtist: String? = null
                        var songTitle: String? = null
                        
                        // Check if streamTitle contains " - " pattern (Artist - Song)
                        val rawMetadata = streamTitle ?: streamArtist
                        if (rawMetadata != null && rawMetadata.contains(" - ")) {
                            val parts = rawMetadata.split(" - ", limit = 2)
                            if (parts.size == 2) {
                                songArtist = parts[0].trim()
                                songTitle = parts[1].trim()
                            }
                        } else if (streamArtist != null && streamTitle != null && 
                                   streamArtist != streamTitle) {
                            // Different artist and title provided
                            songArtist = streamArtist
                            songTitle = streamTitle
                        }
                        
                        // Only update if we have real song info that's different from station name
                        val hasSongInfo = (songArtist != null || songTitle != null) &&
                            !songArtist.equals(stationName, ignoreCase = true) &&
                            !songTitle.equals(stationName, ignoreCase = true) &&
                            songArtist?.length ?: 0 < 100 && // Skip overly long metadata (station descriptions)
                            songTitle?.length ?: 0 < 100

                        val existingExtras = currentItem.mediaMetadata.extras
                        val existingArtist = existingExtras?.getString("stream_song_artist")
                        val existingTitle = existingExtras?.getString("stream_song_title")

                        val desiredArtist = if (hasSongInfo) songArtist else null
                        val desiredTitle = if (hasSongInfo) songTitle else null

                        if (existingArtist == desiredArtist && existingTitle == desiredTitle) {
                            return
                        }
                        
                        val updatedExtras = Bundle(currentItem.mediaMetadata.extras ?: Bundle()).apply {
                            if (hasSongInfo) {
                                putString("stream_song_artist", songArtist)
                                putString("stream_song_title", songTitle)
                            } else {
                                remove("stream_song_artist")
                                remove("stream_song_title")
                            }
                        }

                        // Keep AA Now Playing clean: do not set standard artist/title fields to stream metadata.
                        // Phone UI can read these from extras.
                        val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                            .setExtras(updatedExtras)
                            .build()

                        val updatedItem = currentItem.buildUpon()
                            .setMediaMetadata(updatedMetadata)
                            .build()

                        val index = currentMediaItemIndex
                        replaceMediaItem(index, updatedItem)
                    }
                })
            }
    }

    private fun startFadeIn(token: Int, target: Float) {
        if (target <= 0f) return

        val steps = 10
        val stepMs = 25L
        for (i in 1..steps) {
            handler.postDelayed({
                if (token != fadeInToken) return@postDelayed
                player.volume = target * (i.toFloat() / steps.toFloat())
            }, i * stepMs)
        }
    }

    private fun buildStationItems(stations: List<RadioStation>): List<MediaItem> {
        return stations.mapIndexed { index, station ->
            buildStationItem(station, index + 1)
        }
    }

    private fun buildStationItem(station: RadioStation, stationNumber: Int): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setTitle(station.name)
            .setDisplayTitle(station.name)

        applyStationArtwork(metadataBuilder, station)

        return MediaItem.Builder()
            .setMediaId("station_${station.id}")
            .setUri(station.streamUrl)
            .setMediaMetadata(
                metadataBuilder
                    .setExtras(Bundle().apply {
                        putString("station_id", station.id)
                        putInt("station_number", stationNumber)
                        putString("station_name", station.name)
                    })
                    .build()
            )
            .build()
    }

    private fun applyStationArtwork(metadataBuilder: MediaMetadata.Builder, station: RadioStation) {
        val raw = station.imageUrl.trim()
        if (raw.isBlank()) return

        // Custom images are persisted as local file paths (e.g. cache/processed_*.jpg).
        // Android Auto often cannot resolve file paths / file:// URIs; embedding bytes is more reliable.
        val localFile = File(raw)
        if (localFile.isFile && localFile.canRead()) {
            try {
                val bytes = localFile.readBytes()
                // Defensive cap to avoid shipping very large artwork blobs through metadata.
                if (bytes.isNotEmpty() && bytes.size <= MAX_EMBEDDED_ARTWORK_BYTES) {
                    metadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    return
                } else {
                    Log.w(TAG, "Skipping embedded artwork for station=${station.id}: size=${bytes.size}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read local artwork for station=${station.id} path=$raw", e)
            }
        }

        // Fallback: keep URI-based artwork for remote images.
        // Note: Android Auto may ignore http(s) artwork depending on environment.
        try {
            metadataBuilder.setArtworkUri(Uri.parse(raw))
        } catch (e: Exception) {
            Log.w(TAG, "Invalid artwork URI for station=${station.id}: $raw", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        Log.d(TAG, "onGetSession caller=${controllerInfo.packageName}")
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // If the user swipes the app away, stop playback unless an external controller
        // (e.g., Android Auto) is connected and actively controlling this session.
        val hasExternalController = try {
            mediaLibrarySession.connectedControllers.any {
                it.packageName != applicationContext.packageName
            }
        } catch (_: Exception) {
            false
        }

        if (hasExternalController) {
            Log.d(TAG, "Task removed but keeping service alive (external controller connected)")
            return
        }

        Log.d(TAG, "Task removed; stopping playback/service (no external controller)")
        try {
            player.stop()
        } catch (_: Exception) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }

        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "MediaLibraryService destroyed")
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {
        }
        releaseEqualizer()
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    private fun applyEqPreset(presetRaw: String) {
        val preset = presetRaw.trim().ifBlank { DEFAULT_EQ_PRESET }
        if (preset.equals("Off", ignoreCase = true)) {
            releaseEqualizer()
            return
        }

        val audioSessionId = try {
            player.audioSessionId
        } catch (_: Exception) {
            0
        }

        if (audioSessionId == 0) {
            // Player not fully initialized yet; we'll apply when the audio session becomes available.
            return
        }

        val eq = ensureEqualizer(audioSessionId) ?: return
        eq.enabled = true

        val bands = eq.numberOfBands.toInt().coerceAtLeast(0)
        if (bands == 0) return

        val range = eq.bandLevelRange
        val minLevel = range[0]
        val maxLevel = range[1]
        val boost = ((maxLevel - minLevel) / 4).toShort()
        val mild = ((maxLevel - minLevel) / 8).toShort()

        for (band in 0 until bands) {
            val bandShort = band.toShort()
            val pos = band.toFloat() / (bands - 1).coerceAtLeast(1)

            val target: Int = when (preset) {
                "Normal" -> 0
                "Bass Boost" -> if (pos <= 0.35f) boost.toInt() else 0
                "Treble Boost" -> if (pos >= 0.65f) boost.toInt() else 0
                "Vocal" -> if (pos in 0.35f..0.65f) boost.toInt() else (-mild).toInt()
                "Rock" -> when {
                    pos <= 0.20f -> boost.toInt()
                    pos in 0.20f..0.50f -> 0
                    else -> boost.toInt()
                }
                "Pop" -> when {
                    pos <= 0.25f -> mild.toInt()
                    pos in 0.25f..0.65f -> boost.toInt()
                    else -> mild.toInt()
                }
                else -> 0
            }

            val clamped = target.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
            try {
                eq.setBandLevel(bandShort, clamped)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set EQ band level band=$bandShort", e)
            }
        }
    }

    private fun ensureEqualizer(audioSessionId: Int): Equalizer? {
        val existing = equalizer
        if (existing != null) {
            return try {
                // If the audio session changed, recreate.
                if (equalizerAudioSessionId != audioSessionId) {
                    releaseEqualizer()
                    createEqualizer(audioSessionId)
                } else {
                    existing
                }
            } catch (_: Exception) {
                releaseEqualizer()
                createEqualizer(audioSessionId)
            }
        }

        return createEqualizer(audioSessionId)
    }

    private fun createEqualizer(audioSessionId: Int): Equalizer? {
        return try {
            Equalizer(0, audioSessionId).also {
                equalizer = it
                equalizerAudioSessionId = audioSessionId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer not available on this device", e)
            null
        }
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        } finally {
            equalizer = null
            equalizerAudioSessionId = 0
        }
    }

    companion object {
        private const val TAG = "DashTuneMediaLibrary"
        const val ROOT_ID = "dashtune_root"
        const val FAVORITES_ID = "dashtune_favorites"
        private const val CHANNEL_ID = "dashtune_media_service"
        private const val NOTIFICATION_ID = 100
        private const val MAX_EMBEDDED_ARTWORK_BYTES = 2 * 1024 * 1024

        private const val PREFS_NAME = "dashtune_prefs"
        private const val KEY_VOLUME_MULTIPLIER = "volume_multiplier"
        private const val DEFAULT_VOLUME_MULTIPLIER = 0.7f
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val DEFAULT_EQ_PRESET = "Off"
        
        // Content style hints for Android Auto grid display
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_GRID = 2
        private const val CONTENT_STYLE_LIST = 1
    }
}
