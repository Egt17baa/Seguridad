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

    /**
     * Ronda 2 (n-2), first case: the derivation itself cannot run here (a cost of 96 MiB on a phone whose heap cannot give
     * it, injected): [KDF_OUT_OF_MEMORY], said as it is.
     */
    @Test fun aDerivationWithoutMemoryIsSaid() = runTest {
        val (file, _) = VaultCrypto.create("maestra-de-prueba-buena".toCharArray(), 0, KdfParams(96 * 1024, 1, 1))
        try {
            VaultCrypto.unlock(file, "maestra-de-prueba-buena".toCharArray(), heapKiB = 96L * 1024)
            fail("It opened")
        } catch (e: CryptoFailure) {
            assertEquals(KDF_OUT_OF_MEMORY, masterUnlockErrorText(e))
        }
    }

    /**
     * Ronda 2 (n-2), second case: the master password is right, and wrapping the key again at the cost chosen in Ajustes
     * (afterMasterUnlock) has no memory (the cost injected to fail once). It says so, the file is as it was byte for byte,
     * the vault stays locked, and the next try opens.
     */
    @Test fun noMemoryWhenWrappingAgainLeavesTheFileAsItWas() = runTest {
        var failOnce = false
        val s = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined,
            kdf = { if (failOnce) { failOnce = false; throw CryptoFailure(KDF_OUT_OF_MEMORY) } else KdfParams(1024, 1, 1) },
        )
        s.load()
        s.create("maestra-de-prueba-buena".toCharArray())
        s.lock()
        val before = File(tmp.root, "vault.bin").readBytes()
        failOnce = true
        try {
            s.unlock("maestra-de-prueba-buena".toCharArray())
            fail("It opened")
        } catch (e: CryptoFailure) {
            assertEquals(KDF_OUT_OF_MEMORY, masterUnlockErrorText(e))
        }
        org.junit.Assert.assertArrayEquals(before, File(tmp.root, "vault.bin").readBytes())
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("maestra-de-prueba-buena".toCharArray())
        org.junit.Assert.assertTrue(s.state.value is VaultState.Unlocked)
    }

    /**
     * Ronda 2 (n-5): «Contraseña incorrecta» of a check of the master password comes from one place ([WRONG_MASTER],
     * through [masterUnlockErrorText]): no screen writes the words on its own.
     */
    @Test fun theWordsComeFromOnePlace() {
        val main = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/app/seguridad") }.first { it.isDirectory }
        val found = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> f.readLines().withIndex().filter { "\"Contraseña incorrecta\"" in it.value }.map { "${f.name}:${it.index + 1}" } }.toList()
        assertEquals(listOf("Vault.kt"), found.map { it.substringBefore(":") })
    }
}
