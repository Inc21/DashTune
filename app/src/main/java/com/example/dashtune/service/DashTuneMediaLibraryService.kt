package com.example.dashtune.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MediaLibraryService onCreate() started")
        
        try {
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
                                                    .setTitle("My Stations")
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
                            val stations = repository.getSavedStations().first()
                            val items = buildStationItems(stations)
                            val requestedId = mediaItems.firstOrNull()?.mediaId
                            val resolvedIndex = if (requestedId != null) {
                                items.indexOfFirst { it.mediaId == requestedId }.takeIf { it >= 0 } ?: 0
                            } else {
                                startIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
                            }
                            future.set(MediaItemsWithStartPosition(items, resolvedIndex, startPositionMs))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onSetMediaItems", e)
                            future.setException(e)
                        }
                    }
                    return future
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
                1500,   // playback buffer (default 2.5s, reduced to 1.5s)
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
                // Enable repeat mode for continuous playback
                repeatMode = Player.REPEAT_MODE_ALL
                
                // Listen for stream metadata changes to update now playing display
                addListener(object : Player.Listener {
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        val streamTitle = mediaMetadata.title?.toString()?.trim()
                        val streamArtist = mediaMetadata.artist?.toString()?.trim()
                        Log.d(TAG, "Stream metadata: title=$streamTitle, artist=$streamArtist")
                        
                        val currentItem = currentMediaItem ?: return
                        val stationName = currentItem.mediaMetadata.displayTitle?.toString()
                            ?: currentItem.mediaMetadata.title?.toString() ?: ""
                        val stationNumber = currentItem.mediaMetadata.extras?.getInt("station_number") ?: -1
                        val stationLabel = if (stationNumber > 0) {
                            "Station $stationNumber • $stationName"
                        } else {
                            stationName
                        }
                        
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
                        
                        if (hasSongInfo) {
                            val songLine = when {
                                songArtist != null && songTitle != null -> "$songArtist - $songTitle"
                                songArtist != null -> songArtist
                                else -> songTitle
                            }
                            
                            val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                                // Top row: station number + name. Second row: song line.
                                .setTitle(stationLabel)
                                .setDisplayTitle(stationLabel)
                                .setArtist(songLine)
                                .setDescription(songTitle)
                                .build()
                            
                            val updatedItem = currentItem.buildUpon()
                                .setMediaMetadata(updatedMetadata)
                                .build()
                            
                            val index = currentMediaItemIndex
                            replaceMediaItem(index, updatedItem)
                            Log.d(TAG, "Updated now playing: $songLine")
                        } else {
                            Log.d(TAG, "Skipping metadata update - same as station name or invalid")
                        }
                    }
                })
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
            .setArtist(station.name)
            .setAlbumTitle(station.name)
            .setSubtitle("Station $stationNumber")
            .setDescription("${station.language} • ${station.tags.take(2).joinToString(", ")}")

        if (station.imageUrl.isNotBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(station.imageUrl))
        }

        return MediaItem.Builder()
            .setMediaId("station_${station.id}")
            .setUri(station.streamUrl)
            .setMediaMetadata(
                metadataBuilder
                    .setExtras(Bundle().apply {
                        putString("station_id", station.id)
                        putInt("station_number", stationNumber)
                    })
                    .build()
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        Log.d(TAG, "onGetSession caller=${controllerInfo.packageName}")
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Don't stop the service - let MediaLibraryService handle lifecycle
        // Android Auto needs the service to stay alive even when app is closed
        Log.d(TAG, "Task removed but keeping service alive for media session")
    }

    override fun onDestroy() {
        Log.d(TAG, "MediaLibraryService destroyed")
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
        
        // Content style hints for Android Auto grid display
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_GRID = 2
        private const val CONTENT_STYLE_LIST = 1
    }
}
