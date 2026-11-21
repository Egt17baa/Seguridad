# Tarea 10, fase B: informe del implementador

## Estado

**Hecha.** Las dos suites están en verde (las cifras, abajo). Commits sin subir y sin PR. Base `2ecfee5`.

Esta fase tiene seis partes:
- n-1 y n-2 de la fase A, en su propio commit;
- lo que se enseña al abrir la app, de uno en uno;
- las dos guías;
- las misiones, con el ajuste R-2;
- «Salud» con la bóveda bloqueada y sin bóveda (m-2);
- las guías y el ajuste de las misiones en Ajustes y en su buscador.

Los textos de error son de la fase C y no se han tocado.

## Commits

| Commit | Qué |
|---|---|
| `6cc6966` | «2.0, tarea 10: n-1 y n-2 de la fase A»: segunda comprobación de bóveda activa antes de `commit` en la huella, el test de la comprobación de después de Argon2id y la línea que faltaba en el KDoc de `unlockWithKey` |
| `4d2ac39` | Misiones, lo que se enseña al abrir y las dos guías: lógica y tests |
| `ec301dd` | En pantalla: la guía, las misiones, «Salud» bloqueada o sin bóveda, y Ajustes con las guías y el ajuste R-2 |
| `1d559b2` | Un acceso sin título; el ciclo de vida de los títulos de las misiones |
| `f64e738` | La clave de las filas de «Salud» con `lazyKey` (C-1). La suite completa lo encontró: la fila fija «mission» podía coincidir con el id de una entrada, que es cualquier texto que traiga una copia, y eso cierra la app |
| `9827beb` | «2.0, tarea 10 (fase B): misiones, guías y Salud» (cierre; este informe no entra en git) |

## n-1 y n-2 (primero, en su commit)

- **n-1.** `unlockWithKeyLocked` vuelve a mirar `active()` justo antes de `commit` (`VaultStore.kt:701`). La ventana de la huella queda así tan corta como la de la maestra.
  - Test: `vault/ActiveGateTest.aSwitchDuringTheFingerprintsDecryptionWritesNothing`. El cambio de bóveda se provoca dentro de `trashClockMark`, que se lee después de la primera comprobación.
  - Sin el arreglo, el test falló: el archivo pasó de 368 bytes a otro tamaño. Con el arreglo, el archivo queda igual byte a byte, la bóveda bloqueada, y la misma clave abre después.
- **n-2.** `ActiveGateTest.aSwitchDuringArgon2idWritesNothing`. El cambio de bóveda se provoca dentro de `kdf()`, que lee la comprobación de la maestra después de Argon2id.
  - El test comprueba que el archivo queda igual byte a byte, el estado es `Locked` y no se llama a `onClockReset`.
  - Quitando la comprobación de después de Argon2id (`:584-587` entonces), el test se pone en rojo.
- **KDoc de `unlockWithKey`:** dice ahora que una bóveda que no es la activa vuelve sin lanzar ni escribir. Lo comprueba al principio y justo antes de guardar.
- **R1-2, corregido:** la ventana que queda es lo que pasa entre la última comprobación y la escritura. En la maestra son `onClockReset` y el guardado; en la huella, el guardado.

## 1. Lo que se enseña al abrir

Salido del código; el comentario de cabecera de `settings/Opening.kt` lo resume. Cada elemento sale cuando se cumple su condición y no hay otro antes en la lista:

| # | Qué | De dónde | Cuándo sale |
|---|---|---|---|
| 1 | «Antes de empezar» | `NoticeScreen`, `AppNavHost` | Siempre antes que todo, hasta aceptarlo |
| 2 | Elegir antes del paso a v4 (O-3) | `VaultUpgradeDialog`, en `VaultGate` | Con la bóveda abierta |
| 3 | La línea de la copia hecha en el paso a v4 | `VaultUpgradeDoneNotice` | Con la bóveda abierta |
| 4 | El diálogo tras cambiar la maestra (A6) | `MasterChangedDialog` | — |
| 5 | Lo que dijo una acción de «Bóvedas» | `vaultsNotice` | — |
| 6 | Cómo fue un reemplazo de copias pedido con la bóveda bloqueada (A6) | `ReplaceOutcomeDialog` | — |
| 7 | El aviso del nombre nuevo | `RenameNotice` | Con la bóveda abierta o sin bóveda |
| 8 | La línea del aspecto nuevo (R-C8) | `AppearanceNotice` | Con la bóveda abierta o sin bóveda |
| 9 | La guía: completa, de novedades o la pedida en Ajustes | `GuideHolder` | Con la bóveda abierta o sin bóveda, en una pestaña y sin nada de la app en camino |

