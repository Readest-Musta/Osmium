package org.osmium.keyboard

/**
 * A single choice in a long-press popup.
 *
 * [code] is what gets sent to the IME when the chip is chosen. [direct] is true for
 * characters riti cannot map: [RitiEngine.suggest] drops anything whose keycode is 0,
 * so those must be committed as literal text through the commit-first insertion funnel
 * instead of being fed to the engine.
 */
data class SymbolEntry(val label: String, val code: String, val direct: Boolean)

/**
 * Curated, everyday Unicode symbols for the `.` and `,` long-press popups.
 *
 * The full set lives in [EmojiData.SYMBOL_GROUPS]; this is the short, thumb-reachable
 * shortlist of characters people actually type while composing text. Every non-ASCII
 * entry is flagged [SymbolEntry.direct] so it bypasses riti's ASCII-only key mapping.
 */
object SymbolPalette {

    /** Sentinel chip that opens the full Symbols picker. Never sent to the engine. */
    const val OPEN_SYMBOLS_CODE = "__symbols__"

    /** Sentence punctuation, typographic quotes, brackets and Bengali দাঁড়ি. */
    val PERIOD: List<SymbolEntry> = listOf(
        "!", "?", ";", ":", "…", "—", "–",
        "'", "\"", "‘", "’", "“", "”",
        "(", ")", "[", "]", "{", "}", "।",
        OPEN_SYMBOLS_CODE,
    ).map { symbolEntry(it) }

    /** Currency (including ৳), maths, arrows and common writing marks. */
    val COMMA: List<SymbolEntry> = listOf(
        "৳", "$", "€", "£", "¥", "₹",
        "×", "÷", "±", "≠", "≤", "≥", "≈", "∞",
        "→", "←", "↑", "↓",
        "✓", "✗", "•", "°", "@",
        OPEN_SYMBOLS_CODE,
    ).map { symbolEntry(it) }

    /** Splits [entries] into rows of at most [columns] without dropping any entry. */
    fun <T> rows(entries: List<T>, columns: Int): List<List<T>> {
        require(columns > 0) { "columns must be positive" }
        return entries.chunked(columns)
    }

    private fun symbolEntry(code: String): SymbolEntry {
        val isSentinel = code == OPEN_SYMBOLS_CODE
        return SymbolEntry(
            label = if (isSentinel) "Σ" else code,
            code = code,
            direct = !isSentinel && code.any { it.code >= 128 },
        )
    }
}
