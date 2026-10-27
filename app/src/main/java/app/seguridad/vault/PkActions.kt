package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.ENCRYPTED_EXTENSION
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.pk.Card
import app.seguridad.crypto.pk.PkKind
import app.seguridad.crypto.pk.Received
import app.seguridad.crypto.pk.SIGNATURE_EXTENSION
import app.seguridad.crypto.pk.decryptFileFor
import app.seguridad.crypto.pk.decryptMessage
import app.seguridad.crypto.pk.detectReceived
import app.seguridad.crypto.pk.encryptFileFor
import app.seguridad.crypto.pk.encryptMessage
import app.seguridad.crypto.pk.hasForbiddenNameCharacter
import app.seguridad.crypto.pk.pkKindOfFile
import app.seguridad.crypto.pk.signFile
import app.seguridad.crypto.pk.signText
import app.seguridad.crypto.pk.verifyFileSignature
import app.seguridad.crypto.pk.verifySignedText
import app.seguridad.crypto.safeFileName
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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
    "Solo el contacto elegido podrá leerlo. Está diseñado para resistir también a ordenadores cuánticos futuros " +
        "(cifrado híbrido X-Wing, en proceso de estandarización). Quien vea el mensaje sabrá que existe y cuándo se envía."

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

private const val MAX_RECEIVED_NAME_CHARS = 120
private const val COPY_BUFFER_BYTES = 64 * 1024
private const val TEMP_PREFIX = "recibido"

// ---- El remitente ----

/** Cuánto hay que fiarse de lo que dice una línea de remitente: la pantalla elige el color. */
enum class TrustLevel { GOOD, CAUTION, WARNING }

/** Lo que la pantalla escribe de quién es lo que se acaba de abrir. */
data class SenderLine(val text: String, val level: TrustLevel)

/**
 * El texto del remitente [sender] (el de `senderOf`). El nombre de un contacto es el **guardado** en la bóveda (su título,
 * que eligió el usuario y pasó por `checkContactName`), nunca el de ninguna tarjeta. Sin género («verificado»).
 */
