package app.seguridad.ui.screens

import app.seguridad.tools.HEALTH_EMPTY_VAULT
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import app.seguridad.ui.theme.SectionCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.seguridad.health.DAY_MS
import app.seguridad.health.MISSION_GO
import app.seguridad.health.MISSION_LATER
import app.seguridad.health.MissionAction
import app.seguridad.health.MissionPostponements
import app.seguridad.health.nextMission
import app.seguridad.tools.HEALTH_NO_VAULT
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.seguridad.health.HealthItem
import app.seguridad.health.Issue
import app.seguridad.health.uniquePasswords
import app.seguridad.health.HealthView
import app.seguridad.health.healthView
import app.seguridad.vault.LIST_NO_MEMORY
import app.seguridad.imports.plainReportText
import app.seguridad.net.PwnedPasswords
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

private fun Issue.label() = when (this) {
    Issue.REUSED -> "Repetida"
    Issue.WEAK -> "Débil"
    Issue.OLD -> "Antigua (más de un año)"
    Issue.NO_2FA -> "Sin 2FA"
    Issue.BREACHED -> "Filtrada"
}

/**
 * Vault health: repeated, weak, old and no-2FA passwords, plus an on-demand leak check against HIBP (k-anonymity).
 * Leak results live only in this screen's memory: they are never saved or logged, and are gone when it closes.
 */
