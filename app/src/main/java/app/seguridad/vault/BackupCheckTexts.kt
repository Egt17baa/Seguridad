package app.seguridad.vault

/*
 * The texts of «Comprobar una copia» (Ajustes, 1.4.0). The errors that stop a check are the import's own (Backup.kt,
 * BackupCheck.kt, Vault.kt and CryptoFailure): the check says what the import would say.
 */

/** The row in Ajustes and the screen's title. */
const val BACKUP_CHECK_TITLE = "Comprobar una copia"

const val BACKUP_CHECK_INTRO =
    "Comprueba una copia .sgbak sin importarla: que la contraseña maestra que escribas la abre, que sus entradas se leen " +
        "y que cada adjunto está en la copia y coincide con la huella SHA-256 que guarda su entrada. No cambia tu bóveda ni " +
        "tus copias. Mientras la comprueba, guarda lo que lee en una carpeta temporal de la app y lo borra al terminar."

const val BACKUP_CHECK_PICK = "Elegir copia"
const val BACKUP_CHECK_PASSWORD = "Contraseña maestra de la copia"
const val BACKUP_CHECK_RUN = "Comprobar"
const val BACKUP_CHECK_CANCEL = "Cancelar"
const val BACKUP_CHECK_BUSY = "Comprobando la copia…"

/** A paste longer than the field takes is refused, and said. */
const val BACKUP_CHECK_TOO_LONG = "Ese texto es demasiado largo y no se ha pegado."

/** The file chosen does not start as a `seguridad-backup` copy ([isBackup]); said before the password is asked. */
const val BACKUP_CHECK_NOT_SGBAK = "Este archivo no es una copia .sgbak de Clavero. Esta pantalla solo comprueba esas copias."

/** The file could not be opened or read (an IOException). */
const val BACKUP_CHECK_READ_FAILED = "No se pudo leer el archivo"

/** Anything else that stopped the check. */
const val BACKUP_CHECK_FAILED = "No se pudo comprobar la copia"

const val BACKUP_CHECK_RESULT_TITLE = "Resultado"

/** The report has no problems: it passed every check the import makes before replacing the vault. */
const val BACKUP_CHECK_OK = "No se ha encontrado ningún problema: la contraseña abre la copia, sus entradas se leen y todos sus adjuntos coinciden."

/** The report has problems: the import refuses such a copy. */
const val BACKUP_CHECK_HAS_PROBLEMS = "La copia tiene problemas y no se puede importar así:"

fun backupCheckVersion(version: Int) = "Formato de la copia: versión $version"

fun backupCheckEntries(total: Int) = if (total == 1) "1 entrada" else "$total entradas"

fun backupCheckType(type: EntryType, count: Int) = "${type.label}: $count"

fun backupCheckAttachments(total: Int, ok: Int) = when (total) {
    0 -> "Sin adjuntos"
    1 -> "1 adjunto; coincide: $ok"
    else -> "$total adjuntos; coinciden: $ok"
}

/** [count] attachments that entries list are not in the copy. Counts only: never their names. */
fun backupCheckMissing(count: Int) = if (count == 1) "Falta 1 adjunto en la copia." else "Faltan $count adjuntos en la copia."

/** [count] attachments are in the copy but not as their entry records them. Counts only: never their names. */
fun backupCheckChanged(count: Int) =
    if (count == 1) {
        "1 adjunto no coincide con lo que guarda su entrada: está dañado o se ha cambiado."
    } else {
        "$count adjuntos no coinciden con lo que guarda su entrada: están dañados o se han cambiado."
    }

const val BACKUP_CHECK_NOT_PROVEN_TITLE = "Lo que no comprueba"

/** What a check without problems still does not prove. */
const val BACKUP_CHECK_NOT_PROVEN =
    "No dice si es tu copia más reciente ni si tiene lo que cambiaste después de hacerla. No comprueba que quede espacio " +
        "en este móvil para importarla. Tampoco prueba lo que solo hace la importación: si el coste de Argon2id elegido en " +
        "este móvil es mayor que el de la copia, al importarla se vuelve a cifrar su clave con ese coste, y eso puede " +
        "necesitar más memoria. Importar una copia desactiva la huella: después tendrás que volver a activarla."

/** Next to [app.seguridad.tools.TOOLS_AWAY_WIPE_NOTE]: what stops a check going on. */
const val BACKUP_CHECK_STOPS_NOTE =
    "La contraseña se borra del campo al pulsar «Comprobar». Si sales de la app, vuelves atrás o se bloquea la bóveda " +
        "mientras se comprueba, la comprobación se detiene en cuanto puede y no se muestra su resultado."

/** 2.0, task 8: [backupCheckNotice] when no copy of this vault was ever checked. */
const val BACKUP_CHECK_NEVER = "Aún no has comprobado ninguna copia."

/** 2.0, task 8: [backupCheckNotice] once [days] (30 or more) have passed since the last check. */
fun backupCheckDaysAgo(days: Long) = "Comprobaste una copia por última vez hace $days días."

/**
 * 2.0, task 8 (review M-6): [backupCheckNotice] when the date of the last check is more than a day ahead of the phone's
 * clock (checked with the clock set forward): it cannot be counted from, so it does not silence the reminder for years.
 */
const val BACKUP_CHECK_DATE_AHEAD = "La fecha de tu última comprobación de una copia es posterior a hoy. Comprueba una copia otra vez."

/**
 * 2.0, task 8 (O-5 of the Abogado Digital): after a check without problems of a copy that does not count for the reminder
 * of [backupCheckNotice].
 */
const val BACKUP_CHECK_NOT_COUNTED =
    "Esta comprobación no cuenta para el aviso «Comprobaste una copia por última vez»: la copia es de otra bóveda o de " +
        "antes de la 2.0."

/** 2.0, task 8 (O-5): under «Más información», next to [backupCheckNotice] in Ajustes. */
const val BACKUP_CHECK_NOTICE_MORE =
    "Los días se cuentan desde que comprobaste con «Comprobar una copia» una copia de esta bóveda, no desde que se hizo la " +
        "copia. Las copias de antes de la 2.0 y las de otra bóveda no cuentan."
