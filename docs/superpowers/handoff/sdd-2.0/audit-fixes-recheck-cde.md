# Clavero 2.0: repaso de la ronda de arreglos de la auditoría, partes C, D y E (21-11-2026)

**Quién:** el revisor que hizo las partes C, D y E de la auditoría completa (`final-audit-c.md`, `-d.md`, `-e.md`).
**Qué se repasa:** los commits `7505281..a94455a` que tocan C-1, C-5, C-7, D-1, D-2, D-3 (= RC-5), D-4, E-1 (con la fila 82), E-2 (con RC-2), E-3 (con RC-6) y la fila 84; y que C-2, C-3 y C-6 quedaron apuntados como riesgos con nombre.
**Encargo leído:** `advisor-t12-audit-fixes.md` (Ruling y encargo, líneas 138–378), `audit-fixes-report.md` (sobre todo §4 y §5), `pendientes.md` filas 81–104, `CLAUDE.md` y `experto-seguridad-cripto/SKILL.md`.
**Solo lectura:** no cambié código ni ejecuté Gradle (había un cerrojo de compilación). El controlador informa de que el clon limpio de `a94455a` dio 2 601 tests en la app y 2 536 en jvm-check, sin fallos; no lo he repetido. Cada `archivo:línea` es del HEAD `a94455a`. Rutas: `m/` = `app/src/main/java/app/seguridad/`, `t/` = `app/src/test/java/app/seguridad/`.

## Veredictos

| Hallazgo | Commit | Veredicto |
|---|---|---|
| C-1 | `97d0bfc` | Cerrado con menor (C-1.m1) |
| C-5 | `3882d17` | Cerrado |
| C-7 | `0effa6b` | Cerrado con menor (C-7.m1) |
| C-2, C-3, C-6 | — | Apuntados como riesgos con nombre (filas 102, 103 y 101), bien |
| D-1 | `b3825b4` | Cerrado con menor (D-1.m1, D-1.m2) |
| D-2 | `9b42fec` | Cerrado |
| D-3 = RC-5 | `e629906` | Cerrado |
| D-4 | `b1e1ce4` | Cerrado |
| E-1 (y fila 82) | `ae488ea` | Cerrado |
| E-2 (con RC-2) | `0ae9409` | Cerrado |
| E-3 (con RC-6) | `3399f10` | Cerrado |
| Fila 84 | `462aad3` | Cerrado |

Ninguno queda Abierto. Los cuatro menores son de tests o de una ventana teórica; ninguno cambia el comportamiento que ve el usuario.

## Parte C

### C-1: `PublishedFilesNameNoModelTest` — Cerrado con menor

- **Hace lo pedido:** lee los nombres en tiempo de ejecución de la fila `| \`modelo\` |` de `.claude/skills/advisor/SKILL.md` (`t/PublishedFilesNameNoModelTest.kt:21-29`), falla con un mensaje si falta el archivo o la fila (`:23`, `:25`), recorre exactamente el alcance del encargo (`:33-36`) y deja fuera `build/`, `.git/`, `.claude/` y `app/src/main/assets/` (`:40`). Palabra entera, sin mayúsculas (`:51`). La excepción se reconoce por ruta y contenido, no por número de línea (`:45-47`). El test no escribe ningún nombre.
- **¿Fija de verdad?** Sí: `theScanFindsANameInATestTree` (`:68-86`) deja el rojo fijado en un árbol de prueba (documento y fuente cuentan; `build/`, `assets`, una palabra pegada y la línea de la excepción no). Comprobé que no hay ninguna carpeta `build`, `.git` ni `.claude` dentro del alcance que el filtro pudiera esconder por error.
- **Nada nuevo roto.** El archivo binario se salta por un cero en los primeros 8 000 bytes (`:55`), razonable.
- **C-1.m1 (menor, solo local):** las dos tareas `test` declaran como entradas `docs/` y la tabla de la skill (`jvm-check/build.gradle.kts:69-71`, `app/build.gradle.kts:247-249`), y antes ya README, NOTICE, SECURITY y el workflow (`app/build.gradle.kts:236-241`); pero no `CLAUDE.md`, `.maestro/`, `scripts/`, `gradle/` ni el `build.gradle.kts` de la raíz, que el test también recorre. Un cambio solo en uno de ellos deja la tarea `UP-TO-DATE` en local y el test no corre. En la CI no pasa (no hay caché de compilación: ni `org.gradle.caching` ni `setup-gradle` en `.github/workflows/build-apk.yml`). **Arreglo:** añadir en los dos bloques `inputs.files("../CLAUDE.md")` e `inputs.dir("../.maestro")`, `inputs.dir("../scripts")`, `inputs.dir("../gradle")` e `inputs.files("../build.gradle.kts")` con `withPropertyName` propio, como `docsReadByTests`.

