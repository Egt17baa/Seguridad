# Clavero 2.0, ronda 3 de los arreglos del ataque final (fila 63, RT-29, RT-27, RT-28 y «Salud»): informe del implementador

Encargo: `docs/superpowers/handoff/sdd-2.0/advisor-t12-redteam-fixes-3.md`, con la consulta de la fila 63 (`advisor-t12-redteam-fixes-3-fila63.md`). Base: `d18e1ad`. Sin *push*.

## Estado
| Punto | Estado | Commit |
|---|---|---|
| 1. Fila 63 | Hecho | 71af463 y el complemento 89b1194 |
| 2. RT-29 | Hecho | db66179 |
| 3. RT-27 | Hecho | 2e75be5; `rt29_` por recuento, 5a8b63b |
| 4. RT-28 | Hecho | a65b131 |
| 5. «Salud» | Hecho | 8ee51fa |
| 6. Cierre | Hecho | (este commit) |

## 1. Fila 63
- **Primera parada (consulta):** con la regla sola, `RedTeam20CopyTest.resistsHostileDatesEnumsAndNamesInAV4Copy`, que no estaba en la lista autorizada, se puso en rojo: `Only the long name is refused (La copia está dañada…)`. Su copia hostil lleva `"vaultTag":"<script>"`. El director autorizó el cambio con ajustes (`advisor-t12-redteam-fixes-3-fila63.md`).
- **Código:**
  - `vault/Vault.kt`: `isVaultTag(tag)`, junto a `newVaultTag`. Exige 32 caracteres ASCII `0-9a-f`.
  - `vault/BackupCheck.kt`, `checkCopyStructure`: rechaza con `COPY_DAMAGED` una etiqueta no vacía que no pase `isVaultTag`. KDoc al día.
  - `vault/VaultStore.kt` (`replace`, en `prepare`): «Importar como bóveda nueva» pasa `checkCopyData` y `JSON_HAS_ATTACHMENTS` sobre los datos leídos y subidos a v4, y solo después cambia la etiqueta y pone la fecha a 0. El comentario de RT-21a, al día. El parámetro de la lambda sigue llamándose `data`, para que el ancla de `PlatformSourcesTest` (`checkCopyData(data)`) no cambie.
- **Tests:**
  - `vault/VaultTagRuleTest`, nuevo:
    - 1 000 salidas de `newTag` pasan;
    - otras 1 000 pasan con `Locale.setDefault` en `ar-EG` y en `fa-IR`, restaurada en un `finally`. Esta prueba pasa;
    - se rechazan las mayúsculas, 31 y 33 caracteres, la cadena vacía, `<script>`, `'٣'`, la `'ａ'` de ancho completo y una `g`.
  - `RedTeam20Recheck2Test`: los cuatro `informative63_` cambian (autorizado) y prueban las tres puertas, «Comprobar», «Importar copia» e «Importar como bóveda nueva»:
    - `aShortTagIsRefusedByTheThreeGates` (etiqueta «a»): `COPY_DAMAGED` × 3;
    - `aLongTagIsRefusedByTheThreeGates` (64 «b»): `COPY_DAMAGED` × 3;
    - `aDateOfACheckOverTheLimitIsRefusedByTheThreeGates` (32 hex con fecha, en el límite + 20 contando la fecha): `COPY_DAMAGED` × 3;
    - `aDateOfACheckAtTheLimitPassesTheThreeGates`: `OK` × 3. La bóveda nueva tiene etiqueta propia válida y fecha 0, y lo sellado ocupa como mucho lo comprobado y pasa `checkCopyData`.
  - `RedTeam20CopyTest.resistsHostileDatesEnumsAndNamesInAV4Copy`: **autorizado por el director (consulta de la fila 63)**. Cuatro casos explícitos, cada uno con su `store("fechas-$i")` y sin el `catch … continue`:
    - `<script>` y nombre vacío: `COPY_DAMAGED`;
    - `<script>` y 5 000 «x»: `COPY_DAMAGED`;
    - 32 hex y 5 000 «x»: `COPY_DAMAGED`;
    - 32 hex y nombre vacío: se importa, las funciones de las pantallas no lanzan nada, y al bloquear y abrir con la maestra quedan los ids `{a, b}`.

    En los tres rechazos, el estado del almacén no cambia. KDoc al día.
