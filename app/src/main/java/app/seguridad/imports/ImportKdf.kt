package app.seguridad.imports

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.MAX_KDF_MEMORY_KIB
import app.seguridad.crypto.PasswordUse
import app.seguridad.crypto.appHeapKiB
import app.seguridad.crypto.blockGcm
import app.seguridad.crypto.passwordUtf8
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.util.Base64

/*
 * What the encrypted readers share (Task 10; spec «Parámetros de las claves, con tope antes de calcular» and «Todo
 * lector»). Owner's decisions (a) to (c):
 * - every parameter of a key derivation that comes from the file is checked here BEFORE any work, and a value outside
 *   the bounds is [IMPORT_KDF_TOO_BIG];
 * - every encrypted part is bounded ([IMPORT_MAX_ENCRYPTED_CHARS]) before it is decoded from Base64 or decrypted;
 * - what is decrypted is parsed only as a JSON tree, through the same estimate as a plain file ([decryptedJson]);
 * - every array that holds a secret (the password's bytes, every derived key, every decrypted buffer) is made through
 *   a [Secrets] and wiped in a `finally`.
 * Only standard primitives from Bouncy Castle (PBKDF2, scrypt, Argon2id, HKDF, AES-GCM through the app's [blockGcm]),
 * on byte arrays Clavero owns, so the phone and the JVM tests derive the same keys and every copy can be wiped.
 * The derivations run where [readImport] runs: off the main thread, under [IMPORT_KDF_RUN].
 */

/** PBKDF2 iterations accepted (spec: 5 000 to 2 000 000; Bitwarden's default is 600 000, 2FAS's 10 000). */
const val PBKDF2_MIN_ITERATIONS = 5_000
const val PBKDF2_MAX_ITERATIONS = 2_000_000

/** scrypt N accepted: a power of 2 from 2^14 to 2^20 (Aegis's default is 2^15). */
const val SCRYPT_MIN_N = 1 shl 14
const val SCRYPT_MAX_N = 1 shl 20
const val SCRYPT_MAX_R = 16
const val SCRYPT_MAX_P = 4

/**
 * Longest encrypted part read, in Base64 characters (8 MiB): its plaintext is at most 6 MiB, thousands of entries.
 * Alive at once at the peak: the file's bytes and text, the outer tree's encrypted string, the decoded ciphertext (with
 * room for its tag, so it is not copied to add one), one plaintext array (the CBC buffer is wiped and let go once the
 * plaintext is copied out of it), the plaintext's text and its tree. A bigger part would not leave room on a phone
 * with a heap of 192 MB. Checked before anything is decoded ([IMPORT_ENCRYPTED_TOO_BIG]).
 */
const val IMPORT_MAX_ENCRYPTED_CHARS = 8 * 1024 * 1024

/** Refuses [n] PBKDF2 iterations outside [PBKDF2_MIN_ITERATIONS]..[PBKDF2_MAX_ITERATIONS], before any work. */
fun checkPbkdf2Iterations(n: Int) {
    if (n !in PBKDF2_MIN_ITERATIONS..PBKDF2_MAX_ITERATIONS) throw ImportFailure(IMPORT_KDF_TOO_BIG)
}

/**
 * Refuses scrypt parameters outside the bounds, before any work: [n] a power of 2 from [SCRYPT_MIN_N] to
 * [SCRYPT_MAX_N], [r] 1 to [SCRYPT_MAX_R], [p] 1 to [SCRYPT_MAX_P], [n] under 2^(16·r) as RFC 7914 §2 asks (so at
 * most 2^15 with r = 1; Bouncy Castle refuses more), and its memory (128·N·r bytes, which Bouncy Castle allocates on
 * the Java heap) under the same bound as Argon2id ([KdfParams.checkSane]: at most 128 MiB, and above 64 MiB at most
 * ¾ of [heapKiB]).
 */
fun checkScrypt(n: Int, r: Int, p: Int, heapKiB: Long = appHeapKiB()) {
    val powerOfTwo = n > 0 && (n and (n - 1)) == 0
    if (!powerOfTwo || n !in SCRYPT_MIN_N..SCRYPT_MAX_N || r !in 1..SCRYPT_MAX_R || p !in 1..SCRYPT_MAX_P) {
        throw ImportFailure(IMPORT_KDF_TOO_BIG)
    }
    // With r ≥ 2, 2^(16·r) is past SCRYPT_MAX_N already; the shift is only taken while it fits in an Int.
    if (16 * r < Int.SIZE_BITS - 1 && n >= 1 shl (16 * r)) throw ImportFailure(IMPORT_KDF_TOO_BIG)
    val memoryKiB = 128L * n * r / 1024
    if (memoryKiB > MAX_KDF_MEMORY_KIB) throw ImportFailure(IMPORT_KDF_TOO_BIG)
    checkArgon2id(KdfParams(memoryKiB.toInt(), iterations = 1, parallelism = 1), heapKiB)
}

