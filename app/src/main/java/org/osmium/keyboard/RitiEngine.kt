package org.osmium.keyboard

import android.content.Context
import java.io.File
import uniffi.osmium.OsRiti
import uniffi.osmium.OsSuggestion
import uniffi.osmium.keycodeForChar

/**
 * Maps a candidate's *display* index (after the Bengali-first reorder) back to
 * riti's original index. Missing/out-of-range entries fall back to the display
 * index, which is correct for lists that were not reordered (e.g. EMPTY).
 */
internal fun mapToOriginalIndex(displayIndex: Int, originalIndices: List<Int>): Int =
    originalIndices.getOrNull(displayIndex) ?: displayIndex

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
            "dictionary.json", "autocorrect.json", "suffix.json", "Probhat.json",
            "National_Jatiya.json",
        )
        /** Bump whenever bundled riti assets change so upgrades re-copy them. */
        const val DATA_VERSION = "v2"
        private const val VERSION_FILE = ".version"

        /**
         * True only when the version marker matches and every data file is non-empty.
         * Android-free so an upgrade / truncated-copy regression can be pinned on the JVM.
         */
        internal fun isDataFresh(dir: File, names: List<String>, version: String): Boolean {
            val marker = File(dir, VERSION_FILE)
            if (!marker.exists() || marker.readText().trim() != version) return false
            return names.all { name ->
                val file = File(dir, name)
                file.exists() && file.length() > 0L
            }
        }

        /**
         * Copies bundled riti data into internal storage. A data set is accepted only
         * with a matching version marker; stale or truncated copies are replaced via
         * a temp file + atomic rename so a failed copy can never leave a half file.
         */
        fun ensureData(context: Context) {
            val dbDir = File(context.filesDir, "riti")
            val userDir = File(context.filesDir, "user")
            dbDir.mkdirs()
            userDir.mkdirs()
            if (isDataFresh(dbDir, DATA_FILES, DATA_VERSION)) return

            var complete = true
            for (name in DATA_FILES) {
                val target = File(dbDir, name)
                val tmp = File(dbDir, "$name.tmp")
                try {
                    context.assets.open("$ASSET_DIR/$name").use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmp.length() == 0L || !tmp.renameTo(target)) complete = false
                } catch (_: Exception) {
                    complete = false
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
            }
            // Write the marker last: a failed copy leaves the old marker in place so
            // the next start retries instead of trusting an incomplete data set.
            if (complete) File(dbDir, VERSION_FILE).writeText(DATA_VERSION)
        }

        val EMPTY = OsSuggestion(
            auxiliary = "",
            candidates = emptyList(),
            selection = 0,
            isEmpty = true,
            originalIndices = emptyList(),
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

    /** True when the bundled data set is present, complete and version-matched. */
    fun dataIsFresh(): Boolean = isDataFresh(File(context.filesDir, "riti"), DATA_FILES, DATA_VERSION)

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

    /**
     * A suggestion candidate was committed (ends the session).
     *
     * [displayIndex] is the index the user sees/taps in the (Bengali-first)
     * suggestion bar; [indices] translates it back to riti's original index.
     */
    fun candidateCommitted(displayIndex: Int, indices: List<Int> = emptyList()) {
        val originalIndex = mapToOriginalIndex(displayIndex, indices)
        try {
            riti?.candidateCommitted(originalIndex)
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