### C-5: `fileOf` rechaza un id que no es de adjunto — Cerrado

- `m/vault/Attachments.kt:95-98`: `require(isAttachmentId(id))`, con `ID = [0-9a-f]{32}` (`:68`, `:79`): ni `/`, ni `.`, ni mayúsculas. Falla cerrado antes de nombrar ningún archivo.
- **Llamadores** (los de §4 del informe, comprobados): `VaultStore.kt:1096` (`applyImport`, dentro de `accept`; la excepción sale sin escribir y `ImportFlow.confirm` la convierte en `SaveFailed` con texto fijo, `m/imports/ImportFlow.kt:49-55`); `:1476` (`export`, ya filtrado en `:1474`); `:1681` (`moveIn`, precedido por `attachmentInCopy`; si llegara, `putBack` y relanza); `Attachments.kt:134` (id aleatorio) y `:156` (precedido por `ID.matches`, `:155`). Ninguno cierra la app.
- **Test:** `t/vault/AttachmentsTest.kt:110-122`. Sin el `require`, `fileOf("../vault")` devuelve un `File` y salta `fail(...)` (un `AssertionError`, que el `catch` de `IllegalArgumentException` no recoge): fallaría sin el arreglo.

### C-7: `WorkflowHardeningTest` — Cerrado con menor

- **Hace lo pedido:** los cuatro casos del encargo (`t/tools/WorkflowHardeningTest.kt:94-100`): acciones fijadas a un SHA de 40 hex (`:41-45`); sin `pull_request_target` ni `write-all`, bloque `permissions:` de arriba igual a `contents: read` y solo `release-apk` con `write` (`:47-61`); los cuatro `secrets.SEGURIDAD_*` de `build` con la condición de confianza literal y el `|| ''` (`:63-70`), los demás solo en trabajos con `environment: firma` y cuatro en `play-bundle` (`:71-72`), ningún `secrets[` ni `toJSON(secrets` (`:73`), y `persist-credentials: false` en cada checkout (`:75-77`); el certificado igual al de `docs/PUBLICAR.md`, con sus dos comparaciones y `build-firma` reutilizando los pasos de `build` (`:81-91`).
- **¿Fija de verdad?** Sí: `eachCheckFailsOnAnAlteredCopy` (`:103-116`) altera nueve veces el YAML real y comprueba que cada función da problemas; además afirma que el texto alterado existe (`:104`), así que no puede pasar en vacío si el YAML cambia.
- **C-7.m1 (menor):** ningún caso fija los `if:` de confianza de `build-firma` (`.github/workflows/build-apk.yml:168`) y de `play-bundle` (`:283`). Los secretos de `build` y, por el ancla, de `build-firma` tienen su propia condición (`:88-91`), pero los cuatro de `play-bundle` (`:311-314`) no: con C-3 abierto (secretos aún en el repositorio), su `if:` y el `needs: [build-firma, …]` son la única guarda en el YAML. Un `if: always()` en `play-bundle`, o quitar los dos `if:`, pasaría los cuatro casos. **Arreglo:** en `signingSecretsOnlyInTrustedRuns`, exigir que las líneas `if:` de los trabajos `build-firma` y `play-bundle` sean exactamente `if: $trust` (la cadena `trust` de `:21-22`), y añadir a `eachCheckFailsOnAnAlteredCopy` una alteración que cambie la de `play-bundle` por `if: always()`.

