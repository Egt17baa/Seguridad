# Tarea 4: «Repartir un secreto» (SLIP-0039). Informe del implementador

**Estado:** DONE_WITH_CONCERNS. Commit `0b9cb46` «1.4.0: Repartir un secreto (SLIP-0039)», subido a `ccr-a1a6eabb-765f9g` (ver «Dudas», sobre todo la 1: el texto del aviso cambia «wallet» por «cartera»).

## Qué se ha hecho

### Paso 1: la licencia
- He vuelto a descargar `LICENSE`, `shamir_mnemonic/wordlist.txt` y `vectors.json` de github.com/trezor/python-shamir-mnemonic (rama `master`). `LICENSE` es MIT, «Copyright 2019 SatoshiLabs», con el texto MIT completo.
- Solo he copiado datos, nada de código:
  - `app/src/main/assets/slip39_english.txt`: idéntica a `wordlist.txt` (`cmp` sin diferencias; SHA-256 `bcc45553…01eec3`). Son 1.024 palabras ordenadas, de 4 a 8 letras y con prefijos de 4 letras únicos.
  - `app/src/test/resources/vectors/slip39.json`: idéntica a `vectors.json` (SHA-256 `13ebeceb…cdabbdc4`). Son 45 casos.
- `app/src/main/assets/licenses/slip39.txt` lleva una línea que dice qué se copió y de dónde (solo los datos), seguida del texto MIT literal con su línea de copyright.
- La atribución está en `NOTICE`, en `LicensesScreen.kt:38` y en el README, sección «Licencias», párrafo «Lista de SLIP-0039».

### Paso 4: la implementación, escrita desde la especificación
La he escrito desde `slip-0039.md`, sin mirar ninguna implementación. Todo vive en `app/src/main/java/app/seguridad/tools/slip39/`.

- **`Gf256.kt`:**
  - el GF(256) de AES, con polinomio `0x11B` y tablas exp/log de generador 3;
  - las tablas solo se usan con los índices x, que son públicos;
  - `mul` (`Gf256.kt:35`) no tiene ni tablas ni ramas que dependan de los datos: es la única multiplicación que toca bytes secretos.
- **`Rs1024.kt`:** el `polymod`, el checksum y la verificación, con `shamir` y `shamir_extendable`.
- **`Shamir.kt`:**
  - `interpolate` (Lagrange, `Shamir.kt:18`);
  - `shamirSplit` (SplitSecret, con el secreto en x = 255 y el digest en x = 254, `Shamir.kt:46`);
  - `shamirRecover`, que compara el digest con HMAC-SHA256 y `MessageDigest.isEqual` (`Shamir.kt:69-76`);
  - todo intermedio se pone a cero.
- **`Slip39.kt`:**
  - `GroupSpec`, `Slip39Failure` y los límites;
  - la clase `Slip39(words)`, con `splitSecret` (`:91`), `combineShares` (`:140`), `decode` (`:205`) y `encode`;
  - la red de Feistel de 4 rondas (`:333`), con PBKDF2-HMAC-SHA256 de 2500 << e iteraciones por ronda, de Bouncy Castle (`PKCS5S2ParametersGenerator`, `:361`);
  - el bit extensible activado al repartir (`:108`) y el identificador aleatorio de 15 bits (`:105`).
- **`crypto/Bip39.kt`:**
  - `entropyOf(List<String>): ByteArray?` (`:55`) y `phraseOf(ByteArray): List<String>` (`:88`) no existían, ni con esos nombres ni con otros: solo había `checksumOk`, privado, que construía una cadena de bits;
  - las he añadido, y ahora `checksumOk` usa `entropyOf`, así que el checksum ya no pasa por un `String` de bits, que no se puede borrar.

### Paso 6: la pantalla
- `ui/screens/SecretShareScreen.kt`:
  - modos «Repartir» y «Recomponer»;
  - entrada como frase BIP-39 o como hex;
  - umbral de grupos y grupos (añadir y quitar, de 1 a 16);
  - contraseña opcional;
  - el aviso obligatorio en su propia tarjeta, antes del botón y de las partes (`:346`);
  - «Lo que no protege» (`:293`);
  - la nota del resultado («otra contraseña da otro secreto», `:402`);
  - `TOOLS_AWAY_WIPE_NOTE` (`:302`).
