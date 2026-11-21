# Revisión de la tarea 10, fase C (textos de error), `ce147ba..b1c9d35`

Revisor independiente. Leídos `CLAUDE.md`, `experto-seguridad-cripto/SKILL.md` (principios y «Trabajo conjunto»), `references/auditoria.md` (errores y excepciones), las skills `desarrollo-movil-senior` y `auditoria-seguridad-movil`, los Rulings de T10 (`progress.md:192`, `:195`, `:200`, `:204`), el plan (tarea 10, `plans/…2.0.md:301-322`), el diseño (`specs/…2.0-design.md:143`, `:226`; Formato v4 `:67`, `:266`), R-10 del Abogado inicial (`abogado-2.0-inicial.md:309-311`), el inventario, el informe y el diff. He comprobado cada fila contra el código y buscado lo que falta.

Prueba: `./gradlew -p jvm-check test --tests 'app.seguridad.vault.ErrorTextsTest' --tests 'app.seguridad.tools.ToolTextsTest'`, en verde. No he corrido la suite entera, por el aviso de memoria.

## Veredicto

**Aprobada con cambios.**

Los seis textos que cambian son verdad en todos sus usos y la acción se puede hacer desde donde sale cada uno. No se guardan ni se comparan con un valor guardado. Tampoco tocan formatos, permisos, dependencias, red ni registros. Los textos de A6 están intactos.

Los cambios que pido son del **inventario** y de **`pendientes.md`**, no de los seis textos:
- faltan familias enteras de errores de la bóveda y de las copias;
- una fila afirma algo falso sobre lo que distingue el código, y eso tapa un error de veracidad que ya estaba («Contraseña incorrecta» ante falta de memoria);
- varios motivos de «se deja» estiran «congelado» o «es dato» más allá de lo que dice el código.

## Issues

### Critical

Ninguno.

### Important

**I-1. Al inventario le faltan textos de error de la bóveda y de las copias.** El Ruling de cierre de la fase B pide «cada texto de error de la bóveda, las copias y la importación» (`progress.md:204`). Faltan estos:

1. **El texto por defecto de `CryptoFailure`, «Contraseña incorrecta o datos dañados»** (`crypto/Crypto.kt:198`). Es el error más frecuente de una copia: sale cuando su contraseña es equivocada (`vault/Vault.kt:360`, `:364`) o sus datos no se descifran (`Vault.kt:379-390`).
   - **Dónde sale:**
     - «Importar copia» (`ui/screens/VaultBackup.kt:183`, `e.message`);
     - «Importar como bóveda nueva» (`AppContainer.kt:763`);
     - «Comprobar una copia» (`vault/BackupCheckSession.kt:130`);
     - la pantalla de bloqueo tras la **huella**, por `biometricUnlockError` (`vault/VaultStore.kt:90`). Allí se lee «Contraseña incorrecta» cuando no se ha escrito ninguna contraseña.
   - **Se comparte** con «Cifrar texto» (`ui/screens/SecurityToolsScreen.kt:296`).
2. **«Parámetros de cifrado no válidos»** (`crypto/Crypto.kt:73`, `checkSane`; `vault/Vault.kt:356`). Sale al importar o comprobar una copia «Alto» en un móvil con poco *heap*.
3. **Los fallos de la huella al desbloquear**, que llegan a la misma línea de error que `BIOMETRIC_UNLOCK_FAILED` (`ui/screens/VaultScreen.kt:434`, `onError = { error = it }`):
   - «No se pudo usar la huella» (`android/BiometricVault.kt:557`, `:599`, `:600`, `:629`, `:640`);
   - «No se pudo desbloquear con la huella» (`:571`, `:645`);
   - «La huella no está activada» (`:597`).

   Es el mismo caso que `BIOMETRIC_UNLOCK_FAILED`, que sí gana «Escribe la contraseña maestra.», y con casi las mismas palabras. Son literales propios de cada sitio, solo del camino de desbloqueo: ni se comparan ni están en el anexo, los tests ni `.maestro/`. Se arreglan solo con cadenas. Ojo: el `:469` («No se pudo usar la huella», al **activar**) es otro literal y no debe llevar esa línea.
