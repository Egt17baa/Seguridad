package app.seguridad.tools

import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.totpCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, task 6 (design «Copiar usuario y luego contraseña»): after «Copiar usuario» the app offers «Copiar contraseña»
 * for 30 s. It keeps only the entry's id: the password is read from the open vault at the tap, never kept here.
 */
class TwoStepCopyTest {
    private var clock = 1_000_000L
    private var epoch = 0

    @Test fun offersFor30s() {
        val t = TwoStepCopy(clock = { clock }, lockEpoch = { epoch })
        assertNull(t.pending())
        t.offerPassword("entrada-1")
        assertEquals("entrada-1", t.pending())
        clock += 29_999
        assertEquals("entrada-1", t.pending())
        assertEquals(1L, t.leftMs())
        clock += 1
        assertNull(t.pending())
        assertEquals(0L, t.leftMs())
        // Once gone, it stays gone even if the clock went back.
        clock -= 10_000
        assertNull(t.pending())
        // A newer «Copiar usuario» replaces the older offer and starts its 30 s again.
        t.offerPassword("entrada-1")
        clock += 20_000
        t.offerPassword("entrada-2")
        clock += 20_000
        assertEquals("entrada-2", t.pending())
        // «Copiar contraseña» (or any other copy) ends it.
        t.clear()
        assertNull(t.pending())
        assertEquals(30_000L, TWO_STEP_COPY_MS)
    }

    @Test fun aClockGoingBackEndsTheOffer() {
        val t = TwoStepCopy(clock = { clock })
        t.offerPassword("e")
        clock -= 1
        assertNull(t.pending())
    }

    /** It is handed an id and nothing else, and keeps nothing else: no field can hold a password or an entry. */
    @Test fun neverHoldsPassword() {
        val password = "contraseña-que-nunca-se-guarda"
        val t = TwoStepCopy(clock = { clock }, lockEpoch = { epoch })
        t.offerPassword("entrada-1")
        val allowed = setOf(String::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Function0::class.java, Any::class.java, kotlinx.coroutines.flow.MutableStateFlow::class.java, kotlinx.coroutines.flow.StateFlow::class.java)
        var offer: Any? = null
        for (f in TwoStepCopy::class.java.declaredFields) {
            f.isAccessible = true
            val v = f.get(t)
            if (f.type.name.startsWith("kotlinx.coroutines.flow.")) {
                // The counter of changes: a number, never the entry nor its password.
                assertTrue(f.name, (v as kotlinx.coroutines.flow.StateFlow<*>).value is Long)
                continue
            }
            if (f.name == "offer") {
                offer = v
                continue
            }
            assertTrue("${f.name}: ${f.type}", f.type in allowed || f.type.name.startsWith("kotlin.jvm.functions."))
            assertFalse(f.name, v?.toString()?.contains(password) == true)
        }
        // The offer itself: the id, the time and the lock counter, nothing more.
        val o = offer ?: error("no offer")
        val kept = o.javaClass.declaredFields.onEach { it.isAccessible = true }.associate { it.name to it.type }
        assertEquals(setOf(String::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType), kept.values.toSet())
        assertEquals(3, kept.size)
        // The only way in takes a String id: there is no overload with an entry or a password.
        val ins = TwoStepCopy::class.java.declaredMethods.filter { it.name.startsWith("offer") }
        assertEquals(1, ins.size)
        assertEquals(listOf(String::class.java), ins.single().parameterTypes.toList())
    }

    @Test fun clearedOnLock() {
        val t = TwoStepCopy(clock = { clock }, lockEpoch = { epoch })
        t.offerPassword("entrada-1")
        // A lock (or a switch of vault) since the offer: nothing to offer, even after the vault opens again.
        epoch++
        assertNull(t.pending())
        t.offerPassword("entrada-1")
        assertEquals("entrada-1", t.pending())
        t.clear() // What the app does at every lock, too.
        assertNull(t.pending())
    }

    /**
     * Review m-1 of task 6: «al volver a la app» is coming back from outside it. The offer shows once, on the first
     * return after the app was left within its 30 s; never on a screen composed or reached inside the app.
     */
    @Test fun offeredOnlyOnReturnFromOutside() {
        val t = TwoStepCopy(clock = { clock }, lockEpoch = { epoch })
        t.offerPassword("entrada-1")
        assertNull("not left: no offer on screen", t.takeReturnOffer())
        assertEquals("but it is still valid for «Copiar contraseña»", "entrada-1", t.pending())
        t.markLeft()
        clock += 10_000
        assertEquals("entrada-1", t.takeReturnOffer())
        // Once per return: the next screen of the app does not offer it again.
        assertNull(t.takeReturnOffer())
        assertEquals("entrada-1", t.pending())
        // Another return within the 30 s offers it again.
        t.markLeft()
        assertEquals("entrada-1", t.takeReturnOffer())
        // Left after the 30 s: nothing.
        t.markLeft()
        clock += 20_000
        assertNull(t.takeReturnOffer())
        // Left with no offer: a later offer is not one «after leaving».
        t.markLeft()
        t.offerPassword("entrada-2")
        assertNull(t.takeReturnOffer())
    }

    /** Review m-2 of task 6: every start, end or return of the offer is told, so the screen takes it off when it ends. */
    @Test fun changesTellTheScreen() {
        val t = TwoStepCopy(clock = { clock }, lockEpoch = { epoch })
        val start = t.changes.value
        t.offerPassword("e")
        val offered = t.changes.value
        t.markLeft()
        val left = t.changes.value
        t.clear()
        val cleared = t.changes.value
        assertTrue(start != offered && offered != left && left != cleared)
        // Clearing nothing is no change.
        t.clear()
        assertEquals(cleared, t.changes.value)
    }

    // 2FA in the row: tapping the code copies it; with less than 5 s left, the next one, and it says so.

    private val rfc = TotpSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ") // RFC 6238's key, in Base32.

    @Test fun theRowCopiesTheCodeOnScreen() {
        val at = 1_700_000_010_000L // 20 s left in its 30 s step.
        val c = rowCodeToCopy(rfc, at)!!
        assertEquals(totpCode(rfc, at)!!.first, c.code)
        assertFalse(c.next)
    }

    @Test fun withLessThan5sTheRowCopiesTheNextCode() {
        val start = 1_699_999_980_000L // A step starts here (a multiple of 30 s).
        for (left in 1..4) {
            val at = start + (30 - left) * 1_000L
            assertEquals(left, totpCode(rfc, at)!!.second)
            val c = rowCodeToCopy(rfc, at)!!
            assertTrue(c.next)
            assertEquals(totpCode(rfc, start + 30_000)!!.first, c.code)
        }
        // 5 s left is not less than 5: the code on screen.
        val five = start + 25_000
        assertEquals(5, totpCode(rfc, five)!!.second)
        assertFalse(rowCodeToCopy(rfc, five)!!.next)
        assertEquals(5, ROW_NEXT_CODE_BELOW_S)
    }

    @Test fun anUnsupportedCodeCopiesNothing() {
        assertNull(rowCodeToCopy(rfc.copy(digits = 9), 1_700_000_000_000L))
        assertNull(rowCodeToCopy(TotpSecret("no es base32 !!"), 1_700_000_000_000L))
    }
}
