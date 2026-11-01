# Informe de la tarea 4: CRY-A03, poner a cero las copias de la clave en los parámetros de Bouncy Castle

**Estado:** DONE_WITH_CONCERNS (una corrección al informe de la auditoría sobre ChaCha20-Poly1305; ver «Preocupaciones»).
**Commit:** `490e973` «crypto: borrar la copia de la clave en los parámetros de Bouncy Castle (CRY-A03)», en `ccr-a1a6eabb-765f9g`, ya subido (`d0702a3..490e973`). Autor `Claude <noreply@anthropic.com>`; el mensaje acaba solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (`grep -cE "Claude-Session|claude\.ai"` da 0).

## Qué se ha implementado

Sin cambiar ningún formato ni la firma pública de `aead(...)`. Los bytes de salida no cambian (lo prueban los vectores de `CryptoTest`, `DialectTest`, `SeedCryptoTest` y `vectors/`, todos en verde sin tocarlos).

- `crypto/Crypto.kt:262-277`, `blockGcm` (Twofish, Serpent y Camellia en GCM): `val kp = KeyParameter(key)` fuera del `try`, `AEADParameters(kp, …)` dentro, y `finally { kp.key.fill(0) }` (`:275`). Cubre éxito, `CryptoFailure` por tag y cualquier otra excepción (por ejemplo `IllegalArgumentException` por un nonce vacío). `blockGcm` pasa de `private` a `internal` (misma firma) para que el test lo llame con un motor espía.
- `crypto/Crypto.kt:337-351`, `chaChaPoly(kp, encrypt, nonce, aad, input)`, nuevo, `internal`: el cuerpo que estaba en la rama de `aead`, ahora con `finally { kp.key.fill(0) }` (`:349`). Recibe el `KeyParameter` y lo vacía («se queda con él»), para que el test observe el parámetro real. `aead` lo llama en `:329`: `chaChaPoly(KeyParameter(key), encrypt, nonce, aad, input)`.
- Comentarios (en inglés, como el resto del archivo) que explican por qué basta un `fill(0)`: `getKey()` devuelve el array interno y `GCMBlockCipher.lastKey` es el mismo array; y qué no se puede borrar.
- No se tocan: AES con `SecretKeySpec` (`:322`), `HKDFParameters` (`:282`, `:405`), nada de CriptoPanel, ningún texto de la interfaz.

**Por qué dos estilos de prueba (GCM con motor espía, ChaCha con el parámetro propio):** en GCM, `GCMBlockCipher.init` pasa al motor el `KeyParameter` que le dieron (`cipher.init(true, keyParam)`), así que un `BlockCipher` espía (`KeySpy`, `CryptoTest.kt:41`) recibe exactamente el objeto que construyó `blockGcm`, sin cambiar su firma ni añadir ganchos de prueba al código de producción. `ChaCha20Poly1305` no deja inyectar nada equivalente (su motor ChaCha es privado), así que ahí el ayudante recibe el `KeyParameter` (como proponía el encargo).

## Tests y cifras

6 tests nuevos, todos en `CryptoTest.kt` (`:429-561`), sin tocar los existentes:

1. `bouncy castle KeyParameter keeps its own copy … same array` (`:431`): `KeyParameter(k).key` no es `k`, es la misma instancia en dos llamadas, y vaciarla no toca `k`. Si Bouncy Castle cambia, avisa de que el borrado ya no sirve.
2. `GCMBlockCipher keeps the same array … as its last key` (`:443`): por reflexión, `GCMBlockCipher.lastKey` es el mismo array (`assertSame`) que `kp.key`. Es la suposición que justifica que un solo `fill(0)` borre las dos copias; si el campo desaparece o cambia, el mensaje dice qué volver a comprobar.
3. `GCM over Twofish, Serpent and Camellia wipes the KeyParameter it built …` (`:456`): para cada motor, cifrar y descifrar con `KeySpy`; el `KeyParameter` observado queda a cero, la clave del llamador queda intacta, el resultado es igual al de `aead`, y el descifrado devuelve el texto.
4. `… also when the tag fails` (`:487`): para cada motor, cuatro fallos (un bit del cuerpo, un bit del tag, AAD distinta, clave distinta) dan `CryptoFailure` y el `KeyParameter` queda a cero; la clave del llamador no cambia.
5. `ChaCha20-Poly1305 wipes the KeyParameter it is given …` (`:515`): con la clave y el nonce del RFC 8439, cifrar, descifrar, tag roto y clave equivocada dejan el `KeyParameter` a cero; los bytes son los de `aead`.
6. `aead never touches the key array it is given, with any algorithm` (`:543`): los seis algoritmos (AES, ChaCha, Twofish, Serpent, Camellia, cascada): la clave del llamador queda igual tras cifrar, descifrar y tras un tag roto; mismos bytes en dos cifrados. Protege contra el error que sería grave (vaciar la clave del llamador).