4. **Literales de las pantallas:**
   - `ui/screens/VaultAttachments.kt:71`, `:92`, `:103` y `:171`;
   - `ui/screens/VaultBackup.kt:93` y `:151`;
   - `ui/screens/VaultEntryScreen.kt:242` («No se pudo mover a la papelera (¿almacenamiento lleno?)»), y `:405`, `:422`, `:470`, `:482` y `:701` (importar códigos 2FA en el formulario);
   - `ui/screens/SettingsScreen.kt:167` (`SETTING_SAVE_FAILED`) y `:331`.
5. **De `vault/TrashTexts.kt`:** `SAVE_LOCKED` (`:146`), `ENTRY_IN_TRASH` (`:143`), `HISTORY_RESTORE_REFUSED` (`:140`) y `TRASH_RESTORE_NONE` (`:30`). También `EXPIRY_BAD_DATE` (`vault/EntryForm.kt:43`) y `BACKUP_ENTRY_TOO_BIG_V3` (`vault/Backup.kt:85`), que solo están implícitos en un rango de líneas.
6. **De `imports/`:** `IMPORT_KDF_BUSY` (`ImportTexts.kt:258`; ya dice qué hacer) e `importTooBigText` (`:363`) no están en la lista de la sección 3. Las razones de `SkipReason` (`imports/ImportModel.kt:40-54`) son las líneas de «No entran»: dicen por qué no entra cada elemento, no qué hacer, y no son errores de la acción. Basta una fila que diga que se dejan.

He comprobado que `VaultScreen.kt:429` es el único sitio que llama a `BiometricVault.unlock(`, así que los literales del punto 3 solo salen en la pantalla de bloqueo, junto al campo de la maestra (`:441`).

**Arreglo:** una fila para cada uno.
- Para los del punto 3, cambiarlos (solo cadenas, cada literal de desbloqueo con «. Escribe la contraseña maestra.») o decir por qué no.
- Para los de los puntos 1 y 2: «se deja», porque la constante se comparte con «Cifrar texto», y una fila de `pendientes.md`, porque un texto propio de la copia («… Vuelve a elegir la copia y escribe su contraseña.») necesita código en los sitios donde se enseña.
- El resto, con su motivo. La mayoría ya dice qué hacer («ábrela y vuelve a exportarla», «Elige otra»), y los de «La bóveda se bloqueó: no se ha …» pueden quedarse.

**I-2. Veracidad: «Contraseña incorrecta» tapa errores que el código sí distingue, y la fila del inventario lo oculta.** En `ui/screens/VaultScreen.kt:453`, toda `CryptoFailure` que no es `NOT_A_VAULT` se enseña como «Contraseña incorrecta». Eso incluye dos errores que llegan con su propio mensaje:
- **`KDF_OUT_OF_MEMORY`:** `argon2id` (`crypto/Crypto.kt:237`) → `outOfMemoryAsFailure` (`:88-91`) → `VaultCrypto.unlock` (`vault/Vault.kt:362`) → `derive` (`vault/VaultStore.kt:397`, `:577`), sin cambiar el mensaje;
- **«Parámetros de cifrado no válidos»:** `checkSane` con `cannotFit` (`crypto/Crypto.kt:72-73`, `vault/Vault.kt:356`).

Una bóveda «Alto» en un móvil sin memoria suficiente dice «Contraseña incorrecta». El usuario puede acabar pulsando «He olvidado la contraseña» con la maestra buena, y eso borra la bóveda. El KDoc de `argon2id` (`crypto/Crypto.kt:216-217`: «opening «Alto» data on a small phone fails with a clear message») y la R38/R40 de la 1.4.0 («así `KDF_OUT_OF_MEMORY` dice la verdad», `docs/auditorias/2026-11-08-auditoria-1.4.0.md:228`) cuentan con que ese texto llega a la pantalla. En la de bloqueo no llega. He buscado una aceptación anterior y no la hay: ni en `sdd-1.3.0/progress.md` (F5 y M2, que solo llevan `newPasswordFailureMessage` a crear y cambiar la maestra) ni en las auditorías de `docs/auditorias/`. Así que no reabre ninguna decisión.

Dos filas del inventario son falsas:
- la de «Contraseña incorrecta» dice que lo que distingue el código es «AES-GCM no separa…», y el código distingue además esos dos mensajes;
- la de `KDF_OUT_OF_MEMORY` dice que sale al «abrir», y en la pantalla de bloqueo no sale.

