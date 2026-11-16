# Revisión de la rama 2.0, parte B: copias y ajustes

- **Rango:** `a3ac942..dcf28dc`. Líneas citadas: las de `dcf28dc`.
- **Cómo:** solo lectura, en un clon propio (`scratchpad/br-copias`, `git checkout dcf28dc`). **No ejecuté gradle ni tests**; lo que hay que ejecutar está en «Pruebas que pide el revisor».
- **Lentes:** `experto-seguridad-cripto` (un hallazgo confirmado de seguridad o de veracidad es Important como mínimo), `desarrollo-movil-senior`, `seguridad-criptografia-movil`, `auditoria-seguridad-movil`, y las reglas del bloque común (decisiones (a), (b), (c), resultados tardíos, textos verdaderos, formatos que no se rompen).

## Alcance

**Leídos enteros en `dcf28dc`:**
- `vault/AutoBackup.kt` (1 127), `vault/A6Text.kt` (184), `vault/BackupSchedule.kt` (122), `vault/BackupCheck.kt` (235), `vault/BackupCheckTexts.kt` (102), `vault/VaultRegistry.kt` (176);
- `settings/Settings.kt` (443), `settings/SettingsStore.kt` (295), `settings/Opening.kt` (75), `settings/SettingsSearch.kt` (109);
- `AppContainer.kt` (899);
- `ui/screens/VaultBackup.kt` (277); de `ui/screens/SettingsScreen.kt`, las líneas 150-540 (búsqueda, «Copia automática» y `MasterChangedDialog`);
- de `vault/BackupCheckSession.kt`, las líneas 60-147 (`BackupCheckRuns`);
- de `vault/VaultStore.kt`: 180-500 (constructor, `revision`, `generation`, `saveBookkeeping`, `lastChangeSavedAt`, `masterMark`, `changeMark`, `commit`), 560-680 (desbloqueo y paso a v4), 801-826 (`modify`, `modifyData`), 1118-1150 (`markUsed`, `recordBackupChecked`), 1179-1244 (restaurar), 1278-1330 (adjuntos), 1440-1560 (`importReplace`, `replace`, `undo`), 1730-1738 (`changeMarkOf`);
- de `vault/TrashTexts.kt`, los textos de las copias (`COPIES_KEEP_IT` y sus usos, `HISTORY_ALL_CONFIRM_TEXT`);
- `vault/Attachments.kt:21-30` (límites), `core/Json.kt`, `settings/Appearance.kt:10-42`, `health/Missions.kt:87-102`, `tools/ToolTexts.kt:503-518` (textos del diálogo de A6);
- tests: `AutoBackupTargetsTest.kt:1281-1410` (el exhaustivo, `anImportUndoneGivesBackTheA6StateExactly`, `aRequestOf143IsTheRequestOfNow`) y el archivo real `app/src/test/resources/seguridad/settings-1.4.3.json`.
- Comparado con `a3ac942`: `AutoBackup.replaceNow` y `BackupCheck` (líneas de los adjuntos).

**Documentos:** `CLAUDE.md`, el encargo (`advisor-t12-branch-review.md`, bloque común y parte B), `sdd-2.0/pendientes.md` (entero), `sdd-2.0/progress.md` (cada «Ruling» y «Owner decision» de las tareas 1, 3, 8 y 10) y `sdd-2.0/task-8-rereview-6.md` (entero).

**No leídos enteros:** `ui/screens/BackupCheckScreen.kt`, `settings/Guide.kt`, `settings/RenameNotice.kt` y las partes de `SettingsScreen.kt` fuera de 150-540. Ver «Lo que no pude comprobar».

## Issues

### Critical

**C-1. Con la papelera, la bóveda puede tener más de 50 MB de adjuntos, y entonces cada copia que hace la app se rechaza al importarla o al comprobarla.** Cruza las tareas 3 (papelera), la 1.4.0 (límite de adjuntos) y «Comprobar una copia».
- **Dónde:**
  - `VaultStore.kt:805`: desde la tarea 3, `modify` solo pasa las entradas vivas (`data.live`).
  - `VaultStore.kt:1298-1302`: `addAttachment` suma el total sobre esa lista (`attachments.totalBytes(list.flatMap { it.attachments })`), así que deja fuera los adjuntos de las entradas que están en la papelera.
  - `VaultStore.kt:1392`: la copia lleva también los adjuntos de la papelera («a copy brings the trash back»).
  - `VaultStore.kt:1485-1491` (importar) y `BackupCheck.kt:94-95` (comprobar) pasan `data.entries`, que incluye la papelera (`VaultData.kt:41-44`), a `checkCopyEntries`. Esa función rechaza la copia si la suma pasa de 50 MB (`BackupCheck.kt:204-205`).
  - `writeBackup` solo comprueba los 100 MB, los 6 MB y los 2 000 archivos (`Backup.kt:153`, `:180`), así que la copia **se escribe** sin problema.
- **Escenario:**
  1. Una bóveda con 48 MB de adjuntos manda a la papelera una entrada con 10 MB (los adjuntos siguen ahí 30 días, `VaultStore.kt:390-392`).
  2. Añade 10 MB nuevos: lo permite, porque las entradas vivas suman 48 MB.
  3. La bóveda tiene ahora 58 MB. Cada copia automática y cada exportación se escriben bien, pero «Importar copia», «Importar como bóveda nueva» y «Comprobar una copia» las rechazan con «Los adjuntos de la bóveda pasan de 50 MB en total».
  4. Con «Conservar las N», la rotación borra las copias anteriores, que sí se podían importar. Tras N cambios no queda ninguna copia automática que Clavero pueda restaurar.
- **Por qué es Critical:** la app escribe copias `seguridad-backup` que ella misma no lee (regla «nunca se rompen…»), y la rotación hace que se pierdan, sin aviso, las copias buenas. No hay ningún error en el momento: solo «Comprobar una copia» lo enseñaría, si el usuario la usa.
- **Es regresión de la tarea 3:** en la 1.4.3, borrar una entrada la quitaba del todo y `modify` pasaba todas las entradas.
- **Arreglo:**
  1. `addAttachment` comprueba el total sobre todas las entradas, las de la papelera incluidas. Por ejemplo, con `modifyData { data -> … }` en lugar de `modify`, o con una función pura `attachmentsFit(data: VaultData, adding: Long): Boolean` que sume `data.entries`, y `addAttachment` la llama.
  2. «Restaurar» de la papelera no sube el total, porque ya contaba.
  3. El texto al añadir (es también la fila 37) dice qué hacer y nombra la papelera, porque con este arreglo la papelera cuenta: «Con este adjunto, los adjuntos de la bóveda (también los de la papelera) pasarían de 50 MB. Quita algún adjunto o vacía la papelera para añadir otro.»
  4. **Las bóvedas que ya están por encima** (las de las versiones de prueba de la 2.0) se arreglan solas cuando se vacía la papelera. No hace falta migración: ninguna versión publicada lo permitió.
