# Clavero 2.0, ronda 2 de los arreglos del ataque final (RT-25, RT-26 e informativos 1 a 3): informe del implementador

Encargo: `docs/superpowers/handoff/sdd-2.0/advisor-t12-redteam-fixes-2.md`, «Encargo de la ronda». Base: `79e21ba` (el encargo, guardado en `07e21a2`). Implementador nuevo (el de la ronda 1 no se pudo retomar). Sin *push*.

## Estado
**Lo primero:** el punto 4 se paró en 1,28x y el director lo redefinió (opción (i)): ya está hecho, con el test del criterio en verde. El 1,28x se publica tal cual. Sin *push*.

| Punto | Estado | Commit |
|---|---|---|
| 1. RT-26 | Hecho | bd0c31a |
| 2. Informativo 3 | Hecho | 0ec7c14 |
| 3. RT-25, parte a | Hecho | b632779 |
| 4. RT-25, parte b | Hecho: el tope (`655917d`) y el test del criterio redefinido por el director | 655917d y (commit del criterio) |
| 5. Informativo 1 | Hecho | 78d9708 |
| 6. Informativo 2 | Hecho | 7706c47 |
| 7. Cierre | Hecho | 937b4f2, 22379bd y (commit del criterio) |

## 1. RT-26
- `vault/VaultLimits.kt`: `scanCopy` apunta si la última cadena llevaba una `\` (`lastStringEscaped`) y, al ver `:` en un objeto de clase (todos menos el mapa `tagColors`), marca `escapedName`. `CopyShape` gana `escapedName`, y `checkCopyShape` rechaza con `COPY_DAMAGED`. A cualquier profundidad y con cualquier escape (`\uXXXX`, `\/`, `\"`). El KDoc del modelo y el de `checkCopyShape`, al día.
- **Rojo** (`rt26_` sin `@Ignore`, contra el código anterior): `java.lang.AssertionError: Estimate 108320, tree kept 2205832`.
- **Verde:** `rt26_anEscapedMemberNameIsEstimatedAsTheDecoderReadsIt` (rechazada antes de decodificar).
- Tests nuevos en `RedTeam20CopyShapeTest`:
  - `anEscapeInTheNameOfAFieldIsRefusedAtAnyDepth`: la raíz con `\u` y con `\/`, un miembro de una entrada, uno de un campo y uno de una v3; cada uno, rechazado por `checkCopyShape` y por «Importar copia» sin decodificar nada. Un escape en un valor pasa.
  - `theKeysOfTheTagColoursMayCarryEscapes`: una copia escrita por kotlinx con etiquetas `a"b`, `é`, `x\y` y `c\u0007` en `tagColors` pasa e importa con sus colores.
  - Siguen en verde `theRealFilesOfBeforePass` (los cuatro archivos de `app/src/test/resources/seguridad/`) y `resistsEscapesMultiByteAndRepeatedMembers` del atacante (su `entr\u0069es` sigue rechazado, ahora por RT-26).
- Ejecución: jvm-check `redteam.*`, `BackupCheckTest`, `BackupV3Test`, `VaultV4Test`: 155 tests, 1 ignorado (`rt25_`), 0 fallos.

## 2. Informativo 3
- `vault/BackupCheckTexts.kt`: `CopyReadStep` (`READ`, `TEXT`, `DECODE_FILE`), la costura solo para tests `beforeCopyRead` y `copyRead(step) { … }`, que convierte un `OutOfMemoryError` en `CryptoFailure(COPY_NO_MEMORY)`.
- `vault/VaultStore.kt`, `importReplace`: la exportación JSON lee sus bytes en `withContext(io) { copyRead(READ) { … } }` y hace su texto en `withContext(compute) { copyRead(TEXT) { … } }`; la `.sgbak` se lee en `withContext(io) { copyRead(READ) { BackupReader().read(…) } }` y su archivo se decodifica en `withContext(compute) { copyRead(DECODE_FILE) { … } }`, también en `importText` (la entrada de texto). Todo antes de `replace`; la carpeta temporal se borra en el `finally` que ya estaba.
- `vault/BackupCheck.kt`, `checkBackup`: lo mismo para la lectura y `decodeFile`; la carpeta se borra en su `finally`.
- Los guardados, la exportación y la copia automática, sin cambios (Ruling).
- **Tests** (`vault/NoMemoryTest`):
  - `readingACopyWithoutMemoryChangesNothing`: para la `.sgbak` (`READ`, `DECODE_FILE`) y para `export-1.0.2.json` (`READ`, `TEXT`, `DECODE_FILE`), sale `COPY_NO_MEMORY`, `vault.bin` y `settings.json` quedan iguales byte a byte, no queda ninguna carpeta `import-` y la bóveda sigue abierta; sin la costura, la copia se importa (cada paso se alcanzó de verdad).
  - `checkingACopyWithoutMemoryAtEachReadLeavesNoFolder`: «Comprobar una copia», `READ` y `DECODE_FILE`, sin carpeta; sin la costura, el informe sale.
