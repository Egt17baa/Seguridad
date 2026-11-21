# Director: cierre de la ronda única de arreglos de las revisiones (CR-1 a CR-7, I-1 a I-3) (20-11-2026)

**Pregunta:** ¿se cierra la ronda única (`77e6a51` … `69c967b`, rama `ccr-0aa27f95-u038cd`)? Es la re-revisión corta que pedí en `advisor-t12-review-fixes.md`, con las cinco cuestiones del parte del 20-11 (§3, paso 1).

**Qué se comprobó:**
- el diff `git diff 32af38f..69c967b`, en el código y en `archivo:línea`; de `69c967b` al HEAD actual (`7a8ec70`) solo cambian documentos (`git diff --stat 69c967b..HEAD -- app jvm-check` está vacío);
- el clon limpio de `69c967b` (app 2565/0, jvm-check 2504/0) y el `grep` de `@Ignore`, que aquí también sale vacío;
- sin Gradle: lo que digo del comportamiento sale de leer el código, y los dos arreglos de comportamiento (RC-1 y RC-2) piden su rojo antes de tocar nada.

Veredicto: la ronda se cierra, con seis arreglos menores (RC-1 a RC-6) que van a la ronda de después de la auditoría; RC-1 es una regresión de la propia ronda en CR-5 (cerca de los 24 MiB, la vista previa dice «no cabría» a una importación que el guardado sí hace, recortando historial). La auditoría sigue sobre `69c967b`, sin pararse.

## Motivos

### Lo que se aprueba tal cual

- **CR-1:**
  - `frozenPasswordMark` (`vault/EntryHistory.kt:33-34`) se usa en `withVersion` (`:47`), en `withoutHistory` (`:91-93`), en los adjuntos (`vault/VaultStore.kt:1375`, `:1388`) y en «Duplicar» (`:991`);
  - `comparable()` ignora la marca (`EntryHistory.kt:24-25`), así que no crea versiones;
  - el `grep` de `updatedAt =` también alcanza `ui/`: el editor (`ui/screens/VaultEntryScreen.kt:541`) va por `upsert`, que siempre termina en `withVersion` (`VaultStore.kt:969`), y la identidad (`IdentityScreen.kt:147`) no tiene contraseña. Está cubierto.
- **CR-3:** `contactsGate` (`vault/ContactsView.kt:60-65`) y su lectura en `ui/screens/ContactsScreen.kt:96-99` y `:120-124`. Queda un KDoc falso, que es RC-6.
- **CR-4:** el rechazo llega antes de cambiar nada (`vault/Folders.kt:208-216`), con la misma regla de padre efectivo que `checkFreeName`. Los dos hijos no pueden chocar entre sí, porque ya eran hermanos.
- **CR-7:** `checkMasterToDelete` (`VaultStore.kt:787-803`) cumple el encargo:
  - va con `mutex` y `NonCancellable`, `derive {}` (`KdfLock`) y `active()` antes y después;
  - decodifica en `compute` y convierte el `OutOfMemoryError`;
  - pone la clave a cero en el `finally` (`:800`).

  `deleteVault` lo usa con toda bóveda bloqueada (`AppContainer.kt:726-735`). No existe ningún contador de intentos que este camino se salte: Argon2id es el único coste, igual que en `unlock`.
- **I-1:** `estimated` quita la bandera (`imports/ImportSession.kt:326`) y `canConfirm` la lee (`:335-338`).
- **I-2:** `RedTeam20Recheck2Test.kt:251-252`, 160 exacto.
- **I-3:** `rt28_` informativo (`:602-604`); el ancla, `listTags` en `compute` y el diferencial siguen.
- **No cambia ningún formato:**
  - `git diff --stat 32af38f..69c967b` está vacío en `vault/Backup.kt`, `core/`, `crypto/`, el manifiesto y Gradle;
  - en `Vault.kt` solo cambia un KDoc;
  - `passwordChangedAt` ya existía en la v4.

### 1. La desviación de CR-5

