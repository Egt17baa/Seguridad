package app.seguridad.imports

import kotlinx.serialization.json.JsonObject

/*
 * Aegis's encrypted vault export (spec «Aegis»), written only from its published format document,
 * https://github.com/beemdevelopment/Aegis/blob/master/docs/vault.md (no code read or copied):
 * - `header.slots`: each slot holds the 256-bit master key wrapped with AES-256-GCM (`key`, `key_params.nonce` and
 *   `key_params.tag`, in hexadecimal); a password slot (`type` 1) derives its wrapping key with scrypt from the
 *   password, its `salt` (hexadecimal, 256 bits) and its `n`, `r` and `p`;
 * - `header.params`: the `nonce` and `tag` of the AES-256-GCM encryption of `db`, in hexadecimal;
 * - `db`: the vault's content, Base64 with padding, a UTF-8 JSON object (version 3, `entries` and `groups`).
 * Not yet confirmed against a real export (docs/superpowers/handoff/sdd-1.4.0/task-10-report.md, «Pendiente de
 * confirmar»): that the password is hashed in UTF-8, that GCM takes no associated data, and the salt's length in other
 * versions (16 to 64 bytes are read).
 */

/**
 * Most slots read in a header, and most password slots tried (ruling R24): Aegis keeps one password per vault
 * (vault.md: one slot per credential), so one scrypt per attempt (owner's decision (a)); a file with more is read
 * through its first.
 */
private const val AEGIS_MAX_SLOTS = 16
private const val AEGIS_MAX_PASSWORD_SLOTS = 1
private const val SLOT_PASSWORD = 1
private const val MASTER_KEY_BYTES = 32
private const val NONCE_BYTES = 12
private const val TAG_BYTES = 16
private const val SALT_MIN_BYTES = 16
private const val SALT_MAX_BYTES = 64

/** A password slot, every value checked ([aegisEncrypted]) before any work. [sealedKey] is `key` ‖ `key_params.tag`. */
internal data class AegisSlot(
    val n: Int,
    val r: Int,
    val p: Int,
    val salt: ByteArray,
    val sealedKey: ByteArray,
    val nonce: ByteArray,
)

/**
 * An encrypted export: the password slot it is opened with and the encrypted vault, [sealed] being `db` ‖
 * `header.params.tag`, decoded into one array so no second copy of the vault is made.
 */
internal data class AegisEncrypted(val slots: List<AegisSlot>, val nonce: ByteArray, val sealed: ByteArray)

/**
 * The header and `db` of an encrypted export, before any work (owner's decision (a)): every password slot's scrypt
 * parameters in range ([checkScrypt]: [IMPORT_KDF_TOO_BIG]), all of them before the first runs; the hexadecimal values
 * of their exact lengths; `db` bounded before it is decoded ([IMPORT_MAX_ENCRYPTED_CHARS]). Slots of other types
 * (biometric, raw) are passed over, and only the first [AEGIS_MAX_PASSWORD_SLOTS] password slot is kept (ruling R24).
 * No password slot, more than [AEGIS_MAX_SLOTS] slots or anything missing or of the wrong type is [IMPORT_BROKEN].
 */
