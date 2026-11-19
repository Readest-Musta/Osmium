package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralNudgeTest {

    @Test
    fun weightsMoveTowardChosenFeature() {
        val w = FloatArray(NeuralNudge.FEATURE_COUNT)
        val chosen = FloatArray(NeuralNudge.FEATURE_COUNT) { 1f }
        NeuralNudge.apply(w, chosen, null)
        assertTrue(w[0] > 0f)
    }

    @Test
    fun weightsStayBounded() {
        val w = FloatArray(NeuralNudge.FEATURE_COUNT) { 1f }
        val chosen = FloatArray(NeuralNudge.FEATURE_COUNT) { 10f }
        repeat(100) { NeuralNudge.apply(w, chosen, null) }
        assertTrue(w.all { it <= 1f && it >= -1f })
    }

    @Test
    fun malformedVectorsAreNoOp() {
        val w = FloatArray(NeuralNudge.FEATURE_COUNT) { 0.2f }
        NeuralNudge.apply(w, FloatArray(2), null)
        assertEquals(0.2f, w[0], 0.001f)
    }
}
