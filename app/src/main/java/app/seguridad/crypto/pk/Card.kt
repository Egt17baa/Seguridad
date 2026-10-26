package app.seguridad.crypto.pk

import app.seguridad.core.base45Decode
import app.seguridad.core.base45Encode
import app.seguridad.crypto.CryptoFailure
import org.bouncycastle.math.ec.rfc7748.X25519
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/*
 * Tarjeta de contacto (diseño: «Tarjeta de contacto»). Se lee para siempre desde la 1.2.0:
 *
 *   "SG" | 0x02 | 0x01 (tarjeta) | 0x01 (suite) | clave Ed25519 (32) | clave X-Wing (1216)
 *   | longitud del nombre (1) | nombre en UTF-8, NFC (0 a 64 bytes)
 *   | firma Ed25519 (64) de labeled("seguridad-card-v1") ‖ todo lo anterior
 *
 * El nombre no puede llevar caracteres de control (categoría Cc) ni controles de dirección del texto, y la parte X25519
 * de la clave X-Wing tiene que pasar la comprobación de RFC 7748 §6.1 (los puntos de orden pequeño se rechazan).
 *
 * La firma demuestra que quien tiene la clave Ed25519 eligió esa clave X-Wing y ese nombre. El nombre es solo una
 * sugerencia, sin ningún valor de seguridad: se enseña tal cual y quien guarda el contacto lo cambia.
 */

/** Prefijo del texto de una tarjeta, en las dos formas (Base64url y Base45). */
const val CARD_TEXT_PREFIX = "SGK1:"

/** Lo máximo que se lee: de una tarjeta en bytes y, tal cual llega, de su texto. La mayor válida mide 1.382 bytes. */
const val MAX_CARD_BYTES = 4096

/** Lo máximo que mide el nombre, ya en UTF-8 y normalizado. */
const val MAX_NAME_BYTES = 64

private const val CARD_LABEL = "seguridad-card-v1"

/** Treinta y dos bytes cualesquiera para la comprobación de la clave X25519 (X25519 los recorta, RFC 7748 §5): no son secretos. */
private val PROBE_SCALAR = ByteArray(32) { (it + 1).toByte() }

private const val X25519_PUBLIC_BYTES = 32
private const val ED_AT = 5 // tras la cabecera (`pkHeader` mide 5 bytes)
private const val KEM_AT = ED_AT + ED_PUBLIC_BYTES
private const val NAME_LENGTH_AT = KEM_AT + KEM_PUBLIC_BYTES
private const val NAME_AT = NAME_LENGTH_AT + 1
private const val MIN_CARD_BYTES = NAME_AT + SIG_BYTES

private const val TOO_BIG = "La tarjeta es demasiado grande"
private const val DAMAGED = "La tarjeta está dañada o incompleta"
private const val BAD_SIGNATURE = "La firma de la tarjeta no es válida"
private const val BAD_NAME = "El nombre de la tarjeta no es válido"
private const val NAME_TOO_LONG = "El nombre es demasiado largo: como máximo $MAX_NAME_BYTES bytes en UTF-8"
private const val BAD_NAME_CHARACTERS = "El nombre de la tarjeta tiene caracteres de control o de dirección del texto"
private const val BAD_KEY = "La clave de cifrado de la tarjeta no es válida"
private const val NOT_A_CARD = "El texto de la tarjeta debe empezar por $CARD_TEXT_PREFIX"
private const val BAD_CHARACTERS = "La tarjeta tiene caracteres que no son válidos"

/**
 * Lo que dice una tarjeta ya comprobada: las dos claves públicas y el nombre que propone. `equals` compara el contenido
 * de las claves (un `data class` con arrays compararía las referencias).
 */
data class Card(val edPublic: ByteArray, val kemPublic: ByteArray, val name: String) {
    override fun equals(other: Any?): Boolean =
        other is Card && name == other.name && edPublic.contentEquals(other.edPublic) &&
            kemPublic.contentEquals(other.kemPublic)

    override fun hashCode(): Int = (edPublic.contentHashCode() * 31 + kemPublic.contentHashCode()) * 31 + name.hashCode()
}

