# Clavero 1.4.0: auditoría completa (último paso de seguridad antes del Abogado y del PR)

**Rama:** `ccr-d1e75160-p5f4wc`, HEAD `c3e89a4` (registro sobre `e3aa329`). **Alcance:** `git diff 7c8dea7..HEAD -- app/src/main app/build.gradle.kts build.gradle.kts gradle .github scripts jvm-check/build.gradle.kts` (97 archivos, unas 11 600 líneas añadidas), más el manifiesto y `res/xml`, que no cambian. **Método:** `experto-seguridad-cripto` (`SKILL.md`, `references/auditoria.md`, `references/cripto-moderna.md`) e Ingeniero Móvil Senior (`auditoria-seguridad-movil` con OWASP MASVS v2, `seguridad-criptografia-movil` y su `recetas-cripto.md`, `desarrollo-movil-senior`). La skill «auditoria-seguridad» no está instalada en esta sesión: su papel lo cubre `references/auditoria.md` (método, inventario, entradas hasta el sumidero, cadena de suministro), que se ha seguido. Sin subagentes y sin commit.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`. Lo que se da por bueno en una línea «Ruling» de `progress.md` no se repite, salvo que sea falso sobre el código (no se ha encontrado ningún caso).

**Modelo de amenaza:** otra app del móvil (archivos y documentos que deja elegir, portapapeles, superposiciones), un archivo exportado o una copia manipulados (lo importado es dato hostil, decisión (c)), quien tenga el móvil desbloqueado unos minutos, la muerte del proceso y el giro, y la red hacia el único host. No se protege frente a un móvil con *root* o con el sistema comprometido, ni frente a quien conoce la maestra.

## 1. Veredicto

**Se puede publicar cuando se arregle F-1 (Bajo).** No hay hallazgos críticos, altos ni medios. F-1 es un texto de la vista previa que afirma algo falso («Se reemplaza 1 entrada de la bóveda») en el caso nuevo de R67, y un cupo de etiquetas que se gasta sin motivo. No pierde datos ni abre ninguna vía de ataque, pero la regla del dueño («Detalles menores: se arreglan todos») y la regla de veracidad lo hacen obligatorio antes del PR. F-2 es informativo y cabe en la misma ronda.

Se confirma sobre el código:
- los tres arreglos de `e3aa329` (R66 a R68) y R70;
- las decisiones (a), (b), (c) y (h);
- las reglas del proyecto: primitivas estándar, un solo host, sin logs, `FLAG_SECURE`, secretos fuera de `rememberSaveable` y de `Bundle`, los mismos permisos y componentes, los formatos de siempre y los nombres de otras apps solo en `ImportTexts.kt`.

## 2. Revisión acotada de `e3aa329` (R66 a R68, R70)

Paquete `.superpowers/sdd/2026-11-03-clavero-1.4.0/review-030dfdb..e3aa329.diff` e informe `final-review-fixes-report.md`.

Ejecutado aquí con el *wrapper*: `./gradlew -p jvm-check test` sobre `Slip39Test`, `SecretShareInputTest`, `MergePlanTest`, `VaultStoreImportTest`, `CsvTest`, `TotpImportTest` e `ImportFlowTest`. Resultado: **141 tests, 0 fallos y 0 omitidos**, con los vectores oficiales `officialVectors` de SLIP-0039 en verde.

### R66: un bloqueo durante la copia previa cancela la importación — correcto

- La época sube en cada bloqueo (`vault/VaultStore.kt:553-554`).
- `confirm` la lee en el hilo principal justo después de `startSaving()`, sin ninguna suspensión entre las dos (`ui/screens/ImportScreen.kt:329-331`), y se la pasa a `applyImport` (`:339`).
- `applyImport` la compara dentro de `change`, con el mutex cogido y en la misma sección crítica que la escritura: `if (sinceLock != null && epoch != sinceLock) return@change null` (`vault/VaultStore.kt:698`). El resultado se queda en `Stale`.
- Por eso, un bloqueo seguido de un desbloqueo durante `copyBeforeImport()` da `Stale` aunque la revisión no haya cambiado. El desbloqueo no toca `_revision` (`:210-212`): solo sube con un cambio guardado. Así que, antes de R66, solo la época podía distinguir este caso.
- Lo que pasa después:
  - `ImportFlow` devuelve `NotApplied(Stale)` y la copia se queda (`imports/ImportFlow.kt:39-60`);
  - la sesión ya se había borrado con el bloqueo (`imports/ImportSession.kt:391-402`), así que el resultado no se enseña y sale `IMPORT_WIPED_WHILE_SAVING`.
- Un guardado ya empezado termina: la comprobación es la primera del bloque con el mutex, y `commit` guarda aunque la bóveda se bloquee después (`vault/VaultStore.kt:601-622`).
- **Ventana anterior a `since`:** todas las llamadas a `lock()` van en el hilo principal (`MainActivity.kt:113`, `VaultScreen.kt:121,129,171`, `ContactsScreen.kt:108`, `AppContainer.kt:173`). El recolector de `store.state` borra la sesión (`ImportScreen.kt:196`) antes de que el *Looper* entregue otro toque. No queda ninguna ventana en la que `confirm` lea un plan anterior al bloqueo con la época de después.

### R70: `copyTo` apunta la época al elegir el sitio — correcto

- `since` se lee después de `session.copyPlaceChosen()` (`ui/screens/ImportScreen.kt:350,356`) y se pasa a `applyImport` (`:359`).
- Un bloqueo mientras el diálogo `CREATE_DOCUMENT` está abierto borra la sesión. Entonces `copyPlaceChosen()` devuelve `null` y el documento se borra (`:351-354`); no se importa nada.
- Un bloqueo mientras se escribe la copia: `exportBackup` devuelve `false` (`IMPORT_COPY_LOCKED`), o la época ya no coincide y el resultado es `Stale`.

### R67: el 2FA de un acceso cuenta como duplicado — correcto, con F-1

- El `Matcher` de la bóveda se crea con `codesOfEveryType = true` (`imports/MergePlan.kt:91`) y saca la clave de toda entrada que lleve `totp` (`:577`). Es la misma regla que `totpImportEntries`.
- El `Matcher` del archivo sigue mirando solo las entradas `TOTP`.
- «Reemplazar» sobre ese acceso no cambia nada, porque `replaceWith` devuelve `existing` cuando el tipo es otro (`:277`). Como la clave es la misma, no se pierde ningún secreto.
- Sin embargo, la vista previa y el cupo de etiquetas no lo saben. Es el hallazgo **F-1**.

### R68: correcto

- **Cupo de etiquetas.** `tagsFor(..., onto)` (`imports/MergePlan.kt:140-150`) solo cuenta las etiquetas que la entrada no tiene, hasta `MAX_TAGS`. Es la misma cuenta que hace `replaceWith` (`:308`).
  - `onto` es la entrada tal como va quedando: `out[it].tags` (`:225`), así que dos duplicados de la misma entrada se aplican uno tras otro.
  - `lost` sigue siendo exacto: compara `replaceWith` con todas las etiquetas y con las filtradas (`:239`).
  - El caso de otro tipo (R67) queda fuera de esta cuenta: es F-1.
- **`hexLower`.** `totpKeyOf` (`vault/TotpImport.kt:11`) da el mismo resultado que antes y sigue poniendo a cero los bytes decodificados. `hexWith` (`crypto/Hashes.kt`) es una tabla sin `String.format`.
- **`drawsNothing` en SLIP-0039 y BIP-39.**
  - `normalizedWord` (`tools/slip39/Slip39.kt:314-325`) va por puntos de código después de NFKC.
  - `cleanPhrase` (`tools/slip39/SecretShareInput.kt:55-70`) quita la lista antes de NFKC, cambia un sustituto suelto por «?» y nunca se lo pasa al normalizador.
  - Una marca que precede a una cifra (U+0600…) ya no se quita y da «no es de la lista» (R70). El fallo es cerrado: nunca se acepta una palabra que no está en la lista.
  - Nada cambia en la derivación ni en las sumas de comprobación. Pasan los tests `officialVectors`, `bip39RoundTrip` y `caseSpacingAndUnicodeFormsAreAccepted`.
- **CSV en una pasada.** `parseCsvOf` (`imports/Csv.kt:36-52`) calcula el delimitador una vez y lee la cabecera una vez.
  - Si la cabecera no se puede leer (por ejemplo, un JSON minificado con más de 64 comas en la primera línea), devuelve `null` y se pregunta al siguiente lector, como antes con `csvHeader`.
  - Pasada la cabecera, un error se propaga como antes. El lector se borra en un `finally`.
  - Mantiene los topes de siempre: `CSV_MAX_COLUMNS`, `CSV_MAX_CELLS`, `IMPORT_MAX_ITEMS + 1` y `CSV_MAX_FIELD_CHARS` (`:116,192` y `append`).

## 3. MASVS v2

| Grupo | Veredicto | Evidencia |
|---|---|---|
| **STORAGE** | Cumple | `allowBackup="false"`, `dataExtractionRules` y `fullBackupContent` excluyen todos los dominios (`AndroidManifest.xml:11-13`, `res/xml/data_extraction_rules.xml`, `backup_rules.xml`, sin cambios). Lo leído del archivo vive solo en memoria y se pone a cero: lectura acotada a `IMPORT_MAX_BYTES + 1` (`ui/screens/ImportScreen.kt:245`), `ImportSession.zeroBytes`/`dropAll` (`imports/ImportSession.kt:405-429`), `Secrets.wipe` en cada lector cifrado (`imports/ImportKdf.kt:104-125`) y `CsvReader.wipe` (`imports/Csv.kt:200-204`). La copia antes de importar es una `.sgbak` cifrada con la maestra (`vault/AutoBackup.kt` `importCopyNow`, a través de `VaultStore.exportBackup`, `vault/VaultStore.kt:838-855`). La carpeta temporal de «Comprobar una copia» se borra pase lo que pase y al arrancar (`vault/BackupCheck.kt:109-112,136-143`; `SeguridadApp.kt`). Portapapeles: `copySensitive` en las cuatro herramientas nuevas (`android/Clipboard.kt:45`). El teclado no aprende de ningún campo (`ui/NoLearningInput.kt:24-31`, `MainActivity.kt:45`). |
| **CRYPTO** | Cumple | Solo Bouncy Castle y `javax.crypto`/`java.security`: PBKDF2, scrypt, Argon2id, HKDF, HMAC y GCM (`imports/ImportKdf.kt:13-21`), CBC con PKCS#7 (`imports/BitwardenCrypto.kt:162-182`) y digest de BC en el Laboratorio de hashes (`tools/hashlab/HashLab.kt:33-39`). En Bitwarden, primero el MAC (validación y `data`) con `MessageDigest.isEqual` y solo después CBC (`imports/BitwardenCrypto.kt:135-155`). Todos los parámetros del archivo se acotan antes de calcular nada: PBKDF2 de 5 000 a 2 000 000 iteraciones; scrypt con N potencia de 2 entre 2^14 y 2^20, N < 2^(16r) y memoria dentro de `checkSane`; Argon2id con `checkSane` (`imports/ImportKdf.kt:58-98`). Cada derivación fuerte va dentro de `KdfLock` (`crypto/KdfLock.kt:52-53`; `ImportScreen.kt:304`; `vault/BackupCheck.kt:85`; `vault/VaultStore.kt` `derive`; `crypto/FileCrypto.kt` `fileKey`). Aleatoriedad: `secureRandom` en SLIP-0039 (`tools/slip39/Slip39.kt:101-109`, `Shamir.kt:49-54`). Detalle en §4.2. |
| **AUTH** | Cumple | La huella está ligada a una operación criptográfica: `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)` y `AUTH_BIOMETRIC_STRONG` por uso (`android/BiometricVault.kt:116-127`), `BIOMETRIC_STRONG` y `CryptoObject(cipher)` (`:204,221`). Todas las operaciones van en `KeyOpsQueue`, una a la vez, y DISABLE cancela una activación en curso (`tools/KeyOpsQueue.kt:33-52`; `BiometricVault.kt:166-168,376,540`). Maestra nueva: `newMasterProblem` con NFKC y *leet* (`vault/MasterPolicy.kt:65,117-138,248-254`; decisión (h)). Abrir la bóveda, la huella e «Importar copia» no pasan por esta regla. |
| **NETWORK** | Cumple, sin cambios | El único host sigue siendo `https://api.pwnedpasswords.com/` (`net/PwnedPasswords.kt:24`), con los certificados de eMudhra bloqueados (`net/DistrustedCertificates.kt`). Ningún archivo de `net/` cambia. Fuera de los comentarios, ninguna cadena del diff es una URL nueva. La nota N-04 sobre Certificate Transparency sigue igual. |
| **PLATFORM** | Cumple | El manifiesto no cambia (`git diff --stat` vacío). APK de depuración del árbol final con `aapt2 dump permissions`: INTERNET, CAMERA, USE_BIOMETRIC, USE_FINGERPRINT y el permiso propio de AndroidX, los mismos que en la 1.2.2. `scripts/check-manifest.sh`: OK. Ninguna pantalla nueva abre un `Intent`, un enlace o HTML con datos importados: `openUri` solo aparece en `ui/screens/SettingsScreen.kt:497-499`, con las dos URL fijas de `:491-492`. Una página HTML se reconoce y nunca se analiza (`imports/Detect.kt:68-83`). `FLAG_SECURE`, el filtro de toques tapados y la exclusión de autorrelleno se ponen una vez para toda la app (`android/SecureWindow.kt:23-28`). Las pantallas nuevas no abren diálogos propios. |
| **CODE** | Cumple, con F-1 y F-2 | Entradas hostiles acotadas antes de leer o de construir nada: `ImportLimits.kt`; JSON con estimación del árbol y profundidad máxima de 64 antes de `parseToJsonElement` (`imports/ImportJson.kt:79-180,196-231`), sin deserializar tipos del archivo (solo accesores con nombre fijo, `:241-294`); texto UTF-8 o UTF-16 estricto, sin sustituir con U+FFFD (`imports/TextDecode.kt`); una excepción se convierte en un texto fijo, sin mensajes que salgan del archivo (`imports/Detect.kt:42-54`). R8 activo en *release* (`app/build.gradle.kts:60`). Las dependencias del APK no cambian (`gradle/libs.versions.toml`: solo AGP de 8.13.0 a 9.5.0). Las acciones de CI siguen fijadas por SHA; la suma del *wrapper* coincide con el JAR (`sha256sum`: `497c8c2a…a9c7`) y Gradle 9.6.0 tiene `distributionSha256Sum`. |
| **RESILIENCE** | Cumple lo que pide el perfil | R8 y reducción de recursos en *release* (`app/build.gradle.kts:59-61`), `FLAG_SECURE`, `filterTouchesWhenObscured` y la marca de datos sensibles para accesibilidad (`android/SecureWindow.kt:23-35`). No detecta *root* ni usa Play Integrity: la app no tiene servidor que verifique nada, y el modelo de amenaza excluye un sistema comprometido. Se da como límite aceptado, no como hallazgo. |
| **PRIVACY** | Cumple | Sin permisos, hosts, SDK ni registros nuevos (no hay `Log.` ni `println` en `app/src/main/java`). Lo importado no sale del móvil. Se ofrece borrar el archivo exportado, solo el que se eligió en esta importación y solo después de importar (`imports/ImportSession.kt` `takeRemovable`). «No recoge ni comparte datos» sigue siendo verdad. |

