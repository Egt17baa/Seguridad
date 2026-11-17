package app.seguridad.redteam

import app.seguridad.core.appJson
import app.seguridad.crypto.CryptoFailure
import app.seguridad.vault.COPY_DAMAGED
import app.seguridad.vault.COPY_LIST_MAX
import app.seguridad.vault.COPY_TREE_MAX_BYTES
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.checkBackup
import app.seguridad.vault.checkCopyShape
import app.seguridad.vault.copyLongestList
import app.seguridad.vault.copyTreeEstimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Random
import java.util.UUID

/**
 * RT-21, part b (ruling of the director on the final attack): the analysis of a copy's data before they are decoded
 * ([checkCopyShape]): its model of the decoded tree is never below what is measured, no legitimate shape at the limit of
 * the data passes 80 % of [COPY_TREE_MAX_BYTES], and hostile shapes are refused before anything is decoded.
 */
class RedTeam20CopyShapeTest {
    @get:Rule val tmp = TemporaryFolder()

    private val rnd = Random(20261117L)
    private fun uuid() = UUID(rnd.nextLong(), rnd.nextLong()).toString()
    private val t = "1800000000000"
    private val mib = 1024 * 1024
    private val limit = VaultLimits.VAULT_MAX_DATA_BYTES + VaultLimits.COPY_DATA_MARGIN_BYTES

    /** A v4 blob of entries made by [one] up to [size] bytes, with [prefix] members of the root before them. */
    private fun entries(size: Int, prefix: String = "", one: () -> String) = buildString(size + 70_000) {
        append("{").append(prefix).append("\"entries\":[")
        var first = true
        while (true) {
            val e = one()
            if (length + e.length + 60 > size) break
            if (!first) append(',')
            first = false
            append(e)
        }
        append("],\"vaultTag\":\"").append("b".repeat(32)).append("\"}")
    }

    private fun login(extra: String = "") = "{\"id\":\"${uuid()}\",\"type\":\"LOGIN\",\"title\":\"\",\"createdAt\":$t,\"updatedAt\":$t$extra}"
    private val tenTags = ",\"tags\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\"]"
    private fun tags(n: Int, one: (Int) -> String) = ",\"tags\":[" + (0 until n).joinToString(",") { "\"${one(it)}\"" } + "]"

