# Tarea 8: plan de fusión, copia previa y guardado único — informe

**Estado:** DONE_WITH_CONCERNS. Commit `5efe79a` («1.4.0: plan de fusión, copia previa y guardado único»), subido a `ccr-a1a6eabb-765f9g`.

## Qué se ha hecho

- **`imports/MergePlan.kt` (nuevo):**
  - `DuplicateChoice`, `Duplicate`, `MergePlan`, `ApplyResult` (`Done`, `Stale`, `TooBig`) con la forma del brief.
  - `planMerge`: compara con hash maps, en tiempo lineal.
    - LOGIN: título (sin espacios, sin mayúsculas), usuario (sin espacios) y host. Si a un lado le falta la URL, basta con título y usuario.
    - TOTP: la clave, con la regla de `totpImportEntries` (`totpKeyOf`).
    - NOTE: título y texto.
    - Solo se comparan entradas del mismo tipo, así que `IDENTITY`, `CONTACT` y `SEED` nunca coinciden.
    - El archivo se compara primero consigo mismo (`repeatedInFile`).
  - `importTags`: primero la etiqueta de origen y después cada carpeta pasada por `normalizeTags`, con las comas cambiadas por espacios. Como máximo 10; las carpetas del final se quitan antes que la de origen.
  - `mergedEntries`: las entradas existentes quedan en su sitio. «Reemplazar» cambia la entrada en el mismo sitio; las nuevas van al final, en el orden del archivo, con un id nuevo y `now` como fecha. Si dos duplicados apuntan a la misma entrada, se aplican uno tras otro.
  - `replaceWith`: cumple la regla de la spec.
    - Conserva el id, el tipo, `createdAt`, el favorito y los adjuntos.
    - Un campo vacío o en blanco del archivo nunca borra uno de la bóveda.
    - Notas: debajo de las de la bóveda, tras «Importado el D-M-AAAA:».
    - Etiquetas: la unión. Las propias nunca se pierden.
    - Contraseña: pasa por `withNewPassword`.
    - Una entrada personal, o una de otro tipo, se devuelve sin cambios.
  - `estimatedVaultFileBytes` y `estimatedOverBytes`: la estimación de la vista previa, `ceil(plainJsonBytes*4/3)+4096`. Si la cota inferior de caracteres ya pasa del límite, se devuelve esa cota y no se codifica nada. `plainCharsLowerBound` y `utf8Length` son helpers internos.
- **`imports/ImportFlow.kt` (nuevo):** `ImportFlow.confirm()` hace la copia y, solo si da `Saved`, llama a `apply()`.
  - Si la copia falla o lanza una excepción, devuelve `CopyFailed`, con un texto fijo y nunca el de la excepción.
  - Si se cancela, relanza la cancelación.
  - Si `apply` no da `Done`, devuelve `NotApplied`.
  - No guarda ningún estado.
- **`vault/VaultStore.kt`:**
  - `modify` recibe un `accept` opcional: ve el `VaultFile` cifrado con el mutex tomado, y si devuelve `false` no se guarda nada.
  - `applyImport` es un miembro, no una extensión, porque necesita `modify`, que es privado. Hace todo dentro del mutex y en la misma sección crítica que la escritura (`:678-705`):
    1. comprueba la revisión (si ha cambiado, `Stale`);
    2. cuenta los adjuntos más dos frente a `BACKUP_MAX_ENTRIES`;
    3. calcula `mergedEntries`;
    4. comprueba la cota inferior antes de cifrar;
    5. llama a `checkCanHold`;
    6. después de `VaultCrypto.write`, mide exactamente el archivo (≤ `BACKUP_MAX_ENTRY_BYTES`) y la copia entera (2 + json + archivos de adjuntos ≤ `BACKUP_MAX_TOTAL_BYTES`);
    7. una sola escritura con `commit`, que es `NonCancellable`.
  - Un plan que no cambia nada da `Done(0, 0)` y no escribe.
