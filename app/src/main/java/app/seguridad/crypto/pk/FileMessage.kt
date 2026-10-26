package app.seguridad.crypto.pk

import app.seguridad.crypto.Aead
import app.seguridad.crypto.CHUNK_BYTES
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KEY_BYTES
import app.seguridad.crypto.MAX_FILE_NAME_BYTES
import app.seguridad.crypto.STREAM_PREFIX_BYTES
import app.seguridad.crypto.TAG_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.randomBytes
import app.seguridad.crypto.readFully
import app.seguridad.crypto.safeFileName
import app.seguridad.crypto.secureRandom
import app.seguridad.crypto.streamNonce
import org.bouncycastle.crypto.signers.Ed25519phSigner
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.SecureRandom

/*
 * Archivo cifrado para un contacto (diseño: «Archivo cifrado para un contacto (`.sgenc`)»). Se lee para siempre desde
 * la 1.2.0:
 *
 *   cabecera   = "SG" | 0x02 | 0x03 (archivo para un contacto) | 0x01 (suite) | enc (1120) | prefijo STREAM (7)
 *   clave      = HPKE (setupBaseS, info = "seguridad-file-v1").export("seguridad-file-key-v1", 32)
 *   registro 0 = longitud (2, como en `.sgenc` con contraseña) | sellado(marcas (1) | [clave Ed25519 del remitente (32)]
 *                | nombre del archivo en UTF-8)
 *   datos      = registros de 64 KiB de texto en claro (el último puede ser más corto), ninguno marcado como último
 *   final      = marcado como último: vacío si no está firmado, o la firma (64) si lo está
 *   firma      = Ed25519ph(contexto "seguridad-file-msg-sig-v1",
 *                          cabecera ‖ clave X-Wing del destinatario ‖ registro 0 en claro ‖ datos en claro)
 *
 * Todos los registros se sellan con ChaCha20-Poly1305, con el nonce STREAM de `FileCrypto.kt` (prefijo ‖ contador de
 * 4 bytes ‖ marca de último) y la cabecera completa como datos asociados: el registro 0 lleva el contador 0, los de datos
 * el 1, 2, … y el final el siguiente, con la marca de último. Así, truncar, quitar el final, repetir, reordenar o cambiar
 * un bit se detecta.
 *
 * Cómo se reconoce el final sin longitudes: los registros de después del 0 no llevan longitud. El lector sabe cuánto
 * mide el registro final (16 bytes, o 80 si el registro 0 dice «firmado») y que todos los de datos miden 64 KiB + 16
 * salvo el último, que puede ser más corto; por eso los últimos 16 u 80 bytes del flujo son siempre el final, y lo
 * anterior son los registros de datos.
 *
 * La firma lleva su propio contexto (`seguridad-file-msg-sig-v1`), distinto del de `.sgsig` (`seguridad-file-sig-v1`): una
 * firma de un `.sgsig` nunca vale dentro de un archivo cifrado. Cubre la cabecera (con su `enc` y su prefijo), la clave
 * del destinatario y el registro 0 (con el nombre), así que no se puede reenviar a otra persona ni cambiar el nombre.
 */

private const val FILE_INFO = "seguridad-file-v1"
private const val FILE_KEY_LABEL = "seguridad-file-key-v1"
private const val FILE_SIGNATURE_CONTEXT = "seguridad-file-msg-sig-v1"

private const val FLAG_SIGNED = 0x01
private const val FILE_HEADER_BYTES = 5 + ENC_BYTES + STREAM_PREFIX_BYTES
private const val RECORD_BYTES = CHUNK_BYTES + TAG_BYTES
private const val MIN_RECORD0_BYTES = 1 + TAG_BYTES
private const val MAX_RECORD0_BYTES = 1 + ED_PUBLIC_BYTES + MAX_FILE_NAME_BYTES + TAG_BYTES

private const val CANNOT_ENCRYPT_FILE = "No se ha podido cifrar el archivo para este contacto"
private const val TOO_BIG_FILE = "Archivo demasiado grande"
private const val DAMAGED_FILE = "El archivo está dañado o incompleto"
private const val UNKNOWN_FILE_MARKS = "El archivo usa una función que esta versión no conoce"
private const val BAD_FILE_SIGNATURE = "La firma del archivo no es válida"

/**
 * Lo que se ha abierto: el nombre (sin ruta, listo para guardar) y, si venía firmado y la firma es válida, la clave
 * Ed25519 de quien lo firmó. **Nada dice que esa clave sea de ningún contacto**: quien llama la compara con las que conoce.
 */
data class OpenedFile(val name: String, val senderEd: ByteArray?) {
    override fun equals(other: Any?): Boolean =
        other is OpenedFile && name == other.name && (senderEd contentEquals other.senderEd)

