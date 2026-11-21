# Director: ronda 3 de los arreglos del ataque (RT-27, RT-28, RT-29, fila 63 y «Salud»), 19-11-2026

**Veredicto:** los tres hallazgos son reales y su severidad es correcta: Baja, CVSS 2,5. Recalculé el vector `AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L` y da 2,46, que se redondea a 2,5. Por proceso, un hallazgo de seguridad confirmado cuenta como Important, así que los tres se arreglan, no se aceptan.
- **Fila 63:** se aplica ya la regla de los 32 hex. Sola no cierra los dos sentidos: también hay que mover la comprobación de «Importar como bóveda nueva».
- **El informativo de «Salud»:** es un menor y se arregla.
- **R69:** no se extiende. Su tope era el de otro lector (16 MB, árbol de 48 MiB). Para la parte de RT-29 que no se puede cancelar hay una aceptación propia, con nombre.
- **Los tests del atacante:** `rt29_` pasa a verde sin tocarlo. `rt27_` y `rt28_` se reescriben, autorizados por mí. `rt28_` no puede pasar arreglando la app.

No ejecuté nada. Las cifras son del atacante, y las que doy para después del arreglo son cuentas mías.

## 1. Verificación de cada hallazgo

**RT-27: real.**
- `planMerge` va sin captura de `OutOfMemoryError`: `ui/screens/ImportScreen.kt:319`, dentro de `decode`, lanzado en un `viewModelScope.launch`. La única captura de `decode` envuelve solo a `readImport` (`:305-311`).
- `previewEstimate` (`:212-219`) captura `Exception`, y un `OutOfMemoryError` no lo es.
- La propuesta del atacante para el estimado mueve el cierre a otro sitio:
  - `PreviewEstimate(0L, null, 0)` deja `overBytes == 0`, y con eso `canConfirm` da verdadero (`imports/ImportSession.kt:297-301`);
  - «Importar» haría entonces la copia y `applyImport`, que no captura el `Error` (`VaultStore.kt:850-880` solo captura `Exception`).
- Además, un plan nulo ya significa «bloqueada» y se descarta sin decir nada (`ImportSession.kt:233-236`), así que la falta de memoria necesita su propio camino.
- Ningún texto que ya existe es verdad aquí: ni `LIST_NO_MEMORY` (`vault/Vault.kt:300`), ni `COPY_NO_MEMORY`, ni `IMPORT_JSON_TOO_MANY_VALUES`. Hace falta uno nuevo.
- **El test** demuestra que falta la captura, porque lee el texto de la fuente. No demuestra que la app se cierre. Los 137,9 MB son bytes reservados, no el pico. Basta con el criterio (c) ampliado, como en la fila 59.

**RT-28: real.**
- La línea `f.tags.filter { it in result.tags }` (`ui/screens/VaultList.kt:405`) corre en el hilo principal, dentro de `LaunchedEffect(result)` (`:402`).
- `result.tags` es una `List` (`:340`) y `ListResult` no es una `data class` (`:182`), así que el efecto vuelve a correr con cada resultado.
- Las etiquetas de una vista solo tienen el tope de `COPY_LIST_MAX`.
- **El test no prueba la app:**
  - afirma que las líneas vulnerables siguen en la fuente (`RedTeam20Recheck2Test.kt:561-563`);
  - mide una expresión de `List` frente a una de `HashSet` escritas dentro del propio test, así que el ×5 835 no cambia nunca;
  - nunca puede pasar a verde, y por eso se reescribe.
- Los 7 s son una extrapolación sin medir. El hallazgo descansa en que es F × N en el hilo principal.

**RT-29: real.**
- `fittedHistory` vuelve a codificar toda la historia en cada vuelta (`vault/VaultLimits.kt:113-116`): Σ1..20 = 210 codificaciones por entrada.
- `upgraded` hace una versión con la entrada entera, notas incluidas, por cada contraseña antigua (`Vault.kt:341-352`).
- Lo corren `readAsImported` (`Vault.kt:614-619`) y, con eso, «Importar copia» y «Importar como bóveda nueva», dentro de `KdfLock.derive` y del mutex (`VaultStore.kt:1509-1521`), y «Comprobar» (`BackupCheck.kt:103`).
- Hay un techo: `HISTORY_MAX_VERSIONS` = 20. El factor está acotado en unas 10,5 veces, no crece sin límite.
- **El test** llama al código real y mide lo que dice.

