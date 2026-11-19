# Tarea 10, fase C: inventario de los textos de error (antes de cambiar nada)

Base `ce147ba`. Una fila por texto de error de la bóveda, las copias y la importación.

**Columnas:** texto actual · cuándo sale · `archivo:línea` · qué distingue el código ahí · propuesta (antes → después) o «se deja» con su motivo.

**Rutas abreviadas:** `v/` es `app/src/main/java/app/seguridad/vault/`; `i/`, `…/imports/`; `c/`, `…/crypto/`; `ui/`, `…/ui/screens/`.

## 0. Textos congelados (nombrados antes de nada)

### 0.1. Lo que sale de `a6Text`, `a6StaleText` y `a6DialogView`

Queda palabra por palabra, con las cadenas de error que entran en ellos:

- **`AUTO_BACKUP_REPLACED`, `AUTO_BACKUP_REPLACED_SEVERAL` y `AUTO_BACKUP_REPLACE_WAITS`** (`v/AutoBackup.kt:152`, `:161`, `:197`), `autoBackupUnlistedText` (`:170`), `a6PartialText` y `LEGACY_A6_REQUEST` (`v/A6Text.kt`).
- **`ReplaceStatus.Failed.message`**, que llega a `a6Text(listOf(status.message), …)` (`v/A6Text.kt:152`). Es un `lastError` guardado, uno de:
  - `AUTO_BACKUP_NO_PERMISSION` (`v/AutoBackup.kt:128`);
  - `AUTO_BACKUP_NO_FOLDER` (`:129`);
  - `AUTO_BACKUP_WRITE_FAILED` (`:130`);
  - `AUTO_BACKUP_VAULT_PROBLEM` (`:131`);
  - `AUTO_BACKUP_FAILED` (`:133`);
  - `AUTO_BACKUP_OLD_NOT_DELETED` (`:140`, con «Se volverá a intentar en la próxima copia.»);
  - `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY` (`:147`).

Todos se dejan. Además **son datos** (ver la sección 0.3).

### 0.2. Fijados por un Ruling

Conservan sus palabras:

| Texto | `archivo:línea` | Ruling |
|---|---|---|
| `FINGERPRINT_STALE` | `v/VaultStore.kt:80` | CRY-A02 y O-1 del Abogado, 1.3.1 |
| `MASTER_TOO_WEAK` y `MASTER_TOO_SHORT` | `v/MasterPolicy.kt:37`, `:40` | CRY-A01 y decisión (h) |
| `PASSWORD_NOT_TEXT` | `c/Crypto.kt:99` | F5 |
| `NOT_A_VAULT` («No es una bóveda compatible») y «Contraseña incorrecta» | `v/Vault.kt:275`; `ui/VaultScreen.kt:453` | 1.3.1, «Un archivo que esta versión no abre» |
| `CLOCK_CHANGED_TEXT` | `v/VaultStore.kt:68` | A11 |
| `UPGRADE_NEEDS_MASTER` y los `UPGRADE_*` del diálogo | `v/Vault.kt:278`; `ui/VaultUpgrade.kt:38-55` | O-3; Ruling T1 «O-3 con carpeta» |
| `BACKUP_NEWER` («La copia es de una versión más nueva de Clavero») | `v/Backup.kt:80` | Diseño §Formato v4 y el test contra el lector de la 1.4.3 |
| Todos los de `imports/ImportTexts.kt` | `i/ImportTexts.kt` | `ImportTextsTest`; R13a, R33 (`IMPORT_NOT_PROTECTED`, `IMPORT_REMOVE_EXPLAIN`) y R2, R7, R8 |
| `VAULT_DELETE_*`, `VAULT_DELETED`, `TOO_MANY_VAULTS` | `v/VaultsTexts.kt`, `v/VaultRegistry.kt:176` | R-9 y O-2 del Abogado; Ruling T2 |
| `COPIES_KEEP_IT` y los textos de la papelera y del historial | `v/TrashTexts.kt` | O-2 |

### 0.3. Cadenas que son datos

Cambiarlas sería un cambio de comportamiento, así que no se tocan:

- **Todos los errores de la copia automática** (`AUTO_BACKUP_*` de 0.1, `IMPORT_COPY_*`). Se guardan en `settings.json` como `lastError` y `replaceTold`, y sirven para que cada fallo distinto se avise una vez (`Settings.kt`, `replaceAfter`). Una cadena nueva no coincidiría con la guardada.
- **`IMPORT_COPY_LOCKED` e `IMPORT_COPY_VAULT_CHANGED`**: se comparan por su texto (`v/AutoBackup.kt:752`).
- **`IMPORT_WRONG_PASSWORD`**: se compara por su texto (`i/ImportSession.kt:215`). Además está congelado por `ImportTextsTest`.
- **`NOT_A_VAULT`**: se compara por su texto (`ui/VaultScreen.kt:453`).
- **`UPGRADE_PENDING`**: se compara en `storeSaveErrorText` (`v/VaultStore.kt`), contra la misma constante. También está fijado por O-3.

## 1. La bóveda

