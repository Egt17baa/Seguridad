package app.seguridad.tools

import app.seguridad.tools.photo.Found
import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.photo.PhotoReport
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

// UI copy of the 1.3.0 tools. Texts marked «anexo» are copied verbatim from
// docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md (approved by Abogado Digital): a change goes back to that
// review. ToolTextsTest checks every annex text is here, word for word, and that none uses a forbidden word.
// Parameterised texts are functions; the annex's own placeholder ([lista], N, «…») renders the annex text exactly.

// ---- Limpiar foto ----

const val PHOTO_TOOL_TITLE = "Limpiar foto"
const val PHOTO_TOOL_SUBTITLE = "Quita la ubicación y otros datos ocultos"

/** Anexo: «Explicación (antes de elegir)». */
const val PHOTO_EXPLANATION =
    "Quita los datos que la foto guarda dentro del archivo: la ubicación, el modelo del móvil, la fecha, la miniatura y " +
        "otros. No cambia lo que se ve: una cara, una matrícula, tu portal o un reflejo pueden delatarte igual; para eso, " +
        "usa «Tachar en imagen». Tampoco quita las marcas invisibles que pueda llevar la propia imagen ni el rastro que " +
        "deja el sensor de cada cámara. Muchas apps de mensajería quitan parte de estos datos al enviar una foto, pero no " +
        "siempre todos, y no cuando la envías como documento o archivo. La foto se analiza en tu móvil y no se envía a " +
        "ningún sitio."

const val PHOTO_PICK = "Elegir foto"
const val PHOTO_PICK_OTHER = "Elegir otra foto"
const val PHOTO_FOUND_TITLE = "Lo que trae la foto"
const val PHOTO_ANALYSING = "Analizando la foto…"

/** Anexo: the GPS line; [photoGpsLine] fills in the rounded coordinates. */
private const val PHOTO_GPS_PREFIX = "Ubicación GPS: cerca de "
private const val PHOTO_GPS_SUFFIX = " (redondeado)"

/** GPS found (in XMP) without coordinates we can read: the annex line without the numbers. */
const val PHOTO_GPS_NO_COORDS = "Ubicación GPS"
const val PHOTO_DEVICE = "Móvil: marca, modelo y número de serie"
const val PHOTO_DATE_SOFTWARE = "Fecha y hora, y programa usado"
const val PHOTO_THUMBNAIL = "Miniatura incrustada: puede mostrar la foto entera, antes de recortarla o editarla"
const val PHOTO_XMP_COMMENTS = "Datos del editor (XMP), comentarios"
const val PHOTO_ICC_KEPT = "Perfil de color: se conserva para que no cambien los colores"
const val PHOTO_MOTION_VIDEO = "Incluye un vídeo corto (foto en movimiento): se quita"
const val PHOTO_C2PA =
    "Credenciales de contenido (C2PA): dicen cómo se creó o editó la imagen, a veces con IA. Si la imagen está hecha o " +
        "retocada con IA y la vas a publicar, dilo tú"
const val PHOTO_AUTHOR_RIGHTS = "Datos de autor y de derechos: si la foto no es tuya, no los quites para ocultar de quién es"

/** Not in the annex (no line there for what the reader does not recognise): neutral, it only names it. */
const val PHOTO_OTHER = "Otros datos ocultos del archivo"

/** «Lo que trae la foto», one line per [Found], in the annex order. GPS with coordinates uses [photoGpsLine]. */
val PHOTO_FOUND_TEXTS: Map<Found, String> = linkedMapOf(
    Found.GPS to PHOTO_GPS_NO_COORDS,
    Found.DEVICE to PHOTO_DEVICE,
    Found.DATE_SOFTWARE to PHOTO_DATE_SOFTWARE,
    Found.THUMBNAIL to PHOTO_THUMBNAIL,
    Found.XMP_COMMENTS to PHOTO_XMP_COMMENTS,
    Found.ICC_KEPT to PHOTO_ICC_KEPT,
    Found.MOTION_VIDEO to PHOTO_MOTION_VIDEO,
    Found.C2PA to PHOTO_C2PA,
    Found.AUTHOR_RIGHTS to PHOTO_AUTHOR_RIGHTS,
    Found.OTHER to PHOTO_OTHER,
)

