package app.seguridad.crypto

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.SecureRandom

const val STREAM_PREFIX_BYTES = 7
const val CHUNK_BYTES = 64 * 1024
/** Extension of the files this app writes ([Dialect.SEGURIDAD]). */
const val ENCRYPTED_EXTENSION = ".sgenc"
/** Extension of the legacy dialect's files (.cpenc), still read. */
const val LEGACY_ENCRYPTED_EXTENSION = ".cpenc"
/** The most the sealed file name may measure, in UTF-8 bytes ([safeFileName] keeps it far below: 120 characters). Shared with the public-key files (`pk/FileMessage.kt`). */
internal const val MAX_FILE_NAME_BYTES = 1024

/** STREAM nonce: prefix (7) ‖ counter (4, big-endian) ‖ last-record flag (1). */
internal fun streamNonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
    ByteBuffer.allocate(NONCE_BYTES).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

/** Reads until [len] bytes are in [buf] (from [off]) or the stream ends; returns how many arrived. */
internal fun InputStream.readFully(buf: ByteArray, len: Int = buf.size, off: Int = 0): Int {
    var n = 0
    while (n < len) {
        val r = read(buf, off + n, len - n)
        if (r < 0) break
        n += r
    }
    return n
}

/**
 * Encrypts [input] to [output] in 64 KiB records (STREAM construction, as in age or Tink):
 * header, then the original file [name] as record 0, then the data; the last record is flagged, so truncation,
 * reordering and any changed bit are all detected. Memory use stays constant whatever the file size.
 */
fun encryptStream(
    input: InputStream,
    output: OutputStream,
    name: String,
    password: CharArray,
    alg: Aead = Aead.AES_256_GCM,
    kdf: KdfParams = KdfParams(),
    random: SecureRandom = secureRandom,
) = encryptStream(input, output, name, Secret.Password(password), alg, kdf, random)

fun encryptStream(
    input: InputStream,
    output: OutputStream,
    name: String,
    secret: Secret,
    alg: Aead = Aead.AES_256_GCM,
    kdf: KdfParams = KdfParams(),
    random: SecureRandom = secureRandom,
) {
    val header = newHeader(Header.KIND_FILE, alg, secret, kdf, random)
    val aad = header.bytes()
    val key = keyForHeader(secret, header)
    try {
        output.write(aad)
        val nameBytes = safeFileName(name).toByteArray(Charsets.UTF_8).let { if (it.size > MAX_FILE_NAME_BYTES) it.copyOf(MAX_FILE_NAME_BYTES) else it }
        val sealedName = aead(alg, true, key, streamNonce(header.nonce, 0, false), aad, nameBytes, header.dialect)
        output.write(ByteBuffer.allocate(2).putShort(sealedName.size.toShort()).array())
        output.write(sealedName)
        var counter = 1
        var current = ByteArray(CHUNK_BYTES)
        var currentLen = input.readFully(current)
        while (true) {
            val next = ByteArray(CHUNK_BYTES)
            val nextLen = if (currentLen == CHUNK_BYTES) input.readFully(next) else 0
            val last = nextLen == 0
            val plain = current.copyOf(currentLen)
            output.write(aead(alg, true, key, streamNonce(header.nonce, counter, last), aad, plain, header.dialect))
            plain.fill(0)
            current.fill(0)
            if (last) break
            if (++counter == -1) throw CryptoFailure("Archivo demasiado grande") // Never reuse a nonce.
            current = next
            currentLen = nextLen
        }
        output.flush()
    } finally {
        key.fill(0)
    }
}

/** Decrypts what [encryptStream] wrote; returns the original file name. Throws [CryptoFailure] on any problem. */
fun decryptStream(input: InputStream, output: OutputStream, password: CharArray): String =
    decryptStream(input, output, Secret.Password(password))

fun decryptStream(input: InputStream, output: OutputStream, secret: Secret): String {
    val pin = PushbackInputStream(input, 1)
    val fixed = ByteArray(Header.SIZE_BEFORE_NONCE + STREAM_PREFIX_BYTES)
    if (pin.readFully(fixed) != fixed.size) throw CryptoFailure("No es un archivo cifrado compatible")
    val header = Header.parse(ByteBuffer.wrap(fixed), Header.KIND_FILE)
    val aad = fixed
    val key = keyForHeader(secret, header)
    try {
        val lenBytes = ByteArray(2)
        if (pin.readFully(lenBytes) != 2) throw CryptoFailure()
        val nameLen = ByteBuffer.wrap(lenBytes).short.toInt() and 0xFFFF
        val overhead = header.alg.overhead
        if (nameLen < overhead || nameLen > MAX_FILE_NAME_BYTES + overhead) throw CryptoFailure()
        val sealedName = ByteArray(nameLen)
        if (pin.readFully(sealedName) != nameLen) throw CryptoFailure()
        val name = aead(header.alg, false, key, streamNonce(header.nonce, 0, false), aad, sealedName, header.dialect).toString(Charsets.UTF_8)
        var counter = 1
        val record = ByteArray(CHUNK_BYTES + overhead)
        while (true) {
            val n = pin.readFully(record)
            if (n < overhead) throw CryptoFailure() // Missing final record: truncated.
            val peek = pin.read()
            val last = peek < 0
            if (!last) pin.unread(peek)
            if (!last && n != record.size) throw CryptoFailure()
            val plain = aead(header.alg, false, key, streamNonce(header.nonce, counter, last), aad, record.copyOf(n), header.dialect)
            output.write(plain)
            plain.fill(0)
            if (last) break
            if (++counter == -1) throw CryptoFailure("Archivo demasiado grande") // Never reuse a nonce.
        }
        output.flush()
        return name
    } catch (e: EOFException) {
        throw CryptoFailure()
    } finally {
        key.fill(0)
    }
}

/** Name to save a decrypted file under: the stored one, never a path, nor turned around («factura\u202Efdp.exe»). */
fun safeFileName(name: String): String =
    withoutBidiControls(name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\x00-\\x1F]"), "")).trim().ifEmpty { "archivo" }.take(120)

/**
 * [text] without the characters that change the direction text is shown in: the bidi embeddings and overrides
 * (U+202A–U+202E), isolates (U+2066–U+2069) and marks (U+200E, U+200F, U+061C). With them «factura\u202Efdp.exe» shows
 * as «facturaexe.pdf», and a name from someone else could pass for another.
 */
fun withoutBidiControls(text: String): String =
    text.filterNot { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '\u200E' || it == '\u200F' || it == '\u061C' }