Verificaciones globales, una vez antes del commit, con `--no-daemon --max-workers=1 --offline`:
- `gradle -p jvm-check test`: BUILD SUCCESSFUL, **1257 pasan, 0 fallos** (antes 1251; +6).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, **1262 tests, 0 fallos, 0 errores, 0 omitidos** (antes 1256; +6).

## Evidencia TDD

RED: para que fallara por comportamiento y no solo por compilación, antes escribí el esqueleto sin lógica (`blockGcm` `internal`, `chaChaPoly` sin el `finally`). Con los 6 tests:

```
$ gradle -p jvm-check test --tests '*CryptoTest*' --offline --no-daemon --max-workers=1
CryptoTest > bouncy castle KeyParameter keeps its own copy of the key and getKey returns that same array PASSED
CryptoTest > GCMBlockCipher keeps the same array as the KeyParameter as its last key PASSED
CryptoTest > aead never touches the key array it is given, with any algorithm PASSED
CryptoTest > GCM over Twofish, Serpent and Camellia wipes the KeyParameter it built, on encrypting and on decrypting FAILED
    AssertionError: TWOFISH_256_GCM encrypt is not all zeros: 070A0D101316191C1F2225282B2E3134373A3D404346494C4F5255585B5E6164
CryptoTest > GCM over Twofish, Serpent and Camellia wipes the KeyParameter also when the tag fails FAILED
    AssertionError: Twofish/body is not all zeros: 070A0D10…
CryptoTest > ChaCha20-Poly1305 wipes the KeyParameter it is given, on encrypting, on decrypting and when the tag fails FAILED
    AssertionError: encrypt is not all zeros: 808182838485868788898A8B8C8D8E8F909192939495969798999A9B9C9D9E9F
45 tests completed, 3 failed
```

Los tres que pasan en rojo son los que fijan suposiciones (1, 2) y la salvaguarda de la clave del llamador (6): deben pasar antes y después.

GREEN: tras el `finally`, `gradle -p jvm-check test --tests '*CryptoTest*' --tests '*DialectTest*' --tests '*SeedCryptoTest*'` dio BUILD SUCCESSFUL, con los 6 nuevos en PASSED.

Comprobación de que el test del tag es sensible (mutación, ya revertida): con el `fill(0)` solo en la rama de éxito (sin `finally`), «also when the tag fails» falla con `Twofish/body is not all zeros`. Sin el borrado en ChaCha, el test 5 falla con `encrypt is not all zeros`.

## Archivos cambiados

- `app/src/main/java/app/seguridad/crypto/Crypto.kt` (+28 −7 aprox.: `blockGcm`, `chaChaPoly`, rama de `aead`).
- `app/src/test/java/app/seguridad/crypto/CryptoTest.kt` (+161: imports, `KeySpy`, `assertWiped`, 6 tests).

## Autorrevisión

- La clave del llamador nunca se vacía: `kp.key` es la copia de Bouncy Castle (test 1) y el test 6 lo comprueba con los seis algoritmos y con el tag roto.
- El `finally` está en todas las salidas, incluidas las excepciones distintas de `InvalidCipherTextException`; el `KeyParameter` se crea antes del `try`, así que no hay camino en el que exista sin que haya un `finally` que lo vacíe.
- Vectores: `CryptoTest` (NIST caso 16, RFC 8439, RFC 9106), `DialectTest`, `SeedCryptoTest` ("block ciphers match published vectors", "texts from 1_9_0…") y los `vectors/` pasan sin cambios; los bytes de `aead` no cambian.
- Hermética: sin dependencias, permisos, red ni componentes nuevos. Sin logs. Sin textos de interfaz, así que no hay nada para el Abogado Digital.
- `blockGcm` y `chaChaPoly` son `internal` (no públicas); la firma pública de `aead` es la misma.
- Búsqueda de otros `KeyParameter(` en `app/src/main`: solo estos dos usos en `Crypto.kt` (`Identity.kt` usa `HKDFParameters` y `Ed25519PrivateKeyParameters`, otros hallazgos).

