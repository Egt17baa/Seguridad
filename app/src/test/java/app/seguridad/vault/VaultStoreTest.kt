package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_000L
    private val idle = 5 * 60_000L
    private fun store(name: String = "vault.bin") =
        VaultStore(File(tmp.root, name), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) }, elapsed = { clock })

    /** A store with the 1.3.0 clock mark and the given Argon2id cost for what it wraps next. */
    private fun clockStore(highWater: () -> Long, seen: (Long) -> Unit, kdf: KdfParams = KdfParams(1024, 1, 1), reset: (Long) -> Unit = {}) =
        VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { kdf },
            clockHighWater = highWater, onClockSeen = seen, onClockReset = reset,
        )

    /** A .sgbak (no attachments) of a vault made with [kdf] and one note, opened by the master password "m". */
    private fun backupWith(kdf: KdfParams): Pair<ByteArray, VaultFile> {
        val (file, dek) = VaultCrypto.create("m".toCharArray(), clock, kdf)
        val written = VaultCrypto.write(file, dek, listOf(entry("1", "Wallet")))
        return java.io.ByteArrayOutputStream().also { writeBackup(it, VaultCrypto.encodeFile(written), emptyMap()) }.toByteArray() to written
    }

    private fun entry(id: String, title: String) = VaultEntry(id, EntryType.NOTE, title, notes = "secreto", createdAt = 0, updatedAt = 0)

    @Test fun `create, add, lock and unlock again`() = runTest {
        val s = store().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.create("maestra".toCharArray())
        s.upsert(entry("b", "Banco"))
        s.upsert(entry("a", "agua"))
        assertEquals(listOf("agua", "Banco"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        s.lock()
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        val reopened = store().apply { load() }
        assertEquals(VaultState.Locked, reopened.state.value)
        reopened.unlock("maestra".toCharArray())
        assertEquals(2, (reopened.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun `wrong master keeps it locked`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); lock() }
        try {
            s.unlock("x".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `biometric key needs the master every 7 days`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        clock += MASTER_RECHECK_MS - 1
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
        s.lock()
        clock += 1
        assertFalse(s.biometricAllowed)
        try {
            s.unlockWithKey(key.copyOf())
            fail()
        } catch (e: MasterRequired) {
            // expected
        }
    }

    @Test fun `idle lock after 5 minutes`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        clock += idle - 1
        assertFalse(s.lockIfIdle())
        s.touch()
        clock += idle
        assertTrue(s.lockIfIdle())
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `each lock says why it happened`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        s.lock() // «Bloquear»
        assertEquals(LockReason.MANUAL, s.lastLockReason)
        s.unlock("m".toCharArray())
        clock += idle
        assertTrue(s.lockIfIdle())
        assertEquals(LockReason.IDLE, s.lastLockReason)
        s.lock(LockReason.LEFT_APP) // Already locked: not a lock, the reason stays.
        assertEquals(LockReason.IDLE, s.lastLockReason)
        s.unlock("m".toCharArray())
        s.lock(LockReason.LEFT_APP)
        assertEquals(LockReason.LEFT_APP, s.lastLockReason)
    }

    @Test fun `a lock records whether the app was in use at that moment`() = runTest {
        var foreground = true
        val s = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock },
            kdf = { KdfParams(1024, 1, 1) }, inForeground = { foreground }, elapsed = { clock },
        ).apply { load(); create("m".toCharArray()) }
        s.lock()
        assertTrue(s.lastLockInForeground)
        s.unlock("m".toCharArray())
        // MainActivity.onStart: the idle check runs before the flag is back to true; a later read must not change it.
        foreground = false
        clock += idle
        assertTrue(s.lockIfIdle())
        foreground = true
        assertFalse(s.lastLockInForeground)
        assertEquals(LockReason.IDLE, s.lastLockReason)
    }

    @Test fun `export and import into another phone`() = runTest {
        val a = store("a.bin").apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")) }
        val exported = a.exportText()!!
        val b = store("b.bin").apply { load(); create("otra".toCharArray()) }
        try {
            b.importReplace(exported, "mala".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertTrue((b.state.value as VaultState.Unlocked).entries.isEmpty())
        b.importReplace(exported, "m".toCharArray())
        assertEquals(listOf("Wallet"), (b.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    /** An export written by hand with its master password, holding [entries] as they are (no store checks). */
    private fun handMadeExport(vararg entries: VaultEntry): String {
        val (file, dek) = VaultCrypto.create("m".toCharArray(), clock, KdfParams(1024, 1, 1))
        return VaultCrypto.encodeFile(VaultCrypto.write(file, dek, entries.toList()))
    }

    private fun ref(id: String, size: Long) = AttachmentRef(id, "a.bin", size, "00".repeat(32), "AA".repeat(22))

    @Test fun `a copy that breaks what new entries keep is refused and nothing changes`() = runTest {
        val identity = VaultEntry("i", EntryType.IDENTITY, "Yo", createdAt = 0, updatedAt = 0)
        val big = (0 until 11).map { ref("%032x".format(it), MAX_ATTACHMENT_BYTES.toLong()) } // 55 MB in all.
        val all = listOf(
            "La copia está dañada: no se puede importar" to handMadeExport(entry("1", "a"), entry("1", "b")),
            "La copia está dañada: no se puede importar" to handMadeExport(identity.copy(attachments = listOf(ref("%032x".format(1), 1)))),
            "La copia está dañada: no se puede importar" to handMadeExport(identity.copy(passwordHistory = listOf(PasswordChange("vieja", 0)))),
            "Los adjuntos de la bóveda pasan de 50 MB en total" to handMadeExport(entry("1", "a").copy(attachments = big)),
            "Los adjuntos de la bóveda pasan de 50 MB en total" to handMadeExport(entry("1", "a").copy(attachments = listOf(ref("%032x".format(1), -1)))),
        )
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("x", "Mía")) }
        val before = File(tmp.root, "vault.bin").readText()
        for ((message, export) in all) {
            try {
                s.importReplace(export, "m".toCharArray())
                fail(message)
            } catch (e: CryptoFailure) {
                assertEquals(message, e.message)
            }
            assertEquals(listOf("Mía"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
            assertEquals(before, File(tmp.root, "vault.bin").readText())
        }
    }

    @Test fun `a locked vault is never replaced by an import`() = runTest {
        val export = handMadeExport(entry("1", "Otra"))
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("x", "Mía")); lock() }
        val before = File(tmp.root, "vault.bin").readText()
        for (import in listOf<suspend () -> Unit>(
            { s.importReplace(export, "m".toCharArray()) },
            { s.importReplace(export.byteInputStream(), "m".toCharArray()) },
        )) {
            try {
                import()
                fail()
            } catch (e: CryptoFailure) {
                assertEquals("Abre la bóveda antes de importar una copia", e.message)
            }
        }
        assertEquals(before, File(tmp.root, "vault.bin").readText())
        // With no vault at all («Crea tu bóveda»), importing is the way in.
        val fresh = store("otra.bin").apply { load() }
        fresh.importReplace(export, "m".toCharArray())
        assertEquals(listOf("Otra"), (fresh.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun `change master and destroy`() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "x")) }
        s.changeMaster("nueva".toCharArray())
        s.unlock("nueva".toCharArray())
        s.destroy()
        assertEquals(VaultState.NoVault, s.state.value)
        assertFalse(File(tmp.root, "vault.bin").exists())
    }

    // Seguridad 1.1.0: a Google Authenticator batch goes in with one write, all or nothing.
    @Test fun addAllSavesNewEntriesInOneWrite() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")) }
        val h = listOf(PasswordChange("vieja", 1))
        assertTrue(s.addAll(listOf(entry("2", "B").copy(passwordHistory = h), entry("3", "C"), entry("1", "Pisada"))))
        val titles = (s.state.value as VaultState.Unlocked).entries.map { it.title }
        // An id already in the vault is never overwritten, and a new entry starts without history.
        assertEquals(listOf("B", "C", "Wallet"), titles)
        assertTrue((s.state.value as VaultState.Unlocked).entries.all { it.passwordHistory.isEmpty() })
        val reopened = store().apply { load(); unlock("m".toCharArray()) }
        assertEquals(3, (reopened.state.value as VaultState.Unlocked).entries.size)
        s.lock()
        assertFalse(s.addAll(listOf(entry("4", "D"))))
    }

    // Seguridad 1.0.1: a changed master password locks the vault at once, so the user opens it with the new one
    // (and proves they remember it) before going on; the old data key is not left in memory.
    @Test fun changeMasterLocksVault() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "Wallet")) }
        assertTrue(s.changeMaster("nueva".toCharArray()))
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        // Locked means locked: no change can be saved until it is opened again.
        assertFalse(s.upsert(entry("2", "Otra")))
    }

    @Test fun newMasterUnlocksOldDoesNot() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "Wallet")) }
        assertTrue(s.changeMaster("nueva".toCharArray()))
        try {
            s.unlock("vieja".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("nueva".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        // Same on disk, as another start of the app sees it.
        val reopened = store().apply { load() }
        reopened.unlock("nueva".toCharArray())
        assertEquals(1, (reopened.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun failedChangeMasterKeepsVaultUnlockedAndUnchanged() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "Wallet")) }
        val before = File(tmp.root, "vault.bin").readText()
        val key = s.dataKeyCopy()!!
        breakSaving()
        try {
            s.changeMaster("nueva".toCharArray())
            fail()
        } catch (e: java.io.IOException) {
            // expected
        }
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        assertTrue(key.contentEquals(s.dataKeyCopy()!!))
        assertEquals(before, File(tmp.root, "vault.bin").readText())
        // The old master password still works; the new one was never saved.
        s.lock()
        s.unlock("vieja".toCharArray())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    /** Runs [before] (once) as the computation is dispatched: a lock() that lands in the middle of a save. */
    private class InterruptingDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        var before: (() -> Unit)? = null
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            before?.also { before = null }?.invoke()
            block.run()
        }
    }

    @Test fun `locking during a save neither corrupts the vault nor reopens it`() = runTest {
        val compute = InterruptingDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        compute.before = { s.lock() }
        s.upsert(entry("1", "Wallet"))
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        // Saved with the real key: the master password still opens it, with the change.
        s.unlock("maestra".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        // Same for a master change in flight.
        compute.before = { s.lock() }
        assertTrue(s.changeMaster("nueva".toCharArray()))
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("nueva".toCharArray())
        assertEquals(1, (s.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun `an unlock that finishes after leaving the app stays locked`() = runTest {
        val compute = InterruptingDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        s.lock()
        compute.before = { s.lock() }
        s.unlock("maestra".toCharArray())
        assertEquals(VaultState.Locked, s.state.value)
        assertFalse(s.changeMaster("x".toCharArray()))
    }

    /** Holds every block dispatched while closed, until [release]: an operation that keeps the mutex meanwhile. */
    private class GateDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        private val held = ArrayList<Runnable>()
        var open = true
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            if (open) block.run() else held += block
        }
        fun release() {
            open = true
            held.toList().also { held.clear() }.forEach { it.run() }
        }
    }

    @Test fun `leaving the app while an unlock waits for another one keeps the vault locked`() = runTest {
        val compute = GateDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        val key = s.dataKeyCopy()!!
        s.lock()
        for (waiting in listOf<suspend () -> Unit>({ s.unlockWithKey(key.copyOf()) }, { s.unlock("maestra".toCharArray()) })) {
            compute.open = false
            // The first one holds the mutex (its key derivation is held back); the second waits for it.
            val first = launch(start = CoroutineStart.UNDISPATCHED) { s.unlock("maestra".toCharArray()) }
            val second = launch(start = CoroutineStart.UNDISPATCHED) { waiting() }
            s.lock() // Home: MainActivity.onStop.
            compute.release()
            first.join()
            second.join()
            assertEquals(VaultState.Locked, s.state.value)
            assertNull(s.dataKeyCopy())
        }
        // Nothing was harmed: it opens as usual afterwards.
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    @Test fun `leaving the app while an import waits for the mutex keeps the vault locked`() = runTest {
        val compute = GateDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        s.upsert(entry("1", "Wallet"))
        val export = s.exportText()!!
        compute.open = false
        val first = launch(start = CoroutineStart.UNDISPATCHED) { s.upsert(entry("2", "Otra")) }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { s.importReplace(export, "maestra".toCharArray()) }
        s.lock()
        compute.release()
        first.join()
        second.join()
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        // The import was still saved: the master password opens the copy's content.
        s.unlock("maestra".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun `a damaged vault file is kept aside, never overwritten`() = runTest {
        File(tmp.root, "vault.bin").writeText("{esto no es una bóveda")
        val s = store().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.create("nueva".toCharArray())
        val kept = tmp.root.listFiles()!!.filter { it.name.startsWith("vault.bin.bad-") }
        assertEquals(1, kept.size)
        assertEquals("{esto no es una bóveda", kept.single().readText())
    }

    @Test fun `a 1_9_2 vault opens with the master password and is rewritten with its version`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")); lock() }
        // Make it look like a file from before the version key was always written.
        val f = File(tmp.root, "vault.bin")
        f.writeText(f.readText().replace("\"version\":3,", ""))
        val again = store().apply { load() }
        again.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (again.state.value as VaultState.Unlocked).entries.map { it.title })
        assertTrue(f.readText().contains("\"version\":3"))
    }

    /** A directory where the temporary file goes makes every save fail (even for root, unlike permissions). */
    private fun breakSaving() = File(tmp.root, "vault.bin.tmp").mkdir()

    @Test fun `a full disk does not lock the user out with the right master password`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")); lock() }
        breakSaving()
        s.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        // A real change cannot be saved: it fails loudly and nothing changes.
        try {
            s.delete("1")
            fail()
        } catch (e: java.io.IOException) {
            // expected
        }
        assertEquals(1, (s.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun `a vault that could not be written is not shown as locked`() = runTest {
        breakSaving()
        val s = store().apply { load() }
        try {
            s.create("m".toCharArray())
            fail()
        } catch (e: java.io.IOException) {
            // expected
        }
        assertEquals(VaultState.NoVault, s.state.value)
    }

    @Test fun `importing over an unreadable vault keeps the old file aside`() = runTest {
        val source = store("source.bin").apply { load(); create("maestra".toCharArray()); upsert(entry("a", "Nota")) }
        val export = source.exportText()!!
        // Exists but cannot be read as a file right now: the store shows "no vault" without touching it.
        val unreadable = File(tmp.root, "vault.bin").apply { mkdirs(); File(this, "keep").writeText("x") }
        val s = store().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.importReplace(export, "maestra".toCharArray())
        assertEquals(1, (s.state.value as VaultState.Unlocked).entries.size)
        assertFalse(unreadable.isDirectory)
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("vault.bin.bad-") && File(it, "keep").exists() })
    }

    @Test fun `opening keeps the time of the last change on the vault file`() = runTest {
        val s = store().apply { load(); create("maestra".toCharArray()) }
        s.upsert(entry("a", "Agua"))
        val f = File(tmp.root, "vault.bin")
        assertTrue(f.setLastModified(1_600_000_000_000L))
        assertEquals(1_600_000_000_000L, s.lastChangeSavedAt())
        s.lock()
        clock += 60_000
        val before = f.readText()
        s.unlock("maestra".toCharArray()) // Saves the last master check: bookkeeping, not a change.
        assertFalse("Sí se guardó la comprobación", before == f.readText())
        assertEquals(1_600_000_000_000L, s.lastChangeSavedAt())
        s.upsert(entry("b", "Banco")) // A real change moves it.
        assertTrue(s.lastChangeSavedAt()!! > 1_600_000_000_000L)
    }

    @Test fun `no vault file, no time`() = runTest {
        val s = store().apply { load() }
        assertNull(s.lastChangeSavedAt())
    }

    @Test fun `creating a vault runs beforeCreate first`() = runTest {
        var calls = 0
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            beforeCreate = { calls++; assertFalse("Antes de escribir", File(tmp.root, "vault.bin").exists()) })
        s.load()
        s.create("maestra".toCharArray())
        assertEquals(1, calls)
        assertTrue(s.state.value is VaultState.Unlocked)
        // Opening, editing or locking is not creating.
        s.upsert(entry("a", "Agua"))
        s.lock()
        s.unlock("maestra".toCharArray())
        assertEquals(1, calls)
    }

    @Test fun `a failing beforeCreate creates nothing and keeps the damaged file`() = runTest {
        val f = File(tmp.root, "vault.bin").apply { writeText("dañado") }
        val s = VaultStore(f, Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            beforeCreate = { throw java.io.IOException("disco lleno") })
        s.load()
        assertEquals(VaultState.NoVault, s.state.value)
        assertTrue(runCatching { s.create("maestra".toCharArray()) }.isFailure)
        assertEquals(VaultState.NoVault, s.state.value)
        assertFalse(f.exists())
        assertEquals(0L, s.revision.value)
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("vault.bin.bad-") && it.readText() == "dañado" })
    }

    // A8 (1.3.0): a copy with a cheap Argon2id is wrapped again with the profile's cost, same data key, revision + 1.
    @Test fun importWithWeakKdfIsRewrapped() = runTest {
        val profile = app.seguridad.settings.KdfProfile.STANDARD.params
        val (copy, before) = backupWith(KdfParams(8 * 1024, 1, 1))
        val s = clockStore({ 0 }, {}, profile).apply { load(); create("otra".toCharArray()) }
        s.importReplace(copy.inputStream(), "m".toCharArray())
        val saved = VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText())
        assertEquals(VaultKdf(profile.memoryKiB, profile.iterations, profile.parallelism), saved.kdf)
        assertEquals(before.revision + 1, saved.revision)
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        s.lock()
        s.unlock("m".toCharArray()) // The master opens it with the new cost.
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun importWithStrongerKdfIsKept() = runTest {
        val strong = KdfParams(128 * 1024, 4, 1)
        val (copy, _) = backupWith(strong)
        val s = clockStore({ 0 }, {}, KdfParams(64 * 1024, 3, 1)).apply { load(); create("otra".toCharArray()) }
        s.importReplace(copy.inputStream(), "m".toCharArray())
        val saved = VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText())
        assertEquals(VaultKdf(strong.memoryKiB, strong.iterations, strong.parallelism), saved.kdf)
        assertEquals(1, (s.state.value as VaultState.Unlocked).entries.size)
    }

    // A11 (1.3.0): the store reports the clock it sees and refuses the fingerprint after the clock was set back.
    @Test fun clockSeenOnUnlockAndTouchAndMarkBlocksFingerprint() = runTest {
        val seen = mutableListOf<Long>()
        var mark = 0L
        val s = clockStore({ mark }, { seen += it }).apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        assertEquals(listOf(clock), seen) // The creation opened it.
        clock += 10; s.touch()
        assertEquals(clock, seen.last())
        s.lock()
        mark = clock + 6 * 60_000
        assertFalse(s.biometricAllowed)
        try {
            s.unlockWithKey(key.copyOf())
            fail()
        } catch (e: MasterRequired) {
            // expected
        }
        mark = clock + 4 * 60_000
        assertTrue(s.biometricAllowed)
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    // A12 (1.3.0): destroy() also removes what keepAside() left.
    @Test fun destroyRemovesBadCopies() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        File(tmp.root, "vault.bin.bad-123").writeText("x")
        File(tmp.root, "vault.bin.bad-456").writeText("y")
        File(tmp.root, "att.bad-123").apply { mkdirs(); File(this, "f").writeText("z") }
        File(tmp.root, "otra.bin.bad-1").writeText("not ours")
        File(tmp.root, "att").mkdirs()
        s.destroy()
        val left = tmp.root.listFiles()!!.map { it.name }
        assertTrue(left.toString(), left.none { it.startsWith("vault.bin.bad-") || it.startsWith("att.bad-") })
        assertTrue(left.contains("otra.bin.bad-1"))
        assertFalse(File(tmp.root, "vault.bin").exists())
    }

    // A16c (1.2.1, covered here): the key handed to unlockWithKey is zeroed on every early exit.
    @Test fun unlockWithKeyEarlyExitWipesKey() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        clock += MASTER_RECHECK_MS // Recheck due: early exit with MasterRequired.
        val due = key.copyOf()
        try {
            s.unlockWithKey(due)
            fail()
        } catch (e: MasterRequired) {
            // expected
        }
        assertTrue(due.all { it == 0.toByte() })
        val noVault = store("none.bin").apply { load() } // No vault: nothing to open.
        val spare = key.copyOf()
        noVault.unlockWithKey(spare)
        assertTrue(spare.all { it == 0.toByte() })
    }

    // Fix round 1: a mark left in the future (clock set forward once) is cleared by the master password.
    @Test fun masterUnlockResetsAClockMarkInTheFuture() = runTest {
        var mark = 0L
        val s = clockStore({ mark }, { mark = maxOf(mark, it) }, reset = { mark = it }).apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        mark = clock + 365L * 24 * 60 * 60_000
        assertEquals(BiometricRefusal.CLOCK, s.biometricRefusal)
        assertFalse(s.biometricAllowed)
        s.unlock("m".toCharArray()) // The mark came down to now.
        assertEquals(clock, mark)
        s.lock()
        assertNull(s.biometricRefusal)
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
        // confirmMaster and an import reset it too; a fingerprint unlock and touch() never lower it.
        mark = clock + 60 * 60_000
        assertTrue(s.confirmMaster("m".toCharArray()))
        assertEquals(clock, mark)
        mark = clock + 60 * 60_000
        val (copy, _) = backupWith(KdfParams(1024, 1, 1))
        s.importReplace(copy.inputStream(), "m".toCharArray())
        assertEquals(clock, mark)
        mark = clock + 3
        s.touch()
        assertEquals(clock + 3, mark)
    }

    // Fix round 2 (I-1): the mark restarts only once the copy is saved; a failed save lowers nothing.
    @Test fun failedImportSaveNeverResetsTheClockMark() = runTest {
        val resets = mutableListOf<Long>()
        val s = clockStore({ 0 }, {}, reset = { resets += it }).apply { load(); create("otra".toCharArray()) }
        val (copy, written) = backupWith(KdfParams(1024, 1, 1))
        val json = VaultCrypto.encodeFile(written)
        breakSaving()
        for (import in listOf<suspend () -> Unit>(
            { s.importReplace(json, "m".toCharArray()) },
            { s.importReplace(copy.inputStream(), "m".toCharArray()) },
        )) {
            try {
                import()
                fail()
            } catch (e: java.io.IOException) {
                // expected
            }
        }
        assertTrue(resets.isEmpty())
        assertTrue((s.state.value as VaultState.Unlocked).entries.isEmpty()) // The old vault stays.
    }

    // Fix round 2 (M-1): the refusal carries its reason and its message names only that one; the key is still zeroed.
    @Test fun unlockWithKeyRefusalCarriesItsReason() = runTest {
        var mark = 0L
        val s = clockStore({ mark }, {}).apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        mark = clock + 6 * 60_000
        val a = key.copyOf()
        val clockRefusal = try { s.unlockWithKey(a); null } catch (e: MasterRequired) { e }!!
        assertEquals(BiometricRefusal.CLOCK, clockRefusal.reason)
        assertEquals(CLOCK_CHANGED_TEXT, clockRefusal.message)
        assertTrue(a.all { it == 0.toByte() })
        mark = 0
        clock += MASTER_RECHECK_MS
        val b = key.copyOf()
        val due = try { s.unlockWithKey(b); null } catch (e: MasterRequired) { e }!!
        assertEquals(BiometricRefusal.MASTER_DUE, due.reason)
        assertFalse(due.message!!.contains("hora"))
        assertTrue(b.all { it == 0.toByte() })
    }

    // ---- Audit 1.3.0, F2 (BOV-2, BOV-3, LIBRE-1, COV2-2) ----

    /** An export of another vault (master "m") with one note per id in [ids]. */
    private suspend fun exportWithIds(vararg ids: String): String {
        val other = VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        other.load(); other.create("m".toCharArray())
        ids.forEach { other.upsert(entry(it, "Wallet")) }
        return other.exportText()!!
    }

    /**
     * BOV-2: a store with no vault whose import is cut by MainActivity.onStop (lock) in the middle, before the save: the
     * cost is read once the copy's password is checked (A8, inside replace). The import hook
     * (beforeReplace) writes the settings, so the lock is put where nothing is written yet.
     */
    private fun storeLockingDuringImport(): VaultStore {
        lateinit var s: VaultStore
        var lockOnce = false
        s = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock },
            kdf = { if (lockOnce) { lockOnce = false; s.lock(LockReason.LEFT_APP) }; KdfParams(1024, 1, 1) },
        )
        lockOnce = true
        return s
    }

    @Test fun `BOV-2 an import from Crea tu boveda cut by leaving the app shows the imported vault locked`() = runTest {
        val export = exportWithIds("1")
        val s = storeLockingDuringImport().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.importReplace(export, "m".toCharArray())
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun `BOV-2 the same with a sgbak copy`() = runTest {
        val (copy, _) = backupWith(KdfParams(1024, 1, 1))
        val s = storeLockingDuringImport().apply { load() }
        s.importReplace(copy.inputStream(), "m".toCharArray())
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun `BOV-2 creating a vault never sets a readable imported vault aside`() = runTest {
        val s = storeLockingDuringImport().apply { load() }
        s.importReplace(exportWithIds("1"), "m".toCharArray())
        try {
            s.create("nueva".toCharArray())
            fail()
        } catch (e: IllegalStateException) {
            // expected
        }
        assertTrue(tmp.root.listFiles()!!.none { it.name.startsWith("vault.bin.bad-") })
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    private fun mutexOf(s: VaultStore): kotlinx.coroutines.sync.Mutex =
        VaultStore::class.java.getDeclaredField("mutex").apply { isAccessible = true }.get(s) as kotlinx.coroutines.sync.Mutex

    @Test fun `BOV-3 a fingerprint key whose unlock is cancelled while waiting is wiped`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        val m = mutexOf(s)
        m.lock() // Another operation (a save, an import) holds the mutex.
        val job = launch { s.unlockWithKey(key) }
        testScheduler.runCurrent()
        job.cancel()
        testScheduler.runCurrent()
        m.unlock()
        assertTrue(key.all { it == 0.toByte() })
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `BOV-3 an unlock cancelled during Argon2id still finishes, so its key is never dropped`() = runTest {
        val compute = GateDispatcher()
        var resets = 0
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) }, onClockReset = { resets++ })
        s.load(); s.create("m".toCharArray()); s.lock()
        compute.open = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.unlock("m".toCharArray()) }
        job.cancel() // A rotation: the screen's scope goes while Argon2id runs.
        compute.release()
        job.join()
        // The key it derived is held by the open vault (wiped by the next lock), not left behind in the heap.
        assertTrue(s.state.value is VaultState.Unlocked)
        assertEquals(1, resets)
        val held = dekOf(s)!!
        s.lock()
        assertNull(s.dataKeyCopy())
        assertTrue(held.all { it == 0.toByte() })
    }

    private fun dekOf(s: VaultStore): ByteArray? = VaultStore::class.java.getDeclaredField("dek").apply { isAccessible = true }.get(s) as ByteArray?

    @Test fun `BOV-3 an unlock cancelled during Argon2id and then locked by leaving the app stays locked and wipes its key`() = runTest {
        val compute = GateDispatcher()
        var resets = 0
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) }, onClockReset = { resets++ })
        s.load(); s.create("m".toCharArray()); s.lock()
        resets = 0
        compute.open = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.unlock("m".toCharArray()) }
        job.cancel() // A rotation...
        s.lock(LockReason.LEFT_APP) // ...and then Home, while Argon2id still runs.
        compute.release()
        job.join()
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        assertNull(dekOf(s))
        assertEquals(1, resets) // The master password was right: the clock mark restarts once.
    }

    @Test fun `LIBRE-1 a sgbak of a vault holding an old odd id restores and opens`() = runTest {
        val old = store("old.bin").apply { load(); create("m".toCharArray()) }
        old.upsert(entry("a.b", "Wallet"))
        old.upsert(entry("ñ x", "Otra"))
        val copy = java.io.ByteArrayOutputStream().also { assertTrue(old.exportBackup(it)) }.toByteArray()
        val s = store().apply { load(); create("mia".toCharArray()) }
        s.importReplace(copy.inputStream(), "m".toCharArray())
        s.lock()
        s.unlock("m".toCharArray())
        assertEquals(setOf("a.b", "ñ x"), (s.state.value as VaultState.Unlocked).entries.map { it.id }.toSet())
    }

    @Test fun `BOV-3 a create cancelled during Argon2id still finishes`() = runTest {
        val compute = GateDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        compute.open = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.create("m".toCharArray()) }
        job.cancel()
        compute.release()
        job.join()
        assertTrue(s.state.value is VaultState.Unlocked)
        s.lock()
        s.unlock("m".toCharArray())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    @Test fun `BOV-3 a confirmMaster cancelled during Argon2id still upgrades`() = runTest {
        val compute = GateDispatcher()
        var resets = 0
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) }, onClockReset = { resets++ })
        s.load(); s.create("m".toCharArray())
        resets = 0
        compute.open = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.confirmMaster("m".toCharArray()) }
        job.cancel()
        compute.release()
        job.join()
        assertEquals(1, resets)
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    @Test fun `LIBRE-1 a copy with an id nothing could show is refused as damaged`() = runTest {
        for (bad in listOf("", "x".repeat(1025), "a\u0000b", "a\nb", "\t", "a\u007Fb", "a\u0085b")) {
            val s = store("v-${bad.hashCode()}.bin").apply { load(); create("mia".toCharArray()) }
            try {
                s.importReplace(exportWithIds("ok-1", bad), "m".toCharArray())
                fail("accepted id «$bad»")
            } catch (e: CryptoFailure) {
                assertEquals("La copia está dañada: no se puede importar", e.message)
            }
            assertEquals(0, (s.state.value as VaultState.Unlocked).entries.size)
        }
    }

    @Test fun `LIBRE-1 any other id 1_0 to 1_2_x let in still imports`() = runTest {
        val ids = arrayOf(
            java.util.UUID.randomUUID().toString(), "login-1", "totp_2", " ", "a.b", "ñ", "a/qr", "a/b/c", "%zz", "x?id=y",
            "x#y", "x&y", "me", "add", "encrypt", "sign", "open", "x".repeat(1024),
        )
        val s = store().apply { load(); create("mia".toCharArray()) }
        s.importReplace(exportWithIds(*ids), "m".toCharArray())
        assertEquals(ids.sorted(), (s.state.value as VaultState.Unlocked).entries.map { it.id }.sorted())
    }

    @Test fun `COV2-2 the idle lock counts real time, not the wall clock`() = runTest {
        var elapsed = 50_000L
        val s = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            idleLockMs = { 60_000L }, elapsed = { elapsed },
        ).apply { load(); create("m".toCharArray()) }
        clock -= 3_600_000 // The wall clock goes back an hour (by hand, NITZ or NTP).
        elapsed += 59_999
        assertFalse(s.lockIfIdle())
        elapsed += 1
        assertTrue(s.lockIfIdle())
    }

    @Test fun `COV2-2 the backstop of Al salir counts real time too`() = runTest {
        var elapsed = 0L
        val s = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            idleLockMs = { 0L }, keptOpen = { true }, elapsed = { elapsed },
        ).apply { load(); create("m".toCharArray()) }
        clock += 24 * 3_600_000L // A wall clock set forward a day: no real time has passed.
        assertFalse(s.lockIfIdle())
        clock -= 48 * 3_600_000L
        elapsed += AWAY_BACKSTOP_MS
        assertTrue(s.lockIfIdle())
    }

    @Test fun `COV2-3 Al salir locks 2 minutes after the last use while another app has the focus in multi-window`() = runTest {
        var elapsed = 0L
        var away = false
        val s = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            idleLockMs = { 0L }, keptOpen = { false }, awayInMultiWindow = { away }, elapsed = { elapsed },
        ).apply { load(); create("m".toCharArray()) }
        // Seguridad on screen and in use (split screen or not): «Al salir» never locks on time.
        elapsed += 10 * AWAY_BACKSTOP_MS
        assertFalse(s.lockIfIdle())
        s.touch()
        // The user moves to the other app of the split screen: Seguridad is still visible, onStop never comes.
        away = true
        elapsed += AWAY_BACKSTOP_MS - 1
        assertFalse(s.lockIfIdle())
        elapsed += 1
        assertTrue(s.lockIfIdle())
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `COV2-3 the idle limit`() {
        assertEquals(0L, idleLimitMs(chosen = 0, keptOpen = false, awayInMultiWindow = false))
        assertEquals(AWAY_BACKSTOP_MS, idleLimitMs(chosen = 0, keptOpen = true, awayInMultiWindow = false))
        assertEquals(AWAY_BACKSTOP_MS, idleLimitMs(chosen = 0, keptOpen = false, awayInMultiWindow = true))
        // A time chosen in Ajustes stays as it is, in or out of multi-window (it already counts the time without use).
        assertEquals(15 * 60_000L, idleLimitMs(chosen = 15 * 60_000L, keptOpen = false, awayInMultiWindow = true))
        assertEquals(60_000L, idleLimitMs(chosen = 60_000L, keptOpen = true, awayInMultiWindow = true))
    }

    @Test fun `LIBRE-4 a fingerprint unlock shows only its own messages`() {
        assertEquals("Contraseña incorrecta o datos dañados", biometricUnlockError(CryptoFailure()))
        assertEquals(CLOCK_CHANGED_TEXT, biometricUnlockError(MasterRequired(BiometricRefusal.CLOCK)))
        assertEquals(MasterRequired().message, biometricUnlockError(MasterRequired()))
        assertEquals(BIOMETRIC_UNLOCK_FAILED, biometricUnlockError(java.io.IOException("/data/user/0/app/files/vault: ENOSPC")))
        assertEquals(BIOMETRIC_UNLOCK_FAILED, biometricUnlockError(IllegalStateException()))
        assertEquals("No se pudo abrir la bóveda con la huella", BIOMETRIC_UNLOCK_FAILED)
    }

    @Test fun `BOV-3 a fingerprint key handed over from a scope already gone opens the vault`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        val gone = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job().apply { cancel() }) // The old composition's scope.
        val errors = mutableListOf<String>()
        gone.launchUnlockWithKey(s, key) { errors += it }.join()
        assertTrue(s.state.value is VaultState.Unlocked)
        assertTrue(key.contentEquals(s.dataKeyCopy()!!))
        assertTrue(errors.isEmpty())
    }

    @Test fun `BOV-3 a fingerprint key handed over from a scope already gone while the vault is busy is wiped`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        val m = mutexOf(s)
        m.lock()
        val gone = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job().apply { cancel() })
        val errors = mutableListOf<String>()
        gone.launchUnlockWithKey(s, key) { errors += it }.join()
        m.unlock()
        assertTrue(key.all { it == 0.toByte() })
        assertEquals(VaultState.Locked, s.state.value)
        assertTrue(errors.isEmpty()) // A cancellation is not an error to show.
    }

    @Test fun `BOV-3 a refused fingerprint key reports the store's message`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        s.lock()
        val errors = mutableListOf<String>()
        val bad = ByteArray(32) { 1 }
        launchUnlockWithKey(s, bad) { errors += it }.join()
        assertEquals(listOf("Contraseña incorrecta o datos dañados"), errors)
        assertTrue(bad.all { it == 0.toByte() })
    }
}
