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
    private val dbName = "meter.db"
    private lateinit var helper: SupportSQLiteOpenHelper

    @After
    fun tearDown() {
        if (::helper.isInitialized) {
            helper.close()
        }
        context.deleteDatabase(dbName)
    }

    @Test
    fun productionUpgrades1To4() = verifyProductionUpgrade(1)

    @Test
    fun productionUpgrades2To4() = verifyProductionUpgrade(2)

    @Test
    fun productionUpgrades3To4() = verifyProductionUpgrade(3)

    @Test
    fun freshV4AndReopenPreserveAllEntities() = kotlinx.coroutines.runBlocking {
        var room = com.example.kwh.di.DatabaseModule.provideDatabase(context)
        val dao = room.meterDao()
        val env = dao.insertEnvironment(EnvironmentEntity(name = "House"))
        val meter = dao.insertMeter(MeterEntity(name = "Home", environmentId = env))
        dao.insertReading(MeterReadingEntity(meterId = meter, value = 123.5, recordedAt = 1000, notes = "history"))
        dao.insertActivation(MeterActivationEntity(meterId = meter, startedAt = 500))
        dao.insertSwitchOutcome(SwitchOutcomeEntity(meterId = meter, recommendedDate = 600,
            actualSwitchDate = 700, unitsAtSwitch = 123.5, thresholdTarget = 200))
        room.close()
        room = com.example.kwh.di.DatabaseModule.provideDatabase(context)
        try {
            assertEquals(env, room.meterDao().getMeterById(meter)?.environmentId)
            assertEquals(123.5, room.meterDao().latestBefore(meter, 2000)?.value)
            assertEquals(500L, room.meterDao().getActivationsForMeter(meter).single().startedAt)
            assertEquals(123.5, room.meterDao().getRecentSwitchOutcomes(meter, 6).single().unitsAtSwitch)
            assertEquals(4, room.openHelper.writableDatabase.version)
        } finally { room.close() }
    }

    private fun verifyProductionUpgrade(version: Int) = kotlinx.coroutines.runBlocking {
        helper = if (version == 3) createV3Database() else createV2Database(version)
        helper.writableDatabase.apply {
            execSQL("INSERT INTO meters (id,name,reminder_enabled,reminder_frequency_days,reminder_hour,reminder_minute) VALUES (1,'Home',1,7,9,30)")
            execSQL("INSERT INTO meter_readings (id,meter_id,value,notes,recorded_at) VALUES (1,1,14000,'history',1000),(2,1,14125,NULL,2000)")
            if (version >= 2) execSQL("UPDATE meters SET billing_anchor_day=31, thresholds_csv='200,300,400'")
            if (version == 3) {
                execSQL("INSERT INTO environments (id,name) VALUES (1,'House')")
                execSQL("UPDATE meters SET environment_id=1,safety_buffer_days=5")
                execSQL("INSERT INTO meter_activations (id,meter_id,started_at,ended_at) VALUES (1,1,500,900),(2,1,1000,NULL)")
            }
        }
        helper.close()
        val room = com.example.kwh.di.DatabaseModule.provideDatabase(context)
        try {
            val dao = room.meterDao()
            val meter = dao.getMeterById(1)!! // Forces Room schema validation and real migration discovery.
            assertEquals("Home", meter.name)
            assertTrue(meter.reminderEnabled)
            assertEquals(30, meter.reminderMinute)
            assertEquals(if (version >= 2) 31 else 1, meter.billingAnchorDay)
            assertEquals(if (version >= 2) "200,300,400" else "200,300", meter.thresholdsCsv)
            assertEquals(14000.0, dao.getReadingById(1)?.value)
            assertEquals("history", dao.getReadingById(1)?.notes)
            assertEquals(14125.0, dao.getReadingById(2)?.value)
            if (version == 3) {
                assertEquals(1L, meter.environmentId)
                assertEquals("House", dao.getEnvironmentById(1)?.name)
                assertEquals(5, meter.safetyBufferDays)
                assertEquals(2, dao.getActivationsForMeter(1).size)
                assertEquals(900L, dao.getActivationsForMeter(1).first { it.id == 1L }.endedAt)
                assertEquals(null, dao.getActivationsForMeter(1).first { it.id == 2L }.endedAt)
            }
            assertTrue(dao.getRecentSwitchOutcomes(1, 6).isEmpty()) // Introduced only in v4.
            assertEquals(4, room.openHelper.writableDatabase.version)
        } finally { room.close() }
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

    private fun createV2Database(version: Int = 2): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE meters (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "reminder_enabled INTEGER NOT NULL, " +
                            "reminder_frequency_days INTEGER NOT NULL, " +
                            "reminder_hour INTEGER NOT NULL, " +
                            "reminder_minute INTEGER NOT NULL" +
                            (if (version >= 2) ", billing_anchor_day INTEGER NOT NULL DEFAULT 1, " +
                                "thresholds_csv TEXT NOT NULL DEFAULT '200,300'" else "") + ")"
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
