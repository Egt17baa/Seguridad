package app.seguridad.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64

private val FAST = KdfParams(1024, 1, 1)

/**
 * The texts and files of this app: `SG1:` and `.sgenc`, the names of when it was called «Seguridad». Since 1.3.1 it
 * reads nothing else (owner's decision, 1-11-2026): CriptoPanel's texts (`CP1:`) and files (`.cpenc`) get the same
 * error as any data that is not this app's. One real text set and one real file written by CriptoPanel 1.10.0 stay in
 * `criptopanel/` to check that.
 */
class DialectTest {
    private fun criptopanel(name: String): ByteArray =
        requireNotNull(javaClass.classLoader!!.getResource("criptopanel/$name")) { name }.readBytes()

    private fun secretOf(type: String, value: String): Secret = when (type) {
        "password" -> Secret.Password(value.toCharArray())
        "rawkey" -> Secret.RawKey(requireNotNull(parseRawKey(value)))
        else -> error(type)
    }

    private fun unb64(token: String, prefix: String) = Base64.getUrlDecoder().decode(token.removePrefix(prefix))

    /** The message of the [CryptoFailure] that [block] throws; fails if it throws nothing. */
    private fun failureOf(block: () -> Unit): String? {
        try {
            block()
        } catch (e: CryptoFailure) {
            return e.message
        }
        fail("expected CryptoFailure")
        return null
    }

    /** The random bytes 0, 1, 2… from [start]: what the vectors below were written with. */
    private class Counting(start: Int) : SecureRandom() {
        private var n = start
        override fun nextBytes(bytes: ByteArray) {
            for (i in bytes.indices) bytes[i] = (n++ and 0xff).toByte()
        }
    }

    @Test fun writesSg1() {
        val t = encryptText("hola", "pwd".toCharArray(), kdf = FAST)
        assertTrue(t, t.startsWith("SG1:"))
        val raw = unb64(t, "SG1:")
        assertEquals("SG", String(raw.copyOfRange(0, 2), Charsets.US_ASCII))
        assertEquals("hola", decryptText(t, "pwd".toCharArray()))
    }

    /**
     * Written by the code of Clavero 1.3.0 (b6ea21b, `encryptText` and `encryptStream` with [Counting] as the random
     * source): a text with a password (Argon2id, AES-256-GCM), a text with a raw key (HKDF «seguridad-raw-key», the
     * cascade with its «seguridad-cascade-…» keys), and a `.sgenc` of each kind (ChaCha20-Poly1305 and Twofish-256-GCM).
     * They still open, and the same inputs still give the same bytes: the format did not change when CriptoPanel's
     * was taken out.
     */
    @Test fun `SG1 texts and sgenc files written by 1_3_0 still open and are still written byte for byte`() {
        val plain = "Texto de Clavero 1.3.0: ñ, € y 🔐\nsegunda línea"
        val pw = "contraseña de prueba"
        val key = ByteArray(KEY_BYTES) { it.toByte() }
        val t1 = "SG1:U0cBAQEBAAAEAAEBEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqK_qmjh65h-WTEM00aWiqe-9m-PXydQpOv71GhobYWRiWzyze8aXraqbT" +
            "73icGvfiR8zB6oYTLQ1ZCC3NALJN8mKNbGTmQw"
        val t2 = "SG1:U0cBAQYDAAAAAAAAQEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW7YCI4Uxr6Ou0DjNjx-OsbGAPmvaT_Wy9nUtV2U8DpwkFYq71WyqQJq9" +
            "JLvviXDTs34ZEeRRIVNpqNgn3OINJjXF_UQTAHfw0J96BaHs-kofHmCVBG2pDj_R-M570JgbZ2q1Aseu"
        assertEquals(plain, decryptText(t1, pw.toCharArray()))
        assertEquals(Aead.AES_256_GCM to false, inspectText(t1))
        assertEquals(plain, decryptText(t2, Secret.RawKey(key.copyOf())))
        assertEquals(Aead.CASCADE to true, inspectText(t2))
        assertEquals(t1, encryptText(plain, Secret.Password(pw.toCharArray()), Aead.AES_256_GCM, FAST, Counting(0x10)))
        assertEquals(t2, encryptText(plain, Secret.RawKey(key.copyOf()), Aead.CASCADE, KdfParams(), Counting(0x40)))

        val data = ByteArray(100) { (it * 7 % 251).toByte() }
        val b64 = Base64.getDecoder()
        val f1 = b64.decode(
            "U0cBAgIBAAAEAAEBcHFyc3R1dnd4eXp7fH1+f4CBgoOEhYYAHJHId3PcDvB9jauZDbUnbiOTO9FtM/R8uSphjHCjuospYjRDLbOIQkpppsxQCWxlPpCc" +
                "Mg14nc0RC6mLck8N2Fjxht8inWoTYtStWdfF042E+JHF66Gf109uBzd5yZ2ko7Jc/AvbswrMm+yj1Rwloa0or0Qn/fpLazSDNpoDAidRAebOGURcjy6j" +
                "S9F4gCo+5w==",
        )
        val f2 = b64.decode(
            "U0cBAgMDAAAAAAAAoKGio6SlpqeoqaqrrK2ur7CxsrO0tbYAHHb1p2IH4WMZnl26447WDWUUNR4T8/86rdZcD3jDE+xiAj+chlybxB8VbD5yO2XSJkox" +
                "o2/XU/FkfxYP/echfIFfYSAXMfZNK8ZMeMZQ9v1Y9J6waVBmfy8aOswsCI3ZqaOLRBPh5pH700/TyHOECUV7yzciOM1Zm2U1KzXDFIygvJHUKtAfO4II" +
                "JHHCLUazDg==",
        )
        for ((file, secret) in listOf<Pair<ByteArray, () -> Secret>>(
            f1 to { Secret.Password(pw.toCharArray()) },
            f2 to { Secret.RawKey(key.copyOf()) },
        )) {
            val out = ByteArrayOutputStream()
            assertEquals("datos ñ.txt", decryptStream(ByteArrayInputStream(file), out, secret()))
            assertArrayEquals(data, out.toByteArray())
        }
        fun written(secret: Secret, alg: Aead, start: Int) = ByteArrayOutputStream().also {
            encryptStream(ByteArrayInputStream(data), it, "datos ñ.txt", secret, alg, FAST, Counting(start))
        }.toByteArray()
        assertArrayEquals(f1, written(Secret.Password(pw.toCharArray()), Aead.CHACHA20_POLY1305, 0x70))
        assertArrayEquals(f2, written(Secret.RawKey(key.copyOf()), Aead.TWOFISH_256_GCM, 0xA0))
    }

