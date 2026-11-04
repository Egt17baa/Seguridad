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
