package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.pk.Card
import app.seguridad.crypto.pk.Identity
import app.seguridad.crypto.pk.MAX_NAME_BYTES
import app.seguridad.crypto.pk.cardBytes
import app.seguridad.crypto.pk.cardFromAnyText
import app.seguridad.crypto.pk.fingerprint
import app.seguridad.crypto.pk.parseCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lógica de la pestaña «Contactos» que no es de pantalla: el aviso de cada `CardCheck`, la validación del nombre, el
 * formato de la huella, el filtrado de la lista, la marca de verificado y los mensajes de error.
 */
class ContactsViewTest {
    private val now = 1_790_000_000_000L

    private class Person(val entry: VaultEntry) {
        fun <T> open(block: (Identity) -> T): T = entry.openIdentity().use(block)
    }

    private fun person(name: String = "") = Person(newIdentityEntry(1).copy(username = name))

    private fun cardOf(p: Person, name: String): Card = parseCard(p.open { it.cardBytes(name) })

    private fun contactOf(p: Person, name: String, viaQr: Boolean = false, title: String = name): VaultEntry {
        val bytes = p.open { it.cardBytes(name) }
        return newContactEntry(parseCard(bytes), bytes, now, viaQr).copy(title = title)
    }

    /** La tarjeta firmada por [signer] con la clave X-Wing [kem]: la misma X-Wing con otra Ed25519 (`SharedKem`). */
    private fun forged(signer: Person, kem: ByteArray, name: String): Card {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val body = byteArrayOf(0x53, 0x47, 0x02, 0x01, 0x01) + signer.open { it.edPublic } + kem + byteArrayOf(nameBytes.size.toByte()) + nameBytes
        return parseCard(body + signer.open { it.sign("seguridad-card-v1", body) })
    }

    // ---- El aviso de cada CardCheck ----

    @Test fun unaTarjetaNuevaSePuedeGuardarYDiceDeDondeVino() {
        val card = cardOf(person(), "Ana")
        val viaChat = incomingCardNotice(CardCheck.New(card), viaQr = false)
        assertEquals(NoticeLevel.INFO, viaChat.level)
        assertEquals("Guardar contacto", viaChat.saveLabel)
        // Pegada: debajo de la huella, la frase de siempre, tal cual.
        assertEquals("Si te la han enviado por un chat, compara esta huella con tu contacto en persona o por llamada.", viaChat.footer)
        assertTrue(viaChat.body.contains("sin verificar"))
        val viaQr = incomingCardNotice(CardCheck.New(card), viaQr = true)
        assertEquals("Guardar contacto", viaQr.saveLabel)
        assertTrue(viaQr.body.contains("verificado"))
        assertEquals("", viaQr.footer)
    }

    @Test fun unContactoQueYaTienesDiceSuNombreGuardado() {
        val ana = person()
        val saved = contactOf(ana, "Ana", title = "Ana García")
        val notice = incomingCardNotice(CardCheck.Same(saved), viaQr = false)
        assertEquals("Ya lo tienes: Ana García", notice.title)
        assertEquals(NoticeLevel.INFO, notice.level)
        assertNull(notice.saveLabel)
    }

    @Test fun miPropiaTarjetaDiceQueEsMia() {
        val me = person()
        val notice = incomingCardNotice(CardCheck.Same(me.entry), viaQr = true)
        assertEquals("Es tu propia tarjeta", notice.title)
        assertNull(notice.saveLabel)
    }

