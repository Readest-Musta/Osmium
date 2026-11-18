package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {

    @Test
    fun searchEmptyReturnsEmpty() {
        assertTrue(EmojiData.search("").isEmpty())
        assertTrue(EmojiData.search("   ").isEmpty())
    }

    @Test
    fun searchMatchesKeyword() {
        val results = EmojiData.search("star")
        assertTrue(results.contains("★"))
        assertTrue(results.contains("☆"))
    }

    @Test
    fun searchIsCaseInsensitive() {
        assertEquals(EmojiData.search("star"), EmojiData.search("STAR"))
    }

    @Test
    fun searchDeduplicates() {
        val results = EmojiData.search("arrow")
        assertEquals(results.distinct().size, results.size)
    }

    @Test
    fun symbolGroupsContainBanglaTaka() {
        val currency = EmojiData.SYMBOL_GROUPS.first { it.id == "currency" }
        assertTrue(currency.items.contains("৳"))
    }

    @Test
    fun allEmojiCategoriesHaveItems() {
        assertTrue(EmojiData.EMOJI.isNotEmpty())
        EmojiData.EMOJI.forEach { (_, items) ->
            assertTrue(items.isNotEmpty())
        }
    }
}
