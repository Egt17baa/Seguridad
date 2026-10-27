package app.seguridad.crypto.pk

import app.seguridad.crypto.CryptoFailure
import org.bouncycastle.crypto.hpke.HPKE
import java.util.Base64

/*
 * Base de los formatos de clave pública de la 1.2.0 (diseño: «Primitivas y parámetros» y «Formatos»).
 * Todos empiezan por la misma cabecera de 5 bytes, y se leen para siempre.
 */

/** Versión de formato de todo lo de clave pública. La 1 es la de `.sgenc` con contraseña, que se lee aparte. */
const val PK_VERSION: Byte = 2

/** Suite 1: Ed25519 y HPKE (X-Wing, HKDF-SHA256, ChaCha20-Poly1305). La suite 0 no se usa. */
const val SUITE_1: Byte = 1

/** Segundo byte tras la marca `SG` y la versión: qué contiene el dato. */
enum class PkKind(val id: Byte) {
    CARD(1),
    TEXT_MESSAGE(2),
    FILE_MESSAGE(3),
    TEXT_SIGNATURE(4),
    FILE_SIGNATURE(5),
}

const val ED_PUBLIC_BYTES = 32
const val SIG_BYTES = 64
/** Clave pública de X-Wing: ML-KEM-768 (1184) y X25519 (32). */
const val KEM_PUBLIC_BYTES = 1216
/** Encapsulación de X-Wing: texto cifrado de ML-KEM-768 (1088) y clave efímera de X25519 (32). */
const val ENC_BYTES = 1120
/** Clave privada de X-Wing: una semilla de 32 bytes (Nsk = 32). */
const val KEM_SECRET_BYTES = 32

private const val NOT_COMPATIBLE = "No es un dato compatible"
private const val OTHER_KIND = "Es de otro tipo"

private const val MARK_S: Byte = 0x53 // 'S'
private const val MARK_G: Byte = 0x47 // 'G'
private const val HEADER_BYTES = 5

/** `"SG" | 0x02 | tipo | 0x01`: los 5 bytes con los que empieza todo dato de clave pública. */
fun pkHeader(kind: PkKind): ByteArray = byteArrayOf(MARK_S, MARK_G, PK_VERSION, kind.id, SUITE_1)

/**
 * Comprueba los 5 primeros bytes de [bytes] (lo que venga después no se mira aquí).
 * Una marca, versión o suite que no son las de esta versión, un tipo que no existe o menos de 5 bytes dan «No es un
 * dato compatible»; un tipo que existe pero no es [expected], «Es de otro tipo».
 */
fun parsePkHeader(bytes: ByteArray, expected: PkKind) {
    val known = bytes.size >= HEADER_BYTES && bytes[0] == MARK_S && bytes[1] == MARK_G &&
        bytes[2] == PK_VERSION && bytes[4] == SUITE_1
    val kind = if (known) PkKind.entries.firstOrNull { it.id == bytes[3] } else null
    if (kind == null) throw CryptoFailure(NOT_COMPATIBLE)
    if (kind != expected) throw CryptoFailure(OTHER_KIND)
}

/**
 * HPKE (RFC 9180) en modo base con la suite 1: KEM `0x647a` (X-Wing, MLKEM768-X25519), KDF `0x0001` (HKDF-SHA256) y
 * AEAD `0x0003` (ChaCha20-Poly1305). Una instancia nueva en cada llamada: no se comparte entre hilos.
 */
fun hpkeSuite(): HPKE = HPKE(HPKE.mode_base, HPKE.kem_X_WING, HPKE.kdf_HKDF_SHA256, HPKE.aead_CHACHA20_POLY1305)

/**
 * Los bytes ASCII de una etiqueta (sin `0x00`): el contexto de Ed25519ph y el `info` o el `export` de HPKE. Una
 * etiqueta vacía, con un `0x00` o fuera de ASCII imprimible rompería la separación de dominios: es un error de
 * programación y no de datos, así que lanza [IllegalArgumentException].
 */
internal fun asciiLabel(label: String): ByteArray {
    require(label.isNotEmpty() && label.all { it.code in 0x20..0x7E }) { "Etiqueta no válida" }
    return label.toByteArray(Charsets.US_ASCII)
}

/**
 * Etiqueta de dominio de Ed25519 puro: la etiqueta en ASCII y un byte `0x00`, y a continuación van los datos. Con el
 * `0x00` ninguna etiqueta es prefijo de otra, así que una firma de un formato no vale en otro.
 * (En Ed25519ph la etiqueta es el contexto del RFC 8032, y en el `info` y el `export` de HPKE va en ASCII, sin `0x00`.)
 */
fun labeled(label: String): ByteArray = asciiLabel(label) + 0

/** Base64url (RFC 4648 §5) sin relleno: el texto de las tarjetas, los mensajes y las firmas. */
internal fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** Si [c] es de `A-Z a-z 0-9 - _`, el alfabeto de Base64url. */
internal fun isBase64UrlChar(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'

/**
 * Lee Base64url sin relleno y en su forma canónica; lanza [CryptoFailure] con el relleno `=`, un carácter que no es
 * del alfabeto, una longitud imposible o bits sobrantes distintos de cero (el decodificador del JDK los acepta, y así
 * un mismo dato tendría varios textos). Quien llama limita antes el tamaño de [text].
 */
internal fun base64UrlDecode(text: String): ByteArray {
    if (!text.all(::isBase64UrlChar)) throw CryptoFailure(BAD_BASE64)
    val raw = try {
        Base64.getUrlDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        throw CryptoFailure(BAD_BASE64)
    }
    if (base64UrlEncode(raw) != text) throw CryptoFailure(BAD_BASE64)
    return raw
}

private const val BAD_BASE64 = "El texto no es Base64url válido"
