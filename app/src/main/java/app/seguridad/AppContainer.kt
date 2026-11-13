package app.seguridad

import app.seguridad.core.MemoryPlaceStore
import app.seguridad.vault.VaultLimitExceeded
import app.seguridad.vault.VAULT_NEW_NEEDS_OPEN
import app.seguridad.vault.VAULT_FAILED
import app.seguridad.crypto.CryptoFailure
import app.seguridad.core.Notice
import app.seguridad.core.PickerPlaces
import app.seguridad.core.PlaceStore
import app.seguridad.net.PwnedPasswords
import app.seguridad.net.TrustedChain
import app.seguridad.net.secureClient
import app.seguridad.settings.CLOCK_WRITE_STEP_MS
import app.seguridad.settings.AppearanceNotice
import app.seguridad.settings.RenameNotice
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.clockMarkWorthWriting
import app.seguridad.settings.backupFolders
import app.seguridad.settings.listedCopies
import app.seguridad.settings.resettingClock
import app.seguridad.settings.seeingClock
import app.seguridad.settings.vault
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.settings.withImportCopy
import app.seguridad.settings.withVault
import app.seguridad.settings.withoutVault
import app.seguridad.imports.compareKey
import app.seguridad.tools.ClipboardNoticeRequest
import app.seguridad.tools.TwoStepCopy
import app.seguridad.tools.share.ShareTemp
import app.seguridad.vault.AutoBackup
import app.seguridad.vault.AutoBackupResult
import app.seguridad.vault.BackupTarget
import app.seguridad.vault.LockReason
import app.seguridad.vault.MasterChanged
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.TagColor
import app.seguridad.vault.UndoTrash
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultDeletion
import app.seguridad.vault.VaultRegistry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.WordLists
import app.seguridad.vault.checkStructure
import app.seguridad.vault.displayName
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** The settings file, in the app's private files directory. */
const val SETTINGS_FILE = "settings.json"

/**
 * The last guard of [AppContainer.appScope]: an exception a background job did not catch (each one catches what it
 * expects) must never close the app, as it would on Android (BOV-1). An Error (OutOfMemoryError, say) goes on to the
 * thread's handler as before. Nothing is logged: it could carry data.
 */
private val backgroundFailure = CoroutineExceptionHandler { _, e ->
    if (e !is Exception) Thread.currentThread().let { t -> t.uncaughtExceptionHandler?.uncaughtException(t, e) }
}

