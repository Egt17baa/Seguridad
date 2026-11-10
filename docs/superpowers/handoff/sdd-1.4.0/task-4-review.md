# Tarea 4: «Repartir un secreto» (SLIP-0039). Revisión de la tarea (556ed66..0b9cb46)

### Spec Compliance

- ✅ **Cumple la especificación**, con las desviaciones que R3/R3a y R4 aceptan. Las he comprobado una a una contra `slip-0039.md`:
  - **Paso 1, licencia y datos.** He descargado `shamir_mnemonic/wordlist.txt`, `vectors.json`, `LICENSE` y `slip-0039.md` (rama `master`) en el scratchpad.
    - `cmp wordlist.txt app/src/main/assets/slip39_english.txt` no da diferencias, y `cmp vectors.json app/src/test/resources/vectors/slip39.json` tampoco.
    - `diff LICENSE app/src/main/assets/licenses/slip39.txt` solo encuentra las dos líneas de cabecera añadidas (`slip39.txt:1-2`). El texto MIT y «Copyright 2019 SatoshiLabs» están intactos. La licencia es MIT, así que se permite.
    - La atribución está en `NOTICE:13-15`, `LicensesScreen.kt:38` y `README.md:155`.
  - **Interfaces.**
    - `GroupSpec` (`Slip39.kt:13`) y `Slip39Failure(message)` (`Slip39.kt:16`) existen.
    - `splitSecret(master, passphrase, groupThreshold, groups, iterationExponent = 1, random = secureRandom, check)` está en `Slip39.kt:91-132` y `combineShares(mnemonics, passphrase, check)` en `Slip39.kt:140-198`. Son métodos de `Slip39(words)`, como acepta R4.
  - **Paso 2, tests.** Están todos los que pide el brief:
    - `officialVectors`: 45 casos, 15 válidos, `Slip39Test.kt:44`;
    - `splitCombineEveryThreshold`: 1-de-1, 2-de-3 (cada par), 3-de-5 (cada trío) y 2-de-(2-de-3, 1-de-1), además de 16..32 bytes y 16×16;
    - `bip39RoundTrip` con 12, 15, 18, 21 y 24 palabras;
    - `tooFewShares`, `sharesFromTwoSplits`, `oneWordChanged` y `wrongPassphraseGivesAnotherSecret`;
    - `gf256TablesMatchSpec`.
  - **Paso 4, la norma.** Cada regla, con su evidencia:
    - **GF(256):** `0x11B` y generador 3 (`Gf256.kt:9`, `:14-22`).
    - **RS1024:** «shamir» y «shamir_extendable» (`Rs1024.kt:9-10`). El GEN es el de la norma, carácter a carácter. El `polymod` y `checksum` (`xor 1`, tres palabras big-endian) coinciden con el fragmento de Python de la especificación.
    - **Índices:** el secreto va en x = 255 y el digest en x = 254 (`Shamir.kt:9-10`). D = HMAC-SHA256(R, S)[:4] ‖ R (`Shamir.kt:35-40`, `:49-52`). Las T−2 partes aleatorias van en x = 0..T−3 y el resto se interpola en i (`Shamir.kt:55-58`), como en SplitSecret, pasos 3 a 5.
    - **Feistel:** 4 rondas, `(L, R) = (R, L xor F(i, R))`, con salida `R ‖ L` y el orden 3..0 al descifrar (`Slip39.kt:333-353`). Password = i (1 byte) ‖ P (`:356-358`). La sal es «shamir» ‖ id (2 bytes big-endian) con ext = 0, y vacía con ext = 1 (`:337`). Cada ronda hace `2500 << e` iteraciones (`:338`), 10000·2^e en total, y dkLen = n/2.
    - **Cabecera:** id de 15 bits, ext, e, GI, Gt−1, g−1, I y t−1, con los desplazamientos exactos (`Slip39.kt:238-247`, `:265-268`). El relleno va a la izquierda y es de ≤ 8 bits, todos a 0 (`:220-236`). El checksum se comprueba antes de leer ningún campo (`:216-219`).
    - **Al repartir:** ext = 1 (`:108`) y un id aleatorio de 15 bits con `SecureRandom` (`:105`). GT ≤ G, 1 ≤ Tᵢ ≤ Nᵢ ≤ 16 y Tᵢ = 1 ⇒ Nᵢ = 1 (`:100-103`). El secreto tiene ≥ 128 bits y un número par de bytes (`:100`).
    - **Al recomponer:** se exige que coincidan id, ext, e, GT, G y la longitud (`:159-165`), y G ≥ GT (`:166`). Además, GM = GT tanto si faltan grupos como si sobran (`:169-170`). Cada grupo tiene la misma Tᵢ, índices distintos y Mᵢ = Tᵢ (`:171-176`). Cada valor tiene ≥ 128 bits porque se piden al menos 20 palabras.
    - **Contraseña:** solo ASCII de 32 a 126, rechazado y nunca codificado (`Slip39.kt:322-326`).
  - **Comprobación independiente.** He escrito en Python, desde la especificación y sin la biblioteca (`scratchpad/scripts/vec.py`), un decodificador y combinador. Da los 45 vectores oficiales: 45 aciertos y 0 fallos, con casos de `ext` ∈ {0, 1}. También he ejecutado `gradle -p jvm-check test --tests 'app.seguridad.tools.slip39.Slip39Test'`: `BUILD SUCCESSFUL`, 21 de 21 PASSED, `officialVectors` incluido.
  - **R4.** Las cuatro desviaciones aceptadas son correctas:
    - la clase `Slip39(words)` con `check`;
    - el PBKDF2 de BC: `PKCS5S2ParametersGenerator(SHA256Digest())` con los bytes exactos de la contraseña, y `key.fill(0)` funciona porque `KeyParameter.getKey()` devuelve su propio array;
    - el tope e ≤ 8, comprobado antes de cualquier PBKDF2 (`Slip39.kt:104` y `:167`), y e = 1 al repartir, porque `split()` no pasa exponente (`SecretShareScreen.kt:242`);
    - hasta 64 bytes al recomponer (59 palabras: 520 bits, relleno de 8 y 512 bits de valor). La norma admite de 128 a 512 bits en múltiplos de 16, así que no la contradice.
  - **R3/R3a.** El aviso `SecretShareTexts.kt:23-25` es el texto de R3a palabra por palabra, y `PlatformSourcesTest` lo fija. Ninguna marca aparece fuera de NOTICE, README, `licenses/slip39.txt` y `LicensesScreen.kt:38`: lo he buscado en todos los archivos del diff. «TREZOR» solo está en `Slip39Test.kt:25`.
  - **Paso 6, la pantalla.**
    - Están los modos «Repartir» y «Recomponer», la frase o el hex, el umbral y los grupos, y la contraseña opcional.
    - El aviso va en su propia tarjeta, antes del botón y de las partes (`SecretShareScreen.kt:343-348`, antes de `:352`).
    - Las copias pasan por `copySensitive` (`:365` y `:410`). El estado vive en el ViewModel y lo borran `watchAway`, `GuardEffects`, `ToolColumn(..., guard)` y `onCleared` (`:142`, `:286`, `:288` y `:270`).
    - La entrada de Herramientas tiene el título y el subtítulo del brief (`ToolsScreen.kt`), y la ruta `tools/share-secret` está en `AppNavHost.kt`.
  - **Paso 7.** `checkAssets` exige `slip39_english.txt` (`app/build.gradle.kts:197`).
  - **Más allá del brief, con motivo.** `SecretShareInput`, `SecretShareRuns`, `SecretShareSession` y `SecretShareTexts` siguen el patrón de las tareas 2 y 3. `WordLists.bip39English()` cumple la regla de MasterPolicyTest: las listas viven en un solo sitio. No es sobreingeniería.
