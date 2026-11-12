package app.seguridad.vault

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 2.0: the upgrade of an open vault from version 2 or 3 to version 4 while it waits in memory (design «Formato v4»,
 * Abogado Digital O-3), taken out of [VaultStore] (review M-8 of task 1). It holds the version-4 [file] that waits to be
 * saved, whether the user is being asked ([asking]) and what to tell once it was saved by itself ([done]); the store
 * decides when the vault opens, locks and saves, and keeps every rule about its mutex and its epoch.
 *
 * Every change but [done] is made with [opening] held, the store's lock between [VaultStore.lock] and the last step of an
 * opening, so a lock can never land between checking that an upgrade waits and asking about it.
 */
internal class PendingUpgrade(private val opening: Any) {
    /**
     * The version-4 file that waits to be saved, under the open data key; only set with the vault open. Read under the
     * store's mutex (to save it, or to refuse a save) and under [opening].
     */
    @Volatile var file: VaultFile? = null
        private set

    private val _asking = MutableStateFlow(false)

    /** [VaultStore.pendingUpgrade]: the user has to choose (only once there is a choice to make, review I-1). */
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    /** [VaultStore.upgradeAutoCopyFailed]: there was a folder and its copy before the upgrade was not made (M-5). */
    @Volatile var autoCopyFailed: Boolean = false
        private set

    private val _done = MutableStateFlow<String?>(null)

    /** [VaultStore.upgradeDone]: the name of the copy made before an upgrade saved with no question asked (O-3). */
    val done: StateFlow<String?> = _done.asStateFlow()

    /** With [opening] held: the vault opened, and [next] waits to be saved (null: it opened with nothing waiting). */
    fun openedWith(next: VaultFile?) {
        file = next
        _asking.value = false
        autoCopyFailed = false
    }

    /** With [opening] held: a lock, or an opening cut short, drops what waited; the file on disk stays the 2 or 3. */
    fun drop() = openedWith(null)

    /** With [opening] held, once [file] is saved: from here the vault is a 4. */
    fun saved() {
        file = null
        _asking.value = false
    }

    /** The notice of [done] was shown, or the vault is gone. */
    fun doneShown() {
        _done.value = null
    }

    /**
     * Right after an unlock with the master password that left [file] waiting, with the store's mutex free (so the copy
     * can export the file still on disk): the copy before the upgrade ([copyBefore], AutoBackup.copyBeforeUpgrade; what
     * it throws counts as a failed copy). When it is made and [save] saves the upgrade, its name is told once ([done]).
     * Otherwise the user is asked ([asking]) unless the vault locked meanwhile ([stillOpen] false, or nothing waits any
     * more), saying whether a folder's copy failed. Not cancelled with the caller.
     */
    suspend fun afterUnlock(copyBefore: suspend () -> CopyResult?, save: suspend () -> Boolean, stillOpen: () -> Boolean) =
        withContext(NonCancellable) {
            val copy = try {
                copyBefore()
            } catch (e: Exception) {
                CopyResult.Failed(AUTO_BACKUP_FAILED) // No copy: the user chooses.
            }
            if (copy is CopyResult.Saved && save()) {
                _done.value = copy.where // O-3 with a folder: told once.
                return@withContext
            }
            synchronized(opening) {
                if (file != null && stillOpen()) {
                    // A folder whose copy was not made (M-5); a copy made whose save then failed is not that.
                    autoCopyFailed = copy != null && copy !is CopyResult.Saved
                    _asking.value = true
                }
            }
        }
}
