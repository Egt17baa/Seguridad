package app.seguridad.ui.screens

import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Branch review, C I-1 (test n.º 1 of part C): a dialog of «Bóvedas» that shows or holds something of the open vault or a
 * typed password («Cambiar el nombre» with the vault's name, «Borrar» with its master, «Bóveda nueva» and «Importar como
 * bóveda nueva» with a new or a copy's master) closes when the vault locks, as «PIN y códigos». Only «Borrar» of an empty
 * slot (nothing typed) stays; with no vault, the ones that make one stay too.
 */
class VaultPickerDialogsTest {
    @Test fun eachDialogInEachState() {
        val open = VaultState.Unlocked(VaultData())
        val expected = mapOf(
            open to PickerDialogKind.entries.toSet(),
            VaultState.Locked to setOf(PickerDialogKind.DELETE_EMPTY),
            VaultState.Loading to setOf(PickerDialogKind.DELETE_EMPTY),
            VaultState.NoVault to setOf(PickerDialogKind.NEW, PickerDialogKind.IMPORT_NEW, PickerDialogKind.DELETE_EMPTY),
        )
        for ((state, kept) in expected) {
            assertEquals(state.toString(), kept, PickerDialogKind.entries.filter { pickerDialogKept(it, state) }.toSet())
        }
    }

    /** The screen closes the dialog from the active vault's state (synchronous), never from `canAdd` (read off the main thread). */
    @Test fun theScreenClosesItOnTheVaultsState() {
        val src = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/app/seguridad/ui/screens/VaultPickerScreen.kt") }.first { it.isFile }.readText()
        assertTrue(
            Regex("""LaunchedEffect\(active, activeState(?:, dialog)?\) \{\s*dialog\?\.let \{ if \(!pickerDialogKept\(it\.kind, activeState\)\) dialog = null \}""")
                .containsMatchIn(src),
        )
    }
    private fun screen() = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad/ui/screens/VaultPickerScreen.kt") }.first { it.isFile }.readText()

    /**
     * Audit D-2: the effect looks again when a dialog opens, not only when the state changes, so a dialog opened with the
     * vault already locked (the result of a picker, back after the lock) closes in the same step.
     */
    @Test fun aDialogOpenedAfterTheLockClosesToo() {
        assertTrue(Regex("""LaunchedEffect\(active, activeState, dialog\) \{\s*dialog\?\.let \{ if \(!pickerDialogKept\(it\.kind, activeState\)\) dialog = null \}""").containsMatchIn(screen()))
    }

    /** Audit D-2: «Importar como bóveda nueva» asks for the copy's master only with the vault open (or none), else it says why. */
    @Test fun theImporterAsksOnlyWithTheVaultOpen() {
        assertTrue(
            "else -> if (pickerDialogKept(PickerDialogKind.IMPORT_NEW, active.state.value)) dialog = PickerDialog.ImportNew(uri) " +
                "else snackbar.showSnackbar(VAULT_NEW_NEEDS_OPEN)" in screen(),
        )
        assertEquals(1, Regex("""PickerDialog\.ImportNew\(uri\)""").findAll(screen()).count())
    }
}
