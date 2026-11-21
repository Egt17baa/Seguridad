# Clavero 2.0: informe de la ronda de arreglos de la auditoría completa (21-11-2026)

**Encargo:** `advisor-t12-audit-fixes.md`, «Encargo (para pegar)», los 18 puntos en su orden, con `advisor-t12-review-fixes-cierre.md` (RC-1 a RC-6) y las cinco `final-audit-*.md`.
**Rama:** `ccr-0aa27f95-u038cd`, desde `5ad159e` (el Ruling). Sin *push*: sube el controlador.
**Identidad de los commits:** Egt17baa, con las dos líneas de atribución de la sesión. Ningún `--amend`, ningún `@Ignore`.

Rutas abreviadas: `m/` es `app/src/main/java/app/seguridad/`; `t/`, `app/src/test/java/app/seguridad/`.

## 1. Tabla: commit, rojo y verde de cada punto

| Punto | Hallazgo | Commit | Test(s) | Rojo (antes del arreglo) | Verde |
|---|---|---|---|---|---|
| 1 | B-2 | `7505281` | `PlatformSourcesTest.theLauncherActivityHasAnEmptyTaskAffinity`; `check-manifest.sh` sobre el APK de depuración | `expected: SingletonList<[]> but was: EmptyList<[]>`; el script, abajo (§3) | jvm `PlatformSourcesTest`, `AppNameTest`, `SourceTextsTest`, `EmptySlotGuardSourceTest`, `RedTeam20Recheck2Test`, `RedTeam20Recheck3Test`: 97/0; el script, `OK` |
| 2 | B-1 | `e4f1018` | `PlatformSourcesTest.theNavigationNeverSeesTheLaunchIntent` | `AssertionError: the plain intent, after super.onCreate and before setContent` | jvm 49/0; app `PlatformSourcesTest` + `ui.*` 110/0 |
| 3 | A-1 + B-3 | `d9e341a`, `18adc17` y `539d2fd` | `AppContainerVaultsTest.deletingAVaultForgetsItsFingerprintBeforeItsFolder`, `anOrphanFingerprintIsForgottenAtStart`; `PlatformSourcesTest.deletingAVaultDeletesItsFingerprintPreferences` | `expected:<[(true, true)]> but was:<[(false, false)]>`; `expected:<[aaaa…]> but was:<[]>`; la tercera, `AssertionError` (no existía `forget`) | jvm `PlatformSourcesTest` + `AppContainer*` 90/0; app `PlatformSourcesTest` 50/0 |
| 4 | RC-2 (con E-2) | `0ae9409` | `UpgradeCopyFirstTest.aPassThatWaitedForTheLockDefersItsCopyUntilTheUpgradeCopy`, `aCopyQueuedOnTheLockBeforeAReopenWaitsForTheNewUpgradeCopy`, `theWaitEndsWhenTheUnlockCallerIsCancelled` | RC-2: `the next one is the upgrade's: [antes-de-2-0-…090000.tmp, auto-…090000.tmp, auto-…090001.tmp, antes-de-2-0-…]`; E-2: `the second document is the new opening's upgrade copy: [antes-de-2-0-…, auto-…, antes-de-2-0-…090001.tmp]`; el tercero, verde desde el principio (fija lo que se cumple) | `UpgradeCopyFirstTest` + `AutoBackup*` + `redteam.*`: 300/0 |
| 5 | RC-3 | `2b3ee81` | `UpgradeCopyFirstTest.theTwoPassesWaitForTheUpgradeCopyRightBeforeTheLock` | quitando la espera de `runChanged` (`m/vault/AutoBackup.kt:485`): `two waits: [234] expected:<2> but was:<1>` | `UpgradeCopyFirstTest` 12/0 |
| 6 | D-1 | `b3825b4` | `UpgradeCopyFirstTest.aFolderThatNeverAnswersStillLeavesTheChoice`, `aDueCopyStuckBeforeTheUnlockDoesNotHoldTheUpgrade`; `VaultUpgradeTextsTest.theCopyNotDoneSaysTheWaitAndTheChoice` | los dos primeros, `AssertionError` en `assertFalse(s.upgradeCopying.value)` pasados los 2 minutos (`UpgradeCopyFirstTest.kt:460`, `:515`); el de texto, `AssertionError` (el diálogo no lo usaba) | app `VaultUpgradeTextsTest` + `SourceTextsTest` 16/0; jvm `UpgradeCopyFirstTest` + `VaultStore*` + `AutoBackup*` + `redteam.*` + `ErrorTextsTest` + `SourceTextsTest` 413/0 |
| 7 | RC-1 | `c059ff4` | `VaultStoreImportTest.thePreviewLetsInWhatTrimmingTheHistoryMakesFit`, `theSnapshotCarriesTheWholeVault` | `the preview lets it in expected:<0> but was:<494>`; `expected:<VaultData(… vaultTag=0123…)>` frente a un `rest` sin `vaultTag`, `lastCheckedBackupAt` ni `templatesVersion` | `VaultStoreImportTest` + `FittedHistoryTest` + `imports.*` + `redteam.*`: 520/0. `RedTeam20ListCostTest` sin cambio: B 2 954 912 B, D 1,213x |
| 8 | E-1 | `ae488ea` | `PasswordMarkTest.anEntryOf143JustUnderTheEntryCapStillTakesAnEditThatKeepsItsPassword`, `anEntryOf143JustUnderTheEntryCapCanStopBeingAFavourite` («Favorita»), `aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted` | los dos primeros: `VaultLimitExceeded: La entrada pasa de 64 KB. Acorta sus notas o sus campos.`; el tercero, verde desde el principio (ver §5) | `vault.*` + `imports.*` + `redteam.*`: 1 407, con un rojo ajeno del punto 3 (abajo); después, jvm entera 2 520/0 |
| 9 | Fila 84 | `462aad3` | `AttachmentsTest.theCountOfACopyLimitsTheAttachments` (con el texto y `ForbiddenWords`); `VaultStoreTest.anAttachmentPastTheCopysFileCountIsRefused` | `AssertionError` (con 1 998, `attachmentCountFits` daba verdadero); `AssertionError: Debía rechazarlo` | `vault.*` + `redteam.*` + `AppContainer*` + `ErrorTextsTest` + `SourceTextsTest`: 1 113/0 |
| 10 | C-5 | `3882d17` | `AttachmentsTest.fileOfRefusesAnIdThatIsNotAnAttachmentId` | `AssertionError: Debía rechazar «../vault»` | `vault.*` + `imports.*` + `redteam.*` + `AppContainer*`: 1 450/0 |
| 11 | B-4 | `d2838ee` | `AppContainerTest.theOfferIsShownInSplitScreen`; `PublishedTextsTest.theTwoStepCopyIsSaidWhereItHappens` | `expected:<e> but was:<null>`; `AssertionError` (las frases nuevas no estaban) | `AppContainer*` + `tools.*` + `PlatformSourcesTest` + `*Legal*` + `*Texts*`: 671/0 |
| 12 (a) | RC-5 | `e629906` | `FolderDeletionTextTest.aTopFolderSaysWhereItsSubfoldersGo` | `expected:<Sus entradas [pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»]> but was:<Sus entradas [y sus subcarpetas se quedan sin carpeta]>` | `Folder*` + `Organize*` + `tools.*`: 562/0 |
| 12 (b) | A-2 | `0b01405` | `KdfCostHintTest.theCostHintSaysTheVaultIsRaisedNeverLowered` (con el ancla de su uso en Ajustes) | `expected:<[Es de cada bóveda… como este.]> but was:<[]>` (la constante, vacía, como andamio) | jvm 583/0; app `KdfCostHintTest` + `SourceTextsTest` 13/0 |
| 13 (a) | D-2 | `9b42fec` | `VaultPickerDialogsTest.aDialogOpenedAfterTheLockClosesToo`, `theImporterAsksOnlyWithTheVaultOpen` | los dos, `AssertionError` | app `VaultPickerDialogsTest` + `PlatformSourcesTest` 54/0 |
| 13 (b) | D-4 | `b1e1ce4` | `VaultOrganizeSourceTest.deleteFolderClearsTheRefusalAndWaits` | `the row and its radio button expected:<2> but was:<0>` | app 51/0 |
| 14 | RC-6 + E-3 | `3399f10` | `ContactsViewTest.theMasterGateIsNeverReachedWithAnOpenVault` | verde desde el principio (fija lo que se cumple). Rojo con una entrada alterada (`contactsGate` sin las ramas `asking` y `copying`): `AssertionError: true: [(false, Master), (false, Master), (true, Master), (true, Content), (true, Content)]` | `ContactsViewTest` + `AppContainer*` + `ErrorTextsTest`: 89/0 |
| 15 (a) | RC-4 | `6fc26b7` | `ErrorTextsTest.theScreensSayTheStoreTextsOfAFailedSave` | con `m/ui/screens/VaultBackup.kt:168` sin `storeSaveErrorText`, `AssertionError` (el ancla nueva; el recorrido no lo veía). El recorrido cuenta hoy 19 (visto con `>= 999`: `the scan finds the saves (19)`) | `ErrorTextsTest` 9/0 |
| 15 (b) | C-1 | `97d0bfc` | `PublishedFilesNameNoModelTest.noPublishedFileNamesAModel`, `theScanFindsANameInATestTree` | con un documento temporal con un nombre (`docs/rojo-c1-temporal.md`, borrado después): `expected:<[]> but was:<[docs/rojo-c1-temporal.md:1]>` | jvm 22/0 (con `AppNameTest` y `SourceTextsTest`); app 2/0; 0,6 s |
| 16 | C-7 | `0effa6b` | `WorkflowHardeningTest`: `everyActionIsPinnedToACommit`, `noPullRequestTargetAndReadOnlyByDefault`, `signingSecretsOnlyInTrustedRuns`, `theUploadCertificateIsChecked`, más `eachCheckFailsOnAnAlteredCopy` | sobre el YAML real alterado y restaurado: `but was:<[actions/checkout@v5]>`; `<[pull_request_target]>`; `<[untrusted: KEYSTORE_BASE64: ${{ secrets.SEGURIDAD_KEYSTORE_BASE64 }}]>`; `<[UPLOAD_CERT_SHA256 746398EB…, PUBLICAR 746398ea…]>` | 5/0, más `AppNameTest` |
| 17 | A-3 | `89fd69f` | `BackupV3Test.aCopyOfAV4IsRefusedByThe143Reader` | verde desde el principio; rojo con una entrada alterada (`writeBackup` con la marca 2 para una v4): `expected:<[3]\n> but was:<[2]\n>` | `BackupV3Test` 10/0 |

