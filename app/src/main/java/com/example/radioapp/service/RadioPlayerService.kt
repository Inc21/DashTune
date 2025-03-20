package com.example.radioapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.radioapp.MainActivity
import com.example.radioapp.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RadioPlayerService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        val service: RadioPlayerService
            get() = this@RadioPlayerService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent) = binder

    @Inject
    lateinit var player: ExoPlayer
    
    private var mediaSession: MediaSession? = null
    private var currentStationName: String = ""
    
    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player).build()
        createNotificationChannel()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    fun playStation(url: String, stationName: String) {
        currentStationName = stationName
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun stopPlayback() {
        player.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing radio station"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Now Playing")
            .setContentText(currentStationName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 1
    }
} 