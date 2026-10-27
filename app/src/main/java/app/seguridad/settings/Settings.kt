package app.seguridad.settings

import app.seguridad.crypto.KdfParams
import app.seguridad.vault.AutoBackupResult
import kotlinx.serialization.Serializable

@Serializable
enum class LockAfter(val ms: Long, val label: String) {
    ON_EXIT(0, "Al salir"),
    MIN_1(60_000, "1 minuto"),
    MIN_5(300_000, "5 minutos"),
    MIN_15(900_000, "15 minutos"),
}

@Serializable
enum class ClipboardClear(val seconds: Int) { S15(15), S45(45), S90(90) }

@Serializable
enum class KdfProfile(val params: KdfParams, val label: String) {
    STANDARD(KdfParams(64 * 1024, 3, 1), "Estándar (64 MiB)"),
    HIGH(KdfParams(128 * 1024, 4, 1), "Alto (128 MiB)"),
}

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
data class Settings(
    val lockAfter: LockAfter = LockAfter.MIN_5,
    val clipboard: ClipboardClear = ClipboardClear.S45,
    val kdf: KdfProfile = KdfProfile.STANDARD,
    val theme: ThemeMode = ThemeMode.DARK,
    val noticeAccepted: Boolean = false,
    /** The folder (a SAF tree URI, with a persistable permission) for the automatic copies; null when off. */
    val autoBackupUri: String? = null,
    /** How many automatic copies to keep: one of [AUTO_BACKUP_KEEP_CHOICES]. */
    val autoBackupKeep: Int = AUTO_BACKUP_KEEP_DEFAULT,
    /** Why the last automatic copy failed (a fixed Spanish message, never a path or file contents); null if it went well. */
    val lastAutoBackupError: String? = null,
    /** When the last automatic copy was written (epoch ms); null if none yet. */
    val lastAutoBackupAt: Long? = null,
    /** The automatic copies were turned off by creating a new vault: Ajustes says so until a folder is chosen again. */
    val autoBackupOffForNewVault: Boolean = false,
    /**
     * The document ids of the automatic copies made in [autoBackupUri], oldest first: the only ones rotation deletes
     * ([app.seguridad.vault.AutoBackupResult.copies]). Emptied whenever the folder changes.
     */
    val autoBackupCopies: List<String> = emptyList(),
)

/** Shown in Ajustes while [Settings.autoBackupOffForNewVault]. */
const val AUTO_BACKUP_OFF_NEW_VAULT = "La copia automática se ha desactivado al crear una bóveda nueva, para no borrar las copias de la anterior. " +
    "Elige la carpeta otra vez para activarla."

/**
 * How the last automatic copy went, for Ajustes: only when [r] went to the folder still chosen (turned off or another
 * folder chosen meanwhile, that copy says nothing about the current one). A written copy updates the date even if its
 * old copies could not be deleted.
 */
fun Settings.withAutoBackupResult(r: AutoBackupResult): Settings = when {
    autoBackupUri != r.folder -> this
    r.writtenAt != null -> copy(lastAutoBackupError = r.error, lastAutoBackupAt = r.writtenAt, autoBackupCopies = r.copies ?: autoBackupCopies)
    else -> copy(lastAutoBackupError = r.error)
}

/** How many automatic copies can be kept. */
val AUTO_BACKUP_KEEP_CHOICES = listOf(3, 5, 10)
const val AUTO_BACKUP_KEEP_DEFAULT = 5

/**
 * Whether to offer this profile for new data: STANDARD always; HIGH only when [heapKiB] / 2 can hold its memory.
 * Stricter than what [KdfParams.checkSane] accepts when reading (a quarter of the heap free), so data made here
 * also opens on phones with a somewhat smaller heap.
 */
fun KdfProfile.availableOn(heapKiB: Long): Boolean = this == KdfProfile.STANDARD || heapKiB / 2 >= params.memoryKiB
