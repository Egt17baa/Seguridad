# Revisión de la tarea 6: fuera CriptoPanel

Rango revisado: `1a65545..ba572c0` (un commit). Leí el diff entero en tres pasadas y no estaba cortado. No he vuelto a pasar las suites. Hice una sola prueba, fuera del repositorio: un `git archive b6ea21b` en el scratchpad para comprobar el riesgo 6 (ver abajo). El árbol queda limpio (`git status --short` da 0 líneas y `git worktree list` solo muestra el repositorio). La rama ya va por `d772651`, que solo añade 2 líneas a `progress.md`.

### Spec Compliance
- ✅ **Cumple el brief.** Lo he comprobado archivo por archivo:
  - `crypto/Crypto.kt`:
    - `Dialect` se queda solo con `SEGURIDAD` (`Crypto.kt:31-38`), como permite el brief.
    - `extractToken` busca solo `SG1:` (`:517-520`).
    - `inspectText` y `decryptText` ya no usan `textDialect` (`:582-594`, `:596-605`).
    - Fuera `KDF_RAW_KEY`, con un comentario que reserva el 2 (`:476-477`; ver el punto 7 de los riesgos).
  - `crypto/FileCrypto.kt:13-14`: fuera `LEGACY_ENCRYPTED_EXTENSION`.
  - `vault/Vault.kt`:
    - `format` es obligatorio (`:118`).
    - Fuera `LEGACY_FORMAT`.
    - `supported` (`:166-167`) y `candidates` (`:170-171`) son idénticos a la rama `FORMAT` de antes.
    - `keyAad`/`dataAad` ya no tienen la versión 1 (`:194`, `:200`).
  - `Backup.kt:60`, `:66`, `VaultStore.kt:766` y `PkActions.kt:64`, `:436`, `:453`, `:479`: solo KDoc. `isSymmetricHeader` reconoce solo `SG` a través de `Dialect.ofMagic` (`PkActions.kt:480-481`).
  - Pantallas:
    - `SecurityToolsScreen.kt:353`, `:408` y `:866`, y fuera `LEGACY_TEXT_PREFIX`.
    - `VaultScreen.kt:293`, `SettingsScreen.kt:475` y `VaultBackup.kt:64`: dicen solo `.sgbak` y la exportación `.json` de la 1.0.
  - README:
    - `:24`.
    - La sección pasa a «Formatos» (`:44-60`), con la nota literal del brief (`:48`).
    - Fuera la línea de la lista de pruebas (antes `:217`).
    - La línea de la prueba de «Importar copia» (antes `:307`).
    - `grep -i "criptopanel\|CP1\|cpenc" README.md` solo da `:48`.
  - Tests del paso 1, todos presentes:
    - `DialectTest` `CriptoPanel data is refused`: los 8 `CP1:` de `texts.json` dan el mismo error que un texto ajeno; `ref.cpenc` da el error de un archivo ajeno del mismo tamaño, con la salida vacía.
    - `LegacyVaultTest` `CriptoPanel data is refused`: `vault-v2.json` y `vault-noversion.json`, como texto y como archivo, dan «No es una bóveda compatible», con `vault.bin`, las entradas y la carpeta iguales byte a byte.
    - `AppNameTest`: `no visible text names CriptoPanel` y `no code reads CriptoPanel`.
  - Datos de prueba:
    - Se borran los tres que ya no usa nadie: `file-aes.cpenc`, `file-cascade-rawkey.cpenc` y `vault-v1.json`.
    - Los cuatro que quedan se usan: `texts.json` y `ref.cpenc` en `DialectTest` (`ref.cpenc` también en `PkActionsTest`), y `vault-v2.json` y `vault-noversion.json` en `LegacyVaultTest`.
    - `seguridad/` y `vectors/` no aparecen en el diff.
  - Cuenta de tests, comprobada sobre el diff: 18 `@Test` quitados y 10 añadidos, de los que 4 son renombrados. Quedan −14 + 6 = −8, que coincide con el informe (1280 a 1272 y 1275 a 1267).
  - Commit:
    - autor `Claude <noreply@anthropic.com>` y mensaje del brief;
    - acaba solo en `Co-Authored-By`, y `grep -cE "Claude-Session|claude\.ai"` da 0;
    - en la rama `ccr-a1a6eabb-765f9g`.