- **`vault/AutoBackup.kt`:**
  - Nuevos: `CopyResult`, `IMPORT_COPY_PREFIX` (`seguridad-antes-de-importar-`), las expresiones regulares de nombre y temporal, y `AutoBackupResult.importCopies`.
  - Parámetros nuevos del constructor, con valor por defecto: `importCopies` y `onImportCopy`.
  - `copyBeforeImport()` corre en el `scope` de la clase y bajo su `lock`, con `copyTo` (temporal y renombrado, o escritura directa si no se puede renombrar) y la comprobación de generación de CADENAS-3.
  - La rotación lista la carpeta una sola vez con `seguridad-`:
    - nunca rota las copias de importación;
    - solo las borra, por id, si son objetivos de A6;
    - borra los temporales de importación que quedan sueltos;
    - el aviso de «copias sin apuntar» las cuenta por su nombre;
    - devuelve la lista podada.
- **`settings/Settings.kt`:**
  - `importCopies`, `importCopiesFolder`, `currentImportCopies` y `withImportCopy` (comprueba la carpeta y la generación).
  - `recording` añade las copias de importación de la misma carpeta a `autoBackupReplaceTargets` cuando se guarda la petición, y aplica la lista podada.
  - `forgettingAutoBackupCopies` también las olvida (bóveda nueva o copia importada).
- **`settings/SettingsStore.kt`:** el `undo` de `forgetAutoBackupCopiesForImport` las restaura.
- **`AppContainer.kt`** (no estaba en la lista del brief, pero hace falta conectarlo):
  - pasa `importCopies` y `onImportCopy` (`recordImportCopy` lee la generación dentro del bloqueo de los ajustes);
  - la lista `oldCopies` de `MasterChanged` incluye las copias de importación, para que un reemplazo pedido con la bóveda abierta también se las lleve.
- **`vault/EntryQuery.kt`:** `normalizeTags` corta por puntos de código (`takeCodePoints`). Es la decisión 2.
- **`vault/TotpImport.kt`:** `keyOf` pasa a `internal fun totpKeyOf`, la misma regla en los dos sitios.
- **`imports/ImportTexts.kt`:** `importedOnHeading` y los seis textos `IMPORT_COPY_*`.

## Tests y TDD

- **RED** (antes de implementar): `./gradlew :app:testDebugUnitTest --tests '…MergePlanTest' --tests '…ImportFlowTest' --tests '…VaultStoreImportTest' --tests '…AutoBackupImportCopyTest' --tests '…EntryQueryTest'` falló al compilar: `Unresolved reference 'CopyResult'`, `'ApplyResult'`, `'ImportFlow'`, `'ImportOutcome'`, `'IMPORT_COPY_FAILED'`…
- **GREEN** (mismo comando): MergePlanTest 20/20, ImportFlowTest 5/5, VaultStoreImportTest 9/9, AutoBackupImportCopyTest 11/11 y EntryQueryTest 7/7, todos en verde.
- **Prueba de mutación:** se quitó la comprobación de la revisión y el añadido a los objetivos de A6. Fallaron justo `applyRefusesAStalePlan` y `theChangeOfMasterPasswordReplacesItWhenTheFolderMatches`. Después se restauró el código.
- **Verificación completa** (dos veces; la última, después del último cambio):
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, 1621 tests con 0 fallos y 0 errores (es la ronda inicial, en `5efe79a`; las cifras de cada ronda de arreglos están en su sección), y lint con los 16 avisos conocidos (14 de `libs.versions.toml`, `ObsoleteSdkInt` y `DiscouragedApi`). Ningún aviso `w:` de Kotlin.
  - `gradle -p jvm-check test --no-daemon --max-workers=1`: correcto, 1615 tests con 0 fallos.
- **Tests nuevos:**
  - **MergePlan:** duplicados de LOGIN, TOTP y NOTE; repetidos dentro del archivo; SKIP, KEEP_BOTH, cambios por elemento y la regla de REPLACE (historial, conservar campos, encabezado de las notas, unión de etiquetas, TOTP); IDENTITY y CONTACT intactos; etiquetas (comas, `a/b`, corte a 24 por puntos de código, emoji, tope de 10); `newTags`; `secondImportOfSameFileAddsNothing`; texto guardado tal cual; fórmula de la estimación, que cubre el archivo real.
  - **VaultStoreImport:** una sola escritura; `Stale` por cambio y por bloqueo; todo o nada con IOException; `TooBig` exacto (escapes JSON) y por cota inferior; demasiados adjuntos (`overFiles`); `fingerprintStaysEnabled` (misma DEK y misma `wrappedKey`, y `unlockWithKey` funciona); un plan sin cambios no escribe.
  - **AutoBackupImportCopy:** nombre y temporal; siguiente segundo libre; en `importCopies` y no en `autoBackupCopies`; nunca rotada; objetivos de A6 (con la bóveda bloqueada y abierta, y poda); otra carpeta queda fuera; aviso de sin apuntar; `copyBeforeImportWithoutRenameWritesFinalName`; sin carpeta da `null`; fallos (escritura, carpeta y bóveda bloqueada) sin dejar archivo; una bóveda nueva olvida la lista.
  - **ImportFlow:** copia `Failed` o con excepción, sin `apply`; `Stale` y `TooBig` dan `NotApplied`; el orden; muerte del proceso entre la copia y el guardado, con la bóveda sin cambios en disco.

