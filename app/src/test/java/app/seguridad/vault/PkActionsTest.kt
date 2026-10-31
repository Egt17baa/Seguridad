package app.seguridad.vault

import app.seguridad.crypto.Aead
import app.seguridad.crypto.CHUNK_BYTES
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.STREAM_PREFIX_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.encryptStream
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.pk.Identity
import app.seguridad.crypto.pk.NOT_FOR_ME
import app.seguridad.crypto.pk.cardBytes
import app.seguridad.crypto.pk.cardBytesFromAnyText
import app.seguridad.crypto.pk.hpkeSuite
import app.seguridad.crypto.pk.cardText
import app.seguridad.crypto.pk.decryptFileFor
import app.seguridad.crypto.pk.decryptMessage
import app.seguridad.crypto.pk.encryptFileFor
import app.seguridad.crypto.pk.encryptMessage
import app.seguridad.crypto.pk.parseCard
import app.seguridad.crypto.pk.signFile
import app.seguridad.crypto.pk.signText
import org.junit.After
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
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.CancellationException

/**
 * La lógica de «Cifrar para…», «Firmar» y «Abrir lo recibido» que no es de pantalla: el texto del remitente, el saneado
 * del nombre de un archivo recibido, los errores en español, el plan según `detectReceived`, y el descifrado de un archivo
 * a una carpeta temporal que se vacía siempre (con un archivo manipulado no queda nada ni se informa de ningún remitente).
 */
class PkActionsTest {
    private val now = 1_790_000_000_000L
    private val tempDirs = ArrayList<File>()

    @After fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("pkactions").toFile().also { tempDirs.add(it) }

    /** Mi bóveda: la identidad y, opcionalmente, contactos. */
    private val meEntry = newIdentityEntry(1)
    private fun me(): Identity = meEntry.openIdentity()
    private fun ana() = Identity(ByteArray(32) { (0xA0 + it).toByte() })
    private fun stranger() = Identity(ByteArray(32) { (0x60 + it).toByte() })

    private fun contactOf(id: Identity, cardName: String, title: String = cardName, verified: Boolean = false): VaultEntry {
        val bytes = id.cardBytes(cardName)
        return newContactEntry(parseCard(bytes), bytes, now, viaQr = verified).copy(title = title)
    }

    private fun myCard() = me().use { parseCard(it.cardBytes("Yo")) }

    private val anaContact = contactOf(ana(), "Ana", verified = true)
    private val entries = listOf(meEntry, anaContact)

    private fun data(n: Int) = ByteArray(n) { (it * 7 + 3).toByte() }

    /** Un archivo de Ana para mí, firmado o no. */
    private fun fileFromAna(plain: ByteArray, name: String = "doc.pdf", signed: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        ana().use { encryptFileFor(ByteArrayInputStream(plain), out, name, myCard(), if (signed) it else null) }
        return out.toByteArray()
    }

    // ---- El remitente ----

    @Test fun elRemitenteSeDiceConLosTextosDelDiseno() {
        assertEquals(SenderLine("Firmado por ti", TrustLevel.GOOD), senderLine(Sender.Me, addressedToMe = true))
        assertEquals(
            SenderLine("Verificado · De «Ana»", TrustLevel.GOOD),
            senderLine(Sender.Contact(contactOf(ana(), "Ana", verified = true)), addressedToMe = true),
        )
        assertEquals(
            SenderLine("Sin verificar · De «Ana»: compara la huella", TrustLevel.CAUTION),
            senderLine(Sender.Contact(contactOf(ana(), "Ana", verified = false)), addressedToMe = true),
        )
        assertEquals(
            SenderLine("Firmado por una clave que no está en tus contactos", TrustLevel.WARNING),
            senderLine(Sender.UnknownKey, addressedToMe = true),
        )
        assertEquals(SenderLine("Sin firmar: cualquiera pudo enviarlo", TrustLevel.WARNING), senderLine(Sender.Unsigned, addressedToMe = true))
    }

    /** T13-2: una firma pública (SGS1:, .sgsig) no nombra destinatario: nunca «De …», y siempre la nota de reenvío. */
    @Test fun unaFirmaDeUnContactoVerificadoDiceFirmadoPorYNuncaDe() {
        val line = senderLine(Sender.Contact(contactOf(ana(), "Ana", verified = true)), addressedToMe = false)
        assertEquals(SenderLine("Verificado · Firmado por «Ana»", TrustLevel.GOOD, SIGNATURE_FORWARD_NOTE), line)
        assertEquals("Una firma dice quién lo firmó, no a quién iba dirigido.", line.note)
        assertFalse(line.text.startsWith("De "))
    }

    @Test fun unaFirmaDeUnContactoSinVerificarDiceFirmadoPorSinVerificar() {
        val line = senderLine(Sender.Contact(contactOf(ana(), "Ana", verified = false)), addressedToMe = false)
        assertEquals(SenderLine("Sin verificar · Firmado por «Ana»: compara la huella", TrustLevel.CAUTION, SIGNATURE_FORWARD_NOTE), line)
        assertFalse(line.text.startsWith("De "))
    }

    @Test fun unaFirmaMiaUnaClaveDesconocidaYSinFirmarNoCambian() {
        assertEquals(SenderLine("Firmado por ti", TrustLevel.GOOD), senderLine(Sender.Me, addressedToMe = false))
        assertEquals(
            SenderLine("Firmado por una clave que no está en tus contactos", TrustLevel.WARNING),
            senderLine(Sender.UnknownKey, addressedToMe = false),
        )
        assertEquals(SenderLine("Sin firmar: cualquiera pudo enviarlo", TrustLevel.WARNING), senderLine(Sender.Unsigned, addressedToMe = false))
    }

    @Test fun losMensajesYArchivosCifradosSiguenConDeYSinNota() {
        val line = senderLine(Sender.Contact(contactOf(ana(), "Ana", verified = true)), addressedToMe = true)
        assertEquals("Verificado · De «Ana»", line.text)
        assertNull(line.note)
    }

    @Test fun elNombreEsElGuardadoYNuncaElDeLaTarjeta() {
        val entry = contactOf(ana(), cardName = "Ana Falsa", title = "Ana", verified = true)
        for (addressed in listOf(true, false)) {
            val line = senderLine(Sender.Contact(entry), addressedToMe = addressed)
            assertTrue(line.text.contains("Ana"))
            assertFalse(line.text.contains("Falsa"))
            // Un mensaje de un extraño no pone ningún nombre.
            assertFalse(senderLine(Sender.UnknownKey, addressedToMe = addressed).text.contains("Ana"))
        }
    }

    // ---- CADENAS-4 y CLAVE-PUBLICA-1: el estado va delante y el nombre guardado entre «» ----

    @Test fun elEstadoVaDelanteYElNombreGuardadoEntreComillas() {
        // Un contacto ya guardado (de antes de la 1.3.0, o de una copia) cuyo nombre imita la marca: la línea empieza
        // por el estado real y el nombre va entero dentro de «», detrás.
        for (fake in listOf("Ana (verificado)", "Ana ✓ verificado con QR", "ti")) {
            for (addressed in listOf(true, false)) {
                val unverified = senderLine(Sender.Contact(contactOf(stranger(), "x", title = fake)), addressedToMe = addressed)
                assertTrue(unverified.text, unverified.text.startsWith("Sin verificar · "))
                assertTrue(unverified.text, unverified.text.contains("«$fake»"))
                assertEquals(TrustLevel.CAUTION, unverified.level)
                val verified = senderLine(Sender.Contact(contactOf(stranger(), "x", title = fake, verified = true)), addressedToMe = addressed)
                assertTrue(verified.text, verified.text.startsWith("Verificado · "))
                assertTrue(verified.text.contains("«$fake»"))
            }
        }
        // «Firmado por ti» (mi propia firma) ya no se puede imitar con un contacto llamado «ti».
        val ti = senderLine(Sender.Contact(contactOf(stranger(), "x", title = "ti")), addressedToMe = false)
        assertEquals("Sin verificar · Firmado por «ti»: compara la huella", ti.text)
        assertNotEquals(senderLine(Sender.Me, addressedToMe = false).text, ti.text)
    }

    @Test fun losTextosDelRemitenteConElNombreDeEjemplo() {
        assertEquals("Verificado · De «x»", senderContactText(verified = true, addressedToMe = true, title = "x"))
        assertEquals("Verificado · Firmado por «x»", senderContactText(verified = true, addressedToMe = false, title = "x"))
        assertEquals("Sin verificar · De «x»: compara la huella", senderContactText(verified = false, addressedToMe = true, title = "x"))
        assertEquals(
            "Sin verificar · Firmado por «x»: compara la huella",
            senderContactText(verified = false, addressedToMe = false, title = "x"),
        )
    }