- ⚠️ **No se puede comprobar con el diff:**
  - El informe cuenta 16 avisos de lint frente a los 4 de la línea base, y dice que ninguno está en archivos de esta tarea (14 son de «versión más nueva», que lint ve con red). El controlador debe comparar con la línea base de la tarea 3 en el mismo entorno.
  - La prueba en el móvil sigue pendiente: un vector oficial, el tiempo con e = 8 y «Cancelar», y TalkBack.

### Strengths

- **La criptografía sigue la norma con rigor.** La he contrastado línea a línea con `slip-0039.md` y no he encontrado ninguna desviación fuera de R4. Mi implementación independiente en Python coincide en los 45 vectores.
- **El tiempo constante está bien resuelto donde importa.** `Gf256.mul` no tiene tablas ni ramas que dependan de los datos (`Gf256.kt:35-46`), y el test lo compara con las tablas en los 65.536 pares. Las tablas log/exp solo se indexan con x públicos (`Shamir.kt:22-27`). El digest y las partes repetidas se comparan con `MessageDigest.isEqual` (`Shamir.kt:76`, `Slip39.kt:68`).
- **El borrado es completo y cubre las excepciones con `finally`:**
  - L/R de cada ronda, la contraseña con su prefijo, la sal y la clave derivada (`Slip39.kt:342-351`, `:364-368`);
  - EMS, las partes de grupo y de miembro, y los valores decodificados (`Slip39.kt:110-131`, `:191-197`);
  - R, la etiqueta, D y head (`Shamir.kt:51-52`, `:62-63`, `:77-80`);
  - la entropía y los índices de BIP-39 (`Bip39.kt:73-83`, `:96-103`);
  - el secreto recuperado, al sustituirlo y al borrar (`SecretShareSession.kt:49` y `:57-66`).
