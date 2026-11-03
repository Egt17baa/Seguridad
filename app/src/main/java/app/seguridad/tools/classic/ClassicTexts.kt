package app.seguridad.tools.classic

/** The texts of the «Laboratorio clásico» (1.4.0), apart from the screen so a JVM test can read them. */
const val CLASSIC_TITLE = "Laboratorio clásico"
const val CLASSIC_SUBTITLE = "César, Vigenère y la máquina Enigma"

const val CLASSIC_NOTICE =
    "Son cifrados históricos, rotos desde hace décadas: no protegen nada. Sirven para aprender cómo funcionaban."
const val CLASSIC_NOT_SAVED = "Lo que escribes no se guarda en el móvil y la bóveda no interviene."

const val CLASSIC_TAB_CAESAR = "César"
const val CLASSIC_TAB_VIGENERE = "Vigenère"
const val CLASSIC_TAB_ENIGMA = "Enigma"
const val CLASSIC_TAB_ANALYSIS = "Análisis"

const val CLASSIC_ENCRYPT = "Cifrar"
const val CLASSIC_DECRYPT = "Descifrar"
const val CLASSIC_TEXT_LABEL = "Texto"
const val CLASSIC_RESULT = "Resultado"
const val CLASSIC_COPY = "Copiar"
const val CLASSIC_TOO_LONG = "Ese texto es demasiado largo y no se ha pegado."

/** Said under César and Vigenère: what happens to everything that is not A-Z. */
const val CLASSIC_LETTERS_ONLY =
    "Solo se cambian las letras A-Z. La «ñ», las letras con tilde, los números y los signos pasan sin cambiar."

const val CAESAR_SHIFT_LABEL = "Desplazamiento (0 a 25)"
const val CAESAR_SHIFT_INVALID = "El desplazamiento tiene que ser un número entero de 0 a 25."

const val VIGENERE_KEY_LABEL = "Clave (letras A-Z)"
const val VIGENERE_KEY_INVALID = "La clave necesita al menos una letra de la A a la Z."
const val VIGENERE_NOTE = "Las letras de la clave se repiten a lo largo del texto; los signos no gastan letras de la clave."

const val ENIGMA_INTRO =
    "Enigma I y M3 con sus cableados históricos. Cada pulsación hace girar los rotores antes de cifrar, y por eso una " +
        "letra nunca sale como ella misma."
const val ENIGMA_ROTORS = "Rotores (Walzenlage), de izquierda a derecha"
const val ENIGMA_ROTOR_LEFT = "Izquierdo"
const val ENIGMA_ROTOR_MIDDLE = "Central"
const val ENIGMA_ROTOR_RIGHT = "Derecho"
const val ENIGMA_REFLECTOR = "Reflector (Umkehrwalze)"
const val ENIGMA_RINGS_LABEL = "Anillos (Ringstellung), tres números de 1 a 26"
const val ENIGMA_POSITIONS_LABEL = "Posición inicial (Grundstellung), tres letras"
const val ENIGMA_PLUGS_LABEL = "Tablero de conexiones (Steckerbrett), por ejemplo AV BS CG"
const val ENIGMA_REPEATED_ROTOR = "Cada rotor solo puede usarse una vez."
const val ENIGMA_RINGS_INVALID = "Los anillos son tres números enteros de 1 a 26, por ejemplo 1 1 1."
const val ENIGMA_POSITIONS_INVALID = "La posición inicial son tres letras de la A a la Z, por ejemplo AAA."
const val ENIGMA_PLUG_FORMAT = "Cada conexión son dos letras distintas de la A a la Z, separadas por espacios."
const val ENIGMA_PLUG_REPEATED = "Una letra solo puede estar en una conexión."
const val ENIGMA_PLUG_TOO_MANY = "El tablero admite 13 conexiones como máximo."
const val ENIGMA_LETTERS_ONLY =
    "Solo cuentan las letras A-Z: las minúsculas se leen como mayúsculas y salen en mayúsculas. Lo demás (la «ñ», las " +
        "tildes, los números, los espacios) pasa sin cambiar y no hace girar los rotores."
const val ENIGMA_SAME_KEY = "Para descifrar, escribe el texto cifrado con los mismos ajustes: Enigma deshace lo que hizo."

fun enigmaFinalPositions(positions: String) = "Posición de los rotores al terminar: $positions"

const val ANALYSIS_INTRO = "Cuenta las letras de un texto y mide su índice de coincidencia, para ver a qué se parece."
const val ANALYSIS_TEXT_LABEL = "Texto a analizar"
const val ANALYSIS_TOO_FEW = "Hacen falta al menos dos letras A-Z para medir el índice de coincidencia."
const val ANALYSIS_REFERENCE =
    "Referencia: inglés 0,067, español 0,078 y letras al azar 0,038. Con pocas letras la medida varía mucho."
const val ANALYSIS_LOOKS_NORMAL =
    "Se parece al de un texto normal: es lo que da un texto sin cifrar o cifrado cambiando cada letra siempre por la misma (como César)."
const val ANALYSIS_LOOKS_RANDOM =
    "Se parece al de letras al azar: es lo que da un cifrado que cambia de alfabeto a cada letra (como Vigenère o Enigma)."
const val ANALYSIS_IN_BETWEEN = "Queda entre los dos: con este texto no se puede decir a cuál se parece."
const val ANALYSIS_FREQUENCIES = "Letras, de la más a la menos frecuente"

fun analysisLetters(n: Int) = "Letras A-Z: $n"
fun analysisIc(ic: String) = "Índice de coincidencia: $ic"
