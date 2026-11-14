package app.seguridad.vault

/*
 * Task 8, round 4 (ruling «T8 ronda 4»): what is told about an A6 request (the replacement of the old copies after a change
 * of master password) comes from one place: [a6Summary] reads what each target saved for the request of now, and
 * [a6Text] writes the text from that and the failures to tell, nothing else. Round 6: whether anything is told at all is
 * decided in one place too ([a6Decide]), and the dialog of the change of master password shows only its own request
 * ([a6DialogView]).
 */

/**
 * The state of the A6 request [request] over the [folders] targets of now: [replaced] of them saved a replacement for this
 * very request ([BackupTargetSettings.replaceDone] of [request]; one of an earlier request never counts), which deleted
 * [deleted] copies in all and left [unlisted] files with a copy's name unlisted in their folders. Every other target
 * (waiting, failed, added after the request, or with a request of before) did not replace for it. [stale] (round 6, I-2):
 * the request was asked before a later change of the master password ([A6State.masterMark] is not the vault's now), or
 * the vault's mark of now cannot be compared.
 */
data class A6Summary(
    val request: String,
    val folders: Int,
    val replaced: Int,
    val deleted: Int,
    val unlisted: Int,
    val stale: Boolean = false,
) {
    val notReplaced: Int get() = folders - replaced
}

/** The id of the A6 request of now: [a6]'s, or [LEGACY_A6_REQUEST] for one saved by 1.4.3 or before. */
fun currentA6Request(a6: A6State?): String = a6?.id ?: LEGACY_A6_REQUEST

/** The request a target is in: its own id, or [LEGACY_A6_REQUEST] for one of 1.4.3. */
val BackupTargetSettings.requestId: String get() = replaceRequest ?: LEGACY_A6_REQUEST

/**
 * [A6Summary] of the request of [a6] over [targets], as saved; pure. [masterMark] is the vault's mark of now
 * ([VaultStore.masterMark]); a request saved with another one is [A6Summary.stale]. One saved with none (by 1.4.3, whose
 * request was always of its last change) is not.
 */
fun a6Summary(targets: List<BackupTargetSettings>, a6: A6State?, masterMark: String? = null): A6Summary {
    val request = currentA6Request(a6)
    val done = targets.mapNotNull { t -> t.replaceDone?.takeIf { it.request == request } }
    val stale = a6?.masterMark != null && a6.masterMark != masterMark
    return A6Summary(request, targets.size, done.size, done.sumOf { it.deleted }, done.sumOf { it.unlisted }, stale)
}

/**
 * Round 6 («cuándo hablar»): what the targets in the request of now (their [BackupTargetSettings.requestId] is it) hold for
 * it, one line per folder in a fixed order: waiting, replaced (with what it deleted and left unlisted), or in it with
 * neither (a failure of «Borrar…» asked with the vault open). Targets not in the request (added after it) are not in it,
 * so adding a folder never opens a request again; one that comes back from [VaultSettings.pastTargets] with the request
 * is, so it does.
 */
fun a6Snapshot(targets: List<BackupTargetSettings>, a6: A6State?): String {
    val request = currentA6Request(a6)
    val members = targets.filter { it.requestId == request }.sortedBy { it.uri }.map { t ->
        val done = t.replaceDone?.takeIf { it.request == request }
        val state = when {
            t.replacePending -> "pendiente"
            done != null -> "hecha:${done.deleted}:${done.unlisted}"
            else -> "sin"
        }
        "${t.uri}=$state"
    }
    return "$request\n" + members.joinToString("\n")
}

/** What [a6Decide] says to tell: [failures] (each once), [summary] (null: nothing of it), [snapshot] (kept once closed). */
data class A6Decision(val failures: List<String>, val summary: A6Summary?, val snapshot: String)

/**
 * Round 6 (ruling «T8 tras la ronda 5»): the one place that decides whether an A6 outcome is told or nothing is said, from
 * what the targets saved ([targets], [a6]) and the last summary the user closed ([A6State.said], a [a6Snapshot]); pure.
 * Something is told when there are [failures] not told yet, or when at least one folder replaced for the request and the
 * targets in it hold something the closed summary did not (one waiting that was not, a result that was not in it, one gone
 * or come back). So nothing is ever kept silent while a target in the request waits or has a result that the user has not
 * closed; and once the user closed a summary, nothing more is told until a target in the request changes (a folder added
 * after the request never does). [sameVault] false (an import since the pass began) tells no summary. Null: say nothing.
 */
