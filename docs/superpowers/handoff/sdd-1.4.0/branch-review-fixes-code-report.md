# Clavero 1.4.0: arreglos de la revisión de la rama (parte de código)

Rama `ccr-d1e75160-p5f4wc`, sobre `93b3f71` (que solo añade `.maestro/` y una línea del registro: no se han tocado).
Hallazgos de las tres revisiones (`branch-review-a-imports.md`, `branch-review-b-tools-vault.md`,
`branch-review-c-build-docs.md`) que tocan código, y las decisiones R37 y R38 del registro. La parte de documentos
(README, `CLAUDE.md`, diseño, plan, auditoría, traspaso y la lista del Abogado) va después, en otra tarea.

Método: TDD. Los tests nuevos de comportamiento se ejecutaron primero contra el código de antes, en un *worktree*
aparte en `857c687` (`./gradlew -p jvm-check test --tests …`), y fallaron todos por el motivo esperado (abajo, «Rojo
primero»). Los que solo fijan algo que ya era así (R37, el prefijo de `AppNameTest`, la ficha de Play) se dicen como
fijaciones.

## Puerta

Las dos, con el wrapper (R29), en el árbol final:

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`:
  **BUILD SUCCESSFUL**, 1 803 tests (1 801 en el primer commit, `2ddc305`; +2 de R40), 0 fallos, 0 omitidos; lint 0 errores y 19 avisos (los conocidos: comprobaciones de
  versiones en línea); **0 líneas `w:`**.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, 1 798 tests (1 796 en `2ddc305`; +2 de R40), 0 fallos, 0 omitidos;
  **0 líneas `w:`**.

Antes de esta ronda: 1 789 y 1 784. La primera pasada de `jvm-check` sacó seis avisos `w:` en tests que no son de esta
ronda (`ImportFlowTest.kt:117`, `:120` y `VaultStoreImportTest.kt:204-209`, `runCurrent` sin `@OptIn` de
`ExperimentalCoroutinesApi`; `HistoryTest.kt:72`, un `Json { }` creado dentro del test). Arreglados: `@OptIn` en las dos
clases y el `Json` en una propiedad. Y la primera pasada de `app` sacó cuatro de mi `repoRoot()` (`File.parentFile`
anulable): ahora con `checkNotNull` y `canonicalFile`.

## Rojo primero

Contra el código de antes (`857c687`), con los tests nuevos:

- A M-2: `MergePlanTest.notesThatDifferOnlyByTheSpacesAroundThemAreNotAppended` y
  `appendedNotesAreTrimmedAndRecognisedAgain` fallaban: «Reemplazar» dejaba «clave 123\n\nImportado el 4-11-2026:\nclave 123\n».
- A M-3: los nueve casos nuevos de `BitwardenCryptoTest`, `AegisCryptoTest`, `TwoFasCryptoTest` y los tres de
  `*JsonTest` fallaban con `NeedsPassword` donde se espera `Failed(IMPORT_KDF_TOO_BIG | IMPORT_BROKEN | IMPORT_ENCRYPTED_TOO_BIG)`.
- B I-1: `PlatformSourcesTest.I-1 the labs and the other secret fields use the keyboard that does not learn` fallaba.
- R38: `BackupCheckTest.aCancelledCheckMakesTheUnlockWait` fallaba: el desbloqueo con la maestra corría a la vez que
  la derivación de la comprobación cancelada. Y la fijación nueva de `ImportScreen` en `PlatformSourcesTest`.
- Las API nuevas (`duplicateSubline`, `HashLabSession.startFile/sized/progress`, `KdfLock`, `passwordFieldTakes`) no
  existían: sus tests no compilaban (rojo por compilación), así que no entraron en esa pasada.

Una lección de esa pasada: un test que falla con el cerrojo de derivación cogido deja colgadas las derivaciones de los
tests siguientes (en la pasada roja, dos tests de `BackupCheckTest` fallaron por eso). Los tests nuevos sueltan siempre
el `CountDownLatch` en un `finally`.

## Revisión A (importar)

### M-2. «Reemplazar» compara las notas recortadas

- `imports/MergePlan.kt:250-255`: las notas son iguales si lo son con `trim()`, o si las de la bóveda (sin espacios al
  final) acaban en «\n» + las del archivo recortadas; al añadir, se añaden recortadas. Es la misma regla que el emparejado
  de duplicados (`Matcher`, `e.notes.trim()`). KDoc de `replaceWith` al día (`:228-236`).
- `IMPORT_REPLACE_NOTE` («suma sus notas») sigue siendo verdad: se suman salvo cuando son las mismas.
- Tests: `MergePlanTest.notesThatDifferOnlyByTheSpacesAroundThemAreNotAppended` (una NOTE «x» / «x\n», con tabuladores
  y `\r\n`, un LOGIN «a» / « a », y por el plan entero: el duplicado sale y sus notas quedan como estaban) y
  `appendedNotesAreTrimmedAndRecognisedAgain` (lo añadido se reconoce al reemplazar otra vez, también con espacios al
  final de las notas de la bóveda). `replacingTwiceWithTheSameFileChangesNothingTheSecondTime` sigue en verde.

### M-3. La cabecera de una exportación cifrada se comprueba antes de pedir la contraseña

- `imports/BitwardenJson.kt:37-41`: sin contraseña, `bitwardenProtected(root)` antes de `NeedsPassword`.
- `imports/ImportJson.kt:215` (Aegis, `aegisEncrypted(root)`) y `:224` (2FAS, `twoFasEncrypted(root)`); KDoc `:184-195`.
- Son las mismas funciones que corren antes de derivar con la contraseña: comprueban los topes de la KDF
  (`checkPbkdf2Iterations`, `bitwardenArgon2Params`, `checkScrypt` de todas las ranuras), las longitudes de cada parte y
  el tope de lo cifrado antes de decodificarlo. No derivan nada. Con contraseña, el camino no cambia (se vuelven a
  comprobar dentro de `open*`, como antes), así que las KDF siguen antes de cualquier derivación.
- Lo que cuesta: sin contraseña, la Base64 de lo cifrado (hasta 8 MiB, no secreto) se decodifica una vez más.
- Tests: en `BitwardenCryptoTest.kdfOutOfRangeIsRefusedBeforeWork`, `brokenHeadersAreRefusedBeforeWork` y
  `anEncryptedPartOverTheBoundIsRefusedBeforeDecoding`; `AegisCryptoTest.kdfOutOfRangeIsRefusedBeforeWork`,
  `everySlotIsCheckedBeforeAnyWork`, `brokenHeadersAreRefused`, `anEncryptedPartOverTheBoundIsRefusedBeforeDecoding`;
  `TwoFasCryptoTest.brokenFieldsAreRefusedBeforeWork` y `anEncryptedPartOverTheBoundIsRefusedBeforeDecoding`: cada caso
  también con `password = null`. Las tres pruebas de detección de `BitwardenJsonTest`, `AegisJsonTest` y
  `TwoFasJsonTest`, que usaban cabeceras inventadas e inválidas para comprobar `NeedsPassword`, ahora comprueban que esas
  cabeceras dan `IMPORT_BROKEN` sin pedir nada (`encrypted…DoesNotAskForAPassword`); el `NeedsPassword` lo siguen
  comprobando los archivos de prueba reales (`withoutAPasswordItAsksForOne` de los tres `*CryptoTest`).

### M-4. La fila de un duplicado enseña el usuario y la web que trae «Reemplazar»

- `imports/MergePlan.kt:391-406`, `duplicateSubline(entry)`: en un LOGIN, el usuario y el host de la URL del archivo
  leído con `urlHost` (el de `parseLink`, como un navegador: RT-1), unidos por « · »; en un TOTP, emisor y cuenta; todo
  por `plainReportText`. `ui/screens/ImportScreen.kt:662-675` (`DuplicateRow`) lo usa.
- Sin literal nuevo: el separador « · » es el que ya usaban la misma fila para el TOTP, `OtpQrScreen`, `VaultEntryScreen`
  y `VaultScreen`.
- **La web de la bóveda no se enseña.** Para que se entienda hacen falta palabras («en la bóveda: …» o «(la bóveda no
  tiene web)»), y la tarea pide no añadir literales; dos hosts sin etiqueta confundirían más que ayudarían. El caso de la
  revisión (la bóveda sin web y el archivo con la del atacante) queda cubierto: la fila dice «ana · banco-acceso.example»
  y antes decía solo «ana». Si el controlador quiere el lado de la bóveda, es un texto nuevo para el Abogado.
- Test: `MergePlanTest.aDuplicateLoginShowsTheUserAndTheHostReplaceBringsIn` (host con ruta, `\@` de RT-1, `www.`, sin
  esquema, sin web, vacío, invisibles y RLO, TOTP y NOTE). `EntryType` ya no se importa en `ImportScreen.kt`.

### M-5. KDoc al día

- `planMerge` (`imports/MergePlan.kt:68-82`): `compareKey` (texto mostrado, NFC, espacios juntos, título sin mayúsculas
  y usuario con ellas, R34/R36, RT-2) y el host de `parseLink` (RT-1).
- `planImport` (`:101-112`): lo usan los tests; la pantalla hace lo mismo en dos pasos (`importSnapshot()` y
  `planMerge` en `Default`) porque guarda las entradas de la instantánea para la estimación.
- `VaultStore.importSnapshot` (`vault/VaultStore.kt:661-668`): igual.
- La nota de corrección del diseño (R34/R36) es de la parte de documentos.

### R37. Un bloqueo entre la copia y el guardado da `Stale` y no guarda nada

- Test (fijación, el comportamiento ya era este): `AutoBackupImportCopyTest.aLockBetweenTheCopyAndTheSaveSavesNothing`.
  Plan desde `importSnapshot`, copia de antes de importar hecha, `lock()`, `applyImport` → `ApplyResult.Stale`; el
  archivo de la bóveda byte a byte igual, la revisión igual, la copia sigue en la carpeta y en la lista
  (`importCopies`), y al abrir, la bóveda solo tiene lo de antes. `VaultStoreImportTest.applyAfterALockIsStaleToo` ya
  cubría el caso sin copia.
- Comentarios corregidos: `imports/ImportSession.kt:29-34` (`ImportStep.Saving`), `:399-400` (`forget`) y
  `ui/screens/ImportScreen.kt:166-171`; también el de `PlatformSourcesTest` que decía «which a back or a lock does not cut».

## Revisión B (herramientas y bóveda)

### I-1. Teclado que no aprende en los dos laboratorios

- `ui/theme/Components.kt:173-179`: `SECRET_TEXT_KEYBOARD = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)`,
  el mismo de «Cifrar texto» (`SecurityToolsScreen.kt:398`) y el que «Repartir un secreto» tenía como `SECRET_KEYBOARD`
  privado (ahora usa el compartido, `SecretShareScreen.kt:320`, `:381`).
- Sin `visualTransformation`: el tipo `Password` quita sugerencias y aprendizaje, pero no oculta el texto, que es lo que
  hace «Cifrar texto» (el texto se ve) y lo que necesita un laboratorio. La clave del HMAC sigue en `PasswordField`, oculta.
- Laboratorio de hashes: el texto (`HashLabScreen.kt:295`), el mensaje del HMAC (`:346`), la entrada de Base64/hex
  (`:374`) y la huella esperada (`:451`; la sesión también la trata como secreto, `HashLabSession`).
- Laboratorio clásico: los seis campos (`ClassicLabScreen.kt:178`, `:199`, `:237` —`TextInput`: César, Vigenère, Enigma
  y análisis—, `:290`, `:299`, `:307`): desplazamiento, clave de Vigenère, anillos, posiciones y clavijas. La sesión
  borra todos (`ClassicLabSession.forget`).
- `HASH_LAB_HMAC_INTRO` y `CLASSIC_NOT_SAVED` no cambian: con el teclado, vuelven a ser verdad.
- Test: `PlatformSourcesTest.I-1 the labs and the other secret fields use the keyboard that does not learn`: la
  definición exacta; cada `OutlinedTextField(` de los dos laboratorios (4 y 6, contados) lleva
  `keyboardOptions = SECRET_TEXT_KEYBOARD,` y ninguno `visualTransformation`; la clave del HMAC es `PasswordField`;
  «Repartir un secreto» usa el compartido dos veces; «Cifrar texto» sigue con el suyo.

### M-1. KDoc de `HashLabSession`

- `tools/hashlab/HashLabSession.kt:10-17`: «la pestaña y el algoritmo se quedan; el archivo elegido se va con lo demás,
  con su resultado, su tamaño, lo leído y el algoritmo de ese resultado». Es lo que hace `forget()`.

### M-2. Fuera `sha256Hex(InputStream)` y `sameHash`

- `crypto/Hashes.kt`: borradas (y el `import java.io.InputStream`). Búsqueda en `app` y `jvm-check`: solo las usaba
  `GeneratorAndChecksTest` (el `sha256Hex(vararg)` de `IdentityTest` es una función privada del test, otra cosa).
- `crypto/GeneratorAndChecksTest.kt`: fuera sus dos tests (`sha256 of abc and hash comparison`, `COV1-2 sha256 of a stream
  stops at the caller's checkpoint`); los cubren `HashLabTest` (resúmenes por partes con cancelación, `parseExpected` y la
  comparación en tiempo constante).