- **(a) Entradas enormes.** Los límites se aplican sobre el `String` en bruto, antes de cualquier trabajo:
  - `limited` en cada campo;
  - `splitRequest` y `combineRequest` (`SecretShareInput.kt:101-103`, `:115-120`);
  - la biblioteca otra vez: 256 partes, 1.000 caracteres por parte, de 20 a 59 palabras, 256 caracteres de contraseña y e ≤ 8 (`Slip39.kt:141`, `:206`, `:210` y `:323`).

  Las repetidas se buscan en un `HashMap` en tiempo lineal. NFKC está acotado: 18 × 1.000 caracteres como mucho. PBKDF2 hace como mucho 4 × 640.000 iteraciones, corre en `Dispatchers.Default` y se cancela entre rondas.
- **(b) TOCTOU.**
  - La instantánea se toma una sola vez, al pulsar (`SecretShareScreen.kt:234`, `:252`).
  - `runId` sube con cada `stop()`, y un resultado tardío se rechaza (`SecretShareRuns.kt:31-36`, `:48` y `:76`). El secreto recuperado que se descarta se pone a cero, también cuando `withContext` se lo traga por la cancelación (`produced`, `:83`).
  - `cleanup()` solo borra la instantánea cuando el bloque de `withContext` ha terminado, porque `withContext` espera a que acabe su bloque. No hay una ventana en la que se borre algo que PBKDF2 todavía está leyendo.
  - Los seis tests de `SecretShareRunsTest` cubren el borrado, la cancelación, el doble toque y el `Slip39` real.
- **Unicode.**
  - Los sustitutos sueltos se rechazan antes de normalizar (`Slip39.kt:310-314`, y `'?'` en `SecretShareInput.kt:47`).
  - Se aplica NFKC, se quitan los caracteres Cf (con la comprobación RS1024 detrás) y se usa `lowercase(Locale.ROOT)`.
  - Los homoglifos cirílicos y las letras con acento se rechazan (`unknownWordsAreNamedByPositionOnly`). Ningún mensaje lleva palabras.
- **`Bip39.entropyOf` y `phraseOf` están bien para 12 a 24 palabras:** CS = n/3, `hash[0] >> (8−CS)` y los vectores publicados («abandon…about», «…art», «zoo…wrong»). Además, `checksumOk` ya no pasa por un `String` de bits.
- **La comprobación de NoCopy.** Los `SelectionContainer` de las partes y del resultado (`SecretShareScreen.kt:364` y `:409`) están dentro del `NoCopy` de `ToolColumn` (`SecurityToolsScreen.kt:219`). Así, el «Copiar» del sistema no esquiva `copySensitive`.

