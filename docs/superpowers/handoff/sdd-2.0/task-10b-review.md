# Tarea 10, fase B: revisión (2ecfee5..9827beb)

Revisor de solo lectura. Leídos: `CLAUDE.md`, `experto-seguridad-cripto/SKILL.md` (con «Trabajo conjunto con superpowers»: un hallazgo de seguridad o de un requisito del Abogado confirmado es Important como mínimo) y `references/auditoria.md`, las skills `desarrollo-movil-senior` y `auditoria-seguridad-movil` de Ingeniero Móvil Senior, las líneas «Ruling» y «Owner decision» de `sdd-2.0/progress.md` (sobre todo «Ruling (advisor, T10)», «Ruling (advisor, T10 fase A)» y la decisión del dueño del 14-11-2026 sobre la guía corta), la tarea 10 del plan, el diseño («Ayuda y personalización», O-1 a O-7, R-1, R-2), `task-10b-report.md` y el diff, contrastado con el código alrededor (`VaultStore.kt`, `PendingUpgrade.kt`, `VaultScreen.kt` (`VaultGate`), `AppNavHost.kt`, `SettingsScreen.kt`, `AutoBackup.kt`, `RenameNotice.kt`, `QrHandoff.kt`, `MainActivity.kt`, `SeguridadApp.kt`, `BiometricVault.kt`, `VaultLimits.kt`, `EntryHistory.kt`, `BackupCheck.kt`). No he ejecutado tests: lo de abajo sale de leer el código y de razonar las mutaciones.

## Veredicto

**No aprobada: 0 Critical, 1 Important (I-1) y 9 Minor (m-1 a m-9).** Sigo la convención de `progress.md` (tarea 9): con un Important abierto, la tarea no se aprueba. I-1 es pequeño y el resto está bien: n-1 y n-2 están bien hechos y sus tests fallarían sin el arreglo. `openingItem` es pura y su test exhaustivo recorre de verdad todas las entradas. Ningún aviso que espera se pierde ni se marca como visto sin verse. Las misiones cumplen O-6 y el ciclo de vida de los títulos. Nada cambia en formatos, permisos, dependencias, red ni registros.

## Issues

### Critical

Ninguno.

### Important

**I-1. El paso a v4 (O-3) no se ve al desbloquear desde «Salud», y mientras espera bloquea toda la cola.**
- `HealthScreen.kt:111-119` usa `VaultGate` solo con la bóveda bloqueada o cargando. Al desbloquearla ahí, la rama abierta (`HealthScreen.kt:120` y siguientes) ya no está dentro de `VaultGate`.
- El diálogo del paso a v4 y la línea de después solo los pinta `VaultGate`, y solo cuando su contenido está abierto (`VaultScreen.kt:334-338`).
- Y `openingItem` reserva el turno para ellos: con `upgradePending` o `upgradeDone` y la bóveda abierta, no deja salir nada más (`Opening.kt:57-58`).
- Esto es nuevo de esta fase (m-2): antes «Salud» decía «Desbloquea la bóveda…» y no desbloqueaba. Ahora quien actualiza desde la 1.4.3 puede abrir Herramientas → «Salud», escribir la maestra (una v3 no admite la huella, así que siempre es con la maestra) y:
  - **sin carpeta de copias:** la elección de O-3 («Exportar una copia antes» / «Seguir sin copia») no sale; ve los datos de «Salud» sin el aviso a la vista que pide O-3. No se pierde nada en disco (la v4 espera en memoria), pero el aviso no está «a la vista»;
  - **con carpeta:** el paso se guarda solo, con su copia, y la línea de después (`upgradeDone`, Ruling T1 «⚠️ O-3 con carpeta») no sale. Vive solo en memoria (Ruling T1, ronda 1): si Android cierra el proceso antes de que el usuario pase por una pantalla con `VaultGate`, la línea de O-3 no se ve nunca.
- Mismo hueco, menor: en una pestaña sin `VaultGate` (Herramientas, Ajustes), si `upgradeDone` llega mientras se está allí (la copia de antes del paso tarda; el usuario cambia de pestaña), `UPGRADE_DONE` ocupa el turno y nadie lo pinta. El diálogo de la maestra (A6), el resultado de A6, el aviso de «Bóvedas», los dos avisos y la guía esperan hasta que el usuario vuelva a una pantalla de la bóveda. Antes de esta fase, `MasterChangedDialog` y el resultado de A6 salían en cualquier pestaña.
- **Arreglo propuesto** (el controlador elige):
  - (a, el que recomiendo) que `VaultNav` pinte `VaultUpgradeDialog(vault)` cuando `showing == OpeningItem.UPGRADE` y `VaultUpgradeDoneNotice(vault, name)` cuando `showing == OpeningItem.UPGRADE_DONE`, y quitarlos de `VaultGate`. Así salen en cualquier pantalla con la bóveda abierta (lo que ya dice el KDoc de `VaultUpgradeDialog`: «over every vault screen»), nunca encima del bloqueo, y cada `OpeningItem` salvo `NOTICE` lo pinta el mismo sitio que lo elige; `theScreenDrawsWhatItPicks` pasa a cubrir los nueve.
  - (b, mínimo) que «Salud» use `VaultGate(store, …) { contenido }` en todos los estados, como las demás pantallas de la bóveda. Arregla el camino nuevo, no la espera en Herramientas y Ajustes.
