package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.pk.Identity
import app.seguridad.crypto.pk.NOT_FOR_ME
import app.seguridad.crypto.pk.cardBytes
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
        assertEquals(SenderLine("Firmado por ti", TrustLevel.GOOD), senderLine(Sender.Me))
        assertEquals(SenderLine("De Ana (verificado)", TrustLevel.GOOD), senderLine(Sender.Contact(contactOf(ana(), "Ana", verified = true))))
        assertEquals(
            SenderLine("De Ana, sin verificar: compara la huella", TrustLevel.CAUTION),
            senderLine(Sender.Contact(contactOf(ana(), "Ana", verified = false))),
        )
        assertEquals(SenderLine("Firmado por una clave que no está en tus contactos", TrustLevel.WARNING), senderLine(Sender.UnknownKey))
        assertEquals(SenderLine("Sin firmar: cualquiera pudo enviarlo", TrustLevel.WARNING), senderLine(Sender.Unsigned))
    }

    @Test fun elNombreEsElGuardadoYNuncaElDeLaTarjeta() {
        val entry = contactOf(ana(), cardName = "Ana Falsa", title = "Ana", verified = true)
        val line = senderLine(Sender.Contact(entry))
        assertTrue(line.text.contains("Ana"))
        assertFalse(line.text.contains("Falsa"))
        // Un mensaje de un extraño no pone ningún nombre.
        assertFalse(senderLine(Sender.UnknownKey).text.contains("Ana"))
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
        assertEquals("a\u200Db.txt", sanitizeReceivedName("a\u200Db.txt")) // unir de ancho cero (emoji compuestos)
        assertEquals("a\uFE0Fb", sanitizeReceivedName("a\uFE0Fb"))
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
        assertTrue(cut.none { it.isSurrogate() } || cut.codePointCount(0, cut.length) == cut.length - cut.count { it.isHighSurrogate() })
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
        assertEquals(ReceivedPlan.CardFound(card), planReceived(cardText(ana.cardBytes("Ana"))))
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
        assertEquals("De Ana (verificado)", outcome.line.text)
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
        assertEquals("De Ana, sin verificar: compara la huella", outcome.line.text)
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
        assertEquals("De Ana (verificado)", outcome.line.text)
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
        val card = parseCard(stranger().cardBytes("Eva"))
        val outcome = openReceivedText(cardText(stranger().cardBytes("Eva")), entries)
        assertEquals(ReceivedOutcome.CardFound(card), outcome)
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
        val texts = listOf(SIGN_HELP, ENCRYPT_HELP, NO_IDENTITY_TO_OPEN, NO_IDENTITY_TO_SIGN, UNKNOWN_RECEIVED, SHARE_HINT)
        for (t in texts) {
            val lower = t.lowercase()
            for (banned in listOf("validez legal", "firma electrónica", "imposible", "a prueba", "como signal", "indescifrable")) {
                assertFalse("«$t» dice «$banned»", lower.contains(banned))
            }
        }
        assertEquals("Demuestra a tus contactos que es tuyo y que no ha cambiado.", SIGN_HELP)
        assertTrue(ENCRYPT_HELP.contains("diseñado para resistir también a ordenadores cuánticos futuros (cifrado híbrido X-Wing, en proceso de estandarización)"))
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

    // ---- Descifrar un archivo a una carpeta temporal ----

    private fun decryptToTemp(bytes: ByteArray, dir: File, entries: List<VaultEntry> = this.entries, check: () -> Unit = {}) =
        decryptReceivedFileToTemp(dir, ByteArrayInputStream(bytes), entries, check)

    @Test fun descifrarBienDejaElTemporalYDaElRemitente() {
        val dir = tempDir()
        val plain = data(200_000)
        val received = decryptToTemp(fileFromAna(plain, "informe.pdf"), dir)
        assertEquals(1, dir.listFiles()!!.size)
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

    @Test fun sinIdentidadNoSeEmpiezaNiSeCreaElTemporal() {
        val dir = tempDir()
        val e = assertThrows(CryptoFailure::class.java) { decryptToTemp(fileFromAna(data(10)), dir, listOf(anaContact)) }
        assertEquals(NO_IDENTITY_TO_OPEN, e.message)
        assertEquals(0, dir.listFiles()!!.size)
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

    @Test fun alEmpezarSeVaciaLoQueHubieraQuedado() {
        val dir = tempDir()
        File(dir, "restos").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "otro").writeBytes(byteArrayOf(4))
        val received = decryptToTemp(fileFromAna(data(10)), dir)
        assertEquals(listOf(received.file.name), dir.listFiles()!!.map { it.name })
        clearTempDir(dir)
        assertEquals(0, dir.listFiles()!!.size)
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
}
