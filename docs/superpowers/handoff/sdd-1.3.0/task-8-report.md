# Task 8: PIN, códigos de recuperación y respuestas inventadas — informe

Commit: `b013f8d` «1.3.0: PIN, códigos de recuperación y respuestas inventadas» (rama ccr-a1a6eabb-765f9g, sin push).

## Implementado

- `app/src/main/java/app/seguridad/tools/codes/Pin.kt`: `isWeakPin`, `generatePin(digits = 6, random)`, `strongPinCount`, `pinBits`. Una sola regla (`weakDigits`, sin asignaciones) sirve a las tres, así que el contador y el comprobador no pueden discrepar.
  - Débil: top 20 de 4 cifras; una cifra o un bloque repetido (1111, 1212, 123123); secuencia ascendente o descendente con 9→0 (1234, 9876, 8901); años 1900-2099 (PIN entero a 4 cifras; al inicio o al final desde 5); fechas válidas sin bisiestos (DDMM y MMDD a 4; DDMMAA, MMDDAA y AAMMDD a 6; las de 8 cifras llevan año y las pilla la regla del año); líneas del teclado de 3 o más teclas en los dos sentidos y sus concatenaciones (4 = 4; 6 = 3+3; 7 = 3+4 o 4+3; 8 = 4+4; 5 no se puede partir).
  - `generatePin`: `random.nextInt(10^n)` y vuelve a sortear si es débil (rejection sampling), con ceros a la izquierda. `require(digits in 4..8)`.
  - `isWeakPin` hace `require` de 4 a 8 dígitos ASCII (rechaza `"١٢٣٤"`).
- `RecoveryCodes.kt`: `RECOVERY_ALPHABET`, `recoveryCodes` (10 códigos de 10 símbolos, `XXXX-XXXX-XX`, `nextInt(32)`; si hay algún repetido se sortea el conjunto entero otra vez) y `recoveryCodesNote` (título «Códigos de recuperación · <web>», un código por línea).
- `InventedAnswers.kt`: `withoutAccents` (NFD, sin `Mn`; ñ da n), `inventedAnswer` (4 palabras con `nextInt(size)`, independientes), `InventedQuestion`, `inventedAnswersNote` («Respuestas de seguridad · <web>», `"$question → $answer"` por línea).
- `app/src/main/assets/bip39_spanish.txt`, byte a byte (`cmp` con el original en el scratchpad).
- `ui/screens/CodesScreen.kt`: tres secciones con los textos del anexo. PIN con selector 4-8 y «Quedan N PIN posibles (unos X bits)» (el recuento se calcula una vez en `Dispatchers.Default` y se guarda en un `ConcurrentHashMap` por longitud); códigos con «Otros códigos», «Copiar todos» y «Guardar en la bóveda como nota»; respuestas con web, preguntas, «Sin tildes» (activada), «Otra respuesta», «Añadir pregunta» y «Guardar en la bóveda». Con la bóveda bloqueada se muestra el `VaultGate` existente (con «No guardar»); al abrirse guarda con `store.upsert` (id `UUID.randomUUID()`, como `VaultEntryScreen`) y vuelve a la pantalla con lo sorteado intacto. Todo lo sorteado va en `remember` (ningún `rememberSaveable`), `NoCopy` envuelve el contenido, se copia con `copySensitive`, sin logs.
- `TOOLS` (`Tool("tools/codes", "🔢", ...)`), `AppNavHost` (`tools/codes`), `licenses/bip39.txt`, `LicensesScreen.kt`, `NOTICE` y README con los textos del anexo.

## sha256sum

```
46846a5a0139d1e3cb77293e521c2865f7bcdb82c44e8d0a06a2cd0ecba48c0b  .../scratchpad/es.txt
46846a5a0139d1e3cb77293e521c2865f7bcdb82c44e8d0a06a2cd0ecba48c0b  app/src/main/assets/bip39_spanish.txt
```
2048 líneas, sin `\r`. Ver «Concerns» sobre NFD.

## TDD

- RED: con los tres tests escritos y sin implementación, `gradle -p jvm-check test --tests 'app.seguridad.tools.codes.*'` falló en `:compileTestKotlin` con «Unresolved reference» (`recoveryCodes`, `RECOVERY_ALPHABET`, `recoveryCodesNote`, `isWeakPin`, ...). BUILD FAILED.
- GREEN: tras implementar, 37 de 38 en verde; el único fallo (`plainOrExactFormOfTheSameDraws`) destapó que el archivo de bitcoin/bips guarda las tildes como marcas combinadas (NFD): la forma exacta ahora se normaliza a NFC y el test lo comprueba. Después, todo en verde.

