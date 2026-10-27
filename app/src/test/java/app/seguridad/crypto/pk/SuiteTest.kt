package app.seguridad.crypto.pk

import org.bouncycastle.crypto.hpke.HPKE
import org.junit.Assert.assertEquals
import org.junit.Test

/** La suite 1 necesita X-Wing en HPKE: está en Bouncy Castle desde la 1.86. */
class SuiteTest {
    @Test fun xWingExiste() {
        assertEquals(0x647a, HPKE.kem_X_WING.toInt())
        assertEquals(1120, HPKE(HPKE.mode_base, HPKE.kem_X_WING, HPKE.kdf_HKDF_SHA256, HPKE.aead_CHACHA20_POLY1305).encSize)
    }
}
