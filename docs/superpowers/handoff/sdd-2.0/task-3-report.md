# Tarea 3 de Clavero 2.0: informe del implementador

Papelera de 30 días, «Deshacer» después de mandar a la papelera e historial por entrada. Rama `ccr-0aa27f95-u038cd`, sin push. Base `7bf3121` (el «tarea 3 en marcha» del controlador, sobre `3b32c65`).

## Commit

| SHA | Asunto |
|---|---|
| `23e3887` | 2.0, tarea 3: papelera, deshacer e historial |

## Qué se ha hecho

**Papelera (`vault/Trash.kt`, `vault/VaultStore.kt`).**
- `moveToTrash(ids): Int`, `restore(ids): Int`, `deleteForever(ids): Int` y `emptyTrash(): Int` (`VaultStore.kt:933`, `:954`, `:978`, `:981`). Cada una es un guardado y devuelve 0, sin guardar nada, si no hay nada que hacer o la bóveda está bloqueada.
- `deleteForever` y `emptyTrash` solo quitan entradas que están en la papelera, nunca una viva. Sus adjuntos los borra la recolección que sigue al guardado (`commit` → `collectGarbage`). Mientras la entrada está en la papelera, sus adjuntos se quedan, y las copias los llevan (ya era así desde la tarea 1).
- `delete(id)` (`:922`) llama a `moveToTrash(setOf(id))`. **Excepción (decisión 2):** la identidad y los contactos no van a la papelera y se borran del todo, como antes.
- `restore` no trae una identidad si la bóveda ya tiene otra. La app nunca manda una identidad a la papelera, pero una copia hecha en otro sitio podría traerla.
- `upsert` de un id que está en la papelera devuelve `false` y no guarda nada (por ejemplo, una pantalla que se quedó abierta con esa entrada): nunca hay dos entradas con el mismo id. `addAll` también salta los ids de la papelera.

**Vaciado al desbloquear y regla del reloj (foco 3).**
- `VaultData.purgedTrash(now, clockMark)` (`Trash.kt:35`) siempre apunta `lastUnlockAt = now`.
- Quita lo que lleva en la papelera **más de** 30 días (`TRASH_MS`, estricto), salvo que `now - lastUnlockAt > 2 días` **y** `now - clockMark > 2 días` (`:36`). En ese caso no quita nada en este desbloqueo.
- Una entrada borrada «en el futuro» (un reloj que se atrasó) no se quita nunca.
- Con la maestra: `afterMasterUnlock` tiene un parámetro nuevo, `prepare` (`Vault.kt:467`), que `unlockLocked` usa con la hora y la marca leídas **antes** de que el desbloqueo le diga la hora a nadie (`VaultStore.kt:532`). Una importación no pasa `prepare`, así que una copia trae su papelera tal cual (foco 5).
- Con la huella: ahora un desbloqueo con la huella también escribe, porque tiene que apuntar `lastUnlockAt` y vaciar lo que toque. Es un guardado de contabilidad (`mustSave = false`): un disco lleno no deja la bóveda cerrada (`VaultStore.kt:634`).
- La marca que se lee es el parámetro nuevo `trashClockMark` (`:205`), que por defecto es `clockHighWater`. La app le pasa la suya (decisión 1).

**Deshacer.**
- `UndoTrash(store, elapsed)` (`Trash.kt:47`) guarda en memoria los ids que movió de verdad el último envío (`VaultStore.trash`, `internal`), junto con la hora monotónica y la `lockEpoch` de la bóveda.
- `undo()` llama a `restore` solo si han pasado menos de 5 s (`UNDO_TRASH_MS`) y no ha habido ningún bloqueo desde entonces (`:57`). `offer` es un `StateFlow` con cuántas entradas se movieron, para la pantalla.
- Hay una por bóveda, en su `Slot` (`AppContainer.kt:427`), y `AppContainer.undoTrash` da la de la bóveda activa (`:462`). Cambiar de bóveda bloquea la anterior, así que se olvida, y la nueva no tiene nada que deshacer.

**Historial (`vault/EntryHistory.kt`).**
- `withVersion(previous, now)` (`:34`) pone delante `EntryVersion(now, previous)`, sin historial, sin adjuntos y sin `passwordHistory` (`:16`), cuando cambió algún campo (`changedFrom`).
- No cuentan como cambio `updatedAt`, `lastUsedAt`, el historial, los adjuntos ni la lista heredada. Después aplica los topes con `fittedHistory()`, que ahora es `internal`: se van primero las versiones más viejas. Si nada cambió, devuelve el mismo objeto.
- `upsert` la usa (`VaultStore.kt:770`): el historial, la lista heredada y los adjuntos los guarda la tienda, mande lo que mande el editor. Las entradas personales nunca tienen historial.
- `restoreVersion(entryId, savedAt)` (`:998`): la versión vuelve como un guardado nuevo, y lo que había queda como otra versión. Conserva el id, `createdAt`, `lastUsedAt` y los adjuntos. Devuelve `false` si la versión es igual a la entrada.
- `clearHistory(entryId)` (`:1014`) y `clearAllHistory()` (`:1021`, también las entradas de la papelera) borran las versiones **y** la lista heredada `passwordHistory`.