| Texto actual | Cuándo sale | `archivo:línea` | Qué distingue el código | Propuesta |
|---|---|---|---|---|
| «Contraseña incorrecta» | Desbloquear con la maestra, ante `CryptoFailure` que no sea `NOT_A_VAULT` | `ui/VaultScreen.kt:453` | AES-GCM no separa una contraseña equivocada de una clave envuelta dañada | Se deja: congelado (0.2). Que no distinga los datos dañados va a «Dudas» |
| «No se pudo abrir la bóveda» | Desbloquear, ante cualquier otra excepción | `ui/VaultScreen.kt:455` | Nada: cualquier excepción inesperada | Se deja: no hay una acción veraz sin saber la causa |
| `VAULT_NO_MEMORY` (RT-21, parte c, 17-11-2026) | Desbloquear con la maestra o con la huella cuando leer la bóveda agota la memoria | `v/VaultStore.kt` (`unlockLocked`, `commit`, `unlockWithKeyLocked`); `v/Vault.kt` (`masterUnlockErrorText`) | Un `OutOfMemoryError` al leer: la bóveda queda guardada y bloqueada, y la huella no se apaga | Nuevo, texto del Ruling: dice qué pasa y qué hacer, y nunca «Contraseña incorrecta» |
| `COPY_NO_MEMORY` (RT-21, parte c, 17-11-2026) | «Importar copia», «Importar como bóveda nueva» y «Comprobar una copia» cuando leer la copia agota la memoria | `v/VaultStore.kt` (`replace`, antes de escribir nada); `v/BackupCheck.kt` (`checkBackup`); `v/BackupCheckTexts.kt` (`copyFailureText` lo pasa tal cual) | Un `OutOfMemoryError` antes de cambiar nada | Nuevo, texto del Ruling: «No se ha cambiado nada» es verdad en los tres sitios |
| `LIST_NO_MEMORY` (RT-25, parte a, 19-11-2026) | La lista de la bóveda y «Códigos», cuando calcular la lista (índice de búsqueda, marcas de «Salud», etiquetas, caducidad, búsqueda) agota la memoria | `ui/screens/VaultList.kt` (`EntryListViewModel`, `EntryList`); `v/Vault.kt` | Un `OutOfMemoryError` en `compute`: la bóveda sigue abierta y guardada, la lista no se enseña | Nuevo, texto del Ruling de la ronda 2: dice qué pasa y qué hacer, sin cerrar la app |
| «No se pudo crear la bóveda. Inténtalo de nuevo.» | Crear, ante una excepción que no es de contraseña ni de memoria | `ui/VaultScreen.kt:393` | Nada | Se deja: el reintento lo hace el usuario con el mismo botón |
| `BIOMETRIC_UNLOCK_FAILED` «No se pudo abrir la bóveda con la huella» | Huella, ante una excepción sin texto propio (LIBRE-4) | `v/VaultStore.kt:71` | Ninguna causa conocida, pero la maestra no depende de la huella | «No se pudo abrir la bóveda con la huella» → «No se pudo abrir la bóveda con la huella. Escribe la contraseña maestra.» El campo está en la misma pantalla |
| `FINGERPRINT_STALE`, `CLOCK_CHANGED_TEXT`, `UPGRADE_NEEDS_MASTER` | Huella | `v/VaultStore.kt:80`, `:68`; `v/Vault.kt:278` | — | Se dejan: congelados, y ya dicen qué hacer |
| `UPGRADE_PENDING` | Cualquier guardado mientras espera la elección de O-3 | `v/VaultStore.kt:1635` | — | Se deja: dato (0.3), O-3, y ya dice qué hacer |
| `VAULT_TOO_BIG`, `ENTRY_TOO_BIG`, `TOO_MANY_FIELDS`, `FIELD_NAME_TOO_LONG`, `FIELD_VALUE_TOO_LONG`, `HISTORY_TOO_BIG`, `TOO_MANY_FOLDERS`, `FOLDERS_TOO_DEEP`, `FOLDER_CYCLE`, `TOO_MANY_VIEWS`, `TOO_MANY_TAG_COLORS`, `ORGANIZE_DAMAGED`, `VAULT_NAME_TOO_LONG` | Guardar fuera de los topes | `v/VaultLimits.kt:52-65` | El tope concreto | Se dejan: cada uno ya tiene su qué hacer |
| `FOLDER_NAME_*`, `FOLDER_GONE`, `VIEW_NAME_*`, `TAG_NOT_VALID` | Organizar | `v/Folders.kt:30-37` | — | Se dejan: ya dicen qué hacer |
| `NEEDS_MASTER_FOR_CONTACTS`, `ONLY_ONE_IDENTITY` | Guardar una identidad o un contacto | `v/VaultStore.kt:1652`, `:1664` | — | Fuera de alcance: son de Contactos, con su propio flujo |
| `TRASH_SAVE_FAILED` «No se pudo guardar. ¿Queda espacio en el móvil?» | Cualquier guardado que falla | `v/TrashTexts.kt` | No distingue la causa; la pregunta no la afirma | Se deja: O-2 y Ruling T3 N-1 (`storeSaveErrorText`) |
| `HISTORY_ALL_LOCKED` | Borrar todo el historial tras un bloqueo | `v/TrashTexts.kt` | El bloqueo | Se deja: fijado en la fase A |
| `VAULT_FAILED` «No se pudo hacer. Inténtalo de nuevo.» | Una acción de «Bóvedas» con una excepción desconocida | `v/VaultsTexts.kt:78` | Nada | Se deja: el usuario repite la acción |
| `VAULT_NEW_NEEDS_OPEN`, `VAULT_DELETE_*` | «Bóvedas» | `v/VaultsTexts.kt` | — | Se dejan: congelados, y ya dicen qué hacer |
| `ATTACHMENT_TOO_BIG` «El adjunto pasa de 5 MB» | Añadir un adjunto (`Attachments.read` y `add`, solo desde `addAttachment`) | `v/Attachments.kt:29` | El tamaño | «El adjunto pasa de 5 MB» → «El adjunto pasa de 5 MB. Elige un archivo más pequeño.» |
| `ATTACHMENTS_TOTAL_TOO_BIG` «Los adjuntos de la bóveda pasan de 50 MB en total» | Añadir un adjunto, y también comprobar o importar una copia | `v/Attachments.kt:30`; `v/VaultStore.kt:1292`; `v/BackupCheck.kt:205` | Las dos situaciones, con el mismo texto | Se deja: «Quita algún adjunto» sería falso ante una copia. Separarlo pide código: fila 37 de `pendientes.md` |
| `ATTACHMENT_DAMAGED` | Abrir un adjunto | `v/Attachments.kt:31` | AES-GCM: dañado o cambiado | Se deja: no hay una acción veraz (el original puede no existir ya) |
| «No se pudo añadir el adjunto.» / «No se pudo guardar el adjunto.» / «No se pudo quitar (¿almacenamiento lleno?)» | Adjuntos | `ui/VaultAttachments.kt:75`, `:106`, `:173` | Nada | Se dejan: sin una causa conocida no hay un qué hacer veraz |
| `KDF_OUT_OF_MEMORY` | Argon2id sin memoria (crear, abrir, cambiar la maestra, cifrar) | `c/Crypto.kt:81` | La memoria, no el contexto | Se deja: el qué hacer depende del sitio. Para abrir algo cifrado con el coste «Alto» no hay salida en este móvil. Fila 38 de `pendientes.md` |

