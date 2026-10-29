package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardNoticeTest {
    /** A fake clipboard and a fake vault lock counter. */
    private val written = mutableListOf<String>()
    private val write: (String) -> Unit = { written += it }
    private var epoch = 0
    private val notice = ClipboardNoticeRequest(lockEpoch = { epoch })

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

    @Test fun theGateWritesNothingUntilAccepted() {
        var copied = 0
        assertFalse(copyOrAskNotice("secreto", notice, accepted = false, sdk = 32, write = write, onCopied = { copied++ }))
        assertTrue(notice.asking.value)
        assertTrue(written.isEmpty())
        assertEquals(0, copied)
        assertTrue(notice.accept())
        assertEquals(listOf("secreto"), written)
        assertEquals(1, copied)
        assertFalse(notice.asking.value)
        // Taken: a second tap (or a late recomposition) writes nothing more.
        assertFalse(notice.accept())
        assertEquals(listOf("secreto"), written)
        assertEquals(1, copied)
        // Accepted in this run: the next copy goes straight through, even before Ajustes has saved it.
        assertTrue(copyOrAskNotice("otro", notice, accepted = false, sdk = 32, write = write, onCopied = { copied++ }))
        assertEquals(listOf("secreto", "otro"), written)
        assertEquals(2, copied)
    }

    @Test fun theGateCancelledWritesNothing() {
        var copied = false
        copyOrAskNotice("secreto", notice, accepted = false, sdk = 26, write = write, onCopied = { copied = true })
        notice.cancel()
        assertFalse(notice.asking.value)
        assertFalse(notice.accept())
        assertTrue(written.isEmpty())
        assertFalse(copied)
        assertFalse(notice.acceptedNow)
    }

    @Test fun theGateCopiesAtOnceWhenNoNoticeIsNeeded() {
        var copied = false
        assertTrue(copyOrAskNotice("a", notice, accepted = false, sdk = 33, write = write, onCopied = { copied = true }))
        assertTrue(copyOrAskNotice("b", notice, accepted = true, sdk = 26, write = write))
        assertEquals(listOf("a", "b"), written)
        assertTrue(copied)
        assertFalse(notice.asking.value)
    }

    @Test fun aDroppedCopyAndItsCallbackAreForgotten() {
        var copied = false
        notice.ask("dos", write) { copied = true }
        notice.drop()
        assertFalse(notice.asking.value)
        assertFalse(notice.accept())
        assertTrue(written.isEmpty())
        assertFalse(copied)
        assertFalse(notice.acceptedNow)
    }

    @Test fun aLockBetweenAskAndAcceptRefusesTheCopy() {
        var copied = false
        notice.ask("secreto", write) { copied = true }
        epoch++ // The vault locked; the collector's drop has not run yet.
        assertFalse(notice.accept())
        assertTrue(written.isEmpty())
        assertFalse(copied)
        assertFalse(notice.asking.value)
        assertFalse(notice.acceptedNow)
    }

    @Test fun aLockAndUnlockBetweenAskAndAcceptStillRefusesTheCopy() {
        notice.ask("secreto", write)
        // Locked and opened again before the collector saw the lock: the counter has moved, so no copy.
        epoch++
        assertFalse(notice.accept())
        assertTrue(written.isEmpty())
        // A copy asked after the unlock is fine.
        notice.ask("nuevo", write)
        assertTrue(notice.accept())
        assertEquals(listOf("nuevo"), written)
    }

    @Test fun aNewerCopyReplacesTheOneWaiting() {
        var first = false
        notice.ask("antes", write) { first = true }
        notice.ask("después", write)
        assertTrue(notice.accept())
        assertEquals(listOf("después"), written)
        assertFalse(first)
    }

    @Test fun aWriteThatThrowsLetsTheNoticeAppearAgain() {
        var copied = false
        val failing: (String) -> Unit = { throw IllegalStateException("clipboard service gone") }
        assertFalse(copyOrAskNotice("secreto", notice, accepted = false, sdk = 30, write = failing, onCopied = { copied = true }))
        // Nothing was copied: not accepted, no onCopied, and the copy is gone (it is not retried behind the user's back).
        assertFalse(notice.accept())
        assertFalse(copied)
        assertFalse(notice.acceptedNow)
        assertFalse(notice.asking.value)
        // The next copy asks again.
        assertFalse(copyOrAskNotice("otro", notice, accepted = false, sdk = 30, write = write))
        assertTrue(notice.asking.value)
        assertTrue(written.isEmpty())
        assertTrue(notice.accept())
        assertEquals(listOf("otro"), written)
        assertTrue(notice.acceptedNow)
    }
}
