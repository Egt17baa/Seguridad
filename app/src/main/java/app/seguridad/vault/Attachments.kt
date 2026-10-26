package app.seguridad.vault

import app.seguridad.core.readAtMost
import app.seguridad.core.writeBytesAtomically
import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KEY_BYTES
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.TAG_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.randomBytes
import app.seguridad.crypto.safeFileName
import app.seguridad.crypto.secureRandom
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Largest attachment, in plain: 5 MB. */
const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

/** Largest sum of all the vault's attachments, in plain: 50 MB. */
const val MAX_ATTACHMENTS_TOTAL_BYTES = 50L * 1024 * 1024

/** An attachment over [MAX_ATTACHMENT_BYTES], or one that would take the vault over [MAX_ATTACHMENTS_TOTAL_BYTES]. */
class AttachmentLimit(message: String) : Exception(message)

internal const val ATTACHMENT_TOO_BIG = "El adjunto pasa de 5 MB"
internal const val ATTACHMENTS_TOTAL_TOO_BIG = "Los adjuntos de la bóveda pasan de 50 MB en total"
internal const val ATTACHMENT_DAMAGED = "El adjunto está dañado o se ha cambiado"

/** Associated data prefix of format v1; the id follows it, so a file cannot be moved under another id. */
private const val AAD_PREFIX = "seguridad-attachment-v1\u0000"

private val ID = Regex("[0-9a-f]{32}")
private val SHA256_HEX = Regex("[0-9a-f]{64}")

/** Names of what this store writes in its folder: `<id>.bin`, and `<id>.bin.tmp` while being written. */
private val OWN_FILE = Regex("([0-9a-f]{32})\\.bin(\\.tmp)?")

private fun lowerHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

private fun unhex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

private fun aad(id: String) = (AAD_PREFIX + id).toByteArray(Charsets.UTF_8)

/**
 * Encrypted attachments of the vault, one file each in [dir] (files/vault/att/). Format v1, never to change:
 * `<id>.bin` = nonce(12) ‖ AES-256-GCM(key, nonce, AAD = "seguridad-attachment-v1\u0000" + id, content), with a
 * random 256-bit key per attachment. The key, the SHA-256 of the file, the name and the size live in the entry
 * ([AttachmentRef]), which the vault's data key encrypts and authenticates.
 */
class AttachmentStore(val dir: File, private val random: SecureRandom = secureRandom) {

    private fun fileOf(id: String) = File(dir, "$id.bin")

    /**
     * Reads at most [MAX_ATTACHMENT_BYTES] + 1 bytes of [input] (refused above the limit, nothing written), then
     * encrypts it with a fresh key and writes it atomically. The caller saves the returned reference in the vault.
     */
    fun add(input: InputStream, name: String): AttachmentRef {
        val plain = read(input)
        try {
            return add(plain, name)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * At most [MAX_ATTACHMENT_BYTES] + 1 bytes of [input]; [AttachmentLimit] (and the bytes wiped) above the limit.
     * The caller wipes the result when done.
     */
    fun read(input: InputStream): ByteArray {
        val plain = input.readAtMost(MAX_ATTACHMENT_BYTES + 1)
        if (plain.size > MAX_ATTACHMENT_BYTES) {
            plain.fill(0)
            throw AttachmentLimit(ATTACHMENT_TOO_BIG)
        }
        return plain
    }

    /** Encrypts [plain] (from [read]; the caller wipes it) with a fresh key and writes it atomically. */
    fun add(plain: ByteArray, name: String): AttachmentRef {
        if (plain.size > MAX_ATTACHMENT_BYTES) throw AttachmentLimit(ATTACHMENT_TOO_BIG)
        val key = randomBytes(KEY_BYTES, random)
        try {
            val id = lowerHex(randomBytes(16, random))
            val nonce = randomBytes(NONCE_BYTES, random)
            val sealed = nonce + aead(Aead.AES_256_GCM, true, key, nonce, aad(id), plain)
            fileOf(id).writeBytesAtomically(sealed)
            return AttachmentRef(
                id = id,
                name = safeFileName(name),
                size = plain.size.toLong(),
                sha256 = lowerHex(MessageDigest.getInstance("SHA-256").digest(sealed)),
                key = Base64.getEncoder().encodeToString(key),
            )
        } finally {
            key.fill(0)
        }
    }

    /**
     * The content of [ref], after checking the file's SHA-256 (in constant time) and the GCM tag. Any problem
     * (missing, cut, changed or swapped file, bad reference) is [CryptoFailure] with [ATTACHMENT_DAMAGED].
     * The caller wipes the result when done.
     */
    fun open(ref: AttachmentRef): ByteArray {
        var key: ByteArray? = null
        try {
            if (!ID.matches(ref.id) || !SHA256_HEX.matches(ref.sha256) || ref.size !in 0..MAX_ATTACHMENT_BYTES) throw CryptoFailure()
            val file = fileOf(ref.id)
            val limit = MAX_ATTACHMENT_BYTES + NONCE_BYTES + TAG_BYTES
            val sealed = file.inputStream().use { it.readAtMost(limit + 1) }
            if (sealed.size > limit || sealed.size < NONCE_BYTES + TAG_BYTES) throw CryptoFailure()
            if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(sealed), unhex(ref.sha256))) throw CryptoFailure()
            key = Base64.getDecoder().decode(ref.key)
            if (key.size != KEY_BYTES) throw CryptoFailure()
            val plain = aead(Aead.AES_256_GCM, false, key, sealed.copyOfRange(0, NONCE_BYTES), aad(ref.id), sealed.copyOfRange(NONCE_BYTES, sealed.size))
            if (plain.size.toLong() != ref.size) {
                plain.fill(0)
                throw CryptoFailure()
            }
            return plain
        } catch (e: Exception) {
            // One message for every cause: it says nothing about which check failed.
            throw CryptoFailure(ATTACHMENT_DAMAGED)
        } finally {
            key?.fill(0)
        }
    }

    /** Deletes the file of [id] (an attachment just refused or never saved in the vault); false if not ours. */
    internal fun delete(id: String): Boolean = ID.matches(id) && fileOf(id).delete()

    /**
     * Deletes every file of this store whose id is not in [live] (and any temporary file left by a crash). Other
     * files in [dir] are left alone. Never throws: a failure here must not affect the vault.
     */
    fun collectGarbage(live: Set<String>) {
        try {
            dir.listFiles()?.forEach { f ->
                val m = OWN_FILE.matchEntire(f.name) ?: return@forEach
                if (m.groupValues[2].isNotEmpty() || m.groupValues[1] !in live) {
                    try {
                        f.delete()
                    } catch (e: Exception) {
                        // Left for the next collection.
                    }
                }
            }
        } catch (e: Exception) {
            // Left for the next collection.
        }
    }

    /** What [refs] take, in plain, to check against [MAX_ATTACHMENTS_TOTAL_BYTES]. */
    fun totalBytes(refs: List<AttachmentRef>): Long = refs.sumOf { it.size }
}