@Composable
fun HealthScreen(
    store: VaultStore,
    pwned: PwnedPasswords,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    missionsOff: Boolean = false,
    postponements: MissionPostponements? = null,
    onMission: (MissionAction) -> Unit = {},
) {
    VaultIdleLock(store)
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var breached by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    fun dropResults() {
        breached = emptyMap()
        message = null
        running = false
        done = 0
        total = 0
    }
    // Locking does not close this screen, and leaving the app (ON_STOP) stops recomposition: the check itself
    // watches the vault (below). This only drops the results of a check that had already finished.
    val unlocked = state is VaultState.Unlocked
    LaunchedEffect(unlocked) {
        if (!unlocked) {
            job?.cancelAndJoin()
            job = null
            dropResults()
        }
    }

    BackScaffold("Salud de la bóveda", onBack) { padding ->
        val entries = (state as? VaultState.Unlocked)?.entries
        if (entries == null) {
            // 2.0, task 10 (m-2 of phase A): reached from Herramientas too. With no vault, its own line; locked (or still
            // reading), the vault's gate in place, to open it from here (its fingerprint is this vault's).
            if (state is VaultState.NoVault) {
                // RT-22 (ii): while another vault of the phone has its file, the empty one is not one to create in.
                val guard = LocalEmptySlotGuard.current
                // Round 2, point 10: nothing until it is known (no «Aún no tienes bóveda» flashing while another vault has its file).
                val mayFill by produceState<Boolean?>(null, store) { value = guard() }
                mayFill?.let { Text(if (it) HEALTH_NO_VAULT else HEALTH_EMPTY_VAULT, Modifier.padding(padding).padding(16.dp)) }
            } else {
                VaultGate(store, onImport = {}, modifier = Modifier.padding(padding)) { }
            }
            return@BackScaffold
        }
        val data = (state as VaultState.Unlocked).data
        // Branch review, C m-1: «Salud» is computed once, off the main thread, only when its inputs change (the data, the
        // leaks found, the day; not on scrolling or the check's progress). 2.0, task 10 (phase B): the missions come from
        // that same result, never with the leak check (O-6: [Issue.BREACHED] is taken out). Their titles may hold an
        // entry's title: they live only here, and a lock takes this branch away, which cancels the work and drops them.
        // «Ahora no» per vault and until its next unlock.
        val day = System.currentTimeMillis() / DAY_MS
        // Round 2 of the fixes of the attack, point 9: worked out in health/ (healthView), which catches running out of memory.
        val health by produceState<HealthView?>(null, data, breached, day, missionsOff) {
            // Round 3: cancellable between entries (a new leak found or a lock stops the computation of before).
            value = withContext(Dispatchers.Default) { val work = coroutineContext.job; healthView(data, System.currentTimeMillis(), breached, missionsOff) { work.ensureActive() } }
        }
        // Out of memory: the text instead of anything else (no mission, no count, never «Sin problemas detectados.»).
        if (health?.noMemory == true) {
            // Round 3: a leak check running stops here (its «Cancelar» is not on screen): no request goes after the one in
            // flight; the leaks found stay in `breached`, and dropResults is not called (it would change the key again).
            LaunchedEffect(Unit) { job?.cancel() }
            Text(LIST_NO_MEMORY, color = NegativeColor, modifier = Modifier.padding(padding).padding(16.dp))
            return@BackScaffold
        }
        val epoch = store.lockEpoch
        var postponed by remember(epoch) { mutableStateOf(postponements?.postponed(store.id, epoch).orEmpty()) }
        val mission = health?.missions?.let { nextMission(it, postponed, off = missionsOff) }
        // Until the first result, neither «Sin problemas detectados.» nor any count.
        val items = health?.items
        val byId = entries.associateBy { it.id }
        LazyColumn(Modifier.padding(padding).fillMaxSize().touchesVault(store), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mission != null) {
                item(key = "mission") {
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(mission.title, style = MaterialTheme.typography.titleSmall)
                            // R-2: «Ahora no» weighs the same as «Ir».
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { onMission(mission.action) }, modifier = Modifier.weight(1f)) { Text(MISSION_GO) }
                                OutlinedButton(
                                    onClick = {
                                        postponements?.postpone(store.id, epoch, mission.id)
                                        postponed = postponements?.postponed(store.id, epoch) ?: (postponed + mission.id)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(MISSION_LATER) }
                            }
                        }
                    }
                }
            }
            if (items != null) {
                item {
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (items.isEmpty()) Text("Sin problemas detectados.")
                            Issue.entries.forEach { issue ->
                                val n = items.count { issue in it.issues }
                                if (issue != Issue.BREACHED || breached.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(issue.label())
                                        Text(n.toString(), color = if (n > 0) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "La comprobación envía solo los 5 primeros caracteres de la huella SHA-1 de cada contraseña a Have I Been Pwned; " +
                            "la comparación se hace en tu móvil. Solo se ejecuta cuando la pulsas y el resultado no se guarda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (running) {
                        LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                        Text("Comprobando $done de $total…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { job?.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    } else {
                        Button(
                            onClick = {
                                val groups = uniquePasswords(entries)
                                breached = emptyMap()
                                message = null
                                done = 0
                                total = groups.size
                                running = true
                                job = scope.launch {
                                    // The vault locks (idle, or leaving the app, when this screen no longer
                                    // recomposes): the check stops at once, nothing more is sent, the passwords it
                                    // held are let go and its results are dropped.
                                    val check = coroutineContext.job
                                    launch {
                                        store.state.first { it !is VaultState.Unlocked }
                                        check.cancel()
                                    }
                                    val found = HashMap<String, Int>()
                                    try {
                                        for ((password, ids) in groups) {
                                            if (store.state.value !is VaultState.Unlocked) throw CancellationException()
                                            val chars = password.toCharArray()
                                            val n = try {
                                                pwned.times(chars)
                                            } finally {
                                                chars.fill('\u0000')
                                            }
                                            store.touch() // A check the user is watching is not idle time.
                                            if (n > 0) ids.forEach { found[it] = n }
                                            breached = HashMap(found)
                                            done++
                                        }
                                        message = "Comprobación terminada: ${found.size} ${if (found.size == 1) "acceso filtrado" else "accesos filtrados"}."
                                    } catch (e: CancellationException) {
                                        if (store.state.value is VaultState.Unlocked) {
                                            message = "Comprobación cancelada ($done de $total)."
                                        } else {
                                            dropResults()
                                        }
                                        throw e
                                    } catch (e: Exception) {
                                        message = "No se pudo completar la comprobación ($done de $total). Revisa la conexión."
                                    } finally {
                                        running = false
                                        coroutineContext.cancelChildren() // The vault watcher: done with this check.
                                    }
                                }
                            },
                            enabled = entries.any { it.password.isNotEmpty() && it.type == app.seguridad.vault.EntryType.LOGIN },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Comprobar filtraciones (envía solo 5 caracteres del hash de cada una)") }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            // C-1: an entry's id (any text, from a copy) never meets the fixed key «mission».
            items(items.orEmpty().sortedByDescending { it.issues.size }, key = { lazyKey('e', it.entryId) }) { item ->
                HealthRow(item, byId[item.entryId]?.title.orEmpty(), onClick = { onEdit(item.entryId) })
            }
        }
    }
}

@Composable
private fun HealthRow(item: HealthItem, title: String, onClick: () -> Unit) {
    // Re-review N-3: a pressable panel, so it glows or marks itself by the same rule as the others (SectionCard).
    SectionCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(plainReportText(title).ifEmpty { "(sin título)" }, style = MaterialTheme.typography.titleMedium)
            Text(item.issues.sortedBy { it.ordinal }.joinToString(" · ") { it.label() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Editar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}