## 4. Comprobaciones por tema

### 4.1 Decisiones del dueño

- **(a) Entradas enormes:** cumple.
  - Tope de lectura de 16 MB más un byte, por bloques de 64 KiB y cancelable (`ImportScreen.kt:238-261`, `StopOnCancel`).
  - JSON: estimación del árbol y profundidad antes de analizar; árboles cifrados de 8 MiB como máximo (`ImportKdf.kt:56`); CSV con topes durante la lectura; listas de `otpauth://` con un tope por línea y por número.
  - SLIP-0039: tamaño de cada parte, número de partes y exponente hasta 8 (`Slip39.kt:145,171,210`). Laboratorio de hashes: `MAX_CODEC_CHARS`, `MAX_TEXT_CHARS` y archivo leído en trozos de 64 KiB (`HashLab.kt:42-74`). Laboratorio clásico: `MAX_CLASSIC_CHARS` (`tools/classic/ClassicLabSession.kt:10-13`). Contraseñas pegadas: `PASSWORD_FIELD_MAX_CHARS` (`vault/MasterPolicy.kt:31-34`). Copia: `BACKUP_MAX_READ_BYTES` (`vault/Backup.kt`, `BoundedInput`).
  - Todo va fuera del hilo principal (`Dispatchers.Default` o `IO`), y un `OutOfMemoryError` solo se captura alrededor de `readImport` (`ImportScreen.kt:296-304`).
