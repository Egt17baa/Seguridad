package app.seguridad.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.seguridad.core.Notice
import app.seguridad.tools.FINGERPRINT_ENABLE_INTERRUPTED
import app.seguridad.tools.FINGERPRINT_NOT_RESPONDING
import app.seguridad.tools.KeyLevel
import app.seguridad.tools.KeyOp
import app.seguridad.tools.KeyOpsQueue
import app.seguridad.tools.PromptErrorKind
import app.seguridad.tools.RetryOutcome
import app.seguridad.tools.SingleRun
import app.seguridad.tools.StrongBoxFailure
import app.seguridad.tools.UnlockFailure
import app.seguridad.tools.firstAttempt
import app.seguridad.tools.handOverKey
import app.seguridad.tools.legacyKeyLevel
import app.seguridad.tools.nextAfterFailure
import app.seguridad.tools.promptErrorKind
import app.seguridad.tools.shouldMarkStrongBoxFailed
import app.seguridad.tools.shouldSayEnableInterrupted
import app.seguridad.tools.unlockBlamesStrongBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

private const val ALIAS = "seguridad_vault_biometric"
private const val PREFS = "vault_biometric"
private const val KEY_IV = "iv"
private const val KEY_BLOB = "wrapped"

// The current key was created with setIsStrongBoxBacked(true): KeyInfo cannot say so before Android 12. Goes with the key.
private const val KEY_IN_STRONGBOX = "key_strongbox"

// About the phone, not the key: they survive disable(). Not secrets.
private const val KEY_STRONGBOX_FAILED = "strongbox_failed"
private const val KEY_STRONGBOX_HINT_SHOWN = "strongbox_hint_shown"

// After a prompt ends, androidx.biometric 1.1.0 calls us back (posted) and then removes its fragment with a transaction
// that runs after our callback: a prompt started from the callback would reuse that fragment and be torn down with it.
// The fallback waits until the fragment is gone, checking every POLL ms for at most WAIT ms.
// This tag is an internal of androidx.biometric 1.1.0 (BiometricPrompt's fragment): recheck it whenever biometric is bumped.
private const val BIOMETRIC_FRAGMENT_TAG = "androidx.biometric.BiometricFragment"
private const val PROMPT_GONE_POLL_MS = 100L
private const val PROMPT_GONE_WAIT_MS = 2_000L

/**
 * Wraps the vault's data key with an AES key that lives in the Android Keystore (StrongBox when the phone has it, else
 * the processor's secure zone) and can only be used right after a strong biometric check. Adding a new fingerprint
 * invalidates it. The wrapped key never leaves the phone and is not part of exported vaults.
 */
object BiometricVault {
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun enabled(context: Context): Boolean = prefs(context).contains(KEY_BLOB)

    /** The phone declares a StrongBox Keymaster/KeyMint (Android 9 or later). */
    fun hasStrongBox(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    /** StrongBox failed on this phone once: enabling goes straight to the processor's secure zone. */
    fun strongBoxFailed(context: Context): Boolean = prefs(context).getBoolean(KEY_STRONGBOX_FAILED, false)

    fun strongBoxHintShown(context: Context): Boolean = prefs(context).getBoolean(KEY_STRONGBOX_HINT_SHOWN, false)

    fun markStrongBoxHintShown(context: Context) = prefs(context).edit { putBoolean(KEY_STRONGBOX_HINT_SHOWN, true) }

    private fun markStrongBoxFailed(context: Context) = prefs(context).edit { putBoolean(KEY_STRONGBOX_FAILED, true) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    /** The key of 1.1 and 1.2.2, plus setIsStrongBoxBacked(true) when [strongBox]; nothing else changes. */
    private fun createKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
                // No setUnlockedDeviceRequired: 1.2.0 added it and some phones then refuse to create the key («Este móvil no
                // permite guardar la clave con huella»), so the fingerprint could not be turned on. The fingerprint is still
                // asked for on every use. Bringing it back needs its own release, its own fallback and a real phone.
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    /** Where [key] is; [KeyLevel.UNKNOWN] if KeyInfo cannot be read. Never throws. */
    private fun levelOf(context: Context, key: SecretKey): KeyLevel = try {
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeyLevel.TEE
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeyLevel.SOFTWARE
                else -> KeyLevel.UNKNOWN // SECURITY_LEVEL_UNKNOWN(_SECURE).
            }
        } else {
            @Suppress("DEPRECATION")
            legacyKeyLevel(info.isInsideSecureHardware, prefs(context).getBoolean(KEY_IN_STRONGBOX, false))
        }
    } catch (e: Exception) {
        KeyLevel.UNKNOWN
    }