- **Test:** con (a), el test de fuente cuenta una sola llamada a `VaultUpgradeDialog(` y a `VaultUpgradeDoneNotice(` en `ui/`, en `AppNavHost.kt`, detrás de su `showing ==`. Con (b), un test de fuente de que la rama abierta de `HealthScreen` está dentro del contenido de `VaultGate`. Y una fila del móvil: actualizar desde la 1.4.3, abrir «Salud» desde Herramientas, desbloquear: sale la elección del paso a v4.

### Minor

**m-1. «Ver la guía» y «Ver las novedades de la 2.0» no hacen nada con la bóveda bloqueada o cargando, y la guía sale después sola.**
- Los botones están siempre activos (`SettingsScreen.kt:679-681`) y `request` guarda la petición (`Guide.kt:98-100`).
- Pero `openingItem` exige `calm` (abierta o sin bóveda) también para la guía pedida (`Opening.kt:64`). Con la bóveda bloqueada, el toque no enseña nada; al desbloquear en la pestaña Bóveda, la guía sale de golpe, sin que el usuario la haya vuelto a pedir.
- Además, si la guía corta de quien actualiza aún no se había visto, `done()` la marca como vista aunque lo que se vio fue la completa (Duda 6).
- La pestaña Ajustes no tiene la puerta de la bóveda debajo, y la guía solo lleva textos fijos.
- **Arreglo** (recomendado): los dos botones activos solo con la bóveda abierta o sin bóveda (`settingsEditable(vaultState)` o la misma condición que `calm`), y con la bóveda bloqueada una línea fija, por ejemplo «Desbloquea la bóveda para ver la guía.». Así la petición nunca se queda guardada y nunca sale después sola.
- **No recomiendo** que la guía pedida se salte `calm` en cualquier pestaña. Si la petición se queda guardada (por m-8, o con una entrega en camino) y el usuario pasa a la pestaña Bóveda, la guía saldría encima de la puerta, y eso rompe el Ruling («nunca delante del bloqueo»). Si se prefiere enseñarla con la bóveda bloqueada, solo en la pestaña Ajustes (`onTab == "settings"`), y la petición se olvida al salir de esa pestaña. Con un test en `OpeningTest` en los dos casos.

**m-2. Un toque fuera del diálogo salta toda la guía y la guarda como vista.**
- `GuideDialog` usa el `AlertDialog` del tema con `DialogProperties()` por defecto (`OnboardingScreen.kt:41-42`, `Components.kt:249`): `dismissOnClickOutside = true`, y `onDismissRequest = onDone`.
- Un toque sin querer fuera de la tarjeta en la página 1 de 4 guarda `GUIDE_CURRENT`. La guía no vuelve sola.
- **Arreglo:** `properties = DialogProperties(dismissOnClickOutside = false)`. «Atrás» sigue siendo «Saltar» (Duda 4) y «Saltar» está en cada página.

**m-3. «La copia» (guía completa, 2/4): «Las que tienen calendario solo se hacen al abrir Clavero.»**
- Gramaticalmente, «las que» son las «carpetas» de la frase anterior, y las carpetas no «se hacen» (`Guide.kt:40-41`).
- El código hace lo que quiere decir: un destino con calendario solo copia en `runDue`, al desbloquear y en `MainActivity.onStart` (`AutoBackup.kt:294-297`, `:530`; `AppContainer.kt:513`).
- **Arreglo:** «Las copias con calendario solo se hacen al abrir Clavero.», la misma frase que la página 3 de las novedades (`Guide.kt:59`). `GuideTest` ya exige «al abrir Clavero». Para el Abogado.

**m-4. «La huella» (guía completa, 3/4): «Si el móvil tiene huella, puedes activarla en el menú de la bóveda abierta.»**
- El menú solo ofrece «Activar huella» si `canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS` (`BiometricVault.kt:95-96`, `VaultScreen.kt:220`).
- Un móvil con lector y sin ninguna huella registrada devuelve `BIOMETRIC_ERROR_NONE_ENROLLED` y no ve la opción. Pasa en todas las versiones, de la 26 a la 36.
- Lo de «cada 7 días» es verdad (`MASTER_RECHECK_MS`, `Vault.kt:544`, `:560`).
- **Arreglo:** «Si tienes una huella registrada en el móvil, puedes activarla en el menú de la bóveda abierta.». Para el Abogado.

**m-5. `noPressureWords` no recorre todos los textos de las misiones.**
- El test (`MissionsTest.kt`, `noPressureWords`) saca los títulos de tres bóvedas de ejemplo y quita los que llevan «» (bien: el título de la entrada es del usuario).
- Quedan fuera:
  - «Activa el 2FA en un acceso»;
  - «Cambia N contraseñas antiguas» (plural);
  - `MISSIONS_LABEL`;
  - `HEALTH_NO_VAULT`;
  - y la plantilla «Activa el 2FA en «…»» sin el título.
