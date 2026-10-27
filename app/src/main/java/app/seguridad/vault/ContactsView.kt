package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.pk.Card
import app.seguridad.crypto.pk.MAX_NAME_BYTES
import app.seguridad.crypto.pk.cardBytes
import app.seguridad.crypto.pk.cardQr
import app.seguridad.crypto.pk.cardText
import app.seguridad.crypto.pk.fingerprint
import app.seguridad.crypto.pk.hasForbiddenNameCharacter
import app.seguridad.crypto.pk.strictUtf8
import java.nio.charset.CharacterCodingException
import java.text.Normalizer

/*
 * La lógica de la pestaña «Contactos» que no es de pantalla (así se prueba en jvm-check): el aviso de cada tarjeta que
 * llega, la validación del nombre, el formato de la huella, la lista de contactos, la marca de verificado y los
 * mensajes de error. Las pantallas (`ui/screens/Contacts*.kt`, `IdentityScreen.kt`) solo dibujan lo que sale de aquí.
 *
 * Los nombres que salen de una tarjeta los elige otra persona: aquí son datos y la pantalla los dibuja siempre como
 * texto plano. Ningún texto de esta pestaña promete lo que la app no puede prometer (ni «imposible de descifrar», ni
 * «a prueba de cuánticos», ni «validez legal», ni «como Signal»): `ContactsViewTest` lo comprueba.
 */

// ---- Textos fijos ----

/** Lo que dice la pantalla de la identidad (una línea, del dictamen jurídico). */
const val IDENTITY_EXPLANATION =
    "Tu identidad sirve para que tus contactos te cifren mensajes y para firmar. Se guarda solo en tu bóveda cifrada y en tus copias."

/** Lo que no protege la identidad (dicho siempre, sin exagerar): si se pierde, lo antiguo se puede leer. */
const val IDENTITY_RISK = "Si alguien consiguiera tu identidad, podría leer lo que te enviaron antes y firmar en tu nombre."

/** Una tarjeta pegada o escaneada que no cabe (se rechaza sin guardarla ni decodificarla). */
const val TOO_BIG_CARD = "La tarjeta es demasiado grande"

/** Primer aviso al borrar la identidad (el del diseño). */
const val DELETE_IDENTITY_WARNING =
    "Los contactos tendrán que volver a añadirte y lo que te enviaron a esta identidad ya no se podrá abrir. Esto no se puede deshacer."

/** Segunda confirmación al borrar la identidad. */
const val DELETE_IDENTITY_CONFIRMATION =
    "Se borrará tu identidad de esta bóveda y no hay forma de recuperarla. Las copias que ya hayas exportado conservan la identidad que tenían."

/** La bóveda es de la versión 2 (abierta con la huella): hace falta la maestra una vez antes de guardar identidad o contactos. */
const val NEEDS_MASTER_NOTICE =
    "Para guardar tu identidad y tus contactos hace falta tu contraseña maestra, una sola vez. La bóveda se abrió con la huella."

/** Debajo de la huella de un contacto. */
const val FINGERPRINT_HELP = "Compárala con la que ve tu contacto en su móvil"

/** Lo que dice el detalle de un contacto sin verificar. */
const val UNVERIFIED_NOTICE =
    "Sin verificar: no sabes si esta tarjeta es de quien dice. Compara la huella con tu contacto en persona o por llamada y márcalo cuando coincida."

/**
 * Los nombres se pueden imitar (con letras de otro alfabeto que se ven igual): el aviso de «claves cambiadas» por nombre no
 * lo detecta. La defensa es la huella y la marca «sin verificar».
 */
const val SIMILAR_NAME_WARNING =
    "Un nombre se puede imitar con letras parecidas: lo que identifica a un contacto es su huella, y la marca «sin verificar» importa."

/** La pregunta antes de marcar a un contacto como verificado. */
const val VERIFY_QUESTION =
    "¿La huella que ves aquí es idéntica, cifra a cifra, a la que ve tu contacto en su móvil? Márcalo como verificado solo si la has comparado en persona o por llamada."

/** La bóveda se bloqueó mientras se guardaba (o ya estaba bloqueada). */
const val VAULT_CLOSED = "La bóveda está cerrada"

