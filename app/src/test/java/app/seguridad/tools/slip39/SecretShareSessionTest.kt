package app.seguridad.tools.slip39

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretShareSessionTest {
    @Test fun wipeForgetsEverySecretAndZeroesTheRecoveredBytes() {
        var stopped = 0
        val s = SecretShareSession(onWipe = { stopped++ })
        s.secretInput = "abandon abandon"
        s.passphrase = "clave"
        s.typedParts = "academic acid"
        s.parts = listOf(listOf("a b c"))
        s.error = "x"
        s.busy = true
        val recovered = ByteArray(16) { 7 }
        s.recovered = recovered
        val token = s.token()
        s.wipe()
        assertEquals(1, stopped)
        assertFalse(s.isCurrent(token))
        assertEquals("", s.secretInput)
        assertEquals("", s.passphrase)
        assertEquals("", s.typedParts)
        assertNull(s.parts)
        assertNull(s.recovered)
        assertNull(s.error)
        assertFalse(s.busy)
        assertArrayEquals(ByteArray(16), recovered)
    }

    @Test fun aNewRecoveredSecretZeroesTheOldOne() {
        val s = SecretShareSession()
        val first = ByteArray(16) { 1 }
        s.recovered = first
        s.recovered = ByteArray(16) { 2 }
        assertArrayEquals(ByteArray(16), first)
        s.recovered = null
        assertTrue(s.recovered == null)
    }

    @Test fun theSchemeIsNotASecretAndStays() {
        val s = SecretShareSession()
        s.groupThreshold = "2"
        s.groups = listOf(GroupForm("2", "3"), GroupForm("1", "1"))
        s.wipe()
        assertEquals("2", s.groupThreshold)
        assertEquals(2, s.groups.size)
    }
}
