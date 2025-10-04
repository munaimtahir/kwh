package com.example.kwh.billing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultBillingCycleCalculatorTest {

    private val calculator = DefaultBillingCycleCalculator()
    private val zone: ZoneId = ZoneOffset.UTC

    @Test
    fun `anchor within current month uses same month`() {
        val clock = Clock.fixed(Instant.parse("2024-05-20T12:00:00Z"), zone)

        val window = calculator.currentWindow(anchorDay = 15, clock = clock)

        assertEquals("2024-05-15", window.start.atZone(zone).toLocalDate().toString())
        assertEquals("2024-06-15", window.end.atZone(zone).toLocalDate().toString())
    }

    @Test
    fun `anchor beyond month length falls back to previous month`() {
        val clock = Clock.fixed(Instant.parse("2023-03-30T00:00:00Z"), zone)

        val window = calculator.currentWindow(anchorDay = 31, clock = clock)

        assertEquals("2023-02-28", window.start.atZone(zone).toLocalDate().toString())
        assertEquals("2023-03-28", window.end.atZone(zone).toLocalDate().toString())
    }

    @Test
    fun `anchor 29 handles february in non leap year`() {
        val clock = Clock.fixed(Instant.parse("2023-02-20T00:00:00Z"), zone)

        val window = calculator.currentWindow(anchorDay = 29, clock = clock)

        assertEquals("2023-01-29", window.start.atZone(zone).toLocalDate().toString())
        assertEquals("2023-02-28", window.end.atZone(zone).toLocalDate().toString())
    }

    @Test
    fun `anchor 30 handles february in non leap year`() {
        val clock = Clock.fixed(Instant.parse("2023-02-20T00:00:00Z"), zone)

        val window = calculator.currentWindow(anchorDay = 30, clock = clock)

        assertEquals("2023-01-30", window.start.atZone(zone).toLocalDate().toString())
        assertEquals("2023-02-28", window.end.atZone(zone).toLocalDate().toString())
    }

    @Test
    fun `anchor 31 handles february in non leap year`() {
        val clock = Clock.fixed(Instant.parse("2023-02-20T00:00:00Z"), zone)

        val window = calculator.currentWindow(anchorDay = 31, clock = clock)

        assertEquals("2023-01-31", window.start.atZone(zone).toLocalDate().toString())
        assertEquals("2023-02-28", window.end.atZone(zone).toLocalDate().toString())
    }

    @Test
    fun `leap year preserves february 29 for anchor 31`() {
        val clock = Clock.fixed(Instant.parse("2024-02-20T00:00:00Z"), zone)

        val window = calculator.currentWindow(anchorDay = 31, clock = clock)

        assertEquals("2024-01-31", window.start.atZone(zone).toLocalDate().toString())
        assertEquals("2024-02-29", window.end.atZone(zone).toLocalDate().toString())
    }

    @Test
    fun `invalid anchor throws`() {
        val clock = Clock.fixed(Instant.parse("2024-05-20T00:00:00Z"), zone)

        assertFailsWith<IllegalArgumentException> {
            calculator.currentWindow(anchorDay = 0, clock = clock)
        }
    }
}