- **Rojo** (con `isVaultTag` añadida, sin la regla y sin el cambio de orden): `4 failed`.
  - `informative63_aShortTag…`: `expected:<[COPY_DAMAGED ×3]> but was:<[OK, OK, COPY_DAMAGED]>`;
  - `informative63_aDateOfACheckOverTheLimit…`: `but was:<[COPY_DAMAGED, COPY_DAMAGED, OK]>` (el sentido de la fecha que faltaba);
  - `informative63_aLongTag…`: `but was:<[OK, OK, OK]>`;
  - `resistsHostileDatesEnumsAndNamesInAV4Copy`: `case 0 imported`.
- **Verde:**

  | Ejecución | Tests | Ignorados | Fallos |
  |---|---|---|---|
  | jvm-check: `redteam.*`, `VaultTagRuleTest`, `BackupCheckTest`, `BackupV3Test`, `BackupTest`, `VaultV4Test`, `AppContainerVaultsTest`, `NoMemoryTest` | 247 | 3 (`rt27_`, `rt28_`, `rt29_`) | 0 |
  | jvm-check: `PlatformSourcesTest`, `SingleDecodeTest` | 0 fallos | | |

  Ningún rechazo cambia el estado, y la prueba de la configuración regional pasa: no salta ninguna condición de parada.
- **Decisión del dueño sobre la configuración regional** (`f7d0da0`, anotada en `progress.md`): las cifras árabes o persas no importan. Una etiqueta que no sea de 32 hex ASCII se descarta con `COPY_DAMAGED`, y basta con que la app no se rompa ni se cuelgue. Ya no es condición de parada. Va en `89b1194`, un commit de complemento del punto 1: el controlador ya había hecho su commit (`f7d0da0`) encima de `71af463`, y no reescribo el suyo:
  - `vault/Vault.kt`, `hex`: escribe el hex a mano (`0123456789abcdef`), sin pasar por la configuración regional, así que la app nunca rechaza sus propias copias. Con configuración latina la salida es igual que antes: `VaultTagRuleTest.theTagIsTheSameAsBeforeInALatinLocale` compara 256 bytes con `"%02x"` en `Locale.ROOT`.
  - `VaultTagRuleTest.theAppsOwnTagsPassWithANonLatinDefaultLocale`: con `ar-EG` y `fa-IR`, restaurando la configuración en un `finally`. Pasa siempre.
  - `VaultTagRuleTest.aTagOfArabicOrPersianDigitsIsRefused`: una copia con `"٣".repeat(32)` o `"۳".repeat(32)` da exactamente `COPY_DAMAGED`, sin cambiar el estado ni lanzar otra excepción.
  - Verde: jvm-check `VaultTagRuleTest` (5), `RedTeam20Recheck2Test` y `RedTeam20CopyTest`: 0 fallos.

## 2. RT-29
- `vault/VaultLimits.kt`, `fittedHistory`:
  - mide cada versión una sola vez (`jsonBytes(EntryVersion.serializer(), v)`) y calcula el total como 2 + Σ + (k − 1);
  - primero hace el recorte a 20 de antes;
  - después, mientras queden versiones y el total pase de 64 KiB, quita la más antigua, la primera entre iguales como hacía `minBy`, y resta su tamaño y una coma si quedaba más de una;
  - si cabe, devuelve el mismo objeto;
  - `fittedWithSize` no cambia.
