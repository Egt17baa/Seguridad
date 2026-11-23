package app.seguridad.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 2.1, task 2: what an image read gives is never shown by its toString (a wifi's password, a 2FA key). */
class QrImageReadTest {
    @Test fun theTextIsNotInToString() {
        val read = QrImageRead.Text("WIFI:T:WPA;S:RedSecreta;P:clave-muy-secreta;;")
        assertFalse(read.toString(), "clave-muy-secreta" in read.toString() || "RedSecreta" in read.toString())
        assertEquals("WIFI:T:WPA;S:RedSecreta;P:clave-muy-secreta;;", read.text)
    }
}
