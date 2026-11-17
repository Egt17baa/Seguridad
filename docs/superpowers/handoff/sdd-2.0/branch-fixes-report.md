# Clavero 2.0, revisión de la rama: ronda de arreglos (informe del implementador)

Rama `ccr-0aa27f95-u038cd`, desde `7e1860b`. Encargo: `docs/superpowers/handoff/sdd-2.0/advisor-t12-branch-fixes.md`, «Encargo de la ronda». Sin *push*. Solo `app/src/main` y `app/src/test`.

## Grupo 1. Fila 17 (B I-1)

- **Commit:** `035752d`.
- **Archivos:** `vault/A6Text.kt` (`inRequest` nuevo, :35-47; `a6Summary`, `stale`, :56-61; `a6Snapshot` usa `inRequest`); `test/.../vault/AutoBackupTargetsTest.kt`.
- `requestId` no se toca (lo siguen usando `AutoBackup.kt:494`, `:541` y `Settings.kt:401`). `inRequest` solo lo usa `a6Snapshot`, como dice el Ruling.
- **Tests:** P-17a `everyCombinationOfFoldersWithARequestOf143` (52 combinaciones: la carpeta de la 1.4.3 en 4 destinos × 0 a 2 carpetas añadidas en la 2.0, cada una en 3 estados); P-17b dentro de `everyCombinationOfFolders` (marca m1 frente a m2); P-17c `theStaleTextSaysWhere`; P-17d `aRequestOf143FinishedAfterAnotherChangeSaysSo` y P-17e `aFolderAddedAfterAClosedRequestOf143SaysNothing`, desde `settings-1.4.3.json` con la petición añadida en el test (el recurso no cambia); P-17f `whoIsInARequest`; `aRequestOf143IsTheRequestOfNow` espera `a6StaleText(1, 1)`.
- **Rojo antes del arreglo** (con `inRequest` ya introducido con el significado de hoy, para que compile): 51 tests, 5 fallan:
  ```
  aRequestOf143FinishedAfterAnotherChangeSaysSo :: ComparisonFailure: expected:<Se [terminaron de borrar las copias que la app tenía apuntadas de antes de un cambio de contraseña anterior, en la carpeta. …
  aFolderAddedAfterAClosedRequestOf143SaysNothing :: expected null, but was:<A6Decision(failures=[], summary=A6Summary(request=1.x, folders=2, replaced=1, deleted=1, unlisted=0, stale=false), snapshot=1.x …=hecha:1:0 content://dos=sin)>
  everyCombinationOfFoldersWithARequestOf143 :: AssertionError: REPLACED []
  whoIsInARequest :: AssertionError
  aRequestOf143IsTheRequestOfNow :: ComparisonFailure: expected:<Se [terminaron de borrar …
  ```
  P-17b y P-17c pasaban ya (guardas de lo que funciona).
- **Verde:** `app.seguridad.vault.*`, `app.seguridad.settings.*` y `*VaultsTest`: 931 tests, 0 fallos.
- **Cambio de comportamiento (aceptado en el Ruling, duda 1 opción a):** una petición pendiente de la 1.4.3 termina con el texto prudente (`a6StaleText`), no con `AUTO_BACKUP_REPLACED`. Ningún texto cambia de constante.

## Grupo 2. Fila 18 (B m-1) y los tres KDoc

- **Commit:** `4f0d71b`.
- **Archivos:** `vault/A6Text.kt` (`a6Decide` y `a6SnapshotChanged` nuevo, con paréntesis explícitos; KDoc de `a6Decide` sin la promesa de «nothing is ever kept silent…», KDoc de `a6Text` con `stale`); `vault/VaultStore.kt` (el KDoc de `changeMark` vuelve encima de su función, tras `masterMark`).
- **Test:** P-18 `aFolderDoneAndRemovedAfterClosingSaysNothing` (dos carpetas hechas, cerrado, se quita una hecha: nada; una que esperaba y se va: habla; otra cabecera: habla). Siguen en verde `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers`, `aFolderThatComesBackReopensTheRequest` y el exhaustivo.
- **Rojo:** `aFolderDoneAndRemovedAfterClosingSaysNothing :: AssertionError: expected null, but was:<A6Decision(failures=[], summary=A6Summary(request=R, folders=1, replaced=1, …), snapshot=R content://uno=hecha:1:0)>`.
- **Verde:** `vault.*` y `settings.*`, 0 fallos.

## Grupo 3. C-1, fila 37 (B I-4) y D-1

