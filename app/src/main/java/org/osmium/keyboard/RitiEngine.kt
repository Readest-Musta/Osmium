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

    /** Last engine initialization failure, if any (surfaced in diagnostics). */
    var initError: String? = null
        private set

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
        try {
            ensureData(context)
        } catch (t: Throwable) {
            initError = "asset copy failed: ${t.message ?: t}"
        }
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
        try {
            riti = OsRiti(
                databaseDir = dbDir.absolutePath,
                userDir = effectiveUserDir.absolutePath,
                layout = layout,
            )
            currentLayout = layout
        } catch (t: Throwable) {
            riti = null
            initError = "engine init failed (${t.javaClass.simpleName}): ${t.message ?: t}"
        }
    }

    /**
     * (Re)initialises the engine with the given layout.
     * `layout` is either `avro_phonetic` (built into riti) or a path to a fixed
     * layout file such as our bundled Probhat.json.
     */
    fun useLayout(layout: String) {
        if (layout == currentLayout && riti != null) return
        try {
            riti = OsRiti(
                databaseDir = dbDir.absolutePath,
                userDir = userDir.absolutePath,
                layout = layout,
            )
            currentLayout = layout
            initError = null
        } catch (t: Throwable) {
            riti = null
            currentLayout = null
            initError = "engine init failed (${t.javaClass.simpleName}): ${t.message ?: t}"
        }
    }

    /** Sends a key press to riti and returns the updated suggestion state. */
    fun suggest(keyChar: String, modifier: Int = 0, selection: Int = 0): OsSuggestion {
        val ctx = riti ?: return EMPTY
        return try {
            val code = keycodeForChar(keyChar)
            if (code == 0) EMPTY else ctx.suggestForKey(code, modifier, selection)
        } catch (t: Throwable) {
            initError = "suggest('$keyChar') failed (${t.javaClass.simpleName}): ${t.message}"
            EMPTY
        }
    }

    /** Backspace event inside an active session. */
    fun backspace(ctrl: Boolean = false): OsSuggestion {
        val ctx = riti ?: return EMPTY
        return try {
            ctx.backspace(ctrl)
        } catch (t: Throwable) {
            initError = "backspace failed (${t.javaClass.simpleName}): ${t.message}"
            EMPTY
        }
    }

    /** A suggestion candidate was committed (ends the session). */
    fun candidateCommitted(index: Int) {
        try {
            riti?.candidateCommitted(index)
        } catch (_: Throwable) {
        }
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