## 2. Las copias (exportar, importar, comprobar)

| Texto actual | Cuándo sale | `archivo:línea` | Qué distingue el código | Propuesta |
|---|---|---|---|---|
| `BACKUP_DAMAGED` «La copia está dañada o no es una copia de Clavero» | Leer una copia (importar, comprobar, importar como bóveda nueva; antes, la cabecera) | `v/Backup.kt:79`; `ui/VaultPickerScreen.kt:194` | No separa dañada de ajena (el ZIP o la marca) | «La copia está dañada o no es una copia de Clavero» → «La copia está dañada o no es una copia de Clavero. Elige otra copia.» En los tres sitios hay un botón para elegir |
| `BACKUP_NEWER` | La marca de la copia es más nueva que la última que se lee | `v/Backup.kt:80` | El número de la marca | Conserva sus palabras (0.2) y añade debajo «Actualiza Clavero para abrirla.» (Ruling: diseño §Formato v4) |
| `BACKUP_TOO_BIG`, `BACKUP_ENTRY_TOO_BIG`, `BACKUP_TOO_MANY` | Leer una copia fuera de los topes | `v/Backup.kt:81-84` | El tope | Se dejan: no hay nada que el usuario pueda hacer con esa copia |
| `BACKUP_DOES_NOT_FIT` y `BACKUP_DOES_NOT_FIT_V3` | Escribir una copia: exportar o la copia de antes del paso a v4 | `v/Backup.kt:85-86` | El tope | Se dejan: «quita adjuntos» sería falso en el diálogo del paso a v4, que no deja guardar nada (`UPGRADE_PENDING`). Fila 39 de `pendientes.md` |
| `COPY_DAMAGED` «La copia está dañada: no se puede importar» | Comprobar o importar una copia con ids repetidos o campos dañados | `v/BackupCheck.kt:180` | El defecto de los datos | «La copia está dañada: no se puede importar» → «La copia está dañada: no se puede importar. Elige otra copia.» |
| `SEVERAL_IDENTITIES` | Ídem, con más de una identidad | `v/BackupCheck.kt:179` | — | Se deja: es de Contactos, y otra copia no lo arregla necesariamente |
| `IMPORT_TOO_BIG` «El archivo pasa de 40 MB: no es una copia de la bóveda» | «Importar copia» de un archivo que no es un ZIP y pasa de 40 MB | `v/VaultStore.kt:1665` | El tamaño y que no es una copia `.sgbak` | → «El archivo pasa de 40 MB: no es una copia de la bóveda. Elige una copia .sgbak de Clavero.» |
| `JSON_HAS_ATTACHMENTS`, `REPLACE_NEEDS_OPEN` | Importar | `v/VaultStore.kt:1653`, `:1666` | — | Se dejan: ya dicen qué hacer |
| `IMPORT_SETTINGS_FAILED` | Importar sin poder guardar los ajustes | `v/VaultStore.kt:1656` | No sabe la causa; la pregunta no la afirma | Se deja |
| «No se pudo guardar la copia» / «No se pudo importar la copia» / «No se pudo cambiar la contraseña maestra» | Ajustes, ante una excepción desconocida | `ui/VaultBackup.kt:94`, `:185`, `:154` | Nada | Se dejan: sin una causa conocida no hay un qué hacer veraz |
| «No se pudo leer el archivo» y `BACKUP_CHECK_READ_FAILED` | El documento no se puede abrir | `ui/VaultBackup.kt:107`; `ui/VaultPickerScreen.kt:193`; `v/BackupCheckTexts.kt:29` | Nada: el proveedor | Se dejan |
| `BACKUP_CHECK_FAILED`, `BACKUP_CHECK_NOT_SGBAK`, `BACKUP_CHECK_TOO_LONG` | Comprobar una copia | `v/BackupCheckTexts.kt:23-32` | — | Se dejan: los dos últimos ya dicen qué hacer; el primero no tiene causa |
| Los `AUTO_BACKUP_*` de error | Copia automática | `v/AutoBackup.kt:128-147` | — | Se dejan: datos y A6 (0.1 y 0.3) |
| `AUTO_BACKUP_NOW_NEEDS_OPEN`, `AUTO_BACKUP_ALREADY_TARGET`, `AUTO_BACKUP_TOO_MANY` | Ajustes | `v/BackupTargetTexts.kt` | — | Se dejan: ya dicen qué hacer |
| `UPGRADE_EXPORT_FAILED`, `UPGRADE_SAVE_FAILED`, `UPGRADE_AUTO_COPY_FAILED` | El diálogo del paso a v4 | `ui/VaultUpgrade.kt:46-50` | — | Se dejan: O-3 |

