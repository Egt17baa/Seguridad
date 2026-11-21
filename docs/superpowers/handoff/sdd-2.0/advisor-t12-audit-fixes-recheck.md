# Director: repaso de la ronda de arreglos de la auditoría completa (RC-1 a RC-6, desviaciones y `pendientes.md`) (21-11-2026)

**Pregunta:** ¿se cierra, en lo que es mío, la ronda de arreglos de la auditoría completa (`7505281` … `a94455a`, rama `ccr-0aa27f95-u038cd`)? Son tres cosas:
1. RC-1 a RC-6, frente a lo que pedí en `advisor-t12-review-fixes-cierre.md`;
2. las desviaciones del encargo (`advisor-t12-audit-fixes.md`, líneas 138-378) que el implementador apunta en `audit-fixes-report.md` §5;
3. que `pendientes.md` (filas 81 a 104) refleje bien el estado.

**Qué se comprobó:**
- el diff de cada commit de lo mío (`c059ff4`, `0ae9409`, `2b3ee81`, `6fc26b7`, `e629906`, `3399f10`), y el de los commits de las desviaciones (`d9e341a`, `18adc17`, `539d2fd`, `b3825b4`, `9b42fec`, `ae488ea`, `7505281`), en el código y en `archivo:línea` de `a94455a` (HEAD);
- `git diff --stat 7505281^..a94455a` está vacío en `vault/Backup.kt`, `vault/Vault.kt`, `vault/VaultData.kt`, `crypto/`, `core/` y `gradle/libs.versions.toml`;
- sin Gradle (había una compilación con cerrojo). El controlador me pasa el clon limpio de `a94455a`: app 2 601 y jvm-check 2 536 tests, 0 fallos, 0 omitidos. Lo que digo del comportamiento sale de leer el código y los tests.

Veredicto: la ronda se cierra en lo mío. RC-3 a RC-6 quedan cerrados; RC-1 y RC-2, cerrados con un menor cada uno (un KDoc y dos rojos sobre entrada alterada que faltan). Acepto las seis desviaciones del §5. A `pendientes.md` le faltan cuatro retoques de documento. Son cuatro menores (M-1 a M-4), ninguno de comportamiento, y no piden otro repaso mío.

## Motivos

### 1. RC-1 a RC-6

| Punto | Veredicto | Evidencia |
|---|---|---|
| RC-1 | **Cerrado con menor (M-1)** | Ver abajo |
| RC-2 | **Cerrado** en el código; su tercer test, en M-2 | Ver abajo |
| RC-3 | **Cerrado** | Ver abajo |
| RC-4 | **Cerrado** | Ver abajo |
| RC-5 | **Cerrado** (queda el Abogado) | Ver abajo |
| RC-6 | **Cerrado** | Ver abajo |

**RC-1: el margen, dentro del objetivo del recorte.**
- **Hecho como lo pedí:**
  - `fittedWithSize(limit)` y `fitted(limit)` (`vault/VaultLimits.kt:153`, `:187`) tienen por defecto `VAULT_MAX_DATA_BYTES`;
  - el guardado no cambia, porque `fittedForSave` llama a `fittedWithSize()` sin argumento (`:211`) y `applyImport` a `fitted()` (`vault/VaultStore.kt:1086`);
  - `previewDataBytes` recorta a `VAULT_MAX_DATA_BYTES - PREVIEW_UNSEEN_BYTES` y suma el margen después (`imports/MergePlan.kt:434-435`);
  - `importSnapshot` lleva `d.copy(entries = open.trash)` (`VaultStore.kt:1028`), así que no se queda fuera ningún campo de `VaultData`, tampoco los que se añadan;
  - el KDoc de `VaultSnapshot` (`VaultStore.kt:1794-1799`) y el de `PREVIEW_UNSEEN_BYTES` (`MergePlan.kt:441-446`) dicen lo que cubre el margen ahora.
- **Las dos garantías se cumplen.**
  - Si el guardado da `TooBig` aun sin historial, la vista previa, que recorta más, también da más de 0.
  - Si la vista previa da 0, la bóveda recortada a 24 MiB − 1 KiB, más la contabilidad que cambia entre la instantánea y el guardado (como mucho el margen), cabe en 24 MiB.
