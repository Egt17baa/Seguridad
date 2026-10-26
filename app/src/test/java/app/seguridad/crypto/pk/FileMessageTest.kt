package app.seguridad.crypto.pk

import app.seguridad.crypto.Aead
import app.seguridad.crypto.CHUNK_BYTES
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.STREAM_PREFIX_BYTES
import app.seguridad.crypto.aead
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Archivo cifrado para un contacto (`.sgenc` tipo 3): ida y vuelta, manipulación, truncado, reordenado, firma entre
 * formatos, memoria constante y el vector guardado.
 *
 * El formato se lee para siempre. Los tests de rechazo construyen archivos **bien sellados** pero mal formados con
 * `hand`, que reimplementa el diseño con `hpkeSuite()` y `aead(...)` a mano, sin pasar por `encryptFileFor`: así el
 * diseño y el código se comprueban el uno contra el otro.
 */
class FileMessageTest {
    private fun fixedIdentity() = Identity(ByteArray(32) { it.toByte() })

    /** «Ana» de los vectores: otra semilla fija. */
    private fun anaIdentity() = Identity(ByteArray(32) { (0xA0 + it).toByte() })

    private fun carolIdentity() = Identity(ByteArray(32) { (0x60 + it).toByte() })

    private fun cardOf(id: Identity, name: String = "Contacto") = parseCard(id.cardBytes(name))

    private fun rejects(message: String = "", block: () -> Unit): CryptoFailure =
        assertThrows(message, CryptoFailure::class.java) { block() }

    private val info = "seguridad-file-v1".toByteArray(Charsets.US_ASCII)
    private val keyLabel = "seguridad-file-key-v1".toByteArray(Charsets.US_ASCII)
    private val msgContext = "seguridad-file-msg-sig-v1"
    private val sgsigContext = "seguridad-file-sig-v1"
    private val ikm = ByteArray(64) { (0x40 + it).toByte() }
    private val prefix = ByteArray(STREAM_PREFIX_BYTES) { (0x20 + it).toByte() }

    private fun data(n: Int, seed: Int = 7) = ByteArray(n) { (it * seed + 3).toByte() }

    private fun encrypt(plain: ByteArray, recipient: Identity, sender: Identity?, name: String = "doc.pdf"): ByteArray {
        val out = ByteArrayOutputStream()
        encryptFileFor(ByteArrayInputStream(plain), out, name, cardOf(recipient), sender)
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, me: Identity, out: OutputStream = ByteArrayOutputStream()): OpenedFile =
        decryptFileFor(ByteArrayInputStream(file), out, me)

    private fun nonce(counter: Int, last: Boolean) =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    /** El archivo hecho a mano y dónde acaba cada parte (la cabecera, el registro 0, cada registro de datos, el final). */
    private class Hand(val bytes: ByteArray, val ends: List<Int>)

    /**
     * El diseño escrito a mano. [flags] y [finalPlain] pisan lo que haría un cifrador correcto; [signContext] es el
     * contexto de Ed25519ph con el que se firma; [signer] es quien firma (por defecto [sender]).
     */
    private fun hand(
        recipient: Card,
        sender: Identity?,
        plain: ByteArray,
        name: String = "doc.pdf",
        flags: Int? = null,
        record0Ed: ByteArray? = sender?.edPublic,
        finalPlain: ByteArray? = null,
        signContext: String = msgContext,
        signer: Identity? = sender,
        recipientForSignature: ByteArray = recipient.kemPublic,
        chunk: Int = CHUNK_BYTES,
    ): Hand {
        val h = hpkeSuite()
        val ctx = h.setupBaseS(h.deserializePublicKey(recipient.kemPublic), info, ikm)
        val header = byteArrayOf(0x53, 0x47, 0x02, 0x03, 0x01) + ctx.encapsulation + prefix
        val key = ctx.export(keyLabel, 32)
        fun seal(counter: Int, last: Boolean, p: ByteArray) =
            aead(Aead.CHACHA20_POLY1305, true, key, nonce(counter, last), header, p)

        val out = ByteArrayOutputStream()
        val ends = ArrayList<Int>()
        out.write(header)
        ends.add(out.size())

        val signed = sender != null
        val record0 = byteArrayOf((flags ?: if (signed) 1 else 0).toByte()) +
            (if (signed) (record0Ed ?: sender.edPublic) else ByteArray(0)) + name.toByteArray(Charsets.UTF_8)
        val sealed0 = seal(0, false, record0)
        out.write(ByteBuffer.allocate(2).putShort(sealed0.size.toShort()).array())
        out.write(sealed0)
        ends.add(out.size())

        val sig = signer?.fileSigner(signContext)
        sig?.update(header, 0, header.size)
        val rk = recipientForSignature
        sig?.update(rk, 0, rk.size)
        sig?.update(record0, 0, record0.size)
        var counter = 1
        var at = 0
        while (at < plain.size) {
            val piece = plain.copyOfRange(at, minOf(plain.size, at + chunk))
            sig?.update(piece, 0, piece.size)
            out.write(seal(counter++, false, piece))
            ends.add(out.size())
            at += piece.size
        }
        out.write(seal(counter, true, finalPlain ?: sig?.generateSignature() ?: ByteArray(0)))
        ends.add(out.size())
        return Hand(out.toByteArray(), ends)
    }