- **Las 200 bóvedas pequeñas: aceptado, con una condición.**
  - La desigualdad «la vista previa nunca cuenta menos que el guardado» no depende del tamaño, porque las dos mediciones usan las mismas funciones: `jsonBytes(...fitted())` en `imports/MergePlan.kt:429` frente a `VaultStore.kt:1079`.
  - **Hay una excepción:** `fitted()` solo recorta historial de toda la bóveda por encima de 24 MiB (`vault/VaultLimits.kt:153-166`). Ningún caso pequeño llega ahí, y los cuatro de los topes reales no tienen historial (`VaultStoreImportTest`, `trashFor` y `realCaps`).
  - Ahí se esconde RC-1. La condición es su test.
- **El margen de 1 KiB: el tamaño vale, pero el sitio no.**
  - **Lo que tapa** son cinco campos de `VaultData` (`vault/VaultData.kt:12-27`): `vaultTag`, dos horas, una bandera y la versión de las plantillas. Suman unos 160 B, y el rojo con margen 0 da 100 B.
  - **El fallo:** el margen se suma después de recortar (`MergePlan.kt:429`).
    - `fitted()` recorta hasta que los datos quedan en 24 MiB o menos, y le sobra menos que la última versión quitada (`VaultLimits.kt:163`).
    - Con versiones de menos de 1 KiB, `json − VAULT_MAX_DATA_BYTES` sale entre 1 y 1024. La pantalla dice «La bóveda resultante no cabría en una copia: sobran unos 1 KB» (`ui/screens/ImportScreen.kt:617`) y «Importar» queda apagado (`ImportSession.kt:338`).
    - Mientras, `applyImport` recorta el historial y guarda.
    - Antes de la ronda, la vista previa no medía los 24 MiB y decía 0, así que es una regresión de CR-5, del lado seguro (no se pierde nada). Además, el texto es falso. → **RC-1.**
- **El `TooBig` de los adjuntos sin modelar: aceptado en la vista previa.**
  - **El tope de bytes no se puede alcanzar:**
    - los adjuntos suman como mucho 50 MiB en claro (`vault/Attachments.kt:24`, `:45-46`);
    - se guardan en crudo, sellados (`:112`);
    - el JSON de la copia no pasa de 33 MiB.

    Eso queda por debajo de los 100 MiB de `BACKUP_MAX_TOTAL_BYTES` (`vault/Backup.kt:60`).
  - **El tope del número sí se puede alcanzar,** por un hueco anterior a esta ronda.
    - `attachmentsFit` solo cuenta bytes (`Attachments.kt:45-46`), y `writeBackup` rechaza más de 1 998 adjuntos (`Backup.kt:62`, `:153`).
    - 1 999 adjuntos de unos 26 KB caben en 50 MiB.
    - Desde ese momento no se puede escribir ninguna copia (automática, exportada ni la de antes de la subida), e importar da `TooBig(overFiles)` (`VaultStore.kt:1058-1061`).
    - No es de CR-5, así que no se arregla aquí. Va a la consolidación de la auditoría como hallazgo nuevo, «a comprobar» (propuesta: Baja), con este arreglo:
      - `attachmentsFit` cuenta también `adjuntos + 1 + 2 ≤ BACKUP_MAX_ENTRIES`, con su texto;
      - un test con 1 998 adjuntos pequeños.

### 2. Las tres ventanas de CR-2

- **`runChanged` sin un rojo propio: aceptado como defensa, con un ancla.**
  - El razonamiento del implementador se sostiene:
    - el colector es uno solo (`vault/AutoBackup.kt:458`);
    - la `Mutex` es justa: la cola sigue el orden de llegada (`:448`);
    - el `Unlocked` lanza `runDue`, que espera (`:525`).
  - Aun así, es una línea de producción que ningún test pone en rojo al quitarla. → **RC-3.**