**M-4 (conciliar `passwordHistory` con `history`): decisión.**
- `passwordHistory` se queda como **campo heredado de solo lectura**: la 2.0 no lo vuelve a escribir. `withNewPassword` se ha quitado y `History.kt` solo conserva `MAX_PASSWORD_HISTORY`, con la explicación.
- Cada cambio de contraseña, en el editor y en «Reemplazar» al importar (`MergePlan.kt:381`), va a `history` con `withVersion`.
- `oldPasswords()` (`EntryHistory.kt:45`) lee las dos listas:
  - de `history`, cada versión `LOGIN` cuya contraseña no está vacía y no es la de la versión siguiente, con su `savedAt`;
  - de la lista heredada, las que el historial ya no tiene;
  - cada una una sola vez, de la más nueva a la más vieja.
- Así ninguna contraseña antigua desaparece de la ficha aunque `fitted()` recorte su versión. La ficha (`VaultEntryScreen.kt:196`) y «Salud» (`Health.kt:54`, la edad de la contraseña) leen `oldPasswords()`.
- Solo «Borrar historial» vacía la lista heredada.
- El paso de v3 a v4 no cambia: copia la lista en `history` y la conserva.
- Test con una entrada grande, notas de 40 KiB y 5 contraseñas antiguas (`EntryHistoryTest.upgradeOfLargeEntryKeepsNewestAndEveryOldPassword`): después del paso queda solo la versión más nueva (`clave-vieja-5`, `savedAt` 5000) y la ficha sigue enseñando las 5, también después de volver a abrir.

**Importar (foco 5) y «Salud».**
- `planMerge` no compara con lo que está en la papelera (`MergePlan.kt:117`). Las etiquetas que solo tiene una entrada de la papelera cuentan como nuevas en `TagBudget` y en `newTags` (`liveTags`, `:156`). `mergeImport` deja la papelera tal cual.
- `vaultHealth` y `uniquePasswords` no cuentan la papelera (`Health.kt:41`), ni para las repetidas ni para el 2FA.

**Interfaz.**
- `ui/screens/TrashScreen.kt` (ruta `vault/trash`). Una lista ordenada por fecha de borrado, con el título en texto plano y «Se quita en N días». Cada fila tiene «Restaurar» y «Borrar para siempre» (con confirmación); arriba, «Vaciar papelera» (con confirmación). A la vista, O-2 (`TRASH_SHORT`, `COPIES_KEEP_IT`); el resto, bajo «Más información».
- `ui/screens/EntryHistoryScreen.kt` (ruta `vault/entry/{id}/history`). Cada versión enseña «Hasta el …», el título y «Ver campos» (los campos como en la ficha, con `Field`, que ahora es `internal`: contraseña, frase, notas de una nota, clave 2FA y campos `HIDDEN` ocultos hasta pulsar «Mostrar», y se copian como sensibles). Debajo, «Restaurar esta versión»; arriba, «Borrar historial» (con confirmación y O-2 a la vista).
- La ficha: «Mover a la papelera» con «¿Mover «X» a la papelera?» y «Puedes restaurarla desde la papelera durante 30 días.». También «Versiones (N)» y «Contraseñas anteriores» desde `oldPasswords()`.
- La lista (`VaultScreen.kt:194`) enseña la barra de «Deshacer» durante 5 s (`withTimeoutOrNull`). Se cierra si la bóveda se bloquea, y entonces se olvida. En el menú, «🗑 Papelera (N)».
- Textos en `vault/TrashTexts.kt`.

## Decisiones tomadas aquí (para el controlador)

1. **La marca del reloj de la papelera no es la de A11 tal cual.**
   - Por qué: `SeguridadApp` sube la marca de A11 al arrancar (`rememberClock(System.currentTimeMillis())`), y esa marca se guarda. Con ella, «`now` supera la marca de antes del desbloqueo en más de 2 días» no se cumple casi nunca en la app real: un reloj adelantado con la app cerrada sube la marca al arrancar, y la papelera se vaciaba en el primer desbloqueo. El test del foco 3 pasaba con una marca falsa, pero en el móvil no protegía nada.
   - Qué he hecho:
     - `AppContainer.appStarted(now)` (`AppContainer.kt:266`) sube las marcas de A11 igual que antes; A11 no cambia.
     - `AppContainer.trashClockMark(id)` (`:285`) es la hora más alta vista **en este proceso** por un uso: un desbloqueo, un toque o una comprobación de la maestra, de cualquier bóveda, como A11. El arranque no cuenta. Vive solo en memoria.
   - Cómo queda: en un proceso nuevo, la papelera se vacía si el último desbloqueo fue hace 2 días o menos; si no, se vacía en el siguiente desbloqueo normal (lo que ya aceptaba el ruling de la fila 3).
   - `VaultStore` conserva la lectura del ruling: por defecto lee `clockHighWater`.
   - Test: `TrashTest.appStartIsNotATrashWitness`.
2. **La identidad y los contactos no van a la papelera.** Sus pantallas tienen confirmaciones propias («no hay forma de recuperarla», `DELETE_IDENTITY_CONFIRMATION`). Mandarlas a la papelera haría falso ese texto. Además, restaurar una identidad mientras hay otra rompería «una sola identidad». `delete` las sigue borrando del todo. La papelera es para lo de la pestaña Bóveda, que es lo que dice el diseño (deslizar, menú, selección, carpeta). Si el dueño las quiere en la papelera, hay que cambiar los textos de la identidad, que van al Abogado.
3. **La firma `purgedTrash(now)` del brief lleva un segundo parámetro**, `clockMark`: la regla necesita la marca y la función sigue siendo pura.
4. **`UndoTrash.moveToTrash(ids)`** es lo que llaman las pantallas, y solo recuerda los ids que se movieron de verdad. `canUndo()` y `offer` sirven a la pantalla.
5. **«Borrar historial» vacía también `passwordHistory`.** Si no, el usuario creería que las contraseñas antiguas ya no están, y seguirían en la bóveda.

