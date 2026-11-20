# Auditoría completa de Clavero 2.0, parte D: las tres zonas que `security-review` no leyó línea a línea

**Commit auditado:** `16d2661`, en la rama `ccr-0aa27f95-u038cd`. El código de la app es el de `69c967b`, el final de la ronda única de arreglos de las revisiones: `git diff --stat 69c967b 16d2661 -- app/ jvm-check/` sale vacío. Después de `69c967b` solo hay documentos.

**Encargo:** fila 80 de `pendientes.md` y parte D de `advisor-t12-review-fixes.md`, líneas 237–293:
- `AutoBackup.kt` entero: el alcance de A6 por bóveda, `copyBeforeUpgrade` y el orden nuevo de CR-2, con sus tres ventanas;
- la migración por bóveda de `SettingsStore.kt`: que no baje el perfil de derivación (A8), que no pierda la marca del reloj de A11 y que no mezcle ajustes entre bóvedas;
- `VaultList`, `VaultOrganize`, `VaultPickerScreen` y `EntryFields`: concurrencia, ciclo de vida, estado secreto fuera de `rememberSaveable`, textos verdaderos, OOM y aislamiento entre bóvedas, y en especial si `FOLDER_DELETE_TO_NONE` es falso.

**Solo lectura:**
- no cambié código ni ejecuté Gradle;
- usé `git`, `grep` y la lectura de los archivos;
- escribí solo este archivo, sin commit.

Cada `archivo:línea` sale de `16d2661`. Las rutas son relativas a `app/src/main/java/app/seguridad/`, salvo que digan otra cosa.

**Lecturas previas:**
- `CLAUDE.md`;
- `experto-seguridad-cripto` (`SKILL.md`, `references/auditoria.md`, `cripto-moderna.md` y `criptoanalisis.md`);
- las skills `ingeniero-movil-senior:desarrollo-movil-senior` e `ingeniero-movil-senior:auditoria-seguridad-movil`;
- el diseño de la 2.0, `progress.md` (las últimas líneas y los Ruling y Owner decision de A6, CR-2, T2 I-1 y RT-22), `pendientes.md`, `security-review.md`, `code-review.md`, `review-fixes-report.md` (§3, CR-2), `final-audit-c.md` y el parte del 20-11.

## Alcance y modelo de amenaza

- **Qué entra:**
  - `vault/AutoBackup.kt` (1 132 líneas), entero, con lo que llama: `vault/PendingUpgrade.kt`, `vault/A6Text.kt`, `vault/BackupSchedule.kt`, y en `vault/VaultStore.kt` `opened`, `unlock`, `lock`, `export` y `upgradeRefusal`;
  - en `AppContainer.kt`: el cableado de cada bóveda (`Slot`), las marcas del reloj, `newVault` y `deleteVault`;
  - `settings/SettingsStore.kt` y `settings/Settings.kt`, frente a los de `v1.4.3` (`a3ac942`);
  - las cuatro pantallas, con `vault/Folders.kt`, `vault/OrganizeTexts.kt` y `vault/VaultsTexts.kt`, y el `Field` de `ui/screens/VaultEntryScreen.kt`.
- **Atacantes (decisión (f), más lo de la 2.0):**
  - una carpeta compartida y su proveedor de documentos (otra app que el usuario eligió como carpeta de las copias);
  - quien tiene el móvil desbloqueado un momento, con la bóveda bloqueada;
  - la muerte del proceso;
  - una bóveda contra otra en el mismo móvil;
  - la subida desde la 1.4.3 (`settings.json` y archivo v3).
- **Lo que no entra:**
  - la criptografía y los formatos (parte A);
  - MASVS completo y el APK (parte B);
  - el método general (parte C);
  - el diff de la ronda (parte E).
- **No reabro** los riesgos con nombre 51, 62, 67 y 69, ni la fila 58 (dos `EntryListViewModel`). Ningún hallazgo de esta parte los toca.

## Resumen

No hay nada Crítico, Alto ni Medio. Las tres zonas cumplen lo que pide el encargo:
- **A6** solo borra lo que su bóveda apuntó;
- **la migración** no baja el coste, no pierde la marca de A11 y no mezcla ajustes entre bóvedas;
- **las pantallas** no guardan secretos en estado guardado.

Las tres ventanas de CR-2 son benignas (§1.3). Hay una cuarta, la de una carpeta que no contesta, que es el único hallazgo con impacto (D-1).

| ID | Severidad | Hallazgo | Ubicación |
|---|---|---|---|
| D-1 | Baja | Una carpeta cuyo proveedor no contesta deja la bóveda sin poder guardar nada, con `UPGRADE_COPYING`, hasta que se cierra la app: la espera de la copia de antes de la subida no tiene límite de tiempo | `vault/PendingUpgrade.kt:81-88`; `vault/AutoBackup.kt:749`, `:448`; `android/SafBackupTarget.kt:28-36`, `:53-54`; `vault/VaultStore.kt:315`, `:1748-1749` |
| D-2 | Informativa | En «Bóvedas», un diálogo que se abre después del bloqueo (sobre todo «Importar como bóveda nueva» al volver del selector) no se cierra, y pide una contraseña con la bóveda bloqueada (regla de la revisión de la rama, C I-1) | `ui/screens/VaultPickerScreen.kt:201-203`, `:212-224`, `:197`; `MainActivity.kt:57` |
| D-3 | Informativa | `FOLDER_DELETE_TO_NONE` no es falso en sentido estricto, pero no usa el vocabulario de la app y es ambiguo: las subcarpetas no van a «Sin carpeta», suben «Arriba del todo» con lo que tienen dentro | `vault/OrganizeTexts.kt:60`; `ui/screens/VaultOrganize.kt:360`; `vault/Folders.kt:208`, `:219`, `:223` |
| D-4 | Informativa | «Borrar carpeta» deja a la vista el rechazo de la opción anterior al cambiar de opción, y su botón no tiene estado «ocupado» | `ui/screens/VaultOrganize.kt:346-347`, `:356-357`, `:371-382` |

## Hallazgos

### [Baja] D-1. Una carpeta que no contesta deja la bóveda sin guardar nada, con `UPGRADE_COPYING`, hasta que se cierra la app

