package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class AttachmentsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val attDir get() = File(tmp.root, "att")
    private fun attachments() = AttachmentStore(attDir)
    private fun fileOf(ref: AttachmentRef) = File(attDir, "${ref.id}.bin")
    private fun bytes(n: Int, seed: Int = n) = Random(seed).nextBytes(n)
    private fun hexOf(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun store() =
        VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 5_000L }, kdf = { KdfParams(1024, 1, 1) })

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "secreto", createdAt = 0, updatedAt = 0)

    private fun VaultStore.entry(id: String) = (state.value as VaultState.Unlocked).entries.single { it.id == id }

    private fun assertDamaged(block: () -> Unit) {
        try {
            block()
            fail("Debía fallar")
        } catch (e: CryptoFailure) {
            assertEquals("El adjunto está dañado o se ha cambiado", e.message)
        }
    }

    @Test fun roundTrip() {
        val a = attachments()
        val data = bytes(100_000)
        val ref = a.add(ByteArrayInputStream(data), "../carpeta/foto.jpg")
        assertArrayEquals(data, a.open(ref))
        assertEquals("foto.jpg", ref.name)
        assertEquals(100_000L, ref.size)
        // Empty attachments work too.
        val empty = a.add(ByteArrayInputStream(ByteArray(0)), "vacío.txt")
        assertEquals(0, a.open(empty).size)
        // Each attachment has its own id and key.
        val again = a.add(ByteArrayInputStream(data), "foto.jpg")
        assertFalse(again.id == ref.id)
        assertFalse(again.key == ref.key)
    }

    @Test fun tamperedCiphertextFails() {
        val a = attachments()
        val ref = a.add(ByteArrayInputStream(bytes(1000)), "doc.pdf")
        val original = fileOf(ref).readBytes()
        for (i in listOf(0, 11, 12, 500, original.size - 1)) {
            val changed = original.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            fileOf(ref).writeBytes(changed)
            assertDamaged { a.open(ref) }
            // Even with a matching hash, the GCM tag catches it.
            val sha = hexOf(MessageDigest.getInstance("SHA-256").digest(changed))
            assertDamaged { a.open(ref.copy(sha256 = sha)) }
        }
        fileOf(ref).writeBytes(original.copyOf(original.size - 1)) // Truncated.
        assertDamaged { a.open(ref) }
        fileOf(ref).writeBytes(original)
        assertDamaged { a.open(ref.copy(sha256 = "00" + ref.sha256.drop(2))) }
        assertDamaged { a.open(ref.copy(sha256 = "no-hex")) }
        assertDamaged { a.open(ref.copy(key = Base64.getEncoder().encodeToString(ByteArray(32)))) }
        assertDamaged { a.open(ref.copy(key = "%%%")) }
        assertDamaged { a.open(ref.copy(id = "../vault")) }
        fileOf(ref).delete()
        assertDamaged { a.open(ref) }
    }

    @Test fun swappedFilesBetweenIdsFail() {
        val a = attachments()
        val x = a.add(ByteArrayInputStream(bytes(500, 1)), "x.txt")
        val y = a.add(ByteArrayInputStream(bytes(500, 2)), "y.txt")
        // y's file under x's id, with everything else of y: only the id in the AAD differs.
        fileOf(y).copyTo(fileOf(x), overwrite = true)
        assertDamaged { a.open(x.copy(sha256 = y.sha256, key = y.key, size = y.size)) }
        assertDamaged { a.open(x) }
    }

    @Test fun over5MbRejected() {
        val a = attachments()
        val max = 5 * 1024 * 1024
        assertEquals(max.toLong(), a.add(ByteArrayInputStream(ByteArray(max)), "justo.bin").size)
        try {
            a.add(ByteArrayInputStream(ByteArray(max + 1)), "grande.bin")
            fail("Debía rechazarlo")
        } catch (e: AttachmentLimit) {
            assertEquals("El adjunto pasa de 5 MB", e.message)
        }
        assertEquals(1, attDir.listFiles()!!.size) // Nothing written for the rejected one.
    }

    @Test fun total50MbEnforcedByStore() = runTest {
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("a"))
        s.upsert(note("b"))
        val five = 5 * 1024 * 1024
        repeat(5) { assertTrue(s.addAttachment("a", ByteArrayInputStream(ByteArray(five)), "a$it.bin")) }
        repeat(5) { assertTrue(s.addAttachment("b", ByteArrayInputStream(ByteArray(five)), "b$it.bin")) }
        try {
            s.addAttachment("b", ByteArrayInputStream(ByteArray(1)), "uno-más.bin")
            fail("Debía rechazarlo")
        } catch (e: AttachmentLimit) {
            assertEquals("Los adjuntos de la bóveda pasan de 50 MB en total", e.message)
        }
        assertEquals(5, s.entry("b").attachments.size)
        assertEquals(10, attDir.listFiles()!!.size) // The rejected file is not left behind.
        assertEquals(50L * 1024 * 1024, attachments().totalBytes(s.entry("a").attachments + s.entry("b").attachments))
        // Removing one makes room again.
        assertTrue(s.removeAttachment("b", s.entry("b").attachments.first().id))
        assertTrue(s.addAttachment("b", ByteArrayInputStream(ByteArray(1)), "uno-más.bin"))
    }

    @Test fun garbageCollectionKeepsLiveRemovesDead() = runTest {
        val a = attachments()
        val live = a.add(ByteArrayInputStream(bytes(10)), "vivo")
        val dead = a.add(ByteArrayInputStream(bytes(10)), "muerto")
        val other = File(attDir, "otro.txt").apply { writeText("no es nuestro") }
        val leftover = File(attDir, "${"ab".repeat(16)}.bin.tmp").apply { writeText("a medias") }
        a.collectGarbage(setOf(live.id))
        assertTrue(fileOf(live).exists())
        assertFalse(fileOf(dead).exists())
        assertFalse(leftover.exists())
        assertTrue(other.exists())

        // Through the store: removing one attachment deletes only its file.
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("n"))
        s.addAttachment("n", ByteArrayInputStream(bytes(20)), "uno")
        s.addAttachment("n", ByteArrayInputStream(bytes(30)), "dos")
        val (one, two) = s.entry("n").attachments
        assertTrue(s.removeAttachment("n", one.id))
        assertFalse(fileOf(one).exists())
        assertTrue(fileOf(two).exists())
        assertEquals(listOf(two), s.entry("n").attachments)
        // Same folder as the standalone store above: its file is not referenced by this vault, so a save collects it.
        assertFalse(fileOf(live).exists())

        // An orphan left on disk (say, a crash) goes on the next unlock, the live one stays.
        val orphan = File(attDir, "${"cd".repeat(16)}.bin").apply { writeBytes(bytes(40)) }
        s.lock()
        store().apply { load(); unlock("maestra-larga".toCharArray()) }
        assertFalse(orphan.exists())
        assertTrue(fileOf(two).exists())
    }

    @Test fun deleteEntryRemovesItsFiles() = runTest {
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("a"))
        s.upsert(note("b"))
        s.addAttachment("a", ByteArrayInputStream(bytes(10)), "a")
        s.addAttachment("b", ByteArrayInputStream(bytes(10)), "b")
        val fa = fileOf(s.entry("a").attachments.single())
        val fb = fileOf(s.entry("b").attachments.single())
        s.delete("a")
        assertFalse(fa.exists())
        assertTrue(fb.exists())
        assertArrayEquals(bytes(10), s.openAttachment("b", s.entry("b").attachments.single().id))
    }

    @Test fun formatPinned() {
        val a = attachments()
        val data = bytes(777)
        val ref = a.add(ByteArrayInputStream(data), "x")
        val file = fileOf(ref).readBytes()
        assertTrue(ref.id.matches(Regex("[0-9a-f]{32}")))
        assertEquals(12 + 777 + 16, file.size)
        assertEquals(hexOf(MessageDigest.getInstance("SHA-256").digest(file)), ref.sha256)
        // nonce(12) ‖ AES-256-GCM(key, nonce, AAD = "seguridad-attachment-v1\u0000" + id, content), read with javax.crypto alone.
        val key = Base64.getDecoder().decode(ref.key)
        assertEquals(32, key.size)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, file.copyOfRange(0, 12)))
            updateAAD("seguridad-attachment-v1\u0000${ref.id}".toByteArray(Charsets.UTF_8))
            doFinal(file, 12, file.size - 12)
        }
        assertArrayEquals(data, plain)
    }

    @Test fun upsertKeepsAttachments() = runTest {
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("n"))
        s.addAttachment("n", ByteArrayInputStream(bytes(10)), "a")
        val ref = s.entry("n").attachments.single()
        // The editor builds the entry without attachments: the store keeps the stored ones and their file.
        s.upsert(note("n").copy(title = "Cambiada"))
        assertEquals(listOf(ref), s.entry("n").attachments)
        assertTrue(fileOf(ref).exists())
        // A new entry never brings attachments of its own.
        s.upsert(note("m").copy(attachments = listOf(ref)))
        assertTrue(s.entry("m").attachments.isEmpty())
    }

    @Test fun oldEntriesDecodeAndNoAttachmentsAreNotWritten() {
        val old = """{"id":"x","type":"NOTE","title":"t","createdAt":1,"updatedAt":2}"""
        assertTrue(appJson.decodeFromString<VaultEntry>(old).attachments.isEmpty())
        assertFalse(appJson.encodeToString(note("x")).contains("attachments"))
    }

    @Test fun lockedStoreDoesNothing() = runTest {
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("n"))
        s.lock()
        assertFalse(s.addAttachment("n", ByteArrayInputStream(bytes(10)), "a"))
        assertTrue(attDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun brokenAttachmentFolderNeverBreaksTheVault() = runTest {
        attDir.writeText("un archivo donde iría la carpeta")
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("n"))
        s.lock()
        store().apply { load(); unlock("maestra-larga".toCharArray()) }.also { assertEquals("Nota n", it.entry("n").title) }
    }

    @Test fun damagedVaultKeepsItsAttachmentsAside() = runTest {
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("n"))
        s.addAttachment("n", ByteArrayInputStream(bytes(10)), "a")
        val id = s.entry("n").attachments.single().id
        s.lock()
        File(tmp.root, "vault.bin").writeText("dañado")
        val fresh = store().apply { load() }
        assertEquals(VaultState.NoVault, fresh.state.value)
        fresh.create("otra-maestra".toCharArray())
        // The new vault's collection never touches the files only the damaged one can open.
        val aside = tmp.root.listFiles()!!.single { it.name.startsWith("att.bad-") }
        assertTrue(File(aside, "$id.bin").exists())
    }

    @Test fun readingTheInputNeverHoldsTheVaultLock() = runBlocking {
        // A real I/O dispatcher, so the blocked read waits on its own thread.
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.IO, Dispatchers.Unconfined, now = { 5_000L }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra-larga".toCharArray())
        s.upsert(note("n"))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        // A document provider that hangs (a Drive file not available offline) until released.
        val slow = object : InputStream() {
            private var sent = false
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                started.countDown()
                release.await()
                if (sent) return -1
                sent = true
                b[off] = 7
                return 1
            }
        }
        val adding = async(Dispatchers.Default) { s.addAttachment("n", slow, "lento.txt") }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        // While the read hangs, saving, locking and unlocking all go through.
        try {
            withTimeout(10_000) {
                s.upsert(note("m"))
                s.lock()
                s.unlock("maestra-larga".toCharArray())
            }
        } finally {
            release.countDown() // Also on failure, or the hung read would keep the test from ending.
        }
        assertEquals(2, (s.state.value as VaultState.Unlocked).entries.size)
        assertTrue(adding.await())
        val ref = s.entry("n").attachments.single()
        assertTrue(fileOf(ref).exists()) // Written and saved after the unlock: never collected.
        assertArrayEquals(byteArrayOf(7), s.openAttachment("n", ref.id))
    }

    @Test fun nothingToChangeSavesNothing() = runTest {
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("n"))
        val before = File(tmp.root, "vault.bin").readText()
        assertFalse(s.addAttachment("no-existe", ByteArrayInputStream(bytes(10)), "a"))
        assertFalse(s.removeAttachment("n", "ab".repeat(16)))
        assertEquals(before, File(tmp.root, "vault.bin").readText())
        assertTrue(attDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun total50MbCheckedBeforeWritingTheFile() = runTest {
        val s = store().apply { load(); create("maestra-larga".toCharArray()) }
        s.upsert(note("a"))
        val five = 5 * 1024 * 1024
        repeat(10) { assertTrue(s.addAttachment("a", ByteArrayInputStream(ByteArray(five)), "a$it.bin")) }
        // The folder cannot take a new file: refused for the limit, without trying to encrypt or write one.
        attDir.deleteRecursively()
        attDir.writeText("no es una carpeta")
        try {
            s.addAttachment("a", ByteArrayInputStream(ByteArray(1)), "uno-más.bin")
            fail("Debía rechazarlo")
        } catch (e: AttachmentLimit) {
            assertEquals("Los adjuntos de la bóveda pasan de 50 MB en total", e.message)
        }
        assertEquals(10, s.entry("a").attachments.size)
    }
}
