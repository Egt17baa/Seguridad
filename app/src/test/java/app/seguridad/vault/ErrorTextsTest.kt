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

    /** Review fixes, CR-2: while the copy before the upgrade runs, a save says so (the dialog is not shown yet). */
    @Test fun theTextOfASaveWhileTheUpgradeCopyRuns() {
        assertEquals(
            "Clavero está haciendo la copia automática de antes del cambio al formato nuevo. Espera a que termine e " +
                "inténtalo otra vez.",
            UPGRADE_COPYING,
        )
        assertEquals(UPGRADE_COPYING, storeSaveErrorText(IllegalStateException(UPGRADE_COPYING)))
        assertEquals(emptyList<String>(), ForbiddenWords.problems(UPGRADE_COPYING))
    }

    /** Round 3 of the fixes of the attack, RT-27: the text of an import out of memory, word for word. */
    @Test fun theTextOfAnImportOutOfMemory() {
        assertEquals(
            "Este móvil no tiene memoria suficiente para preparar esta importación. No se ha cambiado nada. Cierra Clavero del " +
                "todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede hacer esta importación.",
            app.seguridad.imports.IMPORT_NO_MEMORY,
        )
        assertEquals(emptyList<String>(), ForbiddenWords.problems(app.seguridad.imports.IMPORT_NO_MEMORY))
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

    /** Review fixes, CR-6: [storeSaveErrorText] with a fallback of its own; the store's texts still go through. */
    @Test fun theSaveErrorTextTakesAFallback() {
        assertEquals("Otro", storeSaveErrorText(java.io.IOException("disco"), fallback = "Otro"))
        assertEquals(TRASH_SAVE_FAILED, storeSaveErrorText(java.io.IOException("disco")))
        for (t in listOf(UPGRADE_PENDING, UPGRADE_COPYING)) assertEquals(t, storeSaveErrorText(IllegalStateException(t), fallback = "Otro"))
        assertEquals(VAULT_TOO_BIG, storeSaveErrorText(VaultLimitExceeded(VAULT_TOO_BIG), fallback = "Otro"))
        assertEquals(FOLDER_DELETE_NAME_TAKEN, storeSaveErrorText(OrganizeRefused(FOLDER_DELETE_NAME_TAKEN), fallback = "Otro"))
        // Anything else is never what the exception said.
        assertEquals("Otro", storeSaveErrorText(IllegalStateException("/data/user/0/…"), fallback = "Otro"))
    }

    /**
     * Review fixes, CR-6: on screen, a save of the vault that fails says the store's own text (an upgrade waiting, a
     * limit, a refused change of the folders) instead of hiding it behind a fixed one. Every `catch (e: Exception)` of
     * `ui/` whose `try` calls a save of the store goes through [storeSaveErrorText]; and the two that do it elsewhere.
     */
    @Test fun theScreensSayTheStoreTextsOfAFailedSave() {
        val root = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }.first { java.io.File(it, "app/src/main/java/app/seguridad").isDirectory }
        val ui = java.io.File(root, "app/src/main/java/app/seguridad/ui")
        val saves = Regex("""\b(store|active|vault|undo)\.(upsert|addAll|clearHistory|restoreVersion|rename|addAttachment|removeAttachment|changeMaster|moveToTrash|delete|restore|purge|emptyTrash|moveEntries|duplicate|createFolder|renameFolder|moveFolder|deleteFolder|saveView|deleteView|setTagColor|clearAllHistory)\(""")
        val hidden = mutableListOf<String>()
        var checked = 0
        for (f in ui.walk().filter { it.isFile && it.extension == "kt" }) {
            val lines = f.readLines()
            for ((i, line) in lines.withIndex()) {
                if (!Regex("""catch \(\w+: Exception\)""").containsMatchIn(line)) continue
                var j = i - 1
                while (j > 0 && !Regex("""\btry\b""").containsMatchIn(lines[j])) j--
                if (!saves.containsMatchIn(lines.subList(j, i).joinToString("\n"))) continue
                checked++
                if (lines.subList(i, minOf(i + 3, lines.size)).none { "storeSaveErrorText(" in it }) hidden += "${f.name}:${i + 1}"
            }
        }
        assertEquals("catches of a save with a fixed text", emptyList<String>(), hidden)
        assertTrue("the scan finds the saves ($checked)", checked >= 10)
        // «Renombrar» (its catch is AppContainer.runMarked) and the trash (its saves come in as a block).
        val picker = java.io.File(ui, "screens/VaultPickerScreen.kt").readText()
        assertTrue(Regex("""active\.rename\(name\)[\s\S]{0,200}storeSaveErrorText\(e, fallback = VAULT_FAILED\)""").containsMatchIn(picker))
        val trash = java.io.File(ui, "screens/TrashScreen.kt").readText()
        assertTrue(Regex("""if \(block\(\) == 0 && none != null\) message = none[\s\S]{0,200}message = storeSaveErrorText\(e\)""").containsMatchIn(trash))
    }
}