- **Rojo** (con `copyRead` sin su `catch`): `7 tests completed, 2 failed`, los dos nuevos, con `java.lang.OutOfMemoryError`.
- **Verde:** jvm-check `redteam.*`, `NoMemoryTest`, `BackupCheckTest`, `BackupTest`: 161 tests, 1 ignorado (`rt25_`), 0 fallos.

## Correcciones del director a mitad de ronda (19-11-2026, por el controlador)
Llegaron después de `0ec7c14` y se aplican desde el punto 3: (1) el tope de RT-25 pasa a ser la suma del exceso sobre `MAX_TAGS` en cada entrada y versión, `> COPY_LIST_MAX` se rechaza; (2) con `noMemory` la lista no parece vacía, decidido por una función pura probada más un ancla; (3) el test del criterio, estable (calentar, mediana, margen por forma, < 10 s), con la forma del suelo juzgada en absoluto; (4) el test de fuente sobre `guard()` y el valor por defecto de `VaultBackup.kt:73` a `{ false }`; (5) el wifi con función pura más ancla; (6) comprobar «Importar como bóveda nueva» con `COPY_NO_MEMORY` y si la copia automática captura el OOM.

## 3. RT-25, parte a
- **Lo que se calcula solo al abrir una bóveda** (inventario) y su protección:

  | Qué | Dónde | Protección |
  |---|---|---|
  | Leer, decodificar, `withPlainVersions`, `sortedForShow`, vaciar la papelera | `vault/VaultStore.kt` (`unlockLocked`, `commit`, `unlockWithKeyLocked`) | Ya estaba: `VAULT_NO_MEMORY` (RT-21 c) |
  | Marcas de «Salud» de la lista (`vaultHealth`), sin 2FA, índice de búsqueda (`SearchIndex.build`), caducidad (`Expiry.dueSoon`), la búsqueda (`query`), recientes, favoritas, recuentos por tipo, fichas de etiquetas | `ui/screens/VaultList.kt`, `EntryListViewModel.compute` | **Nuevo**: `catch (e: OutOfMemoryError)` dentro del `withContext(Dispatchers.Default)`, `cache = null`, `ListResult.NO_MEMORY` |
  | Selección | `vault/Selection.kt` (`followVault`, `retainVisible`) | Lineal en lo enseñado; con `NO_MEMORY` queda vacía |
  | La pantalla: filas, fichas de etiquetas y de vistas | `VaultList.kt` (`LazyColumn`, `LazyRow`, R18) | Perezosas: solo lo que se ve; con `noMemory` no se pinta nada de eso |
  | La copia automática al abrir (`runDue(afterUnlock = true)`) | `vault/AutoBackup.kt:455-457` | No se cambia (Ruling, informativo 3). Ya captura: `attempt` tiene `catch (e: Throwable)` (`AutoBackup.kt:882`, «Never closes the app») y `copyBeforeAny` también (`:778`); un OOM ahí da `AUTO_BACKUP_FAILED`, sin cerrar la app (punto 6) |

  Lo que solo se calcula al abrir una pantalla («Salud», `HealthScreen.kt:135`; «Organizar», `vault/Folders.kt:270`, `withTagColor`, que reúne todas las etiquetas al poner un color con los 200 ocupados) va a `pendientes.md`, con la fila 51.
- `vault/Vault.kt`: `LIST_NO_MEMORY`, el texto exacto del Ruling, fuera de `ui/` (como `VAULT_NO_MEMORY`).
- `ui/screens/VaultList.kt`:
  - `ListResult.noMemory` y `ListResult.NO_MEMORY` (sin nada de la bóveda);
  - la costura solo para tests `beforeListCompute`;
  - la captura en `EntryListViewModel`, como arriba; `selection.retainVisible(r.shown)` vacía la selección; la bóveda sigue abierta; `collectLatest` vuelve a calcular con el siguiente cambio;
  - `ListShows` y la función pura `listShows(result)`: `OUT_OF_MEMORY` va primero, antes que `LOADING`, `EMPTY`, `NO_MATCH` y `ROWS`;
  - `EntryList` (la lista y «Códigos») pinta `LIST_NO_MEMORY` y vuelve antes de todo lo demás: no borra el filtro de etiquetas (`LaunchedEffect(result)`), no enseña herramientas, «Todas (0)», filas, «La bóveda está vacía» ni `EmptyVaultActions`; la rama de la lista vacía usa `listShows`.