## Preocupaciones (para el controlador)

1. **El tope de cantidad (decisión 1).** La spec dice que `BACKUP_MAX_ENTRIES` (2 000) cuenta los adjuntos más dos, no las contraseñas («El límite de 2 000 es de adjuntos, no de contraseñas»), y `writeBackup` comprueba `attachments.size + 2`. Un tope de entradas de la bóveda en 2 000 rechazaría importaciones que caben en una `.sgbak`, como 5 000 contraseñas. Implementé el tope como en la spec y en el código: adjuntos + 2 ≤ 2 000, comprobado antes de construir la lista. El tamaño queda acotado con la cota inferior antes de cifrar y con la medida exacta después. Si la decisión era de verdad «entradas ≤ 2 000», es un cambio de una línea en `VaultStore.kt:680`.
2. **Los textos de A6 ya no lo dicen todo.** Ahora A6 también borra las copias de antes de importar (decisión de la spec). Los textos fijados por tests y revisados por el Abogado Digital solo hablan de las automáticas: `AUTO_BACKUP_REPLACED` (`AutoBackup.kt`), `PASSWORD_CHANGE_REPLACE_COPIES` y `AUTO_BACKUP_REPLACE_DROPPED` (`ToolTexts.kt`), `autoBackupUnlistedText` («con nombre de copia automática»), la pista de `SettingsScreen.kt:284` («Solo se borran las copias automáticas más antiguas (seguridad-auto-…), nunca otros archivos») y `docs/privacidad.html`. No los cambié: rompería las pruebas fijadas (`FinalLegalReviewTest`, `ToolTextsTest`) y la spec manda los textos de A6 a la revisión final del Abogado Digital. Propuesta abajo, en «Textos para el Abogado Digital». Hace falta que el controlador decida, porque si no, el texto afirmaría algo inexacto sobre el código.
3. **Desviaciones pequeñas de la interfaz:**
   - `TooBig` tiene `overFiles: Int = 0`, porque si no, no hay forma de decir «sobran archivos».
   - `mergedEntries` y `replaceWith` tienen un parámetro `zone` con valor por defecto, para que los tests den una fecha fija.
   - `applyImport` es un miembro de `VaultStore`, no una extensión: se llama igual.
   - `CopyResult.Saved.where` es el nombre del archivo, nunca una URI.
   - Un fallo de escritura en `apply` se relanza desde `confirm()`, porque `ImportOutcome` no tiene ese caso; la tarea 9 lo muestra.
4. **Si la bóveda se sustituye mientras se escribe la copia previa** (cambia `generation`), el resultado es `Failed(IMPORT_COPY_FAILED)` y esa copia se queda en la carpeta sin apuntar, como en CADENAS-3.
5. **Un plan que no cambia nada da `Done(0, 0)` sin escribir**, para no hacer una copia automática inútil.

## Revisión propia

- Todo o nada: una sola llamada a `VaultCrypto.write` y un solo `commit` (`VaultStore.kt:608`, `:350`). Una cancelación antes de `commit` limpia la clave y no escribe nada; `commit` es `NonCancellable`.
- No hay validar y después reabrir: la revisión se comprueba dentro de `modify`, con el mutex tomado (`VaultStore.kt:678`).
- No hay asignaciones enormes antes de las comprobaciones: los adjuntos se cuentan primero (`:681`) y la cota inferior va antes de cifrar (`:687`).
- Las coincidencias no son O(n²): `Matcher` (`MergePlan.kt:260`).
- Las cadenas de código existentes de AutoBackup y de A6 no cambian, salvo la lista única de `seguridad-` (`AutoBackup.kt:629`; el número de llamadas a `list` es el mismo, así que los tests de `failListAt` pasan).

