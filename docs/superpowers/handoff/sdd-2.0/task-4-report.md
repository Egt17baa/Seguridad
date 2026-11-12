# Tarea 4: carpetas, vistas, etiquetas de color, ordenar, filtros y búsqueda con erratas. Informe del implementador

**Estado:** DONE_WITH_CONCERNS (las preocupaciones están en «Dudas»: el coste de `markUsed` en una bóveda grande y un test de la tarea 3 cambiado).
**Commit:** `76642a8` («2.0, tarea 4: carpetas, vistas y búsqueda»), sobre `d24fdc2`. Sin push.
**Tests:** app 2 049 en verde (`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`, BUILD SUCCESSFUL, sin fallos ni omitidos); jvm-check 2 042 en verde (`./gradlew -p jvm-check test`). Antes: app 2 019 y jvm-check 2 012.

## Qué se ha hecho

**Sin interfaz (con TDD, los tests en rojo antes, por compilación y por aserción):**
- `vault/Folders.kt` (nuevo): `FolderDeletion { PARENT, TRASH }`, `folderPath`, `descendants` (nunca incluye la carpeta, ni en un ciclo), `folderTree` (en profundidad, por `order` y nombre; una carpeta cuyo padre falta va arriba), y funciones puras sobre `VaultData` (`withNewFolder`, `withFolderRenamed`, `withFolderMoved`, `withoutFolder`, `withEntriesMoved`, `withView`, `withoutView`, `withTagColor`, `withUsed`). Nombres: recortados, que se vea algo, ≤ 60 puntos de código, comparados con `compareKey` sin mayúsculas entre hermanos (`organizeKey`). Errores con texto fijo: `OrganizeRefused` (nombres, carpeta que ya no está, etiqueta no válida) y `VaultLimitExceeded` (500, 5 niveles, ciclo, 50 vistas, 200 colores).
- `VaultStore`: `createFolder` (da `Folder?`, null si está bloqueada), `renameFolder`, `moveFolder`, `deleteFolder`, `moveEntries` (devuelve cuántas), `saveView`, `deleteView`, `setTagColor`, `markUsed`. Cada una es un solo guardado bajo el mutex, con `fittedForSave` y `checkStructure` de siempre. `storeSaveErrorText` enseña también los textos de `OrganizeRefused`.
  - **`markUsed`:** `modifyData(bookkeeping = true)` → `commit(mustSave = false)`, el mismo guardado que la última comprobación de la maestra: el archivo conserva su hora (`lastChangeSavedAt` no cambia), `revision` no sube (la copia «con cada cambio» no salta), no crea versión. Mientras espera el paso a v4 no lanza: da false. Como mucho una escritura por entrada y minuto (`MARK_USED_EVERY_MS`); nunca en la papelera.
  - **`upsert`** conserva `folderId` y `lastUsedAt` de la entrada guardada (el editor no los conoce y los mandaba vacíos: editar sacaba la entrada de su carpeta). Una entrada nueva puede llegar con la carpeta si existe; una personal, nunca.
  - **`restore`** devuelve la entrada a su carpeta si sigue, y a ninguna si se borró. **`restoreVersion`** conserva la carpeta de ahora.
  - **Borrar una carpeta:** PARENT pasa sus entradas y subcarpetas a la de arriba; TRASH borra la carpeta y sus subcarpetas, y manda sus entradas a la papelera con `deletedAt`, como `moveToTrash`, en el mismo guardado. Una vista que miraba una carpeta borrada pasa a la más cercana de arriba que quede.
  - **`fittedForSave`** (`VaultLimits.kt:167`): una entrada que solo cambió de carpeta, de último uso o de papelera cuenta como no cambiada, así que una entrada de la 1.4.3 de más de 64 KiB se puede mover, usar y mandar a la papelera. Antes, mandarla a la papelera (tarea 3) la rechazaba con `ENTRY_TOO_BIG`.