- **Severidad:** Baja. Afecta a la disponibilidad, no a la confidencialidad ni a la integridad: no se pierde ni se escribe nada mal. Necesita una carpeta de las copias cuyo proveedor no conteste. Puede ser una app que el usuario eligió como carpeta, o un proveedor en la nube que se queda esperando a la red; esto último está «a comprobar en el móvil».
- **CVSS 3.1:** 2.5, `AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L`.
- **MASVS v2:** MASVS-PLATFORM-1 (el uso seguro de la IPC: aquí, un `DocumentsProvider` de otra app).
- **CWE:** CWE-1088 (acceso síncrono a un recurso remoto sin límite de tiempo).
- **Dónde:**
  - `vault/PendingUpgrade.kt:81-88`: `afterUnlock` espera `copyBefore()` sin límite;
  - `vault/VaultStore.kt:594`: `copyBefore` es `beforeUpgrade`, que en la app es `autoBackup.copyBeforeUpgrade()` (`AppContainer.kt:441`);
  - `vault/AutoBackup.kt:749`: esa copia es `scope.async { lock.withLock { … } }.await()`, que espera el `lock` de las copias (`:448`) y después escribe en la carpeta;
  - `android/SafBackupTarget.kt:28-36` y `:53-54`: `DocumentsContract.createDocument` y `openOutputStream` son llamadas *binder* bloqueantes en `Dispatchers.IO`, sin ningún límite. Ningún `withTimeout` ni `runInterruptible` del código toca las copias; los dos únicos `withTimeoutOrNull` están en `ui/screens/EntryRow.kt:375` y `VaultScreen.kt:200`, y son de otra cosa.
- **Qué pasa:**
  1. Con una bóveda v2 o v3, el primer desbloqueo con la maestra en la 2.0 pone `copying` a verdadero (`vault/VaultStore.kt:538`, `PendingUpgrade.kt:56`).
  2. `afterUnlock` llama a `copyBeforeUpgrade`.
  3. Si el proveedor no contesta, o si el `lock` de las copias lo tiene otra copia que se quedó dentro del proveedor (la variante de la ventana 3, §1.3), la llamada no vuelve nunca. Entonces:
     - `copying` sigue verdadero;
     - `asking` no llega (`PendingUpgrade.kt:93-99`), así que el diálogo de la subida, con «Exportar una copia antes» y «Seguir sin copia», no sale;
     - cada guardado da `UPGRADE_COPYING` (`VaultStore.kt:315`), que dice «Espera a que termine e inténtalo otra vez» (`:1748-1749`), algo que aquí no es verdad;
     - las copias debidas esperan para siempre (`AutoBackup.kt:485`, `:525`).
  4. «Bloquear» y volver a abrir no lo arregla. El bloqueo pone `copying` a falso (`VaultStore.kt:821`), pero el desbloqueo siguiente lo vuelve a poner a verdadero, y su `copyBeforeUpgrade` espera el mismo `lock`, que sigue tomado.
  5. La huella no sirve: con un archivo v2 o v3 la rechaza `BiometricRefusal.UPGRADE` (diseño, «Formato v4»).
  6. Solo hay dos salidas, y ningún texto las dice:
     - quitar la carpeta en Ajustes, que se puede porque la bóveda está abierta, y después cerrar Clavero del todo;
     - esperar a que el proveedor conteste.
- **Antes de la 2.0 era menos:** en la 1.4.3, un proveedor así solo paraba las copias automáticas. En la 2.0 para además todos los guardados, mientras dura la subida.
- **Impacto:** un proveedor hostil o roto deja la bóveda en solo lectura cada vez que se abre, hasta que el usuario adivina la salida. No se pierde nada: el archivo sigue siendo el v2 o v3, y se abre con la maestra.
- **Corrección** (en `vault/PendingUpgrade.kt:84-88`, y un texto nuevo en `ui/screens/VaultUpgrade.kt:124`):
  ```kotlin
  /** Cuánto espera la subida a la copia de la carpeta antes de preguntar al usuario (la copia sigue sola). */
  internal const val UPGRADE_COPY_WAIT_MS = 120_000L

  val copy = try {
      // Solo se corta la espera (el await de AutoBackup.copyBeforeUpgrade): la copia sigue en el ámbito de las copias.
      withTimeoutOrNull(UPGRADE_COPY_WAIT_MS) { copyBefore() } ?: CopyResult.Failed(UPGRADE_AUTO_COPY_NOT_DONE)
  } catch (e: Exception) {
      CopyResult.Failed(AUTO_BACKUP_FAILED)
  }
  ```
  - En el bloque `synchronized(opening)` se guarda `autoCopyNotDone = copy is CopyResult.Failed && copy.message == UPGRADE_AUTO_COPY_NOT_DONE`.
  - El diálogo enseña, en lugar de `UPGRADE_AUTO_COPY_FAILED`, un texto nuevo para el Abogado. Propuesta: «La copia automática de antes del cambio aún no ha terminado: la carpeta no contesta. Puedes exportar una copia a otro sitio o seguir sin copia.»
  - **Por qué no rompe nada:**
    - `withTimeoutOrNull` funciona dentro del `withContext(NonCancellable)` de `afterUnlock`, porque crea su propio hijo;
    - solo cancela el `await()`: el `async` vive en el ámbito de `AutoBackup` y sigue;
    - si el proveedor contesta tarde, `exportBeforeUpgrade` (`VaultStore.kt:1452`, `:1459`) no escribe nada cuando la subida ya se guardó o se descartó, y el documento vacío se borra (`AutoBackup.kt:979`);
    - si la copia ya tenía su instantánea, lo que escribe es una copia v3 completa con la maestra de ahora, apuntada como las de antes de importar, igual que hoy.
  - «Exportar una copia antes» no usa el `lock` de las copias (`ui/screens/VaultUpgrade.kt:104`): solo el `mutex` de la bóveda, para la instantánea (`VaultStore.kt:1458-1466`). Así funciona aunque el proveedor siga sin contestar.