## Revisión experto-seguridad-cripto

- **TOCTOU (decisión b):** la revisión se compara dentro del mutex, en la misma sección crítica que la escritura (`VaultStore.kt:678`, `:599-620`). Con la bóveda bloqueada da `Stale`. Test: `applyRefusesAStalePlan` (comprobado con la mutación).
- **Atomicidad:** escritura en temporal y renombrado atómico (`core/AtomicFile.kt:15-23`). Si la escritura lanza, los bytes y la revisión no cambian (`applyIsAllOrNothing`). Si el proceso muere entre la copia y el guardado, la bóveda queda igual (`ImportFlowTest.processDeathBetweenCopyAndApplyLeavesTheVaultAsItWas`).
- **Agotamiento de recursos (decisión a):** los adjuntos se cuentan antes de nada; la cota inferior de caracteres (`MergePlan.kt:202`) rechaza antes de cifrar; la medida exacta cubre el archivo cifrado y la copia entera (`VaultStore.kt:697-703`). La vista previa no codifica una importación enorme (`MergePlan.kt:187`). La medida UTF-8 no reserva memoria (`utf8Length`).
- **Datos hostiles (decisión c):** el texto se guarda tal cual (`importedTextIsStoredVerbatim`). Solo se comparan copias normalizadas. `urlHost` trabaja con cadenas, sin un analizador que pueda lanzar excepciones o resolver nombres (`MergePlan.kt:244`). Nada se interpreta como enlace ni como Intent.
- **Claves y huella (A11, CRY-A02):** la clave de datos no cambia y `wrappedKey` y `salt` tampoco (`fingerprintStaysEnabled`). No se llama a nada biométrico ni a la marca del reloj (`applyImport` no llama a `onClockReset` ni a `onClockSeen`). La clave copiada se limpia en todos los caminos de `modify`.
- **Secretos:** las contraseñas sustituidas pasan por `withNewPassword` (`MergePlan.kt:181`). No hay logs. Los textos de error son fijos y nunca muestran la excepción (`ImportFlow.kt:30-35`, `AutoBackup.kt:401-431`). `ImportFlow` no guarda estado.
- **A6:** las copias de importación entran como objetivos solo si es la misma carpeta (`Settings.kt:163`). Se borran por id y nunca por nombre (`AutoBackup.kt:642`). El aviso cuenta por nombre (`:663`). Se olvidan con una bóveda nueva o importada (`Settings.kt:106`) y la lista se restaura si la importación no se guarda (`SettingsStore.kt`). Con la generación comprobada, nunca se apunta una copia de una bóveda sustituida (`AutoBackup.kt:408-411`, `Settings.kt:114`).
- **Formatos y primitivas:** ningún formato nuevo (`.sgbak` es el de siempre), ninguna dependencia, permiso, host ni primitiva nueva.
- **Sin código copiado:** todo está escrito a partir de la spec. No se ha mirado ni adaptado ningún importador ajeno.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - `copyBeforeImport` corre en `scope.async` con el `lock` de AutoBackup (`AutoBackup.kt:399`): nunca se solapa con una rotación, así que el borrado de los temporales sueltos no puede tocar una copia que se está escribiendo, y si la pantalla se va, no se corta entre el renombrado y la lista.
  - `applyImport` se ejecuta en `compute` con el mutex tomado y `commit` es `NonCancellable` (`VaultStore.kt:350`).
  - Las variables `result` y `attachmentRefs` se escriben de forma secuencial dentro del mutex.
- **Muerte del proceso:** la copia previa es durable antes del guardado. Si se muere entre los dos, sobra la copia y la bóveda sigue igual (test). Los ajustes se guardan con escritura atómica y la lista de copias de importación sobrevive a un reinicio.
- **Rendimiento:**
  - el plan es lineal;
  - las coincidencias usan hash maps;
  - la carpeta se lista una vez por rotación;
  - la cota inferior evita cifrar 16 MB para nada;
  - las longitudes de los adjuntos se leen en `io` (`VaultStore.kt:699`).
