package org.osmium.keyboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.StateSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/** Typing modes Osmium supports. */
enum class KeyboardMode { ENGLISH, AVRO, PROBHAT }

/**
 * One long-press popup choice.
 *
 * [direct] marks characters riti cannot map (non-ASCII). Those are committed as
 * literal text by the service instead of being fed to the engine, which would drop
 * them because `keycodeForChar()` returns 0.
 */
data class PopupEntry(
    val label: String,
    val code: String,
    val altgr: Boolean,
    val direct: Boolean = false,
)

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
        /** Select suggestion [index] while a riti session is active (digit keys 1-5). */
        fun onCandidateSelect(index: Int)
        fun onShiftTap()
        fun onSpace()
        fun onBackspace()
        /** Delete the word before the cursor (backspace swipe left). */
        fun onDeleteWord()
        /** Delete from the cursor to the start of the line (backspace swipe down). */
        fun onDeleteToLineStart()
        fun onEnter()
        fun onModeCycle()
        fun onEmojiToggle()
        fun onNumberRowToggle()
        /** Toggle navigation panel. */
        fun onNavigationToggle()
        /** Haptic feedback request (e.g. popup opened). */
        fun onFeedback()
        /** Move the text cursor by `delta` codepoints (spacebar glide). */
        fun onCursorGlide(delta: Int)
        /** Commit a literal symbol that riti cannot map (non-ASCII). */
        fun onSymbol(code: String)
        /** Open the full Symbols picker from a popup's "more" chip. */
        fun onSymbolsRequested()
    }

    var listener: Listener? = null

    var mode: KeyboardMode = KeyboardMode.AVRO
        set(value) {
            field = value
            shift = false
            capsLock = false
            manualShift = false
            candidateSelectActive = false
            relabelAll()
        }

    private var shift = false
    private var capsLock = false
    /** True once the user has touched the shift key; auto-cap must not override it. */
    private var manualShift = false
    /** Digit keys 1-5 act as candidate selectors while a riti session is live. */
    private var candidateSelectActive = false
    private var enterAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    private var numberRowVisible = false
    /** True once the user toggled the number row; auto-show then backs off. */
    private var numberRowManual = false
    private var pal = Theme.palette(context)

    // Appearance settings (read once — the IME rebuilds the view when they change).
    private val isLandscape = resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    private val keyHeightDp = ((BASE_KEY_DP * Prefs.keyHeightScale(context) / 100f) *
        (if (isLandscape) 0.72f else 1f)).toInt().coerceAtLeast(28)
    private val charTextSize = BASE_CHAR_TEXT * Prefs.keyFontScale(context) / 100f
    private val actionTextSize = BASE_ACTION_TEXT * Prefs.keyFontScale(context) / 100f
    private val radiusDp = Prefs.keyRadiusDp(context)
    private val showSpacebarLabel = Prefs.spacebarLabel(context)

    private val handler = Handler(Looper.getMainLooper())

    // Timed gesture callbacks live on the view (not in touch-listener locals) so they
    // can all be cancelled when the view is detached/replaced (B12).
    private var navLongPressRunnable: Runnable? = null
    private var backspaceRepeatRunnable: Runnable? = null
    private var edgeRepeatRunnable: Runnable? = null
    private var spaceGlideActive = false
    private var activeTouchCount = 0

    // Code-built feedback overlays (translucent; added on top of the key rows).
    private var backspaceKey: OsmiumKey? = null
    private var glideTrack: View? = null
    private var glideTrackKnob: View? = null
    private var swipeGhost: TextView? = null

    /** Raw (screen-space) coordinates of [pointerId], or null if it is not in [ev]. */
    private fun MotionEvent.rawOf(pointerId: Int): Pair<Float, Float>? {
        if (pointerId == INVALID_POINTER_ID) return null
        val index = findPointerIndex(pointerId)
        return if (index >= 0) getRawX(index) to getRawY(index) else null
    }

    /** Cancel every pending/timed gesture callback and clear transient gesture state. */
    private fun cancelGestureCallbacks() {
        pendingLongPress?.let { handler.removeCallbacks(it) }
        pendingLongPress = null
        navLongPressRunnable?.let { handler.removeCallbacks(it) }
        navLongPressRunnable = null
        backspaceRepeatRunnable?.let { handler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
        edgeRepeatRunnable?.let { handler.removeCallbacks(it) }
        edgeRepeatRunnable = null
        spaceGlideActive = false
        hideGlideTrack()
        hideSwipeGhost()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelGestureCallbacks()
        hidePopup()
        for (key in keys) key.isPressed = false
        activeTouchCount = 0
        // The IME rebuilds this view; never let a detached view receive callbacks.
        listener = null
    }

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

    private fun pxToDp(px: Float): Float = px / resources.displayMetrics.density

    // ------------------------------------------------------------------
    // Label loading: bundled Probhat, or a user-installed layout
    // ------------------------------------------------------------------

    /** Path to a user layout JSON file, or null for the bundled Probhat labels. */
    var userLayoutPath: String? = null
        set(value) {
            field = value
            loadLabels()
            relabelAll()
        }

    private fun loadLabels() {
        fun bundled(): Map<String, LayoutLabels.Entry> = LayoutLabels.fromLayoutJson(
            JSONObject(context.assets.open("layouts/probhat-labels.json").bufferedReader().readText())
        )
        // A missing/corrupt user layout must never degrade into raw key codes.
        labels = try {
            val raw = userLayoutPath?.let { File(it).readText() }
            if (raw == null) bundled()
            else LayoutLabels.fromLayoutJson(JSONObject(raw)).ifEmpty { bundled() }
        } catch (_: Throwable) {
            try { bundled() } catch (_: Throwable) { emptyMap() }
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
                        // Bengali modes get the same everyday symbol shortlist. Only
                        // the direct (non-ASCII) entries are appended: ASCII ones are
                        // already reachable above via the Probhat layout labels.
                        entries += symbolPaletteFor(code).filter { it.direct }
                            .map { PopupEntry(it.label, it.code, altgr = false, direct = true) }
                    }
                }
                entries
            }
            else -> {
                if (digitRow) return emptyList()
                when {
                    idx >= 0 -> listOf(PopupEntry(TOP_DIGITS[idx], TOP_DIGITS[idx], altgr = false))
                    code == "." || code == "," -> symbolPaletteFor(code).map {
                        PopupEntry(it.label, it.code, altgr = false, direct = it.direct)
                    }
                    else -> emptyList()
                }
            }
        }
    }

    /** The curated symbol shortlist attached to the `.` / `,` keys. */
    private fun symbolPaletteFor(code: String): List<SymbolEntry> = when (code) {
        "." -> SymbolPalette.PERIOD
        "," -> SymbolPalette.COMMA
        else -> emptyList()
    }

    // ------------------------------------------------------------------
    // Touch handling: press feedback, long-press popup, slide-select
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun attachKeyTouch(key: OsmiumKey, code: String, digitRow: Boolean) {
        keys.add(key)
        var activePointerId = INVALID_POINTER_ID

        /** Finish this key's gesture; a non-owning pointer never commits (B11). */
        fun endGesture(commit: Boolean) {
            if (activePointerId == INVALID_POINTER_ID) return
            key.isPressed = false
            val wasPopup = popup != null
            cancelPendingLongPress()
            if (commit) {
                if (wasPopup) commitHighlightedChip() else charTap(code, digitRow)
            }
            hidePopup()
            activePointerId = INVALID_POINTER_ID
            activeTouchCount = (activeTouchCount - 1).coerceAtLeast(0)
        }

        key.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (activePointerId == INVALID_POINTER_ID) {
                        activePointerId = ev.getPointerId(0)
                        activeTouchCount++
                    }
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
                        handler.postDelayed(r, Prefs.longPressMs(context).toLong())
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> true // secondary pointer: ignore
                MotionEvent.ACTION_MOVE -> {
                    val p = ev.rawOf(activePointerId) ?: return@setOnTouchListener true
                    if (popup != null) highlightChipAt(p.first, p.second)
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // Only the owning pointer may finalize the popup when it lifts.
                    if (ev.getPointerId(ev.actionIndex) == activePointerId) endGesture(commit = true)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    endGesture(commit = ev.getPointerId(ev.actionIndex) == activePointerId)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    endGesture(commit = false)
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
            // Home-row candidate selection: digits 1-5 pick candidates 0-4
            // while a session is live, otherwise they type normally.
            if (candidateSelectActive && code.length == 1 && code[0] in '1'..'5') {
                listener?.onCandidateSelect(code[0] - '1')
            } else {
                listener?.onKey(code, shifted = false, altgr = false)
            }
            return
        }
        listener?.onKey(code, shift || capsLock, altgr = false)
        if (shift && !capsLock) {
            shift = false
            manualShift = false
            relabelAll()
        }
    }

    // ------------------------------------------------------------------
    // Spacebar glide cursor
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSpaceGlide(space: OsmiumKey) {
        var activePointerId = INVALID_POINTER_ID
        var downX = 0f
        var downY = 0f
        var accumulated = 0f
        var hapticSteps = 0
        var navLongPressFired = false
        var edgeDir = GestureResolver.NONE
        var glideOriginX = 0f
        val glideActivationPx = dp(GLIDE_START_DP).toFloat()
        val navCancelPx = dp(NAV_MOVE_CANCEL_DY_DP).toFloat()
        val edgePx = dp(EDGE_ZONE_DP).toFloat()
        val screenWidth = resources.displayMetrics.widthPixels

        fun cancelNavLongPress() {
            navLongPressRunnable?.let { handler.removeCallbacks(it) }
            navLongPressRunnable = null
        }

        fun cancelEdgeRepeat() {
            edgeRepeatRunnable?.let { handler.removeCallbacks(it) }
            edgeRepeatRunnable = null
            edgeDir = GestureResolver.NONE
        }

        /**
         * Edge auto-repeat: nothing moves until the finger has dwelt at the edge
         * for [EDGE_DWELL_MS]. The same runnable then moves one cursor step per
         * (tunable) repeat interval. Glide + stillness cancel guarantees the nav
         * long-press can never fire while this is running.
         */
        fun scheduleEdgeRepeat(dir: Int) {
            cancelEdgeRepeat()
            edgeDir = dir
            val direction = if (dir == GestureResolver.LEFT) -1 else 1
            val r = object : Runnable {
                override fun run() {
                    listener?.onCursorGlide(direction)
                    edgeRepeatRunnable = this
                    handler.postDelayed(this,
                        Prefs.glideEdgeRepeatMs(context).toLong().coerceAtLeast(16L))
                }
            }
            edgeRepeatRunnable = r
            handler.postDelayed(r, EDGE_DWELL_MS.toLong())
        }

        fun endGesture(commit: Boolean) {
            if (activePointerId == INVALID_POINTER_ID) return
            val wasNavFired = navLongPressFired
            val wasGliding = spaceGlideActive
            cancelNavLongPress()
            cancelEdgeRepeat()
            hideGlideTrack()
            space.isPressed = false
            if (commit && !wasNavFired && !wasGliding) listener?.onSpace()
            spaceGlideActive = false
            navLongPressFired = false
            activePointerId = INVALID_POINTER_ID
            activeTouchCount = (activeTouchCount - 1).coerceAtLeast(0)
        }

        space.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (activePointerId == INVALID_POINTER_ID) {
                        activePointerId = ev.getPointerId(0)
                        activeTouchCount++
                    }
                    downX = ev.rawX
                    downY = ev.rawY
                    accumulated = 0f
                    hapticSteps = 0
                    navLongPressFired = false
                    edgeDir = GestureResolver.NONE
                    glideOriginX = ev.rawX
                    space.isPressed = true
                    cancelNavLongPress()
                    cancelEdgeRepeat()
                    hideGlideTrack()
                    val r = Runnable {
                        navLongPressRunnable = null
                        navLongPressFired = true
                        space.isPressed = false
                        listener?.onNavigationToggle()
                        listener?.onFeedback()
                    }
                    navLongPressRunnable = r
                    handler.postDelayed(r, Prefs.longPressMs(context).toLong())
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> true // secondary pointer: ignore
                MotionEvent.ACTION_MOVE -> {
                    val p = ev.rawOf(activePointerId) ?: return@setOnTouchListener true
                    val x = p.first
                    val y = p.second
                    // Any decisive movement (horizontal or vertical) cancels the
                    // stillness-based nav long-press. Once gliding, the nav toggle
                    // is already cancelled and can never fire.
                    if (!navLongPressFired && !spaceGlideActive) {
                        if (Math.abs(x - downX) > glideActivationPx ||
                            Math.abs(y - downY) > navCancelPx
                        ) {
                            cancelNavLongPress()
                        }
                    }
                    val activatedNow = Prefs.glideCursor(context) && !spaceGlideActive &&
                        Math.abs(x - downX) > glideActivationPx
                    if (activatedNow) {
                        spaceGlideActive = true
                        glideOriginX = x
                        space.isPressed = false
                        listener?.onFeedback()
                        showGlideTrack(space)
                    }
                    if (spaceGlideActive) {
                        // The travel spent crossing the activation threshold is not
                        // cursor travel: subtract it from the first accumulated delta.
                        val dx = x - downX
                        accumulated += if (activatedNow) {
                            GestureResolver.travelAfterActivation(dx, glideActivationPx)
                        } else {
                            dx
                        }
                        downX = x
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
                        updateGlideTrack((x - glideOriginX) / (screenWidth * 0.4f))
                        // Edge auto-repeat only while gliding: entering (>24dp from
                        // the screen edge) starts the dwell timer, leaving cancels it.
                        val edgeNow = GestureResolver.edgeDirection(x, screenWidth, edgePx)
                        if (edgeNow != edgeDir) {
                            cancelEdgeRepeat()
                            if (edgeNow != GestureResolver.NONE && !navLongPressFired) {
                                scheduleEdgeRepeat(edgeNow)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (ev.getPointerId(ev.actionIndex) == activePointerId) endGesture(commit = true)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    endGesture(commit = ev.getPointerId(ev.actionIndex) == activePointerId)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    endGesture(commit = false)
                    true
                }
                else -> false
            }
        }
    }

    private companion object {
        /** Sentinel for "no pointer owns this gesture". */
        const val INVALID_POINTER_ID = -1

        /** Horizontal drag (dp) before a space tap becomes a cursor glide. */
        const val GLIDE_START_DP = 18
        const val GLIDE_HAPTIC_EVERY = 3

        /** Backspace swipe direction locks: no value, horizontal (word), vertical (line). */
        const val DIR_NONE = 0
        const val DIR_WORD = 1
        const val DIR_LINE = 2

        /** Finger within this many dp of a screen edge starts glide auto-repeat. */
        const val EDGE_ZONE_DP = 24
        /** Dwell at the edge (ms) before auto-repeat begins. */
        const val EDGE_DWELL_MS = 200

        /** Slop (dp) for accepting a near-miss chip during popup slide-select. */
        const val POPUP_HIT_SLOP_DP = 4
        /** Maximum chips per popup row; longer sets wrap into a grid. */
        const val POPUP_COLUMNS = 8

        /** Vertical travel (dp) off the spacebar that cancels its nav long-press. */
        const val NAV_MOVE_CANCEL_DY_DP = 24

        const val BASE_KEY_DP = 46
        const val BASE_CHAR_TEXT = 18f
        const val BASE_ACTION_TEXT = 16f

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
        // Never render blank chips: a missing label must not become a blank choice.
        val usable = entries.filter { it.label.isNotBlank() }
        if (usable.isEmpty()) return
        // Short popups stay a single row; long ones become a grid so every chip
        // remains a usable touch target instead of overflowing the screen.
        val chipWidth = dp(44)
        // Also cap by the actual view width so the grid never runs off-screen on
        // small devices; never fewer than 3 columns to keep rows meaningful.
        val byWidth = ((width - dp(16)) / chipWidth).coerceAtLeast(3)
        val columns = minOf(POPUP_COLUMNS, usable.size, byWidth)
        val rows = SymbolPalette.rows(usable, columns)
        val chipHeight = dp(42)
        val popupWidth = columns * chipWidth + dp(10)
        val popupHeight = rows.size * chipHeight + dp(8)

        val locKey = IntArray(2).also { anchor.getLocationInWindow(it) }
        val locSelf = IntArray(2).also { this@KeyboardView.getLocationInWindow(it) }
        val anchorCenterX = locKey[0] - locSelf[0] + anchor.width / 2
        val anchorTop = locKey[1] - locSelf[1]

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(pal.popupBg)
                setStroke(dp(1), pal.barBg)
            }
            elevation = dp(4).toFloat()
            setPadding(dp(5), dp(4), dp(5), dp(4))
        }
        popupChips = mutableListOf()
        for (row in rows) {
            val rowView = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (entry in row) {
                val chip = TextView(context).apply {
                    gravity = Gravity.CENTER
                    text = entry.label
                    textSize = 19f
                    setTextColor(pal.keyText)
                    contentDescription = if (entry.code == SymbolPalette.OPEN_SYMBOLS_CODE) {
                        "More symbols"
                    } else {
                        entry.label
                    }
                }
                popupChips += chip to entry
                rowView.addView(chip, LinearLayout.LayoutParams(chipWidth, chipHeight))
            }
            panel.addView(rowView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight))
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
        // MotionEvent.rawX/rawY are screen-absolute; chips must be measured in the
        // same space via getLocationOnScreen() (getLocationInWindow() is offset by
        // the bottom-anchored IME window and can never match raw coords) — A1.
        val slop = dp(POPUP_HIT_SLOP_DP).toFloat()
        val loc = IntArray(2)
        var nearest = -1
        var nearestDist = Float.MAX_VALUE
        for ((i, pair) in popupChips.withIndex()) {
            val chip = pair.first
            chip.getLocationOnScreen(loc)
            val left = loc[0].toFloat()
            val top = loc[1].toFloat()
            val right = left + chip.width
            val bottom = top + chip.height
            // Distance from the touch point to the chip rect (0 when inside). This
            // gives both exact hits and nearest-chip selection within `slop`.
            val dx = maxOf(left - rawX, 0f, rawX - right)
            val dy = maxOf(top - rawY, 0f, rawY - bottom)
            val dist = dx * dx + dy * dy
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = i
            }
        }
        val found = if (nearestDist <= slop * slop) nearest else -1
        if (found != highlightedChip) {
            highlightedChip = found
            for ((i, pair) in popupChips.withIndex()) {
                if (i == found) {
                    pair.first.setBackgroundColor(pal.accent)
                    pair.first.setTextColor(ColorMath.onColor(pal.accent))
                } else {
                    pair.first.setBackgroundColor(Color.TRANSPARENT)
                    pair.first.setTextColor(pal.keyText)
                }
            }
        }
    }

    private fun commitHighlightedChip() {
        if (popupChips.isEmpty()) return
        // A clean long-press with no slide previously committed nothing (the base
        // key was swallowed). For compact popups the centre chip is the natural
        // default; for a symbol grid use the first chip so the result is a
        // predictable, useful character rather than an arbitrary quote.
        val index = if (highlightedChip in popupChips.indices) {
            highlightedChip
        } else if (popupChips.size > POPUP_COLUMNS) {
            0
        } else {
            popupChips.size / 2
        }
        val entry = popupChips[index].second
        when {
            entry.code == SymbolPalette.OPEN_SYMBOLS_CODE -> listener?.onSymbolsRequested()
            entry.direct -> listener?.onSymbol(entry.code)
            else -> listener?.onKey(entry.code, shifted = false, altgr = entry.altgr)
        }
    }

    private fun hidePopup() {
        popup?.let { removeView(it) }
        popup = null
        popupChips = mutableListOf()
        highlightedChip = -1
    }

    // ------------------------------------------------------------------
    // Code-built gesture feedback overlays
    // ------------------------------------------------------------------

    /** A view that never consumes touch: feedback overlays must be pass-through. */
    private fun passThroughView(): View = object : View(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean = false
    }

    private fun passThroughText(): TextView = object : TextView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean = false
    }

    /** Location of [anchor] in this view's coordinate space. */
    private fun anchorTopLeft(anchor: View): Pair<Int, Int> {
        val locKey = IntArray(2).also { anchor.getLocationInWindow(it) }
        val locSelf = IntArray(2).also { this@KeyboardView.getLocationInWindow(it) }
        return (locKey[0] - locSelf[0]) to (locKey[1] - locSelf[1])
    }

    /** Translucent track + sliding knob shown just above the spacebar while gliding. */
    private fun showGlideTrack(space: OsmiumKey) {
        hideGlideTrack()
        val (left, top) = anchorTopLeft(space)
        val h = dp(8)
        val track = passThroughView().apply {
            background = GradientDrawable().apply {
                cornerRadius = (h / 2f).coerceAtLeast(1f)
                setColor(Color.argb(70,
                    Color.red(pal.accent), Color.green(pal.accent), Color.blue(pal.accent)))
            }
        }
        val knob = passThroughView().apply {
            background = GradientDrawable().apply {
                cornerRadius = (h / 2f).coerceAtLeast(1f)
                setColor(pal.accent)
            }
        }
        val container = FrameLayout(context).apply {
            isClickable = false
            addView(track, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(knob, FrameLayout.LayoutParams(h, h, Gravity.CENTER_VERTICAL or Gravity.START))
        }
        val w = (space.width - dp(24)).coerceAtLeast(dp(40))
        addView(container, FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.START).apply {
            leftMargin = left + dp(12)
            topMargin = (top - dp(12)).coerceAtLeast(0)
        })
        glideTrack = container
        glideTrackKnob = knob
    }

    /** [progress] is clamped travel in -1f..1f; negative moves the knob left. */
    private fun updateGlideTrack(progress: Float) {
        val container = glideTrack ?: return
        val knob = glideTrackKnob ?: return
        val lp = knob.layoutParams as FrameLayout.LayoutParams
        val usable = (container.width - knob.width).coerceAtLeast(0)
        lp.leftMargin = (usable * (0.5f + progress.coerceIn(-1f, 1f) * 0.5f)).toInt()
        knob.layoutParams = lp
    }

    private fun hideGlideTrack() {
        glideTrack?.let { removeView(it) }
        glideTrack = null
        glideTrackKnob = null
    }

    /** Translucent arrow ghost on ⌫ while a delete-swipe is in progress. */
    private fun showSwipeGhost(anchor: OsmiumKey, action: SwipeResolver.Action) {
        hideSwipeGhost()
        val (left, top) = anchorTopLeft(anchor)
        val ghost = passThroughText().apply {
            text = if (action == SwipeResolver.Action.DELETE_LINE) "↓" else "←"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.argb(210,
                Color.red(pal.accent), Color.green(pal.accent), Color.blue(pal.accent)))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.argb(45, 0, 0, 0))
            }
        }
        addView(ghost, FrameLayout.LayoutParams(anchor.width, anchor.height,
            Gravity.TOP or Gravity.START).apply {
            leftMargin = left
            topMargin = top
        })
        swipeGhost = ghost
    }

    private fun hideSwipeGhost() {
        swipeGhost?.let { removeView(it) }
        swipeGhost = null
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
                    manualShift = true
                } else if (capsLock) {
                    capsLock = false
                    shift = false
                    manualShift = false
                } else {
                    shift = true
                    manualShift = true
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
            // Hold to repeat-delete; swipe left for word delete, down for line delete.
            var activePointerId = INVALID_POINTER_ID
            var downX = 0f
            var downY = 0f
            var wordStartX = 0f
            var swipeFired = false
            var lineDeleted = false
            var repeatStarted = false
            var slopPassed = false
            var swipeDir = DIR_NONE // once locked, the gesture keeps its direction
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

            fun cancelRepeat() {
                backspaceRepeatRunnable?.let { handler.removeCallbacks(it) }
                backspaceRepeatRunnable = null
            }

            /** One-time haptic + ghost cue the first time a swipe direction is chosen. */
            fun enterSwipeMode(action: SwipeResolver.Action) {
                isPressed = false
                listener?.onFeedback()
                backspaceKey?.let { showSwipeGhost(it, action) }
            }

            fun endGesture(commit: Boolean) {
                if (activePointerId == INVALID_POINTER_ID) return
                cancelRepeat()
                hideSwipeGhost()
                isPressed = false
                if (commit && !swipeFired && !repeatStarted) {
                    listener?.onBackspace()
                }
                swipeFired = false
                lineDeleted = false
                repeatStarted = false
                slopPassed = false
                swipeDir = DIR_NONE
                activePointerId = INVALID_POINTER_ID
                activeTouchCount = (activeTouchCount - 1).coerceAtLeast(0)
            }

            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (activePointerId == INVALID_POINTER_ID) {
                            activePointerId = ev.getPointerId(0)
                            activeTouchCount++
                        }
                        downX = ev.rawX
                        downY = ev.rawY
                        wordStartX = ev.rawX
                        swipeFired = false
                        lineDeleted = false
                        repeatStarted = false
                        slopPassed = false
                        swipeDir = DIR_NONE
                        isPressed = true
                        cancelRepeat()
                        hideSwipeGhost()
                        val r: Runnable = object : Runnable {
                            override fun run() {
                                repeatStarted = true
                                listener?.onBackspace()
                                handler.postDelayed(
                                    this,
                                    Prefs.backspaceRepeatMs(context).toLong().coerceAtLeast(16L)
                                )
                            }
                        }
                        backspaceRepeatRunnable = r
                        handler.postDelayed(r, Prefs.longPressMs(context).toLong())
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> true // secondary pointer: ignore
                    MotionEvent.ACTION_MOVE -> {
                        val p = ev.rawOf(activePointerId) ?: return@setOnTouchListener true
                        // Suppress hold-repeat at the FIRST movement past touch slop,
                        // not only once a full swipe threshold has been reached.
                        if (!slopPassed) {
                            val mdx = p.first - downX
                            val mdy = p.second - downY
                            if (mdx * mdx + mdy * mdy > touchSlop * touchSlop) {
                                slopPassed = true
                                cancelRepeat()
                            }
                        }
                        if (Prefs.swipeDeleteEnabled(context)) {
                            val threshold = Prefs.swipeStepDp(context).toFloat()
                            val dxDp = pxToDp(p.first - downX)
                            val dyDp = pxToDp(p.second - downY) // Android: positive = down
                            // classify() treats negative dy as "downward".
                            val base = SwipeResolver.classify(dxDp, -dyDp, threshold)
                            // Line-delete requires a dominant, angled-down swipe. Once a
                            // direction is locked, the other is ignored (hysteresis) so a
                            // drifting word-swipe can never become a line-delete.
                            val action = when {
                                swipeDir == DIR_LINE ->
                                    if (base == SwipeResolver.Action.DELETE_LINE &&
                                        GestureResolver.isLineDelete(dxDp, dyDp, threshold)
                                    ) SwipeResolver.Action.DELETE_LINE
                                    else SwipeResolver.Action.NONE
                                swipeDir == DIR_WORD ->
                                    if (base == SwipeResolver.Action.DELETE_WORD)
                                        SwipeResolver.Action.DELETE_WORD
                                    else SwipeResolver.Action.NONE
                                base == SwipeResolver.Action.DELETE_LINE ->
                                    if (GestureResolver.isLineDelete(dxDp, dyDp, threshold))
                                        SwipeResolver.Action.DELETE_LINE
                                    else SwipeResolver.Action.NONE
                                else -> base
                            }
                            when (action) {
                                SwipeResolver.Action.DELETE_LINE -> {
                                    if (swipeDir == DIR_NONE) {
                                        swipeDir = DIR_LINE
                                        enterSwipeMode(SwipeResolver.Action.DELETE_LINE)
                                    }
                                    cancelRepeat()
                                    if (!lineDeleted) {
                                        listener?.onDeleteToLineStart()
                                        lineDeleted = true
                                        swipeFired = true
                                    }
                                }
                                SwipeResolver.Action.DELETE_WORD -> {
                                    if (swipeDir == DIR_NONE) {
                                        swipeDir = DIR_WORD
                                        enterSwipeMode(SwipeResolver.Action.DELETE_WORD)
                                    }
                                    val wordDxDp = pxToDp(p.first - wordStartX)
                                    if (wordDxDp < 0f) {
                                        val steps = SwipeResolver.stepsFor(wordDxDp, threshold)
                                        if (steps > 0) {
                                            if (!swipeFired) {
                                                cancelRepeat()
                                                swipeFired = true
                                            }
                                            repeat(steps) { listener?.onDeleteWord() }
                                            wordStartX = p.first
                                        }
                                    }
                                }
                                SwipeResolver.Action.NONE -> Unit
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (ev.getPointerId(ev.actionIndex) == activePointerId) endGesture(commit = true)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        endGesture(commit = ev.getPointerId(ev.actionIndex) == activePointerId)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        endGesture(commit = false)
                        true
                    }
                    else -> false
                }
            }
        }
        keys.add(backspace)
        backspaceKey = backspace
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
        numberRowManual = true
        numberRowVisible = !numberRowVisible
        numberRow?.visibility = if (numberRowVisible) VISIBLE else GONE
        return numberRowVisible
    }

    /**
     * Sets the shift state from auto-capitalize. A manual shift/caps choice is
     * never overwritten, but labels are always refreshed so the glyph cannot go
     * stale (e.g. after a previous `setShift(false)`).
     */
    fun setShift(value: Boolean) {
        if (!manualShift && !capsLock) shift = value
        relabelAll()
    }

    /** True while either shift or caps lock is engaged. */
    fun isShiftActive(): Boolean = shift || capsLock

    /** True when the user explicitly chose shift/caps (auto-cap must back off). */
    fun isShiftManual(): Boolean = manualShift || capsLock

    /**
     * Enables/disables digit-key candidate selection (1-5 -> candidate 0-4).
     * While a Bengali composition is live the number row appears automatically so
     * the feature is actually reachable; a user who manually toggled the row keeps
     * full control.
     */
    fun setCandidateSelectionActive(active: Boolean) {
        candidateSelectActive = active
        if (!Prefs.numberRowAlways(context) && !numberRowManual) {
            numberRowVisible = active
            numberRow?.visibility = if (active) VISIBLE else GONE
        }
    }

    /** Sets the editor action the Enter key should perform and relabels it. */
    fun setEnterAction(action: Int) {
        enterAction = action
        relabelAll()
    }

    private fun enterLabel(): String = when (enterAction) {
        EditorInfo.IME_ACTION_SEARCH -> "🔍"
        EditorInfo.IME_ACTION_SEND -> "➤"
        EditorInfo.IME_ACTION_NEXT -> "⇥"
        EditorInfo.IME_ACTION_DONE -> "✓"
        EditorInfo.IME_ACTION_GO -> "➤"
        EditorInfo.IME_ACTION_PREVIOUS -> "⇤"
        else -> "⏎"
    }

    /**
     * True while a gesture is in progress: a pointer is down, a popup is showing,
     * a glide is active, or a timed long-press/repeat callback is pending.
     */
    fun isGestureActive(): Boolean =
        activeTouchCount > 0 || popup != null || spaceGlideActive ||
            pendingLongPress != null || navLongPressRunnable != null ||
            backspaceRepeatRunnable != null || edgeRepeatRunnable != null

    fun relabelAll() {
        val shifted = shift || capsLock
        for (key in keys) {
            val tag = key.tag as? String ?: continue
            key.hasAltGr = false
            when (tag) {
                "shift" -> {
                    // A distinct glyph for caps lock so the two states are
                    // never confused (⇧ = shift, ⇪ = caps lock).
                    key.text = if (capsLock) "⇪" else "⇧"
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
                "enter" -> key.text = enterLabel()
                "backspace", "emoji" -> Unit // static labels
                else -> {
                    key.text = labelFor(tag, shifted)
                    // A small superscript dot marks keys that hide an AltGr layer
                    // behind long-press, so alternates are self-evident.
                    key.hasAltGr = mode == KeyboardMode.PROBHAT &&
                        labels[tag]?.a?.isNotBlank() == true
                }
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

        /** Draw a small AltGr dot in the top-right corner when this key has an alternate. */
        var hasAltGr: Boolean = false
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }

        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!hasAltGr) return
            badgePaint.color = pal.accent
            val r = dp(2).toFloat().coerceAtLeast(1.5f)
            val pad = dp(3).toFloat()
            canvas.drawCircle(width - pad - r, pad + r, r, badgePaint)
        }

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
            val (normal, pressed) = when (kind) {
                OsmiumKeyKind.ACCENT -> pal.accent to ColorMath.blend(pal.accent, pal.keyText, 0.2f)
                OsmiumKeyKind.ACTION -> pal.keyAction to pal.keyPressed
                else -> pal.keyNormal to pal.keyPressed
            }
            // A ripple on top of the pressed colour gives tactile feedback without
            // needing a Material dependency; the rounded drawable doubles as mask.
            val ripple = ColorMath.blend(
                pressed,
                ColorMath.WHITE,
                if (ColorMath.isDark(pressed)) 0.25f else 0f,
            )
            background = RippleDrawable(
                ColorStateList.valueOf(ripple),
                stateDrawable(normal, pressed),
                round(normal),
            )
            stateListAnimator = pressAnimator()
            setTextColor(if (kind == OsmiumKeyKind.ACTION) pal.keyTextHint else ColorMath.onColor(normal))
        }

        private fun round(color: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

        private fun stateDrawable(normal: Int, pressed: Int): StateListDrawable {
            val list = StateListDrawable()
            list.addState(intArrayOf(android.R.attr.state_pressed), round(pressed))
            list.addState(StateSet.NOTHING, round(normal))
            return list
        }

        /** Small scale/elevation "key pop" on press, with a matching release. */
        private fun pressAnimator(): StateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), keyAnimation(0.94f, dp(2).toFloat()))
            addState(StateSet.NOTHING, keyAnimation(1f, 0f))
        }

        private fun keyAnimation(scale: Float, elevation: Float): AnimatorSet =
            AnimatorSet().apply {
                duration = 90
                playTogether(
                    ObjectAnimator.ofFloat(this@OsmiumKey, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(this@OsmiumKey, View.SCALE_Y, scale),
                    ObjectAnimator.ofFloat(this@OsmiumKey, View.TRANSLATION_Z, elevation),
                )
            }
    }
}