- **Prueba de concepto (test que hoy falla):** `UpgradeCopyFirstTest.aFolderThatNeverAnswersStillLeavesTheChoice`, en `app/src/test/java/app/seguridad/vault/UpgradeCopyFirstTest.kt`, con su `SlowFolder` y su `setup`.
  1. `val t = SlowFolder(); val s = setup(t); t.gate = CompletableDeferred()`. La puerta no se completa nunca: es un proveedor que no contesta, y la copia se queda en `write` con el `lock` tomado (`UpgradeCopyFirstTest.kt:66`).
  2. `val unlocking = async { s.unlock("maestra-de-prueba-143".toCharArray()) }; runCurrent()`. Comprobar `assertTrue(s.upgradeCopying.value)`.
  3. `advanceTimeBy(UPGRADE_COPY_WAIT_MS + 1); runCurrent()`.
  4. Afirmar todo esto:
     - `assertFalse(s.upgradeCopying.value)`;
     - `assertTrue(s.pendingUpgrade.value)`;
     - `assertEquals(UPGRADE_PENDING, refusal { s.upsert(note("x")) })`;
     - `withTimeout(5_000) { unlocking.await() }`;
     - `assertTrue(s.completeUpgrade())`, y el archivo es la versión 4 (`VaultCrypto.decodeFile(...).version == 4`).
  5. Al final, `t.gate!!.complete(Unit)` y `runCurrent()`: la copia tardía no rompe nada y no se apunta como la copia de la subida (`upgradeDone` sigue nulo).

  Hoy falla en el punto 4: `copying` sigue verdadero, el guardado dice `UPGRADE_COPYING` y `unlocking` no vuelve.

  **Variante** `aDueCopyStuckBeforeTheUnlockDoesNotHoldTheUpgrade`: con la bóveda bloqueada y `t.gate` sin completar, `auto!!.launchDue(clock)` toma el `lock` antes del desbloqueo y se queda en el proveedor. El desbloqueo y los pasos 3 y 4 deben acabar igual.

### [Informativa] D-2. En «Bóvedas», un diálogo abierto después del bloqueo no se cierra

- **Severidad:** Informativa. No se importa ni se crea nada: `newVault` vuelve a mirar `canAddVault` y rechaza la acción con `VAULT_NEW_NEEDS_OPEN` (`AppContainer.kt:664`). Pero rompe una regla de la revisión de la rama (C I-1): un bloqueo cierra lo que un diálogo tiene de la bóveda o una contraseña escrita (`ui/screens/VaultPickerScreen.kt:163-173`). Y la rompe por un camino real.
- **CVSS 3.1:** no aplica (0.0: sin impacto en C, I ni A; la contraseña escrita es la del propio usuario, en su app y con `FLAG_SECURE`).
- **MASVS v2:** MASVS-STORAGE-2 (secretos que no deben seguir en memoria ni a la vista después del bloqueo).
- **CWE:** CWE-696 (orden incorrecto: el estado se comprueba cuando cambia, no cuando se abre el diálogo).
- **Dónde:**
  - `ui/screens/VaultPickerScreen.kt:201-203`: `LaunchedEffect(active, activeState)` cierra el diálogo que no se debe quedar, pero solo cuando cambia el estado;
  - `:212-224`: el selector de «Importar como bóveda nueva» pone `dialog = PickerDialog.ImportNew(uri)` después de leer la cabecera en `Dispatchers.IO`, sin mirar el estado;
  - `MainActivity.kt:57`: al volver de un selector, `lockIfIdle()` bloquea en `onStart`, antes de que llegue el resultado del selector, si pasó el tiempo. Con «Al salir» bastan 2 min en el selector (`vault/VaultStore.kt:1811`, `:1818-1822`).
  - `:197`: `canAdd` sale de `produceState`, que guarda el valor anterior (verdadero) mientras vuelve a leer en IO. En ese instante, «Nueva bóveda» sigue activo después de un bloqueo.
- **Qué pasa:**
  1. Con la bóveda abierta, el usuario toca «Importar como bóveda nueva» y pasa más de 2 min en el selector.
  2. Al volver, `onStart` bloquea y el efecto corre con `dialog == null`.
  3. Después, el resultado del selector abre «Contraseña maestra de la copia» sobre una bóveda bloqueada. El diálogo se queda abierto, con lo que se escriba en un `remember` (`ui/screens/VaultBackup.kt:247`).
  4. Al confirmar, la acción se rechaza con `VAULT_NEW_NEEDS_OPEN`, y la contraseña se pone a cero (`VaultPickerScreen.kt:407`).
- **Corrección:**
  - en `VaultPickerScreen.kt:201`, `LaunchedEffect(active, activeState, dialog) { dialog?.let { if (!pickerDialogKept(it.kind, activeState)) dialog = null } }`: así un diálogo que se abre con la bóveda bloqueada se cierra en el mismo paso;
  - además, en `:221`: `else -> if (pickerDialogKept(PickerDialogKind.IMPORT_NEW, active.state.value)) dialog = PickerDialog.ImportNew(uri) else snackbar.showSnackbar(VAULT_NEW_NEEDS_OPEN)`.
- **Prueba de concepto:** el repositorio no tiene pruebas de interfaz de Compose (`createComposeRule` no aparece en `app/src/test`). Por eso, como en `VaultPickerDialogsTest.theScreenClosesItOnTheVaultsState` (`app/src/test/java/app/seguridad/ui/screens/VaultPickerDialogsTest.kt:30-39`), dos anclas:
  1. `VaultPickerDialogsTest.aDialogOpenedAfterTheLockClosesToo`: el efecto lleva `dialog` entre sus claves (expresión `LaunchedEffect\(active, activeState, dialog\)`). Hoy falla.
  2. `VaultPickerDialogsTest.theImporterAsksOnlyWithTheVaultOpen`: la línea que pone `PickerDialog.ImportNew(uri)` va detrás de `pickerDialogKept(PickerDialogKind.IMPORT_NEW, active.state.value)`. Hoy falla.

  Y una prueba en el móvil (tarea 11): «Importar como bóveda nueva», 2 min y medio en el selector y elegir una copia. No debe salir el diálogo de la contraseña.

### [Informativa] D-3. `FOLDER_DELETE_TO_NONE`: no es falso en sentido estricto, pero confunde

