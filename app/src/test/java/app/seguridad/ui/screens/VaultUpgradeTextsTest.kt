package app.seguridad.ui.screens

import app.seguridad.crypto.CryptoFailure
import app.seguridad.tools.ForbiddenWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0, tarea 1, ronda 1 de arreglos: los textos del paso al formato nuevo. */
class VaultUpgradeTextsTest {
    /** Review M-3: the export's own reason (a missing or damaged attachment) is shown, as «Exportar» does. */
    @Test fun theExportFailureSaysWhy() {
        assertEquals("El adjunto «a.txt» falta o está dañado", upgradeExportFailure(CryptoFailure("El adjunto «a.txt» falta o está dañado")))
        assertEquals(UPGRADE_EXPORT_FAILED, upgradeExportFailure(java.io.IOException("disco lleno")))
        assertEquals(UPGRADE_EXPORT_FAILED, upgradeExportFailure(null))
    }

    /**
     * Branch review, row 39: a vault that does not fit in a copy says what to do, each place its own: «Exportar» to take
     * things out and export again; the upgrade, that it can go on without a copy (never «quita»).
     */
    @Test fun aVaultThatDoesNotFitSaysWhatToDo() {
        for (m in listOf(app.seguridad.vault.BACKUP_DOES_NOT_FIT, app.seguridad.vault.BACKUP_DOES_NOT_FIT_V3)) {
            assertEquals(
                "$m. Quita adjuntos o entradas grandes, o vacía la papelera, y vuelve a exportarla.",
                exportFailureText(m),
            )
            val upgrade = upgradeExportFailure(CryptoFailure(m))
            assertEquals("$m. Puedes seguir sin copia: la bóveda pasará al formato nuevo y las versiones anteriores ya no la abrirán.", upgrade)
            assertFalse(upgrade, "quita" in upgrade.lowercase())
            assertTrue(UPGRADE_SKIP == "Seguir sin copia")
        }
        // Any other reason, as it was.
        assertEquals("El adjunto «a.txt» falta o está dañado", exportFailureText("El adjunto «a.txt» falta o está dañado"))
        assertEquals(null, exportFailureText(null))
    }

    /** M-5 and O-3 with a folder: fixed texts, at sight, with no forbidden words. */
    @Test fun theNewTextsAreFixedAndClean() {
        assertEquals("No se pudo hacer la copia automática de antes del cambio.", UPGRADE_AUTO_COPY_FAILED)
        assertEquals("Bóveda en el formato nuevo", UPGRADE_DONE_TITLE)
        assertEquals("Las versiones anteriores de Clavero ya no abren esta bóveda ni sus copias nuevas.", UPGRADE_DONE_NOT_BACK)
        assertEquals(
            "La copia de antes del cambio está en la carpeta de la copia automática: «seguridad-antes-de-2-0-20261112-100000.sgbak».",
            upgradeDoneCopy("seguridad-antes-de-2-0-20261112-100000.sgbak"),
        )
        for (t in listOf(UPGRADE_AUTO_COPY_FAILED, UPGRADE_DONE_TITLE, UPGRADE_DONE_NOT_BACK, upgradeDoneCopy("x"), UPGRADE_DONE_OK)) {
            assertTrue(t, ForbiddenWords.problems(t).isEmpty())
        }
    }
    /**
     * Audit D-1: the upgrade waits [app.seguridad.vault.UPGRADE_COPY_WAIT_MS] for the folder's copy; the dialog then says
     * it has not finished (true also of a folder that is only slow), word for word, with the same minutes.
     */
    @Test fun theCopyNotDoneSaysTheWaitAndTheChoice() {
        assertEquals(
            "La copia automática de antes del cambio al formato nuevo no ha terminado en 2 minutos: la carpeta tarda en contestar. " +
                "Puedes exportar una copia a otro sitio o seguir sin copia.",
            UPGRADE_AUTO_COPY_NOT_DONE,
        )
        assertEquals(2L, app.seguridad.vault.UPGRADE_COPY_WAIT_MS / 60_000L)
        assertEquals(0L, app.seguridad.vault.UPGRADE_COPY_WAIT_MS % 60_000L)
        assertTrue("${app.seguridad.vault.UPGRADE_COPY_WAIT_MS / 60_000L} minutos" in UPGRADE_AUTO_COPY_NOT_DONE)
        assertTrue(ForbiddenWords.problems(UPGRADE_AUTO_COPY_NOT_DONE).isEmpty())
        // The dialog says it instead of the failure when the store says so.
        val src = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main/java/app/seguridad/ui/screens/VaultUpgrade.kt") }.first { it.isFile }.readText()
        assertTrue(
            "if (store.upgradeAutoCopyNotDone) Text(UPGRADE_AUTO_COPY_NOT_DONE, color = NegativeColor)\n" +
                "                else if (store.upgradeAutoCopyFailed) Text(UPGRADE_AUTO_COPY_FAILED, color = NegativeColor)" in src,
        )
    }
}
