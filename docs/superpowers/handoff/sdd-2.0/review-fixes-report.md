# Clavero 2.0, ronda única de arreglos de las revisiones (CR-1 a CR-7, I-1 a I-3): informe del implementador

Encargo: `docs/superpowers/handoff/sdd-2.0/advisor-t12-review-fixes.md`, «Encargo (para pegar)». Base: `bdd9872`. Sin *push* ni `--amend`.

## Estado
| Punto | Estado | Commit |
|---|---|---|
| 1. CR-1 | Hecho | 77e6a51 |
| 2. CR-7 | Hecho | 9b6bbc4 |
| 3. CR-2 | Hecho | c4e8f0f |
| 4. CR-3 | Hecho | f8e1845 |
| 5. CR-4 | Hecho | 7879cea |
| 6. CR-5 + I-1 | Hecho | 6a568f3 |
| 7. CR-6 | Hecho | 5e62ac2 |
| 8. I-2 + I-3 | Hecho | d5c167e |
| 9. Cierre | Hecho | (commit de documentos) |

## 1. CR-1: la fecha del último cambio de contraseña
- `vault/EntryHistory.kt`:
  - `frozenPasswordMark(prev)` = `prev.passwordChangedAt ?: (si prev.passwordForHealth() no está vacía: lastPasswordChange(prev); si no, null)`;
  - `withVersion` la usa cuando la contraseña no cambia;
  - `withoutHistory` fija la marca antes de vaciar;
  - `comparable()` pone `passwordChangedAt = null`, así que la marca nunca crea una versión.
- `vault/VaultStore.kt`:
  - `addAttachment` y `removeAttachment` congelan la marca;
  - `duplicate` usa `frozenPasswordMark(source)` en lugar de su fórmula propia, con el mismo resultado.
- `vault/Vault.kt`: el KDoc de `passwordChangedAt`, al día.
- **`grep` de `updatedAt =` y de `copy(history` en `vault/` e `imports/`, sitio a sitio:**

  | Sitio | Qué hace | ¿Se aplica? |
  |---|---|---|
  | `VaultStore.kt:949` (`duplicate`) | Entrada nueva con la fecha de la fuente | Sí: `frozenPasswordMark(source)` |
  | `VaultStore.kt:1270` (restaurar una versión) | Termina en `restored.withVersion(current, now)` | Ya cubierto por `withVersion`: `now` si la contraseña cambia, congelada si no |
  | `VaultStore.kt:1333`, `:1346` (adjuntos) | Suben `updatedAt` sin `withVersion` | Sí |
  | `VaultLimits.kt:98` | Solo mide | No aplica |
  | `VaultLimits.kt:135`, `:167` (`fittedHistory`, `fitted`) | Recortan versiones por los topes | No: la lista de la 1.4.3 (`passwordHistory`) no se recorta, y una edición ya congela la marca en `withVersion` antes de ajustar |
  | `VaultLimits.kt:527` | Las versiones sin historia ni adjuntos | No aplica |
  | `Selection.kt:102` (`changing`: «Favorita», «Etiquetar») | Termina en `withVersion` | Cubierto |
  | `ContactsView.kt:423`, `:433`; `Contacts.kt:68`, `:86`, `:106` | La identidad y los contactos, sin contraseña | No aplica |
  | `TotpImport.kt:28` | Entradas 2FA nuevas, sin contraseña | No aplica |
  | `Vault.kt:361-362` (`upgraded`) | La subida de una v2 o v3: las contraseñas antiguas pasan a ser versiones | No: no edita; la fecha sigue saliendo de la historia o de la lista |
  | `MergePlan.kt:171` | Marcador de tamaño | No aplica |
  | `MergePlan.kt:299` | Una entrada importada nueva | No aplica: es nueva, y su fecha es la de la importación |
  | `MergePlan.kt:377`, `:382` («Reemplazar») | Termina en `withVersion(existing, now)` | Cubierto |
  | `ImportModel.kt:96` | Lo leído de otra app | No aplica |
  | `EntryHistory.kt:18`, `:25`, `:50`, `:91` | Las funciones de la historia | Son el arreglo |

- **Tests** (`vault/PasswordMarkTest`, nuevo):
  - `editsThatKeepThePasswordKeepItsDate`: una entrada anterior a la 2.0 con «Favorita», «Etiquetar», un adjunto añadido y otro quitado conserva `lastPasswordChange`; «Salud» sigue diciendo `OLD`; `Expiry` da la misma fecha;
  - `clearingTheHistoryKeepsTheDateOfTheList`: con `passwordHistory` de la 1.4.3 y una edición de las notas, «Borrar historial» conserva la fecha de la lista;
  - `aSaveWithoutChangesAddsNoVersion`, también para la marca sola;
  - `anEntryWithoutPasswordStaysWithoutAMarkAndANewPasswordIsNow`;
  - `duplicateKeepsTheDateAsBefore`;
  - `aRealVaultOf143KeepsItsDate`: `vault-1.4.3.json` se abre, sube a v4 y se marca su acceso «Favorita». La fecha no cambia (1799999998000) y las demás entradas, que nadie tocó, siguen sin marca.