- **Los tests, con el rojo que predije** (`VaultStoreImportTest.kt:314`, `:338`):
  - `thePreviewLetsInWhatTrimmingTheHistoryMakesFit` dio `expected:<0> but was:<494>`, entre 1 y 1024;
  - el de la instantánea fallaba por `vaultTag`, `lastCheckedBackupAt` y `templatesVersion`;
  - el cambio autorizado (`:420`) compara con los datos del almacén;
  - `RedTeam20ListCostTest` no se mueve (B 2 954 912 B, D 1,213x).
- **La sesión no guarda nada nuevo que sea secreto.** `rest` ya llevaba la papelera, con sus contraseñas. Ahora lleva además la marca de la bóveda y las horas, y se borra donde se borra `existing` (`imports/ImportSession.kt:252`, `:463`).
- **M-1, el KDoc de `estimatedOverBytes`:** no se puso al día con la instantánea entera.
  - `MergePlan.kt:415` enumera solo «the folders, views, colours and name».
  - `:418-419` dice que el margen es «for what the snapshot leaves out», cuando la instantánea ya no deja nada fuera. Contradice el KDoc nuevo de `:441-446` y el de `VaultSnapshot`.
  - `:430-432` afirma que «a save that fits by trimming has a preview of 0». No es verdad en una franja: una bóveda que ya no tiene historial que quitar y cuyo JSON queda a 1 KiB o menos por debajo de los 24 MiB da en la vista previa «sobran unos 1 KB» y se guarda igual.
  - Esa franja es del lado seguro y ya existía antes de la ronda (con o sin RC-1, el margen se suma a una bóveda sin historial). No es una regresión, pero el KDoc tiene que decir la garantía de verdad, la de un solo sentido.

**RC-2: una pasada que superó la espera no copia mientras dura la copia de la subida.**
- La línea es la que pedí (`vault/AutoBackup.kt:835`): `defer` lo pone en `pending` (`:936-938`), y lo hace el `runDue` del desbloqueo, que espera (`:525`). Se aplaza, no se salta.
- `copyBeforeAny` no pasa por `attempt` (`:749-760`).
- `aPassThatWaitedForTheLockDefersItsCopyUntilTheUpgradeCopy` (`UpgradeCopyFirstTest.kt:330`) y el de E-2 (`:380`) tienen el rojo esperado. Después de guardar la v4 llega la copia automática (la última aserción del primero).
- `theWaitEndsWhenTheUnlockCallerIsCancelled` (`:405`) fija lo que ya se cumplía, pero sin su rojo sobre una entrada alterada: va en M-2.
- **Informativo, sin cambio.** `replaceNow` (A6 con el diálogo, `AutoBackup.kt:668`) también pasa por `attempt`. Con `upgradeCopying` en verdadero diría `failedFor` y dejaría el destino en `pending`, pero no se alcanza:
  - `copying` solo es verdadero para una v2 o v3 recién abierta (`vault/PendingUpgrade.kt:68`);
  - una v4 nunca lo tiene;
  - el diálogo de A6 solo sale tras cambiar la maestra de esa bóveda, y cambiarla se rechaza mientras espera la subida (`VaultStore.kt:1417`).

**RC-3:** el ancla de fuente (`UpgradeCopyFirstTest.kt:428`) hace lo que pedí:
- busca exactamente dos `store.upgradeCopying.first { !it }` sin contar los comentarios, cada una con `lock.withLock {` en la línea siguiente;
- sus funciones son `runChanged` y `runDue` (`AutoBackup.kt:485`, `:525`), y ninguna está en `copyBeforeUpgrade` (`:749`);
- quitando `:485`, el rojo es `two waits: [234] expected:<2> but was:<1>`.

**RC-4:**
- el ancla de `store.changeMaster(` … `storeSaveErrorText(e, fallback = newPasswordFailureMessage(` (`ErrorTextsTest.kt:153-154`) cubre `ui/screens/VaultBackup.kt:168`;
- `checked >= 19` (`ErrorTextsTest.kt:148`) es la cifra que el informe midió con `>= 999`;
- el rojo se vio quitando la llamada.