/** Short names for «Copia lista, sin estos datos: [lista]», taken from the lines above. ICC_KEPT is kept, so absent. */
val PHOTO_REMOVED_NAMES: Map<Found, String> = linkedMapOf(
    Found.GPS to "ubicación GPS",
    Found.DEVICE to "marca, modelo y número de serie del móvil",
    Found.DATE_SOFTWARE to "fecha y hora, y programa usado",
    Found.THUMBNAIL to "miniatura incrustada",
    Found.XMP_COMMENTS to "datos del editor (XMP), comentarios",
    Found.MOTION_VIDEO to "vídeo corto (foto en movimiento)",
    Found.C2PA to "credenciales de contenido (C2PA)",
    Found.AUTHOR_RIGHTS to "datos de autor y de derechos",
    Found.OTHER to "otros datos ocultos del archivo",
)

/**
 * Not in the annex. HEIC/HEIF/AVIF: the JPEG is encoded from the pixels, so the original file's metadata does not go
 * with it; the encoder may still embed a colour profile, which the screen then names with [PHOTO_ICC_KEPT].
 */
const val PHOTO_DONE_CONVERTED =
    "Copia lista, sin los datos ocultos del archivo original. El original no se ha tocado y sigue teniéndolos: " +
        "comparte la copia, no el original."

/** Not in the annex: once saved or shared when there was nothing to remove (the card already says so). */
const val PHOTO_DONE_SHORT = "Copia lista."

/** Anexo: «Si no hay nada que quitar». */
const val PHOTO_NOTHING_FOUND =
    "No hemos encontrado datos que quitar en el archivo que nos ha dado Android. Android puede ocultar la ubicación a " +
        "las apps, así que el original de tu galería podría tenerla: comparte la copia de Seguridad, no el original."

/** Anexo: «HEIC, HEIF y AVIF». */
const val PHOTO_HEIF_NOTICE = "Las fotos HEIC, HEIF y AVIF se guardan como JPEG: la copia puede perder algo de calidad."

/** Anexo: «Nombre del archivo». */
const val PHOTO_FILE_NAME_NOTE =
    "El nombre del archivo también puede decir algo de ti (por ejemplo, la fecha en «IMG_20261027_…»). Por eso la " +
        "copia se llama «foto-limpia»."

/** Anexo: the button that stays available when there is nothing to remove. */
const val PHOTO_SAVE_COPY = "Guardar copia"
const val SHARE = "Compartir"

/** Not in the annex: plain failure messages, without internal details. */
const val PHOTO_TOO_BIG = "La foto ocupa más de 50 MB. No se ha guardado nada."
const val PHOTO_SAVE_FAILED = "No se ha podido guardar la copia. No se ha guardado nada."
const val PHOTO_SHARE_FAILED = "No se ha podido compartir la copia."
const val PHOTO_LOST = "No se ha guardado: vuelve a elegir la foto."

/** Anexo: «Al terminar»; [list] replaces «[lista]». */
fun photoDoneText(list: String) =
    "Copia lista, sin estos datos: $list. El original no se ha tocado y sigue teniéndolos: comparte la copia, no el original."

/** Anexo: the GPS line, with both coordinates rounded to 2 decimals, a decimal comma and the minus sign «−». */
fun photoGpsLine(lat: Double, lon: Double) = "$PHOTO_GPS_PREFIX${coordinate(lat)}, ${coordinate(lon)}$PHOTO_GPS_SUFFIX"

private fun coordinate(v: Double): String {
    val r = round(v * 100) / 100
    val digits = String.format(Locale.ROOT, "%.2f", abs(r)).replace('.', ',')
    return if (r < 0) "−$digits" else digits
}

/** The lines of «Lo que trae la foto» for [report], in the annex order (the kept colour profile included). */
fun photoFoundLines(report: PhotoReport): List<String> = PHOTO_FOUND_TEXTS.mapNotNull { (found, text) ->
    when {
        found !in report.found -> null
        found == Found.GPS && report.gps != null -> photoGpsLine(report.gps.first, report.gps.second)
        else -> text
    }
}

