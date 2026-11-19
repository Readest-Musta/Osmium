package org.osmium.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardAccessTest {

    @Test
    fun `incognito never captures clipboard text`() {
        assertFalse(ClipboardAccess.canCapture(incognito = true))
    }

    @Test
    fun `normal mode captures clipboard text`() {
        assertTrue(ClipboardAccess.canCapture(incognito = false))
    }
}
