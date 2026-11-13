package app.seguridad.vault

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2.0, task 8 (design «Copias»): when a target of the automatic copies gets one. [ON_CHANGE] is what 1.x did (a few
 * seconds after each change saved, and at the unlock when one was missed); the others are calendar days in the phone's
 * time zone, and are only made when the app is opened or the vault unlocked ([AutoBackup.runDue]): no background work.
 */
@Serializable
enum class BackupSchedule { ON_CHANGE, DAILY, WEEKLY, MONTHLY }

/**
 * One folder the automatic copies of a vault go to (2.0, task 8; at most [VaultLimits.BACKUP_TARGETS_MAX] per vault), with
 * what 1.4.3 kept for its only folder, now for each: [uri] the folder (a SAF tree URI with a persistable permission; it
 * is the target's identity, never twice in one vault), [keep] how many copies to keep (one of AUTO_BACKUP_KEEP_CHOICES),
 * [schedule], [lastAt] when its last copy was written (epoch ms), [lastError] why its last copy failed (a fixed Spanish
 * text, never a path, a URI or what an exception said), [copies] the document ids of the automatic copies it made, oldest
 * first (the only ones its rotation deletes), and the A6 request to replace its old copies after a change of master
 * password ([replacePending], [replaceTargets], [replaceTold]), as in 1.4.3.
 *
 * [importCopies]: the ids of the copies made before «Importar de otra app» and before the 2.0 upgrade in this folder,
 * oldest first; never rotated, only deleted by A6 (rule R15), by id. Not in the plan's list of fields: A6 «por destino»
 * needs it, as 1.4.3 kept it for its folder.
 */
@Serializable
data class BackupTargetSettings(
    val uri: String,
    val keep: Int,
    val schedule: BackupSchedule,
    val lastAt: Long? = null,
    val lastError: String? = null,
    val copies: List<String> = emptyList(),
    val replacePending: Boolean = false,
    val replaceTargets: List<String>? = null,
    val replaceTold: List<String> = emptyList(),
    val importCopies: List<String> = emptyList(),
    /**
     * Task 8, round 3 (ruling «T8 ronda 3», option A): how many files with exactly a copy's name, not on this target's
     * lists, were in its folder when it replaced its old copies for the A6 request (COPIAS-2); null when it has not
     * replaced them for the request of now (or never could list its folder). Set only by a replacement, whose listing
     * succeeded; cleared by a new request, an import, a new or deleted vault. The success of A6 adds them up over every
     * target, so a folder that finished in an earlier pass still tells its count.
     */
    val replaceUnlisted: Int? = null,
    /**
     * Task 8, round 3 (owner's decision D-3): the vault's change mark ([VaultStore.changeMark]) when this target's last copy
     * was written: a copy by the calendar is skipped while the mark is still this one (the vault did not change). Null
     * until a copy is written (the first copy is never skipped).
     */
    val copiedMark: String? = null,
)

private const val DAY_MS = 24L * 60 * 60 * 1000

/** The longest a period of [this] schedule lasts, to tell a clock set back by more than one period. */
private fun BackupSchedule.longestMs(): Long = when (this) {
    BackupSchedule.ON_CHANGE -> 0L
    BackupSchedule.DAILY -> DAY_MS
    BackupSchedule.WEEKLY -> 7 * DAY_MS
    BackupSchedule.MONTHLY -> 31 * DAY_MS
}

/** The first calendar day a copy is due again after one written on [last]. */
private fun BackupSchedule.nextAfter(last: LocalDate): LocalDate = when (this) {
    BackupSchedule.ON_CHANGE, BackupSchedule.DAILY -> last.plusDays(1)
    BackupSchedule.WEEKLY -> last.plusDays(7)
    // Same day of the next month, or its last day when it has none (31 January → 28 or 29 February).
    BackupSchedule.MONTHLY -> last.plusMonths(1)
}

/**
 * Whether this target gets a copy at [now] by its calendar: never for [BackupSchedule.ON_CHANGE] (changes drive it); with no
 * copy yet, always; otherwise when the calendar day of [now] in [zone] is at or past the day after [lastAt]'s (daily), 7
 * days after (weekly), or the same day of the next month or its last day (monthly).
 *
 * A clock set back makes no copy again: [now] is then before the next day. Only when [now] is more than one whole period
 * behind [lastAt] is [lastAt] taken as written by a clock that ran ahead (set forward, then corrected): one copy is due,
 * whose new [lastAt] restarts the calendar from the real date, so a clock set forward neither loses the schedule for years
 * nor makes more than one copy per target. Copies are never made in a loop: each call answers once.
 */
fun BackupTargetSettings.due(now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (schedule == BackupSchedule.ON_CHANGE) return false
    val last = lastAt ?: return true
    if (last - now > schedule.longestMs()) return true
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return !today.isBefore(schedule.nextAfter(Instant.ofEpochMilli(last).atZone(zone).toLocalDate()))
}
