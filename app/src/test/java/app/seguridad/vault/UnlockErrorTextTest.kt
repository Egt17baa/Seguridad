package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KDF_OUT_OF_MEMORY
import app.seguridad.crypto.KDF_PARAMS_INVALID
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.PasswordUse
import app.seguridad.crypto.argon2id
import app.seguridad.crypto.outOfMemoryAsFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
     * Ronda 2 (n-2), second case, as it stands after row 45: the master password is right and the cost of Ajustes cannot
     * be read ([kdf] throws [KDF_OUT_OF_MEMORY], injected; it runs before afterMasterUnlock, so nothing is wrapped). It
     * says so, the file is as it was byte for byte, the vault stays locked, and the next try opens. Running out of memory
     * while wrapping again opens the vault with the file's cost: [noMemoryWhenWrappingAgainOpensWithTheFileCost].
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

    private val fileCost = KdfParams(1024, 1, 1)

    /** Runs out of memory (as Argon2id would, through [outOfMemoryAsFailure]) for any cost above the file's, always. */
    private val noMemoryAboveTheFile: KekDerive = { m, salt, p ->
        if (p.memoryKiB > fileCost.memoryKiB) outOfMemoryAsFailure(p) { throw OutOfMemoryError("Failed to allocate") }
        argon2id(m, salt, p, use = PasswordUse.EXISTING)
    }

    /**
     * Branch review, row 45 (A I-2, option B), test n.º 2: Ajustes asks for more than the file has, and wrapping again
     * with it runs out of memory every time. Each unlock still opens, with the file's cost (never lowered), the master
     * check recorded, and [KDF_COST_KEPT] told once.
     */
    @Test fun noMemoryWhenWrappingAgainOpensWithTheFileCost() = runTest {
        var clock = 1_000L
        var told = 0
        var profile = fileCost
        val f = File(tmp.root, "vault.bin")
        val s = VaultStore(
            f, Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { profile },
            kekDerive = noMemoryAboveTheFile, onCostKept = { told++ },
        )
        s.load()
        s.create("maestra-de-prueba-buena".toCharArray())
        s.lock()
        profile = KdfParams(2048, 2, 1)
        for (round in 1..2) {
            clock += 10_000
            s.unlock("maestra-de-prueba-buena".toCharArray())
            assertTrue("round $round", s.state.value is VaultState.Unlocked)
            val disk = VaultCrypto.decodeFile(f.readText())
            assertEquals(VaultKdf(1024, 1, 1), disk.kdf)
            assertEquals(clock, disk.masterCheckedAt)
            s.lock()
        }
        assertEquals(1, told)
    }

    /** Row 45: the same from the vault of 1.4.3 (version 3): it goes to version 4 with its own cost, said once. */
    @Test fun noMemoryWhenWrappingAgainUpgradesWithTheFileCost() = runTest {
        var told = 0
        val dir = tmp.newFolder("p143")
        val f = File(dir, "vault.bin")
        f.writeText(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        val s = VaultStore(
            f, Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 1_800_000_000_000L + 3_600_000L }, kdf = { KdfParams(2048, 2, 1) },
            kekDerive = noMemoryAboveTheFile, onCostKept = { told++ },
        )
        s.load()
        s.unlock("maestra-de-prueba-143".toCharArray())
        assertTrue(s.state.value is VaultState.Unlocked)
        assertTrue(s.completeUpgrade(s.lockEpoch))
        val disk = VaultCrypto.decodeFile(f.readText())
        assertEquals(VaultFile.VERSION, disk.version)
        assertEquals(VaultKdf(1024, 1, 1), disk.kdf)
        assertEquals(1, told)
    }

    /**
     * Re-review of the branch, A N-1: [KDF_COST_KEPT] is true on the three paths it is told on (a v4 whose save may fail,
     * the upgrade still waiting in memory, an import): it never says the vault was saved.
     */
    @Test fun theCostKeptTextSaysNoSave() {
        assertEquals(
            "No se pudo subir el coste de Argon2id al elegido en Ajustes: ahora no hay memoria suficiente. La bóveda se queda " +
                "con el coste que ya tenía el archivo o la copia. Se volverá a intentar cada vez que la abras con la contraseña " +
                "maestra. Si en Ajustes tienes el coste «Alto», puedes elegir «Estándar».",
            KDF_COST_KEPT,
        )
        assertTrue("se ha guardado" !in KDF_COST_KEPT)
    }

    /** Row 45: a failure that is not of memory, or with no higher cost to reach, still fails as before. */
    @Test fun otherFailuresWhenWrappingAgainStillFail() = runTest {
        val (file, dek) = VaultCrypto.create("maestra-de-prueba-buena".toCharArray(), 0, fileCost)
        var kept = 0
        try {
            VaultCrypto.afterMasterUnlock(
                file, dek, "maestra-de-prueba-buena".toCharArray(), 5, KdfParams(2048, 1, 1),
                derive = { _, _, _ -> throw CryptoFailure("otra cosa") }, onCostKept = { kept++ },
            )
            fail("It wrapped")
        } catch (e: CryptoFailure) {
            assertEquals("otra cosa", e.message)
        }
        assertEquals(0, kept)
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
