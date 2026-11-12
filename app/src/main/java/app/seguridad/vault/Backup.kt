package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * Format `seguridad-backup` v2 (.sgbak), never to change: a ZIP (java.util.zip) with, in this order,
 *  - `seguridad-backup`: the text "2\n" (version 1, written by 1.1, has "1\n" and is read the same way: only this marker
 *    differs; a number above [BACKUP_NEWEST] is a copy from a newer version, [BACKUP_NEWER], and 1.1 says the same
 *    about a 2). 2.0 writes "3\n" for the copy of a version-4 vault, the same layout with a larger limit per entry
 *    ([BACKUP_V3_MAX_ENTRY_BYTES]): 1.2 to 1.4.3 refuse it with their [BACKUP_NEWER] and touch nothing. The copy of a
 *    vault still of version 2 or 3 (2.0 before the upgrade) keeps "2\n";
 *  - `vault.json`: the vault file exactly as on disk (`VaultFile`, still encrypted);
 *  - `att/<id>.bin`: each attachment file exactly as stored (still encrypted), ids in ascending order.
 * Reading ignores any other name; the valid ones are checked with exact patterns (no zip-slip). Limits on the bytes
 * really read: 100 MB in all, 6 MB per entry (33 MiB with marker 3) and 2 000 entries, and [BACKUP_MAX_READ_BYTES] of
 * the file itself.
 */

internal const val BACKUP_MARKER = "seguridad-backup"
internal const val BACKUP_VAULT = "vault.json"
private val BACKUP_VERSION = "2\n".toByteArray(Charsets.US_ASCII)

/** What version 1 (1.1.x) wrote as its marker: still read, never written. */
private val BACKUP_VERSION_1 = "1\n".toByteArray(Charsets.US_ASCII)

/** 2.0: the marker of the copy of a version-4 vault. */
private val BACKUP_VERSION_3 = "3\n".toByteArray(Charsets.US_ASCII)

/** The newest copy version this app reads (and writes, for a version-4 vault). */
internal const val BACKUP_NEWEST = 3

/**
 * 2.0: the most a file of a copy with marker 3 may take. The vault of up to [VaultLimits.VAULT_MAX_DATA_BYTES] of data,
 * encrypted and in Base64 with the file around it, is just over 32 MiB: 33 MiB leaves it room (ruling T1-1;
 * BackupV3Test.aVaultAtTheDataLimitFitsInACopy).
 */
const val BACKUP_V3_MAX_ENTRY_BYTES = 33L * 1024 * 1024

/**
 * The most the vault file may take in the copy a vault of [vaultVersion] gets: marker 3 for a version-4 vault
 * ([BACKUP_V3_MAX_ENTRY_BYTES]), marker 2 for any other ([BACKUP_MAX_ENTRY_BYTES]). What «Importar de otra app» checks
 * the vault it would save against (ruling T1-2).
 */
fun backupMaxEntryBytes(vaultVersion: Int): Long = if (vaultVersion == VaultFile.VERSION) BACKUP_V3_MAX_ENTRY_BYTES else BACKUP_MAX_ENTRY_BYTES

const val BACKUP_MAX_TOTAL_BYTES = 100L * 1024 * 1024
const val BACKUP_MAX_ENTRY_BYTES = 6L * 1024 * 1024
const val BACKUP_MAX_ENTRIES = 2_000

/**
 * The most of the file [BackupReader] reads, whatever its entries give out (1.4.0, owner's decision for «Comprobar una
 * copia»): a deflated entry made of empty blocks gives no bytes out, so the limits above would never stop it. A copy
 * the app writes is at most [BACKUP_MAX_TOTAL_BYTES] of content plus its headers, its data descriptors and the deflate
 * framing of its stored blocks (about 0.2 MB at 2 000 entries, BackupCheckTest.aCopyAtTheLimitsIsStillRead): 10 MB
 * more leaves every real copy room.
 */
const val BACKUP_MAX_READ_BYTES = BACKUP_MAX_TOTAL_BYTES + 10L * 1024 * 1024

/** How many bytes [isBackup] needs from the start of a file. */
const val BACKUP_HEAD_BYTES = 64

private val ATTACHMENT_ENTRY = Regex("att/([0-9a-f]{32})\\.bin")
private val ATTACHMENT_ID = Regex("[0-9a-f]{32}")

