package app.seguridad.tools

import app.seguridad.tools.photo.Found
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

/** Annex texts the Abogado Digital's review replaced (CU-A). Task 15 updates the annex and deletes this list. */
private val SUPERSEDED_ANNEX_TEXTS = setOf(
    "Las fotos HEIC, HEIF y AVIF se guardan como JPEG: la copia puede perder algo de calidad.",
    "Quita los datos que la foto guarda dentro del archivo: la ubicación, el modelo del móvil, la fecha, la miniatura y " +
        "otros. No cambia lo que se ve: una cara, una matrícula, tu portal o un reflejo pueden delatarte igual; para eso, " +
        "usa «Tachar en imagen». Tampoco quita las marcas invisibles que pueda llevar la propia imagen ni el rastro que " +
        "deja el sensor de cada cámara. Muchas apps de mensajería quitan parte de estos datos al enviar una foto, pero no " +
        "siempre todos, y no cuando la envías como documento o archivo. La foto se analiza en tu móvil y no se envía a " +
        "ningún sitio.",
    "Móvil: marca, modelo y número de serie",
    "Datos de autor y de derechos: si la foto no es tuya, no los quites para ocultar de quién es",
    "El nombre del archivo también puede decir algo de ti (por ejemplo, la fecha en «IMG_20261027_…»). Por eso la copia " +
        "se llama «foto-limpia».",
    "Enseña lo que hay en un código QR sin abrir nada: ni enlaces, ni redes Wi-Fi, ni pagos. Solo puedes copiarlo. La " +
        "imagen se analiza en tu móvil y no se guarda.",
    "Se añadirá como contacto sin verificar. Para verificarlo, compara la huella con la persona, en persona o por llamada.",
    "Se borrarán la bóveda y también los archivos dañados que la app guardó al intentar repararla. No se puede deshacer.",
    "Chip seguro (StrongBox)",
    "Zona segura del procesador",
    "Este móvil tiene chip seguro. Para usarlo, desactiva la huella y vuelve a activarla.",
)

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

    /** Top-level «…» quotes (nested ones stay inside) of the tool sections of the annex. */
    private fun annexTexts(): List<String> {
        val text = annex()
        val tools = text.substring(text.indexOf("\n## Limpiar foto"), text.indexOf("\n## Cambios en los documentos"))
        val out = mutableListOf<String>()
        for (line in tools.lines()) {
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

    @Test
    fun everyAnnexTextIsInTheListVerbatim() {
        val texts = annexTexts()
        assertTrue("Annex parse found too few texts: ${texts.size}", texts.size >= 50)
        val missing = texts.filter { it !in ALL_TOOL_TEXTS && it !in SUPERSEDED_ANNEX_TEXTS }
        assertTrue("Annex texts missing from ToolTexts (or not verbatim):\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun supersededAnnexTextsAreGoneFromTheApp() {
        // The Abogado Digital's review replaced these annex texts; Task 15 updates the annex and removes this list.
        for (old in SUPERSEDED_ANNEX_TEXTS) assertFalse("Still shown: $old", old in ALL_TOOL_TEXTS)
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
    fun heifNoticeNamesTheRealLimit() {
        // The text says «unos 24 megapíxeles»: it must follow the limit heifToJpeg really applies.
        assertTrue("${MAX_REDACT_PIXELS / 1_000_000} megapíxeles" in PHOTO_HEIF_NOTICE)
        assertEquals(24_000_000, MAX_REDACT_PIXELS)
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
            // CU-B: with the ViewModel the drawn values survive a rotation; this is when they really go.
            "Desbloquea la bóveda para guardarlo. Lo que ves aquí se pierde si vuelves atrás, si cierras la app o si " +
                "pasas fuera de esta pantalla más tiempo que el de bloqueo de la bóveda (como mínimo, 1 minuto).",
            CODES_UNLOCK_TO_SAVE,
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