- **Severidad:** Informativa (veracidad y claridad de un texto; no se pierde nada).
- **CVSS 3.1:** no aplica. **MASVS v2:** no aplica.
- **CWE:** no aplica; como mucho, CWE-451 en su sentido más débil.
- **Dónde:**
  - `vault/OrganizeTexts.kt:60`: «Sus entradas y sus subcarpetas se quedan sin carpeta»;
  - lo enseña `ui/screens/VaultOrganize.kt:360` cuando la carpeta borrada está arriba del todo (`parent == null`, `:348`).
- **Qué hace el código** (`vault/Folders.kt:205-233`, opción `PARENT`, con `up = null`, `:208`):
  - las entradas de la carpeta pasan a «Sin carpeta» (`folderId = up`, `:223`): el texto lo dice bien;
  - las subcarpetas pasan arriba del todo (`parentId = up`, `:219`), con sus entradas y sus subcarpetas dentro: las entradas de las subcarpetas no cambian de carpeta.
- **Por qué confunde:**
  - la app usa «Sin carpeta» (`FOLDER_NONE`, `OrganizeTexts.kt:49`) para las entradas y «Arriba del todo» (`FOLDER_TOP`, `:50`; `VaultOrganize.kt:156`) para las carpetas;
  - leído con ese vocabulario, el texto hace pensar que todo, también lo que hay dentro de las subcarpetas, acaba en el filtro «Sin carpeta», o que las subcarpetas desaparecen;
  - el texto hermano, `folderDeleteToParent` (`OrganizeTexts.kt:61`), dice bien «pasan a «X»».

  **Veredicto:** no es falso en sentido estricto (una carpeta arriba del todo no está dentro de ninguna), pero es inexacto con el vocabulario de la app. Se cambia.
- **Corrección** (el texto final, del Abogado): `const val FOLDER_DELETE_TO_NONE = "Sus entradas pasan a «Sin carpeta» y sus subcarpetas, con lo que tienen dentro, arriba del todo"`.
- **Prueba de concepto:** `FolderDeletionTextTest.aTopFolderSaysWhereItsSubfoldersGo`, en `app/src/test/java/app/seguridad/vault/`.
  1. Una `VaultData` con la carpeta A arriba del todo (entrada `e1`) y la subcarpeta B dentro de A (entrada `e2`).
  2. `withoutFolder(A, FolderDeletion.PARENT, now)`.
  3. Afirmar:
     - `e1.folderId == null`;
     - el `parentId` de B es `null`;
     - `e2.folderId == B`.
  4. Afirmar que `FOLDER_DELETE_TO_NONE` contiene `FOLDER_NONE` y «arriba del todo», y que no contiene «subcarpetas se quedan sin carpeta». Hoy falla el punto 4.

### [Informativa] D-4. «Borrar carpeta» deja a la vista el rechazo de la otra opción y no tiene estado «ocupado»

- **Severidad:** Informativa (es un detalle menor; según `CLAUDE.md`, «se arreglan todos»).
- **CVSS 3.1:** no aplica. **MASVS v2:** no aplica. **CWE:** no aplica.
- **Dónde:**
  - `ui/screens/VaultOrganize.kt:346-347`: los estados `to` y `error`;
  - `:356-357`: la fila y el `RadioButton` cambian `to` sin borrar `error`;
  - `:371-382`: «Borrar carpeta» lanza `deleteFolder` sin `busy`.
- **Qué pasa:**
  - con «pasan a la carpeta de arriba» rechazado por `FOLDER_DELETE_NAME_TAKEN` (CR-4, `vault/Folders.kt:215`), el texto rojo sigue a la vista si el usuario elige «a la papelera», una opción que sí funciona. La línea del error ya no habla de lo que está elegido;
  - un doble toque lanza dos borrados. El segundo no hace nada, porque la carpeta ya no está y `withoutFolder` da `null`.
- **Corrección:**
  - `onClick = { to = option; error = null }`, en la fila y en el `RadioButton`;
  - `var busy by remember { mutableStateOf(false) }`;
  - `TextButton(enabled = !busy, onClick = { busy = true; scope.launch { try { … } finally { busy = false } } })`, como `NameDialog` (`:259`, `:275-284`).
- **Prueba de concepto** (ancla, porque no hay pruebas de Compose): `VaultOrganizeSourceTest.deleteFolderClearsTheRefusalAndWaits`. En el cuerpo de `DeleteFolderDialog` busca `to = option; error = null` y un botón con `enabled = !busy`. Hoy falla.

## 1. `AutoBackup.kt`, entero

### 1.1 El alcance de A6 por bóveda: cada bóveda solo borra lo suyo

- **Una instancia por bóveda.** Cada `Slot` tiene su `AutoBackup`, con los destinos, la petición de A6 y los resultados de su propio id (`AppContainer.kt:455`, `:459-463`), en un ámbito propio que el borrado de la bóveda cancela (`:408`, `:744`). Lo que vive en memoria también es de cada instancia: `copiedRevision`, `failedAt`, `pending` y `lock` (`AutoBackup.kt:442-448`).
- **Se borra solo por id, y solo de las listas propias del destino:**
  - las copias automáticas (`copies`) y las de antes de importar y de antes de la 2.0 (`importCopies`): `AutoBackup.kt:1026-1035` y `:1039-1047`;
  - `SafBackupTarget.delete` solo borra un hijo de la carpeta (`android/SafBackupTarget.kt:71-76`);
  - nunca se borra un archivo por su nombre, salvo los `.tmp` con los nombres exactos de la app (`AutoBackup.kt:1031`). Ese borrado entre bóvedas ya está aceptado (fila 56).
- **La generación.** Antes de cada borrado se comprueba que la bóveda sigue siendo la de la copia (`:1040`, `:1048`). Una copia de una bóveda reemplazada no se apunta (`:847-848`, `:914-915`; `settings/Settings.kt:369-372`).
- **Los ajustes:**
  - la petición, sus destinos y lo que se dijo de ella se guardan por bóveda (`AppContainer.kt:953-1002`), y un resultado de una bóveda ya borrada se descarta (`listed(id)`, `:956`, `:984`);
  - importar, crear y borrar tocan solo su id (`settings/SettingsStore.kt:97-104`, `:138-158`; `AppContainer.kt:422`, `:429`, `:754`);
  - un permiso de carpeta solo se devuelve si ningún destino de ninguna bóveda la usa (`AppContainer.kt:863-865`).
