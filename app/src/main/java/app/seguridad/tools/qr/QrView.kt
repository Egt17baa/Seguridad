package app.seguridad.tools.qr

import app.seguridad.crypto.parseOtpAuth
import app.seguridad.tools.QR_CIPHER_NONE
import app.seguridad.tools.QR_CIPHER_UNKNOWN
import app.seguridad.tools.QR_COPIED_WITH_HIDDEN
import app.seguridad.tools.QR_CRYPTO
import app.seguridad.tools.QR_FIELD_ACCOUNT
import app.seguridad.tools.QR_FIELD_ADDRESS
import app.seguridad.tools.QR_FIELD_AMOUNT
import app.seguridad.tools.QR_FIELD_BENEFICIARY
import app.seguridad.tools.QR_FIELD_CIPHER
import app.seguridad.tools.QR_FIELD_COIN
import app.seguridad.tools.QR_FIELD_EMAIL
import app.seguridad.tools.QR_FIELD_HIDDEN_NETWORK
import app.seguridad.tools.QR_FIELD_IBAN
import app.seguridad.tools.QR_FIELD_LATITUDE
import app.seguridad.tools.QR_FIELD_LONGITUDE
import app.seguridad.tools.QR_FIELD_MESSAGE
import app.seguridad.tools.QR_FIELD_NAME
import app.seguridad.tools.QR_FIELD_NETWORK
import app.seguridad.tools.QR_FIELD_NUMBER
import app.seguridad.tools.QR_FIELD_PHONE
import app.seguridad.tools.QR_FIELD_PORT
import app.seguridad.tools.QR_FIELD_REFERENCE
import app.seguridad.tools.QR_FIELD_SCHEME
import app.seguridad.tools.QR_FIELD_SERVICE
import app.seguridad.tools.QR_FIELD_SUBJECT
import app.seguridad.tools.QR_FIELD_TO
import app.seguridad.tools.QR_HIDDEN_ELSEWHERE
import app.seguridad.tools.QR_HTTP
import app.seguridad.tools.QR_IP_HOST
import app.seguridad.tools.QR_KIND_CARD
import app.seguridad.tools.QR_KIND_CONTACT
import app.seguridad.tools.QR_KIND_CRYPTO
import app.seguridad.tools.QR_KIND_EMAIL
import app.seguridad.tools.QR_KIND_GEO
import app.seguridad.tools.QR_KIND_LINK
import app.seguridad.tools.QR_KIND_OTP
import app.seguridad.tools.QR_KIND_PHONE
import app.seguridad.tools.QR_KIND_RECEIVED
import app.seguridad.tools.QR_KIND_SEPA
import app.seguridad.tools.QR_KIND_SMS
import app.seguridad.tools.QR_KIND_TEXT
import app.seguridad.tools.QR_KIND_WIFI
import app.seguridad.tools.QR_MIXED_SCRIPTS
import app.seguridad.tools.QR_SCHEME_NONE
import app.seguridad.tools.QR_SEPA
import app.seguridad.tools.QR_SGK1
import app.seguridad.tools.QR_SHORTENER
import app.seguridad.tools.QR_SMS
import app.seguridad.tools.QR_SUBDOMAINS
import app.seguridad.tools.QR_WIFI_OPEN
import app.seguridad.tools.QR_WIFI_WEP
import app.seguridad.tools.QR_YES
import app.seguridad.tools.qrBidi
import app.seguridad.tools.qrPort
import app.seguridad.tools.qrPunycode
import app.seguridad.tools.qrScheme
import app.seguridad.tools.qrUserInfo
import java.util.Locale

/** What the inspector offers for a QR, besides copying it. Never opening it: there is no such action. */
enum class QrAction { NONE, ADD_CONTACT, OPEN_RECEIVED, ADD_TOTP }

/** One line of the summary: [value] is already as shown, with [visibleText]'s marks. */
data class QrField(val label: String, val value: String)

/**
 * Everything the inspector screen shows for one QR text, worked out here so it can be tested on the JVM. Every string
 * that comes from the QR is already passed through [visibleText]: the screen shows them as they are, as plain text.
 *
 * - [text] is the QR as it came (for «Copiar» only), [shownText] the same with its marks.
 * - [alerts] are «Señales de alerta (N)», one line each; when empty the screen shows «Sin avisos», neutral.
 * - [notes] are said for that kind of QR whatever it holds (a payment, a contact card).
 * - [secret] is the Wi-Fi password, hidden until tapped. [textHidden]: the full text carries a secret (that password,
 *   a 2FA key), so it is hidden until tapped too. [credentialInText]: a link with user info («usuario:contraseña@», or
 *   a token where the user name goes), shown as it is (it is what the alert points at) but a secret all the same (MN,
 *   T7 of §2.5). [carriesSecret] says the screen drops the result when the app stops or the vault locks.
 * - [hostLabels] is the highlighted name of a link: the one the browser connects to, in ASCII (punycode, so no
 *   look-alike letters), split into its labels; an IP whole. [hostReading] is its Unicode reading, shown below it and
 *   smaller, only when it is not the same.
 * - [copyNotice] / [secretCopyNotice]: what «Copiar» / «Copiar contraseña» say when the copy carries hidden characters
 *   (copied as they are, never stripped); null otherwise.
 */
