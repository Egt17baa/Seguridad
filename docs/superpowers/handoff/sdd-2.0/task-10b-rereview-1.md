# Tarea 10, fase B: re-revisión 1 (9827beb..8c31429)

Revisor: el mismo de `task-10b-review.md`, solo leyendo.

He leído el «Ruling (advisor, T10 fase B)» (`progress.md`, al final), el informe `task-10b-fix1-report.md` y el diff `review-9827beb..8c31429.diff`. Lo he contrastado con el código alrededor: `AppNavHost.kt`, `VaultScreen.kt`, `VaultUpgrade.kt`, `Opening.kt`, `Guide.kt`, `QrHandoff.kt`, `QrPendingText.kt`, `QrInspectScreen.kt`, `VaultStore.kt` (`VaultLock`), `AutoBackup.kt`, `SettingsScreen.kt` y `HealthScreen.kt`, además de los tests nuevos.

No he ejecutado tests: el controlador corre las suites en un clon limpio. Las citas son de `8c31429`.

## Veredicto

**Aprobada con menores.** Hay 0 Critical y 0 Important. I-1, m-1 a m-9, la Duda 6 e i-1 están arreglados y tienen su test.

Queda un menor nuevo de documentación (n-1): un KDoc fuera de sitio y tres comentarios que siguen diciendo que el paso a v4 lo pinta `VaultGate`. Es una línea de arreglo y no necesita otra re-revisión larga.

## Estado de cada hallazgo

| Hallazgo | Estado | Evidencia |
|---|---|---|
| **I-1** | Arreglado (opción a) | Ver la sección siguiente |
| **m-1** | Arreglado | Ver «m-1 y la Duda 6» |
| **Duda 6** | Arreglada | Ver «m-1 y la Duda 6» |
| **m-2** | Arreglado | `OnboardingScreen.kt:52-53`: `DialogProperties(dismissOnClickOutside = false)`. «Atrás» sigue llamando a `onDismissRequest = onDone` y «Saltar» sigue en cada página. Hay un test de fuente en `GuideTest.theRoundsTexts` |
| **m-3** | Arreglado | `Guide.kt:42`: «Las copias con calendario solo se hacen al abrir Clavero.», con su test |
| **m-4** | Arreglado | `Guide.kt:47`: «Si tienes una huella registrada en el móvil, …». Es lo que comprueba `canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS` (`BiometricVault.kt:95-96`) |
| **m-5** | Arreglado | `MissionsTest.everyTextOfTheMissionsHasNoPressure`: las plantillas en singular y plural (1, 2 y 7), los títulos producidos de verdad (con un `assert` de que salen el singular, el plural, «en «X»» y «en un acceso»), los textos fijos, `MISSIONS_LABEL` y `HEALTH_NO_VAULT`, cada uno con `pressureWords` y con `ForbiddenWords`. Las dos guías y su sección, en `GuideTest.theRoundsTexts` |
| **m-6** | Arreglado | `nextMission(all, postponed, off)` (`Missions.kt:80`): con `off` devuelve `null`, y `HealthScreen.kt:126,133` lo usa. `MissionsTest.theSwitchTurnsThemAllOff` lo comprueba. `SettingsTest.missionsOffReadsAndWrites` lee el `settings.json` real de la 1.4.3 y uno de la fase A, hace la ida y vuelta, y comprueba que `false` no se escribe |
| **m-7** | Arreglado | Ver «m-7» |
| **m-8** | Arreglado | Ver «m-8» |
| **m-9** | Arreglado | Ver «m-9» |
| **i-1** | Arreglado | `MISSION_BACKUP_TITLE = "Comprueba una copia"` (`Missions.kt:44`). «Ir» lleva a «Comprobar una copia», que comprueba una copia que el usuario elige y no hace ninguna (`BackupCheckTexts.kt`). El título dice lo que se puede hacer allí y va en línea con `backupCheckNotice`. Se aparta del ejemplo del diseño, así que va al Abogado |

### I-1: los dos diálogos del paso a v4