### C-1 (`e4f6aa0`)
- `vault/Attachments.kt`: `ATTACHMENTS_TOTAL_WOULD_PASS` (texto nuevo, el del Ruling palabra por palabra) y `attachmentsFit(data, adding)`, pura, sobre `data.entries` (papelera incluida).
- `vault/VaultStore.kt` `addAttachment`: pasa a `modifyData`; busca la entrada entre `data.live`, comprueba `attachmentsFit` y cambia la entrada en su sitio dentro de `data.entries`. Sin migración.
- **Tests** (`AttachmentsTest`): P-C1a `attachmentsFitCountsTheTrash`; P-C1b `addAttachmentCountsEveryEntry` (de fuente: ni `modify(` ni `totalBytes(` en `addAttachment`); P-C1c `whatTheTrashLeavesAlwaysPassesTheCopyCheck` (25 MB vivos + 25 MB en la papelera; tras cada paso, `checkCopyEntries(data.entries)` no lanza; añadir 1 byte se rechaza hasta vaciar la papelera; restaurar no pasa del tope). `total50MbEnforcedByStore` y `total50MbCheckedBeforeWritingTheFile` esperan el texto nuevo.
- **Rojo:** `whatTheTrashLeavesAlwaysPassesTheCopyCheck :: Debía rechazarlo: la papelera cuenta`; `addAttachmentCountsEveryEntry :: AssertionError: suspend fun addAttachment(…`; los dos `total50Mb*` con `expected:<[Con este adjunto, …]> but was:<[Los adjuntos de la bóveda pasan de 50 MB en total]>`. P-C1a pasaba ya (la función pura se escribió con su test).

### Fila 37 (`af15de2`)
- `vault/BackupCheck.kt` `checkCopyEntries`: dos líneas; un tamaño fuera de `0..MAX_ATTACHMENT_BYTES` da `COPY_DAMAGED`; la suma, `ATTACHMENTS_TOTAL_TOO_BIG` (al añadir, el texto nuevo de C-1).
- **Tests:** P-I4 `BackupCheckTest.anAttachmentSizeOutOfRangeIsDamaged` (−1 y 5 MB + 1: dañada; 11 × 5 MB: total; 10 × 5 MB: pasa); `VaultStoreTest` (el caso de tamaño −1) espera `COPY_DAMAGED`.
- **Rojo:** `anAttachmentSizeOutOfRangeIsDamaged :: expected:<L[a copia está dañada…]> but was:<L[os adjuntos de la bóveda pasan de 50 MB en total]>` y el mismo en `a copy that breaks what new entries keep is refused and nothing changes`.

### D-1 (`ff14e1d`)
- `vault/BackupCheck.kt` `checkCopyEntries`: cuenta solo las identidades con `deletedAt == null`.
- **Test:** `BackupCheckTest.anIdentityInTheTrashIsNotASecondOne` (una viva y otra en la papelera: pasa; dos en la papelera: pasa; dos vivas: `SEVERAL_IDENTITIES`).
- **Rojo:** `anIdentityInTheTrashIsNotASecondOne :: expected null, but was:<La copia tiene más de una identidad: no se puede importar>`.

### Suites tras el grupo 3 (sobre `ff14e1d`)
- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, **2381 tests, 0 fallos**.
- `./gradlew --stop`; `-p jvm-check test`: BUILD SUCCESSFUL, **2337 tests, 0 fallos**.

### Textos que cambian (grupo 3)
- Nueva `ATTACHMENTS_TOTAL_WOULD_PASS` (`Attachments.kt`): «Con este adjunto, los adjuntos de la bóveda (también los de la papelera) pasarían de 50 MB. Quita algún adjunto o vacía la papelera para añadir otro.» Sustituye, al añadir, a `ATTACHMENTS_TOTAL_TOO_BIG` («Los adjuntos de la bóveda pasan de 50 MB en total»), que queda solo para una copia.
- Una copia con un adjunto de tamaño imposible da ahora `COPY_DAMAGED` en lugar de `ATTACHMENTS_TOTAL_TOO_BIG`.

## Grupo 4. A I-1 (el diálogo de A6 y el cambio de bóveda)

