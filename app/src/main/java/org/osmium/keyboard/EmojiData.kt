package org.osmium.keyboard

/**
 * Pure-Kotlin emoji/symbol data and offline keyword search.
 *
 * Consumed by [EmojiPanel]; keeps no Android dependencies so it can be
 * unit-tested on the JVM.
 */
enum class EmojiCategory(val label: String) {
    RECENTS("🕘"),
    SMILEYS("😀"),
    PEOPLE("👤"),
    GESTURES("🤚"),
    HEARTS("❤"),
    ANIMALS("🐾"),
    FOOD("🍔"),
    NATURE("🌸"),
    TRAVEL("✈"),
    ACTIVITY("⚽"),
    SYMBOLS_EMOJI("💯"),
    FLAGS("🏁"),
    OBJECTS("💡"),
    SYMBOLS("#")
}

object EmojiData {

    /** Emoji lists per category. RECENTS is user state and SYMBOLS is [SYMBOL_GROUPS]. */
    val EMOJI: Map<EmojiCategory, List<String>> = mapOf(
        EmojiCategory.SMILEYS to listOf(
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
        ),
        EmojiCategory.PEOPLE to listOf(
            "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔",
            "👩", "🧓", "👴", "👵", "👮", "🕵", "💂", "👷",
            "🤴", "👸", "👳", "👲", "🧕", "🤵", "👰", "🤰",
            "🤱", "👼", "🎅", "🤶", "🦸", "🦹", "🧙", "🧚",
            "🧛", "🧜", "🧝", "🧞", "🧟", "💆", "💇", "🚶",
            "🧍", "🧎", "🏃", "💃", "🕺", "👯", "🧖", "🧗",
            "🤺", "🏇", "🏂", "🏌", "🏄", "🚣", "🏊", "⛹",
            "🏋", "🚴", "🚵", "🤸", "🤼", "🤽", "🤾", "🤹",
            "🧘", "🛀", "🛌", "👪", "👫", "👬", "👭", "💏",
            "💑", "🗣", "👤", "👥", "🫂", "👣"
        ),
        EmojiCategory.GESTURES to listOf(
            "✌", "🤞", "🤟", "🤘", "👌", "🤏", "👈", "👉",
            "👆", "👇", "☝", "✋", "🤚", "🖐", "🖖", "👋",
            "🤙", "💪", "🦾", "✊", "👊", "🤛", "🤜", "🤝",
            "🙌", "🙏", "👍", "👎", "👐", "🤲", "🤜", "🤛",
            "✍", "🤳", "💅", "🤌", "🤙", "👋", "🤚", "🖐"
        ),
        EmojiCategory.HEARTS to listOf(
            "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖",
            "💘", "💝", "💟", "♥", "💌", "😻", "💋", "👄",
            "💏", "💑", "🌹", "🥀", "💐"
        ),
        EmojiCategory.ANIMALS to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
            "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
            "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
            "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜",
            "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐",
            "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋",
            "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🐘",
            "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐃", "🐂",
            "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐", "🦌",
            "🐕", "🐩", "🦮", "🐈", "🐓", "🦃", "🦚", "🦜",
            "🦢", "🕊", "🐇", "🦝", "🦨", "🦡", "🦦", "🐁",
            "🐀", "🐿", "🦔", "🐾", "🐉", "🐲"
        ),
        EmojiCategory.FOOD to listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
            "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
            "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶",
            "🌽", "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯",
            "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞",
            "🧇", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟",
            "🍕", "🥪", "🥙", "🌮", "🌯", "🥗", "🥘", "🍝",
            "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤", "🍙",
            "🍚", "🍘", "🍥", "🍢", "🍡", "🍧", "🍨", "🍦",
            "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫",
            "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "🍼",
            "☕", "🍵", "🧃", "🥤", "🧋", "🍶", "🍺", "🍻",
            "🥂", "🍷", "🥃", "🍸", "🍹", "🧉", "🍾", "🧊"
        ),
        EmojiCategory.NATURE to listOf(
            "🌸", "🌺", "🌻", "🌷", "🌹", "🍀", "🌿", "🍃",
            "🌱", "🌲", "🌳", "🌴", "🌵", "🌾", "💐", "🌼",
            "🌞", "🌝", "🌚", "🌛", "🌜", "⭐", "🌟", "✨",
            "⚡", "🔥", "🌈", "☀", "⛅", "☁", "🌧", "❄",
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐦", "🕊", "🦋", "🐝", "🐞", "🐢", "🐟", "🐬",
            "🐘", "🦏", "🐪", "🐄", "🐅", "🐆", "🦓", "🦌"
        ),
        EmojiCategory.TRAVEL to listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🦽", "🦼",
            "🛴", "🚲", "🛵", "🏍", "🛺", "🚨", "🚔", "🚍",
            "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞",
            "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊",
            "✈", "🛫", "🛬", "🛩", "💺", "🛰", "🚀", "🛸",
            "🚁", "🛶", "⛵", "🚤", "🛥", "🛳", "⛴", "🚢",
            "⚓", "⛽", "🚧", "🚦", "🚥", "🚏", "🗺", "🗿",
            "🗽", "🗼", "🏰", "🏯", "🏟", "🎡", "🎢", "🎠",
            "⛲", "⛱", "🏖", "🏝", "🏜", "🌋", "⛰", "🏔",
            "🏕", "⛺", "🏠", "🏡", "🏘", "🏗", "🏭", "🏢",
            "🏬", "🏣", "🏤", "🏥", "🏦", "🏨", "🏪", "🏫",
            "🏩", "💒", "🏛", "⛪", "🕌", "🕍", "🛕", "🕋",
            "⛩", "🌅", "🌄", "🌠", "🎇", "🎆", "🌇", "🌆",
            "🏙", "🌃", "🌌", "🌉", "🌁"
        ),
        EmojiCategory.ACTIVITY to listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
            "🏏", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊",
            "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌", "🎿",
            "⛷", "🏂", "🪂", "🏋", "🤼", "🤸", "⛹", "🤺",
            "🤾", "🏌", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣",
            "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅",
            "🎖", "🏵", "🎗", "🎫", "🎟", "🎪", "🤹", "🎭",
            "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷",
            "🎺", "🎸", "🎻", "🎲", "♟", "🎯", "🎳", "🎮",
            "🎰", "🧩"
        ),
        EmojiCategory.SYMBOLS_EMOJI to listOf(
            "💯", "✅", "❌", "⭕", "❗", "❓", "‼", "⁉",
            "™", "©", "®", "〰", "➰", "➿", "🔞", "🚫",
            "🚸", "⚠", "✔", "✖", "➕", "➖", "✳", "❇",
            "♻", "💱", "💲", "📈", "📉", "🆗", "🆕", "🆓",
            "🆙", "🔝", "🔃", "🔄", "🔁", "🔂", "▶", "◀",
            "🔼", "🔽", "⏩", "⏪", "⏫", "⏬", "➡", "⬅",
            "⬆", "⬇", "↗", "↘", "↙", "↖", "↕", "↔",
            "🔀", "🔜", "🔙", "🔚", "🔛", "🅰", "🅱", "🅾",
            "🅿", "🆎", "🆒", "🆔", "🆖", "🆘", "🆚", "🈁",
            "🈂", "🈷", "🈶", "🈯", "🉐", "🈹", "🈚", "🈲",
            "🉑", "🈸", "🈴", "🈳", "㊗", "㊙", "🈺", "🈵",
            "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "🟤", "⚫",
            "⚪", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪", "🟫",
            "⬛", "⬜", "◼", "◻", "◾", "◽", "▪", "▫",
            "🔶", "🔷", "🔸", "🔹", "🔺", "🔻", "💠", "🔘",
            "🔳", "🔲"
        ),
        EmojiCategory.FLAGS to listOf(
            "🏁", "🚩", "🎌", "🏴", "🏳", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️",
            "🇧🇩", "🇮🇳", "🇵🇰", "🇳🇵", "🇱🇰", "🇲🇻", "🇺🇸", "🇬🇧",
            "🇨🇦", "🇦🇺", "🇯🇵", "🇰🇷", "🇨🇳", "🇩🇪", "🇫🇷", "🇮🇹",
            "🇪🇸", "🇧🇷", "🇷🇺", "🇸🇦", "🇦🇪", "🇶🇦", "🇹🇷", "🇪🇬",
            "🇳🇬", "🇿🇦", "🇸🇬", "🇲🇾", "🇹🇭", "🇮🇩", "🇵🇭", "🇻🇳"
        ),
        EmojiCategory.OBJECTS to listOf(
            "💡", "🔆", "🔦", "📱", "💻", "⌨", "🖥", "🖱",
            "🎧", "🎤", "🎸", "🎺", "🎻", "🥁", "📚", "📖",
            "✏", "🖊", "📌", "📎", "🔑", "🔒", "🔨", "🛠",
            "⚙", "🧲", "🔬", "🔭", "📡", "💊", "💉", "🩺",
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑",
            "🚒", "🚚", "🚲", "🛵", "✈", "🚀", "🛸", "⛵",
            "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱",
            "🏆", "🥇", "🥈", "🥉", "🎯", "🎮", "🎲", "🎬"
        )
    )

    data class SymbolGroup(val id: String, val label: String, val items: List<String>)

    /** Special-character groups shown under the SYMBOLS tab. */
    val SYMBOL_GROUPS: List<SymbolGroup> = listOf(
        SymbolGroup(
            id = "currency",
            label = "৳",
            items = listOf(
                "৳", "$", "£", "€", "¥", "₹", "₽", "₩", "₺", "₴",
                "₦", "฿", "₫", "₱", "₡", "₵", "₸", "₪", "₮", "₧",
                "₤", "₣", "₥", "₲", "₳", "₶", "₷", "₻", "₼", "₾",
                "₿", "﹩", "＄", "￠", "￡", "￥", "￦"
            )
        ),
        SymbolGroup(
            id = "math",
            label = "∑",
            items = listOf(
                "∑", "∏", "∐", "−", "+", "±", "×", "÷", "∓", "∔",
                "∕", "∖", "∗", "∘", "∙", "√", "∛", "∜", "∝", "∞",
                "∟", "∠", "∡", "∢", "∣", "∤", "∥", "∦", "∧", "∨",
                "∩", "∪", "∫", "∬", "∭", "∮", "∯", "∰", "∱", "∲",
                "∳", "∴", "∵", "∶", "∷", "∸", "∹", "∺", "∻", "∼",
                "∽", "∾", "∿", "≀", "≁", "≂", "≃", "≄", "≅", "≆",
                "≇", "≈", "≉", "≊", "≋", "≌", "≍", "≎", "≏", "≐",
                "≑", "≒", "≓", "≔", "≕", "≖", "≗", "≘", "≙", "≚",
                "≛", "≜", "≝", "≞", "≟", "≠", "≡", "≢", "≣", "≤",
                "≥", "≦", "≧", "≨", "≩", "≪", "≫", "≬", "≭", "≮",
                "≯", "≰", "≱", "≲", "≳", "≴", "≵", "≶", "≷", "≸",
                "≹", "≺", "≻", "≼", "≽", "≾", "≿", "⊀", "⊁", "⊂",
                "⊃", "⊄", "⊅", "⊆", "⊇", "⊈", "⊉", "⊊", "⊋", "⊌",
                "⊍", "⊎", "⊏", "⊐", "⊑", "⊒", "⊓", "⊔", "⊕", "⊖",
                "⊗", "⊘", "⊙", "⊚", "⊛", "⊜", "⊝", "⊞", "⊟", "⊠",
                "⊡", "⊢", "⊣", "⊤", "⊥", "⊦", "⊧", "⊨", "⊩", "⊪",
                "⊫", "⊬", "⊭", "⊮", "⊯", "⊰", "⊱", "⊲", "⊳", "⊴",
                "⊵", "⊶", "⊷", "⊸", "⊹", "⊺", "⊻", "⊼", "⊽", "⊾",
                "⊿", "⋀", "⋁", "⋂", "⋃", "⋄", "⋅", "⋆", "⋇", "⋈",
                "⋉", "⋊", "⋋", "⋌", "⋍", "⋎", "⋏", "⋐", "⋑", "⋒",
                "⋓", "⋔", "⋕", "⋖", "⋗", "⋘", "⋙", "⋚", "⋛", "⋜",
                "⋝", "⋞", "⋟", "⋠", "⋡", "⋢", "⋣", "⋤", "⋥", "⋦",
                "⋧", "⋨", "⋩", "⋪", "⋫", "⋬", "⋭", "⋮", "⋯", "⋰",
                "⋱", "⁒", "⁄", "⅟", "½", "⅓", "¼", "⅕", "⅙", "⅐",
                "⅛", "⅑", "⅒", "⅔", "¾", "⅖", "⅗", "⅘", "⅚", "⅜",
                "⅝", "⅞", "℅", "‰", "‱", "°", "℃", "℉", "µ", "π",
                "∀", "∁", "∂", "∃", "∄", "∅", "∆", "∇", "∈", "∉",
                "∊", "∋", "∌", "∍", "∎"
            )
        ),
        SymbolGroup(
            id = "arrows",
            label = "→",
            items = listOf(
                "←", "→", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙",
                "↚", "↛", "↜", "↝", "↞", "↟", "↠", "↡", "↢", "↣",
                "↤", "↥", "↦", "↧", "↨", "↩", "↪", "↫", "↬", "↭",
                "↮", "↯", "↰", "↱", "↲", "↳", "↴", "↵", "↶", "↷",
                "↸", "↹", "↺", "↻", "↼", "↽", "↾", "↿", "⇀", "⇁",
                "⇂", "⇃", "⇄", "⇅", "⇆", "⇇", "⇈", "⇉", "⇊", "⇋",
                "⇌", "⇍", "⇎", "⇏", "⇐", "⇑", "⇒", "⇓", "⇔", "⇕",
                "⇖", "⇗", "⇘", "⇙", "⇚", "⇛", "⇜", "⇝", "⇞", "⇟",
                "⇠", "⇡", "⇢", "⇣", "⇤", "⇥", "⇦", "⇧", "⇨", "⇩",
                "⇪", "⇫", "⇬", "⇭", "⇮", "⇯", "⇰", "⇱", "⇲", "⇳",
                "⇴", "⇵", "⇶", "⇷", "⇸", "⇹", "⇺", "⇻", "⇼", "⇽",
                "⇾", "⇿", "➔", "➘", "➙", "➚", "➛", "➜", "➝", "➞",
                "➟", "➠", "➡", "➢", "➣", "➤", "➥", "➦", "➧", "➨",
                "➩", "➪", "➫", "➬", "➭", "➮", "➯", "➱", "➲", "➳",
                "➴", "➵", "➶", "➷", "➸", "➹", "➺", "➻", "➼", "➽",
                "➾", "⟵", "⟶", "⟷", "⟸", "⟹", "⟺", "⟻", "⟼", "⟽",
                "⟾", "⟿", "⬅", "⬆", "⬇", "⬈", "⬉", "⬊", "⬋", "⬌",
                "⬍", "⬎", "⬏", "⬐", "⬑", "⬒", "⬓", "⬔", "⬕", "⬖",
                "⬗", "⬘", "⬙"
            )
        ),
        SymbolGroup(
            id = "marks",
            label = "★",
            items = listOf(
                "★", "☆", "✡", "✦", "✧", "✩", "✪", "✫", "✬", "✭",
                "✮", "✯", "✰", "✱", "✲", "✳", "✴", "✵", "✶", "✷",
                "✸", "✹", "✺", "✻", "✼", "✽", "✾", "✿", "❀", "❁",
                "❂", "❃", "❄", "❅", "❆", "❇", "❈", "❉", "❊", "❋",
                "❌", "❍", "❎", "❏", "❐", "❑", "❒", "❖", "❘", "❙",
                "❚", "❛", "❜", "❝", "❞", "❟", "❠", "❡", "❢", "❣",
                "❤", "❥", "❦", "❧", "♠", "♣", "♥", "♦", "♤", "♧",
                "♡", "♢", "♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛",
                "♜", "♝", "♞", "♟", "⚜", "🔱", "✔", "✖", "✗", "✘",
                "✙", "✚", "✛", "✜", "✝", "✞", "✟", "✠", "✢", "✣",
                "✤", "✥"
            )
        ),
        SymbolGroup(
            id = "quotes",
            label = "❝",
            items = listOf(
                "❝", "❞", "❛", "❜", "❟", "❠", "❡", "❢", "❣", "❤",
                "❥", "❦", "❧", "«", "»", "‹", "›", "„", "“", "”",
                "‘", "’", "‚", "‛", "‟", "〈", "〉", "《", "》", "「",
                "」", "『", "』", "【", "】", "〔", "〕", "〖", "〗", "〘",
                "〙", "〚", "〛", "｢", "｣", "⟦", "⟧", "⟨", "⟩", "⟪",
                "⟫", "⟬", "⟭", "⟮", "⟯", "（", "）", "［", "］", "｛",
                "｝", "⟅", "⟆"
            )
        ),
        SymbolGroup(
            id = "misc",
            label = "♻",
            items = listOf(
                "♻", "♲", "♳", "♴", "♵", "♶", "♷", "♸", "♹", "♺",
                "♼", "♽", "♾", "♿", "⚊", "⚋", "⚌", "⚍", "⚎", "⚏",
                "⚐", "⚑", "⚒", "⚓", "⚔", "⚕", "⚖", "⚗", "⚘", "⚙",
                "⚚", "⚛", "⚜", "⚝", "⚞", "⚟", "⚠", "⚡", "⚢", "⚣",
                "⚤", "⚥", "⚦", "⚧", "⚨", "⚩", "⚪", "⚫", "⚬", "⚭",
                "⚮", "⚯", "⚰", "⚱", "⚲", "⚳", "⚴", "⚵", "⚶", "⚷",
                "⚸", "⚹", "⚺", "⚻", "⚼", "⚽", "⚾", "⛉", "⛊", "⛋",
                "⛌", "⛍", "⛎", "⛏", "⛐", "⛑", "⛒", "⛓", "⛔", "⛕",
                "⛖", "⛗", "⛘", "⛙", "⛚", "⛛", "⛜", "⛝", "⛞", "⛟",
                "⛠", "⛡", "⛢", "⛣", "⛤", "⛥", "⛦", "⛧", "⛨", "⛩",
                "⛪", "⛫", "⛬", "⛭", "⛮", "⛯", "⛰", "⛱", "⛲", "⛳",
                "⛴", "⛵", "⛶", "⛷", "⛸", "⛹", "⛺", "⛻", "⛼", "⛽",
                "⛾", "⛿", "✀", "✁", "✂", "✃", "✄", "✅", "✆", "✇",
                "✈", "✉", "✊", "✋", "✌", "✍", "✎", "✏", "✐", "✑",
                "✒", "✓", "✔", "✕", "✖", "✗", "✘", "✙", "✚", "✛",
                "✜", "✝", "✞", "✟", "✠", "✡", "✢", "✣", "✤", "✥",
                "✦", "✧", "✩", "✪", "✫", "✬", "✭", "✮", "✯", "✰",
                "✱", "✲", "✳", "✴", "✵", "✶", "✷", "✸", "✹", "✺",
                "✻", "✼", "✽", "✾", "✿", "❀", "❁", "❂", "❃", "❄",
                "❅", "❆", "❇", "❈", "❉", "❊", "❋", "❌", "❍", "❎",
                "❏", "❐", "❑", "❒", "❓", "❔", "❕", "❗", "❖", "❘",
                "❙", "❚", "❛", "❜", "❝", "❞", "❟", "❠", "❡", "❢",
                "❣", "❤", "❥", "❦", "❧", "♠", "♣", "♥", "♦", "♤",
                "♧", "♡", "♢", "♩", "♪", "♫", "♬", "♭", "♮", "♯",
                "♰", "♱"
            )
        ),
        SymbolGroup(
            id = "bangla",
            label = "অ",
            items = listOf(
                "অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও",
                "ঔ", "ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ",
                "ঞ", "ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ",
                "ন", "প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ",
                "ষ", "স", "হ", "ড়", "ঢ়", "য়", "ৎ", "ং", "ঃ", "ঁ",
                "া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো", "ৌ",
                "্", "০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮",
                "৯", "৳", "৺", "৻", "ৼ", "৽", "৾"
            )
        )
    )

    /** Every symbol exposed under the SYMBOLS tab; used to split recents. */
    val SYMBOL_ITEMS: Set<String> = SYMBOL_GROUPS.flatMap { it.items }.toSet()

    /** True when [item] is one of the special-character symbols (not an emoji). */
    fun isSymbol(item: String): Boolean = item in SYMBOL_ITEMS

    /** Lowercase keyword -> emoji/symbol strings. */
    val KEYWORDS: Map<String, List<String>> = mapOf(
        "smile" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂"),
        "laugh" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "😹"),
        "joy" to listOf("😂", "🤣", "😹"),
        "happy" to listOf("😀", "😃", "😄", "😁", "🙂", "😊", "🥰"),
        "cry" to listOf("😢", "😭", "😿"),
        "sad" to listOf("😔", "😪", "🙁", "😕", "🥺", "😢", "😭"),
        "angry" to listOf("😤", "😠", "😡", "🤬"),
        "love" to listOf(
            "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎",
            "💕", "💞", "💓", "💗", "💖", "💘", "💝", "😍", "😘", "😻", "💋"
        ),
        "heart" to listOf(
            "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎",
            "💔", "❣", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥", "💌"
        ),
        "kiss" to listOf("😘", "💋", "👄", "😽"),
        "cool" to listOf("😎", "🕶", "🆒"),
        "party" to listOf("🥳", "🎉", "🎊", "🎈", "🍾", "🥂", "🎂"),
        "ghost" to listOf("👻"),
        "alien" to listOf("👽", "🛸"),
        "robot" to listOf("🤖"),
        "skull" to listOf("💀", "☠"),
        "poop" to listOf("💩"),
        "clown" to listOf("🤡"),
        "star" to listOf("★", "☆"),
        "sparkle" to listOf("✨", "🌟", "⭐", "🔆"),
        "fire" to listOf("🔥", "🧨", "🎇", "🎆"),
        "zap" to listOf("⚡"),
        "rainbow" to listOf("🌈"),
        "sun" to listOf("🌞", "☀", "🌅", "🌄", "🌇", "🌆"),
        "moon" to listOf("🌝", "🌚", "🌛", "🌜", "🌙", "🌕", "🌖", "🌗", "🌘"),
        "cloud" to listOf("☁", "⛅", "🌧", "🌦", "⛈", "🌩", "🌨"),
        "rain" to listOf("🌧", "☔", "💧"),
        "snow" to listOf("❄", "⛄", "🌨", "☃"),
        "flower" to listOf("🌸", "🌺", "🌻", "🌷", "🌹", "💐", "🌼", "🥀"),
        "tree" to listOf("🌲", "🌳", "🌴", "🌵", "🌱", "🌿", "🍃", "🍀", "🌾"),
        "cat" to listOf("🐱", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "🐈"),
        "dog" to listOf("🐶", "🐕", "🐩", "🦮", "🐺"),
        "monkey" to listOf("🐵", "🐒", "🦍", "🦧", "🙈", "🙉", "🙊"),
        "bear" to listOf("🐻", "🐼", "🐨"),
        "panda" to listOf("🐼"),
        "lion" to listOf("🦁"),
        "tiger" to listOf("🐯", "🐅"),
        "fox" to listOf("🦊"),
        "rabbit" to listOf("🐰", "🐇"),
        "mouse" to listOf("🐭", "🐁", "🐀"),
        "frog" to listOf("🐸"),
        "pig" to listOf("🐷", "🐖", "🐽"),
        "cow" to listOf("🐮", "🐄", "🐂"),
        "horse" to listOf("🐴", "🐎", "🦄"),
        "deer" to listOf("🦌"),
        "giraffe" to listOf("🦒"),
        "elephant" to listOf("🐘"),
        "bird" to listOf("🐦", "🐧", "🕊", "🦆", "🦅", "🦉", "🦚", "🦜", "🦢"),
        "butterfly" to listOf("🦋"),
        "bee" to listOf("🐝"),
        "fish" to listOf("🐟", "🐠", "🐡", "🐬", "🐳", "🐋", "🦈"),
        "music" to listOf("🎵", "🎶", "🎼", "🎤", "🎧", "🎸", "🎹", "🥁", "🎷", "🎺", "🎻"),
        "food" to listOf(
            "🍔", "🍕", "🍜", "🍚", "🍛", "🍲", "🍗", "🍝", "🍣", "🍱",
            "🍦", "🍩", "🍪", "🎂", "🍰", "🍎", "🍌", "🍇", "🍓", "🍉"
        ),
        "pizza" to listOf("🍕"),
        "burger" to listOf("🍔"),
        "rice" to listOf("🍚", "🍛"),
        "coffee" to listOf("☕"),
        "tea" to listOf("🍵", "☕"),
        "beer" to listOf("🍺", "🍻"),
        "wine" to listOf("🍷", "🍸", "🍹", "🥂"),
        "apple" to listOf("🍎", "🍏"),
        "banana" to listOf("🍌"),
        "grape" to listOf("🍇"),
        "watermelon" to listOf("🍉"),
        "strawberry" to listOf("🍓"),
        "cherry" to listOf("🍒"),
        "peach" to listOf("🍑"),
        "pineapple" to listOf("🍍"),
        "coconut" to listOf("🥥"),
        "kiwi" to listOf("🥝"),
        "tomato" to listOf("🍅"),
        "avocado" to listOf("🥑"),
        "carrot" to listOf("🥕"),
        "corn" to listOf("🌽"),
        "pepper" to listOf("🌶"),
        "bread" to listOf("🍞", "🥐", "🥖", "🥨", "🥯"),
        "cheese" to listOf("🧀"),
        "egg" to listOf("🥚", "🍳"),
        "popcorn" to listOf("🍿"),
        "chocolate" to listOf("🍫"),
        "candy" to listOf("🍬", "🍭"),
        "cake" to listOf("🍰", "🎂", "🧁"),
        "honey" to listOf("🍯"),
        "phone" to listOf("📱", "☎", "📞", "📟"),
        "computer" to listOf("💻", "⌨", "🖥", "🖱", "💾", "💽", "💿", "📀"),
        "camera" to listOf("📷", "📸", "🎥", "📹", "📽"),
        "book" to listOf("📚", "📖", "📕", "📗", "📘", "📙", "📓", "📔", "📒", "📃", "📜", "📄", "📰"),
        "key" to listOf("🔑", "🗝"),
        "lock" to listOf("🔒", "🔓", "🔐"),
        "tool" to listOf("🔨", "🛠", "⛏", "⚒", "🔧", "🔩", "⚙", "🗜"),
        "car" to listOf("🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑", "🚒", "🚚", "🚛", "🚜"),
        "bus" to listOf("🚌", "🚎", "🚍", "🚐"),
        "train" to listOf("🚆", "🚇", "🚈", "🚉", "🚄", "🚅", "🚂", "🚝"),
        "plane" to listOf("✈", "🛫", "🛬", "🛩", "💺"),
        "rocket" to listOf("🚀", "🛸"),
        "ship" to listOf("🚢", "🛳", "⛴", "🛶", "🚤", "🛥", "⛵"),
        "bike" to listOf("🚲", "🛵", "🏍", "🛴"),
        "sport" to listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🎯", "🎳", "🎮", "🎲", "🧩", "♟"),
        "football" to listOf("⚽", "🏈", "🏉"),
        "basketball" to listOf("🏀"),
        "tennis" to listOf("🎾"),
        "trophy" to listOf("🏆", "🥇", "🥈", "🥉", "🏅", "🎖"),
        "game" to listOf("🎮", "🎲", "🧩", "♟", "🎯", "🎳"),
        "flag" to listOf("🏁", "🚩", "🎌", "🏴", "🏳", "🏳️‍🌈", "🇧🇩"),
        "bangladesh" to listOf("🇧🇩"),
        "bangla" to listOf(
            "অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও", "ঔ",
            "ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ", "ট", "ঠ",
            "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন", "প", "ফ", "ব", "ভ",
            "ম", "য", "র", "ল", "শ", "ষ", "স", "হ", "ড়", "ঢ়", "য়", "ৎ",
            "ং", "ঃ", "ঁ", "০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "৳"
        ),
        "dollar" to listOf("$", "＄", "﹩", "💵", "💲"),
        "euro" to listOf("€"),
        "pound" to listOf("£", "￡"),
        "yen" to listOf("¥", "￥"),
        "rupee" to listOf("₹"),
        "taka" to listOf("৳"),
        "currency" to listOf("৳", "$", "£", "€", "¥", "₹", "₽", "₩", "₺", "₴", "₦", "฿", "₫", "₱"),
        "math" to listOf("∑", "∏", "√", "∞", "π", "≠", "≈", "≤", "≥", "±", "×", "÷", "∫", "∂", "∆", "∇", "∈", "∉", "∩", "∪", "⊂", "⊃", "⊆", "⊇", "⊕", "⊗", "⊥", "⊢", "∀", "∃", "∅", "½", "¼", "¾", "‰", "°"),
        "check" to listOf("✅", "✔", "✓", "☑", "🆗"),
        "cross" to listOf("❌", "✖", "✗", "✘", "❎"),
        "question" to listOf("❓", "❔", "⁉", "‼"),
        "warning" to listOf("⚠", "🚫", "🔞", "🚸"),
        "arrow" to listOf("←", "→", "↑", "↓", "↔", "⇐", "⇒", "↦"),
        "arrowup" to listOf("↑", "⬆", "↟", "⇡", "⏫"),
        "arrowdown" to listOf("↓", "⬇", "↡", "⇣", "⏬"),
        "quote" to listOf("❝", "❞", "❛", "❜", "«", "»", "“", "”", "‘", "’", "„", "「", "」", "【", "】"),
        "recycle" to listOf("♻"),
        "peace" to listOf("☮", "🕊")
    )

    /**
     * Bengali aliases so queries typed with the on-screen Bengali keyboard find
     * the right emoji/symbols. Merged with [KEYWORDS] at search time.
     */
    val BENGALI_KEYWORDS: Map<String, List<String>> = mapOf(
        "হাসি" to listOf("😀", "😃", "😄", "😁", "😂", "🤣", "🙂", "😊", "😹"),
        "কান্না" to listOf("😢", "😭", "🥺", "😔", "😪"),
        "প্রেম" to listOf("❤", "💕", "💞", "💓", "😍", "😘", "😻"),
        "হৃদয়" to listOf("❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔"),
        "টাকা" to listOf("৳", "💵", "💲"),
        "অর্থ" to listOf("৳", "💵", "💰", "💲"),
        "সূর্য" to listOf("☀", "🌞", "🌅"),
        "চাঁদ" to listOf("🌙", "🌝", "🌚", "🌛"),
        "আগুন" to listOf("🔥", "🧨"),
        "ফুল" to listOf("🌸", "🌺", "🌻", "🌷", "🌹", "💐"),
        "বিড়াল" to listOf("🐱", "😺", "🐈"),
        "কুকুর" to listOf("🐶", "🐕", "🐩"),
        "বাংলাদেশ" to listOf("🇧🇩"),
        "বাংলা" to listOf("🇧🇩") + (SYMBOL_GROUPS.first { it.id == "bangla" }.items),
        "খেলা" to listOf("⚽", "🏀", "🎮", "🎲"),
        "তারা" to listOf("★", "☆", "⭐", "✨"),
        "ধন্যবাদ" to listOf("🙏"),
        "হ্যাঁ" to listOf("✅", "✔"),
        "না" to listOf("❌", "✖")
    )

    /**
     * Romanised spellings of the Bengali aliases above. The on-screen keys insert
     * ASCII into the search box in every mode, so these aliases are what a Bengali
     * typist can actually type while the picker is open.
     */
    val ROMANIZED_BENGALI_KEYWORDS: Map<String, List<String>> = mapOf(
        "hasi" to BENGALI_KEYWORDS.getValue("হাসি"),
        "kanna" to BENGALI_KEYWORDS.getValue("কান্না"),
        "prem" to BENGALI_KEYWORDS.getValue("প্রেম"),
        "hridoy" to BENGALI_KEYWORDS.getValue("হৃদয়"),
        "taka" to BENGALI_KEYWORDS.getValue("টাকা"),
        "ortho" to BENGALI_KEYWORDS.getValue("অর্থ"),
        "surjo" to BENGALI_KEYWORDS.getValue("সূর্য"),
        "chand" to BENGALI_KEYWORDS.getValue("চাঁদ"),
        "agun" to BENGALI_KEYWORDS.getValue("আগুন"),
        "phul" to BENGALI_KEYWORDS.getValue("ফুল"),
        "biral" to BENGALI_KEYWORDS.getValue("বিড়াল"),
        "kukur" to BENGALI_KEYWORDS.getValue("কুকুর"),
        "bangladesh" to BENGALI_KEYWORDS.getValue("বাংলাদেশ"),
        "bangla" to BENGALI_KEYWORDS.getValue("বাংলা"),
        "khela" to BENGALI_KEYWORDS.getValue("খেলা"),
        "tara" to BENGALI_KEYWORDS.getValue("তারা"),
        "dhonnobad" to BENGALI_KEYWORDS.getValue("ধন্যবাদ"),
        "ha" to BENGALI_KEYWORDS.getValue("হ্যাঁ"),
        "na" to BENGALI_KEYWORDS.getValue("না"),
    )

    // ------------------------------------------------------------------
    // Ranked, multi-token offline search
    // ------------------------------------------------------------------

    private const val VS16 = "\uFE0F" // variation selector-16
    private const val ZWJ = "\u200D"  // zero-width joiner

    /** Case-folds and strips VS16/ZWJ so decorated emoji still match plain queries. */
    fun normalize(s: String): String = s.lowercase().replace(VS16, "").replace(ZWJ, "")

    /** One searchable item together with all of its (English + Bengali) keywords. */
    data class SearchEntry(val text: String, val keywords: List<String>)

    /**
     * Ranks [entries] against [tokens] (already split on whitespace). Every
     * token must match somewhere — a keyword, or the item's own text — otherwise
     * the entry is dropped (AND semantics). Per token the best match scores:
     * exact keyword 5, keyword prefix 4, keyword substring 3, text substring 1.
     * Scores are summed across tokens; ties keep first-seen order. Results are
     * deduplicated and capped at 200.
     */
    fun rank(tokens: List<String>, entries: List<SearchEntry>): List<String> {
        val normTokens = tokens.map { normalize(it) }.filter { it.isNotEmpty() }
        if (normTokens.isEmpty()) return emptyList()

        val bestByText = LinkedHashMap<String, Int>()
        for (entry in entries) {
            val text = normalize(entry.text)
            val keywords = entry.keywords.map { normalize(it) }
            var total = 0
            var matchesAll = true
            for (token in normTokens) {
                var best = 0
                for (kw in keywords) {
                    val score = when {
                        kw == token -> 5
                        kw.startsWith(token) -> 4
                        kw.contains(token) -> 3
                        else -> 0
                    }
                    if (score > best) best = score
                }
                if (best == 0 && text.contains(token)) best = 1
                if (best == 0) {
                    matchesAll = false
                    break
                }
                total += best
            }
            if (!matchesAll) continue
            val prev = bestByText[entry.text]
            if (prev == null || total > prev) bestByText[entry.text] = total
        }
        return bestByText.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(200)
    }

    /** Keyword index over all datasets, built once and shared by [search]. */
    private val searchEntries: List<SearchEntry> by lazy {
        val keywords = LinkedHashMap<String, MutableList<String>>()
        fun add(keyword: String, items: List<String>) {
            keywords.getOrPut(normalize(keyword)) { mutableListOf() }.addAll(items)
        }
        for ((keyword, items) in KEYWORDS) add(keyword, items)
        for ((keyword, items) in BENGALI_KEYWORDS) add(keyword, items)
        for ((keyword, items) in ROMANIZED_BENGALI_KEYWORDS) add(keyword, items)

        val byText = LinkedHashMap<String, MutableList<String>>()
        for ((keyword, items) in keywords) {
            for (item in items) byText.getOrPut(item) { mutableListOf() }.add(keyword)
        }
        // Items with no keyword are still findable by their own glyph.
        for (items in EMOJI.values) for (item in items) byText.getOrPut(item) { mutableListOf() }
        for (group in SYMBOL_GROUPS) for (item in group.items) byText.getOrPut(item) { mutableListOf() }
        byText.map { (text, kws) -> SearchEntry(text, kws) }
    }

    /** Offline ranked search. Blank queries return an empty list. */
    fun search(query: String): List<String> =
        rank(query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }, searchEntries)
}
