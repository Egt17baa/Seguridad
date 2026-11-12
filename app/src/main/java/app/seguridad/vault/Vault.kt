package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KEY_BYTES
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.PasswordUse
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
enum class EntryType(
    val label: String,
    /** 2.0: only a version-4 vault holds it (the templates of task 5); no earlier version knows it. */
    val v4Only: Boolean = false,
) {
    LOGIN("Acceso"),
    SEED("Frase semilla"),
    NOTE("Nota"),
    TOTP("Código 2FA"),

    /** The one identity of this person for public-key encryption and signatures (see `Contacts.kt`); version-3 vaults only. */
    IDENTITY("Mi identidad"),

    /** Someone else's card, to encrypt for them and to know who signed what; version-3 vaults only. */
    CONTACT("Contacto"),

    /** 2.0, templates (task 5): offered nowhere yet ([VAULT_ENTRY_TYPES]). */
    CARD("Tarjeta", v4Only = true),
    WIFI("Wifi", v4Only = true),
    LICENSE("Licencia", v4Only = true),
    PASSPORT("Pasaporte", v4Only = true),
    ;

    /** [IDENTITY] and [CONTACT]: they live in the vault but have their own tab, with no passwords, attachments, history or health. */
    val isPersonal: Boolean get() = this == IDENTITY || this == CONTACT
}

/**
 * The types the password list and the entry form offer: everything but [EntryType.isPersonal] and, until their templates
 * exist (task 5), the [EntryType.v4Only] ones.
 */
val VAULT_ENTRY_TYPES: List<EntryType> = EntryType.entries.filterNot { it.isPersonal || it.v4Only }

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
    // 2.0 (format v4): all with a default, so an entry of version 2 or 3 reads the same and is written the same.
    /** The [Folder.id] it is in; null for none. */
    val folderId: String? = null,
    /** Fields the user added ([VaultLimits]: at most 30). */
    val fields: List<CustomField> = emptyList(),
    val notesFormat: NotesFormat = NotesFormat.PLAIN,
    /** Earlier versions of this entry ([fitted] keeps it within its limits), each without history or attachments. */
    val history: List<EntryVersion> = emptyList(),
    /** When it went to the trash; null when it is not there. */
    val deletedAt: Long? = null,
    val expiresAt: Long? = null,
    /** Change the password every this many months; null for no reminder. */
    val rotateMonths: Int? = null,
    val lastUsedAt: Long? = null,
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
     * [FORMAT] in every file this app writes ([VaultCrypto] always sets it). No default, on purpose: a file without
     * it is not a vault of this app, and [VaultCrypto.decodeFile] refuses it as «No es una bóveda compatible» before
     * any key is derived. Until 1.3.0 a missing key meant CriptoPanel's vault, which is no longer read (owner's
     * decision, 1-11-2026); this app never left it out ([VaultCrypto] has written it since its first version).
     */
    val format: String,
    /**
     * 2: every field is authenticated (see [VaultCrypto]). 3: the same binding (the version number is one of the bound
     * fields, so a 2 cannot pass for a 3 or the other way round) and the first that may hold [EntryType.IDENTITY]
     * and [EntryType.CONTACT] entries. 4 (2.0): the same binding again, and the data blob is a [VaultData] instead of a
     * list of entries. This app always writes it: 2 (1.0 and 1.1), 3 (1.2 to 1.4.3) or 4. A file without it
     * ([VERSION_UNKNOWN], edited by hand) is tried as 3 and as 2, never as 4 (a 4 always says it): the version whose
     * associated data opens the blobs is the right one (see [VaultCrypto.resolve]).
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

        /**
         * The version this app writes (2.0). 1.2 to 1.4.3 read only 2 and 3: a 4 gets «No es una bóveda compatible»
         * with the master password and no fingerprint ([BiometricRefusal.UNSUPPORTED]), and nothing changes. That only
         * happens if an earlier version is installed over 2.0, which Android does not allow for a published app without
         * uninstalling it first; the unlock screen says so before the upgrade (Abogado Digital, O-3).
         */
        const val VERSION = 4

        /**
         * What 1.2 to 1.4.3 wrote. Version 1.1 only reads [VERSION_2] and loses nothing: with the master password it
         * says "No es una bóveda compatible"; opened with the fingerprint, a 3 without an identity or contacts shows its
         * entries, every save fails and the file does not change, except that changing the master password rewrites it as
         * a 2 holding the same entries (all of them types 1.1 knows); a 3 with an identity gives "Contraseña incorrecta o
         * datos dañados". 2.0 reads it and passes it to [VERSION] at the first unlock with the master password.
         */
        const val VERSION_3 = 3

        /** What 1.1 wrote: no identity or contacts. 2.0 reads it and passes it to [VERSION] as a 3. */
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