internal const val BACKUP_DAMAGED = "La copia está dañada o no es una copia de Clavero"
internal const val BACKUP_NEWER = "La copia es de una versión más nueva de Clavero"
internal const val BACKUP_TOO_BIG = "La copia pasa de 100 MB"
internal const val BACKUP_ENTRY_TOO_BIG = "Un archivo de la copia pasa de 6 MB"
internal const val BACKUP_ENTRY_TOO_BIG_V3 = "Un archivo de la copia pasa de 33 MB"
internal const val BACKUP_TOO_MANY = "La copia tiene más de 2 000 archivos"
internal const val BACKUP_DOES_NOT_FIT = "La bóveda no cabe en una copia (100 MB en total, 6 MB por archivo y 2 000 archivos)"
internal const val BACKUP_DOES_NOT_FIT_V3 = "La bóveda no cabe en una copia (100 MB en total, 33 MB por archivo y 2 000 archivos)"

/** Whether [vaultJson] is a version-4 vault file, whose copy gets the marker 3; anything else keeps the marker 2. */
private fun isV4(vaultJson: String): Boolean = try {
    VaultCrypto.decodeFile(vaultJson).let { it.format == VaultFile.FORMAT && it.version == VaultFile.VERSION }
} catch (e: CryptoFailure) {
    false
}

/** Name offered when exporting: seguridad-boveda-AAAAMMDD.sgbak. */
fun backupFileName(date: LocalDate): String = "seguridad-boveda-%04d%02d%02d.sgbak".format(date.year, date.monthValue, date.dayOfMonth)

/**
 * Whether [head] (the first [BACKUP_HEAD_BYTES] of a file) may be something «Importar copia» reads: a `seguridad-backup`
 * ([isBackup]) or a JSON object (a vault export of Seguridad 1.0.x: the strict JSON parser accepts only blanks
 * before the `{`, never a byte-order mark). A head that is all blanks and as long as [BACKUP_HEAD_BYTES] is let through,
 * since the `{` may come later. Checked before the master password is asked, so a photo or a note is turned down at
 * once; it never refuses what [VaultStore.importReplace] accepts, which still checks everything (owner's phone test,
 * 1-11-2026).
 */
fun mayBeVaultImport(head: ByteArray): Boolean {
    if (isBackup(head)) return true
    var i = 0
    while (i < head.size && (head[i].toInt() and 0xff).toChar() in " \t\r\n") i++
    return if (i == head.size) head.size >= BACKUP_HEAD_BYTES else head[i] == '{'.code.toByte()
}

/**
 * Whether [head] (the first [BACKUP_HEAD_BYTES] of a file) is a `seguridad-backup`: a ZIP local file header whose
 * name is [BACKUP_MARKER]. Anything else (a JSON export of 1.0.x, another ZIP) is not.
 */
fun isBackup(head: ByteArray): Boolean {
    val name = BACKUP_MARKER.toByteArray(Charsets.US_ASCII)
    if (head.size < 30 + name.size) return false
    val signature = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
    if (signature.indices.any { head[it] != signature[it] }) return false
    val nameLength = (head[26].toInt() and 0xff) or ((head[27].toInt() and 0xff) shl 8)
    return nameLength == name.size && name.indices.all { head[30 + it] == name[it] }
}

/** An attachment that is missing on disk or whose file does not match its SHA-256, found while writing a copy. */
internal class BackupAttachmentMismatch(val id: String) : Exception()

/**
 * Writes the copy to [out] (left open: the caller closes it), with the marker 3 when [vaultJson] is a version-4 vault and
 * the marker 2 otherwise, each with its own limit per file. [attachments] maps each attachment id to its file;
 * with [expectedSha256] (id to hex), every file is checked while it is written and a missing or different one throws
 * [BackupAttachmentMismatch], so a copy that could never be restored is never finished. For the same reason a vault
 * over the limits [BackupReader] applies throws [CryptoFailure] with [BACKUP_DOES_NOT_FIT].
 */
