package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
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
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * «Comprobar una copia» (1.4.0, task 5): [checkBackup] reads a `seguridad-backup` copy, opens it with its master password
 * and checks every attachment against its entry, with the same checks [VaultStore.importReplace] uses, and writes nothing
 * but its own temporary folder, deleted however it ends. Owner's decision (a): every size and count is bounded before the
 * work; (b): two runs never share a folder.
 */
class BackupCheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba"

    private fun phone(name: String) = File(tmp.root, name).apply { mkdirs() }

    private fun store(dir: File) =
        VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 7_000L }, kdf = { KdfParams(1024, 1, 1) })

    private fun note(id: String, title: String = "Nota $id") = VaultEntry(id, EntryType.NOTE, title, notes = "secreto", createdAt = 0, updatedAt = 0)

    private fun login(id: String) = VaultEntry(id, EntryType.LOGIN, "Banco", username = "ana", password = "clave-larga-1", createdAt = 0, updatedAt = 0)

    private fun VaultStore.entries() = (state.value as VaultState.Unlocked).entries

    private fun VaultStore.data() = (state.value as VaultState.Unlocked).data

    private fun bytes(n: Int, seed: Int = n) = Random(seed).nextBytes(n)

    /** A vault with two notes and a login, and three attachments (one note with two). */
    private suspend fun phoneA(): VaultStore = store(phone("a")).apply {
        load()
        create(master.toCharArray())
        upsert(note("1", "Pasaporte"))
        upsert(note("2", "Contrato"))
        upsert(login("3"))
        assertTrue(addAttachment("1", ByteArrayInputStream(bytes(70_000, 1)), "pasaporte.jpg"))
        assertTrue(addAttachment("1", ByteArrayInputStream(bytes(10, 2)), "reverso.jpg"))
        assertTrue(addAttachment("2", ByteArrayInputStream(bytes(0, 3)), "vacío.txt"))
    }

    private suspend fun VaultStore.backup(): ByteArray = ByteArrayOutputStream().also { assertTrue(exportBackup(it)) }.toByteArray()

    private fun entriesOf(zip: ByteArray): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out += e.name to z.readBytes()
            }
        }
        return out
    }

    private fun zipOf(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** The local entries of [entries] as a ZIP writes them, without the central directory (a stream goes on after them). */
    private fun localEntriesOf(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        val z = ZipOutputStream(bos)
        for ((name, data) in entries) {
            z.putNextEntry(ZipEntry(name))
            z.write(data)
            z.closeEntry()
        }
        z.flush()
        return bos.toByteArray()
    }

    /** The copy of [store]'s vault rewritten with [change] to its entries, under its own master password (as its owner could). */
    private fun rewritten(zip: ByteArray, change: (List<VaultEntry>) -> List<VaultEntry>): ByteArray {
        val parts = entriesOf(zip)
        val file = VaultCrypto.decodeFile(parts.single { it.first == BACKUP_VAULT }.second.decodeToString())
        val dek = VaultCrypto.unlock(file, master.toCharArray())
        val json = VaultCrypto.encodeFile(VaultCrypto.write(file, dek, VaultData(entries = change(VaultCrypto.read(file, dek).entries)))).toByteArray()
        return zipOf(parts.map { if (it.first == BACKUP_VAULT) BACKUP_VAULT to json else it })
    }

    private suspend fun check(zip: ByteArray, temp: File, pw: String = master) = checkBackup(ByteArrayInputStream(zip), pw.toCharArray(), temp)

    private suspend fun checkFails(zip: ByteArray, temp: File, pw: String = master): String {
        try {
            check(zip, temp, pw)
        } catch (e: CryptoFailure) {
            assertTrue("No deja nada en la carpeta temporal", temp.listFiles().isNullOrEmpty())
            return e.message.orEmpty()
        }
        fail("Debía fallar")
        error("unreachable")
    }

    private suspend fun importFails(zip: ByteArray, pw: String = master): String {
        val b = store(phone("b-" + Random.nextInt(1_000_000))).apply { load() }
        try {
            b.importReplace(ByteArrayInputStream(zip), pw.toCharArray())
        } catch (e: CryptoFailure) {
            return e.message.orEmpty()
        }
        fail("La importación debía fallar")
        error("unreachable")
    }

    private fun temp() = File(phone("cache"), "comprobar")

    private fun snapshot(dir: File): Map<String, List<Byte>> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to it.readBytes().toList() }

    @Test fun goodBackupReports() = runTest {
        val a = phoneA()
        val temp = temp()
        val report = check(a.backup(), temp)
        // 2.0: marker 3; task 8: with the vault's tag.
        assertEquals(BackupCheckReport(3, mapOf(EntryType.NOTE to 2, EntryType.LOGIN to 1), 3, 3, emptyList(), a.data().vaultTag), report)
        assertTrue(temp.listFiles().isNullOrEmpty())
        // A copy of version 1 (1.1.x) is read the same way, and says so.
        val v1 = zipOf(listOf(BACKUP_MARKER to "1\n".toByteArray()) + entriesOf(a.backup()).drop(1))
        assertEquals(1, check(v1, temp).version)
    }

    @Test fun changedAttachmentIsReported() = runTest {
        val a = phoneA()
        val ref = a.entries().single { it.id == "1" }.attachments[0]
        val name = "att/${ref.id}.bin"
        val original = entriesOf(a.backup())
        val flipped = original.map { (n, d) -> n to if (n == name) d.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() } else d }
        val cut = original.map { (n, d) -> n to if (n == name) d.copyOf(d.size - 1) else d }
        for (bad in listOf(flipped, cut)) {
            val report = check(zipOf(bad), temp())
            assertEquals(3, report.attachments)
            assertEquals(2, report.attachmentsOk)
            assertEquals(listOf(backupCheckChanged(1)), report.problems)
            // Counts and problems only: no entry title nor attachment name.
            for (p in report.problems) for (secret in listOf("pasaporte", "Pasaporte", "reverso", "Banco", "ana")) assertFalse(p, secret in p)
        }
    }

    @Test fun missingAttachmentIsReported() = runTest {
        val a = phoneA()
        val byName = a.entries().flatMap { it.attachments }.associateBy { it.name }
        val refs = listOf("pasaporte.jpg", "reverso.jpg", "vacío.txt").map { byName.getValue(it) }
        val zip = a.backup()
        val oneGone = zipOf(entriesOf(zip).filterNot { it.first == "att/${refs[1].id}.bin" })
        val report = check(oneGone, temp())
        assertEquals(BackupCheckReport(3, mapOf(EntryType.NOTE to 2, EntryType.LOGIN to 1), 3, 2, listOf(backupCheckMissing(1)), a.data().vaultTag), report)
        // Several of each, counted apart.
        val ref0 = "att/${refs[0].id}.bin"
        val mixed = zipOf(
            entriesOf(zip).filterNot { it.first == "att/${refs[1].id}.bin" || it.first == "att/${refs[2].id}.bin" }
                .map { (n, d) -> n to if (n == ref0) d.copyOf(5) else d },
        )
        assertEquals(listOf(backupCheckMissing(2), backupCheckChanged(1)), check(mixed, temp()).problems)
        assertEquals(0, check(mixed, temp()).attachmentsOk)
        // The import refuses the same copies, with its message for the first attachment.
        assertEquals("La copia está incompleta o dañada: falta o no coincide el adjunto «reverso.jpg»", importFails(oneGone))
        assertTrue(temp().listFiles().isNullOrEmpty())
    }

    @Test fun wrongPasswordThrows() = runTest {
        val zip = phoneA().backup()
        assertEquals("Contraseña incorrecta o datos dañados", checkFails(zip, temp(), pw = "contraseña-mala"))
        assertEquals(importFails(zip, pw = "contraseña-mala"), checkFails(zip, temp(), pw = "contraseña-mala"))
    }

    @Test fun newerVersionThrowsNewerMessage() = runTest {
        val good = entriesOf(phoneA().backup())
        val newer = zipOf(listOf(BACKUP_MARKER to "4\n".toByteArray()) + good.drop(1))
        assertEquals(BACKUP_NEWER, checkFails(newer, temp()))
        assertEquals(importFails(newer), checkFails(newer, temp()))
        // A vault file of a newer version inside a copy: what the import says too.
        val json = good[1].second.decodeToString().replace("\"version\":4", "\"version\":5")
        val newerVault = zipOf(good.map { if (it.first == BACKUP_VAULT) BACKUP_VAULT to json.toByteArray() else it })
        assertEquals(NOT_A_VAULT, checkFails(newerVault, temp()))
        assertEquals(importFails(newerVault), checkFails(newerVault, temp()))
    }

    @Test fun theChecksAreTheImportsOwn() = runTest {
        val zip = phoneA().backup()
        val identity = { id: String -> VaultEntry(id, EntryType.IDENTITY, "Yo", createdAt = 0, updatedAt = 0) }
        val cases = listOf(
            // More than one identity.
            rewritten(zip) { it + identity("y1") + identity("y2") },
            // Two entries with the same id.
            rewritten(zip) { it + it.first() },
            // An id no screen could route.
            rewritten(zip) { it + note("") },
            // An attachment declared over 5 MB.
            rewritten(zip) { list -> list.map { e -> e.copy(attachments = e.attachments.map { it.copy(size = 6L * 1024 * 1024) }) } },
            // A cut copy, and one with no vault inside.
            zip.copyOf(zip.size / 2),
            zipOf(listOf(BACKUP_MARKER to "2\n".toByteArray())),
        )
        for (bad in cases) {
            val expected = importFails(bad)
            assertEquals(expected, checkFails(bad, temp()))
        }
    }

    @Test fun tempFilesAreDeleted() = runTest {
        val a = phoneA()
        val zip = a.backup()
        val temp = temp()
        check(zip, temp)
        assertTrue(temp.listFiles().isNullOrEmpty())
        checkFails(zip, temp, pw = "contraseña-mala")
        checkFails(zipOf(listOf(BACKUP_MARKER to "4\n".toByteArray()) + entriesOf(zip).drop(1)), temp)
        checkFails(zip.copyOf(zip.size / 2), temp)
        checkFails(rewritten(zip) { it + it.first() }, temp)
        // A problem in the report is no failure, and leaves nothing either.
        check(zipOf(entriesOf(zip).dropLast(1)), temp)
        assertTrue(temp.listFiles().isNullOrEmpty())
        // A read error of the file (an IOException) too.
        val failing = object : InputStream() {
            private val inner = ByteArrayInputStream(zip)
            private var n = 0
            override fun read(): Int = if (++n > 20_000) throw java.io.IOException("se cortó") else inner.read()
        }
        try {
            checkBackup(failing, master.toCharArray(), temp)
            fail("Debía fallar")
        } catch (e: java.io.IOException) {
            // expected
        }
        assertTrue(temp.listFiles().isNullOrEmpty())
    }

    @Test fun cancelLeavesNoTemp() = runBlocking {
        val zip = phoneA().backup()
        val temp = temp()
        val reading = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) {
            // Cancelled while the attachments are being written to the temporary folder.
            val slow = object : InputStream() {
                private val inner = ByteArrayInputStream(zip)
                override fun read(): Int = inner.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (inner.available() < zip.size / 2) {
                        reading.complete(Unit)
                        Thread.sleep(20)
                    }
                    return inner.read(b, off, minOf(len, 512))
                }
            }
            checkBackup(slow, master.toCharArray(), temp)
            fail("Debía cancelarse")
        }
        reading.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(temp.listFiles().isNullOrEmpty())
    }

    /** An Argon2id that tells when it starts and waits for [release] before running (it cannot be cut, as the real one). */
    private inner class SlowUnlock(private val release: java.util.concurrent.CountDownLatch? = null) : (VaultFile, CharArray) -> ByteArray {
        val events: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf<String>())
        val keys: MutableList<ByteArray> = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
        val started = CompletableDeferred<Unit>()
        private val n = java.util.concurrent.atomic.AtomicInteger()

        override fun invoke(file: VaultFile, pw: CharArray): ByteArray {
            val i = n.incrementAndGet()
            events += "empieza $i"
            started.complete(Unit)
            if (i == 1) release?.await()
            // The real derivation, on a copy of the password: the caller may zero its own meanwhile (BackupCheckRuns.stop).
            val key = VaultCrypto.unlock(file, master.toCharArray())
            keys += key
            events += "acaba $i"
            return key
        }
    }

    private fun zeroed(b: ByteArray) = b.all { it == 0.toByte() }

    @Test fun cancelDuringTheDerivationZeroesTheKey() = runBlocking {
        val zip = phoneA().backup()
        val temp = temp()
        val release = java.util.concurrent.CountDownLatch(1)
        val unlock = SlowUnlock(release)
        val job = launch(Dispatchers.Default) { checkBackup(ByteArrayInputStream(zip), master.toCharArray(), temp, unlock = unlock) }
        unlock.started.await()
        job.cancel() // «Cancelar», leaving the app or a lock while Argon2id runs.
        release.countDown()
        job.join()
        assertTrue(job.isCancelled)
        // The derivation ended and gave the copy's data key: it was zeroed although its result was dropped.
        assertEquals(listOf("empieza 1", "acaba 1"), unlock.events)
        assertTrue(zeroed(unlock.keys.single()))
        assertTrue(temp.listFiles().isNullOrEmpty())
        // And after a check that ends well, too.
        val ok = SlowUnlock()
        assertEquals(3, checkBackup(ByteArrayInputStream(zip), master.toCharArray(), temp, unlock = ok).attachmentsOk)
        assertTrue(zeroed(ok.keys.single()))
    }

    @Test fun afterCancelTheNextDerivationWaits() = runBlocking {
        // The screen's own path: «Comprobar», «Cancelar» during Argon2id, «Comprobar» again.
        val zip = phoneA().backup()
        val temp = temp()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
        val session = BackupCheckSession()
        val runs = BackupCheckRuns(session, scope)
        val release = java.util.concurrent.CountDownLatch(1)
        val unlock = SlowUnlock(release)
        val first = master.toCharArray()
        val second = master.toCharArray()
        runs.start(first) { checkBackup(ByteArrayInputStream(zip), it, temp, unlock = unlock) }
        unlock.started.await()
        runs.stop()
        assertTrue(first.all { it == '\u0000' }) // Zeroed at once by «Cancelar».
        runs.start(second) { checkBackup(ByteArrayInputStream(zip), it, temp, unlock = unlock) }
        kotlinx.coroutines.delay(300)
        assertEquals("La segunda espera a que acabe la primera", listOf("empieza 1"), unlock.events)
        release.countDown()
        kotlinx.coroutines.withTimeout(10_000) { while (session.report == null) kotlinx.coroutines.delay(10) }
        assertEquals(listOf("empieza 1", "acaba 1", "empieza 2", "acaba 2"), unlock.events)
        assertEquals(3, session.report!!.attachmentsOk)
        kotlinx.coroutines.withTimeout(10_000) { while (!temp.listFiles().isNullOrEmpty()) kotlinx.coroutines.delay(10) }
        assertTrue(unlock.keys.all { zeroed(it) })
        assertTrue(second.all { it == '\u0000' })
        scope.cancel()
    }

    /**
     * Ruling R38 (branch review B, M-4): a check stopped during its Argon2id keeps the process-wide derivation lock until
     * that derivation ends, so the unlock with the master that comes next waits for it instead of running beside it (two
     * «Alto» at once would run out of heap and say, falsely, that the phone has no memory for «Alto»).
     */
    @Test fun aCancelledCheckMakesTheUnlockWait() = runBlocking {
        val a = phoneA()
        val zip = a.backup()
        a.lock()
        val temp = temp()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
        val release = java.util.concurrent.CountDownLatch(1)
        val unlock = SlowUnlock(release)
        val check = scope.launch { checkBackup(ByteArrayInputStream(zip), master.toCharArray(), temp, unlock = unlock) }
        val opened = try {
            unlock.started.await()
            check.cancel()
            val opening = scope.async { a.unlock(master.toCharArray()) }
            kotlinx.coroutines.delay(300)
            assertEquals("The unlock waits for the cancelled check's derivation", listOf("empieza 1"), unlock.events)
            assertTrue(a.state.value is VaultState.Locked)
            assertFalse(opening.isCompleted)
            opening
        } finally {
            release.countDown() // Never left held: the lock is the whole process's.
        }
        kotlinx.coroutines.withTimeout(10_000) { opened.await() }
        assertEquals(listOf("empieza 1", "acaba 1"), unlock.events)
        assertTrue(a.state.value is VaultState.Unlocked)
        check.join()
        assertTrue(unlock.keys.all { zeroed(it) })
        scope.cancel()
    }

    @Test fun cancelWhileTheFolderIsMadeLeavesNothing() = runBlocking {
        val zip = phoneA().backup()
        val temp = temp()
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            lateinit var job: kotlinx.coroutines.Job
            var dispatched = 0
            // The check and its IO share one thread. The first IO block is the one that makes the run's folder: the check
            // is cancelled right after it ran and before the check goes on with its result (queued behind on that thread).
            val io = object : kotlinx.coroutines.CoroutineDispatcher() {
                override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                    val first = dispatched++ == 0
                    pool.execute {
                        block.run()
                        if (first) job.cancel()
                    }
                }
            }
            job = launch(pool.asCoroutineDispatcher(), start = kotlinx.coroutines.CoroutineStart.LAZY) {
                checkBackup(ByteArrayInputStream(zip), master.toCharArray(), temp, io = io)
            }
            job.start()
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(dispatched >= 1)
            assertTrue("Ninguna carpeta run-… queda", temp.listFiles().isNullOrEmpty())
            // Nor is it kept as «in use»: a later clean-up would delete anything left, and nothing is.
            File(temp, "run-1").mkdirs()
            removeBackupCheckLeftovers(temp)
            assertTrue(temp.listFiles().isNullOrEmpty())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun twoChecksAtOnceDoNotInterfere() = runBlocking {
        val a = phoneA()
        val zip = a.backup()
        val bad = zipOf(entriesOf(zip).dropLast(1))
        val temp = temp()
        repeat(3) {
            val reports = (0 until 6).map { i ->
                async(Dispatchers.IO) { checkBackup(ByteArrayInputStream(if (i % 2 == 0) zip else bad), master.toCharArray(), temp) }
            }.awaitAll()
            reports.forEachIndexed { i, r -> assertEquals(if (i % 2 == 0) 3 else 2, r.attachmentsOk) }
        }
        assertTrue(temp.listFiles().isNullOrEmpty())
    }

    @Test fun leftoversGoButNotARunGoingOn() = runBlocking {
        val zip = phoneA().backup()
        val temp = temp().apply { mkdirs() }
        // What a run killed with the process leaves.
        File(temp, "run-123").apply { mkdirs() }.resolve("0123456789abcdef0123456789abcdef.bin").writeBytes(bytes(10))
        File(temp, "suelto").writeBytes(bytes(3))
        var cleanedDuringRun = false
        val sneaky = object : InputStream() {
            private val inner = ByteArrayInputStream(zip)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!cleanedDuringRun && inner.available() < zip.size / 2) {
                    cleanedDuringRun = true
                    removeBackupCheckLeftovers(temp) // Mid-run: its own folder must stay.
                }
                return inner.read(b, off, minOf(len, 4096))
            }
        }
        val report = withContext(Dispatchers.IO) { checkBackup(sneaky, master.toCharArray(), temp) }
        assertTrue(cleanedDuringRun)
        assertEquals(3, report.attachmentsOk)
        assertTrue(temp.listFiles().isNullOrEmpty())
        removeBackupCheckLeftovers(File(tmp.root, "no-existe")) // Never throws.
    }

    @Test fun vaultIsUntouched() = runTest {
        val a = phoneA()
        val zip = a.backup()
        val revision = a.revision.value
        val generation = a.generation
        val before = snapshot(phone("a"))
        check(zip, temp())
        check(zipOf(entriesOf(zip).dropLast(1)), temp())
        checkFails(zip, temp(), pw = "contraseña-mala")
        assertEquals(revision, a.revision.value)
        assertEquals(generation, a.generation)
        assertEquals(before, snapshot(phone("a")))
        assertTrue(a.state.value is VaultState.Unlocked)
    }

    @Test fun aHugeFileIsRefusedAfterItsFirstBytes() = runTest {
        // «200 GB» of noise: refused at the first header, never read to the end.
        var given = 0L
        val noise = object : InputStream() {
            private val r = Random(5)
            override fun read(): Int {
                given++
                return r.nextInt(256)
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = minOf(len, 8192)
                for (i in off until off + n) b[i] = r.nextInt(256).toByte()
                given += n
                return n
            }
        }
        try {
            checkBackup(noise, master.toCharArray(), temp())
            fail("Debía fallar")
        } catch (e: CryptoFailure) {
            assertEquals(BACKUP_DAMAGED, e.message)
        }
        assertTrue("Leídos: $given", given <= 64 * 1024)
        assertTrue(temp().listFiles().isNullOrEmpty())
    }

    @Test fun aForgedSizeIsRefusedBeforeTheWork() = runTest {
        val good = entriesOf(phoneA().backup())
        // A local header that says 4 GB but brings a few bytes: the sizes in headers are never used to allocate.
        val head = localEntriesOf(good.take(2))
        val name = "att/" + "e".repeat(32) + ".bin"
        val forged = java.nio.ByteBuffer.allocate(30 + name.length).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x04034b50); putShort(20); putShort(0); putShort(0) // Stored, no data descriptor.
            putShort(0); putShort(0); putInt(0)
            putInt(-1); putInt(-1) // 0xFFFFFFFF compressed and uncompressed.
            putShort(name.length.toShort()); putShort(0)
            put(name.toByteArray())
        }.array()
        val message = checkFails(head + forged + bytes(100), temp(), pw = "contraseña-mala")
        assertEquals(BACKUP_DAMAGED, message)

        // An entry that never ends: empty deflate blocks give no bytes out, so only the bytes read in can stop it.
        val endless = head + java.nio.ByteBuffer.allocate(30 + 7).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x04034b50); putShort(20); putShort(8); putShort(8) // Deflated, sizes in a data descriptor.
            putShort(0); putShort(0); putInt(0); putInt(0); putInt(0)
            putShort(7); putShort(0)
            put("relleno".toByteArray())
        }.array()
        var given = 0L
        val stream = object : InputStream() {
            private val block = byteArrayOf(0, 0, 0, -1, -1) // A stored block, not final, of length 0.
            private var i = 0L
            override fun read(): Int {
                val b = if (i < endless.size) endless[i.toInt()] else block[((i - endless.size) % 5).toInt()]
                i++
                given++
                return b.toInt() and 0xff
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                for (k in off until off + len) b[k] = read().toByte()
                return len
            }
        }
        try {
            // The wrong password is never tried: the size refuses the copy first.
            checkBackup(stream, "contraseña-mala".toCharArray(), temp())
            fail("Debía fallar")
        } catch (e: CryptoFailure) {
            assertEquals(BACKUP_TOO_BIG, e.message)
        }
        assertTrue("Leídos: $given", given in BACKUP_MAX_READ_BYTES..BACKUP_MAX_READ_BYTES + 64 * 1024)
        assertTrue(temp().listFiles().isNullOrEmpty())

        // Thousands of attachments: the count is bounded too.
        val many = good + (0 until BACKUP_MAX_ENTRIES).map { "att/" + "%032x".format(it) + ".bin" to ByteArray(0) }
        assertEquals(BACKUP_TOO_MANY, checkFails(zipOf(many), temp()))
    }

    @Test fun aCopyAtTheLimitsIsStillRead() {
        // The most a real copy can weigh beyond its content: as many attachments as fit, each of encrypted (random)
        // bytes, 100 MB in all. The limit on the bytes read in leaves it room.
        val dir = phone("limits")
        val count = BACKUP_MAX_ENTRIES - 2
        val each = ((BACKUP_MAX_TOTAL_BYTES - 1024) / count).toInt()
        val files = (0 until count).associate { i -> "%032x".format(i) to File(dir, "$i").apply { writeBytes(bytes(each, i)) } }
        val out = File(dir, "copia.sgbak")
        out.outputStream().use { writeBackup(it, "{}", files) }
        assertTrue("Tamaño: ${out.length()}", out.length() < BACKUP_MAX_READ_BYTES)
        val contents = out.inputStream().buffered().use { BackupReader().read(it, File(dir, "t")) }
        assertEquals(count, contents.attachments.size)
        assertArrayEquals(bytes(each, 7), contents.attachments.getValue("%032x".format(7)).readBytes())
    }

    @Test fun theReaderStopsWhenAskedTo() {
        val zip = runBlocking { phoneA().backup() }
        val temp = File(phone("work"), "t")
        var calls = 0
        try {
            BackupReader().read(ByteArrayInputStream(zip), temp) { if (++calls > 3) throw CancellationException("parar") }
            fail("Debía pararse")
        } catch (e: CancellationException) {
            // The cancellation goes through as it is, never as a damaged copy.
        }
        assertTrue(temp.listFiles().isNullOrEmpty())
    }

    /**
     * 2.0, task 8 (design «Copias», ruling (b)): the date of the check is kept only for a copy whose tag, read from the very
     * data checked, is the open vault's; never for another vault's copy, one with no tag (made before 2.0), or after a lock.
     */
    @Test fun checkRecordsDateOnlyForOwnTag() = runTest {
        val a = phoneA()
        val b = store(phone("b")).apply {
            load()
            create(master.toCharArray())
            upsert(note("9"))
        }
        val own = check(a.backup(), temp())
        val other = check(b.backup(), temp())
        assertTrue(own.vaultTag.isNotEmpty())
        assertEquals(a.data().vaultTag, own.vaultTag)
        assertTrue(own.vaultTag != other.vaultTag)
        val revision = a.revision.value

        // Another vault's copy, and one with no tag (before 2.0): nothing recorded.
        assertFalse(a.recordBackupChecked(other.vaultTag))
        assertFalse(a.recordBackupChecked(""))
        assertEquals(0L, a.data().lastCheckedBackupAt)
        assertEquals(null, a.data().withBackupChecked("", 5L))
        assertEquals(null, a.data().withBackupChecked(other.vaultTag, 5L))

        // Its own copy: the date is kept, saved (it is there after a lock and a new unlock) and makes no automatic copy.
        assertTrue(a.recordBackupChecked(own.vaultTag))
        assertEquals(7_000L, a.data().lastCheckedBackupAt)
        assertEquals(revision, a.revision.value)
        a.lock()
        a.unlock(master.toCharArray())
        assertEquals(7_000L, a.data().lastCheckedBackupAt)

        // A lock after the check started drops it, even with the vault open again.
        val b2 = store(phone("c")).apply {
            load()
            create(master.toCharArray())
        }
        val started = b2.lockEpoch
        val tag = b2.data().vaultTag
        b2.lock()
        b2.unlock(master.toCharArray())
        assertFalse(b2.recordBackupChecked(tag, started))
        assertEquals(0L, b2.data().lastCheckedBackupAt)
        assertTrue(b2.recordBackupChecked(tag, b2.lockEpoch))
    }

    /** 2.0, task 8: the reminder, only after 30 days, or while no copy was ever checked (0). */
    @Test fun noticeAfter30Days() {
        val day = 24L * 60 * 60 * 1000
        val now = 1_800_000_000_000L
        assertEquals("Aún no has comprobado ninguna copia.", backupCheckNotice(0L, now))
        assertEquals(null, backupCheckNotice(now, now))
        assertEquals(null, backupCheckNotice(now - 29 * day, now))
        assertEquals(null, backupCheckNotice(now - 30 * day + 1, now))
        assertEquals("Tu última copia comprobada es de hace 30 días.", backupCheckNotice(now - 30 * day, now))
        assertEquals("Tu última copia comprobada es de hace 45 días.", backupCheckNotice(now - 45 * day - 5, now))
        // A clock set back behind the check: no reminder (it is not 30 days old).
        assertEquals(null, backupCheckNotice(now + 3 * day, now))
    }
}
