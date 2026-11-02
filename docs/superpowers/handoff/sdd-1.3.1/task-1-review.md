# Task 1 review: CRY-B01 (d228e41..cbf3721)

### Spec Compliance
- ✅ **Spec compliant.** This includes the wiring deviation the controller ruled on, which I judge correct and complete.
  - **Interfaces match the brief exactly:**
    - `typealias TrustedChain` at `net/DistrustedCertificates.kt:78`;
    - `DistrustingTrustManager(base, trustedChain = { chain, _, _ -> chain.toList() })` at `:96-99`;
    - `distrustCompromisedCas(trustedChainOf)` at `:143-146`;
    - `secureClient(appVersion, trustedChainOf)` at `net/HttpClient.kt:15,17`;
    - `androidTrustedChain(system)` at `android/TrustedChain.kt:17-23`.
  - **Order** is refuse, then system, then refuse, in one helper (`DistrustedCertificates.kt:107-111`). All three overloads use it (`:113-114`, `:117-120`, `:122-125`).
  - **Host:**
    - `null` without a socket (`:114`);
    - `(socket as? SSLSocket)?.handshakeSession?.peerHost` (`:118`);
    - `engine?.handshakeSession?.peerHost` (`:123`).
  - **Unchanged:** `checkClientTrusted` and `getAcceptedIssuers` appear only as context lines (`:127-135`).
  - **KDoc** (`:80-95`) names TrustManagerImpl and «intermediateIndex», and explains why the chain is checked both before and after the system.
  - **`androidTrustedChain`:**
    - uses `X509TrustManagerExtensions(system)` (`TrustedChain.kt:18`);
    - throws `CertificateException` when `host == null` (`:20`);
    - otherwise calls `checkServerTrusted(chain, authType, host)` (`:21`).
  - **Wiring:**
    - the constructor parameter is at `AppContainer.kt:80-84` and is used at `:106`;
    - `SeguridadApp.kt:30` passes `{ androidTrustedChain(it) }`.
    - Complete, checked against the named risk "some client or container is built without the Android chain":
      - `SeguridadApp.kt:18` is the only `AppContainer(` in `app/src/main`; the other hits are in `AppContainerTest`;
      - `AppContainer.kt:106` is the only `secureClient(` call site.
    - `jvm-check` excludes `**/android/**` and `**/SeguridadApp.kt` (`jvm-check/build.gradle.kts:9,11`), so the deviation was needed.
  - **Step 1 tests:**
    - all three are present: `DistrustedCertificatesTest.kt:164`, `:172` and `:187`;
    - they cover all three overloads, with a `null` socket or engine;
    - the fake engine supplies `peerHost`, and a `null` engine gives `host = null` (`:191-192`, `:196`);
    - the existing tests are unchanged (the diff only adds lines to both test files).
  - **"As today" test:** `HttpClientTest.kt:33-41`.
  - **Commit subject** matches step 6.
- ⚠️ **Cannot verify from the diff:**
  - The commit author and trailer (`Co-Authored-By` only, no `Claude-Session`) and the push. I ran no git commands, as instructed.
  - The test counts 1208 (jvm-check) and 1213 (app). They are consistent with the +6 tests the diff shows (3 + 2 + 1).
  - On a phone only:
    - that the second, hostname-aware validation accepts the real `api.pwnedpasswords.com` chain;
    - that `peerHost` is non-null during the callback;
    - that Certificate Transparency or OCSP, which are not passed to the second validation, do not break it (targetSdk 36 at `app/build.gradle.kts:32`, no custom Network Security Config in `res/xml/`).

  This is the owner's phone test: «Comprobar contraseña» and «Salud» must still give a result.

### Strengths
- **It is the audit's two-layer fix**, design option 2 including option 1, with no scope creep beyond the justified socket fake and the source test.
- **Strong spy test** (`DistrustedCertificatesTest.kt:172-184`):
  - it runs per overload, with both an `X509ExtendedTrustManager` spy and a plain `X509TrustManager` spy;
  - so it also covers the `?:` fallback branch (`DistrustedCertificates.kt:119`, `:124`).
- **The fake `SSLSocket` goes beyond the brief and is the right call** (`DistrustedCertificatesTest.kt:193`, helper `:467-487`). Socket is the real path:
  - Conscrypt calls the `Socket` overload of an `X509ExtendedTrustManager`;
  - `ConscryptEngineSocket` redirects engine calls to its socket.
- **The right trust manager instance is fed to `X509TrustManagerExtensions`:**
  - it gets the system one (`DistrustedCertificates.kt:145-146`), not the wrapper;
  - `HttpClientTest.kt:44-50` locks this in. Fed the wrapper, `X509TrustManagerExtensions` would throw IAE, because `DistrustingTrustManager` has no `(X509Certificate[], String, String)` method.