private const val UNKNOWN_CARD = "No se pudo leer la tarjeta"
private const val WRONG_MASTER = "Contraseña incorrecta"
private const val NOT_OPENED = "No se pudo abrir la bóveda"
private const val NOT_SAVED = "No se pudo guardar"
private const val NOT_SAVED_STATE = "No se pudo guardar: hace falta la contraseña maestra o ya existe una identidad"
private const val NAME_EMPTY = "Escribe un nombre"
private const val NAME_NOT_TEXT = "El nombre no es texto válido"
private const val NAME_FORBIDDEN = "El nombre tiene caracteres de control, de dirección del texto, invisibles o no permitidos"
private const val NEW_CONTACT_NAME = "Contacto nuevo"
private const val NEW_SUFFIX = " (nuevo)"

// ---- El aviso de una tarjeta que llega ----

/** Cómo de grave es un aviso: la pantalla elige el color. */
enum class NoticeLevel { INFO, WARNING, DANGER }

/**
 * Lo que la pantalla enseña de una tarjeta que llega. [title] y [body] van encima de la huella y [footer] debajo (puede
 * estar vacío). [saveLabel] es el texto del único botón que guarda, o `null` si **no se puede guardar** (sin botón).
 */
data class CardNotice(
    val level: NoticeLevel,
    val title: String,
    val body: String,
    val saveLabel: String?,
    val footer: String = "",
)

/**
 * El aviso de [check] (la comparación de la tarjeta con mi identidad y los contactos, `checkIncomingCard`). [viaQr] es
 * que la tarjeta se escaneó (queda verificada si se guarda) y no que se pegó (queda sin verificar).
 *
 * - [CardCheck.New]: se puede guardar.
 * - [CardCheck.Same]: «Ya lo tienes: <nombre guardado>», o «Es tu propia tarjeta» si es la de mi identidad. No se guarda.
 * - [CardCheck.SharedKem]: aviso fuerte y **sin botón para guardar**: dos tarjetas legítimas nunca comparten la clave de
 *   cifrado. El aviso es **neutro**: no dice cuál de las dos es la falsa, porque quien publica una tarjeta con una clave
 *   ajena puede haber llegado antes, y entonces la que se rechaza es la legítima. Desde que la firma del remitente cubre
 *   la identidad entera del destinatario, a quien copia la clave no le sirve para leer ni para hacerse pasar por nadie.
 * - [CardCheck.KeysChanged]: aviso, y solo «Guardar como contacto nuevo»: nunca sustituye al antiguo.
 *
 * Los nombres que salen de aquí son los **guardados** por el usuario (`VaultEntry.title`); el que trae la tarjeta no se usa.
 */
fun incomingCardNotice(check: CardCheck, viaQr: Boolean): CardNotice = when (check) {
    is CardCheck.New -> if (viaQr) {
        CardNotice(
            NoticeLevel.INFO,
            NEW_CONTACT_NAME,
            "Se guardará como verificado porque has escaneado su QR. Eso solo vale si lo has escaneado delante de tu contacto.",
            "Guardar contacto",
        )
    } else {
        CardNotice(
            NoticeLevel.INFO,
            NEW_CONTACT_NAME,
            "Se guardará como sin verificar.",
            "Guardar contacto",
            footer = "Si te la han enviado por un chat, compara esta huella con tu contacto en persona o por llamada.",
        )
    }
    is CardCheck.Same ->
        if (check.entry.type == EntryType.IDENTITY) {
            CardNotice(NoticeLevel.INFO, "Es tu propia tarjeta", "No hace falta añadirla: es la de tu identidad.", null)
        } else {
            CardNotice(NoticeLevel.INFO, "Ya lo tienes: ${check.entry.title}", "Esta tarjeta tiene las mismas claves que ese contacto.", null)
        }
    is CardCheck.SharedKem -> CardNotice(
        NoticeLevel.DANGER,
        "Clave de cifrado repetida",
        "Esta tarjeta usa la misma clave de cifrado que ${ownerName(check.entry)}, con otra firma. Una de las dos es falsa: " +
            "compara la huella con las dos personas, en persona o por llamada. No se guarda.",
        null,
    )
    is CardCheck.KeysChanged -> CardNotice(
        NoticeLevel.WARNING,
        "Las claves de este contacto han cambiado",
        "Esta tarjeta se parece a tu contacto «${check.entry.title}», pero trae otras claves. Puede que haya cambiado de móvil o " +
            "de identidad, o que alguien intente hacerse pasar por él. No sustituye a tu contacto: si la guardas, queda como un " +
            "contacto nuevo. Compara las dos huellas con él.",
        "Guardar como contacto nuevo",
        footer = (if (viaQr) {
            "Al haberla escaneado se guardará como verificado: solo vale si la has escaneado delante de tu contacto. "
        } else {
            "Se guardará como sin verificar hasta que compares su huella en persona o por llamada. "
        }) + SIMILAR_NAME_WARNING,
    )
}