/** Visual role of a key. */
enum class OsmiumKeyKind { CHAR, ACTION, ACCENT }

/**
 * Shared label parsing for keyboard layouts: works for the bundled
 * probhat-labels.json AND for full OpenBangla layout files (Probhat.json, or
 * any user-installed one), which use Key_X_Normal / Key_X_Shift / Key_X_AltGr.
 */
object LayoutLabels {

    /** (normal, shifted, altGr) labels for one physical key. */
    data class Entry(val n: String, val s: String, val a: String?)

    /**
     * Physical char -> Android `Key_<Name>_*` suffix for punctuation. This is the
     * complete set used by OpenBangla layouts (Probhat has 32 such names); an
     * incomplete map silently drops alternates such as Key_Ampersand_AltGr.
     */
    val NAMES: Map<Char, String> = mapOf(
        ',' to "Comma", '.' to "Period", ';' to "Semicolon", ':' to "Colon",
        '\'' to "Apostrophe", '"' to "Quote", '!' to "Exclaim", '?' to "Question",
        '-' to "Minus", '/' to "Slash", '\\' to "BackSlash", '=' to "Equals",
        '+' to "Plus", '@' to "At", '#' to "Hash", '$' to "Dollar",
        '%' to "Percent", '^' to "Circum", '&' to "Ampersand", '*' to "Asterisk",
        '(' to "ParenLeft", ')' to "ParenRight", '[' to "BracketLeft", ']' to "BracketRight",
        '{' to "BraceLeft", '}' to "BraceRight", '|' to "Bar", '<' to "Less",
        '>' to "Greater", '~' to "Tilde", '`' to "Grave", '_' to "UnderScore"
    )

    /** Every physical key char [fromLayoutJson] understands. */
    val KEYS: List<Char> = "qwertyuiopasdfghjklzxcvbnm0123456789".toList() + NAMES.keys

    /** Android `Key_<Name>` -> physical key char (inverse of [NAMES] for punctuation). */
    fun codeFor(name: String): String? = when {
        name.length == 1 -> name
        else -> NAMES.entries.firstOrNull { it.value == name }?.key?.toString()
    }

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

        // OpenBangla layout format: { layout: { Key_q_Normal: "দ", Key_q_AltGr: … } }.
        // Full layout files (Probhat.json and user imports) nest the Key_* entries
        // under "layout"; accept a flat object too for compatibility.
        val layout = root.optJSONObject("layout") ?: root
        for (c in KEYS) {
            val name = if (c.isLetterOrDigit()) c.toString() else NAMES[c] ?: continue
            val normal = layout.optString("Key_${name}_Normal", "")
            if (normal.isEmpty()) continue
            val shifted = layout.optString("Key_${name.uppercase()}_Normal", "")
            val altGr = layout.optString("Key_${name}_AltGr", "")
            addLabel(c.toString(), normal, shifted.ifEmpty { normal }, altGr)
        }
        return map
    }
}