- `ErrorTextsTest.theTextOfTheListOutOfMemory` (texto exacto y `ForbiddenWords`), fila nueva en `task-10c-inventory.md`, y el ancla de `PlatformSourcesTest` al día (la captura con `NO_MEMORY`).
- **Tests** (`ui/screens/EntryListNoMemoryTest`, solo en la app):
  - `theVaultListOutOfMemoryShowsTheTextAndStaysOpen` y `theCodesTabOutOfMemoryShowsTheTextAndStaysOpen`: con la costura, `noMemory` sin datos, selección vacía, la bóveda abierta y nada lanzado (manejador de excepciones del hilo); sin la costura, el siguiente `upsert` vuelve a calcular y enseña la lista;
  - `whatTheListPaints` (la función pura) y `theListShowsTheTextBeforeAnythingElse` (el ancla).
- **Rojo** (la captura cambiada a `IllegalStateException`): `3 tests completed, 2 failed`, los dos del ViewModel, `TimeoutCancellationException: Timed out waiting for 5000 ms` (el `OutOfMemoryError` salía sin capturar).
- **Verde:** app, `EntryListNoMemoryTest`, `ErrorTextsTest`, `SelectionTest`, `SourceTextsTest`, `ui.redteam.*`: en verde (el ancla vieja de `PlatformSourcesTest` falló y se puso al día); jvm-check, `redteam.*`, `NoMemoryTest`, `ErrorTextsTest`, `PlatformSourcesTest`, `SourceTextsTest`: en verde, 1 ignorado (`rt25_`).
- **Punto 6, «Importar como bóveda nueva»:** `AppContainer.newVault` captura cualquier `Throwable` y deshace el hueco (`dropVault`). Test nuevo `NoMemoryTest.anImportAsNewWithoutMemoryAtEachReadLeavesNoVault` (`READ` y `DECODE_FILE`): el registro, la bóveda activa y los ajustes quedan como estaban. Matiz: `settings.json` no queda igual byte a byte, porque `startClock` guarda la hora más alta vista (A11, `clockHighWaterMs`), que es la del reloj y no un cambio de datos; el test compara todo lo demás.

## 4. RT-25, parte b: el tope y el criterio (redefinido por el director)
### El tope (regla corregida por el director)
- `vault/BackupCheck.kt`: `copyExtraTags(entries, stopAbove)` suma, en cada entrada (también las de la papelera) y en cada versión de su historial, `max(0, tags.size − MAX_TAGS)`, en una pasada y sin conjunto. `checkCopyEntries` rechaza con `COPY_DAMAGED` si la suma es `> COPY_LIST_MAX` (justo en 10 000 pasa). Lo usan igual la importación (`VaultStore.replace`, en `prepare`) y «Comprobar una copia» (`checkBackup`). El KDoc explica por qué no se usa `max(COPY_LIST_MAX, MAX_TAGS × (entradas + versiones))`: las versiones (el historial se recorta a 20 en cada guardado, `fittedHistory`) y la papelera (se vacía a los 30 días) lo subirían, la bóveda quedaría por encima del tope y sus copias se rechazarían sin avisar. También explica el coste aceptado: se rechaza una copia hecha a mano con más de 10 000 etiquetas sobrantes en entradas de más de 10.
- Lo propio no cambia: abrir, guardar y exportar no tienen este tope.
- **Rojo** (la regla desactivada con `false &&`):
  - `rt25_aCopyThatPassesTheBudgetCanAlsoBeListed` (sin `@Ignore` y ya adaptado): `AssertionError: Accepted, but showing it needs 3.3 times its estimate (tree 15950336, index 72753608, chips 14810080; estimate 31395252)`;
  - `versionsAndTheTrashDoNotRaiseIt`: `the trash and its versions do not raise it: imported`;
  - `justAtTheCapPassesAndOneMoreIsRefused`: `one over the cap: imported`.
- **Verde:** jvm-check `redteam.*`, `BackupCheckTest`, `BackupTest`, `BackupV3Test`: 169 tests, 0 ignorados, 0 fallos.
- `rt25_` adaptado (lo autoriza el Ruling): «aceptada» quiere decir pasar `checkCopyShape`, la decodificación, `checkCopyStructure`, `checkCopySize` y `checkCopyEntries`. Su forma (exceso 299 400) se rechaza y el test pasa.
- Tests (`redteam/RedTeam20TagExcessTest`), cada uno por «Importar copia» y por «Comprobar una copia»:
  - `twoThousandEntriesWithTenUniqueTagsEachPass`: 20 000 etiquetas distintas y exceso 0. Pasa.
  - `aCopyOf1xWithFiftyTagsInAnEntryPasses`: la copia real `vault-1.4.3.json` (v3), con 50 etiquetas escritas a mano en su primera entrada y sellada otra vez con su clave. Exceso 40. Pasa.
  - `aVaultOfItsOwnWith30000TagsOpensButItsCopyIsRefused`: se abre con la maestra y con la clave de la huella; su copia (exceso 29 000) se rechaza con `COPY_DAMAGED`.
  - `theCopyOfTheAttackIsRefused`: 60 × 5 000, exceso 299 400. Se rechaza.
  - `justAtTheCapPassesAndOneMoreIsRefused`: con 10 000 pasa; con 10 001 se rechaza.
  - `versionsAndTheTrashDoNotRaiseIt`: 200 entradas en la papelera con 20 versiones de 10 etiquetas, que con la regla anterior habrían subido el tope a 42 000, siguen dejando sitio solo a 10 000 sobrantes. Las sobrantes de las versiones también cuentan (20 × 501).

