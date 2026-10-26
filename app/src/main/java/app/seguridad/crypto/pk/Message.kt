package app.seguridad.crypto.pk

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.randomBytes
import app.seguridad.crypto.secureRandom
import java.nio.charset.CharacterCodingException
import java.security.SecureRandom

/*
 * Mensaje de texto cifrado para un contacto (diseño: «Mensaje cifrado para un contacto»). Se lee para siempre desde la
 * 1.2.0:
 *
 *   texto           = "SGM1:" + Base64url sin relleno de (cabecera ‖ cuerpo)
 *   cabecera        = "SG" | 0x02 | 0x02 (texto para un contacto) | 0x01 (suite) | enc (1120)
 *   cuerpo          = HPKE.seal(info = "seguridad-msg-v1", aad = cabecera, texto_interior)
 *   texto_interior  = marcas (1: bit 0 = firmado; el resto, a 0)
 *                   | [clave Ed25519 del remitente (32) | firma (64)]   solo si está firmado
 *                   | mensaje en UTF-8
 *   firma           = Ed25519("seguridad-msg-sig-v1" ‖ cabecera ‖ clave X-Wing del destinatario ‖ mensaje)
 *
 * La firma cubre la cabecera (con su `enc`) y la clave X-Wing del destinatario: quien abre un mensaje firmado para él y
 * lo vuelve a cifrar para otra persona con el mismo `texto_interior` no puede hacerlo pasar por suyo ni por «de A para
 * C», porque la firma ya no cuadra con la cabecera nueva ni con la clave de C.
 *
 * El mensaje no lleva el identificador del destinatario: si HPKE no lo abre se dice [NOT_FOR_ME], sin distinguir
 * «es de otra persona» de «está manipulado».
 */

/** Prefijo del texto de un mensaje cifrado. */
const val MESSAGE_PREFIX = "SGM1:"

/** Lo máximo que mide un mensaje, en bytes de UTF-8. */
const val MAX_MESSAGE_BYTES = 64 * 1024

/** Lo que se dice cuando HPKE no abre el mensaje (no es para esta identidad, o alguien lo ha tocado). */
const val NOT_FOR_ME = "No es para ti o está dañado"

internal const val BAD_MESSAGE_SIGNATURE = "La firma del mensaje no es válida"

private const val MESSAGE_INFO = "seguridad-msg-v1"
private const val MESSAGE_SIGNATURE_LABEL = "seguridad-msg-sig-v1"

/** Semilla efímera de la encapsulación de X-Wing (`Neseed` de la especificación, 64 bytes): el `ikmE` de [encryptMessage]. */
internal const val IKM_E_BYTES = 64

private const val MARK_SIGNED = 0x01
private const val HEADER_BYTES = 5 + ENC_BYTES // cabecera de 5 bytes y `enc`
private const val SIGNED_PART_BYTES = ED_PUBLIC_BYTES + SIG_BYTES
private const val AEAD_TAG_BYTES = 16

/** Lo mínimo y lo máximo que mide un mensaje decodificado: sin firma y vacío, y firmado y de 64 KiB. */
private const val MIN_RAW_BYTES = HEADER_BYTES + 1 + AEAD_TAG_BYTES
private const val MAX_RAW_BYTES = HEADER_BYTES + 1 + SIGNED_PART_BYTES + MAX_MESSAGE_BYTES + AEAD_TAG_BYTES

/** Lo máximo que mide el cuerpo en Base64url (los caracteres de [MAX_RAW_BYTES] bytes). */
private const val MAX_BODY_CHARS = (MAX_RAW_BYTES + 2) / 3 * 4

/**
 * Cuántas uniones de trozos se abren de verdad al leer un texto pegado (un descifrado HPKE o una lectura de tarjeta, que
 * es lo que cuesta): la unión de todos los trozos y, quitando el último, las demás. Las uniones que se descartan antes
 * (Base64url no canónico o tamaño imposible) no cuentan.
 */
internal const val MAX_OPEN_ATTEMPTS = 8

private const val NOT_A_MESSAGE = "El texto debe empezar por $MESSAGE_PREFIX"
private const val TOO_BIG = "El mensaje cifrado es demasiado grande"
private const val TOO_LONG = "El mensaje es demasiado largo: como máximo 64 KiB en UTF-8"
private const val BAD_TEXT = "El mensaje no es texto válido"
private const val CANNOT_ENCRYPT = "No se ha podido cifrar el mensaje para este contacto"
private const val UNKNOWN_MARKS = "El mensaje usa una función que esta versión no conoce"
private const val DAMAGED_MESSAGE = "El mensaje está dañado"