    @Test fun laMismaClaveDeCifradoConOtraFirmaEsUnAvisoFuerteSinBotonParaGuardar() {
        val ana = person()
        val saved = contactOf(ana, "Ana", title = "Ana García")
        val impostor = forged(person(), ana.open { it.kemPublic }, "Ana")
        val notice = incomingCardNotice(CardCheck.SharedKem(saved, impostor), viaQr = false)
        assertEquals(NoticeLevel.DANGER, notice.level)
        assertNull("No se puede guardar", notice.saveLabel)
        assertEquals(
            "Esta tarjeta usa la misma clave de cifrado que Ana García, con otra firma. Una de las dos es falsa: compara la " +
                "huella con las dos personas, en persona o por llamada. No se guarda.",
            notice.body,
        )
        // Neutro: no acusa a la tarjeta nueva (la copia pudo llegar antes y la que se rechaza ser la legítima).
        assertEquals("Clave de cifrado repetida", notice.title)
        for (text in listOf(notice.title, notice.body, notice.footer)) {
            for (accusation in listOf("sospechosa", "suplantación", "intento", "Esta tarjeta es falsa")) {
                assertFalse("«$accusation» en «$text»", text.contains(accusation))
            }
        }
        // T13-3: con mi identidad no hay duda de cuál es la copia (la X-Wing es mía): texto propio, sin «compara con las dos personas».
        val mine = incomingCardNotice(CardCheck.SharedKem(person().entry, impostor), viaQr = true)
        assertEquals(
            "Esta tarjeta usa la clave de cifrado de tu identidad con otra firma: es una copia falsa de tu clave. No se guarda.",
            mine.body,
        )
        assertFalse(mine.body.contains("compara la huella con las dos personas"))
        assertEquals(NoticeLevel.DANGER, mine.level)
        assertNull(mine.saveLabel)
        assertEquals(notice.title, mine.title)
    }

    /** El aviso de `SharedKem` pide comparar la huella con las dos personas: las dos huellas se pueden enseñar. */
    @Test fun conLaMismaClaveDeCifradoSeEnseniaLaHuellaDeLoQueYaTienes() {
        val ana = person()
        val saved = contactOf(ana, "Ana", title = "Ana García")
        val impostor = forged(person(), ana.open { it.kemPublic }, "Ana")
        assertEquals(ana.open { fingerprint(it.edPublic, it.kemPublic) }, savedFingerprint(CardCheck.SharedKem(saved, impostor)))
        val me = person()
        val onMe = forged(person(), me.open { it.kemPublic }, "Yo")
        assertEquals(me.open { fingerprint(it.edPublic, it.kemPublic) }, savedFingerprint(CardCheck.SharedKem(me.entry, onMe)))
        // Las dos huellas son distintas: la de la tarjeta nueva no es la del contacto guardado.
        assertNotEquals(savedFingerprint(CardCheck.SharedKem(saved, impostor)), fingerprint(impostor.edPublic, impostor.kemPublic))
        // Con las claves cambiadas, la del contacto guardado; con una tarjeta nueva o la misma, ninguna.
        val other = cardOf(person(), "Ana")
        assertEquals(ana.open { fingerprint(it.edPublic, it.kemPublic) }, savedFingerprint(CardCheck.KeysChanged(saved, other)))
        assertNull(savedFingerprint(CardCheck.New(other)))
        assertNull(savedFingerprint(CardCheck.Same(saved)))
        // Una entrada que no se lee no rompe nada.
        assertNull(savedFingerprint(CardCheck.SharedKem(saved.copy(card = "%%%"), impostor)))
    }

    @Test fun lasClavesCambiadasSoloSePuedenGuardarComoContactoNuevo() {
        val saved = contactOf(person(), "Ana")
        val card = cardOf(person(), "Ana")
        val notice = incomingCardNotice(CardCheck.KeysChanged(saved, card), viaQr = true)
        assertEquals("Las claves de este contacto han cambiado", notice.title)
        assertEquals(NoticeLevel.WARNING, notice.level)
        assertEquals("Guardar como contacto nuevo", notice.saveLabel)
        // Nunca sustituye al antiguo, y dice que el nombre no basta: la defensa es la huella y la marca.
        assertTrue(notice.body.contains("No sustituye a tu contacto"))
        assertTrue(notice.body.contains("huellas"))
        // La defensa contra los nombres imitados (homoglifos) está dicha en pantalla.
        assertTrue(notice.footer.contains("sin verificar"))
        assertTrue(notice.footer.contains("huella"))
        assertTrue(incomingCardNotice(CardCheck.KeysChanged(saved, card), viaQr = false).footer.contains("sin verificar"))
        // Escanear el QR no quita el aviso ni salta la comparación.
        assertEquals(notice.saveLabel, incomingCardNotice(CardCheck.KeysChanged(saved, card), viaQr = false).saveLabel)
    }

