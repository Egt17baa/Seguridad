package app.seguridad.vault

// 2.0, task 8: the texts of the copies to several folders (Ajustes, «Copia automática»). For the final review of the
// Abogado Digital (task report, «Textos para el Abogado Digital»).

/** 2.0, task 8 (O-4 of the Abogado Digital): on view in «Copia automática». */
const val AUTO_BACKUP_SCHEDULE_ON_OPEN = "Las copias con calendario se hacen al abrir la app, si hubo cambios."

/** 2.0, task 8: under «Más información» of «Copia automática». */
const val AUTO_BACKUP_MORE =
    "Guarda una copia cifrada (.sgbak, con los adjuntos) en hasta 3 carpetas que elijas, cada una con su calendario: unos " +
        "segundos después de cada cambio en la bóveda, o cada día, cada semana o cada mes si la bóveda cambió desde la última " +
        "copia en esa carpeta. Clavero no funciona en segundo " +
        "plano: las copias con calendario se hacen cuando abres la app o la bóveda, y si no la abres no hay copia. Elige " +
        "carpetas que se sincronicen o que copies fuera del móvil."

/** 2.0, task 8: the button that adds a second or third folder. */
const val AUTO_BACKUP_ADD_FOLDER = "Añadir otra carpeta"

/** 2.0, task 8: a folder that is already a target of this vault. */
const val AUTO_BACKUP_ALREADY_TARGET = "Esa carpeta ya recibe las copias de esta bóveda. Elige otra."

/** 2.0, task 8: a fourth folder. */
const val AUTO_BACKUP_TOO_MANY = "Ya hay 3 carpetas. Quita una para añadir otra."

/** 2.0, task 8: a folder removed (or the copies turned off, with the last one). */
const val AUTO_BACKUP_REMOVED = "Ya no se harán copias en esa carpeta. Las copias ya hechas siguen en ella."

/** 2.0, task 8: the title of the card of the [n]-th folder. */
fun autoBackupTargetTitle(n: Int) = "Carpeta $n de la copia automática"

/** 2.0, task 8: how each calendar is called in Ajustes. */
fun scheduleLabel(s: BackupSchedule) = when (s) {
    BackupSchedule.ON_CHANGE -> "Tras cada cambio"
    BackupSchedule.DAILY -> "Cada día"
    BackupSchedule.WEEKLY -> "Cada semana"
    BackupSchedule.MONTHLY -> "Cada mes"
}

/** 2.0, task 8: the end of «Copia automática activada: se hará una ahora y después …». */
fun scheduleAfter(s: BackupSchedule) = when (s) {
    BackupSchedule.ON_CHANGE -> "tras cada cambio en la bóveda"
    BackupSchedule.DAILY -> "cada día, si hubo cambios, al abrir la app"
    BackupSchedule.WEEKLY -> "cada semana, si hubo cambios, al abrir la app"
    BackupSchedule.MONTHLY -> "cada mes, si hubo cambios, al abrir la app"
}

/**
 * Task 8, round 4 (i-3): «Hacer una copia ahora» with the vault locked. The copy is not asked (it would live in memory only),
 * so nothing is promised.
 */
const val AUTO_BACKUP_NOW_NEEDS_OPEN = "La copia se hace con la bóveda abierta: ábrela (pestaña Bóveda) y vuelve a pulsar «Hacer una copia ahora»."