- **(b) Carreras entre comprobar y usar:** cumple.
  - Se lee una sola vez y se usan esos bytes (`ImportSession.hold`/`bytesFor`).
  - El plan sale de una instantánea tomada con el mutex (`VaultStore.kt:666-670`). Revisión y época se comparan con el mutex cogido (`:696-698`).
  - Un resultado de un *run* viejo se descarta (`ImportSession.onRead`/`onPlan`; `SecretShareRuns.kt:48-55,75-88`; `BackupCheckRuns`).
  - Los secretos se comparan en tiempo constante: MAC de Bitwarden, *digest* de SLIP-0039 y huella esperada (`BitwardenCrypto.kt:151`, `Shamir.kt:76`, `HashLab.kt:98`).
  - «Comprobar una copia» lee la cabecera para avisar antes de pedir la contraseña, y la comprobación vuelve a leer y comprueba lo que lee (`BackupCheckScreen.kt:143-175,178-188`).
- **(c) Lo importado es dato hostil:** cumple.
  - Solo como texto: `plainReportText` en las listas, la cabecera, el usuario, la web, las etiquetas y el diálogo de borrar (`VaultEntryScreen.kt`, `VaultScreen.kt`, `ImportScreen.kt:663-680`).
  - Se guarda tal cual. No se abre como enlace ni como `Intent`.
  - El JSON se lee como árbol con accesores de nombre fijo. Un archivo cifrado dentro de otro no se descifra por segunda vez (`BitwardenCrypto.kt:191-196`).
  - Las entradas importadas se crean con id nuevo, sin adjuntos y sin historial (`MergePlan.kt:227`).
