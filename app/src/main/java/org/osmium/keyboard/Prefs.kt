package org.osmium.keyboard

import android.content.Context
import android.content.SharedPreferences

/** User preferences for Osmium. Stored locally only. */
object Prefs {
    private const val FILE = "osmium_prefs"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Which Bangla typing scheme is active: "avro" or "probhat". */
    fun banglaScheme(context: Context): String = sp(context).getString(KEY_SCHEME, "avro")!!

    fun setBanglaScheme(context: Context, scheme: String) {
        sp(context).edit().putString(KEY_SCHEME, scheme).apply()
    }

    fun hapticsEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_HAPTICS, true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    /** Default incognito preference: when true, learning stays off even in normal mode. */
    fun incognitoByDefault(context: Context): Boolean = sp(context).getBoolean(KEY_INCOGNITO, false)

    fun setIncognitoByDefault(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_INCOGNITO, value).apply()
    }

    /** Recently used emoji (newest first). */
    fun emojiRecents(context: Context): List<String> {
        val raw = sp(context).getString(KEY_EMOJI_RECENTS, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun addEmojiRecent(context: Context, emoji: String) {
        val list = listOf(emoji) + emojiRecents(context).filter { it != emoji }
        sp(context).edit().putString(KEY_EMOJI_RECENTS, list.take(24).joinToString("\n")).apply()
    }

    private const val KEY_SCHEME = "bangla_scheme"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_INCOGNITO = "incognito_by_default"
    private const val KEY_EMOJI_RECENTS = "emoji_recents"
}
