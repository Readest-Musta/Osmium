package org.osmium.keyboard

import kotlin.math.pow

/**
 * Android-free colour maths shared by the theme and the keyboard views.
 *
 * Kept independent of `android.graphics.Color` so contrast rules can be unit-tested
 * on the JVM. Colours are ARGB ints in the usual `0xAARRGGBB` form.
 */
object ColorMath {

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    private fun Int.red(): Int = (this shr 16) and 0xFF
    private fun Int.green(): Int = (this shr 8) and 0xFF
    private fun Int.blue(): Int = this and 0xFF

    /** Relative luminance (WCAG) in 0..1. Alpha is ignored. */
    fun luminance(argb: Int): Float {
        fun channel(value: Int): Float {
            val c = value / 255f
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel(argb.red()) +
            0.7152f * channel(argb.green()) +
            0.0722f * channel(argb.blue())
    }

    fun isDark(argb: Int): Boolean = luminance(argb) < 0.5f

    /** Linear blend of [a] toward [b] by [fraction] (0..1), preserving alpha of [a]. */
    fun blend(a: Int, b: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        fun mix(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (a and 0xFF000000.toInt()) or
            (mix(a.red(), b.red()) shl 16) or
            (mix(a.green(), b.green()) shl 8) or
            mix(a.blue(), b.blue())
    }

    /** WCAG contrast ratio between two opaque colours, in 1..21. */
    fun contrastRatio(a: Int, b: Int): Float {
        val la = luminance(a)
        val lb = luminance(b)
        val (hi, lo) = if (la >= lb) la to lb else lb to la
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /**
     * Reads best-legible text colour (black or white) for [background].
     * Replaces hardcoded "always use the dark-theme text colour" assumptions,
     * which produce unreadable chips in light and custom themes.
     */
    fun onColor(background: Int): Int = if (
        contrastRatio(background, BLACK) >= contrastRatio(background, WHITE)
    ) BLACK else WHITE
}
