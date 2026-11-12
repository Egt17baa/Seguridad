# Re-revisión 1 de la tarea 3 (23e3887..968e62d)

Leídos: CLAUDE.md, experto-seguridad-cripto/SKILL.md, la revisión original, los rulings «T3» de progress.md, el informe de arreglos y el diff. Ejecutados: `TrashTest`, `EntryHistoryTest`, `HistoryTest`, `MergePlanTest` (verdes).

## Por hallazgo

- **I-1: Fixed.** `Trash.kt` `purgedTrash`: `skip = jumped && !trashPurgeSkipped`; el campo `trashPurgeSkipped` (`VaultData.kt:26`) va en el blob cifrado y se persiste (el test lo cruza por procesos nuevos leyendo el archivo). Test que fallaría sin el arreglo: `TrashTest.spacedUnlocksStillEmptyTheTrash` (cuatro procesos nuevos, +3/+6/+31/+34 días: salta, vacía, salta, vacía; y la parte pura `[true,false,true,false,true,false]`). Que con aperturas cada 3 días vacíe en la 2.ª y no en la 3.ª es exactamente «como mucho una vez seguida» del ruling: conforme. Seguridad: el filtro sigue siendo `now - deletedAt <= TRASH_MS`, así que nunca quita nada con menos de 30 días según el reloj actual. Con un adelanto permanente, la 2.ª apertura vacía: límite ya aceptado en el ruling T3 duda 3; el salto protege de un error puntual, no de quien tenga la maestra. Un adelanto de una sola apertura (corregido antes de la siguiente) no vacía nada.
- **I-2: Fixed.** `History.kt` `withNewPassword` (solo LOGIN, no vacía y distinta, `take(5)`); usado en `upsert` (`VaultStore.kt` ~784-793, solo LOGIN->LOGIN, así un cambio de tipo no archiva una frase), en `restoreVersion` y en `replaceWith` (`MergePlan.kt:379`). `oldPasswords()` ya une sin repetir y `withoutHistory` vacía ambas. Tests: `EntryHistoryTest.oldPasswordSurvivesTrimmedHistory` (notas de 40 KiB y 21 ediciones, más Reemplazar), `HistoryTest.listKeepsTheFiveNewest`, `editAddsVersion`, `MergePlanTest.replaceFollowsTheRule`. Fallarían sin el arreglo.
- **M-1: Fixed.** `Trash.kt`: `UndoTrash` recoge `store.state` y hace `forget()` si no está `Unlocked`, y `valid()` olvida lo caducado; `AppContainer.kt:430` pasa el `scope`. Test `undoOfferGoesWithTheLock`.
- **M-2: Fixed.** `VaultStore.restoreVersion`: `if (version.type.isPersonal || current.type.isPersonal) return null`; además, `passwordHistory` solo se toca LOGIN->LOGIN. La pantalla da `HISTORY_RESTORE_REFUSED` en `false` y muestra el mensaje del `IllegalStateException` (límite, UPGRADE_PENDING) en vez de «espacio». Test `restoreVersionNeverMakesItPersonal` (IDENTITY y CONTACT hostiles escritos en el archivo).
- **M-3: Fixed (aceptable).** `saveRefusedText` (`TrashTexts.kt`) da `ENTRY_IN_TRASH` en `VaultEntryScreen.kt:~302`. No cierra el editor: es aceptable, porque el texto es veraz, lleva a restaurar desde la papelera y no se pierde lo que el usuario estaba editando. Test `saveRefusedSaysWhy`.
- **M-4: Fixed.** `historyOpen(versions, oldPasswords)` y las contraseñas anteriores a la vista (ocultas, `Field(secret = true)`) en `EntryHistoryScreen.kt`. Test `historyLabelCountsLegacy` (la pantalla no tiene test de UI).
- **M-5: Fixed.** `unlockWithKeyLocked` descifra una vez y pasa `read = data` a `commit`/`opened` (`VaultStore.kt:636-638`). Sin test propio, pero la cobertura de desbloqueo con huella existente sigue verde. No queda la medición de rendimiento prometida (tareas 4 y final); no es de esta ronda.
- **M-6: Fixed.** `purgedTrash`: `deletedAt > now` pasa a `now`; también cuando salta. `purgeAfter30Days` lo comprueba. La comparación `same` por identidad evita reescribir sin cambios.
- **M-7: Fixed.** `restoreVersion(..., index)` exige que la posición tenga ese `savedAt`; la pantalla pasa `i`. Test `restoreVersionByIndex`.

## Nuevos problemas

- **N-1 (Minor).** `EntryHistoryScreen.kt` (catch de `IllegalStateException`): muestra `e.message` de cualquier `IllegalStateException`. Hoy los de `restoreVersion` son textos españoles fijos (`UPGRADE_PENDING`, `ONLY_ONE_IDENTITY`, `NEEDS_MASTER_FOR_CONTACTS`, `VaultLimitExceeded`), pero un `check` futuro o ajeno con texto en inglés o con datos saldría a la pantalla. Arreglo: aceptar solo `VaultLimitExceeded` o los textos conocidos y, para el resto, `TRASH_SAVE_FAILED`. Mismo patrón que `ContactsView.kt:450`. Riesgo bajo.
- Nada Critical ni Important nuevo. `trashPurgeSkipped` con valor por defecto: los archivos sin el campo se leen bien.

Verdict: needs fixes (solo N-1, Minor; CLAUDE.md manda arreglar todos los menores).

## Revisión experto-seguridad-cripto

- Sin primitivas, red, logs ni permisos nuevos. `trashPurgeSkipped` y `lastUnlockAt` viven dentro del blob autenticado: no se pueden falsificar sin la clave de datos.
- Retención: el vaciado nunca quita lo de menos de 30 días según el reloj actual (`Trash.kt` filtro `now - deletedAt <= TRASH_MS`); el reloj adelantado puede adelantar el vaciado solo si persiste en dos aperturas seguidas (límite aceptado, ruling T3 duda 3). Los textos O-2 vuelven a ser verdaderos para quien abre cada pocos días.
- Dato hostil: `restoreVersion` rechaza tipos personales (`VaultStore.kt` restoreVersion), `upsert` y `restore` conservan sus invariantes. Las contraseñas antiguas siguen ocultas hasta pedirlas (`EntryHistoryScreen.kt`, `Field(secret = true)`) y no están en `rememberSaveable`.
- `passwordHistory` guarda secretos en claro dentro del blob cifrado, igual que en 1.4.3; sin cambio de modelo de amenaza.

## Revisión Ingeniero Móvil Senior

- Concurrencia: todo sigue en `modifyData` (mutex, una lectura); M-2 y M-7 deciden dentro de la misma sección crítica. `UndoTrash` recoge `store.state` en el `scope` de la bóveda (`AppContainer.kt:430`), sin fugas: se cancela con el scope. `collect` sobre un `StateFlow` no pierde el último estado.
- `catch (CancellationException) { throw }` añadido en `TrashScreen.kt`, `EntryHistoryScreen.kt` (corrutinas bien canceladas).
- `unlockWithKeyLocked` ahora descifra una sola vez (`VaultStore.kt:636`), menos coste en el desbloqueo con huella.
- `AppContainer` acepta `vaultNow` para los tests (valor por defecto `System::currentTimeMillis`): sin cambio de comportamiento.
- Tests: los cuatro de arriba pasan en JVM; las pantallas (Compose) no tienen test de UI, igual que antes.
