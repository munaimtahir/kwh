package com.example.kwh.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeterDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private lateinit var helper: SupportSQLiteOpenHelper

    @After
    fun tearDown() {
        if (::helper.isInitialized) {
            helper.close()
        }
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate2To3_addsEnvironmentAndActivationSchema_preservesExistingMeterData() {
        helper = createV2Database()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO meters (id, name, reminder_enabled, reminder_frequency_days, " +
                "reminder_hour, reminder_minute, billing_anchor_day, thresholds_csv) VALUES " +
                "(1, 'Home', 0, 7, 9, 0, 1, '200,300')"
        )

        MeterDatabase.MIGRATION_2_3.migrate(db)

        // Existing meter row survives untouched, with new columns defaulted.
        db.query("SELECT * FROM meters WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Home", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("environment_id")))
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("safety_buffer_days")))
        }

        // New tables exist and are usable.
        db.execSQL("INSERT INTO environments (id, name) VALUES (1, 'House')")
        db.execSQL(
            "INSERT INTO meter_activations (id, meter_id, started_at, ended_at) VALUES " +
                "(1, 1, 1000, NULL)"
        )
        db.query("SELECT * FROM environments WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("House", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        db.query("SELECT * FROM meter_activations WHERE meter_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("started_at")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("ended_at")))
        }
    }

    @Test
    fun migrate3To4_addsSwitchOutcomesSchema_preservesExistingData() {
        helper = createV3Database()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO meters (id, name, reminder_enabled, reminder_frequency_days, " +
                "reminder_hour, reminder_minute, billing_anchor_day, thresholds_csv, " +
                "environment_id, safety_buffer_days) VALUES " +
                "(1, 'Home', 0, 7, 9, 0, 1, '200,300', NULL, 3)"
        )

        MeterDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT * FROM meters WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Home", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }

        db.execSQL(
            "INSERT INTO switch_outcomes (id, meter_id, recommended_date, actual_switch_date, " +
                "units_at_switch, threshold_target) VALUES (1, 1, 1000, 2000, 95.5, 100)"
        )
        db.query("SELECT * FROM switch_outcomes WHERE meter_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("recommended_date")))
            assertEquals(2000L, cursor.getLong(cursor.getColumnIndexOrThrow("actual_switch_date")))
            assertEquals(100, cursor.getInt(cursor.getColumnIndexOrThrow("threshold_target")))
        }
    }

    private fun createV3Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE meters (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "reminder_enabled INTEGER NOT NULL, " +
                            "reminder_frequency_days INTEGER NOT NULL, " +
                            "reminder_hour INTEGER NOT NULL, " +
                            "reminder_minute INTEGER NOT NULL, " +
                            "billing_anchor_day INTEGER NOT NULL DEFAULT 1, " +
                            "thresholds_csv TEXT NOT NULL DEFAULT '200,300', " +
                            "environment_id INTEGER DEFAULT NULL, " +
                            "safety_buffer_days INTEGER NOT NULL DEFAULT 3)"
                    )
                    db.execSQL(
                        "CREATE TABLE meter_readings (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "meter_id INTEGER NOT NULL, " +
                            "value REAL NOT NULL, " +
                            "notes TEXT, " +
                            "recorded_at INTEGER NOT NULL, " +
                            "FOREIGN KEY(meter_id) REFERENCES meters(id) ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "CREATE INDEX index_meter_readings_meter_id ON meter_readings (meter_id)"
                    )
                    db.execSQL(
                        "CREATE TABLE environments (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE meter_activations (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "meter_id INTEGER NOT NULL, " +
                            "started_at INTEGER NOT NULL, " +
                            "ended_at INTEGER, " +
                            "FOREIGN KEY(meter_id) REFERENCES meters(id) ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "CREATE INDEX index_meter_activations_meter_id ON meter_activations (meter_id)"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun createV2Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE meters (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "reminder_enabled INTEGER NOT NULL, " +
                            "reminder_frequency_days INTEGER NOT NULL, " +
                            "reminder_hour INTEGER NOT NULL, " +
                            "reminder_minute INTEGER NOT NULL, " +
                            "billing_anchor_day INTEGER NOT NULL DEFAULT 1, " +
                            "thresholds_csv TEXT NOT NULL DEFAULT '200,300')"
                    )
                    db.execSQL(
                        "CREATE TABLE meter_readings (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "meter_id INTEGER NOT NULL, " +
                            "value REAL NOT NULL, " +
                            "notes TEXT, " +
                            "recorded_at INTEGER NOT NULL, " +
                            "FOREIGN KEY(meter_id) REFERENCES meters(id) ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "CREATE INDEX index_meter_readings_meter_id ON meter_readings (meter_id)"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }
}