/** False when the only thing found is the colour profile, which the copy keeps. */
fun photoHasSomethingToRemove(report: PhotoReport) = (report.found - Found.ICC_KEPT).isNotEmpty()

/**
 * The message once the copy is saved or shared: «Copia lista…» with what was removed, or [PHOTO_DONE_SHORT] when there
 * was nothing. When [converted] (HEIC/HEIF/AVIF to JPEG), [report] is the converted JPEG's own: the original's
 * metadata is gone, and a colour profile the encoder embedded is named.
 */
fun photoDoneMessage(report: PhotoReport, converted: Boolean = false): String {
    if (converted) {
        return if (Found.ICC_KEPT in report.found) "$PHOTO_DONE_CONVERTED $PHOTO_ICC_KEPT." else PHOTO_DONE_CONVERTED
    }
    val removed = PHOTO_REMOVED_NAMES.filterKeys { it in report.found }.values
    return if (removed.isEmpty()) PHOTO_DONE_SHORT else photoDoneText(removed.joinToString("; "))
}

// ---- Tachar en imagen ----

const val REDACT_TOOL_TITLE = "Tachar en imagen"
const val REDACT_TOOL_SUBTITLE = "Tapa con bloques negros lo que no quieras enseñar"

/** Anexo: «Explicación». */
const val REDACT_EXPLANATION =
    "Arrastra el dedo para tapar con un bloque negro lo que no quieras que se vea. El bloque sustituye los píxeles: en " +
        "la copia que guardes o compartas, lo de debajo no se puede recuperar. La copia se crea de nuevo, sin los datos " +
        "ocultos del archivo original."

/** Anexo: «Lo que no protege». */
const val REDACT_LIMITS =
    "Ten en cuenta: el original sigue teniendo todo, no lo compartas. Lo que queda a la vista puede dar pistas (el " +
        "tamaño del bloque, el texto de alrededor, un reflejo), así que tapa con margen. Y si ya enviaste la imagen " +
        "antes, esto no la borra de donde la mandaste."

/** Anexo: «Si se reduce». */
const val REDACT_DOWNSCALED =
    "La imagen es muy grande y se ha reducido a unos 24 megapíxeles para poder editarla. La copia tendrá ese tamaño."

/**
 * «Si se reduce» with the size it really has: [REDACT_DOWNSCALED] at 24. Android 8 (no ImageDecoder) can only halve
 * the sides, so there it may be fewer (6 at the least), and the annex number would not be true.
 */
fun redactDownscaledText(megapixels: Int) =
    "La imagen es muy grande y se ha reducido a unos $megapixels megapíxeles para poder editarla. La copia tendrá ese " +
        "tamaño."

/** Not in the annex: buttons, progress and plain failure messages, without internal details. */
const val REDACT_PICK = "Elegir imagen"
const val REDACT_PICK_OTHER = "Elegir otra imagen"
const val REDACT_OPENING = "Abriendo la imagen…"
const val REDACT_RENDERING = "Preparando la copia…"
const val REDACT_UNDO = "Deshacer"
const val REDACT_REDO = "Rehacer"
const val REDACT_TOO_BIG = "La imagen ocupa más de 50 MB."
const val REDACT_DAMAGED =
    "No se ha podido abrir esta imagen: el archivo está dañado o tiene un formato que no conocemos."
const val REDACT_COPY_FAILED = "No se ha podido crear la copia tachada. No se ha guardado nada."
const val REDACT_LOST = "No se ha guardado: vuelve a elegir la imagen y a tachar lo que quieras tapar."

// ---- Inspector de QR ----

/** Anexo: «Explicación». */
const val QR_EXPLANATION =
    "Enseña lo que hay en un código QR sin abrir nada: ni enlaces, ni redes Wi-Fi, ni pagos. Solo puedes copiarlo. La " +
        "imagen se analiza en tu móvil y no se guarda."

/** Anexo: «Sin avisos», shown in a neutral colour, without ✓. */
const val QR_NO_ALERTS =
    "No vemos señales típicas de engaño. Eso no garantiza que sea seguro: un enlace puede parecer normal y ser un " +
        "fraude. Si no esperabas este QR o te meten prisa, no lo abras."