/** What [VaultCrypto] can open: a [VaultFile.FORMAT] file of version 2, 3 or 4, or one that does not say its version. */
private fun supported(f: VaultFile) = f.format == VaultFile.FORMAT && f.version in READ_VERSIONS

private val READ_VERSIONS = setOf(VaultFile.VERSION_UNKNOWN, VaultFile.VERSION_2, VaultFile.VERSION_3, VaultFile.VERSION)

/** Versions to try for [f]: its own, or 3 and 2 when the file does not say (newest first); never 4, which always says it. */
private fun candidates(f: VaultFile) =
    if (f.version != VaultFile.VERSION_UNKNOWN) listOf(f.version) else listOf(VaultFile.VERSION_3, VaultFile.VERSION_2)

/**
 * Opens the first candidate version that authenticates. Trying the 2 after the 3 is no downgrade: the version is one
 * of the bound fields, so a blob only opens under the version it was sealed with.
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
 * The label comes from the file's own format ("seguridad-vault-key-v2"). Versions 2, 3 and 4 share it and bind the format,
 * version, Argon2id cost and salt to the wrapped key: the version number inside the associated data is what tells
 * them apart, so wrapping again under a 4 needs the master password. Internal only so a test can wrap the real 1.1.1
 * fixture under the key Bouncy Castle 1.80 derived (`PasswordBytesTest`, review F5, m3).
 */
internal fun keyAad(f: VaultFile, version: Int) = fieldsAad("${f.format}-key-v2", f, version)

/**
 * Version 2 binds the same fields plus the revision and the last master check to the entries: a phone with root
 * can neither stretch the 7-day biometric window nor mix an older data blob into the current file.
 * Internal only so a test can seal entries that authenticate but do not decode (final review 1.3.1, F1).
 */
internal fun dataAad(f: VaultFile, version: Int) = fieldsAad("${f.format}-data-v2", f, version, f.revision, f.masterCheckedAt)

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

/** What [VaultCrypto] says of a file it cannot open; the unlock screen shows it, not «Contraseña incorrecta» (F2). */
internal const val NOT_A_VAULT = "No es una bóveda compatible"

/** 2.0: the unlock screen's line for [BiometricRefusal.UPGRADE] (design «Formato v4»). */
const val UPGRADE_NEEDS_MASTER = "Clavero 2.0 necesita la maestra una vez para pasar la bóveda al formato nuevo."

private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

/**
 * 2.0, the upgrade to v4: each LOGIN's [VaultEntry.passwordHistory] also becomes [VaultEntry.history], one
 * [EntryVersion] per old password, with its date (the entry as it is, with that password; without history or
 * attachments). [VaultEntry.passwordHistory] is kept, as the entry screen and «Salud» still read it until the history of
 * task 3 replaces them. A missing [VaultData.vaultTag] is made (16 random bytes in hex).
 */
private fun VaultData.upgraded(random: SecureRandom): VaultData = copy(
    entries = entries.map { e ->
        if (e.passwordHistory.isEmpty() || e.history.isNotEmpty()) {
            e
        } else {
            val base = e.copy(history = emptyList(), attachments = emptyList(), passwordHistory = emptyList())
            e.copy(history = e.passwordHistory.map { EntryVersion(it.changedAt, base.copy(password = it.password)) })
        }
    },
    vaultTag = vaultTag.ifEmpty { newVaultTag(random) },
)

private fun newVaultTag(random: SecureRandom): String = hex(randomBytes(16, random))