- **La aceptación con nombre** «la lectura de una copia no se corta a medias» va en el KDoc de `importReplace(InputStream…)` (`VaultStore.kt`) y de `checkBackup` (`BackupCheck.kt`), con su tope y la cifra medida.
- **Test diferencial** `vault/FittedHistoryTest`: el bucle de antes, copiado en el test como referencia, contra el nuevo.
  - Dos tandas de 1 000 casos, con `Random(20261119L)` y `+1`. Cubren de 0 a 25 versiones, empates en `savedAt`, totales a los dos lados de 64 KiB (cada tanda pide más del 10 % a cada lado) y notas con `"`, `\`, U+0001, U+001F, U+2028, «é», «€» y «😀».
  - Más los archivos `vault-1.1.json`, `vault-1.4.3.json` y `export-1.0.2.json` pasados por `readAsImported`: dan los mismos datos, y `===` cuando caben.
  - **Ninguna diferencia** en los 2 000 casos. El test pasa con el código de antes y con el nuevo.
  - Cada tanda tarda unos 2 s.
- **`rt29_`**, sin `@Ignore` y sin otros cambios:
  - **rojo:** `RT-29: 8 logins of 262144 B of notes: pair 37.5 ms, with 20 old passwords 515.8 ms (x13.7)`, y `AssertionError: the upgrade costs 13.7 times the read of the same copy (at most 5)`;
  - **verde:** `pair 51.9 ms, with 20 old passwords 97.3 ms (x1.9)`, por debajo del umbral de consulta (3).
- **Cifras de `rt29Rows_`:**

  | Fila | Antes | Después |
  |---|---|---|
  | 1 entrada / 2 entradas (256 KiB de notas, 20 contraseñas antiguas) | 70,9 ms / 132,5 ms (×1,87) | 15,1 ms / 31,8 ms (×2,10) |
  | «Comprobar», cancelada al decodificar, acaba después de (la pareja entera: unos 17 ms) | 103,8 ms | 27,8 ms |
  | Una derivación pedida mientras «Importar copia» sube a v4 espera | 98,7 ms | 22,1 ms |
  | `rt29_`: la copia hostil frente a la pareja | ×13,7 (×10,1 a ×14,4 en el atacante) | ×1,9 |

- **Verde:** jvm-check `redteam.*`, `FittedHistoryTest`, `VaultV4Test`, `VaultLimitsTest` y `EntryHistoryTest`: 176 tests, 2 ignorados (`rt27_` y `rt28_`), 0 fallos.

## 3. RT-27
- **Hecho en el árbol, sin commit:**
  - `imports/ImportMemory.kt`, nuevo: la costura `beforeImportWork`, `planMergeOrNoMemory` y `previewEstimateOrNoMemory`.
  - `imports/ImportSession.kt`: `estimateNoMemory`, `planNoMemory(run)` y `estimateNoMemory(plan, default, overrides)`. Lo borran `clearEstimate`, `onPlan` y `dropAll`.
  - `imports/ImportTexts.kt`: el texto `IMPORT_NO_MEMORY`, exacto.
  - `ui/screens/ImportScreen.kt`: solo llama a los envoltorios. Una instantánea nula sigue siendo el bloqueo; un plan nulo con instantánea va a `planNoMemory`, y un estimado nulo a `estimateNoMemory`. La primera rama del `when` de la vista previa es `s.estimateNoMemory -> Text(IMPORT_NO_MEMORY…)`.
  - Tests:
    - `ImportStateTest`: 5 nuevos (no queda nada guardado; un bloqueo o un «Cancelar» mientras tanto no enseñan el texto; un estimado viejo no marca el actual; cambiar una opción vuelve a intentarlo; los envoltorios dan nulo);
    - `ErrorTextsTest.theTextOfAnImportOutOfMemory`;
    - la fila del inventario de la fase C;
    - `rt27_` reescrito, como autoriza el punto 4.
- **Rojo:**
  - el `rt27_` original, sin `@Ignore`: `whole-vault work of «Importar de otra app» with no catch of OutOfMemoryError expected:<[]> but was:<[planMerge(read, it.entries, it.revision), previewEstimate(plan, default, overrides, existing, System.currentTimeMillis())]>`, con `planMerge allocates 142380816 B, the list's index 200634320 B (x0.71)`;
  - con la captura de los envoltorios cambiada a `IllegalStateException`: `ImportStateTest.theWrappersGiveNullWithoutMemory` y el `rt27_` nuevo, con `java.lang.OutOfMemoryError`.
- **Verde:**
  - jvm-check `rt27_`, `ImportStateTest` (49), `ErrorTextsTest` e `ImportTextsTest`: en verde;
  - la app, `ImportStateTest` y `ui.redteam.*`: 52 tests y 0 fallos (compila `ImportScreen.kt`).
