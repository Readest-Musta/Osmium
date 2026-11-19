package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `luminance bounds`() {
        assertEquals(0f, ColorMath.luminance(black), 0.001f)
        assertEquals(1f, ColorMath.luminance(white), 0.001f)
        assertTrue(ColorMath.isDark(black))
        assertTrue(!ColorMath.isDark(white))
    }

    @Test
    fun `blend interpolates and preserves alpha`() {
        val mid = ColorMath.blend(black, white, 0.5f)
        assertEquals(0xFF, (mid ushr 24) and 0xFF)
        val channel = mid and 0xFF
        assertTrue(channel in 126..128)
    }

    @Test
    fun `onColor picks black on light and white on dark`() {
        assertEquals(black, ColorMath.onColor(white))
        assertEquals(white, ColorMath.onColor(black))
    }

    @Test
    fun `onColor is readable on a mid accent`() {
        val accent = 0xFF4F8CFF.toInt()
        val chosen = ColorMath.onColor(accent)
        assertTrue(ColorMath.contrastRatio(accent, chosen) >= 3f)
    }
}