    private fun used(): Long {
        repeat(4) { System.gc() }
        return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    /** The model is never below the tree the decoder keeps (live heap after GC), for 1 MiB of each shape. */
    @Test fun theModelIsNeverBelowWhatIsMeasured() {
        fun short() = Integer.toString(rnd.nextInt(1 shl 20), 36)
        val shapes = mapOf(
            "list" to entries(mib) { login(tags(1_000) { short() }) },
            "set" to buildString {
                append("{\"views\":[")
                var i = 0
                while (length < mib) {
                    if (i > 0) append(',')
                    append("{\"id\":\"v$i\",\"name\":\"v\",\"filter\":{\"tags\":[")
                    append((0 until 1_000).joinToString(",") { "\"${short()}\"" })
                    append("]}}")
                    i++
                }
                append("]}")
            },
            "map" to buildString {
                append("{\"tagColors\":{")
                var i = 0
                while (length < mib) {
                    if (i > 0) append(',')
                    append("\"t$i\":\"RED\"")
                    i++
                }
                append("}}")
            },
            "object" to entries(mib) { login() },
        )
        for ((name, json) in shapes) {
            val before = used()
            val data = appJson.decodeFromString(VaultData.serializer(), json)
            val retained = used() - before
            val estimate = copyTreeEstimate(json.toByteArray(), 4)
            println("RT-21 model, $name: ${json.length} bytes of JSON, measured $retained, estimated $estimate (x${"%.2f".format(estimate.toDouble() / retained)})")
            assertTrue("$name: estimated $estimate, measured $retained", estimate >= retained)
            assertTrue(data.toString().isNotEmpty())
        }
    }

    private fun legit(name: String, json: String) {
        val bytes = json.toByteArray()
        assertTrue(name, bytes.size <= limit)
        val estimate = copyTreeEstimate(bytes, 4)
        println("RT-21 legitimate, $name: ${bytes.size} bytes, estimated $estimate (${"%.1f".format(estimate * 100.0 / COPY_TREE_MAX_BYTES)} %)")
        assertTrue("$name: $estimate over 80 % of $COPY_TREE_MAX_BYTES", estimate <= COPY_TREE_MAX_BYTES * 0.8)
        assertTrue(name, copyLongestList(bytes, 4) <= COPY_LIST_MAX)
        checkCopyShape(bytes, 4)
    }

    @Test fun legitimateNotesAndLogins() {
        val note = "Texto de una nota larga sin nada raro. ".repeat(1_500)
        legit("a, notes", entries(limit) { "{\"id\":\"${uuid()}\",\"type\":\"NOTE\",\"title\":\"Nota\",\"notes\":\"$note\",\"createdAt\":$t,\"updatedAt\":$t}" })
        legit("b, logins", entries(limit) { login() })
        legit("c, logins with 10 tags", entries(limit) { login(tenTags) })
    }

    @Test fun legitimateFieldsHistoryAndOldPasswords() {
        legit("d, 30 fields", entries(limit) { login(",\"fields\":[" + (0 until 30).joinToString(",") { "{\"id\":\"${uuid()}\",\"name\":\"x\",\"kind\":\"TEXT\",\"value\":\"y\"}" } + "]") })
        legit("e, 20 versions", entries(limit) { login(",\"history\":[" + (0 until 20).joinToString(",") { "{\"savedAt\":$t,\"entry\":${login()}}" } + "]") })
        legit("g, 5 old passwords", entries(limit) { login(",\"password\":\"p\",\"passwordHistory\":[" + (0 until 5).joinToString(",") { "{\"password\":\"q\",\"changedAt\":$t}" } + "]") })
    }

    @Test fun legitimateOrganization() {
        val folders = (0 until VaultLimits.FOLDERS_MAX).joinToString(",") { "{\"id\":\"${uuid()}\",\"name\":\"Carpeta\",\"order\":$it}" }
        val views = (0 until VaultLimits.VIEWS_MAX).joinToString(",") {
            "{\"id\":\"${uuid()}\",\"name\":\"Vista\",\"filter\":{\"text\":\"x\",\"types\":[\"LOGIN\",\"NOTE\"],\"tags\":[\"a\",\"b\",\"c\"],\"favorites\":true,\"sort\":\"USED\"}}"
        }
        val colors = (0 until VaultLimits.TAG_COLORS_MAX).joinToString(",") { "\"t$it\":\"RED\"" }
        legit("f, organization", entries(limit, "\"folders\":[$folders],\"views\":[$views],\"tagColors\":{$colors},") { login(tenTags) })
    }

    /** (h) The four real files of the tests' resources pass, as copies. */
    @Test fun theRealFilesOfBeforePass() = runBlocking {
        fun text(name: String) = javaClass.getResourceAsStream("/seguridad/$name")!!.use { it.readBytes() }.decodeToString()
        for ((name, master) in listOf("vault-1.1.json" to "maestra-de-prueba", "vault-1.4.3.json" to "maestra-de-prueba-143")) {
            val file = VaultCrypto.decodeFile(text(name))
            val dek = VaultCrypto.unlock(file, master.toCharArray())
            val json = VaultCrypto.readJson(file, dek).toByteArray()
            checkCopyShape(json, VaultCrypto.resolve(file, dek).version)
            VaultCrypto.read(file, dek, copy = true)
        }
        val store = VaultStore(File(tmp.newFolder("x"), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF }).apply { load() }
        store.importReplace(text("export-1.0.2.json"), "maestra-de-prueba".toCharArray())
        val backup = javaClass.getResourceAsStream("/seguridad/backup-1.1.sgbak")!!.use { it.readBytes() }
        checkBackup(ByteArrayInputStream(backup), "maestra-de-prueba".toCharArray(), File(tmp.root, "comprobar"), Dispatchers.Unconfined, Dispatchers.Unconfined)
        Unit
    }

    /** Refused by [checkCopyShape], and through «Importar copia» with nothing decoded ([VaultCrypto.decodes]). */
    private fun refused(name: String, json: String, version: Int = 4) {
        try {
            checkCopyShape(json.toByteArray(), version)
            fail("$name: not refused")
        } catch (e: CryptoFailure) {
            assertEquals(name, COPY_DAMAGED, e.message)
        }
        if (version != 4) return
        val copy = rt20Copy(rt20HostileFile(json).first)
        val store = VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF }).apply { runBlocking { load() } }
        VaultCrypto.decodes.set(0)
        try {
            runBlocking { store.importReplace(ByteArrayInputStream(copy), RT20_MASTER.toCharArray()) }
            fail("$name: imported")
        } catch (e: CryptoFailure) {
            assertEquals(name, COPY_DAMAGED, e.message)
        }
        assertEquals("$name: nothing decoded", 0, VaultCrypto.decodes.get())
        assertEquals(VaultState.NoVault, store.state.value)
    }

    @Test fun repeatedAndSpreadTagsUnderTheListCapAreRefused() {
        refused("24 MiB of «a», 10 000 an entry", entries(limit) { login(tags(10_000) { "a" }) })
        var k = 0
        refused("24 MiB of distinct 5-letter tags, spread", entries(limit) { login(tags(5_000) { (k++).toString(36).padStart(5, 'a') }) })
    }

    @Test fun listsOverTheCapAreRefused() {
        val over = COPY_LIST_MAX + 1
        refused("tagColors", "{\"tagColors\":{" + (0 until over).joinToString(",") { "\"t$it\":\"RED\"" } + "}}")
        refused("folders", "{\"folders\":[" + (0 until over).joinToString(",") { "{\"id\":\"f$it\",\"name\":\"c\"}" } + "]}")
        refused("views", "{\"views\":[" + (0 until over).joinToString(",") { "{\"id\":\"v$it\",\"name\":\"v\",\"filter\":{}}" } + "]}")
        refused("one entry's tags", "{\"entries\":[" + login(tags(over) { "a" }) + "]}")
        // The list of entries is not capped by it.
        val many = "{\"entries\":[" + (0 until over).joinToString(",") { "{\"id\":\"e$it\",\"type\":\"NOTE\",\"title\":\"t\",\"createdAt\":1,\"updatedAt\":1}" } + "]}"
        checkCopyShape(many.toByteArray(), 4)
    }

    @Test fun theTagsOfTwoThousandViewsAreRefused() {
        var k = 0
        val views = buildString {
            append("{\"views\":[")
            for (i in 0 until 2_000) {
                if (i > 0) append(',')
                append("{\"id\":\"v$i\",\"name\":\"v\",\"filter\":{\"tags\":[")
                append((0 until 1_500).joinToString(",") { "\"" + (k++).toString(36).padStart(5, 'a') + "\"" })
                append("]}}")
            }
            append("]}")
        }
        assertTrue(views.length <= limit)
        refused("2 000 views with tags", views)
    }

    @Test fun theSameInAVersion3WithoutItsVersion() {
        val v3 = buildString {
            append('[')
            var first = true
            while (length < limit - 200_000) {
                if (!first) append(',')
                first = false
                append(login(tags(10_000) { "a" }))
            }
            append(']')
        }
        refused("v3, 24 MiB of «a»", v3, version = 3)
        refused("v3, one entry's tags", "[" + login(tags(COPY_LIST_MAX + 1) { "a" }) + "]", version = 3)
        // The root list of a v3 is the entries: not capped.
        val many = "[" + (0 until COPY_LIST_MAX + 1).joinToString(",") { "{\"id\":\"e$it\",\"type\":\"NOTE\",\"title\":\"t\",\"createdAt\":1,\"updatedAt\":1}" } + "]"
        checkCopyShape(many.toByteArray(), 3)
    }

    /** A vault of its own (not a copy) with 20 000 tags in an entry still opens, with the master and with the key. */
    @Test fun aVaultOfItsOwnWithManyTagsStillOpens() = runBlocking {
        val dir = tmp.newFolder("propia")
        val s = VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF }).apply { load(); create(RT20_MASTER.toCharArray()) }
        val key = s.dataKeyCopy()!!
        val file = VaultCrypto.decodeFile(File(dir, "vault.bin").readText())
        val many = VaultEntry("m", EntryType.NOTE, "Muchas", createdAt = 1, updatedAt = 1, tags = (0 until 20_000).map { "t$it" })
        File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, key, VaultCrypto.read(file, key).copy(entries = listOf(many)))))
        val again = VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF }).apply { load() }
        again.unlock(RT20_MASTER.toCharArray())
        assertEquals(20_000, (again.state.value as VaultState.Unlocked).entries.single().tags.size)
        again.lock()
        again.unlockWithKey(key.copyOf())
        assertEquals(20_000, (again.state.value as VaultState.Unlocked).entries.single().tags.size)
    }
}