- ⚠️ **No se puede verificar con el diff:** dónde va la «línea de las notas de la versión». El README no tiene sección de notas, y el plan (`plans/2026-11-01-clavero-1.3.1.md:273`) y el diseño (`:117`) no dicen dónde va. El implementador la puso en «Formatos» (`README.md:48`) y lo avisa. Lo decide el controlador.

### Comprobaciones de los riesgos con nombre

1. **Los formatos de Clavero no cambian de comportamiento.** Comprobado.
   - **id 2 de la cabecera.** `git log -G "kdfId *= *2|KDF_RAW_KEY,"` y `git log -G "KDF_RAW_KEY\b" -- app/src/main` solo dan `c70f932` (lo introduce) y `ba572c0` (lo quita).
     - En las 15 revisiones de `Crypto.kt` (`git log --follow`), los únicos constructores de `Header(` en `main` son `parse` y `newHeader`.
     - `newHeader` escribe `kdfId = if (raw) KDF_RAW_KEY_HKDF else KDF_ARGON2ID` con `dialect = Dialect.SEGURIDAD` desde `c70f932:…/Crypto.kt:331-341` hasta hoy (`Crypto.kt:547-556`).
     - `git grep "Header(\|kdfId *=" 1a65545 -- app/src/main` no da ningún otro escritor: `FileCrypto.kt:59` llama a `newHeader`.
   - **`seguridad-vault` versión 1.** Ninguna versión la escribió.
     - En todas las revisiones de `Vault.kt`, desde `07f220e:…/Vault.kt:177-178`, `wrap` escribe `format = VaultFile.FORMAT` y `version = VaultFile.VERSION` (2, y 3 desde `5276b0d`).
     - `write` exige `FORMAT` y la versión 2 (`07f220e:236`), o la 2 o la 3 (hoy `Vault.kt:317`).
     - `supported(FORMAT)` nunca aceptó la 1 (`07f220e:95`).
     - `git log -G "LEGACY_FORMAT"` solo da `07f220e`, `5276b0d` (que solo cambia ramas de lectura) y `ba572c0`.
   - **`.sgbak` con una AAD v1.** No puede existir.
     - La exportación exige `Unlocked` y escribe `encodeFile(vault)` (`VaultStore.kt:725-727`).
     - Para llegar a `Unlocked` hace falta `afterMasterUnlock`, que vuelve a envolver todo lo que no es `FORMAT` 3 (`Vault.kt:355`), o la huella, que rechaza otro formato (`Vault.kt:399`).
     - `backup-1.1.sgbak` lleva `"format":"seguridad-vault","version":2` (comprobado con `unzip -p`), igual que `export-1.0.2.json` y `vault-1.1.json`.
   - Con `FORMAT`, `candidates` y las AAD de las versiones 2, 3 y sin versión dan los mismos bytes que en la base, porque la rama `FORMAT` no cambia (diff, líneas 847-900).
   - `extractToken`, `inspectText` y `decryptText` dan lo mismo con `SG1:`.
   - Los formatos de clave pública no se tocan.
2. **Un dato de CriptoPanel recibe el error genérico.** Comprobado.
   - Un `CP1:` da «El texto cifrado debe empezar por SG1:» (`Crypto.kt:598`). Su cabecera detrás de `SG1:`, o un `.cpenc`, dan `NOT_COMPATIBLE` desde `Header.parse` (`:490`). Una bóveda sin `format` da `NOT_A_VAULT` desde `decodeFile` (`Vault.kt:381-385`).
   - Un `.cpenc` en «Abrir lo recibido» es `PickedFile.Other` (`PkActionsTest`), igual que cualquier archivo ajeno. En Cifrar archivos falla antes de escribir nada, porque `pkKindOfFile` (`FileMessage.kt:315-323`) no lo toma por un archivo de Contactos.
   - `git grep -niE "criptopanel|CP1|cpenc" ba572c0 -- app/src/main` solo da dos comentarios (`Crypto.kt:476`, `Vault.kt:115`), y `kotlinLiterals` no los cuenta.
   - `git grep -il criptopanel`, fuera de los documentos históricos, no da nada en `assets`, `res`, `docs/privacidad.html` ni la ficha. Solo quedan `CLAUDE.md`, la plantilla de PR (las arregla el controlador) y `SKILL.md:107` (un ejemplo de proceso).
