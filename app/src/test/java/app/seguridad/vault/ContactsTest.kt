package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.pk.Card
import app.seguridad.crypto.pk.Identity
import app.seguridad.crypto.pk.NOT_FOR_ME
import app.seguridad.crypto.pk.cardBytes
import app.seguridad.crypto.pk.encryptMessage
import app.seguridad.crypto.pk.parseCard
import app.seguridad.health.vaultHealth
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * La identidad y los contactos como entradas de la bóveda: la entrada de la identidad, la de un contacto, quién firma
 * lo recibido (`senderOf`) y qué hacer con una tarjeta que llega (`checkIncomingCard`).
 *
 * Los tests no guardan ninguna semilla en un array: las identidades salen de `newIdentityEntry` y se abren con
 * `openIdentity().use {}`.
 */
class ContactsTest {
    private val now = 1_790_000_000_000L

    /** Una persona: su entrada de identidad, para firmar y cifrar como ella. */
    private class Person(val entry: VaultEntry) {
        fun <T> open(block: (Identity) -> T): T = entry.openIdentity().use(block)

        fun card(name: String): Card = parseCard(open { it.cardBytes(name) })
    }

    private fun person() = Person(newIdentityEntry(now))

    private fun contactOf(p: Person, name: String, viaQr: Boolean = false): VaultEntry {
        val bytes = p.open { it.cardBytes(name) }
        return newContactEntry(parseCard(bytes), bytes, now, viaQr)
    }

    /**
     * Una tarjeta bien firmada por [signer] (su Ed25519) con la clave X-Wing [kem] y el nombre [name], armada a mano
     * como en `CardTest`: la forma de tener «las mismas claves Ed25519 con otra X-Wing» o «la misma X-Wing con otra
     * Ed25519», que `cardBytes` no da.
     */
    private fun forged(signer: Person, kem: ByteArray, name: String): Card {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val body = byteArrayOf(0x53, 0x47, 0x02, 0x01, 0x01) + signer.open { it.edPublic } + kem + byteArrayOf(nameBytes.size.toByte()) + nameBytes
        return parseCard(body + signer.open { it.sign("seguridad-card-v1", body) })
    }

    private fun Person.ed() = open { it.edPublic }

    private fun Person.kem() = open { it.kemPublic }

    // ---- La entrada de la identidad ----

    @Test fun laIdentidadNuevaTieneUnaSemillaDe32BytesEnBase64() {
        val e = newIdentityEntry(now)
        assertEquals(EntryType.IDENTITY, e.type)
        assertEquals("Mi identidad", e.title)
        assertEquals(now, e.createdAt)
        assertEquals(now, e.updatedAt)
        assertEquals(32, Base64.getDecoder().decode(e.identitySeed).size)
        assertTrue(e.identitySeed.endsWith("=")) // Base64 estándar con relleno.
        assertEquals("", e.card)
        assertFalse(e.verified)
        assertTrue(e.attachments.isEmpty())
        assertTrue(e.passwordHistory.isEmpty())
        val other = newIdentityEntry(now)
        assertTrue(e.id != other.id)
        assertTrue(e.identitySeed != other.identitySeed)
    }

    // ---- Renovar la identidad (A6) ----

    @Test fun renewedIdentityHasNewKeysSameId() {
        val old = newIdentityEntry(now).copy(username = "Yo")
        val later = now + 5_000
        val renewed = renewedIdentityEntry(old, later)
        assertEquals(old.id, renewed.id)
        assertEquals(old.createdAt, renewed.createdAt)
        assertEquals(later, renewed.updatedAt)
        assertEquals(EntryType.IDENTITY, renewed.type)
        assertEquals("Yo", renewed.username) // The name on the card is kept.
        assertEquals(32, Base64.getDecoder().decode(renewed.identitySeed).size)
        assertTrue(renewed.identitySeed != old.identitySeed)
        val before = old.openIdentity().use { it.edPublic to it.kemPublic }
        val after = renewed.openIdentity().use { it.edPublic to it.kemPublic }
        assertFalse(before.first.contentEquals(after.first)) // Ed25519 changes...
        assertFalse(before.second.contentEquals(after.second)) // ...and so does X-Wing.
        // The old entry is not touched.
        assertEquals(now, old.updatedAt)
    }