- **Los textos:**
  - el recuento de copias sin apuntar dice «de otra bóveda» entre sus ejemplos (`AutoBackup.kt:171-196`; `vault/A6Text.kt:198-209`);
  - `AUTO_BACKUP_REPLACED` y `AUTO_BACKUP_REPLACED_SEVERAL` hablan solo de las copias «que la app tenía apuntadas en esta carpeta» (`AutoBackup.kt:153-165`). Es verdad por bóveda.
- **La condición de parada de CR-2 se cumple:** A6 no borra la copia `seguridad-antes-de-2-0-` de una petición de la 1.4.3.
  - Esa petición solo borra los destinos congelados al pedirla: `Settings.kt:378-381`, donde `replaceRequested` añade las `importCopies` de ese momento, y `withImportCopy` (`:346-349`) no los amplía.
  - Una petición sin lista solo toca `copies` (`AutoBackup.kt:855`).
  - Una petición de la 2.0 (un cambio de maestra después de la subida) sí la borra, porque está con la maestra de antes. Así lo dicen el KDoc (`AutoBackup.kt:111-114`) y los textos («las de antes de la 2.0»).

### 1.2 `copyBeforeUpgrade`

- `AutoBackup.kt:749-794`:
  - prueba los destinos en su orden y se queda con el primero que la guarda;
  - un bloqueo o un reemplazo de la bóveda paran (`:757`);
  - escribe como las demás: primero un `.tmp` y luego cambia el nombre, o directamente si el proveedor no deja cambiarlo (`:942-968`);
  - el documento se apunta en `importCopies` con la generación de cuando empezó (`:768-779`; `AppContainer.kt:969-977`);
  - captura `Throwable`, así que nunca cierra la app (`:780-793`).
- **Lo que copia es el archivo v2 o v3 que está en disco** (`VaultStore.kt:1452`, `:1454-1475`): `json` sale de `vault`, que sigue siendo el v3, y `writeBackup(..., vaultVersion = version)` le pone la marca 2, que la 1.4.3 abre.
  - Con la subida guardada o descartada, no escribe nada y el documento vacío se borra (`:1459`; `AutoBackup.kt:977-981`).
  - Los adjuntos son los mismos: la subida no los cambia; `refs` sale de los datos subidos, que tienen las mismas entradas (`VaultStore.kt:1463`).
- **El único fallo es el de D-1:** no tiene límite de tiempo.

### 1.3 El orden nuevo de CR-2 y sus tres ventanas

- **Lo nuevo:**
  - `copying` pasa a verdadero en `opened`, dentro de `synchronized(opening)` y antes de publicar `Unlocked` (`VaultStore.kt:538-539`);
  - `runDue` y `runChanged` esperan `upgradeCopying.first { !it }` antes de tomar el `lock` (`AutoBackup.kt:485`, `:525`);
  - `copying` vuelve a falso con `saved`, con `drop` (el bloqueo, `VaultStore.kt:821`) y en el `finally` de `afterUnlock` (`PendingUpgrade.kt:100-104`).
  - La espera es un `StateFlow.first`, que se puede cancelar, y va fuera del `lock`, así que no hay bloqueo mutuo con `copyBeforeUpgrade`.
  - El orden de memoria es bueno: `copying` se escribe antes que `state`, en el mismo hilo, y los dos son `StateFlow`; quien ve `Unlocked` ve también `copying`.

| Ventana | Veredicto | Evidencia |
|---|---|---|
| **`runChanged` sin rojo propio** | Confirmada y sin riesgo hoy: su espera es solo una defensa. Hay un único recolector (`AutoBackup.kt:450-459`): cada `Unlocked` lanza `runDue(afterUnlock = true)`, que espera, y `runChanged` no puede empezar hasta que `runDue` vuelve. Un `runChanged` ya empezado antes del desbloqueo de una v2 o v3 solo es posible si quedó de una apertura anterior del mismo proceso (la subida no se guardó y se bloqueó): entonces se comporta como la ventana 3. | `AutoBackup.kt:458`, `:485`; `review-fixes-report.md`, §3. Si se quiere fijar, un ancla de fuente: `AutoBackupSourceTest.bothRunsWaitForTheUpgradeCopyBeforeTheirLock`, que busca `store.upgradeCopying.first { !it }` justo antes de `lock.withLock` en `runChanged` y en `runDue`. |
| **Un `Error` que deja `copying` en verdadero** | Confirmada, y no deja un estado atascado. Entre `opened` (`VaultStore.kt:640`) y el `try` de `afterUnlock` solo están `tellCostKept`, que no lanza nada, `collectGarbage`, que captura `Exception` (`:408-417`), y `master.fill`. Un `Error` sube hasta `VaultScreen.kt:471`, que solo captura `CryptoFailure` y `Exception` (`:473`, `:477`), y cierra la app: no hay ningún manejador global en `app/src/main`. Un `Error` dentro de `copyBefore` pasa por el `finally`, que pone `copying` a falso. Con el proceso muerto, `copying` (en memoria) desaparece, el archivo sigue siendo el v2 o v3 (nada se escribió) y el desbloqueo siguiente empieza de cero. | `PendingUpgrade.kt:81-105`; `AppContainer.kt:91-93` (los `Error` van al manejador del hilo); `grep` de `setDefaultUncaughtExceptionHandler` vacío. La otra llamada a `unlock`, que sí captura `Throwable` (`AppContainer.kt:620`, `:670-674`), es la de una bóveda importada, que ya es v4, así que nunca deja la subida pendiente. |
| **Un `launchDue` justo antes de un desbloqueo** | Confirmada y benigna. `runDue` pasa la espera con la bóveda bloqueada, toma el `lock` y mira `Unlocked` dentro del bucle, destino por destino (`AutoBackup.kt:540`). Si el desbloqueo cae en medio: (a) hace una copia automática del archivo v3 en disco, con la marca 2, que la 1.4.3 abre y que está bajo la maestra de ahora; (b) A6, si había una petición de la 1.4.3, solo borra sus destinos congelados (§1.1); (c) `copyBeforeUpgrade` espera el `lock` (el `Mutex` es FIFO) y hace su copia después; (d) mientras tanto, cada guardado dice `UPGRADE_COPYING`, que es verdad. El único efecto es que la copia de la subida no es el primer documento. No se pierde nada ni se escribe nada con otra maestra. | `AutoBackup.kt:507-509`, `:525-546`; `VaultStore.kt:1443`, `:1454-1475`. La variante peligrosa (que esa copia se quede dentro del proveedor) es D-1. |

