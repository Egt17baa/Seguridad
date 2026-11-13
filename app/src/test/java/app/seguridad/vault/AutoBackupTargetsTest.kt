package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.settings.LegacyVaultSettings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.TargetRefusal
import app.seguridad.settings.VaultSettings
import app.seguridad.settings.canAddTarget
import app.seguridad.settings.firstBackupError
import app.seguridad.settings.oldestBackupAt
import app.seguridad.settings.replaceToldAny
import app.seguridad.settings.listedCopies
import app.seguridad.settings.vault
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.settings.withTarget
import app.seguridad.settings.withTargetAdded
import app.seguridad.settings.withTargetFolderChanged
import app.seguridad.settings.withTargetRemoved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 2.0, task 8 (design «Copias»): up to 3 targets per vault, each with its calendar and its own lists; A6, the rotation and
 * the copies before importing by target and by vault; the settings of 1.4.3 as one [BackupSchedule.ON_CHANGE] target.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoBackupTargetsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val masterA = "maestra-de-prueba-a"
    private val masterB = "maestra-de-prueba-b"

    /** November 2030: later than the files' own times, so no copy looks missed by them. */
    private fun at(d: Int, h: Int = 10, m: Int = 0) = LocalDateTime.of(2030, 11, d, h, m).toInstant(ZoneOffset.UTC).toEpochMilli()

    private var clock = at(13)

    private fun store(name: String, beforeReplace: (suspend () -> ReplacePrepared)? = null) =
        if (beforeReplace == null) {
            VaultStore(File(tmp.newFolder(name), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        } else {
            VaultStore(
                File(tmp.newFolder(name), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock },
                kdf = { KdfParams(1024, 1, 1) }, beforeReplace = beforeReplace,
            )
        }

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "secreto $id", createdAt = 0, updatedAt = 0)

    private suspend fun opened(name: String, master: String) = store(name).apply {
        load()
        create(master.toCharArray())
    }

    /** Files in memory; a document id is the file's name (a rename changes it, as with ExternalStorageProvider). */
    private class Folder : BackupTarget {
        val files = LinkedHashMap<String, ByteArray>()
        /** Every file created here, in order. */
        val created = mutableListOf<String>()
        /** Called at the first byte written (to cut the copy in the middle). */
        var onWrite: (() -> Unit)? = null
        /** Thrown by [create] (a folder that cannot be written); [tries] counts the attempts. */
        var failCreate: Exception? = null
        var tries = 0

        override suspend fun create(name: String): BackupFile {
            tries++
            failCreate?.let { throw it }
            check(name !in files)
            files[name] = ByteArray(0)
            created += name
            return object : BackupFile {
                var current = name
                override val id: String get() = "doc:$current"

                override suspend fun write(writer: suspend (OutputStream) -> Unit) {
                    writer(object : OutputStream() {
                        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            onWrite?.let { hook ->
                                onWrite = null
                                hook()
                            }
                            files[current] = files.getValue(current) + b.copyOfRange(off, off + len)
                        }
                    })
                }

                override fun rename(to: String) {
                    check(to !in files)
                    files[to] = files.remove(current)!!
                    current = to
                }

                override fun delete() {
                    files.remove(current)
                }
            }
        }

        override fun list(prefix: String) = files.keys.filter { it.startsWith(prefix) }.map { BackupEntry("doc:$it", it) }

        override fun delete(id: String) {
            files.remove(id.removePrefix("doc:"))
        }

        fun copies() = files.keys.filter { it.startsWith(AUTO_BACKUP_PREFIX) && !it.endsWith(".tmp") }
    }

    private val settings by lazy { SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined) }

    /** As AppContainer wires it: the vault [id]'s targets from the settings, each result recorded there. */
    private fun TestScope.auto(s: VaultStore, id: String, folders: Map<String, Folder>) = AutoBackup(
        s,
        targets = { settings.settings.value.vault(id).targets },
        open = { folders[it] },
        scope = backgroundScope,
        now = { clock },
        onResult = { r -> settings.updateVault(id) { it.withAutoBackupResult(r, s.generation) } },
        zone = ZoneOffset.UTC,
    ).also { runCurrent() }

    private fun TestScope.settle() {
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
    }

    private fun target(id: String, uri: String) = settings.settings.value.vault(id).targets.single { it.uri == uri }

    /** What a copy holds, opened with [master] in a phone of its own: the titles of its entries. */
    private suspend fun titlesIn(copy: ByteArray, master: String): Set<String> {
        val other = store("abrir-${System.nanoTime()}").apply { load() }
        other.importReplace(ByteArrayInputStream(copy), master.toCharArray())
        return (other.state.value as VaultState.Unlocked).entries.map { it.title }.toSet()
    }

    @Test fun threeTargetsMax() = runTest {
        var v = VaultSettings()
        for (n in 1..4) v = v.withTargetAdded("content://c$n", BackupSchedule.ON_CHANGE)
        assertEquals(listOf("content://c1", "content://c2", "content://c3"), v.targets.map { it.uri })
        assertEquals(TargetRefusal.TOO_MANY, v.canAddTarget("content://c4"))
        assertEquals(TargetRefusal.ALREADY_A_TARGET, v.canAddTarget("content://c2"))
        assertEquals(v, v.withTargetFolderChanged("content://c1", "content://c2")) // Never one folder twice.
        assertEquals(TargetRefusal.ALREADY_A_TARGET, v.withTargetRemoved("content://c3").canAddTarget("content://c1"))
        assertNull(v.withTargetRemoved("content://c3").canAddTarget("content://c4"))
        // A settings file with more (or one folder twice) is read as at most 3, never one folder twice.
        File(tmp.root, "settings.json").writeText(
            """{"perVault":{"v":{"targets":[{"uri":"content://c1","keep":3,"schedule":"DAILY"},{"uri":"content://c1","keep":5,"schedule":"WEEKLY","copies":["dup"]},""" +
                """{"uri":"content://c2","keep":7,"schedule":"ON_CHANGE"},{"uri":"content://c3","keep":10,"schedule":"MONTHLY"},""" +
                """{"uri":"content://c4","keep":5,"schedule":"ON_CHANGE","copies":["d4"]}]}}}""",
        )
        settings.load()
        val read = settings.settings.value.vault("v").targets
        assertEquals(listOf("content://c1", "content://c2", "content://c3"), read.map { it.uri })
        assertEquals(listOf(3, 5, 10), read.map { it.keep }) // 7 is not a choice: 5.
        assertEquals(listOf(BackupSchedule.DAILY, BackupSchedule.ON_CHANGE, BackupSchedule.MONTHLY), read.map { it.schedule })
        // Review M-7: the lists of the targets left out are kept as past ones, never lost.
        val past = settings.settings.value.vault("v").pastTargets
        assertEquals(listOf(listOf("dup"), listOf("d4")), past.map { it.copies })

        // Three targets: one change, one copy in each, one at a time and in their order.
        val s = opened("a", masterA)
        val order = mutableListOf<String>()
        val folders = (1..3).associate { n -> "content://f$n" to Folder().also { f -> f.onWrite = { order += "f$n" } } }
        auto(s, "a", folders)
        settings.updateVault("a") { (1..3).fold(it) { acc, n -> acc.withTargetAdded("content://f$n", BackupSchedule.ON_CHANGE) } }
        s.upsert(note("1"))
        settle()
        assertEquals(listOf("f1", "f2", "f3"), order)
        for ((uri, f) in folders) {
            assertEquals(1, f.copies().size)
            assertEquals(f.copies().map { "doc:$it" }, target("a", uri).copies)
        }
    }

    /**
     * Two targets whose folders hold the same files (two tree URIs of one folder): each deletes only the copies on its own
     * list, never the other's, even with names alike; and a file left there by someone else is never touched.
     */
    @Test fun eachTargetRotatesOnlyItsOwn() = runTest {
        val s = opened("a", masterA)
        val shared = Folder()
        shared.files["seguridad-auto-20200101-000000.sgbak"] = byteArrayOf(1) // Not on any list.
        auto(s, "a", mapOf("content://uno" to shared, "content://dos" to shared))
        settings.updateVault("a") {
            it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE, keep = 3).withTargetAdded("content://dos", BackupSchedule.ON_CHANGE, keep = 5)
        }
        repeat(7) { i ->
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        val uno = target("a", "content://uno").copies
        val dos = target("a", "content://dos").copies
        assertEquals(3, uno.size)
        assertEquals(5, dos.size)
        assertTrue(uno.none { it in dos })
        // In the folder: the 3 of the one, the 5 of the other, and the foreign file.
        assertEquals((uno + dos).map { it.removePrefix("doc:") }.toSet() + "seguridad-auto-20200101-000000.sgbak", shared.files.keys.toSet())
    }

    /** A6 by target: each replaces the old copies on its own lists, whatever its calendar, at the first unlock. */
    @Test fun a6PerTarget() = runTest {
        val s = opened("a", masterA)
        val one = Folder()
        val two = Folder()
        val a = auto(s, "a", mapOf("content://uno" to one, "content://dos" to two))
        settings.updateVault("a") {
            it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE).withTargetAdded("content://dos", BackupSchedule.DAILY)
        }
        // Copies under the old password: three in «uno» (by change), one in «dos» (its calendar, once a day) and two asked.
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        a.request("content://dos")
        settle()
        assertEquals(3, one.copies().size)
        assertEquals(1, two.copies().size)
        val old = settings.settings.value.vault("a").listedCopies
        // The password changes (the vault locks): the request is saved for each target, with its own copies.
        s.lock()
        clock += 60_000
        assertEquals(AUTO_BACKUP_REPLACE_WAITS, a.replaceOldCopies(old).error)
        assertTrue(target("a", "content://uno").replacePending)
        assertTrue(target("a", "content://dos").replacePending)
        // The next unlock does both, «dos» also though its calendar is not due (it copied today).
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertEquals(1, one.copies().size)
        assertEquals(1, two.copies().size)
        assertTrue(old.none { id -> id.removePrefix("doc:") in one.files || id.removePrefix("doc:") in two.files })
        assertFalse(target("a", "content://uno").replacePending)
        assertFalse(target("a", "content://dos").replacePending)
        // Told once, as one outcome for both, with the text of several folders.
        val told = a.replaceOutcome.value!!
        assertTrue(told.replaced)
        assertNull(told.error)
        assertTrue(replacedText(told.unlistedLeft, 2).startsWith("Se hizo una copia nueva en cada carpeta"))
    }

    /** A settings.json of 1.4.3 (and the objects the builds of tasks 2 to 7 wrote) give one ON_CHANGE target; nothing is lost. */
    @Test fun oldSettingsGiveOneOnChangeTarget() = runTest {
        val folder = "content://com.android.externalstorage.documents/tree/primary%3ACopias"
        val before = "content://com.android.externalstorage.documents/tree/primary%3AAntes"
        File(tmp.root, "settings.json").writeText(
            """{"lockAfter":"MIN_1","kdf":"HIGH","autoBackupUri":"$folder","autoBackupKeep":10,
               "lastAutoBackupError":"No se pudo hacer la copia automática.","lastAutoBackupAt":1700000000000,
               "autoBackupCopies":["c1","c2"],"autoBackupCopiesFolder":"$folder","autoBackupReplacePending":true,
               "autoBackupReplaceTargets":["c1","i1"],"autoBackupReplaceTold":["x"],"importCopies":["i0"],"importCopiesFolder":"$before",
               "clockHighWaterMs":1800000000000,
               "perVault":{"otra":{"autoBackupKeep":3,"autoBackupCopies":["d1"],"autoBackupCopiesFolder":"$before","autoBackupOffForNewVault":true}}}""",
        )
        val big = SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined, heapKiB = { 1L shl 30 })
        big.load()
        val v = big.settings.value.vault(PRIMARY_VAULT_ID)
        assertEquals(
            listOf(
                BackupTargetSettings(
                    folder, 10, BackupSchedule.ON_CHANGE, 1700000000000L, "No se pudo hacer la copia automática.", listOf("c1", "c2"),
                    replacePending = true, replaceTargets = listOf("c1", "i1"), replaceTold = listOf("x"),
                ),
            ),
            v.targets,
        )
        // The import copies of another folder are kept for it, as 1.4.3 kept them.
        assertEquals(listOf(before), v.pastTargets.map { it.uri })
        assertEquals(listOf("i0"), v.pastTargets.single().importCopies)
        assertEquals(1800000000000L, v.clockHighWaterMs)
        assertEquals(app.seguridad.settings.KdfProfile.HIGH, v.kdf)
        // A vault of the builds before task 8, copies off with its list kept: no target, the list kept for that folder.
        val other = big.settings.value.vault("otra")
        assertEquals(emptyList<BackupTargetSettings>(), other.targets)
        assertTrue(other.autoBackupOffForNewVault)
        assertEquals(listOf("d1"), other.pastTargets.single { it.uri == before }.copies)
        // The same folder chosen again goes on rotating that list.
        assertEquals(listOf("d1"), other.withTargetAdded(before, BackupSchedule.WEEKLY).targets.single().copies)
        // Written once in the new shape, read again the same; the old names are gone.
        big.update { it.copy(lockAfter = app.seguridad.settings.LockAfter.MIN_5) }
        val text = File(tmp.root, "settings.json").readText()
        assertFalse("autoBackupUri" in text || "autoBackupCopiesFolder" in text)
        val again = SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined, heapKiB = { 1L shl 30 }).apply { load() }
        assertEquals(v, again.settings.value.vault(PRIMARY_VAULT_ID))
        assertEquals(other, again.settings.value.vault("otra"))
        // The conversion itself, with a request of 1.2.x (no targets) and the copies off.
        assertNull(LegacyVaultSettings(autoBackupUri = folder, autoBackupReplacePending = true).converted().targets.single().replaceTargets)
        assertEquals(VaultSettings(), LegacyVaultSettings().converted())
    }

    /**
     * Each vault's copies go only to its own targets: the target of vault A never gets B's copy, also with B's copies
     * running at the same time; a lock in the middle of A's copies writes nothing more of A, and says nothing after it.
     */
    @Test fun noCopyOfAnotherVault() = runTest {
        val a = opened("a", masterA)
        val b = opened("b", masterB)
        val folderA = Folder()
        val folderA2 = Folder()
        val folderB = Folder()
        // One opener for every folder: only each vault's own targets say where its copies go, as in AppContainer.
        val all = mapOf("content://a" to folderA, "content://a2" to folderA2, "content://b" to folderB)
        val autoA = auto(a, "a", all)
        val autoB = auto(b, "b", all)
        settings.updateVault("a") {
            it.withTargetAdded("content://a", BackupSchedule.DAILY).withTargetAdded("content://a2", BackupSchedule.DAILY)
        }
        settings.updateVault("b") { it.withTargetAdded("content://b", BackupSchedule.ON_CHANGE) }
        a.upsert(note("de-A"))
        b.upsert(note("de-B"))
        settle()
        // B's change copies B, into B's folder only; A's targets have a calendar and wait for runDue.
        assertEquals(1, folderB.copies().size)
        assertTrue(folderA.files.isEmpty() && folderA2.files.isEmpty())
        assertEquals(setOf("Nota de-B"), titlesIn(folderB.files.getValue(folderB.copies().single()), masterB))
        // A's calendar, while B copies again.
        b.upsert(note("otra-de-B"))
        autoA.runDue(clock)
        settle()
        assertEquals(setOf("Nota de-A"), titlesIn(folderA.files.getValue(folderA.copies().single()), masterA))
        assertEquals(setOf("Nota de-A"), titlesIn(folderA2.files.getValue(folderA2.copies().single()), masterA))
        assertEquals(2, folderB.copies().size)
        assertTrue(folderB.copies().all { titlesIn(folderB.files.getValue(it), masterB).all { t -> t.endsWith("de-B") } })
        assertEquals(scheduledCopiesText(2), autoA.scheduledNotice.value)

        // The next day, A locks (a switch to B locks it) while its first copy is being written: that copy, of A, ends in
        // A's own folder (its snapshot was taken before the lock), but nothing else of A is written, nothing lands in B's
        // folder, and nothing is said after the lock.
        clock = at(14)
        val inA = folderA.files.keys.toSet()
        val inA2 = folderA2.files.keys.toSet()
        val inB = folderB.files.keys.toSet()
        folderA.onWrite = { a.lock() }
        autoA.runDue(clock)
        runCurrent()
        assertEquals(1, (folderA.files.keys - inA).size)
        assertTrue((folderA.files.keys - inA).all { titlesIn(folderA.files.getValue(it), masterA) == setOf("Nota de-A") })
        assertEquals(inA2, folderA2.files.keys.toSet())
        assertEquals(inB, folderB.files.keys.toSet())
        assertNull(autoA.scheduledNotice.value)
        assertEquals(at(13), target("a", "content://a2").lastAt)
        // Unlocked again: the target that waited gets its copy, of A.
        a.unlock(masterA.toCharArray())
        runCurrent()
        assertEquals(2, folderA.copies().size)
        assertEquals(2, folderA2.copies().size)
        assertEquals(inB, folderB.files.keys.toSet())
        assertTrue(folderA.copies().all { titlesIn(folderA.files.getValue(it), masterA) == setOf("Nota de-A") })
        autoB.runDue(clock) // B has nothing due: «con cada cambio», and nothing missed.
        runCurrent()
        assertEquals(inB, folderB.files.keys.toSet())
    }

    /** A target with a calendar copies only from [AutoBackup.runDue] and only when due; never twice with the clock set back. */
    @Test fun calendarTargetsCopyOnlyWhenDue() = runTest {
        val s = opened("a", masterA)
        val f = Folder()
        val a = auto(s, "a", mapOf("content://a" to f))
        settings.updateVault("a") { it.withTargetAdded("content://a", BackupSchedule.WEEKLY) }
        s.upsert(note("1"))
        settle()
        assertTrue("A change does not copy to a target with a calendar", f.files.isEmpty())
        a.runDue(clock)
        assertEquals(1, f.copies().size)
        a.scheduledNotice.value?.let { assertTrue(a.takeScheduled(it)) }
        // The same day, later in the week, and with the clock set back: nothing.
        for (t in listOf(at(13, 18), at(19, 23, 59), at(12), at(10))) {
            clock = t
            a.runDue(t)
            assertEquals(1, f.copies().size)
        }
        assertNull(a.scheduledNotice.value)
        // A week after: one, and a second call that day makes none.
        clock = at(20, 9)
        a.runDue(clock)
        a.runDue(clock + 1_000)
        assertEquals(2, f.copies().size)
        // Locked: it waits, and is made at the unlock (which runs runDue).
        clock = at(27, 9)
        s.lock()
        a.runDue(clock)
        runCurrent()
        assertEquals(2, f.copies().size)
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertEquals(3, f.copies().size)
        // Changing the calendar keeps the target and its copies.
        settings.updateVault("a") { it.withTarget("content://a") { t -> t.copy(schedule = BackupSchedule.ON_CHANGE) } }
        clock += 60_000
        s.upsert(note("2"))
        settle()
        assertEquals(4, f.copies().size)
        assertEquals(4, target("a", "content://a").copies.size)
    }

    // ---- Round 1 of fixes (review of task 8) ----

    /** Files in [f] with a copy's name, as a phone of 1.4.3 left them: their ids are "doc:<name>". */
    private fun oldCopies(f: Folder, n: Int): List<String> = (1..n).map { i ->
        val name = "seguridad-auto-202001%02d-000000.sgbak".format(i)
        f.files[name] = byteArrayOf(i.toByte())
        "doc:$name"
    }

    /**
     * Review I-1: a folder chosen again keeps its «Conservar las N últimas» (and its calendar): its first copy never deletes
     * copies the user wanted to keep. Removed and added again in 2.0, and from 1.4.3 with the copies off.
     */
    @Test fun aFolderChosenAgainKeepsItsNumberOfCopies() = runTest {
        val s = opened("a", masterA)
        val f = Folder()
        val a = auto(s, "a", mapOf("content://f" to f))
        settings.updateVault("a") { it.withTargetAdded("content://f", BackupSchedule.WEEKLY, keep = 10) }
        settings.updateVault("a") { it.withTarget("content://f") { t -> t.copy(schedule = BackupSchedule.ON_CHANGE) } }
        repeat(10) { i ->
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        assertEquals(10, f.copies().size)
        settings.updateVault("a") { it.withTargetRemoved("content://f") }
        // As Ajustes adds it: «con cada cambio» and the default number; what the folder had comes back.
        settings.updateVault("a") { it.withTargetAdded("content://f", BackupSchedule.ON_CHANGE) }
        assertEquals(10, target("a", "content://f").keep)
        clock += 60_000
        a.request("content://f")
        settle()
        assertEquals(10, f.copies().size)
        assertEquals(10, target("a", "content://f").copies.size)

        // From 1.4.3 with the copies off and «Conservar las 10»: chosen again, the 10 listed stay (9 and the new one).
        val g = Folder()
        val ids = oldCopies(g, 10)
        File(tmp.root, "settings.json").writeText(
            """{"perVault":{"b":{"autoBackupKeep":10,"autoBackupCopies":[${ids.joinToString(",") { "\"$it\"" }}],""" +
                """"autoBackupCopiesFolder":"content://g"}}}""",
        )
        settings.load()
        val b = opened("b", masterB)
        val autoB = auto(b, "b", mapOf("content://g" to g))
        settings.updateVault("b") { it.withTargetAdded("content://g", BackupSchedule.ON_CHANGE) }
        assertEquals(10, target("b", "content://g").keep)
        autoB.request("content://g")
        settle()
        assertEquals(10, g.files.size)
        // A folder changed to one that had its own number keeps the larger.
        val v = VaultSettings().withTargetAdded("content://x", BackupSchedule.DAILY, keep = 3).withTargetAdded("content://y", BackupSchedule.DAILY, keep = 10)
            .withTarget("content://y") { it.copy(copies = listOf("y1")) }.withTargetRemoved("content://y")
        assertEquals(10, v.withTargetFolderChanged("content://x", "content://y").targets.single().keep)
    }

    private suspend fun TestScope.a6Asked(s: VaultStore, a: AutoBackup) {
        val old = settings.settings.value.vault("a").listedCopies
        s.lock()
        runCurrent()
        assertEquals(AUTO_BACKUP_REPLACE_WAITS, a.replaceOldCopies(old).error)
    }

    /** Review I-2: with two folders that fail differently, both failures are told, in one outcome, each once in all. */
    @Test fun a6FailuresAreToldOnceInAll() = runTest {
        val s = opened("a", masterA)
        val one = Folder()
        val two = Folder()
        val a = auto(s, "a", mapOf("content://uno" to one, "content://dos" to two))
        settings.updateVault("a") { it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE).withTargetAdded("content://dos", BackupSchedule.ON_CHANGE) }
        clock += 60_000
        s.upsert(note("1"))
        settle()
        a6Asked(s, a)
        one.failCreate = java.io.IOException("lleno")
        two.failCreate = SecurityException("sin permiso")
        s.unlock(masterA.toCharArray())
        runCurrent()
        val told = a.replaceOutcome.value!!
        assertFalse(told.replaced)
        assertEquals(listOf(AUTO_BACKUP_WRITE_FAILED, AUTO_BACKUP_NO_PERMISSION), told.error!!.lines())
        assertEquals(listOf(AUTO_BACKUP_WRITE_FAILED), target("a", "content://uno").replaceTold)
        assertEquals(listOf(AUTO_BACKUP_NO_PERMISSION), target("a", "content://dos").replaceTold)
        a.replaceOutcomeShown(told)
        // The same failures at the next unlock: nothing told again.
        s.lock()
        runCurrent()
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertNull(a.replaceOutcome.value)
        // «uno» now fails as «dos» did: already told for this request, so not told again either.
        one.failCreate = SecurityException("sin permiso")
        s.lock()
        runCurrent()
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertNull(a.replaceOutcome.value)
        // Both work: the success, once.
        one.failCreate = null
        two.failCreate = null
        s.lock()
        runCurrent()
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertTrue(a.replaceOutcome.value!!.replaced)
    }

    /** Review I-2: two folders that fail the same way: the failure is told once, not once per folder. */
    @Test fun a6SameFailureInTwoFoldersIsToldOnce() = runTest {
        val s = opened("a", masterA)
        val one = Folder()
        val two = Folder()
        val a = auto(s, "a", mapOf("content://uno" to one, "content://dos" to two))
        settings.updateVault("a") { it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE).withTargetAdded("content://dos", BackupSchedule.ON_CHANGE) }
        clock += 60_000
        s.upsert(note("1"))
        settle()
        a6Asked(s, a)
        one.failCreate = java.io.IOException("lleno")
        two.failCreate = java.io.IOException("lleno")
        s.unlock(masterA.toCharArray())
        runCurrent()
        val told = a.replaceOutcome.value!!
        assertEquals(AUTO_BACKUP_WRITE_FAILED, told.error)
        a.replaceOutcomeShown(told)
        s.lock()
        runCurrent()
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertNull(a.replaceOutcome.value)
    }

    /**
     * Review I-3: an «Importar copia» while the first of two folders is written, during A6: the second folder is read again
     * and its request is gone, so nothing says the old copies were deleted (they are still there), at the unlock and when
     * asked with the vault open.
     */
    @Test fun anImportDuringA6NeverSaysTheOldCopiesWereDeleted() = runTest {
        val s = opened("a", masterA)
        val imported = store("i", beforeReplace = { settings.forgetAutoBackupCopiesForImport("a") })
        val one = Folder()
        val two = Folder()
        val a = auto(s, "a", mapOf("content://uno" to one, "content://dos" to two))
        settings.updateVault("a") { it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE).withTargetAdded("content://dos", BackupSchedule.ON_CHANGE) }
        clock += 60_000
        s.upsert(note("1"))
        settle()
        val oldInTwo = two.copies().toSet()
        val copy = one.files.getValue(one.copies().single())
        // The import runs on a store of its own that shares the settings, as the import of the app forgets the lists.
        a6Asked(s, a)
        one.onWrite = { kotlinx.coroutines.runBlocking { imported.load(); imported.importReplace(ByteArrayInputStream(copy), masterA.toCharArray()) } }
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertTrue(oldInTwo.all { it in two.files })
        assertTrue(a.replaceOutcome.value?.replaced != true)
        assertFalse(target("a", "content://dos").replacePending)

        // Asked again with the vault open, and an import (here, a new vault: the generation changes) during the first copy.
        a.replaceOutcome.value?.let(a::replaceOutcomeShown)
        clock += 60_000
        s.upsert(note("2"))
        settle()
        val listed = settings.settings.value.vault("a").listedCopies
        val inTwo = two.copies().toSet()
        one.onWrite = { kotlinx.coroutines.runBlocking { settings.forgetAutoBackupCopiesForImport("a"); s.destroy(); s.create(masterA.toCharArray()) } }
        val r = a.replaceOldCopies(listed)
        assertTrue("never «done» after the vault changed", r.error != null)
        assertTrue(inTwo.all { it in two.files })
    }

    /** Review M-8 (a): A6 in one folder shared by two targets: each deletes only its own, never another's nor a stranger's. */
    @Test fun a6InASharedFolderDeletesOnlyEachOnesOwn() = runTest {
        val s = opened("a", masterA)
        val shared = Folder()
        val stranger = "seguridad-auto-20200101-000000.sgbak"
        shared.files[stranger] = byteArrayOf(1)
        val a = auto(s, "a", mapOf("content://uno" to shared, "content://dos" to shared))
        settings.updateVault("a") { it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE).withTargetAdded("content://dos", BackupSchedule.DAILY) }
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        a.request("content://dos")
        settle()
        val uno = target("a", "content://uno").copies
        val dos = target("a", "content://dos").copies
        assertEquals(3, uno.size)
        assertEquals(1, dos.size)
        // «dos» is asked to delete everything in the folder, the stranger and «uno»'s too: it deletes only its own.
        settings.updateVault("a") { it.withTarget("content://dos") { t -> t.copy(replacePending = true, replaceTargets = uno + dos + "doc:$stranger") } }
        clock += 60_000
        s.lock()
        runCurrent()
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertTrue(stranger in shared.files)
        assertTrue(uno.all { it.removePrefix("doc:") in shared.files })
        assertTrue(dos.none { it.removePrefix("doc:") in shared.files })
        assertFalse(target("a", "content://dos").replacePending)
    }

    /** Review M-8 (b): after «Cambiar de carpeta» or «Quitar», the folder left is never touched, by the rotation nor by A6. */
    @Test fun aFolderLeftIsNeverTouched() = runTest {
        val s = opened("a", masterA)
        val x = Folder()
        val y = Folder()
        val a = auto(s, "a", mapOf("content://x" to x, "content://y" to y))
        settings.updateVault("a") { it.withTargetAdded("content://x", BackupSchedule.ON_CHANGE, keep = 3) }
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        val inX = x.files.toMap()
        settings.updateVault("a") { it.withTargetFolderChanged("content://x", "content://y") }
        repeat(5) { i ->
            clock += 60_000
            s.upsert(note("m$i"))
            settle()
        }
        a6Asked(s, a)
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertEquals(inX.keys, x.files.keys)
        assertEquals(1, y.copies().size)
        // Removed with copies listed: none is deleted, and its list is kept for the same folder chosen again.
        settings.updateVault("a") { it.withTargetRemoved("content://y") }
        val inY = y.files.keys.toSet()
        repeat(4) { i ->
            clock += 60_000
            s.upsert(note("k$i"))
            settle()
        }
        assertEquals(inY, y.files.keys)
        assertEquals(1, settings.settings.value.vault("a").pastTargets.single { it.uri == "content://y" }.copies.size)
    }

    /** Review M-1: the calendar's notice is never shown after a lock, also one too quick for the collector to see. */
    @Test fun theCalendarNoticeNeverOutlivesALock() = runTest {
        val s = opened("a", masterA)
        val f = Folder()
        val a = auto(s, "a", mapOf("content://f" to f))
        settings.updateVault("a") { it.withTargetAdded("content://f", BackupSchedule.DAILY) }
        a.runDue(clock)
        val text = a.scheduledNotice.value!!
        s.lock()
        s.unlock(masterA.toCharArray()) // No yield: the collector never sees the lock.
        assertFalse(a.takeScheduled(text))
        assertNull(a.scheduledNotice.value)
        // A lock during the copy: nothing posted.
        clock = at(14)
        f.onWrite = { s.lock() }
        a.runDue(clock)
        runCurrent()
        assertNull(a.scheduledNotice.value)
    }

    /** Review M-3: a folder that fails is not tried again each time the app comes back, only later or at the unlock. */
    @Test fun aFailingFolderIsNotRetriedAtEveryOpening() = runTest {
        val s = opened("a", masterA)
        val f = Folder()
        val a = auto(s, "a", mapOf("content://f" to f))
        settings.updateVault("a") { it.withTargetAdded("content://f", BackupSchedule.DAILY) }
        f.failCreate = java.io.IOException("lleno")
        a.runDue(clock)
        assertEquals(1, f.tries)
        a.runDue(clock + 60_000)
        a.runDue(clock + 4 * 60_000)
        assertEquals(1, f.tries)
        a.runDue(clock + RETRY_AFTER_FAILURE_MS + 60_000)
        assertEquals(2, f.tries)
        s.lock()
        runCurrent()
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertEquals(3, f.tries)
        assertEquals(AUTO_BACKUP_WRITE_FAILED, target("a", "content://f").lastError)
    }

    /** Review M-4: a change copied when the app was opened is not copied again by the debounce that follows. */
    @Test fun aChangeIsCopiedOnce() = runTest {
        val s = opened("a", masterA)
        val f = Folder()
        val a = auto(s, "a", mapOf("content://f" to f))
        settings.updateVault("a") { it.withTargetAdded("content://f", BackupSchedule.ON_CHANGE) }
        s.upsert(note("1"))
        a.runDue(clock) // The app comes back before the debounce: the change is missed, and copied now.
        assertEquals(1, f.copies().size)
        settle()
        assertEquals(1, f.copies().size)
        clock += 60_000
        s.upsert(note("2"))
        settle()
        assertEquals(2, f.copies().size)
    }

    /** Review M-5: the dialog of A6 reads the folders that still have the request, not a later failure of another. */
    @Test fun theA6DialogReadsOnlyTheFoldersWithTheRequest() {
        val v = VaultSettings(
            targets = listOf(
                BackupTargetSettings("content://uno", 5, BackupSchedule.ON_CHANGE, lastAt = 9L, lastError = AUTO_BACKUP_WRITE_FAILED),
                BackupTargetSettings("content://dos", 5, BackupSchedule.ON_CHANGE, lastAt = 7L, replacePending = true, replaceTold = emptyList()),
            ),
        )
        assertNull(v.firstBackupError)
        assertEquals(7L, v.oldestBackupAt)
        assertFalse(v.replaceToldAny)
        val done = v.copy(targets = v.targets.map { it.copy(replacePending = false) })
        assertEquals(AUTO_BACKUP_WRITE_FAILED, done.firstBackupError)
    }

    /** Review M-8 (g): a vault's settings of 1.4.3 with a value of the wrong type lose nothing else. */
    @Test fun aBadOldVaultLosesNothingElse() = runTest {
        File(tmp.root, "settings.json").writeText(
            """{"lockAfter":"MIN_1","perVault":{"mala":{"autoBackupUri":"content://m","autoBackupCopies":"x"},""" +
                """"buena":{"targets":[{"uri":"content://b","keep":10,"schedule":"WEEKLY","copies":["b1"]}]},""" +
                """"vieja":{"autoBackupUri":"content://v","autoBackupKeep":3}}}""",
        )
        settings.load()
        val all = settings.settings.value
        assertEquals(app.seguridad.settings.LockAfter.MIN_1, all.lockAfter)
        assertEquals(listOf(BackupTargetSettings("content://b", 10, BackupSchedule.WEEKLY, copies = listOf("b1"))), all.vault("buena").targets)
        assertEquals(listOf(BackupTargetSettings("content://v", 3, BackupSchedule.ON_CHANGE)), all.vault("vieja").targets)
        assertEquals(emptyList<BackupTargetSettings>(), all.vault("mala").targets)
    }

    /**
     * Re-review 1, I-3 (a): a change copies «uno» (con cada cambio), which replaces its old copies, while «dos» (daily, its
     * request still waiting) does not try: nothing says the old copies were deleted «en cada carpeta» until «dos» did too.
     */
    @Test fun noSuccessWhileAFolderThatDidNotTryStillHasTheRequest() = runTest {
        val s = opened("a", masterA)
        val one = Folder()
        val two = Folder()
        val a = auto(s, "a", mapOf("content://uno" to one, "content://dos" to two))
        settings.updateVault("a") { it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE).withTargetAdded("content://dos", BackupSchedule.DAILY) }
        clock += 60_000
        s.upsert(note("1"))
        settle()
        a.request("content://dos")
        settle()
        val oldInTwo = two.copies().toSet()
        a6Asked(s, a)
        one.failCreate = java.io.IOException("lleno")
        two.failCreate = SecurityException("sin permiso")
        s.unlock(masterA.toCharArray())
        runCurrent()
        a.replaceOutcomeShown(a.replaceOutcome.value!!)
        // «uno» works again and a change is saved: «uno» replaces, «dos» (daily) does not try.
        one.failCreate = null
        clock += 60_000
        s.upsert(note("2"))
        settle()
        assertFalse(target("a", "content://uno").replacePending)
        assertTrue(target("a", "content://dos").replacePending)
        assertNull("no «se borraron … en cada carpeta» while «dos» still has its old copies", a.replaceOutcome.value)
        assertTrue(oldInTwo.all { it in two.files })
        // «dos» works at the next unlock: now the success, once, with every folder done.
        two.failCreate = null
        s.lock()
        runCurrent()
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertTrue(a.replaceOutcome.value!!.replaced)
        assertTrue(oldInTwo.none { it in two.files })
    }

    /** Re-review 1, I-3 (b): a lock after the first of two folders replaced: no success until the second one has too. */
    @Test fun noSuccessWhenALockLeavesAFolderOut() = runTest {
        val s = opened("a", masterA)
        val one = Folder()
        val two = Folder()
        val a = auto(s, "a", mapOf("content://uno" to one, "content://dos" to two))
        settings.updateVault("a") { it.withTargetAdded("content://uno", BackupSchedule.ON_CHANGE).withTargetAdded("content://dos", BackupSchedule.ON_CHANGE) }
        clock += 60_000
        s.upsert(note("1"))
        settle()
        val oldInTwo = two.copies().toSet()
        a6Asked(s, a)
        one.onWrite = { s.lock() }
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertFalse(target("a", "content://uno").replacePending)
        assertTrue(target("a", "content://dos").replacePending)
        assertNull(a.replaceOutcome.value)
        assertTrue(oldInTwo.all { it in two.files })
        s.unlock(masterA.toCharArray())
        runCurrent()
        assertTrue(a.replaceOutcome.value!!.replaced)
        assertTrue(oldInTwo.none { it in two.files })
    }
}
