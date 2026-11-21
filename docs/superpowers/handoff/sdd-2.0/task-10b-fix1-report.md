# Tarea 10, fase B: ronda 1 de arreglos

## Estado

**Hecha.** Base `9827beb`; HEAD de partida `a333555`, que solo traía documentos.

Las dos suites pasan por separado, con `./gradlew --stop` entre ellas (las cifras, abajo). Los commits no se han subido ni hay PR.

No cambia nada de formatos, permisos, dependencias ni red. Los textos de A6 no se tocan. Los textos de error siguen para la fase C.

## Commits

| Commit | Qué |
|---|---|
| `89b12a2` | I-1, m-1 a m-9, duda 6 e i-1, con sus tests |
| `8c31429` | «2.0, tarea 10 (fase B): ronda 1 de arreglos» (cierre; este informe no entra en git) |

## Antes de I-1

**1a. Los Rulings de O-3 de la tarea 1** (`progress.md`, líneas 42 y 45, y el diseño, O-3):
- O-3 pide que el aviso del paso a v4 esté **a la vista**, con «Exportar una copia antes» como opción destacada.
- «O-3 con carpeta» pide que, tras el paso con carpeta, se enseñe **una vez** el aviso informativo.
- El Ruling de la ronda 1 acepta que ese aviso viva solo en memoria.

Ninguno limita en qué pantalla sale el aviso. El KDoc de `VaultUpgradeDialog` ya decía «over every vault screen». Enseñarlo encima de Herramientas, Ajustes o «Salud», siempre con la bóveda abierta, cumple mejor «a la vista»: con la elección en `VaultGate`, quien desbloqueaba desde «Salud» no la veía. Es coherente, así que seguí adelante.

**1b. Lo que hacía la rama abierta de `VaultGate`:**
- el `Box(modifier.fillMaxSize().touchesVault(store))`, para todos los estados: cualquier toque cuenta como uso para el bloqueo por inactividad;
- `content(s.entries)`;
- los dos diálogos del paso a v4.

«Salud» ya tiene su propio `touchesVault(store)` en la lista y su `VaultIdleLock(store)`. Solo le faltaban los dos diálogos, y ahora los pinta `VaultNav`. No hay nada más que cubrir.

**1c. El selector de «Exportar una copia antes»:**
- Su launcher (`rememberDocumentPicker`) y `keepOpenOnce` están dentro de `VaultUpgradeDialog` y se mueven con él, sin cambios. El diálogo sigue en la composición mientras se elige el archivo, porque `upgradePending` sigue siendo cierto hasta `completeUpgrade`. Así, el resultado del selector llega.
- `keepOpenOnce` solo hace esperar a la guía (`deliveryPending`). No quita el turno de `UPGRADE`.
- Un bloqueo con el diálogo abierto saca la bóveda de `OPEN`, así que `openingItem` deja de elegir `UPGRADE` (`Opening.kt`, comprobado en las 12 288 combinaciones). Además, el almacén descarta el paso pendiente al bloquear (`upgrades.drop`). El diálogo desaparece.

## Hallazgos y arreglos

Cada test se vio en rojo con el código de antes. Para que compilaran, puse primero unos stubs sin el comportamiento: diez tests en rojo. `missionsOffReadsAndWrites` describe una lectura que ya funcionaba, así que pasó a la primera.