- **Un `Error` que deja `copying` en verdadero: aceptado, como riesgo con nombre.**
  - La ventana va de `openedWith` (`VaultStore.kt:538`, `PendingUpgrade.kt:56`) al `finally` de `afterUnlock` (`PendingUpgrade.kt:100-104`).
  - En medio solo están `tellCostKept` y `collectGarbage` (`VaultStore.kt:641-642`, que captura toda `Exception`, `:411-416`). No hay ningún punto de suspensión que se pueda cancelar.
  - Por lo que leo de kotlinx.coroutines, `withContext(NonCancellable)` no cambia de *dispatcher* y vuelve sin comprobar la cancelación (`VaultStore.kt:584-594`).
  - **Si pasa:**
    - un bloqueo lo cierra (`drop()`, `VaultStore.kt:816-821`), y el de inactividad también;
    - el archivo sigue siendo un 2 o un 3;
    - mientras tanto, guardar dice `UPGRADE_COPYING` sin que haya copia.

    Antes de la ronda, la misma ventana dejaba `UPGRADE_PENDING` sin aviso, así que la ronda no la empeora.
- **Un `launchDue` justo antes de un desbloqueo: real, y más ancho de lo que dice el informe.**
  - Vale para cualquier pasada que superó la espera (`AutoBackup.kt:485`, `:525`) y consigue el `lock` después del desbloqueo:
    - un `runDue` de `launchDue` que esperaba el `lock` detrás de una copia lenta;
    - una pasada que cruzó un bloqueo y un desbloqueo nuevo de una bóveda que sigue siendo 3.
  - Como la cola es por orden de llegada, entra antes que `copyBeforeUpgrade` (`:749`), y en `:540` y `:833` ya ve `Unlocked`.
  - El texto sigue siendo verdadero y A6 no borra la copia de la subida (aún no existe, y `replaceTargets` está congelada). Pero la copia de la subida vuelve a esperar una pasada entera, que es el síntoma de CR-2.
  - → **RC-2**, una línea en `attempt`, que aplaza y no salta.

### 3. CR-6 ampliado a «Contactos»: aprobado

- **Por qué hacía falta:**
  - una v3 con la subida pendiente tiene `needsMaster` en falso (`VaultStore.kt` `needsMaster`: la 3 cuenta), así que `contactsGate` enseña el contenido (`ContactsView.kt:61`);
  - sus guardados fallan con `UPGRADE_*`;
  - antes salía `NOT_SAVED_STATE`, «hace falta la contraseña maestra o ya existe una identidad», que era falso.
- **Ahora:** `saveFailureMessage` pasa por `storeSaveErrorText` (`ContactsView.kt:470-474`), y no hay ningún texto nuevo.
- **Qué deja pasar `storeSaveErrorText`** (`VaultStore.kt:1757-1765`): solo constantes. Todos los `VaultLimitExceeded(…)` y `OrganizeRefused(…)` del código se construyen con constantes (comprobado con `grep`), y el mensaje de una excepción ajena nunca llega a la pantalla.
- **Faltan dos cosas en el test que fija CR-6** (`ErrorTextsTest`): ver RC-4.

### 4. Los dos tests cambiados fuera de la lista: aprobados

- **El ancla de R16** (`RedTeam20Recheck3Test.kt:245`):
  - es un cambio de firma, permitido por la cláusula «un texto o una firma de lo autorizado»;
  - además, ahora fija que la pantalla pasa `snap.rest`.
- **`deleteNeedsMasterAndName`** (`AppContainerVaultsTest`):
  - afirmaba que la bóveda quedaba abierta tras `WRONG_NAME`, que era justo el defecto de CR-7, y mi encargo pedía «el almacén nunca llega a `Unlocked`»;
  - está en `app.seguridad`, fuera de `vault/`, `imports/` y `redteam/`, así que no era una parada.

### 5. Mis dudas del Ruling

- **`entryLimitProblem` con la marca congelada: aceptado, como riesgo con nombre.**
  - Una entrada anterior a la 2.0 que esté a unos 33 B o menos de los 64 KiB (`VaultLimits.kt:19`) da `ENTRY_TOO_BIG` con «Favorita», «Etiquetar», un adjunto o «Reemplazar».
  - El motivo: `fittedForSave` comprueba la entrada cambiada (`VaultLimits.kt:197-215`, `:221-227`), y `"passwordChangedAt":` más 13 cifras entra en su tamaño.
  - El texto es verdadero, no se pierde nada y la ventana es estrecha. No saltó ningún test de topes.