    @Test fun renewedIdentityTakesItsSeedFromTheRandomGivenAndDerivesTheSameWayAsACreatedOne() {
        val old = newIdentityEntry(now)
        val fixed = java.security.SecureRandom.getInstance("SHA1PRNG").apply { setSeed(7L) }
        val a = renewedIdentityEntry(old, now + 1, fixed)
        val again = java.security.SecureRandom.getInstance("SHA1PRNG").apply { setSeed(7L) }
        val b = renewedIdentityEntry(old, now + 2, again)
        assertEquals(a.identitySeed, b.identitySeed)
        // The same seed as a new identity of that random gives the same keys: nothing but the standard derivation.
        val created = newIdentityEntry(now, java.security.SecureRandom.getInstance("SHA1PRNG").apply { setSeed(7L) })
        assertEquals(created.identitySeed, a.identitySeed)
        assertArrayEquals(created.openIdentity().use { it.edPublic }, a.openIdentity().use { it.edPublic })
    }

    @Test fun renewingSomethingThatIsNotAnIdentityIsRefused() {
        val contact = contactOf(person(), "Ana")
        assertThrows(CryptoFailure::class.java) { renewedIdentityEntry(contact, now) }
    }

    @Test fun oldMessageCannotBeOpenedAfterRenew() {
        val old = newIdentityEntry(now)
        val sender = person()
        val oldCard = old.openIdentity().use { parseCard(it.cardBytes("Yo")) }
        val toOld = sender.open { encryptMessage("hola", oldCard, it) }
        // Before renewing it opens.
        assertTrue(openReceivedText(toOld, listOf(old)) is ReceivedOutcome.Opened)
        val renewed = renewedIdentityEntry(old, now + 1)
        val outcome = openReceivedText(toOld, listOf(renewed)) // Does not throw.
        assertTrue(outcome is ReceivedOutcome.Failed)
        assertEquals(ReceivedOutcome.Failed(NOT_FOR_ME), outcome)
        // The old identity's card is not the new one's.
        val newCard = renewed.openIdentity().use { parseCard(it.cardBytes("Yo")) }
        assertFalse(newCard.edPublic.contentEquals(oldCard.edPublic))
    }

    @Test fun openIdentityDaLasMismasClavesYUnaIdentidadNuevaCadaVez() {
        val e = newIdentityEntry(now)
        val a = e.openIdentity()
        val b = e.openIdentity()
        assertNotSame(a, b)
        assertArrayEquals(a.edPublic, b.edPublic)
        assertArrayEquals(a.kemPublic, b.kemPublic)
        a.close()
        // Cerrar la de uno no cierra la del otro.
        assertEquals(64, b.sign("etiqueta", byteArrayOf(1)).size)
        b.close()
    }

    @Test fun unaIdentidadCerradaLanzaIllegalStateException() {
        val id = newIdentityEntry(now).openIdentity()
        id.close()
        assertThrows(IllegalStateException::class.java) { id.sign("etiqueta", byteArrayOf(1)) }
        assertThrows(IllegalStateException::class.java) { id.kemKeyPair() }
        assertThrows(IllegalStateException::class.java) { id.fileSigner("etiqueta") }
        val closedByUse = newIdentityEntry(now).openIdentity().also { it.use { } }
        assertThrows(IllegalStateException::class.java) { closedByUse.sign("etiqueta", byteArrayOf(1)) }
    }

