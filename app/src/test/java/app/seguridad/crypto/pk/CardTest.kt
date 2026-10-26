package app.seguridad.crypto.pk

import app.seguridad.core.base45Decode
import app.seguridad.crypto.CryptoFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Tarjeta de contacto (`SGK1:`): estructura, firma, nombre, las dos formas de texto y el vector guardado.
 *
 * El formato de la tarjeta se lee para siempre. `armada` construye una tarjeta desde cero con el diseño en la mano, sin
 * pasar por `cardBytes`: así los tests de rechazo pueden fabricar tarjetas **bien firmadas** pero mal formadas.
 */
class CardTest {
    private fun fixedIdentity() = Identity(ByteArray(32) { it.toByte() })

    private fun rejects(message: String = "", block: () -> Unit): CryptoFailure =
        assertThrows(message, CryptoFailure::class.java) { block() }

    /** `"SG" 02 01 01 | Ed25519 | X-Wing | longitud | nombre | firma("seguridad-card-v1" ‖ lo anterior)`. */
    private fun armada(
        id: Identity,
        name: ByteArray,
        nameLength: Int = name.size,
        ed: ByteArray = id.edPublic,
        kem: ByteArray = id.kemPublic,
        label: String = "seguridad-card-v1",
        signer: Identity = id,
    ): ByteArray {
        val body = byteArrayOf(0x53, 0x47, 0x02, 0x01, 0x01) + ed + kem + byteArrayOf(nameLength.toByte()) + name
        return body + signer.sign(label, body)
    }

    private fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    // ---- Ida y vuelta ----

    @Test fun idaYVueltaConNombre() {
        val id = fixedIdentity()
        val bytes = id.cardBytes("Ana")
        assertEquals(1318 + 3, bytes.size)
        assertArrayEquals(byteArrayOf(0x53, 0x47, 0x02, 0x01, 0x01), bytes.copyOf(5))
        val card = parseCard(bytes)
        assertEquals("Ana", card.name)
        assertArrayEquals(id.edPublic, card.edPublic)
        assertArrayEquals(id.kemPublic, card.kemPublic)
    }

    @Test fun idaYVueltaConNombreVacio() {
        val id = fixedIdentity()
        val bytes = id.cardBytes("")
        assertEquals(1318, bytes.size)
        assertEquals("", parseCard(bytes).name)
    }

    /** Ed25519 es determinista: la misma identidad y el mismo nombre dan siempre la misma tarjeta. */
    @Test fun lasTarjetasSonDeterministas() {
        assertArrayEquals(fixedIdentity().cardBytes("Ana"), fixedIdentity().cardBytes("Ana"))
        assertFalse(fixedIdentity().cardBytes("Ana").contentEquals(fixedIdentity().cardBytes("Bea")))
    }

    @Test fun laTarjetaCoincideConElDiseno() {
        val id = fixedIdentity()
        val name = "Ana".toByteArray()
        val bytes = id.cardBytes("Ana")
        assertArrayEquals(armada(id, name), bytes)
        // La firma es la de la última parte, sobre "seguridad-card-v1" ‖ 0x00 ‖ todo lo anterior.
        val body = bytes.copyOf(bytes.size - 64)
        val sig = bytes.copyOfRange(bytes.size - 64, bytes.size)
        assertTrue(verify(id.edPublic, "seguridad-card-v1", body, sig))
        assertFalse(verify(id.edPublic, "seguridad-text-sig-v1", body, sig))
        assertFalse(verify(id.edPublic, "seguridad-card-v1", body.copyOf(body.size - 1), sig))
    }