### El criterio, primero parado y después redefinido por el director (opción (i))
**Primera medida (parada).** Con las legítimas escritas como la app (ids UUID, fechas de 13 cifras), «etiquetas distintas justo en el tope» daba 1,28x de la legítima más cara (17,65 B/B), por encima de 1,25x. La misma forma sin ninguna etiqueta sobrante ya daba 1,21x, así que lo que pasa de 1,25x es la compacidad del JSON (ids cortos, que LIBRE-1 admite, fechas de una cifra, título vacío) y no lo cierra ningún tope de etiquetas. Avisé al controlador y paré.

**Decisión del director (opción (i)):** se acepta el 1,28x, publicado tal cual, sin otro tope ni otro umbral. El criterio se redefine en `redteam/RedTeam20ListCostTest.kt` (su KDoc sustituye a `advisor-t12-redteam-fixes-2.md:37-51`):
- **B, el criterio:** el suelo que deja pasar el tope (b), dos entradas de 5 010 etiquetas distintas (exceso exacto 10 000), cuesta como mucho 4 MiB en absoluto (mediana de 5).
- **C, cada forma es la que dice:** cada forma pasa todas las comprobaciones de la importación, y `copyExtraTags` da su exceso exacto: 0 en las parejas, 10 000 en la del tope, en la de versiones y en el suelo, y 9 990 en la de repetidas.
- **D, guardia de regresión (no decide hallazgos):** la pareja sin exceso más densa queda como mucho a 1,5x de la legítima más cara escrita por la app.
- **Filas informativas**, sin aserción.

**Cómo se mide:** el montón vivo después de GC por byte de JSON, con muestras de 1 MiB. Cada forma tiene su `Random(20261118L)`, hay calentamiento, se toma la mediana de 5 y el orden es siempre el mismo (`measured()`: primero las legítimas, después las parejas, después el resto). **Hallazgo de la medida:** el valor depende de lo que la JVM hizo antes. La pareja del tope medida justo después del calentamiento daba 24,40 B/B, y después de las legítimas 21,40. La legítima (c) daba 16,28 o 17,65 según el orden. Con eso, D llegaba a 1,50 y la forma del tope quedaba por debajo de su pareja (0,925, imposible). Con el orden fijo, las tres ejecuciones dan lo mismo, ±0,004.

**Rojo** (sin commit): `false &&` en `BackupCheck.kt:240` y un suelo de exceso 100 000 (20 entradas de 5 010). `RED floor excess 100000`, mediana de 30 845 512 B: `AssertionError: the floor costs 30845512 bytes to show (at most 4194304)`. Deshecho después; `git diff app/src/main` vacío.

**`rt25_`:** la rama «aceptada» ya no compara con la estimación: `assertTrue("Accepted by every check of the import, with $excess tags over MAX_TAGS", refused)`. Su KDoc remite el criterio general a `RedTeam20ListCostTest`. Se quitó su `shownCost`, que ya no se usaba.

**Tabla** (B/B; mínimo, mediana y máximo de la ejecución 3 de la clase sola; los márgenes, de las tres ejecuciones de la clase sola, de `redteam.*` en jvm-check y de la app):