- **(h) La maestra en *leet*:** cumple. `isKnownWordInLeet` (`vault/MasterPolicy.kt:117-138`):
  - lee la contraseña en NFKC;
  - antes y después de la palabra solo admite cifras y símbolos;
  - prueba «1», «!» y «|» todos como «i» y todos como «l»;
  - no mira nada por encima de 128 puntos de código;
  - y se aplica solo en `newMasterProblem` (`:248-254`), es decir, al crear la bóveda y al cambiar la maestra.

  `MasterPolicyTest` pasa. Lo que queda (RT-11) está apuntado para la 1.4.1 (R50).

### 4.2 Criptografía de las exportaciones, SLIP-0039 y hashes

- **Bitwarden protegido con contraseña:**
  - la cabecera se comprueba entera antes de derivar (`BitwardenCrypto.kt:70-93`);
  - la clave sale de PBKDF2-SHA256 o de Argon2id, este con la sal pasada por SHA-256, en su propia llamada a BC y con `checkSane` antes (R23, `ImportKdf.kt:192-204`);
  - después, HKDF-Expand con «enc» y «mac» (`BitwardenCrypto.kt:123-126`);
  - se comprueban los dos HMAC-SHA256 sobre IV ‖ texto cifrado, comparados con `isEqual`, y solo entonces se descifra CBC (`:135-139`);
  - un relleno mal hecho después de un MAC bueno da `IMPORT_BROKEN`, así que no hay oráculo de relleno: el MAC va antes.
