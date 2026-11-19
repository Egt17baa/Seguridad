# Clavero 2.0: re-comprobación 3 del equipo rojo (arreglos de la ronda 3, decisión (f))

## Estado

- **Hecha.** Base: `bbcf90d` (cierre de la ronda 3), en el *worktree* `/home/user/Seguridad-rt`, rama `rt-2.0`.
- **Alcance:** el que fijó el dueño el 19-11-2026 para ir más rápido (`progress.md`, «Owner decision», ir más rápido hasta las revisiones):
  - solo los arreglos de la ronda 3 (fila 63, RT-27, RT-28, RT-29 y «Salud») y que no vuelvan RT-21 a RT-29;
  - ningún ataque nuevo sobre otras partes de la app;
  - corre a la vez que `code-review` y `security-review`.
- **Veredictos:**
  - fila 63, RT-27, RT-28, RT-29 y «Salud»: **cerrados**;
  - los tests reescritos con autorización (`rt27_`, `rt28_`, `rt29_`, los cuatro `informative63_`, `resistsHostileDatesEnumsAndNamesInAV4Copy` y el ancla de R16): **prueban lo que dicen**, con dos observaciones menores (I-2 e I-3);
  - regresiones RT-21 a RT-26, `RedTeam20ListCostTest` y `RedTeam20CheckAsImportTest`: **en verde, sin cambios en sus tests**.
- **Ningún hallazgo nuevo** (ningún RT-30). No abre la ronda 4.
- **Un informativo nuevo**, I-1: la sesión de importar no mantiene sola «“Importar” nunca activo con `estimateNoMemory`». No se llega a ese estado desde la pantalla. Arreglo de una línea, para la ronda única de arreglos.
- **Último commit:** «2.0, ataque: re-comprobación 3 de los arreglos (ronda 3: cerrados, sin hallazgos nuevos)».
- **Tests:** `app/src/test/java/app/seguridad/redteam/RedTeam20Recheck3Test.kt`, con 11 tests: 10 `resists…` y 1 `informative…`. Ninguno es de tiempo: son recuentos, resultados exactos y anclas de fuente.
- **Ejecuciones (19-11-2026, con `flock`, de una en una):**

  | Ejecución | Tests | Ignorados | Fallos |
  |---|---|---|---|
  | Base, `jvm-check`, `redteam.*` (las clases de `bbcf90d`) | 136 | 0 | 0 |
  | `jvm-check`, `--tests 'app.seguridad.redteam.*'` | 147 | 0 | 0 |
  | La app, `:app:testDebugUnitTest --tests 'app.seguridad.redteam.*' --tests 'app.seguridad.ui.redteam.*'`, después `./gradlew --stop` | 150 | 0 | 0 |

  - La ejecución de base compiló ya `RedTeam20Recheck3Test`, porque se escribió mientras corría. Dio 147 tests y 1 fallo, el de un ancla mía: buscaba un comentario que `code()` quita. Se corrigió el ancla. Las 136 de `bbcf90d`, en verde.
  - No queda ningún `@Ignore` en `redteam/` ni en `ui/redteam/`.

## Resumen

| RT o punto | Arreglo | Veredicto | Severidad |
|---|---|---|---|
| Fila 63 | `isVaultTag` en `checkCopyStructure`, y «Importar como bóveda nueva» comprueba antes de cambiar la etiqueta (`71af463`, `89b1194`) | Cerrado. Las tres puertas dicen lo mismo con 10 etiquetas × 2 fechas, y en el único borde de tamaño que las distingue | — |
| RT-27 | `ImportMemory.kt`, `planNoMemory` y `estimateNoMemory` (`2e75be5`) | Cerrado. La máquina de estados resiste; I-1 es informativo | — |
| RT-28 | `listTags` en `compute` y `goneFilterTags` (`a65b131`) | Cerrado. Un resultado viejo nunca poda más que la regla de antes | — |
| RT-29 | `fittedHistory` lineal (`db66179`) y `historyEncodings` (`5a8b63b`) | Cerrado. Exacto en 64 KiB y en 64 KiB + 1, de 1 a 25 versiones, con empates; una codificación por versión, exacta | — |
| «Salud» | `active` entre entradas y la parada de la comprobación con `NO_MEMORY` (`8ee51fa`) | Cerrado. Ninguna petición después de la cancelación, y ninguna `CancellationException` tragada | — |
| Tests reescritos | `rt27_`, `rt28_`, `rt29_`, `informative63_` (4), `resistsHostileDatesEnumsAndNamesInAV4Copy` y el ancla de R16 | Prueban lo que dicen; I-2 e I-3 son menores | — |
| Regresiones | RT-21 a RT-26, `RedTeam20ListCostTest` y `RedTeam20CheckAsImportTest` | En verde. Ninguno de sus tests cambió en la ronda 3 (`git diff d18e1ad..bbcf90d -- app/src/test`) | — |
| **I-1** (nuevo) | — | Informativo: `estimated` no borra `estimateNoMemory` y `canConfirm` no lo lee | Info |

