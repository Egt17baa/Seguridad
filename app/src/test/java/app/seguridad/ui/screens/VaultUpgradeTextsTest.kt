package app.seguridad.ui.screens

import app.seguridad.crypto.CryptoFailure
import app.seguridad.tools.ForbiddenWords
import org.junit.Assert.assertEquals
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
}