- **Aegis:**
  - scrypt con `checkScrypt` en todas las ranuras antes de la primera derivación, y solo se prueba la primera (R24, `AegisCrypto.kt:55-88`; las referencias de esta lista son líneas del propio archivo);
  - la clave maestra se desenvuelve con AES-256-GCM y la base de datos se abre con GCM y etiqueta de 128 bits (`ImportKdf.kt:218-222`, a través de `blockGcm`, que borra la salida si la etiqueta falla).
- **2FAS:** PBKDF2-SHA256 con 10 000 iteraciones fijas, comprobadas; IV de 12 bytes; sal de 8 a 1024 bytes; texto cifrado al menos tan largo como la etiqueta; GCM (`imports/TwoFasCrypto.kt:36-53,77-82`). Las 10 000 iteraciones son el formato de 2FAS, no una elección de Clavero.
- **SLIP-0039:**
  - Feistel de 4 rondas con PBKDF2-HMAC-SHA256 de BC, `2500 << e` iteraciones y prefijo «shamir» ‖ id solo si `ext = 0`. En descifrado, las rondas van al revés (`Slip39.kt:339-375`).
  - GF(256) con el polinomio 0x11B y producto en tiempo constante (`tools/slip39/Gf256.kt:35-46`). Las tablas de logaritmos solo se usan con índices, que son públicos (`Shamir.kt:18-33`).
  - RS1024 con el `GEN` de la especificación y las cadenas «shamir» y «shamir_extendable» (`Rs1024.kt:8-37`). La suma se comprueba antes de leer ningún campo (`Slip39.kt:221`).
  - El relleno debe ser de 8 bits como máximo y todo ceros (`:224-241`).
  - *Digest* HMAC en el índice 254, comprobado en tiempo constante (`Shamir.kt:69-86`). Se rechazan el 1-de-N (`Slip39.kt:107`) y los grupos de más o de menos (`:173-179`).
  - Pasan los vectores oficiales.
  - No es criptografía propia: es una especificación publicada, con primitivas de BC y de `javax.crypto` y verificada con sus vectores (R4).
- **Laboratorio de hashes:** SHA-256, SHA-512, SHA3-256, BLAKE2b-512 y BLAKE3-256 de BC, y HMAC-SHA256 (`HashLab.kt:25-95`). Los decodificadores tienen tope y solo aceptan ASCII (`:118-168`).

### 4.3 Concurrencia, ciclo de vida, muerte del proceso y giro

- **`KeyOpsQueue`:** `Mutex` y un dispatcher de un solo hilo (`BiometricVault.kt:183`). Cada operación va entera dentro de la fila; un DISABLE cancela las activaciones y espera a que terminen sus `finally`. `KeyOpsQueueTest`: 10 tests en verde.
- **`ImportSession`:** cada resultado lleva su *run*; cualquier bloqueo borra la sesión (`ImportScreen.kt:196`); el guardado va en `NonCancellable`, así que salir de la pantalla no lo corta (R17).
- **`KdfLock`:**
  - orden de los cerrojos: primero el mutex de la bóveda y después `KdfLock`;
  - ningún bloque de `derive` llama a la bóveda, así que no puede haber un interbloqueo;
  - un bloque cancelado termina y suelta el cerrojo (`KdfLock.kt:52-53`).
- **`BackupCheckSession`:** la comprobación se para con cualquier bloqueo y con `ON_STOP` cuando no es un giro (`BackupCheckScreen.kt:113-121,193,209`). La clave de la copia se pone a cero pase lo que pase (`vault/BackupCheck.kt:85-87,110`).
- **Muerte del proceso:** nada se guarda en `rememberSaveable` ni en un `Bundle`; las cinco pantallas nuevas guardan su estado en el ViewModel (`grep`: ninguna aparece entre los usos de `rememberSaveable`). Si el proceso muere entre la copia y el guardado, la bóveda queda como estaba, porque el `rename` es atómico (`ImportFlow.kt:24-35`).
- **Giro:** el ViewModel sobrevive. `ON_STOP` con `isChangingConfigurations` no para nada.

