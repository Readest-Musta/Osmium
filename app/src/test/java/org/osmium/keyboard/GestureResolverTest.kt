package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure gesture best-outcomes decisions (no device needed). */
class GestureResolverTest {

    @Test
    fun edgeDirection_detects_left_right_and_middle() {
        val width = 1080
        val edge = 72f // ~24dp at 3x
        assertEquals(GestureResolver.LEFT, GestureResolver.edgeDirection(30f, width, edge))
        assertEquals(GestureResolver.LEFT, GestureResolver.edgeDirection(72f, width, edge))
        assertEquals(GestureResolver.NONE, GestureResolver.edgeDirection(73f, width, edge))
        assertEquals(GestureResolver.NONE, GestureResolver.edgeDirection(540f, width, edge))
        assertEquals(GestureResolver.RIGHT, GestureResolver.edgeDirection(1007f, width, edge))
        assertEquals(GestureResolver.RIGHT, GestureResolver.edgeDirection(1008f, width, edge))
        assertEquals(GestureResolver.NONE, GestureResolver.edgeDirection(1006f, width, edge))
    }

    @Test
    fun edgeDirection_disabled_when_edge_nonpositive_or_width_invalid() {
        assertEquals(GestureResolver.NONE, GestureResolver.edgeDirection(0f, 1080, 0f))
        assertEquals(GestureResolver.NONE, GestureResolver.edgeDirection(0f, 0, 72f))
    }

    @Test
    fun travelAfterActivation_subtracts_threshold_without_flipping_direction() {
        // 18dp activation: first 18dp of travel is the activation itself.
        assertEquals(0f, GestureResolver.travelAfterActivation(18f, 18f), 0.001f)
        assertEquals(12f, GestureResolver.travelAfterActivation(30f, 18f), 0.001f)
        assertEquals(-12f, GestureResolver.travelAfterActivation(-30f, 18f), 0.001f)
        // A delta below the activation must clamp to zero, not reverse.
        assertEquals(0f, GestureResolver.travelAfterActivation(10f, 18f), 0.001f)
        assertEquals(0f, GestureResolver.travelAfterActivation(-10f, 18f), 0.001f)
    }

    @Test
    fun lineDelete_requires_downward_dominant_directional_swipe() {
        val t = 60f
        assertTrue(GestureResolver.isLineDelete(0f, 80f, t))          // straight down
        assertTrue(GestureResolver.isLineDelete(20f, 80f, t))         // slight right, still dominant
        assertFalse(GestureResolver.isLineDelete(80f, 80f, t))        // 45° is not enough
        assertFalse(GestureResolver.isLineDelete(-100f, 70f, t))      // mostly left -> word delete
        assertFalse(GestureResolver.isLineDelete(0f, -80f, t))        // upward
        assertFalse(GestureResolver.isLineDelete(0f, 50f, t))         // below threshold
    }
}
