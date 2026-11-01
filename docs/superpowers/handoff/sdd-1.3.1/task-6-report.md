# Tarea 6: fuera CriptoPanel. Informe del implementador

**Estado:** DONE. Commit `ba572c0`, con push (ver «Commits» al final).

Clavero deja de leer los textos `CP1:`, los archivos `.cpenc` y las bóvedas `criptopanel-vault`, y ningún texto de la app
nombra CriptoPanel. Nada propio de Clavero o de «Seguridad» cambia de bytes: `SG1:`, `.sgenc`, `seguridad-vault` 2 y 3 (y
sin `version`), `.sgbak` 1 y 2, la exportación de la 1.0.2 y los formatos de clave pública siguen pasando con sus datos
de siempre, y se añaden textos y archivos escritos con el código de la 1.3.0 que lo fijan byte a byte.

Las líneas «antes» son de `1a65545` (antes de la tarea); las «ahora», del commit de la tarea.

## 1. Lo que se ha quitado y por qué ya no tiene uso

| Antes | Qué | Por qué está muerto |
|---|---|---|
| `crypto/Crypto.kt:32` | `Dialect.CRIPTOPANEL` (`CP`, `CP1:`, `.cpenc`, `"criptopanel"`) | Es lo que pide la tarea. `Dialect` se queda con `SEGURIDAD` (el brief lo permite), así que `aead(…, dialect)`, `cascadeKey`, `rawKeyHkdf` y `Header.dialect` no cambian de firma ni de bytes. |
| `crypto/Crypto.kt:437`, `:449`, `:477-478` | `Header.KDF_RAW_KEY` (id 2: la clave aleatoria usada tal cual, sin HKDF) | Solo lo escribió CriptoPanel 1.9.0 (su propio KDoc decía «old CP1 texts and files from 1.9.0»). «Seguridad» nunca: `newHeader` es el único que construye cabeceras y escribe `KDF_RAW_KEY_HKDF` (3) desde el primer commit (`git show c70f932:…/Crypto.kt:337`, y `git log -G "kdfId = \|KDF_RAW_KEY\b"` no da ningún otro escritor). Sin el dialecto CP, el id 2 solo podía llegar en datos manipulados. Ahora `Header.parse` lo rechaza con «Derivación de clave desconocida» (`Crypto.kt:496`); queda un comentario para no reutilizar el 2 (`Crypto.kt:476-477`). |
| `crypto/Crypto.kt:518-526` | `extractToken` recorría los prefijos de todos los dialectos | Con un solo prefijo es `indexOf(TEXT_PREFIX)` (`Crypto.kt:517-520`); mismo resultado para `SG1:`. |
| `crypto/Crypto.kt:587-588`, `:593-599`, `:608-617` | `textDialect` y las comprobaciones «prefijo de un dialecto con cabecera del otro» | `Header.parse` solo devuelve cabeceras `SG` (`Crypto.kt:490`), así que `header.dialect != dialect` ya no podía ser verdad. Un `SG1:` con la cabecera de un `CP1:` sigue dando `NOT_COMPATIBLE`, ahora desde `Header.parse` (test `CriptoPanel data is refused`). |
| `crypto/FileCrypto.kt:15-16` | `LEGACY_ENCRYPTED_EXTENSION = ".cpenc"` | Solo lo usaba el botón de `SecurityToolsScreen.kt:870`. |
| `ui/screens/SecurityToolsScreen.kt:59`, `:586-587` | import de `LEGACY_ENCRYPTED_EXTENSION` y `LEGACY_TEXT_PREFIX = "CP1:"` | Solo los usaban tres textos de la pantalla (sección 9). |
| `vault/Vault.kt:117` | El valor por defecto de `format` (`LEGACY_FORMAT`) | Pedido por la tarea. Ahora `format` es obligatorio (`Vault.kt:118`): un JSON sin él da «No es una bóveda compatible» en `decodeFile` (`Vault.kt:381-385`), antes de derivar ninguna clave. |
| `vault/Vault.kt:142` | `LEGACY_FORMAT = "criptopanel-vault"` | Pedido por la tarea. |
| `vault/Vault.kt:171-175`, `:178-182` | La rama `LEGACY_FORMAT` de `supported` y de `candidates` (`listOf(2, 1)`) | Solo existían para ese formato. `supported` es ahora `FORMAT` y versión 0, 2 o 3, igual que antes para `FORMAT` (`Vault.kt:166-167`); `candidates` da `[3, 2]` sin versión, igual que antes para `FORMAT` (`Vault.kt:170-171`). |
| `vault/Vault.kt:206-207`, `:213-214` | La versión 1 de la AAD (`"${format}-key-v1"`, `"${format}-data-v1"`) | Solo se llegaba con `candidates` de `LEGACY_FORMAT` o con un `LEGACY_FORMAT` de versión 1: `supported` nunca aceptó un `seguridad-vault` de versión 1 en ninguna de las 15 versiones de `Vault.kt` (desde `07f220e:94-96`). Para `FORMAT` 2 y 3 la AAD es la misma de siempre, byte a byte (`Vault.kt:194`, `:200`). |
| Datos de prueba | `criptopanel/file-aes.cpenc`, `criptopanel/file-cascade-rawkey.cpenc`, `criptopanel/vault-v1.json` | Ningún test los usa ya. |

Se quedan, uno de cada tipo, para los tests de rechazo: `criptopanel/texts.json` (los 8 `CP1:`), `criptopanel/ref.cpenc`,
`criptopanel/vault-v2.json` y `criptopanel/vault-noversion.json`, sin cambiar un byte (la lista `files` de `texts.json`
nombra los dos `.cpenc` borrados: es solo un dato que nadie lee).

### Qué significa «v1» en cada formato (comprobado antes de borrar)

- **`seguridad-vault`** nunca tuvo versión 1: desde el primer `Vault.kt` (`07f220e`) «Seguridad» escribe la 2
  (`version = VaultFile.VERSION`, y `VERSION = 2` entonces; 3 desde `5276b0d`) y `supported(FORMAT)` acepta 0 y 2 (luego 0, 2
  y 3), nunca 1. Lo que el contexto llama «`seguridad-vault` v1 a v3» se cubre igual: la 2 (1.0.x y 1.1.x, con
  `export-1.0.2.json` y `vault-1.1.json`), la 3 y la que no dice su versión (`VaultTamperTest`, `a vault file without a
  version key still opens`).
