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
enum class EntryType(val label: String) { LOGIN("Acceso"), SEED("Frase semilla"), NOTE("Nota segura"), TOTP("Código 2FA") }

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
     * 1: only the two sealed blobs were authenticated. 2: every field is (see [VaultCrypto]).
     * Old [LEGACY_FORMAT] files left the field out when it had its default value, so a missing one
     * ([VERSION_UNKNOWN]) can be either: the version whose associated data opens the blobs is the right one
     * (see [VaultCrypto.resolve]). A [FORMAT] file is always version 2.
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
        const val VERSION = 2
        const val VERSION_UNKNOWN = 0
    }
}

private val b64 = Base64.getEncoder()
private val unb64 = Base64.getDecoder()

/** The plain fields of [f], in a fixed order, as associated data: changing any of them makes opening fail. */
private fun fieldsAad(label: String, f: VaultFile, version: Int, vararg extra: Any): ByteArray =
    (listOf(label, f.format, version, f.kdf.memoryKiB, f.kdf.iterations, f.kdf.parallelism, f.salt) + extra)
        .joinToString("\u0000").toByteArray(Charsets.UTF_8)

/** Formats and versions [VaultCrypto] can open. Version 1 only ever existed in the legacy format. */
private fun supported(f: VaultFile) = when (f.format) {
    VaultFile.FORMAT -> f.version == VaultFile.VERSION_UNKNOWN || f.version == VaultFile.VERSION
    VaultFile.LEGACY_FORMAT -> f.version in VaultFile.VERSION_UNKNOWN..VaultFile.VERSION
    else -> false
}

/** Versions to try for [f]: its own, or (legacy files only) both when the file does not say (newest first). */
private fun candidates(f: VaultFile) = when {
    f.version != VaultFile.VERSION_UNKNOWN -> listOf(f.version)
    f.format == VaultFile.LEGACY_FORMAT -> listOf(VaultFile.VERSION, 1)
    else -> listOf(VaultFile.VERSION)
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
 * ("criptopanel-vault-key-v1", "criptopanel-vault-key-v2"). Version 2 binds the format, version, Argon2id cost
 * and salt to the wrapped key.
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

    /** A [VaultFile.FORMAT] version-2 file holding [entries] under [dek], with [dek] wrapped by [master] (fresh salt). */
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

    /** [file] with its real version, found by which associated data opens its entries. */
    fun resolve(file: VaultFile, dek: ByteArray): VaultFile {
        if (file.version != VaultFile.VERSION_UNKNOWN) return file
        val (json, version) = openAny(dek, file.data, file) { dataAad(file, it) }
        json.fill(0)
        return file.copy(version = version)
    }

    /**
     * Re-encrypts all entries with a fresh nonce and the next revision. [VaultFile.FORMAT] version 2 only
     * (see [afterMasterUnlock]).
     */
    fun write(file: VaultFile, dek: ByteArray, entries: List<VaultEntry>, random: SecureRandom = secureRandom): VaultFile {
        check(file.format == VaultFile.FORMAT && file.version == VaultFile.VERSION) { "Old vault format: unlock with the master password first" }
        val next = file.copy(revision = file.revision + 1)
        return next.copy(data = seal(dek, encodeEntries(entries), dataAad(next, VaultFile.VERSION), random))
    }

    /**
     * After the master password opened [file]: records the check (re-sealing, since it is authenticated) and turns a
     * legacy file (any version) or a version-1 file into [VaultFile.FORMAT] version 2, which needs the master
     * password once more to wrap the key with the new binding. The data key stays the same (a fingerprint set up
     * with it keeps working) and the revision goes up by one.
     */
    fun afterMasterUnlock(file: VaultFile, dek: ByteArray, master: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): VaultFile {
        val known = resolve(file, dek)
        val entries = read(known, dek)
        return if (known.format != VaultFile.FORMAT || known.version == 1) {
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
