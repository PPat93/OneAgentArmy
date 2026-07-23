package com.parrotworks.oneagentarmy.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeoutFormatTest {

    @Test
    fun `formats whole minutes with zero-padded seconds`() {
        assertEquals("4:00", formatTimeout(240))
        assertEquals("15:00", formatTimeout(900))
    }

    @Test
    fun `formats minute-and-seconds values`() {
        assertEquals("1:30", formatTimeout(90))
        assertEquals("0:05", formatTimeout(5))
        assertEquals("0:00", formatTimeout(0))
    }

    @Test
    fun `parses valid m colon ss values`() {
        assertEquals(90, parseTimeout("1:30"))
        assertEquals(240, parseTimeout("4:00"))
        assertEquals(900, parseTimeout("15:00"))
        assertEquals(5, parseTimeout("0:05"))
    }

    @Test
    fun `parses with surrounding whitespace`() {
        assertEquals(150, parseTimeout(" 2:30 "))
    }

    @Test
    fun `rejects a bare number - ambiguous between seconds and shorthand`() {
        assertNull(parseTimeout("130"))
    }

    @Test
    fun `rejects single-digit seconds`() {
        assertNull(parseTimeout("1:5"))
    }

    @Test
    fun `rejects seconds of 60 or more`() {
        assertNull(parseTimeout("1:75"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(parseTimeout("abc"))
        assertNull(parseTimeout(""))
        assertNull(parseTimeout("1:30:00"))
    }

    @Test
    fun `format and parse round-trip`() {
        for (seconds in listOf(1, 59, 60, 90, 240, 899, 900)) {
            assertEquals(seconds, parseTimeout(formatTimeout(seconds)))
        }
    }
}