| Hallazgo | Arreglo | `archivo:línea` | Test (y su rojo) |
|---|---|---|---|
| **I-1** | `VaultNav` pinta `VaultUpgradeDialog` y `VaultUpgradeDoneNotice` detrás de `showing == OpeningItem.UPGRADE` y `UPGRADE_DONE`. `VaultGate` ya no los pinta | `AppNavHost.kt:288-289`; `VaultScreen.kt:338` | `OpeningTest.eachDialogIsDrawnOnceBehindItsCondition`: una sola llamada en `ui/`, en `AppNavHost.kt`, detrás de su condición. En rojo: las dos estaban en `VaultScreen.kt` |
| **m-1** | Los dos botones de la guía solo con la bóveda abierta o sin bóveda (`canOpenGuide`), y si no, la línea fija `GUIDE_NEEDS_OPEN`. Una petición se olvida en cuanto no se puede enseñar donde se pidió: fuera de la pestaña Ajustes o sin la bóveda abierta (`guideRequestKept` y `forgetRequest`). Así, nunca queda guardada donde `onTab == null` | `SettingsScreen.kt:683-686`; `Guide.kt:82`, `:88`, `:114`; `Opening.kt:57`; `AppNavHost.kt:265-266` | `GuideTest.ajustesOpensItOnlyWithTheVaultOpenOrNone` y `OpeningTest.aRequestIsKeptOnlyWhereItWasAsked`, con todas las pestañas, `null` incluido, y todas las fases. Rojos |
| **Duda 6** | `done(kind)` solo guarda `guideSeen`, y solo da por vista la guía de esta ejecución, si `kind` es la que tocaba | `Guide.kt:125` | `GuideTest.onlyTheDueGuideIsSavedAsSeen`: quien actualiza ve la completa desde Ajustes y las novedades siguen pendientes. Rojo |
| **m-2** | `DialogProperties(dismissOnClickOutside = false)`. «Atrás» y «Saltar» siguen saltando la guía | `OnboardingScreen.kt:53` | `GuideTest.theRoundsTexts`. Rojo |
| **m-3** | «Las copias con calendario solo se hacen al abrir Clavero.» | `Guide.kt:42` | `GuideTest.theRoundsTexts`. Rojo |
| **m-4** | «Si tienes una huella registrada en el móvil, puedes activarla…» | `Guide.kt:47` | `GuideTest.theRoundsTexts`. Rojo |
| **m-5** | Ver la lista detallada debajo de la tabla | — | `MissionsTest.everyTextOfTheMissionsHasNoPressure` y `GuideTest.theRoundsTexts` |
| **m-6** | El apagado de R-2 dentro de la selección: `nextMission(all, postponed, off)` | `Missions.kt:80`; `HealthScreen.kt:133` | `MissionsTest.theSwitchTurnsThemAllOff` (rojo). Lectura: `SettingsTest.missionsOffReadsAndWrites` |
| **m-7** | Ver la lista detallada debajo de la tabla | — | `OpeningTest.eachDialogIsDrawnOnceBehindItsCondition` (rojo, por los dos de I-1) |
| **m-8** | `QrHandoff.pendingState` y `VaultLock.keepOpenOnceState`, observables. `put`, `take` y `clear` hacen lo mismo que antes y solo actualizan la marca. `VaultNav` los observa | `QrHandoff.kt:58-62`; `VaultStore.kt:1715-1722`; `AppNavHost.kt:256-257` | `QrHandoffTest.pendingIsObservable`. Rojo |
| **m-9** | Los dos snackbars que salen solos esperan a que la cola esté vacía antes de tomarse: `VaultNav` da `LocalOpeningBusy provides (showing != null)`, y la pantalla espera con `snapshotFlow { openingBusy.value }.first { !it }` | `OnboardingScreen.kt:36`; `AppNavHost.kt:332`; `VaultScreen.kt:172`, `:305` | `OpeningTest.theSnackbarsWaitForTheQueue`. Rojo |
| **i-1 (veracidad)** | El título de la misión de la copia pasa a «Comprueba una copia», que es lo que hace «Comprobar una copia», adonde lleva «Ir». Esa pantalla no hace copias. El nuevo título va en línea con `backupCheckNotice` («Aún no has comprobado ninguna copia.», «Comprueba una copia otra vez.») y se aparta del ejemplo del diseño («Haz y comprueba una copia») | `Missions.kt:44` | `MissionsTest.theCopysTitleSaysWhatItsScreenDoes` y `oneAtATime`. Rojos |

Detalle de m-5. `pressureWords` recorre ahora:
- los textos fijos;
- cada plantilla con un título neutro, en singular y en plural (1, 2 y 7): «Cambia N contraseñas repetidas/débiles/antiguas», «Activa el 2FA en «X»», «Activa el 2FA en un acceso» y «Comprueba una copia»;
- los títulos que de verdad produce `missions`;
- `MISSIONS_LABEL` y `HEALTH_NO_VAULT`;
- todos los textos de las dos guías y de su sección.