### M-3. Un solo tope de contraseña pegada

- Lo que aceptan hoy: crear la bóveda, cambiar la maestra, desbloquear e «Importar copia» **no tienen tope** (`PasswordField`
  sin límite; `newMasterProblem` tampoco). «Comprobar una copia» tenía 1024 y la contraseña de una exportación en
  «Importar de otra app», otro 1024 propio.
- Decisión: `vault/MasterPolicy.kt:23-34`, `PASSWORD_FIELD_MAX_CHARS = 65_536` (unidades UTF-16) y
  `passwordFieldTakes(value)`, que usan los dos campos (`BackupCheckScreen.kt:138`, `ImportScreen.kt:227`); fuera
  `MAX_PASSWORD_CHARS` y `MAX_FILE_PASSWORD_CHARS`. El texto de rechazo no cambia (`BACKUP_CHECK_TOO_LONG`,
  `IMPORT_PASSWORD_TOO_LONG`).
- Por qué así y no un tope nuevo en crear y cambiar la maestra: un tope allí necesita un texto nuevo y cambia una
  pantalla que la revisión no señala; un tope en desbloquear podría dejar fuera una bóveda que ya existe. 64 Ki
  unidades es una cadena de 128 KiB, que el campo y la memoria aguantan sin problema (Argon2id, PBKDF2 y scrypt resumen la
  contraseña, así que su longitud no cuesta memoria en la derivación), y muy por encima de lo que una persona escribe o
  pega de un gestor (los generadores dan unas decenas o cientos de caracteres). Con eso «Comprobar una copia» acepta en
  la práctica toda maestra que acepta «Importar copia», que es lo que la pantalla dice de sí misma. Lo que queda: una
  maestra de más de 65 536 unidades, creada pegando un documento, se importa pero no se comprueba; se dice aquí y no es
  realista.
