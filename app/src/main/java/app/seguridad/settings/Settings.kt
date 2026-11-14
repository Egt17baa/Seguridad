package app.seguridad.settings

import app.seguridad.crypto.KdfParams
import app.seguridad.vault.A6State
import app.seguridad.vault.A6Summary
import app.seguridad.vault.AutoBackupResult
import app.seguridad.vault.ReplaceDone
import app.seguridad.vault.a6Summary
import app.seguridad.vault.requestId
import app.seguridad.vault.BackupSchedule
import app.seguridad.vault.BackupTargetSettings
import app.seguridad.vault.VaultLimits
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

/**
 * The settings of the whole app (2.0: the theme, the lock, the clipboard, the A11 clock mark, the notices accepted) and
 * those of each vault ([perVault], by its local id, [app.seguridad.vault.VaultInfo]). Never a secret, nor a vault's name.
 */
@Serializable
data class Settings(
    val lockAfter: LockAfter = LockAfter.MIN_5,
    val clipboard: ClipboardClear = ClipboardClear.S45,
    val theme: ThemeMode = ThemeMode.DARK,
    val noticeAccepted: Boolean = false,
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
     * 2.0, task 2: the settings of each vault, by its local id. A settings.json of 1.4.3 or before had them at the top:
     * [SettingsStore.load] reads them as those of [app.seguridad.vault.PRIMARY_VAULT_ID]. A vault with none has the
     * defaults ([vault]): no automatic copies.
     */
    val perVault: Map<String, VaultSettings> = emptyMap(),
    /**
     * 2.0, task 5 bis: the look (design «Aspecto «Cristal», temas y logo»), with defaults, so a settings.json of 1.4.3
     * (which had only [theme]) reads as «Noche» and «Cristal» with the mode it had. Never a secret.
     */
    val style: ThemeStyle = ThemeStyle.GLASS,
    val palette: PaletteId = PaletteId.NIGHT,
    /** The tones of «Personalizado», kept also while another palette is chosen; null until they are first applied. */
    val custom: CustomPalette? = null,
    /** «Reducir transparencias»: opaque panels, never blurred. [highContrast] turns it on too (its switch shows it so). */
    val reduceTransparency: Boolean = false,
    /** «Alto contraste»: texts at 7:1, opaque panels and marked borders. */
    val highContrast: Boolean = false,
    /**
     * R-C8 of the Abogado Digital: the one line, after updating, that says how to go back to a look without transparencies.
     * As [renameNoticePending]: null until the first start of 2.0 decides it ([SettingsStore.decideRenameNotice]), true
     * only if data from an earlier version was there, false once shown or for a new install.
     */
    val appearanceNoticePending: Boolean? = null,
)

/**
 * 2.0, task 2: what each vault has of its own (design «Varias bóvedas»): its automatic copies, A6, its copies of before
 * importing and its Argon2id cost. 2.0, task 8 (ruling 2 ↔ 8 of the plan): the copies are a list of [targets], at most
 * [VaultLimits.BACKUP_TARGETS_MAX], each with its folder, schedule, number of copies kept, lists and A6 request. A
 * settings.json of 1.4.3 (or of tasks 2 to 7) had one folder: [decodeSettings] turns it into one [BackupSchedule.ON_CHANGE]
 * target, with everything it had ([LegacyVaultSettings]).
 */