/** Lo que se ha abierto: el texto y, si venía firmado y la firma es válida, la clave Ed25519 de quien lo firmó. */
data class OpenedMessage(val text: String, val senderEd: ByteArray?) {
    override fun equals(other: Any?): Boolean =
        other is OpenedMessage && text == other.text && (senderEd contentEquals other.senderEd)

    override fun hashCode(): Int = text.hashCode() * 31 + (senderEd?.contentHashCode() ?: 0)

    /** Sin el texto: que un registro o una pantalla de errores no lo copie sin querer. */
    override fun toString(): String = "OpenedMessage(signed=${senderEd != null})"
}

/**
 * Cifra [text] para [recipient] y devuelve `"SGM1:"` y el mensaje en Base64url. Con [sender] va firmado (la firma lleva
 * su clave Ed25519); con `null`, sin firmar. Lanza [CryptoFailure] con un texto de más de [MAX_MESSAGE_BYTES] bytes en
 * UTF-8, con un sustituto suelto, o con una clave del destinatario que Bouncy Castle rechaza (una tarjeta de
 * [parseCard] no la tiene, pero [Card] se puede construir a mano). Toda excepción de Bouncy Castle sale como
 * [CryptoFailure]: nunca una excepción de otro tipo.
 *
 * La semilla efímera sale de [random]. El texto en claro de la entrada es un `String`, que no se puede poner a cero;
 * sí se ponen a cero sus bytes en UTF-8 y el `texto_interior` en cuanto se sella.
 */
fun encryptMessage(text: String, recipient: Card, sender: Identity?, random: SecureRandom = secureRandom): String {
    val ikmE = randomBytes(IKM_E_BYTES, random)
    try {
        return encryptMessage(text, recipient, sender, ikmE)
    } finally {
        ikmE.fill(0)
    }
}

/**
 * [encryptMessage] con la semilla efímera [ikmE] (64 bytes) fija, para los vectores: con la misma entrada sale siempre
 * el mismo mensaje. Una semilla de otro tamaño es un [CryptoFailure]. Nunca se usa con una semilla repetida salvo en
 * los tests: repetirla repite la clave de HPKE.
 */
internal fun encryptMessage(text: String, recipient: Card, sender: Identity?, ikmE: ByteArray): String {
    if (ikmE.size != IKM_E_BYTES) throw CryptoFailure(CANNOT_ENCRYPT)
    val message = try {
        strictUtf8(text)
    } catch (e: CharacterCodingException) {
        throw CryptoFailure(BAD_TEXT)
    }
    var inner: ByteArray? = null
    var signedData: ByteArray? = null
    try {
        if (message.size > MAX_MESSAGE_BYTES) throw CryptoFailure(TOO_LONG)
        val info = asciiLabel(MESSAGE_INFO)
        val context = bouncy(CANNOT_ENCRYPT) {
            val suite = hpkeSuite()
            suite.setupBaseS(suite.deserializePublicKey(recipient.kemPublic), info, ikmE)
        }
        val enc = context.encapsulation
        if (enc.size != ENC_BYTES) throw CryptoFailure(CANNOT_ENCRYPT)
        val header = pkHeader(PkKind.TEXT_MESSAGE) + enc

        inner = if (sender == null) {
            byteArrayOf(0) + message
        } else {
            signedData = header + recipient.kemPublic + message
            val signature = sender.sign(MESSAGE_SIGNATURE_LABEL, signedData)
            byteArrayOf(MARK_SIGNED.toByte()) + sender.edPublic + signature + message
        }
        val sealed = bouncy(CANNOT_ENCRYPT) { context.seal(header, inner) }
        return MESSAGE_PREFIX + base64UrlEncode(header + sealed)
    } finally {
        message.fill(0)
        inner?.fill(0)
        signedData?.fill(0)
    }
}