- **El editor y `upsert` sin cambios: comprobado.**
  - `trySave` no mira si hay cambios y siempre manda `updatedAt = now` (`VaultEntryScreen.kt:505-555`);
  - `upsert` termina en `withVersion` (`VaultStore.kt:969`), que congela la marca y no crea versión.

  La fecha no se mueve. Que `updatedAt` y el archivo se reescriban en un guardado sin cambios ya pasaba antes y no es de CR-1.
- **«Exportar una copia antes» frente al `lock`: comprobado, sin conflicto.**
  - `exportBeforeUpgrade` solo toma el `mutex` del almacén para la instantánea y escribe fuera (`VaultStore.kt:1452-1475`). La pantalla lo llama directamente (`ui/screens/VaultUpgrade.kt:104`), sin el `lock` de `AutoBackup`.
  - Con el aviso en pantalla, `copying` está en falso (`PendingUpgrade.kt:97`, `:103`), y las copias automáticas corren a la vez. Las dos exportan el mismo 3 y no se bloquean entre sí.
- **Cómo se enseña `OrganizeRefused`: comprobado.**
  - El diálogo se queda abierto con el texto en rojo (`ui/screens/VaultOrganize.kt:347`, `:367`, `:378-379`);
  - `FoldersTest` comprueba que un rechazo no cambia nada.
- **`FOLDER_DELETE_TO_NONE`: no es falso para las entradas, pero sí inexacto para las subcarpetas, según el vocabulario de la propia app.**
  - `withoutFolder` deja a las subcarpetas con `parentId = up`, que es nulo para una carpeta de arriba del todo (`Folders.kt:208`, `:219`): pasan a la raíz.
  - La app llama a ese sitio «Arriba del todo» para las carpetas (`FOLDER_TOP`, `vault/OrganizeTexts.kt:50`, usado en `VaultOrganize.kt:156`) y «Sin carpeta» para las entradas (`FOLDER_NONE`, `:49`, usado en `VaultOrganize.kt:166` y `:174`).
  - Por eso, «sus subcarpetas se quedan sin carpeta» (`OrganizeTexts.kt:60`) se puede leer como que se deshacen.
  - Además, choca con el rechazo nuevo de CR-4, que habla del «sitio al que pasaría». → **RC-5.**

## Arreglos (ronda de después de la auditoría, mismo implementador, un commit por punto, rojo antes del arreglo)

Van junto con lo que salga de la auditoría, en una sola ronda. No paran la auditoría. Después, un clon limpio.

**RC-1 (CR-5, Menor, regresión de la ronda): el margen, dentro del objetivo del recorte**
- **Arreglo:**
  - `fittedWithSize(limit: Long = VaultLimits.VAULT_MAX_DATA_BYTES)` y `fitted(limit)` (`VaultLimits.kt:148`, `:153`, `:181`); el guardado sigue con el valor por defecto;
  - `previewDataBytes(data) = jsonBytes(data.fitted(VAULT_MAX_DATA_BYTES - PREVIEW_UNSEEN_BYTES)) + PREVIEW_UNSEEN_BYTES` (`MergePlan.kt:429`).
- **Por qué se sigue en el lado seguro:**
  - si el guardado da `TooBig` aun quitando todo el historial, la vista previa también lo quita todo, y su margen cubre lo que no ve;
  - si la vista previa da 0, el guardado cabe recortando como mucho lo mismo.
- **En el mismo commit:**
  - `importSnapshot` lleva `rest = d.copy(entries = open.trash)` (`VaultStore.kt:1021`), así que ningún campo de `VaultData` se queda fuera, ni los que se añadan mañana;
  - el KDoc de `PREVIEW_UNSEEN_BYTES` (`MergePlan.kt:434-438`) y el de `VaultSnapshot` dicen que el margen cubre lo que la contabilidad cambia entre la instantánea y el guardado sin subir `revision` (el `lastUsedAt` de `markUsed` y la lista de `COPY_DATA_MARGIN_BYTES`, `VaultLimits.kt:31-37`);
  - autorizado: `VaultStoreImportTest.kt:371` (la igualdad de `VaultSnapshot`) pasa a comparar con los datos del almacén.