«Nada en camino» quiere decir que no hay un QR entregado ni retenido para una pantalla de la bóveda (`QrHandoff.hasPending`, `QrHandoff.kt:45`) y que no hay un selector ni el escáner abiertos (`VaultLock.keepOpenOnce`).

Lo que no sale solo:
- **No salen al abrir, porque responden a algo que hace el usuario:** el aviso del portapapeles (A14), los snackbars y la oferta de «Copiar contraseña» al volver.
- **La app no recibe archivos ni textos de otras apps.** No tiene filtro `ACTION_SEND` (`AndroidManifest.xml`: solo `MAIN`/`LAUNCHER`). Las únicas entregas son suyas: el QR y los selectores.

**La función.** `openingItem(OpeningState)` (`settings/Opening.kt:52`) es pura y devuelve uno solo. `AppNavHost` la llama (`AppNavHost.kt:253`) y pinta cada diálogo solo cuando lo elige (`:274-291`). El diálogo del paso a v4 lo pinta `VaultGate`; mientras espera, la función no deja salir nada más.

**Tests.** `settings/OpeningTest`:
- recorre las 12 288 combinaciones (`oneAtATimeInEveryCombination`): en cada una sale el primero, en el orden de `OpeningItem`, de los que se cumplen, y solo ese;
- `theNoticeBeforeAnything`: «Antes de empezar» sale antes que todo (O-7);
- `theGuideNeverDelaysTheUpgradeNorItsCopy`: la guía nunca retrasa el paso a v4 ni su copia;
- `theGuideNeverCoversTheLockNorADelivery`: nunca tapa el bloqueo, nunca sale con la bóveda cargando, con una entrega en camino ni fuera de una pestaña;
- `theScreenDrawsWhatItPicks`: comprueba en el código fuente que cada diálogo de `AppNavHost` va detrás de `showing == OpeningItem.X`.

## 2. Las dos guías

- **Pantalla:** `GuideDialog` (`ui/screens/OnboardingScreen.kt`). Una página cada vez, con «Siguiente» y «Saltar». Volver atrás también salta (`:42`). La última página tiene «Entendido».
  - El texto se desplaza, así que no se corta al 200 %.
  - Solo hay textos fijos.
  - La página que se ve es un número, guardado con `rememberSaveable`.
- **Guardar «vista»:** `GuideHolder` (`settings/Guide.kt:85`, en `AppContainer`).
  - Al terminar o al saltar, `done()` (`:103`) guarda `withGuideSeen()` en el ámbito de la app, sin que se pueda cancelar.
  - Si la escritura falla, la guía no vuelve en esta ejecución, para no entrar en un bucle, pero vuelve en el siguiente arranque, porque nada cambió en disco (`GuideTest.aFailedSaveBringsItBackAtTheNextStart`).
- **Desde Ajustes:** la sección «Guía» tiene «Ver la guía» y «Ver las novedades de la 2.0» (`SettingsScreen.kt:679`). Está en el índice del buscador (`SettingItem.GUIDE`), y los tests de índice la cubren.
- **Quién ve cuál:**
  - instalación nueva: la completa;
  - quien actualiza desde la 1.x: la de novedades (`GuideTest.whoUpdatedGetsWhatIsNew`, con el `settings.json` real de la 1.4.3);
  - quien ya la vio: ninguna.

### Guía completa (instalación nueva): las 4 pantallas del plan

1. **La contraseña maestra.** «Abre tu bóveda. Si la olvidas, nadie puede recuperar lo guardado.» Es la misma idea que `NOTICE_NO_RECOVERY_SHORT`.
2. **La copia.** «En Ajustes puedes exportar una copia o elegir hasta 3 carpetas para la copia automática. Las que tienen calendario solo se hacen al abrir Clavero.» Se comprueba con `VaultLimits.BACKUP_TARGETS_MAX` y cumple O-4.
3. **La huella.** «Si el móvil tiene huella, puedes activarla en el menú de la bóveda abierta. Por seguridad, cada 7 días pide la contraseña maestra.» El menú solo la ofrece si `BiometricVault.available` (`VaultScreen.kt:220`); la regla de los 7 días es la de siempre.
4. **Varias bóvedas.** «Puedes tener hasta 10 bóvedas, cada una con su contraseña maestra. Solo hay una abierta a la vez.» Se comprueba con `VAULTS_MAX` y con `switchTo`, que bloquea la abierta. No presenta las bóvedas como forma de ocultar nada (O-1).