- **Rojo:** `3 failed`.
  - `editsThatKeep…`: `the date of the last change of its password expected:<1700000000000> but was:<1763072004000>`;
  - `aSaveWithoutChanges…`: `AssertionError`, porque la marca sola creaba un cambio;
  - `clearingTheHistory…`: `expected:<1700000001000> but was:<1700000000000>`.
- **Verde:** jvm-check `PasswordMarkTest`, las clases de la historia, `SelectionTest`, `ExpiryTest`, `health.*`, `VaultStoreTest`, `VaultV4Test`, `VaultV3Test`, `imports.*` y `redteam.*`: 707 tests y 0 fallos. Ningún test de topes ni con datos de la 1.x falla, así que no salta ninguna parada.

## 2. CR-7: borrar una bóveda bloqueada sin abrirla
- `vault/VaultStore.kt`: `checkMasterToDelete(master): String?`. Solo lee:
  - va bajo el `mutex`, con `NonCancellable`, y con `derive {}` (`KdfLock`);
  - comprueba `active()` antes y después de Argon2id;
  - decodifica en `compute`, y un `OutOfMemoryError` da `CryptoFailure(VAULT_NO_MEMORY)`;
  - pone la clave a cero en un `finally`;
  - devuelve el nombre de los datos;
  - no toca `_state`, el reloj, la papelera ni la subida, y no vuelve a envolver la clave.
- `AppContainer.kt` (`deleteVault`): toda bóveda bloqueada, sea v2, v3 o v4, pasa por `checkMasterToDelete`. Con la bóveda abierta, sigue `confirmMaster`. El nombre se compara con `displayName(VaultData(name = name), order)`, igual que antes.
- No cambia la estructura de `KdfLock` ni la del `mutex`.
- **Tests** (`AppContainerVaultsTest`, nuevos): `deletingALockedV3VaultNeverOpensItNorCopiesIt` (con el `vault-1.4.3.json` real) y `deletingALockedV4VaultNeverOpensItNorCopiesIt`. En los dos casos hay una carpeta que apunta lo que se crea y A6 pendiente, y se comprueba que:
  - con la maestra mal, sale `CryptoFailure`;
  - con el nombre mal, `WRONG_NAME`;
  - `vault.bin` no cambia;
  - con la maestra y el nombre bien, `DELETED`;
  - la carpeta no recibe ningún documento;
  - el almacén nunca llega a `Unlocked`.
- **Ajuste por comportamiento, en un test fuera de `vault/`, `imports/` y `redteam/`:** `deleteNeedsMasterAndName` afirmaba que, tras `WRONG_NAME` con la bóveda bloqueada, esta quedaba abierta, que era justo el defecto de CR-7. Ahora afirma que sigue bloqueada.
- **Rojo:** `2 failed`.
  - La v3: `AssertionError` en `AppContainerVaultsTest.kt:585`: el archivo se reescribía, porque el desbloqueo hacía la subida y la copia.
  - La v4: `the file is not written before it goes` (`:597`).
- **Verde:** jvm-check `AppContainer*`, `redteam.*` y `VaultStoreTest`: 263 tests y 0 fallos. Con la maestra mal no se borra nada, así que no salta ninguna parada.

## 3. CR-2: primero la copia de la subida, y un texto verdadero mientras dura
- `vault/PendingUpgrade.kt`: `copying: StateFlow<Boolean>`.
  - Pasa a verdadero con `openedWith(next != null)`, antes de publicar `Unlocked` (`VaultStore.kt`, `opened`).
  - Pasa a falso con `saved()`, con `drop()` (que es `openedWith(null)`) y en el `finally` de `afterUnlock`, si la apertura sigue siendo la misma. Así es falso en cuanto sale el aviso (`asking`), y también si la copia falla o el guardado falla.
- `vault/VaultStore.kt`:
  - `upgradeCopying` lo expone.
  - Un solo ayudante, `upgradeRefusal()`: con `asking`, `UPGRADE_PENDING`; si no, `UPGRADE_COPYING`. Lo usan `modifyData`, `changeMaster` y `checkCanReplace`, que es la comprobación de los dos `importReplace`.
  - Texto nuevo `UPGRADE_COPYING`, palabra por palabra el del encargo, en la lista de `storeSaveErrorText`.
- `vault/AutoBackup.kt`: `runDue` (el colector de `Unlocked` y `launchDue`) y `runChanged` esperan `store.upgradeCopying.first { !it }` antes de `lock.withLock`. Se aplaza, no se salta. `copyBeforeUpgrade` no espera.
- **Tests** (`vault/UpgradeCopyFirstTest.kt`, nuevo, 7 tests, con una regla de 30 s para no colgar el cerrojo de Gradle). Usan el `vault-1.4.3.json` real, una carpeta falsa lenta y A6 pendiente:
  - `theUpgradeCopyGoesFirstAndTheDueCopiesWaitForIt`: el primer documento es `seguridad-antes-de-2-0-…`. Durante la copia, `upsert` da `UPGRADE_COPYING` y `launchDue` espera. Después de guardar la v4, corren la copia debida y A6: se borra la copia vieja, y la de la subida sigue en la carpeta, apuntada en `importCopies`.
  - `theDueCopiesWaitEvenWhenTheyCouldTakeTheLockFirst`: la carrera de la revisión. El colector y `launchDue` tienen su turno antes de que la copia de la subida llegue al `lock`, y no escriben nada.
  - `whenTheDialogAsksASaveSaysUpgradePending`: con el aviso, `UPGRADE_PENDING`.
  - La espera siempre termina, cada caso con `withTimeout`: `theWaitEndsWithALock`, `theWaitEndsWithACopyThatFails`, `theWaitEndsWithASaveThatFails` y `theWaitEndsWithTheScopeCancelled`.
