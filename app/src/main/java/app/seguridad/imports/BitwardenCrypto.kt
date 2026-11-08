package app.seguridad.imports

import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.appHeapKiB
import kotlinx.serialization.json.JsonObject
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.MessageDigest

/*
 * Bitwarden's JSON export «.json (Encrypted)» with «Protegida con contraseña» (spec «Bitwarden protegido con
 * contraseña»), written only from public documents (no code read or copied):
 * - https://bitwarden.com/help/encrypted-export/ (the password and a salt give a key with the KDF, stretched with HKDF
 *   into an encryption key and a MAC key);
 * - https://community.bitwarden.com/t/new-backup-options-for-bitwarden-academic-questions/45338 (Bitwarden's staff:
 *   the fields; a random salt per export, stored in the file; HKDF-Expand into the encryption and MAC parts;
 *   encKeyValidation_DO_NOT_EDIT, a short string under the same key, to check the key before `data`);
 * - Bitwarden's help page «crypto.html» (https://github.com/bitwarden/help): PBKDF2-SHA256 over the UTF-8 password with
 *   the salt string in UTF-8; HKDF-Expand with the infos «enc» and «mac», 32 bytes each; the cipher string type 2,
 *   «2.iv|ct|mac» in Base64, AES-256-CBC with a 16-byte IV, HMAC-SHA256 with the MAC key over IV ‖ ciphertext, checked
 *   before decrypting;
 * - https://bitwarden.com/help/kdf-algorithms/ (PBKDF2-SHA256 or Argon2id, its memory in MiB).
 * Not yet confirmed against a real export (docs/superpowers/handoff/sdd-1.4.0/task-10-report.md, «Pendiente de
 * confirmar»): the Argon2id salt, SHA-256 of the salt string (a Bitwarden developer's forum post), and that the salt
 * string is used as text, not decoded.
 */

/** Longest `salt` and `encKeyValidation_DO_NOT_EDIT` read: far more than the 24 and about 100 characters they have. */
private const val BITWARDEN_SALT_MAX_CHARS = 1024
private const val BITWARDEN_VALIDATION_MAX_CHARS = 4096

private const val KDF_PBKDF2_SHA256 = 0
private const val KDF_ARGON2ID = 1
private const val KEY_BYTES = 32
private const val IV_BYTES = 16
private const val MAC_BYTES = 32
private const val AES_BLOCK = 16

/** A cipher string of type 2 (AES-256-CBC, HMAC-SHA256), decoded: not secret. */
internal class EncString(val iv: ByteArray, val ct: ByteArray, val mac: ByteArray)

/**
 * What the header of a protected export says, every value checked ([bitwardenProtected]) before any work. [argon2]
 * is null for PBKDF2-SHA256, which then runs [iterations] times.
 */
internal data class BitwardenProtected(
    val salt: String,
    val iterations: Int,
    val argon2: KdfParams?,
    val validation: EncString,
    val data: EncString,
)

/** The two halves of the stretched key. They live in the read's [Secrets]. */
internal class BitwardenKeys(val enc: ByteArray, val mac: ByteArray)

/**
 * The header of a protected export, before any work (owner's decision (a)): the KDF's settings in range
 * ([checkPbkdf2Iterations], [bitwardenArgon2Params]: [IMPORT_KDF_TOO_BIG]), the salt present and bounded, and both
 * cipher strings of type 2 with their parts' lengths ([encString]). Anything missing, of the wrong type or of another
 * KDF or cipher type is [IMPORT_BROKEN].
 */
