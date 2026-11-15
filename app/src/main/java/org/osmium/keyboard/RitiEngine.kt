package org.osmium.keyboard

import android.content.Context
import java.io.File
import uniffi.osmium.OsRiti
import uniffi.osmium.OsSuggestion
import uniffi.osmium.keycodeForChar

/**
 * Kotlin bridge to the riti engine through the osmium-native (UniFFI) bindings.
 *
 * Data files bundled as assets are copied into internal storage on first use —
 * riti needs real filesystem paths.
 */
class RitiEngine(private val context: Context) {

    private val dbDir = File(context.filesDir, "riti")
    private val userDir = File(context.filesDir, "user")
    private val scratchDir = File(context.cacheDir, "incognito-session")
    private var riti: OsRiti? = null
    private var currentLayout: String? = null
    private var currentIncognito = false

    companion object {
        private const val ASSET_DIR = "riti"
        private val DATA_FILES = listOf(
            "dictionary.json", "autocorrect.json", "suffix.json", "Probhat.json"
        )

        /** Copies bundled riti data files into internal storage (idempotent). */
        fun ensureData(context: Context) {
            val dbDir = File(context.filesDir, "riti")
            val userDir = File(context.filesDir, "user")
            dbDir.mkdirs()
            userDir.mkdirs()
            for (name in DATA_FILES) {
                val target = File(dbDir, name)
                if (target.exists() && target.length() > 0) continue
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        val EMPTY = OsSuggestion(
            auxiliary = "", candidates = emptyList(), selection = 0, isEmpty = true
        )
    }

    init {
        ensureData(context)
    }

    /**
     * Incognito mode: point riti at a throwaway user directory so nothing it
     * learns in this session can persist. Leaving incognito wipes the scratch
     * directory and restores the normal context.
     */
    fun setIncognito(active: Boolean) {
        if (active == currentIncognito && riti != null) return
        currentIncognito = active
        val effectiveUserDir = if (active) {
            scratchDir.apply { deleteRecursively(); mkdirs() }
        } else {
            scratchDir.deleteRecursively()
            userDir.apply { mkdirs() }
        }
        val layout = currentLayout ?: "avro_phonetic"
        riti = OsRiti(
            databaseDir = dbDir.absolutePath,
            userDir = effectiveUserDir.absolutePath,
            layout = layout,
        )
        currentLayout = layout
    }

    /**
     * (Re)initialises the engine with the given layout.
     * `layout` is either `avro_phonetic` (built into riti) or a path to a fixed
     * layout file such as our bundled Probhat.json.
     */
    fun useLayout(layout: String) {
        if (layout == currentLayout && riti != null) return
        riti = OsRiti(
            databaseDir = dbDir.absolutePath,
            userDir = userDir.absolutePath,
            layout = layout,
        )
        currentLayout = layout
    }

    /** Sends a key press to riti and returns the updated suggestion state. */
    fun suggest(keyChar: String, selection: Int = 0): OsSuggestion {
        val ctx = riti ?: return EMPTY
        val code = keycodeForChar(keyChar)
        return if (code == 0) EMPTY else ctx.suggestForKey(code, 0, selection)
    }

    /** Backspace event inside an active session. */
    fun backspace(ctrl: Boolean = false): OsSuggestion {
        val ctx = riti ?: return EMPTY
        return ctx.backspace(ctrl)
    }

    /** A suggestion candidate was committed (ends the session). */
    fun candidateCommitted(index: Int) {
        riti?.candidateCommitted(index)
    }

    /** Drop the current composition. */
    fun finishSession() {
        riti?.finishSession()
    }

    fun isSessionActive(): Boolean = riti?.isSessionActive() ?: false

    fun updateEngine(layout: String) {
        riti?.updateEngine(dbDir.absolutePath, userDir.absolutePath, layout)
        currentLayout = layout
    }
}