    @Test fun elNombreGuardadoSeMuestraComoTextoSinInterpretarlo() {
        val saved = contactOf(person(), "Ana", title = "**Ana** [x](http://a.es) <b>")
        val notice = incomingCardNotice(CardCheck.Same(saved), viaQr = false)
        assertEquals("Ya lo tienes: **Ana** [x](http://a.es) <b>", notice.title)
    }

    @Test fun ningunTextoPrometeLoQueNoSePuedePrometer() {
        val ana = person()
        val saved = contactOf(ana, "Ana")
        val card = cardOf(person(), "Ana")
        val impostor = forged(person(), ana.open { it.kemPublic }, "Ana")
        val texts = mutableListOf<String>()
        for (viaQr in listOf(true, false)) {
            for (check in listOf(
                CardCheck.New(card), CardCheck.Same(saved), CardCheck.Same(person().entry),
                CardCheck.SharedKem(saved, impostor), CardCheck.KeysChanged(saved, card),
            )) {
                val n = incomingCardNotice(check, viaQr)
                texts += listOf(n.title, n.body, n.footer, n.saveLabel.orEmpty())
            }
        }
        texts += listOf(
            IDENTITY_EXPLANATION, DELETE_IDENTITY_WARNING, DELETE_IDENTITY_CONFIRMATION, NEEDS_MASTER_NOTICE,
            FINGERPRINT_HELP, UNVERIFIED_NOTICE, SIMILAR_NAME_WARNING, VERIFY_QUESTION, IDENTITY_RISK, TOO_BIG_CARD,
        )
        val banned = listOf("imposible", "cuántic", "100 %", "100%", "signal", "validez legal", "inviolable", "indescifrable", "extremo a extremo")
        for (t in texts) for (b in banned) assertFalse("«$t» contiene «$b»", t.lowercase().contains(b))
    }

    @Test fun losTextosFijosDicenLoQueDebenDecir() {
        assertEquals(
            "Tu identidad sirve para que tus contactos te cifren mensajes y para firmar. Se guarda solo en tu bóveda cifrada y en tus copias.",
            IDENTITY_EXPLANATION,
        )
        assertTrue(DELETE_IDENTITY_WARNING.contains("Los contactos tendrán que volver a añadirte y lo que te enviaron a esta identidad ya no se podrá abrir"))
        assertEquals("Compárala con la que ve tu contacto en su móvil", FINGERPRINT_HELP)
    }

    // ---- Validación del nombre ----

    @Test fun unNombreNormalSeAcepta() {
        assertEquals(ContactName.Valid("Ana García"), checkContactName("Ana García"))
        assertEquals(ContactName.Valid("Ana"), checkContactName("  Ana  "))
        assertEquals(ContactName.Valid("李雷 🙂"), checkContactName("李雷 🙂"))
    }

    @Test fun elNombreSeNormalizaANfc() {
        assertEquals(ContactName.Valid("Aná"), checkContactName("Ana\u0301")) // a + acento combinante
    }

    @Test fun miPropioNombreSePuedeDejarVacio() {
        assertEquals(ContactName.Valid(""), checkOwnName(""))
        assertEquals(ContactName.Valid(""), checkOwnName("   "))
        assertEquals(ContactName.Valid("Bea"), checkOwnName(" Bea "))
        assertTrue(checkOwnName("Bea\u202E") is ContactName.Invalid)
        assertTrue(checkOwnName("a".repeat(65)) is ContactName.Invalid)
    }

    @Test fun unNombreVacioOSoloDeEspaciosSeRechaza() {
        for (raw in listOf("", "   ", " ", "　")) {
            val r = checkContactName(raw)
            assertEquals("Escribe un nombre", (r as ContactName.Invalid).message)
        }
    }

    @Test fun elLimiteSonSesentaYCuatroBytesNoCaracteres() {
        assertTrue(checkContactName("a".repeat(MAX_NAME_BYTES)) is ContactName.Valid)
        val tooLong = checkContactName("a".repeat(MAX_NAME_BYTES + 1))
        assertTrue((tooLong as ContactName.Invalid).message.contains("64 bytes"))
        assertTrue(checkContactName("é".repeat(32)) is ContactName.Valid) // 64 bytes
        assertTrue(checkContactName("é".repeat(33)) is ContactName.Invalid) // 66 bytes con 33 caracteres
        assertTrue(checkContactName("🙂".repeat(16)) is ContactName.Valid) // 64 bytes con 16 caracteres
        assertTrue(checkContactName("🙂".repeat(17)) is ContactName.Invalid)
    }

