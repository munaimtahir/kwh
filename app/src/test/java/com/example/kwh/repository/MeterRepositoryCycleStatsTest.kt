package com.example.kwh.repository

import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.data.MeterEntity
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.testing.FakeMeterDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeterRepositoryCycleStatsTest {

    private val zone = ZoneOffset.UTC
    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-25T00:00:00Z"), zone)
    private lateinit var dao: FakeMeterDao
    private lateinit var repository: MeterRepository

    @Before
    fun setUp() {
        dao = FakeMeterDao()
        repository = MeterRepository(dao, DefaultBillingCycleCalculator(), clock)
    }

    @Test
    fun `baseline uses earliest reading within window`() = runTest {
        val meterId = dao.insertMeter(
            MeterEntity(name = "Home", billingAnchorDay = 15, thresholdsCsv = "50,80")
        )
        dao.insertReading(reading(meterId, "2024-03-10", 100.0))
        val baselineId = dao.insertReading(reading(meterId, "2024-03-16", 110.0))
        val latestId = dao.insertReading(reading(meterId, "2024-03-24", 130.0))

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        assertEquals(baselineId, stats.baseline?.id)
        assertEquals(latestId, stats.latest?.id)
        assertEquals(20.0, stats.usedUnits, 1e-6)
    }

    @Test
    fun `baseline falls back to reading before window`() = runTest {
        val meterId = dao.insertMeter(
            MeterEntity(name = "Office", billingAnchorDay = 15)
        )
        dao.insertReading(reading(meterId, "2024-03-10", 200.0))
        dao.insertReading(reading(meterId, "2024-03-05", 180.0))

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        assertEquals(200.0, stats.baseline?.value)
        assertNull(stats.latest)
        assertEquals(0.0, stats.usedUnits, 1e-6)
    }

    @Test
    fun `projection handles zero rate`() = runTest {
        val meterId = dao.insertMeter(
            MeterEntity(name = "Lab", billingAnchorDay = 15)
        )
        dao.insertReading(reading(meterId, "2024-03-15", 100.0))
        dao.insertReading(reading(meterId, "2024-03-22", 100.0))

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        assertEquals(0.0, stats.projectedUnits)
    }

    @Test
    fun `projection rounds to nearest whole unit`() = runTest {
        val meterId = dao.insertMeter(
            MeterEntity(name = "Cabin", billingAnchorDay = 15)
        )
        dao.insertReading(reading(meterId, "2024-03-15", 100.0))
        dao.insertReading(reading(meterId, "2024-03-22", 105.0))

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        assertEquals(16.0, stats.projectedUnits)
    }

    @Test
    fun `threshold eta only returns values within cycle`() = runTest {
        val meterId = dao.insertMeter(
            MeterEntity(name = "Garden", billingAnchorDay = 15, thresholdsCsv = "50,80")
        )
        dao.insertReading(reading(meterId, "2024-03-15", 100.0))
        dao.insertReading(reading(meterId, "2024-03-25", 140.0))

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        assertEquals(50, stats.nextThreshold?.threshold)
        assertEquals(LocalDate.parse("2024-03-28"), stats.nextThreshold?.eta)
    }

    @Test
    fun `threshold eta omitted when beyond cycle end`() = runTest {
        val meterId = dao.insertMeter(
            MeterEntity(name = "Basement", billingAnchorDay = 15, thresholdsCsv = "200")
        )
        dao.insertReading(reading(meterId, "2024-03-15", 100.0))
        dao.insertReading(reading(meterId, "2024-03-25", 140.0))

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        assertNull(stats.nextThreshold)
    }

    private fun reading(meterId: Long, date: String, value: Double): MeterReadingEntity {
        return MeterReadingEntity(
            meterId = meterId,
            value = value,
            notes = null,
            recordedAt = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
}