/**
 * La huella de la entrada guardada con la que choca [check], para enseñarla junto a la de la tarjeta que llega: la del
 * contacto con las claves cambiadas ([CardCheck.KeysChanged]), o la del contacto o de mi identidad que usa la misma clave
 * de cifrado ([CardCheck.SharedKem], cuyo aviso pide comparar las dos). `null` con los demás resultados, o si la entrada
 * no se lee. Las huellas son públicas.
 */
fun savedFingerprint(check: CardCheck): String? = try {
    when (check) {
        is CardCheck.KeysChanged -> cardFingerprint(check.entry.contactCard())
        is CardCheck.SharedKem ->
            if (check.entry.type == EntryType.IDENTITY) check.entry.openIdentity().use { fingerprint(it.edPublic, it.kemPublic) }
            else cardFingerprint(check.entry.contactCard())
        is CardCheck.New, is CardCheck.Same -> null
    }
} catch (e: CryptoFailure) {
    null
}

/** El nombre con el que se habla de la entrada que comparte la clave: el guardado del contacto, o «tu identidad». */
private fun ownerName(entry: VaultEntry): String = if (entry.type == EntryType.IDENTITY) "tu identidad" else entry.title

/**
 * El nombre con el que se rellena el campo al guardar [check]: el de la tarjeta si es nueva; con las claves cambiadas, el
 * de la tarjeta más « (nuevo)» (para no tener dos contactos con el mismo nombre), si cabe en el límite. Con cualquier
 * otro resultado (que no se guarda), vacío.
 */
fun suggestedContactName(check: CardCheck): String = when (check) {
    is CardCheck.New -> check.card.name
    is CardCheck.KeysChanged -> {
        val candidate = if (check.card.name.isBlank()) NEW_CONTACT_NAME else check.card.name + NEW_SUFFIX
        if (checkContactName(candidate) is ContactName.Valid) candidate else check.card.name
    }
    is CardCheck.Same, is CardCheck.SharedKem -> ""
}

// ---- El nombre de un contacto ----

/** El resultado de [checkContactName]: el nombre ya normalizado y recortado, o el motivo (en español) de que no valga. */
sealed interface ContactName {
    data class Valid(val name: String) : ContactName

    data class Invalid(val message: String) : ContactName
}

/**
 * Si [raw] vale como nombre guardado de un contacto. Se normaliza a NFC y tiene que ser texto (sin sustitutos sueltos) y no
 * tener ningún carácter de la lista fija prohibida de las tarjetas (`hasForbiddenNameCharacter`, también en los extremos);
 * después se recortan los espacios de los extremos y tiene que quedar algo, de como mucho [MAX_NAME_BYTES] bytes en UTF-8
 * (bytes, no caracteres).
 * Es la misma regla que se aplica al nombre de una tarjeta al leerla.
 */
fun checkContactName(raw: String): ContactName {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
    // Antes de recortar: un carácter prohibido en los extremos (un salto de línea, un separador) se avisa, no se quita en silencio.
    try {
        strictUtf8(normalized)
    } catch (e: CharacterCodingException) {
        return ContactName.Invalid(NAME_NOT_TEXT)
    }
    if (hasForbiddenNameCharacter(normalized)) return ContactName.Invalid(NAME_FORBIDDEN)
    val name = normalized.trim()
    if (name.isEmpty()) return ContactName.Invalid(NAME_EMPTY)
    val size = strictUtf8(name).size
    if (size > MAX_NAME_BYTES) {
        return ContactName.Invalid("El nombre es demasiado largo: como máximo $MAX_NAME_BYTES bytes en UTF-8 (tiene $size)")
    }
    return ContactName.Valid(name)
}

/** [checkContactName], pero un nombre en blanco vale (y es `""`): mi tarjeta puede ir sin nombre, y quien la recibe pone el suyo. */
fun checkOwnName(raw: String): ContactName = if (raw.isBlank()) ContactName.Valid("") else checkContactName(raw)

// ---- La huella ----

/** La huella de [card] (6 grupos de 5 cifras): la que ve su dueño en su móvil. No depende del nombre. */
fun cardFingerprint(card: Card): String = fingerprint(card.edPublic, card.kemPublic)

