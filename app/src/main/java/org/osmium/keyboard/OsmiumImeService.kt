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

    private var mode = KeyboardMode.AVRO
    private var latest = RitiEngine.EMPTY
    private var incognito = false
    private var appliedThemeKey: String? = null
    /** State for double-space → দাঁড়ি (।). */
    private var lastSpaceAt = 0L
    private var lastWasSpace = false
    /** Last clipboard text seen, for change detection. */
    private var lastClip = ""

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

    // ------------------------------------------------------------------
    // View setup
    // ------------------------------------------------------------------

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        return try {
            buildInputView()
        } catch (t: Throwable) {
            // Never leave the user with a blank keyboard — show the error on screen.
            errorView(t)
        }
    }

    private fun errorView(t: Throwable): View {
        val text = android.widget.TextView(this).apply {
            text = "Osmium error — please report this:\n\n${t.stackTraceToString().take(2000)}"
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            textSize = 12f
        }
        return android.widget.ScrollView(this).apply { addView(text) }
    }

    private fun buildInputView(): View {
        appliedThemeKey = Theme.key(this)
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
                currentInputConnection?.commitText(emoji, 1)
                haptic()
            }
        }

        clipboardPanel = ClipboardPanel(this).apply {
            visibility = View.GONE
            onPaste = { text ->
                currentInputConnection?.commitText(text, 1)
                haptic()
            }
        }

        container.addView(
            suggestionBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(46)
            )
        )
        container.addView(
            emojiPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(230))
        )
        container.addView(
            clipboardPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(230))
        )
        container.addView(
            keyboardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return container
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Rebuild the input view if the theme setting changed since last time.
        if (Theme.key(this) != appliedThemeKey) {
            setInputView(onCreateInputView())
        }
        // Determine incognito state: user preference OR field privacy flags.
        val noLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        val isPassword = isPasswordField(info)
        incognito = Prefs.incognitoByDefault(this) || noLearning || isPassword
        engine.setIncognito(incognito)
        suggestionBar?.setIncognito(incognito)
        engine.finishSession()
        latest = RitiEngine.EMPTY
        suggestionBar?.clear()
        emojiPanel?.visibility = View.GONE
        clipboardPanel?.visibility = View.GONE
        lastWasSpace = false
        autoCapitalizeIfNeeded()
        captureClipboard()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) captureClipboard()
    }

    /** Records newly copied text into the local clipboard history. */
    private fun captureClipboard() {
        try {
            val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
            if (text.isNotBlank() && text != lastClip && text.length <= 4000) {
                lastClip = text
                Prefs.addClipboardItem(this, text)
                clipboardPanel?.refresh()
            }
        } catch (_: Throwable) {
        }
    }

    private fun toggleClipboardPanel() = guarded {
        haptic()
        val clip = clipboardPanel ?: return@guarded
        val clipShowing = clip.visibility == View.VISIBLE
        clip.visibility = if (clipShowing) View.GONE else View.VISIBLE
        if (!clipShowing) {
            clip.refresh()
            emojiPanel?.visibility = View.GONE
        }
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
            setInputView(errorView(t))
        }
    }

    override fun onKey(code: String, shifted: Boolean, altgr: Boolean) = guarded {
        haptic()
        val char = if (shifted) code.uppercase() else code
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

    override fun onFeedback() = guarded { haptic() }

    override fun onShiftTap() = guarded { haptic() }

    override fun onSpace() = guarded {
        haptic()
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
        if (Prefs.doubleSpaceDari(this) && mode != KeyboardMode.PROBHAT &&
            lastWasSpace && now - lastSpaceAt < 400
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

    /** Shift on when the cursor sits at a sentence start (English mode only). */
    private fun autoCapitalizeIfNeeded() {
        if (!Prefs.autoCapitalize(this) || mode != KeyboardMode.ENGLISH) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(16, 0)?.toString().orEmpty().trimEnd()
        val atSentenceStart = before.isEmpty() || before.last() in ".!?।\n"
        keyboardView?.setShift(atSentenceStart)
    }

    override fun onBackspace() = guarded {
        haptic()
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) {
            // Riti owns the composition; let it recompute.
            val s = engine.backspace(ctrl = false)
            latest = s
            render(s)
        } else {
            deleteOneCodepoint(ic)
        }
    }

    override fun onEnter() = guarded {
        haptic()
        val ic = currentInputConnection ?: return@guarded
        if (engine.isSessionActive()) {
            commitCurrentCandidate()
        }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onModeCycle() = guarded {
        haptic()
        cycleMode()
    }

    override fun onEmojiToggle() = guarded {
        haptic()
        val panel = emojiPanel ?: return@guarded
        val showing = panel.visibility == View.VISIBLE
        panel.visibility = if (showing) View.GONE else View.VISIBLE
        if (!showing) clipboardPanel?.visibility = View.GONE
    }

    override fun onNumberRowToggle() = guarded {
        haptic()
        keyboardView?.toggleNumberRow()
    }

    // ------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------

    private fun onSuggestion(index: Int) = guarded {
        val candidate = latest.candidates.getOrNull(index) ?: return@guarded
        val ic = currentInputConnection ?: return@guarded
        // Replace the composing region with the picked candidate in one step.
        ic.setComposingText(candidate, 1)
        ic.finishComposingText()
        engine.candidateCommitted(index)
        latest = RitiEngine.EMPTY
        suggestionBar?.clear()
        haptic()
    }

    private fun sendToRiti(char: String, modifier: Int = 0) {
        val s = engine.suggest(char, modifier = modifier, selection = latest.selection)
        if (s.isEmpty && engine.initError != null) {
            // Engine is broken — show why instead of silently doing nothing.
            setInputView(errorView(IllegalStateException("riti engine: ${engine.initError}")))
            return
        }
        latest = s
        render(s)
    }

    private fun render(s: OsSuggestion) {
        val ic = currentInputConnection ?: return
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
            suggestionBar?.update(s)
        }
    }

    /** Commits the currently selected candidate and ends the riti session. */
    private fun commitCurrentCandidate() {
        val ic = currentInputConnection ?: return
        val selected = latest.candidates.getOrNull(latest.selection) ?: ""
        if (selected.isNotEmpty()) {
            // Replace the composing region with the final text in one step,
            // then commit it (finishComposingText on stale text would
            // duplicate it into the field).
            ic.setComposingText(selected, 1)
            ic.finishComposingText()
            engine.candidateCommitted(latest.selection)
        }
        latest = RitiEngine.EMPTY
        suggestionBar?.clear()
    }

    private fun clearComposition(ic: InputConnection) {
        ic.setComposingText("", 1)
        ic.finishComposingText()
        latest = RitiEngine.EMPTY
        suggestionBar?.clear()
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

    private fun cycleMode() {
        if (engine.isSessionActive()) commitCurrentCandidate()

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