## Tests y resultados

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 828 tests, 0 fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 828 tests, 0 fallos; lint 0 errores y 11 avisos (de dependencias, ya existían); `checkAssets`: «solo archivos de app/src/main/assets».
- Nuevos: PinTest 13, RecoveryCodesTest 6, InventedAnswersTest 8, ToolTextsTest +1 (`pinCountIsWrittenWithSpanishSeparators`).
- `strongPinCount` completo, sin atajos (JVM, mismo resultado en jvm-check y en Android): 4 cifras = 9126 (0,00 s), 5 = 95 970, 6 = 879 761 (0,02 s), 7 = 9 603 795 (0,15 s), 8 = 96 030 150 en 2,2 s (límite 30 s, en el test). Fuerza bruta contra `isWeakPin(String)` para 4, 5, 6 y también 7 cifras; el 8 solo se prueba en tiempo y rango (70 %-100 %), no por fuerza bruta (10^8 cadenas).
- Tests de seguridad: `rejectionSamplingKeepsLeadingZerosAndSkipsWeakDraws` (un `SecureRandom` guionizado: 1234 y 1111 se rechazan, 42 da «0042», solo se llama a `nextInt(10000)`, y `nextInt()` sin cota falla el test); `aRunWithAnyDuplicateIsDrawnAgain` (conjunto con un código repetido se descarta entero, guion de `nextInt(32)`); `uniqueWithoutAccents` (las 2048 formas sin tildes son distintas y ASCII, así que quitar tildes no pierde entropía); `spanishListIsPinned`; `generatedNeverWeak` (10 000 por longitud, 4 a 8).

## Archivos

Crear: `tools/codes/{Pin,RecoveryCodes,InventedAnswers}.kt`, `ui/screens/CodesScreen.kt`, `assets/bip39_spanish.txt`, tests `tools/codes/{PinTest,RecoveryCodesTest,InventedAnswersTest}.kt`.
Modificar: `tools/ToolTexts.kt`, `ToolTextsTest.kt`, `ToolsScreen.kt`, `AppNavHost.kt`, `LicensesScreen.kt`, `assets/licenses/bip39.txt`, `NOTICE`, `README.md`.

## Textos añadidos que no son del anexo (todos en `ToolTexts.kt` y en `ALL_TOOL_TEXTS`, así que pasan `ToolTextsTest`)

«PIN y códigos» y «PIN, códigos de recuperación y respuestas inventadas» (título y subtítulo, literales del brief), «PIN», «Longitud del PIN», «Calculando cuántos PIN quedan…», «Códigos de recuperación», «Copiar», «Copiar todos», «Otro PIN», «Otros códigos», «Guardar en la bóveda como nota», «Web o servicio», «Escribe la web o el servicio para dar nombre a la nota.», «Pregunta», «Desbloquea la bóveda para guardarlo. Lo que ves aquí se conserva mientras tanto.», «No guardar», «Guardado en la bóveda como nota.», «No se pudo guardar en la bóveda.», «Otra respuesta», «Añadir pregunta», «Guardar en la bóveda» (estos tres últimos del brief), y los títulos de nota (`recoveryNoteTitle`, `answersNoteTitle`). `pinCountText` formatea N con punto de millares y X con una decimal y coma. Ninguno usa palabras prohibidas.

## Auto-revisión

- Sin dependencias, permisos, red ni componentes nuevos; formatos intactos (no se tocó ningún test existente salvo añadir uno a ToolTextsTest).
- README: la frase del anexo está en español en la línea de BIP-39 («las listas de palabras BIP-39 en inglés y en español, MIT»), y añadí la herramienta nueva a la línea «Herramientas». `docs/privacidad.html` y la ficha de Play son del anexo «Cambios en los documentos» y no entran en esta tarea.
- Sin logs en `tools/codes/` ni en `CodesScreen.kt` (grep de `Log.`, `println`, `Timber`); sin `rememberSaveable`.

## Concerns

