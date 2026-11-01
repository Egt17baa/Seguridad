package app.seguridad.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba del dueño (1-11-2026): una foto descifrada se guardó como «copia-de-foto.jpg (1)» y no se podía abrir. Con el
 * tipo `application/octet-stream`, el proveedor de documentos no sabe dónde acaba el nombre y, si ya hay un archivo con
 * ese nombre, pone « (1)» detrás de la extensión. Con el tipo de la extensión lo pone delante: «copia-de-foto (1).jpg».
 */
class RestoredFileMimeTest {
    private val table = mapOf(
        "jpg" to "image/jpeg", "png" to "image/png", "heic" to "image/heic", "mp4" to "video/mp4", "m4a" to "audio/mp4",
        "pdf" to "application/pdf", "txt" to "text/plain", "html" to "text/html", "svg" to "image/svg+xml",
        "apk" to "application/vnd.android.package-archive", "zip" to "application/zip",
    )
    private fun mime(name: String) = restoredFileMime(name) { table[it] }

    @Test fun mediaDocumentsAndTextKeepTheirType() {
        assertEquals("image/jpeg", mime("copia-de-foto.jpg"))
        assertEquals("image/jpeg", mime("FOTO.JPG"))
        assertEquals("image/png", mime("captura.png"))
        assertEquals("image/heic", mime("IMG_0001.heic"))
        assertEquals("video/mp4", mime("video.mp4"))
        assertEquals("audio/mp4", mime("nota de voz.m4a"))
        assertEquals("application/pdf", mime("contrato.v2.pdf"))
        assertEquals("text/plain", mime("notas.txt"))
    }

    @Test fun anythingElseStaysPlainBytes() {
        for (name in listOf(
            "sin-extension", "acaba-en-punto.", "programa.apk", "pagina.html", "dibujo.svg", "copia.zip",
            "copia.sgbak", "foto.jpg.sgenc", "rara.desconocida", "larga.extensionlarguisima",
        )) assertEquals(name, OCTET_STREAM, mime(name))
    }
}
