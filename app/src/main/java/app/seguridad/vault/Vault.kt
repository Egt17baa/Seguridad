package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KEY_BYTES
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.SALT_BYTES
import app.seguridad.crypto.TAG_BYTES
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.aead
import app.seguridad.crypto.appHeapKiB
import app.seguridad.crypto.argon2id
import app.seguridad.crypto.randomBytes
import app.seguridad.crypto.secureRandom
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64

@Serializable
enum class EntryType(val label: String) {
    LOGIN("Acceso"),
    SEED("Frase semilla"),
    NOTE("Nota segura"),
    TOTP("Código 2FA"),

    /** The one identity of this person for public-key encryption and signatures (see `Contacts.kt`); version-3 vaults only. */
    IDENTITY("Mi identidad"),

    /** Someone else's card, to encrypt for them and to know who signed what; version-3 vaults only. */
    CONTACT("Contacto"),
    ;

    /** [IDENTITY] and [CONTACT]: they live in the vault but have their own tab, with no passwords, attachments, history or health. */
    val isPersonal: Boolean get() = this == IDENTITY || this == CONTACT
}

/** The types the password list and the entry form offer: everything but [EntryType.isPersonal]. */
val VAULT_ENTRY_TYPES: List<EntryType> = EntryType.entries.filterNot { it.isPersonal }

@Serializable
data class VaultEntry(
    val id: String,
    val type: EntryType,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    /** Wallet recovery words, space separated. */
    val seedWords: String = "",
    val totp: TotpSecret? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean = false,
    /** Lowercase labels, see [normalizeTags]. */
    val tags: List<String> = emptyList(),
    /** Previous LOGIN passwords, newest first, at most [MAX_PASSWORD_HISTORY]; kept by [withNewPassword]. */
    val passwordHistory: List<PasswordChange> = emptyList(),
    /** Encrypted files kept in files/vault/att/ (see [AttachmentStore]); the store keeps them across edits. */
    val attachments: List<AttachmentRef> = emptyList(),
    /**
     * [EntryType.IDENTITY] only: the 32-byte identity seed in standard Base64 with padding. The `String` cannot be
     * wiped from memory (like every password in these entries): `Contacts.kt` decodes it into a short-lived array it
     * zeroes at once, and callers close each `Identity` they open.
     */
    val identitySeed: String = "",
    /** [EntryType.CONTACT] only: the canonical card bytes (`cardBytes`) in standard Base64 with padding. Read with `contactCard()`. */
    val card: String = "",
    /** [EntryType.CONTACT] only: the user compared the card in person (QR) or its fingerprint by phone. */
    val verified: Boolean = false,
    val verifiedAt: Long = 0,
    /** `"qr"` or `"fingerprint"` when [verified]; empty otherwise. */
    val verifiedBy: String = "",
)

/**
 * One encrypted attachment: its file is files/vault/att/<[id]>.bin (format v1 in [AttachmentStore]). Its own
 * random [key] lives only here, inside the entries the data key encrypts and authenticates.
 */
@Serializable
data class AttachmentRef(
    /** 16 random bytes, lowercase hex. */
    val id: String,
    /** Name to save it under ([app.seguridad.crypto.safeFileName]). */
    val name: String,
    /** Size of the content in plain. */
    val size: Long,
    /** SHA-256 of the encrypted file, lowercase hex. */
    val sha256: String,
    /** The attachment's AES-256 key, Base64. */
    val key: String,
)

@Serializable
data class PasswordChange(val password: String, val changedAt: Long)

@Serializable
data class VaultKdf(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    fun params() = KdfParams(memoryKiB, iterations, parallelism)
}

/**
 * What lives in files/vault/vault.bin (and in an exported backup). A random data key (DEK) encrypts the entries;
 * the DEK itself is stored wrapped by a key derived from the master password with Argon2id. All with AES-256-GCM.
 * Nothing in here is readable without the master password (or the device's biometric key, kept elsewhere).
 */
