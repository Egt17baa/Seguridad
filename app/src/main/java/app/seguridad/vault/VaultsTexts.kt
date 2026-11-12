package app.seguridad.vault

// 2.0, task 2: the texts of «Bóvedas» (VaultPickerScreen). Minimal text (M-3): what has to be seen is on view, the rest
// under «Más información». Abogado Digital: O-1 (no text presents several vaults as a way to hide one), O-2 and R-9
// (deleting a vault and «Importar como bóveda nueva» say what they do and what they do not delete).

const val VAULTS_TITLE = "Bóvedas"

/** On view, at the top of the list. */
const val VAULTS_SHORT = "Cada bóveda tiene su contraseña maestra, su archivo, su huella y su copia automática. Solo hay una abierta a la vez."

/** Under «Más información». */
const val VAULTS_MORE =
    "Antes de abrirla, la lista dice «Bóveda 1», «Bóveda 2»… y su color; su nombre va cifrado dentro de ella y se ve al " +
        "abrirla. Cambiar de bóveda bloquea la que está abierta. Este móvil guarda sin cifrar cuántas bóvedas hay, su color y " +
        "su orden."

const val VAULT_OPEN_NOW = "Abierta"
const val VAULT_ACTIVE = "Elegida"
const val VAULT_CHOOSE = "Elegir"
const val VAULT_RENAME = "Cambiar el nombre"
const val VAULT_NAME_LABEL = "Nombre de la bóveda"
const val VAULT_NEW = "Nueva bóveda"
const val VAULT_NEW_SHORT = "La bóveda abierta se bloquea. La nueva empieza vacía y sin copia automática: elige su carpeta en Ajustes."
const val VAULT_CREATE = "Crear"

/** COV2-1: a vault is added only from an open one. */
const val VAULT_NEW_NEEDS_OPEN = "Para crear o importar otra bóveda, abre antes una de las que tienes."
const val VAULT_COLOR = "Color"
const val VAULT_UP = "Subir"
const val VAULT_DOWN = "Bajar"
const val VAULT_CHANGE = "Cambiar de bóveda"

const val VAULT_IMPORT_NEW = "Importar como bóveda nueva"

/** R-9: on view, before the password of the copy is asked. */
const val VAULT_IMPORT_NEW_SHORT = "Crea otra bóveda con lo que tiene la copia. No cambia ni borra las bóvedas que ya tienes."
const val VAULT_IMPORT_NEW_DONE = "Copia importada como bóveda nueva"

const val VAULT_DELETE = "Borrar esta bóveda"

/** The delete button is for the vault that is open: it has to be opened first (ruling 3). */
const val VAULT_DELETE_OPEN_FIRST = "Para borrar una bóveda, ábrela con su contraseña maestra."
const val VAULT_DELETE_LAST = "Es la única bóveda: para borrarla, usa «He olvidado la contraseña maestra» en la pantalla de bloqueo."

/** O-2 and R-9: on view in the confirmation, what goes and what stays. */
fun vaultDeleteWarning(name: String): String =
    "Se borra «$name» de este móvil: su archivo, sus adjuntos, su huella y sus ajustes. Las copias ya hechas (automáticas o " +
        "exportadas) no se borran y se siguen abriendo con su contraseña maestra."

fun vaultDeleteTypeName(name: String): String = "Para confirmar, escribe su nombre: «$name»"

/** Review M-1, on view: a slot with no vault file at all goes without its master (there is nothing to open). */
const val VAULT_DELETE_EMPTY_WARNING =
    "Se quita esta bóveda vacía de este móvil: no tiene ningún archivo de bóveda. Las copias ya hechas, si las hay, no se borran."

/** Review M-2: a file that could not be read is kept for recovery, so that slot is not deleted from here. */
const val VAULT_DELETE_DAMAGED = "Esta bóveda tiene un archivo que no se pudo leer y que se guarda para recuperarlo: no se puede borrar desde aquí."

/** The vault locked while it was being deleted (or its master could not be checked): nothing was deleted. */
const val VAULT_DELETE_LOCKED = "La bóveda se bloqueó: no se ha borrado nada. Ábrela y vuelve a intentarlo."

/** How [app.seguridad.AppContainer.deleteVault] ended; nothing is deleted unless it is [DELETED]. */
enum class VaultDeletion { DELETED, WRONG_NAME, LOCKED, KEPT_DAMAGED, LAST_VAULT }

/** What «Bóvedas» says after a deletion: one text for each way it can end (re-review 1, minor 2). */
fun deletionText(outcome: VaultDeletion): String = when (outcome) {
    VaultDeletion.DELETED -> VAULT_DELETED
    VaultDeletion.WRONG_NAME -> VAULT_DELETE_NAME_WRONG
    VaultDeletion.LOCKED -> VAULT_DELETE_LOCKED
    VaultDeletion.KEPT_DAMAGED -> VAULT_DELETE_DAMAGED
    VaultDeletion.LAST_VAULT -> VAULT_DELETE_LAST
}

const val VAULT_DELETE_MASTER = "Contraseña maestra de esta bóveda"
const val VAULT_DELETED = "Bóveda borrada de este móvil. Las copias ya hechas siguen donde estaban."
const val VAULT_DELETE_NAME_WRONG = "El nombre no coincide: no se ha borrado nada."
const val VAULT_FAILED = "No se pudo hacer. Inténtalo de nuevo."

/** The colour's name, so the colour is never the only sign (R-1, WCAG 2.2: 1.4.1). */
fun TagColor.label(): String = when (this) {
    TagColor.RED -> "rojo"
    TagColor.ORANGE -> "naranja"
    TagColor.YELLOW -> "amarillo"
    TagColor.GREEN -> "verde"
    TagColor.TEAL -> "turquesa"
    TagColor.BLUE -> "azul"
    TagColor.PURPLE -> "morado"
    TagColor.GRAY -> "gris"
}
