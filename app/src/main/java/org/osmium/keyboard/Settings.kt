package org.osmium.keyboard

import android.content.Context

/**
 * One fingerprint for every preference that changes what the input view looks
 * like or how it behaves. The IME compares this value on each input start and
 * rebuilds the entire input view when it differs.
 *
 * [Theme.key] contributes the baked-in drawing settings; the extra fields make
 * timing, feedback, glide and scheme changes part of the same rebuild signal so
 * nothing can be changed only in the settings screen and silently ignored by a
 * keyboard that is already running.
 */
object Settings {
    fun key(context: Context): String = buildString {
        append(Theme.key(context))
        append('|').append(Prefs.banglaScheme(context))
        append('|').append(Prefs.longPressMs(context))
        append('|').append(Prefs.backspaceRepeatMs(context))
        append('|').append(Prefs.hapticsEnabled(context))
        append('|').append(Prefs.hapticMs(context))
        append('|').append(Prefs.hapticStrength(context))
        append('|').append(Prefs.glideCursor(context))
        append('|').append(Prefs.glideSensitivityDp(context))
        append('|').append(Prefs.glideEdgeRepeatMs(context))
        append('|').append(Prefs.swipeDeleteEnabled(context))
        append('|').append(Prefs.swipeStepDp(context))
        append('|').append(Prefs.doubleSpaceDari(context))
        append('|').append(Prefs.autoCapitalize(context))
        append('|').append(Prefs.selectedLayout(context))
        append('|').append(Prefs.incognitoByDefault(context))
    }
}
