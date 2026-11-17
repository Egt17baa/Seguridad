# Re-revisión de la rama 2.0, parte B: copias y ajustes

- **Alcance:** la ronda de arreglos `a6e66f1..0d6df21`, grupos 1, 2, 3, 7 y 8 y D-1:
  - fila 17 (`035752d`) y fila 18 (`4f0d71b`);
  - C-1 (`e4f6aa0`), fila 37 (`af15de2`) y D-1 (`ff14e1d`);
  - fila 30 (`9e97517`), fila 39 (`c2da53c`), fila 42 (`848f118`) y B I-2 (`0d6df21`, con el anexo);
  - B m-2 (`8c9adc7`).

  Además, todos los commits de la ronda que tocan archivos de la parte B (`git log a6e66f1..0d6df21 -- …`): `353a148` (A I-1) y `c8a2b82` (A m-2 y m-4), solo donde cruzan con las copias.
- **Cómo:** solo leyendo el código, en mi clon (`scratchpad/br-copias`, `checkout 0d6df21`). **No ejecuté gradle ni tests.** «Falla sin el arreglo» quiere decir que leí cada aserción frente al código de antes del commit. Las líneas citadas son las de `0d6df21`.
- **Lentes:** `experto-seguridad-cripto`, `desarrollo-movil-senior`, `seguridad-criptografia-movil`, `auditoria-seguridad-movil`, y los Rulings de `advisor-t12-branch-fixes.md`.

## Veredicto

**Aprobada con menores: 0 Critical, 0 Important, 2 Minor y 4 informativos.** Los once arreglos de mi parte están hechos como dicen los Rulings. Cada uno tiene un test que falla sin él. No veo ninguna regresión en las copias, en A6 ni en los ajustes.

