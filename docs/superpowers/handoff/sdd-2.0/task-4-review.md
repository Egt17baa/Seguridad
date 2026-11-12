# Revisión de la tarea 4 de Clavero 2.0: carpetas, vistas, etiquetas de color, ordenar, filtros y búsqueda con erratas

Rango `d24fdc2..76642a8` (`d1464ae` es el commit del registro del controlador, con los cuatro «Ruling (T4…)»). Revisé el diff entero. Fuera de él solo miré los riesgos con nombre:
- `VaultStore.kt`: `commit`, `opened`, `saveBookkeeping`, `modifyData`, `addAll` y la apertura con la huella (`:618-639`, como referencia de M-5);
- `VaultLimits.kt`: `checkStructure` y `fittedForSave`;
- `BackupCheck.kt`: `checkCopyStructure`;
- `AppNavHost.kt`: `VaultScoped`;
- `ui/theme/Theme.kt`, para los fondos de `TagPalette`.

Leídos: `CLAUDE.md`, el brief, el diseño (carpetas, vistas, colores, ordenar, filtros, búsqueda, O-2 y R-1), el registro con todos sus «Ruling» y el informe del implementador. De `experto-seguridad-cripto` leí `SKILL.md` y `references/auditoria.md` (la tarea no toca criptografía; apliqué su lista de entradas hostiles y de DoS). De Ingeniero Móvil Senior, `desarrollo-movil-senior` y `auditoria-seguridad-movil` (MASVS-STORAGE, -PLATFORM y -CODE).

Tests ejecutados (`export ANDROID_HOME=/home/user/android-sdk`): `FoldersTest` (8), `FuzzySearchTest` (8), `EntryQueryTest` (13), `OrganizeStoreTest` (4), `TagPaletteTest` (3), `PlatformSourcesTest` (31) y `HealthTest` (17). Todos en verde, sin fallos ni errores. Medidas en la JVM: búsqueda con erratas en 2 000 entradas, 2,4 ms; índice nuevo, 35,6 ms; índice tras guardar, 3,7 ms; `upsert` y `markUsed` con la bóveda en 23 230 KiB, unos 790 ms cada uno.

### Spec Compliance

**Cumple:**
- **API del brief:**
  - `createFolder` (da `Folder?`: null con la bóveda bloqueada), `renameFolder`, `moveFolder`, `deleteFolder` con `FolderDeletion { PARENT, TRASH }`, `moveEntries` (da cuántas, `Int`), `saveView`, `deleteView`, `setTagColor` y `markUsed` (`VaultStore.kt:940-985`). Cada operación es un solo `modifyData`, con el mutex.
  - `folderPath` y `descendants` (`Folders.kt:55`, `:69`), `FuzzySearch.score` (`FuzzySearch.kt:68`) y `List<VaultEntry>.query(filter, data, now, weak, …)` (`EntryQuery.kt:151`).
  - Las desviaciones de tipo (`Folder?` e `Int`) son razonables.
- **Todos los tests del brief existen con su nombre:**
  - `FoldersTest`: los 6;
  - `FuzzySearchTest`: los 5 y `twoThousandEntriesUnder50ms`;
  - `EntryQueryTest`: los 5.

  Hay además pruebas de hostilidad (`hostileInputsDoNotThrow`, el ciclo de `cycleRefused`) y la medida M-6 de la tarea 1.
- **Carpetas:**
  - 500 carpetas; 5 niveles, contando la rama movida (`Folders.kt:173-174`, `branchHeight`); sin ciclos (`:169`, y `checkStructure` en cada guardado y en `checkCopyStructure`).
  - Nombres comparados con `compareKey` y sin mayúsculas entre hermanos, guardados tal cual, solo recortados (`Folders.kt:43`, `:121-135`).
  - Borrar a PARENT o a TRASH. Lo de la papelera solo recibe `deletedAt`, como `moveToTrash`.
  - Una vista que miraba una carpeta borrada pasa a la más cercana de arriba (`Folders.kt:203-207`).
  - Una entrada restaurada vuelve a su carpeta si sigue, y si no a ninguna (`VaultStore.kt:1038`).