3. **Código muerto.**
   - Todo lo que se quita era inalcanzable para los datos propios (ver el riesgo 1).
   - `Dialect` con una entrada está justificado: mantiene las firmas y las etiquetas HKDF.
   - La guarda de formato de `afterMasterUnlock` (`Vault.kt:355`) también. Sin ella, un archivo ajeno llegaría a `write` y saltaría con `IllegalStateException` en vez de `CryptoFailure`.
   - La guarda `version == 1` de `biometricRefusal` (`Vault.kt:399`) no hace daño, pero su motivo nuevo no se sostiene: Minor 4.
4. **Disco.** Ninguna versión publicada pudo dejar una `criptopanel-vault` en `vault.bin` ni en una copia automática:
   - `replace` guarda `checked`, la salida de `afterMasterUnlock`, nunca `incoming` (`VaultStore.kt:803`, `:837`, `:848`);
   - la exportación y la copia automática exigen `Unlocked` (riesgo 1).

   Si existiera una, `load` la apartaría con `keepAside()` (`VaultStore.kt:262-270`). Eso renombra `vault.bin` y `att/` a `.bad-<hora>` y no borra nada. Solo «He olvidado la contraseña» los borra, y es una acción explícita del usuario (`VaultStore.kt:961-963`). Lo acepto.
5. **Textos.**
   - Los textos nuevos y cambiados están en español y no tienen ninguna palabra prohibida: `grep` sobre las líneas `+` del diff.
   - Son verdad sobre el código:
     - «Sirve una copia .sgbak … o una exportación .json de la 1.0.» y «acepta esa copia y la exportación .json de la 1.0.» corresponden a `importReplace`.
     - «(SG1:…)», «empieza por SG1:» y «Elegir archivo .sgenc» corresponden a `decryptText`/`decryptStream`.
     - En el README, «de la 1.1.1» coincide con `seguridad/README.txt`, y lo de «escritos con el código de la 1.3.0» lo comprobé (riesgo 6).
6. **Vectores.**
   - Los vectores fijos de la tarea 4 (`CryptoTest.Kat.answers`) siguen igual: el diff solo cambia su KDoc y borra dos tests. Los sigue usando `fixed known answers…` (`CryptoTest.kt:504-528`).
   - Los vectores de la 1.3.0 los escribe de verdad el código de la 1.3.0. Lo comprobé así:
     - extraje `b6ea21b` con `git archive` en el scratchpad;
     - añadí allí un `VecCheckTest` con los mismos cuatro textos (`diff` de las cadenas: iguales) y el mismo `Counting`;
     - ejecuté `gradle -p jvm-check test --tests '*VecCheckTest*' --offline`, que dio **PASSED**: `encryptText` y `encryptStream` de la 1.3.0 producen `t1`, `t2`, `f1` y `f2` byte a byte.
7. **El id 2 está dentro del alcance.** Es código muerto de CriptoPanel: el brief pide quitar «lo que quede sin uso» y solo CriptoPanel 1.9.0 lo escribió (riesgo 1). Además quita superficie, porque era la clave aleatoria usada tal cual, sin clave por mensaje. Lo fija un test (`DialectTest.kt:154-162`) y el número queda reservado (`Crypto.kt:476-477`). No es un extra arriesgado.

