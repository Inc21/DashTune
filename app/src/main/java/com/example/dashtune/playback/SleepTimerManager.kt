package com.example.dashtune.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    
    private val _isTimerActive = MutableStateFlow(false)
    val isTimerActive: StateFlow<Boolean> = _isTimerActive.asStateFlow()
    
    private val _remainingTimeMinutes = MutableStateFlow(0)
    val remainingTimeMinutes: StateFlow<Int> = _remainingTimeMinutes.asStateFlow()
    
    private var onTimerComplete: (() -> Unit)? = null
    
    fun startTimer(durationMinutes: Int, onComplete: () -> Unit) {
        cancelTimer()
        
        onTimerComplete = onComplete
        _isTimerActive.value = true
        _remainingTimeMinutes.value = durationMinutes
        
        timerJob = scope.launch {
            var remainingSeconds = durationMinutes * 60
            
            while (remainingSeconds > 0 && isActive) {
                delay(1000) // Wait 1 second
                remainingSeconds--
                _remainingTimeMinutes.value = (remainingSeconds + 59) / 60 // Round up
            }
            
            if (isActive) {
                _isTimerActive.value = false
                _remainingTimeMinutes.value = 0
                onTimerComplete?.invoke()
            }
        }
    }
    
    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _isTimerActive.value = false
        _remainingTimeMinutes.value = 0
        onTimerComplete = null
    }
    
    fun cleanup() {
        cancelTimer()
        scope.cancel()
    }
}