    @Test fun tarjetaEqualsCompara() {
        val id = fixedIdentity()
        val a = parseCard(id.cardBytes("Ana"))
        val b = parseCard(id.cardBytes("Ana"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, parseCard(id.cardBytes("Bea")))
        assertNotEquals(a, parseCard(Identity(ByteArray(32) { (it + 1).toByte() }).cardBytes("Ana")))
    }

    // ---- Nombre ----

    @Test fun elNombreSeGuardaEnNfc() {
        val id = fixedIdentity()
        val decomposed = "Aná" // «Ana» + acento combinante
        assertEquals("Aná", parseCard(id.cardBytes(decomposed)).name)
        assertEquals("Aná", parseCard(id.cardBytes(decomposed)).name)
        // Los mismos bytes que si se hubiera escrito ya compuesto.
        assertArrayEquals(id.cardBytes("Aná"), id.cardBytes(decomposed))
        // El límite de 64 bytes cuenta el nombre ya normalizado: 32 × «á» son 64 bytes compuestos y 96 descompuestos.
        assertEquals("á".repeat(32), parseCard(id.cardBytes("á".repeat(32))).name)
    }

    @Test fun limiteDeNombreAlCrear() {
        val id = fixedIdentity()
        assertEquals("a".repeat(64), parseCard(id.cardBytes("a".repeat(64))).name)
        assertEquals(1318 + 64, id.cardBytes("a".repeat(64)).size)
        rejects("65 bytes") { id.cardBytes("a".repeat(65)) }
        // Cuenta bytes y no caracteres.
        assertEquals("é".repeat(32), parseCard(id.cardBytes("é".repeat(32))).name) // 64 bytes
        rejects("66 bytes") { id.cardBytes("é".repeat(33)) }
        assertEquals("😀".repeat(16), parseCard(id.cardBytes("😀".repeat(16))).name) // 16 × 4 = 64
        rejects("68 bytes") { id.cardBytes("😀".repeat(17)) }
        rejects("normalizado, 66 bytes") { id.cardBytes("á".repeat(33)) }
        assertEquals(64, MAX_NAME_BYTES)
    }

    /** Un sustituto suelto no es texto: se rechaza en vez de cambiarlo por «?». */
    @Test fun nombreConSustitutoSueltoAlCrear() {
        val id = fixedIdentity()
        rejects("sustituto alto") { id.cardBytes("Ana\uD800") }
        rejects("sustituto bajo") { id.cardBytes("\uDC00Ana") }
    }

    @Test fun nombreMuyLargoAlLeer() {
        val id = fixedIdentity()
        assertEquals("a".repeat(64), parseCard(armada(id, ByteArray(64) { 'a'.code.toByte() })).name) // control: 64 vale
        rejects("65 bytes, bien firmada") { parseCard(armada(id, ByteArray(65) { 'a'.code.toByte() })) }
        rejects("255 bytes, bien firmada") { parseCard(armada(id, ByteArray(255) { 'a'.code.toByte() })) }
    }

    @Test fun laLongitudDelNombreTieneQueCuadrar() {
        val id = fixedIdentity()
        val name = "Ana".toByteArray()
        assertEquals("Ana", parseCard(armada(id, name, nameLength = 3)).name) // control
        rejects("declara menos") { parseCard(armada(id, name, nameLength = 2)) }
        rejects("declara más") { parseCard(armada(id, name, nameLength = 4)) }
        rejects("declara 0") { parseCard(armada(id, name, nameLength = 0)) }
        rejects("declara 0xFF") { parseCard(armada(id, name, nameLength = 0xFF)) }
    }

    @Test fun nombreSinNormalizarAlLeer() {
        val id = fixedIdentity()
        assertEquals("Aná", parseCard(armada(id, "Aná".toByteArray())).name) // control: compuesto
        rejects("descompuesto, bien firmada") { parseCard(armada(id, "Aná".toByteArray())) }
        rejects("Å de Angstrom (U+212B), que en NFC es U+00C5") { parseCard(armada(id, "Å".toByteArray())) }
    }

    @Test fun nombreQueNoEsUtf8AlLeer() {
        val id = fixedIdentity()
        val malformed = mapOf(
            "byte de continuación suelto" to byteArrayOf(0x80.toByte()),
            "FF" to byteArrayOf(0xFF.toByte()),
            "secuencia cortada" to byteArrayOf(0xC3.toByte()),
            "continuación errónea" to byteArrayOf(0xC3.toByte(), 0x28),
            "sobrelarga de «/»" to byteArrayOf(0xC0.toByte(), 0xAF.toByte()),
            "sustituto en UTF-8 (CESU-8)" to byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()),
            "mayor que U+10FFFF" to byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
        )
        for ((what, name) in malformed) {
            rejects("$what, bien firmada") { parseCard(armada(id, name)) }
        }
    }

    // ---- Claves y firma ----

    @Test fun claveDeCifradoQueNoSeDecodifica() {
        val id = fixedIdentity()
        val name = "Ana".toByteArray()
        assertEquals("Ana", parseCard(armada(id, name)).name) // control: la armada con su clave vale
        // La parte ML-KEM con coeficientes fuera de rango no pasa la comprobación de módulo de FIPS 203 (§7.2): Bouncy
        // Castle lanza IllegalArgumentException y `parseCard` la convierte en CryptoFailure.
        val badKem = id.kemPublic.also { k -> for (i in 0 until 1184) k[i] = 0xFF.toByte() }
        rejects("ML-KEM fuera de rango, bien firmada") { parseCard(armada(id, name, kem = badKem)) }
        val badStart = id.kemPublic.also { it[0] = 0xFF.toByte(); it[1] = 0xFF.toByte() }
        rejects("dos bytes fuera de rango, bien firmada") { parseCard(armada(id, name, kem = badStart)) }
    }

