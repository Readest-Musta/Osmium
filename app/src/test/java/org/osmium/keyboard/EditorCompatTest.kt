package org.osmium.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCompatTest {

    @Test
    fun whatsappNeedsDirectCommit() {
        assertTrue(EditorCompat.needsDirectCommit("com.whatsapp", 0x00000001))
        assertTrue(EditorCompat.needsDirectCommit("com.whatsapp.w4b", 0x00000001))
        assertFalse(EditorCompat.needsDirectCommit("com.example.app", 0x00000001))
    }

    @Test
    fun webEditFieldsNeedDirectCommit() {
        assertTrue(EditorCompat.needsDirectCommit(null, 0x00000061))
    }

    @Test
    fun composingProbeDetectsMissingText() {
        assertTrue(EditorCompat.composingStuck("আমি", "আমি"))
        assertFalse(EditorCompat.composingStuck("", "আমি"))
        assertFalse(EditorCompat.composingStuck(null, "আমি"))
    }
}
