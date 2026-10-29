package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.Dialect
import app.seguridad.crypto.Header
import app.seguridad.crypto.ENCRYPTED_EXTENSION
import app.seguridad.crypto.MAX_SAVED_NAME_BYTES
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.takeUtf8Bytes
import app.seguridad.crypto.pk.Card
import app.seguridad.crypto.pk.PkKind
import app.seguridad.crypto.pk.Received
import app.seguridad.crypto.pk.SIGNATURE_EXTENSION
import app.seguridad.crypto.pk.decryptFileFor
import app.seguridad.crypto.pk.decryptMessage
import app.seguridad.crypto.pk.PK_VERSION
import app.seguridad.crypto.pk.decodeStrictUtf8
import app.seguridad.crypto.pk.detectReceivedWithBytes
import app.seguridad.crypto.pk.encryptFileFor
import app.seguridad.crypto.pk.encryptMessage
import app.seguridad.crypto.pk.hasForbiddenNameCharacter
import app.seguridad.crypto.pk.pkKindOfFile
import app.seguridad.crypto.pk.signFile
import app.seguridad.crypto.pk.signText
import app.seguridad.crypto.pk.verifyFileSignature
import app.seguridad.crypto.pk.verifySignedText
import app.seguridad.crypto.safeFileName
import app.seguridad.tools.sameKeyContactsText
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CharacterCodingException
import java.util.UUID
import java.util.concurrent.CancellationException

/*
 * La lógica de «Cifrar para…», «Firmar» y «Abrir lo recibido» que no es de pantalla (las pantallas, en `ui/`, no se
 * prueban en `jvm-check`): el texto del remitente, el saneado del nombre de un archivo recibido, los errores en español,
 * el plan según `detectReceived`, y el descifrado de un archivo a una carpeta temporal que se vacía siempre.
 *
 * Reglas que se cumplen aquí:
 *  - el nombre que se enseña de un remitente es siempre el **guardado** del contacto, nunca el de la tarjeta de dentro de
 *    un mensaje ni el de otra persona;
 *  - un archivo descifrado se escribe primero en una carpeta propia de la caché y solo se entrega si todo ha terminado
 *    bien (registro final y firma comprobados); con un fallo, una cancelación o cualquier excepción no queda nada;
 *  - la [app.seguridad.crypto.pk.Identity] se abre en cada llamada y se cierra en un `finally`; nunca se guarda.
 *  - el texto en claro y los nombres no salen en `toString`.
 */

// ---- Textos fijos ----

/** Lo que dice «Firmar» (revisión jurídica: sin «validez legal» ni «firma electrónica»). */
const val SIGN_HELP = "Demuestra a tus contactos que es tuyo y que no ha cambiado."

/** Lo que dice «Cifrar para…» (revisión jurídica: sin promesas absolutas). */
const val ENCRYPT_HELP =
    "Solo quien tenga la identidad del contacto elegido podrá leerlo. Si alguien robara esa identidad podría leer también " +
        "lo que ya recibió, y quien vea el mensaje sabe que existe, su tamaño y cuándo se envió."

/** Un `.sgenc` (o `.cpenc`) cifrado con contraseña elegido en «Abrir lo recibido»: se abre en Herramientas. */
const val SYMMETRIC_FILE_NOTICE = "Este archivo está cifrado con contraseña: ábrelo en Herramientas → Cifrar archivos"

/** Debajo del texto cifrado o firmado que se comparte: es público. */
const val SHARE_HINT = "El texto cifrado o firmado no es secreto: se puede compartir por cualquier chat."

/** Un contacto sin verificar (el aviso del plan). */
const val UNVERIFIED_ENCRYPT_WARNING = "No has comprobado su huella: si alguien cambió su tarjeta, podría leerlo"

/** No hay identidad: no se puede firmar. */
const val NO_IDENTITY_TO_SIGN = "Crea tu identidad en Contactos para firmar"

/** No hay identidad: no se puede abrir lo cifrado para ti. */
const val NO_IDENTITY_TO_OPEN = "Crea tu identidad en Contactos para abrir mensajes cifrados"

/** Lo que se dice cuando no hay ninguna tarjeta, mensaje ni firma en lo pegado. */
const val UNKNOWN_RECEIVED = "No es una tarjeta, un mensaje cifrado ni un texto firmado de Seguridad"

