# Tarea 1: CRY-B01, la desconfianza de eMudhra con la cadena validada (informe del implementador)

**Estado:** DONE_WITH_CONCERNS: hecha, con una desviación obligada en el cableado que se explica abajo.
**Commit:** `cbf3721` «net: la desconfianza de eMudhra mira también la cadena validada (CRY-B01)», con push a `ccr-a1a6eabb-765f9g`.

## Qué se ha hecho

- **`net/DistrustedCertificates.kt`**
  - `typealias TrustedChain = (chain: Array<out X509Certificate>, authType: String, host: String?) -> List<X509Certificate>` (`:78`).
  - `class DistrustingTrustManager(base: X509TrustManager, trustedChain: TrustedChain = { chain, _, _ -> chain.toList() })` (`:96-99`).
  - Las tres sobrecargas de `checkServerTrusted` pasan por `checked` (`:107-111`), que hace tres cosas en orden:
    1. `refuse` de la cadena presentada;
    2. la llamada al sistema, igual que antes;
    3. `refuse(trustedChain(chain, authType, host))`.
  - El `host` sale de la sesión del apretón de manos:
    - sin socket, es `null` (`:114`);
    - con un socket, es `(socket as? SSLSocket)?.handshakeSession?.peerHost` (`:118`);
    - con un motor, es `engine?.handshakeSession?.peerHost` (`:123`).
  - `checkClientTrusted` y `getAcceptedIssuers` no cambian (`:127-135`).
  - El KDoc de la clase (`:80-95`) explica la caché de intermedios de Conscrypt: `TrustManagerImpl` y su «intermediateIndex», compartida por todo el proceso desde Android 7. Explica también por qué se comprueba antes y después.
  - `fun OkHttpClient.Builder.distrustCompromisedCas(trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null)` (`:143-146`). Sin función, construye el mismo `DistrustingTrustManager(system)` que en la 1.3.0.
- **`net/HttpClient.kt`:** `fun secureClient(appVersion: String, trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null)` (`:15`), que se lo pasa a `distrustCompromisedCas` (`:17`).
- **`android/TrustedChain.kt`** (nuevo, solo Android): `fun androidTrustedChain(system: X509TrustManager): TrustedChain` (`:17-23`).
  - `X509TrustManagerExtensions(system)` se crea una vez (`:18`).
  - Con `host == null` lanza `CertificateException`, es decir, falla cerrado (`:20`).
  - Si no, devuelve `extensions.checkServerTrusted(chain, authType, host)` (`:21`).
- **Cableado, con una desviación del brief.** El brief pide `secureClient(appVersion) { androidTrustedChain(it) }` dentro de `AppContainer.client`. Así no compila en `jvm-check`:
  - `AppContainer.kt` se compila y se prueba en la JVM (lo hace `AppContainerTest`), y `jvm-check/build.gradle.kts` solo excluye `ui/`, `android/`, `MainActivity.kt` y `SeguridadApp.kt`;
  - `android/TrustedChain.kt` está excluido y `X509TrustManagerExtensions` no existe en la JVM.

  He seguido el patrón que ya usa el proyecto para lo que es solo de Android (`backupFolder`, `releaseFolder`, `elapsedRealtime`, `revokeShare`):
  - `AppContainer` recibe el parámetro `trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null` (`AppContainer.kt:80-84`);
  - `AppContainer.client` es `secureClient(appVersion, trustedChainOf)` (`AppContainer.kt:106`, la antigua `:99`);
  - `SeguridadApp` le pasa `trustedChainOf = { androidTrustedChain(it) }` (`SeguridadApp.kt:30`).

  En la JVM se queda en `null`, que es el comportamiento de la 1.3.0 más la comprobación previa.

## Tests

- **`DistrustedCertificatesTest`**, 3 nuevos; los 7 que ya había no cambian:
  - `a chain completed from the system's cache is refused` (`:164`):
    - con `trustedChain = { c, _, _ -> c.toList() + compromised + aaaRoot }`, `checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA")` lanza `CertificateException`;
    - se comprueba en las tres sobrecargas.
  - `the system never sees a chain with a compromised key` (`:172`):
    - con `arrayOf(compromised, aaaRoot)`, lanza y el espía no se llamó;
    - en las tres sobrecargas (las del `Socket` y del `SSLEngine` con `null`);
    - con dos espías: `X509ExtendedTrustManager`, como en Android, y `X509TrustManager` simple, por la rama `?:`.
  - `the validated chain gets the host` (`:187`):
    - la sobrecarga del `SSLEngine` pasa el `handshakeSession.peerHost` (`"api.pwnedpasswords.com"`) de un motor falso, y con `null` pasa `host = null`;
    - además, lo mismo con un `SSLSocket` falso, porque es el camino real de OkHttp en Android, y `null` en la sobrecarga sin socket;
    - también comprueba que llegan la cadena presentada y el `authType`.