data class QrView(
    val text: String,
    val shownText: String,
    val title: String,
    val fields: List<QrField>,
    val alerts: List<String>,
    val notes: List<String>,
    val action: QrAction,
    val secret: String? = null,
    val textHidden: Boolean = false,
    val hostLabels: List<String> = emptyList(),
    val hostReading: String? = null,
    val copyNotice: String? = null,
    val secretCopyNotice: String? = null,
    val credentialInText: Boolean = false,
) {
    val carriesSecret: Boolean get() = textHidden || credentialInText
}

/**
 * The view of [text]. Throws [IllegalArgumentException] with the size message, as [classifyQr] does, over
 * [MAX_QR_TEXT_BYTES]; nothing else throws.
 */
fun qrView(text: String): QrView {
    val kind = classifyQr(text)
    val base = QrView(text, visibleText(text), QR_KIND_TEXT, emptyList(), emptyList(), emptyList(), QrAction.NONE)
    // Outside a link (whose own signal says it), hidden characters anywhere in the text are one more alert.
    val hidden = hiddenMarks(text).takeIf { it.isNotEmpty() }?.let { listOf(qrBidi(it.joinToString(", "))) }.orEmpty()
    fun f(label: String, value: String?) = value?.takeIf { it.isNotBlank() }?.let { QrField(label, visibleText(it)) }
    val view = when (kind) {
        is QrKind.Link -> linkView(base, kind)
        is QrKind.Wifi -> {
            val open = kind.security == "nopass" && kind.password == null
            val cipher = when {
                open -> QR_CIPHER_NONE
                kind.security == "nopass" -> QR_CIPHER_UNKNOWN
                else -> kind.security
            }
            val alerts = listOfNotNull(QR_WIFI_OPEN.takeIf { open }, QR_WIFI_WEP.takeIf { kind.security == "WEP" })
            base.copy(
                title = QR_KIND_WIFI,
                fields = listOfNotNull(
                    f(QR_FIELD_NETWORK, kind.ssid),
                    f(QR_FIELD_CIPHER, cipher),
                    f(QR_FIELD_HIDDEN_NETWORK, QR_YES.takeIf { kind.hidden }),
                ),
                alerts = alerts + hidden,
                secret = kind.password,
                textHidden = kind.password != null,
            )
        }
        is QrKind.Sepa -> base.copy(
            title = QR_KIND_SEPA,
            fields = listOfNotNull(
                f(QR_FIELD_BENEFICIARY, kind.name),
                f(QR_FIELD_IBAN, kind.iban),
                f(QR_FIELD_AMOUNT, kind.amount),
                f(QR_FIELD_REFERENCE, kind.reference),
            ),
            alerts = hidden,
            notes = listOf(QR_SEPA),
        )
        is QrKind.Crypto -> base.copy(
            title = QR_KIND_CRYPTO,
            fields = listOfNotNull(
                f(QR_FIELD_COIN, kind.scheme.replaceFirstChar { it.titlecase(Locale.ROOT) }),
                f(QR_FIELD_ADDRESS, kind.address),
                f(QR_FIELD_AMOUNT, kind.amount),
            ),
            alerts = hidden,
            notes = listOf(QR_CRYPTO),
        )
        is QrKind.Contact -> base.copy(
            title = QR_KIND_CONTACT,
            fields = listOfNotNull(f(QR_FIELD_NAME, kind.name)) +
                kind.phones.mapNotNull { f(QR_FIELD_PHONE, it) } + kind.emails.mapNotNull { f(QR_FIELD_EMAIL, it) },
            alerts = hidden,
        )
        is QrKind.Sms -> base.copy(
            title = QR_KIND_SMS,
            fields = listOfNotNull(f(QR_FIELD_TO, kind.number), f(QR_FIELD_MESSAGE, kind.body)),
            alerts = listOfNotNull(QR_SMS.takeIf { isShortNumber(kind.number) }) + hidden,
        )
        is QrKind.Phone -> base.copy(title = QR_KIND_PHONE, fields = listOfNotNull(f(QR_FIELD_NUMBER, kind.number)), alerts = hidden)
        is QrKind.Email -> base.copy(
            title = QR_KIND_EMAIL,
            fields = listOfNotNull(f(QR_FIELD_TO, kind.to), f(QR_FIELD_SUBJECT, kind.subject), f(QR_FIELD_MESSAGE, kind.body)),
            alerts = hidden,
        )
        is QrKind.Geo -> base.copy(
            title = QR_KIND_GEO,
            fields = listOfNotNull(f(QR_FIELD_LATITUDE, kind.lat), f(QR_FIELD_LONGITUDE, kind.lon)),
            alerts = hidden,
        )
        is QrKind.SeguridadCard -> base.copy(title = QR_KIND_CARD, alerts = hidden, notes = listOf(QR_SGK1), action = QrAction.ADD_CONTACT)
        is QrKind.SeguridadReceived -> base.copy(title = QR_KIND_RECEIVED, alerts = hidden, action = QrAction.OPEN_RECEIVED)
        is QrKind.OtpAuth -> {
            // Only the names: the key itself stays in the hidden full text.
            val otp = parseOtpAuth(kind.text)
            base.copy(
                title = QR_KIND_OTP,
                fields = listOfNotNull(f(QR_FIELD_SERVICE, otp?.issuer), f(QR_FIELD_ACCOUNT, otp?.account)),
                alerts = hidden,
                action = QrAction.ADD_TOTP,
                textHidden = true,
            )
        }
        is QrKind.PlainText -> base.copy(alerts = hidden)
    }
    // A hidden character the browser would decode (percent-encoded in the host) is in the copy too, as written.
    val copyHidden = hasHiddenChars(text) || (kind is QrKind.Link && Signal.HIDDEN_CHARS in kind.signals)
    return view.copy(
        copyNotice = QR_COPIED_WITH_HIDDEN.takeIf { copyHidden },
        secretCopyNotice = view.secret?.takeIf { hasHiddenChars(it) }?.let { QR_COPIED_WITH_HIDDEN },
    )
}

