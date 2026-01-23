package com.example.dashtune.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dashtune.data.dao.RadioStationDao
import com.example.dashtune.data.model.RadioStation

@Database(
    entities = [RadioStation::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun radioStationDao(): RadioStationDao
    
    companion object {
        // Migration from version 1 to 2 (adding country field)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the country column with an empty string as default value
                db.execSQL("ALTER TABLE radio_stations ADD COLUMN country TEXT NOT NULL DEFAULT ''")
            }
        }
        
        // Migration from version 2 to 3 (adding codec and bitrate fields)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the codec column with an empty string as default value
                db.execSQL("ALTER TABLE radio_stations ADD COLUMN codec TEXT NOT NULL DEFAULT ''")
                // Add the bitrate column with 0 as default value
                db.execSQL("ALTER TABLE radio_stations ADD COLUMN bitrate INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // Migration from version 3 to 4 (adding language, tags, and votes fields)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the language column with an empty string as default value
                db.execSQL("ALTER TABLE radio_stations ADD COLUMN language TEXT NOT NULL DEFAULT ''")
                // Add the tags column with an empty string as default value
                db.execSQL("ALTER TABLE radio_stations ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                // Add the votes column with 0 as default value
                db.execSQL("ALTER TABLE radio_stations ADD COLUMN votes INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}