/**
 * Abre un mensaje de [encryptMessage] para la identidad [me]. [token] puede ser lo que se pegó de un chat: tras el
 * **último** [MESSAGE_PREFIX] (la misma regla que `detectReceived`) se leen los trozos de Base64url separados por
 * espacios o saltos de línea (los chats parten las líneas largas y a veces meten espacios), hasta el primer trozo con un
 * carácter que no es de Base64url (de él se toma el principio válido). Como detrás puede venir texto que también parece
 * Base64url («Gracias»), se prueba la unión de todos los trozos y, si no se abre, quitando de uno en uno los últimos.
 * Solo se consideran las uniones que decodifican en su forma canónica y miden lo que puede medir un mensaje, y de ellas
 * se abren como mucho [MAX_OPEN_ATTEMPTS] con HPKE: las descartadas antes no gastan intento, así que una frase larga sin
 * acentos ni puntuación detrás no impide abrir el mensaje. Lo que abre HPKE es el mensaje: una unión equivocada no pasa
 * la etiqueta de autenticación.
 *
 * Lanza [CryptoFailure] y nunca devuelve un texto sin comprobar:
 * - [NOT_FOR_ME] si ninguna unión se abre, sea por ser de otra persona, por estar manipulado (cualquier byte, incluida
 *   la `enc`), por un Base64url mal formado o por un tamaño imposible;
 * - si ninguna se abre y la mejor unión tiene una cabecera que no es de la suite 1 o no es de un mensaje de texto,
 *   el rechazo de la cabecera;
 * - con una marca desconocida (un bit distinto de 0 que no es el de «firmado») o un interior que no cuadra;
 * - con la marca de «firmado» y una firma que no es válida: se rechaza entero, nunca se enseña como «sin firmar».
 *
 * Con más de [MAX_TEXT_FILE_BYTES] caracteres pegados se rechaza sin mirarlos.
 *
 * Cuando hay firma válida, [OpenedMessage.senderEd] es la clave con la que se comprobó, pero **nada dice que esa clave
 * sea de ningún contacto**: quien llama la compara con las que conoce. El texto devuelto es un `String` y no se puede
 * poner a cero; el `texto_interior` descifrado y los bytes del mensaje sí.
 */
fun decryptMessage(token: String, me: Identity): OpenedMessage {
    if (token.length > MAX_TEXT_FILE_BYTES) throw CryptoFailure(TOO_BIG)
    val start = token.lastIndexOf(MESSAGE_PREFIX)
    if (start < 0) throw CryptoFailure(NOT_A_MESSAGE)
    val runs = base64UrlRuns(token, start + MESSAGE_PREFIX.length, MAX_BODY_CHARS)
    val keyPair = me.kemKeyPair() // una identidad cerrada es un error de programa, no de datos
    val info = asciiLabel(MESSAGE_INFO)

    var headerFailure: CryptoFailure? = null
    var opened: ByteArray? = null
    var header = ByteArray(0)
    var attempts = 0
    for (count in plausibleJoins(runs, MIN_RAW_BYTES, MAX_RAW_BYTES)) {
        val raw = try {
            base64UrlDecode(runs.subList(0, count).joinToString(""))
        } catch (e: CryptoFailure) {
            continue
        }
        try {
            parsePkHeader(raw, PkKind.TEXT_MESSAGE)
        } catch (e: CryptoFailure) {
            // Todas las uniones empiezan igual (cada una es el principio de la más larga) y la cabecera cabe en sus
            // primeros caracteres: si esta no es de un mensaje de texto, ninguna lo es.
            headerFailure = e
            break
        }
        attempts++
        val h = raw.copyOf(HEADER_BYTES)
        try {
            opened = hpkeSuite().setupBaseR(raw.copyOfRange(5, HEADER_BYTES), keyPair, info).open(h, raw.copyOfRange(HEADER_BYTES, raw.size))
            header = h
            break
        } catch (e: Exception) {
            // InvalidCipherTextException (la etiqueta no cuadra) y cualquier otra de Bouncy Castle: es lo mismo para quien lee.
        }
        if (attempts >= MAX_OPEN_ATTEMPTS) break
    }
    val inner = opened ?: throw (headerFailure ?: CryptoFailure(NOT_FOR_ME))
    var message: ByteArray? = null
    var signedData: ByteArray? = null
    try {
        if (inner.isEmpty()) throw CryptoFailure(DAMAGED_MESSAGE)
        val marks = inner[0].toInt() and 0xFF
        if (marks and MARK_SIGNED.inv() != 0) throw CryptoFailure(UNKNOWN_MARKS)
        val signed = marks == MARK_SIGNED
        val messageAt = if (signed) 1 + SIGNED_PART_BYTES else 1
        if (inner.size < messageAt) throw CryptoFailure(DAMAGED_MESSAGE)
        if (inner.size - messageAt > MAX_MESSAGE_BYTES) throw CryptoFailure(TOO_LONG)
        val text = inner.copyOfRange(messageAt, inner.size).also { message = it }
        var senderEd: ByteArray? = null
        if (signed) {
            val ed = inner.copyOfRange(1, 1 + ED_PUBLIC_BYTES)
            val signature = inner.copyOfRange(1 + ED_PUBLIC_BYTES, messageAt)
            signedData = header + me.kemPublic + text
            if (!verify(ed, MESSAGE_SIGNATURE_LABEL, signedData, signature)) throw CryptoFailure(BAD_MESSAGE_SIGNATURE)
            senderEd = ed
        }
        val decoded = try {
            decodeStrictUtf8(text)
        } catch (e: CharacterCodingException) {
            throw CryptoFailure(BAD_TEXT)
        }
        return OpenedMessage(decoded, senderEd)
    } finally {
        inner.fill(0)
        message?.fill(0)
        signedData?.fill(0)
    }
}