## Tests y resultados

Nuevos:
- **`TrashTest` (15):**
  - los ocho del brief: `trashedLeaveEntries`, `restoreBringsBack`, `deleteForeverRemovesAttachments`, `purgeAfter30Days`, `clockJumpDoesNotEmptyTrash` (foco 3), `backupKeepsTrash` (foco 5), `undoWithin5s` y `undoForgottenOnLock`;
  - además: `personalEntriesSkipTheTrash`, `restoreNeverMakesASecondIdentity`, `purgeAtFingerprintUnlock`, `undoFollowsTheActiveVault` (`AppContainer`, cambiar de bóveda), `appStartIsNotATrashWitness` y `trashTextsSayWhatStays` (O-2, y ni «irrecuperable» ni «borrado seguro»).
- **`EntryHistoryTest` (11):**
  - los ocho del brief: `editAddsVersion`, `noChangeNoVersion`, `lastUsedAtIsNotAVersion`, `capAt20`, `capAt64KiB`, `restoreVersionMakesNewVersion`, `clearHistoryEmptiesIt` y `versionsHaveNoAttachments`;
  - además: `personalEntriesKeepNoHistory`, `oldPasswordsJoinLegacyAndHistory` y `upgradeOfLargeEntryKeepsNewestAndEveryOldPassword` (M-4).
- **`MergePlanTest.trashIsNotADuplicate`** (foco 5).
- **`HealthTest`:** `trashNotCounted` y `oldReadsTheHistory`.

Cambiados por el cambio de comportamiento (no por comodidad):
- `HistoryTest`: `withNewPassword` desaparece; ahora comprueban `withVersion` y `oldPasswords`, y `keepsAtMostFive` pasa a ser `legacyListIsStillRead`.
- `MergePlanTest` (cuatro aserciones) y `RedTeamRecheckTest.resistsAndRecordsWhatReplaceTakesAsItComes`: la contraseña antigua se lee con `oldPasswords()`. `replaceFollowsTheRule` comprueba además que la versión está en `history` y que `passwordHistory` queda vacía.
- `VaultStoreImportTest.applySavesEverythingInOneWrite`: `oldPasswords()`.
- `AttachmentsTest.deleteEntryRemovesItsFiles`: después de `delete` el archivo se queda (está en la papelera) y se va con `deleteForever`.
- `PlatformSourcesTest`: el borrado de la ficha va por `undo.moveToTrash(setOf(entry.id))`, sin un segundo `onBack`. Los textos de R9 fijan `moveToTrashTitle(plainReportText(entry.title))` y los de `TrashScreen` y `EntryHistoryScreen`.

Resultados finales, antes del commit, sobre el árbol de `23e3887`:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2010** tests, 0 fallos (base 1982). Lint: 0 errores y 20 avisos, ninguno en los archivos de esta tarea.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2003** tests, 0 fallos (base 1975).

## Pruebas TDD (RED y GREEN)

- **RED de compilación**, con todos los tests nuevos escritos antes del código: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.TrashTest'`. Salida:
  ```
  e: …/HealthTest.kt:11:28 Unresolved reference 'withVersion'.
  e: …/EntryHistoryTest.kt:57:62 Unresolved reference 'oldPasswords' on receiver of type 'VaultEntry'.
  e: …/EntryHistoryTest.kt:118:23 Unresolved reference 'restoreVersion' on receiver of type 'VaultStore'.
  e: …/EntryHistoryTest.kt:134:22 Unresolved reference 'clearHistory' on receiver of type 'VaultStore'.
  e: …/EntryHistoryTest.kt:143:11 Unresolved reference 'moveToTrash' on receiver of type 'VaultStore'.
  e: …/EntryHistoryTest.kt:144:27 Unresolved reference 'clearAllHistory' …
  ```
- **Primer GREEN parcial:** `106 tests completed, 4 failed`. Dos eran fallos del test (`trashNotCounted` esperaba `{b}` sin contar `NO_2FA`; `lastUsedAtIsNotAVersion` leía `clock` antes de que `edit` lo subiera), corregidos en el test. Los otros dos eran tests antiguos que leían `passwordHistory` después de «Reemplazar», actualizados por la decisión de M-4.
- **RED de comportamiento por mutación**, para los tests que compilaban sin la API nueva:
  - sin el filtro de la papelera en `planMerge` ni en `vaultHealth`/`uniquePasswords`: `MergePlanTest.trashIsNotADuplicate` y `HealthTest.trashNotCounted` dan `2 tests completed, 2 failed`; con el filtro, verdes;
  - con `val jumped = false` en `purgedTrash`: `TrashTest > clockJumpDoesNotEmptyTrash FAILED (AssertionError at TrashTest.kt:176)`, y los demás en verde; con la regla, verde.
