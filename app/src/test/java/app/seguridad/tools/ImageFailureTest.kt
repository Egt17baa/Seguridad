package app.seguridad.tools

import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PhotoFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** M-7: running out of memory is never shown as a damaged file. */
class ImageFailureTest {
    private fun text(t: Throwable) = imageOpenFailureText(t, tooBig = "big", tooLarge = "large") { "damaged:${it.message}" }

    @Test fun outOfMemoryHasItsOwnText() {
        assertEquals(IMAGE_NO_MEMORY, text(OutOfMemoryError()))
        assertEquals(IMAGE_NO_MEMORY, text(ImageOutOfMemory()))
        assertFalse("dañado" in IMAGE_NO_MEMORY)
    }

    @Test fun theOtherFailuresKeepTheirTexts() {
        assertEquals("big", text(ImageTooBig()))
        assertEquals("large", text(ImageTooLarge()))
        assertEquals("damaged:$PHOTO_DAMAGED", text(PhotoFailure(PHOTO_DAMAGED)))
        assertEquals("damaged:x", text(IllegalStateException("x")))
    }

    @Test fun theTextIsChecked() {
        assertEquals("No hay memoria suficiente para abrir esta imagen en este móvil.", IMAGE_NO_MEMORY)
        assert(IMAGE_NO_MEMORY in ALL_TOOL_TEXTS)
    }
}
