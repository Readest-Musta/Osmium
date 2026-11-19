package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ---- Task 8: ranked multi-token search (RED until implemented) ----

    private fun entry(text: String, vararg keywords: String) =
        EmojiData.SearchEntry(text, keywords.toList())

    @Test
    fun rankExactKeywordBeforePrefixBeforeSubstring() {
        val ranked = EmojiData.rank(
            listOf("abc"),
            listOf(
                entry("sub", "zabc"),       // keyword contains token
                entry("pre", "abcdef"),     // keyword starts with token
                entry("exact", "abc"),      // keyword equals token
            )
        )
        assertEquals(listOf("exact", "pre", "sub"), ranked)
    }

    @Test
    fun rankRequiresEveryTokenToMatch() {
        val ranked = EmojiData.rank(
            listOf("ab", "cd"),
            listOf(
                entry("only-ab", "ab"),
                entry("both", "ab", "cd"),
                entry("only-cd", "cd"),
            )
        )
        assertEquals(listOf("both"), ranked)
    }

    @Test
    fun rankNormalizesVariationSelectorAndJoiner() {
        val ranked = EmojiData.rank(
            listOf("ha\uFE0Fsi", "bro\u200Dther"),
            listOf(entry("x", "hasi", "brother"))
        )
        assertEquals(listOf("x"), ranked)
    }

    @Test
    fun searchHasBengaliAliases() {
        assertTrue(EmojiData.search("হাসি").contains("😀"))
        assertTrue(EmojiData.search("টাকা").contains("৳"))
    }

    @Test
    fun searchSupportsMultipleTokens() {
        val results = EmojiData.search("love heart")
        assertTrue(results.isNotEmpty())
        // ❤ has both "love" and "heart" keywords, so it must outrank items
        // that only match one of the two tokens.
        assertEquals("❤", results.first())
    }

    @Test
    fun searchToleratesVariationSelectorsInQuery() {
        assertEquals(EmojiData.search("rainbow"), EmojiData.search("rainbow\uFE0F"))
        assertFalse(EmojiData.search("rainbow\uFE0F").isEmpty())
    }
}