## Preocupaciones

1. **Corrección al informe de la auditoría (importante para el informe final).** La auditoría dice «Lo mismo para ChaCha20-Poly1305 (`:319-325`)», pero solo es cierto en parte. Con `javap` sobre `bcprov-jdk18on-1.86.jar`, `ChaCha20Poly1305.init` hace `KeyParameter.copyTo(this.key, 0, 32)`: guarda **otra copia** de la clave en su campo privado `key`, que no es el array del `KeyParameter`. Lo confirmé con una prueba aparte (fuera del repositorio): tras vaciar `kp.key`, `ChaCha20Poly1305.key` sigue valiendo `0102…20` y `own == kp.getKey()` da `false`. Por tanto en ChaCha el `fill(0)` quita **una** copia (la del `KeyParameter`), no todas. En GCM sí es el mismo array (`lastKey`, test 2). No he usado reflexión para vaciar el campo privado (frágil y con R8, y fuera de la regla «solo APIs estándar»). El comentario de `chaChaPoly` lo dice así.
2. **Fuera del alcance, solo una observación:** si el tag falla al descifrar, el búfer `out` con el texto en claro parcial no se pone a cero en ninguno de los dos caminos de Bouncy Castle (el `catch` lo suelta). No es una clave y no es CRY-A03; si el controlador lo quiere, es un cambio pequeño (`out` fuera del `run`) para otra tarea.
3. El test 2 usa reflexión sobre un campo privado de Bouncy Castle (`lastKey`). En JVM 21 con Bouncy Castle en el classpath funciona; si una versión futura lo renombra, falla con un mensaje que lo explica, que es lo que se busca.

## Frase de riesgo residual para la auditoría final

> Las claves pasan por `SecretKeySpec` (AES-256-GCM vía Conscrypt, que además guarda su propia copia en el `Cipher`) y por `HKDFParameters` (que clona la IKM), y los motores de Bouncy Castle expanden subclaves (Twofish, Serpent, Camellia, ChaCha) y `ChaCha20Poly1305` guarda una copia más en un campo privado. No hay API para ponerlas a cero y quedan en el heap hasta el recolector, también tras bloquear; verlas exige volcar la memoria del proceso (root o depurador), y esa misma capacidad permitiría esperar al siguiente desbloqueo. Desde la 1.3.1 se borra la copia del `KeyParameter` (y, en GCM, la que comparte `GCMBlockCipher.lastKey`) en todas las salidas, también con tag fallido. Se descartó pasar AES a la implementación de Bouncy Castle: se perdería la aceleración por hardware y la resistencia a los canales laterales de tiempo.

## Revisión experto-seguridad-cripto

Referencias leídas: `SKILL.md` y `references/cripto-moderna.md` (§2 AEAD, §10 antipatrones).
- Sin criptografía propia: solo `GCMBlockCipher`, `ChaCha20Poly1305` y `KeyParameter` de Bouncy Castle 1.86. Mismos parámetros que antes: tag de 128 bits (`TAG_BYTES * 8`, `Crypto.kt:266`, `:340`), nonce y AAD sin cambios.
- Higiene de secretos («`fill(0)` en cada salida, también en las ramas de error»): `Crypto.kt:275` (éxito, `CryptoFailure` y cualquier otra excepción) y `Crypto.kt:349` (ídem). El test 4 (`CryptoTest.kt:487`) cubre el tag roto en el cuerpo, en el tag, con AAD distinta y con clave distinta; el 5 (`:515`), lo mismo en ChaCha.
- Compatibilidad: ningún formato cambia; los vectores NIST (GCM caso 16), RFC 8439 y RFC 9106 y los datos reales de `vectors/` siguen en verde; el test 3 compara `blockGcm` con `aead`.
- Suposiciones de la biblioteca fijadas por tests: `getKey()` es la misma instancia y no la del llamador (`CryptoTest.kt:431`) y `lastKey` es ese array (`:443`). Verificado además con `javap` sobre `bcprov-jdk18on-1.86.jar`.
- Riesgo residual honesto: AES (Conscrypt), `HKDFParameters`, subclaves de los motores y la copia privada de `ChaCha20Poly1305` (preocupación 1). Severidad del hallazgo Baja (1.9), sin cambio.
- No se rebaja ningún hallazgo para cerrar antes: la corrección sobre ChaCha queda dicha.

