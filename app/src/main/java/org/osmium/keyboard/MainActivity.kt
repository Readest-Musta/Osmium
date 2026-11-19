package org.osmium.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.io.File

/**
 * Setup + settings screen, also reachable from the suggestion bar's ⚙.
 * Sections: Setup, Appearance, Behaviour, Backup, Diagnostics.
 *
 * Changes apply to the keyboard the next time it opens (the IME rebuilds its
 * input view when its settings fingerprint changes).
 */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private var preview: KeyboardPreview? = null
    private var enableButton: Button? = null
    private var pickButton: Button? = null
    private val pad by lazy { (16 * resources.displayMetrics.density).toInt() }

    private companion object {
        const val REQ_IMAGE = 3001
        const val REQ_EXPORT = 3002
        const val REQ_IMPORT = 3003
        const val REQ_LAYOUT = 3004

        /** Colour presets offered in the custom-theme picker. */
        val SWATCHES = listOf(
            0x17191F, 0x262A35, 0x3A4050, 0x1E2530,
            0xFDFDFE, 0xE4E7ED, 0xDFE3EC, 0x1B1E26,
            0x4F8CFF, 0x2E6BDF, 0x1B5E20, 0x388E3C,
            0xE53935, 0xD81B60, 0xFFB300, 0x00897B,
            0x5E35B1, 0x7E57C2, 0x000000, 0xFFFFFF,
        )

        val COLOR_TARGETS = listOf(
            "custom_color_keyboard_bg" to "Background",
            "custom_color_key" to "Keys",
            "custom_color_text" to "Key text",
            "custom_color_accent" to "Accent",
            "custom_color_bar" to "Suggestion bar",
            "custom_color_panel" to "Panels",
        )
    }

    /** Custom-colour swatch buttons, keyed by preference key. */
    private val swatchButtons = mutableMapOf<String, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
        }
        buildAppBar()
        buildPrivacyCard()
        buildSetupSection()
        add(TextView(this).apply {
            text = "Live preview"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF4F8CFF.toInt())
            setPadding(pad, pad, pad, pad / 4)
        })
        preview = KeyboardPreview(this).also { add(it) }
        add(note("Colours and sizing update here instantly; the keyboard picks them up the next time it opens."))
        buildAppearanceSection()
        buildBehaviourSection()
        buildLayoutsSection()
        buildBackupSection()
        buildDiagnosticsSection()
        setContentView(
            android.widget.ScrollView(this).apply {
                addView(root, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Settings (including a raw-JSON import or a theme change made while
        // we were in the background) may have replaced the colours since the
        // last bind. Refresh the swatches from the live prefs/palette.
        if (::root.isInitialized) {
            bindColorSwatches()
            preview?.refresh()
            refreshSetupState()
        }
    }

    /** True when Osmium appears in the system's enabled-IME list. */
    private fun isOsmiumEnabled(): Boolean {
        val id = ComponentName(this, OsmiumImeService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
            ?: return false
        return enabled.split(':').any { it.equals(id, ignoreCase = true) }
    }

    /** True when Osmium is the currently selected input method. */
    private fun isOsmiumDefault(): Boolean {
        val id = ComponentName(this, OsmiumImeService::class.java).flattenToString()
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return false
        return current.equals(id, ignoreCase = true)
    }

    /** Reflects real setup progress instead of showing the same two static buttons. */
    private fun refreshSetupState() {
        enableButton?.text = if (isOsmiumEnabled()) {
            "✓ Osmium is enabled on this device"
        } else {
            "① Enable Osmium in system settings"
        }
        pickButton?.text = if (isOsmiumDefault()) {
            "✓ Osmium is the active keyboard"
        } else {
            "② Switch to Osmium now"
        }
    }

    /** Fallback swatch colour for an unset custom colour in the active palette. */
    private fun fallbackColor(key: String): Int = Theme.palette(this).let {
        when (key) {
            "custom_color_keyboard_bg" -> it.keyboardBg
            "custom_color_key" -> it.keyNormal
            "custom_color_text" -> it.keyText
            "custom_color_accent" -> it.accent
            "custom_color_bar" -> it.barBg
            else -> it.panelBg
        }
    }

    /** Paints and re-wires every swatch button from current preferences. */
    private fun bindColorSwatches() {
        for ((key, name) in COLOR_TARGETS) {
            val button = swatchButtons[key] ?: continue
            val current = Prefs.customColor(this, key) ?: fallbackColor(key)
            val lum = (0.299 * Color.red(current) +
                    0.587 * Color.green(current) +
                    0.114 * Color.blue(current)) / 255.0
            button.setTextColor(if (lum > 0.5) Color.BLACK else Color.WHITE)
            button.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(current)
                cornerRadius = 12f * resources.displayMetrics.density
            }
            button.setOnClickListener { pickColor(key, name, current) }
        }
    }

    // ------------------------------------------------------------------
    // Section builders
    // ------------------------------------------------------------------

    private fun header(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF4F8CFF.toInt())
        setPadding(pad, pad, pad, pad / 4)
    }

    private fun note(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF9AA0B0.toInt())
        setPadding(pad, pad / 2, pad, pad)
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(pad, pad, pad, pad / 4)
    }

    /** When non-null, newly added views go inside this card instead of the root. */
    private var cardHost: LinearLayout? = null

    private fun add(v: View) {
        (cardHost ?: root).addView(v)
    }

    /** Starts a themed card with a compact title. Pair with [endCard]. */
    private fun beginCard(title: String) {
        val pal = Theme.palette(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(pal.panelBg)
                cornerRadius = 16f * resources.displayMetrics.density
            }
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(pad, pad / 3, pad, 0) })
        cardHost = card
        add(TextView(this).apply {
            text = title.uppercase()
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pal.accent)
            setPadding(0, 0, 0, pad / 3)
        })
    }

    private fun endCard() {
        cardHost = null
    }

    private fun switchSetting(text: String, current: Boolean, onChange: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            this.text = text
            isChecked = current
            setPadding(pad, 0, pad, 0)
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }

    /** Label + seek bar row. `format` renders the live value into the label. */
    private fun sliderSetting(
        title: String,
        min: Int, max: Int, current: Int,
        format: (Int) -> String,
        onChange: (Int) -> Unit,
    ): View {
        val valueLabel = TextView(this).apply {
            textSize = 14f
            setPadding(pad, pad / 4, pad, 0)
            text = "${title}: ${format(current)}"
        }
        val bar = SeekBar(this).apply {
            setPadding(pad, 0, pad, 0)
            setMax(max - min)
            setProgress(current - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) valueLabel.text = "${title}: ${format(progress + min)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    onChange(sb!!.progress + min)
                }
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(valueLabel)
            addView(bar)
        }
    }

    /** Reads the version from the installed package (no generated BuildConfig here). */
    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    } catch (_: Throwable) {
        "unknown"
    }

    /** Gives the settings screen an identity — it currently opens as an anonymous list. */
    private fun buildAppBar() {
        add(TextView(this).apply {
            text = "Osmium  🔘  ·  ওসমিয়াম"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFE8EAF0.toInt())
            setPadding(pad, pad / 2, pad, 0)
        })
        add(TextView(this).apply {
            text = "Privacy-first Bengali keyboard · v${appVersion()}"
            textSize = 12f
            setTextColor(0xFF9AA0B0.toInt())
            setPadding(pad, 0, pad, pad / 2)
        })
    }

    /** Puts the project's strongest differentiator front and centre. */
    private fun buildPrivacyCard() {
        val pal = Theme.palette(this)
        add(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(pal.panelBg)
                cornerRadius = 16f * resources.displayMetrics.density
            }
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Privacy first"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(pal.accent)
            })
            addView(TextView(context).apply {
                text = "No INTERNET permission. No analytics. Learning and clipboard history "
                    "stay on this device and are never uploaded."
                textSize = 13f
                setTextColor(pal.keyText)
                setPadding(0, pad / 3, 0, pad / 3)
            })
            addView(switchSetting(
                "Incognito by default (no learning, no clipboard capture)",
                Prefs.incognitoByDefault(context),
            ) { Prefs.setIncognitoByDefault(context, it) })
        })
    }

    private fun buildSetupSection() {
        beginCard("Setup")
        val enable = Button(this).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        val pick = Button(this).apply {
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        enableButton = enable
        pickButton = pick
        add(enable); add(pick)
        refreshSetupState()

        add(label("Bangla typing scheme (for the অ mode):"))
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(pad, 0, pad, pad)
            val avro = RadioButton(this@MainActivity).apply {
                text = "Avro Phonetic (অভ্র ফোনেটিক)"; id = 1001
            }
            val probhat = RadioButton(this@MainActivity).apply {
                text = "Probhat (প্রভাত)"; id = 1002
            }
            addView(avro); addView(probhat)
            when (Prefs.banglaScheme(this@MainActivity)) {
                "probhat" -> check(1002)
                else -> check(1001)
            }
            setOnCheckedChangeListener { _, checkedId ->
                Prefs.setBanglaScheme(this@MainActivity,
                    if (checkedId == 1002) "probhat" else "avro")
            }
        }
        add(radioGroup)
        endCard()
    }

    private fun buildAppearanceSection() {
        beginCard("Appearance")
        add(label("Keyboard theme:"))
        val themeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(pad, 0, pad, 0)
            val items = listOf(
                "System" to (2001 to "system"),
                "Dark" to (2002 to "dark"),
                "Light" to (2003 to "light"),
                "Custom" to (2004 to "custom"),
            )
            for ((text, pair) in items) {
                addView(RadioButton(this@MainActivity).apply {
                    this.text = text
                    id = pair.first
                })
            }
            when (Theme.mode(this@MainActivity)) {
                "dark" -> check(2002)
                "light" -> check(2003)
                "custom" -> check(2004)
                else -> check(2001)
            }
            setOnCheckedChangeListener { _, checkedId ->
                val m = when (checkedId) {
                    2002 -> "dark"
                    2003 -> "light"
                    2004 -> "custom"
                    else -> "system"
                }
                Theme.setMode(this@MainActivity, m)
                // Rebuild the activity so its own widgets (swatch fallbacks in
                // particular) pick up the new palette immediately.
                recreate()
            }
        }
        add(themeGroup)
        add(note("Custom theme uses the colours below; unset colours fall back to the system palette."))

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, 0, pad, 0)
        }
        for ((key, name) in COLOR_TARGETS) {
            val button = Button(this).apply {
                text = name
                textSize = 11f
            }
            swatchButtons[key] = button
            colorRow.addView(button,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(2, 2, 2, 2)
                })
        }
        add(colorRow)
        bindColorSwatches()

        val clearColors = Button(this).apply {
            text = "Reset custom colours"
            setOnClickListener {
                for (key in Prefs.customColorKeys()) Prefs.setCustomColor(this@MainActivity, key, null)
            }
        }
        add(clearColors)

        add(sliderSetting("Keyboard height", 80, 140, Prefs.keyHeightScale(this),
            { "$it%" }, { Prefs.setKeyHeightScale(this, it); preview?.refresh() }))
        add(sliderSetting("Key corner radius", 0, 18, Prefs.keyRadiusDp(this),
            { "${it}dp" }, { Prefs.setKeyRadiusDp(this, it); preview?.refresh() }))
        add(sliderSetting("Key text size", 80, 150, Prefs.keyFontScale(this),
            { "$it%" }, { Prefs.setKeyFontScale(this, it); preview?.refresh() }))
        val oneHanded = Button(this).apply {
            fun relabel() {
                text = "One-handed: " + when (Prefs.oneHandedMode(context)) {
                    "left" -> "left"
                    "right" -> "right"
                    else -> "off"
                }
            }
            relabel()
            setOnClickListener {
                val next = when (Prefs.oneHandedMode(context)) {
                    "off" -> "left"
                    "left" -> "right"
                    else -> "off"
                }
                Prefs.setOneHandedMode(context, next)
                relabel()
            }
        }
        add(oneHanded)
        add(switchSetting("Number row always visible", Prefs.numberRowAlways(this),
            { Prefs.setNumberRowAlways(this, it) }))
        add(switchSetting("Show mode name on spacebar", Prefs.spacebarLabel(this),
            { Prefs.setSpacebarLabel(this, it) }))
        add(label("Suggestion-bar controls:"))
        add(switchSetting("Show ১২৩ (number row) button", Prefs.showNumberToggle(this),
            { Prefs.setShowNumberToggle(this, it) }))
        add(switchSetting("Show 📋 clipboard button", Prefs.showClipboardChip(this),
            { Prefs.setShowClipboardChip(this, it) }))
        add(switchSetting("Show # symbols button", Prefs.showSymbolsButton(this),
            { Prefs.setShowSymbolsButton(this, it) }))
        add(switchSetting("Show ⚙ settings button", Prefs.showSettingsButton(this),
            { Prefs.setShowSettingsButton(this, it) }))
        add(switchSetting("Show ⇱ navigation button", Prefs.showNavButton(this),
            { Prefs.setShowNavButton(this, it) }))

        add(label("Background image"))
        val bgRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, 0, pad, 0) }
        bgRow.addView(Button(this).apply {
            text = "Choose image…"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                startActivityForResult(intent, REQ_IMAGE)
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bgRow.addView(Button(this).apply {
            text = "Remove"
            setOnClickListener {
                val old = Prefs.bgImagePath(this@MainActivity)
                if (old.isNotEmpty()) File(old).delete()
                Prefs.setBgImagePath(this@MainActivity, "")
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        add(bgRow)
        add(sliderSetting("Background darkening", 0, 90, Prefs.bgImageDim(this),
            { "$it%" }, { Prefs.setBgImageDim(this, it) }))
        endCard()
    }

    private fun buildBehaviourSection() {
        beginCard("Behaviour")
        add(switchSetting("Key press vibration", Prefs.hapticsEnabled(this),
            { Prefs.setHapticsEnabled(this, it) }))
        add(sliderSetting("Vibration length", 2, 40, Prefs.hapticMs(this),
            { "${it}ms" }, { Prefs.setHapticMs(this, it) }))
        add(sliderSetting("Vibration strength", 20, 255, Prefs.hapticStrength(this),
            { it.toString() }, { Prefs.setHapticStrength(this, it) }))
        add(sliderSetting("Long-press delay", 150, 900, Prefs.longPressMs(this),
            { "${it}ms" }, { Prefs.setLongPressMs(this, it) }))
        add(sliderSetting("Backspace repeat speed", 20, 150, Prefs.backspaceRepeatMs(this),
            { "every ${it}ms" }, { Prefs.setBackspaceRepeatMs(this, it) }))
        add(switchSetting("Double-space types দাঁড়ি (।)", Prefs.doubleSpaceDari(this),
            { Prefs.setDoubleSpaceDari(this, it) }))
        add(switchSetting("Auto-capitalize sentences (English)", Prefs.autoCapitalize(this),
            { Prefs.setAutoCapitalize(this, it) }))
        add(switchSetting("Spacebar glide moves the cursor", Prefs.glideCursor(this),
            { Prefs.setGlideCursor(this, it) }))
        add(sliderSetting("Glide sensitivity", 10, 60, Prefs.glideSensitivityDp(this),
            { "${it}dp per character" }, { Prefs.setGlideSensitivityDp(this, it) }))
        add(sliderSetting("Glide edge speed", 20, 150, Prefs.glideEdgeRepeatMs(this),
            { "every ${it}ms" }, { Prefs.setGlideEdgeRepeatMs(this, it) }))
        add(switchSetting("Swipe left on ⌫ deletes a word", Prefs.swipeDeleteEnabled(this),
            { Prefs.setSwipeDeleteEnabled(this, it) }))
        add(sliderSetting("Swipe distance for word delete", 30, 100, Prefs.swipeStepDp(this),
            { "${it}dp" }, { Prefs.setSwipeStepDp(this, it) }))
        endCard()
    }

    /** Custom keyboard layouts: import + selection. */
    private fun buildLayoutsSection() {
        beginCard("Keyboard layouts")
        add(note("The প্ (fixed) mode uses the selected layout: bundled Probhat or National Jatiya, " +
                "or any imported OpenBangla layout JSON (Key_X_Normal entries). Long-press popups, " +
                "AltGr layer and labels are derived from it."))

        val layouts = Prefs.installedLayouts(this)
        val selected = Prefs.selectedLayout(this)
        val idToName = mutableMapOf<Int, String>()
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(pad, 0, pad, 0)
            val bundled = listOf("probhat" to "প্রভাত (bundled)", "jatiya" to "জাতীয় / National (bundled)")
            bundled.forEachIndexed { i, (name, label) ->
                val id = 4100 + i
                idToName[id] = name
                addView(RadioButton(this@MainActivity).apply {
                    text = label
                    this.id = id
                    isChecked = selected == name
                })
            }
            layouts.forEachIndexed { i, name ->
                val id = 4100 + bundled.size + i
                idToName[id] = name
                addView(RadioButton(this@MainActivity).apply {
                    text = name.removeSuffix(".json")
                    this.id = id
                    isChecked = selected == name
                })
            }
            setOnCheckedChangeListener { _, checkedId ->
                val name = idToName[checkedId] ?: return@setOnCheckedChangeListener
                Prefs.setSelectedLayout(this@MainActivity, name)
            }
        }
        add(group)

        val importButton = Button(this).apply {
            text = "Import layout file…"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                startActivityForResult(intent, REQ_LAYOUT)
            }
        }
        add(importButton)

        val deleteButton = Button(this).apply {
            text = "Delete selected custom layout"
            setOnClickListener {
                val name = Prefs.selectedLayout(this@MainActivity)
                if (name == "probhat") {
                    toast("The bundled Probhat layout cannot be deleted")
                } else {
                    File(Prefs.layoutsDir(this@MainActivity), name).delete()
                    Prefs.setSelectedLayout(this@MainActivity, "probhat")
                    recreate()
                }
            }
        }
        add(deleteButton)

        // Recovery for a layout that fails to load in the engine: a permanent
        // way back to the bundled Probhat, plus one-tap undo of the last import.
        add(Button(this).apply {
            text = "Revert to bundled Probhat"
            setOnClickListener {
                Prefs.setSelectedLayout(this@MainActivity, "probhat")
                toast("Reverted to bundled Probhat — reopen the keyboard")
                recreate()
            }
        })
        val previous = Prefs.previousLayout(this)
        if (previous != "probhat" && File(Prefs.layoutsDir(this), previous).exists()) {
            add(Button(this).apply {
                text = "Undo last layout import (${previous.removeSuffix(".json")})"
                setOnClickListener {
                    Prefs.setSelectedLayout(this@MainActivity, previous)
                    toast("Restored previous layout — reopen the keyboard")
                    recreate()
                }
            })
        }
        endCard()
    }

    /**
     * Strict, atomic layout import (Task 6). Rejects label-only JSON that riti
     * cannot load, installs via temp-file + rename, and records the previously
     * selected layout so a bad import can be rolled back from Settings.
     */
    private fun importLayout(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: throw java.io.IOException("Could not read the file")
            val error = LayoutImport.validate(json)
            if (error != null) {
                toast("Layout import failed: $error")
                return
            }

            val dir = Prefs.layoutsDir(this)
            val base = (uri.lastPathSegment ?: "layout")
                .substringAfterLast('/')
                .substringAfterLast(':')
                .substringBeforeLast(".json")
                .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                .take(40)
                .ifEmpty { "layout" }
            var name = "$base.json"
            var n = 1
            while (File(dir, name).exists()) { n++; name = "$base-$n.json" }

            // Atomic install: never leave a half-written file the engine could
            // choke on (and never touch the previous layout until this succeeds).
            val tmp = File(dir, "$name.tmp")
            try {
                tmp.writeText(json)
                if (!tmp.renameTo(File(dir, name))) {
                    throw java.io.IOException("Could not install layout file")
                }
            } finally {
                if (tmp.exists()) tmp.delete()
            }

            Prefs.setPreviousLayout(this, Prefs.selectedLayout(this))
            Prefs.setSelectedLayout(this, name)
            toast("Layout imported — reopen the keyboard")
            recreate()
        } catch (t: Throwable) {
            toast("Layout import failed: ${t.message}")
        }
    }

    private fun buildBackupSection() {
        beginCard("Backup")
        val export = Button(this).apply {
            text = "Export all settings to a file"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "osmium-settings.json")
                startActivityForResult(intent, REQ_EXPORT)
            }
        }
        val import = Button(this).apply {
            text = "Import settings from a file"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                startActivityForResult(intent, REQ_IMPORT)
            }
        }
        add(export); add(import)
        add(note("Settings are stored only on this device. Export produces a shareable JSON file; " +
                "importing applies everything in it. Nothing is ever uploaded."))

        val advanced = Button(this).apply {
            text = "Advanced: edit raw settings JSON"
            setOnClickListener { showRawEditor() }
        }
        add(advanced)
        add(note("Full control: every Osmium preference is a JSON key here. Edit values, save, and " +
                "reopen the keyboard. Invalid values are ignored."))
        endCard()
    }

    /** Power-user raw preference editor. */
    private fun showRawEditor() {
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            setText(Prefs.exportJson(this@MainActivity))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            minLines = 6
            setPadding(24, 12, 24, 12)
        }
        AlertDialog.Builder(this)
            .setTitle("Raw settings JSON")
            .setView(android.widget.ScrollView(this).apply { addView(edit) })
            .setPositiveButton("Save") { _, _ ->
                try {
                    val count = Prefs.importJson(this, edit.text.toString())
                    toast("Applied $count settings — reopen the keyboard")
                    recreate()
                } catch (t: Throwable) {
                    toast("Not valid JSON: ${t.message}")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildDiagnosticsSection() {
        beginCard("Diagnostics")
        val diagView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(pad, 0, pad, pad)
        }
        fun render() {
            val sb = StringBuilder()
            for (check in Diagnostics.run(this@MainActivity)) {
                sb.append(if (check.ok) "✅ " else "❌ ")
                    .append(check.name)
                    .append("\n")
                    .append("     ").append(check.detail).append("\n\n")
            }
            diagView.text = sb.toString()
        }
        render()
        add(diagView)
        add(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply {
                text = "Refresh"
                setOnClickListener { render() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "Copy report"
                setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Osmium diagnostics", diagView.text))
                    toast("Diagnostics copied")
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        add(note(
            "Learning happens only on this device — your typing never leaves your phone, and " +
            "incognito mode turns it off completely. Osmium has no internet permission."
        ))
        endCard()
    }

    // ------------------------------------------------------------------
    // Colour picker dialog
    // ------------------------------------------------------------------

    private fun pickColor(key: String, name: String, current: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }
        val hexInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "#RRGGBB"
            setText(String.format("#%06X", 0xFFFFFF and current))
            setSingleLine(true)
        }
        container.addView(TextView(this).apply {
            text = "Tap a preset, or type a hex value:"
            textSize = 13f
        })
        container.addView(hexInput)

        var chosen = current
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (chunk in SWATCHES.chunked(5)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (color in chunk) {
                row.addView(View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(color or 0xFF000000.toInt())
                        cornerRadius = 10f * resources.displayMetrics.density
                    }
                    setOnClickListener {
                        chosen = color or 0xFF000000.toInt()
                        hexInput.setText(String.format("#%06X", 0xFFFFFF and chosen))
                    }
                }, LinearLayout.LayoutParams(0, (36 * resources.displayMetrics.density).toInt(), 1f).apply {
                    setMargins(4, 4, 4, 4)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        container.addView(grid)

        AlertDialog.Builder(this)
            .setTitle(name)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val text = hexInput.text.toString().trim().removePrefix("#")
                if (text.length == 6) {
                    try {
                        Prefs.setCustomColor(this, key, text.toLong(16).toInt() or 0xFF000000.toInt())
                        recreate() // refresh the swatch buttons
                    } catch (_: Exception) {
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------
    // File picking results
    // ------------------------------------------------------------------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri: Uri = data.data!!
        when (requestCode) {
            REQ_IMAGE -> try {
                val dir = File(filesDir, "custom")
                dir.mkdirs()
                val target = File(dir, "keyboard_bg.img")
                contentResolver.openInputStream(uri)!!.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                Prefs.setBgImagePath(this, target.absolutePath)
            } catch (t: Throwable) {
                toast("Could not load image: ${t.message}")
            }
            REQ_EXPORT -> try {
                contentResolver.openOutputStream(uri)!!.use { out ->
                    out.write(Prefs.exportJson(this).toByteArray(Charsets.UTF_8))
                }
                toast("Settings exported")
            } catch (t: Throwable) {
                toast("Export failed: ${t.message}")
            }
            REQ_IMPORT -> try {
                val json = contentResolver.openInputStream(uri)!!
                    .bufferedReader().readText()
                val count = Prefs.importJson(this, json)
                toast("Imported $count settings — reopen the keyboard")
                recreate()
            } catch (t: Throwable) {
                toast("Import failed: ${t.message}")
            }
            REQ_LAYOUT -> importLayout(uri)
        }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
