package app.seguridad.vault

import app.seguridad.settings.AUTO_BACKUP_KEEP_DEFAULT
import kotlinx.coroutines.CoroutineScope
import java.time.ZoneId

/*
 * 2.0, task 8: the tests of 1.x's automatic copies (AutoBackupTest, AutoBackupF3Test, AutoBackupImportCopyTest) wrote to
 * one folder, given by separate readers. Each reader now builds that folder as the only target, [BackupSchedule.ON_CHANGE]
 * as 1.4.3's was, so those tests run against the same engine the app uses with several targets (AutoBackupTargetsTest).
 */

/** [AutoBackup] with one [BackupSchedule.ON_CHANGE] target made, at each read, from 1.4.3's readers. */
fun oneTargetBackup(
    store: VaultStore,
    folder: () -> String?,
    open: (String) -> BackupTarget?,
    scope: CoroutineScope,
    now: () -> Long,
    keep: () -> Int = { AUTO_BACKUP_KEEP_DEFAULT },
    lastCopyAt: () -> Long? = { null },
    copies: () -> List<String> = { emptyList() },
    replacePending: () -> Boolean = { false },
    replaceTargets: () -> List<String>? = { null },
    replaceTold: () -> List<String> = { emptyList() },
    onResult: suspend (AutoBackupResult) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
    importCopies: () -> List<String> = { emptyList() },
    onImportCopy: suspend (folder: String, id: String, generation: Long) -> Unit = { _, _, _ -> },
): AutoBackup = AutoBackup(
    store,
    targets = {
        folder()?.let {
            listOf(
                BackupTargetSettings(
                    it, keep(), BackupSchedule.ON_CHANGE, lastCopyAt(), null, copies(), replacePending(), replaceTargets(),
                    replaceTold(), importCopies(),
                ),
            )
        } ?: emptyList()
    },
    open = open,
    scope = scope,
    now = now,
    onResult = onResult,
    zone = zone,
    onImportCopy = onImportCopy,
)

/** 1.4.3's request(): a copy soon to its one folder, if there is one. */
fun AutoBackup.requestOne(folder: String?) {
    if (folder != null) request(folder)
}