- **Commit:** `353a148`.
- **Archivos:** `AppContainer.kt` (`masterChangedLock`; `onMasterChanged(vaultId, …)` comprueba `vaultId == activeId` y abre dentro del cerrojo; `switchTo` pone `activeId` y cierra el diálogo dentro del mismo cerrojo, y si estaba abierto con `requestedAt == 0` publica el aviso); `ui/screens/VaultBackup.kt` (`LocalOnMasterChanged` con la bóveda; pasa `store.id`); `AppNavHost.kt` sin cambio de código (`container::onMasterChanged` toma la firma nueva); `vault/VaultsTexts.kt` (`MASTER_CHANGED_IN_OTHER_VAULT`, `MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT`, `masterChangedInOtherVaultText`).
- **Tests** (`AppContainerVaultsTest`): `changeOfMasterThenSwitchNeverOpensA6ForTheOtherVault` (n.º 1 del revisor: la derivación de A detenida en el hilo de cálculo, `switchTo(B)` en paralelo; el diálogo no se abre, `replace()` da `false`, los ajustes de B no cambian y el aviso, con la línea de la huella, está en `vaultsNotice`); `aSwitchThatClosesTheDialogUnaskedSaysSo` (cerrar sin pedir nada lo dice; cerrado por el usuario, nada).
- **Rojo** (con la firma nueva ya puesta y el parámetro sin usar): `changeOfMasterThenSwitchNeverOpensA6ForTheOtherVault :: the dialog of A6 never opens on B`; `aSwitchThatClosesTheDialogUnaskedSaysSo :: expected:<La contraseña maestra de la otra bóveda se cambió. …> but was:<null>`.
- **Verde:** todo `:app:testDebugUnitTest`, 2383 tests, 0 fallos.
- **Textos nuevos** (Ruling palabra por palabra): `MASTER_CHANGED_IN_OTHER_VAULT` «La contraseña maestra de la otra bóveda se cambió. Al cambiar de bóveda, el aviso que ofrece borrar sus copias anteriores no se llegó a mostrar o se cerró: esas copias siguen abriéndose con la contraseña anterior. Para borrarlas, vuelve a esa bóveda y cambia otra vez la contraseña maestra, o bórralas a mano.»; con `fingerprintFailed`, en otra línea, `MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT` «En esa bóveda la huella ya no la abre, pero no se pudo desactivar: ábrela con la contraseña nueva, desactiva la huella y vuelve a activarla.»

## Grupo 5. Fila 38 (A I-3) y fila 45 (A I-2)

### Fila 38 (`2a03ae6`)
- `crypto/Crypto.kt`: `outOfMemoryAsFailure(p, block)` recibe el coste y da `kdfOutOfMemoryText(p)`: por encima de `DEFAULT_KDF_MEMORY_KIB`, `KDF_OUT_OF_MEMORY`; con 64 MiB o menos, `KDF_NO_MEMORY_NOW` (nueva). `argon2id` le pasa `p`. `cannotFit` sigue con `KDF_OUT_OF_MEMORY` (siempre por encima de 64 MiB). `newPasswordFailureMessage` deja pasar las dos. `vault/Vault.kt` `masterUnlockErrorText` también. `copyFailureText` (fila 42) las deja pasar (lo demás «igual»).
- **Test** n.º 3: `CryptoTest.outOfMemoryTextFollowsTheCost` (textos exactos, 1 MiB y 64 MiB → `KDF_NO_MEMORY_NOW`; 64 MiB + 1 KiB y 128 MiB → `KDF_OUT_OF_MEMORY`; las dos listas; ninguna dice «otras apps»). `SettingsTest.runningOutOfMemoryInArgon2IsAClearFailure` pasa el coste «Alto».
- **Rojo:** `outOfMemoryTextFollowsTheCost :: ComparisonFailure: expected:<[No hay memoria suficiente para este coste de Argon2id, más alto que «Estándar». …]> but was:<[Este móvil no tiene memoria suficiente para el coste …`.
- **Textos:** `KDF_OUT_OF_MEMORY`: antes «Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id» → ahora «No hay memoria suficiente para este coste de Argon2id, más alto que «Estándar». Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede con ese coste.» Nueva `KDF_NO_MEMORY_NOW`: «Ahora no hay memoria suficiente para esta operación. Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez.» Ningún test ata un documento a `KDF_OUT_OF_MEMORY` (solo lo citan documentos de traspaso viejos, de la 1.4.0).

### Fila 45 (`3024ba3`)
- `vault/Vault.kt`: `typealias KekDerive`, `EXISTING_DERIVE`; `wrap` toma `derive`; `afterMasterUnlock(…, derive = EXISTING_DERIVE, onCostKept = {})`: si `weaker` y el `wrap` con el coste subido lanza `KDF_OUT_OF_MEMORY` o `KDF_NO_MEMORY_NOW`, un v4 del formato propio se re-sella con `write(known.copy(masterCheckedAt = now), …)` y un v2/v3 (u otro formato) se envuelve con `known.kdf.params()` exacto; después `onCostKept()`. Cualquier otro fallo, o sin `weaker`, sale como antes. El coste nunca baja: en el camino nuevo es el del archivo, eje por eje.
- `vault/VaultStore.kt`: parámetros `kekDerive` (solo tests) y `onCostKept`; `tellCostKept` lo dice una vez por almacén (= por proceso y bóveda) y solo con el estado `Unlocked` (no tras un bloqueo), desde el desbloqueo con la maestra (v4 y paso a v4) y desde `replace()` (JSON y copia).
- `AppContainer.kt`: `onCostKept = { vaultsNotices.post(KDF_COST_KEPT) }`. `vault/VaultsTexts.kt`: `KDF_COST_KEPT`, palabra por palabra del Ruling.
- **Tests** (`UnlockErrorTextTest`): `noMemoryWhenWrappingAgainOpensWithTheFileCost` (fallo persistente solo al envolver por encima del coste del archivo; dos desbloqueos abren; `kdf` del disco igual; `masterCheckedAt` sube; aviso una vez); `noMemoryWhenWrappingAgainUpgradesWithTheFileCost` (`vault-1.4.3.json` pasa a v4 con su coste 1024/1/1, aviso una vez); `otherFailuresWhenWrappingAgainStillFail`. El de la ronda 2 (`noMemoryWhenWrappingAgainLeavesTheFileAsItWas`) sigue en verde: su fallo sale de `kdf()`, antes de envolver.
- **Rojo:** los dos primeros, `CryptoFailure: Ahora no hay memoria suficiente para esta operación. …` (cada desbloqueo fallaba).
- **Verde:** todo `:app:testDebugUnitTest`, 2387 tests, 0 fallos.
- **Texto nuevo** `KDF_COST_KEPT`: «No se pudo subir el coste de Argon2id al elegido en Ajustes: ahora no hay memoria suficiente. La bóveda se ha guardado con el coste que ya tenía el archivo o la copia. Se volverá a intentar cada vez que la abras con la contraseña maestra. Si en Ajustes tienes el coste «Alto», puedes elegir «Estándar».» Nota para el Abogado: en el desbloqueo de un v4, el guardado es el de siempre (`commit` con `mustSave = false`): con el disco lleno la bóveda se abre igual y «se ha guardado» no sería cierto en ese caso extremo.

