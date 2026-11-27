package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.currentAutoBackupCopies
import app.seguridad.settings.currentImportCopies
import app.seguridad.settings.oneTarget
import app.seguridad.settings.principal
import app.seguridad.settings.updatePrincipal
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.settings.withImportCopy
import app.seguridad.settings.autoBackupReplacePending
import app.seguridad.settings.autoBackupReplaceTargets
import app.seguridad.settings.autoBackupReplaceTold
import app.seguridad.settings.autoBackupUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneOffset

/** A folder in memory whose writes can wait on [gate] (a slow upload) or fail ([failWrite]). */
private class SlowFolder : BackupTarget {
    val files = LinkedHashMap<String, ByteArray>()
    private val ids = HashMap<String, String>()
    private var next = 0
    val created = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    var failWrite: Exception? = null

    private fun idOf(name: String) = ids.getOrPut(name) { "doc-${next++}" }

    override suspend fun create(name: String): BackupFile {
        created += name
        files[name] = ByteArray(0)
        ids[name] = "doc-${next++}"
        return object : BackupFile {
            var current = name
            override val id: String get() = idOf(current)
            override suspend fun write(writer: suspend (OutputStream) -> Unit) {
                failWrite?.let { throw it }
                writer(object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        files[current] = files.getValue(current) + b.copyOfRange(off, off + len)
                    }
                })
                gate?.let { g -> gate = null; g.await() }
            }
            override fun rename(to: String) {
                files[to] = files.remove(current)!!
                ids.remove(current)
                ids[to] = "doc-${next++}"
                current = to
            }
            override fun delete() {
                files.remove(current)
                ids.remove(current)
            }
        }
    }

    override fun list(prefix: String) = files.keys.filter { it.startsWith(prefix) }.map { BackupEntry(idOf(it), it) }

    override fun delete(id: String) {
        val name = ids.entries.firstOrNull { it.value == id }?.key ?: return
        files.remove(name)
        ids.remove(name)
    }

    fun put(name: String): String {
        files[name] = byteArrayOf(1)
        return idOf(name)
    }

    fun idOfName(name: String) = idOf(name)
}

