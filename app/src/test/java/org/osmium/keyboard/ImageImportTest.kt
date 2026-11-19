package org.osmium.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageImportTest {

    @Test
    fun rejectsNonImagesAndOversizedFiles() {
        assertFalse(ImageImport.isAcceptable("text/plain", 100))
        assertFalse(ImageImport.isAcceptable(null, 100))
        assertFalse(ImageImport.isAcceptable("image/png", 0))
        assertFalse(ImageImport.isAcceptable("image/png", ImageImport.MAX_BYTES + 1))
    }

    @Test
    fun acceptsReasonableImages() {
        assertTrue(ImageImport.isAcceptable("image/png", 1024))
        assertTrue(ImageImport.isAcceptable("image/jpeg", ImageImport.MAX_BYTES))
    }

    @Test
    fun rejectsAbsurdDimensions() {
        assertTrue(ImageImport.dimensionsAcceptable(1080, 1920))
        assertFalse(ImageImport.dimensionsAcceptable(0, 100))
        assertFalse(ImageImport.dimensionsAcceptable(5000, 100))
    }
}