    @Test fun openIdentityConUnaSemillaRotaLanzaCryptoFailure() {
        val e = newIdentityEntry(now)
        listOf(
            e.copy(identitySeed = ""),
            e.copy(identitySeed = "esto no es base64!"),
            e.copy(identitySeed = Base64.getEncoder().encodeToString(ByteArray(31))),
            e.copy(identitySeed = Base64.getEncoder().encodeToString(ByteArray(33))),
            e.copy(type = EntryType.NOTE),
        ).forEach { assertThrows(CryptoFailure::class.java) { it.openIdentity() } }
    }

    @Test fun identityDevuelveLaPrimeraONull() {
        val a = newIdentityEntry(now)
        val b = newIdentityEntry(now)
        assertNull(emptyList<VaultEntry>().identity())
        assertNull(listOf(contactOf(person(), "Ana")).identity())
        assertSame(a, listOf(contactOf(person(), "Ana"), a, b).identity())
    }

    // ---- La entrada de un contacto ----

    @Test fun unContactoPorTextoNoEstaVerificadoYPorQrSi() {
        val ana = person()
        val bytes = ana.open { it.cardBytes("Ana") }
        val card = parseCard(bytes)

        val text = newContactEntry(card, bytes, now, viaQr = false)
        assertEquals(EntryType.CONTACT, text.type)
        assertEquals("Ana", text.title)
        assertFalse(text.verified)
        assertEquals(0L, text.verifiedAt)
        assertEquals("", text.verifiedBy)

        val qr = newContactEntry(card, bytes, now + 5, viaQr = true)
        assertTrue(qr.verified)
        assertEquals(now + 5, qr.verifiedAt)
        assertEquals("qr", qr.verifiedBy)

        listOf(text, qr).forEach {
            assertEquals("", it.identitySeed)
            assertArrayEquals(bytes, Base64.getDecoder().decode(it.card))
            assertEquals(card, it.contactCard())
            assertTrue(it.attachments.isEmpty())
        }
    }

    @Test fun unContactoSinNombreSeLlamaContacto() {
        val p = person()
        assertEquals("Contacto", contactOf(p, "").title)
    }

    @Test fun newContactEntryRechazaUnaTarjetaQueNoEsLaDeSusBytes() {
        val ana = person()
        val bytes = ana.open { it.cardBytes("Ana") }
        val other = person().card("Luis")
        assertThrows(CryptoFailure::class.java) { newContactEntry(other, bytes, now, viaQr = true) }
        assertThrows(CryptoFailure::class.java) { newContactEntry(parseCard(bytes), bytes.copyOf(bytes.size - 1), now, viaQr = false) }
    }