object VaultCrypto {
    /** A new vault: fresh DEK wrapped with [master]. Returns the file and the open DEK (caller keeps it while unlocked). */
    fun create(master: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): Pair<VaultFile, ByteArray> {
        val dek = randomBytes(KEY_BYTES, random)
        try {
            return wrap(dek, VaultData(vaultTag = newVaultTag(random)), master, PasswordUse.NEW, now, kdf, random) to dek
        } catch (e: Exception) {
            dek.fill(0)
            throw e
        }
    }

    /**
     * A [VaultFile.FORMAT] version-4 file holding [data] under [dek], with [dek] wrapped by [master] (fresh salt). [use]
     * is [PasswordUse.NEW] for a new vault or a new master password, and [PasswordUse.EXISTING] when wrapping again under
     * the master that has just opened the file (review F5, M1: a 1.0.x/1.1.x master must keep working after the upgrade).
     */
    private fun wrap(dek: ByteArray, data: VaultData, master: CharArray, use: PasswordUse, now: Long, kdf: KdfParams, random: SecureRandom, revision: Long = 0): VaultFile {
        val salt = randomBytes(SALT_BYTES, random)
        val kek = argon2id(master, salt, kdf, use = use)
        try {
            val plain = VaultFile(
                format = VaultFile.FORMAT, // Required (no default), so it is always written out.
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
                data = seal(dek, encodeData(data), dataAad(plain, VaultFile.VERSION), random),
            )
        } finally {
            kek.fill(0)
        }
    }

    /** The DEK, or [CryptoFailure] for a wrong master password or a damaged or altered file. */
    fun unlock(file: VaultFile, master: CharArray, heapKiB: Long = appHeapKiB()): ByteArray {
        if (!supported(file)) throw CryptoFailure(NOT_A_VAULT)
        val kdf = file.kdf.params().also { it.checkSane(heapKiB) }
        val salt = try {
            unb64.decode(file.salt)
        } catch (e: IllegalArgumentException) {
            throw CryptoFailure()
        }
        val kek = argon2id(master, salt, kdf, use = PasswordUse.EXISTING)
        try {
            return openAny(kek, file.wrappedKey, file) { keyAad(file, it) }.first.also { if (it.size != KEY_BYTES) throw CryptoFailure() }
        } finally {
            kek.fill(0)
        }
    }

    /**
     * The vault's data: a [VaultData] for a version-4 file, and `VaultData(entries = list)` for one of version 2 or 3
     * (or without a version). [CryptoFailure] for a key that does not open it, or data that does not decode: also JSON
     * nested deeper than [VaultLimits.JSON_MAX_DEPTH], refused before it is decoded. The versions in
     * [VaultEntry.history] come back without history or attachments of their own, whatever the file says.
     */
    fun read(file: VaultFile, dek: ByteArray): VaultData {
        val (json, version) = openAny(dek, file.data, file) { dataAad(file, it) }
        try {
            if (!jsonNestingWithin(json)) throw CryptoFailure()
            val text = json.toString(Charsets.UTF_8)
            val data = if (version == VaultFile.VERSION) {
                appJson.decodeFromString(VaultData.serializer(), text)
            } else {
                VaultData(entries = appJson.decodeFromString<List<VaultEntry>>(text))
            }
            return data.withPlainVersions()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw CryptoFailure()
        } catch (e: IllegalArgumentException) {
            throw CryptoFailure() // A value the serializer refused (kotlinx's own errors are of this kind too).
        } finally {
            json.fill(0)
        }
    }

    /** The data's JSON exactly as stored (decrypted, not decoded): for tests that check what was really written. */
    internal fun readJson(file: VaultFile, dek: ByteArray): String {
        val json = openAny(dek, file.data, file) { dataAad(file, it) }.first
        try {
            return json.toString(Charsets.UTF_8)
        } finally {
            json.fill(0)
        }
    }

    /** True if this version can open [file] at all (its format and version); nothing is decrypted. */
    fun opens(file: VaultFile): Boolean = supported(file)

