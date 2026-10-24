package app.seguridad.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opening a system picker never closes the app: every RuntimeException from launch() becomes a message. */
class PickerLaunchTest {
    private class NoAppException : RuntimeException()

    private fun noApp(e: RuntimeException) = e is NoAppException

    @Test
    fun aLaunchThatWorksGivesNoMessage() {
        var launched = false
        assertNull(pickerLaunchError(::noApp) { launched = true })
        assertTrue(launched)
    }

    @Test
    fun noAppToHandleItSaysSo() {
        assertEquals(NO_FILE_PICKER, pickerLaunchError(::noApp) { throw NoAppException() })
    }

    @Test
    fun theFragment125RequestCodeCrashIsCaught() {
        // androidx.fragment 1.2.5 threw this for every ActivityResultRegistry request code (>= 0x10000).
        val message = pickerLaunchError(::noApp) { throw IllegalArgumentException("Can only use lower 16 bits for requestCode") }
        assertEquals(PICKER_FAILED, message)
    }

    @Test
    fun anyOtherRuntimeExceptionIsCaught() {
        assertEquals(PICKER_FAILED, pickerLaunchError(::noApp) { throw SecurityException("denied") })
        assertEquals(PICKER_FAILED, pickerLaunchError(::noApp) { throw IllegalStateException("not attached") })
    }

    @Test
    fun theMessagesAreInSpanish() {
        assertEquals("Este móvil no tiene ninguna app para elegir o guardar archivos.", NO_FILE_PICKER)
        assertEquals("No se pudo abrir el selector de archivos.", PICKER_FAILED)
    }
}
