# Clavero 1.4.0: repaso de la revisión de la rama, área B (herramientas y bóveda)

Repaso acotado de los arreglos de código de los hallazgos de B (`branch-review-b-tools-vault.md`). Se revisan `2ddc305` y `45c2660`, sobre el diff `93b3f71..45c2660`, con el informe `branch-review-fixes-code-report.md` y las decisiones R38 y R40 de `progress.md`. También se revisa el cambio de `HashLabSession` del M-1 de C, porque toca el área B. Quedan fuera los documentos, que otro agente está escribiendo ahora. Solo se ha leído el código: no se ha ejecutado Gradle, por instrucción. La puerta de `45c2660` que da R40 es: app 1803 y jvm-check 1798 tests, ninguno fallido ni saltado.

## Veredicto

**Necesita arreglos:** 3 Minor, y ninguno es de seguridad. Los cinco hallazgos de B (I-1 y M-1 a M-4) están arreglados y son ciertos en el código. R38 y R40 están bien hechos: no hay ningún camino de interbloqueo y `.sgenc` no cambia.

## Hallazgos nuevos

### N-1 (Minor): un KDoc huérfano en `SecretShareScreen.kt` documenta la función equivocada

- **Dónde:** `ui/screens/SecretShareScreen.kt:277`. Al quitar `private val SECRET_KEYBOARD` quedó su KDoc («For the secret fields, as in «Cifrar texto»: no autocorrection, and the password keyboard…»). Ahora va seguido de una línea en blanco y de `@OptIn … fun SecretShareScreen(...)`, así que Dokka y el IDE lo toman por la documentación de `SecretShareScreen`.
- **Fallo:** quien pase el ratón sobre `SecretShareScreen` lee una descripción del teclado.
- **Arreglo:** borrar esa línea. La constante ya tiene su KDoc en `ui/theme/Components.kt:173-179`.

### N-2 (Minor): el progreso de un archivo anterior todavía puede escribirse sobre la ejecución nueva (M-1 de C)

- **Dónde:**
  - `tools/hashlab/HashLabSession.kt:67-72`: `startFile()` pone `readBytes = 0` y `totalBytes = -1` **antes** de `file.start()`, que es lo que sube `run`.
  - `HashLabSession.kt:82-86`: `progress(run, read)` comprueba `isCurrent(run)` y después escribe, en dos pasos sin cerrojo, desde el hilo de lectura (`HashLabScreen.kt:209-213`).
- **Fallo:**
  1. Se está leyendo el archivo A.
  2. El hilo de IO de A pasa `isCurrent(runA)`, que es verdadero.
  3. En el hilo principal, el usuario elige B. `job?.cancel()` no detiene ese paso, porque la cancelación solo se ve en el siguiente `ensureActive()`.
  4. `startFile()` pone `readBytes = 0` y sube `run`.
  5. El hilo de A escribe `readBytes = nA`.
  6. B enseña «Calculando… nA de tamañoB» hasta su primer MiB.

  Con el orden de `startFile()`, la ventana se abre ya antes de subir `run`. Es solo una cifra de progreso: no es secreta y no cambia ningún resultado (`finish` sí rechaza la ejecución vieja). Pero contradice lo que el arreglo afirma («only the run started last writes its progress and size», `PlatformSourcesTest.kt:139-141`).
- **Arreglo:**
  - Hacer atómicas la comprobación y la escritura frente al inicio. Por ejemplo, un `private val lock = Any()` en `HashLabSession`, con `synchronized(lock)` en `startFile`, `sized`, `progress` y `forget` para la parte de `run` y del progreso.
  - Llamar a `file.start()` primero en `startFile()` y poner a cero después.
  - Añadir a `HashLabSessionTest` un caso que lo pruebe en el orden «comprobar, empezar y escribir» con dos hilos y un `CountDownLatch`.

### N-3 (Minor): `imports/MergePlan.kt.orig` se confirmó dentro de `app/src/main/java`

- **Dónde:** `app/src/main/java/app/seguridad/imports/MergePlan.kt.orig`, de 445 líneas, añadido en `2ddc305`. Es la copia que deja un `patch`: su contenido es el `MergePlan.kt` de una versión intermedia.
- **Fallo:** no se compila, porque no es `.kt`, ni va en el APK. Pero es una segunda copia desfasada de un lector en el árbol de fuentes. `grep` la encuentra junto al archivo real, y quien la lea o la copie toma reglas que ya no valen (R34 y R36 sí están en la copia; los cambios de este arreglo, no). El área es A y C, pero está en el commit que se me pidió repasar.
- **Arreglo:** borrarla con `git rm`. Un test en `PlatformSourcesTest` puede impedir que vuelva a pasar: ningún archivo de `app/src/main` acabado en `.orig`, `.rej` o `~`.

