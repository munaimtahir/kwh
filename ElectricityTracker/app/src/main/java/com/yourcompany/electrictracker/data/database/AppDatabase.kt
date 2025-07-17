package com.yourcompany.electrictracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yourcompany.electrictracker.data.dao.UsageDao
import com.yourcompany.electrictracker.data.entities.Usage

@Database(entities = [Usage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "electricity_tracker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
