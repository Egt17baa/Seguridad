package app.seguridad.settings

import app.seguridad.crypto.KdfParams
import app.seguridad.vault.AutoBackupResult
import app.seguridad.vault.VaultState
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
     * The document ids of the automatic copies made in [autoBackupCopiesFolder], oldest first: the only ones rotation
     * deletes ([app.seguridad.vault.AutoBackupResult.copies]), and only while that is still [autoBackupUri]. Kept
     * when the copies are turned off, so choosing the same folder again goes on rotating them; forgotten with a new
     * or an imported vault (they are the previous vault's).
     */
    val autoBackupCopies: List<String> = emptyList(),
    val autoBackupCopiesFolder: String? = null,
    /**
     * «Borrar las copias automáticas anteriores y hacer una nueva» was asked for while the vault was locked (it always is
     * right after a change of master password): the copies made after the next unlock replace the old ones, and this is
     * cleared once a copy has deleted every target ([autoBackupReplaceTargets]) and every leftover temporary copy; until
     * then each copy tries again. Saved, so a restart in between does not lose the request.
     */
    val autoBackupReplacePending: Boolean = false,
    /**
     * M4: the ids of the copies that request still has to delete, those listed when it was asked (made before the change
     * of master password); copies made after it follow [autoBackupKeep]. Null for a request saved before 1.3.0 (a default,
     * so an older settings.json still reads): its first attempt deletes every listed copy but the new one, as before, and
     * what it could not delete becomes this list. Cleared with the request.
     */
    val autoBackupReplaceTargets: List<String>? = null,
    /**
     * M2: the failures already told to the user for that request (fixed texts, as [lastAutoBackupError]), so each is told
     * once per request, also after a restart. Cleared with the request, and when it is asked again.
     */
    val autoBackupReplaceTold: List<String> = emptyList(),
    /** The highest time of day the app ever saw (epoch ms), a mark that only rises: a clock set back behind it ends the fingerprint. */
    val clockHighWaterMs: Long = 0,
    /**
     * A14: the notice about the keyboard's clipboard history (Android 8 to 12, with no EXTRA_IS_SENSITIVE) was accepted;
     * copying a secret no longer asks. With a default, so a settings.json from 1.2.2 or before still reads.
     */
    val clipboardHistoryNoticeAccepted: Boolean = false,
    /**
     * The notice that the app used to be called «Seguridad» ([RenameNotice]): null until the first start of 1.3.0 decides
     * it (the default, so a settings.json from 1.2.x still reads); then true only if data from an earlier version was
     * there (this file, or the vault), and false from the moment it is shown, or for a new install.
     */
    val renameNoticePending: Boolean? = null,
    /**
     * 1.4.0: the document ids of the copies made just before «Importar de otra app» in [importCopiesFolder], oldest
     * first ([app.seguridad.vault.AutoBackup.copyBeforeImport]). Never rotated and not in [autoBackupCopies]; they are
     * encrypted with the master password of their moment, so a request to replace the old copies after a change of it
     * (A6) takes those of the chosen folder among its targets ([withAutoBackupResult]). Forgotten with a new or an
     * imported vault, as [autoBackupCopies]. With defaults, so an older settings.json still reads.
     */
    val importCopies: List<String> = emptyList(),
    val importCopiesFolder: String? = null,
)

/** [Settings.autoBackupCopies] if they are in the folder chosen now; none otherwise. */
val Settings.currentAutoBackupCopies: List<String>
    get() = if (autoBackupUri != null && autoBackupCopiesFolder == autoBackupUri) autoBackupCopies else emptyList()

/** [Settings.importCopies] if they are in the folder chosen now; none otherwise. */
val Settings.currentImportCopies: List<String>
    get() = if (autoBackupUri != null && importCopiesFolder == autoBackupUri) importCopies else emptyList()

/** Settings with no automatic copy, nor import copy, listed as this app's (see [Settings.autoBackupCopies]). */
fun Settings.forgettingAutoBackupCopies(): Settings =
    copy(autoBackupCopies = emptyList(), autoBackupCopiesFolder = null, importCopies = emptyList(), importCopiesFolder = null)
        .withoutReplaceRequest()

/**
 * These settings with the import copy [id] just written to [folder] added to [Settings.importCopies]: only if [folder] is
 * still the chosen one, and (as [withAutoBackupResult]) only if the copy's vault, [generation], is still [current]. A list
 * of another folder is replaced.
 */
fun Settings.withImportCopy(folder: String, id: String, generation: Long? = null, current: Long? = null): Settings {
    if (autoBackupUri != folder || (generation != null && current != null && generation != current)) return this
    val kept = if (importCopiesFolder == folder) importCopies else emptyList()
    return copy(importCopies = (kept + id).distinct(), importCopiesFolder = folder)
}

/** These settings with no request to replace the old copies: its targets and what it told go with it. */
fun Settings.withoutReplaceRequest(): Settings =
    copy(autoBackupReplacePending = false, autoBackupReplaceTargets = null, autoBackupReplaceTold = emptyList())

/**
 * These settings with [chosen] as the folder for the copies. The request to replace the old copies ([Settings.autoBackupReplacePending])
 * goes only if the folder is another one: the same folder chosen again still has the copies it was asked about.
 */