private val FINGERPRINT_FORMAT = Regex("(\\d{5} ){5}\\d{5}")

/**
 * La huella en dos filas de tres grupos (para leerla en voz alta y que quepa en cualquier pantalla). Una cadena que no
 * tiene el formato de [fingerprint] se devuelve tal cual, en una sola fila.
 */
fun fingerprintRows(fingerprint: String): List<String> {
    if (!FINGERPRINT_FORMAT.matches(fingerprint)) return listOf(fingerprint)
    val groups = fingerprint.split(' ')
    return groups.chunked(3).map { it.joinToString(" ") }
}

// ---- Mi tarjeta ----

/**
 * La tarjeta de mi identidad, calculada de una vez: los bytes, el texto para compartir (Base64url), el texto del QR
 * (Base45) y la huella. Es todo público; la [Identity] que la produjo ya está cerrada.
 */
class MyCard(val bytes: ByteArray, val text: String, val qrText: String, val fingerprint: String)

/**
 * La tarjeta de esta identidad, con el nombre que ella se ha puesto (`username`; vacío si no se ha puesto ninguno, y
 * quien la recibe escribe el suyo). Abre la identidad y la cierra antes de volver. Lanza [CryptoFailure] si la entrada no
 * es una identidad o el nombre no vale.
 */
fun VaultEntry.myCard(): MyCard = openIdentity().use { id ->
    val bytes = id.cardBytes(username)
    MyCard(bytes, cardText(bytes), cardQr(bytes), fingerprint(id.edPublic, id.kemPublic))
}

// ---- La lista de contactos ----

/**
 * Los contactos de [entries] cuyo nombre guardado contiene [text] (sin distinguir mayúsculas ni acentos, como la búsqueda
 * de la bóveda), por orden de nombre. Nunca la identidad ni otros tipos.
 */
fun contactsList(entries: List<VaultEntry>, text: String): List<VaultEntry> =
    entries.query(EntryFilter(text = text, type = EntryType.CONTACT))

/** Lo que se escribe junto al nombre en la lista y en el detalle. */
fun verificationMark(entry: VaultEntry): String = when {
    !entry.verified -> "sin verificar"
    entry.verifiedBy == "qr" -> "verificado con QR"
    entry.verifiedBy == "fingerprint" -> "verificado con la huella"
    else -> "verificado"
}

/**
 * El contacto [entry] marcado como verificado por la huella en [now]. Si ya estaba verificado (por QR o por huella) se
 * devuelve igual, sin cambiar cómo se verificó. Solo vale para un contacto.
 */
fun markVerified(entry: VaultEntry, now: Long): VaultEntry {
    require(entry.type == EntryType.CONTACT) { "Solo se verifica un contacto" }
    if (entry.verified) return entry
    return entry.copy(verified = true, verifiedAt = now, verifiedBy = "fingerprint", updatedAt = now)
}

/**
 * El contacto [entry] con el nombre [name], que tiene que valer según [checkContactName] (si no, `IllegalArgumentException`);
 * se guarda el nombre ya normalizado. La tarjeta y la verificación no cambian.
 */
fun renamedContact(entry: VaultEntry, name: String, now: Long): VaultEntry {
    require(entry.type == EntryType.CONTACT) { "Solo se renombra un contacto" }
    val valid = checkContactName(name) as? ContactName.Valid ?: throw IllegalArgumentException("Nombre no válido")
    return entry.copy(title = valid.name, updatedAt = now)
}

// ---- Mensajes de error ----

/** El mensaje de una tarjeta que no se pudo leer: el de la [CryptoFailure] (ya en español) o uno genérico. */
fun cardReadErrorMessage(e: Throwable): String = (e as? CryptoFailure)?.message?.takeIf { it.isNotBlank() } ?: UNKNOWN_CARD

/** Lo mismo que dice la pantalla de desbloqueo cuando falla la contraseña maestra. */
fun masterFailureMessage(e: Throwable): String = if (e is CryptoFailure) WRONG_MASTER else NOT_OPENED

/**
 * Qué decir de lo que devolvió `upsert` o `delete` (true si guardó; false si la bóveda estaba bloqueada), o `null` si todo fue bien.
 */
fun saveFailureMessage(result: Result<Boolean>): String? {
    val e = result.exceptionOrNull() ?: return if (result.getOrNull() == true) null else VAULT_CLOSED
    return if (e is IllegalStateException) NOT_SAVED_STATE else NOT_SAVED
}
