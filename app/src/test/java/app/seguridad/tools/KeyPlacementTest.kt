package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPlacementTest {
    @Test
    fun strongBoxOnlyWhenPresentAndNewEnoughAndNotFailed() {
        for (has in listOf(false, true)) for (sdk in listOf(27, 28)) for (failed in listOf(false, true)) {
            val expected = if (has && sdk >= 28 && !failed) KeyLevel.STRONGBOX else KeyLevel.TEE
            assertEquals("has=$has sdk=$sdk failed=$failed", expected, firstAttempt(has, sdk, failed))
        }
        // Newer versions keep trying StrongBox; older ones never do, whatever the phone says.
        assertEquals(KeyLevel.STRONGBOX, firstAttempt(hasStrongBox = true, sdk = 37, strongBoxFailedBefore = false))
        assertEquals(KeyLevel.TEE, firstAttempt(hasStrongBox = true, sdk = 26, strongBoxFailedBefore = false))
    }

    @Test
    fun hintOnlyOnceAndOnlyForTeeWithStrongBox() {
        assertTrue(shouldOfferStrongBoxHint(hasStrongBox = true, current = KeyLevel.TEE, strongBoxFailedBefore = false, hintShown = false))
        // Already shown once.
        assertFalse(shouldOfferStrongBoxHint(hasStrongBox = true, current = KeyLevel.TEE, strongBoxFailedBefore = false, hintShown = true))
        // StrongBox failed on this phone: re-enabling would land in the TEE again.
        assertFalse(shouldOfferStrongBoxHint(hasStrongBox = true, current = KeyLevel.TEE, strongBoxFailedBefore = true, hintShown = false))
        // No StrongBox on this phone.
        assertFalse(shouldOfferStrongBoxHint(hasStrongBox = false, current = KeyLevel.TEE, strongBoxFailedBefore = false, hintShown = false))
        // Already in StrongBox, or nothing known about the key (no key, unreadable KeyInfo).
        for (level in listOf(KeyLevel.STRONGBOX, KeyLevel.SOFTWARE, KeyLevel.UNKNOWN)) {
            assertFalse("$level", shouldOfferStrongBoxHint(hasStrongBox = true, current = level, strongBoxFailedBefore = false, hintShown = false))
        }
    }

    @Test
    fun doFinalFailureFallsBackOnce() {
        assertEquals(KeyLevel.TEE, nextAfterFailure(KeyLevel.STRONGBOX, cancelled = false))
        assertNull(nextAfterFailure(KeyLevel.TEE, cancelled = false))
        assertNull(nextAfterFailure(KeyLevel.STRONGBOX, cancelled = true))
        assertNull(nextAfterFailure(KeyLevel.TEE, cancelled = true))
        // Never attempted, so nothing to fall back from.
        assertNull(nextAfterFailure(KeyLevel.SOFTWARE, cancelled = false))
        assertNull(nextAfterFailure(KeyLevel.UNKNOWN, cancelled = false))
        // The fallback chain ends after one step: StrongBox, then the TEE, then stop.
        assertNull(nextAfterFailure(nextAfterFailure(KeyLevel.STRONGBOX, cancelled = false)!!, cancelled = false))
    }

    @Test
    fun beforeApi31SecureHardwareIsStrongBoxOnlyIfCreatedThere() {
        // KeyInfo before Android 12 only says «inside secure hardware»: StrongBox comes from how the key was created.
        assertEquals(KeyLevel.STRONGBOX, legacyKeyLevel(insideSecureHardware = true, createdInStrongBox = true))
        assertEquals(KeyLevel.TEE, legacyKeyLevel(insideSecureHardware = true, createdInStrongBox = false))
        // Out of secure hardware is software, whatever was asked for.
        assertEquals(KeyLevel.SOFTWARE, legacyKeyLevel(insideSecureHardware = false, createdInStrongBox = true))
        assertEquals(KeyLevel.SOFTWARE, legacyKeyLevel(insideSecureHardware = false, createdInStrongBox = false))
    }

    @Test
    fun labels() {
        assertEquals("Chip seguro (StrongBox)", keyLevelLabel(KeyLevel.STRONGBOX))
        assertEquals("Zona segura del procesador", keyLevelLabel(KeyLevel.TEE))
        assertEquals("Software: el móvil no protege esta clave con hardware", keyLevelLabel(KeyLevel.SOFTWARE))
        assertEquals(KEY_LOCATION_UNKNOWN, keyLevelLabel(KeyLevel.UNKNOWN))
        // Every label is checked by ToolTextsTest (annex wording, forbidden words).
        for (level in KeyLevel.entries) assertTrue("$level", keyLevelLabel(level) in ALL_TOOL_TEXTS)
    }
}