- **Tests:**
  - `attachmentsFitCountsTheTrash`: unos datos con 45 MB en las entradas vivas y 10 MB en la papelera, con referencias de tamaño declarado, sin bytes reales, no admiten 1 MB más.
  - Un test de fuente: `addAttachment` usa `attachmentsFit` y no `modify` con `live`.
  - Un test de punta a punta pequeño, con los límites inyectados si se puede: mandar a la papelera, añadir, exportar e importar. O la invariante «toda bóveda que se puede guardar pasa `checkCopyEntries`» sobre las operaciones de la papelera.

### Important

**I-1. Fila 17, la petición heredada de la 1.4.3 (`"1.x"`): el diseño decidido cierra los caminos a y b, pero su arreglo no debe tocar `requestId`.** El veredicto y los tests, en la tabla de filas. Lo que se ve en el código:
- **Camino a:**
  - `a6Summary` solo da `stale` cuando hay una marca (`A6Text.kt:44`, `a6?.masterMark != null`);
  - una petición de la 1.4.3 no la tiene: llega sin `a6` (`SettingsStore.kt:221-229`, el destino con `autoBackupReplacePending`, `:209`, y sin `replaceRequest`), y al cerrarla se guarda `A6State("1.x")` sin marca (`AutoBackup.kt:402`).
- **Camino b:** `requestId` vale `replaceRequest ?: LEGACY_A6_REQUEST` (`A6Text.kt:33`), y `a6Snapshot` filtra con él (`:57`). Con la petición `"1.x"`, una carpeta nueva (`replaceRequest == null`) cuenta como de la petición.
- **Cuidado con el arreglo:** `requestId` lo usan también `AutoBackup.kt:494`, `:541` (`request = t.requestId` de un destino que espera) y `Settings.kt:401` (`replaceAfter`: `r.request ?: requestId`, que pone `replaceRequest = "1.x"` explícito al terminar). Si se cambia `requestId` para que una carpeta nueva no sea `"1.x"`, una carpeta heredada que termina dejaría de guardar su `ReplaceDone("1.x")`.
  - **El arreglo correcto** es un predicado aparte, `BackupTargetSettings.inRequest(request)`, que solo usan `a6Snapshot` y `a6Summary`:
    - para un id de la 2.0, `replaceRequest == request`;
    - para `"1.x"`, `(replacePending && replaceRequest == null) || (replaceRequest == "1.x" && replaceDone?.request == "1.x")`.
  - `a6Summary` sigue contando `folders` sobre todas las carpetas de ahora, lo que da el parcial prudente («en N de las M carpetas; en las demás puede quedar…»), que es verdad.
- **El punto (1) del diseño:** en `a6Summary`, `stale = (a6?.masterMark == null && request == LEGACY_A6_REQUEST) || (marcas distintas)`. No depende del parámetro `masterMark`, así que vale también en `VaultSettings.a6Summary` (`Settings.kt:249`).
  - El diálogo de la maestra (`AppNavHost.kt:312`, filtrado por su propia petición en `SettingsScreen.kt:503`) nunca tiene `"1.x"`, porque `replaceNow` siempre crea un UUID (`AutoBackup.kt:648`). No cambia.
- **No abre otro camino.** Lo comprobé:
  - una carpeta `"1.x"` que espera, quitada en la 2.0 y vuelta, sigue siendo de la petición (`keptPast`/`restored`, `Settings.kt:281-286`, `:323-331`, conservan `replacePending`);
  - con una petición de la 2.0 ya pedida (`a6.id` = UUID), una carpeta `"1.x"` que vuelve no es de esa petición y no la reabre. Su reemplazo heredado se hace callado, y es verdad lo que ya se dijo («En la otra carpeta puede quedar…»);
  - pulsar «Borrar…» con la bóveda bloqueada sobrescribe los objetivos heredados con los de la petición nueva (`Settings.kt:378-381`).
- **Qué más cambia de comportamiento:**
  - quien actualiza con una petición pendiente y **un solo** cambio de maestra ve el texto prudente («…de antes de un cambio de contraseña anterior… Míralo a mano.») en lugar del éxito llano (`AUTO_BACKUP_REPLACED`);
  - `aRequestOf143IsTheRequestOfNow` (`AutoBackupTargetsTest.kt:1396-1403`) pasa a esperar `a6StaleText(1, 1)`;
  - la foto cerrada (`said`) de una petición `"1.x"` ya cerrada con una carpeta añadida después cambia de contenido, porque la carpeta nueva deja de salir. **Una vez**, en el primer desbloqueo con la versión arreglada, `a6Decide` ve N ≠ S y vuelve a decir el resumen, con el texto prudente. Es verdad, pero es una repetición: o se acepta, o se compara S quitando de ella las líneas de las carpetas que no son de la petición. Va a «Dudas».

**I-2. Los textos de A6 dicen qué se borra y se callan la copia de antes de la 2.0, que también se borra.**
- **Dónde:**
  - el botón `PASSWORD_CHANGE_REPLACE_COPIES`, «Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)» (`ToolTexts.kt:508`);
  - los resultados `AUTO_BACKUP_REPLACED`, `AUTO_BACKUP_REPLACED_SEVERAL` (`AutoBackup.kt:152-164`) y `a6PartialText` (`A6Text.kt:159`), que dicen «se borraron las copias automáticas y las de antes de importar…»;
  - las cuentas de copias sin apuntar dicen «(automática o de antes de importar)» (`AutoBackup.kt:175-193`, `A6Text.kt:176-182`), pero cuentan también `seguridad-antes-de-2-0-` (`AutoBackup.kt:1052`).
