package app.seguridad.crypto.pk

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.extractPrefixedToken
import app.seguridad.crypto.extractToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Mensaje cifrado de texto para un contacto (`SGM1:`): ida y vuelta, manipulación, reenvío subrepticio, marcas, límites,
 * pegado desde un chat y el vector guardado.
 *
 * El formato se lee para siempre. Los tests de rechazo construyen mensajes **bien sellados** pero mal formados con
 * `sellado`, que reimplementa el diseño con `hpkeSuite()` a mano, sin pasar por `encryptMessage`.
 */
class MessageTest {
    private fun fixedIdentity() = Identity(ByteArray(32) { it.toByte() })

    /** «Ana» de los vectores: otra semilla fija. */
    private fun anaIdentity() = Identity(ByteArray(32) { (0xA0 + it).toByte() })

    private fun cardOf(id: Identity, name: String = "Contacto") = parseCard(id.cardBytes(name))

    private fun rejects(message: String = "", block: () -> Unit): CryptoFailure =
        assertThrows(message, CryptoFailure::class.java) { block() }

    private val info = "seguridad-msg-v1".toByteArray(Charsets.US_ASCII)
    private val ikm = ByteArray(64) { (0x40 + it).toByte() }

    private fun decode(token: String) = base64UrlDecode(token.removePrefix("SGM1:"))
    private fun encode(bytes: ByteArray) = "SGM1:" + base64UrlEncode(bytes)

    /** El `texto_interior` del diseño: marcas, [clave Ed25519 ‖ firma] si está firmado, mensaje. */
    private fun inner(marks: Int, signed: ByteArray?, message: ByteArray) =
        byteArrayOf(marks.toByte()) + (signed ?: ByteArray(0)) + message

    /** `sign("seguridad-msg-sig-v1", cabecera ‖ X-Wing del destinatario ‖ mensaje)` escrito a mano. */
    private fun signature(signer: Identity, header: ByteArray, recipient: Card, message: ByteArray) =
        signer.sign("seguridad-msg-sig-v1", header + recipient.kemPublic + message)

    /** Sella [inner] para [recipient] con HPKE a mano: `cabecera = "SG" 02 02 01 ‖ enc`, `aad = cabecera`. */
    private fun sealed(recipient: Card, build: (header: ByteArray) -> ByteArray): String {
        val h = hpkeSuite()
        val ctx = h.setupBaseS(h.deserializePublicKey(recipient.kemPublic), info, ikm)
        val header = byteArrayOf(0x53, 0x47, 0x02, 0x02, 0x01) + ctx.encapsulation
        return encode(header + ctx.seal(header, build(header)))
    }

    /** El `texto_interior` en claro de [token], abierto a mano (sin pasar por `decryptMessage`). */
    private fun rawInner(token: String, me: Identity): ByteArray {
        val raw = decode(token)
        val header = raw.copyOf(5 + 1120)
        val enc = raw.copyOfRange(5, 5 + 1120)
        return hpkeSuite().setupBaseR(enc, me.kemKeyPair(), info).open(header, raw.copyOfRange(header.size, raw.size))
    }

    // ---- Ida y vuelta ----