- `ErrorTextsTest.theTextOfASaveWhileTheUpgradeCopyRuns`: el texto palabra por palabra, `storeSaveErrorText` y `ForbiddenWords`.
- **Cambio autorizado:** en `VaultV4Test.kt:306` (dentro de `beforeUpgrade`), `UPGRADE_PENDING` pasa a `UPGRADE_COPYING`.
- Inventario: fila nueva en `docs/superpowers/handoff/sdd-2.0/task-10c-inventory.md`.
- **Rojo**, dos veces, cada una con un trozo del arreglo quitado:
  - Sin `upgradeRefusal` (siempre `UPGRADE_PENDING`): `theUpgradeCopyGoesFirstAndTheDueCopiesWaitForIt`, `ComparisonFailure: expected:<[Clavero está haciendo la copia automática …]> but was:<[Antes de guardar nada, elige en el aviso …]>`.
  - Sin la espera en `runDue` y `runChanged`: `theDueCopiesWaitEvenWhenTheyCouldTakeTheLockFirst`, `AssertionError: nothing before the upgrade's copy: [seguridad-auto-20270115-090000.sgbak.tmp] expected:<0> but was:<1>`. La copia automática de la v3 entraba antes que la de la subida.
  - Nota: en el primer intento, una comprobación que fallaba con la puerta de la carpeta cerrada dejaba el desbloqueo (que no se cancela) esperando, y el test acababa por la regla de 30 s. Ahora las comprobaciones de mientras dura van en un `try` cuyo `finally` abre la puerta.
- **Verde:** jvm-check `UpgradeCopyFirstTest`, `AutoBackup*`, `VaultV4Test`, `VaultV3Test`, `VaultStoreTest`, `ErrorTextsTest`, `EntryHistoryTest` y `redteam.*`: 25 clases, 445 tests y 0 fallos.
- **Paradas:** A6 no borra la copia `seguridad-antes-de-2-0-` (se comprueba), y la espera termina en los cuatro casos. No salta ninguna.

## 4. CR-3: «Contactos» con la subida pendiente
- `vault/ContactsView.kt`: función pura `contactsGate(needsMaster, asking, copying)`, que da `ContactsGate`:
  - sin `needsMaster`, `Content`;
  - con `asking`, `Notice(UPGRADE_PENDING)`;
  - con `copying`, `Notice(UPGRADE_COPYING)`;
  - si no, `Master`.
- `ui/screens/ContactsScreen.kt` (`ContactsFrame`): lee `pendingUpgrade` y `upgradeCopying`. Con la subida pendiente enseña el texto, sin campo de contraseña. `MasterPrompt` queda solo para `needsMaster` sin subida pendiente. El botón flotante sigue igual (`!needsMaster`).
- KDoc puesto al día: `ContactsFrame`, `MasterPrompt` y `VaultStore.needsMasterForContacts`.
- **Test:** `ContactsViewTest.theContactsGateWithTheUpgradeWaiting`, con las ocho combinaciones.
- **Rojo:** no compila: `Unresolved reference 'ContactsGate'` y `'contactsGate'` (`ContactsViewTest.kt:608`).
- **Verde:**
  - jvm-check `ContactsViewTest` y `ContactsTest`: 76 tests y 0 fallos;
  - app `ContactsViewTest`: 46 tests y 0 fallos, y así compila `ContactsScreen.kt`.

## 5. CR-4: borrar una carpeta con una subcarpeta del mismo nombre
- `vault/Folders.kt` (`withoutFolder`, con `PARENT`): antes de cambiar nada, cada subcarpeta que sube se compara con las carpetas del destino. Se usa `organizeKey`, y una carpeta cuyo padre no está cuenta como de arriba del todo, la misma regla de `checkFreeName`. Si choca, da `OrganizeRefused(FOLDER_DELETE_NAME_TAKEN)`.
- Texto nuevo `FOLDER_DELETE_NAME_TAKEN`, palabra por palabra el del encargo. `VaultOrganize.kt` ya lo enseña con `storeSaveErrorText`. Tiene su fila en el inventario.
- **Tests** (`FoldersTest`):
  - `deleteToParentRefusedWhenASubfolderNameIsTaken`: arriba del todo («Trabajo» > «Casa» junto a «casa») y dentro de otra carpeta («Viejo» > «Proyectos» > «Fotos» junto a «FOTOS»). Un rechazo no cambia nada, ni en memoria ni en el archivo. `TRASH` no cambia. Sin choque, se borra.
  - `deleteToParentComparesWithOrganizeKey`: «trabajo » frente a «Trabajo», un padre que falta, `TRASH`, el texto palabra por palabra y `ForbiddenWords`.
