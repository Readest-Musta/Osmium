package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNavTest {
    @Test
    fun previousWordStartCountsWholeWord() {
        assertEquals(6, TextNav.previousWordStart("ami bangla"))
    }

    @Test
    fun previousWordStartSkipsTrailingSpaces() {
        assertEquals(6, TextNav.previousWordStart("ami bangla "))
    }

    @Test
    fun previousWordStartDoesNotSplitSurrogatePairs() {
        assertEquals(2, TextNav.previousWordStart("hi 😀"))
    }

    @Test
    fun previousWordStartKeepsBanglaWordWhole() {
        assertEquals("বাংলা".length, TextNav.previousWordStart("আমি বাংলা"))
    }

    @Test
    fun lineStartLengthUsesLastNewline() {
        assertEquals(3, TextNav.lineStartLength("abc\ndef"))
        assertEquals(3, TextNav.lineStartLength("abc"))
    }

    @Test
    fun wordRangeAroundAtBoundary() {
        assertEquals(4..11, TextNav.wordRangeAround("ami banglay", 5))
        assertEquals(4..4, TextNav.wordRangeAround("ami  bang", 4))
    }

    @Test
    fun classifyWordVsLineVsNone() {
        assertEquals(SwipeResolver.Action.DELETE_WORD, SwipeResolver.classify(-70f, 10f, 60f))
        assertEquals(SwipeResolver.Action.DELETE_LINE, SwipeResolver.classify(10f, -70f, 60f))
        assertEquals(SwipeResolver.Action.NONE, SwipeResolver.classify(-30f, 0f, 60f))
    }

    @Test
    fun stepsForCountsWholeSteps() {
        assertEquals(2, SwipeResolver.stepsFor(-130f, 60f))
        assertEquals(0, SwipeResolver.stepsFor(59f, 60f))
    }
}