### Novedades (quien actualiza): la lista para el dueño

Cuatro pantallas de una o dos frases. Ninguna depende de la versión de Android: el difuminado, que sí depende, queda fuera.

1. **Varias bóvedas y carpetas.** «Hasta 10 bóvedas, cada una con su contraseña, y carpetas dentro de cada una.» Tareas 2 y 6: `VAULTS_MAX`, `FOLDERS_MAX`.
2. **Papelera e historial.** «Lo que borras pasa 30 días en la papelera. Al cambiar una entrada se guarda su versión anterior.» Tarea 3: `TRASH_DAYS`. El historial usa la misma frase que m-6 de la fase A.
3. **Copias en varias carpetas.** «Hasta 3 carpetas, cada una con su calendario. Las copias con calendario solo se hacen al abrir Clavero.» Tarea 8: `BACKUP_TARGETS_MAX`, `BackupSchedule`; cumple O-4.
4. **Tipos nuevos.** «Tarjeta bancaria, red wifi, licencia y pasaporte, cada uno con sus campos.» Tarea 5: `EntryType.CARD/WIFI/LICENSE/PASSPORT`, con las etiquetas de R-4.

No entran el icono cambiable, que no está en la 2.0 (el test lo comprueba), ni el aspecto «Cristal», que ya lo dice la línea de aspecto R-C8.

`GuideTest.thePages` comprueba:
- 4 pantallas en la completa y como mucho 4 en las novedades, de una o dos frases;
- que ningún texto lleva palabras prohibidas ni «ocult…», «escond…» o «secret…» (O-1, O-7);
- que los números salen de las constantes;
- que dicen «cada 7 días» y «al abrir Clavero».

## 3. Misiones

- **Cálculo:** `missions(items, data, now)` (`health/Missions.kt:55`), con lo que «Salud» ya calcula. El orden es: repetidas, débiles, un acceso sin 2FA, antiguas y la copia.
  - Las de la copia salen cuando `backupCheckNotice(data.lastCheckedBackupAt, now)` dice que toca: la misma función con la misma fecha (`theCopysMissionIsTheNoticesRule`).
  - Nunca se calculan con la comprobación de filtraciones, así que ninguna lleva a la red (O-6, `noMissionGoesOnline`).
- **En pantalla:** una cada vez (`nextMission`), arriba de «Salud» (`HealthScreen.kt:146`), con «Ir» y «Ahora no» del mismo peso (R-2: dos `OutlinedButton` con `weight(1f)`).
- **Una misión solo navega.** `MissionAction` lleva al editor de la entrada (`vault/edit`) o a «Comprobar una copia» (`settings/check-backup`), por su `routeBase` (`AppNavHost.kt`).
- **Los títulos con el nombre de una entrada son datos de la bóveda:**
  - se muestran como texto plano (`plainReportText`);
  - se calculan en `Dispatchers.Default`, dentro de la rama de la bóveda abierta de «Salud» (`HealthScreen.kt:125`), así que un bloqueo cancela el cálculo y los quita;
  - nunca van en un aviso que dure más, ni en estado guardado;
  - lo que sobrevive solo guarda ids (`MissionsTest.titlesLiveOnlyWithTheOpenVault`).
- **«Ahora no»:** `MissionPostponements` (`Missions.kt:82`, en `AppContainer`) guarda por bóveda y por `lockEpoch`, solo en memoria. Un bloqueo, incluido el que hace un cambio de bóveda, lo termina (`postponedUntilNextUnlock` y `MissionsVaultTest`, con `AppContainer` real y cambio de bóveda).
- **Sin presión:** `noPressureWords` compara palabras enteras («haya» pasa y «ya» no) y busca «!» y «¡» en cualquier sitio. Ninguna misión lleva rachas, puntos ni cuentas atrás.
- **R-2:** el ajuste «Sugerencias de «Salud»» → «Enseñar sugerencias» (`Settings.missionsOff`, `SettingsScreen.kt:674`) las apaga todas. Está en el índice del buscador (`SettingItem.MISSIONS`).
- **Rutas:** los destinos de `MissionAction` están en el test de rutas, que también vale con el modo sencillo (`SimpleModeTest.everyToolRouteStaysReachable`).