### C-2, C-3 y C-6: apuntados — bien

- `pendientes.md`, fila 101 (C-6, «Aceptado» con el motivo de `targetSdk` 36 y lo que habría que repetir en el móvil si se adopta), fila 102 (C-2, aceptado por el dueño) y fila 103 (C-3, aceptado por el dueño, con C-7 como lo que lo reduce).
- El informe consolidado los recoge igual (`docs/auditorias/2026-11-21-auditoria-2.0.md:137`, `:262`, `:276`, `:303`, `:348`, `:363`). La frase de la fila 101 («niega el texto en claro desde Android 9») es cierta y deja a la vista que Android 8.x lo permite, como dice el Ruling.

## Parte D

### D-1: la espera de la subida, acotada — Cerrado con menor

- **Hace lo pedido:** `m/vault/PendingUpgrade.kt:14` (`UPGRADE_COPY_WAIT_MS = 120_000L`) y `:100-106`: `withTimeoutOrNull` alrededor de `copyBefore()`, dentro del `try` de antes y del `withContext(NonCancellable)` (`:96`). Solo se corta el `await` de `AutoBackup.copyBeforeUpgrade` (`m/vault/AutoBackup.kt:749`, `scope.async { lock.withLock { … } }.await()`): la copia sigue en el ámbito de las copias. `autoCopyNotDone` se apunta dentro de `synchronized(opening)` (`:115`) y se borra en `openedWith` (`:67`). El diálogo lo enseña en lugar del fallo (`m/ui/screens/VaultUpgrade.kt:132-133`), y el `finally` pone `copying` a falso (`:122`), así que `UPGRADE_COPYING` no sigue pasados los 2 minutos.
- **La bandera `waited` (desviación del implementador, §5):** correcta. La fórmula literal del encargo (`?: Failed(NOT_DONE)`) habría convertido el `null` de «no hay carpeta» (`copyBeforeAny` sin destinos, `AutoBackup.kt:752-761`) en «no ha terminado en 2 minutos», un texto falso al instante. Con la bandera: `null` de la copia → `waited = false` → `null` (sin texto); un fallo rápido o una excepción → `waited = false`; solo el `null` de `withTimeoutOrNull` deja `waited = true`. `copyBeforeUpgrade` no captura la cancelación del `await` (`AutoBackup.kt:749`; el `catch` de `copyBeforeNow`, `:780-782`, está dentro del `async`, que no se cancela), así que el tiempo agotado llega como `null` de `withTimeoutOrNull` y no como un `Failed` de la copia.
- **Tests:** `t/vault/UpgradeCopyFirstTest.kt:446-480` mira justo antes y justo después del tope con tiempo virtual (`:455-460`), comprueba `upgradeAutoCopyNotDone`, el rechazo `UPGRADE_PENDING`, que la subida se guarda como v4 y que la copia tardía no se anuncia, queda en `currentImportCopies` y A6 no la borra (`:474-479`). La variante `:488-534` empieza con la copia atascada desde antes del desbloqueo y tiene el `lock` tomado («Hacer una copia ahora»), como pedía el encargo. Sin el arreglo, `assertFalse(s.upgradeCopying.value)` (`:460`, `:515`) falla. Texto: `t/ui/screens/VaultUpgradeTextsTest.kt:56-73`, palabra por palabra, `ForbiddenWords` y «2 minutos» ligado a la constante.
- **Nada nuevo roto:** con la copia aún dentro del proveedor, las copias automáticas que siguen esperan el `lock` (no escriben nada fuera de orden); «Exportar una copia antes» no usa ese `lock`. Cada nueva apertura con la carpeta colgada vuelve a esperar 2 minutos y deja otro `async` en cola: acotado por las acciones del usuario, y cada uno sale sin escribir si la subida ya se guardó (`VaultStore.kt:1468`).
- **D-1.m1 (menor, de test):** nada fija que «sin carpeta», un fallo rápido y una copia que lanza dejan `upgradeAutoCopyNotDone` en falso. Hoy lo cubre de rebote `assertFalse(none.upgradeAutoCopyFailed)` (`t/vault/VaultV4Test.kt:340`), porque la fórmula sin bandera pondría también `autoCopyFailed`; pero un cambio que solo estropee `waited` no se vería. **Arreglo:** en `VaultV4Test.upgradeCopiesFirstWithBackupFolder`, `assertFalse(s.upgradeAutoCopyNotDone)` junto a `:321`, y `assertFalse(t.upgradeAutoCopyNotDone)`, `assertFalse(thrown.upgradeAutoCopyNotDone)` y `assertFalse(none.upgradeAutoCopyNotDone)` junto a `:329`, `:335` y `:340`.
- **D-1.m2 (nota, ventana teórica):** si la copia termina justo cuando vence el tiempo (la cancelación de `withTimeoutOrNull` llega después de `.also { waited = false }` pero antes de que el bloque devuelva), `withTimeoutOrNull` da `null` con `waited = false`, y la subida pregunta como si no hubiera carpeta, sin el texto de la copia y aunque la copia exista (apuntada como las de antes de importar). Es de microsegundos y benigno. **Arreglo opcional:** guardar el resultado fuera del bloque (`var made: CopyResult? = null; withTimeoutOrNull(…) { copyBefore().also { made = it; waited = false } }`) y usar `made` cuando `waited` es falso.

