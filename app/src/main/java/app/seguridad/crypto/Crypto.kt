package app.seguridad.crypto

import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.CamelliaEngine
import org.bouncycastle.crypto.engines.SerpentEngine
import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wire format family. New data is always written as [SEGURIDAD]; [CRIPTOPANEL] (CP1:, .cpenc) is still read.
 * The two differ only in the header magic, the text prefix, the file extension and the HKDF label prefix.
 */
enum class Dialect(val magic: ByteArray, val textPrefix: String, val fileExtension: String, val labelPrefix: String) {
    SEGURIDAD(byteArrayOf('S'.code.toByte(), 'G'.code.toByte()), "SG1:", ".sgenc", "seguridad"),
    CRIPTOPANEL(byteArrayOf('C'.code.toByte(), 'P'.code.toByte()), "CP1:", ".cpenc", "criptopanel"),
    ;

    companion object {
        fun ofMagic(magic: ByteArray): Dialect? = entries.firstOrNull { it.magic.contentEquals(magic) }
    }
}

/** Authenticated ciphers offered; [id] is what the header stores. */
enum class Aead(val id: Byte, val label: String, val detail: String) {
    AES_256_GCM(1, "AES-256", "AES-256-GCM: el estándar mundial, acelerado por el hardware del móvil"),
    CHACHA20_POLY1305(2, "ChaCha20", "ChaCha20-Poly1305: el moderno de TLS 1.3 y WireGuard"),
    TWOFISH_256_GCM(3, "Twofish-256", "Twofish-256-GCM: finalista de AES, de Bruce Schneier"),
    SERPENT_256_GCM(4, "Serpent-256", "Serpent-256-GCM: finalista de AES, con el mayor margen de seguridad"),
    CAMELLIA_256_GCM(5, "Camellia-256", "Camellia-256-GCM: estándar ISO y del gobierno japonés"),
    CASCADE(6, "Cascada", "AES-256 → Twofish-256 → Serpent-256: tres cifrados con claves independientes"),
    ;

    companion object {
        fun of(id: Byte): Aead = entries.firstOrNull { it.id == id } ?: throw CryptoFailure("Algoritmo desconocido")
    }
}

/** Argon2id cost; stored with every ciphertext so it can be raised later without breaking old ones. */
data class KdfParams(val memoryKiB: Int = DEFAULT_KDF_MEMORY_KIB, val iterations: Int = 3, val parallelism: Int = 1) {
    /**
     * Refuses absurd values from a crafted header (memory bombs) and costs that plainly cannot fit this phone.
     * [heapKiB] is what this phone lets the app use ([appHeapKiB]).
     */
    fun checkSane(heapKiB: Long = appHeapKiB()) {
        // Hard bounds, the same on every phone: no header may ask for more than MAX_KDF_MEMORY_KIB (128 MiB),
        // 10 passes or 4 lanes, however big the heap is.
        val outOfBounds = memoryKiB !in 8 * parallelism..MAX_KDF_MEMORY_KIB || iterations !in 1..10 || parallelism !in 1..4
        // Above the default, refuse only what cannot fit: Bouncy Castle's Argon2 takes memoryKiB of the heap
        // (plus about 3 % of object overhead) and the app needs room for itself, so a quarter of the heap stays
        // free. «Alto» (128 MiB) then opens on any phone with at least 171 MiB per app, also on one that does not
        // offer it for new data (see KdfProfile.availableOn); a 128 MiB phone could never run it, so it is refused
        // before any work. Inside this bound an OutOfMemoryError is still possible and [argon2id] turns it into a
        // CryptoFailure. The default is always accepted, or a phone with a small heap could not open its own data.
        val cannotFit = memoryKiB > DEFAULT_KDF_MEMORY_KIB && memoryKiB > heapKiB * 3 / 4
        if (outOfBounds || cannotFit) throw CryptoFailure("Parámetros de cifrado no válidos")
    }
}

/** What this phone lets the app use on the Java heap, in KiB (Bouncy Castle's Argon2 allocates its memory there). */
fun appHeapKiB(): Long = Runtime.getRuntime().maxMemory() / 1024

/** Shown when Argon2id runs out of memory instead of crashing the app. */
const val KDF_OUT_OF_MEMORY = "Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id"

