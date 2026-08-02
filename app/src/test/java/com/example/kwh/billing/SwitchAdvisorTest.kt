package com.example.kwh.billing

import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class SwitchAdvisorTest {

    private val today = LocalDate.parse("2024-03-20")

    @Test
    fun `no forecast means no recommendation`() {
        val result = SwitchAdvisor.recommend(
            meterId = 1,
            forecast = null,
            safetyBufferDays = 3,
            today = today
        )

        assertNull(result)
    }

    @Test
    fun `plenty of days remaining is NORMAL urgency`() {
        val forecast = ThresholdForecast(threshold = 100, eta = today.plusDays(10))

        val result = SwitchAdvisor.recommend(1, forecast, safetyBufferDays = 3, today = today)

        assertEquals(today.plusDays(7), result?.switchByDate)
        assertEquals(SwitchUrgency.NORMAL, result?.urgency)
    }

    @Test
    fun `within the soon window is SOON urgency`() {
        // eta - buffer = today + 2, which is within the 2-day soon window.
        val forecast = ThresholdForecast(threshold = 100, eta = today.plusDays(5))

        val result = SwitchAdvisor.recommend(1, forecast, safetyBufferDays = 3, today = today)

        assertEquals(today.plusDays(2), result?.switchByDate)
        assertEquals(SwitchUrgency.SOON, result?.urgency)
    }

    @Test
    fun `switchByDate today or in the past is OVERDUE`() {
        val forecast = ThresholdForecast(threshold = 100, eta = today.plusDays(3))

        val result = SwitchAdvisor.recommend(1, forecast, safetyBufferDays = 3, today = today)

        assertEquals(today, result?.switchByDate)
        assertEquals(SwitchUrgency.OVERDUE, result?.urgency)
    }

    @Test
    fun `switchByDate already passed is OVERDUE`() {
        val forecast = ThresholdForecast(threshold = 100, eta = today.plusDays(1))

        val result = SwitchAdvisor.recommend(1, forecast, safetyBufferDays = 5, today = today)

        assertEquals(today.minusDays(4), result?.switchByDate)
        assertEquals(SwitchUrgency.OVERDUE, result?.urgency)
    }

    @Test
    fun `zero safety buffer switches exactly on the eta`() {
        val forecast = ThresholdForecast(threshold = 100, eta = today.plusDays(5))

        val result = SwitchAdvisor.recommend(1, forecast, safetyBufferDays = 0, today = today)

        assertEquals(today.plusDays(5), result?.switchByDate)
        assertEquals(SwitchUrgency.NORMAL, result?.urgency)
    }

    @Test
    fun `switchThresholdForecast targets the lowest threshold even when a higher one is next`() {
        // usedUnits (105) has already passed the first threshold (100), so plain nextThreshold
        // logic would target 200 next -- the switch target must stay pinned to 100 regardless.
        val forecast = SwitchAdvisor.switchThresholdForecast(
            thresholdsCsv = "100,200",
            usedUnits = 105.0,
            ratePerDay = 5.0,
            cycleEnd = today.plusDays(10),
            today = today
        )

        assertEquals(100, forecast?.threshold)
        // Already 5 units over target at 5/day => "should have switched" 1 day ago.
        assertEquals(today.minusDays(1), forecast?.eta)
    }

    @Test
    fun `switchThresholdForecast returns null when zero rate`() {
        val forecast = SwitchAdvisor.switchThresholdForecast(
            thresholdsCsv = "100",
            usedUnits = 50.0,
            ratePerDay = 0.0,
            cycleEnd = today.plusDays(10),
            today = today
        )

        assertNull(forecast)
    }

    @Test
    fun `switchThresholdForecast returns null when not yet crossed and projected beyond cycle end`() {
        val forecast = SwitchAdvisor.switchThresholdForecast(
            thresholdsCsv = "100",
            usedUnits = 10.0,
            ratePerDay = 1.0,
            cycleEnd = today.plusDays(5),
            today = today
        )

        assertNull(forecast)
    }

    @Test
    fun `switchThresholdForecast still fires when already crossed even beyond cycle end window`() {
        val forecast = SwitchAdvisor.switchThresholdForecast(
            thresholdsCsv = "100",
            usedUnits = 110.0,
            ratePerDay = 1.0,
            cycleEnd = today.plusDays(1),
            today = today
        )

        assertEquals(100, forecast?.threshold)
        assertEquals(today.minusDays(10), forecast?.eta)
    }
}
