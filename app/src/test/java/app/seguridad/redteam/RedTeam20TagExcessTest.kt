package app.seguridad.redteam

import app.seguridad.core.appJson
import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.randomBytes
import app.seguridad.vault.COPY_DAMAGED
import app.seguridad.vault.COPY_LIST_MAX
import app.seguridad.vault.EntryType
import app.seguridad.vault.EntryVersion
import app.seguridad.vault.MAX_TAGS
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.checkBackup
import app.seguridad.vault.copyExtraTags
import app.seguridad.vault.dataAad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Random
import java.util.UUID

/**
 * RT-25, part b (ruling of round 2 of the fixes of the attack, as the director corrected it): a copy whose tags over
 * [MAX_TAGS], summed over every entry and every version ([copyExtraTags]), are more than [COPY_LIST_MAX] is refused as
 * [COPY_DAMAGED] by «Importar copia» and «Comprobar una copia»; nothing the screens write ever counts, and neither
 * versions nor the trash raise the allowance. Invented data only (decision (e)).
 */
class RedTeam20TagExcessTest {
    @get:Rule val tmp = TemporaryFolder()

    private val rnd = Random(20261118L)
    private fun uuid() = UUID(rnd.nextLong(), rnd.nextLong()).toString()
    private var k = 0
    private fun distinct(n: Int) = List(n) { (k++).toString(36).padStart(5, 'a') }

    private fun login(tags: List<String>, deletedAt: Long? = null, history: List<EntryVersion> = emptyList()) =
        VaultEntry(uuid(), EntryType.LOGIN, "t", createdAt = 1, updatedAt = 1, tags = tags, deletedAt = deletedAt, history = history)

    /** All the tags over [MAX_TAGS], counted to the end (the check stops once over [COPY_LIST_MAX]). */
    private fun excess(entries: List<VaultEntry>) = copyExtraTags(entries, stopAbove = Long.MAX_VALUE)

    private fun v4Copy(entries: List<VaultEntry>): ByteArray {
        val json = appJson.encodeToString(VaultData.serializer(), VaultData(entries = entries, vaultTag = "b".repeat(32)))
        return rt20Copy(rt20HostileFile(json).first)
    }