| Forma | Tipo | Mín. | Mediana | Máx. | Margen |
|---|---|---|---|---|---|
| Notas largas | legítima | 1,36 | 1,36 | 1,36 | 0,08 |
| Accesos mínimos distintos | legítima | 8,61 | 8,61 | 8,63 | 0,49 |
| Accesos con 10 etiquetas distintas de 5 letras | legítima | 17,63 | **17,64** | 17,65 | **1,00** (la más cara) |
| 30 campos visibles con nombres distintos | legítima | 5,17 | 5,17 | 5,17 | 0,29 |
| 20 versiones con etiquetas | legítima | 3,63 | 3,63 | 3,63 | 0,21 |
| Carpetas, vistas y colores en sus topes | legítima | 15,69 | 16,95 | 17,01 | 0,96 (bimodal en el mínimo; la mediana, igual en las tres) |
| Pareja del tope: 10 etiquetas distintas por entrada, JSON mínimo, exceso 0 | pareja | 21,40 | 21,40 | 21,40 | **D: 1,212–1,216 ≤ 1,5** |
| Pareja de las versiones, exceso 0 | pareja | 4,98 | 4,98 | 4,98 | — |
| Pareja de las repetidas, exceso 0 | pareja | 8,37 | 8,37 | 8,37 | — |
| Etiquetas distintas justo en el tope, exceso 10 000 | hostil | 22,59 | 22,60 | 22,64 | 1,055–1,057 frente a su pareja; **1,280–1,284 frente a la legítima (informativo, se publica tal cual)** |
| Versiones con etiquetas distintas, exceso 10 000 | hostil | 7,72 | 7,72 | 7,72 | 1,549 frente a su pareja; 0,438 frente a la legítima |
| Etiquetas repetidas, una entrada de 10 000, exceso 9 990 | hostil | 8,65 | 8,65 | 8,65 | 1,033 frente a su pareja; 0,490 frente a la legítima |
| Nombres de campo distintos, exceso 0 | hostil | 6,68 | 6,68 | 6,70 | 0,378 frente a la legítima |
| **Suelo**: dos entradas de 5 010 distintas, exceso 10 000 | B | 2 954 912 B | **2 954 912 B** | 2 954 912 B | **≤ 4 MiB** (en las cinco ejecuciones) |

- **Estimación** (`copyTreeEstimate`, informativa): la pareja da 9,69 B/B y la del tope 9,95 B/B. Por 24 MiB + 4 KiB: 243 912 040 y 250 518 411 bytes, frente a `COPY_TREE_MAX_BYTES` = 274 726 912. Las dos pasan el presupuesto del árbol.
- **Tiempos** (por test, con muestras de 1 MiB, así que no hizo falta la de 512 KiB): `cEachShapeIsWhatItSays`, 0,6–0,8 s; `dTheCompactEncodingStaysUnderItsGuard`, que mide las legítimas y las parejas, 5,4–7,7 s; `bTheFloorOfTheCapIsAtMostFourMiB`, 0,35–0,46 s; `informativeRows`, 2,2–3,1 s.
- **Ejecuciones:** jvm-check `RedTeam20ListCostTest` tres veces, 4 tests y 0 fallos cada vez; jvm-check `redteam.*`, 115 tests y 0 fallos; la app, `redteam.*` y `ui.redteam.*`, 118 tests y 0 fallos, y después `--stop`. Ninguna condición de parada: B = 2,95 MB (≤ 3,5 MiB), D ≤ 1,216 (≤ 1,30) y los márgenes se mueven como mucho ±0,004.
- La medida provisional `redteam-fixes-2-criterio.kt.txt` se quita: el test la sustituye.

## 5. Informativo 1
- **Test de fuente** `app/src/test/java/app/seguridad/EmptySlotGuardSourceTest.kt` (corre en jvm-check y en la app). No nombra `EntryListViewModel` ni `Field`.
  - `onlyTheTwoScreensCallTheStoreOnASlotAndEachAsksTheGuardFirst`: recorre `ui/` (sin los comentarios). Cada `.create(` y cada `.importReplace(` está en `VaultScreen.kt` o en `VaultBackup.kt`, con `!guard()` antes y en la misma función de primer nivel (`CreateVault`, `vaultActions`). Pide al menos 2 coincidencias, y hoy son exactamente 2. El `!guard()` que rechaza solo aparece en esos dos archivos, y cada archivo que llama a `guard()` lo lee de `LocalEmptySlotGuard.current`.
  - `theGuardIsProvidedAtTheRootAndRefusesByDefault`: un solo `LocalEmptySlotGuard provides container::mayFillEmptySlot`, en `AppNavHost.kt`, ningún otro proveedor en `ui/`, y el valor por defecto `{ false }`.
- **Una diferencia con la corrección del director:** él pedía «la llamada a guard() solo en esos dos archivos», pero `HealthScreen.kt:117-118` también llama a `guard()`, solo para elegir el texto (`HEALTH_NO_VAULT` o `HEALTH_EMPTY_VAULT`), sin llamar al almacén. El test lo admite: fija que el `!guard()` que rechaza solo esté en los dos archivos. Si el director prefiere quitar esa lectura de «Salud», es un cambio de pantalla aparte.
- **`ui/screens/VaultBackup.kt:73`:** el valor por defecto de `LocalEmptySlotGuard` pasa a `{ { false } }`, para que falle cerrado (una vista previa sin proveedor enseña `VAULT_NEW_NEEDS_OPEN`). El KDoc, al día.
- KDoc de `VaultStore.create` y de `importReplace(InputStream…)`: una pantalla solo los llama sobre un hueco después de `AppContainer.mayFillEmptySlot`. El almacén no cambia, así que `newVault` sigue funcionando.
- **Rojo** (con el valor por defecto `{ true }`): `2 tests completed, 1 failed`, `AssertionError: the default fails closed`. **Verde:** jvm-check `EmptySlotGuardSourceTest`, `PlatformSourcesTest`, `RedTeam20RecheckTest`: 53 tests, 0 fallos.

