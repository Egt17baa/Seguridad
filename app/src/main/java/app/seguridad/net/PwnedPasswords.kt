package app.seguridad.net

import app.seguridad.core.readAtMost
import app.seguridad.crypto.pwnedCount
import app.seguridad.crypto.sha1Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Maximum accepted body from the fixed Have I Been Pwned range endpoint (defence in depth). */
private const val MAX_PWNED_RESPONSE_BYTES = 2 * 1024 * 1024

/** Have I Been Pwned range API (k-anonymity): only the first 5 hex chars of the SHA-1 leave the device. */
class PwnedPasswords(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.pwnedpasswords.com/".toHttpUrl(),
) {
    suspend fun times(password: CharArray): Int {
        val hash = sha1Hex(password)
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("range").addPathSegment(hash.take(5)).build())
            .header("Add-Padding", "true")
            .build()
        val body = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) throw IOException("HIBP respondió ${response.code}")
                val bytes = response.body.byteStream().use { it.readAtMost(MAX_PWNED_RESPONSE_BYTES + 1) }
                if (bytes.size > MAX_PWNED_RESPONSE_BYTES) throw IOException("HIBP devolvió una respuesta demasiado grande")
                bytes.toString(Charsets.US_ASCII)
            }
        }
        return pwnedCount(body, hash.drop(5))
    }
}
