package org.osmium.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import java.io.File

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
 * Extras:
 *  - Long-press popups (G-board style): hold, slide onto a character, release.
 *  - Spacebar glide: drag horizontally on the spacebar to move the text cursor.
 *  - Appearance driven by user prefs: key height/radius/font scale, colours
 *    (via [Theme]), optional background image.
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
        /** Move the text cursor by `delta` codepoints (spacebar glide). */
        fun onCursorGlide(delta: Int)
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

    // Appearance settings (read once — the IME rebuilds the view when they change).
    private val keyHeightDp = (BASE_KEY_DP * Prefs.keyHeightScale(context) / 100f).toInt()
    private val charTextSize = BASE_CHAR_TEXT * Prefs.keyFontScale(context) / 100f
    private val actionTextSize = BASE_ACTION_TEXT * Prefs.keyFontScale(context) / 100f
    private val radiusDp = Prefs.keyRadiusDp(context)
    private val showSpacebarLabel = Prefs.spacebarLabel(context)
    private val longPressMs = Prefs.longPressMs(context).toLong()
    private val repeatMs = Prefs.backspaceRepeatMs(context).toLong()

    private val handler = Handler(Looper.getMainLooper())

    private val rows: LinearLayout
    private val keys = mutableListOf<OsmiumKey>()
    private var numberRow: LinearLayout? = null

    /** Labels: physical char -> (normal, shifted, altGr). Loaded per mode. */
    private var labels: Map<String, LayoutLabels.Entry> = emptyMap()

    init {
        loadLabels()
        rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(rows, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        numberRowVisible = Prefs.numberRowAlways(context)
        numberRow = buildRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), digitRow = true)
            .also {
                it.visibility = if (numberRowVisible) VISIBLE else GONE
                rows.addView(it, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }

        rows.addView(buildRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")))
        rows.addView(buildRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")))
        rows.addView(buildActionMiddleRow())
        rows.addView(buildBottomRow())
        applyBackground()
        relabelAll()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    // ------------------------------------------------------------------
    // Label loading: bundled Probhat, or a user-installed layout
    // ------------------------------------------------------------------

    /** Path to a user layout JSON file, or null for the bundled Probhat labels. */
    var userLayoutPath: String? = null
        set(value) {
            field = value
            loadLabels()
        }

    private fun loadLabels() {
        labels = try {
            val raw = userLayoutPath?.let { File(it).readText() }
                ?: context.assets.open("layouts/probhat-labels.json").bufferedReader().readText()
            LayoutLabels.fromLayoutJson(JSONObject(raw))
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    // ------------------------------------------------------------------
    // Background image (user-chosen, optional)
    // ------------------------------------------------------------------

    private fun applyBackground() {
        val path = Prefs.bgImagePath(context)
        val file = if (path.isNotEmpty()) File(path) else null
        if (file == null || !file.exists()) {
            setBackgroundColor(pal.keyboardBg)
            return
        }
        try {
            // Decode at roughly screen size to keep memory sane.
            val targetW = resources.displayMetrics.widthPixels / 2
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > targetW * 2) sample *= 2
            val bmpOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, bmpOpts) ?: run {
                setBackgroundColor(pal.keyboardBg); return
            }
            val dim = Prefs.bgImageDim(context).coerceIn(0, 90)
            val dimAlpha = (dim * 255 / 100)
            val dimColor = Color.argb(dimAlpha, 0, 0, 0)
            val dimmed = if (Theme.isDark(context))
                Color.argb(dimAlpha, 10, 12, 16)
            else Color.argb(dimAlpha, 240, 241, 245)
            background = LayerDrawable(arrayOf(
                BitmapDrawable(resources, bmp),
                ColorDrawable(if (pal.keyboardBg == Theme.dark.keyboardBg) dimColor else dimmed),
            ))
        } catch (_: Throwable) {
            setBackgroundColor(pal.keyboardBg)
        }
    }

    private fun buildRow(codes: List<String>, digitRow: Boolean = false): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (code in codes) {
            val key = OsmiumKey(context, OsmiumKeyKind.CHAR)
            key.tag = code
            attachKeyTouch(key, code, digitRow)
            row.addView(
                key,
                LinearLayout.LayoutParams(0, dp(keyHeightDp), 1f).apply {
                    setMargins(dp(2), dp(4), dp(2), dp(2))
                }
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
                val p = labels[code]
                if (digitRow) {
                    // Fraction-sign AltGr layer on Bengali digit keys.
                    p?.a?.let { entries += PopupEntry(it, code, altgr = true) }
                } else {
                    // Top-row letters: long-press types the digit (Bengali here).
                    if (idx >= 0) labels[TOP_DIGITS[idx]]?.let { d ->
                        entries += PopupEntry(d.n, TOP_DIGITS[idx], altgr = false)
                    }
                    p?.a?.let { entries += PopupEntry(it, code, altgr = true) }
                    if (code == "." || code == ",") {
                        PROBHAT_PUNCT_EXTRAS[code]?.forEach { ch ->
                            labels[ch]?.let { p2 ->
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
                    cancelPendingLongPress()
                    val entries = popupsFor(code, digitRow)
                    if (entries.isNotEmpty()) {
                        val r = Runnable {
                            pendingLongPress = null
                            showPopup(key, entries)
                            listener?.onFeedback()
                        }
                        pendingLongPress = r
                        handler.postDelayed(r, longPressMs)
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
                    cancelPendingLongPress()
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

    private fun cancelPendingLongPress() {
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
    // Spacebar glide cursor
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSpaceGlide(space: OsmiumKey) {
        var downX = 0f
        var glideActive = false
        var accumulated = 0f
        var hapticSteps = 0

        space.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    accumulated = 0f
                    glideActive = false
                    hapticSteps = 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Prefs.glideCursor(context) && !glideActive) {
                        glideActive = Math.abs(ev.rawX - downX) > dp(GLIDE_START_DP)
                        if (glideActive) {
                            space.isPressed = false
                            listener?.onFeedback()
                        }
                    }
                    if (glideActive) {
                        accumulated += ev.rawX - downX
                        downX = ev.rawX
                        val step = dp(Prefs.glideSensitivityDp(context)).coerceAtLeast(dp(8)).toFloat()
                        val steps = (accumulated / step).toInt()
                        if (steps != 0) {
                            accumulated -= steps * step
                            listener?.onCursorGlide(steps)
                            hapticSteps += Math.abs(steps)
                            if (hapticSteps >= GLIDE_HAPTIC_EVERY) {
                                hapticSteps = 0
                                listener?.onFeedback()
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!glideActive) listener?.onSpace()
                    glideActive = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    glideActive = false
                    true
                }
                else -> false
            }
        }
    }

    private companion object {
        /** Horizontal drag (dp) before a space tap becomes a cursor glide. */
        const val GLIDE_START_DP = 18
        const val GLIDE_HAPTIC_EVERY = 3

        const val BASE_KEY_DP = 46
        const val BASE_CHAR_TEXT = 18f
        const val BASE_ACTION_TEXT = 16f

        /** Extra characters offered on the punctuation keys (English/Avro). */
        val PUNCT_POPUP = listOf("!", "?", ";", ":", "'", "\"")
        val COMMA_POPUP = listOf("'", "\"", ":", ";", "!")
        /** Probhat: extra normal-layer chars reachable from '.' / ',' popups. */
        val PROBHAT_PUNCT_EXTRAS = mapOf(
            "." to listOf("!", "?", ":", ";"),
            "," to listOf("'", "\"", ":", ";")
        )
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
            orientation = LinearLayout.HORIZONTAL
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
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val shiftKey = OsmiumKey(context, OsmiumKeyKind.ACTION).apply {
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
            val key = OsmiumKey(context, OsmiumKeyKind.CHAR)
            key.tag = code
            attachKeyTouch(key, code, digitRow = false)
            row.addView(key, rowParams(1f))
        }

        val backspace = OsmiumKey(context, OsmiumKeyKind.ACTION).apply {
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
                                handler.postDelayed(this, repeatMs)
                            }
                        }
                        repeat = r
                        handler.postDelayed(r, longPressMs)
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
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val emojiKey = OsmiumKey(context, OsmiumKeyKind.ACTION).apply {
            tag = "emoji"
            text = "☺"
            setOnClickListener { listener?.onEmojiToggle() }
        }
        keys.add(emojiKey)
        row.addView(emojiKey, rowParams(1f))

        val modeKey = OsmiumKey(context, OsmiumKeyKind.ACTION).apply {
            tag = "mode"
            setOnClickListener { listener?.onModeCycle() }
        }
        keys.add(modeKey)
        row.addView(modeKey, rowParams(1f))

        val comma = OsmiumKey(context, OsmiumKeyKind.CHAR)
        comma.tag = ","
        attachKeyTouch(comma, ",", digitRow = false)
        row.addView(comma, rowParams(1f))

        val space = OsmiumKey(context, OsmiumKeyKind.ACTION).apply {
            tag = "space"
        }
        attachSpaceGlide(space)
        keys.add(space)
        row.addView(space, rowParams(4f))

        val period = OsmiumKey(context, OsmiumKeyKind.CHAR)
        period.tag = "."
        attachKeyTouch(period, ".", digitRow = false)
        row.addView(period, rowParams(1f))

        val enter = OsmiumKey(context, OsmiumKeyKind.ACCENT).apply {
            tag = "enter"
            text = "⏎"
            setOnClickListener { listener?.onEnter() }
        }
        keys.add(enter)
        row.addView(enter, rowParams(1.4f))

        return row
    }

    private fun rowParams(weight: Float) = LinearLayout.LayoutParams(0, dp(keyHeightDp), weight).apply {
        setMargins(dp(2), dp(4), dp(2), dp(2))
    }

    /** Toggles the number row. Returns the new visibility state. */
    fun toggleNumberRow(): Boolean {
        numberRowVisible = !numberRowVisible
        numberRow?.visibility = if (numberRowVisible) VISIBLE else GONE
        return numberRowVisible
    }

    /** Forces the shift state (e.g. auto-capitalize at a sentence start). */
    fun setShift(value: Boolean) {
        shift = value
        if (value) relabelAll()
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
                "space" -> key.text = if (showSpacebarLabel) when (mode) {
                    KeyboardMode.ENGLISH -> "English"
                    KeyboardMode.AVRO -> "অভ্র"
                    KeyboardMode.PROBHAT -> "প্রভাত"
                } else ""
                "enter", "backspace", "emoji" -> Unit // static labels
                else -> key.text = labelFor(tag, shifted)
            }
        }
    }

    private fun labelFor(code: String, shifted: Boolean): String = when (mode) {
        KeyboardMode.PROBHAT -> {
            val p = labels[code]
            if (shifted) p?.s ?: code else p?.n ?: code
        }
        else -> if (shifted) code.uppercase() else code
    }

    /** Re-styles every view for the current theme; call after theme changes. */
    fun applyTheme() {
        pal = Theme.palette(context)
        applyBackground()
        for (key in keys) key.refreshStyle()
        relabelAll()
    }

    private val TOP_ROW = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val TOP_DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    /** A single key. */
    @SuppressLint("ViewConstructor")
    inner class OsmiumKey(context: Context, val kind: OsmiumKeyKind) : TextView(context) {

        init {
            gravity = Gravity.CENTER
            textSize = when (kind) {
                OsmiumKeyKind.CHAR -> charTextSize
                else -> actionTextSize
            }
            typeface = if (kind == OsmiumKeyKind.CHAR) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            refreshStyle()
        }

        fun refreshStyle() {
            background = when (kind) {
                OsmiumKeyKind.ACCENT -> fillDrawable(pal.accent)
                OsmiumKeyKind.ACTION -> stateDrawable(pal.keyAction, pal.keyPressed)
                else -> stateDrawable(pal.keyNormal, pal.keyPressed)
            }
            setTextColor(if (kind == OsmiumKeyKind.ACTION) pal.keyTextHint else pal.keyText)
        }

        private fun round(color: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
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

/**
 * Shared label parsing for keyboard layouts: works for the bundled
 * probhat-labels.json AND for full OpenBangla layout files (Probhat.json, or
 * any user-installed one), which use Key_X_Normal / Key_X_Shift / Key_X_AltGr.
 */
object LayoutLabels {

    /** (normal, shifted, altGr) labels for one physical key. */
    data class Entry(val n: String, val s: String, val a: String?)

    fun fromLayoutJson(root: JSONObject): Map<String, Entry> {
        val map = mutableMapOf<String, Entry>()

        fun addLabel(code: String, n: String?, s: String?, a: String?) {
            val normal = n?.takeIf { it.isNotEmpty() } ?: return
            map[code] = Entry(
                n = normal,
                s = s?.takeIf { it.isNotEmpty() } ?: normal,
                a = a?.takeIf { it.isNotEmpty() }
            )
        }

        if (root.has("letters")) {
            // Bundled label format: { letters: { q: {n,s,a} }, digits: {...}, punct: {...} }
            for (section in listOf("letters", "digits", "punct")) {
                if (!root.has(section)) continue
                val obj = root.getJSONObject(section)
                for (ch in obj.keys()) {
                    val e = obj.getJSONObject(ch)
                    map[ch] = Entry(
                        n = e.optString("n", ch),
                        s = e.optString("s", if (section == "letters") ch.uppercase() else ch),
                        a = if (e.has("a")) e.optString("a", "").takeIf { it.isNotEmpty() } else null
                    )
                }
            }
            return map
        }

        // OpenBangla layout format: { Key_q_Normal: "দ", Key_q_AltGr: ... , ... }
        val KEYS = ("qwertyuiopasdfghjklzxcvbnm0123456789" +
                ",.;:'\"?!-/\\")
        val NAMES = mapOf(
            ',' to "Comma", '.' to "Period", ';' to "Semicolon", ':' to "Colon",
            '\'' to "Apostrophe", '"' to "Quote", '!' to "Exclaim", '?' to "Question",
            '-' to "Minus", '/' to "Slash", '\\' to "Backslash"
        )
        for (c in KEYS) {
            val name = if (c.isLetterOrDigit()) c.toString() else NAMES[c] ?: continue
            val normal = root.optString("Key_${name}_Normal", "")
            if (normal.isEmpty()) continue
            val shifted = root.optString("Key_${name.uppercase()}_Normal", "")
            val altGr = root.optString("Key_${name}_AltGr", "")
            addLabel(c.toString(), normal, shifted.ifEmpty { normal }, altGr)
        }
        return map
    }
}
