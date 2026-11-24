package app.seguridad.redteam

import app.seguridad.AppContainer
import app.seguridad.VaultsRefused
import app.seguridad.core.appJson
import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfLock
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.randomBytes
import app.seguridad.health.LeakResults
import app.seguridad.health.healthView
import app.seguridad.health.uniquePasswords
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.planMerge
import app.seguridad.vault.COPY_DAMAGED
import app.seguridad.vault.EntryFilter
import app.seguridad.vault.EntryType
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.SavedView
import app.seguridad.vault.SearchIndex
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultData
import app.seguridad.vault.checkCopyData
import app.seguridad.vault.isVaultTag
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultFile
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.checkBackup
import app.seguridad.vault.copyTreeEstimate
import app.seguridad.vault.dataAad
import app.seguridad.vault.jsonBytes
import app.seguridad.vault.writeBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.management.ManagementFactory
import java.security.SecureRandom
import java.util.Base64
import java.util.Random
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/*
 * Re-check 2 of the final attack of Clavero 2.0 (decision (f)): the fixes of round 2 (RT-25 a and b, RT-26, informatives
 * 1 to 3, points 8 to 10, M-1 to M-8) and the director's final list of attacks (advisor-t12-redteam-fixes-2-cierre-ok.md),
 * judged by the criterion of advisor-t12-redteam-fixes-2-criterio.md. Same naming as the attack: rtN_… asserts the SAFE
 * behaviour and fails while RT-N is open (@Ignore'd only for the gate); resists… passes; informative… fixes what is
 * accepted as it is today. Measures, never a real OutOfMemoryError: the live heap after GC, the bytes a thread allocated
 * and times compared with a pair of the same size (never seconds on their own). Invented data only, and the real files
 * of the tests' resources. Report: docs/superpowers/handoff/sdd-2.0/redteam-recheck-2.md.
 */
class RedTeam20Recheck2Test {
    @get:Rule val tmp = TemporaryFolder()

    companion object {
        private const val V143 = "maestra-de-prueba-143"
        private val root: File = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/AndroidManifest.xml").isFile }
        private val main = File(root, "app/src/main/java/app/seguridad")
        private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

        /** The real file of 1.4.3 (version 3) and its open data key: the header every copy of 1.x here is sealed under. */
        private val base: Pair<VaultFile, ByteArray> by lazy {
            val file = VaultCrypto.decodeFile(RedTeam20Recheck2Test::class.java.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
            file to VaultCrypto.unlock(file, V143.toCharArray())
        }

        private fun used(): Long {
            repeat(4) {
                System.gc()
                Thread.yield()
            }
            return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        }

        private fun allocated(): Long = threads.getThreadAllocatedBytes(Thread.currentThread().id)

        private fun <T> nanos(block: () -> T): Pair<T, Long> {
            val t0 = System.nanoTime()
            val r = block()
            return r to System.nanoTime() - t0
        }

        /** The median of [n] times of [block], after one run to warm up. */
        private fun median(n: Int = 3, block: () -> Unit): Long {
            block()
            return List(n) { nanos(block).second }.sorted()[n / 2]
        }
    }

    /** [json] (the entries of a copy of 1.x, a JSON list) sealed as the data of the real file of 1.4.3, with its key. */
    private fun v3(json: ByteArray): VaultFile {
        val (file, dek) = base
        val version = VaultCrypto.resolve(file, dek).version
        val nonce = randomBytes(NONCE_BYTES)
        return file.copy(data = Base64.getEncoder().encodeToString(nonce + aead(Aead.AES_256_GCM, true, dek, nonce, dataAad(file, version), json)))
    }

    private fun sgbak(file: VaultFile, marker3: Boolean = false): ByteArray =
        ByteArrayOutputStream().also { writeBackup(it, VaultCrypto.encodeFile(file), emptyMap(), vaultVersion = if (marker3) VaultFile.VERSION else null) }.toByteArray()

    private fun store(compute: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined) =
        VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, compute, kdf = { RT20_KDF }).apply { runBlocking { load() } }

    /** [entries] logins of 1.x, each with notes of [notes] bytes and [oldPasswords] old passwords: a JSON list. */
    private fun oneXLogins(entries: Int, notes: Int, oldPasswords: Int, password: (Int, Int) -> String = { e, k -> "a$e-$k" }): ByteArray = buildString {
        append('[')
        repeat(entries) { e ->
            if (e > 0) append(',')
            append("{\"id\":\"e$e\",\"type\":\"LOGIN\",\"title\":\"t$e\",\"password\":\"p\",\"notes\":\"")
            repeat(notes) { append('n') }
            append("\",\"createdAt\":1,\"updatedAt\":1")
            if (oldPasswords > 0) {
                append(",\"passwordHistory\":[")
                repeat(oldPasswords) { k ->
                    if (k > 0) append(',')
                    append("{\"password\":\"").append(password(e, k)).append("\",\"changedAt\":").append(k).append('}')
                }
                append(']')
            }
            append('}')
        }
        append(']')
    }.toByteArray()