| Arreglo | Estado | Evidencia y test que falla sin él |
|---|---|---|
| **Fila 17 (B I-1)** | Hecho | `inRequest` aparte (`A6Text.kt:41-46`), y lo usa solo `a6Snapshot`. `requestId` no cambia (`:33`), así que `replaceAfter` sigue guardando `ReplaceDone("1.x")`; lo fija `whoIsInARequest`. `stale` de una petición heredada (`:58-59`). Tests: el exhaustivo `everyCombinationOfFoldersWithARequestOf143` (52 casos; sin el arreglo, `summary.stale` es falso y la foto lleva las carpetas nuevas), P-17b en el exhaustivo de siempre, `theStaleTextSaysWhere`, `aRequestOf143FinishedAfterAnotherChangeSaysSo` (desde el `settings.json` real de la 1.4.3; antes daba `AUTO_BACKUP_REPLACED`), `aFolderAddedAfterAClosedRequestOf143SaysNothing` (antes, la carpeta `dos` entraba como `"1.x"` y la foto cambiaba) y `aRequestOf143IsTheRequestOfNow`, que pasa a esperar `a6StaleText(1, 1)`. La duda 1 (la repetición única), aceptada en el Ruling, y con `a6SnapshotChanged` la línea `=sin` que desaparece ni siquiera cuenta |
| **Fila 18 (B m-1 y KDoc)** | Hecho | `a6SnapshotChanged` (`A6Text.kt:110-118`) con la cabecera, las líneas nuevas y las `=pendiente` que se van. Los tres KDoc al día: `changeMark` está ahora encima de su función, en `VaultStore.kt`. Test: `aFolderDoneAndRemovedAfterClosingSaysNothing` (sin el arreglo, quitar `dos` cambiaba la foto y hablaba). Además cubre que una carpeta pendiente que se va sí habla, y que otra cabecera también |
| **C-1** | Hecho | `attachmentsFit` (`Attachments.kt:45-46`) suma `data.entries`, la papelera incluida. `addAttachment` pasa a `modifyData` (`VaultStore.kt:1311-1323`) y conserva el orden de las entradas (`data.entries.map`). La entrada sigue saliendo solo de las vivas, y la comparación `===` vale porque `live` filtra sin copiar. Tests: `attachmentsFitCountsTheTrash`, el de fuente `addAttachmentCountsEveryEntry` y `whatTheTrashLeavesAlwaysPassesTheCopyCheck`, de punta a punta: 25 MB vivos y 25 MB en la papelera; sin el arreglo, el `addAttachment` siguiente pasaba y `fail(...)` saltaba. Ningún otro camino añade adjuntos: importar de otra app los vacía (`VaultStore.kt:958`), y restaurar o restaurar una versión no cambian el total |
| **Fila 37 (B I-4)** | Hecho | `BackupCheck.kt:210-211`: un tamaño fuera de 0..5 MB da `COPY_DAMAGED`; la suma de más de 50 MB, `ATTACHMENTS_TOTAL_TOO_BIG`. Al añadir sale `ATTACHMENTS_TOTAL_WOULD_PASS` (`Attachments.kt:36-38`), que dice «pasarían» y nombra la papelera. Tests: `BackupCheckTest` (`:533-540`) y `VaultStoreTest`, donde el tamaño -1 pasa a `COPY_DAMAGED` |
| **D-1** | Hecho | `BackupCheck.kt:199` solo cuenta las identidades vivas, como `upsert`. Lo comprobé: `entries.identity()` (`Contacts.kt:33`) se usa sobre `Unlocked.entries`, que son `data.live` (`VaultStore.kt:62`), así que una identidad en la papelera nunca pasa por la de la bóveda. `restore` sigue negándose a una segunda identidad viva. Test: en `BackupCheckTest`, una identidad viva y otra en la papelera pasan; dos vivas, no |
| **Fila 30 (B I-3)** | Hecho | `TrashTexts.kt:20`, con las palabras de `HISTORY_ALL_CONFIRM_TEXT`. Test en `TrashTest` (P-I3) |
| **Fila 39** | Hecho | `exportFailureText` (`VaultBackup.kt:281-291`) y `upgradeExportFailure` (`VaultUpgrade.kt:62-77`). El del paso a v4 remite a «Seguir sin copia», que existe con ese nombre (`VaultUpgrade.kt:47`). Test en `VaultUpgradeTextsTest` |
| **Fila 42** | Hecho | `copyFailureText` (`BackupCheckTexts.kt:105-117`) en los tres sitios: `VaultBackup.kt:186`, `BackupCheckSession.kt:130` y, en «Bóvedas», `copyRefusal` (`AppContainer.kt`, al final del archivo) en `VaultPickerScreen`. Borrar una bóveda usa `masterRefusal` con `masterUnlockErrorText`. Tests: `aCopysFailureIsSaidAsOfACopy`, `theVaultsActionsSayTheirOwnFailures` (con B activa: la copia con `masterA` da el texto de la copia; borrar B con `masterA` hace que `confirmMaster` lance y da `WRONG_MASTER`) y el de fuente de los tres sitios |
| **B I-2** | Hecho | Los cinco literales de A6 nombran «las de antes de la 2.0» (`ToolTexts.kt:508`, `AutoBackup.kt:152-193`, `A6Text.kt:174-198`). El anexo lleva «(cambiado en la 2.0, B I-2)» en 7 sitios. Tests: `theA6TextsNameTheCopiesOfBefore20` (11 textos), más `FinalLegalReviewTest`, `ToolTextsTest` y `AutoBackupF3Test` al día |
| **B m-2** | Hecho | `forgetOrphanVaults` (`AppContainer.kt:739-757`) aplica la doble condición del Ruling: fuera del registro **y** sin carpeta en el disco. Está bajo `changingVaults`. Corre después de `runBlocking { settings.load() }` (`SeguridadApp.kt:74-81`), así que nunca ve los ajustes por defecto. Test `theSettingsOfAVaultThatIsGoneGoAtTheStart`: un id sin carpeta se va y se suelta su carpeta; uno con carpeta se queda; `principal` se queda, y se comprueba leyendo el archivo otra vez |

## Issues

### Critical

Ninguno.

### Important

Ninguno.

### Minor