/**
 * La tarjeta de esta identidad con [name] como nombre propuesto. El nombre se normaliza a NFC y tiene que medir como
 * mucho [MAX_NAME_BYTES] bytes en UTF-8 (cuenta bytes, no caracteres) y ser texto (sin sustitutos sueltos);
 * si no, lanza [CryptoFailure]. Ed25519 es determinista: el mismo nombre da siempre los mismos bytes.
 */
fun Identity.cardBytes(name: String): ByteArray {
    val nameBytes = try {
        strictUtf8(Normalizer.normalize(name, Normalizer.Form.NFC))
    } catch (e: CharacterCodingException) {
        throw CryptoFailure(BAD_NAME)
    }
    if (nameBytes.size > MAX_NAME_BYTES) throw CryptoFailure(NAME_TOO_LONG)
    if (hasForbiddenNameCharacter(Normalizer.normalize(name, Normalizer.Form.NFC))) throw CryptoFailure(BAD_NAME_CHARACTERS)
    val body = pkHeader(PkKind.CARD) + edPublic + kemPublic + byteArrayOf(nameBytes.size.toByte()) + nameBytes
    return body + sign(CARD_LABEL, body)
}

/**
 * Lee y comprueba una tarjeta. Lanza [CryptoFailure] (nunca otra excepción) si no mide exactamente lo que dice su
 * longitud de nombre, si la cabecera no es la de una tarjeta de la suite 1, si la firma no es válida para su clave
 * Ed25519, si la clave X-Wing no se decodifica, o si el nombre mide más de [MAX_NAME_BYTES] bytes, no es UTF-8 válido
 * o no está en NFC. Con más de [MAX_CARD_BYTES] bytes se rechaza sin mirarla.
 */
fun parseCard(bytes: ByteArray): Card {
    if (bytes.size > MAX_CARD_BYTES) throw CryptoFailure(TOO_BIG)
    parsePkHeader(bytes, PkKind.CARD)
    if (bytes.size < MIN_CARD_BYTES) throw CryptoFailure(DAMAGED)
    val nameLength = bytes[NAME_LENGTH_AT].toInt() and 0xFF
    if (nameLength > MAX_NAME_BYTES) throw CryptoFailure(NAME_TOO_LONG)
    if (bytes.size != MIN_CARD_BYTES + nameLength) throw CryptoFailure(DAMAGED)

    val edPublic = bytes.copyOfRange(ED_AT, KEM_AT)
    val kemPublic = bytes.copyOfRange(KEM_AT, NAME_LENGTH_AT)
    val signedEnd = bytes.size - SIG_BYTES
    // `verify` no lanza: una clave que no es un punto de la curva, de orden pequeño o una firma mala dan false.
    if (!verify(edPublic, CARD_LABEL, bytes.copyOf(signedEnd), bytes.copyOfRange(signedEnd, bytes.size))) {
        throw CryptoFailure(BAD_SIGNATURE)
    }

    val name = try {
        decodeStrictUtf8(bytes.copyOfRange(NAME_AT, signedEnd))
    } catch (e: CharacterCodingException) {
        throw CryptoFailure(BAD_NAME)
    }
    if (!Normalizer.isNormalized(name, Normalizer.Form.NFC)) throw CryptoFailure(BAD_NAME)
    if (hasForbiddenNameCharacter(name)) throw CryptoFailure(BAD_NAME_CHARACTERS)

    // Que la clave X-Wing se decodifique no lo garantiza la firma, que solo dice quién la eligió. Bouncy Castle lanza
    // IllegalArgumentException (por ejemplo, con la comprobación de módulo de ML-KEM, FIPS 203 §7.2), y a veces otras.
    try {
        hpkeSuite().deserializePublicKey(kemPublic)
    } catch (e: RuntimeException) {
        throw CryptoFailure(BAD_KEY)
    }
    // La parte X25519 (los últimos 32 bytes) no puede ser de orden pequeño: Bouncy Castle la decodifica sin mirarlo y
    // el cifrado fallaría después. RFC 7748 §6.1: el acuerdo da todo ceros (aquí, `false`) con los 7 puntos de orden
    // pequeño. El escalar es uno cualquiera y no es secreto.
    if (!X25519.calculateAgreement(PROBE_SCALAR, 0, kemPublic, KEM_PUBLIC_BYTES - X25519_PUBLIC_BYTES, ByteArray(X25519_PUBLIC_BYTES), 0)) {
        throw CryptoFailure(BAD_KEY)
    }
    return Card(edPublic, kemPublic, name)
}