## 4. «Salud» desde Herramientas (m-2)

- **Sin bóveda:** «Aún no tienes bóveda. Créala en la pestaña Bóveda.» (`HealthScreen.kt:115`).
- **Con la bóveda bloqueada o cargando:** `VaultGate` en su sitio (`:117`): la pantalla de desbloqueo de esa bóveda, con su huella. Al abrirla, aparece «Salud».

## Tests

- `:app`: **BUILD SUCCESSFUL**: 2349 tests, 0 fallos, 0 omitidos; assemble, lint, licencias, Fragment y assets en verde (sobre `f64e738`).
- jvm-check: **BUILD SUCCESSFUL**: 2305 tests, 0 fallos, 0 omitidos (sobre `f64e738`, por separado).
- Antes, las clases tocadas, en jvm-check: `ActiveGateTest` (2), `MissionsTest` (10), `OpeningTest` (5), `GuideTest` (6), `MissionsVaultTest` (1), `SimpleModeTest`, `SettingsSearchTest`, `SettingsTest`, `SourceTextsTest` y los demás de `settings/` y `health/`, en verde. También `:app:compileDebugKotlin`.
- **Rojo visto:** n-1 (sin el arreglo), n-2 (con la mutación) y las clases nuevas de misiones, guías y apertura (sin compilar antes de escribir el código).

## Tabla de textos nuevos

| Texto | Cuándo se ve | `archivo:línea` | Test |
|---|---|---|---|
| «La contraseña maestra» / «Abre tu bóveda. Si la olvidas, nadie puede recuperar lo guardado.» | Guía completa, 1/4 | `settings/Guide.kt:37` | `GuideTest.thePages` |
| «La copia» / «En Ajustes puedes exportar una copia o elegir hasta 3 carpetas para la copia automática. Las que tienen calendario solo se hacen al abrir Clavero.» | Guía completa, 2/4 | `settings/Guide.kt:38` | Ídem («3 carpetas», «al abrir Clavero») |
| «La huella» / «Si el móvil tiene huella, puedes activarla en el menú de la bóveda abierta. Por seguridad, cada 7 días pide la contraseña maestra.» | Guía completa, 3/4 | `settings/Guide.kt:43` | Ídem («cada 7 días») |
| «Varias bóvedas» / «Puedes tener hasta 10 bóvedas, cada una con su contraseña maestra. Solo hay una abierta a la vez.» | Guía completa, 4/4 | `settings/Guide.kt:47` | Ídem («10 bóvedas») |
| «Varias bóvedas y carpetas» / «Hasta 10 bóvedas, cada una con su contraseña, y carpetas dentro de cada una.» | Novedades, 1/4 | `settings/Guide.kt:55` | Ídem |
| «Papelera e historial» / «Lo que borras pasa 30 días en la papelera. Al cambiar una entrada se guarda su versión anterior.» | Novedades, 2/4 | `settings/Guide.kt:56` | Ídem («30 días») |
| «Copias en varias carpetas» / «Hasta 3 carpetas, cada una con su calendario. Las copias con calendario solo se hacen al abrir Clavero.» | Novedades, 3/4 | `settings/Guide.kt:57` | Ídem |
| «Tipos nuevos» / «Tarjeta bancaria, red wifi, licencia y pasaporte, cada uno con sus campos.» | Novedades, 4/4 | `settings/Guide.kt:61` | Ídem |
| «Saltar», «Siguiente», «Entendido» | Botones de la guía («Saltar» en todas menos la última) | `settings/Guide.kt:67-69` | `GuideTest.theWordsOfTheGuide` |
| «N de M» | Bajo cada página | `settings/Guide.kt:72` | (formato; sin palabras) |
| «Guía» / «Ver la guía» / «Ver las novedades de la 2.0» | Sección de Ajustes | `settings/Guide.kt:75-77`; `SettingsScreen.kt:679` | `GuideTest.theWordsOfTheGuide`; `SettingsSearchTest` (índice y dibujo) |
| «Cambia N contraseñas repetidas» / «Cambia 1 contraseña débil» / «Cambia N contraseñas débiles» / «Cambia 1 contraseña antigua» / «Cambia N contraseñas antiguas» | Misión arriba de «Salud» | `health/Missions.kt:72` | `MissionsTest.oneAtATime`, `noPressureWords` |
| «Activa el 2FA en «título»» / «Activa el 2FA en un acceso» | Misión (el título de la entrada es dato de la bóveda) | `health/Missions.kt:64` | `MissionsTest.oneAtATime`, `anUntitledLoginHasNoEmptyQuotes`, `noMissionGoesOnline` |
| «Haz y comprueba una copia» | Misión, cuando `backupCheckNotice` lo dice | `health/Missions.kt:67` | `MissionsTest.theCopysMissionIsTheNoticesRule` |
| «Ir» / «Ahora no» | Botones de la misión, del mismo peso | `health/Missions.kt:41-42` | `MissionsTest.theWordsWordForWord` |
| «Sugerencias de «Salud»» / «Enseñar sugerencias» / «Una sugerencia cada vez en «Salud de la bóveda», con «Ahora no». Ninguna se conecta a internet.» | Sección de Ajustes (R-2) | `health/Missions.kt:45-47`; `SettingsScreen.kt:674` | `MissionsTest.theWordsWordForWord`; `SettingsSearchTest` |
| «Aún no tienes bóveda. Créala en la pestaña Bóveda.» | «Salud» sin bóveda | `tools/ToolCatalog.kt:46` | `MissionsTest.theWordsWordForWord` |