- **Copia hostil:**
  - Un ciclo hace que la copia se rechace como dañada (`BackupCheck.kt:178-184`).
  - Un padre que falta cuenta como carpeta de arriba en todas las funciones (`folderTree`, `checkFreeName`, `withoutFolder`, `checkStructure`).
  - `folderPath`, `descendants` y `folderTree` nunca dan vueltas: usan conjuntos de vistos.
- **Búsqueda:**
  - Lee el título, el usuario, el host (`searchHost`, nunca el usuario ni la clave del enlace), el servicio y la cuenta del 2FA (ruling T4, dudas menores), las etiquetas, la carpeta y las de arriba, y los nombres de los campos no ocultos (`EntryQuery.kt:118-131`).
  - Nunca lee contraseñas, campos ocultos (ni su nombre), notas, la frase semilla ni el secreto del 2FA (`EntryQueryTest.neverSearchesPasswordsHiddenFieldsOrNotes`, también con erratas).
  - Va fuera del hilo principal: `withContext(Dispatchers.Default)` con `collectLatest` (`VaultList.kt:201-211`).
- **Erratas:**
  - Las coincidencias exactas van antes que las de erratas: siempre por debajo de `FUZZY_BASE`.
  - Damerau-Levenshtein (OSA): ≤ 1 de 4 a 7 letras y ≤ 2 desde 8. Con menos de 4 letras, solo coincidencias exactas.
  - Sin acentos, con `compareKey`. Más de 64 puntos de código, solo exacta (`FuzzySearch.kt:100`, `:108`).
  - La poda por filas es correcta también con trasposiciones: el mínimo de cada fila no baja.
- **Orden** por nombre (`Collator` es-ES primario, con `CollationKey` precalculadas), modificación, creación y uso (`EntryQuery.kt:183-189`).
- **Lista:**
  - «Recientes» (las 5 con el `lastUsedAt` más alto) y «Favoritos» arriba, solo sin filtros. Debajo, «Todas».
  - Fichas de un toque: tipos, Favoritas, Débiles, Sin 2FA y Caducan pronto.
  - Fichas de vistas, selector de orden y árbol de carpetas.
  - Arrastre con asa a una barra de carpetas, con la acción de TalkBack «Mover a…» y «Mover a…» en la ficha.
- **Bloqueo y cambio de bóveda:**
  - Al bloquear se borran el texto, el filtro, la caché con el índice y el resultado (`VaultList.kt:199`, `:237-244`). Un resultado tardío se descarta (`:210`).
  - Al cambiar de bóveda, `VaultScoped` tira la tienda de ViewModels (`AppNavHost.kt:143-145`).
  - Nada de la lista va en `rememberSaveable` ni en un `Bundle`: `PlatformSourcesTest` lo comprueba también en `VaultOrganize.kt`.
- **`markUsed` (ruling T4, duda 1):**
  - Sin versión: `withUsed` solo toca `lastUsedAt`.
  - Sin subir `revision` y sin cambiar la hora del archivo: `commit(mustSave = false)` y `saveBookkeeping` (`VaultStore.kt:402-406`, `:768`).
  - Sin copia automática.
  - Mientras espera el paso a v4, da `false` sin guardar (`:751`).
  - Un fallo del disco no cierra la bóveda (`commit`, `:433-438`), y quien llama lo envuelve en `runCatching` (`VaultEntryScreen.kt:165`).
  - Una escritura tardía nunca cae en otra bóveda: cada `VaultStore` es de una bóveda, `since`/`epoch` deja bloqueada la que se bloqueó, y el cambio hace `lock` + `awaitIdle`.
  - Como mucho una escritura por entrada y minuto.
  - ⚠️ No cumple «no bloquea la interfaz» en una bóveda grande: ver I-2.
- **Colores:** 8 colores fijos con contraste ≥ 4,5:1 sobre 4 fondos por tema (`TagPaletteTest`). El nombre de la etiqueta se ve siempre, y el selector nombra cada color (R-1).
- **Texto plano (R9):** los nombres de carpetas, vistas y etiquetas, la ruta, la entrada arrastrada y los avisos «Movida a…» pasan por `plainReportText`, y `PlatformSourcesTest` R9 lo fija. Sin enlaces, HTML ni `Linkify`.

