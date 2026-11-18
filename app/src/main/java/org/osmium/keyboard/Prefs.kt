package org.osmium.keyboard

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * User preferences for Osmium. Stored locally only — no sync, no analytics.
 *
 * All UI-affecting values are read live where possible; the IME rebuilds its
 * input view when [Settings.key] changes (see Settings.kt).
 */
object Prefs {
    private const val FILE = "osmium_prefs"

    fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // Typing scheme
    // ------------------------------------------------------------------

    /** Which Bangla typing scheme is active: "avro" or "probhat". */
    fun banglaScheme(context: Context): String = sp(context).getString(KEY_SCHEME, "avro")!!

    fun setBanglaScheme(context: Context, scheme: String) {
        sp(context).edit().putString(KEY_SCHEME, scheme).apply()
    }

    // ------------------------------------------------------------------
    // Feedback
    // ------------------------------------------------------------------

    fun hapticsEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_HAPTICS, true)
    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    /** Vibration length in ms. */
    fun hapticMs(context: Context): Int = sp(context).getInt(KEY_HAPTIC_MS, 10)
    fun setHapticMs(context: Context, ms: Int) {
        sp(context).edit().putInt(KEY_HAPTIC_MS, ms).apply()
    }

    /** Vibration amplitude 1..255. */
    fun hapticStrength(context: Context): Int = sp(context).getInt(KEY_HAPTIC_STRENGTH, 120)
    fun setHapticStrength(context: Context, amp: Int) {
        sp(context).edit().putInt(KEY_HAPTIC_STRENGTH, amp).apply()
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /** Long-press delay before a popup opens, in ms. */
    fun longPressMs(context: Context): Int = sp(context).getInt(KEY_LONG_PRESS_MS, 350)
    fun setLongPressMs(context: Context, ms: Int) {
        sp(context).edit().putInt(KEY_LONG_PRESS_MS, ms).apply()
    }

    /** Backspace auto-repeat interval while held, in ms. */
    fun backspaceRepeatMs(context: Context): Int = sp(context).getInt(KEY_REPEAT_MS, 60)
    fun setBackspaceRepeatMs(context: Context, ms: Int) {
        sp(context).edit().putInt(KEY_REPEAT_MS, ms).apply()
    }

    /** Double-tap space inserts a দাঁড়ি (।) instead of a plain space. */
    fun doubleSpaceDari(context: Context): Boolean = sp(context).getBoolean(KEY_DOUBLE_SPACE, false)
    fun setDoubleSpaceDari(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_DOUBLE_SPACE, value).apply()
    }

    /** Auto-capitalize sentence starts in English mode. */
    fun autoCapitalize(context: Context): Boolean = sp(context).getBoolean(KEY_AUTOCAP, true)
    fun setAutoCapitalize(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTOCAP, value).apply()
    }

    /** Glide-on-spacebar to move the text cursor. */
    fun glideCursor(context: Context): Boolean = sp(context).getBoolean(KEY_GLIDE_CURSOR, true)
    fun setGlideCursor(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_GLIDE_CURSOR, value).apply()
    }

    /** Horizontal distance (dp) one character of cursor-glide costs. */
    fun glideSensitivityDp(context: Context): Int = sp(context).getInt(KEY_GLIDE_SENS, 22)
    fun setGlideSensitivityDp(context: Context, dp: Int) {
        sp(context).edit().putInt(KEY_GLIDE_SENS, dp).apply()
    }

    /** Swipe gestures on backspace: left deletes a word, down deletes to line start. */
    fun swipeDeleteEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_SWIPE_DELETE_ENABLED, true)
    fun setSwipeDeleteEnabled(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_SWIPE_DELETE_ENABLED, value).apply()
    }

    /** Distance (dp) per backspace swipe step: gesture threshold and words per swipe. */
    fun swipeStepDp(context: Context): Int = sp(context).getInt(KEY_SWIPE_STEP_DP, 60)
    fun setSwipeStepDp(context: Context, dp: Int) {
        sp(context).edit().putInt(KEY_SWIPE_STEP_DP, dp).apply()
    }

    // ------------------------------------------------------------------
    // Appearance
    // ------------------------------------------------------------------

    /** Keyboard height scale, 80..140 (%). */
    fun keyHeightScale(context: Context): Int = sp(context).getInt(KEY_HEIGHT_SCALE, 100)
    fun setKeyHeightScale(context: Context, percent: Int) {
        sp(context).edit().putInt(KEY_HEIGHT_SCALE, percent).apply()
    }

    /** Key corner radius in dp. */
    fun keyRadiusDp(context: Context): Int = sp(context).getInt(KEY_RADIUS_DP, 9)
    fun setKeyRadiusDp(context: Context, dp: Int) {
        sp(context).edit().putInt(KEY_RADIUS_DP, dp).apply()
    }

    /** Key label font scale in %. */
    fun keyFontScale(context: Context): Int = sp(context).getInt(KEY_FONT_SCALE, 100)
    fun setKeyFontScale(context: Context, percent: Int) {
        sp(context).edit().putInt(KEY_FONT_SCALE, percent).apply()
    }

    /** Background image path (in filesDir) or empty. */
    fun bgImagePath(context: Context): String = sp(context).getString(KEY_BG_IMAGE, "")!!
    fun setBgImagePath(context: Context, path: String) {
        sp(context).edit().putString(KEY_BG_IMAGE, path).apply()
    }

    /** Darkening overlay on the background image, 0..90 (%). */
    fun bgImageDim(context: Context): Int = sp(context).getInt(KEY_BG_DIM, 35)
    fun setBgImageDim(context: Context, percent: Int) {
        sp(context).edit().putInt(KEY_BG_DIM, percent).apply()
    }

    /** Show the mode name on the spacebar. */
    fun spacebarLabel(context: Context): Boolean = sp(context).getBoolean(KEY_SPACE_LABEL, true)
    fun setSpacebarLabel(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_SPACE_LABEL, value).apply()
    }

    /** Number row visible from the start, every session. */
    fun numberRowAlways(context: Context): Boolean = sp(context).getBoolean(KEY_NUMBER_ALWAYS, false)
    fun setNumberRowAlways(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_NUMBER_ALWAYS, value).apply()
    }

    /** Default incognito preference: when true, learning stays off even in normal mode. */
    fun incognitoByDefault(context: Context): Boolean = sp(context).getBoolean(KEY_INCOGNITO, false)
    fun setIncognitoByDefault(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_INCOGNITO, value).apply()
    }

    // ------------------------------------------------------------------
    // Custom theme colours (empty string = fall back to the base palette)
    // ------------------------------------------------------------------

    private val COLOR_KEYS = listOf(
        "custom_color_keyboard_bg", "custom_color_key", "custom_color_text",
        "custom_color_accent", "custom_color_bar", "custom_color_panel"
    )

    fun customColor(context: Context, key: String): Int? =
        sp(context).getString(key, "")!!.takeIf { it.length >= 6 }?.let {
            try {
                val argb = it.toLong(16).toInt() or 0xFF000000.toInt()
                argb
            } catch (_: Exception) { null }
        }

    fun setCustomColor(context: Context, key: String, argb: Int?) {
        val value = argb?.let { Integer.toHexString(it).takeLast(6) } ?: ""
        sp(context).edit().putString(key, value).apply()
    }

    fun customColorKeys(): List<String> = COLOR_KEYS

    // ------------------------------------------------------------------
    // Clipboard history (local only)
    // ------------------------------------------------------------------

    /** Clipboard history: list of (text, pinned), newest first. */
    fun clipboardItems(context: Context): List<ClipboardItem> {
        val raw = sp(context).getString(KEY_CLIPBOARD, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ClipboardItem(text = o.optString("t"), pinned = o.optBoolean("p", false))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class ClipboardItem(val text: String, val pinned: Boolean)

    /** Adds a copied text at the front (deduped). Trims history. */
    fun addClipboardItem(context: Context, text: String) {
        val existing = clipboardItems(context)
        val filtered = existing.filter { it.text != text }
        val pinned = filtered.filter { it.pinned }.take(40)
        val unpinned = listOf(ClipboardItem(text, false)) +
                filtered.filter { !it.pinned }.take(CLIPBOARD_MAX - 1)
        saveClipboard(context, pinned + unpinned)
    }

    fun toggleClipboardPin(context: Context, index: Int) {
        val items = clipboardItems(context).toMutableList()
        items.getOrNull(index)?.let {
            items[index] = it.copy(pinned = !it.pinned)
            saveClipboard(context, items)
        }
    }

    fun removeClipboardItem(context: Context, index: Int) {
        val items = clipboardItems(context).toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            saveClipboard(context, items)
        }
    }

    fun clearClipboardHistory(context: Context) {
        saveClipboard(context, clipboardItems(context).filter { it.pinned })
    }

    private fun saveClipboard(context: Context, items: List<ClipboardItem>) {
        val arr = org.json.JSONArray()
        for (item in items.take(CLIPBOARD_MAX + 40)) {
            arr.put(org.json.JSONObject().put("t", item.text).put("p", item.pinned))
        }
        sp(context).edit().putString(KEY_CLIPBOARD, arr.toString()).apply()
    }

    private const val CLIPBOARD_MAX = 30

    // ------------------------------------------------------------------
    // Suggestion-bar toolbar visibility
    // ------------------------------------------------------------------

    fun showClipboardChip(context: Context): Boolean =
        sp(context).getBoolean(KEY_TOOL_CLIP, true)
    fun setShowClipboardChip(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_TOOL_CLIP, value).apply()
    }

    fun showNumberToggle(context: Context): Boolean =
        sp(context).getBoolean(KEY_TOOL_NUM, true)
    fun setShowNumberToggle(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_TOOL_NUM, value).apply()
    }

    fun showSettingsButton(context: Context): Boolean =
        sp(context).getBoolean(KEY_TOOL_SET, true)
    fun setShowSettingsButton(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_TOOL_SET, value).apply()
    }

    fun showNavButton(context: Context): Boolean =
        sp(context).getBoolean(KEY_TOOL_NAV, true)
    fun setShowNavButton(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_TOOL_NAV, value).apply()
    }

    // ------------------------------------------------------------------
    // User-installed keyboard layouts
    // ------------------------------------------------------------------

    /** Selected fixed-layout: "probhat" (bundled) or a file name in layouts/. */
    fun selectedLayout(context: Context): String =
        sp(context).getString(KEY_LAYOUT, "probhat")!!
    fun setSelectedLayout(context: Context, name: String) {
        sp(context).edit().putString(KEY_LAYOUT, name).apply()
    }

    /** Directory where imported layout JSON files live. */
    fun layoutsDir(context: Context): File {
        val dir = File(context.filesDir, "layouts")
        dir.mkdirs()
        return dir
    }

    fun installedLayouts(context: Context): List<String> =
        layoutsDir(context).listFiles { f -> f.name.endsWith(".json") }
            ?.map { it.name }?.sorted() ?: emptyList()

    // ------------------------------------------------------------------
    // Recently used emoji (newest first)
    // ------------------------------------------------------------------

    fun emojiRecents(context: Context): List<String> {
        val raw = sp(context).getString(KEY_EMOJI_RECENTS, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun addEmojiRecent(context: Context, emoji: String) {
        val list = listOf(emoji) + emojiRecents(context).filter { it != emoji }
        sp(context).edit().putString(KEY_EMOJI_RECENTS, list.take(24).joinToString("\n")).apply()
    }

    // ------------------------------------------------------------------
    // Backup: export / import the whole preference file as JSON
    // ------------------------------------------------------------------

    fun exportJson(context: Context): String {
        val root = org.json.JSONObject()
        root.put("osmium_settings_version", 1)
        for ((k, v) in sp(context).all) {
            when (v) {
                is Boolean -> root.put(k, v)
                is Int -> root.put(k, v.toLong())
                is Long -> root.put(k, v)
                is Float -> root.put(k, v.toDouble())
                is String -> root.put(k, v)
            }
        }
        return root.toString(2)
    }

    /**
     * Applies a settings JSON. Returns the number of keys applied.
     *
     * Every entry goes through [SettingsSchema.normalize] first: unknown keys
     * and wrong-typed values are skipped, and numbers are clamped to the same
     * ranges the settings sliders use. This keeps a hand-edited raw JSON file
     * from writing (for example) a String into an Int preference and bricking
     * the keyboard with a ClassCastException on the next read.
     */
    fun importJson(context: Context, json: String): Int {
        val root = org.json.JSONObject(json)
        val editor = sp(context).edit()
        var count = 0
        for (key in root.keys()) {
            val normalized = SettingsSchema.normalize(key, root.get(key)) ?: continue
            val (k, v) = normalized
            when (v) {
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
                is String -> editor.putString(k, v)
                else -> continue
            }
            count++
        }
        editor.apply()
        return count
    }

    private const val KEY_SCHEME = "bangla_scheme"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_HAPTIC_MS = "haptic_ms"
    private const val KEY_HAPTIC_STRENGTH = "haptic_strength"
    private const val KEY_INCOGNITO = "incognito_by_default"
    private const val KEY_EMOJI_RECENTS = "emoji_recents"
    private const val KEY_LONG_PRESS_MS = "long_press_ms"
    private const val KEY_REPEAT_MS = "backspace_repeat_ms"
    private const val KEY_DOUBLE_SPACE = "double_space_dari"
    private const val KEY_AUTOCAP = "auto_capitalize"
    private const val KEY_GLIDE_CURSOR = "glide_cursor_enabled"
    private const val KEY_GLIDE_SENS = "glide_sensitivity_dp"
    private const val KEY_SWIPE_DELETE_ENABLED = "swipe_delete_enabled"
    private const val KEY_SWIPE_STEP_DP = "swipe_step_dp"
    private const val KEY_HEIGHT_SCALE = "key_height_scale"
    private const val KEY_RADIUS_DP = "key_radius_dp"
    private const val KEY_FONT_SCALE = "key_font_scale"
    private const val KEY_BG_IMAGE = "bg_image_path"
    private const val KEY_BG_DIM = "bg_image_dim"
    private const val KEY_SPACE_LABEL = "spacebar_label"
    private const val KEY_NUMBER_ALWAYS = "number_row_always"
    private const val KEY_C_BG = "custom_color_keyboard_bg"
    private const val KEY_C_KEY = "custom_color_key"
    private const val KEY_C_TEXT = "custom_color_text"
    private const val KEY_C_ACCENT = "custom_color_accent"
    private const val KEY_C_BAR = "custom_color_bar"
    private const val KEY_C_PANEL = "custom_color_panel"
    private const val KEY_CLIPBOARD = "clipboard_items"
    private const val KEY_TOOL_CLIP = "toolbar_clipboard"
    private const val KEY_TOOL_NUM = "toolbar_number"
    private const val KEY_TOOL_SET = "toolbar_settings"
    private const val KEY_TOOL_NAV = "toolbar_nav"
    private const val KEY_LAYOUT = "selected_layout"
}