fun writeBackup(out: OutputStream, vaultJson: String, attachments: Map<String, File>, expectedSha256: Map<String, String> = emptyMap()) {
    require(attachments.keys.all { ATTACHMENT_ID.matches(it) }) { "Id de adjunto no válido" }
    val json = vaultJson.toByteArray(Charsets.UTF_8)
    val v4 = isV4(vaultJson)
    val marker = if (v4) BACKUP_VERSION_3 else BACKUP_VERSION
    val entryMax = if (v4) BACKUP_V3_MAX_ENTRY_BYTES else BACKUP_MAX_ENTRY_BYTES
    val doesNotFit = if (v4) BACKUP_DOES_NOT_FIT_V3 else BACKUP_DOES_NOT_FIT
    if (json.size > entryMax || attachments.size + 2 > BACKUP_MAX_ENTRIES) throw CryptoFailure(doesNotFit)
    var total = (marker.size + json.size).toLong()
    val zip = ZipOutputStream(out)
    zip.putNextEntry(ZipEntry(BACKUP_MARKER))
    zip.write(marker)
    zip.closeEntry()
    zip.putNextEntry(ZipEntry(BACKUP_VAULT))
    zip.write(json)
    zip.closeEntry()
    // Encrypted bytes do not compress: stored blocks, no time wasted.
    zip.setLevel(Deflater.NO_COMPRESSION)
    val buf = ByteArray(64 * 1024)
    for (id in attachments.keys.sorted()) {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = try {
            attachments.getValue(id).inputStream()
        } catch (e: FileNotFoundException) {
            if (id in expectedSha256) throw BackupAttachmentMismatch(id) else throw e
        }
        zip.putNextEntry(ZipEntry("att/$id.bin"))
        input.use {
            var size = 0L
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                size += n
                total += n
                if (size > entryMax || total > BACKUP_MAX_TOTAL_BYTES) throw CryptoFailure(doesNotFit)
                digest.update(buf, 0, n)
                zip.write(buf, 0, n)
            }
        }
        zip.closeEntry()
        expectedSha256[id]?.let { hex ->
            if (!MessageDigest.isEqual(digest.digest(), unhexOrEmpty(hex))) throw BackupAttachmentMismatch(id)
        }
    }
    zip.finish()
    zip.flush()
}

private fun unhexOrEmpty(hex: String): ByteArray =
    if (hex.length % 2 != 0 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) ByteArray(0)
    else ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** The SHA-256 of [file] equals [hex] (lower-case hex), compared in constant time. False if it cannot be read. */
internal fun fileMatchesSha256(file: File, hex: String, maxBytes: Long): Boolean {
    val expected = unhexOrEmpty(hex)
    if (expected.size != 32 || !file.isFile || file.length() > maxBytes) return false
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) return false
                digest.update(buf, 0, n)
            }
        }
        MessageDigest.isEqual(digest.digest(), expected)
    } catch (e: java.io.IOException) {
        false
    }
}

/**
 * What a copy holds: the vault file as text, each attachment id with its file in the temporary folder, and the copy's
 * [version] (1, 2 or 3, from its marker).
 */
class BackupContents(val vaultJson: String, val attachments: Map<String, File>, val version: Int = BACKUP_NEWEST)

/**
 * [input] read at most [max] bytes in all (more throws [CryptoFailure] with [BACKUP_TOO_BIG]), with [check] called before
 * each read: a cancellation it throws stops the reading at once, also inside one read of the ZIP that would go on
 * through many empty blocks.
 */
private class BoundedInput(input: InputStream, private val max: Long, private val check: () -> Unit) : FilterInputStream(input) {
    private var count = 0L

    private fun counted(n: Long) {
        if (n > 0) count += n
        if (count > max) throw CryptoFailure(BACKUP_TOO_BIG)
    }

    override fun read(): Int {
        check()
        return super.read().also { if (it >= 0) counted(1) }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        check()
        return super.read(b, off, len).also { counted(it.toLong()) }
    }

    override fun skip(n: Long): Long {
        check()
        return super.skip(n).also { counted(it) }
    }

    // No mark and reset: what was counted stays counted.
    override fun markSupported(): Boolean = false
}

