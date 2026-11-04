package app.seguridad.imports

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * 2FAS's encrypted backup (spec «2FAS»). No format document from 2FAS itself was found; written from Clavero's design
 * (`servicesEncrypted` = Base64(ciphertext and tag):Base64(salt):Base64(IV), PBKDF2-HMAC-SHA256, AES-256-GCM) and two
 * published READMEs, never their code:
 * - https://github.com/AlexanderQueen/2FAS-GUI-Backup-Decryption-Tool (PBKDF2-HMAC-SHA256, 10 000 iterations, a 256-bit
 *   key, AES-GCM);
 * - https://github.com/elliotwutingfeng/2fas-backup-decryptor (what it opens is the JSON array of the services).
 * Not yet confirmed against a real export (Task 10 report, «Pendiente de confirmar»): the fixed iteration count (the
 * file carries none), the IV's length (12 bytes), the salt's length, that the password is hashed in UTF-8, and that
 * `groups` stays in clear beside `servicesEncrypted`.
 */

/** The iterations 2FAS uses (documented in the README above); the file does not say them. */
const val TWOFAS_PBKDF2_ITERATIONS = 10_000

private const val KEY_BYTES = 32
private const val IV_BYTES = 12
private const val TAG_BYTES = 16
private const val SALT_MIN_BYTES = 8
private const val SALT_MAX_BYTES = 1024

/** `servicesEncrypted`, decoded: [sealed] is the ciphertext with its 16-byte tag at the end. Not secret. */
internal data class TwoFasEncrypted(val sealed: ByteArray, val salt: ByteArray, val iv: ByteArray)

/**
 * `servicesEncrypted` before any work (owner's decision (a)): bounded before it is decoded
 * ([IMPORT_MAX_ENCRYPTED_CHARS]); three parts, a 12-byte IV, a salt of 8 to 1024 bytes and a ciphertext at least as
 * long as its tag, or [IMPORT_BROKEN]. The iterations are fixed ([TWOFAS_PBKDF2_ITERATIONS]) and checked too.
 */
internal fun twoFasEncrypted(root: JsonObject): TwoFasEncrypted {
    val text = try {
        root.textOrNull("servicesEncrypted")
    } catch (e: WrongType) {
        null
    } ?: throw ImportFailure(IMPORT_BROKEN)
    if (text.length > IMPORT_MAX_ENCRYPTED_CHARS) throw ImportFailure(IMPORT_ENCRYPTED_TOO_BIG)
    val parts = text.split(':')
    if (parts.size != 3) throw ImportFailure(IMPORT_BROKEN)
    val sealed = importBase64(parts[0])
    val salt = importBase64(parts[1])
    val iv = importBase64(parts[2])
    if (sealed.size < TAG_BYTES || salt.size !in SALT_MIN_BYTES..SALT_MAX_BYTES || iv.size != IV_BYTES) {
        throw ImportFailure(IMPORT_BROKEN)
    }
    checkPbkdf2Iterations(TWOFAS_PBKDF2_ITERATIONS)
    return TwoFasEncrypted(sealed, salt, iv)
}

/** The key of [e] from [password]: PBKDF2-HMAC-SHA256, 32 bytes; the password's bytes and the key are in [s]. */
internal fun twoFasKey(e: TwoFasEncrypted, password: CharArray, s: Secrets): ByteArray =
    pbkdf2Sha256(importPasswordBytes(password, s), e.salt, TWOFAS_PBKDF2_ITERATIONS, KEY_BYTES, s)

/** The services' plaintext, opened with [key] (AES-256-GCM, its tag checked: [IMPORT_WRONG_PASSWORD]); in [s]. */
internal fun twoFasOpen(e: TwoFasEncrypted, key: ByteArray, s: Secrets): ByteArray = aesGcmOpen(key, e.iv, e.sealed, s)

/**
 * The services of [e], read as data like a plain backup: [decryptedJson] against [treeBudget], which must give an
 * array (anything else is [IMPORT_BROKEN]), then [readTwoFasPlain] with its item limits and with [groups] (the file's,
 * in clear) as the folders.
 */
internal fun twoFasRead(e: TwoFasEncrypted, key: ByteArray, groups: JsonElement?, treeBudget: Long, s: Secrets): ImportRead {
    val services = decryptedJson(twoFasOpen(e, key, s), treeBudget) as? JsonArray ?: throw ImportFailure(IMPORT_BROKEN)
    val root = if (groups == null) mapOf("services" to services) else mapOf("services" to services, "groups" to groups)
    return readTwoFasPlain(JsonObject(root))
}

/**
 * An encrypted backup ([root]) opened with [password] (the caller's: not wiped here). Every secret array is wiped
 * before it returns or throws.
 */
internal fun openTwoFas(root: JsonObject, password: CharArray, treeBudget: Long, secrets: Secrets = Secrets()): ImportRead = try {
    val e = twoFasEncrypted(root)
    twoFasRead(e, twoFasKey(e, password, secrets), root["groups"], treeBudget, secrets)
} finally {
    secrets.wipe()
}