/** [KdfParams.checkSane], as the import says it: [IMPORT_KDF_TOO_BIG]. */
internal fun checkArgon2id(params: KdfParams, heapKiB: Long = appHeapKiB()) {
    try {
        params.checkSane(heapKiB)
    } catch (e: CryptoFailure) {
        throw ImportFailure(IMPORT_KDF_TOO_BIG)
    }
}

/**
 * Bitwarden's Argon2id settings, its memory in MiB (Bitwarden's unit, documented), as [KdfParams] checked by
 * [checkArgon2id]. A memory that does not fit in KiB as an Int is refused too.
 */
internal fun bitwardenArgon2Params(memoryMiB: Int, iterations: Int, parallelism: Int, heapKiB: Long = appHeapKiB()): KdfParams {
    if (memoryMiB !in 1..MAX_KDF_MEMORY_KIB / 1024) throw ImportFailure(IMPORT_KDF_TOO_BIG)
    return KdfParams(memoryMiB * 1024, iterations, parallelism).also { checkArgon2id(it, heapKiB) }
}

/**
 * The secret arrays of one read: each is made or kept here and wiped by [wipe], which every reader calls in a
 * `finally`. [onKeep] sees each array, for the tests that check they end as zeros.
 */
internal class Secrets(private val onKeep: (ByteArray) -> Unit = {}) {
    private val held = ArrayList<ByteArray>()

    fun bytes(size: Int): ByteArray = keep(ByteArray(size))

    /**
     * An array the caller wipes itself, at once and in a `finally`, and then lets go: it is not held until [wipe], so a
     * large buffer does not stay alive beside its copy (M-6). [onKeep] sees it too.
     */
    fun scratch(size: Int): ByteArray = ByteArray(size).also(onKeep)

    fun keep(a: ByteArray): ByteArray {
        held += a
        onKeep(a)
        return a
    }

    fun wipe() {
        for (a in held) a.fill(0)
        held.clear()
    }
}

/**
 * The password in UTF-8, as the three apps derive from it (their documents give UTF-8). A lone surrogate cannot be
 * written by them, so it can be no export's password: [IMPORT_WRONG_PASSWORD], with no work done.
 */
internal fun importPasswordBytes(password: CharArray, s: Secrets): ByteArray = try {
    s.keep(passwordUtf8(password, PasswordUse.NEW))
} catch (e: CryptoFailure) {
    throw ImportFailure(IMPORT_WRONG_PASSWORD)
}

/** Running out of heap inside a derivation is the file asking too much for this phone, never a crash. */
private inline fun <T> kdf(block: () -> T): T = try {
    block()
} catch (e: OutOfMemoryError) {
    throw ImportFailure(IMPORT_KDF_TOO_BIG)
}

/** PBKDF2-HMAC-SHA256 (RFC 8018) of [password] and [salt], [length] bytes; [iterations] checked first ([checked]: always, but in a test vector). */
internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int, s: Secrets, checked: Boolean = true): ByteArray {
    if (checked) checkPbkdf2Iterations(iterations)
    val gen = PKCS5S2ParametersGenerator(SHA256Digest())
    gen.init(password, salt, iterations)
    // The generator keeps a reference to [password] (no copy; the caller's Secrets wipes it). Its own buffer for the
    // derived key is copied into the KeyParameter and cannot be reached to wipe (accepted, as CRY-A03's round keys);
    // the copy this returns is wiped by [s].
    val kp = kdf { gen.generateDerivedParameters(length * 8) } as KeyParameter
    return s.keep(kp.key)
}

/**
 * scrypt (RFC 7914) of [password] and [salt], [length] bytes; the parameters checked first ([checkScrypt]; [checked]:
 * always, but in a test). A parameter Bouncy Castle still refuses (an [IllegalArgumentException]) is the file asking
 * what this phone cannot do, [IMPORT_KDF_TOO_BIG], never «dañado».
 */
internal fun scryptKey(
    password: ByteArray,
    salt: ByteArray,
    n: Int,
    r: Int,
    p: Int,
    length: Int,
    s: Secrets,
    checked: Boolean = true,
): ByteArray {
    if (checked) checkScrypt(n, r, p)
    return s.keep(
        kdf {
            try {
                SCrypt.generate(password, salt, n, r, p, length)
            } catch (e: IllegalArgumentException) {
                throw ImportFailure(IMPORT_KDF_TOO_BIG)
            }
        },
    )
}

/**
 * Argon2id (RFC 9106, version 0x13) of [password] and [salt] (any length of 8 bytes or more), [length] bytes; [params]
 * checked first. It makes its own Bouncy Castle call instead of the app's `argon2id` (ruling R23) because that one
 * requires a 16-byte salt and Bitwarden's Argon2id salt is a 32-byte SHA-256; the same [checkArgon2id] bound
 * ([KdfParams.checkSane]) and the same out-of-memory guard run here.
 */