- **Las dudas del director que caen en esta parte:**
  - **«Exportar una copia antes» frente al `lock`:** no lo toma. Llama directamente a `store.exportBeforeUpgrade` (`ui/screens/VaultUpgrade.kt:104`), que solo toma el `mutex` de la bóveda para la instantánea (`VaultStore.kt:1458-1466`). No hay bloqueo mutuo con las copias.
  - **El `dispatcher` del ámbito de `AutoBackup`:** es `Dispatchers.IO` (`AppContainer.kt:113`, `:408`). El orden de CR-2 vale con cualquiera.

## 2. La migración por bóveda de `SettingsStore.kt`

- **El coste de Argon2id (A8) no baja:**
  - `LegacyVaultSettings.kdf` (`settings/SettingsStore.kt:200`) pasa tal cual a la primera bóveda (`:241`, `:274-276`);
  - lo comprueba `PerVaultSettingsTest.oldSettingsMoveToPrincipal`, con `"kdf":"HIGH"` (`app/src/test/java/app/seguridad/settings/PerVaultSettingsTest.kt:31`, `:50`);
  - `sane` baja «Alto» a «Estándar» solo si el móvil no tiene memoria para «Alto» (`SettingsStore.kt:177`). Es la misma regla de la 1.4.3 (`a3ac942:…/SettingsStore.kt:125`), ahora por bóveda;
  - el perfil es solo el coste de las envolturas nuevas. Volver a envolver al desbloquear coge el máximo de cada eje entre el archivo y el perfil (`vault/Vault.kt:603-607`). Sin memoria para subir, se queda con el coste que tenía el archivo, nunca uno menor (`:611-624`);
  - una bóveda nueva empieza con «Estándar», que es lo que dice el diseño («Por bóveda: … el coste de Argon2id»). No baja nada que existiera: una copia «Alto» importada como bóveda nueva sigue en «Alto» por A8.
- **La marca del reloj de A11 no se pierde:**
  - `clockHighWaterMs` de la 1.4.3 pasa a la primera bóveda (`SettingsStore.kt:201`, `:242`; `Settings.kt:179`; mismo test, `:58`);
  - en memoria, la marca vigente es el máximo entre la guardada (o la bajada por la maestra) y la más alta vista (`AppContainer.kt:267-268`);
  - cada hora vista sube la de todas las bóvedas (`:282-291`, `:297-302`), y solo la maestra de una bóveda baja la suya (`:323-331`; `Settings.kt:432`, `:435`);
  - una bóveda nueva empieza con la más alta de ese momento (`AppContainer.kt:334-341`, `:668`);
  - el guardado solo sube la marca, salvo el reinicio de la propia bóveda (`:373-375`);
  - en el primer arranque de la 2.0, el registro se reconstruye con `principal` (`vault/VaultRegistry.kt:163-176`, `rebuilt`), así que `clockIds()` la incluye.
- **No mezcla ajustes entre bóvedas:**
  - lo de la 1.4.3 va solo a `principal` (`SettingsStore.kt:274-276`);
  - los campos de la 1.4.3 (`LEGACY_FIELDS`, `:251`) y los de nivel superior de `Settings` de la 2.0 (`Settings.kt:41-101`) no tienen ningún nombre en común: un `settings.json` escrito por la 2.0 nunca trae de vuelta una primera bóveda borrada (lo comprueba el final del mismo test);
  - cada objeto de `perVault` se convierte por su cuenta (`SettingsStore.kt:280-295`);
  - todo lo que se lee o escribe por bóveda lleva su id: `AppContainer.kt:415`, `:432`, `:434`, `:455`, `:462`; Ajustes usa `vault.id` (`ui/screens/SettingsScreen.kt:294`, `:659-660`), y un cambio de bóveda rehace la pantalla (`ui/AppNavHost.kt:202-204`).
- **Las listas de la 1.4.3 se tratan como las trataba la 1.4.3:**
  - solo las de la carpeta elegida (`SettingsStore.kt:223-224`, como `currentAutoBackupCopies` y `currentImportCopies` de la 1.4.3); `importCopiesFolder` existe desde la 1.4.0;
  - las de otra carpeta van a `pastTargets` (`:232-239`);
  - la petición de A6 llega con `replaceRequest = null`, que se lee como `LEGACY_A6_REQUEST` (`vault/A6Text.kt:31-35`, `:45-50`).
- **Un archivo dañado da los valores de fábrica** (`SettingsStore.kt:39-43`), como en la 1.4.3. Ningún campo de la 1.4.3 tiene un tipo distinto en la 2.0, así que la migración no añade ninguna forma nueva de fallar.

## 3. Las cuatro pantallas

- **`VaultList`:**
  - **estado secreto:** la búsqueda, el filtro y la posición viven en `EntryListViewModel`, nunca en estado guardado (`ui/screens/VaultList.kt:241-249`), y se borran en cada bloqueo de su propia bóveda (`:291`, `:351-359`); ninguno de los cuatro archivos usa `rememberSaveable` (`grep` vacío);
  - **OOM:** el cálculo va fuera del hilo principal y captura `OutOfMemoryError` (`:305-313`);
  - **un bloqueo a mitad:** un resultado que llega después de un bloqueo no se publica (`:315`), y `collectLatest` espera al cálculo anterior antes de vaciar `cache` (`:294-300`), así que no queda nada de la bóveda en la memoria del `ViewModel`;
  - **los diálogos:** solo guardan ids (`:428-434`);
  - **las copias al portapapeles:** leen la entrada de la bóveda abierta en el momento del toque y van con `copySensitive` (`:470-497`);
  - **la papelera:** su confirmación quita `trashAsk` antes de lanzar el guardado, así que un doble toque no hace nada (`:699-700`);
  - **aislamiento:** `key(vault)` rehace la navegación y sus `ViewModel` (`ui/AppNavHost.kt:202-204`).
  - **Textos:** `movedSelected(0)`, «Ya estaban ahí», es verdad porque la lista solo tiene entradas que se pueden mover (`:326`).
