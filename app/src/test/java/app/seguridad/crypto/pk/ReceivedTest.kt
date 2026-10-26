package app.seguridad.crypto.pk

import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * «Abrir lo recibido»: `detectReceived` decide por el **último** prefijo (`SGS1:`, `SGM1:` o `SGK1:`) que haya en el
 * texto, no lanza nunca y no valida lo que reconoce más que lo imprescindible (una tarjeta se lee para dar `CardText`).
 */
class ReceivedTest {
    private fun fixedIdentity() = Identity(ByteArray(32) { it.toByte() })

    private fun anaIdentity() = Identity(ByteArray(32) { (0xA0 + it).toByte() })

    private fun cardOf(id: Identity, name: String = "Contacto") = parseCard(id.cardBytes(name))

    private fun cardTextOf(id: Identity, name: String) = cardText(id.cardBytes(name))

    private fun messageOf(text: String = "Hola Ana") = encryptMessage(text, cardOf(anaIdentity(), "Ana"), fixedIdentity())

    @Test fun unaTarjetaEsCardText() {
        val me = fixedIdentity()
        val received = detectReceived(cardTextOf(me, "Beto"))
        assertTrue(received is Received.CardText)
        assertEquals(cardOf(me, "Beto"), (received as Received.CardText).card)
    }

    @Test fun unaTarjetaDeQrEsCardText() {
        val me = fixedIdentity()
        val received = detectReceived(cardQr(me.cardBytes("Beto")))
        assertEquals(Received.CardText(cardOf(me, "Beto")), received)
    }

    @Test fun unaTarjetaPegadaDelChatEsCardText() {
        val me = fixedIdentity()
        val text = cardTextOf(me, "Beto")
        val expected = Received.CardText(cardOf(me, "Beto"))
        assertEquals(expected, detectReceived("  \n$text\n\n"))
        assertEquals(expected, detectReceived("Mi tarjeta:\n$text"))
        assertEquals(expected, detectReceived("$text\nGracias"))
        assertEquals(expected, detectReceived("$text\nGracias\nde nada, Ana."))
        assertEquals(expected, detectReceived("$text Gracias"))
        // Partida por el chat en varias líneas y con espacios.
        val wrapped = text.chunked(60).joinToString("\n")
        assertEquals(expected, detectReceived(wrapped))
        assertEquals(expected, detectReceived(wrapped + "\nGracias"))
        assertEquals(expected, detectReceived(text.chunked(77).joinToString(" ")))
    }

    @Test fun unMensajeEsMessage() {
        val token = messageOf()
        val received = detectReceived(token)
        assertTrue(received is Received.Message)
        val opened = decryptMessage((received as Received.Message).token, anaIdentity())
        assertEquals("Hola Ana", opened.text)
        // Pegado del chat, partido, con texto antes y después: el token se abre igual.
        val wrapped = "Mira:\n" + token.chunked(70).joinToString("\n") + "\nGracias"
        val r2 = detectReceived(wrapped)
        assertTrue(r2 is Received.Message)
        assertEquals("Hola Ana", decryptMessage((r2 as Received.Message).token, anaIdentity()).text)
    }

    @Test fun unTextoFirmadoEsSigned() {
        val block = signText("Mañana a las 10", fixedIdentity())
        val received = detectReceived(block)
        assertTrue(received is Received.Signed)
        assertEquals("Mañana a las 10", verifySignedText((received as Received.Signed).block).text)
        val wrapped = "  $block\r\n"
        assertEquals("Mañana a las 10", verifySignedText((detectReceived(wrapped) as Received.Signed).block).text)
    }

    @Test fun unTextoFirmadoQueContieneUnMensajeOUnaTarjetaEnSuCuerpoEsSigned() {
        val me = fixedIdentity()
        val message = messageOf()
        val card = cardTextOf(me, "Beto")
        val inBody = signText("Te paso esto:\n$message\ny esto:\n$card\nun saludo", me)
        val received = detectReceived(inBody)
        assertTrue(received is Received.Signed)
        val opened = verifySignedText((received as Received.Signed).block)
        assertTrue(opened.text.contains("SGM1:") && opened.text.contains("SGK1:"))
        // También con el prefijo suelto.
        val loose = signText("el prefijo SGM1: se usa para los mensajes", me)
        assertTrue(detectReceived(loose) is Received.Signed)
        val looseCard = signText("el prefijo SGK1: es de las tarjetas", me)
        assertTrue(detectReceived(looseCard) is Received.Signed)
    }