- Patrón de la tarea 2:
  - un ViewModel con `SecretShareSession` (un `GuardedSession`), `watchAway` (`:142`), `GuardEffects` (`:286`) y `ToolColumn(..., s.guard)` (`:288`);
  - `onCleared` borra la sesión (`:270`);
  - todas las copias pasan por `copySensitive` (`:365` y `:410`);
  - no hay `rememberSaveable`.
- `SecretShareSession.forget()` (`tools/slip39/SecretShareSession.kt:57`) borra la frase o el hex, la contraseña, las partes tecleadas, las partes generadas y el secreto recuperado. Este último se pone a cero también cuando lo sustituye otro (`:49`).
- `SecretShareRuns.kt`, nuevo, se ocupa de las ejecuciones en `Dispatchers.Default`, cancelables entre rondas, y rechaza los resultados tardíos. Se puede probar en la JVM.
- `SecretShareInput.kt`, nuevo:
  - `splitRequest` y `combineRequest` toman una instantánea del formulario y comprueban los límites antes de hacer nada;
  - también están aquí `splitInput`, `schemeOf`, `partsOf` y `phraseOrNull`.
- `SecretShareTexts.kt` reúne todos los textos de la herramienta.
- `AppNavHost.kt:254` tiene la ruta `tools/share-secret`, y `ToolsScreen.kt:52` la entrada «Repartir un secreto», con el subtítulo del brief.
- `app/build.gradle.kts:197`: `checkAssets` exige también `slip39_english.txt`. He actualizado el mensaje y la descripción de la tarea, y su registro dice «con las cuatro listas de palabras».
- `vault/MasterPolicy.kt`:
  - `WordLists` gana `bip39English()` (`:110`), leída una vez por proceso en IO, para que la pantalla no vuelva a nombrar la lista BIP-39;
  - es lo que exige `MasterPolicyTest` («las listas viven en un solo sitio»);
  - nace la constante `BIP39_ENGLISH_LIST`, y la lista SLIP-39 se lee en el propio ViewModel, en IO.

## Decisión del dueño durante la tarea, (a) y (b): cómo se cubre

**(a) Entradas enormes o maliciosas.**
1. **Los límites se miden sobre el `String` en bruto, en el ViewModel, antes de cualquier trabajo:**
   - `vm.limited(...)` rechaza el pegado demasiado largo antes de guardarlo (`SecretShareScreen.kt:168-176`, y cada campo en `:316`, `:377`, `:418` y `:443`);
   - después, `splitRequest` y `combineRequest` vuelven a medir el texto en bruto antes de partirlo, normalizarlo o copiarlo (`SecretShareInput.kt:101-103` y `:115-120`; también `:24`);
   - la biblioteca hace lo mismo por su cuenta:
     - número de partes antes de nada (`Slip39.kt:141`);
     - longitud de la contraseña antes de copiarla (`:322-324`);
     - longitud de cada parte antes de normalizarla (`:206`);
     - número de palabras, de 20 a 59;
     - exponente de iteraciones, como mucho 8 (`:167`, antes de PBKDF2);
     - grupos y miembros, de 1 a 16;
     - un máximo de 16 grupos en `schemeOf`.
2. **Nada cuadrático ni sin límite:**
   - las partes repetidas se buscan en un `HashMap` por (grupo, miembro), en tiempo lineal (`Slip39.kt:145-156`);
   - la expansión de NFKC está acotada (1.000 caracteres por parte como mucho);
   - 256 partes como mucho, y 16 grupos.
3. **PBKDF2:**
   - como mucho 2.560.000 iteraciones (e ≤ 8);
   - en `Dispatchers.Default` (`SecretShareRuns.kt:47` y `:75`);
   - con `ensureActive` antes de cada ronda (`Slip39.kt:333` y siguientes, en `check()`) y entre grupos.
4. **Tests:**
   - `SecretShareInputTest.anOversizedCombineIsRefusedBeforeAnyWork`: 10 MB de partes, una contraseña de 1 M y 5.000 partes, todo en menos de 1 s;
   - `SecretShareInputTest.anOversizedSplitIsRefusedBeforeAnyWork`: un secreto de 10 MB, una contraseña de 10 MB, un número de 10 MB y 5.000 grupos;
   - `Slip39Test.boundsComeBeforeAnyWork`: 5.000 partes de 9 MB, una parte enorme, una contraseña de 1 M, 19 y 60 palabras;
   - `Slip39Test.theIterationExponentIsCapped`: una parte que pide e = 15 se rechaza en menos de 2 s.