/** `"SGK1:"` y la tarjeta [bytes] en Base64url sin relleno, para pegarla en un chat o un correo. No comprueba [bytes]. */
fun cardText(bytes: ByteArray): String = CARD_TEXT_PREFIX + base64UrlEncode(bytes)

/** `"SGK1:"` y la tarjeta [bytes] en Base45, para el QR (todo en el alfabeto alfanumérico). No comprueba [bytes]. */
fun cardQr(bytes: ByteArray): String = CARD_TEXT_PREFIX + base45Encode(bytes)

/**
 * Lee una tarjeta de su texto, en la forma de [cardText] o en la de [cardQr].
 * - Con más de [MAX_CARD_BYTES] caracteres tal cual llegan se rechaza antes de quitar nada y de decodificar.
 * - Se quitan los espacios y saltos de línea de alrededor y, en Base64url, también los de dentro (los chats parten las
 *   líneas largas). En Base45 el espacio es un dígito y no se toca.
 * - Base64url se lee sin relleno y en su forma canónica: el relleno `=`, el alfabeto estándar (`+` y `/`) y los bits
 *   sobrantes distintos de cero se rechazan, para que cada tarjeta tenga un único texto.
 * Lanza [CryptoFailure] con cualquier fallo, también de decodificación.
 */
fun cardFromAnyText(text: String): Card {
    if (text.length > MAX_CARD_BYTES) throw CryptoFailure(TOO_BIG)
    val trimmed = text.trim()
    if (!trimmed.startsWith(CARD_TEXT_PREFIX)) throw CryptoFailure(NOT_A_CARD)
    val body = trimmed.substring(CARD_TEXT_PREFIX.length)
    val compact = body.filterNot { it.isWhitespace() }

    // Un texto solo con [0-9A-Z-] cabe en los dos alfabetos: se prueba como Base64url y, si no es una tarjeta, como
    // Base45. Una tarjeta real (su cabecera, su firma) solo es válida en una de las dos lecturas.
    var failure: CryptoFailure? = null
    if (compact.all(::isBase64UrlChar)) {
        try {
            return parseCard(
                try {
                    base64UrlDecode(compact)
                } catch (e: CryptoFailure) {
                    throw CryptoFailure(DAMAGED)
                },
            )
        } catch (e: CryptoFailure) {
            failure = e
        }
    }
    if (body.all(::isBase45Char)) {
        try {
            return parseCard(base45DecodeOrFail(body))
        } catch (e: CryptoFailure) {
            failure = failure ?: e
        }
    }
    throw failure ?: CryptoFailure(BAD_CHARACTERS)
}

/**
 * Si [name] (ya en NFC) tiene un carácter de control Unicode (categoría Cc: U+0000 a U+001F y U+007F a U+009F, con CR,
 * LF y TAB) o un control de dirección del texto (U+061C, U+200E, U+200F, U+202A a U+202E, U+2066 a U+2069), que
 * permiten que un nombre se enseñe distinto de lo que dice.
 */
private fun hasForbiddenNameCharacter(name: String): Boolean = name.any {
    Character.getType(it) == Character.CONTROL.toInt() ||
        it == '\u061C' || it == '\u200E' || it == '\u200F' || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069'
}

/** [base45Decode] con [CryptoFailure] en vez de [IllegalArgumentException]. */
private fun base45DecodeOrFail(text: String): ByteArray = try {
    base45Decode(text)
} catch (e: IllegalArgumentException) {
    throw CryptoFailure(DAMAGED)
}

private fun isBase45Char(c: Char) = c in '0'..'9' || c in 'A'..'Z' || c in " $%*+-./:"

/** Los bytes UTF-8 de [s]; lanza [CharacterCodingException] con un sustituto suelto en vez de poner «?». */
private fun strictUtf8(s: String): ByteArray {
    val encoded = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(s))
    return ByteArray(encoded.remaining()).also { encoded.get(it) }
}

/** El texto de [b]; lanza [CharacterCodingException] con UTF-8 mal formado, sobrelargo, CESU-8 o mayor que U+10FFFF. */
private fun decodeStrictUtf8(b: ByteArray): String =
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(b))
        .toString()
