package app.seguridad.vault

/*
 * 2.0, task 3: the texts of the trash and of the history of each entry (the minimal text rule, M-3). Abogado Digital,
 * O-2: «Borrar para siempre», «Vaciar papelera» and «Borrar historial» say on view that the copies already made keep what
 * they hold, and the trash says that it empties itself on opening the vault after 30 days. Never «irrecuperable» nor any
 * promise of a deletion nothing can undo: the copies, and what the phone's storage keeps, are outside the app's reach.
 */

const val TRASH_TITLE = "Papelera"

/** On view, at the top of the trash (O-2). */
const val TRASH_SHORT = "Lo que borras se queda aquí 30 días. Al abrir la bóveda, lo que lleve más de 30 días se quita solo."

/** On view in the trash and in each confirmation of «Borrar para siempre», «Vaciar papelera» and «Borrar historial» (O-2). */
const val COPIES_KEEP_IT = "Las copias ya hechas (automáticas o exportadas) lo conservan."

/** Folded under «Más información» in the trash. */
const val TRASH_MORE =
    "Lo de la papelera no sale en la lista, en la búsqueda, en «Salud» ni al buscar duplicados al importar, pero va en las copias. " +
        "Si la hora del móvil salta más de 2 días hacia delante, ese día no se quita nada: se hace en la siguiente apertura."

const val TRASH_EMPTY_LIST = "La papelera está vacía."
const val TRASH_RESTORE = "Restaurar"
const val TRASH_DELETE_FOREVER = "Borrar para siempre"
const val TRASH_EMPTY = "Vaciar papelera"
const val TRASH_OPEN = "Papelera"

/** «Restaurar» found the vault already has an identity (only a copy made elsewhere can bring one to the trash). */
const val TRASH_RESTORE_NONE = "No se ha restaurado: la bóveda ya tiene otra identidad."

/** A save of the trash or of the history that failed (a full disk, say). */
const val TRASH_SAVE_FAILED = "No se pudo guardar. ¿Queda espacio en el móvil?"

fun trashDeleteForeverTitle(name: String): String = "¿Borrar «$name» para siempre?"

/** On view in the confirmation of «Borrar para siempre» (O-2). */
const val TRASH_DELETE_FOREVER_TEXT = "Se quita de la bóveda, con sus adjuntos. $COPIES_KEEP_IT"

const val TRASH_EMPTY_TITLE = "¿Vaciar la papelera?"

/** On view in the confirmation of «Vaciar papelera» (O-2). */
fun trashEmptyText(count: Int): String =
    "Se quita${if (count == 1) " 1 entrada" else "n $count entradas"} de la bóveda, con sus adjuntos. $COPIES_KEEP_IT"

/** When an entry in the trash goes by itself, at an unlock. */
fun trashLeavesIn(deletedAt: Long, now: Long): String {
    val left = ((deletedAt + TRASH_MS - now) / (24 * 60 * 60_000L)).coerceAtLeast(0)
    return when (left) {
        0L -> "Se quita al abrir la bóveda"
        1L -> "Se quita en 1 día"
        else -> "Se quita en $left días"
    }
}

const val MOVE_TO_TRASH = "Mover a la papelera"
fun moveToTrashTitle(name: String): String = "¿Mover «$name» a la papelera?"
fun moveToTrashCountTitle(count: Int): String = "¿Mover $count entradas a la papelera?"

/** On view in the confirmation of a move to the trash. */
const val MOVE_TO_TRASH_TEXT = "Puedes restaurarla desde la papelera durante 30 días."

fun movedToTrash(count: Int): String = if (count == 1) "Movida a la papelera" else "$count entradas movidas a la papelera"
const val UNDO = "Deshacer"

const val HISTORY_TITLE = "Versiones"

/** On view, at the top of the versions of an entry. */
const val HISTORY_SHORT = "Cada guardado que cambia algo deja aquí la versión anterior."

/** Folded under «Más información». */
const val HISTORY_MORE =
    "Se guardan hasta 20 versiones y 64 KB por entrada; al pasar de ahí se quitan las más antiguas. Los adjuntos no tienen " +
        "versiones. Las contraseñas y los campos ocultos están ocultos también aquí. Restaurar una versión no borra nada: lo que había " +
        "queda como otra versión."

const val HISTORY_EMPTY = "No hay versiones anteriores."
const val HISTORY_RESTORE = "Restaurar esta versión"
const val HISTORY_RESTORED = "Versión restaurada. Lo que había queda como otra versión."
const val HISTORY_CLEAR = "Borrar historial"
fun historyClearTitle(name: String): String = "¿Borrar el historial de «$name»?"

/** On view in the confirmation of «Borrar historial» (O-2). */
const val HISTORY_CLEAR_TEXT = "Se quitan sus versiones y sus contraseñas anteriores. $COPIES_KEEP_IT"
const val HISTORY_CLEARED = "Historial borrado"
fun historyOpen(count: Int): String = "Versiones ($count)"
fun versionUntil(date: String): String = "Hasta el $date"
