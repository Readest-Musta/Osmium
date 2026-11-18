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
    /** User directory the current [riti] instance was actually built with. */
    private var activeUserDir: String? = null

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

    /** The user directory riti must use for the current privacy state. */
    private fun effectiveUserDir(): File = if (currentIncognito) scratchDir else userDir

    /**
     * Single path for (re)creating the native engine. The user directory is
     * always chosen from the *current* incognito state, so a layout/engine
     * change can never silently fall back to the persistent user directory.
     */
    private fun rebuild(layout: String) {
        val effectiveDir = effectiveUserDir()
        effectiveDir.mkdirs()
        try {
            riti = OsRiti(
                databaseDir = dbDir.absolutePath,
                userDir = effectiveDir.absolutePath,
                layout = layout,
            )
            currentLayout = layout
            activeUserDir = effectiveDir.absolutePath
            initError = null
        } catch (t: Throwable) {
            riti = null
            currentLayout = null
            activeUserDir = null
            initError = "engine init failed (${t.javaClass.simpleName}): ${t.message ?: t}"
        }
    }

    /**
     * Incognito mode: point riti at a throwaway user directory so nothing it
     * learns in this session can persist. Leaving incognito wipes the scratch
     * directory and restores the normal context.
     */
    fun setIncognito(active: Boolean) {
        val intendedDir = if (active) scratchDir else userDir
        // The flag alone is not enough: an earlier layout/engine change could
        // have rebuilt riti against the other directory while the flag was
        // stale. Only skip work when the *actual* directory matches as well.
        if (active == currentIncognito && riti != null &&
            activeUserDir == intendedDir.absolutePath
        ) return
        currentIncognito = active
        if (active) {
            scratchDir.apply { deleteRecursively(); mkdirs() }
        } else {
            scratchDir.deleteRecursively()
            userDir.mkdirs()
        }
        val layout = currentLayout ?: "avro_phonetic"
        rebuild(layout)
    }

    /** Removes everything riti learned during an incognito session. */
    fun clearScratch() {
        try {
            scratchDir.deleteRecursively()
        } catch (_: Throwable) {
        }
    }

    /**
     * (Re)initialises the engine with the given layout.
     * `layout` is either `avro_phonetic` (built into riti) or a path to a fixed
     * layout file such as our bundled Probhat.json.
     */
    fun useLayout(layout: String) {
        // A mode switch while incognito must rebuild against the scratch dir,
        // never the persistent user dir.
        if (layout == currentLayout && riti != null &&
            activeUserDir == effectiveUserDir().absolutePath
        ) return
        rebuild(layout)
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
        // Go through the shared helper so incognito cannot be bypassed here.
        rebuild(layout)
    }
}