private const val PASTE_SOMETHING = "Pega un texto"
private const val TOO_BIG_RECEIVED = "El texto es demasiado grande"
private const val GENERIC_OPEN_ERROR = "No se ha podido abrir. Comprueba que está completo."

/** Nombre con el que se propone guardar un archivo recibido cuyo nombre, saneado, queda vacío. */
const val DEFAULT_RECEIVED_NAME = "archivo-recibido"

/** Carpeta, dentro de la caché privada, donde esperan los archivos descifrados (una subcarpeta por operación). */
const val PK_OPEN_TEMP_DIR = "pk-open"

private const val MAX_RECEIVED_NAME_CHARS = 120
private const val COPY_BUFFER_BYTES = 64 * 1024
private const val TEMP_PREFIX = "recibido"

// ---- El remitente ----

/** Cuánto hay que fiarse de lo que dice una línea de remitente: la pantalla elige el color. */
enum class TrustLevel { GOOD, CAUTION, WARNING }

/**
 * Lo que la pantalla escribe de quién es lo que se acaba de abrir; [note], si hay, va debajo en letra pequeña. [sameKey],
 * si hay, es el aviso de A16a: la clave de firma está guardada en varios contactos.
 */
data class SenderLine(val text: String, val level: TrustLevel, val note: String? = null, val sameKey: String? = null)

/** Debajo de «Firmado por …» en un `SGS1:` o un `.sgsig`: son públicos, cualquiera puede reenviarlos (Davis 2001). */
const val SIGNATURE_FORWARD_NOTE = "Una firma dice quién lo firmó, no a quién iba dirigido."

/**
 * El texto del remitente [sender] (el de `senderOf`). El nombre de un contacto es el **guardado** en la bóveda (su título,
 * que eligió el usuario y pasó por `checkContactName`), nunca el de ninguna tarjeta. Sin género («verificado»).
 *
 * [addressedToMe] es `true` para lo cifrado (`SGM1:`, `.sgenc` de tipo 3), cuya firma cubre mi identidad: «De Ana». Es `false`
 * para una firma pública (`SGS1:`, `.sgsig`), que no nombra destinatario y puede reenviarla cualquiera: «Firmado por Ana» y,
 * debajo, [SIGNATURE_FORWARD_NOTE]. «Firmado por ti», la clave desconocida y «Sin firmar» son iguales en los dos casos.
 */
fun senderLine(sender: Sender, addressedToMe: Boolean): SenderLine = when (sender) {
    Sender.Me -> SenderLine("Firmado por ti", TrustLevel.GOOD)
    is Sender.Contact -> {
        val from = if (addressedToMe) "De" else "Firmado por"
        val note = if (addressedToMe) null else SIGNATURE_FORWARD_NOTE
        val sameKey = if (sender.sameKeyCount > 1) sameKeyContactsText(sender.sameKeyCount) else null
        if (sender.entry.verified) SenderLine("$from ${sender.entry.title} (verificado)", TrustLevel.GOOD, note, sameKey)
        else SenderLine("$from ${sender.entry.title}, sin verificar: compara la huella", TrustLevel.CAUTION, note, sameKey)
    }
    Sender.UnknownKey -> SenderLine("Firmado por una clave que no está en tus contactos", TrustLevel.WARNING)
    Sender.Unsigned -> SenderLine("Sin firmar: cualquiera pudo enviarlo", TrustLevel.WARNING)
}

/** El aviso de cifrar para [contact], o `null` si está verificado. */
fun encryptWarning(contact: VaultEntry): String? = if (contact.verified) null else UNVERIFIED_ENCRYPT_WARNING

// ---- Errores en español ----

/**
 * El mensaje de un fallo al abrir algo recibido: el de la [CryptoFailure] (ya en español, `NOT_FOR_ME` incluido) o uno
 * genérico. Nunca el `toString` ni la traza de otra excepción: pueden llevar rutas o datos.
 */
fun openErrorMessage(e: Throwable): String = actionErrorMessage(e, GENERIC_OPEN_ERROR)