    /** Where the fingerprint key is, for Ajustes; [KeyLevel.UNKNOWN] without a key or if it cannot be read. Never throws. */
    fun keyLevel(context: Context): KeyLevel = try {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { levelOf(context, it) } ?: KeyLevel.UNKNOWN
    } catch (e: Exception) {
        KeyLevel.UNKNOWN
    }

    /**
     * Drops the wrapped key and the Keystore key. What is known about StrongBox on this phone stays. It runs in the queue
     * ([KeyOpsQueue]): after the operation running now, if any, and cancelling an enable in flight (which ends as a
     * failure, its keys wiped). It runs to the end even if the caller is cancelled, so it is never skipped: once it
     * returns, [enabled] is false (CRY-A02 turns the fingerprint off before creating or deleting the vault).
     */
    suspend fun disable(context: Context) {
        val app = context.applicationContext
        withContext(NonCancellable) { queue.run(KeyOp.DISABLE) { clear(app) } }
    }

    /** [disable] itself, from an operation that already holds the queue (on its thread). */
    private fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_IV)
            remove(KEY_BLOB)
            remove(KEY_IN_STRONGBOX)
        }
        deleteKey()
    }

    // Every operation on ALIAS, one at a time from its first step to its last (1.4.0); the Keystore calls run on its
    // single thread, the prompts on the main one. Never closed: it lives as long as the process, like this object.
    private val queue = KeyOpsQueue(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    // Where enable and unlock run: the main thread, going to the queue's for the Keystore. Never cancelled as a whole.
    private val scope = MainScope()

    /**
     * Shows the prompt for [cipher]. [onOk] gets the authorised cipher, or null if the CryptoObject came back without it.
     * [onError] gets how to read the error; a user cancel goes to [onCancel] instead.
     */
    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onOk: (Cipher?) -> Unit,
        onError: (PromptErrorKind, String) -> Unit,
        onCancel: () -> Unit = {},
    ): BiometricPrompt {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Bóveda de Clavero")
            .setNegativeButtonText("Usar contraseña maestra")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        return BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOk(result.cryptoObject?.cipher)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (val kind = promptErrorKind(errorCode)) {
                        PromptErrorKind.CANCEL -> onCancel()
                        else -> onError(kind, errString.toString())
                    }
                }
            },
        ).also { it.authenticate(info, BiometricPrompt.CryptoObject(cipher)) }
    }

    /** How a prompt ended. */
    private sealed interface PromptAnswer {
        /** The fingerprint was accepted; [cipher] is null if the CryptoObject came back without it. */
        class Ok(val cipher: Cipher?) : PromptAnswer

        /** An error that is not a user cancel. */
        class Error(val message: String) : PromptAnswer

        /** The user said no. */
        data object Cancel : PromptAnswer
    }

    /**
     * Shows the prompt for [cipher] and waits for its answer, on the main thread; [onShown] gets the prompt. Cancelled
     * (a disable during an enable, the activity going away), a late answer is ignored and the prompt, if its activity is
     * still there, is closed: a fingerprint there would do nothing.
     */
    private suspend fun askFingerprint(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onShown: (BiometricPrompt) -> Unit,
    ): PromptAnswer = suspendCancellableCoroutine { answer ->
        val reply: (PromptAnswer) -> Unit = { if (answer.isActive) answer.resume(it) }
        val shown = prompt(activity, title, cipher, onOk = { reply(PromptAnswer.Ok(it)) }, onError = { _, message ->
            reply(PromptAnswer.Error(message))
        }, onCancel = { reply(PromptAnswer.Cancel) })
        onShown(shown)
        answer.invokeOnCancellation {
            ContextCompat.getMainExecutor(activity).execute {
                if (!activity.isDestroyed) {
                    try {
                        shown.cancelAuthentication()
                    } catch (e: Exception) {
                        // Nothing to do: the operation already ended, and nothing is stored from this prompt.
                    }
                }
            }
        }
    }

    /** A prompt asked for now would never show nor call back. */
    private fun canPrompt(activity: FragmentActivity): Boolean =
        !activity.isFinishing && !activity.isDestroyed && !activity.supportFragmentManager.isStateSaved

    private val interruption = Notice<String>()

    /**
     * M-4: an enable cut short because the activity was re-created (a turn of the phone, a theme change) while it ran. The
     * screen that asked is gone with that activity, so the message waits here for the new one ([VaultScreen] shows it,
     * then [interruptionShown]). Posted only once that enable's operation has ended with nothing stored
     * ([shouldSayEnableInterrupted]): a key whose doFinal and prefs write still finished after the re-creation is on, and
     * then nothing is said. So when it is said, the switch is off and «Activar huella» can be tapped again.
     */
    val interrupted: StateFlow<String?> = interruption.value

    fun interruptionShown(message: String) = interruption.shown(message)

    // One enable at a time: a second «Activar huella» while one is running is ignored. Freed by [ended], once the
    // enable's operation has left the queue (its key generation, if one was running, has returned and its key is gone).
    private val enabling = AtomicBoolean(false)

    /**
     * One run of [enable]: both copies of the data key, and what is known about it. [job], [observer], [prompt] and [gone]
     * belong to the main thread; the rest is written by the operation, one step after another, and read once it ended.
     */
    private class EnableFlow(val activity: FragmentActivity, val keys: List<ByteArray>, val onDone: (String?) -> Unit) {
        var job: Job? = null
        var observer: LifecycleEventObserver? = null

        /** The prompt on screen, to close it when the activity is re-created (it would stay, tied to the old one). */
        var prompt: BiometricPrompt? = null

        /** The activity went away: nothing is said to it. */
        var gone = false

        /** It was re-created before the outcome was known (M-4): [ended] may tell the new one ([shouldSayEnableInterrupted]). */
        var recreated = false

        var strongBoxFailure: StrongBoxFailure? = null
        var marked = false

        // How it ended, set once by [decide] as soon as it is known. [done] is read by the observer (main thread): a
        // re-creation after it is not an interruption.
        @Volatile var done = false

        @Volatile var stored = false

        @Volatile var message: String? = null

        @Volatile var notify = false

        fun decide(stored: Boolean, message: String?, notify: Boolean = true) {
            if (done) return
            this.stored = stored
            this.message = message
            this.notify = notify
            done = true
        }
    }

    /**
     * Asks for the fingerprint, then stores [dataKey] wrapped; [dataKey] is wiped either way. With StrongBox, a failure
     * at any step (key, init, prompt error, doFinal) deletes the key and runs the whole cycle once more in the processor's
     * secure zone: the fingerprint may be asked for twice. «StrongBox falló» is recorded only on evidence
     * ([shouldMarkStrongBoxFailed]). A user cancel stops, with no retry and no message. [onDone] gets null when enabled,
     * a message when not; it is not called on cancel, nor when the activity goes away. When the activity is re-created
     * meanwhile (M-4), the flow ends as well (its prompt belongs to the old activity), and [interrupted] says so instead,
     * for the new activity, if nothing was stored. The whole of it, both cycles included, is one [KeyOp.ENABLE] in the queue: a [disable]
     * meanwhile cancels it, and it ends as a failure that marks nothing new and says nothing. Call it on the main thread.
     */
    fun enable(activity: FragmentActivity, dataKey: ByteArray, onDone: (String?) -> Unit) {
        if (!enabling.compareAndSet(false, true)) {
            dataKey.fill(0) // Another enable is running: this one is ignored, and its copy must not linger.
            return
        }
        val first = firstAttempt(hasStrongBox(activity), Build.VERSION.SDK_INT, strongBoxFailed(activity))
        // The fallback's own copy, taken before the first attempt can wipe [dataKey]; the operation wipes both.
        val spare = if (nextAfterFailure(first, cancelled = false) != null) dataKey.copyOf() else null
        val flow = EnableFlow(activity, listOfNotNull(dataKey, spare), onDone)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                // Never a silent cancel on a re-creation: the new activity tells the user to tap again. Only recorded here:
                // doFinal and the prefs write, on the queue's thread, may still finish, so [ended] decides once the
                // operation is over whether the notice is true (review I1).
                val interrupted = !flow.done && activity.isChangingConfigurations
                flow.recreated = interrupted
                // Ends the operation, which wipes both keys and drops an unused key once its current Keystore step has
                // returned, then frees the next enable ([ended]). Nothing is said to this activity.
                flow.gone = true
                flow.job?.cancel()
                // The old prompt is of no use now (the flow is done, a fingerprint there does nothing): closed. Any
                // failure leaves it as it was, which is still closed for the vault.
                if (interrupted) {
                    try {
                        flow.prompt?.cancelAuthentication()
                    } catch (e: Exception) {
                        // Nothing to do: the flow ends, and [ended] decides what the new activity is told.
                    }
                }
                flow.prompt = null
            }
        }
        flow.observer = observer
        activity.lifecycle.addObserver(observer)
        // Undispatched: the operation is in the queue before this returns, so a disable asked right after cancels it.
        flow.job = scope.launch(start = CoroutineStart.UNDISPATCHED) { runEnable(flow, first, dataKey, spare) }
    }

    /** The coroutine of [enable]: its operation in the queue, then [ended] on the main thread, whatever happened. */
    private suspend fun runEnable(flow: EnableFlow, first: KeyLevel, dataKey: ByteArray, spare: ByteArray?) {
        try {
            queue.run(KeyOp.ENABLE) {
                try {
                    // A wrapped key left behind would point at the Keystore key replaced below.
                    clear(flow.activity)
                    enableSteps(flow, first, dataKey, spare)
                } finally {
                    // Still in the queue, also when cancelled: no other operation runs before the key made here is gone.
                    flow.keys.forEach { it.fill(0) }
                    if (!flow.stored) deleteKey()
                }
            }
        } catch (e: CancellationException) {
            // A disable, or the activity going away: a failure that says nothing ([decide] was not called).
        } catch (e: Exception) {
            flow.decide(stored = false, "No se pudo activar la huella")
        } finally {
            ended(flow)
        }
    }

    /** Both cycles of [enable], in its operation; every way out goes through [EnableFlow.decide] or a cancel. */
    private suspend fun enableSteps(flow: EnableFlow, first: KeyLevel, dataKey: ByteArray, spare: ByteArray?) {
        var level = first
        var key = dataKey
        var fallback = spare // The fallback's copy, used once.
        while (true) {
            val failed = attempt(flow, level, key) ?: return
            // This attempt did not store the key ([Failed.failure] null: the user cancelled). Falls back once, or ends.
            key.fill(0)
            deleteKey()
            val next = nextAfterFailure(level, cancelled = failed.failure == null)
            val retry = fallback
            if (next == null || retry == null || failed.failure == null) {
                return flow.decide(stored = false, failed.message, notify = failed.failure != null) // Cancelled: nothing to say, as before.
            }
            flow.strongBoxFailure = failed.failure
            note(flow, RetryOutcome.PENDING) // A Keystore refusal is recorded now.
            // After a prompt (not a KEY_SETUP: no prompt was shown), wait until its fragment is gone.
            if (failed.failure != StrongBoxFailure.KEY_SETUP && !promptGone(flow.activity)) {
                return flow.decide(stored = false, "No se pudo activar la huella")
            }
            level = next
            key = retry
            fallback = null
        }
    }

    /** Why an attempt did not store the key: [failure] null when the user cancelled. */
    private class Failed(val failure: StrongBoxFailure?, val message: String)

    /**
     * One cycle, in the enable's operation: key and init (on the queue's thread), prompt (main thread), doFinal and the
     * prefs (queue's thread). Null once [flow] is decided (stored, or stopped with a message); else why it failed.
     */
    private suspend fun attempt(flow: EnableFlow, level: KeyLevel, key: ByteArray): Failed? {
        val activity = flow.activity
        if (!withContext(Dispatchers.Main) { canPrompt(activity) }) {
            flow.decide(stored = false, "No se pudo activar la huella")
            return null
        }
        val inStrongBox = level == KeyLevel.STRONGBOX
        // StrongBox key generation can take hundreds of ms: here, on the queue's thread, never on the main one.
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, createKey(inStrongBox)) }
        } catch (e: Exception) {
            return Failed(StrongBoxFailure.KEY_SETUP, "Este móvil no permite guardar la clave con huella")
        }
        val answer = withContext(Dispatchers.Main) {
            // Back on the main thread: the app may have gone to the background meanwhile.
            if (canPrompt(activity)) askFingerprint(activity, "Activar desbloqueo con huella", cipher) { flow.prompt = it } else null
        }
        if (answer == null) {
            flow.decide(stored = false, "No se pudo activar la huella")
            return null
        }
        return when (answer) {
            // Every error that is not a cancel gets the one retry; whether StrongBox is blamed is decided later.
            is PromptAnswer.Error -> Failed(StrongBoxFailure.AUTH_OR_FINAL, answer.message)
            PromptAnswer.Cancel -> Failed(null, "")
            is PromptAnswer.Ok -> {
                val c = answer.cipher ?: return Failed(StrongBoxFailure.AUTH_OR_FINAL, "No se pudo usar la huella")
                // A cancel that has already arrived (a disable, the activity going) stores nothing.
                currentCoroutineContext().ensureActive()
                val stored = try {
                    val wrapped = c.doFinal(key)
                    prefs(activity).edit {
                        putString(KEY_IV, Base64.getEncoder().encodeToString(c.iv))
                        putString(KEY_BLOB, Base64.getEncoder().encodeToString(wrapped))
                        if (inStrongBox) putBoolean(KEY_IN_STRONGBOX, true) else remove(KEY_IN_STRONGBOX)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
                if (stored) {
                    flow.decide(stored = true, null)
                    null
                } else {
                    Failed(StrongBoxFailure.AUTH_OR_FINAL, "No se pudo activar la huella")
                }
            }
        }
    }

    /**
     * Waits, on the main thread, until the prompt's own fragment is gone (see [BIOMETRIC_FRAGMENT_TAG]); false after
     * [PROMPT_GONE_WAIT_MS]. Dispatched: the prompt's own removal, already queued, runs first.
     */
    private suspend fun promptGone(activity: FragmentActivity): Boolean = withContext(Dispatchers.Main) {
        val start = SystemClock.uptimeMillis()
        while (activity.supportFragmentManager.findFragmentByTag(BIOMETRIC_FRAGMENT_TAG) != null) {
            if (SystemClock.uptimeMillis() - start >= PROMPT_GONE_WAIT_MS) return@withContext false
            delay(PROMPT_GONE_POLL_MS)
        }
        true
    }

    /** Records «StrongBox falló» if [shouldMarkStrongBoxFailed] says so for what is known now. */
    private fun note(flow: EnableFlow, retry: RetryOutcome) {
        val failure = flow.strongBoxFailure ?: return
        if (!flow.marked && shouldMarkStrongBoxFailed(failure, retry)) {
            markStrongBoxFailed(flow.activity) // From now on, enabling goes straight to the processor's secure zone.
            flow.marked = true
        }
    }

    /**
     * Every exit of [enable], once, on the main thread, after its operation left the queue (or was cancelled before its
     * turn): posts the M-4 notice if it is true, wipes both copies (again), lets the next enable start, then reports.
     */
    private fun ended(flow: EnableFlow) {
        flow.done = true // Cancelled before it was decided: a failure, nothing to say.
        // M-4: [EnableFlow.stored] is final now; the notice says nothing was stored, so never after a stored key.
        if (shouldSayEnableInterrupted(flow.recreated, flow.stored)) interruption.post(FINGERPRINT_ENABLE_INTERRUPTED)
        note(flow, if (flow.stored) RetryOutcome.STORED else RetryOutcome.FAILED)
        flow.keys.forEach { it.fill(0) }
        enabling.set(false)
        flow.observer?.let { flow.activity.lifecycle.removeObserver(it) }
        flow.observer = null
        flow.prompt = null
        if (flow.notify && !flow.gone) flow.onDone(flow.message)
    }

    // One unlock at a time (CU-B revisión 1): the automatic start and a tap on «Usar huella» must not both run. The
    // queue would put the second one after the first; the guard ignores it, as before, so it never asks twice.
    private val unlocking = SingleRun()

    /**
     * Asks for the fingerprint and hands back the data key; [onError] with a message otherwise. Ignored while another
     * unlock runs. [onKey] and [onError] are called on the main thread, once the operation has left the queue (so they
     * may call [disable]). The guard is freed on every end: [onKey], [onError], a cancel, a prompt that cannot be shown,
     * any exception, and, at once, the activity going away (a prompt that never calls back, a rotation during
     * Cipher.init). That also cancels the operation, which ends once its current Keystore step has returned; the new
     * activity's automatic start is not refused, and waits in the queue behind it, so a second Cipher.init on StrongBox
     * never runs alongside the first. A [disable] waits for an unlock; it cancels nothing.
     */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val ticket = unlocking.start() ?: return
        var observer: LifecycleEventObserver? = null
        // Undispatched: it runs until its operation waits in the queue, so the observer is added before it can end.
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var key: ByteArray? = null // Released by the fingerprint: always handed over or wiped, never dropped.
            var message: String? = null
            try {
                queue.run(KeyOp.UNLOCK) { unlockSteps(activity, onKey = { key = it }, onError = { message = it }) }
            } catch (e: CancellationException) {
                // The activity went away: nothing to say (a key already released is still handed over, below).
            } catch (e: Exception) {
                if (key == null && message == null) message = "No se pudo usar la huella"
            } catch (e: Throwable) {
                // An Error (OutOfMemoryError in the Keystore, say): the guard is released (finally), so a later «Usar
                // huella» is not refused for good, and the Error goes on as before.
                key?.fill(0)
                throw e
            } finally {
                ticket.release()
                observer?.let { activity.lifecycle.removeObserver(it) }
                observer = null
            }
            val released = key
            if (released != null) {
                // onKey owns the key once it returns; if it throws (an Error too), the key is zeroed first.
                handOverKey(released, onKey) { onError("No se pudo desbloquear con la huella") }
            } else {
                message?.let(onError)
            }
        }
        if (job.isActive) {
            observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    // Freed at once (review M1): the new activity's automatic start is not refused while this one
                    // unwinds. It waits in the queue behind it, so two Cipher.init on StrongBox never run together.
                    ticket.release()
                    job.cancel()
                }
            }.also { activity.lifecycle.addObserver(it) }
        }
    }

    /**
     * [unlock] itself, in its operation (queue's thread, the prompt on the main one). Calls [onKey] or [onError] at most
     * once, as its last step; neither when it ends with nothing to say (a cancel, no prompt possible).
     */
    private suspend fun unlockSteps(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        // Read in the operation: the state the last one left.
        val p = prefs(activity)
        val iv = p.getString(KEY_IV, null)
        val blob = p.getString(KEY_BLOB, null)
        if (iv == null || blob == null) return onError("La huella no está activada")
        val decoder = Base64.getDecoder()
        val ivBytes = runCatching { decoder.decode(iv) }.getOrNull() ?: return onError("No se pudo usar la huella")
        val wrapped = runCatching { decoder.decode(blob) }.getOrNull() ?: return onError("No se pudo usar la huella")
        var key: SecretKey? = null

        // Created in StrongBox (recorded when enabling) or, if that record is missing, as KeyInfo reports it.
        fun strongBoxKey() = p.getBoolean(KEY_IN_STRONGBOX, false) || key?.let { levelOf(activity, it) } == KeyLevel.STRONGBOX

        // A StrongBox key failing where the key is used ([unlockBlamesStrongBox]): recorded and turned off, so
        // re-enabling (with the master password) goes to the processor's secure zone. Anything else: [message], as in 1.2.2.
        fun failedAt(failure: UnlockFailure, message: String) {
            if (unlockBlamesStrongBox(failure) && strongBoxKey()) {
                markStrongBoxFailed(activity)
                clear(activity)
                onError(FINGERPRINT_NOT_RESPONDING)
            } else {
                onError(message)
            }
        }

        // M-5: loading the Keystore and Cipher.init (a begin on the StrongBox chip, tens to hundreds of ms on some phones)
        // run off the main thread. The CryptoObject contract is unchanged: the cipher is initialised before authenticate.
        val cipher = try {
            val k = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            key = k
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, ivBytes)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear(activity)
            return onError("Has cambiado las huellas del móvil: entra con la contraseña maestra y vuelve a activarla")
        } catch (e: Exception) {
            // Broad on purpose: any failure to start a StrongBox key leads to the TEE on re-enable, the safe direction.
            return failedAt(UnlockFailure.INIT, "No se pudo usar la huella")
        }
        val answer = withContext(Dispatchers.Main) {
            // Back on the main thread: the activity may have gone meanwhile; a prompt asked for now would never show.
            if (canPrompt(activity)) askFingerprint(activity, "Desbloquear la bóveda", cipher) {} else null
        } ?: return
        when (answer) {
            PromptAnswer.Cancel -> return
            // The prompt never uses the key: sensor faults, timeouts and lockouts are not StrongBox's (1.2.2 behaviour).
            is PromptAnswer.Error -> failedAt(UnlockFailure.PROMPT_ERROR, answer.message)
            is PromptAnswer.Ok -> {
                val c = answer.cipher ?: return failedAt(UnlockFailure.NO_CRYPTO_OBJECT, "No se pudo usar la huella")
                // A StrongBox that rejects the fingerprint's auth token fails here, not at init.
                val dataKey = try {
                    c.doFinal(wrapped)
                } catch (e: Exception) {
                    return failedAt(UnlockFailure.DO_FINAL, "No se pudo desbloquear con la huella")
                }
                onKey(dataKey)
            }
        }
    }
}
