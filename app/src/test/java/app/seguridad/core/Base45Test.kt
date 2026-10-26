package app.seguridad.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Random

/** Base45 (RFC 9285): los vectores del §4.3, el alfabeto, las rarezas de los grupos y los límites de valor. */
class Base45Test {
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    private fun rejects(text: String) {
        assertThrows("«$text» debía rechazarse", IllegalArgumentException::class.java) { base45Decode(text) }
    }

    /** RFC 9285 §4.3 (los ejemplos con texto). */
    @Test fun vectoresDelRfc9285() {
        val vectors = listOf(
            "AB" to "BB8",
            "Hello!!" to "%69 VD92EX0",
            "base-45" to "UJCLQE7W581",
            "ietf!" to "QED8WEX0",
        )
        for ((plain, encoded) in vectors) {
            assertEquals(encoded, base45Encode(ascii(plain)))
            assertArrayEquals(ascii(plain), base45Decode(encoded))
        }
    }

    @Test fun vacioEsVacio() {
        assertEquals("", base45Encode(ByteArray(0)))
        assertEquals(0, base45Decode("").size)
    }

    /** Un byte da 2 caracteres y dos bytes dan 3: el tamaño de la salida es el del RFC. */
    @Test fun tamanos() {
        for (n in 0..40) {
            assertEquals("$n bytes", n / 2 * 3 + n % 2 * 2, base45Encode(ByteArray(n)).length)
        }
    }

    @Test fun extremosDeValor() {
        // 0xFFFF es el mayor valor de un grupo de 3, y 0xFF el mayor de uno de 2.
        assertEquals("FGW", base45Encode(byteArrayOf(-1, -1)))
        assertArrayEquals(byteArrayOf(-1, -1), base45Decode("FGW"))
        assertEquals("U5", base45Encode(byteArrayOf(-1)))
        assertArrayEquals(byteArrayOf(-1), base45Decode("U5"))
        assertEquals("000", base45Encode(byteArrayOf(0, 0)))
        assertEquals("00", base45Encode(byteArrayOf(0)))
    }

    /** Todos los pares de bytes y todos los bytes sueltos. */
    @Test fun idaYVueltaExhaustiva() {
        for (v in 0..0xFFFF) {
            val pair = byteArrayOf((v shr 8).toByte(), v.toByte())
            val text = base45Encode(pair)
            assertEquals(3, text.length)
            assertArrayEquals("par $v", pair, base45Decode(text))
        }
        for (v in 0..0xFF) {
            val single = byteArrayOf(v.toByte())
            assertArrayEquals("byte $v", single, base45Decode(base45Encode(single)))
        }
    }

    @Test fun idaYVueltaAleatoria() {
        val random = Random(45)
        for (n in 0..300) {
            val data = ByteArray(n).also(random::nextBytes)
            assertArrayEquals("$n bytes", data, base45Decode(base45Encode(data)))
        }
    }

    /** El texto del QR solo usa el alfabeto alfanumérico de QR (ISO/IEC 18004), también con todos los valores. */
    @Test fun soloAlfabetoDeQr() {
        val all = ByteArray(512) { it.toByte() }
        assertEquals(true, Regex("[0-9A-Z $%*+\\-./:]*").matches(base45Encode(all)))
    }

    @Test fun rechazaValoresFueraDeRango() {
        rejects("GGW") // 65536: un valor más que 0xFFFF
        rejects(":::") // el mayor valor posible de 3 caracteres
        rejects("V5") // 256 en un grupo de 2
        rejects("::") // el mayor valor posible de 2 caracteres
        rejects("FGWGGW") // el primer grupo vale y el segundo no
        rejects("FGW::")
    }

    @Test fun rechazaGruposDeUno() {
        rejects("A")
        rejects("AAAA") // 3 + 1
        rejects("BB8A")
        rejects("BB8BB8BB8B")
    }

    @Test fun rechazaCaracteresFueraDelAlfabeto() {
        rejects("bb8") // las minúsculas no están
        rejects("BB#")
        rejects("BB8\n")
        rejects("BB8\t")
        rejects("BB=")
        rejects("BB_")
        rejects("BBé")
        rejects("BB\u0000")
        rejects("ＢＢ8") // letras de ancho completo
        rejects("BB8 ") // espacio duro
    }

    /** El espacio sí es del alfabeto (valor 36) y no se descarta. */
    @Test fun elEspacioEsUnDigito() {
        assertArrayEquals(ascii("Hello!!"), base45Decode("%69 VD92EX0"))
        rejects("%69VD92EX0") // sin el espacio, 10 caracteres: un grupo de 1
    }
}
