package org.osmium.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Typing modes Osmium supports. */
enum class KeyboardMode { ENGLISH, AVRO, PROBHAT }

/** One long-press popup choice. */
data class PopupEntry(val label: String, val code: String, val altgr: Boolean)

/**
 * The main QWERTY-arranged keyboard view.
 *
 * Key *codes* are the physical-layout characters ("q", "w", ...). What happens with
 * a code is decided by the IME service: English commits it directly, Avro/Probhat
 * feed it to the riti engine.
 *
 * Long-press on keys with alternates opens a G-board-style popup: hold, slide
 * onto a character, release to commit.
 */
class KeyboardView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onKey(code: String, shifted: Boolean, altgr: Boolean = false)
        fun onShiftTap()
        fun onSpace()
        fun onBackspace()
        fun onEnter()
        fun onModeCycle()
        fun onEmojiToggle()
        fun onNumberRowToggle()
        /** Haptic feedback request (e.g. popup opened). */
        fun onFeedback()
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
    private var pal = Theme.palette(context)
    private val handler = Handler(Looper.getMainLooper())

    private val rows: LinearLayout
    private val keys = mutableListOf<OsmiumKey>()
    private var numberRow: LinearLayout? = null

    /** Probhat labels: physical char -> (normal, shifted, altGr). */
    private val probhatLabels: Map<String, ProbhatKey> by lazy {
        val raw = context.assets.open("layouts/probhat-labels.json").bufferedReader().readText()
        val root = JSONObject(raw)
        val map = mutableMapOf<String, ProbhatKey>()
        val letters = root.getJSONObject("letters")
        for (ch in letters.keys()) {
            val e = letters.getJSONObject(ch)
            map[ch] = ProbhatKey(
                n = e.optString("n", ch),
                s = e.optString("s", ch.uppercase()),
                a = if (e.has("a")) e.getString("a") else null
            )
        }
        val digits = root.getJSONObject("digits")
        for (ch in digits.keys()) {
            val e = digits.getJSONObject(ch)
            map[ch] = ProbhatKey(
                n = e.optString("n", ch),
                s = ch,
                a = if (e.has("a")) e.getString("a") else null
            )
        }
        val punct = root.getJSONObject("punct")
        for (ch in punct.keys()) {
            val e = punct.getJSONObject(ch)
            map[ch] = ProbhatKey(
                n = e.optString("n", ch),
                s = ch,
                a = if (e.has("a")) e.getString("a") else null
            )
        }
        map
    }

    private data class ProbhatKey(val n: String, val s: String, val a: String?)

    init {
        rows = LinearLayout(context).apply { orientation = VERTICAL }
        addView(rows, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        numberRow = buildRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), digitRow = true)
            .also {
                it.visibility = GONE
                rows.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }

        rows.addView(buildRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")))
        rows.addView(buildRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")))
        rows.addView(buildActionMiddleRow())
        rows.addView(buildBottomRow())
        relabelAll()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildRow(codes: List<String>, digitRow: Boolean = false): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (code in codes) {
            val key = OsmiumKey(context, OsmiumKey.Kind.CHAR)
            key.tag = code
            attachKeyTouch(key, code, digitRow)
            row.addView(
                key,
                LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(4), dp(2), dp(2)) }
            )
        }
        return row
    }

    // ------------------------------------------------------------------
    // Long-press popup data, per mode
    // ------------------------------------------------------------------

    private fun popupsFor(code: String, digitRow: Boolean): List<PopupEntry> {
        val idx = TOP_ROW.indexOf(code)
        return when (mode) {
            KeyboardMode.PROBHAT -> {
                val entries = mutableListOf<PopupEntry>()
                val p = probhatLabels[code]
                if (digitRow) {
                    // Fraction-sign AltGr layer on Bengali digit keys.
                    p?.a?.let { entries += PopupEntry(it, code, altgr = true) }
                } else {
                    // Top-row letters: long-press types the digit (Bengali here).
                    if (idx >= 0) probhatLabels[TOP_DIGITS[idx]]?.let { d ->
                        entries += PopupEntry(d.n, TOP_DIGITS[idx], altgr = false)
                    }
                    p?.a?.let { entries += PopupEntry(it, code, altgr = true) }
                    if (code == "." || code == ",") {
                        PROBHAT_PUNCT_EXTRAS[code]?.forEach { ch ->
                            probhatLabels[ch]?.let { p2 ->
                                entries += PopupEntry(p2.n, ch, altgr = false)
                            }
                        }
                    }
                }
                entries
            }
            else -> {
                if (digitRow) return emptyList()
                when {
                    idx >= 0 -> listOf(PopupEntry(TOP_DIGITS[idx], TOP_DIGITS[idx], altgr = false))
                    code == "." -> PUNCT_POPUP.map { PopupEntry(it, it, altgr = false) }
                    code == "," -> COMMA_POPUP.map { PopupEntry(it, it, altgr = false) }
                    else -> emptyList()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Touch handling: press feedback, long-press popup, slide-select
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun attachKeyTouch(key: OsmiumKey, code: String, digitRow: Boolean) {
        keys.add(key)
        key.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    key.isPressed = true
                    cancelLongPress()
                    val entries = popupsFor(code, digitRow)
                    if (entries.isNotEmpty()) {
                        pendingLongPress = Runnable {
                            pendingLongPress = null
                            showPopup(key, entries)
                            listener?.onFeedback()
                        }
                        handler.postDelayed(pendingLongPress, LONG_PRESS_MS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popup != null) highlightChipAt(ev.rawX, ev.rawY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    key.isPressed = false
                    val wasPopup = popup != null
                    cancelLongPress()
                    if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        if (wasPopup) {
                            commitHighlightedChip()
                        } else {
                            charTap(code, digitRow)
                        }
                    }
                    hidePopup()
                    true
                }
                else -> false
            }
        }
    }

    private var pendingLongPress: Runnable? = null

    private fun cancelLongPress() {
        pendingLongPress?.let { handler.removeCallbacks(it) }
        pendingLongPress = null
    }

    /** Plain tap on a character key (no popup involved). */
    private fun charTap(code: String, digitRow: Boolean) {
        if (digitRow) {
            listener?.onKey(code, shifted = false, altgr = false)
            return
        }
        listener?.onKey(code, shift || capsLock, altgr = false)
        if (shift && !capsLock) {
            shift = false
            relabelAll()
        }
    }

    // ------------------------------------------------------------------
    // Popup view management
    // ------------------------------------------------------------------

    private var popup: LinearLayout? = null
    private var popupChips = mutableListOf<Pair<TextView, PopupEntry>>()
    private var highlightedChip = -1

    private fun showPopup(anchor: OsmiumKey, entries: List<PopupEntry>) {
        hidePopup()
        val chipWidth = dp(42)
        val popupWidth = entries.size * chipWidth + dp(10)
        val popupHeight = dp(48)

        val locKey = IntArray(2).also { anchor.getLocationInWindow(it) }
        val locSelf = IntArray(2).also { this@KeyboardView.getLocationInWindow(it) }
        val anchorCenterX = locKey[0] - locSelf[0] + anchor.width / 2
        val anchorTop = locKey[1] - locSelf[1]

        val panel = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(pal.popupBg)
                setStroke(dp(1), pal.barBg)
            }
            elevation = dp(4).toFloat()
            setPadding(dp(5), dp(1), dp(5), dp(1))
        }
        popupChips = mutableListOf()
        for (entry in entries) {
            val chip = TextView(context).apply {
                gravity = Gravity.CENTER
                text = entry.label
                textSize = 19f
                setTextColor(pal.keyText)
            }
            popupChips += chip to entry
            panel.addView(chip, LinearLayout.LayoutParams(chipWidth, LinearLayout.LayoutParams.MATCH_PARENT))
        }

        val lp = FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, popupHeight, Gravity.TOP or Gravity.START
        )
        // Clamp horizontally so the popup stays on screen.
        val left = (anchorCenterX - popupWidth / 2)
            .coerceIn(dp(2), (width - popupWidth - dp(2)).coerceAtLeast(dp(2)))
        lp.leftMargin = left
        lp.topMargin = (anchorTop - popupHeight - dp(6)).coerceAtLeast(dp(2))
        addView(panel, lp)
        popup = panel
        highlightedChip = -1
    }

    private fun highlightChipAt(rawX: Float, rawY: Float) {
        var found = -1
        val loc = IntArray(2)
        for ((i, pair) in popupChips.withIndex()) {
            pair.first.getLocationInWindow(loc)
            if (rawX >= loc[0] && rawX <= loc[0] + pair.first.width &&
                rawY >= loc[1] && rawY <= loc[1] + pair.first.height
            ) {
                found = i
                break
            }
        }
        if (found != highlightedChip) {
            highlightedChip = found
            for ((i, pair) in popupChips.withIndex()) {
                if (i == found) {
                    pair.first.setBackgroundColor(pal.accent)
                    pair.first.setTextColor(Theme.dark.keyText)
                } else {
                    pair.first.setBackgroundColor(Color.TRANSPARENT)
                    pair.first.setTextColor(pal.keyText)
                }
            }
        }
    }

    private fun commitHighlightedChip() {
        val entry = popupChips.getOrNull(highlightedChip)?.second ?: return
        listener?.onKey(entry.code, shifted = false, altgr = entry.altgr)
    }

    private fun hidePopup() {
        popup?.let { removeView(it) }
        popup = null
        popupChips = mutableListOf()
        highlightedChip = -1
    }

    // ------------------------------------------------------------------
    // Action rows
    // ------------------------------------------------------------------

    private fun buildActionMiddleRow(): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }

        val shiftKey = OsmiumKey(context, OsmiumKey.Kind.ACTION).apply {
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
            val key = OsmiumKey(context, OsmiumKey.Kind.CHAR)
            key.tag = code
            attachKeyTouch(key, code, digitRow = false)
            row.addView(key, rowParams(1f))
        }

        val backspace = OsmiumKey(context, OsmiumKey.Kind.ACTION).apply {
            tag = "backspace"
            text = "⌫"
            setOnClickListener { listener?.onBackspace() }
            // Hold to repeat-delete.
            var repeat: Runnable? = null
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val r: Runnable = object : Runnable {
                            override fun run() {
                                listener?.onBackspace()
                                handler.postDelayed(this, 60)
                            }
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

        val emojiKey = OsmiumKey(context, OsmiumKey.Kind.ACTION).apply {
            tag = "emoji"
            text = "☺"
            setOnClickListener { listener?.onEmojiToggle() }
        }
        keys.add(emojiKey)
        row.addView(emojiKey, rowParams(1f))

        val modeKey = OsmiumKey(context, OsmiumKey.Kind.ACTION).apply {
            tag = "mode"
            setOnClickListener { listener?.onModeCycle() }
        }
        keys.add(modeKey)
        row.addView(modeKey, rowParams(1f))

        val comma = OsmiumKey(context, OsmiumKey.Kind.CHAR)
        comma.tag = ","
        attachKeyTouch(comma, ",", digitRow = false)
        row.addView(comma, rowParams(1f))

        val space = OsmiumKey(context, OsmiumKey.Kind.ACTION).apply {
            tag = "space"
            setOnClickListener { listener?.onSpace() }
        }
        keys.add(space)
        row.addView(space, rowParams(4f))

        val period = OsmiumKey(context, OsmiumKey.Kind.CHAR)
        period.tag = "."
        attachKeyTouch(period, ".", digitRow = false)
        row.addView(period, rowParams(1f))

        val enter = OsmiumKey(context, OsmiumKey.Kind.ACCENT).apply {
            tag = "enter"
            text = "⏎"
            setOnClickListener { listener?.onEnter() }
        }
        keys.add(enter)
        row.addView(enter, rowParams(1.4f))

        return row
    }

    private fun rowParams(weight: Float) = LinearLayout.LayoutParams(0, dp(46), weight).apply {
        setMargins(dp(2), dp(4), dp(2), dp(2))
    }

    /** Toggles the number row. Returns the new visibility state. */
    fun toggleNumberRow(): Boolean {
        numberRowVisible = !numberRowVisible
        numberRow?.visibility = if (numberRowVisible) VISIBLE else GONE
        return numberRowVisible
    }

    fun relabelAll() {
        val shifted = shift || capsLock
        for (key in keys) {
            val tag = key.tag as? String ?: continue
            when (tag) {
                "shift" -> {
                    key.text = "⇧"
                    key.setTextColor(if (shifted) pal.accent else pal.keyTextHint)
                }
                "mode" -> key.text = when (mode) {
                    KeyboardMode.ENGLISH -> "EN"
                    KeyboardMode.AVRO -> "অ"
                    KeyboardMode.PROBHAT -> "প্"
                }
                "space" -> key.text = when (mode) {
                    KeyboardMode.ENGLISH -> "English"
                    KeyboardMode.AVRO -> "অভ্র"
                    KeyboardMode.PROBHAT -> "প্রভাত"
                }
                "enter", "backspace", "emoji" -> Unit // static labels
                else -> key.text = labelFor(tag, shifted)
            }
        }
    }

    private fun labelFor(code: String, shifted: Boolean): String = when (mode) {
        KeyboardMode.PROBHAT -> {
            val p = probhatLabels[code]
            if (shifted) p?.s ?: code else p?.n ?: code
        }
        else -> if (shifted) code.uppercase() else code
    }

    /** Re-styles every view for the current theme; call after theme changes. */
    fun applyTheme() {
        pal = Theme.palette(context)
        setBackgroundColor(pal.keyboardBg)
        for (key in keys) key.refreshStyle()
        relabelAll()
    }

    private val TOP_ROW = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val TOP_DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private companion object {
        const val LONG_PRESS_MS = 350L

        /** Extra characters offered on the punctuation keys (English/Avro). */
        val PUNCT_POPUP = listOf("!", "?", ";", ":", "'", "\"")
        val COMMA_POPUP = listOf("'", "\"", ":", ";", "!")
        /** Probhat: extra normal-layer chars reachable from '.' / ',' popups. */
        val PROBHAT_PUNCT_EXTRAS = mapOf(
            "." to listOf("!", "?", ":", ";"),
            "," to listOf("'", "\"", ":", ";")
        )
    }

    /** A single key. */
    @SuppressLint("ViewConstructor")
    inner class OsmiumKey(context: Context, val kind: Kind) : TextView(context) {

        enum class Kind { CHAR, ACTION, ACCENT }

        init {
            gravity = Gravity.CENTER
            textSize = when (kind) {
                Kind.CHAR -> 18f
                else -> 16f
            }
            typeface = if (kind == Kind.CHAR) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            refreshStyle()
        }

        fun refreshStyle() {
            background = when (kind) {
                Kind.ACCENT -> fillDrawable(pal.accent, pal.accent)
                Kind.ACTION -> stateDrawable(pal.keyAction, pal.keyPressed)
                else -> stateDrawable(pal.keyNormal, pal.keyPressed)
            }
            setTextColor(if (kind == Kind.ACTION) pal.keyTextHint else pal.keyText)
        }

        private fun round(color: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(color)
        }

        private fun fillDrawable(normal: Int) = round(normal)

        private fun stateDrawable(normal: Int, pressed: Int): StateListDrawable {
            val list = StateListDrawable()
            list.addState(intArrayOf(android.R.attr.state_pressed), round(pressed))
            list.addState(android.util.StateSet.NOTHING, round(normal))
            return list
        }
    }
}
