package app.seguridad.crypto.pk

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KEY_BYTES
import app.seguridad.crypto.randomBytes
import app.seguridad.crypto.secureRandom
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.Ed25519phSigner
import java.security.SecureRandom

private const val ED_INFO = "seguridad-identity-ed25519-v1"
private const val XWING_INFO = "seguridad-identity-xwing-v1"
private const val FINGERPRINT_LABEL = "seguridad-fingerprint-v1"
private const val FINGERPRINT_GROUPS = 6
private const val FINGERPRINT_GROUP_BYTES = 5
private const val FINGERPRINT_MODULUS = 100_000L

private const val BAD_SEED = "La semilla de la identidad no es válida"
private const val BAD_PUBLIC_KEY = "La clave pública no es válida"

/**
 * La identidad de una persona: de una semilla de 32 bytes salen, con HKDF-SHA256 (sal vacía) y un `info` propio para
 * cada una, la clave privada de Ed25519 (firmar) y la de X-Wing (recibir mensajes). Con la misma semilla salen siempre
 * las mismas claves.
 *
 * Copia la semilla: quien la pasa sigue siendo dueño de su array y lo pone a cero cuando quiera. [close] pone a cero la
 * copia y suelta las claves privadas; después, [sign], [fileSigner] y [kemKeyPair] lanzan [IllegalStateException].
 * Los objetos de clave de Bouncy Castle no se pueden poner a cero (guardan su propia copia): [close] solo suelta la
 * referencia, y la copia vive hasta que el recolector de basura la libere. Las claves públicas siguen disponibles.
 */
class Identity(seed: ByteArray) : AutoCloseable {
    private val seedCopy: ByteArray
    private var edPrivate: Ed25519PrivateKeyParameters?
    private var kemPair: AsymmetricCipherKeyPair?

    /** Clave pública de Ed25519 (32 bytes). No se modifica. */
    val edPublic: ByteArray

    /** Clave pública de X-Wing (1216 bytes). No se modifica. */
    val kemPublic: ByteArray

    init {
        if (seed.size != KEY_BYTES) throw CryptoFailure(BAD_SEED)
        val ed = derived(seed, ED_INFO, Ed25519PrivateKeyParameters.KEY_SIZE) { Ed25519PrivateKeyParameters(it, 0) }
        val h = hpkeSuite()
        // Nunca `HPKE.deriveKeyPair`: en Bouncy Castle 1.86 no coincide con el borrador. La pública sale de la privada.
        val kem = derived(seed, XWING_INFO, KEM_SECRET_BYTES) { h.deserializePrivateKey(it, null) }
        edPublic = ed.generatePublicKey().encoded
        kemPublic = h.serializePublicKey(kem.public)
        check(edPublic.size == ED_PUBLIC_BYTES && kemPublic.size == KEM_PUBLIC_BYTES) { "Tamaños de clave inesperados" }
        edPrivate = ed
        kemPair = kem
        seedCopy = seed.copyOf()
    }