## Revisión Ingeniero Móvil Senior

Skill leída: `seguridad-criptografia-movil/SKILL.md` (principios 1 y «Almacenamiento seguro»).
- Concurrencia y ciclo de vida: `aead` sigue siendo una función pura sin estado compartido; el `KeyParameter` se crea y se vacía dentro de la misma llamada (`Crypto.kt:263-276`, `:329`, `:337-350`), sin hilos ni coroutines nuevos.
- AES sigue por Conscrypt (`Crypto.kt:322`), con la aceleración por hardware y sin cambio de proveedor.
- Rendimiento: un `fill(0)` de 32 bytes por llamada; despreciable. Las lecturas en bloque de archivos (`FileCrypto`) llaman a `aead` por trozo, con la misma sobrecarga mínima.
- Android/R8: el código nuevo no usa reflexión en producción (la reflexión solo está en el test 2, que corre en JVM); no hay cambios en `proguard`.
- API: `blockGcm` y `chaChaPoly` son `internal`, no se amplía la superficie pública; la firma de `aead` es la misma (`Crypto.kt:295-303`).

## Textos para el Abogado Digital (revisión final)

Ninguno: la tarea no añade ni cambia ningún texto de la interfaz ni de la documentación.

---

# Ronda 1 (revisión de la tarea 4: «Needs fixes», pequeño)

**Commit:** `86b6eec` «crypto: ronda 1 de CRY-A03, ChaCha reescrito con clave cero, búfer del texto en claro y vectores fijos», en `ccr-a1a6eabb-765f9g`, ya subido (`490e973..86b6eec`). Mismas reglas de autor y de mensaje (`grep -cE "Claude-Session|claude\.ai"` da 0).

## Qué se ha cambiado

**Importante 1: la afirmación «no se puede borrar sin reflexión» era falsa para ChaCha.** Tiene razón el revisor.
- `crypto/Crypto.kt:368-387`, `chaChaPoly`: el `ChaCha20Poly1305` sale del `run` (parámetro `cipher`, con valor por defecto `ChaCha20Poly1305()`), y en el `finally`, tras `kp.key.fill(0)` (`:381`), hace `cipher.init(false, AEADParameters(KeyParameter(ByteArray(KEY_BYTES)), TAG_BYTES * 8, nonce, ByteArray(0)))` (`:383`) dentro de `try/catch (RuntimeException)`, para que un fallo del re-init no tape la excepción original (un nonce de tamaño equivocado ni siquiera llegó a copiar la clave). Se hace en modo descifrar, donde `init` no comprueba la reutilización del nonce.
- Qué sobrescribe ese `init` (API pública, bcprov 1.86): la copia privada `ChaCha20Poly1305.key`, las palabras de clave del motor (`Salsa20Engine.engineState[4..11]`) **y** la clave de Poly1305 derivada de la real (`init` termina en `reset(true, true)`, que llama a `initMAC()`). Esto último lo comprobé yo con una prueba aparte (`chk/Chk5.java`): el estado de Poly1305 cambia y queda igual que el de un `init` nuevo con clave de ceros.
- KDoc corregido (`Crypto.kt:359-367`): dice qué guarda `ChaCha20Poly1305`, que el re-init lo sobrescribe, y que **no sirve para los motores de GCM** (re-claveado de Twofish, Serpent o Camellia crea arrays nuevos y deja los viejos).
- Test (`CryptoTest.kt:597`): con una instancia propia pasada a `chaChaPoly` y lectura por reflexión de `ChaCha20Poly1305.key`, de `chacha20.engineState[4..11]` y de los enteros de `poly1305` (estilo del test de `lastKey`; si un campo desaparece, `AssertionError` con «bcprov no longer has X.y: check again…»). Lleva su **control**: tras un `init` normal, el campo privado es la clave, las palabras del motor son la clave en little-endian, y el estado de Poly1305 difiere del de clave cero; así se sabe que las lecturas miden algo. Después de cifrar, descifrar, tag roto y clave equivocada, los tres quedan sobrescritos.