- **Rojo**, con la constante ya puesta y sin la regla: `2 failed`, los dos `expected app.seguridad.vault.OrganizeRefused to be thrown, but nothing was thrown`.
- **Verde:** jvm-check `FoldersTest`, `*Organiz*`, `ErrorTextsTest` y `redteam.*`: 18 clases, 176 tests y 0 fallos.

## 6. CR-5 e I-1: la vista previa de «Importar de otra app»
- `vault/VaultStore.kt` (`importSnapshot`, `:1017`, y `VaultSnapshot`): el *snapshot* lleva `rest`, un `VaultData` con la papelera como `entries` y las carpetas, las vistas, los colores y el nombre. Se toma en la misma sección del `mutex` que las entradas vivas y la revisión.
- `imports/MergePlan.kt`: `estimatedOverBytes(data: VaultData)` (`:421`) mide con los mismos dos topes que `applyImport`:
  - el de la copia, con el archivo estimado como `estimatedVaultFileBytes`;
  - `VAULT_MAX_DATA_BYTES`, con `jsonBytes(data.fitted())`, que es la medida del guardado.

  Las dos medidas añaden `PREVIEW_UNSEEN_BYTES` (1 KiB, `:438`), por lo que el *snapshot* deja fuera: la marca de la bóveda, dos horas, una bandera y la versión de las plantillas. Igual que antes, un texto que ya pasa el tope por su recuento no se codifica. `previewDataBytes` y `previewFileBytes` son lo que cuenta, en `internal`, para el test.
- `imports/ImportSession.kt`:
  - `previewEstimate(…, existing, rest, now)` mide `rest.copy(entries = fusionadas + papelera)`;
  - la sesión guarda `rest` junto a `existing` (`onPlan`, `:252`) y lo borra donde borra `existing` (`dropAll`, `:462-463`).
- `ui/screens/ImportScreen.kt` pasa `snap.rest` a `onPlan` y `session.rest` a `previewEstimateOrNoMemory`.
- **I-1:**
  - `estimated` pone `estimateNoMemory = false`;
  - `canConfirm` añade `&& !estimateNoMemory`.
- **Cambios autorizados (solo el argumento nuevo, con un resto vacío):**
  - `ImportStateTest.kt:358-367` y `:711-717`;
  - `RedTeam20Recheck2Test.kt:541`;
  - `RedTeam20Recheck3Test.kt:223`;
  - `VaultStoreImportTest.kt:238`.
- **Ajuste mecánico por la firma** (apuntado, como dice el encargo): el ancla de R16 en `RedTeam20Recheck3Test.kt:244` busca el texto de la llamada, que ahora termina en `snap.entries, snap.rest)`.
- **I-1, test:** `informativeAnEstimateAfterNoMemoryForTheSameChoicesTurnsImportOn` pasa a `resistsAnEstimateAfterNoMemoryForTheSameChoices`. Afirma lo seguro:
  - con `estimateNoMemory`, «Importar» está desactivado;
  - una estimación posterior de las mismas opciones quita la bandera;
  - `canConfirm` lee la bandera.

  Se mantienen las comprobaciones del código de la pantalla.
- **Tests de CR-5** (`VaultStoreImportTest`, nuevos):
  - `thePreviewMeasuresTheTrashAndTheFoldersToo`: las entradas vivas caben. Con la papelera, la vista previa da `overBytes` de 10 000 o más; con la papelera justa y 500 carpetas, más de 0 (antes daba 0).
  - `thePreviewNeverCountsLessThanTheSave`: el diferencial de **200 bóvedas aleatorias** (`Random(20261120)`), con entradas vivas, papelera, carpetas, vistas, colores, nombre, una importación con duplicados y una opción aleatoria. Pasa por el `previewEstimate` y el `applyImport` reales, y comprueba:
    - si `applyImport` da `TooBig`, la vista previa da más de 0;
    - si la vista previa da 0, se importa;
    - después de cada guardado, el JSON que tiene el almacén y el archivo en disco no pasan de lo que contó la vista previa.
  - `thePreviewAndTheSaveAgreeAtTheRealCaps` y `…NearTheEdge`: cuatro casos con los topes reales. Con un hueco de 100 000 bytes, una nota de 30 000 se guarda y tres de 50 000 dan `TooBig`; con un hueco de 20 000, una nota de 17 000 se guarda y una de 21 000 da `TooBig`. Cifras: vista previa 0 y `Done`; 51 540 y `TooBig(50 616)`; 0 y `Done`; 2 196 y `TooBig(1 272)`.
