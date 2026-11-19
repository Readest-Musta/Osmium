package org.osmium.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Clipboard history panel, shown in place of the emoji panel. Fully local —
 * entries live in SharedPreferences and never leave the device.
 *
 *   tap a row    → paste it into the field
 *   📌 tap       → pin / unpin (pinned entries survive clearing)
 *   ✕ tap        → remove entry
 */
class ClipboardPanel(context: Context) : LinearLayout(context) {

    var onPaste: ((String) -> Unit)? = null

    private val listHost: LinearLayout
    private val clearButton: TextView
    private val noteView: TextView
    /** Incognito: pasting is allowed, but nothing is recorded or modified. */
    private var readOnly = false

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Theme.palette(context).panelBg)

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            addView(TextView(context).apply {
                text = "📋 Clipboard"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Theme.palette(context).keyText)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            clearButton = TextView(context).apply {
                text = "Clear"
                textSize = 13f
                setTextColor(Theme.palette(context).accent)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setOnClickListener {
                    Prefs.clearClipboardHistory(context)
                    refresh()
                }
            }
            addView(clearButton)
        }
        addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        noteView = TextView(context).apply {
            textSize = 12f
            setTextColor(Theme.palette(context).keyTextHint)
            setPadding(dp(10), 0, dp(10), dp(6))
            visibility = View.GONE
        }
        addView(noteView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        listHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(ScrollView(context).apply {
            addView(listHost, FrameParams())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        refresh()
    }

    @SuppressLint("ViewConstructor")
    private inner class Row(val item: Prefs.ClipboardItem) : LinearLayout(context) {

        init {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            addView(TextView(context).apply {
                text = item.text.replace("\n", "␤").take(60)
                textSize = 14f
                maxLines = 2
                setTextColor(Theme.palette(context).keyText)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (!readOnly) {
                addView(TextView(context).apply {
                    text = if (item.pinned) "📌" else "○"
                    textSize = 14f
                    setPadding(dp(8), 0, dp(8), 0)
                    contentDescription = "Pin or unpin"
                    setOnClickListener {
                        Prefs.toggleClipboardPin(context, item.id)
                        refresh()
                    }
                })

                addView(TextView(context).apply {
                    text = "✕"
                    textSize = 14f
                    setTextColor(Theme.palette(context).keyTextHint)
                    setPadding(dp(8), 0, dp(8), 0)
                    contentDescription = "Remove entry"
                    setOnClickListener {
                        Prefs.removeClipboardItem(context, item.id)
                        refresh()
                    }
                })
            }

            isClickable = true
            setOnClickListener { onPaste?.invoke(item.text) }
        }
    }

    /** Switches between editable history and incognito's paste-only view. */
    fun setReadOnly(value: Boolean) {
        if (readOnly == value) return
        readOnly = value
        refresh()
    }

    /** Rebuilds the list from prefs. */
    fun refresh() {
        clearButton.visibility = if (readOnly) View.GONE else View.VISIBLE
        noteView.visibility = if (readOnly) View.VISIBLE else View.GONE
        if (readOnly) {
            noteView.text = "Incognito: paste-only. Nothing you copy now is saved here."
        }
        listHost.removeAllViews()
        val items = Prefs.clipboardItems(context)
        if (items.isEmpty()) {
            listHost.addView(TextView(context).apply {
                text = if (readOnly) {
                    "Nothing to paste yet.\nCopied text is not recorded while incognito is on."
                } else {
                    "Nothing copied yet.\nCopy text anywhere — it will show up here (stored only on this device)."
                }
                textSize = 13f
                setTextColor(Theme.palette(context).keyTextHint)
                setPadding(dp(12), dp(14), dp(12), dp(8))
            })
            return
        }
        // Pinned first, then newest-first (by the stable timestamp).
        val ordered = items.sortedWith(
            compareByDescending<Prefs.ClipboardItem> { it.pinned }.thenByDescending { it.at }
        )
        for (item in ordered) listHost.addView(Row(item), RowParams())
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun FrameParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun RowParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
}