- Ninguno lleva hoy una palabra de presión, pero el Ruling pide el test.
- **Arreglo:** enumerar los textos fijos y cada plantilla con un título neutro, por ejemplo «Activa el 2FA en «X»», singular y plural de cada recuento, `MISSIONS_LABEL` y `HEALTH_NO_VAULT`. Pasar también los textos de las dos guías por `pressureWords`.

**m-6. R-2 («las apaga todas») y `missionsOff` sin test de comportamiento ni de lectura.**
- `missionsOff` no sale en ningún test (`grep -rn missionsOff app/src/test`: nada).
- Que lo apaga todo solo se ve leyendo `HealthScreen.kt:125-128`.
- **Arreglo:**
  - en `SettingsTest`, el `settings.json` real de la 1.4.3 y uno de la fase A leen `missionsOff = false`; `true` hace ida y vuelta, y `false` no se escribe;
  - una función pura `shownMission(all, postponed, off)`, o el apagado dentro de `nextMission`, con test de que con `off` no sale ninguna;
  - o, como mínimo, un test de fuente del `if (missionsOff) emptyList()`.

**m-7. `theScreenDrawsWhatItPicks` solo busca la cadena de la condición.**
- `OpeningTest.kt` (el último test) comprueba que `showing == OpeningItem.X` aparece en `AppNavHost.kt`.
- No comprueba que cada diálogo se pinte una sola vez, ni que esa llamada esté detrás de su condición. Si alguien vuelve a añadir un `RenameNoticeDialog(...)` suelto, el test sigue en verde.
- **Arreglo:** para cada pareja (`ReplaceOutcomeDialog(`/`A6_OUTCOME`, `RenameNoticeDialog(onClose`/`RENAME`, `AppearanceNoticeDialog(`/`APPEARANCE`, `GuideDialog(`/`GUIDE`, `MasterChangedDialog(`/`MASTER_CHANGED`, el `AlertDialog` de `vaultsNotice`/`VAULTS_NOTICE`, y los dos del paso a v4 si se acepta I-1 (a)), comprobar que hay una sola llamada en `ui/` y que su línea, o la anterior, lleva `showing == OpeningItem.X`.

**m-8. `deliveryPending` se lee de estado que Compose no observa.**
- `AppNavHost.kt:271` lee `QrHandoff.hasPending()` y `VaultLock.keepOpenOnce` durante la composición. Ninguno de los dos es estado de Compose.
- Cuando la entrega termina, nada recompone `VaultNav`. La guía espera hasta el siguiente cambio que sí recomponga: una pestaña, los ajustes o el estado de la bóveda.
- Además, `pending` es un `WeakHashMap` (`QrHandoff.kt:38`): un `QrPendingText` que no se suelta sigue contando hasta el siguiente GC.
- Falla hacia el lado seguro: la guía sale tarde, nunca encima. En la práctica pesa poco, porque la guía solo sale en una pestaña y las entregas van a pantallas de detalle.
- **Arreglo:** un `StateFlow<Boolean>` en `QrHandoff`, puesto al día en `put`, `take`, `clear`, `hold`, `release` y `appLeft`, y otro, o un `mutableStateOf`, para `keepOpenOnce`. O dejarlo escrito en el KDoc de `OpeningState.deliveryPending` como límite aceptado. Que decida el controlador.

**m-9. El aviso de la copia con calendario (y el de «Activar huella» interrumpida) sale por su cuenta, fuera de `openingItem`, y puede quedar debajo de un diálogo de arranque.**
- El informe dice que los snackbars responden a algo que hace el usuario. Dos no:
  - `scheduledNotice`, lo que copió el calendario (`VaultScreen.kt:295-299`): se toma una sola vez (`takeScheduled`) al enseñarlo;
  - `BiometricVault.interrupted` (`VaultScreen.kt:164-175`), que se da por visto al enseñarlo.
- Las copias con calendario corren justo al desbloquear y al abrir la app (`AutoBackup.kt:457`, `:505`), es decir, en el mismo momento en que salen el paso a v4, su línea, el aviso del aspecto o la guía. El snackbar sale entonces detrás del diálogo, bajo el velo, unos segundos, y cuenta como visto.
- No es un dato perdido: solo informa, y la fecha de la última copia sigue en Ajustes. Pero el encargo pide que nada de lo que sale solo al abrir se pinte por su cuenta.
- **Arreglo:** que la pantalla de la bóveda no tome `scheduledNotice` mientras `openingItem` enseña algo. Por ejemplo, un `CompositionLocal` con `showing != null` desde `VaultNav`, y `LaunchedEffect(scheduled, opening)` que espere a `false`. O dejarlo escrito en el comentario de cabecera de `Opening.kt` como lo que queda fuera, con su motivo. Que decida el controlador.

### Informativos (sin arreglo pedido)

