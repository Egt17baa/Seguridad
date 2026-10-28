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

/** Not in the annex: a HEIC/HEIF/AVIF whose header declares more than the conversion accepts (MAX_DECLARED_*). */
const val PHOTO_TOO_LARGE =
    "La foto es demasiado grande para convertirla: pasa de 260 megapíxeles o de 32.768 píxeles de lado. No se ha " +
        "guardado nada."

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
const val REDACT_TOO_LARGE =
    "La imagen es demasiado grande para editarla: pasa de 260 megapíxeles o de 32.768 píxeles de lado."
const val REDACT_CHANGED =
    "Los bloques cambiaron mientras se preparaba la copia, así que no se ha guardado ni compartido. Vuelve a intentarlo."
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

/** Not in the annex: the text is over [app.seguridad.tools.qr.MAX_QR_TEXT_BYTES]; nothing is analysed. */
const val QR_TOO_BIG = "El código QR tiene demasiado texto para inspeccionarlo."
const val QR_SGK1 =
    "Se añadirá como contacto sin verificar. Para verificarlo, compara la huella con la persona, en persona o por llamada."

/** Not in the annex: the tile in Herramientas (the plan's words). */
const val QR_TOOL_TITLE = "Inspector de QR"
const val QR_TOOL_SUBTITLE = "Mira qué hay en un QR sin abrirlo"

/**
 * Not in the annex: the hidden-characters line when the marks are not in the text as shown (percent-encoded in the
 * name, a selector after an emoji of the name, a line break), so «marcados como ⟨U+202E⟩» would not be true. Built on
 * the annex line; goes to the Abogado Digital with the other new texts.
 */
const val QR_HIDDEN_ELSEWHERE =
    "Hay caracteres invisibles o que cambian el orden del texto escondidos en el enlace (codificados con «%», junto a " +
        "un emoji del nombre o como saltos de línea). Pueden hacer que el texto parezca otro."

/** Not in the annex: buttons, progress, the hidden full text and plain failure messages, without internal details. */
const val QR_SCAN = "📷 Escanear QR"
const val QR_PICK_IMAGE = "Elegir imagen"
const val QR_READING_IMAGE = "Buscando el código en la imagen…"
const val QR_FULL_TEXT = "Texto completo"
const val QR_TEXT_HIDDEN_PASSWORD = "Lleva la contraseña de la red: toca «Mostrar» para verlo."
const val QR_TEXT_HIDDEN_OTP = "Lleva la clave secreta del código 2FA: toca «Mostrar» para verlo."
const val QR_SHOW = "Mostrar"
const val QR_HIDE = "Ocultar"
const val QR_COPY = "Copiar"
const val QR_COPY_PASSWORD = "Copiar contraseña"
const val QR_ADD_CONTACT = "Añadir contacto"
const val QR_OPEN_RECEIVED = "Abrir lo recibido"
const val QR_ADD_TOTP = "Añadir a la bóveda"
const val QR_NOT_FOUND =
    "No hemos encontrado ningún código QR en la imagen. Prueba con otra en la que el código se vea entero y nítido."
const val QR_IMAGE_TOO_BIG = "La imagen ocupa más de 50 MB."
const val QR_IMAGE_TOO_LARGE =
    "La imagen es demasiado grande para buscar el código: pasa de 260 megapíxeles o de 32.768 píxeles de lado."
const val QR_IMAGE_DAMAGED =
    "No se ha podido abrir esta imagen: el archivo está dañado o tiene un formato que no conocemos."
const val QR_NO_CAMERA_PERMISSION = "Sin permiso de cámara: elige una imagen con el código QR."
const val QR_CAMERA_FAILED = "No se ha podido abrir la cámara: elige una imagen con el código QR."

/** Not in the annex: after «Copiar» when what is copied carries the hidden characters the alerts name (never stripped). */
const val QR_COPIED_WITH_HIDDEN = "Copiado tal cual, con los caracteres ocultos que señalan los avisos."

/** Not in the annex: what each kind of QR is called, and the names of the fields shown for it. */
const val QR_KIND_LINK = "Enlace"
const val QR_KIND_WIFI = "Red Wi-Fi"
const val QR_KIND_SEPA = "Pago SEPA"
const val QR_KIND_CRYPTO = "Pago en criptomonedas"
const val QR_KIND_CONTACT = "Contacto"
const val QR_KIND_SMS = "SMS"
const val QR_KIND_PHONE = "Llamada"
const val QR_KIND_EMAIL = "Correo"
const val QR_KIND_GEO = "Ubicación"
const val QR_KIND_CARD = "Tarjeta de contacto de Seguridad"
const val QR_KIND_RECEIVED = "Mensaje cifrado o texto firmado de Seguridad"
const val QR_KIND_OTP = "Código 2FA"
const val QR_KIND_TEXT = "Texto"

/** Over the highlighted name: the ASCII one the browser connects to. */
const val QR_FIELD_WEB = "Web"