**Fila 63: el atacante tiene razón, y le falta un caso.**
- `appJson` no escribe los valores por defecto (`core/Json.kt:6-10`). Por eso `lastCheckedBackupAt = 0L` (`VaultStore.kt:1524`) encoge los datos unos 35 B, hasta unos 43 B.
- La regla de los 32 hex cierra el sentido de la etiqueta. No cierra el de la fecha: una copia con etiqueta válida y fecha de comprobación, unos bytes por encima, la rechaza «Comprobar» y la importa «Importar como bóveda nueva».
- Los cuatro tests construyen copias sin fecha, así que no lo ven.
- **Comprobado** que la regla no rompe nada publicado:
  - `origin/main` y `a3ac942` (la 1.4.3) tienen `VERSION = 3` y ningún `vaultTag`;
  - las versiones 2 y 3 guardan una lista de entradas (`Vault.kt:449`);
  - la app solo escribe `newVaultTag`, que son 16 bytes con `"%02x"`: 32 hex en minúsculas (`Vault.kt:334`, `:351`, `:354`, `:358`, `:372`, `:617`; `VaultStore.kt:1524`);
  - la v4 no está publicada, y el APK de prueba nunca va al móvil (decisión del dueño del 16-11);
  - los generadores de los tests usan `"a"`, `"b"` o `"0".repeat(32)`, que pasan.

**«Salud»: real.**
- La rama `noMemory` (`HealthScreen.kt:138-141`) vuelve antes de la barra y de «Cancelar» (`:198`).
- `produceState` depende de `breached` (`:134`), y `healthView` no tiene puntos de cancelación (`health/HealthView.kt:27-34`, `health/Health.kt:41-71`).
- La decisión (a) pide que el trabajo largo se pueda cancelar, y aquí no se puede. Es un menor y se arregla.

## 2. Decisiones

**RT-27: se arregla.**
1. **Dos envoltorios en `imports/`, como `healthView`.** El archivo nuevo es `imports/ImportMemory.kt`:
   - la costura `@Volatile internal var beforeImportWork: () -> Unit = {}`;
   - `planMergeOrNoMemory(read, existing, revision): MergePlan?`;
   - `previewEstimateOrNoMemory(…): PreviewEstimate?`.

   Cada uno es `try { beforeImportWork(); … } catch (e: OutOfMemoryError) { null }`. R11b y R11c se amplían solo a estas dos llamadas.
2. **`ImportScreen.kt`** llama solo a los envoltorios:
   - si la instantánea es nula, sigue `onPlan(run, null, …)`, que es el bloqueo;
   - si el plan es nulo con instantánea, `session.planNoMemory(run)`;
   - si el estimado es nulo, `session.estimateNoMemory(plan, default, overrides)`;
   - el `catch (e: Exception)` del estimado se queda como está.
3. **`ImportSession`:**
   - `planNoMemory(run)`: solo si `isRun(run)` y el paso es `Reading`. Hace `dropAll()` y `error = IMPORT_NO_MEMORY`, y vuelve a «Elegir» con el texto, sin guardar nada.
   - `estimateNoMemory(…)`: solo con el mismo plan, las mismas opciones y el paso `Preview`. Pone `estimateNoMemory = true` y deja `overBytes` nulo, así que «Importar» queda desactivado.
   - `clearEstimate`, `onPlan` y `dropAll` lo borran. Cambiar una opción vuelve a intentarlo.
   - En la vista previa, la primera rama del `when` (`ImportScreen.kt:603-608`) pasa a ser `s.estimateNoMemory -> Text(IMPORT_NO_MEMORY, color = NegativeColor)`, en lugar de la rueda de «Calculando».
4. **Texto nuevo**, exacto, en `imports/ImportTexts.kt`:

   `IMPORT_NO_MEMORY` = «Este móvil no tiene memoria suficiente para preparar esta importación. No se ha cambiado nada. Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede hacer esta importación.»

   Se fija en `ErrorTextsTest` y en `ForbiddenWords`, con su fila en el inventario de la fase C. Va al Abogado.