    // ---- CADENAS-5: «La firma es válida» en verde solo con un remitente de confianza ----

    @Test fun laFirmaDeUnArchivoSoloEsVerdeConMiFirmaOUnContactoVerificado() {
        assertEquals(VerifyVerdict(FILE_SIGNATURE_VALID, TrustLevel.GOOD), fileVerifyVerdict(Sender.Me))
        assertEquals(VerifyVerdict(FILE_SIGNATURE_VALID, TrustLevel.GOOD), fileVerifyVerdict(Sender.Contact(anaContact)))
        assertEquals("La firma es válida: el archivo no ha cambiado.", FILE_SIGNATURE_VALID)
    }

    @Test fun conUnaClaveDesconocidaLaFirmaNoSeDiceValidaNiEnVerde() {
        val v = fileVerifyVerdict(Sender.UnknownKey)
        assertEquals(TrustLevel.WARNING, v.level)
        assertFalse(v.text.startsWith("La firma es válida"))
        assertEquals(
            "El archivo no ha cambiado desde que se firmó, pero la clave que lo firmó no está en tus contactos: no se sabe quién es.",
            v.text,
        )
        // Sin firma no puede llegar aquí (un .sgsig siempre lleva clave), pero nunca sería verde.
        assertEquals(TrustLevel.WARNING, fileVerifyVerdict(Sender.Unsigned).level)
    }

    @Test fun conUnContactoSinVerificarDiceQuienLoFirmoYPideLaHuella() {
        val mallory = contactOf(stranger(), "x", title = "Ana (verificado)")
        val v = fileVerifyVerdict(Sender.Contact(mallory))
        assertEquals(TrustLevel.CAUTION, v.level)
        assertFalse(v.text.startsWith("La firma es válida"))
        assertEquals(
            "El archivo no ha cambiado desde que lo firmó «Ana (verificado)», pero ese contacto está sin verificar: compara la huella.",
            v.text,
        )
        assertEquals(fileSignatureUnverifiedText("Ana (verificado)"), v.text)
    }

    @Test fun deUnaFirmaRealDeUnExtranoAlVeredicto() {
        // CADENAS-5 de punta a punta: Mallory firma con su clave; la bóveda solo tiene a Ana, verificada.
        val plain = data(1000)
        val sig = stranger().use { signFile(ByteArrayInputStream(plain), it) }
        val sender = verifyFileAgainst(ByteArrayInputStream(plain), sig, entries)
        assertEquals(Sender.UnknownKey, sender)
        assertNotEquals(TrustLevel.GOOD, fileVerifyVerdict(sender).level)
    }

    // ---- Errores en español ----

    @Test fun unaCryptoFailureSeMuestraConSuMensaje() {
        assertEquals("La firma del archivo no es válida", openErrorMessage(CryptoFailure("La firma del archivo no es válida")))
        assertEquals(NOT_FOR_ME, openErrorMessage(CryptoFailure(NOT_FOR_ME)))
        assertEquals(CryptoFailure().message, openErrorMessage(CryptoFailure()))
    }

    @Test fun otraExcepcionDaElMensajeGenericoSinTraza() {
        val generic = "No se ha podido abrir. Comprueba que está completo."
        assertEquals(generic, openErrorMessage(IOException("/data/user/0/app.seguridad/cache/x: EIO")))
        assertEquals(generic, openErrorMessage(IllegalStateException("La identidad está cerrada")))
        assertEquals(generic, openErrorMessage(NullPointerException()))
        assertEquals(generic, openErrorMessage(CryptoFailure("")))
        assertEquals(generic, openErrorMessage(CryptoFailure("   ")))
        assertFalse(openErrorMessage(RuntimeException("boom")).contains("boom"))
    }

    @Test fun elErrorDeCifrarOFirmarTieneSuPropioGenerico() {
        assertEquals("No se ha podido cifrar.", actionErrorMessage(IOException("x"), "No se ha podido cifrar."))
        assertEquals("Mensaje", actionErrorMessage(CryptoFailure("Mensaje"), "No se ha podido cifrar."))
    }

    // ---- Nombre de un archivo recibido ----

    @Test fun unNombreNormalNoCambia() {
        assertEquals("informe final.pdf", sanitizeReceivedName("informe final.pdf"))
        assertEquals("Ñandú ✓.txt", sanitizeReceivedName("Ñandú ✓.txt"))
    }

    @Test fun seQuitanControlesDelAlfabetoCYDeLaC1() {
        assertEquals("ab.txt", sanitizeReceivedName("a\u0000b\u001F.txt"))
        assertEquals("ab.txt", sanitizeReceivedName("a\u007Fb\u0085.txt"))
        assertEquals("ab.txt", sanitizeReceivedName("a\u0080b\u009F.txt"))
        assertEquals("ab.txt", sanitizeReceivedName("a\nb\r.txt"))
    }

    @Test fun seQuitanLosControlesBidiYLosInvisiblesDeLaListaFija() {
        // El clásico: «gpj.exe» que se ve como «exe.jpg».
        assertEquals("fotogpj.exe", sanitizeReceivedName("foto\u202Egpj.exe"))
        for (cp in listOf(0x200B, 0x200E, 0x200F, 0x202A, 0x202D, 0x2066, 0x2069, 0x061C, 0x00AD, 0xFEFF, 0x2028, 0x2029, 0x3164, 0xE0041, 0xE000)) {
            val bad = String(Character.toChars(cp))
            assertEquals("cp=${cp.toString(16)}", "ab.txt", sanitizeReceivedName("a${bad}b.txt"))
        }
    }

    @Test fun seMantienenLosCaracteresQueLaListaAdmiteExpresamente() {
        assertEquals("ab.txt", sanitizeReceivedName("a\u200Db.txt")) // el unidor solo se queda entre emoji (ver abajo)
        // U+FE0F se queda tras un emoji o un símbolo (CIFRADO-2: tras una letra ASCII no se ve y se quita).
        assertEquals("\u2764\uFE0F.txt", sanitizeReceivedName("\u2764\uFE0F.txt"))
        assertEquals("ab", sanitizeReceivedName("a\uFE0Fb"))
    }

    @Test fun elNombreRecibidoQuitaLoMismoQueSafeFileName() {
        // CIFRADO-2: una sola lista. Lo que safeFileName quita, sanitizeReceivedName también (y al revés).
        for (cp in listOf(0x3164, 0x115F, 0x1160, 0xFFA0, 0x034F, 0x17B4, 0x17B5, 0xFE00, 0xFE0E, 0xE0100, 0xE000, 0xFDD0,
            0xFFFE, 0x1FFFE, 0xE0001, 0x200C, 0x0600, 0x180E)) {
            val c = String(Character.toChars(cp))
            assertEquals("cp=${cp.toString(16)}", "ab.txt", sanitizeReceivedName("a${c}b$c.txt"))
            assertEquals("cp=${cp.toString(16)}", "archivo-recibido", sanitizeReceivedName(c))
        }
        for (cp in 0..0x10FFFF) {
            // A lone surrogate: safeFileName writes «?» (as UTF-8 always did), sanitizeReceivedName drops it.
            if (cp == '/'.code || cp == '\\'.code || cp == '.'.code || cp in 0xD800..0xDFFF) continue
            val c = String(Character.toChars(cp))
            val name = "a${c}b"
            assertEquals("cp=${cp.toString(16)}", app.seguridad.crypto.safeFileName(name), sanitizeReceivedName(name))
        }
        assertEquals("ab.txt", sanitizeReceivedName("a\uD800b.txt"))
    }

    @Test fun unNombreNuncaEsUnaRuta() {
        assertEquals("passwd", sanitizeReceivedName("../../etc/passwd"))
        assertEquals("evil.exe", sanitizeReceivedName("C:\\Windows\\evil.exe"))
        assertEquals("foo", sanitizeReceivedName("foo/"))
        assertEquals("foo", sanitizeReceivedName("a/b\\foo/"))
        assertFalse(sanitizeReceivedName("a/b\\c").contains('/'))
        assertFalse(sanitizeReceivedName("a/b\\c").contains('\\'))
    }

    @Test fun puntosYEspaciosDeLosExtremosSeRecortan() {
        assertEquals("informe.pdf", sanitizeReceivedName("  informe.pdf. . "))
        assertEquals("bashrc", sanitizeReceivedName("..bashrc"))
        assertEquals("a b.txt", sanitizeReceivedName(" a b.txt "))
        assertEquals("x", sanitizeReceivedName("\u00A0x\u00A0")) // espacio duro
    }