- **Tests (rojo primero):**
  - `thePreviewLetsInWhatTrimmingTheHistoryMakesFit`:
    - una bóveda con unos 40 accesos de 20 versiones pequeñas (de 300 a 500 B), llenada con `trashFor` hasta unos 2 000 B por debajo de los 24 MiB;
    - se importa una nota de unos 8 000 B;
    - `applyImport` da `Done` y quedan menos versiones que antes;
    - la vista previa da 0.
    - Rojo esperado hoy: `overBytes` entre 1 y 1024.
  - `theSnapshotCarriesTheWholeVault`:
    - con `vaultTag`, `lastCheckedBackupAt` y `templatesVersion` no nulos, `snap.rest` es igual a los datos del almacén con la papelera como entradas;
    - rojo hoy: falta `vaultTag`.
  - El diferencial de 200 casos y los cuatro de los topes reales siguen en verde. Cada test tarda menos de 10 s.

**RC-2 (CR-2, Menor): una pasada que superó la espera no copia mientras dura la copia de la subida**
- **Arreglo:** en `attempt`, `AutoBackup.kt:833`, `if (store.state.value !is VaultState.Unlocked || store.upgradeCopying.value) { defer(uri); return null }`.
  - `defer` lo pone en `pending` (`:934-936`), y lo hace el `runDue` del colector de ese desbloqueo, que espera la copia (`:458`, `:525`, `:534`). Se aplaza, no se salta.
  - `copyBeforeAny` no pasa por `attempt` (`:749`, `:766`).
- **Tests en `UpgradeCopyFirstTest` (rojo primero):**
  - `aPassThatWaitedForTheLockDefersItsCopyUntilTheUpgradeCopy`:
    1. con la 1.4.3, la primera copia de la subida falla y el aviso pregunta;
    2. una copia pedida se queda retenida en su escritura (`gate`) y retiene el `lock`;
    3. se bloquea la bóveda y se llama a `launchDue`, que espera el `lock`;
    4. se desbloquea otra vez;
    5. se abre la puerta.

    El documento siguiente al retenido es `seguridad-antes-de-2-0-…`, y después de guardar la v4 llega un `seguridad-auto-…`, todo con `withTimeout`. Vale una secuencia equivalente. Rojo esperado hoy: el siguiente es `seguridad-auto-…`.
  - `theWaitEndsWhenTheUnlockCallerIsCancelled`: se cancela el trabajo que llamó a `unlock` durante la copia retenida. La copia termina y `upgradeCopying` acaba en falso. Es el quinto caso de «la espera siempre termina», y puede salir verde desde el principio, porque fija lo que ya se cumple.
- **Clases que tienen que seguir en verde:** `AutoBackup*` y `redteam.*`.

**RC-3 (CR-2, solo test): un rojo para la espera de `runChanged`**
- Ancla de fuente: en `AutoBackup.kt` hay exactamente dos `store.upgradeCopying.first { !it }`, una en `runChanged` y otra en `runDue`, cada una inmediatamente antes de su `lock.withLock {` (sin contar los comentarios), y ninguna en `copyBeforeUpgrade`.
- Rojo: quitar `:485`.

**RC-4 (CR-6, solo test): dos huecos de `theScreensSayTheStoreTextsOfAFailedSave`**
- un ancla de `storeSaveErrorText(e, fallback = newPasswordFailureMessage(` en `ui/screens/VaultBackup.kt:168`: su `try` anidado se le escapa al recorrido;
- `checked >= 19` en lugar de `>= 10` (`ErrorTextsTest.kt:147`), para que no se puedan perder sitios sin que nadie lo vea.

**RC-5 (texto): `FOLDER_DELETE_TO_NONE` con las palabras de la app**
- `OrganizeTexts.kt:60` pasa a `"Sus entradas pasan a «$FOLDER_NONE» y sus subcarpetas, a «$FOLDER_TOP»"`, que se lee «Sus entradas pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»». Sigue el patrón de `folderDeleteToParent` (`:61`).
- Test en jvm-check: el texto palabra por palabra y `ForbiddenWords`.
- Va al Abogado, que puede retocarlo siempre que nombre los dos sitios.