@Serializable
data class VaultFile(
    /**
     * [FORMAT] for every file this app writes ([VaultCrypto] always sets it, so it is always in the JSON).
     * The default is [LEGACY_FORMAT] on purpose: the old app left the key out because it was its default value,
     * so a file without it is one of those. Never change this default, or no old vault would open.
     */
    val format: String = LEGACY_FORMAT,
    /**
     * 1: only the two sealed blobs were authenticated. 2: every field is (see [VaultCrypto]). 3: the same binding
     * (the version number is one of the bound fields, so a 2 cannot pass for a 3 or the other way round) and the
     * only one that may hold [EntryType.IDENTITY] and [EntryType.CONTACT] entries.
     * Old [LEGACY_FORMAT] files left the field out when it had its default value, so a missing one
     * ([VERSION_UNKNOWN]) can be either: the version whose associated data opens the blobs is the right one
     * (see [VaultCrypto.resolve]). A [FORMAT] file always says its version: 2 (written by 1.1) or 3.
     */
    val version: Int = VERSION_UNKNOWN,
    val kdf: VaultKdf,
    /** Base64 salt for the master password. */
    val salt: String,
    /** Base64 nonce ‖ DEK encrypted with the master key. */
    val wrappedKey: String,
    /** Base64 nonce ‖ entries JSON encrypted with the DEK. */
    val data: String,
    /** Last unlock with the master password (biometrics stop working 7 days after it). */
    val masterCheckedAt: Long = 0,
    /** Bumped on every save, and authenticated with the data: an older data blob cannot be spliced in. */
    val revision: Long = 0,
) {
    companion object {
        const val FORMAT = "seguridad-vault"
        /** The older format this app imports: read only, and rewritten as [FORMAT] on import or unlock. */
        const val LEGACY_FORMAT = "criptopanel-vault"
        /**
         * The version this app writes. Version 1.1 only reads [VERSION_2] and loses nothing: with the master password it
         * says "No es una bóveda compatible"; opened with the fingerprint, a 3 without an identity or contacts shows its
         * entries, every save fails and the file does not change, except that changing the master password rewrites it as
         * a 2 holding the same entries (all of them types 1.1 knows); a 3 with an identity gives "Contraseña incorrecta o
         * datos dañados". It only happens if 1.1 is installed over 1.2, which Android does not allow for a published app
         * without uninstalling it first.
         */
        const val VERSION = 3

        /** What 1.1 wrote and 1.2 still reads: no identity or contacts, and it stays 2 until the master password is typed. */
        const val VERSION_2 = 2
        const val VERSION_UNKNOWN = 0
    }
}

private val b64 = Base64.getEncoder()
private val unb64 = Base64.getDecoder()

/** The plain fields of [f], in a fixed order, as associated data: changing any of them makes opening fail. */
private fun fieldsAad(label: String, f: VaultFile, version: Int, vararg extra: Any): ByteArray =
    (listOf(label, f.format, version, f.kdf.memoryKiB, f.kdf.iterations, f.kdf.parallelism, f.salt) + extra)
        .joinToString("\u0000").toByteArray(Charsets.UTF_8)

/**
 * Formats and versions [VaultCrypto] can open. Version 1 only ever existed in the legacy format, and the legacy
 * format never goes past 2 (CriptoPanel has no version 3).
 */
private fun supported(f: VaultFile) = when (f.format) {
    VaultFile.FORMAT -> f.version == VaultFile.VERSION_UNKNOWN || f.version == VaultFile.VERSION_2 || f.version == VaultFile.VERSION
    VaultFile.LEGACY_FORMAT -> f.version in VaultFile.VERSION_UNKNOWN..VaultFile.VERSION_2
    else -> false
}

/** Versions to try for [f]: its own, or both when the file does not say (newest first). */
private fun candidates(f: VaultFile) = when {
    f.version != VaultFile.VERSION_UNKNOWN -> listOf(f.version)
    f.format == VaultFile.LEGACY_FORMAT -> listOf(VaultFile.VERSION_2, 1)
    else -> listOf(VaultFile.VERSION, VaultFile.VERSION_2)
}

/**
 * Opens the first candidate version that authenticates. Trying version 1 is no downgrade: a blob sealed with
 * version-1 associated data only exists in a genuine version-1 file (it needs the key to make one).
 */
private fun openAny(key: ByteArray, sealed: String, f: VaultFile, aad: (Int) -> ByteArray): Pair<ByteArray, Int> {
    for (v in candidates(f)) {
        try {
            return open(key, sealed, aad(v)) to v
        } catch (e: CryptoFailure) {
            // Next candidate.
        }
    }
    throw CryptoFailure()
}

/**
 * The labels come from the file's own format, so a [VaultFile.LEGACY_FORMAT] file gets exactly the old labels
 * ("criptopanel-vault-key-v1", "criptopanel-vault-key-v2"). Versions 2 and 3 share the label and bind the format,
 * version, Argon2id cost and salt to the wrapped key: the version number inside the associated data is what tells
 * them apart, so wrapping again under a 3 needs the master password.
 */
