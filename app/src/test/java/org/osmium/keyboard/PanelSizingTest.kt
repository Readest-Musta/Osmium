package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSizingTest {
    @Test
    fun clampsToUsableRange() {
        assertEquals(45, PanelSizing.heightPercent(10))
        assertEquals(75, PanelSizing.heightPercent(100))
        assertEquals(60, PanelSizing.heightPercent(60))
    }
}
