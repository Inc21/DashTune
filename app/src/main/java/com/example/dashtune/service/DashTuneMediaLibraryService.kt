package com.example.dashtune.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
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

    private var volumeMultiplier: Float = 1.0f

    private var fadeInPending: Boolean = false
    private var fadeInToken: Int = 0
    private var fadeInTargetVolume: Float = 1.0f
    private var lastNonZeroPlayerVolume: Float = 1.0f

    // Simple flag to handle Android Auto double-skips
    private var lastAACommandTimeMs = 0L
    private val AA_DEBOUNCE_MS = 800L

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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

    private fun normalizeVoiceQuery(query: String?): String {
        return query.orEmpty().trim().lowercase()
    }

    private fun parseStationNumberFromQuery(normalizedQuery: String): Int? {
        val match = Regex("\\b(?:station|preset)\\s*(\\d{1,3})\\b").find(normalizedQuery) ?: return null
        val n = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        return n.takeIf { it > 0 }
    }

    private fun armTransitionMute() {
        // Mute the current stream and immediately apply the leveller to prevent the pop
        // Only arm if not already pending to avoid double-muting
        if (fadeInPending) {
            Log.d(TAG, "armTransitionMute skipped: already pending")
            return
        }
        
        // Set flag immediately to prevent double-calls
        fadeInPending = true
        fadeInToken += 1
        fadeInTargetVolume = volumeMultiplier.coerceAtLeast(0f)
        
        Log.d(TAG, "armTransitionMute called, setting volume immediately")
        // Apply mute and leveller directly without posting to handler
        player.volume = 0f
        player.volume = fadeInTargetVolume
        Log.d(TAG, "armTransitionMute done: volume now=${player.volume}, fadeInToken=$fadeInToken")
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
                    
                    val availablePlayerCommands = Player.Commands.Builder()
                        .addAll(
                            Player.COMMAND_PLAY_PAUSE,
                            Player.COMMAND_PREPARE,
                            Player.COMMAND_STOP,
                            Player.COMMAND_SEEK_TO_NEXT,
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_PREVIOUS,
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                            Player.COMMAND_SET_MEDIA_ITEM,
                            Player.COMMAND_CHANGE_MEDIA_ITEMS,
                            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                            Player.COMMAND_GET_TIMELINE,
                            Player.COMMAND_GET_METADATA
                        )
                        .build()
                    
                    Log.d(TAG, "Accepting connection with explicit next/prev commands")
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(availablePlayerCommands)
                        .build()
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

                override fun onSearch(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    query: String,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<Void>> {
                    Log.d(TAG, "onSearch query='$query' caller=${browser.packageName}")

                    serviceScope.launch {
                        try {
                            val stations = repository.getSavedStations().first()
                            val normalized = normalizeVoiceQuery(query)
                            val requestedNumber = parseStationNumberFromQuery(normalized)

                            val resultCount = when {
                                stations.isEmpty() -> 0
                                requestedNumber != null -> if (stations.getOrNull(requestedNumber - 1) != null) 1 else 0
                                normalized.isBlank() || normalized == "dashtune" || normalized.contains("dashtune") -> 1
                                else -> stations.count { station ->
                                    val name = station.name.trim()
                                    name.isNotEmpty() && name.lowercase().contains(normalized)
                                }
                            }

                            session.notifySearchResultChanged(browser, query, resultCount, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onSearch", e)
                            session.notifySearchResultChanged(browser, query, 0, params)
                        }
                    }

                    return Futures.immediateFuture(LibraryResult.ofVoid())
                }

                override fun onGetSearchResult(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    query: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    Log.d(TAG, "onGetSearchResult query='$query' page=$page pageSize=$pageSize caller=${browser.packageName}")
                    val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        try {
                            val stations = repository.getSavedStations().first()
                            if (stations.isEmpty()) {
                                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                                return@launch
                            }

                            val normalized = normalizeVoiceQuery(query)
                            val requestedNumber = parseStationNumberFromQuery(normalized)

                            val resultItems: List<MediaItem> = when {
                                requestedNumber != null -> {
                                    val index = requestedNumber - 1
                                    val station = stations.getOrNull(index)
                                    if (station != null) listOf(buildStationItem(station, requestedNumber)) else emptyList()
                                }
                                normalized.isBlank() || normalized == "dashtune" || normalized.contains("dashtune") -> {
                                    val first = stations.first()
                                    listOf(buildStationItem(first, 1))
                                }
                                else -> {
                                    val matches = stations.mapIndexedNotNull { idx, station ->
                                        val name = station.name.trim()
                                        if (name.isNotEmpty() && name.lowercase().contains(normalized)) {
                                            buildStationItem(station, idx + 1)
                                        } else {
                                            null
                                        }
                                    }
                                    matches
                                }
                            }

                            val start = (page * pageSize).coerceAtLeast(0)
                            val end = (start + pageSize).coerceAtMost(resultItems.size)
                            val paged = if (start in 0..end) resultItems.subList(start, end) else emptyList()
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onGetSearchResult", e)
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
                    Log.d(TAG, "onGetItem mediaId=$mediaId caller=${browser.packageName}")
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
                                    Log.d(TAG, "onGetItem returning station: ${station.name} (index=$index, totalStations=${stations.size})")
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
                    Log.d(TAG, "onSetMediaItems from ${controller.packageName}, items=${mediaItems.size}, startIndex=$startIndex")
                    
                    val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex.coerceAtLeast(0))
                    
                    // Build enriched items with full metadata synchronously
                    val enrichedItems = runBlocking {
                        try {
                            val stations = repository.getSavedStations().first()
                            
                            // If only 1 item is requested (AA clicking a station), load FULL playlist
                            // so skip buttons can navigate through all stations
                            if (mediaItems.size == 1 && controller.packageName == "com.google.android.projection.gearhead") {
                                val requestedStationId = mediaItems[0].mediaId.removePrefix("station_")
                                val requestedStation = stations.find { it.id == requestedStationId }
                                
                                if (requestedStation != null) {
                                    val targetIndex = stations.indexOf(requestedStation)
                                    val allStationItems = buildStationItems(stations)
                                    Log.d(TAG, "AA requested 1 station, loading full playlist of ${allStationItems.size} stations, starting at index=$targetIndex")
                                    return@runBlocking Pair(allStationItems, targetIndex)
                                }
                            }
                            
                            // Otherwise, enrich the provided items normally
                            val enriched = mediaItems.map { item ->
                                val stationId = item.mediaId.removePrefix("station_")
                                val station = stations.find { it.id == stationId }
                                
                                if (station != null) {
                                    val stationNumber = stations.indexOf(station) + 1
                                    buildStationItem(station, stationNumber)
                                } else {
                                    item
                                }
                            }
                            Pair(enriched, safeIndex)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to enrich items", e)
                            Pair(mediaItems.toList(), safeIndex)
                        }
                    }
                    
                    val (finalItems, finalIndex) = enrichedItems
                    Log.d(TAG, "Enriched ${finalItems.size} items, applying to player at index=$finalIndex")
                    
                    // Manually apply items to player to ensure playback starts
                    handler.post {
                        try {
                            player.stop()
                            player.clearMediaItems()
                            player.setMediaItems(finalItems)
                            player.seekTo(finalIndex, 0)
                            player.prepare()
                            player.playWhenReady = true
                            Log.d(TAG, "Player setup complete: index=$finalIndex, itemCount=${player.mediaItemCount}, playWhenReady=${player.playWhenReady}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to apply items to player", e)
                        }
                    }
                    
                    // Return enriched items for Media3 compatibility
                    return Futures.immediateFuture(MediaItemsWithStartPosition(finalItems, finalIndex, startPositionMs))
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    // Handle navigation commands
                    if (playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                        playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                        playerCommand == Player.COMMAND_SEEK_TO_NEXT ||
                        playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS
                    ) {
                        // Only apply debounce for Android Auto to fix double-skip
                        if (controller.packageName == "com.google.android.projection.gearhead") {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastAACommandTimeMs < AA_DEBOUNCE_MS) {
                                Log.d(TAG, "BLOCKED duplicate Android Auto command")
                                return SessionResult.RESULT_SUCCESS
                            }
                            lastAACommandTimeMs = now
                        }
                        
                        // Original navigation behavior - works for both AA and phone
                        var count = player.mediaItemCount
                        if (count <= 0) {
                            return SessionResult.RESULT_SUCCESS
                        }
                        
                        // If only 1 station loaded, load full playlist before navigating
                        if (count == 1) {
                            Log.d(TAG, "Only 1 station in playlist, loading full playlist for navigation")
                            try {
                                val currentStationId = player.currentMediaItem?.mediaId?.removePrefix("station_")
                                val stations = runBlocking { repository.getSavedStations().first() }
                                val currentStation = stations.find { it.id == currentStationId }
                                
                                if (currentStation != null && stations.size > 1) {
                                    val currentIndex = stations.indexOf(currentStation)
                                    val allStationItems = buildStationItems(stations)
                                    
                                    player.stop()
                                    player.clearMediaItems()
                                    player.setMediaItems(allStationItems)
                                    player.seekToDefaultPosition(currentIndex)
                                    player.prepare()
                                    player.playWhenReady = true
                                    
                                    count = player.mediaItemCount
                                    Log.d(TAG, "Loaded full playlist: ${allStationItems.size} stations, current index=$currentIndex")
                                } else {
                                    Log.d(TAG, "Cannot expand playlist: station not found or only 1 station saved")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load full playlist", e)
                            }
                        }
                        
                        // Prepare for station change
                        armTransitionMute()
                        val direction = if (
                            playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                            playerCommand == Player.COMMAND_SEEK_TO_NEXT
                        ) 1 else -1
                        
                        // Get current state
                        val wasPlayWhenReady = player.playWhenReady
                        val index = player.currentMediaItemIndex
                        
                        // Calculate target index with wrap-around
                        val targetIndex = if (direction > 0) {
                            if (index >= count - 1) 0 else index + 1  // Wrap at end
                        } else {
                            if (index <= 0) count - 1 else index - 1  // Wrap at start
                        }
                        
                        // Log and perform navigation
                        Log.d(TAG, "Navigation: index=$index → $targetIndex (count=$count, from=${controller.packageName})")
                        
                        // Temporarily disable repeat mode to prevent auto-advance during seek
                        val previousRepeatMode = player.repeatMode
                        player.repeatMode = Player.REPEAT_MODE_OFF
                        
                        // Seek to target station
                        player.seekToDefaultPosition(targetIndex)
                        
                        // Restore repeat mode after seek completes
                        handler.postDelayed({
                            player.repeatMode = previousRepeatMode
                        }, 100)
                        
                        player.playWhenReady = wasPlayWhenReady
                        
                        // Return SUCCESS to indicate we've handled the command
                        // DO NOT call super as it would process the skip again, causing double-skip
                        return SessionResult.RESULT_SUCCESS
                    }

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

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    if (intent.action != Intent.ACTION_MEDIA_BUTTON) {
                        return super.onMediaButtonEvent(session, controllerInfo, intent)
                    }

                    val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    if (keyEvent == null || keyEvent.action != KeyEvent.ACTION_DOWN) {
                        return super.onMediaButtonEvent(session, controllerInfo, intent)
                    }

                    // Only apply debounce for Android Auto to fix double-skip
                    if (controllerInfo.packageName == "com.google.android.projection.gearhead") {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastAACommandTimeMs < AA_DEBOUNCE_MS) {
                            Log.d(TAG, "BLOCKED duplicate Android Auto button")
                            return true
                        }
                        lastAACommandTimeMs = now
                    }
                    
                    Log.d(TAG, "Processing media button ${keyEvent.keyCode}")

                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            
                            val count = player.mediaItemCount
                            if (count <= 0) return true
                            
                            armTransitionMute()
                            val wasPlayWhenReady = player.playWhenReady
                            val index = player.currentMediaItemIndex
                            val nextIndex = if (index >= count - 1) 0 else index + 1
                            
                            Log.d(TAG, "Media Next: index=$index -> $nextIndex (count=$count)")
                            player.seekTo(nextIndex, 0)
                            player.prepare()
                            player.playWhenReady = wasPlayWhenReady
                            return true
                        }

                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            
                            val count = player.mediaItemCount
                            if (count <= 0) return true
                            
                            armTransitionMute()
                            val wasPlayWhenReady = player.playWhenReady
                            val index = player.currentMediaItemIndex
                            val prevIndex = if (index <= 0) count - 1 else index - 1
                            
                            Log.d(TAG, "Media Previous: index=$index -> $prevIndex (count=$count)")
                            player.seekTo(prevIndex, 0)
                            player.prepare()
                            player.playWhenReady = wasPlayWhenReady
                            return true
                        }
                    }

                    return super.onMediaButtonEvent(session, controllerInfo, intent)
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
        val transferListener = object : TransferListener {
            override fun onTransferInitializing(
                source: androidx.media3.datasource.DataSource,
                dataSpec: DataSpec,
                isNetwork: Boolean
            ) {
                if (isNetwork) {
                    Log.d(TAG, "HTTP init: ${dataSpec.uri}")
                }
            }

            override fun onTransferStart(
                source: androidx.media3.datasource.DataSource,
                dataSpec: DataSpec,
                isNetwork: Boolean
            ) {
                if (isNetwork) {
                    Log.d(TAG, "HTTP start: ${dataSpec.httpMethod} ${dataSpec.uri}")
                }
            }

            override fun onBytesTransferred(
                source: androidx.media3.datasource.DataSource,
                dataSpec: DataSpec,
                isNetwork: Boolean,
                bytesTransferred: Int
            ) {
            }

            override fun onTransferEnd(
                source: androidx.media3.datasource.DataSource,
                dataSpec: DataSpec,
                isNetwork: Boolean
            ) {
                if (isNetwork) {
                    Log.d(TAG, "HTTP end: ${dataSpec.uri}")
                }
            }
        }

        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("DashTune/1.0")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setTransferListener(transferListener)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,
                30000,
                2500,
                5000
            )
            .build()

        val exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .build()
            .apply {
                // Clear any stale items from previous sessions
                clearMediaItems()
                
                // REPEAT_MODE_ALL allows auto-advance needed for wrap-around
                repeatMode = Player.REPEAT_MODE_ALL

                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        Log.d(TAG, "Media item transition: reason=$reason mediaId=${mediaItem?.mediaId} index=$currentMediaItemIndex")
                        
                        if (!fadeInPending) {
                            armTransitionMute()
                        }
                        
                        // Clear stream metadata on station change to prevent stale data
                        mediaItem?.let { item ->
                            val currentExtras = item.mediaMetadata.extras
                            if (currentExtras?.containsKey("stream_song_artist") == true || 
                                currentExtras?.containsKey("stream_song_title") == true) {
                                val clearedExtras = Bundle(currentExtras).apply {
                                    remove("stream_song_artist")
                                    remove("stream_song_title")
                                }
                                val clearedMetadata = item.mediaMetadata.buildUpon()
                                    .setExtras(clearedExtras)
                                    .build()
                                val clearedItem = item.buildUpon()
                                    .setMediaMetadata(clearedMetadata)
                                    .build()
                                replaceMediaItem(currentMediaItemIndex, clearedItem)
                                Log.d(TAG, "Cleared stream metadata on station transition")
                            }
                        }
                    }
                    
                    override fun onVolumeChanged(volume: Float) {
                        if (volume > 0f) {
                            lastNonZeroPlayerVolume = volume
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
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
                            fadeInPending = false
                            playWhenReady = true
                            startFadeIn(fadeInToken, fadeInTargetVolume)
                        }
                    }

                    // onMediaItemTransition method moved and merged with the implementation above

                    override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                        // Process ICY metadata from radio streams
                        for (i in 0 until metadata.length()) {
                            val entry = metadata.get(i)
                            if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                                val icyTitle = entry.title?.trim()
                                Log.d(TAG, "ICY metadata received: title=$icyTitle")
                                
                                if (icyTitle != null) {
                                    processStreamMetadata(icyTitle, null)
                                }
                            }
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        val streamTitle = mediaMetadata.title?.toString()?.trim()
                        val streamArtist = mediaMetadata.artist?.toString()?.trim()
                        Log.d(TAG, "onMediaMetadataChanged: title=$streamTitle, artist=$streamArtist")
                        
                        if (streamTitle != null || streamArtist != null) {
                            processStreamMetadata(streamTitle, streamArtist)
                        }
                    }
                })
            }

        return exoPlayer
    }

    private fun processStreamMetadata(streamTitle: String?, streamArtist: String?) {
        handler.post {
            try {
                val currentItem = player.currentMediaItem
                if (currentItem == null) {
                    Log.d(TAG, "processStreamMetadata: no current item")
                    return@post
                }
                
                val stationName = currentItem.mediaMetadata.extras?.getString("station_name")
                    ?: currentItem.mediaMetadata.title?.toString().orEmpty()
                Log.d(TAG, "processStreamMetadata: stationName=$stationName, streamTitle=$streamTitle, streamArtist=$streamArtist")

                var songArtist: String? = null
                var songTitle: String? = null

                // Try to parse "Artist - Title" format from streamTitle
                val rawMetadata = streamTitle ?: streamArtist
                if (rawMetadata != null && rawMetadata.contains(" - ")) {
                    val parts = rawMetadata.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        songArtist = parts[0].trim()
                        songTitle = parts[1].trim()
                        Log.d(TAG, "Parsed metadata: artist=$songArtist, title=$songTitle")
                    }
                } else if (streamArtist != null && streamTitle != null && streamArtist != streamTitle) {
                    songArtist = streamArtist
                    songTitle = streamTitle
                    Log.d(TAG, "Using separate artist/title: artist=$songArtist, title=$songTitle")
                }

                // Validate metadata quality
                val hasSongInfo = (songArtist != null || songTitle != null) &&
                    !songArtist.equals(stationName, ignoreCase = true) &&
                    !songTitle.equals(stationName, ignoreCase = true) &&
                    (songArtist?.length ?: 0) < 100 &&
                    (songTitle?.length ?: 0) < 100

                Log.d(TAG, "Metadata validation: hasSongInfo=$hasSongInfo")

                val existingExtras = currentItem.mediaMetadata.extras
                val existingArtist = existingExtras?.getString("stream_song_artist")
                val existingTitle = existingExtras?.getString("stream_song_title")

                val desiredArtist = if (hasSongInfo) songArtist else null
                val desiredTitle = if (hasSongInfo) songTitle else null

                // Skip if no change
                if (existingArtist == desiredArtist && existingTitle == desiredTitle) {
                    Log.d(TAG, "Metadata unchanged, skipping update")
                    return@post
                }

                // Update MediaItem with stream metadata
                // Store in extras for PlaybackManager AND in title/artist for MediaController notification
                val updatedExtras = Bundle(currentItem.mediaMetadata.extras ?: Bundle()).apply {
                    if (hasSongInfo) {
                        putString("stream_song_artist", songArtist)
                        putString("stream_song_title", songTitle)
                    } else {
                        remove("stream_song_artist")
                        remove("stream_song_title")
                    }
                }

                // Build metadata with BOTH extras and standard fields
                // MediaController observes title/artist changes, so this will trigger notification
                val metadataBuilder = currentItem.mediaMetadata.buildUpon()
                    .setExtras(updatedExtras)
                
                if (hasSongInfo) {
                    // Set title and artist in standard fields to trigger MediaController update
                    metadataBuilder
                        .setTitle(songTitle)
                        .setArtist(songArtist)
                } else {
                    // Clear title/artist to show station name only
                    metadataBuilder
                        .setTitle(stationName)
                        .setArtist(null)
                }
                
                val updatedMetadata = metadataBuilder.build()

                val updatedItem = currentItem.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()

                val index = player.currentMediaItemIndex
                
                player.replaceMediaItem(index, updatedItem)
                Log.d(TAG, "✓ Metadata updated: artist=$desiredArtist, title=$desiredTitle (in both extras and standard fields)")
                
                // Verify the update
                val verifyItem = player.currentMediaItem
                val verifyExtras = verifyItem?.mediaMetadata?.extras
                Log.d(TAG, "Verification: extras keys=${verifyExtras?.keySet()?.joinToString()}, artist=${verifyExtras?.getString("stream_song_artist")}, title=${verifyExtras?.getString("stream_song_title")}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in processStreamMetadata", e)
            }
        }
    }

    private fun startFadeIn(token: Int, target: Float) {
        if (target <= 0f) return

        Log.d(TAG, "startFadeIn called: token=$token, fadeInToken=$fadeInToken, target=$target")
        // Fade in from 0 to target volume over 100ms
        val steps = 4
        val stepMs = 25L
        for (i in 1..steps) {
            handler.postDelayed({
                if (token != fadeInToken) {
                    Log.d(TAG, "startFadeIn step $i skipped: token mismatch ($token != $fadeInToken)")
                    return@postDelayed
                }
                val newVolume = target * (i.toFloat() / steps.toFloat())
                Log.d(TAG, "startFadeIn step $i/$steps: setting volume to $newVolume")
                player.volume = newVolume
            }, i * stepMs)
        }
    }
    
    // These methods are no longer used - we've switched to direct implementation in the handlers
    // with a time-based debounce window

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
            .setSubtitle("Station $stationNumber")

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
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
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
        
        // Content style hints for Android Auto grid display
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_GRID = 2
        private const val CONTENT_STYLE_LIST = 1
    }
}