- Test: `MasterPolicyTest.thePasswordFieldsShareOneCapFarPastAnyMaster` (1 500 caracteres pasan, el límite justo pasa,
  uno más no; un emoji cuenta dos; las dos pantallas usan la función y ninguna guarda un tope suyo).

### M-4 / R38. Un cerrojo de derivación para todo el proceso

- `crypto/KdfLock.kt` (nuevo): `object KdfLock` con un `Mutex` (justo: FIFO) y
  `suspend fun <T> derive(context, block): T = mutex.withLock { withContext(NonCancellable) { withContext(context) { block() } } }`.
  - Quien espera el cerrojo y se cancela no deriva nada (la espera de `Mutex` se puede cancelar).
  - Una vez empezada, la derivación llega al final con el cerrojo cogido aunque se cancele quien la llamó (Bouncy Castle no
    se puede interrumpir), y lo que da vuelve a quien la llamó, así que una clave derivada siempre llega al código que la
    borra (BOV-3, el mismo patrón que el `derive` que tenía `VaultStore`).
  - Esperar suspende: no bloquea ningún hilo, tampoco el principal. El bloque corre en el `context` que se le da
    (`compute`, que es `Dispatchers.Default` en la app, o `Dispatchers.Default`).
- Quién lo toma:
  - `VaultStore` (`vault/VaultStore.kt:314-321`): `derive` pasa por `KdfLock`, y lo usan ahora **todas** sus Argon2id:
    crear (`:411`), desbloquear y volver a envolver (`:441`, `:443`), confirmar la maestra y volver a envolver (`:526`,
    `:533`), cambiar la maestra (`:801`) e «Importar copia» (`:912`, `:915`). Antes, crear, desbloquear y confirmar iban
    con `withContext(compute)` sin más (dentro de `NonCancellable`, así que no cambia nada para ellos salvo la espera).
  - «Comprobar una copia» (`vault/BackupCheck.kt:85`): sustituye su `derivations` propio, que solo ordenaba una
    comprobación con otra.
  - «Importar de otra app» (`ui/screens/ImportScreen.kt:284-303`): con contraseña, toda la lectura (`readImport`, con su
    Argon2id, scrypt o PBKDF2) va bajo el cerrojo; sin contraseña no se deriva nada y no espera. Los lectores de
    `imports/ImportKdf.kt` son funciones puras y síncronas; el cerrojo va donde se llaman (KDoc `ImportKdf.kt:35-36`).
    Un resultado que llega después de «Cancelar», de un borrado o de otro archivo lleva un `run` viejo y
    `ImportSession.onRead` lo tira, como antes.
  - «Cifrar texto» (`ui/screens/SecurityToolsScreen.kt:280-283`): su Argon2id con contraseña (y el cifrado de un texto
    corto). No lo pedía la revisión, pero es el mismo fallo y el arreglo cabe en una línea.
