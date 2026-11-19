package org.osmium.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import uniffi.osmium.OsSuggestion
import java.io.File

/**
 * Osmium IME — the heart of the keyboard.
 *
 * Input flow (Bangla modes):
 *   key tap → riti keycode → riti suggestion → composing text + suggestion bar
 *   space / tap  → candidate committed, riti session ends
 */
class OsmiumImeService : InputMethodService(), KeyboardView.Listener {

    /** riti MODIFIER_ALT_GR (see riti::context). */
    private companion object {
        const val MODIFIER_ALT_GR = 2
    }

    private lateinit var engine: RitiEngine
    private var keyboardView: KeyboardView? = null
    private var suggestionBar: SuggestionBar? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipboardPanel: ClipboardPanel? = null
    private var navigationPanel: NavigationPanel? = null

    private var mode = KeyboardMode.AVRO
    private var latest = RitiEngine.EMPTY
    private var incognito = false
    /** True when the current editor is a password field (privacy-sensitive UI). */
    private var currentFieldIsPassword = false
    /** Fingerprint of the settings baked into the current input view. */
    private var appliedSettingsKey: String? = null
    /** True while the error/retry view is installed, so a new field can recover. */
    private var errorShowing = false
    /** Track orientation to rebuild input view on rotation. */
    private var appliedOrientation = -1
    /** Input type / editor action of the field currently being edited. */
    private var currentInputType = 0
    private var currentEnterAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    /** State for double-space → দাঁড়ি (।). */
    private var lastSpaceAt = 0L
    private var lastWasSpace = false
    /** Last clipboard text seen, for change detection. */
    private var lastClip = ""

    /** Single owner of which panel is open; closing always resets transient state. */
    private enum class PanelState { NONE, EMOJI, CLIPBOARD, NAV }
    private var panelState = PanelState.NONE

    /** Text snapshots backing the undo/redo fallback when the app has no native undo. */
    private var undoSnapshot: String? = null
    private var redoSnapshot: String? = null

