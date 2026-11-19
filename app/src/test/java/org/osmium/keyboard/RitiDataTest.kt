package org.osmium.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RitiDataTest {

    private fun tempDir(): File = Files.createTempDirectory("riti-data-test").toFile()

    @Test
    fun `fresh marker and non-empty files are accepted`() {
        val dir = tempDir()
        File(dir, ".version").writeText("v1")
        for (name in listOf("dictionary.json", "autocorrect.json")) {
            File(dir, name).writeText("{}")
        }
        assertTrue(RitiEngine.isDataFresh(dir, listOf("dictionary.json", "autocorrect.json"), "v1"))
    }

    @Test
    fun `missing marker is stale`() {
        val dir = tempDir()
        File(dir, "dictionary.json").writeText("{}")
        assertFalse(RitiEngine.isDataFresh(dir, listOf("dictionary.json"), "v1"))
    }

    @Test
    fun `version mismatch forces a refresh`() {
        val dir = tempDir()
        File(dir, ".version").writeText("v0")
        File(dir, "dictionary.json").writeText("{}")
        assertFalse(RitiEngine.isDataFresh(dir, listOf("dictionary.json"), "v1"))
    }

    @Test
    fun `truncated file is never accepted as valid`() {
        val dir = tempDir()
        File(dir, ".version").writeText("v1")
        File(dir, "dictionary.json").writeText("")
        assertFalse(RitiEngine.isDataFresh(dir, listOf("dictionary.json"), "v1"))
    }
}