- PBKDF2: el de «Importar de otra app» entra porque va dentro de la misma lectura; no ocupa memoria (unos cientos de
  bytes), pero sacarlo partiría la lectura en dos. El de SLIP-0039 queda fuera por eso mismo: no ocupa memoria.
- «Cifrar archivo» (decisión R40 del controlador, en un segundo commit): solo la derivación de la clave toma el cerrojo,
  nunca el cifrado del flujo, así que un desbloqueo no espera nunca lo que tarde un archivo entero.
  - `crypto/FileCrypto.kt:21-30`, `fileKey`: con contraseña, `KdfLock.deriveBlocking { keyForHeader(…) }`; con una clave
    aleatoria (HKDF, sin memoria), `keyForHeader` sin esperar. Lo usan `encryptStream` (`:69`) y `decryptStream`
    (`:111`) en el mismo sitio donde antes llamaban a `keyForHeader`: antes de escribir o leer nada del flujo.
  - El cerrojo no va dentro de `keyForHeader`, porque también lo usan `encryptText`/`decryptText`, que «Cifrar texto»
    ya llama bajo `KdfLock` (no es reentrante: sería un interbloqueo).
  - `crypto/KdfLock.kt:55-68`, `deriveBlocking`: para código que no suspende y ya va fuera del hilo principal
    (`FileCrypto` corre en `Dispatchers.IO`): `runBlocking { derive(EmptyCoroutineContext, block) }`, en el hilo de
    quien llama. Esperar no se puede cancelar (como la derivación). En el hilo principal de Android lanza
    `IllegalStateException`. La comprobación es por reflexión (`android.os.Looper`, `:71-81`) para que `crypto/` siga
    compilando en `jvm-check`; allí y con los *stubs* de los tests de Android da `false`. En los tests se le pasa la
    comprobación (`deriveBlocking({ true }) { … }`).
  - Formato: ningún cambio, el `.sgenc` es el mismo byte a byte (mismo `keyForHeader`, misma cabecera, mismo orden de
    escritura). Siguen en verde los tests de formato con datos anteriores: `CryptoTest` (los `.sgenc` hechos a mano como
    una versión antigua, nombres de 1 024 bytes), `PasswordBytesTest` (lo que abría Bouncy Castle 1.80),
    `StreamingMemoryTest`, `SeedCryptoTest` y `RedTeamCryptoTest`.
  - Comportamiento: el mismo, salvo la espera. Una operación cancelada mientras espera deriva igual cuando le toca (ya era
    así: la derivación no se podía cortar).