### Strengths
- **Se quita sin tocar un byte de lo propio.** `supported`, `candidates`, `keyAad` y `dataAad` dejan exactamente la rama `FORMAT` de antes, y los vectores de la 1.3.0 lo fijan para `SG1:` y `.sgenc`, cascada y HKDF «seguridad-raw-key» incluidos. Lo verifiqué contra el código de `b6ea21b`.
- **Tests de rechazo con datos reales y con una referencia.** Cada `CP1:` se compara con el error de un texto ajeno, y `ref.cpenc` con un archivo ajeno del mismo tamaño. La importación mira `vault.bin` byte a byte, las entradas y la lista de la carpeta, como texto y como archivo (`LegacyVaultTest.kt:47-80`).
- **El rechazo llega antes de Argon2id:** `decodeFile` para la bóveda y `Header.parse` para los textos y archivos.
- **Informe con evidencia histórica real** (secciones 1 y 3), y he podido reproducirla.
- **Lo que mezclaba los dos formatos se separa bien:** `seguridadVaultWithoutFormatKeyFails`, `a vault file without a version key still opens` y `unknownFormatIsRefused` con una bóveda propia.

### Issues
#### Critical (Must Fix)
Ninguno.

#### Important (Should Fix)
Ninguno.

#### Minor (Nice to Have)
1. **`Vault.kt:246`: un comentario que ya no es verdad.**
   - **Qué pasa:** `format = VaultFile.FORMAT, // Not the default, so it is always written out.` Desde esta tarea `format` no tiene valor por defecto (`:118`), y se escribe siempre porque es obligatorio.
   - **Por qué importa:** el paso 4 del brief pide quitar «las menciones que ya no son verdad».
   - **Arreglo:** `// Required (no default), so it is always written out.`
2. **`Vault.kt:332`: una línea de 166 caracteres.**
   - **Qué pasa:** al rehacer el KDoc de `afterMasterUnlock` quedó una línea de 166 caracteres («…wrapped again with the cost asked, same data key, revision + 1. That happens at any unlock with the master password, on purpose: after an import of a copy made»). El resto del bloque no pasa de 120.
   - **Arreglo:** volver a partir el párrafo.
3. **`Vault.kt:122-124`: el KDoc de `version` se contradice.**
   - **Qué pasa:** dice «A [FORMAT] file always says its version … One that does not ([VERSION_UNKNOWN]) is tried as both». Antes lo explicaba `LEGACY_FORMAT`. Ahora ninguna versión de la app escribió una `seguridad-vault` sin versión (`wrap`, desde `07f220e:178`).
   - **Arreglo:** «This app always writes it; a file without it (edited by hand) is tried as both…».
4. **`Vault.kt:399` (KDoc en `:388-390`): la guarda `file.version == 1` es arbitraria.**
   - **Qué pasa:** el motivo nuevo («a version this format never had») vale igual para la 4, la 5 y cualquier otra. Esas siguen hasta `opened` → `read` (`VaultStore.kt:459`, `:346`), fallan como `CryptoFailure` y, por la regla de la tarea 3, desactivan la huella. Solo la 1 da `MASTER_DUE`.
   - **Por qué importa:** es coherencia y mantenimiento. Con un `vault.bin` manipulado, la maestra tampoco abre, así que no se pierden datos.
   - **Arreglo:** poner `!supported(file) -> BiometricRefusal.MASTER_DUE`. Cubre otro formato y cualquier versión desconocida; `VERSION_UNKNOWN` se sigue permitiendo porque llega ya resuelto. Añadir a `VaultV3Test` el caso de la versión 4. Otra opción es dejar el código y decir en el KDoc que la 1 se conserva solo por el test `VaultV3Test.kt:676`.
5. **`DialectTest.kt:24-25`: la procedencia del archivo no está clara.**
   - **Qué pasa:** el KDoc dice «one real file written by CriptoPanel 1.10.0». El `generator` de `texts.json` (CriptoPanel 1.10.0, `bac076a`) solo cubre sus textos y sus `files`, y esos `files` son los dos `.cpenc` borrados en esta tarea. `ref.cpenc` no está en esa lista, y el test que se borra lo llamaba «written by an independent implementation» (`1a65545:…/CryptoTest.kt:246`). Además, `texts.json` nombra ahora dos archivos que ya no existen.
   - **Arreglo:** «the `CP1:` texts of `criptopanel/texts.json` (CriptoPanel 1.10.0; its `files` list names two `.cpenc` removed in 1.3.1) and `criptopanel/ref.cpenc`».