    /**
     * [file] with its real version if [dek] authenticates its entries under one of the candidate versions (AES-256-GCM
     * only: the JSON is not decoded and the decrypted bytes are wiped), or null if it authenticates under none: the key
     * is another vault's, or the blob is damaged (AES-GCM cannot tell those two apart). What the fingerprint needs to
     * tell a stale key from entries this version cannot decode ([VaultStore.unlockWithKey], F1).
     */
    fun authenticated(file: VaultFile, dek: ByteArray): VaultFile? {
        val (json, version) = try {
            openAny(dek, file.data, file) { dataAad(file, it) }
        } catch (e: CryptoFailure) {
            return null
        }
        json.fill(0)
        return file.copy(version = version)
    }

    /** [file] with its real version, found by which associated data opens its entries. */
    fun resolve(file: VaultFile, dek: ByteArray): VaultFile {
        if (file.version != VaultFile.VERSION_UNKNOWN) return file
        val (json, version) = openAny(dek, file.data, file) { dataAad(file, it) }
        json.fill(0)
        return file.copy(version = version)
    }

    /**
     * Re-encrypts [data] with a fresh nonce and the next revision: a [VaultFile.FORMAT] file of version 4 only. Throws
     * [IllegalStateException] for any other file: a 2 or a 3 passes to 4 only with the master password, which wraps the
     * key again ([afterMasterUnlock]), so this app never writes a 2 or a 3 again. [data] is written as it is: the store
     * fits it first ([fittedForSave]).
     */
    fun write(file: VaultFile, dek: ByteArray, data: VaultData, random: SecureRandom = secureRandom): VaultFile {
        check(file.format == VaultFile.FORMAT && file.version == VaultFile.VERSION) {
            "Old vault format: unlock with the master password first"
        }
        val next = file.copy(revision = file.revision + 1)
        return next.copy(data = seal(dek, encodeData(data), dataAad(next, file.version), random))
    }

    /**
     * After the master password opened [file]: records the check (re-sealing, since it is authenticated) and always
     * gives a version-4 file. A file of version 2 or 3 (or without a version) passes to 4, which needs the master password
     * once more to wrap the key with the new binding, and its data is [upgraded] (the old passwords also in the history,
     * a [VaultData.vaultTag]) and [fitted]. The data key stays the same (a fingerprint set up with it keeps working) and
     * the revision goes up by one. No limit of [VaultLimits] refuses it: an old vault always passes. A
     * version-4 file is only re-sealed, unless its Argon2id is cheaper than [kdf] in memory or passes: then the key is
     * wrapped again with the cost asked, same data key, revision + 1. That happens at any unlock with the master
     * password, on purpose: after an import of a copy made with a lower cost (A8), and also after a higher cost was
     * chosen in Ajustes, which takes effect at the next unlock with the master password (that unlock then runs Argon2id
     * twice: once to open with the old cost, once to wrap with the new one). If saving that fails ([VaultStore] opens
     * anyway), the vault in memory already has the new cost and any later save writes it; only if no save ever succeeds
     * does the next start do it again, never silently lowering anything. [VaultStore.confirmMaster] (a check inside an
     * open session) does not do it: it only checks, so a save the user is waiting for is not delayed by a second
     * Argon2id; the next unlock does it. [VaultStore.unlock] keeps a 4 made from a 3 in memory until the copy before it is
     * made or declined ([VaultStore.pendingUpgrade]).
     *
     * Whatever is wrapped again never costs less than the file did on any axis (CR-2): memory, passes and parallelism
     * are each the higher of the file's and [kdf]'s. A lower parallelism alone is not «cheaper»: Argon2id's cost to an
     * attacker is its memory and its passes (the lanes split that work across threads, RFC 9106 §4), so it is not a reason to
     * wrap again, but it is never lowered when wrapping for another reason.
     */
    fun afterMasterUnlock(file: VaultFile, dek: ByteArray, master: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): VaultFile {
        val known = resolve(file, dek)
        val old = known.version != VaultFile.VERSION
        val read = read(known, dek)
        val data = if (old) read.upgraded(random).fitted() else read.copy(vaultTag = read.vaultTag.ifEmpty { newVaultTag(random) })
        val weaker = known.kdf.memoryKiB < kdf.memoryKiB || known.kdf.iterations < kdf.iterations
        // Whatever is wrapped again never costs less than the file did on any axis (a stronger old file keeps its strength).
        val cost = KdfParams(
            maxOf(known.kdf.memoryKiB, kdf.memoryKiB),
            maxOf(known.kdf.iterations, kdf.iterations),
            maxOf(known.kdf.parallelism, kdf.parallelism),
        )
        return if (known.format != VaultFile.FORMAT || old || weaker) {
            wrap(dek, data, master, PasswordUse.EXISTING, now, cost, random, known.revision + 1)
        } else {
            write(known.copy(masterCheckedAt = now), dek, data, random)
        }
    }

