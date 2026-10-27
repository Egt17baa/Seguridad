package app.seguridad.android

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ALIAS = "seguridad_vault_biometric"
private const val PREFS = "vault_biometric"
private const val KEY_IV = "iv"
private const val KEY_BLOB = "wrapped"

/**
 * Wraps the vault's data key with an AES key that lives in the Android Keystore (secure hardware when available)
 * and can only be used right after a strong biometric check. Adding a new fingerprint invalidates it.
 * The wrapped key never leaves the phone and is not part of exported vaults.
 */
object BiometricVault {
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun enabled(context: Context): Boolean = prefs(context).contains(KEY_BLOB)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                // The key only works while the phone is unlocked. Only new keys get it (enable() creates a fresh one); the keys
                // already in the Keystore keep working as they were. StrongBox is left for 1.3.0: some makers fail with StrongBox
                // plus per-use authentication, and there is no phone here to test it on.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUnlockedDeviceRequired(true)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    fun disable(context: Context) {
        prefs(context).edit { clear() }
        runCatching { keyStore().deleteEntry(ALIAS) }
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

    /** Asks for the fingerprint, then stores [dataKey] wrapped; [dataKey] is wiped either way. */
    fun enable(activity: FragmentActivity, dataKey: ByteArray, onDone: (String?) -> Unit) {
        val cipher = try {
            runCatching { keyStore().deleteEntry(ALIAS) }
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: Exception) {
            dataKey.fill(0)
            onDone("Este móvil no permite guardar la clave con huella")
            return
        }
        prompt(activity, "Activar desbloqueo con huella", cipher, onOk = { c ->
            try {
                val wrapped = c.doFinal(dataKey)
                prefs(activity).edit {
                    putString(KEY_IV, Base64.getEncoder().encodeToString(c.iv))
                    putString(KEY_BLOB, Base64.getEncoder().encodeToString(wrapped))
                }
                onDone(null)
            } catch (e: Exception) {
                onDone("No se pudo activar la huella")
            } finally {
                dataKey.fill(0)
            }
        }, onError = {
            dataKey.fill(0)
            onDone(it)
        }, onCancel = {
            dataKey.fill(0) // Cancelled: the copy of the data key must not linger.
        })
    }

    /** Asks for the fingerprint and hands back the data key; [onError] with a message otherwise. */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val p = prefs(activity)
        val iv = p.getString(KEY_IV, null)
        val blob = p.getString(KEY_BLOB, null)
        if (iv == null || blob == null) return onError("La huella no está activada")
        val cipher = try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.getDecoder().decode(iv))) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            return onError("Has cambiado las huellas del móvil: entra con la contraseña maestra y vuelve a activarla")
        } catch (e: Exception) {
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
