package app.seguridad.imports

import app.seguridad.vault.EntryType

/*
 * Every text of «Importar de otra app» that the user sees (rulings R2 and R7). The names of other products may appear
 * only in the string literals of this file (and in the README); the rest of the code refers to these constants.
 * Kotlin identifiers such as ImportFormat.BITWARDEN are not visible text.
 */

/** The tags that say where an imported entry came from (spec «Etiquetas»): they become visible vault tags. */
const val ORIGIN_TAG_BROWSER = "importado-navegador"
const val ORIGIN_TAG_FIREFOX = "importado-firefox"
const val ORIGIN_TAG_APPLE = "importado-apple"
const val ORIGIN_TAG_BITWARDEN = "importado-bitwarden"
const val ORIGIN_TAG_AEGIS = "importado-aegis"
const val ORIGIN_TAG_TWOFAS = "importado-2fas"
const val ORIGIN_TAG_OTPAUTH = "importado-otpauth"

/** [SkipReason.NOT_TOTP]: it names a code type of other apps. */
const val SKIP_NOT_TOTP = "Código que no es TOTP (HOTP, Steam…)"

/** The file passes [IMPORT_MAX_BYTES]: refused before anything in it is decoded. */
const val IMPORT_FILE_TOO_BIG = "El archivo pasa de 16 MB. Clavero no lo lee."

/** The bytes are not valid UTF-8 (with or without BOM) nor UTF-16 with BOM. */
const val IMPORT_NOT_TEXT = "El archivo está dañado o no es texto."

/** The content is not one of the formats Clavero reads. */
const val IMPORT_UNKNOWN = "Clavero no reconoce este archivo."

/** More than [IMPORT_MAX_ITEMS] rows or elements. */
const val IMPORT_TOO_MANY_ITEMS = "El archivo tiene más de 20 000 elementos: la bóveda resultante no cabría en una copia."

/** A quoted CSV field that never closes: everything after it would be one field. */
const val IMPORT_CSV_UNCLOSED_QUOTE = "El archivo está dañado: unas comillas no se cierran."

/** A CSV row with more than [CSV_MAX_COLUMNS] columns. */
const val IMPORT_CSV_TOO_MANY_COLUMNS = "El archivo tiene filas con más de 64 columnas. Clavero no lo lee."

/** A CSV with more than [CSV_MAX_CELLS] cells in all. */
const val IMPORT_CSV_TOO_MANY_CELLS = "El archivo tiene demasiadas celdas. Clavero no lo lee."

/** Anything else a reader could not read: never the text of the exception, which could carry a value from the file. */
const val IMPORT_BROKEN = "El archivo está dañado o Clavero no sabe leerlo."

/**
 * A Bitwarden JSON encrypted with the account key (spec «Bitwarden protegido con contraseña»): only Bitwarden can
 * open it. Ruling R19: the options are named as Bitwarden's export screen shows them («Formato de archivo»: «.json»,
 * «.json (Encrypted)»; «Tipo de exportación»: «Cuenta restringida» or «Protegida con contraseña»).
 */
const val IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED =
    "Esta exportación está cifrada con tu cuenta de Bitwarden y solo Bitwarden puede abrirla. Vuelve a exportar " +
        "con el formato «.json», o con «.json (Encrypted)» y el tipo «Protegida con contraseña» (no «Cuenta restringida»)."

/*
 * Encrypted exports (Task 10). Fixed texts: never what an exception said, never a value from the file.
 */

/**
 * The password does not open the file, or the encrypted part was changed: the HMAC (Bitwarden) and the GCM tag (Aegis,
 * 2FAS) cannot tell the two apart, so the text says both.
 */
const val IMPORT_WRONG_PASSWORD = "La contraseña no es correcta o el archivo está dañado."

/** The key derivation the file asks for is outside the bounds (ImportKdf.kt): refused before any work. */
const val IMPORT_KDF_TOO_BIG =
    "Este archivo pide más memoria o más cálculo del que Clavero puede usar en este móvil. Vuelve a exportarlo con los " +
        "ajustes normales de la otra app o en un formato sin cifrar."

/** The encrypted part passes [IMPORT_MAX_ENCRYPTED_CHARS]: refused before it is decoded. */
const val IMPORT_ENCRYPTED_TOO_BIG = "La parte cifrada de este archivo pasa de 8 MB. Clavero no la lee."