    /** Los 7 puntos de orden pequeño de Curve25519 (u en little-endian, RFC 7748 §6.1 y §7): el acuerdo da todo ceros. */
    private val smallOrderU = mapOf(
        "u = 0" to "00".repeat(32),
        "u = 1" to "01" + "00".repeat(31),
        "orden 8 (a)" to "e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800",
        "orden 8 (b)" to "5f9c95bca3508c24b1d0b1559c83ef5b04445cc4581c8e86d8224eddd09f1157",
        "u = p - 1" to "ecff" + "ff".repeat(29) + "7f",
        "u = p" to "edff" + "ff".repeat(29) + "7f",
        "u = p + 1" to "eeff" + "ff".repeat(29) + "7f",
    )

    /** Una clave X-Wing con la parte X25519 de orden pequeño, bien firmada por su dueña, no vale: cifrarle fallaría. */
    @Test fun claveX25519DeOrdenPequeno() {
        val id = fixedIdentity()
        val name = "Ana".toByteArray()
        for ((what, u) in smallOrderU) {
            val kem = id.kemPublic.also { Hex.decode(u).copyInto(it, 1184) }
            assertEquals(1216, kem.size)
            rejects("$what, bien firmada") { parseCard(armada(id, name, kem = kem)) }
        }
        // Control: con el resto igual y una X25519 de verdad, vale.
        assertEquals("Ana", parseCard(armada(id, name, kem = id.kemPublic)).name)
    }

    /** Un nombre con control (Cc) o con dirección del texto se rechaza al crear y, bien firmado, al leer. */
    @Test fun caracteresProhibidosEnElNombre() {
        val id = fixedIdentity()
        val forbidden = listOf(
            "U+0000", "\u0000", "LF", "\n", "CR", "\r", "TAB", "\t", "U+001F", "\u001F", "U+007F", "\u007F",
            "U+0080", "\u0080", "U+0085", "\u0085", "U+009F", "\u009F",
            "U+061C", "\u061C", "U+200E", "\u200E", "U+200F", "\u200F",
            "U+202A", "\u202A", "U+202B", "\u202B", "U+202C", "\u202C", "U+202D", "\u202D", "U+202E", "\u202E",
            "U+2066", "\u2066", "U+2067", "\u2067", "U+2068", "\u2068", "U+2069", "\u2069",
        ).chunked(2).map { it[0] to it[1] }
        for ((what, c) in forbidden) {
            rejects("$what al crear, solo") { id.cardBytes(c) }
            rejects("$what al crear, dentro") { id.cardBytes("An${c}a") }
            rejects("$what al leer, solo") { parseCard(armada(id, c.toByteArray())) }
            rejects("$what al leer, dentro") { parseCard(armada(id, "An${c}a".toByteArray())) }
        }
        // Los que no son de control siguen valiendo: espacio, ancho cero unido (emoji), letras con acento, CJK.
        for (ok in listOf("Ana María", "Ana\u00A0M", "\u4F60\u597D", "\uD83D\uDC68\u200D\uD83D\uDC69", "\u05D0\u05D1")) {
            assertEquals(ok, parseCard(id.cardBytes(ok)).name)
            assertEquals(ok, parseCard(armada(id, ok.toByteArray())).name)
        }
    }

    /**
     * Los separadores de línea y de párrafo y los caracteres de formato invisibles (ancho cero, unión de palabras,
     * marca de orden de bytes…) permiten un nombre que no se ve o se ve partido: se rechazan al crear y, bien firmados,
     * al leer. U+200C y U+200D se siguen admitiendo: son necesarios en escrituras como la persa y en los emoji.
     */
    @Test fun caracteresInvisiblesEnElNombre() {
        val id = fixedIdentity()
        val forbidden = listOf(
            0x2028, 0x2029, 0x200B, 0x2060, 0x2061, 0x2062, 0x2063, 0x2064, 0xFEFF, 0x180E,
        )
        for (code in forbidden) {
            val what = "U+%04X".format(code)
            val c = String(Character.toChars(code))
            rejects("$what al crear, solo") { id.cardBytes(c) }
            rejects("$what al crear, dentro") { id.cardBytes("An${c}a") }
            rejects("$what al crear, al final") { id.cardBytes("Ana$c") }
            rejects("$what al leer, solo") { parseCard(armada(id, c.toByteArray())) }
            rejects("$what al leer, dentro") { parseCard(armada(id, "An${c}a".toByteArray())) }
            rejects("$what al leer, al final") { parseCard(armada(id, "Ana$c".toByteArray())) }
        }
        // Los vecinos de esos rangos que no son de formato siguen valiendo, y U+200C y U+200D también.
        for (ok in listOf("\u200C", "\u200D", "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645", "\u2027", "\u202F", "\u205F", "\u2070", "\u180D")) {
            val name = "An${ok}a"
            assertEquals(name, parseCard(id.cardBytes(name)).name)
            assertEquals(name, parseCard(armada(id, name.toByteArray())).name)
        }
    }