### D-2: un diálogo de «Bóvedas» abierto tras el bloqueo — Cerrado

- `m/ui/screens/VaultPickerScreen.kt:202-204`: `LaunchedEffect(active, activeState, dialog)`, así que un diálogo que se abre con la bóveda ya bloqueada se cierra en el mismo paso. `:223`: el selector de «Importar como bóveda nueva» solo abre la contraseña si `pickerDialogKept(IMPORT_NEW, active.state.value)` (lectura síncrona, no el `activeState` de la composición); si no, `VAULT_NEW_NEEDS_OPEN` («Para crear o importar otra bóveda, abre antes una de las que tienes.», `m/vault/VaultsTexts.kt:28`), que es verdad en ese caso. Con `NoVault` se sigue ofreciendo (`:171`), como antes.
- **Tests:** `t/ui/screens/VaultPickerDialogsTest.kt` `aDialogOpenedAfterTheLockClosesToo` y `theImporterAsksOnlyWithTheVaultOpen` (este además exige una sola aparición de `PickerDialog.ImportNew(uri)`, así que no puede quedar otra vía sin guarda). Fallarían sin el arreglo.
- **El cambio en `theScreenClosesItOnTheVaultsState`:** su expresión pasa a admitir `(?:, dialog)?`. Es la consecuencia directa del arreglo pedido y no afloja nada, porque el test nuevo exige `dialog` entre las claves. Lo apruebo; si se quiere, se puede quitar el `?` para que diga lo mismo que el nuevo, pero no hace falta.
- **Sin bucle:** el efecto solo pone `dialog = null`, que relanza el efecto una vez y termina.

### D-3 = RC-5: `FOLDER_DELETE_TO_NONE` — Cerrado

- `m/vault/OrganizeTexts.kt:63`: «Sus entradas pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»», con las constantes `FOLDER_NONE` y `FOLDER_TOP` (`:49-50`), el vocabulario de la app. Coincide con el código: `vault/Folders.kt` con `up = null` deja las entradas sin carpeta y las subcarpetas arriba del todo; las entradas de las subcarpetas no cambian.
- **Test:** `t/vault/FolderDeletionTextTest.kt`, palabra por palabra y `ForbiddenWords`; fallaba con el texto anterior (rojo copiado en el informe). El comportamiento lo fija ya `t/vault/FoldersTest.kt:126-127` (una carpeta de arriba borrada con `PARENT` deja su subcarpeta con `parentId` nulo). Mi propuesta de D-3 decía además «con lo que tienen dentro»; el director eligió la redacción de RC-5, que es verdadera. Va al Abogado.