@Serializable
data class VaultSettings(
    val kdf: KdfProfile = KdfProfile.STANDARD,
    /**
     * The highest time of day the app ever saw (epoch ms), a mark that only rises, except that this vault's master
     * password restarts it: a clock set back behind it ends this vault's fingerprint (A11). 2.0 (ruling T2, I-1): one per
     * vault, so another vault's master password never heals this one's clock; 1.4.3 kept it at the top of the file,
     * where [SettingsStore.load] reads it as the first vault's.
     */
    val clockHighWaterMs: Long = 0,
    /** The folders the automatic copies go to, in the order they are made; none when the copies are off. */
    val targets: List<BackupTargetSettings> = emptyList(),
    /** The automatic copies were turned off by creating a new vault: Ajustes says so until a folder is chosen again. */
    val autoBackupOffForNewVault: Boolean = false,
    /**
     * What folders that are no longer a target (removed, or changed for another) had, as 1.4.3 kept its list when the copies
     * were turned off: their lists of copies ([BackupTargetSettings.copies], [BackupTargetSettings.importCopies]), their
     * number of copies and calendar, an A6 request still waiting there with what it told (O-5), their result for the
     * request ([BackupTargetSettings.replaceRequest], [BackupTargetSettings.replaceDone]) and their change mark; the same
     * folder chosen again (added, or a target changed to it) gets them back and goes on rotating them. Only folders with
     * some copy listed; at most [PAST_TARGETS_MAX], the newest. Forgotten with a new or an imported vault.
     */
    val pastTargets: List<BackupTargetSettings> = emptyList(),
    /** Task 8, round 4: the A6 request of now and what was last told of it; null when none was asked (or for 1.4.3's). */
    val a6: A6State? = null,
)

/** How many lists of folders no longer chosen [VaultSettings.pastTargets] keeps. */
const val PAST_TARGETS_MAX = 6

/** The settings of the vault [id]; the defaults (no copies, the standard cost) for a vault that has none yet. */
fun Settings.vault(id: String): VaultSettings = perVault[id] ?: VaultSettings()

/** These settings with those of the vault [id] changed by [change]. */
fun Settings.withVault(id: String, change: (VaultSettings) -> VaultSettings): Settings {
    val before = vault(id)
    val after = change(before)
    return if (after == before && id in perVault) this else copy(perVault = perVault + (id to after))
}

/** These settings without those of the vault [id] (a vault deleted: its copies stay where they are, unlisted). */
fun Settings.withoutVault(id: String): Settings = if (id in perVault) copy(perVault = perVault - id) else this

/** The target whose folder is [uri]; null if it is not one. */
fun VaultSettings.target(uri: String): BackupTargetSettings? = targets.firstOrNull { it.uri == uri }

/** The folders of the targets, in their order. */
val VaultSettings.backupFolders: List<String> get() = targets.map { it.uri }

/** These settings with the target of [uri] changed by [change]; as they are if it is not a target. */
fun VaultSettings.withTarget(uri: String, change: (BackupTargetSettings) -> BackupTargetSettings): VaultSettings =
    if (targets.none { it.uri == uri }) this else copy(targets = targets.map { if (it.uri == uri) change(it) else it })

/**
 * Every copy, automatic or of before importing (and of before 2.0), listed in any target: what A6 replaces after a change
 * of master password ([app.seguridad.vault.MasterChanged]). Each target only ever deletes the ids of its own lists.
 */
val VaultSettings.listedCopies: List<String> get() = targets.flatMap { it.copies + it.importCopies }

/** Whether any target waits to replace its old copies (A6). */
val VaultSettings.replacePending: Boolean get() = targets.any { it.replacePending }

/**
 * The targets the dialog after a change of master password reads (review M-5 of task 8): those whose A6 request still
 * waits, while any does; once none does, every target (the replacement went to all of them).
 */
private val VaultSettings.a6Targets: List<BackupTargetSettings> get() = targets.filter { it.replacePending }.ifEmpty { targets }

/** Whether a failure of the A6 request was already told in any target that has it. */
val VaultSettings.replaceToldAny: Boolean get() = a6Targets.any { it.replaceTold.isNotEmpty() }

/** The first failure, in the targets' order, of those [a6Targets]; null when none failed. */
val VaultSettings.firstBackupError: String? get() = a6Targets.firstNotNullOfOrNull { it.lastError }