/**
 * Ruling R19: a `.zip` (it starts with `PK\x03\x04`), as Bitwarden's «.zip (with attachments)»: told before anything is
 * parsed.
 */
const val IMPORT_ZIP =
    "Es un archivo .zip, y Clavero no lee archivos .zip (como la exportación «.zip (with attachments)» de Bitwarden). " +
        "Exporta en el formato «.json»: los adjuntos no se importan."

/** Ruling R21: an HTML page (as Aegis's HTML export): told before anything is parsed, and never shown as a page. */
const val IMPORT_HTML =
    "Es una página HTML, y Clavero no lee exportaciones en HTML. Vuelve a exportar en «.json» o en «.txt» (en Aegis, " +
        "por ejemplo, la exportación JSON o TXT)."

/**
 * Ruling R21 (worded again by Task 10's review, M-7): how to bring the codes of Google Authenticator. The buttons named
 * are the vault's («Añadir», the type «Código 2FA» and «Escanear QR»), whose scanner already reads
 * `otpauth-migration://`. Sources, checked on 8-11-2026:
 * - export only as QR codes, more than one for several accounts: Google's help «Transfer Google Authenticator codes to
 *   a new phone», https://support.google.com/accounts/answer/1066447 («Transfer codes» → «Export codes»; «If you
 *   transfer more than one account, your old device may create more than one QR code»);
 * - no screenshot of that screen on Android (on iOS there may be): 2FAS's help,
 *   https://2fas.com/support/2fas-auth-mobile-app/how-to-move-transfer-tokens-codes-from-google-authenticator-to-2fas/
 *   («Android, unfortunately, doesn't allow you to take screenshots in the Google Authenticator app»);
 * - Aegis's option «Exportar a Google Authenticator» («Genera códigos QR de exportación compatibles con Google
 *   Authenticator»), shown as QR codes on the screen, not as a file: Aegis's Spanish interface texts
 *   (app/src/main/res/values-es-rES/strings.xml, pref_google_auth_export_title and _descriptor, read as UI text only)
 *   and its v3.3 release notes («QR codes exported for Google Authenticator»),
 *   https://github.com/beemdevelopment/Aegis/releases/tag/v3.3.
 */
const val IMPORT_GOOGLE_AUTHENTICATOR =
    "Google Authenticator solo exporta sus códigos como uno o varios códigos QR, y en Android no deja hacer captura de " +
        "esa pantalla. Para traerlos, muestra esos QR en otro dispositivo y escanéalos uno a uno con Clavero: en la " +
        "bóveda, pulsa «Añadir», elige «Código 2FA» y luego «Escanear QR». La opción «Exportar a Google Authenticator» " +
        "de Aegis también muestra códigos QR, y se escanean igual."

/** A JSON nested deeper than [JSON_MAX_DEPTH]: refused before it is parsed. */
const val IMPORT_JSON_TOO_DEEP = "El archivo tiene más de 64 niveles anidados. Clavero no lo lee."

/** A JSON whose parsed tree would pass [JSON_MAX_TREE_BYTES], by an estimate made before parsing: refused unparsed. */
const val IMPORT_JSON_TOO_MANY_VALUES = "El archivo tiene demasiados datos. Clavero no lo lee."

/** The name a line of an `otpauth://` list is listed by in «no entran» when it names no service nor account. */
fun importLineTitle(line: Int): String = "Línea $line"

/** The name of the line that keeps each URI of a login after the first one, in its notes. */
const val NOTE_LINE_URL = "URL"

/** The fields of a Bitwarden card, by their JSON name, and the label of their line in the note («Titular: …»). */
val BITWARDEN_CARD_LINES: List<Pair<String, String>> = listOf(
    "cardholderName" to "Titular",
    "brand" to "Marca",
    "number" to "Número",
    "expMonth" to "Mes de caducidad",
    "expYear" to "Año de caducidad",
    "code" to "Código de seguridad",
)

