package app.seguridad.vault

import app.seguridad.tools.ForbiddenWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit A-2: the «Más información» of the cost of Argon2id in Ajustes says what the code does: the vault's file is raised
 * to the chosen cost at the next unlock with the master password (VaultStore, the new wrap) and never lowered; copies
 * already made, and texts and files already encrypted, keep theirs. It never says that a higher cost protects copies made
 * before.
 */
class KdfCostHintTest {
    @Test fun theCostHintSaysTheVaultIsRaisedNeverLowered() {
        assertEquals(
            "Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar " +
                "textos y archivos. Si el archivo de la bóveda tiene un coste menor, sube a este la próxima vez que la abras con " +
                "la contraseña maestra; nunca se baja. Las copias ya hechas, y los textos y archivos ya cifrados, guardan su " +
                "coste. Lo cifrado con el coste alto solo se abre en móviles que den a la app al menos 171 MiB de memoria: " +
                "muchos móviles económicos no llegan, así que úsalo solo si vas a abrir tus copias en móviles como este.",
            KDF_COST_MORE,
        )
        assertFalse("Lo ya cifrado guarda su coste" in KDF_COST_MORE)
        assertEquals(emptyList<String>(), ForbiddenWords.problems(KDF_COST_MORE))
        // Ajustes shows this constant, and no copy of the old text.
        val settings = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/app/seguridad/ui/screens/SettingsScreen.kt") }.first { it.isFile }.readText()
        assertTrue("Hint(KDF_COST_MORE)" in settings)
        assertFalse("Lo ya cifrado guarda su coste" in settings)
    }
}