fun qrAlertsTitle(count: String) = "Señales de alerta ($count)"

const val QR_MIXED_SCRIPTS =
    "El nombre mezcla letras de alfabetos distintos (por ejemplo, una «а» cirílica que parece latina). Es un truco " +
        "habitual para imitar webs conocidas."

/** [unicode] replaces «…»: the Unicode form of the punycode name. */
fun qrPunycode(unicode: String) =
    "El nombre usa letras especiales codificadas (punycode). Se lee así: «$unicode». Comprueba que es la web que esperas."

const val QR_IP_HOST =
    "El enlace va a una dirección numérica, no a un nombre. Las webs conocidas casi nunca se enlazan así."
const val QR_HTTP = "La conexión no va cifrada (http): lo que envíes lo puede leer quien esté en la red."

fun qrUserInfo(host: String) = "Lo que va antes de la «@» no es la web: la web real es «$host»."

/** [port] replaces «[:puerto]», for example «:8080». */
fun qrPort(port: String) = "Usa un puerto poco habitual ($port). No tiene por qué ser malo, pero es raro en una web normal."

const val QR_SHORTENER = "Es un enlace acortado: no se ve adónde lleva. El destino solo se conoce al abrirlo."
const val QR_SUBDOMAINS =
    "Tiene muchos niveles en el nombre. Fíjate en cómo termina: lo que va al final es lo que manda."

fun qrScheme(scheme: String) = "Este tipo de enlace ($scheme) puede ejecutar código o abrir otras apps. No lo abras."

/** [marks] replaces «⟨U+202E⟩»: the marks as the inspector shows them. */
fun qrBidi(marks: String) =
    "Hay caracteres invisibles o que cambian el orden del texto, marcados como $marks. Pueden hacer que el texto parezca otro."

const val QR_WIFI_OPEN = "Red abierta: lo que hagas en ella lo puede ver cualquiera que esté conectado."
const val QR_WIFI_WEP = "WEP es un cifrado antiguo que se rompe en minutos."
const val QR_SEPA =
    "Comprueba a quién pagas antes de hacerlo: pegar un QR falso encima del bueno es un fraude habitual. Seguridad no " +
        "comprueba el beneficiario ni hace pagos."
const val QR_CRYPTO =
    "Comprueba la dirección completa con quien te la dio, por otro medio. Un pago en criptomonedas normalmente no se " +
        "puede deshacer. Seguridad no hace pagos."
const val QR_SMS = "Un SMS a un número corto puede tener coste."
const val QR_SGK1 =
    "Se añadirá como contacto sin verificar. Para verificarlo, compara la huella con la persona, en persona o por llamada."

// ---- PIN y códigos ----

/** [count] and [bits] replace «N» and «X». */
fun pinText(count: String, bits: String) =
    "PIN al azar, sin los patrones más usados (años, fechas, repeticiones, secuencias y líneas del teclado). Quedan " +
        "$count PIN posibles (unos $bits bits). Un PIN corto protege porque el móvil o la tarjeta se bloquean tras pocos " +
        "intentos: no lo uses como contraseña."

const val RECOVERY_CODES_TEXT =
    "Diez códigos al azar para guardar fuera del alcance de otros, por ejemplo en la bóveda o impresos. Úsalos donde " +
        "puedas elegir tus propios códigos; si un servicio te da los suyos, guarda esos."

// ---- Respuestas inventadas ----

const val ANSWERS_TITLE = "Respuestas inventadas para preguntas de seguridad"
const val ANSWERS_EXPLANATION =
    "Las respuestas a preguntas como «¿tu primera mascota?» son fáciles de adivinar o de encontrar en redes sociales. " +
        "Es mejor responder con palabras al azar que no tengan que ver contigo y guardarlas en la bóveda: el servicio " +
        "solo comprueba que coincidan."
const val ANSWERS_LIMIT =
    "Úsalo solo en las preguntas que eliges tú para recuperar una cuenta. Nunca en los datos que te piden para " +
        "identificarte (nombre, DNI, fecha de nacimiento, dirección, teléfono): esos tienen que ser verdaderos."