- **Los dos están en `VaultNav`, cada uno detrás de su condición:**
  - `VaultNav` pinta `if (showing == OpeningItem.UPGRADE) VaultUpgradeDialog(vault)` y `if (showing == OpeningItem.UPGRADE_DONE) upgradeDone?.let { VaultUpgradeDoneNotice(vault, it) }` (`AppNavHost.kt:288-289`).
  - La rama abierta de `VaultGate` solo pinta `content(s.entries)` (`VaultScreen.kt:338`).
  - No hay ninguna otra llamada en `ui/`, y el test de m-7 lo comprueba.
- **Nunca encima de la puerta ni con la bóveda bloqueada:**
  - `openingItem` solo elige `UPGRADE` y `UPGRADE_DONE` con `vault == OPEN` (`Opening.kt:65-66`), algo que el test exhaustivo comprueba en las 12 288 combinaciones.
  - Al bloquear, la fase sale de `OPEN` en la misma recomposición y `lock()` llama a `upgrades.drop()` (`VaultStore.kt:743`).
  - La línea de después (`done`) sobrevive al bloqueo a propósito (`PendingUpgrade.kt`, «The notice of [done] was shown, or the vault is gone»), pero no se pinta hasta la siguiente apertura. Así pasa en cualquier pestaña, porque `phase` sale de `vaultState`, que es de toda la app, y no de la pantalla.
- **1a, el paso a v4 y O-3** (Rulings de la tarea 1, `progress.md:42` y `:45`, y diseño, O-3):
  - O-3 pide el aviso «a la vista», con «Exportar una copia antes» como opción destacada.
  - «O-3 con carpeta» pide que el aviso informativo se enseñe una vez.
  - La ronda 1 acepta que viva solo en memoria.
  - Ninguno fija la pantalla. Enseñarlo encima de Herramientas, Ajustes o «Salud», con la bóveda abierta, lo pone más a la vista, no menos. La lectura del implementador es correcta.
- **1b, lo que hacía la rama abierta de `VaultGate`:**
  - el `Box(...touchesVault(store))` sigue fuera del `when` (`VaultScreen.kt:331`), así que vale para todos los estados;
  - `content`;
  - los dos diálogos.
  - «Salud» tiene su `VaultIdleLock(store)` y su `touchesVault(store)` en la lista.
  - Un diálogo es una ventana propia, así que sus toques no pasaban por el `touchesVault` del `Box` ni antes ni ahora. No se pierde nada.
- **1c, el selector y `keepOpenOnce`:**
  - `rememberDocumentPicker` y `keepOpenOnce` siguen dentro de `VaultUpgradeDialog` (`VaultUpgrade.kt:81`, `:120-123`), sin cambios.
  - El diálogo sigue en la composición mientras `upgradePending`, también mientras el selector está encima, así que el resultado llega. Ahora vive en `VaultNav`, fuera de un destino de la navegación, y un cambio de pestaña ya no lo saca.
  - `VaultUpgradeDialog` solo usa `LocalContext`, y `VaultUpgradeDoneNotice` ningún *local*, así que salir del `CompositionLocalProvider` de `VaultNav` no les quita nada.
  - Con `keepOpenOnce` puesto, la guía espera y el diálogo no se ve afectado.
- **`VaultGate` en el resto de pantallas** (Inspector de QR ×2, Códigos, Contactos, Bóveda, «Salud»): todas reciben el `vault` de `VaultNav`. Antes, quien desbloqueaba desde el Inspector veía el diálogo encima de un contenido vacío, y ahora lo ve encima de lo mismo. No hay regresión.

### m-1 y la Duda 6

- **Los botones de la guía solo con la bóveda abierta o sin bóveda:**
  - `enabled = canOpenGuide(vaultState)`, con `GUIDE_NEEDS_OPEN` debajo cuando no se puede (`SettingsScreen.kt:683-686`, `Guide.kt:82`, `:88`).
  - Con test de la función y de la fuente: dos `enabled = guideOpenable` y la línea.
- **Una petición nunca se queda guardada fuera de su sitio:**
  - `guideRequestKept(onTab, phase)` solo la conserva en la pestaña Ajustes (`onTab == "settings"`) y con la bóveda abierta o sin bóveda (`Opening.kt:57-58`).
  - Si no, se olvida: `LaunchedEffect(requestKept, guideRequested)` llama a `forgetRequest()` (`AppNavHost.kt:265-266`).
  - `onTab` es `null` en cualquier pantalla que no es una pestaña, así que ahí siempre se olvida.
  - `aRequestIsKeptOnlyWhereItWasAsked` recorre las 5 pestañas, `null` y las 4 fases.
