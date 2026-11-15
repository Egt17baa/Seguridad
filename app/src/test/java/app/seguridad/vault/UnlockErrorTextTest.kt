package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KDF_OUT_OF_MEMORY
import app.seguridad.crypto.KDF_PARAMS_INVALID
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.0, tarea 10, fase C, ronda 1 (I-2): el fallo de abrir la bóveda con la maestra, en la pantalla de bloqueo. Lo que el
 * almacén distingue antes de probar la clave (un archivo que esta versión no abre, un Argon2id sin memoria, unos
 * parámetros de cifrado imposibles) se dice tal cual: con la maestra buena nadie debe leer «Contraseña incorrecta» y
 * pulsar «He olvidado la contraseña maestra», que borra la bóveda. Todo lo demás, «Contraseña incorrecta» palabra por
 * palabra; nunca el mensaje de una excepción cualquiera.
 */
class UnlockErrorTextTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun whatTheStoreTellsApartIsSaid() {
        assertEquals(NOT_A_VAULT, masterUnlockErrorText(CryptoFailure(NOT_A_VAULT)))
        assertEquals(KDF_OUT_OF_MEMORY, masterUnlockErrorText(CryptoFailure(KDF_OUT_OF_MEMORY)))
        assertEquals(KDF_PARAMS_INVALID, masterUnlockErrorText(CryptoFailure(KDF_PARAMS_INVALID)))
        assertEquals("Parámetros de cifrado no válidos", KDF_PARAMS_INVALID)
        // Anything else: the frozen words, never the exception's message.
        assertEquals("Contraseña incorrecta", WRONG_MASTER)
        for (other in listOf(CryptoFailure(), CryptoFailure("lo que sea"), CryptoFailure(BACKUP_DAMAGED))) {
            assertEquals(WRONG_MASTER, masterUnlockErrorText(other))
        }
    }

    private fun store() = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })

    private suspend fun failureOf(master: String): CryptoFailure {
        val s = store().apply { load() }
        try {
            s.unlock(master.toCharArray())
        } catch (e: CryptoFailure) {
            return e
        }
        fail("It opened")
        throw AssertionError()
    }

    @Test fun aWrongPasswordIsStillAWrongPassword() = runTest {
        store().apply { load(); create("maestra-de-prueba-buena".toCharArray()) }
        assertEquals(WRONG_MASTER, masterUnlockErrorText(failureOf("maestra-de-prueba-mala")))
    }

    /** A header asking for impossible Argon2id parameters: refused before any derivation, and said so. */
    @Test fun impossibleParametersAreSaid() = runTest {
        store().apply { load(); create("maestra-de-prueba-buena".toCharArray()) }
        val f = File(tmp.root, "vault.bin")
        f.writeText(f.readText().replace(Regex("\"memoryKiB\":\\d+"), "\"memoryKiB\":999999999"))
        assertEquals(KDF_PARAMS_INVALID, masterUnlockErrorText(failureOf("maestra-de-prueba-buena")))
    }
}