/**
 * Runs an Argon2id derivation and turns running out of heap into a [CryptoFailure] the screens show, instead of a
 * crash. Only for that: Argon2's memory is allocated by Bouncy Castle when the generator is set up (before the
 * password is touched) and is local to [block], so it is all garbage once this returns or throws.
 */
internal inline fun <T> outOfMemoryAsFailure(block: () -> T): T = try {
    block()
} catch (e: OutOfMemoryError) {
    throw CryptoFailure(KDF_OUT_OF_MEMORY)
}

/**
 * CIFRADO-1 (audit 1.3.0): a new password with a lone surrogate (half of an emoji, only possible when pasted from
 * another app) is not text, so nothing new is protected with it ([PasswordUse.NEW]). Data made earlier under one still
 * opens ([PasswordUse.EXISTING]): Seguridad 1.0.x and 1.1.x (Bouncy Castle 1.80) derived a key for some of them.
 */
const val PASSWORD_NOT_TEXT = "La contraseña tiene un carácter que no es texto válido: escríbela de nuevo."

/**
 * What a password is for, which decides how one that is not valid UTF-16 is encoded (review F5, M1). Valid text is
 * encoded the same way in both cases, byte for byte as every version of the app did.
 */
enum class PasswordUse {
    /**
     * It protects something new: a new vault, a new master password, a new text or file. A lone surrogate is refused
     * with [PASSWORD_NOT_TEXT], as Bouncy Castle 1.82 and later do.
     */
    NEW,

    /**
     * It opens something made earlier (unlocking the vault, importing a copy, decrypting an `SG1:`/`CP1:` text or a
     * `.sgenc`/`.cpenc` file), or wraps it again under the same password it already had. Encoded exactly as Bouncy
     * Castle 1.80 did (Seguridad 1.0.x and 1.1.x): a high surrogate takes **whatever unit follows** as its pair,
     * `(((hi and 0x3FF) shl 10) or (next and 0x3FF)) + 0x10000` in 4 bytes, even when that unit is not a low surrogate
     * (from 1.2.0, Bouncy Castle 1.86 refused those, so no other key can exist for them). A lone low surrogate and a high
     * one at the end are refused, as every version did.
     */
    EXISTING,
}

/** How [utf8Of] treats a password that is not valid UTF-16. */
private enum class LoneSurrogates { REFUSE, BC_1_80, QUESTION_MARK }

/**
 * CIFRADO-1 (audit 1.3.0): [chars] in UTF-8, in a new array of exactly that size that the caller wipes. For valid text it
 * is byte for byte what Bouncy Castle's `PasswordConverter.UTF8` gives (1.80 and 1.86: `PKCS5PasswordToUTF8Bytes`), and
 * so every Argon2id key stays the same; but it leaves no copy behind (Bouncy Castle encodes through a buffer of its own
 * and a `ByteArrayOutputStream`, and wipes neither). What is not valid UTF-16 follows [malformed]; a refusal throws
 * [CryptoFailure] ([PASSWORD_NOT_TEXT]) in the measuring pass, before anything is allocated. Plain arithmetic: no
 * `String`, no `CharsetEncoder` (whose buffers are not ours to wipe).
 */
