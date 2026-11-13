package app.seguridad.vault

/*
 * 2.0, task 4: the texts of the folders, the saved views, the tag colours, the order and the quick filters of the list
 * (minimal text, M-3: what each thing is in a line; the rest under «Más información»). They go to the Abogado Digital's
 * final review.
 */

const val SEARCH_LABEL = "Buscar"

/** Under the search box, folded: what the search reads and what it never reads. */
const val SEARCH_MORE =
    "Busca en el nombre, el usuario, la web (solo su dominio), el servicio y la cuenta del 2FA, las etiquetas, las " +
        "carpetas y los nombres de los campos que no están ocultos. Nunca en las contraseñas, los campos ocultos, las " +
        "notas ni las palabras de una frase semilla. Perdona una errata en palabras de 4 a 7 letras y dos desde 8, y una " +
        "en el principio de una palabra mientras escribes; primero salen las que coinciden tal cual."

const val ALL_ENTRIES = "Todo"
const val RECENT_TITLE = "Recientes"
const val FAVORITES_TITLE = "Favoritos"
const val ALL_TITLE = "Todas"
const val FILTER_FAVORITES = "★ Favoritas"
const val FILTER_WEAK = "Débiles"
const val FILTER_NO_2FA = "Sin 2FA"
const val FILTER_EXPIRING = "Caducan pronto"
const val NO_RESULTS = "Sin resultados"
const val EMPTY_VAULT = "Tu bóveda está vacía."
const val CLEAR_FILTERS = "Quitar filtros"

const val SORT_LABEL = "Orden"

fun sortName(s: SortOrder): String = when (s) {
    SortOrder.NAME -> "Por nombre"
    SortOrder.UPDATED -> "Por modificación"
    SortOrder.CREATED -> "Por creación"
    SortOrder.USED -> "Por uso"
}

const val FOLDERS_TITLE = "Carpetas"
const val FOLDERS_SHORT = "Toca una carpeta para ver lo que tiene."
const val FOLDERS_EMPTY = "Aún no hay carpetas."
const val FOLDER_NEW = "Nueva carpeta"
const val FOLDER_NEW_INSIDE = "Nueva subcarpeta"
const val FOLDER_RENAME = "Renombrar"
const val FOLDER_MOVE = "Mover"
const val FOLDER_DELETE = "Borrar carpeta"
const val FOLDER_NAME_LABEL = "Nombre de la carpeta"
const val FOLDER_ALL = "Todas las entradas"
const val FOLDER_NONE = "Sin carpeta"
const val FOLDER_TOP = "Arriba del todo"
const val FOLDER_WITH_SUBFOLDERS = "Con subcarpetas"
const val FOLDER_OPTIONS = "Opciones de la carpeta"
const val FOLDER_FILTER_OFF = "Quitar carpeta"

fun folderCrumb(path: List<String>): String = "📁 " + path.joinToString(" › ")

fun folderDeleteTitle(name: String): String = "¿Borrar la carpeta «$name»?"

/** The choices of deleting a folder ([FolderDeletion]): the first for one at the top, the second for one inside another. */
const val FOLDER_DELETE_TO_NONE = "Sus entradas y sus subcarpetas se quedan sin carpeta"
fun folderDeleteToParent(parent: String): String = "Sus entradas y sus subcarpetas pasan a «$parent»"
const val FOLDER_DELETE_TO_TRASH = "Sus entradas, y las de sus subcarpetas, van a la papelera; las subcarpetas se borran"
const val FOLDER_DELETE_TRASH_NOTE = "Desde la papelera se pueden restaurar durante 30 días, sin carpeta."

const val MOVE_TO = "Mover a…"
fun moveToTitle(name: String): String = "Mover «$name» a…"
fun folderMoveTitle(name: String): String = "Mover la carpeta «$name» a…"
fun movedTo(folder: String?): String = if (folder == null) "Movida a «$FOLDER_NONE»" else "Movida a «$folder»"
const val DRAG_HANDLE = "Arrastrar a una carpeta"
const val DROP_HERE = "Suelta en una carpeta"
const val DROP_MORE = "Para otras carpetas, usa «Mover a…» en la ficha."

const val VIEWS_TITLE = "Vistas"
const val VIEW_SAVE = "Guardar vista"
const val VIEW_SAVE_SHORT = "Guarda la búsqueda, los filtros, la carpeta y el orden de ahora con un nombre."
const val VIEW_NAME_LABEL = "Nombre de la vista"
const val VIEWS_MANAGE = "Gestionar vistas"
const val VIEW_DELETE = "Borrar vista"
const val VIEW_DELETE_SHORT = "Borrar una vista no borra ninguna entrada."

const val TAG_COLORS_TITLE = "Colores de etiquetas"
const val TAG_COLORS_OPEN = "🎨 Colores"
const val TAG_COLORS_SHORT = "Elige un color para cada etiqueta. El nombre se ve siempre."
const val TAG_COLOR_NONE = "Sin color"
const val TAGS_EMPTY = "Aún no hay etiquetas. Ponlas al editar una entrada."

const val SAVE = "Guardar"
const val CANCEL = "Cancelar"
const val CLOSE = "Cerrar"
const val CONFIRM_DELETE = "Borrar"