**Suites enteras al final (punto 18), por separado:** ver §8.

## 2. Lo hecho en cada punto, con `archivo:línea`

1. **B-2.** `app/src/main/AndroidManifest.xml:28`: `android:taskAffinity=""` solo en `.MainActivity`, con su comentario (`:20-22`). `scripts/check-manifest.sh:11-14` (cabecera), la columna nueva del `awk` y la comprobación del bucle: falla con «`app.seguridad.MainActivity sin android:taskAffinity="" (…)`» y si la actividad no aparece una vez. README: la descripción del script (`README.md:117`) y la prueba «2.0, versión y permisos» (`README.md:416`), que dice que el manifiesto solo cambia frente a la 1.4.3 en `versionName` y `taskAffinity`.
2. **B-1.** `m/MainActivity.kt:39`: `intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, MainActivity::class.java)`, después de `super.onCreate` y antes de `setContent`; sin `onNewIntent`. KDoc de la clase (`:27-32`).
3. **A-1 + B-3.** `m/AppContainer.kt:752-760`: `forgetBiometric(id)` antes de `registry.remove(id)`, con el mismo `try`; comentarios verdaderos en `:140-145` y `:755-759`. `m/android/BiometricVault.kt:188-202` (`forget`, `:193`), en la fila (`KeyOp.DISABLE`), con `edit(commit = true)` (`:197-201`), la clave del Keystore y `deleteSharedPreferences(perVaultName(PREFS, vaultId))` salvo para `PRIMARY_VAULT_ID` (`:199`). `m/SeguridadApp.kt:41`. `forgetOrphanVaults` (`m/AppContainer.kt:785-792`) olvida la huella de cada huérfano antes de quitar sus ajustes.
4. **RC-2.** `m/vault/AutoBackup.kt:835`: `if (store.state.value !is VaultState.Unlocked || store.upgradeCopying.value) { defer(uri); return null }`.
5. **RC-3.** Solo test.
6. **D-1.** `m/vault/PendingUpgrade.kt:14` (`UPGRADE_COPY_WAIT_MS = 120_000L`), `:98-105` (`withTimeoutOrNull`, dentro del `try` de antes), `:115` (`autoCopyNotDone`, dentro de `synchronized(opening)`), `:67` (se pone a falso en `openedWith`). `m/vault/VaultStore.kt:327` (`upgradeAutoCopyNotDone`). `m/ui/screens/VaultUpgrade.kt:58` (texto) y `:132-133` (el diálogo lo enseña en lugar de `UPGRADE_AUTO_COPY_FAILED`).
7. **RC-1.** `m/vault/VaultLimits.kt:153` y `:187` (`fitted(limit)`, con el valor por defecto de siempre); `m/imports/MergePlan.kt:434-435` (`previewDataBytes`), KDoc de `PREVIEW_UNSEEN_BYTES`; `m/vault/VaultStore.kt:1028` (`rest = d.copy(entries = open.trash)`) y el KDoc de `VaultSnapshot`. Cambio autorizado: la igualdad de `VaultSnapshot` de `VaultStoreImportTest` compara con los datos del almacén.
8. **E-1.** `m/vault/VaultLimits.kt:102-103` (`bytesWithoutHistory` sin `history` ni `passwordChangedAt`) y `:18-19` (KDoc de `ENTRY_MAX_JSON_BYTES`).
9. **Fila 84.** `m/vault/Attachments.kt:52-54` (`ATTACHMENTS_COUNT_WOULD_PASS`) y `:60-61` (`attachmentCountFits`); `m/vault/VaultStore.kt:1381`, antes del tope de bytes. La pantalla enseña el mensaje de `AttachmentLimit` tal cual (`m/ui/screens/VaultAttachments.kt:73-74`).
10. **C-5.** `m/vault/Attachments.kt:95-98`: `require(isAttachmentId(id))` en `fileOf`. Llamadores, en §4.
11. **B-4.** `m/AppContainer.kt:225` (`twoStepCopy.markLeft()` después de la guarda); KDoc de `TwoStepCopy` (`m/tools/TwoStepCopy.kt:12-19`, `:44-48`) y comentario de `m/MainActivity.kt:123-125`. README (`:24` y `:407`) y notas (`2026-11-15-clavero-2.0-notas.md:28`), con las frases del encargo.
12. **(a) RC-5:** `m/vault/OrganizeTexts.kt:63`. **(b) A-2:** `m/vault/VaultsTexts.kt:135-140` (`KDF_COST_MORE`) y `m/ui/screens/SettingsScreen.kt:668`.
13. **(a) D-2:** `m/ui/screens/VaultPickerScreen.kt:202` (`LaunchedEffect(active, activeState, dialog)`) y `:223`. **(b) D-4:** `m/ui/screens/VaultOrganize.kt:349` (`busy`), `:359-360` (`error = null`), `:374-388` (`enabled = !busy`, con `finally`).
14. **RC-6 + E-3.** KDoc de `deleteVault` (`m/AppContainer.kt:705-720`), de `ContactsFrame` (`m/ui/screens/ContactsScreen.kt:78-84`) y de `MasterPrompt` (`:130-137`). Sin cambio de código; `ContactsGate.Master` se queda como defensa.
15. **(a) RC-4** y **(b) C-1**: solo tests, más las entradas de C-1 en `jvm-check/build.gradle.kts:69-71` y `app/build.gradle.kts` (bloque de `tasks.withType<Test>`).
16. **C-7** y 17. **A-3**: solo tests.