## 3. La importación de otras apps

Todos los de `i/ImportTexts.kt` se dejan: `IMPORT_FILE_TOO_BIG`, `IMPORT_NOT_TEXT`, `IMPORT_UNKNOWN`, `IMPORT_TOO_MANY_ITEMS`, `IMPORT_CSV_*`, `IMPORT_BROKEN`, `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED`, `IMPORT_WRONG_PASSWORD`, `IMPORT_KDF_TOO_BIG`, `IMPORT_ENCRYPTED_TOO_BIG`, `IMPORT_ZIP`, `IMPORT_HTML`, `IMPORT_GOOGLE_AUTHENTICATOR`, `IMPORT_JSON_*`, `IMPORT_COPY_*`, `IMPORT_SAVE_FAILED`, `IMPORT_READ_FAILED`, `IMPORT_PASSWORD_TOO_LONG`, `IMPORT_REPLACE_TOO_BIG`, `IMPORT_VAULT_CHANGED`, `IMPORT_REMOVE_*`.

Los motivos:
- están congelados por `ImportTextsTest` y por los Rulings de la 1.4.0 (R13a, R19, R21, R22 y R33);
- algunos se guardan o se comparan (0.3);
- los que no tienen qué hacer («Clavero no lo lee») dicen lo que pasó y no hay acción veraz.

## 4. Fuera de alcance

Son errores de las herramientas, y quedan apuntados aquí sin tocar:
- `crypto/pk/*`: tarjetas, mensajes, firmas e identidad de Contactos;
- `crypto/Totp.kt`;
- `NOT_COMPATIBLE` (`c/Crypto.kt:512`), de «Cifrar texto»;
- los de `tools/*`: generador, hashes, clásico, SLIP-0039, foto, tachar, QR, PIN y códigos.
- «Salud» (`ui/screens/HealthScreen.kt:229`, «No se pudo completar la comprobación (… de …). Revisa la conexión.»): es la comprobación de filtraciones en red, no un error de la bóveda, las copias ni la importación (r-3 de la re-revisión 2).

## Resumen de la propuesta

- **6 cadenas cambian:** `BIOMETRIC_UNLOCK_FAILED`, `ATTACHMENT_TOO_BIG`, `BACKUP_DAMAGED`, `BACKUP_NEWER` (con una línea añadida), `COPY_DAMAGED` e `IMPORT_TOO_BIG`.
- **3 filas nuevas en `pendientes.md`:** 37 (`ATTACHMENTS_TOTAL_TOO_BIG`), 38 (`KDF_OUT_OF_MEMORY`) y 39 (`BACKUP_DOES_NOT_FIT`).
- **El resto se deja**, con su motivo en cada fila.

---

# Ronda 1 del inventario (`task-10c-review.md` y «Ruling (advisor, T10 fase C)»)

## La búsqueda, para repetirla

Se lanza desde `app/src/main/java/app/seguridad`:

```bash
P='"[^"]*(No se |no se ha|no se puede|No es |no es |dañad|no válid|incorrect|bloqueó|pasa de|pasan de|no cabe|demasiad|Ya hay|ya no |no responde|no está|No hay|Falta|falta )[^"]*"'
grep -rnoE "$P" vault imports crypto/Crypto.kt android/BiometricVault.kt AppContainer.kt \
  ui/screens/{VaultScreen,VaultBackup,VaultAttachments,VaultEntryScreen,VaultPickerScreen,BackupCheckScreen,ImportScreen,SettingsScreen,TrashScreen,EntryHistoryScreen,VaultOrganize,VaultUpgrade}.kt
```

Además, a mano:
- cada `throw CryptoFailure(` y `throw AttachmentLimit(`;
- cada `catch (e: CryptoFailure)` de `ui/screens/`, `AppContainer.kt`, `vault/BackupCheckSession.kt` y `vault/VaultStore.kt`, para ver qué texto enseña.

Sobre el árbol de la ronda 1 da **180 coincidencias** en 30 archivos. Cada una está en una fila: las de la versión 1 del inventario o las de abajo. (Superado en la ronda 2, n-3: el método bueno enumera por los sitios que enseñan errores; ver más abajo.) Las de `crypto/pk`, `vault/Contacts*.kt` y `vault/PkActions.kt` son de Contactos y quedan fuera de alcance, igual que en la sección 4.

## Filas corregidas