**Arreglo:** corregir las dos filas y abrir una fila nueva (41) en `pendientes.md`, para la revisión de la rama (código). La propuesta para esa fila: en `VaultScreen.kt:453`, enseñar tal cual `NOT_A_VAULT`, `KDF_OUT_OF_MEMORY` y el mensaje de `checkSane`, y «Contraseña incorrecta» para lo demás. Así «Contraseña incorrecta» conserva sus palabras y solo sale donde puede ser verdad. Es código, así que no va en esta fase (Ruling «solo cadenas»). Es de veracidad: lo apunto para el Abogado y para el dueño.

### Minor

**m-1. Varios motivos de «se deja» estiran «es dato» o «congelado».** En casi todos el resultado está bien, pero el motivo no es el que dice el código:
- **`IMPORT_COPY_*` no se guardan en `settings.json`** (inventario 0.3). Son `CopyResult.Failed`, solo en memoria (`vault/AutoBackup.kt:758-788`; `ui/screens/ImportScreen.kt:345-387`). Lo que sí se guarda es lo de `AUTO_BACKUP_*`: `lastError` y las claves de `replaceTold` (`settings/Settings.kt:384-410`), que se comparan con su texto (`vault/AutoBackup.kt:888-897` y `:1110-1124`). Ahí el motivo es verdad.
- **Las comparaciones de `IMPORT_COPY_LOCKED` e `IMPORT_COPY_VAULT_CHANGED`** (`AutoBackup.kt:752`), **`IMPORT_WRONG_PASSWORD`** (`imports/ImportSession.kt:215`), **`NOT_A_VAULT`** (`VaultScreen.kt:453`) y **`UPGRADE_PENDING`** (`VaultStore.kt:1647`) comparan la constante consigo misma, en memoria. Cambiar el valor no cambia el comportamiento. El Ruling dice «se compara → se para», y el implementador lo ha cumplido al pie de la letra. Pido que el controlador diga en un Ruling si «se compara» se refiere solo a un valor guardado o recibido. Mientras tanto, estas filas deberían decir «comparada con la misma constante; se deja por el Ruling al pie de la letra» y no «sería un cambio de comportamiento». Las cinco están además fijadas o ya dicen qué hacer, así que el resultado no cambia.
- **`ImportTextsTest` no congela «todos los de `ImportTexts.kt`».** Solo fija palabra por palabra estos:
  - `IMPORT_TRADEMARKS` (`ImportTextsTest.kt:221`);
  - `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED` (`:279`);
  - `IMPORT_REPLACE_NOTE` (`:292`);
  - `IMPORT_TOO_MANY_ITEMS` (`:301`);
  - `IMPORT_NOT_PROTECTED` e `IMPORT_REMOVE_EXPLAIN` (`:245-256`).

  A los demás solo les pasa las palabras prohibidas (`:194-217`). Los Rulings R19, R21 y R22 fijan lo que dicen, no sus palabras. El resultado («se deja») se sostiene por otro motivo: cada `ErrorText` de la pantalla de importar tiene al lado el botón «Elegir otro archivo» (`ImportScreen.kt:523-559`, `:612-616`), así que el qué hacer ya está a la vista. Hay que corregir el motivo.
- **Los fijados por un Ruling.** Según el Ruling posterior (`progress.md:204`), conservan sus palabras, y una línea añadida debajo tiene que llevar su propio Ruling. Así que «congelado» basta como motivo para dejarlos como están: no pido cambiarlo. Los he mirado uno por uno y casi todos ya dicen qué hacer. Lo que sí falta, I-1.3 y m-4, no está fijado.

**m-2. `BACKUP_NEWER`: o está fijado y la línea nueva necesita su Ruling, o no lo está y solo falla el comentario.** El diseño (`:67`, `:266`) describe lo que dice la **1.4.3** ante la marca nueva, y eso va dentro de su APK. No fija la constante de la 2.0. El controlador tiene que elegir una de dos:
- **(a) Está fijado** (como dice el inventario, 0.2). Entonces, según `progress.md:204`, la línea añadida «lleva su Ruling», y hoy solo cita el diseño. Hace falta un Ruling que la nombre.
- **(b) No está fijado** (es lo que creo yo). Entonces la línea añadida vale sin Ruling, y lo único mal es el motivo: `vault/Backup.kt:80-81` dice «1.4.3 says them» y `ErrorTextsTest.kt:18` dice «the reader of 1.4.3», que no lee esta constante. El inventario debería decir «se conservan las palabras por coherencia con lo que dice la 1.4.3». El texto: «Actualiza Clavero para abrirla.» solo sale cuando la marca pasa de `BACKUP_NEWEST` (`Backup.kt:327`). Es el único caso en que actualizar puede abrirla, así que no nombra una causa que el código no distinga. Lo acepto, con una nota para el Abogado: «para abrirla» suena a promesa.