- **GREEN:** `TrashTest tests="14"` (15 con `trashTextsSayWhatStays`), `PlatformSourcesTest tests="31"`, `SourceTextsTest tests="12"`, todos con `failures="0"`. Después, la suite completa (arriba).
- **Sin TDD, dicho con franqueza:**
  - `trashTextsSayWhatStays` se escribió después de `TrashTexts.kt`, como guarda de O-2.
  - `personalEntriesSkipTheTrash` y `restoreNeverMakesASecondIdentity` se reescribieron al tomar la decisión 2; antes probaban la papelera para la identidad.
  - Las pantallas no tienen test de pantalla: no hay emulador; queda para Maestro, en la tarea 11.

## Autorrevisión

- **Cada desbloqueo con la huella escribe ahora el archivo** (`lastUnlockAt`, con la revisión + 1 y un nonce nuevo). Es un guardado de contabilidad: conserva la fecha de modificación, así que no provoca ninguna copia automática, y si falla la bóveda se abre igual. Es lo mismo que ya hacía el desbloqueo con la maestra con `masterCheckedAt`.
- **`VaultStore.kt` pasa de unas 1 315 a unas 1 450 líneas.** La papelera y el historial podrían salir a su propia pieza junto con la importación (M-7 de la tarea 2, aplazado a la revisión final). No lo he hecho para no ampliar el alcance.
- **La barra de «Deshacer»** se enseña al volver a la lista: la ficha se va sola cuando la entrada desaparece. Su `LaunchedEffect` empieza al recomponer la lista, así que puede quedar algo menos de los 5 s de `UndoTrash`; pulsar después de ese tiempo no hace nada. Dos envíos seguidos de una entrada cada uno no reinician la barra (la clave del efecto no cambia), y «Deshacer» trae el último envío.
- **`clearAllHistory`** existe en la API, pero no tiene botón: el brief solo pide «Borrar historial» por entrada. Un botón en Ajustes encaja en la tarea 10.
- **«Comprobar una copia»** cuenta en su informe también las entradas de la papelera, que sí están en la copia. No dice cuántas están en la papelera; podría añadirlo la tarea 8.

## Dudas

1. **Decisión 1** (la marca del reloj de la papelera que no sube al arrancar): cambia la lectura literal del ruling de la fila 3, que no protegía en la app real.
2. **Decisión 2** (la identidad y los contactos fuera de la papelera), que se aparta de «`delete(id)` pasa a llamar a `moveToTrash`».
3. **Límite que queda:** ninguna regla sin una hora de confianza distingue un reloj adelantado que **se queda** adelantado. El primer desbloqueo no vacía nada, pero el segundo, con la misma hora mala, ve `lastUnlockAt` reciente y vacía lo que esa hora dice que tiene más de 30 días. El diseño solo pide que el salto no vacíe la papelera en ese desbloqueo. Si se quiere más, una opción es no vaciar nunca más de lo que el reloj monotónico (`elapsedRealtime`) confirma dentro del mismo arranque del móvil. Necesita la decisión del dueño.

## Revisión experto-seguridad-cripto

Modelo de amenaza de esta tarea:
- un reloj del móvil puesto mal, sin querer o a propósito, que vacía la papelera: es pérdida de datos, no confidencialidad;
- una copia o un archivo manipulado con una papelera o un historial hostiles;
- datos que siguen en la memoria o en el disco después de «borrar».

Recorrí `references/auditoria.md` (no hay ninguna referencia sobre el borrado seguro ni sobre la remanencia) con lo que dice la skill sobre la honestidad epistémica.

- **Ninguna primitiva nueva.** No hay criptografía nueva: la papelera y el historial van dentro del blob de datos v4, cifrados y autenticados con la clave de datos como lo demás (`VaultCrypto.write`, `Vault.kt`). `lastUnlockAt` va dentro del blob autenticado, así que nadie puede ponerlo «reciente» desde fuera sin la clave.
- **Ninguna promesa de borrado que la app no cumple (O-2, remanencia).** Los textos dicen «se quita de la bóveda» y que las copias lo conservan (`TrashTexts.kt`), nunca «irrecuperable» ni «borrado seguro»; `trashTextsSayWhatStays` lo fija.
  - Lo que se queda: el JSON viejo pasa por `writeTextAtomically` (renombrado), así que los bloques del archivo anterior pueden seguir en el almacenamiento flash hasta que se reutilicen. Las `String` de Kotlin con las contraseñas de las versiones no se pueden borrar de la memoria, como las demás contraseñas de la bóveda (está en el KDoc de `VaultEntry.identitySeed`).
  - No es un hallazgo nuevo; es el límite que ya conocía el proyecto, y los textos no prometen nada más.
- **Regla del reloj (foco 3):** `Trash.kt:36` y la decisión 1 (`AppContainer.kt:266`, `:285`).
  - La marca se lee **antes** de que el propio desbloqueo cuente como uso (`VaultStore.kt:532`, `:613`).
  - Es estricta: ante la duda, no se borra nada (fallo del lado conservador).
  - El límite está en «Dudas», 3. La severidad es baja: hace falta un reloj adelantado que dure dos desbloqueos, y aun así las copias conservan lo borrado.
- **Comprobar y usar a la vez (decisión (b)):**
  - todas las operaciones de la papelera y del historial van dentro de `modifyData` (mutex, una sola lectura del estado, `fittedForSave`, `commit` que no se puede cancelar);
  - `UndoTrash` comprueba la época del bloqueo, así que un «Deshacer» después de un bloqueo no restaura nada (`Trash.kt:57`; tests `undoForgottenOnLock` y `undoFollowsTheActiveVault`);
  - un `upsert` desde una pantalla vieja no puede tapar una entrada de la papelera con su mismo id (`VaultStore.kt:770`).
