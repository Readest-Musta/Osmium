package org.osmium.keyboard

/** Pure geometry rule for the centre gap in a split keyboard row. */
object SplitLayout {
    /** Index before which the gap is inserted, or -1 when a row is too short to split. */
    fun splitIndex(keyCount: Int): Int = if (keyCount >= 7) keyCount / 2 else -1
}