**RC-6 (documentación): el KDoc de `MasterPrompt`**
- `ContactsScreen.kt:129-135` dice que una comprobación de la maestra «wraps it as version 3». En la 2.0 es falso: `confirmMaster` solo comprueba (`VaultStore.kt:768-776`).
- La rama `ContactsGate.Master` (`ContactsView.kt:60-65`) no se alcanza en la 2.0:
  - una v2 solo se abre con la maestra, porque la huella rechaza todo lo que no es v4 (`Vault.kt:681-683`);
  - al abrirse, siempre deja la subida pendiente o guardada (`VaultStore.kt:640-643`);
  - un bloqueo la descarta y enseña la pantalla de bloqueo.
- El KDoc dice que es una defensa que la 2.0 no alcanza y que, si se alcanzara, la comprobación no pasaría la bóveda a la 3. Sin cambio de código.

**Se para y consulta si:**
- el rojo de RC-1 o el de RC-2 no aparece;
- RC-1 cambia algo del guardado (`fitted()` sin argumento tiene que dar lo mismo, y `RedTeam20ListCostTest`, `rt29_` y `FittedHistoryTest` siguen igual);
- RC-2 pone en rojo por comportamiento un test de `AutoBackup*` o de `redteam/`.

## Filas nuevas de `pendientes.md`

| # | Qué | Destino | Estado |
|---|---|---|---|
| 81 | RC-1 a RC-6 de este cierre | Ronda de después de la auditoría | Abierto |
| 82 | Riesgo con nombre (CR-1): una entrada anterior a la 2.0 a menos de unos 33 B de los 64 KiB da `ENTRY_TOO_BIG` al editarla sin cambiar la contraseña | — | Aceptado |
| 83 | Riesgo con nombre (CR-2): un `Error` entre `openedWith` y el `finally` de `afterUnlock` deja `copying` en verdadero hasta un bloqueo | — | Aceptado |
| 84 | El número de adjuntos: `attachmentsFit` no cuenta el tope de `BACKUP_MAX_ENTRIES`, y con 1 999 adjuntos ninguna copia se puede escribir | Consolidación de la auditoría («a comprobar», propuesta Baja) | Abierto |

## Riesgos o dudas

- **Sin ejecutar nada:**
  - RC-1 sale de leer `fittedWithSize` (`VaultLimits.kt:153-166`) y `previewDataBytes` (`MergePlan.kt:429`);
  - RC-2, del orden de llegada de la `Mutex` y de `:540` y `:833`.

  Sus rojos lo confirman o lo descartan.
- **Que no haya cancelación entre `unlockLocked` y `afterUnlock`** (`VaultStore.kt:584-594`) lo deduzco del funcionamiento de `withContext(NonCancellable)` sin cambio de *dispatcher*. El segundo test de RC-2 fija la parte comprobable.
- **No medí cuánto tarda el test de RC-1 cerca de los 24 MiB.** Por las cifras del implementador (unos 3,5 s por caso), cabe en 10 s.
- **Las partes A, B, D y E de la auditoría** no estaban en la rama al escribir esto (solo `final-audit-c.md`) y no conocen RC-1 a RC-6. El controlador los cruza en la consolidación para no contarlos dos veces: la parte D mira `AutoBackup.kt` entero, y la E, este mismo diff.
- **La fila 84** es de antes de la ronda y no la comprobé en el móvil. Falta confirmar que «Añadir adjunto» no tiene otro tope en la pantalla.

## Revisión experto-seguridad-cripto

