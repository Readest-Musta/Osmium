package org.osmium.keyboard

/**
 * Ready-made custom palettes. Each preset fills all six user-overridable colour
 * slots so applying one is a single, predictable action (no half-themed keyboard).
 */
data class ThemePreset(val name: String, val colors: Map<String, Int>)

object ThemePresets {

    private const val BG = "custom_color_keyboard_bg"
    private const val KEY = "custom_color_key"
    private const val TEXT = "custom_color_text"
    private const val ACCENT = "custom_color_accent"
    private const val BAR = "custom_color_bar"
    private const val PANEL = "custom_color_panel"

    val ALL: List<ThemePreset> = listOf(
        ThemePreset("Midnight", mapOf(
            BG to 0xFF121318.toInt(), KEY to 0xFF232733.toInt(), TEXT to 0xFFE8EAF0.toInt(),
            ACCENT to 0xFF4F8CFF.toInt(), BAR to 0xFF1A1D24.toInt(), PANEL to 0xFF121318.toInt(),
        )),
        ThemePreset("AMOLED", mapOf(
            BG to 0xFF000000.toInt(), KEY to 0xFF161616.toInt(), TEXT to 0xFFFFFFFF.toInt(),
            ACCENT to 0xFF00E5FF.toInt(), BAR to 0xFF0A0A0A.toInt(), PANEL to 0xFF000000.toInt(),
        )),
        ThemePreset("Paper", mapOf(
            BG to 0xFFEDEFF3.toInt(), KEY to 0xFFFFFFFF.toInt(), TEXT to 0xFF1B1E26.toInt(),
            ACCENT to 0xFF2E6BDF.toInt(), BAR to 0xFFF7F8FA.toInt(), PANEL to 0xFFE4E7ED.toInt(),
        )),
        ThemePreset("Forest", mapOf(
            BG to 0xFF101A16.toInt(), KEY to 0xFF1C2A23.toInt(), TEXT to 0xFFE6F2EA.toInt(),
            ACCENT to 0xFF4CAF7D.toInt(), BAR to 0xFF14201A.toInt(), PANEL to 0xFF101A16.toInt(),
        )),
    )

    fun preset(name: String): ThemePreset? = ALL.firstOrNull { it.name == name }
}
