package org.osmium.keyboard

import kotlin.math.abs

/** Pure helpers for navigating/editing text by word and by line. All offsets are UTF-16 indices. */
object TextNav {
    private fun isSeparator(codePoint: Int): Boolean = Character.isWhitespace(codePoint)

    /**
     * Number of UTF-16 chars to delete for the word immediately before the cursor (end of [before]).
     * Trailing whitespace is skipped and not counted; 0 when there is no word before the cursor.
     */
    fun previousWordStart(before: String): Int {
        var end = before.length
        while (end > 0) {
            val cp = before.codePointBefore(end)
            if (!isSeparator(cp)) break
            end -= Character.charCount(cp)
        }
        var start = end
        while (start > 0) {
            val cp = before.codePointBefore(start)
            if (isSeparator(cp)) break
            start -= Character.charCount(cp)
        }
        return end - start
    }

    /** Number of UTF-16 chars from the last newline (exclusive) to the cursor (end of [before]). */
    fun lineStartLength(before: String): Int = before.length - before.lastIndexOf('\n') - 1

    /** UTF-16 half-open range [start, end) of the word around [cursor]; empty at a separator. */
    fun wordRangeAround(text: String, cursor: Int): IntRange = rangeAround(text, cursor, ::isSeparator)

    /**
     * Bangla-aware variant: the danda (।) and double danda (॥) sentence marks
     * also end a word, so selecting a word in Bengali prose never swallows the
     * punctuation that follows it.
     */
    fun wordRangeAroundBangla(text: String, cursor: Int): IntRange =
        rangeAround(text, cursor) { cp ->
            isSeparator(cp) || cp == 0x0964 || cp == 0x0965
        }

    private fun rangeAround(
        text: String,
        cursor: Int,
        separator: (Int) -> Boolean,
    ): IntRange {
        val pos = cursor.coerceIn(0, text.length)
        var start = pos
        while (start > 0) {
            val cp = text.codePointBefore(start)
            if (separator(cp)) break
            start -= Character.charCount(cp)
        }
        var end = pos
        while (end < text.length) {
            val cp = text.codePointAt(end)
            if (separator(cp)) break
            end += Character.charCount(cp)
        }
        return start..end
    }
}

/** Resolves swipe gestures into text-editing actions. Distances are in density-independent pixels. */
object SwipeResolver {
    enum class Action { NONE, DELETE_WORD, DELETE_LINE }

    /** Leftward-dominant swipes delete a word, downward-dominant swipes delete a line. */
    fun classify(dx: Float, dy: Float, thresholdDp: Float): Action {
        val adx = abs(dx)
        val ady = abs(dy)
        if (adx < thresholdDp && ady < thresholdDp) return Action.NONE
        return when {
            adx >= ady && dx < 0f && adx >= thresholdDp -> Action.DELETE_WORD
            ady > adx && dy < 0f && ady >= thresholdDp -> Action.DELETE_LINE
            else -> Action.NONE
        }
    }

    /** Whole steps of travel, using the absolute distance. */
    fun stepsFor(distanceDp: Float, stepDp: Float): Int {
        if (stepDp <= 0f) return 0
        return (abs(distanceDp) / stepDp).toInt()
    }
}
