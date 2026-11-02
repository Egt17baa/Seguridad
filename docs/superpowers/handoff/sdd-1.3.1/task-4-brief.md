### Task 4: CRY-A03, poner a cero las claves en los parámetros de Bouncy Castle

**Archivos:**
- Modificar: `app/src/main/java/app/seguridad/crypto/Crypto.kt:254-266`, `blockGcm`, y `:319-328`, ChaCha20-Poly1305
- Test: `app/src/test/java/app/seguridad/crypto/CryptoTest.kt`

**Interfaces:** ninguna nueva. `aead(...)` da los mismos bytes que antes.

- [ ] **Paso 1: tests que fallan o que fijan:**
  - `KeyParameter(k).key` es la misma instancia en dos llamadas y no es `k`. Así lo hace Bouncy Castle 1.86, y si cambia, el test avisa de que el borrado ya no sirve;
  - un test con un `KeyParameter` creado por la función que se pueda observar desde el test, con un ayudante `internal` que reciba el `KeyParameter` (por ejemplo `internal fun blockGcmWith(kp: KeyParameter, …)`), comprueba que después de `aead` sus bytes son todo ceros, también cuando el tag falla (`CryptoFailure`);
  - los vectores de `CryptoTest` y `DialectTest` siguen pasando tal cual.
- [ ] **Paso 2:** ejecutar `gradle -p jvm-check test --tests '*CryptoTest*' --offline --no-daemon`. Debe fallar el test de los ceros.
- [ ] **Paso 3: implementar** `val kp = KeyParameter(key)` y `finally { kp.key.fill(0) }` en los dos caminos de Bouncy Castle, con el comentario de por qué (`getKey()` devuelve el array interno, que es el mismo que `GCMBlockCipher.lastKey`). AES con `SecretKeySpec` y `HKDFParameters` no cambian: son el riesgo aceptado del diseño.
- [ ] **Paso 4:** las dos verificaciones globales, con 0 fallos.
- [ ] **Paso 5: commit** «crypto: borrar la copia de la clave en los parámetros de Bouncy Castle (CRY-A03)» y push.