### 4.4 Reglas del proyecto

| Regla | Estado | Evidencia |
|---|---|---|
| Solo `javax.crypto` y Bouncy Castle, sin criptografía propia | Cumple | §4.2; los cifrados clásicos son una herramienta didáctica que no protege ningún dato. |
| Red solo hacia `api.pwnedpasswords.com` | Cumple | §3 NETWORK. |
| Sin logs con datos y `FLAG_SECURE` | Cumple | No hay `Log.`, `println` ni `printStackTrace`; `android/SecureWindow.kt:24`. |
| Ningún secreto en `rememberSaveable` ni en un `Bundle` | Cumple | Los usos que quedan son anteriores y no son secretos (`PkActionsScreen`, `ContactAddScreen`, `IdentityScreen`, `LicensesScreen`, `ClipboardHistoryNotice`); ninguno está en las pantallas de la 1.4.0. |
| Sin permisos, componentes exportados, hosts ni dependencias nuevos en el APK | Cumple | `aapt2` y `check-manifest.sh`; `libs.versions.toml`. |
| `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` se siguen leyendo | Cumple | `VaultCrypto` no cambia. `Backup.kt` solo añade un tope de lectura y la versión. `FileCrypto` solo añade `KdfLock` y la regla de los nombres. `EntryTypeCompatTest` está en verde. |
| Nombres de otras apps solo en `ImportTexts.kt` | Cumple | Fuera de él, `grep` solo encuentra los literales permitidos de la 1.1 (Google Authenticator), «la copia de Google» y Guava. `ImportTextsTest`: 16 tests en verde. |
| Palabras prohibidas | Cumple | `SourceTextsTest`: 12 tests en verde. |

Tests de política y plataforma ejecutados aquí: `SourceTextsTest`, `ImportTextsTest`, `AppNameTest`, `PlatformSourcesTest`, `ImportJsonTest`, `RedTeam*`, `BackupCheck*`, `KeyOpsQueueTest`, `EntryTypeCompatTest` e `ImportModelTest`. Resultado: **184 tests, 0 fallos y 0 omitidos**. Criptografía: `SeedCryptoTest`, `AegisCryptoTest`, `TwoFasCryptoTest`, `BitwardenCryptoTest`, `ImportKdfTest`, `KdfLockTest`, `HashLabTest` y `MasterPolicyTest`. Resultado: **139 tests, 0 fallos y 0 omitidos**.

## 5. Hallazgos

| ID | Severidad | Hallazgo | Ubicación |
|---|---|---|---|
| F-1 | Bajo | Con R67, «Reemplazar» sobre un 2FA que ya está en un acceso cuenta como reemplazo y gasta el cupo de etiquetas, pero no cambia nada | `imports/ImportSession.kt:295,447`; `imports/MergePlan.kt:225,277`; `ui/screens/ImportScreen.kt:575` |
| F-2 | Info | `importHex` acepta cifras que no son ASCII (`Character.digit`) | `imports/ImportKdf.kt:254-255` |

### F-1 [Bajo] «Reemplazar» sobre un 2FA que está en un acceso: la vista previa dice que reemplaza, y el cupo de etiquetas se gasta

- **Dónde:**
  - `imports/MergePlan.kt:91,577`: con R67, el duplicado de un `TOTP` del archivo puede ser un `LOGIN` de la bóveda;
  - `:225`: `onto` se calcula para cualquier `REPLACE`, sin mirar el tipo;
  - `:140-150`: `tagsFor` cuenta esas etiquetas en `created`;
  - `:277`: `replaceWith` devuelve `existing` cuando el tipo es otro;
  - `imports/ImportSession.kt:447`: `previewCounts` cuenta el `REPLACE` como `replaced++`;
  - `:295`: con eso, `canConfirm` se activa;
  - `ui/screens/ImportScreen.kt:575`: la pantalla dice `importReplacedText(1)`.
- **Qué pasa:** se comprobó con un test temporal, que se borró después (el árbol quedó limpio en `git status`). Con un 2FA del archivo de clave igual a la de un acceso, y «Reemplazar» elegido:
  - `previewCounts` da `replaced = 1` y la vista previa enseña «Se reemplaza 1 entrada de la bóveda.»;
  - `mergeImport` devuelve la misma instancia del acceso, sin ningún cambio;
  - `TagBudget.tagsFor(..., onto = login.tags)` gasta una etiqueta nueva de carpeta («carpeta») que nunca se pone.