- Orden de los cerrojos, documentado en `KdfLock`: el `mutex` de `VaultStore` y después `KdfLock`; «Comprobar una
  copia», «Importar de otra app», «Cifrar texto» y «Cifrar archivo» solo toman `KdfLock`, y sus bloques nunca llaman a la bóveda. Un bloque
  de `derive` es cálculo puro: no suspende ni llama a la bóveda, a los ajustes, a `KeyOpsQueue` (la fila de la huella,
  que no toma ninguno de los dos y cuyas operaciones no llaman a `VaultStore`: `BiometricVault.kt:168`, `:376`, `:540`)
  ni a otro `derive` (no es reentrante). El *ticket* `IMPORT_KDF_RUN` de importar no espera nunca (`SingleRun.start()`
  devuelve `null`) y se toma antes. Sin ciclos, no hay interbloqueo.
- Cancelación en `VaultStore`: `changeMaster` (si se cancela mientras espera, su `finally` borra la copia de la clave),
  y `replace` (el `catch` de la segunda derivación borra la clave). Desbloquear, crear y confirmar ya van en
  `NonCancellable`.
- Tests:
  - `crypto/KdfLockTest.kt`: `twoDerivationsAtOnceNeverOverlap` (seis a la vez, como mucho una dentro);
    `aCancelledDerivationKeepsTheLockUntilItEnds` (la segunda espera hasta que acaba la cancelada, y lo que da la
    cancelada le llega); `aCallerCancelledWhileItWaitsNeverDerives`; `anExceptionInTheBlockReleasesTheLock`; y de R40,
    `theBlockingEntryRefusesTheMainThread` y `aFileDerivationAndAnotherDerivationNeverOverlap` (con el cerrojo cogido
    por otra derivación, cifrar y descifrar un archivo con contraseña esperan sin escribir nada; con clave aleatoria no
    esperan; al soltarlo, los dos terminan y el archivo se abre).
  - `vault/BackupCheckTest.aCancelledCheckMakesTheUnlockWait`: el caso de la revisión entre componentes: comprobación
    cancelada a mitad de su Argon2id y `VaultStore.unlock` con la maestra, que espera (sigue bloqueada) hasta que acaba;
    la clave de la comprobación queda a cero. `afterCancelTheNextDerivationWaits` (dos comprobaciones) sigue en verde.
  - `PlatformSourcesTest` fija `KdfLock.derive(Dispatchers.Default, read)` en `ImportScreen.kt`.