- **Desviación del encargo, decidida y apuntada para el director:**
  - Las 200 bóvedas del diferencial son pequeñas, de unos KB, y no rozan los 24 MiB. Medido: cada caso a tamaño real pasa por 24 MiB unas 25 veces (escribir el archivo, el desbloqueo con su guardado de servicio, la vista previa y el guardado). Así, 50 casos no acababan en 60 s, más de 1,2 s por caso.
  - Lo que garantiza CR-5 no depende del tope: la vista previa nunca cuenta menos que el guardado. Por eso los 200 casos comprueban esa desigualdad a tamaño pequeño, en 1,8 s, y los cuatro casos de los topes reales comprueban el veredicto, en unos 3,5 s cada test.
- **Conocido y sin cambiar:** `applyImport` tiene un tercer `TooBig`, el de los adjuntos (el número de archivos y los 100 MiB en `accept`), que la vista previa no modela. El encargo acota CR-5 a los dos topes, así que los generadores no llevan adjuntos, y «TooBig ⇒ vista previa > 0» vale para esos dos topes.
- **Rojos**, cada uno con una parte del arreglo quitada:
  - `previewEstimate` midiendo solo `r.entries`, el código de antes: `3 failed`.
    - `thePreviewMeasuresTheTrashAndTheFoldersToo`: `AssertionError`.
    - `…RealCaps`: `room 100000, [50000, 50000, 50000]: TooBig(overBytes=50616, overFiles=0), preview 0`.
    - `…NearTheEdge`: `room 20000, [21000]: TooBig(overBytes=1272, overFiles=0), preview 0`.
  - `PREVIEW_UNSEEN_BYTES = 0`: `thePreviewNeverCountsLessThanTheSave`, `case 0: the store holds 15489 JSON bytes, the preview counted 15389`.
  - Sin las dos líneas de I-1: `resistsAnEstimateAfterNoMemoryForTheSameChoices`, `the estimate that arrived is the one shown`.
- **Verde:**
  - jvm-check `imports.*`, `VaultStoreImportTest`, `AutoBackupImportCopyTest` y `redteam.*`: 34 clases, 532 tests y 0 fallos. Por encima de 5 s solo hay dos tests que ya existían: `dTheCompactEncodingStaysUnderItsGuard`, con 8,8 s, y `resistsAV4CopyWithoutATagAtTheLimit…`, con 10,8 s, que no son de esta ronda.
  - app `PlatformSourcesTest`, 47 tests, e `ImportStateTest`, 49; así compila `ImportScreen.kt`.

## 7. CR-6: los errores de guardado en pantalla
- `vault/VaultStore.kt`: `storeSaveErrorText(e, fallback = TRASH_SAVE_FAILED)`, con el parámetro nuevo y su valor por defecto. Solo pasan los textos fijos del almacén; lo demás da `fallback`, nunca lo que dijo la excepción.
- **Arreglados** (antes escondían `UPGRADE_*`, `VaultLimitExceeded` u `OrganizeRefused` detrás de un texto fijo):
  - `EntryHistoryScreen.kt:127` («Borrar historial»): `storeSaveErrorText(e)`;
  - `VaultPickerScreen.kt:345-353` («Renombrar»): `storeSaveErrorText(e, fallback = VAULT_FAILED)`, dentro del bloque; antes acababa en `VAULT_FAILED` en `AppContainer.runMarked`;
  - `TrashScreen.kt:89` (restaurar, borrar y vaciar): `storeSaveErrorText(e)`;
  - `CodesScreen.kt:187-198` (guardar la nota de «PIN y códigos»): `storeSaveErrorText(e, fallback = CODES_SAVE_FAILED)`, sin registrar nada;
  - `VaultAttachments.kt:76` y `:174` (añadir y quitar un adjunto), con su texto de antes como `fallback`;
  - `VaultEntryScreen.kt:214`, `:244` y `:373` (a la papelera desde la ficha): `failed` pasa a ser el texto, con el de antes como `fallback`;
  - `VaultEntryScreen.kt:485` (los lotes de un QR de migración): `storeSaveErrorText(x, fallback = "No se pudo importar")`;
  - `VaultBackup.kt:167` («Cambiar la contraseña maestra»): `storeSaveErrorText(e, fallback = newPasswordFailureMessage(…))`;
  - `vault/ContactsView.kt` (`saveFailureMessage`, de identidad y contactos): `storeSaveErrorText(e, fallback = …)`, con los dos textos de antes como `fallback`.
- **El `grep`** que pide el encargo es ahora un test: `ErrorTextsTest.theScreensSayTheStoreTextsOfAFailedSave` recorre todo `catch (<nombre>: Exception)` de `ui/` cuyo `try` llama a un guardado del almacén. Encuentra 19, todos con `storeSaveErrorText`:

| Sitio | Guardado |
|---|---|
| `CodesScreen.kt:192` | `store.upsert` (arreglado) |
| `EntryHistoryScreen.kt:102` | `store.restoreVersion` (ya estaba) |
| `EntryHistoryScreen.kt:127` | `store.clearHistory` (arreglado) |
| `SettingsScreen.kt:780` | `vault.clearAllHistory` (ya estaba) |
| `VaultAttachments.kt:75` | `store.addAttachment` (arreglado) |
| `VaultAttachments.kt:173` | `store.removeAttachment` (arreglado) |
| `VaultEntryScreen.kt:329` | `store.duplicate` (ya estaba) |
| `VaultEntryScreen.kt:349` | `store.moveEntries` (ya estaba) |
| `VaultEntryScreen.kt:372` | `undo.moveToTrash` (arreglado) |
| `VaultEntryScreen.kt:448` | `store.upsert` (ya estaba) |
| `VaultEntryScreen.kt:484` | `store.addAll` (arreglado) |
| `VaultList.kt:451` | `store.moveEntries` (ya estaba) |
| `VaultList.kt:504` | `store.duplicate` (ya estaba) |
| `VaultList.kt:706` | `undo.moveToTrash` (ya estaba) |
| `VaultList.kt:727` | `undo.moveToTrash` (ya estaba) |
| `VaultOrganize.kt:378` | `store.deleteFolder` (ya estaba) |
| `VaultOrganize.kt:411` | `store.deleteView` (ya estaba) |
| `VaultOrganize.kt:440` | `store.setTagColor` (ya estaba) |
| `VaultPickerScreen.kt:351` | `active.rename` (arreglado) |

  El mismo test fija, aparte, `TrashScreen` (sus guardados llegan como un bloque) y «Renombrar».
- **Se quedan como estaban, por su motivo:**
  - `VaultOrganize.kt:280` y `:305` ya usaban `storeSaveErrorText` (sus guardados van en lambdas que el test no ve);
  - `VaultScreen.kt:206` («Deshacer» de la papelera): solo puede fallar el disco, porque la subida pendiente ya habría rechazado el paso a la papelera;
  - `VaultBackup.kt:197` («Importar copia»): `CryptoFailure(UPGRADE_*)` ya pasa tal cual con `copyFailureText`;
  - `imports/ImportFlow.kt` (fuera de `ui/`): `SaveFailed` nunca dice lo que dijo la excepción (regla de la 1.4.0), y su llamador ya se rechaza con la subida pendiente;
  - `SettingsScreen.kt:348`, `:387`, `:467` y `:624`, y `AppearanceScreen.kt:130`: son guardados de los ajustes (`SettingsStore`), no de la bóveda;
  - `VaultScreen.kt:414` y `:477` (crear y abrir), y `VaultPickerScreen.kt:334` (crear una bóveda): no son guardados de una bóveda abierta, y tienen su propio texto de contraseña.
- **Tests:**
  - `ErrorTextsTest.theSaveErrorTextTakesAFallback`;
  - `ErrorTextsTest.theScreensSayTheStoreTextsOfAFailedSave`;
  - `ContactsViewTest.aContactSaveRefusedSaysTheStoreText`.
- **Rojos:**
  - sin el parámetro, no compila: `No parameter with name 'fallback' found` (`ErrorTextsTest.kt:115`);
  - con el parámetro y sin arreglar las pantallas: `catches of a save with a fixed text expected:<[]> but was:<[CodesScreen.kt:190, VaultEntryScreen.kt:372, EntryHistoryScreen.kt:128, VaultAttachments.kt:74, VaultAttachments.kt:172]>`;
  - con el `grep` ampliado a cualquier nombre de variable y a `delete`: `but was:<[VaultEntryScreen.kt:484]>`, y `aContactSaveRefusedSaysTheStoreText`: `expected:<[Antes de guardar nada, elige en el aviso …]> but was:<[No se pudo guardar: hace falta la contraseña maestra o ya existe una identidad]>`.
- **Verde:**
  - jvm-check `ErrorTextsTest`, `ContactsViewTest`, `ContactsTest`, `EntryHistoryTest` y `redteam.*`: 19 clases, 250 tests y 0 fallos;
  - app `ContactsViewTest` y `ErrorTextsTest`: 47 y 9 tests, y así compilan las pantallas tocadas.

## 8. I-2 e I-3
- **I-2:** `RedTeam20Recheck2Test.rt29_`, la línea 251, pasa a `assertEquals(8L * 20, encodings)`, porque el recuento es exacto: `min(k, 20)` por entrada.
- **I-3:** en `rt28_`, la aserción de tiempo (`tTags <= 2 * tChips`) pasa a una fila informativa (`println … informative`), como se hizo con `rt29_`. Lo demás no cambia: el ancla de `VaultList.kt`, `listTags` en `compute` y el diferencial de 3 000 casos siguen fijando RT-28.
- **Rojo:** no lo hay. Son dos cambios solo de test, uno que estrecha una aserción y otro que la quita. El verde enseña la cifra exacta.
- **Verde:** jvm-check `RedTeam20Recheck2Test`, 14 tests, y `RedTeam20ListCostTest`, 4 tests, sin fallos.
  - `RT-29: 8 logins of 262144 B of notes: 160 encodings of a version (20 per entry)`.
  - `RT-28: listTags … 52.77 ms; the chips alone 50.73 ms (x1.04, informative)`.

## 9. Cierre