**⚠️ No cumple o queda a medias:**
- ⚠️ **La lista se cae con una etiqueta normal** (C-1). Con una copia hostil se cae igual (I-1).
- ⚠️ **Ruling T4, duda 1 («no bloquea la interfaz»):** cada `markUsed` vuelve a descifrar y decodificar la bóveda entera en el hilo principal (I-2).
- ⚠️ **«La posición se borra al bloquear»** (brief, paso 3): no se cumple. La posición sobrevive al bloqueo (M-4). El fallo ya estaba en la 1.4.x; la tarea mueve el código tal cual.
- ⚠️ **«Damerau-Levenshtein por palabra»:** el código compara además con cualquier principio de la palabra (`prefixDistance`), y eso amplía bastante los resultados. Ningún «Ruling» lo recoge (M-7).
- ⚠️ **«Mover a…» en el menú** (diseño, «Carpetas anidadas»): la fila no tiene menú visible, solo la acción de TalkBack y el botón de la ficha. La selección múltiple y el menú rápido son de la tarea 6. Basta apuntarlo para esa tarea.

**Extra, todo justificado:**
- `upsert` conserva `folderId` y `lastUsedAt`, y `restoreVersion` conserva la carpeta (`VaultStore.kt:806`, `:1085`).
- `fittedForSave` deja pasar una entrada vieja de más de 64 KiB que solo cambió de carpeta, de último uso o de papelera.
- «Sin 2FA» de «Salud» ya cuenta el 2FA propio del acceso.
- `searchHost`.

He comprobado que el cambio de `fittedForSave` no deja colar nada:
- `sameButPlace` (`VaultLimits.kt:176-177`) compara la entrada entera salvo esos tres campos, con la igualdad del `data class`, así que cualquier cambio de contenido, de historial o de adjuntos vuelve a pasar por `checkLimits`.
- El tope de 24 MiB y `checkStructure` se comprueban siempre (`:169-171`).
- `restoreVersion` cambia `updatedAt`, así que nunca cuenta como «solo de sitio».

### Strengths

- **La lógica es pura y está bien separada.** `Folders.kt`, `FuzzySearch.kt`, `TagPalette.kt` y `EntryQuery.kt` no dependen de Android: jvm-check los compila y la suite los prueba sin emulador. La tienda solo envuelve con `modifyData`, una sola lectura bajo el mutex (decisión (b)).
- **Las invariantes de las carpetas se comprueban dos veces:** antes del cambio, con un texto fijo de qué hacer, y en el guardado con `checkStructure`. Esto también vale para lo que llega de una copia (`checkCopyStructure`).
- **El índice de búsqueda está bien pensado:**
  - se construye una vez por cambio de la bóveda;
  - después de un guardado reaprovecha los textos normalizados, los hosts y las claves del `Collator` (3,7 ms con 2 000 entradas);
  - cada tecla solo puntúa (2,4 ms);
  - todo vive en el ViewModel y se tira al bloquear.
- **`searchHost` nunca da el usuario ni la clave de un enlace** (`MergePlan.kt:519-524`), y el test lo prueba con `https://ana:gatito@banco.es/gatito?gatito=1`, tal cual y con erratas.
- **`markUsed` aprovecha bien el guardado «de contabilidad»** que ya existía. No crea versión ni sube `revision`, conserva la hora del archivo, no lanza mientras espera el paso a v4 y tiene su límite por minuto. El test comprueba también que el uso sobrevive a bloquear y abrir.
- **`upsert` ya no saca una entrada de su carpeta** al editarla, y lo restaurado de la papelera no apunta a una carpeta que no está. Son dos fallos que la tarea encontró y cerró, con su test.
- **Los diálogos solo guardan ids** (`OrganizeDialog`), se cierran en un efecto cuando su objeto desaparece (`Gone`) y todos llaman a `FilterObscuredTouches()`.
- **Los colores tienen el contraste comprobado** y nunca son la única señal.

### Issues

#### Critical

**C-1. Una etiqueta llamada «colors» tumba la lista de la bóveda en cada apertura** (`VaultList.kt:381-390`).
- **Qué pasa.**
  - La fila de etiquetas es un `LazyRow` con `items(result.tags, key = { it })`, seguido de `item(key = "colors")` para la ficha «🎨 Colores».
  - Las claves de un `LazyRow` tienen que ser únicas. Con dos elementos de la misma clave en pantalla, Compose lanza `IllegalArgumentException: Key "colors" was already used…` al medir, y la app se cierra.
  - Una etiqueta se guarda en minúsculas (`normalizeTags`), así que «Colors», «COLORS» o «colors» dan la clave `colors`.
