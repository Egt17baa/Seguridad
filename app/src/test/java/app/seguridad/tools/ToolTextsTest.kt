package app.seguridad.tools

import app.seguridad.tools.photo.Found
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.share.PHOTO_FILE_BASE
import app.seguridad.tools.photo.PhotoFormat
import app.seguridad.tools.photo.PhotoReport
import app.seguridad.vault.AUTO_BACKUP_REPLACED
import app.seguridad.vault.AUTO_BACKUP_REPLACE_WAITS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class ToolTextsTest {
    private val annexPath = "docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md"

    /** The annex, found from the test's working directory (app/ for Gradle on Android, jvm-check/ for the JVM check). */
    private fun annex(): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, annexPath)
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("Annex not found: $annexPath")
    }

    /** The tool sections of the annex (from «Limpiar foto» to before «Cambios en los documentos»), by heading. */
    private fun annexSections(): Map<String, String> {
        val text = annex()
        val tools = text.substring(text.indexOf("\n## Limpiar foto"), text.indexOf("\n## Cambios en los documentos"))
        return tools.split("\n## ").filter { it.isNotBlank() }.associate { it.substringBefore('\n').trim() to it }
    }

    /** Top-level «…» quotes (nested ones stay inside) of [section]. */
    private fun quotes(section: String): List<String> {
        val out = mutableListOf<String>()
        for (line in section.lines()) {
            // A rule for the implementer, not a text shown on screen.
            if (line.trimStart().startsWith("- Los acortadores")) continue
            var depth = 0
            val sb = StringBuilder()
            for (c in line) {
                when (c) {
                    '«' -> { if (depth > 0) sb.append(c); depth++ }
                    '»' -> { depth--; if (depth > 0) sb.append(c) else { out += sb.toString(); sb.clear() } }
                    else -> if (depth > 0) sb.append(c)
                }
            }
            assertEquals("Unbalanced quotes in: $line", 0, depth)
        }
        // «Sin avisos (… sin ✓ ni «limpio»)»: a word the annex forbids, not a text.
        return out - setOf("limpio")
    }

    /** Top-level «…» quotes of all the tool sections of the annex. */
    private fun annexTexts(): List<String> = annexSections().values.flatMap { quotes(it) }

    @Test
    fun everyAnnexTextIsInTheListVerbatim() {
        val texts = annexTexts()
        assertTrue("Annex parse found too few texts: ${texts.size}", texts.size >= 50)
        val missing = texts.filter { it !in ALL_TOOL_TEXTS }
        assertTrue("Annex texts missing from ToolTexts (or not verbatim):\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun everyAppTextIsInTheAnnex() {
        // M-5: the other way round. Every text of ALL_TOOL_TEXTS (with the placeholders it is listed with) is quoted in
        // the annex verbatim, so the final legal review sees all of them in one place.
        val annex = annexTexts().toSet()
        val missing = ALL_TOOL_TEXTS.distinct().filter { it !in annex }
        assertTrue("App texts missing from the annex:\n" + missing.joinToString("\n") { "«$it»" }, missing.isEmpty())
    }

    @Test
    fun everyToolSectionOfTheAnnexIsCheckedAgainstTheApp() {
        // One section per task (4: Limpiar foto, 5: Tachar, 6-7: QR, 8: PIN and answers, 9: StrongBox, 10-13 and the
        // catch-ups: the texts added after the opinion), plus the app texts the opinion did not list (M-5): none may
        // drop out of the parse, and all are in the app.
        // The number of texts of each section, exactly: a text that loses its «» would no longer be checked.
        val expected = linkedMapOf(
            "Limpiar foto" to 17, "Tachar en imagen" to 3, "Inspector de QR" to 19, "PIN y códigos" to 2,
            "Respuestas inventadas" to 6, "StrongBox y pendientes" to 7, "Textos añadidos después del dictamen" to 36,
            "Textos de la app que no estaban en el dictamen" to 123,
        )
        val sections = annexSections()
        assertEquals(expected.keys.toList(), sections.keys.toList())
        for ((title, count) in expected) {
            val texts = quotes(sections.getValue(title))
            assertEquals("$title: texts found", count, texts.size)
            val missing = texts.filter { it !in ALL_TOOL_TEXTS }
            assertTrue("$title, not in the app:\n" + missing.joinToString("\n"), missing.isEmpty())
        }
    }

    @Test
    fun theAnnexNamesTheCopyAsTheAppDoes() {
        val text = annex()
        assertTrue("`$PHOTO_FILE_BASE.<ext>`" in text)
        assertFalse("foto-limpia" in text)
        assertTrue(IMAGE_TOOLS_LIMIT in quotes(annexSections().getValue("Textos añadidos después del dictamen")))
    }

    @Test
    fun noForbiddenWords() {
        // «Sin avisos» names «seguro» only to deny it: ForbiddenWords strips «no garantiza que sea seguro». Tachar says
        // «no se puede recuperar» about the covered pixels of the copy; no word of it is forbidden, so no text is exempt.
        for (text in ALL_TOOL_TEXTS) {
            val problems = ForbiddenWords.problems(text)
            assertTrue("$problems in: $text", problems.isEmpty())
        }
    }

    @Test
    fun clipboardHistoryNoticeIsTheReviewedTextWithTheRealSeconds() {
        assertEquals(
            "En este Android, el teclado puede guardar lo copiado en su historial. Seguridad lo borra del portapapeles " +
                "a los 45 s (si Android cierra la app antes, al volver a abrirla), pero no del historial del teclado.",
            clipboardHistoryNoticeText(45),
        )
        for (c in app.seguridad.settings.ClipboardClear.entries) {
            assertTrue(clipboardHistoryNoticeText(c.seconds) in ALL_TOOL_TEXTS)
            assertTrue("a los ${c.seconds} s (" in clipboardHistoryNoticeText(c.seconds))
        }
        assertEquals("Entendido, copiar", CLIPBOARD_HISTORY_ACCEPT)
        assertEquals("Cancelar", CLIPBOARD_HISTORY_CANCEL)
    }

    @Test
    fun heifNoticeNamesTheRealLimit() {
        // The text says «unos 24 megapíxeles»: it must follow the limit heifToJpeg really applies.
        assertTrue("${MAX_REDACT_PIXELS / 1_000_000} megapíxeles" in PHOTO_HEIF_NOTICE)
        assertEquals(24_000_000, MAX_REDACT_PIXELS)
    }

    @Test
    fun theUnsupportedFormatTextNamesHeicOnlyWhereItCanBeConverted() {
        // HEIC needs ImageDecoder (Android 9, API 28); minSdk is 26, where the text must not suggest it.
        assertEquals(PHOTO_UNSUPPORTED, photoUnsupportedText(28))
        assertEquals(PHOTO_UNSUPPORTED, photoUnsupportedText(35))
        for (sdk in listOf(26, 27)) {
            val t = photoUnsupportedText(sdk)
            assertFalse(t, "HEIC" in t.substringBefore(":"))
            assertEquals(PHOTO_UNSUPPORTED_BEFORE_ANDROID_9, t)
        }
        assertEquals(
            "Este formato no se admite en este móvil. Usa una foto JPEG, PNG o WebP: las HEIC necesitan Android 9 o posterior.",
            PHOTO_UNSUPPORTED_BEFORE_ANDROID_9,
        )
        assertTrue(PHOTO_UNSUPPORTED_BEFORE_ANDROID_9 in ALL_TOOL_TEXTS)
        // AVIF (API 31) is never offered: from Android 9 to 11 the text names only formats that work there.
        assertFalse("AVIF" in PHOTO_UNSUPPORTED)
    }

    @Test
    fun pinCountIsWrittenWithSpanishSeparators() {
        assertEquals(pinText("8.463", "13,0"), pinCountText(8_463, 13.0))
        assertTrue("Quedan 98.765.432 PIN posibles (unos 26,6 bits)" in pinCountText(98_765_432, 26.6))
        assertTrue("Quedan 999 PIN posibles (unos 10,0 bits)" in pinCountText(999, 9.9999))
    }

    @Test
    fun exceptionsAreTheAnnexOnes() {
        assertTrue("no garantiza que sea seguro" in QR_NO_ALERTS)
        assertTrue("no se puede recuperar" in REDACT_EXPLANATION)
    }

    @Test
    fun gpsIsRoundedWithSpanishDecimalComma() {
        assertEquals("Ubicación GPS: cerca de 40,42, −3,70 (redondeado)", photoGpsLine(40.4168, -3.7038))
        assertEquals("Ubicación GPS: cerca de −33,87, 151,21 (redondeado)", photoGpsLine(-33.8688, 151.2093))
        // Never a «−0,00».
        assertEquals("Ubicación GPS: cerca de 0,00, 0,00 (redondeado)", photoGpsLine(-0.001, 0.0))
    }

    @Test
    fun foundLinesFollowTheAnnex() {
        val all = Found.entries.toSet()
        val lines = photoFoundLines(PhotoReport(PhotoFormat.JPEG, all, 40.42 to -3.70))
        assertEquals(
            listOf(
                "Ubicación GPS: cerca de 40,42, −3,70 (redondeado)", PHOTO_DEVICE, PHOTO_DATE_SOFTWARE, PHOTO_THUMBNAIL,
                PHOTO_XMP_COMMENTS, PHOTO_ICC_KEPT, PHOTO_MOTION_VIDEO, PHOTO_C2PA, PHOTO_AUTHOR_RIGHTS, PHOTO_OTHER,
            ),
            lines,
        )
        // The colour profile is kept, and the screen says so whenever it is there.
        assertEquals(listOf(PHOTO_ICC_KEPT), photoFoundLines(PhotoReport(PhotoFormat.PNG, setOf(Found.ICC_KEPT), null)))
        // GPS in XMP without readable coordinates: no numbers.
        assertEquals(listOf(PHOTO_GPS_NO_COORDS), photoFoundLines(PhotoReport(PhotoFormat.JPEG, setOf(Found.GPS), null)))
        assertTrue(photoFoundLines(PhotoReport(PhotoFormat.WEBP, emptySet(), null)).isEmpty())
    }

    @Test
    fun nothingToRemoveIgnoresTheKeptProfile() {
        assertFalse(photoHasSomethingToRemove(PhotoReport(PhotoFormat.JPEG, setOf(Found.ICC_KEPT), null)))
        assertFalse(photoHasSomethingToRemove(PhotoReport(PhotoFormat.JPEG, emptySet(), null)))
        assertTrue(photoHasSomethingToRemove(PhotoReport(PhotoFormat.JPEG, setOf(Found.ICC_KEPT, Found.OTHER), null)))
    }

    @Test
    fun doneListNamesWhatWasRemovedButNotTheProfile() {
        val report = PhotoReport(PhotoFormat.JPEG, setOf(Found.GPS, Found.ICC_KEPT, Found.THUMBNAIL), 1.0 to 2.0)
        assertEquals(
            "Copia lista, sin estos datos: ubicación GPS; miniatura incrustada. El original no se ha tocado y sigue " +
                "teniéndolos: comparte la copia, no el original.",
            photoDoneMessage(report),
        )
        // Nothing removed: a short line, not the card's «No hemos encontrado…» again.
        assertEquals(PHOTO_DONE_SHORT, photoDoneMessage(PhotoReport(PhotoFormat.PNG, setOf(Found.ICC_KEPT), null)))
        assertEquals("Copia lista.", PHOTO_DONE_SHORT)
    }

    @Test
    fun convertedPhotoNeverClaimsEverythingWentAndNamesTheKeptProfile() {
        val plain = photoDoneMessage(PhotoReport(PhotoFormat.JPEG, emptySet(), null), converted = true)
        assertEquals(PHOTO_DONE_CONVERTED, plain)
        val withIcc = photoDoneMessage(PhotoReport(PhotoFormat.JPEG, setOf(Found.ICC_KEPT), null), converted = true)
        assertEquals("$PHOTO_DONE_CONVERTED $PHOTO_ICC_KEPT.", withIcc)
        for (t in listOf(plain, withIcc)) assertFalse(t, "todos" in t)
    }

    @Test
    fun downscaleTextIsTheAnnexOneAt24Megapixels() {
        // ImageDecoder reduces to about 24 MP (the annex text); Android 8 halves the sides, so it says the real size.
        assertEquals(REDACT_DOWNSCALED, redactDownscaledText(24))
        assertEquals(
            "La imagen es muy grande y se ha reducido a unos 6 megapíxeles para poder editarla. La copia tendrá ese tamaño.",
            redactDownscaledText(6),
        )
    }

    @Test
    fun afterChangingTheMasterPasswordTheTextsAreTheOnesOfTheDesign() {
        assertEquals("Cambiar la contraseña no cambia las copias que ya existen.", PASSWORD_CHANGE_COPIES)
        assertEquals("Las copias .sgbak de antes, también las automáticas, se siguen abriendo con la contraseña anterior.", PASSWORD_CHANGE_OLD_BACKUPS)
        assertEquals("Hacer una copia nueva y borrar las automáticas anteriores", PASSWORD_CHANGE_REPLACE_COPIES)
        assertEquals(
            "Si temes que alguien tenga la contraseña anterior y una copia, con las dos puede ver todo lo que guardabas " +
                "al hacerla: cambia las contraseñas y los códigos 2FA que más te importen, y renueva tu identidad.",
            PASSWORD_CHANGE_RENEW_HINT,
        )
        assertEquals("Renovar identidad", RENEW_IDENTITY)
        assertEquals(
            "Tus contactos tendrán que añadir tu tarjeta nueva, y no podrás abrir lo que te enviaron a la anterior. " +
                "Avísales por otro medio: mientras usen la anterior, quien tenga tu identidad antigua podrá firmar como " +
                "tú y leer lo que te envíen, si llega a ver esos mensajes.",
            RENEW_IDENTITY_WARNING,
        )
        assertEquals("Las copias automáticas anteriores que ibas a borrar no se borraron: siguen en su carpeta.", AUTO_BACKUP_REPLACE_DROPPED)
        assertEquals(
            "Contraseña maestra cambiada. La huella ya no abre la bóveda, pero no se pudo desactivar: abre la bóveda con " +
                "la contraseña nueva, desactiva la huella y vuelve a activarla.",
            PASSWORD_CHANGED_FINGERPRINT_FAILED,
        )
        // The result texts of the replacement are in the list too, so noForbiddenWords covers them.
        assertTrue(ALL_TOOL_TEXTS.containsAll(listOf(AUTO_BACKUP_REPLACED, AUTO_BACKUP_REPLACE_WAITS)))
    }

    @Test
    fun textsOfTheLegalReviewAreTheProposedOnes() {
        assertEquals("Chip de seguridad (StrongBox)", KEY_LOCATION_STRONGBOX)
        assertEquals("Zona aislada del procesador (TEE)", KEY_LOCATION_TEE)
        assertEquals(
            "Este móvil tiene chip de seguridad (StrongBox). Para usarlo, desactiva la huella y vuelve a activarla.",
            STRONGBOX_AVAILABLE_NOTICE,
        )
        assertEquals(
            // CU-B: with the ViewModel the drawn values survive a rotation; this is when they really go, and only they
            // (the sites and the questions stay). The Abogado's and the reviewer's wording, revisión 2.
            "Desbloquea la bóveda para guardarlo. El PIN, los códigos y las respuestas se pierden si vuelves atrás, si se " +
                "cierra la app, si bloqueas la bóveda o si estás fuera de esta pantalla más tiempo que el bloqueo de la " +
                "bóveda (1 minuto como mínimo).",
            CODES_UNLOCK_TO_SAVE,
        )
        assertEquals(
            "La activación de la huella se interrumpió porque cambió la pantalla (por ejemplo, al girar el móvil). Vuelve " +
                "a tocar «Activar huella».",
            FINGERPRINT_ENABLE_INTERRUPTED,
        )
        assertEquals(
            "Las fotos HEIC, HEIF y AVIF se guardan como JPEG: la copia puede perder algo de calidad y, si la foto pasa " +
                "de unos 24 megapíxeles, se reduce a ese tamaño.",
            PHOTO_HEIF_NOTICE,
        )
        assertTrue("«copia-de-foto»" in PHOTO_FILE_NAME_NOTE)
        assertEquals("copia-de-foto", PHOTO_FILE_BASE)
        assertEquals("Quita la ubicación y otros datos ocultos del archivo", PHOTO_TOOL_SUBTITLE)
        assertEquals("Móvil o cámara: marca y modelo, y a veces número de serie o nombre del propietario", PHOTO_DEVICE)
        assertEquals("datos del móvil o la cámara", PHOTO_REMOVED_NAMES.getValue(Found.DEVICE))
        assertEquals(
            "Datos de autor y de derechos: la copia no los lleva. Si la foto no es tuya, no uses la copia para ocultar de quién es",
            PHOTO_AUTHOR_RIGHTS,
        )
        assertEquals("No se ha podido guardar la copia. Si ha quedado un archivo a medias en la carpeta, bórralo.", PHOTO_SAVE_FAILED)
        assertEquals("El texto completo lleva la contraseña de la red: toca «Mostrar» para verlo.", QR_TEXT_HIDDEN_PASSWORD)
        assertEquals("El texto completo lleva la clave secreta del código 2FA: toca «Mostrar» para verlo.", QR_TEXT_HIDDEN_OTP)
        assertEquals("Se hará en cuanto abras la bóveda con la contraseña nueva.", AUTO_BACKUP_REPLACE_WAITS)
        assertEquals("La hora del móvil se ha atrasado. Por seguridad, escribe la contraseña maestra.", app.seguridad.vault.CLOCK_CHANGED_TEXT)
        assertTrue(RESET_WIPES_DAMAGED.startsWith("Lo guardado no se puede descifrar sin la contraseña. Se borrarán"))
        assertTrue("Las copias .sgbak no se borran" in RESET_WIPES_DAMAGED)
        assertTrue(app.seguridad.vault.CLOCK_CHANGED_TEXT in ALL_TOOL_TEXTS)
    }

    @Test
    fun publicKeyTextsAreTheDesignOnes() {
        // A15b and A16a, verbatim from the design (rows A15b and A16a) with a final period; the real name and count go in.
        assertEquals("El nombre del archivo («x») va dentro, cifrado: lo verá el destinatario.", fileNameInsideText("x"))
        assertEquals(
            "El nombre del archivo («factura marzo.pdf») va dentro, cifrado: lo verá el destinatario.",
            fileNameInsideText("factura marzo.pdf"),
        )
        assertEquals("No enviar el nombre", DONT_SEND_FILE_NAME)
        assertEquals("Esta clave está guardada en 2 contactos.", sameKeyContactsText(2))
        assertEquals("Esta clave está guardada en 5 contactos.", sameKeyContactsText(5))
        assertEquals("No se ha podido copiar.", CLIPBOARD_COPY_FAILED)
        assertEquals("Entendido", CLIPBOARD_COPY_FAILED_OK)
        assertTrue(ALL_TOOL_TEXTS.containsAll(listOf(CLIPBOARD_COPY_FAILED, CLIPBOARD_COPY_FAILED_OK)))
        assertTrue(ALL_TOOL_TEXTS.containsAll(listOf(fileNameInsideText("x"), DONT_SEND_FILE_NAME, sameKeyContactsText(2))))
    }

    @Test
    fun everyTextConstantIsChecked() {
        // A new text in ToolTexts.kt cannot escape noForbiddenWords: every String constant there is in the list
        // (private fragments, such as the GPS line's prefix, inside one of its texts).
        val fields = Class.forName("app.seguridad.tools.ToolTextsKt").declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .onEach { it.isAccessible = true }
        assertTrue("Too few constants found: ${fields.size}", fields.size >= 60)
        for (f in fields) {
            val v = f.get(null) as String
            if (Modifier.isPrivate(f.modifiers)) {
                assertTrue("${f.name} in no text", ALL_TOOL_TEXTS.any { v in it })
            } else {
                assertTrue("${f.name} missing from ALL_TOOL_TEXTS", v in ALL_TOOL_TEXTS)
            }
        }
        assertTrue(ALL_TOOL_TEXTS.containsAll(PHOTO_FOUND_TEXTS.values))
        assertTrue(ALL_TOOL_TEXTS.containsAll(PHOTO_REMOVED_NAMES.values))
    }
}