- **Olvidarla no rompe nada:** la petición solo vive en memoria (`_requested`) y no escribe nada. La guía que tocaba no depende de ella, porque `current()` cae a `guideDue` si no se ha descartado.
- **Duda 6:**
  - `done(kind)` solo pone `_dismissed` y escribe `withGuideSeen()` si `kind` es la guía que tocaba (`Guide.kt:125`).
  - `onlyTheDueGuideIsSavedAsSeen` usa el `settings.json` real de la 1.4.3: ver la completa desde Ajustes deja `GUIDE_BEFORE_2_0` en disco y las novedades pendientes, y verlas después escribe `GUIDE_CURRENT`.
  - Quien actualiza no pierde las novedades.
  - Si una escritura falla, `_dismissed` ya era cierto y una petición posterior de la misma guía vuelve a intentar guardarla. Bien.

### m-7

`OpeningTest.eachDialogIsDrawnOnceBehindItsCondition` recorre todos los `.kt` de `ui/` y busca los ocho diálogos de la cola, incluidos los dos que movió I-1. Para cada uno exige:
- una sola llamada, quitando comentarios, KDoc y la línea `fun X(`;
- en `AppNavHost.kt`;
- con `showing == OpeningItem.X` en esa línea o en la anterior.

Con el código de antes fallaba, porque las dos llamadas del paso a v4 estaban en `VaultScreen.kt`.

No es fácil de engañar:
- una segunda llamada en cualquier archivo de `ui/` lo pone en rojo;
- una llamada sin su condición también.

El único punto débil es el del aviso de «Bóvedas»: se busca `vaultsNotice?.let` y no el `AlertDialog` que hay dentro. Es aceptable, porque el texto `Text(shown)` solo existe ahí.

### m-8

- **`QrHandoff.pendingState`** (`QrHandoff.kt:58-62`) se pone al día en cada entrada y en cada salida: `put`, `clear` (y por tanto `take`), `hold`, `release` y `appLeft`.
  - Lo calcula `updatePending()`, que siempre se llama desde un método `@Synchronized`.
  - Lo que hacen `put`, `take` y `clear` no cambia: solo actualizan la marca al final.
  - `pendingIsObservable` comprueba cada camino, también un `take` para otro destino.
- **Los caminos que sueltan `pending`:** los dos únicos que crean un `QrPendingText` son los del Inspector (`QrInspectScreen.kt:386` y `:463`). Los dos lo sueltan a mano de cuatro maneras:
  - al guardar o abandonar (`:417`, `:493`);
  - en el `onDispose` sin cambio de configuración (`:414`, `:490`);
  - en el `onCleared` de su ViewModel (`:392`, `:469`), también cuando `VaultScopes.drop` lo limpia al cambiar de bóveda;
  - con `appLeft`, al salir de la app.
- **El GC:** ningún camino depende solo de él. El `WeakHashMap` es solo la red de seguridad de memoria, y la marca se recalcula en cada `release`.
- **`VaultLock.keepOpenOnceState`** (`VaultStore.kt:1715-1722`) cambia `@Volatile var` por un `MutableStateFlow`, con el mismo getter y setter. Es seguro entre hilos.
- **La marca no se queda puesta para siempre:** se borra en cada `MainActivity.onStart`, y se borra a mano si el selector no se abre (`launchOrError`, en `VaultBackup.kt:202` y `:213`, `ImportScreen.kt:495` y `:501`, `BackupCheckScreen.kt:234` y `VaultUpgrade.kt:123`). Todos los selectores que la ponen son del sistema (SAF y el escáner) y sacan la actividad a `onStop`. No ha cambiado respecto a antes.

### m-9

