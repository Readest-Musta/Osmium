package org.osmium.keyboard

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Emoji picker panel. Small, fast, fully offline.
 */
class EmojiPanel(context: Context) : LinearLayout(context) {

    var onEmojiPicked: ((String) -> Unit)? = null

    private val grid: GridView
    private val tabs = mutableListOf<TextView>()
    private var selected = EmojiCategory.SMILEYS

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.palette(context).panelBg)

        val tabBar = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (cat in EmojiCategory.values()) {
            val tab = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                text = cat.label
                setTextColor(Theme.palette(context).keyText)
                setOnClickListener {
                    selected = cat
                    refresh()
                }
                tag = cat
            }
            tabs.add(tab)
            tabBar.addView(
                tab,
                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    .apply { setMargins(dp(2), dp(4), dp(2), dp(4)) }
            )
        }
        addView(tabBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))

        grid = GridView(context).apply {
            numColumns = 8
            horizontalSpacing = dp(2)
            verticalSpacing = dp(2)
            setPadding(dp(6), 0, dp(6), dp(6))
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    /** Re-styles for the current theme. */
    fun applyTheme() {
        val pal = Theme.palette(context)
        setBackgroundColor(pal.panelBg)
        for (tab in tabs) tab.setTextColor(pal.keyText)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun emojisFor(cat: EmojiCategory): List<String> = when (cat) {
        EmojiCategory.RECENTS -> Prefs.emojiRecents(context)
        EmojiCategory.SMILEYS -> SMILEYS
        EmojiCategory.GESTURES -> GESTURES
        EmojiCategory.HEARTS -> HEARTS
        EmojiCategory.NATURE -> NATURE
        EmojiCategory.OBJECTS -> OBJECTS
    }

    private fun refresh() {
        for (tab in tabs) {
            tab.typeface = if (tab.tag == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tab.alpha = if (tab.tag == selected) 1f else 0.55f
        }
        val items = emojisFor(selected)
        grid.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            items
        ).apply {
            setDropDownViewResource(android.R.layout.simple_list_item_1)
        }
        grid.setOnItemClickListener { _, _, position, _ ->
            val emoji = items.getOrNull(position) ?: return@setOnItemClickListener
            Prefs.addEmojiRecent(context, emoji)
            onEmojiPicked?.invoke(emoji)
        }
    }

    companion object {
        private val SMILEYS = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😉", "😊", "😍", "😘", "😜", "🤪", "😝",
            "🤔", "🤗", "🤭", "🤫", "😑", "😐", "😶", "😏",
            "😔", "😪", "😴", "😌", "😷", "🤒", "🤕", "🥳",
            "😎", "🤓", "🧐", "😕", "🙁", "😮", "😯", "😲",
            "😳", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
            "😈", "💀", "💩", "🤡", "👻", "👽", "🤖", "😺",
            "🙌", "🙏", "👍", "👎", "👌", "🤞", "🤟", "🤘",
            "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
            "💕", "💞", "💓", "💗", "💖", "💘", "💝", "✨"
        )
        private val GESTURES = listOf(
            "✌", "🤞", "🤟", "🤘", "👌", "🤏", "👈", "👉",
            "👆", "👇", "☝", "✋", "🤚", "🖐", "🖖", "👋",
            "🤙", "💪", "🦾", "🖐", "✊", "👊", "🤛", "🤜"
        )
        private val HEARTS = listOf(
            "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖",
            "💘", "💝", "💟", "♥", "💌", "😻", "💋", "👄"
        )
        private val NATURE = listOf(
            "🌸", "🌺", "🌻", "🌷", "🌹", "🍀", "🌿", "🍃",
            "🌱", "🌲", "🌳", "🌴", "🌵", "🌾", "💐", "🌼",
            "🌞", "🌝", "🌚", "🌛", "🌜", "⭐", "🌟", "✨",
            "⚡", "🔥", "🌈", "☀", "⛅", "☁", "🌧", "❄",
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐦", "🕊", "🦋", "🐝", "🐞", "🐢", "🐟", "🐬",
            "🐘", "🦏", "🐪", "🐄", "🐅", "🐆", "🦓", "🦌"
        )
        private val OBJECTS = listOf(
            "💡", "🔆", "🔦", "📱", "💻", "⌨", "🖥", "🖱",
            "🎧", "🎤", "🎸", "🎺", "🎻", "🥁", "📚", "📖",
            "✏", "🖊", "📌", "📎", "🔑", "🔒", "🔨", "🛠",
            "⚙", "🧲", "🔬", "🔭", "📡", "💊", "💉", "🩺",
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑",
            "🚒", "🚚", "🚲", "🛵", "✈", "🚀", "🛸", "⛵",
            "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱",
            "🏆", "🥇", "🥈", "🥉", "🎯", "🎮", "🎲", "🎬"
        )
    }
}
