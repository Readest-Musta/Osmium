package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun reorderedSelectionSurvivesPunctuationWhenInverted() {
        // Regression for the display->original inversion when feeding the
        // selection back into riti on EVERY key (sendToRiti), not just on
        // commit. Riti reads the passed `selection` as its ORIGINAL candidate
        // index and echoes it back on punctuation keys; the native wrapper then
        // remaps it to a display slot via originalIndices.indexOf(...).
        //
        // Simulate a Bengali-first reorder whose display order holds riti's
        // originals [1, 3, 0, 2]. The user picks display slot 2 (riti original 0).
        val originalIndices = listOf(1, 3, 0, 2)
        val displaySelection = 2

        // FIX: invert the display index before feeding the punctuation key.
        val fedToRiti = mapToOriginalIndex(displaySelection, originalIndices)
        assertEquals(0, fedToRiti)
        // Riti echoes the fed index; the wrapper remaps it back to a display slot
        // that must equal the word the user was composing (no jump).
        assertEquals(displaySelection, originalIndices.indexOf(fedToRiti))

        // BUG (old behaviour): passing the display index (2) is read by riti as
        // original index 2, which remaps to display slot 3 — the wrong word is
        // composed and later committed on space.
        val buggyFedToRiti = displaySelection
        assertEquals(3, originalIndices.indexOf(buggyFedToRiti))
        assertNotEquals(displaySelection, originalIndices.indexOf(buggyFedToRiti))

        // A non-reordered list is unaffected (inversion is a no-op), which is
        // why this only surfaced after the Bengali-first reorder / candidate pick.
        val identity = listOf(0, 1, 2, 3)
        assertEquals(2, mapToOriginalIndex(2, identity))
        assertEquals(2, identity.indexOf(mapToOriginalIndex(2, identity)))
    }
}
