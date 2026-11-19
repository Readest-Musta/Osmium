package org.osmium.keyboard

/** Pure check used by the settings screen's "is Osmium enabled?" badge. */
object SetupState {
    fun isEnabled(enabledIds: List<String>, ourId: String): Boolean =
        enabledIds.any { it.equals(ourId, ignoreCase = true) }
}
