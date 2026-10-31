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
import app.seguridad.tools.PromptErrorKind
import app.seguridad.tools.RetryOutcome
import app.seguridad.tools.SingleRun
import app.seguridad.tools.StrongBoxFailure
import app.seguridad.tools.UnlockFailure
import app.seguridad.tools.firstAttempt
import app.seguridad.tools.legacyKeyLevel
import app.seguridad.tools.nextAfterFailure
import app.seguridad.tools.promptErrorKind
import app.seguridad.tools.shouldMarkStrongBoxFailed
import app.seguridad.tools.unlockBlamesStrongBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

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

    /** Drops the wrapped key and the Keystore key. What is known about StrongBox on this phone stays. */
    fun disable(context: Context) {
        prefs(context).edit {
            remove(KEY_IV)
            remove(KEY_BLOB)
            remove(KEY_IN_STRONGBOX)
        }
        deleteKey()
    }

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

    /** A prompt asked for now would never show nor call back. */
    private fun canPrompt(activity: FragmentActivity): Boolean =
        !activity.isFinishing && !activity.isDestroyed && !activity.supportFragmentManager.isStateSaved

    private val interruption = Notice<String>()

    /**
     * M-4: an enable cut short because the activity was re-created (a turn of the phone, a theme change) while it ran. The
     * screen that asked is gone with that activity, so the message waits here for the new one ([VaultScreen] shows it,
     * then [interruptionShown]). Nothing was stored: the switch stays off and «Activar huella» can be tapped again.
     */
    val interrupted: StateFlow<String?> = interruption.value

    fun interruptionShown(message: String) = interruption.shown(message)

    // One enable at a time: a second «Activar huella» while one is running is ignored. Freed by [release], from [end] on
    // every exit, or once a key generation still running at [end] has returned.
    private val enabling = AtomicBoolean(false)

    /** One run of [enable]: both copies of the data key, and what is known about the StrongBox attempt. Main thread. */
    private class EnableFlow(val activity: FragmentActivity, val keys: List<ByteArray>, val onDone: (String?) -> Unit) {
        val scope: CoroutineScope = MainScope()
        var done = false
        var strongBoxFailure: StrongBoxFailure? = null
        var marked = false
        var observer: LifecycleEventObserver? = null

        /** The prompt on screen, to close it when the activity is re-created (it would stay, tied to the old one). */
        var prompt: BiometricPrompt? = null

        // A key is being generated off the main thread: freeing the next enable and deleting the key wait for it.
        var keygenRunning = false
    }

    /**
     * Asks for the fingerprint, then stores [dataKey] wrapped; [dataKey] is wiped either way. With StrongBox, a failure
     * at any step (key, init, prompt error, doFinal) deletes the key and runs the whole cycle once more in the processor's
     * secure zone: the fingerprint may be asked for twice. «StrongBox falló» is recorded only on evidence
     * ([shouldMarkStrongBoxFailed]). A user cancel stops, with no retry and no message. [onDone] gets null when enabled,
     * a message when not; it is not called on cancel, nor when the activity goes away. When the activity is re-created
     * meanwhile (M-4), the flow ends as well (its prompt belongs to the old activity), and [interrupted] says so instead,
     * for the new activity. Call it on the main thread.
     */
    fun enable(activity: FragmentActivity, dataKey: ByteArray, onDone: (String?) -> Unit) {
        if (!enabling.compareAndSet(false, true)) {
            dataKey.fill(0) // Another enable is running: this one is ignored, and its copy must not linger.
            return
        }
        val first = firstAttempt(hasStrongBox(activity), Build.VERSION.SDK_INT, strongBoxFailed(activity))
        // The fallback's own copy, taken before the first attempt can wipe [dataKey]; [end] wipes both.
        val spare = if (nextAfterFailure(first, cancelled = false) != null) dataKey.copyOf() else null
        val flow = EnableFlow(activity, listOfNotNull(dataKey, spare), onDone)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                // Never a silent cancel on a re-creation: the new activity tells the user to tap again.
                val interrupted = !flow.done && activity.isChangingConfigurations
                if (interrupted) interruption.post(FINGERPRINT_ENABLE_INTERRUPTED)
                end(flow, null, stored = false, notify = false)
                // The old prompt is of no use now (the flow is done, a fingerprint there does nothing): closed. Its
                // callbacks see flow.done. Any failure leaves it as it was, which is still closed for the vault.
                if (interrupted) {
                    try {
                        flow.prompt?.cancelAuthentication()
                    } catch (e: Exception) {
                        // Nothing to do: the flow already ended and stored nothing.
                    }
                }
                flow.prompt = null
            }
        }
        flow.observer = observer
        activity.lifecycle.addObserver(observer)
        // A wrapped key left behind would point at the Keystore key replaced below.
        disable(activity)
        attempt(flow, first, dataKey, spare)
    }

    /** One cycle: key and init (off the main thread), prompt, doFinal. [spare] is the fallback's copy, if any. */
    private fun attempt(flow: EnableFlow, level: KeyLevel, key: ByteArray, spare: ByteArray?) {
        val activity = flow.activity
        if (flow.done) return
        if (!canPrompt(activity)) return end(flow, "No se pudo activar la huella", stored = false)
        val inStrongBox = level == KeyLevel.STRONGBOX
        flow.scope.launch {
            // StrongBox key generation can take hundreds of ms: not on the main thread.
            flow.keygenRunning = true
            val cipher = try {
                withContext(Dispatchers.Default) {
                    try {
                        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, createKey(inStrongBox)) }
                    } catch (e: Exception) {
                        null
                    }
                }
            } finally {
                // Back on the main thread, also when cancelled (withContext waits for the block). If the flow ended
                // meanwhile (activity gone), the key just made is this flow's: no newer enable could start, the guard held.
                flow.keygenRunning = false
                if (flow.done) release(stored = false)
            }
            if (flow.done) return@launch
            if (cipher == null) {
                return@launch failed(flow, level, key, spare, StrongBoxFailure.KEY_SETUP, "Este móvil no permite guardar la clave con huella")
            }
            // Back on the main thread: the app may have gone to the background meanwhile.
            if (!canPrompt(activity)) return@launch end(flow, "No se pudo activar la huella", stored = false)
            flow.prompt = prompt(activity, "Activar desbloqueo con huella", cipher, onOk = { c ->
                if (flow.done) return@prompt // Ended meanwhile (activity gone): its keys are wiped, nothing is stored.
                if (c == null) {
                    return@prompt failed(flow, level, key, spare, StrongBoxFailure.AUTH_OR_FINAL, "No se pudo usar la huella")
                }
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
                    end(flow, null, stored = true)
                } else {
                    failed(flow, level, key, spare, StrongBoxFailure.AUTH_OR_FINAL, "No se pudo activar la huella")
                }
            }, onError = { _, message ->
                // Every error that is not a cancel gets the one retry; whether StrongBox is blamed is decided later.
                if (!flow.done) failed(flow, level, key, spare, StrongBoxFailure.AUTH_OR_FINAL, message)
            }, onCancel = {
                if (!flow.done) failed(flow, level, key, spare, null, "")
            })
        }
    }

    /** This attempt did not store the key ([failure] null: the user cancelled). Falls back once, or ends. */
    private fun failed(flow: EnableFlow, level: KeyLevel, key: ByteArray, spare: ByteArray?, failure: StrongBoxFailure?, message: String) {
        key.fill(0)
        deleteKey()
        val next = nextAfterFailure(level, cancelled = failure == null)
        if (next == null || spare == null || failure == null) {
            return end(flow, message, stored = false, notify = failure != null) // Cancelled: nothing to say, as before.
        }
        flow.strongBoxFailure = failure
        note(flow, RetryOutcome.PENDING) // A Keystore refusal is recorded now.
        if (failure == StrongBoxFailure.KEY_SETUP) return attempt(flow, next, spare, null) // No prompt was shown.
        flow.scope.launch {
            // Posted (Dispatchers.Main): the prompt's own removal, already queued, runs first. Then wait until it is gone.
            val start = SystemClock.uptimeMillis()
            while (flow.activity.supportFragmentManager.findFragmentByTag(BIOMETRIC_FRAGMENT_TAG) != null) {
                if (SystemClock.uptimeMillis() - start >= PROMPT_GONE_WAIT_MS) {
                    return@launch end(flow, "No se pudo activar la huella", stored = false)
                }
                delay(PROMPT_GONE_POLL_MS)
            }
            attempt(flow, next, spare, null)
        }
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
     * Every exit of [enable], once: wipes both copies at once, then drops an unused key and frees the next enable
     * ([release]), then reports. With a key being generated, [release] waits for it ([attempt]).
     */
    private fun end(flow: EnableFlow, message: String?, stored: Boolean, notify: Boolean = true) {
        if (flow.done) return
        flow.done = true
        note(flow, if (stored) RetryOutcome.STORED else RetryOutcome.FAILED)
        flow.keys.forEach { it.fill(0) }
        if (!flow.keygenRunning) release(stored)
        flow.scope.cancel()
        flow.observer?.let { flow.activity.lifecycle.removeObserver(it) }
        if (notify) flow.onDone(message)
    }

    /** Drops the Keystore key unless it holds the stored wrap, then lets the next enable start. */
    private fun release(stored: Boolean) {
        if (!stored) deleteKey()
        enabling.set(false)
    }

    // One unlock at a time (CU-B revisión 1): the automatic start and a tap on «Usar huella» must not run two
    // Cipher.init on StrongBox at once, whose failure would then be blamed on StrongBox ([UnlockFailure.INIT]).
    private val unlocking = SingleRun()

    /**
     * Asks for the fingerprint and hands back the data key; [onError] with a message otherwise. Ignored while another
     * unlock runs. The guard is freed on every end: [onKey], [onError], a cancel, a prompt that cannot be shown, any
     * exception, and the activity going away once the prompt was shown (a prompt that never calls back). Before that,
     * a re-creation (a rotation during Cipher.init) does not free it: the init still running ends through onEnd
     * (canPrompt is false on a destroyed activity), so the new activity's automatic start can never run a second
     * Cipher.init on StrongBox alongside it.
     */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val ticket = unlocking.start() ?: return
        var observer: LifecycleEventObserver? = null
        var promptShown = false
        val finish: () -> Unit = {
            ticket.release()
            observer?.let { activity.lifecycle.removeObserver(it) }
            observer = null
        }
        observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY && promptShown) finish() }.also {
            activity.lifecycle.addObserver(it)
        }
        try {
            unlockOnce(
                activity,
                onKey = { key ->
                    finish()
                    onKey(key)
                },
                onError = { message ->
                    finish()
                    onError(message)
                },
                onEnd = finish,
                onShown = { promptShown = true },
            )
        } catch (e: Exception) {
            finish()
            onError("No se pudo usar la huella")
        } catch (e: Throwable) {
            finish() // As in the coroutine of unlockOnce: released, then the Error goes on.
            throw e
        }
    }

    /** [unlock] itself; [onEnd] when it ends without calling [onKey] or [onError] (a cancel, no prompt possible). */
    private fun unlockOnce(
        activity: FragmentActivity,
        onKey: (ByteArray) -> Unit,
        onError: (String) -> Unit,
        onEnd: () -> Unit,
        onShown: () -> Unit,
    ) {
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
                disable(activity)
                onError(FINGERPRINT_NOT_RESPONDING)
            } else {
                onError(message)
            }
        }

        // M-5: loading the Keystore and Cipher.init (a begin on the StrongBox chip, tens to hundreds of ms on some phones)
        // run off the main thread. The CryptoObject contract is unchanged: the cipher is initialised before authenticate,
        // which is called back on the main thread.
        val scope = MainScope()
        scope.launch {
            try {
                unlockStarted(activity, scope, ivBytes, wrapped, onKey, onError, onEnd, onShown, { key = it }, ::failedAt)
            } catch (e: CancellationException) {
                onEnd()
                throw e
            } catch (e: Exception) {
                onError("No se pudo usar la huella")
            } catch (e: Throwable) {
                // An Error (OutOfMemoryError in the Keystore, say): the guard is released first, so a later «Usar huella»
                // is not refused for good, and the Error goes on as before.
                onEnd()
                throw e
            }
        }
    }

    /** The coroutine of [unlockOnce]: Cipher.init off the main thread, then the prompt on it. */
    private suspend fun unlockStarted(
        activity: FragmentActivity,
        scope: CoroutineScope,
        ivBytes: ByteArray,
        wrapped: ByteArray,
        onKey: (ByteArray) -> Unit,
        onError: (String) -> Unit,
        onEnd: () -> Unit,
        onShown: () -> Unit,
        keptKey: (SecretKey) -> Unit,
        failedAt: (UnlockFailure, String) -> Unit,
    ) {
        run {
            val started: Result<Cipher> = try {
                withContext(Dispatchers.Default) {
                    try {
                        val k = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
                        keptKey(k)
                        Result.success(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, ivBytes)) })
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            } finally {
                scope.cancel() // One step; what follows does not suspend.
            }
            val cipher = started.getOrElse { e ->
                if (e is KeyPermanentlyInvalidatedException) {
                    disable(activity)
                    return onError("Has cambiado las huellas del móvil: entra con la contraseña maestra y vuelve a activarla")
                }
                // Broad on purpose: any failure to start a StrongBox key leads to the TEE on re-enable, the safe direction.
                return failedAt(UnlockFailure.INIT, "No se pudo usar la huella")
            }
            // Back on the main thread: the activity may have gone meanwhile; a prompt asked for now would never show.
            if (!canPrompt(activity)) return onEnd()
            onShown()
            showUnlockPrompt(activity, cipher, wrapped, onKey, onError, onEnd, failedAt)
        }
    }

    /** The unlock prompt for an initialised [cipher]; [failedAt] decides what a failure means (see [unlock]). */
    private fun showUnlockPrompt(
        activity: FragmentActivity,
        cipher: Cipher,
        wrapped: ByteArray,
        onKey: (ByteArray) -> Unit,
        onError: (String) -> Unit,
        onEnd: () -> Unit,
        failedAt: (UnlockFailure, String) -> Unit,
    ) {
        prompt(activity, "Desbloquear la bóveda", cipher, onCancel = onEnd, onOk = { c ->
            if (c == null) return@prompt failedAt(UnlockFailure.NO_CRYPTO_OBJECT, "No se pudo usar la huella")
            // A StrongBox that rejects the fingerprint's auth token fails here, not at init.
            val dataKey = try {
                c.doFinal(wrapped)
            } catch (e: Exception) {
                return@prompt failedAt(UnlockFailure.DO_FINAL, "No se pudo desbloquear con la huella")
            }
            try {
                onKey(dataKey)
            } catch (e: Exception) {
                dataKey.fill(0) // onKey took the key and failed: nobody else holds this copy.
                onError("No se pudo desbloquear con la huella")
            }
        }, onError = { _, message ->
            // The prompt never uses the key: sensor faults, timeouts and lockouts are not StrongBox's (1.2.2 behaviour).
            failedAt(UnlockFailure.PROMPT_ERROR, message)
        })
    }
}
