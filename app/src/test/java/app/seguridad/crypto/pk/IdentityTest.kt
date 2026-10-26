package app.seguridad.crypto.pk

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.hex
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.Ed25519phSigner
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Suite 1 (cabecera, etiquetas), identidad (de la semilla a las claves), firma y huella.
 *
 * La corrección la fijan los vectores oficiales (RFC 8032 y el apéndice A.5 de draft-ietf-hpke-pq-04), que están en
 * `src/test/resources/vectors`. Los valores de `identidadFija` y de `huella` son, en cambio, valores guardados que
 * calculó esta misma implementación: protegen contra un cambio futuro de biblioteca, no prueban nada por sí solos.
 */
class IdentityTest {
    private fun vectors(name: String): Map<String, String> =
        requireNotNull(javaClass.getResource("/vectors/$name")) { name }.readText().lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun Map<String, String>.bytes(key: String): ByteArray = Hex.decode(getValue(key))

    private inline fun <reified T : Throwable> failsWith(message: String = "", crossinline block: () -> Unit): T =
        assertThrows(message, T::class.java) { block() }

    private fun fixedSeed() = ByteArray(32) { it.toByte() }

    private fun sha256Hex(vararg parts: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach(md::update)
        return hex(md.digest()).lowercase()
    }

    /** HKDF-SHA256 (RFC 5869) con sal vacía y 32 bytes de salida, hecho con `javax.crypto`: una segunda implementación. */
    private fun hkdf32(ikm: ByteArray, info: String): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(ByteArray(32), "HmacSHA256")) // sal vacía = HashLen ceros (§2.2)
            doFinal(ikm)
        }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(prk, "HmacSHA256"))
            update(info.toByteArray(Charsets.US_ASCII))
            update(1)
            doFinal()
        }
    }

    private fun pureSign(sk: Ed25519PrivateKeyParameters, message: ByteArray): ByteArray =
        Ed25519Signer().run { init(true, sk); update(message, 0, message.size); generateSignature() }

    // ---- Vectores oficiales ----

    /** RFC 8032 §7.1, TEST 1. Va por `Ed25519Signer` para fijar la biblioteca que usa `Identity.sign`. */
    @Test fun ed25519Rfc8032() {
        val v = vectors("rfc8032.txt")
        val sk = Ed25519PrivateKeyParameters(v.bytes("ed25519-test1.secret"), 0)
        assertArrayEquals(v.bytes("ed25519-test1.public"), sk.generatePublicKey().encoded)
        val message = v.bytes("ed25519-test1.message")
        assertEquals(0, message.size)
        assertArrayEquals(v.bytes("ed25519-test1.signature"), pureSign(sk, message))
        val verifier = Ed25519Signer().apply { init(false, sk.generatePublicKey()) }
        verifier.update(message, 0, message.size)
        assertTrue(verifier.verifySignature(v.bytes("ed25519-test1.signature")))
    }

    /** RFC 8032 §7.3, TEST abc: Ed25519ph con el contexto vacío. */
    @Test fun ed25519phRfc8032() {
        val v = vectors("rfc8032.txt")
        val sk = Ed25519PrivateKeyParameters(v.bytes("ed25519ph-abc.secret"), 0)
        assertArrayEquals(v.bytes("ed25519ph-abc.public"), sk.generatePublicKey().encoded)
        val message = v.bytes("ed25519ph-abc.message")
        assertEquals("abc", String(message, Charsets.US_ASCII))
        val signer = Ed25519phSigner(ByteArray(0)).apply { init(true, sk) }
        signer.update(message, 0, message.size)
        assertArrayEquals(v.bytes("ed25519ph-abc.signature"), signer.generateSignature())
        val verifier = Ed25519phSigner(ByteArray(0)).apply { init(false, sk.generatePublicKey()) }
        verifier.update(message, 0, message.size)
        assertTrue(verifier.verifySignature(v.bytes("ed25519ph-abc.signature")))
    }

    /** draft-ietf-hpke-pq-04, apéndice A.5 (MLKEM768-X25519, HKDF-SHA256, ChaCha20Poly1305, modo base). */
    @Test fun xWingA5() {
        val v = vectors("hpke-pq-04-A5.txt")
        val h = hpkeSuite()
        val skRm = v.bytes("skRm")
        val pkRm = v.bytes("pkRm")
        val info = v.bytes("info")
        val aad = v.bytes("aad")
        val pt = v.bytes("pt")
        val ct = v.bytes("ct")
        val enc = v.bytes("enc")
        val exporterContext = v.bytes("exporter_context")
        val exported = v.bytes("exported_value")
        val length = v.getValue("L").toInt()
        assertEquals(KEM_SECRET_BYTES, skRm.size)
        assertEquals(KEM_PUBLIC_BYTES, pkRm.size)
        assertEquals(ENC_BYTES, enc.size)

        // De la clave privada de 32 bytes sale la pública del vector (sin pasarle la pública).
        val kpR = h.deserializePrivateKey(skRm, null)
        assertArrayEquals(pkRm, h.serializePublicKey(kpR.public))
        assertArrayEquals(skRm, h.serializePrivateKey(kpR.private))

        // Quien envía, con la aleatoriedad fija del vector: `enc`, `seal` y `export`.
        val sender = h.setupBaseS(h.deserializePublicKey(pkRm), info, v.bytes("ikmE"))
        assertArrayEquals(enc, sender.encapsulation)
        assertArrayEquals(ct, sender.seal(aad, pt))
        assertArrayEquals(exported, sender.export(exporterContext, length))

        // Quien recibe: abre, y exporta lo mismo.
        val receiver = h.setupBaseR(enc, kpR, info)
        assertArrayEquals(pt, receiver.open(aad, ct))
        assertArrayEquals(exported, receiver.export(exporterContext, length))

        // Y un bit cambiado en el cifrado no se abre.
        val tampered = ct.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val receiver2 = h.setupBaseR(enc, kpR, info)
        failsWith<InvalidCipherTextException> { receiver2.open(aad, tampered) }
    }

    // ---- Identidad ----

    // Si cambia, cambian las identidades de todos: no actualizar.
    @Test fun identidadFija() {
        val id = Identity(fixedSeed())
        assertEquals(ED_PUBLIC_BYTES, id.edPublic.size)
        assertEquals(KEM_PUBLIC_BYTES, id.kemPublic.size)
        assertEquals(PINNED_IDENTITY_SHA256, sha256Hex(id.edPublic, id.kemPublic))
        // Siempre las mismas.
        val again = Identity(fixedSeed())
        assertArrayEquals(id.edPublic, again.edPublic)
        assertArrayEquals(id.kemPublic, again.kemPublic)
        // Otra semilla, otras claves.
        val other = Identity(ByteArray(32) { (it + 1).toByte() })
        assertFalse(id.edPublic.contentEquals(other.edPublic))
        assertFalse(id.kemPublic.contentEquals(other.kemPublic))
    }

    /** La derivación es HKDF-SHA256 con sal vacía y esos `info`, comprobada con otra implementación de HKDF. */
    @Test fun derivacionHkdf() {
        val seed = fixedSeed()
        val id = Identity(seed)
        val edSk = hkdf32(seed, "seguridad-identity-ed25519-v1")
        val kemSk = hkdf32(seed, "seguridad-identity-xwing-v1")
        assertFalse(edSk.contentEquals(kemSk))
        assertArrayEquals(Ed25519PrivateKeyParameters(edSk, 0).generatePublicKey().encoded, id.edPublic)
        val h = hpkeSuite()
        assertArrayEquals(h.serializePublicKey(h.deserializePrivateKey(kemSk, null).public), id.kemPublic)
    }

    /** Lo que se cifra para `kemPublic` solo lo abre `kemKeyPair()`, también cuando ya se han puesto a cero los temporales. */
    @Test fun kemIdaYVuelta() {
        val id = Identity(fixedSeed())
        val h = hpkeSuite()
        val info = "prueba".toByteArray(Charsets.US_ASCII)
        val sender = h.setupBaseS(h.deserializePublicKey(id.kemPublic), info)
        val sealed = sender.seal(ByteArray(0), "hola".toByteArray())
        val opened = h.setupBaseR(sender.encapsulation, id.kemKeyPair(), info).open(ByteArray(0), sealed)
        assertEquals("hola", String(opened))
        // Otra identidad no puede abrirlo.
        val stranger = Identity(ByteArray(32) { (it + 1).toByte() })
        failsWith<InvalidCipherTextException> {
            h.setupBaseR(sender.encapsulation, stranger.kemKeyPair(), info).open(ByteArray(0), sealed)
        }
    }

    @Test fun semillaDeLongitudIncorrecta() {
        for (n in listOf(0, 1, 31, 33, 64)) {
            val e = failsWith<CryptoFailure>("semilla de $n bytes") { Identity(ByteArray(n)) }
            assertEquals("La semilla de la identidad no es válida", e.message)
        }
    }

    @Test fun semillaCopiadaYCierre() {
        val seed = fixedSeed()
        val id = Identity(seed)
        val pk = id.edPublic.copyOf()
        val sig = id.sign("etiqueta", "datos".toByteArray())
        // Cambiar la semilla del llamador después no cambia la identidad…
        seed.fill(9)
        assertArrayEquals(sig, id.sign("etiqueta", "datos".toByteArray()))
        // …y cerrarla no toca el array del llamador.
        val seed2 = fixedSeed()
        val id2 = Identity(seed2)
        id2.close()
        assertArrayEquals(fixedSeed(), seed2)
        // Cerrada, ya no firma ni da claves privadas; las públicas siguen ahí. Cerrar otra vez no falla.
        failsWith<IllegalStateException> { id2.sign("etiqueta", ByteArray(0)) }
        failsWith<IllegalStateException> { id2.fileSigner("contexto") }
        failsWith<IllegalStateException> { id2.kemKeyPair() }
        id2.close()
        assertArrayEquals(pk, id2.edPublic)
        assertEquals(KEM_PUBLIC_BYTES, id2.kemPublic.size)
        // `use` la cierra al terminar.
        val id3 = Identity(fixedSeed())
        id3.use { assertTrue(verify(it.edPublic, "a", "b".toByteArray(), it.sign("a", "b".toByteArray()))) }
        failsWith<IllegalStateException> { id3.sign("a", ByteArray(0)) }
    }

    @Test fun semillaNueva() {
        val a = newIdentitySeed()
        val b = newIdentitySeed()
        assertEquals(32, a.size)
        assertEquals(32, b.size)
        assertFalse(a.contentEquals(b))
        // Usa el generador que se le da.
        val fixed = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) = bytes.fill(7)
        }
        assertArrayEquals(ByteArray(32) { 7 }, newIdentitySeed(fixed))
    }

    // ---- Firma ----

    @Test fun firmaIdaYVuelta() {
        val id = Identity(fixedSeed())
        val data = "mensaje".toByteArray()
        val sig = id.sign("seguridad-text-sig-v1", data)
        assertEquals(SIG_BYTES, sig.size)
        assertArrayEquals(sig, id.sign("seguridad-text-sig-v1", data)) // Ed25519 es determinista
        assertTrue(verify(id.edPublic, "seguridad-text-sig-v1", data, sig))
        // Cualquier cambio la invalida: datos, etiqueta, clave, y cada byte de la firma.
        assertFalse(verify(id.edPublic, "seguridad-text-sig-v1", "mensajE".toByteArray(), sig))
        assertFalse(verify(id.edPublic, "seguridad-card-v1", data, sig))
        assertFalse(verify(Identity(ByteArray(32) { (it + 1).toByte() }).edPublic, "seguridad-text-sig-v1", data, sig))
        for (i in sig.indices) {
            val bad = sig.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertFalse("byte $i de la firma", verify(id.edPublic, "seguridad-text-sig-v1", data, bad))
        }
    }

    /** `verify` da `false` (nunca lanza) con lo que no mide lo que debe o no es una clave. */
    @Test fun verificarEntradaMalFormada() {
        val id = Identity(fixedSeed())
        val sig = id.sign("a", "b".toByteArray())
        assertFalse(verify(id.edPublic, "a", "b".toByteArray(), sig.copyOf(63)))
        assertFalse(verify(id.edPublic, "a", "b".toByteArray(), sig + 0))
        assertFalse(verify(id.edPublic, "a", "b".toByteArray(), ByteArray(0)))
        assertFalse(verify(id.edPublic.copyOf(31), "a", "b".toByteArray(), sig))
        assertFalse(verify(id.edPublic + 0, "a", "b".toByteArray(), sig))
        assertFalse(verify(ByteArray(0), "a", "b".toByteArray(), sig))
        assertFalse(verify(ByteArray(32) { 0xFF.toByte() }, "a", "b".toByteArray(), sig)) // no es un punto de la curva
        assertFalse(verify(ByteArray(32), "a", "b".toByteArray(), sig))
    }

    /** En Ed25519 puro se firma `etiqueta ‖ 0x00 ‖ datos`, comprobado con la biblioteca sin pasar por `Identity.sign`. */
    @Test fun firmaPuraSobreEtiquetaMasDatos() {
        val seed = fixedSeed()
        val id = Identity(seed)
        val sk = Ed25519PrivateKeyParameters(hkdf32(seed, "seguridad-identity-ed25519-v1"), 0)
        val data = byteArrayOf(1, 2, 3)
        val expected = pureSign(sk, "seguridad-msg-sig-v1".toByteArray(Charsets.US_ASCII) + 0 + data)
        assertArrayEquals(expected, id.sign("seguridad-msg-sig-v1", data))
    }

    @Test fun etiquetasSeparadas() {
        val id = Identity(fixedSeed())
        val sig = id.sign("a", "bc".toByteArray())
        assertTrue(verify(id.edPublic, "a", "bc".toByteArray(), sig))
        // Sin el 0x00 se podría mover un carácter de los datos a la etiqueta; con él, no.
        assertFalse(verify(id.edPublic, "ab", "c".toByteArray(), sig))
        assertFalse(verify(id.edPublic, "abc", ByteArray(0), sig))
    }

    @Test fun etiquetaConCeroFinal() {
        assertArrayEquals(byteArrayOf(0x61, 0x62, 0x63, 0), labeled("abc"))
        assertArrayEquals("seguridad-card-v1".toByteArray(Charsets.US_ASCII) + 0, labeled("seguridad-card-v1"))
        // Una etiqueta vacía, con un 0x00 dentro o fuera de ASCII rompería la separación: es un error de programación.
        for (bad in listOf("", "a\u0000b", "ñ", "a\n")) {
            failsWith<IllegalArgumentException>("«$bad»") { labeled(bad) }
        }
    }

    @Test fun firmaDeArchivo() {
        val id = Identity(fixedSeed())
        val data = ByteArray(200_000) { (it * 31).toByte() }
        val context = "seguridad-file-sig-v1"
        val sig = id.fileSigner(context).run { update(data, 0, data.size); generateSignature() }
        assertEquals(SIG_BYTES, sig.size)
        fun check(pk: ByteArray, ctx: String, content: ByteArray, s: ByteArray) =
            fileVerifier(pk, ctx).run {
                // En trozos, como se hará con un archivo.
                content.asList().chunked(64 * 1024).forEach { c -> val a = c.toByteArray(); update(a, 0, a.size) }
                verifySignature(s)
            }
        assertTrue(check(id.edPublic, context, data, sig))
        assertFalse("otro contexto", check(id.edPublic, "seguridad-file-msg-sig-v1", data, sig))
        assertFalse("un byte distinto", check(id.edPublic, context, data.copyOf().also { it[100_000] = 0 }, sig))
        assertFalse("otro firmante", check(Identity(ByteArray(32) { 5 }).edPublic, context, data, sig))
        assertFalse("truncado", check(id.edPublic, context, data.copyOf(199_999), sig))
        // Ed25519ph y Ed25519 puro no se confunden: la misma firma no vale en el otro modo.
        assertFalse(verify(id.edPublic, context, data, sig))
        val pure = id.sign(context, data)
        assertFalse(check(id.edPublic, context, data, pure))
    }

    @Test fun verificadorDeArchivoConClaveMalFormada() {
        for (n in listOf(0, 31, 33)) {
            val e = failsWith<CryptoFailure>("clave de $n bytes") { fileVerifier(ByteArray(n), "seguridad-file-sig-v1") }
            assertEquals("La clave pública no es válida", e.message)
        }
        // Del tamaño justo, pero no es un punto de la curva.
        val off = failsWith<CryptoFailure> { fileVerifier(ByteArray(32) { 0xFF.toByte() }, "seguridad-file-sig-v1") }
        assertEquals("La clave pública no es válida", off.message)
    }

    // ---- Huella ----

    @Test fun huella() {
        val id = Identity(fixedSeed())
        val fp = fingerprint(id.edPublic, id.kemPublic)
        assertTrue(fp, Regex("^\\d{5}( \\d{5}){5}$").matches(fp))
        assertEquals(PINNED_FINGERPRINT, fp)
        assertEquals(fp, fingerprint(id.edPublic.copyOf(), id.kemPublic.copyOf()))

        // Cambia con cualquier byte de cualquiera de las dos claves.
        for (i in id.edPublic.indices) {
            val ed = id.edPublic.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNotEquals("byte $i de la clave Ed25519", fp, fingerprint(ed, id.kemPublic))
        }
        for (i in id.kemPublic.indices) {
            val kem = id.kemPublic.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNotEquals("byte $i de la clave X-Wing", fp, fingerprint(id.edPublic, kem))
        }
        // Dos identidades, dos huellas.
        val other = Identity(ByteArray(32) { (it + 1).toByte() })
        assertNotEquals(fp, fingerprint(other.edPublic, other.kemPublic))
    }

    /** La fórmula del diseño, hecha otra vez aparte con `MessageDigest` y `BigInteger`. */
    @Test fun huellaSegunLaFormula() {
        val id = Identity(fixedSeed())
        val digest = MessageDigest.getInstance("SHA-512").digest(
            "seguridad-fingerprint-v1".toByteArray(Charsets.US_ASCII) + 0 + byteArrayOf(1) + id.edPublic + id.kemPublic,
        )
        val expected = (0..5).joinToString(" ") { i ->
            BigInteger(1, digest.copyOfRange(i * 5, i * 5 + 5)).mod(BigInteger.valueOf(100_000)).toString().padStart(5, '0')
        }
        assertEquals(expected, fingerprint(id.edPublic, id.kemPublic))
    }

    @Test fun huellaRechazaTamanosIncorrectos() {
        val id = Identity(fixedSeed())
        // Con tamaños libres se podría mover un byte de una clave a la otra y dar la misma huella.
        for ((ed, kem) in listOf(
            id.edPublic.copyOf(31) to id.kemPublic,
            id.edPublic + id.kemPublic[0] to id.kemPublic.copyOfRange(1, KEM_PUBLIC_BYTES),
            id.edPublic to id.kemPublic + 0,
            id.edPublic to ByteArray(0),
        )) {
            val e = failsWith<CryptoFailure> { fingerprint(ed, kem) }
            assertEquals("La clave pública no es válida", e.message)
        }
    }

    // ---- Cabecera ----

    @Test fun cabecera() {
        assertEquals("5347020101", hex(pkHeader(PkKind.CARD)).lowercase())
        assertEquals(PK_VERSION, 2.toByte())
        assertEquals(SUITE_1, 1.toByte())
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), PkKind.entries.map { it.id })
        // Cada tipo da su cabecera y solo la acepta quien espera ese tipo; lo que sigue a los 5 bytes no cuenta.
        for (kind in PkKind.entries) {
            val h = pkHeader(kind)
            assertEquals(5, h.size)
            assertEquals(kind.id, h[3])
            parsePkHeader(h, kind)
            parsePkHeader(h + ByteArray(100) { 0x55 }, kind)
            for (other in PkKind.entries.filter { it != kind }) {
                assertEquals("Es de otro tipo", failsWith<CryptoFailure> { parsePkHeader(h, other) }.message)
            }
        }
    }

    @Test fun cabeceraRechazada() {
        val good = pkHeader(PkKind.CARD)
        fun incompatible(bytes: ByteArray, what: String) =
            assertEquals(what, "No es un dato compatible", failsWith<CryptoFailure>(what) { parsePkHeader(bytes, PkKind.CARD) }.message)

        // Otra marca (también la de un archivo cualquiera).
        incompatible(good.copyOf().also { it[0] = 'X'.code.toByte() }, "marca 0")
        incompatible(good.copyOf().also { it[1] = 'H'.code.toByte() }, "marca 1")
        incompatible(byteArrayOf(0x50, 0x4B, 3, 4, 1), "zip")
        // La versión 1 (el formato con contraseña de la 1.1) y otras.
        for (v in listOf(0, 1, 3, 0xFF)) incompatible(good.copyOf().also { it[2] = v.toByte() }, "versión $v")
        // La suite 0 (no se usa), la 2 (todavía no existe) y otras.
        for (s in listOf(0, 2, 0xFF)) incompatible(good.copyOf().also { it[4] = s.toByte() }, "suite $s")
        // Un tipo que esta versión no conoce no es «otro tipo» de los suyos.
        for (k in listOf(0, 6, 0x7F, 0xFF)) incompatible(good.copyOf().also { it[3] = k.toByte() }, "tipo $k")
        // Corta o vacía.
        for (n in 0..4) incompatible(good.copyOf(n), "longitud $n")
    }

    private companion object {
        // Valores guardados, calculados una vez con esta implementación (no son vectores oficiales).
        const val PINNED_IDENTITY_SHA256 = "d6a404c76aa976f8f6ef709442116607f37249eaf470d2c8b3103b6081f529d9"
        const val PINNED_FINGERPRINT = "21415 99006 12148 39231 74939 41740"
    }
}