**m-1. Concordancia en el texto de copias sin apuntar en plural** (ya estaba en la 1.4.3, y ahora se copia al anexo).
- **Dónde:** `AutoBackup.kt:190`, «En la carpeta quedan $n copias con nombre de copia de la app (automática, de antes de importar o de antes de la 2.0) que…». Con «copias», debe ir «automáticas», como en `:179` y en `A6Text.kt:180`.
- **En el anexo:** `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:129` copia el mismo singular.
- **Arreglo:** «(automáticas, de antes de importar o de antes de la 2.0)» en los dos sitios.
- **Test:** `autoBackupUnlistedText(2)` contiene «(automáticas,».

**m-2. Comentario desfasado en `AutoBackup.kt:846-849`.**
- Cita el texto viejo («y las de antes de importar hechas antes del cambio») y razona sobre `AUTO_BACKUP_REPLACED` para una petición sin objetivos (de antes de la 1.3.0).
- Esa petición es heredada (`"1.x"`), así que desde la fila 17 sale siempre con `a6StaleText`.
- El razonamiento sigue siendo cierto: las copias de antes de importar y la de antes de la 2.0 son posteriores al cambio, así que nada de antes del cambio queda sin borrar. Pero hay que reescribir el comentario con el texto que de verdad sale.
- Solo documentación.

### Info

- **i-1. Cruza con A I-1 (`353a148`), a comprobar por el revisor de A.**
  - `MASTER_CHANGED_IN_OTHER_VAULT` (`VaultsTexts.kt`, a partir de `:90`) sale con `it.open && it.requestedAt == 0L` (`AppContainer.kt`, `switchTo`), sin mirar si la bóveda tenía carpetas de copia automática.
  - Sin carpetas, el diálogo no ofrecía borrar nada (`SettingsScreen.kt:515`, `autoBackupOn`), y el texto dice «el aviso que ofrece borrar sus copias anteriores… esas copias siguen abriéndose con la contraseña anterior».
  - Las exportaciones a mano sí siguen abriéndose así, así que no es falso del todo. Pero la oferta de borrar no existía.
  - **Cómo comprobarlo:** cambiar la maestra en una bóveda sin carpetas, cambiar de bóveda y leer el aviso.
- **i-2. Cruza con A m-2 (`c8a2b82`), a comprobar por el revisor de A.**
  - `checkCopySize` (`BackupCheck.kt:234-236`) rechaza como `COPY_DAMAGED` una copia v4 cuyos datos pasan de 24 MiB.
  - Los guardados normales ajustan con la misma medida (`VaultLimits.kt:118`, `:152`, `jsonBytes`).
  - El desbloqueo con la maestra de una v4 sin `vaultTag` añade uno sin ajustar (`Vault.kt:507`, `read.copy(vaultTag = …)`). Unos 45 bytes en una bóveda justo en el tope la pasarían, y sus copias se rechazarían: la misma clase que C-1.
  - Solo afecta a bóvedas de compilaciones de prueba de la 2.0 anteriores a la tarea 8.
  - **Test que lo cierra:** una bóveda v4 sin tag en el tope exacto: tras `unlock`, `checkCopySize(data)` no lanza; si lanza, se aplica `fitted()` también ahí.
- **i-3. `forgetOrphanVaults` falla hacia el lado seguro.** Si al borrar una bóveda no se pudo borrar su carpeta (`VaultRegistry.kt:133`), sus ajustes se quedan para siempre. Es lo que pide la doble condición.
- **i-4. `copyFailureText`** da `COPY_WRONG_PASSWORD_OR_DAMAGED` también a un `CryptoFailure()` que no venga de la contraseña (un JSON dañado). Dice «o copia dañada», así que es verdad.

## Revisión experto-seguridad-cripto

- **Formato:**
  - C-1 y D-1 devuelven la garantía de que una copia propia siempre se lee: la regla de añadir (`Attachments.kt:45-46`, `VaultStore.kt:1320`) y la de la identidad (`BackupCheck.kt:199`) miden ya lo mismo que la comprobación de la copia (`BackupCheck.kt:199`, `:210-211`).
  - No cambia ningún formato: `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` se leen igual. Una copia con una identidad en la papelera ahora se acepta, y antes se rechazaba: es más permisivo, y está acotado por los límites de entradas.
  - i-2 es el mismo tipo de garantía y queda para la parte A.
- **Veracidad de A6:**
  - una petición heredada nunca da el éxito llano (`A6Text.kt:58-59`);
  - una carpeta nueva no reabre lo cerrado (`:41-46`, `:110-118`);
  - los textos nombran todo lo que A6 borra (B I-2).
  - El borrado en sí no cambió: los ids de las listas propias, después de escribir la copia nueva (`AutoBackup.kt:1016-1062`).
- **Errores:**
  - «Contraseña incorrecta o copia dañada» no nombra una causa que AES-GCM no distingue (`BackupCheckTexts.kt:105-117`);
  - borrar una bóveda dice lo mismo que la pantalla de bloqueo (`masterRefusal`);
  - `KDF_OUT_OF_MEMORY` y `KDF_NO_MEMORY_NOW` pasan tal cual.
- **Ajustes huérfanos:** solo se borran con la doble condición, así que un `vaults.json` dañado no se lleva marcas A11 (`AppContainer.kt:739-757`).
- Sin cambios en la red, los permisos, los registros ni las dependencias.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - `addAttachment` sigue leyendo el documento fuera del mutex, con la época de antes (`VaultStore.kt:1314-1315`);
  - `forgetOrphanVaults` va bajo `changingVaults` y en IO (`SeguridadApp.kt:81`);
  - `a6SnapshotChanged` es pura.
- **Ciclo de vida:** la limpieza de huérfanos corre una vez por arranque, después de leer los ajustes de forma síncrona (`SeguridadApp.kt:74-77`). Un fallo de escritura lo deja para el siguiente arranque.
- **Rendimiento:**
  - `attachmentsFit` suma las referencias que ya están en memoria;
  - el test de C-1 escribe unos 50 MB en el disco de la prueba, que es aceptable pero lento: si la suite crece, usar límites inyectados.
- **Compose:** nada nuevo en `rememberSaveable`. `VaultPickerScreen` solo envuelve dos llamadas.
- **Tests:** son deterministas. Los de fuente buscan cadenas concretas (`copyFailureText(e)`, `masterRefusal { deletionText(`), así que un renombrado los rompe a propósito.

## Textos para el Abogado Digital (revisión final)

Son textos nuevos o cambiados en esta ronda, todos de mi parte:
- **C-1 y fila 37, al añadir un adjunto:** «Con este adjunto, los adjuntos de la bóveda (también los de la papelera) pasarían de 50 MB. Quita algún adjunto o vacía la papelera para añadir otro.» (`Attachments.kt:36-38`)
- **Fila 30:** `COPIES_KEEP_IT`, «Las copias ya hechas (automáticas o exportadas) pueden conservarlo; la copia automática quita las más antiguas.» Va junto con la fila 19.
- **Fila 39:**
  - al exportar: «… Quita adjuntos o entradas grandes, o vacía la papelera, y vuelve a exportarla.»;
  - en el paso a v4: «… Puedes seguir sin copia: la bóveda pasará al formato nuevo y las versiones anteriores ya no la abrirán.»
- **Fila 42:**
  - «Contraseña incorrecta o copia dañada. Vuelve a elegir la copia y escribe su contraseña.»;
  - «La copia pide unos parámetros de cifrado que Clavero no admite. Elige otra copia.»
- **B I-2:**
  - el botón: «Hacer una copia nueva y borrar las anteriores (automáticas, de antes de importar y de antes de la 2.0)»;
  - `AUTO_BACKUP_REPLACED`, `_SEVERAL` y `a6PartialText`, con «…las copias automáticas, las de antes de importar y las de antes de la 2.0 hechas antes del cambio de contraseña…»;
  - los de copias sin apuntar, con «(automática, de antes de importar o de antes de la 2.0)». En plural, «automáticas» (m-1).
- **Fila 17:** quien actualiza con una petición pendiente de la 1.4.3 ve `a6StaleText` en lugar del éxito llano.
- **Para la parte A (i-1):** `MASTER_CHANGED_IN_OTHER_VAULT` y su línea de la huella (`VaultsTexts.kt`).