- **i-1, «Haz y comprueba una copia» → «Ir» lleva a «Comprobar una copia»**, que no hace copias (`Missions.kt:67`, `MissionAction.CheckBackup`). El texto es el ejemplo del diseño. Quien no tiene ninguna copia tiene que ir a Ajustes → «Exportar». Al Abogado, por la regla de veracidad.
- **i-2, «Al cambiar una entrada se guarda su versión anterior.»** (novedades, 2/4). Es la frase de m-6 de la fase A, ya aceptada (`HISTORY_ALL_SHORT`, `TrashTexts.kt:97`).
  - Límites del código: la identidad y los contactos no tienen historial; `withVersion` no guarda nada si nada cambió (`EntryHistory.kt:37`); `fittedHistory` quita las más antiguas por encima de 20 versiones o 64 KiB, así que una entrada cerca de 64 KiB puede quedarse sin su versión anterior (`VaultLimits.kt:101-109`); y el tope de 24 MiB de la bóveda quita las más antiguas de toda la bóveda.
  - Ninguno es el caso normal. Al Abogado con el resto.
- **i-3, «Lo que borras pasa 30 días en la papelera.»** Coincide con `TRASH_SHORT`, ya aceptado (`TrashTexts.kt:13`).
  - La identidad y los contactos se borran del todo (Ruling T3, duda 2).
  - Con el reloj adelantado, la papelera se puede vaciar antes (Ruling T3, duda 3, límite documentado).
  - «Vaciar papelera» la vacía cuando se quiera.
- **i-4.** `MissionPostponements` usa el `lockEpoch` de cada `VaultStore`, que empieza en 0 con cada `VaultStore` nuevo.
  - Hoy no hay choque: un id de bóveda nueva es aleatorio y nunca se repite (`AppContainer.kt`, `deleteVault`: «a new vault never gets the same id»), y «He olvidado» conserva el mismo `VaultStore`, cuya época solo sube.
  - El `AppContainer.lockEpoch` de toda la app, que nunca vuelve a un valor, lo haría robusto sin depender de eso. No lo pido.
- **i-5.** Las misiones se calculan con un `now` tomado al recalcular (`HealthScreen.kt:127`) y no se rehacen al cambiar de día. La línea de `BackupCheckNoticeLine` sí lleva el día en la clave. Con «Salud» abierta más de un día, la misión de la copia podría ir con un día de retraso. Despreciable.

## Comprobaciones pedidas

### n-1 y n-2 (`VaultStore.kt`, `ActiveGateTest`)

- **n-1, `VaultStore.kt:699-701`.** La segunda comprobación `if (!active()) return key.fill(0)` va justo antes de `commit`, con el mutex tomado (`:661`).
  - El test provoca el cambio dentro de `trashClockMark` (`:676`), que se lee **después** de la primera comprobación (`:673`).
  - **Mutación:** sin la línea 701, `commit(..., mustSave = false)` llama a `saveBookkeeping(next)`, que escribe el archivo vuelto a cifrar con AES-GCM y un nonce nuevo, así que los bytes cambian siempre. `opened` ya rechazaba abrirla (`:497`, `!active()`), así que el estado `Locked` no distingue el arreglo. Lo que lo distingue es `assertArrayEquals(before, file().readBytes())`, y el test lo tiene. Fallaría sin el arreglo.
  - La segunda mitad (`armed = false`, misma clave, `Unlocked`) prueba que la regla de los 7 días y la del reloj no eran las que lo paraban.
- **n-2, `VaultStore.kt:584-587`.** `kdf()` solo se evalúa como argumento de `afterMasterUnlock`, dentro de `derive` (`:579`), después del Argon2id de `VaultCrypto.unlock` (`:577`) y después de la primera comprobación (`:573`). Ni `load()` ni la primera comprobación lo llaman, y `create` va antes de `armed = true`.
  - **Mutación:** sin `:584-587`, `onClockReset(now())` sube `resets` a 1 y `commit` escribe. Fallan dos aserciones: el archivo byte a byte y `resets`. El test es bueno.
- **El KDoc de `unlockWithKey`** (`:654-656`) dice lo que hace el código.

### `openingItem` (`settings/Opening.kt`) y su test

- **Pura:** solo lee su `OpeningState`, sin E/S ni estado global (`Opening.kt:52-66`).
- **Exhaustivo de verdad:** `OpeningState` tiene 12 campos: 9 booleanos, `VaultPhase` (4 valores) y `GuideKind?` (3). `2 × 4 × 2⁹ × 3 = 12 288`, y el bucle recorre los 12 campos sin fijar ninguno (`OpeningTest.kt`, `all()`).
- **No es tautológico del todo:**
  - `wanted()` vuelve a escribir las condiciones a partir del diseño y elige por el orden del `enum`, no por el `when`. Un cambio de orden en el `when` o de condición en uno de los dos lados lo detecta.
  - Comparte con la implementación la idea de `calm`. Por eso valen las tres propiedades independientes:
    - `theNoticeBeforeAnything`;
    - `theGuideNeverDelaysTheUpgradeNorItsCopy`;
    - `theGuideNeverCoversTheLockNorADelivery`, que comprueba el bloqueo, la carga, la entrega y la pestaña por separado.
  - Suficiente.