/** The fields of a Bitwarden identity, by their JSON name, and the label of their line in the note. */
val BITWARDEN_IDENTITY_LINES: List<Pair<String, String>> = listOf(
    "title" to "Tratamiento",
    "firstName" to "Nombre",
    "middleName" to "Segundo nombre",
    "lastName" to "Apellidos",
    "address1" to "Dirección",
    "address2" to "Dirección 2",
    "address3" to "Dirección 3",
    "city" to "Ciudad",
    "state" to "Provincia o estado",
    "postalCode" to "Código postal",
    "country" to "País",
    "company" to "Empresa",
    "email" to "Correo",
    "phone" to "Teléfono",
    "ssn" to "Número de seguridad social",
    "username" to "Usuario",
    "passportNumber" to "Pasaporte",
    "licenseNumber" to "Permiso de conducir",
)

/**
 * The start of a Steam code as Bitwarden stores it in `login.totp` (not a visible text, but it names a product, so its
 * literal lives here, ruling R2): it is [SkipReason.NOT_TOTP].
 */
const val BITWARDEN_STEAM_PREFIX = "steam://"

/** The label of a custom field that has no name of its own, in the notes («Campo: valor»). */
const val NOTE_LINE_UNNAMED_FIELD = "Campo"

/**
 * The line before the notes of the file when «Reemplazar» keeps the vault's notes and adds the file's below (spec «El
 * plan de fusión»): «Importado el D-M-AAAA:», the day of the import, without leading zeros.
 */
fun importedOnHeading(day: Int, month: Int, year: Int): String = "Importado el $day-$month-$year:"

/*
 * The copy before an import (spec «Confirmar: la copia y el guardado»). Fixed texts: never a path, a URI or what an
 * exception said. Each says that nothing was imported, as «sin copia no se importa».
 */
const val IMPORT_COPY_NO_PERMISSION =
    "No se pudo hacer la copia antes de importar: la app ya no tiene permiso para usar la carpeta de la copia " +
        "automática. Vuelve a elegirla en Ajustes. No se ha importado nada."
const val IMPORT_COPY_NO_FOLDER =
    "No se pudo hacer la copia antes de importar: no se encuentra la carpeta de la copia automática. Vuelve a elegirla " +
        "en Ajustes. No se ha importado nada."
const val IMPORT_COPY_WRITE_FAILED =
    "No se pudo escribir la copia antes de importar en la carpeta de la copia automática. ¿Queda espacio? No se ha " +
        "importado nada."
const val IMPORT_COPY_VAULT_PROBLEM =
    "No se pudo hacer la copia antes de importar: un adjunto falta o está dañado, o la bóveda no cabe en una copia. " +
        "Exporta la bóveda a mano para ver el motivo. No se ha importado nada."
const val IMPORT_COPY_LOCKED = "La bóveda se ha bloqueado antes de hacer la copia. No se ha importado nada."
const val IMPORT_COPY_FAILED = "No se pudo hacer la copia antes de importar. No se ha importado nada."

/**
 * The vault was replaced or deleted (a new vault, «Importar copia», «He olvidado la contraseña») while its copy was
 * written: that complete copy stays in the folder, unlisted, and nothing is imported.
 */
const val IMPORT_COPY_VAULT_CHANGED =
    "La bóveda se ha sustituido o borrado mientras se hacía la copia, así que no se ha importado nada. La copia que se " +
        "hizo sigue en la carpeta."

/**
 * The copy was made, but saving the import threw (a full disk, almost always before the atomic rename, so with nothing
 * saved). It claims only what holds on every path: the import did not finish, and the copy was made; the space is
 * asked, not stated.
 */
const val IMPORT_SAVE_FAILED =
    "No se ha podido terminar la importación. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo."

/*
 * The screen «Importar de otra app» (Task 9; spec «Interfaz»). Fixed texts, in Spanish; what comes from the file is never
 * part of them but a value shown apart (plainReportText). Every one goes to the Abogado Digital's final review.
 */
const val IMPORT_TITLE = "Importar de otra app"
const val IMPORT_INTRO =
    "Lee un archivo que hayas exportado de otro gestor de contraseñas, de un navegador o de una app de 2FA, y añade sus " +
        "entradas a la bóveda. Antes de cambiar nada te enseña lo que entra y te pide que lo confirmes."
