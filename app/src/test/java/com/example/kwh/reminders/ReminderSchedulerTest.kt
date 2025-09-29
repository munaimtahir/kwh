package com.example.kwh.reminders

import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertTrue

class ReminderSchedulerTest {

    @Test
    fun nextReminderTime_neverReturnsPast() {
        val next = ReminderScheduler.nextReminderTime(frequencyDays = 1, hour = 0, minute = 0)
        val threshold = ZonedDateTime.now().plusMinutes(1)
        assertTrue(next.isAfter(threshold) || next.isEqual(threshold))
    }

    @Test
    fun nextReminderDelay_isNonNegative() {
        val delay = ReminderScheduler.nextReminderDelay(1, 23, 59)
        assertTrue(!delay.isNegative)
    }
}