- **Parada:** en la puerta (jvm-check `redteam.*`, `SourceTextsTest`, `PlatformSourcesTest` e `imports.*`: 543 tests y 1 fallo) se pone en rojo **`PlatformSourcesTest.Importar de otra app holds what it read in a ViewModel…`** (`PlatformSourcesTest.kt:688`), que no está en la lista autorizada. Su ancla de R16 pide literalmente `"planMerge(read, it.entries, it.revision)" in src` y `Regex("planMerge\\(").count == 1` en `ImportScreen.kt`. El `rt27_` autorizado pide lo contrario: ningún `planMerge(` suelto en `ImportScreen.kt`. Los dos no se pueden cumplir a la vez.
- **Resuelto:** el controlador autorizó cambiar esas dos líneas del ancla de R16 (`7deb0de`, **autorizado por el controlador (consulta del punto 3)**): `"val snap = store.importSnapshot()" in src && "planMergeOrNoMemory(read, snap.entries, snap.revision)" in src` y una sola coincidencia de `planMergeOrNoMemory\(`. El resto del ancla no cambia.
- **Puerta** (jvm-check `redteam.*`, `SourceTextsTest`, `PlatformSourcesTest`, `imports.*` y `ErrorTextsTest`): 549 tests y 0 fallos, salvo `rt29_`, que en esa ejecución dio `x6.0` (ver «RT-29: el test de tiempo varía» abajo). En las dos ejecuciones de `redteam.*` que siguieron dio `x1.9` y `x4.5`, y solo, `x2.4`.

## RT-29: el test de tiempo varía (a consulta)
- `rt29_`, sin tocar, mide la mediana de 3 de `readAsImported`, la hostil frente a la pareja. Después del arreglo dio, según la ejecución: ×1,9 (solo), ×1,9, ×4,5 y ×6,0 (dentro de suites más grandes) y ×2,4 (solo).
- Lo que más varía es la pareja: entre 11,6 y 51,9 ms. La hostil va de 52 a 97 ms.
- El arreglo es real: antes daba ×10 a ×14 y ahora sale por debajo de 5 en 4 ejecuciones de 5.
- Aun así, el test no es estable frente a su umbral (5) ni frente al umbral de consulta del director (3).
- **Decisión** (`56f7ba3`): opción (a). `rt29_` pasa a contar codificaciones, y queda **autorizado por el controlador (consulta de `rt29_`)**:
  - `vault/VaultLimits.kt`: el contador determinista `historyEncodings` (un `AtomicLong`, como `VaultCrypto.decodes`). Cuenta las versiones que `fittedHistory` codifica para medirlas.
  - `rt29_` afirma que la copia hostil (8 entradas de 20 versiones) hace como mucho 20 codificaciones de una versión por entrada. Es la cota exacta del código, más estricta que «versiones + 2»: `fittedHistory` codifica una vez cada versión que queda tras el recorte a 20, y nada más. Así lo explica el KDoc. Los milisegundos quedan como fila informativa.
  - **Rojo** (el bucle viejo, contando las versiones de cada lista codificada): `AssertionError: 1680 encodings of a version for 8 entries of 20 versions (at most 20 per entry)`, es decir, 210 por entrada. Deshecho.
  - **Verde:** 160 codificaciones, 20 por entrada (y ×2,4 como fila informativa). jvm-check `redteam.*` y `FittedHistoryTest`: verde.

## 4. RT-28
- `vault/EntryQuery.kt`: la función pura `listTags(listable, filterTags): ListTags(sorted, goneFromFilter)`. Hace un solo `HashSet`, que no se guarda, y es O(F + N).
- `ui/screens/VaultList.kt`:
  - `compute` llama a `listTags(listable, f.tags)`;
  - `ListResult` gana `goneFilterTags`;
  - `LaunchedEffect(result)` hace `if (result.goneFilterTags.isEmpty()) f.tags else f.tags - result.goneFilterTags` y nunca vuelve a mirar si algo está en `result.tags`. Sigue vigilando las carpetas que desaparecen.
- `PlatformSourcesTest` («a gone tag filter is dropped»): su ancla de esa línea, al día. Es un cambio mecánico del texto, que el dueño pide no parar.
- **`rt28_`**, reescrito (autorizado):
  - la copia sigue dando `OK`;
  - `listTags` con N = 200 000 y la vista de 10 000 da `gone` vacío, y tarda como mucho 2 veces `flatMapTo(HashSet()).sorted()` solo, con la mediana de 5 (medido: ×0,65 y ×0,97);
  - con 3 etiquetas quitadas de la bóveda, `gone` son exactamente esas 3;
  - el ancla: ningún `in result.tags`, `LaunchedEffect(result)` lee `goneFilterTags` (2 coincidencias) y `val tagsNow = listTags(listable, f.tags)` va en `compute`.