- **Ciclo de vida y estado:** no hay interfaz. `ImportFlow` no guarda estado y nada va a `rememberSaveable` ni a un `Bundle`.
- **API:** `modify` mantiene la sintaxis de lambda al final para todos los que ya lo llamaban (`accept` va primero, con valor por defecto; `VaultStore.kt:599`). Los parámetros nuevos de AutoBackup tienen valor por defecto, así que los tests existentes compilan sin cambios.

## Textos para el Abogado Digital (revisión final)

Textos nuevos (`imports/ImportTexts.kt`):
- «Importado el D-M-AAAA:» (`importedOnHeading`; por ejemplo, «Importado el 4-11-2026:»).
- «No se pudo hacer la copia antes de importar: la app ya no tiene permiso para usar la carpeta de la copia automática. Vuelve a elegirla en Ajustes. No se ha importado nada.»
- «No se pudo hacer la copia antes de importar: no se encuentra la carpeta de la copia automática. Vuelve a elegirla en Ajustes. No se ha importado nada.»
- «No se pudo escribir la copia antes de importar en la carpeta de la copia automática. ¿Queda espacio? No se ha importado nada.»
- «No se pudo hacer la copia antes de importar: un adjunto falta o está dañado, o la bóveda no cabe en una copia. Exporta la bóveda a mano para ver el motivo. No se ha importado nada.»
- «La bóveda se ha bloqueado antes de hacer la copia. No se ha importado nada.»
- «No se pudo hacer la copia antes de importar. No se ha importado nada.»
- El nombre de archivo `seguridad-antes-de-importar-AAAAMMDD-HHMMSS.sgbak` (lo ve el usuario en su carpeta).

Textos de A6 que el código ya no cubre del todo (preocupación 2; **no cambiados**, propuesta para el controlador y el Abogado Digital):
- `PASSWORD_CHANGE_REPLACE_COPIES`: «Hacer una copia nueva y borrar las automáticas anteriores» → propuesta: «Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)».
- `AUTO_BACKUP_REPLACED`: «…se borraron las copias automáticas de antes del cambio de contraseña…» → propuesta: «…se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña: las que la app tenía apuntadas en esta carpeta. …».
- `AUTO_BACKUP_REPLACE_DROPPED`: «Las copias automáticas anteriores que ibas a borrar…» → propuesta: «Las copias anteriores que ibas a borrar no se borraron: siguen en su carpeta.».
- `autoBackupUnlistedText`: «con nombre de copia automática» → propuesta: «con nombre de copia de la app (automática o de antes de importar)».
- La pista de `SettingsScreen.kt:284`: «Solo se borran las copias automáticas más antiguas (seguridad-auto-…), nunca otros archivos de la carpeta.» → propuesta: «…más antiguas (seguridad-auto-…) y, si lo pides tras cambiar la contraseña maestra, las de antes de importar (seguridad-antes-de-importar-…); nunca otros archivos de la carpeta.».
- `docs/privacidad.html` (frase de A6 fijada en `FinalLegalReviewTest`): la misma ampliación, en la tarea 12.

---

## Ronda de arreglos 1 (FIX_BASE 5efe79a → `7782807`)

Hallazgo → cambio → test:

- **I-1, cota antes de cifrar con escapes JSON.**
  - Cambio: `plainCharsLowerBound` pasa a ser `escapedTextBytes`, que suma `jsonEscapedLength` de los campos de texto: `"`, `\` y los escapes cortos cuentan 2; los demás controles por debajo de U+0020, 6; el resto, su longitud UTF-8. No construye nada (`MergePlan.kt`). Se usa en `estimatedVaultFileBytes` y en el control previo de `applyImport`, antes de codificar.
  - Tests:
    - `MergePlanTest.theEscapedLengthIsTheJsonOne`: igual a lo que escribe `appJson`, con `"`, `\`, `\b`, `\f`, `\n`, `\r`, `\t`, `\u0001`, `\u001F`, ñ, €, 😀 y un surrogate suelto; además, cota inferior cercana.
    - `MergePlanTest.aHeavilyEscapedImportIsTooBigWithoutEncodingIt`: 75 × 63 000 `\u0001`; la estimación sale de la fórmula sobre los bytes contados.
    - `VaultStoreImportTest.aHeavilyEscapedImportIsRefusedBeforeEncoding`: `TooBig` con exactamente la cifra del control previo (la medida exacta daría otra) y sin `.tmp`.
    - `tooBigIsRefused`: rehecho para el camino exacto (20 000 notas pequeñas que pasan el control previo y no caben cifradas).