## 6. Informativo 2 (la ficha del wifi)
- `ui/screens/EntryFields.kt`:
  - funciones puras `isWifiName(type, key)` (`WIFI` y `WIFI_SSID_KEY`) y `fieldShownText(value, plain, marked)`: con `marked`, `visibleText` (`tools/qr/Visible.kt:23`); con `plain`, `plainReportText`; si no, el valor tal cual;
  - `EntryFieldsCard` pasa `marked = isWifiName(entry.type, t.key)` solo al campo de la plantilla;
  - `shownValue` pasa a `internal` para el test.
- `ui/screens/VaultEntryScreen.kt`: `Field` gana `marked: Boolean = false`, y su `Text` enseña `fieldShownText(value, plain, marked)`. «Copiar» sigue siendo `copySensitive(context, value)` (`:165`), con el valor tal cual. Los demás campos y los títulos siguen con `plainReportText` (R9 y R10). Sin estado guardado nuevo y sin texto nuevo, solo las marcas «⟨U+XXXX⟩».
- **Test** `ui/screens/EntryFieldsTest.theNameOfAWifiShowsItsMarksAndCopiesAsItIs` (solo en la app):
  - «Casa» + U+200B: `shownValue` da el valor tal cual, que es lo que copia `Field`, y se enseña como «Casa⟨U+200B⟩»;
  - con `plain`, sin marcas, «Casa»;
  - `isWifiName` es verdad solo para el nombre de la red de un wifi;
  - anclas: el `Text` de `Field`, un solo `copySensitive(context, value)` en `Field`, y un solo `marked = isWifiName(entry.type, t.key)` en la ficha. Ninguna pasa en vacío.
- `PlatformSourcesTest` (R9): el ancla de `Field` pasa a `fieldShownText(...)` y se añade `plain -> plainReportText(value, value.length)` en `EntryFields.kt`.
- **Rojo** (el `Text` con `marked` fijado a `false`): `3 tests completed, 1 failed`, el ancla del `Text` de `Field`. **Verde:** app, `ui.screens.*` (con `EntryListNoMemoryTest`), `PlatformSourcesTest`, `EmptySlotGuardSourceTest`, `ui.redteam.*` y `SourceTextsTest`: 79 tests, 0 fallos.

## 7. Cierre
### Tabla por hallazgo
| Hallazgo | Rojo | Verde | Tests |
|---|---|---|---|
| RT-26 | `rt26_`: `Estimate 108320, tree kept 2205832` | `bd0c31a` | `rt26_anEscapedMemberNameIsEstimatedAsTheDecoderReadsIt`; `RedTeam20CopyShapeTest.anEscapeInTheNameOfAFieldIsRefusedAtAnyDepth` y `theKeysOfTheTagColoursMayCarryEscapes`; siguen `theRealFilesOfBeforePass` y `resistsEscapesMultiByteAndRepeatedMembers` |
| Informativo 3 | `NoMemoryTest`: los dos nuevos con `java.lang.OutOfMemoryError` | `0ec7c14` | `readingACopyWithoutMemoryChangesNothing`, `checkingACopyWithoutMemoryAtEachReadLeavesNoFolder`; y, en `b632779`, `anImportAsNewWithoutMemoryAtEachReadLeavesNoVault` |
| RT-25 a | `EntryListNoMemoryTest`: los dos del ViewModel, `TimeoutCancellationException` (el OOM salía sin capturar) | `b632779` | `theVaultListOutOfMemoryShowsTheTextAndStaysOpen`, `theCodesTabOutOfMemoryShowsTheTextAndStaysOpen`, `whatTheListPaints`, `theListShowsTheTextBeforeAnythingElse`; `ErrorTextsTest.theTextOfTheListOutOfMemory`; ancla en `PlatformSourcesTest` |
| RT-25 b, tope | `rt25_`: `Accepted, but showing it needs 3.3 times its estimate…`; `versionsAndTheTrashDoNotRaiseIt` y `justAtTheCapPassesAndOneMoreIsRefused`: `…imported` | `655917d` | `rt25_aCopyThatPassesTheBudgetCanAlsoBeListed`; los seis de `RedTeam20TagExcessTest` |
| RT-25 b, criterio (redefinido) | B con `false &&` y un suelo de exceso 100 000: `the floor costs 30845512 bytes to show (at most 4194304)` | (commit del criterio) | `RedTeam20ListCostTest` (B, C, D y las filas informativas); `rt25_` con `assertTrue(refused)` |
| Informativo 1 | `the default fails closed` | `78d9708` | `EmptySlotGuardSourceTest` (2 tests) |
| Informativo 2 | El ancla del `Text` de `Field` | `7706c47` | `EntryFieldsTest.theNameOfAWifiShowsItsMarksAndCopiesAsItIs`; anclas de R9 en `PlatformSourcesTest` |

