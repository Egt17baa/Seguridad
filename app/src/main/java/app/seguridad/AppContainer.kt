package app.seguridad

import app.seguridad.net.PwnedPasswords
import app.seguridad.net.secureClient
import app.seguridad.settings.CLOCK_WRITE_STEP_MS
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.clockMarkWorthWriting
import app.seguridad.settings.resettingClock
import app.seguridad.settings.seeingClock
import app.seguridad.settings.currentAutoBackupCopies
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.tools.ClipboardNoticeRequest
import app.seguridad.tools.share.ShareTemp
import app.seguridad.vault.AutoBackup
import app.seguridad.vault.AutoBackupResult
import app.seguridad.vault.BackupTarget
import app.seguridad.vault.MasterChanged
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

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
class AppContainer(
    filesDir: File,
    /** The app's private cache; holds the copy handed to another app by «Compartir» (see [shareTemp]). */
    cacheDir: File,
    /** The installed version (BuildConfig.VERSION_NAME), for the User-Agent of the HIBP queries. */
    appVersion: String,
    backupFolder: (String) -> BackupTarget? = { null },
    releaseFolder: (String) -> Unit = {},
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

    /** The only connection of the app (Have I Been Pwned), with the eMudhra keys distrusted; built on first use. */
    val client: OkHttpClient by lazy { secureClient(appVersion) }

    /** Password vault (files/vault/vault.bin); never in backups (allowBackup is off). Lock time and Argon2id cost come from the settings. */
    val vault = VaultStore(
        File(File(filesDir, "vault"), "vault.bin"),
        kdf = { settings.settings.value.kdf.params },
        idleLockMs = { settings.settings.value.lockAfter.ms },
        inForeground = { foreground },
        // The copies' rotation does not know which vault each copy is of: a new vault (the old one damaged and set
        // aside, or deleted) would have it delete the old vault's copies. Turned off; Ajustes says so.
        beforeCreate = { settings.turnOffAutoBackupForNewVault()?.let(releaseFolder) },
        // Same for an import: the copies stay on, but those made so far (the replaced vault's) are never deleted. Written
        // before the vault is saved, and put back if that fails (COPIAS-3); a copy still being written of the replaced
        // vault is told apart by the vault's generation (CADENAS-3, see [recordAutoBackup]).
        beforeReplace = { settings.forgetAutoBackupCopiesForImport() },
        clockHighWater = { clockMark },
        onClockSeen = ::rememberClock,
        onClockReset = ::resetClock,
        elapsed = elapsedRealtime,
        awayInMultiWindow = { _awayInOtherApp.value },
    )

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

    // The A11 clock mark. The one in force is kept here, in memory, and saved to the settings when it is worth it: a
    // write that fails (a full disk) never closes the app and never lowers the mark the fingerprint is checked against
    // (BOV-1). All guarded by [clockLock].
    private val clockLock = Any()

    /** The highest time told by [rememberClock] since the last [resetClock] (or since the app started). */
    private var clockSeen = 0L

    /** A reset not saved yet: the mark restarts there instead of at the saved one. */
    private var clockLowered: Long? = null

    /** Counts the resets, so a write that started before one never clears it. */
    private var clockResets = 0

    private var clockWriting = false

    /** [elapsedRealtime] before which no write is tried again, after one failed. */
    private var clockRetryAt = Long.MIN_VALUE

    /**
     * The highest time the app has seen (A11): the saved mark, or the reset not saved yet, raised by every time seen
     * since. The vault's fingerprint gate reads it, so a mark that could not be saved still counts.
     */
    val clockMark: Long
        get() = synchronized(clockLock) { maxOf(clockLowered ?: settings.settings.value.clockHighWaterMs, clockSeen) }

    /**
     * Keeps the highest time seen (it only rises). Called at every unlock and touch and once at start, so it writes
     * only when the time is [CLOCK_WRITE_STEP_MS] past the saved mark: the saved one may lag by that much, well under
     * the 5 minutes of slack, and the one in memory never does.
     */
    fun rememberClock(seen: Long) {
        synchronized(clockLock) { if (seen > clockSeen) clockSeen = seen }
        saveClock()
    }

    /** After a master password check the mark restarts at [now], even lower: a clock set forward by mistake heals. */
    private fun resetClock(now: Long) {
        synchronized(clockLock) {
            clockLowered = now
            clockSeen = now
            clockResets++
        }
        saveClock()
    }

    /**
     * Writes [clockMark] to the settings when it is due (a reset, or a rise of [CLOCK_WRITE_STEP_MS]), one write at a
     * time. A failed write is tried again by a later call, once [CLOCK_WRITE_STEP_MS] have passed; meanwhile the mark in
     * memory is the one that counts.
     */
    private fun saveClock() {
        synchronized(clockLock) {
            val saved = settings.settings.value.clockHighWaterMs
            val due = clockLowered != null || clockMarkWorthWriting(saved, clockSeen)
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
                        // The reset not saved yet (it may lower the mark), then the highest time seen since (only raises it).
                        (clockLowered?.let(s::resettingClock) ?: s).seeingClock(clockSeen)
                    }
                }
                saved = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A full disk: the mark stays in memory (see [clockMark]) and is written later. Never closes the app.
            } finally {
                synchronized(clockLock) {
                    clockWriting = false
                    if (saved && resets == clockResets) clockLowered = null
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

    /** Encrypted copies to the folder chosen in Ajustes, a few seconds after each change to the vault. */
    val autoBackup = AutoBackup(
        vault,
        folder = { settings.settings.value.autoBackupUri },
        open = backupFolder,
        appScope,
        now = System::currentTimeMillis,
        keep = { settings.settings.value.autoBackupKeep },
        lastCopyAt = { settings.settings.value.lastAutoBackupAt },
        copies = { settings.settings.value.currentAutoBackupCopies },
        replacePending = { settings.settings.value.autoBackupReplacePending },
        onResult = ::recordAutoBackup,
    )

    /**
     * The dialog after a change of master password (A6) and what its replacement of the old copies answered: here, not in
     * a screen, so a rotation during the key derivation or the replacement does not lose it (M-3).
     */
    val masterChanged = MasterChanged(appScope, replace = autoBackup::replaceOldCopies, now = System::currentTimeMillis)

    /**
     * A copy waiting for the A14 notice (Android 8 to 12), shown by the dialog at the root of the app. Here for the same
     * reason as [masterChanged]; the text is dropped when the vault locks (here) and when the app is left (MainActivity).
     * The drop here comes a moment later and may miss a quick lock and unlock: the lock counter makes accept() refuse
     * either way.
     */
    val clipboardNotice = ClipboardNoticeRequest(lockEpoch = { vault.lockEpoch }).also { notice ->
        appScope.launch {
            vault.state.map { it is VaultState.Unlocked }.distinctUntilChanged().collect { open -> if (!open) notice.drop() }
        }
    }

    /**
     * Keeps how the last automatic copy went, for Ajustes, and the copies it made (for the next rotation), if it went
     * to the folder still chosen. Waited for by [AutoBackup] before its next copy, so that one sees this list. The vault's
     * generation is read inside the settings' lock: an import bumps it before it forgets the list (a write that takes that
     * same lock), so a copy of the replaced vault can never put its list back, whichever of the two writes first (CADENAS-3).
     */
    private suspend fun recordAutoBackup(result: AutoBackupResult) {
        try {
            settings.update { it.withAutoBackupResult(result, vault.generation) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A full disk: Ajustes just shows the previous state, and the copy just made is not rotated; never closes the app.
        }
    }
}