- **Lo importado es dato hostil (decisión (c)):**
  - una copia trae su papelera tal cual: no se interpreta, solo se muestra el título con `plainReportText` (`TrashScreen.kt`, fijado en `PlatformSourcesTest`);
  - las versiones de una copia se leen sin historial ni adjuntos (`withPlainVersions`, de la tarea 1) y se escriben sin la lista heredada (`EntryHistory.kt:16`);
  - `restore` no deja crear una segunda identidad desde la papelera de una copia (`restoreNeverMakesASecondIdentity`);
  - los topes del historial siguen en vigor (`fittedHistory`, `checkLimits`).
- **Secretos en la interfaz:**
  - el historial enseña ocultos las contraseñas, las frases, las notas de una nota, las claves 2FA y los campos `HIDDEN` (`EntryHistoryScreen.kt`, `VersionCard`), y los copia con `copySensitive` (`Field`);
  - no hay nada en `rememberSaveable` ni en un `Bundle`: el estado de las pantallas es `remember`, y `MoreInfo` (no es secreto);
  - los diálogos llevan `FilterObscuredTouches`;
  - las pantallas se van al bloquear (cuando `entry == null` o la bóveda no está `Unlocked`, `onBack`).
- **Duplicados de importación (foco 5):** lo de la papelera no cuenta como duplicado (`MergePlan.kt:117`). Esto es a propósito: «Reemplazar» nunca cambia una entrada de la papelera, y un archivo que trae lo mismo entra como entrada nueva, visible para el usuario.
- **Sin registros, sin red, sin permisos ni dependencias nuevos.** Ningún `Log` y ningún componente exportado nuevo; las únicas rutas nuevas son internas de Navigation y no tocan el manifiesto.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - todo cambio pasa por el mutex de `VaultStore` (`modifyData`), y los desbloqueos siguen en `NonCancellable`;
  - el guardado de la huella (`VaultStore.kt:634`) cifra en `compute`, igual que los demás guardados;
  - `UndoTrash` solo usa estado `@Volatile` y un `StateFlow` (`Trash.kt:47`);
  - `clockUsed` va bajo `clockLock`, como las marcas de A11 (`AppContainer.kt:285`).
- **Ciclo de vida y varias bóvedas:**
  - una `UndoTrash` por `Slot` (`AppContainer.kt:427`), y la de la bóveda activa en `:462`;
  - las pantallas nuevas viven dentro de `VaultScoped`/`key(vault)` (`AppNavHost.kt:277`), así que se rehacen al cambiar de bóveda;
  - la barra de «Deshacer» se olvida al bloquear (`VaultScreen.kt:194`).
- **Compose:**
  - `LazyColumn` en la papelera y en el historial, con claves estables (el id; para las versiones, `savedAt` y su índice);
  - la cuenta atrás de «Se quita en N días» se actualiza una vez por minuto, no por segundo;
  - las versiones están plegadas por defecto, así que no se componen campos secretos sin necesidad.
- **Navegación:**
  - `backOnce(nav)` en las dos rutas nuevas;
  - ninguna llama a `onBack` dos veces después de borrar (`PlatformSourcesTest`, `deleting an entry goes back once`);
  - el id va codificado en la ruta (LIBRE-1, `AppNavHost.kt:287`).
- **Rendimiento:**
  - `withVersion` compara con una copia normalizada, que es O(campos);
  - `fittedHistory` mide el JSON del historial de una entrada, como mucho 64 KiB;
  - `oldPasswords` es O(versiones) por entrada; «Salud» lo llama una vez por acceso.
  - La medición en el tope de 24 MiB sigue en M-6 (tarea 4 y revisión final).
- **Accesibilidad (R-1):**
  - los 5 s de «Deshacer» son un tiempo para actuar; la alternativa sin tiempo es la papelera, con «Restaurar» durante 30 días, y la confirmación lo dice;
  - los botones son texto, sin depender solo del color;
  - el «🗑» va acompañado del texto «Papelera».

## Textos para el Abogado Digital (revisión final)

Todos en `app/src/main/java/app/seguridad/vault/TrashTexts.kt`, salvo los indicados.

- `TRASH_SHORT`: «Lo que borras se queda aquí 30 días. Al abrir la bóveda, lo que lleve más de 30 días se quita solo.» (O-2, a la vista).
- `COPIES_KEEP_IT`: «Las copias ya hechas (automáticas o exportadas) lo conservan.» (O-2, a la vista en la papelera y en las tres confirmaciones).
- `TRASH_MORE` (bajo «Más información»): «Lo de la papelera no sale en la lista, en la búsqueda, en «Salud» ni al buscar duplicados al importar, pero va en las copias. Si la hora del móvil salta más de 2 días hacia delante, ese día no se quita nada: se hace en la siguiente apertura.»
- `TRASH_EMPTY_LIST`: «La papelera está vacía.»
- Botones: «Restaurar», «Borrar para siempre», «Vaciar papelera»; el menú dice «🗑 Papelera (N)».
- `TRASH_RESTORE_NONE`: «No se ha restaurado: la bóveda ya tiene otra identidad.»
- `TRASH_SAVE_FAILED`: «No se pudo guardar. ¿Queda espacio en el móvil?»
- «Borrar para siempre»:
  - título `trashDeleteForeverTitle`: «¿Borrar «X» para siempre?»;
  - texto `TRASH_DELETE_FOREVER_TEXT`: «Se quita de la bóveda, con sus adjuntos. Las copias ya hechas (automáticas o exportadas) lo conservan.»
