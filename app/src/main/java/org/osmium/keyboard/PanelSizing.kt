package org.osmium.keyboard

/** Pure rule for panel height as a percentage of the available IME height. */
object PanelSizing {
    const val DEFAULT = 60
    private const val MIN = 45
    private const val MAX = 75

    fun heightPercent(pref: Int): Int = pref.coerceIn(MIN, MAX)
}