**m-3. La fila 37 de `pendientes.md` y su fila del inventario están mal descritas.**
- `VaultStore.kt:1292` es el sitio de **añadir un adjunto** (`AttachmentLimit`), no el de una copia. El de la copia es solo `vault/BackupCheck.kt:205` (`CryptoFailure`). Son dos sitios que lanzan el error por separado, así que separarlos solo pide una constante nueva en `:1292`. Sigue siendo código (bien en `pendientes.md`), pero la descripción tiene que decirlo.
- Falta un detalle de veracidad. `BackupCheck.kt:204` lanza «Los adjuntos de la bóveda pasan de 50 MB en total» también cuando **un** adjunto tiene un tamaño fuera de 0..5 MB, también negativo (`VaultStoreTest.kt:166`, con un tamaño de -1). Ese texto es falso en ese caso. Hay que añadirlo a la fila 37.

**m-4. `TAG_NOT_VALID` («Esa etiqueta no es válida.», `vault/Folders.kt:37`) está en el grupo de «ya dicen qué hacer», y no lo dice.** Sale en dos sitios, con condiciones distintas:
- `ui/screens/VaultOrganize.kt:184`, «Etiquetar» en la selección: `normalizedTag` pasa las comas a espacios (`vault/Selection.kt:108`), así que solo sale con un nombre vacío;
- `vault/Folders.kt:265`, `withTagColor`: `normalizeTags(tag).singleOrNull()` (`vault/EntryQuery.kt:48-50`) también da null con dos o más etiquetas, separadas por una coma.

No se guarda ni se compara. Se puede arreglar solo con la cadena, pero la línea tiene que ser verdad en los dos sitios. Por ejemplo, «Esa etiqueta no es válida. Escribe un solo nombre, sin comas.». Antes hay que mirar si en `withTagColor` el usuario escribe el nombre o lo elige de una lista; si lo elige, la línea no le sirve. Si no se arregla, hay que corregir el motivo.

**m-5. A la fila 40 le falta `README.md:320`,** que cita el texto de antes de `COPY_DAMAGED` («da «La copia está dañada: no se puede importar»»). Hay que añadirlo junto a la línea 329.

**m-6. La duda 1 del informe dice «basta un `\n` en los seis», y no es verdad.** `ToolTextsTest.quotes()` lee el anexo línea a línea (`ToolTextsTest.kt:39-57`). Un `BIOMETRIC_UNLOCK_FAILED` con salto de línea no podría estar nunca en el anexo y rompería `everyAppTextIsInTheAnnex` (`:71-76`). Lo mismo con `ErrorTextsTest.kt:27`. Hay que corregir la frase del informe (ver la opinión, abajo).

**m-7. El inventario no cita R-10 del Abogado inicial** (`abogado-2.0-inicial.md:309-311`), que es la regla de esta fase: ningún error propone desinstalar, «Seguir sin copia» ni borrar sin decir qué se pierde, y ninguno culpa al usuario. Los seis cumplen. Pero `UPGRADE_EXPORT_FAILED` («… Vuelve a intentarlo o sigue sin copia.», `ui/screens/VaultUpgrade.kt:46`) propone «Seguir sin copia». Lo que cuesta está a la vista en el mismo diálogo (`UPGRADE_NOT_BACK`, `:39`), así que creo que cumple. Hay que apuntarlo para el Abogado.

**m-8 (informativo).** El `?: "Contraseña incorrecta"` de `ui/screens/VaultBackup.kt:183` no se usa nunca: `CryptoFailure` siempre lleva mensaje. Sin cambio.

## Comprobación fila por fila del inventario

**C** = conforme; **NC** = no conforme.

### 0.1. A6

| Fila | Resultado | Por qué |
|---|---|---|
| `AUTO_BACKUP_REPLACED`, `_SEVERAL`, `_REPLACE_WAITS`, `autoBackupUnlistedText`, `a6PartialText`, `LEGACY_A6_REQUEST` | C | Sin cambios en el diff. `AUTO_BACKUP_REPLACE_WAITS` se compara en `AutoBackup.kt:1110` y `:1120` |
| Los `ReplaceStatus.Failed.message` (`AUTO_BACKUP_NO_PERMISSION` … `_NO_RETRY`) | C | Se guardan como `lastError` (`Settings.kt:384-386`) y como claves de `replaceTold` (`:410`). Se comparan en `AutoBackup.kt:888`. Sin cambios |

