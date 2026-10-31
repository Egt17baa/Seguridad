package app.seguridad.crypto

import app.seguridad.crypto.pk.isForbiddenNameCodePoint
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
/** The most the sealed file name may measure, in UTF-8 bytes ([safeFileName] keeps it far below: [MAX_SAVED_NAME_BYTES]). Shared with the public-key files (`pk/FileMessage.kt`). */
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
        // safeFileName keeps it within MAX_SAVED_NAME_BYTES (255); a longer one would be a bug, never cut (it could split a
        // UTF-8 character).
        val nameBytes = safeFileName(name).toByteArray(Charsets.UTF_8)
        if (nameBytes.size > MAX_FILE_NAME_BYTES) throw CryptoFailure("No se ha podido cifrar el archivo")
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

/** The most code points [safeFileName] keeps (before the byte limit, [MAX_SAVED_NAME_BYTES]). */
internal const val MAX_FILE_NAME_CODE_POINTS = 120

/**
 * The most UTF-8 bytes of a name that is written or proposed to save under (A16b, design: «sin pasar de 255 bytes»; many
 * file systems and some SAF providers refuse longer names). Only for writing and saving: reading still accepts up to
 * [MAX_FILE_NAME_BYTES].
 */
const val MAX_SAVED_NAME_BYTES = 255

/**
 * CIFRADO-2 (audit 1.3.0): a code point no name that is shown or saved keeps, the one list of [safeFileName] and
 * `sanitizeReceivedName` (they used to differ). Taken out:
 *  - everything the fixed list of card names forbids (`isForbiddenNameCodePoint`, `crypto/pk/Card.kt`): C0, DEL, C1,
 *    U+2028/2029, the soft hyphen, CGJ U+034F, the Hangul fillers U+115F, U+1160, U+3164 and U+FFA0, the Khmer inherent
 *    vowels U+17B4/17B5, U+180E, zero width, LRM/RLM and the bidi controls, U+2060–206F, U+FEFF, U+FFF0–FFFB, U+FFFE/FFFF,
 *    lone surrogates, private use, and U+E0000–E0FFF (the tags and the supplementary variation selectors);
 *  - every other format character (category Cf, such as U+200C or U+0600–0605), except the zero-width joiner, which is
 *    left to [withoutLooseJoiners];
 *  - the variation selectors U+FE00–FE0E (U+FE0F, the emoji one, is left to [withoutLooseSelectors]);
 *  - the noncharacters U+FDD0–FDEF and the last two code points of every plane.
 * The card list is fixed (it is also the read rule of a card); here it is only shown or saved, so it may take more.
 */
internal fun isHiddenInName(cp: Int): Boolean = cp != ZWJ && (
    isForbiddenNameCodePoint(cp) ||
        Character.getType(cp) == Character.FORMAT.toInt() ||
        cp in 0xFE00..0xFE0E ||
        cp in 0xFDD0..0xFDEF || (cp and 0xFFFE) == 0xFFFE
    )

/**
 * [s] without the code points of [isHiddenInName]. A lone surrogate (half of a pair: not text, but visible as «�») is
 * [loneSurrogate] if given, or goes: [safeFileName] keeps the «?» that UTF-8 has always written for it inside a file
 * (`FileMessageTest`), and `sanitizeReceivedName` drops it.
 */
internal fun withoutHiddenNameCharacters(s: String, loneSurrogate: Char? = null): String {
    val out = StringBuilder(s.length)
    s.codePoints().forEach { cp ->
        when {
            cp in 0xD800..0xDFFF -> loneSurrogate?.let { out.append(it) }
            !isHiddenInName(cp) -> out.appendCodePoint(cp)
        }
    }
    return out.toString()
}

private const val ZWJ = 0x200D
private const val EMOJI_SELECTOR = 0xFE0F

/** What [safeFileName] trims from both ends: dots and spaces (also the no-break ones), as `sanitizeReceivedName` does. */
private fun isNameTrimmable(c: Char) = c == '.' || c.isWhitespace() || Character.isSpaceChar(c)

/** A code point a kept zero-width joiner may touch: not ASCII, not a space, not another joiner (an emoji, in practice). */
private fun canJoin(cp: Int) = cp >= 0x80 && cp != ZWJ && !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)

/**
 * A code point U+FE0F may follow: one that [canJoin] (an emoji or symbol, in practice) and is not another U+FE0F, or the
 * base of a keycap emoji («1️⃣»: 0–9, # and *). A fixed rule, like the joiner's: no Unicode tables that change with the
 * JDK.
 */
private fun canTakeEmojiSelector(cp: Int) =
    (canJoin(cp) && cp != EMOJI_SELECTOR) || cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

/**
 * [s] without the emoji presentation selectors (U+FE0F) that show nothing: one at the start or after a code point that
 * cannot take it ([canTakeEmojiSelector]: ASCII other than a keycap base, a space, a joiner or another selector) goes
 * («fac\uFE0Ftura» is «factura»). One after an emoji or a symbol («❤️») stays. Idempotent: what a kept selector follows
 * is never removed by this, by [withoutLooseJoiners] or by a trim.
 */