Todos los textos pasan `ForbiddenWords`, y `SourceTextsTest` recorre todo `app/src/main/java`. Ninguno nombra otra app. Siguen M-3: una o dos líneas y nada plegado.

## Revisión experto-seguridad-cripto

- **Decisión (b), ningún resultado tardío.**
  - La huella y la maestra de una bóveda que ya no se enseña no escriben nada. Lo comprueban al empezar, después de Argon2id (la maestra) o justo antes de guardar (la huella), siempre con el mutex del almacén (`VaultStore.kt:673`, `:701`).
  - `ActiveGateTest` provoca el cambio en mitad del trabajo y compara el archivo byte a byte; los dos tests se vieron en rojo.
- **O-6, la red.** Ninguna misión lleva a la red: `missions` recibe `vaultHealth` sin el mapa de filtraciones, y `MissionAction` solo tiene destinos de navegación (`health/Missions.kt:22-35`). Solo los dos botones de siempre conectan con Pwned Passwords.
- **Datos de la bóveda en las misiones.**
  - El título con el nombre de una entrada vive solo en la rama de la bóveda abierta de «Salud». Se calcula fuera del hilo principal y se cancela al bloquear (`HealthScreen.kt:125`).
  - Nunca va en `rememberSaveable`, en un `Bundle`, en un aviso ni en `AppContainer`, que solo guarda ids por bóveda y por época (`Missions.kt:82`).
  - Se muestra como texto plano, sin marcas de dirección (`plainReportText`, `noMissionGoesOnline`).
- **O-7.**
  - «Antes de empezar» va antes que todo y la guía no lo puede saltar, porque se guarda aparte (`noticeAccepted` frente a `guideSeen`). Lo comprueba `OpeningTest.theNoticeBeforeAnything`.
  - La guía no tapa el bloqueo, y la huella del desbloqueo sigue siendo la de la bóveda que se enseña.
  - El modo sencillo sigue sin leerse fuera de Herramientas: `SimpleModeTest`, que ahora incluye las rutas de las misiones.
- **O-3.** La guía nunca retrasa la elección ni la copia del paso a v4 (`theGuideNeverDelaysTheUpgradeNorItsCopy`).
- **Entregas.** La guía espera a cualquier QR en camino y a un selector abierto (`AppNavHost.kt:271`). No hay entregas de otras apps, porque no hay `ACTION_SEND`.
- **C-1, claves de las listas.** La fila de la misión tiene la clave fija `"mission"`, y las filas de las entradas usan `lazyKey('e', id)` (`HealthScreen.kt:244`). Así, un id de entrada importado no puede repetir una clave y cerrar la app. Lo comprueba `PlatformSourcesTest` («C-1»), que falló con la primera versión y está en verde.
- **Nada nuevo** en formatos, permisos, dependencias, red ni registros. `settings.json` gana `missionsOff`, que solo se escribe cuando es `true`, para la fila 8.

