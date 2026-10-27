package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.pk.Card
import app.seguridad.crypto.pk.Identity
import app.seguridad.crypto.pk.newIdentitySeed
import app.seguridad.crypto.pk.parseCard
import app.seguridad.crypto.secureRandom
import app.seguridad.health.normalizeName
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import java.util.UUID

/*
 * La identidad y los contactos como entradas de la bóveda (diseño: «Identidad y contactos en la bóveda»). Solo existen en
 * una bóveda de la versión 3. Codificación, que se lee para siempre:
 *  - `identitySeed`: la semilla de 32 bytes en Base64 estándar con relleno (`java.util.Base64`);
 *  - `card`: los bytes canónicos de la tarjeta (`cardBytes`), también en Base64 estándar con relleno.
 *
 * Todo el código de aquí es sin estado: ningún campo guarda una [Identity] ni una semilla.
 */

private const val BAD_IDENTITY = "La identidad guardada no es válida"
private const val BAD_STORED_CARD = "La tarjeta guardada del contacto no se puede leer"
private const val CARD_MISMATCH = "La tarjeta no coincide con sus bytes"
private const val QR = "qr"

private val encoder = Base64.getEncoder()
private val decoder = Base64.getDecoder()

/** La entrada de la identidad, o `null` si todavía no hay (solo puede haber una). */
fun List<VaultEntry>.identity(): VaultEntry? = firstOrNull { it.type == EntryType.IDENTITY }

/**
 * Una [Identity] nueva con la semilla de esta entrada **en cada llamada**: quien llama la cierra (`openIdentity().use { }`),
 * y cerrada, usarla lanza [IllegalStateException]. Lanza [CryptoFailure] si la entrada no es una identidad o su semilla no
 * es Base64 de 32 bytes. El array con la semilla se pone a cero antes de volver; el `String` de la entrada no se puede
 * borrar de la memoria (como las contraseñas del resto de entradas).
 */
fun VaultEntry.openIdentity(): Identity {
    if (type != EntryType.IDENTITY) throw CryptoFailure(BAD_IDENTITY)
    val seed = try {
        decoder.decode(identitySeed)
    } catch (e: IllegalArgumentException) {
        throw CryptoFailure(BAD_IDENTITY)
    }
    try {
        return Identity(seed) // Lanza CryptoFailure si no mide 32 bytes.
    } finally {
        seed.fill(0)
    }
}

/**
 * La entrada de una identidad nueva: semilla de 32 bytes de [random] (el array se pone a cero al terminar) y un id
 * propio. Su título es «Mi identidad». Guardarla es cosa de `VaultStore.upsert`, que rechaza una segunda.
 */
fun newIdentityEntry(now: Long, random: SecureRandom = secureRandom): VaultEntry {
    val seed = newIdentitySeed(random)
    try {
        return VaultEntry(
            id = UUID.randomUUID().toString(),
            type = EntryType.IDENTITY,
            title = EntryType.IDENTITY.label,
            identitySeed = encoder.encodeToString(seed),
            createdAt = now,
            updatedAt = now,
        )
    } finally {
        seed.fill(0)
    }
}

/**
 * La entrada de un contacto con la tarjeta [card], que tiene que ser la que dicen sus bytes canónicos [cardBytes] (se
 * vuelve a leer con `parseCard`, firma incluida; si no coincide, [CryptoFailure]). Queda verificado solo si [viaQr]
 * (la tarjeta se escaneó en persona): `verifiedBy` = `"qr"`. Pegada como texto, no. El título es el nombre de la tarjeta
 * («Contacto» si no tiene), que quien la guarda puede cambiar.
 */
fun newContactEntry(card: Card, cardBytes: ByteArray, now: Long, viaQr: Boolean): VaultEntry {
    if (parseCard(cardBytes) != card) throw CryptoFailure(CARD_MISMATCH)
    return VaultEntry(
        id = UUID.randomUUID().toString(),
        type = EntryType.CONTACT,
        title = card.name.ifBlank { EntryType.CONTACT.label },
        card = encoder.encodeToString(cardBytes),
        createdAt = now,
        updatedAt = now,
        verified = viaQr,
        verifiedAt = if (viaQr) now else 0,
        verifiedBy = if (viaQr) QR else "",
    )
}

/**
 * La tarjeta guardada de este contacto, **siempre con `parseCard`** (firma incluida): nunca se arma una [Card] a mano.
 * Si lo guardado ya no se lee (Base64 roto, tarjeta dañada, firma que no cuadra), lanza [CryptoFailure]; nunca da una
 * tarjeta parcial.
 */
fun VaultEntry.contactCard(): Card {
    if (type != EntryType.CONTACT) throw CryptoFailure(BAD_STORED_CARD)
    val bytes = try {
        decoder.decode(card)
    } catch (e: IllegalArgumentException) {
        throw CryptoFailure(BAD_STORED_CARD)
    }
    return parseCard(bytes)
}

/** Quién firmó lo que se acaba de abrir. */
sealed interface Sender {
    /** La firma es de mi propia identidad. */
    data object Me : Sender

    /** La firma es de este contacto (verificado o no: eso lo dice la entrada). */
    data class Contact(val entry: VaultEntry) : Sender

    /** La firma es válida, pero de una clave que no es mía ni de ningún contacto. */
    data object UnknownKey : Sender

    /** No lleva firma. */
    data object Unsigned : Sender
}

