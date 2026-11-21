# Tarea 10, fase A: informe del implementador

## Estado

**Hecha.** Las dos suites están en verde (las cifras, abajo). Commits sin subir, sin PR.

La fase A incluye:
- los campos `simpleMode` y `guideSeen`. `guideSeen` sustituye a `onboardingDone` por la decisión del dueño del 14-11-2026 y solo decide qué guía toca; las pantallas son de la fase B;
- el modo sencillo en Herramientas, con el interruptor en Ajustes;
- la última bóveda: estaba hecha en la tarea 2; aquí se añaden los tests que faltaban;
- el buscador de Ajustes;
- «Borrar todo el historial» (pendientes, fila 1);
- la escala de la letra.

## Commits

| Commit | Qué |
|---|---|
| `bd35798` | `simpleMode` y `onboardingDone`, decididos al primer arranque y con la primera bóveda; el `settings.json` real de la 1.4.3 |
| `f0dda99` | El modo sencillo en Herramientas, el buscador de Ajustes, «Borrar todo el historial» y los tests de la última bóveda |
| `2dbc3f3` | Lo guardado sin cifrar en los tests, la escala de la letra y `PlatformSourcesTest` con el catálogo de herramientas |
| `c4fae0a` | La guía vista como número de versión (`guideSeen`): completa, de novedades o ninguna (decisión del dueño) |
| `8c3fb31` | «2.0, tarea 10 (fase A): modo sencillo, última bóveda, buscador de Ajustes y borrar el historial» (este informe aparte: `.superpowers/sdd` no entra en git) |
| `7e868de` | Tras el repaso del asesor: los textos nuevos palabra por palabra, cada línea del índice pintada, la huella tardía sin tragarse excepciones y la última bóveda sin archivo |