- **`criptopanel-vault`** tuvo la 1 (CriptoPanel 1.9.2) y la 2; solo para ella existía la AAD de la versión 1.
- **`.sgbak` v1** (`seguridad-backup` de la 1.1) no se toca: `Backup.kt:20`, `:32`, `:169` y `backup-1.1.sgbak` siguen.
- **El id 1 de la cabecera `SG`** (`Header.VERSION`) es otra cosa y no cambia.

## 2. Lo que parece antiguo y se queda

- **`Dialect` con una sola entrada** (`Crypto.kt:31-38`): mantiene las firmas públicas (`aead(…, dialect)`) y los tests que
  pasan `header.dialect` (`PasswordBytesTest.kt:132-143`), y las etiquetas HKDF salen igual («seguridad-cascade-…»,
  «seguridad-raw-key»). `Dialect.ofMagic` sigue en `Header.parse` y en `PkActions.isSymmetricHeader` (`PkActions.kt:480-481`).
- **`KDF_RAW_KEY_HKDF` y `rawKeyHkdf`**: es el modo de clave aleatoria que escribe Clavero (`Crypto.kt:553`).
- **La cascada y `cascadeKey`**: es de Clavero (`Aead.CASCADE`), con el vector fijo de la tarea 4 (`CryptoTest`) y ahora
  también el texto de la 1.3.0 de `DialectTest`.
- **`VERSION_UNKNOWN`** para un `seguridad-vault` sin `version`: tolerancia propia de Clavero, con su test.
- **`biometricRefusal`: `file.format != FORMAT || file.version == 1 → MASTER_DUE`** (`Vault.kt:399`). Lo fija un test de
  Clavero (`VaultV3Test.kt:676`), y quitar el `version == 1` haría que un `vault.bin` manipulado con versión 1 diera
  `CryptoFailure` al leerlo con la huella, que desactiva la huella (tarea 3), en vez de pedir la maestra. Solo cambia su
  KDoc. **Ronda 1:** pasa a `!supported(file) → MASTER_DUE`, que cubre también la 4 y cualquier otra (Minor 4).
- **`afterMasterUnlock`: `known.format != FORMAT || …`** (`Vault.kt:355`): ya no lo alcanza ningún archivo que pase
  `unlock`, pero es una guarda sin coste de una función pública; solo cambia su KDoc.
- **`write`: `check(file.format == FORMAT && …)`** (`Vault.kt:317`) y **`needsMasterForContacts`** (`VaultStore.kt:629`):
  siguen protegiendo de un formato desconocido.

## 3. Ninguna versión de «Seguridad» dejó una `criptopanel-vault` en disco

- **Importar la vuelve a envolver antes de guardar:** `VaultStore.replace` llama a
  `VaultCrypto.afterMasterUnlock(incoming, …)` (`VaultStore.kt:804`) y guarda solo ese resultado, `checked`
  (`VaultStore.kt:837` `commit(checked, …)` y `:848` `save(checked)`). `afterMasterUnlock` envuelve de nuevo todo archivo que
  no sea `FORMAT` 3 (antes `Vault.kt:370`, ahora `:355`), y `wrap` escribe siempre `format = VaultFile.FORMAT` y
  `version = VaultFile.VERSION` (antes `Vault.kt:260-261`, ahora `:246-247`).
- **Los demás escritores también escriben `FORMAT`:** `create` y `changeMaster` pasan por `wrap` (`Vault.kt:226`, `:366`), y
  `write` exige `FORMAT` (`Vault.kt:317`); `VaultStore` solo guarda lo que sale de ellos (`VaultStore.kt:383-394`, `:415-422`,
  `:488-493`, `:561-570`, `:690-697`, `:804-848`).
- **En toda la historia:** las 33 versiones de `VaultStore.kt` (`git log --follow`) tienen 0 coincidencias de
  `(commit|save|saveBookkeeping|opened)(incoming` y todas pasan por `afterMasterUnlock(incoming`; la primera (`07f220e`)
  ya hacía `commit(checked, …)` tras `afterMasterUnlock`. Las 15 versiones de `Vault.kt` escriben `format = VaultFile.FORMAT`
  en `wrap`.
- **Consecuencia:** sin campo `format`, un `vault.bin` daría ahora `CryptoFailure` en `load` y se apartaría como
  `vault.bin.bad-<hora>` (`VaultStore.kt:247-249`). No puede existir ninguno escrito por «Seguridad» o Clavero.

## 4. Lo que recibe un dato de CriptoPanel (el mismo error que cualquier dato ajeno)

| Dato | Antes | Ahora | Igual que |
|---|---|---|---|
| Texto `CP1:` (los 8 de `texts.json`, con su secreto correcto) | Se descifraba | «El texto cifrado debe empezar por SG1:»; `inspectText` y `extractToken` dan `null` | Un texto que no es de Clavero |
| Su cabecera tras `SG1:` | `NOT_COMPATIBLE` | `NOT_COMPATIBLE` («No es un dato cifrado compatible») | Bytes ajenos tras `SG1:` |
| `ref.cpenc` | Se descifraba | «No es un dato cifrado compatible», **sin escribir nada** en la salida | Un archivo ajeno del mismo tamaño |
| `.cpenc` en «Abrir lo recibido» | `PickedFile.Symmetric` | `PickedFile.Other` | Cualquier otro archivo |
| `vault-v2.json`, `vault-noversion.json` al importar (texto y archivo, con su maestra) | Se importaban | «No es una bóveda compatible», sin tocar `vault.bin`, las entradas ni los archivos de la carpeta | Un JSON que no es una bóveda |

Ningún cuelgue, ninguna salida a medias (`decryptStream` falla en `Header.parse` antes de escribir, `FileCrypto.kt:98-99`;
la pantalla descifra en un temporal que borra al fallar), ningún texto en inglés. «Importar copia» sigue dejando pasar
cualquier JSON por la cabecera (`mayBeVaultImport`), pide la maestra y después dice «No es una bóveda compatible», como con
cualquier otro JSON.