- `vault/FuzzySearch.kt` (nuevo): `normalize` (`compareKey`, minúsculas, sin acentos), `score(query, text): Int?` y `prepare(query).score(texts, words)`. Primero las exactas: el texto entero, el principio, el principio de una palabra o cualquier sitio (< 100). Después, por palabra: dentro de una palabra tal cual, o con distancia Damerau-Levenshtein (OSA) ≤ 1 para 4 a 7 letras y ≤ 2 desde 8, contra la palabra o un principio suyo («amazn» encuentra «Amazon»); con menos de 4 letras, solo exacta (≥ 100, más 10 por errata). Las palabras de más de 64 puntos de código solo se comparan tal cual, y la matriz corta en cuanto una fila pasa del máximo.
- `vault/EntryQuery.kt`: `SearchIndex` (texto normalizado y palabras de cada entrada, y la clave de orden del título; con `previous`, reutiliza los textos, los hosts y las claves del índice anterior, con su mismo `Collator`) y `List<VaultEntry>.query(filter, data, now, weak, no2fa = null, index = null)`. Busca en el título, el usuario, el host de la URL (`searchHost`), el servicio y la cuenta del 2FA, las etiquetas, la carpeta y las de arriba, y los nombres de los campos no ocultos. Filtra por tipos, carpeta (con o sin subcarpetas; una carpeta que no está no deja nada), etiquetas (todas), favoritas, débiles, sin 2FA y caducan pronto (30 días). Ordena con texto por puntuación y después por el orden elegido (nombre con `Collator` es-ES primario sobre `compareKey`, o modificación, creación y uso, de más reciente a menos). Más `recentEntries` (las 5 de `lastUsedAt` más alto) y `favoriteEntries`. Nunca la papelera, la identidad ni los contactos. `ListQuery` sigue para los contactos, sin cambios.
- `imports/MergePlan.kt`: `searchHost(url)` lee el enlace como `urlHost` (como un navegador, con `https://` delante si no tiene esquema) y da solo el host en Unicode, sin `www.`; nunca el resto del enlace (usuario y clave incluidos).
- `health/Health.kt`: `loginsWithout2fa` (lo usa «Sin 2FA») y una corrección: un acceso con 2FA propio ya no sale como «sin 2FA» en «Salud» (test `loginWithItsOwnCodeHas2fa`).
- `vault/TagPalette.kt` (nuevo): 8 colores fijos, cada uno en claro y en oscuro, con contraste de texto ≥ 4,5:1 sobre las 4 superficies de cada tema (`TagPaletteTest`). Un nombre por color (`tagColorName`), para que el color no sea la única señal (R-1).
- `vault/OrganizeTexts.kt` (nuevo): todos los textos nuevos.

**Interfaz:**
- `ui/screens/VaultList.kt` (nuevo; la lista sale de `VaultScreen.kt`):
  - `EntryListViewModel` guarda el texto, el `EntryFilter` y la posición. El cálculo va en `Dispatchers.Default`, con `collectLatest` (cada cambio cancela el anterior) y una caché por `VaultData` con el índice y «Salud» (débiles, sin 2FA). `reset()` al bloquear borra el filtro, la posición, la caché y el resultado, y un resultado que llega tarde se descarta si la bóveda está bloqueada.
  - La lista: búsqueda (su «Más información» solo con texto); orden en un menú; «📁 Carpetas»; «Guardar vista» con un filtro puesto; la ruta de la carpeta, con «Con subcarpetas» y «Quitar carpeta»; las fichas de un toque (Todo, cada tipo con su número, ★ Favoritas, Débiles, Sin 2FA, Caducan pronto); las fichas de las vistas y «Gestionar vistas»; las etiquetas en su color y «🎨 Colores». Arriba, «Recientes» y «Favoritos» solo sin texto ni filtros, y debajo «Todas».
  - Cada fila enseña su carpeta y hasta 3 etiquetas en su color. Con carpetas en la bóveda, tiene un asa «⠿» para arrastrarla a una barra de carpetas (sin carpeta y las 12 primeras; las demás, por «Mover a…»), y la acción de TalkBack «Mover a…».
- `ui/screens/VaultOrganize.kt` (nuevo): los diálogos del árbol de carpetas (abrir una carpeta, nueva, subcarpeta, renombrar, mover, borrar con las dos opciones), mover a…, nombre, vistas (borrar con confirmación en la misma fila) y colores. Cada diálogo guarda solo ids, y enseña los rechazos con `storeSaveErrorText`.
- `VaultEntryScreen.kt`: la ruta de la carpeta, el botón «Mover a…» (si hay carpetas o la entrada está en una) y `markUsed` al copiar un campo (`Field(onCopied)`).

**Tests nuevos o ampliados:** `FoldersTest` (los 6 del plan, más `pathAndTreeOrder` y `moveEntriesKeepsHistoryAndDates`), `FuzzySearchTest` (los 5 del plan, más `twoThousandEntriesUnder50ms`, `hostileInputsDoNotThrow` y `upsertNearTheCapReference`), `EntryQueryTest` (los 5 del plan, más `searchFieldsAndHostAndFolder`), `OrganizeStoreTest` (`markUsedIsNoVersionNorChange`, `viewsSavedReplacedAndDeleted`, `tagColors`, `oldBigEntryCanMoveAndBeUsed`), `TagPaletteTest` y `HealthTest.loginWithItsOwnCodeHas2fa`.