    @Test fun idaYVueltaFirmada() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val token = encryptMessage("Hola, ¿qué tal? 🔐", cardOf(b), a)
        assertTrue(token.startsWith(MESSAGE_PREFIX))
        val opened = decryptMessage(token, b)
        assertEquals("Hola, ¿qué tal? 🔐", opened.text)
        assertArrayEquals(a.edPublic, opened.senderEd)
    }

    @Test fun idaYVueltaSinFirmar() {
        val b = anaIdentity()
        val opened = decryptMessage(encryptMessage("Hola", cardOf(b), null), b)
        assertEquals("Hola", opened.text)
        assertNull(opened.senderEd)
    }

    @Test fun mensajeVacio() {
        val a = fixedIdentity()
        val b = anaIdentity()
        assertEquals("", decryptMessage(encryptMessage("", cardOf(b), null), b).text)
        val signed = decryptMessage(encryptMessage("", cardOf(b), a), b)
        assertEquals("", signed.text)
        assertArrayEquals(a.edPublic, signed.senderEd)
    }

    @Test fun elFormatoEsElDelDiseno() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val msg = "Hola".toByteArray()
        val unsigned = decode(encryptMessage("Hola", cardOf(b), null))
        assertArrayEquals(byteArrayOf(0x53, 0x47, 0x02, 0x02, 0x01), unsigned.copyOf(5))
        // cabecera (5 + enc 1120), marcas (1), mensaje y etiqueta de ChaCha20-Poly1305 (16)
        assertEquals(5 + 1120 + 1 + msg.size + 16, unsigned.size)
        val signed = decode(encryptMessage("Hola", cardOf(b), a))
        assertEquals(5 + 1120 + 1 + 32 + 64 + msg.size + 16, signed.size)
        // El contenido, abierto a mano con el diseño en la mano: marcas ‖ [clave ‖ firma] ‖ mensaje.
        val inner = rawInner(encode(signed), b)
        assertEquals(1 + 96 + msg.size, inner.size)
        assertEquals(1, inner[0].toInt())
        assertArrayEquals(a.edPublic, inner.copyOfRange(1, 33))
        assertArrayEquals(msg, inner.copyOfRange(97, inner.size))
        val header = signed.copyOf(1125)
        assertTrue(verify(a.edPublic, "seguridad-msg-sig-v1", header + b.kemPublic + msg, inner.copyOfRange(33, 97)))
        assertEquals(0, rawInner(encode(unsigned), b)[0].toInt())
    }

    @Test fun cadaMensajeLlevaUnaEncapsulacionNueva() {
        val b = cardOf(anaIdentity())
        assertNotEquals(encryptMessage("Hola", b, null), encryptMessage("Hola", b, null))
    }

    @Test fun usaElGeneradorQueSePasa() {
        val b = cardOf(anaIdentity())
        fun fixedRandom() = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) = bytes.indices.forEach { bytes[it] = (0x40 + it).toByte() }
        }
        assertEquals(encryptMessage("Hola", b, null, fixedRandom()), encryptMessage("Hola", b, null, fixedRandom()))
        assertEquals(encryptMessage("Hola", b, null, ikm), encryptMessage("Hola", b, null, fixedRandom()))
    }

    @Test fun ikmEDeterministaDaElMismoMensaje() {
        val a = fixedIdentity()
        val b = cardOf(anaIdentity())
        assertEquals(encryptMessage("Hola", b, a, ikm), encryptMessage("Hola", b, a, ikm))
        assertNotEquals(encryptMessage("Hola", b, a, ikm), encryptMessage("Hola", b, a, ByteArray(64) { 7 }))
    }

    // ---- Manipulación ----

    @Test fun cambiarCadaByteSeRechaza() {
        val b = anaIdentity()
        for (signer in listOf(fixedIdentity(), null)) {
            val raw = decode(encryptMessage("Hola", cardOf(b), signer, ikm))
            for (i in raw.indices) {
                val tampered = raw.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
                val failure = rejects("byte $i") { decryptMessage(encode(tampered), b) }
                // Fuera de la cabecera de 5 bytes, ningún cambio se distingue de «no es para ti»: ni siquiera se dice qué falló.
                if (i >= 5) assertEquals("byte $i", NOT_FOR_ME, failure.message)
            }
        }
    }

    @Test fun cambiarCadaByteDeLaCabeceraDaUnRechazoDeCabecera() {
        val b = anaIdentity()
        val raw = decode(encryptMessage("Hola", cardOf(b), null, ikm))
        for (i in 0 until 5) {
            val tampered = raw.copyOf().also { it[i] = (it[i].toInt() xor 0x80).toByte() }
            val failure = rejects("byte $i") { decryptMessage(encode(tampered), b) }
            assertNotEquals(NOT_FOR_ME, failure.message)
        }
    }

    @Test fun truncadoYBytesDeMasSeRechazan() {
        val b = anaIdentity()
        val raw = decode(encryptMessage("Hola", cardOf(b), fixedIdentity(), ikm))
        for (n in listOf(0, 1, 4, 5, 6, 1124, 1125, 1126, 1141, raw.size - 1)) {
            rejects("truncado a $n") { decryptMessage(encode(raw.copyOf(n)), b) }
        }
        rejects("un byte de más") { decryptMessage(encode(raw + 0), b) }
        rejects("sin cuerpo") { decryptMessage("SGM1:", b) }
    }

    @Test fun otraIdentidadNoLoAbre() {
        val token = encryptMessage("Secreto", cardOf(fixedIdentity()), anaIdentity())
        val failure = rejects { decryptMessage(token, anaIdentity()) }
        assertEquals(NOT_FOR_ME, failure.message)
        assertEquals(NOT_FOR_ME, rejects { decryptMessage(token, Identity(ByteArray(32) { 99 })) }.message)
    }

    @Test fun otroTipoDeDatoSeRechaza() {
        val id = fixedIdentity()
        rejects { decryptMessage(cardText(id.cardBytes("Ana")).replace("SGK1:", "SGM1:"), id) }
        rejects { decryptMessage("SGK1:" + base64UrlEncode(id.cardBytes("Ana")), id) }
        rejects { decryptMessage("SG1:abc", id) }
        rejects { decryptMessage("hola", id) }
        rejects { decryptMessage("", id) }
    }

    // ---- Reenvío subrepticio ----

    /**
     * A firma para B. B abre el mensaje y lo vuelve a cifrar para C con el mismo `texto_interior` (con la firma de A
     * dentro). C lo abre bien con HPKE, pero la firma de A cubre la cabecera y la clave X-Wing de B: no cuadra, y se
     * rechaza entero, sin enseñar el texto.
     */
    @Test fun reenvioSubrepticioSeRechaza() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val c = Identity(ByteArray(32) { (0x70 + it).toByte() })
        val original = encryptMessage("Dile a C que sí", cardOf(b), a)
        assertEquals("Dile a C que sí", decryptMessage(original, b).text)
        val stolen = rawInner(original, b)
        val forwarded = sealed(cardOf(c)) { stolen }
        // El sellado HPKE es correcto: abierto a mano, C ve lo mismo que B.
        assertArrayEquals(stolen, rawInner(forwarded, c))
        val failure = rejects { decryptMessage(forwarded, c) }
        assertEquals(BAD_MESSAGE_SIGNATURE, failure.message)
    }

    @Test fun reenvioAlMismoDestinatarioConOtraCabeceraTambienSeRechaza() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val stolen = rawInner(encryptMessage("Hola", cardOf(b), a), b)
        // Otra `enc` (otra ikm) para la misma destinataria: la cabecera cambia y la firma de A ya no vale.
        val h = hpkeSuite()
        val ctx = h.setupBaseS(h.deserializePublicKey(cardOf(b).kemPublic), info, ByteArray(64) { 5 })
        val header = byteArrayOf(0x53, 0x47, 0x02, 0x02, 0x01) + ctx.encapsulation
        val token = encode(header + ctx.seal(header, stolen))
        assertEquals(BAD_MESSAGE_SIGNATURE, rejects { decryptMessage(token, b) }.message)
    }

    // ---- Marcas y firma ----

    @Test fun unaMarcaConUnBitDesconocidoSeRechaza() {
        val b = anaIdentity()
        val msg = "Hola".toByteArray()
        for (marks in listOf(0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x03, 0xFE, 0xFF)) {
            val token = sealed(cardOf(b)) { inner(marks, null, msg) }
            rejects("marcas $marks") { decryptMessage(token, b) }
        }
        // Y con una firma válida dentro: el bit desconocido pesa más.
        val a = fixedIdentity()
        val token = sealed(cardOf(b)) { header ->
            inner(0x03, a.edPublic + signature(a, header, cardOf(b), msg), msg)
        }
        rejects("firmado con un bit desconocido") { decryptMessage(token, b) }
    }

    @Test fun elInteriorSinMarcasSeRechaza() {
        val b = anaIdentity()
        rejects("interior vacío") { decryptMessage(sealed(cardOf(b)) { ByteArray(0) }, b) }
    }

    @Test fun firmadoSinFirmaCompletaSeRechaza() {
        val b = anaIdentity()
        for (extra in listOf(0, 1, 32, 95, 96 - 1)) {
            val token = sealed(cardOf(b)) { inner(1, ByteArray(extra), ByteArray(0)) }
            rejects("firmado con $extra bytes") { decryptMessage(token, b) }
        }
    }

    @Test fun marcaFirmadaConFirmaMalaSeRechazaEnteroYSinTexto() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val msg = "No me fío".toByteArray()
        val goodToken = sealed(cardOf(b)) { header -> inner(1, a.edPublic + signature(a, header, cardOf(b), msg), msg) }
        assertEquals("No me fío", decryptMessage(goodToken, b).text)

        // La firma con un bit cambiado.
        val bad = sealed(cardOf(b)) { header ->
            val sig = signature(a, header, cardOf(b), msg).also { it[10] = (it[10].toInt() xor 1).toByte() }
            inner(1, a.edPublic + sig, msg)
        }
        assertEquals(BAD_MESSAGE_SIGNATURE, rejects { decryptMessage(bad, b) }.message)

        // Firma de otro texto.
        val otherText = sealed(cardOf(b)) { header ->
            inner(1, a.edPublic + signature(a, header, cardOf(b), "otro".toByteArray()), msg)
        }
        assertEquals(BAD_MESSAGE_SIGNATURE, rejects { decryptMessage(otherText, b) }.message)

        // Firma de otro formato: misma clave, otra etiqueta.
        val otherLabel = sealed(cardOf(b)) { header ->
            inner(1, a.edPublic + a.sign("seguridad-card-v1", header + b.kemPublic + msg), msg)
        }
        assertEquals(BAD_MESSAGE_SIGNATURE, rejects { decryptMessage(otherLabel, b) }.message)

        // Firma de otra persona con la clave de A.
        val other = Identity(ByteArray(32) { 3 })
        val impostor = sealed(cardOf(b)) { header -> inner(1, a.edPublic + signature(other, header, cardOf(b), msg), msg) }
        assertEquals(BAD_MESSAGE_SIGNATURE, rejects { decryptMessage(impostor, b) }.message)

        // Una clave Ed25519 que no es un punto de la curva.
        val badKey = sealed(cardOf(b)) { header -> inner(1, ByteArray(32) { 0x02 } + signature(a, header, cardOf(b), msg), msg) }
        assertEquals(BAD_MESSAGE_SIGNATURE, rejects { decryptMessage(badKey, b) }.message)
    }

    @Test fun elTextoInteriorNoUtf8SeRechaza() {
        val b = anaIdentity()
        for (bytes in listOf(byteArrayOf(0xC3.toByte()), byteArrayOf(0xC0.toByte(), 0x80.toByte()), byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()))) {
            rejects("UTF-8 mal formado") { decryptMessage(sealed(cardOf(b)) { inner(0, null, bytes) }, b) }
        }
    }

    // ---- Límites ----

    @Test fun elLimiteSonSesentaYCuatroKiB() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val limit = "a".repeat(MAX_MESSAGE_BYTES)
        val opened = decryptMessage(encryptMessage(limit, cardOf(b), a), b)
        assertEquals(limit, opened.text)
        rejects("64 KiB + 1") { encryptMessage(limit + "a", cardOf(b), a) }
        rejects("64 KiB + 1 sin firmar") { encryptMessage(limit + "a", cardOf(b), null) }
        // Cuenta bytes en UTF-8, no caracteres: 21.846 × «€» (3 bytes) son 65.538.
        rejects("multibyte") { encryptMessage("€".repeat(21_846), cardOf(b), null) }
        assertEquals("€".repeat(21_845), decryptMessage(encryptMessage("€".repeat(21_845), cardOf(b), null), b).text)
    }

    @Test fun unMensajeAbiertoMasGrandeQueElLimiteSeRechaza() {
        val b = anaIdentity()
        val big = ByteArray(MAX_MESSAGE_BYTES + 1) { 'a'.code.toByte() }
        rejects("más de 64 KiB dentro") { decryptMessage(sealed(cardOf(b)) { inner(0, null, big) }, b) }
    }

    @Test fun unTextoEnormeSeRechazaSinProcesarlo() {
        val b = anaIdentity()
        rejects("más de 1 MiB") { decryptMessage("SGM1:" + "A".repeat(1 shl 20), b) }
        rejects("más de 1 MiB con relleno") { decryptMessage("hola ".repeat(1 shl 18) + "SGM1:AAAA", b) }
        // Dentro del límite del pegado pero sin ser un mensaje: se descarta por tamaño, sin intentar abrirlo.
        assertEquals(NOT_FOR_ME, rejects("1 MiB de Base64url") { decryptMessage("SGM1:" + "A".repeat((1 shl 20) - 10), b) }.message)
        rejects("miles de trozos") { decryptMessage("SGM1:" + "A ".repeat(400_000), b) }
    }

    @Test fun unMensajeEntreMuchoTextoSeAbre() {
        val b = anaIdentity()
        val token = encryptMessage("Entre mucho texto", cardOf(b), fixedIdentity())
        val filler = "Esto es una conversación larga. ".repeat(5_000) // ~160.000 caracteres
        val pasted = filler + "\n" + token.chunked(70).joinToString("\n") + "\n" + filler
        assertTrue(pasted.length in 300_000..MAX_TEXT_FILE_BYTES)
        assertEquals("Entre mucho texto", decryptMessage(pasted, b).text)
    }

    @Test fun unTextoQueNoSePuedeCodificarSeRechaza() {
        val b = cardOf(anaIdentity())
        rejects("sustituto suelto") { encryptMessage("hola \uD800", b, null) }
    }

    // ---- Pegado del chat ----

    @Test fun elPegadoDelChatSeAbre() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val token = encryptMessage("Quedamos a las diez", cardOf(b), a)
        fun opens(label: String, pasted: String) {
            assertEquals(label, "Quedamos a las diez", decryptMessage(pasted, b).text)
        }
        opens("tal cual", token)
        opens("con saltos finales", "$token\n\n")
        opens("con espacios alrededor", "   $token   ")
        opens("texto antes y después", "Te paso esto: $token Gracias")
        opens("texto antes y después en líneas", "Te paso esto:\n$token\nUn abrazo, Ana")
        opens("partido en líneas de 60", token.chunked(60).joinToString("\n"))
        opens("partido en líneas de 76 con CRLF", token.chunked(76).joinToString("\r\n"))
        opens("líneas con sangría y espacios al final", token.chunked(50).joinToString("  \n   "))
        opens("partido y con notas", "Hola:\n" + token.chunked(64).joinToString("\n") + "\nGuardado el lunes")
        opens("entre comillas", "«$token »")
        opens("trozos de 60 separados por espacios", token.chunked(60).joinToString(" "))
        opens("trozos de 60 separados por saltos", token.chunked(60).joinToString("\n"))
        opens("espacios y saltos mezclados", token.chunked(37).mapIndexed { i, c -> if (i % 3 == 0) "$c \n" else "$c " }.joinToString(""))
        opens("una palabra de Base64url en la línea siguiente", "$token\nGracias")
        opens("una palabra de Base64url tras un espacio", "$token Gracias")
        opens("varias palabras de Base64url detrás", "$token Gracias de nuevo")
        opens("partido con espacios y una palabra detrás", token.chunked(60).joinToString(" ") + "\nGracias")
        opens("palabra de Base64url y punto", "$token Gracias.")
        opens("antes y después con el mensaje partido", "Te paso esto: " + token.chunked(60).joinToString(" ") + " Gracias")
    }

    @Test fun elPegadoDelChatSinElPrefijoSeRechaza() {
        val b = anaIdentity()
        val token = encryptMessage("Hola", cardOf(b), null)
        rejects("sin prefijo") { decryptMessage(token.removePrefix("SGM1:"), b) }
        rejects("prefijo en minúsculas") { decryptMessage(token.replace("SGM1:", "sgm1:"), b) }
    }

    @Test fun extractPrefixedTokenGeneralizaExtractToken() {
        assertEquals("SGM1:abc_-", extractPrefixedToken("nota\nSGM1:abc_-\nGuardado el lunes", "SGM1:"))
        assertEquals("SGM1:abcd", extractPrefixedToken("SGM1:ab\ncd\n", "SGM1:"))
        assertEquals("SGM1:abc", extractPrefixedToken("Mira: SGM1:abc. Gracias", "SGM1:"))
        assertEquals("SGS1:xyz", extractPrefixedToken("texto\nSGS1:xyz", "SGS1:"))
        assertNull(extractPrefixedToken("SGM1:", "SGM1:"))
        assertNull(extractPrefixedToken("nada", "SGM1:"))
        assertNull(extractPrefixedToken("SG1:abc", "SGM1:"))
        // extractToken sigue igual para SG1: y CP1:.
        assertEquals("CP1:abc_-", extractToken("nota\nCP1:abc_-\nGuardado el lunes"))
        assertEquals("SG1:abcd", extractToken("SG1:ab\ncd\n"))
    }

    // ---- BC rechaza la clave ----

    /**
     * `Card` es una clase de datos con constructor público: una tarjeta que no salió de `parseCard` puede llevar una clave
     * que Bouncy Castle rechaza al cifrar (con `IllegalStateException`, `IllegalArgumentException`…). Eso no puede
     * escapar como una excepción cualquiera: es un [CryptoFailure] con mensaje en español.
     */
    @Test fun unaClaveQueBouncyCastleRechazaNoHaceFallarConOtraExcepcion() {
        val good = cardOf(anaIdentity())
        val a = fixedIdentity()
        val smallOrder = good.kemPublic.copyOf().also { it.fill(0, 1184, 1216) } // X25519 de orden pequeño (u = 0)
        val badModulus = good.kemPublic.copyOf().also { it.fill(0xFF.toByte(), 0, 1184) } // ML-KEM fuera de rango (FIPS 203 §7.2)
        val keys = mapOf(
            "X25519 de orden pequeño" to smallOrder,
            "ML-KEM fuera de rango" to badModulus,
            "demasiado corta" to good.kemPublic.copyOf(1215),
            "demasiado larga" to good.kemPublic + 0,
            "vacía" to ByteArray(0),
        )
        for ((name, kem) in keys) {
            val card = Card(good.edPublic, kem, "x")
            val failure = rejects(name) { encryptMessage("hola", card, null) }
            assertTrue(name, failure.message!!.isNotBlank() && failure.message != NOT_FOR_ME)
            rejects("$name firmado") { encryptMessage("hola", card, a) }
            rejects("$name con ikmE") { encryptMessage("hola", card, null, ikm) }
        }
    }

    @Test fun unIkmEQueNoMideSesentaYCuatroBytesSeRechaza() {
        val card = cardOf(anaIdentity())
        for (n in listOf(0, 32, 63, 65)) {
            rejects("ikmE de $n bytes") { encryptMessage("hola", card, null, ByteArray(n)) }
        }
    }

    // ---- Vector guardado ----

    private fun vector(key: String): String {
        val json = requireNotNull(javaClass.getResource("/vectors/seguridad-pk.json")) { "seguridad-pk.json" }.readText()
        return Json.parseToJsonElement(json).jsonObject.getValue(key).jsonPrimitive.content
    }

    private val vectorText = "Hola Ana, ¿quedamos mañana a las 10? 🔐"

    /**
     * Un mensaje de la identidad fija (semilla 00 01 … 1F) a «Ana» (semilla A0 A1 … BF) con `ikmE` = 40 41 … 7F, guardado
     * en `seguridad-pk.json` como `message_signed` y `message_unsigned`. Se generó una vez. Lo permanente es que los
     * mensajes guardados se abran (las primeras comprobaciones): si fallan, el formato ha cambiado y no se puede
     * actualizar el vector, hay que arreglar el código.
     */
    @Test fun mensajesGuardados() {
        val sender = fixedIdentity()
        val ana = anaIdentity()
        val signed = vector("message_signed")
        val unsigned = vector("message_unsigned")
        assertTrue(signed.startsWith("SGM1:") && unsigned.startsWith("SGM1:"))

        val openedSigned = decryptMessage(signed, ana)
        assertEquals(vectorText, openedSigned.text)
        assertArrayEquals(sender.edPublic, openedSigned.senderEd)
        val openedUnsigned = decryptMessage(unsigned, ana)
        assertEquals(vectorText, openedUnsigned.text)
        assertNull(openedUnsigned.senderEd)

        // Si esto falla, cambió cómo Bouncy Castle usa `ikmE`; los mensajes guardados siguen abriéndose si el resto del test
        // pasa. (Ed25519 es determinista y HPKE con `ikmE` fijo también, con esta versión de Bouncy Castle.)
        assertEquals(signed, encryptMessage(vectorText, cardOf(ana, "Ana"), sender, ikm))
        assertEquals(unsigned, encryptMessage(vectorText, cardOf(ana, "Ana"), null, ikm))

        // Y nadie más lo abre.
        assertEquals(NOT_FOR_ME, rejects { decryptMessage(signed, sender) }.message)
    }
}