**Menor 2: el texto en claro tras un tag roto.**
- `Crypto.kt:262-277`, `runAead` (nuevo, privado, común a `blockGcm` y `chaChaPoly`): `val out = alloc(cipher.getOutputSize(...))` fuera del `try`; una marca `done` que solo pasa a `true` cuando se ha construido el resultado; `finally { if (!done) out.fill(0) }` (`:274`). Así se pone a cero con `CryptoFailure` y con cualquier otra excepción, y **nunca** el array que se devuelve (tampoco la rama `copyOf`, que ya borraba `out`).
- Sin ganchos de producción: `blockGcm` y `chaChaPoly` reciben `alloc: (Int) -> ByteArray = { ByteArray(it) }` (`internal`), un asignador que el test usa para quedarse con la referencia del búfer.
- Test (`CryptoTest.kt:626`): para Twofish, Serpent, Camellia y ChaCha: en éxito hay un solo búfer, es el que se devuelve (`assertSame`) y contiene el texto; con el último bit del tag cambiado (el caso peor: el texto entero ya está dentro) hay un solo búfer y queda todo a cero. Ver el RED más abajo: antes del arreglo el búfer contenía `546578746F2066696A6F…` («Texto fijo de Clavero…»).

**Menor 3: el cableado `aead → chaChaPoly`.** Test de código fuente (`CryptoTest.kt:656`), en el estilo de `MasterPolicyTest`: lee `Crypto.kt` sin comentarios y exige las cuatro ramas exactas (`Aead.TWOFISH_256_GCM -> blockGcm(TwofishEngine(), encrypt, key, nonce, aad, input)`, las otras dos GCM y `Aead.CHACHA20_POLY1305 -> chaChaPoly(KeyParameter(key), encrypt, nonce, aad, input)`), y que haya un solo `GCMBlockCipher.newInstance(` (el de `blockGcm`), un solo `ChaCha20Poly1305()` (el valor por defecto de `chaChaPoly`) y exactamente dos `KeyParameter(key)` (`blockGcm` y la rama de ChaCha). Un `aead` que vuelva a construir su propio cifrador sin el `finally` rompe el test. Es de código fuente porque no hay forma de observar el `KeyParameter` de ChaCha desde `aead` sin ganchos en producción. Funciona en `jvm-check` (directorio `jvm-check/`) y en las pruebas de `app` (directorio `app/`), como el patrón de `SeedCryptoTest`.

**Menor 4: vectores fijos que no son tautológicos** (`CryptoTest.kt:50-66`, `Kat`; test en `:516`). No había ningún texto cifrado fijo del dialecto Seguridad para Twofish, Serpent, Camellia ni la cascada (`app/src/test/resources/vectors/` solo tiene `hpke-pq-04-A5.txt`, `rfc8032.txt` y `seguridad-pk.json`; `resources/seguridad/` tiene la bóveda y la copia de la 1.1, que no fijan esos algoritmos; solo `criptopanel/file-cascade-rawkey.cpenc` y `texts.json`, que la tarea 6 borra). Ahora hay una constante hexadecimal por algoritmo (los seis) con clave `00..1f`, nonce `a0..ab`, AAD de 15 bytes y texto de 41 bytes (dos bloques y un resto). Se quitaron las comparaciones «same bytes as aead» de los tests 3 y 5, que usan ahora esos vectores.
- **Cómo se generaron:** con el código de la base `d0702a3` (antes de CRY-A03), con un worktree de ese commit en el directorio temporal (ya borrado) y un test temporal que llamaba a `aead(alg, true, key, nonce, aad, pt)` para `Aead.entries`. Resultado idéntico en `HEAD`: el test `fixed known answers…` pasa con el código nuevo, así que los bytes no cambian.
- **Cómo se comprobaron** (sin Bouncy Castle en los modos; script de Python fuera del repositorio, con `cryptography`):
  - AES-256-GCM y ChaCha20-Poly1305: la API AEAD de `cryptography` (OpenSSL) da exactamente los mismos bytes.
  - GCM compuesto a mano (CTR + GHASH, 40 líneas de Python) sobre llamadas de un solo bloque; la composición se validó antes contra `AESGCM` de `cryptography`. Camellia-256: el bloque lo da OpenSSL (`cryptography`), así que es **totalmente independiente**. Twofish y Serpent: el bloque lo da el motor en bruto de Bouncy Castle (un `Eng.java` mínimo) y la composición GCM es independiente; los motores tienen sus propios vectores publicados en `SeedCryptoTest` (Twofish de ceros, Camellia RFC 3713; el Serpent solo tiene ida y vuelta).
  - Cascada: capa por capa (AES con `AESGCM`, Twofish y Serpent con la composición a mano), con las subclaves de HKDF-SHA256 de `cryptography` (sal `None`, información `seguridad-cascade-<CAPA>`). Coincide.
  - Salida: `AES_256_GCM OK, CHACHA20_POLY1305 OK, CAMELLIA_256_GCM OK, TWOFISH_256_GCM OK, SERPENT_256_GCM OK, CASCADE OK, ALL OK`.
