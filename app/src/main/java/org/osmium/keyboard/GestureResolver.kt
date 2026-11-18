package org.osmium.keyboard

import kotlin.math.atan2
import kotlin.math.abs

/**
 * Pure (Android-free) decisions for gesture best-outcomes. Kept separate from
 * [KeyboardView] so the edge/glide/safety rules can be verified by local JVM
 * tests — there is no device in the build harness.
 */
object GestureResolver {

    const val NONE = 0
    const val LEFT = -1
    const val RIGHT = 1

    /**
     * Which screen edge the finger is dwelling at. [rawX] is a screen-absolute
     * coordinate; [screenWidthPx] and [edgePx] are in the same pixel unit.
     */
    fun edgeDirection(rawX: Float, screenWidthPx: Int, edgePx: Float): Int {
        if (edgePx <= 0f || screenWidthPx <= 0) return NONE
        if (rawX <= edgePx) return LEFT
        if (rawX >= screenWidthPx - edgePx) return RIGHT
        return NONE
    }

    /**
     * Travel left after subtracting the activation distance from a first glide
     * delta. The initial thumb movement that crossed the activation threshold
     * must not be counted as cursor travel.
     */
    fun travelAfterActivation(delta: Float, activation: Float): Float {
        if (delta == 0f || activation <= 0f) return delta
        val sign = if (delta > 0f) 1f else -1f
        val remaining = delta - sign * activation
        // Never flip direction: a delta smaller than the activation yields 0.
        return if ((remaining > 0f) == (delta > 0f)) remaining else 0f
    }

    /**
     * Hysteresis gate for line-delete. A downward swipe clears [thresholdDp]
     * only when the vertical axis is dominant by [dominance]x AND the swipe
     * angle is at least ~56° from horizontal. This stops a mostly-leftward
     * word-delete whose finger drifts down from deleting the whole line.
     * [dy] follows the Android convention: positive is down the screen.
     */
    fun isLineDelete(
        dx: Float,
        dy: Float,
        thresholdDp: Float,
        dominance: Float = 1.5f,
    ): Boolean {
        if (dy <= 0f || dy < thresholdDp) return false
        val adx = abs(dx)
        if (dy < adx * dominance) return false
        val angle = Math.toDegrees(atan2(dy.toDouble(), adx.toDouble()))
        return angle >= 56.0
    }
}
