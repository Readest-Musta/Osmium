package org.osmium.keyboard

import android.content.Context
import android.content.res.Configuration

/**
 * Colour palettes for the keyboard UI. Pure Kotlin (no resource lookup) so the
 * IME can re-theme its views at any time based on the user's preference.
 */
object Theme {

    data class Palette(
        val keyboardBg: Int,
        val keyNormal: Int,
        val keyPressed: Int,
        val keyAction: Int,
        val keyText: Int,
        val keyTextHint: Int,
        val accent: Int,
        val barBg: Int,
        val panelBg: Int,
        val popupBg: Int,
    )

    val dark = Palette(
        keyboardBg = 0xFF17191F.toInt(),
        keyNormal = 0xFF262A35.toInt(),
        keyPressed = 0xFF3A4050.toInt(),
        keyAction = 0xFF1E2530.toInt(),
        keyText = 0xFFE8EAF0.toInt(),
        keyTextHint = 0xFF8A90A0.toInt(),
        accent = 0xFF4F8CFF.toInt(),
        barBg = 0xFF1E2128.toInt(),
        panelBg = 0xFF17191F.toInt(),
        popupBg = 0xFF333947.toInt(),
    )

    val light = Palette(
        keyboardBg = 0xFFE4E7ED.toInt(),
        keyNormal = 0xFFFDFDFE.toInt(),
        keyPressed = 0xFFD2D7E2.toInt(),
        keyAction = 0xFFDFE3EC.toInt(),
        keyText = 0xFF1B1E26.toInt(),
        keyTextHint = 0xFF5C6470.toInt(),
        accent = 0xFF2E6BDF.toInt(),
        barBg = 0xFFEFF1F5.toInt(),
        panelBg = 0xFFECEEF2.toInt(),
        popupBg = 0xFFFFFFFF.toInt(),
    )

    /** "system" | "dark" | "light" */
    private const val PREF = "theme"
    private const val DEFAULT = "system"

    fun mode(context: Context): String =
        Prefs.sp(context).getString(PREF, DEFAULT) ?: DEFAULT

    fun setMode(context: Context, mode: String) {
        Prefs.sp(context).edit().putString(PREF, mode).apply()
    }

    fun isDark(context: Context): Boolean = when (mode(context)) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun palette(context: Context): Palette = if (isDark(context)) dark else light

    /** Changes when the effective palette changes — used to detect re-themes. */
    fun key(context: Context): String = "${mode(context)}:${isDark(context)}"
}