### Issues

#### Critical (Must Fix)
Ninguno.

#### Important (Should Fix)

**I-1. «Como frase BIP-39» se muestra para cualquier secreto de 16 a 32 bytes, también el de unas partes que no vienen de una frase BIP-39.** Evidencia: `SecretShareInput.kt:72-73`, `SecretShareScreen.kt:393` y `:398-401`, y `SecretShareTexts.kt:78`.
- **El problema.** Una cartera de hardware guarda en sus partes SLIP-39 la semilla maestra BIP-32 de 16 o 32 bytes, porque `slip-0039.md` (§«Usage», línea 305) dice que DEBE hacerlo. Si alguien recompone esas partes en Clavero, la herramienta le enseña una frase BIP-39 de 12 o 24 palabras, válida y con su checksum. Esa frase no es la de su cartera: metida en una cartera BIP-39, daría otra cartera distinta. Es el mismo problema del aviso de R3, en sentido contrario.
- **Por qué importa.** El aviso invita a recomponer en Clavero («Recomponlas en Clavero…»), y la nota del resultado (`SHARE_RESULT_NOTE`) solo habla de la contraseña. Quien migre de cartera puede tomar esa frase por su copia y destruir las partes. Además, la regla de veracidad del proyecto se aplica a lo que la pantalla da a entender. Es un hallazgo de seguridad confirmado, y por eso es Important como mínimo, según la sección «Trabajo conjunto con superpowers» de `experto-seguridad-cripto`.
- **Arreglo.** Una nota fija bajo la frase y antes de «Copiar». Una propuesta, para el Abogado Digital: «Esta es la frase BIP-39 de estos bytes. Si las partes vienen de una cartera, el secreto es su semilla y esta frase daría otra cartera distinta: usa la frase solo si repartiste una frase BIP-39.» Otra opción es esconder la frase tras un «Mostrar como frase BIP-39» con esa nota. El código no puede distinguir el origen, así que la solución es de texto. Hay que añadir un test de fuente en `PlatformSourcesTest` que fije la nota junto a `Output(context, phrase)`.

#### Minor (Nice to Have)

**M-1. Los límites de «Recomponer» no cuadran entre sí.** `SHARE_MAX_PARTS_CHARS = 40_000` (`SecretShareInput.kt:10-11`) frente a `SLIP39_MAX_PARTS = 256` (`Slip39.kt:27`).
- **El problema.** 256 partes de 33 palabras, que es el caso de un secreto de 256 bits, ocupan unos 64.000 caracteres. El propio comentario reconoce que caben unas 160 partes de 33 palabras y unas 70 de 59. Solo afecta a esquemas muy grandes, como 16 grupos de 16-de-16 con 256 bits, que en la práctica no existen.
- **Arreglo.** Subir el límite a unos 70.000 caracteres o, si no, decir en el KDoc de `SLIP39_MAX_PARTS` que la interfaz recorta antes.

**M-2. Los vectores inválidos solo comprueban que salga algún `Slip39Failure`, no cuál** (`Slip39Test.kt:54-56`).
- **El problema.** Un rechazo por el motivo equivocado pasaría el test. Por ejemplo, el caso 3, «invalid padding», podría caer en el checksum por un error de decodificación.
- **Arreglo.** Un mapa del nombre del caso al mensaje esperado:
  - 2 y 21: `slip39Checksum`;
  - 3 y 22: `slip39BadLength`;
  - 6 a 9 y 25 a 28: `SLIP39_NOT_SAME_SET`;
  - 10 y 29: `SLIP39_MORE_GROUPS_THAN_COUNT`;
  - 11 y 30: `slip39SameIndex`;
  - 12 y 31: `slip39ThresholdMismatch`;
  - 13 y 32: `SLIP39_DIGEST_MISMATCH`;
  - 14 y 15, y 33 y 34: `slip39TooFewGroups`;
  - 16 y 35: `slip39WrongCount`;
  - 39: `slip39WordCount`;
  - 40: `slip39BadLength`.

