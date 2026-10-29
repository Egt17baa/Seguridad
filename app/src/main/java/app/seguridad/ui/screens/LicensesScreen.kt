package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.SectionCard

private class Component(val name: String, val license: String, val url: String, val asset: String, val note: String = "")

private val components = listOf(
    Component("Kotlin, kotlinx.serialization y kotlinx.coroutines", "Apache-2.0", "https://kotlinlang.org", "apache-2.0.txt"),
    Component("AndroidX (Core, Activity, Lifecycle, Navigation, Biometric)", "Apache-2.0", "https://developer.android.com/jetpack/androidx", "apache-2.0.txt"),
    Component("Jetpack Compose y Material 3", "Apache-2.0", "https://developer.android.com/jetpack/compose", "apache-2.0.txt"),
    Component("OkHttp y Okio", "Apache-2.0", "https://square.github.io/okhttp/", "apache-2.0.txt"),
    Component("ZXing y ZXing Android Embedded", "Apache-2.0", "https://github.com/journeyapps/zxing-android-embedded", "apache-2.0.txt", "Lectura de códigos QR con la cámara."),
    Component("Bouncy Castle", "MIT", "https://www.bouncycastle.org/", "bouncycastle.txt", "Algoritmos criptográficos: Argon2id, ChaCha20-Poly1305, Twofish, Serpent, Camellia, Ed25519, HPKE y el cifrado híbrido X-Wing."),
    Component("Lista de palabras BIP-39", "MIT", "https://github.com/bitcoin/bips/blob/master/bip-0039/bip-0039-wordlists.md", "bip39.txt", "Las 2.048 palabras en inglés y las 2.048 en español, sin cambios."),
    Component("Lista de palabras larga de la EFF", "CC BY 3.0 US", "https://www.eff.org/dice", "cc-by-3.0.txt", "De la Electronic Frontier Foundation. Se usa sin cambios en el Generador. Licencia: https://creativecommons.org/licenses/by/3.0/us/"),
)

/** One entry per component, each with its license text that can be unfolded. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun asset(name: String) =
        runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
    BackScaffold("Licencias", onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Seguridad solo usa componentes de código abierto con licencia libre y gratuita.", style = MaterialTheme.typography.bodyMedium)
            components.forEach { c ->
                var open by rememberSaveable(c.name) { mutableStateOf(false) }
                val text = remember(c.asset) { asset(c.asset) }
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text("Licencia: ${c.license}", style = MaterialTheme.typography.labelLarge)
                        if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { open = !open }, enabled = text.isNotBlank()) { Text(if (open) "Ocultar el texto de la licencia" else "Ver el texto de la licencia") }
                        if (open) Text(text.trim(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