1. **1470 y 3690 (decisión mía, a revisar).** El brief lista las columnas como 1-4-7, 2-5-8-0 y 3-6-9, pero exige que `1470` sea débil. Con esas líneas `1470` no lo sería (el 0 no está bajo el 7). Añadí el 0 a las columnas laterales (`1-4-7-0` y, por simetría, `3-6-9-0`; `Pin.kt:27`). Resultado: `3690` y `0963` también son débiles. Si se prefiere solo `1470`, es quitar una cadena.
2. **El archivo de bitcoin/bips guarda las tildes como marcas combinadas (NFD):** 334 palabras. Se conserva byte a byte (SHA fijado) pero `inventedAnswer(plain = false)` compone a NFC, que es lo que teclea un teclado, para que el servicio compare igual (si no, «ábaco» guardado en NFD no coincidiría con lo que se teclea). Test: `exactFormKeepsAccentsComposedAsAKeyboardTypesThem`.
3. Al cambiar «Sin tildes» se sortean de nuevo todas las respuestas ya mostradas (no se pueden volver a poner tildes). Es lo que se ve, y eso es lo que se guarda.
4. Una respuesta son 4 palabras independientes de 2048 (44 bits); puede repetirse una palabra (probabilidad ~0,3 %). No se excluyen repeticiones para no sesgar la distribución; no se muestran los 44 bits porque el anexo no tiene texto para ello.
5. 8 cifras tardan 2,2 s en la JVM de escritorio; en un móvil lento será más. El texto «Calculando cuántos PIN quedan…» cubre la espera y el cálculo no bloquea el hilo principal. No hay cancelación a mitad del bucle (el resultado se guarda igualmente).
6. La parte de Compose (`CodesScreen`) compila y pasa lint, pero no se ha ejecutado en un dispositivo ni con tests instrumentados: sin probar a mano el flujo de bóveda bloqueada, rotar el móvil (se vuelve a sortear) y guardar.
7. Atribución del commit: usé el pie de `context.md` («Claude Opus 5.5»), como el resto de commits de la rama, no el del recordatorio de la sesión.

## Revisión experto-seguridad-cripto

Leído `SKILL.md` (y los principios de aleatoriedad/secretos que toca). Lista contra mi diff:

- **Aleatoriedad:** solo `secureRandom` de `crypto/Crypto.kt:104` (por defecto de los tres generadores; `Pin.kt:115`, `RecoveryCodes.kt:21`, `InventedAnswers.kt:21`). Siempre `nextInt(bound)` (que rechaza dentro, sin sesgo de módulo): `Pin.kt:120`, `RecoveryCodes.kt:17`, `InventedAnswers.kt:23`. El único `%` de `Pin.kt` es `v % 10` para extraer dígitos de un valor ya uniforme, y `(d[i] - d[i-1] + 10) % 10` para secuencias, ninguno sobre un Int aleatorio sin cota. PIN = muestreo por rechazo sobre `weakDigits` (`Pin.kt:119-126`), por lo que los PIN restantes son equiprobables. Test: `PinTest.rejectionSamplingKeepsLeadingZerosAndSkipsWeakDraws`.
- **Entropía:** código `32^10 = 2^50` (`RecoveryCodesTest.fiftyBitsEach`); respuesta `2048^4 = 2^44`; el PIN muestra los bits reales del conjunto que queda (`pinBits`, `Pin.kt:147`), no `log2(10^n)`. Alfabeto sin 0/O/1/I (`RecoveryCodes.kt:10`, `alphabetHasNoLookAlikes`).
- **Sin criptografía propia:** ninguna primitiva; solo `SecureRandom` y `java.text.Normalizer`. Sin dependencias nuevas.
- **Secretos en pantalla y en memoria:** `remember` y nunca `rememberSaveable` (`CodesScreen.kt:127-144`, grep sin coincidencias de `rememberSaveable`); `NoCopy` (`CodesScreen.kt:199`) quita Copiar/Cortar del menú de texto; salida solo por `copySensitive` (`CodesScreen.kt:215,244,291`: marca sensible y borrado automático). `FLAG_SECURE` ya lo pone `MainActivity`. Sin logs (grep de `Log.`, `println`, `Timber` en `tools/codes/` y `CodesScreen.kt`: nada). El `catch` de guardado no registra nada (`CodesScreen.kt:158-165`).
- **Bóveda:** guardado con `store.upsert` (`CodesScreen.kt:159`) como `NOTE`, sin tocar `password`, adjuntos ni historial; id nuevo `UUID.randomUUID()` (`CodesScreen.kt:148`). Bóveda bloqueada: `VaultGate` existente (`CodesScreen.kt:189`), que ya cuenta los toques para el bloqueo por inactividad y retira el contenido al bloquearse; `upsert` devuelve `false` si se bloqueó y se avisa («No se pudo guardar»). La nota queda cifrada por la bóveda; nada se escribe en claro.
- **Entrada no fiable:** el asset es propio y fijado por SHA-256 (`InventedAnswersTest.spanishListIsPinned`, 2048 líneas, únicas, y únicas sin tildes). Sin entrada externa nueva. `isWeakPin` rechaza lo que no sean 4-8 cifras ASCII (`Pin.kt:99`).
- **Licencias:** lista MIT de bitcoin/bips; `licenses/bip39.txt`, `LicensesScreen.kt:34`, `NOTICE:11` y README actualizados; `:app:checkLicense` y `:app:checkAssets` en verde (el asset es de `app/src/main/assets`).
- **Red/permisos/formatos:** ninguno nuevo ni cambiado; no hay componentes exportados nuevos.
- **Texto honesto:** el PIN aviso «no lo uses como contraseña» y «cada herramienta dice lo que no protege» vienen del anexo (`pinText`, `ANSWERS_LIMIT`, `ANSWERS_WARNINGS`); ninguna palabra prohibida (`ToolTextsTest.noForbiddenWords` en verde con los textos nuevos incluidos).
- **Riesgo residual (señalado, no bloqueante):** un PIN corto solo protege con bloqueo tras pocos intentos (lo dice el texto del anexo); las respuestas guardadas dependen de la contraseña maestra de la bóveda.

