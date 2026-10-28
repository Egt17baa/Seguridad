package app.seguridad.tools

/**
 * Where the fingerprint key lives, as the Keystore reports it. [UNKNOWN]: no key, or its KeyInfo could not be read
 * (API 31+ «unknown secure» level included).
 */
enum class KeyLevel { STRONGBOX, TEE, SOFTWARE, UNKNOWN }

/** StrongBox exists from Android 9 (API 28). */
private const val STRONGBOX_MIN_SDK = 28

/**
 * What enabling the fingerprint tries first: StrongBox only if the phone has it, runs Android 9 or later and StrongBox
 * has not failed on it before; otherwise the processor's secure zone (TEE), the key of 1.1 and 1.2.2.
 */
fun firstAttempt(hasStrongBox: Boolean, sdk: Int, strongBoxFailedBefore: Boolean): KeyLevel =
    if (hasStrongBox && sdk >= STRONGBOX_MIN_SDK && !strongBoxFailedBefore) KeyLevel.STRONGBOX else KeyLevel.TEE

/**
 * After an attempt at [tried] did not end with a wrapped key: the level to try next, or null to stop. A StrongBox failure
 * falls back once to the TEE; a user cancel is not a failure (no retry), and nothing comes after the TEE.
 */
fun nextAfterFailure(tried: KeyLevel, cancelled: Boolean): KeyLevel? =
    if (!cancelled && tried == KeyLevel.STRONGBOX) KeyLevel.TEE else null

/** How the StrongBox attempt failed. */
enum class StrongBoxFailure {
    /** generateKey or Cipher.init threw: the Keystore refused the StrongBox key. */
    KEY_SETUP,

    /** A prompt error that is not a cancel, no CryptoObject, or doFinal failed: could be the user or the moment. */
    AUTH_OR_FINAL,
}

/** How the TEE retry after a StrongBox failure ended, as far as it is known. */
enum class RetryOutcome {
    /** Not ended yet (the decision taken when the StrongBox attempt fails). */
    PENDING,

    /** The TEE key wrapped and stored the data key. */
    STORED,

    /** The retry failed, was cancelled, timed out waiting, or was stopped (app in the background, activity gone). */
    FAILED,
}

/**
 * Whether to record «StrongBox falló en este móvil», which sends later enables straight to the TEE and hides R2. Only on
 * evidence: a Keystore refusal at once; otherwise only if the same fingerprint then worked in the TEE. Pressing Home,
 * a timeout or a lockout must not pin a phone to the TEE for good.
 */
fun shouldMarkStrongBoxFailed(failure: StrongBoxFailure, retry: RetryOutcome): Boolean = when (failure) {
    StrongBoxFailure.KEY_SETUP -> true
    StrongBoxFailure.AUTH_OR_FINAL -> retry == RetryOutcome.STORED
}

// androidx.biometric.BiometricPrompt.ERROR_* (the platform's BiometricConstants), mirrored so the JVM tests can use them.
const val PROMPT_ERROR_HW_UNAVAILABLE = 1
const val PROMPT_ERROR_UNABLE_TO_PROCESS = 2
const val PROMPT_ERROR_CANCELED = 5
const val PROMPT_ERROR_LOCKOUT = 7
const val PROMPT_ERROR_VENDOR = 8
const val PROMPT_ERROR_USER_CANCELED = 10
const val PROMPT_ERROR_NEGATIVE_BUTTON = 13

/** What a BiometricPrompt error code says about who is to blame. */
enum class PromptErrorKind {
    /** The user said no (the negative button, or cancelled): not a failure. */
    CANCEL,

    /** The sensor or the secure hardware behind it did not work. */
    HARDWARE,

    /** The user or the moment: system cancel (Home, app switch), timeout, lockout, no fingerprints, etc. */
    OTHER,
}

fun promptErrorKind(code: Int): PromptErrorKind = when (code) {
    PROMPT_ERROR_USER_CANCELED, PROMPT_ERROR_NEGATIVE_BUTTON -> PromptErrorKind.CANCEL
    PROMPT_ERROR_HW_UNAVAILABLE, PROMPT_ERROR_UNABLE_TO_PROCESS, PROMPT_ERROR_VENDOR -> PromptErrorKind.HARDWARE
    else -> PromptErrorKind.OTHER
}

/**
 * R2: someone who enabled the fingerprint before 1.3.0 keeps the key in the TEE (no silent migration: it would ask for
 * the fingerprint and open another way to fail). Ajustes says once that StrongBox is there, unless it failed here.
 */
fun shouldOfferStrongBoxHint(hasStrongBox: Boolean, current: KeyLevel, strongBoxFailedBefore: Boolean, hintShown: Boolean): Boolean =
    hasStrongBox && current == KeyLevel.TEE && !strongBoxFailedBefore && !hintShown

/**
 * Before Android 12, KeyInfo has no security level, only «inside secure hardware». A key created with
 * setIsStrongBoxBacked(true) is in StrongBox (generateKey fails otherwise), so that is remembered when enabling.
 */
fun legacyKeyLevel(insideSecureHardware: Boolean, createdInStrongBox: Boolean): KeyLevel = when {
    !insideSecureHardware -> KeyLevel.SOFTWARE
    createdInStrongBox -> KeyLevel.STRONGBOX
    else -> KeyLevel.TEE
}

/** The «Dónde está la clave de la huella» line (annex texts, in ToolTexts). */
fun keyLevelLabel(level: KeyLevel): String = when (level) {
    KeyLevel.STRONGBOX -> KEY_LOCATION_STRONGBOX
    KeyLevel.TEE -> KEY_LOCATION_TEE
    KeyLevel.SOFTWARE -> KEY_LOCATION_SOFTWARE
    KeyLevel.UNKNOWN -> KEY_LOCATION_UNKNOWN
}
