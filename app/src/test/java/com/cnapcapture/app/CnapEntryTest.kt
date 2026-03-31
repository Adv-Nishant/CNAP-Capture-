package com.cnapcapture.app

import com.cnapcapture.app.data.CnapEntry
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for data-layer logic that does not require an Android runtime context.
 */
class CnapEntryTest {

    // -----------------------------------------------------------------------
    // CnapEntry model tests
    // -----------------------------------------------------------------------

    @Test
    fun `new entry defaults to non-repeat caller`() {
        val entry = CnapEntry(
            phoneNumber = "+919876543210",
            cnapName = "RAJESH KUMAR",
            timestampMs = System.currentTimeMillis(),
            source = "TELECOM_API"
        )
        assertFalse("Default isRepeatCaller should be false", entry.isRepeatCaller)
    }

    @Test
    fun `repeat caller entry stores flag correctly`() {
        val entry = CnapEntry(
            phoneNumber = "+919876543210",
            cnapName = "RAJESH KUMAR",
            timestampMs = System.currentTimeMillis(),
            source = "ACCESSIBILITY",
            isRepeatCaller = true
        )
        assertTrue(entry.isRepeatCaller)
    }

    @Test
    fun `entry equality is based on all fields`() {
        val ts = 1_700_000_000_000L
        val a = CnapEntry(1L, "+919876543210", "RAJESH KUMAR", ts, "TELECOM_API", false)
        val b = CnapEntry(1L, "+919876543210", "RAJESH KUMAR", ts, "TELECOM_API", false)
        assertEquals(a, b)
    }

    @Test
    fun `entries with different ids are not equal`() {
        val ts = 1_700_000_000_000L
        val a = CnapEntry(1L, "+919876543210", "RAJESH KUMAR", ts, "TELECOM_API", false)
        val b = CnapEntry(2L, "+919876543210", "RAJESH KUMAR", ts, "TELECOM_API", false)
        assertNotEquals(a, b)
    }

    @Test
    fun `entries with different names are not equal`() {
        val ts = 1_700_000_000_000L
        val a = CnapEntry(1L, "+919876543210", "RAJESH KUMAR", ts, "TELECOM_API", false)
        val b = CnapEntry(1L, "+919876543210", "PRIYA SHARMA", ts, "TELECOM_API", false)
        assertNotEquals(a, b)
    }

    // -----------------------------------------------------------------------
    // Source constant tests
    // -----------------------------------------------------------------------

    @Test
    fun `source constants have expected values`() {
        assertEquals("TELECOM_API", com.cnapcapture.app.service.CallMonitorService.SOURCE_TELECOM_API)
        assertEquals("ACCESSIBILITY", com.cnapcapture.app.service.CallMonitorService.SOURCE_ACCESSIBILITY)
    }

    // -----------------------------------------------------------------------
    // Timestamp tests
    // -----------------------------------------------------------------------

    @Test
    fun `timestamp stored as unix epoch millis`() {
        val now = System.currentTimeMillis()
        val entry = CnapEntry(
            phoneNumber = "+911234567890",
            cnapName = "TEST NAME",
            timestampMs = now,
            source = "TELECOM_API"
        )
        assertEquals(now, entry.timestampMs)
        // A unix epoch in milliseconds is a 13-digit number for dates after year 2001.
        assertTrue(entry.timestampMs.toString().length == 13)
    }
}
