package org.osmium.keyboard

import org.json.JSONObject

/**
 * Strict validation for user-imported keyboard layouts (Task 6).
 *
 * The IME's engine (riti) can only load a full OpenBangla layout: a top-level
 * `layout` object containing `Key_<Name>_Normal` entries. Label-only JSON (our
 * own `probhat-labels.json` format) is a *derived* asset and must never be
 * accepted as an installable layout — doing so produced a keyboard of dead keys
 * in the debug report. Validation is pure Kotlin (no Android dependencies) so it
 * is covered by [LayoutParityTest] on the JVM.
 */
object LayoutImport {

    /** Guard against pathologically large pasted/imported files. */
    const val MAX_JSON_CHARS = 1_000_000
    /** A layout with more keys than this is not a plausible keyboard layout. */
    private const val MAX_LAYOUT_KEYS = 1_000

    /**
     * Returns a human-readable reason when [json] is not a safe, loadable layout,
     * or `null` when it passes validation.
     */
    fun validate(json: String): String? {
        if (json.isBlank()) return "The file is empty"
        if (json.length > MAX_JSON_CHARS) {
            return "Layout file is too large (limit ${MAX_JSON_CHARS / 1_000} KB)"
        }

        val root = try {
            JSONObject(json)
        } catch (t: Throwable) {
            return "Invalid JSON: ${t.message ?: t.javaClass.simpleName}"
        }

        val layout = root.optJSONObject("layout")
            ?: return "Not an OpenBangla layout: missing 'layout' object"

        var normalKeys = 0
        for (key in layout.keys()) {
            if (!key.startsWith("Key_") || !key.endsWith("_Normal")) continue
            val value = layout.opt(key)
            if (value !is String) return "Invalid layout: $key must be a string"
            if (value.isNotEmpty()) normalKeys++
        }
        if (normalKeys == 0) {
            return "Not an OpenBangla layout: no non-empty Key_*_Normal entries"
        }
        if (layout.keys().asSequence().count() > MAX_LAYOUT_KEYS) {
            return "Layout has too many keys (limit $MAX_LAYOUT_KEYS)"
        }
        return null
    }
}