const val IMPORT_FORMATS =
    "Lee las exportaciones en CSV de Chrome, Edge, Brave, Google Contraseñas, Firefox, Apple y Bitwarden; el JSON de " +
        "Bitwarden, sin cifrar o «Protegida con contraseña»; el JSON de Aegis y la copia de 2FAS, cifrados o sin cifrar, " +
        "y las listas de enlaces otpauth:// (como el TXT de Aegis, y también los enlaces de exportación de Google " +
        "Authenticator). No lee el .zip con adjuntos de Bitwarden ni el HTML de Aegis."
const val IMPORT_TRADEMARKS = "Los nombres de otras apps son marcas de sus titulares. Clavero no tiene relación con ellos."
/** R33: true for a plain export and for a password-protected one (read since Task 10). */
const val IMPORT_NOT_PROTECTED =
    "Clavero no protege el archivo exportado. Si lo exportaste sin contraseña, está sin cifrar en tu móvil; si lo " +
        "protegiste con contraseña, sus entradas siguen cifradas con esa contraseña. En los dos casos puede haber copias " +
        "en la nube, en la papelera o en otras apps. Al terminar, Clavero puede intentar borrar ese archivo (algunas apps " +
        "no lo dejan), pero no esas copias."
const val IMPORT_WIPE_NOTE =
    "Lo leído del archivo, la contraseña que escribas y la vista previa se borran si vuelves atrás, si se cierra la app, " +
        "si la bóveda se bloquea o si estás fuera de esta pantalla más tiempo que el bloqueo de la bóveda (1 minuto como " +
        "mínimo). Entonces hay que volver a empezar."
const val IMPORT_PICK = "Elegir archivo"
const val IMPORT_PICK_OTHER = "Elegir otro archivo"
const val IMPORT_READING = "Leyendo el archivo…"
const val IMPORT_CANCEL = "Cancelar"
const val IMPORT_READ_FAILED = "No se pudo leer el archivo."

/** The file chosen, by its name as the provider gives it (sanitised, then shown through plainReportText). */
fun importFileText(name: String): String = "Archivo: $name"

/** Ruling R21: the password step says whose password it is. */
fun importPasswordIntro(format: ImportFormat): String = when (format) {
    ImportFormat.AEGIS ->
        "Esta exportación de Aegis está cifrada. Escribe la contraseña de la bóveda de Aegis con la que se hizo."
    ImportFormat.TWOFAS -> "Esta copia de 2FAS está cifrada. Escribe la contraseña que pusiste al hacerla en 2FAS."
    ImportFormat.BITWARDEN ->
        "Esta exportación de Bitwarden está protegida con contraseña. Escribe la contraseña que pusiste al exportarla."
    else -> "Este archivo está cifrado. Escribe la contraseña con la que lo exportaste."
}
const val IMPORT_PASSWORD_LABEL = "Contraseña del archivo"
const val IMPORT_PASSWORD_OPEN = "Abrir"
const val IMPORT_PASSWORD_TOO_LONG = "Esa contraseña es demasiado larga: no se ha pegado."

/** Another file's password is still being worked out (one at a time, SingleRun). */
const val IMPORT_KDF_BUSY = "Todavía se está comprobando la contraseña anterior. Inténtalo dentro de unos segundos."

const val IMPORT_PREVIEW_TITLE = "Vista previa"

/** «Entran N entradas nuevas: …», by type, in the order of the vault's types. */
fun previewEntersText(added: Map<EntryType, Int>): String {
    val parts = EntryType.entries.mapNotNull { t -> added[t]?.takeIf { it > 0 }?.let { typeCount(t, it) } }
    val total = added.values.sum()
    if (total == 0) return "No entra ninguna entrada nueva."
    val list = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " y " + parts.last()
    return (if (total == 1) "Entra 1 entrada nueva: " else "Entran $total entradas nuevas: ") + list + "."
}

private fun typeCount(t: EntryType, n: Int): String = when (t) {
    EntryType.LOGIN -> if (n == 1) "1 acceso" else "$n accesos"
    EntryType.NOTE -> if (n == 1) "1 nota" else "$n notas"
    EntryType.TOTP -> if (n == 1) "1 código 2FA" else "$n códigos 2FA"
    else -> "$n ${t.label.lowercase()}" // Never imported: readers build only the three above.
}

