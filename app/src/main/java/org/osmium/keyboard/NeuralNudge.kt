package org.osmium.keyboard

/** Bounded online update of the tiny re-ranker's 7-feature weight vector. */
object NeuralNudge {
    const val FEATURE_COUNT = 7
    private const val LEARNING_RATE = 0.05f

    /**
     * Nudges weights toward the chosen feature vector and away from the runner-up.
     * Weights stay within [-1, 1]; a no-op when either vector is malformed.
     */
    fun apply(weights: FloatArray, chosen: FloatArray, runnerUp: FloatArray?) {
        if (chosen.size != FEATURE_COUNT) return
        for (i in 0 until FEATURE_COUNT) {
            val signal = when {
                runnerUp != null && runnerUp.size == FEATURE_COUNT -> chosen[i] - runnerUp[i]
                else -> chosen[i]
            }
            weights[i] = (weights[i] + LEARNING_RATE * signal).coerceIn(-1f, 1f)
        }
    }
}