    @Test fun losCaracteresProhibidosSeRechazan() {
        val forbidden = listOf(
            "Ana\nPérez", "Ana\tPérez", "Ana\u0000", "Ana​Pérez", "‮Ana", "Ana⁦", "Ana­", "Ana﻿",
            "Ana", "Ana ", "​", "Ana󠁁", // etiqueta U+E0041
        )
        for (raw in forbidden) {
            val r = checkContactName(raw)
            assertTrue("«${raw.map { it.code.toString(16) }}» debería rechazarse", r is ContactName.Invalid)
            assertTrue((r as ContactName.Invalid).message.contains("caracteres"))
        }
        // Los que el diseño admite expresamente.
        assertTrue(checkContactName("می‌خواهم") is ContactName.Valid)
        assertTrue(checkContactName("❤️") is ContactName.Valid)
    }

    @Test fun unSustitutoSueltoNoEsTexto() {
        val r = checkContactName("Ana\uD800")
        assertTrue(r is ContactName.Invalid)
    }

    @Test fun elNombreSugeridoParaUnaTarjetaNuevaEsElDeLaTarjeta() {
        val card = cardOf(person(), "Ana")
        assertEquals("Ana", suggestedContactName(CardCheck.New(card)))
        assertEquals("", suggestedContactName(CardCheck.New(cardOf(person(), ""))))
    }

    @Test fun elNombreSugeridoParaClavesCambiadasSeDistingueDelAntiguo() {
        val saved = contactOf(person(), "Ana")
        assertEquals("Ana (nuevo)", suggestedContactName(CardCheck.KeysChanged(saved, cardOf(person(), "Ana"))))
        // Si no cabe en 64 bytes, el de la tarjeta tal cual.
        val long = "a".repeat(MAX_NAME_BYTES)
        assertEquals(long, suggestedContactName(CardCheck.KeysChanged(saved, cardOf(person(), long))))
        assertEquals("Contacto nuevo", suggestedContactName(CardCheck.KeysChanged(saved, cardOf(person(), ""))))
    }

    // ---- CADENAS-4 y CLAVE-PUBLICA-1: un nombre no puede imitar la marca de verificación ----

    private val imitations = listOf(
        "Ana (verificado)", "Ana (Verificado)", "Ana ✓ verificado con QR", "Ana (verificada)", "Ana, sin verificar",
        "Ana VERIFICADO", "Ana (verificádo)", "Ana ✓", "Ana ✔️", "Ana ☑", "Ana ✅", "ti", "Ti", "tú", "TÚ", "tu", "Ana (tú)",
        "Ana (tu)", "Ana v e r i f i c a d o", "Ana ｖｅｒｉｆｉｃａｄｏ", "Ana verified",
    )

    @Test fun unNombreQueImitaLaMarcaDeVerificacionSeRechaza() {
        for (raw in imitations) {
            val r = checkContactName(raw)
            assertTrue("«$raw» debería rechazarse", r is ContactName.Invalid)
            assertEquals(NAME_IMITATES_MARK, (r as ContactName.Invalid).message)
            assertTrue(raw, imitatesTrustMark(raw))
        }
        // Mi propio nombre, que va en mi tarjeta, tampoco.
        assertTrue(checkOwnName("Ana (verificado)") is ContactName.Invalid)
        // Los nombres normales siguen valiendo, también los que llevan «ti» o «tu» dentro de otra palabra.
        for (ok in listOf("Ana", "Tito", "Tuya", "Martín", "Ana Tú Pérez", "Verónica", "Vera", "Fico", "❤️", "Ana (casa)", "Ti Ana")) {
            assertTrue("«$ok» debería valer", checkContactName(ok) is ContactName.Valid)
            assertFalse(ok, imitatesTrustMark(ok))
        }
        assertEquals(
            "El nombre no puede parecerse a la marca de verificación («verificado», «sin verificar», ✓) ni ser «ti» o «tú»",
            NAME_IMITATES_MARK,
        )
    }