# Fix round 1 (revisión de b013f8d)

1. **Preguntas en blanco:** «Guardar en la bóveda» se desactiva mientras alguna pregunta esté en blanco, con el texto de ayuda `ANSWERS_BLANK_QUESTION` («Cada respuesta necesita su pregunta: escríbela o quita la pregunta que no vayas a usar.»). Para no dejar al usuario atrapado con una fila de más, cada fila (si hay más de una) lleva «Quitar pregunta» (`ANSWERS_REMOVE_QUESTION`). Ya no se descarta ninguna respuesta en silencio (`CodesScreen.kt`, `save`).
2. **Pregunta de una línea:** `singleLine = true` en el campo y, en `inventedAnswersNote`, todo salto de línea (`\R`: LF, CR, CRLF, NEL, U+2028/9) de la pregunta pasa a espacio; igual en la web de los títulos de las dos notas (`oneLine`, `InventedAnswers.kt:29`). Una «→» escrita se conserva. Tests: `lineBreaksInAQuestionOrSiteBecomeSpacesButAnArrowStays`, `aLineBreakInTheSiteNeverSplitsTheTitle`.
3. **Barridos del teclado:** cualquier PIN de 4 a 8 cifras que sea subcadena contigua de `1472583690`, `0963852741`, `3692581470` o `0741852963` es débil (`Pin.kt` `KEYPAD_SWEEPS`/`isKeypadSweep`, sin asignaciones). Se mantienen las líneas y concatenaciones anteriores. Test `keypadColumnSweepsCutToLengthAreWeak` (14725836, 14725, 36925, 1472, 8147, 58369, 963852, 0741852, 09638527, 25836, 74185296 débiles; 14725837, 14735, 36915, 47253 no). Las pruebas por fuerza bruta (4 a 7) usan la misma regla y siguen en verde. Nuevos recuentos: 4 = 9102, 5 = 95 946, 6 = 879 749, 7 = 9 603 783, 8 = 96 030 138 (4,0 s en la JVM, antes 2,2 s; límite 30 s).
4. **Tras guardar bien:** los códigos se sortean de nuevo y la web se vacía; en respuestas se vacían la web y las filas y queda una fila nueva en blanco con otra respuesta (`CodesScreen.kt`, `save`). Si falla el guardado no se borra nada.
5. **Sin notas duplicadas:** `saving` (se pone antes de lanzar y se baja en `finally`), `requestSave` ignora toques con `saving || pending != null`, y los dos botones de guardar se desactivan con `busy`. Vale también para el guardado que se hace al abrirse la bóveda desde el `VaultGate`.
6. **Snackbar sin bloquear:** se lanza en su propia corrutina (`scope.launch { snackbar.showSnackbar(...) }`) después de limpiar y antes de que `pending` deje la composición; el guardado ya no espera a que se descarte el aviso.
7. **Recuento de 8 cifras:** un único `Deferred` compartido por longitud (`pinCount(digits)` con `ConcurrentHashMap.computeIfAbsent` y un `CoroutineScope(SupervisorJob() + Default)` global): una segunda petición (otra visita, cambio rápido de longitud) espera a la primera y reutiliza el resultado.