/**
 * Manual dependency graph, one per process. Plain JVM: no Android types, so jvm-check compiles it too.
 * [backupFolder] opens the folder saved for the automatic copies (Android passes the SAF one), and [releaseFolder]
 * gives its access back when creating a new vault turns them off.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest: the state of whichever vault is active.
class AppContainer(
    filesDir: File,
    /** The app's private cache; holds the copy handed to another app by «Compartir» (see [shareTemp]). */
    cacheDir: File,
    /** The installed version (BuildConfig.VERSION_NAME), for the User-Agent of the HIBP queries. */
    appVersion: String,
    private val backupFolder: (String) -> BackupTarget? = { null },
    private val releaseFolder: (String) -> Unit = {},
    /**
     * Lives as long as the process: the automatic copies, the record of how they went, and the clean-ups that must finish
     * even if the screen that started them goes away (the temporary files of «Abrir lo recibido»).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailure),
    /**
     * A clock that only moves forward, also while the phone sleeps (SeguridadApp passes SystemClock.elapsedRealtime):
     * the vault's idle lock (COV2-2) and the wait before writing the clock mark again after a failed write.
     */
    private val elapsedRealtime: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Where the settings file is read and written (tests run it in place). */
    settingsIo: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Revokes the read permission of a shared copy's URI before [shareTemp] deletes it (ANDROID-1; Android passes
     * Context.revokeUriPermission through the FileProvider). May throw: the copy is deleted all the same.
     */
    revokeShare: (File) -> Unit = {},
    /** 1.4.2: where the kept places of the file pickers are written ([PickerPlaces]); in memory on the JVM. */
    placeStore: PlaceStore = MemoryPlaceStore(),
    /** The app's package, for [PickerPlaces]' check that a place is another app's document. */
    packageName: String = "io.github.egt17baa.seguridad",
    /**
     * CRY-B01: the chain the system validated, for the eMudhra check of [client]. SeguridadApp passes androidTrustedChain
     * (Android only); null on the JVM, which checks the chain the server presented (see secureClient).
     */
    trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null,
    /**
     * Opens a file of the app's `assets/` for [wordLists]: SeguridadApp passes AssetManager.open. On the JVM there are
     * none, so the lists cannot be read and the rule of a new master stays at the length and the estimate.
     */
    openAsset: (String) -> InputStream = { name -> throw FileNotFoundException(name) },
    /**
     * 2.0, task 2: forgets the fingerprint key and its preferences of a vault that is deleted ([deleteVault]); Android
     * passes BiometricVault.disable for that vault's id. A failure leaves a key that wraps the data key of a vault that is
     * gone, which opens nothing.
     */
    private val forgetBiometric: suspend (String) -> Unit = {},
    /** Where the vaults derive and encrypt ([VaultStore]'s compute); the tests hold it to stop a save half way. */
    private val vaultCompute: CoroutineDispatcher = Dispatchers.Default,
    /** The wall clock of the vaults ([VaultStore]'s now); the tests move it (the trash's 30 days, task 3). */
    private val vaultNow: () -> Long = System::currentTimeMillis,
) {
    val settings = SettingsStore(File(filesDir, SETTINGS_FILE), settingsIo)

    /**
     * The app's activity is started (MainActivity.onStart to onStop). Set to false in onStop before the vault is locked,
     * so a lock seen while it is true happened with the app in use, on any tab («PIN y códigos» wipes then); a lock
     * from leaving the app is seen with it false.
     */
    @Volatile var foreground: Boolean = false

    private val _awayInOtherApp = MutableStateFlow(false)

    /**
     * COV2-3: in split screen or a floating window, another app has the focus (Seguridad is still visible, so no onStop).
     * Set by MainActivity through [leftForAnotherApp] and [backFromAnotherApp]; the vault's idle lock reads it (the
     * «Al salir» backstop) and «PIN y códigos» counts it as time away (M3 of the F1 review). The app's own dialogs and
     * menus never change it: they are windows of the activity, not activities.
     */
    val awayInOtherApp: StateFlow<Boolean> = _awayInOtherApp.asStateFlow()

    /**
     * The word lists of `assets/` (the rule of a new master and the Generator's phrases), read once per process and off
     * the main thread, so no screen reads or normalizes them again when it opens or turns (F6). Not secret.
     */
    val wordLists = WordLists(openAsset)

    /**
     * 2.0, task 2: the vaults of the phone (ids, colours, order and the last one opened; never names) in
     * `files/vaults.json`. Read once, here: a few hundred bytes, before the first frame as the settings.
     */
    val registry = VaultRegistry(filesDir)

    /** 1.4.2: where each file picker opens ([PickerPlace]); forgotten with the vault. */
    val pickerPlaces = PickerPlaces(placeStore, packageName, sessionOpen = { vault.state.value is VaultState.Unlocked })

    /** The only connection of the app (Have I Been Pwned), with the eMudhra keys distrusted; built on first use. */
    val client: OkHttpClient by lazy { secureClient(appVersion, trustedChainOf) }

    private val vaultDir = File(filesDir, "vault")

    /**
     * The notice of the new name (owner's decision, 31-10-2026), shown once to those who updated: here, not in a screen,
     * so a rotation keeps the dialog up until «Entendido».
     */
    val renameNotice = RenameNotice(settings, appScope)

    /** 2.0, task 5 bis (R-C8): the line after updating on how to go back to a look without transparencies. */
    val appearanceNotice = AppearanceNotice(settings, appScope)

    /**
     * At the start of the app, after the settings are loaded: decides once whether the notice of the new name is due
     * ([SettingsStore.decideRenameNotice]). Data from an earlier version: the settings file, or anything in the vault's
     * folder (the vault, its attachments, a damaged vault set aside). Blocking file access: call off the main thread.
     */
    suspend fun decideRenameNotice() {
        settings.decideRenameNotice(vaultExists = vaultDir.listFiles()?.isNotEmpty() == true)
    }

    /**
     * Another app took the focus in multi-window. With «Al salir» the vault's backstop counts from now
     * ([VaultStore.leftForAnotherWindow], M1); a repeat while already away changes nothing.
     */
    fun leftForAnotherApp() {
        if (_awayInOtherApp.value) return
        vault.leftForAnotherWindow()
        _awayInOtherApp.value = true
    }

    /** Back on top: the time away is checked first (it may lock, as on onStart), then the flag goes. */
    fun backFromAnotherApp() {
        vault.lockIfIdle()
        _awayInOtherApp.value = false
    }

    // The A11 clock marks, one per vault (2.0, ruling T2, I-1). The ones in force are kept here, in memory, and saved to
    // the settings when it is worth it: a write that fails (a full disk) never closes the app and never lowers the mark
    // the fingerprint is checked against (BOV-1). All guarded by [clockLock].
    private val clockLock = Any()

    /**
     * Per vault, the highest time told by [rememberClock] since that vault's last [resetClock] (or since the app started).
     * 2.0 (ruling T2, I-1): every time seen raises it for every vault; a reset is only its own vault's.
     */
    private val clockSeen = HashMap<String, Long>()

    /** Per vault, a reset not saved yet: its mark restarts there instead of at the saved one. */
    private val clockLowered = HashMap<String, Long>()

    /** Counts the resets, so a write that started before one never clears it. */
    private var clockResets = 0

    private var clockWriting = false

    /** [elapsedRealtime] before which no write is tried again, after one failed. */
    private var clockRetryAt = Long.MIN_VALUE

    /** The vaults of the list: the only ones whose marks are kept and written (a deleted one is never brought back). */
    private fun clockIds(): List<String> = registry.vaults.value.map { it.id }

    /** [clockMark] with [clockLock] held. */
    private fun markOf(id: String): Long =
        maxOf(clockLowered[id] ?: settings.settings.value.vault(id).clockHighWaterMs, clockSeen[id] ?: 0L)

    /**
     * The highest time the app has seen (A11) for the vault [id]: its saved mark, or its reset not saved yet, raised by
     * every time seen since. That vault's fingerprint gate reads it, so a mark that could not be saved still counts.
     * Only that vault's master password lowers it ([resetClock]).
     */
    fun clockMark(id: String): Long = synchronized(clockLock) { markOf(id) }

    /**
     * Keeps the highest time seen (it only rises), for every vault. Called at every unlock and touch and once at start,
     * so it writes only when the time is [CLOCK_WRITE_STEP_MS] past a saved mark: the saved one may lag by that much,
     * well under the 5 minutes of slack, and the one in memory never does.
     */
    fun rememberClock(seen: Long) {
        synchronized(clockLock) {
            for (id in clockIds()) {
                if (seen > (clockSeen[id] ?: 0L)) clockSeen[id] = seen
                if (seen > (clockUsed[id] ?: 0L)) clockUsed[id] = seen
            }
        }
        saveClock()
    }

    /**
     * The start of the app (SeguridadApp): raises the A11 marks as [rememberClock] does, but is no use of any vault, so
     * the trash's clock ([trashClockMark]) does not take it: a clock set forward while the app was closed is seen at the
     * start, and must still look set forward at the first unlock (task 3, focus 3).
     */
    fun appStarted(now: Long) {
        synchronized(clockLock) {
            for (id in clockIds()) if (now > (clockSeen[id] ?: 0L)) clockSeen[id] = now
        }
        saveClock()
    }

    /**
     * Per vault, the highest time seen in this process by a use (an unlock, a touch, a master check; any vault's, as the
     * A11 marks), never by the start of the app ([appStarted]): in memory only.
     */
    private val clockUsed = HashMap<String, Long>()

    /**
     * 2.0, task 3: the mark the trash's clock rule of the vault [id] reads before an unlock ([app.seguridad.vault.purgedTrash],
     * focus 3): the highest time the app saw in use in this process; 0 when none. Not the A11 mark, which the start of the
     * app raises to the time of that start (so a clock set forward while the app was closed would never look so), and
     * which is saved (so a start of an earlier process would count too).
     */
    fun trashClockMark(id: String): Long = synchronized(clockLock) { clockUsed[id] ?: 0L }

    /**
     * After a master password check of the vault [id] its mark restarts at [now], even lower: a clock set forward by
     * mistake heals, for that vault only (ruling T2, I-1: another vault's master never heals the clock of this one's
     * fingerprint).
     */
    private fun resetClock(id: String, now: Long) {
        synchronized(clockLock) {
            clockLowered[id] = now
            clockSeen[id] = now
            clockUsed[id] = now
            clockResets++
        }
        saveClock()
    }

    /** A new vault [id] starts with the highest mark of that moment, of any vault (ruling T2, I-1). */
    private fun startClock(id: String) {
        synchronized(clockLock) {
            val highest = (clockIds() - id).maxOfOrNull { markOf(it) } ?: 0L
            clockLowered.remove(id)
            clockSeen[id] = maxOf(highest, clockSeen[id] ?: 0L)
        }
        saveClock()
    }

    /** A vault that is gone: its mark is not kept. */
    private fun forgetClock(id: String) {
        synchronized(clockLock) {
            clockSeen.remove(id)
            clockLowered.remove(id)
            clockUsed.remove(id)
        }
    }

    /**
     * Writes the vaults' marks to the settings when one is due (a reset, or a rise of [CLOCK_WRITE_STEP_MS]), one write
     * at a time, all of them in that write. A failed write is tried again by a later call, once [CLOCK_WRITE_STEP_MS]
     * have passed; meanwhile the marks in memory are the ones that count.
     */
    private fun saveClock() {
        synchronized(clockLock) {
            val s = settings.settings.value
            val due = clockLowered.isNotEmpty() || clockIds().any { clockMarkWorthWriting(s.vault(it).clockHighWaterMs, clockSeen[it] ?: 0L) }
            if (!due || clockWriting || elapsedRealtime() < clockRetryAt) return
            clockWriting = true
        }
        appScope.launch {
            var resets = -1
            var saved = false
            try {
                settings.update { s ->
                    synchronized(clockLock) {
                        resets = clockResets
                        // For each vault, its reset not saved yet (it may lower the mark), then the highest time seen since
                        // (only raises it).
                        clockIds().fold(s) { all, id ->
                            all.withVault(id) { v -> (clockLowered[id]?.let(v::resettingClock) ?: v).seeingClock(clockSeen[id] ?: 0L) }
                        }
                    }
                }
                saved = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A full disk: the marks stay in memory (see [clockMark]) and are written later. Never closes the app.
            } finally {
                synchronized(clockLock) {
                    clockWriting = false
                    if (saved && resets == clockResets) clockLowered.clear()
                    if (!saved) clockRetryAt = elapsedRealtime() + CLOCK_WRITE_STEP_MS
                }
            }
            if (saved) saveClock() // What was told while this write ran.
        }
    }

    /**
     * The one shared image copy (cacheDir/compartir/<random>/): wiped at start and on return after 10 minutes, never on
     * lock; the permission of its URI is revoked whenever it goes.
     */
    val shareTemp = ShareTemp(cacheDir, System::currentTimeMillis, revokeShare)

    val pwned: PwnedPasswords by lazy { PwnedPasswords(client) }

    /**
     * 2.0, task 2: one vault of the phone, built the first time it is needed and kept while it is on the list: its store
     * (its own file, folder, data key and settings) and its automatic copies, in a scope of its own that a deletion
     * cancels. Every vault is always locked but the active one ([activeVault]): the store's gate refuses to open any other.
     */
    private inner class Slot(val id: String) {
        val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext[Job]))

        /** files/vault/vault.bin for the first vault, files/vaults/<id>/vault.bin for the others; never in backups (allowBackup is off). */
        val store: VaultStore = VaultStore(
            File(registry.dirOf(id), "vault.bin"),
            compute = vaultCompute,
            now = vaultNow,
            kdf = { settings.settings.value.vault(id).kdf.params },
            idleLockMs = { settings.settings.value.lockAfter.ms },
            inForeground = { foreground },
            // The copies' rotation does not know which vault each copy is of: a new vault (the old one damaged and set
            // aside, or deleted) would have it delete the old vault's copies. Turned off; Ajustes says so.
            beforeCreate = { settings.turnOffAutoBackupForNewVault(id).forEach { releaseIfUnused(it) } },
            // Same for an import: the copies stay on, but those made so far (the replaced vault's) are never deleted. Written
            // before the vault is saved, and put back if that fails (COPIAS-3); a copy still being written of the replaced
            // vault is told apart by the vault's generation (CADENAS-3, see [recordAutoBackup]).
            beforeReplace = { settings.forgetAutoBackupCopiesForImport(id) },
            // A11, each vault's own mark: every time seen raises them all, its master password lowers only its own.
            clockHighWater = { clockMark(id) },
            onClockSeen = ::rememberClock,
            onClockReset = { now -> resetClock(id, now) },
            // 2.0, task 3: the trash's clock, which the start of the app does not raise.
            trashClockMark = { trashClockMark(id) },
            elapsed = elapsedRealtime,
            awayInMultiWindow = { _awayInOtherApp.value },
            // 2.0: the copy before the vault passes to v4, in this vault's automatic copies' folder; none (no folder, or it
            // failed) leaves the choice to the user (the upgrade dialog, Abogado Digital O-3).
            beforeUpgrade = { autoBackup.copyBeforeUpgrade() },
            id = id,
            active = { activeId == id },
        )

        /**
         * Encrypted copies of this vault to the folders chosen for it in Ajustes (2.0, task 8: up to 3 targets, each with
         * its calendar), a few seconds after each change or when the calendar says so.
         */
        val autoBackup: AutoBackup = AutoBackup(
            store,
            targets = { settings.settings.value.vault(id).targets },
            open = backupFolder,
            scope,
            now = System::currentTimeMillis,
            onResult = { recordAutoBackup(id, store, it) },
            onImportCopy = { folder, copy, generation -> recordImportCopy(id, store, folder, copy, generation) },
        )

        /** 2.0, task 3: «Deshacer» after a move to this vault's trash; a lock of this vault (a switch locks it) forgets it. */
        val undoTrash = UndoTrash(store, elapsedRealtime, scope)
    }

    private val slots = HashMap<String, Slot>()

    /** The vault [id] of the list ([IllegalArgumentException] for any other), built once. */
    private fun slot(id: String): Slot = synchronized(slots) {
        slots[id] ?: run {
            require(registry.vaults.value.any { it.id == id }) { "Unknown vault" }
            Slot(id).also { slots[id] = it }
        }
    }

    /** The active vault's id. Written by [switchTo] before the one it leaves is locked, so that one's gate is shut first. */
    @Volatile private var activeId: String = registry.lastOpened

    private val _activeVault = MutableStateFlow(slot(activeId).store)

    /**
     * 2.0, task 2: the vault the app shows, the only one that can be open. A screen, a ViewModel or anything else that
     * keeps a store follows this and is built again for the new one ([switchTo]): a store kept from before only ever reads
     * or writes its own vault, which is locked by then, so nothing ends up in the other one.
     */
    val activeVault: StateFlow<VaultStore> = _activeVault.asStateFlow()

    /** [activeVault] now. Read it again after a [switchTo]. */
    val vault: VaultStore get() = activeVault.value

    /** The id of [vault]. */
    val activeVaultId: String get() = activeId

    /** The automatic copies of [vault]. */
    val autoBackup: AutoBackup get() = slot(_activeVault.value.id).autoBackup // Review M-3: always [vault]'s.

    /**
     * 2.0, task 8: the app came to the foreground (MainActivity.onStart): the active vault's copies that are due by their
     * calendar ([AutoBackup.runDue]), off the main thread; with the vault locked they wait for its unlock, which runs
     * them too. No background work: if the app is not opened, there is no copy (Ajustes says so).
     */
    fun appOpened() {
        val backup = autoBackup
        appScope.launch { backup.runDue(System.currentTimeMillis()) }
    }

    /** 2.0, task 3: «Deshacer» of the active vault's trash; another vault's (after a switch) has nothing to undo. */
    val undoTrash: UndoTrash get() = slot(_activeVault.value.id).undoTrash

    /** The state of whichever vault is active: what app-wide clean-ups follow (a lock, a switch). */
    val vaultState: Flow<VaultState> = activeVault.flatMapLatest { it.state }

    // [lockEpoch] for the whole app: it goes up with every lock of the active vault and with every switch, and never comes
    // back to a value it had, so something kept for one vault (the A14 copy waiting) is never accepted in another.
    private val epochLock = Any()
    private var epochBase = 0
    private var epochFrom = 0

    /** Goes up with every lock of the active vault and every switch ([VaultStore.lockEpoch] for the whole app). */
    val lockEpoch: Int get() = synchronized(epochLock) { epochBase + (vault.lockEpoch - epochFrom) }

    private val switching = Mutex()

    /** One create, import as a new vault or delete at a time (each may switch twice). */
    private val changingVaults = Mutex()

    /**
     * 2.0, task 2: makes [id] the active vault. The one open is locked first ([LockReason.MANUAL], with everything a lock
     * does: «PIN y códigos», the copy waiting for the A14 notice, the screens with secrets), whatever it was doing ends
     * (its mutex: a save already started lands in it, never in the other), and then [id] is shown, locked. The dialog
     * after a change of master password goes too (it is of the vault left). [IllegalArgumentException] for an id not in
     * the list. The same vault again does nothing.
     */
    suspend fun switchTo(id: String) = switching.withLock {
        require(registry.vaults.value.any { it.id == id }) { "Unknown vault" }
        if (id == activeId) return@withLock
        val old = slot(activeId)
        val next = slot(id)
        withContext(NonCancellable) {
            activeId = id // Shuts the old one's gate before it is locked: an unlock finishing now stays locked.
            old.store.lock(LockReason.MANUAL)
            clipboardNotice.drop()
            masterChanged.close()
            old.store.awaitIdle()
            next.store.load()
            synchronized(epochLock) {
                epochBase += old.store.lockEpoch - epochFrom + 1
                epochFrom = next.store.lockEpoch
                _activeVault.value = next.store
            }
            try {
                registry.setLastOpened(id)
            } catch (e: Exception) {
                // A full disk: the app starts on another vault next time; never blocks the switch.
            }
        }
    }

    /** The colour for a new vault: the first no vault has, or one by turns. */
    private fun nextColor(): TagColor {
        val used = registry.vaults.value.map { it.color }.toSet()
        return TagColor.entries.firstOrNull { it !in used } ?: TagColor.entries[registry.vaults.value.size % TagColor.entries.size]
    }

    /**
     * 2.0, task 2: a new vault with [master] (the caller checks the rule of a new master, MasterPolicy) and [name] (saved
     * inside it, encrypted), born in format v4; it becomes the active one, open. Returns its id. The one open before is
     * locked. [app.seguridad.vault.VaultLimitExceeded] when there are already 10, or the name is too long;
     * [IllegalStateException] unless [canAddVault]. If creating it
     * fails, nothing is left of it and the vault of before is active again, locked.
     */
    suspend fun createVault(name: String, master: CharArray): String = newVault(name, made = { it.create(master, name.trim()) })

    /**
     * 2.0, task 2: «Importar como bóveda nueva»: a new vault from a copy ([VaultStore.importReplace], with its master
     * password), with a new id, also when the same copy was imported before; it becomes the active one, open. [name] is
     * its name if not blank, else it keeps the copy's. Returns its id. The copy is checked and saved in the new vault's
     * folder first, with the vault open before still the active one (review I-2): a wrong password or a damaged copy
     * throws (the exception says why), leaves nothing of the new vault and the vault of before as it was, open or not.
     * Only then is the vault open before locked and the new one opened with [master] (a second key derivation).
     */
    suspend fun importAsNewVault(input: InputStream, master: CharArray, name: String): String = newVault(
        name,
        // Not the active one yet: its gate keeps it locked, so the vault open now stays open while the copy is checked.
        before = { store ->
            store.load()
            store.importReplace(input, master)
        },
        made = { store ->
            store.unlock(master)
            if (name.isNotBlank()) store.rename(name)
        },
    )

    /**
     * Whether another vault can be made now: only from an open one, or when no vault of the phone has a file yet
     * (COV2-1 with several vaults: someone holding the phone for a moment, every vault locked, cannot make one of their own,
     * open, and with it change the settings of the whole app). An empty slot left by «He olvidado la contraseña» can
     * still be made into a vault on its own screen, as before.
     */
    fun canAddVault(): Boolean =
        vault.state.value is VaultState.Unlocked || registry.vaults.value.none { File(registry.dirOf(it.id), "vault.bin").exists() }

    /**
     * A new vault: [before] runs on its store while the vault of now is still the active one (so a failure there changes
     * nothing of it), then the switch, then [made]. Review M-3: both get the new vault's own store, never whichever
     * is active.
     */
    private suspend fun newVault(
        name: String,
        before: suspend (VaultStore) -> Unit = {},
        made: suspend (VaultStore) -> Unit,
    ): String = changingVaults.withLock {
        if (!canAddVault()) throw VaultsRefused(VAULT_NEW_NEEDS_OPEN)
        VaultData(name = name.trim()).checkStructure() // A name too long: nothing is made.
        val previous = activeId
        val info = registry.create(nextColor())
        startClock(info.id) // Ruling T2, I-1: the highest mark of this moment.
        val store = slot(info.id).store
        try {
            before(store)
            switchTo(info.id)
            made(store)
        } catch (e: Throwable) {
            withContext(NonCancellable) { dropVault(info.id, previous) }
            throw e
        }
        info.id
    }

    /** Undoes a new vault that could not be made: [previous] active again, and nothing of [id] left. Best effort. */
    private suspend fun dropVault(id: String, previous: String) {
        try {
            switchTo(previous)
        } catch (e: Exception) {
            // Already gone from the list: the first vault is shown instead (below).
        }
        synchronized(slots) { slots.remove(id) }?.let { it.store.lock(); it.scope.cancel() }
        forgetClock(id)
        try {
            registry.remove(id)
        } catch (e: Exception) {
            // A folder no vault points at: nothing reads it again until the registry is rebuilt.
        }
        try {
            settings.update { it.withoutVault(id) }
        } catch (e: Exception) {
            // Settings of a vault that is gone: never read.
        }
    }

    /**
     * 2.0, task 2 (ruling 3): deletes the vault [id] from this phone. It is opened with [master] (a wrong one throws
     * [app.seguridad.crypto.CryptoFailure], nothing deleted) and so shows its name; [typedName] must be that name as it is
     * shown ([displayName]), compared as the import compares titles ([compareKey]): an invisible character, or spaces at
     * the ends, do not count, the case does. A vault with no file (nothing to protect) needs neither. What happened is the
     * result ([VaultDeletion]); nothing is deleted unless it is [VaultDeletion.DELETED]: not when the name is not its own,
     * the vault locked meanwhile, a file of it is kept for recovery, or it is the only vault (the existing «He olvidado la
     * contraseña» is for that).
     * It switches to [id] first if it was not the active one (locking the one open). Deletes its folder (its file and
     * attachments), its fingerprint key and its settings, and gives back its copies' folder if no other vault uses it; it
     * never touches the copies already made (R-9, O-2: the confirmation says so). Then the first vault left is active,
     * locked.
     */
    suspend fun deleteVault(id: String, master: CharArray, typedName: String): VaultDeletion = changingVaults.withLock {
        require(registry.vaults.value.any { it.id == id }) { "Unknown vault" }
        // Re-review 1, minor 2: its own outcome, never taken for a damaged file.
        if (registry.vaults.value.size <= 1) return@withLock VaultDeletion.LAST_VAULT
        switchTo(id)
        val store = slot(id).store // Review M-3: the vault named, whatever is active.
        store.load()
        // Review M-2: no master nor name only for a slot with no vault file at all; a file set aside or unreadable stays.
        if (store.state.value is VaultState.NoVault && !isEmptySlot(id)) return@withLock VaultDeletion.KEPT_DAMAGED
        if (store.state.value !is VaultState.NoVault) {
            if (store.state.value is VaultState.Unlocked) {
                if (!store.confirmMaster(master)) return@withLock VaultDeletion.LOCKED
            } else {
                store.unlock(master)
            }
            val data = (store.state.value as? VaultState.Unlocked)?.data ?: return@withLock VaultDeletion.LOCKED
            val order = registry.vaults.value.indexOfFirst { it.id == id }
            if (compareKey(typedName) != compareKey(displayName(data, order))) return@withLock VaultDeletion.WRONG_NAME
        }
        withContext(NonCancellable) {
            switchTo(registry.vaults.value.first { it.id != id }.id)
            val gone = synchronized(slots) { slots.remove(id) }
            gone?.scope?.cancel()
            forgetClock(id)
            registry.remove(id)
            try {
                forgetBiometric(id)
            } catch (e: Exception) {
                // A key left in the Keystore wraps the data key of a vault that is gone: it opens nothing.
            }
            val folders = settings.settings.value.vault(id).backupFolders
            try {
                settings.update { it.withoutVault(id) }
            } catch (e: Exception) {
                // Settings of a vault that is gone: never read again (a new vault never gets the same id).
            }
            folders.forEach { releaseIfUnused(it) }
        }
        VaultDeletion.DELETED
    }

    /**
     * Whether the folder of the vault [id] has no vault file at all: no `vault.bin` (also one that could not be read) and
     * nothing set aside as damaged (`vault.bin.bad-…`, `att.bad-…`, BOV-2, kept for recovery). Only such a slot is deleted
     * without its master and its name (review M-2). Reads the disk.
     */
    fun isEmptySlot(id: String): Boolean {
        val names = registry.dirOf(id).list() ?: return true
        return names.none { it == "vault.bin" || it.startsWith("vault.bin.bad-") || it.startsWith("att.bad-") }
    }

    private val vaultsNotices = Notice<String>()

    /**
     * 2.0 (review I-2): what the last action of «Bóvedas» said (created, imported, deleted, or why not), until it is shown
     * ([vaultsNoticeShown]). Here, not in the screen: the action switches vaults, which rebuilds every screen.
     */
    val vaultsNotice: StateFlow<String?> = vaultsNotices.value

    fun vaultsNoticeShown(message: String) = vaultsNotices.shown(message)

    private val _vaultsBusy = MutableStateFlow(false)

    /** An action of «Bóvedas» is running ([runVaultsAction]); its screen waits, also after it was rebuilt. */
    val vaultsBusy: StateFlow<Boolean> = _vaultsBusy.asStateFlow()

    /**
     * Runs one action of «Bóvedas» to its end ([block] gives what to say, or null) and keeps what it says in
     * [vaultsNotice]: a limit or a refusal by its own text, a wrong password or a damaged copy by the store's text, and
     * anything else by [VAULT_FAILED]. Not cut by the caller leaving. One at a time: while another runs ([vaultsBusy]),
     * nothing is done and the result is null.
     */
    suspend fun runVaultsAction(block: suspend () -> String?): String? {
        if (!_vaultsBusy.compareAndSet(false, true)) return null // Another one runs: this one does nothing.
        return runMarked(block)
    }

    /** [runVaultsAction] once [vaultsBusy] is set by the caller; clears it at the end. */
    private suspend fun runMarked(block: suspend () -> String?): String? = withContext(NonCancellable) {
        val message = try {
            block()
        } catch (e: VaultLimitExceeded) {
            e.message
        } catch (e: VaultsRefused) {
            e.message
        } catch (e: CryptoFailure) {
            e.message ?: CryptoFailure().message
        } catch (e: Exception) {
            VAULT_FAILED
        } finally {
            _vaultsBusy.value = false
        }
        message?.let(vaultsNotices::post)
        message
    }

    /** [runVaultsAction] in [appScope], for the screen; one at a time ([vaultsBusy]). */
    fun launchVaultsAction(block: suspend () -> String?) {
        // Re-review 1, minor 1: marked busy here, at once and atomically, so a second tap in the same frame does nothing.
        if (!_vaultsBusy.compareAndSet(false, true)) return
        appScope.launch { runMarked(block) }
    }

    /**
     * Gives back the permission of the copies' folder [uri] (a target was removed or changed, or the vault was deleted),
     * unless a target of any vault still has its copies there.
     */
    fun releaseIfUnused(uri: String) {
        if (settings.settings.value.perVault.values.none { v -> v.targets.any { it.uri == uri } }) releaseFolder(uri)
    }

    /** The vault whose master password changed last ([onMasterChanged]): its dialog replaces its copies, never another's. */
    @Volatile private var masterChangedSlot: Slot? = null

    /**
     * The dialog after a change of master password (A6) and what its replacement of the old copies answered: here, not in
     * a screen, so a rotation during the key derivation or the replacement does not lose it (M-3). 2.0: of the vault whose
     * password changed; a switch closes it.
     */
    val masterChanged = MasterChanged(
        appScope,
        replace = { targets -> (masterChangedSlot ?: slot(activeId)).autoBackup.replaceOldCopies(targets) },
        now = System::currentTimeMillis,
        // 1.4.0: the import copies too, as they are under the password of before (AutoBackup.copyBeforeImport).
        // 2.0, task 8: those of every target; each target only deletes the ids on its own lists.
        oldCopies = { settings.settings.value.vault((masterChangedSlot ?: slot(activeId)).id).listedCopies },
    )

    /** The screen that changed the master password of the active vault says so ([MasterChanged.changed]). */
    fun onMasterChanged(fingerprintFailed: Boolean, hasIdentity: Boolean) {
        masterChangedSlot = slot(activeId)
        masterChanged.changed(fingerprintFailed, hasIdentity)
    }

    /**
     * A copy waiting for the A14 notice (Android 8 to 12), shown by the dialog at the root of the app. Here for the same
     * reason as [masterChanged]; the text is dropped when the vault locks (here) and when the app is left (MainActivity).
     * The drop here comes a moment later and may miss a quick lock and unlock: the lock counter makes accept() refuse
     * either way.
     */
    val clipboardNotice = ClipboardNoticeRequest(lockEpoch = { lockEpoch }).also { notice ->
        appScope.launch {
            vaultState.map { it is VaultState.Unlocked }.distinctUntilChanged().collect { open -> if (!open) notice.drop() }
        }
    }

    /**
     * 2.0, task 6: «Copiar contraseña» offered for 30 s after «Copiar usuario» ([TwoStepCopy]): only the entry's id. A
     * lock or a switch of vault ends it ([lockEpoch]), and it is also cleared here, as [clipboardNotice] is.
     */
    val twoStepCopy = TwoStepCopy(elapsedRealtime, lockEpoch = { lockEpoch }).also { offer ->
        appScope.launch {
            vaultState.map { it is VaultState.Unlocked }.distinctUntilChanged().collect { open -> if (!open) offer.clear() }
        }
    }

    init {
        // 1.4.2: the picker places kept only in memory go at every lock and unlock (PickerPlace.kept). Here, after
        // [vault] exists. Like [clipboardNotice], a lock and unlock quicker than this collector may be seen as none.
        appScope.launch {
            vaultState.map { it is VaultState.Unlocked }.distinctUntilChanged().drop(1).collect { pickerPlaces.forgetSession() }
        }
    }

    /**
     * Keeps how the last automatic copy of the vault [id] went, for Ajustes, and the copies it made (for the next
     * rotation), if it went to the folder still chosen. Waited for by [AutoBackup] before its next copy, so that one sees
     * this list. The vault's generation is read inside the settings' lock: an import bumps it before it forgets the list
     * (a write that takes that same lock), so a copy of the replaced vault can never put its list back, whichever of the
     * two writes first (CADENAS-3). Only the settings of [id] change.
     */
    internal suspend fun recordAutoBackup(id: String, store: VaultStore, result: AutoBackupResult) {
        try {
            // Review M-5: a late result of a vault deleted meanwhile is dropped, so its settings never come back.
            settings.update { s -> if (!listed(id)) s else s.withVault(id) { v -> v.withAutoBackupResult(result, store.generation) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A full disk: Ajustes just shows the previous state, and the copy just made is not rotated; never closes the app.
        }
    }

    /**
     * Lists the import copy [copy] just written to [folder] (VaultSettings.importCopies of [id]), for A6. As
     * [recordAutoBackup], the vault's generation is read inside the settings' lock, so a copy of a vault replaced
     * meanwhile is never listed.
     */
    internal suspend fun recordImportCopy(id: String, store: VaultStore, folder: String, copy: String, generation: Long) {
        try {
            settings.update { s -> if (!listed(id)) s else s.withVault(id) { v -> v.withImportCopy(folder, copy, generation, store.generation) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A full disk: the copy is there, only not listed (A6 counts it as unlisted by its name); never closes the app.
        }
    }

    /** Whether [id] is still a vault of the list (a result that arrives after its deletion is not). */
    private fun listed(id: String): Boolean = registry.vaults.value.any { it.id == id }
}

/** A vault action refused by a rule of the app (not a failure): [message] is a fixed Spanish text that says why. */
class VaultsRefused(message: String) : IllegalStateException(message)