    @Test fun elNombreSugeridoNuncaEsUnoQueImitaLaMarca() {
        val saved = contactOf(person(), "Ana")
        for (fake in listOf("Ana (verificado)", "Ana ✓ verificado con QR", "ti")) {
            val card = cardOf(person(), fake) // la tarjeta se sigue leyendo (parseCard no cambia: Card.kt, regla de la 1.2.0)
            assertEquals(fake, card.name)
            assertEquals("", suggestedContactName(CardCheck.New(card)))
            assertEquals("", suggestedContactName(CardCheck.KeysChanged(saved, card)))
            // Y se enseña aparte, entre «», con un aviso.
            assertEquals(cardNameImitatesText(fake), imitatedCardNameText(CardCheck.New(card)))
            assertEquals(cardNameImitatesText(fake), imitatedCardNameText(CardCheck.KeysChanged(saved, card)))
        }
        assertNull(imitatedCardNameText(CardCheck.New(cardOf(person(), "Ana"))))
        assertNull(imitatedCardNameText(CardCheck.Same(saved)))
        assertEquals(
            "La tarjeta trae el nombre «x», que se puede confundir con la marca de verificación o con tu propia firma: " +
                "escribe tú el nombre del contacto.",
            cardNameImitatesText("x"),
        )
    }

    @Test fun unContactoYaGuardadoConUnNombreAsiSeSigueLeyendoYSeEnseniaEntreComillas() {
        // Decisión (CADENAS-4): la regla nueva solo se aplica a lo que se escribe o llega ahora. Un contacto guardado antes
        // (o que viene en una copia) con un nombre así se sigue leyendo, listando, verificando y borrando; se enseña entre
        // «» para que no pase por la marca; y solo al cambiarle el nombre hay que poner otro.
        val legacy = contactOf(person(), "Ana", title = "Ana (verificado)")
        val (file, dek) = app.seguridad.vault.VaultCrypto.create("maestra".toCharArray(), now, app.seguridad.crypto.KdfParams(1024, 1, 1))
        val read = VaultCrypto.read(VaultCrypto.write(file, dek, listOf(legacy)), dek)
        assertEquals(listOf(legacy), read)
        assertEquals(listOf(legacy), contactsList(read, "verificado"))
        assertEquals("«Ana (verificado)»", shownContactTitle(legacy.title))
        assertEquals("Ana", shownContactTitle("Ana"))
        assertTrue(markVerified(legacy, now + 1).verified)
        assertEquals("Ya lo tienes: «Ana (verificado)»", incomingCardNotice(CardCheck.Same(legacy), viaQr = false).title)
        assertTrue(checkContactName(legacy.title) is ContactName.Invalid)
        assertEquals("Ana García", renamedContact(legacy, "Ana García", now + 1).title)
    }

    // ---- La huella ----

    @Test fun laHuellaDeUnaTarjetaEsLaDeSusClaves() {
        val p = person()
        val card = cardOf(p, "Ana")
        assertEquals(fingerprint(card.edPublic, card.kemPublic), cardFingerprint(card))
        // No depende del nombre.
        assertEquals(cardFingerprint(card), cardFingerprint(cardOf(p, "Otro nombre")))
        assertNotEquals(cardFingerprint(card), cardFingerprint(cardOf(person(), "Ana")))
    }

    @Test fun laHuellaSeMuestraEnSeisGruposDeCincoCifrasEnDosFilas() {
        val fp = cardFingerprint(cardOf(person(), "Ana"))
        assertTrue(Regex("(\\d{5} ){5}\\d{5}").matches(fp))
        val rows = fingerprintRows(fp)
        assertEquals(2, rows.size)
        assertTrue(rows.all { Regex("\\d{5} \\d{5} \\d{5}").matches(it) })
        assertEquals(fp, rows.joinToString(" "))
    }

    @Test fun lasFilasDeLaHuellaConservanLosCerosDeLaIzquierda() {
        assertEquals(listOf("00001 00020 03000", "40000 00005 99999"), fingerprintRows("00001 00020 03000 40000 00005 99999"))
    }

    @Test fun unaHuellaQueNoTieneElFormatoSeEnseñaTalCualEnUnaFila() {
        assertEquals(listOf("12345 678"), fingerprintRows("12345 678"))
        assertEquals(listOf(""), fingerprintRows(""))
    }

