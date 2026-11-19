package org.osmium.keyboard

/**
 * Pure privacy decision for clipboard capture. Kept Android-free so the rule that
 * incognito never records copied text can be pinned by a JVM test.
 */
object ClipboardAccess {
    fun canCapture(incognito: Boolean): Boolean = !incognito
}