- **I-2, «Contraseña incorrecta»** (`ui/VaultScreen.kt`). El almacén distingue (ronda 2, n-2: `KDF_OUT_OF_MEMORY` también puede salir al volver a envolver tras una maestra buena) tres casos que ahora la pantalla dice tal cual: `NOT_A_VAULT`, `KDF_OUT_OF_MEMORY` y `KDF_PARAMS_INVALID`. Solo los demás son AES-GCM, que no separa una contraseña equivocada de una clave envuelta dañada. Se arregló con código, en su propio commit (`masterUnlockErrorText`, `vault/Vault.kt`). «Contraseña incorrecta» conserva sus palabras (1.3.1).
- **I-2, `KDF_OUT_OF_MEMORY`.** Antes de la ronda 1 no llegaba a la pantalla de bloqueo: lo tapaba «Contraseña incorrecta». Ahora sale tal cual. No nombra ninguna acción.
- **m-1, `IMPORT_COPY_*`.** No son datos: son `CopyResult.Failed`, solo en memoria (`v/AutoBackup.kt:758-788`). Fila por fila:
  - `IMPORT_COPY_NO_PERMISSION` y `IMPORT_COPY_NO_FOLDER` ya dicen qué hacer (elegir la carpeta otra vez);
  - `IMPORT_COPY_WRITE_FAILED` («¿Queda espacio?», una pregunta, no una causa afirmada) e `IMPORT_COPY_FAILED` (sin causa conocida) terminan en «No se ha importado nada.», y vuelven a la vista previa con «Importar» a la vista (`ImportSession.kt:344-347`). Se dejan: no hay otro qué hacer veraz;
  - `IMPORT_COPY_LOCKED` e `IMPORT_COPY_VAULT_CHANGED` se dejan. Se comparan con la misma constante, en memoria, así que cambiarlos no cambiaría el comportamiento: se dejan por el Ruling al pie de la letra, y además ya dicen lo que pasó y que no se importó nada.
- **m-1, las cinco comparadas** (`IMPORT_COPY_LOCKED`, `IMPORT_COPY_VAULT_CHANGED`, `IMPORT_WRONG_PASSWORD`, `NOT_A_VAULT`, `UPGRADE_PENDING`). Se comparan con la misma constante, en memoria. Se dejan por el Ruling al pie de la letra, no porque cambiarlas cambie el comportamiento. No se reabren (Ruling T10 fase C).
- **m-1, `ImportTexts.kt`.** `ImportTextsTest` solo fija palabra por palabra seis textos:
  - `IMPORT_TRADEMARKS`;
  - `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED`;
  - `IMPORT_REPLACE_NOTE`;
  - `IMPORT_TOO_MANY_ITEMS`;
  - `IMPORT_NOT_PROTECTED`;
  - `IMPORT_REMOVE_EXPLAIN`.

  Los demás errores se dejan por otro motivo: cada `ErrorText` de la pantalla de importar tiene al lado el botón «Elegir otro archivo» (`IMPORT_PICK_OTHER`, `ui/ImportScreen.kt:523-559`, `:612-616`), así que el qué hacer ya está a la vista.
- **m-2, `BACKUP_NEWER`.** La constante de la 2.0 no está fijada. El texto de la 1.4.3 está en su propio APK. Su primera frase se conserva para que diga lo mismo que la 1.4.3, y lo que sigue es «Actualiza Clavero.», sin «para abrirla», que sonaba a promesa.
- **m-3, la fila 37.** `ATTACHMENTS_TOTAL_TOO_BIG` lo lanzan dos sitios por separado:
  - añadir un adjunto (`v/VaultStore.kt:1292`, `AttachmentLimit`);
  - comprobar o importar una copia (`v/BackupCheck.kt:204-205`, `CryptoFailure`), donde además es falso cuando un solo adjunto tiene un tamaño fuera de 0..5 MB, también negativo.

  Va a `pendientes.md`, fila 37, marcada de veracidad.
- **m-4, `TAG_NOT_VALID`** («Esa etiqueta no es válida.», `v/Folders.kt:37`). Se deja, pero el motivo de antes («ya dice qué hacer») era falso. Lo sostienen estos dos:
  - en «Etiquetar» (`ui/VaultOrganize.kt:184`) solo sale con el nombre vacío, porque las comas pasan a espacios (`v/Selection.kt:108`), y el usuario tiene delante el campo vacío;
  - en los colores de las etiquetas (`ui/VaultOrganize.kt:438`, `withTagColor`, `v/Folders.kt:265`) la etiqueta **se elige** de la lista de las que ya hay, no se escribe; solo falla con una etiqueta guardada rara (de una copia).

  «Escribe un solo nombre, sin comas» no sería verdad en ninguno de los dos sitios.
- **m-7, R-10 del Abogado inicial** (`abogado-2.0-inicial.md:309-311`). Ningún error propone desinstalar, «Seguir sin copia» ni borrar sin decir qué se pierde, y ninguno culpa al usuario. Los textos de esta fase cumplen. `UPGRADE_EXPORT_FAILED` («No se pudo guardar la copia. Vuelve a intentarlo o sigue sin copia.», `ui/VaultUpgrade.kt:46`) propone «Seguir sin copia». Lo que cuesta está a la vista en el mismo diálogo (`UPGRADE_NOT_BACK`), así que se apunta para el Abogado.

## Filas nuevas (I-1)