    override fun onCreate() {
        super.onCreate()
        engine = RitiEngine(this)
        mode = when (Prefs.banglaScheme(this)) {
            "probhat" -> KeyboardMode.PROBHAT
            else -> KeyboardMode.AVRO
        }
        // Initialise the engine for the saved mode (was missing in v0.1).
        layoutFor(mode)?.let { engine.useLayout(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Never let incognito-session learning survive the IME process.
        engine.clearScratch()
    }

    // ------------------------------------------------------------------
    // View setup
    // ------------------------------------------------------------------

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        return try {
            buildInputView()
        } catch (t: Throwable) {
            // Never leave the user with a blank keyboard — show a recoverable error.
            errorShowing = true
            errorView(t)
        }
    }

    /** Replaces the input view with a short message and a working Retry action. */
    private fun showError(t: Throwable) {
        errorShowing = true
        setInputView(errorView(t))
    }

    private fun errorView(t: Throwable): View {
        val detail = t.message?.take(160) ?: t.javaClass.simpleName
        val message = android.widget.TextView(this).apply {
            text = "Osmium hit a problem. Your text is safe.\n\n$detail"
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            textSize = 14f
        }
        val retry = android.widget.Button(this).apply {
            text = "Retry — rebuild keyboard"
            setOnClickListener { retryFromError() }
        }
        return android.widget.ScrollView(this).apply {
            addView(LinearLayout(this@OsmiumImeService).apply {
                orientation = LinearLayout.VERTICAL
                addView(message)
                addView(retry)
            })
        }
    }

    /**
     * Escape hatch out of the error view: drop any half-finished composition,
     * force an engine rebuild (which clears a stale [RitiEngine.initError] on
     * success) and put a fresh input view back. If the engine is still broken,
     * show the error again instead of returning to a dead end.
     */
    private fun retryFromError() {
        try {
            engine.finishSession()
            latest = RitiEngine.EMPTY
            suggestionBar?.clear()
            val layout = layoutFor(mode)
            if (layout != null) engine.updateEngine(layout)
            if (layout != null && engine.initError != null) {
                showError(IllegalStateException(engine.initError))
                return
            }
            setInputView(buildInputView())
        } catch (t: Throwable) {
            showError(t)
        }
    }

    private fun buildInputView(): View {
        appliedSettingsKey = Settings.key(this)
        errorShowing = false
        appliedOrientation = resources.configuration.orientation
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        suggestionBar = SuggestionBar(this).apply {
            setModeLabel(mode, selectedLayoutName())
            onSuggestionTap = { index -> onSuggestion(index) }
            onLanguageToggle = { cycleMode() }
            onNumberRowToggle = { keyboardView?.toggleNumberRow() }
            onSettingsTap = { launchSetup() }
            onClipboardToggle = { toggleClipboardPanel() }
            onNavToggle = { onNavigationToggle() }
        }

        keyboardView = KeyboardView(this).apply {
            this.mode = this@OsmiumImeService.mode
            if (this@OsmiumImeService.mode == KeyboardMode.PROBHAT) {
                userLayoutPath = selectedLayoutPath()
            }
            listener = this@OsmiumImeService
        }

        emojiPanel = EmojiPanel(this).apply {
            visibility = View.GONE
            onEmojiPicked = { emoji ->
                insertTextSafely(emoji)
                haptic()
            }
        }

        clipboardPanel = ClipboardPanel(this).apply {
            visibility = View.GONE
            onPaste = { text ->
                insertTextSafely(text)
                haptic()
            }
        }

        navigationPanel = NavigationPanel(this).apply {
            visibility = View.GONE
            onAction = { action -> onNavAction(action) }
        }

        val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // The keyboard is hidden while a panel is open, so panels can use more
        // of the landscape height without squeezing the emoji grid to ~24dp.
        val panelHeightDp = if (isLandscape) 200 else 230
        container.addView(
            suggestionBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(46)
            )
        )
        container.addView(
            emojiPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(panelHeightDp))
        )
        container.addView(
            clipboardPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(panelHeightDp))
        )
        container.addView(
            navigationPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(panelHeightDp))
        )
        container.addView(
            keyboardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return container
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // A scheme chosen in the Settings screen must take effect without a
        // process restart. Reconcile it before rebuilding so the fresh view is
        // labelled and laid out for the new mode.
        reconcileScheme()
        // One unified signal covers every view-affecting preference (theme,
        // timings, haptics, glide, scheme, ...). Also recover automatically if
        // the error/retry view was showing when this field gained focus.
        val currentOrientation = resources.configuration.orientation
        if (errorShowing || Settings.key(this) != appliedSettingsKey ||
            currentOrientation != appliedOrientation
        ) {
            appliedOrientation = currentOrientation
            setInputView(onCreateInputView())
        }
        // Determine incognito state: user preference OR field privacy flags.
        val noLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        val isPassword = isPasswordField(info)
        currentFieldIsPassword = isPassword
        currentInputType = info.inputType
        currentEnterAction = if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            EditorInfo.IME_ACTION_NONE
        } else {
            info.imeOptions and EditorInfo.IME_MASK_ACTION
        }
        keyboardView?.setEnterAction(currentEnterAction)
        incognito = Prefs.incognitoByDefault(this) || noLearning || isPassword
        engine.setIncognito(incognito)
        suggestionBar?.setIncognito(incognito)
        suggestionBar?.setClipboardEnabled(!incognito)
        // On a restart of the same field, commit what was being composed before
        // dropping the session; for a different field, finishing is correct.
        if (restarting && engine.isSessionActive()) commitCurrentCandidate()
        engine.finishSession()
        latest = RitiEngine.EMPTY
        suggestionBar?.clear()
        keyboardView?.setCandidateSelectionActive(false)
        setPanelState(PanelState.NONE)
        resetDoubleSpace()
        autoCapitalizeIfNeeded()
        captureClipboard()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        captureClipboard()
    }

    /** Hardware DPAD up/down cycles the highlighted candidate during a session. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) &&
            engine.isSessionActive() && latest.candidates.isNotEmpty()
        ) {
            cycleCandidate(if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Reads the first text item on the clipboard, or null when unavailable. */
    private fun currentClipboardText(): String? {
        return try {
            val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return null
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    /** Records newly copied text into the local clipboard history. */
    private fun captureClipboard() {
        val text = currentClipboardText() ?: return
        if (incognito) {
            // Never persist clipboard content while incognito. Still remember
            // it so leaving incognito cannot retroactively backfill text that
            // was copied during the incognito session.
            lastClip = text
            return
        }
        if (text.isNotBlank() && text != lastClip && text.length <= 4000) {
            lastClip = text
            Prefs.addClipboardItem(this, text)
            clipboardPanel?.refresh()
        }
    }

    /** Clears the double-space → দাঁড়ি tracking state. */
    fun resetDoubleSpace() {
        lastWasSpace = false
        lastSpaceAt = 0L
    }

    /**
     * Single owner of panel visibility. Every transition to [PanelState.NONE]
     * clears transient UI state (emoji search query/focus, navigation repeat
     * timers) so no panel can reopen in a stale state.
     */
    private fun setPanelState(state: PanelState) {
        if (state == PanelState.NONE && panelState != PanelState.NONE) {
            emojiPanel?.clearSearch()
            navigationPanel?.clearTimers()
        }
        panelState = state
        emojiPanel?.visibility = if (state == PanelState.EMOJI) View.VISIBLE else View.GONE
        clipboardPanel?.visibility = if (state == PanelState.CLIPBOARD) View.VISIBLE else View.GONE
        navigationPanel?.visibility = if (state == PanelState.NAV) View.VISIBLE else View.GONE
        keyboardView?.visibility = if (state == PanelState.NONE) View.VISIBLE else View.GONE
    }

    /** True while the emoji search field is open and should receive on-screen keys. */
    private fun emojiSearchActive(): Boolean =
        panelState == PanelState.EMOJI && emojiPanel?.isSearchFocused() == true

    private fun toggleClipboardPanel() = guarded {
        haptic()
        val clip = clipboardPanel ?: return@guarded
        val opening = panelState != PanelState.CLIPBOARD
        if (opening) clip.refresh()
        setPanelState(if (opening) PanelState.CLIPBOARD else PanelState.NONE)
    }

    private fun isPasswordField(info: EditorInfo): Boolean {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return cls == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
    }

    // ------------------------------------------------------------------
    // Key handling (KeyboardView.Listener) — all guarded so a bug can never
    // kill the keyboard; errors are shown on the keyboard itself instead.
    // ------------------------------------------------------------------

    private fun guarded(body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            showError(t)
        }
    }

    override fun onKey(code: String, shifted: Boolean, altgr: Boolean) = guarded {
        haptic()
        val char = if (shifted) code.uppercase() else code
        // Touch-only devices: type into the emoji search box while it is focused,
        // instead of committing the character to the app's text field.
        if (emojiSearchActive()) {
            emojiPanel?.appendSearch(char)
            return@guarded
        }
        val modifier = if (altgr) MODIFIER_ALT_GR else 0
        when (mode) {
            KeyboardMode.ENGLISH -> {
                currentInputConnection?.commitText(char, 1)
                lastWasSpace = false
                autoCapitalizeIfNeeded()
            }
            KeyboardMode.AVRO -> sendToRiti(char, modifier)
            KeyboardMode.PROBHAT -> sendToRiti(char, modifier)
        }
    }

    override fun onCandidateSelect(index: Int) = guarded {
        haptic()
        if (!engine.isSessionActive()) return@guarded
        val current = latest
        if (index !in current.candidates.indices || index == current.selection) return@guarded
        // Keep the riti state, original-index map and auxiliary buffer intact;
        // only the displayed selection changes. Re-render swaps the composing
        // text to the newly picked candidate and highlights it in the bar.
        latest = OsSuggestion(
            auxiliary = current.auxiliary,
            candidates = current.candidates,
            selection = index,
            isEmpty = current.isEmpty,
            originalIndices = current.originalIndices,
        )
        render(latest)
    }

    /** Cycle the highlighted candidate (hardware DPAD up/down). */
    private fun cycleCandidate(delta: Int) = guarded {
        if (!engine.isSessionActive()) return@guarded
        val count = latest.candidates.size
        if (count == 0) return@guarded
        onCandidateSelect((latest.selection + delta + count) % count)
    }

    override fun onFeedback() = guarded { haptic() }

    override fun onShiftTap() = guarded { haptic() }

    override fun onSpace() = guarded {
        haptic()
        if (emojiSearchActive()) {
            emojiPanel?.appendSearch(" ")
            return@guarded
        }
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) {
            commitCurrentCandidate()
            lastWasSpace = false
            lastSpaceAt = 0L
            ic.commitText(" ", 1)
            return@guarded
        }
        // Double-space → দাঁড়ি (।): second space soon after the first one
        // replaces that space with the Bangla full stop.
        val now = android.os.SystemClock.uptimeMillis()
        // Verify the character before the cursor really is our space: a
        // backspace, a committed candidate or a cursor move may have changed
        // the field since. দাঁড়ি belongs to Bengali modes only.
        if (Prefs.doubleSpaceDari(this) && mode != KeyboardMode.ENGLISH &&
            lastWasSpace && now - lastSpaceAt < 400 &&
            ic.getTextBeforeCursor(1, 0)?.toString() == " "
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("।", 1)
            lastWasSpace = false
            lastSpaceAt = 0L
        } else {
            ic.commitText(" ", 1)
            lastWasSpace = true
            lastSpaceAt = now
        }
        autoCapitalizeIfNeeded()
    }

    override fun onCursorGlide(delta: Int) = guarded {
        resetDoubleSpace()
        val ic = currentInputConnection ?: return@guarded
        val pos = cursorPosition(ic) ?: return@guarded
        val target = (pos + delta).coerceAtLeast(0)
        ic.setSelection(target, target)
    }

    /** Current cursor position via extracted text; null when unavailable. */
    private fun cursorPosition(ic: InputConnection): Int? {
        val et = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            ?: return null
        return et.startOffset + et.selectionStart
    }

    /**
     * English-only auto-cap at a word boundary that follows a sentence break
     * (or the start of the field). A manual shift/caps choice always wins.
     */
    private fun autoCapitalizeIfNeeded() {
        if (!Prefs.autoCapitalize(this) || mode != KeyboardMode.ENGLISH) return
        if (keyboardView?.isShiftManual() == true || autoCapSuppressedField()) return
        val ic = currentInputConnection ?: return
        // Trim spaces/tabs only: a trailing newline is itself a boundary.
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty().trimEnd(' ', '\t')
        val atSentenceStart = before.isEmpty() || before.last() in ".!?।\n\r"
        keyboardView?.setShift(atSentenceStart)
    }

    /** Password, URL/web, e-mail and numeric/phone fields never get auto-cap. */
    private fun autoCapSuppressedField(): Boolean {
        if (currentFieldIsPassword) return true
        val cls = currentInputType and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        ) return true
        val variation = currentInputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    }

    override fun onBackspace() = guarded {
        haptic()
        resetDoubleSpace()
        if (emojiSearchActive()) {
            emojiPanel?.backspaceSearch()
            return@guarded
        }
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) {
            // Riti owns the composition; let it recompute.
            val s = engine.backspace(ctrl = false)
            latest = s
            render(s)
        } else {
            val et = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            if (et != null && et.selectionStart != et.selectionEnd) {
                // Delete the selection.
                ic.commitText("", 1)
            } else {
                deleteOneCodepoint(ic)
            }
        }
    }

    override fun onDeleteWord() = guarded {
        haptic()
        resetDoubleSpace()
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) commitCurrentCandidate()
        val before = ic.getTextBeforeCursor(5000, 0)?.toString().orEmpty()
        val count = TextNav.previousWordStart(before)
        if (count > 0) ic.deleteSurroundingText(count, 0)
    }

    override fun onDeleteToLineStart() = guarded {
        haptic()
        resetDoubleSpace()
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) commitCurrentCandidate()
        val before = ic.getTextBeforeCursor(5000, 0)?.toString().orEmpty()
        val count = TextNav.lineStartLength(before)
        if (count > 0) ic.deleteSurroundingText(count, 0)
    }

    // ------------------------------------------------------------------
    // Navigation / editing panel actions
    // ------------------------------------------------------------------

    /** Dispatches a single action raised by [NavigationPanel]. */
    private fun onNavAction(action: NavAction) = guarded {
        haptic()
        resetDoubleSpace()
        val ic = currentInputConnection ?: return@guarded
        // Never navigate or edit in the middle of a riti composition.
        if (engine.isSessionActive()) commitCurrentCandidate()

        when (action) {
            NavAction.HOME, NavAction.LEFT, NavAction.UP, NavAction.DOWN,
            NavAction.RIGHT, NavAction.END, NavAction.DOC_START, NavAction.DOC_END -> {
                sendMovementKey(ic, action)
            }
            NavAction.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
                captureClipboard()
            }
            NavAction.CUT -> {
                snapshotForUndo(ic)
                ic.performContextMenuAction(android.R.id.cut)
                hideNavigationPanel() // text changed
            }
            NavAction.PASTE -> {
                // Funnel pasted text through the same composition-safe path as
                // the emoji/clipboard panels. Any active riti session was
                // already committed by the guard above, so commitText here
                // cannot overwrite a live composing span.
                val text = currentClipboardText()
                if (text != null) {
                    insertTextSafely(text)
                } else {
                    ic.performContextMenuAction(android.R.id.paste)
                }
                hideNavigationPanel() // text changed
            }
            NavAction.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            NavAction.SELECT_WORD -> selectWordAtCursor(ic)
            NavAction.UNDO -> {
                if (!ic.performContextMenuAction(android.R.id.undo)) undoFromSnapshot(ic)
            }
            NavAction.REDO -> {
                if (!ic.performContextMenuAction(android.R.id.redo)) redoFromSnapshot(ic)
            }
        }
    }

    /** Best-effort full field text, used only for the undo/redo snapshot fallback. */
    private fun fullText(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(20_000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(20_000, 0)?.toString().orEmpty()
        return before + after
    }

    /** Records the pre-edit text so a field without native undo can still revert. */
    private fun snapshotForUndo(ic: InputConnection) {
        undoSnapshot = fullText(ic)
        redoSnapshot = null
    }

    private fun undoFromSnapshot(ic: InputConnection) {
        val snap = undoSnapshot ?: return
        redoSnapshot = fullText(ic)
        replaceAllText(ic, snap)
        undoSnapshot = null
    }

    private fun redoFromSnapshot(ic: InputConnection) {
        val snap = redoSnapshot ?: return
        undoSnapshot = fullText(ic)
        replaceAllText(ic, snap)
        redoSnapshot = null
    }

    /** Replaces the whole editor contents in a single batch edit. */
    private fun replaceAllText(ic: InputConnection, text: String) {
        ic.beginBatchEdit()
        try {
            ic.performContextMenuAction(android.R.id.selectAll)
            ic.commitText(text, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun hideNavigationPanel() {
        // Restore the keyboard too — with a panel open it is GONE.
        setPanelState(PanelState.NONE)
    }

    /**
     * Sends DOWN+UP key events for a movement, then verifies the cursor moved.
     * Fields that ignore DPAD/MOVE keys are handled with an explicit selection.
     */
    private fun sendMovementKey(ic: InputConnection, action: NavAction) {
        val shiftActive = keyboardView?.isShiftActive() == true
        val keyCode: Int
        var meta = 0
        when (action) {
            NavAction.LEFT -> keyCode = KeyEvent.KEYCODE_DPAD_LEFT
            NavAction.RIGHT -> keyCode = KeyEvent.KEYCODE_DPAD_RIGHT
            NavAction.UP -> keyCode = KeyEvent.KEYCODE_DPAD_UP
            NavAction.DOWN -> keyCode = KeyEvent.KEYCODE_DPAD_DOWN
            NavAction.HOME -> keyCode = KeyEvent.KEYCODE_MOVE_HOME
            NavAction.END -> keyCode = KeyEvent.KEYCODE_MOVE_END
            NavAction.DOC_START -> {
                keyCode = KeyEvent.KEYCODE_MOVE_HOME
                meta = KeyEvent.META_CTRL_ON
            }
            NavAction.DOC_END -> {
                keyCode = KeyEvent.KEYCODE_MOVE_END
                meta = KeyEvent.META_CTRL_ON
            }
            else -> return
        }
        // Shift+arrow extends the selection (Document start/end use plain CTRL).
        if (shiftActive && (action == NavAction.LEFT || action == NavAction.RIGHT)) {
            meta = meta or KeyEvent.META_SHIFT_ON
        }

        val before = cursorPosition(ic)
        ic.sendKeyEvent(movementKeyEvent(KeyEvent.ACTION_DOWN, keyCode, meta))
        ic.sendKeyEvent(movementKeyEvent(KeyEvent.ACTION_UP, keyCode, meta))
        val after = cursorPosition(ic)
        if (before != null && after == before) {
            // Mirror the key-event meta logic: only LEFT/RIGHT extend a selection.
            val extendSelection = shiftActive &&
                (action == NavAction.LEFT || action == NavAction.RIGHT)
            fallbackSelection(ic, action, before, extendSelection)
        }
    }

    private fun movementKeyEvent(action: Int, keyCode: Int, meta: Int): KeyEvent {
        val now = android.os.SystemClock.uptimeMillis()
        return KeyEvent(now, now, action, keyCode, meta)
    }

    /** setSelection fallback computed from extracted text for fields that ignore key events. */
    private fun fallbackSelection(
        ic: InputConnection, action: NavAction, cursor: Int, extend: Boolean
    ) {
        val et = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        val s = et.text?.toString() ?: return
        val base = et.startOffset
        val rel = et.selectionStart.coerceIn(0, s.length)
        val anchor = cursor // absolute cursor read before the (ignored) key events
        val target = when (action) {
            NavAction.LEFT ->
                if (rel > 0) anchor - Character.charCount(s.codePointBefore(rel)) else return
            NavAction.RIGHT ->
                if (rel < s.length) anchor + Character.charCount(s.codePointAt(rel)) else return
            NavAction.HOME -> base + lineStartAt(s, rel)
            NavAction.END -> {
                val nl = s.indexOf('\n', rel)
                base + if (nl < 0) s.length else nl
            }
            NavAction.DOC_START -> base
            NavAction.DOC_END -> base + s.length
            NavAction.UP -> {
                val ls = lineStartAt(s, rel)
                val prev = s.lastIndexOf('\n', ls - 2)
                if (prev < 0) base else base + prev + 1
            }
            NavAction.DOWN -> {
                val nl = s.indexOf('\n', rel)
                if (nl < 0) base + s.length else base + nl + 1
            }
            else -> return
        }
        if (extend) {
            ic.setSelection(minOf(anchor, target), maxOf(anchor, target))
        } else {
            ic.setSelection(target, target)
        }
    }

    /** Start index of the line containing [pos] within [s]. */
    private fun lineStartAt(s: String, pos: Int): Int {
        if (pos <= 0) return 0
        val nl = s.lastIndexOf('\n', pos - 1)
        return if (nl < 0) 0 else nl + 1
    }

    /** Selects the single word around the cursor using extracted text. */
    private fun selectWordAtCursor(ic: InputConnection) {
        val et = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        val s = et.text?.toString() ?: return
        val rel = et.selectionStart.coerceIn(0, s.length)
        val range = TextNav.wordRangeAroundBangla(s, rel)
        // Extracted-text offsets are relative to et.startOffset; setSelection is absolute.
        ic.setSelection(et.startOffset + range.first, et.startOffset + range.last)
    }

    override fun onEnter() = guarded {
        haptic()
        resetDoubleSpace()
        if (emojiSearchActive()) {
            // Done searching: clear the query/focus and hand the keyboard back.
            setPanelState(PanelState.NONE)
            return@guarded
        }
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) {
            commitCurrentCandidate()
        }
        val action = currentEnterAction
        if (action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            // Plain newline (including IME_FLAG_NO_ENTER_ACTION fields).
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } else {
            ic.performEditorAction(action)
        }
    }

    override fun onModeCycle() = guarded {
        haptic()
        cycleMode()
    }

    override fun onEmojiToggle() = guarded {
        haptic()
        val panel = emojiPanel ?: return@guarded
        val opening = panelState != PanelState.EMOJI
        if (opening) {
            panel.applyTheme()
            panel.refresh()
        }
        setPanelState(if (opening) PanelState.EMOJI else PanelState.NONE)
    }

    override fun onNumberRowToggle() = guarded {
        haptic()
        keyboardView?.toggleNumberRow()
    }

    override fun onNavigationToggle() = guarded {
        haptic()
        val nav = navigationPanel ?: return@guarded
        val opening = panelState != PanelState.NAV
        if (opening) nav.refreshTheme()
        setPanelState(if (opening) PanelState.NAV else PanelState.NONE)
    }

    // ------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------

    private fun onSuggestion(index: Int) = guarded {
        val candidate = latest.candidates.getOrNull(index) ?: return@guarded
        val ic = currentInputConnection ?: return@guarded
        resetDoubleSpace()
        // Keep the composing-text swap, commit and state reset atomic so the
        // editor never observes a half-finished candidate commit.
        ic.beginBatchEdit()
        try {
            // Replace the composing region with the picked candidate in one step.
            ic.setComposingText(candidate, 1)
            ic.finishComposingText()
            // `index` is a display index; tell riti its original index instead.
            engine.candidateCommitted(index, latest.originalIndices)
            latest = RitiEngine.EMPTY
            suggestionBar?.clear()
            keyboardView?.setCandidateSelectionActive(false)
        } finally {
            ic.endBatchEdit()
        }
        haptic()
    }

    private fun sendToRiti(char: String, modifier: Int = 0) {
        val s = engine.suggest(char, modifier = modifier, selection = latest.selection)
        if (s.isEmpty && engine.initError != null) {
            // Engine is broken — show why, with a way back instead of a dead end.
            showError(IllegalStateException("riti engine: ${engine.initError}"))
            return
        }
        latest = s
        render(s)
    }

    private fun render(s: OsSuggestion) {
        val ic = currentInputConnection ?: return
        keyboardView?.setCandidateSelectionActive(!s.isEmpty && s.candidates.isNotEmpty())
        if (s.isEmpty) {
            clearComposition(ic)
            suggestionBar?.clear()
            return
        }
        // The composing text is ONLY the selected Bengali candidate — the
        // romanized auxiliary text is shown in the suggestion bar, never
        // typed into the field (same as OpenBangla's IBus engine).
        val composed = s.candidates.getOrNull(s.selection) ?: ""
        if (composed.isEmpty()) {
            clearComposition(ic)
            suggestionBar?.clear()
        } else {
            ic.setComposingText(composed, 1)
            // In an incognito password field, never echo typed characters in
            // the suggestion strip — compose silently instead.
            if (!(incognito && currentFieldIsPassword)) {
                suggestionBar?.update(s)
            }
        }
    }

    /** Commits the currently selected candidate and ends the riti session. */
    private fun commitCurrentCandidate() {
        val ic = currentInputConnection ?: return
        resetDoubleSpace()
        ic.beginBatchEdit()
        try {
            val selected = latest.candidates.getOrNull(latest.selection) ?: ""
            if (selected.isNotEmpty()) {
                // Replace the composing region with the final text in one step,
                // then commit it (finishComposingText on stale text would
                // duplicate it into the field).
                ic.setComposingText(selected, 1)
                ic.finishComposingText()
                // `latest.selection` is a display index; translate before riti.
                engine.candidateCommitted(latest.selection, latest.originalIndices)
            }
            latest = RitiEngine.EMPTY
            suggestionBar?.clear()
            keyboardView?.setCandidateSelectionActive(false)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun clearComposition(ic: InputConnection) {
        ic.beginBatchEdit()
        try {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            latest = RitiEngine.EMPTY
            suggestionBar?.clear()
            keyboardView?.setCandidateSelectionActive(false)
        } finally {
            ic.endBatchEdit()
        }
    }

    /**
     * Commits arbitrary text (emoji / clipboard paste) without leaving a stale
     * riti composition behind. The active candidate is committed first, then
     * the composing span is finished and engine/bar state is reset, so the
     * subsequent [commitText] cannot overwrite a live composing region or
     * desync riti's input session.
     */
    private fun insertTextSafely(text: String) {
        val ic = currentInputConnection ?: return
        snapshotForUndo(ic)
        ic.beginBatchEdit()
        try {
            if (engine.isSessionActive()) commitCurrentCandidate()
            // No composing span may remain when foreign text is inserted.
            ic.finishComposingText()
            latest = RitiEngine.EMPTY
            suggestionBar?.clear()
            if (text.isNotEmpty()) ic.commitText(text, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    /** Codepoint-aware delete (handles surrogate pairs / emoji). */
    private fun deleteOneCodepoint(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val len = if (before.length == 2 && Character.isHighSurrogate(before[0])) 2 else 1
        ic.deleteSurroundingText(len, 0)
    }

    // ------------------------------------------------------------------
    // Mode switching & helpers
    // ------------------------------------------------------------------

    /**
     * Applies a Bangla scheme changed from the Settings activity. The IME reads
     * the scheme once in [onCreate], so without this a change made in Settings
     * was ignored until the process died. English mode is an explicit choice
     * from [cycleMode] and is left alone; the scheme takes effect when the user
     * cycles back into a Bangla mode.
     */
    private fun reconcileScheme() = guarded {
        if (mode == KeyboardMode.ENGLISH) return@guarded
        val desired = when (Prefs.banglaScheme(this)) {
            "probhat" -> KeyboardMode.PROBHAT
            else -> KeyboardMode.AVRO
        }
        if (mode == desired) return@guarded

        // A scheme change must not silently swallow a half-finished word.
        if (engine.isSessionActive()) commitCurrentCandidate()
        engine.finishSession()
        latest = RitiEngine.EMPTY
        resetDoubleSpace()
        keyboardView?.setCandidateSelectionActive(false)
        mode = desired
        layoutFor(mode)?.let { engine.useLayout(it) }
        keyboardView?.apply {
            mode = this@OsmiumImeService.mode
            userLayoutPath = if (mode == KeyboardMode.PROBHAT) selectedLayoutPath() else null
        }
        suggestionBar?.setModeLabel(mode, selectedLayoutName())
        suggestionBar?.clear()
    }

    private fun cycleMode() {
        if (engine.isSessionActive()) commitCurrentCandidate()
        resetDoubleSpace()

        mode = when (mode) {
            KeyboardMode.AVRO -> KeyboardMode.PROBHAT
            KeyboardMode.PROBHAT -> KeyboardMode.ENGLISH
            KeyboardMode.ENGLISH -> KeyboardMode.AVRO
        }

        val layout = layoutFor(mode)
        if (layout != null) {
            engine.useLayout(layout)
            Prefs.setBanglaScheme(
                this,
                if (mode == KeyboardMode.PROBHAT) "probhat" else "avro"
            )
        } else {
            engine.finishSession()
        }

        keyboardView?.apply {
            mode = this@OsmiumImeService.mode
            userLayoutPath = if (mode == KeyboardMode.PROBHAT) selectedLayoutPath() else null
        }
        suggestionBar?.setModeLabel(mode, selectedLayoutName())
        suggestionBar?.clear()
    }

    /** riti layout identifier for a mode, or null for English. */
    private fun layoutFor(mode: KeyboardMode): String? = when (mode) {
        KeyboardMode.AVRO -> "avro_phonetic"
        KeyboardMode.PROBHAT -> {
            RitiEngine.ensureData(this)
            selectedLayoutPath()
        }
        KeyboardMode.ENGLISH -> null
    }

    /** Path to the selected fixed layout file (bundled Probhat or imported). */
    private fun selectedLayoutPath(): String {
        RitiEngine.ensureData(this)
        val name = Prefs.selectedLayout(this)
        val custom = File(Prefs.layoutsDir(this), name)
        return if (name != "probhat" && custom.exists()) custom.absolutePath
        else File(filesDir, "riti/Probhat.json").absolutePath
    }

    /** Selected layout display name, or null for the bundled Probhat. */
    private fun selectedLayoutName(): String? {
        val name = Prefs.selectedLayout(this)
        return if (name == "probhat") null else name.removeSuffix(".json")
    }

    private fun launchSetup() {
        val intent = android.content.Intent(this, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requestHideSelf(0)
    }

    private fun haptic() {
        if (!Prefs.hapticsEnabled(this)) return
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val ms = Prefs.hapticMs(this).coerceIn(1, 60)
        val amp = Prefs.hapticStrength(this).coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms.toLong(), amp))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms.toLong())
        }
    }

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
