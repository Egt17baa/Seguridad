package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 2.0, review I-1 and I-2 of task 5: the fields of a copy made elsewhere (its maker knows its master password) as the app
 * writes them, or the copy is damaged, in «Importar copia» and in «Comprobar una copia» alike ([checkCopyEntries]): no id
 * twice (a repeated template secret would come back as a visible field), at most 30, names of 100 and values of 10 000
 * code points (400 000 fields would freeze the card). In the entry and in every version of its history.
 */
class CopyFieldsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba-campos"
    private var clock = 1_800_000_000_000L

    private fun field(id: String, name: String = "Campo", value: String = "v", kind: FieldKind = FieldKind.TEXT) = CustomField(id, name, kind, value)

    private fun wifi(fields: List<CustomField>, history: List<EntryVersion> = emptyList()) =
        VaultEntry("w", EntryType.WIFI, "Casa", createdAt = 1, updatedAt = 1, fields = fields, history = history)

    private val hostile: List<Pair<String, VaultEntry>> = listOf(
        "a template secret twice" to wifi(listOf(field("password", kind = FieldKind.HIDDEN, value = "señuelo"), field("password", value = "clave-real"))),
        "an id of the user's twice" to wifi(listOf(field("x"), field("x"))),
        "31 fields" to wifi((0..VaultLimits.FIELDS_MAX).map { field("f$it") }),
        "a name of 101" to wifi(listOf(field("f", name = "n".repeat(101)))),
        "a value of 10 001" to wifi(listOf(field("f", value = "v".repeat(10_001)))),
        "in a version of the history" to wifi(emptyList(), listOf(EntryVersion(1, wifi(listOf(field("p"), field("p")))))),
        "31 in a version" to wifi(emptyList(), listOf(EntryVersion(1, wifi((0..VaultLimits.FIELDS_MAX).map { field("f$it") })))),
    )

    @Test fun repeatedOrOversizedFieldsAreDamaged() {
        for ((what, e) in hostile) {
            assertEquals(what, COPY_DAMAGED, assertThrows(what, CryptoFailure::class.java) { checkCopyEntries(listOf(e)) }.message)
        }
        // Within every limit, with emoji counted as one: fine.
        checkCopyEntries(
            listOf(
                wifi(
                    (0 until VaultLimits.FIELDS_MAX - 1).map { field("f$it") } +
                        field("e", name = "😀".repeat(100), value = "😀".repeat(10_000)),
                    listOf(EntryVersion(1, wifi(listOf(field("p"))))),
                ),
            ),
        )
    }

    private fun store(dir: File) = VaultStore(
        File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock },
    )

    /** A vault in [dir] whose file holds [data] as it is (written past the store's checks), opened. */
    private suspend fun hostileVault(dir: File, data: VaultData): VaultStore {
        val maker = store(dir).apply {
            load()
            create(master.toCharArray())
        }
        val file = VaultCrypto.decodeFile(maker.exportText()!!)
        val key = VaultCrypto.unlock(file, master.toCharArray())
        File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, key, data)))
        return store(dir).apply {
            load()
            unlock(master.toCharArray())
        }
    }

    @Test fun importAndCheckRefuseThem() = runTest {
        for ((what, e) in hostile) {
            val maker = hostileVault(tmp.newFolder(), VaultData(entries = listOf(e)))
            // «Importar copia» (the JSON and the .sgbak): nothing changes.
            val mine = store(tmp.newFolder()).apply {
                load()
                create(master.toCharArray())
                upsert(VaultEntry("mía", EntryType.NOTE, "Mía", createdAt = 0, updatedAt = 0))
            }
            val json = maker.exportText()!!
            assertEquals(what, COPY_DAMAGED, assertThrows(what, CryptoFailure::class.java) { runBlocking { mine.importReplace(json, master.toCharArray()) } }.message)
            val backup = ByteArrayOutputStream().also { assertTrue(what, maker.exportBackup(it)) }.toByteArray()
            assertEquals(what, COPY_DAMAGED, assertThrows(what, CryptoFailure::class.java) { runBlocking { mine.importReplace(ByteArrayInputStream(backup), master.toCharArray()) } }.message)
            assertEquals(what, listOf("mía"), (mine.state.value as VaultState.Unlocked).entries.map { it.id })
            // «Comprobar una copia».
            assertEquals(
                what, COPY_DAMAGED,
                assertThrows(what, CryptoFailure::class.java) {
                    runBlocking { checkBackup(ByteArrayInputStream(backup), master.toCharArray(), tmp.newFolder(), Dispatchers.Unconfined, Dispatchers.Unconfined) }
                }.message,
            )
        }
    }
}