fun a6Decide(targets: List<BackupTargetSettings>, a6: A6State?, failures: List<String>, sameVault: Boolean, masterMark: String?): A6Decision? {
    val summary = a6Summary(targets, a6, masterMark).takeIf { sameVault && it.replaced > 0 }
    val snapshot = a6Snapshot(targets, a6)
    val changed = summary != null && snapshot != a6?.said
    if (failures.isEmpty() && !changed) return null
    return A6Decision(failures, summary, snapshot)
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
        val unlisted = when {
            summary.unlisted == 0 -> ""
            summary.notReplaced == 0 -> " " + autoBackupUnlistedText(summary.unlisted, summary.folders > 1)
            else -> " " + a6UnlistedInDoneText(summary.unlisted, summary.replaced)
        }
        val said = when {
            // Round 6 (I-2): a request asked before a later change of the master password says so, and that the copies made
            // after that change were not deleted.
            summary.stale -> a6StaleText(summary.replaced, summary.folders)
            summary.notReplaced == 0 -> if (summary.folders > 1) AUTO_BACKUP_REPLACED_SEVERAL else AUTO_BACKUP_REPLACED
            else -> a6PartialText(summary.replaced, summary.folders)
        }
        parts += said + unlisted
    }
    return parts.joinToString("\n")
}

/**
 * Round 6 (I-2): [replaced] of [folders] folders finished the request asked at an earlier change of the master password
 * (the vault's mark changed since: another change, or a wrap with a higher cost). True in every case: it says which
 * copies went (those listed before that change), and that those made after it were not deleted.
 */
fun a6StaleText(replaced: Int, folders: Int): String {
    val where = when {
        folders == 1 -> "en la carpeta"
        replaced == folders -> "en todas las carpetas"
        else -> "en $replaced de las $folders carpetas; en las demás puede quedar alguna copia de antes de ese cambio"
    }
    return "Se terminaron de borrar las copias que la app tenía apuntadas de antes de un cambio de contraseña anterior, $where. " +
        "Las copias hechas después de ese cambio no se han borrado: si cambiaste la contraseña otra vez, puede que alguna se " +
        "abra con una contraseña anterior. Míralo a mano."
}

/**
 * Round 6 (m-4): what the dialog after a change of master password shows about its own request: [canReplace] whether
 * «Borrar…» can be pressed, [text] the result (null: none), [negative] whether it is a failure or not all done. [summary]
 * must be of the request that dialog asked (m-1), or null. «Done» with no folder replaced for it is not known as done.
 */
data class A6DialogView(val canReplace: Boolean, val text: String?, val negative: Boolean)

fun a6DialogView(status: ReplaceStatus, working: Boolean, summary: A6Summary?): A6DialogView {
    val done = status is ReplaceStatus.Done
    val partial = done && summary != null && summary.replaced > 0 && summary.notReplaced > 0
    val unknown = done && (summary == null || summary.replaced == 0)
    val canReplace = !working &&
        (status is ReplaceStatus.NotAsked || status is ReplaceStatus.Unknown || status is ReplaceStatus.Failed || partial || unknown)
    return when {
        working || unknown -> A6DialogView(canReplace, null, negative = false)
        done -> A6DialogView(canReplace, a6Text(emptyList(), summary), negative = partial || summary?.stale == true)
        status is ReplaceStatus.Waits -> A6DialogView(canReplace, AUTO_BACKUP_REPLACE_WAITS, negative = false)
        status is ReplaceStatus.Failed -> A6DialogView(canReplace, a6Text(listOf(status.message), summary), negative = true)
        else -> A6DialogView(canReplace, null, negative = false)
    }
}

/** Task 8, round 4: [replaced] of [folders] folders (some, not all) replaced their old copies for the request. */
fun a6PartialText(replaced: Int, folders: Int): String {
    val done = "Se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña (las " +
        "que la app tenía apuntadas) en $replaced de las $folders carpetas."
    val left = folders - replaced
    // Round 5 (m-8): true whatever those folders hold (one added after the change has nothing of before; one that failed
    // half-way deleted some).
    val notDone = if (left == 1) {
        "En la otra carpeta puede quedar alguna copia de antes del cambio de contraseña: míralo a mano."
    } else {
        "En las otras $left carpetas puede quedar alguna copia de antes del cambio de contraseña: míralo a mano."
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
