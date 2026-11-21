package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.PasswordUse
import app.seguridad.crypto.argon2id
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * RT-21, part a (ruling of the director on the final attack): a vault's data are decoded once when it opens with the
 * master password and when a copy is imported; what is shown is what was written (decision (b)); and a copy that its
 * checks refuse spends no second Argon2id (the checks run before any write or wrap).
 */
class SingleDecodeTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba-unica"
    private var wraps = 0
    private val counting: KekDerive = { m, salt, p ->
        wraps++
        argon2id(m, salt, p, use = PasswordUse.EXISTING)
    }

    private fun store(dir: File, kdf: KdfParams = KdfParams(1024, 1, 1)) = VaultStore(
        File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 1_800_000_000_000L + 3_600_000L },
        kdf = { kdf }, kekDerive = counting,
    )

    private fun note(id: String, title: String) = VaultEntry(id, EntryType.NOTE, title, notes = "n $id", createdAt = 0, updatedAt = 0)

    private fun VaultStore.data() = (state.value as VaultState.Unlocked).data

    private suspend fun filled(dir: File): VaultStore = store(dir).apply {
        load()
        create(master.toCharArray())
        upsert(note("b", "beta"))
        upsert(note("a", "Alfa"))
    }

    private fun onDisk(dir: File, key: ByteArray) = VaultCrypto.read(VaultCrypto.decodeFile(File(dir, "vault.bin").readText()), key)

    @Test fun anUnlockWithTheMasterDecodesOnce() = runTest {
        val dir = tmp.newFolder("a")
        val s = filled(dir)
        s.lock()
        VaultCrypto.decodes.set(0)
        s.unlock(master.toCharArray())
        assertEquals(1, VaultCrypto.decodes.get())
        assertEquals(onDisk(dir, s.dataKeyCopy()!!).sortedForShow(), s.data())
    }

    @Test fun anImportDecodesOnceAndShowsWhatItWrote() = runTest {
        val src = filled(tmp.newFolder("src"))
        val v4 = ByteArrayOutputStream().also { assertTrue(src.exportBackup(it)) }.toByteArray()
        val v3Text = javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString()
        val v3 = ByteArrayOutputStream().also { writeBackup(it, v3Text, emptyMap()) }.toByteArray()
        for ((name, copy, pw, newTag) in listOf(
            Quad("v4", v4, master, false), Quad("v4-tag", v4, master, true), Quad("v3", v3, "maestra-de-prueba-143", false),
        )) {
            val dir = tmp.newFolder(name)
            val s = store(dir).apply { load() }
            VaultCrypto.decodes.set(0)
            s.importReplace(ByteArrayInputStream(copy), pw.toCharArray(), newTag)
            assertEquals(name, 1, VaultCrypto.decodes.get())
            assertEquals(name, onDisk(dir, s.dataKeyCopy()!!).sortedForShow(), s.data())
        }
    }

    /** A copy its checks refuse (two identities) is refused before the key is wrapped again with the cost of Ajustes. */
    @Test fun aRefusedCopySpendsNoSecondArgon2id() = runTest {
        val (file, dek) = VaultCrypto.create(master.toCharArray(), 0, KdfParams(1024, 1, 1))
        val two = listOf(
            VaultEntry("y1", EntryType.IDENTITY, "Yo", createdAt = 0, updatedAt = 0),
            VaultEntry("y2", EntryType.IDENTITY, "Otra", createdAt = 0, updatedAt = 0),
        )
        val json = VaultCrypto.encodeFile(VaultCrypto.write(file, dek, VaultData(entries = two)))
        val copy = ByteArrayOutputStream().also { writeBackup(it, json, emptyMap()) }.toByteArray()
        val s = store(tmp.newFolder("d"), kdf = KdfParams(2048, 2, 1)).apply { load() }
        wraps = 0
        try {
            s.importReplace(ByteArrayInputStream(copy), master.toCharArray())
            fail("It imported")
        } catch (e: CryptoFailure) {
            assertEquals(SEVERAL_IDENTITIES, e.message)
        }
        assertEquals(0, wraps)
    }

    private data class Quad(val name: String, val copy: ByteArray, val pw: String, val newTag: Boolean)
}
