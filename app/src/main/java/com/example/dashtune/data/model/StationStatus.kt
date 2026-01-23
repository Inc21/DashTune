package com.example.dashtune.data.model

/**
 * Represents the validation status of a radio station
 */
enum class StationStatus {
    /**
     * Station has not been validated yet
     */
    UNKNOWN,
    
    /**
     * Station is confirmed to be working
     */
    WORKING,
    
    /**
     * Station failed to load or play
     */
    FAILED,
    
    /**
     * Station is currently being tested
     */
    VALIDATING
} 