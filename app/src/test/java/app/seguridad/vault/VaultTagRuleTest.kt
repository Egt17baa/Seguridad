package app.seguridad.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Round 3 of the fixes of the attack, row 63: [isVaultTag] takes exactly what the app writes ([VaultCrypto.newTag]: 16
 * random bytes as 32 lower-case hex) and nothing else; and the app's own tags pass it whatever the default locale, also
 * one with digits that are not Latin (Arabic in Egypt, Persian in Iran), so the rule never refuses a copy of its own.
 */
class VaultTagRuleTest {
    @Test fun theAppsOwnTagsPass() {
        repeat(1_000) { val t = VaultCrypto.newTag(); assertTrue(t, isVaultTag(t)) }
    }

    @Test fun theAppsOwnTagsPassWithANonLatinDefaultLocale() {
        val saved = Locale.getDefault()
        try {
            for (tag in listOf("ar-EG", "fa-IR")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                repeat(1_000) { val t = VaultCrypto.newTag(); assertTrue("$tag: $t", isVaultTag(t)) }
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun anythingElseIsRefused() {
        val ok = "0123456789abcdef0123456789abcdef"
        assertTrue(isVaultTag(ok))
        for (bad in listOf(
            ok.uppercase(), ok.dropLast(1), ok + "0", "", "<script>",
            ok.dropLast(1) + "٣", // Arabic-Indic digit three.
            ok.dropLast(1) + "ａ", // Full-width «a».
            ok.dropLast(1) + "g",
        )) {
            assertFalse(bad, isVaultTag(bad))
        }
    }
}
