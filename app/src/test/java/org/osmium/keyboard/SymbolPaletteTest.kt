package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolPaletteTest {

    private val allLists get() = listOf(SymbolPalette.PERIOD, SymbolPalette.COMMA)

    @Test
    fun `every entry has a non-blank label and unique code`() {
        for (list in allLists) {
            assertTrue(list.all { it.label.isNotBlank() && it.code.isNotBlank() })
            assertEquals(list.size, list.map { it.code }.distinct().size)
        }
    }

    @Test
    fun `direct is true exactly for non-ASCII codes`() {
        for (entry in SymbolPalette.PERIOD + SymbolPalette.COMMA) {
            if (entry.code == SymbolPalette.OPEN_SYMBOLS_CODE) continue
            val allAscii = entry.code.all { it.code < 128 }
            assertEquals(entry.code, allAscii, !entry.direct)
        }
    }

    @Test
    fun `palettes stay within the popup grid budget`() {
        for (list in allLists) assertTrue(list.size <= 24)
    }

    @Test
    fun `rows split evenly without dropping entries`() {
        assertEquals(SymbolPalette.PERIOD, SymbolPalette.rows(SymbolPalette.PERIOD, 8).flatten())
        assertEquals(SymbolPalette.COMMA, SymbolPalette.rows(SymbolPalette.COMMA, 8).flatten())
    }

    @Test
    fun `sentinel opens the picker and is never flagged direct`() {
        val sentinel = SymbolPalette.PERIOD.last()
        assertEquals(SymbolPalette.OPEN_SYMBOLS_CODE, sentinel.code)
        assertEquals("Σ", sentinel.label)
        assertTrue(!sentinel.direct)
    }
}
