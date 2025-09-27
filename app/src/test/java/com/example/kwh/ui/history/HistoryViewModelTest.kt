package com.example.kwh.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryViewModelTest {

    private val parser = HistoryViewModel.CsvParser()

    @Test
    fun `parse handles CRLF endings`() {
        val csv = buildString {
            appendLine("meter_id,recorded_at,value,notes")
            append("1,1700000000000,123.45, note with spaces \r\n")
        }

        val rows = parser.parse(csv)

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(1L, row.meterId)
        assertEquals(1700000000000L, row.recordedAtEpochMillis)
        assertEquals(123.45, row.value, 0.0)
        assertEquals("note with spaces", row.notes)
    }

    @Test
    fun `parse handles missing notes`() {
        val csv = "meter_id,recorded_at,value\r\n2,1700000001000,42.0\r\n"

        val rows = parser.parse(csv)

        assertEquals(1, rows.size)
        assertNull(rows.first().notes)
    }
}