- **App:** `ui/screens/EntryListTagsTest`. Con la vista entera, `gone` vacío; al quitar 3 etiquetas, `goneFilterTags == {t0, t1, t2}`.
- **Rojo** (`rt28_` original sin `@Ignore`): `RT-28: view of 1 000 tags against 200 000: 519.4 ms on the main thread per result … (x3394)` y `AssertionError: the main thread compares each tag of the view with every tag of the vault (x3394 a set)`.
- **Verde:**

  | Ejecución | Tests | Fallos |
  |---|---|---|
  | jvm-check: `redteam.*` y `PlatformSourcesTest` | 196 | 0 |
  | La app: `EntryListTagsTest`, `EntryListNoMemoryTest` y `SelectionTest` | 13 | 0 |

- `RedTeam20ListCostTest` da las mismas cifras: D, 1,213; el suelo, 2 954 912 B.

## 5. «Salud»
- **(a)** En la rama `noMemory` de `HealthScreen.kt`, antes de volver: `LaunchedEffect(Unit) { job?.cancel() }`. La comprobación de filtraciones en marcha se para: después de la petición en curso no sale ninguna más, las marcas parciales se quedan en `breached` y no se llama a `dropResults`.
- **(b)** `vaultHealth(…, active: () -> Unit = {})` llama a `active` cada `HEALTH_ACTIVE_EVERY` = 256 entradas, en los accesos y en los wifi. `healthView(…, active)` lo pasa y lo llama también antes de las misiones. `HealthScreen` le da `{ work.ensureActive() }`, con el `job` del bloque `withContext(Dispatchers.Default)`. `healthView` sigue capturando solo `OutOfMemoryError`, y la lista no cambia: usa el valor por defecto.
- **Tests:**
  - jvm-check: `HealthViewTest.aCancellationFromActiveGoesOutAsItIs` (con k = 1, 3 y 8, la `CancellationException` sale tal cual, nunca como `NO_MEMORY`) y `vaultHealthCallsActiveBetweenEntries` (al menos entradas/256 llamadas).
  - Los de fuente autorizados:
    - `informativeSaludWithoutMemoryHidesTheCancelOfARunningCheck`, invertido: con `NO_MEMORY` la comprobación se para, sin `dropResults`;
    - `HealthScreenTest.outOfMemoryShowsTheTextBeforeAnythingElse`, que ahora lee la rama hasta su `return@BackScaffold`;
    - `vaultHealthRunsOnceOffTheMainThread`, que sigue en verde sin cambios (una sola `healthView(`, a 4 líneas o menos de `withContext(Dispatchers.Default)`, y las mismas claves).
  - `MissionsTest.titlesLiveOnlyWithTheOpenVault`: su ancla, al día (`active`). Es un cambio mecánico.
- **Rojo** (sin la parada en `NO_MEMORY` y sin `active` entre entradas): `3 failed`.
  - `vaultHealthCallsActiveBetweenEntries`: `0 calls for 10000 entries`;
  - `aCancellationFromActiveGoesOutAsItIs`: `k = 3: not cancelled (1 calls)`;
  - `informativeSalud…`: `NO_MEMORY stops the running check`.
- **Verde:**

  | Ejecución | Tests | Fallos |
  |---|---|---|
  | jvm-check: `redteam.*` y `health.*` | 171 | 0 |
  | La app: `HealthScreenTest`, `EntryListNoMemoryTest` y `ui.redteam.*` | 10 | 0 |

## 6. Cierre