6. **Se pierde la única comprobación independiente del contenedor (`CryptoTest`, tests borrados `decrypts texts/a file written by an independent implementation`).**
   - **Qué pasa:** eran lo único que contrastaba la cabecera, Argon2id desde la cabecera, STREAM y el registro del nombre con un código distinto del de la app. Los vectores nuevos (`DialectTest.kt:73-113`) son de regresión: detectan un cambio, pero no un error simétrico que esté desde el principio.
   - **Arreglo:** añadir un texto `SG1:` (AES-256-GCM, con contraseña) y un `.sgenc` (ChaCha20-Poly1305) escritos de forma independiente, con `cryptography` y `argon2-cffi` de Python, como se hizo con `Kat` en la tarea 4. Hay que comprobar que la app los abre.
7. **`AppNameTest.kt:103`: `no code reads CriptoPanel` solo compara literales enteros.**
   - **Qué pasa:** un texto como «Elegir archivo .sgenc o .cpenc» o «empieza por SG1: o CP1:», justo los que quita esta tarea, pasaría los dos tests nuevos.
   - **Contexto:** cumple la redacción del brief; esto es ampliar la cobertura.
   - **Arreglo:** `"CP1:" in lit || ".cpenc" in lit` además del conjunto exacto. Hoy no hay ningún literal así en `app/src/main`, así que seguiría en verde.

### Assessment
**Task quality:** Needs fixes
**Reasoning:** la tarea es correcta y el comportamiento está verificado. Los formatos propios no cambian (lo comprobé contra el código de la 1.3.0), y lo de CriptoPanel se rechaza con el error genérico, sin tocar el disco. Solo hay 7 detalles menores de comentarios, coherencia y cobertura, que el proyecto arregla todos.

### Revisión experto-seguridad-cripto
- **Modelo de amenaza.** Lo que se quita es superficie de lectura de datos que pueden venir de fuera, como un texto pegado, un archivo recibido o una copia importada. No queda ningún camino nuevo.
  - El id 2 usaba la clave aleatoria directamente como clave AEAD, sin HKDF por mensaje. Ahora `Header.parse` lo rechaza antes de tocar el secreto (`Crypto.kt:496`), y `keyForHeader` solo deriva con HKDF y la sal de la cabecera (`Crypto.kt:433-438`).
  - La AAD v1 de la bóveda no autenticaba los campos ni la fecha. Desaparece, y las AAD de las versiones 2 y 3 siguen ligando formato, versión, coste, sal, revisión y fecha (`Vault.kt:160-163`, `:194`, `:200`).
- **Sin degradación.** Una `seguridad-vault` con `version = 1` sigue rechazada al abrir (`Vault.kt:166-167`, `VaultTamperTest` `changing the cost, salt or version breaks the unlock`), y con la huella pide la maestra (`Vault.kt:399`).
- **Rechazo barato y sin oráculo nuevo.**
  - Una cabecera sin `SG` falla en `Header.parse` (`Crypto.kt:488-490`) y una bóveda sin `format` en `decodeFile` (`Vault.kt:381-385`). Ninguna llega a Argon2id.
  - Los mensajes son los mismos que para cualquier dato ajeno.
  - `decryptStream` no escribe nada antes de leer la cabecera (el test mira `out.size() == 0`).
- **Formatos que se leen siempre.**
  - En toda la historia, ningún escritor de «Seguridad» o Clavero emitió el id 2 ni una `seguridad-vault` v1 (riesgo 1, con `git log -G` y las 15 revisiones de cada archivo).
  - Los vectores de la 1.3.0 los produce el código de `b6ea21b` (prueba en el scratchpad).
  - `seguridad/` y `vectors/` no se tocan.