/** Under the highlighted name, smaller, when its Unicode reading is not the same (punycode). */
fun qrHostReading(unicode: String) = "Se lee así: «$unicode»"
const val QR_FIELD_PORT = "Puerto"
const val QR_FIELD_SCHEME = "Tipo de enlace"
const val QR_SCHEME_NONE = "Sin indicar"
const val QR_FIELD_NETWORK = "Red"
const val QR_FIELD_CIPHER = "Cifrado"
const val QR_CIPHER_NONE = "Ninguno: red abierta"
const val QR_CIPHER_UNKNOWN = "Sin indicar"
const val QR_FIELD_HIDDEN_NETWORK = "Red oculta"
const val QR_YES = "Sí"
const val QR_FIELD_BENEFICIARY = "Beneficiario"
const val QR_FIELD_IBAN = "IBAN"
const val QR_FIELD_AMOUNT = "Importe"
const val QR_FIELD_REFERENCE = "Concepto"
const val QR_FIELD_COIN = "Moneda"
const val QR_FIELD_ADDRESS = "Dirección"
const val QR_FIELD_NAME = "Nombre"
const val QR_FIELD_PHONE = "Teléfono"
const val QR_FIELD_EMAIL = "Correo"
const val QR_FIELD_TO = "Para"
const val QR_FIELD_SUBJECT = "Asunto"
const val QR_FIELD_MESSAGE = "Mensaje"
const val QR_FIELD_NUMBER = "Número"
const val QR_FIELD_LATITUDE = "Latitud"
const val QR_FIELD_LONGITUDE = "Longitud"
const val QR_FIELD_SERVICE = "Servicio"
const val QR_FIELD_ACCOUNT = "Cuenta"
const val QR_FIELD_PASSWORD = "Contraseña"

// ---- PIN y códigos ----

/** [count] and [bits] replace «N» and «X». */
fun pinText(count: String, bits: String) =
    "PIN al azar, sin los patrones más usados (años, fechas, repeticiones, secuencias y líneas del teclado). Quedan " +
        "$count PIN posibles (unos $bits bits). Un PIN corto protege porque el móvil o la tarjeta se bloquean tras pocos " +
        "intentos: no lo uses como contraseña."

/** The number with a point between thousands and the bits with one decimal and a comma (Spanish), then [pinText]. */
fun pinCountText(count: Long, bits: Double): String =
    pinText(count.toString().reversed().chunked(3).joinToString(".").reversed(), String.format(Locale.ROOT, "%.1f", bits).replace('.', ','))

// Not in the annex: the names and labels of the screen «PIN y códigos» and the titles of the notes it saves.
const val CODES_TOOL_TITLE = "PIN y códigos"
const val CODES_TOOL_SUBTITLE = "PIN, códigos de recuperación y respuestas inventadas"
const val CODES_PIN_TITLE = "PIN"
const val CODES_PIN_LENGTH = "Longitud del PIN"
const val CODES_PIN_COUNTING = "Calculando cuántos PIN quedan…"
const val CODES_RECOVERY_TITLE = "Códigos de recuperación"
const val CODES_COPY = "Copiar"
const val CODES_COPY_ALL = "Copiar todos"
const val CODES_ANOTHER_PIN = "Otro PIN"
const val CODES_ANOTHER_CODES = "Otros códigos"
const val CODES_SAVE_AS_NOTE = "Guardar en la bóveda como nota"
const val CODES_SITE_LABEL = "Web o servicio"
const val CODES_SITE_NEEDED = "Escribe la web o el servicio para dar nombre a la nota."
const val CODES_QUESTION_LABEL = "Pregunta"
const val CODES_UNLOCK_TO_SAVE = "Desbloquea la bóveda para guardarlo. Lo que ves aquí se conserva mientras tanto."
const val CODES_CANCEL_SAVE = "No guardar"
const val CODES_SAVED = "Guardado en la bóveda como nota."
const val CODES_SAVE_FAILED = "No se pudo guardar en la bóveda."

