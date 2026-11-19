package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlidePathTest {

    @Test
    fun resamplesLineEvenly() {
        val pts = listOf(GlidePoint(0f, 0f), GlidePoint(100f, 0f))
        val out = GlidePath.resample(pts, 5)
        assertEquals(5, out.size)
        assertEquals(0f, out.first().x, 0.01f)
        assertEquals(100f, out.last().x, 0.01f)
        assertEquals(50f, out[2].x, 0.01f)
    }

    @Test
    fun emptyAndSinglePointTracesAreSafe() {
        assertTrue(GlidePath.resample(emptyList(), 8).isEmpty())
        assertEquals(3, GlidePath.resample(listOf(GlidePoint(1f, 1f)), 3).size)
    }

    @Test
    fun nearestKeyPicksCloserCenter() {
        val keys = listOf(
            GlideKey("a", 10f, 10f, 10f, 10f),
            GlideKey("b", 100f, 10f, 10f, 10f),
        )
        assertEquals("a", GlidePath.nearestKey(GlidePoint(12f, 11f), keys)?.code)
    }

    @Test
    fun crossingSequenceDedupsAndHonoursMinDistance() {
        val keys = listOf(
            GlideKey("a", 10f, 10f, 8f, 8f),
            GlideKey("b", 40f, 10f, 8f, 8f),
        )
        val pts = listOf(
            GlidePoint(10f, 10f), GlidePoint(11f, 10f),
            GlidePoint(40f, 10f), GlidePoint(41f, 10f),
        )
        val seq = GlidePath.crossingSequence(pts, keys, 5f)
        assertEquals(listOf("a", "b"), seq)
    }
}
