package org.osmium.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Emoji picker panel. Small, fast, fully offline.
 *
 * A search field is pinned at the top; below it a horizontally scrollable tab
 * bar selects an emoji category. The SYMBOLS tab reveals a second scrollable
 * row of sub-tabs backed by [EmojiData.SYMBOL_GROUPS], with a leading recents
 * tab. Tapping any emoji or symbol records it in the matching (emoji vs symbol)
 * recents list and forwards it through [onEmojiPicked].
 */
class EmojiPanel(context: Context) : LinearLayout(context) {

    var onEmojiPicked: ((String) -> Unit)? = null

    private val grid: GridView
    private val searchBox: EditText
    private val subTabRow: HorizontalScrollView
    private val tabs = mutableListOf<TextView>()
    private val subTabs = mutableListOf<TextView>()
    private var adapter: EmojiAdapter? = null
    private var selected = EmojiCategory.SMILEYS
    private var symbolGroupIndex = 0

    /** Leading "recent symbols" sub-tab, then the fixed symbol groups. */
    private val symbolTabs: List<EmojiData.SymbolGroup> =
        listOf(EmojiData.SymbolGroup("recent", "🕘", emptyList())) + EmojiData.SYMBOL_GROUPS

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.palette(context).panelBg)

        // Search field, pinned above the tab bar.
        searchBox = EditText(context).apply {
            hint = "Search emoji & symbols"
            setSingleLine(true)
            textSize = 14f
            setTextColor(Theme.palette(context).keyText)
            setHintTextColor(Theme.palette(context).keyTextHint)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = refresh()
            })
        }
        addView(searchBox, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)))

        // Horizontally scrollable category tabs. Fixed widths: layout_weight is
        // meaningless inside a HorizontalScrollView.
        val tabBar = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (cat in EmojiCategory.values()) {
            val tab = newTab(cat.label, cat)
            tab.setOnClickListener {
                selected = cat
                // A tab tap means "show this category", not "keep filtering".
                clearSearch()
                refresh()
            }
            tabs.add(tab)
            tabBar.addView(tab, LayoutParams(dp(44), dp(44)))
        }
        addView(HorizontalScrollView(context).apply {
            addView(
                tabBar,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(44))
            )
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))

        // Sub-tabs for the SYMBOLS category; hidden for every other tab.
        val subTabBar = LinearLayout(context).apply { orientation = HORIZONTAL }
        for ((index, group) in symbolTabs.withIndex()) {
            val tab = newTab(group.label, group.id)
            tab.setOnClickListener {
                symbolGroupIndex = index
                clearSearch()
                refresh()
            }
            subTabs.add(tab)
            subTabBar.addView(tab, LayoutParams(dp(44), dp(40)))
        }
        subTabRow = HorizontalScrollView(context).apply {
            addView(
                subTabBar,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(40))
            )
        }
        addView(subTabRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))

        grid = GridView(context).apply {
            numColumns = 8
            horizontalSpacing = dp(2)
            verticalSpacing = dp(2)
            setPadding(dp(6), 0, dp(6), dp(6))
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun newTab(label: String, tag: Any): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 20f
        text = label
        setTextColor(Theme.palette(context).keyText)
        this.tag = tag
    }

    /** Re-styles for the current theme, including the grid's text/cell colours. */
    fun applyTheme() {
        val pal = Theme.palette(context)
        setBackgroundColor(pal.panelBg)
        searchBox.setTextColor(pal.keyText)
        searchBox.setHintTextColor(pal.keyTextHint)
        for (tab in tabs) tab.setTextColor(pal.keyText)
        for (tab in subTabs) tab.setTextColor(pal.keyText)
        adapter?.notifyDataSetChanged()
    }

    /** True while the search field has focus and on-screen keys should type into it. */
    fun isSearchFocused(): Boolean = searchBox.hasFocus()

    /** Appends a character/space typed on the on-screen keyboard to the query. */
    fun appendSearch(text: String) {
        searchBox.append(text)
    }

    /** Deletes the character (or selection) before the search caret. */
    fun backspaceSearch() {
        val text = searchBox.text ?: return
        val start = searchBox.selectionStart
        val end = searchBox.selectionEnd
        when {
            start != end -> text.delete(start, end)
            start > 0 -> {
                val cp = Character.codePointBefore(text, start)
                text.delete(start - Character.charCount(cp), start)
            }
        }
    }

    /**
     * Ends search mode: clears the query and focus so a later open starts from
     * a clean state and subsequent keys go back to the app's text field.
     */
    fun clearSearch() {
        searchBox.text?.clear()
        searchBox.clearFocus()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    /** Items to show for the current tab/sub-tab when the search box is blank. */
    private fun itemsForSelection(): List<String> = when (selected) {
        EmojiCategory.RECENTS -> Prefs.emojiRecents(context)
        EmojiCategory.SYMBOLS -> {
            val group = symbolTabs.getOrNull(symbolGroupIndex)
            if (group?.id == "recent") Prefs.symbolRecents(context) else group?.items.orEmpty()
        }
        else -> EmojiData.EMOJI[selected].orEmpty()
    }

    /** Records a pick in the correct recents list (emoji vs special symbol). */
    private fun recordPick(item: String) {
        if (EmojiData.isSymbol(item)) Prefs.addSymbolRecent(context, item)
        else Prefs.addEmojiRecent(context, item)
    }

    /** Rebuilds the grid from the current tab, sub-tab and search state. */
    fun refresh() {
        // Active tab/sub-tab styling; sub-tabs only belong to the SYMBOLS tab.
        for (tab in tabs) {
            val active = tab.tag == selected
            tab.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tab.alpha = if (active) 1f else 0.55f
        }
        for ((index, tab) in subTabs.withIndex()) {
            val active = index == symbolGroupIndex
            tab.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tab.alpha = if (active) 1f else 0.55f
        }
        subTabRow.visibility = if (selected == EmojiCategory.SYMBOLS) View.VISIBLE else View.GONE

        val query = searchBox.text?.toString().orEmpty()
        val items = if (query.isBlank()) itemsForSelection() else EmojiData.search(query)
        val next = EmojiAdapter(items)
        adapter = next
        grid.adapter = next
        grid.setOnItemClickListener { _, _, position, _ ->
            val item = items.getOrNull(position) ?: return@setOnItemClickListener
            recordPick(item)
            onEmojiPicked?.invoke(item)
        }
    }

    /**
     * Grid cells themed with the live palette: keyText for the glyph and an
     * panelBg rounded cell background, instead of the platform list-item colours.
     */
    private inner class EmojiAdapter(items: List<String>) :
        ArrayAdapter<String>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
                setPadding(dp(2), dp(4), dp(2), dp(4))
            }
            val pal = Theme.palette(context)
            view.text = getItem(position)
            view.setTextColor(pal.keyText)
            view.background = GradientDrawable().apply {
                setColor(pal.panelBg)
                cornerRadius = dp(6).toFloat()
            }
            return view
        }
    }
}
