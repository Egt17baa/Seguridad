package app.seguridad.tools

import app.seguridad.vault.AUTO_BACKUP_OLD_NOT_DELETED
import app.seguridad.vault.AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY
import app.seguridad.vault.AUTO_BACKUP_REPLACED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The final review of the Abogado Digital (31-10-2026, `abogado-revision-final.md`): its required changes (O-1 to O-8)
 * and recommended ones (R-1 to R-14), as applied after the technical check, with the owner's decisions of 31-10-2026
 * (Data safety option A, developer name «Egt17baa», no donations ever, France, Russia and Belarus left out).
 */
class FinalLegalReviewTest {
    private fun repo(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/java").isDirectory && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun read(path: String) = File(repo(), path).readText()

    /** One line per run of spacing, so a sentence wrapped in the source still matches. */
    private fun flat(text: String) = text.replace(Regex("\\s+"), " ")

    private fun policy() = flat(
        read("docs/privacidad.html").replace(Regex("<style[\\s\\S]*?</style>"), " ").replace(Regex("<[^>]+>"), "")
            .replace("&laquo;", "«").replace("&raquo;", "»").replace("&amp;", "&"),
    )

    private fun literals(path: String) = kotlinLiterals(read(path)).map { it.second }

    @Test
    fun o1ThePolicySaysOnlyTheListedCopiesAreDeleted() {
        val p = policy()
        assertTrue(
            p,
            ("Puedes pedir que se haga una copia nueva y se borren las copias automáticas de antes del cambio que la app " +
                "tiene apuntadas en esa carpeta. No se borran las que no tenga apuntadas (por ejemplo, las de antes de la " +
                "versión 1.2.1 o las de otra bóveda) ni las que se hayan copiado a otro sitio (otra carpeta, la nube o su " +
                "papelera).") in p,
        )
        assertFalse("que la app hizo en esa carpeta" in p)
    }

    @Test
    fun o2AndR6TheA6TextsAreTheProposedOnes() {
        assertEquals(
            "Se hizo una copia nueva y se borraron las copias automáticas de antes del cambio de contraseña: las que la app " +
                "tenía apuntadas en esta carpeta. Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) " +
                "no se borran.",
            AUTO_BACKUP_REPLACED,
        )
        assertEquals(
            "La copia automática se hizo, pero no se pudieron borrar las antiguas. Se volverá a intentar en la próxima copia.",
            AUTO_BACKUP_OLD_NOT_DELETED,
        )
        // Where nothing tries again (the request was dropped, or there is none saved): the sentence without the promise.
        assertEquals("La copia automática se hizo, pero no se pudieron borrar las antiguas.", AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY)
        assertTrue(ALL_TOOL_TEXTS.containsAll(listOf(AUTO_BACKUP_OLD_NOT_DELETED, AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY)))
    }

    @Test
    fun o3ToO8ThePlayConsoleStepsAreInPublicar() {
        val pub = flat(read("docs/PUBLICAR.md"))
        // O-3, owner's option A.
        assertTrue("Responde **«No»** a «¿Tu app recoge o comparte alguno de los tipos de datos de usuario obligatorios?»" in pub)
        assertFalse("tratamiento efímero" in pub)
        // O-4: no encryption form in Play.
        assertTrue("Play no tiene un formulario de cifrado (a diferencia del App Store)" in pub)
        assertFalse("En el formulario de exportación de Play" in pub)
        // O-5 and the owner's decision on Russia and Belarus.
        for (place in listOf("Guadalupe", "Martinica", "Guayana Francesa", "Reunión", "Mayotte", "San Pedro y Miquelón", "San Bartolomé",
            "San Martín", "Polinesia Francesa", "Nueva Caledonia", "Wallis y Futuna", "Rusia", "Bielorrusia")) {
            assertTrue(place, place in pub)
        }
        // O-6.
        assertTrue("**Estado de comerciante (DSA):** declara **«no comerciante»**" in pub)
        assertTrue("1-2-2027" in pub)
        // O-7.
        assertTrue("actualiza la fecha de la política el día que se publique y publica `docs/privacidad.html` en GitHub Pages antes de enviar la app a revisión" in pub)
        assertTrue("Última actualización: 31 de octubre de 2026." in policy())
        // O-8: the developer name is the policy's.
        assertTrue("**Nombre de desarrollador:** «Egt17baa»" in pub)
        // The owner's open actions.
        assertTrue("Acciones pendientes del dueño" in pub)
    }

    @Test
    fun r1AndR2ThePolicyNamesHibpsOperatorAndKeepsNothing() {
        val p = policy()
        assertTrue(
            ("Have I Been Pwned lo gestiona Superlative Enterprises Pty Ltd, en Australia, fuera de la Unión Europea, y usa " +
                "Cloudflare para atender las consultas; tu proveedor de red y cada uno de ellos lo tratan según su propia " +
                "política (la de Have I Been Pwned: haveibeenpwned.com/Privacy). Nosotros no recibimos nada de esa consulta.") in p,
        )
        assertTrue("tu identidad ni tus contactos. Como no recibimos datos, no conservamos ninguno." in p)
    }

    @Test
    fun r3ToR5TheToolsSummaryIsTheProposedOne() {
        val texts = literals("app/src/main/java/app/seguridad/ui/screens/ToolsScreen.kt")
        assertTrue(
            "El cifrado y el análisis de las imágenes se hacen en tu móvil. Solo «¿Filtrada?» y «Comprobar filtraciones» usan " +
                "internet, cuando las pulsas, y solo envían 5 caracteres de la huella de cada contraseña." in texts,
        )
        assertTrue("Cifrado autenticado (AES-256-GCM, ChaCha20-Poly1305 y otros): si lo cifrado se cambia, se detecta al descifrarlo." in texts)
        val src = flat(read("app/src/main/java/app/seguridad/ui/screens/ToolsScreen.kt"))
        assertTrue(
            src,
            "\"Tu contraseña se convierte en clave con Argon2id (\${settings.kdf.label}), que hace muy lento cada intento de " +
                "adivinarla por fuerza bruta. Una contraseña corta sigue siendo débil.\"" in src,
        )
        assertFalse(texts.any { it.startsWith("Todo se hace en tu móvil") })
    }

    @Test
    fun r7TheListingNamesNoMessagingBrand() {
        val listing = read("docs/ficha-google-play.md").substringAfter("## Descripción breve")
        assertFalse("WhatsApp" in listing || "Telegram" in listing)
        assertTrue("envíalo por el canal que quieras (una app de mensajería o el correo)" in listing)
    }

    @Test
    fun r8ToR10TheLicencesAndTheName() {
        val licenses = read("app/src/main/java/app/seguridad/ui/screens/LicensesScreen.kt")
        assertTrue("AndroidX (Core, Activity, Fragment, Lifecycle, Navigation, Biometric y otras bibliotecas de AndroidX)" in licenses)
        assertTrue("\"Guava ListenableFuture, JetBrains Annotations y JSpecify\", \"Apache-2.0\"" in licenses)
        val notice = flat(read("NOTICE"))
        assertTrue(
            notice,
            ("- ZXing (Apache-2.0). Its NOTICE file includes: \"Barcode4J Copyright 2002-2010 Jeremias Märki, Copyright " +
                "2005-2006 Dietmar Bürkle. Portions of this software were contributed under section 5 of the Apache License.\"") in notice,
        )
        val readme = flat(read("README.md"))
        assertTrue(
            ("**Nombre.** La licencia Apache-2.0 no da permiso para usar el nombre «Clavero» ni el icono (§6). Si publicas " +
                "una versión modificada, usa otro nombre y otro icono, para que nadie la confunda con esta.") in readme,
        )
        assertFalse("®" in readme)
    }

    @Test
    fun r11SecurityGivesTheProjectAddressAndNoOther() {
        val sec = read("SECURITY.md")
        assertTrue("Si no aparece, escribe a seguridad.egt17baa@gmail.com antes de publicar los detalles." in flat(sec))
        assertTrue("Private vulnerability reporting" in sec)
        val mails = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+").findAll(sec).map { it.value }.toSet()
        assertEquals(setOf("seguridad.egt17baa@gmail.com"), mails)
    }

    @Test
    fun r12AndDonationsInTheReadme() {
        val readme = flat(read("README.md"))
        assertTrue("Comprobado el 31-10-2026 en la revisión final del Abogado Digital: el SHA-256 coincide y `cmp` no escribe nada." in readme)
        // Owner's decision (P-6): never donations, as the policy says.
        assertFalse("donaciones que condicionen" in readme)
        assertTrue("versión de pago, donaciones ni uso de datos con fines comerciales" in readme)
    }

    @Test
    fun r13NoButtonSaysCameraToTalkBack() {
        assertEquals("Escanear QR", QR_SCAN)
        val java = File(repo(), "app/src/main/java")
        val bad = java.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> kotlinLiterals(f.readText()).filter { "📷 " in it.second }.map { "${f.name}:${it.first}" } }.toList()
        assertTrue(bad.toString(), bad.isEmpty())
    }

    @Test
    fun r14TheCreateTextSaysCryptocurrencyWallets() {
        assertTrue("frases semilla de tus carteras de criptomonedas" in VAULT_CREATE_INTRO)
        assertFalse("wallet" in VAULT_CREATE_INTRO.lowercase())
    }

    // R-5 of the earlier dictamen: with no identity, the advice on passwords and 2FA stays, without «renueva tu identidad».
    @Test
    fun theAdviceWithoutIdentityIsTheSameSentenceWithoutTheRenewal() {
        assertEquals(
            "Si temes que alguien tenga la contraseña anterior y una copia, con las dos puede ver todo lo que guardabas al " +
                "hacerla: cambia las contraseñas y los códigos 2FA que más te importen.",
            PASSWORD_CHANGE_SECRETS_HINT,
        )
        assertEquals(PASSWORD_CHANGE_SECRETS_HINT.removeSuffix(".") + ", y renueva tu identidad.", PASSWORD_CHANGE_RENEW_HINT)
        assertTrue(PASSWORD_CHANGE_SECRETS_HINT in ALL_TOOL_TEXTS)
    }
}