private fun linkView(base: QrView, link: QrKind.Link): QrView {
    val host = link.host
    val unicode = link.unicodeHost ?: host
    // Over the whole text, not only the link: whitespace trimmed before reading it (a tab, U+2028) is marked in the
    // full text too, and a marked text never goes with «Sin avisos».
    val marks = hiddenMarks(base.text)
    val signalled = link.signals.map { s ->
        when (s) {
            Signal.MIXED_SCRIPTS -> QR_MIXED_SCRIPTS
            Signal.PUNYCODE -> qrPunycode(visibleText(unicode.orEmpty()))
            Signal.IP_HOST -> QR_IP_HOST
            Signal.PLAIN_HTTP -> QR_HTTP
            // The name the browser really connects to: ASCII, so no look-alike letters in it.
            Signal.USERINFO -> qrUserInfo(visibleText(host.orEmpty()))
            Signal.ODD_PORT -> qrPort(":" + (link.port?.toString() ?: "…"))
            Signal.SHORTENER -> QR_SHORTENER
            Signal.MANY_LEVELS -> QR_SUBDOMAINS
            Signal.DANGEROUS_SCHEME -> qrScheme(visibleText("${link.scheme}:"))
            Signal.HIDDEN_CHARS -> if (marks.isEmpty()) QR_HIDDEN_ELSEWHERE else qrBidi(marks.joinToString(", "))
        }
    }
    val alerts = if (Signal.HIDDEN_CHARS !in link.signals && marks.isNotEmpty()) signalled + qrBidi(marks.joinToString(", ")) else signalled
    val fields = listOfNotNull(
        link.port?.let { QrField(QR_FIELD_PORT, it.toString()) },
        QrField(QR_FIELD_SCHEME, if (link.scheme.isEmpty()) QR_SCHEME_NONE else visibleText(link.scheme)),
    )
    // What stands out is what the browser connects to: for a look-alike name, its `xn--` form, not the imitation.
    val labels = when {
        host.isNullOrEmpty() -> emptyList()
        Signal.IP_HOST in link.signals -> listOf(visibleText(host))
        else -> host.split('.').map(::visibleText)
    }
    val reading = unicode?.takeIf { host != null && it != host }?.let(::visibleText)
    return base.copy(
        title = QR_KIND_LINK, fields = fields, alerts = alerts, hostLabels = labels, hostReading = reading,
        credentialInText = Signal.USERINFO in link.signals,
    )
}

/**
 * A short number (a premium-rate service, typically 5 or 6 digits in Spain): at most 6 digits once spaces, dots,
 * dashes and brackets are left out, and no «+».
 */
private fun isShortNumber(number: String): Boolean {
    val digits = number.filterNot { it == ' ' || it == '-' || it == '.' || it == '(' || it == ')' }
    return digits.isNotEmpty() && digits.length <= 6 && digits.all { it in '0'..'9' }
}