- **`VaultOrganize`:**
  - cada diálogo se cierra cuando no hay datos, es decir, al bloquear (`Gone`, `ui/screens/VaultOrganize.kt:141`);
  - cada guardado pasa por el almacén y cada rechazo sale como `storeSaveErrorText`, en rojo dentro del diálogo, que se queda abierto: `:281`, `:306`, `:379`, `:412`, `:441`. Esto responde a la duda del director sobre cómo se enseña `OrganizeRefused`;
  - los nombres se enseñan con `plainReportText`;
  - `NameDialog` tiene `busy` (`:259`, `:275-284`), pero «Borrar carpeta» no lo tiene (D-4).
- **`VaultPickerScreen`:**
  - **nombres:** solo el de la bóveda activa y abierta (`ui/screens/VaultPickerScreen.kt:137`, `:233`); las demás son «Bóveda N»;
  - **contraseñas:** las maestras se ponen a cero (`:338`, `:367`, `:407`);
  - **las acciones:** van de una en una en `appScope` (`AppContainer.kt:853-858`), y `newVault` vuelve a mirar `canAddVault` (`:664`);
  - **borrar:** pide la maestra y el nombre, y una bóveda bloqueada se borra sin abrirla (CR-7, `vault/VaultStore.kt:787-803`; `AppContainer.kt:715-760`);
  - **el orden y el color:** solo con `canAdd` (`:255`, `:263`, `:275`);
  - el único fallo es D-2.
- **`EntryFields`:**
  - los campos ocultos y los secretos de las plantillas se enseñan tapados y se copian con `copySensitive` (`ui/screens/VaultEntryScreen.kt:145-176`);
  - el CVV y el PIN van con `NoCopy` y `NumberPassword` (`ui/screens/EntryFields.kt:240-254`);
  - lo que se escribe vive en la sesión del editor (`EntryEditSession`), y las notas en un `remember` de la composición (`:407`), nunca en estado guardado;
  - los nombres de los campos van con `plainReportText` (`:183`, `:331`), y el nombre de un wifi, con sus marcas (`:134`, `:141-145`);
  - la estimación de la fuerza tiene tope (`vault/FormHelpers.kt:167`), y el lector de las notas también (`LimitedMarkup`);
  - los textos `FORMAT_HELP`, `EXPIRY_ONLY_IN_APP` y `WIFI_WPA3_NOTE` dicen lo que hace el código (`vault/LimitedMarkup.kt:6`, `:66-73`).
- **`FOLDER_DELETE_TO_NONE`:** ver D-3. No es falso en sentido estricto; se cambia porque es inexacto y confunde.

## 4. Los riesgos con nombre

- **Filas 51, 62, 67 y 69:** no se reabren. Ninguno de los hallazgos les añade una prueba nueva:
  - D-1 es de la carpeta de las copias, no de la memoria;
  - D-2 a D-4 son de la interfaz.
- **Fila 58 (dos `EntryListViewModel`):** tampoco se reabre. Una nota del Ingeniero Móvil Senior, abajo, deja «a comprobar» si la navegación vieja libera sus `ViewModel` después de un cambio de bóveda. No es un riesgo de seguridad: el `ViewModel` viejo se vacía con el bloqueo de su bóveda.

## Lo que está bien

- **A6 por bóveda:** una instancia por bóveda, los borrados solo por id y de las listas propias, la generación comprobada antes de cada borrado, y los ajustes y los permisos por bóveda (§1.1).
- **La copia de la subida no la borra una petición de la 1.4.3** (§1.1). Cumple la condición de parada del encargo de CR-2.
- **`copyBeforeUpgrade`** copia el archivo que la 1.4.3 abre, nunca cierra la app y no escribe nada cuando la subida ya terminó (§1.2).
- **CR-2:** la espera va fuera del `lock`, siempre acaba (salvo D-1) y se aplaza sin saltarse nada. Las tres ventanas son benignas (§1.3).
- **La migración:** el coste no baja, la marca de A11 se conserva y los ajustes no se mezclan; los tests de la migración existen (§2).
- **Las pantallas:**
  - ningún `rememberSaveable` en los cuatro archivos;
  - los secretos, tapados y copiados como sensibles;
  - el cálculo de la lista, fuera del hilo principal y con su captura de OOM;
  - los diálogos se cierran con el bloqueo, salvo el caso de D-2;
  - los nombres, con `plainReportText` (§3).

## Revisión experto-seguridad-cripto

- **Modelo de amenaza.** Se repasaron una carpeta compartida hostil o rota, quien tiene el móvil desbloqueado un momento, la muerte del proceso, una bóveda contra otra y la subida desde la 1.4.3.
  - Lo único que pasa de lo informativo es D-1, de disponibilidad: CWE-1088, Baja, CVSS 2.5. No hay pérdida de confidencialidad ni de integridad: el archivo se queda en su v2 o v3 y se abre con la maestra (`vault/PendingUpgrade.kt:81-88`, `vault/AutoBackup.kt:749`).
- **Ninguna copia con otra maestra ni de otra bóveda:**
  - A6 borra por id y solo de las listas de su bóveda (`vault/AutoBackup.kt:1026-1047`, `:1040`, `:1048`);
  - una copia de una bóveda reemplazada no se apunta (`:847-848`; `settings/Settings.kt:369-372`);
  - la copia de la subida es el archivo v2 o v3 con la maestra de ahora (`vault/VaultStore.kt:1454-1475`).
