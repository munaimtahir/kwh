package com.example.kwh.repository

import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.billing.SwitchAdvisor
import com.example.kwh.billing.SwitchUrgency
import com.example.kwh.data.MeterEntity
import com.example.kwh.testing.FakeMeterDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StabilizationTest {
    private val dao = FakeMeterDao()
    private val clock = Clock.fixed(Instant.parse("2024-03-10T00:00:00Z"), ZoneOffset.UTC)
    private val repository = MeterRepository(dao, clock, DefaultBillingCycleCalculator())
    private fun time(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private suspend fun add(id: Long, date: String, value: Double) = repository.addReading(id, value, null, time(date))
    private suspend fun usage(id: Long, expected: Double) {
        val stats = assertNotNull(repository.getCycleStats(id))
        assertEquals(expected, stats.usedUnits)
        assertEquals(stats, repository.meterOverview(id).first()?.cycleStats)
    }

    @Test fun `carry forward backdating and deletion recompute both paths`() = runTest {
        val id = dao.insertMeter(MeterEntity(name = "Home"))
        val latest = add(id, "2024-03-10", 14125.0)
        usage(id, 0.0) // No invented usage before first known reading.
        val baseline = add(id, "2024-02-26", 14000.0)
        usage(id, 125.0)
        val older = add(id, "2024-02-20", 13900.0)
        usage(id, 125.0)
        add(id, "2024-03-05", 14050.0)
        usage(id, 125.0)
        repository.deleteReading(baseline)
        usage(id, 225.0)
        repository.deleteReading(older)
        usage(id, 75.0)
        repository.deleteReading(latest)
        usage(id, 0.0)
    }

    @Test fun `ordinary entries respect both chronological neighbors and duplicates`() = runTest {
        val id = dao.insertMeter(MeterEntity(name = "Home"))
        add(id, "2024-03-01", 15840.0)
        add(id, "2024-03-10", 16000.0)
        assertFailsWith<IllegalArgumentException> { add(id, "2024-03-11", 15480.0) }
        assertFailsWith<IllegalArgumentException> { add(id, "2024-03-05", 15800.0) }
        assertFailsWith<IllegalArgumentException> { add(id, "2024-03-05", 16001.0) }
        assertFailsWith<IllegalArgumentException> { add(id, "2024-02-28", 16001.0) }
        add(id, "2024-03-05", 15900.0) // Must not compare against today's latest.
        assertFailsWith<IllegalArgumentException> { add(id, "2024-03-05", 15900.0) }
        assertFailsWith<IllegalArgumentException> {
            repository.addReading(id, 15900.0, null, time("2024-03-05") + 1000)
        }
        assertFailsWith<IllegalArgumentException> { add(id, "2024-03-05", 15901.0) }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0)) {
            assertFailsWith<IllegalArgumentException> { add(id, "2024-03-11", value) }
        }
        add(id, "2024-03-06", 15900.0) // Equal cumulative values on different dates are valid.
        val other = dao.insertMeter(MeterEntity(name = "Other"))
        add(other, "2024-03-05", 15900.0)
        assertEquals(4, repository.readingsForMeter(id).first().size)
    }

    @Test fun `configured threshold boundaries preserve forecast and crossed switch behavior`() = runTest {
        val thresholds = listOf(200, 300, 400)
        for (threshold in thresholds) for (used in listOf(threshold - 1, threshold, threshold + 1)) {
            val id = dao.insertMeter(MeterEntity(name = "$used", thresholdsCsv = thresholds.joinToString(",")))
            add(id, "2024-02-26", 14000.0)
            add(id, "2024-03-10", 14000.0 + used)
            usage(id, used.toDouble())
            val stats = assertNotNull(repository.getCycleStats(id))
            assertEquals(used / 9.0, stats.ratePerDay)
            val next = thresholds.firstOrNull { it > used }
            assertEquals(next, stats.nextThreshold?.threshold)
            if (next != null) assertEquals(
                LocalDate.now(clock).plusDays(ceil((next - used) / stats.ratePerDay).toLong()),
                stats.nextThreshold?.eta
            )
            val forecast = assertNotNull(SwitchAdvisor.switchThresholdForecast(
                thresholds.joinToString(","), stats.usedUnits, stats.ratePerDay,
                LocalDate.parse("2024-04-01"), LocalDate.now(clock)
            ))
            assertEquals(thresholds.first(), forecast.threshold)
            assertEquals(used >= thresholds.first(), forecast.eta <= LocalDate.now(clock))
            assertEquals(SwitchUrgency.OVERDUE, SwitchAdvisor.recommend(id, forecast, 3, LocalDate.now(clock))?.urgency)
        }
    }
}
