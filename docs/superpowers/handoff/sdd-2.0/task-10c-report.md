# Tarea 10, fase C: errores que dicen qué hacer

## Estado

**Hecha.** Base `ce147ba`. Las dos suites pasan por separado, con `./gradlew --stop` entre ellas (las cifras, abajo). Los commits no se han subido.

Solo cambian cadenas. No se tocan formatos, permisos, dependencias, red ni registros. La única excepción: `IMPORT_TOO_BIG` pasa de `private` a `internal`, para que el test lo vea; el valor es el mismo.

## Commits

| Commit | Qué |
|---|---|
| `5e110f6` | El inventario, en su propio commit antes de cambiar ningún texto: `task-10c-inventory.md`, copiado a `docs/superpowers/handoff/sdd-2.0/` |
| `b8de226` | Los seis textos, con `ErrorTextsTest` y los tests que los citaban al día |
| `97385c9` | Filas 37 a 40 de `pendientes.md` |
| `a8294f4` | El anexo de textos de la 1.3.0 (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:116`) con el texto nuevo de `BIOMETRIC_UNLOCK_FAILED`: `ToolTextsTest` exige que cada texto de la app esté en el anexo, palabra por palabra, y la suite completa lo encontró |
| `b1c9d35` | «2.0, tarea 10 (fase C): errores que dicen qué hacer» (cierre; este informe no entra en git) |

## Tabla final de los textos cambiados

Todos siguen la misma forma: la frase de antes (lo que pasó) y, a continuación, el qué hacer. Ver la duda 1.

| Antes | Después | Cuándo sale | `archivo:línea` | Test |
|---|---|---|---|---|
| «No se pudo abrir la bóveda con la huella» | «No se pudo abrir la bóveda con la huella. Escribe la contraseña maestra.» | Huella: una excepción sin texto propio (LIBRE-4). El campo de la maestra está en la misma pantalla | `vault/VaultStore.kt:71` | `ErrorTextsTest`; `VaultStoreTest` «LIBRE-4…» |
| «El adjunto pasa de 5 MB» | «El adjunto pasa de 5 MB. Elige un archivo más pequeño.» | Añadir un adjunto (solo desde `addAttachment`) | `vault/Attachments.kt:29` | `ErrorTextsTest`; `AttachmentsTest.over5MbRejected` |
| «La copia está dañada o no es una copia de Clavero» | «… Elige otra copia.» | Leer una copia al importarla, al comprobarla o al importarla como bóveda nueva. En los tres sitios hay un botón para elegir otra | `vault/Backup.kt:79` | `ErrorTextsTest`; `BackupCheckTest`, `VaultV3Test` (por la constante) |
| «La copia es de una versión más nueva de Clavero» | La misma frase y, a continuación, «Actualiza Clavero. (ronda 1, m-2: sin «para abrirla»)» Conserva sus palabras: diseño §Formato v4, el texto de la 1.4.3 | La marca de la copia es más nueva que la que esta versión lee | `vault/Backup.kt:82` | `ErrorTextsTest`; `BackupV3Test` |
| «La copia está dañada: no se puede importar» | «… Elige otra copia.» | Comprobar o importar una copia con ids repetidos o campos dañados | `vault/BackupCheck.kt:180` | `ErrorTextsTest`; `VaultStoreTest` (LIBRE-1 y «a copy that breaks…») |
| «El archivo pasa de 40 MB: no es una copia de la bóveda» | «… Elige una copia .sgbak de Clavero.» | «Importar copia» con un archivo que no es un ZIP y pasa de 40 MB | `vault/VaultStore.kt:1665` | `ErrorTextsTest` |

**El rojo, visto:** cinco tests fallaron con los textos de antes:
- `ErrorTextsTest.eachSaysWhatHappenedThenWhatToDo`;
- `AttachmentsTest.over5MbRejected`;
- tres de `VaultStoreTest`.

## Los que se dejan, con su motivo

La fila de cada texto está en el inventario. En resumen:

- **Congelados por A6** (palabra por palabra):
  - `AUTO_BACKUP_REPLACED`, `_SEVERAL` y `_REPLACE_WAITS`;
  - `autoBackupUnlistedText`, `a6PartialText` y `LEGACY_A6_REQUEST`;
  - los errores que llegan como `ReplaceStatus.Failed.message`: `AUTO_BACKUP_NO_PERMISSION`, `_NO_FOLDER`, `_WRITE_FAILED`, `_VAULT_PROBLEM`, `_FAILED`, `_OLD_NOT_DELETED` (con «Se volverá a intentar en la próxima copia.») y `_OLD_NOT_DELETED_NO_RETRY`.
- **Fijados por un Ruling:**
  - `FINGERPRINT_STALE` (CRY-A02);
  - `MASTER_TOO_WEAK` y `MASTER_TOO_SHORT` (CRY-A01);
  - `PASSWORD_NOT_TEXT` (F5);
  - `NOT_A_VAULT` y «Contraseña incorrecta» (1.3.1);
  - `CLOCK_CHANGED_TEXT` (A11);
  - los `UPGRADE_*` (O-3);
  - los de `ImportTexts.kt` (`ImportTextsTest`; R13a, R19, R21, R22, R33);
  - `VAULT_DELETE_*` y `TOO_MANY_VAULTS` (R-9, T2);
  - los de la papelera y el historial (O-2).
- **Ya dicen qué hacer:** los topes de `VaultLimits.kt` y de `Folders.kt`, `JSON_HAS_ATTACHMENTS`, `REPLACE_NEEDS_OPEN`, `AUTO_BACKUP_NOW_NEEDS_OPEN`, `_ALREADY_TARGET`, `_TOO_MANY` y `BACKUP_CHECK_NOT_SGBAK`.
- **Sin una acción veraz**, porque no se conoce la causa o no hay salida:
  - «No se pudo abrir la bóveda»;
  - «No se pudo guardar o importar la copia», «No se pudo leer el archivo» y `BACKUP_CHECK_FAILED`;
  - `ATTACHMENT_DAMAGED`;
  - los topes de lectura de una copia (`BACKUP_TOO_BIG` y los demás);
  - `SEVERAL_IDENTITIES`;
  - los errores de los adjuntos en la interfaz.
- **Necesitan código:** van a `pendientes.md` (abajo).
- **Fuera de alcance**, porque son de las herramientas: `crypto/pk/*` (Contactos), `crypto/Totp.kt`, `NOT_COMPATIBLE` y `tools/*`.

## Las cadenas que son datos, y qué hice

Ninguna se ha cambiado:

- **Los errores de la copia automática y los `IMPORT_COPY_*`:** se guardan en `settings.json` (`lastError`, `replaceTold`) y sirven para avisar una sola vez de cada fallo distinto. Cambiarlos cambiaría ese comportamiento.
- **`IMPORT_COPY_LOCKED` e `IMPORT_COPY_VAULT_CHANGED`:** se comparan (`AutoBackup.kt:752`).
- **`IMPORT_WRONG_PASSWORD`:** se compara (`ImportSession.kt:215`).
- **`NOT_A_VAULT`:** se compara (`VaultScreen.kt:453`).
- **`UPGRADE_PENDING`:** se compara (`storeSaveErrorText`).

Comprobé que los seis textos cambiados no se guardan, no se comparan por su texto y no sirven para no repetir un aviso. Sus usos son `throw CryptoFailure(X)` o `AttachmentLimit(X)`, y se enseñan con su mensaje. El snackbar de `VaultPickerScreen.kt:194` usa la constante.

## Filas nuevas de `pendientes.md`

- **37. `ATTACHMENTS_TOTAL_TOO_BIG`:** sale al añadir un adjunto y también ante una copia. Un qué hacer veraz necesita una constante para cada caso.
- **38. `KDF_OUT_OF_MEMORY`:** el qué hacer depende del sitio, y el código no lo sabe.
- **39. `BACKUP_DOES_NOT_FIT` y `_V3`:** «quita adjuntos» sería falso en el diálogo del paso a v4, que no deja guardar nada.
- **40. README, línea 329:** cita el texto antiguo de `BACKUP_DAMAGED`. Va a la tarea 12.

## Consecuencias en Maestro y documentos

Busqué los seis textos de antes:
- **`.maestro/`:** ningún flujo los usa.
- **`docs/privacidad.html` y la ficha de Play:** no los citan.
- **README, línea 329:** cita «La copia está dañada o no es una copia de Clavero», en una nota de pruebas de la 1.3.0. Es la fila 40, para la tarea 12; no lo he tocado.
- **Tests:** `VaultStoreTest` (4 líneas) y `AttachmentsTest` (1), al día en el mismo commit.
- **El anexo de textos de la 1.3.0, línea 116:** ahora lleva el texto nuevo de `BIOMETRIC_UNLOCK_FAILED`, con la nota de que la línea de qué hacer es de la 2.0. `ToolTextsTest` lo exige.

## Tests

- `:app`: **BUILD SUCCESSFUL**: 2362 tests, 0 fallos; assemble, lint, licencias, Fragment y assets en verde.
- jvm-check: **BUILD SUCCESSFUL**: 2318 tests, 0 fallos (después de `./gradlew --stop`).
- Antes de las suites completas, corrí en jvm-check `vault.*`, `imports.*`, `redteam.*`, `AppContainer*`, `SourceTextsTest`, `PublishedTextsTest` y `FinalLegalReviewTest`. Todas en verde.

## Revisión experto-seguridad-cripto

- **Veracidad.** Ninguna línea de qué hacer nombra una causa:
  - «Elige otra copia» no dice si la copia está dañada o es ajena; la primera parte ya dice que el código no lo sabe (`Backup.kt:79`).
  - «Actualiza Clavero» (ronda 1, m-2) solo sale cuando la marca es más nueva que la que esta versión lee (`Backup.kt:325`).
  - «Escribe la contraseña maestra» sale donde está su campo (`VaultScreen.kt`, `UnlockVault`). La maestra no depende de la huella.
- **Ningún reintento prometido:** lo fija `ErrorTextsTest`.
- **Sin datos de la bóveda en los mensajes.** Los textos son fijos, nunca una ruta ni lo que dice una excepción (LIBRE-4, `biometricUnlockError`).
- **Formatos intactos.** `BACKUP_MARKER`, `SG1:`, `.sgenc` y `seguridad-vault` no cambian. La marca de la copia se sigue leyendo igual: solo cambia el mensaje.

## Revisión Ingeniero Móvil Senior

- **Dónde salen.** Los textos se enseñan en snackbars o en la línea de error de su pantalla, siempre a la vista (M-3).
- **Desde dónde se llega a lo que piden.** Elegir otra copia: los botones «Importar copia», «Importar una copia exportada», «Elegir copia» e «Importar como bóveda nueva». El campo de la maestra: en la misma pantalla. Elegir otro adjunto: el selector de adjuntos. Todo se alcanza también con el modo sencillo encendido, que solo filtra Herramientas.
- **Letra al 200 %.** Son textos de una o dos frases que se ajustan solos; ninguno va en un `maxLines`.

## Textos para el Abogado Digital (revisión final)

Los seis de la tabla final. `BACKUP_NEWER` conserva las palabras que dice la 1.4.3 y añade «Actualiza Clavero. (ronda 1, m-2: sin «para abrirla»)».

## Dudas

1. **Forma de los dos textos.** Uso dos frases seguidas en la misma cadena («… de Clavero. Elige otra copia.»), como los errores ya aprobados. Corregido en la ronda 1 (m-6): un `\n` no bastaba. `ToolTextsTest` lee el anexo de la 1.3.0 línea a línea, así que un texto con salto de línea no podría estar nunca en él, y `ErrorTextsTest` también tendría que cambiar. Ahora es un Ruling: dos frases en la misma cadena, sin `\n`.
2. **«Contraseña incorrecta» nombra una causa que AES-GCM no distingue:** una clave envuelta dañada da el mismo error. Está congelado desde la 1.3.1, así que no lo he tocado. Lo apunto para el Abogado y para el dueño.