- **`HttpClientTest`**, 2 nuevos:
  - `withoutAValidatedChainTheClientIsBuiltAsBefore` (`:33`): sin función, el gestor de confianza es `DistrustingTrustManager` sobre el del sistema (mismos emisores menos los de eMudhra) y los cuatro *timeouts* siguen en 15 s. Es el «como hoy» del brief.
  - `theValidatedChainComesFromTheSystemTrustManager` (`:44`): con función, esta recibe una sola vez el gestor del sistema, no el `DistrustingTrustManager`.
- **`PlatformSourcesTest`**, 1 nuevo: `CRY-B01 the app's client checks the chain Android validated, and fails closed without the server's name` (`:296`). Comprueba en el código fuente:
  - el cableado de `SeguridadApp` y `AppContainer`;
  - que `android/TrustedChain.kt` usa `X509TrustManagerExtensions(system)` y `checkServerTrusted(chain, authType, host)`;
  - que lanza `CertificateException` sin `host`.

  Es el patrón del archivo para el código que la JVM no puede ejecutar.

**Recuentos (verificación global, una vez, antes del commit):**
- `gradle -p jvm-check test --offline --no-daemon --max-workers=1`: **1208 tests**, 0 fallos, 0 errores y 0 omitidos (antes 1202, +6). BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: **1213 tests**, 0 fallos y 0 errores (antes 1207, +6). BUILD SUCCESSFUL, exit 0.
  - El lint no tiene avisos nuevos.
  - El único aviso en estos archivos es `CustomX509TrustManager` sobre la declaración de `DistrustingTrustManager`. Ya estaba en la 1.3.0 (la clase existía) y es el aviso que se espera de un envoltorio deliberado que siempre llama al sistema.
  - Kotlin no da ningún aviso de compilación en los archivos tocados.

## Pruebas de TDD

**RED 1** (tests escritos, código sin tocar):
`gradle -p jvm-check test --tests '*DistrustedCertificatesTest*' --tests '*HttpClientTest*' --offline --no-daemon --max-workers=1`
```
e: .../DistrustedCertificatesTest.kt:165:60 Too many arguments for 'constructor(base: X509TrustManager): DistrustingTrustManager'.
e: .../DistrustedCertificatesTest.kt:189:53 Too many arguments for 'constructor(base: X509TrustManager): DistrustingTrustManager'.
e: .../HttpClientTest.kt:46:55 Too many arguments for 'fun secureClient(appVersion: String): OkHttpClient'.
> Task :compileTestKotlin FAILED
BUILD FAILED in 43s
```
Es lo esperado: la API nueva no existía. Los motores y sockets falsos compilaron sin errores.

**RED 2** (solo las firmas nuevas, con los parámetros aceptados e ignorados, para ver un fallo en ejecución), con el mismo comando:
```
DistrustedCertificatesTest > the system never sees a chain with a compromised key FAILED
    java.lang.AssertionError  at ...(DistrustedCertificatesTest.kt:181)      <- assertFalse(extended.called): el sistema vio la cadena
DistrustedCertificatesTest > the validated chain gets the host FAILED
    java.lang.AssertionError: expected:<[([[ ...                            <- trustedChain nunca se llamó: no llegó el host
DistrustedCertificatesTest > a chain completed from the system's cache is refused FAILED
    java.lang.AssertionError: expected java.security.cert.CertificateException to be thrown, but nothing was thrown
HttpClientTest > theValidatedChainComesFromTheSystemTrustManager FAILED
    java.lang.AssertionError: expected:<1> but was:<0>                       <- la función no se usó
15 tests completed, 4 failed
BUILD FAILED in 31s
```
Fallan justo los tres tests nuevos de `DistrustedCertificatesTest`, y cada uno por su motivo. `withoutAValidatedChainTheClientIsBuiltAsBefore` pasa, como se espera: es la guarda del comportamiento de hoy. Después se quitó el esbozo y se implementó.