/** Como [openErrorMessage], con el genérico [fallback] (el de cifrar, firmar o guardar). */
fun actionErrorMessage(e: Throwable, fallback: String): String =
    (e as? CryptoFailure)?.message?.takeIf { it.isNotBlank() } ?: fallback

// ---- Nombres de archivo ----

/**
 * El nombre con el que proponer guardar un archivo descifrado. El que lleva dentro lo eligió otra persona, así que se
 * sanea: se queda con el último trozo no vacío tras separar por `/` y `\` (nunca una ruta); se quitan los controles
 * (Cc: C0, DEL y C1), los de dirección del texto y todo lo de la lista fija de nombres de tarjeta
 * (`hasForbiddenNameCharacter`); se recortan los espacios y los puntos de los extremos (así `.` y `..` quedan vacíos); no
 * pasa de 120 caracteres ni de [MAX_SAVED_NAME_BYTES] bytes en UTF-8 (es el nombre que se propone para guardar) ni parte
 * un par sustituto; y si queda vacío es [DEFAULT_RECEIVED_NAME]. Es idempotente.
 */
fun sanitizeReceivedName(name: String): String {
    val segments = name.split('/', '\\').map { trimEnds(withoutForbidden(it)) }
    var result = segments.lastOrNull { it.isNotEmpty() } ?: return DEFAULT_RECEIVED_NAME
    if (result.length > MAX_RECEIVED_NAME_CHARS) {
        result = result.take(MAX_RECEIVED_NAME_CHARS)
        if (result.last().isHighSurrogate()) result = result.dropLast(1)
        result = trimEnds(result)
    }
    if (result.toByteArray(Charsets.UTF_8).size > MAX_SAVED_NAME_BYTES) result = trimEnds(takeUtf8Bytes(result, MAX_SAVED_NAME_BYTES))
    return result.ifEmpty { DEFAULT_RECEIVED_NAME }
}

private fun withoutForbidden(s: String): String {
    val out = StringBuilder(s.length)
    s.codePoints().forEach { cp ->
        val one = String(Character.toChars(cp))
        if (!hasForbiddenNameCharacter(one)) out.append(one)
    }
    return out.toString()
}

private fun isTrimmable(c: Char) = c == '.' || c.isWhitespace() || Character.isSpaceChar(c)

private fun trimEnds(s: String): String = s.trim(::isTrimmable)

/** El nombre que va dentro con «No enviar el nombre» (A15b), sin la extensión. */
const val ANONYMOUS_FILE_BASE = "archivo"

private const val MAX_ANONYMOUS_EXTENSION_CHARS = 10

/**
 * A15b, «No enviar el nombre»: `archivo.<ext>`, con la extensión de [original] (la que queda tras [safeFileName], así que
 * nunca la de una ruta ni la de un nombre dado la vuelta) en minúsculas, solo si mide de 1 a 10 caracteres y todos son
 * letras o cifras ASCII; si no, `archivo`. No queda nada más del nombre original.
 */
fun anonymousFileName(original: String): String {
    val name = safeFileName(original)
    val dot = name.lastIndexOf('.')
    if (dot < 0) return ANONYMOUS_FILE_BASE
    val ext = name.substring(dot + 1)
    val ok = ext.length in 1..MAX_ANONYMOUS_EXTENSION_CHARS && ext.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
    return if (ok) "$ANONYMOUS_FILE_BASE.${ext.lowercase(java.util.Locale.ROOT)}" else ANONYMOUS_FILE_BASE
}

/** `<original>.sgenc`: cómo se llama el archivo cifrado para un contacto. */
fun encryptedFileName(original: String): String = safeFileName(original, ENCRYPTED_EXTENSION.utf8Size()) + ENCRYPTED_EXTENSION

/** `<original>.sgsig`: cómo se llama la firma de un archivo. */
fun signatureFileName(original: String): String = safeFileName(original, SIGNATURE_EXTENSION.utf8Size()) + SIGNATURE_EXTENSION

private fun String.utf8Size() = toByteArray(Charsets.UTF_8).size

// ---- A15a: un resultado solo junto a las entradas de las que salió ----

/**
 * Las entradas de las que sale un resultado de «Cifrar para…» o «Firmar»: el contacto (su id; `null` en «Firmar»), el
 * texto (`null` con un archivo) y el archivo (su URI como texto; `null` con un texto). [text] es el texto en claro: vive
 * solo en memoria, como el campo, y no sale en `toString`.
 */
