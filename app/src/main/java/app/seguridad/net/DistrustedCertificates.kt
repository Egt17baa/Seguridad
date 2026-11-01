package app.seguridad.net

import okhttp3.OkHttpClient
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
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
    "8D417DB2DD8BF5E3084D1E3F196D583849D81BDD4C00C70B9D39369E96B8C782", // emSign ECC Root CA - G3 (removed by Mozilla and Chrome)
    "C619E4780ED8E0ABEDA24AD732567DE4383A7EA941338F6D1A7D1B49AC792FE9", // emSign Root TLS CA - G3
    "0A6C2B2CA5B364207954171987AC9D2178561569D8205457E77CA90C32EDE282", // emSign Root CS CA - G3
)

/**
 * The same roots by name: a chain anchored on one of them may not include the root certificate itself.
 * The roots are distrusted on purpose, as browsers did: they are the eMudhra roots of the compromised intermediates,
 * not unrelated ones, and the app only talks to api.pwnedpasswords.com, which no eMudhra CA issues for.
 * Matched by CN and O only, so an OU present in one root and not in another cannot make the check miss.
 */
internal val DISTRUSTED_ROOT_CNS = setOf("emSign ECC Root CA - G3", "emSign Root TLS CA - G3", "emSign Root CS CA - G3")
private const val EMUDHRA_ORG = "eMudhra Technologies Limited"

private fun X500Name.values(type: ASN1ObjectIdentifier): List<String> =
    getRDNs(type).flatMap { rdn -> rdn.typesAndValues.filter { it.type == type }.map { IETFUtils.valueToString(it.value) } }

/** True for the subject or issuer name of an emSign G3 root (see [DISTRUSTED_ROOT_CNS]). */
internal fun X500Principal.isDistrustedRootName(): Boolean {
    val name = try {
        X500Name.getInstance(encoded)
    } catch (e: IllegalArgumentException) {
        return false
    }
    return EMUDHRA_ORG in name.values(BCStyle.O) && name.values(BCStyle.CN).any { it in DISTRUSTED_ROOT_CNS }
}

internal fun X509Certificate.spkiSha256(): String =
    MessageDigest.getInstance("SHA-256").digest(publicKey.encoded).joinToString("") { "%02X".format(it) }

// Names first: they are cheap, and the SPKI hash is only needed when they do not match.
internal fun X509Certificate.isDistrusted(): Boolean =
    subjectX500Principal.isDistrustedRootName() || issuerX500Principal.isDistrustedRootName() || spkiSha256() in DISTRUSTED_SPKI_SHA256

/**
 * The chain the system validated and accepted for a server, up to the root it trusts: it can hold certificates the
 * server did not send (see [DistrustingTrustManager]). [host] is the server's name from the handshake, or null when
 * it is not known.
 */
typealias TrustedChain = (chain: Array<out X509Certificate>, authType: String, host: String?) -> List<X509Certificate>

/**
 * The system's trust decisions, plus a refusal of any chain that goes through a distrusted key, whichever root
 * it chains to. Never weaker than [base]: it only adds checks, one before [base] and one after [base] accepted the chain.
 *
 * Why twice (CRY-B01): on Android, [base] is Network Security Config's RootTrustManager over Conscrypt's
 * TrustManagerImpl, which keeps the intermediates of every chain it validates in a cache («intermediateIndex», one for
 * the whole process: every default trust manager shares it since Android 7) and uses them to complete a later chain
 * that lacks its intermediate. So the chain the server presents is not always the chain the system validated:
 * 1. the presented chain is refused before [base] sees it, so the system never validates, nor caches, a compromised
 *    intermediate through this client;
 * 2. after [base] accepted it, [trustedChain] gives the chain [base] built, and it is refused too: a server that sends
 *    only a leaf is caught when the system completes it with a compromised intermediate cached any other way.
 *
 * On Android, [trustedChain] is androidTrustedChain (X509TrustManagerExtensions, in `android/`). The default, the
 * presented chain again, is right on the JVM, which keeps no such cache.
 */
@Suppress("CustomX509TrustManager") // Lint: it always asks the system first and only adds refusals, never accepts on its own.
class DistrustingTrustManager(
    private val base: X509TrustManager,
    /** Internal so HttpClientTest can see that the client checks the chain the given function makes. */
    internal val trustedChain: TrustedChain = { chain, _, _ -> chain.toList() },
) : X509ExtendedTrustManager() {
    private fun refuse(chain: List<X509Certificate>) {
        chain.firstOrNull { it.isDistrusted() }?.let {
            throw CertificateException("Distrusted certificate authority (eMudhra key compromise): ${it.subjectX500Principal.name}")
        }
    }

    /** The presented chain, then [system] (the check of [base]), then the chain [base] validated for [host]. */
    private inline fun checked(chain: Array<out X509Certificate>, authType: String, host: String?, system: () -> Unit) {
        refuse(chain.asList())
        system()
        refuse(trustedChain(chain, authType, host))
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
        checked(chain, authType, host = null) { base.checkServerTrusted(chain, authType) }

    // The server's name, from the handshake in progress: the same place RootTrustManager reads it from.
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        checked(chain, authType, (socket as? SSLSocket)?.handshakeSession?.peerHost) {
            (base as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: base.checkServerTrusted(chain, authType)
        }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        checked(chain, authType, engine?.handshakeSession?.peerHost) {
            (base as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: base.checkServerTrusted(chain, authType)
        }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = base.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        (base as? X509ExtendedTrustManager)?.checkClientTrusted(chain, authType, socket) ?: base.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        (base as? X509ExtendedTrustManager)?.checkClientTrusted(chain, authType, engine) ?: base.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = base.acceptedIssuers.filterNot { it.isDistrusted() }.toTypedArray()
}

/**
 * HTTPS through the system trust store, minus the compromised eMudhra keys (see [DistrustingTrustManager]).
 * [trustedChainOf] makes, from the system's trust manager, the [TrustedChain] that gives the chain it validated (Android
 * passes androidTrustedChain); without it, the presented chain is checked again, which is enough on the JVM.
 */
fun OkHttpClient.Builder.distrustCompromisedCas(trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null): OkHttpClient.Builder {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
    val system = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    val trust = trustedChainOf?.let { DistrustingTrustManager(system, it(system)) } ?: DistrustingTrustManager(system)
    val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return sslSocketFactory(tls.socketFactory, trust)
}