**(b) TOCTOU y carreras.**
1. **Instantánea:**
   - la petición se construye una sola vez, al pulsar, y el trabajo solo ve esa copia (`SecretShareScreen.kt:234` y `:252`);
   - la biblioteca valida y combina dentro de una misma llamada, sobre sus propias copias decodificadas.
2. **Resultado tardío:**
   - cada ejecución lleva un número;
   - un borrado (`session.wipe` → `runs.stop()`, `SecretShareScreen.kt:120`), un «Cancelar», una edición, un cambio de modo o un segundo toque llaman a `stop()`, que cancela y sube el número (`SecretShareRuns.kt:31`);
   - un resultado solo se muestra si `runId == mine` (`:48` y `:76`);
   - el secreto recuperado que no se acepta se pone a cero, también cuando `withContext` lo descarta por la cancelación (`:75` y `:83`).
3. **Doble toque:**
   - `combine` y `split` paran siempre la ejecución anterior;
   - además, mientras hay una en marcha, el botón deja sitio a «Calculando… Cancelar».
4. **Tiempo constante:**
   - el digest se compara con `MessageDigest.isEqual` (`Shamir.kt:76`), y las partes repetidas también (`Slip39.kt`, `sameAs`);
   - el checksum de BIP-39 se compara como un entero sin ramas por bit.
5. **Tests:**
   - `SecretShareRunsTest.aRecoveredSecretThatArrivesAfterAWipeIsRefusedAndZeroed`;
   - `partsThatArriveAfterAWipeAreNotShown`;
   - `aCancelDuringTheWorkRefusesTheResult`;
   - `aSecondTapSupersedesTheFirstRun`: un segundo toque durante el trabajo, y dos toques antes de que empiece; queda un solo trabajo y gana el último;
   - `aFailureIsShownOnlyForTheLastRun`;
   - `theRealWorkStopsAtTheNextRoundAfterAWipe`: con el `Slip39` real, un borrado dentro de la comprobación de PBKDF2 para el trabajo.

## Entradas hostiles (punto 5 del controlador)
- **Sustitutos sueltos:** se rechazan como «palabra que no es de la lista», antes de normalizar.
- **Normalización:** NFKC (letras de ancho completo y ligaduras) y fuera los caracteres de formato invisibles (ZWSP, BOM).
- **Espacios:** cualquiera sirve de separador, también el espacio de no separación (NBSP), los tabuladores y los finales de línea.
- **Palabras con acento o de otro alfabeto:** dan «La palabra N de la parte M no es de la lista SLIP-39.».
- **Mayúsculas:** no importan.
- **Partes repetidas:** una parte idéntica cuenta una vez; dos partes distintas con el mismo número dan error.
- **Contraseña:** solo ASCII imprimible (32 a 126); cualquier otra cosa se rechaza con un mensaje claro y nunca se codifica en silencio.
- **Errores:** todo error es un `Slip39Failure` en español, y ningún mensaje lleva palabras, bytes ni la contraseña (`unknownWordsAreNamedByPositionOnly`).
- **Estructura:** solo con el checksum válido se lee algún campo; el relleno de más de 8 bits o con algún bit a 1 se rechaza.

## Tests y verificación

**TDD.**
- **RED:** `gradle -p jvm-check test --tests 'app.seguridad.tools.slip39.*'`, con los tests escritos antes del código. Falló al compilar: `Unresolved reference 'Slip39'`, `'Slip39Failure'`, `'GroupSpec'`, `'SecretShareSession'` y `'GroupForm'`. Era lo esperado, porque no existía nada.
- **GREEN 1:** el mismo comando dio `BUILD SUCCESSFUL`, con 21 de 21 en `Slip39Test` y 3 de 3 en `SecretShareSessionTest`. Hubo antes una vuelta en rojo de `unknownWordsAreNamedByPositionOnly`, porque un caso con ZWSP contradecía la decisión de quitar los caracteres de formato. Lo pasé al test que acepta esas entradas.
- **Decisión del dueño:** tras ella añadí `SecretShareRunsTest` (6), los tests de tamaño de `SecretShareInputTest` y los casos de `boundsComeBeforeAnyWork`; los vi fallar al compilar por la falta de `SecretShareRuns`, `combineRequest` y `splitRequest`, e implementé lo necesario hasta el verde.
- **Test nuevos:** 38 en `tools/slip39` (21 + 8 + 6 + 3), 2 en `PlatformSourcesTest` (la pantalla y el de marcas) y 2 asserts en `AppContainerTest`.

