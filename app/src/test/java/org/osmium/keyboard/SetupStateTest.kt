package org.osmium.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStateTest {

    @Test
    fun findsOurImeAmongEnabledIds() {
        val list = listOf("com.other/.Ime", "org.osmium.keyboard/.OsmiumImeService")
        assertTrue(SetupState.isEnabled(list, "org.osmium.keyboard/.OsmiumImeService"))
    }

    @Test
    fun toleratesMissingAndDifferentlyCasedIds() {
        assertTrue(SetupState.isEnabled(listOf("ORG.OSMIUM.KEYBOARD/.OSMIUMIMESERVICE"),
            "org.osmium.keyboard/.OsmiumImeService"))
        assertFalse(SetupState.isEnabled(emptyList(), "org.osmium.keyboard/.OsmiumImeService"))
    }
}