- **M-1, etiquetas del 2FA.**
  - Cambio: si el emisor o la cuenta del archivo están en blanco, se conservan los de la bóveda.
  - Test: `replaceTakesTheCodeOfATotp` comprueba la cuenta, el emisor y el caso con valores.
- **M-2, REPLACE repetido.**
  - Cambio: las notas no se añaden si las de la bóveda ya terminan con ellas. `replaceWith` devuelve la propia entrada cuando solo cambiaría `updatedAt`.
  - Tests: `MergePlanTest.replacingTwiceWithTheSameFileChangesNothingTheSecondTime`; `VaultStoreImportTest.aSecondReplaceOfTheSameFileWritesNothing` (`Done(0,0)`, mismos bytes, misma revisión); `replaceNeverEmptiesAFieldOfTheVault` pasa a `assertSame`.
- **M-3, petición anterior a la 1.3.0.**
  - Cambio: se quita `+ importCopies()` del camino sin objetivos (`AutoBackup.kt`).
  - Test: `AutoBackupImportCopyTest.aRequestWithoutTargetsKeepsTheImportCopies`.
- **M-4 (R16), instantánea del plan.**
  - Cambio: `VaultStore.importSnapshot(): VaultSnapshot?` (entradas y revisión leídas con el mutex) y `VaultStore.planImport(read)` en `MergePlan.kt`, que es el camino documentado (la KDoc de `planMerge` lo nombra).
  - Test: `VaultStoreImportTest.aPlanFromTheSnapshotIsNeverPairedWithANewerRevision`. Un dispatcher retiene el guardado de una edición con el mutex tomado; el plan espera, y sale con las entradas nuevas y la revisión nueva, y se aplica. Bloqueada: `null`.
- **M-5 (R16), fallo al guardar.**
  - Cambio: `ImportOutcome.SaveFailed(where, message = IMPORT_SAVE_FAILED)`; `confirm()` captura `Exception` y relanza la cancelación.
  - Test: `ImportFlowTest.aSaveThatFailsIsSaidWithAFixedText` (el texto de la excepción no llega).
- **M-6, bóveda sustituida durante la copia.**
  - Cambio: `IMPORT_COPY_VAULT_CHANGED`.
  - Test: `AutoBackupImportCopyTest.aVaultReplacedDuringTheCopyIsSaidAsSuch` (`destroy` durante la subida: el texto, la copia sigue en la carpeta y no se apunta).
- **M-7, KDoc de `AppContainer`.**
  - Cambio: `recordImportCopy` va debajo de `recordAutoBackup`, cada una con su KDoc.
- **M-8, espacios dobles.**
  - Cambio: un grupo de espacios pasa a ser uno antes de `normalizeTags`.
  - Test: `foldersBecomeTagsWithTheOriginTag` («trabajo casa», también con `,,\t`).
- **M-9, copia automática de más.**
  - Cambio: `copyTo` y `fill` aceptan `defer = true`; `importCopyNow` pasa `false`.
  - Test: `AutoBackupImportCopyTest.aLockDuringTheCopyMakesNoAutomaticCopyLater`.
- **M-10, KDoc.**
  - Cambio: ahora dice «no clock reset, as the mark only rises, as with any save; no biometric call». Corrige también la frase de este informe: `applyImport` no llama a `onClockReset`, y `commit` llama a `onClockSeen` como cualquier guardado.
