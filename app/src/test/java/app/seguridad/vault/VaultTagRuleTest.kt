package app.seguridad.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

/**
 * Round 3 of the fixes of the attack, row 63: [isVaultTag] takes exactly what the app writes ([VaultCrypto.newTag]: 16
 * random bytes as 32 lower-case hex, written by hand, never through the locale) and nothing else; the app's own tags
 * pass it whatever the default locale, also one with digits that are not Latin (Arabic in Egypt, Persian in Iran), so
 * the rule never refuses a copy of its own. A copy whose tag is in Arabic or Persian digits is refused with exactly
 * COPY_DAMAGED and changes nothing (the owner's decision: such a tag is simply not the app's).
 */
class VaultTagRuleTest {
    @get:Rule val tmp = TemporaryFolder()

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

    /** The same 16 bytes give the same tag as `"%02x"` in a Latin locale. */
    @Test fun theTagIsTheSameAsBeforeInALatinLocale() {
        val bytes = ByteArray(256) { it.toByte() }
        val expected = bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
        val hex = Class.forName("app.seguridad.vault.VaultKt").getDeclaredMethod("hex", ByteArray::class.java).apply { isAccessible = true }
        assertEquals(expected, hex.invoke(null, bytes))
    }

    /** A copy whose tag is 32 Arabic or Persian digits: COPY_DAMAGED, exactly, and the store as it was. */
    @Test fun aTagOfArabicOrPersianDigitsIsRefused() {
        for (digit in listOf("\u0663", "\u06F3")) {
            val json = "{\"entries\":[{\"id\":\"e\",\"type\":\"NOTE\",\"title\":\"N\",\"createdAt\":1,\"updatedAt\":1}],\"vaultTag\":\"${digit.repeat(32)}\"}"
            val copy = app.seguridad.redteam.rt20Copy(app.seguridad.redteam.rt20HostileFile(json).first)
            val s = VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { app.seguridad.redteam.RT20_KDF })
            runBlocking { s.load() }
            val before = s.state.value
            try {
                runBlocking { s.importReplace(ByteArrayInputStream(copy), app.seguridad.redteam.RT20_MASTER.toCharArray()) }
                fail("imported")
            } catch (e: app.seguridad.crypto.CryptoFailure) {
                assertEquals(COPY_DAMAGED, e.message)
            }
            assertEquals(before, s.state.value)
        }
    }
}
