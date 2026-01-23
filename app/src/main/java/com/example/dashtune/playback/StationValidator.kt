package com.example.dashtune.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.dashtune.data.model.RadioStation
import com.example.dashtune.data.model.StationStatus
import com.example.dashtune.data.repository.RadioStationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class to validate if a radio station is alive and can be played
 */
@Singleton
class StationValidator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RadioStationRepository
) {
    private companion object {
        private const val TAG = "StationValidator"
        private const val VALIDATION_TIMEOUT_MS = 20000L  // 20 seconds timeout for slow streams
    }
    
    /**
     * Validates if a station is playable
     * @return true if station is playable, false otherwise
     */
    suspend fun validateStation(station: RadioStation): Boolean {
        if (station.status == StationStatus.WORKING) {
            return true
        }
        
        station.status = StationStatus.VALIDATING
        
        var result = false
        var validationPlayer: ExoPlayer? = null
        val mainHandler = Handler(Looper.getMainLooper())
        
        try {
            val validationResult = withTimeoutOrNull(VALIDATION_TIMEOUT_MS) {
                var isValid = false
                var validationComplete = false
                
                // Run on main thread
                withContext(Dispatchers.Main) {
                    try {
                        // Configure DataSource to follow redirects
                        val dataSourceFactory = DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
                            .setUserAgent("DashTune/1.0")
                        
                        // Create player on main thread with redirect support
                        validationPlayer = ExoPlayer.Builder(context)
                            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
                            .build()
                        
                        validationPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == ExoPlayer.STATE_READY && !validationComplete) {
                                    Log.d(TAG, "Station validated successfully: ${station.name}")
                                    isValid = true
                                    validationComplete = true
                                    station.status = StationStatus.WORKING
                                }
                            }
                            
                            override fun onPlayerError(error: PlaybackException) {
                                if (!validationComplete) {
                                    Log.e(TAG, "Station validation failed: ${station.name} - ${error.message}")
                                    isValid = false
                                    validationComplete = true
                                    station.status = StationStatus.FAILED
                                }
                            }
                        })
                        
                        // Try to play the station
                        val mediaItem = MediaItem.fromUri(station.streamUrl)
                        validationPlayer?.setMediaItem(mediaItem)
                        validationPlayer?.prepare()
                        validationPlayer?.play()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up validation player: ${e.message}", e)
                        isValid = false
                        validationComplete = true
                        station.status = StationStatus.FAILED
                    }
                }
                
                // Wait until validation completes through listeners
                while (!validationComplete) {
                    kotlinx.coroutines.delay(100)
                }
                
                isValid
            }
            
            // Update final result
            result = validationResult == true
            
            // Update station status if timeout occurred
            if (validationResult == null) {
                Log.e(TAG, "Station validation timed out: ${station.name}")
                station.status = StationStatus.FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating station: ${e.message}", e)
            station.status = StationStatus.FAILED
        } finally {
            // Release player on main thread
            withContext(Dispatchers.Main) {
                try {
                    validationPlayer?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing validation player", e)
                }
            }
        }
        
        // Update status in database for saved stations
        if (station.status == StationStatus.WORKING && result) {
            try {
                if (repository.isStationSaved(station.id)) {
                    Log.d(TAG, "Station ${station.name} validated and saved in database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating station status in repository", e)
            }
        }
        
        return result
    }
} 