## 3. `check-manifest.sh` sobre el APK de depuración (punto 1)

Rojo, con el script nuevo y el manifiesto de antes:

```
check-manifest: ERROR: app.seguridad.MainActivity sin android:taskAffinity="" (taskAffinity=-): otra app podría meter una pantalla suya en la tarea de Clavero (B-2)
check-manifest: androidx.compose.ui.tooling.PreviewActivity exportado, permitido solo porque el APK es debuggable
check-manifest: 1 error(es) en app/build/outputs/apk/debug/app-debug.apk
(salida 1)
```

Verde, con el manifiesto nuevo:

```
check-manifest: androidx.compose.ui.tooling.PreviewActivity exportado, permitido solo porque el APK es debuggable
check-manifest: OK (app/build/outputs/apk/debug/app-debug.apk, debug)
(salida 0)
```

`aapt2 dump xmltree`: `A: http://schemas.android.com/apk/res/android:taskAffinity(0x01010012)=""`, dentro del `E: activity` de `MainActivity`. El valor vacío se lee como `""`, como preveía el Ruling.

## 4. C-5: los llamadores de `fileOf` y qué hacen con el rechazo

Ninguno cierra la app; todos fallan cerrados con un texto fijo.

| Llamador | De dónde sale el id | Qué pasa con un id que no es de adjunto |
|---|---|---|
| `m/vault/VaultStore.kt:1096` (`applyImport`, `accept`) | las referencias de la propia bóveda | la `IllegalArgumentException` sale de `applyImport`, dentro del `mutex`, sin escribir nada; `ImportFlow.confirm` la recoge (`m/imports/ImportFlow.kt:53`) y da `SaveFailed`, con su texto fijo |
| `m/vault/VaultStore.kt:1476` (`export`) | las referencias de la bóveda | nunca llega: `:1474` ya rechaza con `CryptoFailure(attachmentUnusable(nombre))` |
| `m/vault/VaultStore.kt:1681` (`moveIn`, «Importar copia») | las referencias de la copia | nunca llega: `attachmentInCopy` (`m/vault/BackupCheck.kt:315`) da `MISSING` y la importación se rechaza antes. Si llegara, `moveIn` lo deshace (`putBack`) y relanza, y la pantalla dice «No se pudo importar la copia» (`m/ui/screens/VaultBackup.kt:198`) |
| `m/vault/Attachments.kt:134` (`add`) | 16 bytes aleatorios en hexadecimal | siempre válido |
| `m/vault/Attachments.kt:156` (`open`) | la referencia | nunca llega: `ID.matches(ref.id)` va antes (`:155`) y da `CryptoFailure` (`ATTACHMENT_DAMAGED`) |

