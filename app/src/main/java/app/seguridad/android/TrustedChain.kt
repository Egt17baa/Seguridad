package app.seguridad.android

import android.net.http.X509TrustManagerExtensions
import app.seguridad.net.TrustedChain
import java.security.cert.CertificateException
import javax.net.ssl.X509TrustManager

/**
 * CRY-B01: the chain [system] validated for a server, with any intermediate Conscrypt took from its cache, for the
 * eMudhra check of the app's client (see app.seguridad.net.DistrustingTrustManager).
 *
 * X509TrustManagerExtensions takes Network Security Config's RootTrustManager, the default trust manager, from API 24
 * (minSdk is 26). It validates the chain again for the server's name and returns the chain it built, up to the root.
 *
 * Fails closed, and the leak check then says it could not check:
 * - without the server's name the connection is refused. This is stricter than the system, which would take the
 *   default configuration: the name always comes from the handshake (see DistrustingTrustManager);
 * - a system trust manager it cannot take (never the default one on API 24+) refuses every connection, rather than
 *   closing the app when the client is built.
 *
 * This second validation does not get the stapled OCSP response nor the SCTs of the TLS extension (the variant with the
 * server's name has no session): if Android ever required Certificate Transparency of the app (a Network Security
 * Config, or the default of a future targetSdk), it could refuse a server the first validation accepts. It fails closed
 * (N-04): check «Comprobar contraseña» with network on a phone at every raise of targetSdk.
 */
fun androidTrustedChain(system: X509TrustManager): TrustedChain {
    val extensions = try {
        X509TrustManagerExtensions(system)
    } catch (e: IllegalArgumentException) {
        return { _, _, _ -> throw CertificateException("The system trust manager cannot give the chain it validated", e) }
    }
    return { chain, authType, host ->
        if (host == null) throw CertificateException("No server name: the chain the system validated cannot be checked")
        extensions.checkServerTrusted(chain, authType, host)
    }
}
