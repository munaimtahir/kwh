package com.example.kwh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MeterEntity::class,
        MeterReadingEntity::class,
        EnvironmentEntity::class,
        MeterActivationEntity::class,
        SwitchOutcomeEntity::class
    ],
    version = 4,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `environments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL)"
                )
                database.execSQL(
                    "ALTER TABLE meters ADD COLUMN environment_id INTEGER DEFAULT NULL"
                )
                database.execSQL(
                    "ALTER TABLE meters ADD COLUMN safety_buffer_days INTEGER NOT NULL DEFAULT 3"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meter_activations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`meter_id` INTEGER NOT NULL, " +
                        "`started_at` INTEGER NOT NULL, " +
                        "`ended_at` INTEGER, " +
                        "FOREIGN KEY(`meter_id`) REFERENCES `meters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_meter_activations_meter_id` " +
                        "ON `meter_activations` (`meter_id`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `switch_outcomes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`meter_id` INTEGER NOT NULL, " +
                        "`recommended_date` INTEGER NOT NULL, " +
                        "`actual_switch_date` INTEGER NOT NULL, " +
                        "`units_at_switch` REAL NOT NULL, " +
                        "`threshold_target` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`meter_id`) REFERENCES `meters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_switch_outcomes_meter_id` " +
                        "ON `switch_outcomes` (`meter_id`)"
                )
            }
        }
    }
}
