package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the display-index -> riti-original-index translation in isolation. */
class RitiEngineMappingTest {
    @Test
    fun mapsDisplayIndexToRitiOriginalIndex() {
        val indices = listOf(3, 1, 0)
        assertEquals(3, mapToOriginalIndex(0, indices))
        assertEquals(1, mapToOriginalIndex(1, indices))
        assertEquals(0, mapToOriginalIndex(2, indices))
    }

    @Test
    fun fallsBackToDisplayIndexWhenMappingIsMissing() {
        assertEquals(5, mapToOriginalIndex(5, listOf(3, 1, 0)))
        assertEquals(0, mapToOriginalIndex(0, emptyList()))
        assertEquals(7, mapToOriginalIndex(7, emptyList()))
    }
}
