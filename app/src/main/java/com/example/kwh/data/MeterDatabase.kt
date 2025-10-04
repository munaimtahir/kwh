package com.example.kwh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MeterEntity::class, MeterReadingEntity::class],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE meters ADD COLUMN billing_anchor_day INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE meters ADD COLUMN thresholds_csv TEXT NOT NULL DEFAULT '200,300'"
                )
            }
        }
    }
}