private fun utf8Of(chars: CharArray, malformed: LoneSurrogates): ByteArray {
    // A pair: a high surrogate and a low one; with BC_1_80, a high surrogate and any unit after it.
    fun pairAt(i: Int) = chars[i].isHighSurrogate() && i + 1 < chars.size &&
        (chars[i + 1].isLowSurrogate() || malformed == LoneSurrogates.BC_1_80)
    var size = 0
    var i = 0
    while (i < chars.size) {
        val c = chars[i].code
        size += when {
            c < 0x80 -> 1
            c < 0x800 -> 2
            pairAt(i) -> 4.also { i++ }
            chars[i].isSurrogate() -> if (malformed == LoneSurrogates.QUESTION_MARK) 1 else throw CryptoFailure(PASSWORD_NOT_TEXT)
            else -> 3
        }
        i++
    }
    val out = ByteArray(size)
    var o = 0
    i = 0
    while (i < chars.size) {
        val c = chars[i].code
        when {
            c < 0x80 -> out[o++] = c.toByte()
            c < 0x800 -> {
                out[o++] = (0xC0 or (c shr 6)).toByte()
                out[o++] = (0x80 or (c and 0x3F)).toByte()
            }
            pairAt(i) -> {
                // For a real pair this is Character.toCodePoint; for BC_1_80 it is Bouncy Castle 1.80's arithmetic.
                val cp = (((c and 0x3FF) shl 10) or (chars[i + 1].code and 0x3FF)) + 0x10000
                out[o++] = (0xF0 or (cp shr 18)).toByte()
                out[o++] = (0x80 or ((cp shr 12) and 0x3F)).toByte()
                out[o++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                out[o++] = (0x80 or (cp and 0x3F)).toByte()
                i++
            }
            chars[i].isSurrogate() -> out[o++] = '?'.code.toByte() // Only QUESTION_MARK gets here.
            else -> {
                out[o++] = (0xE0 or (c shr 12)).toByte()
                out[o++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                out[o++] = (0x80 or (c and 0x3F)).toByte()
            }
        }
        i++
    }
    return out
}

/** [chars] in UTF-8 as Argon2id hashes it for [use] ([utf8Of], [PasswordUse]); [CryptoFailure] if it is refused. The caller wipes it. */
internal fun passwordUtf8(chars: CharArray, use: PasswordUse): ByteArray =
    utf8Of(chars, if (use == PasswordUse.NEW) LoneSurrogates.REFUSE else LoneSurrogates.BC_1_80)

/** [chars] in UTF-8 with each lone surrogate as «?», exactly as `String(chars).toByteArray(UTF_8)` ([utf8Of]). The caller wipes it. */
internal fun utf8Replacing(chars: CharArray): ByteArray = utf8Of(chars, LoneSurrogates.QUESTION_MARK)

/**
 * What to say when protecting something with a new password failed: the [CryptoFailure] texts meant for that
 * ([PASSWORD_NOT_TEXT], [KDF_OUT_OF_MEMORY]), or [fallback] for anything else (never a raw exception). Review F5, M2.
 */
fun newPasswordFailureMessage(e: Throwable, fallback: String): String =
    (e as? CryptoFailure)?.message?.takeIf { it == PASSWORD_NOT_TEXT || it == KDF_OUT_OF_MEMORY } ?: fallback

/** Wrong password, damaged or tampered data: deliberately one message, so it leaks nothing about which. */
class CryptoFailure(message: String = "Contraseña incorrecta o datos dañados") : Exception(message)

const val KEY_BYTES = 32
/** Argon2id memory used for new ciphertexts and vaults: 64 MiB. */
const val DEFAULT_KDF_MEMORY_KIB = 64 * 1024
/** Highest Argon2id memory a header may ask for: 2× the default (Bouncy Castle allocates it on the Java heap). */
const val MAX_KDF_MEMORY_KIB = 128 * 1024
const val SALT_BYTES = 16
const val NONCE_BYTES = 12
const val TAG_BYTES = 16

val secureRandom: SecureRandom by lazy { SecureRandom() }

fun randomBytes(n: Int, random: SecureRandom = secureRandom): ByteArray = ByteArray(n).also(random::nextBytes)

/**
 * Argon2id (RFC 9106, version 0x13) of [password] in UTF-8, encoded for [use] ([PasswordUse]: what protects something new
 * and what opens something made earlier differ only for a password that is not valid text); the caller wipes the result
 * when done. Throws [CryptoFailure] ([KDF_OUT_OF_MEMORY]) when the phone has no heap for [p]'s memory, so opening «Alto»
 * data on a small phone fails with a clear message rather than crashing the app, and ([PASSWORD_NOT_TEXT]) for a
 * password [use] refuses.
 */
fun argon2id(password: CharArray, salt: ByteArray, p: KdfParams, length: Int = KEY_BYTES, use: PasswordUse = PasswordUse.NEW): ByteArray {
    // Keep this public entry point safe too: callers must not be able to bypass header validation.
    require(salt.size == SALT_BYTES) { "Argon2id salt must be 16 bytes" }
    require(length in 4..1024) { "Argon2id output length is out of bounds" }
    p.checkSane()
    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withMemoryAsKB(p.memoryKiB)
        .withIterations(p.iterations)
        .withParallelism(p.parallelism)
        .build()
    // CIFRADO-1: our own UTF-8 copy (the same bytes Bouncy Castle's char[] overload hashed), wiped when done.
    val pw = passwordUtf8(password, use)
    try {
        val out = ByteArray(length)
        outOfMemoryAsFailure { Argon2BytesGenerator().apply { init(params) }.generateBytes(pw, out) }
        return out
    } finally {
        pw.fill(0)
    }
}

/** Raw Argon2id with secret and associated data, only for the RFC 9106 test vector. */
internal fun argon2idRaw(password: ByteArray, salt: ByteArray, secret: ByteArray, ad: ByteArray, p: KdfParams, length: Int): ByteArray {
    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt).withSecret(secret).withAdditional(ad)
        .withMemoryAsKB(p.memoryKiB).withIterations(p.iterations).withParallelism(p.parallelism)
        .build()
    val out = ByteArray(length)
    Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out)
    return out
}