Lo hacen `MissionsTest.everyTextOfTheMissionsHasNoPressure` y `GuideTest.theRoundsTexts`. Estos tests no se vieron en rojo, porque ningún texto llevaba ya una palabra de presión.

Detalle de m-7. Para cada uno de los ocho diálogos de la cola, el test exige una sola llamada en `ui/`, en `AppNavHost.kt`, con `showing == OpeningItem.X` en su línea o en la anterior:
- `VaultUpgradeDialog`;
- `VaultUpgradeDoneNotice`;
- `MasterChangedDialog`;
- el `AlertDialog` de `vaultsNotice`;
- `ReplaceOutcomeDialog`;
- `RenameNoticeDialog`;
- `AppearanceNoticeDialog`;
- `GuideDialog`.

## Caminos de `pending` (m-8)

`QrPendingText` entra en `pending` en su `init`, con `hold`, si se llevó un texto. Sale a mano por todos estos caminos:
- `QrPendingText.clear()` → `release`, que se llama:
  - al guardar o abandonar (`QrInspectScreen.kt`, `vm.pending.clear()`);
  - al salir la pantalla sin cambio de configuración (`DisposableEffect … onDispose`);
  - al limpiarse su ViewModel (`onCleared`), también cuando `VaultScopes.drop` limpia los ViewModels al cambiar de bóveda;
  - al bloquearse la bóveda después de haber estado abierta en ese flujo (`vaultStateIs(false)`).
- `QrHandoff.appLeft()` vacía `pending` entero al salir de la app, con la regla del bloqueo al salir.

Ningún camino depende solo del recolector de basura. El `WeakHashMap` sigue como red de seguridad de memoria, igual que antes, pero ya no cuenta para la marca: esta se calcula y se publica en cada entrada y en cada salida (`updatePending`).

## ¿Puede `scheduledNotice` llevar un fallo?

No. `postScheduled` solo publica cuántas copias con calendario se escribieron (`byCalendar`). Se cuenta una copia cuando `result.writtenAt != null && result.copies != null` (`AutoBackup.kt:543`), y el texto es «Se hizo la copia con calendario.» o «Se hicieron N copias con calendario.».

Una copia escrita cuya rotación falló sí está escrita, así que el texto es verdad. Los fallos van a `lastError`, en Ajustes. No hace falta ningún arreglo de veracidad.

## Tests

- `:app`: **BUILD SUCCESSFUL**: 2360 tests, 0 fallos, 0 omitidos; assemble, lint, licencias, Fragment y assets en verde (sobre `89b12a2`).
- jvm-check: **BUILD SUCCESSFUL**: 2316 tests, 0 fallos, 0 omitidos (sobre `89b12a2`, después de `./gradlew --stop`).

Antes de las suites completas, corrí en jvm-check las clases tocadas (`health.*`, `settings.*`, `tools.qr.*`, `SimpleModeTest`, `PlatformSourcesTest`, `SourceTextsTest`) y `:app:compileDebugKotlin`. Todas en verde.

## Textos nuevos o cambiados

| Texto | Cuándo se ve | `archivo:línea` | Test |
|---|---|---|---|
| «En Ajustes puedes exportar una copia o elegir hasta 3 carpetas para la copia automática. Las copias con calendario solo se hacen al abrir Clavero.» (m-3) | Guía completa, 2/4 | `settings/Guide.kt:42` | `GuideTest.theRoundsTexts` |
| «Si tienes una huella registrada en el móvil, puedes activarla en el menú de la bóveda abierta. Por seguridad, cada 7 días pide la contraseña maestra.» (m-4) | Guía completa, 3/4 | `settings/Guide.kt:47` | `GuideTest.theRoundsTexts` |
| «Desbloquea la bóveda para ver la guía.» (m-1) | Sección «Guía» de Ajustes, con la bóveda bloqueada o cargando | `settings/Guide.kt:82`; `SettingsScreen.kt:686` | `GuideTest.ajustesOpensItOnlyWithTheVaultOpenOrNone` |
| «Comprueba una copia» (i-1; antes «Haz y comprueba una copia») | Misión, cuando `backupCheckNotice` lo dice | `health/Missions.kt:44` | `MissionsTest.theCopysTitleSaysWhatItsScreenDoes` |