**M-3. `splitInput` llama a `bip39.check(clean)`, que calcula `suggest()` para cada palabra desconocida, aunque solo se usa la posición de la primera** (`SecretShareInput.kt:34`, `Bip39.kt:43`).
- **El problema.** Corre en el hilo principal, al pulsar «Repartir». Con el tope de 1.000 caracteres son unos pocos milisegundos, sin riesgo de ANR, pero es trabajo que se tira.
- **Arreglo.** Buscar la primera palabra desconocida con `normalize` y el índice, y después llamar a `entropyOf`.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** SLIP-0039 está implementado como dice la norma: los 45 vectores pasan en Kotlin y en una reimplementación independiente en Python. El tiempo constante, el borrado, los límites y el rechazo de resultados tardíos están bien hechos y probados. Queda I-1: la pantalla presenta como «frase BIP-39» el secreto de cualquier conjunto de partes, y puede engañar a quien recomponga las de una cartera. Se arregla con una nota.

---

## Revisión experto-seguridad-cripto

He leído `SKILL.md` («Trabajo conjunto con superpowers»: un hallazgo de seguridad confirmado es Important como mínimo). Modelo de amenaza: alguien que manipula una parte (cabecera falsa, e = 15, relleno, x = 0), un pegado hostil (tamaño, Unicode), un bloqueo o una salida a mitad del cálculo, y el portapapeles.

**Primitivas.** Solo se usan primitivas estándar:
- HMAC-SHA256 de `javax.crypto.Mac` (`Shamir.kt:35-40`);
- PBKDF2-HMAC-SHA256 de BC (`Slip39.kt:361-364`);
- SHA-256 de `MessageDigest` (`Bip39.kt:71`, `:92`).

GF(256), Shamir, RS1024 y Feistel son la construcción que fija la norma, no criptografía propia. Las he validado contra los vectores en Kotlin (ejecución propia: 21 de 21) y en Python (45 de 45, `ext` 0 y 1).

**Aleatoriedad.** Todo sale de `SecureRandom`: el id (`Slip39.kt:105`), R y las partes aleatorias (`Shamir.kt:49`, `:54`). No hay ningún `java.util.Random`.

**Índice 0 y digest.** El secreto va en 255 y el digest en 254 (`Shamir.kt:9-10`). Los índices de miembro y de grupo son de 0 a 15 y nunca coinciden con 254 ni con 255, así que `Gf256.log` nunca recibe 0 (`Shamir.kt:22-26`). El ataque de x = 0 de la norma (nota 3) no es posible. El digest se compara en tiempo constante (`Shamir.kt:76`), y una parte con un valor cambiado y el checksum recalculado da `SLIP39_DIGEST_MISMATCH` (`combineChecksTheRulesOfTheFormat`).

**Partes manipuladas.**
- Con e = 15, se rechaza antes de PBKDF2 (`Slip39.kt:167`, `theIterationExponentIsCapped`, en menos de 2 s).
- Con GT > G, se rechaza (`:166`).
- Un relleno de más de 8 bits o con algún bit a 1 se rechaza (`:222`, `:234`; vectores 3 y 22).
- Una cabecera solo se lee con el checksum válido (`:216-219`).

**Tiempo constante.**
- `Gf256.mul` (`Gf256.kt:35-46`) es la única multiplicación con datos secretos.
- `sameAs` cortocircuita en campos públicos y compara el valor con `isEqual` (`Slip39.kt:66-68`).
- Riesgo residual que acepto, sin hallazgo: `Rs1024.polymod` ramifica según los bits de las palabras (`Rs1024.kt:21`), y `Bip39.entropyOf` compara el checksum con `!=` (`Bip39.kt:77`). Es un cálculo local, sin un oráculo remoto. Solo revela lo que el usuario ya ve (que el checksum falla) o requiere medir la CPU del propio móvil.