- **Qué hace el código:** la copia del paso a v4 va a `importCopies` (`AutoBackup.kt:110-114`, `:744`) y A6 la borra (`AutoBackup.kt:1028-1034`), como pide el Ruling T1 (dudas 4 a 6).
- **Cruza tareas (1 y 8):** solo el «Más información» de Ajustes lo dice (`SettingsScreen.kt:396-400`). El diálogo después de cambiar la maestra no lo dice.
- **Por qué importa:** la copia `seguridad-antes-de-2-0-` es la que abre la 1.4.3 (O-3 y el aviso del paso a v4 la nombran). Quien la guarda como vuelta atrás lee un botón que enumera lo que borra sin nombrarla. Es un texto incompleto sobre un borrado: veracidad, así que Important.
- **Arreglo, solo textos (la redacción final, al Abogado):**
  - botón: «Hacer una copia nueva y borrar las anteriores (automáticas, de antes de importar y de antes de la 2.0)»;
  - en los tres resultados: «…las copias automáticas, las de antes de importar y la de antes de la 2.0 hechas antes del cambio…»;
  - en los de sin apuntar: «(automática, de antes de importar o de antes de la 2.0)».
- **Test:** un test de texto que fije que los cinco literales nombran «antes de la 2.0», o un test de fuente de que `UPGRADE_COPY_PREFIX` está en la lista de A6 y su nombre en los textos.

**I-3. Fila 30: `COPIES_KEEP_IT` («Las copias ya hechas (automáticas o exportadas) lo conservan.», `TrashTexts.kt:16`; se usa en `:38`, `:44` y `:87`) no es verdad en cuanto hay copia automática.**
- **Qué hace el código:**
  - «Borrar para siempre», «Vaciar papelera» y «Borrar historial» son guardados normales: suben `revision` y cambian `mtime`. A los 3 s sale la copia de cada destino `ON_CHANGE` (`AutoBackup.kt:452-457`, `:491`), y su rotación borra la copia más antigua de la lista (`AutoBackup.kt:1025`), que sí lo conservaba.
  - A los destinos con calendario les pasa lo mismo en su próxima copia, porque la marca de D-3 cambió.
  - Tras `keep - 1` cambios más no queda ninguna copia automática con lo borrado.
  - **«Conservar 1» no existe:** las opciones son 3, 5 y 10 (`Settings.kt:415`), y `sane` pasa cualquier otro número a 5 (`SettingsStore.kt:181`).
  - Así que, justo después, quedan al menos 2 copias automáticas con lo borrado. Las exportadas a mano lo conservan siempre, y A6 puede borrar las automáticas.
- **Es de veracidad, así que Important.** El texto hermano `HISTORY_ALL_CONFIRM_TEXT` (`TrashTexts.kt:106-107`) ya lo dice bien («pueden conservarlas; la copia automática quita las más antiguas»).
- **Arreglo, una constante (redacción al Abogado, con la fila 19):** «Las copias ya hechas (automáticas o exportadas) pueden conservarlo; la copia automática borra las más antiguas, así que con el tiempo deja de estar en ellas.»
- **Test:** los tres textos de confirmación no contienen «lo conservan.» y sí «pueden conservarlo». Fijados en `TrashTextsTest`, o en el test que ya fija `COPIES_KEEP_IT`.

**I-4. Fila 37: `BackupCheck.kt:204-205` da «Los adjuntos de la bóveda pasan de 50 MB en total» a una copia con un solo adjunto de tamaño fuera de 0..5 MB, también negativo, y `VaultStore.kt:1301` da ese mismo texto al añadir un adjunto, cuando los adjuntos aún no pasan de 50 MB (pasarían).**
- **Los dos textos son falsos,** así que Important.
- **Ya estaba en la 1.4.3** (`a3ac942`, `BackupCheck.kt:168`), pero se toca con C-1.
- **Arreglo:**
  ```kotlin
  if (refs.any { it.size !in 0..MAX_ATTACHMENT_BYTES.toLong() }) throw CryptoFailure(COPY_DAMAGED)
  if (refs.sumOf { it.size } > MAX_ATTACHMENTS_TOTAL_BYTES) throw CryptoFailure(ATTACHMENTS_TOTAL_TOO_BIG)
  ```
  - `COPY_DAMAGED` («La copia está dañada: no se puede importar. Elige otra copia.») es verdad aquí: ninguna versión de Clavero deja un adjunto de más de 5 MB (`Attachments.kt:21`, igual en la 1.4.3) ni un tamaño negativo.
  - Para el total de una copia, `ATTACHMENTS_TOTAL_TOO_BIG` es verdad tal cual.
  - Al añadir, la constante nueva de C-1, punto 3.
- **Tests:**
  - `VaultStoreTest` (el caso de `:166`, tamaño -1) pasa a esperar `COPY_DAMAGED`;
  - uno nuevo con un adjunto de 5 MB + 1 declarado: `COPY_DAMAGED`;
  - uno con 11 de 5 MB declarados: `ATTACHMENTS_TOTAL_TOO_BIG`;
  - al añadir: el texto nuevo, no `ATTACHMENTS_TOTAL_TOO_BIG`.

### Minor

**m-1. Fila 18, sigue como dice la re-revisión 6.**
- **m-1 de la re-revisión 6:** `A6Text.kt:84` cuenta como cambio cualquier diferencia entre N y S, también una línea `hecha` que desaparece, y `runDue(afterUnlock)` publica siempre (`AutoBackup.kt:546`).
- **m-3:**
  - el KDoc de `changeMark` (`VaultStore.kt:427-435`) queda separado de su función (`:448`) por el KDoc y el cuerpo de `masterMark` (`:436-446`);
  - el KDoc de `a6Decide` (`A6Text.kt:77-78`) promete «nothing is ever kept silent while a target in the request waits», y calla en tres casos;
  - el KDoc de `a6Text` (`:89-97`) no nombra `stale`.
- **Arreglo:** en `a6Decide`, `changed = summary != null && (linesN - linesS).isNotEmpty() || (linesS - linesN).any { it.endsWith("=pendiente") }`, comparando primero la cabecera de la petición. Mover el KDoc y ajustar los otros dos.
- **Test:** S con todas hechas; quitar una hecha y desbloquear: nada. Siguen hablando `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers` y `aFolderThatComesBackReopensTheRequest`.