    // ---------------------------------------------------------------------------------------------------------------
    // Attack 1 and row 63: «Importar como bóveda nueva» against «Comprobar una copia».
    // ---------------------------------------------------------------------------------------------------------------

    private val limit = VaultLimits.VAULT_MAX_DATA_BYTES + VaultLimits.COPY_DATA_MARGIN_BYTES.toLong()

    /**
     * A v4 copy of one note whose data are exactly [size] bytes (the date of a check counted), with the tag [tag] and the
     * date of its last check [checkedAt] (0: none, not written).
     */
    private fun copyOfSize(size: Long, tag: String, checkedAt: Long = 0L): ByteArray {
        val rnd = Random(20261119L)
        val one = VaultData(entries = listOf(VaultEntry(UUID(rnd.nextLong(), rnd.nextLong()).toString(), EntryType.NOTE, "Nota", notes = "n", createdAt = 1, updatedAt = 1)), vaultTag = tag, lastCheckedBackupAt = checkedAt)
        val pad = (size - jsonBytes(one)).toInt() + 1
        val data = one.copy(entries = listOf(one.entries[0].copy(notes = "n".repeat(pad))))
        check(jsonBytes(data) == size)
        return rt20Copy(rt20HostileFile(appJson.encodeToString(VaultData.serializer(), data)).first)
    }

    private fun checked(copy: ByteArray, master: String = RT20_MASTER): String = try {
        val report = runBlocking { checkBackup(ByteArrayInputStream(copy), master.toCharArray(), tmp.newFolder(), Dispatchers.Unconfined, Dispatchers.Unconfined) }
        if (report.problems.isEmpty()) "OK" else "problems: ${report.problems}"
    } catch (e: CryptoFailure) {
        e.message ?: "?"
    }

    private fun importedAsNew(copy: ByteArray, master: String = RT20_MASTER): String = try {
        val s = store()
        // What AppContainer.importAsNewVault runs on the new vault's store first (its `before`).
        runBlocking { s.importReplace(ByteArrayInputStream(copy), master.toCharArray(), newTag = true) }
        "OK"
    } catch (e: CryptoFailure) {
        e.message ?: "?"
    }

    /** «Importar copia» (no new tag): "OK" or the message it refuses with. */
    private fun imported(copy: ByteArray, master: String = RT20_MASTER): String = try {
        runBlocking { store().importReplace(ByteArrayInputStream(copy), master.toCharArray()) }
        "OK"
    } catch (e: CryptoFailure) {
        e.message ?: "?"
    }

    /** The three gates of a copy, in one line: «Comprobar», «Importar copia» and «Importar como bóveda nueva». */
    private fun gates(copy: ByteArray) = listOf(checked(copy), imported(copy), importedAsNew(copy))

    /*
     * Row 63, as round 3 of the fixes closed it (the director's ruling, advisor-t12-redteam-fixes-3.md, point 2): a tag
     * the app never writes is refused by the three gates (isVaultTag), and «Importar como bóveda nueva» checks the data as
     * read, before it gives the vault a tag of its own and drops the date of a check: the three say the same. Changed with
     * the director's authorisation (the four informative63_ of re-check 2 fixed what was accepted then).
     */

    @Test fun informative63_aShortTagIsRefusedByTheThreeGates() {
        assertEquals(List(3) { COPY_DAMAGED }, gates(copyOfSize(limit, "a")))
    }

    @Test fun informative63_aLongTagIsRefusedByTheThreeGates() {
        assertEquals(List(3) { COPY_DAMAGED }, gates(copyOfSize(limit, "b".repeat(64))))
    }

    /** A tag as the app writes it and a date of a check, 20 B over the limit counting the date: refused by the three. */
    @Test fun informative63_aDateOfACheckOverTheLimitIsRefusedByTheThreeGates() {
        assertEquals(List(3) { COPY_DAMAGED }, gates(copyOfSize(limit + 20, "0123456789abcdef0123456789abcdef", checkedAt = 1_800_000_000_000L)))
    }