## 5. Tests

### Nuevos (6)

- `DialectTest`:
  - `SG1 texts and sgenc files written by 1_3_0 still open and are still written byte for byte`: dos textos (contraseña con
    AES-256-GCM; clave aleatoria con la cascada) y dos `.sgenc` (contraseña con ChaCha20-Poly1305; clave con
    Twofish-256-GCM), escritos con el código de **b6ea21b (1.3.0)** en un *worktree* aparte con un `SecureRandom` que cuenta
    (el generador no se ha guardado en el repositorio). Se abren, y las mismas entradas dan los mismos bytes. Sustituye lo
    que daban los datos de CriptoPanel, que fijaban de paso el contenedor común (cabecera, Argon2id, STREAM y registro del
    nombre): sin él, un cambio simétrico en el escritor y el lector no lo vería ningún test.
  - `CriptoPanel data is refused` (texto y archivo, sección 4).
  - `kdf 2, a raw key used without HKDF, is refused`.
- `LegacyVaultTest`: `CriptoPanel data is refused` (bóvedas, sección 4).
- `AppNameTest`: `no visible text names CriptoPanel` (literales de `app/src/main/java` con `kotlinLiterals` y `res/values*`
  con `xmlTexts`, sin distinguir mayúsculas) y `no code reads CriptoPanel` (ningún literal es `"CP1:"`, `".cpenc"`,
  `"criptopanel"` ni `"criptopanel-vault"`).

### Borrados (14, todos de CriptoPanel)

- `DialectTest`: `readsEveryCriptopanelFixture`, `readsCriptopanelFiles`, `rejectsHybridHeaders` (su parte `SG1:` + cabecera
  CP pasa a `CriptoPanel data is refused`), `cascadeLabelsDiffer` (comparaba las etiquetas SG con las CP).
- `LegacyVaultTest`: `importsCriptopanelV1`, `importsCriptopanelNoVersion`, `importsCriptopanelV2`,
  `legacyFilesHaveNoFormatKeyAndReadAsLegacy`, `upgradeKeepsTheDataKeyAndBumpsTheRevision`, `legacyFormatWithNewLabelsFails`.
- `VaultTamperTest`: `version 1 vaults still open and become version 2 at the next master unlock` (una `criptopanel-vault`
  v1 hecha a mano).
- `SeedCryptoTest`: `texts from 1_9_0 with the key used directly still decrypt` (`CP1:` con el id 2).
- `CryptoTest`: `decrypts texts written by an independent implementation` (dos `CP1:`) y `decrypts a file written by an
  independent implementation` (`ref.cpenc`).

### Cambiados (solo se quita la parte de CriptoPanel)

- `LegacyVaultTest`:
  - `unknownFormatIsRefused` usa una bóveda de Clavero en vez de `vault-v2.json`;
  - `seguridadVaultWithoutFormatKeyFails` (m-2): el JSON sin `format` lo rechaza ahora `decodeFile` («No es una bóveda
    compatible») en vez de leerse como `criptopanel-vault` y fallar al abrir; la original sigue abriendo;
  - `noBiometricsForLegacyV2EvenWhenRecentlyChecked` pasa a `noBiometricsForAnotherFormatEvenWhenRecentlyChecked` y
    `writeRefusesALegacyFile` a `writeRefusesAnotherFormat`, con una bóveda de Clavero y otro `format`.
- `VaultTamperTest`: `vault files without a version key, as 1_9_2 and 1_9_3 wrote them, still open` pasa a `a vault file
  without a version key still opens`, sin la mitad de la 1.9.2; un comentario.
- `DialectTest`: `extractTokenFindsBoth` pasa a `extractTokenFindsSg1`.
- `BackupTest.jsonExportsStillImport`, `ImportHeadTest`, `MessageTest.extractTokenSigueIgualParaLosFormatosAntiguos`,
  `SeedCryptoTest` (`extractToken("CP1:")`), `CryptoTest` (`decryptText("CP1:%%%")`): fuera las líneas de CriptoPanel.
- `PkActionsTest.unSgencConContrasena…`: un `.cpenc` real ya no es `Symmetric` sino `Other`.
- `AppNameTest.theIdentityTheFormats…`: fuera `LEGACY_FORMAT` y `CRIPTOPANEL`; se añade `Dialect.entries == [SEGURIDAD]`.
- `CryptoTest`, KDoc de `Kat`: el detalle pendiente (para Twofish el motor tiene vector publicado en `SeedCryptoTest.kt:40`;
  para Serpent solo ida y vuelta) y que ahora son el valor conocido de la cascada.
- `PasswordBytesTest`, `ImportHeadTest`: KDoc.

Los datos de `app/src/test/resources/seguridad/` y `vectors/` no se han tocado; tampoco los vectores de la tarea 4.

### Números

| Verificación | Antes | Ahora | Diferencia |
|---|---|---|---|
| `./gradlew :app:testDebugUnitTest …` (app) | 1280 | **1272**, 0 fallos | −8 = −14 borrados + 6 nuevos |
| `gradle -p jvm-check cleanTest test` | 1275 | **1267**, 0 fallos | −8, los mismos (ninguno está en `ui/`) |

Por clase: `DialectTest` 7 → 6, `LegacyVaultTest` 16 → 11, `VaultTamperTest` 7 → 6, `SeedCryptoTest` 15 → 14,
`CryptoTest` 34 → 32, `AppNameTest` 6 → 8.

## 6. TDD

**RED** (paso 2, el comando del brief con `--max-workers=1`), con los tests nuevos y el código sin tocar:

```
./gradlew :app:testDebugUnitTest --tests '*AppNameTest*' --tests '*DialectTest*' --tests '*LegacyVaultTest*' --offline --no-daemon --max-workers=1
DialectTest > kdf 2, a raw key used without HKDF, is refused FAILED      (expected CryptoFailure: se descifraba)
DialectTest > CriptoPanel data is refused FAILED                         (expected CryptoFailure: el CP1: se descifraba)
AppNameTest > no code reads CriptoPanel FAILED
    SecurityToolsScreen.kt:587 «CP1:», Crypto.kt:32 «CP1:», «.cpenc», «criptopanel», FileCrypto.kt:16 «.cpenc», Vault.kt:142 «criptopanel-vault»
AppNameTest > no visible text names CriptoPanel FAILED
    VaultScreen.kt:293 «Sirve una copia … o una criptopanel-vault …», SettingsScreen.kt:475 «… y la criptopanel-vault, …», Crypto.kt:32, Vault.kt:142
LegacyVaultTest > CriptoPanel data is refused FAILED                     (expected CryptoFailure: la bóveda se importaba)
31 tests completed, 5 failed
BUILD FAILED in 35s
```

El test de los vectores de la 1.3.0 pasó ya en RED, como debe: fija lo que no puede cambiar.

**GREEN**, enfocado (todas las clases tocadas y `VaultV3Test`): `./gradlew :app:testDebugUnitTest --tests '*AppNameTest*'
--tests '*DialectTest*' --tests '*LegacyVaultTest*' --tests '*VaultTamperTest*' --tests '*BackupTest*' --tests
'*ImportHeadTest*' --tests '*PkActionsTest*' --tests '*MessageTest*' --tests '*SeedCryptoTest*' --tests '*CryptoTest*'
--tests '*PasswordBytesTest*' --tests '*VaultV3Test*' --offline --no-daemon --max-workers=1` → `BUILD SUCCESSFUL`, 361
tests, 0 fallos.

**Verificaciones globales** (`export ANDROID_HOME=/opt/android-sdk`):

```
gradle -p jvm-check cleanTest test --no-daemon --max-workers=1 --offline
BUILD SUCCESSFUL in 1m 51s                       → 1267 tests, 0 fallos, 0 omitidos
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
BUILD SUCCESSFUL in 3m 32s                       → 1272 tests, 0 fallos, 0 omitidos; lint, licencias, Fragment y assets en verde
```

Los vectores de la 1.3.0 se generaron en un *worktree* de `b6ea21b` (`git worktree add --detach … b6ea21b`, un test
desechable `GenVectorsTest` y `gradle -p jvm-check test --tests '*GenVectorsTest*' --offline`), que después se borró
(`git worktree remove`; `git worktree list` solo muestra el repositorio).

## 7. README

- `README.md:24`: «Se siguen leyendo las exportaciones JSON de la 1.0.x.» (fuera «y de CriptoPanel»).
- La sección «Compatibilidad con CriptoPanel» pasa a **«Formatos»** (`README.md:44-61`): empieza por los formatos propios
  («Clavero escribe y lee sus propios formatos…: textos `SG1:`, archivos `.sgenc`, la bóveda `seguridad-vault` y, desde la
  1.1.0, la copia `seguridad-backup` en archivos `.sgbak`.»), sigue la nota de la versión, y conserva lo de las copias, los
  formatos de la 1.2.0 y el aviso de la 1.2. La lista de CriptoPanel desaparece.
- **Nota de la versión** (`README.md:48`), literal del brief: «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`,
  `.cpenc` y su bóveda).» El README no tiene una sección de notas de versión; la he puesto en «Formatos», donde ya están
  las otras notas de versión sobre formatos («Formatos nuevos de la 1.2.0», «Aviso: … 1.2 … 1.1»). El controlador puede
  moverla.
- «JSON de la 1.0.x y de CriptoPanel» pasa a «JSON de la 1.0.x».
- La última frase: «Estos formatos se leerán siempre: hay tests con datos reales escritos por versiones anteriores (la
  exportación JSON de la 1.0.2, la bóveda y la copia `.sgbak` de la 1.1.1, y textos `SG1:` y archivos `.sgenc` escritos con
  el código de la 1.3.0).» (antes: «… generados por CriptoPanel 1.10.0»).
- Fuera la línea de la lista de pruebas «Importar una copia real de la bóveda de CriptoPanel…» (antes `README.md:217`).
- Línea de la prueba de la 1.3.0 «Importar copia» (antes `:307`): «Un JSON exportado de la 1.0 sigue pidiendo la
  contraseña.» (fuera «o de CriptoPanel»).

## 8. Autorrevisión

- `grep -rni "criptopanel\|CP1\|cpenc" app/src/main` solo da dos comentarios que explican por qué falta algo
  (`Crypto.kt:476`, `Vault.kt:115`); ningún literal (lo comprueba `AppNameTest`).
- Las firmas públicas de los formatos de Clavero no cambian: `encryptText`, `decryptText`, `inspectText`, `extractToken`,
  `encryptStream`, `decryptStream`, `aead`, `VaultCrypto.*`. Cambia el constructor de `VaultFile` (`format` sin valor por
  defecto), que solo usaba sin `format` un test borrado.
- Las tareas 1 a 5 siguen: `blockGcm`, `chaChaPoly` y `runAead` intactos (sus tests de `CryptoTest` pasan), `Totp.kt`,
  `MasterPolicy.kt`, `net/` y la huella sin tocar.
- Los avisos nuevos de Kotlin en mis tests (`classLoader`, `parentFile`) están corregidos.

## 9. Textos para el Abogado Digital (revisión final)

Textos de la interfaz cambiados (se acortan; siguen en línea, como estaban, no son textos nuevos):
1. `ui/screens/VaultScreen.kt:293`, «Crea tu bóveda»: «Sirve una copia .sgbak de esta app (con los adjuntos) o una
   exportación .json de la 1.0.» (antes nombraba la `criptopanel-vault`).
2. `ui/screens/SettingsScreen.kt:475`, Ajustes, junto a exportar e importar:
   `«Exportar bóveda» guarda una copia .sgbak con los adjuntos. «Importar copia» acepta esa copia y la exportación .json de la 1.0.`
3. `ui/screens/SecurityToolsScreen.kt:353`, Cifrar texto, al importar un `.txt` sin texto cifrado: «Ese archivo no contiene
   un texto cifrado (SG1:…).»
4. `ui/screens/SecurityToolsScreen.kt:408`, etiqueta del campo: «Texto cifrado (empieza por SG1:)».
5. `ui/screens/SecurityToolsScreen.kt:866`, botón de Cifrar archivos al descifrar: «Elegir archivo .sgenc».

README: los cambios de la sección 7, sobre todo la nota «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`,
`.cpenc` y su bóveda).» y la frase de los tests con datos reales.

