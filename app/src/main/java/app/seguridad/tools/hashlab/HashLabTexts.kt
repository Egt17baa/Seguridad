package app.seguridad.tools.hashlab

/** The texts of the «Laboratorio de hashes» (1.4.0), apart from the screen so a JVM test can read them. */
const val HASH_LAB_TITLE = "Laboratorio de hashes"
const val HASH_LAB_SUBTITLE = "SHA-2, SHA-3, BLAKE y HMAC de texto y archivos"

const val HASH_LAB_INTRO =
    "Calcula la huella de un texto o de un archivo y compárala con la que publica su autor, por ejemplo la de una descarga " +
        "o la del firmware de una cartera física. Todo se calcula en tu móvil."

/** «Lo que no protege»: what a matching hash does not say. */
const val HASH_LAB_NOT_PROTECTED_TITLE = "Lo que no protege"
const val HASH_LAB_NOT_PROTECTED =
    "Que la huella coincida solo dice que el archivo es el mismo que el de la huella. No dice que la huella venga de quien " +
        "dice venir: si te la dio la misma web que el archivo, quien cambie uno puede cambiar la otra."

const val HASH_LAB_TAB_TEXT = "Texto"
const val HASH_LAB_TAB_FILE = "Archivo"
const val HASH_LAB_TAB_HMAC = "HMAC"
const val HASH_LAB_TAB_CODEC = "Base64/hex"

const val HASH_LAB_EXPECTED_LABEL = "Huella esperada (hex o Base64)"
const val HASH_LAB_MATCH = "✓ Coincide"
const val HASH_LAB_NO_MATCH = "✗ No coincide"
const val HASH_LAB_EXPECTED_INVALID =
    "La huella esperada no es válida: tiene que ser hex o Base64 de 32 o de 64 bytes."

const val HASH_LAB_TEXT_LABEL = "Texto (se calcula en UTF-8)"
const val HASH_LAB_TOO_LONG = "Ese texto es demasiado largo y no se ha pegado."
const val HASH_LAB_PICK_FILE = "Elegir archivo"
const val HASH_LAB_FILE_FAILED = "No se pudo leer el archivo."
const val HASH_LAB_CANCEL = "Cancelar"
const val HASH_LAB_COPY = "Copiar"

const val HASH_LAB_HMAC_INTRO =
    "HMAC-SHA256 con una clave que escribes. La clave y el mensaje no se guardan en el móvil: solo están en esta pantalla, " +
        "y el aviso de abajo dice cuándo se borran."
const val HASH_LAB_KEY_LABEL = "Clave"
const val HASH_LAB_KEY_AS_TEXT = "Clave en texto"
const val HASH_LAB_KEY_AS_HEX = "Clave en hex"
const val HASH_LAB_KEY_INVALID = "La clave en hex no es válida."
const val HASH_LAB_KEY_EMPTY = "Escribe una clave."
const val HASH_LAB_MESSAGE_LABEL = "Mensaje (UTF-8)"

const val HASH_LAB_ENCODE = "Codificar"
const val HASH_LAB_DECODE = "Decodificar"
const val HASH_LAB_CODEC_INPUT = "Entrada"
const val HASH_LAB_CODEC_INVALID = "La entrada no es válida para ese formato."
const val HASH_LAB_NOT_TEXT = "No es texto UTF-8: se muestra en hex."

fun hashLabBusy(done: String, total: String?): String =
    if (total == null) "Calculando… $done" else "Calculando… $done de $total"

const val HASH_LAB_ALGO_CHANGED = "Elegiste otro algoritmo: el resultado de antes ya no vale."
fun hashLabRecalc(algo: String) = "Calcular otra vez con $algo"

/** The pasted hash is [want] bytes long and the result is not: another algorithm's size (or HMAC-SHA256's, [hmac]). */
fun hashLabWrongSize(want: Int, hmac: Boolean): String {
    val names = if (want == 64) "SHA-512 o BLAKE2b-512" else "SHA-256, SHA3-256 o BLAKE3-256"
    return "Esta huella tiene $want bytes, el tamaño de $names." +
        if (hmac) " HMAC-SHA256 da 32 bytes." else " Elige uno de esos algoritmos."
}
