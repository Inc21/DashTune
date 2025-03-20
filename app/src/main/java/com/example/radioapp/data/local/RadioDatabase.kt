package com.example.radioapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.radioapp.data.model.RadioStation

@Database(
    entities = [RadioStation::class],
    version = 1,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun radioStationDao(): RadioStationDao
} 