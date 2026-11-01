package app.seguridad.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.net.Socket
import java.nio.ByteBuffer
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
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

/** Stands in for the system (Android's is an X509ExtendedTrustManager) and notes whether it was asked anything. */
private class SpyExtended : X509ExtendedTrustManager() {
    var called = false
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) { called = true }
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) { called = true }
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) { called = true }
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) = Unit
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** The three ways the TLS stack asks about a server, with no socket and no engine for the last two. */
private val overloads = listOf<(X509TrustManager, Array<X509Certificate>) -> Unit>(
    { tm, chain -> tm.checkServerTrusted(chain, "ECDHE_ECDSA") },
    { tm, chain -> (tm as X509ExtendedTrustManager).checkServerTrusted(chain, "ECDHE_ECDSA", null as Socket?) },
    { tm, chain -> (tm as X509ExtendedTrustManager).checkServerTrusted(chain, "ECDHE_ECDSA", null as SSLEngine?) },
)

/** The session of a handshake in progress: only the server's name is known. */
private fun handshakeSession(peerHost: String): SSLSession =
    Proxy.newProxyInstance(SSLSession::class.java.classLoader, arrayOf(SSLSession::class.java)) { _, method, _ ->
        if (method.name == "getPeerHost") peerHost else throw UnsupportedOperationException(method.name)
    } as SSLSession

/** An engine in the middle of a handshake: it only answers [getHandshakeSession]. */
private class HandshakingEngine(private val session: SSLSession) : SSLEngine() {
    override fun getHandshakeSession(): SSLSession = session
    override fun wrap(srcs: Array<out ByteBuffer>, offset: Int, length: Int, dst: ByteBuffer): SSLEngineResult = unused()
    override fun unwrap(src: ByteBuffer, dsts: Array<out ByteBuffer>, offset: Int, length: Int): SSLEngineResult = unused()
    override fun getDelegatedTask(): Runnable = unused()
    override fun closeInbound(): Unit = unused()
    override fun isInboundDone(): Boolean = unused()
    override fun closeOutbound(): Unit = unused()
    override fun isOutboundDone(): Boolean = unused()
    override fun getSupportedCipherSuites(): Array<String> = unused()
    override fun getEnabledCipherSuites(): Array<String> = unused()
    override fun setEnabledCipherSuites(suites: Array<out String>): Unit = unused()
    override fun getSupportedProtocols(): Array<String> = unused()
    override fun getEnabledProtocols(): Array<String> = unused()
    override fun setEnabledProtocols(protocols: Array<out String>): Unit = unused()
    override fun getSession(): SSLSession = unused()
    override fun beginHandshake(): Unit = unused()
    override fun getHandshakeStatus(): SSLEngineResult.HandshakeStatus = unused()
    override fun setUseClientMode(mode: Boolean): Unit = unused()
    override fun getUseClientMode(): Boolean = unused()
    override fun setNeedClientAuth(need: Boolean): Unit = unused()
    override fun getNeedClientAuth(): Boolean = unused()
    override fun setWantClientAuth(want: Boolean): Unit = unused()
    override fun getWantClientAuth(): Boolean = unused()
    override fun setEnableSessionCreation(flag: Boolean): Unit = unused()
    override fun getEnableSessionCreation(): Boolean = unused()
}

/** A socket in the middle of a handshake (the way OkHttp connects on Android): it only answers [getHandshakeSession]. */
private class HandshakingSocket(private val session: SSLSession) : SSLSocket() {
    override fun getHandshakeSession(): SSLSession = session
    override fun getSupportedCipherSuites(): Array<String> = unused()
    override fun getEnabledCipherSuites(): Array<String> = unused()
    override fun setEnabledCipherSuites(suites: Array<out String>): Unit = unused()
    override fun getSupportedProtocols(): Array<String> = unused()
    override fun getEnabledProtocols(): Array<String> = unused()
    override fun setEnabledProtocols(protocols: Array<out String>): Unit = unused()
    override fun getSession(): SSLSession = unused()
    override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener): Unit = unused()
    override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener): Unit = unused()
    override fun startHandshake(): Unit = unused()
    override fun setUseClientMode(mode: Boolean): Unit = unused()
    override fun getUseClientMode(): Boolean = unused()
    override fun setNeedClientAuth(need: Boolean): Unit = unused()
    override fun getNeedClientAuth(): Boolean = unused()
    override fun setWantClientAuth(want: Boolean): Unit = unused()
    override fun getWantClientAuth(): Boolean = unused()
    override fun setEnableSessionCreation(flag: Boolean): Unit = unused()
    override fun getEnableSessionCreation(): Boolean = unused()
}

private fun unused(): Nothing = throw UnsupportedOperationException("not used by the trust manager")

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

    // CRY-B01: Android's trust manager (Conscrypt's TrustManagerImpl) completes a chain that lacks its intermediate with
    // the intermediates it cached from earlier chains, so what it validated can hold a key the server never sent.
    @Test fun `a chain completed from the system's cache is refused`() {
        val completed = DistrustingTrustManager(AcceptAll) { c, _, _ -> c.toList() + compromised + aaaRoot }
        for (check in overloads) {
            assertThrows(CertificateException::class.java) { check(completed, arrayOf(aaaRoot)) }
        }
    }

    // CRY-B01: refused before the system validates it, so the system never caches the compromised intermediate.
    @Test fun `the system never sees a chain with a compromised key`() {
        for (check in overloads) {
            val extended = SpyExtended()
            var plainCalled = false
            val plain = object : X509TrustManager by AcceptAll {
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) { plainCalled = true }
            }
            assertThrows(CertificateException::class.java) { check(DistrustingTrustManager(extended), arrayOf(compromised, aaaRoot)) }
            assertThrows(CertificateException::class.java) { check(DistrustingTrustManager(plain), arrayOf(compromised, aaaRoot)) }
            assertFalse(extended.called)
            assertFalse(plainCalled)
        }
    }

    // CRY-B01: Android needs the server's name to give the chain it validated; it comes from the handshake in progress.
    @Test fun `the validated chain gets the host`() {
        val asked = mutableListOf<Triple<List<X509Certificate>, String, String?>>()
        val tm = DistrustingTrustManager(AcceptAll) { c, authType, host -> asked += Triple(c.toList(), authType, host); c.toList() }
        val session = handshakeSession("api.pwnedpasswords.com")
        tm.checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA", HandshakingEngine(session))
        tm.checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA", null as SSLEngine?)
        tm.checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA", HandshakingSocket(session))
        tm.checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA", null as Socket?)
        tm.checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA")
        val hosts = listOf("api.pwnedpasswords.com", null, "api.pwnedpasswords.com", null, null)
        assertEquals(hosts.map { Triple(listOf(aaaRoot), "ECDHE_ECDSA", it) }, asked)
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