    private fun flip(b: ByteArray, at: Int) = b.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }

    // ---- Ida y vuelta ----

    @Test fun idaYVueltaFirmadaYSinFirmar() {
        val a = fixedIdentity()
        val b = anaIdentity()
        for (size in listOf(0, 1, CHUNK_BYTES, CHUNK_BYTES + 1, 1024 * 1024)) {
            for (signed in listOf(true, false)) {
                val plain = data(size)
                val file = encrypt(plain, b, if (signed) a else null, "informe.pdf")
                val out = ByteArrayOutputStream()
                val opened = decrypt(file, b, out)
                assertEquals("size=$size signed=$signed", "informe.pdf", opened.name)
                assertArrayEquals("size=$size signed=$signed", plain, out.toByteArray())
                if (signed) assertArrayEquals(a.edPublic, opened.senderEd) else assertNull(opened.senderEd)
            }
        }
    }

    @Test fun elTamanoDelArchivoCifradoEsElEsperado() {
        val b = anaIdentity()
        for (size in listOf(0, 1, CHUNK_BYTES, CHUNK_BYTES + 1, 3 * CHUNK_BYTES + 17)) {
            val records = (size + CHUNK_BYTES - 1) / CHUNK_BYTES
            val name = "doc.pdf".length
            val unsigned = 1132 + 2 + (1 + name + 16) + size + 16 * records + 16
            assertEquals("sin firmar $size", unsigned, encrypt(data(size), b, null).size)
            assertEquals("firmado $size", unsigned + 32 + 64, encrypt(data(size), b, fixedIdentity()).size)
        }
    }

    @Test fun dosCifradosDelMismoArchivoNoSeParecen() {
        val b = anaIdentity()
        val one = encrypt(data(100), b, null)
        val two = encrypt(data(100), b, null)
        assertEquals(one.size, two.size)
        assertNotEquals(Hex.toHexString(one), Hex.toHexString(two))
        // Ni la encapsulación ni el prefijo STREAM se repiten.
        assertFalse(one.copyOfRange(5, 1132).contentEquals(two.copyOfRange(5, 1132)))
    }

    @Test fun elCifradorCoincideConElDisenoEscritoAMano() {
        val a = fixedIdentity()
        val b = anaIdentity()
        for (size in listOf(0, 1, CHUNK_BYTES - 1, CHUNK_BYTES, CHUNK_BYTES + 1, 2 * CHUNK_BYTES, 2 * CHUNK_BYTES + 5)) {
            for (sender in listOf(a, null)) {
                val out = ByteArrayOutputStream()
                encryptFileFor(ByteArrayInputStream(data(size)), out, "doc.pdf", cardOf(b), sender, ikm, prefix)
                assertEquals("size=$size signed=${sender != null}", Hex.toHexString(hand(cardOf(b), sender, data(size)).bytes), Hex.toHexString(out.toByteArray()))
            }
        }
    }

    @Test fun lasLecturasCortasNoCambianElResultado() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val plain = data(2 * CHUNK_BYTES + 123)
        // Un flujo que entrega 1 byte (o pocos) de cada vez.
        fun dribble(bytes: ByteArray, step: Int) = object : InputStream() {
            var pos = 0
            override fun read(): Int = if (pos >= bytes.size) -1 else bytes[pos++].toInt() and 0xFF
            override fun read(buf: ByteArray, off: Int, len: Int): Int {
                if (pos >= bytes.size) return -1
                val n = minOf(len, step, bytes.size - pos)
                System.arraycopy(bytes, pos, buf, off, n)
                pos += n
                return n
            }
        }
        for (signer in listOf(a, null)) {
            val enc = ByteArrayOutputStream()
            encryptFileFor(dribble(plain, 1000), enc, "doc.pdf", cardOf(b), signer, ikm, prefix)
            assertEquals(Hex.toHexString(hand(cardOf(b), signer, plain).bytes), Hex.toHexString(enc.toByteArray()))
            val out = ByteArrayOutputStream()
            val opened = decryptFileFor(dribble(enc.toByteArray(), 7), out, b)
            assertEquals("doc.pdf", opened.name)
            assertArrayEquals(plain, out.toByteArray())
        }
    }

    @Test fun elNombreSeLimpia() {
        val b = anaIdentity()
        for ((given, expected) in listOf(
            "../../etc/passwd" to "passwd",
            "C:\\datos\\foto.png" to "foto.png",
            "a\u0000b\nc.txt" to "abc.txt",
            "" to "archivo",
            "   " to "archivo",
            "ñandú 🔐.txt" to "ñandú 🔐.txt",
            "x".repeat(2000) to "x".repeat(120),
        )) {
            val file = encrypt(data(10), b, null, given)
            assertEquals(expected, decrypt(file, b).name)
        }
    }

    @Test fun unNombreConUnSustitutoSueltoNoRompeElCifrado() {
        val b = anaIdentity()
        val opened = decrypt(encrypt(data(10), b, null, "a\uD800b.txt"), b)
        assertEquals("a?b.txt", opened.name)
    }

    @Test fun unaClaveDelDestinatarioQueNoValeSeRechaza() {
        val b = anaIdentity()
        val bad = Card(b.edPublic, ByteArray(1216) { 1 }.copyOf(100), "x")
        rejects("clave corta") { encryptFileFor(ByteArrayInputStream(ByteArray(1)), ByteArrayOutputStream(), "a", bad, null) }
        rejects("ikmE de otro tamaño") {
            encryptFileFor(ByteArrayInputStream(ByteArray(1)), ByteArrayOutputStream(), "a", cardOf(b), null, ByteArray(63), prefix)
        }
        rejects("prefijo de otro tamaño") {
            encryptFileFor(ByteArrayInputStream(ByteArray(1)), ByteArrayOutputStream(), "a", cardOf(b), null, ikm, ByteArray(6))
        }
    }

    // ---- Manipulación ----

    /** Un archivo de 4 registros de datos (el último corto) para los tests de manipulación. */
    private fun sample(signed: Boolean) =
        hand(cardOf(anaIdentity()), if (signed) fixedIdentity() else null, data(3 * CHUNK_BYTES + 5))

    @Test fun cambiarUnBitEnCualquierParteSeRechaza() {
        val b = anaIdentity()
        for (signed in listOf(true, false)) {
            val h = sample(signed)
            val e = h.ends // [cabecera, registro 0, datos 1..4, final]
            assertEquals(7, e.size)
            val positions = linkedMapOf(
                "marca SG" to 0, "versión" to 2, "tipo" to 3, "suite" to 4,
                "enc (primero)" to 5, "enc (medio)" to 600, "enc (último)" to 1124,
                "prefijo STREAM (primero)" to 1125, "prefijo STREAM (último)" to 1131,
                "longitud del registro 0 (alta)" to e[0], "longitud del registro 0 (baja)" to e[0] + 1,
                "registro 0 (primero)" to e[0] + 2, "registro 0 (último)" to e[1] - 1,
                "datos 1 (primero)" to e[1], "datos 1 (medio)" to e[1] + 1000, "datos 1 (último)" to e[2] - 1,
                "datos 3 (medio)" to e[3] + 5000, "datos 4 (primero)" to e[4], "datos 4 (último)" to e[5] - 1,
                "final (primero)" to e[5], "final (último)" to e[6] - 1,
            )
            for ((what, at) in positions) {
                rejects("bit en $what (firmado=$signed)") { decrypt(flip(h.bytes, at), b) }
            }
        }
    }

    @Test fun elArchivoSinTocarSeAbre() {
        val h = sample(true)
        val out = ByteArrayOutputStream()
        val opened = decrypt(h.bytes, anaIdentity(), out)
        assertArrayEquals(fixedIdentity().edPublic, opened.senderEd)
        assertArrayEquals(data(3 * CHUNK_BYTES + 5), out.toByteArray())
    }

    @Test fun truncarEnCadaFronteraYAlrededorSeRechaza() {
        val b = anaIdentity()
        for (signed in listOf(true, false)) {
            val h = sample(signed)
            val cuts = sortedSetOf(0, 1, 4, 5, 6, 1131)
            for (end in h.ends.dropLast(1)) for (d in -1..1) cuts.add(end + d)
            cuts.add(h.bytes.size - 1)
            for (cut in cuts) {
                if (cut >= h.bytes.size) continue
                rejects("truncado en $cut (firmado=$signed)") { decrypt(h.bytes.copyOf(cut), b) }
            }
        }
    }

    @Test fun quitarElRegistroFinalSeRechazaYNoHayFinalFalso() {
        val b = anaIdentity()
        for (signed in listOf(true, false)) {
            for (size in listOf(0, 1, CHUNK_BYTES, CHUNK_BYTES + 1, 2 * CHUNK_BYTES)) {
                val h = hand(cardOf(b), if (signed) fixedIdentity() else null, data(size))
                val withoutFinal = h.bytes.copyOf(h.ends[h.ends.size - 2])
                rejects("sin final: tamaño $size firmado=$signed") { decrypt(withoutFinal, b) }
            }
        }
    }

    @Test fun unRegistroDeDatosMarcadoComoUltimoEsElFinalFalso() {
        // Un cifrador malo que marca como último el último registro de datos y no pone final: se rechaza.
        val b = anaIdentity()
        val card = cardOf(b)
        val h = hpkeSuite()
        val ctx = h.setupBaseS(h.deserializePublicKey(card.kemPublic), info, ikm)
        val header = byteArrayOf(0x53, 0x47, 0x02, 0x03, 0x01) + ctx.encapsulation + prefix
        val key = ctx.export(keyLabel, 32)
        val s0 = aead(Aead.CHACHA20_POLY1305, true, key, nonce(0, false), header, byteArrayOf(0) + "a".toByteArray())
        val s1 = aead(Aead.CHACHA20_POLY1305, true, key, nonce(1, true), header, data(10))
        val file = header + ByteBuffer.allocate(2).putShort(s0.size.toShort()).array() + s0 + s1
        rejects { decrypt(file, b) }
    }

    @Test fun intercambiarDosRegistrosDeDatosSeRechaza() {
        val b = anaIdentity()
        for (signed in listOf(true, false)) {
            val h = sample(signed)
            val e = h.ends
            val swapped = h.bytes.copyOf()
            // Datos 1 y datos 2 miden lo mismo (64 KiB + etiqueta).
            System.arraycopy(h.bytes, e[1], swapped, e[2], e[2] - e[1])
            System.arraycopy(h.bytes, e[2], swapped, e[1], e[3] - e[2])
            assertFalse(swapped.contentEquals(h.bytes))
            rejects("firmado=$signed") { decrypt(swapped, b) }
        }
    }

    @Test fun repetirOQuitarUnRegistroDeDatosSeRechaza() {
        val b = anaIdentity()
        val h = sample(true)
        val e = h.ends
        val duplicated = h.bytes.copyOf(e[2]) + h.bytes.copyOfRange(e[1], e[2]) + h.bytes.copyOfRange(e[2], h.bytes.size)
        rejects("repetido") { decrypt(duplicated, b) }
        val removed = h.bytes.copyOf(e[1]) + h.bytes.copyOfRange(e[2], h.bytes.size)
        rejects("quitado") { decrypt(removed, b) }
    }

    @Test fun cualquierByteDeMasAlFinalSeRechaza() {
        val b = anaIdentity()
        for (signed in listOf(true, false)) {
            val h = sample(signed)
            rejects("1 byte de más") { decrypt(h.bytes + 0, b) }
            rejects("un registro de más") { decrypt(h.bytes + h.bytes.copyOfRange(h.ends[5], h.ends[6]), b) }
        }
    }

    @Test fun otraIdentidadNoLoAbre() {
        val a = fixedIdentity()
        val b = anaIdentity()
        for (signer in listOf(a, null)) {
            val file = encrypt(data(CHUNK_BYTES + 10), b, signer)
            for (other in listOf(a, carolIdentity())) {
                val out = ByteArrayOutputStream()
                val e = rejects { decrypt(file, other, out) }
                assertEquals(NOT_FOR_ME, e.message)
                assertEquals("no se escribe nada con otra identidad", 0, out.size())
            }
        }
    }

    @Test fun elFlujoQueNoEsUnArchivoSeRechazaConSuMotivo() {
        val b = anaIdentity()
        val file = encrypt(data(10), b, null)
        for (kind in PkKind.entries) {
            if (kind == PkKind.FILE_MESSAGE) continue
            rejects("tipo $kind") { decrypt(file.copyOf().also { it[3] = kind.id }, b) }
        }
        assertEquals("Es de otro tipo", rejects { decrypt(file.copyOf().also { it[3] = PkKind.TEXT_MESSAGE.id }, b) }.message)
        assertEquals("No es un dato compatible", rejects { decrypt("hola".toByteArray(), b) }.message)
        assertEquals("No es un dato compatible", rejects { decrypt(ByteArray(0), b) }.message)
    }

    // ---- Marcas y registro 0 ----

    @Test fun lasMarcasDesconocidasSeRechazan() {
        val a = fixedIdentity()
        val b = anaIdentity()
        for (flags in listOf(0x02, 0x03, 0x04, 0x80, 0xFE, 0xFF)) {
            for (signer in listOf(a, null)) {
                // Firmado o no, con una marca que no es 0 ni 1.
                val file = hand(cardOf(b), signer, data(10), flags = flags).bytes
                val e = rejects("marcas $flags firmado=${signer != null}") { decrypt(file, b) }
                assertNotEquals(NOT_FOR_ME, e.message)
            }
        }
        // Y con las dos válidas, el mismo constructor abre bien (el test no rechaza por otra causa).
        assertNull(decrypt(hand(cardOf(b), null, data(10), flags = 0).bytes, b).senderEd)
        assertArrayEquals(a.edPublic, decrypt(hand(cardOf(b), a, data(10), flags = 1).bytes, b).senderEd)
    }

    @Test fun elRegistroCeroDebeCuadrarConLaMarca() {
        val a = fixedIdentity()
        val b = anaIdentity()
        // Marcado como firmado pero sin clave del remitente: demasiado corto.
        val noKey = hand(cardOf(b), null, data(10), name = "", flags = 1).bytes
        rejects("firmado sin clave") { decrypt(noKey, b) }
        // Sin marca de firmado y con un final de firma, o firmado y con un final vacío o de otro tamaño.
        rejects("sin firmar, final con 64 bytes") { decrypt(hand(cardOf(b), null, data(10), finalPlain = ByteArray(64)).bytes, b) }
        rejects("sin firmar, final con 1 byte") { decrypt(hand(cardOf(b), null, data(10), finalPlain = ByteArray(1)).bytes, b) }
        rejects("firmado, final vacío") { decrypt(hand(cardOf(b), a, data(10), finalPlain = ByteArray(0)).bytes, b) }
        rejects("firmado, final de 63") { decrypt(hand(cardOf(b), a, data(10), finalPlain = ByteArray(63)).bytes, b) }
        rejects("firmado, final de 65") { decrypt(hand(cardOf(b), a, data(10), finalPlain = ByteArray(65)).bytes, b) }
    }

    @Test fun unNombreQueNoEsUtf8SeRechaza() {
        val b = anaIdentity()
        val h = hpkeSuite()
        val ctx = h.setupBaseS(h.deserializePublicKey(cardOf(b).kemPublic), info, ikm)
        val header = byteArrayOf(0x53, 0x47, 0x02, 0x03, 0x01) + ctx.encapsulation + prefix
        val key = ctx.export(keyLabel, 32)
        fun seal(counter: Int, last: Boolean, p: ByteArray) = aead(Aead.CHACHA20_POLY1305, true, key, nonce(counter, last), header, p)
        val s0 = seal(0, false, byteArrayOf(0, 0xC3.toByte(), 0x28))
        val file = header + ByteBuffer.allocate(2).putShort(s0.size.toShort()).array() + s0 + seal(1, true, ByteArray(0))
        rejects { decrypt(file, b) }
    }

    @Test fun laLongitudDelRegistroCeroTieneLimites() {
        val b = anaIdentity()
        val file = encrypt(data(10), b, null)
        for (len in listOf(0, 1, 16, 1 + 32 + 1024 + 16 + 1, 0xFFFF)) {
            val bad = file.copyOf()
            bad[1132] = (len ushr 8).toByte()
            bad[1133] = len.toByte()
            rejects("longitud $len") { decrypt(bad, b) }
        }
    }

    // ---- Firma ----

    @Test fun unaFirmaHechaConElContextoDelSgsigNoValeDentroDelArchivo() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val card = cardOf(b)
        // La firma Ed25519ph de `.sgsig` sobre los mismos bytes que cubre la firma del archivo, puesta en el registro final.
        val honest = hand(card, a, data(1000))
        val header = honest.bytes.copyOf(1132)
        val record0 = byteArrayOf(1) + a.edPublic + "doc.pdf".toByteArray()
        val crossSigner = a.fileSigner(sgsigContext)
        for (part in listOf(header, card.kemPublic, record0, data(1000))) crossSigner.update(part, 0, part.size)
        val crossSig = crossSigner.generateSignature()
        // Es una firma válida de `.sgsig` sobre esos bytes: lo comprueba el verificador del `.sgsig`.
        val check = fileVerifier(a.edPublic, sgsigContext)
        for (part in listOf(header, card.kemPublic, record0, data(1000))) check.update(part, 0, part.size)
        assertTrue(check.verifySignature(crossSig))

        val forged = hand(card, a, data(1000), finalPlain = crossSig).bytes
        val e = rejects { decrypt(forged, b) }
        assertEquals("La firma del archivo no es válida", e.message)
        // Y el constructor, con el contexto correcto, abre: la única diferencia es el contexto.
        assertArrayEquals(a.edPublic, decrypt(hand(card, a, data(1000)).bytes, b).senderEd)
        assertArrayEquals(a.edPublic, decrypt(hand(card, a, data(1000), signContext = msgContext).bytes, b).senderEd)
        rejects { decrypt(hand(card, a, data(1000), signContext = sgsigContext).bytes, b) }
    }

    @Test fun unaFirmaDeOtraPersonaOUnaFirmaMalSeRechaza() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val c = carolIdentity()
        // La clave del registro 0 es la de A, pero firma C.
        rejects("firma de otro") { decrypt(hand(cardOf(b), a, data(50), signer = c).bytes, b) }
        // Una firma de 64 bytes cualquiera.
        rejects("firma de ceros") { decrypt(hand(cardOf(b), a, data(50), finalPlain = ByteArray(64)).bytes, b) }
        // La clave del registro 0 no es la de quien firma, aunque la firma sea válida para su propia clave.
        rejects("clave cambiada") { decrypt(hand(cardOf(b), a, data(50), record0Ed = c.edPublic).bytes, b) }
        // La clave del registro 0 no es un punto válido.
        rejects("clave inválida") { decrypt(hand(cardOf(b), a, data(50), record0Ed = ByteArray(32) { 0xFF.toByte() }).bytes, b) }
    }

    @Test fun laFirmaCubreElRegistroCeroYLosDatos() {
        val a = fixedIdentity()
        val b = anaIdentity()
        val card = cardOf(b)
        val honest = hand(card, a, data(50))
        // Firma del archivo con un nombre, y un registro 0 con otro nombre pero la misma firma.
        val sigOnlyOver = hand(card, a, data(50), name = "otro.pdf")
        // Firmas distintas: cambia el nombre, que está en el registro 0.
        assertFalse(honest.bytes.copyOfRange(honest.ends[honest.ends.size - 2], honest.bytes.size)
            .contentEquals(sigOnlyOver.bytes.copyOfRange(sigOnlyOver.ends[sigOnlyOver.ends.size - 2], sigOnlyOver.bytes.size)))
        // Con la firma del archivo «doc.pdf» en un registro 0 con «otro.pdf», se rechaza.
        val sigPlain = run {
            val s = a.fileSigner(msgContext)
            val header = honest.bytes.copyOf(1132)
            val record0 = byteArrayOf(1) + a.edPublic + "doc.pdf".toByteArray()
            for (part in listOf(header, card.kemPublic, record0, data(50))) s.update(part, 0, part.size)
            s.generateSignature()
        }
        rejects("nombre cambiado") { decrypt(hand(card, a, data(50), name = "otro.pdf", finalPlain = sigPlain).bytes, b) }
        // Lo mismo con los datos.
        rejects("datos cambiados") { decrypt(hand(card, a, data(50, seed = 9), finalPlain = sigPlain).bytes, b) }
    }

    @Test fun elReenvioAOtraPersonaNoConservaLaFirma() {
        // Ana abre un archivo firmado por A para ella, y lo vuelve a cifrar para Carol con la misma firma: no vale.
        val a = fixedIdentity()
        val ana = anaIdentity()
        val carol = carolIdentity()
        val original = hand(cardOf(ana), a, data(2000))
        val sig = run {
            val raw = aead(Aead.CHACHA20_POLY1305, false, hpkeKey(original.bytes, ana), nonce(2, true), original.bytes.copyOf(1132), original.bytes.copyOfRange(original.ends[2], original.bytes.size))
            raw
        }
        assertEquals(64, sig.size)
        val forwarded = hand(cardOf(carol), a, data(2000), finalPlain = sig)
        rejects("a Carol con la firma de Ana") { decrypt(forwarded.bytes, carol) }
        // Aunque la firma cubriera la clave de Ana en vez de la de Carol, la cabecera es otra.
        val forwarded2 = hand(cardOf(carol), a, data(2000), recipientForSignature = cardOf(ana).kemPublic)
        rejects("firma sobre la clave de Ana") { decrypt(forwarded2.bytes, carol) }
        // Y la firma correcta de A para Carol, sí.
        assertArrayEquals(a.edPublic, decrypt(hand(cardOf(carol), a, data(2000)).bytes, carol).senderEd)
    }

    /** La clave de ChaCha20-Poly1305 de [file] según [me], con HPKE a mano. */
    private fun hpkeKey(file: ByteArray, me: Identity): ByteArray =
        hpkeSuite().setupBaseR(file.copyOfRange(5, 1125), me.kemKeyPair(), info).export(keyLabel, 32)

    @Test fun elArchivoNoSePuedeAbrirComoMensajeDeTextoNiAlReves() {
        val b = anaIdentity()
        val file = encrypt(data(10), b, null)
        rejects { decryptMessage("SGM1:" + base64UrlEncode(file), b) }
        val message = encryptMessage("hola", cardOf(b), null)
        rejects { decrypt(base64UrlDecode(message.removePrefix("SGM1:")), b) }
    }

    // ---- pkKindOfFile ----

    @Test fun pkKindOfFileReconoceLosArchivos() {
        fun head(kind: PkKind) = pkHeader(kind) + ByteArray(20)
        assertEquals(PkKind.FILE_MESSAGE, pkKindOfFile(head(PkKind.FILE_MESSAGE)))
        assertEquals(PkKind.FILE_SIGNATURE, pkKindOfFile(head(PkKind.FILE_SIGNATURE)))
        // Solo se miran los 5 primeros bytes.
        assertEquals(PkKind.FILE_MESSAGE, pkKindOfFile(pkHeader(PkKind.FILE_MESSAGE)))
        assertEquals(PkKind.FILE_SIGNATURE, pkKindOfFile(pkHeader(PkKind.FILE_SIGNATURE) + "cualquier cosa".toByteArray()))
        for (kind in listOf(PkKind.CARD, PkKind.TEXT_MESSAGE, PkKind.TEXT_SIGNATURE)) assertNull(kind.name, pkKindOfFile(head(kind)))
        // Un archivo real.
        assertEquals(PkKind.FILE_MESSAGE, pkKindOfFile(encrypt(data(3), anaIdentity(), null).copyOf(5)))
    }

    @Test fun pkKindOfFileNuncaLanza() {
        for (n in 0..4) assertNull("longitud $n", pkKindOfFile(pkHeader(PkKind.FILE_MESSAGE).copyOf(n)))
        assertNull(pkKindOfFile(ByteArray(0)))
        assertNull(pkKindOfFile(ByteArray(100)))
        assertNull(pkKindOfFile("hola, esto no es nada".toByteArray()))
        for (i in 0..4) {
            val h = pkHeader(PkKind.FILE_MESSAGE)
            h[i] = (h[i].toInt() xor 0x10).toByte()
            assertNull("byte $i", pkKindOfFile(h))
        }
        // Un tipo que no existe.
        assertNull(pkKindOfFile(pkHeader(PkKind.FILE_MESSAGE).also { it[3] = 9 }))
        assertNull(pkKindOfFile(pkHeader(PkKind.FILE_MESSAGE).also { it[3] = 0 }))
        // La versión 1 de `.sgenc` (con contraseña) no es de clave pública.
        assertNull(pkKindOfFile(byteArrayOf(0x53, 0x47, 0x01, 0x03, 0x01)))
    }

    // ---- Memoria ----

    /** 64 MiB sin guardar nunca más que unos registros. */
    private class PatternStream(private val size: Long, private val onRead: () -> Unit = {}) : InputStream() {
        private var pos = 0L
        override fun read(): Int = if (pos >= size) -1 else ((pos++ * 31) and 0xFF).toInt()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= size) return -1
            val n = minOf(len.toLong(), size - pos).toInt()
            for (i in 0 until n) b[off + i] = (((pos + i) * 31) and 0xFF).toByte()
            pos += n
            onRead()
            return n
        }
    }

    private class DigestSink(private val onWrite: () -> Unit = {}) : OutputStream() {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        override fun write(b: Int) {
            digest.update(b.toByte())
            count++
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            digest.update(b, off, len)
            count += len
            onWrite()
        }
    }

    /**
     * Lo que está vivo en el montón ahora: se fuerza una recolección completa y se mide. Con ella, la basura de los
     * registros ya usados no cuenta, y lo que queda es lo que el cifrado retiene de verdad (los búferes en uso).
     */
    private fun liveBytes(): Long {
        val rt = Runtime.getRuntime()
        System.gc()
        return rt.totalMemory() - rt.freeMemory()
    }

    /** Mide el pico de memoria viva: `sample` se llama con cada lectura o escritura y mide una de cada 16 (cada 1 MiB o así). */
    private class PeakMeter(private val live: () -> Long) {
        var baseline = 0L
        var peak = 0L
        private var calls = 0
        fun start() { baseline = live(); peak = baseline }
        fun sampleNow() { peak = maxOf(peak, live()) }
        fun sample() {
            if (++calls % 16 == 0) sampleNow()
        }
        val delta get() = peak - baseline
    }

    @Test fun elMetodoDeMedirMemoriaVeUnBufer() {
        // Control: si se reservaran 16 MiB, el medidor los vería.
        val meter = PeakMeter(::liveBytes)
        meter.start()
        val hold = ByteArray(16 * 1024 * 1024)
        hold[0] = 1
        meter.sampleNow()
        assertTrue("el medidor no ve 16 MiB: ${meter.delta}", meter.delta > 12L * 1024 * 1024)
        assertEquals(1, hold[0].toInt())
    }

    /**
     * Un archivo firmado de 64 MiB se cifra y se descifra, con los dos lados conectados, sin pasar de 8 MiB de pico
     * de memoria viva. Mismo método que `StreamingMemoryTest` (el archivo pasa por tuberías sin guardarse), pero aquí
     * el archivo cabe en el montón del test, así que además se mide con recolecciones forzadas lo que se retiene.
     */
    @Test fun unArchivoDeSesentaYCuatroMiBSeCifraYSeDescifraSinPasarDeOchoMiB() {
        val size = 64L * 1024 * 1024
        val sender = fixedIdentity()
        val me = anaIdentity()
        val card = cardOf(me)
        val pipeIn = PipedInputStream(128 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)
        val meter = PeakMeter(::liveBytes)
        meter.start()
        var failure: Throwable? = null
        val writer = thread {
            try {
                pipeOut.use { encryptFileFor(PatternStream(size) { meter.sample() }, it, "video.mp4", card, sender) }
            } catch (t: Throwable) {
                failure = t
            }
        }
        val sink = DigestSink { meter.sample() }
        val opened = try {
            decryptFileFor(pipeIn, sink, me)
        } finally {
            pipeIn.close()
            writer.join()
        }
        failure?.let { throw it }
        assertEquals("video.mp4", opened.name)
        assertArrayEquals(sender.edPublic, opened.senderEd)
        assertEquals(size, sink.count)
        val expected = MessageDigest.getInstance("SHA-256").also { d ->
            PatternStream(size).use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; d.update(b, 0, n) } }
        }
        assertArrayEquals(expected.digest(), sink.digest.digest())
        assertTrue("pico de ${meter.delta} bytes sobre ${meter.baseline}", meter.delta < 8L * 1024 * 1024)
    }

    // ---- Vector guardado ----

    private fun vector(key: String): String {
        val json = requireNotNull(javaClass.getResource("/vectors/seguridad-pk.json")) { "seguridad-pk.json" }.readText()
        return Json.parseToJsonElement(json).jsonObject.getValue(key).jsonPrimitive.content
    }

    private val vectorPlain = ByteArray(70_000) { (it * 7 + 3).toByte() }

    /**
     * Un archivo firmado de 70.000 bytes fijos con el nombre «doc.pdf», de la identidad fija (semilla 00 01 … 1F) a «Ana»
     * (semilla A0 A1 … BF), con `ikmE` = 40 41 … 7F y prefijo STREAM = 20 21 … 26, guardado en `seguridad-pk.json` como
     * `file_signed_hex`. Se generó una vez. Lo permanente es que el archivo guardado se abra (las primeras
     * comprobaciones): si falla, el formato ha cambiado y no se puede actualizar el vector, hay que arreglar el código.
     */
    @Test fun archivoGuardado() {
        val sender = fixedIdentity()
        val ana = anaIdentity()
        val file = Hex.decode(vector("file_signed_hex"))
        assertEquals(PkKind.FILE_MESSAGE, pkKindOfFile(file.copyOf(5)))
        val out = ByteArrayOutputStream()
        val opened = decrypt(file, ana, out)
        assertEquals("doc.pdf", opened.name)
        assertArrayEquals(sender.edPublic, opened.senderEd)
        assertArrayEquals(vectorPlain, out.toByteArray())

        // Si esto falla, cambió cómo Bouncy Castle usa `ikmE`; el archivo guardado sigue abriéndose si el resto del test pasa.
        val again = ByteArrayOutputStream()
        encryptFileFor(ByteArrayInputStream(vectorPlain), again, "doc.pdf", cardOf(ana, "Ana"), sender, ikm, prefix)
        assertEquals(Hex.toHexString(file), Hex.toHexString(again.toByteArray()))
        // Y el diseño escrito a mano da lo mismo.
        assertEquals(Hex.toHexString(file), Hex.toHexString(hand(cardOf(ana, "Ana"), sender, vectorPlain).bytes))

        // Nadie más lo abre.
        assertEquals(NOT_FOR_ME, rejects { decrypt(file, sender) }.message)
        // Y cada bit tocado del final se rechaza.
        rejects { decrypt(flip(file, file.size - 1), ana) }
    }
}
