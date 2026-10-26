package app.seguridad.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

private fun cert(name: String): X509Certificate {
    val file = listOf("src/test/resources/emudhra", "../app/src/test/resources/emudhra").map { File(it, name) }.first { it.exists() }
    return file.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
}

/** Stands in for the system: accepts every chain, so only the extra check can refuse. */
private object AcceptAll : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(cert("aaa-certificate-services.pem"), cert("emsign-ecc-root-ca-g3.pem"))
}

/** The real certificates from the eMudhra disclosure (emudhra.we-have-a-problem.com). */
class DistrustedCertificatesTest {
    private val compromised = cert("em-dv-tls-ca-g3b-cross.pem") // EM DV TLS CA - G3B, cross-signed by AAA Certificate Services.
    private val aaaRoot = cert("aaa-certificate-services.pem")
    private val emsignEcc = cert("emsign-ecc-root-ca-g3.pem")
    private val tm = DistrustingTrustManager(AcceptAll)

    private fun refused(vararg chain: X509Certificate) = try {
        tm.checkServerTrusted(arrayOf(*chain), "ECDHE_ECDSA")
        false
    } catch (e: CertificateException) {
        true
    }

    @Test fun `the published public key hash matches the certificate`() {
        assertEquals("EBED6475C2C2D80FCB199F3FE8DD6E6B3CE60B77EB15D4E42C3DEC0C6F5B5105", compromised.spkiSha256())
        assertEquals("8D417DB2DD8BF5E3084D1E3F196D583849D81BDD4C00C70B9D39369E96B8C782", emsignEcc.spkiSha256())
    }

    @Test fun `a chain through a compromised intermediate is refused even under a trusted root`() {
        assertTrue(refused(compromised, aaaRoot))
        assertTrue(refused(emsignEcc))
    }

    @Test fun `other chains are left to the system`() {
        assertFalse(refused(aaaRoot))
    }

    @Test fun `distrusted roots are not offered as issuers`() {
        assertEquals(listOf(aaaRoot), tm.acceptedIssuers.toList())
    }

    @Test fun `emSign G3 roots are recognised by name with or without the OU`() {
        for (cn in listOf("emSign ECC Root CA - G3", "emSign Root TLS CA - G3", "emSign Root CS CA - G3")) {
            assertTrue(X500Principal("CN=$cn,O=eMudhra Technologies Limited,C=IN").isDistrustedRootName())
            assertTrue(X500Principal("CN=$cn,O=eMudhra Technologies Limited,OU=emSign PKI,C=IN").isDistrustedRootName())
        }
        assertTrue(emsignEcc.subjectX500Principal.isDistrustedRootName())
    }

    @Test fun `other names are not matched`() {
        assertFalse(X500Principal("CN=emSign Root CA - G1,O=eMudhra Technologies Limited,OU=emSign PKI,C=IN").isDistrustedRootName())
        assertFalse(X500Principal("CN=emSign Root TLS CA - G3,O=Someone Else,C=IN").isDistrustedRootName())
        assertFalse(aaaRoot.subjectX500Principal.isDistrustedRootName())
    }

    @Test fun `the app's HTTP client uses the distrusting trust manager`() {
        val client = secureClient(appVersion = "test")
        assertTrue(client.x509TrustManager is DistrustingTrustManager)
        try {
            client.x509TrustManager!!.checkServerTrusted(arrayOf(compromised, aaaRoot), "ECDHE_ECDSA")
            fail()
        } catch (e: CertificateException) {
            // expected: refused by the system (unknown chain) or by the eMudhra check
        }
    }
}