**Verificación completa:** 
- `export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` → `BUILD SUCCESSFUL in 3m 8s`; 1395 tests de Android (`testDebugUnitTest`), 0 fallos; `checkAssets`: «assets/: solo archivos de app/src/main/assets, con las cuatro listas de palabras»; lint: «0 errors, 16 warnings» (ninguno en archivos de esta tarea; ver la duda 2).
- `gradle -p jvm-check test --no-daemon --max-workers=1` → `BUILD SUCCESSFUL in 1m 16s`; 1390 PASSED, 0 FAILED.

## Archivos
- **Nuevos:**
  - en `tools/slip39/`: `Gf256.kt`, `Rs1024.kt`, `Shamir.kt`, `Slip39.kt`, `SecretShareTexts.kt`, `SecretShareSession.kt`, `SecretShareInput.kt` y `SecretShareRuns.kt`;
  - `ui/screens/SecretShareScreen.kt`;
  - `assets/slip39_english.txt` y `assets/licenses/slip39.txt`;
  - en los tests: `Slip39Test.kt`, `SecretShareSessionTest.kt`, `SecretShareInputTest.kt`, `SecretShareRunsTest.kt` y `resources/vectors/slip39.json`.
- **Cambiados:**
  - `crypto/Bip39.kt`, `vault/MasterPolicy.kt`, `ui/AppNavHost.kt`, `ui/screens/ToolsScreen.kt` y `ui/screens/LicensesScreen.kt`;
  - `app/build.gradle.kts`, `NOTICE` y `README.md`;
  - `PlatformSourcesTest.kt` y `AppContainerTest.kt`.

## Desviaciones del brief (con su motivo)
1. **Funciones dentro de una clase:** `splitSecret` y `combineShares` son métodos de `Slip39(words)` y no funciones sueltas, porque necesitan la lista de palabras, que es un asset; es el mismo patrón que `Bip39(words)`. Además, aceptan un parámetro opcional `check: () -> Unit`, por el que se cancelan.
2. **PBKDF2 de Bouncy Castle, no de `javax.crypto`.** La regla del proyecto permite las dos. Tiene tres ventajas:
   - recibe los bytes exactos de la contraseña (`SecretKeyFactory` pasa `char` a bytes según el proveedor, que en Android es otro);
   - los tests de la JVM y el móvil ejecutan exactamente el mismo código, así que los vectores oficiales prueban también lo que corre en Android;
   - la clave derivada se puede poner a cero.

   HMAC-SHA256 (el digest) sigue siendo de `javax.crypto.Mac`.
3. **Límites propios:**
   - «Recomponer» acepta secretos de hasta 64 bytes (59 palabras), para que se puedan recomponer las semillas BIP-32 de otras herramientas, aunque «Repartir» siga en 16 a 32 bytes;
   - el exponente de iteraciones está limitado a 8;
   - al repartir se usa siempre e = 1, sin selector en la interfaz.

## Dudas
1. **«wallet» → «cartera» en el aviso R3.** El texto de R3, tal cual, rompía `FinalLegalReviewTest.noAppTextSaysWallet`, que aplica R-14 de la revisión final del Abogado Digital de la 1.3.0: «carteras de criptomonedas» en lugar de «wallets», en todo texto de la app. Siguiendo el mismo principio de R3 (las reglas del dueño mandan), he cambiado solo esa palabra, sin tocar el test. Ahora dice: «Estas partes no son una copia SLIP-39 de cartera. Metidas en una cartera de hardware como recuperación SLIP-39 darían otra cartera distinta. Recomponlas en Clavero o con una herramienta SLIP-0039 que devuelva la entropía.» Si el controlador prefiere «wallet», hace falta una excepción en ese test, y es decisión del dueño o del Abogado Digital.
2. **Lint:** salen 16 avisos que no son míos (14 de versiones más nuevas, que lint ve con red, más `DiscouragedApi` y `ObsoleteSdkInt`), frente a los 4 de la línea base que se esperaban; ninguno está en archivos de esta tarea. El único mío (`ModifierParameter`, `SecretShareScreen.kt:440`) está corregido.
3. **Copias que no se pueden borrar:** los `String` (el texto de los campos de Compose, las partes generadas y la copia en pantalla del hex y de la frase recuperados) no se pueden poner a cero. Ocurre lo mismo en las demás herramientas. Tampoco se pueden borrar las copias internas de BC y de `javax.crypto` (el estado de HMac y `SecretKeySpec`).
4. **En el móvil queda por probar:**
   - repartir y recomponer un vector oficial (de `slip39.json`) en el teléfono;
   - el tiempo con e = 8 (unos segundos, con «Cancelar»);
   - TalkBack en los campos numéricos.