**RC-5:**
- `FOLDER_DELETE_TO_NONE` (`vault/OrganizeTexts.kt:63`) se construye con `FOLDER_NONE` y `FOLDER_TOP` (`:49-50`), y se lee «Sus entradas pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»»;
- `FolderDeletionTextTest` lo fija palabra por palabra y con `ForbiddenWords`, y corre en jvm-check (`jvm-check/build.gradle.kts:20` incluye `app/src/test/java`, y solo excluye `**/ui/**`);
- está en el inventario para el Abogado.

**RC-6:**
- el KDoc de `MasterPrompt` (`ui/screens/ContactsScreen.kt:130-136`) dice lo que pedí: es una defensa que la 2.0 no alcanza, y si se alcanzara, `confirmMaster` (`VaultStore.kt:773-776`) solo comprobaría la maestra y no pasaría la bóveda a la 3;
- el de `ContactsFrame` (`:78-84`) y el de `deleteVault` (`AppContainer.kt:705-720`, E-3) también son verdaderos;
- `theMasterGateIsNeverReachedWithAnOpenVault` (`ContactsViewTest.kt:634`), con la v2 real `vault-1.1.json`, recorre los cuatro flujos con y sin copia, y tiene su rojo sobre `contactsGate` alterado (`vault/ContactsView.kt:60-65`).

### 2. Las desviaciones del encargo (§5 del informe)

**(a) El punto 3 en tres commits (`d9e341a`, `18adc17`, `539d2fd`): aceptada.**
- A-1 y B-3 son un solo arreglo en mi Ruling («Ronda, punto 3»): el orden y `forget` tocan las mismas líneas. Partirlo en dos commits habría sido artificial.
- **`18adc17`** es el cambio de firma autorizado («`forget`, solo esa firma»): `VaultRegistryTest.kt:185` pasa de `disable` a `forget`.
  - Queda una mancha en el historial: `d9e341a` deja ese test en rojo, porque no corrió `vault.*` aunque tocó `SeguridadApp.kt`.
  - Está dicho en el mensaje de `18adc17`. No se arregla, porque sin `--amend` ni *squash* no hay forma, y la rama entra entera.
  - Para la próxima vez: un cambio en `SeguridadApp.kt` corre también `vault.VaultRegistryTest`.
- **`539d2fd`:**
  - `edit(commit = true) { … }` es la extensión de `androidx.core.content` (`android/BiometricVault.kt:15`), una dependencia que ya existía (`app/build.gradle.kts:95`);
  - escribe en el acto y deja sin mirar el booleano de `commit()`, como la forma anterior;
  - el ancla (`PlatformSourcesTest.deletingAVaultDeletesItsFingerprintPreferences`) mira la forma nueva y el orden frente a `deleteSharedPreferences` (`BiometricVault.kt:197-203`);
  - `lintDebug` vuelve a los 21 avisos de antes.

**(b) D-1 con una bandera, en lugar de `withTimeoutOrNull { copyBefore() } ?: Failed(…)`: aceptada. Es mejor que mi fórmula, que tenía un fallo.**
- `copyBeforeUpgrade` da `null` cuando no hay carpeta (`AutoBackup.kt:752-760`), y el `beforeUpgrade` por defecto del almacén también da `null` (`VaultStore.kt:229`).
- Con mi fórmula, ese `null` habría pasado a «no ha terminado en 2 minutos», con o sin carpeta.
- La bandera (`PendingUpgrade.kt:100-106`, `:115`) los distingue. El `Failed` del almacén lleva `AUTO_BACKUP_FAILED`, porque `vault/` no puede nombrar un texto de `ui/`, y el diálogo elige el texto (`ui/screens/VaultUpgrade.kt:132-133`).
- Los dos tests miran justo antes y justo después del tope, con tiempo virtual (`UpgradeCopyFirstTest.kt:446`, `:488`). La copia tardía:
  - queda en las de antes de importar;
  - no se anuncia como la de la subida;
  - A6 no la borra.
- **Informativo, sin cambio.** kotlinx.coroutines documenta que el tiempo de `withTimeoutOrNull` corre aparte del bloque: un valor que llega justo al tope se puede perder.
  - Con la bandera, el resultado es un diálogo sin la línea de la copia (se toma como `null`) o «no ha terminado» para una copia que acaba de terminar.
  - Los dos casos dejan la elección con un texto que no es falso, y la copia queda apuntada.
  - Si alguien vuelve a tocar el bloque, que guarde el resultado en una variable dentro del bloque y decida por ella, no por lo que devuelve.