- **Coste de la derivación:** la migración conserva el perfil (`settings/SettingsStore.kt:200`, `:241`), y volver a envolver nunca baja un eje (`vault/Vault.kt:603-607`, `:611-624`). Sin cambios en ninguna primitiva, longitud, AAD ni coste de Argon2id.
- **A11:** la marca solo sube, salvo el reinicio por la maestra de la propia bóveda (`AppContainer.kt:267-268`, `:282-291`, `:323-341`, `:373-375`). La migración la conserva (`settings/SettingsStore.kt:201`, `:242`).
- **Secretos en memoria:** la clave se pone a cero al bloquear (`vault/VaultStore.kt:816-821`), las maestras de «Bóvedas» se ponen a cero (`ui/screens/VaultPickerScreen.kt:338`, `:367`, `:407`), y nada de la bóveda queda en el `ViewModel` de la lista después de un bloqueo (`ui/screens/VaultList.kt:291`, `:294-300`, `:315`). D-2 es la única excepción a la regla de cerrar los diálogos al bloquear, y no tiene impacto.
- **Registros:** nada nuevo. `AutoBackup` solo da textos fijos (`vault/AutoBackup.kt:128-134`, `:1070-1076`).

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - una copia cada vez, con un `Mutex` por bóveda (`vault/AutoBackup.kt:448`);
  - la espera de CR-2 es un `StateFlow` que se puede cancelar, fuera del `lock` (`:485`, `:525`);
  - `afterUnlock` va en `NonCancellable` y tiene su `finally` (`vault/PendingUpgrade.kt:81-105`);
  - lo que falta es un límite de tiempo frente a la IPC de un proveedor (D-1). La corrección propuesta solo corta el `await` y no toca la copia, que sigue en su ámbito.
- **Ciclo de vida:**
  - `MainActivity.onStart` bloquea antes de entregar el resultado de un selector (`MainActivity.kt:57`). Por eso, en «Bóvedas», un diálogo que se abre desde ese resultado tiene que mirar el estado también al abrirse (D-2);
  - los diálogos de la lista y de organizar se cierran al bloquear (`ui/screens/VaultList.kt:694-733`; `ui/screens/VaultOrganize.kt:141`);
  - `key(vault)` rehace la navegación en cada cambio de bóveda (`ui/AppNavHost.kt:202-204`).
  - **A comprobar** (no es de seguridad): si la versión de navigation-compose del proyecto libera los `ViewModelStore` de las entradas de la navegación vieja cuando `key(vault)` la quita. Si no los libera, cada cambio deja un `EntryListViewModel` vacío (ya pasó por `reset()` con el bloqueo) que sigue escuchando su almacén hasta que termina la actividad. Se mide en el móvil, con la fila 58.
- **Rendimiento:**
  - el cálculo de la lista va fuera del hilo principal (`ui/screens/VaultList.kt:305-313`);
  - `folderTree` se recalcula en cada movimiento del arrastre (`:649`), pero es O(n log n) con 500 carpetas como mucho, y `organizeKey` solo se calcula con órdenes iguales (`vault/Folders.kt:111-133`). No lo cuento como hallazgo.
- **Estado:**
  - ningún `rememberSaveable` en los cuatro archivos;
  - en Compose, solo `remember` con ids o nombres;
  - los secretos del editor, en `EntryEditSession`;
  - `FilterObscuredTouches` en cada diálogo (por ejemplo, `ui/screens/VaultOrganize.kt:209`, `:264`, `:353`; `ui/screens/VaultPickerScreen.kt:376`, `:434`, `:455`, `:489`; `ui/screens/EntryFields.kt:377`).
- **Accesibilidad:** el color de una bóveda va siempre con su nombre (`ui/screens/VaultPickerScreen.kt:142`, `:281-283`).

## Textos para el Abogado Digital

- **`FOLDER_DELETE_TO_NONE`** (`vault/OrganizeTexts.kt:60`): «Sus entradas y sus subcarpetas se quedan sin carpeta».
  - No es falso en sentido estricto, pero confunde (D-3).
  - Propuesta: «Sus entradas pasan a «Sin carpeta» y sus subcarpetas, con lo que tienen dentro, arriba del todo».
- **`UPGRADE_COPYING`** (`vault/VaultStore.kt:1748-1749`): «… Espera a que termine e inténtalo otra vez.» Es verdad salvo con una carpeta que no contesta (D-1). Con la corrección de D-1, deja de salir pasado el tiempo de espera.
- **Texto nuevo, si se aplica D-1:** «La copia automática de antes del cambio aún no ha terminado: la carpeta no contesta. Puedes exportar una copia a otro sitio o seguir sin copia.»
- **`FOLDER_DELETE_NAME_TAKEN`** (`vault/Folders.kt:39-40`): verdadero (CR-4). Con D-4 deja de quedarse a la vista al elegir «a la papelera».
- **`VAULT_NEW_NEEDS_OPEN`** (`vault/VaultsTexts.kt:28`): verdadero. Es lo que dice hoy el caso de D-2 al confirmar, y lo que diría la corrección de D-2 al volver del selector.
- **Sin cambios, verificados contra el código:**
  - `VAULTS_SHORT`, `VAULTS_MORE`, `VAULT_NEW_SHORT`, `vaultDeleteWarning`, `VAULT_DELETED`, `VAULT_DELETE_EMPTY_WARNING`, `VAULT_DELETE_DAMAGED` y `VAULT_DELETE_LAST` (`vault/VaultsTexts.kt:10-77`);
  - `AUTO_BACKUP_REPLACED`, `AUTO_BACKUP_REPLACED_SEVERAL` y `autoBackupUnlistedText` (`vault/AutoBackup.kt:153-196`);
  - `FOLDER_DELETE_TO_TRASH` y `FOLDER_DELETE_TRASH_NOTE` (`vault/OrganizeTexts.kt:62-63`);
  - `MOVE_TO_TRASH_TEXT` (`vault/TrashTexts.kt:65`);
  - `movedSelected` y `taggedSelected` (`vault/ListTexts.kt:37-43`);
  - `FORMAT_HELP` y los textos de los campos (`ui/screens/EntryFields.kt:93-114`).
- **Dos matices ya tratados en otras filas:**
  - «Cada bóveda tiene su contraseña maestra» (`VAULTS_SHORT`) y «Importar como bóveda nueva» comparten la clave de datos (RT-23, una nota de corrección del diseño);
  - «se siguen abriendo con su contraseña maestra» (`vaultDeleteWarning`) quiere decir la maestra que tenía cada copia al hacerse.

  Los dos van con la fila 14.

Modelo: el director (esfuerzo extra).