/** GCM over any 128-bit block cipher from Bouncy Castle. */
private fun blockGcm(engine: BlockCipher, encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray =
    try {
        GCMBlockCipher.newInstance(engine).run {
            init(encrypt, AEADParameters(KeyParameter(key), TAG_BYTES * 8, nonce, aad))
            val out = ByteArray(getOutputSize(input.size))
            val n = processBytes(input, 0, input.size, out, 0)
            val total = n + doFinal(out, n)
            if (total == out.size) out else out.copyOf(total).also { out.fill(0) }
        }
    } catch (e: InvalidCipherTextException) {
        throw CryptoFailure()
    }

/** Independent 256-bit keys for each cascade layer, from the one key (HKDF-SHA256). */
internal fun cascadeKey(key: ByteArray, layer: String, dialect: Dialect): ByteArray {
    val out = ByteArray(KEY_BYTES)
    HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters(key, null, "${dialect.labelPrefix}-cascade-$layer".toByteArray())) }.generateBytes(out, 0, out.size)
    return out
}

private val CASCADE_LAYERS = listOf(Aead.AES_256_GCM, Aead.TWOFISH_256_GCM, Aead.SERPENT_256_GCM)

/** Bytes each encryption adds: one tag, or one per layer for the cascade. */
val Aead.overhead: Int get() = if (this == Aead.CASCADE) CASCADE_LAYERS.size * TAG_BYTES else TAG_BYTES

/**
 * One-shot AEAD: returns ciphertext ‖ tag, or the plaintext; throws [CryptoFailure] on a bad tag.
 * [dialect] only matters for [Aead.CASCADE], whose layer keys are derived with the dialect's labels.
 */
fun aead(
    alg: Aead,
    encrypt: Boolean,
    key: ByteArray,
    nonce: ByteArray,
    aad: ByteArray,
    input: ByteArray,
    dialect: Dialect = Dialect.SEGURIDAD,
): ByteArray = when (alg) {
    Aead.TWOFISH_256_GCM -> blockGcm(TwofishEngine(), encrypt, key, nonce, aad, input)
    Aead.SERPENT_256_GCM -> blockGcm(SerpentEngine(), encrypt, key, nonce, aad, input)
    Aead.CAMELLIA_256_GCM -> blockGcm(CamelliaEngine(), encrypt, key, nonce, aad, input)
    // Each layer authenticates on its own; the same nonce is safe because every layer has a different key.
    Aead.CASCADE -> {
        val layers = if (encrypt) CASCADE_LAYERS else CASCADE_LAYERS.reversed()
        layers.fold(input) { data, layer ->
            val k = cascadeKey(key, layer.name, dialect)
            try {
                aead(layer, encrypt, k, nonce, aad, data)
            } finally {
                k.fill(0)
                if (data !== input) data.fill(0) // Intermediate layer, no longer needed.
            }
        }
    }
    Aead.AES_256_GCM -> try {
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(aad)
            doFinal(input)
        }
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure()
    }
    Aead.CHACHA20_POLY1305 -> try {
        ChaCha20Poly1305().run {
            init(encrypt, AEADParameters(KeyParameter(key), TAG_BYTES * 8, nonce, aad))
            val out = ByteArray(getOutputSize(input.size))
            val n = processBytes(input, 0, input.size, out, 0)
            val total = n + doFinal(out, n)
            if (total == out.size) out else out.copyOf(total).also { out.fill(0) }
        }
    } catch (e: InvalidCipherTextException) {
        throw CryptoFailure()
    }
}

