package app.seguridad.net

import okhttp3.OkHttpClient
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * eMudhra incident (October 2026): the private keys of eight intermediate CAs were recoverable (weak randomness),
 * so anyone can sign "valid" certificates with them. They are also cross-signed by "AAA Certificate Services",
 * a root many Android versions still trust, so an un-updated phone would accept a forged HTTPS certificate.
 * Browsers, Go, rustls and Node.js block them; Android's trust store is only updated with the system.
 *
 * Public-key SHA-256 (SPKI) of the eight intermediates and of the eMudhra roots involved, checked against the
 * certificates published at https://emudhra.we-have-a-problem.com (impacted-intermediates.json, roots.json).
 */
internal val DISTRUSTED_SPKI_SHA256 = setOf(
    "EBED6475C2C2D80FCB199F3FE8DD6E6B3CE60B77EB15D4E42C3DEC0C6F5B5105", // EM DV TLS CA - G3B
    "43BFFA19D6EF651451F6841E019931E96F6F2EAEFF28AB4B6F1CE4576DE2717B", // EM OV TLS CA - G3B
    "E0254C7FCDC265E2F0F8A428DABB75AB4AE039B4872D62F5EE57F0877D5A8A41", // EM EV TLS CA - G3B
    "49DD353DC2B919A3770645117D0E60C16115E7D1BC259EEFE734B00EA434FE5F", // EM DV TLS CA - G3C
    "63ECEFEE431D49523BC1136C4E72D0206A477086CAF606CD5CF025686C109CF8", // EM OV TLS CA - G3C
    "5C0660A2B164910D8FF589C18E14552E9E0EC237DCEFE2CB736D41A4811F81A0", // EM EV TLS CA - G3C
    "EC4FFA1AD0AC88081314B7AAFFEF6A91F97CD57291BB5BA686C5415F5EB4F6B6", // EM OV CS CA - G3B
    "92C063D67E8F14DB71D288E11266FFDAF69CBF115F7BAB14F3BC2F68D25E0E30", // EM OV CS CA - G3C
)

/**
 * Uses exact SPKI matching only. Blocking issuers by distinguished name would also reject unrelated certificates
 * issued by a root with the same subject (including future renewals or re-keyed certificates).
 */
internal fun X509Certificate.isDistrusted(): Boolean = spkiSha256() in DISTRUSTED_SPKI_SHA256

/**
 * The system's trust decisions, plus a refusal of any chain that goes through a distrusted key, whichever root
 * it chains to. Never weaker than [base]: it only adds a check after [base] accepted the chain.
 */
class DistrustingTrustManager(private val base: X509TrustManager) : X509ExtendedTrustManager() {
    private fun refuse(chain: Array<out X509Certificate>) {
        chain.firstOrNull { it.isDistrusted() }?.let {
            throw CertificateException("Distrusted certificate authority (eMudhra key compromise): ${it.subjectX500Principal.name}")
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        base.checkServerTrusted(chain, authType)
        refuse(chain)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
        (base as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: base.checkServerTrusted(chain, authType)
        refuse(chain)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) {
        (base as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: base.checkServerTrusted(chain, authType)
        refuse(chain)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = base.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        (base as? X509ExtendedTrustManager)?.checkClientTrusted(chain, authType, socket) ?: base.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        (base as? X509ExtendedTrustManager)?.checkClientTrusted(chain, authType, engine) ?: base.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = base.acceptedIssuers.filterNot { it.isDistrusted() }.toTypedArray()
}

/** HTTPS through the system trust store, minus the compromised eMudhra keys (see [DistrustingTrustManager]). */
fun OkHttpClient.Builder.distrustCompromisedCas(): OkHttpClient.Builder {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
    val system = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    val trust = DistrustingTrustManager(system)
    val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return sslSocketFactory(tls.socketFactory, trust)
}
