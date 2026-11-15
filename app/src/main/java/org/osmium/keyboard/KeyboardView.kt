package org.osmium.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Typing modes Osmium supports. */
enum class KeyboardMode { ENGLISH, AVRO, PROBHAT }

/**
 * The main QWERTY-arranged keyboard view.
 *
 * Key *codes* are the physical-layout characters ("q", "w", ...). What happens with
 * a code is decided by the IME service: English commits it directly, Avro/Probhat
 * feed it to the riti engine.
 */
class KeyboardView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onKey(code: String, shifted: Boolean)
        fun onShiftTap()
        fun onSpace()
        fun onBackspace()
        fun onEnter()
        fun onModeCycle()
        fun onEmojiToggle()
        fun onNumberRowToggle()
    }

    var listener: Listener? = null

    var mode: KeyboardMode = KeyboardMode.AVRO
        set(value) {
            field = value
            shift = false
            capsLock = false
            relabelAll()
        }

    private var shift = false
    private var capsLock = false
    private var numberRowVisible = false

    private val keys = mutableListOf<OsmiumKey>()
    private val numberRow: LinearLayout
    private var numberToggleKey: OsmiumKey? = null

    /** Probhat labels: physical char -> (unshifted label, shifted label). */
    private val probhatLabels: Map<String, Pair<String, String>> by lazy {
        val raw = context.assets.open("layouts/probhat-labels.json").bufferedReader().readText()
        val root = JSONObject(raw)
        val map = mutableMapOf<String, Pair<String, String>>()
        val letters = root.getJSONObject("letters")
        for (ch in letters.keys()) {
            val e = letters.getJSONObject(ch)
            map[ch] = e.optString("n", ch) to e.optString("s", ch.uppercase())
        }
        val digits = root.getJSONObject("digits")
        for (ch in digits.keys()) map[ch] = digits.optString(ch, ch) to ch
        for (ch in listOf(",", ".")) map[ch] = ch to ch
        map
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(context.resources.getColor(R.color.keyboard_bg, null))

        numberRow = buildRow(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            digitRow = true
        )
        numberRow.visibility = GONE
        addView(numberRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(buildRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")))
        addView(buildRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")))
        addView(buildActionMiddleRow())
        addView(buildBottomRow())
        relabelAll()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildRow(codes: List<String>, digitRow: Boolean = false): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (code in codes) {
            val key = OsmiumKey(context)
            key.tag = code
            key.setCharAction(code, digitRow)
            row.addView(
                key,
                LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(4), dp(2), dp(2)) }
            )
        }
        return row
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun OsmiumKey.setCharAction(code: String, digitRow: Boolean) {
        keys.add(this)
        val base = code
        setOnClickListener {
            if (digitRow) {
                // Number row commits digits directly (Probhat goes through riti).
                listener?.onKey(base, false)
            } else {
                listener?.onKey(base, shift || capsLock)
                if (shift && !capsLock) {
                    shift = false
                    relabelAll()
                }
            }
        }
        if (!digitRow) {
            // Long-press top-row letters types their digit (G-board style).
            setOnLongClickListener {
                val idx = TOP_ROW.indexOf(base)
                if (idx >= 0) {
                    listener?.onKey(TOP_DIGITS[idx], false)
                    true
                } else false
            }
        }
    }

    private fun buildActionMiddleRow(): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }

        val shiftKey = OsmiumKey(context).apply {
            weight = 1.4f
            isAction = true
            setOnClickListener {
                if (shift && !capsLock) {
                    capsLock = true // double tap -> caps lock
                } else if (capsLock) {
                    capsLock = false
                    shift = false
                } else {
                    shift = true
                }
                relabelAll()
                listener?.onShiftTap()
            }
            tag = "shift"
        }
        keys.add(shiftKey)
        row.addView(shiftKey, rowParams(1.4f))

        for (code in listOf("z", "x", "c", "v", "b", "n", "m")) {
            val key = OsmiumKey(context)
            key.tag = code
            key.setCharAction(code, digitRow = false)
            row.addView(key, rowParams(1f))
        }

        val backspace = OsmiumKey(context).apply {
            weight = 1.4f
            isAction = true
            tag = "backspace"
            text = "⌫"
            setOnClickListener { listener?.onBackspace() }
            // Hold to repeat-delete.
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var repeat: Runnable? = null
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val r = Runnable {
                            listener?.onBackspace()
                            handler.postDelayed(it, 60)
                        }
                        repeat = r
                        handler.postDelayed(r, 350)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        repeat?.let { handler.removeCallbacks(it) }
                        repeat = null
                    }
                }
                false
            }
        }
        keys.add(backspace)
        row.addView(backspace, rowParams(1.4f))
        return row
    }

    private fun buildBottomRow(): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }

        val emojiKey = OsmiumKey(context).apply {
            weight = 1f
            isAction = true
            tag = "emoji"
            text = "☺"
            setOnClickListener { listener?.onEmojiToggle() }
        }
        keys.add(emojiKey)
        row.addView(emojiKey, rowParams(1f))

        val modeKey = OsmiumKey(context).apply {
            weight = 1f
            isAction = true
            tag = "mode"
            setOnClickListener { listener?.onModeCycle() }
        }
        keys.add(modeKey)
        row.addView(modeKey, rowParams(1f))

        val comma = OsmiumKey(context).apply {
            weight = 1f
            tag = ","
            text = ","
            setCharAction(",", digitRow = false)
        }
        row.addView(comma, rowParams(1f))

        val space = OsmiumKey(context).apply {
            weight = 4f
            isAction = true
            tag = "space"
            text = "␣"
            setOnClickListener { listener?.onSpace() }
        }
        keys.add(space)
        row.addView(space, rowParams(4f))

        val period = OsmiumKey(context).apply {
            weight = 1f
            tag = "."
            text = "."
            setCharAction(".", digitRow = false)
        }
        row.addView(period, rowParams(1f))

        val enter = OsmiumKey(context).apply {
            weight = 1.4f
            isAction = true
            tag = "enter"
            text = "⏎"
            setOnClickListener { listener?.onEnter() }
        }
        keys.add(enter)
        row.addView(enter, rowParams(1.4f))

        numberToggleKey = null
        return row
    }

    private fun rowParams(weight: Float) = LayoutParams(0, dp(46), weight).apply {
        setMargins(dp(2), dp(4), dp(2), dp(2))
    }

    /** Toggles the number row. Returns the new visibility state. */
    fun toggleNumberRow(): Boolean {
        numberRowVisible = !numberRowVisible
        numberRow.visibility = if (numberRowVisible) VISIBLE else GONE
        return numberRowVisible
    }

    fun relabelAll() {
        val shifted = shift || capsLock
        for (key in keys) {
            val tag = key.tag as? String ?: continue
            when (tag) {
                "shift" -> {
                    key.text = if (capsLock) "⇪" else if (shift) "⇧" else "⇧"
                    key.setTextColor(
                        if (shifted) context.resources.getColor(R.color.accent, null)
                        else context.resources.getColor(R.color.key_text, null)
                    )
                }
                "mode" -> key.text = when (mode) {
                    KeyboardMode.ENGLISH -> "EN"
                    KeyboardMode.AVRO -> "অ"
                    KeyboardMode.PROBHAT -> "প্"
                }
                "space" -> key.text = "␣"
                else -> key.text = labelFor(tag, shifted)
            }
        }
    }

    private fun labelFor(code: String, shifted: Boolean): String = when (mode) {
        KeyboardMode.PROBHAT -> {
            val p = probhatLabels[code] ?: (code to code.uppercase())
            if (shifted) p.second else p.first
        }
        else -> if (shifted) code.uppercase() else code
    }

    private val TOP_ROW = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val TOP_DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    /** A single key. */
    @SuppressLint("ViewConstructor")
    inner class OsmiumKey(context: Context) : TextView(context) {
        var isAction: Boolean = false
            set(value) {
                field = value
                background = if (value) actionBg() else charBg()
            }

        init {
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(context.resources.getColor(R.color.key_text, null))
            background = charBg()
            isClickable = true
            isFocusable = true
        }

        private fun roundBg(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(color)
        }

        private fun charBg() = roundBg(context.resources.getColor(R.color.key_normal, null))
        private fun actionBg() = roundBg(context.resources.getColor(R.color.key_action, null))
    }
}
