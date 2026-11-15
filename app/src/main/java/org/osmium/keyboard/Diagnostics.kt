package org.osmium.keyboard

import android.content.Context
import uniffi.osmium.keycodeForChar

/**
 * Self-diagnostics that run inside the app UI so failures are visible on the
 * device (we ship no network access, so there is no crash reporting service).
 */
object Diagnostics {

    data class Check(val name: String, val ok: Boolean, val detail: String)

    fun run(context: Context): List<Check> = listOf(
        checkDataFiles(context),
        checkNativeLibrary(),
        checkEngine(context),
        checkProbhat(context),
    )

    private fun checkDataFiles(context: Context): Check {
        return try {
            RitiEngine.ensureData(context)
            val dir = java.io.File(context.filesDir, "riti")
            val files = dir.listFiles()?.joinToString { f -> "${f.name}(${f.length() / 1024}KB)" }
            Check("Bundled data files copied", true, files ?: "empty")
        } catch (t: Throwable) {
            Check("Bundled data files copied", false, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun checkNativeLibrary(): Check {
        return try {
            val code = keycodeForChar("a")
            if (code != 0) {
                Check("Native library (libosmium_native.so) loads", true, "keycodeForChar('a') = $code")
            } else {
                Check("Native library (libosmium_native.so) loads", false, "keycodeForChar returned 0")
            }
        } catch (t: Throwable) {
            Check("Native library (libosmium_native.so) loads", false, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun checkEngine(context: Context): Check {
        return try {
            val engine = RitiEngine(context)
            engine.useLayout("avro_phonetic")
            val s = engine.suggest("a")
            val detail = "candidates=${s.candidates.take(3)}, engineError=${engine.initError ?: "none"}"
            Check("Avro Phonetic engine + suggestion", s.candidates.isNotEmpty(), detail)
        } catch (t: Throwable) {
            Check("Avro Phonetic engine + suggestion", false, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun checkProbhat(context: Context): Check {
        return try {
            val engine = RitiEngine(context)
            val path = java.io.File(context.filesDir, "riti/Probhat.json").absolutePath
            engine.useLayout(path)
            val s = engine.suggest("k")
            val detail = "key 'k' → ${s.candidates.firstOrNull() ?: s.auxiliary}"
            Check("Probhat engine", s.candidates.isNotEmpty(), detail)
        } catch (t: Throwable) {
            Check("Probhat engine", false, "${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
