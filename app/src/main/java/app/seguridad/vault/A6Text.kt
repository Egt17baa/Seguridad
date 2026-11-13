package app.seguridad.vault

/*
 * Task 8, round 4 (ruling «T8 ronda 4»): what is told about an A6 request (the replacement of the old copies after a change
 * of master password) comes from one place: [a6Summary] reads what each target saved for the request of now, and
 * [a6Text] writes the text from that and the failures to tell, nothing else. The dialog after an unlock (AppNavHost) and
 * the dialog of the change of master password (SettingsScreen) both use them.
 */

/**
 * The state of the A6 request [request] over the [folders] targets of now: [replaced] of them saved a replacement for this
 * very request ([BackupTargetSettings.replaceDone] of [request]; one of an earlier request never counts), which deleted
 * [deleted] copies in all and left [unlisted] files with a copy's name unlisted in their folders. Every other target
 * (waiting, failed, added after the request, or with a request of before) did not replace for it.
 */
data class A6Summary(val request: String, val folders: Int, val replaced: Int, val deleted: Int, val unlisted: Int) {
    val notReplaced: Int get() = folders - replaced

    /** What was told: a summary equal to the last one told is not told again. */
    val key: String get() = "$request|$folders|$replaced|$deleted|$unlisted"
}

/** The id of the A6 request of now: [a6]'s, or [LEGACY_A6_REQUEST] for one saved by 1.4.3 or before. */
fun currentA6Request(a6: A6State?): String = a6?.id ?: LEGACY_A6_REQUEST

/** The request a target is in: its own id, or [LEGACY_A6_REQUEST] for one of 1.4.3. */
val BackupTargetSettings.requestId: String get() = replaceRequest ?: LEGACY_A6_REQUEST

/** [A6Summary] of the request of [a6] over [targets], as saved; pure. */
fun a6Summary(targets: List<BackupTargetSettings>, a6: A6State?): A6Summary {
    val request = currentA6Request(a6)
    val done = targets.mapNotNull { t -> t.replaceDone?.takeIf { it.request == request } }
    return A6Summary(request, targets.size, done.size, done.sumOf { it.deleted }, done.sumOf { it.unlisted })
}

/**
 * The text of an A6 outcome: the [failures] not told yet (fixed texts, one per line), then what [summary] says, only when
 * at least one folder replaced:
 * - every folder replaced for this request: [AUTO_BACKUP_REPLACED] (one folder) or [AUTO_BACKUP_REPLACED_SEVERAL]
 *   («…en cada carpeta»), and the files left unlisted;
 * - only some: in how many folders the copies of before were deleted and in how many not (with the warning that some may
 *   open with an earlier password), and the files left unlisted in those that did.
 * «En cada carpeta» is never said unless every folder of now has a replacement of this request.
 */
fun a6Text(failures: List<String>, summary: A6Summary?): String {
    val parts = ArrayList<String>()
    if (failures.isNotEmpty()) parts += failures.joinToString("\n")
    if (summary != null && summary.replaced > 0) {
        val said = if (summary.notReplaced == 0) {
            val several = summary.folders > 1
            val base = if (several) AUTO_BACKUP_REPLACED_SEVERAL else AUTO_BACKUP_REPLACED
            if (summary.unlisted > 0) base + " " + autoBackupUnlistedText(summary.unlisted, several) else base
        } else {
            val base = a6PartialText(summary.replaced, summary.folders)
            if (summary.unlisted > 0) base + " " + a6UnlistedInDoneText(summary.unlisted, summary.replaced) else base
        }
        parts += said
    }
    return parts.joinToString("\n")
}

/** Task 8, round 4: [replaced] of [folders] folders (some, not all) replaced their old copies for the request. */
fun a6PartialText(replaced: Int, folders: Int): String {
    val done = "Se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña (las " +
        "que la app tenía apuntadas) en $replaced de las $folders carpetas."
    val left = folders - replaced
    val notDone = if (left == 1) {
        "En la otra carpeta no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano."
    } else {
        "En las otras $left carpetas no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano."
    }
    return "$done $notDone"
}

/** Task 8, round 4: [n] files left unlisted in the [inFolders] folders that replaced, when not all did. */
fun a6UnlistedInDoneText(n: Int, inFolders: Int): String {
    val where = if (inFolders == 1) "En la carpeta en que se borraron" else "En las carpetas en que se borraron"
    return if (n == 1) {
        "$where quedaba 1 copia con nombre de copia de la app (automática o de antes de importar) que la app no tenía " +
            "apuntada (por ejemplo, de antes de la versión 1.2.1 o de otra bóveda): no se ha borrado y puede que se abra con " +
            "una contraseña anterior. Si no la necesitas, bórrala a mano."
    } else {
        "$where quedaban $n copias con nombre de copia de la app (automáticas o de antes de importar) que la app no tenía " +
            "apuntadas (por ejemplo, de antes de la versión 1.2.1 o de otra bóveda): no se han borrado y puede que se abran " +
            "con una contraseña anterior. Si no las necesitas, bórralas a mano."
    }
}
