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