## Revisión C (compilación y documentos, partes de código)

### M-1. El borrado del Laboratorio de hashes y la línea larga

- El tamaño, lo leído y el algoritmo del archivo pasan del ViewModel a la sesión, para que el borrado los ponga a cero y
  se pueda probar en la JVM: `tools/hashlab/HashLabSession.kt:45-96` (`algo`, `fileAlgo`, `readBytes`, `totalBytes`,
  `startFile()`, `sized(run, …)`, `progress(run, …)`; `forget()` pone `readBytes = 0`, `totalBytes = -1` y
  `fileAlgo = algo`). `file.clear()` sube la ejecución, así que una lectura que siga después del borrado ya no puede
  escribir su progreso ni su tamaño (`FileHashSession.isCurrent`, `tools/cipher/CipherSessions.kt:173-174`, con `run`
  `@Volatile` porque el progreso se escribe desde el hilo de la lectura).
- `ui/screens/HashLabScreen.kt:130-230`: el ViewModel delega (`session.startFile()`, `session.sized`,
  `session.progress`); su `run` ya no es `@Volatile` (solo lo usa el hilo principal para «Cancelar») y fuera
  `mutableLongStateOf`.
- La línea de 156 caracteres del KDoc (`HashLabSession.kt:12`) está partida.
- Test: `tools/hashlab/HashLabSessionTest.kt` (`aWipeInTheMiddleOfAReadingLeavesNothingOfTheFile`,
  `aNewFileStartsFromZeroAndOnlyItsRunWrites`). La fijación de `PlatformSourcesTest` (COV1-2 del laboratorio) que
  buscaba `if (run == mine && n - shown >= PROGRESS_STEP)` busca ahora `session.progress(mine, n)`,
  `session.sized(mine, size)` y `val mine = session.startFile()`.

### M-4. `AppNameTest` fija `seguridad-antes-de-importar-`

- `tools/AppNameTest.kt:132-135`: `IMPORT_COPY_PREFIX` y, de paso, `backupFileName` (`seguridad-boveda-20261026.sgbak`),
  que `CLAUDE.md` daba como fijado aquí. El prefijo no cambia. (`CLAUDE.md:35` es de la parte de documentos.)

### M-5. Nombres de otras apps fuera de la ficha de Play y de sus «Novedades»

- `imports/ImportTextsTest.thePlayListingAndItsNovedadesNameNoOtherProduct`: con la misma lista `names` y la misma
  búsqueda `namesIn` (subcadena, cualquier caja) recorre, línea a línea, los textos de la tienda de
  `docs/ficha-google-play.md` (desde «## Nombre de la app» hasta el final, así que una sección «Novedades» que se añada
  ahí también cuenta) y el bloque de código que sigue a «Google Play, «Novedades»» en cada traspaso
  `docs/superpowers/handoff/*-traspaso.md` (hoy, el de la 1.4.0).
- Única excepción, exacta (`LISTING_ALLOWED`, con el comentario que remite a R30): «• Importa las cuentas exportadas
  desde Google Authenticator.». El test comprueba también que esa línea sigue ahí (si se quita, sale de la lista) y que
  una línea nueva con un nombre se detecta. La cabecera «# Ficha de Google Play (borrador)» no es texto de la tienda y
  queda fuera.

### M-6. Los documentos que leen los tests son entradas de Gradle