- **Escenario.**
  1. El usuario etiqueta una entrada con «colors», o la trae de otra app con «Importar de otra app».
  2. Guarda y vuelve a la lista: la app se cierra.
  3. La abre, desbloquea y la lista es lo primero que ve: se vuelve a cerrar.

  `result.tags` sale de todas las entradas, no de las que se ven, así que ni buscar ni filtrar lo evita. Desde la lista no hay forma de quitar la etiqueta. Solo se libra quien tiene tantas etiquetas que las dos fichas nunca caen a la vez en pantalla. La pestaña «Códigos» no pinta esa ficha (`!totpOnly`).
- **Lo mismo pasa con las vistas** (`VaultList.kt:369`, `:373`): `items(views, key = { it.id })` comparte la fila con `item(key = "manage")`. El usuario no puede crear una vista con el id `manage`, porque los ids son UUID, pero una copia hostil sí (I-1).
- **Arreglo.**
  - Dar a cada clave un prefijo propio en las dos filas: `key = { "t:$it" }` para las etiquetas y `key = { "v:${it.id}" }` para las vistas. Las fichas fijas quedan como `"colors"` y `"manage"`, que no pueden chocar.
  - Hacer lo mismo en `ViewsDialog` (`VaultOrganize.kt:372`) y `TagColorsDialog` (`:427`), aunque allí no haya claves fijas, para que el patrón sea uno.
  - Fijarlo con un test de código fuente en `PlatformSourcesTest`: ninguna `key =` de un `items(...)` sobre datos de la bóveda sin prefijo. Mejor aún, una función `lazyKey(kind, id)` y un test.

#### Important

**I-1. Una copia hostil con ids de carpeta o de vista repetidos pasa la comprobación y luego tumba la lista o cuelga la tienda** (`VaultLimits.kt:197-215`, `BackupCheck.kt:178-184`, `Folders.kt:85-90`).
- **Qué pasa.** `checkStructure` cuenta las carpetas y los niveles, y busca ciclos con `parent.putIfAbsent`, que se queda con la primera carpeta de cada id. Nunca rechaza ids repetidos, ni de carpetas ni de vistas. Una copia con marca 3, hecha por quien conoce su contraseña (decisión (c): lo importado es hostil), pasa `checkCopyStructure` y queda guardada así. Tiene dos efectos:
  1. **Vistas con el mismo id**, o con el id `manage`: la fila de vistas (`VaultList.kt:369`) y `ViewsDialog` (`VaultOrganize.kt:372`) repiten clave, y la app se cierra en cada apertura (como en C-1). El arreglo de C-1 con prefijos no basta: dos vistas con el mismo id siguen dando la misma clave.
  2. **Carpetas con el mismo id:** `branchHeight` recorre todos los caminos, porque `children` agrupa por `parentId` y mete todas las carpetas que tienen ese id. Ejemplo:
     - 100 carpetas con id `A` arriba, 100 con id `B` y padre `A`, 100 `C`, 100 `D` y 100 `E`;
     - son 500 carpetas y 5 niveles: `checkStructure` las acepta;
     - al mover una carpeta `A`, `branchHeight` hace unas 100⁴ = 10⁸ llamadas, cada una con una copia de un `Set`.

     Son decenas de segundos con el mutex de la tienda tomado, dentro de `withContext(compute)`, sin punto de cancelación: ningún guardado avanza mientras dura. `folderTree` y `descendants` no tienen el problema, porque descartan lo ya visto.
- **Arreglo.**
  - En `checkStructure`: rechazar ids de carpeta repetidos y de vista repetidos. Así `checkCopyStructure` da `COPY_DAMAGED`, como con un ciclo, y el texto ya existe.
  - En `branchHeight`: memoizar por id, o recorrer en anchura con un conjunto de vistos, como `descendants`.
  - Tests:
    - `duplicateFolderIdsRefused` y `duplicateViewIdsRefused` en `FoldersTest` u `OrganizeStoreTest`, con `checkCopyStructure`;
    - un `branchHeight` sobre 500 carpetas repetidas que termine en menos de, por ejemplo, 100 ms.