internal fun aegisEncrypted(root: JsonObject): AegisEncrypted = try {
    val header = root.obj("header") ?: throw WrongType()
    val list = header.list("slots") ?: throw WrongType()
    if (list.size > AEGIS_MAX_SLOTS) throw WrongType()
    val slots = list.mapNotNull { e ->
        val o = e as? JsonObject ?: throw WrongType()
        if (o.number("type") != SLOT_PASSWORD) return@mapNotNull null
        val n = o.number("n") ?: throw WrongType()
        val r = o.number("r") ?: throw WrongType()
        val p = o.number("p") ?: throw WrongType()
        checkScrypt(n, r, p)
        val saltHex = o.textOrNull("salt") ?: throw WrongType()
        if (saltHex.length % 2 != 0 || saltHex.length / 2 !in SALT_MIN_BYTES..SALT_MAX_BYTES) throw WrongType()
        val keyParams = o.obj("key_params") ?: throw WrongType()
        AegisSlot(
            n, r, p,
            salt = importHex(saltHex, saltHex.length / 2),
            sealedKey = importHex(o.textOrNull("key") ?: throw WrongType(), MASTER_KEY_BYTES) +
                importHex(keyParams.textOrNull("tag") ?: throw WrongType(), TAG_BYTES),
            nonce = importHex(keyParams.textOrNull("nonce") ?: throw WrongType(), NONCE_BYTES),
        )
    }
    if (slots.isEmpty()) throw WrongType()
    val params = header.obj("params") ?: throw WrongType()
    val nonce = importHex(params.textOrNull("nonce") ?: throw WrongType(), NONCE_BYTES)
    val tag = importHex(params.textOrNull("tag") ?: throw WrongType(), TAG_BYTES)
    // `db` decoded with room for its tag after it: GCM takes ciphertext ‖ tag, and the vault is not copied to add it.
    val sealed = importBase64(root.textOrNull("db") ?: throw WrongType(), spare = TAG_BYTES)
    if (sealed.size == TAG_BYTES) throw WrongType()
    tag.copyInto(sealed, sealed.size - TAG_BYTES)
    AegisEncrypted(slots.take(AEGIS_MAX_PASSWORD_SLOTS), nonce, sealed)
} catch (e: WrongType) {
    throw ImportFailure(IMPORT_BROKEN)
}

/** The scrypt key of [slot] from [password] (32 bytes); the password's bytes and the key are in [s]. */
internal fun aegisSlotKey(slot: AegisSlot, password: CharArray, s: Secrets): ByteArray =
    scryptKey(importPasswordBytes(password, s), slot.salt, slot.n, slot.r, slot.p, MASTER_KEY_BYTES, s)

/** The master key [slot] wraps, opened with [derived] (AES-256-GCM, its tag checked); null when the tag fails. */
internal fun aegisUnwrap(slot: AegisSlot, derived: ByteArray, s: Secrets): ByteArray? = try {
    aesGcmOpen(derived, slot.nonce, slot.sealedKey, s)
} catch (e: ImportFailure) {
    if (e.message == IMPORT_WRONG_PASSWORD) null else throw e
}

/** The vault's plaintext, opened with [master] (AES-256-GCM, its tag checked: [IMPORT_WRONG_PASSWORD]); in [s]. */
internal fun aegisDb(e: AegisEncrypted, master: ByteArray, s: Secrets, onDecrypt: () -> Unit = {}): ByteArray {
    onDecrypt()
    return aesGcmOpen(master, e.nonce, e.sealed, s)
}

/**
 * An encrypted export ([root]) opened with [password] (the caller's: not wiped here): the header checked, its first
 * password slot tried (one scrypt, ruling R24; [onDerive] is called right before it, for the tests); if it does not
 * open the master key, [IMPORT_WRONG_PASSWORD] and `db` is never decrypted; then `db` decrypted and read as data like
 * a plain export: [decryptedJson] against [treeBudget], then [readAegisPlain] with its item limits. Every secret array
 * is wiped before it returns or throws.
 */
internal fun openAegis(
    root: JsonObject,
    password: CharArray,
    treeBudget: Long,
    secrets: Secrets = Secrets(),
    onDerive: () -> Unit = {},
    onDecrypt: () -> Unit = {},
): ImportRead = try {
    val e = aegisEncrypted(root)
    val master = e.slots.firstNotNullOfOrNull {
        onDerive()
        aegisUnwrap(it, aegisSlotKey(it, password, secrets), secrets)
    } ?: throw ImportFailure(IMPORT_WRONG_PASSWORD)
    val content = decryptedJson(aegisDb(e, master, secrets, onDecrypt), treeBudget) as? JsonObject
        ?: throw ImportFailure(IMPORT_BROKEN)
    readAegisPlain(content)
} finally {
    secrets.wipe()
}
