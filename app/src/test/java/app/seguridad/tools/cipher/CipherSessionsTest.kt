package app.seguridad.tools.cipher

import app.seguridad.crypto.Aead
import app.seguridad.tools.codes.LockSeen
import app.seguridad.tools.codes.ScreenGuard
import app.seguridad.vault.LockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COV1-1 (audit 1.3.0): «Cifrar texto», «Cifrar archivos» and «¿Contraseña filtrada?» follow the «PIN y códigos» rule;
 * COV1-2: «Huella SHA-256» shows a fingerprint only for the file it names.
 */
class CipherSessionsTest {
    private val seed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    /** «Cifrar texto» after decrypting a seed phrase, with the form filled in. */
    private fun decrypted() = TextCryptoSession().apply {
        encrypt = false
        seedMode = false
        alg = Aead.CHACHA20_POLY1305
        useKey = true
        input = "SG1:texto-cifrado"
        password = "contraseña"
        keyHex = "00".repeat(32)
        output = seed
        error = "Clave incorrecta o texto dañado"
        fileMessage = true to "Texto cifrado importado de nota.txt."
    }

    private fun TextCryptoSession.assertWiped() {
        assertEquals("", input)
        assertEquals("", password)
        assertEquals("", keyHex)
        assertNull(output)
        assertNull(error)
        assertNull(fileMessage)
        // Not secrets: the choices stay.
        assertFalse(encrypt)
        assertFalse(seedMode)
        assertEquals(Aead.CHACHA20_POLY1305, alg)
        assertTrue(useKey)
    }

    @Test fun `back after max(lock, 60 s) away the seed phrase and the secrets go`() {
        for ((lock, away) in listOf(0L to 60_000L, 60_000L to 60_000L, 300_000L to 300_000L, 300_000L to 3_600_000L)) {
            val s = decrypted()
            s.guard.stopped(1_000)
            assertTrue("$lock/$away", s.guard.started(1_000 + away, lockAfterMs = lock))
            s.assertWiped()
        }
    }

    @Test fun `a short trip away and a rotation keep everything`() {
        for ((lock, away) in listOf(0L to 59_999L, 60_000L to 59_999L, 300_000L to 299_999L)) {
            val s = decrypted()
            s.guard.stopped(0)
            assertFalse("$lock/$away", s.guard.started(away, lockAfterMs = lock))
            assertEquals(seed, s.output)
            assertEquals("contraseña", s.password)
        }
        // A rotation: the old screen stops and is disposed, the new one starts at once.
        val s = decrypted()
        s.guard.stopped(10_000)
        s.guard.stopped(10_001)
        assertFalse(s.guard.started(10_050, lockAfterMs = 0))
        assertEquals(seed, s.output)
    }

    @Test fun `the time in another app in multi-window counts as away`() {
        val s = decrypted()
        s.guard.otherAppFocused(0)
        assertFalse(s.guard.otherAppLeft(59_999, lockAfterMs = 0))
        assertEquals(seed, s.output)
        s.guard.otherAppFocused(100_000)
        assertTrue(s.guard.otherAppLeft(160_000, lockAfterMs = 0))
        s.assertWiped()
    }

    @Test fun `a lock of the vault with the app in use wipes, one from leaving the app does not`() {
        val s = decrypted()
        s.guard.vaultStateIs(unlocked = true) { error("not a lock") }
        s.guard.vaultStateIs(unlocked = false) { LockSeen(LockReason.MANUAL, appInForeground = true, now = 1_000, lockAfterMs = 300_000) }
        s.assertWiped()
        // Leaving the app locks it at once (MainActivity.onStop): the time away decides instead.
        val t = decrypted()
        t.guard.vaultStateIs(unlocked = true) { error("not a lock") }
        t.guard.vaultStateIs(unlocked = false) { LockSeen(LockReason.LEFT_APP, appInForeground = false, now = 1_000, lockAfterMs = 0) }
        assertEquals(seed, t.output)
        // The idle lock keeps a screen in use, and wipes one left alone for the lock time.
        val u = decrypted()
        u.guard.vaultStateIs(unlocked = true) { error("not a lock") }
        u.guard.used(250_000)
        u.guard.vaultStateIs(unlocked = false) { LockSeen(LockReason.IDLE, appInForeground = true, now = 300_000, lockAfterMs = 300_000) }
        assertEquals(seed, u.output)
        val v = decrypted()
        v.guard.vaultStateIs(unlocked = true) { error("not a lock") }
        v.guard.vaultStateIs(unlocked = false) { LockSeen(LockReason.IDLE, appInForeground = true, now = 300_000, lockAfterMs = 300_000) }
        v.assertWiped()
        // With no vault at all (never open), nothing is a lock.
        val w = decrypted()
        w.guard.vaultStateIs(unlocked = false) { error("not a lock") }
        assertEquals(seed, w.output)
    }

    @Test fun `a run going on when the screen is wiped does not bring its result back`() {
        val s = decrypted()
        val run = s.token()
        assertTrue(s.isCurrent(run))
        s.guard.stopped(0)
        s.guard.started(60_000, lockAfterMs = 0)
        assertFalse(s.isCurrent(run))
        // A run started after the wipe is current.
        assertTrue(s.isCurrent(s.token()))
    }