**GREEN:**
`gradle -p jvm-check test --tests '*DistrustedCertificatesTest*' --tests '*HttpClientTest*' --tests '*PlatformSourcesTest*' --tests '*PwnedPasswordsTest*' --tests '*AppContainerTest*' --offline --no-daemon --max-workers=1`
```
DistrustedCertificatesTest > the system never sees a chain with a compromised key PASSED
DistrustedCertificatesTest > the validated chain gets the host PASSED
DistrustedCertificatesTest > a chain completed from the system's cache is refused PASSED
HttpClientTest > withoutAValidatedChainTheClientIsBuiltAsBefore PASSED
HttpClientTest > theValidatedChainComesFromTheSystemTrustManager PASSED
PlatformSourcesTest > CRY-B01 the app's client checks the chain Android validated, and fails closed without the server's name PASSED
(... los 7 tests anteriores de DistrustedCertificatesTest y los 3 de HttpClientTest, PASSED)
BUILD SUCCESSFUL in 31s     (44 PASSED, 0 FAILED)
```

## Archivos cambiados

- `app/src/main/java/app/seguridad/net/DistrustedCertificates.kt`
- `app/src/main/java/app/seguridad/net/HttpClient.kt`
- `app/src/main/java/app/seguridad/android/TrustedChain.kt` (nuevo)
- `app/src/main/java/app/seguridad/AppContainer.kt`
- `app/src/main/java/app/seguridad/SeguridadApp.kt`, fuera de la lista del brief, por la desviación del cableado
- `app/src/test/java/app/seguridad/net/DistrustedCertificatesTest.kt`
- `app/src/test/java/app/seguridad/net/HttpClientTest.kt`
- `app/src/test/java/app/seguridad/PlatformSourcesTest.kt`, para comprobar el cableado

## Revisión propia

- **Completo:**
  - las cinco firmas de «Interfaces» son las del brief;
  - el orden refuse, sistema y refuse está en las tres sobrecargas;
  - el host sale de la sesión del apretón de manos;
  - sin host, falla cerrado;
  - `checkClientTrusted` y `getAcceptedIssuers` no cambian;
  - el KDoc pedido está escrito.
- **Comportamiento sin función:** es idéntico al de la 1.3.0 más la comprobación previa. No es más estricto con las cadenas sanas: la cadena presentada ya se rechazaba entera si llevaba un certificado de eMudhra, ahora solo se rechaza antes.
- **Patrones:** el cableado sigue el de `revokeShare` y `backupFolder`. La comprobación del código Android sigue `PlatformSourcesTest`.
- **YAGNI:** lo único que añado fuera del brief está justificado:
  - el socket falso cubre el camino real de OkHttp en Android, y un fallo ahí dejaría «Comprobar contraseña» y «Salud» rotos en todos los móviles (punto 1 de «Puntos que revisar»);
  - el test de código fuente cubre el cableado que obliga la desviación: sin él, nada comprobaría que Android usa `androidTrustedChain`.
- **Salida limpia:** no hay avisos nuevos de Kotlin ni de lint.

## Preocupaciones

1. **Desviación del cableado:** se explica arriba, y la decide el controlador. El resultado en Android es el mismo que pide el brief, pero `SeguridadApp.kt` no estaba en la lista de archivos.
2. **Solo se puede comprobar en el móvil:**
   - Que `androidTrustedChain` acepta una cadena sana de `api.pwnedpasswords.com`. Es una segunda validación con nombre de servidor por `RootTrustManager → NetworkSecurityTrustManager → TrustManagerImpl`. No hay Network Security Config propia ni configuración por dominio.
   - Que `peerHost` no es `null` durante `checkServerTrusted`. *(Corregido en la ronda 1: la versión anterior decía que la 1.3.0 ya dependía de ello, y no es así.)*
     - `RootTrustManager` lee el mismo `getHandshakeSession().getPeerHost()`, pero solo falla si la **sesión** es `null`. Con un host `null` usa la configuración por defecto (`ApplicationConfig.getConfigForHostname`).
     - Así que `android/TrustedChain.kt` (rechazo sin host) es **más estricto que la 1.3.0**.
     - En la práctica, `peerHost` es el nombre que OkHttp pasa a `createSocket(socket, host, port, true)`, así que viene puesto.
     - La única prueba real es la del dueño en el móvil: es **obligatoria**, no un trámite.

   Los dos puntos quedan en la prueba del dueño en el móvil: «Comprobar contraseña» y «Salud» tienen que seguir funcionando.
