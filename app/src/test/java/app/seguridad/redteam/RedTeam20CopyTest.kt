package app.seguridad.redteam

import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.randomBytes
import app.seguridad.vault.Expiry
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultFile
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.backupCheckNotice
import app.seguridad.vault.checkBackup
import app.seguridad.vault.dataAad
import app.seguridad.vault.displayName
import app.seguridad.vault.trashLeavesIn
import app.seguridad.vault.writeBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Base64

/** The master of every hostile copy of the 2.0 attack: invented, as all the data here (decision (e)). */
internal const val RT20_MASTER = "maestra-de-prueba-del-ataque"

/** The cheap Argon2id of the tests (the cost is not what is attacked here). */
internal val RT20_KDF = KdfParams(1024, 1, 1)

/**
 * A version-4 vault file whose data blob is exactly [json], sealed under its own data key with the associated data of
 * version 4: what someone who knows the master password of a copy (its maker) can write by hand. Returns the file and
 * the open data key.
 */
internal fun rt20HostileFile(json: String, now: Long = 1L): Pair<VaultFile, ByteArray> {
    val (f, dek) = VaultCrypto.create(RT20_MASTER.toCharArray(), now, RT20_KDF)
    val nonce = randomBytes(NONCE_BYTES)
    val sealed = aead(Aead.AES_256_GCM, true, dek, nonce, dataAad(f, VaultFile.VERSION), json.toByteArray(Charsets.UTF_8))
    return f.copy(data = Base64.getEncoder().encodeToString(nonce + sealed)) to dek
}

/** A `seguridad-backup` copy (marker 3, no attachments) of [file]. */
internal fun rt20Copy(file: VaultFile): ByteArray =
    ByteArrayOutputStream().also { writeBackup(it, VaultCrypto.encodeFile(file), emptyMap()) }.toByteArray()

/** A v4 data blob with one entry of [extra] (JSON members, already written) around the required fields. */
internal fun rt20OneEntry(extra: String, tag: String = "a".repeat(32)): String =
    "{\"entries\":[{\"id\":\"rt\",\"type\":\"NOTE\",\"title\":\"Nota\",\"createdAt\":1,\"updatedAt\":1$extra}],\"vaultTag\":\"$tag\"}"

/*
 * Final attack of Clavero 2.0 (owner's decisions (d) and (f), R32), phase 1, surfaces 1 and 2: hostile v4 copies in
 * «Importar copia», «Importar como bóveda nueva» and «Comprobar una copia», and the upgrade to v4. rtN_… asserts the SAFE
 * behaviour and fails while RT-N is open (@Ignore'd only so the gate stays green; the fix removes it); resists… documents
 * an attack that fails today and stays as a regression test. Report: docs/superpowers/handoff/sdd-2.0/redteam-report.md.
 */