- **Secretos en memoria.** Sin cambios. La rama quitada (`secret.bytes.copyOf()`) era una copia que el llamante ya borraba. Los vectores de la tarea 4 (`CryptoTest.kt:504-528`) siguen pasando.
- **Red, permisos, logs y `FLAG_SECURE`:** el diff no los toca.
- **Falta (Minor 6):** una comprobación independiente del contenedor `SG1:`/`.sgenc`, para que no dependa solo de los vectores de regresión.

### Revisión Ingeniero Móvil Senior
- **Serialización.** `VaultFile.format` sin valor por defecto (`Vault.kt:118`):
  - kotlinx.serialization lanza `MissingFieldException`. Con `"format": null` también falla, porque `coerceInputValues` (`core/Json.kt:8`) solo actúa si hay valor por defecto.
  - `decodeFile` atrapa cualquier `Exception` y la convierte en `NOT_A_VAULT` (`Vault.kt:380-385`).
  - Con `encodeDefaults = false`, el JSON que se escribe tiene los mismos bytes: `format` ya era distinto del valor por defecto y se escribía siempre.
  - El único constructor en `main` es `wrap` (`Vault.kt:245`).
- **Arranque.** `load()` aparta un `vault.bin` que no se puede leer con `keepAside()` (`VaultStore.kt:247-249`, `:262-270`). No borra nada, y ningún escritor pudo dejar uno sin `format` (riesgo 4).
- **Compose.** Solo cambian cinco cadenas (`SecurityToolsScreen.kt:353`, `:408`, `:866`, `VaultScreen.kt:293` y `SettingsScreen.kt:475`) y un `import`/`const` sin uso. No hay estado, recomposición ni diseño nuevos, y nada nuevo en `rememberSaveable`.
- **Concurrencia.** La importación falla en `decodeFile`, dentro de `compute` y antes del `mutex` (`VaultStore.kt:757-760`). No toca `beforeReplace`, los ajustes de la copia automática ni el `epoch`.
- **Lint y compilación.** No hay referencias que queden colgando en `config/`, en las *baselines* ni en ProGuard (`grep` vacío). Solo hay una línea larga de KDoc (Minor 2).
- **Cohesión.** Mantener `Dialect` y `Header.dialect` evita cambiar firmas como `aead(…, dialect)` y `cascadeKey`, y la llamada de `PasswordBytesTest`. Es razonable.

### Textos para el Abogado Digital (revisión final)
Interfaz (cambian; siguen en línea, como estaban):
1. `ui/screens/VaultScreen.kt:293`, «Crea tu bóveda»: «Sirve una copia .sgbak de esta app (con los adjuntos) o una exportación .json de la 1.0.»
2. `ui/screens/SettingsScreen.kt:475`, Ajustes: `«Exportar bóveda» guarda una copia .sgbak con los adjuntos. «Importar copia» acepta esa copia y la exportación .json de la 1.0.`
3. `ui/screens/SecurityToolsScreen.kt:353`, Cifrar texto: «Ese archivo no contiene un texto cifrado (SG1:…).»
4. `ui/screens/SecurityToolsScreen.kt:408`, etiqueta: «Texto cifrado (empieza por SG1:)»
5. `ui/screens/SecurityToolsScreen.kt:866`, botón: «Elegir archivo .sgenc»

README:
- `README.md:24`: «Se siguen leyendo las exportaciones JSON de la 1.0.x.»
- `README.md:44-48`: la sección «Formatos», con «Clavero escribe y lee sus propios formatos, que conservan los nombres de cuando se llamaba «Seguridad»: …» y la nota «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`, `.cpenc` y su bóveda).» (su sitio está pendiente: ⚠️).
- `README.md:53`: «JSON de la 1.0.x: se siguen importando. …»
- `README.md:60`: «Estos formatos se leerán siempre: hay tests con datos reales escritos por versiones anteriores (la exportación JSON de la 1.0.2, la bóveda y la copia `.sgbak` de la 1.1.1, y textos `SG1:` y archivos `.sgenc` escritos con el código de la 1.3.0).»
- `README.md:305`, la prueba de la 1.3.0 «Importar copia» en la lista: «Un JSON exportado de la 1.0 sigue pidiendo la contraseña.»
- Fuera la línea de la lista de pruebas sobre CriptoPanel.