- «Vaciar papelera»:
  - título `TRASH_EMPTY_TITLE`: «¿Vaciar la papelera?»;
  - texto `trashEmptyText`: «Se quita 1 entrada de la bóveda, con sus adjuntos. …» o «Se quitan N entradas de la bóveda, con sus adjuntos. …», seguido de `COPIES_KEEP_IT`.
- `trashLeavesIn`: «Se quita en N días», «Se quita en 1 día» o «Se quita al abrir la bóveda».
- Mover a la papelera:
  - botón `MOVE_TO_TRASH`: «Mover a la papelera»;
  - títulos: «¿Mover «X» a la papelera?» y `moveToTrashCountTitle`, «¿Mover N entradas a la papelera?» (este segundo, para la tarea 6);
  - texto `MOVE_TO_TRASH_TEXT`: «Puedes restaurarla desde la papelera durante 30 días.»
- La barra: `movedToTrash`, «Movida a la papelera» o «N entradas movidas a la papelera», con la acción «Deshacer».
- Historial:
  - `HISTORY_TITLE`: «Versiones»; `historyOpen`: «Versiones (N)»;
  - `HISTORY_SHORT`: «Cada guardado que cambia algo deja aquí la versión anterior.»;
  - `HISTORY_MORE`: «Se guardan hasta 20 versiones y 64 KB por entrada; al pasar de ahí se quitan las más antiguas. Los adjuntos no tienen versiones. Las contraseñas y los campos ocultos están ocultos también aquí. Restaurar una versión no borra nada: lo que había queda como otra versión.»;
  - `HISTORY_EMPTY`: «No hay versiones anteriores.»;
  - `HISTORY_RESTORE`: «Restaurar esta versión»; `HISTORY_RESTORED`: «Versión restaurada. Lo que había queda como otra versión.»;
  - `versionUntil`: «Hasta el <fecha>».
- «Borrar historial»:
  - título `historyClearTitle`: «¿Borrar el historial de «X»?»;
  - texto `HISTORY_CLEAR_TEXT`: «Se quitan sus versiones y sus contraseñas anteriores. Las copias ya hechas (automáticas o exportadas) lo conservan.»;
  - `HISTORY_CLEARED`: «Historial borrado».
- En `ui/screens/VaultEntryScreen.kt`: «No se pudo mover a la papelera (¿almacenamiento lleno?)» y «Ver campos» / «Ocultar campos» (en `EntryHistoryScreen.kt`).
- **Para la política de privacidad (tarea 12):**
  - la papelera guarda lo borrado 30 días dentro de la bóveda cifrada y en sus copias;
  - el historial guarda hasta 20 versiones por entrada, con las contraseñas anteriores;
  - la app guarda dentro de la bóveda cifrada la fecha del último desbloqueo (`lastUnlockAt`), para vaciar la papelera;
  - nada sale del móvil.
- **No cambia:** los textos de borrar la identidad (`DELETE_IDENTITY_WARNING` y `DELETE_IDENTITY_CONFIRMATION`), porque la identidad y los contactos se siguen borrando del todo (decisión 2). Siguen siendo ciertos.

## Archivos

Nuevos:
- `app/src/main/java/app/seguridad/vault/Trash.kt`
- `app/src/main/java/app/seguridad/vault/EntryHistory.kt`
- `app/src/main/java/app/seguridad/vault/TrashTexts.kt`
- `app/src/main/java/app/seguridad/ui/screens/TrashScreen.kt`
- `app/src/main/java/app/seguridad/ui/screens/EntryHistoryScreen.kt`
- `app/src/test/java/app/seguridad/vault/TrashTest.kt`
- `app/src/test/java/app/seguridad/vault/EntryHistoryTest.kt`

Cambiados:
- `vault/VaultStore.kt`, `vault/Vault.kt` (`prepare`, KDoc de `passwordHistory`), `vault/History.kt`, `vault/VaultLimits.kt` (`fittedHistory` interna)
- `imports/MergePlan.kt`, `health/Health.kt`
- `AppContainer.kt`, `SeguridadApp.kt`
- `ui/AppNavHost.kt`, `ui/screens/VaultScreen.kt`, `ui/screens/VaultEntryScreen.kt`
- Tests: `HistoryTest`, `MergePlanTest`, `HealthTest`, `RedTeamRecheckTest`, `VaultStoreImportTest`, `AttachmentsTest`, `PlatformSourcesTest`

---

## Ronda 1 de arreglos (revisión `task-3-review.md`)

Commit `968e62d` («2.0, tarea 3: ronda 1 de arreglos de la revisión»), sin push. Rulings que se aplican: «Ruling (T3, I-1)», «(T3, I-2)» y «(T3, menores)».

Corrección al informe de arriba: `TrashTest` tenía 14 tests en `23e3887`, no 15.

### Cambios