### Ejecuciones del cierre (19-11-2026, siempre con `flock`, una a una)
| Ejecución | Tests | Ignorados | Fallos |
|---|---|---|---|
| jvm-check: `BackupCheckTest`, `BackupV3Test`, `BackupTest`, `VaultV4Test`, `VaultStoreTest`, `AppContainerVaultsTest`, `ErrorTextsTest`, `SelectionTest`, `NoMemoryTest` | 199 | 0 | 0 |
| jvm-check: `redteam.*` | 110 | 0 | 0 |
| app: `redteam.*`, `ui.redteam.*`, `EntryListNoMemoryTest` y `EntryFieldsTest`; después `./gradlew --stop` | 120 | 0 | 0 |

Ningún `@Ignore` en `redteam/` ni en `ui/redteam/`. Las suites enteras no se corrieron, como pide el encargo.

### Punto 6 del controlador: lo que comprobé
- **«Importar como bóveda nueva» con `COPY_NO_MEMORY`:** `AppContainer.newVault` (`AppContainer.kt:670-677`) captura cualquier `Throwable` y deshace el hueco nuevo (`dropVault`: vuelve a la bóveda de antes, quita el registro y los ajustes de la nueva). Lo cubren el test que ya había y el nuevo `anImportAsNewWithoutMemoryAtEachReadLeavesNoVault`. Un matiz: `settings.json` puede cambiar en `clockHighWaterMs` (la hora más alta vista, A11, que guarda `startClock`). Es la contabilidad del reloj, no un cambio de datos, así que «No se ha cambiado nada» sigue siendo verdad para el usuario.
- **La copia automática al abrir** (`AutoBackup.kt:455-457`, `runDue(afterUnlock = true)`): codifica la bóveda en `VaultStore.export`, dentro de `attempt`, que tiene `catch (e: Throwable)` («Never closes the app: even an Error here only means there is no copy this time», `AutoBackup.kt:882-885`). `copyBeforeAny` hace lo mismo (`:778`). Un `OutOfMemoryError` ahí da `AUTO_BACKUP_FAILED` y no cierra la app. Solo lo informo, sin cambios.

### Comprobaciones finales
- **Regresión más amplia:** jvm-check `vault.*` y `AppContainer*` en una sola ejecución: 901 tests, 0 fallos. También las clases que leen los archivos de `ui/` que he tocado: jvm-check `ImportTextsTest`, `RedTeamRecheckTest` y `RedTeam20TagExcessTest` (63, 0 fallos), y la app `MarkupTextTest` (2, 0 fallos).
- **El valor por defecto `{ false }` no llega a nadie real:** el único `setContent` está en `MainActivity.kt:34` y no hay ningún `ComposeView`, así que todo pasa por el proveedor de `AppNavHost.kt:335`.
- **Quién llama a `checkCopyEntries`:** solo `VaultStore.replace` (`VaultStore.kt:1529`) y `checkBackup` (`BackupCheck.kt:99`). Abrir, guardar y exportar no tienen el tope.
- **Divergencia entre «Importar» y «Comprobar» con una copia de antes de la 2.0 (duda nueva, sin arreglar):** la importación comprueba los datos después de `upgraded()` (`Vault.kt:338-348`), que convierte cada contraseña antigua en una versión con las etiquetas de la entrada, ajustada a 64 KiB de historial por entrada. «Comprobar una copia» comprueba los datos tal como están guardados. La misma copia de la 1.x (dos entradas de 2 010 etiquetas con 5 contraseñas antiguas cada una: exceso 4 000 tal como está, varias veces más tras la subida) pasa «Comprobar» y la rechaza «Importar». Lo fija `RedTeam20TagExcessTest.aCopyOfBefore20CountsItsUpgradedVersionsOnlyWhenImported`, que pasa hoy. La 1.x real con 50 etiquetas y 5 contraseñas antiguas (exceso 40, y 240 tras la subida) sigue pasando las dos.