**m-2. Borrar una bóveda puede dejar sus ajustes en `settings.json` y el permiso de su carpeta para siempre, y el texto dice que se borran.**
- **Dónde:** `AppContainer.kt:701-713`. Primero se quita del registro (que borra la carpeta) y después `settings.update { it.withoutVault(id) }`, que se traga el fallo (`:710-712`).
  - Con el disco lleno, o con el proceso muerto entre las dos escrituras, `perVault[id]` se queda: los URI de las carpetas, sus listas y la petición de A6.
  - `releaseIfUnused` (`:784-786`) mira todo `perVault`, así que nunca suelta esa carpeta.
  - `VaultsTexts.kt:48` dice «Se borra … su archivo, sus adjuntos, su huella y sus ajustes».
- **Por qué es Minor:** solo con un fallo, y no hay ningún secreto (lo que queda es lo de la fila 8). La afirmación queda en falso solo en ese caso.
- **Arreglo:** al arrancar, después de `settings.load()`, quitar las bóvedas de `perVault` que no están en el registro y soltar sus carpetas sin uso. Es idempotente, y también limpia lo que deja `dropVault` (`:657-661`).
- **Test:** `settings.json` con `perVault` de un id que no está en `vaults.json`: tras arrancar, no está, y la carpeta se suelta (con `releaseFolder` falso).

### Info

- **i-1. D-3, la marca de cambio** (`VaultStore.kt:448-453`, `changeMarkOf` en `:1735`): SHA-256 de `mtime` de `vault.bin`, el coste y `vaultTag`.
  - **Cada cambio que hace el usuario la mueve,** porque todos pasan por `modify`/`modifyData` con `mustSave` (`commit`, `:467-469`, `save` reescribe el archivo): las entradas, la papelera (mandar, restaurar, borrar, vaciar), el historial (restaurar y borrar), las carpetas, las vistas, los campos propios, el nombre de la bóveda, los adjuntos, cambiar la maestra e importar.
  - «Ahora no» de las misiones vive en memoria (`health/Missions.kt:87-102`), así que no es un cambio de la bóveda.
  - **Lo que la app escribe sola no la mueve,** porque son guardados de mantenimiento con `setLastModified` de vuelta (`:414-418`): la purga de la papelera y la última comprobación de la maestra al desbloquear (`:588`, `:602`, `:706-711`), «Recientes» (`markUsed`, `:1124`), la fecha de «Comprobar una copia» (`:1134-1140`) y el paso a v4 (`:634`). Tampoco suben `revision`, así que no hacen ninguna copia ni rotan ninguna.
  - **Única excepción:** volver a envolver con un coste mayor (A8) cambia `kdf` y mueve la marca, así que hace **una** copia con calendario, pero ninguna `ON_CHANGE`. Es inofensivo (el archivo sí cambió: sal y coste nuevos) y rota la más antigua como cualquier copia.
  - La precisión de `mtime`, en la fila 13 bis (móvil).
- **i-2. Varias bóvedas, una carpeta.** Dos bóvedas pueden elegir la misma carpeta (`canAddTarget` solo mira la suya, `Settings.kt:255-259`).
  - Cada destino borra solo los ids de sus listas (`AutoBackup.kt:1021-1034`), así que la copia de B nunca va a la carpeta de A salvo que se elija esa carpeta en B, y A6 de A nunca borra una copia de B. Las de B cuentan en A como «sin apuntar», y el texto lo dice («o de otra bóveda»).
  - `freeName` (`:994-1001`) no está protegido entre bóvedas. Dos copias en el mismo segundo solo pueden coincidir justo en un cambio de bóveda (la copia de A sigue escribiéndose tras su bloqueo). El proveedor da otro nombre o rechaza el cambio de nombre, y nunca se borra una copia completa (`:953-962`).
- **i-3. Cambiar de bóveda a mitad de una copia.** El ámbito de la bóveda solo se cancela al borrarla (`AppContainer.kt:698-699`).
  - La copia de A sigue y se apunta en los ajustes de A (`recordAutoBackup`, `:848-857`, con `listed(id)`).
  - Un bloqueo antes del `export` la aplaza (`AutoBackup.kt:973-976`, `pending`), y al desbloquear se hace sin que D-3 la salte (`:540`, `waited`).
  - Un permiso de carpeta perdido no da `null` en `open` (`SafBackupTarget.open`). Sale `SecurityException` y `lastError = AUTO_BACKUP_NO_PERMISSION` (`:1066`), visible en Ajustes (`SettingsScreen.kt:435`), sin volver a intentarlo en 5 minutos (`:552`).
- **i-4. `replaceNow` con la bóveda abierta usa los objetivos de una petición anterior que espera** (`AutoBackup.kt:662`). No se alcanza en el uso normal: `changeMaster` bloquea la bóveda, y «Borrar…» con la bóveda bloqueada sobrescribe los objetivos (`Settings.kt:378-381`). Ya estaba en la 1.4.3 (`a3ac942`, `AutoBackup.kt:370`).
- **i-5. Una entrada de `perVault` que no se lee hace que se pierda todo `settings.json`** (`SettingsStore.kt:36-46`, `decodeSettings`): un campo sin valor por defecto con un valor desconocido, como `schedule`. Entonces todo vuelve a los valores por defecto, también las marcas de A11. Solo pasa con un archivo dañado, porque la app escribe de forma atómica y Android no deja instalar una versión anterior. Se acepta.
- **i-6. El arranque.** `openingItem` (`Opening.kt:60-74`) pone el resultado de A6 detrás de `UPGRADE` y `UPGRADE_DONE`.
  - Con una petición heredada pendiente, el primer desbloqueo de la 2.0 lanza a la vez `runDue(afterUnlock)` y `copyBeforeUpgrade`, bajo el mismo cerrojo de `AutoBackup`.
  - Mientras el paso a v4 espera, `exportBackup` exporta el archivo v3 que hay en el disco (`VaultStore.kt:1383-1392`). La copia del reemplazo es v3 y la abre la 1.4.3. No se pierde nada.
  - La copia `seguridad-antes-de-2-0-` es de la contraseña ya cambiada en la 1.4.3, y no entra en los objetivos heredados.
- **i-7. El buscador de Ajustes** indexa solo constantes (`SettingsSearch.kt:60-80`, `:90-92`) y corta la consulta a 60 puntos de código antes de `FuzzySearch` (`:95-104`).
- **i-8. `rememberSaveable` de la parte B:** `changing` (`SettingsScreen.kt:301`) guarda solo el URI de una carpeta, que ya está en `settings.json`. No es un secreto.

## Filas de `pendientes.md`