### 0.2. Fijados por un Ruling

| Fila | Resultado | Por qué |
|---|---|---|
| `FINGERPRINT_STALE` | C | Ya dice qué hacer |
| `MASTER_TOO_WEAK` y `MASTER_TOO_SHORT` | C | CRY-A01. El primero ya dice qué hacer; el segundo es la regla en sí |
| `PASSWORD_NOT_TEXT` | C | «escríbela de nuevo» |
| `NOT_A_VAULT` y «Contraseña incorrecta» | **NC** | Ver I-2: «Contraseña incorrecta» tapa errores que el código distingue |
| `CLOCK_CHANGED_TEXT` | C | |
| `UPGRADE_*` | C | O-3. Comprobado `VaultUpgrade.kt:38-61`; R-10, ver m-7 |
| `BACKUP_NEWER` | C, con m-2 | El motivo citado no es el del diseño |
| Todos los de `ImportTexts.kt` | **NC** (motivo) | m-1: `ImportTextsTest` fija pocos. El resultado se sostiene por el botón «Elegir otro archivo» |
| `VAULT_DELETE_*`, `VAULT_DELETED`, `TOO_MANY_VAULTS` | C | Ya dicen qué hacer, salvo `VAULT_DELETE_NAME_WRONG` y `VAULT_DELETE_DAMAGED`, que dicen lo que pasó dentro de su propio diálogo. R-9 no fija sus palabras; motivo aceptable |
| `COPIES_KEEP_IT`, papelera e historial | C, con I-1.5 | O-2 fija los textos de borrar. Los errores de `TrashTexts.kt` no están listados uno a uno |

### 0.3. Datos

| Fila | Resultado | Por qué |
|---|---|---|
| `AUTO_BACKUP_*` | C | Se guardan de verdad |
| `IMPORT_COPY_*` se guardan | **NC** | No se guardan (m-1) |
| `IMPORT_COPY_LOCKED` e `IMPORT_COPY_VAULT_CHANGED` | C, con m-1 | Se comparan consigo mismas, en memoria |
| `IMPORT_WRONG_PASSWORD` | C, con m-1 | Ídem. No lo fija `ImportTextsTest` |
| `NOT_A_VAULT` y `UPGRADE_PENDING` | C, con m-1 | Ídem |

### 1. La bóveda

| Fila | Resultado | Por qué |
|---|---|---|
| «Contraseña incorrecta» | **NC** | I-2 |
| «No se pudo abrir la bóveda» (`VaultScreen.kt:455`) | C | Cualquier excepción; no hay una acción veraz |
| «No se pudo crear la bóveda. Inténtalo de nuevo.» | C | Lo repite el usuario con el mismo botón; no promete nada |
| `BIOMETRIC_UNLOCK_FAILED` (cambia) | C | Único uso: `UnlockVault` (`VaultScreen.kt:430-434`), con el campo de la maestra en la misma pantalla (`:441`). La maestra no depende de la huella. No se guarda; está en `ALL_TOOL_TEXTS` (`ToolTexts.kt:777`) y en el anexo al día. Ver I-1.3 por sus hermanos |
| `FINGERPRINT_STALE`, `CLOCK_CHANGED_TEXT` y `UPGRADE_NEEDS_MASTER` | C | |
| `UPGRADE_PENDING` | C | Ya dice qué hacer |
| Topes de `VaultLimits.kt:52-65` | C | Los 13 dicen qué hacer (comprobado uno por uno) |
| `Folders.kt:30-37` | **NC** | `TAG_NOT_VALID` no lo dice (m-4); los demás, sí |
| `NEEDS_MASTER_FOR_CONTACTS` y `ONLY_ONE_IDENTITY` | C | Son de Contactos; el primero ya dice qué hace falta |
| `TRASH_SAVE_FAILED` | C | «¿Queda espacio en el móvil?» no afirma la causa. El motivo «Ruling T3 N-1» es de qué se enseña, no de sus palabras; motivo aceptable |
| `HISTORY_ALL_LOCKED` | C | |
| `VAULT_FAILED` | C | Lo repite el usuario |
| `VAULT_NEW_NEEDS_OPEN` y `VAULT_DELETE_*` | C | |
| `ATTACHMENT_TOO_BIG` (cambia) | C | Solo en `Attachments.read` y `add(plain)` (`Attachments.kt:83`, `:90`). Las dos se llaman solo desde `addAttachment` (`VaultStore.kt:1287`, `:1294`) y se enseñan en `VaultAttachments.kt:73`, con el selector de adjuntos en la misma pantalla |
| `ATTACHMENTS_TOTAL_TOO_BIG` | C (resultado), **NC** (descripción) | m-3 |
| `ATTACHMENT_DAMAGED` | C | |
| Los tres literales de los adjuntos | C | Ver I-1.4 por los que faltan en ese mismo archivo |
| `KDF_OUT_OF_MEMORY` | **NC** | I-2: en «abrir» no sale. La fila 38 está bien en lo demás |