class RedTeam20CopyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = 1_800_000_000_000L

    private fun store(name: String) = VaultStore(
        File(File(tmp.root, name).apply { mkdirs() }, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined,
        now = { clock }, kdf = { RT20_KDF }, elapsed = { clock },
    )

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    /** Bytes allocated by every live thread while [block] runs (garbage included): an upper bound of its peak. */
    private fun allocatedBy(block: () -> Unit): Long {
        fun total() = threads.getThreadAllocatedBytes(threads.allThreadIds).filter { it > 0 }.sum()
        val before = total()
        block()
        return total() - before
    }

    /** One entry whose tags are [n] distinct short strings: about 7 bytes of JSON each. */
    private fun manyTags(n: Int) = rt20OneEntry(
        buildString(n * 8) {
            append(",\"tags\":[")
            for (i in 0 until n) {
                if (i > 0) append(',')
                append('"').append(i.toString(36)).append('"')
            }
            append(']')
        },
    )

    /** A vault an app could really hold: entries with long notes, [bytes] of JSON in all (none over 64 KiB). */
    private fun realisticOfSize(bytes: Int): String {
        val note = "Texto de una nota larga sin nada raro. ".repeat(1_500) // ~58 KB, under the 64 KiB of one entry.
        val each = note.length + 120
        return buildString(bytes + each) {
            append("{\"entries\":[")
            var i = 0
            while (length < bytes) {
                if (i > 0) append(',')
                append("{\"id\":\"n$i\",\"type\":\"NOTE\",\"title\":\"Nota $i\",\"notes\":\"").append(note).append("\",\"createdAt\":1,\"updatedAt\":1}")
                i++
            }
            append("],\"vaultTag\":\"").append("b".repeat(32)).append("\"}")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-21 (row 52): a copy is decoded whole before any of its limits is checked, and its tags have none at all.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-21 (row 52, i-5), «Importar copia»: [VaultCrypto.read] (Vault.kt:409-427) only checks the nesting
     * ([jsonNestingWithin], Vault.kt:412) and then decodes the whole blob; [VaultStore.importReplace] decodes it twice
     * (in afterMasterUnlock, VaultStore.kt:1501-1506, and again at :1507) before the first check (:1509-1514), and
     * [checkCopyEntries] (BackupCheck.kt:196-212) has no limit on the tags of an entry. A copy written by hand with one
     * entry and 2,6 million distinct short tags (18 MiB of JSON, under the 24 MiB of [checkCopySize]) decodes to about
     * 8 bytes of objects per byte of JSON (about 150 MiB of tree; a real vault of the same size, about 1), plus garbage.
     *
     * The safe behaviour asserted: such a copy is refused (a [CryptoFailure]) and refused before it is decoded, so the
     * import allocates no more than it does for a real vault of the same size (the measure is the bytes every thread
     * allocates during the call: an upper bound of the peak, with no OutOfMemoryError ever provoked). It fails today:
     * the hostile copy is imported, and allocates about twice as much as the real one.
     *
     * Not a cap of 10 tags (MAX_TAGS, EntryQuery.kt:12): no published version wrote more than 10 from its own screens,
     * but «Importar copia» of 1.3.0 to 1.4.3 kept any number from a copy made by hand, and their copies carried them on;
     * see the report, «Fila 52».
     */
    @Test fun rt21_aCopyWhoseTreeOutgrowsThePhoneIsRefusedBeforeItIsDecoded() = runBlocking {
        val size = 18 * 1024 * 1024
        val hostileJson = manyTags(size / 7)
        val hostile = rt20Copy(rt20HostileFile(hostileJson).first)
        val real = rt20Copy(rt20HostileFile(realisticOfSize(hostileJson.length)).first)
        // The real vault of the same size imports, and is what the hostile copy is measured against.
        val realBytes = allocatedBy { runBlocking { store("real").apply { load() }.importReplace(ByteArrayInputStream(real), RT20_MASTER.toCharArray()) } }
        var refused: String? = null
        val hostileBytes = allocatedBy {
            runBlocking {
                try {
                    store("hostil").apply { load() }.importReplace(ByteArrayInputStream(hostile), RT20_MASTER.toCharArray())
                } catch (e: CryptoFailure) {
                    refused = e.message
                }
            }
        }
        println("RT-21: real ${realBytes shr 20} MiB allocated, hostile ${hostileBytes shr 20} MiB, refused=$refused")
        assertTrue("A copy whose tree is ~8 times its JSON must be refused (it was imported)", refused != null)
        assertTrue(
            "Refused before it is decoded: no more allocated than for a real vault of the same size (real ${realBytes shr 20} MiB, hostile ${hostileBytes shr 20} MiB)",
            hostileBytes <= realBytes,
        )
    }

    /**
     * RT-21, after the fix (part b): «Comprobar una copia» of the same copy (a list of 300 000 tags, over [COPY_LIST_MAX])
     * refuses it as damaged. The measure of what such a tree keeps stays, with [VaultCrypto.read] without the bounds of a
     * copy (the vault's own file has none): it is several times the copy's JSON. Measured on a smaller copy (2 MiB), so
     * the test stays far from the 512 MiB of the tests' heap; the ratio is what scales to 24 MiB.
     */
    @Test fun resistsACheckOfACopyOverTheListCapRefusesIt() = runBlocking {
        val json = manyTags(300_000)
        val (file, dek) = rt20HostileFile(json)
        try {
            checkBackup(ByteArrayInputStream(rt20Copy(file)), RT20_MASTER.toCharArray(), File(tmp.root, "comprobar"), Dispatchers.Unconfined, Dispatchers.Unconfined)
            fail("The check found nothing wrong")
        } catch (e: CryptoFailure) {
            assertEquals(app.seguridad.vault.COPY_DAMAGED, e.message)
        }
        fun used(): Long {
            repeat(3) { System.gc() }
            return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        }
        val before = used()
        val data = VaultCrypto.read(file, dek)
        val retained = used() - before
        assertEquals(300_000, data.entries.single().tags.size)
        val ratio = retained.toDouble() / json.length
        println("RT-21 impact: ${json.length} bytes of JSON keep ${retained shr 10} KiB of tree (x${"%.1f".format(ratio)})")
        assertTrue("The tree is several times the JSON (x$ratio)", ratio > 4)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Surface 1: each v4 field with hostile values, and the version and the marker.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Surface 1: dates at the ends of a Long (deletedAt, expiresAt, lastUsedAt, lastUnlockAt and lastCheckedBackupAt in
     * the future, at Long.MAX_VALUE or negative), unknown enums with a default (notesFormat, a view's sort; an unknown one without a default, such as a field's kind
     * or a type in a view, makes the copy damaged), a vaultTag
     * that is not hex, and a name of 5 000 code points. The import either takes it or refuses it with a CryptoFailure;
     * nothing throws anything else, and what the screens compute from it (the trash's line, the expiry notice, the copy's
     * reminder, the vault's name) does not throw either. Today a name over 100 code points is refused as damaged
     * ([checkStructure], VaultLimits.kt:228) and the rest is taken.
     */
    @Test fun resistsHostileDatesEnumsAndNamesInAV4Copy() = runBlocking {
        val m = Long.MAX_VALUE
        val entries = "{\"entries\":[" +
            "{\"id\":\"a\",\"type\":\"NOTE\",\"title\":\"A\",\"createdAt\":$m,\"updatedAt\":-1,\"deletedAt\":$m,\"expiresAt\":$m,\"lastUsedAt\":$m,\"notesFormat\":\"HTML\",\"rotateMonths\":2147483647,\"passwordChangedAt\":${Long.MIN_VALUE}}," +
            "{\"id\":\"b\",\"type\":\"LOGIN\",\"title\":\"B\",\"password\":\"x\",\"createdAt\":1,\"updatedAt\":1,\"deletedAt\":-9223372036854775807,\"expiresAt\":-1,\"rotateMonths\":1,\"passwordChangedAt\":$m," +
            "\"fields\":[{\"id\":\"f\",\"name\":\"n\",\"kind\":\"HIDDEN\",\"value\":\"v\"}]}" +
            "],\"views\":[{\"id\":\"v\",\"name\":\"V\",\"filter\":{\"sort\":\"RANDOM\"}}]," +
            "\"lastUnlockAt\":$m,\"lastCheckedBackupAt\":$m,\"trashPurgeSkipped\":true,\"templatesVersion\":-5,\"vaultTag\":\"<script>\""
        for (name in listOf("", "x".repeat(5_000))) {
            val s = store("fechas-${name.length}").apply { load() }
            try {
                s.importReplace(ByteArrayInputStream(rt20Copy(rt20HostileFile("$entries,\"name\":\"$name\"}").first)), RT20_MASTER.toCharArray())
            } catch (e: CryptoFailure) {
                assertTrue("Only the long name is refused (${e.message})", name.isNotEmpty())
                continue
            }
            val data = (s.state.value as VaultState.Unlocked).data
            data.entries.forEach { e -> e.deletedAt?.let { trashLeavesIn(it, clock) } }
            Expiry.dueSoon(data.entries, clock)
            backupCheckNotice(data.lastCheckedBackupAt, clock)
            displayName(data, 0)
            // Opened again with the master password: the trash's dates in the future become now, nothing else is lost.
            s.lock()
            s.unlock(RT20_MASTER.toCharArray())
            assertEquals(setOf("a", "b"), (s.state.value as VaultState.Unlocked).data.entries.map { it.id }.toSet())
        }
    }

    /**
     * Surface 1 and 2: a file without its version (edited by hand) is tried as 3 and as 2, never as 4 (Vault.kt:221-225):
     * a real v4 file with its version taken out does not open, whatever the master password, so a 4 can never be read
     * with the binding of another version.
     */
    @Test fun resistsAV4FileWithoutItsVersionIsNeverOpened() {
        val (file, dek) = VaultCrypto.create(RT20_MASTER.toCharArray(), 1L, RT20_KDF)
        dek.fill(0)
        val unversioned = file.copy(version = VaultFile.VERSION_UNKNOWN)
        try {
            VaultCrypto.unlock(unversioned, RT20_MASTER.toCharArray())
            fail("A v4 without its version opened")
        } catch (e: CryptoFailure) {
            // As it must: the key's associated data of 3 and 2 do not open a 4's.
        }
        // Nor as a 3, said by hand: the version is bound.
        try {
            VaultCrypto.unlock(file.copy(version = VaultFile.VERSION_3), RT20_MASTER.toCharArray())
            fail("A v4 said to be a 3 opened")
        } catch (e: CryptoFailure) {
            // As it must.
        }
    }

    /**
     * Surface 1, the marker against the version inside: a v4 vault in a copy with marker 2 (6 MB per file). The marker only
     * sets the reader's limit per file; the vault inside is read by its own version, so it imports as it is.
     */
    @Test fun resistsTheMarkerAgainstTheVersionInside() = runBlocking {
        val (v4, _) = VaultCrypto.create(RT20_MASTER.toCharArray(), 1L, RT20_KDF, name = "Cuatro")
        val json = VaultCrypto.encodeFile(v4)
        val marker2 = ByteArrayOutputStream().also { writeBackup(it, json, emptyMap(), vaultVersion = VaultFile.VERSION_3) }.toByteArray()
        val s = store("marca2").apply { load() }
        s.importReplace(ByteArrayInputStream(marker2), RT20_MASTER.toCharArray())
        assertEquals("Cuatro", (s.state.value as VaultState.Unlocked).data.name)
    }
}