**(c) El rojo de RC-2, con una petición A6 guardada durante la copia retenida: aceptada.**
- Mi lista de pasos del cierre estaba incompleta: el `runDue` de `launchDue` no tenía nada que copiar, así que el test salía verde sin el arreglo. Es un error mío, no del implementador.
- La petición A6 es un estado real: `autoBackupReplacePending` existe desde la 1.3 y sigue hasta borrar todos los destinos.
- Además, `attempt` es el único punto por el que pasa la copia (`AutoBackup.kt:497`, `:546`), así que el motivo de la pasada (A6, calendario, `pending` o un cambio perdido) no cambia lo que se prueba.
- El rojo es el que predije: la copia automática va antes que la de la subida.

**(d) Los tests verdes desde el principio, con su rojo sobre una entrada alterada: aceptada en cuatro, y en dos falta el rojo (M-2).**
- **Con su rojo alterado en el informe:**
  - `ContactsViewTest.theMasterGateIsNeverReachedWithAnOpenVault`;
  - `BackupV3Test.aCopyOfAV4IsRefusedByThe143Reader` (`:206`);
  - C-1 (un documento temporal);
  - C-7 (nueve alteraciones del YAML).
- **Sin él:**
  - `UpgradeCopyFirstTest.theWaitEndsWhenTheUnlockCallerIsCancelled` (`:405`). Mi encargo lo nombra en la regla de TDD («la segunda parte de RC-2», `advisor-t12-audit-fixes.md:163`).
  - `PasswordMarkTest.aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted` (`:165`), que cae en la regla general de que cada test nuevo enseña un rojo.
- **El razonamiento del §5 sobre el segundo es correcto:** `checkCopyEntries` y la importación no aplican el tope de una entrada a lo que no cambia, así que ya aceptaban la copia. Aun así, hay que ver que el test puede fallar.
- **Una debilidad del primero:** su aserción «the copy ended» (`UpgradeCopyFirstTest.kt:421`) también acepta el documento `….tmp`. Los dos tests de D-1 lo excluyen (`:476`, `:527`).

**(e) El cambio de `VaultPickerDialogsTest.theScreenClosesItOnTheVaultsState`: aceptada.**
- Es un ancla de fuente en `ui/screens`, fuera de los paquetes cuyo rojo por comportamiento era una parada. Además, es la consecuencia directa de lo que pedí (añadir `dialog` a las claves).
- El `(?:, dialog)?` opcional de `:35` no debilita nada, porque `aDialogOpenedAfterTheLockClosesToo` (`:46`) exige la clave sin opción.

**(f) La línea de B-2 en el README (`README.md:416` y `:117`): aceptada.**
- «Entorno de firma» (`README.md:212-240`) no compara nada con la 1.4.3. El sitio del encargo estaba mal, y era mío.
- Las dos líneas están donde el README hace esa comparación (la prueba «2.0, versión y permisos») y donde describe el script.
- **Falta que lo sigan los documentos que citan el sitio viejo:** `docs/auditorias/2026-11-21-auditoria-2.0.md:261`, `:392` y `:495`, que dicen «README («Entorno de firma»)». Si no se cambian, el repaso de documentos añadiría una tercera línea donde no toca. Va en M-3.

### 3. `pendientes.md`, filas 81 a 104 (`pendientes.md:89-112`)

- **Bien:**
  - la 81 tiene los seis commits de RC-1 a RC-6;
  - la 82 está cerrada por E-1 (`ae488ea`);
  - la 84 está hecha, con el riesgo de lo que ya existe en la 104;
  - las 85 a 100 tienen una fila por hallazgo, con su commit y su destino;
  - las 101 a 103 son los riesgos con nombre C-6, C-2 y C-3, con quien los aceptó;
  - la 104 es el riesgo de las bóvedas de la 1.4.3 con más de 1 998 adjuntos.