### Tabla de rojos y verdes
| Punto | Commit | Rojo visto | Verde |
|---|---|---|---|
| CR-1 | `77e6a51` | `PasswordMarkTest`: 3 fallos (la fecha se movía a `updatedAt`, la marca sola creaba una versión, borrar historial perdía la fecha de la lista) | 707 tests, 0 fallos |
| CR-7 | `9b6bbc4` | `AppContainerVaultsTest`: 2 fallos (v3: el archivo se reescribía; v4: `the file is not written before it goes`) | 263 tests, 0 fallos |
| CR-2 | `c4e8f0f` | Sin `upgradeRefusal`: `ComparisonFailure` (`UPGRADE_PENDING` en lugar de `UPGRADE_COPYING`). Sin la espera: `nothing before the upgrade's copy: [seguridad-auto-…sgbak.tmp] expected:<0> but was:<1>` | 445 tests, 0 fallos |
| CR-3 | `f8e1845` | No compila: `Unresolved reference 'ContactsGate'` | jvm-check 76 y app 46, 0 fallos |
| CR-4 | `7879cea` | 2 fallos: `expected …OrganizeRefused to be thrown, but nothing was thrown` | 176 tests, 0 fallos |
| CR-5 + I-1 | `6a568f3` | Midiendo solo las entradas: 3 fallos (`TooBig(overBytes=50616) … preview 0`, `TooBig(overBytes=1272) … preview 0`). `PREVIEW_UNSEEN_BYTES = 0`: `the store holds 15489 JSON bytes, the preview counted 15389`. Sin I-1: `the estimate that arrived is the one shown` | 532 tests, 0 fallos |
| CR-6 | `5e62ac2` | No compila sin `fallback`. Luego 5 sitios con texto fijo (`CodesScreen.kt:190`, `VaultEntryScreen.kt:372`, `EntryHistoryScreen.kt:128`, `VaultAttachments.kt:74` y `:172`). Con el `grep` ampliado: `VaultEntryScreen.kt:484` y `saveFailureMessage` | 250 tests, 0 fallos |
| I-2 + I-3 | `d5c167e` | No hay rojo: solo cambia el test (una aserción más estricta y otra que pasa a informativa) | 18 tests, 0 fallos; 160 codificaciones |

### Los dos `grep`
- **CR-1:** la tabla de la sección 1, `updatedAt =` y `copy(history` en `vault/` e `imports/`.
- **CR-6:** la tabla de la sección 7, con los 19 `catch` de `ui/` alrededor de un guardado. Ahora la fija `ErrorTextsTest.theScreensSayTheStoreTextsOfAFailedSave`.

### Cifras
- **`RedTeam20ListCostTest`:**
  - Antes: B, el suelo, 2 954 912 B, unos 2,82 MiB; D, la guardia, 1,21x (21,40 frente a 17,64 B/B, fila 62 de `pendientes.md`).
  - Después, en jvm-check: B 2 954 912 B; D 1,213x (21,40 frente a 17,65).
  - Después, en app: B 2 954 912 B; D 1,215x (frente a 17,61).
  - Dentro de los límites: B por debajo de 3,5 MiB, D por debajo de 1,30 y un cambio de menos de ±0,02.
- **`rt29_`:**
  - Antes: 160, con `<= 8L * 20`.
  - Después: `assertEquals(8L * 20, encodings)` da 160, 20 por entrada; la pareja tarda 37,3 ms y con 20 contraseñas antiguas, 64,4 ms (x1,7, informativo).
- **`rt28_`:** 52,8 ms frente a 50,7 ms (x1,04, informativo).

### Ejecuciones del cierre (cada suite por separado, una sola ejecución de Gradle cada vez)
- **jvm-check, las clases tocadas** (`AppContainerVaultsTest`, `PlatformSourcesTest`, `imports.*`, `ContactsViewTest`, `ContactsTest`, `ErrorTextsTest`, `FoldersTest`, `PasswordMarkTest`, `UpgradeCopyFirstTest`, `VaultStoreImportTest`, `VaultV4Test`, `VaultV3Test`, `VaultStoreTest`, `EntryHistoryTest` y `AutoBackup*`): 34 clases, 840 tests y 0 fallos.
- **jvm-check `redteam.*`:** 15 clases, 147 tests y 0 fallos.
- **app, las mismas clases tocadas:** 34 clases, 840 tests y 0 fallos.
- **app `redteam.*`:** 15 clases, 147 tests y 0 fallos.

### Revisión experto-seguridad-cripto
- **Formatos:** no cambia ninguno, ni `appJson`, ni una versión, ni el AAD, ni una marca de copia.
  - CR-1 solo rellena `passwordChangedAt`, un campo que ya existe en la v4 (`vault/EntryHistory.kt:33`) y que nunca escribe una v2 ni una v3.
  - `comparable()` lo pone a nulo (`EntryHistory.kt:25`), así que no aparece ninguna versión falsa.
  - Los datos de la 1.x pasan: `aRealVaultOf143KeepsItsDate`, `VaultV3Test` y `UpgradeCopyFirstTest`, con `vault-1.4.3.json`.