fun senderLine(sender: Sender): SenderLine = when (sender) {
    Sender.Me -> SenderLine("Firmado por ti", TrustLevel.GOOD)
    is Sender.Contact ->
        if (sender.entry.verified) SenderLine("De ${sender.entry.title} (verificado)", TrustLevel.GOOD)
        else SenderLine("De ${sender.entry.title}, sin verificar: compara la huella", TrustLevel.CAUTION)
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
 * pasa de 120 caracteres ni parte un par sustituto; y si queda vacío es [DEFAULT_RECEIVED_NAME]. Es idempotente.
 */
fun sanitizeReceivedName(name: String): String {
    val segments = name.split('/', '\\').map { trimEnds(withoutForbidden(it)) }
    var result = segments.lastOrNull { it.isNotEmpty() } ?: return DEFAULT_RECEIVED_NAME
    if (result.length > MAX_RECEIVED_NAME_CHARS) {
        result = result.take(MAX_RECEIVED_NAME_CHARS)
        if (result.last().isHighSurrogate()) result = result.dropLast(1)
        result = trimEnds(result)
    }
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

/** `<original>.sgenc`: cómo se llama el archivo cifrado para un contacto. */
fun encryptedFileName(original: String): String = safeFileName(original) + ENCRYPTED_EXTENSION

/** `<original>.sgsig`: cómo se llama la firma de un archivo. */
fun signatureFileName(original: String): String = safeFileName(original) + SIGNATURE_EXTENSION

// ---- Abrir lo recibido (texto) ----

/** Qué hacer con un texto pegado, según `detectReceived`. */
sealed interface ReceivedPlan {
    /** Una tarjeta válida: lleva al flujo de añadir contacto. */
    data class CardFound(val card: Card) : ReceivedPlan

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
    return when (val received = detectReceived(text)) {
        is Received.CardText -> ReceivedPlan.CardFound(received.card)
        is Received.Message -> ReceivedPlan.Message(received.token)
        is Received.Signed -> ReceivedPlan.Signed(received.block)
        Received.Unknown -> ReceivedPlan.Unrecognized(UNKNOWN_RECEIVED)
    }
}

/** Lo que sale de abrir un texto recibido. */
sealed interface ReceivedOutcome {
    /** Era una tarjeta: se enseña el flujo de añadir contacto. */
    data class CardFound(val card: Card) : ReceivedOutcome

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
            is ReceivedPlan.CardFound -> ReceivedOutcome.CardFound(plan.card)
            is ReceivedPlan.Unrecognized -> ReceivedOutcome.Failed(plan.message)
            is ReceivedPlan.Message -> {
                val identity = entries.identity() ?: return ReceivedOutcome.Failed(NO_IDENTITY_TO_OPEN)
                val opened = identity.openIdentity().use { decryptMessage(plan.token, it) }
                opened(opened.text, opened.senderEd, entries)
            }
            is ReceivedPlan.Signed -> {
                val signed = verifySignedText(plan.block)
                opened(signed.text, signed.signerEd, entries)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return ReceivedOutcome.Failed(openErrorMessage(e))
    }
}

private fun opened(text: String, senderEd: ByteArray?, entries: List<VaultEntry>): ReceivedOutcome.Opened {
    val sender = senderOf(senderEd, entries)
    return ReceivedOutcome.Opened(text, sender, senderLine(sender))
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

/** Qué falta tras elegir un archivo. */
enum class PickStep { READY_TO_DECRYPT, NEED_SIGNED_FILE, NEED_SIGNATURE, READY_TO_VERIFY }

/** El paso siguiente tras elegir un archivo de tipo [kind], sabiendo si ya hay un `.sgsig` ([haveSignature]) y un archivo ([haveFile], contando el recién elegido). */
fun nextPickStep(kind: PickedKind, haveSignature: Boolean, haveFile: Boolean): PickStep = when (kind) {
    PickedKind.ENCRYPTED -> PickStep.READY_TO_DECRYPT
    PickedKind.SIGNATURE -> if (haveFile) PickStep.READY_TO_VERIFY else PickStep.NEED_SIGNED_FILE
    PickedKind.OTHER -> if (haveSignature) PickStep.READY_TO_VERIFY else PickStep.NEED_SIGNATURE
}

// ---- Descifrar un archivo a una carpeta temporal ----

/**
 * Un archivo descifrado y comprobado, esperando en la carpeta temporal a que el usuario elija dónde guardarlo. [name] ya
 * está saneado; [sender] solo existe porque el registro final y la firma se comprobaron. Sin datos en `toString`.
 */
class ReceivedFile internal constructor(val file: File, val name: String, val sender: Sender) {
    override fun toString(): String = "ReceivedFile(sender=${sender::class.simpleName})"
}

/**
 * Descifra [input] (un `.sgenc` de tipo 3) para mi identidad en un archivo nuevo de [dir], que se vacía antes. Solo si
 * termina bien (registro final y firma comprobados) lo devuelve, con el nombre saneado y el remitente. Con un
 * [CryptoFailure], una cancelación ([checkCancelled] lanza [CancellationException]) o cualquier otra excepción, [dir]
 * queda vacío y la excepción sigue su camino: nunca hay remitente de un archivo a medias.
 */
fun decryptReceivedFileToTemp(dir: File, input: InputStream, entries: List<VaultEntry>, checkCancelled: () -> Unit = {}): ReceivedFile {
    val identity = entries.identity() ?: throw CryptoFailure(NO_IDENTITY_TO_OPEN)
    clearTempDir(dir)
    val tmp = File.createTempFile(TEMP_PREFIX, null, dir)
    try {
        val opened = identity.openIdentity().use { me ->
            CheckedOutputStream(FileOutputStream(tmp).buffered(), checkCancelled).use { out -> decryptFileFor(input, out, me) }
        }
        checkCancelled()
        return ReceivedFile(tmp, sanitizeReceivedName(opened.name), senderOf(opened.senderEd, entries))
    } catch (t: Throwable) {
        clearTempDir(dir)
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

/** Deja [dir] existente y vacía (borra todo lo que hubiera dentro). */
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