## Revisión experto-seguridad-cripto
He recorrido `references/cripto-moderna.md` (aleatoriedad, KDF, comparaciones) y `auditoria.md` sobre el diff.

**Aleatoriedad.**
- Todo sale de `SecureRandom` (`secureRandom` de `crypto/Crypto.kt`, inyectable para los tests): el identificador de 15 bits (`Slip39.kt:105`) y, en Shamir, R y los valores aleatorios (`Shamir.kt:49` y `:54`).
- No hay ningún `Random`.

**Primitivas.**
- Solo se usan primitivas estándar: HMAC-SHA256 de `javax.crypto.Mac` (`Shamir.kt`) y PBKDF2-HMAC-SHA256 de Bouncy Castle (`Slip39.kt:361`).
- GF(256) y Shamir no son criptografía propia: son el esquema que fija la norma, y los 45 vectores oficiales (`officialVectors`) cubren las dos variantes de `ext`, 128 y 256 bits y los grupos.

**Canales laterales.**
- Los bytes secretos solo se multiplican con `Gf256.mul`, en tiempo constante (`Gf256.kt:35`).
- Las tablas log/exp solo se indexan con los x, que son públicos (`Shamir.kt:22-29`).
- El digest y las partes repetidas se comparan con `MessageDigest.isEqual` (`Shamir.kt:76`).

**Borrado de datos.**
Se ponen a cero:
- las entradas: la contraseña en bytes (`Slip39.kt:106-111` y el `finally` de `combineShares`) y los índices de las palabras (`:250`);
- los intermediarios: EMS, las partes de grupo y de miembro, L/R de Feistel, la contraseña con su prefijo, la sal y la clave derivada (`:364-366`), R, D y la etiqueta en Shamir;
- en BIP-39, la entropía y los índices (`Bip39.kt:78`, `:83` y `:104`).

**Validación según la norma.**
- Se comprueban el checksum antes que los campos, el relleno (como mucho 8 bits y a cero), el identificador, `ext`, `e`, GT, G y la longitud comunes, G ≥ GT, GM = GT, Mᵢ = Tᵢ, los índices distintos y Tᵢ = 1 ⇒ Nᵢ = 1 (`splitRefusesWhatSlip39Forbids` y `combineChecksTheRulesOfTheFormat`).
- El índice 0 no es un ataque aquí: el secreto está en x = 255, como explica la norma.

**Contraseña.**
- Solo ASCII imprimible (`Slip39.kt:324`).
- Una contraseña equivocada da otro secreto: el test `wrongPassphraseGivesAnotherSecret` lo deja escrito y la pantalla lo dice (`SHARE_NOT_PROTECTED` y `SHARE_RESULT_NOTE`).

**Lo que no protege, dicho en la pantalla:**
- quien reúna el umbral (y la contraseña) obtiene el secreto;
- una contraseña equivocada da otro secreto sin aviso;
- las partes no son una copia SLIP-39 de cartera.

**Otros puntos:**
- **Logs y mensajes:** no hay ningún log, y ningún mensaje lleva datos (`unknownWordsAreNamedByPositionOnly`).
- **Red y formatos:** no hay red nueva ni formato nuevo de Clavero, y las partes SLIP-39 son el formato de la norma.
- **Licencias:** no hay dependencias nuevas.
- **Marcas:** ninguna marca sale en los textos de `app/src/main/java`, salvo en `LicensesScreen.kt`; lo comprueba `PlatformSourcesTest.no wallet brand appears…`. «TREZOR» solo aparece en `Slip39Test.kt`.

**Riesgo residual (bajo):**
- con e = 8 en un móvil lento, cada ronda puede tardar alrededor de 1 s, y «Cancelar» espera a que acabe la ronda en curso;
- los `String` de Compose no se pueden borrar.