- **`LocalOpeningBusy provides (showing != null)`** (`AppNavHost.kt:332`). La pantalla de la bóveda espera con `snapshotFlow { openingBusy.value }.first { !it }` antes de tomar cada snackbar:
  - «Activar huella» interrumpida (`VaultScreen.kt:172`): antes del `try/finally` que llama a `interruptionShown`, así que si se cancela mientras espera no cuenta como visto;
  - lo que copió el calendario (`VaultScreen.kt:305`): antes de `takeScheduled`.
- **No se pierden:** mientras esperan no se toman. Si la pantalla sale de la composición, el aviso sigue en `AutoBackup` o en `BiometricVault` y sale la próxima vez. El bloqueo lo descarta igual que antes (decisión de la tarea 8).
- **No esperan para siempre:** `showing` vuelve a `null` cuando se cierra el diálogo, que el usuario siempre puede cerrar, o cuando un bloqueo saca la fase `OPEN`.
- **`scheduledNotice` no puede llevar un fallo:** `postScheduled(byCalendar, …)` solo publica cuando `n > 0` (`AutoBackup.kt:433-437`). `byCalendar` cuenta solo las copias con `writtenAt != null && copies != null` (`:543`). El texto es `scheduledCopiesText(n)` (`:209`), «Se hizo la copia con calendario.» o «Se hicieron N copias con calendario.», y una copia escrita cuya rotación falló sigue escrita. Es verdad.
- **El test** (`theSnackbarsWaitForTheQueue`) es de fuente: dos esperas, cada una antes de lo que da el aviso por visto. Para esto basta.

## Issues nuevos

### Critical

Ninguno.

### Important

Ninguno.

### Minor

**n-1. Documentación que no dice lo que hace el código.**
- **`Opening.kt:46-56`:** el KDoc nuevo de `guideRequestKept` se metió entre el KDoc de `openingItem` y la función. Ahora `openingItem` no tiene KDoc, y el primer bloque queda suelto encima del segundo. **Arreglo:** mover `guideRequestKept`, con su KDoc, debajo de `openingItem`, o encima del primer bloque.
- **`AppNavHost.kt:267-268`:** el comentario dice «the upgrade's choice is drawn by VaultGate, and nothing else here while it waits». Ahora lo pinta `VaultNav`, en las líneas 288-289. **Arreglo:** «the upgrade's choice and its line are drawn here too (below); nothing else while they wait».
- **`OpeningTest.kt:84`:** el comentario de `theScreenDrawsWhatItPicks` dice «the upgrade's choice is VaultGate's», y la lista de ese test todavía excluye `UPGRADE` y `UPGRADE_DONE`. **Arreglo:** quitar la exclusión, porque ya se pintan en `AppNavHost.kt`, y corregir el comentario. Si no, borrar ese test, que ya cubre `eachDialogIsDrawnOnceBehindItsCondition`.

### Informativo

- `LocalOpeningBusy` está declarado en `OnboardingScreen.kt:36`, que es el archivo de la guía, aunque no es de la guía. Encajaría mejor junto a `openingItem` o en `AppNavHost.kt`. No lo pido.

## Regresiones comprobadas (ninguna)

- **Los otros seis diálogos:** las llamadas y sus parámetros no cambian, salvo `GuideDialog`, que ahora llama a `done(kind)`. `MasterChangedDialog`, `a6Text`, `a6Decide` y `a6Summary` no aparecen en el diff.
- **O-3:** se ve más que antes, y el orden de `openingItem` no ha cambiado.
- **O-7:** `NoticeScreen` sigue antes de `VaultNav` (`AppNavHost.kt:180-193`), y `guideSeen` es independiente.
- **La huella automática de la puerta:** la rama `Locked` de `VaultGate`, con `UnlockVault`, no cambia (`VaultScreen.kt:335`). La guía y los diálogos del paso a v4 nunca salen con `LOCKED`.
- **Fuera de alcance:** nada. Los archivos son los de los hallazgos, sus tests y tres documentos: `progress.md`, `pendientes.md` (filas 32 a 34, que coinciden con el Ruling) y el traspaso.
- **Lo que no cambia:** formatos, permisos, el manifiesto, las dependencias, la red y los registros.

## Revisión experto-seguridad-cripto

