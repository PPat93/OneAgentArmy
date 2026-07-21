package com.parrotworks.oneagentarmy.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNormalizationTest {

    @Test
    fun `lowercases plain ASCII text`() {
        assertEquals("hello world", normalizeForSearch("Hello World"))
    }

    @Test
    fun `strips Polish diacritics via NFD decomposition`() {
        assertEquals("glowa", normalizeForSearch("Głowa"))
        assertEquals("glowa", normalizeForSearch("GŁOWA"))
    }

    @Test
    fun `maps l-stroke explicitly since it does not decompose in Unicode`() {
        assertEquals("laka", normalizeForSearch("łąka"))
    }

    @Test
    fun `two differently-cased and accented spellings normalize to the same string`() {
        assertEquals(normalizeForSearch("wiadomość"), normalizeForSearch("WIADOMOŚĆ"))
    }
}
