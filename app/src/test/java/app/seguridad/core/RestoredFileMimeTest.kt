package app.seguridad.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Prueba del dueño (1-11-2026): una foto descifrada se guardó como «copia-de-foto.jpg (1)» y no se podía abrir. El
 * proveedor de documentos solo separa la extensión si corresponde al tipo pedido; con `application/octet-stream` no
 * corresponde y, si ya hay un archivo con ese nombre, pone « (1)» detrás. Con el tipo de la extensión lo pone delante.
 */
class RestoredFileMimeTest {
    private val table = mapOf(
        "jpg" to "image/jpeg", "png" to "image/png", "heic" to "image/heic", "mp4" to "video/mp4", "m4a" to "audio/mp4",
        "pdf" to "application/pdf", "txt" to "text/plain", "html" to "text/html", "svg" to "image/svg+xml",
        "apk" to "application/vnd.android.package-archive", "zip" to "application/zip", "gif" to "image/gif",
        "abcdefgh" to "image/x-ocho", "abcdefghi" to "image/x-nueve", "extensionlarguisima" to "image/x-larga",
    )
    private fun mime(name: String, locale: Locale = Locale.ROOT) = restoredFileMime(name, locale) { table[it] }

    @Test fun mediaDocumentsAndTextKeepTheirType() {
        assertEquals("image/jpeg", mime("copia-de-foto.jpg"))
        assertEquals("image/jpeg", mime("FOTO.JPG"))
        assertEquals("image/png", mime("captura.png"))
        assertEquals("image/heic", mime("IMG_0001.heic"))
        assertEquals("video/mp4", mime("video.mp4"))
        assertEquals("audio/mp4", mime("nota de voz.m4a"))
        assertEquals("application/pdf", mime("contrato.v2.pdf"))
        assertEquals("text/plain", mime("notas.txt"))
        // At most 8 characters of extension.
        assertEquals("image/x-ocho", mime("a.abcdefgh"))
        // The phone's language does not change a lowercase extension, nor an upper-case one without «I».
        assertEquals("image/heic", mime("IMG_0001.heic", Locale("tr")))
        assertEquals("image/jpeg", mime("FOTO.JPG", Locale("tr")))
        assertEquals("image/gif", mime("ANIMADA.GIF", Locale("es")))
    }

    @Test fun anUpperCaseIInTurkishOrAzeriStaysPlainBytes() {
        // Android lowercases the extension with the phone's language («HEIC» → «heıc»): it would not match image/heic
        // and the provider would add a second extension («IMG.HEIC.heic»).
        assertEquals(OCTET_STREAM, mime("IMG_0001.HEIC", Locale("tr")))
        assertEquals(OCTET_STREAM, mime("ANIMADA.GIF", Locale("az")))
        assertEquals("image/heic", mime("IMG_0001.HEIC", Locale("es")))
    }

    @Test fun anythingElseStaysPlainBytes() {
        for (name in listOf(
            "sin-extension", "acaba-en-punto.", "programa.apk", "pagina.html", "dibujo.svg", "copia.zip",
            "copia.sgbak", "foto.jpg.sgenc", "rara.desconocida", "larga.extensionlarguisima", "nueve.abcdefghi",
        )) assertEquals(name, OCTET_STREAM, mime(name))
    }
}