**I-2. Cada `markUsed` descifra y decodifica la bóveda entera en el hilo principal** (`VaultStore.kt:768`, `:425-442`, `:449-453`; ruling T4, duda 1).
- **Qué pasa.**
  - `modifyData` hace `change`, `fittedForSave` y `VaultCrypto.write` en `compute`, pero `commit` (`withContext(NonCancellable)`, sin cambio de hilo) llama a `opened(next, key, since)` con `read = null`.
  - `opened` hace entonces `VaultCrypto.read(v, key)` (AES-GCM, la comprobación de anidado y la decodificación JSON de hasta 24 MiB) y ordena las entradas, todo en el hilo de quien llama.
  - `markUsed` se lanza desde `rememberCoroutineScope()` en la ficha (`VaultEntryScreen.kt:165`), es decir, desde `Dispatchers.Main`.
- **Escenario.**
  1. Una bóveda cerca del tope: 2 000 notas de 11,8 KB, como en `upsertNearTheCapReference`.
  2. El usuario abre una entrada y toca «Copiar» en el usuario.
  3. Tras el cifrado y la escritura (fuera del hilo principal), el hilo principal decodifica unos 23 MiB.

  En la JVM, el guardado entero cuesta unos 790 ms, y la relectura es una parte grande. En un móvil medio es varias veces más: un tirón visible, y cerca de un ANR si se suman otros guardados. Y no lo provoca una acción de guardar, sino copiar un campo.
- **Es anterior a la tarea,** porque todos los guardados por `modifyData` releen así. Pero en la 1.4.x la bóveda era mucho más pequeña, y con el tope de 24 MiB de la tarea 1 y `markUsed` se nota mucho más. El ruling pide justo esta comprobación.
- **Arreglo.**
  - Que `modifyData` conserve los datos ya ajustados y los pase a `commit(..., read = fitted)`, como ya hace la apertura con la huella (`VaultStore.kt:634-638`, revisión M-5: «what is written is what opens»). Así no hay segunda lectura.
  - Si se quiere conservar la comprobación de leer lo escrito, hacer `VaultCrypto.read` en `withContext(compute)` dentro de `commit`, antes de `opened`.
  - Test: un `VaultStore` con un `compute` que apunte en qué hilo corre, y que compruebe que ni `VaultCrypto.read` ni la decodificación pasan por el hilo de quien llama. O, al menos, un test de código fuente que fije `read =` en la llamada de `modifyData`.

#### Minor

**M-1. El texto de búsqueda y el de una vista no tienen tope** (`VaultList.kt:337`, `:193-196`; `Folders.kt:236-246`; `FuzzySearch.kt:98-116`).
- **Qué pasa.**
  - `onValueChange = { list.query = it }` acepta cualquier longitud.
  - `withView` comprueba el nombre, pero no `filter.text`.
  - `checkStructure` tampoco lo mira, así que una vista de una copia puede traer megas de texto.
- **Escenario.**
  1. Una copia hostil con una vista cuyo texto es «gmial» repetido 200 000 veces.
  2. Al tocar su ficha, `apply` pone el texto en un `OutlinedTextField` de una línea, y su maquetación en el hilo principal congela la app.
  3. Además, `compute` evalúa 200 000 palabras contra cada palabra de cada entrada (son erratas, así que no corta), sin ningún punto de cancelación. `collectLatest` espera a que termine antes de atender la tecla siguiente.

  Un usuario que pega un texto largo tiene el mismo problema, en pequeño.
- **Arreglo.**
  - Un tope de, por ejemplo, 200 puntos de código para el texto de búsqueda: `takeCodePoints` en `onValueChange` y en `apply(view)`.
  - El mismo tope en `withView`, como `OrganizeRefused`, o recortando.
  - Recortar al leer una vista de una copia: no rechazarla, para no romper la regla de abrir siempre.
  - Opcional: un `ensureActive()` por entrada en `compute`.