data class PkInputs(val contactId: String?, val text: String?, val fileUri: String?) {
    override fun toString(): String = "PkInputs(contact=${contactId != null}, text=${text != null}, file=${fileUri != null})"
}

/** Un resultado ([output], el `SGM1:` o el `SGS1:`, que es público) con las entradas de las que salió. */
data class PkResult(val inputs: PkInputs, val output: String) {
    override fun toString(): String = "PkResult($inputs)"
}

/**
 * Lo que se enseña como resultado: [last] solo si salió de las entradas de ahora ([current]); si no, `null`. Así un
 * trabajo que termina después de cambiar el contacto, el texto o el archivo nunca pone su salida junto a las entradas
 * nuevas (como `ContactAddScreen` en la 1.2.1).
 */
fun resultFor(current: PkInputs, last: PkResult?): String? = last?.takeIf { it.inputs == current }?.output

/** A message under «Cifrar para…» or «Firmar» ([ok] for success, [text] in Spanish) with the inputs it is about. */
data class PkMessage(val inputs: PkInputs, val ok: Boolean, val text: String) {
    override fun toString(): String = "PkMessage($inputs, ok=$ok)"
}

/** Like [resultFor] for a message: [last] as (ok, text) only while the inputs are still [current]; a late one is hidden. */
fun messageFor(current: PkInputs, last: PkMessage?): Pair<Boolean, String>? = last?.takeIf { it.inputs == current }?.let { it.ok to it.text }

// ---- Abrir lo recibido (texto) ----

/** Qué hacer con un texto pegado, según `detectReceived`. */
sealed interface ReceivedPlan {
    /**
     * Una tarjeta válida: lleva al flujo de añadir contacto. [bytes] son sus bytes canónicos: el flujo recibe `cardText(bytes)`,
     * que siempre se vuelve a leer, aunque la tarjeta viniera con texto delante o detrás.
     */
    class CardFound(val card: Card, val bytes: ByteArray) : ReceivedPlan {
        override fun equals(other: Any?): Boolean = other is CardFound && card == other.card && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = card.hashCode() * 31 + bytes.contentHashCode()

        override fun toString(): String = "CardFound"
    }

    /** Un mensaje cifrado ([token] empieza en `SGM1:`). */
    data class Message(val token: String) : ReceivedPlan

    /** Un texto firmado. */
    data class Signed(val block: String) : ReceivedPlan

    /** Nada que se reconozca; [message] es lo que se dice. */
    data class Unrecognized(val message: String) : ReceivedPlan
}

/** Decide qué es [text] (lo pegado). No lanza nunca y no comprueba nada más. */
fun planReceived(text: String): ReceivedPlan {
    if (text.isBlank()) return ReceivedPlan.Unrecognized(PASTE_SOMETHING)
    if (text.length > MAX_TEXT_FILE_BYTES) return ReceivedPlan.Unrecognized(TOO_BIG_RECEIVED)
    val (received, cardBytes) = detectReceivedWithBytes(text)
    return when (received) {
        is Received.CardText -> ReceivedPlan.CardFound(received.card, cardBytes ?: return ReceivedPlan.Unrecognized(UNKNOWN_RECEIVED))
        is Received.Message -> ReceivedPlan.Message(received.token)
        is Received.Signed -> ReceivedPlan.Signed(received.block)
        Received.Unknown -> ReceivedPlan.Unrecognized(UNKNOWN_RECEIVED)
    }
}

/** Lo que sale de abrir un texto recibido. */
sealed interface ReceivedOutcome {
    /** Era una tarjeta: se enseña el flujo de añadir contacto con `cardText(bytes)`. */
    class CardFound(val card: Card, val bytes: ByteArray) : ReceivedOutcome {
        override fun equals(other: Any?): Boolean = other is CardFound && card == other.card && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = card.hashCode() * 31 + bytes.contentHashCode()

        override fun toString(): String = "CardFound"
    }

    /** Se abrió (o se comprobó): [text] y de quién es. El texto no sale en `toString`. */
    data class Opened(val text: String, val sender: Sender, val line: SenderLine) : ReceivedOutcome {
        override fun toString(): String = "Opened(sender=${sender::class.simpleName})"
    }