3. **Riesgo pequeño:** la validación por nombre no recibe el OCSP grapado ni los SCT de la extensión TLS, solo los SCT del propio certificado. Si Android aplicara Certificate Transparency a esta app y el certificado de Cloudflare solo llevara SCT por TLS, la segunda validación fallaría y la comprobación de filtraciones diría «No se pudo comprobar…». Es fallar cerrado, no una debilidad. Los certificados públicos actuales llevan los SCT dentro, y la prueba en el móvil lo confirmaría.
4. **Construcción del cliente:** `X509TrustManagerExtensions(system)` lanzaría `IllegalArgumentException` al construir el cliente si el gestor del sistema no tuviera el `checkServerTrusted` con nombre de servidor. En la API 24 y posteriores, el gestor por defecto es siempre `RootTrustManager`, que sí lo tiene (minSdk 26), así que no se protege más.

## Revisión experto-seguridad-cripto

He usado como lista `SKILL.md` y `references/cripto-moderna.md` (§8 TLS: «No desactives nunca la verificación de certificados»).

- **Modelo de amenaza:** un intermediario en la red con la clave de un intermedio de eMudhra comprometido, firmado por «AAA Certificate Services». Ataca en dos conexiones: la primera llena la caché y la segunda manda solo la hoja. Lo que protege: el prefijo SHA-1 de 5 caracteres y la integridad de la respuesta de HIBP.
- **La caché nunca se llena desde este cliente:** `refuse(chain.asList())` va antes de `system()` (`DistrustedCertificates.kt:108-109`). Lo prueba el test `the system never sees a chain with a compromised key` (`DistrustedCertificatesTest.kt:172-185`, en las tres sobrecargas y con los dos tipos de gestor).
- **Una caché llenada por otro camino también se detecta:** se rechaza la cadena que construyó el sistema (`DistrustedCertificates.kt:110`). En Android, esa cadena la da `X509TrustManagerExtensions.checkServerTrusted(chain, authType, host)` (`android/TrustedChain.kt:21`). Lo prueba `a chain completed from the system's cache is refused` (`DistrustedCertificatesTest.kt:164-170`).
- **Nunca más débil que el sistema:** `system()` se llama siempre y sus excepciones salen igual (`DistrustedCertificates.kt:109`, `:114`, `:119-120`, `:124-125`). No hay ningún camino que acepte sin el sistema.
- **Falla cerrado:**
  - sin nombre de servidor (`android/TrustedChain.kt:20`);
  - si la segunda validación lanza, la excepción sale de `checked` y el apretón de manos falla;
  - la interfaz ya lo trata como «No se pudo comprobar…» (`catch (e: Exception)` en `ui/screens/SecurityToolsScreen.kt:1029-1030`).
- **La comparación** sigue siendo por SPKI SHA-256 y por nombre de las raíces (`DistrustedCertificates.kt:69-70`), sin cambios. No he añadido la capa opcional de los nombres de los intermedios: el diseño la descarta (opción 3).
- **El primer `refuse` no es más permisivo:** la cadena presentada se rechaza entera si cualquier certificado es de eMudhra, igual que antes. Solo cambia el momento.
- **Hermética:** ni red, ni dependencias, ni permisos nuevos. `android.net.http.X509TrustManagerExtensions` es una API de la plataforma. La única red sigue siendo `api.pwnedpasswords.com`.
- **Logs y secretos:** no hay logs. Los mensajes de excepción no llevan datos del usuario, solo el nombre del certificado o un texto fijo, y no se enseñan.
- **Formatos:** no cambia ninguno.
- **Referencias:** MASVS-NETWORK-1 y -2, y CWE-295 (validación incorrecta de certificados).

## Revisión Ingeniero Móvil Senior

He usado `seguridad-criptografia-movil` (Red: «Validación TLS estándar…») y `desarrollo-movil-senior`.

