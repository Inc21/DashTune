package com.example.dashtune.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.dashtune.MainActivity
import com.example.dashtune.data.model.RadioStation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RadioPlayerService : Service() {

    private val binder = RadioPlayerBinder()
    private lateinit var player: ExoPlayer
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private val handler = Handler()
    
    private var currentStation: RadioStation? = null
    private var playbackStateListener: PlaybackStateListener? = null

    inner class RadioPlayerBinder : Binder() {
        fun getService(): RadioPlayerService = this@RadioPlayerService
    }

    interface PlaybackStateListener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onBufferingStateChanged(isBuffering: Boolean)
        fun onError(errorMessage: String)
    }

    fun setPlaybackStateListener(listener: PlaybackStateListener) {
        playbackStateListener = listener
    }

    override fun onCreate() {
        super.onCreate()
        
        // Configure DataSource to follow redirects
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("DashTune/1.0")
        
        // Initialize ExoPlayer with custom DataSource that follows redirects
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build()
            
        // Set up player listeners for error handling
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        playbackStateListener?.onBufferingStateChanged(false)
                        playbackStateListener?.onPlaybackStateChanged(player.isPlaying)
                        updateNotification()
                    }
                    Player.STATE_BUFFERING -> {
                        playbackStateListener?.onBufferingStateChanged(true)
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        playbackStateListener?.onBufferingStateChanged(false)
                        playbackStateListener?.onPlaybackStateChanged(false)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackStateListener?.onPlaybackStateChanged(isPlaying)
                updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Handle playback errors
                val errorMessage = "Playback error: ${error.errorCodeName}"
                Log.e(TAG, errorMessage, error)
                playbackStateListener?.onError(errorMessage)
                
                // Try to recover automatically if possible
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    // Network error, maybe try again after a delay
                    handler.postDelayed({
                        currentStation?.let { playStation(it) }
                    }, 3000)
                }
            }
        })

        // Set up notification and media session
        setupMediaSession()
        setupNotification()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession.Builder(this, player)
            .setId("RadioPlayerService")
            .build()
    }

    private fun setupNotification() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Radio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Radio Player Controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Radio Player")
            .setContentText("No station playing")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
    }

    private fun updateNotification() {
        val station = currentStation ?: return
        
        // Add play/pause action
        val playPauseIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RadioPlayerService::class.java).apply {
                action = if (player.isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Add stop action
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RadioPlayerService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = notificationBuilder
            .setContentTitle(station.name)
            .setContentText("Now playing")
            .clearActions()
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    fun playStation(station: RadioStation) {
        currentStation = station
        
        try {
            // Clear any previous items
            player.clearMediaItems()
            
            // Create media item
            val mediaItem = MediaItem.fromUri(station.streamUrl)
            
            // Set media item and prepare
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing station: ${e.message}", e)
            playbackStateListener?.onError("Failed to play: ${e.message}")
        }
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun stop() {
        player.stop()
        playbackStateListener?.onPlaybackStateChanged(false)
        playbackStateListener?.onBufferingStateChanged(false)
        stopForeground(true)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RadioPlayerService"
        private const val NOTIFICATION_CHANNEL_ID = "radio_player_channel"
        private const val NOTIFICATION_ID = 1
        
        private const val ACTION_PLAY = "com.example.dashtune.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.example.dashtune.ACTION_PAUSE"
        private const val ACTION_STOP = "com.example.dashtune.ACTION_STOP"
    }
} 