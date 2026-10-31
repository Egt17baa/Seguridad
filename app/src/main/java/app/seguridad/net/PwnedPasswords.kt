package app.seguridad.net

import app.seguridad.core.readAtMost
import app.seguridad.crypto.pwnedCount
import app.seguridad.crypto.sha1HexChars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.CharBuffer

/** The hex characters of the SHA-1 that are sent (k-anonymity range). */
private const val PREFIX_CHARS = 5

/** Maximum accepted body from the fixed Have I Been Pwned range endpoint (defence in depth). */
private const val MAX_PWNED_RESPONSE_BYTES = 2 * 1024 * 1024

/** Have I Been Pwned range API (k-anonymity): only the first 5 hex chars of the SHA-1 leave the device. */
class PwnedPasswords(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.pwnedpasswords.com/".toHttpUrl(),
) {
    suspend fun times(password: CharArray): Int {
        // CIFRADO-1: the full hash stays in a CharArray that is wiped; only the 5-character prefix becomes a String.
        val hash = sha1HexChars(password)
        try {
            val request = Request.Builder()
                .url(baseUrl.newBuilder().addPathSegment("range").addPathSegment(String(hash, 0, PREFIX_CHARS)).build())
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
            return pwnedCount(body, CharBuffer.wrap(hash, PREFIX_CHARS, hash.size - PREFIX_CHARS))
        } finally {
            hash.fill('\u0000')
        }
    }
}