/** What unlocks a ciphertext: a password (stretched with Argon2id) or a random 256-bit key used as is. */
sealed interface Secret {
    class Password(val chars: CharArray) : Secret
    class RawKey(val bytes: ByteArray) : Secret {
        init {
            require(bytes.size == KEY_BYTES)
        }
    }
}

/** A fresh random AES-256 key, as 64 hex characters in groups of 4 for writing down. */
fun newRawKeyHex(random: SecureRandom = secureRandom): String =
    hex(randomBytes(KEY_BYTES, random)).lowercase().chunked(4).joinToString(" ")

/** Whether [text] is a 64-hex-character key (same rules as [parseRawKey]) without building the key itself. */
fun isRawKeyText(text: String): Boolean {
    var digits = 0
    for (c in text) {
        when {
            c.isWhitespace() || c == '-' || c == ':' -> Unit
            c.lowercaseChar() in "0123456789abcdef" -> digits++
            else -> return false
        }
    }
    return digits == KEY_BYTES * 2
}

/** Parses a 64-hex-character key, ignoring spaces, dashes and case; null if it is not one. */
fun parseRawKey(text: String): ByteArray? {
    val clean = text.filter { !it.isWhitespace() && it != '-' && it != ':' }
    if (clean.length != KEY_BYTES * 2 || clean.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return ByteArray(KEY_BYTES) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/**
 * The encryption key for [secret]; for a raw key, a copy (the caller wipes it). A password is encoded for [use]: [PasswordUse.NEW]
 * when encrypting, [PasswordUse.EXISTING] when decrypting (review F5, M1).
 */
internal fun keyForHeader(secret: Secret, header: Header, use: PasswordUse = PasswordUse.NEW): ByteArray = when (secret) {
    is Secret.Password -> {
        if (header.kdfId != Header.KDF_ARGON2ID) throw CryptoFailure("Esto se cifró con una clave, no con una contraseña")
        argon2id(secret.chars, header.salt, header.kdf, use = use)
    }
    is Secret.RawKey -> when (header.kdfId) {
        // A fresh key per message from the random salt, so one raw key can protect any number of texts and files.
        Header.KDF_RAW_KEY_HKDF -> rawKeyHkdf(secret.bytes, header.salt, header.dialect)
        Header.KDF_RAW_KEY -> secret.bytes.copyOf()
        else -> throw CryptoFailure("Esto se cifró con una contraseña, no con una clave")
    }
}

internal fun rawKeyHkdf(key: ByteArray, salt: ByteArray, dialect: Dialect): ByteArray {
    val out = ByteArray(KEY_BYTES)
    HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters(key, salt, "${dialect.labelPrefix}-raw-key".toByteArray())) }.generateBytes(out, 0, out.size)
    return out
}

/** Whether [kdfId] means a raw key rather than a password. */
internal fun isRawKdf(kdfId: Byte) = kdfId == Header.KDF_RAW_KEY || kdfId == Header.KDF_RAW_KEY_HKDF