- **Formatos y primitivas:**
  - no cambian `SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, el AAD, las longitudes ni Argon2id (los `--stat` de `Backup.kt`, `core/` y `crypto/` están vacíos);
  - `passwordChangedAt` ya existía en la v4, y una v2 o v3 no se escribe nunca (`EntryHistory.kt:33-34`, `:47`).
- **CR-7 (CWE-226 y CWE-212):**
  - la clave se pone a cero en el `finally` (`VaultStore.kt:800`), y `json.fill(0)` va en `VaultCrypto.read` (`Vault.kt:458-482`);
  - no publica estado, no toca el reloj de A11, la papelera, el envoltorio ni la subida, ni escribe ninguna copia;
  - una maestra equivocada no borra nada (`AppContainer.kt:731`).
  - Informativo, sin cambio: para leer el nombre decodifica toda la bóveda. En una v2 o v3 el nombre siempre es "" (`Vault.kt:466-470`), pero cualquier desbloqueo ya decodifica lo mismo, así que no es una regresión.
- **CR-1 (integridad de la señal de «Salud», CWE-1287):**
  - la marca se congela desde lo guardado antes del cambio, y no crea versiones falsas (`EntryHistory.kt:24-25`);
  - el riesgo de los 64 KiB (fila 82) va del lado seguro.
- **CR-2:**
  - A6 no amplía lo que borra: la copia de la subida queda en `importCopies`, y `replaceTargets` está congelada (`UpgradeCopyFirstTest`);
  - RC-2 no añade ningún cerrojo y reutiliza `pending`.
- **CR-5 y RC-1:**
  - RC-1 mantiene las dos garantías: un `TooBig` del guardado siempre da más de 0 en la vista previa, y una vista previa de 0 siempre se guarda;
  - la sesión borra `rest` donde borra `existing` (`ImportSession.kt:251-252`, `:462-463`), así que la papelera no vive más que las entradas.
- **CR-6:** solo pasan constantes (`VaultStore.kt:1757-1765`). En «PIN y códigos» sigue sin registrarse nada (`CodesScreen.kt`, diff).

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - la espera es `StateFlow.first {}`, que se puede cancelar, y va fuera del `lock` (`AutoBackup.kt:485`, `:525`);
  - `copyBeforeUpgrade` no espera (`:749`);
  - la cola de la `Mutex` (`:448`) sigue el orden de llegada, y por eso queda la ventana de RC-2, que se cierra donde se decide cada destino (`:833`);
  - con un solo colector (`:458`), la espera de `runChanged` es una defensa (RC-3).
- **Ciclo de vida:**
  - `ContactsFrame` lee `pendingUpgrade` y `upgradeCopying` con `collectAsStateWithLifecycle` (`ContactsScreen.kt:96-99`);
  - la decisión es una función pura que se prueba en jvm-check (`ContactsView.kt:60-65`);
  - el error de «Borrar carpeta» vive en un `remember` y no es secreto (`VaultOrganize.kt:347`).
- **Interfaz:**
  - nueve pantallas dicen el texto del almacén;
  - en la ficha, `failed` pasa de `Boolean` a texto (`VaultEntryScreen.kt:214`, `:244`);
  - no hay ningún `rememberSaveable` nuevo.
- **Rendimiento:**
  - RC-1 no añade pasadas (es el mismo `fitted`, con otro objetivo), y la vista previa sigue en `Dispatchers.Default`, cancelable por `collectLatest` (`ImportScreen.kt:210-220`);
  - `RedTeam20ListCostTest` no se mueve en la ronda (B 2 954 912 B, D 1,213x).
- **Tests:** son deterministas, con `withTimeout`, sin `sleep` y con la regla de 30 s (`UpgradeCopyFirstTest`). El `finally` del test abre la puerta del desbloqueo, que no se cancela.

## Textos para el Abogado Digital

- `UPGRADE_COPYING` (`VaultStore.kt:1748-1749`): es nuevo.
- `FOLDER_DELETE_NAME_TAKEN` (`Folders.kt:39-40`): es nuevo.
- `FOLDER_DELETE_TO_NONE` (`OrganizeTexts.kt:60`): cambia con RC-5 a «Sus entradas pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»». Se puede retocar, siempre que nombre los dos sitios y no diga que las subcarpetas desaparecen.
- `NEEDS_MASTER_NOTICE` (`ContactsView.kt:68`):
  - en la 2.0 no se enseña (RC-6), y si se enseñara diría algo falso («La bóveda se abrió con la huella»);
  - quitarlo no rompe nada técnico, pero exige tocar código. Si el Abogado lo pide, va a esa ronda.
- **«Contactos» enseña ahora textos que ya existían** donde antes decía `NOT_SAVED_STATE`: `NEEDS_MASTER_FOR_CONTACTS`, `ONLY_ONE_IDENTITY`, `UPGRADE_PENDING`, `UPGRADE_COPYING` y los de los topes.
- `importTooBigText` (`ImportTexts.kt:371-381`): con RC-1 deja de decir «sobran unos 1 KB» a una importación que cabe.

Modelo: el director (esfuerzo extra).
