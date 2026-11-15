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

## Resumen de la propuesta

- **6 cadenas cambian:** `BIOMETRIC_UNLOCK_FAILED`, `ATTACHMENT_TOO_BIG`, `BACKUP_DAMAGED`, `BACKUP_NEWER` (con una línea añadida), `COPY_DAMAGED` e `IMPORT_TOO_BIG`.
- **3 filas nuevas en `pendientes.md`:** 37 (`ATTACHMENTS_TOTAL_TOO_BIG`), 38 (`KDF_OUT_OF_MEMORY`) y 39 (`BACKUP_DOES_NOT_FIT`).
- **El resto se deja**, con su motivo en cada fila.