/**
 * Los trozos de Base64url de [text] desde [from], separados por espacios o saltos de línea: se para en el primero con un
 * carácter que no es de Base64url (del que se guarda su principio válido) o al pasar de [limit] caracteres en total.
 * Lo usa también «Abrir lo recibido» para leer una tarjeta (`Received.kt`).
 */
internal fun base64UrlRuns(text: String, from: Int, limit: Int): List<String> {
    val runs = ArrayList<String>()
    var total = 0
    var i = from
    while (i < text.length && total <= limit) {
        while (i < text.length && text[i].isWhitespace()) i++
        val begin = i
        while (i < text.length && isBase64UrlChar(text[i])) i++
        if (i > begin) {
            runs.add(text.substring(begin, i))
            total += i - begin
        }
        if (i < text.length && !text[i].isWhitespace()) break // un carácter que no es de Base64url: aquí acaba
    }
    return runs
}

/**
 * Los números de trozos de [runs] cuya unión (los primeros `count` trozos) puede ser un dato en Base64url canónico de
 * entre [minBytes] y [maxBytes] bytes, de la unión más larga a la más corta. No construye ninguna unión: lo decide con
 * las longitudes acumuladas y con el último carácter (con un resto de 2 caracteres los 4 bits sobrantes tienen que ser
 * cero, y con 3 los 2), que es lo mismo que comprueba [base64UrlDecode]. Cuesta lo que miden los trozos, y no lo que
 * mide cada unión por el número de uniones.
 */
internal fun plausibleJoins(runs: List<String>, minBytes: Int, maxBytes: Int): List<Int> {
    val totals = IntArray(runs.size)
    var total = 0
    for (i in runs.indices) {
        total += runs[i].length
        totals[i] = total
    }
    val counts = ArrayList<Int>()
    for (count in runs.size downTo 1) {
        val chars = totals[count - 1]
        val rest = chars % 4
        if (rest == 1) continue // un solo carácter no es un byte
        val bytes = chars / 4 * 3 + if (rest == 0) 0 else rest - 1
        if (bytes < minBytes || bytes > maxBytes) continue
        val last = base64UrlValue(runs[count - 1].last())
        if (rest == 2 && last and 0x0F != 0) continue
        if (rest == 3 && last and 0x03 != 0) continue
        counts.add(count)
    }
    return counts
}

/** El valor de 6 bits de un carácter de Base64url (RFC 4648 §5); [c] tiene que ser de [isBase64UrlChar]. */
private fun base64UrlValue(c: Char): Int = when (c) {
    in 'A'..'Z' -> c - 'A'
    in 'a'..'z' -> c - 'a' + 26
    in '0'..'9' -> c - '0' + 52
    '-' -> 62
    else -> 63
}

/**
 * Ejecuta [block] y convierte cualquier excepción de Bouncy Castle (`IllegalStateException`,
 * `IllegalArgumentException`, `InvalidCipherTextException`…) en un [CryptoFailure] con [failure]. No atrapa los
 * `Error` (falta de memoria).
 */
internal inline fun <T> bouncy(failure: String, block: () -> T): T = try {
    block()
} catch (e: CryptoFailure) {
    throw e
} catch (e: Exception) {
    throw CryptoFailure(failure)
}
