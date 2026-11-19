package org.osmium.keyboard

/** Pure editor-compatibility rules for the composing-span fallback. */
object EditorCompat {
    private const val VARIATION_WEB_EDIT_TEXT = 0x00000060

    /** Apps whose composer is known to drop or ignore IME composing spans. */
    private val DIRECT_COMMIT_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    fun needsDirectCommit(packageName: String?, inputType: Int): Boolean {
        if (packageName in DIRECT_COMMIT_PACKAGES) return true
        return (inputType and 0x00000fff) == VARIATION_WEB_EDIT_TEXT
    }

    /** True when [beforeCursor] actually contains the composing text we set. */
    fun composingStuck(beforeCursor: String?, composed: String): Boolean =
        composed.isEmpty() || beforeCursor?.endsWith(composed) == true
}