## Revisión Ingeniero Móvil Senior
**Ciclo de vida.**
- El estado vive en el ViewModel (`SecretShareScreen.kt:120-139`) y nunca en el estado guardado; no hay `rememberSaveable` (`PlatformSourcesTest`).
- Volver atrás → `onCleared` → `session.wipe()` (`:270`).
- Bloqueo, otra app o tiempo fuera → `watchAway` y `GuardEffects` (`:142` y `:286`).
- La nota del tamaño excesivo se va al salir de la pantalla (`DisposableEffect`).

**Concurrencia.**
- `viewModelScope` y `Dispatchers.Default` a través de `SecretShareRuns`; las listas se leen en IO (`:145`, y `bip39English()` en `MasterPolicy.kt:110`).
- No hay trabajo pesado en el hilo principal.
- Hay una sola ejecución a la vez, y gana la última (`SecretShareRunsTest`).
- `session` y `runs` llevan tipo explícito, porque su referencia mutua daba un error de inferencia recursiva en Kotlin (`:120-123`).

**Rendimiento.**
- Los campos están acotados: el secreto a 1.000, las partes a 40.000, la contraseña a 256 y los números a 2.
- Las partes generadas se pintan dentro de la `Column` desplazable de `ToolColumn` (como mucho 256).

**Interfaz.**
- Material 3, con `FilterChip`, `SectionCard`, `PasswordField` y `KeyboardType.Password` sin autocorrección en los campos secretos (`:276`), como en «Cifrar texto».
- El teclado numérico va en los números.
- `ModifierParameter` está corregido.

**Portapapeles:** `copySensitive`, con el aviso de Android 8 a 12 y el borrado (`:365` y `:410`).

**Navegación:** `AppNavHost.kt:254` pasa `vault`, `lockAfterMs` y `awayInOtherApp`, igual que las tareas 2 y 3.

**La JVM compila** todo `tools/slip39`, porque no hay dependencias de Android. Probado con `gradle -p jvm-check test`.

## Textos para el Abogado Digital (revisión final)
Todos están en `app/src/main/java/app/seguridad/tools/slip39/SecretShareTexts.kt`, salvo el último, que está en `LicensesScreen.kt:38`.

**Pantalla y entrada de «Herramientas».**
- «Repartir un secreto». Subtítulo: «Divide una frase o un secreto en partes (SLIP-0039)».
- Introducción: «Divide una frase BIP-39 o un secreto en hex en varias partes, con el reparto de secretos de Shamir de SLIP-0039. Con las partes necesarias se recompone el secreto; con menos, no. Todo se calcula en tu móvil y nada se guarda.»
- «Lo que no protege»: «Quien reúna las partes necesarias obtiene el secreto, sea quien sea; si pusiste contraseña, le hace falta también. Guarda cada parte en un sitio distinto. Una contraseña equivocada no da ningún error: da otro secreto distinto, sin avisar. Y estas partes no son una copia SLIP-39 de cartera: lee el aviso.»
- «Aviso»: «Estas partes no son una copia SLIP-39 de cartera. Metidas en una cartera de hardware como recuperación SLIP-39 darían otra cartera distinta. Recomponlas en Clavero o con una herramienta SLIP-0039 que devuelva la entropía.» Es R3, con «cartera» por R-14: ver la duda 1.

**Modos y entrada.**
- «Repartir», «Recomponer», «Frase BIP-39», «Secreto en hex».
- «Frase BIP-39 (12, 15, 18, 21 o 24 palabras en inglés)», «Secreto en hex (de 16 a 32 bytes, en número par)».
- «Se reparte la entropía de la frase, y al recomponer sale la misma frase. Una passphrase BIP-39 (la «palabra 25») no entra en el reparto: guárdala aparte.»
- «Una frase BIP-39 tiene 12, 15, 18, 21 o 24 palabras.», «La palabra N no es de la lista BIP-39 en inglés.», «La frase no cuadra con su suma de control: revisa las palabras.»
- «El secreto en hex tiene que ser de 16 a 32 bytes en número par: de 32 a 64 cifras hex, en múltiplos de 4.», «El secreto es demasiado largo.», «Hay demasiado texto en las partes.», «No se pudo leer la lista de palabras de la app.»