## 10. Puntos para el controlador

1. **`.github/pull_request_template.md:9`** sigue diciendo que `CP1:`, `.cpenc` y `criptopanel-vault` «se siguen leyendo».
   No es mío (documentos); hay que cambiarlo con `CLAUDE.md` antes del PR, que usa esa plantilla.
2. **La skill `experto-seguridad-cripto`** no exige leer formatos de CriptoPanel: solo lo nombra como ejemplo de proceso
   («Sección de auditoría en el diseño del proyecto, como en CriptoPanel», `SKILL.md:107`). No la he tocado.
3. Documentos históricos que nombran los datos de CriptoPanel (`auditoria/04-cripto*.md`,
   `docs/AUDITORIA_SEGURIDAD_2026-10.md:118`): no se tocan, según el diseño.
4. **El id 2 de la cabecera** se quita aunque el brief solo nombra el dialecto y el formato: es lo que dejó sin uso quitar
   `CP1:`/`.cpenc` (sección 1).
5. El sitio de la nota de la versión en el README (sección 7).

## Revisión experto-seguridad-cripto

Lista de `references/cripto-moderna.md` y `references/auditoria.md` sobre el diff:
- **Solo se quita superficie.** No hay primitivas, parámetros ni formatos nuevos; los vectores conocidos de la tarea 4
  (`CryptoTest`, `fixed known answers for every algorithm…`) y los de RFC y NIST siguen pasando.
- **Menos caminos de clave.** Se va el id 2, que usaba la clave aleatoria directamente como clave AEAD de cada mensaje
  (sin separación de dominio ni clave por mensaje): `Header.parse` lo rechaza antes de tocar el secreto (`Crypto.kt:496`) y
  `keyForHeader` solo deriva con HKDF y la sal de la cabecera (`Crypto.kt:429-439`).
- **Rechazo antes del trabajo caro.** Una cabecera sin la marca `SG` falla en `Header.parse` (`Crypto.kt:490`) y una bóveda
  sin `format`, en `decodeFile` (`Vault.kt:381-385`): ninguna llega a Argon2id.
- **Sin degradación.** Desaparece la AAD de la versión 1 (que no autenticaba la fecha ni el resto de campos); la que queda
  liga formato, versión, coste, sal, revisión y fecha (`Vault.kt:160-163`, `:194`, `:200`), los mismos bytes que antes para
  `seguridad-vault`. Un `seguridad-vault` con versión 1 sigue rechazado al abrir (`Vault.kt:166-167`, `VaultTamperTest`) y,
  desde la ronda 1, cualquier archivo que la maestra no abriría (otro formato, versión 1, 4…) pide la maestra con la huella
  en vez de fallar al leer y desactivarla (`Vault.kt:401`, `VaultV3Test`
  `biometricRefusalAsksForTheMasterForAVersionOrFormatThatDoesNotOpen`).
- **Comprobación independiente del contenedor (ronda 1).** Un texto `SG1:` y un `.sgenc` hechos con `cryptography` de
  Python, byte a byte, se abren con la API pública de Clavero (`DialectTest`); y al revés, Python abre los vectores de la
  1.3.0 sin el código de Clavero. Cubre un error que el escritor y el lector compartirían (Minor 6).
- **Sin salida a medias ni oráculos nuevos.** El `.cpenc` falla en la cabecera, sin escribir (`FileCrypto.kt:98-99`; el test
  mira que la salida queda vacía). Los mensajes son los mismos que para datos ajenos, en español.
- **Secretos en memoria:** el borrado de claves de la tarea 4 no cambia; la rama quitada era una copia de la clave que el
  llamante ya borraba.
- **Red, permisos, logs, `FLAG_SECURE`:** sin cambios.

## Revisión Ingeniero Móvil Senior

- **Compatibilidad:** `Dialect` se queda con una entrada, así que no cambia ninguna firma pública ni la llamada de
  `PasswordBytesTest` (`aead(…, header.dialect)`). `VaultFile.format` obligatorio: kotlinx.serialization lanza
  `MissingFieldException` (también con `"format": null`, porque `coerceInputValues` solo actúa con valor por defecto), y
  `decodeFile` la convierte en `CryptoFailure` (`Vault.kt:381-385`); `encodeFile` lo escribe siempre.
- **Arranque:** `VaultStore.load` (`VaultStore.kt:247-249`) apartaría un `vault.bin` sin `format`; la sección 3 muestra que
  no hay ninguno.
- **Huella (ronda 1):** `biometricRefusal` decide antes de usar la clave para leer. `VaultScreen.kt:310` y `:352` lo
  consultan para enseñar «Usar huella» o el motivo, antes de cualquier aviso de la huella. `VaultStore.kt:454` lo consulta
  tras `resolve`, que con una versión dicha no usa la clave (`Vault.kt` `resolve`), y antes de `opened`, que lee las
  entradas. Una versión que no se abre lanza `MasterRequired`, que no desactiva la huella (tarea 3).
- **Pantallas:** solo cambian cinco cadenas en Compose (sin estado ni diseño nuevo). «Abrir lo recibido» trata un `.cpenc`
  como cualquier archivo (`PkActions.kt:462`, `:480-481`).
- **Hilos y ciclo de vida:** sin cambios; la importación sigue fallando antes de tomar el `mutex` (`VaultStore.kt:760`).
- **Compilación:** lint, `assembleDebug`, `checkLicense`, `checkFragmentVersion` y `checkAssets` en verde (sección 5).

## Commits

- `ba572c0` fuera CriptoPanel: Clavero deja de leer CP1:, .cpenc y criptopanel-vault (código, tests, datos de prueba y
  README juntos, porque los tests no compilan sin el código). Autor `Claude <noreply@anthropic.com>`; el mensaje termina
  solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (`grep -cE "Claude-Session|claude\.ai"` da 0).
  Push a `origin/ccr-a1a6eabb-765f9g` (`1a65545..ba572c0`).


---

# Ronda 1: los 7 detalles menores de la revisión