- **Faltan cuatro retoques (M-3):**
  1. **Fila 87 (`:95`):** cita `d9e341a` y `18adc17`, pero no `539d2fd`, y dice «con `commit()`»; hoy es `edit(commit = true)`.
  2. **Fila 83 (`:91`):**
     - dice «un `Error` durante la copia de la subida», pero no es ahí: un `Error` durante la copia llega al `finally` de `afterUnlock` (`PendingUpgrade.kt:122`) y deja `copying` en falso;
     - la ventana de mi cierre va de `openedWith` al `try` de `afterUnlock` (`tellCostKept` y `collectGarbage`);
     - le falta también AC-E1, la variante que mi Ruling mandó con ella al móvil: un `OutOfMemoryError` al guardar la subida, porque `completeUpgradeLocked` solo captura `Exception` (`VaultStore.kt:672-679`). La auditoría lo dice (`2026-11-21-auditoria-2.0.md:322`, `:462`), pero la fila 51 (`:59`) no.
  3. **Fila 12 (`:18`):** sigue diciendo «oferta al volver de otra app», que B-4 dejó falso. Ahora es «la oferta en pantalla dividida o en una ventana flotante; a pantalla completa no sale, porque salir bloquea».
  4. **Las pruebas en el móvil de la ronda** solo están dentro de filas «Hecho» (85, 86, 89, 92 y 95), y la fila 84 no dice la suya. Según la cabecera del archivo, lo del móvil es lo único que puede quedar «Abierto», así que necesitan una fila «Móvil» abierta: la 106 de abajo.
- **De paso, fuera del rango:** la fila 80 (`:88`) sigue «Abierto». Su cierre ya está previsto en el repaso de documentos (`2026-11-21-auditoria-2.0.md:392`).

### 4. El conjunto: una línea de A-1 que contradice a las otras dos

- `AppContainer.kt:790`, en el `catch` de `forgetOrphanVaults`, dice «a key that opens nothing on the disk».
- Es la misma afirmación que A-1 corrigió en `:143-144` y en `:757-758`: la clave envuelve la clave de datos, que también abre las copias hechas antes, y esas copias pueden estar en el almacenamiento del móvil.
- Es un comentario y no cambia nada del comportamiento. Lo pongo aquí para que no se cuente dos veces con el repaso de la parte A (M-4).

## Arreglos (menores; ninguno de comportamiento)

**M-1 (RC-1, KDoc): `estimatedOverBytes` y `previewDataBytes` dicen la garantía de verdad**
- `imports/MergePlan.kt:415`: la bóveda entera, como en `VaultSnapshot`, no una lista de campos.
- `:418-419`: «Each allows [PREVIEW_UNSEEN_BYTES] for what the accounting may change between the snapshot and the save».
- `:430-432`: «a preview of 0 always saves, and a save refused even without any history has a preview over 0; a vault with no history left within the margin under the cap is told it would not fit, on the safe side».
- Sin cambio de código ni de tests.

**M-2 (tests verdes desde el principio): sus dos rojos, y una aserción más estricta**
- Una ejecución con `--tests` de cada uno, sobre una alteración que se deshace después, con el mensaje copiado en `audit-fixes-report.md` §1:
  - `theWaitEndsWhenTheUnlockCallerIsCancelled`: por ejemplo, `copyBeforeUpgrade` sin `scope.async` y `afterUnlock` sin `withContext(NonCancellable)`, de modo que la copia muera con quien llamó. Con solo una de las dos el test sigue en verde, porque las dos defensas se cubren entre sí, y eso también se apunta;
  - `aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted`: por ejemplo, `checkCopyEntries` (`vault/BackupCheck.kt:256`) con el tope de una entrada medido con la marca, como antes de E-1.
- `UpgradeCopyFirstTest.kt:421`: `it.startsWith("seguridad-antes-de-2-0-") && !it.endsWith(".tmp")`, como en `:476` y `:527`.
- De paso, la línea en blanco que falta antes del KDoc de los tests nuevos: `UpgradeCopyFirstTest.kt:323`, `:423`, `:440`; `ContactsViewTest.kt:627`; `PasswordMarkTest.kt:131`. Es solo estilo.

**M-3 (documentos): la línea de B-2 y `pendientes.md`**
- `docs/auditorias/2026-11-21-auditoria-2.0.md:261`, `:392` y `:495`: «README («Pruebas de la 2.0», `README.md:416`, y la descripción de `check-manifest.sh`, `:117`)», en lugar de «Entorno de firma».
- `pendientes.md`: las filas 87, 83, 51 y 12, como dice §3, y las filas nuevas de abajo.

