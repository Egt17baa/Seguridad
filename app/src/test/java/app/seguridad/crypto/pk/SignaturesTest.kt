package app.seguridad.crypto.pk

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.Ed25519phSigner
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Firmas de texto (`SGS1:`) y de archivo (`.sgsig`): ida y vuelta, normalización, pegado desde un chat, manipulación de
 * cada campo, separación de dominios y los vectores guardados.
 *
 * Los formatos se leen para siempre. Los tests de rechazo construyen firmas **bien hechas** pero mal formadas a mano,
 * con BC directamente (`handText`, `handFile`), sin pasar por `signText` ni `signFile`: así el diseño y el código se
 * comprueban el uno contra el otro.
 */
class SignaturesTest {
    private fun fixedIdentity() = Identity(ByteArray(32) { it.toByte() })

    private fun otherIdentity() = Identity(ByteArray(32) { (0xA0 + it).toByte() })

    private fun rejects(message: String = "", block: () -> Unit): CryptoFailure =
        assertThrows(message, CryptoFailure::class.java) { block() }

    private val textLabel = "seguridad-text-sig-v1"
    private val fileContext = "seguridad-file-sig-v1"
    private val msgContext = "seguridad-file-msg-sig-v1"

    private fun data(n: Int, seed: Int = 7) = ByteArray(n) { (it * seed + 3).toByte() }

    // ---- A mano, según el diseño ----

    /**
     * `"SGS1:" + base64url(cabecera ‖ clave ‖ firma)` de [text] tal cual (sin normalizar) con [label]. La firma sale de
     * `Identity.sign` y se comprueba aparte con Ed25519 de BC sobre `"etiqueta" 00 ‖ texto`.
     */
    private fun handText(id: Identity, text: String, label: String = textLabel, kind: PkKind = PkKind.TEXT_SIGNATURE): String {
        val message = label.toByteArray(Charsets.US_ASCII) + 0 + text.toByteArray(Charsets.UTF_8)
        val sig = id.sign(label, text.toByteArray(Charsets.UTF_8))
        // La firma de `Identity.sign` es la de Ed25519 puro sobre `etiqueta 00 ‖ texto`: se comprueba con BC directamente.
        val v = Ed25519Signer().apply { init(false, Ed25519PublicKeyParameters(id.edPublic, 0)) }
        v.update(message, 0, message.size)
        assertTrue(v.verifySignature(sig))
        return SIGNATURE_PREFIX + base64UrlEncode(pkHeader(kind) + id.edPublic + sig)
    }

    private fun tokenOf(block: String) = block.substring(block.lastIndexOf(SIGNATURE_PREFIX))
    private fun rawOf(block: String) = base64UrlDecode(tokenOf(block).removePrefix(SIGNATURE_PREFIX))

    /** Un `.sgsig` hecho a mano con Ed25519ph de BC: el contenido entero de una vez, con [context]. */
    private fun handFile(id: Identity, content: ByteArray, context: String = fileContext, kind: PkKind = PkKind.FILE_SIGNATURE): ByteArray {
        val signer = id.fileSigner(context)
        signer.update(content, 0, content.size)
        return pkHeader(kind) + id.edPublic + signer.generateSignature()
    }