| Fila | Veredicto | Motivo y evidencia |
|---|---|---|
| 17 | **Se arregla** (I-1), con el diseño decidido | El diseño cierra los caminos a y b sin abrir otro, si la pertenencia va en un predicado aparte (`inRequest`) y no en `requestId` (`A6Text.kt:33`, que usan `AutoBackup.kt:494`, `:541` y `Settings.kt:401`). Los tests, abajo (P-17a a P-17f). Cambio de comportamiento: el éxito llano heredado pasa al texto prudente, y una repetición única del resumen al actualizar (ver «Dudas») |
| 18 | **Se arregla** (m-1) | `A6Text.kt:84`; KDoc en `VaultStore.kt:427-448` y `A6Text.kt:77-78`, `:89-97` |
| 20 | **Se acepta** | **Un solo fallo sí llega al mismo estado:** la muerte del proceso entre `beforeReplace` (`VaultStore.kt:1511`, olvido escrito en el disco) y el `save` (`:1533`), que con 50 MB de adjuntos que mover (`:1527`) dura segundos. Al arrancar, la bóveda es la de antes, sin listas ni petición de A6, y nada lo dice; si se vuelve a importar, `droppedRequest` ya es `false`. **Motivos para aceptarlo:** ya estaba desde COPIAS-3 (1.3.x); falla hacia el lado seguro (nada se borra, solo deja de apuntarse, `:1552-1557`); y el fallo de esa petición ya se dijo una vez y queda en el `lastError` de la carpeta en Ajustes (`forgettingAutoBackupCopies`, `Settings.kt:335-340`, no lo borra). **Si se quiere cerrar:** el olvido guarda aparte (`VaultSettings.droppedForImport`) la petición y las listas con la `masterMark` del archivo de antes; al desbloquear, si la marca es la misma (no se importó), se devuelven; si no, se borran. Unas 30 líneas y dos tests. No lo recomiendo en esta versión |
| 21 | **Se acepta** | **Un solo fallo sí llega al mismo estado:** el fallo dicho se apunta en `replaceTold` en el `onResult` del intento (`AutoBackup.kt:894-900`; `Settings.kt:410`), antes de que el diálogo se vea. Si el proceso muere, o se quita la app, mientras el resultado espera detrás de `UPGRADE`, `UPGRADE_DONE`, `MASTER_CHANGED` o `VAULTS_NOTICE` (`Opening.kt:64-69`), ese fallo ya no se vuelve a decir. **Motivos:** ningún texto queda falso; la petición sigue pendiente y se vuelve a intentar (`AutoBackup.kt:528`, `:531`); el `lastError` de la carpeta queda en Ajustes (`SettingsScreen.kt:435`), y cuando la copia se hizo y no se borró, es `AUTO_BACKUP_OLD_NOT_DELETED` («…no se pudieron borrar las antiguas. Se volverá a intentar…»). Cuando la copia nueva no se pudo escribir, el `lastError` no dice que quedan copias antiguas, pero la petición sigue y su éxito o su parcial se dirán. **Si se quiere cerrar:** apuntar los fallos dichos al cerrar el diálogo (como `said`, m-7 de la ronda 5), en `replaceOutcomeShown`; con eso, un fallo se repite hasta que se cierra |
| 30 | **Se arregla** (I-3) | Con «Conservar N» (3, 5 o 10; «Conservar 1» no existe, `Settings.kt:415`), justo después del borrado quedan al menos `N - 1` copias automáticas con lo borrado, y desaparecen tras `N - 1` cambios más. Las exportadas a mano lo conservan siempre. A6 puede borrarlas. La redacción final, al Abogado, con la fila 19 |
| 37 | **Se arregla** (I-4, con C-1) | `BackupCheck.kt:204-205` partido en dos; constante nueva al añadir en `VaultStore.kt:1301`, que cuente la papelera |
| 39 | **Se arregla**, solo texto por sitio | `BACKUP_DOES_NOT_FIT` y `_V3` (`Backup.kt:87-88`) salen tal cual en la exportación a mano (`VaultBackup.kt:93-95`) y en «Exportar una copia antes» del paso a v4 (`VaultUpgrade.kt:61`, `upgradeExportFailure`). Las copias automáticas y la de antes de importar ya los cambian por un texto propio (`AutoBackup.kt:1068`, `:783`). **Código:** dos constantes de qué hacer y un `when` en cada uno de los 2 sitios (unas 8 líneas). Exportar: «Quita adjuntos o entradas grandes y vuelve a exportarla.» Paso a v4: «Puedes seguir sin copia: la bóveda pasa al formato nuevo igual; las versiones anteriores ya no la abrirán.» (al Abogado; ver que el botón «Seguir sin copia» existe con ese nombre). Test: los dos sitios dan su texto y ninguno dice «quita» en el paso a v4 |
| 42 | **Se arregla** | El texto por defecto de `CryptoFailure` (`Crypto.kt:207`) y `KDF_PARAMS_INVALID` (`Crypto.kt:87`) llegan tal cual a «Importar copia» (`VaultBackup.kt:183-184`), «Importar como bóveda nueva» (`AppContainer.kt:762-763`) y «Comprobar una copia» (`BackupCheckSession.kt:129-130`). **Código:** una función pura `copyFailureText(e: CryptoFailure): String` en `vault/`. El texto por defecto pasa a «Contraseña incorrecta o copia dañada. Vuelve a elegir la copia y escribe su contraseña.», `KDF_PARAMS_INVALID` pasa a uno de la copia, y lo demás (`KDF_OUT_OF_MEMORY`, `COPY_DAMAGED`…) queda igual. Son 3 sitios de una línea, unas 15 líneas y un test de la función, más un test de fuente de que los 3 sitios la usan. Borrar una bóveda (`AppContainer.kt:688-690`, la maestra de la bóveda, no de una copia) debe usar `masterUnlockErrorText` (fila 43); está en `runMarked` (`:762-763`), así que ese sitio necesita saber de qué acción viene: un parámetro de `runVaultsAction` |

## Pruebas que pide el revisor

**Fila 17** (en `AutoBackupTargetsTest`):
- **P-17a, el exhaustivo con `"1.x"`:** en `everyCombinationOfFolders`, un segundo bucle con `request = LEGACY_A6_REQUEST`, `a6 = null` y los destinos que entran por `LegacyVaultSettings(…, autoBackupReplacePending = true).converted()` en lugar de `withAutoBackupResult(replaceRequested)`. Para cada combinación:
  - `summary.stale == true`;
  - el texto empieza por `a6StaleText(replaced, n)`;
  - `Fate.ADDED_AFTER` no cuenta en `a6Snapshot` (la línea `uri=sin` no está) y no reabre lo cerrado.
