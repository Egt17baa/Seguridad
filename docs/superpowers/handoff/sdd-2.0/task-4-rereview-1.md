# Re-revisión 1 de la tarea 4 de Clavero 2.0

Rango `76642a8..c688eae` (el diff de código y de tests; los dos commits del registro son solo documentos). Leídos: `CLAUDE.md`, `SKILL.md` de `experto-seguridad-cripto`, la revisión original, los «Ruling (T4, …)» (`progress.md:76-84`), la sección «Ronda 1 de arreglos» del informe y el diff entero.

Tests ejecutados: `FoldersTest` (11), `FuzzySearchTest` (10), `OrganizeStoreTest` (8), `VaultV4Test` (14), `PlatformSourcesTest` (32), `TagPaletteThemeTest` (2) y `EntryQueryTest` (13). Todos en verde, sin fallos ni omitidos.

## Por hallazgo

**C-1. Arreglado.**
- `lazyKey(kind, id) = "$kind:$id"` (`VaultList.kt:461`). Todas las filas con datos de la bóveda lo usan: vistas `v:` (`VaultList.kt:382`), etiquetas `t:` (`:395`), secciones `r:`, `f:`, `a:` (`:411`, `:428`) y, en los diálogos, carpetas `f:` (`VaultOrganize.kt:188`, `:289`), vistas `v:` (`:372`) y etiquetas `t:` (`:425`).
- Las claves fijas de ambos archivos («search», «tools», «crumb», «chips», «views», «manage», «tags», «colors», «h-…», «empty», «none») no llevan «:», así que no pueden chocar. No queda ninguna `key = {` sin prefijo en estos dos archivos (`grep`).
- Test: `PlatformSourcesTest` «C-1 the keys of the vault's lazy rows…» (`:152`). Fallaría sin el arreglo, porque exige `lazyKey(` en cada `items(... key = {…})` y ningún «:» en las fijas.
- Además, las vistas se pintan con `distinctBy { it.id }` (`VaultList.kt:377`, `VaultOrganize.kt:372`), así que ni una bóveda con ids de vista repetidos repite clave.
- Menor del test (M-9 más abajo): la expresión regular solo ve `items(` con `key =` en la misma línea.

**I-1. Arreglado, con una observación.**
- `checkStructure` rechaza ids de carpeta y de vista repetidos con `ORGANIZE_DAMAGED` (`VaultLimits.kt:205-210`). `checkCopyStructure` lo convierte en `COPY_DAMAGED` (`BackupCheck.kt:178-184`), y se llama al importar (`VaultStore.kt:1328`) y al comprobar una copia (`BackupCheck.kt:91`).
- `branchHeight` es ahora una pasada por niveles con `seen.add`: cada id una vez, lineal (`Folders.kt:88-98`). El ciclo termina.
- Tests (`FoldersTest.kt`, añadidos al final): `duplicateFolderIdsRefused` (la copia de 5 × 100, `branchHeight` = 5 en menos de 100 ms, rechazo, ciclo), `duplicateViewIdsRefused` (dos vistas «manage») y `hostileCopyWithRepeatedIdsIsNotImported` (`importReplace` da `COPY_DAMAGED` y la bóveda queda como estaba). Los tres fallarían sin el arreglo.
- **Una bóveda que ya está en disco con ids repetidos:**
  - `checkStructure` no se llama al abrir (`VaultCrypto.read` no lo comprueba; `unlockLocked` no pasa por `fittedForSave`), así que **la bóveda abre y se lee** sin bloquear al usuario.
  - Cualquier guardado después (`fittedForSave`, `VaultLimits.kt:170-171`) lanza `ORGANIZE_DAMAGED`, también `markUsed` (que la interfaz envuelve en `runCatching`).
  - Hay salida: `deleteView(id)` y `withoutFolder(id)` quitan todas las filas de ese id (`Folders.kt:202`, `:257`), y el resultado ya pasa la comprobación. La interfaz enseña una sola fila por id (`distinctBy`, `folderTree` con vistos).
  - Es seguro y aceptable. Una bóveda así solo puede venir de una compilación de desarrollo de la 2.0 (las carpetas y vistas son nuevas en la 2.0, la app nunca repite un id y ahora la importación lo rechaza). Es la misma política que ya había para un ciclo.
  - Lo único que falta es el texto (M-10).

