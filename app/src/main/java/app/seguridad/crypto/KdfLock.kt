package app.seguridad.crypto

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * One key derivation of large memory at a time in the whole process (ruling R38, branch review B of 1.4.0, M-4).
 * [KdfParams.checkSane] lets one Argon2id take up to 128 MiB when it fits in three quarters of the heap: it is sized for
 * one derivation. Two at once (a «Comprobar una copia» stopped during its Argon2id, which cannot be cut, and the unlock
 * with the master right after it) could run out of heap and say [KDF_OUT_OF_MEMORY], which would not be true: the phone
 * has the memory for one, and a retry works.
 *
 * Who takes it:
 * - every Argon2id of [app.seguridad.vault.VaultStore] (create, unlock, the master's confirmation, the rewrap after it,
 *   the change of the master and «Importar copia»), through its `derive`;
 * - the Argon2id of «Comprobar una copia» ([app.seguridad.vault.checkBackup]);
 * - «Importar de otra app» when it reads a file with a password: Argon2id and scrypt are memory-hard; PBKDF2 (Bitwarden,
 *   2FAS) needs a few hundred bytes, but it runs inside the same read, so it waits too;
 * - «Cifrar texto» (its Argon2id with a password, and the encryption of the text, which is short).
 *
 * Not taken by «Cifrar archivo»: its Argon2id runs inside the encryption of the stream, so holding the lock there would
 * make an unlock wait for a whole file (the derivation would have to be split from the stream first). Nor by
 * SLIP-0039's PBKDF2, which needs a few hundred bytes.
 *
 * Lock order, so nothing can deadlock: the vault's mutex, then this one ([app.seguridad.vault.VaultStore] holds its
 * mutex while it derives); «Comprobar una copia», «Importar de otra app» and «Cifrar texto» take only this one, and
 * their blocks never call the vault. A [derive] block is plain computation: it never suspends, never calls the vault, the settings,
 * `KeyOpsQueue` (the fingerprint's queue, which takes neither lock) or another [derive], since the lock is not
 * reentrant. «Importar de otra app» takes its own one-attempt ticket (`IMPORT_KDF_RUN`, which never waits) before this
 * lock. Waiting here suspends (no thread is blocked, the main thread neither) and [derive] runs its block on the
 * context it is given, `Dispatchers.Default` in the app, never on the main thread.
 *
 * [Mutex] is fair: callers get it in the order they asked.
 */
object KdfLock {
    private val mutex = Mutex()

    /** Whether a derivation holds the lock now (for the tests). */
    val isLocked: Boolean get() = mutex.isLocked

    /**
     * Runs [block] on [context] holding the lock. A caller cancelled while it waits never runs [block]. Once [block]
     * starts it runs to its end, the lock held until then even if the caller is cancelled meanwhile (Bouncy Castle's
     * derivations cannot be interrupted), and what it made is returned to the caller all the same, so a key it derives
     * always reaches the code that wipes it (BOV-3).
     */
    suspend fun <T> derive(context: CoroutineContext, block: () -> T): T =
        mutex.withLock { withContext(NonCancellable) { withContext(context) { block() } } }
}