### Tabla de rojos y verdes
| Hallazgo o punto | Rojo | Verde | Tests |
|---|---|---|---|
| Fila 63 | `[OK, OK, COPY_DAMAGED]`, `[COPY_DAMAGED, COPY_DAMAGED, OK]`, `[OK, OK, OK]` y `case 0 imported` | `71af463`, `89b1194` | Los cuatro `informative63_` (autorizados), `resistsHostileDatesEnumsAndNamesInAV4Copy` (autorizado por el director, consulta de la fila 63) y `VaultTagRuleTest` (5) |
| RT-29 | `rt29_`: `x13.7` en tiempo; en codificaciones, `1680` (210 por entrada) | `db66179`, `5a8b63b` | `FittedHistoryTest` (diferencial, 2 000 casos, sin ninguna diferencia) y `rt29_` (autorizado por el controlador, consulta de `rt29_`: 160 codificaciones, 20 por entrada) |
| RT-27 | El `rt27_` original: `but was:<[planMerge(…), previewEstimate(…)]>`. Con la captura cambiada: `OutOfMemoryError` en dos tests | `2e75be5` | `rt27_` (reescrito), 5 en `ImportStateTest`, `ErrorTextsTest.theTextOfAnImportOutOfMemory` y el ancla de R16 de `PlatformSourcesTest` (autorizado por el controlador, consulta del punto 3) |
| RT-28 | `rt28_` original: `519 ms … (x3394)` | `a65b131` | `rt28_` (reescrito), `EntryListTagsTest` (en la app) y el ancla de `PlatformSourcesTest` |
| «Salud» | `0 calls for 10000 entries`, `k = 3: not cancelled (1 calls)` y `NO_MEMORY stops the running check` | `8ee51fa` | `HealthViewTest` (2 nuevos), `informativeSalud…` invertido, `HealthScreenTest` ajustado y `MissionsTest` (ancla) |

### Cifras de RT-29, antes y después
| Medida | Antes | Después |
|---|---|---|
| Codificaciones de una versión por entrada, con 20 contraseñas antiguas | 210 | 20 |
| `rt29_`: la copia hostil frente a la pareja, en tiempo (informativo) | ×13,7 (×10,1 a ×14,4 en el atacante) | ×1,9 a ×4,5 según la carga de la suite (×6,0 una vez, con la puerta grande) |
| 1 entrada / 2 entradas | 70,9 / 132,5 ms | 15,1 / 31,8 ms |
| «Comprobar», cancelada al decodificar, acaba después de | 103,8 ms | 27,8 ms |
| Una derivación pedida mientras «Importar» sube a v4 espera | 98,7 ms | 22,1 ms |

### Ejecuciones del cierre (con `flock`, de una en una)
| Ejecución | Tests | Ignorados | Fallos |
|---|---|---|---|
| jvm-check: `BackupCheckTest`, `BackupV3Test`, `BackupTest`, `VaultV4Test`, `AppContainerVaultsTest`, `NoMemoryTest`, `RedTeam20CheckAsImportTest`, `FittedHistoryTest`, `VaultTagRuleTest`, `ImportStateTest`, `ErrorTextsTest`, `ImportTextsTest`, `HealthViewTest` y `PlatformSourcesTest` | 247 | 0 | 0 |
| jvm-check: `redteam.*` | 136 | 0 | 0 |
| La app: las mismas clases, más `HealthScreenTest`, `EntryListNoMemoryTest` y `EntryListTagsTest` | 255 | 0 | 0 |
| La app: `redteam.*` y `ui.redteam.*`; después `--stop` | 139 | 0 | 0 |

No queda ningún `@Ignore` en `redteam/` ni en `ui/redteam/`.

### Notas
- **El `--amend` que se coló:** un `--amend` mío llegó a fundirse con el commit `f7d0da0` del controlador. Lo deshice al momento con `git reset --soft f7d0da0`: su commit queda intacto y mi complemento va en `89b1194`. No volví a usar `--amend`.
- **Anclas de fuente puestas al día por cambios mecánicos**, como pide el dueño:
  - `PlatformSourcesTest`: R16, autorizado, y la de las etiquetas del filtro;
  - `MissionsTest`: el cálculo de «Salud».

## Revisión experto-seguridad-cripto
- **Fila 63** (CWE-436, R5): las tres puertas comprueban los mismos datos.
  - `BackupCheck.kt` (`checkCopyStructure`, la regla de `isVaultTag`).
  - `VaultStore.kt` (`replace`, `prepare`: `checkCopyData(data)` antes de cambiar la etiqueta y la fecha).
  - La etiqueta de la app no depende de la configuración regional (`Vault.kt`, `hex` a mano).
  - Lista blanca: solo se admite lo que escribe la app, y nada publicado lleva `vaultTag` (LIBRE-1 intacto).
- **RT-29** (CWE-407):
  - lineal (`VaultLimits.kt`, `fittedHistory`), con el mismo resultado (`FittedHistoryTest`), y la decisión de qué versiones se borran no cambia;
  - la parte que no se puede cancelar queda aceptada con nombre (filas 67 y 66) y con sus cifras.
