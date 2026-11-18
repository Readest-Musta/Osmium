package org.osmium.keyboard

import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Probhat / custom-layout parity (Task 6).
 *
 * The bundled label asset is a derived view of `riti/Probhat.json`: every
 * non-empty `Key_*_AltGr` character defined by the engine layout must appear in
 * the label asset with the *same* character. A drift here silently produces dead
 * AltGr keys and unlabelled popups, so it is guarded by a JVM test.
 *
 * The second half pins strict layout-import validation: label-only JSON (which
 * riti cannot load) must be rejected, while a real OpenBangla layout is accepted.
 */
class LayoutParityTest {

    /** Assets live in src/main/assets (not on the JVM test classpath); find the repo/module root. */
    private fun asset(path: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        var tries = 0
        while (dir != null && tries++ < 8) {
            for (prefix in arrayOf("app/src/main/assets/", "src/main/assets/")) {
                val f = File(dir, prefix + path)
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("asset not found: $path")
    }

    /** Android Key_* name -> physical label code (the char used as a label-map key). */
    private fun codeFor(name: String): String? =
        if (name.length == 1) name else PUNCT_NAMES[name]

    private val punctKeys: List<String>
        get() = JSONObject(asset("riti/Probhat.json"))
            .getJSONObject("layout").keys().asSequence()
            .filter { it.startsWith("Key_") && it.endsWith("_Normal") }
            .map { it.removePrefix("Key_").removeSuffix("_Normal") }
            .filter { name ->
                name.length != 1 || (name[0] !in 'a'..'z' && name[0] !in 'A'..'Z' && name[0] !in '0'..'9')
            }
            .toList()

    @Test
    fun everyNonEmptyProbhatAltGrHasAMatchingLabel() {
        val probhat = JSONObject(asset("riti/Probhat.json"))
        val labels = JSONObject(asset("layouts/probhat-labels.json"))
        val layout = probhat.getJSONObject("layout")

        fun labelEntry(code: String): JSONObject? {
            for (section in arrayOf("letters", "digits", "punct")) {
                val obj = labels.optJSONObject(section) ?: continue
                if (obj.has(code)) return obj.getJSONObject(code)
            }
            return null
        }

        val problems = mutableListOf<String>()
        var checked = 0
        for (key in layout.keys()) {
            if (!key.startsWith("Key_") || !key.endsWith("_AltGr")) continue
            val ch = layout.getString(key)
            if (ch.isEmpty()) continue
            val name = key.removePrefix("Key_").removeSuffix("_AltGr")
            val code = codeFor(name)
            if (code == null) {
                problems += "no physical-key mapping for $key ('$ch')"
                continue
            }
            checked++
            val entry = labelEntry(code)
            if (entry == null) {
                problems += "missing label entry for $key -> '$code' (expected '$ch')"
                continue
            }
            val actual = entry.optString("a", "")
            if (actual != ch) {
                problems += "AltGr mismatch for $key -> '$code': label '$actual' != '$ch'"
            }
        }

        assertTrue("expected 17 non-empty AltGr keys, checked $checked", checked == 17)
        assertTrue(
            "Probhat / label AltGr parity problems:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    @Test
    fun fromLayoutJsonParsesEveryProbhatPunctuationName() {
        val probhat = JSONObject(asset("riti/Probhat.json"))
        val parsed = LayoutLabels.fromLayoutJson(probhat)
        val problems = mutableListOf<String>()
        for (name in punctKeys) {
            val code = codeFor(name)
            if (code == null) {
                problems += "no physical-key mapping for $name"
                continue
            }
            val expected = probhat.getJSONObject("layout").getString("Key_${name}_Normal")
            val entry = parsed[code]
            if (entry == null) {
                problems += "fromLayoutJson dropped Key_${name}_Normal -> '$code' ('$expected')"
            } else if (entry.n != expected) {
                problems += "wrong normal for '$code': '${entry.n}' != '$expected'"
            }
        }
        assertTrue(
            "fromLayoutJson punctuation coverage problems:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    @Test
    fun labelOnlyJsonIsNotAValidLayout() {
        val labelOnly = JSONObject(asset("layouts/probhat-labels.json")).toString()
        val error = LayoutImport.validate(labelOnly)
        assertNotNull("label-only JSON must be rejected", error)
        assertTrue("error should explain the missing layout object: $error",
            error!!.contains("layout", ignoreCase = true))
    }

    @Test
    fun validOpenBanglaLayoutPassesValidation() {
        assertNull(LayoutImport.validate(asset("riti/Probhat.json")))
    }

    @Test
    fun layoutWithoutNormalKeysIsRejected() {
        val json = """{"layout":{"Key_q_AltGr":"x","Key_w_AltGr":"y"}}"""
        assertNotNull(LayoutImport.validate(json))
    }

    @Test
    fun oversizeLayoutIsRejected() {
        val json = "{\"layout\":{\"Key_q_Normal\":\"${"x".repeat(LayoutImport.MAX_JSON_CHARS)}\"}}"
        assertNotNull(LayoutImport.validate(json))
    }

    private companion object {
        val PUNCT_NAMES = mapOf(
            "Ampersand" to "&", "Apostrophe" to "'", "Asterisk" to "*", "At" to "@",
            "BackSlash" to "\\", "Bar" to "|", "BraceLeft" to "{", "BraceRight" to "}",
            "BracketLeft" to "[", "BracketRight" to "]", "Circum" to "^", "Colon" to ":",
            "Comma" to ",", "Dollar" to "$", "Equals" to "=", "Exclaim" to "!",
            "Grave" to "`", "Greater" to ">", "Hash" to "#", "Less" to "<", "Minus" to "-",
            "ParenLeft" to "(", "ParenRight" to ")", "Percent" to "%", "Period" to ".",
            "Plus" to "+", "Question" to "?", "Quote" to "\"", "Semicolon" to ";",
            "Slash" to "/", "Tilde" to "~", "UnderScore" to "_"
        )
    }
}
