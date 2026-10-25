package app.seguridad.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PwnedPasswordsTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PwnedPasswords

    // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
    private val suffix = "1E4C9B93F3F0682250B6CF8331B7EE68FD8"

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = PwnedPasswords(secureClient(), server.url("/"))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun sendsOnlyFivePrefixChars() = runTest {
        server.enqueue(MockResponse().setBody("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n"))
        api.times("password".toCharArray())
        val req = server.takeRequest()
        val path = req.path!!
        assertTrue(path, Regex("/range/[0-9A-F]{5}").matches(path))
        assertEquals("/range/5BAA6", path)
        assertEquals("true", req.getHeader("Add-Padding"))
        assertEquals("Seguridad/1.0.0", req.getHeader("User-Agent"))
        assertFalse(req.requestLine.contains("password"))
        assertFalse(req.requestLine.contains(suffix))
        assertEquals(0, req.bodySize)
    }

    @Test fun parsesCount() = runTest {
        server.enqueue(MockResponse().setBody("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n$suffix:3861493\r\n"))
        assertEquals(3861493, api.times("password".toCharArray()))
    }

    @Test fun paddingIsZero() = runTest {
        server.enqueue(MockResponse().setBody("$suffix:0\r\n"))
        assertEquals(0, api.times("password".toCharArray()))
    }

    @Test fun oversizedResponseThrows() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(2 * 1024 * 1024 + 1)))
        try {
            api.times("password".toCharArray())
            fail("oversized response must be rejected")
        } catch (e: IOException) {
            // A hostile or broken response cannot allocate unbounded memory.
        }
    }

    @Test fun non200Throws() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            api.times("password".toCharArray())
            fail()
        } catch (e: IOException) {
            // expected
        }
    }
}