    /**
     * La lista fija de puntos de código prohibidos en el nombre (diseño, «Tarjeta de contacto»), escrita aquí otra vez a
     * mano y por separado del código. Es fija a propósito: no depende de la versión de Unicode ni de ICU del móvil.
     */
    private val forbiddenRanges = listOf(
        0x0000..0x001F, 0x007F..0x009F, // Cc
        0x00AD..0x00AD, 0x034F..0x034F, 0x061C..0x061C,
        0x115F..0x1160, 0x3164..0x3164, 0xFFA0..0xFFA0, // rellenos de Hangul
        0x17B4..0x17B5, 0x180E..0x180E,
        0x200B..0x200B, 0x200E..0x200F, 0x2028..0x2029, 0x202A..0x202E, 0x2060..0x206F,
        0xFEFF..0xFEFF, 0xFFF0..0xFFFB, 0xFFFE..0xFFFF,
        0xD800..0xDFFF, // sustitutos sueltos
        0xE000..0xF8FF, 0xF0000..0x10FFFF, // uso privado
        0x1BCA0..0x1BCA3, 0x1D173..0x1D17A, 0xE0000..0xE0FFF,
    )

    private fun inForbiddenRanges(cp: Int) = forbiddenRanges.any { cp in it }

    /** El filtro, punto de código por punto de código y sin muestreo: coincide con la tabla de arriba en los 0x110000. */
    @Test fun laListaFijaCoincideConLaTablaEnTodosLosPuntosDeCodigo() {
        for (cp in 0..0x10FFFF) {
            val s = String(Character.toChars(cp))
            assertEquals("U+%04X".format(cp), inForbiddenRanges(cp), hasForbiddenNameCharacter(s))
        }
        // Admitidos expresamente.
        for (cp in listOf(0x200C, 0x200D, 0xFE00, 0xFE0A, 0xFE0F, 0x180B, 0x180C, 0x180D, 0x180F)) {
            assertFalse("U+%04X".format(cp), hasForbiddenNameCharacter(String(Character.toChars(cp))))
        }
        // Un carácter suplementario normal (emoji) no se confunde con un sustituto suelto; uno suelto, sí.
        assertFalse(hasForbiddenNameCharacter("\uD83D\uDC68"))
        assertTrue(hasForbiddenNameCharacter("a\uD83D"))
        assertTrue(hasForbiddenNameCharacter("\uDC68a"))
        assertTrue(hasForbiddenNameCharacter("\uDC68\uD83D"))
    }

    /** Un caso por rango rechazado (los dos extremos y, en los largos, uno de en medio), al crear y, bien firmado, al leer. */
    @Test fun cadaRangoProhibidoSeRechazaAlCrearYAlLeer() {
        val id = fixedIdentity()
        fun check(cp: Int) {
            val what = "U+%04X".format(cp)
            val c = String(Character.toChars(cp))
            rejects("$what al crear, solo") { id.cardBytes(c) }
            rejects("$what al crear, dentro") { id.cardBytes("An${c}a") }
            rejects("$what al crear, al final") { id.cardBytes("Ana$c") }
            // Un sustituto suelto no es UTF-8: al leer solo puede llegar como CESU-8, que también se rechaza.
            val utf8 = if (cp in 0xD800..0xDFFF) byteArrayOf(0xED.toByte(), (0x80 or ((cp shr 6) and 0x3F)).toByte(), (0x80 or (cp and 0x3F)).toByte())
                else c.toByteArray()
            rejects("$what al leer, solo") { parseCard(armada(id, utf8)) }
            rejects("$what al leer, dentro") { parseCard(armada(id, "An".toByteArray() + utf8 + "a".toByteArray())) }
            rejects("$what al leer, al final") { parseCard(armada(id, "Ana".toByteArray() + utf8)) }
        }
        for (r in forbiddenRanges) {
            check(r.first)
            check(r.last)
            if (r.last - r.first > 2) check((r.first + r.last) / 2)
        }
        // Los que pedía la revisión, por nombre.
        for (cp in listOf(0x00AD, 0x034F, 0x3164, 0x2066, 0xFFF9, 0xE0041, 0xD83D, 0xDC68, 0xFFFE, 0xFFFF, 0x10FFFF, 0xF0000, 0x1D173)) check(cp)
    }

    /** Se admiten ZWJ, ZWNJ, selectores de variación y letras normales: lo necesario para persa, devanagari y emoji. */
    @Test fun caracteresAdmitidosEnElNombre() {
        val id = fixedIdentity()
        val admitted = listOf(
            "familia con ZWJ" to "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", // U+1F468 U+200D U+1F469 U+200D U+1F467
            "corazón con U+FE0F" to "\u2764\uFE0F",
            "persa con ZWNJ" to "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645",
            "devanagari con ZWJ" to "\u0915\u094D\u200D\u0937",
            "selectores mongoles" to "\u1820\u180B\u180C\u180D\u180F",
            "selectores de variación" to "\u2764\uFE00\uFE0E\uFE0F",
            "U+FFFC y U+FFFD" to "a\uFFFCb\uFFFD",
            "emoji suplementario" to "\uD83D\uDD10",
            "CJK" to "\u4F60\u597D",
        )
        for ((what, name) in admitted) {
            assertEquals("$what al crear", name, parseCard(id.cardBytes(name)).name)
            assertEquals("$what al leer", name, parseCard(armada(id, name.toByteArray())).name)
        }
    }

