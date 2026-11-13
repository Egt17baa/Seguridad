package app.seguridad.vault

/*
 * 2.0, task 6: the texts of the list and the card (design «La lista y la ficha»): the row's menu and its swipes, the 2FA
 * of a row, the copy in two steps, the selection bar, the empty list and «Ver en grande». Minimal text (M-3): a word or
 * a line each. They go to the Abogado Digital's final review.
 */

// The row: its «⋮» menu, its swipes (also TalkBack's actions of the row) and its 2FA.
const val ROW_MENU = "Más opciones"
fun rowMenuLabel(title: String): String = "Más opciones de «$title»"
const val COPY_USERNAME = "Copiar usuario"
const val COPY_PASSWORD = "Copiar contraseña"
const val COPY_TOTP = "Copiar 2FA"
const val EDIT = "Editar"
const val USERNAME_COPIED = "Usuario copiado"
const val PASSWORD_COPIED = "Contraseña copiada"
const val CODE_COPIED = "Código copiado"
const val NEXT_CODE_COPIED = "Copiado el código siguiente"
const val CODE_TAP_TO_COPY = "Toca para copiar"
const val ROW_SELECT = "Seleccionar"
fun codeSecondsLeft(seconds: Int): String = "Quedan $seconds s"

/** The copy in two steps: back in the app after «Copiar usuario», for 30 s. */
fun copyPasswordOffer(title: String): String = "¿Copiar también la contraseña de «$title»?"

// The selection bar.
fun selectedCount(count: Int): String = if (count == 1) "1 seleccionada" else "$count seleccionadas"
const val SELECT_ALL = "Seleccionar todo"
const val SELECTION_END = "Terminar la selección"
const val SELECTION_MOVE = "Mover a carpeta"
const val SELECTION_TAG = "Etiquetar"
const val SELECTION_FAVORITE = "Favorita"
const val SELECTION_UNFAVORITE = "Quitar favorita"
const val SELECTION_TRASH = "Papelera"
const val SELECTION_TAG_LABEL = "Etiqueta"
fun tagSelectedTitle(count: Int): String = if (count == 1) "Etiquetar 1 entrada" else "Etiquetar $count entradas"
fun movedSelected(count: Int, folder: String?): String = when (count) {
    0 -> "Ya estaban ahí"
    1 -> "1 entrada movida"
    else -> "$count entradas movidas"
} + if (count == 0) "" else (folder?.let { " a «$it»" } ?: " fuera de las carpetas")
fun taggedSelected(count: Int, tag: String): String =
    if (count == 0) "Ya la tenían (o no caben más de 10 etiquetas)." else "«$tag» puesta en ${if (count == 1) "1 entrada" else "$count entradas"}"
fun favoriteSelected(count: Int, favorite: Boolean): String = when {
    count == 0 -> "Sin cambios"
    favorite -> if (count == 1) "1 entrada marcada como favorita" else "$count entradas marcadas como favoritas"
    else -> if (count == 1) "1 entrada ya no es favorita" else "$count entradas ya no son favoritas"
}

// The empty list.
const val EMPTY_CREATE_FIRST = "Crear la primera"
const val EMPTY_IMPORT = "Importar"

// «Ver en grande».
const val BIG_OPEN = "Ver en grande"
const val BIG_TITLE = "En grande"
const val BIG_LETTERS = "Letras"
const val BIG_DIGITS = "Cifras"
const val BIG_SYMBOLS = "Símbolos"
const val BIG_SPACE = "espacio"