- **JVM behaviour without the function is unchanged:**
  - the default `TrustedChain` refuses the same presented chain again (`:98`);
  - the set of accepted chains is identical to 1.3.0, and only which `CertificateException` is thrown first changes;
  - the existing test at `DistrustedCertificatesTest.kt` (the «app's HTTP client» test) already tolerates either exception.
- **Lint is clean for this task:**
  - in the changed files, `app/build/reports/lint-results-debug.xml` has only the `CustomX509TrustManager` warning at `DistrustedCertificates.kt:96`;
  - the class existed before this task;
  - there is no `TrustAllX509TrustManager` and no `NewApi` warning.

### Issues
#### Critical (Must Fix)
None.

#### Important (Should Fix)
None.

#### Minor (Nice to Have)
1. **A construction failure would crash the app instead of failing closed.**
   - **Where:** `android/TrustedChain.kt:18`. `X509TrustManagerExtensions(system)` runs eagerly while the client is built.
   - **What happens:** it throws `IllegalArgumentException` if the system trust manager lacks `checkServerTrusted(X509Certificate[], String, String)`.
     - That runs inside `AppContainer.client` (`AppContainer.kt:106`), which is reached through `pwned` (`:252`).
     - `pwned` is evaluated during composition (`ui/AppNavHost.kt:212`, `:248`), outside any `try`.
     - The result would be an app crash on opening «Comprobar contraseña» or «Salud», instead of the designed fail-closed «No se pudo comprobar. Revisa la conexión.».
   - **Likelihood:** theoretical on API 26+ AOSP.
     - The default trust manager is Network Security Config's `RootTrustManager`, which has the method.
     - OkHttp's `AndroidCertificateChainCleaner` relies on the same constructor, and treats IAE as an expected outcome.
   - **Fix:** catch `IllegalArgumentException` in `androidTrustedChain` and return `{ _, _, _ -> throw CertificateException("…") }`. Or record the accepted risk.
2. **No test checks that the returned `TrustedChain` is actually used.**
   - **Where:** `DistrustedCertificates.kt:146`, `DistrustingTrustManager(system, it(system))`, is the only link between the Android chain and the check.
   - **Gap:** `HttpClientTest.kt:44-50` only proves that the factory receives the system trust manager.
     - A mutation such as `trustedChainOf?.let { it(system); DistrustingTrustManager(system) }` passes every test, `PlatformSourcesTest.kt:296-303` included.
   - **Fix:** either
     - expose `trustedChain` as `internal val` and add `assertSame(returnedFn, (client.x509TrustManager as DistrustingTrustManager).trustedChain)`, or
     - add `"DistrustingTrustManager(system, it(system))"` to the source test.
3. **The report's rationale overstates the 1.3.0 dependency on `peerHost`.** No code change is needed.
   - **What the report says** (Concern 2): `RootTrustManager` "fails if [peerHost] is null", so 1.3.0 already depended on it.
   - **What `RootTrustManager` does:**
     - it throws only when the handshake *session* is null;
     - a null host maps to the default config (`ApplicationConfig.getConfigForHostname`).
   - **So** `TrustedChain.kt:20` is strictly more demanding than 1.3.0.
   - **In practice** `peerHost` is the hostname OkHttp passes to `createSocket(socket, host, port, true)`, so it is set. The owner's phone test is still the only real evidence: keep it as a mandatory item, not a formality.
4. **Pre-existing lint noise.**
   - **Where:** `CustomX509TrustManager` at `DistrustedCertificates.kt:96` (`lint-results-debug.xml:149-163`). The declaration line was rewritten in this diff.
   - **Status:** not new and not blocking.
   - **Optional fix:** `@Suppress("CustomX509TrustManager")`, with the one-line reason "always delegates to the system first". Use `@Suppress`, not `@SuppressLint`, because `net/` compiles on the JVM.
5. **Nit: the issuer comparison relies on iteration order.**
   - **Where:** `HttpClientTest.kt:39` compares `acceptedIssuers` from two separate TrustManagerFactory instances as ordered lists.
   - **Why it matters:** the test relies on the JVM trust store iterating in the same order both times. It passes, but comparing as sets would be sturdier.

### Assessment
**Task quality:** Approved

**Reasoning:** The fix matches the audit and the design, layer by layer:
- the system never sees a compromised presented chain;
- the chain Android validated is checked afterwards;
- it fails closed without a host;
- JVM behaviour is unchanged.

The wiring through the constructor is complete, with a single construction site and a single client. The remaining items are Minor hardening and test-tightness points, plus the phone test the plan already requires.

### Revisión experto-seguridad-cripto
Lenses: `SKILL.md` (threat model first, evidence, fail closed) and `references/cripto-moderna.md` §TLS («no desactives la verificación»).

- **Threat model:** an on-path attacker holding a compromised eMudhra intermediate key that is cross-signed by AAA.
  - The assets are the 5-character SHA-1 prefix and the integrity of the HIBP answer.
  - The attack runs in two connections: the first fills Conscrypt's cache, the second sends only the leaf.
- **Cache poisoning through this client is closed:**
  - `refuse(chain.asList())` runs before `system()` (`DistrustedCertificates.kt:108-109`), in all three overloads;
  - test: `DistrustedCertificatesTest.kt:172-184`.
- **A cache filled another way is caught:**
  - the chain the system validated is refused (`:110`);
  - on Android it comes from `X509TrustManagerExtensions.checkServerTrusted` (`TrustedChain.kt:21`), over the same process-wide `TrustManagerImpl` and cache;
  - test: `:164-169`.
- **Two validations instead of one is sound:**
  - with `pathlen:0`, every path that validates a leaf signed by a compromised key must contain a certificate with that SPKI;
  - so path ambiguity between the two validations cannot route around the SPKI check (`:69-70`);
  - the validated chain also includes the trust anchor, so the eMudhra root-name check is stronger than before.
- **Never weaker than the system:**
  - `system()` always runs and nothing is caught (`:109`, `:114`, `:119`, `:124`);
  - the second layer only adds refusals.
- **Fails closed:**
  - with no host (`TrustedChain.kt:20`);
  - when the second validation fails, its exception propagates and Conscrypt turns any callback exception into a handshake failure;
  - the UI shows the existing fixed text (`ui/screens/SecurityToolsScreen.kt:1029-1030`).
  - One exception is the construction-time IAE path (Minor 1).
- **TLS resumption:** the session cache belongs to this client's own `SSLContext` (`DistrustedCertificates.kt:147`), so only sessions validated through this manager can resume.
- **Hermetic:**
  - only a platform API is added (`android.net.http.X509TrustManagerExtensions`);
  - no dependency, permission or host is added;
  - there is a single client (`AppContainer.kt:106`);
  - nothing is logged;
  - the exception messages carry only certificate subject names or fixed text, and are never shown.
- **Residual, availability only and fail-closed:**
  - the second validation gets neither stapled OCSP nor TLS-extension SCTs;
  - if CT applied and the certificate had only TLS SCTs, the leak check would say «No se pudo comprobar…»;
  - this is a phone-test item (⚠️).
- **References:** MASVS-NETWORK-1 and -2; CWE-295.

### Revisión Ingeniero Móvil Senior
Lenses: `seguridad-criptografia-movil` (Red: standard TLS validation, never disable it) and `desarrollo-movil-senior`.

- **Platform split:**
  - Android-only code lives in `android/`, which `jvm-check` excludes (`jvm-check/build.gradle.kts:9,11`);
  - it is injected through the `AppContainer` constructor (`AppContainer.kt:80-84`), the same pattern as `revokeShare` (`:79`);
  - there is one construction site (`SeguridadApp.kt:18-31`);
  - `net/` stays plain JVM and testable.
- **API levels:**
  - `X509TrustManagerExtensions` with the duck-typed `RootTrustManager`, and `SSLSocket` or `SSLEngine.getHandshakeSession()`, are all API 24 or lower;
  - minSdk is 26 (`app/build.gradle.kts:31`);
  - lint reports no `NewApi`.
- **Real OkHttp path:**
  - Conscrypt calls the `Socket` overload with its own `SSLSocket`;
  - the host is read where `RootTrustManager` reads it (`DistrustedCertificates.kt:118`);
  - covered by `DistrustedCertificatesTest.kt:193`.
  - A failure to get the host becomes a failed handshake, and the user sees «No se pudo comprobar. Revisa la conexión.» (`SecurityToolsScreen.kt:1030`), not a crash.
- **Lifecycle and concurrency:**
  - the client is a lazy process singleton (`AppContainer.kt:106`);
  - `X509TrustManagerExtensions` is built once per client (`TrustedChain.kt:18`);
  - the returned lambda and `DistrustingTrustManager` hold no mutable state, so they are safe for concurrent connections.
- **Threading:**
  - the client is built during composition (`ui/AppNavHost.kt:212`, `:248`), so on the main thread;
  - that was already true in 1.3.0, and the new reflective lookup costs very little;
  - the only concern is Minor 1, where an exception would crash rather than degrade.
- **Performance:** one extra chain validation per full handshake. «Salud» reuses the pooled connection.
- **Lint and warnings:** only the existing `CustomX509TrustManager` (Minor 4). The report says Kotlin gives no warnings on the touched files, and the passing `:app:assembleDebug` is consistent with that.
- **Texts:** none new for the user. Nothing goes to the Abogado Digital.