5. **`applyImport` y la copia de antes de importar quedan como están.** Es un riesgo con nombre, de acuerdo con el Ruling de la re-comprobación 1: si el guardado se queda sin memoria, la app se cierra, no se escribe nada (el cambio de nombre es atómico, `VaultStore.kt:985`) y la copia se queda.

**RT-28: se arregla, fuera del hilo principal y sin guardar ningún conjunto de tamaño N.**
- Una función pura nueva en `vault/EntryQuery.kt`: `internal fun listTags(listable, filterTags: Set<String>): ListTags(sorted, goneFromFilter)`.
  - Hace el `HashSet` una sola vez, como hoy en `VaultList.kt:340`, y de él la lista ordenada y las etiquetas del filtro que ya no están. Es O(F + N).
  - No se queda con el conjunto.
- `compute` la usa. `ListResult` gana `goneFilterTags: Set<String> = emptySet()`.
- `LaunchedEffect(result)` pasa a ser `val tags = if (result.goneFilterTags.isEmpty()) f.tags else f.tags - result.goneFilterTags`. Nunca vuelve a mirar si algo está en `result.tags`.
- **No vale la opción 2 del atacante.** Guardar un `Set` de todas las etiquetas añade memoria que `shownCost` no mide (`RedTeam20ListCostTest.kt:74-78`), y el criterio de RT-25 se quedaría por debajo de la realidad.
- **Un resultado viejo no hace daño:**
  - una etiqueta que falta no puede volver desde las fichas, porque las fichas salen de `result.tags`;
  - si el filtro cambió mientras tanto, ya hay un cálculo en marcha para el nuevo.

**RT-29: se arregla la parte cuadrática. La que no se puede cancelar se acepta con nombre.**
- **`fittedHistory` mide cada versión una sola vez:**
  - con `jsonBytes(EntryVersion.serializer(), v)`, y el total es 2 + Σ + (k − 1);
  - primero, el recorte a 20 de hoy;
  - después, mientras quede alguna y el total pase el tope, se quita la más antigua con el mismo desempate de hoy (`minBy`, la primera entre iguales), restando su tamaño y una coma si quedaba más de una;
  - devuelve el mismo objeto si cabe;
  - `fittedWithSize` no cambia.