/** When the target of [a6Targets] that copied least recently last did; null if any has none yet (or there is no target). */
val VaultSettings.oldestBackupAt: Long?
    get() = a6Targets.let { t -> if (t.isEmpty() || t.any { it.lastAt == null }) null else t.minOf { it.lastAt!! } }

/** Task 8, round 4: [a6Summary] of these settings: the request of now over the targets of now. */
val VaultSettings.a6Summary: A6Summary get() = a6Summary(targets, a6)

/** Why a target cannot be added ([withTargetAdded]); null when it can. */
enum class TargetRefusal { TOO_MANY, ALREADY_A_TARGET }

/** Whether [uri] can be added as a new target. */
fun VaultSettings.canAddTarget(uri: String): TargetRefusal? = when {
    targets.any { it.uri == uri } -> TargetRefusal.ALREADY_A_TARGET
    targets.size >= VaultLimits.BACKUP_TARGETS_MAX -> TargetRefusal.TOO_MANY
    else -> null
}

/**
 * These settings with [uri] added as the last target, with [schedule] and [keep]: as they are if [canAddTarget] refuses.
 * The lists of that folder kept from before ([pastTargets]) come back with it, so its copies go on rotating, as 1.4.3 did
 * with the same folder chosen again, and so do its «Conservar las N últimas» and its calendar (review I-1 of task 8: a
 * folder chosen again never rotates with fewer copies than the user had chosen for it, so no setting deletes copies the
 * user wanted to keep).
 */
fun VaultSettings.withTargetAdded(uri: String, schedule: BackupSchedule, keep: Int = AUTO_BACKUP_KEEP_DEFAULT): VaultSettings {
    if (canAddTarget(uri) != null) return this
    val past = pastTargets.firstOrNull { it.uri == uri }
    val added = restored(uri, past?.keep?.takeIf { it in AUTO_BACKUP_KEEP_CHOICES } ?: keep, past?.schedule ?: schedule, past)
    return copy(targets = targets + added, pastTargets = pastTargets.filter { it.uri != uri }, autoBackupOffForNewVault = false)
}

/**
 * The target of [uri], with [keep] and [schedule], and what the folder had when it stopped being one ([past]): its lists,
 * and (round 3, O-5) an A6 request that was waiting there, even if the request of the other folders closed meanwhile (the
 * copies listed there are still under the master password of before), and (round 4, m-2) its result for the request and
 * its change mark. One code for adding a folder and for changing a folder to it (round 5, m-1).
 */
private fun restored(uri: String, keep: Int, schedule: BackupSchedule, past: BackupTargetSettings?) = BackupTargetSettings(
    uri, keep, schedule,
    copies = past?.copies.orEmpty(), importCopies = past?.importCopies.orEmpty(),
    replacePending = past?.replacePending == true, replaceTargets = past?.replaceTargets, replaceTold = past?.replaceTold.orEmpty(),
    replaceRequest = past?.replaceRequest, replaceDone = past?.replaceDone, copiedMark = past?.copiedMark,
)

/**
 * These settings with the target of [from] going to the folder [to] instead (Ajustes, «Cambiar de carpeta»), with its
 * schedule and number of copies. The same folder chosen again keeps its lists and its A6 request, and starts its date and
 * error again (as 1.4.3); another folder starts with what that folder had ([pastTargets], as [withTargetAdded], round 5),
 * and the lists of [from] (with its request) are kept there. As they are if [from] is not a target or [to] is another target.
 */
fun VaultSettings.withTargetFolderChanged(from: String, to: String): VaultSettings {
    val old = target(from) ?: return this
    if (from == to) return withTarget(from) { it.copy(lastAt = null, lastError = null) }.copy(autoBackupOffForNewVault = false)
    if (targets.any { it.uri == to }) return this
    val past = pastTargets.firstOrNull { it.uri == to }
    // Review I-1: the folder's lists rotate with the larger of the two numbers of copies, never with fewer than chosen.
    val keep = maxOf(old.keep, past?.keep?.takeIf { it in AUTO_BACKUP_KEEP_CHOICES } ?: old.keep)
    val fresh = restored(to, keep, old.schedule, past)
    return copy(
        targets = targets.map { if (it.uri == from) fresh else it },
        pastTargets = keptPast(pastTargets.filter { it.uri != to && it.uri != from }, old),
        autoBackupOffForNewVault = false,
    )
}

