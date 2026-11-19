package org.osmium.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Compact code-built keypad used for `TYPE_CLASS_NUMBER` / `TYPE_CLASS_PHONE`
 * fields. It commits literal characters straight to the editor — no riti
 * composition, no suggestion strip — which is what those fields expect.
 */
@SuppressLint("ViewConstructor")
class NumericPanel(context: Context) : LinearLayout(context) {

    /** A literal digit/symbol was tapped. */
    var onKey: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    /** Return to the full keyboard ("ABC"). */
    var onDone: (() -> Unit)? = null

    private val themedButtons = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.palette(context).panelBg)

        addView(keyRow(listOf("1", "2", "3")), rowParams())
        addView(keyRow(listOf("4", "5", "6")), rowParams())
        addView(keyRow(listOf("7", "8", "9")), rowParams())
        addView(keyRow(listOf("⌫", "0", "⏎")), rowParams())
        // A single compact row covers both numeric and phone fields; "*", "#"
        // and "+" are the characters phone dialers actually need.
        addView(keyRow(listOf("ABC", "-", ".", ",", "+", "*", "#"), compact = true), rowParams())
    }

    private fun rowParams() = LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    )

    private fun keyRow(items: List<String>, compact: Boolean = false): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            for (item in items) {
                val btn = TextView(context).apply {
                    text = item
                    gravity = Gravity.CENTER
                    textSize = if (compact) 13f else 18f
                    typeface = Typeface.DEFAULT_BOLD
                    isClickable = true
                    isFocusable = true
                    val vpad = if (compact) dp(6) else dp(10)
                    setPadding(dp(2), vpad, dp(2), vpad)
                    setOnClickListener {
                        when (item) {
                            "⌫" -> onBackspace?.invoke()
                            "⏎" -> onEnter?.invoke()
                            "ABC" -> onDone?.invoke()
                            else -> onKey?.invoke(item)
                        }
                    }
                    themedButtons += this
                    style(this)
                }
                addView(btn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                })
            }
        }

    private fun style(btn: TextView) {
        val pal = Theme.palette(context)
        btn.setTextColor(pal.keyText)
        btn.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), round(pal.keyPressed))
            addState(android.util.StateSet.NOTHING, round(pal.keyNormal))
        }
    }

    private fun round(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(color)
    }

    /** Re-styles the whole panel for the current theme. */
    fun refreshTheme() {
        setBackgroundColor(Theme.palette(context).panelBg)
        for (btn in themedButtons) style(btn)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