**I-2. Arreglado.**
- `modifyData` guarda lo que escribió, `fittedForSave(...).withPlainVersions()`, y lo pasa como `commit(…, read = saved)` (`VaultStore.kt:774-791`). Sin `read`, `commit` descifra dentro de `withContext(compute)`, y solo si la época no cambió (`:441-446`); lo mismo el desbloqueo de v2/v3 pendiente de pasar a v4 (`:561-566`). Ningún camino descifra ya en el hilo de quien llama.
- Corrección de reutilizar el objeto:
  - `VaultCrypto.read` termina en `data.withPlainVersions()` (`Vault.kt:373`), que es lo que ahora se aplica antes de escribir. Lo que se escribe y lo que se abre son los mismos datos, ya ajustados (`fitted` y recorte incluidos), porque `saved` es exactamente el objeto que se serializa.
  - El orden por título lo hace `opened` igual que antes (`:460`).
  - Escribir con `withPlainVersions` solo puede reducir el tamaño frente a lo medido por `fittedForSave`, así que no rompe el tope de 24 MiB.
  - Si `accept` rechaza o no hay cambio, se sale con `false` antes de `commit` (`:785-787`).
  - Con un bloqueo a mitad, `opened` lo ve por la época y deja la bóveda bloqueada con la clave borrada (`markUsedCutByALock`).
  - Con una escritura que falla y `mustSave = false`, `opened` abre con `saved`, que es lo que estaba en memoria antes y lo que se escribirá en el siguiente guardado (`markUsedWithAFailedWrite`).
- Test: `OrganizeStoreTest.savesOpenWhatTheyWroteWithoutDecodingAgain` usa `assertSame` en una entrada no cambiada tras `upsert`, `moveEntries` y `markUsed`; una decodificación crearía objetos nuevos, así que falla sin el arreglo. Comprueba además lo abierto frente al disco tras bloquear y abrir. `PlatformSourcesTest` fija `read = saved)` y `withContext(compute) { VaultCrypto.read`.

**M-1. Arreglado.** `SEARCH_TEXT_MAX = 200` y `searchText` (`EntryQuery.kt:58-61`) se aplican en el cuadro de búsqueda (`VaultList.kt:350`), en `apply` e `isOn` al leer una vista (`:199-202`, se recorta, no se rechaza), en `withView` (`Folders.kt:250`) y en `FuzzySearch.prepare` (`:74`). Test: `searchAndViewTextsAreCapped`.

**M-2. Arreglado.** `SEARCH_MORE` nombra «el servicio y la cuenta del 2FA» y el principio de palabra (`OrganizeTexts.kt:12-16`), y coincide con `EntryQuery.kt:118-131` y `FuzzySearch.kt`.

**M-3. Arreglado.** Comentario corregido (`FuzzySearchTest.kt:57`) y `typoLimitsAtTheEdges` con los bordes 3/4/7/8 y `allowedEdits` de 1 a 9.

**M-4. Arreglado.** El `onDispose` solo escribe la posición con la bóveda abierta (`VaultList.kt:303`); lo fija `PlatformSourcesTest`.

**M-5. Arreglado.** Al desaparecer la carpeta, `includeSubfolders` vuelve a `true` (`VaultList.kt:296`), y `unfiltered()` y «Recientes» usan `plain()`, que ignora «Con subcarpetas» sin carpeta (`:189-194`, `:234`). Sin test propio, pero la lógica es de una línea y está cubierta por el pin de código; no lo cuento como fallo.

**M-6. Arreglado.** Los tres tests existen y pasan:
- `VaultV4Test.markUsedWhileTheUpgradeWaits`: `false`, sin lanzar, y el archivo sigue siendo el v3.
- `OrganizeStoreTest.markUsedWithAFailedWrite`: la bóveda sigue abierta, el uso queda en memoria, el archivo no cambia, y el siguiente guardado que funciona (el del desbloqueo) lo escribe; otra tienda sobre el mismo archivo lo ve.
- `OrganizeStoreTest.markUsedCutByALock`: un bloqueo durante la escritura deja `Locked`, y el siguiente desbloqueo ve el uso guardado.