/** Header shared by texts and files; the whole of it is authenticated as associated data. */
internal data class Header(
    val kind: Byte,
    val alg: Aead,
    val kdf: KdfParams,
    /** [KDF_ARGON2ID] for passwords, [KDF_RAW_KEY_HKDF] (or the older [KDF_RAW_KEY]) for a raw key; then [kdf] is unused and stored as zeros. */
    val kdfId: Byte,
    val salt: ByteArray,
    /** Full 12-byte nonce for texts, 7-byte STREAM prefix for files. */
    val nonce: ByteArray,
    /** Which magic the header carries; it also picks the HKDF labels. */
    val dialect: Dialect,
) {
    fun bytes(): ByteArray = ByteBuffer.allocate(SIZE_BEFORE_NONCE + nonce.size).apply {
        put(dialect.magic); put(VERSION); put(kind); put(alg.id); put(kdfId)
        if (kdfId == KDF_ARGON2ID) {
            putInt(kdf.memoryKiB); put(kdf.iterations.toByte()); put(kdf.parallelism.toByte())
        } else {
            putInt(0); put(0); put(0)
        }
        put(salt); put(nonce)
    }.array()

    companion object {
        const val VERSION: Byte = 1
        const val KDF_ARGON2ID: Byte = 1
        /** Raw key used directly (old CP1 texts and files from 1.9.0; still read, no longer written). */
        const val KDF_RAW_KEY: Byte = 2
        /** Raw key, expanded with HKDF-SHA256 and the header's random salt. */
        const val KDF_RAW_KEY_HKDF: Byte = 3
        const val KIND_TEXT: Byte = 1
        const val KIND_FILE: Byte = 2
        const val SIZE_BEFORE_NONCE = 2 + 1 + 1 + 1 + 1 + 4 + 1 + 1 + SALT_BYTES

        fun nonceSize(kind: Byte) = if (kind == KIND_FILE) STREAM_PREFIX_BYTES else NONCE_BYTES

        /** Parses and validates; [expectedKind] guards against feeding a file header to the text decoder and vice versa. */
        fun parse(buf: ByteBuffer, expectedKind: Byte, heapKiB: Long = appHeapKiB()): Header {
            if (buf.remaining() < SIZE_BEFORE_NONCE) throw CryptoFailure(NOT_COMPATIBLE)
            val magic = ByteArray(2).also { buf.get(it) }
            val dialect = Dialect.ofMagic(magic) ?: throw CryptoFailure(NOT_COMPATIBLE)
            if (buf.get() != VERSION) throw CryptoFailure("Versión de formato no compatible")
            val kind = buf.get()
            if (kind != expectedKind) throw CryptoFailure(if (kind == KIND_FILE) "Esto es un archivo cifrado" else "Esto es un texto cifrado")
            val alg = Aead.of(buf.get())
            val kdfId = buf.get()
            if (kdfId != KDF_ARGON2ID && !isRawKdf(kdfId)) throw CryptoFailure("Derivación de clave desconocida")
            val kdf = KdfParams(buf.int, buf.get().toInt() and 0xFF, buf.get().toInt() and 0xFF)
            if (kdfId == KDF_ARGON2ID) kdf.checkSane(heapKiB)
            val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
            if (buf.remaining() < nonceSize(kind)) throw CryptoFailure()
            val nonce = ByteArray(nonceSize(kind)).also { buf.get(it) }
            return Header(kind, alg, kdf, kdfId, salt, nonce, dialect)
        }
    }
}

/** Prefix of the texts this app writes ([Dialect.SEGURIDAD]); the legacy dialect's (CP1:) is still read. */
const val TEXT_PREFIX = "SG1:"

/** Not a header of any dialect, or a text prefix that does not match its header's magic. */
internal const val NOT_COMPATIBLE = "No es un dato cifrado compatible"

/** Largest .txt imported into the text decrypter (a ciphertext is a few KB). */
const val MAX_TEXT_FILE_BYTES = 1024 * 1024

/** The first "SG1:" or "CP1:" ciphertext inside a text file, which may also hold other lines; null if there is none. */
fun extractToken(text: String): String? {
    // One pass over the text per prefix: where each one first appears, and the earliest wins (two prefixes never start at
    // the same place).
    val (prefix, start) = Dialect.entries
        .map { it.textPrefix to text.indexOf(it.textPrefix) }
        .filter { (_, at) -> at >= 0 }
        .minByOrNull { (_, at) -> at } ?: return null
    return extractPrefixedToken(text, prefix, start)
}

/**
 * The token that starts with [prefix] at [start] (its first appearance) inside [text], which may be a chat message or a
 * file with other lines; null if nothing follows the prefix. Only for the "SG1:" and "CP1:" texts, through [extractToken]: the
 * public-key formats ("SGM1:", "SGS1:", "SGK1:") have their own reader (`pk/Message.kt`, `pk/Received.kt`), which tries
 * every way of joining the pieces because a word after the token can look like Base64url too.
 * The token is Base64url: on its own line it ends at the first other character (a space, a full stop…), and chat apps
 * and editors that wrap it are handled by joining the following lines as long as they are nothing but Base64url.
 */
