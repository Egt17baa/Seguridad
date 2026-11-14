package app.seguridad.vault

import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.qr.WIFI_QR_NOT_ADDED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, task 9: «Añadir a la bóveda» of a wifi QR goes through the entry form (task 7), filled from the code and never
 * saved by itself: the form's «Guardar» saves it, with its own checks and the `sinceLock` of that moment. What the code
 * brought lives only in the form's session, so a lock, leaving or a cleared ViewModel take the password with it.
 */
class WifiQrFormTest {
    private fun session() = EntryEditSession(PlainCells, null, EntryType.WIFI, newId = "nuevo-1").also { it.load(emptyList()) }

    private fun EntryEditSession.value(key: String) = templateValue(EntryType.WIFI, key)

    @Test fun theCodeFillsTheFormAndNothingIsSaved() {
        val s = session()
        assertFalse(s.hasUnsavedChanges)
        val text = s.takeHandOver("WIFI:T:WEP;S:Casa;P:clave;H:true;;")!!
        s.applyWifiQr(text)
        assertEquals(EntryType.WIFI, s.type.value)
        assertEquals("Casa", s.value(WIFI_SSID_KEY))
        assertEquals("clave", s.value(WIFI_PASSWORD_KEY))
        assertEquals("WEP", s.value(WIFI_SECURITY_KEY))
        assertEquals("true", s.value(WIFI_HIDDEN_KEY))
        // The name of the network, as the title, only when there is none.
        assertEquals("Casa", s.title.value)
        assertNull(s.error.value)
        // Changes on the form: «Atrás» asks before dropping them (task 7), and only «Guardar» keeps them.
        assertTrue(s.hasUnsavedChanges)
        assertFalse(s.finished.value)
        // What «Guardar» would keep: the four fields of the template, the password hidden.
        val fields = (fieldsToSave(EntryType.WIFI, s.templateValues.value, s.customFields.value) as FormFields.Ok).fields
        assertEquals(listOf("Casa", "clave", "WEP", "true"), Templates.fieldsFor(EntryType.WIFI).map { t -> fields.first { it.id == t.key }.value })
        assertEquals(FieldKind.HIDDEN, fields.first { it.id == WIFI_PASSWORD_KEY }.kind)
    }

    @Test fun aTitleAlreadyTypedStays() {
        val s = session()
        s.title.value = "Wifi de casa"
        s.applyWifiQr("WIFI:T:WPA;S:Casa;P:clave;;")
        assertEquals("Wifi de casa", s.title.value)
        assertEquals("false", s.value(WIFI_HIDDEN_KEY))
    }

    @Test fun anOpenNetworkLeavesThePasswordEmpty() {
        val s = session()
        s.applyWifiQr("WIFI:T:nopass;S:Bar;;")
        assertEquals("nopass", s.value(WIFI_SECURITY_KEY))
        assertEquals("", s.value(WIFI_PASSWORD_KEY))
    }

    @Test fun aCodeTheFormCannotHoldFillsNothing() {
        // The inspector does not offer these; if one ever came, the form says so and keeps what it had.
        for (t in listOf("WIFI:T:WPA2;S:Casa;P:clave;;", "WIFI:S:a;S:b;;", "hola", "WIFI:T:WPA;S:Casa;P:clave\\")) {
            val s = session()
            s.applyWifiQr(t)
            assertEquals(t, WIFI_QR_NOT_ADDED, s.error.value)
            assertEquals(t, "", s.value(WIFI_SSID_KEY))
            assertEquals(t, "", s.value(WIFI_PASSWORD_KEY))
            assertEquals(t, "", s.title.value)
            // The defaults of a new wifi stay: WPA, not hidden.
            assertEquals(t, "WPA", s.value(WIFI_SECURITY_KEY))
        }
    }

    @Test fun theCodeIsAppliedOnceAndGoesWithALock() {
        val s = session()
        s.applyWifiQr(s.takeHandOver("WIFI:T:WPA;S:Casa;P:clave;;")!!)
        // A rotation composes the form again: not handed over a second time.
        assertNull(s.takeHandOver("WIFI:T:WPA;S:Casa;P:clave;;"))
        // A lock clears everything the code brought, the password included.
        s.vaultStateIs(unlocked = false, emptyList())
        assertTrue(s.templateValues.value.isEmpty())
        assertEquals("", s.title.value)
        // The next unlock loads a new, empty wifi; the code is not applied again.
        s.vaultStateIs(unlocked = true, emptyList())
        assertEquals("", s.value(WIFI_PASSWORD_KEY))
        assertNull(s.takeHandOver("WIFI:T:WPA;S:Casa;P:clave;;"))
    }

    @Test fun leavingWithoutARotationLetsThePasswordGo() {
        val s = session()
        s.applyWifiQr("WIFI:T:WPA;S:Casa;P:clave;;")
        s.left(changingConfigurations = true)
        assertEquals("clave", s.value(WIFI_PASSWORD_KEY))
        s.left(changingConfigurations = false)
        assertTrue(s.templateValues.value.isEmpty())
    }

    @Test fun anExistingEntryOfAnotherTypeIsNeverTurnedIntoAWifi() {
        val login = VaultEntry("e1", EntryType.LOGIN, "Banco", password = "x", createdAt = 1, updatedAt = 1)
        val s = EntryEditSession(PlainCells, "e1", EntryType.LOGIN, newId = "n").also { it.load(listOf(login)) }
        s.applyWifiQr("WIFI:T:WPA;S:Casa;P:clave;;")
        assertEquals(EntryType.LOGIN, s.type.value)
        assertEquals(WIFI_QR_NOT_ADDED, s.error.value)
        assertTrue(s.templateValues.value.isEmpty())
    }
}