### D-4: «Borrar carpeta» — Cerrado

- `m/ui/screens/VaultOrganize.kt:349` (`busy`), `:359-360` (la fila y el `RadioButton` ponen `error = null`), `:374-388` (`enabled = !busy`, `busy = true` antes de lanzar y `busy = false` en `finally`), como `NameDialog`.
- **Test:** `t/ui/screens/VaultOrganizeSourceTest.kt`: dos `{ to = option; error = null }`, el `busy` y el `finally` dentro del cuerpo de `DeleteFolderDialog`. Fallaba antes (`expected:<2> but was:<0>`). Un doble toque ya no lanza dos borrados.

## Parte E

### E-1: la marca del almacén y el tope de una entrada — Cerrado (cierra la fila 82)

- `m/vault/VaultLimits.kt:102-103`: `bytesWithoutHistory` mide sin `history` y sin `passwordChangedAt`; KDoc de `ENTRY_MAX_JSON_BYTES` al día (`:18`). Es la única medida del tope (`:232`), que usan el guardado, `applyImport`, `entryFitsImported` (`m/imports/MergePlan.kt:177-178`) y la comprobación de copias, así que el tope solo se afloja, como mucho en lo que ocupa un `Long` con su clave, y la marca sigue contando en los 24 MiB.
- **Tests** (`t/vault/PasswordMarkTest.kt`): `entryJustUnderTheCap` construye una entrada de 1.4.3 de exactamente `cap - 10` B y lo afirma; la edición del mismo tamaño y quitar «Favorita» daban `ENTRY_TOO_BIG` antes (rojo en el informe) y ahora guardan con la marca congelada. `aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted` afirma primero que la entrada con marca pasa del tope (así el test no es vacío) y que «Comprobar una copia» e «Importar copia» la aceptan. Que saliera verde antes del arreglo está bien explicado en §5 del informe (`fittedForSave` no mira las entradas que no cambian); fija la regla «la app nunca rechaza sus propias copias».
- `ENTRY_TOO_BIG` («La entrada pasa de 64 KB…») sigue siendo verdad: solo sale cuando la medida pasa del tope.
- Fila 82: «Hecho, cerrado por E-1 (`ae488ea`)»; fila 90, «Hecho». Bien.

### E-2 con RC-2: el orden de las copias tras una apertura nueva — Cerrado

- `m/vault/AutoBackup.kt:835`: `attempt` aplaza (`defer`, `:936-938`) cuando `upgradeCopying` es verdadero, ya con el `lock` tomado. Aplazar no pierde nada: `attempt` saca el destino de `pending` al empezar (`:829`) y `defer` lo vuelve a meter; el `runDue(afterUnlock = true)` de la apertura nueva, que el colector (`:458`) procesa después de la pasada vieja, espera la copia de la subida (`:525`) y luego copia lo pendiente (`waited`, `:534`). Una petición «Hacer una copia ahora» o una de A6 pasa también por `pending`, así que tampoco se pierde.
- **Tests** (`t/vault/UpgradeCopyFirstTest.kt:330`, `:380`, `:405`): el rojo de RC-2 necesitó una petición A6 guardada mientras la copia está retenida (§5 del informe); es la secuencia equivalente y fija lo que pedía. El de E-2 sigue mis pasos y comprueba que el segundo documento es la copia `seguridad-antes-de-2-0-` de la apertura nueva.
- **Lo que queda (informativo, no menor):** `copying` puede pasar a verdadero mientras una pasada ya está dentro de `attempt` escribiendo; entonces esa única copia va antes que la de la subida. Es la ventana residual que ya describía E-2, solo de orden y sin efecto de seguridad (A6 no puede borrar una copia que aún no existe). El Ruling eligió una sola comprobación; lo acepto.

### E-3 con RC-6: los KDoc falsos — Cerrado

