package org.osmium.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import uniffi.osmium.OsSuggestion
import java.io.File

/**
 * One consistent read of a text window plus its selection. Both offsets are
 * absolute (relative to the start of the editor's text), so callers never mix
 * the per-call coordinate systems of getTextBeforeCursor/getExtractedText.
 */
internal data class EditorSnapshot(
    val text: String,
    val startOffset: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    /** Selection index relative to [text]. */
    val relativeSelection: Int get() = (selectionStart - startOffset).coerceIn(0, text.length)
}

/**
 * Pure validation/normalisation of one text-window read. Returns null when the
 * offsets are unusable so the caller can fall through to another read source.
 * `TextSnapshot` already reports absolute selection offsets; `ExtractedText`
 * reports them relative to [base].
 */
internal fun editorSnapshotOf(
    text: String?,
    base: Int,
    selectionStart: Int,
    selectionEnd: Int,
    selectionIsAbsolute: Boolean,
): EditorSnapshot? {
    if (base < 0 || selectionStart < 0 || selectionEnd < 0) return null
    val start = if (selectionIsAbsolute) selectionStart else base + selectionStart
    val end = if (selectionIsAbsolute) selectionEnd else base + selectionEnd
    return EditorSnapshot(text.orEmpty(), base, start, end)
}

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
        /** Prefix of the per-subtype mode marker declared in method.xml. */
        const val SUBTYPE_MODE_PREFIX = "Osmium.Mode="
    }

    private lateinit var engine: RitiEngine
    private var keyboardView: KeyboardView? = null
    private var suggestionBar: SuggestionBar? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipboardPanel: ClipboardPanel? = null
    private var navigationPanel: NavigationPanel? = null
    private var numericPanel: NumericPanel? = null

    private var mode = KeyboardMode.AVRO
    private var latest = RitiEngine.EMPTY
    private var incognito = false
    /** True when the current editor is a password field (privacy-sensitive UI). */
    private var currentFieldIsPassword = false
    /** Password/number/email/URI fields must receive literal ASCII, not Bengali. */
    private var currentFieldAsciiOnly = false
    /**
     * What the IME itself has committed recently. The editor's getTextBeforeCursor
     * can lag behind commitText() during fast typing, which made auto-capitalize
     * think every key started a new sentence. This local buffer is authoritative.
     */
    private val recentCommitted = ArrayDeque<Char>()
    /** Last string we put in the composing span; avoids a redundant editor round-trip. */
    private var lastComposed: String? = null

    /**
     * Editor compatibility: some chat composers (notably WhatsApp) clear or ignore
     * composing spans. When detected, this field switches to direct-insert mode.
     */
    private var directCommitMode = false
    /** Length of the text we inserted directly for the current word. */
    private var directCommitLength = 0
    /** Whether the composing-span probe has run for the current field. */
    private var composingProbeDone = false

    private fun noteCommitted(text: String) {
        for (c in text) {
            recentCommitted.addLast(c)
            while (recentCommitted.size > 64) recentCommitted.removeFirst()
        }
    }
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
    private enum class PanelState { NONE, EMOJI, CLIPBOARD, NAV, NUMERIC }
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
        // The system subtype, when reported via onCurrentInputMethodSubtypeChanged,
        // overrides this initial choice (English / Probhat / Avro).
        layoutFor(mode)?.let { engine.useLayout(it) }
    }

    override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(subtype)
        guarded { applySubtypeMode(subtype, rebuildView = true) }
    }

    /**
     * Maps a system subtype's extra value (written in method.xml) onto the
     * internal keyboard mode. Bangla modes are persisted so the Settings screen
     * and `reconcileScheme()` stay in sync; English is a transient choice, as
     * in `cycleMode()`.
     */
    private fun applySubtypeMode(subtype: InputMethodSubtype?, rebuildView: Boolean) {
        if (!::engine.isInitialized) return
        val extra = subtype?.extraValue.orEmpty()
        val desired = when {
            extra.contains("${SUBTYPE_MODE_PREFIX}english") -> KeyboardMode.ENGLISH
            extra.contains("${SUBTYPE_MODE_PREFIX}probhat") -> KeyboardMode.PROBHAT
            extra.contains("${SUBTYPE_MODE_PREFIX}avro") -> KeyboardMode.AVRO
            else -> return
        }
        if (desired == mode) return
        switchToMode(desired)
        if (rebuildView) {
            setInputView(buildInputView())
            // buildInputView() creates fresh panels (all GONE) while `panelState`
            // still holds the pre-rebuild value. Re-assert visibility from the
            // current field so the first panel-chip tap is not swallowed.
            setPanelState(panelForField(currentInputType))
        }
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
            onSymbolsToggle = { openSymbolsPanel() }
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

        numericPanel = NumericPanel(this).apply {
            visibility = View.GONE
            onKey = { ch ->
                guarded {
                    haptic()
                    resetDoubleSpace()
                    currentInputConnection?.commitText(ch, 1)
                }
            }
            onBackspace = {
                guarded {
                    haptic()
                    resetDoubleSpace()
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }
            onEnter = { guarded { this@OsmiumImeService.onEnter() } }
            onDone = {
                guarded {
                    haptic()
                    setPanelState(PanelState.NONE)
                }
            }
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
            numericPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(panelHeightDp))
        )
        container.addView(
            keyboardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return wrapOneHanded(container)
    }

    /**
     * Docks the whole input view to one side (78% width) with a flip rail and an
     * exit handle. Everything else — panels, suggestions, key geometry — keeps
     * working because only the outer container is resized.
     */
    private fun wrapOneHanded(container: View): View {
        val dock = Prefs.oneHandedMode(this)
        if (dock == "off") return container
        val pal = Theme.palette(this)
        val dockWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        val railGravity = if (dock == "left") Gravity.END else Gravity.START
        fun railButton(label: String, description: String, click: () -> Unit) =
            TextView(this).apply {
                text = label
                textSize = 20f
                setTextColor(pal.keyText)
                setBackgroundColor(pal.keyboardBg)
                setPadding(px(6), px(8), px(6), px(8))
                contentDescription = description
                isClickable = true
                setOnClickListener { guarded { click() } }
            }

        return FrameLayout(this).apply {
            addView(container, FrameLayout.LayoutParams(
                dockWidth, FrameLayout.LayoutParams.MATCH_PARENT,
                if (dock == "left") Gravity.START else Gravity.END
            ))
            addView(railButton("⇄", "Flip keyboard side") {
                Prefs.setOneHandedMode(this@OsmiumImeService,
                    if (dock == "left") "right" else "left")
                setInputView(onCreateInputView())
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                railGravity or Gravity.TOP
            ))
            addView(railButton("⤢", "Full-width keyboard") {
                Prefs.setOneHandedMode(this@OsmiumImeService, "off")
                setInputView(onCreateInputView())
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                railGravity or Gravity.BOTTOM
            ))
        }
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
        currentFieldAsciiOnly = InputTypeRules.isAsciiOnly(info.inputType, isPassword)
        currentInputType = info.inputType
        currentEnterAction = if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            EditorInfo.IME_ACTION_NONE
        } else {
            info.imeOptions and EditorInfo.IME_MASK_ACTION
        }
        keyboardView?.setEnterAction(currentEnterAction)
        incognito = Prefs.incognitoByDefault(this) || noLearning || isPassword
        // On a restart of the same field, commit what was being composed before
        // dropping the session; for a different field, finishing is correct.
        // This must run BEFORE setIncognito: a privacy-state change
        // (normal -> password) rebuilds the engine, which drops the session
        // and would otherwise leave the editor's composing span unterminated.
        if (restarting && engine.isSessionActive()) commitCurrentCandidate()
        engine.setIncognito(incognito)
        suggestionBar?.setIncognito(incognito)
        suggestionBar?.setClipboardReadOnly(incognito)
        clipboardPanel?.setReadOnly(incognito)
        engine.finishSession()
        latest = RitiEngine.EMPTY
        suggestionBar?.clear()
        keyboardView?.setCandidateSelectionActive(false)
        // Numeric/phone fields get a compact literal-keypad panel; every other
        // field gets the normal keyboard. Closing any previous panel here also
        // clears its transient state via setPanelState().
        setPanelState(panelForField(info.inputType))
        resetDoubleSpace()
        recentCommitted.clear()
        lastComposed = null
        directCommitLength = 0
        composingProbeDone = false
        val pkg = if (android.os.Build.VERSION.SDK_INT >= 28) currentInputEditorInfo?.packageName else null
        directCommitMode = EditorCompat.needsDirectCommit(pkg, info.inputType)
        autoCapitalizeIfNeeded()
        captureClipboard()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        captureClipboard()
    }

    /**
     * Hardware keys: DPAD up/down cycles the highlighted candidate, and BACK
     * dismisses an open panel instead of being swallowed by the editor.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) &&
            engine.isSessionActive() && latest.candidates.isNotEmpty()
        ) {
            cycleCandidate(if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && panelState != PanelState.NONE) {
            setPanelState(PanelState.NONE)
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

    /** Writes text to the system clipboard when an editor ignores COPY/CUT. */
    private fun copyToSystemClipboard(text: String) {
        if (incognito) return
        try {
            val cm = getSystemService(ClipboardManager::class.java) ?: return
            cm.setPrimaryClip(ClipData.newPlainText("Osmium", text))
        } catch (_: Throwable) {
        }
    }

    /** Records newly copied text into the local clipboard history. */
    private fun captureClipboard() {
        val text = currentClipboardText() ?: return
        if (!ClipboardAccess.canCapture(incognito)) {
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

    /** Panel a field of [inputType] should start with. */
    private fun panelForField(inputType: Int): PanelState {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) {
            PanelState.NUMERIC
        } else {
            PanelState.NONE
        }
    }

    /**
     * Single owner of panel visibility. Every transition away from a panel
     * clears transient UI state (emoji search query/focus, navigation repeat
     * timers) so no panel can reopen in a stale state.
     */
    private fun setPanelState(state: PanelState) {
        // Clear transient state whenever a panel is left, including a direct
        // panel→panel switch (e.g. emoji→numeric on a new field), not just on
        // the way back to NONE.
        if (panelState != PanelState.NONE && state != panelState) {
            emojiPanel?.clearSearch()
            clipboardPanel?.clearSearch()
            navigationPanel?.clearTimers()
        }
        panelState = state
        emojiPanel?.visibility = if (state == PanelState.EMOJI) View.VISIBLE else View.GONE
        clipboardPanel?.visibility = if (state == PanelState.CLIPBOARD) View.VISIBLE else View.GONE
        navigationPanel?.visibility = if (state == PanelState.NAV) View.VISIBLE else View.GONE
        numericPanel?.visibility = if (state == PanelState.NUMERIC) View.VISIBLE else View.GONE
        keyboardView?.visibility = if (state == PanelState.NONE) View.VISIBLE else View.GONE
    }

    /** True while the emoji search field is open and should receive on-screen keys. */
    private fun emojiSearchActive(): Boolean =
        panelState == PanelState.EMOJI && emojiPanel?.isSearchFocused() == true

    /** True while the clipboard search box owns the on-screen keys. */
    private fun clipboardSearchActive(): Boolean =
        panelState == PanelState.CLIPBOARD && clipboardPanel?.isSearchFocused() == true

    private fun toggleClipboardPanel() = guarded {
        haptic()
        val clip = clipboardPanel ?: return@guarded
        val opening = panelState != PanelState.CLIPBOARD
        if (opening) {
            clip.refresh()
            clip.focusSearch()
        }
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
        if (clipboardSearchActive()) {
            clipboardPanel?.appendSearch(char)
            return@guarded
        }
        val modifier = if (altgr) MODIFIER_ALT_GR else 0
        when (mode) {
            KeyboardMode.ENGLISH -> {
                currentInputConnection?.commitText(char, 1)
                noteCommitted(char)
                lastWasSpace = false
                autoCapitalizeIfNeeded()
            }
            KeyboardMode.AVRO, KeyboardMode.PROBHAT -> {
                // Password fields must receive literal ASCII: transliterating a
                // password into Bengali would lock the user out of their account.
                if (currentFieldAsciiOnly && char.all { it.code < 128 }) {
                    currentInputConnection?.commitText(char, 1)
                } else {
                    sendToRiti(char, modifier)
                }
            }
        }
    }

    /**
     * A popup symbol riti cannot map (non-ASCII). Commit it as literal text through
     * the same commit-first funnel used by emoji and clipboard, so a live Bengali
     * composition is never overwritten.
     */
    override fun onSymbol(code: String) = guarded {
        haptic()
        insertTextSafely(code)
    }

    /** The popup's "more" chip: jump straight to the full Symbols picker. */
    override fun onSymbolsRequested() = guarded {
        haptic()
        openSymbolsPanel()
    }

    private fun openSymbolsPanel() = guarded {
        emojiPanel?.applyTheme()
        emojiPanel?.showSymbols()
        if (panelState != PanelState.EMOJI) setPanelState(PanelState.EMOJI)
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
        if (clipboardSearchActive()) {
            clipboardPanel?.appendSearch(" ")
            return@guarded
        }
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) {
            commitCurrentCandidate()
            lastWasSpace = false
            lastSpaceAt = 0L
            ic.commitText(" ", 1)
            noteCommitted(" ")
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
            if (recentCommitted.lastOrNull() == ' ') recentCommitted.removeLast()
            noteCommitted("।")
            lastWasSpace = false
            lastSpaceAt = 0L
        } else {
            ic.commitText(" ", 1)
            noteCommitted(" ")
            lastWasSpace = true
            lastSpaceAt = now
        }
        autoCapitalizeIfNeeded()
    }

    override fun onCursorGlide(delta: Int) = guarded {
        // B5: moving the selection while a riti session is live strands the
        // composing span and the engine buffer. End the composition first, then
        // move the cursor on committed text.
        if (engine.isSessionActive()) commitCurrentCandidate()
        resetDoubleSpace()
        val ic = currentInputConnection ?: return@guarded
        val pos = cursorPosition(ic) ?: return@guarded
        val target = (pos + delta).coerceAtLeast(0)
        ic.setSelection(target, target)
    }

    /**
     * Reads text and selection in as few InputConnection calls as possible.
     * On API 33+ [InputConnection.takeSnapshot] is tried first for an atomic
     * read, but the platform documents that IMEs normally receive `null` (and
     * the default implementation returns `null`), so a null/invalid snapshot
     * MUST fall through to a single getExtractedText() read — never be treated
     * as "no text". That fallback is also the only path on older devices.
     */
    private fun editorSnapshot(ic: InputConnection): EditorSnapshot? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val snapshot = ic.takeSnapshot()
            if (snapshot != null) {
                val surrounding = snapshot.surroundingText
                editorSnapshotOf(
                    surrounding.text?.toString(),
                    surrounding.offset,
                    snapshot.selectionStart,
                    snapshot.selectionEnd,
                    selectionIsAbsolute = true,
                )?.let { return it }
            }
        }
        // Single fallback read: null takeSnapshot, invalid offsets, or API < 33.
        val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        return editorSnapshotOf(
            et.text?.toString(),
            et.startOffset,
            et.selectionStart,
            et.selectionEnd,
            selectionIsAbsolute = false,
        )
    }

    /** Current absolute cursor position; null when unavailable. */
    private fun cursorPosition(ic: InputConnection): Int? = editorSnapshot(ic)?.selectionStart

    /**
     * English-only auto-cap at a word boundary that follows a sentence break
     * (or the start of the field). A manual shift/caps choice always wins.
     */
    private fun autoCapitalizeIfNeeded() {
        if (!Prefs.autoCapitalize(this) || mode != KeyboardMode.ENGLISH) return
        if (keyboardView?.isShiftManual() == true || autoCapSuppressedField()) return
        // Use what we committed, not the editor's lagging view of it: during fast
        // typing getTextBeforeCursor() can return text from before the last
        // commit, making every key look like a sentence start.
        keyboardView?.setShift(AutoCap.shouldShift(recentCommitted.lastOrNull()))
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
        if (clipboardSearchActive()) {
            clipboardPanel?.backspaceSearch()
            return@guarded
        }
        val ic = currentInputConnection ?: return@guarded
        recentCommitted.removeLastOrNull()
        if (engine.isSessionActive()) {
            // Riti owns the composition; let it recompute.
            val s = engine.backspace(ctrl = false)
            latest = s
            render(s)
        } else {
            // One snapshot decides both "is there a selection?" and the
            // codepoint width to delete — no second read that could race.
            val snap = editorSnapshot(ic)
            if (snap != null && snap.selectionStart != snap.selectionEnd) {
                ic.commitText("", 1)
            } else {
                deleteOneCodepoint(ic, snap)
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
                val selected = ic.getSelectedText(0)?.toString()
                val ok = ic.performContextMenuAction(android.R.id.copy)
                if (EditorActions.needsDirectCopy(ok, selected)) {
                    copyToSystemClipboard(selected!!)
                }
                captureClipboard()
            }
            NavAction.CUT -> {
                snapshotForUndo(ic)
                val selected = ic.getSelectedText(0)?.toString()
                val ok = ic.performContextMenuAction(android.R.id.cut)
                if (EditorActions.needsDirectCut(ok, selected)) {
                    copyToSystemClipboard(selected!!)
                    ic.commitText("", 1)
                }
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
            NavAction.SELECT_ALL -> {
                if (!ic.performContextMenuAction(android.R.id.selectAll)) {
                    val (start, end) = EditorActions.safeSelection(0, fullText(ic).length)
                    ic.setSelection(start, end)
                }
            }
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

        val before = editorSnapshot(ic)?.selectionStart
        ic.sendKeyEvent(movementKeyEvent(KeyEvent.ACTION_DOWN, keyCode, meta))
        ic.sendKeyEvent(movementKeyEvent(KeyEvent.ACTION_UP, keyCode, meta))
        val after = editorSnapshot(ic)
        if (before != null && after != null && after.selectionStart == before) {
            // Mirror the key-event meta logic: only LEFT/RIGHT extend a selection.
            val extendSelection = shiftActive &&
                (action == NavAction.LEFT || action == NavAction.RIGHT)
            // Reuse the "after" snapshot for the fallback instead of reading the
            // editor a third time, which could observe a different field state.
            fallbackSelection(ic, action, before, extendSelection, after)
        }
    }

    private fun movementKeyEvent(action: Int, keyCode: Int, meta: Int): KeyEvent {
        val now = android.os.SystemClock.uptimeMillis()
        return KeyEvent(now, now, action, keyCode, meta)
    }

    /** setSelection fallback computed from the post-key snapshot for fields that ignore key events. */
    private fun fallbackSelection(
        ic: InputConnection, action: NavAction, cursor: Int, extend: Boolean,
        snap: EditorSnapshot
    ) {
        val s = snap.text
        val base = snap.startOffset
        val rel = snap.relativeSelection
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

    /** Selects the single word around the cursor from one consistent snapshot. */
    private fun selectWordAtCursor(ic: InputConnection) {
        val snap = editorSnapshot(ic) ?: return
        val s = snap.text
        val range = TextNav.wordRangeAroundBangla(s, snap.relativeSelection)
        // Snapshot text offsets are relative to startOffset; setSelection is absolute.
        ic.setSelection(snap.startOffset + range.first, snap.startOffset + range.last)
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
            panel.focusSearch()
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
            if (directCommitMode) {
                // Replace the word we inserted directly, not a composing span.
                if (directCommitLength > 0) ic.deleteSurroundingText(directCommitLength, 0)
                directCommitLength = 0
            }
            // commitText replaces the composing span reliably in all editors
            // (setComposingText + finishComposingText fails in some, e.g. WhatsApp).
            ic.commitText(candidate, 1)
            noteCommitted(candidate)
            ic.finishComposingText()
            lastComposed = null
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
        // `latest.selection` is a DISPLAY index (post Bengali-first reorder /
        // candidate pick). Riti reads this value as its own ORIGINAL candidate
        // index and echoes it back on punctuation keys, so it must be inverted
        // here just as it is on commit — otherwise typing `.`/`?`/`!` mid-word
        // jumps to the wrong candidate and commits the wrong word on space.
        val originalSelection = mapToOriginalIndex(latest.selection, latest.originalIndices)
        val s = engine.suggest(char, modifier = modifier, selection = originalSelection)
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
        } else if (directCommitMode) {
            // Editor drops composing spans: keep the live word visible by replacing
            // our own previous insertion with only universally supported calls.
            ic.beginBatchEdit()
            try {
                if (directCommitLength > 0) ic.deleteSurroundingText(directCommitLength, 0)
                ic.commitText(composed, 1)
                directCommitLength = composed.length
            } finally {
                ic.endBatchEdit()
            }
            if (!currentFieldAsciiOnly) suggestionBar?.update(s)
        } else {
            if (composed != lastComposed) {
                // Setting the same composing text every keystroke forces an editor
                // round-trip and a full re-layout in some apps; skip it when unchanged.
                ic.setComposingText(composed, 1)
                lastComposed = composed
                scheduleComposingProbe(composed)
            }
            // Password fields never echo typed characters in the suggestion
            // strip — compose silently instead. (Password fields already force
            // incognito on, but the explicit check keeps the privacy gate local.)
            if (!currentFieldAsciiOnly) {
                suggestionBar?.update(s)
            }
        }
    }

    /**
     * One-shot check per field: if the editor does not contain the composing text we
     * just set, its composing span is unreliable, so switch to direct-insert mode.
     */
    private fun scheduleComposingProbe(composed: String) {
        if (composingProbeDone || composed.isEmpty()) return
        composingProbeDone = true
        handler.postDelayed({
            if (!directCommitMode && latest.candidates.getOrNull(latest.selection) == composed) {
                val before = currentInputConnection
                    ?.getTextBeforeCursor(composed.length.coerceAtMost(128), 0)?.toString()
                if (!EditorCompat.composingStuck(before, composed)) {
                    clearComposition(currentInputConnection ?: return@postDelayed)
                    directCommitMode = true
                    render(latest)
                }
            }
        }, 150)
    }

    /** Commits the currently selected candidate and ends the riti session. */
    private fun commitCurrentCandidate() {
        val ic = currentInputConnection ?: return
        resetDoubleSpace()
        ic.beginBatchEdit()
        try {
            val selected = latest.candidates.getOrNull(latest.selection) ?: ""
            if (selected.isNotEmpty()) {
                // commitText() replaces the active composing span and is supported
                // by every editor. setComposingText()+finishComposingText() is
                // mishandled by some editors (notably WhatsApp): the candidate
                // shows in our bar but an empty/stale span gets committed, so the
                // word never appears. Clear any leftover composing span afterwards.
                if (directCommitMode) {
                    // The word is already in the field; just finalise the engine.
                    directCommitLength = 0
                } else {
                    ic.commitText(selected, 1)
                    noteCommitted(selected)
                    ic.finishComposingText()
                }
                lastComposed = null
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
            if (directCommitMode) {
                if (directCommitLength > 0) ic.deleteSurroundingText(directCommitLength, 0)
                directCommitLength = 0
            } else {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            }
            lastComposed = null
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
    private fun deleteOneCodepoint(ic: InputConnection, snap: EditorSnapshot? = null) {
        // Prefer the two characters immediately before the cursor from the
        // same snapshot; only fall back to a separate read when the snapshot
        // has no text before the cursor window.
        val before = if (snap != null && snap.relativeSelection > 0) {
            val rel = snap.relativeSelection
            snap.text.substring(maxOf(0, rel - 2), rel)
        } else {
            ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        }
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
        val next = when (mode) {
            KeyboardMode.AVRO -> KeyboardMode.PROBHAT
            KeyboardMode.PROBHAT -> KeyboardMode.ENGLISH
            KeyboardMode.ENGLISH -> KeyboardMode.AVRO
        }
        switchToMode(next)
    }

    /** Applies [next] as the active mode: engine, persisted scheme and UI. */
    private fun switchToMode(next: KeyboardMode) {
        if (engine.isSessionActive()) commitCurrentCandidate()
        resetDoubleSpace()

        mode = next

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

    /** Path to the selected fixed layout file (bundled Probhat/Jatiya or imported). */
    private fun selectedLayoutPath(): String {
        RitiEngine.ensureData(this)
        val name = Prefs.selectedLayout(this)
        val bundled = when (name) {
            "probhat" -> "Probhat.json"
            "jatiya" -> "National_Jatiya.json"
            else -> null
        }
        if (bundled != null) return File(filesDir, "riti/$bundled").absolutePath
        val custom = File(Prefs.layoutsDir(this), name)
        return if (custom.exists()) custom.absolutePath
        else File(filesDir, "riti/Probhat.json").absolutePath
    }

    /** Selected layout display name, or null for the bundled Probhat. */
    private fun selectedLayoutName(): String? = when (Prefs.selectedLayout(this)) {
        "probhat" -> null
        "jatiya" -> "জাতীয়"
        else -> Prefs.selectedLayout(this).removeSuffix(".json")
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