    /** Firma [data] con Ed25519 puro: el mensaje firmado es `labeled(label) ‖ data`. */
    fun sign(label: String, data: ByteArray): ByteArray {
        val signer = Ed25519Signer().apply { init(true, edKey()) }
        val prefix = labeled(label)
        signer.update(prefix, 0, prefix.size)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    /**
     * Un firmador de Ed25519ph (prehash SHA-512) ya iniciado, con [context] como contexto del RFC 8032. Se le pasan los
     * datos con `update` a medida que llegan y se termina con `generateSignature()`.
     */
    fun fileSigner(context: String): Ed25519phSigner =
        Ed25519phSigner(asciiLabel(context)).apply { init(true, edKey()) }

    /** El par de claves de X-Wing, para `HPKE.setupBaseR`. */
    fun kemKeyPair(): AsymmetricCipherKeyPair = kemPair ?: throw closed()

    override fun close() {
        seedCopy.fill(0)
        edPrivate = null
        kemPair = null
    }

    private fun edKey(): Ed25519PrivateKeyParameters = edPrivate ?: throw closed()

    private fun closed() = IllegalStateException("La identidad está cerrada")
}

/** Semilla nueva de una identidad: 32 bytes del generador seguro. Quien la pide la pone a cero al terminar. */
fun newIdentitySeed(random: SecureRandom = secureRandom): ByteArray = randomBytes(KEY_BYTES, random)

/**
 * Comprueba una firma de [Identity.sign]. Da `false`, nunca lanza, con una clave o una firma que no miden lo que deben
 * o que no son válidas. Ed25519 de Bouncy Castle (RFC 8032 §5.1.7) rechaza también las firmas con `S` fuera de rango.
 */
fun verify(edPublic: ByteArray, label: String, data: ByteArray, sig: ByteArray): Boolean {
    if (sig.size != SIG_BYTES) return false
    val key = decodeEdPublic(edPublic) ?: return false
    val verifier = Ed25519Signer().apply { init(false, key) }
    val prefix = labeled(label)
    verifier.update(prefix, 0, prefix.size)
    verifier.update(data, 0, data.size)
    return verifier.verifySignature(sig)
}

/** El verificador de Ed25519ph para [fileSigner], ya iniciado: `update` con los datos y `verifySignature(sig)`. */
fun fileVerifier(edPublic: ByteArray, context: String): Ed25519phSigner {
    val key = decodeEdPublic(edPublic) ?: throw CryptoFailure(BAD_PUBLIC_KEY)
    return Ed25519phSigner(asciiLabel(context)).apply { init(false, key) }
}

/** La clave pública de Ed25519, o `null` si no mide 32 bytes o no es un punto válido de la curva. */
private fun decodeEdPublic(edPublic: ByteArray): Ed25519PublicKeyParameters? {
    if (edPublic.size != ED_PUBLIC_BYTES) return null
    return try {
        Ed25519PublicKeyParameters(edPublic, 0)
    } catch (e: IllegalArgumentException) {
        null // Bouncy Castle decodifica el punto al construirla y lanza con uno que no está en la curva
    }
}

/**
 * La huella para comparar dos tarjetas por teléfono: `SHA-512(labeled("seguridad-fingerprint-v1") ‖ suite ‖ Ed25519 ‖
 * X-Wing)`, en 6 grupos de 5 cifras. El grupo `i` es el valor de los 5 bytes que empiezan en `i*5` (big-endian, sin
 * signo) módulo 100.000. No depende del nombre de la tarjeta. Los tamaños tienen que ser exactos, porque si no se podría
 * mover un byte de una clave a la otra sin cambiar la huella.
 */
fun fingerprint(edPublic: ByteArray, kemPublic: ByteArray): String {
    if (edPublic.size != ED_PUBLIC_BYTES || kemPublic.size != KEM_PUBLIC_BYTES) throw CryptoFailure(BAD_PUBLIC_KEY)
    val digest = SHA512Digest()
    val label = labeled(FINGERPRINT_LABEL)
    digest.update(label, 0, label.size)
    digest.update(SUITE_1)
    digest.update(edPublic, 0, edPublic.size)
    digest.update(kemPublic, 0, kemPublic.size)
    val hash = ByteArray(digest.digestSize)
    digest.doFinal(hash, 0)
    return (0 until FINGERPRINT_GROUPS).joinToString(" ") { group ->
        var value = 0L
        for (j in 0 until FINGERPRINT_GROUP_BYTES) {
            value = (value shl 8) or (hash[group * FINGERPRINT_GROUP_BYTES + j].toLong() and 0xFF)
        }
        (value % FINGERPRINT_MODULUS).toString().padStart(5, '0')
    }
}

/** HKDF-SHA256 (RFC 5869) de [seed] con sal vacía y [info]; [build] usa la clave y después se pone a cero. */
private inline fun <T> derived(seed: ByteArray, info: String, length: Int, build: (ByteArray) -> T): T {
    val key = ByteArray(length)
    try {
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(seed, null, asciiLabel(info)))
            generateBytes(key, 0, length)
        }
        return build(key)
    } finally {
        key.fill(0)
    }
}
