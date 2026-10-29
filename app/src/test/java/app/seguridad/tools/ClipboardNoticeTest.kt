package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardNoticeTest {
    @Test fun android8To12AskOnceAndAndroid13DoesNot() {
        assertTrue(needsClipboardHistoryNotice(sdk = 26, accepted = false))
        assertTrue(needsClipboardHistoryNotice(sdk = 32, accepted = false))
        // Android 13 has EXTRA_IS_SENSITIVE: no notice.
        assertFalse(needsClipboardHistoryNotice(sdk = 33, accepted = false))
        assertFalse(needsClipboardHistoryNotice(sdk = 34, accepted = false))
        // Accepted once: never again.
        assertFalse(needsClipboardHistoryNotice(sdk = 26, accepted = true))
        assertFalse(needsClipboardHistoryNotice(sdk = 32, accepted = true))
    }

    @Test fun acceptingHandsTheTextOverOnceAndForgetsIt() {
        val n = ClipboardNoticeRequest()
        assertFalse(n.asking.value)
        n.ask("secreto")
        assertTrue(n.asking.value)
        assertEquals("secreto", n.accept())
        assertFalse(n.asking.value)
        // Taken: a second tap (or a late recomposition) copies nothing.
        assertNull(n.accept())
        assertTrue(n.acceptedNow)
    }

    @Test fun cancelAndLockDropTheText() {
        val n = ClipboardNoticeRequest()
        n.ask("uno")
        n.cancel()
        assertFalse(n.asking.value)
        assertNull(n.accept())
        assertFalse(n.acceptedNow)
        n.ask("dos")
        n.drop()
        assertFalse(n.asking.value)
        assertNull(n.accept())
        assertFalse(n.acceptedNow)
    }

    @Test fun aNewerCopyReplacesTheOneWaiting() {
        val n = ClipboardNoticeRequest()
        n.ask("antes")
        n.ask("después")
        assertEquals("después", n.accept())
    }

    @Test fun theNoticeNamesTheChosenTime() {
        assertEquals(
            "En este Android, el teclado puede guardar lo copiado en su historial. Seguridad lo borra del portapapeles " +
                "a los 45 s, pero no del historial del teclado.",
            clipboardHistoryNoticeText(45),
        )
        assertTrue("a los 15 s," in clipboardHistoryNoticeText(15))
        assertTrue("a los 90 s," in clipboardHistoryNoticeText(90))
    }
}