**Tests actualizados** porque la lista cambió de archivo o de forma (con las mismas comprobaciones, y alguna más):
- `PlatformSourcesTest`: el ViewModel de la lista, R18 y R9. Ahora miran `VaultList.kt` y `VaultOrganize.kt`, y añaden que el cálculo va fuera del hilo principal, que `reset` borra la caché y el resultado, y que los nombres de carpetas y vistas pasan por `plainReportText`.
- `VaultTotpTest`: la fila de un 2FA no admitido, ahora en `VaultList.kt`.
- `EntryHistoryTest.lastUsedAtIsNotAVersion` (ver «Dudas»).

## Medidas (M-6 de la tarea 1 y rendimiento), en la JVM y solo como referencia

- **Búsqueda con erratas en 2 000 entradas** (índice ya hecho, lo mejor de 10 tras calentar): unos **2,3 ms** (de 2,3 a 2,4 ms en las ejecuciones). El test exige menos de 50 ms. Hacer el índice cuesta de 31 a 49 ms la primera vez y de **3 a 5 ms** después de un guardado (con `previous`). El número del móvil se comprueba a mano.
- **M-6, un `upsert` con la bóveda en 23 230 KiB** (2 000 notas de unos 11,8 KB, cerca del tope de 24 MiB): **de 0,5 a 1,3 s**, según la ejecución (508 ms, 547 ms, 780 ms y 1 305 ms). Ajusta los límites, cifra, escribe y vuelve a leer la bóveda. `markUsed` cuesta lo mismo: de 0,5 a 1,3 s. El test solo pide menos de 30 s, como referencia.

## Dudas

1. **El coste de `markUsed` en una bóveda grande.** Es un guardado entero de la bóveda (cifrar, escribir y volver a leer), como cualquier otro: unos 0,5 a 1,3 s en la JVM cerca de 24 MiB, y varias veces más en un móvil, con el mutex tomado. Lo acoté a una escritura por entrada y minuto. Guardarlo solo en memoria hasta el siguiente guardado perdería «Recientes» en cada bloqueo, así que lo descarté. Si molesta en el móvil, la salida es juntar las marcas y escribirlas con el próximo guardado o con un retraso. Se deja para la revisión final (rendimiento).
2. **`EntryHistoryTest.lastUsedAtIsNotAVersion` (tarea 3)** esperaba que `upsert` guardara el `lastUsedAt` que mandaba quien llama. Ahora `upsert` conserva el de la tienda: solo `markUsed` lo cambia. Así el editor, que no lo conoce, no lo borra. El test lo comprueba así y añade `markUsed`, sin versión.
3. **La búsqueda incluye el servicio y la cuenta del 2FA**, aunque el diseño no los nombra. No son secretos, la 1.4.3 ya los buscaba y la pestaña «Códigos» los necesita. El host se busca en su forma Unicode («españa.es»).
4. **Varias etiquetas en un filtro o en una vista se combinan con «y»** (la entrada tiene todas), como los demás filtros. Una etiqueta sola funciona como antes.
5. **Con texto, primero la puntuación** (las exactas antes que las de erratas) y, entre iguales, el orden elegido. Sin texto, solo el orden: «Favoritos» tiene su sección arriba y las favoritas ya no van primero en «Todas». `ListQuery` (contactos) sigue igual.
6. **Borrar una carpeta a la papelera no tiene «Deshacer»**: la confirmación lo dice y las entradas se restauran desde la papelera, sin carpeta, porque la carpeta ya no está. Con PARENT, una subcarpeta puede acabar junto a otra del mismo nombre. Se acepta, porque rechazar el borrado sería peor. Los nombres se comprueban al crear, renombrar y mover.
7. **Una entrada nueva no entra sola en la carpeta abierta**: la ruta del editor no la lleva. La tienda ya lo admite (`upsert` con `folderId` válido), así que puede hacerse en la tarea 6 o la 10.
8. **«Caducan pronto»** mira `expiresAt` (30 días). Los campos de fecha marcados «caduca» son de la tarea 5.
9. **Los nombres de carpetas y vistas de una copia hecha fuera** no tienen tope propio en `checkStructure`, para no rechazar copias. Los acota el tope de 24 MiB, se enseñan con `plainReportText` (cortados) y la búsqueda solo compara con erratas las palabras de hasta 64 puntos de código.
10. **El color de las etiquetas es fijo**: no sigue el color dinámico (tarea 10), y así el contraste comprobado sigue valiendo.