    override fun hashCode(): Int = name.hashCode() * 31 + (senderEd?.contentHashCode() ?: 0)

    /** Sin el nombre: que un registro o una pantalla de errores no lo copie sin querer. */
    override fun toString(): String = "OpenedFile(signed=${senderEd != null})"
}

/**
 * Cifra [input] para [recipient] y lo escribe en [output] en registros de 64 KiB, con [name] (sin ruta) en el registro 0.
 * Con [sender] el archivo va firmado (la firma lleva su clave Ed25519); con `null`, sin firmar. La memoria que se usa no
 * depende del tamaño del archivo. Lanza [CryptoFailure] con una clave del destinatario que Bouncy Castle rechaza (una
 * tarjeta de [parseCard] no la tiene, pero [Card] se puede construir a mano) y con un archivo de más de 2^32 registros;
 * toda excepción de Bouncy Castle sale como [CryptoFailure]. Los errores de lectura y de escritura son los del flujo.
 *
 * La semilla efímera y el prefijo STREAM salen de [random]. [input] y [output] los abre y los cierra quien llama.
 */
fun encryptFileFor(
    input: InputStream,
    output: OutputStream,
    name: String,
    recipient: Card,
    sender: Identity?,
    random: SecureRandom = secureRandom,
) {
    val ikmE = randomBytes(IKM_E_BYTES, random)
    val streamPrefix = randomBytes(STREAM_PREFIX_BYTES, random)
    try {
        encryptFileFor(input, output, name, recipient, sender, ikmE, streamPrefix)
    } finally {
        ikmE.fill(0)
    }
}

/**
 * [encryptFileFor] con la semilla efímera [ikmE] (64 bytes) y el prefijo STREAM [streamPrefix] (7 bytes) fijos, para los
 * vectores: con la misma entrada sale siempre el mismo archivo. Un tamaño que no es el suyo es un [CryptoFailure].
 * Nunca se usa con valores repetidos salvo en los tests: repetirlos repite la clave y los nonces.
 */
internal fun encryptFileFor(
    input: InputStream,
    output: OutputStream,
    name: String,
    recipient: Card,
    sender: Identity?,
    ikmE: ByteArray,
    streamPrefix: ByteArray,
) {
    if (ikmE.size != IKM_E_BYTES || streamPrefix.size != STREAM_PREFIX_BYTES) throw CryptoFailure(CANNOT_ENCRYPT_FILE)
    val nameBytes = safeFileName(name).toByteArray(Charsets.UTF_8)
    try {
        if (nameBytes.size > MAX_FILE_NAME_BYTES) throw CryptoFailure(CANNOT_ENCRYPT_FILE)
        val context = bouncy(CANNOT_ENCRYPT_FILE) {
            val suite = hpkeSuite()
            suite.setupBaseS(suite.deserializePublicKey(recipient.kemPublic), asciiLabel(FILE_INFO), ikmE)
        }
        val enc = context.encapsulation
        if (enc.size != ENC_BYTES) throw CryptoFailure(CANNOT_ENCRYPT_FILE)
        val header = pkHeader(PkKind.FILE_MESSAGE) + enc + streamPrefix
        val key = bouncy(CANNOT_ENCRYPT_FILE) { context.export(asciiLabel(FILE_KEY_LABEL), KEY_BYTES) }
        val chunk = ByteArray(CHUNK_BYTES)
        var record0: ByteArray? = null
        try {
            val signer = sender?.fileSigner(FILE_SIGNATURE_CONTEXT)
            record0 = if (sender == null) {
                byteArrayOf(0) + nameBytes
            } else {
                byteArrayOf(FLAG_SIGNED.toByte()) + sender.edPublic + nameBytes
            }
            // La firma cubre, en este orden: cabecera, clave X-Wing del destinatario, registro 0 en claro y datos en claro.
            if (signer != null) {
                val recipientKem = recipient.kemPublic
                signer.update(header, 0, header.size)
                signer.update(recipientKem, 0, recipientKem.size)
                signer.update(record0, 0, record0.size)
            }
            output.write(header)
            val sealed0 = sealRecord(key, header, streamPrefix, 0, false, record0)
            output.write(ByteBuffer.allocate(2).putShort(sealed0.size.toShort()).array())
            output.write(sealed0)

            var counter = 1
            while (true) {
                val n = input.readFully(chunk)
                if (n == 0) break
                signer?.update(chunk, 0, n)
                val plain = if (n == CHUNK_BYTES) chunk else chunk.copyOf(n)
                try {
                    output.write(sealRecord(key, header, streamPrefix, counter, false, plain))
                } finally {
                    if (plain !== chunk) plain.fill(0)
                }
                if (n < CHUNK_BYTES) { counter++; break } // un registro corto solo puede ser el último de datos
                if (++counter == -1) throw CryptoFailure(TOO_BIG_FILE) // Nunca se repite un nonce.
            }
            val finalPlain = bouncy(CANNOT_ENCRYPT_FILE) { signer?.generateSignature() } ?: ByteArray(0)
            output.write(sealRecord(key, header, streamPrefix, counter, true, finalPlain))
            output.flush()
        } finally {
            key.fill(0)
            chunk.fill(0)
            record0?.fill(0)
        }
    } finally {
        nameBytes.fill(0)
    }
}