const val ANSWERS_WARNINGS =
    "Si te las pueden pedir por teléfono, ten la bóveda a mano. Si pierdes la bóveda y sus copias, pierdes estas respuestas."
const val ANSWERS_NO_ACCENTS = "Sin tildes"
const val ANSWERS_NO_ACCENTS_NOTE = "Muchos servicios no aceptan tildes ni «ñ». Guardamos la forma exacta que ves."

// ---- StrongBox y pendientes ----

const val KEY_LOCATION_TITLE = "Dónde está la clave de la huella"
const val KEY_LOCATION_STRONGBOX = "Chip seguro (StrongBox)"
const val KEY_LOCATION_TEE = "Zona segura del procesador"
const val KEY_LOCATION_SOFTWARE = "Software: el móvil no protege esta clave con hardware"

/** R2, shown once. */
const val STRONGBOX_AVAILABLE_NOTICE = "Este móvil tiene chip seguro. Para usarlo, desactiva la huella y vuelve a activarla."

/** A6: goes before the design's text. */
const val PASSWORD_CHANGE_COPIES = "Cambiar la contraseña no cambia las copias que ya existen."

/** A12, in the dialog. */
const val RESET_WIPES_DAMAGED =
    "Se borrarán la bóveda y también los archivos dañados que la app guardó al intentar repararla. No se puede deshacer."

/** Every text above, the parameterised ones with the annex's placeholders; ToolTextsTest checks them. */
val ALL_TOOL_TEXTS: List<String> = listOf(
    PHOTO_TOOL_TITLE, PHOTO_TOOL_SUBTITLE, PHOTO_EXPLANATION, PHOTO_PICK, PHOTO_PICK_OTHER, PHOTO_FOUND_TITLE,
    PHOTO_ANALYSING, photoGpsLine(40.42, -3.70),
) + PHOTO_FOUND_TEXTS.values + PHOTO_REMOVED_NAMES.values + listOf(
    PHOTO_DONE_CONVERTED, PHOTO_DONE_SHORT, "$PHOTO_DONE_CONVERTED $PHOTO_ICC_KEPT.", PHOTO_NOTHING_FOUND,
    PHOTO_HEIF_NOTICE, PHOTO_FILE_NAME_NOTE, PHOTO_SAVE_COPY, SHARE,
    PHOTO_TOO_BIG, PHOTO_SAVE_FAILED, PHOTO_SHARE_FAILED, PHOTO_LOST, photoDoneText("[lista]"),
    PHOTO_DAMAGED, PHOTO_UNSUPPORTED,
    REDACT_TOOL_TITLE, REDACT_TOOL_SUBTITLE, REDACT_EXPLANATION, REDACT_LIMITS, REDACT_DOWNSCALED, redactDownscaledText(6),
    REDACT_PICK, REDACT_PICK_OTHER, REDACT_OPENING, REDACT_RENDERING, REDACT_UNDO, REDACT_REDO, REDACT_TOO_BIG,
    REDACT_DAMAGED, REDACT_COPY_FAILED, REDACT_LOST,
    QR_EXPLANATION, QR_NO_ALERTS, qrAlertsTitle("N"), QR_MIXED_SCRIPTS, qrPunycode("…"), QR_IP_HOST, QR_HTTP,
    qrUserInfo("[host]"), qrPort("[:puerto]"), QR_SHORTENER, QR_SUBDOMAINS, qrScheme("[esquema]"), qrBidi("⟨U+202E⟩"),
    QR_WIFI_OPEN, QR_WIFI_WEP, QR_SEPA, QR_CRYPTO, QR_SMS, QR_SGK1,
    pinText("N", "X"), RECOVERY_CODES_TEXT,
    ANSWERS_TITLE, ANSWERS_EXPLANATION, ANSWERS_LIMIT, ANSWERS_WARNINGS, ANSWERS_NO_ACCENTS, ANSWERS_NO_ACCENTS_NOTE,
    KEY_LOCATION_TITLE, KEY_LOCATION_STRONGBOX, KEY_LOCATION_TEE, KEY_LOCATION_SOFTWARE, STRONGBOX_AVAILABLE_NOTICE,
    PASSWORD_CHANGE_COPIES, RESET_WIPES_DAMAGED,
)