    /** «Zoë» en NFD (e + U+0308) se normaliza a NFC al crear; al leer solo se admite ya normalizada. */
    @Test fun zoeEnNfdSeNormalizaAlCrear() {
        val id = fixedIdentity()
        val nfd = "Zoe\u0308"
        val nfc = "Zo\u00EB"
        assertEquals(nfc, parseCard(id.cardBytes(nfd)).name)
        assertArrayEquals(id.cardBytes(nfc), id.cardBytes(nfd))
        assertEquals(nfc, parseCard(armada(id, nfc.toByteArray())).name)
        rejects("NFD al leer") { parseCard(armada(id, nfd.toByteArray())) }
    }

    /** Las funciones de Base64url son de todos los formatos: mensajes neutros y forma canónica. */
    @Test fun base64UrlDeLosFormatos() {
        val data = ByteArray(10) { (it * 37).toByte() }
        val text = base64UrlEncode(data)
        assertArrayEquals(data, base64UrlDecode(text))
        for (bad in listOf("$text=", "+/+/", "AA A", "A", text.dropLast(1) + "B")) {
            val e = rejects(bad) { base64UrlDecode(bad) }
            assertFalse(e.message!!.contains("tarjeta"))
        }
    }

    @Test fun firmaDeOtraIdentidad() {
        val ana = fixedIdentity()
        val eve = Identity(ByteArray(32) { (it + 1).toByte() })
        val name = "Ana".toByteArray()
        // Eve firma las claves de Ana con la suya: la firma no es de la clave que va en la tarjeta.
        rejects("firmada por otra") { parseCard(armada(ana, name, signer = eve)) }
        // Eve cambia la clave Ed25519 por la suya sin poder firmar las claves de Ana.
        rejects("clave Ed25519 ajena") { parseCard(armada(ana, name, ed = eve.edPublic)) }
        // Eve cambia la clave X-Wing de la tarjeta de Ana por la suya, sin poder firmarla: así leería lo que le envíen a Ana.
        val spliced = ana.cardBytes("Ana").also { eve.kemPublic.copyInto(it, 5 + 32) }
        rejects("clave X-Wing cambiada") { parseCard(spliced) }
        // Y la de Eve con sus propias claves vale, claro.
        assertArrayEquals(eve.edPublic, parseCard(armada(eve, name)).edPublic)
    }

    /** La etiqueta de dominio: una firma de otro formato sobre los mismos bytes no vale como tarjeta. */
    @Test fun firmaConOtraEtiqueta() {
        val id = fixedIdentity()
        val name = "Ana".toByteArray()
        for (label in listOf("seguridad-text-sig-v1", "seguridad-msg-sig-v1", "seguridad-card-v2", "seguridad-card")) {
            rejects(label) { parseCard(armada(id, name, label = label)) }
        }
    }

    /** Una clave Ed25519 de orden pequeño con la firma forjada (R = neutro, S = 0) no vale como tarjeta. */
    @Test fun claveEdDeOrdenPequeno() {
        val id = fixedIdentity()
        val neutral = ByteArray(32).also { it[0] = 1 }
        val forged = neutral + ByteArray(32)
        val body = byteArrayOf(0x53, 0x47, 0x02, 0x01, 0x01) + neutral + id.kemPublic + byteArrayOf(0)
        rejects("clave neutra") { parseCard(body + forged) }
    }

    // ---- Cada byte, cada longitud ----

    @Test fun cambiarCualquierByteLaInvalida() {
        val bytes = fixedIdentity().cardBytes("Ana")
        parseCard(bytes) // control
        for (i in bytes.indices) {
            for (mask in intArrayOf(0x01, 0x80, 0xFF)) {
                val bad = bytes.copyOf().also { it[i] = (it[i].toInt() xor mask).toByte() }
                rejects("byte $i con máscara $mask") { parseCard(bad) }
            }
        }
    }

