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
        // BACKUP_NEWER keeps its words (design §Formato v4, the reader of 1.4.3): a line is added after them.
        BACKUP_NEWER to ("La copia es de una versión más nueva de Clavero" to "Actualiza Clavero para abrirla."),
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
    @Test fun theFrozenOnesKeepTheirWords() {
        assertEquals("No es una bóveda compatible", NOT_A_VAULT)
        assertEquals("No se pudo hacer la copia automática.", AUTO_BACKUP_FAILED)
        assertEquals("Se hará en cuanto abras la bóveda con la contraseña nueva.", AUTO_BACKUP_REPLACE_WAITS)
        assertTrue("Se volverá a intentar en la próxima copia." in AUTO_BACKUP_OLD_NOT_DELETED)
    }
}