## Revisión experto-seguridad-cripto

Repasé el diff con `references/auditoria.md` como lista de comprobación. Esta tarea no toca criptografía: no cambia ningún formato, clave, KDF ni AAD.

- **La búsqueda nunca lee secretos.** El índice solo toma el título, el usuario, el host, el servicio y la cuenta del 2FA, las etiquetas, los nombres de las carpetas y los nombres de los campos no ocultos (`vault/EntryQuery.kt:118-129`). El host sale de `searchHost` (`imports/MergePlan.kt:519`), que nunca da el usuario ni la clave de un enlace. Lo prueban `EntryQueryTest.neverSearchesPasswordsHiddenFieldsOrNotes` (contraseña, nota, campo oculto con su nombre y valor, secreto del 2FA, `https://ana:gatito@…` y frase semilla; ni tal cual ni con errata) y el antiguo `searchNeverMatchesSecrets`.
- **Entradas hostiles** (decisión (a) de la 1.4.0): Damerau-Levenshtein solo hasta 64 puntos de código (`FuzzySearch.kt:28`) y con corte en cuanto una fila pasa del máximo (`FuzzySearch.kt:151`). Un ciclo de carpetas en una copia no cuelga `folderPath`, `descendants`, `folderTree` ni `branchHeight` (conjuntos de vistos, `Folders.kt:55-112`), y la tienda nunca lo guarda (`checkStructure`). Lo prueban `FoldersTest.cycleRefused` y `FuzzySearchTest.hostileInputsDoNotThrow` (sustituto suelto, NUL, 5 000 caracteres).
- **Se muestra como texto plano** (R9 y R10, restricciones globales): los nombres de carpetas y vistas, las etiquetas, la ruta y la entrada arrastrada pasan por `plainReportText` (`VaultList.kt`, `VaultOrganize.kt`), fijado en `PlatformSourcesTest` R9 con la búsqueda global de `Text(` sin `plainReportText`. Sin enlaces, HTML ni `Linkify`.
- **Lo que vive en memoria y se borra al bloquear** (F-4): la búsqueda, el filtro, el índice y el resultado viven en `EntryListViewModel`, nunca en `rememberSaveable` ni en un `Bundle` (fijado en `PlatformSourcesTest`). Al bloquear, `reset()` lo borra todo (`VaultList.kt:237-243`), y un resultado tardío se descarta (`VaultList.kt:210`). Los diálogos solo guardan ids (`OrganizeDialog`). La entrada arrastrada solo vive en el estado de la composición mientras dura el arrastre, y la lista sale de la composición al bloquear (`VaultGate`).
- **`markUsed` no es un cambio:** no crea versión ni sube `revision`, y no cambia la hora del archivo (`VaultStore.kt:751`, `:768`, `:985`). Así no lanza la copia automática ni hace creer que hay una copia pendiente. Lo prueba `OrganizeStoreTest.markUsedIsNoVersionNorChange`, que también comprueba que el uso sobrevive a bloquear y abrir.
- **Integridad:** `upsert` no deja que el editor cambie la carpeta ni el último uso (`VaultStore.kt:806`). Una versión restaurada no mueve la entrada (`:1085`). Lo restaurado de la papelera no apunta a una carpeta que ya no está (`:1038`). Borrar una carpeta a la papelera solo pone `deletedAt` (`Folders.kt:189`), así que la papelera, sus 30 días y las copias siguen como en la tarea 3. Lo de la papelera nunca sale en la lista, en la búsqueda ni en «Recientes» o «Favoritos» (`EntryQueryTest.trashNeverShown`).
- **Topes:** 500 carpetas, 5 niveles contando la rama movida, 50 vistas y 200 colores (los de etiquetas que ya nadie usa se quitan antes), con `VaultLimitExceeded` y nada guardado (`FoldersTest.limit500`, `depthFiveAllowedSixRefused`, `OrganizeStoreTest`).
- **Ventanas:** el menú de orden, los diálogos y sus menús llaman a `FilterObscuredTouches()`, lo que exige ANDROID-3 en `PlatformSourcesTest`.
- Sin permisos, dependencias, red ni registros nuevos (`checkLicense` en verde).
- **Hallazgos:** ninguno. Lo que queda se acepta: el coste de `markUsed` es de rendimiento, no de seguridad (Duda 1), y los nombres largos de una copia ajena los acota el tope de 24 MiB (Duda 9).

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** el cálculo de la lista va en `Dispatchers.Default` (`VaultList.kt:208`), con `collectLatest`, que cancela la ejecución anterior y espera a que termine. Así la caché solo la toca una ejecución a la vez (`@Volatile` por si acaso). Las operaciones de la tienda son un solo `modifyData` bajo el mutex. `markUsed` va en el ámbito de la pantalla: si se sale antes de tomar el mutex, solo se pierde la marca, y el guardado ya empezado es `NonCancellable` (`commit`).
- **Rendimiento:**
  - Índice por cambio de la bóveda; después, cada tecla solo puntúa: 2,3 ms en 2 000 entradas en la JVM. Tras un guardado, el índice reutiliza lo anterior y cuesta de 3 a 5 ms.
  - El orden por nombre usa `CollationKey` precalculadas (no `Collator.compare` en cada comparación). «Salud» (débiles, sin 2FA) se calcula una vez por cambio.
  - Las fichas de etiquetas y vistas van en `LazyRow` (R18), y las claves de las filas son únicas por sección (`r:`, `f:`, `a:`).
  - Coste aceptado: `markUsed` en una bóveda enorme (Duda 1).
