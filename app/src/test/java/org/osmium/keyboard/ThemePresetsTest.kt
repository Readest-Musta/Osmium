package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetsTest {

    @Test
    fun everyPresetDefinesAllSixColourSlots() {
        for (preset in ThemePresets.ALL) {
            assertEquals(Prefs.customColorKeys().toSet(), preset.colors.keys)
            assertTrue(preset.colors.values.all { (it and 0xFF000000.toInt()) != 0 })
        }
    }

    @Test
    fun presetLookupIsCaseSensitiveAndSafe() {
        assertEquals("AMOLED", ThemePresets.preset("AMOLED")?.name)
        assertTrue(ThemePresets.preset("missing") == null)
    }
}