- `jvm-check/build.gradle.kts:51-68` y `app/build.gradle.kts:228-249` (`tasks.withType<Test>().configureEach`):
  `inputs.files(…)` con `PathSensitivity.RELATIVE` de `README.md`, `NOTICE`, `SECURITY.md`, `docs/privacidad.html`,
  `docs/ficha-google-play.md`, `docs/PUBLICAR.md`, `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`,
  `.github/workflows/build-apk.yml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, los traspasos
  `*-traspaso.md` (M-5) y `app/src/main/res`; en `app`, también `src/main/assets` y `src/main/java` (que los tests leen
  sin comentarios). La lista sale de buscar en `app/src/test/java` cada `File(…)` y cada ruta fuera de `src` (lo leen
  `PublishedTextsTest`, `FinalLegalReviewTest`, `AppNameTest`, `ImportTextsTest`, `ToolTextsTest`, `SourceTextsTest`,
  `PlatformSourcesTest`, `MasterPolicyTest`). Ninguno lee `CLAUDE.md`, así que no está.
- Caché de configuración: no está activada (`gradle.properties` no tiene `org.gradle.configuration-cache`). Aun así las
  entradas son rutas fijas resueltas al configurar, sin `project` en tiempo de ejecución, así que son compatibles.
- Comprobación: con `jvm-check` y sin cambiar nada, la segunda ejecución sale `> Task :test UP-TO-DATE`; con
  solo un salto de línea más en `docs/ficha-google-play.md`, `> Task :test` se ejecuta (y otra vez al quitarlo). Con
  `app` (`:app:testDebugUnitTest --tests app.seguridad.tools.AppNameTest`): `UP-TO-DATE` sin cambios, y se ejecuta tras
  un salto de línea más en `README.md`. Los dos archivos han vuelto a su contenido.

## Otros detalles arreglados en el código tocado

- `ImportScreen.kt`: las importaciones estaban desordenadas (`DuplicateChoice`, `PreviewEstimate`,
  `importFoldersNotTagsText`); ordenadas. Fuera `EntryType`, que ya no se usaba.
- `GeneratorAndChecksTest.kt`: fuera los `import` que quedaban sin uso (`ByteArrayInputStream`, `assertFalse`).
- `FileHashSession.run` es `@Volatile`: se lee ahora desde el hilo de la lectura.

## Revisión experto-seguridad-cripto

He recorrido `SKILL.md` y `references/cripto-moderna.md` (§3, KDF) como lista de comprobación del diff.

- **Primitivas:** ninguna nueva. Las KDF siguen siendo las de Bouncy Castle con sus topes antes de calcular. El
  cerrojo es de `kotlinx.coroutines`, no criptografía.
- **Topes antes de derivar (decisión (a)):** con M-3, sin contraseña corren las mismas comprobaciones de cabecera que
  antes de derivar (`BitwardenJson.kt:39`, `ImportJson.kt:215`, `:224`), y con contraseña el orden no cambia: primero
  `bitwardenProtected`/`aegisEncrypted`/`twoFasEncrypted`, después la KDF (`BitwardenCrypto.kt`, `AegisCrypto.kt:121`,
  `TwoFasCrypto.kt:96`). Nada se deriva antes de la contraseña.
- **Memoria (R38):** `checkSane` sigue pensado para una derivación de hasta 128 MiB en ¾ del montón; `KdfLock` hace
  que lo sea en todo el proceso para la bóveda, las comprobaciones, las importaciones, «Cifrar texto» y «Cifrar archivo»
  (`KdfLock.kt`, `VaultStore.kt:321`, `BackupCheck.kt:85`, `ImportScreen.kt:303`, `SecurityToolsScreen.kt:281`,
  `FileCrypto.kt:29-30`). `KDF_OUT_OF_MEMORY` vuelve a decir la verdad.
- **Claves a cero (BOV-3):** `KdfLock.derive` devuelve lo derivado aunque se cancele quien llama
  (`KdfLockTest.aCancelledDerivationKeepsTheLockUntilItEnds`); `BackupCheck` borra la clave en su `finally`
  (`BackupCheckTest.aCancelledCheckMakesTheUnlockWait` comprueba los ceros); `changeMaster` y `replace` borran la copia de
  la clave si se cancelan mientras esperan (`VaultStore.kt:799-804`, `:930-933`).
- **Caché del teclado (MASVS-STORAGE-2, I-1):** cerrado en los diez campos de los laboratorios
  (`HashLabScreen.kt:295`, `:346`, `:374`, `:451`; `ClassicLabScreen.kt:178`, `:199`, `:237`, `:290`, `:299`, `:307`).
- **Suplantación (A M-4):** la fila del duplicado enseña el host que entraría, leído como un navegador (`urlHost`, RT-1)
  y sin controles ni invisibles (`plainReportText`) (`MergePlan.kt:400`).
- **Tiempo constante (decisión (b)):** fuera `sameHash`, que comparaba con `==` y ya no se usaba.
- **Datos hostiles (decisión (c)):** lo nuevo que se enseña del archivo (el host) pasa por `plainReportText` y sale
  como `Text`; nada se convierte en enlace ni en `Intent`.
- **Sin logs, sin red, sin permisos nuevos:** el diff no añade `Log`, `println`, hosts, permisos, componentes ni
  dependencias. Ningún formato cambia.

## Revisión Ingeniero Móvil Senior

He aplicado `desarrollo-movil-senior` (ciclo de vida, concurrencia, rendimiento) y `seguridad-criptografia-movil`.

- **Concurrencia:** `KdfLock` es un `Mutex` de corrutinas: esperar suspende y no ocupa ningún hilo, así que ni el hilo
  principal ni `Default` se bloquean (`KdfLock.kt`). El bloque corre en `compute`/`Default`. El orden de los cerrojos
  está escrito en su KDoc y no tiene ciclos (arriba). `withLock` es `inline`: entre el final del bloque y el `unlock` no
  hay punto de suspensión, así que el cerrojo nunca se suelta antes de que acabe la derivación.
- **Hilo principal:** los llamadores de `KdfLock.derive` (ViewModels en `Main.immediate`, `VaultStore` y `checkBackup`)
  solo suspenden; el trabajo va en `Dispatchers.Default` o en el `compute` de la tienda.
- **Ciclo de vida:** el progreso del Laboratorio de hashes vive en la sesión (`ComposeCells`), no en estado guardado; un
  borrado con la lectura en marcha la invalida (`HashLabSession.kt:75-96`). Ningún `rememberSaveable` nuevo.
- **Compose:** `SECRET_TEXT_KEYBOARD` es un `val` de nivel superior (estable, sin recomposiciones de más). El tipo
  `Password` no oculta el texto, y algunos teclados quitan los emojis o la predicción en esos campos: es lo previsto, como
  en «Cifrar texto».
- **Rendimiento:** M-3 añade sin contraseña una decodificación Base64 de hasta 8 MiB (milisegundos) en `Default`; el tope
  de 64 Ki caracteres en un campo es manejable para un `OutlinedTextField`.
- **Build:** las entradas nuevas de los tests (M-6) son archivos y carpetas fijos, sin acceso a `project` al ejecutar;
  la caché de configuración no está activada.

## Textos para el Abogado Digital (revisión final)

- **Ningún literal nuevo ni cambiado** en la interfaz.
- Cambian cosas visibles sin texto nuevo:
  - la fila de un duplicado en «Importar de otra app» enseña, además del usuario, la web (su host) que traería
    «Reemplazar»: «ana · banco-acceso.example» (M-4). Si se quiere enseñar también la web de la bóveda o decir que no
    tiene, sería un texto nuevo en `imports/ImportTexts.kt`;
  - un archivo cifrado que Clavero no puede abrir (KDF fuera de rango, cabecera rota, parte cifrada demasiado grande) da
    su texto de siempre (`IMPORT_KDF_TOO_BIG`, `IMPORT_BROKEN`, `IMPORT_ENCRYPTED_TOO_BIG`) antes de pedir la contraseña;
  - los campos de los laboratorios usan el teclado sin sugerencias: `HASH_LAB_HMAC_INTRO` («La clave y el mensaje no se
    guardan en el móvil») y `CLASSIC_NOT_SAVED` («Lo que escribes no se guarda en el móvil») vuelven a ser verdad sin
    cambiarlos;
  - el tope de una contraseña pegada en «Comprobar una copia» y en la contraseña de una exportación pasa de 1 024 a
    65 536 caracteres; `BACKUP_CHECK_TOO_LONG` e `IMPORT_PASSWORD_TOO_LONG` no cambian.
- `IMPORT_REPLACE_NOTE` («suma sus notas»): sigue siendo verdad; ahora no se suman las que son las mismas salvo espacios
  al principio o al final. Conviene leerlo con M-2, como pedía la revisión A.
- `KDF_OUT_OF_MEMORY` (`crypto/Crypto.kt`): sin cambio; con R38 y R40 vuelve a ser verdad en el caso de la revisión B.