### 2. Las copias

| Fila | Resultado | Por qué |
|---|---|---|
| `BACKUP_DAMAGED` (cambia) | C | Sale en `BackupReader` (`Backup.kt:278-360`), que solo usan `importReplace` (`VaultStore.kt:1448`) y `checkBackup` (`BackupCheck.kt:86`), y en los snackbars de la cabecera (`VaultBackup.kt:109`, que el inventario no cita, y `VaultPickerScreen.kt:194`). Se llega a «Importar copia» desde Ajustes y desde «Crear» («Importar una copia exportada» → `actions.import`, `VaultScreen.kt:284`), a «Importar como bóveda nueva» (`AppContainer.kt:593-599` → `:763`) y a «Comprobar una copia» (`BackupCheckSession.kt:130`). Cada una tiene su botón para elegir otra. «Comprobar una copia» se abre desde Herramientas, pero el error sale dentro de esa pantalla, con su botón: el modo sencillo no lo impide |
| `BACKUP_NEWER` (añade una línea) | C, con m-2 | |
| `BACKUP_TOO_BIG`, `_ENTRY_TOO_BIG`, `_TOO_MANY` | C | Falta citar `_V3` (I-1.5) |
| `BACKUP_DOES_NOT_FIT` y `_V3` | C | Comprobado: el diálogo del paso a v4 enseña el mensaje de la `CryptoFailure` (`VaultUpgrade.kt:61`) y no deja guardar (`UPGRADE_PENDING`). La fila 39 se sostiene |
| `COPY_DAMAGED` (cambia) | C | `checkCopyEntries` y `checkCopyStructure` (`BackupCheck.kt:194-218`), llamadas solo desde `replace` (`VaultStore.kt:1478`, `:1482`; también el JSON de la 1.0, `importText` `:1418`) y desde `checkBackup` (`BackupCheck.kt:95-96`). En todos los sitios se puede elegir otra copia |
| `SEVERAL_IDENTITIES` | C | |
| `IMPORT_TOO_BIG` (cambia) | C | Solo en `VaultStore.kt:1443`: no es una cabecera `seguridad-backup` y pasa de 40 MB. Un `.sgbak` real siempre pasa por `isBackup` (`Backup.kt:119-126`), y el JSON de la 1.0 de más de 40 MB nunca vale, así que «Elige una copia .sgbak de Clavero» es verdad en sus tres caminos. `.sgbak` es la extensión de toda copia (`Backup.kt:98`) y coincide con `JSON_HAS_ATTACHMENTS`. Pasar de `private` a `internal` no cambia el valor |
| `JSON_HAS_ATTACHMENTS` y `REPLACE_NEEDS_OPEN` | C | |
| `IMPORT_SETTINGS_FAILED` | C | |
| Los tres «No se pudo …» de Ajustes | C | |
| «No se pudo leer el archivo» y `BACKUP_CHECK_READ_FAILED` | C | |
| `BACKUP_CHECK_FAILED`, `_NOT_SGBAK` y `_TOO_LONG` | C | Comprobado en `BackupCheckTexts.kt:23-32` |
| `AUTO_BACKUP_*` de error | C | |
| `AUTO_BACKUP_NOW_NEEDS_OPEN`, `_ALREADY_TARGET` y `_TOO_MANY` | C | Comprobado en `BackupTargetTexts.kt:21-52` |
| `UPGRADE_EXPORT_FAILED`, `_SAVE_FAILED` y `_AUTO_COPY_FAILED` | C | Ver m-7 |
| Falta: la `CryptoFailure` por defecto y `checkSane` | **NC** | I-1.1 y I-1.2 |