- **Intento descartado:** quise cruzar Twofish y Serpent también con implementaciones independientes de PyPI (`twofish`, `pyserpent`). Descargarlas y compilar/ejecutar su código externo no estaba permitido en esta sesión y no lo repetí ni lo rodeé; borré lo descargado. Por eso Twofish y Serpent quedan comprobados solo en la composición de GCM. Si el controlador quiere el cruce completo de los motores, tendría que pedirlo y autorizar esas descargas; el vector sigue siendo un respaldo contra regresiones (fija la salida del código de la base), que es lo que necesita la tarea 6.

**Menor 5: frase de riesgo residual** (más abajo, completa y sustituye a la de la primera entrega).

**Menor 6:** no se hizo, por instrucción del controlador. Aun así, `runAead` ya reparte el cuerpo común entre `blockGcm` y `chaChaPoly` (era necesario para el búfer).

## Tests y cifras

- 4 tests nuevos (`fixed known answers…`, `ChaCha20-Poly1305 keeps its own copy… overwrites all three`, `a bad tag leaves no plaintext…`, `aead reaches the Bouncy Castle paths only through…`) y 3 reescritos (`GCM over … wipes … on encrypting and on decrypting`, `… also when the tag fails`, `ChaCha20-Poly1305 wipes the KeyParameter…`, ahora con los vectores y sin «same bytes as aead»). Los tests 1, 2 y 6 de la primera entrega no cambian.
- `gradle -p jvm-check test`: BUILD SUCCESSFUL, **1261 pasan, 0 fallos** (antes 1257; +4).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, **1266 tests, 0 fallos, 0 errores, 0 omitidos** (antes 1262; +4).

## Evidencia TDD

Los vectores se generaron antes con la base; después, un esqueleto sin lógica (`runAead` sin el `finally`, `chaChaPoly` con los parámetros `cipher` y `alloc` pero sin el re-init) para que el rojo fuera de comportamiento:

```
$ gradle -p jvm-check test --tests '*CryptoTest*' --offline --no-daemon --max-workers=1
CryptoTest > ChaCha20-Poly1305 keeps its own copy of the key, the engine its key words and Poly1305 a key of its own, and chaChaPoly overwrites all three FAILED
    AssertionError: encrypt: ChaCha20Poly1305.key is not all zeros: 000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F
CryptoTest > a bad tag leaves no plaintext in the buffer Bouncy Castle decrypted into, and success returns that buffer whole FAILED
    AssertionError: Twofish: the buffer after a bad tag is not all zeros: 546578746F2066696A6F20646520436C617665726F20706172612070726F626172204352592D413033
49 tests completed, 2 failed
```

(Todo lo demás pasaba ya: los vectores fijos coinciden con el código sin cambios, y el test de código fuente también.) GREEN: tras `runAead`/re-init, `gradle -p jvm-check test --tests '*CryptoTest*' --tests '*DialectTest*' --tests '*SeedCryptoTest*'` dio BUILD SUCCESSFUL, y luego las dos verificaciones globales de arriba.

## Frase de riesgo residual para la auditoría final (versión completa)

> Desde la 1.3.1 se borra en todas las salidas, también con tag fallido, la copia de la clave que guardan los parámetros de Bouncy Castle: `KeyParameter.key` (que en GCM es el mismo array que `GCMBlockCipher.lastKey`, y en Twofish el `workingKey` del motor) y, en ChaCha20-Poly1305, la copia privada de `ChaCha20Poly1305`, las palabras de clave de su motor y la clave de Poly1305, que se sobrescriben con un `init` de clave cero; el búfer con el texto en claro de un descifrado fallido también se borra. Lo que sigue sin poder ponerse a cero, y queda en el heap hasta el recolector, también tras bloquear: la copia de la clave que pueda guardar `SecretKeySpec` en el `Cipher` de AES-256-GCM (Conscrypt; «puede guardar», no verificado), la IKM que clona `HKDFParameters`, las subclaves que expanden Twofish, Serpent y Camellia (volver a poner la clave en un motor crea arrays nuevos y deja los viejos, así que el truco de ChaCha no sirve) y la subclave `H` y las tablas que GCM deriva de la clave. El borrado es de mejor esfuerzo: el recolector de ART puede mover un array antes de que se borre y dejar una copia anterior. Verlas exige volcar la memoria del proceso (root o depurador), y esa misma capacidad permitiría esperar al siguiente desbloqueo; por eso el hallazgo sigue en Baja (1.9). Se descartó pasar AES a la implementación de Bouncy Castle: se perdería la aceleración por hardware y la resistencia a los canales laterales de tiempo.