| Texto actual | Cuándo sale | `archivo:línea` | Qué distingue el código | Propuesta |
|---|---|---|---|---|
| Texto por defecto de `CryptoFailure`, «Contraseña incorrecta o datos dañados» | Contraseña equivocada o datos que no se descifran: al importar una copia, al importarla como bóveda nueva, al comprobarla, y en la pantalla de bloqueo tras la huella (`biometricUnlockError`) | `c/Crypto.kt:204`; `ui/VaultBackup.kt:183`; `AppContainer.kt:763`; `v/BackupCheckSession.kt:130`; `v/VaultStore.kt:90` | AES-GCM no lo separa | Se deja: la constante es la misma que la de «Cifrar texto». Para la copia, fila 42 de `pendientes.md`; tras la huella, fila 41 (la 1.3.1 decidió que «da el error de siempre», CRY-A02) |
| `KDF_PARAMS_INVALID` «Parámetros de cifrado no válidos» | `checkSane`: un encabezado fuera de los límites (más de 128 MiB, de 1 a 10 pasadas, de 1 a 4 carriles), o «Alto» en un móvil con poco *heap* | `c/Crypto.kt:84` (constante desde la ronda 1, con el mismo valor) | Esas dos condiciones, antes de derivar | Se deja: es verdad en las dos, y tampoco empuja a «He olvidado». En las copias, fila 42 |
| «No se pudo usar la huella», «No se pudo desbloquear con la huella» y «La huella no está activada» | Desbloquear con la huella (`BiometricVault.unlock`, solo desde `VaultScreen.kt:429`) | `android/BiometricVault.kt:560`, `:574`, `:600`, `:602-603`, `:632`, `:643`, `:648` | La causa técnica (Keystore, diálogo de la huella) | **Cambian**, solo como cadenas: añaden «. Escribe la contraseña maestra.» (`FINGERPRINT_UNLOCK_*`, `v/VaultStore.kt`). El `:472` (activar) no cambia |
| «La bóveda se bloqueó: no se ha añadido.», «… no se ha quitado.», «La bóveda se bloqueó o el adjunto ya no está: no se ha guardado.» | Adjuntos | `ui/VaultAttachments.kt:71`, `:171`, `:92` | El bloqueo, o que el adjunto ya no está | Se dejan: lo que pasó deja claro qué hacer (abrirla), y la revisión los acepta |
| «El adjunto es demasiado grande» | Respaldo de `AttachmentLimit` sin mensaje | `ui/VaultAttachments.kt:73` | — | Se deja: nunca sale, porque `AttachmentLimit` siempre lleva mensaje |
| «El adjunto está dañado» | Respaldo de `CryptoFailure` sin mensaje al abrir un adjunto | `ui/VaultAttachments.kt:103` | — | Se deja: nunca sale; el que sale es `ATTACHMENT_DAMAGED` |
| «La bóveda se bloqueó: ábrela y vuelve a exportarla» | Exportar | `ui/VaultBackup.kt:93` | — | Se deja: ya dice qué hacer |
| «La bóveda se bloqueó: no se ha cambiado nada» | Cambiar la maestra | `ui/VaultBackup.kt:151` | — | Se deja: bloqueo, aceptado |
| «No se pudo mover a la papelera (¿almacenamiento lleno?)» | La ficha, al mandar la entrada a la papelera | `ui/VaultEntryScreen.kt:242` | No sabe la causa; la pregunta no la afirma | Se deja: como `TRASH_SAVE_FAILED` |
| «El QR de Google Authenticator no es válido o está dañado» | Importar los códigos de esa app desde el formulario | `ui/VaultEntryScreen.kt:405` | — | Se deja: está en la lista exacta de `ImportTextsTest` (R7) |
| «El QR o enlace no es un código 2FA (otpauth://totp/…)» | Ídem, con un QR o un enlace de otro tipo | `ui/VaultEntryScreen.kt:422` | — | Se deja: ya dice lo que hace falta |
| «La bóveda se bloqueó: no se ha importado nada» / «No se pudo importar» | Ídem, al guardar | `ui/VaultEntryScreen.kt:470`, `:482` | El bloqueo / nada | Se dejan: el bloqueo está aceptado; el segundo no tiene causa |
| «La clave secreta no es válida (Base32: …, al menos 16)» | El formulario de un 2FA | `ui/VaultEntryScreen.kt:509` | — | Se deja: ya dice qué hace falta |
| «No se pudo abrir el escáner: pega el enlace otpauth:// o escribe la clave» | Ídem | `ui/VaultEntryScreen.kt:701` | — | Se deja: ya dice qué hacer |
| «No hay códigos nuevos que importar.» / «No se importan (N):» | Ídem | `ui/VaultEntryScreen.kt:768`, `:774` | — | Se dejan: son informativos, no fallos |
| `SETTING_SAVE_FAILED` «No se pudo guardar el ajuste» | Ajustes y «Aspecto» | `ui/SettingsScreen.kt:167` | Nada | Se deja: sin causa conocida no hay un qué hacer veraz |
| «No se pudo obtener permiso para usar esa carpeta. Elige otra.» | Elegir la carpeta de las copias | `ui/SettingsScreen.kt:331` | — | Se deja: ya dice qué hacer |
| `SAVE_LOCKED`, `HISTORY_ALL_LOCKED` | El editor; borrar todo el historial | `v/TrashTexts.kt:146`, `:120` | El bloqueo | Se dejan: bloqueo, aceptado |
| `ENTRY_IN_TRASH` | El editor de una entrada que está en la papelera | `v/TrashTexts.kt:143` | — | Se deja: ya dice qué hacer |
| `HISTORY_RESTORE_REFUSED` «Esta versión no se puede restaurar.» | Restaurar una versión que ya no está, igual a la entrada o de un tipo que no cabe | `v/TrashTexts.kt:140` | Tres casos, un solo texto | Se deja: ninguna acción es verdad en los tres |
| `TRASH_RESTORE_NONE` | Restaurar una identidad con otra en la bóveda | `v/TrashTexts.kt:30` | — | Se deja: es de Contactos, y ya dice por qué |
| `EXPIRY_BAD_DATE`; la fecha de un campo | El formulario | `v/EntryForm.kt:43`, `:37` | — | Se dejan: ya dicen qué hacer |
| `BACKUP_ENTRY_TOO_BIG_V3` | Leer una copia con la marca 3 | `v/Backup.kt:85` | El tope | Se deja, como los demás topes de lectura |
| «Id de adjunto no válido» | `require` de `writeBackup`: error de programa, nunca de datos | `v/Backup.kt:147` | — | Se deja: no lo ve el usuario (y si lo viera, sería como texto de `IllegalArgumentException`) |
| «El adjunto «X» falta o está dañado: quítalo de su entrada para poder hacer la copia» | Exportar | `v/VaultStore.kt:1694` | — | Se deja: ya dice qué hacer |
| «La copia está incompleta o dañada: falta o no coincide el adjunto «X»» | Importar una copia | `v/BackupCheck.kt:183` | Dos casos | Se deja: no lo distingue, y otra copia no tiene por qué arreglarlo |
| Los problemas de «Comprobar una copia» («Falta N adjunto…», «N adjuntos no coinciden…») | El informe de la comprobación | `v/BackupCheckTexts.kt:55-62` | — | Se dejan: son la lista del informe, debajo de `BACKUP_CHECK_HAS_PROBLEMS` |
| «Ya la tenían (o no caben más de 10 etiquetas).» | Etiquetar en la selección | `v/ListTexts.kt:43` | Dos casos | Se deja: es un resultado, no un error |
| `IMPORT_KDF_BUSY` | Importar con contraseña mientras corre otra | `i/ImportTexts.kt:258` | — | Se deja: ya dice qué hacer |
| `importTooBigText` | Importar algo que no cabe en una copia | `i/ImportTexts.kt:363` | Lo que sobra | Se deja: dice cuánto sobra y vuelve a la vista previa, donde «Importar» queda apagado hasta cambiar una elección (`ImportSession.kt:348-353`, M-2 de la 1.4.0) |
| Las razones de `SkipReason` («Un campo pasa del tamaño máximo», «Fila o elemento dañado»…) | Las líneas de «No entran» | `i/ImportModel.kt:40-54` | — | Se dejan: dicen por qué no entra cada elemento; no son fallos de la acción |
| `UPGRADE_SAVE_FAILED` | El diálogo del paso a v4 | `ui/VaultUpgrade.kt:47` | — | Se deja: O-3, y ya dice qué hacer |
| «El texto cifrado está incompleto o dañado», `NOT_COMPATIBLE` | «Cifrar texto» | `c/Crypto.kt:609`, `:518` | — | Fuera de alcance (herramienta) |
| Contactos: `masterFailureMessage` y los textos de `ContactsView.kt`, `Contacts.kt` y `PkActions.kt` | Contactos | `v/ContactsView.kt` | — | Fuera de alcance. `masterFailureMessage` tapa también `KDF_OUT_OF_MEMORY`, como I-2: fila 43 de `pendientes.md` |

---

# Ronda 2 del inventario (`task-10c-rereview-1.md` y «Ruling (advisor, T10 fase C, ronda 2)»)

## El método: por dónde se enseñan los errores, no por sus palabras

Se lanza desde `app/src/main/java/app/seguridad`. Lo que da, lo guardé en `sites.txt`: **281 sitios**.

```bash
UI=ui/screens/{VaultScreen,VaultBackup,VaultAttachments,VaultEntryScreen,VaultPickerScreen,BackupCheckScreen,ImportScreen,SettingsScreen,TrashScreen,EntryHistoryScreen,VaultOrganize,VaultUpgrade,HealthScreen}.kt
grep -rnE "ErrorText\(" $UI                                                          # 1. cada ErrorText(
grep -rnE "\b(error|message|failed|session\.error|form\.error\.value)\s*=\s*[^=]" $UI  # 2. cada estado de error que se pone
grep -rnE "showSnackbar\(|onError\(|onMessage\(" $UI android/BiometricVault.kt        # 3. cada resultado de fallo que se enseña
grep -rnE "throw (CryptoFailure|AttachmentLimit|VaultLimitExceeded|OrganizeRefused|ImportFailure|VaultsRefused|MasterRequired|IllegalStateException)\(|CopyResult\.Failed\(|ImportFailure\(|Failed\(StrongBoxFailure" \
  vault imports core crypto/Crypto.kt android/BiometricVault.kt AppContainer.kt       # 4. cada throw y cada resultado de fallo del alcance
```

Cada sitio se sigue hasta su cadena: un literal, una constante o el `e.message` de lo que se lanzó.

Para cruzar, un script lista los literales y las constantes de esos 281 sitios que no aparecen en este inventario. Se sigue usando el patrón de frases de la ronda 1, ampliado con lo que añadió el revisor («más de», «como mucho», «no coincide», «Has cambiado», «no tiene», «no permite») y con `core/`.

Lo que salió del cruce: constantes de límites y de formato (números, no textos), textos de Contactos y de «Cifrar texto» (fuera de alcance) y las filas de abajo.

## Filas corregidas en la ronda 2

- **`KDF_PARAMS_INVALID`** (n-1): ahora `checkSane` separa sus dos condiciones (`crypto/Crypto.kt:73-76`):
  - fuera de los límites, que se mira primero, sigue dando `KDF_PARAMS_INVALID`;
  - dentro de los límites pero sin *heap* suficiente da `KDF_OUT_OF_MEMORY`. Antes esto daba «Parámetros de cifrado no válidos».
- **`KDF_OUT_OF_MEMORY`** (n-2). La frase de la ronda 1, «sale antes de probar la clave», no es del todo verdad. También puede salir después de una maestra buena, al volver a envolver la clave con el coste de Ajustes (`afterMasterUnlock`, `vault/Vault.kt:512`). Entonces el archivo queda igual, la bóveda bloqueada, y el siguiente intento abre (`UnlockErrorTextTest.noMemoryWhenWrappingAgainLeavesTheFileAsItWas`). En la pantalla de bloqueo sale tal cual.
- **Contactos, `masterFailureMessage`** (n-5): ahora usa `masterUnlockErrorText`. La fila 43 de `pendientes.md` queda cerrada.

## Filas nuevas de la ronda 2 (cada una juzgada sola)

| Texto actual | Cuándo sale | `archivo:línea` | Qué distingue el código | Propuesta |
|---|---|---|---|---|
| «No se pudo activar la huella» | Activar la huella: crear la clave, `doFinal` o el diálogo, después del reintento sin StrongBox | `android/BiometricVault.kt:406`, `:431`, `:449`, `:464`, `:490` | La causa técnica, ya reintentada | Se deja: el reintento con la otra zona ya lo hizo el código, y no hay un qué hacer veraz. La bóveda sigue abriéndose con la maestra |
| «Este móvil no permite guardar la clave con huella» | Activar, cuando el Keystore no crea la clave | `android/BiometricVault.kt:457` | El Keystore | Se deja: no hay nada que hacer en ese móvil, y lo que pasó está dicho |
| «Has cambiado las huellas del móvil: entra con la contraseña maestra y vuelve a activarla» | Desbloquear con una clave invalidada | `android/BiometricVault.kt:629` | `KeyPermanentlyInvalidatedException` | Se deja: ya dice qué hacer |
| `FINGERPRINT_NOT_RESPONDING` | Desbloquear, cuando falla StrongBox | `android/BiometricVault.kt:615`; `tools/ToolTexts.kt:497` | Fallo de StrongBox | Se deja: ya dice qué hacer |
| El texto de error del **sistema** en el diálogo de la huella (n-7) | Desbloquear: bloqueo por intentos, tiempo agotado, sensor | `android/BiometricVault.kt:641` (`PromptAnswer.Error.message`, que viene de `BiometricPrompt`) | Lo que dice Android | Se deja: es del sistema, en el idioma del móvil, y la app no lo escribe. Añadirle una línea sería tocar el código que monta el mensaje, no una cadena. El campo de la maestra está debajo |
| `NO_FILE_PICKER` «Este móvil no tiene ninguna app para elegir o guardar archivos.» / `PICKER_FAILED` «No se pudo abrir el selector de archivos.» | Cualquier selector: copias, importar, adjuntos y las herramientas | `core/PickerLaunch.kt:4`, `:7` | Que no hay app / que falló al lanzarla | Se dejan: se comparten con las herramientas. El primero dice lo que pasa; el segundo no tiene causa |
| «No coinciden» | Repetir la maestra al crear la bóveda, al cambiarla y al crear otra bóveda | `ui/VaultScreen.kt:368`; `ui/VaultBackup.kt:254`; `ui/VaultPickerScreen.kt:426` | Que las dos no son iguales | Se deja: es un aviso en vivo junto al campo «Repítela», y el flujo `borra-datos/p19-crear-boveda-maestra-nueva.yaml` lo busca palabra por palabra (líneas 110 y 131), y ese flujo no se toca en esta fase |
| ««X»: es HOTP (por contador), que esta app no admite» / ««X»: tipo de código desconocido» / ««X»: la clave es demasiado corta…» | Las cuentas que no entran al importar los códigos de Google Authenticator | `crypto/OtpMigration.kt:190-192` | El motivo de cada cuenta | Se dejan: son la lista de lo que no entra, como `SkipReason`, no un fallo de la acción |
| Borrar una bóveda con la maestra equivocada: el texto por defecto de `CryptoFailure` | «Bóvedas» → borrar | `AppContainer.kt:688-690` (`unlock` o `confirmMaster`) → `:763` (`e.message ?: CryptoFailure().message`) | AES-GCM: contraseña o datos | Se deja: «Contraseña incorrecta o datos dañados» no nombra una causa que no distinga, y la misma rama enseña los fallos de «Importar como bóveda nueva». Separarlos pide código: fila 42 |
| «A vault already exists» | `create` sobre una bóveda que se puede leer | `vault/VaultStore.kt:538` | — | Se deja: no lo ve el usuario. La pantalla pone su propio texto («No se pudo crear la bóveda. Inténtalo de nuevo.», `newPasswordFailureMessage`) |
| «Sin permiso de cámara: pega el enlace otpauth:// o escribe la clave» | El formulario de un 2FA | `ui/VaultEntryScreen.kt:644` | — | Se deja: ya dice qué hacer |
| «El portapapeles está vacío» | Pegar en el formulario de un 2FA | `ui/VaultEntryScreen.kt:706` | — | Se deja: informativo; el qué hacer es evidente |
| `TOTP_UNSUPPORTED_LINK` | El formulario de un 2FA | `ui/VaultEntryScreen.kt:421` | CRY-B02 | Se deja: fijado por CRY-B02 (1.3.1), y dice por qué |
| `SETTINGS_NEED_OPEN_VAULT` | Ajustes con la bóveda bloqueada | `ui/SettingsScreen.kt:310` y siguientes | — | Se deja: ya dice qué hacer |
| «Aún no tienes bóveda: no hay nada que copiar.» | «Hacer una copia ahora» sin bóveda | `ui/SettingsScreen.kt:443` | — | Se deja: dice lo que pasa; crear la bóveda está en su pestaña |
| `AUTO_BACKUP_REPLACE_DROPPED`, `AUTO_BACKUP_REMOVED` | Quitar una carpeta | `ui/SettingsScreen.kt:460` | — | Se dejan: A6 y la fase anterior |
| La línea de error de la pantalla de bloqueo (n-6) | — | `ui/VaultScreen.kt:478` | — | No es una región viva: lo decía mal el informe de la ronda 1. Va a la fila 44 de `pendientes.md` (TalkBack en el móvil), sin tocar el código |