## Tests

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` (con `ANDROID_HOME=/home/user/android-sdk`): **BUILD SUCCESSFUL**: 2321 tests, 0 fallos, 0 omitidos; `assembleDebug`, `lintDebug`, `checkLicense`, `checkFragmentVersion` y `checkAssets` en verde.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**: 2277 tests, 0 fallos, 0 omitidos.
- Las dos, sobre `7e868de`, el código final.
- jvm-check compila `app/src/main/java` salvo `ui/` y `android/`, y lee los tests de la app. Los archivos nuevos que no son de interfaz (`tools/ToolCatalog.kt`, `settings/SettingsSearch.kt`) y todos los tests nuevos corren en las dos suites. No hay copia que mantener a mano.

### Tests nuevos o cambiados

| Test | Qué fija |
|---|---|
| `settings/SettingsTest.oldSettingsKeepSimpleModeOff` | Usa el `settings.json` real de la 1.4.3, con y sin bóveda. El modo sencillo queda en `false` y toca `GuideDue.WHATS_NEW`, también al volver a leer el archivo. Una bóveda creada después no enciende el modo sencillo. Con la guía vista no toca ninguna |
| `settings/SettingsTest.newInstallTurnsSimpleModeOn` | Dos arranques sin bóveda: sigue sin decidir y toca `FULL`. Crear la primera bóveda lo enciende, una sola vez. Si la primera viene de una copia, queda apagado |
| `settings/SettingsTest.theGuideDueGoesByTheVersionSeen` | `FULL`, `WHATS_NEW` o `NONE` según la versión vista. Una versión más alta (la app bajada de versión) da `NONE`. `withGuideSeen` nunca baja el número. Un número negativo da `FULL` |
| `settings/SettingsTest.firstVaultNeverThrows` | Si no se puede guardar, vale para esta ejecución y la bóveda se crea igual |
| `SimpleModeFirstVaultTest` (4) | Los caminos reales de `AppContainer`: `VaultStore.create` lo enciende; `importReplace` sin bóveda, `importAsNewVault` sin bóvedas, una segunda bóveda y volver a crear tras `destroy()` no lo encienden. Con la 1.4.3 (su `settings.json` y su `vault.bin`): apagado, `WHATS_NEW` y `"guideSeen":1` en disco |
| `tools/SimpleModeTest` (4) | Ver la lista detallada debajo de esta tabla |
| `settings/SettingsSearchTest` (9) | `findsByTitleAndLine`, `toleratesTypo`, la consulta vacía (también un `\u200B`), la consulta enorme cortada y en menos de 2 s. `everySectionIsIndexed` falla si un `Section(` de Ajustes, «Aspecto» o «Acerca de» no toma su título del índice, o si una entrada no se pinta o no tiene ancla. `everyIndexedLineIsDrawn` falla si una línea del índice deja de pintarse. `theNewTextsWordForWord` fija los textos nuevos. `indexesFixedTextsOnly` y `textFollowsTheSystemScale` completan la clase |
| `vault/EntryHistoryTest.clearAllHistoryAfterALockClearsNothing` | Ni con la bóveda bloqueada ni con un bloqueo y una apertura entre la confirmación y el guardado se borra nada (`null`) |
| `vault/EntryHistoryTest.copiesMadeBeforeKeepTheHistory` | Exportar, borrar todo el historial y restaurar la copia en otra bóveda: el historial vuelve. Así se comprueba el texto «Las copias ya hechas… lo conservan» |
| `LastVaultTest` (5) | La app arranca en la última abierta. Si se borra, arranca en la primera de la lista, ahora y en el siguiente arranque. Un `lastOpened` que no está, o que no es un id, cae en la primera. Si la última sigue en la lista pero sin archivo, arranca en ella, vacía («Crea tu bóveda»), y la otra no se toca. La huella tardía de otra bóveda pasa todas las comprobaciones sin ninguna excepción y se para en la puerta: no abre nada. `vaults.json` lleva `lastOpened` y ningún nombre |
| `PlatformSourcesTest` (2 líneas) | La lista de herramientas se movió a `tools/ToolCatalog.kt` |

`tools/SimpleModeTest` (4 tests) comprueba que:
- se ven las cuatro herramientas del diseño y la lista es un subconjunto;
- toda ruta de herramienta, también las ocultas, sigue siendo un `composable("…")` de `AppNavHost.kt`, y la navegación no lee el ajuste;
- solo leen el ajuste `Settings.kt`, `SettingsStore.kt`, `SettingsSearch.kt`, `ToolCatalog.kt`, `ToolsScreen.kt` y el interruptor de `SettingsScreen.kt`. Por eso ningún aviso ni ajuste de protección puede depender de él (O-7);
- la tarjeta «Cómo protegemos tus datos» queda fuera del filtro;
- el texto dice que hay más y que se enseñan en Ajustes.

**TDD:**
- **Rojo de verdad:** el campo versionado de la guía. Los tests se escribieron antes, la compilación falló en rojo y después pasaron a verde.
- **Código antes que el test:** en las demás partes escribí el código antes o a la vez que el test, así que no las cuento como TDD.
- **Mutaciones, para compensar:** las probé en jvm-check sobre el código final y cada una hizo fallar sus tests:
  - `withFirstVault` siempre a `true`: fallan `importingTheFirstVaultLeavesItOff`, `importingAsANewVaultFirstLeavesItOff` y `newInstallTurnsSimpleModeOn`;
  - `clearAllHistory` sin `sinceLock`: falla `clearAllHistoryAfterALockClearsNothing`;
  - sin `!active()` en `VaultStore.opened` (`VaultStore.kt:497`): falla `aLateFingerprintOfAnotherVaultOpensNothing`.

**Límite del índice:** `everySectionIsIndexed` y `everyIndexedLineIsDrawn` ven las secciones y las líneas indexadas. Un control nuevo dentro de una sección que ya existe, con un texto que no está en el índice, no hace fallar nada; quien lo añada tiene que meterlo en `SettingItem.lines`.

**Procedencia del `settings.json` de la 1.4.3** (`app/src/test/resources/seguridad/settings-1.4.3.json`): lo escribió el `SettingsStore` de la etiqueta `v1.4.3` (`a3ac942`), en un *worktree* temporal ya borrado, con `./gradlew -p jvm-check test --tests 'app.seguridad.gen.GenSettings143Test'`. Para reproducirlo, este test en `app/src/test/java/app/seguridad/gen/` de ese *worktree*:

```kotlin
@Test fun write() = runTest {
    val out = File(System.getProperty("gen.out") ?: "settings-1.4.3.json")
    out.delete()
    val s = SettingsStore(out, Dispatchers.Unconfined)
    s.load()
    s.decideRenameNotice(vaultExists = false) // primer arranque de una instalación de la 1.3.0 o posterior
    s.update { it.copy(noticeAccepted = true) }
    s.update { it.copy(lockAfter = LockAfter.MIN_1, clipboard = ClipboardClear.S15, theme = ThemeMode.SYSTEM, kdf = KdfProfile.STANDARD) }
    s.update { it.withFolderChosen("content://com.android.externalstorage.documents/tree/primary%3ACopias-de-prueba") }
    s.update { it.copy(lastAutoBackupAt = 1762700000000L, autoBackupCopies = listOf("doc-prueba-1", "doc-prueba-2"), autoBackupCopiesFolder = it.autoBackupUri) }
    s.update { it.copy(clockHighWaterMs = 1762900000000L, clipboardHistoryNoticeAccepted = true) }
}
```

Los datos son de prueba; la carpeta y los ids de documento son inventados.

## Decisiones de diseño

### La guía: `guideSeen: Int` (decisión del dueño del 14-11-2026)

Distingue tres casos con un número, la versión de la guía vista:
- `GUIDE_NONE = 0`: instalación nueva, toca la guía completa (`GuideDue.FULL`);
- `GUIDE_BEFORE_2_0 = 1`: quien actualiza desde 1.x, toca la guía corta de novedades (`WHATS_NEW`);
- `GUIDE_CURRENT = 2`: ya la vio o la saltó (`NONE`).

`Settings.guideDue` hace el cálculo (`settings/Settings.kt:123`). Al verla o saltarla, `withGuideSeen()` pone `GUIDE_CURRENT` y nunca baja el número (`:131`).

Por qué un número y no un sí o no:
1. **`appJson` no escribe los valores por defecto.** Un booleano `false` no distingue un archivo de la 1.4.3 de una instalación nueva que aún no ha visto la guía. El valor de quien actualiza (1) sí se escribe.
2. **Sirve para versiones futuras.** Una versión con guía propia solo sube `GUIDE_CURRENT` a 3:
   - quien vio la 2 recibe las novedades;
   - una instalación nueva, la completa;
   - quien vio la 3, ninguna.

   Las versiones sin guía no cambian nada. No es el número de la app, porque no toda versión trae guía.
3. **Bajar de versión es inofensivo.** Un número más alto que el actual (la app bajada de versión) da `NONE`, nunca un error.

**Cuándo se decide:** una sola vez, en el primer arranque de la 2.0 (`Settings.decidedAtFirstStart`, `settings/Settings.kt:143`, llamada desde `SettingsStore.decideRenameNotice`, `SettingsStore.kt:65`). La marca es `appearanceNoticePending == null`, la única que solo es nula hasta ese arranque; `renameNoticePending` ya viene en `false` en un archivo de la 1.4.3. Hay data anterior si existe el `settings.json` o la bóveda. En los arranques siguientes no se vuelve a evaluar: para entonces también una instalación nueva tiene ya `settings.json`.

### El modo sencillo: `simpleMode: Boolean?`

- **Nulo:** sin decidir; se lee como apagado (`simpleOn`).
- **Al actualizar:** el primer arranque lo pone en `false`.
- **Primera bóveda de una instalación nueva:** se decide en `AppContainer.firstVaultMade` (`AppContainer.kt:460`). La llaman los ganchos `beforeCreate` y `beforeReplace` de cada bóveda (`:406`, `:413`), que son los únicos que ven todos los caminos: la pantalla «Crea tu bóveda» llama a `store.create` directamente, sin pasar por `createVault`.
  - Solo actúa mientras el ajuste está sin decidir y ninguna bóveda del móvil tiene `vault.bin`.
  - Creada: encendido. Importada de una copia: apagado. El diseño solo dice «activado al crear la primera bóveda» y no habla de importar.
  - Nunca lanza: si no se puede guardar, vale solo para esta ejecución (`SettingsStore.firstVault`, `SettingsStore.kt:79`).

### Herramientas

- La lista se movió a `tools/ToolCatalog.kt`, fuera de `ui/`, para que se pruebe en las dos suites.
- `visibleTools(simple)` es el único filtro.
- «Salud de la bóveda» (`vault/health`) entra en la lista en los dos modos; el modo sencillo es un subconjunto del completo. Con la bóveda bloqueada, su pantalla dice «Desbloquea la bóveda para ver su salud.» y no enseña nada (`HealthScreen.kt`).

### El buscador de Ajustes

- **Índice:** `settings/SettingsSearch.kt` (`enum SettingItem`), con títulos y líneas que son constantes. Las pantallas las leen de ahí; las de «Aspecto» que solo eran títulos o etiquetas se movieron a ese archivo.
- **Consulta:** se corta a 60 puntos de código (`SettingsSearch.cut`, `:90`) antes de `FuzzySearch.prepare`. Una consulta vacía no devuelve nada (`:99`), porque `score` da 0 a la consulta vacía.
- **Resultados de Ajustes:** cada sección se registra con `anchor(…)` (`SettingsScreen.kt:188`) y un resultado desplaza la pantalla hasta ella (`bringIntoView`). Solo se ofrecen las secciones pintadas en ese momento, así que «Dónde está la clave de la huella» solo sale con la huella encendida.
- **Resultados de «Aspecto» y «Acerca de»:** abren esas pantallas.
- **La consulta en sí:** vive en `remember` (memoria, sin guardar; `:205`).

### «Borrar todo el historial»

- **Dónde:** una sección en Ajustes (`SettingsScreen.kt:715`), solo con la bóveda abierta.
- **Confirmación:** el diálogo guarda `lockEpoch` al abrirse (`:725`) y el guardado usa `clearAllHistory(sinceLock = since)` (`:744`). Un bloqueo cierra el diálogo (`:722`).
- **Errores:** pasan por `storeSaveErrorText` (`:748`): solo textos fijos.
- **Resultado de `clearAllHistory`:** ahora devuelve `Int?` (`VaultStore.kt:1236`):
  - `null`: no se guardó, porque la bóveda estaba bloqueada o hubo un bloqueo;
  - `0`: no había nada que borrar;
  - `n`: entradas borradas.
- **¿Las copias guardan el historial? Sí.** `writeBackup` mete el archivo entero de la bóveda (`Backup.kt:157`), y `copiesMadeBeforeKeepTheHistory` lo demuestra. El texto reutiliza `COPIES_KEEP_IT` (O-2) y nombra la papelera y las contraseñas anteriores, porque `withoutHistory` quita las dos cosas.

### La última bóveda

La tarea 2 ya la hizo:
- `lastOpened` vive en `vaults.json` (`VaultRegistry.kt:76`);
- si falta, la app cae en la primera de la lista (`:76`), también al borrar (`:132`);
- `switchTo` la apunta;
- la pantalla de desbloqueo enseña la bóveda que se abre («Bóveda N» y su color) con `LocalVaultSwitcher`.

No la rehíce, y añadí los tests que faltaban (`LastVaultTest`).

La huella siempre es la de la bóveda que se enseña:
- `BiometricVault.unlock(activity, store.id, …)` (`VaultScreen.kt:431`);
- todo se rehace con `key(vault)` (`AppNavHost.kt:186`);
- una clave que llega tarde para otra bóveda acaba en `opened()` con `!active()`: se borra y la bóveda queda bloqueada (`VaultStore.kt:497`). No es `StaleBiometricKey`, así que no apaga la huella de nadie.

## Tabla de textos visibles nuevos

| Texto | Cuándo se ve | `archivo:línea` | Test que lo fija |
|---|---|---|---|
| «Salud de la bóveda» / «Contraseñas repetidas, débiles o antiguas, y accesos sin 2FA» | Tarjeta de Herramientas, en los dos modos | `tools/ToolCatalog.kt:42-43` | `SettingsSearchTest.theNewTextsWordForWord` (literal); `SimpleModeTest.simpleShowsTheFourOfTheDesign` (la ruta) |
| «Modo sencillo» | Título de la sección de Ajustes y etiqueta del interruptor | `tools/ToolCatalog.kt:46`; `SettingsScreen.kt:663` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Herramientas enseña solo cifrar texto, cifrar archivo, el generador y la salud de la bóveda. No oculta avisos ni ajustes.» | Bajo el interruptor, siempre | `tools/ToolCatalog.kt:49` | `SettingsSearchTest.theNewTextsWordForWord` (literal); `SimpleModeTest.theListSaysWhereTheOthersAre` («No oculta avisos ni ajustes», O-7) |
| «Hay más herramientas. Para verlas, apaga «Modo sencillo» en Ajustes.» | Al final de Herramientas, con el modo encendido | `tools/ToolCatalog.kt:52`; `ToolsScreen.kt:66` | `SettingsSearchTest.theNewTextsWordForWord` (literal); `SimpleModeTest.theListSaysWhereTheOthersAre` (que se pinta) |
| «Buscar en Ajustes» | Etiqueta del campo, arriba de Ajustes | `settings/SettingsSearch.kt:82` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Ningún ajuste coincide.» | Búsqueda sin resultados | `settings/SettingsSearch.kt:83` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Aspecto → {título}» / «Acerca de, licencias y avisos → {título}» | Resultado de una pantalla de dentro (el título sale del índice) | `SettingsScreen.kt:231-232` | `SettingsSearchTest.indexesFixedTextsOnly` (los títulos) |
| «Historial de las entradas» | Título de la sección de Ajustes | `vault/TrashTexts.kt:95` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Cada entrada guarda sus versiones anteriores. Aquí puedes borrarlas de todas a la vez.» | Bajo el título | `vault/TrashTexts.kt:96` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Borrar todo el historial» | Botón, activo con la bóveda abierta | `vault/TrashTexts.kt:97` | `SettingsSearchTest.theNewTextsWordForWord` |
| «¿Borrar el historial de todas las entradas?» | Título de la confirmación | `vault/TrashTexts.kt:98` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Se quitan las versiones y las contraseñas anteriores de todas las entradas, también de las de la papelera. Las copias ya hechas (automáticas o exportadas) lo conservan.» | Texto de la confirmación (O-2) | `vault/TrashTexts.kt:101` | `SettingsSearchTest.theNewTextsWordForWord` (literal); `EntryHistoryTest.copiesMadeBeforeKeepTheHistory` (su verdad: la copia restaurada trae el historial) |
| «La bóveda se bloqueó: no se ha borrado nada.» | Snackbar, con un bloqueo entre la confirmación y el guardado | `vault/TrashTexts.kt:104` | `SettingsSearchTest.theNewTextsWordForWord`; la condición, `EntryHistoryTest.clearAllHistoryAfterALockClearsNothing` |
| «Ninguna entrada tenía historial.» / «Historial borrado de 1 entrada.» / «Historial borrado de N entradas.» | Snackbar con el resultado | `vault/TrashTexts.kt:109-111` | `SettingsSearchTest.theNewTextsWordForWord` |

Reutilizados sin cambios:
- «Borrar historial», el botón del diálogo, que es `HISTORY_CLEAR`;
- «Cancelar»;
- `SETTINGS_NEED_OPEN_VAULT`, en la sección del historial con la bóveda bloqueada;
- `storeSaveErrorText`, los errores.

Movidos sin cambiar el texto a `settings/SettingsSearch.kt`, desde literales de `SettingsScreen.kt` y constantes de `AppearanceScreen.kt`:
- los títulos de las secciones;
- «Exportar bóveda», «Importar copia» y «Cambiar contraseña maestra»;
- «Acerca de, licencias y avisos»;
- «Ver la política de privacidad» y «Ver licencias»;
- de «Aspecto»: «Modo», «Estilo», «Paleta», «Transparencias y contraste», «Reducir transparencias», «Alto contraste» y sus líneas.

Todos pasan `ForbiddenWords` (`SimpleModeTest`, `SettingsSearchTest`, `EntryHistoryTest` y `SourceTextsTest`, que recorre todo `app/src/main/java`) y ninguno nombra otra app. Siguen M-3: una línea a la vista y nada bajo «Más información».

## Lo que se guarda sin cifrar (para `pendientes.md`, fila 8)

**En `settings.json`, desde esta fase:**
- `simpleMode`: `true` o `false`. Solo aparece una vez decidido; mientras es nulo no se escribe (`settings/Settings.kt:89`).
- `guideSeen`: la versión de la guía vista, un número pequeño. Se escribe solo cuando no es 0: con 1 al actualizar desde 1.x y con 2 al ver o saltar la guía (`settings/Settings.kt:98`). Dice si el móvil tenía Clavero 1.x antes de la 2.0.

Lo comprueban los tests:
- `SimpleModeFirstVaultTest.creatingTheFirstVaultTurnsItOn`: `"simpleMode":true` y nada de `guideSeen`;
- `anUpdateKeepsItOff`: `"guideSeen":1`.

**En `vaults.json`, sin cambios en esta fase:** `lastOpened`, el id local de la última bóveda abierta («principal» o 32 cifras hexadecimales). Junto a los ids, los colores y el orden, viene de la tarea 2, sin nombres. Lo comprueba `LastVaultTest.theAppStartsOnTheLastOneOpened`.

**No se guarda:**
- la consulta del buscador (solo memoria);
- la época de la confirmación del historial.

El borrado del historial cambia solo la bóveda cifrada.

## Flujos de Maestro afectados (no se han tocado)

En una instalación nueva, crear la bóveda enciende el modo sencillo (`comun/crear-boveda.yaml` y `comun/inicio-desde-cero.yaml`). Desde entonces, `comun/abrir-herramienta.yaml` ya no encuentra las herramientas ocultas. Fallan los flujos que abren una:

| Flujo | Herramientas ocultas que abre |
|---|---|
| `p15-laboratorio-hashes.yaml` | Laboratorio de hashes |
| `p16-laboratorio-clasico.yaml` | Laboratorio clásico |
| `p17-repartir-un-secreto.yaml` | Repartir un secreto |
| `p19-recorrer-herramientas.yaml` | ¿Filtrada?, Laboratorio de hashes, Laboratorio clásico, Repartir un secreto, Limpiar foto, Tachar en imagen, Inspector de QR, PIN y códigos |
| `giro-conserva-estado.yaml` | Laboratorio de hashes, Laboratorio clásico, Repartir un secreto |
| `borra-datos/p15-p16-p17-bloquear-borra.yaml` | Laboratorio de hashes, Laboratorio clásico, Repartir un secreto |
| `semiautomaticas/p15-laboratorio-hashes-archivo.yaml` | Laboratorio de hashes |
| `semiautomaticas/p17-recomponer-vector-oficial.yaml` | Repartir un secreto |
| `comun/hashes-abc-con-huella.yaml` (subflujo) | Laboratorio de hashes |

La lista sale de buscar en todos los flujos los títulos de las herramientas ocultas, no solo `HERRAMIENTA:`. `borra-datos/p05-ayuda-codigos-qr.yaml` y el resto de `semiautomaticas/` no abren Herramientas.

Otros cambios para los flujos:
- **`p19`:** encuentra además la tarjeta nueva «Salud de la bóveda» entre «Generador» y «¿Filtrada?». No la busca, así que no falla por ella.
- **`p20-version-y-licencias.yaml`:** no cambia; «Acerca de, licencias y avisos» sigue igual.
- **Arreglo (tarea 11):** apagar «Modo sencillo» en Ajustes después de crear la bóveda, o usar el buscador de Ajustes. Es la fila 27 de `pendientes.md`.

## Revisión experto-seguridad-cripto

- **Ninguna primitiva, formato ni red nueva.** No cambia ningún formato de Clavero. `settings.json` gana dos campos que se leen con valores por defecto, y un archivo de la 1.4.3 se lee igual (`settings/SettingsTest.kt`, con el archivo real).
- **O-7: el modo sencillo no puede ocultar protección.**
  - Solo filtra la lista de Herramientas (`tools/ToolCatalog.kt:40`, `ToolsScreen.kt:52-53`).
  - Ningún otro archivo lo lee. `SimpleModeTest.onlyTheToolsListReadsIt` recorre todo `app/src/main/java` y falla si un aviso, la navegación o un ajuste de protección empiezan a leerlo.
  - Las rutas ocultas siguen registradas (`everyToolRouteStaysReachable`).
  - El interruptor se puede cambiar con la bóveda bloqueada. Es un ajuste de presentación, no de protección (COV2-1 no aplica): quien tenga el móvil un momento solo consigue ver más o menos tarjetas, y cada herramienta tiene su propio guardián.
- **Decisión (b), carrera entre comprobar y usar.** El historial se borra con la época leída al abrir la confirmación (`SettingsScreen.kt:725`, `:744`; `VaultStore.kt:1236`). Un bloqueo entre medias no borra nada, aunque la bóveda se vuelva a abrir (`EntryHistoryTest.clearAllHistoryAfterALockClearsNothing`).
- **O-2, veracidad.** «Las copias ya hechas lo conservan» se comprobó contra el código (`Backup.kt:157`) y con un test que restaura el historial. El texto no promete un borrado que no se puede deshacer.
- **Buscador.** La consulta se corta antes de comparar (`SettingsSearch.kt:90`): una pegada de 2 MB cuesta como una de 60 puntos de código (`aHugeQueryIsCut`). El índice son constantes; ningún dato de la bóveda, carpeta o copia (`indexesFixedTextsOnly`). La consulta solo vive en memoria, nunca en estado guardado (`SettingsScreen.kt:205`).
- **Huella y última bóveda.**
  - La huella siempre es de la bóveda enseñada (`VaultScreen.kt:431`, `AppNavHost.kt:186`).
  - Una clave tardía de otra bóveda se borra sin abrir nada ni apagar su huella (`VaultStore.kt:497`; `LastVaultTest.aLateFingerprintOfAnotherVaultOpensNothing`).
  - Un `lastOpened` manipulado, sea un id que no está o `../vault`, cae en la primera de la lista y nunca llega a una ruta (`VaultRegistry.kt:76`; `aRegistryNamingAMissingVaultFallsBackToTheFirst`).
- **Metadatos sin cifrar.** `guideSeen = 1` revela que el móvil tenía Clavero 1.x. Es un dato del mismo tipo que el que ya da la existencia de `settings.json` o de `files/vault/`, y va a la fila 8 para la política.
- **Registros.** Ningún registro nuevo ni `Log.`.
- **A7, el teclado no aprende.** El campo del buscador está dentro de `NoLearningInput`, que envuelve toda la app (`MainActivity.kt:45`), así que pide `IME_FLAG_NO_PERSONALIZED_LEARNING` como los demás campos.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida y estado.**
  - La consulta del buscador y la época de la confirmación usan `remember`, nunca `rememberSaveable` (`SettingsScreen.kt:205`, `:718`). Con `configChanges` del giro, la consulta sobrevive al giro; un cambio de tema la borra, y se acepta: no es un secreto ni un trabajo.
  - El diálogo se cierra al bloquear (`:722`).
  - Las anclas se registran y se quitan con `DisposableEffect` (`:188-199`), así que un resultado solo se ofrece si su sección está compuesta.
- **Concurrencia.**
  - `firstVaultMade` lee el disco en `Dispatchers.IO` y relanza `CancellationException` (`AppContainer.kt:460`).
  - `clearAllHistory` corre bajo el mutex de la bóveda, como todo guardado.
  - El botón se desactiva mientras trabaja (`busy`).
- **Rendimiento.** El buscador recorre 17 entradas con la consulta acotada. `remember(query)` evita repetir la búsqueda al recomponer, y se calcula en el hilo principal, a microsegundos.
- **Accesibilidad (R-1).**
  - El interruptor es una fila entera con `toggleable` y `Role.Switch` (`SettingSwitch`).
  - Los resultados son botones de texto que se pueden enfocar.
  - La línea de «Hay más herramientas» es texto normal, que TalkBack lee.
  - Los iconos de las tarjetas siguen siendo decorativos.
- **Letra al 200 %.**
  - Nada fija la escala ni la densidad, y las pantallas tocadas no usan `maxLines` ni `TextOverflow`.
  - Ajustes se desplaza y las tarjetas de Herramientas tienen altura mínima, no fija (`SettingsSearchTest.textFollowsTheSystemScale`).
  - **Comprobación en el móvil (para `pendientes.md`):** con la letra del sistema al 200 %, mirar en Ajustes el buscador, sus resultados, el interruptor del modo sencillo y la sección del historial con su diálogo, y en Herramientas, con el modo sencillo, las cuatro tarjetas y la línea final. Ningún texto debe cortarse.
- **Arquitectura.** El catálogo y el índice salieron de `ui/` para probarse en jvm-check. `Section` gana un `modifier` opcional sin cambiar a quien lo llama. Lint está en verde.

## Textos para el Abogado Digital (revisión final)

1. Todos los de la tabla de textos visibles nuevos, arriba.
2. **El modo sencillo** (O-7): «Herramientas enseña solo … No oculta avisos ni ajustes.» y «Hay más herramientas. Para verlas, apaga «Modo sencillo» en Ajustes.».
3. **«Borrar todo el historial»** (O-2): su confirmación dice que las copias ya hechas lo conservan (`COPIES_KEEP_IT`) y que también se quita lo de la papelera.
4. **Para la política (fila 8):**
   - `settings.json` guarda sin cifrar si el modo sencillo está encendido y la versión de la guía vista; esta revela que hubo Clavero 1.x en el móvil;
   - `vaults.json` guarda cuál fue la última bóveda abierta, por su id local, sin nombre.

## Dudas

1. **Nulo es apagado.** El brief dice que `simpleMode` nulo se lee como `false`. Por eso una instalación nueva ve todas las herramientas hasta crear su primera bóveda. Lo dejé así, porque es lo más prudente: no oculta nada.
2. **«Salud de la bóveda» como tarjeta.** El diseño pone «Salud» en el modo sencillo, pero antes solo se llegaba desde la bóveda. La añadí a Herramientas en los dos modos, para que el modo sencillo sea un subconjunto del completo. El dueño puede preferir que solo salga en el modo sencillo.
3. **Un `settings.json` de la 1.4.3 sin bóveda** (instaló, aceptó el aviso y no creó bóveda) cuenta como actualización: modo sencillo apagado y la guía de novedades. Si después crea su primera bóveda, el modo sencillo sigue apagado.
4. **Versiones de desarrollo de la 2.0.** Una versión de prueba de las tareas 5 bis a 9 ya decidió `appearanceNoticePending`, así que no se vuelve a decidir: queda `guideSeen = 0` (la guía completa) y el modo sencillo sin decidir. Solo afecta a móviles de prueba, nunca a la 1.4.3 publicada.
5. **El interruptor con la bóveda bloqueada.** Se puede cambiar sin abrir la bóveda: no es un ajuste de protección. Si el dueño prefiere tratarlo como los demás (COV2-1), basta con `enabled = editable`.
6. **«Importar como bóveda nueva» como primera bóveda** deja el modo sencillo apagado, igual que «Importar copia». El diseño no dice nada.
7. **«Salud» sin bóveda.** En una instalación nueva, antes de la primera bóveda, la tarjeta abre una pantalla que dice «Desbloquea la bóveda para ver su salud.». No hay nada que desbloquear y no enseña nada de ninguna bóveda. Es un texto de error para la fase B: con `NoVault`, algo como «Aún no tienes bóveda».
8. **La última bóveda sin su archivo.** Si sigue en la lista pero su `vault.bin` desapareció fuera de la app, la app arranca en ella, vacía («Crea tu bóveda», con el selector de bóvedas), y no salta a otra. Así lo hizo la tarea 2, y lo fija `LastVaultTest.aListedLastVaultWithoutItsFileIsShownEmpty`.
9. **`MissionAction`.** Los destinos de sus misiones no existen aún. `SimpleModeTest.everyToolRouteStaysReachable` se ampliará en la fase B con cada destino.

---

# Ronda 1 de arreglos

**Estado: hecha.** Base `6336099`. Las dos suites están en verde (las cifras, abajo). Commits sin subir y sin PR.

## Commits

| Commit | Qué |
|---|---|
| `992b39a` | Los textos de m-1, m-5, m-6 y m-7, el test de m-3 y una bóveda de la 1.x sin `settings.json` |
| `f418a52` | i-6, en su propio commit: una huella o una maestra que llegan tarde para otra bóveda no escriben nada |
| `b67d35b` | «2.0, tarea 10 (fase A): ronda 1 de arreglos» (cierre; este informe no entra en git) |

## Qué se hizo

- **m-1:** `SIMPLE_MODE_SHORT` dice ahora «Encendido, Herramientas enseña solo…» (`tools/ToolCatalog.kt:49`). Es verdad con el interruptor encendido y con él apagado.
- **m-5:** la línea de la sección del historial depende del estado (`historyAllHint`, `vault/TrashTexts.kt:113`; se pinta en `SettingsScreen.kt:729`):
  - sin bóveda: «Aún no tienes bóveda.»;
  - con la bóveda bloqueada: «Abre la bóveda (pestaña Bóveda) para borrar el historial.»;
  - abierta, o mientras se lee: ninguna.

  La sección se sigue pintando sin bóveda, así que el buscador tiene adónde llevar.
- **m-6:** `HISTORY_ALL_SHORT` dice «Al cambiar una entrada se guarda su versión anterior. Aquí puedes borrar las de todas a la vez.» (`vault/TrashTexts.kt:97`). Las identidades y los contactos no guardan historial.
- **m-7:** Ajustes no ofrece «Conservar 1»: las opciones son 3, 5 y 10 (`AUTO_BACKUP_KEEP_CHOICES`), y `SettingsStore.sane` cambia cualquier otro número por 5. Lo comprobé con el mínimo, 3 copias, haciendo una copia en cada cambio (`AutoBackupTest.clearingTheHistoryMayRotateAnOlderCopyOut`, `:275`):
  - antes de borrar, las tres copias tienen el historial;
  - el borrado es un guardado, así que hace una copia nueva sin historial;
  - la rotación quita la más antigua, que lo tenía;
  - las otras dos lo conservan: restauradas, la nota tiene sus versiones;
  - la copia nueva no lo tiene.

  Por eso «lo conservan» no siempre es verdad, y el texto pasa a decir «pueden»: «Las copias ya hechas (automáticas o exportadas) pueden conservarlas; la copia automática quita las más antiguas.» (`vault/TrashTexts.kt:106`). Lo fijan `theNewTextsWordForWord` y `copiesMadeBeforeKeepTheHistory`, que comprueba además que ya no lleva `COPIES_KEEP_IT`.
- **m-3:** `SimpleModeFirstVaultTest.aVaultWithAFileMeansItIsNotTheFirst` cubre el caso de un modo sin decidir con una bóveda que ya tiene archivo. Ni crear una segunda bóveda ni «Importar como bóveda nueva» lo deciden. Lo vi fallar con la mutación `if (none || true)` en `AppContainer.kt:463`: falla justo este test, y vuelve a verde al deshacerla.
- **i-6, en su propio commit:**
  - `unlockWithKeyLocked` mira `active()` antes de cualquier efecto (`VaultStore.kt:669`). No purga la papelera, no hace el guardado de contabilidad y no toca la marca del reloj; borra la clave y deja la bóveda bloqueada.
  - **El desbloqueo con la maestra tenía el mismo efecto tardío.** `switchTo` cambia de bóveda activa y espera al mutex, así que una maestra escrita en la pantalla de A acababa después del cambio. Entonces escribía el archivo de A: la última comprobación de la maestra, la purga y quizá una clave vuelta a envolver. Lo arreglé con dos comprobaciones dentro del mutex:
    - antes de Argon2id (`:573`), para no derivar una clave para una bóveda que ya no se enseña;
    - después de Argon2id y antes del primer efecto (`:584`); la marca del reloj, `onClockReset`, queda detrás de esa comprobación.
  - **Tests:** `LastVaultTest.aLateFingerprintOfAnotherVaultOpensNothing` y `aLateMasterUnlockOfAnotherVaultWritesNothing`. En los dos, el `vault.bin` de A y el `settings.json` quedan iguales byte a byte, y después A se abre con su huella o con su maestra como siempre. Los dos fallaron antes del arreglo: el archivo de A pasaba de 389 a 438 bytes.
  - Ha bastado con una comprobación, así que no hay riesgo con nombre. El resto que queda se apunta en «Dudas».
- **Cómo detecta la actualización:**
  - En `a3ac942`, la 1.4.3 escribe siempre `settings.json` en el primer arranque. `SeguridadApp` llama a `decideRenameNotice`, que escribe `renameNoticePending` (en una instalación nueva, `false`) aunque no se haya cambiado ningún ajuste. Solo una escritura que falla, por ejemplo con el disco lleno, deja una bóveda sin `settings.json`.
  - La 2.0 ya cubría ese caso: cuenta como «datos de antes» el archivo de ajustes o cualquier cosa en `files/vault/` (`SettingsStore.kt:61`, `AppContainer.kt:199`).
  - El test nuevo es `SimpleModeFirstVaultTest.aVaultOf1xWithoutSettingsIsAnUpdate`, con el `vault-1.4.3.json` real y sin `settings.json`. Comprueba que `guideSeen` = 1, que toca la guía de novedades y que el modo sencillo queda apagado, también en el arranque siguiente. Pasó a la primera; no hizo falta tocar el código.
- **Sin cambios de código:**
  - m-4 (las tarjetas al 200 %) y m-9 (TalkBack lee «Modo sencillo» dos veces) van a las filas del móvil, con el arreglo que propone el revisor;
  - m-2 es de la fase B.

## Tests

- `:app`: **BUILD SUCCESSFUL**: 2325 tests, 0 fallos, 0 omitidos; assemble, lint, licencias, Fragment y assets en verde.
- jvm-check: **BUILD SUCCESSFUL**: 2281 tests, 0 fallos, 0 omitidos (las dos sobre `f418a52`, por separado).

## Textos visibles nuevos o cambiados en esta ronda

| Texto | Cuándo se ve | `archivo:línea` | Test que lo fija |
|---|---|---|---|
| «Encendido, Herramientas enseña solo cifrar texto, cifrar archivo, el generador y la salud de la bóveda. No oculta avisos ni ajustes.» | Bajo el interruptor del modo sencillo, siempre | `tools/ToolCatalog.kt:49` | `SettingsSearchTest.theNewTextsWordForWord`; `SimpleModeTest.theListSaysWhereTheOthersAre` |
| «Al cambiar una entrada se guarda su versión anterior. Aquí puedes borrar las de todas a la vez.» | Sección «Historial de las entradas» | `vault/TrashTexts.kt:97` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Aún no tienes bóveda.» | Esa sección, sin bóveda | `vault/TrashTexts.kt:115` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Abre la bóveda (pestaña Bóveda) para borrar el historial.» | Esa sección, con la bóveda bloqueada | `vault/TrashTexts.kt:116` | `SettingsSearchTest.theNewTextsWordForWord` |
| «Se quitan las versiones y las contraseñas anteriores de todas las entradas, también de las de la papelera. Las copias ya hechas (automáticas o exportadas) pueden conservarlas; la copia automática quita las más antiguas.» | Confirmación de «Borrar todo el historial» (O-2) | `vault/TrashTexts.kt:106` | `SettingsSearchTest.theNewTextsWordForWord` (literal); su verdad, en `AutoBackupTest.clearingTheHistoryMayRotateAnOlderCopyOut` y `EntryHistoryTest.copiesMadeBeforeKeepTheHistory` |

Los demás textos de la fase A siguen como en la tabla de arriba. `SETTINGS_NEED_OPEN_VAULT` ya no se usa en la sección del historial.

## Revisión experto-seguridad-cripto (ronda 1)

- **Decisión (b), ningún resultado tardío.** La huella y la maestra de una bóveda que ya no se enseña no dejan nada escrito. Lo comprueban dentro del mutex, antes del primer efecto (`VaultStore.kt:573`, `:584`, `:669`). Los tests miran byte a byte el archivo de A y los ajustes, y se vieron fallar antes del arreglo. La puerta de siempre, `opened()` con `!active()` (`:497`), sigue debajo como segunda defensa.
- **La huella no se apaga.** La comprobación nueva devuelve sin lanzar nada, así que `launchUnlockWithKey` no llama a `onWrongKey` (`VaultStore.kt:126-132`). El test llama a `unlockWithKey` sin `try`.
- **Veracidad.**
  - m-1: el texto es verdad con el modo encendido y apagado.
  - m-6: deja fuera a las identidades y los contactos, que no guardan historial.
  - m-7: «pueden conservarlas» es verdad en todos los casos, comprobado con la rotación real.
  - m-5: ya no se pide abrir una bóveda que no existe.
- **Nada nuevo** en formatos, red, permisos, dependencias ni registros.

## Revisión Ingeniero Móvil Senior (ronda 1)

- **Concurrencia.** Las tres comprobaciones corren dentro del mutex del almacén. La de antes de Argon2id ahorra una derivación de memoria alta para una bóveda que ya no se enseña. Después de la segunda comprobación, el código hace lo mismo que antes. `KdfLock` sigue siendo uno para todo el proceso.
- **Estado de la interfaz.** `historyAllHint` es una función pura del estado (`vault/TrashTexts.kt:113`). La pantalla solo la pinta, y `Loading` no pinta nada, así que no parpadea un aviso falso mientras se lee la bóveda.
- **Tests.** Los tests de contenedor esperan a `advanceUntilIdle` antes de comparar bytes, para no confundir con escrituras pendientes de otros trabajos.

## Textos para el Abogado Digital (revisión final): cambios de la ronda 1

1. **m-1:** «Encendido, Herramientas enseña solo cifrar texto, cifrar archivo, el generador y la salud de la bóveda. No oculta avisos ni ajustes.».
2. **m-5:** «Aún no tienes bóveda.» y «Abre la bóveda (pestaña Bóveda) para borrar el historial.».
3. **m-6:** «Al cambiar una entrada se guarda su versión anterior. Aquí puedes borrar las de todas a la vez.».
4. **m-7 (O-2):** «… Las copias ya hechas (automáticas o exportadas) pueden conservarlas; la copia automática quita las más antiguas.». Sustituye, solo en este botón, a `COPIES_KEEP_IT` («… lo conservan.»). Ver la duda R1-1.

## Dudas (ronda 1)

**R1-1. `COPIES_KEEP_IT` de la tarea 3**

«Las copias ya hechas (automáticas o exportadas) lo conservan.» lo usan:
- «Borrar para siempre», «Vaciar papelera» y «Borrar historial» de una entrada;
- `TrashTest` lo comprueba en todos ellos.

Cada uno de esos borrados es también un guardado, con la misma rotación. El texto tiene, por tanto, la misma imprecisión que m-7.
- No lo he cambiado: es texto de la tarea 3, aprobado bajo O-2 y fuera de esta ronda.
- Propongo la misma solución («pueden conservarlo; la copia automática quita las más antiguas») para la revisión final del Abogado, junto con la fila 19 de `pendientes.md`.

**R1-2. Lo que queda de i-6 sin cubrir**

Tras la segunda comprobación, el desbloqueo hace enseguida `commit`, sin derivar ni esperar nada. Si `switchTo` cambia de bóveda justo en ese instante, se escribe el archivo de A, pero no se abre (`opened()` lo rechaza). La ventana es de microsegundos.

Cerrarla del todo pediría que `switchTo` cambiara de bóveda activa dentro del mutex del almacén. Eso es más que una comprobación y toca el código de la tarea 2. Lo dejo como riesgo con nombre para la revisión de la rama, parte «formato y bóvedas». Aun en ese caso, es el archivo de A con la clave de A y nunca cae en otra bóveda.

**R1-3. La tabla de dudas del revisor**

| # | Decisión |
|---|---|
| 1 a 3 | Se quedan como están, según su recomendación |
| 4 | No es pregunta del dueño (Ruling T10 fase A): solo afecta a móviles con una versión de prueba de la 2.0, y ahora su rama tiene test (m-3) |
| 5, 6 y 8 | Se aceptan |
| 7 y 9 | Pasan a la fase B, con m-2 |