internal fun argon2idKey(password: ByteArray, salt: ByteArray, params: KdfParams, length: Int, s: Secrets): ByteArray {
    checkArgon2id(params)
    val p = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withMemoryAsKB(params.memoryKiB)
        .withIterations(params.iterations)
        .withParallelism(params.parallelism)
        .build()
    val out = s.bytes(length)
    kdf { Argon2BytesGenerator().apply { init(p) }.generateBytes(password, out) }
    return out
}

/** HKDF-Expand (RFC 5869 §2.3) with HMAC-SHA256 and [prk] taken as the pseudorandom key (no Extract step). */
internal fun hkdfExpandSha256(prk: ByteArray, info: ByteArray, length: Int, s: Secrets): ByteArray {
    val out = s.bytes(length)
    HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters.skipExtractParameters(prk, info)) }.generateBytes(out, 0, length)
    return out
}

/**
 * AES-256-GCM decryption of [sealed] (ciphertext ‖ 16-byte tag) with [key] and [nonce], no associated data, through
 * the app's [blockGcm]: its output buffer is wiped when the tag fails, so nothing a wrong key decrypted is left. A tag
 * that fails is [IMPORT_WRONG_PASSWORD] (GCM cannot tell a wrong key from a changed file).
 */
internal fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, s: Secrets): ByteArray = try {
    blockGcm(AESEngine.newInstance(), false, key, nonce, ByteArray(0), sealed) { s.bytes(it) }
} catch (e: CryptoFailure) {
    throw ImportFailure(IMPORT_WRONG_PASSWORD)
}

/**
 * The bytes of the Base64 (RFC 4648, standard alphabet, padded) in [text], followed by [spare] zero bytes (room for a
 * tag, so the caller need not copy the result to append it). Line breaks and spaces are passed over (some apps wrap
 * their Base64). [maxChars] is checked first ([IMPORT_ENCRYPTED_TOO_BIG]); anything else that is not Base64 is
 * [IMPORT_BROKEN]. Only for encrypted parts and their parameters, which are not secret.
 */
internal fun importBase64(text: String, maxChars: Int = IMPORT_MAX_ENCRYPTED_CHARS, spare: Int = 0): ByteArray {
    if (text.length > maxChars) throw ImportFailure(IMPORT_ENCRYPTED_TOO_BIG)
    val compact = if (text.any { it == '\n' || it == '\r' || it == ' ' }) text.filterNot { it == '\n' || it == '\r' || it == ' ' } else text
    if (compact.isEmpty() || compact.length % 4 != 0) throw ImportFailure(IMPORT_BROKEN)
    return try {
        if (spare == 0) {
            Base64.getDecoder().decode(compact)
        } else {
            val size = compact.length / 4 * 3 - compact.takeLast(2).count { it == '=' }
            val out = ByteArray(size + spare)
            // decode(String) makes this same Latin-1 copy of the text inside; written here, the bytes land in [out].
            if (Base64.getDecoder().decode(compact.toByteArray(Charsets.ISO_8859_1), out) != size) throw ImportFailure(IMPORT_BROKEN)
            out
        }
    } catch (e: IllegalArgumentException) {
        throw ImportFailure(IMPORT_BROKEN)
    }
}

/** The [bytes] bytes written in hexadecimal in [text] (either case), or [IMPORT_BROKEN]; the length is checked first. */
internal fun importHex(text: String, bytes: Int): ByteArray {
    if (text.length != bytes * 2) throw ImportFailure(IMPORT_BROKEN)
    val out = ByteArray(bytes)
    for (i in 0 until bytes) {
        val hi = Character.digit(text[2 * i], 16)
        val lo = Character.digit(text[2 * i + 1], 16)
        if (hi < 0 || lo < 0) throw ImportFailure(IMPORT_BROKEN)
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

/**
 * What an encrypted part gave, read as data like any plain export (owner's decisions (a) and (c)): strict text
 * ([decodeText]: UTF-8, nothing replaced), then the same scan as a plain file before parsing ([checkJsonShape], depth
 * and tree estimate against [budget]), then only [Json.parseToJsonElement] into a tree. Not text or not JSON is
 * [IMPORT_BROKEN]. [plain] is the caller's to wipe; the String made from it cannot be (accepted, spec).
 */
internal fun decryptedJson(plain: ByteArray, budget: Long): JsonElement {
    val text = decodeText(plain) ?: throw ImportFailure(IMPORT_BROKEN)
    checkJsonShape(text, budget)
    return try {
        Json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        throw ImportFailure(IMPORT_BROKEN)
    } catch (e: IllegalArgumentException) {
        throw ImportFailure(IMPORT_BROKEN)
    }
}
