package app.seguridad.crypto.pk

import app.seguridad.crypto.CHUNK_BYTES
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.readFully
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.text.Normalizer

/*
 * Firmas de clave pública (diseño: «Firma de un texto» y «Firma de un archivo»). Se leen para siempre desde la 1.2.0.
 *
 * Texto: el texto normalizado, un salto de línea y la línea `SGS1:` + Base64url sin relleno de
 *
 *   "SG" | 0x02 | 0x04 (firma de texto) | 0x01 (suite) | clave Ed25519 del firmante (32)
 *   | firma Ed25519 (64) de "seguridad-text-sig-v1" 00 ‖ texto normalizado en UTF-8
 *
 * Archivo (`.sgsig`, exactamente 101 bytes):
 *
 *   "SG" | 0x02 | 0x05 (firma de archivo) | 0x01 (suite) | clave Ed25519 del firmante (32)
 *   | firma Ed25519ph (64), contexto "seguridad-file-sig-v1", del contenido del archivo (SHA-512 lo aplica el firmador)
 *
 * El contexto de `.sgsig` no es el de la firma dentro de un archivo cifrado para un contacto
 * (`seguridad-file-msg-sig-v1`): si lo fuera, alguien podría convencer a Ana de firmar un archivo preparado y usar su
 * firma como si ella hubiera enviado un archivo cifrado. Por eso una no vale como la otra.
 *
 * La firma solo dice «esta clave firmó esto»: **nada dice que la clave sea de ningún contacto**. Quien llama la compara
 * con las que conoce.
 */

/** Prefijo de la línea con la firma de un texto. */
const val SIGNATURE_PREFIX = "SGS1:"

/** Extensión del archivo con la firma de otro archivo. */
const val SIGNATURE_EXTENSION = ".sgsig"

private const val TEXT_SIG_LABEL = "seguridad-text-sig-v1"
private const val FILE_SIG_CONTEXT = "seguridad-file-sig-v1"

private const val HEADER_BYTES = 5
private const val SIGNER_AT = HEADER_BYTES
private const val SIG_AT = SIGNER_AT + ED_PUBLIC_BYTES

/** Lo que mide la firma, en bytes (cabecera, clave y firma) y en caracteres de Base64url sin relleno: 101 y 135. */
private const val SIGNATURE_RAW_BYTES = SIG_AT + SIG_BYTES
private const val SIGNATURE_TOKEN_CHARS = (SIGNATURE_RAW_BYTES * 4 + 2) / 3

/** Lo más largo que puede ser el texto a firmar: con el salto de línea y la línea de la firma, el bloque cabe en [MAX_TEXT_FILE_BYTES]. */
private const val MAX_SIGNED_TEXT_BYTES = MAX_TEXT_FILE_BYTES - 1 - SIGNATURE_PREFIX.length - SIGNATURE_TOKEN_CHARS

private const val TOO_LONG_TEXT = "El texto es demasiado largo para firmarlo"
private const val TOO_BIG_TEXT = "El texto es demasiado grande"
private const val BAD_TEXT = "El texto no es válido"
private const val NOT_SIGNED = "El texto no lleva una firma ($SIGNATURE_PREFIX)"
private const val DAMAGED_SIGNATURE = "La firma está dañada o incompleta"
private const val BAD_TEXT_SIGNATURE = "La firma no es válida: el texto ha cambiado o no es de quien dice"
private const val CANNOT_SIGN_FILE = "No se ha podido firmar el archivo"
private const val BAD_FILE_SIGNATURE = "La firma no es válida: el archivo ha cambiado o no es de quien dice"

/**
 * El texto que se firma y se comprueba: Unicode NFC, `\r\n` y `\r` pasados a `\n`, y sin espacios ni saltos al
 * principio ni al final (`trim()`). Así los cambios que suelen hacer las apps de chat no rompen la firma. Es idempotente.
 */
