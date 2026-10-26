package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
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
 *    differs; a number above 2 is a copy from a newer version, [BACKUP_NEWER], and 1.1 says the same about a 2);
 *  - `vault.json`: the vault file exactly as on disk (`VaultFile`, still encrypted);
 *  - `att/<id>.bin`: each attachment file exactly as stored (still encrypted), ids in ascending order.
 * Reading ignores any other name; the valid ones are checked with exact patterns (no zip-slip). Limits on the bytes
 * really read: 100 MB in all, 6 MB per entry and 2 000 entries.
 */

internal const val BACKUP_MARKER = "seguridad-backup"
internal const val BACKUP_VAULT = "vault.json"
private val BACKUP_VERSION = "2\n".toByteArray(Charsets.US_ASCII)

/** What version 1 (1.1.x) wrote as its marker: still read, never written. */
private val BACKUP_VERSION_1 = "1\n".toByteArray(Charsets.US_ASCII)

/** The newest copy version this app reads. */
private const val BACKUP_NEWEST = 2

const val BACKUP_MAX_TOTAL_BYTES = 100L * 1024 * 1024
const val BACKUP_MAX_ENTRY_BYTES = 6L * 1024 * 1024
const val BACKUP_MAX_ENTRIES = 2_000

/** How many bytes [isBackup] needs from the start of a file. */
const val BACKUP_HEAD_BYTES = 64

private val ATTACHMENT_ENTRY = Regex("att/([0-9a-f]{32})\\.bin")
private val ATTACHMENT_ID = Regex("[0-9a-f]{32}")

internal const val BACKUP_DAMAGED = "La copia está dañada o no es una copia de Seguridad"
internal const val BACKUP_NEWER = "La copia es de una versión más nueva de Seguridad"
internal const val BACKUP_TOO_BIG = "La copia pasa de 100 MB"
internal const val BACKUP_ENTRY_TOO_BIG = "Un archivo de la copia pasa de 6 MB"
internal const val BACKUP_TOO_MANY = "La copia tiene más de 2 000 archivos"
internal const val BACKUP_DOES_NOT_FIT = "La bóveda no cabe en una copia (100 MB en total, 6 MB por archivo y 2 000 archivos)"

/** Name offered when exporting: seguridad-boveda-AAAAMMDD.sgbak. */
fun backupFileName(date: LocalDate): String = "seguridad-boveda-%04d%02d%02d.sgbak".format(date.year, date.monthValue, date.dayOfMonth)

/**
 * Whether [head] (the first [BACKUP_HEAD_BYTES] of a file) is a `seguridad-backup`: a ZIP local file header whose
 * name is [BACKUP_MARKER]. Anything else (a JSON export of 1.0.x or CriptoPanel, another ZIP) is not.
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
 * Writes the copy to [out] (left open: the caller closes it). [attachments] maps each attachment id to its file;
 * with [expectedSha256] (id to hex), every file is checked while it is written and a missing or different one throws
 * [BackupAttachmentMismatch], so a copy that could never be restored is never finished. For the same reason a vault
 * over the limits [BackupReader] applies throws [CryptoFailure] with [BACKUP_DOES_NOT_FIT].
 */
fun writeBackup(out: OutputStream, vaultJson: String, attachments: Map<String, File>, expectedSha256: Map<String, String> = emptyMap()) {
    require(attachments.keys.all { ATTACHMENT_ID.matches(it) }) { "Id de adjunto no válido" }
    val json = vaultJson.toByteArray(Charsets.UTF_8)
    if (json.size > BACKUP_MAX_ENTRY_BYTES || attachments.size + 2 > BACKUP_MAX_ENTRIES) throw CryptoFailure(BACKUP_DOES_NOT_FIT)
    var total = (BACKUP_VERSION.size + json.size).toLong()
    val zip = ZipOutputStream(out)
    zip.putNextEntry(ZipEntry(BACKUP_MARKER))
    zip.write(BACKUP_VERSION)
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
                if (size > BACKUP_MAX_ENTRY_BYTES || total > BACKUP_MAX_TOTAL_BYTES) throw CryptoFailure(BACKUP_DOES_NOT_FIT)
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

/** What a copy holds: the vault file as text and each attachment id with its file in the temporary folder. */
class BackupContents(val vaultJson: String, val attachments: Map<String, File>)

/** Reads a `seguridad-backup` copy, version 1 or 2. */
class BackupReader {
    /**
     * Reads the copy from [input] (left open), writing each attachment to `<id>.bin` in [tempDir] as it comes, never
     * the whole copy in memory. Any problem throws [CryptoFailure] (a read error of [input] stays an IOException)
     * and deletes what was written. Names are only ever taken from the exact patterns, so nothing is written
     * outside [tempDir]; other names are skipped, but their bytes count against the limits too.
     */
    fun read(input: InputStream, tempDir: File): BackupContents {
        val written = ArrayList<File>()
        try {
            return readInto(ZipInputStream(input), tempDir, written)
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
        val attachments = LinkedHashMap<String, File>()

        /** Copies the current entry to [sink] (or nowhere), counting the bytes really read against every limit. */
        fun copy(sink: OutputStream?) {
            var size = 0L
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                size += n
                total += n
                if (size > BACKUP_MAX_ENTRY_BYTES) throw CryptoFailure(BACKUP_ENTRY_TOO_BIG)
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
                if (!version.contentEquals(BACKUP_VERSION) && !version.contentEquals(BACKUP_VERSION_1)) {
                    val number = version.decodeToString().trim().toIntOrNull()
                    throw CryptoFailure(if (number != null && number > BACKUP_NEWEST) BACKUP_NEWER else BACKUP_DAMAGED)
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
        return BackupContents(vaultJson, attachments)
    }
}