    @Test fun gananLosUltimos() {
        val me = fixedIdentity()
        val message = messageOf()
        val card = cardTextOf(me, "Beto")
        val signed = signText("hola", me)
        assertTrue(detectReceived("$signed\n$message") is Received.Message)
        assertTrue(detectReceived("$signed\n$card") is Received.CardText)
        assertTrue(detectReceived("$message\n$card") is Received.CardText)
        assertTrue(detectReceived("$card\n$message") is Received.Message)
        assertTrue(detectReceived("$message\n$signed") is Received.Signed)
        assertTrue(detectReceived("$card\n$signed") is Received.Signed)
        // El mensaje que se entrega empieza en el último SGM1:, no en uno anterior.
        val second = messageOf("segundo")
        val r = detectReceived("$message\n$second") as Received.Message
        assertEquals("segundo", decryptMessage(r.token, anaIdentity()).text)
    }

    @Test fun unTextoCualquieraEsUnknown() {
        assertEquals(Received.Unknown, detectReceived(""))
        assertEquals(Received.Unknown, detectReceived("   \n\r\n "))
        assertEquals(Received.Unknown, detectReceived("Hola, ¿cómo estás?"))
        assertEquals(Received.Unknown, detectReceived("SG1: algo"))
        assertEquals(Received.Unknown, detectReceived("sgs1:abc sgm1:abc sgk1:abc"))
        assertEquals(Received.Unknown, detectReceived("SGS1 SGM1 SGK1"))
        assertEquals(Received.Unknown, detectReceived("🔐".repeat(1000)))
    }

    @Test fun unaTarjetaRotaEsUnknownYNoLanza() {
        val text = cardTextOf(fixedIdentity(), "Beto")
        assertEquals(Received.Unknown, detectReceived("SGK1:"))
        assertEquals(Received.Unknown, detectReceived("SGK1:AAAA"))
        assertEquals(Received.Unknown, detectReceived(text.dropLast(3)))
        val flipped = text.substring(0, 300) + (if (text[300] == 'A') 'B' else 'A') + text.substring(301)
        assertEquals(Received.Unknown, detectReceived(flipped))
        assertEquals(Received.Unknown, detectReceived("SGK1:" + "\u0000\uD800 ñ".repeat(100)))
    }

    @Test fun demasiadoLargoEsUnknown() {
        val message = messageOf()
        val padding = "x".repeat(MAX_TEXT_FILE_BYTES - message.length - 1)
        // En el límite se reconoce (el mensaje va detrás de un relleno de la longitud justa) y un carácter más ya no.
        assertTrue(detectReceived("$padding\n$message") is Received.Message)
        assertEquals(Received.Unknown, detectReceived("$padding\n$message" + "x"))
        assertEquals(Received.Unknown, detectReceived("SGS1:" + "A".repeat(MAX_TEXT_FILE_BYTES)))
        assertEquals(Received.Unknown, detectReceived("x".repeat(MAX_TEXT_FILE_BYTES + 1)))
    }

    @Test fun noLanzaConEntradasRaras() {
        val random = Random(42)
        val pieces = listOf("SGS1:", "SGM1:", "SGK1:", "A", "AAAA", "-_", "\n", "\r\n", " ", "é", "\uD800", "\u0000", "=", "+", "Gracias", ".")
        repeat(2000) {
            val text = buildString { repeat(random.nextInt(12)) { append(pieces[random.nextInt(pieces.size)]) } }
            detectReceived(text) // no lanza, sea lo que sea
        }
        repeat(200) {
            val text = buildString { repeat(1 + random.nextInt(4)) { append(pieces[random.nextInt(3)]); append("A".repeat(random.nextInt(3000))) } }
            detectReceived(text)
        }
    }
}
