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

/**
 * Osmium IME — the heart of the keyboard.
 *
 * Input flow (Bangla modes):
 *   key tap → riti keycode → riti suggestion → composing text + suggestion bar
 *   space / tap  → candidate committed, riti session ends
 */
class OsmiumImeService : InputMethodService(), KeyboardView.Listener {

    private lateinit var engine: RitiEngine
    private var keyboardView: KeyboardView? = null
    private var suggestionBar: SuggestionBar? = null
    private var emojiPanel: EmojiPanel? = null

    private var mode = KeyboardMode.AVRO
    private var latest = RitiEngine.EMPTY
    private var incognito = false

    override fun onCreate() {
        super.onCreate()
        engine = RitiEngine(this)
        mode = when (Prefs.banglaScheme(this)) {
            "probhat" -> KeyboardMode.PROBHAT
            else -> KeyboardMode.AVRO
        }
        // Initialise the engine for the saved mode (was missing in v0.1).
        engine.useLayout(layoutFor(mode))
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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        suggestionBar = SuggestionBar(this).apply {
            setModeLabel(mode)
            onSuggestionTap = { index -> onSuggestion(index) }
            onLanguageToggle = { cycleMode() }
            onNumberRowToggle = { keyboardView?.toggleNumberRow() }
            onSettingsTap = { launchSetup() }
        }

        keyboardView = KeyboardView(this).apply {
            this.mode = this@OsmiumImeService.mode
            listener = this@OsmiumImeService
        }

        emojiPanel = EmojiPanel(this).apply {
            visibility = View.GONE
            onEmojiPicked = { emoji ->
                currentInputConnection?.commitText(emoji, 1)
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
            keyboardView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return container
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
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
    // Key handling (KeyboardView.Listener)
    // ------------------------------------------------------------------

    override fun onKey(code: String, shifted: Boolean) {
        haptic()
        val char = if (shifted) code.uppercase() else code
        when (mode) {
            KeyboardMode.ENGLISH -> currentInputConnection?.commitText(char, 1)
            KeyboardMode.AVRO -> sendToRiti(char)
            KeyboardMode.PROBHAT -> sendToRiti(char)
        }
    }

    override fun onShiftTap() { haptic() }

    override fun onSpace() {
        haptic()
        val ic = currentInputConnection ?: return
        if (engine.isSessionActive()) {
            commitCurrentCandidate()
        }
        ic.commitText(" ", 1)
    }

    override fun onBackspace() {
        haptic()
        val ic = currentInputConnection ?: return
        if (engine.isSessionActive()) {
            // Riti owns the composition; let it recompute.
            val s = engine.backspace(ctrl = false)
            latest = s
            render(s)
        } else {
            deleteOneCodepoint(ic)
        }
    }

    override fun onEnter() {
        haptic()
        val ic = currentInputConnection ?: return
        if (engine.isSessionActive()) {
            commitCurrentCandidate()
        }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onModeCycle() {
        haptic()
        cycleMode()
    }

    override fun onEmojiToggle() {
        haptic()
        val panel = emojiPanel ?: return
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    override fun onNumberRowToggle() {
        haptic()
        keyboardView?.toggleNumberRow()
    }

    // ------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------

    private fun onSuggestion(index: Int) {
        val candidate = latest.candidates.getOrNull(index) ?: return
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        ic.commitText(candidate, 1)
        engine.candidateCommitted(index)
        latest = RitiEngine.EMPTY
        suggestionBar?.clear()
        haptic()
    }

    private fun sendToRiti(char: String) {
        val s = engine.suggest(char, selection = latest.selection)
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
        val selected = s.candidates.getOrNull(s.selection) ?: ""
        val composed = s.auxiliary + selected
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
        ic.finishComposingText()
        if (selected.isNotEmpty()) {
            ic.commitText(selected, 1)
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

        keyboardView?.mode = mode
        suggestionBar?.setModeLabel(mode)
        suggestionBar?.clear()
    }

    /** riti layout identifier for a mode, or null for English. */
    private fun layoutFor(mode: KeyboardMode): String? = when (mode) {
        KeyboardMode.AVRO -> "avro_phonetic"
        KeyboardMode.PROBHAT -> {
            RitiEngine.ensureData(this)
            java.io.File(filesDir, "riti/Probhat.json").absolutePath
        }
        KeyboardMode.ENGLISH -> null
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(8, 120))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(8)
        }
    }

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