    private fun store() = VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF })
        .apply { runBlocking { load() } }

    private fun check(copy: ByteArray, master: String) = runBlocking {
        checkBackup(ByteArrayInputStream(copy), master.toCharArray(), tmp.newFolder(), Dispatchers.Unconfined, Dispatchers.Unconfined)
    }

    /** Imported («Importar copia») and checked («Comprobar una copia»). */
    private fun passes(name: String, copy: ByteArray, master: String = RT20_MASTER) {
        val s = store()
        runBlocking { s.importReplace(ByteArrayInputStream(copy), master.toCharArray()) }
        assertTrue(name, s.state.value is VaultState.Unlocked)
        check(copy, master)
    }

    /** Refused with [COPY_DAMAGED] by both, nothing imported. */
    private fun refused(name: String, copy: ByteArray, master: String = RT20_MASTER) {
        val s = store()
        try {
            runBlocking { s.importReplace(ByteArrayInputStream(copy), master.toCharArray()) }
            fail("$name: imported")
        } catch (e: CryptoFailure) {
            assertEquals(name, COPY_DAMAGED, e.message)
        }
        assertEquals(name, VaultState.NoVault, s.state.value)
        try {
            check(copy, master)
            fail("$name: checked")
        } catch (e: CryptoFailure) {
            assertEquals(name, COPY_DAMAGED, e.message)
        }
    }

    @Test fun twoThousandEntriesWithTenUniqueTagsEachPass() {
        val entries = List(2_000) { login(distinct(10)) }
        assertEquals(0L, excess(entries))
        passes("2 000 entries, 20 000 distinct tags", v4Copy(entries))
    }

    /**
     * A real copy of 1.4.3 (version 3) whose first entry got [tags] distinct tags written by hand, as 1.x let them be,
     * and [oldPasswords] old passwords (1.x kept up to 5); sealed again with its own key. The copy and the excess as
     * «Comprobar una copia» reads it (the data as stored, before the upgrade to v4).
     */
    private fun oneXCopy(tags: Int, oldPasswords: Int = 0, entries: Int = 1): Pair<ByteArray, Long> {
        val master = "maestra-de-prueba-143"
        val file = VaultCrypto.decodeFile(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        val dek = VaultCrypto.unlock(file, master.toCharArray())
        val version = VaultCrypto.resolve(file, dek).version
        val list = appJson.parseToJsonElement(VaultCrypto.readJson(file, dek)).jsonArray
        check(list.size >= entries)
        val changed = list.mapIndexed { i, e ->
            if (i >= entries) return@mapIndexed e
            var o = e.jsonObject + ("tags" to JsonArray(distinct(tags).map { JsonPrimitive(it) }))
            if (oldPasswords > 0) {
                o = o + ("passwordHistory" to JsonArray(List(oldPasswords) { JsonObject(mapOf("password" to JsonPrimitive("antigua-$it"), "changedAt" to JsonPrimitive(1_700_000_000_000L + it))) }))
            }
            JsonObject(o)
        }
        val json = JsonArray(changed).toString().toByteArray()
        val nonce = randomBytes(NONCE_BYTES)
        val sealed = file.copy(data = Base64.getEncoder().encodeToString(nonce + aead(Aead.AES_256_GCM, true, dek, nonce, dataAad(file, version), json)))
        val copy = ByteArrayOutputStream().also { app.seguridad.vault.writeBackup(it, VaultCrypto.encodeFile(sealed), emptyMap()) }.toByteArray()
        return copy to excess(VaultCrypto.read(sealed, dek, copy = true).entries)
    }

    @Test fun aCopyOf1xWithFiftyTagsInAnEntryPasses() {
        val (copy, extra) = oneXCopy(50, oldPasswords = 5)
        assertEquals(40L, extra)
        passes("1.x with 50 tags in an entry", copy, "maestra-de-prueba-143")
    }

    /**
     * Point 5 of the closure (a doubt for the director, not fixed): «Importar copia» checks a copy of before 2.0 after its
     * upgrade to v4, where each old password becomes a version with the entry's tags (Vault.kt `upgraded`), fitted to
     * 64 KiB of history per entry; «Comprobar una copia» checks the data as stored. So the same 1.x copy (two entries of
     * 2 010 tags with 5 old passwords each: 4 000 over 10 as stored, several times that once upgraded) passes «Comprobar»
     * and is refused by «Importar». This test pins what happens today; the report asks the director whether it should.
     */
    @Test fun aCopyOfBefore20CountsItsUpgradedVersionsOnlyWhenImported() {
        val master = "maestra-de-prueba-143"
        val (copy, extra) = oneXCopy(MAX_TAGS + 2_000, oldPasswords = 5, entries = 2)
        assertEquals("as stored", 4_000L, extra)
        check(copy, master) // «Comprobar una copia»: it passes.
        val s = store()
        try {
            runBlocking { s.importReplace(ByteArrayInputStream(copy), master.toCharArray()) }
            fail("imported")
        } catch (e: CryptoFailure) {
            assertEquals(COPY_DAMAGED, e.message)
        }
        assertEquals(VaultState.NoVault, s.state.value)
    }

    /**
     * A vault of one's own with 30 000 distinct tags in 100 entries (only from a copy made by hand) opens with the master
     * and with the fingerprint's key (its own file has no bound), but its copy is refused: 29 000 tags over 10.
     */
    @Test fun aVaultOfItsOwnWith30000TagsOpensButItsCopyIsRefused() = runBlocking {
        val dir = tmp.newFolder("propia")
        val s = VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF }).apply { load(); create(RT20_MASTER.toCharArray()) }
        val key = s.dataKeyCopy()!!
        val file = VaultCrypto.decodeFile(File(dir, "vault.bin").readText())
        val many = List(100) { login(distinct(300)) }
        File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, key, VaultCrypto.read(file, key).copy(entries = many))))
        val again = VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF }).apply { load() }
        again.unlock(RT20_MASTER.toCharArray())
        assertEquals(30_000, (again.state.value as VaultState.Unlocked).entries.sumOf { it.tags.size })
        again.lock()
        again.unlockWithKey(key.copyOf())
        assertEquals(30_000, (again.state.value as VaultState.Unlocked).entries.sumOf { it.tags.size })
        assertEquals(29_000L, excess(many))
        val copy = ByteArrayOutputStream().also { assertTrue(again.exportBackup(it)) }.toByteArray()
        refused("the copy of a vault of its own with 29 000 tags over 10", copy)
    }

    /** The copy of the attack (RedTeam20RecheckTest.rt25_: 60 entries of 5 000 distinct tags): 299 400 over 10. */
    @Test fun theCopyOfTheAttackIsRefused() {
        val entries = List(60) { login(distinct(5_000)) }
        assertEquals(299_400L, excess(entries))
        refused("the attack", v4Copy(entries))
    }

    @Test fun justAtTheCapPassesAndOneMoreIsRefused() {
        val at = List(100) { login(distinct(MAX_TAGS + 100)) }
        assertEquals(COPY_LIST_MAX.toLong(), excess(at))
        passes("exactly at the cap", v4Copy(at))
        val over = at.dropLast(1) + login(distinct(MAX_TAGS + 101))
        assertEquals(COPY_LIST_MAX + 1L, excess(over))
        refused("one over the cap", v4Copy(over))
    }

    /**
     * Versions and the trash only add to the sum, never to the allowance: 200 entries in the trash (gone long ago) with
     * 20 versions of 10 tags each, which a cap of `MAX_TAGS × (entries + versions)` would have raised to 42 000, still
     * leave room for only 10 000 over; and tags over 10 in versions count too.
     */
    @Test fun versionsAndTheTrashDoNotRaiseIt() {
        val trash = List(200) { login(distinct(10), deletedAt = 1L, history = List(20) { v -> EntryVersion(v.toLong(), login(distinct(10))) }) }
        val over = trash + login(distinct(MAX_TAGS + 5_000)) + login(distinct(MAX_TAGS + 5_001))
        assertEquals(COPY_LIST_MAX + 1L, excess(over))
        refused("the trash and its versions do not raise it", v4Copy(over))
        val inVersions = listOf(login(distinct(10), history = List(20) { v -> EntryVersion(v.toLong(), login(distinct(MAX_TAGS + 501))) }))
        assertEquals(20L * 501, excess(inVersions))
        refused("tags over 10 in versions count", v4Copy(inVersions))
    }
}