**M-2. `SEARCH_MORE` no dice todo lo que se busca** (`OrganizeTexts.kt:12-16`).
- El texto enumera dónde busca: «el nombre, el usuario, la web (solo su dominio), el servicio del 2FA, las etiquetas, las carpetas y los nombres de los campos…».
- El código busca también la cuenta del 2FA (`EntryQuery.kt:125`), que el ruling T4 (dudas menores) acepta.
- La regla de veracidad del proyecto pide que la lista sea completa: decir «el servicio y la cuenta del 2FA».

**M-3. Faltan los límites exactos de las erratas en los tests** (`FuzzySearchTest.kt:57-59`).
- El comentario «7 letters: still one» usa «netflx», que tiene 6 letras.
- Ningún test prueba los límites:
  - una palabra de 7 letras con 2 erratas, que no debe encontrarse;
  - una de 8 letras con 2 erratas, que sí;
  - una de 4 letras con 1 errata frente a una de 3 con 1, que existe a medias (`gmal` y `bvv`).

  Un fallo de uno en `allowedEdits`, como `length <= 8`, no lo pararía nada.
- **Arreglo:** añadir pares exactos en los límites, por ejemplo 7 letras con dos trasposiciones da `null` y 8 letras con dos erratas da un resultado, y corregir el comentario.

**M-4. La posición de la lista sobrevive al bloqueo** (`VaultList.kt:289-295`; ya estaba en `VaultScreen.kt` de la 1.4.x).
- **Qué pasa.**
  1. Al bloquear, `reset()` pone `firstIndex = 0` y `firstOffset = 0` (`:237-241`).
  2. Justo después, la lista sale de la composición (`VaultGate`), y el `onDispose` de `DisposableEffect(listState)` vuelve a escribir la posición de antes.
- **Escenario.** El usuario baja hasta la entrada 300 y la bóveda se bloquea. Al abrirla, la lista sale otra vez a esa altura. El brief (paso 3) y F-4 de la 1.4.1 dicen que la posición se borra al bloquear, y el test de código fuente dice «drops them on lock». No es un secreto, porque son dos números, pero el requisito escrito no se cumple.
- **Arreglo:** en el `onDispose`, guardar solo si `store.state.value is VaultState.Unlocked`, y fijarlo en `PlatformSourcesTest`.

**M-5. Al desaparecer la carpeta que se veía, «Con subcarpetas» puede quedarse apagado y la lista parece filtrada** (`VaultList.kt:281-287`).
- **Qué pasa.** El `LaunchedEffect(result)` quita la carpeta que ya no está con `f.copy(tags = …, folderId = folder)`, pero conserva `includeSubfolders = false`. Entonces:
  - `unfiltered()` (`:188`) compara con `initialFilter()`, que lo tiene a `true`, y da `false`;
  - «Recientes» y «Favoritos» desaparecen;
  - aparece «Guardar vista», aunque ninguna ficha esté marcada.

  «Quitar carpeta» y `DeleteFolderDialog` sí lo vuelven a poner a `true`, pero el diálogo solo lo hace si la carpeta borrada es justo la que se veía.
- **Escenario.**
  1. El usuario abre la subcarpeta «B», apaga «Con subcarpetas», abre «Carpetas» y borra «A», su madre, a la papelera.
  2. «B» se va con ella y la lista vuelve a toda la bóveda, pero sin «Recientes» y con «Guardar vista».
- **Arreglo:** al quitar la carpeta, `includeSubfolders = true`, o que `unfiltered()` no mire `includeSubfolders` cuando `folderId == null`.

**M-6. Faltan tests de lo que pide el ruling T4, duda 1** (`OrganizeStoreTest.kt:40-67`).
- Hay tests de que no crea versión, no sube la revisión, no cambia la hora del archivo, respeta el límite por minuto, no toca la papelera y no funciona bloqueada.
- Faltan:
  1. `markUsed` mientras espera el paso a v4: da `false`, el archivo sigue siendo el v3 y no lanza `UPGRADE_PENDING`.
  2. Un fallo al escribir, con el archivo o la carpeta de solo lectura: la bóveda sigue abierta y el uso queda en memoria.
  3. Un bloqueo entre el cambio y `commit`: queda bloqueada y el siguiente desbloqueo ve el uso guardado.

  El código cumple los tres, pero nada lo protege si cambia. Con el arreglo de I-2, el punto 2 importa todavía más.

