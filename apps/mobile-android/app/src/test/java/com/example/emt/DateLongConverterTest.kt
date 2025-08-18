package com.example.emt

import com.example.emt.data.DateLongConverter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class DateLongConverterTest {

    private val converter = DateLongConverter()

    @Test
    fun dateToTimestamp_and_fromTimestamp_areCorrect() {
        val date = Date()
        val timestamp = converter.dateToTimestamp(date)
        val resultDate = converter.fromTimestamp(timestamp)
        assertEquals(date, resultDate)
    }

    @Test
    fun nullDate_returns_nullTimestamp() {
        val timestamp = converter.dateToTimestamp(null)
        assertEquals(null, timestamp)
    }

    @Test
    fun nullTimestamp_returns_nullDate() {
        val date = converter.fromTimestamp(null)
        assertEquals(null, date)
    }
}
