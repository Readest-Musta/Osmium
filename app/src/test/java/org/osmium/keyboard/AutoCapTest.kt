package org.osmium.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCapTest {

    @Test
    fun startsOfFieldAndSentencesAreCapitalised() {
        assertTrue(AutoCap.shouldShift(null))
        for (c in listOf('.', '!', '?', '।', '\n', '\r')) {
            assertTrue(AutoCap.shouldShift(c))
        }
    }

    @Test
    fun midWordLettersAreNotCapitalised() {
        for (c in listOf('a', 'B', '7', ' ', '৳')) {
            assertFalse(AutoCap.shouldShift(c))
        }
    }
}