- **P-17b, `stale` con la 2.0:** en el mismo exhaustivo, con `A6State("R", masterMark = "m1")` y `a6Decide(…, masterMark = "m2")`, el texto sale con `a6StaleText`; con `"m1"`, no.
- **P-17c, las ramas de `a6StaleText`:** `a6StaleText(1, 1)` contiene «en la carpeta»; `(2, 2)`, «en todas las carpetas»; `(1, 2)`, «en 1 de las 2 carpetas; en las demás puede quedar» (m-2 de la re-revisión 6).
- **P-17d, el camino a, desde el `settings.json` real de la 1.4.3:** el archivo `/seguridad/settings-1.4.3.json` con `"autoBackupReplacePending":true,"autoBackupReplaceTargets":["doc-prueba-1"]` añadidos al JSON (en el test, sin cambiar el recurso). `decodeSettings` y después:
  1. la carpeta falla (`AUTO_BACKUP_WRITE_FAILED`);
  2. un cambio de maestra sin «Borrar…» (la marca de la bóveda cambia: `masterMark` distinto);
  3. la carpeta se recupera y reemplaza con `request = t.requestId`.

  El texto publicado es `a6StaleText(1, 1)`, nunca `AUTO_BACKUP_REPLACED`.
- **P-17e, el camino b, desde el mismo archivo:**
  1. reemplaza;
  2. se cierra (`said = a6Snapshot`);
  3. se añade la carpeta `content://dos`.

  `a6Decide(…)` da `null` (nada que decir), y `a6Snapshot` no cambia.
- **P-17f, que `replaceDone` siga guardándose:** un destino heredado que termina guarda `replaceRequest == "1.x"` y `replaceDone.request == "1.x"`, y sigue siendo de la petición (`inRequest("1.x")`). Cambia `aRequestOf143IsTheRequestOfNow` a `a6StaleText(1, 1)`.

**Fila 18:**
- **P-18:** `aFolderDoneAndRemovedAfterClosingSaysNothing`: dos carpetas hechas, cerrado, `withTargetRemoved` de una hecha, y `a6Decide` da `null`.

**C-1 e I-4:**
- **P-C1a:** `attachmentsFitCountsTheTrash` (función pura, referencias con tamaño declarado): 45 MB vivos + 10 MB en la papelera + 1 MB nuevo, y no cabe.
- **P-C1b:** de fuente: `VaultStore.addAttachment` no llama a `modify {` (que solo da `live`) para el total.
- **P-C1c, la invariante:** de cada `VaultData` que deja la papelera (mandar, restaurar y añadir un adjunto), `checkCopyEntries(data.entries)` no lanza.
- **P-I4:** en `checkCopyEntries`: tamaño -1 da `COPY_DAMAGED`; 5 MB + 1 da `COPY_DAMAGED`; 11 × 5 MB da `ATTACHMENTS_TOTAL_TOO_BIG`.

**I-2 e I-3:**
- **P-I2:** `PASSWORD_CHANGE_REPLACE_COPIES`, `AUTO_BACKUP_REPLACED`, `AUTO_BACKUP_REPLACED_SEVERAL` y `a6PartialText(1, 2)` contienen «antes de la 2.0».
- **P-I3:** `TRASH_DELETE_FOREVER_TEXT`, `trashEmptyText(2)` y `HISTORY_CLEAR_TEXT` no contienen «lo conservan.».

**m-2:**
- **P-m2:** `AppContainer` con un `settings.json` cuyo `perVault` tiene un id que no está en `vaults.json`: tras arrancar, ese id no está y se llamó a `releaseFolder(uri)`.

**Fila 42:**
- **P-42:** `copyFailureText(CryptoFailure())` es el texto de la copia, y `copyFailureText(CryptoFailure(KDF_OUT_OF_MEMORY))` sigue siendo `KDF_OUT_OF_MEMORY`. Más el test de fuente de los 3 sitios.

## Dudas

1. **Fila 17, la repetición única** (I-1, «Qué más cambia»): con la pertenencia nueva, una foto `said` de `"1.x"` cerrada con una carpeta añadida después ya no coincide, y el primer desbloqueo tras el arreglo repite el resumen, con el texto prudente.
   - **Opción a:** se acepta (es verdad, una vez, y solo afecta a quien tenga ya una petición `"1.x"` cerrada en una versión de prueba de la 2.0). Recomendada: ningún usuario de la 1.4.3 tiene todavía un `said`.
   - **Opción b:** comparar S quitándole las líneas de las carpetas que no son miembros.
2. **¿C-1 es Critical o Important?** Lo pongo Critical porque la app escribe copias que no lee y la rotación se lleva las buenas, sin aviso. Las condiciones (rozar los 50 MB de adjuntos y tener algo con adjuntos en la papelera) lo hacen poco frecuente. Que lo decida el controlador.
3. **Filas 20 y 21:** propongo aceptarlas (fallan del lado seguro y ningún texto queda falso), con el arreglo escrito por si el director prefiere cerrarlas.

## Lo que no pude comprobar

- No ejecuté nada: cada afirmación sale de leer el código. Las pruebas de arriba lo confirman.
- `ui/screens/BackupCheckScreen.kt` (cambio de 14 líneas), `settings/Guide.kt`, `settings/RenameNotice.kt` y el resto de `SettingsScreen.kt` (más allá de 150-540) no los leí enteros. En particular, que el modo sencillo solo filtra Herramientas es de la parte C (fila 27).
- `VaultUpgrade.kt:61` y el nombre del botón «Seguir sin copia» del paso a v4, para el texto de la fila 39: los leí solo en la línea citada.
- Que `ExternalStorageProvider` no sobrescriba al cambiar el nombre a uno que ya existe (i-2): solo en el móvil.
- La precisión de `mtime` y `setLastModified` (D-3, fila 13 bis): solo en el móvil.

## Lo que se guarda sin cifrar (para la política; fila 8)

Los dos archivos están en la carpeta privada de la app (`filesDir`), fuera de la copia del sistema (`allowBackup` desactivado). Se escriben de forma atómica (`writeTextAtomically`). `appJson` no escribe los valores por defecto ni los nulos (`core/Json.kt:5-10`), así que un campo que no aparece tiene su valor por defecto.

