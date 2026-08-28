package com.example.qoesense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TelemetrySampleEntity::class], version = 1, exportSchema = false)
@TypeConverters(TelemetryConverters::class)
abstract class QoESenseDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile
        private var instance: QoESenseDatabase? = null

        fun getInstance(context: Context): QoESenseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QoESenseDatabase::class.java,
                    "qoesense.db",
                ).build().also { instance = it }
            }
    }
}
