package org.osmium.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTypeRulesTest {

    @Test
    fun passwordIsAlwaysAsciiOnly() {
        assertTrue(InputTypeRules.isAsciiOnly(0x00000001, isPassword = true))
    }

    @Test
    fun numberPhoneAndDatetimeAreAsciiOnly() {
        for (cls in listOf(0x00000002, 0x00000003, 0x00000004)) {
            assertTrue(InputTypeRules.isAsciiOnly(cls, false))
        }
    }

    @Test
    fun emailAndUriVariationsAreAsciiOnly() {
        assertTrue(InputTypeRules.isAsciiOnly(0x00000031, false)) // email
        assertTrue(InputTypeRules.isAsciiOnly(0x00000011, false)) // uri
    }

    @Test
    fun plainTextFieldIsNotAsciiOnly() {
        assertFalse(InputTypeRules.isAsciiOnly(0x00000001, false))
    }
}