**M-7. La comparación con el principio de la palabra va más allá del diseño, y ningún «Ruling» lo recoge** (`FuzzySearch.kt:130-160`).
- El diseño y el brief dicen «Damerau-Levenshtein por palabra». `prefixDistance` mide la distancia con el principio más cercano de la palabra.
- Ayuda mientras se escribe («amazn» encuentra «Amazon»), pero amplía mucho las erratas de las palabras largas. Por ejemplo, «contrato» (8 letras, 2 erratas) encuentra cualquier entrada con «contraseña», porque está a 2 de «contras»; y «casa» encuentra «cascada».
- El informe lo cuenta en «Qué se ha hecho», pero no en «Dudas», y el controlador no lo ha decidido.
- **Arreglo:** que el controlador lo decida. Si se mantiene:
  - apuntarlo en el registro;
  - decirlo en `SEARCH_MORE` (por ejemplo, «…y encuentra también el principio de una palabra»);
  - dar a la errata del principio un coste mayor, para que la palabra entera con una errata salga antes.

  Si no, comparar solo con la palabra entera, y con su principio solo para la última palabra que se está escribiendo.

**M-8. `TagPaletteTest` mide contra fondos copiados a mano, que no se comprueban contra el tema** (`TagPalette.kt:39-42`).
- `LIGHT_BACKGROUNDS` y `DARK_BACKGROUNDS` repiten a mano los valores de `ui/theme/Theme.kt`, y ningún test los liga.
- Hoy coinciden, y el contenedor de los diálogos, `surfaceContainerHigh` oscuro `#1C2128`, queda entre dos fondos medidos.
- La tarea 10 cambia los temas (alto contraste, colores de acento). Si cambia un fondo, este test seguiría en verde sin medir nada nuevo, contra R-1 («contraste también con el color dinámico»).
- **Arreglo:** que el test lea los `ColorScheme` claro y oscuro de `Theme.kt`, haciéndolos `internal`, y mida contra `surface`, `background`, `surfaceVariant`, `surfaceContainer*` y `secondaryContainer`. Como mínimo, que compruebe que las listas coinciden con el tema.

**Veredicto: spec ❌ (C-1, I-2 y M-4 frente a requisitos escritos), quality Needs fixes.** 1 Critical, 2 Important y 8 Minor.

### Revisión experto-seguridad-cripto

Lista: `references/auditoria.md`, con la entrada hostil de la decisión (a) y la (c) de la 1.4.0, y la regla de veracidad. La tarea no toca formato, claves, KDF ni AAD, y no hay criptografía nueva.

- **Confidencialidad de la búsqueda: correcta.** El índice solo toma texto no secreto (`EntryQuery.kt:118-131`), y el host sale sin el usuario ni la clave del enlace (`MergePlan.kt:519-524`). Los tests lo prueban tal cual y con erratas (`EntryQueryTest.kt:109-128`, `FuzzySearchTest.kt:131`). El índice y el resultado viven solo en el ViewModel y se tiran al bloquear (`VaultList.kt:237-244`).
- **Integridad:**
  - `upsert` no deja que el editor cambie la carpeta ni el último uso (`VaultStore.kt:806`).
  - Una entrada personal nunca tiene carpeta (`:789`).
  - Una entrada nueva solo entra en una carpeta que existe (`:792-793`).
  - El cambio de `fittedForSave` no deja colar contenido: lo compara todo salvo esos tres campos (`VaultLimits.kt:176-177`), y el tope de 24 MiB y `checkStructure` siguen comprobándose siempre (`:169-171`).
- **Entrada hostil (decisión (a)):**
  - Bien acotadas: Damerau-Levenshtein hasta 64 puntos de código, con poda (`FuzzySearch.kt:100`, `:151`); ciclos y padres que faltan (`Folders.kt:55-117`, `VaultLimits.kt:205-213`).
  - Sin acotar: los ids repetidos (I-1: la app se cierra y la tienda se cuelga, `Folders.kt:85-90`, `VaultLimits.kt:202-203`) y el texto de una vista (M-1). Lo de I-1 es exactamente lo que la decisión (c) pide cerrar antes de que la copia entre.
