package org.osmium.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import uniffi.osmium.OsSuggestion

/**
 * The bar above the keyboard: suggestions on the left, language + number-row
 * + settings controls on the right.
 */
class SuggestionBar(context: Context) : LinearLayout(context) {

    var onSuggestionTap: ((Int) -> Unit)? = null
    var onLanguageToggle: (() -> Unit)? = null
    var onNumberRowToggle: (() -> Unit)? = null
    var onSettingsTap: (() -> Unit)? = null
    var onClipboardToggle: (() -> Unit)? = null
    var onSymbolsToggle: (() -> Unit)? = null
    var onNavToggle: (() -> Unit)? = null

    private val chips = mutableListOf<TextView>()
    private lateinit var langButton: TextView
    private lateinit var previewChip: TextView
    private var selectedIndex = 0
    private val incognitoIndicator: TextView
    private val navButton: TextView
    private val numberButton: TextView
    private val clipboardChip: TextView
    private val symbolsButton: TextView
    private val settingsButton: TextView

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Theme.palette(context).barBg)
        gravity = Gravity.CENTER_VERTICAL

        incognitoIndicator = TextView(context).apply {
            text = "🕵"
            gravity = Gravity.CENTER
            textSize = 13f
            visibility = GONE
        }.also { addView(it, LayoutParams(dp(28), LayoutParams.MATCH_PARENT)) }

        // Romanized buffer preview: the text already composed before the word
        // being typed. Small, single-line, ellipsized and hidden when empty.
        previewChip = TextView(context).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textSize = 12f
            setTextColor(Theme.palette(context).keyTextHint)
            visibility = GONE
        }.also { addView(it, LayoutParams(dp(72), LayoutParams.MATCH_PARENT)) }

        langButton = controlButton("", "Switch typing mode").also {
            it.setOnClickListener { onLanguageToggle?.invoke() }
            addView(it, barParams(1f))
        }

        for (i in 0 until MAX_SUGGESTIONS) {
            val chip = TextView(context).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                textSize = 16f
                setTextColor(Theme.palette(context).keyText)
                setPadding(dp(4), 0, dp(4), 0)
                visibility = View.INVISIBLE
                setOnClickListener {
                    if (text.isNotEmpty()) onSuggestionTap?.invoke(chips.indexOf(this))
                }
            }
            chips.add(chip)
            addView(chip, barParams(1.6f))
        }

        navButton = controlButton("⇱", "Navigation and editing").also {
            it.setOnClickListener { onNavToggle?.invoke() }
            addView(it, barParams(1f))
        }

        numberButton = controlButton("১২৩", "Toggle number row").also {
            it.setOnClickListener { onNumberRowToggle?.invoke() }
            addView(it, barParams(1f))
        }

        clipboardChip = controlButton("📋", "Clipboard history").also {
            it.setOnClickListener { onClipboardToggle?.invoke() }
            addView(it, barParams(1f))
        }

        symbolsButton = controlButton("#", "Symbols").also {
            it.setOnClickListener { onSymbolsToggle?.invoke() }
            addView(it, barParams(1f))
        }

        settingsButton = controlButton("⚙", "Settings").also {
            it.setOnClickListener { onSettingsTap?.invoke() }
            addView(it, barParams(1f))
        }
        refreshControls()
    }

    /** Applies the toolbar-visibility preferences. */
    fun refreshControls() {
        navButton.visibility = if (Prefs.showNavButton(context)) VISIBLE else GONE
        numberButton.visibility = if (Prefs.showNumberToggle(context)) VISIBLE else GONE
        // The clipboard stays reachable even in incognito: the panel switches to
        // paste-only instead of silently disappearing.
        clipboardChip.visibility = if (Prefs.showClipboardChip(context)) VISIBLE else GONE
        symbolsButton.visibility = if (Prefs.showSymbolsButton(context)) VISIBLE else GONE
        settingsButton.visibility = if (Prefs.showSettingsButton(context)) VISIBLE else GONE
    }

    /** Re-styles for the current theme. */
    fun applyTheme() {
        val pal = Theme.palette(context)
        setBackgroundColor(pal.barBg)
        previewChip.setTextColor(pal.keyTextHint)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is TextView && child !== incognitoIndicator && child !in chips) {
                child.setTextColor(pal.keyTextHint)
            }
        }
        styleChips()
    }

    /** Highlights the active candidate and restores the others to the theme colour. */
    private fun styleChips() {
        val pal = Theme.palette(context)
        for (i in chips.indices) {
            val chip = chips[i]
            if (i == selectedIndex && chip.visibility == VISIBLE) {
                chip.setBackgroundColor(pal.accent)
                chip.setTextColor(Theme.dark.keyText)
            } else {
                chip.setBackgroundColor(Color.TRANSPARENT)
                chip.setTextColor(pal.keyText)
            }
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun barParams(weight: Float) = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)

    private fun controlButton(label: String, description: String): TextView =
        TextView(context).apply {
            gravity = Gravity.CENTER
            text = label
            maxLines = 1
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Theme.palette(context).keyTextHint)
            contentDescription = description
            isClickable = true
        }

    fun setModeLabel(mode: KeyboardMode, layoutName: String? = null) {
        langButton.text = when (mode) {
            KeyboardMode.ENGLISH -> "EN"
            KeyboardMode.AVRO -> "অভ্র"
            KeyboardMode.PROBHAT -> layoutName?.takeIf { it.isNotBlank() && it != "probhat" }
                ?.removeSuffix(".json") ?: "প্রভাত"
        }
    }

    /** Shows the incognito badge next to the language button. */
    fun setIncognito(active: Boolean) {
        incognitoIndicator.visibility = if (active) VISIBLE else GONE
    }

    /**
     * Marks the clipboard chip read-only (incognito): it remains visible so pasting
     * a deliberately copied snippet still works, but its label tells the user that
     * nothing new will be recorded.
     */
    fun setClipboardReadOnly(readOnly: Boolean) {
        clipboardChip.contentDescription = if (readOnly) {
            "Clipboard history (read-only in incognito)"
        } else {
            "Clipboard history"
        }
        clipboardChip.alpha = if (readOnly) 0.7f else 1f
    }

    fun update(suggestion: OsSuggestion) {
        val candidates = if (suggestion.isEmpty) emptyList() else suggestion.candidates
        for (i in chips.indices) {
            val chip = chips[i]
            val text = candidates.getOrNull(i) ?: ""
            if (text.isEmpty()) {
                chip.visibility = View.INVISIBLE
                chip.text = ""
            } else {
                chip.visibility = View.VISIBLE
                chip.text = text
            }
        }
        val auxiliary = suggestion.auxiliary.trim()
        previewChip.text = auxiliary
        previewChip.visibility = if (auxiliary.isEmpty()) GONE else VISIBLE
        selectedIndex = suggestion.selection.coerceAtLeast(0)
        styleChips()
    }

    fun clear() {
        for (chip in chips) {
            chip.text = ""
            chip.visibility = View.INVISIBLE
        }
        previewChip.text = ""
        previewChip.visibility = GONE
        selectedIndex = 0
        styleChips()
    }

    companion object {
        private const val MAX_SUGGESTIONS = 5
    }
}