- `m/AppContainer.kt:705-720` (`deleteVault`): ahora dice que una bóveda bloqueada no se abre y solo se comprueba su maestra (`checkMasterToDelete`, `m/vault/VaultStore.kt:793-809`), que `LOCKED` es la carrera de la abierta o una bóveda que ya no es la activa (`:796`, `:799`, y `confirmMaster`, `:776-783`), y que una bóveda que se bloqueó entre el diálogo y la confirmación se borra si su maestra y su nombre son los suyos. Todo eso coincide con `AppContainer.kt:730-739`.
- `m/ui/screens/ContactsScreen.kt:78-84` (`ContactsFrame`) y `:130-137` (`MasterPrompt`): dicen que en la 2.0 una v2 abierta siempre tiene la subida pendiente o guardada, que `MasterPrompt` queda como defensa no alcanzada y que `confirmMaster` no pasa la bóveda a la 3. Verdadero.
- **Test:** `t/vault/ContactsViewTest.kt` `theMasterGateIsNeverReachedWithAnOpenVault`, con una v2 real (`vault-1.1.json`), la copia retenida, los dos caminos (copia hecha; copia fallida y un guardado de la subida que falla) y cada valor de los cuatro flujos combinados. Salió verde desde el principio (fija lo que se cumple); el rojo con `contactsGate` alterado está en el informe. Comprueba además que la bóveda estuvo abierta en algún momento, así que no pasa en vacío.
- `NEEDS_MASTER_NOTICE` queda para el Abogado, como decidió el director.

## La fila 84: el número de adjuntos — Cerrado

- `m/vault/Attachments.kt:60-61`: `attachmentCountFits` = adjuntos de todas las entradas (papelera incluida: `VaultData.entries` la contiene, `m/vault/VaultData.kt:41-44`) + 1 + 2 ≤ `BACKUP_MAX_ENTRIES` (2 000, `m/vault/Backup.kt:62`), la misma cuenta que `writeBackup` (`Backup.kt:153`). `m/vault/VaultStore.kt:1381`: se mira antes del tope de bytes y antes de `attachments.add`, así que no queda ningún archivo a medias.
- Es la única vía que añade adjuntos: «Duplicar» no los copia (`VaultStore.kt:995`), «Importar de otra app» no trae y «Importar copia» sustituye la bóveda por una que ya cabe.
- **Texto:** «como mucho 1 998» es verdad (con 1 997 cabe uno más; con 1 998 no). El test lo liga a `BACKUP_MAX_ENTRIES - 2`.
- **Tests:** `t/vault/AttachmentsTest.kt` `theCountOfACopyLimitsTheAttachments` (1 997 / 1 998 y la papelera) y `t/vault/VaultStoreTest.kt` `anAttachmentPastTheCopysFileCountIsRefused` (texto, archivo de la bóveda igual byte a byte, ninguna carpeta de adjuntos nueva y la entrada con sus 100). Sin el arreglo, el segundo guarda y salta `fail`.
- Fila 104: el riesgo de las bóvedas de la 1.4.3 con más de 1 998 adjuntos, «Aceptado». Bien.

## Revisión experto-seguridad-cripto