## 5. Desviaciones y decisiones del implementador (para el director)

- **Punto 3: un commit, no dos.** A-1 y B-3 son el mismo arreglo en el Ruling («Ronda, punto 3»). Al correr `vault.*` en el punto 8 salió en rojo `VaultRegistryTest.biometricKeysArePerVault`, que fijaba `BiometricVault.disable` en `SeguridadApp`: es el cambio de firma autorizado («`forget`, solo esa firma»). Va en su propio commit, `18adc17`, porque no se usa `--amend`. Al final, `lintDebug` dio dos avisos nuevos en `forget` (`ApplySharedPref` y `UseKtx`, por `.edit()…commit()`): `539d2fd` usa `edit(commit = true) { … }`, que escribe igual en el acto, y el ancla de `PlatformSourcesTest` mira esa forma. `lintDebug` vuelve a 0 errores y 21 avisos, los de antes de la ronda.
- **Punto 4, el rojo de RC-2:** con los pasos del cierre, el `runDue` de `launchDue` no tenía nada que copiar (la copia retenida acaba bien, así que no deja `pending`), y el test salía verde sin el arreglo. La «secuencia equivalente»: además, una petición A6 guardada mientras la copia está retenida. Con ella, el rojo es el esperado (la copia automática va antes que la de la subida).
- **Punto 6, la fórmula del encargo:** `withTimeoutOrNull(…) { copyBefore() } ?: CopyResult.Failed(UPGRADE_AUTO_COPY_NOT_DONE)` convertiría el `null` de «no hay carpeta» en «no ha terminado en 2 minutos». Se distingue con una bandera (`waited`, `m/vault/PendingUpgrade.kt:100-105`). El texto vive en `ui/` (`VaultUpgrade.kt:58`), así que el `Failed` del almacén lleva `AUTO_BACKUP_FAILED` y la bandera decide el texto.
- **Punto 8, el tercer test** (`aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted`) salió verde también antes del arreglo: «Comprobar una copia» e «Importar copia» no aplican el tope de una entrada a las entradas que no cambian (`fittedForSave`), así que ya aceptaban esa copia. Fija la regla «la app nunca rechaza sus propias copias». Ningún test afirmaba `ENTRY_TOO_BIG` justo en el tope contando la marca, así que no cambió ninguna cifra.
- **Punto 1, el README:** «Entorno de firma» no tiene ninguna comparación con la 1.4.3. La línea va donde el README la hace: la prueba «2.0, versión y permisos» (`README.md:416`), y la descripción del script (`:117`) dice que ahora exige el atributo.
- **Punto 13 (a), un test existente:** `VaultPickerDialogsTest.theScreenClosesItOnTheVaultsState` fijaba `LaunchedEffect(active, activeState)` con una expresión regular. El encargo pide añadir `dialog` a las claves, así que su expresión admite ahora `(?:, dialog)?`. No está en la lista de cambios autorizados; es la consecuencia directa del arreglo pedido, y el test sigue comprobando lo mismo.
- **Punto 15 (b):** además del test pedido, `theScanFindsANameInATestTree` deja fijado el rojo sobre un árbol de prueba (un documento y una fuente cuentan; `build/`, `assets` y la línea de la excepción no).
- **Punto 16:** `eachCheckFailsOnAnAlteredCopy` deja fijado el rojo de los cuatro casos, con nueve alteraciones del YAML.
- **Puntos 6, 9 y 12 (b):** para ver el rojo por comportamiento y no por compilación, primero se declaró lo mínimo sin efecto (la constante y la propiedad de D-1, `attachmentCountFits` devolviendo siempre verdadero, `KDF_COST_MORE` vacía); el arreglo llegó después del rojo.
- **Paradas:** no se cumplió ninguna. No cambia ningún formato, `appJson`, versión, AAD ni marca de copia; ningún test con datos de la 1.x se puso en rojo; el manifiesto solo cambia en `taskAffinity`; `fitted()` sin argumento da lo mismo y `RedTeam20ListCostTest` (B 2 954 912 B, D 1,213x), `rt29_` y `FittedHistoryTest` siguen igual; la copia tardía de D-1 no se anuncia como la de la subida ni la borra A6, y `UPGRADE_COPYING` no sigue pasados los 2 minutos; el test de C-1 no encontró ningún nombre fuera de la excepción.