- **`AppNavHost` pinta cada diálogo solo cuando lo elige** (`AppNavHost.kt:274-307`): los seis diálogos de `VaultNav` (A6, el nombre, el aspecto, la guía, «Bóvedas» y la maestra) van detrás de `showing ==`. No queda ninguno suelto en `AppNavHost.kt`.
- **He buscado diálogos o avisos que salgan solos al arrancar** en `ui/`, `MainActivity` y `SeguridadApp`:
  - los demás `AlertDialog` responden a un toque del usuario (`ClipboardHistoryNotice`, A14; confirmaciones de borrar; `PasswordDialog`; los de «Bóvedas»; el de StrongBox en Ajustes);
  - `BackupCheckNoticeLine` es una línea dentro de la pantalla, no un diálogo;
  - la huella automática de la puerta (`VaultScreen.kt:438`) solo corre con la bóveda bloqueada, cuando la guía no puede salir.
  - Ningún diálogo de antes quedó fuera de la función, salvo el paso a v4 y su línea, que la función elige y pinta `VaultGate` (I-1). Fuera quedan dos snackbars que salen solos: el de la copia con calendario y el de «Activar huella» interrumpida (m-9).
- **El paso a v4 y su copia nunca se retrasan:** `UPGRADE` y `UPGRADE_DONE` van primero tras `NOTICE`, y la copia la hace `afterUnlock` en el almacén, sin pasar por la interfaz. Lo que sí puede pasar es que no se pinten (I-1).
- **O-7:**
  - `NoticeScreen` sale antes de que se componga `VaultNav` (`AppNavHost.kt:180-193`);
  - `noticeAccepted` y `guideSeen` son campos distintos;
  - «Saltar» solo llama a `GuideHolder.done()`, que escribe `withGuideSeen()`.
- **La guía nunca tapa el bloqueo:** exige `OPEN` o `NONE` (`Opening.kt:55`, `:64`). Al bloquear, sale de la composición, y la huella automática solo corre en `UnlockVault`. Con un QR o un selector en camino espera (m-8: espera de más, nunca de menos), y solo sale en una pestaña.

### Lo que pasa a ir en cola

- **Nadie marca un aviso como visto sin enseñarlo.** Lo marcan solo al cerrar el diálogo:
  - `OnceNotice.closed()` (`RenameNotice.kt:51-56`); `showIfDue` no escribe nada;
  - `replaceOutcomeShown` (`AutoBackup.kt:392`), con comparación por identidad, sin cambios;
  - `vaultsNoticeShown` (`AppContainer.kt:736`);
  - `masterChanged.close` (desde el diálogo).
- **Ninguno se descarta por esperar:** las fuentes son `StateFlow`, que siguen teniendo su valor hasta que se cierra el diálogo.
- **Coincidencias reales:**
  - el resultado de A6 detrás del diálogo de la maestra: sale al cerrarlo. Puede repetir algo, pero no pierde nada;
  - el aviso de «Bóvedas» detrás del paso a v4: sale tras la elección.
- **Hoy `Notice.post` ya sustituye un aviso pendiente por el último.** No es nuevo; esperar más lo hace un poco más probable solo si el usuario lanza dos acciones de «Bóvedas» seguidas, y la segunda no se puede lanzar con un diálogo modal encima.
- **Los textos de A6 no cambian:** el diff no toca `a6Text`, `a6Decide` ni `a6Summary`, y `MasterChangedDialog` recibe los mismos parámetros (`AppNavHost.kt:292-307`).

### Guías

**Cada afirmación contra el código, en las API de la 26 a la 36:**

| Afirmación | Dónde está en el código | ¿Es verdad? |
|---|---|---|
| «hasta 3 carpetas» | `VaultLimits.BACKUP_TARGETS_MAX = 3`; tope en `SettingsScreen.kt:404` | Sí |
| las copias con calendario solo al abrir Clavero | `AutoBackup.kt:294-297`, `:530`; `MainActivity.onStart` → `appOpened`; ni WorkManager ni permisos | Sí; la frase de la página 2 de la completa, m-3 |
| la huella «en el menú de la bóveda abierta» | `VaultScreen.kt:213-232`, solo con `Unlocked` y `available` | Sí, salvo «tiene huella»: m-4 |
| la huella «cada 7 días» | `MASTER_RECHECK_MS` | Sí |
| «hasta 10 bóvedas… una abierta a la vez» | `VAULTS_MAX = 10`, `VaultRegistry.kt:99`; `switchTo` bloquea la abierta (fase A y tarea 2) | Sí; ningún texto habla de ocultar (O-1, con test) |
| papelera, 30 días | `TRASH_DAYS = 30`, `Trash.kt:17`, `:31` | Sí, como `TRASH_SHORT` (i-3) |
| historial, «se guarda su versión anterior» | `withVersion` | Sí en el caso normal; límites en i-2 |
| los cuatro tipos nuevos | `Vault.kt:43-46`: «Tarjeta bancaria», «Red wifi», «Licencia», «Pasaporte» | Sí |

