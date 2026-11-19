package org.osmium.keyboard

/**
 * Pure decisions for editor actions that some apps (WebView, Chrome, custom
 * editors) do not implement through `InputConnection.performContextMenuAction`.
 */
object EditorActions {

    /**
     * True when the context-menu action failed and we have selected text we can
     * copy ourselves. Never writes to the clipboard when there is no selection.
     */
    fun needsDirectCopy(contextMenuSucceeded: Boolean, selectedText: String?): Boolean =
        !contextMenuSucceeded && !selectedText.isNullOrEmpty()

    /** True when a cut must be completed by copying and deleting the selection. */
    fun needsDirectCut(contextMenuSucceeded: Boolean, selectedText: String?): Boolean =
        needsDirectCopy(contextMenuSucceeded, selectedText)

    /** Clamps a selection range to the field length. */
    fun safeSelection(anchor: Int, length: Int): Pair<Int, Int> {
        val max = length.coerceAtLeast(0)
        return anchor.coerceIn(0, max) to max
    }
}
