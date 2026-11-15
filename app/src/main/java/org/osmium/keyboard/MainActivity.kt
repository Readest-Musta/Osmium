package org.osmium.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView

/**
 * Simple setup + settings screen. Also reachable from the suggestion bar's ⚙.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Osmium ⚙  ওসমিয়াম"
            textSize = 24f
            setPadding(pad, pad, pad, pad / 2)
        }

        val privacy = TextView(this).apply {
            text = getString(R.string.privacy_note)
            textSize = 13f
            setTextColor(0xFF9AA0B0.toInt())
            setPadding(pad, 0, pad, pad)
        }

        val enableButton = Button(this).apply {
            text = "① Enable Osmium in system settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val pickButton = Button(this).apply {
            text = "② Switch to Osmium now"
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        val schemeLabel = TextView(this).apply {
            text = "Bangla typing scheme (for the অ mode):"
            textSize = 15f
            setPadding(pad, pad, pad, pad / 4)
        }

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(pad, 0, pad, pad)
            val avro = RadioButton(this@MainActivity).apply {
                text = "Avro Phonetic (অভ্র ফোনেটিক)"
                id = 1001
            }
            val probhat = RadioButton(this@MainActivity).apply {
                text = "Probhat (প্রভাত)"
                id = 1002
            }
            addView(avro)
            addView(probhat)
            when (Prefs.banglaScheme(this@MainActivity)) {
                "probhat" -> check(1002)
                else -> check(1001)
            }
            setOnCheckedChangeListener { _, checkedId ->
                val scheme = if (checkedId == 1002) "probhat" else "avro"
                Prefs.setBanglaScheme(this@MainActivity, scheme)
            }
        }

        val hapticsSwitch = Switch(this).apply {
            text = "Key press vibration"
            isChecked = Prefs.hapticsEnabled(this@MainActivity)
            setPadding(pad, 0, pad, 0)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setHapticsEnabled(this@MainActivity, checked)
            }
        }

        val incognitoSwitch = Switch(this).apply {
            text = "Incognito by default (no learning anywhere)"
            isChecked = Prefs.incognitoByDefault(this@MainActivity)
            setPadding(pad, pad / 2, pad, 0)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setIncognitoByDefault(this@MainActivity, checked)
            }
        }

        val learnMore = TextView(this).apply {
            text = "Learning happens only on this device — your typing never leaves your phone, and incognito mode turns it off completely. Osmium has no internet permission."
            textSize = 13f
            setTextColor(0xFF9AA0B0.toInt())
            setPadding(pad, pad, pad, pad)
        }

        val diagLabel = TextView(this).apply {
            text = "Diagnostics"
            textSize = 15f
            setPadding(pad, pad, pad, pad / 4)
        }

        val diagView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(pad, 0, pad, pad)
            text = run {
                val sb = StringBuilder()
                for (check in Diagnostics.run(this@MainActivity)) {
                    sb.append(if (check.ok) "✅ " else "❌ ")
                        .append(check.name)
                        .append(if (check.ok) "" else " — ${check.detail}")
                        .append("\n")
                        .append(if (check.ok) "     " else "     ")
                        .append(check.detail)
                        .append("\n\n")
                }
                sb.toString()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(privacy)
            addView(enableButton)
            addView(pickButton)
            addView(schemeLabel)
            addView(radioGroup)
            addView(hapticsSwitch)
            addView(incognitoSwitch)
            addView(diagLabel)
            addView(diagView)
            addView(learnMore)
        }

        setContentView(root)
    }
}