## Revisión experto-seguridad-cripto (ronda 1)

- Higiene de secretos «`fill(0)` en cada salida, también en las ramas de error»: clave en `Crypto.kt:302` y `:381`, y ahora también el texto en claro de un descifrado fallido en `:274`; tests en `CryptoTest.kt:626` (búfer), `:550` y `:574` (claves).
- Sin criptografía propia ni cambio de parámetros: tag de 128 bits y nonce/AAD igual. El re-init con clave cero **no produce ni usa cifrado**: solo vacía el estado y es en modo descifrar.
- Bytes sin cambiar: los seis vectores fijos (`CryptoTest.kt:50-66`, test `:516`) generados con `d0702a3` y comprobados con una composición independiente (ver arriba), más NIST caso 16, RFC 8439 y RFC 9106, `DialectTest` y `SeedCryptoTest` en verde.
- Suposiciones de la biblioteca fijadas: `KeyParameter` copia y `getKey()` es la misma instancia (`:491`), `lastKey` es ese array (`:503`), y ahora también los tres sitios que sobrescribe el re-init de ChaCha (`:597`), cada uno con su control.
- Honestidad: el riesgo residual queda dicho (arriba), sin rebajar la severidad.

## Revisión Ingeniero Móvil Senior (ronda 1)

- Sin estado compartido ni hilos: el `ChaCha20Poly1305` y el búfer son locales a la llamada (`Crypto.kt:368-387`, `:262-277`).
- Coste: un `init` de ChaCha extra por llamada (una expansión de clave y un bloque de keystream) frente a un registro de 64 KiB: despreciable. Un `fill(0)` del búfer solo cuando algo falla.
- Android/R8: en producción no hay reflexión; la reflexión solo está en los tests de JVM. `alloc` y `cipher` son parámetros `internal` con valor por defecto: sin superficie pública nueva; la firma de `aead` no cambia.
- Pendiente de la tarea 6 (anotado): las pruebas de `CryptoTest` ya no dependen de los fixtures de CriptoPanel para los bytes de Twofish, Serpent, Camellia y la cascada.

## Textos para el Abogado Digital (revisión final)

Ninguno.

---

# Ronda 2 (nueva revisión de la tarea 4: un Menor de comentario y dos detalles)

Solo comentarios y texto del informe; ningún cambio de lógica.

- **`CryptoTest.kt:41-49`, KDoc de `Kat`:** ya no dice que los motores en bruto de Twofish y Serpent «tienen sus propios vectores en SeedCryptoTest». Ahora dice que `SeedCryptoTest` tiene un vector publicado para Twofish y solo una ida y vuelta para Serpent, así que en esos dos solo es independiente la composición de GCM. (El informe de la ronda 1 ya lo decía bien.)
- **`Crypto.kt:254-261`, KDoc de `runAead`:** de «se borra en cualquier salida salvo el éxito» a lo exacto: se borra en todo fallo; en éxito el array devuelto no se borra nunca, y lo único que se borra entonces es un búfer más largo sin usar, cuando el resultado es una copia más corta (rama `copyOf`).
- **Informe, frase de riesgo residual de la ronda 1:** «el subclave `H`» pasa a «la subclave `H`».

**Qué ejecuté:** `gradle -p jvm-check test --tests '*CryptoTest*' --tests '*DialectTest*' --tests '*SeedCryptoTest*' --offline --no-daemon --max-workers=1` con `ANDROID_HOME=/opt/android-sdk`: BUILD SUCCESSFUL, ningún test en rojo. No repetí las dos verificaciones globales, porque no cambia ningún código ejecutable (solo comentarios KDoc de un archivo de producción y de uno de tests); las últimas son las de la ronda 1 (jvm-check 1261, app 1266, 0 fallos).