- **CR-7, sin abrir la bóveda** (`VaultStore.kt:787-801`):
  - la maestra se comprueba bajo el `mutex`, en `NonCancellable`;
  - `KdfLock` va por `derive {}`;
  - mira `active()` antes y después de Argon2id;
  - la clave se pone a cero en un `finally` (`:800`);
  - no toca `_state`, la papelera, el reloj de A11, la subida ni el envoltorio.

  Una maestra equivocada no borra nada (`AppContainer.kt:731`). No cambia la estructura de `KdfLock` ni la del `mutex`.
- **CR-2:**
  - la copia de antes de la subida va primero (`AutoBackup.kt:485`, `:525`);
  - `copying` siempre vuelve a falso (`PendingUpgrade.kt:56`, `:66` y `:103`, en un `finally`), así que no hay espera sin fin;
  - A6 no borra la copia `seguridad-antes-de-2-0-`, porque `importCopies` la conserva (`UpgradeCopyFirstTest`).
- **CR-5:**
  - la vista previa nunca cuenta menos que el guardado (`MergePlan.kt:429`, `:438`), comprobado en 200 casos y en los topes reales;
  - el *snapshot* sale de una sola sección del `mutex` (`VaultStore.kt:1017`);
  - la sesión guarda la papelera en `rest` y la borra donde borra `existing` (`ImportSession.kt:252`, `:462-463`), con la misma vida al bloquear o cancelar;
  - nada se registra.

  Conocido y sin cambiar: el tercer `TooBig`, el de los adjuntos, no lo modela la vista previa; lo sigue decidiendo `applyImport`.
- **I-1:** «Importar» nunca está activo con `IMPORT_NO_MEMORY` a la vista (`ImportSession.kt:338`).
- **CR-6:** `storeSaveErrorText` (`VaultStore.kt:1757`) solo deja pasar textos fijos del almacén y nunca el mensaje de la excepción. Así ninguna ruta ni ningún texto en inglés llega a la pantalla. En «PIN y códigos» sigue sin registrarse nada.
- **CR-4:** el rechazo llega antes de cambiar nada (`Folders.kt:215`), y el archivo queda igual byte a byte (test).
- No hay dependencias, permisos ni hosts nuevos.

### Revisión Ingeniero Móvil Senior
- **Concurrencia:**
  - la espera de CR-2 es `StateFlow.first {}`, que se puede cancelar, antes de `lock.withLock`, y `copyBeforeUpgrade` no espera (`AutoBackup.kt:485`, `:525`);
  - los tests usan `StandardTestDispatcher`, `runCurrent`, `withTimeout` y una regla de 30 s, sin `sleep`;
  - el desbloqueo, que no se cancela, abre la puerta en un `finally` del test.
- **Ciclo de vida:**
  - `ContactsFrame` lee `pendingUpgrade` y `upgradeCopying` con `collectAsStateWithLifecycle` (`ContactsScreen.kt:96-99`);
  - la decisión es una función pura (`ContactsView.kt:60`) que se prueba en jvm-check.
- **Rendimiento:**
  - la vista previa hace una medida JSON más que antes, `jsonBytes(fitted())` contando sin construir la cadena, siempre en `Dispatchers.Default` y cancelable por `collectLatest`;
  - el diferencial a tamaño real cuesta más de 1,2 s por caso, así que se redujo a 4 casos (unos 3,5 s por test), y los 200 casos van a tamaño pequeño, en 1,8 s;
  - `RedTeam20ListCostTest` no se mueve.
- **Interfaz:**
  - los errores de guardado dicen el texto del almacén en 9 pantallas;
  - en la ficha, `failed` pasa de `Boolean` a texto (`VaultEntryScreen.kt:214`, `:244`);
  - no hay estado nuevo en `rememberSaveable`.
- **Importaciones:** las nuevas de `storeSaveErrorText` se pusieron al principio del bloque de importaciones de cada pantalla. Es un detalle sin efecto.

### Textos para el Abogado Digital (revisión final)
- `UPGRADE_COPYING` (`vault/VaultStore.kt:1748`): «Clavero está haciendo la copia automática de antes del cambio al formato nuevo. Espera a que termine e inténtalo otra vez.»
- `FOLDER_DELETE_NAME_TAKEN` (`vault/Folders.kt:39`): «Una de sus subcarpetas se llama igual que otra carpeta del sitio al que pasaría. Cambia antes uno de los dos nombres.»
- No hay textos nuevos fuera de esos dos. CR-6 solo hace que las pantallas enseñen textos que ya existían (`UPGRADE_*`, los topes y los de «Organizar»), y «Contactos» ahora dice `NEEDS_MASTER_FOR_CONTACTS` u `ONLY_ONE_IDENTITY` donde antes decía `NOT_SAVED_STATE`.

### Para el director
- **La desviación de CR-5:** las 200 bóvedas aleatorias van a tamaño pequeño, con la desigualdad de la medida, y hay 4 casos con los topes reales. El motivo es el coste medido; está en la sección 6.
- **Los ajustes por la firma** fuera de la lista autorizada: el ancla de R16 en `RedTeam20Recheck3Test.kt:244`, y `deleteNeedsMasterAndName` (CR-7, por comportamiento: afirmaba el defecto).