/**
 * Review fixes, CR-2: opening a vault of 1.4.3 (v3) with a folder makes the copy before the upgrade to v4 first; the
 * automatic copies that are due (A6, a copy «con cada cambio» missed, [AutoBackup.launchDue]) wait for it
 * ([VaultStore.upgradeCopying]) and run after the upgrade is saved; meanwhile a save says [UPGRADE_COPYING], and
 * [UPGRADE_PENDING] once the dialog asks. The wait always ends: a lock, a copy that fails, a save that fails, a scope
 * cancelled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeCopyFirstTest {
    @get:Rule val tmp = TemporaryFolder()

    /** No test may hang the shared lock of Gradle. */
    @get:Rule val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(30)

    private var clock = 1_800_000_000_000L + 3_600_000L
    private val folder = "content://x/tree/primary%3ACopias"
    private lateinit var settings: SettingsStore
    private var auto: AutoBackup? = null
    private var failSave = false
    private val results = mutableListOf<AutoBackupResult>()

    private fun TestScope.setup(
        target: SlowFolder,
        a6: List<String>? = null,
        scope: CoroutineScope = backgroundScope,
        beforeCopy: suspend () -> Unit = {},
    ): VaultStore {
        File(tmp.root, "vault.bin").writeBytes(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() })
        settings = SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined)
        kotlinx.coroutines.runBlocking {
            settings.load()
            settings.updatePrincipal {
                it.oneTarget(
                    autoBackupUri = folder, autoBackupCopies = a6.orEmpty(), autoBackupCopiesFolder = a6?.let { folder },
                    autoBackupReplacePending = a6 != null, autoBackupReplaceTargets = a6,
                )
            }
        }
        val s = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            beforeUpgrade = { beforeCopy(); auto!!.copyBeforeUpgrade() },
            writeFile = { f, t -> if (failSave && t.contains("\"version\":4")) throw IOException("disco lleno") else f.writeText(t) },
        )
        auto = oneTargetBackup(
            s, folder = { settings.settings.value.principal.autoBackupUri }, open = { target }, scope, now = { clock },
            keep = { 5 }, lastCopyAt = { Long.MAX_VALUE }, copies = { settings.settings.value.principal.currentAutoBackupCopies },
            replacePending = { settings.settings.value.principal.autoBackupReplacePending },
            replaceTargets = { settings.settings.value.principal.autoBackupReplaceTargets },
            replaceTold = { settings.settings.value.principal.autoBackupReplaceTold },
            onResult = { r -> results += r; settings.updatePrincipal { it.withAutoBackupResult(r, s.generation) } },
            zone = ZoneOffset.UTC,
            importCopies = { settings.settings.value.principal.currentImportCopies },
            onImportCopy = { f, id, generation, oldPassword -> settings.updatePrincipal { it.withImportCopy(f, id, generation, s.generation, oldPassword) } },
        )
        runCurrent()
        kotlinx.coroutines.runBlocking { s.load() }
        return s
    }

    private fun refusal(block: suspend () -> Unit): String? = try {
        kotlinx.coroutines.runBlocking { block() }
        null
    } catch (e: IllegalStateException) {
        e.message
    }

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "n $id", createdAt = 0, updatedAt = 0)

    @Test fun theUpgradeCopyGoesFirstAndTheDueCopiesWaitForIt() = runTest {
        val t = SlowFolder()
        val old = t.put("seguridad-auto-20261001-100000.sgbak")
        val s = setup(t, a6 = listOf(old))
        t.gate = CompletableDeferred()
        val gate = t.gate!!
        val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
        runCurrent()
        // A failed check opens the gate, so the unlock (which does not cancel) ends and the test reports instead of hanging.
        try {
            assertTrue("the copy before the upgrade runs", s.upgradeCopying.value)
            assertTrue("its document is the first: ${t.created}", t.created.first().startsWith("seguridad-antes-de-2-0-"))
            assertEquals("nothing else meanwhile: ${t.created}", 1, t.created.size)
            // Meanwhile: a save says the copy runs; «launchDue» (the app opened again) waits too.
            assertEquals(UPGRADE_COPYING, refusal { s.upsert(note("x")) })
            auto!!.launchDue(clock)
            runCurrent()
            assertEquals("nothing else meanwhile: ${t.created}", 1, t.created.size)
        } finally {
            gate.complete(Unit)
        }
        runCurrent()
        withTimeout(5_000) { unlocking.await() }
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
        assertFalse(s.upgradeCopying.value)
        assertEquals(4, VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText()).version)
        // Then the copy that was due, and A6: the old copy goes, the upgrade's stays, listed among the import copies.
        val upgradeCopy = t.files.keys.single { it.startsWith("seguridad-antes-de-2-0-") }
        assertTrue("an automatic copy after it: ${t.created}", t.created.drop(1).any { it.startsWith("seguridad-auto-") })
        assertFalse("A6 replaced the old copy: ${t.files.keys}", "seguridad-auto-20261001-100000.sgbak" in t.files)
        assertTrue("the upgrade's copy stays", upgradeCopy in t.files)
        assertTrue("listed among the import copies", t.idOfName(upgradeCopy) in settings.settings.value.principal.currentImportCopies)
        // ...and not counted as a copy left unlisted by the replacement.
        val replaced = results.filter { it.replaced }
        assertTrue("A6 ran: $results", replaced.isNotEmpty())
        assertEquals("unlisted left: $replaced", 0, replaced.sumOf { it.unlistedLeft })
    }

    /**
     * A copy asked for meanwhile («Hacer una copia ahora» or a folder just chosen: [AutoBackup.request], the debounced
     * run) waits too, also when its debounce ends before the upgrade's copy reaches the lock.
     */
    @Test fun aCopyAskedForMeanwhileWaitsToo() = runTest {
        val t = SlowFolder()
        val hold = CompletableDeferred<Unit>()
        val s = setup(t, beforeCopy = { hold.await() })
        val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
        runCurrent()
        try {
            assertTrue(s.upgradeCopying.value)
            auto!!.request(folder)
            advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals("nothing before the upgrade's copy: ${t.created}", 0, t.created.size)
        } finally {
            hold.complete(Unit)
        }
        runCurrent()
        withTimeout(5_000) { unlocking.await() }
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
        assertTrue("its document is the first: ${t.created}", t.created.first().startsWith("seguridad-antes-de-2-0-"))
        assertTrue("the copy asked for, after it: ${t.created}", t.created.drop(1).any { it.startsWith("seguridad-auto-") })
        assertEquals(4, VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText()).version)
    }

    @Test fun theDueCopiesWaitEvenWhenTheyCouldTakeTheLockFirst() = runTest {
        // The race of the review: the collector of «Unlocked» and «launchDue» get their turn before the upgrade's copy
        // reaches the lock; they wait instead of copying the version 3 first.
        val t = SlowFolder()
        val old = t.put("seguridad-auto-20261001-100000.sgbak")
        val hold = CompletableDeferred<Unit>()
        val s = setup(t, a6 = listOf(old), beforeCopy = { hold.await() })
        val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
        runCurrent()
        try {
            assertTrue(s.upgradeCopying.value)
            auto!!.launchDue(clock)
            runCurrent()
            assertEquals("nothing before the upgrade's copy: ${t.created}", 0, t.created.size)
        } finally {
            hold.complete(Unit)
        }
        runCurrent()
        withTimeout(5_000) { unlocking.await() }
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
        assertTrue("its document is the first: ${t.created}", t.created.first().startsWith("seguridad-antes-de-2-0-"))
        assertTrue("an automatic copy after it: ${t.created}", t.created.drop(1).any { it.startsWith("seguridad-auto-") })
        assertTrue("the upgrade's copy stays: ${t.files.keys}", t.files.keys.any { it.startsWith("seguridad-antes-de-2-0-") })
    }

    @Test fun whenTheDialogAsksASaveSaysUpgradePending() = runTest {
        val t = SlowFolder().apply { failWrite = IOException("sin permiso") }
        val s = setup(t)
        s.unlock("maestra-de-prueba-143".toCharArray())
        runCurrent()
        assertTrue(s.pendingUpgrade.value)
        assertFalse(s.upgradeCopying.value)
        assertEquals(UPGRADE_PENDING, refusal { s.upsert(note("x")) })
    }

    /** The wait always ends: a lock during the copy. */
    @Test fun theWaitEndsWithALock() = runTest {
        val t = SlowFolder()
        val s = setup(t)
        val gate = CompletableDeferred<Unit>().also { t.gate = it }
        val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
        runCurrent()
        assertTrue(s.upgradeCopying.value)
        s.lock()
        withTimeout(5_000) { s.upgradeCopying.first { !it } }
        gate.complete(Unit)
        runCurrent()
        withTimeout(5_000) { unlocking.await() }
    }

    /** The wait always ends: a copy that fails. */
    @Test fun theWaitEndsWithACopyThatFails() = runTest {
        val t = SlowFolder().apply { failWrite = IOException("sin permiso") }
        val s = setup(t)
        s.unlock("maestra-de-prueba-143".toCharArray())
        runCurrent()
        withTimeout(5_000) { s.upgradeCopying.first { !it } }
        assertTrue(s.pendingUpgrade.value)
    }

    /** The wait always ends: a save of the upgrade that fails. */
    @Test fun theWaitEndsWithASaveThatFails() = runTest {
        failSave = true
        val t = SlowFolder()
        val s = setup(t)
        s.unlock("maestra-de-prueba-143".toCharArray())
        runCurrent()
        withTimeout(5_000) { s.upgradeCopying.first { !it } }
        assertEquals(3, VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText()).version)
    }

    /** The wait always ends: the scope of the copies cancelled (the vault deleted) while a due copy waits. */
    @Test fun theWaitEndsWithTheScopeCancelled() = runTest {
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val t = SlowFolder()
        val s = setup(t, scope = scope)
        val gate = CompletableDeferred<Unit>().also { t.gate = it }
        val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
        runCurrent()
        assertTrue(s.upgradeCopying.value)
        val waiting = scope.launch { auto!!.runDue(clock) }
        runCurrent()
        scope.cancel()
        withTimeout(5_000) { waiting.join() }
        assertTrue(waiting.isCancelled)
        gate.complete(Unit)
        runCurrent()
        withTimeout(5_000) { unlocking.await() }
    }

    /**
     * Audit RC-2: a pass that got past the wait while no upgrade's copy ran, and queued on the lock behind a slow copy,
     * gets the lock after the vault was opened again (still a 3): its copy is deferred until the upgrade's copy, never
     * made before it. Steps: the first upgrade's copy fails and the dialog asks; a copy asked for holds the lock in its
     * write, and an A6 request is saved meanwhile; a lock and [AutoBackup.launchDue], which waits for the lock; a new
     * unlock; the write ends.
     */
    @Test fun aPassThatWaitedForTheLockDefersItsCopyUntilTheUpgradeCopy() = runTest {
        val t = SlowFolder().apply { failWrite = IOException("sin permiso") }
        val s = setup(t)
        s.unlock("maestra-de-prueba-143".toCharArray())
        runCurrent()
        assertTrue("the dialog asks", s.pendingUpgrade.value)
        assertFalse(s.upgradeCopying.value)
        t.failWrite = null
        val gate = CompletableDeferred<Unit>().also { t.gate = it }
        var unlocking: kotlinx.coroutines.Deferred<Unit>? = null
        try {
            auto!!.request(folder)
            advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
            runCurrent()
            assertTrue("the copy asked for holds the lock: ${t.created}", t.created.last().startsWith("seguridad-auto-"))
            val held = t.created.size
            // An A6 request comes in meanwhile (a change of master password in another session of the test's settings):
            // the pass of launchDue has a copy to make.
            val old = t.put("seguridad-auto-20261001-100000.sgbak")
            settings.updatePrincipal {
                it.oneTarget(
                    autoBackupUri = folder, autoBackupCopies = listOf(old), autoBackupCopiesFolder = folder,
                    autoBackupReplacePending = true, autoBackupReplaceTargets = listOf(old),
                )
            }
            s.lock()
            auto!!.launchDue(clock)
            runCurrent()
            unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
            runCurrent()
            assertTrue(s.upgradeCopying.value)
            assertEquals("nothing else while the copy is held: ${t.created}", held, t.created.size)
            gate.complete(Unit)
            runCurrent()
            withTimeout(5_000) { unlocking.await() }
            assertTrue("the next one is the upgrade's: ${t.created}", t.created[held].startsWith("seguridad-antes-de-2-0-"))
        } finally {
            gate.complete(Unit)
        }
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(4, VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText()).version)
        val upgrade = t.created.indexOfFirst { it.startsWith("seguridad-antes-de-2-0-") && t.created.indexOf(it) > 0 }
        assertTrue("an automatic copy after the v4 is saved: ${t.created}", t.created.drop(upgrade + 1).any { it.startsWith("seguridad-auto-") })
    }

    /**
     * Audit E-2: the first opening's copy is slow; a lock lets that opening's due pass queue on the lock; a second opening
     * with the master starts its own upgrade's copy, which goes before that queued pass once the slow copy ends.
     */
    @Test fun aCopyQueuedOnTheLockBeforeAReopenWaitsForTheNewUpgradeCopy() = runTest {
        val t = SlowFolder()
        val old = t.put("seguridad-auto-20261001-100000.sgbak")
        val s = setup(t, a6 = listOf(old))
        val gate = CompletableDeferred<Unit>().also { t.gate = it }
        try {
            val first = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
            runCurrent()
            assertTrue(s.upgradeCopying.value)
            s.lock()
            runCurrent()
            val second = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
            runCurrent()
            assertTrue(s.upgradeCopying.value)
            gate.complete(Unit)
            runCurrent()
            withTimeout(5_000) { first.await() }
            withTimeout(5_000) { second.await() }
            assertTrue("the second document is the new opening's upgrade copy: ${t.created}", t.created[1].startsWith("seguridad-antes-de-2-0-"))
        } finally {
            gate.complete(Unit)
        }
    }

    /** The wait always ends: the job that called unlock is cancelled while the upgrade's copy is held. */
    @Test fun theWaitEndsWhenTheUnlockCallerIsCancelled() = runTest {
        val t = SlowFolder()
        val s = setup(t)
        val gate = CompletableDeferred<Unit>().also { t.gate = it }
        val unlocking = launch { s.unlock("maestra-de-prueba-143".toCharArray()) }
        runCurrent()
        try {
            assertTrue(s.upgradeCopying.value)
            unlocking.cancel()
            runCurrent()
        } finally {
            gate.complete(Unit)
        }
        runCurrent()
        withTimeout(5_000) { unlocking.join() }
        withTimeout(5_000) { s.upgradeCopying.first { !it } }
        assertTrue("the copy ended: ${t.files.keys}", t.files.keys.any { it.startsWith("seguridad-antes-de-2-0-") && !it.endsWith(".tmp") })
    }

    /**
     * Audit RC-3: the wait of CR-2 is in the two passes, each right before its lock: [AutoBackup.runChanged]'s (a defence
     * with one collector, no behaviour test turns red without it) and [AutoBackup.runDue]'s; never in copyBeforeUpgrade,
     * which would wait for itself.
     */
    @Test fun theTwoPassesWaitForTheUpgradeCopyRightBeforeTheLock() {
        val path = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/app/seguridad/vault/AutoBackup.kt") }.first { it.isFile }
        val code = path.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
        val waits = code.indices.filter { code[it] == "store.upgradeCopying.first { !it }" }
        assertEquals("two waits: $waits", 2, waits.size)
        for (w in waits) assertTrue(code[w + 1], code[w + 1].startsWith("lock.withLock {"))
        val owners = waits.map { w -> code.subList(0, w).last { it.contains(" fun ") } }
        assertEquals(listOf("private suspend fun runChanged() {", "suspend fun runDue(now: Long, afterUnlock: Boolean = false) {"), owners)
        val upgrade = code.single { it.startsWith("suspend fun copyBeforeUpgrade()") }
        assertFalse(upgrade, "upgradeCopying" in upgrade)
    }

    /**
     * Audit D-1: a folder that never answers (the upgrade's copy held in its write, with the lock) does not hold the
     * vault: after [UPGRADE_COPY_WAIT_MS] the user is asked, saying the copy has not finished ([VaultStore.upgradeAutoCopyNotDone]),
     * and the upgrade can be saved. The copy goes on in AutoBackup's scope: when it ends it is listed with the import
     * copies, never told as the upgrade's ([VaultStore.upgradeDone] stays null), and A6 does not delete it.
     */
    @Test fun aFolderThatNeverAnswersStillLeavesTheChoice() = runTest {
        val t = SlowFolder()
        val old = t.put("seguridad-auto-20261001-100000.sgbak")
        val s = setup(t, a6 = listOf(old))
        val gate = CompletableDeferred<Unit>().also { t.gate = it }
        try {
            val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
            runCurrent()
            assertTrue(s.upgradeCopying.value)
            advanceTimeBy(UPGRADE_COPY_WAIT_MS - 1)
            runCurrent()
            assertTrue("still waiting just before the time", s.upgradeCopying.value)
            advanceTimeBy(2)
            runCurrent()
            assertFalse(s.upgradeCopying.value)
            assertTrue(s.pendingUpgrade.value)
            assertTrue(s.upgradeAutoCopyNotDone)
            assertTrue(s.upgradeAutoCopyFailed)
            assertEquals(UPGRADE_PENDING, refusal { s.upsert(note("x")) })
            withTimeout(5_000) { unlocking.await() }
            assertTrue(s.completeUpgrade())
            assertEquals(4, VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText()).version)
        } finally {
            gate.complete(Unit)
        }
        runCurrent()
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals("never told as the upgrade's", null, s.upgradeDone.value)
        val late = t.files.keys.single { it.startsWith("seguridad-antes-de-2-0-") }
        assertFalse(late, late.endsWith(".tmp"))
        assertTrue("listed with the import copies", t.idOfName(late) in settings.settings.value.principal.currentImportCopies)
        assertFalse("A6 ran: ${t.files.keys}", "seguridad-auto-20261001-100000.sgbak" in t.files)
        assertTrue("A6 did not delete the late copy: ${t.files.keys}", late in t.files)
    }

    /**
     * Audit D-1: a copy stuck in the folder since before the unlock (an opening whose dialog asked, «Hacer una copia
     * ahora» held in its write, a lock and a new unlock) holds the lock, so the upgrade's copy cannot even start: the
     * choice comes all the same after [UPGRADE_COPY_WAIT_MS]. When the folder answers, the upgrade's copy is made late,
     * listed with the import copies, not told as the upgrade's, and A6 does not delete it.
     */
    @Test fun aDueCopyStuckBeforeTheUnlockDoesNotHoldTheUpgrade() = runTest {
        val t = SlowFolder().apply { failWrite = IOException("sin permiso") }
        val s = setup(t)
        s.unlock("maestra-de-prueba-143".toCharArray())
        runCurrent()
        assertTrue(s.pendingUpgrade.value)
        t.failWrite = null
        val gate = CompletableDeferred<Unit>().also { t.gate = it }
        try {
            auto!!.request(folder)
            advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
            runCurrent()
            assertTrue("«Hacer una copia ahora» is held: ${t.created}", t.created.last().startsWith("seguridad-auto-"))
            val old = t.put("seguridad-auto-20261001-100000.sgbak")
            settings.updatePrincipal {
                it.oneTarget(
                    autoBackupUri = folder, autoBackupCopies = listOf(old), autoBackupCopiesFolder = folder,
                    autoBackupReplacePending = true, autoBackupReplaceTargets = listOf(old),
                )
            }
            s.lock()
            runCurrent()
            val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }
            runCurrent()
            assertTrue(s.upgradeCopying.value)
            advanceTimeBy(UPGRADE_COPY_WAIT_MS + 1)
            runCurrent()
            assertFalse(s.upgradeCopying.value)
            assertTrue(s.pendingUpgrade.value)
            assertTrue(s.upgradeAutoCopyNotDone)
            assertEquals(UPGRADE_PENDING, refusal { s.upsert(note("x")) })
            withTimeout(5_000) { unlocking.await() }
        } finally {
            gate.complete(Unit)
        }
        runCurrent()
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals("never told as the upgrade's", null, s.upgradeDone.value)
        val late = t.files.keys.filter { it.startsWith("seguridad-antes-de-2-0-") && !it.endsWith(".tmp") }
        assertEquals("the late copy: ${t.files.keys}", 1, late.size)
        assertTrue("listed with the import copies", t.idOfName(late.single()) in settings.settings.value.principal.currentImportCopies)
        assertFalse("A6 ran: ${t.files.keys}", "seguridad-auto-20261001-100000.sgbak" in t.files)
        assertTrue(s.completeUpgrade())
        assertEquals(4, VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText()).version)
        assertTrue("A6 did not delete the late copy", late.single() in t.files)
    }
}
