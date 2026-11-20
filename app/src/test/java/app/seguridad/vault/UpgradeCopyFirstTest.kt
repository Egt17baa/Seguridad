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
            onResult = { r -> settings.updatePrincipal { it.withAutoBackupResult(r, s.generation) } },
            zone = ZoneOffset.UTC,
            importCopies = { settings.settings.value.principal.currentImportCopies },
            onImportCopy = { f, id, generation -> settings.updatePrincipal { it.withImportCopy(f, id, generation, s.generation) } },
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
}
