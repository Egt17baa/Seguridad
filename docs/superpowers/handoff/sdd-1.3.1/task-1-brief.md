### Task 1: CRY-B01, la desconfianza de eMudhra con la cadena validada

**Archivos:**
- Modificar: `app/src/main/java/app/seguridad/net/DistrustedCertificates.kt:72-116`, `app/src/main/java/app/seguridad/net/HttpClient.kt:12-14` y `app/src/main/java/app/seguridad/AppContainer.kt:99`
- Crear: `app/src/main/java/app/seguridad/android/TrustedChain.kt`
- Tests: `app/src/test/java/app/seguridad/net/DistrustedCertificatesTest.kt` y `HttpClientTest.kt`

**Interfaces:**
- Produce:
  - `typealias TrustedChain = (chain: Array<out X509Certificate>, authType: String, host: String?) -> List<X509Certificate>` en `net/`;
  - `class DistrustingTrustManager(base: X509TrustManager, trustedChain: TrustedChain = { c, _, _ -> c.toList() })`;
  - `fun OkHttpClient.Builder.distrustCompromisedCas(trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null)`;
  - `fun secureClient(appVersion: String, trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null): OkHttpClient`;
  - `fun androidTrustedChain(system: X509TrustManager): TrustedChain` en `android/TrustedChain.kt`.

- [ ] **Paso 1: tests que fallan,** en `DistrustedCertificatesTest`:
  - `a chain completed from the system's cache is refused`: con `trustedChain = { c, _, _ -> c.toList() + compromised + aaaRoot }`, `checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA")` lanza `CertificateException`;
  - `the system never sees a chain with a compromised key`: un `X509TrustManager` espía que apunta si lo llamaron; con `arrayOf(compromised, aaaRoot)`, lanza y el espía no se llamó. Igual en las tres sobrecargas de `checkServerTrusted`; la del `Socket` y la del `SSLEngine` con `null`;
  - `the validated chain gets the host`: la sobrecarga con `SSLEngine` pasa a `trustedChain` el `handshakeSession.peerHost` del motor, con un `SSLEngine` falso cuya `handshakeSession` devuelve una `SSLSession` falsa con `peerHost = "api.pwnedpasswords.com"`; con `null` como motor pasa `host = null`;
  - los tests que ya hay siguen pasando sin cambios.
- [ ] **Paso 2:** ejecutar `gradle -p jvm-check test --tests '*DistrustedCertificatesTest*' --offline --no-daemon`. Deben fallar los tres tests nuevos.
- [ ] **Paso 3: implementar en `DistrustedCertificates.kt`.** Cada `checkServerTrusted`:
  1. `refuse` de la cadena presentada;
  2. después, la llamada al sistema, como hoy;
  3. después, `refuse(trustedChain(chain, authType, host))`.

  El `host` sale de `(socket as? SSLSocket)?.handshakeSession?.peerHost` o de `engine?.handshakeSession?.peerHost`, y en la sobrecarga sin socket es `null`. `checkClientTrusted` y `getAcceptedIssuers` no cambian. El KDoc de la clase explica la caché de intermedios de Conscrypt (con `TrustManagerImpl`, «intermediateIndex») y por qué se comprueba antes y después.
- [ ] **Paso 4: implementar `androidTrustedChain`.**
  - Usa `X509TrustManagerExtensions(system).checkServerTrusted(chain, authType, host)`.
  - Con `host == null`, lanza `CertificateException`: falla cerrado.
  - Cablearlo en `AppContainer.client` con `secureClient(appVersion) { androidTrustedChain(it) }`.
  - `HttpClientTest` comprueba que `secureClient` sin función sigue construyendo el cliente como hoy.
- [ ] **Paso 5:** las dos verificaciones globales, con 0 fallos.
- [ ] **Paso 6: commit** «net: la desconfianza de eMudhra mira también la cadena validada (CRY-B01)» y push.