    /** No se pudo; [message] ya está en español. */
    data class Failed(val message: String) : ReceivedOutcome
}

/**
 * Abre [text] (pegado o importado): una tarjeta se entrega tal cual, un mensaje se descifra con mi identidad, un texto
 * firmado se comprueba. En los dos últimos casos dice de quién es con `senderOf`. Nunca lanza (salvo cancelación): los
 * fallos son [ReceivedOutcome.Failed] con un mensaje en español. La identidad se abre y se cierra dentro.
 */
fun openReceivedText(text: String, entries: List<VaultEntry>): ReceivedOutcome {
    try {
        return when (val plan = planReceived(text)) {
            is ReceivedPlan.CardFound -> ReceivedOutcome.CardFound(plan.card, plan.bytes)
            is ReceivedPlan.Unrecognized -> ReceivedOutcome.Failed(plan.message)
            is ReceivedPlan.Message -> {
                val identity = entries.identity() ?: return ReceivedOutcome.Failed(NO_IDENTITY_TO_OPEN)
                val opened = identity.openIdentity().use { decryptMessage(plan.token, it) }
                opened(opened.text, opened.senderEd, entries, addressedToMe = true)
            }
            is ReceivedPlan.Signed -> {
                val signed = verifySignedText(plan.block)
                opened(signed.text, signed.signerEd, entries, addressedToMe = false)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return ReceivedOutcome.Failed(openErrorMessage(e))
    }
}

private fun opened(text: String, senderEd: ByteArray?, entries: List<VaultEntry>, addressedToMe: Boolean): ReceivedOutcome.Opened {
    val sender = senderOf(senderEd, entries)
    return ReceivedOutcome.Opened(text, sender, senderLine(sender, addressedToMe))
}

// ---- Cifrar y firmar ----

/** La identidad guardada, abierta durante [block] y cerrada después; [CryptoFailure] con [missing] si no hay. */
private inline fun <T> withIdentity(entries: List<VaultEntry>, missing: String, block: (app.seguridad.crypto.pk.Identity) -> T): T {
    val entry = entries.identity() ?: throw CryptoFailure(missing)
    return entry.openIdentity().use(block)
}

/**
 * Cifra [text] para el contacto [contact] y da el `SGM1:`. Con [sign] va firmado con mi identidad (de [entries]); si no hay
 * identidad, [CryptoFailure] con [NO_IDENTITY_TO_SIGN]. Sin firmar no hace falta identidad.
 */
fun encryptTextForContact(text: String, contact: VaultEntry, sign: Boolean, entries: List<VaultEntry>): String {
    val card = contact.contactCard()
    if (!sign) return encryptMessage(text, card, null)
    return withIdentity(entries, NO_IDENTITY_TO_SIGN) { encryptMessage(text, card, it) }
}

/**
 * Cifra [input] para [contact] hacia [output] (en streaming) con [name] dentro; con [sign], firmado. [checkCancelled] se
 * llama en cada escritura y lanza [CancellationException] si hay que parar. No cierra los flujos.
 */
fun encryptFileForContact(
    input: InputStream,
    output: OutputStream,
    name: String,
    contact: VaultEntry,
    sign: Boolean,
    entries: List<VaultEntry>,
    checkCancelled: () -> Unit = {},
) {
    val card = contact.contactCard()
    val checkedIn = CheckedInputStream(input, checkCancelled)
    val checkedOut = CheckedOutputStream(output, checkCancelled)
    if (!sign) return encryptFileFor(checkedIn, checkedOut, name, card, null)
    withIdentity(entries, NO_IDENTITY_TO_SIGN) { encryptFileFor(checkedIn, checkedOut, name, card, it) }
}

/** Firma [text] con mi identidad: el texto normalizado y la línea `SGS1:`. */
fun signTextAs(text: String, entries: List<VaultEntry>): String = withIdentity(entries, NO_IDENTITY_TO_SIGN) { signText(text, it) }

/** El `.sgsig` (101 bytes) de [input] con mi identidad; se lee en bloques de 64 KiB. [checkCancelled] se llama en cada lectura. */
fun signFileAs(input: InputStream, entries: List<VaultEntry>, checkCancelled: () -> Unit = {}): ByteArray =
    withIdentity(entries, NO_IDENTITY_TO_SIGN) { signFile(CheckedInputStream(input, checkCancelled), it) }

/** Comprueba que [sig] (un `.sgsig`) es de [input] y de quién es (`senderOf`); [CryptoFailure] si no cuadra. */
fun verifyFileAgainst(input: InputStream, sig: ByteArray, entries: List<VaultEntry>, checkCancelled: () -> Unit = {}): Sender =
    senderOf(verifyFileSignature(CheckedInputStream(input, checkCancelled), sig), entries)

// ---- Qué archivo se ha elegido ----

/** Lo que es un archivo elegido en «Abrir lo recibido», por sus primeros bytes. */
enum class PickedKind { ENCRYPTED, SIGNATURE, OTHER }

/** El tipo de un archivo por sus primeros bytes ([head]); cualquier otra cosa es [PickedKind.OTHER] (el archivo firmado). */
fun pickedKind(head: ByteArray): PickedKind = when (pkKindOfFile(head)) {
    PkKind.FILE_MESSAGE -> PickedKind.ENCRYPTED
    PkKind.FILE_SIGNATURE -> PickedKind.SIGNATURE
    else -> PickedKind.OTHER
}

/** Lo que es un archivo elegido en «Abrir lo recibido», mirando su contenido. */
sealed interface PickedFile {
    /** Un `.sgenc` de tipo 3 (cifrado para un contacto). */
    data object Encrypted : PickedFile

    /** Un `.sgsig`: [bytes] son los primeros 1.024 bytes (una firma válida mide 101; `verifyFileSignature` rechaza otro tamaño). */
    class Signature(val bytes: ByteArray) : PickedFile

    /** Un `.sgenc` o `.cpenc` cifrado con contraseña (el de Herramientas → Cifrar archivos). */
    data object Symmetric : PickedFile

    /** Un archivo de texto con algo que se reconoce (`SGM1:`, `SGS1:`, `SGK1:`): se lee como si se hubiera pegado. */
    class Text(val text: String, val plan: ReceivedPlan) : PickedFile {
        override fun toString(): String = "Text"
    }

    /** Cualquier otra cosa: el archivo que firma un `.sgsig`. */
    data object Other : PickedFile
}

private const val SIGNATURE_KEEP_BYTES = 1024
private const val HEAD_BYTES = 8

/**
 * Qué es un archivo elegido, por sus primeros bytes [bytes] (como mucho [MAX_TEXT_FILE_BYTES] + 1; basta con eso). En este orden:
 *  1. cifrado con contraseña (`SG`/`CP`, versión 1, archivo): [PickedFile.Symmetric], con o sin `.sgsig` ya elegido;
 *  2. de clave pública: un `.sgenc` de tipo 3 ([PickedFile.Encrypted]) o un `.sgsig` ([PickedFile.Signature]); otro binario
 *     que empiece por `SG` 2 (una tarjeta en bytes) es [PickedFile.Other] y nunca se lee como texto;
 *  3. un texto (UTF-8 estricto, de hasta [MAX_TEXT_FILE_BYTES]) en el que `detectReceived` reconoce algo se lee igual que si
 *     se pegara ([PickedFile.Text]): las reglas de lectura valen igual para un `.txt` importado. No si ya hay un `.sgsig`
 *     elegido (entonces es el archivo firmado);
 *  4. todo lo demás: [PickedFile.Other].
 */
fun classifyPicked(bytes: ByteArray, haveSignature: Boolean): PickedFile {
    if (isSymmetricHeader(bytes)) return PickedFile.Symmetric
    when (pickedKind(bytes.copyOf(minOf(bytes.size, HEAD_BYTES)))) {
        PickedKind.ENCRYPTED -> return PickedFile.Encrypted
        PickedKind.SIGNATURE -> return PickedFile.Signature(bytes.copyOf(minOf(bytes.size, SIGNATURE_KEEP_BYTES)))
        PickedKind.OTHER -> Unit
    }
    if (bytes.size >= 3 && bytes[0] == 'S'.code.toByte() && bytes[1] == 'G'.code.toByte() && bytes[2] == PK_VERSION) return PickedFile.Other
    if (haveSignature || bytes.isEmpty() || bytes.size > MAX_TEXT_FILE_BYTES) return PickedFile.Other
    val text = try {
        decodeStrictUtf8(bytes)
    } catch (e: CharacterCodingException) {
        return PickedFile.Other
    }
    val plan = planReceived(text)
    return if (plan is ReceivedPlan.Unrecognized) PickedFile.Other else PickedFile.Text(text, plan)
}

/** Si [bytes] empieza como un archivo cifrado con contraseña: `SG` o `CP`, versión 1 y tipo archivo. */
private fun isSymmetricHeader(bytes: ByteArray): Boolean =
    bytes.size >= 4 && Dialect.ofMagic(bytes.copyOf(2)) != null && bytes[2] == Header.VERSION && bytes[3] == Header.KIND_FILE

/** Qué falta tras elegir un archivo. */
enum class PickStep { READY_TO_DECRYPT, NEED_SIGNED_FILE, NEED_SIGNATURE, READY_TO_VERIFY }

/** El paso siguiente tras elegir un archivo de tipo [kind], sabiendo si ya hay un `.sgsig` ([haveSignature]) y un archivo ([haveFile], contando el recién elegido). */
fun nextPickStep(kind: PickedKind, haveSignature: Boolean, haveFile: Boolean): PickStep = when (kind) {
    PickedKind.ENCRYPTED -> PickStep.READY_TO_DECRYPT
    PickedKind.SIGNATURE -> if (haveFile) PickStep.READY_TO_VERIFY else PickStep.NEED_SIGNED_FILE
    PickedKind.OTHER -> if (haveSignature) PickStep.READY_TO_VERIFY else PickStep.NEED_SIGNATURE
}

/**
 * Los archivos elegidos hasta ahora en «Abrir lo recibido». [U] es el tipo del URI (genérico: así se prueba sin Android).
 * Solo lleva datos que no son secretos (URIs, nombres que da el proveedor y los bytes de una firma pública).
 *  - [encrypted] y [encryptedName]: un `.sgenc` de tipo 3;
 *  - [signature]: los primeros bytes de un `.sgsig`;
 *  - [signed] y [signedName]: el archivo que firma el `.sgsig`;
 *  - [textCandidate] y [textCandidateName]: el último archivo elegido que se leyó como texto ([PickedFile.Text]). Un `.txt`
 *    con un texto firmado dentro también puede ser el archivo de un `.sgsig`: si después se elige el `.sgsig` y no hay
 *    otro archivo firmado elegido, se comprueba contra este.
 */
class FileSelection<U : Any>(
    val encrypted: U? = null,
    val encryptedName: String = "",
    val signature: ByteArray? = null,
    val signed: U? = null,
    val signedName: String = "",
    val textCandidate: U? = null,
    val textCandidateName: String = "",
) {
    override fun toString(): String =
        "FileSelection(encrypted=${encrypted != null}, signature=${signature != null}, signed=${signed != null}, text=${textCandidate != null})"
}

/**
 * Lo elegido después de elegir [uri] (con el nombre [name]), que [classifyPicked] dice que es [picked]:
 *  - un `.sgenc` de tipo 3, uno con contraseña o un texto empiezan de cero (el texto queda como [FileSelection.textCandidate]);
 *  - un `.sgsig` quita el `.sgenc` y, si todavía no hay archivo firmado, toma como tal el último texto elegido;
 *  - cualquier otro archivo quita el `.sgenc` y es el archivo firmado.
 */
fun <U : Any> FileSelection<U>.afterPicking(picked: PickedFile, uri: U, name: String): FileSelection<U> = when (picked) {
    PickedFile.Encrypted -> FileSelection(encrypted = uri, encryptedName = name)
    PickedFile.Symmetric -> FileSelection()
    is PickedFile.Text -> FileSelection(textCandidate = uri, textCandidateName = name)
    is PickedFile.Signature ->
        if (signed != null) {
            FileSelection(signature = picked.bytes, signed = signed, signedName = signedName)
        } else {
            FileSelection(signature = picked.bytes, signed = textCandidate, signedName = if (textCandidate != null) textCandidateName else "")
        }
    PickedFile.Other -> FileSelection(signature = signature, signed = uri, signedName = name)
}

// ---- Descifrar un archivo a una carpeta temporal ----

/**
 * Un archivo descifrado y comprobado, esperando en la carpeta temporal de su operación ([dir]) a que el usuario elija dónde
 * guardarlo. [name] ya está saneado; [sender] solo existe porque el registro final y la firma se comprobaron. Sin datos en
 * `toString`. [discard] borra la carpeta de esta operación y nada más.
 */
class ReceivedFile internal constructor(val file: File, val name: String, val sender: Sender, internal val dir: File) {
    /** Borra la subcarpeta de esta operación (y el archivo descifrado que lleva dentro). */
    fun discard() {
        dir.deleteRecursively()
    }

    override fun toString(): String = "ReceivedFile(sender=${sender::class.simpleName})"
}

/**
 * Descifra [input] (un `.sgenc` de tipo 3) para mi identidad en un archivo nuevo dentro de una subcarpeta propia de la
 * operación (un UUID al azar) de [root]. Solo si termina bien (registro final y firma comprobados) lo devuelve, con el
 * nombre saneado ([sanitizeReceivedName]) y el remitente. Con un [CryptoFailure], una cancelación ([checkCancelled] lanza
 * [CancellationException]) o cualquier otra excepción se borra **solo la subcarpeta de esta operación** y la excepción
 * sigue su camino: nunca hay remitente de un archivo a medias. [root] se vacía entero solo al arrancar la app
 * ([wipePkTemp]) y al bloquearse la bóveda.
 */
fun decryptReceivedFileToTemp(root: File, input: InputStream, entries: List<VaultEntry>, checkCancelled: () -> Unit = {}): ReceivedFile {
    val identity = entries.identity() ?: throw CryptoFailure(NO_IDENTITY_TO_OPEN)
    val opDir = File(root, UUID.randomUUID().toString())
    try {
        if (!opDir.mkdirs()) throw java.io.IOException("No se pudo crear la carpeta temporal")
        val tmp = File.createTempFile(TEMP_PREFIX, null, opDir)
        val opened = identity.openIdentity().use { me ->
            CheckedOutputStream(FileOutputStream(tmp).buffered(), checkCancelled).use { out -> decryptFileFor(input, out, me, rawName = true) }
        }
        checkCancelled()
        return ReceivedFile(tmp, sanitizeReceivedName(opened.name), senderOf(opened.senderEd, entries), opDir)
    } catch (t: Throwable) {
        opDir.deleteRecursively()
        throw t
    }
}

/** Copia el archivo descifrado a [out] (el documento que eligió el usuario) en bloques de 64 KiB; no cierra [out]. */
fun saveReceivedFile(received: ReceivedFile, out: OutputStream, checkCancelled: () -> Unit = {}) {
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    try {
        received.file.inputStream().use { input ->
            while (true) {
                checkCancelled()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
        out.flush()
    } finally {
        buffer.fill(0)
    }
}

/** Borra la carpeta de temporales de «Abrir lo recibido» de la caché [cacheDir], con todo lo que tenga: al arrancar la app y cada vez que la bóveda no está abierta. */
fun wipePkTemp(cacheDir: File) {
    File(cacheDir, PK_OPEN_TEMP_DIR).deleteRecursively()
}

/** Deja [dir] existente y vacía (borra todo lo que hubiera dentro): al bloquearse la bóveda. */
fun clearTempDir(dir: File) {
    dir.mkdirs()
    dir.listFiles()?.forEach { it.deleteRecursively() }
}

/** Un flujo de entrada que llama a [check] antes de cada lectura (no cierra nada que no sea suyo). */
private class CheckedInputStream(private val input: InputStream, private val check: () -> Unit) : InputStream() {
    override fun read(): Int {
        check()
        return input.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        check()
        return input.read(b, off, len)
    }

    override fun close() = input.close()
}

/** Un flujo de salida que llama a [check] antes de cada escritura: así se puede cancelar un trabajo largo. */
private class CheckedOutputStream(private val out: OutputStream, private val check: () -> Unit) : OutputStream() {
    override fun write(b: Int) {
        check()
        out.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        check()
        out.write(b, off, len)
    }

    override fun flush() = out.flush()

    override fun close() = out.close()
}
