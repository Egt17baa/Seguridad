package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.seguridad.ui.theme.SectionCard

/** Shown once, before anything else, until «Entendido» saves Settings.noticeAccepted. */
@Composable
fun NoticeScreen(onAccept: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("🛡", style = MaterialTheme.typography.displaySmall)
            Text("Antes de empezar", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Seguridad guarda tus contraseñas, códigos 2FA y frases semilla, y cifra textos y archivos. Todo se queda en este móvil, sin cuentas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sin recuperación de la contraseña maestra", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Si olvidas la contraseña maestra de la bóveda, o la de algo que cifres, nadie puede recuperar lo guardado: ni tú, ni nosotros. " +
                            "Apúntala y guárdala donde solo tú puedas verla, y exporta de vez en cuando una copia cifrada.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sin auditoría independiente", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "La app usa algoritmos estándar y bibliotecas conocidas, pero no ha pasado una auditoría de seguridad independiente. " +
                            "Para fondos importantes usa una wallet hardware y un gestor de contraseñas auditado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Entendido") }
        }
    }
}
