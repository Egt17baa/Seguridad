package app.seguridad.crypto.pk

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES

/*
 * «Abrir lo recibido»: qué es lo que ha llegado (pegado de un chat o importado de un .txt), sin descifrar ni comprobar
 * nada que no haga falta para decidirlo.
 */

/** Lo que es un texto recibido: una tarjeta, un mensaje cifrado, un texto firmado u otra cosa. */
sealed interface Received {
    /** Una tarjeta de contacto que se ha leído y es válida. */
    data class CardText(val card: Card) : Received

    /** Un mensaje cifrado: [token] empieza en `SGM1:` (con lo que venga detrás, para [decryptMessage]). Sin comprobar. */
    data class Message(val token: String) : Received

    /** Un texto firmado: [block] es todo el texto, para [verifySignedText]. Sin comprobar. */
    data class Signed(val block: String) : Received

    /** Ninguna de las anteriores. */
    data object Unknown : Received
}

/** Lo más largo que se lee del texto de una tarjeta, en caracteres: el límite que aplica [cardFromAnyText] (la mayor válida mide unos 2.100). */
private const val MAX_CARD_TEXT_CHARS = MAX_CARD_BYTES

/**
 * Decide qué es [text]:
 *
 * - Si el texto recortado es **entero** una tarjeta válida (en Base64url o en Base45, con los trozos que parta el chat),
 *   es [Received.CardText]. Va primero porque el cuerpo Base45 de un QR puede llevar por casualidad `SGM1:`, `SGS1:` o
 *   `SGK1:` (esos caracteres son del alfabeto Base45) y no por eso deja de ser la tarjeta.
 * - Si no, decide el **último** prefijo que haya entre [SIGNATURE_PREFIX] (`SGS1:`), [MESSAGE_PREFIX] (`SGM1:`) y
 *   [CARD_TEXT_PREFIX] (`SGK1:`), la misma regla que usa [decryptMessage] para elegir el `SGM1:`:
 *
 * - `SGS1:` da [Received.Signed]: lo que va antes es el texto firmado, así que un texto firmado que **contiene** un
 *   mensaje o una tarjeta en su cuerpo sigue siendo un texto firmado. Lo que firma es la última línea, que no se puede
 *   falsear desde dentro.
 * - `SGM1:` da [Received.Message] con el texto desde ese prefijo.
 * - `SGK1:` da [Received.CardText] solo si la tarjeta se lee y es válida (con trozos partidos por el chat y texto detrás);
 *   si no, [Received.Unknown].
 * - Sin prefijos, [Received.Unknown].
 *
 * Nunca lanza. Con más de [MAX_TEXT_FILE_BYTES] caracteres da [Received.Unknown] sin mirarlo. El mensaje y el texto
 * firmado no se comprueban aquí: eso lo hacen [decryptMessage] y [verifySignedText], con su error en español.
 */
fun detectReceived(text: String): Received {
    if (text.length > MAX_TEXT_FILE_BYTES) return Received.Unknown
    cardOrNull(text)?.let { return Received.CardText(it) }
    val signed = text.lastIndexOf(SIGNATURE_PREFIX)
    val message = text.lastIndexOf(MESSAGE_PREFIX)
    val card = text.lastIndexOf(CARD_TEXT_PREFIX)
    return when {
        signed >= 0 && signed > message && signed > card -> Received.Signed(text)
        message >= 0 && message > card -> Received.Message(text.substring(message))
        card >= 0 -> cardAt(text, card)?.let { Received.CardText(it) } ?: Received.Unknown
        else -> Received.Unknown
    }
}

/**
 * La tarjeta que empieza en [at]: primero tal cual hasta el final (lo normal, y la forma de QR en Base45) y, si no es
 * una tarjeta, con los trozos de Base64url que parta el chat, quitando de uno en uno los últimos (para que una palabra
 * como «Gracias» detrás no se pegue). `null` si ninguna lo es.
 *
 * De las uniones solo se leen (con [parseCard], que verifica la firma) como mucho [MAX_OPEN_ATTEMPTS], y solo cuentan las
 * que pueden ser una tarjeta: Base64url canónico y un tamaño que cuadra con su longitud de nombre. Así una frase larga
 * sin acentos ni puntuación detrás no la deja sin leer.
 */
private fun cardAt(text: String, at: Int): Card? {
    cardOrNull(text.substring(at))?.let { return it }
    val runs = base64UrlRuns(text, at + CARD_TEXT_PREFIX.length, MAX_CARD_TEXT_CHARS)
    var attempts = 0
    for (count in plausibleJoins(runs, MIN_CARD_BYTES, MIN_CARD_BYTES + MAX_NAME_BYTES)) {
        val raw = try {
            base64UrlDecode(runs.subList(0, count).joinToString(""))
        } catch (e: CryptoFailure) {
            continue
        }
        if (!hasCardLength(raw)) continue
        try {
            return parseCard(raw)
        } catch (e: Exception) {
            // Una unión que no es la tarjeta (firma, nombre o clave que no valen): se prueba la siguiente.
        }
        if (++attempts >= MAX_OPEN_ATTEMPTS) break
    }
    return null
}

/** [cardFromAnyText] con `null` en vez de [app.seguridad.crypto.CryptoFailure] (y con cualquier otra excepción, para no lanzar nunca). */
private fun cardOrNull(text: String): Card? = try {
    cardFromAnyText(text)
} catch (e: Exception) {
    null
}
