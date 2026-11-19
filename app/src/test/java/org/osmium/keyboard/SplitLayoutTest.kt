package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLayoutTest {

    @Test
    fun longRowsSplitInTheMiddle() {
        assertEquals(5, SplitLayout.splitIndex(10))
        assertEquals(4, SplitLayout.splitIndex(9))
        assertEquals(4, SplitLayout.splitIndex(8))
    }

    @Test
    fun shortRowsStayWhole() {
        assertEquals(-1, SplitLayout.splitIndex(6))
        assertEquals(-1, SplitLayout.splitIndex(3))
    }
}