fun Settings.withFolderChosen(chosen: String): Settings = copy(
    autoBackupUri = chosen,
    lastAutoBackupError = null,
    lastAutoBackupAt = null,
    autoBackupOffForNewVault = false,
).let { if (chosen == autoBackupUri) it else it.withoutReplaceRequest() }

/** These settings with the automatic copies turned off by the user: a request to replace the old ones goes with them. */
fun Settings.withAutoBackupTurnedOff(): Settings =
    copy(autoBackupUri = null, lastAutoBackupError = null, lastAutoBackupAt = null).withoutReplaceRequest()

/** Shown in Ajustes while [Settings.autoBackupOffForNewVault]. */
const val AUTO_BACKUP_OFF_NEW_VAULT = "La copia automática se ha desactivado al crear una bóveda nueva, para no borrar las copias de la anterior. " +
    "Elige la carpeta otra vez para activarla."

/**
 * How the last automatic copy went, for Ajustes: only when [r] went to the folder still chosen (turned off or another
 * folder chosen meanwhile, that copy says nothing about the current one). A written copy updates the date even if its
 * old copies could not be deleted. [generation] is the vault's ([app.seguridad.vault.VaultStore.generation]) when this
 * is recorded: a result of a vault that was replaced since ([AutoBackupResult.generation] differs) keeps neither its
 * list nor its «replaced», so the forgotten list of the replaced vault is never written back (CADENAS-3).
 */
fun Settings.withAutoBackupResult(r: AutoBackupResult, generation: Long? = null): Settings {
    val stale = r.generation != null && generation != null && r.generation != generation
    return recording(if (stale) r.copy(copies = null, replaced = false, replaceTargets = null, importCopies = null) else r)
}

private fun Settings.recording(r: AutoBackupResult): Settings = when {
    autoBackupUri != r.folder -> this
    // Asked while the vault was locked: the request, its targets (M4) and nothing told yet; what Ajustes shows about the
    // last copy does not change.
    // 1.4.0: the import copies of this folder were made under the master password of before too, so they are targets.
    r.replaceRequested ->
        copy(
            autoBackupReplacePending = true,
            autoBackupReplaceTargets = r.replaceTargets?.let { (it + importCopiesIn(r.folder)).distinct() },
            autoBackupReplaceTold = emptyList(),
        )
    r.writtenAt != null && r.copies != null ->
        copy(
            lastAutoBackupError = r.error,
            lastAutoBackupAt = r.writtenAt,
            autoBackupCopies = r.copies,
            autoBackupCopiesFolder = r.folder,
        ).withImportCopiesLeft(r).replaceAfter(r)
    r.writtenAt != null -> copy(lastAutoBackupError = r.error, lastAutoBackupAt = r.writtenAt).replaceAfter(r)
    else -> copy(lastAutoBackupError = r.error).replaceAfter(r)
}

/** [Settings.importCopies] in [folder]; none if the list is of another folder. */
private fun Settings.importCopiesIn(folder: String): List<String> = if (importCopiesFolder == folder) importCopies else emptyList()

/** The import copies [r] found still listed and in its folder (a replacement deleted the others, or they are gone). */
private fun Settings.withImportCopiesLeft(r: AutoBackupResult): Settings =
    if (r.importCopies != null && importCopiesFolder == r.folder) copy(importCopies = r.importCopies) else this

/**
 * The pending request after [r]: done when [r] replaced the old copies (CR-1: AutoBackup says so only when every target
 * and leftover temporary copy went); otherwise kept, with the targets left (M4) and the failure it told added (M2).
 */
private fun Settings.replaceAfter(r: AutoBackupResult): Settings {
    if (!autoBackupReplacePending) return this
    if (r.replaced) return withoutReplaceRequest()
    val told = if (r.newRequest) emptyList() else autoBackupReplaceTold
    return copy(
        autoBackupReplaceTargets = r.replaceTargets ?: autoBackupReplaceTargets,
        autoBackupReplaceTold = if (r.toldKey != null && r.toldKey !in told) told + r.toldKey else told,
    )
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

/** The clock mark is rewritten only when the time is this far past it (it is told at every touch). */
const val CLOCK_WRITE_STEP_MS = 30_000L

/** Whether [seen] is worth writing over [mark]: at least [CLOCK_WRITE_STEP_MS] past it. A lower time never is. */
fun clockMarkWorthWriting(mark: Long, seen: Long): Boolean = seen >= mark + CLOCK_WRITE_STEP_MS

/** These settings having seen [seen]: the mark only rises. */
fun Settings.seeingClock(seen: Long): Settings = if (seen > clockHighWaterMs) copy(clockHighWaterMs = seen) else this

/** These settings after a master password check at [now]: the mark restarts there, which may lower it. */
fun Settings.resettingClock(now: Long): Settings = if (now == clockHighWaterMs) this else copy(clockHighWaterMs = now)

/**
 * COV2-1 (owner's ruling): whether Ajustes lets the copies' folder (choose, change, turn off), «Conservar las N
 * últimas», the idle lock, the clipboard time and the Argon2id cost be changed. Only with the vault open, or with no
 * vault (nothing to protect, and the Argon2id cost also serves text and file encryption): someone holding the phone
 * for a moment with the vault locked must not redirect its copies or weaken its defences.
 */
fun settingsEditable(state: VaultState): Boolean = state is VaultState.Unlocked || state is VaultState.NoVault