- **RT-27** (CWE-400 y CWE-770, decisión (a)):
  - `imports/ImportMemory.kt`;
  - con `estimateNoMemory`, `canConfirm` da falso (`ImportSession.kt`), así que nunca se llega a un guardado sin captura (fila 69);
  - `IMPORT_NO_MEMORY` es verdad («No se ha cambiado nada»).
- **RT-28** (CWE-407 y CWE-1050): `listTags` (`EntryQuery.kt`) es O(F + N), en `compute`, fuera del hilo principal, y no guarda ningún conjunto. El criterio de RT-25 sigue igual: D 1,213 y el suelo, 2 954 912 B.
- **«Salud»:** `active` hace que el cálculo se pueda cancelar (`Health.kt`, `HealthView.kt`), y con `NO_MEMORY` no sale ninguna petición más a HIBP (`HealthScreen.kt`, `LaunchedEffect(Unit) { job?.cancel() }`).
- **Lo que no cambia:** ninguna primitiva, clave, formato, dependencia, permiso ni host.

## Revisión Ingeniero Móvil Senior
- **RT-27:** la captura va dentro del bloque de `Dispatchers.Default` de cada llamada (`ImportScreen.kt`). La vista previa ya no se queda en «Calculando». La máquina de estados se prueba en jvm-check (`ImportStateTest`).
- **RT-28:** en el caso normal, el efecto de Compose en `Main` es O(1) (`VaultList.kt`, `LaunchedEffect(result)`). La poda se calcula en `compute`, en `Default`. Sin estado guardado nuevo.
- **«Salud»:**
  - `produceState` ahora sí corta el cálculo anterior, porque `ensureActive` va entre entradas;
  - el `LaunchedEffect` de la rama `noMemory` para la comprobación de filtraciones;
  - `DisposableEffect` sigue parándola al salir.
- **Tiempos:** `rt29_` pasa a contar codificaciones, porque los tiempos variaban con la carga de la suite. `rt28_` compara con una medida del mismo tamaño, con la mediana de 5 (×0,65 y ×0,97).
- **Pruebas:** siempre con `flock`, una ejecución a la vez, y `--stop` después de la app.

## Textos para el Abogado Digital (revisión final)
- **`IMPORT_NO_MEMORY`** (`imports/ImportTexts.kt`): «Este móvil no tiene memoria suficiente para preparar esta importación. No se ha cambiado nada. Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede hacer esta importación.»
- `COPY_DAMAGED`, que ya existía, ahora también para una etiqueta que la app no escribe (fila 63).

## Después del cierre: una corrutina viva en un test de la app (clon limpio de `bbcf90d`)
- **El fallo:** en el clon limpio, `ActiveGateTest.aSwitchDuringTheFingerprintsDecryptionWritesNothing` dio `UncaughtExceptionsBeforeTest`, por «Dispatchers.Main was accessed when the platform dispatcher was absent».
- **Causa:** `EntryListNoMemoryTest` y `EntryListTagsTest` cancelaban el `viewModelScope` sin esperarlo. Un cálculo de la lista que seguía en `Default` volvía a `Dispatchers.Main` después de `resetMain()`. En el clon, la excepción sale en el `system-err` de `EntryListNoMemoryTest`. No es una fuga de la app.
- **Arreglo, solo en los tests:** `list.viewModelScope.coroutineContext.job.cancelAndJoin()` antes de `resetMain`, en los dos. También se quita el `import org.junit.Ignore` que sobraba en `RedTeam20Recheck2Test.kt`.
- **Rojo** (temporal, sin commit): un cálculo dormido 500 ms al acabar `EntryListTagsTest` y un `@After` que, después de `resetMain`, recoge lo no capturado. Resultado: `AssertionError: a coroutine of the test was left alive: [java.lang.IllegalStateException: Dispatchers.Main was accessed when the platform dispatcher was absent…]`. Es la misma excepción que en el clon.
- **Verde:**
  - con `cancelAndJoin` y el mismo montaje temporal, `EntryListTagsTest` y `ActiveGateTest`: 3 tests y 0 fallos;
  - sin el montaje, en la app, `ui.screens.*`, `ActiveGateTest`, `RedTeam20Recheck2Test` y `ui.redteam.*`: 48 tests, 0 fallos y ningún «Dispatchers.Main was accessed» en los resultados;
  - jvm-check, `RedTeam20Recheck2Test`: en verde.