**Estado:** DONE. Commit: `875f037` (encima de `4a97f7b`, el registro del controlador), con push a `origin/ccr-a1a6eabb-765f9g` (`4a97f7b..875f037`); `git log -1 --format=%B | grep -cE "Claude-Session|claude\.ai"` da 0.

| # | Hallazgo | Arreglo |
|---|---|---|
| 1 | `Vault.kt:246`, el comentario «Not the default…» ya no era verdad | `// Required (no default), so it is always written out.` |
| 2 | `Vault.kt:332`, una línea de 166 caracteres en el KDoc de `afterMasterUnlock` | El primer párrafo, partido de nuevo a ≤ 120 (`Vault.kt:327-339`). |
| 3 | `Vault.kt:122-124`, el KDoc de `version` se contradecía | «This app always writes it: 2 (1.0 and 1.1) or 3. A file without it ([VERSION_UNKNOWN], edited by hand) is tried as both…» (`Vault.kt:119-125`). |
| 4 | `Vault.kt:399`, la guarda `version == 1` era arbitraria | Opción A del controlador: `!supported(file) -> BiometricRefusal.MASTER_DUE` (`Vault.kt:401`), con el KDoc reescrito (`Vault.kt:388-396`) y el test `VaultV3Test.biometricRefusalAsksForTheMasterForAVersionOrFormatThatDoesNotOpen` (`VaultV3Test.kt:682`). |
| 5 | `DialectTest.kt:24-25`, la procedencia de los datos | El KDoc dice de dónde viene cada uno: los 8 `CP1:` de `texts.json` son de CriptoPanel 1.10.0 (`bac076a`), y su lista `files` nombra dos `.cpenc` borrados en la 1.3.1. `ref.cpenc` es un archivo del contenedor de CriptoPanel: llegó con el traslado desde CriptoPanel (`c70f932`), donde su test lo llamaba «written by an independent implementation», y el repositorio no guarda qué lo escribió. Los datos de `criptopanel/` no se han tocado. |
| 6 | Se perdía la única comprobación independiente del contenedor | Dos vectores hechos fuera de Clavero con `cryptography` 50.0.1 de Python, como constantes en `DialectTest` (`SG1 texts and sgenc files written outside Clavero with Python cryptography open`, `DialectTest.kt:128`). Clavero los abre a la primera, sin ajustar nada. El generador (abajo) también abre, sin el código de Clavero, los vectores `t1` y `f1` de la 1.3.0. |
| 7 | `AppNameTest.kt:103` solo comparaba literales enteros | `no code reads CriptoPanel` falla también si un literal contiene `"CP1:"` o `".cpenc"` (este último sin distinguir mayúsculas). Hoy sigue en verde (`AppNameTest.kt:100-110`). |

## Comprobación previa de la 4 (antes de cambiar nada)

- **Quién llama a `biometricRefusal` y `biometricAllowed`:**
  - `VaultScreen.kt:310`: `bio = … && store.biometricAllowed` decide si se enseña «Usar huella» y si se lanza el aviso de la huella (`:324`), así que va antes de usar la clave;
  - `VaultScreen.kt:352`: el texto del motivo;
  - `VaultStore.kt:425` y `:428`: los dos *getters* que usa la pantalla, sobre el archivo cargado;
  - `VaultStore.kt:454`, en `unlockWithKeyLocked`: después de `resolve`, que con una versión dicha devuelve el archivo sin tocar la clave (`if (file.version != VERSION_UNKNOWN) return file`), y antes de `opened`, que lee las entradas con la clave.

  Ninguno usa la clave de la huella para leer antes de la comprobación.
- **Tests que fijan otro comportamiento:** no hay ninguno.
  - `VaultV3Test.kt:676` fija que la versión 1 da `MASTER_DUE`, y sigue igual.
  - Los tests de la huella con versiones cambiadas (`VaultV3Test.kt:522-580`) usan 2, 3 o sin versión, todas abribles, y siguen esperando `CryptoFailure`.
  - `VaultV3Test.kt:485` (una versión 4 con la maestra da `CryptoFailure`) no pasa por la huella.
- **Lo que cambia:** antes, un `vault.bin` con versión 4 (o cualquiera que no fuera 1) y la fecha al día llegaba a `opened` → `read`, fallaba con `CryptoFailure` y la regla de la tarea 3 desactivaba la huella. Ahora da `MasterRequired(MASTER_DUE)`: la huella no se desactiva y la maestra dice «No es una bóveda compatible». Para 0, 2 y 3 no cambia nada: el test da `null` dentro de los 7 días.

## TDD

- **RED** (con los tests nuevos y `Vault.kt` aún con `version == 1`): `./gradlew :app:testDebugUnitTest --tests '*VaultV3Test*' --tests '*DialectTest*' --tests '*AppNameTest*' --offline --no-daemon --max-workers=1` dio:

  ```
  VaultV3Test > biometricRefusalAsksForTheMasterForAVersionOrFormatThatDoesNotOpen FAILED
      java.lang.AssertionError: version 4 expected:<MASTER_DUE> but was:<null>
  49 tests completed, 1 failed
  ```

  En esa misma ejecución pasaron:
  - el test de los vectores de Python, porque Clavero los abre con el código de antes (no es un test de cambio: si hubiera fallado, era un hallazgo);
  - `no code reads CriptoPanel`, ya más amplio, porque no queda ningún literal así.
- **GREEN**, tras el cambio de la 4: `./gradlew :app:testDebugUnitTest --tests '*VaultV3Test*' --tests '*VaultTamperTest*' --tests '*DialectTest*' --tests '*CryptoTest*' --tests '*AppNameTest*' --tests '*LegacyVaultTest*' --tests '*VaultStoreTest*' --tests '*AppContainerTest*' --offline --no-daemon --max-workers=1` dio `BUILD SUCCESSFUL`, con 189 tests y 0 fallos:

  | Clase | Tests |
  |---|---|
  | AppContainerTest | 7 |
  | AppNameTest | 8 |
  | CryptoTest | 32 |
  | DialectTest | 7 |
  | LegacyVaultTest | 11 |
  | SeedCryptoTest | 14 |
  | VaultStoreTest | 70 |
  | VaultTamperTest | 6 |
  | VaultV3Test | 34 |

  No sale ningún aviso de Kotlin nuevo.