### `settings.json` (`Settings`, `settings/Settings.kt:40-101`)

| Campo | Qué es | Desde |
|---|---|---|
| `lockAfter` | Bloqueo tras no usarla: `ON_EXIT`, `MIN_1`, `MIN_5` o `MIN_15` | 1.x |
| `clipboard` | Borrar el portapapeles a los 15, 45 o 90 s | 1.x |
| `theme` | `DARK`, `LIGHT` o `SYSTEM` | 1.x |
| `noticeAccepted` | «Antes de empezar» aceptado | 1.x |
| `clipboardHistoryNoticeAccepted` | Aviso A14 aceptado | 1.x |
| `renameNoticePending` | Aviso del nombre nuevo pendiente | 1.3 |
| `style` | `GLASS` o `SOLID` | 2.0, 5 bis |
| `palette` | `NIGHT`, `OCEAN`, `FOREST`, `SUNSET`, `GRAPHITE`, `SAND`, `WALLPAPER` o `CUSTOM` | 2.0, 5 bis |
| `custom` | `accentHue` y `backgroundHue` (0 a 11) y `glass` (`LOW`, `MEDIUM` o `HIGH`) | 2.0, 5 bis |
| `reduceTransparency`, `highContrast` | Interruptores de aspecto | 2.0, 5 bis |
| `appearanceNoticePending` | Línea R-C8 pendiente | 2.0, 5 bis |
| `simpleMode` | Modo sencillo (null: sin decidir) | 2.0, 10 |
| `guideSeen` | 0, 1 o 2: qué guía se vio. **El 1 revela que en el móvil hubo Clavero 1.x** | 2.0, 10 |
| `missionsOff` | «Sugerencias de «Salud»» apagadas (solo se escribe si es `true`) | 2.0, 10 |
| `perVault` | Un objeto por **id local** de bóveda (`principal` o 32 caracteres hexadecimales aleatorios), con lo de abajo | 2.0, 2 |

**Por bóveda** (`VaultSettings`, `Settings.kt:170-195`):

| Campo | Qué es |
|---|---|
| `kdf` | `STANDARD` o `HIGH`: el coste de Argon2id elegido |
| `clockHighWaterMs` | Marca A11: la hora más alta vista (ms desde 1970) |
| `targets` | Hasta 3 destinos (abajo) |
| `autoBackupOffForNewVault` | La copia se apagó al crear una bóveda nueva |
| `pastTargets` | Hasta 6 destinos antiguos, con los mismos campos |
| `a6.id` | UUID aleatorio de la última petición de A6 (o `"1.x"`) |
| `a6.said` | La foto del último resumen cerrado: la petición, los **URI de las carpetas** y por carpeta `pendiente`, `sin` o `hecha:<borradas>:<sin apuntar>` |
| `a6.masterMark` | SHA-256 en hexadecimal de `"a6|" + sal` del archivo de la bóveda. La sal ya está sin cifrar en `vault.bin`. Solo dice si la bóveda se volvió a cifrar desde la petición |

**Por destino** (`BackupTargetSettings`, `vault/BackupSchedule.kt:29-60`):

| Campo | Qué es |
|---|---|
| `uri` | El URI SAF de la carpeta. Con el proveedor de almacenamiento, lleva la **ruta de la carpeta** (p. ej. `primary%3ACopias`) |
| `keep` | 3, 5 o 10 |
| `schedule` | `ON_CHANGE`, `DAILY`, `WEEKLY` o `MONTHLY` |
| `lastAt` | La hora de la última copia |
| `lastError` | Un texto fijo en español (nunca una ruta ni un mensaje de excepción) |
| `copies` | Los ids de documento de las copias automáticas. Con el proveedor de almacenamiento, el id lleva la ruta y el **nombre del archivo**, con la fecha y la hora de cada copia |
| `importCopies` | Lo mismo, de las copias de antes de importar y de antes de la 2.0 |
| `replacePending`, `replaceTargets`, `replaceTold` | La petición de A6 pendiente: los ids que hay que borrar y los fallos ya dichos (textos fijos) |
| `replaceRequest` | El UUID de la petición en la que está la carpeta |
| `replaceDone` | `request`, `deleted` y `unlisted` (números) |
| `copiedMark` | La marca de D-3: SHA-256 en hexadecimal de `"<mtime de vault.bin>|<memoria>,<iteraciones>,<paralelismo>|<vaultTag>"` (`VaultStore.kt:1735-1737`). `vaultTag` son 16 bytes aleatorios que van **cifrados** dentro de la bóveda, así que la marca no deja leer nada: solo cambia cuando la bóveda cambia |

**No guarda:** nombres de bóvedas, de entradas o de carpetas de la bóveda; ninguna contraseña, clave ni secreto; nada de lo que se busca en Ajustes (la consulta vive en memoria, `SettingsScreen.kt:213`). «Ahora no» de las misiones, en memoria.

**Lectura de un `settings.json` de la 1.4.3:** los campos de arriba del archivo (`autoBackupUri`, `autoBackupKeep`, `autoBackupCopies`, `autoBackupReplacePending`… `SettingsStore.kt:198-214`) se leen como la bóveda `principal` y se escriben ya en `perVault`. Nunca vuelven a escribirse arriba.

### `vaults.json` (`VaultRegistry`, `vault/VaultRegistry.kt:38-42`)

| Campo | Qué es |
|---|---|
| `vaults[].id` | El id local (`principal` o 32 hexadecimales aleatorios) |
| `vaults[].color` | `RED`, `ORANGE`, `YELLOW`, `GREEN`, `TEAL`, `BLUE`, `PURPLE` o `GRAY` |
| `vaults[].order` | El orden en la lista. Su número revela **cuántas bóvedas** hay |
| `lastOpened` | El id de la última bóveda abierta |

Ningún nombre (opción A del dueño).

### Otros archivos sin cifrar que no son de la parte B (para la parte D)

- Las preferencias de la huella por bóveda.
- Los lugares de los selectores (`PickerPlaces`, 1.4.2).
- La carpeta de cada bóveda (`files/vaults/<id>/`), cuya existencia también revela cuántas hay.

## Revisión experto-seguridad-cripto