fun importReplacedText(n: Int): String =
    if (n == 1) "Se reemplaza 1 entrada de la bóveda." else "Se reemplazan $n entradas de la bóveda."

/** The title of the list of duplicates. */
fun importDuplicatesText(n: Int): String = if (n == 1) "1 ya está en la bóveda" else "$n ya están en la bóveda"

const val IMPORT_DUPLICATES_ALL = "Para todos:"
const val IMPORT_CHOICE_SKIP = "Saltar"
const val IMPORT_CHOICE_KEEP_BOTH = "Conservar los dos"
const val IMPORT_CHOICE_REPLACE = "Reemplazar"
const val IMPORT_REPLACE_NOTE =
    "«Reemplazar» toma del archivo el título, el usuario, la contraseña, la web y el 2FA que no estén vacíos, y suma sus " +
        "notas y sus etiquetas (hasta 10 por entrada) a las de la bóveda. La contraseña anterior queda en el historial; " +
        "los adjuntos y el favorito no cambian."

fun importRepeatedText(n: Int): String =
    if (n == 1) "1 elemento está repetido dentro del archivo: entra solo una vez." else "$n elementos están repetidos dentro del archivo: entra solo uno de cada."

fun importBecameNotesText(n: Int): String =
    if (n == 1) "1 tarjeta o identidad entra como nota." else "$n tarjetas o identidades entran como notas."

/** Most new tags the preview names; the rest are counted ([importNewTagsText]). */
const val IMPORT_TAGS_SHOWN = 20

/** The new tags: the first [IMPORT_TAGS_SHOWN] in the order given (that of the merged entries, not sorted), then «y N más». */
fun importNewTagsText(tags: List<String>): String {
    val shown = tags.take(IMPORT_TAGS_SHOWN).joinToString(", ") { "#$it" }
    val more = tags.size - IMPORT_TAGS_SHOWN
    return "Etiquetas nuevas: " + shown + if (more > 0) " y $more más." else ""
}

/**
 * Ruling R18: [n] distinct folders (as tags normalise them) whose new tag [IMPORT_MAX_NEW_TAGS] really kept off their
 * entries ([MergeResult.foldersNotTags]); those entries keep the origin tag and the tags that already existed.
 */
fun importFoldersNotTagsText(n: Int): String =
    (if (n == 1) "1 carpeta del archivo no pasa a etiqueta" else "$n carpetas del archivo no pasan a etiqueta") +
        " en sus entradas: una importación crea como mucho $IMPORT_MAX_NEW_TAGS etiquetas nuevas. Esas entradas llevan la " +
        "etiqueta de origen y las etiquetas que ya existían."
const val IMPORT_NEW_TAGS_NONE = "No se crea ninguna etiqueta nueva."

/** The title of «no entran», with how many. */
const val IMPORT_SKIPPED_TITLE = "No entran"
const val IMPORT_NO_TITLE = "(sin título)"

/** One line of «no entran»: the title (plain, never a password or a note) and why. */
fun importSkippedLine(title: String, reason: String): String = "$title: $reason"

const val IMPORT_CONFIRM = "Importar"
const val IMPORT_CONFIRM_NOTE =
    "Antes de guardar, Clavero hace una copia cifrada de la bóveda tal como está, con tu contraseña maestra: en la " +
        "carpeta de la copia automática o donde elijas. Sin esa copia no se importa nada."
const val IMPORT_ESTIMATING = "Comprobando que la bóveda resultante cabe en una copia…"
const val IMPORT_NOTHING_TO_DO = "Con estas opciones no entra ni cambia nada."
const val IMPORT_SAVING = "Haciendo la copia de antes de importar y guardando…"

/** M-4: the screen was wiped (time away, another app) while the save ran: it goes on, but its result is not shown. */
const val IMPORT_WIPED_WHILE_SAVING =
    "Se ha borrado lo de esta pantalla mientras se guardaba: la importación puede haberse hecho. Mira la bóveda antes " +
        "de volver a importar."

/** No folder for the automatic copies: where to save the copy before importing is asked. */
const val IMPORT_CHOOSE_COPY_PLACE =
    "No hay carpeta para la copia automática. Elige dónde guardar la copia de antes de importar: está cifrada con tu " +
        "contraseña maestra."