**Contraseña.**
- Solo ASCII de 32 a 126, comprobado en la biblioteca (`Slip39.kt:324`) y probado con ñ, é, tab, NUL, €, emoji, un sustituto suelto y 0x7F (`passphraseMustBePrintableAscii`).
- Una contraseña equivocada da otro secreto: el test lo documenta y la pantalla lo dice (`SecretShareTexts.kt:13-15`, `:79-80`).

**Borrado.** Está completo (ver «Strengths»). Lo que no se puede borrar, y el informe lo reconoce, son los `String` de Compose (campos, partes generadas, hex y frase mostrados), la copia de la clave de `SecretKeySpec` y el estado interno de HMac/BC.

**Logs y mensajes.**
- No hay `Log`, `println` ni `printStackTrace` en `tools/slip39`, en `SecretShareScreen.kt` ni en `Bip39.kt` (lo he buscado con grep).
- Los mensajes de `Slip39Failure` solo llevan posiciones y cuentas, nunca palabras ni bytes (`SecretShareTexts.kt:83-106`, `unknownWordsAreNamedByPositionOnly`).
- `require` en `encode` y `phraseOf` no lleva datos.

**Portapapeles.** Solo `copySensitive` (`SecretShareScreen.kt:365`, `:410`), y el «Copiar» del sistema está bloqueado por `NoCopy` (`SecurityToolsScreen.kt:219`).

**Hallazgos de seguridad:**
- I-1 (Important): la frase BIP-39 mostrada al recomponer puede tomarse por la de una cartera.
- M-1, M-2 y M-3 son de calidad, no de seguridad.

## Revisión Ingeniero Móvil Senior

He leído `desarrollo-movil-senior`, `seguridad-criptografia-movil` y `auditoria-seguridad-movil` (MASVS: STORAGE, CRYPTO, PLATFORM, CODE).

**Ciclo de vida.**
- El estado vive en `SecretShareViewModel` (`SecretShareScreen.kt:113-273`), sin `rememberSaveable` ni un `Bundle`. `remember(secret)` (`:392-393`) es una copia de pantalla, no estado guardado.
- `session` se declara antes que `runs`, y `watchAway` va en el `init` que sigue a los dos (`:120-142`), así que el `{ runs.stop() }` de `onWipe` no puede verse sin inicializar.
- Volver atrás llama a `onCleared`, y este a `session.wipe()` (`:270-272`). Un bloqueo, otra app o el tiempo fuera pasan por `watchAway`, `GuardEffects` y `usedFor(guard)` (`:142`, `:286` y `:288`), y teclear cuenta como uso (`guard.typed()`, `:169`).

**Concurrencia.**
- Una sola ejecución a la vez. `runId` y `job` solo se tocan en el hilo principal, porque `viewModelScope` usa `Main.immediate` y el `finally` vuelve a él.
- PBKDF2, la decodificación y NFKC corren en `Dispatchers.Default` (`SecretShareRuns.kt:47`, `:75`), y las listas se leen en IO (`SecretShareScreen.kt:145-153`, `MasterPolicy.kt:110`).
- `ensureActive()` lanza `CancellationException`, que atraviesa `Slip39` porque solo hay `finally` y ningún `catch (Exception)`. En el `init`, el `catch` de `IllegalArgumentException` y `IOException` no se traga esa excepción, que deriva de `IllegalStateException`.
- Al cancelar se espera, como mucho, una ronda: con e = 8, unas 640.000 iteraciones, unos 0,5 a 1,5 s en un móvil de gama media, que hay que confirmar en el móvil.

**Rendimiento.**
- Todos los campos tienen límite. Como mucho se pintan 256 partes en la `Column` desplazable.
- En el hilo principal solo corren `splitRequest` y `combineRequest`, con SHA-256 y `lines()` sobre 40.000 caracteres como mucho. M-3 es el único trabajo de más.