/**
 * Abre un archivo de [encryptFileFor] para la identidad [me] y escribe lo descifrado en [output]; devuelve el nombre
 * (pasado por [safeFileName]) y, con firma válida, la clave del remitente (ver [OpenedFile]).
 *
 * **Se escribe a medida que se descifra.** Cada registro se autentica antes de escribirse, pero que el archivo esté
 * completo (que no le falte el final), y que la firma sea válida, solo se sabe al llegar al registro final. Con un
 * [CryptoFailure] la salida tiene un trozo del archivo o todo, sin comprobar: **quien llama tiene que borrarla** (se
 * escribe aparte y solo se da por buena si esta función vuelve) y no enseñar el remitente ni abrir lo escrito.
 *
 * Lanza [CryptoFailure], nunca otra excepción de cifrado (los errores del flujo son los suyos):
 * - [NOT_FOR_ME] si HPKE no lo abre: es de otra persona, o la cabecera o el registro 0 están manipulados;
 * - el rechazo de la cabecera si no es de la suite 1 o no es de un archivo para un contacto;
 * - con una marca desconocida (un bit distinto de 0 que no es el de «firmado»), un registro que no cuadra, un archivo
 *   truncado, con algo de más o con un registro cambiado de sitio;
 * - con la marca de «firmado» y una firma que no es válida: se rechaza entero, nunca se enseña como «sin firmar».
 */
fun decryptFileFor(input: InputStream, output: OutputStream, me: Identity): OpenedFile {
    val header = ByteArray(FILE_HEADER_BYTES)
    val read = input.readFully(header)
    parsePkHeader(header, PkKind.FILE_MESSAGE) // con menos de 5 bytes, los ceros del final no son la marca `SG`
    if (read != header.size) throw CryptoFailure(DAMAGED_FILE)
    val streamPrefix = header.copyOfRange(FILE_HEADER_BYTES - STREAM_PREFIX_BYTES, FILE_HEADER_BYTES)
    val keyPair = me.kemKeyPair() // una identidad cerrada es un error de programa, no de datos
    val key = bouncy(NOT_FOR_ME) {
        hpkeSuite().setupBaseR(header.copyOfRange(5, 5 + ENC_BYTES), keyPair, asciiLabel(FILE_INFO))
            .export(asciiLabel(FILE_KEY_LABEL), KEY_BYTES)
    }
    var record0: ByteArray? = null
    try {
        val lengthBytes = ByteArray(2)
        if (input.readFully(lengthBytes) != 2) throw CryptoFailure(DAMAGED_FILE)
        val length = ByteBuffer.wrap(lengthBytes).short.toInt() and 0xFFFF
        if (length < MIN_RECORD0_BYTES || length > MAX_RECORD0_BYTES) throw CryptoFailure(DAMAGED_FILE)
        val sealed0 = ByteArray(length)
        if (input.readFully(sealed0) != length) throw CryptoFailure(DAMAGED_FILE)
        // Es el primer registro que se autentica: si no abre, la clave es otra (no es para esta identidad) o algo está tocado.
        val plain0 = openRecord(key, header, streamPrefix, 0, false, sealed0, NOT_FOR_ME).also { record0 = it }

        val marks = plain0[0].toInt() and 0xFF
        if (marks and FLAG_SIGNED.inv() != 0) throw CryptoFailure(UNKNOWN_FILE_MARKS)
        val signed = marks == FLAG_SIGNED
        val nameAt = if (signed) 1 + ED_PUBLIC_BYTES else 1
        if (plain0.size < nameAt) throw CryptoFailure(DAMAGED_FILE)
        val name = try {
            safeFileName(decodeStrictUtf8(plain0.copyOfRange(nameAt, plain0.size)))
        } catch (e: CharacterCodingException) {
            throw CryptoFailure(DAMAGED_FILE)
        }
        val senderEd = if (signed) plain0.copyOfRange(1, nameAt) else null
        val verifier = senderEd?.let { fileVerifier(it, FILE_SIGNATURE_CONTEXT) }
        if (verifier != null) {
            val myKem = me.kemPublic
            verifier.update(header, 0, header.size)
            verifier.update(myKem, 0, myKem.size)
            verifier.update(plain0, 0, plain0.size)
        }

        val finalSize = TAG_BYTES + if (signed) SIG_BYTES else 0
        val window = ByteArray(RECORD_BYTES + finalSize)
        var have = input.readFully(window)
        var counter = 1
        while (true) {
            if (have == window.size) {
                // Con tantos bytes por delante, los primeros 64 KiB + 16 son un registro de datos completo.
                writeData(key, header, streamPrefix, counter, window, 0, RECORD_BYTES, verifier, output)
                if (++counter == -1) throw CryptoFailure(TOO_BIG_FILE) // Nunca se repite un nonce.
                System.arraycopy(window, RECORD_BYTES, window, 0, finalSize)
                have = finalSize + input.readFully(window, len = RECORD_BYTES, off = finalSize)
            } else {
                // Se acabó el flujo: los últimos `finalSize` bytes son el final y lo anterior, a lo sumo un registro de datos corto.
                if (have < finalSize) throw CryptoFailure(DAMAGED_FILE)
                val dataLength = have - finalSize
                if (dataLength > 0) {
                    if (dataLength <= TAG_BYTES) throw CryptoFailure(DAMAGED_FILE)
                    writeData(key, header, streamPrefix, counter, window, 0, dataLength, verifier, output)
                    counter++
                }
                val finalPlain = openRecord(key, header, streamPrefix, counter, true, window.copyOfRange(dataLength, have), DAMAGED_FILE)
                try {
                    if (verifier == null) {
                        if (finalPlain.isNotEmpty()) throw CryptoFailure(DAMAGED_FILE)
                    } else {
                        if (finalPlain.size != SIG_BYTES) throw CryptoFailure(DAMAGED_FILE)
                        if (!bouncy(BAD_FILE_SIGNATURE) { verifier.verifySignature(finalPlain) }) throw CryptoFailure(BAD_FILE_SIGNATURE)
                    }
                } finally {
                    finalPlain.fill(0)
                }
                break
            }
        }
        output.flush()
        return OpenedFile(name, senderEd)
    } finally {
        key.fill(0)
        record0?.fill(0)
    }
}