## Veredictos

### Fila 63: cerrada
- **Regla:** `isVaultTag` (`vault/Vault.kt:373`) exige exactamente 32 caracteres ASCII `0-9a-f`. `checkCopyStructure` (`vault/BackupCheck.kt:292`) rechaza con `COPY_DAMAGED` una etiqueta no vacía que no la cumpla.
- **Orden:** «Importar como bóveda nueva» llama a `checkCopyData(data)` y después cambia la etiqueta y la fecha (`vault/VaultStore.kt`, `prepare` en `replace`). `AppContainer.importAsNewVault` no añade otra comprobación: solo llama a `importReplace(input, master, newTag = true)`.
- **La etiqueta de la app** se escribe en hex a mano (`Vault.kt:338-345`), sin pasar por la configuración regional.
- **Bordes** (`resistsIsVaultTagAtItsBorders`): se rechazan la cadena vacía, las mayúsculas, 31 y 33 caracteres, las cifras árabes y persas, el ancho completo (`０`, `ａ`), una `g`, un NUL, un par sustituto, un espacio, un salto de línea y los caracteres vecinos de los rangos (`/`, `:`, `` ` ``).
- **Diferencial de las tres puertas** (`resistsTheThreeGatesSayTheSameWithEachTagAndDate`):
  - 10 etiquetas: ninguna, una como la de la app y ocho que la app no escribe;
  - por 2 fechas de comprobación: ninguna, y una;
  - «Comprobar», «Importar copia» e «Importar como bóveda nueva» dan exactamente lo mismo, `OK` o `COPY_DAMAGED`;
  - un rechazo no cambia el estado del almacén;
  - lo sellado por «Importar como bóveda nueva» tiene etiqueta propia válida y fecha 0, y pasa `checkCopyData`;
  - «Importar copia» conserva la etiqueta válida, o crea una si no había.
- **El borde de tamaño que distingue las puertas** (`resistsAV4CopyWithoutATagAtTheLimitIsCountedWithItsTagByTheThreeGates`):
  - una v4 sin etiqueta crece al recibirla (`Vault.kt:636`, en `readAsImported`, antes de las comprobaciones de las tres puertas);
  - en el límite, contando la etiqueta que se le da, las tres dan `OK`, y lo sellado ocupa como mucho lo comprobado;
  - con un byte más, las tres dan `COPY_DAMAGED`.

### RT-27: cerrado
- **Envoltorios** (`imports/ImportMemory.kt`): capturan solo `OutOfMemoryError`, dos `catch` en total.
  - Con el gancho lanzando `CancellationException`, los dos la dejan salir tal cual.
  - Otro `Error` (`StackOverflowError`) tampoco se convierte en nulo.
  - Test: `resistsTheWrappersLetACancelOutAndTheScreenRoutesNullToNoMemory`.
- **La pantalla:**
  - un plan nulo va a `planNoMemory(run)`;
  - un estimado nulo va a `estimateNoMemory`;
  - `CancellationException` se relanza antes de `catch (e: Exception)`;
  - hay una sola `importSnapshot()`, y `onPlan(run, plan, snap.entries)` usa la misma instantánea (R16).
- **Máquina de estados** (`resistsAPlanWithoutMemoryOnlyForTheRunStillReading`, `resistsAnEstimateWithoutMemoryKeepsImportOffAndAStaleOneIsDropped`):
  - `planNoMemory` deja el primer paso sin nada guardado, con `IMPORT_NO_MEMORY`, «Importar» desactivado y `startSaving()` nulo;
  - un bloqueo, un «Cancelar» u otro archivo mientras tanto: el resultado viejo se descarta y el texto no aparece;
  - `estimateNoMemory` deja «Importar» desactivado;
  - un estimado viejo (otras opciones, otro plan o un bloqueo) no cambia nada;
  - cambiar una opción vuelve a intentarlo.
- **Veracidad de `IMPORT_NO_MEMORY`:** «No se ha cambiado nada» es cierto en las dos rutas. El plan y el estimado no tocan la bóveda, y con `estimateNoMemory` no se llega a guardar (salvo I-1, que la pantalla no alcanza).

### RT-28: cerrado
- `listTags` (`vault/EntryQuery.kt:152-156`) va en `compute`, en `Default`. El efecto de `VaultList.kt` hace `f.tags - result.goneFilterTags` solo si algo falta.
- **Un resultado viejo que poda el filtro de ahora** (`resistsAStaleResultNeverPrunesMoreThanTheRuleOfBefore`). Es un diferencial de 3 000 casos de la regla nueva contra la de antes (`f.tags.filter { it in result.tags }`), con el resultado calculado para un filtro f1 y el filtro de ahora f2:
  - con f2 = f1, las dos reglas dan lo mismo;
  - con un resultado viejo (más de 500 casos), la nueva conserva un superconjunto de la de antes, y lo que conserva de más está solo en f2 \ f1, lo que el resultado no miró. El resultado siguiente, calculado para f2, lo quita;
  - nunca quita una etiqueta que tenga la bóveda.
- `rt28_` dio ×0,95, ×1,00 y ×0,97 frente a su tope de 2 (I-3).

### RT-29: cerrado
- **El diferencial de la ronda es fiable:** `FittedHistoryTest.before()` es, línea a línea, el `fittedHistory` de `db66179^`. Lo he comprobado con `git show`.
- **Los bordes** (`resistsFittedHistoryIsExactAtItsBordersAndEncodesEachVersionOnce`):
  - de 1 a 25 versiones, con fechas distintas y todas iguales;
  - con el historial en exactamente 64 KiB y en 64 KiB + 1;
  - en los 100 casos, las mismas versiones que el bucle de antes;
  - el mismo objeto cuando cabe;
  - una sola versión que pasa el límite deja el historial vacío;
  - siempre quedan como mucho 20 versiones y 64 KiB.
- **El recuento es exacto:** `historyEncodings` es exactamente `min(k, 20)` en cada caso, y 0 con el historial vacío. `rt29_` solo afirma la cota superior (I-2).
- **`historyBytes()`** sigue en `VaultLimits.kt:225` (`HISTORY_TOO_BIG`): es una codificación por entrada, ya ajustada, y es lineal.
- **`rt29_`**, informativo: 160 codificaciones (20 por entrada) en las dos ejecuciones; ×3,3, ×2,2 y ×3,7 en tiempo.

### «Salud»: cerrada
- **Ninguna petición después de la cancelación** (`resistsNoRequestGoesAfterTheCancelOfALeakCheck`):
  - la única llamada a la red de la pantalla es `pwned.times(…)`, en el bucle de la comprobación;
  - con MockWebServer, se cancela mientras su petición está en vuelo: de 5, sale exactamente 1;
  - la parada de `NO_MEMORY` es `LaunchedEffect(Unit) { job?.cancel() }`, en la rama, antes de su `return@BackScaffold`.
- **Lo que esto prueba:** la pantalla se ancla por fuente. Lo que se afirma es «ninguna petición después de la cancelación», que corre en cuanto se compone `NO_MEMORY`. La petición en vuelo termina y su resultado se descarta.
- **Ninguna `CancellationException` tragada** (`resistsNoCancellationExceptionIsSwallowedBySalud`):
  - con 600 accesos, una cancelación desde `active` en cualquiera de sus llamadas (entre entradas y antes de las misiones) sale tal cual, nunca como `NO_MEMORY`;
  - `healthView` tiene un solo `catch`, el de `OutOfMemoryError`;
  - la pantalla le pasa `work.ensureActive()`;
  - el `catch (e: Exception)` de `Health.kt:96` envuelve solo `URI(…)` en `hostOf`, sin `active`.

### Tests reescritos con autorización
- **`rt27_`:** prueba los dos envoltorios con el error hecho a mano, el estado de la sesión y el ancla de la pantalla (ningún `planMerge(` ni `previewEstimate(` suelto, y cada envoltorio una vez). No ancla el reparto nulo → `planNoMemory` / `estimateNoMemory`. Lo cubre `resistsTheWrappersLetACancelOut…`.
- **`rt28_`:** la copia pasa «Comprobar», `gone` vacío con la vista entera, exactamente las 3 etiquetas quitadas y el ancla de `VaultList.kt`. Su aserción de tiempo es I-3.
- **`rt29_`:** cuenta codificaciones, como decidió el controlador. Es I-2.
- **Los cuatro `informative63_`:** prueban las tres puertas de verdad, con `checkBackup`, `importReplace` e `importReplace(newTag = true)`, que es lo que ejecuta `importAsNewVault`. El de «en el límite» comprueba la etiqueta propia, la fecha 0, lo sellado ≤ lo comprobado y `checkCopyData`.
- **`resistsHostileDatesEnumsAndNamesInAV4Copy`:** cumple la consulta de la fila 63. Son cuatro casos explícitos, cada uno con su almacén, `COPY_DAMAGED` exacto, el estado sin cambios en los rechazos y sin el `catch … continue`. `<script>` con el nombre vacío es el caso que prueba la regla.
- **El ancla de R16** (`PlatformSourcesTest.kt:690-692`): solo cambian las dos líneas autorizadas, y conserva lo que fija R16:
  - una instantánea que da las entradas y la revisión (`.revision` una vez);
  - una sola llamada al plan.

  Como antes, no ancla que `onPlan` reciba `snap.entries`. Lo ancla ahora `resistsTheWrappersLetACancelOut…`, junto con una sola `importSnapshot(`.
- **Los cambios mecánicos** de `MissionsTest`, `HealthScreenTest`, `informativeSalud…` y del ancla de las etiquetas del filtro: comprobados en el diff. Ninguno afloja lo que fijaba.

## Informativos

### I-1 [Info] La sesión de importar no mantiene sola «“Importar” nunca activo con `estimateNoMemory`»
- **Lo que pasa:**
  - `ImportSession.estimated(…)` (`imports/ImportSession.kt`) pone `overBytes` y no borra `estimateNoMemory`;
  - `canConfirm` no lee `estimateNoMemory`.
- **Secuencia:** `estimateNoMemory(p, d, o)` seguido de `estimated(p, d, o, PreviewEstimate(0, …))` con las MISMAS opciones. «Importar» queda activo, y la vista previa sigue enseñando `IMPORT_NO_MEMORY`, que es su primera rama.
- **Por qué no es un hallazgo:** la pantalla no lo alcanza.
  - Las dos llamadas tienen un solo sitio, el `collectLatest` sobre `snapshotFlow` de (plan, opciones) (`ui/screens/ImportScreen.kt:207-223`).
  - Cada cambio cancela el estimado anterior, y `withContext` descarta el resultado después de una cancelación. Así, nunca llegan dos estimados de las mismas opciones.
  - Aunque se llegara, el estimado habría cabido, es decir, la memoria alcanzó. El guardado tiene su comprobación exacta y queda sin captura con nombre (fila 69).
- **Test:** `RedTeam20Recheck3Test.informativeAnEstimateAfterNoMemoryForTheSameChoicesTurnsImportOn`. Fija el comportamiento de hoy y ancla que solo hay una llamada, dentro de `collectLatest`.
- **Propuesta,** para la ronda única de arreglos (una línea): `estimated` pone `estimateNoMemory = false`, o `canConfirm` añade `&& !estimateNoMemory`. El test pasaría a afirmar lo seguro.
- **CVSS 3.1:** 0,0 (no alcanzable).

### I-2 [Info] `rt29_` afirma solo la cota superior
`assertTrue(encodings <= 8L * 20)` también pasa con 0, por ejemplo si se quita el contador. Lo compensa la aserción de que todas las versiones se recortaron, y este informe añade la cuenta exacta (`historyEncodings == min(k, 20)`, `resistsFittedHistory…`). Propuesta, menor: `assertEquals(8L * 20, encodings)`.

### I-3 [Info] `rt28_` mantiene una aserción de tiempo
`tTags <= 2 * tChips`, con la mediana de 5, dio ×0,95, ×1,00 y ×0,97 en las tres ejecuciones de hoy: la holgura es amplia. Aun así, corre en la CI de `main`, en otra máquina. Si alguna vez falla allí, se pasa a un recuento, como se hizo con `rt29_`.

## Lo que resiste
- **Fila 63:**
  - `resistsIsVaultTagAtItsBorders`;
  - `resistsTheThreeGatesSayTheSameWithEachTagAndDate`, con 20 copias × 3 puertas;
  - `resistsAV4CopyWithoutATagAtTheLimitIsCountedWithItsTagByTheThreeGates`;
  - y los `informative63_` y `VaultTagRuleTest` de la ronda, en verde.
- **RT-27:**
  - `resistsAPlanWithoutMemoryOnlyForTheRunStillReading`;
  - `resistsAnEstimateWithoutMemoryKeepsImportOffAndAStaleOneIsDropped`;
  - `resistsTheWrappersLetACancelOutAndTheScreenRoutesNullToNoMemory`.
- **RT-28:** `resistsAStaleResultNeverPrunesMoreThanTheRuleOfBefore`.
- **RT-29:** `resistsFittedHistoryIsExactAtItsBordersAndEncodesEachVersionOnce`.
- **«Salud»:**
  - `resistsNoRequestGoesAfterTheCancelOfALeakCheck`;
  - `resistsNoCancellationExceptionIsSwallowedBySalud`.
- **Regresiones:**
  - RT-21 a RT-26, en `RedTeam20RecheckTest`, `RedTeam20VaultsTest`, `RedTeam20CopyTest` y `ui/redteam/RedTeam20WifiQrTest`, con los demás de `redteam/`;
  - `RedTeam20ListCostTest`;
  - `RedTeam20CheckAsImportTest`;
  - todos en verde en las dos suites, sin cambios en sus tests en la ronda 3.

## Revisión experto-seguridad-cripto
- **Fila 63 (CWE-436, R5):** las tres puertas comprueban los mismos datos, ya con la etiqueta generada para una v4 sin ella (`Vault.kt:636`), y con la regla de lista blanca (`Vault.kt:373`, `BackupCheck.kt:292`).
  - «Importar como bóveda nueva» comprueba antes de cambiar la etiqueta y la fecha (`VaultStore.kt`, `prepare`), y lo sellado nunca ocupa más que lo comprobado (`RedTeam20Recheck3Test`, el borde de la v4 sin etiqueta).
  - LIBRE-1 intacto: ningún formato publicado lleva `vaultTag`.
- **RT-27 (CWE-400, CWE-770):** la captura es solo de `OutOfMemoryError` (`ImportMemory.kt:20`, `:34`). Las cancelaciones salen tal cual, y la sesión nunca pasa a guardar desde `planNoMemory` ni desde `estimateNoMemory` por la pantalla. I-1 es un endurecimiento de la sesión, no una vulnerabilidad.
- **RT-28 (CWE-407):** O(F + N) en `Default` (`EntryQuery.kt:152-156`). El efecto en `Main` es O(1) en el caso normal y O(F) si algo falta (`VaultList.kt:409-413`).
- **RT-29 (CWE-407):** lineal y exacto (`VaultLimits.kt:113-138`). Decide qué versiones se borran, y la referencia del diferencial es el código de antes, comprobado.
- **«Salud»:** con `NO_MEMORY` no sale ninguna petición a HIBP después de la cancelación (`HealthScreen.kt`, la rama `noMemory`; `PwnedPasswords.kt:34`, `withContext(Dispatchers.IO)` es un punto de cancelación inmediato). El prefijo k-anónimo no cambia.
- **Sin cambios:** ninguna primitiva, clave, formato, dependencia, permiso ni host. Los tests usan solo datos inventados y no salen a la red real (MockWebServer en local).

## Revisión Ingeniero Móvil Senior
- **Concurrencia:**
  - el estimado de la importación corre en `collectLatest`, que cancela el anterior (`ImportScreen.kt:207-223`), y por eso I-1 no es alcanzable;
  - `produceState` de «Salud» corta su cálculo en `ensureActive` entre entradas;
  - la comprobación de filtraciones se cancela en `withContext(IO)`.
- **Ciclo de vida:**
  - `LaunchedEffect(Unit) { job?.cancel() }` en la rama `noMemory` y `DisposableEffect` al salir;
  - ningún estado guardado nuevo, ni `rememberSaveable`.
- **Rendimiento:**
  - `rt28_`: ×0,95 a ×1,00;
  - `rt29_`: 20 codificaciones por entrada, y en tiempo de ×2,2 a ×3,7, informativo.
- **Pruebas:**
  - deterministas (recuentos, latches y resultados exactos), sin `sleep`;
  - los 11 tests nuevos tardan unos 8 s, sobre todo las dos copias de 24 MiB del borde de la fila 63;
  - corren en las dos suites, sin nada de `ui/`.

## Textos para el Abogado Digital (revisión final)
- Ninguno nuevo. `IMPORT_NO_MEMORY` ya está apuntado por la ronda 3, y su «No se ha cambiado nada» es cierto (ver RT-27).
