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
 * Without the name it cannot ask, so the connection is refused (fails closed): the leak check then says it could not
 * check.
 */
fun androidTrustedChain(system: X509TrustManager): TrustedChain {
    val extensions = X509TrustManagerExtensions(system)
    return { chain, authType, host ->
        if (host == null) throw CertificateException("No server name: the chain the system validated cannot be checked")
        extensions.checkServerTrusted(chain, authType, host)
    }
}