    /**
     * The same exactly at the limit: the three pass; the new vault has a tag of its own and no date of a check, and what
     * it sealed is no bigger than what was checked and passes the same checks.
     */
    @Test fun informative63_aDateOfACheckAtTheLimitPassesTheThreeGates() {
        val tag = "0123456789abcdef0123456789abcdef"
        val copy = copyOfSize(limit, tag, checkedAt = 1_800_000_000_000L)
        assertEquals(listOf("OK", "OK", "OK"), gates(copy))
        val s = store()
        runBlocking { s.importReplace(ByteArrayInputStream(copy), RT20_MASTER.toCharArray(), newTag = true) }
        val sealed = (s.state.value as VaultState.Unlocked).data
        assertTrue("a tag of its own", sealed.vaultTag != tag && isVaultTag(sealed.vaultTag))
        assertEquals("no date of a check", 0L, sealed.lastCheckedBackupAt)
        assertTrue("sealed ${jsonBytes(sealed)} <= checked $limit", jsonBytes(sealed) <= limit)
        checkCopyData(sealed)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Attack 2: the cost of the upgrade of a copy of 1.x, now also in «Comprobar».
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-29 [Baja]. A copy of 1.x whose logins have notes over 64 KiB and 20 old passwords each. The upgrade makes one
     * version per old password with the whole entry (notes included), and `fittedHistory` measured the history again
     * after each version it dropped: 20 + 19 + … + 1 = 210 encodings of a version (its notes) per entry, in «Importar
     * copia», «Importar como bóveda nueva» and «Comprobar una copia» (`VaultCrypto.readAsImported`).
     * Fixed in round 3 (fittedHistory measures each version once). The safe behaviour asserted, as a count and not in
     * milliseconds (the controller's decision on this test, «consulta de rt29_»: times varied with the load of the suite,
     * ×1.9 to ×6.0): reading it as it is imported encodes at most one version per version of each entry, here exactly
     * 20 per entry (the versions + 2 the decision allowed is looser than the code: `fittedHistory` encodes each version
     * kept after the cut to 20 once, and nothing else; `historyEncodings`). The times stay as an informative row.
     */
    @Test fun rt29_theUpgradeOfACopyOf1xCostsAboutWhatReadingItDoes() {
        val (_, dek) = base
        val notes = 256 * 1024
        val hostile = v3(oneXLogins(entries = 8, notes = notes, oldPasswords = 20))
        val pair = v3(oneXLogins(entries = 8, notes = notes, oldPasswords = 0))
        app.seguridad.vault.historyEncodings.set(0)
        val imported = VaultCrypto.readAsImported(hostile, dek, copy = true).imported
        val encodings = app.seguridad.vault.historyEncodings.get()
        assertTrue("every version made by the upgrade dropped by the fit", imported.entries.all { it.history.isEmpty() })
        // Review fixes, I-2: the count is exact, min(k, 20) per entry.
        assertEquals("encodings of a version for 8 entries of 20 versions (20 per entry)", 8L * 20, encodings)
        val tPair = median { VaultCrypto.readAsImported(pair, dek, copy = true) }
        val tHostile = median { VaultCrypto.readAsImported(hostile, dek, copy = true) }
        println("RT-29: 8 logins of $notes B of notes: $encodings encodings of a version (${encodings / 8} per entry); pair ${tPair / 1_000_000.0} ms, with 20 old passwords ${tHostile / 1_000_000.0} ms (x${"%.1f".format(tHostile.toDouble() / tPair)}, informative)")
    }

    /**
     * RT-29, rows (no assertion on times): what it scales to, a check cancelled while the upgrade runs (it
     * runs it to its end), and a derivation asked for while «Importar copia» upgrades (it waits: the upgrade runs inside
     * `KdfLock.derive`, NonCancellable, and the store's mutex).
     */
    @Test fun rt29Rows_theUpgradeIsNotCancelledAndHoldsTheLockOfTheDerivations() {
        val (_, dek) = base
        val notes = 256 * 1024
        val t1 = median { VaultCrypto.readAsImported(v3(oneXLogins(1, notes, 20)), dek, copy = true) }
        val t2 = median { VaultCrypto.readAsImported(v3(oneXLogins(2, notes, 20)), dek, copy = true) }
        println("RT-29 rows: 1 entry ${t1 / 1_000_000.0} ms, 2 entries ${t2 / 1_000_000.0} ms (x${"%.2f".format(t2.toDouble() / t1)}: linear in the notes)")
        val copy = sgbak(v3(oneXLogins(2, notes, 20)))
        val tPair = median { VaultCrypto.readAsImported(v3(oneXLogins(2, notes, 0)), dek, copy = true) }
        val decoding = CountDownLatch(1)
        val saved = VaultCrypto.beforeDecode
        try {
            VaultCrypto.beforeDecode = { decoding.countDown() }
            // «Comprobar una copia» cancelled as soon as the data are decoded: the upgrade still runs to its end.
            runBlocking {
                val job = launch(Dispatchers.Default) {
                    try {
                        checkBackup(ByteArrayInputStream(copy), V143.toCharArray(), tmp.newFolder(), Dispatchers.IO, Dispatchers.Default)
                    } catch (e: Exception) {
                        // Cancelled.
                    }
                }
                assertTrue(decoding.await(20, TimeUnit.SECONDS))
                val (_, afterCancel) = nanos { runBlocking { job.cancelAndJoin() } }
                println("RT-29 rows: «Comprobar» cancelled at the decoding ends ${afterCancel / 1_000_000.0} ms later (the pair's whole read: ${tPair / 1_000_000.0} ms)")
            }
            // «Importar copia»: a derivation asked for during the upgrade waits for it.
            val decoding2 = CountDownLatch(1)
            VaultCrypto.beforeDecode = { decoding2.countDown() }
            val s = store(Dispatchers.Default)
            val importer = Thread { runBlocking { s.importReplace(ByteArrayInputStream(copy), V143.toCharArray()) } }.apply { start() }
            assertTrue(decoding2.await(20, TimeUnit.SECONDS))
            val (_, waited) = nanos { runBlocking { KdfLock.derive(Dispatchers.Default) { } } }
            importer.join(30_000)
            println("RT-29 rows: a derivation asked for while «Importar copia» upgrades waits ${waited / 1_000_000.0} ms")
            assertTrue(s.state.value is VaultState.Unlocked)
        } finally {
            VaultCrypto.beforeDecode = saved
        }
    }

    /**
     * Attack 2, memory: the versions the upgrade makes (`upgraded`, Vault.kt:341-350; one VaultEntry and one
     * EntryVersion per old password) are not in the estimate of the copy's tree (VaultLimits.kt:313-318). Measured with
     * two sizes, the tree as read plus what the upgrade adds, held at once (as `fitted` holds them), against the estimate
     * that let the copy in. Resists: the estimate charges 160 B for every object, and a PasswordChange is far less.
     */
    @Test fun resistsTheUpgradeOfManyOldPasswordsStaysUnderTheEstimate() {
        val (_, dek) = base
        val upgraded = Class.forName("app.seguridad.vault.VaultKt").getDeclaredMethod("upgraded", VaultData::class.java, SecureRandom::class.java)
            .apply { isAccessible = true }
        for ((entries, one) in listOf(20 to false, 40 to false, 20 to true, 40 to true)) {
            val json = oneXLogins(entries, notes = 0, oldPasswords = 5_000) { _, k -> if (one) ('a' + k % 26).toString() else "" }
            val estimate = copyTreeEstimate(json, 3)
            val file = v3(json)
            val u0 = used()
            val read = VaultCrypto.read(file, dek, copy = true)
            val u1 = used()
            val up = upgraded.invoke(null, read, SecureRandom()) as VaultData
            val u2 = used()
            check(up.entries.sumOf { it.history.size } == entries * 5_000 && read.entries.size == entries)
            val tree = u1 - u0
            val extra = u2 - u1
            println(
                "Upgrade memory, ${entries * 5_000} old passwords of ${if (one) 1 else 0} character (${json.size} B of JSON): estimate $estimate, tree $tree, upgrade $extra, " +
                    "both ${tree + extra} (${"%.2f".format((tree + extra).toDouble() / estimate)} of the estimate; ${"%.0f".format(extra.toDouble() / (entries * 5_000))} B per old password)",
            )
            assertTrue("read + upgrade ${tree + extra} under the estimate $estimate", tree + extra <= estimate)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Attack 3: the source test of the guard of the empty slot.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Every public way of VaultStore and AppContainer from an empty slot to a vault (VaultStore.create, both
     * importReplace; AppContainer.createVault and importAsNewVault, the only callers outside ui/, AppContainer.kt:601 and
     * :617) asks the rule of the guard: with vault A with its file and locked and vault B emptied and shown, the guard
     * says no and both wrappers refuse (newVault, canAddVault), leaving the registry as it was.
     */
    @Test fun resistsNoWrapperOutsideTheScreensFillsAnEmptySlotWithoutTheRule() = runTest {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-de-prueba-a-rt2".toCharArray(), name = "Uno")
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        val b = c.createVault("Dos", "maestra-de-prueba-b-rt2".toCharArray())
        c.vault.lock()
        c.vault.destroy()
        assertEquals(b, c.activeVaultId)
        assertEquals(VaultState.NoVault, c.vault.state.value)
        assertTrue(File(c.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").exists())
        val before = c.registry.vaults.value
        assertFalse(c.mayFillEmptySlot())
        for ((name, attempt) in listOf<Pair<String, suspend () -> Unit>>(
            "createVault" to { c.createVault("Tres", "maestra-de-prueba-c-rt2".toCharArray()) },
            "importAsNewVault" to { c.importAsNewVault(ByteArrayInputStream(copy), "maestra-de-prueba-a-rt2".toCharArray(), "Cuatro") },
        )) {
            try {
                attempt()
                fail("$name made a vault")
            } catch (e: VaultsRefused) {
                // As it must.
            }
        }
        assertEquals(before, c.registry.vaults.value)
        assertEquals(VaultState.NoVault, c.vault.state.value)
    }

    /**
     * Attack 3, informative: the source test is textual. Its own check, applied by reflection (its `blank` and its
     * expressions `call`, `refusing` and `topFun`) to a function where the refusing `!guard()` is in one lambda and the
     * call in another (or after an `if` that does not return), accepts the call: «before it in the same function» does
     * not mean «the call only runs if the guard said yes». No screen of today is written like that (the two calls, in
     * VaultScreen.kt and VaultBackup.kt, return right after the guard; and the behaviour test above).
     */
    @Test fun informativeTheSourceTestReadsTheGuardBeforeTheCallNotAroundIt() {
        val t = Class.forName("app.seguridad.EmptySlotGuardSourceTest").getDeclaredConstructor().newInstance()
        fun field(name: String) = t.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(t) as Regex
        val blank = t.javaClass.getDeclaredMethod("blank", String::class.java).apply { isAccessible = true }
        val call = field("call")
        val refusing = field("refusing")
        val topFun = field("topFun")
        fun accepted(src: String): Boolean {
            val code = blank.invoke(t, src) as String
            return call.findAll(code).all { m ->
                val start = topFun.findAll(code).map { it.range.first }.lastOrNull { it < m.range.first } ?: return@all false
                (refusing.findAll(code).map { it.range.first }.lastOrNull { it < m.range.first } ?: -1) > start
            }
        }
        val otherLambda = """
            |@Composable
            |fun CreateVault(store: VaultStore) {
            |    val guard = LocalEmptySlotGuard.current
            |    val a = { scope.launch { if (!guard()) return@launch } }
            |    val b = { scope.launch { store.create(pw) } }
            |}
        """.trimMargin()
        val noReturn = """
            |fun CreateVault(store: VaultStore) {
            |    val guard = LocalEmptySlotGuard.current
            |    scope.launch { if (!guard()) error = "no"; store.create(pw) }
            |}
        """.trimMargin()
        assertTrue("guard in another lambda", accepted(otherLambda))
        assertTrue("guard that does not return", accepted(noReturn))
        assertFalse("no guard at all", accepted("fun CreateVault(store: VaultStore) {\n    scope.launch { store.create(pw) }\n}"))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Attack 4: the length of the tags of a copy (screens cut them to 24, a copy has no such bound).
    // ---------------------------------------------------------------------------------------------------------------

    private fun longTextEntries(kind: String, bytes: Int): VaultData {
        // One-letter words: the most words the index can make of a byte (FuzzySearch.words, an IntArray per word).
        val text = buildString { while (length < bytes) append("a ") }.substring(0, bytes)
        val e = VaultEntry(
            "e", EntryType.LOGIN, if (kind == "title") text else "t", createdAt = 1, updatedAt = 1,
            tags = if (kind == "tag") listOf(text) else emptyList(),
        )
        return VaultData(entries = listOf(e), vaultTag = "a".repeat(32))
    }

    /**
     * A tag (or a title) of one-letter words, with no bound but the size of the copy. What the list builds of it
     * (SearchIndex.build: its normalized text and an IntArray per word; the chips) grows linearly with its length, and its
     * density is in the order of the legitimate shapes (RedTeam20ListCostTest: 17.64 B/B for the dearest one written by
     * the app): the criterion of RT-25 leaves the density to row 62. No superlinear cost (compareKey, the collation key of a
     * title, the words), so it resists.
     */
    @Test fun resistsALongTagOrTitleCostsTheListLinearly() {
        for (kind in listOf("tag", "title")) {
            val cost = LongArray(2)
            val time = LongArray(2)
            for ((i, bytes) in listOf(256 * 1024, 512 * 1024).withIndex()) {
                val data = longTextEntries(kind, bytes)
                val json = appJson.encodeToString(VaultData.serializer(), data).length
                val u0 = used()
                val index = SearchIndex.build(data.entries, data.folders)
                val chips = data.entries.flatMapTo(HashSet()) { it.tags }.sorted()
                val u1 = used()
                check(index.hashCode() != 0 || chips.isNotEmpty())
                cost[i] = u1 - u0
                time[i] = median { SearchIndex.build(data.entries, data.folders) }
                println("A long $kind of $bytes B ($json B of JSON): the list keeps ${cost[i]} B (${"%.2f".format(cost[i].toDouble() / json)} B/B), built in ${time[i] / 1_000_000.0} ms")
            }
            assertTrue("$kind: memory linear (x${cost[1].toDouble() / cost[0]})", cost[1] <= cost[0] * 2.5)
            assertTrue("$kind: time linear (x${time[1].toDouble() / time[0]})", time[1] <= time[0] * 4)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Attack 5: «Salud» after the fix.
    // ---------------------------------------------------------------------------------------------------------------

    private fun bigVault(n: Int): VaultData {
        val rnd = Random(20261119L)
        return VaultData(
            entries = List(n) { i ->
                VaultEntry(UUID(rnd.nextLong(), rnd.nextLong()).toString(), EntryType.LOGIN, "Servicio $i", username = "usuario$i@example.com",
                    password = if (i % 3 == 0) "hola$i" else "x9!Qz-${rnd.nextLong()}", url = "https://s$i.example.com/", createdAt = 1, updatedAt = 1, tags = listOf("t${i % 50}"))
            },
            vaultTag = "a".repeat(32),
        )
    }

    /**
     * What «Salud» works out on the main thread, outside the catch of point 9 (HealthScreen.kt: `entries.associateBy`,
     * the rows `sortedByDescending`, the counts per issue and, since 2.1.1, `leaks.breached(entries, check)` on every
     * composition; and, on a tap, `LeakResults.start`: `uniquePasswords` and the marks of the logins checked), is small
     * beside what the catch covers (healthView): measured as bytes allocated, with every login leaked (branch review of
     * 2.1.1, BR-7 a), it resists. The texts are anchored.
     */
    @Test fun resistsWhatSaludMakesOnTheMainThreadIsSmallBesideWhatItCatches() {
        val src = File(main, "ui/screens/HealthScreen.kt").readText()
        for (anchor in listOf("val byId = entries.associateBy { it.id }", "items(items.orEmpty().sortedByDescending { it.issues.size }", "leaks.start(", "val breached = leaks.breached(entries, check)")) {
            assertTrue(anchor, anchor in src)
        }
        // 2.1.1, task 5: the tap's grouping moved to the session object the screen calls (LeakResults.start).
        assertTrue("val groups = uniquePasswords(entries)" in File(main, "health/LeakResults.kt").readText())
        val data = bigVault(20_000)
        val entries = data.entries
        // A finished check that found every login leaked, run at once (Unconfined, no network).
        val done = LeakResults(CoroutineScope(Dispatchers.Unconfined), lockEpoch = { 0 })
        done.start(entries, { 3 }, isOpen = { true }, awaitClosed = { kotlinx.coroutines.awaitCancellation() })
        val check = done.current()
        check(check.end == LeakResults.End.DONE && check.found.size == entries.size)
        val leaked = done.breached(entries, check)
        val a0 = allocated()
        val view = healthView(data, 1_800_000_000_000L, leaked, missionsOff = false)
        val a1 = allocated()
        val byId = entries.associateBy { it.id }
        val rows = view.items.sortedByDescending { it.issues.size }
        val counts = app.seguridad.health.Issue.entries.map { issue -> view.items.count { issue in it.issues } }
        val breached = done.breached(entries, check)
        val a2 = allocated()
        // The tap: LeakResults.start up to its launch (a scope already ended runs nothing, so no request).
        val tap = LeakResults(CoroutineScope(Job().apply { cancel() }), lockEpoch = { 0 })
        tap.start(entries, { 0 }, isOpen = { true }, awaitClosed = {})
        val a3 = allocated()
        check(!view.noMemory && byId.size == entries.size && rows.isNotEmpty() && counts.isNotEmpty() && breached.size == entries.size && tap.current().running)
        println("«Salud», 20 000 logins, all leaked: healthView allocates ${a1 - a0} B; the main thread ${a2 - a1} B per composition and ${a3 - a2} B on a tap of the check")
        assertTrue(a2 - a1 <= (a1 - a0) / 2)
        assertTrue(a3 - a2 <= (a1 - a0) / 2)
    }

    /**
     * Attack 5, informative, inverted in round 3 (the director's ruling, authorised): with NO_MEMORY, «Salud» still returns
     * before its leak check (HealthScreen.kt, `return@BackScaffold` before the progress and «Cancelar»), but first stops
     * the check that runs (`LaunchedEffect(Unit) { leaks.cancel() }`): no request goes after the one in flight. 2.1.1,
     * task 5 (owner, 24-11-2026): leaving the screen no longer stops it (it goes on while a login is edited, in the
     * session object [app.seguridad.health.LeakResults]); a lock does.
     */
    @Test fun informativeSaludWithoutMemoryHidesTheCancelOfARunningCheck() {
        val src = File(main, "ui/screens/HealthScreen.kt").readText()
        val noMemory = src.indexOf("if (health?.noMemory == true)")
        val back = src.indexOf("return@BackScaffold", noMemory)
        val cancel = src.indexOf("OutlinedButton(onClick = { leaks.cancel() }")
        assertTrue(noMemory in 0 until back && back < cancel)
        assertTrue("produceState keyed by the leaks found", "produceState<HealthView?>(null, data, breached, day, missionsOff)" in src)
        assertFalse("leaving does not stop it (2.1.1, task 5)", "onDispose" in src)
        assertTrue("NO_MEMORY stops the running check", "LaunchedEffect(Unit) { leaks.cancel() }" in src.substring(noMemory, back))
        assertFalse("without dropping its results (which would change the key)", "forget()" in src.substring(noMemory, back))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Attack 6: more work over the whole vault with no catch.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-27 [Baja]. «Importar de otra app» works the whole vault out: `planMerge` (a Matcher of every live entry: keys of
     * titles, users and hosts, and `liveTags`, MergePlan.kt) and `previewEstimate` (the merged vault). Measured: planMerge
     * allocates about what the list's index does for the same vault, while the list keeps its own index. Fixed in round
     * 3 (the director's ruling, advisor-t12-redteam-fixes-3.md; this test rewritten with his authorisation): both run in a
     * wrapper that turns running out of memory into null (imports/ImportMemory.kt), and the session then says
     * IMPORT_NO_MEMORY with «Importar» off. The safe behaviour asserted: with the error made by hand, both wrappers give
     * null and the session is in IMPORT_NO_MEMORY with «Importar» off; and ImportScreen.kt calls only the wrappers.
     */
    @Test fun rt27_theImportScreensWholeVaultWorkCatchesRunningOutOfMemory() {
        val data = bigVault(20_000)
        val one = ImportRead(ImportFormat.PASSWORDS_CSV, listOf(ImportedItem(0, VaultEntry("i", EntryType.LOGIN, "Nuevo", createdAt = 1, updatedAt = 1), emptyList())), emptyList(), 0)
        val a0 = allocated()
        val plan = planMerge(one, data.entries, 0)
        val a1 = allocated()
        val index = SearchIndex.build(data.entries, data.folders)
        val a2 = allocated()
        check(plan.newItems.size == 1 && index.hashCode() != 0)
        println("RT-27: 20 000 logins: planMerge allocates ${a1 - a0} B, the list's index ${a2 - a1} B (x${"%.2f".format((a1 - a0).toDouble() / (a2 - a1))})")
        // With the error made by hand: both wrappers give null, and the session says so with «Importar» off.
        val session = app.seguridad.imports.ImportSession()
        try {
            app.seguridad.imports.beforeImportWork = { throw OutOfMemoryError() }
            assertEquals(null, app.seguridad.imports.planMergeOrNoMemory(one, data.entries, 0))
            assertEquals(null, app.seguridad.imports.previewEstimateOrNoMemory(plan, app.seguridad.imports.DuplicateChoice.SKIP, emptyMap(), data.entries, app.seguridad.vault.VaultData(), 1L))
            val run = session.begin(Any())
            session.hold(run, byteArrayOf(1))
            session.onRead(run, app.seguridad.imports.ReadResult.Ok(one))
            session.planNoMemory(run)
        } finally {
            app.seguridad.imports.beforeImportWork = {}
        }
        assertEquals(app.seguridad.imports.IMPORT_NO_MEMORY, session.error)
        assertFalse(session.canConfirm)
        // At the preview, an estimate out of memory keeps «Importar» off.
        val preview = app.seguridad.imports.ImportSession()
        val run = preview.begin(Any())
        preview.hold(run, byteArrayOf(1))
        preview.onRead(run, app.seguridad.imports.ReadResult.Ok(one))
        preview.onPlan(run, plan, data.entries)
        preview.estimateNoMemory(plan, preview.default, preview.overrides)
        assertTrue(preview.estimateNoMemory)
        assertFalse(preview.canConfirm)
        // The anchor: ImportScreen.kt, out of its comments, calls no bare planMerge( nor previewEstimate(, and each wrapper once.
        val src = File(main, "ui/screens/ImportScreen.kt").readLines().filterNot { val t = it.trimStart(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
            .joinToString("\n") { it.substringBefore("//") }
        assertEquals("no bare planMerge(", 0, Regex("""\bplanMerge\(""").findAll(src).count())
        assertEquals("no bare previewEstimate(", 0, Regex("""\bpreviewEstimate\(""").findAll(src).count())
        assertEquals(1, Regex("""\bplanMergeOrNoMemory\(""").findAll(src).count())
        assertEquals(1, Regex("""\bpreviewEstimateOrNoMemory\(""").findAll(src).count())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Found on the way: a saved view of a copy with thousands of tags, on the main thread.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-28 [Baja]. A saved view's tags come from the copy with no bound but COPY_LIST_MAX (10 000 per list; the excess
     * of RT-25 b counts entries and versions, not views). Applied, its tags are the list's filter, and at every result
     * (each letter typed, each save) `LaunchedEffect(result)` ran on the main thread `f.tags.filter { it in result.tags }`,
     * where `result.tags` is a sorted List of every tag of the vault: F × N comparisons (measured before the fix: 519 ms
     * for a view of 1 000 against 200 000, ×3 394 a set). Fixed in round 3 (rewritten with the director's authorisation):
     * the list works out the tags of the filter that are gone off the main thread, in `compute`, with [listTags] (one set,
     * not kept), and the effect only takes [ListResult.goneFilterTags] away. The safe behaviour asserted: the copy still
     * passes «Comprobar»; listTags with N = 200 000 and the view of 10 000 gives nothing gone and costs at most twice the
     * chips alone; with 3 tags taken out of the vault, exactly those 3 are gone; and VaultList.kt has no `in result.tags`.
     */
    @Test fun rt28_aViewWithManyTagsDoesNotFreezeTheMainThread() {
        val all: List<String> = List(200_000) { "t" + it.toString(36).padStart(4, '0') }.sorted() // 200 000 distinct
        // The copy that carries it passes every check of «Importar copia» and «Comprobar una copia»: 20 000 logins with 10
        // tags of their own (no excess) and a view of the last 10 000 of them (one list of COPY_LIST_MAX).
        val rnd = Random(20261119L)
        val data = VaultData(
            entries = List(20_000) { i -> VaultEntry(UUID(rnd.nextLong(), rnd.nextLong()).toString(), EntryType.LOGIN, "s$i", createdAt = 1, updatedAt = 1, tags = all.subList(10 * i, 10 * i + 10)) },
            views = listOf(SavedView("v", "Vista", EntryFilter(tags = all.takeLast(10_000).toSet()))),
            vaultTag = "a".repeat(32),
        )
        assertEquals("OK", checked(rt20Copy(rt20HostileFile(appJson.encodeToString(VaultData.serializer(), data)).first)))
        val view: Set<String> = data.views.single().filter.tags.mapTo(LinkedHashSet()) { String(it.toCharArray()) } // decoded apart
        val result = app.seguridad.vault.listTags(data.entries, view)
        assertEquals(all, result.sorted)
        assertEquals("nothing of the view is gone", emptySet<String>(), result.goneFromFilter)
        val tChips = median(5) { check(data.entries.flatMapTo(HashSet()) { it.tags }.sorted().size == all.size) }
        val tTags = median(5) { check(app.seguridad.vault.listTags(data.entries, view).sorted.size == all.size) }
        // Review fixes, I-3: the time is an informative row, as for rt29_ (a timing test in the CI can block a PR for nothing);
        // RT-28 stays fixed by the anchor of VaultList.kt, listTags in compute and the differential of 3 000 cases.
        println("RT-28: listTags with 200 000 tags and a view of 10 000: ${tTags / 1_000_000.0} ms off the main thread; the chips alone ${tChips / 1_000_000.0} ms (x${"%.2f".format(tTags.toDouble() / tChips)}, informative)")
        // Three tags of the view taken out of the vault: exactly those three are gone.
        val takenOut = view.take(3).toSet()
        val fewer = data.entries.map { e -> if (e.tags.any { it in takenOut }) e.copy(tags = e.tags - takenOut) else e }
        assertEquals(takenOut, app.seguridad.vault.listTags(fewer, view).goneFromFilter)
        // The anchor: no lookup in the list of tags is left in VaultList.kt, and the effect reads goneFilterTags.
        val src = File(main, "ui/screens/VaultList.kt").readText()
        assertEquals(0, Regex("""\bin result\.tags\b""").findAll(src).count())
        assertTrue(Regex("""LaunchedEffect\(result\) \{[^}]*result\.goneFilterTags""").containsMatchIn(src))
        assertEquals(2, Regex("""result\.goneFilterTags""").findAll(src).count())
        assertTrue("computed with listTags in compute", "val tagsNow = listTags(listable, f.tags)" in src)
    }
}
