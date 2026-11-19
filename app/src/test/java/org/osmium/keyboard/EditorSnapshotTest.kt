package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure decision logic behind [editorSnapshot]. The InputConnection calls
 * themselves need an Android runtime; these tests pin the offset handling that
 * decides whether a candidate read is usable (and therefore whether the
 * getExtractedText() fallback must be taken).
 */
class EditorSnapshotTest {

    @Test
    fun invalidCandidateOffsetsAreRejectedSoCallerFallsBack() {
        // takeSnapshot() returning null is handled at the call site; these are
        // the equally invalid "snapshot present but offsets unusable" cases
        // that must also fall through to the single getExtractedText() read.
        assertNull(editorSnapshotOf("abc", -1, 0, 0, selectionIsAbsolute = true))
        assertNull(editorSnapshotOf("abc", 0, -1, 2, selectionIsAbsolute = true))
        assertNull(editorSnapshotOf("abc", 0, 1, -1, selectionIsAbsolute = true))
        assertNull(editorSnapshotOf("abc", 5, -1, 6, selectionIsAbsolute = false))
    }

    @Test
    fun absoluteSnapshotOffsetsArePreserved() {
        // TextSnapshot reports selection offsets relative to the editor, not
        // to the surrounding-text window, so no startOffset is added.
        val snap = editorSnapshotOf("world", 5, 8, 8, selectionIsAbsolute = true)
        assertEquals(EditorSnapshot("world", 5, 8, 8), snap)
        assertEquals(3, snap!!.relativeSelection)
    }

    @Test
    fun relativeExtractedTextOffsetsBecomeAbsolute() {
        // ExtractedText.selectionStart/End are window-relative; startOffset is
        // the absolute base. Both read paths must produce the same snapshot.
        val fallback = editorSnapshotOf("world", 5, 3, 3, selectionIsAbsolute = false)
        assertEquals(EditorSnapshot("world", 5, 8, 8), fallback)
        assertEquals(
            editorSnapshotOf("world", 5, 8, 8, selectionIsAbsolute = true),
            fallback
        )
    }

    @Test
    fun nullTextIsAnEmptyWindowButStillValid() {
        val snap = editorSnapshotOf(null, 0, 0, 0, selectionIsAbsolute = true)
        assertEquals(EditorSnapshot("", 0, 0, 0), snap)
    }
}
