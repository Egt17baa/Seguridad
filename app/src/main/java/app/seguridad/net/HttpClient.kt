package app.seguridad.net

import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.time.Duration

/** The only HTTP client of the app: system trust store minus the compromised eMudhra keys, no cache, no cookies. */
fun secureClient(): OkHttpClient =
    OkHttpClient.Builder()
        .distrustCompromisedCas()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(15))
        .writeTimeout(Duration.ofSeconds(15))
        .callTimeout(Duration.ofSeconds(15))
        .cache(null as Cache?)
        .cookieJar(CookieJar.NO_COOKIES)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Seguridad/1.0.0").build())
        }
        .build()