- **Ciclo de vida:** la posición de la lista, como dos números en el ViewModel (`LazyListState(list.firstIndex, list.firstOffset)`, auditoría de la 1.4.1). Un filtro con una etiqueta o una carpeta que ya no están se quita (`LaunchedEffect(result)`). Un diálogo cuyo objeto desaparece se cierra en un efecto, no durante la composición (`Gone`).
- **Accesibilidad (R-1):** arrastrar tiene alternativa, la acción «Mover a…» de la fila (`VaultList.kt:518`) y el botón de la ficha. El asa tiene `contentDescription` y el menú de cada carpeta dice su nombre. Los colores tienen nombre y contraste ≥ 4,5:1 en los dos temas (`TagPaletteTest`). Los radios y los botones de los diálogos llevan `Role`.
- **Arquitectura:** la lógica pura está en `vault/` (jvm-check la compila y la prueba sin Android: `Folders.kt`, `FuzzySearch.kt`, `TagPalette.kt`, `EntryQuery.kt`), y la interfaz, en `VaultList.kt` y `VaultOrganize.kt`. `VaultScreen.kt` adelgaza (de 630 a unas 480 líneas). `VaultStore.kt` crece unas 50 líneas: sigue pendiente partirlo (menor de la tarea 2, revisión final).
- **Compatibilidad:** sin campos de formato nuevos (todo estaba en `VaultData` y `VaultEntry` desde la tarea 1). `Health` cambia a mejor: un acceso con 2FA propio ya no sale como «sin 2FA».
- No probado en un móvil: ni el arrastre, ni el tiempo real de la búsqueda, ni TalkBack. Va a las pruebas a mano o a Maestro (tarea 11).

## Textos para el Abogado Digital (revisión final)

Todos están en `app/src/main/java/app/seguridad/vault/OrganizeTexts.kt`, salvo los errores (`vault/Folders.kt`) y los nombres de los colores (`vault/TagPalette.kt`).

- Búsqueda: «Buscar»; plegado, `SEARCH_MORE`: «Busca en el nombre, el usuario, la web (solo su dominio), el servicio del 2FA, las etiquetas, las carpetas y los nombres de los campos que no están ocultos. Nunca en las contraseñas, los campos ocultos, las notas ni las palabras de una frase semilla. Perdona una errata en palabras de 4 a 7 letras y dos desde 8; primero salen las que coinciden tal cual.» (Es veraz con el código: `EntryQuery.kt:118-129` y `FuzzySearch.kt`.)
- Lista: «Todo», «Recientes», «Favoritos», «Todas», «★ Favoritas», «Débiles», «Sin 2FA», «Caducan pronto», «Sin resultados», «Tu bóveda está vacía. Pulsa «Añadir».» (ya existía), «Quitar filtros».
- Orden: «Orden: …», «Por nombre», «Por modificación», «Por creación», «Por uso».
- Carpetas:
  - Generales: «Carpetas», «Toca una carpeta para ver lo que tiene.», «Aún no hay carpetas.», «Nueva carpeta», «Nueva subcarpeta», «Renombrar», «Mover», «Borrar carpeta», «Nombre de la carpeta», «Todas las entradas», «Sin carpeta», «Arriba del todo», «Con subcarpetas», «Opciones de la carpeta «…»», «Quitar carpeta», y la ruta «📁 A › B».
  - Al borrar: «¿Borrar la carpeta «…»?», «Sus entradas y sus subcarpetas se quedan sin carpeta», «Sus entradas y sus subcarpetas pasan a «…»», «Sus entradas, y las de sus subcarpetas, van a la papelera; las subcarpetas se borran», «Desde la papelera se pueden restaurar durante 30 días, sin carpeta.»
  - Mover: «Mover a…», «Mover «…» a…», «Mover la carpeta «…» a…», «Movida a «…»», «Arrastrar a una carpeta», «Suelta en una carpeta», «Para otras carpetas, usa «Mover a…» en la ficha.»
