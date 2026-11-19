package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorActionsTest {

    @Test
    fun directCopyOnlyWhenActionFailedAndTextSelected() {
        assertTrue(EditorActions.needsDirectCopy(false, "hello"))
        assertFalse(EditorActions.needsDirectCopy(true, "hello"))
        assertFalse(EditorActions.needsDirectCopy(false, ""))
        assertFalse(EditorActions.needsDirectCopy(false, null))
    }

    @Test
    fun safeSelectionNeverExceedsField() {
        assertEquals(0 to 10, EditorActions.safeSelection(-5, 10))
        assertEquals(4 to 10, EditorActions.safeSelection(4, 10))
    }
}
