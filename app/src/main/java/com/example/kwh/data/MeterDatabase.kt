package com.example.kwh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MeterEntity::class, MeterReadingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MeterDatabase : RoomDatabase() {
    abstract fun meterDao(): MeterDao

    companion object {
        @Volatile
        private var INSTANCE: MeterDatabase? = null

        fun get(context: Context): MeterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeterDatabase::class.java,
                    "meter.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