internal fun bitwardenProtected(root: JsonObject, heapKiB: Long = appHeapKiB()): BitwardenProtected = try {
    val kdfType = root.number("kdfType") ?: throw WrongType()
    val iterations = root.number("kdfIterations") ?: throw WrongType()
    val argon2 = when (kdfType) {
        KDF_PBKDF2_SHA256 -> {
            checkPbkdf2Iterations(iterations)
            null
        }
        KDF_ARGON2ID -> bitwardenArgon2Params(
            root.number("kdfMemory") ?: throw WrongType(), iterations, root.number("kdfParallelism") ?: throw WrongType(), heapKiB,
        )
        else -> throw WrongType()
    }
    val salt = root.textOrNull("salt") ?: throw WrongType()
    if (salt.isEmpty() || salt.length > BITWARDEN_SALT_MAX_CHARS || !wellFormed(salt)) throw WrongType()
    // A validation string longer than any real one is a broken file, not a big one ([IMPORT_ENCRYPTED_TOO_BIG] speaks of 8 MB).
    val validationText = root.textOrNull("encKeyValidation_DO_NOT_EDIT") ?: throw WrongType()
    if (validationText.length > BITWARDEN_VALIDATION_MAX_CHARS) throw WrongType()
    val validation = encString(validationText, BITWARDEN_VALIDATION_MAX_CHARS)
    val data = encString(root.textOrNull("data") ?: throw WrongType(), IMPORT_MAX_ENCRYPTED_CHARS)
    BitwardenProtected(salt, iterations, argon2, validation, data)
} catch (e: WrongType) {
    throw ImportFailure(IMPORT_BROKEN)
}

/**
 * A cipher string «2.iv|ct|mac»: [maxChars] checked before anything is decoded ([IMPORT_ENCRYPTED_TOO_BIG]); then a
 * 16-byte IV, a ciphertext of whole AES blocks and a 32-byte MAC, or [IMPORT_BROKEN]. Any other type is broken.
 */
internal fun encString(text: String, maxChars: Int): EncString {
    if (text.length > maxChars) throw ImportFailure(IMPORT_ENCRYPTED_TOO_BIG)
    if (!text.startsWith("2.")) throw ImportFailure(IMPORT_BROKEN)
    val parts = text.substring(2).split('|')
    if (parts.size != 3) throw ImportFailure(IMPORT_BROKEN)
    val iv = importBase64(parts[0], maxChars)
    val ct = importBase64(parts[1], maxChars)
    val mac = importBase64(parts[2], maxChars)
    if (iv.size != IV_BYTES || ct.isEmpty() || ct.size % AES_BLOCK != 0 || mac.size != MAC_BYTES) throw ImportFailure(IMPORT_BROKEN)
    return EncString(iv, ct, mac)
}

/**
 * The key of [p] from [password]: PBKDF2-SHA256 or Argon2id (with the salt hashed by SHA-256, from documentation),
 * then HKDF-Expand «enc» and «mac». Every array is in [s]: the password's bytes, the salt's, the key and both halves.
 */
internal fun bitwardenKeys(p: BitwardenProtected, password: CharArray, s: Secrets): BitwardenKeys {
    val pw = importPasswordBytes(password, s)
    val salt = s.keep(p.salt.toByteArray(Charsets.UTF_8))
    val key = if (p.argon2 == null) {
        pbkdf2Sha256(pw, salt, p.iterations, KEY_BYTES, s)
    } else {
        argon2idKey(pw, s.keep(MessageDigest.getInstance("SHA-256").digest(salt)), p.argon2, KEY_BYTES, s)
    }
    return BitwardenKeys(
        hkdfExpandSha256(key, "enc".toByteArray(Charsets.US_ASCII), KEY_BYTES, s),
        hkdfExpandSha256(key, "mac".toByteArray(Charsets.US_ASCII), KEY_BYTES, s),
    )
}

/**
 * The plaintext of `data` (owner's decision (b)): first the HMAC of `encKeyValidation_DO_NOT_EDIT`, then the HMAC of
 * `data`, each compared in constant time ([MessageDigest.isEqual]); only when both hold is `data` decrypted
 * ([onDecrypt] is called right before, for the tests). A MAC that fails is [IMPORT_WRONG_PASSWORD]: a wrong password and
 * a changed file look the same. A bad padding after a good MAC is [IMPORT_BROKEN].
 */
