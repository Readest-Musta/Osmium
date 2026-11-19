package org.osmium.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView

/** Actions exposed by the navigation / editing panel. */
enum class NavAction {
    HOME, LEFT, UP, DOWN, RIGHT, END,
    DOC_START, DOC_END,
    COPY, CUT, PASTE, SELECT_WORD, SELECT_ALL,
    UNDO, REDO
}

/**
 * Code-built navigation & editing panel hosted in the IME's shared panel slot.
 *
 * Three rows:
 *   ⇤ ◀ ▲ ▼ ▶ ⇥                  caret navigation (hold arrows to repeat)
 *   ⏮ Start        End ⏭          document start / end
 *   Copy  Cut  Paste  Select word  Select all
 */
@SuppressLint("ViewConstructor")
class NavigationPanel(context: Context) : LinearLayout(context) {

    var onAction: ((NavAction) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val longPressMs = Prefs.longPressMs(context).toLong()
    private val repeatMs = Prefs.backspaceRepeatMs(context).toLong().coerceAtLeast(16L)
    private val themedButtons = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.palette(context).panelBg)

        addView(TextView(context).apply {
            text = "← Keyboard"
            textSize = 13f
            setTextColor(Theme.palette(context).accent)
            setPadding(dp(10), dp(4), dp(10), dp(2))
            isClickable = true
            setOnClickListener { onBack?.invoke() }
        }, rowParams())

        addView(row().apply {
            addView(arrow("⇤", NavAction.HOME), cell())
            addView(arrow("◀", NavAction.LEFT), cell())
            addView(arrow("▲", NavAction.UP), cell())
            addView(arrow("▼", NavAction.DOWN), cell())
            addView(arrow("▶", NavAction.RIGHT), cell())
            addView(arrow("⇥", NavAction.END), cell())
        }, rowParams())

        addView(row().apply {
            addView(button("⏮ Start", NavAction.DOC_START), cell())
            addView(button("End ⏭", NavAction.DOC_END), cell())
            addView(button("↶ Undo", NavAction.UNDO), cell())
            addView(button("↷ Redo", NavAction.REDO), cell())
        }, rowParams())

        addView(row().apply {
            addView(button("Copy", NavAction.COPY), cell())
            addView(button("Cut", NavAction.CUT), cell())
            addView(button("Paste", NavAction.PASTE), cell())
            addView(button("Select word", NavAction.SELECT_WORD), cell())
            addView(button("Select all", NavAction.SELECT_ALL), cell())
        }, rowParams())
    }

    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(3), dp(4), dp(3))
    }

    private fun rowParams() = LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    )

    private fun cell() = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(2), 0, dp(2), 0)
    }

    private fun button(label: String, action: NavAction): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isClickable = true
        isFocusable = true
        setPadding(dp(2), dp(10), dp(2), dp(10))
        setOnClickListener { onAction?.invoke(action) }
        themedButtons += this
        style(this)
    }

    /** An arrow button: tap dispatches once; holding repeats at the configured rate. */
    private fun arrow(label: String, action: NavAction): TextView {
        val btn = button(label, action)
        var repeated = false
        var repeat: Runnable? = null
        btn.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeated = false
                    val r = object : Runnable {
                        override fun run() {
                            repeated = true
                            onAction?.invoke(action)
                            handler.postDelayed(this, repeatMs)
                        }
                    }
                    repeat = r
                    handler.postDelayed(r, longPressMs)
                    false // still allow a plain tap via the click listener
                }
                MotionEvent.ACTION_UP -> {
                    repeat?.let { handler.removeCallbacks(it) }
                    repeat = null
                    false
                }
                MotionEvent.ACTION_CANCEL -> {
                    repeat?.let { handler.removeCallbacks(it) }
                    repeat = null
                    repeated = false
                    false
                }
                else -> false
            }
        }
        // Replaces the click listener installed by button(): consume the click
        // that follows a hold so a held arrow does not dispatch one extra step.
        btn.setOnClickListener {
            if (repeated) repeated = false else onAction?.invoke(action)
        }
        return btn
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

    /** Cancels any pending arrow hold-repeat callbacks (called on every close). */
    fun clearTimers() {
        handler.removeCallbacksAndMessages(null)
    }

    /** Dismisses the panel. */
    fun close() {
        clearTimers()
        visibility = GONE
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
