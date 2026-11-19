package org.osmium.keyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The bundled National Jatiya layout must be complete and label every QWERTY key. */
class JatiyaLayoutTest {

    private fun asset(path: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        var tries = 0
        while (dir != null && tries++ < 8) {
            for (prefix in arrayOf("app/src/main/assets/", "src/main/assets/")) {
                val f = File(dir, prefix + path)
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("asset not found: $path")
    }

    @Test
    fun everyQwertyKeyHasAJatiyaLabel() {
        val layout = JSONObject(asset("riti/National_Jatiya.json"))
        val labels = LayoutLabels.fromLayoutJson(layout)
        for (ch in "qwertyuiopasdfghjklzxcvbnm") {
            assertTrue("missing label for '$ch'", labels[ch.toString()]?.n?.isNotBlank() == true)
        }
    }

    @Test
    fun representativeJatiyaMappingsAreCorrect() {
        val labels = LayoutLabels.fromLayoutJson(JSONObject(asset("riti/National_Jatiya.json")))
        assertEquals("ত", labels["k"]?.n)
        assertEquals("ৃ", labels["a"]?.n)
    }
}