## Arreglos de B comprobados

- **I-1, el teclado que aprende:**
  - `SECRET_TEXT_KEYBOARD` existe (`ui/theme/Components.kt:179`) y es igual al de «Cifrar texto».
  - Lo llevan los 4 `OutlinedTextField` del Laboratorio de hashes: el texto, el mensaje del HMAC, la entrada del códec y la huella esperada (`HashLabScreen.kt:295`, `:346`, `:374`, `:451`).
  - Lo llevan los 6 del Laboratorio clásico: el desplazamiento, la clave de Vigenère, `TextInput`, los anillos, las posiciones y las clavijas (`ClassicLabScreen.kt:178`, `:199`, `:237`, `:290`, `:299`, `:307`).
  - La clave del HMAC sigue en `PasswordField`, y los dos campos de «Repartir un secreto» usan la constante compartida.
  - El test de fuente (`PlatformSourcesTest`, «I-1 the labs and the other secret fields…») cuenta las llamadas por archivo, de modo que un campo nuevo sin el teclado lo rompe, y comprueba que ninguno tiene `visualTransformation`. Su analizador de paréntesis vale mientras las etiquetas sean constantes sin paréntesis, como dice su KDoc.
  - Con el teclado arreglado, `HASH_LAB_HMAC_INTRO` y `CLASSIC_NOT_SAVED` vuelven a ser verdad y no hace falta cambiar ningún texto.
- **M-1, el KDoc de `HashLabSession`:** ahora es verdad (`HashLabSession.kt:14-16`). El tipo y el algoritmo se conservan, y el archivo se borra con su resultado, su tamaño, su progreso y el algoritmo de ese resultado (`forget`, `:88-100`).
- **M-2, el código muerto:** `sha256Hex(InputStream)` y `sameHash` desaparecen de `crypto/Hashes.kt`, junto con sus casos de `GeneratorAndChecksTest`. No queda ninguna llamada en `app/src/main`, y la sobrecarga `sha256Hex(ed, kem)` de las identidades no se toca.
- **M-3, el tope de 1024:** pasa a `PASSWORD_FIELD_MAX_CHARS = 65 536` con `passwordFieldTakes` (`vault/MasterPolicy.kt:23-34`). Lo usan «Comprobar una copia» (`BackupCheckScreen.kt:138`) y la contraseña de una exportación (`ImportScreen.kt:227`), y no queda ningún tope de 1024 en los campos de contraseña. La cadena de mayor tamaño son 128 KiB, y Argon2id solo la recorre, sin coste de memoria. El KDoc dice la verdad: «Comprobar» acepta en la práctica cualquier maestra que acepte «Importar copia», y crear la bóveda, cambiar la maestra y desbloquear siguen sin tope.
- **M-4 y R38, `crypto/KdfLock.kt`:**
  - **Equidad:** el `Mutex` de kotlinx da el cerrojo por orden de llegada, y al soltarlo lo pasa al primero que espera, sin que nadie se cuele.
  - **Semántica de `NonCancellable`:**
    - `mutex.withLock { withContext(NonCancellable) { withContext(context) { block() } } }`.
    - Quien se cancela mientras espera no llega a ejecutar el bloque; lo prueba `aCallerCancelledWhileItWaitsNeverDerives`.
    - Un bloque empezado termina con el cerrojo cogido.
    - Su resultado vuelve aunque quien lo llamó se haya cancelado: la vuelta de `withContext(NonCancellable)` con el mismo despachador no comprueba la cancelación. Es el mismo patrón que tenía `VaultStore.derive` y que pedía BOV-3. Lo prueban `aCancelledDerivationKeepsTheLockUntilItEnds` (la clave de 32 bytes llega) y `anExceptionInTheBlockReleasesTheLock`.
  - **Orden de los cerrojos:** el único que toma los dos es `VaultStore`, siempre primero su `mutex` y después `KdfLock` (`VaultStore.kt:321` y todas sus llamadas: `:411`, `:441`, `:443`, `:526`, `:533`, `:801`, `:912`, `:915`). Los bloques de los demás son cálculo puro y no tocan la bóveda, los ajustes ni la fila de la huella:
    - «Comprobar una copia» (`BackupCheck.kt:85`, `VaultCrypto.unlock`);
    - «Importar de otra app» (`ImportScreen.kt:303`, `readImport` sin `suspend`);
    - «Cifrar texto» (`SecurityToolsScreen.kt:280-282`);
    - «Cifrar archivo» (`FileCrypto.kt:29-30`).
  - **`KeyOpsQueue`:** no toma ni el mutex de la bóveda ni `KdfLock`, y ningún bloque que tenga uno de ellos espera a la fila: `onKey` y `onError` van después de salir de ella.
  - **Conclusión:** no hay ningún ciclo, así que no hay interbloqueo posible.