    @Test fun siQuedaVacioSeUsaArchivoRecibido() {
        for (name in listOf("", " ", ".", "..", "...", "/", "\\", "../", "\u202E", "\u0000\u0001", " . . ")) {
            assertEquals("name=<$name>", "archivo-recibido", sanitizeReceivedName(name))
        }
    }

    @Test fun elNombreNoPasaDe120CaracteresNiPartePares() {
        val long = "a".repeat(300)
        assertEquals(120, sanitizeReceivedName(long).length)
        val emoji = "\uD83D\uDE00" // 2 unidades
        val cut = sanitizeReceivedName("a".repeat(119) + emoji + "zzz")
        assertTrue(cut.length <= 120)
        // Sin ningún sustituto suelto: pasar por UTF-8 y volver da el mismo texto (un suelto se convertiría en «?»).
        assertEquals(cut, String(cut.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertFalse(cut.last().isHighSurrogate())
    }

    @Test fun elNombreSaneadoEsIdempotente() {
        for (name in listOf("a\u202Eb/..", "  x.y. ", "ok.txt", "../../..", "\uD83D\uDE00.png")) {
            val once = sanitizeReceivedName(name)
            assertEquals(once, sanitizeReceivedName(once))
        }
    }

    @Test fun losNombresDeLasSalidas() {
        assertEquals("a.pdf.sgenc", encryptedFileName("a.pdf"))
        assertEquals("a.pdf.sgsig", signatureFileName("a.pdf"))
        assertEquals("x.sgenc", encryptedFileName("../../x"))
        assertEquals("archivo.sgsig", signatureFileName(""))
    }

    // ---- El plan de «Abrir lo recibido» ----

    @Test fun elPlanSigueLoQueDiceDetectReceived() {
        val ana = ana()
        val card = parseCard(ana.cardBytes("Ana"))
        assertEquals(ReceivedPlan.CardFound(card, ana.cardBytes("Ana")), planReceived(cardText(ana.cardBytes("Ana"))))
        val message = encryptMessage("hola", myCard(), ana)
        assertTrue(planReceived(message) is ReceivedPlan.Message)
        val signed = me().use { signText("hola", it) }
        assertTrue(planReceived(signed) is ReceivedPlan.Signed)
    }

    @Test fun elPlanDeUnTextoQueNoSeReconoceDaUnMensajeEnEspanol() {
        for (text in listOf("hola", "SGX1:abc", "SGK1:abc")) {
            val plan = planReceived(text)
            assertTrue(text, plan is ReceivedPlan.Unrecognized)
            assertTrue((plan as ReceivedPlan.Unrecognized).message.isNotBlank())
        }
        assertEquals("Pega un texto", (planReceived("  \n") as ReceivedPlan.Unrecognized).message)
    }

    @Test fun elTextoPegadoNoPasaDelLimite() {
        val big = "a".repeat(MAX_TEXT_FILE_BYTES + 1)
        assertTrue(planReceived(big) is ReceivedPlan.Unrecognized)
    }

    // ---- Abrir un texto recibido ----

    @Test fun unMensajeFirmadoPorUnContactoVerificadoDiceDeQuienEs() {
        val token = encryptMessage("hola Yo", myCard(), ana())
        val outcome = openReceivedText(token, entries)
        assertTrue(outcome is ReceivedOutcome.Opened)
        outcome as ReceivedOutcome.Opened
        assertEquals("hola Yo", outcome.text)
        assertEquals(Sender.Contact(anaContact), outcome.sender)
        assertEquals("Verificado · De «Ana»", outcome.line.text)
    }

    @Test fun unMensajeSinFirmarDiceQueNoEstaFirmado() {
        val outcome = openReceivedText(encryptMessage("hola", myCard(), null), entries) as ReceivedOutcome.Opened
        assertEquals(Sender.Unsigned, outcome.sender)
        assertEquals("Sin firmar: cualquiera pudo enviarlo", outcome.line.text)
    }

    @Test fun unMensajeDeUnaClaveDesconocidaNoPoneNombre() {
        val outcome = openReceivedText(encryptMessage("hola", myCard(), stranger()), entries) as ReceivedOutcome.Opened
        assertEquals(Sender.UnknownKey, outcome.sender)
        assertEquals("Firmado por una clave que no está en tus contactos", outcome.line.text)
    }

    @Test fun unContactoSinVerificarSeDiceSinVerificar() {
        val unverified = contactOf(ana(), "Ana", verified = false)
        val outcome = openReceivedText(encryptMessage("hola", myCard(), ana()), listOf(meEntry, unverified)) as ReceivedOutcome.Opened
        assertEquals("Sin verificar · De «Ana»: compara la huella", outcome.line.text)
        assertEquals(TrustLevel.CAUTION, outcome.line.level)
    }

    @Test fun unMensajeQueNoEsParaMiDaNoEsParaTi() {
        val other = Identity(ByteArray(32) { (0x10 + it).toByte() })
        val forOther = encryptMessage("hola", parseCard(other.cardBytes("Otro")), ana())
        val outcome = openReceivedText(forOther, entries)
        assertEquals(ReceivedOutcome.Failed(NOT_FOR_ME), outcome)
    }

    @Test fun sinIdentidadUnMensajeNoSeAbreYSeDiceQueHayQueCrearla() {
        val token = encryptMessage("hola", myCard(), ana())
        val outcome = openReceivedText(token, listOf(anaContact))
        assertEquals(ReceivedOutcome.Failed(NO_IDENTITY_TO_OPEN), outcome)
        assertTrue(NO_IDENTITY_TO_OPEN.contains("Crea tu identidad"))
    }

    @Test fun unTextoFirmadoPorUnContactoSeComprueba() {
        val signed = ana().use { signText("Mi cuenta nueva", it) }
        val outcome = openReceivedText(signed, listOf(anaContact)) as ReceivedOutcome.Opened // sin identidad también vale
        assertEquals("Mi cuenta nueva", outcome.text)
        assertEquals("Verificado · Firmado por «Ana»", outcome.line.text)
        assertEquals(SIGNATURE_FORWARD_NOTE, outcome.line.note)
    }

    @Test fun miPropioTextoFirmadoDiceFirmadoPorTi() {
        val signed = me().use { signText("hecho", it) }
        val outcome = openReceivedText(signed, entries) as ReceivedOutcome.Opened
        assertEquals(Sender.Me, outcome.sender)
        assertEquals("Firmado por ti", outcome.line.text)
    }

    @Test fun unTextoFirmadoYCambiadoSeRechazaConSuMensaje() {
        val signed = ana().use { signText("pago 10", it) }.replace("pago 10", "pago 99")
        val outcome = openReceivedText(signed, entries)
        assertTrue(outcome is ReceivedOutcome.Failed)
        assertTrue((outcome as ReceivedOutcome.Failed).message.startsWith("La firma no es válida"))
    }

    @Test fun unaTarjetaPegadaSeEntregaParaAnadirla() {
        val bytes = stranger().cardBytes("Eva")
        val outcome = openReceivedText(cardText(bytes), entries)
        assertEquals(ReceivedOutcome.CardFound(parseCard(bytes), bytes), outcome)
    }

    /** I2: la tarjeta que `detectReceived` encuentra dentro de un texto más largo llega al flujo de añadir contacto. */
    @Test fun unaTarjetaConTextoAlrededorLlegaLegibleAlFlujoDeAnadir() {
        val bytes = stranger().cardBytes("Eva")
        val text = cardText(bytes)
        val wrapped = text.chunked(60).joinToString("\n")
        for (pasted in listOf("Hola, mi tarjeta: $text Gracias", "Hola, mi tarjeta:\n$wrapped\nGracias", "x".repeat(5000) + "\n$text", "$text\nUn saludo")) {
            val outcome = openReceivedText(pasted, entries)
            assertTrue(pasted.take(40), outcome is ReceivedOutcome.CardFound)
            outcome as ReceivedOutcome.CardFound
            assertArrayEquals(bytes, outcome.bytes)
            // Lo que se le pasa al flujo de añadir (el texto canónico) siempre se vuelve a leer, aunque el pegado no empiece por SGK1:.
            assertArrayEquals(bytes, cardBytesFromAnyText(cardText(outcome.bytes)))
            assertEquals(parseCard(bytes), outcome.card)
        }
        // La tarjeta del QR (Base45) también.
        val qr = openReceivedText(app.seguridad.crypto.pk.cardQr(bytes), entries)
        assertTrue(qr is ReceivedOutcome.CardFound)
        assertArrayEquals(bytes, (qr as ReceivedOutcome.CardFound).bytes)
    }

    @Test fun nadaReconocibleDaUnFallo() {
        assertTrue(openReceivedText("hola", entries) is ReceivedOutcome.Failed)
    }

    @Test fun elResultadoNoEnseniaElTextoEnToString() {
        val outcome = openReceivedText(encryptMessage("secreto-que-no-sale", myCard(), ana()), entries)
        assertFalse(outcome.toString().contains("secreto-que-no-sale"))
    }

    // ---- Cifrar y firmar ----

    @Test fun cifrarTextoParaUnContactoFirmadoSeAbreYDicePorQuien() {
        val token = encryptTextForContact("hola Ana", anaContact, sign = true, entries)
        val opened = ana().use { decryptMessage(token, it) }
        assertEquals("hola Ana", opened.text)
        assertArrayEquals(me().use { it.edPublic }, opened.senderEd)
    }

    @Test fun cifrarSinFirmarNoNecesitaIdentidad() {
        val token = encryptTextForContact("hola", anaContact, sign = false, listOf(anaContact))
        val opened = ana().use { decryptMessage(token, it) }
        assertNull(opened.senderEd)
    }

    @Test fun firmarSinIdentidadDiceQueHayQueCrearla() {
        val e = assertThrows(CryptoFailure::class.java) { encryptTextForContact("hola", anaContact, sign = true, listOf(anaContact)) }
        assertEquals(NO_IDENTITY_TO_SIGN, e.message)
        assertTrue(NO_IDENTITY_TO_SIGN.contains("Crea tu identidad en Contactos para firmar"))
        assertThrows(CryptoFailure::class.java) { signTextAs("hola", listOf(anaContact)) }
        assertThrows(CryptoFailure::class.java) { signFileAs(ByteArrayInputStream(ByteArray(3)), listOf(anaContact)) }
    }

    @Test fun cifrarParaAlgoQueNoEsUnContactoSeRechaza() {
        assertThrows(CryptoFailure::class.java) { encryptTextForContact("hola", meEntry, sign = false, entries) }
    }

    @Test fun firmarUnTextoYComprobarloDaMiIdentidad() {
        val block = signTextAs("  Hola\r\n", entries)
        val outcome = openReceivedText(block, entries) as ReceivedOutcome.Opened
        assertEquals("Hola", outcome.text)
        assertEquals(Sender.Me, outcome.sender)
    }

    @Test fun cifrarUnArchivoParaUnContactoSeAbreYVaFirmado() {
        val plain = data(70_000)
        val out = ByteArrayOutputStream()
        encryptFileForContact(ByteArrayInputStream(plain), out, "doc.pdf", anaContact, sign = true, entries)
        val dec = ByteArrayOutputStream()
        val opened = ana().use { decryptFileFor(ByteArrayInputStream(out.toByteArray()), dec, it) }
        assertArrayEquals(plain, dec.toByteArray())
        assertEquals("doc.pdf", opened.name)
        assertArrayEquals(me().use { it.edPublic }, opened.senderEd)
    }

    @Test fun firmarUnArchivoYComprobarloDaQuienFirmo() {
        val plain = data(130_000)
        val sig = signFileAs(ByteArrayInputStream(plain), entries)
        assertEquals(101, sig.size)
        assertEquals(Sender.Me, verifyFileAgainst(ByteArrayInputStream(plain), sig, entries))
        val fromAna = ana().use { signFile(ByteArrayInputStream(plain), it) }
        assertEquals(Sender.Contact(anaContact), verifyFileAgainst(ByteArrayInputStream(plain), fromAna, entries))
        val fromStranger = stranger().use { signFile(ByteArrayInputStream(plain), it) }
        assertEquals(Sender.UnknownKey, verifyFileAgainst(ByteArrayInputStream(plain), fromStranger, entries))
    }

    @Test fun unArchivoCambiadoNoCuadraConSuFirma() {
        val plain = data(1000)
        val sig = signFileAs(ByteArrayInputStream(plain), entries)
        val changed = plain.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertThrows(CryptoFailure::class.java) { verifyFileAgainst(ByteArrayInputStream(changed), sig, entries) }
    }

    @Test fun cancelarAlFirmarOComprobarUnArchivoPropagaLaCancelacion() {
        val plain = data(300_000)
        var calls = 0
        assertThrows(CancellationException::class.java) {
            signFileAs(ByteArrayInputStream(plain), entries) { if (++calls == 2) throw CancellationException("cancelado") }
        }
        val sig = signFileAs(ByteArrayInputStream(plain), entries)
        calls = 0
        assertThrows(CancellationException::class.java) {
            verifyFileAgainst(ByteArrayInputStream(plain), sig, entries) { if (++calls == 2) throw CancellationException("cancelado") }
        }
        calls = 0
        assertThrows(CancellationException::class.java) {
            encryptFileForContact(ByteArrayInputStream(plain), ByteArrayOutputStream(), "x", anaContact, true, entries) {
                if (++calls == 2) throw CancellationException("cancelado")
            }
        }
    }

    @Test fun elAvisoDeUnContactoSinVerificarEsElDelPlan() {
        val unverified = contactOf(ana(), "Ana", verified = false)
        assertEquals(
            "No has comprobado su huella: si alguien cambió su tarjeta, podría leerlo",
            encryptWarning(unverified),
        )
        assertNull(encryptWarning(anaContact))
    }

    @Test fun losTextosFijosNoPrometenLoQueNoSePuedePrometer() {
        val texts = listOf(SIGN_HELP, ENCRYPT_HELP, NO_IDENTITY_TO_OPEN, NO_IDENTITY_TO_SIGN, UNKNOWN_RECEIVED, SHARE_HINT, SYMMETRIC_FILE_NOTICE)
        for (t in texts) {
            val lower = t.lowercase()
            for (banned in listOf("validez legal", "firma electrónica", "imposible", "a prueba", "como signal", "indescifrable")) {
                assertFalse("«$t» dice «$banned»", lower.contains(banned))
            }
        }
        assertEquals("Demuestra a tus contactos que es tuyo y que no ha cambiado.", SIGN_HELP)
        // M5: dice también lo que no protege, sin promesas absolutas.
        assertEquals(
            "Solo quien tenga la identidad del contacto elegido podrá leerlo. Si alguien robara esa identidad podría leer también " +
                "lo que ya recibió, y quien vea el mensaje sabe que existe, su tamaño y cuándo se envió.",
            ENCRYPT_HELP,
        )
        assertEquals(
            "Este archivo está cifrado con contraseña: ábrelo en Herramientas → Cifrar archivos",
            SYMMETRIC_FILE_NOTICE,
        )
    }

    // ---- Qué archivo se ha elegido ----

    @Test fun seReconoceElTipoDeUnArchivoElegido() {
        val encrypted = fileFromAna(data(10)).copyOf(64)
        assertEquals(PickedKind.ENCRYPTED, pickedKind(encrypted))
        val sig = signFileAs(ByteArrayInputStream(data(10)), entries)
        assertEquals(PickedKind.SIGNATURE, pickedKind(sig))
        assertEquals(PickedKind.OTHER, pickedKind(data(64)))
        assertEquals(PickedKind.OTHER, pickedKind(ByteArray(0)))
    }

    @Test fun elPasoSiguienteDependeDeLoQueYaHay() {
        assertEquals(PickStep.READY_TO_DECRYPT, nextPickStep(PickedKind.ENCRYPTED, haveSignature = false, haveFile = false))
        assertEquals(PickStep.NEED_SIGNED_FILE, nextPickStep(PickedKind.SIGNATURE, haveSignature = true, haveFile = false))
        assertEquals(PickStep.READY_TO_VERIFY, nextPickStep(PickedKind.SIGNATURE, haveSignature = true, haveFile = true))
        assertEquals(PickStep.NEED_SIGNATURE, nextPickStep(PickedKind.OTHER, haveSignature = false, haveFile = true))
        assertEquals(PickStep.READY_TO_VERIFY, nextPickStep(PickedKind.OTHER, haveSignature = true, haveFile = true))
    }

    // ---- Lo elegido, en cualquier orden (también un .txt antes que su .sgsig) ----

    /** Elige [bytes] (con el URI [uri]) sobre [selection], como hace la pantalla: `classifyPicked` y `afterPicking`. */
    private fun pick(selection: FileSelection<String>, uri: String, bytes: ByteArray) =
        selection.afterPicking(classifyPicked(bytes, haveSignature = selection.signature != null), uri, "$uri.nombre")

    @Test fun unTxtElegidoAntesQueSuSgsigEsElArchivoFirmado() {
        // Un .txt con un texto firmado dentro: se lee como texto, y también tiene su .sgsig.
        val txt = utf8(me().use { signText("Acta de la reunión", it) })
        val sig = signFileAs(ByteArrayInputStream(txt), entries)
        val afterText = pick(FileSelection(), "content://acta.txt", txt)
        assertTrue(classifyPicked(txt, haveSignature = false) is PickedFile.Text)
        assertNull(afterText.signed)
        assertEquals("content://acta.txt", afterText.textCandidate)
        // Después, su .sgsig: el archivo firmado es el .txt, y la firma se comprueba contra él.
        val afterSig = pick(afterText, "content://acta.txt.sgsig", sig)
        assertEquals("content://acta.txt", afterSig.signed)
        assertEquals("content://acta.txt.nombre", afterSig.signedName)
        assertEquals(PickStep.READY_TO_VERIFY, nextPickStep(PickedKind.SIGNATURE, haveSignature = true, haveFile = afterSig.signed != null))
        assertEquals(Sender.Me, verifyFileAgainst(ByteArrayInputStream(txt), afterSig.signature!!, entries))
        // En el otro orden ya funcionaba: con un .sgsig elegido, el .txt es el archivo firmado.
        val sigFirst = pick(pick(FileSelection(), "content://acta.txt.sgsig", sig), "content://acta.txt", txt)
        assertEquals("content://acta.txt", sigFirst.signed)
    }

    @Test fun unArchivoFirmadoYaElegidoGanaAlTextoYUnSgencEmpiezaDeCero() {
        val txt = utf8(me().use { signText("hecho", it) })
        val other = data(500)
        val sig = signFileAs(ByteArrayInputStream(other), entries)
        // Texto, luego otro archivo, luego el .sgsig: el archivo firmado es el otro, no el texto.
        val s = pick(pick(pick(FileSelection(), "content://t", txt), "content://otro", other), "content://firma", sig)
        assertEquals("content://otro", s.signed)
        assertNull(s.textCandidate)
        // Un .sgenc después de un texto lo olvida: un .sgsig de después no tiene archivo firmado.
        val enc = fileFromAna(data(10))
        val afterEnc = pick(pick(FileSelection(), "content://t", txt), "content://c.sgenc", enc)
        assertEquals("content://c.sgenc", afterEnc.encrypted)
        assertNull(afterEnc.textCandidate)
        val noFile = pick(afterEnc, "content://firma", sig)
        assertNull(noFile.encrypted)
        assertNull(noFile.signed)
        assertEquals(PickStep.NEED_SIGNED_FILE, nextPickStep(PickedKind.SIGNATURE, haveSignature = true, haveFile = noFile.signed != null))
        // Y nada de lo elegido sale en toString.
        assertFalse(s.toString().contains("content://"))
    }

    // ---- Descifrar un archivo a una carpeta temporal ----

    private fun decryptToTemp(bytes: ByteArray, dir: File, entries: List<VaultEntry> = this.entries, check: () -> Unit = {}) =
        decryptReceivedFileToTemp(dir, ByteArrayInputStream(bytes), entries, check)

    @Test fun descifrarBienDejaElTemporalYDaElRemitente() {
        val dir = tempDir()
        val plain = data(200_000)
        val received = decryptToTemp(fileFromAna(plain, "informe.pdf"), dir)
        // Una subcarpeta propia de la operación, con el archivo dentro.
        assertEquals(listOf(received.file.parentFile), dir.listFiles()!!.toList())
        assertEquals(listOf(received.file), received.file.parentFile!!.listFiles()!!.toList())
        assertArrayEquals(plain, received.file.readBytes())
        assertEquals("informe.pdf", received.name)
        assertEquals(Sender.Contact(anaContact), received.sender)
        // Se guarda en el documento elegido y la carpeta se vacía.
        val saved = ByteArrayOutputStream()
        saveReceivedFile(received, saved)
        assertArrayEquals(plain, saved.toByteArray())
        clearTempDir(dir)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun elNombreDeDentroDelArchivoSeSaneaAntesDeProponerlo() {
        val dir = tempDir()
        val received = decryptToTemp(fileFromAna(data(10), "foto\u202Egpj.exe"), dir)
        assertEquals("fotogpj.exe", received.name)
        clearTempDir(dir)
    }

    @Test fun unArchivoSinFirmarNoDiceNingunNombre() {
        val dir = tempDir()
        val received = decryptToTemp(fileFromAna(data(10), signed = false), dir)
        assertEquals(Sender.Unsigned, received.sender)
        clearTempDir(dir)
    }

    @Test fun conElUltimoRegistroManipuladoNoQuedaNadaNiSeInformaDeRemitente() {
        val dir = tempDir()
        val file = fileFromAna(data(200_000))
        val tampered = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val e = assertThrows(CryptoFailure::class.java) { decryptToTemp(tampered, dir) }
        assertTrue(e.message!!.isNotBlank())
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun conUnRegistroDeEnMedioManipuladoNoQuedaNada() {
        val dir = tempDir()
        val file = fileFromAna(data(300_000))
        val tampered = file.copyOf().also { it[file.size / 2] = (it[file.size / 2].toInt() xor 1).toByte() }
        assertThrows(CryptoFailure::class.java) { decryptToTemp(tampered, dir) }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun truncadoNoQuedaNada() {
        val dir = tempDir()
        val file = fileFromAna(data(200_000))
        assertThrows(CryptoFailure::class.java) { decryptToTemp(file.copyOf(file.size - 100), dir) }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun unArchivoQueNoEsParaMiDaNoEsParaTiYNoDejaNada() {
        val dir = tempDir()
        val other = Identity(ByteArray(32) { (0x10 + it).toByte() })
        val out = ByteArrayOutputStream()
        ana().use { encryptFileFor(ByteArrayInputStream(data(100)), out, "x", parseCard(other.cardBytes("Otro")), it) }
        val e = assertThrows(CryptoFailure::class.java) { decryptToTemp(out.toByteArray(), dir) }
        assertEquals(NOT_FOR_ME, e.message)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun sinIdentidadNoCreaNingunaCarpetaYNoTocaLosRestos() {
        val dir = tempDir()
        File(dir, "resto").writeBytes(byteArrayOf(1))
        val e = assertThrows(CryptoFailure::class.java) { decryptToTemp(fileFromAna(data(10)), dir, listOf(anaContact)) }
        assertEquals(NO_IDENTITY_TO_OPEN, e.message)
        assertEquals(listOf("resto"), dir.listFiles()!!.map { it.name })
    }

    @Test fun cancelarAMitadBorraElTemporalYPropagaLaCancelacion() {
        val dir = tempDir()
        var calls = 0
        val file = fileFromAna(data(400_000))
        assertThrows(CancellationException::class.java) {
            decryptToTemp(file, dir, check = { if (++calls == 3) throw CancellationException("cancelado") })
        }
        assertTrue(calls >= 3)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun otraExcepcionDelFlujoTambienBorraElTemporal() {
        val dir = tempDir()
        val file = fileFromAna(data(200_000))
        val broken = object : java.io.InputStream() {
            var n = 0
            override fun read(): Int = throw IOException("fallo de lectura")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (n >= file.size / 2) throw IOException("fallo de lectura")
                val k = minOf(len, file.size / 2 - n)
                System.arraycopy(file, n, b, off, k)
                n += k
                return k
            }
        }
        assertThrows(IOException::class.java) { decryptReceivedFileToTemp(dir, broken, entries) }
        assertEquals(0, dir.listFiles()!!.size)
    }

    /** M2: cada operación tiene su subcarpeta y borra solo la suya; lo demás se vacía al arrancar la app y al bloquear. */
    @Test fun cadaOperacionTieneSuSubcarpetaYBorraSoloLaSuya() {
        val dir = tempDir()
        File(dir, "resto").writeBytes(byteArrayOf(1, 2, 3))
        val first = decryptToTemp(fileFromAna(data(10)), dir)
        val second = decryptToTemp(fileFromAna(data(20)), dir)
        assertNotEquals(first.file.parentFile, second.file.parentFile)
        // Un fallo de una tercera operación no toca ni los restos ni las otras dos.
        val tampered = fileFromAna(data(10)).let { it.copyOf().also { b -> b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte() } }
        assertThrows(CryptoFailure::class.java) { decryptToTemp(tampered, dir) }
        assertTrue(first.file.exists() && second.file.exists())
        assertTrue(File(dir, "resto").exists())
        first.discard()
        assertFalse(first.file.exists() || first.file.parentFile!!.exists())
        assertTrue(second.file.exists())
        second.discard()
        assertEquals(listOf("resto"), dir.listFiles()!!.map { it.name })
    }

    @Test fun alArrancarSeVaciaLaCarpetaDeTemporalesDeLaCache() {
        val cache = tempDir()
        val open = File(cache, PK_OPEN_TEMP_DIR)
        val op = File(open, "123e4567-e89b-12d3-a456-426614174000").also { it.mkdirs() }
        File(op, "recibido123.tmp").writeBytes(byteArrayOf(9, 9, 9))
        File(open, "suelto").writeBytes(byteArrayOf(1))
        val other = File(cache, "otra-cosa").also { it.writeBytes(byteArrayOf(5)) }
        wipePkTemp(cache)
        assertFalse(open.exists())
        assertTrue(other.exists())
        wipePkTemp(cache) // sin carpeta tampoco falla
        assertEquals("pk-open", PK_OPEN_TEMP_DIR)
    }

    @Test fun clearTempDirSinCarpetaNoFalla() {
        val dir = File(tempDir(), "no-existe")
        clearTempDir(dir)
        assertTrue(dir.isDirectory)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun guardarCancelandoAMitadPropagaLaCancelacion() {
        val dir = tempDir()
        val received = decryptToTemp(fileFromAna(data(400_000)), dir)
        var calls = 0
        assertThrows(CancellationException::class.java) {
            saveReceivedFile(received, ByteArrayOutputStream()) { if (++calls == 2) throw CancellationException("cancelado") }
        }
        clearTempDir(dir)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun elArchivoRecibidoNoEnseniaNadaEnToString() {
        val dir = tempDir()
        val received = decryptToTemp(fileFromAna(data(10), "secreto-nombre.pdf"), dir)
        assertFalse(received.toString().contains("secreto-nombre"))
        assertNotEquals("", received.toString())
        clearTempDir(dir)
    }

    // ---- M3: el nombre de dentro ----

    @Test fun unNombreInternoVacioOInvalidoSeProponeComoArchivoRecibido() {
        for (name in listOf("", "   ", ".", "..", "\u202E", "../..", "\u0000")) {
            // Hecho a mano: nuestro cifrador ya no escribe un nombre vacío, pero otro programa podría.
            val dir = tempDir()
            val received = decryptToTemp(fileWithBadSignature(data(10), ByteArray(0), signed = false, name = name), dir)
            assertEquals("name=<$name>", "archivo-recibido", received.name)
            received.discard()
        }
        // Un archivo que de verdad se llama «archivo» conserva su nombre.
        val dir = tempDir()
        assertEquals("archivo", decryptToTemp(fileFromAna(data(10), "archivo"), dir).name)
    }

    // ---- M7 (a): todos los registros AEAD válidos y la firma Ed25519ph inválida ----

    /** Un archivo firmado a mano para mí, bien sellado en todos sus registros pero con [finalPlain] como «firma». */
    private fun fileWithBadSignature(plain: ByteArray, finalPlain: ByteArray, signed: Boolean = true, name: String = "doc.pdf"): ByteArray {
        val prefix = ByteArray(STREAM_PREFIX_BYTES) { (0x20 + it).toByte() }
        val ikm = ByteArray(64) { (0x40 + it).toByte() }
        val h = hpkeSuite()
        val ctx = h.setupBaseS(h.deserializePublicKey(myCard().kemPublic), "seguridad-file-v1".toByteArray(Charsets.US_ASCII), ikm)
        val header = byteArrayOf(0x53, 0x47, 0x02, 0x03, 0x01) + ctx.encapsulation + prefix
        val key = ctx.export("seguridad-file-key-v1".toByteArray(Charsets.US_ASCII), 32)
        fun seal(counter: Int, last: Boolean, p: ByteArray) =
            aead(Aead.CHACHA20_POLY1305, true, key, ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array(), header, p)
        val out = ByteArrayOutputStream()
        out.write(header)
        val record0 = if (signed) byteArrayOf(1) + ana().use { it.edPublic } + name.toByteArray() else byteArrayOf(0) + name.toByteArray()
        val sealed0 = seal(0, false, record0)
        out.write(ByteBuffer.allocate(2).putShort(sealed0.size.toShort()).array())
        out.write(sealed0)
        var counter = 1
        var at = 0
        while (at < plain.size) {
            val piece = plain.copyOfRange(at, minOf(plain.size, at + CHUNK_BYTES))
            out.write(seal(counter++, false, piece))
            at += piece.size
        }
        out.write(seal(counter, true, finalPlain))
        return out.toByteArray()
    }

    @Test fun conTodosLosRegistrosValidosYLaFirmaInvalidaNoQuedaNadaNiHayRemitente() {
        val dir = tempDir()
        val plain = data(200_000)
        // Control: con la firma de verdad el montaje a mano se abre (si no, el test no probaría lo que dice).
        val good = ByteArrayOutputStream()
        ana().use { encryptFileFor(ByteArrayInputStream(plain), good, "doc.pdf", myCard(), it) }
        assertEquals(good.size(), fileWithBadSignature(plain, ByteArray(64)).size)
        // Toda la parte de datos está bien sellada: solo la firma (64 bytes) es mala.
        val e = assertThrows(CryptoFailure::class.java) { decryptToTemp(fileWithBadSignature(plain, ByteArray(64)), dir) }
        assertEquals("La firma del archivo no es válida", e.message)
        assertEquals(0, dir.listFiles()!!.size)
        // Una firma de otro archivo (válida en forma) tampoco.
        val other = ana().use { signFile(ByteArrayInputStream(data(5)), it) }.copyOfRange(37, 101)
        assertThrows(CryptoFailure::class.java) { decryptToTemp(fileWithBadSignature(plain, other), dir) }
        assertEquals(0, dir.listFiles()!!.size)
    }

    // ---- Unknown key-share (Davis, «Defective Sign & Encrypt», USENIX 2001): el ataque de punta a punta ----

    /**
     * Las tres bóvedas del ataque. Ana es «yo» ([meEntry]) y tiene a Bob como contacto verificado. Bob tiene su identidad y
     * guarda como «Mallory» la tarjeta de Mallory, que lleva la Ed25519 de Mallory y **la X-Wing de Ana**, firmada por
     * Mallory: una tarjeta válida, armada a mano porque `cardBytes` usa las dos claves de una misma identidad.
     */
    private inner class UnknownKeyShare {
        val bobEntry = newIdentityEntry(now)
        val anaEntries = listOf(meEntry, bobEntry.openIdentity().use { contactOf(it, "Bob", verified = true) })
        val malloryCardBytes: ByteArray = stranger().use { mallory ->
            val name = "Mallory".toByteArray(Charsets.UTF_8)
            val body = byteArrayOf(0x53, 0x47, 0x02, 0x01, 0x01) + mallory.edPublic + me().use { it.kemPublic } +
                byteArrayOf(name.size.toByte()) + name
            body + mallory.sign("seguridad-card-v1", body)
        }
        val malloryContact = newContactEntry(parseCard(malloryCardBytes), malloryCardBytes, now, viaQr = false).copy(title = "Mallory")
        val bobEntries = listOf(bobEntry, malloryContact)
    }

    @Test fun unMensajeFirmadoParaUnaTarjetaConMiXWingNuncaDiceDeBob() {
        val s = UnknownKeyShare()
        // Bob escribe a «Mallory», firmado; Mallory se lo reenvía a Ana tal cual.
        val forwarded = encryptTextForContact("Te paso las cuentas", s.malloryContact, sign = true, s.bobEntries)
        val outcome = openReceivedText(forwarded, s.anaEntries)
        assertEquals(ReceivedOutcome.Failed("La firma del mensaje no es válida"), outcome)
        assertFalse(outcome.toString().contains("Bob"))

        // Control: sin firmar, Ana lo abre como «Sin firmar».
        val unsigned = openReceivedText(encryptTextForContact("Te paso las cuentas", s.malloryContact, sign = false, s.bobEntries), s.anaEntries)
        unsigned as ReceivedOutcome.Opened
        assertEquals("Te paso las cuentas", unsigned.text)
        assertEquals(Sender.Unsigned, unsigned.sender)
        assertEquals("Sin firmar: cualquiera pudo enviarlo", unsigned.line.text)
    }

    @Test fun unArchivoFirmadoParaUnaTarjetaConMiXWingNuncaDiceDeBobYNoDejaNada() {
        val s = UnknownKeyShare()
        val dir = tempDir()
        val plain = data(200_000)
        val forwarded = ByteArrayOutputStream().also {
            encryptFileForContact(ByteArrayInputStream(plain), it, "cuentas.pdf", s.malloryContact, sign = true, s.bobEntries)
        }.toByteArray()
        val e = assertThrows(CryptoFailure::class.java) { decryptToTemp(forwarded, dir, s.anaEntries) }
        assertEquals("La firma del archivo no es válida", e.message)
        assertEquals("la carpeta temporal queda vacía", 0, dir.listFiles()!!.size)

        // Control: sin firmar, Ana lo abre como «Sin firmar».
        val unsigned = ByteArrayOutputStream().also {
            encryptFileForContact(ByteArrayInputStream(plain), it, "cuentas.pdf", s.malloryContact, sign = false, s.bobEntries)
        }.toByteArray()
        val received = decryptToTemp(unsigned, dir, s.anaEntries)
        assertEquals(Sender.Unsigned, received.sender)
        assertEquals("Sin firmar: cualquiera pudo enviarlo", senderLine(received.sender, addressedToMe = true).text)
        assertArrayEquals(plain, received.file.readBytes())
        received.discard()
        assertEquals(0, dir.listFiles()!!.size)
    }

    // ---- M6: elegir un archivo que es texto, y el cifrado con contraseña ----

    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test fun unArchivoDeTextoConUnMensajeSeLeeComoTexto() {
        val token = encryptMessage("hola", myCard(), ana())
        val picked = classifyPicked(utf8("Mira esto:\n$token\nGracias"), haveSignature = false)
        assertTrue(picked is PickedFile.Text)
        picked as PickedFile.Text
        assertTrue(picked.text.contains(token))
        assertTrue(picked.plan is ReceivedPlan.Message)
        val signed = classifyPicked(utf8(me().use { signText("hecho", it) }), false)
        assertTrue((signed as PickedFile.Text).plan is ReceivedPlan.Signed)
        val card = classifyPicked(utf8(cardText(ana().cardBytes("Ana"))), false)
        assertTrue((card as PickedFile.Text).plan is ReceivedPlan.CardFound)
    }

    @Test fun unArchivoDeTextoSinNadaReconocibleSigueElCaminoDeArchivoFirmado() {
        assertTrue(classifyPicked(utf8("Esto es un texto cualquiera"), false) is PickedFile.Other)
        assertTrue(classifyPicked(data(1000), false) is PickedFile.Other) // no es UTF-8
        assertTrue(classifyPicked(ByteArray(0), false) is PickedFile.Other)
    }

    @Test fun conUnaFirmaYaElegidaElArchivoDeTextoEsElArchivoFirmado() {
        val token = encryptMessage("hola", myCard(), ana())
        assertTrue(classifyPicked(utf8(token), haveSignature = true) is PickedFile.Other)
    }

    @Test fun unTextoDeMasDeLoPermitidoNoSeLee() {
        val token = encryptMessage("hola", myCard(), ana())
        val big = utf8(token + " ".repeat(MAX_TEXT_FILE_BYTES + 1 - token.length))
        assertEquals(MAX_TEXT_FILE_BYTES + 1, big.size)
        assertTrue(classifyPicked(big, false) is PickedFile.Other)
        val exact = utf8(token + " ".repeat(MAX_TEXT_FILE_BYTES - token.length))
        assertTrue(classifyPicked(exact, false) is PickedFile.Text)
    }

    @Test fun unUtf8MalFormadoNoSeLeeComoTexto() {
        val token = utf8(encryptMessage("hola", myCard(), ana()))
        assertTrue(classifyPicked(token + byteArrayOf(0xC3.toByte()), false) is PickedFile.Other) // byte suelto
    }

    @Test fun losArchivosBinariosDeContactosSeReconocenAntesQueElTexto() {
        assertTrue(classifyPicked(fileFromAna(data(10)).copyOf(64), false) is PickedFile.Encrypted)
        val sig = signFileAs(ByteArrayInputStream(data(10)), entries)
        val picked = classifyPicked(sig, false)
        assertTrue(picked is PickedFile.Signature)
        assertArrayEquals(sig, (picked as PickedFile.Signature).bytes)
        // Una cabecera «SG» 2 de otro tipo (una tarjeta binaria) no es ni un .sgenc ni un .sgsig ni texto.
        val binaryCard = ana().cardBytes("Ana")
        assertTrue(classifyPicked(binaryCard, false) is PickedFile.Other)
    }

    @Test fun unArchivoQueEmpiezaPorSG02ConUnMensajeDetrasNuncaSeClasificaComoTexto() {
        // Una cabecera binaria SG 0x02 de cualquier tipo manda sobre el texto que lleve detrás: un `SGM1:` pegado dentro de un
        // .sgsig o de una tarjeta binaria no puede convertirlo en «un mensaje pegado».
        val message = utf8(encryptMessage("hola", myCard(), ana()))
        for (kind in 1..9) {
            val bytes = byteArrayOf('S'.code.toByte(), 'G'.code.toByte(), 2, kind.toByte(), 1) + message
            for (haveSignature in listOf(false, true)) {
                val picked = classifyPicked(bytes, haveSignature)
                assertFalse("tipo $kind, firma $haveSignature", picked is PickedFile.Text)
            }
        }
        // En concreto, el tipo 5 (firma de archivo) es una firma y el 1 (tarjeta) es otro binario.
        val sgsig = byteArrayOf('S'.code.toByte(), 'G'.code.toByte(), 2, 5, 1) + message
        assertTrue(classifyPicked(sgsig, false) is PickedFile.Signature)
        assertTrue(classifyPicked(byteArrayOf('S'.code.toByte(), 'G'.code.toByte(), 2, 1, 1) + message, false) is PickedFile.Other)
    }

    private fun symmetricFile(extra: (ByteArray) -> ByteArray = { it }): ByteArray {
        val out = ByteArrayOutputStream()
        encryptStream(ByteArrayInputStream(data(100)), out, "nota.txt", "una contraseña larga".toCharArray(), Aead.AES_256_GCM, KdfParams(1024, 1, 1))
        return extra(out.toByteArray())
    }

    @Test fun unSgencConContrasenaTieneSuPropioAvisoYNuncaSeTomaPorFirmaInvalida() {
        assertTrue(classifyPicked(symmetricFile(), false) is PickedFile.Symmetric)
        assertTrue(classifyPicked(symmetricFile(), haveSignature = true) is PickedFile.Symmetric)
        // El dialecto antiguo (CP1, .cpenc) también.
        val legacy = symmetricFile { b -> b.copyOf().also { it[0] = 'C'.code.toByte(); it[1] = 'P'.code.toByte() } }
        assertTrue(classifyPicked(legacy, false) is PickedFile.Symmetric)
        // Y no se confunde con lo de clave pública.
        assertEquals(PickedKind.OTHER, pickedKind(symmetricFile().copyOf(8)))
        assertTrue(SYMMETRIC_FILE_NOTICE.contains("cifrado con contraseña"))
        assertFalse(SYMMETRIC_FILE_NOTICE.lowercase().contains("firma"))
    }

    // ---- A15a: a result is shown only next to the inputs it came from ----

    @Test fun staleResultIsHidden() {
        val inputs = PkInputs(contactId = "ana", text = "hola", fileUri = null)
        val last = PkResult(inputs, "SGM1:abc")
        assertEquals("SGM1:abc", resultFor(inputs, last))
        assertEquals("SGM1:abc", resultFor(PkInputs("ana", "hola", null), last))
        // Another contact, another text or another file: nothing.
        assertNull(resultFor(PkInputs("luis", "hola", null), last))
        assertNull(resultFor(PkInputs("ana", "hola!", null), last))
        assertNull(resultFor(PkInputs("ana", "", null), last))
        assertNull(resultFor(PkInputs(null, "hola", null), last))
        assertNull(resultFor(PkInputs("ana", null, "content://x/1"), last))
        assertNull(resultFor(inputs, null))
        // «Firmar» has no contact: the text is what counts.
        val signed = PkResult(PkInputs(null, "texto", null), "SGS1:xyz")
        assertEquals("SGS1:xyz", resultFor(PkInputs(null, "texto", null), signed))
        assertNull(resultFor(PkInputs(null, "texto ", null), signed))
        // The plain text never shows in toString.
        assertFalse("hola" in inputs.toString())
        assertFalse("hola" in last.toString())
    }

    // ---- A15b: «No enviar el nombre» ----

    @Test fun anonymousFileName() {
        assertEquals("archivo.pdf", anonymousFileName("factura marzo.pdf"))
        assertEquals("archivo", anonymousFileName("sin-extension"))
        assertEquals("archivo", anonymousFileName("datos.extensionlarga")) // 15 characters
        assertEquals("archivo.abcdefghij", anonymousFileName("x.abcdefghij")) // exactly 10
        assertEquals("archivo", anonymousFileName("foto.jp-g"))
        assertEquals("archivo", anonymousFileName("foto.jpé"))
        assertEquals("archivo", anonymousFileName("foto.\u0661\u0662")) // Arabic-Indic digits are not ASCII.
        assertEquals("archivo", anonymousFileName("termina en punto."))
        assertEquals("archivo", anonymousFileName(""))
        // Lowercased, and only the last extension.
        assertEquals("archivo.jpg", anonymousFileName("IMG_20261027_1200.JPG"))
        assertEquals("archivo.gz", anonymousFileName("copia de Ana.tar.gz"))
        // A path or a direction override never get through.
        assertEquals("archivo.txt", anonymousFileName("../../Ana García/notas.txt"))
        assertEquals("archivo.exe", anonymousFileName("factura\u202Efdp.exe")) // The real extension, not the one shown.
        // Nothing of the original name is left.
        assertFalse("factura" in anonymousFileName("factura marzo.pdf"))
    }

    // ---- A16a: the same signing key in several contacts ----

    @Test fun senderLineWarnsWhenTheKeyIsInSeveralContacts() {
        val ana = contactOf(ana(), "Ana", verified = true)
        val line = senderLine(Sender.Contact(ana, sameKeyCount = 2), addressedToMe = true)
        assertEquals("Verificado · De «Ana»", line.text)
        assertEquals("Esta clave está guardada en 2 contactos.", line.sameKey)
        assertEquals("Esta clave está guardada en 3 contactos.", senderLine(Sender.Contact(ana, sameKeyCount = 3), addressedToMe = false).sameKey)
        // One contact: no warning.
        assertNull(senderLine(Sender.Contact(ana), addressedToMe = true).sameKey)
        assertNull(senderLine(Sender.Me, addressedToMe = true).sameKey)
    }

    @Test fun openingAMessageFromAKeyKeptTwiceSaysSo() {
        val older = contactOf(ana(), "Ana").copy(updatedAt = now - 5)
        val newer = contactOf(ana(), "Ana", title = "Ana trabajo").copy(updatedAt = now + 5)
        val token = ana().use { a -> encryptMessage("hola", myCard(), a) }
        val opened = openReceivedText(token, listOf(meEntry, older, newer)) as ReceivedOutcome.Opened
        assertEquals("hola", opened.text)
        assertEquals(newer, (opened.sender as Sender.Contact).entry)
        assertEquals("Esta clave está guardada en 2 contactos.", opened.line.sameKey)
    }

    // ---- Fix round 1 ----

    @Test fun saveNamesWithTheirSuffixStayWithin255Bytes() {
        val emoji = "\uD83D\uDE00"
        for (name in listOf("文".repeat(200), emoji.repeat(120), "a".repeat(300), "\u00F1".repeat(200) + ".pdf")) {
            for (saved in listOf(encryptedFileName(name), signatureFileName(name))) {
                assertTrue(saved, saved.toByteArray(Charsets.UTF_8).size <= 255)
                assertEquals(saved, String(saved.toByteArray(Charsets.UTF_8), Charsets.UTF_8)) // no lone surrogate
            }
        }
        assertTrue(encryptedFileName("文".repeat(200)).endsWith(".sgenc"))
        assertTrue(signatureFileName(emoji.repeat(120)).endsWith(emoji + ".sgsig"))
        // The received name proposed to save under also stays within 255 bytes (120 CJK would be 360).
        val received = sanitizeReceivedName("文".repeat(200))
        assertEquals("文".repeat(85), received)
        assertEquals(received, sanitizeReceivedName(received))
        assertEquals("a".repeat(120), sanitizeReceivedName("a".repeat(300))) // ASCII: the 120-character limit, as before.
    }

    /** A file for me from Ana whose name (raw UTF-8, no safeFileName) is [nameBytes]: like one from an older build. */
    private fun oldStyleFileFromAna(nameBytes: ByteArray, plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ana().use {
            app.seguridad.crypto.pk.encryptFileForNameBytes(
                ByteArrayInputStream(plain), out, nameBytes, myCard(), it, ByteArray(64) { 7 }, ByteArray(STREAM_PREFIX_BYTES) { 9 },
            )
        }
        return out.toByteArray()
    }

    @Test fun aPublicKeyFileWithALongNameStillOpens() {
        val dir = tempDir()
        val emoji = "\uD83D\uDE00"
        for (name in listOf("\u00F1".repeat(300) + ".txt", "\u00E9".repeat(512), emoji.repeat(256))) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            assertTrue(nameBytes.size > 255 && nameBytes.size <= 1024)
            val file = oldStyleFileFromAna(nameBytes, data(1000))
            // decryptFileFor (FileMessage.kt) reads it: the raw name whole, or through safeFileName.
            val raw = me().use { decryptFileFor(ByteArrayInputStream(file), ByteArrayOutputStream(), it, rawName = true) }
            assertEquals(name, raw.name)
            val safe = me().use { decryptFileFor(ByteArrayInputStream(file), ByteArrayOutputStream(), it) }
            assertEquals(app.seguridad.crypto.safeFileName(name), safe.name)
            assertTrue(safe.name.toByteArray(Charsets.UTF_8).size <= 255)
            // «Abrir lo recibido»: decrypted, with the sender, and shown through sanitizeReceivedName.
            val received = decryptToTemp(file, dir)
            assertArrayEquals(data(1000), received.file.readBytes())
            assertEquals(sanitizeReceivedName(name), received.name)
            assertTrue(received.name.toByteArray(Charsets.UTF_8).size <= 255)
            assertEquals(Sender.Contact(anaContact), received.sender)
            received.discard()
        }
    }

    @Test fun aLateMessageIsHiddenLikeALateResult() {
        val inputs = PkInputs("ana", "hola", null)
        val msg = PkMessage(inputs, false, "No se ha podido cifrar el mensaje.")
        assertEquals(false to "No se ha podido cifrar el mensaje.", messageFor(inputs, msg))
        assertNull(messageFor(PkInputs("luis", "hola", null), msg))
        assertNull(messageFor(PkInputs("ana", "hola!", null), msg))
        assertNull(messageFor(PkInputs("ana", null, "content://x/1"), msg))
        assertNull(messageFor(inputs, null))
        val fileMsg = PkMessage(PkInputs("ana", null, "content://x/1"), true, "Archivo cifrado y guardado.")
        assertEquals(true to "Archivo cifrado y guardado.", messageFor(PkInputs("ana", null, "content://x/1"), fileMsg))
        assertNull(messageFor(PkInputs("ana", null, "content://x/2"), fileMsg))
        assertFalse("hola" in msg.toString())
    }

    @Test fun elNombreRecibidoSoloGuardaElUnidorEntreEmoji() {
        val zwj = "\u200D"
        val family = "\uD83D\uDC68$zwj\uD83D\uDC69$zwj\uD83D\uDC67"
        assertEquals("$family.png", sanitizeReceivedName("$family.png"))
        assertEquals("factura.pdf", sanitizeReceivedName("fac${zwj}tura.pdf"))
        assertEquals(DEFAULT_RECEIVED_NAME, sanitizeReceivedName(zwj))
        assertEquals(DEFAULT_RECEIVED_NAME, sanitizeReceivedName("$zwj/$zwj . $zwj"))
        assertEquals("ok", sanitizeReceivedName("ok/$zwj")) // the last segment with something visible
        // A joiner the 120-character cut leaves at the end goes: 118 «a» + «é» + joiner = 120 units.
        assertEquals("a".repeat(118) + "é", sanitizeReceivedName("a".repeat(118) + "é" + zwj + "é"))
        // The same rule as safeFileName, and idempotent.
        for (n in listOf("$family.png", "fac${zwj}tura.pdf", "a".repeat(118) + "é" + zwj + "é")) {
            assertEquals(app.seguridad.crypto.safeFileName(n), sanitizeReceivedName(n))
            assertEquals(sanitizeReceivedName(n), sanitizeReceivedName(sanitizeReceivedName(n)))
        }
    }
}