- **Formato:** C-1 rompe la garantía de que una copia `seguridad-backup` que escribe la app se puede leer (`VaultStore.kt:805`, `:1298-1302` frente a `BackupCheck.kt:204-205` y `VaultStore.kt:1491`). Es lo primero que hay que arreglar. No cambia ningún formato: es un límite al añadir.
- **Veracidad de borrados (A6):**
  - I-1 (`A6Text.kt:33`, `:44`, `:57`), I-2 (`ToolTexts.kt:508`, `AutoBackup.kt:152-164`, `A6Text.kt:159`) e I-3 (`TrashTexts.kt:16`) son textos sobre qué copias existen todavía, y por tanto sobre qué se puede abrir con una contraseña anterior. Por la skill, Important.
  - El borrado en sí está bien: solo ids de las listas propias, nunca por nombre (`AutoBackup.kt:1016-1062`), y solo después de escribir y comprobar la copia nueva (`:840-852`).
- **Marcas sin cifrar:**
  - `masterMark` (`VaultStore.kt:442-446`) es un SHA-256 de la sal, con separación de dominio; la sal ya es pública.
  - `copiedMark` (`:1735-1737`) es un SHA-256 con un `vaultTag` aleatorio que va cifrado.
  - Ninguna de las dos es un MAC ni lo necesita; ninguna revela la contraseña ni la clave.
  - Lo que sí revelan los ajustes es metadato: las rutas de las carpetas y las fechas de las copias (`copies`) y cuántas bóvedas hay. Va a la política (fila 8 y la tabla de arriba).
- **Comprobar y usar (b):**
  - `runDue` y `runChanged` vuelven a leer cada destino bajo el cerrojo del motor (`AutoBackup.kt:488-490`, `:526-527`);
  - la generación protege contra una importación a mitad (`:836-844`, `:1035`, `:1043`);
  - la petición se guarda antes de tocar ninguna carpeta (`:651-653`);
  - el borrado de una bóveda descarta sus resultados tardíos (`AppContainer.kt:851`, `:866`, `:879`).
- **Sin cambios en** la red, los permisos, los registros y la criptografía: solo SHA-256 de `java.security`. Ningún log.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - un solo `Mutex` por motor (`AutoBackup.kt:447`), y dentro el de los ajustes (`SettingsStore.kt:30`), siempre en ese orden;
  - `copyBeforeUpgrade` y `runDue` del primer desbloqueo se serializan en él (i-6);
  - el ámbito de cada bóveda solo se cancela al borrarla (`AppContainer.kt:402`, `:698-699`).
- **Persistencia y ciclo de vida:**
  - `settings.json` y `vaults.json` son atómicos por separado. Entre los dos, el orden es seguro: primero el registro y después los ajustes, así que lo que queda es un ajuste huérfano y nunca una bóveda sin ajustes que pierda una lista. El huérfano es m-2;
  - los resultados de A6 en memoria (`AutoBackup.kt:377`) se pierden con el proceso, y se aceptan (filas 20 y 21).
- **Hilo principal:**
  - copias, rotación, lecturas de ajustes y la comprobación de copias van en IO o en Default (`AutoBackup.kt:450-458`, `SettingsStore.kt:37`, `:162`, `BackupCheck.kt:84-103`);
  - el buscador de Ajustes compara constantes cortas, con la consulta acotada (`SettingsSearch.kt:95-104`).
- **Compose:**
  - el único `rememberSaveable` de la parte B lleva un URI que no es secreto (`SettingsScreen.kt:301`);
  - el diálogo de A6 lee su estado de `AppContainer` y pinta lo que da una función pura (`SettingsScreen.kt:500-526`, `a6DialogView`).
- **Rendimiento:** C-1 se arregla sin coste (una suma sobre las referencias de adjuntos, ya en memoria).

## Textos para el Abogado Digital (revisión final)

- **I-2, propuestas:**
  - botón «Hacer una copia nueva y borrar las anteriores (automáticas, de antes de importar y de antes de la 2.0)»;
  - en `AUTO_BACKUP_REPLACED`, `_SEVERAL` y `a6PartialText`: «…las copias automáticas, las de antes de importar y la de antes de la 2.0 hechas antes del cambio de contraseña…»;
  - en los textos de copias sin apuntar: «(automática, de antes de importar o de antes de la 2.0)».
- **I-3 (fila 30, junto con la 19):** `COPIES_KEEP_IT` → «Las copias ya hechas (automáticas o exportadas) pueden conservarlo; la copia automática borra las más antiguas, así que con el tiempo deja de estar en ellas.»
- **C-1 e I-4 (fila 37), texto nuevo al añadir:** «Con este adjunto, los adjuntos de la bóveda (también los de la papelera) pasarían de 50 MB. Quita algún adjunto o vacía la papelera para añadir otro.»
- **I-1 (fila 17):** quien actualiza con una petición pendiente de la 1.4.3 verá `a6StaleText` («Se terminaron de borrar las copias que la app tenía apuntadas de antes de un cambio de contraseña anterior, en la carpeta. Las copias hechas después de ese cambio no se han borrado: si cambiaste la contraseña otra vez, puede que alguna se abra con una contraseña anterior. Míralo a mano.»). Sigue la propuesta de la fila 19 («Esta petición no borró las copias hechas después de ese cambio: …»).
- **Fila 39:**
  - al exportar: «… Quita adjuntos o entradas grandes y vuelve a exportarla.»;
  - en el paso a v4: «Puedes seguir sin copia: la bóveda pasa al formato nuevo igual; las versiones anteriores ya no la abrirán.».
- **Fila 42:** «Contraseña incorrecta o copia dañada. Vuelve a elegir la copia y escribe su contraseña.»
- **m-2:** `VaultsTexts.kt:48` («… y sus ajustes») es verdad cuando se aplique m-2. Sin m-2, si falla la escritura, deja de serlo.
- **Política (fila 8):** la tabla «Lo que se guarda sin cifrar», campo a campo. Lo nuevo frente a la 1.4.3:
  - hasta 3 carpetas, con su calendario y cuántas copias guardan;
  - las rutas y las fechas de las copias en los ids;
  - las marcas SHA-256 opacas (`masterMark`, `copiedMark`);
  - el id aleatorio de la petición de A6 y su foto, con los URI de las carpetas;
  - el aspecto, el modo sencillo, la guía vista (que revela que hubo Clavero 1.x) y las misiones apagadas;
  - y en `vaults.json`, cuántas bóvedas hay, su color, su orden y la última abierta.