/**
 * These settings without the target of [uri] (Ajustes, «Quitar»): what it had (its lists, an A6 request still waiting
 * there, its result) is kept in [pastTargets] and comes back if the folder does (O-5); while it is out, the request does
 * not delete anything there.
 */
fun VaultSettings.withTargetRemoved(uri: String): VaultSettings {
    val old = target(uri) ?: return this
    return copy(targets = targets.filter { it.uri != uri }, pastTargets = keptPast(pastTargets.filter { it.uri != uri }, old))
}

/**
 * [past] with the lists of [gone] (and its number of copies and calendar) added as the newest, at most [PAST_TARGETS_MAX];
 * nothing added when it has none.
 */
internal fun keptPast(past: List<BackupTargetSettings>, gone: BackupTargetSettings): List<BackupTargetSettings> {
    if (gone.copies.isEmpty() && gone.importCopies.isEmpty()) return past
    // Round 3 (O-5): an A6 request still waiting goes with the lists it is about, to come back if the folder does.
    val kept = BackupTargetSettings(
        gone.uri, gone.keep, gone.schedule, copies = gone.copies, importCopies = gone.importCopies,
        replacePending = gone.replacePending, replaceTargets = gone.replaceTargets, replaceTold = gone.replaceTold,
        replaceRequest = gone.replaceRequest, replaceDone = gone.replaceDone, copiedMark = gone.copiedMark,
    )
    return (past + kept).takeLast(PAST_TARGETS_MAX)
}

/** Settings with no automatic copy, nor import copy, listed as this app's in any target or past target, and no A6 request. */
fun VaultSettings.forgettingAutoBackupCopies(): VaultSettings =
    copy(
        targets = targets.map { it.copy(copies = emptyList(), importCopies = emptyList(), copiedMark = null).withoutReplaceRequest() },
        pastTargets = emptyList(),
        a6 = null,
    )

/**
 * These settings with the import copy [id] just written to [folder] added to that target's [BackupTargetSettings.importCopies]:
 * only if [folder] is still a target, and only if the copy's vault, [generation], is still [current].
 */
fun VaultSettings.withImportCopy(folder: String, id: String, generation: Long? = null, current: Long? = null): VaultSettings {
    if (generation != null && current != null && generation != current) return this
    return withTarget(folder) { it.copy(importCopies = (it.importCopies + id).distinct()) }
}

/** This target with no request to replace the old copies: its targets and what it told go with it. */
fun BackupTargetSettings.withoutReplaceRequest(): BackupTargetSettings =
    copy(replacePending = false, replaceTargets = null, replaceTold = emptyList(), replaceRequest = null, replaceDone = null)

/** Shown in Ajustes while [VaultSettings.autoBackupOffForNewVault]. */
const val AUTO_BACKUP_OFF_NEW_VAULT = "La copia automática se ha desactivado al crear una bóveda nueva, para no borrar las copias de la anterior. " +
    "Elige la carpeta otra vez para activarla."

/**
 * How the last copy to the target of [r]'s folder went, for Ajustes: only when [r] went to a folder that is still a
 * target (removed or changed meanwhile, that copy says nothing about the current one). [generation] is the vault's
 * ([app.seguridad.vault.VaultStore.generation]) when this is recorded: a result of a vault that was replaced since
 * ([AutoBackupResult.generation] differs) keeps neither its list nor its «replaced», so the forgotten list of the replaced
 * vault is never written back (CADENAS-3).
 */
fun VaultSettings.withAutoBackupResult(r: AutoBackupResult, generation: Long? = null): VaultSettings = withTarget(r.folder) { it.withResult(r, generation) }

