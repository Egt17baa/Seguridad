package app.seguridad.net

import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.time.Duration

/**
 * The only HTTP client of the app: system trust store minus the compromised eMudhra keys, no cache, no cookies.
 * [appVersion] is the installed version (BuildConfig.VERSION_NAME), sent as the User-Agent «Clavero/<version>» (the app's visible name since 1.3.0; before, «Seguridad»).
 */
fun secureClient(appVersion: String): OkHttpClient =
    OkHttpClient.Builder()
        .distrustCompromisedCas()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(15))
        .writeTimeout(Duration.ofSeconds(15))
        .callTimeout(Duration.ofSeconds(15))
        .cache(null as Cache?)
        .cookieJar(CookieJar.NO_COOKIES)
        // Only api.pwnedpasswords.com: a redirect (to another host, or to plain http) is never followed.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Clavero/$appVersion").build())
        }
        .build()