### 3 y 4. Importación y fuera de alcance

| Fila | Resultado | Por qué |
|---|---|---|
| 3. `ImportTexts.kt` | C (resultado), **NC** (motivo) | m-1 |
| 4. Fuera de alcance | C | `crypto/pk/*`, `Totp.kt`, `NOT_COMPATIBLE` y `tools/*` no se tocan. Pero la `CryptoFailure` por defecto y `checkSane`, que viven en `crypto/Crypto.kt`, sí llegan a las copias (I-1) |

### `pendientes.md`

| Fila | Resultado | Por qué |
|---|---|---|
| 37 | NC (descripción) | m-3 |
| 38 | C, con I-2 | Añadir que en la pantalla de bloqueo ni siquiera sale |
| 39 | C | |
| 40 | NC | Falta `README.md:320` (m-5) |

### Otras comprobaciones

| Qué | Resultado |
|---|---|
| Anexo de la 1.3.0 (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:116`) | C: la cita «…» es una sola, en una línea, igual que la constante, y la nota de la 2.0 va fuera de las comillas, así que `quotes()` no la toma. Respeta la intención de `ToolTextsTest`: el anexo sigue siendo la lista completa para el Abogado. `ToolTextsTest`, en verde |
| `.maestro/`, `docs/privacidad.html` y la ficha de Play | C: no citan ninguno de los seis |
| Tests | C: `VaultStoreTest` y `AttachmentsTest` al día; `ErrorTextsTest`, en verde, con las palabras prohibidas |
| `SourceTextsTest` y `ForbiddenWords` | C: «Elige», «Actualiza» y «Escribe» no están prohibidas (`ErrorTextsTest.kt:28`) |
| Nombres de otras apps | C: ninguno en los textos nuevos |
| M-3 | C: los seis se enseñan a la vista (snackbars o `Text` de error), en una o dos frases y sin `maxLines` en esas pantallas |
| Español | C |
| Alcance | C: solo cadenas y un `private` → `internal`. A6, formatos, permisos, dependencias, red y registros, sin cambios |

## Opinión sobre las dudas

1. **Dos frases en la misma cadena, o un salto de línea.** Me quedo con las dos frases.
   - M-3 habla de «una o dos líneas a la vista»: líneas que se leen, no un `\n`.
   - Los textos ya aprobados por el Abogado usan esa forma (`AUTO_BACKUP_NO_PERMISSION`, `VAULT_TOO_BIG`).
   - Un `\n` no saldría gratis: rompe el anexo (`ToolTextsTest.quotes()` lee por líneas; m-6) y deja una línea suelta en snackbars estrechos.

   Pido que el controlador lo apunte como Ruling: «debajo» del diseño y «otra línea» del plan se cumplen con una segunda frase en la misma cadena.
2. **«Contraseña incorrecta».** Estoy de acuerdo en no cambiar sus palabras: el código no distingue una contraseña equivocada de una clave envuelta dañada. Añadirle «Vuelve a escribirla.» reforzaría esa causa, así que tampoco lo haría. Pero la duda se queda corta: el mismo texto tapa `KDF_OUT_OF_MEMORY` y `checkSane`, que el código sí distingue (I-2). Eso se arregla con código (fila 41) y, sobre todo, evita que alguien borre su bóveda con «He olvidado la contraseña» teniendo la maestra buena. Va al Abogado y al dueño.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza.** Los mensajes son de una app local, sin servidor. Un texto de error no es un oráculo útil: todos son fijos y no dan más información de la que ya daba la constante anterior. Separar «sin memoria» de «contraseña incorrecta» (I-2) tampoco da a un atacante nada de la contraseña: la falta de memoria depende del móvil y del coste, no del secreto.
- **Sin datos en los mensajes.** Los seis son constantes. `BIOMETRIC_UNLOCK_FAILED` sigue tapando el texto de cualquier excepción (LIBRE-4, `vault/VaultStore.kt:89-92`; test `VaultStoreTest.kt:944-946`). Ningún mensaje lleva una ruta, una URI, un nombre de adjunto ni un dato de la bóveda.
- **Veracidad (regla del proyecto).**
  - Ninguno de los seis nombra una causa que el código no distinga. «Elige otra copia» no elige entre dañada y ajena (`vault/Backup.kt:79`).
  - Ninguno promete un reintento: `ErrorTextsTest.kt:29-30` busca «se volverá a intentar» e «inténtalo».
  - Lo que falta en veracidad es lo de antes de esta fase: I-2 (`ui/screens/VaultScreen.kt:453`) y m-3 (`vault/BackupCheck.kt:204`).
- **Fail-open y excepciones tragadas.** No hay ninguno nuevo. Las ramas que lanzan cada error no han cambiado.
- **Formatos.** `BACKUP_MARKER`, `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup`, sin cambios. La marca de la copia se sigue leyendo igual (`vault/Backup.kt:319-327`).
- **Primitivas, dependencias y red.** Sin cambios.

## Revisión Ingeniero Móvil Senior

- **Dónde salen.**
  - Snackbars: `ui/screens/VaultBackup.kt:109` y `:190`; `ui/screens/VaultPickerScreen.kt:194`; `AppContainer.kt:769`, por `vaultsNotices`, que vive en memoria y no se guarda.
  - `Text` de error: `ui/screens/VaultScreen.kt` (`UnlockVault`), `ui/screens/VaultAttachments.kt:73` y el `session.error` de «Comprobar una copia».

  Todos a la vista (M-3). Ninguno tiene `maxLines`, así que al 200 % se ajustan solos.
- **Estado y ciclo de vida.** Ninguno se guarda en `rememberSaveable` ni en `settings.json`. El `error` de `UnlockVault` es un `remember` y se pierde al girar, como antes (BOV-3, documentado en `vault/VaultStore.kt:111-112`).
- **Se llega a la acción.**
  - El campo de la maestra está en la misma pantalla que la huella.
  - El selector de adjuntos está en `VaultAttachments`.
  - Para elegir otra copia: «Importar copia» (Ajustes, y desde «Crear» con «Importar una copia exportada»), «Importar como bóveda nueva» (Bóvedas) y el botón de «Comprobar una copia».
  - Con la bóveda bloqueada, los errores de una copia no llegan a salir:
    - «Importar copia» la rechaza antes de leerla, con `REPLACE_NEEDS_OPEN` («Abre la bóveda antes de importar una copia», `vault/VaultStore.kt:1434`, `checkCanReplace`);
    - «Importar como bóveda nueva» necesita una bóveda abierta (`canAddVault`, `AppContainer.kt:613` y `:626`; Ruling T2), y la pantalla dice `VAULT_NEW_NEEDS_OPEN` (`ui/screens/VaultPickerScreen.kt:282`);
    - desde «Crear» no hay ninguna bóveda que abrir.

    Así que, cuando un error dice «Elige otra copia», el botón para elegirla se puede usar.

  El modo sencillo solo filtra la lista de Herramientas: no esconde ninguna de estas pantallas una vez dentro.
- **Accesibilidad.** Los textos de error de `UnlockVault` no anuncian sus cambios en una región viva, a diferencia del de `newMasterProblem` (`ui/screens/VaultScreen.kt:365`). Ya era así antes de esta fase y no lo pide la tarea; sugiero apuntarlo a la fila del móvil de TalkBack (34).
- **Concurrencia y rendimiento.** Nada cambia.

## Textos para el Abogado Digital (revisión final)

- Los seis textos cambiados, con sus palabras finales:
  - «No se pudo abrir la bóveda con la huella. Escribe la contraseña maestra.»
  - «El adjunto pasa de 5 MB. Elige un archivo más pequeño.»
  - «La copia está dañada o no es una copia de Clavero. Elige otra copia.»
  - «La copia es de una versión más nueva de Clavero. Actualiza Clavero para abrirla.» Ver m-2: «para abrirla».
  - «La copia está dañada: no se puede importar. Elige otra copia.»
  - «El archivo pasa de 40 MB: no es una copia de la bóveda. Elige una copia .sgbak de Clavero.»
- La línea del anexo de la 1.3.0 (`:116`), con la nota de la 2.0.
- «Contraseña incorrecta» ante falta de memoria o un coste que no cabe (I-2, fila 41). Pido decisión del Abogado y del dueño sobre la prioridad.
- «Contraseña incorrecta o datos dañados» tras un desbloqueo con huella (I-1.1).
- «Los adjuntos de la bóveda pasan de 50 MB en total» ante un solo adjunto fuera de tope (m-3).
- `UPGRADE_EXPORT_FAILED` frente a R-10 («sigue sin copia», m-7).
- Si se aplican los arreglos de I-1.3 y m-4, sus textos nuevos.