private fun keyAad(f: VaultFile, version: Int) =
    if (version == 1) "${f.format}-key-v1".toByteArray() else fieldsAad("${f.format}-key-v2", f, version)

/**
 * Version 2 binds the same fields plus the revision and the last master check to the entries: a phone with root
 * can neither stretch the 7-day biometric window nor mix an older data blob into the current file.
 */
private fun dataAad(f: VaultFile, version: Int) =
    if (version == 1) "${f.format}-data-v1".toByteArray() else fieldsAad("${f.format}-data-v2", f, version, f.revision, f.masterCheckedAt)

/** Encrypts and then wipes [plain]. */
private fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray, random: SecureRandom, wipe: Boolean = true): String {
    val nonce = randomBytes(NONCE_BYTES, random)
    try {
        return b64.encodeToString(nonce + aead(Aead.AES_256_GCM, true, key, nonce, aad, plain))
    } finally {
        if (wipe) plain.fill(0)
    }
}

private fun open(key: ByteArray, sealed: String, aad: ByteArray): ByteArray {
    val raw = try {
        unb64.decode(sealed)
    } catch (e: IllegalArgumentException) {
        throw CryptoFailure()
    }
    if (raw.size < NONCE_BYTES + TAG_BYTES) throw CryptoFailure()
    return aead(Aead.AES_256_GCM, false, key, raw.copyOfRange(0, NONCE_BYTES), aad, raw.copyOfRange(NONCE_BYTES, raw.size))
}

private const val NOT_A_VAULT = "No es una bóveda compatible"

object VaultCrypto {
    /** A new vault: fresh DEK wrapped with [master]. Returns the file and the open DEK (caller keeps it while unlocked). */
    fun create(master: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): Pair<VaultFile, ByteArray> {
        val dek = randomBytes(KEY_BYTES, random)
        try {
            return wrap(dek, emptyList(), master, now, kdf, random) to dek
        } catch (e: Exception) {
            dek.fill(0)
            throw e
        }
    }

    /** A [VaultFile.FORMAT] version-3 file holding [entries] under [dek], with [dek] wrapped by [master] (fresh salt). */
    private fun wrap(dek: ByteArray, entries: List<VaultEntry>, master: CharArray, now: Long, kdf: KdfParams, random: SecureRandom, revision: Long = 0): VaultFile {
        val salt = randomBytes(SALT_BYTES, random)
        val kek = argon2id(master, salt, kdf)
        try {
            val plain = VaultFile(
                format = VaultFile.FORMAT, // Not the default, so it is always written out.
                version = VaultFile.VERSION, // Always written out, so it is never ambiguous again.
                kdf = VaultKdf(kdf.memoryKiB, kdf.iterations, kdf.parallelism),
                salt = b64.encodeToString(salt),
                wrappedKey = "",
                data = "",
                masterCheckedAt = now,
                revision = revision,
            )
            return plain.copy(
                wrappedKey = seal(kek, dek, keyAad(plain, VaultFile.VERSION), random, wipe = false), // The open DEK stays in use.
                data = seal(dek, encodeEntries(entries), dataAad(plain, VaultFile.VERSION), random),
            )
        } finally {
            kek.fill(0)
        }
    }

    /** The DEK, or [CryptoFailure] for a wrong master password or a damaged or altered file. Opens both formats. */
    fun unlock(file: VaultFile, master: CharArray, heapKiB: Long = appHeapKiB()): ByteArray {
        if (!supported(file)) throw CryptoFailure(NOT_A_VAULT)
        val kdf = file.kdf.params().also { it.checkSane(heapKiB) }
        val salt = try {
            unb64.decode(file.salt)
        } catch (e: IllegalArgumentException) {
            throw CryptoFailure()
        }
        val kek = argon2id(master, salt, kdf)
        try {
            return openAny(kek, file.wrappedKey, file) { keyAad(file, it) }.first.also { if (it.size != KEY_BYTES) throw CryptoFailure() }
        } finally {
            kek.fill(0)
        }
    }

    fun read(file: VaultFile, dek: ByteArray): List<VaultEntry> {
        val json = openAny(dek, file.data, file) { dataAad(file, it) }.first
        try {
            return appJson.decodeFromString<List<VaultEntry>>(json.toString(Charsets.UTF_8))
        } catch (e: kotlinx.serialization.SerializationException) {
            throw CryptoFailure()
        } finally {
            json.fill(0)
        }
    }