/** [VaultSettings.withAutoBackupResult] for this target, whatever [AutoBackupResult.folder] says (the caller routed it). */
fun BackupTargetSettings.withResult(r: AutoBackupResult, generation: Long? = null): BackupTargetSettings {
    val stale = r.generation != null && generation != null && r.generation != generation
    return recording(if (stale) r.copy(copies = null, replaced = false, replaceTargets = null, importCopies = null) else r)
}

private fun BackupTargetSettings.recording(r: AutoBackupResult): BackupTargetSettings = when {
    // Asked while the vault was locked: the request, its targets (M4) and nothing told yet; what Ajustes shows about the
    // last copy does not change. 1.4.0: the import copies of this folder were made under the master password of before
    // too, so they are targets.
    r.replaceRequested -> copy(
        replacePending = true, replaceTargets = r.replaceTargets?.let { (it + importCopies).distinct() }, replaceTold = emptyList(),
        replaceRequest = r.request, replaceDone = null,
    )
    // D-3: the change mark of the copy written (null when unknown keeps none, so the next copy by the calendar is made).
    r.writtenAt != null && r.copies != null ->
        copy(lastError = r.error, lastAt = r.writtenAt, copies = r.copies, copiedMark = r.mark).withImportCopiesLeft(r).replaceAfter(r)
    r.writtenAt != null -> copy(lastError = r.error, lastAt = r.writtenAt).replaceAfter(r)
    else -> copy(lastError = r.error).replaceAfter(r)
}

/** The import copies [r] found still listed and in its folder (a replacement deleted the others, or they are gone). */
private fun BackupTargetSettings.withImportCopiesLeft(r: AutoBackupResult): BackupTargetSettings =
    if (r.importCopies != null) copy(importCopies = r.importCopies) else this

/**
 * The pending request after [r]: done when [r] replaced the old copies (CR-1: AutoBackup says so only when every target
 * and leftover temporary copy went); otherwise kept, with the targets left (M4) and the failure it told added (M2).
 */
private fun BackupTargetSettings.replaceAfter(r: AutoBackupResult): BackupTargetSettings {
    // Round 4: a replacement saves its result for its request, whether the request was saved (asked while locked) or not
    // (asked with the vault open, then maybe cut by a lock before the other folders).
    if (r.replaced) {
        val request = r.request ?: requestId
        return withoutReplaceRequest().copy(replaceRequest = request, replaceDone = ReplaceDone(request, r.deleted, r.unlistedLeft))
    }
    // Asked again with the vault open and not replaced (it failed): it is in the new request, with no result for it.
    val joined = if (r.newRequest && r.request != null) copy(replaceRequest = r.request, replaceDone = null) else this
    if (!replacePending) return joined
    val told = if (r.newRequest) emptyList() else replaceTold
    return joined.copy(
        replaceTargets = r.replaceTargets ?: replaceTargets,
        replaceTold = if (r.toldKey != null && r.toldKey !in told) told + r.toldKey else told,
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
fun VaultSettings.seeingClock(seen: Long): VaultSettings = if (seen > clockHighWaterMs) copy(clockHighWaterMs = seen) else this

/** These settings after a master password check at [now]: the mark restarts there, which may lower it. */
fun VaultSettings.resettingClock(now: Long): VaultSettings = if (now == clockHighWaterMs) this else copy(clockHighWaterMs = now)

/**
 * COV2-1 (owner's ruling): whether Ajustes lets the copies' folder (choose, change, turn off), «Conservar las N
 * últimas», the idle lock, the clipboard time and the Argon2id cost be changed. Only with the vault open, or with no
 * vault (nothing to protect, and the Argon2id cost also serves text and file encryption): someone holding the phone
 * for a moment with the vault locked must not redirect its copies or weaken its defences.
 */
fun settingsEditable(state: VaultState): Boolean = state is VaultState.Unlocked || state is VaultState.NoVault
