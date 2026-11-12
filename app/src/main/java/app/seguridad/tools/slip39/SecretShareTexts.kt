package app.seguridad.tools.slip39

/** The texts of «Repartir un secreto» (1.4.0), apart from the screen so a JVM test can read them. */
const val SHARE_TITLE = "Repartir un secreto"
const val SHARE_SUBTITLE = "Divide una frase o un secreto en partes (SLIP-0039)"

const val SHARE_INTRO =
    "Divide una frase BIP-39 o un secreto en hex en varias partes, con el reparto de secretos de Shamir de SLIP-0039. " +
        "Con las partes necesarias se recompone el secreto; con menos, no. Todo se calcula en tu móvil y nada se guarda."

/** «Lo que no protege»: who gathers the parts, the passphrase that gives another secret, and what the parts are not. */
const val SHARE_NOT_PROTECTED_TITLE = "Lo que no protege"
const val SHARE_NOT_PROTECTED =
    "Quien reúna las partes necesarias obtiene el secreto, sea quien sea; si pusiste contraseña, le hace falta también. " +
        "Guarda cada parte en un sitio distinto. Una contraseña equivocada no da ningún error: da otro secreto distinto, " +
        "sin avisar. Y estas partes no son una copia SLIP-39 de cartera: lee el aviso."

/**
 * Ruling R3 of the controller (owner's rules: no other product's name in the app, and the spelling of «Recomponlas»),
 * with «cartera» for «wallet»: R-14 of the Abogado Digital's final review of 1.3.0 (FinalLegalReviewTest.noAppTextSaysWallet).
 */
const val SHARE_WARNING_TITLE = "Aviso"
const val SHARE_WARNING =
    "Estas partes no son una copia SLIP-39 de cartera. Metidas en una cartera de hardware como recuperación SLIP-39 " +
        "darían otra cartera distinta. Recomponlas en Clavero o con una herramienta SLIP-0039 que devuelva la entropía."

const val SHARE_MODE_SPLIT = "Repartir"
const val SHARE_MODE_COMBINE = "Recomponer"

const val SHARE_INPUT_PHRASE = "Frase BIP-39"
const val SHARE_INPUT_HEX = "Secreto en hex"
const val SHARE_PHRASE_LABEL = "Frase BIP-39 (12, 15, 18, 21 o 24 palabras en inglés)"
const val SHARE_HEX_LABEL = "Secreto en hex (de 16 a 32 bytes, en número par)"
const val SHARE_PHRASE_NOTE =
    "Se reparte la entropía de la frase, y al recomponer sale la misma frase. Una passphrase BIP-39 (la «palabra 25») no " +
        "entra en el reparto: guárdala aparte."
const val SHARE_PHRASE_COUNT = "Una frase BIP-39 tiene 12, 15, 18, 21 o 24 palabras."
fun sharePhraseUnknown(position: Int) = "La palabra $position no es de la lista BIP-39 en inglés."
const val SHARE_PHRASE_CHECKSUM = "La frase no cuadra con su suma de control: revisa las palabras."
const val SHARE_HEX_INVALID =
    "El secreto en hex tiene que ser de 16 a 32 bytes en número par: de 32 a 64 cifras hex, en múltiplos de 4."
const val SHARE_SECRET_TOO_LONG = "El secreto es demasiado largo."
const val SHARE_PARTS_TOO_LONG = "Hay demasiado texto en las partes."
const val SHARE_WORDS_MISSING = "No se pudo leer la lista de palabras de la app."

const val SHARE_GROUP_THRESHOLD_LABEL = "Grupos necesarios"
fun shareGroupTitle(group: Int) = "Grupo $group"
const val SHARE_MEMBER_THRESHOLD_LABEL = "Partes necesarias"
const val SHARE_MEMBER_COUNT_LABEL = "Partes"
const val SHARE_ADD_GROUP = "Añadir grupo"
const val SHARE_REMOVE_GROUP = "Quitar grupo"
const val SHARE_GROUPS_NOTE =
    "Lo normal es un solo grupo: por ejemplo, 2 partes necesarias de 3. Con varios grupos, hacen falta las partes " +
        "necesarias de tantos grupos como diga «Grupos necesarios»."
const val SHARE_NUMBER_INVALID = "Escribe un número del 1 al 16 en cada casilla."

const val SHARE_PASSPHRASE_LABEL = "Contraseña (opcional)"
const val SHARE_PASSPHRASE_NOTE =
    "Solo caracteres ASCII imprimibles, como pide SLIP-0039: letras sin acentos ni eñes, números, espacios y signos. " +
        "Para recomponer hace falta la misma; con otra sale un secreto distinto."