Textos nuevos que no son del anexo (en `ToolTexts.kt` y `ALL_TOOL_TEXTS`): `ANSWERS_BLANK_QUESTION`, `ANSWERS_REMOVE_QUESTION`.

Verificación (una vez): `gradle -p jvm-check test` BUILD SUCCESSFUL, 831 tests, 0 fallos; `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` BUILD SUCCESSFUL, 831 tests, 0 fallos, lint 0 errores y 11 avisos (los de siempre).

Revisión experto-seguridad-cripto del arreglo: aleatoriedad intacta (`Pin.kt` sigue con `nextInt(bound)` y rechazo; los barridos solo ensanchan el conjunto de PIN rechazados, los restantes siguen siendo equiprobables); los secretos siguen en `remember`, sin logs; ahora además se retiran de la pantalla tras guardarlos (`CodesScreen.kt`, `save`); `oneLine` evita que una pregunta pegada con saltos de línea fabrique líneas falsas en la nota (`InventedAnswers.kt:29,38-39`); sin dependencias, permisos ni formatos nuevos. Pendiente como antes: la pantalla no se ha probado en dispositivo.

# Fix round 2 (re-revisión de a22c523)

- **Importante, recuento atascado:** pulsar la longitud ya elegida ya no pone `count = null`: solo se reinicia el recuento si `n != digits` (el efecto con clave `digits` es quien lo calcula); pulsarla de nuevo sigue sorteando un PIN nuevo (`CodesScreen.kt`, selector de longitud).
- **`Pin.kt`:** `KEYPAD_SWEEPS` es ahora `Array<IntArray>` y `isKeypadSweep` recorre con índices, sin iterador por llamada; el comentario «sin asignaciones» vuelve a ser cierto. `strongPinCount(8)` = 96 030 138 en 3,1 s (mismo resultado que antes).
- **Desbordamiento a 360 dp:** las tres filas de botones (PIN, códigos y cada respuesta) usan `FlowRow` (con `ExperimentalLayoutApi`).
- **Orden de imports:** `ANSWERS_REMOVE_QUESTION` va tras `ANSWERS_OTHER`.
- **Guardado desde el `VaultGate`, idempotente:** al encolar un guardado se construye ya la nota completa, con su id fijo (`PendingSave(kind, entry)`, `CodesScreen.kt:120`); lo que se escribe es siempre esa misma entrada, así que repetir la escritura (por ejemplo, un bloqueo y desbloqueo entre la escritura y el `pending = null`) sustituye la nota en vez de duplicarla ni de escribir los valores ya redibujados. La escritura (`write`) corre en el `scope` de la pantalla, con un solo guardado a la vez (`saving`), así que salir del gate no la cancela; y si se escribió (`upsert` devolvió true), siempre se limpian los valores de ese tipo, aunque el usuario hubiese salido del gate. «No guardar» se desactiva mientras se escribe. En el camino sin gate se usa la misma `write`. Si falla, no se borra nada y el gate se cierra con el aviso.

Verificación (una vez): `gradle -p jvm-check test` BUILD SUCCESSFUL, 831 tests, 0 fallos; `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` BUILD SUCCESSFUL, 831 tests, 0 fallos, lint 0 errores y 11 avisos (los de siempre).

Revisión experto-seguridad-cripto: sin cambios en aleatoriedad ni en las reglas del PIN (`Pin.kt` solo cambia la estructura del barrido); los secretos siguen en `remember`/estado del compositor y sin logs; la nota encolada contiene los códigos o respuestas en memoria hasta escribirse, igual que ya los tenía la pantalla, y se descarta con `pending = null`. Sin permisos, dependencias ni formatos nuevos. Sigue sin probarse en dispositivo (gate, rotación, 360 dp).
