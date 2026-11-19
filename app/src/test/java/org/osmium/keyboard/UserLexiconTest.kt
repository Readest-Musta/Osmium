package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLexiconTest {

    @Test
    fun commitAddsWordAndCharacterNgrams() {
        val s = UserLexicon.State()
        UserLexicon.recordCommit(s, "Kemon", listOf("hi"), 1000L, true)
        assertTrue(s.words.containsKey("kemon"))
        assertTrue(s.charNgrams.containsKey("kem"))
        assertEquals(1, s.bigrams["hi"]?.get("kemon"))
    }

    @Test
    fun incognitoAndEmptyWordsAreIgnored() {
        val s = UserLexicon.State()
        UserLexicon.recordCommit(s, "kemon", emptyList(), 1000L, false)
        UserLexicon.recordCommit(s, "", emptyList(), 1000L, true)
        assertTrue(s.words.isEmpty())
    }

    @Test
    fun agingDropsOldEntries() {
        val s = UserLexicon.State()
        val now = System.currentTimeMillis()
        UserLexicon.recordCommit(s, "hasi", emptyList(), now - 1000L * 60 * 60 * 24 * 365, true)
        UserLexicon.age(s, now)
        assertTrue(s.words.isEmpty())
    }

    @Test
    fun malformedJsonDecodesToEmptyState() {
        assertTrue(UserLexicon.decode("not json").words.isEmpty())
    }

    @Test
    fun roundTripPreservesWordAndWeights() {
        val s = UserLexicon.State()
        UserLexicon.recordCommit(s, "bhalo", emptyList(), 1000L, true)
        s.weights[0] = 0.5f
        val back = UserLexicon.decode(UserLexicon.encode(s))
        assertTrue(back.words.containsKey("bhalo"))
        assertEquals(0.5f, back.weights[0], 0.001f)
    }
}