## 6. Textos nuevos y cambiados

Están también en `task-10c-inventory.md` («Ronda de arreglos de la auditoría completa»).

| Constante o sitio | Texto | Estado |
|---|---|---|
| `UPGRADE_AUTO_COPY_NOT_DONE` (`m/ui/screens/VaultUpgrade.kt:58`) | «La copia automática de antes del cambio al formato nuevo no ha terminado en 2 minutos: la carpeta tarda en contestar. Puedes exportar una copia a otro sitio o seguir sin copia.» | Nuevo (D-1) |
| `ATTACHMENTS_COUNT_WOULD_PASS` (`m/vault/Attachments.kt:52`) | «Con este adjunto, la bóveda tendría más adjuntos de los que caben en una copia (como mucho 1 998, también los de la papelera). Quita algún adjunto o vacía la papelera para añadir otro.» | Nuevo (fila 84) |
| `KDF_COST_MORE` (`m/vault/VaultsTexts.kt:135`) | «Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar textos y archivos. Si el archivo de la bóveda tiene un coste menor, sube a este la próxima vez que la abras con la contraseña maestra; nunca se baja. Las copias ya hechas, y los textos y archivos ya cifrados, guardan su coste. Lo cifrado con el coste alto solo se abre en móviles que den a la app al menos 171 MiB de memoria: muchos móviles económicos no llegan, así que úsalo solo si vas a abrir tus copias en móviles como este.» | Cambiado (A-2): antes, «… Lo ya cifrado guarda su coste. …», escrito en `SettingsScreen.kt` |
| `FOLDER_DELETE_TO_NONE` (`m/vault/OrganizeTexts.kt:63`) | «Sus entradas pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»» | Cambiado (RC-5, D-3): antes, «Sus entradas y sus subcarpetas se quedan sin carpeta» |
| `README.md:24` | «tras «Copiar usuario», si Clavero comparte la pantalla con otra app (pantalla dividida o ventana flotante), ofrece «Copiar contraseña» durante 30 segundos; a pantalla completa, salir de Clavero bloquea la bóveda y la oferta se acaba.» | Cambiado (B-4): antes, «al volver a la app ofrece…» |
| `README.md:407` | «Tras «Copiar usuario», con Clavero en pantalla dividida, tocar la otra app antes de 30 s: Clavero ofrece «Copiar contraseña». A pantalla completa, salir y volver: la bóveda está bloqueada y no se ofrece.» | Cambiado (B-4) |
| Notas de la 2.0 (`2026-11-15-clavero-2.0-notas.md:28`) | «tras «Copiar usuario», en pantalla dividida o en una ventana flotante, ofrece «Copiar contraseña» durante 30 segundos.» | Cambiado (B-4): antes, «al volver ofrece «Copiar contraseña»» |
| `README.md:117` y `:416` | La descripción de `check-manifest.sh` dice que exige `android:taskAffinity=""` en `MainActivity`; la prueba «2.0, versión y permisos» dice que el manifiesto solo cambia frente a la 1.4.3 en `versionName` y en `taskAffinity` (B-2, decisión del dueño), «que impide que otra app meta una pantalla suya en la tarea de Clavero» | Cambiado (B-2) |
| `scripts/check-manifest.sh` | «… sin android:taskAffinity="" (taskAffinity=…): otra app podría meter una pantalla suya en la tarea de Clavero (B-2)» y «… aparece N veces en el manifiesto (debe estar una vez)» | Nuevo, solo de compilación |