- **API y versión:**
  - `X509TrustManagerExtensions` existe desde la API 17 y acepta el `RootTrustManager` de Network Security Config por *duck typing* desde la API 24; minSdk es 26 (`app/build.gradle.kts:31`).
  - Va solo en `android/` (`android/TrustedChain.kt:3`), que `jvm-check` excluye.
  - El módulo de Android compila sin avisos (`:app:compileDebugKotlin`).
- **Camino real en Android:** OkHttp conecta con un `SSLSocket` de Conscrypt, y Conscrypt llama a la sobrecarga del `Socket`. `ConscryptEngineSocket` también la usa, porque envuelve el gestor y convierte la del motor en la del socket.
  - El host sale de `(socket as? SSLSocket)?.handshakeSession?.peerHost` (`DistrustedCertificates.kt:118`), igual que lo lee `RootTrustManager`.
  - El test cubre el socket y el motor (`DistrustedCertificatesTest.kt:187-198`).
- **Ciclo de vida y concurrencia:**
  - `AppContainer.client` sigue siendo `by lazy`, un singleton de proceso (`AppContainer.kt:106`).
  - `X509TrustManagerExtensions` se crea una vez por cliente (`android/TrustedChain.kt:18`).
  - La función devuelta no tiene estado mutable y `DistrustingTrustManager` tampoco (`DistrustedCertificates.kt:96-99`), así que se puede usar desde varias conexiones a la vez.
- **Rendimiento:** cada apretón de manos completo hace una segunda validación de la cadena, unos milisegundos. Las peticiones de «Salud» reutilizan la conexión o la sesión TLS de la caché propia del `SSLContext` del cliente, que solo guarda sesiones validadas por este gestor (`DistrustedCertificates.kt:147`).
- **Arquitectura:** la inyección por el constructor de `AppContainer` (`AppContainer.kt:80-84`) desde `SeguridadApp` (`SeguridadApp.kt:30`) es el mismo patrón que `revokeShare` y `backupFolder` (`SeguridadApp.kt:20-28`). `net/` sigue siendo JVM pura y se puede probar.
- **Pendiente en el móvil:** la prueba del dueño. «Comprobar contraseña» y «Salud» tienen que dar resultado con la red normal.

## Textos para el Abogado Digital (revisión final)

No hay textos nuevos para el usuario. Los dos mensajes de `CertificateException` están en inglés, son internos y la interfaz no los enseña: «No se pudo comprobar. Revisa la conexión.» ya existía y no cambia.

---

# Ronda de arreglos 1 (revisión de la tarea 1, sección «Minor»)

La revisión aprobó la tarea, sin hallazgos Critical ni Important. Por la regla del proyecto se arreglan los cinco menores.

## Cambios

1. **Fallo al construir: falla cerrado en vez de cerrar la app.**
   - `android/TrustedChain.kt:22-26`: si `X509TrustManagerExtensions(system)` lanza `IllegalArgumentException`, `androidTrustedChain` devuelve un `TrustedChain` que siempre lanza `CertificateException` (con la causa). Así la interfaz dice «No se pudo comprobar…» y no se cierra la composición (`ui/AppNavHost.kt:212`, `:248`).
   - El KDoc (`:15-19`) lo explica.
2. **Se prueba que el cliente usa el `TrustedChain` devuelto.**
   - `DistrustingTrustManager.trustedChain` pasa de `private` a `internal` (`net/DistrustedCertificates.kt:99-100`), con un KDoc que explica que es para el test.
   - `HttpClientTest.theValidatedChainComesFromTheSystemTrustManager` comprueba ahora `assertSame(validated, (client.x509TrustManager as DistrustingTrustManager).trustedChain)` (`HttpClientTest.kt:51`).
   - Prueba de mutación: con la línea `:148` cambiada a `trustedChainOf?.let { it(system); DistrustingTrustManager(system) }`, el test falla (`expected same:<HttpClientTest$$Lambda…> was not:<DistrustingTrustManager$$Lambda…>`). Después se restauró.
3. **Informe corregido** («Preocupaciones», punto 2): `RootTrustManager` solo falla si la **sesión** es `null`, y con un host `null` usa la configuración por defecto. Por eso:
   - `android/TrustedChain.kt:28` (rechazo sin host) es **más estricto que la 1.3.0**;
   - la prueba en el móvil es **obligatoria**: «Comprobar contraseña» y «Salud» tienen que dar resultado.

   El KDoc de `TrustedChain.kt` decía que «sin el nombre no se puede preguntar», que tampoco era exacto. Ahora dice que es un rechazo a propósito, más estricto que el sistema.