- Errores (`Folders.kt`): «Ponle un nombre a la carpeta.», «Ya hay una carpeta con ese nombre en el mismo sitio. Elige otro.», «El nombre de una carpeta puede tener como mucho 60 caracteres. Acórtalo.», «Esa carpeta ya no está en la bóveda. Elige otra.», «Ponle un nombre a la vista.», «Ya hay una vista con ese nombre. Elige otro.», «El nombre de una vista puede tener como mucho 60 caracteres. Acórtalo.», «Esa etiqueta no es válida.». Los de los topes (500, 5 niveles, ciclo, 50 vistas, 200 colores) ya estaban en `VaultLimits.kt` desde la tarea 1.
- Vistas: «Vistas», «Guardar vista», «Guarda la búsqueda, los filtros, la carpeta y el orden de ahora con un nombre.», «Nombre de la vista», «Gestionar vistas», «Borrar vista», «Borrar una vista no borra ninguna entrada.», «Borrar».
- Colores: «Colores de etiquetas», «🎨 Colores», «Elige un color para cada etiqueta. El nombre se ve siempre.», «Sin color», «Aún no hay etiquetas. Ponlas al editar una entrada.», «Rojo», «Naranja», «Amarillo», «Verde», «Turquesa», «Azul», «Morado», «Gris».
- Botones: «Guardar», «Cancelar», «Cerrar».
- Para la política (O-2 y la papelera): borrar una carpeta «a la papelera» manda sus entradas a la papelera y borra sus subcarpetas; no es un borrado para siempre. La organización (carpetas, vistas, colores y último uso) vive cifrada dentro de la bóveda y va en las copias.

---

# Ronda 1 de arreglos

**Commit:** `c688eae` («2.0, tarea 4: ronda 1 de arreglos de la revisión»), sobre `76642a8`. Sin push.
**Tests:** app 2 062 en verde (`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`, sin fallos ni omitidos); jvm-check 2 053 en verde. Antes: 2 049 y 2 042.
**Medidas** en la JVM, solo como referencia:
- M-6, `upsert` con la bóveda en 23 230 KiB: **423 ms**, y `markUsed`, 430 ms (antes, de 0,5 a 1,3 s): ya no se descifra la bóveda otra vez tras guardar.
- Búsqueda en 2 000 entradas: 2,9 ms.
- Índice: 31 ms la primera vez y 3,3 ms tras guardar.

Todos los arreglos siguen TDD: cada test se escribió antes y falló, por compilación o por aserción.

**C-1. Claves de Compose que chocan.**
- Qué cambia:
  - `lazyKey(kind, id) = "$kind:$id"` (`ui/screens/VaultList.kt:461`). Todas las filas hechas con datos de la bóveda lo usan: etiquetas `t:` (`:395`), vistas `v:` (`:382`), las secciones `r:`, `f:` y `a:`, y en los diálogos las carpetas `f:`, las vistas `v:` y las etiquetas `t:` (`VaultOrganize.kt:188`, `:289`, `:372`, `:425`).
  - Las fichas fijas («colors», «manage», «search»…) no llevan «:», así que no pueden chocar.
  - Además, las vistas se quitan los ids repetidos antes de pintarse (`distinctBy`).
- Test: `PlatformSourcesTest` «C-1 the keys of the vault's lazy rows never meet the fixed ones»: toda `key =` de un `items(` en `VaultList.kt` y `VaultOrganize.kt` empieza por `lazyKey(`, y ninguna clave fija de `item(key = …)` lleva «:». El pin de R18 se actualiza.

**I-1. Ids repetidos en una copia hostil.**
- Qué cambia:
  - `checkStructure` rechaza ids de carpeta o de vista repetidos con `ORGANIZE_DAMAGED` (`VaultLimits.kt:208`, `:210`). Así `checkCopyStructure` da `COPY_DAMAGED`, en «Importar copia» y en «Comprobar una copia».
  - `branchHeight` recorre nivel a nivel, cada id una vez (`Folders.kt:88`).