**M-7. Arreglado.**
- `distances` da la distancia a la palabra entera y al principio más cercano, más corto que ella (`FuzzySearch.kt:154-181`; la poda por fila sirve para las dos).
- `score`: primero la palabra entera con sus erratas (`FUZZY_BASE`); si no, el principio con `PREFIX_EDITS = 1` como mucho, puntuado desde `PREFIX_BASE = 200` (`:108-137`). Así toda coincidencia de palabra entera va antes.
- Palabras de menos de 4 letras y de más de `FUZZY_MAX_WORD` quedan solo exactas (`allowed = 0`).
- Nota de corrección en el diseño (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:96-99`) y en `SEARCH_MORE`.
- Test: `startOfAWordOneTypoAndLast` («gmia» encuentra «Gmail», «contrato» no encuentra «Contraseña del banco», el orden entre palabra entera con dos erratas y principio con una).

**M-8. Arreglado.** `LightColors` y `DarkColors` son `internal` (`Theme.kt:25`, `:59`) y `TagPaletteThemeTest` mide cada color sobre las 9 superficies de cada tema y comprueba que los fondos de `TagPalette` son los del tema.

## Problemas nuevos

Ninguno Critical ni Important.

- **M-9 (Menor). El test de C-1 solo ve `items(` con `key =` en la misma línea** (`PlatformSourcesTest.kt:155`, expresión regular `items\([^\n]*?key = \{`). Un `items(` partido en varias líneas, o una lista nueva en otro archivo (`ContactsScreen`, `TrashScreen`, `HealthScreen` usan `key = { it.id }` sin prefijo; no tienen fichas fijas con las que chocar, así que hoy no hay fallo), pasaría sin que lo vea. Arreglo: aceptar saltos de línea en la expresión regular, y avisar si una lista tiene `item(key = "…")` fijo y `items(…)` con datos sin `lazyKey` en cualquier archivo de `ui/screens`.
- **M-10 (Menor). `ORGANIZE_DAMAGED` no dice qué hacer** (`VaultLimits.kt:64`: «Las carpetas o las vistas de la bóveda están dañadas.»). En una bóveda con ids repetidos en disco (caso de I-1) es el texto que verá quien intente guardar, y la salida existe (borrar esa carpeta o esa vista). Los demás textos de esta familia dicen la acción (`FOLDER_CYCLE`: «Muévela a otro sitio»). El informe dice que «no llega a verse», pero sí se ve en ese caso. Arreglo: añadir «Borra la carpeta o la vista repetida.» (y apuntarlo en los textos para el Abogado, que ya lo listan).

Nada más: sin cambios de formato, claves, permisos, red ni dependencias.

## Veredicto: necesita arreglos (solo dos Menores, M-9 y M-10; los once hallazgos de la revisión original están arreglados)

## Revisión experto-seguridad-cripto

- Disponibilidad ante datos hostiles (decisión (c) de la 1.4.0): ids repetidos rechazados antes de entrar (`VaultLimits.kt:205-210`, `BackupCheck.kt:178-184`) y `branchHeight` lineal (`Folders.kt:88-98`); una etiqueta o un id de vista que coincida con una clave fija ya no cierra la app (`VaultList.kt:461`); textos acotados (`EntryQuery.kt:58-61`, `FuzzySearch.kt:74`).
- Integridad de lo abierto: lo que se abre tras un guardado es el mismo objeto que se cifró, normalizado igual que `VaultCrypto.read` (`VaultStore.kt:774-791`, `Vault.kt:373`). Un bloqueo a mitad deja la clave borrada (`opened`, `VaultStore.kt:455-470`). El descifrado de `commit` se salta si la época cambió (`:441`). La regla de la apertura con la huella («lo escrito es lo que abre») se cumple en todos los guardados.
- Una bóveda ya guardada con ids repetidos abre y se lee, y solo falla al guardar con un error que se cura borrando el elemento repetido (M-10 pide que el texto lo diga). Es el mismo trato que un ciclo.
- Veracidad: `SEARCH_MORE` coincide con el código (`OrganizeTexts.kt:12-16`); la nota del diseño recoge el ruling M-7.

## Revisión Ingeniero Móvil Senior

- Concurrencia: ningún descifrado ni decodificación en el hilo de quien llama en `modifyData`, `commit` ni el desbloqueo con paso a v4 pendiente (`VaultStore.kt:774-791`, `:441-446`, `:561-566`). El orden de la lista en `opened` sigue en el hilo de quien llama, sobre objetos ya decodificados. Un detalle sin riesgo: el desbloqueo v2/v3 descifra en `compute` aunque la bóveda se bloquee en el intervalo (`:561`), solo trabajo perdido que `opened` descarta.
- Compose: claves únicas por construcción (`VaultList.kt:382`, `:395`, `:461`; `VaultOrganize.kt:188`, `:289`, `:372`, `:425`); `distinctBy` en vistas.
- Ciclo de vida: la posición de la lista se guarda solo con la bóveda abierta (`VaultList.kt:303`); «Con subcarpetas» se restablece al quitarse la carpeta (`:296`).
- Rendimiento: `branchHeight` lineal con 500 carpetas repetidas en menos de 100 ms (`FoldersTest`), el prefijo con poda por fila (`FuzzySearch.kt:154-181`), el texto de búsqueda acotado a 200 puntos de código.
- Accesibilidad: el contraste de los colores se mide contra los temas reales (`TagPaletteThemeTest`).