    /**
     * Same data under a new master password, new salt and a **new data key**, as a version-4 file: an old export plus
     * the old (maybe leaked) password must not open anything saved after the change. Returns the file and the new open DEK.
     */
    fun changeMaster(file: VaultFile, dek: ByteArray, newMaster: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): Pair<VaultFile, ByteArray> {
        val read = read(file, dek)
        val data = if (resolve(file, dek).version == VaultFile.VERSION) read else read.upgraded(random).fitted()
        val newDek = randomBytes(KEY_BYTES, random)
        try {
            return wrap(newDek, data, newMaster, PasswordUse.NEW, now, kdf, random, file.revision + 1) to newDek
        } catch (e: Exception) {
            newDek.fill(0)
            throw e
        }
    }

    private fun encodeData(data: VaultData): ByteArray = appJson.encodeToString(VaultData.serializer(), data).toByteArray(Charsets.UTF_8)

    fun encodeFile(file: VaultFile): String = appJson.encodeToString(VaultFile.serializer(), file)

    fun decodeFile(text: String): VaultFile = try {
        appJson.decodeFromString(VaultFile.serializer(), text)
    } catch (e: Exception) {
        throw CryptoFailure(NOT_A_VAULT)
    }

    /**
     * Whether a biometric unlock may proceed: only for a version-4 file, within 7 days of the last master check and never
     * with a date in the future. A file [unlock] would open but of version 2 or 3, or without a version, is
     * [BiometricRefusal.UPGRADE], before the clock and the 7 days: passing it to 4 wraps the key again, which needs the
     * master password (2.0, design «Formato v4»). Any other format or version (1, 5…) is
     * [BiometricRefusal.UNSUPPORTED]: the unlock screen offers no fingerprint and shows no notice for it, the key is
     * never tried on it, and the master password then gets «No es una bóveda compatible» ([NOT_A_VAULT]) instead of
     * «Contraseña incorrecta». [highWater] is the highest time the app ever saw (a mark that only rises): a clock set
     * back more than [CLOCK_SLACK_MS] behind it asks for the master password, so setting the clock back no longer
     * stretches the 7 days.
     */
    fun biometricAllowed(file: VaultFile, now: Long, highWater: Long = 0): Boolean = biometricRefusal(file, now, highWater) == null

    /** Why [biometricAllowed] says no, or null when it says yes; the screen words the reasons differently. */
    fun biometricRefusal(file: VaultFile, now: Long, highWater: Long = 0): BiometricRefusal? = when {
        !supported(file) -> BiometricRefusal.UNSUPPORTED
        file.version != VaultFile.VERSION -> BiometricRefusal.UPGRADE
        now < file.masterCheckedAt || now < highWater - CLOCK_SLACK_MS -> BiometricRefusal.CLOCK
        now - file.masterCheckedAt >= MASTER_RECHECK_MS -> BiometricRefusal.MASTER_DUE
        else -> null
    }
}

/**
 * Why the fingerprint is refused: the master password is due (7 days), the clock went backwards, this version cannot
 * open the file (its format or version; the screen shows no notice for it), or the file is of an earlier version and
 * passes to v4 with the master password once ([UPGRADE_NEEDS_MASTER]).
 */
enum class BiometricRefusal { MASTER_DUE, CLOCK, UNSUPPORTED, UPGRADE }

/** How far behind the highest time seen the clock may be before the fingerprint asks for the master password. */
const val CLOCK_SLACK_MS = 5 * 60_000L

/** Biometrics need the master password again after this long. */
const val MASTER_RECHECK_MS = 7L * 24 * 60 * 60_000

/** Words in a seed phrase, and whether the count is one BIP-39 allows. */
fun seedWordCount(words: String): Pair<Int, Boolean> {
    val n = words.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    return n to (n in setOf(12, 15, 18, 21, 24))
}