    private fun flip(b: ByteArray, at: Int) = b.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }

    // ---- normalizeSignedText ----

    @Test fun normalizaNfcSaltosYEspacios() {
        assertEquals("Hola\nmundo", normalizeSignedText("  \r\n\tHola\r\nmundo\r\n \n"))
        assertEquals("a\nb\nc", normalizeSignedText("a\rb\r\nc"))
        assertEquals("é", normalizeSignedText("é"))
        assertEquals("", normalizeSignedText(" \r\n "))
        // Idempotente: lo que firma `signText` es lo que `verifySignedText` vuelve a calcular.
        for (s in listOf("é\r\n  x ", "  ạ́ ", "línea\r\r\rotra", "🔐 \n", "a​ ")) {
            val n = normalizeSignedText(s)
            assertEquals(n, normalizeSignedText(n))
        }
    }

    // ---- Firma de texto ----

    @Test fun textoIdaYVuelta() {
        val me = fixedIdentity()
        val block = signText("Hola Ana, ¿quedamos mañana? 🔐", me)
        val opened = verifySignedText(block)
        assertEquals("Hola Ana, ¿quedamos mañana? 🔐", opened.text)
        assertArrayEquals(me.edPublic, opened.signerEd)
    }

    @Test fun elBloqueTieneLaForma() {
        val me = fixedIdentity()
        val block = signText("  uno\r\ndos \n", me)
        val lines = block.split("\n")
        assertEquals(3, lines.size)
        assertEquals("uno", lines[0])
        assertEquals("dos", lines[1])
        assertTrue(lines[2].startsWith("SGS1:"))
        assertEquals("SGS1:", SIGNATURE_PREFIX)
        assertEquals(".sgsig", SIGNATURE_EXTENSION)
        // 101 bytes: cabecera (5) ‖ clave (32) ‖ firma (64), Base64url sin relleno (135 caracteres).
        val raw = base64UrlDecode(lines[2].removePrefix("SGS1:"))
        assertEquals(101, raw.size)
        assertArrayEquals(pkHeader(PkKind.TEXT_SIGNATURE), raw.copyOf(5))
        assertArrayEquals(me.edPublic, raw.copyOfRange(5, 37))
        assertEquals(135, lines[2].length - 5)
        assertEquals(block, signText("uno\ndos", me)) // Ed25519 es determinista
    }

    @Test fun coincideConElDisenoEscritoAMano() {
        val me = fixedIdentity()
        val text = "Hola Ana, ¿quedamos mañana? 🔐"
        assertEquals(text + "\n" + handText(me, text), signText(text, me))
    }

    @Test fun seAceptaConSaltosCrlfEspaciosYEAcentoDescompuesta() {
        val me = fixedIdentity()
        val block = signText("Mañana\nsin falta\ncafé", me)
        assertEquals("Mañana\nsin falta\ncafé", verifySignedText(block.replace("\n", "\r\n")).text)
        assertEquals("Mañana\nsin falta\ncafé", verifySignedText(block.replace("\n", "\r")).text)
        assertEquals("Mañana\nsin falta\ncafé", verifySignedText("  \r\n\n$block \r\n\n  ").text)
        val decomposed = block.replace("é", "é").replace("ñ", "ñ")
        assertNotEquals(block, decomposed)
        val opened = verifySignedText(decomposed)
        assertEquals("Mañana\nsin falta\ncafé", opened.text)
        assertArrayEquals(me.edPublic, opened.signerEd)
        // También si se firma ya descompuesto: el texto que sale es el normalizado.
        assertEquals("café", verifySignedText(signText("café", me)).text)
    }

    @Test fun seRechazaSiCambiaUnaLetra() {
        val block = signText("Pago de 100 euros a Ana", fixedIdentity())
        rejects { verifySignedText(block.replace("100", "900")) }
        rejects { verifySignedText(block.replace("Ana", "Ena")) }
        rejects { verifySignedText(block.replace("Pago", "pago")) }
        rejects { verifySignedText("Pago de 100 euros a Ana.\n" + tokenOf(block)) }
        rejects { verifySignedText("Pago de 100  euros a Ana\n" + tokenOf(block)) } // un espacio de más dentro no se quita
        rejects { verifySignedText("Pago de 100 euros a Ana\n\n" + tokenOf(block).replace("SGS1:", "SGS1:A")) }
    }

    @Test fun cadaByteDeLaFirmaCuenta() {
        val me = fixedIdentity()
        val block = signText("texto", me)
        val raw = rawOf(block)
        val text = "texto\n"
        for (i in raw.indices) {
            rejects("byte $i") { verifySignedText(text + SIGNATURE_PREFIX + base64UrlEncode(flip(raw, i))) }
        }
        // Un byte de más o de menos.
        rejects { verifySignedText(text + SIGNATURE_PREFIX + base64UrlEncode(raw + 0)) }
        rejects { verifySignedText(text + SIGNATURE_PREFIX + base64UrlEncode(raw.copyOf(100))) }
        rejects { verifySignedText(text + SIGNATURE_PREFIX) }
        rejects { verifySignedText(text + SIGNATURE_PREFIX + base64UrlEncode(ByteArray(0))) }
    }

    @Test fun otroFirmanteNoVale() {
        val me = fixedIdentity()
        val other = otherIdentity()
        val raw = rawOf(signText("texto", me))
        // La firma de `me` con la clave de `other` en la cabecera.
        val swapped = raw.copyOf().also { other.edPublic.copyInto(it, 5) }
        rejects { verifySignedText("texto\n" + SIGNATURE_PREFIX + base64UrlEncode(swapped)) }
        // Y con una clave que no es un punto de la curva.
        val junk = raw.copyOf().also { ByteArray(32) { 0xFF.toByte() }.copyInto(it, 5) }
        rejects { verifySignedText("texto\n" + SIGNATURE_PREFIX + base64UrlEncode(junk)) }
    }

    @Test fun lineaPartidaPorElChatSeAcepta() {
        val me = fixedIdentity()
        val block = signText("Hola\nmundo", me)
        val token = tokenOf(block)
        val text = block.substring(0, block.lastIndexOf(SIGNATURE_PREFIX))
        for (cut in listOf(10, 40, 70, 100, 134)) {
            for (sep in listOf("\n", "\r\n", " ", "\n  ", "  \n")) {
                val split = token.substring(0, cut) + sep + token.substring(cut)
                assertEquals("cut=$cut", "Hola\nmundo", verifySignedText(text + split).text)
            }
        }
        // En tres trozos.
        val three = token.substring(0, 30) + "\n" + token.substring(30, 80) + "\n" + token.substring(80)
        assertEquals("Hola\nmundo", verifySignedText(text + three).text)
        // Cortada justo detrás del prefijo.
        assertEquals("Hola\nmundo", verifySignedText(text + "SGS1:\n" + token.removePrefix("SGS1:")).text)
        // Con una nota o una palabra de Base64url detrás (no se pega a la firma).
        assertEquals("Hola\nmundo", verifySignedText("$block\nGracias").text)
        assertEquals("Hola\nmundo", verifySignedText("$block\nGracias\nde nada").text)
        assertEquals("Hola\nmundo", verifySignedText("$block Gracias").text)
        assertEquals("Hola\nmundo", verifySignedText("$block.").text)
        assertEquals("Hola\nmundo", verifySignedText(text + token.substring(0, 50) + "\n" + token.substring(50) + "\nGracias").text)
    }

    @Test fun conDosLineasSgs1VaLaUltima() {
        val me = fixedIdentity()
        val other = otherIdentity()
        val a = signText("A", me)
        val b = signText("B", me)
        // La última firma es la de «B»: lo anterior (con la firma de «A» incluida) es el texto firmado, y no cuadra.
        rejects { verifySignedText(a + "\n" + tokenOf(b)) }
        // Una primera línea falsa y la última buena sobre todo lo anterior: vale, y el texto lleva la línea falsa.
        val fake = "SGS1:" + "A".repeat(135)
        val signedOverFake = signText("$fake\nhola", me)
        val opened = verifySignedText(signedOverFake)
        assertEquals("$fake\nhola", opened.text)
        // Un bloque firmado cuyo texto es otro bloque firmado.
        val nested = signText(a + "\notra cosa", other)
        val outer = verifySignedText(nested)
        assertEquals(a + "\notra cosa", outer.text)
        assertArrayEquals(other.edPublic, outer.signerEd)
        // Una segunda línea SGS1: detrás de la buena (alguien la añade) la deja sin efecto: se rechaza, no se acepta la primera.
        rejects { verifySignedText(a + "\n" + "SGS1:" + "B".repeat(135)) }
        // Con SGS1: a mitad de línea en el texto firmado.
        val mid = signText("mira SGS1:abc esto", me)
        assertEquals("mira SGS1:abc esto", verifySignedText(mid).text)
    }

    @Test fun cabeceraDeOtroTipoOMalSeRechaza() {
        val me = fixedIdentity()
        val text = "texto"
        for (kind in PkKind.entries.filter { it != PkKind.TEXT_SIGNATURE }) {
            val e = rejects("$kind") { verifySignedText(text + "\n" + handText(me, text, kind = kind)) }
            assertEquals("Es de otro tipo", e.message)
        }
        val raw = rawOf(signText(text, me))
        for (i in 0 until 5) rejects("cabecera $i") { verifySignedText("$text\n" + SIGNATURE_PREFIX + base64UrlEncode(flip(raw, i))) }
    }

    @Test fun sinFirmaOMalBase64SeRechaza() {
        rejects { verifySignedText("") }
        rejects { verifySignedText("solo un texto") }
        rejects { verifySignedText("SGS1") }
        rejects { verifySignedText("texto\nSGS1:") }
        rejects { verifySignedText("texto\nSGS1:" + "*".repeat(135)) }
        rejects { verifySignedText("texto\nSGS1:" + "A".repeat(134)) }
        rejects { verifySignedText("texto\nSGS1:" + "A".repeat(136)) }
        // Base64url no canónico: el último carácter con bits sobrantes (el de 135 caracteres termina en 2 bits a cero).
        val token = tokenOf(signText("texto", fixedIdentity()))
        val last = token.last()
        val other = if (last == 'A') 'B' else 'A'
        rejects { verifySignedText("texto\n" + token.dropLast(1) + other) }
        // Alfabeto estándar (`+` y `/`): el trozo acaba en el primero y la firma queda corta.
        rejects { verifySignedText("texto\n" + token.dropLast(5) + "+/+/+") }
        rejects { verifySignedText("texto\n" + token.dropLast(5) + "AAAA/") }
    }

    @Test fun separacionDeDominiosEnTexto() {
        val me = fixedIdentity()
        val text = "texto"
        // Con las demás etiquetas de Ed25519 puro del diseño, y con el contexto de los archivos, no vale.
        for (label in listOf("seguridad-msg-sig-v1", "seguridad-card-v1", "seguridad-fingerprint-v1", "seguridad-text-sig-v2", "seguridad-text-sig")) {
            rejects(label) { verifySignedText(text + "\n" + handText(me, text, label)) }
        }
        // La de «texto firmado» tampoco vale como firma de un archivo con ese contenido.
        val content = text.toByteArray()
        val rawText = rawOf(signText(text, me))
        rejects { verifyFileSignature(ByteArrayInputStream(content), pkHeader(PkKind.FILE_SIGNATURE) + rawText.copyOfRange(5, 101)) }
        // Y al revés: una firma de archivo no vale en un texto.
        val fileSig = handFile(me, content)
        rejects { verifySignedText(text + "\n" + SIGNATURE_PREFIX + base64UrlEncode(pkHeader(PkKind.TEXT_SIGNATURE) + fileSig.copyOfRange(5, 101))) }
    }

    @Test fun textoConSustitutoSueltoOMuyGrandeSeRechaza() {
        val me = fixedIdentity()
        rejects { signText("hola \uD800 mundo", me) }
        rejects { signText("a".repeat(MAX_TEXT_FILE_BYTES), me) }
        rejects { signText("é".repeat(MAX_TEXT_FILE_BYTES / 2), me) }
        // Lo más grande que se firma se verifica.
        var n = MAX_TEXT_FILE_BYTES - 1 - SIGNATURE_PREFIX.length - 135
        val big = signText("a".repeat(n), me)
        assertTrue(big.length <= MAX_TEXT_FILE_BYTES)
        assertEquals(n, verifySignedText(big).text.length)
        rejects { verifySignedText("a".repeat(MAX_TEXT_FILE_BYTES + 1)) }
        n = MAX_TEXT_FILE_BYTES + 1 - SIGNATURE_PREFIX.length - 135
        rejects { verifySignedText("a".repeat(n) + "\n" + tokenOf(signText("x", me))) }
    }

    @Test fun elTextoVacioSeFirma() {
        val me = fixedIdentity()
        val block = signText("  \r\n ", me)
        assertEquals("", verifySignedText(block).text)
        assertEquals("", verifySignedText(tokenOf(block)).text)
    }

    @Test fun toStringNoEnseñaElTexto() {
        val opened = verifySignedText(signText("secreto-del-texto", fixedIdentity()))
        assertTrue(!opened.toString().contains("secreto-del-texto"))
        assertEquals(opened, verifySignedText(signText("secreto-del-texto", fixedIdentity())))
        assertEquals(opened.hashCode(), verifySignedText(signText("secreto-del-texto", fixedIdentity())).hashCode())
        assertNotEquals(opened, verifySignedText(signText("secreto-del-texto", otherIdentity())))
    }

    // ---- Firma de archivo ----

    @Test fun archivoIdaYVuelta() {
        val me = fixedIdentity()
        for (size in listOf(0, 1, 100, 65_535, 65_536, 65_537, 131_072, 200_000)) {
            val content = data(size)
            val sig = signFile(ByteArrayInputStream(content), me)
            assertEquals(101, sig.size)
            assertArrayEquals(me.edPublic, verifyFileSignature(ByteArrayInputStream(content), sig))
        }
    }

    @Test fun archivoCoincideConElDisenoEscritoAMano() {
        val me = fixedIdentity()
        for (size in listOf(0, 5, 70_000)) {
            val content = data(size)
            val sig = signFile(ByteArrayInputStream(content), me)
            assertArrayEquals(handFile(me, content), sig)
            assertArrayEquals(pkHeader(PkKind.FILE_SIGNATURE), sig.copyOf(5))
            assertArrayEquals(me.edPublic, sig.copyOfRange(5, 37))
        }
        // La firma de Ed25519ph es la de «SHA-512 del contenido» con el contexto: se comprueba con BC a mano.
        val content = data(1000)
        val sig = signFile(ByteArrayInputStream(content), me)
        val v = Ed25519phSigner(fileContext.toByteArray(Charsets.US_ASCII)).apply { init(false, Ed25519PublicKeyParameters(me.edPublic, 0)) }
        v.update(content, 0, content.size)
        assertTrue(v.verifySignature(sig.copyOfRange(37, 101)))
    }

    @Test fun archivoSeLeeEnBloquesDe64KiBYNoDependeDeComoLleguen() {
        val me = fixedIdentity()
        val content = data(300_000)
        val reads = ArrayList<Int>()
        val spy = object : InputStream() {
            private val inner = ByteArrayInputStream(content)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads.add(len)
                return inner.read(b, off, minOf(len, 777)) // el flujo da trozos pequeños e irregulares
            }
        }
        val sig = signFile(spy, me)
        assertTrue(reads.isNotEmpty())
        assertTrue("mayor lectura ${reads.max()}", reads.max() <= 64 * 1024)
        assertArrayEquals(handFile(me, content), sig)
        assertArrayEquals(me.edPublic, verifyFileSignature(spy2(content), sig))
    }

    private fun spy2(content: ByteArray) = object : InputStream() {
        private val inner = ByteArrayInputStream(content)
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            assertTrue(len <= 64 * 1024)
            return inner.read(b, off, minOf(len, 1000))
        }
    }

    @Test fun archivoConUnByteCambiadoSeRechaza() {
        val me = fixedIdentity()
        val content = data(70_000)
        val sig = signFile(ByteArrayInputStream(content), me)
        for (at in listOf(0, 1, 65_535, 65_536, 69_999)) {
            rejects("byte $at") { verifyFileSignature(ByteArrayInputStream(flip(content, at)), sig) }
        }
        rejects { verifyFileSignature(ByteArrayInputStream(content + 0), sig) }
        rejects { verifyFileSignature(ByteArrayInputStream(content.copyOf(69_999)), sig) }
        rejects { verifyFileSignature(ByteArrayInputStream(ByteArray(0)), sig) }
        // Cada byte de la firma cuenta.
        for (i in sig.indices) {
            rejects("sig $i") { verifyFileSignature(ByteArrayInputStream(content), flip(sig, i)) }
        }
    }

    @Test fun archivoConOtroFirmanteSeRechaza() {
        val me = fixedIdentity()
        val other = otherIdentity()
        val content = data(1000)
        val sig = signFile(ByteArrayInputStream(content), me)
        val swapped = sig.copyOf().also { other.edPublic.copyInto(it, 5) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), swapped) }
        // La firma de otro sobre otro contenido, y sobre este con la clave de quien firmó otra cosa.
        val others = signFile(ByteArrayInputStream(content), other)
        assertArrayEquals(other.edPublic, verifyFileSignature(ByteArrayInputStream(content), others))
        assertNotEquals(Hex.toHexString(sig), Hex.toHexString(others))
        // Una clave que no es un punto de la curva.
        val junk = sig.copyOf().also { ByteArray(32) { 0xFF.toByte() }.copyInto(it, 5) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), junk) }
    }

    @Test fun sgsigDeOtroTipoOTamanoMalSeRechaza() {
        val me = fixedIdentity()
        val content = data(1000)
        for (kind in PkKind.entries.filter { it != PkKind.FILE_SIGNATURE }) {
            val e = rejects("$kind") { verifyFileSignature(ByteArrayInputStream(content), handFile(me, content, kind = kind)) }
            assertEquals("Es de otro tipo", e.message)
        }
        val sig = signFile(ByteArrayInputStream(content), me)
        for (i in 0 until 5) rejects("cabecera $i") { verifyFileSignature(ByteArrayInputStream(content), flip(sig, i)) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), sig + 0) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), sig.copyOf(100)) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), sig.copyOf(37)) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), ByteArray(0)) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), ByteArray(101)) }
        rejects { verifyFileSignature(ByteArrayInputStream(content), ByteArray(4096)) }
    }

    @Test fun firmaDeArchivoCifradoNoValeComoSgsig() {
        val me = fixedIdentity()
        val content = data(5000)
        // La misma estructura, firmada con el contexto de la firma dentro de un archivo cifrado: no vale como `.sgsig`.
        val sneaky = handFile(me, content, context = msgContext)
        val e = rejects { verifyFileSignature(ByteArrayInputStream(content), sneaky) }
        assertTrue(e.message!!.isNotEmpty())
        // Y la buena sí.
        assertArrayEquals(me.edPublic, verifyFileSignature(ByteArrayInputStream(content), handFile(me, content)))
        // Y a la inversa: un `.sgsig` no vale como firma del archivo cifrado (contextos distintos, comprobado con BC).
        val sgsig = handFile(me, content).copyOfRange(37, 101)
        val v = Ed25519phSigner(msgContext.toByteArray(Charsets.US_ASCII)).apply { init(false, Ed25519PublicKeyParameters(me.edPublic, 0)) }
        v.update(content, 0, content.size)
        assertTrue(!v.verifySignature(sgsig))
        // Ni con Ed25519 puro: firmar «etiqueta 00 ‖ contenido» no es lo mismo que Ed25519ph.
        val pure = me.sign(fileContext, content)
        rejects { verifyFileSignature(ByteArrayInputStream(content), pkHeader(PkKind.FILE_SIGNATURE) + me.edPublic + pure) }
    }

    @Test fun elArchivoNoSeLeeSiLaFirmaEsMala() {
        var read = false
        val spy = object : InputStream() {
            override fun read(): Int { read = true; return -1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { read = true; return -1 }
        }
        rejects { verifyFileSignature(spy, ByteArray(100)) }
        assertTrue(!read)
    }

    // ---- Vectores guardados ----

    private fun vector(key: String): String {
        val json = requireNotNull(javaClass.getResource("/vectors/seguridad-pk.json")) { "seguridad-pk.json" }.readText()
        return Json.parseToJsonElement(json).jsonObject.getValue(key).jsonPrimitive.content
    }

    private val vectorText = "Hola Ana, ¿quedamos mañana a las 10? 🔐"
    private val vectorFile = ByteArray(70_000) { (it * 7 + 3).toByte() }

    /**
     * Una firma de texto y una de archivo de la identidad fija (semilla 00 01 … 1F), guardadas en `seguridad-pk.json`
     * como `text_signature` (el bloque entero, con su `\n`) y `file_signature` (los 101 bytes del `.sgsig`, en
     * hexadecimal) sobre «Hola Ana, ¿quedamos mañana a las 10? 🔐» y sobre 70.000 bytes `(i*7+3)`. Se generaron una vez. Lo
     * permanente es que lo guardado se verifique (las primeras comprobaciones): si falla, el formato ha cambiado y no se
     * puede actualizar el vector, hay que arreglar el código.
     */
    @Test fun firmasGuardadas() {
        val me = fixedIdentity()
        val block = vector("text_signature")
        val opened = verifySignedText(block)
        assertEquals(vectorText, opened.text)
        assertArrayEquals(me.edPublic, opened.signerEd)
        assertTrue(block.startsWith("$vectorText\nSGS1:"))

        val sig = Hex.decode(vector("file_signature"))
        assertEquals(101, sig.size)
        assertArrayEquals(me.edPublic, verifyFileSignature(ByteArrayInputStream(vectorFile), sig))

        // Ed25519 es determinista: el código de hoy da los mismos bytes. El diseño escrito a mano, también.
        assertEquals(block, signText(vectorText, me))
        assertEquals(Hex.toHexString(sig), Hex.toHexString(signFile(ByteArrayInputStream(vectorFile), me)))
        assertEquals(block, vectorText + "\n" + handText(me, vectorText))
        assertEquals(Hex.toHexString(sig), Hex.toHexString(handFile(me, vectorFile)))

        // Y lo guardado no vale si cambia un byte.
        rejects { verifySignedText(block.replace("10?", "11?")) }
        rejects { verifyFileSignature(ByteArrayInputStream(flip(vectorFile, 69_999)), sig) }
    }
}
