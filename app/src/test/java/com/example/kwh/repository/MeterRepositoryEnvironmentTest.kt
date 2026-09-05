package com.example.kwh.repository

import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.billing.SwitchUrgency
import com.example.kwh.data.MeterActivationEntity
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
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeterRepositoryEnvironmentTest {

    private val zone = ZoneOffset.UTC
    private var now = Instant.parse("2024-03-25T00:00:00Z")
    private val clock: Clock = object : Clock() {
        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
    private lateinit var dao: FakeMeterDao
    private lateinit var repository: MeterRepository

    @Before
    fun setUp() {
        dao = FakeMeterDao()
        repository = MeterRepository(dao, clock, DefaultBillingCycleCalculator())
    }

    @Test
    fun `createEnvironment and assignMeterToEnvironment link a meter`() = runTest {
        val envId = repository.createEnvironment("  House  ")
        val meterId = dao.insertMeter(MeterEntity(name = "Meter A"))

        val updated = repository.assignMeterToEnvironment(meterId, envId)

        assertEquals(envId, updated?.environmentId)
        assertEquals("House", repository.environments.first().single { it.id == envId }.name)
    }

    @Test
    fun `activateMeter opens first activation for a newly assigned meter`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterId = dao.insertMeter(MeterEntity(name = "Meter A", environmentId = envId))

        val result = repository.activateMeter(meterId)

        assertEquals(meterId, result?.id)
        assertEquals(meterId, repository.getActiveMeter(envId)?.id)
        assertTrue(repository.isMeterActive(meterId).first())
    }

    @Test
    fun `activateMeter is idempotent for the already-active meter`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterId = dao.insertMeter(MeterEntity(name = "Meter A", environmentId = envId))
        repository.activateMeter(meterId)
        val historyBefore = repository.activationHistory(meterId).first()

        repository.activateMeter(meterId)
        val historyAfter = repository.activationHistory(meterId).first()

        assertEquals(historyBefore.size, historyAfter.size)
        assertEquals(1, historyAfter.size)
    }

    @Test
    fun `activateMeter switches active meter and closes the prior activation`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterAId = dao.insertMeter(MeterEntity(name = "Meter A", environmentId = envId))
        val meterBId = dao.insertMeter(MeterEntity(name = "Meter B", environmentId = envId))
        repository.activateMeter(meterAId)

        now = now.plusSeconds(3600)
        repository.activateMeter(meterBId)

        assertEquals(meterBId, repository.getActiveMeter(envId)?.id)
        assertTrue(!repository.isMeterActive(meterAId).first())
        assertTrue(repository.isMeterActive(meterBId).first())

        val historyA = repository.activationHistory(meterAId).first()
        assertEquals(1, historyA.size)
        assertEquals(now.toEpochMilli(), historyA.single().endedAt)
    }

    @Test
    fun `activateMeter returns null for a meter not in an environment`() = runTest {
        val meterId = dao.insertMeter(MeterEntity(name = "Solo"))

        val result = repository.activateMeter(meterId)

        assertNull(result)
        assertTrue(!repository.isMeterActive(meterId).first())
    }

    @Test
    fun `deleteEnvironment unassigns its meters instead of leaving a dangling reference`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterId = dao.insertMeter(MeterEntity(name = "Meter A", environmentId = envId))
        repository.activateMeter(meterId)

        val deleted = repository.deleteEnvironment(envId)

        assertTrue(deleted)
        val meter = dao.getMeterById(meterId)
        assertNull(meter?.environmentId)
    }

    @Test
    fun `environmentOverviews groups meters and reports the active meter`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterAId = dao.insertMeter(MeterEntity(name = "Meter A", environmentId = envId))
        dao.insertMeter(MeterEntity(name = "Meter B", environmentId = envId))
        dao.insertMeter(MeterEntity(name = "Solo"))
        repository.activateMeter(meterAId)

        val overviews = repository.environmentOverviews.first()
        val ungrouped = repository.ungroupedMeterOverviews.first()

        assertEquals(1, overviews.size)
        assertEquals(2, overviews.single().meters.size)
        assertEquals(meterAId, overviews.single().activeMeterId)
        assertEquals(1, ungrouped.size)
        assertEquals("Solo", ungrouped.single().meter.name)
    }

    @Test
    fun `active-day-weighted rate ignores idle gaps for meters in an environment`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterId = dao.insertMeter(
            MeterEntity(name = "Meter A", billingAnchorDay = 1, thresholdsCsv = "200", environmentId = envId)
        )
        dao.insertReading(reading(meterId, "2024-03-01", 100.0))
        dao.insertReading(reading(meterId, "2024-03-25", 128.0))

        // Active 03-01..03-06 (5 days), idle 03-06..03-16 (10 days, another meter carrying load),
        // active again 03-16..now=03-25 (9 days). Calendar-elapsed would be 24 days.
        dao.insertActivation(
            MeterActivationEntity(meterId = meterId, startedAt = millisOf("2024-03-01"), endedAt = millisOf("2024-03-06"))
        )
        dao.insertActivation(
            MeterActivationEntity(meterId = meterId, startedAt = millisOf("2024-03-16"), endedAt = null)
        )

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        assertEquals(28.0, stats.usedUnits, 1e-6)
        assertEquals(2.0, stats.ratePerDay, 1e-6)
    }

    @Test
    fun `meters without activation rows keep calendar-elapsed rate even when in an environment`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterId = dao.insertMeter(
            MeterEntity(name = "Meter A", billingAnchorDay = 1, thresholdsCsv = "200", environmentId = envId)
        )
        dao.insertReading(reading(meterId, "2024-03-01", 100.0))
        dao.insertReading(reading(meterId, "2024-03-25", 128.0))

        val stats = repository.getCycleStats(meterId)

        assertNotNull(stats)
        // No activation rows recorded yet: falls back to calendar-elapsed days (24).
        assertEquals(28.0 / 24.0, stats.ratePerDay, 1e-6)
    }

    @Test
    fun `environmentOverviews surfaces a switch recommendation targeting the least-used sibling`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterAId = dao.insertMeter(
            MeterEntity(name = "Meter A", billingAnchorDay = 1, thresholdsCsv = "50", environmentId = envId)
        )
        val meterBId = dao.insertMeter(MeterEntity(name = "Meter B", environmentId = envId))
        val meterCId = dao.insertMeter(MeterEntity(name = "Meter C", environmentId = envId))
        dao.insertReading(reading(meterBId, "2024-02-26", 100.0))
        dao.insertReading(reading(meterBId, "2024-03-25", 110.0))
        dao.insertReading(reading(meterCId, "2024-02-26", 100.0))
        dao.insertReading(reading(meterCId, "2024-03-25", 120.0))
        dao.insertReading(reading(meterAId, "2024-02-26", 0.0))
        dao.insertReading(reading(meterAId, "2024-03-25", 48.0))
        repository.activateMeter(meterAId)

        val overview = repository.environmentOverviews.first().single()

        assertNotNull(overview.switchRecommendation)
        assertEquals(SwitchUrgency.OVERDUE, overview.switchRecommendation?.urgency)
        assertEquals(meterBId, overview.switchTargetMeterId)
        val recommendation = overview.switchRecommendation!!
        assertEquals(LocalDate.parse("2024-03-23"), recommendation.switchByDate)
        repository.activateMeter(meterBId)
        val outcome = repository.switchOutcomes(meterAId).first().single()
        assertEquals(48.0, outcome.unitsAtSwitch)
        assertEquals(50, outcome.thresholdTarget)
        assertEquals(millisOf("2024-03-23"), outcome.recommendedDate)
    }

    @Test
    fun `safety buffer tightens after repeated switches with comfortable margin, clamped at the minimum`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterAId = dao.insertMeter(
            MeterEntity(name = "Meter A", billingAnchorDay = 1, thresholdsCsv = "100", environmentId = envId)
        )
        val meterBId = dao.insertMeter(MeterEntity(name = "Meter B", environmentId = envId))
        dao.insertReading(reading(meterAId, "2024-03-01", 0.0))

        repository.activateMeter(meterAId)
        repeat(6) {
            dao.insertReading(
                MeterReadingEntity(meterId = meterAId, value = 70.0, notes = null, recordedAt = now.toEpochMilli())
            )
            now = now.plusSeconds(7_200)
            repository.activateMeter(meterBId) // logs an outcome for A (comfortable margin: 100-70=30)
            now = now.plusSeconds(7_200)
            repository.activateMeter(meterAId) // reactivate for the next iteration
        }

        val outcomes = repository.switchOutcomes(meterAId).first()
        val accuracy = repository.calibrationAccuracy(meterAId).first()
        val finalMeter = dao.getMeterById(meterAId)

        assertEquals(6, outcomes.size)
        assertNotNull(accuracy)
        assertEquals(30.0, accuracy.averageUnitsMargin, 1e-6)
        assertEquals(MeterRepository.MIN_SAFETY_BUFFER_DAYS, finalMeter?.safetyBufferDays)
    }

    @Test
    fun `safety buffer widens after repeated switches landing over threshold, clamped at the maximum`() = runTest {
        val envId = repository.createEnvironment("House")
        val meterAId = dao.insertMeter(
            MeterEntity(name = "Meter A", billingAnchorDay = 1, thresholdsCsv = "100", environmentId = envId)
        )
        val meterBId = dao.insertMeter(MeterEntity(name = "Meter B", environmentId = envId))
        dao.insertReading(reading(meterAId, "2024-03-01", 0.0))

        repository.activateMeter(meterAId)
        repeat(10) {
            dao.insertReading(
                MeterReadingEntity(meterId = meterAId, value = 110.0, notes = null, recordedAt = now.toEpochMilli())
            )
            now = now.plusSeconds(7_200)
            repository.activateMeter(meterBId) // logs an outcome for A (already over: 100-110=-10)
            now = now.plusSeconds(7_200)
            repository.activateMeter(meterAId)
        }

        val accuracy = repository.calibrationAccuracy(meterAId).first()
        val finalMeter = dao.getMeterById(meterAId)

        assertNotNull(accuracy)
        assertEquals(-10.0, accuracy.averageUnitsMargin, 1e-6)
        assertEquals(MeterRepository.MAX_SAFETY_BUFFER_DAYS, finalMeter?.safetyBufferDays)
    }

    private fun millisOf(date: String): Long =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun reading(meterId: Long, date: String, value: Double): MeterReadingEntity {
        return MeterReadingEntity(
            meterId = meterId,
            value = value,
            notes = null,
            recordedAt = millisOf(date)
        )
    }
}
