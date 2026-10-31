package app.seguridad.core

/**
 * ANDROID-2 (+CADENAS-1/2, audit 1.3.0): whether a URI handed back by a system picker (OpenDocument, CreateDocument,
 * OpenDocumentTree) may be read or written. Only a `content://` URI of another app's provider is accepted:
 * - `file://` (any case) is refused: ContentResolver opens it with Seguridad's own UID, so a picker that is not the
 *   system's could make the app read or truncate its private files (vault.bin, settings.json);
 * - a `content://` of the app's own providers (the package name, or `<package>.<anything>`: «.compartir», the
 *   androidx-startup one) is refused: the app may open them even though they are not exported;
 * - anything else (`android.resource`, no scheme, no authority) is refused.
 * The authority is compared without its `userinfo@` and `:port` and ignoring case. Another app's provider is not a
 * risk in itself: it gets what the user chose to give it, as with any picker. A narrow deny list, not an allow list.
 */
fun isAcceptableDocumentUri(scheme: String?, authority: String?, packageName: String): Boolean {
    if (scheme != "content") return false
    val host = documentAuthorityHost(authority) ?: return false
    val own = packageName.lowercase()
    return host != own && !host.startsWith("$own.")
}

/** [authority] without `userinfo@` and `:port`, in lower case; null when nothing is left. */
fun documentAuthorityHost(authority: String?): String? =
    authority?.substringAfterLast('@')?.substringBefore(':')?.lowercase()?.takeIf { it.isNotEmpty() }