## Revisión experto-seguridad-cripto
- **RT-26 (CWE-436):** `scanCopy` marca `escapedName` en cuanto un nombre de miembro de clase lleva `\` (`vault/VaultLimits.kt:441`) y `checkCopyShape` rechaza (`:319`). Rechazar es más seguro que deshacer los escapes, porque no hace falta otro analizador, y las claves de `tagColors` (que son datos) siguen libres. El KDoc «never below it» vuelve a ser verdad.
- **RT-25 b (CWE-400, CWE-770, decisión (a)):** el tope va antes de construir el índice: `checkCopyEntries` (`vault/BackupCheck.kt:240`) corre en `prepare`, antes de sellar, y `VaultList.kt` solo construye el índice al abrir. Va en una pasada, sin reservar memoria y con parada temprana. Las versiones y la papelera solo suman, así que no hay forma de inflar el permiso. Lo propio no tiene tope (`aVaultOfItsOwnWith30000TagsOpensButItsCopyIsRefused`).
- **El criterio (redefinido por el director):** el exceso que deja pasar el tope está acotado en absoluto (2,95 MB ≤ 4 MiB). La densidad del JSON compacto (1,21x) queda como riesgo con nombre (fila 62), con su guardia D.
- **RT-25 a:** el `OutOfMemoryError` se captura solo alrededor de `compute` (`ui/screens/VaultList.kt:306`), que es lo que amplía R11c. La caché se suelta y no queda nada de la bóveda en `ListResult.NO_MEMORY`. Nunca dice «Contraseña incorrecta».
- **Informativo 3:** `copyRead` (`vault/BackupCheckTexts.kt`) convierte el OOM en `COPY_NO_MEMORY` dentro de cada `withContext` (`VaultStore.kt:1457`, `:1482`, `:1484`, `:1491`, `:1492`; `BackupCheck.kt:88`, `:90`), antes de `replace`. Las costuras (`beforeCopyRead`, `beforeListCompute`) son `internal` y no hacen nada en producción.
- **Informativo 1 (CWE-284):** el test de fuente fija el control de acceso de RT-22 (ii), y el valor por defecto falla cerrado (`ui/screens/VaultBackup.kt:74`). **Informativo 2 (CWE-451):** la ficha marca los invisibles del nombre de la red (`ui/screens/EntryFields.kt:141`, `VaultEntryScreen.kt:163`), y la copia sigue siendo el valor exacto (`VaultEntryScreen.kt:167`).
- **Lo que no cambia:** ninguna primitiva, ningún formato (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`: los cuatro archivos reales de los recursos pasan) y ninguna dependencia, permiso ni host.

## Revisión Ingeniero Móvil Senior
- **La lista:** la captura va dentro de `withContext(Dispatchers.Default)` (`VaultList.kt:300-310`). `collectLatest` vuelve a calcular con el siguiente cambio, sin bucles propios. `selection.retainVisible` vacía la selección, y la bóveda no se bloquea sola, así que se puede ir a Ajustes y exportar.
- **Compose:** `EntryList` vuelve pronto con `listShows(result) == OUT_OF_MEMORY` (`VaultList.kt:383-386`), antes de cualquier `remember`, efecto o `LazyColumn`. Vuelve pronto en un `@Composable`, y es válido; no borra el filtro de etiquetas. `Field(marked)` solo cambia lo que se pinta, sin estado guardado nuevo.
- **El test del ViewModel:** `Dispatchers.setMain(Unconfined)` y un manejador global de excepciones para ver que nada se escapa. Cada test, menos de 1 s.
- **Rendimiento:** `copyExtraTags` es O(entradas + versiones) y para en el tope.
- **Medidas:** el montón vivo después de GC (G1) depende de lo que la JVM hizo antes. El test del criterio necesitó una `Random` por forma, la mediana de 5 y un orden fijo de medida (`measured()`) para dar siempre lo mismo.

## Textos para el Abogado Digital (revisión final)
- **`LIST_NO_MEMORY`** (`vault/Vault.kt:296`), el texto exacto del Ruling: «Este móvil no tiene memoria suficiente para enseñar esta bóveda. La bóveda sigue guardada: cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede enseñarla.»
- **Las marcas del nombre de la red en la ficha:** «⟨U+XXXX⟩», las mismas del Inspector y de la pantalla del QR; ningún texto nuevo.
- **`COPY_DAMAGED`**, que ya existía, ahora también para una copia con más de 10 000 etiquetas sobrantes o con un escape en un nombre de campo (filas 54 y 57: «dañada» en el caso casi imposible de una copia propia hecha a mano).
- **`VAULT_NEW_NEEDS_OPEN`**, que ya existía, se enseña también en una vista previa sin proveedor (el valor por defecto que falla cerrado). No llega al usuario.

## Dudas para el director
1. **El criterio de RT-25:** decidido (opción (i)); nada más que preguntar.
2. **`HealthScreen.kt:117-118`** también lee `guard()`, para elegir su texto. El test lo admite (el `!guard()` que rechaza solo está en los dos archivos). ¿Se deja así?
3. **«Salud» y «Organizar»** sin la captura de la lista (fila 59): ¿se protegen en esta ronda o en otra?
4. **Copias de antes de la 2.0:** «Importar» cuenta las versiones que crea la subida y «Comprobar» no (fila 61). ¿Se cuenta el exceso antes de la subida, en los dos sitios, o se deja así?
