package app.seguridad.net

import okhttp3.CookieJar
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HttpClientTest {
    // The APK leaves out OkHttp's Public Suffix List (androidResources and checkAssets in app/build.gradle.kts). OkHttp
    // reads it to parse cookies, so a cookie jar here would need it back, with its MPL-2.0 notice.
    @Test fun noCookies() {
        assertSame(CookieJar.NO_COOKIES, secureClient(appVersion = "1.0").cookieJar)
    }

    @Test fun noCache() {
        assertNull(secureClient(appVersion = "1.0").cache)
    }
}
