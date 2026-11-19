package org.osmium.keyboard

/** Pure auto-capitalize decision, independent of the editor's lagging state. */
object AutoCap {
    private val SENTENCE_BREAKS = setOf('.', '!', '?', '।', '\n', '\r')

    /** True when the next typed character should start a capital letter. */
    fun shouldShift(lastCommitted: Char?): Boolean =
        lastCommitted == null || lastCommitted in SENTENCE_BREAKS
}
