package app.seguridad.vault

import app.seguridad.tools.ForbiddenWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, tarea 10 (fase C): los errores de la bóveda y de las copias que ganan su qué hacer (inventario
 * `task-10c-inventory.md`). Palabra por palabra: la primera parte es la de antes (lo que pasó); la segunda, lo que se
 * puede hacer donde sale, y nunca nombra una causa que el código no distingue ni promete un reintento.
 */
class ErrorTextsTest {
    private val changed = mapOf(
        BIOMETRIC_UNLOCK_FAILED to ("No se pudo abrir la bóveda con la huella" to "Escribe la contraseña maestra."),
        ATTACHMENT_TOO_BIG to ("El adjunto pasa de 5 MB" to "Elige un archivo más pequeño."),
        BACKUP_DAMAGED to ("La copia está dañada o no es una copia de Clavero" to "Elige otra copia."),
        // BACKUP_NEWER keeps its first sentence, to say the same as 1.4.3 does (with its own copy of it); ronda 1 (m-2).
        BACKUP_NEWER to ("La copia es de una versión más nueva de Clavero" to "Actualiza Clavero."),
        COPY_DAMAGED to ("La copia está dañada: no se puede importar" to "Elige otra copia."),
        IMPORT_TOO_BIG to ("El archivo pasa de 40 MB: no es una copia de la bóveda" to "Elige una copia .sgbak de Clavero."),
    )

    @Test fun eachSaysWhatHappenedThenWhatToDo() {
        for ((text, parts) in changed) {
            assertEquals("${parts.first}. ${parts.second}", text)
            assertEquals(text, emptyList<String>(), ForbiddenWords.problems(text))
            // Never a retry it does not make, nor a cause it does not tell apart.
            assertTrue(text, Regex("(?i)se volverá a intentar|inténtalo|por(que)? (el|la) (disco|memoria|red)").find(text) == null)
        }
    }

    /** The ones that stay word for word (frozen: A6, a Ruling, or data kept in settings.json). */
    /** RT-21, part c: the two texts of running out of memory, word for word (ruling of the director on the final attack). */
    @Test fun theTextsOfRunningOutOfMemory() {
        assertEquals(
            "Este móvil no tiene memoria suficiente para leer esta copia. No se ha cambiado nada. Cierra Clavero del todo, " +
                "vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede leerla.",
            COPY_NO_MEMORY,
        )
        assertEquals(
            "Este móvil no tiene memoria suficiente para abrir esta bóveda. La bóveda sigue guardada: cierra Clavero del todo, " +
                "vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede abrirla.",
            VAULT_NO_MEMORY,
        )
        assertEquals(VAULT_NO_MEMORY, masterUnlockErrorText(app.seguridad.crypto.CryptoFailure(VAULT_NO_MEMORY)))
        assertEquals(COPY_NO_MEMORY, copyFailureText(app.seguridad.crypto.CryptoFailure(COPY_NO_MEMORY)))
        for (t in listOf(COPY_NO_MEMORY, VAULT_NO_MEMORY)) assertEquals(t, emptyList<String>(), ForbiddenWords.problems(t))
    }

    /** RT-25, part a: the text of the list out of memory, word for word (ruling of round 2 of the fixes of the attack). */
    @Test fun theTextOfTheListOutOfMemory() {
        assertEquals(
            "Este móvil no tiene memoria suficiente para enseñar esta bóveda. La bóveda sigue guardada: cierra Clavero del todo, " +
                "vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede enseñarla.",
            LIST_NO_MEMORY,
        )
        assertEquals(emptyList<String>(), ForbiddenWords.problems(LIST_NO_MEMORY))
    }

    @Test fun theFrozenOnesKeepTheirWords() {
        assertEquals("No es una bóveda compatible", NOT_A_VAULT)
        assertEquals("No se pudo hacer la copia automática.", AUTO_BACKUP_FAILED)
        assertEquals("Se hará en cuanto abras la bóveda con la contraseña nueva.", AUTO_BACKUP_REPLACE_WAITS)
        assertTrue("Se volverá a intentar en la próxima copia." in AUTO_BACKUP_OLD_NOT_DELETED)
    }

    /**
     * Ronda 1 (I-1.3): the fingerprint's own failures at the gate (BiometricVault.unlock, called only from the gate,
     * VaultScreen's UnlockVault, next to the master password's field) say what to do, as [BIOMETRIC_UNLOCK_FAILED]
     * does. The same words when enabling the fingerprint (another place, no field there) stay as they were.
     */
    @Test fun theFingerprintsFailuresAtTheGate() {
        assertEquals("No se pudo usar la huella. Escribe la contraseña maestra.", FINGERPRINT_UNLOCK_UNUSABLE)
        assertEquals("No se pudo desbloquear con la huella. Escribe la contraseña maestra.", FINGERPRINT_UNLOCK_FAILED)
        assertEquals("La huella no está activada. Escribe la contraseña maestra.", FINGERPRINT_UNLOCK_NOT_ENABLED)
        val src = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main/java/app/seguridad/android/BiometricVault.kt") }.first { it.isFile }.readText()
        // From unlock( to the end of unlockSteps: every message of its own is one of the three constants.
        val unlock = src.substring(src.indexOf("fun unlock(activity: FragmentActivity"))
        for (old in listOf("\"No se pudo usar la huella\"", "\"No se pudo desbloquear con la huella\"", "\"La huella no está activada\"")) {
            assertTrue("$old is still in the unlock", old !in unlock)
        }
        assertEquals(5, Regex("FINGERPRINT_UNLOCK_UNUSABLE").findAll(unlock).count())
        assertEquals(2, Regex("FINGERPRINT_UNLOCK_FAILED").findAll(unlock).count())
        assertEquals(1, Regex("FINGERPRINT_UNLOCK_NOT_ENABLED").findAll(unlock).count())
        // Enabling keeps its words.
        val enable = src.substring(0, src.indexOf("fun unlock(activity: FragmentActivity"))
        assertTrue("return Failed(StrongBoxFailure.AUTH_OR_FINAL, \"No se pudo usar la huella\")" in enable)
    }
}