## Revisión Ingeniero Móvil Senior

- **Estado y ciclo de vida.**
  - `GuideHolder` y `MissionPostponements` viven en `AppContainer`, así que sobreviven a un giro y a recrear la actividad.
  - La página de la guía es un `Int` en `rememberSaveable`, que no es un secreto.
  - El cálculo de las misiones usa `produceState` con las claves `data` y `missionsOff`: se vuelve a hacer cuando cambia la bóveda y se cancela al salir de la composición.
- **Concurrencia.**
  - `done()` guarda en el ámbito de la app con `NonCancellable`, así que salir de la pantalla no lo corta.
  - Las misiones se calculan en `Dispatchers.Default`.
  - `openingItem` es pura y barata: se recalcula en cada recomposición de `VaultNav`, sin trabajo de E/S. `QrHandoff.hasPending` es una lectura sincronizada.
- **Accesibilidad (R-1).**
  - El texto de cada página es una región viva educada (`LiveRegionMode.Polite`), así que TalkBack lo anuncia al pasar de página.
  - Los botones son de texto y los dos de la misión pesan igual.
  - La guía se desplaza, así que no corta texto al 200 %. Queda comprobarlo en el móvil, en la misma fila de la letra al 200 % de la fase A.
- **Navegación.**
  - Una misión navega a `vault/edit?id=…` (el id codificado, LIBRE-1) o a `settings/check-backup`.
  - «Salud» bloqueada usa `VaultGate`, la misma puerta que el resto de pantallas de la bóveda.
  - La guía solo sale en una pestaña (`onTab != null`), nunca encima de una pantalla de detalle.
- **Compilación.** `:app:compileDebugKotlin` sin errores; lint en la suite completa.

## Textos para el Abogado Digital (revisión final)

1. **Las dos guías:** los ocho textos de la tabla, con O-1 (ninguno presenta las bóvedas como forma de ocultar una), O-4 («solo se hacen al abrir Clavero») y O-7 (saltarla no salta «Antes de empezar»). La de novedades, además, para el dueño.
2. **Misiones (R-2, O-6):** los títulos («Cambia N contraseñas…», «Activa el 2FA en «título»», «Haz y comprueba una copia»), «Ir» y «Ahora no» del mismo peso, y el ajuste «Sugerencias de «Salud»» con su línea «Ninguna se conecta a internet.».
3. **«Salud» sin bóveda:** «Aún no tienes bóveda. Créala en la pestaña Bóveda.».
4. **Fila 4 de `pendientes.md`:** «nadie puede recuperar lo guardado» de la guía habla de olvidar la maestra, no de un borrado. Es la misma frase que «Antes de empezar» y no choca con O-2, que trata de los borrados.
5. **Para la política (fila 8):** `settings.json` gana `missionsOff`, sin cifrar.

## Dudas

1. **La firma de `missions`.** El plan decía `missions(report: HealthReport, data, now)`, pero `HealthReport` no existe. Uso `missions(items: List<HealthItem>, data, now)`, con `items` sacado de `vaultHealth`, que es lo que «Salud» ya calcula. Es un cambio de nombre y nada más.
2. **«Ahora no» va por clase de misión**, no por entrada: pospone, por ejemplo, todas las de «Activa el 2FA» hasta el siguiente desbloqueo. Así se enseña una cada vez y no se encadenan.
3. **Si no se puede guardar «vista»**, la guía no vuelve en esta ejecución, para no entrar en un bucle con el disco lleno, sino en el siguiente arranque. El Ruling dice «vuelve». Si se prefiere que vuelva enseguida, es una línea en `GuideHolder.done`.
4. **Volver atrás en la guía cuenta como «Saltar»**, y la guarda como vista.
5. **El orden de lo que se enseña al abrir** pone primero el paso a v4 y su copia, después los diálogos que responden a algo que hizo el usuario (la maestra, «Bóvedas» y el resultado de A6), después los dos avisos y la guía al final. Antes, el resultado de A6 y el diálogo de la maestra podían salir a la vez que otros: ahora salen de uno en uno.
6. **Ver una guía desde Ajustes también la guarda como vista.** Es inofensivo: quien la abre desde Ajustes ya ha pasado el arranque.