fun normalizeSignedText(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n').trim()

/**
 * Firma [text] con [me]: da el texto normalizado, un `\n` y la línea `SGS1:` con la firma. Ed25519 es determinista: el
 * mismo texto da siempre el mismo bloque. Lanza [CryptoFailure] con un sustituto suelto o con un texto de tal tamaño
 * que el bloque pasaría de [MAX_TEXT_FILE_BYTES] (lo máximo que se lee al verificar).
 */
fun signText(text: String, me: Identity): String {
    val normalized = normalizeSignedText(text)
    val bytes = try {
        strictUtf8(normalized)
    } catch (e: CharacterCodingException) {
        throw CryptoFailure(BAD_TEXT)
    }
    try {
        if (bytes.size > MAX_SIGNED_TEXT_BYTES) throw CryptoFailure(TOO_LONG_TEXT)
        val signature = me.sign(TEXT_SIG_LABEL, bytes)
        return normalized + "\n" + SIGNATURE_PREFIX + base64UrlEncode(pkHeader(PkKind.TEXT_SIGNATURE) + me.edPublic + signature)
    } finally {
        bytes.fill(0)
    }
}

/**
 * El texto firmado, ya normalizado, y la clave Ed25519 de quien lo firmó (32 bytes). Con `equals` por el contenido de
 * la clave; sin el texto en `toString`.
 */
data class SignedText(val text: String, val signerEd: ByteArray) {
    override fun equals(other: Any?): Boolean = other is SignedText && text == other.text && signerEd.contentEquals(other.signerEd)

    override fun hashCode(): Int = text.hashCode() * 31 + signerEd.contentHashCode()

    override fun toString(): String = "SignedText(length=${text.length})"
}

/**
 * Comprueba un texto firmado y da el texto y la clave del firmante, o lanza [CryptoFailure] (nunca otra excepción).
 *
 * - Se toma la **última** aparición de [SIGNATURE_PREFIX]: lo que va antes, normalizado igual que al firmar, es el texto
 *   firmado, y la firma tiene que cuadrar con eso. Una línea `SGS1:` falsa antes (o dentro del texto) solo forma parte
 *   del texto; una añadida después deja el bloque sin firma válida.
 * - La firma mide siempre [SIGNATURE_TOKEN_CHARS] caracteres: si el chat la ha partido en varias líneas o con espacios se
 *   juntan los trozos hasta completarlos, y lo que venga después («Gracias») no se pega. Se lee en Base64url canónico.
 * - Con más de [MAX_TEXT_FILE_BYTES] caracteres se rechaza sin mirarlo.
 *
 * La clave que se devuelve es con la que se comprobó la firma, pero **nada dice que sea de un contacto**.
 */
fun verifySignedText(block: String): SignedText {
    if (block.length > MAX_TEXT_FILE_BYTES) throw CryptoFailure(TOO_BIG_TEXT)
    val at = block.lastIndexOf(SIGNATURE_PREFIX)
    if (at < 0) throw CryptoFailure(NOT_SIGNED)
    val raw = base64UrlDecode(signatureToken(block, at + SIGNATURE_PREFIX.length))
    if (raw.size != SIGNATURE_RAW_BYTES) throw CryptoFailure(DAMAGED_SIGNATURE)
    parsePkHeader(raw, PkKind.TEXT_SIGNATURE)
    val signerEd = raw.copyOfRange(SIGNER_AT, SIG_AT)
    val signature = raw.copyOfRange(SIG_AT, SIGNATURE_RAW_BYTES)
    val text = normalizeSignedText(block.substring(0, at))
    val bytes = try {
        strictUtf8(text)
    } catch (e: CharacterCodingException) {
        throw CryptoFailure(BAD_TEXT)
    }
    try {
        // `verify` no lanza: una clave que no es un punto de la curva o una firma mala dan false.
        if (!verify(signerEd, TEXT_SIG_LABEL, bytes, signature)) throw CryptoFailure(BAD_TEXT_SIGNATURE)
    } finally {
        bytes.fill(0)
    }
    return SignedText(text, signerEd)
}

/** Los [SIGNATURE_TOKEN_CHARS] caracteres de Base64url que empiezan en [from], con los trozos que haya partido el chat. */
private fun signatureToken(block: String, from: Int): String {
    val token = StringBuilder()
    for (run in base64UrlRuns(block, from, SIGNATURE_TOKEN_CHARS)) {
        if (token.length + run.length > SIGNATURE_TOKEN_CHARS) throw CryptoFailure(DAMAGED_SIGNATURE)
        token.append(run)
        if (token.length == SIGNATURE_TOKEN_CHARS) return token.toString()
    }
    throw CryptoFailure(DAMAGED_SIGNATURE)
}

/**
 * Los bytes del `.sgsig` de [input] con [me] (101 exactamente), que se lee entero en bloques de 64 KiB: la memoria no
 * crece con el tamaño. Ed25519ph aplica SHA-512 él mismo: se le dan los datos tal cual. No cierra [input]. Lanza
 * [CryptoFailure] si el firmador falla; los errores de lectura salen como `IOException`.
 */
fun signFile(input: InputStream, me: Identity): ByteArray {
    val signer = me.fileSigner(FILE_SIG_CONTEXT)
    val chunk = ByteArray(CHUNK_BYTES)
    try {
        while (true) {
            val n = input.readFully(chunk)
            if (n == 0) break
            signer.update(chunk, 0, n)
            if (n < chunk.size) break
        }
        val signature = bouncy(CANNOT_SIGN_FILE) { signer.generateSignature() }
        return pkHeader(PkKind.FILE_SIGNATURE) + me.edPublic + signature
    } finally {
        chunk.fill(0)
    }
}

/**
 * Comprueba que [sig] (un `.sgsig`) es de [input] y da la clave Ed25519 de quien lo firmó, o lanza [CryptoFailure]. Se
 * rechaza sin leer [input] un `.sgsig` que no mide exactamente 101 bytes, que no es de la suite 1 o es de otro tipo (por
 * ejemplo, un archivo cifrado). Una firma hecha con otro contexto (como la de dentro de un archivo cifrado para un
 * contacto) no vale. [input] se lee entero, en bloques de 64 KiB, y no se cierra. **Nada dice que la clave que se
 * devuelve sea de un contacto**: quien llama la compara con las que conoce.
 */
fun verifyFileSignature(input: InputStream, sig: ByteArray): ByteArray {
    parsePkHeader(sig, PkKind.FILE_SIGNATURE)
    if (sig.size != SIGNATURE_RAW_BYTES) throw CryptoFailure(DAMAGED_SIGNATURE)
    val signerEd = sig.copyOfRange(SIGNER_AT, SIG_AT)
    val verifier = fileVerifier(signerEd, FILE_SIG_CONTEXT)
    val chunk = ByteArray(CHUNK_BYTES)
    try {
        while (true) {
            val n = input.readFully(chunk)
            if (n == 0) break
            verifier.update(chunk, 0, n)
            if (n < chunk.size) break
        }
    } finally {
        chunk.fill(0)
    }
    val valid = bouncy(BAD_FILE_SIGNATURE) { verifier.verifySignature(sig.copyOfRange(SIG_AT, SIGNATURE_RAW_BYTES)) }
    if (!valid) throw CryptoFailure(BAD_FILE_SIGNATURE)
    return signerEd
}