- **Las llamadas de `VaultStore`:** todas las derivaciones con Argon2id pasan por `derive`: crear, desbloquear, confirmar la maestra, volver a envolver después, cambiar la maestra e «Importar copia». `create` y `afterMasterUnlock` antes iban con `withContext(compute)` sin cerrojo. Ahora `create` no se puede cancelar, pero ya iba dentro de `withContext(NonCancellable)` (`:401`), así que no cambia nada. Las claves se ponen a cero igual que antes, con el `catch (e: Exception)` de cada llamada.
- **«Cifrar texto»:** antes era cancelable con `withContext(Default)` y ahora espera dentro de `KdfLock.derive`. Lo único que cambia es que un resultado de una pantalla cerrada se calcula entero y después se descarta, como antes. Con una clave en bruto también espera, sin daño: el texto es corto.
- **R40, `FileCrypto.fileKey`:**
  - Solo `Secret.Password` toma `KdfLock.deriveBlocking`. Una clave en bruto (HKDF) no espera; lo prueba `aFileDerivationAndAnotherDerivationNeverOverlap`.
  - El flujo se cifra fuera del cerrojo, y no se escribe nada antes de tener la clave (mismo test).
  - **Reentrada:** `keyForHeader` no toma el cerrojo y se llama desde `fileKey`, ya dentro de él, o desde `encryptText`/`decryptText`, dentro de `KdfLock.derive` en «Cifrar texto». Las únicas llamadas a `encryptStream` y `decryptStream` en la app (`SecurityToolsScreen.kt:661`, `:694`) están fuera de cualquier `derive`, así que no hay anidamiento.
  - **Hilo principal:** se comprueba por reflexión (`Looper.getMainLooper().getThread()`, API pública). Con R8 vale igual, porque es una clase del sistema. En los tests de JVM y en los de Android, con stubs, da falso. `theBlockingEntryRefusesTheMainThread` prueba que se rechaza.
  - `runBlocking` bloquea un hilo de IO mientras otra derivación corre en `Default`: es otro grupo de hilos y no hay ciclo. La espera no se puede cancelar, y está documentado.
  - **`.sgenc`:** sigue siendo idéntico byte a byte. La cabecera, la clave y el formato no cambian; solo cambia dónde se deriva la clave.
- **El M-1 de C en `HashLabSession`:** el algoritmo, el algoritmo del resultado, el tamaño y el progreso viven ahora en la sesión (`ComposeCells`), sobreviven al giro y se borran con el archivo. `isCurrent` lee un `run` `@Volatile` (`CipherSessions.kt:144`). Con el borrado, `file.clear()` sube `run`, y un `sized` o un `finish` tardíos se rechazan. El caso que queda abierto es N-2.

## Revisión experto-seguridad-cripto

- **Qué serializa `KdfLock`:** todas las derivaciones grandes (Argon2id y scrypt), de modo que `checkSane` (`Crypto.kt:62-74`) vuelve a valer para la única derivación que corre. `KDF_OUT_OF_MEMORY` deja de poder salir por dos derivaciones a la vez. Las claves siguen llegando a quien las pone a cero (BOV-3, `KdfLockTest.aCancelledDerivationKeepsTheLockUntilItEnds`).
- **El teclado de los campos secretos:** ya no aprende lo escrito (MASVS-STORAGE-2, `Components.kt:173-179`).
- **Comparaciones:** queda fuera el último comparador que no era de tiempo constante (`sameHash`).
- **Formatos y primitivas:** ninguno cambia (`FileCrypto.kt:29-30`).
- **Lo nuevo:** no hay nada de seguridad. N-2 es una cifra de progreso y N-3 es un archivo que no se compila.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** el orden de los cerrojos es fijo (el mutex de la bóveda, después `KdfLock`), los bloques no se suspenden y no hay reentrada, así que no hay interbloqueo (`KdfLock.kt:28-38`). La espera suspende en todos los casos menos en `deriveBlocking`, que solo se usa fuera del hilo principal y lo comprueba (`KdfLock.kt:66-69`).
- **Ciclo de vida:** el estado del Laboratorio de hashes sigue en el ViewModel y en la sesión, sin `rememberSaveable` (`HashLabSession.kt:44-61`).
- **Pendientes:** N-1, el KDoc, y N-2, la carrera del progreso entre el hilo de lectura y el principal. Son de calidad, no de seguridad.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo. I-1 se arregló con el teclado, así que `HASH_LAB_HMAC_INTRO` y `CLASSIC_NOT_SAVED` no cambian. M-3 no añade ningún mensaje.
