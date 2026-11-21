# Clavero 2.0: re-comprobación 2 del equipo rojo (arreglos de la ronda 2, decisión (f))

## Estado

- **Hecha.** Base: `5ebdd85` (el clon limpio tras M-1 a M-8), en el *worktree* `/home/user/Seguridad-rt`, rama `rt-2.0`.
- **Veredictos:**
  - RT-25 (partes a y b), RT-26, los informativos 1 a 3, los puntos 8 a 10 y M-1 a M-8: **cerrados**;
  - la fila 63: **informativo, como decidió el director**, pero con un sentido más que el director no listó (una etiqueta larga encoge sin tope);
  - los ataques de la lista final encuentran **tres hallazgos nuevos**, los tres Baja: **RT-27**, **RT-28** y **RT-29**.
- **Abren otra ronda,** la 3 (encargo del ataque, `advisor-t12-attack.md:199`: «Un hallazgo nuevo abre otra ronda. Con 5 rondas, decide el dueño»).
- **Último commit:** «2.0, ataque: re-comprobación 2 de los arreglos (RT-27 a RT-29)».
- **Criterio:** el de `advisor-t12-redteam-fixes-2-criterio.md`, «Para el encargo de la re-comprobación 2». La lista que manda es la de `advisor-t12-redteam-fixes-2-cierre-ok.md`.
- **Ejecuciones (19-11-2026, con `flock`, de una en una):**
  - base, antes de escribir nada: `jvm-check`, `redteam.*`: 122 tests, 0 fallos;
  - `jvm-check`, `--tests 'app.seguridad.redteam.*'`: verde, 136 tests, 3 ignorados (RT-27, RT-28 y RT-29), 0 fallos;
  - en la misma ejecución, las clases de los arreglos fuera de `redteam/`: `NoMemoryTest` (8), `EmptySlotGuardSourceTest` (4), `PlatformSourcesTest` (47) y `HealthViewTest` (2), todas en verde;
  - la app, `:app:testDebugUnitTest --tests 'app.seguridad.redteam.*' --tests 'app.seguridad.ui.redteam.*'`: verde, 139 tests, 3 ignorados (RT-27, RT-28 y RT-29), 0 fallos; en la misma ejecución, `EntryListNoMemoryTest` (4), `EntryFieldsTest` (3) y `HealthScreenTest` (3), en verde; después `./gradlew --stop`.

## Resumen

