package org.osmium.keyboard

/**
 * Android-free rules for choosing literal-ASCII input from `EditorInfo.inputType`.
 * Constants mirror `android.text.InputType` so the logic is JVM-testable.
 */
object InputTypeRules {

    private const val MASK_CLASS = 0x0000000f
    private const val MASK_VARIATION = 0x00000fff
    private const val CLASS_NUMBER = 0x00000002
    private const val CLASS_PHONE = 0x00000003
    private const val CLASS_DATETIME = 0x00000004
    private const val VARIATION_URI = 0x00000010
    private const val VARIATION_EMAIL = 0x00000030

    /**
     * Fields where transliterating to Bengali would produce unusable text:
     * passwords, numbers, phone numbers, dates, emails and URIs.
     */
    fun isAsciiOnly(inputType: Int, isPassword: Boolean): Boolean {
        if (isPassword) return true
        val cls = inputType and MASK_CLASS
        if (cls == CLASS_NUMBER || cls == CLASS_PHONE || cls == CLASS_DATETIME) return true
        // TYPE_MASK_VARIATION includes the low class bits; strip them so a plain
        // text field (class 1) with EMAIL variation (0x30) reads as 0x30.
        val variation = inputType and MASK_VARIATION and 0xFFFFFFF0.toInt()
        return variation == VARIATION_URI || variation == VARIATION_EMAIL
    }
}