- **Impacto:**
  - El texto afirma algo falso: va contra la regla de veracidad, y lo debe saber el Abogado. Además, `IMPORT_REPLACE_NOTE` dice que «Reemplazar» toma «el 2FA» del archivo, y aquí no lo toma.
  - Si es lo único elegido, «Importar» se activa. Se hace la copia previa, no se guarda nada y la pantalla final dice «No ha entrado ninguna entrada nueva» y «0 reemplazadas».
  - Con más de 100 carpetas nuevas en el archivo, un elemento posterior puede quedarse sin su etiqueta de carpeta (`foldersNotTags` lo cuenta bien).
  - No se pierde ningún dato: la clave es la misma y la copia previa existe. Tampoco abre ninguna vía de ataque. Severidad: Baja (CWE-684, el producto no hace lo que dice su especificación).
- **Corrección propuesta:**
  1. En `Duplicate`, guardar si se puede reemplazar: `val replaceable: Boolean`, verdadero cuando `existing.type == item.entry.type` y la entrada no es personal. `planMerge` ya tiene a mano las dos entradas, porque `inVault` guarda el id y se pueden buscar por posición.
  2. En `previewCounts`, `REPLACE -> if (d.replaceable) replaced++ else skippedDuplicates++`.
  3. En `mergeImport`, si no se puede reemplazar, `continue` antes de `tagsFor`, que entonces no gasta nada; o bien `onto` solo cuando el tipo coincide, más el `continue`.
  4. En la fila (`DuplicateRow`), no ofrecer «Reemplazar» cuando no se puede, o poner debajo una línea fija, por ejemplo «Este código ya está en el acceso de la bóveda; «Reemplazar» no lo cambia.». Ese texto iría a la lista del Abogado.
  5. Un test en `MergePlanTest` (vista previa, entradas y cupo) y otro en `ImportSessionTest` (con solo eso, `canConfirm` es falso).
- **Referencia:** R67, regla de veracidad (decisión O-1 de la 1.3.1) y «Detalles menores: se arreglan todos».

### F-2 [Info] `importHex` acepta cifras hexadecimales que no son ASCII

- **Dónde:** `imports/ImportKdf.kt:254-255`, que usa `Character.digit(c, 16)`.
- **Qué pasa:** `Character.digit` acepta las letras de ancho completo de la A a la F (U+FF21…) y las cifras de otros sistemas («٣»…). La sal, el nonce o la etiqueta de Aegis escritos así se leen.
- **Impacto:** ninguno en la práctica: son parámetros que no son secretos, y la etiqueta GCM sigue autenticando. Solo es una lectura menos estricta de lo que dice (c) («analizados estrictamente») y de lo que hace `hexValue` del Laboratorio (`tools/hashlab/HashLab.kt:118-123`, solo ASCII).
- **Corrección:** en `importHex`, usar la misma tabla ASCII (`'0'..'9'`, `'a'..'f'`, `'A'..'F'`) y añadir un test con «ＡＢ». El `percentDecode` de `crypto/OtpMigration.kt:207-208` (de la 1.1, fuera del diff) hace lo mismo, pero luego rechaza todo valor de 0x80 o más. Se puede igualar en la misma ronda.

### Lo que está bien (para no romperlo)

- El MAC va antes del descifrado.
- Todo parámetro de una derivación se acota antes de calcular nada.
- Un solo cerrojo de derivación, con un orden de cerrojos fijo.
- Cada secreto se borra en un `finally`, a través de `Secrets`.
- Se usa lo que se leyó, una sola vez.
- La época y la revisión se comparan dentro de la sección crítica de la escritura.
- Nada importado llega nunca a un `Intent`, un enlace o HTML.
- Una sola lista de lo que no se dibuja (`core/Invisible.kt`).
- SLIP-0039 en tiempo constante, verificado con los vectores oficiales.
- El manifiesto y los permisos no cambian, y las acciones de CI están fijadas por SHA.

## 6. Siguientes pasos

1. Arreglar F-1, con sus tests y la línea de texto para el Abogado, y F-2 en la misma ronda. Después, una revisión acotada de esos cambios.
2. La revisión final del Abogado Digital, con la lista de abajo.
3. El PR a `main` con **build** y **jvm-check** en verde, y después las pruebas del dueño en el móvil (README, P-4 a P-27; P-19 con el APK firmado).

## Revisión experto-seguridad-cripto