Sin cambio de texto, para su revisión: `NEEDS_MASTER_NOTICE` (no se enseña en la 2.0; el KDoc lo dice), `UPGRADE_COPYING` (con D-1 deja de salir pasados 2 minutos) y `ENTRY_TOO_BIG` (con E-1 ya no sale a quien no hizo más grande una entrada).

## 7. Filas de `pendientes.md`

Añadidas de la 85 en adelante, y puestas al día la 81, la 82 y la 84 (ver el archivo). La fila del riesgo con nombre de las bóvedas de la 1.4.3 con más de 1 998 adjuntos va como «Aceptado», y C-6, C-2 y C-3 como riesgos con nombre.

## 8. Suites enteras (punto 18)

Por separado y con el cerrojo, en este checkout:

1. `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: `BUILD SUCCESSFUL`; **2 601 tests, 0 fallos, 0 errores, 0 omitidos**; lint, 0 errores y 23 avisos (21 de antes y 2 nuevos, arreglados después en `539d2fd`: tras ese commit, `PlatformSourcesTest` 50/0 en la app y en jvm, y `lintDebug` 0 errores y 21 avisos). `check-manifest.sh` sobre ese APK de depuración: `OK`.
2. `./gradlew --stop`.
3. `./gradlew -p jvm-check test`: `BUILD SUCCESSFUL`; **2 536 tests, 0 fallos, 0 errores, 0 omitidos**.

`grep -rnE '^\s*@Ignore|^import org.junit.Ignore' app/src/test jvm-check`: vacío.

## Revisión experto-seguridad-cripto

Recorrí `references/auditoria.md` y `references/cripto-moderna.md` contra el diff de la ronda.

- **Primitivas y formatos:** ningún cambio en `m/crypto/`, `m/core/` ni en la escritura de `seguridad-vault`, `seguridad-backup`, `SG1:` o `.sgenc`. `m/vault/Backup.kt` no cambia (A-3 solo lo lee en un test, con el código de `a3ac942` copiado como función privada). E-1 solo cambia una medida (`m/vault/VaultLimits.kt:102-103`), en el sentido de aflojar el tope de una entrada; los 24 MiB siguen midiendo la bóveda entera con la marca. RC-1 no cambia lo que se guarda: `fitted()` sin argumento es el de siempre (`:187`), y `RedTeam20ListCostTest` da las mismas cifras.
- **Gestión de claves (A-1 + B-3, CWE-459):** la huella se olvida antes de quitar la bóveda del registro (`m/AppContainer.kt:752-760`), así que la peor muerte del proceso deja la bóveda en la lista y sin huella. `forget` escribe en el acto (`edit(commit = true)`) y borra el archivo de preferencias de esa bóveda, nunca el de la primera, que guarda lo del StrongBox (`m/android/BiometricVault.kt:193-201`). `forgetOrphanVaults` cierra el único hueco que deja una muerte entre el registro y los ajustes (`m/AppContainer.kt:785-792`). Los comentarios ya no dicen que una clave olvidada «no abre nada»: envuelve la clave de datos, que abre también las copias de antes.
- **Plataforma:** B-2 (CWE-1021): `taskAffinity=""` solo en `MainActivity` (`app/src/main/AndroidManifest.xml:28`), comprobado en el APK por `check-manifest.sh`. B-1 (CWE-940): la navegación ya no ve el *intent* de quien abre la app (`m/MainActivity.kt:39`); sin `onNewIntent` ni `handleDeepLink`.
- **Rutas (C-5, CWE-22):** `fileOf` falla cerrado (`m/vault/Attachments.kt:96`); §4 lista los llamadores.
- **Disponibilidad (D-1, CWE-1088):** la espera de la subida está acotada a 120 s (`m/vault/PendingUpgrade.kt:102`); solo se corta el `await`, la copia sigue en el ámbito de `AutoBackup`. La copia tardía queda apuntada como las de antes de importar y A6 no la borra (dos tests).
- **Orden de las copias (RC-2, E-2, CWE-362 sin efecto de seguridad):** se aplaza, no se salta (`m/vault/AutoBackup.kt:835`, `defer`).
- **Fila 84:** el tope de número se mira antes de escribir el archivo del adjunto (`m/vault/VaultStore.kt:1381`), así que no queda nada a medias.
- **Cadena de firma (C-7) y regla de los nombres (C-1):** fijadas por tests que leen el YAML y los documentos; C-3 sigue siendo el riesgo de mayor impacto, aceptado por el dueño.
- **Veracidad (CWE-451 en sentido débil, CWE-1116):** A-2, B-4, D-1, RC-5 y los KDoc de E-3 y RC-6 dicen lo que hace el código.
- **Secretos y registros:** ningún registro nuevo; los textos nuevos son constantes; ningún secreto nuevo en memoria (`TwoStepCopy` sigue guardando solo el id).

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** B-1 va en `onCreate`, antes de `setContent` (`m/MainActivity.kt:37-39`); `singleTop` y `configChanges` no cambian. B-4 usa el camino de la pantalla dividida que ya existía (`m/AppContainer.kt:217-227`), detrás de la guarda de «ya fuera», así que un segundo aviso del sistema no vuelve a marcar. D-2 cierra en el mismo paso un diálogo abierto con la bóveda ya bloqueada (`m/ui/screens/VaultPickerScreen.kt:202`, `:223`).
- **Concurrencia:** D-1 usa `withTimeoutOrNull` dentro del `NonCancellable` de `afterUnlock`, que crea su propio hijo y solo cancela el `await` (`m/vault/PendingUpgrade.kt:102`); el test `aFolderThatNeverAnswersStillLeavesTheChoice` mira justo antes y justo después del tope con tiempo virtual. RC-2 aplaza dentro del `lock`, sin cerrojos nuevos. `BiometricVault.forget` va en la misma fila que las demás operaciones de la huella (`KeyOpsQueue`) y no se cancela con quien la llama.
- **Interfaz:** D-4 añade «ocupado» y borra el rechazo anterior (`m/ui/screens/VaultOrganize.kt:349-388`), como `NameDialog`. Los textos nuevos son constantes con su test. Ningún `rememberSaveable` nuevo.
- **Rendimiento:** RC-1 recorta con el mismo `fitted`, con otro objetivo (el test cerca de los 24 MiB tarda unos 4,5 s); el test de C-1 recorre el repositorio en 0,6 s. Los tests nuevos no usan `sleep`: *latches*, `CompletableDeferred`, tiempo virtual y `withTimeout`.
- **Compilación:** el test de C-1 declara `docs/` y la tabla de la skill como entradas de las dos tareas `test` (`jvm-check/build.gradle.kts:69-71`, `app/build.gradle.kts`, bloque `tasks.withType<Test>`). `deleteSharedPreferences` pide API 24 y el `minSdk` es 26.
- **Para el móvil:** B-2 y B-1 (§10 de la auditoría), la oferta de B-4 en pantalla dividida, el diálogo de D-1 a los 2 minutos con una carpeta lenta y «Añadir adjunto» con muchos adjuntos (fila 84).

## Textos para el Abogado Digital (revisión final)

- **Nuevos:** `UPGRADE_AUTO_COPY_NOT_DONE` (D-1) y `ATTACHMENTS_COUNT_WOULD_PASS` (fila 84), con el texto de §6.
- **Cambiados:** `KDF_COST_MORE` (A-2; no debe decir nunca que subir el coste protege las copias hechas antes), `FOLDER_DELETE_TO_NONE` (RC-5; puede retocarse si nombra los dos sitios y no dice que las subcarpetas desaparecen), las tres frases de B-4 (README `:24` y `:407`, notas `:28`; la ficha de Play no puede prometer la oferta a pantalla completa) y la línea de B-2 del README (`:416`, con la de `:117`).
- **Sin cambio, para su revisión:** `NEEDS_MASTER_NOTICE` (no se enseña en la 2.0 y diría algo falso si se viera; quitarlo exige código, en su ronda si lo pide), `UPGRADE_COPYING` y `ENTRY_TOO_BIG`.