- **R15, textos de A6.**
  - Cambio: nuevos `AUTO_BACKUP_REPLACED`, `PASSWORD_CHANGE_REPLACE_COPIES`, `AUTO_BACKUP_REPLACE_DROPPED` y `autoBackupUnlistedText`, y la pista de `SettingsScreen.kt`, con la redacción propuesta. Fijados en `FinalLegalReviewTest`, `ToolTextsTest` y `AutoBackupF3Test`. El anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md` se actualizó al pie de la letra, porque `ToolTextsTest` lo compara en los dos sentidos.
  - Siguen siendo ciertos tras M-3: la petición antigua no tiene copias de importación anteriores al cambio. `docs/privacidad.html` queda para la tarea 12.

**Comandos y salida:**
- Tests de la ronda: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.imports.*' --tests 'app.seguridad.vault.*' --tests 'app.seguridad.tools.*' --tests 'app.seguridad.settings.*'` dan 1256 tests sin fallos. La primera pasada tuvo 4 fallos: el anexo de textos sin actualizar (3) y `replaceNeverEmptiesAFieldOfTheVault` (M-2). Se arreglaron.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, 1631 tests sin fallos, lint con los 16 avisos conocidos (`NewerVersionAvailable` 7, `GradleDependency` 6, `AndroidGradlePluginVersion` 1, `DiscouragedApi` 1 y `ObsoleteSdkInt` 1) y ningún `w:`.
- `gradle -p jvm-check test --no-daemon --max-workers=1`: correcto, 1626 tests sin fallos.

### Revisión experto-seguridad-cripto (ronda 1)
- Agotamiento: el control previo ya no se queda corto con los escapes (`MergePlan.kt` `jsonEscapedLength` / `escapedTextBytes`, `VaultStore.kt` `applyImport`). Un archivo hostil de unos 5 MB de controles se rechaza sin codificar ni cifrar (test).
- TOCTOU: instantánea atómica (`VaultStore.importSnapshot`), así que el plan y la comprobación de la revisión van sobre la misma lista.
- Veracidad: los textos de A6 dicen ahora lo que borra el código (objetivos automáticos y de importación de la carpeta). M-6 y M-5 tienen textos propios y fijos. No se muestra nunca el texto de una excepción (`ImportFlow.confirm`).
- No se pierden datos: el emisor y la cuenta del 2FA se conservan (M-1). Las copias de importación de una petición antigua se quedan (M-3).

### Revisión Ingeniero Móvil Senior (ronda 1)
- Memoria: ninguna asignación grande antes de rechazar. El JSON solo se construye cuando la cota cabe (6 MB como máximo).
- Concurrencia: `importSnapshot` con el mutex; `copyBeforeImport` sigue en el `scope` y bajo el `lock`; la cancelación se relanza en `ImportFlow`.
- Planificación: un bloqueo durante la copia previa no deja una copia automática pendiente (M-9). Un REPLACE sin cambios no escribe ni dispara una copia (M-2).

### Textos para el Abogado Digital (revisión final), ronda 1
Textos nuevos (`imports/ImportTexts.kt`):
- `IMPORT_COPY_VAULT_CHANGED`: «La bóveda se ha sustituido o borrado mientras se hacía la copia, así que no se ha importado nada. La copia que se hizo sigue en la carpeta.»
- `IMPORT_SAVE_FAILED`: «No se pudo guardar la importación: la bóveda no ha cambiado. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo.»