    @Test fun cambiarCualquierByteInvalidaLaTarjetaSinNombre() {
        val bytes = fixedIdentity().cardBytes("")
        for (i in bytes.indices) {
            val bad = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0xFF).toByte() }
            rejects("byte $i") { parseCard(bad) }
        }
    }

    @Test fun unByteDeMasOdeMenos() {
        for (name in listOf("", "Ana", "a".repeat(64))) {
            val bytes = fixedIdentity().cardBytes(name)
            parseCard(bytes) // control
            rejects("«$name» más un 0x00") { parseCard(bytes + 0) }
            rejects("«$name» más un 0x01") { parseCard(bytes + 1) }
            rejects("«$name» más 64 bytes") { parseCard(bytes + ByteArray(64)) }
            rejects("«$name» menos el último") { parseCard(bytes.copyOf(bytes.size - 1)) }
            rejects("«$name» menos el primero") { parseCard(bytes.copyOfRange(1, bytes.size)) }
            for (n in bytes.indices) rejects("«$name» cortada a $n") { parseCard(bytes.copyOf(n)) }
        }
    }

    /** Quitar un byte del nombre y la longitud a la vez deja una firma que ya no es la del cuerpo. */
    @Test fun acortarElNombreYSuLongitudNoVale() {
        val id = fixedIdentity()
        val bytes = id.cardBytes("Ana")
        val nameLengthAt = 5 + 32 + 1216
        val shorter = bytes.copyOf(nameLengthAt) + byteArrayOf(2) + bytes.copyOfRange(nameLengthAt + 1, nameLengthAt + 3) +
            bytes.copyOfRange(bytes.size - 64, bytes.size)
        rejects("nombre de 2 con firma de 3") { parseCard(shorter) }
    }

    @Test fun tarjetaMayorQueElMaximo() {
        val bytes = fixedIdentity().cardBytes("Ana")
        rejects("más de 4 KiB") { parseCard(bytes + ByteArray(MAX_CARD_BYTES)) }
        rejects("justo 4 KiB") { parseCard(bytes.copyOf(MAX_CARD_BYTES)) }
        rejects("vacía") { parseCard(ByteArray(0)) }
        assertEquals(4096, MAX_CARD_BYTES)
    }

    @Test fun cabecerasDeOtrosTiposYVersiones() {
        val bytes = fixedIdentity().cardBytes("Ana")
        // Un mensaje, una firma… no son una tarjeta: se rechaza por la cabecera, con el mensaje de «otro tipo».
        for (kind in 2..5) {
            val other = bytes.copyOf().also { it[3] = kind.toByte() }
            assertEquals("Es de otro tipo", rejects("tipo $kind") { parseCard(other) }.message)
        }
        for ((at, value) in listOf(0 to 0x00, 1 to 0x00, 2 to 0x01, 2 to 0x03, 3 to 0x00, 3 to 0x06, 4 to 0x00, 4 to 0x02)) {
            val other = bytes.copyOf().also { it[at] = value.toByte() }
            assertEquals("No es un dato compatible", rejects("byte $at = $value") { parseCard(other) }.message)
        }
    }

    // ---- Texto ----

    @Test fun cardTextEsBase64UrlSinRelleno() {
        val bytes = fixedIdentity().cardBytes("Ana")
        val text = cardText(bytes)
        assertEquals("SGK1:", CARD_TEXT_PREFIX)
        assertTrue(text.startsWith("SGK1:"))
        val body = text.removePrefix("SGK1:")
        assertEquals(base64Url(bytes), body)
        assertTrue(Regex("[A-Za-z0-9_-]+").matches(body))
        assertEquals((bytes.size * 4 + 2) / 3, body.length) // sin relleno
        assertTrue(body.startsWith("U0cCAQ")) // 53 47 02 01 01 …
    }

    @Test fun cardQrSoloUsaElAlfabetoAlfanumericoDeQr() {
        for (name in listOf("", "Ana", "Aná", "a".repeat(64))) {
            val bytes = Identity(ByteArray(32) { (it * 7 + name.length).toByte() }).cardBytes(name)
            val text = cardQr(bytes)
            assertTrue(text.startsWith("SGK1:"))
            assertTrue("«$name»", Regex("[0-9A-Z $%*+\\-./:]+").matches(text))
            assertArrayEquals(bytes, base45Decode(text.removePrefix("SGK1:")))
        }
    }

    @Test fun lasDosFormasDanLaMismaTarjeta() {
        val id = fixedIdentity()
        for (name in listOf("", "Ana", "Aná", "a".repeat(64), "😀")) {
            val bytes = id.cardBytes(name)
            val fromText = cardFromAnyText(cardText(bytes))
            val fromQr = cardFromAnyText(cardQr(bytes))
            assertEquals("«$name»", fromText, fromQr)
            assertEquals(parseCard(bytes), fromText)
        }
    }

    /** Los bytes que guarda un contacto (`newContactEntry` los vuelve a leer): los de la tarjeta, en las dos formas de texto. */
    @Test fun losBytesDeUnaTarjetaLeidaDeTextoSonLosCanonicos() {
        val id = fixedIdentity()
        for (name in listOf("", "Ana", "Aná", "a".repeat(64), "😀")) {
            val bytes = id.cardBytes(name)
            assertArrayEquals("«$name» Base64url", bytes, cardBytesFromAnyText(cardText(bytes)))
            assertArrayEquals("«$name» Base45", bytes, cardBytesFromAnyText(cardQr(bytes)))
            assertArrayEquals("«$name» con espacios", bytes, cardBytesFromAnyText("  \n" + cardText(bytes).chunked(60).joinToString("\n") + " \n"))
        }
    }

    @Test fun losBytesDeUnTextoQueNoEsTarjetaSeRechazanComoLaTarjeta() {
        val bytes = fixedIdentity().cardBytes("Ana")
        rejects("sin prefijo") { cardBytesFromAnyText(cardText(bytes).removePrefix("SGK1:")) }
        rejects("un byte cambiado") { cardBytesFromAnyText(cardText(bytes.copyOf().also { it[100] = (it[100] + 1).toByte() })) }
        rejects("vacío") { cardBytesFromAnyText("") }
        rejects("demasiado grande") { cardBytesFromAnyText("SGK1:" + "A".repeat(MAX_CARD_BYTES)) }
    }

    /** Muchas identidades, para tocar los dos finales posibles de Base64url (1 o 2 caracteres) y de Base45 (2 o 3). */
    @Test fun lasDosFormasConMuchasIdentidades() {
        val random = java.util.Random(1216)
        for (n in 0 until 24) {
            val id = Identity(ByteArray(32).also(random::nextBytes))
            val name = "x".repeat(n % 5)
            val bytes = id.cardBytes(name)
            assertEquals(parseCard(bytes), cardFromAnyText(cardText(bytes)))
            assertEquals(parseCard(bytes), cardFromAnyText(cardQr(bytes)))
        }
    }

    @Test fun espaciosYSaltosAlrededor() {
        val bytes = fixedIdentity().cardBytes("Ana")
        val expected = parseCard(bytes)
        for (around in listOf(" ", "\n", "\r\n", "\t", "  \n\r\n  ", " ")) {
            assertEquals(expected, cardFromAnyText(around + cardText(bytes) + around))
            assertEquals(expected, cardFromAnyText(around + cardQr(bytes) + around))
        }
    }

    @Test fun espaciosYSaltosDentroDeBase64Url() {
        val bytes = fixedIdentity().cardBytes("Ana")
        val expected = parseCard(bytes)
        val body = cardText(bytes).removePrefix("SGK1:")
        // Partida en líneas de 76 con CRLF, como la parten algunos programas de correo.
        assertEquals(expected, cardFromAnyText("SGK1:" + body.chunked(76).joinToString("\r\n")))
        // Con sangría y espacios entre trozos.
        assertEquals(expected, cardFromAnyText("  SGK1:" + body.chunked(40).joinToString("\n  ") + "\n"))
        // Un espacio entre cada dos caracteres.
        assertEquals(expected, cardFromAnyText("SGK1:" + body.chunked(2).joinToString(" ")))
    }

    /** En Base45 el espacio es un dígito: un salto de línea dentro lo rompe, y un espacio dentro cambia los datos. */
    @Test fun saltosDentroDeBase45Rechazados() {
        val bytes = fixedIdentity().cardBytes("Ana")
        val body = cardQr(bytes).removePrefix("SGK1:")
        rejects("salto") { cardFromAnyText("SGK1:" + body.substring(0, 300) + "\n" + body.substring(300)) }
        rejects("espacio sobrante") { cardFromAnyText("SGK1:" + body.substring(0, 300) + " " + body.substring(300)) }
    }

    @Test fun base64UrlEstricto() {
        val bytes = fixedIdentity().cardBytes("Ana")
        assertEquals(1, bytes.size % 3) // el final son 2 caracteres, con 4 bits de relleno que tienen que ser 0
        val body = cardText(bytes).removePrefix("SGK1:")
        parseCard(Base64.getUrlDecoder().decode(body)) // control
        // Con relleno.
        rejects("relleno") { cardFromAnyText("SGK1:$body==") }
        rejects("relleno a medias") { cardFromAnyText("SGK1:$body=") }
        // El alfabeto estándar y no el de URL.
        val standard = Base64.getEncoder().withoutPadding().encodeToString(bytes)
        if (standard != body) rejects("alfabeto estándar") { cardFromAnyText("SGK1:$standard") }
        rejects("más") { cardFromAnyText("SGK1:" + body.dropLast(1) + "+") }
        rejects("barra") { cardFromAnyText("SGK1:" + body.dropLast(1) + "/") }
        // Bits de relleno distintos de cero: el decodificador del JDK los acepta y da los mismos bytes; aquí no.
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val last = body.last()
        val altered = body.dropLast(1) + alphabet[alphabet.indexOf(last) xor 1]
        assertArrayEquals(bytes, Base64.getUrlDecoder().decode(altered))
        rejects("relleno distinto de cero") { cardFromAnyText("SGK1:$altered") }
        // Una letra de más o de menos (longitud 1 mod 4).
        rejects("un carácter más") { cardFromAnyText("SGK1:" + body + "A") }
        rejects("un carácter menos") { cardFromAnyText("SGK1:" + body.dropLast(1)) }
        rejects("tres menos") { cardFromAnyText("SGK1:" + body.dropLast(3)) }
    }

    @Test fun textosQueNoSonTarjetas() {
        val bytes = fixedIdentity().cardBytes("Ana")
        val body = cardText(bytes).removePrefix("SGK1:")
        rejects("vacío") { cardFromAnyText("") }
        rejects("solo espacios") { cardFromAnyText("   \n ") }
        rejects("solo el prefijo") { cardFromAnyText("SGK1:") }
        rejects("sin prefijo") { cardFromAnyText(body) }
        rejects("prefijo en minúsculas") { cardFromAnyText("sgk1:$body") }
        rejects("prefijo de mensaje") { cardFromAnyText("SGM1:$body") }
        rejects("prefijo de firma") { cardFromAnyText("SGS1:$body") }
        rejects("texto delante") { cardFromAnyText("Mi tarjeta: SGK1:$body") }
        rejects("texto detrás") { cardFromAnyText("SGK1:$body gracias") }
        rejects("carácter fuera de los dos alfabetos") { cardFromAnyText("SGK1:" + body.dropLast(1) + "é") }
        rejects("otro prefijo con Base45") { cardFromAnyText("SGM1:" + cardQr(bytes).removePrefix("SGK1:")) }
        rejects("basura en Base64url") { cardFromAnyText("SGK1:" + "A".repeat(1000)) }
        rejects("basura en Base45") { cardFromAnyText("SGK1:" + "0".repeat(1000)) }
        rejects("cabecera de otro tipo") {
            cardFromAnyText(cardText(bytes.copyOf().also { it[3] = 2 }))
        }
    }

    /** El límite de 4 KiB cuenta el texto tal cual llega: se rechaza antes de quitar espacios y de decodificar. */
    @Test fun textoDeMasDe4KiBSeRechazaSinDecodificar() {
        val text = cardText(fixedIdentity().cardBytes("Ana"))
        assertTrue(text.length < MAX_CARD_BYTES)
        // Justo en el límite, con espacios alrededor, vale…
        assertEquals("Ana", cardFromAnyText(text + " ".repeat(MAX_CARD_BYTES - text.length)).name)
        // …y un carácter más, no: la misma tarjeta, que es válida, se rechaza por el tamaño del texto.
        assertEquals(MAX_CARD_BYTES + 1, (text + " ".repeat(MAX_CARD_BYTES + 1 - text.length)).length)
        rejects("4 KiB y un carácter") { cardFromAnyText(text + " ".repeat(MAX_CARD_BYTES + 1 - text.length)) }
        rejects("espacios delante") { cardFromAnyText(" ".repeat(MAX_CARD_BYTES) + text) }
        rejects("1 MiB") { cardFromAnyText("SGK1:" + "A".repeat(1 shl 20)) }
        val qr = cardQr(fixedIdentity().cardBytes("Ana"))
        rejects("QR con espacios de más") { cardFromAnyText(qr + " ".repeat(MAX_CARD_BYTES)) }
    }

    // ---- Vector guardado ----

    private fun vectorCardHex(): String {
        val json = requireNotNull(javaClass.getResource("/vectors/seguridad-pk.json")) { "seguridad-pk.json" }.readText()
        return Json.parseToJsonElement(json).jsonObject.getValue("card_hex").jsonPrimitive.content
    }

    /**
     * La tarjeta de la identidad fija (semilla 00 01 … 1F) con el nombre «Ana», guardada en `seguridad-pk.json`. Se
     * generó una vez y se lee para siempre: si este test falla, el formato ha cambiado y no se puede actualizar el
     * vector, hay que arreglar el código.
     */
    @Test fun tarjetaGuardada() {
        val hex = vectorCardHex()
        assertEquals(hex.lowercase(), hex)
        assertEquals(2 * (1318 + 3), hex.length)
        val stored = Hex.decode(hex)
        val id = fixedIdentity()
        val card = parseCard(stored)
        assertEquals("Ana", card.name)
        assertArrayEquals(id.edPublic, card.edPublic)
        assertArrayEquals(id.kemPublic, card.kemPublic)
        assertEquals(card, cardFromAnyText(cardText(stored)))
        assertEquals(card, cardFromAnyText(cardQr(stored)))
        // La firma es determinista: hoy se escribe exactamente lo mismo.
        assertArrayEquals(stored, id.cardBytes("Ana"))
        // Y la huella no depende del nombre.
        val other = parseCard(id.cardBytes("Bea"))
        assertEquals(fingerprint(other.edPublic, other.kemPublic), fingerprint(card.edPublic, card.kemPublic))
    }
}