Ninguna afirmación depende de la versión de Android. No sale nada de fuera de la 2.0: el icono cambiable no está, y hay test.

**Quién ve cuál:**
- **Desde la 1.4.3:** `decidedAtFirstStart` corre antes del primer fotograma (`SeguridadApp.kt:74-77`, `runBlocking`) y pone `GUIDE_BEFORE_2_0`. Le sale la de novedades (`GuideTest.whoUpdatedGetsWhatIsNew`, con el `settings.json` real).
- **Instalación nueva:** `GUIDE_NONE`, la completa, encima de «Crea tu bóveda» y después de «Antes de empezar».
- **Quien actualiza con la bóveda bloqueada** (lo normal): la guía espera al desbloqueo, al paso a v4 y a sus avisos. Es el orden correcto.

**Si no se puede guardar «vista»:** `SettingsStore.update` lanza la excepción sin cambiar la memoria (el test lo comprueba: `guideSeen` sigue en `GUIDE_NONE`). `_dismissed` impide un bucle en esta ejecución, y el siguiente arranque la vuelve a enseñar. Es el patrón de `noticeAcceptedNow` (`AppNavHost.kt:179-190`). Ver la Duda 3.

**Volver atrás es «Saltar»:** sí (`onDismissRequest = onDone`). También lo es un toque fuera de la tarjeta, que no debería serlo: m-2.

**Ver una guía desde Ajustes la marca como vista:** sí (`done()` siempre llama a `withGuideSeen`). Con la bóveda bloqueada, m-1.

### Misiones

- **Fuera del hilo principal y cancelado al bloquear:**
  - `produceState` con `withContext(Dispatchers.Default)` (`HealthScreen.kt:125-130`), dentro de la rama abierta, después del `return@BackScaffold` (`:119`);
  - un bloqueo quita la rama: se cancela la corrutina y se pierde el estado con los títulos.
- **Nunca guardado:** nada en `rememberSaveable` en `HealthScreen.kt`. El test lo busca en la fuente, y he comprobado el archivo.
- **Solo ids:** `AppContainer` solo guarda `MissionPostponements`, con ids de tipo de misión por bóveda y época (`Missions.kt:82-100`).
- **Texto plano:** `plainReportText(..., 60)` en el título (`Missions.kt:63`), con test de U+202E.
- **«Ahora no», por bóveda y hasta el siguiente desbloqueo:**
  - solo en memoria: la época sube con cada `lock()` (`VaultStore.kt:739`) y el cambio de bóveda bloquea la que se deja;
  - `MissionsVaultTest` usa el `AppContainer` real y un cambio real de bóveda;
  - fallaría si `MissionPostponements` no mirara el id, porque las dos bóvedas tienen la época 0 en ese momento.
- **`pressureWords`:** cuenta palabras enteras (`[\p{L}\p{N}]+`) y busca «!» y «¡» en cualquier parte (`Missions.kt:101-107`). Los casos de «haya» y «ya» tienen test. Cobertura de los textos: m-5.
- **R-2:** `missionsOff` → `emptyList()` (`HealthScreen.kt:126`); `HealthScreen` tiene una sola llamada (`AppNavHost.kt:386`), que pasa `settings.missionsOff` y `container.missionPostponements`: ningún otro camino usa los valores por defecto. Falta un test: m-6.
- **O-6:**
  - `vaultHealth(data.live, now)` va sin el mapa de filtraciones;
  - `MissionAction` solo tiene `EditEntry` y `CheckBackup`, que solo navegan (`AppNavHost.kt` en la ruta `vault/health`);
  - el título del 2FA es solo el de la entrada.
- **Rutas:**
  - `vault/edit?id=` lleva el id codificado (LIBRE-1);
  - `settings/check-backup` es un destino;
  - `SimpleModeTest` comprueba los dos con una regex sobre `composable("…`, y la navegación no lee el modo sencillo.
- **La misión de la copia:** usa exactamente `backupCheckNotice(data.lastCheckedBackupAt, now) != null` (`Missions.kt:67`), la misma función y la misma fecha que `BackupCheckNoticeLine` (`VaultBackup.kt:274`). El test recorre los casos de nunca, de más de 30 días, de menos y de fecha adelantada.

### «Salud» sin bóveda y bloqueada

- Sin bóveda, `HEALTH_NO_VAULT` (`HealthScreen.kt:114-115`).
- Bloqueada o cargando, `VaultGate` con el contenido vacío (`:117`). No se compone nada de la bóveda hasta que está abierta: `entries == null` va antes de todo lo demás.
- La huella es la de `store`, la bóveda que se enseña.
- Falta la parte de abierta dentro de la puerta: I-1.
- **C-1:**
  - la clave fija es `"mission"` y las filas de datos usan `lazyKey('e', id)` (`:244`), que tiene «:»;
  - los demás `item {}` de esa lista no llevan clave: Compose les da claves de posición, de otro tipo, que no chocan con un `String`;
  - `PlatformSourcesTest` («C-1») recorre todas las pantallas y exige que ninguna clave fija tenga «:».
  - Ninguna clave fija puede coincidir con la de una fila de datos.