**Plataforma y almacenamiento.**
- `KeyboardType.Password` sin autocorrección en los campos secretos (`:276`), y `PasswordField` para la contraseña.
- `FLAG_SECURE` es global. Las partes y el resultado están dentro de `NoCopy` y no se escribe nada en disco.
- Los vectores de prueba quedan fuera del APK (`src/test/resources`), y `checkAssets` exige la lista (`build.gradle.kts:197`).

**Interfaz.**
- Material 3: `FilterChip`, `SectionCard`, «Calculando… Cancelar» mientras trabaja y el teclado numérico en los números.
- El aviso R3 va en su tarjeta antes del botón, y «Lo que no protege» al principio (`:290-295`).

**Hallazgo de Android:** ninguno más allá de I-1 (texto) y M-3 (trabajo de más en el hilo principal).

## Textos para el Abogado Digital (revisión final)

Todos los textos visibles nuevos están en `app/src/main/java/app/seguridad/tools/slip39/SecretShareTexts.kt`, salvo el de Licencias (`LicensesScreen.kt:38`). He comprobado que el informe del implementador los recoge todos y que coinciden con el código. Para la revisión final:

**Entrada y cabecera.**
- `SHARE_TITLE`: «Repartir un secreto».
- `SHARE_SUBTITLE`: «Divide una frase o un secreto en partes (SLIP-0039)».
- `SHARE_INTRO`: «Divide una frase BIP-39 o un secreto en hex en varias partes, con el reparto de secretos de Shamir de SLIP-0039. Con las partes necesarias se recompone el secreto; con menos, no. Todo se calcula en tu móvil y nada se guarda.»
- `SHARE_NOT_PROTECTED_TITLE`: «Lo que no protege».
- `SHARE_NOT_PROTECTED`: «Quien reúna las partes necesarias obtiene el secreto, sea quien sea; si pusiste contraseña, le hace falta también. Guarda cada parte en un sitio distinto. Una contraseña equivocada no da ningún error: da otro secreto distinto, sin avisar. Y estas partes no son una copia SLIP-39 de cartera: lee el aviso.»
- `SHARE_WARNING_TITLE`: «Aviso».
- `SHARE_WARNING` (R3a, palabra por palabra): «Estas partes no son una copia SLIP-39 de cartera. Metidas en una cartera de hardware como recuperación SLIP-39 darían otra cartera distinta. Recomponlas en Clavero o con una herramienta SLIP-0039 que devuelva la entropía.»

**Modos y entrada.**
- Modos y tipos de entrada: «Repartir», «Recomponer», «Frase BIP-39» y «Secreto en hex».
- Etiquetas de los campos: «Frase BIP-39 (12, 15, 18, 21 o 24 palabras en inglés)» y «Secreto en hex (de 16 a 32 bytes, en número par)».
- `SHARE_PHRASE_NOTE`: «Se reparte la entropía de la frase, y al recomponer sale la misma frase. Una passphrase BIP-39 (la «palabra 25») no entra en el reparto: guárdala aparte.» Lleva el anglicismo «passphrase».
- Errores de la frase BIP-39:
  - «Una frase BIP-39 tiene 12, 15, 18, 21 o 24 palabras.»
  - «La palabra N no es de la lista BIP-39 en inglés.»
  - «La frase no cuadra con su suma de control: revisa las palabras.»
- Errores del hex y de la entrada:
  - «El secreto en hex tiene que ser de 16 a 32 bytes en número par: de 32 a 64 cifras hex, en múltiplos de 4.»
  - «El secreto es demasiado largo.»
  - «Hay demasiado texto en las partes.»
  - «No se pudo leer la lista de palabras de la app.»

**Grupos.**
- Etiquetas: «Grupos necesarios», «Grupo N», «Partes necesarias», «Partes», «Añadir grupo» y «Quitar grupo».
- «Lo normal es un solo grupo: por ejemplo, 2 partes necesarias de 3. Con varios grupos, hacen falta las partes necesarias de tantos grupos como diga «Grupos necesarios».»
- «Escribe un número del 1 al 16 en cada casilla.»

**Contraseña.**
- «Contraseña (opcional)».
- «Solo caracteres ASCII imprimibles, como pide SLIP-0039: letras sin acentos ni eñes, números, espacios y signos. Para recomponer hace falta la misma; con otra sale un secreto distinto.»