4. **Lint:** `@Suppress("CustomX509TrustManager")` en `net/DistrustedCertificates.kt:96`, con el motivo en la misma línea: «it always asks the system first and only adds refusals, never accepts on its own». Va `@Suppress` y no `@SuppressLint`, porque `net/` compila en la JVM.
   - `app/build/reports/lint-results-debug.xml` (13:41, de esta ejecución): **0** `CustomX509TrustManager` y ningún aviso en `DistrustedCertificates.kt` ni en `TrustedChain.kt`. Quedan 10 avisos, todos anteriores y en otros archivos: 6 NewerVersionAvailable, 2 GradleDependency, 1 DiscouragedApi y 1 ObsoleteSdkInt.
5. **`HttpClientTest.kt:39`:** los `acceptedIssuers` se comparan como conjuntos (`toSet()`), no como listas ordenadas.

También cambia `PlatformSourcesTest.kt:303-304`. Comprueba en el código fuente que `androidTrustedChain` atrapa `IllegalArgumentException` y devuelve una función que lanza `CertificateException`, porque la JVM no puede ejecutar `android/`.

## Tests y comandos

**RED**, con `gradle -p jvm-check test --tests '*DistrustedCertificatesTest*' --tests '*HttpClientTest*' --tests '*PlatformSourcesTest*' --offline --no-daemon --max-workers=1`:
- con `trustedChain` aún privado:
  ```
  e: .../HttpClientTest.kt:51:84 Cannot access 'val trustedChain: ...': it is private in 'app.seguridad.net.DistrustingTrustManager'.
  > Task :compileTestKotlin FAILED
  ```
- con `trustedChain` ya `internal` y sin el arreglo 1:
  ```
  PlatformSourcesTest > CRY-B01 the app's client checks the chain Android validated, and fails closed without the server's name FAILED
      java.lang.AssertionError
  32 tests completed, 1 failed
  ```

**GREEN**, con el mismo comando: `BUILD SUCCESSFUL in 19s`, con 32 de 32 PASSED (10 DistrustedCertificatesTest, 5 HttpClientTest y 17 PlatformSourcesTest).

**Verificaciones globales**, una vez, con `export ANDROID_HOME=/opt/android-sdk`:
- `gradle -p jvm-check test --offline --no-daemon --max-workers=1`: `BUILD SUCCESSFUL in 1m 3s`, con **1208 tests**, 0 fallos, 0 errores y 0 omitidos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: `BUILD SUCCESSFUL in 3m 8s`, con **1213 tests**, 0 fallos y 0 errores. Kotlin no da avisos.

Los recuentos no cambian respecto a la ronda 0 (1208 y 1213): esta ronda refuerza tests que ya había y no añade ninguno.

## Revisión experto-seguridad-cripto (ronda 1)

- **Falla cerrado también al construir:** `android/TrustedChain.kt:24-25`. Un gestor del sistema que no se puede usar rechaza todas las conexiones con `CertificateException`; nunca acepta sin la segunda comprobación.
- **Probado el enlace entre la cadena de Android y el rechazo:** `HttpClientTest.kt:51`, con la mutación detectada. `internal` no amplía la API pública: solo lo ve el módulo, y es una función, no un secreto.
- **La supresión de lint no oculta nada:** el gestor sigue llamando siempre al sistema (`DistrustedCertificates.kt:111`) y solo añade rechazos (`:110`, `:112`).
- **Más estricto que la 1.3.0 sin host:** `TrustedChain.kt:28`. Ahora está bien documentado (KDoc `:15-17` y el informe).

## Revisión Ingeniero Móvil Senior (ronda 1)

- **La app no se cierra al abrir «Comprobar contraseña» o «Salud»** aunque falle `X509TrustManagerExtensions` (`android/TrustedChain.kt:22-26`). `AppContainer.client`, que se construye durante la composición, ya no puede lanzar por esta causa.
- **`@Suppress`, no `@SuppressLint`** (`DistrustedCertificates.kt:96`): `net/` sigue compilando en la JVM, y el lint de Android lo respeta (el aviso desaparece del informe).
- **Prueba en el móvil obligatoria:** «Comprobar contraseña» y «Salud» con la red normal.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo. El mensaje de `CertificateException` que se añade está en inglés, es interno y no se enseña.