/** Reads a `seguridad-backup` copy, version 1, 2 or 3. */
class BackupReader {
    /**
     * Reads the copy from [input] (left open), writing each attachment to `<id>.bin` in [tempDir] as it comes, never
     * the whole copy in memory. Any problem throws [CryptoFailure] (a read error of [input] stays an IOException)
     * and deletes what was written. Names are only ever taken from the exact patterns, so nothing is written
     * outside [tempDir]; other names are skipped, but their bytes count against the limits too. At most
     * [BACKUP_MAX_READ_BYTES] of [input] are read. [check] runs before each read of [input]: whatever it throws (a
     * coroutine's cancellation) goes through as it is, with what was written deleted.
     */
    fun read(input: InputStream, tempDir: File, check: () -> Unit = {}): BackupContents {
        val written = ArrayList<File>()
        try {
            return readInto(ZipInputStream(BoundedInput(input, BACKUP_MAX_READ_BYTES, check)), tempDir, written)
        } catch (e: Exception) {
            written.forEach { it.delete() }
            throw when (e) {
                is CryptoFailure -> e
                // A cut or malformed ZIP (also a name that is not valid UTF-8).
                is ZipException, is EOFException, is IllegalArgumentException -> CryptoFailure(BACKUP_DAMAGED)
                else -> e
            }
        }
    }

    private fun readInto(zip: ZipInputStream, tempDir: File, written: MutableList<File>): BackupContents {
        val buf = ByteArray(64 * 1024)
        var entries = 0
        var total = 0L
        var vaultJson: String? = null
        var copyVersion = BACKUP_NEWEST
        // The marker comes first, so every other entry is read with its version's limit.
        var entryMax = BACKUP_MAX_ENTRY_BYTES
        var entryTooBig = BACKUP_ENTRY_TOO_BIG
        val attachments = LinkedHashMap<String, File>()

        /** Copies the current entry to [sink] (or nowhere), counting the bytes really read against every limit. */
        fun copy(sink: OutputStream?) {
            var size = 0L
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                size += n
                total += n
                if (size > entryMax) throw CryptoFailure(entryTooBig)
                if (total > BACKUP_MAX_TOTAL_BYTES) throw CryptoFailure(BACKUP_TOO_BIG)
                sink?.write(buf, 0, n)
            }
        }

        fun bytes(): ByteArray = java.io.ByteArrayOutputStream().also { copy(it) }.toByteArray()

        while (true) {
            val entry = zip.nextEntry ?: break
            if (++entries > BACKUP_MAX_ENTRIES) throw CryptoFailure(BACKUP_TOO_MANY)
            val name = entry.name
            if (entries == 1) {
                // The marker comes first, with exactly this version.
                if (name != BACKUP_MARKER) throw CryptoFailure(BACKUP_DAMAGED)
                val version = bytes()
                copyVersion = when {
                    version.contentEquals(BACKUP_VERSION_1) -> 1
                    version.contentEquals(BACKUP_VERSION) -> 2
                    version.contentEquals(BACKUP_VERSION_3) -> 3
                    else -> {
                        val number = version.decodeToString().trim().toIntOrNull()
                        throw CryptoFailure(if (number != null && number > BACKUP_NEWEST) BACKUP_NEWER else BACKUP_DAMAGED)
                    }
                }
                if (copyVersion == 3) {
                    entryMax = BACKUP_V3_MAX_ENTRY_BYTES
                    entryTooBig = BACKUP_ENTRY_TOO_BIG_V3
                }
                continue
            }
            val att = ATTACHMENT_ENTRY.matchEntire(name)
            when {
                name == BACKUP_MARKER -> throw CryptoFailure(BACKUP_DAMAGED) // Twice.
                name == BACKUP_VAULT -> {
                    if (vaultJson != null) throw CryptoFailure(BACKUP_DAMAGED) // Twice.
                    vaultJson = bytes().decodeToString()
                }
                att != null -> {
                    val id = att.groupValues[1]
                    if (id in attachments) throw CryptoFailure(BACKUP_DAMAGED) // Twice.
                    if (attachments.isEmpty()) tempDir.mkdirs()
                    // The file name is built from the 32 hex digits only, never from the entry name.
                    val file = File(tempDir, "$id.bin")
                    if (file.exists()) throw CryptoFailure(BACKUP_DAMAGED)
                    written += file
                    FileOutputStream(file).use { out ->
                        copy(out)
                        out.fd.sync()
                    }
                    attachments[id] = file
                }
                else -> copy(null) // Ignored, but counted.
            }
        }
        if (entries == 0 || vaultJson == null) throw CryptoFailure(BACKUP_DAMAGED)
        return BackupContents(vaultJson, attachments, copyVersion)
    }
}