| RT o punto | Arreglo | Veredicto | Severidad |
|---|---|---|---|
| RT-25, parte a | La captura de `OutOfMemoryError` en `EntryListViewModel.compute` y `LIST_NO_MEMORY` (`b632779`) | Cerrado | — |
| RT-25, parte b | `copyExtraTags` y su tope de 10 000 sobrantes (`655917d`); el criterio redefinido (`e587d90`) | Cerrado. La longitud de las etiquetas resiste (ataque 4) | — |
| RT-26 | `escapedName` y `COPY_DAMAGED` (`bd0c31a`) | Cerrado | — |
| Informativo 1 | El test de fuente del guardián y el valor por defecto `{ false }` (`78d9708`, endurecido en el punto 10 y en M-5) | Cerrado. Ningún envoltorio fuera de `ui/` llega a un hueco sin la regla; el test de fuente sigue siendo textual (informativo 2) | — |
| Informativo 2 | El nombre de la red con sus marcas en la ficha (`7706c47`) | Cerrado | — |
| Informativo 3 | `copyRead` alrededor de cada lectura de una copia (`0ec7c14`) | Cerrado | — |
| Punto 8 | `readAsImported` y `checkCopyData`, compartidos (`6ede139`) | Cerrado, salvo la fila 63 (informativo 1) | — |
| Punto 9 | `healthView` con su captura; `withTagColor` con tope (`ae9028a`) | Cerrado. Lo que «Salud» reserva en el hilo principal resiste; el «Cancelar» perdido es informativo 3 | — |
| Punto 10 | `produceState<Boolean?>(null, …)` y el test de fuente endurecido | Cerrado | — |
| M-1 a M-8 | `a783a42` | Cerrados | — |
| **RT-27** (nuevo) | — | Abierto. «Importar de otra app» calcula sobre toda la bóveda sin capturar `OutOfMemoryError`: `planMerge` y `previewEstimate` | **Baja**: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L`), MASVS-CODE-4 |
| **RT-28** (nuevo) | — | Abierto. Una vista de una copia con miles de etiquetas hace que el hilo principal compare cada una con todas las de la bóveda en cada resultado de la lista: segundos de bloqueo por letra escrita | **Baja**: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L`), MASVS-CODE-4 |
| **RT-29** (nuevo) | — | Abierto. Subir una copia de 1.x con notas grandes y contraseñas antiguas cuesta de 10 a 14 veces su lectura, sin poder cancelarse, y en «Importar» con el cerrojo de las derivaciones | **Baja**: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L`), MASVS-CODE-4 |

## Hallazgos nuevos

### RT-27 [Baja] «Importar de otra app» calcula sobre toda la bóveda sin la captura

**Cómo lo he atacado.** Es el ataque 6 de la lista, juzgado con el criterio (c) ampliado como para «Salud». En `ui/`, dos pasadas:
- cada `withContext(Dispatchers.Default)`, mirando si recorre la bóveda entera y si captura `OutOfMemoryError`;
- las operaciones de colección sobre la bóveda en el hilo principal (`flatMap`, `associateBy`, `groupBy`, `sorted`, `filter`, `in`).

Lo demás no reserva del orden de la bóveda:
- `ContactAddScreen.kt:162`, `ContactDetailScreen.kt:85`, `IdentityScreen.kt:179` y `PkActionsScreen.kt` leen una tarjeta o un contacto;
- `CodesScreen.kt:121` calcula una constante;
- `VaultOrganize.kt:204` cuenta por carpeta;
- `ContactsScreen.kt:198` cuenta los contactos.

La segunda pasada dio RT-28.

**Lo que pasa:**
- `ImportViewModel.decode` llama a `planMerge(read, it.entries, it.revision)` dentro de `withContext(Dispatchers.Default)` (`ui/screens/ImportScreen.kt:319`), en un `viewModelScope.launch` (`:290`) sin ninguna captura. La única captura de `decode` envuelve solo a `readImport` (`:306-310`, R11c), y se cierra antes.
- `planMerge` construye un `Matcher` de cada entrada viva (las claves de títulos, usuarios y hosts, `imports/MergePlan.kt:641-668`) y `liveTags`, el conjunto de todas las etiquetas vivas (`MergePlan.kt:147`, `:157-158`).
- La vista previa llama a `previewEstimate(…)` (`ImportScreen.kt:213`), que hace la bóveda fusionada entera (`mergeImport`, otra vez `liveTags`, `MergePlan.kt:278`). Su `try` captura `CancellationException` y `Exception` (`:214-219`), y un `OutOfMemoryError` es un `Error`.
- **Medido**, como orden de magnitud: con 20 000 accesos inventados, `planMerge` reserva 137,9 MB y el índice de la lista para la misma bóveda, 198,5 MB (0,69 veces). Son bytes reservados por el hilo, no el pico.
  - El `EntryListViewModel` de la lista sigue en la pila de navegación con su índice mientras se importa.
  - El hallazgo no descansa en esa cifra, sino en que falta la captura (criterio (c), ampliado como para «Salud»).

**Impacto:** el mismo de «Salud» antes del punto 9 (fila 59).
- Un `OutOfMemoryError` en esas dos llamadas sale del `viewModelScope` y cierra la app.
- No se pierde nada: el plan no ha cambiado la bóveda.
- Pasa cada vez que se entra en «Importar de otra app» con esa bóveda; la lista sigue funcionando.
- No hace falta ninguna copia hostil: basta una bóveda propia grande en un móvil con poca memoria. Por eso `AC:H`, `UI:R` y `A:L`.

**Arreglo propuesto:**
1. `planMerge` dentro de un `try { … } catch (e: OutOfMemoryError)`, como `readImport` (R11c). Con un texto fijo en lugar del plan: `LIST_NO_MEMORY` o uno propio, para el Abogado.
2. En `previewEstimate`, añadir `OutOfMemoryError` a la captura. El estimado vacío (`PreviewEstimate(0L, null, 0)`) ya dice «el guardado decide».
3. `applyImport` (la fusión dentro de `modifyData`) queda como está: es un guardado, y el Ruling de la re-comprobación 1 los aceptó sin captura.

**Test:** `RedTeam20Recheck2Test.rt27_theImportScreensWholeVaultWorkCatchesRunningOutOfMemory` (`app/src/test/java/app/seguridad/redteam/RedTeam20Recheck2Test.kt:497`).
- Mide `planMerge` frente al índice.
- Comprueba en la fuente que las dos llamadas están dentro de un `try` que captura `OutOfMemoryError`, `Error` o `Throwable`. Antes se asegura de que sí ve la captura que ya hay alrededor de `readImport`.
- Sin el `@Ignore`, hoy falla con:

```
RT-27: 20 000 logins: planMerge allocates 137871600 B, the list's index 198482088 B (x0.69)
java.lang.AssertionError: whole-vault work of «Importar de otra app» with no catch of OutOfMemoryError expected:<[]> but was:<[planMerge(read, it.entries, it.revision), previewEstimate(plan, default, overrides, existing, System.currentTimeMillis())]>
```

### RT-28 [Baja] Una vista con miles de etiquetas bloquea el hilo principal en cada resultado de la lista

**Cómo lo he atacado.** Visto al buscar más cálculos de toda la bóveda (ataque 6). El tope de RT-25 b cuenta las etiquetas de las entradas y de sus versiones, no las de las vistas. El filtro de una vista guardada solo tiene el tope de cualquier lista de una copia, `COPY_LIST_MAX` (10 000).

**Lo que pasa:**
- Al aplicar la vista (`EntryListViewModel.apply`, `ui/screens/VaultList.kt`), sus etiquetas pasan al filtro de la lista.
- En cada resultado nuevo de la lista `LaunchedEffect(result)` corre en el hilo principal (`VaultList.kt:402-408`). Ahí está `val tags = f.tags.filter { it in result.tags }.toSet()` (`:405`).
- Cada letra escrita en la búsqueda y cada guardado dan un resultado nuevo: `ListResult` es una clase normal, no `data class` (`:182`), así que cada resultado es un objeto distinto y el efecto se relanza.
  - Una vista exige todas sus etiquetas (`tags.all { it in e.tags }`, `vault/EntryQuery.kt:173`), así que con esta vista la lista sale vacía.
  - Aun así, cada letra da otro resultado y otra pasada.
- `result.tags` es una `List` ordenada de todas las etiquetas de la bóveda (`:340`), así que son F × N comparaciones.
- La copia del test pasa todas las comprobaciones de «Comprobar una copia» (`OK`):
  - 20 000 accesos con 10 etiquetas propias cada uno (sin exceso, N = 200 000), una forma que las pantallas pueden escribir;
  - una vista con las 10 000 últimas.
- **Medido** con la misma expresión y los mismos tipos (anclada a la fuente), con 1 000 de esas etiquetas: **723 ms** por resultado en el hilo principal, frente a 0,12 ms con un `HashSet` (**×5 835**).
- **Extrapolado** (lineal en F, sin medir): con las 10 000 de la vista, unos 7,2 s por resultado en la JVM de las pruebas, y más en un móvil. Android da «no responde» a los 5 s sin atender una pulsación.
- Si todas las etiquetas de la vista están en la bóveda, el filtro no cambia y el trabajo se repite en cada resultado.

**Impacto:**
- Con esa vista aplicada, la lista se congela segundos con cada letra y cada guardado, y puede salir «Clavero no responde».
- Se sale bloqueando, porque `reset` borra el filtro, o cerrando la app.
- Hace falta importar una copia hecha a mano (de quien conoce su maestra) y tocar la vista. No se pierde nada.

**Arreglo propuesto** (cualquiera de los dos):
1. Podar el filtro fuera del hilo principal, en `compute`, contra un conjunto (`HashSet`).
2. Que `ListResult` lleve las etiquetas también como `Set` y `LaunchedEffect` use `it in tagSet`.

En los dos, O(F + N). No propongo un tope a las etiquetas de una vista: la app no tiene ninguno y podría rechazar una copia propia.

**Test:** `RedTeam20Recheck2Test.rt28_aViewWithManyTagsDoesNotFreezeTheMainThread` (`RedTeam20Recheck2Test.kt:559`). Sin el `@Ignore`, hoy falla con:

```
RT-28: view of 1 000 tags against 200 000: 723.1579 ms on the main thread per result (x10 for a view of 10 000: 7231.579 ms); with a set 0.123928 ms (x5835)
java.lang.AssertionError: the main thread compares each tag of the view with every tag of the vault (x5835 a set)
```

### RT-29 [Baja] Subir una copia de 1.x con notas grandes cuesta diez veces su lectura y no se puede cancelar

**Cómo lo he atacado.** Es el ataque 2, CPU. Una copia de 1.x (v3) con accesos de 256 KiB de notas y 20 contraseñas antiguas cada uno.
- Lo mido con `VaultCrypto.readAsImported`, lo que corren «Importar copia», «Importar como bóveda nueva» y «Comprobar una copia».
- Lo comparo, en el mismo test, con la misma copia sin las contraseñas antiguas.

**Lo que pasa:**
- `upgraded` hace una versión por contraseña antigua con la entrada entera, notas incluidas (`vault/Vault.kt:342-352`).
- `fittedHistory` vuelve a medir toda la historia después de cada versión que quita (`vault/VaultLimits.kt:113-116`): 20 + 19 + … + 1 = 210 codificaciones de las notas por entrada.
- **Medido:**
  - con 8 entradas, la pareja tarda 49,7 ms y la hostil 501 ms (**×10,1**);
  - con 2 entradas, ×9,8 y ×14,4 en dos ejecuciones;
  - es lineal en las notas (×1,66 a ×2,01 al doblar).
- **Sin cancelación** (decisión (a)):
  - «Comprobar», cancelada justo al decodificar (`VaultCrypto.beforeDecode`), acaba de 87 a 144 ms después, y la lectura entera de la pareja dura de 17 a 21 ms;
  - en «Importar copia», una derivación pedida mientras tanto espera de 94 a 115 ms, porque la subida corre dentro de `KdfLock.derive` (`NonCancellable`) y con el mutex del almacén (`VaultStore.kt:1520`).
- **Extrapolado** (lineal, sin medir) a 24 MiB de notas, unas 12 veces la muestra de 8 entradas: unos 6 s en la JVM de las pruebas, y varias veces más en un móvil. Todo ese tiempo, ninguna bóveda se puede abrir y nada se puede cifrar con contraseña.
- Lo mismo pasa una vez, en el primer guardado, con una v4 hecha a mano con historias de más de 64 KiB (`fittedForSave`).

**Impacto:**
- Unos segundos (en el móvil, quizá decenas) en los que «Comprobar» no se puede parar.
- Con «Importar», todas las derivaciones de la app esperan.
- No se pierde nada y acaba solo. Hace falta una copia hecha a mano o una bóveda propia de la 1.x con notas muy grandes y contraseñas antiguas, así que `AC:H`, `UI:R` y `A:L`.
- R69 aceptó tener el cerrojo durante una lectura acotada de otro lector. Esto es unas 10 veces esa lectura; extender R69 le toca al director.

**Arreglo propuesto:**
1. Que `fittedHistory` mida cada versión una sola vez y vaya restando, como hace ya `fittedWithSize` con `freed`. Así queda en unas 20 codificaciones por entrada, lineal.
2. Un `ensureActive` entre entradas en «Comprobar», que ya corre fuera del cerrojo.

**Test:** `RedTeam20Recheck2Test.rt29_theUpgradeOfACopyOf1xCostsAboutWhatReadingItDoes`. Pide como mucho 5 veces la pareja. Las filas están en `rt29Rows_theUpgradeIsNotCancelledAndHoldsTheLockOfTheDerivations`, que no afirma nada sobre tiempos. Sin el `@Ignore`, hoy falla con:

```
RT-29: 8 logins of 262144 B of notes: pair 49.662693 ms, with 20 old passwords 501.301141 ms (x10.1)
java.lang.AssertionError: the upgrade costs 10.1 times the read of the same copy (at most 5)
```

## Re-comprobación de cada arreglo

### RT-25, parte a (la lista)
- La captura va dentro de `withContext(Dispatchers.Default)`, alrededor de `compute` (`ui/screens/VaultList.kt:302-310`). Con `NO_MEMORY`, `EntryList` vuelve antes de todo lo demás (`:383-386`).
- Lo cubren `EntryListNoMemoryTest` (la lista y «Códigos») y el ancla de `PlatformSourcesTest`.
- Ningún camino nuevo dentro de la lista. Lo que se reserva fuera de `compute` (`selection.retainVisible`, las claves `lazyKey` de lo que se ve) es lineal y pequeño.
- **Cerrado.**

### RT-25, parte b (el tope y el criterio)
- `copyExtraTags` (`vault/BackupCheck.kt:232-241`) cuenta las entradas, la papelera y cada versión, y para en el tope. `checkCopyEntries` rechaza por encima de 10 000 (`:253`).
- Una copia de 1.x se cuenta después de la subida, en «Importar» y en «Comprobar» (punto 8).
- `RedTeam20TagExcessTest` y `RedTeam20ListCostTest` siguen en verde en las dos puertas.
- **La longitud de las etiquetas (ataque 4) resiste:** `resistsALongTagOrTitleCostsTheListLinearly` (`RedTeam20Recheck2Test.kt:403`).
  - Una etiqueta de palabras de una letra (lo más denso para `FuzzySearch.words`: un `IntArray` por palabra) cuesta 17,73 y 18,01 B/B con 256 y 512 KiB. Es lineal, en el orden de la legítima más cara (17,64).
  - Un título igual cuesta 20,06 y 23,09 B/B. La clave de ordenación (`CollationKey`) lo hace algo más caro, pero sigue por debajo de la guardia D (1,5 × 17,64 = 26,5).
  - El tiempo también es lineal (×1,6 y ×2,2 al doblar).
  - No hay ningún coste superlineal en `compareKey`, en la clave ni en las palabras. Por el criterio, la densidad no es hallazgo y va con la fila 62 (informativo 5).
- **Cerrado.**

### RT-26
- `scanCopy` marca `escapedName` (`vault/VaultLimits.kt:441`) y `checkCopyShape` rechaza (`:319`).
- `rt26_` y `RedTeam20CopyShapeTest` en verde. Las claves de `tagColors` siguen libres.
- **Cerrado.**

### Informativo 1 (el guardián del hueco vacío) y ataque 3
- **Comportamiento:** `resistsNoWrapperOutsideTheScreensFillsAnEmptySlotWithoutTheRule` (`:313`). Las funciones públicas que pasan de `NoVault` a una bóveda son `VaultStore.create` y los dos `importReplace`, y fuera de `ui/` solo las llaman `AppContainer.createVault` (`AppContainer.kt:601`) e `importAsNewVault` (`:611-617`).
  - Escenario: A con su archivo y bloqueada, B vaciada y a la vista.
  - `mayFillEmptySlot()` da falso.
  - Los dos envoltorios lanzan `VaultsRefused` (`newVault`, `:664`).
  - El registro queda igual.
- **El texto:** el valor por defecto `{ false }` (`ui/screens/VaultBackup.kt:74`) y las cinco formas de M-5 siguen rechazadas por `EmptySlotGuardSourceTest`.
- Sigue siendo textual (informativo 2): fuera de `ui/` no mira, y dentro solo pide un `!guard()` antes de la llamada en la misma función.
- **Cerrado.**

### Informativo 2 (la ficha del wifi)
- `fieldShownText` (`ui/screens/EntryFields.kt:141-142`) y `marked = isWifiName(entry.type, t.key)` (`:176`) enseñan las marcas.
- «Copiar» copia el valor tal cual (`ui/screens/VaultEntryScreen.kt:167`).
- **Cerrado.**

### Informativo 3 (la lectura de una copia)
- `copyRead` envuelve cada lectura: `VaultStore.kt:1457`, `:1482`, `:1484`, `:1491`, `:1492` y `BackupCheck.kt:92`, `:94`.
- `NoMemoryTest` en verde.
- **Cerrado.**

### Punto 8 (R5) y ataque 1
- «Importar» (`VaultStore.kt:1520-1531`) y «Comprobar» (`BackupCheck.kt:103`) usan la misma `readAsImported` (`vault/Vault.kt:614-619`) y la misma `checkCopyData`.
- Lo que la lista del director pedía, todo en verde en `RedTeam20CheckAsImportTest`, en las dos puertas:
  - la versión desconocida y una sola decodificación (M-4);
  - las contraseñas antiguas;
  - más de 24 MiB después de `fitted()`;
  - varios defectos, también tamaño + entradas (M-2);
  - el adjunto que falta (M-1).
- **«Importar como bóveda nueva» (fila 63):** informativo 1, abajo.
- **Cerrado**, salvo la fila 63, que ya estaba aceptada.

### Punto 9 («Salud» y «Organizar») y ataque 5
- `healthView` captura (`health/HealthView.kt:27-34`) y la pantalla vuelve antes de la misión, de la lista y de «Sin problemas detectados.» (`ui/screens/HealthScreen.kt:138-141`).
- `withTagColor` solo guarda las etiquetas con color (`vault/Folders.kt:273-274`, con el nombre de M-6).
- **Lo reservado en el hilo principal, fuera de la captura, resiste:** `resistsWhatSaludMakesOnTheMainThreadIsSmallBesideWhatItCatches` (`:444`). Con 20 000 accesos:
  - `healthView` reserva 32,4 MB;
  - lo de cada composición (`associateBy`, `:147`; `sortedByDescending`, `:256`; los recuentos) reserva 1,18 MB (3,6 %);
  - `uniquePasswords` al pulsar (`:202`) reserva 2,9 MB (9 %).

  Es lineal y menor que lo capturado: no hay una forma en que lo capturado quepa y esto no.
- **El «Cancelar» perdido** es informativo 3.
- **Cerrado.**

### Punto 10
- Las anclas (a) a (d) de `EmptySlotGuardSourceTest` siguen en verde, y `produceState<Boolean?>(null, store)` está en `HealthScreen.kt:119`.
- **Cerrado.**

### M-1 a M-8
- M-1, M-2, M-3 y M-4 están en `RedTeam20CheckAsImportTest`, en verde en las dos puertas.
- M-5 en `EmptySlotGuardSourceTest`.
- M-6: `for (entryTag in e.tags)` (`Folders.kt:274`).
- M-7: los KDoc de `BackupCheck.kt:18-24` y `:46-50`.
- M-8: las filas 58, 61, 62 y 63 de `pendientes.md`.
- **Cerrados.**

## Lo que resiste

- **Ataque 2, memoria de la subida:** `resistsTheUpgradeOfManyOldPasswordsStaysUnderTheEstimate` (`:279`).
  - Se mide con la `upgraded` real, por reflexión, y con lo leído y lo subido a la vez, como los tiene `fitted`.
  - Con 100 000 y 200 000 contraseñas antiguas, lo leído más lo subido es, en los dos tamaños:
    - el **0,89** de la estimación con contraseñas de un carácter, la forma peor: la estimación sube 2 B y la cadena real, unos 48 B;
    - el 0,71 con contraseñas vacías.
  - La subida añade 164 B por contraseña antigua, frente a los 200 B que estimaba el director.
  - El margen es de un 11 %. La estimación cobra 160 B por objeto, y un `PasswordChange` cuesta mucho menos.
  - **Extrapolado** (lineal): sigue en 0,89 a cualquier tamaño, bajo la estimación que dejó entrar la copia. Si falta memoria, la captura de «Importar» (`VaultStore.kt:1543`), la de «Comprobar» (`BackupCheck.kt:104`) y la del desbloqueo propio (`VaultStore.kt:604`) dan `COPY_NO_MEMORY` o `VAULT_NO_MEMORY`.
- **Ataque 3:** ningún envoltorio fuera de `ui/` llega a un hueco vacío sin la regla (arriba).
- **Ataque 4:** la longitud de las etiquetas y de los títulos (arriba).
- **Ataque 5:** lo que «Salud» reserva en el hilo principal (arriba).
- **Ataque 7, dos `EntryListViewModel` a la vez:** cada uno calcula dentro de su captura. `EntryListNoMemoryTest` lo prueba en la lista y en «Códigos». No he encontrado ningún cierre fuera de la captura. Queda como riesgo con nombre (filas 58 y 62), para medirlo en el móvil.

## Informativos

1. **Fila 63, en los dos sentidos.** Lo fijan cuatro tests, en verde (`:179-199`).
   - **El que aceptó el director:** con una etiqueta de un carácter y los datos justo en 24 MiB + 4 KiB, «Comprobar» da `OK` y «Importar como bóveda nueva» da `COPY_DAMAGED` (`VaultStore.kt:1524`, +31 B).
   - **El otro, que el director no listó:** una etiqueta de más de 32 caracteres (la app nunca la escribe) encoge al cambiarla, sin más tope que la copia. Con una etiqueta de 64 y 20 B por encima del límite, «Comprobar» da `COPY_DAMAGED` y «Importar como bóveda nueva» la importa.
   - Eso no es falsa seguridad, sino lo contrario: «Comprobar» rechaza una copia que una de las importaciones acepta.
   - La condición del director («si el atacante encuentra más») se cumple en ese sentido débil. Su salida, que `checkCopyStructure` rechace como dañada una etiqueta no vacía que no tenga 32 hex en minúsculas, cierra los dos sentidos. Lo decide el director.
- **Si se aplica esa salida, los cuatro `informative63_` se ponen en rojo,** porque fijan lo de hoy. El implementador los cambia: las dos copias, rechazadas por las dos.
2. **El test de fuente sigue siendo textual.** `informativeTheSourceTestReadsTheGuardBeforeTheCallNotAroundIt` (`:349`) aplica por reflexión su `blank` y sus expresiones `call`, `refusing` y `topFun`.
   - Acepta un `!guard()` en otra lambda de la misma función.
   - Acepta un `if (!guard())` que no vuelve.
   - «Antes, en la misma función» no prueba que la llamada solo corra si el guardián dijo que sí.
   - Hoy ninguna pantalla está escrita así, y el test de comportamiento lo confirma.
   - Si se quiere cerrar por construcción, el guardián tendría que ir dentro del almacén: un parámetro de `create` e `importReplace` que no tenga valor por defecto.
3. **«Salud» con `NO_MEMORY` esconde una comprobación de filtraciones en marcha.** Lo fija `informativeSaludWithoutMemoryHidesTheCancelOfARunningCheck` (`:472`).
   - `produceState` depende de `breached` (`HealthScreen.kt:134`), así que cada filtración nueva vuelve a calcular `healthView`.
   - Si ese cálculo se queda sin memoria, la pantalla vuelve (`:138-141`) antes de la barra, de «Cancelar» (`:198`) y del mensaje, y la comprobación sigue enviando prefijos sin que se vea.
   - Salir de la pantalla la para (`:91`), y bloquear también. Es lo que el usuario pidió, así que no sale nada nuevo.
   - Arreglo, si se quiere: `job?.cancel()` en la rama `noMemory`, o la fila del progreso antes de la vuelta.
   - Relacionado: `healthView` no tiene puntos de cancelación, así que cuando `breached` cambia, el cálculo anterior sigue hasta el final en `Default`. Con muchas filtraciones seguidas puede haber varios a la vez, como mucho uno por hilo.
4. **Densidad de etiquetas y títulos largos** (fila 62).
   - Etiquetas: 17,7 a 18,0 B/B. Títulos: 20,1 a 23,1 B/B.
   - En el hilo principal, cada ficha de etiqueta que se ve hace una clave `lazyKey('t', tag)`, que es una copia entera de la etiqueta (`VaultList.kt:599`, `:811`).
   - `plainReportText` recorre todo un texto cuyos caracteres no se dibujan: se corta por lo que enseña, no por lo que lee.
   - Las dos cosas son lineales y no cierran la app, pero un título o una etiqueta de megas de U+200B se vuelve a recorrer en cada composición. Va al móvil con la fila 62.

## Necesita prueba en el móvil

- **RT-27:** con una bóveda grande cuya lista quepa justo, abrir «Importar de otra app» en un móvil de 192 a 256 MiB. Va con las filas 51 y 58.
- **RT-28:** con la copia del test, aplicar la vista y escribir en la búsqueda. Medir cuánto tarda cada letra y si sale «no responde».
- **RT-29:** importar y comprobar una copia de 1.x con notas grandes y contraseñas antiguas, y medir cuánto tarda y si «Cancelar» responde.
- **Informativo 5:** un título de varios MB de U+200B. Ver cuánto tarda la lista en desplazarse.

## Revisión experto-seguridad-cripto

- **RT-27:** CWE-400 y CWE-770, como la fila 59. La decisión (a) y R11c piden que el trabajo largo sobre datos grandes no cierre la app, y el plan de importar recorre la bóveda entera. La prueba está en `ImportScreen.kt:213-219` y `:319`.
- **RT-29:** CWE-407 y CWE-400. La decisión (a) pide que el trabajo largo se pueda cancelar, y aquí el coste cuadrático en las versiones (`VaultLimits.kt:113-116`) va dentro de `KdfLock.derive`, sin cancelación.
- **RT-28:** CWE-407 (*Inefficient Algorithmic Complexity*) y CWE-1050. La decisión (a) pide el trabajo largo fuera del hilo principal. Es O(F × N) en el hilo principal (`VaultList.kt:405`), con F acotado solo por `COPY_LIST_MAX`.
- **Lo que está bien:**
  - el presupuesto del árbol cubre también la subida a v4: 0,71 de la estimación, medido;
  - «Comprobar» e «Importar» comparten los datos y las comprobaciones;
  - RT-26 cierra la clase CWE-436 en los nombres;
  - ninguna copia ni ningún envoltorio llega a un hueco vacío sin la regla;
  - ningún texto nuevo dice «Contraseña incorrecta» por falta de memoria.
- Sin primitivas, formatos, dependencias, permisos ni hosts nuevos. No se tocó `app/src/main`.

## Revisión Ingeniero Móvil Senior

- **RT-27:**
  - un `viewModelScope.launch` sin `CoroutineExceptionHandler` propaga el `Error` y cierra el proceso;
  - `collectLatest` no lo para;
  - el `EntryListViewModel` de la pantalla de abajo conserva su índice mientras se importa (`AppNavHost.kt:350-352`), así que los dos se suman.
- **RT-28:**
  - `LaunchedEffect(result)` corre en `Dispatchers.Main`;
  - `List.contains` es lineal;
  - el arreglo natural lleva la poda a `compute`, en `Default`, o le da a `ListResult` un `Set`;
  - sin estado guardado nuevo.
- **Informativo 3:** `rememberCoroutineScope` y `DisposableEffect` paran la comprobación al salir, y el ciclo de vida es correcto. Solo falta que se vea mientras dura.
- **Los tests:**
  - las medidas usan calentamiento, la mediana de 3 y una semilla fija (`Random(20261119L)`);
  - restauran `VaultCrypto.beforeDecode` en un `finally`;
  - en jvm-check el más largo dura 3,0 s (`informative63_aLongTagImportsAsANewVault`); en la app, 2,9 s (el mismo);
  - todos están fuera de `ui/`, así que corren también en jvm-check.

## Textos para el Abogado Digital (revisión final)

- RT-27, si se arregla con un texto fijo en «Importar de otra app»: `LIST_NO_MEMORY` («… para enseñar esta bóveda…») no describe del todo un plan de importar, así que quizá haga falta uno propio (regla M-3).
- RT-28 y RT-29: ninguno.
- Fila 63: ninguno (`COPY_DAMAGED` ya existe).

## Puerta

| Ejecución | Tests | Ignorados | Fallos | Resultado |
|---|---|---|---|---|
| `jvm-check`, `--tests 'app.seguridad.redteam.*'` | 136 | 3 (RT-27, RT-28, RT-29) | 0 | verde |
| La misma ejecución: `NoMemoryTest`, `EmptySlotGuardSourceTest`, `PlatformSourcesTest`, `HealthViewTest` | 61 | 0 | 0 | verde |
| `:app:testDebugUnitTest --tests 'app.seguridad.redteam.*' --tests 'app.seguridad.ui.redteam.*'`, después `--stop` | 139 | 3 (RT-27, RT-28, RT-29) | 0 | verde |
| La misma ejecución: `EntryListNoMemoryTest`, `EntryFieldsTest`, `HealthScreenTest` | 10 | 0 | 0 | verde |

- Sin los `@Ignore`, fallan exactamente `rt27_`, `rt28_` y `rt29_`, con las salidas copiadas arriba (`jvm-check`, de dos en dos y uno: 2 fallos de 2, y 1 de 3 con los de la memoria).
- El archivo nuevo es `app/src/test/java/app/seguridad/redteam/RedTeam20Recheck2Test.kt`: 14 tests, 11 en verde y 3 ignorados.
- No se tocaron `app/src/main` ni `pendientes.md`.