- **Verificaciones globales:**

  ```
  gradle -p jvm-check cleanTest test --no-daemon --max-workers=1 --offline
  BUILD SUCCESSFUL in 1m 37s        → 1269 tests, 0 fallos, 0 omitidos (antes de la ronda, 1267)
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
  BUILD SUCCESSFUL in 3m 3s         → 1274 tests, 0 fallos, 0 omitidos (antes de la ronda, 1272); lint y comprobaciones en verde
  ```

  Son dos tests más en cada una: los vectores de Python (`DialectTest`) y el de la huella (`VaultV3Test`). No se borra ninguno.

## El generador de los vectores independientes (Minor 6), entero

Ejecutado con `python3 -I ../pygen/sg_vectors.py` desde un directorio vacío del scratchpad (`pyvec/`), con `cryptography`
50.0.1. No se guarda en el repositorio.

```python
"""Independent SG1: and .sgenc vectors for Clavero (task 6, review Minor 6).

Built byte by byte from the container format, with Python's `cryptography` only (no Clavero code):

  header = "SG" | 0x01 (version) | kind (1 text, 2 file) | alg id (1 AES-256-GCM, 2 ChaCha20-Poly1305) | kdf id (1 Argon2id)
           | memoryKiB (4 bytes, big-endian) | iterations (1) | parallelism (1) | salt (16) | nonce (12 text, 7 file prefix)
  key    = Argon2id v0x13 (password in UTF-8, salt, memoryKiB, iterations, parallelism), 32 bytes
  text   = "SG1:" + base64url without padding (header || AEAD(key, nonce, plaintext, aad = header))
  file   = header || u16 big-endian length of the sealed name || sealed name || records
           sealed name = AEAD(key, prefix || counter 0 (4 bytes BE) || 0x00, name in UTF-8, aad = header)
           record i    = AEAD(key, prefix || counter i (4 bytes BE) || last flag, chunk of at most 64 KiB, aad = header)

Run with `python3 -I sg_vectors.py` from a scratch directory.
"""
import base64
import struct

from cryptography.hazmat.primitives.ciphers.aead import AESGCM, ChaCha20Poly1305
from cryptography.hazmat.primitives.kdf.argon2 import Argon2id

CHUNK = 64 * 1024


def argon2id(password: str, salt: bytes, memory_kib: int, iterations: int, lanes: int) -> bytes:
    return Argon2id(salt=salt, length=32, iterations=iterations, lanes=lanes, memory_cost=memory_kib).derive(
        password.encode("utf-8"))


def header(kind: int, alg: int, memory_kib: int, iterations: int, lanes: int, salt: bytes, nonce: bytes) -> bytes:
    assert len(salt) == 16
    return b"SG" + bytes([1, kind, alg, 1]) + struct.pack(">I", memory_kib) + bytes([iterations, lanes]) + salt + nonce


def stream_nonce(prefix: bytes, counter: int, last: bool) -> bytes:
    assert len(prefix) == 7
    return prefix + struct.pack(">I", counter) + bytes([1 if last else 0])


def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode("ascii")


def unb64url(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


# Sanity check of the library: the RFC 9106 Argon2id vector (CryptoTest checks the same one against Bouncy Castle).
rfc = Argon2id(salt=b"\x02" * 16, length=32, iterations=3, lanes=4, memory_cost=32, ad=b"\x04" * 12,
               secret=b"\x03" * 8).derive(b"\x01" * 32)
assert rfc.hex() == "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", rfc.hex()

PASSWORD = "contraseña independiente 🔐"

# 1. An SG1: text: AES-256-GCM, Argon2id m=1024 KiB, t=2, p=2.
text_plain = "Texto escrito fuera de Clavero: ñ, € y 🔐\nsegunda línea".encode("utf-8")
t_salt = bytes(range(0x30, 0x40))
t_nonce = bytes(range(0x60, 0x6C))
t_header = header(1, 1, 1024, 2, 2, t_salt, t_nonce)
assert len(t_header) == 40
t_key = argon2id(PASSWORD, t_salt, 1024, 2, 2)
token = "SG1:" + b64url(t_header + AESGCM(t_key).encrypt(t_nonce, text_plain, t_header))

# 2. A .sgenc file: ChaCha20-Poly1305, Argon2id m=2048 KiB, t=1, p=1, one data record (the last one).
f_name = "informe ñ.txt".encode("utf-8")
f_data = bytes((i * 13 + 5) % 256 for i in range(300))
f_salt = bytes(range(0x80, 0x90))
f_prefix = bytes(range(0xA0, 0xA7))
f_header = header(2, 2, 2048, 1, 1, f_salt, f_prefix)
assert len(f_header) == 35
f_key = argon2id(PASSWORD, f_salt, 2048, 1, 1)
aead = ChaCha20Poly1305(f_key)
sealed_name = aead.encrypt(stream_nonce(f_prefix, 0, False), f_name, f_header)
records = b""
chunks = [f_data[i:i + CHUNK] for i in range(0, len(f_data), CHUNK)] or [b""]
for i, chunk in enumerate(chunks):
    records += aead.encrypt(stream_nonce(f_prefix, i + 1, i == len(chunks) - 1), chunk, f_header)
sgenc = f_header + struct.pack(">H", len(sealed_name)) + sealed_name + records

print("TOKEN=" + token)
print("SGENC=" + base64.b64encode(sgenc).decode("ascii"))
print("TEXT_PLAIN=" + repr(text_plain.decode("utf-8")))
print("FILE_DATA_SHA256=" + __import__("hashlib").sha256(f_data).hexdigest())

# 3. The other way round: DialectTest's vectors written by Clavero 1.3.0 open here, without Clavero's code.
t1 = ("SG1:U0cBAQEBAAAEAAEBEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqK_qmjh65h-WTEM00aWiqe-9m-PXydQpOv71GhobYWRiWzyze8aXraqbT"
      "73icGvfiR8zB6oYTLQ1ZCC3NALJN8mKNbGTmQw")
raw = unb64url(t1[4:])
h, body = raw[:40], raw[40:]
assert h[:6] == b"SG\x01\x01\x01\x01"
mem, it, lanes = struct.unpack(">I", h[6:10])[0], h[10], h[11]
key = argon2id("contraseña de prueba", h[12:28], mem, it, lanes)
assert AESGCM(key).decrypt(h[28:40], body, h).decode("utf-8") == "Texto de Clavero 1.3.0: ñ, € y 🔐\nsegunda línea"
f1 = base64.b64decode(
    "U0cBAgIBAAAEAAEBcHFyc3R1dnd4eXp7fH1+f4CBgoOEhYYAHJHId3PcDvB9jauZDbUnbiOTO9FtM/R8uSphjHCjuospYjRDLbOIQkpppsxQCWxlPpCc"
    "Mg14nc0RC6mLck8N2Fjxht8inWoTYtStWdfF042E+JHF66Gf109uBzd5yZ2ko7Jc/AvbswrMm+yj1Rwloa0or0Qn/fpLazSDNpoDAidRAebOGURcjy6j"
    "S9F4gCo+5w==")
h = f1[:35]
assert h[:6] == b"SG\x01\x02\x02\x01"
mem, it, lanes = struct.unpack(">I", h[6:10])[0], h[10], h[11]
key = argon2id("contraseña de prueba", h[12:28], mem, it, lanes)
prefix = h[28:35]
n = struct.unpack(">H", f1[35:37])[0]
name = ChaCha20Poly1305(key).decrypt(stream_nonce(prefix, 0, False), f1[37:37 + n], h)
data = ChaCha20Poly1305(key).decrypt(stream_nonce(prefix, 1, True), f1[37 + n:], h)
assert name.decode("utf-8") == "datos ñ.txt" and data == bytes(i * 7 % 251 for i in range(100))
print("CLAVERO_1_3_0_VECTORS_OPEN_IN_PYTHON=OK")
```