    @Test fun contactCardNuncaDevuelveUnaTarjetaParcial() {
        val e = contactOf(person(), "Ana")
        val bytes = Base64.getDecoder().decode(e.card)
        val damagedAt = { i: Int -> bytes.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() } }
        listOf(
            e.copy(card = ""),
            e.copy(card = "no es base64!"),
            e.copy(card = Base64.getEncoder().encodeToString(bytes.copyOf(bytes.size - 1))),
            e.copy(card = Base64.getEncoder().encodeToString(damagedAt(10))), // Una clave cambiada: la firma no cuadra.
            e.copy(card = Base64.getEncoder().encodeToString(damagedAt(bytes.size - 1))), // La firma.
            e.copy(card = Base64.getEncoder().encodeToString(damagedAt(2))), // La cabecera.
        ).forEach { assertThrows(CryptoFailure::class.java) { it.contactCard() } }
    }

    // ---- Quién firma lo recibido ----

    @Test fun senderOfDistingueYoContactoClaveDesconocidaYSinFirma() {
        val me = Person(newIdentityEntry(now))
        val ana = person()
        val luis = person()
        val anaEntry = contactOf(ana, "Ana")
        val entries = listOf(contactOf(luis, "Luis"), me.entry, anaEntry)

        assertEquals(Sender.Me, senderOf(me.ed(), entries))
        assertEquals(Sender.Contact(anaEntry), senderOf(ana.ed(), entries))
        assertEquals(Sender.UnknownKey, senderOf(person().ed(), entries))
        assertEquals(Sender.Unsigned, senderOf(null, entries))
        assertEquals(Sender.UnknownKey, senderOf(ByteArray(7), entries)) // Una clave que no mide 32 bytes no es de nadie.
        assertEquals(Sender.UnknownKey, senderOf(ana.ed(), emptyList()))
        assertEquals(Sender.Unsigned, senderOf(null, emptyList()))
    }

    @Test fun meTienePrioridadSobreContact() {
        val me = Person(newIdentityEntry(now))
        // Alguien se guardó mi tarjeta como contacto (o importó una copia que la trae).
        val contactWithMyKeys = contactOf(me, "Yo mismo")
        assertEquals(Sender.Me, senderOf(me.ed(), listOf(contactWithMyKeys, me.entry)))
    }

    @Test fun conDosContactosConLaMismaClaveGanaElPrimero() {
        val ana = person()
        val first = contactOf(ana, "Ana")
        val second = contactOf(ana, "Ana (otra vez)")
        assertSame(first, (senderOf(ana.ed(), listOf(first, second)) as Sender.Contact).entry)
        assertSame(second, (senderOf(ana.ed(), listOf(second, first)) as Sender.Contact).entry)
    }

    @Test fun senderPrefersVerifiedThenNewest() {
        // A16a: two contacts with the same Ed25519. The verified one wins, whatever the order or the dates.
        val ana = person()
        val verifiedOld = contactOf(ana, "Ana").copy(verified = true, updatedAt = now - 1_000)
        val unverifiedNew = contactOf(ana, "Ana (nueva)").copy(verified = false, updatedAt = now + 1_000)
        for (list in listOf(listOf(verifiedOld, unverifiedNew), listOf(unverifiedNew, verifiedOld))) {
            val s = senderOf(ana.ed(), list) as Sender.Contact
            assertSame(verifiedOld, s.entry)
            assertEquals(2, s.sameKeyCount)
        }
        // Both alike: the newest (higher updatedAt) wins, whatever the order.
        val older = contactOf(ana, "Ana").copy(updatedAt = now - 1_000)
        val newer = contactOf(ana, "Ana (otra)").copy(updatedAt = now + 1_000)
        assertSame(newer, (senderOf(ana.ed(), listOf(older, newer)) as Sender.Contact).entry)
        assertSame(newer, (senderOf(ana.ed(), listOf(newer, older)) as Sender.Contact).entry)
        val bothVerifiedOld = older.copy(verified = true)
        val bothVerifiedNew = newer.copy(verified = true)
        assertSame(bothVerifiedNew, (senderOf(ana.ed(), listOf(bothVerifiedNew, bothVerifiedOld)) as Sender.Contact).entry)
        assertSame(bothVerifiedNew, (senderOf(ana.ed(), listOf(bothVerifiedOld, bothVerifiedNew)) as Sender.Contact).entry)
        // The count: only readable contacts with that Ed25519 (not another person, not my identity, not a note).
        val me = Person(newIdentityEntry(now))
        val entries = listOf(older, contactOf(person(), "Luis"), me.entry, newer, contactOf(ana, "Nota").copy(type = EntryType.NOTE))
        assertEquals(2, sameKeyCount(ana.ed(), entries))
        assertEquals(1, sameKeyCount(ana.ed(), listOf(older)))
        assertEquals(0, sameKeyCount(person().ed(), entries))
        assertEquals(2, (senderOf(ana.ed(), entries) as Sender.Contact).sameKeyCount)
        // One contact with that key: count 1 (no warning).
        assertEquals(1, (senderOf(ana.ed(), listOf(older)) as Sender.Contact).sameKeyCount)
    }

    @Test fun senderOfSaltaLosContactosIlegiblesYLaIdentidadRota() {
        val ana = person()
        val good = contactOf(ana, "Ana")
        val broken = contactOf(person(), "Roto").copy(card = "AAAA")
        val notAContact = contactOf(person(), "Nota").copy(type = EntryType.NOTE) // Solo cuentan las entradas CONTACT.
        val brokenIdentity = newIdentityEntry(now).copy(identitySeed = "AAAA")
        val entries = listOf(broken, brokenIdentity, notAContact, good)
        assertEquals(Sender.Contact(good), senderOf(ana.ed(), entries))
        assertEquals(Sender.UnknownKey, senderOf(person().ed(), entries))
        // Una entrada de tipo nota con la tarjeta de otra persona no la convierte en contacto.
        val other = person()
        assertEquals(Sender.UnknownKey, senderOf(other.ed(), listOf(contactOf(other, "Otro").copy(type = EntryType.NOTE))))
    }

    // ---- Una tarjeta que llega ----

    @Test fun checkIncomingCardDaNewSameYKeysChanged() {
        val ana = person()
        val anaEntry = contactOf(ana, "Ana")
        val entries = listOf(anaEntry, contactOf(person(), "Luis"))

        val fresh = person().card("Marta")
        assertEquals(CardCheck.New(fresh), checkIncomingCard(fresh, entries))

        assertEquals(CardCheck.Same(anaEntry), checkIncomingCard(ana.card("Ana"), entries))
        // Las mismas claves con otro nombre propuesto siguen siendo el mismo contacto.
        assertEquals(CardCheck.Same(anaEntry), checkIncomingCard(ana.card("Ana García"), entries))

        // «Ana» con otras claves: las claves de este contacto han cambiado.
        val impostor = person().card("Ana")
        assertEquals(CardCheck.KeysChanged(anaEntry, impostor), checkIncomingCard(impostor, entries))
    }

    @Test fun elNombreSeComparaNormalizado() {
        val anaEntry = contactOf(person(), "Ana")
        val entries = listOf(anaEntry)
        listOf("ana", "ANA", "  Ana  ", "www.ana.com").forEach {
            val card = person().card(it)
            assertEquals(it, CardCheck.KeysChanged(anaEntry, card), checkIncomingCard(card, entries))
        }
        val different = person().card("Anabel")
        assertEquals(CardCheck.New(different), checkIncomingCard(different, entries))
    }

    @Test fun unContactoRenombradoSeReconocePorSuNombreOPorElDeSuTarjeta() {
        val anaEntry = contactOf(person(), "Ana").copy(title = "Ana del trabajo")
        val byCardName = person().card("Ana")
        assertEquals(CardCheck.KeysChanged(anaEntry, byCardName), checkIncomingCard(byCardName, listOf(anaEntry)))
        val byTitle = person().card("Ana del trabajo")
        assertEquals(CardCheck.KeysChanged(anaEntry, byTitle), checkIncomingCard(byTitle, listOf(anaEntry)))
    }

    @Test fun losNombresVaciosNoSeParecenEntreSi() {
        val sinNombre = contactOf(person(), "") // Su título es «Contacto», y la tarjeta no tiene nombre.
        val other = person().card("")
        assertEquals(CardCheck.New(other), checkIncomingCard(other, listOf(sinNombre)))
    }

    @Test fun lasMismasClavesEd25519ConOtraXWingSonKeysChangedAunqueElNombreDifiera() {
        val ana = person()
        val anaEntry = contactOf(ana, "Ana")
        val newKem = person().kem()
        val card = forged(ana, newKem, "Otro nombre")
        assertArrayEquals(ana.ed(), card.edPublic)
        assertEquals(CardCheck.KeysChanged(anaEntry, card), checkIncomingCard(card, listOf(contactOf(person(), "Luis"), anaEntry)))
    }

    @Test fun laXWingDeUnContactoConOtraEd25519EsSharedKem() {
        val ana = person()
        val anaEntry = contactOf(ana, "Ana")
        val mallory = person()
        // Mallory firma, con su Ed25519, una tarjeta que lleva la clave X-Wing de Ana: unknown key-share.
        val card = forged(mallory, ana.kem(), "Mallory")
        assertArrayEquals(ana.kem(), card.kemPublic)
        assertEquals(CardCheck.SharedKem(anaEntry, card), checkIncomingCard(card, listOf(anaEntry)))
    }

    @Test fun laXWingDeLaPropiaIdentidadConOtraEd25519EsSharedKem() {
        val me = Person(newIdentityEntry(now))
        val mallory = person()
        val card = forged(mallory, me.kem(), "Mallory")
        assertEquals(CardCheck.SharedKem(me.entry, card), checkIncomingCard(card, listOf(me.entry)))
    }

    @Test fun laPropiaTarjetaEsSameConLaEntradaDeLaIdentidad() {
        val me = Person(newIdentityEntry(now))
        assertEquals(CardCheck.Same(me.entry), checkIncomingCard(me.card("Yo"), listOf(me.entry)))
    }

    @Test fun elOrdenEsSameLuegoSharedKemLuegoKeysChangedLuegoNew() {
        val ana = person()
        val luis = person()
        val anaEntry = contactOf(ana, "Ana")
        val luisEntry = contactOf(luis, "Luis")
        val entries = listOf(anaEntry, luisEntry)

        // Same gana a todo: las dos claves de Luis, con el nombre de Ana.
        assertEquals(CardCheck.Same(luisEntry), checkIncomingCard(luis.card("Ana"), entries))

        // SharedKem gana a KeysChanged por nombre: la X-Wing de Luis, con el nombre de Ana y otra Ed25519.
        val mallory = person()
        val sharedWithName = forged(mallory, luis.kem(), "Ana")
        assertEquals(CardCheck.SharedKem(luisEntry, sharedWithName), checkIncomingCard(sharedWithName, entries))

        // SharedKem gana a KeysChanged por Ed25519: la X-Wing de Luis con la Ed25519 de Ana.
        val mixed = forged(ana, luis.kem(), "Quien sea")
        assertEquals(CardCheck.SharedKem(luisEntry, mixed), checkIncomingCard(mixed, entries))

        // Sin ninguna de las dos claves, y sin nombre conocido: New.
        val nobody = person().card("Nadie")
        assertEquals(CardCheck.New(nobody), checkIncomingCard(nobody, entries))
    }

    @Test fun checkIncomingCardSaltaLosContactosIlegiblesYNoLanza() {
        val card = person().card("Ana")
        // Sin tarjeta que leer no hay claves que comparar: ni su título «Ana» cuenta.
        val broken = contactOf(person(), "Ana").copy(card = "AAAA", id = "roto")
        val brokenIdentity = newIdentityEntry(now).copy(identitySeed = "AAAA")
        assertEquals(CardCheck.New(card), checkIncomingCard(card, listOf(broken, brokenIdentity)))
    }

    // ---- Salud y búsqueda ----

    @Test fun vaultHealthNoIncluyeLaIdentidadNiLosContactos() {
        val weak = VaultEntry("l1", EntryType.LOGIN, "Banco", password = "123456", createdAt = 0, updatedAt = 0)
        val entries = listOf(weak, newIdentityEntry(now), contactOf(person(), "Ana"), contactOf(person(), "Luis", viaQr = true))
        assertEquals(listOf("l1"), vaultHealth(entries, now).map { it.entryId })
        assertTrue(vaultHealth(entries.drop(1), now).isEmpty())
    }

    @Test fun laBusquedaEncuentraALosContactosPorSuNombre() {
        val ana = contactOf(person(), "Ana García")
        val luis = contactOf(person(), "Luis")
        val found = listOf(ana, luis, newIdentityEntry(now)).query(EntryFilter("garcia"))
        assertEquals(listOf(ana), found)
        assertEquals(listOf(luis), listOf(ana, luis).query(EntryFilter("", type = EntryType.CONTACT)).filter { it.title == "Luis" })
    }
}