- **Test diferencial**, con el bucle viejo copiado en el test como referencia. `Random(20261119L)` y al menos 2 000 casos, que cubran:
  - de 0 a 25 versiones;
  - empates en `savedAt`;
  - totales a los dos lados de 64 KiB;
  - `"`, `\`, caracteres de control, U+2028 y texto no ASCII;
  - los archivos de `app/src/test/resources/seguridad/` pasados por `fitted()`: dan los mismos datos;
  - `===` cuando cabe.

  Cualquier diferencia: se para y se consulta.
- **La parte que no se puede cancelar se acepta con nombre:** «la lectura de una copia no se corta a medias».
  - Qué es: descifrar, decodificar, subir a v4, ajustar y comprobar. En «Importar copia» y en «Importar como bóveda nueva» va dentro de `KdfLock.derive` y del mutex.
  - Su tope: el de la copia (33 MiB por archivo, `COPY_TREE_MAX_BYTES` y 24 MiB + 4 KiB de datos) y, tras el arreglo, lo medido. Por mi cuenta, unas 2 veces la pareja; el umbral para consultar es 3.
  - Por qué: la decodificación es un solo `decodeFromString`. Un `ensureActive` dentro de la subida solo acortaría la cola, y al salir `withContext` ya lanza la cancelación, así que nunca se entrega un resultado tardío.
  - Coste si me equivoco: unos segundos de espera en un solapamiento raro.

**Fila 63: se aplica ya.**
- **La regla:** `checkCopyStructure` (`BackupCheck.kt:275-284`) rechaza con `COPY_DAMAGED` un `vaultTag` no vacío que no tenga exactamente 32 caracteres ASCII de `0-9a-f`.
  - Va en `isVaultTag`, junto a `newVaultTag`.
  - Los tests: 1 000 salidas de `newVaultTag` pasan, y estas fallan: mayúsculas, 31 y 33 caracteres, `'٣'` y la `'ａ'` de ancho completo.
- **«Importar como bóveda nueva»** (`VaultStore.kt:1524`) pasa `checkCopyData` y la comprobación de los adjuntos sobre `read`, y después cambia la etiqueta y la fecha.
  - Esto sustituye a la frase de RT-21a de `:1517-1519`, que hay que poner al día.
  - Los datos que se sellan pasan igual: la etiqueta nueva es válida y del mismo tamaño, y una fecha a 0 no se escribe.
  - Test: los datos sellados ocupan como mucho lo comprobado, y pasan `checkCopyData`.
- **Los cuatro `informative63_`** cambian, y las tres puertas («Comprobar», «Importar copia» y «Importar como bóveda nueva») dan lo mismo:

  | Caso | Resultado en las tres |
  |---|---|
  | Etiqueta «a» | `COPY_DAMAGED` |
  | Etiqueta de 64 «b» | `COPY_DAMAGED` |
  | Nuevo: 32 hex con fecha, en el límite + 20 contando la fecha | `COPY_DAMAGED` |
  | Nuevo: 32 hex con fecha, justo en el límite | OK; la bóveda nueva lleva etiqueta propia y ninguna fecha |

## 3. «Salud»: un menor que se arregla
- **(a)** En la rama `noMemory` (`HealthScreen.kt:138-141`), `LaunchedEffect(Unit) { job?.cancel() }`.
  - Después de la petición en curso no sale ninguna más.
  - Las marcas parciales se quedan en `breached`.
  - No se llama a `dropResults`, que cambiaría la clave y volvería a calcular.
- **(b)** `vaultHealth` gana `active: () -> Unit = {}`, llamado entre entradas (al menos cada 256) y antes de `missions`.
  - `healthView` lo pasa, y `HealthScreen` le da `{ job.ensureActive() }` del bloque `withContext`.
  - `healthView` sigue capturando solo `OutOfMemoryError`.
  - La lista no cambia: usa el valor por defecto.

## 4. Encargo de la ronda 3 (para pegar)

---
**Ronda 3 de los arreglos del ataque final de Clavero 2.0: fila 63, RT-29, RT-27, RT-28 y «Salud»**

**Antes de empezar**
- **Quién:** el implementador de la ronda 2. Si no se le puede retomar, uno nuevo con este encargo y `redteam-fixes-2-report.md`.
- **Lee primero:**
  - `CLAUDE.md`;
  - `experto-seguridad-cripto`: `SKILL.md`, `references/auditoria.md` y `references/cripto-moderna.md`;
  - `desarrollo-movil-senior` y `seguridad-criptografia-movil`;
  - este Ruling, que el controlador guarda en `sdd-2.0/advisor-t12-redteam-fixes-3.md`;
  - `sdd-2.0/redteam-recheck-2.md` y `RedTeam20Recheck2Test.kt`;
  - R69 (`sdd-1.4.0/progress.md:309`), R11b y R11c, y la decisión (a).
- **Dónde:** en `/home/user/Seguridad`, rama `ccr-0aa27f95-u038cd`, desde el commit que guarde este Ruling. Nadie más toca `app/src/main` hasta que cierre la re-comprobación 3 (R32).
- **Gradle:**
  - siempre `flock /home/user/.gradle-run.lock ./gradlew … --no-daemon --max-workers=1`;
  - se itera en `-p jvm-check test --tests …`;
  - lo de `ui/`, solo en la app, con `export ANDROID_HOME=/home/user/android-sdk`, `:app:testDebugUnitTest --tests …` y después `./gradlew --stop`;
  - nunca las suites enteras ni dos Gradle a la vez.
- **Tests:**
  - ningún `OutOfMemoryError` de verdad: se construye a mano con una costura;
  - cada test, menos de 10 s, sin `sleep` y con `Random(20261119L)`.

**TDD**
- Se quita el `@Ignore` de `rt27_`, `rt28_` y `rt29_`, se enseña su rojo en el informe y se arregla.
- `rt29_` pasa a verde sin cambios.
- **Cambios autorizados en los tests**, y ningún otro:
  - **`rt27_`:** se reescribe. Conserva su fila de memoria y comprueba:
    - con la costura, que los dos envoltorios dan nulo y que la sesión queda en `IMPORT_NO_MEMORY` con «Importar» desactivado;
    - un ancla de fuente: `ui/screens/ImportScreen.kt` no tiene ningún `planMerge(` ni `previewEstimate(` suelto, fuera de los comentarios, y llama una vez a cada envoltorio.
  - **`rt28_`:** se reescribe, y se enseña antes su rojo. Comprueba:
    - que la copia sigue dando «OK»;
    - que `listTags` con N = 200 000 y la vista de 10 000 da `gone` vacío y tarda como mucho 2 veces `flatMapTo(HashSet()).sorted()` solo;
    - que con 3 etiquetas quitadas de la bóveda, `gone` son exactamente esas 3;
    - un ancla: en `VaultList.kt` no queda ningún `in result.tags`, y `LaunchedEffect(result)` lee `goneFilterTags`, con un mínimo de coincidencias.
  - **Los cuatro `informative63_`:** como en el punto 2.
  - **`informativeSaludWithoutMemoryHidesTheCancelOfARunningCheck`:** se invierte.
  - **`HealthScreenTest.outOfMemoryShowsTheTextBeforeAnythingElse`:** corta la rama en la primera `}`; se ajusta.
  - **`HealthScreenTest.vaultHealthRunsOnceOffTheMainThread`:** sigue en verde. Se mantienen una sola llamada a `healthView(`, el `withContext(Dispatchers.Default)` a 4 líneas o menos y las claves de `produceState`.

**Commits**
- Identidad: `git -c user.name=Egt17baa -c user.email=Egt17baa@users.noreply.github.com commit`.
- Asunto: «2.0, arreglos del ataque (ronda 3): …».
- Las dos líneas de atribución, como en `40482b4`.
- Ningún nombre de modelo ni dato del dueño. **Sin push.**

**Orden.** Cada punto es un commit, con la puerta `redteam.*` en verde y el «Estado» del informe al día.
1. **Fila 63:**
   - `isVaultTag` y la regla en `checkCopyStructure`;
   - la comprobación de «Importar como bóveda nueva» antes del cambio, con su KDoc;
   - los tests del punto 2.

   Antes, se corren `BackupCheckTest`, `BackupV3Test`, `BackupTest`, `VaultV4Test`, `AppContainerVaultsTest`, `NoMemoryTest` y `RedTeam20CheckAsImportTest`.
2. **RT-29:**
   - `fittedHistory` lineal, con el test diferencial;
   - `rt29_` en verde;
   - las filas de `rt29Rows_` antes y después en el informe;
   - la aceptación con nombre en el KDoc de `importReplace` y de `checkBackup`.
3. **RT-27:** los envoltorios y la costura, `planNoMemory` y `estimateNoMemory`, `IMPORT_NO_MEMORY` y su sitio en la vista previa. Tests en `ImportStateTest`, que corre en jvm-check:
   - no queda nada guardado;
   - un bloqueo o un «Cancelar» mientras tanto no enseña el texto;
   - un estimado viejo no marca el actual;
   - cambiar una opción vuelve a intentarlo.

   Más `rt27_`.
4. **RT-28:** `listTags`, `goneFilterTags` y el efecto, más `rt28_` y una prueba de `EntryListViewModel` en la app:
   - con la vista entera, `gone` vacío;
   - después de quitar 3 etiquetas, el filtro pierde solo esas 3.

   `RedTeam20ListCostTest` queda en verde, con las mismas cifras.
5. **«Salud»:** (a) y (b) del punto 3. Tests en jvm-check:
   - una `active` que lanza `CancellationException` en la llamada k se propaga y no da `NO_MEMORY`;
   - `vaultHealth` la llama al menos entradas/256 veces.

   Más los tests de fuente autorizados.
6. **Cierre:**
   - Se corren por separado las clases de arriba, `ErrorTextsTest`, `ImportTextsTest`, `HealthViewTest`, `HealthScreenTest`, `EntryListNoMemoryTest` y `redteam.*`, en cada suite.
   - Informe `redteam-fixes-3-report.md`, en `.superpowers/sdd/2026-11-12-clavero-2.0/` y en `docs/superpowers/handoff/sdd-2.0/`, con:
     - una tabla con el rojo, el verde y cada test;
     - las cifras de RT-29 antes y después;
     - «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior», con `archivo:línea`;
     - «Textos para el Abogado Digital (revisión final)»: `IMPORT_NO_MEMORY`.
   - `pendientes.md`:

     | Fila | Contenido | Estado |
     |---|---|---|
     | 63 | — | Hecho |
     | 64 | RT-27; el móvil, con la 58 | Hecho |
     | 65 | RT-28; el móvil: aplicar la vista y escribir | Hecho |
     | 66 | RT-29 | Hecho |
     | 67 | Riesgo con nombre: «la lectura de una copia no se corta a medias», con las cifras medidas | Aceptado / móvil |
     | 68 | «Salud» | Hecho |
     | 69 | Riesgo con nombre: el guardado de «Importar de otra app» sin captura | Aceptado, con las 55 y 58 |
     | 70 | Informativo 2 de la re-comprobación 2: el test de fuente sigue siendo textual. No se cambia el almacén, porque rompería `newVault` | Aceptado |
     | 62 | Se le añade el informativo 4: los títulos y las etiquetas de megas de U+200B | Móvil |

**Se para y consulta si:**
- el test diferencial encuentra una diferencia;
- `rt29_` pasa de 3 veces después del arreglo;
- algún archivo de prueba o test de copias de la 1.x o de la app falla con la regla de los 32 hex, fuera de los autorizados;
- `RedTeam20ListCostTest` se mueve: B por encima de 3,5 MiB, D por encima de 1,30 o un margen más de ±0,02;
- algo toca los formatos, `appJson`, el orden de las comprobaciones (salvo el cambio de «Importar como bóveda nueva») o la estructura de `KdfLock` y del mutex;
- un test fuera de la lista autorizada se pone en rojo.

**Nunca** rompe `SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup` ni lo que escribieron la 1.0 a la 1.4.x (LIBRE-1). Sin dependencias, permisos ni hosts nuevos.

---

## 5. Qué ataca la re-comprobación 3
El atacante pone su *worktree* al día con `git -C /home/user/Seguridad-rt merge --ff-only ccr-0aa27f95-u038cd` y escribe `redteam-recheck-3.md`, con los tests en `RedTeam20Recheck3Test.kt`.

1. **RT-27:**
   - más trabajo de toda la bóveda sin captura en la importación, como `TagBudget`, o lo que la vista previa calcula en el hilo principal;
   - la máquina de estados: un bloqueo, un «Cancelar» u otro archivo mientras se estima; un estimado viejo; «Importar» nunca activo con `estimateNoMemory`;
   - la veracidad de `IMPORT_NO_MEMORY`.
2. **RT-28:**
   - otros F × N en el hilo principal: `isOn` por cada vista, `selectionFilterKey`, `plain`, las fichas, «Organizar» con vistas y colores;
   - un resultado viejo que poda el filtro de ahora.
3. **RT-29:**
   - la exactitud de `fittedHistory`: empates, escapes, más de 20 versiones, el borde de 64 KiB;
   - otros bucles cuadráticos: `fitted`, `fittedForSave`, `withPlainVersions`, `sortedForShow`, `withVersion`, y `upgraded` con 200 000 contraseñas antiguas (la CPU);
   - las cifras de la aceptación con nombre: cuánto espera una derivación.
4. **Fila 63:**
   - los bordes de la regla, con dígitos no ASCII, mayúsculas, 31 y 33 caracteres, y una v4 sin etiqueta;
   - un test diferencial de las tres puertas con fechas y etiquetas;
   - que lo sellado nunca ocupe más que lo comprobado.
5. **«Salud»:**
   - que no salga ninguna petición después de `NO_MEMORY`;
   - que no se trague ninguna `CancellationException`;
   - que no haya varios cálculos a la vez cuando llegan filtraciones seguidas.
6. **Regresiones:** RT-21 a RT-26, `RedTeam20ListCostTest` y `RedTeam20CheckAsImportTest`.

Un hallazgo nuevo abre la ronda 4; con 5, decide el dueño. Al cerrar el ataque:
- el clon limpio, sin `@Ignore`;
- el informe publicado, `docs/auditorias/2026-11-17-ataque-2.0.md`;
- `code-review` alto.

## Riesgos o dudas
- **Nadie ha medido RT-29 después del arreglo.** Las «2 veces» salen de cuentas mías sobre los tiempos del atacante (unos 0,27 ms por codificación). Por eso el umbral para consultar es 3.
- **La regla de los 32 hex rechaza algo nuevo al final del ciclo.** No toca nada publicado, pero si la v4 llegó a algún sitio fuera de los tests, lo diría la condición de parada.
- **Hay que reescribir dos tests del atacante** (`rt27_` y `rt28_`). Los vuelve a comprobar el mismo atacante.
- **Quedan con nombre:** el guardado de una importación sin captura (fila 69) y la lectura de una copia que no se corta a medias (fila 67).
- **No abrí:** `MergePlan.kt:641-668` (el `Matcher`), `RedTeam20CheckAsImportTest` entero ni `ImportStateTest`. Si algún test de ahí depende de que «Importar como bóveda nueva» compruebe después del cambio, salta la condición de parada.
- **Los tests de tiempo corren también en la CI de `main`,** en otra máquina. Las holguras: `rt29_` ≤ 5 frente a unas 2 calculadas, y `rt28_` ≤ 2×.

## Revisión experto-seguridad-cripto
- **RT-27** (CWE-400 y CWE-770, decisión (a)):
  - la última defensa va donde falta (`ImportScreen.kt:212-219`, `:319`);
  - «Importar» queda desactivado si falta memoria, así que no pasa a un guardado sin captura (`ImportSession.kt:297-301`).
  - Hay un texto nuevo, cierto, que no dice «Contraseña incorrecta» (regla de veracidad).
- **RT-28** (CWE-407): de O(F × N) en el hilo principal (`VaultList.kt:405`) a O(F + N) en `Default`, sin guardar más memoria, así que el criterio de RT-25 sigue midiendo la realidad (`RedTeam20ListCostTest.kt:74-78`).
- **RT-29** (CWE-407): de lo cuadrático acotado (`VaultLimits.kt:113-116`) a lo lineal.
  - Como esa función decide qué versiones se borran, el test diferencial es lo que hace el cambio seguro para los datos.
  - La parte que no se puede cancelar se acepta con su propio tope, no estirando R69.
- **Fila 63** (CWE-436 entre puertas, R5): las tres comprueban los mismos datos.
  - La regla solo admite lo que escribe la app (`Vault.kt:334`, `:354`).
  - Ningún formato publicado tiene `vaultTag` (`origin/main` y `a3ac942` con `VERSION = 3`; `Vault.kt:449`).
- **Sin cambios de base:** ninguna primitiva, clave, formato, dependencia ni host nuevo. La decisión (b) se cumple: una sola lectura, y el cambio de la etiqueta y la fecha es una función de lo ya comprobado, dentro de la misma sección crítica.

## Revisión Ingeniero Móvil Senior
- **RT-27:** un `Error` dentro de `viewModelScope.launch` cierra el proceso.
  - Los envoltorios capturan dentro del bloque de `Default`.
  - Las transiciones de `ImportSession` se prueban en jvm-check, porque `ImportViewModel` es privado y pide un `Context` (`ImportScreen.kt:182-188`).
  - La rueda de «Calculando» no se queda girando para siempre (`:603-608`).
- **RT-28:**
  - el efecto de Compose en `Main` hace O(1) en el caso normal;
  - la poda se calcula en `compute`, que ya corre en `Default` (`VaultList.kt:302-310`);
  - no hay estado guardado nuevo;
  - `LaunchedEffect(result)` sigue vigilando las carpetas que desaparecen.
- **«Salud»:**
  - `produceState` cancela su corrutina cuando cambia una clave, pero un bucle sin puntos de cancelación sigue hasta el final; `active` lo arregla;
  - parar la comprobación desde un `LaunchedEffect` dentro de la rama es correcto con el ciclo de vida, y `DisposableEffect` (`HealthScreen.kt:91`) la sigue parando al salir.
- **Pruebas:**
  - `redteam/` y `imports/` corren en las dos suites, y `ui/` solo en la app (`jvm-check/build.gradle.kts:20-22`);
  - siempre con `flock` y una ejecución a la vez.

Modelo: el director.