    // ---- Mi tarjeta ----

    @Test fun miTarjetaLlevaMiNombreYLaMismaHuellaQueVeElOtro() {
        val me = person("Bea")
        val mine = me.entry.myCard()
        val card = parseCard(mine.bytes)
        assertEquals("Bea", card.name)
        assertEquals(cardFingerprint(card), mine.fingerprint)
        assertEquals(card, cardFromAnyText(mine.text))
        assertEquals(card, cardFromAnyText(mine.qrText))
        assertTrue(mine.text.startsWith("SGK1:"))
        assertTrue(mine.qrText.startsWith("SGK1:"))
        // El QR va en Base45 (modo alfanumérico): solo mayúsculas, cifras y «espacio $%*+-./:».
        assertTrue(mine.qrText.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:" })
    }

    @Test fun miTarjetaSinNombreEsValida() {
        val mine = person().entry.myCard()
        assertEquals("", parseCard(mine.bytes).name)
    }

    @Test fun miTarjetaEsLaMismaEnCadaApertura() {
        val me = person("Bea")
        val a = me.entry.myCard()
        val b = me.entry.myCard()
        assertEquals(a.text, b.text)
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test fun miTarjetaSoloGuardaDatosPublicosNiLaIdentidadNiLaSemilla() {
        val me = person("Bea")
        val mine = me.entry.myCard()
        // Por la forma: ningún campo es una Identity ni nada que no sean bytes o textos.
        // (Sin los campos estáticos que añade el compilador de Compose, como `$stable`.)
        for (f in MyCard::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
            assertTrue("${f.name}: ${f.type}", f.type == ByteArray::class.java || f.type == String::class.java)
        }
        // Por el contenido: ni la semilla ni sus claves privadas aparecen en lo que guarda.
        val seed = java.util.Base64.getDecoder().decode(me.entry.identitySeed)
        val seedHex = seed.joinToString("") { "%02x".format(it) }
        val seedB64 = java.util.Base64.getEncoder().encodeToString(seed)
        val seedUrl = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(seed)
        assertFalse(indexOf(mine.bytes, seed) >= 0)
        for (t in listOf(mine.text, mine.qrText, mine.fingerprint)) {
            for (needle in listOf(seedHex, seedB64, seedUrl)) assertFalse(t.contains(needle, ignoreCase = true))
        }
        // Y lo que hay es una tarjeta pública: se lee sin ninguna clave.
        assertEquals(me.open { it.edPublic }.toList(), parseCard(mine.bytes).edPublic.toList())
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int =
        (0..haystack.size - needle.size).firstOrNull { i -> needle.indices.all { haystack[i + it] == needle[it] } } ?: -1

    // ---- De la tarjeta real al aviso (checkIncomingCard + incomingCardNotice) ----

    private fun noticeFor(card: Card, entries: List<VaultEntry>, viaQr: Boolean = false) =
        checkIncomingCard(card, entries).let { it to incomingCardNotice(it, viaQr) }

    @Test fun deLaTarjetaRealAlAvisoNuevaYaLaTienesYMiPropiaTarjeta() {
        val me = person("Bea")
        val ana = person()
        val entries = listOf(me.entry, contactOf(ana, "Ana", title = "Ana García"))
        // Nueva: otra persona.
        val (newCheck, newNotice) = noticeFor(cardOf(person(), "Carlos"), entries)
        assertTrue(newCheck is CardCheck.New)
        assertEquals("Guardar contacto", newNotice.saveLabel)
        // Un contacto que ya tengo, aunque llegue con otro nombre en la tarjeta.
        val (sameCheck, sameNotice) = noticeFor(cardOf(ana, "Ana otra vez"), entries, viaQr = true)
        assertTrue(sameCheck is CardCheck.Same)
        assertEquals("Ya lo tienes: Ana García", sameNotice.title)
        assertNull(sameNotice.saveLabel)
        // Mi propia tarjeta.
        val (ownCheck, ownNotice) = noticeFor(cardOf(me, "Bea"), entries)
        assertTrue(ownCheck is CardCheck.Same && (ownCheck as CardCheck.Same).entry.type == EntryType.IDENTITY)
        assertEquals("Es tu propia tarjeta", ownNotice.title)
        assertNull(ownNotice.saveLabel)
    }

    @Test fun deLaTarjetaRealAlAvisoSharedKemNuncaTieneCaminoParaGuardar() {
        val me = person("Bea")
        val ana = person()
        val entries = listOf(me.entry, contactOf(ana, "Ana", title = "Ana García"))
        // La X-Wing de un contacto con otra Ed25519 y OTRO nombre (que no puede pasar por «claves cambiadas»).
        val onContact = forged(person(), ana.open { it.kemPublic }, "Mallory")
        val (c1, n1) = noticeFor(onContact, entries)
        assertTrue(c1 is CardCheck.SharedKem)
        assertEquals(NoticeLevel.DANGER, n1.level)
        assertNull(n1.saveLabel)
        assertTrue(n1.body.contains("Ana García"))
        // También con el mismo nombre que el contacto, por QR o pegada.
        for (qr in listOf(true, false)) {
            val (c, n) = noticeFor(forged(person(), ana.open { it.kemPublic }, "Ana"), entries, qr)
            assertTrue(c is CardCheck.SharedKem)
            assertNull(n.saveLabel)
        }
        // La X-Wing de mi propia identidad.
        val onMine = forged(person(), me.open { it.kemPublic }, "Bea")
        val (c2, n2) = noticeFor(onMine, entries)
        assertTrue(c2 is CardCheck.SharedKem)
        assertNull(n2.saveLabel)
        assertTrue(n2.body.contains("tu identidad"))
    }

    @Test fun deLaTarjetaRealAlAvisoClavesCambiadasSoloOfreceGuardarComoNuevo() {
        val ana = person()
        val entries = listOf(contactOf(ana, "Ana", title = "Ana García"))
        // Mismo nombre, otras claves.
        val (c1, n1) = noticeFor(cardOf(person(), "Ana García"), entries)
        assertTrue(c1 is CardCheck.KeysChanged)
        assertEquals("Guardar como contacto nuevo", n1.saveLabel)
        // Misma Ed25519 con otra X-Wing y otro nombre.
        val newKem = person().open { it.kemPublic }
        val (c2, n2) = noticeFor(forged(ana, newKem, "Anita"), entries, viaQr = true)
        assertTrue(c2 is CardCheck.KeysChanged)
        assertEquals("Guardar como contacto nuevo", n2.saveLabel)
        assertEquals("Las claves de este contacto han cambiado", n2.title)
    }

    @Test fun unaIdentidadConNombreNoValidoNoDaTarjeta() {
        val bad = person().entry.copy(username = "Ana‮")
        try {
            bad.myCard()
            org.junit.Assert.fail("Debería lanzar CryptoFailure")
        } catch (e: CryptoFailure) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    // ---- La lista de contactos ----

    @Test fun laListaSoloTieneContactosOrdenadosPorNombre() {
        val me = person().entry
        val all = listOf(
            me,
            contactOf(person(), "Zoe"),
            contactOf(person(), "Ángel"),
            contactOf(person(), "Beatriz"),
            VaultEntry(id = "n", type = EntryType.NOTE, title = "Aaa nota", createdAt = 1, updatedAt = 1),
        )
        assertEquals(listOf("Ángel", "Beatriz", "Zoe"), contactsList(all, "").map { it.title })
    }

    @Test fun laBusquedaNoDistingueMayusculasNiAcentos() {
        val all = listOf(contactOf(person(), "Ángel Núñez"), contactOf(person(), "Beatriz"), contactOf(person(), "Zoe"))
        assertEquals(listOf("Ángel Núñez"), contactsList(all, "angel").map { it.title })
        assertEquals(listOf("Ángel Núñez"), contactsList(all, "NUNEZ").map { it.title })
        assertEquals(listOf("Beatriz"), contactsList(all, "  bea ").map { it.title })
        assertTrue(contactsList(all, "nadie").isEmpty())
    }

    @Test fun laBusquedaNoEncuentraLaIdentidadNiOtrosTiposAunqueCoincidaElTexto() {
        val me = person().entry // «Mi identidad»
        val note = VaultEntry(id = "n", type = EntryType.NOTE, title = "Mi nota", createdAt = 1, updatedAt = 1)
        assertTrue(contactsList(listOf(me, note), "mi").isEmpty())
    }

    @Test fun laBusquedaNoMiraElContenidoDeLaTarjeta() {
        val all = listOf(contactOf(person(), "Ana", title = "Compañera"))
        assertTrue(contactsList(all, "Ana").isEmpty()) // Solo el nombre guardado.
        assertEquals(1, contactsList(all, "compa").size)
    }

    // ---- Verificado ----

    @Test fun laMarcaDiceSiEstaVerificadoYComo() {
        val p = person()
        assertEquals("sin verificar", verificationMark(contactOf(p, "Ana", viaQr = false)))
        assertEquals("verificado con QR", verificationMark(contactOf(p, "Ana", viaQr = true)))
        assertEquals("verificado con la huella", verificationMark(contactOf(p, "Ana").let { markVerified(it, now) }))
        assertEquals("verificado", verificationMark(contactOf(p, "Ana").copy(verified = true, verifiedBy = "")))
    }

    @Test fun marcarComoVerificadoGuardaLaHuellaComoMetodoYLaFecha() {
        val unverified = contactOf(person(), "Ana")
        val later = now + 5_000
        val v = markVerified(unverified, later)
        assertTrue(v.verified)
        assertEquals("fingerprint", v.verifiedBy)
        assertEquals(later, v.verifiedAt)
        assertEquals(later, v.updatedAt)
        assertEquals(unverified.card, v.card)
        assertEquals(unverified.id, v.id)
        assertEquals(unverified.title, v.title)
    }

    @Test fun marcarComoVerificadoUnoYaVerificadoPorQrNoCambiaNada() {
        val byQr = contactOf(person(), "Ana", viaQr = true)
        assertSame(byQr, markVerified(byQr, now + 1))
    }

    @Test fun soloSeVerificaUnContacto() {
        val me = person().entry
        try {
            markVerified(me, now)
            org.junit.Assert.fail("Debería rechazar la identidad")
        } catch (e: IllegalArgumentException) {
            // bien
        }
    }

    @Test fun cambiarElNombreNoToucaLaTarjetaNiLaVerificacion() {
        val byQr = contactOf(person(), "Ana", viaQr = true)
        val renamed = renamedContact(byQr, "Ana García", now + 1)
        assertEquals("Ana García", renamed.title)
        assertEquals(now + 1, renamed.updatedAt)
        assertEquals(byQr.card, renamed.card)
        assertTrue(renamed.verified)
        assertEquals("qr", renamed.verifiedBy)
        assertEquals(byQr.verifiedAt, renamed.verifiedAt)
    }

    // ---- Mensajes de error ----

    @Test fun laContrasenaMaestraMalaDiceLoMismoQueLaPantallaDeDesbloqueo() {
        assertEquals("Contraseña incorrecta", masterFailureMessage(CryptoFailure("lo que sea")))
        assertEquals("No se pudo abrir la bóveda", masterFailureMessage(java.io.IOException("x")))
        assertEquals("La bóveda está cerrada", VAULT_CLOSED)
    }

    @Test fun guardarSinExitoDiceElMotivoYGuardarBienNoDiceNada() {
        assertNull(saveFailureMessage(Result.success(true)))
        assertEquals(VAULT_CLOSED, saveFailureMessage(Result.success(false)))
        assertEquals("No se pudo guardar", saveFailureMessage(Result.failure(java.io.IOException("disco lleno"))))
        assertTrue(saveFailureMessage(Result.failure(IllegalStateException("x")))!!.contains("contraseña maestra"))
    }

    @Test fun elMensajeDeUnaTarjetaIlegibleEsElDeLaTarjetaOUnoGenerico() {
        assertEquals("La tarjeta está dañada o incompleta", cardReadErrorMessage(CryptoFailure("La tarjeta está dañada o incompleta")))
        assertEquals("No se pudo leer la tarjeta", cardReadErrorMessage(IllegalArgumentException("x")))
        // Un mensaje vacío de CryptoFailure no deja la pantalla sin texto.
        assertEquals("No se pudo leer la tarjeta", cardReadErrorMessage(CryptoFailure("")))
    }
}
