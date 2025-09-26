package com.example.kwh.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MeterDaoTest {

    private lateinit var database: MeterDatabase
    private lateinit var dao: MeterDao

    @Before
    fun setUp() {
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeterDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.meterDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertMeterAndReadings_emitsLatestReading() = runTest {
        val meterId = dao.insertMeter(MeterEntity(name = "Home"))
        dao.insertReading(
            MeterReadingEntity(
                meterId = meterId,
                value = 120.5,
                notes = "baseline",
                recordedAt = 1_000L
            )
        )
        dao.insertReading(
            MeterReadingEntity(
                meterId = meterId,
                value = 121.0,
                notes = null,
                recordedAt = 2_000L
            )
        )

        val meters = dao.observeMetersWithReadings().first()
        assertEquals(1, meters.size)
        val latest = meters.single().readings.maxByOrNull { it.recordedAt }
        assertNotNull(latest)
        assertEquals(121.0, latest.value, 0.0)
    }

    @Test
    fun deleteMeter_cascadesToReadings() = runTest {
        val meterId = dao.insertMeter(MeterEntity(name = "Office"))
        val readingId = dao.insertReading(
            MeterReadingEntity(
                meterId = meterId,
                value = 50.0,
                notes = null,
                recordedAt = 500L
            )
        )
        assertTrue(readingId > 0)

        val meter = dao.getMeterById(meterId)
        assertNotNull(meter)
        dao.deleteMeter(meter)

        val readings = dao.observeReadingsForMeter(meterId).first()
        assertTrue(readings.isEmpty())
    }

    @Test
    fun getReadingById_returnsEntity() = runTest {
        val meterId = dao.insertMeter(MeterEntity(name = "Lab"))
        val readingId = dao.insertReading(
            MeterReadingEntity(
                meterId = meterId,
                value = 10.0,
                notes = "test",
                recordedAt = 100L
            )
        )

        val reading = dao.getReadingById(readingId)
        assertNotNull(reading)
        assertEquals(10.0, reading.value, 0.0)
    }
}