### Ajustes

- `SettingItem.MISSIONS` y `SettingItem.GUIDE` están en el índice (`SettingsSearch.kt:66-67`). Sus anclas se pintan en `SettingsScreen.kt:674`, `:679`, y `SettingsSearchTest` cuenta que cada entrada se pinta.
- `missionsOff` vale `false` por defecto. `appJson` no escribe los valores por defecto (`explicitNulls = false`, sin `encodeDefaults`) e ignora las claves que no conoce (`Json.kt:6-10`):
  - el `settings.json` de la 1.4.3 y el de la fase A se leen igual;
  - una vuelta a una versión anterior ignora la clave.
  - Falta el test: m-6.

### Textos

- En español y sin palabras prohibidas: `ForbiddenWords` en `GuideTest` y `MissionsTest`, y `SourceTextsTest` recorre todo `app/src/main/java`.
- No nombran otras apps.
- M-3: una o dos frases por texto, y nada plegado.
- `GUIDE_STEP` («N de M») es un formato.

### Fuera de alcance y lo que no cambia

- No cambian los formatos (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`), los permisos, el manifiesto, las dependencias, la red ni los registros (`git diff --stat`: 19 archivos, todos de esta fase).
- `QrHandoff.hasPending` solo lee.
- `VaultStore` gana una línea y su KDoc.

## Opinión sobre las dudas del informe

1. **`missions(items: List<HealthItem>, …)` en lugar de `HealthReport`:** aceptada. `HealthReport` no existe, y `items` es lo que «Salud» ya calcula.
2. **«Ahora no» por tipo de misión:** aceptada. El diseño dice «se oculta hasta el siguiente desbloqueo» y no habla de entradas. Por tipo, no se encadenan cinco «Activa el 2FA» seguidas, y eso va con R-2 (sin presión).
3. **Si falla «vista», vuelve en el siguiente arranque:** aceptada. Es la lectura razonable de «la guía vuelve»: no se marca como vista si no se guardó. Hacerla volver en la misma ejecución, con el disco lleno, sería un bucle. Coincide con `noticeAcceptedNow` y con `OnceNotice`. Propongo que el controlador lo deje escrito como Ruling.
4. **Atrás = «Saltar»:** aceptada. Es lo de todos los diálogos de la app. Un toque fuera no debería saltarla: m-2.
5. **El orden:** aceptado. He comprobado que nada se pierde ni se marca como visto sin verse. El paso a v4 y su línea necesitan que alguien los pinte en todas las pantallas: I-1.
6. **Ver desde Ajustes la marca como vista:** aceptada, con m-1 (con la bóveda bloqueada, el toque no hace nada y la guía sale sola después; propongo desactivar los botones).

## Revisión experto-seguridad-cripto

- **Decisión (b), ningún resultado tardío:**
  - la huella vuelve a mirar si la bóveda es la activa justo antes de `commit`, con el mutex tomado (`VaultStore.kt:661`, `:701`);
  - la maestra lo mira después de Argon2id y antes de `onClockReset` (`:584-587`);
  - los dos tests comparan el archivo byte a byte y fallarían sin el arreglo (lo he razonado más arriba).
  - La ventana que queda es la del guardado mismo, con el mutex. No veo más carrera.
- **Datos de la bóveda en las misiones:**
  - el título de la entrada solo vive en el `produceState` de la rama abierta (`HealthScreen.kt:125`);
  - se muestra con `plainReportText` (`Missions.kt:63`);
  - nunca va en `rememberSaveable`, en un `Bundle` ni en `AppContainer`, que solo guarda ids de tipo (`Missions.kt:82`).
  - No sale en registros (no hay `Log` en el diff).
- **O-6:** ninguna misión llama a `PwnedPasswords`. `missions` no recibe el mapa de filtraciones (`HealthScreen.kt:128`), y los destinos son de navegación.
- **O-3:** el paso a v4 nunca se retrasa por la guía. Pero en el camino nuevo de «Salud» su aviso no sale (**I-1**). Es un requisito del Abogado («a la vista»), y la línea de después puede perderse si Android cierra el proceso.
- **O-7:** «Antes de empezar» va aparte (`AppNavHost.kt:180-193`) y la guía no lo puede saltar.
- **O-1:** ningún texto presenta las bóvedas como forma de ocultar. `GuideTest` busca «ocult», «escond» y «secret».
- **Tapjacking:** `FilterObscuredTouches()` en el texto de `GuideDialog` (`OnboardingScreen.kt:45`), y el `AlertDialog` del tema lo pone en el botón de confirmar (`Components.kt:256`).
- **C-1:** `lazyKey('e', …)` en las filas de «Salud» (`HealthScreen.kt:244`). Un id de entrada importado no puede repetir `"mission"`.
- **La puerta de «Salud»:** con la bóveda bloqueada no compone nada de la bóveda (`HealthScreen.kt:111-119`), y la huella es la de la bóveda que se enseña.
- **MASVS:**
  - STORAGE: los títulos no van a estado guardado, `missionsOff` y `guideSeen` no dicen nada de la bóveda;
  - PLATFORM: no hay componentes exportados ni `Intent` nuevos;
  - PRIVACY: sin red.
  - No encuentro ningún `Log`, `print` ni `setJavaScriptEnabled` en el diff.
- **Nada nuevo** en formatos, permisos, red, registros ni dependencias. `settings.json` gana `missionsOff`, sin cifrar, que no dice nada de la bóveda (a la fila 8 de la política).

## Revisión Ingeniero Móvil Senior

- **Estado y ciclo de vida:**
  - `GuideHolder` y `MissionPostponements` viven en `AppContainer` (`AppContainer.kt:195-199`);
  - la página de la guía es un `Int` en `rememberSaveable` (`OnboardingScreen.kt:38`), no un secreto;
  - `postponed` es `remember(epoch)` con ids;
  - `produceState(null, data, missionsOff)` rehace el cálculo cuando cambia la bóveda y se cancela al salir de la composición.
- **Recomposición:**
  - `openingItem` es barata y pura;
  - `deliveryPending` lee estado que no se observa (**m-8**);
  - `guideKind` es `remember(settings, guideRequested, guideDismissed)`.
- **Concurrencia:** `GuideHolder.done()` guarda en `appScope` con `NonCancellable` (`Guide.kt:103-117`), y las misiones van en `Dispatchers.Default`.
- **Diálogos:**
  - todos los de arranque los elige una sola función. El paso a v4 aún lo pinta otro sitio (**I-1**);
  - un toque fuera salta la guía (**m-2**);
  - los botones de Ajustes no hacen nada con la bóveda bloqueada (**m-1**);
  - dos snackbars que salen solos al abrir no pasan por la cola (**m-9**).
- **Accesibilidad (R-1):**
  - región viva educada en el texto de cada página (`OnboardingScreen.kt:47`);
  - el texto se desplaza (`verticalScroll`), así que al 200 % no se corta. Queda la fila del móvil;
  - «Ir» y «Ahora no» son dos `OutlinedButton` con `weight(1f)` (`HealthScreen.kt:146-153`).
- **Navegación:**
  - una misión navega por `routeBase` con el id codificado;
  - «Salud» bloqueada usa `VaultGate`, pero la parte abierta debería ir dentro (**I-1** (b)).
  - La guía solo sale en una pestaña (`onTab != null`).
- **Tests:**
  - `OpeningTest` es exhaustivo de verdad;
  - `ActiveGateTest` es bueno;
  - los huecos están en **m-5**, **m-6** y **m-7**.

## Textos para el Abogado Digital (revisión final)

1. **Guía completa** (`settings/Guide.kt:36-50`):
   - «La contraseña maestra» / «Abre tu bóveda. Si la olvidas, nadie puede recuperar lo guardado.»;
   - «La copia» / «En Ajustes puedes exportar una copia o elegir hasta 3 carpetas para la copia automática. Las que tienen calendario solo se hacen al abrir Clavero.» (propuesta m-3: «Las copias con calendario solo se hacen al abrir Clavero.»);
   - «La huella» / «Si el móvil tiene huella, puedes activarla en el menú de la bóveda abierta. Por seguridad, cada 7 días pide la contraseña maestra.» (propuesta m-4: «Si tienes una huella registrada en el móvil, …»);
   - «Varias bóvedas» / «Puedes tener hasta 10 bóvedas, cada una con su contraseña maestra. Solo hay una abierta a la vez.» (O-1).
2. **Novedades de la 2.0** (`Guide.kt:53-62`), también para el dueño:
   - «Varias bóvedas y carpetas»;
   - «Papelera e historial» (límites en i-2 e i-3);
   - «Copias en varias carpetas» (O-4);
   - «Tipos nuevos».
3. **Los botones y Ajustes:** «Saltar», «Siguiente», «Entendido», «N de M», y en Ajustes «Guía», «Ver la guía» y «Ver las novedades de la 2.0».
4. **Misiones (R-2, O-6):**
   - «Cambia N contraseñas repetidas / débiles / antiguas» (y en singular);
   - «Activa el 2FA en «título»» / «Activa el 2FA en un acceso»;
   - «Haz y comprueba una copia», que lleva a «Comprobar una copia», que no hace copias (i-1);
   - «Ir» y «Ahora no», del mismo peso;
   - el ajuste «Sugerencias de «Salud»» / «Enseñar sugerencias» / «Una sugerencia cada vez en «Salud de la bóveda», con «Ahora no». Ninguna se conecta a internet.».
5. **«Salud» sin bóveda:** «Aún no tienes bóveda. Créala en la pestaña Bóveda.».
6. **Política (fila 8):** `settings.json` gana `missionsOff`, sin cifrar.
7. **O-3:** si se acepta I-1 (a), el diálogo del paso a v4 y su línea salen en cualquier pantalla con la bóveda abierta, no solo en las de la bóveda. Los textos no cambian.
