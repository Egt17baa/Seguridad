package app.seguridad.net

import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class HttpClientTest {
    // The APK leaves out OkHttp's Public Suffix List (androidResources and checkAssets in app/build.gradle.kts). OkHttp
    // reads it to parse cookies, so a cookie jar here would need it back, with its MPL-2.0 notice.
    @Test fun noCookies() {
        assertSame(CookieJar.NO_COOKIES, secureClient(appVersion = "1.0").cookieJar)
    }

    @Test fun noCache() {
        assertNull(secureClient(appVersion = "1.0").cache)
    }

    @Test fun noRedirects() {
        val client = secureClient(appVersion = "1.0")
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    // CRY-B01: on the JVM nothing is passed, and the client is the one of 1.3.0: the system's trust manager under the
    // eMudhra check, with the same timeouts.
    @Test fun withoutAValidatedChainTheClientIsBuiltAsBefore() {
        val client = secureClient(appVersion = "1.0")
        val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().single()
        val trust = client.x509TrustManager
        assertTrue(trust is DistrustingTrustManager)
        assertEquals(system.acceptedIssuers.filterNot { it.isDistrusted() }.toSet(), trust!!.acceptedIssuers.toSet())
        assertEquals(listOf(15_000, 15_000, 15_000, 15_000), with(client) { listOf(connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis, callTimeoutMillis) })
    }

    // CRY-B01: on Android, the function gets the system's own trust manager (not the eMudhra check), once, when the client
    // is built, and the eMudhra check of the client uses the chain it makes.
    @Test fun theValidatedChainComesFromTheSystemTrustManager() {
        val given = mutableListOf<X509TrustManager>()
        val validated: TrustedChain = { chain, _, _ -> chain.toList() }
        val client = secureClient(appVersion = "1.0") { system -> given += system; validated }
        assertEquals(1, given.size)
        assertFalse(given.single() is DistrustingTrustManager)
        assertSame(validated, (client.x509TrustManager as DistrustingTrustManager).trustedChain)
    }
}