**M-4 (el conjunto, comentario):**
- `AppContainer.kt:790`: «The next start does not see it again once the settings are gone: a key left in the Keystore that wraps this vault's data key, which also opens the copies made before (audit A-1).»
- Es la misma verdad que `:143-144` y `:757-758`.

**Cómo van:**
- M-1 y M-4 son comentarios en `app/src/main`, y M-2 toca un test. Van en un solo commit antes del último clon limpio (el de la puerta del APK), así que no piden otro.
- M-3 va al repaso final de documentos.
- No piden otro repaso mío: los miro en la puerta del APK.

## Ruling

- **RC-1 a RC-6:** cerrados; RC-1 con M-1 y RC-2 con M-2 (sus rojos).
- **Las seis desviaciones del §5:** aceptadas.
  - La (b) corrige un fallo de mi fórmula, y la (c) y la (f) corrigen dos errores de mi encargo.
  - La (d) queda abierta solo en lo que pide M-2.
- **`pendientes.md`:** refleja bien el estado de la ronda, salvo los retoques de M-3.
- **M-4:** es del conjunto, y lo compartimos con el repaso de la parte A.
- **Sigue el plan:** los demás repasos, el clon limpio de las dos suites con `check-manifest.sh` y `aapt2 dump xmltree` frente a la 1.4.3, el repaso final de documentos, el Abogado, la fecha de la política y la puerta del APK.

## Filas nuevas de `pendientes.md`

| # | Qué | Destino | Estado |
|---|---|---|---|
| 105 | M-1 a M-4 del repaso del director de la ronda de la auditoría (`advisor-t12-audit-fixes-recheck.md`): el KDoc de la vista previa, los dos rojos sobre entrada alterada y la aserción del `.tmp`, la línea de B-2 en la auditoría y `pendientes.md`, y el comentario de `forgetOrphanVaults` | M-1, M-2 y M-4: un commit antes del último clon limpio; M-3: repaso final de documentos | Abierto |
| 106 | Móvil: las pruebas de la ronda de la auditoría (§10): B-2 (otra app no mete su pantalla en la tarea de Clavero), B-1, B-4 en pantalla dividida y en ventana flotante, D-1 con una carpeta lenta (el diálogo a los 2 minutos), D-2 (2 min y medio en el selector) y «Añadir adjunto» cerca de 1 998 adjuntos (fila 84) | Tarea 11 y dueño | Abierto |

## Riesgos o dudas

- **Sin ejecutar nada:** las garantías de RC-1, el orden de RC-2 y lo que no se alcanza en `replaceNow` salen de leer el código. Los rojos los vio el implementador y el clon limpio está en verde.
- **La alteración de M-2 para `theWaitEnds…`:**
  - la deduzco de que hay dos defensas, `scope.async` en `AutoBackup.kt:749` y `NonCancellable` en `PendingUpgrade.kt:96`;
  - si con las dos quitadas sigue en verde, el informe lo dice y el test se queda como está;
  - no pide otro cambio.
- **La pérdida de un valor justo al tope de `withTimeoutOrNull`** la doy por la documentación de kotlinx.coroutines, sin medirla. Su efecto es solo de texto, y del lado neutro.
- **Los repasos de las partes A a E** van a la vez que este. Si el de la parte A ve M-4, se cuenta una vez.

## Revisión experto-seguridad-cripto

