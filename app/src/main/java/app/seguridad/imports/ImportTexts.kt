package app.seguridad.imports

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

/** A Bitwarden JSON encrypted with the account key (spec «Bitwarden protegido con contraseña»): only Bitwarden can open it. */
const val IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED =
    "Esta exportación está cifrada con tu cuenta de Bitwarden. Expórtala como \"JSON\" o como \"JSON (protegido con contraseña)\"."

/** A JSON nested deeper than [JSON_MAX_DEPTH]: refused before it is parsed. */
const val IMPORT_JSON_TOO_DEEP = "El archivo tiene más de 64 niveles anidados. Clavero no lo lee."

/** A JSON with more than [JSON_MAX_TOKENS] values and names: refused before it is parsed. */
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