## Grupo 6. A m-2 y m-4, A m-3, prueba n.º 7

### A m-2 y m-4 (`c8a2b82`)
- `vault/BackupCheck.kt`: `checkCopyStructure` rechaza con `COPY_DAMAGED` un nombre de carpeta o de vista de más de `ORGANIZE_NAME_MAX` puntos de código (m-4); `checkCopySize(data)` nueva: datos por encima de `VaultLimits.VAULT_MAX_DATA_BYTES` dan `COPY_DAMAGED` (m-2). `checkBackup` la llama solo para una copia v4 (una anterior a la 2.0 se sube y se ajusta al importarla, así que sin ajustar podría pasar del tope y la importación aceptarla; las copias reales de antes nunca pasan de 6 MB). `vault/VaultStore.kt` `replace()`: `checkCopySize` en `compute`, después de `afterMasterUnlock` y `read`.
- **Tests** (`BackupCheckTest`): n.º 4 `v4CopyOverTheDataLimitIsDamaged` (3 notas de 8 MiB + 1 byte; «Comprobar» y «Importar copia» dan `COPY_DAMAGED` y la bóveda de destino queda igual, archivo a archivo); n.º 6 `longFolderNameIsDamaged` (carpeta y vista de 61 «ñ»: dañada; 60: se lee). Ayudante `rewrittenData`.
- **Rojo:** `longFolderNameIsDamaged :: Debía fallar`; `v4CopyOverTheDataLimitIsDamaged :: Debía fallar`.

### A m-3 (`f4323ce`)
- `vault/VaultData.kt`: `sortedForShow()` (clave `lowercase()` una vez por entrada, orden estable, el mismo que antes).
- `vault/VaultStore.kt`: se ordena dentro de `withContext(compute)` en `commit` (lectura propia), el paso a v4 (`unlockLocked`), la huella (`unlockWithKeyLocked`, `shown`), `modifyData` (`saved`) y `replace` (`Triple(checked, refs, shown)`, también para la importación de JSON). `opened()` recibe `read` siempre (ya no descifra ni ordena; nulo solo tras un bloqueo). Lo escrito en el archivo conserva su orden.
- **Tests:** n.º 5 `VaultStoreTest.sortRunsOnCompute` (de fuente: `opened` sin `sorted` ni `VaultCrypto.read(`; cada `.sortedForShow()` dentro de un `withContext(compute)`); `theListIsSortedAfterEveryPath` (maestra, huella, `upsert`, importar y la bóveda de la 1.4.3 antes del paso a v4); `PlatformSourcesTest` pasa a buscar `withContext(compute) { VaultCrypto.read(next, key).sortedForShow() }`.
- **Rojo** (los tests nuevos contra el `VaultStore.kt` de `c8a2b82`, restaurado después): `sortRunsOnCompute :: AssertionError: private fun opened(… (d -> d.copy(entries = d.entries.sortedBy …`. `theListIsSortedAfterEveryPath` es una guarda: el orden ya era el de antes (la primera ejecución dio un `NullPointerException` del propio test, `javaClass` dentro de `File.apply`; corregido).
- La medida en el móvil sigue en la fila 3.