- **Formatos y primitivas:**
  - `git diff --stat 7505281^..a94455a` está vacío en `vault/Backup.kt`, `vault/Vault.kt`, `vault/VaultData.kt`, `crypto/`, `core/` y `gradle/libs.versions.toml`;
  - no cambian `SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, el AAD, las longitudes ni Argon2id;
  - RC-1 solo cambia lo que se mide en la vista previa (`MergePlan.kt:434-435`), y lo que se guarda sigue igual (`VaultLimits.kt:187`, `:211`; `VaultStore.kt:1086`).
- **RC-1, la integridad de la decisión (CWE-682):**
  - las dos garantías se cumplen (§1), y la franja que queda es del lado seguro: se rechaza de más, nunca se guarda de menos;
  - la instantánea entera no expone nada nuevo: `rest` ya llevaba la papelera, y la sesión lo borra donde borra `existing` (`ImportSession.kt:252`, `:463`).
- **RC-2 y E-2 (CWE-362, sin efecto de seguridad):**
  - se aplaza, no se salta (`AutoBackup.kt:835`, `:936-938`), sin cerrojos nuevos;
  - A6 no amplía lo que borra: la copia de la subida queda en las de antes de importar, y `replaceTargets` está congelada (`UpgradeCopyFirstTest.kt:446`, `:488`, las aserciones de A6);
  - `replaceNow` (`:668`) no se alcanza con `copying` en verdadero (`PendingUpgrade.kt:68`, `VaultStore.kt:1417`).
- **D-1 (disponibilidad, CWE-400):**
  - la espera está acotada (`PendingUpgrade.kt:14`, `:102`) y la copia sigue en el ámbito de `AutoBackup` (`AutoBackup.kt:749`);
  - la bandera (`PendingUpgrade.kt:100-106`) evita un texto falso cuando no hay carpeta.
- **Veracidad (CWE-1116):**
  - RC-6 y E-3 dicen lo que hace el código (`ContactsScreen.kt:78-84`, `:130-136`; `AppContainer.kt:705-720`; `ContactsView.kt:60-65`; `VaultStore.kt:773-776`);
  - quedan dos comentarios inexactos, M-1 (`MergePlan.kt:415-432`) y M-4 (`AppContainer.kt:790`). El de M-4 quita importancia a una clave que queda en el Keystore (contexto de CWE-459).
- **Secretos y registros:** ningún `Log`, `println` ni `rememberSaveable` nuevo en `app/src/main` (`git diff 7505281^..a94455a`). Los textos nuevos son constantes.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - RC-2 decide dentro del `lock`, en el sitio por donde pasa cada destino (`AutoBackup.kt:835`), y no añade ninguna espera;
  - la espera de RC-3 sigue siendo un `StateFlow.first {}` que se puede cancelar, fuera del `lock` (`:485`, `:525`);
  - D-1 usa `withTimeoutOrNull` dentro del `NonCancellable` de `afterUnlock` (`PendingUpgrade.kt:96-106`), y solo se corta el `await`;
  - el `finally` (`:122`) apaga `copying` también ante un `Error` durante la copia, y por eso la fila 83 tiene que decir otra ventana (M-3).
- **Ciclo de vida:**
  - `forget` va en la fila de la huella (`KeyOp.DISABLE`) y con `NonCancellable` (`BiometricVault.kt:193-206`);
  - `edit(commit = true)` escribe en el acto y `deleteSharedPreferences` borra también la caché del proceso (API 24, con `minSdk` 26);
  - el diálogo de D-2 se vuelve a mirar al abrirse (`VaultPickerScreen.kt:202`).
- **Rendimiento:**
  - RC-1 no añade pasadas: es el mismo `fitted`, con otro objetivo, en `Dispatchers.Default` y cancelable (`ui/screens/ImportScreen.kt:208`, `collectLatest`);
  - el test cerca de los 24 MiB tarda unos 4,5 s;
  - `RedTeam20ListCostTest` no se mueve.
- **Tests:**
  - son deterministas, con tiempo virtual, `CompletableDeferred` y `withTimeout`, sin `sleep` (ningún `Thread.sleep` nuevo en el diff de `app/src/test`);
  - las anclas de fuente de RC-3 y RC-4 quitan los comentarios antes de contar;
  - lo que falta es M-2.
- **Compilación:**
  - `androidx.core.content.edit` viene de `core-ktx`, que ya estaba (`app/build.gradle.kts:95`);
  - las entradas nuevas de las tareas `test` (`../docs` y la tabla de la skill) hacen que un cambio de un documento vuelva a correr los tests. En CI no cuesta nada; en local, la suite entera vuelve a correr.

## Textos para el Abogado Digital (revisión final)

- Ningún texto nuevo de este repaso. M-1 y M-4 son comentarios del código, y M-3 son documentos técnicos.
- Siguen para él los de la ronda (`audit-fixes-report.md` §6 y `task-10c-inventory.md`, «Ronda de arreglos de la auditoría completa»). Entre ellos, `FOLDER_DELETE_TO_NONE` (RC-5): se puede retocar, siempre que nombre los dos sitios y no diga que las subcarpetas desaparecen.

Modelo: el director (esfuerzo extra).