**Botones y estado:** «Repartir», «Recomponer», «Calculando…», «Cancelar», «Copiar» y «Ese texto es demasiado largo y no se ha pegado.»

**Partes y resultado.**
- Cabeceras de las partes:
  - «Grupo N: 1 parte» / «Grupo N: hacen falta T de estas N partes»;
  - «Parte N»;
  - «Un solo grupo.» / «Hacen falta las partes de T de los G grupos.»
- «Partes, una por línea».
- «Escribe o pega las partes necesarias, cada una en su línea. Mayúsculas y espacios de más no importan.»
- Resultado: «Secreto en hex» y «Como frase BIP-39».
- «Si la contraseña no es la que se usó al repartir, este es otro secreto distinto: SLIP-0039 no avisa.»
- **Texto nuevo que propone I-1, para validar:** «Esta es la frase BIP-39 de estos bytes. Si las partes vienen de una cartera, el secreto es su semilla y esta frase daría otra cartera distinta: usa la frase solo si repartiste una frase BIP-39.»

**Mensajes de error (`Slip39Failure`).**
- Partes:
  - «Hay demasiadas partes: como mucho 256.»
  - «Escribe al menos una parte.»
  - «La parte N es demasiado larga.»
  - «La parte N tiene C palabras: una parte tiene de 20 a 59.»
  - «La palabra P de la parte N no es de la lista SLIP-39.»
  - «La parte N tiene alguna palabra mal: su suma de control no cuadra.»
  - «La parte N no tiene una longitud válida.»
- Conjunto de partes:
  - «Las partes no son del mismo reparto: no coinciden su identificador, sus grupos o su longitud.»
  - «Las partes piden más grupos de los que tiene el reparto.»
  - «Hacen falta partes de X grupos y hay de Y.»
  - «Sobran partes: hacen falta partes de X grupos y hay de Y.»
  - «Las partes del grupo N no coinciden en cuántas hacen falta.»
  - «Hay dos partes distintas con el mismo número en el grupo N.»
  - «En el grupo N hacen falta X partes y hay Y.»
  - «Las partes no recomponen el secreto: alguna está cambiada o es de otro reparto.»
- Cálculo y contraseña:
  - «El cálculo pedido es demasiado largo: Clavero acepta un exponente de iteraciones de 0 a 8.»
  - «La contraseña solo puede llevar caracteres ASCII imprimibles: letras sin acentos ni eñes, números, espacios y signos.»
  - «La contraseña es demasiado larga: como mucho 256 caracteres.»
- Configuración del reparto:
  - «El secreto tiene que tener de 16 a 32 bytes, en número par.»
  - «Hacen falta de 1 a 16 grupos, y los grupos necesarios tienen que ser de 1 al número de grupos.»
  - «En cada grupo, las partes tienen que ser de 1 a 16, y las necesarias, de 1 al número de partes.»
  - «Un grupo que pide 1 parte solo puede tener 1 parte: si quieres más copias, copia esa misma parte.»

**Licencias** (`LicensesScreen.kt:38`): «Lista de palabras SLIP-0039», «MIT» y la URL `https://github.com/trezor/python-shamir-mnemonic`. El detalle: «Las 1.024 palabras en inglés de SLIP-0039, sin cambios, de python-shamir-mnemonic (Copyright 2019 SatoshiLabs). «Repartir un secreto» las usa para las partes. Solo se usan los datos, no el código.»

**Documentos:**
- el párrafo «Lista de SLIP-0039» del README (`README.md:155`);
- la entrada de `NOTICE` (`:13-15`);
- la cabecera en inglés de `assets/licenses/slip39.txt` (`:1`).

**Notas para el Abogado:**
- Se mezclan «SLIP-39» y «SLIP-0039»: R3a usa las dos.
- Las marcas solo aparecen en la atribución de la licencia, como pide R3a.
- Ningún texto usa palabras prohibidas (`ForbiddenWords`) ni «wallet».