## Revisión experto-seguridad-cripto

- **O-3, a la vista.** La elección del paso a v4 y su línea salen ahora en cualquier pantalla con la bóveda abierta, también en «Salud» desde Herramientas (`AppNavHost.kt:288-289`). Nunca salen encima de la puerta: un bloqueo saca la fase `OPEN` y el almacén descarta el paso pendiente. «Exportar una copia antes» sigue con su selector y `keepOpenOnce`.
- **Decisión (b), ningún resultado tardío.**
  - Una guía pedida en Ajustes ya no se queda esperando para salir después, encima de la puerta o fuera de una pestaña (`guideRequestKept`, `AppNavHost.kt:265-266`).
  - Los dos snackbars que salen solos ya no se gastan debajo de un diálogo (`VaultScreen.kt:172`, `:305`).
- **Veracidad.**
  - i-1: el título de la misión de la copia dice lo que hace la pantalla.
  - m-3: la frase de las copias con calendario.
  - m-4: la huella registrada (`BIOMETRIC_STRONG`, `BiometricVault.kt:95-96`).
  - `scheduledNotice` no lleva fallos.
- **Datos de la bóveda.** Nada de lo nuevo guarda datos de la bóveda: `pendingState` y `keepOpenOnceState` son booleanos, y `LocalOpeningBusy` también.
- **m-8.** Observar las entregas no cambia qué se entrega ni cuándo se borra: `put`, `take` y `clear` hacen lo mismo.
- **Nada nuevo** en formatos, permisos, dependencias, red ni registros.

## Revisión Ingeniero Móvil Senior

- **Composición.**
  - Los dos diálogos del paso a v4 pasan de `VaultGate` a `VaultNav`, que también tiene la clave de la bóveda (`key(vault)`). El launcher del selector vive en la composición del diálogo, como antes.
  - `deliveryPending` se lee ahora de dos `StateFlow` con `collectAsStateWithLifecycle` (`AppNavHost.kt:256-257`). Cuando la entrega termina, `VaultNav` se recompone y la guía sale sin esperar a otro cambio.
- **Efectos.**
  - Los snackbars esperan con `snapshotFlow { … }.first { !it }` dentro del mismo `LaunchedEffect(message)` y `LaunchedEffect(scheduled)`. Si se cancelan mientras esperan, no se marcan como vistos, porque el `try/finally` de «Activar huella» empieza después de la espera.
  - La petición de la guía se olvida con `LaunchedEffect(requestKept, guideRequested)`.
- **Accesibilidad.** Los botones de la guía desactivados llevan su línea fija debajo. El color nunca es la única señal (R-1).
- **`VaultLock.keepOpenOnce`.** Sigue siendo una `var` que se lee y se escribe igual en todos los sitios que ya la usaban. Por debajo es un `MutableStateFlow`, igual de seguro entre hilos que el `@Volatile` de antes.

## Textos para el Abogado Digital (revisión final)

1. **i-1:** «Comprueba una copia», el título de la misión de la copia. Se aparta del ejemplo del diseño («Haz y comprueba una copia») por la regla de veracidad: «Ir» lleva a «Comprobar una copia», que no hace copias. Va en línea con `backupCheckNotice`.
2. **m-3:** «Las copias con calendario solo se hacen al abrir Clavero.», en la guía completa (O-4).
3. **m-4:** «Si tienes una huella registrada en el móvil, puedes activarla en el menú de la bóveda abierta.».
4. **m-1:** «Desbloquea la bóveda para ver la guía.».
5. **i-2 e i-3** (Ruling): «Al cambiar una entrada se guarda su versión anterior.» y «Lo que borras pasa 30 días en la papelera.», en las novedades 2/4. Coinciden con `HISTORY_ALL_SHORT` y con `TRASH_SHORT`.

## Dudas

Ninguna nueva.
- La duda 3 queda como Ruling: si «vista» no se puede guardar, la guía vuelve en el siguiente arranque.
- Las dudas 1, 2, 4 y 5 están aceptadas.
- i-4 e i-5 van a las filas 32 y 33 de `pendientes.md`, y la prueba de I-1 en el móvil, a la fila 34, como dice el Ruling.