- **Rutas (C-5, CWE-22):** `fileOf` falla cerrado en el único punto por el que pasan todos (`m/vault/Attachments.kt:95-98`); el id es `[0-9a-f]{32}` (`:68`), sin separadores. Ningún llamador convierte el rechazo en un cierre de la app (§C-5).
- **Cadena de firma (C-7, CWE-1357):** fijados por test los SHA de las acciones, los permisos, la condición de confianza de los secretos de `build` y el certificado de subida (`t/tools/WorkflowHardeningTest.kt:41-91`). Falta fijar los `if:` de `build-firma` y `play-bundle` (`.github/workflows/build-apk.yml:168`, `:283`), que con C-3 abierto son la guarda de los secretos de `play-bundle` (`:311-314`): C-7.m1.
- **Disponibilidad (D-1, CWE-1088):** la espera está acotada (`m/vault/PendingUpgrade.kt:102`) y la copia no se cancela, así que no queda un documento a medias por el corte; la copia tardía no se anuncia como la de la subida y A6 no la borra (`t/vault/UpgradeCopyFirstTest.kt:474-479`, `:526-533`). La bandera evita un texto falso (CWE-451) en el caso «sin carpeta».
- **Límites (E-1, fila 84):** la medida de una entrada solo se afloja en un campo acotado que pone el almacén (`m/vault/VaultLimits.kt:102-103`); el tope de número se mira antes de escribir nada (`m/vault/VaultStore.kt:1381`). Ninguna copia que escribe la app deja de leerse (`t/vault/PasswordMarkTest.kt`, `aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted`).
- **Formatos y primitivas:** ninguno de estos commits toca `m/crypto/`, la escritura de `seguridad-vault`, `seguridad-backup`, `SG1:` o `.sgenc`, ni el AAD de los adjuntos.
- **Secretos en memoria y registros:** ningún registro nuevo; D-2 cierra antes un diálogo con contraseña tras el bloqueo (`m/ui/screens/VaultPickerScreen.kt:202-204`, `:223`); los textos nuevos son constantes.
- **Regla «ningún nombre de modelo» (C-1):** fijada por test (`t/PublishedFilesNameNoModelTest.kt:63-65`); este archivo tampoco nombra ninguno.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** `withTimeoutOrNull` dentro de `withContext(NonCancellable)` crea su propio hijo y solo corta el `await` (`m/vault/PendingUpgrade.kt:96-106`); el `async` vive en el ámbito de `AutoBackup` (`m/vault/AutoBackup.kt:749`). RC-2 aplaza dentro del `lock` sin cerrojos nuevos (`:835`), y el colector secuencial (`:458`) garantiza que la pasada de la apertura nueva corre después. Ventana teórica de D-1.m2, benigna.
- **Ciclo de vida y Compose:** D-2 usa la lectura síncrona del estado en el resultado del selector (`VaultPickerScreen.kt:223`) y `dialog` como clave del efecto (`:202`); D-4 sigue el patrón de `NameDialog` con `finally` (`m/ui/screens/VaultOrganize.kt:374-388`). Ningún `rememberSaveable` nuevo; la contraseña de la copia sigue en `remember`.
- **Tests:** sin `sleep`; tiempo virtual, `CompletableDeferred` y `withTimeout` (`t/vault/UpgradeCopyFirstTest.kt:446-534`, `t/vault/ContactsViewTest.kt`). Los de interfaz son anclas de fuente porque el repositorio no tiene pruebas de Compose; es lo que pedía el encargo.
- **Compilación:** entradas de las tareas `test` incompletas para el test de C-1 (C-1.m1), solo en local.
- **Para el móvil:** el diálogo de D-1 a los 2 minutos con una carpeta lenta; D-2 con 2 minutos y medio en el selector; «Añadir adjunto» cerca de los 1 998 (fila 84).

## Textos para el Abogado Digital (revisión final)

Sin textos nuevos de este repaso. Los de la ronda que vienen de mis partes, ya listados en `audit-fixes-report.md` §6:
- `UPGRADE_AUTO_COPY_NOT_DONE` (`m/ui/screens/VaultUpgrade.kt:58-60`, D-1): verdadero también cuando la carpeta solo es lenta.
- `FOLDER_DELETE_TO_NONE` (`m/vault/OrganizeTexts.kt:63`, D-3 = RC-5): puede retocarse si nombra los dos sitios y no dice que las subcarpetas desaparecen.
- `ATTACHMENTS_COUNT_WOULD_PASS` (`m/vault/Attachments.kt:52-54`, fila 84): la cifra 1 998 debe seguir ligada a `BACKUP_MAX_ENTRIES - 2`.
- `NEEDS_MASTER_NOTICE` (`m/vault/ContactsView.kt:68-69`, E-3): no se enseña en la 2.0 y sería falso si se viera.
- Sin cambio: `UPGRADE_COPYING` (con D-1 deja de salir pasados 2 minutos) y `ENTRY_TOO_BIG` (con E-1 ya no sale a quien no hizo más grande una entrada).