/**
 * Quién es el firmante con la Ed25519 [senderEd]: `null` es [Sender.Unsigned]; si coincide con mi identidad, [Sender.Me]
 * (tiene prioridad sobre un contacto con mis mismas claves); si coincide con un contacto, [Sender.Contact] (con dos con
 * la misma Ed25519, el primero de la lista); si no, [Sender.UnknownKey]. Un contacto cuya tarjeta no se lee, o una identidad
 * rota, se saltan: nunca lanza. Las claves son públicas, así que se comparan sin tiempo constante.
 */
fun senderOf(senderEd: ByteArray?, entries: List<VaultEntry>): Sender {
    if (senderEd == null) return Sender.Unsigned
    if (ownKeys(entries)?.first?.contentEquals(senderEd) == true) return Sender.Me
    for (entry in entries) {
        val card = readableCard(entry) ?: continue
        if (card.edPublic.contentEquals(senderEd)) return Sender.Contact(entry)
    }
    return Sender.UnknownKey
}

/** Qué hacer con una tarjeta que llega (la lógica; los avisos son de la pantalla). */
sealed interface CardCheck {
    /** Nadie la tiene todavía: se puede añadir. */
    data class New(val card: Card) : CardCheck

    /**
     * Las dos claves son las de [entry]: ya está. Si [entry] es la identidad ([EntryType.IDENTITY]), es mi propia tarjeta.
     */
    data class Same(val entry: VaultEntry) : CardCheck

    /**
     * La clave X-Wing de la tarjeta es la de [entry] (un contacto o mi identidad) y la Ed25519 no: una de las dos tarjetas
     * copia la clave de cifrado de la otra (unknown key-share), y no se sabe cuál, porque la copia pudo llegar antes. Aviso
     * fuerte y neutro; no se debe añadir. La firma del remitente cubre la Ed25519 y la X-Wing del destinatario, así que
     * la copia no sirve para que lo firmado para una se tome por firmado para la otra.
     */
    data class SharedKem(val entry: VaultEntry, val card: Card) : CardCheck

    /**
     * Las claves de [entry] han cambiado: la tarjeta lleva su misma Ed25519 con otra X-Wing, o su mismo nombre
     * normalizado con otras claves.
     */
    data class KeysChanged(val entry: VaultEntry, val card: Card) : CardCheck
}

/**
 * Compara [card] con mi identidad y con los contactos de [entries], en este orden de evaluación:
 *  1. [CardCheck.Same]: las dos claves son las de una entrada (un contacto o mi identidad);
 *  2. [CardCheck.SharedKem]: la X-Wing es la de una entrada (un contacto o mi identidad) y la Ed25519 no;
 *  3. [CardCheck.KeysChanged]: la Ed25519 es la de un contacto y la X-Wing no (aunque el nombre difiera), o el nombre
 *     normalizado ([normalizeName], sobre el título del contacto o el nombre de su tarjeta) es el de un contacto con otras
 *     claves. Un nombre vacío no coincide con nada;
 *  4. [CardCheck.New].
 * En cada paso gana la primera entrada de la lista. Los contactos cuya tarjeta no se lee y una identidad rota se saltan.
 * Toda la comparación de claves es con `contentEquals`: son públicas.
 */
fun checkIncomingCard(card: Card, entries: List<VaultEntry>): CardCheck {
    val ownEntry = entries.identity()
    val own = ownKeys(entries)?.let { (ed, kem) -> Known(ownEntry!!, ed, kem, null) }
    val known = entries.mapNotNull { e ->
        if (e === ownEntry) own else readableCard(e)?.let { Known(e, it.edPublic, it.kemPublic, it.name) }
    }
    val contacts = known.filter { it !== own }

    known.firstOrNull { it.ed.contentEquals(card.edPublic) && it.kem.contentEquals(card.kemPublic) }
        ?.let { return CardCheck.Same(it.entry) }
    known.firstOrNull { it.kem.contentEquals(card.kemPublic) }?.let { return CardCheck.SharedKem(it.entry, card) }
    contacts.firstOrNull { it.ed.contentEquals(card.edPublic) }?.let { return CardCheck.KeysChanged(it.entry, card) }
    val name = comparable(card.name)
    if (name.isNotEmpty()) {
        contacts.firstOrNull { comparable(it.entry.title) == name || it.cardName?.let(::comparable) == name }
            ?.let { return CardCheck.KeysChanged(it.entry, card) }
    }
    return CardCheck.New(card)
}

private class Known(val entry: VaultEntry, val ed: ByteArray, val kem: ByteArray, val cardName: String?)

private fun comparable(name: String): String = normalizeName(Normalizer.normalize(name, Normalizer.Form.NFC))

/** La Ed25519 y la X-Wing públicas de mi identidad, o `null` si no hay o no se lee. Cierra la [Identity] que abre. */
private fun ownKeys(entries: List<VaultEntry>): Pair<ByteArray, ByteArray>? {
    val entry = entries.identity() ?: return null
    return try {
        entry.openIdentity().use { it.edPublic to it.kemPublic }
    } catch (e: CryptoFailure) {
        null
    }
}

/** La tarjeta de [entry] si es un contacto y se lee; `null` en cualquier otro caso. */
private fun readableCard(entry: VaultEntry): Card? {
    if (entry.type != EntryType.CONTACT) return null
    return try {
        entry.contactCard()
    } catch (e: CryptoFailure) {
        null
    }
}