internal fun withoutLooseSelectors(s: String): String {
    if (s.indexOf('\uFE0F') < 0) return s
    val out = StringBuilder(s.length)
    var previous = -1
    s.codePoints().forEach { cp ->
        if (cp == EMOJI_SELECTOR && !(previous >= 0 && canTakeEmojiSelector(previous))) return@forEach
        out.appendCodePoint(cp)
        previous = cp
    }
    return out.toString()
}

/**
 * [s] without the zero-width joiners (U+200D) that do not sit between two code points that [canJoin]: one at either end,
 * next to ASCII, next to a space or next to another joiner goes («fac\u200Dtura» is «factura»). One between two emoji
 * («👨‍👩‍👧») stays: it only joins them into one picture. Idempotent: the neighbours of a kept joiner are never removed.
 */
internal fun withoutLooseJoiners(s: String): String {
    if (s.indexOf('\u200D') < 0) return s
    val cps = s.codePoints().toArray()
    val out = StringBuilder(s.length)
    for (i in cps.indices) {
        val cp = cps[i]
        if (cp == ZWJ && !(i > 0 && i < cps.size - 1 && canJoin(cps[i - 1]) && canJoin(cps[i + 1]))) continue
        out.appendCodePoint(cp)
    }
    return out.toString()
}

/**
 * [withoutLooseSelectors], [withoutLooseJoiners] and trimming with [trim], until none changes anything (a trim can leave
 * a joiner or a selector at an end).
 */
internal fun trimWithJoiners(s: String, trim: (String) -> String): String {
    var current = s
    while (true) {
        val next = trim(withoutLooseJoiners(withoutLooseSelectors(current)))
        if (next == current) return current
        current = next
    }
}

/**
 * Name to save a decrypted file under, and the one written inside a new file: the stored one, never a path, nor turned
 * around («factura\u202Efdp.exe»). Taken out: every code point of [isHiddenInName] (CIFRADO-2: the controls, the format
 * characters, the bidi controls, the Hangul fillers and the other blanks and invisibles of the card list, U+FE00–FE0E,
 * private use and the noncharacters), the same list as `sanitizeReceivedName`; a lone surrogate becomes «?», the byte
 * that is written for it anyway. That covers the invisible characters known to make one name look like another (Unicode
 * TR #36), not every look-alike: letters of other scripts that look the same stay. The zero-width joiner (U+200D) stays only between two non-ASCII code points that are not spaces (an
 * emoji sequence such as «👨‍👩‍👧»), and U+FE0F only after an emoji, a symbol or a keycap base («❤️», «1️⃣»): elsewhere
 * they go ([withoutLooseJoiners], [withoutLooseSelectors]), also after the cuts. No dots or spaces at either end (so
 * never «.» nor «..»); with nothing visible left (only joiners, fillers, dots or spaces), it is «archivo».
 *
 * At most [MAX_FILE_NAME_CODE_POINTS] code points and [MAX_SAVED_NAME_BYTES] UTF-8 bytes minus [reservedBytes] (the
 * bytes of a suffix the caller appends, such as «.sgenc»), always cut between whole code points (A16b: never half of a
 * surrogate pair, never inside a UTF-8 character). Idempotent. It only shortens, never rejects: a file written with a
 * longer name (up to [MAX_FILE_NAME_BYTES]) still decrypts, and the name inside an old file is read as it is; only the
 * name proposed or shown changes.
 */
fun safeFileName(name: String, reservedBytes: Int = 0): String {
    val maxBytes = (MAX_SAVED_NAME_BYTES - reservedBytes).coerceAtLeast(1)
    val trim = { t: String -> t.trim(::isNameTrimmable) }
    val clean = trimWithJoiners(withoutHiddenNameCharacters(name.substringAfterLast('/').substringAfterLast('\\'), loneSurrogate = '?'), trim)
    val cut = trimWithJoiners(takeUtf8Bytes(takeCodePoints(clean, MAX_FILE_NAME_CODE_POINTS), maxBytes), trim)
    return if (cut.isEmpty() || cut == "." || cut == "..") "archivo" else cut
}

/** The first [max] code points of [s] (never half of a surrogate pair). */
internal fun takeCodePoints(s: String, max: Int): String =
    if (s.codePointCount(0, s.length) <= max) s else s.substring(0, s.offsetByCodePoints(0, max))

/** The longest start of [s] made of whole code points whose UTF-8 measures at most [maxBytes]. */
internal fun takeUtf8Bytes(s: String, maxBytes: Int): String {
    var bytes = 0
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        // A lone surrogate is written as «?» (1 byte); counting 3 only keeps the name shorter.
        val n = when {
            cp < 0x80 -> 1
            cp < 0x800 -> 2
            cp < 0x10000 -> 3
            else -> 4
        }
        if (bytes + n > maxBytes) break
        bytes += n
        i += Character.charCount(cp)
    }
    return s.substring(0, i)
}

/**
 * [text] without the characters that change the direction text is shown in: the bidi embeddings and overrides
 * (U+202A–U+202E), isolates (U+2066–U+2069) and marks (U+200E, U+200F, U+061C). With them «factura\u202Efdp.exe» shows
 * as «facturaexe.pdf», and a name from someone else could pass for another.
 */
fun withoutBidiControls(text: String): String =
    text.filterNot { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '\u200E' || it == '\u200F' || it == '\u061C' }