- **Primitivas y parámetros:** solo hay primitivas estándar de BC y de `javax.crypto` (`imports/ImportKdf.kt:13-21`, `imports/BitwardenCrypto.kt:6-15`, `tools/slip39/Shamir.kt:3-6`, `tools/hashlab/HashLab.kt:9-16`). Los parámetros del archivo se acotan antes de calcular nada (`imports/ImportKdf.kt:58-98`). Los secretos se comparan en tiempo constante (`imports/BitwardenCrypto.kt:151`, `tools/slip39/Shamir.kt:76`, `tools/hashlab/HashLab.kt:98`).
- **Manipulación y contraseñas:** en Bitwarden, primero el MAC y después CBC, así que no hay oráculo de relleno (`imports/BitwardenCrypto.kt:135-139,169-174`). En GCM, una etiqueta que falla borra la salida (`imports/ImportKdf.kt:218-222`). Una contraseña equivocada da siempre el mismo texto, y no se vuelve a intentar sola.
- **Carreras:** R66 y R70 cierran la de bloquear y desbloquear durante la copia previa (CWE-367): la época se compara con el mutex cogido (`vault/VaultStore.kt:698`) y se lee sin ninguna suspensión antes (`ui/screens/ImportScreen.kt:329-331,350-356`).
- **SLIP-0039:** Feistel, GF(256), RS1024 y el *digest* coinciden con la especificación, y los vectores oficiales están en verde (`tools/slip39/Slip39.kt:209-256,339-375`, `Gf256.kt:35-46`, `Rs1024.kt:12-37`, `Shamir.kt:69-86`). La aleatoriedad viene de `secureRandom` (`Slip39.kt:101-109`).
- **Hallazgos:** F-1 (Bajo) es de veracidad y de lógica, no de criptografía; F-2 (Info) es una lectura menos estricta de parámetros que no son secretos. Ninguno se rebaja para cerrar antes.
- **Límites aceptados que se mantienen:** las `String` de lo importado no se pueden borrar (especificación); los parámetros débiles de un archivo ajeno (2FAS con 10 000 iteraciones; Argon2id de Bitwarden desde 1 MiB) solo debilitan ese archivo, no la bóveda, que se guarda con su propio Argon2id.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** el estado de las cinco pantallas nuevas está en su ViewModel, con celdas de Compose, y nunca en estado guardado (`ui/screens/ImportScreen.kt:158-174`, `BackupCheckScreen.kt:87-104`). El guardado de la importación va en `NonCancellable` (`ImportScreen.kt:333,358`), así que salir no lo corta. `ON_STOP` sin giro para «Comprobar una copia» (`BackupCheckScreen.kt:209`). Los selectores de documentos mantienen abierta la bóveda una sola vez (`ImportScreen.kt:484-493`).
- **Concurrencia:** las corrutinas van con `Dispatchers.Default` o `IO`. Un solo cerrojo de derivación, con un orden de cerrojos que evita interbloqueos (`crypto/KdfLock.kt:30-40`). La fila de la huella va en un solo hilo (`android/BiometricVault.kt:183`, `tools/KeyOpsQueue.kt:27-53`). Las lecturas se pueden cancelar entre bloques (`ImportScreen.kt` `StopOnCancel`, `HashLab.kt:64-74`).
- **Rendimiento:** CSV en una pasada (`imports/Csv.kt:36-52`), `hexLower` sin `format` y JSON estimado en una pasada lineal antes de analizar (`imports/ImportJson.kt:98-180`). R69 queda como coste aceptado para la 1.4.1.
- **Plataforma:** el manifiesto no cambia y los permisos son los de la 1.2.2 (`aapt2`). `FLAG_SECURE` y `NoLearningInput` valen para toda la app (`android/SecureWindow.kt:23-28`, `MainActivity.kt:45`). El portapapeles va con `copySensitive` (`android/Clipboard.kt:45`).
- **Compilación:** AGP 9.5.0 con Kotlin integrado (`build.gradle.kts`), R8 en *release* (`app/build.gradle.kts:59-61`), suma del *wrapper* comprobada en CI (`.github/workflows/build-apk.yml:71`) y acciones fijadas por SHA.
- **F-1** es también un defecto de la interfaz: la vista previa y el botón «Importar» deben reflejar lo que se va a guardar de verdad.

## Textos para el Abogado Digital (revisión final)

- **F-1 (pendiente de arreglo):** hoy, «Se reemplaza 1 entrada de la bóveda.» (`importReplacedText`, `imports/ImportTexts.kt:270-271`) puede aparecer sin que se reemplace nada, y `IMPORT_REPLACE_NOTE` (`:280-283`) dice que «Reemplazar» toma «el 2FA», lo que no ocurre cuando ese 2FA está dentro de un acceso. El arreglo propuesto añade quizá una línea fija nueva en la fila del duplicado, por ejemplo «Este código ya está en el acceso de la bóveda; «Reemplazar» no lo cambia.», para revisarla.
- **Lo que cambia sin texto nuevo, desde `e3aa329`** (ya en la lista del informe de los arreglos):
  - un 2FA del archivo cuyo secreto ya está en un acceso sale como duplicado;
  - una parte o una frase pegada con un invisible que no es de formato se lee;
  - un bloqueo durante la copia previa cancela la importación aunque la bóveda se abra otra vez.

  Lo que dice el README (`P-8`, «Importar de otra app») sigue siendo verdad para el tercer punto.
- **F-2** no tiene texto.