Salida:

```
TOKEN=SG1:U0cBAQEBAAAEAAICMDEyMzQ1Njc4OTo7PD0-P2BhYmNkZWZnaGlqa33HEVI8DXD57gJ6TTrKyHfqnrEV62CbXvr3MVPhH7JGc71sgsw1kX4CAk4dtbZW2vFnSFUQ5J2dLo-QkqprzC_XQOUE5Z0rM-6Duy_H
SGENC=U0cBAgIBAAAIAAEBgIGCg4SFhoeIiYqLjI2Oj6ChoqOkpaYAHniay2z+OQCax8OzeY9fXB/+92x1MW9HcQU0VW3QwtsHOqtOJkGNhRalWuBgeKBlvkOpkf3xQjjLo5guvUy66BV901kU0CN8l0gm7/U418KDzQt/Fp0fauc8qRzeJHb7K2HsuLM3ci/l7MVrfxosrakfwidknX0PSAb2lM+JC0DDytknWKTC9CMtC6HaKSq0nEaDoFtsz4T63MPDzbpJgxXDVGlOj3H3OjudEOOopPe2U2z5drvUTf/54PXFMQ1yzWEAhicDsO2dfTzySQh44BQbGhxr333GYzCvgSmgJtm1gY09+l8k9n/WaOFFpq73WRldRNiBDwSsoCvWqvrJcgermgWolVhwO8k2rgc+hL+XIRghnecE9HFTgQSt/uJhQfHj7X4hVr0fBzy8NuI8DHpXSvKjjS9NFyOnXO1I+v+58HQLwgaXPcL+BoGxxLEkgyv3AqyjWKhPayE=
TEXT_PLAIN='Texto escrito fuera de Clavero: ñ, € y 🔐\nsegunda línea'
FILE_DATA_SHA256=77c217f22a739fe20c0612284c38ecbb560c2b621b06f11578adbe27899d2ca9
CLAVERO_1_3_0_VECTORS_OPEN_IN_PYTHON=OK
```

**Qué cubren:**
- la cabecera completa con un coste que no es el de la app: m = 1024 KiB, t = 2 y p = 2 en el texto, que el test comprueba que se leen de la cabecera; m = 2048 KiB, t = 1 y p = 1 en el archivo;
- una contraseña con «ñ» y un emoji fuera del BMP, en UTF-8;
- AES-256-GCM y ChaCha20-Poly1305 con la cabecera entera como AAD;
- el nonce STREAM (prefijo, contador y marca de último), el registro 0 del nombre y su longitud en 2 bytes;
- el prefijo `SG1:` y el Base64url sin relleno.

**Qué no cubren:** un segundo registro de datos. Serían 64 KiB de constantes, y el contador por encima de 1 ya lo cubren las idas y vueltas.

## Textos para el Abogado Digital (ronda 1)

Ninguno nuevo. La huella con un archivo que no se abre usa el texto que ya había para la versión 1 (`MasterRequired`, «Escribe tu contraseña maestra (se pide cada 7 días)»), y después la maestra dice «No es una bóveda compatible».

## Revisión experto-seguridad-cripto (ronda 1)

- **La 4 cierra un caso que desactivaba la huella:** un `vault.bin` manipulado con una versión desconocida ya no llega a leer con la clave de datos (`Vault.kt:401`). No cambia ningún formato ni la AAD.
- **La 6 da una comprobación independiente real.** Argon2id desde la cabecera, AEAD con la cabecera como AAD, STREAM y el registro del nombre, todo hecho con una implementación distinta, abren con `decryptText` y `decryptStream`. El generador comprueba antes la librería contra el vector de RFC 9106, el mismo que usa `CryptoTest` contra Bouncy Castle. La comprobación va en los dos sentidos.
- **La 7 vigila también un texto que vuelva a ofrecer `CP1:` o `.cpenc`.**

## Revisión Ingeniero Móvil Senior (ronda 1)

- **El orden con la huella es correcto:** `biometricRefusal` va antes de que se use la clave para leer, y antes del aviso en la pantalla (ver la comprobación previa). `!supported` es una función privada del mismo archivo y no cuesta nada: no hace E/S ni deriva claves.
- **Comentarios y KDoc:** dicen la verdad y tienen como mucho 120 caracteres en los párrafos tocados. `Vault.kt:343` (el párrafo CR-2, que no se ha tocado) sigue con 126.
