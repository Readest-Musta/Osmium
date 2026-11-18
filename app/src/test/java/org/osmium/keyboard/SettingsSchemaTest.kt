package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Validation rules for raw settings-JSON import, exercised against the pure
 * [SettingsSchema] helper (no Android runtime required).
 */
class SettingsSchemaTest {

    @Test
    fun wrongTypeIsSkipped() {
        // A string where a boolean is expected is the classic way a raw import
        // bricks the keyboard: getBoolean() would later throw ClassCastException.
        assertNull(SettingsSchema.normalize("haptics_enabled", "true"))
        assertNull(SettingsSchema.normalize("toolbar_nav", "false"))
        // Integers where booleans/strings are expected.
        assertNull(SettingsSchema.normalize("incognito_by_default", 1))
        assertNull(SettingsSchema.normalize("bangla_scheme", true))
        // Strings where an int is expected.
        assertNull(SettingsSchema.normalize("long_press_ms", "350"))
        assertNull(SettingsSchema.normalize("key_height_scale", "100"))
        // JSON null / unknown object types.
        assertNull(SettingsSchema.normalize("haptic_ms", null))
    }

    @Test
    fun numericValuesAreClampedToSliderRanges() {
        // long-press slider is 150..900
        assertEquals("long_press_ms" to 900, SettingsSchema.normalize("long_press_ms", 5000))
        assertEquals("long_press_ms" to 150, SettingsSchema.normalize("long_press_ms", 10))
        // backspace repeat slider is 20..150
        assertEquals("backspace_repeat_ms" to 150, SettingsSchema.normalize("backspace_repeat_ms", 1000))
        assertEquals("backspace_repeat_ms" to 20, SettingsSchema.normalize("backspace_repeat_ms", 1))
        // height slider is 80..140, radius 0..18
        assertEquals("key_height_scale" to 140, SettingsSchema.normalize("key_height_scale", 500))
        assertEquals("key_radius_dp" to 0, SettingsSchema.normalize("key_radius_dp", -5))
        // Large JSON integers (written as Long) must clamp, not overflow into a valid value.
        assertEquals("haptic_ms" to 40, SettingsSchema.normalize("haptic_ms", 100_000_000_000L))
    }

    @Test
    fun unknownKeysAreSkipped() {
        assertNull(SettingsSchema.normalize("not_a_setting", 5))
        assertNull(SettingsSchema.normalize("theme_", "dark"))
        assertNull(SettingsSchema.normalize("", true))
    }

    @Test
    fun versionMarkerIsSkipped() {
        assertNull(SettingsSchema.normalize("osmium_settings_version", 1))
        assertNull(SettingsSchema.normalize("osmium_settings_version", "1"))
    }

    @Test
    fun validKeysRoundTrip() {
        val valid = listOf(
            "bangla_scheme" to "probhat" as Any,
            "theme" to "dark" as Any,
            "haptics_enabled" to false as Any,
            "haptic_ms" to 25 as Any,
            "haptic_strength" to 180 as Any,
            "long_press_ms" to 350 as Any,
            "backspace_repeat_ms" to 60 as Any,
            "glide_cursor_enabled" to true as Any,
            "glide_sensitivity_dp" to 22 as Any,
            "key_height_scale" to 100 as Any,
            "bg_image_path" to "" as Any,
            "custom_color_accent" to "4F8CFF" as Any,
            "clipboard_items" to "[]" as Any,
            "toolbar_settings" to true as Any,
            "selected_layout" to "my-layout.json" as Any,
        )
        for ((key, value) in valid) {
            assertEquals(key to value, SettingsSchema.normalize(key, value))
        }
    }

    @Test
    fun jsonNumberFormsAreAcceptedForIntKeys() {
        // exportJson writes ints as Long; org.json may also hand back integral Doubles.
        assertEquals("long_press_ms" to 350, SettingsSchema.normalize("long_press_ms", 350L))
        assertEquals("long_press_ms" to 350, SettingsSchema.normalize("long_press_ms", 350.0))
        assertEquals("haptic_ms" to 2, SettingsSchema.normalize("haptic_ms", 2.0f))
    }

    @Test
    fun nonIntegralNumbersAreSkipped() {
        assertNull(SettingsSchema.normalize("long_press_ms", 350.7))
        assertNull(SettingsSchema.normalize("haptic_ms", Double.NaN))
        assertNull(SettingsSchema.normalize("haptic_ms", Double.POSITIVE_INFINITY))
    }
}
