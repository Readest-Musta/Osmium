package org.osmium.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure clipboard persistence layer (Task 8). No Android
 * dependencies: [ClipboardCodec] only deals in JSON strings and data classes.
 */
class ClipboardCodecTest {

    @Test
    fun legacyJsonMigratesWithoutDataLoss() {
        val legacy = """[{"t":"hello","p":true},{"t":"world","p":false}]"""
        val items = ClipboardCodec.decode(legacy)

        assertEquals(2, items.size)
        assertEquals("hello", items[0].text)
        assertTrue(items[0].pinned)
        assertEquals("world", items[1].text)
        assertFalse(items[1].pinned)

        // Every migrated entry gains a stable, unique id and a timestamp.
        assertTrue(items.all { it.id.isNotBlank() })
        assertEquals(2, items.map { it.id }.distinct().size)
        assertTrue(items.all { it.at != 0L })
    }

    @Test
    fun migrationIsDeterministicAcrossReads() {
        val legacy = """[{"t":"x","p":true},{"t":"y"}]"""
        assertEquals(ClipboardCodec.decode(legacy), ClipboardCodec.decode(legacy))
    }

    @Test
    fun encodeDecodeRoundTripsAllFields() {
        val items = ClipboardCodec.decode("""[{"t":"a","p":true},{"t":"b"}]""")
        val restored = ClipboardCodec.decode(ClipboardCodec.encode(items))
        assertEquals(items, restored)
    }

    @Test
    fun newFormatKeepsStoredIdsAndTimestamps() {
        val json = """[{"id":"fixed-id","t":"kept","p":true,"at":12345}]"""
        val item = ClipboardCodec.decode(json).single()
        assertEquals("fixed-id", item.id)
        assertEquals(12345L, item.at)
        assertTrue(item.pinned)
    }

    @Test
    fun recopyDedupesAndPreservesPinAndId() {
        val existing = ClipboardCodec.decode("""[{"t":"hi","p":true,"at":7}]""")
        val updated = ClipboardCodec.add(existing, "hi", 99L) { "NEW" }

        assertEquals(1, updated.size)
        assertEquals("hi", updated[0].text)
        assertTrue(updated[0].pinned)   // re-copy keeps the pin
        assertEquals(existing[0].id, updated[0].id) // and the stable id
        assertEquals(99L, updated[0].at)
    }

    @Test
    fun addUsesGeneratedIdForNewText() {
        val added = ClipboardCodec.add(emptyList(), "new text", 55L) { "GEN" }
        assertEquals(1, added.size)
        assertEquals("GEN", added[0].id)
        assertEquals("new text", added[0].text)
        assertEquals(55L, added[0].at)
    }

    @Test
    fun decodeDedupesLegacyDuplicatesPreferringPinned() {
        val legacy = """[{"t":"dup"},{"t":"dup","p":true}]"""
        val items = ClipboardCodec.decode(legacy)
        assertEquals(1, items.size)
        assertTrue(items[0].pinned)
    }

    @Test
    fun malformedJsonYieldsEmptyHistory() {
        assertTrue(ClipboardCodec.decode("not-json").isEmpty())
        assertTrue(ClipboardCodec.decode("[]").isEmpty())
    }
}