    @Test fun `CriptoPanel data is refused`() {
        // A text: every CP1: of criptopanel/texts.json (each algorithm, with a password and with a raw key) gets, even with
        // its right secret, what a text that is not this app's gets; nothing finds it in a text file either.
        val notOurs = failureOf { decryptText("Hola, esto no es un texto de Clavero", "x".toCharArray()) }
        assertEquals("El texto cifrado debe empezar por SG1:", notOurs)
        val texts = Json.parseToJsonElement(criptopanel("texts.json").toString(Charsets.UTF_8)).jsonObject.getValue("texts").jsonArray
        assertEquals(Aead.entries.size + 2, texts.size)
        texts.forEach { el ->
            val o = el.jsonObject
            val token = o.getValue("token").jsonPrimitive.content
            assertTrue(token.startsWith("CP1:"))
            val secret = secretOf(o.getValue("secretType").jsonPrimitive.content, o.getValue("secret").jsonPrimitive.content)
            assertEquals(token, notOurs, failureOf { decryptText(token, secret) })
            assertNull(token, inspectText(token))
            assertNull(token, extractToken("nota\n$token\nGuardado el lunes"))
            // Its header behind this app's prefix: what any bytes that are not this app's header get.
            val behindSg1 = "SG1:" + token.removePrefix("CP1:")
            val noise = "SG1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(unb64(token, "CP1:").size) { (it * 31 + 7).toByte() })
            assertEquals(NOT_COMPATIBLE, failureOf { decryptText(noise, "x".toCharArray()) })
            assertEquals(token, NOT_COMPATIBLE, failureOf { decryptText(behindSg1, secret) })
            assertNull(inspectText(behindSg1))
        }

        // A file: criptopanel/ref.cpenc (password "pw") gets what a file that is not this app's gets, and nothing is written.
        val ref = criptopanel("ref.cpenc")
        val out = ByteArrayOutputStream()
        val cp = failureOf { decryptStream(ByteArrayInputStream(ref), out, "pw".toCharArray()) }
        assertEquals(0, out.size())
        val otherOut = ByteArrayOutputStream()
        val other = failureOf { decryptStream(ByteArrayInputStream(ByteArray(ref.size) { (it * 31 + 7).toByte() }), otherOut, "pw".toCharArray()) }
        assertEquals(0, otherOut.size())
        assertEquals("No es un dato cifrado compatible", other)
        assertEquals(other, cp)
    }

    /**
     * The key id 2 (a raw key used as it is, without HKDF) only ever came in CriptoPanel 1.9.0's CP1 texts and files;
     * this app always wrote 3 (`newHeader`). Since 1.3.1 a header with it is refused, also behind this app's magic.
     */
    @Test fun `kdf 2, a raw key used without HKDF, is refused`() {
        val key = parseRawKey(newRawKeyHex())!!
        val header = Header(Header.KIND_TEXT, Aead.SERPENT_256_GCM, KdfParams(), 2, ByteArray(SALT_BYTES), randomBytes(NONCE_BYTES), Dialect.SEGURIDAD)
        val aad = header.bytes()
        val token = TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(aad + aead(Aead.SERPENT_256_GCM, true, key, header.nonce, aad, "zoo vote".toByteArray()))
        assertEquals("Derivación de clave desconocida", failureOf { decryptText(token, Secret.RawKey(key)) })
        assertNull(inspectText(token))
    }

    @Test fun extractTokenFindsSg1() {
        assertEquals("SG1:abcd", extractToken("SG1:ab\ncd\n"))
        assertEquals("SG1:abc_-", extractToken("nota\nSG1:abc_-\nGuardado el lunes"))
    }

    @Test fun sgencRoundTripAllAlgorithms() {
        val data = ByteArray(CHUNK_BYTES + 17) { (it * 13).toByte() }
        val key = parseRawKey(newRawKeyHex())!!
        Aead.entries.forEach { alg ->
            listOf<() -> Secret>({ Secret.Password("pwd".toCharArray()) }, { Secret.RawKey(key.copyOf()) }).forEach { secret ->
                val enc = ByteArrayOutputStream().also { encryptStream(ByteArrayInputStream(data), it, "datos.bin", secret(), alg, FAST) }.toByteArray()
                assertEquals("SG", String(enc.copyOfRange(0, 2), Charsets.US_ASCII))
                val header = Header.parse(ByteBuffer.wrap(enc), Header.KIND_FILE)
                assertEquals(Dialect.SEGURIDAD, header.dialect)
                val dec = ByteArrayOutputStream()
                assertEquals("datos.bin", decryptStream(ByteArrayInputStream(enc), dec, secret()))
                assertArrayEquals(alg.name, data, dec.toByteArray())
            }
        }
    }
}