**I-1. El vaciado se salta como mucho una vez seguida.**
- Campo nuevo en `VaultData`: `trashPurgeSkipped: Boolean = false`. Va dentro del blob cifrado y autenticado.
- `purgedTrash` (`Trash.kt`) solo salta si hay salto del reloj **y** la apertura anterior no saltó (`skip = jumped && !trashPurgeSkipped`). Apunta el resultado en `trashPurgeSkipped`, así que la apertura siguiente siempre vacía lo de más de 30 días.
- Los textos de O-2 vuelven a ser ciertos: «se quita solo al abrir la bóveda» y «se hace en la siguiente apertura» (`TRASH_MORE`).
- Para el test, `AppContainer` tiene un parámetro nuevo, `vaultNow` (el `now` de los `VaultStore`; por defecto, `System::currentTimeMillis`).

**I-2. `passwordHistory` se vuelve a escribir.**
- Vuelve `withNewPassword` (`History.kt`), igual que en la 1.4.3: un LOGIN cuya contraseña no vacía cambia guarda las 5 últimas.
- Se usa en `upsert` (con un cambio de tipo, la lista se queda como estaba), en `replaceWith` («Reemplazar») y en `restoreVersion`, además de `history`.
- `oldPasswords()` junta las dos listas sin repetir. Se han corregido los KDoc de `passwordHistory`, de `History.kt` y de `EntryHistory.kt`.

**M-1.** `UndoTrash(store, elapsed, scope)` recoge `store.state` y hace `forget()` en cuanto la bóveda no está abierta. Además, `valid()` pone `offer` a 0 si el último envío ya no vale. En `AppContainer`, cada `Slot` le pasa su `scope`.

**M-2.** `restoreVersion` devuelve `false` si la versión o la entrada es personal (IDENTITY o CONTACT). `EntryHistoryScreen` ahora enseña:
- el texto de un `IllegalStateException` (los textos de la tienda, en español: un tope, el paso pendiente, una sola identidad);
- `HISTORY_RESTORE_REFUSED` («Esta versión no se puede restaurar.») cuando `restoreVersion` da `false`;
- «¿Queda espacio?» solo para los demás fallos.

Las `CancellationException` se relanzan en `EntryHistoryScreen` y en `TrashScreen`, porque es una `IllegalStateException`.

**M-3.** `saveRefusedText(state, id)` (`TrashTexts.kt`): da `ENTRY_IN_TRASH` («Está en la papelera: no se ha guardado. Restáurala desde la papelera para editarla.») o `SAVE_LOCKED` (el texto de antes). El editor lo usa cuando `upsert` da `false`. El editor no sale solo: así el aviso se ve, y lo escrito se queda hasta que el usuario vuelve atrás.

**M-4.**
- El botón de la ficha usa `historyOpen(versions, oldPasswords)`: «Versiones (N)» o, si no hay versiones, «Contraseñas anteriores (M)».
- La pantalla de versiones enseña, debajo de «Borrar historial», las contraseñas anteriores (`oldPasswords()`), ocultas hasta pulsar y copiadas como sensibles. Así «Borrar historial» nunca borra algo que no se ve.

**M-5.** `unlockWithKeyLocked` descifra una sola vez. `opened` y `commit` aceptan los datos ya leídos (`read`). La escritura de cada desbloqueo se queda, porque hace falta para `lastUnlockAt`. Su coste en el tope de 24 MiB sigue en M-6 de la tarea 1 (medición en la tarea 4 y en la revisión final).

**M-6.** En `purgedTrash`, una entrada con `deletedAt > now` pasa a `deletedAt = now`: sus 30 días cuentan desde la hora de ahora, nunca más.

**M-7.** `restoreVersion(entryId, savedAt, index = -1)`: con `index`, la versión de esa posición, que además tiene que tener ese `savedAt`. La pantalla pasa el índice.

### Tests que lo cubren

- `TrashTest.spacedUnlocksStillEmptyTheTrash` (I-1).
  - Montaje: un `AppContainer` nuevo para cada apertura, es decir, un proceso nuevo, sin ningún uso visto. Cuatro aperturas, a t0 + 3, + 6, + 31 y + 34 días, todas «salto» según la regla. «viejo» está en la papelera desde 40 días antes de t0 y «nuevo» desde t0.
  - Resultado: la primera salta; la segunda quita «viejo»; la tercera salta; la cuarta quita «nuevo».
  - En la parte pura, 6 aperturas separadas por 3 días dan `[true, false, true, false, true, false]`: nunca dos saltos seguidos.
  - Nota: con la regla del ruling, entre tres aperturas así la que vacía es la **segunda**, no la tercera; el test lo fija tal como la regla lo hace.
- `EntryHistoryTest.oldPasswordSurvivesTrimmedHistory` (I-2):
  - en una entrada con notas de 40 KiB, se cambia A→B y se editan las notas tres veces: la versión con A se recorta y `oldPasswords()` sigue dando A;
  - en una entrada pequeña, un cambio y 21 ediciones de las notas: quedan 20 versiones y la contraseña antigua sigue;
  - «Reemplazar» escribe `passwordHistory`.