- Tests en `FoldersTest`:
  - `duplicateFolderIdsRefused`: la copia de la revisión, 5 × 100 carpetas repetidas, da `branchHeight` 5 en menos de 100 ms y se rechaza; un ciclo tampoco da vueltas;
  - `duplicateViewIdsRefused`: dos vistas «manage» son una copia dañada, y una sola vale;
  - `hostileCopyWithRepeatedIdsIsNotImported`: `importReplace` con esa copia da `COPY_DAMAGED`, y la bóveda queda como estaba.

**I-2. Volver a descifrar tras cada guardado.**
- Qué cambia:
  - `modifyData` guarda lo que escribió (`fittedForSave` + `withPlainVersions`) y lo pasa a `commit(…, read = saved)` (`VaultStore.kt:774`, `:791`). Así ningún guardado (editar, mover, `markUsed`…) descifra ni decodifica la bóveda otra vez.
  - Si a `commit` no le llega `read` (desbloqueo, importar), descifra en `withContext(compute)`, y antes solo si la bóveda no se bloqueó (`:441`).
  - El desbloqueo de una v2 o v3 que espera el paso a v4 también descifra en `compute` (`:561`).
- Tests:
  - `OrganizeStoreTest.savesOpenWhatTheyWroteWithoutDecodingAgain`: una entrada que no cambió sigue siendo el mismo objeto (`assertSame`) tras `upsert`, `moveEntries` y `markUsed`, porque una decodificación crea objetos nuevos; y lo abierto coincide con el disco tras bloquear y abrir.
  - `PlatformSourcesTest` fija `read = saved)` y `withContext(compute) { VaultCrypto.read(next, key) }`.

**M-1. Textos de búsqueda sin tope.**
- Qué cambia:
  - `SEARCH_TEXT_MAX = 200` y `searchText()` (`EntryQuery.kt:58`, `:61`). Se aplican en el cuadro de búsqueda (`VaultList.kt:350`), al leer una vista (`apply`, `isOn`, `:199-202`; una de una copia se recorta, no se rechaza), al guardarla (`Folders.kt:250`) y en `FuzzySearch.prepare` (`FuzzySearch.kt:74`), como última defensa.
  - El `ensureActive` opcional no se puso: con el tope, el trabajo ya está acotado.
- Test: `OrganizeStoreTest.searchAndViewTextsAreCapped`.

**M-2. `SEARCH_MORE` incompleto.**
- Qué cambia: nombra «el servicio y la cuenta del 2FA» y «una [errata] en el principio de una palabra mientras escribes» (`OrganizeTexts.kt:12`).

**M-3. Faltaban los límites de las erratas.**
- Qué cambia: se corrige el comentario de «netflx» (6 letras).
- Test: `FuzzySearchTest.typoLimitsAtTheEdges`. Comprueba `allowedEdits` de 1 a 9 letras, 3 letras con una errata (no), 4 con una (sí), 7 con dos (no) y con una (sí), y 8 con dos (sí) y con tres (no).

**M-4. La posición sobrevivía al bloqueo.**
- Qué cambia: el `onDispose` solo devuelve la posición al ViewModel con la bóveda abierta (`VaultList.kt:303`).
- Test: lo fija `PlatformSourcesTest`.

**M-5. «Con subcarpetas» se quedaba apagado.**
- Qué cambia:
  - Al quitarse una carpeta que ya no está, «Con subcarpetas» vuelve a encenderse (`VaultList.kt:296`).
  - `unfiltered()` y «Recientes» ya no miran «Con subcarpetas» sin carpeta (`plain`, `:192`).

**M-6. Faltaban tests de `markUsed`.**
- Tests:
  - `VaultV4Test.markUsedWhileTheUpgradeWaits`: da `false`, sin lanzar, y el archivo sigue siendo el v3.
  - `OrganizeStoreTest.markUsedWithAFailedWrite`: la bóveda sigue abierta, el uso se queda en memoria y el siguiente guardado que funciona lo escribe; otra tienda sobre el mismo archivo lo ve. Esto corrige lo que suponía mi primer test: el archivo en memoria conserva el uso, como la última comprobación de la maestra.
  - `OrganizeStoreTest.markUsedCutByALock`: un bloqueo mientras se escribe deja la bóveda bloqueada, y el desbloqueo siguiente ve el uso guardado.