**Grupos.**
- «Grupos necesarios», «Grupo N», «Partes necesarias», «Partes», «Añadir grupo», «Quitar grupo».
- «Lo normal es un solo grupo: por ejemplo, 2 partes necesarias de 3. Con varios grupos, hacen falta las partes necesarias de tantos grupos como diga «Grupos necesarios».»
- «Escribe un número del 1 al 16 en cada casilla.»

**Contraseña.**
- «Contraseña (opcional)».
- «Solo caracteres ASCII imprimibles, como pide SLIP-0039: letras sin acentos ni eñes, números, espacios y signos. Para recomponer hace falta la misma; con otra sale un secreto distinto.»

**Botones y estado.**
- «Repartir», «Recomponer», «Calculando…», «Cancelar», «Copiar».
- «Ese texto es demasiado largo y no se ha pegado.»

**Partes y resultado.**
- «Grupo N: 1 parte» / «Grupo N: hacen falta T de estas N partes», «Parte N».
- «Un solo grupo.» / «Hacen falta las partes de T de los G grupos.»
- «Partes, una por línea», «Escribe o pega las partes necesarias, cada una en su línea. Mayúsculas y espacios de más no importan.»
- «Secreto en hex», «Como frase BIP-39», «Si la contraseña no es la que se usó al repartir, este es otro secreto distinto: SLIP-0039 no avisa.»

**Mensajes de `Slip39Failure`.**
- Número y longitud de las partes:
  - «Hay demasiadas partes: como mucho 256.»
  - «Escribe al menos una parte.»
  - «La parte N es demasiado larga.»
  - «La parte N tiene C palabras: una parte tiene de 20 a 59.»
- Palabras y estructura de una parte:
  - «La palabra P de la parte N no es de la lista SLIP-39.»
  - «La parte N tiene alguna palabra mal: su suma de control no cuadra.»
  - «La parte N no tiene una longitud válida.»
- El conjunto de partes:
  - «Las partes no son del mismo reparto: no coinciden su identificador, sus grupos o su longitud.»
  - «Las partes piden más grupos de los que tiene el reparto.»
  - «Hacen falta partes de X grupos y hay de Y.»
  - «Sobran partes: hacen falta partes de X grupos y hay de Y.»
  - «Las partes del grupo N no coinciden en cuántas hacen falta.»
  - «Hay dos partes distintas con el mismo número en el grupo N.»
  - «En el grupo N hacen falta X partes y hay Y.»
  - «Las partes no recomponen el secreto: alguna está cambiada o es de otro reparto.»
- El cálculo y la contraseña:
  - «El cálculo pedido es demasiado largo: Clavero acepta un exponente de iteraciones de 0 a 8.»
  - «La contraseña solo puede llevar caracteres ASCII imprimibles: letras sin acentos ni eñes, números, espacios y signos.»
  - «La contraseña es demasiado larga: como mucho 256 caracteres.»
- La configuración del reparto:
  - «El secreto tiene que tener de 16 a 32 bytes, en número par.»
  - «Hacen falta de 1 a 16 grupos, y los grupos necesarios tienen que ser de 1 al número de grupos.»
  - «En cada grupo, las partes tienen que ser de 1 a 16, y las necesarias, de 1 al número de partes.»
  - «Un grupo que pide 1 parte solo puede tener 1 parte: si quieres más copias, copia esa misma parte.»

**Licencias** (`LicensesScreen.kt:38`): «Lista de palabras SLIP-0039», «MIT», «Las 1.024 palabras en inglés de SLIP-0039, sin cambios, de python-shamir-mnemonic (Copyright 2019 SatoshiLabs). «Repartir un secreto» las usa para las partes. Solo se usan los datos, no el código.»

**Documentos, para revisarlos también:** el párrafo nuevo del README («Lista de SLIP-0039»), la entrada de `NOTICE` y la cabecera de `assets/licenses/slip39.txt`.

---

# Ronda de arreglos 1 (revisión `task-4-review.md`)

**Commit:** `54161a8` «1.4.0: Repartir un secreto, arreglos de la revisión de la tarea 4», subido a `ccr-a1a6eabb-765f9g`.