    /** The entries' JSON exactly as stored (decrypted, not decoded): for tests that check what was really written. */
    internal fun readJson(file: VaultFile, dek: ByteArray): String {
        val json = openAny(dek, file.data, file) { dataAad(file, it) }.first
        try {
            return json.toString(Charsets.UTF_8)
        } finally {
            json.fill(0)
        }
    }

    /** [file] with its real version, found by which associated data opens its entries. */
    fun resolve(file: VaultFile, dek: ByteArray): VaultFile {
        if (file.version != VaultFile.VERSION_UNKNOWN) return file
        val (json, version) = openAny(dek, file.data, file) { dataAad(file, it) }
        json.fill(0)
        return file.copy(version = version)
    }

    /**
     * Re-encrypts all entries with a fresh nonce and the next revision, **keeping the file's version**: a [VaultFile.FORMAT]
     * file of version 2 or 3 only (see [afterMasterUnlock]). Throws [IllegalStateException] for any other file, and for a
     * version-2 file with [EntryType.IDENTITY] or [EntryType.CONTACT] entries: those need the version 3, which needs the
     * master password (wrapping the key again), so a vault opened with the fingerprint never writes them.
     */
    fun write(file: VaultFile, dek: ByteArray, entries: List<VaultEntry>, random: SecureRandom = secureRandom): VaultFile {
        check(file.format == VaultFile.FORMAT && (file.version == VaultFile.VERSION_2 || file.version == VaultFile.VERSION)) {
            "Old vault format: unlock with the master password first"
        }
        check(file.version == VaultFile.VERSION || entries.none { it.type.isPersonal }) {
            "Identity and contacts need a version-3 vault: confirm the master password first"
        }
        val next = file.copy(revision = file.revision + 1)
        return next.copy(data = seal(dek, encodeEntries(entries), dataAad(next, file.version), random))
    }

    /**
     * After the master password opened [file]: records the check (re-sealing, since it is authenticated) and turns a
     * legacy file (any version), a version-1 file or a version-2 file into [VaultFile.FORMAT] version 3, which needs
     * the master password once more to wrap the key with the new binding. The data key stays the same (a fingerprint
     * set up with it keeps working) and the revision goes up by one. A version-3 file is only re-sealed.
     */
    fun afterMasterUnlock(file: VaultFile, dek: ByteArray, master: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): VaultFile {
        val known = resolve(file, dek)
        val entries = read(known, dek)
        return if (known.format != VaultFile.FORMAT || known.version != VaultFile.VERSION) {
            wrap(dek, entries, master, now, kdf, random, known.revision + 1)
        } else {
            write(known.copy(masterCheckedAt = now), dek, entries, random)
        }
    }

    /**
     * Same entries under a new master password, new salt and a **new data key**: an old export plus the old (maybe
     * leaked) password must not open anything saved after the change. Returns the file and the new open DEK.
     */
    fun changeMaster(file: VaultFile, dek: ByteArray, newMaster: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): Pair<VaultFile, ByteArray> {
        val entries = read(file, dek)
        val newDek = randomBytes(KEY_BYTES, random)
        try {
            return wrap(newDek, entries, newMaster, now, kdf, random, file.revision + 1) to newDek
        } catch (e: Exception) {
            newDek.fill(0)
            throw e
        }
    }

    private fun encodeEntries(entries: List<VaultEntry>): ByteArray = appJson.encodeToString(entries).toByteArray(Charsets.UTF_8)

    fun encodeFile(file: VaultFile): String = appJson.encodeToString(VaultFile.serializer(), file)

    fun decodeFile(text: String): VaultFile = try {
        appJson.decodeFromString(VaultFile.serializer(), text)
    } catch (e: Exception) {
        throw CryptoFailure(NOT_A_VAULT)
    }

    /**
     * Whether a biometric unlock may proceed: only for [VaultFile.FORMAT] (a legacy file needs the master password
     * once to be converted), not for version 1 (date not authenticated; an unknown version is [resolve]d with the
     * key first), within 7 days of the last master check and never with a date in the future.
     */
    fun biometricAllowed(file: VaultFile, now: Long): Boolean =
        file.format == VaultFile.FORMAT && file.version != 1 && now - file.masterCheckedAt in 0 until MASTER_RECHECK_MS
}

/** Biometrics need the master password again after this long. */
const val MASTER_RECHECK_MS = 7L * 24 * 60 * 60_000

/** Words in a seed phrase, and whether the count is one BIP-39 allows. */
fun seedWordCount(words: String): Pair<Int, Boolean> {
    val n = words.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    return n to (n in setOf(12, 15, 18, 21, 24))
}