**M-7. El principio de palabra iba más allá del diseño.**
- Qué cambia:
  - `distances` da la distancia a la palabra entera y al principio más cercano (`FuzzySearch.kt:154`).
  - Primero cuenta la palabra entera, con las erratas que permite su longitud. Si no, el principio de una palabra, con `PREFIX_EDITS = 1` como mucho (`:130`) y puntuado desde `PREFIX_BASE = 200` (`:28`, `:31`), por detrás de toda coincidencia de palabra entera.
  - Nota de corrección en el diseño, sección de la búsqueda (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:96`).
- Test: `FuzzySearchTest.startOfAWordOneTypoAndLast`. Comprueba que «gmia» encuentra «Gmail», que «contrato» no encuentra «Contraseña del banco», y que una palabra entera con dos erratas sale antes que un principio con una.

**M-8. Fondos copiados a mano en el test de colores.**
- Qué cambia: `LightColors` y `DarkColors` pasan a `internal` (`ui/theme/Theme.kt:25`, `:59`).
- Test: `ui/TagPaletteThemeTest`, que solo corre en `:app`. Mide cada color sobre las 9 superficies de cada `ColorScheme` (`surface`, `background`, `surfaceVariant`, `surfaceContainer*` y `secondaryContainer`) y comprueba que los fondos de `TagPalette` son los del tema.

**Lo que queda fuera:**
- «Mover a…» en un menú de la fila (diseño, «Carpetas anidadas»): se apunta para la tarea 6, con la selección múltiple y el menú rápido.
- La medida de «Salud» con cada cambio de la bóveda, en el móvil: va a la revisión final.

## Revisión experto-seguridad-cripto (ronda 1)

- **Disponibilidad ante datos hostiles** (decisión (c) de la 1.4.0):
  - Una etiqueta normal ya no cierra la app (C-1, `VaultList.kt:461`).
  - Una copia con ids repetidos se rechaza antes de entrar (`VaultLimits.kt:208-210`, que llega a `COPY_DAMAGED` por `checkCopyStructure`) y ya no puede colgar la tienda con el mutex tomado (`Folders.kt:88`).
  - Los textos de búsqueda y de vista tienen tope (`EntryQuery.kt:58-61`, `FuzzySearch.kt:74`).
  - Todo con tests de la copia hostil, también a través de `importReplace`.
- **Integridad de lo abierto (I-2):**
  - Lo que se abre tras un guardado es lo que se acaba de cifrar y escribir: `fittedForSave` + `withPlainVersions` es la misma normalización que hace `VaultCrypto.read`. Es la regla de la apertura con la huella, «what is written is what opens».
  - Un bloqueo a mitad sigue dejando la bóveda bloqueada y la clave borrada: `commit` no descifra si la época cambió, y `opened` decide (`VaultStore.kt:441`). Lo cubre `markUsedCutByALock`.
  - Ningún descifrado del vault entero queda en el hilo de quien llama, en esos caminos.
- **Veracidad:** `SEARCH_MORE` dice todo lo que se busca, también el principio de palabra (M-2), y coincide con `EntryQuery.kt` y `FuzzySearch.kt`.
- Sin cambios de formato, claves, red, permisos ni dependencias.

## Revisión Ingeniero Móvil Senior (ronda 1)

- **Compose:** cada clave de lista perezosa es única, porque las de datos llevan un espacio propio (`lazyKey`) y las fijas no llevan «:». Lo fija `PlatformSourcesTest`.
- **Rendimiento:**
  - Sin la segunda decodificación, `upsert` y `markUsed` cerca del tope bajan a unos 430 ms en la JVM, y no hay trabajo de decodificación en el hilo principal (`VaultStore.kt:791`).
  - El orden de la lista en `opened` sigue en el hilo de quien llama: 2 000 elementos, ya decodificados, unos milisegundos.
  - `branchHeight` es lineal.
- **Ciclo de vida:**
  - La posición se borra al bloquear (M-4, `VaultList.kt:303`).
  - El filtro vuelve a «sin filtros» cuando desaparece la carpeta que se veía (M-5, `:192`, `:296`).
- **Accesibilidad:** el contraste de los colores se mide ahora contra los temas reales (`TagPaletteThemeTest`), así que un cambio de tema en la tarea 10 lo notará.

## Textos para el Abogado Digital (revisión final), ronda 1

- `SEARCH_MORE` cambia: «Busca en el nombre, el usuario, la web (solo su dominio), el servicio y la cuenta del 2FA, las etiquetas, las carpetas y los nombres de los campos que no están ocultos. Nunca en las contraseñas, los campos ocultos, las notas ni las palabras de una frase semilla. Perdona una errata en palabras de 4 a 7 letras y dos desde 8, y una en el principio de una palabra mientras escribes; primero salen las que coinciden tal cual.»
- Nuevo (`VaultLimits.kt`): «Las carpetas o las vistas de la bóveda están dañadas.». No llega a verse al importar ni al comprobar una copia, porque allí se enseña «La copia está dañada: no se puede importar»; solo sería visible en un guardado de una bóveda así, que la app no crea.