internal fun bitwardenData(p: BitwardenProtected, keys: BitwardenKeys, s: Secrets, onDecrypt: () -> Unit = {}): ByteArray {
    if (!macHolds(p.validation, keys.mac, s) || !macHolds(p.data, keys.mac, s)) throw ImportFailure(IMPORT_WRONG_PASSWORD)
    onDecrypt()
    return aesCbcOpen(keys.enc, p.data.iv, p.data.ct, s)
}

/** HMAC-SHA256 with [macKey] over IV ‖ ciphertext, compared with [e]'s MAC in constant time. */
private fun macHolds(e: EncString, macKey: ByteArray, s: Secrets): Boolean {
    val kp = KeyParameter(macKey)
    try {
        val hmac = HMac(SHA256Digest())
        hmac.init(kp)
        hmac.update(e.iv, 0, e.iv.size)
        hmac.update(e.ct, 0, e.ct.size)
        val mac = s.bytes(MAC_BYTES)
        hmac.doFinal(mac, 0)
        return MessageDigest.isEqual(mac, e.mac)
    } finally {
        kp.key.fill(0)
    }
}

/**
 * AES-256-CBC with PKCS#7 padding; the plaintext is in [s]. Only after [macHolds]. The output buffer, as long as the
 * ciphertext, is wiped as soon as the plaintext is copied out of it, and let go ([Secrets.scratch]), so only one
 * plaintext-sized array stays alive while it is parsed (M-6).
 */
private fun aesCbcOpen(key: ByteArray, iv: ByteArray, ct: ByteArray, s: Secrets): ByteArray {
    val kp = KeyParameter(key)
    try {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(AESEngine.newInstance()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(kp, iv))
        val out = s.scratch(cipher.getOutputSize(ct.size))
        try {
            val n = try {
                val head = cipher.processBytes(ct, 0, ct.size, out, 0)
                head + cipher.doFinal(out, head)
            } catch (e: InvalidCipherTextException) {
                throw ImportFailure(IMPORT_BROKEN)
            }
            return s.keep(out.copyOf(n))
        } finally {
            out.fill(0)
        }
    } finally {
        kp.key.fill(0)
    }
}

/**
 * The items of `data`, read as data (owner's decision (c)): through [decryptedJson] (the same tree estimate as a plain
 * file, against [treeBudget]), then only as a plain export ([readBitwardenPlain], with its item limits). An object that
 * is not one, or one that says it is encrypted again, is [IMPORT_BROKEN]: nothing inside is decrypted a second time.
 */
internal fun bitwardenRead(p: BitwardenProtected, keys: BitwardenKeys, treeBudget: Long, s: Secrets, onDecrypt: () -> Unit = {}): ImportRead {
    val tree = decryptedJson(bitwardenData(p, keys, s, onDecrypt), treeBudget) as? JsonObject ?: throw ImportFailure(IMPORT_BROKEN)
    val again = try {
        tree.flag("encrypted") ?: false
    } catch (e: WrongType) {
        throw ImportFailure(IMPORT_BROKEN)
    }
    if (again) throw ImportFailure(IMPORT_BROKEN)
    return readBitwardenPlain(tree)
}

/**
 * A protected export ([root]) opened with [password] (the caller's: not wiped here): the header checked, the key
 * derived, both MACs checked, `data` decrypted and read. Every secret array is wiped before it returns or throws.
 */
internal fun openBitwarden(
    root: JsonObject,
    password: CharArray,
    treeBudget: Long,
    secrets: Secrets = Secrets(),
    onDecrypt: () -> Unit = {},
): ImportRead = try {
    val p = bitwardenProtected(root)
    bitwardenRead(p, bitwardenKeys(p, password, secrets), treeBudget, secrets, onDecrypt)
} finally {
    secrets.wipe()
}