private fun extractPrefixedToken(text: String, prefix: String, start: Int): String? {
    fun isB64(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
    val lines = text.substring(start + prefix.length).lines()
    // On its own line the token ends at the first other character (a space, a full stop…).
    val body = StringBuilder(lines.first().trimStart().takeWhile(::isB64))
    // Chat apps and editors may wrap it: a following line continues it only if it is nothing but Base64url,
    // so a note written after it ("Guardado el lunes") is never glued on.
    if (body.length == lines.first().trim().length) {
        for (line in lines.drop(1)) {
            val t = line.trim()
            if (t.isEmpty() || !t.all(::isB64)) break
            body.append(t)
        }
    }
    return if (body.isEmpty()) null else prefix + body
}

internal fun newHeader(kind: Byte, alg: Aead, secret: Secret, kdf: KdfParams, random: SecureRandom): Header {
    val raw = secret is Secret.RawKey
    return Header(
        kind = kind,
        alg = alg,
        kdf = kdf,
        kdfId = if (raw) Header.KDF_RAW_KEY_HKDF else Header.KDF_ARGON2ID,
        salt = randomBytes(SALT_BYTES, random),
        nonce = randomBytes(Header.nonceSize(kind), random),
        dialect = Dialect.SEGURIDAD,
    )
}

/** Encrypts [plain] into "SG1:" + URL-safe Base64 of header ‖ ciphertext ‖ tag. */
fun encryptText(plain: String, password: CharArray, alg: Aead = Aead.AES_256_GCM, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): String =
    encryptText(plain, Secret.Password(password), alg, kdf, random)

fun encryptText(plain: String, secret: Secret, alg: Aead = Aead.AES_256_GCM, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): String {
    val header = newHeader(Header.KIND_TEXT, alg, secret, kdf, random)
    val aad = header.bytes()
    val key = keyForHeader(secret, header, PasswordUse.NEW)
    val plainBytes = plain.toByteArray(Charsets.UTF_8)
    try {
        val sealed = aead(alg, true, key, header.nonce, aad, plainBytes, header.dialect)
        return header.dialect.textPrefix + Base64.getUrlEncoder().withoutPadding().encodeToString(aad + sealed)
    } finally {
        key.fill(0)
        plainBytes.fill(0)
    }
}

/** Decrypts a "SG1:" or "CP1:" text; spaces and line breaks added by chat apps are ignored. */
fun decryptText(token: String, password: CharArray): String = decryptText(token, Secret.Password(password))

/** The dialect whose text prefix [clean] starts with, or null. */
private fun textDialect(clean: String): Dialect? = Dialect.entries.firstOrNull { clean.startsWith(it.textPrefix) }

/** How a "SG1:" or "CP1:" text was encrypted, read from its header without any secret; null if it is not one. */
fun inspectText(token: String): Pair<Aead, Boolean>? = try {
    val clean = token.trim().filterNot { it.isWhitespace() }
    val dialect = textDialect(clean)
    if (dialect == null) {
        null
    } else {
        val raw = Base64.getUrlDecoder().decode(clean.removePrefix(dialect.textPrefix))
        val h = Header.parse(ByteBuffer.wrap(raw), Header.KIND_TEXT)
        if (h.dialect != dialect) null else h.alg to isRawKdf(h.kdfId)
    }
} catch (e: Exception) {
    null
}

/** [heapKiB] is only for tests: what the reading phone lets the app use. */
fun decryptText(token: String, secret: Secret, heapKiB: Long = appHeapKiB()): String {
    val clean = token.trim().filterNot { it.isWhitespace() }
    val dialect = textDialect(clean) ?: throw CryptoFailure("El texto cifrado debe empezar por $TEXT_PREFIX")
    val raw = try {
        Base64.getUrlDecoder().decode(clean.removePrefix(dialect.textPrefix))
    } catch (e: IllegalArgumentException) {
        throw CryptoFailure("El texto cifrado está incompleto o dañado")
    }
    val buf = ByteBuffer.wrap(raw)
    val header = Header.parse(buf, Header.KIND_TEXT, heapKiB)
    // A prefix of one dialect on a header of the other was never written by either format.
    if (header.dialect != dialect) throw CryptoFailure(NOT_COMPATIBLE)
    val aad = raw.copyOfRange(0, raw.size - buf.remaining())
    val sealed = raw.copyOfRange(aad.size, raw.size)
    if (sealed.size < header.alg.overhead) throw CryptoFailure()
    val key = keyForHeader(secret, header, PasswordUse.EXISTING)
    try {
        val plain = aead(header.alg, false, key, header.nonce, aad, sealed, header.dialect)
        try {
            return plain.toString(Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    } finally {
        key.fill(0)
    }
}
