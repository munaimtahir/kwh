package com.example.kwh.data

import com.example.kwh.testing.FakeMeterDao
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeterDaoTest {

    private lateinit var dao: FakeMeterDao

    @Before
    fun setUp() {
        dao = FakeMeterDao()
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