- **O-3, a la vista:** el paso a v4 y su línea salen con la bóveda abierta en cualquier pantalla (`AppNavHost.kt:288-289`). Nunca salen encima de la puerta (`Opening.kt:65-66`), y el bloqueo descarta el paso pendiente (`VaultStore.kt:743`). Cierra el camino nuevo de «Salud».
- **Decisión (b), ningún resultado tardío:**
  - una guía pedida no sobrevive fuera de Ajustes ni con la bóveda bloqueada (`Opening.kt:57-58`, `AppNavHost.kt:265-266`), así que no puede salir después encima de la puerta;
  - un snackbar solo se gasta cuando de verdad se ve (`VaultScreen.kt:172`, `:305`).
- **Veracidad:**
  - el título de la misión de la copia (`Missions.kt:44`);
  - la huella registrada (`Guide.kt:47`, con `BIOMETRIC_STRONG`);
  - la frase de las copias con calendario (`Guide.kt:42`);
  - `scheduledNotice` solo informa de copias escritas (`AutoBackup.kt:433-437`, `:543`).
- **Datos de la bóveda:** `pendingState`, `keepOpenOnceState` y `LocalOpeningBusy` son booleanos. Que la entrega se observe no cambia qué se entrega ni cuándo se borra: `put`, `take` y `clear` hacen lo mismo (`QrHandoff.kt`).
- **MASVS:**
  - STORAGE: `missionsOff` y `guideSeen` no dicen nada de la bóveda;
  - PLATFORM: no hay componentes exportados ni `Intent` nuevos;
  - NETWORK y PRIVACY: nada nuevo.
- **Nada nuevo** en formatos, permisos, dependencias, red ni registros.

## Revisión Ingeniero Móvil Senior

- **Composición:**
  - los diálogos del paso a v4 viven en `VaultNav` (`key(vault)`), y el launcher del selector sobrevive a los cambios de pestaña;
  - `deliveryPending` sale de dos `StateFlow` observados con `collectAsStateWithLifecycle` (`AppNavHost.kt:256-257`).
- **Efectos:**
  - `snapshotFlow { … }.first { !it }` con `rememberUpdatedState` va dentro del mismo `LaunchedEffect`; si se cancela, no da nada por visto;
  - olvidar la petición lo hace un `LaunchedEffect` con claves; un fotograma de retraso no importa.
- **Estado:** `VaultLock.keepOpenOnce` es un `MutableStateFlow` detrás de la misma propiedad, y nada cambia para los ocho sitios que la usan.
- **Accesibilidad (R-1):**
  - los botones desactivados llevan su línea fija debajo (`SettingsScreen.kt:686`), así que el color nunca es la única señal;
  - la guía ya no se cierra con un toque fuera (`OnboardingScreen.kt:53`).
- **Tests:**
  - el de una sola llamada (m-7), el de la Duda 6 y el de la petición en cada pestaña y fase son buenos;
  - los de m-9 y m-2 son de fuente, suficientes para lo que comprueban;
  - n-1 pide limpiar el test antiguo `theScreenDrawsWhatItPicks`.

## Textos para el Abogado Digital (revisión final)

1. **i-1:** «Comprueba una copia», el título de la misión de la copia, en lugar del ejemplo del diseño, «Haz y comprueba una copia». Es por veracidad: «Ir» lleva a «Comprobar una copia», que no hace copias (`Missions.kt:44`).
2. **m-3:** «En Ajustes puedes exportar una copia o elegir hasta 3 carpetas para la copia automática. Las copias con calendario solo se hacen al abrir Clavero.» (`Guide.kt:41-42`, O-4).
3. **m-4:** «Si tienes una huella registrada en el móvil, puedes activarla en el menú de la bóveda abierta. Por seguridad, cada 7 días pide la contraseña maestra.» (`Guide.kt:47`).
4. **m-1:** «Desbloquea la bóveda para ver la guía.», en Ajustes con la bóveda bloqueada o cargando (`Guide.kt:82`).
5. **Los demás textos de la fase B** no han cambiado y siguen como en `task-10b-review.md`. Entre ellos van i-2 e i-3, que el Ruling manda al Abogado.
6. **O-3:** los textos del paso a v4 no cambian. Ahora salen en cualquier pantalla con la bóveda abierta, también en Herramientas, Ajustes y «Salud».