/**
 * Lo que dicen los 5 primeros bytes de [head] (el principio de un archivo): [PkKind.FILE_MESSAGE] para un archivo
 * cifrado para un contacto, [PkKind.FILE_SIGNATURE] para un `.sgsig`, y `null` para cualquier otra cosa, con menos de 5
 * bytes, o con otra versión o suite. Nunca lanza. Lo que viene después de los 5 bytes no se mira.
 */
fun pkKindOfFile(head: ByteArray): PkKind? =
    listOf(PkKind.FILE_MESSAGE, PkKind.FILE_SIGNATURE).firstOrNull { kind ->
        try {
            parsePkHeader(head, kind)
            true
        } catch (e: CryptoFailure) {
            false
        }
    }

/** Sella [plain] como el registro [counter] (el último, o no) de un archivo con esa [header] y ese [streamPrefix]. */
private fun sealRecord(key: ByteArray, header: ByteArray, streamPrefix: ByteArray, counter: Int, last: Boolean, plain: ByteArray): ByteArray =
    try {
        aead(Aead.CHACHA20_POLY1305, true, key, streamNonce(streamPrefix, counter, last), header, plain)
    } catch (e: Exception) {
        throw CryptoFailure(CANNOT_ENCRYPT_FILE)
    }

/**
 * Abre el registro sellado [sealed]; con [failure] si no se autentica (`aead` dice «Contraseña incorrecta», que aquí no
 * tiene sentido) o si Bouncy Castle lanza otra cosa.
 */
private fun openRecord(key: ByteArray, header: ByteArray, streamPrefix: ByteArray, counter: Int, last: Boolean, sealed: ByteArray, failure: String): ByteArray =
    try {
        aead(Aead.CHACHA20_POLY1305, false, key, streamNonce(streamPrefix, counter, last), header, sealed)
    } catch (e: Exception) {
        throw CryptoFailure(failure)
    }

/** Abre el registro de datos que hay en [window] entre [from] y [from] + [length], lo da a la firma y lo escribe. */
private fun writeData(
    key: ByteArray,
    header: ByteArray,
    streamPrefix: ByteArray,
    counter: Int,
    window: ByteArray,
    from: Int,
    length: Int,
    verifier: Ed25519phSigner?,
    output: OutputStream,
) {
    val plain = openRecord(key, header, streamPrefix, counter, false, window.copyOfRange(from, from + length), DAMAGED_FILE)
    try {
        verifier?.update(plain, 0, plain.size)
        output.write(plain)
    } finally {
        plain.fill(0)
    }
}
