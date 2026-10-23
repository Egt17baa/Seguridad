package app.seguridad.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64

private val FAST = KdfParams(1024, 1, 1)

/** Ciphertexts written by CriptoPanel 1.10.0 (CP1:, .cpenc) and the new Seguridad format (SG1:, .sgenc). */
class DialectTest {
    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResource("criptopanel/$name")) { name }.readBytes()

    private fun secretOf(type: String, value: String): Secret = when (type) {
        "password" -> Secret.Password(value.toCharArray())
        "rawkey" -> Secret.RawKey(requireNotNull(parseRawKey(value)))
        else -> error(type)
    }

    private val fixtures by lazy { Json.parseToJsonElement(resource("texts.json").toString(Charsets.UTF_8)).jsonObject }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun unb64(token: String, prefix: String) = Base64.getUrlDecoder().decode(token.removePrefix(prefix))

    private fun expectMessage(message: String, block: () -> Unit) {
        try {
            block()
            fail("expected CryptoFailure")
        } catch (e: CryptoFailure) {
            assertEquals(message, e.message)
        }
    }

    @Test fun writesSg1() {
        val t = encryptText("hola", "pwd".toCharArray(), kdf = FAST)
        assertTrue(t, t.startsWith("SG1:"))
        val raw = unb64(t, "SG1:")
        assertEquals("SG", String(raw.copyOfRange(0, 2), Charsets.US_ASCII))
        assertEquals("hola", decryptText(t, "pwd".toCharArray()))
    }

    @Test fun readsEveryCriptopanelFixture() {
        val texts = fixtures.getValue("texts").jsonArray
        assertEquals(Aead.entries.size + 2, texts.size)
        val seen = mutableSetOf<String>()
        texts.forEach { el ->
            val o = el.jsonObject
            val token = o.getValue("token").jsonPrimitive.content
            assertTrue(token.startsWith("CP1:"))
            val secret = secretOf(o.getValue("secretType").jsonPrimitive.content, o.getValue("secret").jsonPrimitive.content)
            val alg = o.getValue("algorithm").jsonPrimitive.content
            assertEquals(alg, o.getValue("plaintext").jsonPrimitive.content, decryptText(token, secret))
            assertEquals(Aead.valueOf(alg), inspectText(token)?.first)
            seen += alg
        }
        assertEquals(Aead.entries.map { it.name }.toSet(), seen)
    }

    @Test fun readsCriptopanelFiles() {
        val expected = ByteArray(70_000) { (it % 251).toByte() }
        val files = fixtures.getValue("files").jsonArray
        assertEquals(2, files.size)
        files.forEach { el ->
            val o = el.jsonObject
            val file = o.getValue("file").jsonPrimitive.content
            val secret = secretOf(o.getValue("secretType").jsonPrimitive.content, o.getValue("secret").jsonPrimitive.content)
            val out = ByteArrayOutputStream()
            assertEquals(file, "datos.bin", decryptStream(ByteArrayInputStream(resource(file)), out, secret))
            assertArrayEquals(file, expected, out.toByteArray())
        }
    }

    @Test fun rejectsHybridHeaders() {
        val cp = fixtures.getValue("texts").jsonArray.first().jsonObject.getValue("token").jsonPrimitive.content
        expectMessage("No es un dato cifrado compatible") { decryptText("SG1:" + cp.removePrefix("CP1:"), "x".toCharArray()) }
        val sg = encryptText("hola", "pwd".toCharArray(), kdf = FAST)
        expectMessage("No es un dato cifrado compatible") { decryptText("CP1:" + sg.removePrefix("SG1:"), "pwd".toCharArray()) }
        assertEquals(null, inspectText("CP1:" + sg.removePrefix("SG1:")))
        assertEquals(null, inspectText("SG1:" + cp.removePrefix("CP1:")))
    }

    @Test fun cascadeLabelsDiffer() {
        val k = ByteArray(KEY_BYTES) { it.toByte() }
        assertNotEquals(
            b64(cascadeKey(k, "AES_256_GCM", Dialect.SEGURIDAD)),
            b64(cascadeKey(k, "AES_256_GCM", Dialect.CRIPTOPANEL)),
        )
        val salt = ByteArray(SALT_BYTES)
        assertNotEquals(b64(rawKeyHkdf(k, salt, Dialect.SEGURIDAD)), b64(rawKeyHkdf(k, salt, Dialect.CRIPTOPANEL)))
    }

    @Test fun extractTokenFindsBoth() {
        assertEquals("CP1:abc_-", extractToken("nota\nCP1:abc_-\nGuardado el lunes"))
        assertEquals("SG1:abcd", extractToken("SG1:ab\ncd\n"))
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