- **Disponibilidad:**
  - C-1 deja al usuario sin acceso a su lista por una etiqueta normal. No expone ningún dato, pero bloquea la app.
  - I-2 es de disponibilidad y de rendimiento, no de confidencialidad.
- **`markUsed`:** no sube `revision` ni toca la hora del archivo (`VaultStore.kt:402-406`, `:768`), así que no provoca copias de más ni hace creer que hay una copia pendiente. Un bloqueo a mitad deja la bóveda bloqueada y la clave borrada (`opened`, `:451-461`). La copia de la clave del guardado en curso se borra al terminar, como en los demás guardados.
- **Veracidad de los textos:** a `SEARCH_MORE` le falta la cuenta del 2FA (M-2), y el principio de palabra (M-7) no está decidido. `FOLDER_DELETE_TRASH_NOTE` («durante 30 días, sin carpeta») es veraz con la regla de la tarea 3 y con `restore` (`VaultStore.kt:1038`).
- Sin red, permisos, dependencias ni registros nuevos.

### Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - El cálculo de la lista va en `Dispatchers.Default` con `collectLatest`, que cancela el anterior y espera a que acabe (`VaultList.kt:201-211`). La caché la toca una sola ejecución cada vez.
  - Pero `compute` no tiene ningún punto de cancelación, así que una búsqueda cara retrasa la siguiente (M-1).
  - El guardado del uso relee la bóveda en el hilo principal (I-2: `VaultStore.kt:425-453`, llamado desde `VaultEntryScreen.kt:165`).
- **Compose:**
  - Las claves de los `Lazy*` chocan: el dato de usuario y la clave fija comparten espacio (C-1: `VaultList.kt:382`, `:390`, `:369`, `:373`).
  - Las filas de la lista llevan prefijo (`r:`, `f:`, `a:`) y están bien.
  - `folderTree` no repite carpetas, así que las claves de `FoldersDialog` y `FolderPicker` (`VaultOrganize.kt:188`, `:289`) son únicas.
- **Ciclo de vida:**
  - Bien: el estado de la lista está en el ViewModel, la tienda de ViewModels es por bóveda y se tira al cambiar (`AppNavHost.kt:133-147`), y los diálogos se cierran en un efecto (`VaultOrganize.kt:97-100`).
  - Mal: la posición se vuelve a escribir después del `reset()` (M-4: `VaultList.kt:290-295`), y «Con subcarpetas» se queda colgado (M-5: `:281-287`).
- **Rendimiento:**
  - El índice es bueno: incremental, con claves de `Collator` precalculadas y 2,4 ms por tecla con 2 000 entradas en la JVM.
  - Cada cambio de la bóveda, también cada `markUsed`, recalcula «Salud» entera (`vaultHealth`, `VaultList.kt:219-221`), también en la pestaña «Códigos», que no usa «Débiles» ni «Sin 2FA». Va fuera del hilo principal y es aceptable; basta medirlo en el móvil con el resto (revisión final).
- **Accesibilidad (R-1):**
  - El arrastre tiene alternativa: la acción «Mover a…» (`VaultList.kt:518`) y el botón de la ficha (`VaultEntryScreen.kt:241`).
  - El asa tiene `contentDescription` y mide 32 dp, por encima de los 24 de WCAG 2.2 AA (2.5.8).
  - Los colores tienen nombre y contraste comprobado; falta ligarlo al tema (M-8).
- **MASVS-STORAGE:** nada de la lista va en `rememberSaveable` ni en un `Bundle` (`PlatformSourcesTest`, `VaultList.kt` y `VaultOrganize.kt`). La organización vive cifrada dentro de `VaultData`.
- **MASVS-CODE:** la entrada hostil de una copia (I-1, M-1) es lo único abierto.
- **No probado aquí:** el arrastre, TalkBack y los tiempos en el móvil (pruebas a mano y tarea 11).

### Textos para el Abogado Digital (revisión final)

Los del informe del implementador (`OrganizeTexts.kt`, los errores de `Folders.kt` y los nombres de `TagPalette.kt`), con dos cambios de esta revisión:
- `SEARCH_MORE` debe nombrar también la cuenta del 2FA (M-2).
- Si se mantiene la comparación con el principio de una palabra (M-7), `SEARCH_MORE` debe decirlo.