Textos fijados que cambian (R15):
- `PASSWORD_CHANGE_REPLACE_COPIES` (`tools/ToolTexts.kt`): «Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)».
- `AUTO_BACKUP_REPLACED` (`vault/AutoBackup.kt`): «Se hizo una copia nueva y se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña: las que la app tenía apuntadas en esta carpeta. Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.»
- `AUTO_BACKUP_REPLACE_DROPPED` (`tools/ToolTexts.kt`): «Las copias anteriores que ibas a borrar no se borraron: siguen en su carpeta.»
- `autoBackupUnlistedText(1)`: «En la carpeta queda 1 copia con nombre de copia de la app (automática o de antes de importar) que la app no tenía apuntada (por ejemplo, de antes de la versión 1.2.1 o de otra bóveda): no se ha borrado y puede que se abra con una contraseña anterior. Si no la necesitas, bórrala a mano.»
- `autoBackupUnlistedText(n)`: «En la carpeta quedan n copias con nombre de copia de la app (automática o de antes de importar) que la app no tenía apuntadas (por ejemplo, de antes de la versión 1.2.1 o de otra bóveda): no se han borrado y puede que se abran con una contraseña anterior. Si no las necesitas, bórralas a mano.»
- La pista de `ui/screens/SettingsScreen.kt` (copia automática): «Cada copia va cifrada con la contraseña maestra que tenía la bóveda al hacerla: si la cambias, las copias anteriores se siguen abriendo con la antigua. Solo se borran las copias automáticas más antiguas (seguridad-auto-…) y, si lo pides tras cambiar la contraseña maestra, las de antes de importar (seguridad-antes-de-importar-…); nunca otros archivos de la carpeta.»
- Se actualizó a la par el anexo de textos de la 1.3.0 (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`). `docs/privacidad.html` y la línea de pruebas del README de la 1.3.0 quedan para la tarea 12.

---

## Ronda de arreglos 2 (FIX_BASE 7782807 → `b0779e1`)

- **N-1.**
  - Cambio: un comentario en `AutoBackup.kt` (camino de la petición sin objetivos, en `attempt`). Explica que una petición sin objetivos se guardó antes de la 1.3.0, que toda copia de importación (1.4.0) se hizo después, y que por eso no existe ninguna de antes del cambio y `AUTO_BACKUP_REPLACED` sigue siendo cierto.
  - Test: el de M-3, `aRequestWithoutTargetsKeepsTheImportCopies`, ya lo cubre.
- **N-2.** Elegí cambiar el texto para que diga solo lo que es cierto en todos los caminos.
  - El texto: `IMPORT_SAVE_FAILED` pasa a «No se ha podido terminar la importación. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo.». Ya no dice que la bóveda no ha cambiado. El espacio se pregunta, no se afirma. «La copia sí se hizo» es cierto siempre: `SaveFailed` solo llega después de `CopyResult.Saved`.
  - La KDoc: se corrigió la de `ImportFlow` y la de `ImportOutcome.SaveFailed`. Ahora dicen que casi siempre es un disco lleno antes del renombrado atómico, sin nada guardado, y que no se puede descartar un fallo después del renombrado. Ya no está la frase «throws before its atomic rename».
  - Los tests, por camino:
    - `ImportFlowTest.everySaveFailureGivesTheSameTrueText`: `IllegalStateException` (como el rechazo de `checkCanHold`), `IOException` y `RuntimeException` dan todas el mismo `SaveFailed`; el texto no contiene «no ha cambiado».
    - `ImportFlowTest.aFullDiskIsASaveFailureAndTheVaultIsAsItWas`: con el almacén real y el guardado roto, da `SaveFailed` y los bytes no cambian.
    - Un fallo después del renombrado (`opened` no puede volver a leer lo que acaba de escribir) no se puede provocar sin modificar el almacén. El texto ya no afirma nada que ese caso haga falso.
- **N-3.**
  - Cambio: en el informe, la línea de «1621 tests» dice ahora que es la cifra de la ronda inicial (`5efe79a`). La de la ronda 1 era 1631 y la de esta ronda va abajo.

**Comandos y salida:**
- Tests de la ronda: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.imports.*' --tests 'app.seguridad.vault.*' --tests 'app.seguridad.tools.*'` dan 1215 tests sin fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, 1633 tests sin fallos, lint con los 16 avisos conocidos (`NewerVersionAvailable` 7, `GradleDependency` 6, `AndroidGradlePluginVersion` 1, `DiscouragedApi` 1 y `ObsoleteSdkInt` 1) y ningún `w:`.
- `gradle -p jvm-check test --no-daemon --max-workers=1`: correcto, 1628 tests sin fallos.

### Revisión experto-seguridad-cripto (ronda 2)
- Veracidad: `IMPORT_SAVE_FAILED` (`imports/ImportTexts.kt`) es cierto en todos los caminos que llegan a `SaveFailed` (`imports/ImportFlow.kt`, `confirm`). No muestra nunca el texto de una excepción. `AUTO_BACKUP_REPLACED` queda protegido por el comentario de N-1 en `vault/AutoBackup.kt` (`attempt`).

### Revisión Ingeniero Móvil Senior (ronda 2)
- Sin cambios de comportamiento: solo cambian el texto, las KDoc y un comentario. La cancelación se sigue relanzando en `ImportFlow.confirm`.

### Textos para el Abogado Digital (revisión final), ronda 2
- `IMPORT_SAVE_FAILED` (`imports/ImportTexts.kt`), **cambiado**. Sustituye a «No se pudo guardar la importación: la bóveda no ha cambiado. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo.» Ahora dice: «No se ha podido terminar la importación. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo.»
