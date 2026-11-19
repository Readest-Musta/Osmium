package org.osmium.keyboard

/** Pure validation rules for a user-chosen keyboard background image. */
object ImageImport {

    const val MAX_BYTES = 8L * 1024 * 1024
    const val MAX_DIMENSION = 4096

    fun isAcceptable(mimeType: String?, sizeBytes: Long): Boolean {
        if (mimeType == null || !mimeType.startsWith("image/", ignoreCase = true)) return false
        return sizeBytes in 1..MAX_BYTES
    }

    fun dimensionsAcceptable(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION
}
