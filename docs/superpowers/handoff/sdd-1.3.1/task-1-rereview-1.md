# Task 1 re-review, round 1: CRY-B01 fix (4cc8fd2..f0a1b63)

Scope: the five Minor findings of `task-1-review.md`, and the fix diff (`f0a1b63`, 4 files, +22/-8). I read the diff once and ran no git commands. I did not re-run the suite. I read the current files and the existing lint report to check line numbers and one claim.

### Finding Verdicts

- **1. `X509TrustManagerExtensions` built eagerly; an IAE would crash composition instead of failing closed** — ADDRESSED.
  - `android/TrustedChain.kt:22-26`: the constructor is in a `try`. On `IllegalArgumentException` the function `return`s a `TrustedChain` that always throws `CertificateException` (with the cause). It never accepts, so it is fail-closed.
  - The client build (`AppContainer.client`, reached from composition at `ui/AppNavHost.kt:212` and `:248`) can no longer throw for this cause.
  - The KDoc (`:15-19`) states both fail-closed cases, and says the no-host rejection is stricter than the system.
  - Coverage: source-level only, `PlatformSourcesTest.kt:296-305`. The new regex `catch \(e: IllegalArgumentException\) \{\s*return \{ _, _, _ -> throw CertificateException\(` matches the real lines `TrustedChain.kt:24-25` (checked by hand against the file as it stands; `code()` at `PlatformSourcesTest.kt:25` strips only comment lines).
    - The brief's pattern for `android/` code that the JVM cannot run is the same, and the review's own fix suggestion was the catch plus a throwing function. A source regex is weak, but it is acceptable here.
  - The report shows a RED run (the source test failing without the catch) and a GREEN run (32 of 32).
- **2. No test proves the returned `TrustedChain` is used by `distrustCompromisedCas`** — ADDRESSED.
  - `DistrustedCertificates.kt:99-100`: `internal val trustedChain`, with a KDoc that names the reason.
  - `HttpClientTest.kt:50-51` (diff lines 126-131): `assertSame(validated, (client.x509TrustManager as DistrustingTrustManager).trustedChain)`. `validated` is a named val, so identity is meaningful. `assertSame` is already imported (`HttpClientTest.kt:7`), and `TrustedChain` resolves in the same package.
  - This closes exactly the mutation the review named (`trustedChainOf?.let { it(system); DistrustingTrustManager(system) }`). The report says it ran that mutation and the test failed with «expected same». I did not re-run it, but the logic holds: the default lambda is a different instance from `validated`.
  - The `internal` widening is small (same module only, a function, not a secret).
- **3. Report overstated the 1.3.0 dependency on `peerHost`** — ADDRESSED.
  - Report «Preocupaciones» 2 now says `RootTrustManager` fails only when the handshake *session* is null, that a null host takes the default configuration, that `TrustedChain.kt` is therefore stricter than 1.3.0, and that the phone test is mandatory.
  - The round-1 sections repeat it (changes item 3; cripto review «Más estricto que la 1.3.0 sin host»; móvil review «Prueba en el móvil obligatoria»).
  - The code KDoc was corrected as well (`TrustedChain.kt:16-17`).
- **4. `CustomX509TrustManager` lint warning on the rewritten line** — ADDRESSED.
  - `DistrustedCertificates.kt:96` carries `@Suppress("CustomX509TrustManager")`, with the reason on the same line. It is `@Suppress`, not `@SuppressLint`, so `net/` still compiles on the JVM. The suppression is for one named lint id only, and the class still always calls the system first.
  - Verified: `app/build/reports/lint-results-debug.xml` (13:41:10) is newer than the edited sources (13:36:18 and 13:36:26), has 0 `CustomX509TrustManager`, and no entry for `DistrustedCertificates.kt` or `TrustedChain.kt`. The 10 remaining warnings are DiscouragedApi 1, GradleDependency 2, NewerVersionAvailable 6 and ObsoleteSdkInt 1, matching the report.
- **5. `HttpClientTest` compared `acceptedIssuers` as ordered lists** — ADDRESSED.
  - `HttpClientTest.kt:39` (diff line 116): both sides are now `.toSet()`.

### New Breakage in the Fix Diff
None.
- Nothing in the diff changes the runtime behaviour of the `checked` path. The only production changes are a `try/catch` around the constructor and an `internal` modifier.
- `catch` returns from inside the `val extensions = try { … }` expression. The branch has type `Nothing`, so it type-checks, and the report's `:app:assembleDebug` and 1213-test run confirm it compiles.
- No new `//` or `*` line trips the forbidden-words scan or `SourceTextsTest`: the new exception message is English, internal and not shown.

### Out-of-Scope Observations
- Nit, report only: «Preocupaciones» 4 in the original body of `task-1-report.md` (line 150) still says the construction risk is «no se protege más». The round-1 section supersedes it by protecting it. No code impact; fix it only if the report is kept as a record.
- The failing-closed `TrustedChain` for the IAE path is covered only by a regex on the source, not by behaviour. This is unavoidable while `android/` is outside `jvm-check`, and the path is theoretical on API 26+.
- The phone-test items stay open and mandatory: «Comprobar contraseña» and «Salud» must give a result (second validation with the host, non-null `peerHost`, no CT or OCSP surprise).

### Verdict
**Fix round:** All findings addressed, no new Critical/Important breakage.
