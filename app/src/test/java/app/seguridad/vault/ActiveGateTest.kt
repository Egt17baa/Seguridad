package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.0, tarea 10 (n-1 y n-2 de la fase A): un cambio de bóveda en mitad de un desbloqueo, después de la primera
 * comprobación de bóveda activa. Con la maestra, durante Argon2id; con la huella, mientras se descifra la bóveda. En
 * los dos casos, la segunda comprobación, justo antes de escribir, deja el archivo como estaba, la bóveda bloqueada y
 * la marca del reloj sin tocar.
 */
class ActiveGateTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba-puerta"
    private val kdfParams = KdfParams(1024, 1, 1)
    private fun file() = File(tmp.root, "vault.bin")

    /** A vault made and locked, and its data key (what the fingerprint would release). */
    private suspend fun made(): ByteArray {
        val s = VaultStore(file(), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { kdfParams })
        s.load()
        s.create(master.toCharArray())
        val key = s.dataKeyCopy()!!
        s.lock()
        return key
    }

    /** n-2: the switch happens while Argon2id runs ([kdf] is read by the master's check, after the first gate). */
    @Test fun aSwitchDuringArgon2idWritesNothing() = runTest {
        made()
        val before = file().readBytes()
        var switched = false
        var armed = false
        var resets = 0
        val s = VaultStore(
            file(), Dispatchers.Unconfined, Dispatchers.Unconfined,
            kdf = { if (armed) switched = true; kdfParams },
            onClockReset = { resets++ },
            active = { !switched },
        )
        s.load()
        armed = true
        s.unlock(master.toCharArray())
        assertTrue("It was the active one when it started", switched)
        assertEquals(VaultState.Locked, s.state.value)
        assertArrayEquals("The file, byte for byte", before, file().readBytes())
        assertEquals("The clock mark is not lowered", 0, resets)
    }

    /** n-1: the switch happens while the fingerprint's unlock decrypts the vault (after the trash's mark is read). */
    @Test fun aSwitchDuringTheFingerprintsDecryptionWritesNothing() = runTest {
        val key = made()
        val before = file().readBytes()
        var switched = false
        var armed = false
        val s = VaultStore(
            file(), Dispatchers.Unconfined, Dispatchers.Unconfined,
            kdf = { kdfParams },
            trashClockMark = { if (armed) switched = true; 0L },
            active = { !switched },
        )
        s.load()
        armed = true
        s.unlockWithKey(key.copyOf())
        assertTrue(switched)
        assertEquals(VaultState.Locked, s.state.value)
        assertArrayEquals("The file, byte for byte", before, file().readBytes())
        // Not switched: the same key opens it.
        armed = false
        switched = false
        s.unlockWithKey(key)
        assertTrue(s.state.value is VaultState.Unlocked)
    }
}