const val SHARE_SPLIT_BUTTON = "Repartir"
const val SHARE_COMBINE_BUTTON = "Recomponer"
const val SHARE_BUSY = "Calculando…"
const val SHARE_CANCEL = "Cancelar"
const val SHARE_COPY = "Copiar"
const val SHARE_TOO_LONG = "Ese texto es demasiado largo y no se ha pegado."

fun shareGroupHeader(group: Int, threshold: Int, count: Int) =
    if (count == 1) "Grupo $group: 1 parte" else "Grupo $group: hacen falta $threshold de estas $count partes"
fun sharePartLabel(part: Int) = "Parte $part"
fun shareGroupsNeeded(threshold: Int, groups: Int) =
    if (groups == 1) "Un solo grupo." else "Hacen falta las partes de $threshold de los $groups grupos."

const val SHARE_PARTS_LABEL = "Partes, una por línea"
const val SHARE_PARTS_NOTE = "Escribe o pega las partes necesarias, cada una en su línea. Mayúsculas y espacios de más no importan."
const val SHARE_RESULT_HEX = "Secreto en hex"
const val SHARE_RESULT_PHRASE = "Como frase BIP-39"

/**
 * Review I-1: under the phrase, before «Copiar». The code turns any 16 to 32 bytes (in steps of 4) into a phrase and
 * cannot tell where they came from; the parts of a cartera of hardware hold its BIP-32 seed, whose «phrase» is another.
 */
const val SHARE_RESULT_PHRASE_NOTE =
    "Esta es la frase BIP-39 de estos bytes. Si las partes vienen de una cartera, el secreto es su semilla y esta frase " +
        "daría otra cartera distinta: usa la frase solo si repartiste una frase BIP-39."
const val SHARE_RESULT_NOTE =
    "Si la contraseña no es la que se usó al repartir, este es otro secreto distinto: SLIP-0039 no avisa."

// The messages of Slip39Failure. None carries a word or a byte of a part, the secret or the passphrase.

fun slip39TooManyParts(max: Int) = "Hay demasiadas partes: como mucho $max."
const val SLIP39_NO_PARTS = "Escribe al menos una parte."
fun slip39PartTooLong(part: Int) = "La parte $part es demasiado larga."
fun slip39WordCount(part: Int, count: Int, min: Int, max: Int) =
    "La parte $part tiene $count ${if (count == 1) "palabra" else "palabras"}: una parte tiene de $min a $max."
fun slip39UnknownWord(position: Int, part: Int) = "La palabra $position de la parte $part no es de la lista SLIP-39."
fun slip39Checksum(part: Int) = "La parte $part tiene alguna palabra mal: su suma de control no cuadra."
fun slip39BadLength(part: Int) = "La parte $part no tiene una longitud válida."
const val SLIP39_NOT_SAME_SET = "Las partes no son del mismo reparto: no coinciden su identificador, sus grupos o su longitud."
const val SLIP39_MORE_GROUPS_THAN_COUNT = "Las partes piden más grupos de los que tiene el reparto."
fun slip39TooFewGroups(needed: Int, found: Int) = "Hacen falta partes de $needed grupos y hay de $found."
fun slip39TooManyGroups(needed: Int, found: Int) = "Sobran partes: hacen falta partes de $needed grupos y hay de $found."
fun slip39ThresholdMismatch(group: Int) = "Las partes del grupo $group no coinciden en cuántas hacen falta."
fun slip39SameIndex(group: Int) = "Hay dos partes distintas con el mismo número en el grupo $group."
fun slip39WrongCount(group: Int, needed: Int, found: Int) = "En el grupo $group hacen falta $needed partes y hay $found."
const val SLIP39_DIGEST_MISMATCH = "Las partes no recomponen el secreto: alguna está cambiada o es de otro reparto."
fun slip39Exponent(max: Int) = "El cálculo pedido es demasiado largo: Clavero acepta un exponente de iteraciones de 0 a $max."
const val SLIP39_PASSPHRASE_NOT_ASCII =
    "La contraseña solo puede llevar caracteres ASCII imprimibles: letras sin acentos ni eñes, números, espacios y signos."
fun slip39PassphraseTooLong(max: Int) = "La contraseña es demasiado larga: como mucho $max caracteres."
const val SLIP39_SECRET_LENGTH = "El secreto tiene que tener de 16 a 32 bytes, en número par."
const val SLIP39_GROUP_LIMITS = "Hacen falta de 1 a 16 grupos, y los grupos necesarios tienen que ser de 1 al número de grupos."
const val SLIP39_MEMBER_LIMITS = "En cada grupo, las partes tienen que ser de 1 a 16, y las necesarias, de 1 al número de partes."
const val SLIP39_ONE_OF_MANY = "Un grupo que pide 1 parte solo puede tener 1 parte: si quieres más copias, copia esa misma parte."
