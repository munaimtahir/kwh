package com.example.emt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun `isKwhValid returns true for positive numbers`() {
        assertTrue(isKwhValid("10"))
        assertTrue(isKwhValid("0.5"))
    }

    @Test
    fun `isKwhValid returns false for zero`() {
        assertFalse(isKwhValid("0"))
    }

    @Test
    fun `isKwhValid returns false for negative numbers`() {
        assertFalse(isKwhValid("-10"))
    }

    @Test
    fun `isKwhValid returns false for non-numeric input`() {
        assertFalse(isKwhValid("abc"))
    }

    private fun isKwhValid(kwh: String): Boolean {
        val kwhValue = kwh.toDoubleOrNull()
        return kwhValue != null && kwhValue > 0
    }
}