const val IMPORT_CHOOSE_COPY_PLACE_BUTTON = "Elegir dónde guardarla"
const val IMPORT_COPY_CANCELLED = "Sin la copia de antes de importar no se importa: no se ha importado nada."
const val IMPORT_COPY_DOCUMENT_FAILED =
    "No se pudo escribir la copia antes de importar en el archivo elegido. ¿Queda espacio? No se ha importado nada."

/** ApplyResult.Stale: the vault changed (or locked) between the preview and the save. */
const val IMPORT_VAULT_CHANGED =
    "La bóveda ha cambiado desde la vista previa, así que no se ha importado nada. Vuelve a elegir el archivo."

/**
 * ApplyResult.TooBig (and the preview's estimate): how much the vault would pass the limits of a copy. Sizes are rounded
 * up (KB under 1 MB, MB with one decimal), so the amount said is never below the real one.
 */
fun importTooBigText(overBytes: Long, overFiles: Int): String {
    val over = when {
        overFiles > 0 -> if (overFiles == 1) "sobra 1 archivo" else "sobran $overFiles archivos"
        overBytes >= 1024L * 1024 -> {
            val tenths = (overBytes * 10 + 1024L * 1024 - 1) / (1024L * 1024)
            "sobran unos ${tenths / 10}" + (if (tenths % 10 != 0L) ",${tenths % 10}" else "") + " MB"
        }
        else -> "sobran unos ${((overBytes + 1023) / 1024).coerceAtLeast(1)} KB"
    }
    return "La bóveda resultante no cabría en una copia: $over. No se ha importado nada."
}

const val IMPORT_DONE_TITLE = "Importación terminada"

fun importDoneText(added: Int, replaced: Int): String {
    val a = when (added) {
        0 -> "No ha entrado ninguna entrada nueva."
        1 -> "Ha entrado 1 entrada nueva."
        else -> "Han entrado $added entradas nuevas."
    }
    val r = when (replaced) {
        0 -> ""
        1 -> " Se ha actualizado 1 entrada de la bóveda."
        else -> " Se han actualizado $replaced entradas de la bóveda."
    }
    return a + r
}

/**
 * Where the copy before importing is ([where]: its name, never a path or a URI). One chosen by hand is not in the
 * automatic copies' folder, so A6 does not delete it after a change of master password: the text says so.
 */
fun importCopyWhereText(where: String, inAutoFolder: Boolean): String =
    if (inAutoFolder) {
        "La copia de antes de importar es «$where», en la carpeta de la copia automática."
    } else {
        "La copia de antes de importar es «$where», donde la guardaste. Clavero no la borra si cambias la contraseña " +
            "maestra: bórrala tú cuando ya no la necesites."
    }

const val IMPORT_SKIPPED_DONE_TITLE = "No han entrado"
const val IMPORT_ENDED_TITLE = "No se ha terminado la importación"

/** «Quitar el archivo exportado» (spec «Quitar el archivo exportado»). */
const val IMPORT_REMOVE = "Quitar el archivo exportado"
/** R33: «sin cifrar» only for an export made without a password. */
const val IMPORT_REMOVE_EXPLAIN =
    "El archivo que has importado sigue en tu móvil, sin cifrar si lo exportaste sin contraseña. Clavero puede intentar " +
        "borrar ese mismo archivo; algunas apps no lo dejan. No borra las copias que haya en la nube, en la papelera ni en " +
        "otras apps, y no garantiza que el almacenamiento lo borre físicamente."
const val IMPORT_REMOVE_DECLINE = "No, déjalo"
const val IMPORT_REMOVING = "Borrando el archivo…"
const val IMPORT_REMOVED =
    "Archivo borrado. Mira si queda alguna copia en la nube, en la papelera o en otras apps."
const val IMPORT_REMOVE_FAILED =
    "No se pudo borrar el archivo. Bórralo a mano desde la app donde lo guardaste, y mira también la papelera y la nube."
const val IMPORT_REMOVE_UNSUPPORTED =
    "La app donde está el archivo no deja que Clavero lo borre. Bórralo a mano desde ella, y mira también la papelera " +
        "y la nube."
const val IMPORT_REMOVE_DECLINED = "De acuerdo. Clavero no lo vuelve a preguntar: bórralo a mano cuando ya no lo necesites."