fun recoveryNoteTitle(site: String) = "Códigos de recuperación · $site"
fun answersNoteTitle(site: String) = "Respuestas de seguridad · $site"

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
const val ANSWERS_OTHER = "Otra respuesta"
const val ANSWERS_ADD_QUESTION = "Añadir pregunta"
const val ANSWERS_SAVE = "Guardar en la bóveda"
const val ANSWERS_BLANK_QUESTION = "Cada respuesta necesita su pregunta: escríbela o quita la pregunta que no vayas a usar."
const val ANSWERS_REMOVE_QUESTION = "Quitar pregunta"

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
    PHOTO_TOO_BIG, PHOTO_TOO_LARGE, PHOTO_SAVE_FAILED, PHOTO_SHARE_FAILED, PHOTO_LOST, photoDoneText("[lista]"),
    PHOTO_DAMAGED, PHOTO_UNSUPPORTED,
    REDACT_TOOL_TITLE, REDACT_TOOL_SUBTITLE, REDACT_EXPLANATION, REDACT_LIMITS, REDACT_DOWNSCALED, redactDownscaledText(6),
    REDACT_PICK, REDACT_PICK_OTHER, REDACT_OPENING, REDACT_RENDERING, REDACT_UNDO, REDACT_REDO, REDACT_TOO_BIG,
    REDACT_TOO_LARGE, REDACT_CHANGED, REDACT_DAMAGED, REDACT_COPY_FAILED, REDACT_LOST,
    QR_EXPLANATION, QR_NO_ALERTS, qrAlertsTitle("N"), QR_MIXED_SCRIPTS, qrPunycode("…"), QR_IP_HOST, QR_HTTP,
    qrUserInfo("[host]"), qrPort("[:puerto]"), QR_SHORTENER, QR_SUBDOMAINS, qrScheme("[esquema]"), qrBidi("⟨U+202E⟩"),
    QR_WIFI_OPEN, QR_WIFI_WEP, QR_SEPA, QR_CRYPTO, QR_SMS, QR_SGK1, QR_TOO_BIG,
    QR_TOOL_TITLE, QR_TOOL_SUBTITLE, QR_HIDDEN_ELSEWHERE, QR_SCAN, QR_PICK_IMAGE, QR_READING_IMAGE, QR_FULL_TEXT,
    QR_TEXT_HIDDEN_PASSWORD, QR_TEXT_HIDDEN_OTP, QR_SHOW, QR_HIDE, QR_COPY, QR_COPY_PASSWORD, QR_ADD_CONTACT,
    QR_OPEN_RECEIVED, QR_ADD_TOTP, QR_NOT_FOUND, QR_IMAGE_TOO_BIG, QR_IMAGE_TOO_LARGE, QR_IMAGE_DAMAGED,
    QR_NO_CAMERA_PERMISSION, QR_CAMERA_FAILED,
    QR_KIND_LINK, QR_KIND_WIFI, QR_KIND_SEPA, QR_KIND_CRYPTO, QR_KIND_CONTACT, QR_KIND_SMS, QR_KIND_PHONE, QR_KIND_EMAIL,
    QR_KIND_GEO, QR_KIND_CARD, QR_KIND_RECEIVED, QR_KIND_OTP, QR_KIND_TEXT,
    QR_FIELD_WEB, qrHostReading("…"), QR_COPIED_WITH_HIDDEN, QR_FIELD_PORT, QR_FIELD_SCHEME, QR_SCHEME_NONE, QR_FIELD_NETWORK, QR_FIELD_CIPHER,
    QR_CIPHER_NONE, QR_CIPHER_UNKNOWN, QR_FIELD_HIDDEN_NETWORK, QR_YES, QR_FIELD_BENEFICIARY, QR_FIELD_IBAN,
    QR_FIELD_AMOUNT, QR_FIELD_REFERENCE, QR_FIELD_COIN, QR_FIELD_ADDRESS, QR_FIELD_NAME, QR_FIELD_PHONE, QR_FIELD_EMAIL,
    QR_FIELD_TO, QR_FIELD_SUBJECT, QR_FIELD_MESSAGE, QR_FIELD_NUMBER, QR_FIELD_LATITUDE, QR_FIELD_LONGITUDE,
    QR_FIELD_SERVICE, QR_FIELD_ACCOUNT, QR_FIELD_PASSWORD,
    pinText("N", "X"), pinCountText(8_463, 13.0), RECOVERY_CODES_TEXT,
    ANSWERS_TITLE, ANSWERS_EXPLANATION, ANSWERS_LIMIT, ANSWERS_WARNINGS, ANSWERS_NO_ACCENTS, ANSWERS_NO_ACCENTS_NOTE,
    ANSWERS_OTHER, ANSWERS_ADD_QUESTION, ANSWERS_SAVE, ANSWERS_BLANK_QUESTION, ANSWERS_REMOVE_QUESTION,
    CODES_TOOL_TITLE, CODES_TOOL_SUBTITLE, CODES_PIN_TITLE, CODES_PIN_LENGTH, CODES_PIN_COUNTING, CODES_RECOVERY_TITLE,
    CODES_COPY, CODES_COPY_ALL, CODES_ANOTHER_PIN, CODES_ANOTHER_CODES, CODES_SAVE_AS_NOTE, CODES_SITE_LABEL,
    CODES_SITE_NEEDED, CODES_QUESTION_LABEL, CODES_UNLOCK_TO_SAVE, CODES_CANCEL_SAVE, CODES_SAVED, CODES_SAVE_FAILED,
    recoveryNoteTitle("[web]"), answersNoteTitle("[web]"),
    KEY_LOCATION_TITLE, KEY_LOCATION_STRONGBOX, KEY_LOCATION_TEE, KEY_LOCATION_SOFTWARE, STRONGBOX_AVAILABLE_NOTICE,
    PASSWORD_CHANGE_COPIES, RESET_WIPES_DAMAGED,
)