- `EntryHistoryTest.editAddsVersion`: ahora comprueba también que `passwordHistory` tiene la contraseña antigua. `HistoryTest.listKeepsTheFiveNewest`: el tope de 5 y los casos sin cambio. `MergePlanTest.replaceFollowsTheRule`: `passwordHistory == [vieja]`.
- `EntryHistoryTest.restoreVersionNeverMakesItPersonal` (M-2): unas versiones hostiles, IDENTITY y CONTACT, escritas en el archivo; `restoreVersion` da `false` y la entrada sigue siendo LOGIN.
- `EntryHistoryTest.restoreVersionByIndex` (M-7): dos versiones con el mismo `savedAt`; el índice manda, y un índice con otra fecha da `false`.
- `EntryHistoryTest.historyLabelCountsLegacy` (M-4).
- `TrashTest.undoOfferGoesWithTheLock` (M-1): mover, crear otra bóveda (cambia a ella) y volver; `offer.value == 0`.
- `TrashTest.saveRefusedSaysWhy` (M-3).
- `TrashTest.purgeAfter30Days` (M-6): la entrada «futuro» queda con `deletedAt == now`, y `trashPurgeSkipped` es falso.

### TDD

- **RED de compilación** (`./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.TrashTest'`):
  ```
  e: …/EntryHistoryTest.kt:298:84 No parameter with name 'index' found.
  e: …/EntryHistoryTest.kt:305:51 No parameter with name 'versions' found.
  e: …/HistoryTest.kt:38:31 Unresolved reference 'withNewPassword' on receiver of type 'VaultEntry'.
  e: …/TrashTest.kt:151:28 Unresolved reference 'trashPurgeSkipped' on receiver of type 'VaultData'.
  e: …/TrashTest.kt:333:101 No parameter with name 'vaultNow' found.
  e: …/TrashTest.kt:388:22 Unresolved reference 'ENTRY_IN_TRASH'.
  e: …/TrashTest.kt:388:38 Unresolved reference 'saveRefusedText'.
  ```
- **GREEN, tests enfocados** (`TrashTest`, `EntryHistoryTest`, `HistoryTest`, `MergePlanTest`, `HealthTest`): 17, 15, 8, 60 y 16 tests respectivamente, todos con `failures="0"`.
- **RED por mutación de los dos Important.** Con `skip = jumped`, sin «una vez como mucho», y sin `withNewPassword` en `upsert`:
  ```
  EntryHistoryTest > editAddsVersion FAILED
  EntryHistoryTest > oldPasswordSurvivesTrimmedHistory FAILED
  TrashTest > spacedUnlocksStillEmptyTheTrash FAILED
  32 tests completed, 3 failed
  ```
  Con el código restaurado, en verde.

### Comandos y resultados (sobre `968e62d`)

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2018** tests, 0 fallos. Lint: 0 errores y 20 avisos, ninguno en los archivos de la tarea.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2011** tests, 0 fallos.

### Textos nuevos para el Abogado Digital (revisión final)

En `vault/TrashTexts.kt`:
- `HISTORY_RESTORE_REFUSED`: «Esta versión no se puede restaurar.»
- `ENTRY_IN_TRASH`: «Está en la papelera: no se ha guardado. Restáurala desde la papelera para editarla.»
- `SAVE_LOCKED`: «La bóveda se bloqueó: no se ha guardado» (el de antes, ahora como constante).
- `historyOpen`: «Versiones (N)» o «Contraseñas anteriores (M)».
- `historyOldPasswords`: «Contraseñas anteriores (M)».

`TRASH_MORE` («ese día no se quita nada: se hace en la siguiente apertura») ahora es cierto siempre.

Para la política (tarea 12): la bóveda apunta, cifrado, si el último desbloqueo dejó la papelera sin vaciar (`trashPurgeSkipped`). Las 5 últimas contraseñas anteriores se siguen guardando como en la 1.4.3, además del historial.

### Revisión experto-seguridad-cripto (ronda 1)

- **`trashPurgeSkipped`** va dentro del blob v4 autenticado: sin la clave de datos nadie puede forzar un salto. La regla sigue fallando del lado conservador, pero ahora tiene un límite: como mucho una apertura sin vaciar. El límite de la duda 3 (un reloj adelantado que se queda adelantado) no empeora (`Trash.kt`, `purgedTrash`).
- **M-2:** una versión de una copia hostil ya no convierte una entrada en identidad o contacto (`VaultStore.kt`, `restoreVersion`, la comprobación `isPersonal`). Decisión (c).
- **M-6:** una fecha de borrado en el futuro ya no alarga lo que se guarda más allá de 30 días del reloj de ahora. Es minimización.
- **I-2:** no hay ningún secreto nuevo. `passwordHistory` ya existía, va cifrado, y «Borrar historial» lo vacía (`withoutHistory`).
- Ningún `Log`, ni red, ni permisos, ni dependencias.

### Revisión Ingeniero Móvil Senior (ronda 1)

- **`UndoTrash`** colecciona en el `scope` del `Slot`, que se cancela al borrar la bóveda. No retiene ninguna pantalla (`Trash.kt`, `init`; `AppContainer.kt`, `Slot.undoTrash`).
- **Corrutinas de las pantallas:** las `CancellationException` se relanzan antes de `catch (IllegalStateException)` y de `catch (Exception)` (`EntryHistoryScreen.kt`, `TrashScreen.kt`).
- **M-5:** un descifrado menos en cada desbloqueo con la huella (`VaultStore.kt`: `unlockWithKeyLocked`, y `commit`/`opened` con `read`).
- **M-4:** las contraseñas anteriores salen plegadas en `Field`, ocultas hasta pulsar, y nada va en el estado guardado.