### Prueba n.º 7 (i-5), solo informativa (no se confirma)
- Una copia v4 con una entrada con `"tags":["","",…]`: con 6 500 000 etiquetas (archivo de la bóveda de 26 000 405 caracteres) y con 8 100 000 (32 400 405 caracteres, junto al tope de 33 MiB), con el *heap* de los tests (512 MiB): **`importReplace` importa, sin `OutOfMemoryError` y sin `CryptoFailure`**, y la entrada guarda las 8 100 000 etiquetas vacías.
- No se cierra la app aquí, así que no pasa al ataque por la regla del Ruling. Lo apunto para el controlador: una copia hecha a mano puede meter millones de etiquetas vacías en una entrada (no hay tope de etiquetas por entrada en `checkCopyEntries`); con un *heap* de 256 MiB el resultado puede ser otro (fila «Móvil» de la memoria).

### Suites tras el grupo 6 (sobre `f4323ce`)
- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, **2391 tests, 0 fallos**.
- `./gradlew --stop`; `-p jvm-check test`: BUILD SUCCESSFUL, **2347 tests, 0 fallos**.

## Grupo 7. Textos de las copias

### B I-2: NO HECHO (para el controlador: un test ata un documento a las constantes)
- Escrito y en rojo: P-I2 `AutoBackupTargetsTest.theA6TextsNameTheCopiesOfBefore20` (rojo: `AssertionError: Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)`).
- Al cambiar los cinco literales, `ToolTextsTest` falla en `everyAnnexTextIsInTheListVerbatim`, `everyToolSectionOfTheAnnexIsCheckedAgainstTheApp` y `everyAppTextIsInTheAnnex`: el anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md` (líneas 120, 124, 128 y 129) fija palabra por palabra `PASSWORD_CHANGE_REPLACE_COPIES`, `AUTO_BACKUP_REPLACED` y los dos `autoBackupUnlistedText` de una carpeta. Las líneas 448-450 citan además `AUTO_BACKUP_REPLACED_SEVERAL`, `a6PartialText`, los de varias carpetas y `a6UnlistedInDoneText` (no los comprueba ningún test, pero quedarían viejos).
- Según el encargo, paré ahí: **no hay commit de B I-2** y los archivos quedaron como estaban. El cambio entero (código y tests: los cinco literales, `ToolTextsTest`, `FinalLegalReviewTest.o2AndR6TheA6TextsAreTheProposedOnes`, `AutoBackupF3Test` y P-I2) está en `/home/user/Seguridad/.superpowers/sdd/2026-11-12-clavero-2.0/branch-fixes-b-i2.patch`; se aplica con `git apply` en cuanto el anexo tenga los textos nuevos. Textos propuestos (con las palabras de Ajustes, en plural):
  - `PASSWORD_CHANGE_REPLACE_COPIES`: «Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)» → «Hacer una copia nueva y borrar las anteriores (automáticas, de antes de importar y de antes de la 2.0)».
  - `AUTO_BACKUP_REPLACED`: «…se borraron las copias automáticas y las de antes de importar hechas antes del cambio…» → «Se hizo una copia nueva y se borraron las copias automáticas, las de antes de importar y las de antes de la 2.0 hechas antes del cambio de contraseña: las que la app tenía apuntadas en esta carpeta. Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.»
  - `AUTO_BACKUP_REPLACED_SEVERAL`: lo mismo, «…en cada carpeta y se borraron las copias automáticas, las de antes de importar y las de antes de la 2.0 hechas antes del cambio de contraseña: las que la app tenía apuntadas en cada carpeta. …».
  - `a6PartialText`: «Se borraron las copias automáticas, las de antes de importar y las de antes de la 2.0 hechas antes del cambio de contraseña (las que la app tenía apuntadas) en N de las M carpetas.»
  - Sin apuntar (`autoBackupUnlistedText` con una y con varias carpetas, `a6UnlistedInDoneText`): «(automática o de antes de importar)» → «(automática, de antes de importar o de antes de la 2.0)»; en plural, «(automáticas, de antes de importar o de antes de la 2.0)», salvo el plural de una carpeta, que ya decía «automática» en singular y así lo fija el anexo (lo dejo igual, para no cambiar más de lo pedido).

### Fila 30 (`9e97517`)
- `vault/TrashTexts.kt` `COPIES_KEEP_IT`: «Las copias ya hechas (automáticas o exportadas) lo conservan.» → «Las copias ya hechas (automáticas o exportadas) pueden conservarlo; la copia automática quita las más antiguas.» (sale en «Borrar para siempre», «Vaciar papelera», «Borrar historial» y la papelera).
- **Test:** P-I3 `TrashTest.theCopiesMayKeepItNotAlways`. **Rojo:** `ComparisonFailure: expected:<…[pueden conservarlo; la copia automática quita las más antiguas].> but was:<…[lo conservan].>`. Ningún test ata un documento a esta constante.

### Fila 39 (`c2da53c`)
- `ui/screens/VaultBackup.kt`: `EXPORT_DOES_NOT_FIT_TODO` y `exportFailureText(message)` (un `when`): `BACKUP_DOES_NOT_FIT` o `_V3` + «. Quita adjuntos o entradas grandes, o vacía la papelera, y vuelve a exportarla.»
- `ui/screens/VaultUpgrade.kt`: `UPGRADE_DOES_NOT_FIT_TODO` y `upgradeExportFailure` con su `when`: + «. Puedes seguir sin copia: la bóveda pasará al formato nuevo y las versiones anteriores ya no la abrirán.» (el botón se llama «Seguir sin copia», `UPGRADE_SKIP`).
- **Test:** `VaultUpgradeTextsTest.aVaultThatDoesNotFitSaysWhatToDo` (cada sitio su texto; el del paso a v4 sin «quita»). **Rojo:** `expected:<…2 000 archivos)[. Quita adjuntos o entradas grandes, o vacía la papelera, y vuelve a exportarla.]> but was:<…2 000 archivos)[]>`.

### Fila 42 (`848f118`)
- `vault/BackupCheckTexts.kt`: `COPY_WRONG_PASSWORD_OR_DAMAGED`, `COPY_KDF_PARAMS_INVALID` y `copyFailureText(e)` (pura; lo demás, `KDF_OUT_OF_MEMORY`, `KDF_NO_MEMORY_NOW`, `COPY_DAMAGED`…, igual).
- La usan `ui/screens/VaultBackup.kt` («Importar copia»), `vault/BackupCheckSession.kt` («Comprobar una copia») e «Importar como bóveda nueva». Sin parámetro nuevo en `runVaultsAction`: dos funciones en `AppContainer.kt`, `copyRefusal { }` (convierte el `CryptoFailure` en `VaultsRefused(copyFailureText(e))`) y `masterRefusal { }` (`VaultsRefused(masterUnlockErrorText(e))`), que envuelven las dos acciones en `VaultPickerScreen.kt`. `AppContainer.importAsNewVault` y `deleteVault` siguen lanzando `CryptoFailure` (lo esperan sus tests).
- **Tests:** P-42 `BackupCheckTest.aCopysFailureIsSaidAsOfACopy`; `AppContainerVaultsTest.theVaultsActionsSayTheirOwnFailures` (importar como nueva con la contraseña mala: el texto de la copia; borrar con la maestra mala: `WRONG_MASTER`, y la bóveda sigue); `theCopyTextsAreUsedWhereACopyIsOpened` (de fuente, los tres sitios y el borrado).
- **Rojo:** `aCopysFailureIsSaidAsOfACopy` y `theVaultsActionsSayTheirOwnFailures :: expected:<…incorrecta o [copia dañada. Vuelve a elegir la copia y escribe su contraseña.]> but was:<…incorrecta o [datos dañados]>`; `theCopyTextsAreUsedWhereACopyIsOpened :: AssertionError`.
- **Textos nuevos:** `COPY_WRONG_PASSWORD_OR_DAMAGED` «Contraseña incorrecta o copia dañada. Vuelve a elegir la copia y escribe su contraseña.» (antes salía «Contraseña incorrecta o datos dañados»); `COPY_KDF_PARAMS_INVALID` «La copia pide unos parámetros de cifrado que Clavero no admite. Elige otra copia.» (antes «Parámetros de cifrado no válidos»). Borrar una bóveda con la maestra mal: antes «Contraseña incorrecta o datos dañados», ahora «Contraseña incorrecta» (`WRONG_MASTER`).
- **Verde:** todo `:app:testDebugUnitTest`, 2396 tests, 0 fallos.

## Grupo 8. B m-2 (`8c9adc7`)

- `AppContainer.kt`: `forgetOrphanVaults()`, bajo `changingVaults` (no se cruza con crear ni borrar una bóveda): quita de `perVault` un id solo si **no está en el registro y además no existe su carpeta** (`registry.dirOf(id)`: `files/vaults/<id>/`, o `files/vault/` para `principal`; un id que no nombra carpeta cuenta como sin carpeta); después `releaseIfUnused` de sus carpetas de copias. Un fallo al escribir no cambia nada (se reintenta en el siguiente arranque). `SeguridadApp.kt`: se lanza en `appScope` (IO) tras `settings.load()`.
- **Test:** `AppContainerVaultsTest.theSettingsOfAVaultThatIsGoneGoAtTheStart` (P-m2 y el contrario: el id sin registro ni carpeta se va y su carpeta se suelta; el id sin registro con su carpeta en el disco se queda; `principal` se queda; el archivo lo refleja; `SeguridadApp` lo llama después de leer los ajustes).
- **Rojo:** `theSettingsOfAVaultThatIsGoneGoAtTheStart :: AssertionError` (con la función vacía).
- Con esto, `VaultsTexts.kt:48` («… y sus ajustes») vuelve a ser verdad tras un fallo, en el siguiente arranque.
- **Verde:** todo `:app:testDebugUnitTest`, 2397 tests, 0 fallos.

## Grupo 9. Parte C

### C I-1 (`8341f12`)
- `ui/screens/VaultPickerScreen.kt`: `PickerDialog.kind` y `PickerDialogKind`; `pickerDialogKept(kind, state)` pura (abierta: todos; bloqueada o cargando: solo `DELETE_EMPTY`; sin bóveda: `NEW`, `IMPORT_NEW`, `DELETE_EMPTY`); `LaunchedEffect(active, activeState)` cierra el diálogo. La señal es `activeState` (síncrona), no `canAdd`. (Se decide sobre el tipo, no sobre `PickerDialog`, porque `ImportNew` lleva un `Uri`, que no se construye en un test de JVM.)
- **Tests:** `ui/screens/VaultPickerDialogsTest` (los cinco diálogos en los cuatro estados; de fuente). **Rojo:** `eachDialogInEachState :: Locked expected:<[DELETE_EMPTY]> but was:<[NEW, RENAME, DELETE, DELETE_EMPTY, IMPORT_NEW]>`; `theScreenClosesItOnTheVaultsState :: AssertionError`.

### C I-2 y m-3 (`d15d67a`)
- `ui/screens/EntryFields.kt`: `onBig: ((String) -> Unit)? = null` y `bigActionFor(entry, fieldId, onBig)`: sin manejador, sin botón. `ui/screens/EntryHistoryScreen.kt`: recibe `twoStep: TwoStepCopy`; cada `Field`, las contraseñas anteriores y `EntryFieldsCard` copian con `onCopied = twoStep::clear` (por `VersionCard(v, onCopied, …)`). `ui/AppNavHost.kt` pasa `container.twoStepCopy`. `PlatformSourcesTest` (R9) busca los dos `Field` con `onCopied = onCopied`.
- **Tests:** `ui/screens/EntryFieldsTest.noBigButtonWithoutHandler` (n.º 2) y `theHistorysCopiesEndTheOffer` (de fuente). **Rojo:** `noBigButtonWithoutHandler :: expected null, but was:<…EntryFieldsKt$$Lambda…>`; `theHistorysCopiesEndTheOffer :: AssertionError`.

### Fila 26 (`a5d333f`)
- `vault/Templates.kt`: `wifiSecurityLabel("WPA")` → «WPA/WPA2» (antes «WPA/WPA2/WPA3»); `WIFI_WPA3_NOTE` «Si la red es solo WPA3, puede que el QR no sirva para conectarse.», bajo el selector en `ui/screens/EntryFields.kt`. Lo guardado (`WPA`) no cambia.
- **Test:** `TemplatesTest.templatesNameNoBrands` (n.º 3). **Rojo:** `expected:<WPA/WPA2[]> but was:<WPA/WPA2[/WPA3]>`.

### C m-1 (`4d5917a`)
- `ui/screens/HealthScreen.kt`: un solo `produceState<HealthView?>(null, data, breached, day, missionsOff)` en `Dispatchers.Default` da los `items` y, de ellos sin `Issue.BREACHED`, las misiones. Sin resultado, ni la tarjeta del resumen («Sin problemas detectados.» y las cuentas) ni filas. Con esto la fila 33 tiene la clave `day`.
- **Tests:** `ui/screens/HealthScreenTest` (n.º 5: un solo `vaultHealth(`, dentro de `withContext(Dispatchers.Default)`, con esas claves; nada antes del resultado). `MissionsTest.titlesLiveOnlyWithTheOpenVault` sigue la forma nueva. **Rojo:** `vaultHealthRunsOnceOffTheMainThread :: expected:<1> but was:<2>`; `nothingIsSaidBeforeTheResult :: AssertionError`.

### C m-2 (`d12f70b`)
- `settings/Guide.kt`: en las dos guías, «Las copias con calendario solo se hacen al abrir Clavero.» → «Las copias con calendario se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, y solo si hubo cambios.» (Ruling, palabra por palabra).
- **Decisión mía, para el Abogado:** la página de novedades no puede pasar de 160 caracteres (`GuideTest.thePages`, texto mínimo M-3); con la línea del Ruling pasaba (168). Acorté su primera frase: «Hasta 3 carpetas, cada una con su calendario.» → «Hasta 3 carpetas, con calendario propio.» (156 en total). La línea del Ruling queda igual.
- **Test:** `GuideTest.theRoundsTexts` fija las dos páginas. **Rojo:** `expected:<…ias con calendario s[e hacen con la bóveda abierta, …]> but was:<…ias con calendario s[olo se hac…`.
- El README (línea 31) dice todavía «Las copias con calendario solo se hacen al abrir Clavero o la bóveda»: no lo ata ningún test y no lo toco (segunda pasada de documentos).

### Suites finales (sobre `d12f70b`)
- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, **2403 tests, 0 fallos**.
- `./gradlew --stop`; `-p jvm-check test`: BUILD SUCCESSFUL, **2352 tests, 0 fallos**.

## Lo no hecho y por qué
- **B I-2:** no confirmado. El anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md` está atado por `ToolTextsTest` a cuatro de los literales; el encargo dice parar ahí. Parche listo en `/home/user/Seguridad/.superpowers/sdd/2026-11-12-clavero-2.0/branch-fixes-b-i2.patch` (grupo 7).
- **Sin *push*:** el controlador pidió subir tras cada commit, pero el encargo dice «No hagas push» y el permiso del entorno lo rechazó; los commits desde `4d5917a` están solo en local (los anteriores, según el controlador, ya los subió él).
- Prueba n.º 7: solo informativa (grupo 6); una copia hecha a mano puede meter millones de etiquetas vacías en una entrada.

## Textos para el Abogado Digital (revisión final)
- `ATTACHMENTS_TOTAL_WOULD_PASS` (C-1, al añadir un adjunto).
- `COPY_DAMAGED` ahora también para un adjunto de tamaño imposible en una copia (fila 37).
- `MASTER_CHANGED_IN_OTHER_VAULT` y `MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT` (A I-1, en `vaultsNotice`); `KDF_COST_KEPT` (fila 45, en `vaultsNotice`; ver la nota del disco lleno en el grupo 5).
- `KDF_OUT_OF_MEMORY` (nuevo texto) y `KDF_NO_MEMORY_NOW` (fila 38).
- `COPIES_KEEP_IT` (fila 30, con la 19).
- `EXPORT_DOES_NOT_FIT_TODO` y `UPGRADE_DOES_NOT_FIT_TODO` (fila 39).
- `COPY_WRONG_PASSWORD_OR_DAMAGED` y `COPY_KDF_PARAMS_INVALID` (fila 42); borrar una bóveda con la maestra mal dice ahora «Contraseña incorrecta».
- La etiqueta «WPA/WPA2» y `WIFI_WPA3_NOTE` (fila 26).
- La línea de la guía y la primera frase acortada de la página de novedades (C m-2).
- Los cinco de A6 con «las de antes de la 2.0» (B I-2, pendientes de aplicar, con su parche).
- Filas 46 y 47, como dice el Ruling.

## Revisión experto-seguridad-cripto
- `vault/Attachments.kt` (`attachmentsFit`) y `vault/VaultStore.kt` `addAttachment`: el total cuenta la papelera; toda bóveda que la app guarda pasa `checkCopyEntries` (P-C1c). Se cierra «la app escribe copias que no lee» (C-1).
- `vault/BackupCheck.kt` `checkCopyEntries`: tamaño fuera de rango = copia dañada; identidades solo vivas (D-1). `checkCopyStructure` y `checkCopySize`: nombres y tamaño de datos acotados (decisión (a), copias hostiles de la decisión (c)).
- `AppContainer.kt` `onMasterChanged`/`switchTo` bajo `masterChangedLock`: el diálogo de A6 nunca opera sobre las copias de otra bóveda (A I-1).
- `vault/Vault.kt` `afterMasterUnlock`: con falta de memoria al subir el coste se usa el del archivo, eje por eje; nunca baja (CR-2, A8). Solo con `KDF_OUT_OF_MEMORY`/`KDF_NO_MEMORY_NOW` y `weaker`; cualquier otro fallo sale igual. El aviso, solo con la bóveda abierta.
- `crypto/Crypto.kt`: Argon2id sigue siendo el de Bouncy Castle; solo cambia el texto según `p.memoryKiB`.
- `vault/A6Text.kt`: veracidad de A6 (filas 17 y 18); `requestId` intacto para lo que se guarda.
- `ui/screens/VaultPickerScreen.kt`: el nombre de la bóveda y las contraseñas escritas se van con el bloqueo (C I-1).
- Sin primitivas nuevas, sin permisos, red ni registros; ningún formato (`seguridad-vault`, `seguridad-backup`, `SG1:`, `.sgenc`) cambia.

## Revisión Ingeniero Móvil Senior
- `vault/VaultStore.kt` (`commit`, `unlockLocked`, `unlockWithKeyLocked`, `modifyData`, `replace`): el orden de la lista y la lectura van en `compute`; `opened()` ya no trabaja en el hilo de quien llama (m-3).
- `ui/screens/HealthScreen.kt`: un `produceState` en `Dispatchers.Default`; nada pesado en la composición (m-1).
- `AppContainer.kt` `vaultsNotices`: los avisos nuevos viven fuera de la composición y sobreviven al giro y al cambio de bóveda.
- `AppContainer.kt` `forgetOrphanVaults` bajo `changingVaults`, lanzado en `appScope` (IO) desde `SeguridadApp.kt` tras `settings.load()`; nunca en el hilo principal.
- `ui/screens/VaultPickerScreen.kt`: `LaunchedEffect(active, activeState)`, con el estado síncrono de la bóveda.
- `ui/screens/EntryHistoryScreen.kt`: `TwoStepCopy` viene de `AppContainer` (vive con el proceso, no con la pantalla).
