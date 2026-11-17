package org.osmium.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Colour palettes for the keyboard UI. Pure Kotlin (no resource lookup) so the
 * IME can re-theme its views at any time based on the user's preference.
 *
 * Modes: "system" (follow the OS dark setting), "dark", "light", or "custom"
 * (base palette + per-colour overrides the user picked).
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

    /** "system" | "dark" | "light" | "custom" */
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
        "custom" -> (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun customPalette(context: Context): Palette {
        val base = if (isDark(context)) dark else light
        fun pick(key: String, fallback: Int): Int =
            Prefs.customColor(context, key) ?: fallback
        val keyNormal = pick("custom_color_key", base.keyNormal)
        val keyText = pick("custom_color_text", base.keyText)
        return Palette(
            keyboardBg = pick("custom_color_keyboard_bg", base.keyboardBg),
            keyNormal = keyNormal,
            keyPressed = keyNormal.shifted(1.25f),
            keyAction = keyNormal.shifted(0.85f),
            keyText = keyText,
            keyTextHint = keyText.withAlpha(140),
            accent = pick("custom_color_accent", base.accent),
            barBg = pick("custom_color_bar", base.barBg),
            panelBg = pick("custom_color_panel", base.panelBg),
            popupBg = keyNormal.shifted(1.3f),
        )
    }

    private fun Int.shifted(factor: Float): Int {
        val r = (Color.red(this) * factor).coerceIn(0f, 255f).toInt()
        val g = (Color.green(this) * factor).coerceIn(0f, 255f).toInt()
        val b = (Color.blue(this) * factor).coerceIn(0f, 255f).toInt()
        return Color.argb(255, r, g, b)
    }

    private fun Int.withAlpha(alpha: Int): Int =
        Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    fun palette(context: Context): Palette = when (mode(context)) {
        "dark" -> dark
        "light" -> light
        "custom" -> customPalette(context)
        else -> if (isDark(context)) dark else light
    }

    /**
     * Fingerprint of every setting that is *baked into the input views*.
     * The IME compares this against the last applied value and rebuilds the
     * whole input view when it differs. Live-read settings (delays, haptics,
     * glide behaviour) are intentionally NOT part of this.
     */
    fun key(context: Context): String = buildString {
        append(mode(context))
        append(':').append(isDark(context))
        for (k in Prefs.customColorKeys()) append(':').append(Prefs.customColor(context, k) ?: 0)
        append(':').append(Prefs.keyHeightScale(context))
        append(':').append(Prefs.keyRadiusDp(context))
        append(':').append(Prefs.keyFontScale(context))
        append(':').append(Prefs.spacebarLabel(context))
        append(':').append(Prefs.numberRowAlways(context))
        append(':').append(Prefs.bgImagePath(context).ifEmpty { "-" })
        append(':').append(Prefs.bgImageDim(context))
    }
}
