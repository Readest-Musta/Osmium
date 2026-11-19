package org.osmium.keyboard

/**
 * Pure (Android-free) schema for raw settings-JSON import.
 *
 * A raw JSON file is untrusted input: writing, say, a String into an Int
 * preference makes the next `getInt()` throw ClassCastException inside the IME
 * and bricks the keyboard. [normalize] is the single gate every imported entry
 * passes through. It returns the canonical `(key, value)` pair, or `null` when
 * the entry must be ignored:
 *  - unknown keys (including the backup version marker) are dropped;
 *  - values must match the key's declared type;
 *  - numeric values are clamped to the same ranges the settings sliders use;
 *  - JSON's Long/Double number forms are folded back into Int.
 */
object SettingsSchema {

    private const val VERSION_KEY = "osmium_settings_version"

    private enum class Kind { BOOL, INT, TEXT }

    private data class Spec(val kind: Kind, val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE)

    private val SPECS: Map<String, Spec> = buildMap {
        // Behaviour / feedback — ranges mirror the sliders in MainActivity.
        put("haptics_enabled", Spec(Kind.BOOL))
        put("haptic_ms", Spec(Kind.INT, 2, 40))
        put("haptic_strength", Spec(Kind.INT, 20, 255))
        put("long_press_ms", Spec(Kind.INT, 150, 900))
        put("backspace_repeat_ms", Spec(Kind.INT, 20, 150))
        put("glide_cursor_enabled", Spec(Kind.BOOL))
        put("glide_sensitivity_dp", Spec(Kind.INT, 10, 60))
        put("glide_edge_repeat_ms", Spec(Kind.INT, 20, 150))
        put("swipe_delete_enabled", Spec(Kind.BOOL))
        put("swipe_step_dp", Spec(Kind.INT, 30, 100))
        put("double_space_dari", Spec(Kind.BOOL))
        put("auto_capitalize", Spec(Kind.BOOL))
        put("incognito_by_default", Spec(Kind.BOOL))

        // Appearance
        put("theme", Spec(Kind.TEXT))
        put("key_height_scale", Spec(Kind.INT, 80, 140))
        put("key_radius_dp", Spec(Kind.INT, 0, 18))
        put("key_font_scale", Spec(Kind.INT, 80, 150))
        put("bg_image_path", Spec(Kind.TEXT))
        put("bg_image_dim", Spec(Kind.INT, 0, 90))
        put("spacebar_label", Spec(Kind.BOOL))
        put("number_row_always", Spec(Kind.BOOL))
        // Suggestion-bar controls
        put("toolbar_clipboard", Spec(Kind.BOOL))
        put("toolbar_symbols", Spec(Kind.BOOL))
        put("toolbar_number", Spec(Kind.BOOL))
        put("toolbar_settings", Spec(Kind.BOOL))
        put("toolbar_nav", Spec(Kind.BOOL))
        put("one_handed_mode", Spec(Kind.TEXT))

        // Strings (values are parsed defensively by their readers)
        put("bangla_scheme", Spec(Kind.TEXT))
        put("selected_layout", Spec(Kind.TEXT))
        // T6 rollback marker: without a spec the layout-rollback state was the
        // only pref silently dropped on every settings import/export.
        put("previous_layout", Spec(Kind.TEXT))
        put("emoji_recents", Spec(Kind.TEXT))
        put("symbol_recents", Spec(Kind.TEXT))
        put("clipboard_items", Spec(Kind.TEXT))
        put("custom_color_keyboard_bg", Spec(Kind.TEXT))
        put("custom_color_key", Spec(Kind.TEXT))
        put("custom_color_text", Spec(Kind.TEXT))
        put("custom_color_accent", Spec(Kind.TEXT))
        put("custom_color_bar", Spec(Kind.TEXT))
        put("custom_color_panel", Spec(Kind.TEXT))
    }

    /** Returns the safe form of [value] for [key], or null to skip the entry. */
    fun normalize(key: String, value: Any?): Pair<String, Any>? {
        if (key == VERSION_KEY) return null
        val spec = SPECS[key] ?: return null
        return when (spec.kind) {
            Kind.BOOL -> if (value is Boolean) key to value else null
            Kind.TEXT -> if (value is String) key to value else null
            Kind.INT -> normalizeInt(key, value, spec.min, spec.max)
        }
    }

    private fun normalizeInt(key: String, value: Any?, min: Int, max: Int): Pair<String, Int>? {
        // Widen first so an out-of-range Long/Double clamps instead of wrapping
        // into a plausible in-range value via toInt() truncation.
        val wide: Long = when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Short -> value.toLong()
            is Byte -> value.toLong()
            is Double -> integralLong(value) ?: return null
            is Float -> integralLong(value.toDouble()) ?: return null
            else -> return null
        }
        return key to wide.coerceIn(min.toLong(), max.toLong()).toInt()
    }

    /** JSON has no int type; export writes ints as Long and parsers may return Double. */
    private fun integralLong(value: Double): Long? {
        if (!value.isFinite() || value != Math.floor(value)) return null
        return value.toLong()
    }
}
