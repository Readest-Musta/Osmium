package org.osmium.keyboard

import android.content.Context
import android.graphics.Typeface
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

    private val chips = mutableListOf<TextView>()
    private lateinit var langButton: TextView
    private val incognitoIndicator: TextView

    init {
        orientation = HORIZONTAL
        setBackgroundColor(context.resources.getColor(R.color.bar_bg, null))
        gravity = Gravity.CENTER_VERTICAL

        incognitoIndicator = TextView(context).apply {
            text = "🕵"
            gravity = Gravity.CENTER
            textSize = 13f
            visibility = GONE
        }.also { addView(it, LayoutParams(dp(28), LayoutParams.MATCH_PARENT)) }

        langButton = controlButton("", weight = 1f).also {
            it.setOnClickListener { onLanguageToggle?.invoke() }
            addView(it, barParams(1f))
        }

        for (i in 0 until MAX_SUGGESTIONS) {
            val chip = TextView(context).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                textSize = 16f
                setTextColor(context.resources.getColor(R.color.key_text, null))
                setPadding(dp(4), 0, dp(4), 0)
                visibility = View.INVISIBLE
                setOnClickListener {
                    if (text.isNotEmpty()) onSuggestionTap?.invoke(chips.indexOf(this))
                }
            }
            chips.add(chip)
            addView(chip, barParams(1.6f))
        }

        val numberButton = controlButton("১২৩", 1f).also {
            it.setOnClickListener { onNumberRowToggle?.invoke() }
            addView(it, barParams(1f))
        }

        val settingsButton = controlButton("⚙", 1f).also {
            it.setOnClickListener { onSettingsTap?.invoke() }
            addView(it, barParams(1f))
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun barParams(weight: Float) = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)

    private fun controlButton(label: String, weight: Float): TextView =
        TextView(context).apply {
            gravity = Gravity.CENTER
            text = label
            maxLines = 1
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(context.resources.getColor(R.color.key_text_hint, null))
            isClickable = true
        }

    fun setModeLabel(mode: KeyboardMode) {
        langButton.text = when (mode) {
            KeyboardMode.ENGLISH -> "EN"
            KeyboardMode.AVRO -> "অভ্র"
            KeyboardMode.PROBHAT -> "প্রভাত"
        }
    }

    /** Shows the incognito badge next to the language button. */
    fun setIncognito(active: Boolean) {
        if (active && incognitoIndicator == null) {
            incognitoIndicator = TextView(context).apply {
                text = "🕵"
                gravity = Gravity.CENTER
                textSize = 13f
                addView(this, LayoutParams(dp(28), LayoutParams.MATCH_PARENT))
            }
        }
        incognitoIndicator?.visibility = if (active) VISIBLE else GONE
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
    }

    fun clear() {
        for (chip in chips) {
            chip.text = ""
            chip.visibility = View.INVISIBLE
        }
    }

    companion object {
        private const val MAX_SUGGESTIONS = 5
    }
}
