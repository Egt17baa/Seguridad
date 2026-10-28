package app.seguridad.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import app.seguridad.tools.FINGERPRINT_NOT_RESPONDING
import app.seguridad.tools.KeyLevel
import app.seguridad.tools.firstAttempt
import app.seguridad.tools.legacyKeyLevel
import app.seguridad.tools.nextAfterFailure
import java.security.KeyStore
import java.util.Base64
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

// After a prompt ends, its fragment is removed by a transaction that runs after our callback: a prompt started from the
// callback would be torn down with it. The fallback waits for that, and for the dialog to go away.
private const val FALLBACK_DELAY_MS = 600L

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

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onOk: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Bóveda de Seguridad")
            .setNegativeButtonText("Usar contraseña maestra")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher?.let(onOk) ?: onError("No se pudo usar la huella")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }
            },
        ).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /**
     * Asks for the fingerprint, then stores [dataKey] wrapped; [dataKey] is wiped either way. With StrongBox, a failure
     * at any step (key, init, prompt error, doFinal) deletes the key, remembers it, and runs the whole cycle once more in
     * the processor's secure zone: the fingerprint may be asked for twice. A user cancel stops, with no retry and no
     * message. [onDone] gets null when enabled, a message when not; it is not called on cancel.
     */
    fun enable(activity: FragmentActivity, dataKey: ByteArray, onDone: (String?) -> Unit) {
        val first = firstAttempt(hasStrongBox(activity), Build.VERSION.SDK_INT, strongBoxFailed(activity))
        // The fallback's own copy, taken before the first attempt can wipe [dataKey]; wiped on every exit too.
        val spare = if (nextAfterFailure(first, cancelled = false) != null) dataKey.copyOf() else null
        // A wrapped key left behind would point at the Keystore key replaced below.
        disable(activity)
        attempt(activity, first, dataKey, spare, onDone)
    }

    private fun attempt(activity: FragmentActivity, level: KeyLevel, dataKey: ByteArray, spare: ByteArray?, onDone: (String?) -> Unit) {
        val inStrongBox = level == KeyLevel.STRONGBOX

        // This attempt did not store the key: fall back once ([nextAfterFailure]) with [spare], or stop.
        fun fail(cancelled: Boolean, message: String, afterPrompt: Boolean) {
            dataKey.fill(0)
            deleteKey()
            val next = nextAfterFailure(level, cancelled)
            if (next == null || spare == null) {
                spare?.fill(0)
                if (!cancelled) onDone(message) // Cancelled: nothing to say, as before.
                return
            }
            markStrongBoxFailed(activity) // From now on, enabling goes straight to the processor's secure zone.
            val retry = { attempt(activity, next, spare, null, onDone) }
            if (afterPrompt) Handler(Looper.getMainLooper()).postDelayed(retry, FALLBACK_DELAY_MS) else retry()
        }

        // A prompt asked for now would never show nor call back, and the keys would linger.
        if (activity.isFinishing || activity.isDestroyed || activity.supportFragmentManager.isStateSaved) {
            dataKey.fill(0)
            spare?.fill(0)
            deleteKey()
            onDone("No se pudo activar la huella")
            return
        }
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, createKey(inStrongBox)) }
        } catch (e: Exception) {
            return fail(cancelled = false, "Este móvil no permite guardar la clave con huella", afterPrompt = false)
        }
        prompt(activity, "Activar desbloqueo con huella", cipher, onOk = { c ->
            val stored = try {
                val wrapped = c.doFinal(dataKey)
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
                dataKey.fill(0)
                spare?.fill(0)
                onDone(null)
            } else {
                fail(cancelled = false, "No se pudo activar la huella", afterPrompt = true)
            }
        }, onError = { message ->
            fail(cancelled = false, message, afterPrompt = true)
        }, onCancel = {
            fail(cancelled = true, "", afterPrompt = true) // The copies of the data key must not linger.
        })
    }

    /** Asks for the fingerprint and hands back the data key; [onError] with a message otherwise. */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val p = prefs(activity)
        val iv = p.getString(KEY_IV, null)
        val blob = p.getString(KEY_BLOB, null)
        if (iv == null || blob == null) return onError("La huella no está activada")
        val ivBytes = runCatching { Base64.getDecoder().decode(iv) }.getOrNull() ?: return onError("No se pudo usar la huella")
        var key: SecretKey? = null
        val cipher = try {
            key = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ivBytes)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            return onError("Has cambiado las huellas del móvil: entra con la contraseña maestra y vuelve a activarla")
        } catch (e: Exception) {
            // Created in StrongBox (recorded when enabling) or, if that record is missing, as KeyInfo reports it.
            val strongBoxKey = p.getBoolean(KEY_IN_STRONGBOX, false) || (key != null && levelOf(activity, key) == KeyLevel.STRONGBOX)
            if (strongBoxKey) {
                // StrongBox does not answer here: re-enabling (with the master password) goes to the processor's secure zone.
                markStrongBoxFailed(activity)
                disable(activity)
                return onError(FINGERPRINT_NOT_RESPONDING)
            }
            return onError("No se pudo usar la huella")
        }
        prompt(activity, "Desbloquear la bóveda", cipher, onOk = { c ->
            try {
                onKey(c.doFinal(Base64.getDecoder().decode(blob)))
            } catch (e: Exception) {
                onError("No se pudo desbloquear con la huella")
            }
        }, onError = onError)
    }
}