    @Test fun `files and leaked passwords forget the secrets and the answer, and keep the rest`() {
        val f = FileCryptoSession().apply {
            useKey = true
            password = "contraseña"
            keyHex = "00".repeat(32)
            message = true to "Archivo descifrado y guardado."
        }
        f.guard.stopped(0)
        assertTrue(f.guard.started(60_000, lockAfterMs = 0))
        assertEquals("", f.password)
        assertEquals("", f.keyHex)
        assertNull(f.message)
        assertTrue(f.useKey)

        val b = BreachSession().apply {
            password = "123456"
            result = false to "⚠ Aparece 3 veces en filtraciones."
        }
        b.guard.vaultStateIs(unlocked = true) { error("not a lock") }
        b.guard.vaultStateIs(unlocked = false) { LockSeen(LockReason.MANUAL, appInForeground = true, now = 0, lockAfterMs = 0) }
        assertEquals("", b.password)
        assertNull(b.result)
    }

    @Test fun `M1 typing is use, so the idle lock keeps what is being typed`() {
        // The soft keyboard sends no key event and no tap lands on the app: each change of a field calls used().
        for (session in listOf<GuardedSession>(decrypted(), FileCryptoSession().apply { password = "contraseña" }, BreachSession().apply { password = "123" })) {
            session.guard.vaultStateIs(unlocked = true) { error("not a lock") }
            session.guard.used(1_000) // the last tap, on the field
            session.guard.used(61_000) // a letter typed 60 s later
            session.guard.vaultStateIs(unlocked = false) { LockSeen(LockReason.IDLE, appInForeground = true, now = 62_000, lockAfterMs = 60_000) }
            when (session) {
                is TextCryptoSession -> assertEquals(seed, session.output)
                is FileCryptoSession -> assertEquals("contraseña", session.password)
                is BreachSession -> assertEquals("123", session.password)
            }
        }
        // Without the typing, the same lock wipes.
        val s = decrypted()
        s.guard.vaultStateIs(unlocked = true) { error("not a lock") }
        s.guard.used(1_000)
        s.guard.vaultStateIs(unlocked = false) { LockSeen(LockReason.IDLE, appInForeground = true, now = 62_000, lockAfterMs = 60_000) }
        s.assertWiped()
    }

    @Test fun `M2 a wipe gives the decrypted copy waiting for the save dialog up`() {
        val discarded = mutableListOf<String>()
        val f = FileCryptoSession(onDiscard = { discarded += it })
        f.pending = PendingCopy("/cache/dec123", f.token())
        f.guard.stopped(0)
        assertTrue(f.guard.started(60_000, lockAfterMs = 0))
        assertNull("the dialog's answer finds nothing to save", f.takePending())
        assertEquals(listOf("/cache/dec123"), discarded)
        // Back in time: the answer takes the copy (once), and nothing is deleted.
        val g = FileCryptoSession(onDiscard = { discarded += it })
        g.pending = PendingCopy("/cache/dec456", g.token())
        g.guard.stopped(0)
        assertFalse(g.guard.started(59_999, lockAfterMs = 0))
        assertEquals("/cache/dec456", g.takePending()?.path)
        assertNull(g.takePending())
        g.wipe()
        assertEquals(listOf("/cache/dec123"), discarded)
    }

    @Test fun `the guard is the rule of PIN y codigos`() {
        var wipes = 0
        val g = ScreenGuard(createdAt = 0) { wipes++ }
        g.stopped(0)
        assertFalse(g.started(59_999, lockAfterMs = 0))
        g.stopped(100_000)
        assertTrue(g.started(400_000, lockAfterMs = 300_000))
        assertEquals(1, wipes)
    }

    @Test fun `COV1-2 a slow earlier file never shows its fingerprint under a later file's name`() {
        val h = FileHashSession()
        val a = h.start()
        assertTrue(h.named(a, "grande.iso"))
        val b = h.start()
        assertNull("the earlier name goes at once", h.name)
        assertTrue(h.named(b, "pequeño.txt"))
        assertTrue(h.busy)
        // B (small) ends first.
        assertTrue(h.finish(b, "hash-de-b"))
        assertEquals("pequeño.txt", h.name)
        assertEquals("hash-de-b", h.hash)
        assertFalse(h.busy)
        // A ends later: dropped, and its name never comes back.
        assertFalse(h.finish(a, "hash-de-a"))
        assertFalse(h.named(a, "grande.iso"))
        assertEquals("pequeño.txt", h.name)
        assertEquals("hash-de-b", h.hash)
        // A ends first while B still runs: «Calculando…» stays.
        val c = h.start()
        assertFalse(h.finish(b, "hash-de-b"))
        assertTrue(h.busy)
        assertNull(h.hash)
        assertTrue(h.finish(c, null)) // unreadable
        assertNull(h.hash)
        assertFalse(h.busy)
        // M8: the screen says it could not be read, until the next pick.
        assertTrue(h.failed)
        h.start()
        assertFalse(h.failed)
    }
}