## Qué ha cambiado
- **I-1 (Important).**
  - Nota fija bajo «Como frase BIP-39», entre la frase y «Copiar»: `SHARE_RESULT_PHRASE_NOTE` (`SecretShareTexts.kt:84`), que se pinta con `Output(context, phrase, note = SHARE_RESULT_PHRASE_NOTE)` (`SecretShareScreen.kt:401`).
  - `Output` dibuja la nota antes del botón de copiar.
  - El texto es el que propuso la revisión, con «cartera». Es fiel al código: `phraseOrNull` convierte en frase cualquier secreto de 16 a 32 bytes en pasos de 4, y no puede saber de dónde viene.
  - Test: `PlatformSourcesTest.Repartir un secreto…` fija el texto, la llamada y que la nota va antes de `copySensitive`.
- **M-1.**
  - `SHARE_MAX_PARTS_CHARS = SLIP39_MAX_PARTS * SHARE_MAX_PART_LINE_CHARS` (`SecretShareInput.kt:17`), es decir, 256 × 531 = 135 936. Caben las 256 partes de 59 palabras, cada una en su línea.
  - Sigue acotado y se comprueba antes de cualquier trabajo (`combineRequest`), y el KDoc de `SLIP39_MAX_PARTS` lo dice.
  - Tests:
    - `SecretShareInputTest.thePartsFieldHoldsTheLargestCombination`: el borde exacto pasa, y un carácter más da `SHARE_PARTS_TOO_LONG`;
    - `Slip39Test.boundsComeBeforeAnyWork`: 256 partes de 59 palabras superan los límites y fallan después, en el checksum.
- **M-2.**
  - `officialVectors` compara ahora el tipo de fallo (el mensaje sin sus números) de cada vector inválido con `INVALID_VECTOR_REASONS`, el mapa de la revisión: 30 casos, más el 5 y el 24 (`slip39WrongCount`).
  - Un vector que falle por otro motivo hace fallar el test.
- **M-3.**
  - `splitInput` ya no llama a `bip39.check` y no calcula sugerencias: con `normalize`, el nuevo `Bip39.isWord` (`Bip39.kt:29`) y `entropyOf`, se queda solo con la posición de la primera palabra desconocida (`SecretShareInput.kt:41-46`).
  - Test: `SecretShareInputTest.aPhraseWithAnUnknownWordNamesTheFirstOne`, con los tests de frase que ya había.

## Tests y comandos
- **Tests de los arreglos:**
  - `gradle -p jvm-check test --no-daemon --max-workers=1 --tests 'app.seguridad.tools.slip39.*' --tests 'app.seguridad.PlatformSourcesTest' --tests 'app.seguridad.tools.SourceTextsTest' --tests 'app.seguridad.tools.FinalLegalReviewTest' --tests 'app.seguridad.crypto.SeedCryptoTest'`;
  - salida: `BUILD SUCCESSFUL in 28s`.
- **Verificación de Android:**
  - `export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkAssets --no-daemon --max-workers=1`;
  - salida: `BUILD SUCCESSFUL in 3m 35s`; 1397 tests de Android, 0 fallos; lint: «0 errors, 16 warnings», los mismos 16 de antes, ninguno en archivos de esta tarea.
- **Verificación de la JVM:**
  - `gradle -p jvm-check test --no-daemon --max-workers=1`;
  - salida: `BUILD SUCCESSFUL in 1m 7s`; 1392 PASSED, 0 FAILED.

## Revisión experto-seguridad-cripto (ronda 1)
- **I-1:** cerrado con texto, porque el código no puede distinguir una entropía BIP-39 de una semilla BIP-32. La nota no promete nada que el código no haga.
- **M-1:** los límites siguen acotados antes de cualquier trabajo, y el campo admite ahora unos 136 000 caracteres como mucho.
- **M-2:** los rechazos se comprueban por su motivo, así que una regresión en la decodificación (por ejemplo, del relleno al checksum) ya no pasaría desapercibida.

## Revisión Ingeniero Móvil Senior (ronda 1)
- **M-3:** menos trabajo en el hilo principal al pulsar «Repartir».
- **M-1:** un campo de Compose con hasta 136 000 caracteres, que solo se alcanza si alguien lo pega. Los demás límites y el rechazo de un pegado más largo no cambian (`vm.limited`, `SecretShareScreen.kt:378`).

## Textos para el Abogado Digital (revisión final): textos nuevos de esta ronda
- `SHARE_RESULT_PHRASE_NOTE`: «Esta es la frase BIP-39 de estos bytes. Si las partes vienen de una cartera, el secreto es su semilla y esta frase daría otra cartera distinta: usa la frase solo si repartiste una frase BIP-39.»
