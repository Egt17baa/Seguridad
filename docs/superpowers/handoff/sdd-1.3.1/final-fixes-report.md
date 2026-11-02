# Informe de la ronda de arreglos de la revisión final de la 1.3.1 (F1-F9)

- **Rama:** `ccr-a1a6eabb-765f9g`. Base de la ronda: `688b482` (`e6796db` más el registro).
- **Estado:** F1, F2 y F4 a F9 hechos, probados, con commit y push. En la primera entrega F3 quedó parado: la condición (2) era falsa para el diseño decidido (§F3).
- **Ronda 2:** el controlador eligió para F3 la opción A y pidió volver al orden de la 1.3.0 en F1 con la versión declarada. Las dos cosas están hechas, con commit y push: ver la sección «Ronda 2» al final, que manda sobre §F1 («El orden») y §F3.
- **Commits** (autor `Claude <noreply@anthropic.com>`, el mensaje acaba solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, `grep -cE "Claude-Session|claude\.ai"` = 0 en los cuatro, push después de cada uno, sin `amend`, `rebase` ni `force`):

| Commit | Grupo | Asunto |
|---|---|---|
| `37d50f2` | F1 + F2 | huella: solo se desactiva si su clave no autentica la bóveda (F1, F2) |
| `2e99040` | F4 + F5 | 2FA: TOTP no admitido en la lista y en el inspector, y la clave sin copias (F4, F5) |
| `68c38ed` | F6 + F7 | listas de palabras: una vez por proceso, fuera del hilo principal, y en el APK (F6, F7) |
| `86b400a` | F8 + F9 | cripto: Dialect con una sola fuente del prefijo SG1: y KDoc de la segunda validación TLS (F8, F9) |

- **Skills leídas:** `experto-seguridad-cripto` (`SKILL.md`, `references/cripto-moderna.md` §2, §3 y §10, y `references/auditoria.md` §4 como lista de comprobación del diff); de Ingeniero Móvil Senior, `desarrollo-movil-senior` y `seguridad-criptografia-movil`; `superpowers:test-driven-development`.
- **Sin subagentes.** No se ha tocado `CLAUDE.md`, el README, la plantilla del PR, el diseño, el plan, `docs/` ni los datos de prueba (`app/src/test/resources/seguridad/`, `vectors/`).

## Verificaciones globales

Antes de cada commit, una vez, las dos (con `export ANDROID_HOME=/opt/android-sdk`):

```
gradle -p jvm-check test --no-daemon --max-workers=1 --offline
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
```

| Antes de | jvm-check | app | Resultado |
|---|---|---|---|
| (base de la ronda) | 1269 | 1274 | — |
| `37d50f2` (F1 + F2) | 1273, 0 fallos | 1278, 0 fallos | `BUILD SUCCESSFUL in 1m 8s` / `BUILD SUCCESSFUL in 4m 2s` |
| `2e99040` (F4 + F5) | 1276, 0 fallos | 1281, 0 fallos | `BUILD SUCCESSFUL in 1m 5s` / `BUILD SUCCESSFUL in 3m 32s` |
| `68c38ed` (F6 + F7) | 1281, 0 fallos | 1286, 0 fallos | `BUILD SUCCESSFUL in 1m 3s` / `BUILD SUCCESSFUL in 3m 46s` |
| `86b400a` (F8 + F9) | 1281, 0 fallos | 1286, 0 fallos | `BUILD SUCCESSFUL in 1m 5s` / `BUILD SUCCESSFUL in 3m 6s` |

Los números salen de los XML de `jvm-check/build/test-results/test/` y `app/build/test-results/testDebugUnitTest/` (suma de `tests`, `failures` y `errors`; 0 omitidos). `checkFragmentVersion`: `androidx.fragment:fragment 1.9.1 (>= 1.3.0)`. `lintDebug` pasa con los 10 avisos de siempre (`NewerVersionAvailable` ×6, `GradleDependency` ×2, `DiscouragedApi` en `AndroidManifest.xml:32` y `ObsoleteSdkInt` en `mipmap-anydpi-v26`), ninguno de esta ronda. Los tests crecen de 1274/1269 a 1286/1281 (+12 en cada uno); ninguno se ha quitado.

Durante el trabajo se ejecutaron los tests de lo que se tocaba (`gradle -p jvm-check test --offline --max-workers=1 --tests …`).

---

## F1. La huella solo se desactiva cuando su clave no autentica la bóveda

**Qué se cambió:**
- `vault/Vault.kt:305` `VaultCrypto.opens(file)`: si esta versión abre el archivo, sin descifrar nada (envuelve `supported`).
- `vault/Vault.kt:313-321` `VaultCrypto.authenticated(file, dek)`: el archivo con su versión real si la DEK **autentica** el bloque de datos con AES-256-GCM en alguna versión candidata, o `null`. No decodifica el JSON y borra lo descifrado (`json.fill(0)`). Elegí esta función nueva en vez de apoyarme en `resolve`, porque `resolve` no prueba la clave cuando el archivo dice su versión (v2 y v3) y `afterMasterUnlock` lo sigue necesitando así; es lo más simple que cubre todas las versiones sin duplicar el descifrado.
- `vault/Vault.kt:201` `dataAad` pasa de `private` a `internal` (como `keyAad`), solo para que un test selle unas entradas que autentican y no se decodifican.
- `vault/VaultStore.kt:124` `class StaleBiometricKey : Exception(FINGERPRINT_STALE)`: no es una `CryptoFailure`.
- `vault/VaultStore.kt:463-485` `unlockWithKeyLocked`, en este orden:
  1. `!VaultCrypto.opens(v)`: borra la clave y lanza `MasterRequired(UNSUPPORTED)`, **sin usarla** (`:466-469`);
  2. `VaultCrypto.authenticated(v, key) ?: run { key.fill(0); throw StaleBiometricKey() }` (`:473-476`);
  3. `biometricRefusal` sobre el archivo resuelto, como antes;
  4. `opened()`: una `CryptoFailure` aquí es un JSON que autentica y no se decodifica.
- `vault/VaultStore.kt:108` `launchUnlockWithKey` captura `StaleBiometricKey` (antes `CryptoFailure`): solo ella llama a `onWrongKey` y enseña `FINGERPRINT_STALE`. Una `CryptoFailure` va ahora a `onError(biometricUnlockError(e))`, «Contraseña incorrecta o datos dañados», y conserva la huella.
- `vault/VaultStore.kt:75` `biometricUnlockError` reconoce también `StaleBiometricKey` (su mensaje es `FINGERPRINT_STALE`).
- La clave se borra en toda salida: `:466-469`, `:473-476`, la negativa de `biometricRefusal`, `opened()` y el `catch (Throwable)` de `unlockWithKey` (`if (dek !== key) key.fill(0)`), que cubre cualquier otra excepción.
- KDoc de `launchUnlockWithKey` (`vault/VaultStore.kt:79-95`, F9) y de `unlockWithKey` (`:446-452`): dicen exactamente qué desactiva la huella y en qué orden se mira.

**El orden, y qué cambia:** el orden es el de N-01 y es el mismo para todas las versiones: primero si se abre, después si la clave autentica y después la regla de los 7 días y el reloj.
- `VERSION_UNKNOWN` **no cambia**: la clave de otra bóveda da `FINGERPRINT_STALE` y la buena con la maestra pendiente da `MasterRequired` (`VaultStoreTest.kt:1033`, sin tocar, salvo el comentario).
- **Cambia la v2 y la v3 con la maestra pendiente:** antes, la clave de otra bóveda daba `MasterRequired` y conservaba la huella hasta el siguiente desbloqueo con huella tras la maestra; ahora da `FINGERPRINT_STALE` enseguida, como `VERSION_UNKNOWN`. `MasterRequired` sigue sin desactivar nunca la huella: la desactiva la clave vieja, no los 7 días. La clave buena con la maestra pendiente sigue dando `MasterRequired` y conserva la huella (`CRY-A02 the 7-day rule asks for the master password and keeps the fingerprint`, sin cambios). Coste: un descifrado AES-GCM más del bloque en cada desbloqueo con huella (N-01), y el bloque se descifra y se borra también cuando después la regla de los 7 días dice que no (con `VERSION_UNKNOWN` ya pasaba).

**Tests:**
- `VaultStoreTest.kt:1157` `F1 entries that authenticate but do not decode give the usual error, fingerprint kept` (v3 y `VERSION_UNKNOWN`): `onWrongKey` no se llama, el texto es el de `CryptoFailure()`, la bóveda sigue bloqueada y la clave a cero.
- `VaultStoreTest.kt:1172` `F1 a vault of another format and no version asks for the master, the key untried`: `format = "otra-cosa"` sin `version`. La clave buena y la de otra bóveda dan lo mismo, `MasterRequired(UNSUPPORTED)`, y eso prueba que no se usa la clave: probada contra el AAD de otro formato no autenticaría y daría `StaleBiometricKey`. Además `onWrongKey` no se llama, el texto es `NOT_A_VAULT`, la clave queda a cero y `vault.bin` no cambia.
- `VaultStoreTest.kt:1200` `F1 the stale key is its own exception, not a CryptoFailure, and keeps its text`.
- `VaultStoreTest.kt:1098` (cambiado: antes `CRY-A02 with the master password due, a version-3 vault asks for it before trying the key…`): ahora una v3 con la maestra pendiente y la clave de otra bóveda da `FINGERPRINT_STALE` y `onWrongKey` una vez. Cambia porque fijaba el orden anterior de la v2 y la v3.
- `VaultV3Test.kt:522` `deLaVersion3NoSePasaALa2PorLaHuella` y `:542` `unaClaveEnvueltaDeLaVersion2JuntoAUnosDatosDeLaVersion3SeRechaza` (cambiados): `failsWith<CryptoFailure>` pasa a `failsWith<StaleBiometricKey>` en `unlockWithKey`, porque son claves que no autentican (una v3 que dice ser v2 y los híbridos). Lo que ve el usuario no cambia (`FINGERPRINT_STALE`, como antes por la `CryptoFailure`).
- Siguen igual y en verde: la DEK de otra bóveda da `FINGERPRINT_STALE` (`CRY-A02 another vault's key turns the fingerprint off once…`), `BOV-3 a refused fingerprint key…`, el reloj, cualquier otro fallo, la cancelación y `onWrongKey` que lanza.

**RED** (antes del cambio, con los habilitadores sin comportamiento: `dataAad` `internal`, `UNSUPPORTED` en el `enum` sin usar y una `StaleBiometricKey` vacía):

```
VaultStoreTest > F1 the stale key is its own exception, not a CryptoFailure, and keeps its text FAILED
    java.lang.AssertionError: expected:<La huella ya no abre esta bóveda (…)> but was:<null>
VaultStoreTest > F1 with the master password due, another vault's key is stale for a version-3 vault too, … FAILED
    java.lang.AssertionError: expected:<1> but was:<0>
VaultStoreTest > F1 a vault of another format and no version asks for the master password without trying the key FAILED
    java.lang.AssertionError: app.seguridad.crypto.CryptoFailure: Contraseña incorrecta o datos dañados
VaultStoreTest > F1 entries that authenticate but do not decode give the usual error and keep the fingerprint FAILED
    java.lang.AssertionError: version 3 expected:<0> but was:<1>
VaultStoreTest > F2 the unlock screen says a vault it cannot open is not compatible and shows no 7-day notice for it FAILED
VaultV3Test > biometricRefusalIsUnsupportedForAVersionOrFormatThatDoesNotOpen FAILED
    java.lang.AssertionError: version 1 expected:<UNSUPPORTED> but was:<MASTER_DUE>
VaultV3Test > biometricRefusalSaysWhyTheClockOrTheSevenDays FAILED
    java.lang.AssertionError: expected:<UNSUPPORTED> but was:<MASTER_DUE>
108 tests completed, 7 failed
```

(Los nombres de algunos tests se acortaron después a 120 columnas; son los mismos tests.)

**GREEN:** `gradle -p jvm-check test --offline --max-workers=1 --tests 'app.seguridad.vault.*'` → `BUILD SUCCESSFUL`, 504 tests, 0 fallos. En el primer GREEN fallaron dos tests de `VaultV3Test` que fijaban `CryptoFailure` para una clave que no autentica (`deLaVersion3…` y `unaClaveEnvuelta…`): se adaptaron, como se explica arriba.

## F2. Una bóveda que esta versión no abre no parece una contraseña olvidada

**Qué se cambió:**
- `vault/Vault.kt:434` `enum class BiometricRefusal { MASTER_DUE, CLOCK, UNSUPPORTED }`, y `:423` `!supported(file) -> BiometricRefusal.UNSUPPORTED` (antes `MASTER_DUE`), antes del reloj y de los 7 días.
- `vault/VaultStore.kt:131-139` `MasterRequired`: `CLOCK` lleva `CLOCK_CHANGED_TEXT`, `UNSUPPORTED` lleva `NOT_A_VAULT` (un texto que ya existía, y que no se espera ver porque la pantalla no ofrece la huella) y `MASTER_DUE` el de siempre.
- `vault/Vault.kt:224` `NOT_A_VAULT` pasa de `private` a `internal`.
- `ui/screens/VaultScreen.kt:342`, desbloqueo con la maestra: `error = if (e.message == NOT_A_VAULT) NOT_A_VAULT else "Contraseña incorrecta"`.
- `ui/screens/VaultScreen.kt:358-361`: el aviso de los 7 días solo para `MASTER_DUE`, y `BiometricRefusal.UNSUPPORTED, null -> Unit`. Sin texto nuevo. El botón de la huella sigue sin ofrecerse (`store.biometricAllowed` es `false`).
- KDoc verdaderos: `vault/Vault.kt:408-418` (`biometricAllowed`/`biometricRefusal`), `:430-434` (`BiometricRefusal`), y el comentario de `VaultV3Test.kt:681-684`.

**Tests:**
- `VaultV3Test.kt:685` `biometricRefusalIsUnsupportedForAVersionOrFormatThatDoesNotOpen`:
  - las versiones 1, 4 y −1, `format = "otra-cosa"` (con y sin versión) y una v4 con fechas que también darían el reloj o los 7 días dan `UNSUPPORTED`;
  - las versiones 0, 2 y 3 no cambian: `null` dentro de los 7 días, `MASTER_DUE` después y `CLOCK` con el reloj atrás;
  - `MASTER_DUE` sigue con «Escribe tu contraseña maestra (se pide cada 7 días)» y `UNSUPPORTED` lleva «No es una bóveda compatible»;
  - por la tienda, con una v4 en disco: `biometricRefusal` da `UNSUPPORTED`, `biometricAllowed` da `false`, `unlockWithKey` da `MasterRequired(UNSUPPORTED)`, la maestra correcta da `CryptoFailure(NOT_A_VAULT)` y el archivo no cambia.
- `VaultV3Test.kt:670` `biometricRefusalSaysWhyTheClockOrTheSevenDays`: la versión 1 da `UNSUPPORTED`.
- `VaultStoreTest.kt:1230` `F2 the unlock screen says a vault it cannot open is not compatible and shows no 7-day notice for it` (test de código fuente, el patrón del proyecto para `ui/`): la línea del `catch`, el import de `NOT_A_VAULT`, el aviso de los 7 días solo con `MASTER_DUE` y `BiometricRefusal.UNSUPPORTED, null -> Unit`.

**RED/GREEN:** en la salida de F1 de arriba (los tres F2 fallan antes y pasan después).

## F3. La estimación de fuerza mide caracteres: **PARADO (BLOCKED)**

No he tocado `health/Health.kt` ni `vault/MasterPolicy.kt` para esto. La tarea dice: «Si (1) o (2) no se cumplen, para y avisa». La (1) se cumple y la (2) no.

**Qué medí.** Una copia en Java de `estimateBits` de hoy, comprobada contra la clase compilada de jvm-check (`HealthKt.estimateBits`, con el mismo resultado en todos los casos, `f3-real-vs-copy.txt`), y el diseño decidido: `Normalizer.normalize(p, NFC)`, sin `Character.FORMAT` y por puntos de código (pool, rachas, secuencias y longitud), con `COMMON` también sobre la forma normalizada. Búsqueda al azar con semilla fija (`new Random(20261101L)`), 200 000 textos por grupo:

| Grupo | Iguales | Más fuerte que hoy |
|---|---|---|
| (1) NFC, plano básico, sin Cf (ASCII, letras españolas en NFC, símbolos) | 200 000 de 200 000 | 0 |
| (2) El generador de la tarea (lo anterior más emojis, letras españolas en NFD y Cf) | 65 420 | **52** |
| Cualquier punto de código asignado | — | **32** (31 con más puntos de código en NFC que unidades UTF-16) |

**Por qué falla la (2).** NFC no solo junta letra y tilde: cambia los propios caracteres.
- **Una composición rompe una racha o una secuencia.** «…I u Y» + U+0303: «I u Y» es una secuencia de teclado (`uiop`, una sola letra), pero en NFC «Y» + U+0303 es «Ỹ» (U+1EF8), que no está en la fila, y la secuencia se rompe. Ejemplo encontrado por el generador de la tarea: 24 unidades, longitud efectiva 22 → 23, de 167,4 a 175,0 bits.
- **Exclusiones de composición: una unidad pasa a ser dos puntos de código.** U+095C (ड़), U+0344 y U+FB2F se descomponen en NFC y no se vuelven a componer.
- **Singletons que pasan a ASCII y cambian el grupo de símbolos:**
  - U+212A, el signo kelvin, pasa a «K», y «ñKabcdefgh» sube de 20,9 a 21,7 bits;
  - U+037E, el punto y coma griego, pasa a «;», y la misma cadena sube de 20,9 a 21,9 bits.

**La consecuencia es la contraria de N-02.** «ड़ड़ड़ड़ड़» (U+095C ×5, cinco caracteres visibles iguales):
- hoy se rechaza: 5 unidades, `MASTER_TOO_SHORT`;
- con el diseño decidido pasa la longitud (10 puntos de código en NFC) y la estimación (66,4 bits), y su núcleo no está en las listas, así que **se aceptaría como maestra nueva**.

Evidencia, copiada junto a este informe en `.superpowers/sdd/2026-11-01-clavero-1.3.1/f3-evidence/`:
- salidas: `f3-probe.txt`, `f3-search.txt`, `f3-explain.txt`, `f3-optionA.txt` y `f3-real-vs-copy.txt`;
- fuentes en Java: `Probe.java` (las dos estimaciones), `Search.java`, `Explain.java`, `OptionA.java` y `Real.java`, que compara la copia con la clase compilada de jvm-check.

Para repetirlo: `javac -d . Probe.java Search.java && java -cp . Search`.

**Opciones para el controlador** (no he implementado ninguna):
- **A. Quedarse con el menor de los dos.**
  - Cálculo:
    - `estimateBits(p) = min(hoy(p), nfcSinCfPorPuntos(p))`;
    - la longitud de `newMasterProblem` es `min(p.length, puntos de código de la forma NFC sin Cf)`.
  - Las dos condiciones se cumplen por construcción: (1), porque en NFC, plano básico y sin Cf las dos medidas son iguales, y (2), porque el mínimo nunca supera a hoy.
  - Resultados, medidos con la copia (`f3-optionA.txt`):
    - las tres candidatas de N-02 dan `MASTER_TOO_SHORT`;
    - «ड़ड़ड़ड़ड़» sigue siendo `MASTER_TOO_SHORT`;
    - «Xq7#vB2!mK», «casa perro luz mar sol» y «contraseña123» no cambian.
  - Un detalle: «Ábaco12345» (NFD) pasa de rechazarse por la palabra a rechazarse por la estimación (42,5 bits), con el mismo `MASTER_TOO_WEAK`, que es lo que pide `MasterPolicyTest`.
  - «Salud» solo cambia, y siempre a más débil, con texto que no esté en NFC, fuera del plano básico o con Cf.
- **B. La salida que ya daba N-02:** no tocar el código y corregir el KDoc de `vault/MasterPolicy.kt:87-88`: «una racha de caracteres iguales (del plano básico, en NFC)».

## F4. TOTP no admitido: la lista y el inspector de QR

**Qué se cambió:**
- `crypto/Totp.kt:159-182`: `OtpAuthRead` (`Usable`, `UnsupportedSettings` y `NotUsable`) pasa a ser público, con `names` (servicio y cuenta, nunca la clave; `null` en `NotUsable`). `crypto/Totp.kt:190` `readOtpAuth` pasa a ser público.
- **Quitadas** `parseOtpAuth`, `otpAuthUnsupported` y `otpAuthNames`: solo envolvían ese resultado y nadie del código las usa ya. Los KDoc que las nombraban dicen ahora `readOtpAuth` (`Totp.kt`, y el comentario de `crypto/OtpMigration.kt:136`).
- `tools/qr/QrView.kt:185-198`: un solo `readOtpAuth(kind.text)`. `action = if (unsupported) QrAction.NONE else QrAction.ADD_TOTP` (`:196`), y la nota `TOTP_UNSUPPORTED_LINK` se mantiene. Lo que no es un enlace usable sigue con `ADD_TOTP`, como en la 1.3.0.
- `ui/screens/VaultEntryScreen.kt:257-266` `applyOtpAuth`: un solo `when (val read = readOtpAuth(text))` con los tres casos.
- `crypto/Totp.kt:114` `TOTP_UNSUPPORTED_ROW = "2FA no admitido"`.
- `ui/screens/VaultScreen.kt:460-477`, la fila: `if (!t.isSupported()) Text(TOTP_UNSUPPORTED_ROW, style = labelSmall, color = onSurfaceVariant, maxLines = 1)`, el mismo estilo que «${left}s». No cambia la altura de la fila: es una sola línea `labelSmall`, más baja que la columna del código (`titleMedium` y `labelSmall`). El bloque de `totpCode` sigue igual: da `null` para esos ajustes, y cualquier otro `null` (una clave no usable) sigue sin enseñar nada.

**Tests:**
- `tools/qr/QrViewTest.kt:244`: con `digits=10`, `period=300`, `algorithm=MD5`, `digits=5` y `period=5`, la acción es `NONE` (antes `ADD_TOTP`); los nombres, la nota y la clave oculta, como antes.
- `vault/VaultTotpTest.kt:345` `F4 the text of the list row is short and passes the forbidden words`.
- `vault/VaultTotpTest.kt:357` `the entry screens use the decision from Totp, not a copy of it` (cambiado; antes fijaba «la lista no cambia»):
  - `applyOtpAuth` llama a `readOtpAuth(` una sola vez, con `when (val read = readOtpAuth(text))`, `is OtpAuthRead.UnsupportedSettings -> form.error.value = TOTP_UNSUPPORTED_LINK`, y sin `parseOtpAuth(` ni `otpAuthUnsupported(`;
  - la fila enseña `TOTP_UNSUPPORTED_ROW` en `labelSmall` solo dentro de `if (!t.isSupported())`, con `totpCode(t, now)` intacto detrás;
  - la lista no usa ni `TOTP_UNSUPPORTED_LINK` ni `TOTP_UNSUPPORTED_ENTRY`.
- Los tests que usaban las tres funciones quitadas (`VaultTotpTest`, `OtpMigrationTest`) usan ahora tres ayudas privadas del propio test sobre `readOtpAuth`. Los cuerpos y los datos no cambian. `SourceTextsTest`, `AppNameTest` y los tests de TOTP siguen en verde.

## F5. Copias de la clave TOTP

**Qué se cambió:**
- `crypto/Totp.kt:23-44` `base32Decode`: escribe en un `ByteArray(clean.length * 5 / 8)` (`:31`), del tamaño exacto, sin `ByteArrayOutputStream`.
- `crypto/Totp.kt:71-78`: `totpKey` usa `longEnoughOrWiped`, que pone a cero el array corto que descarta.
- `crypto/Totp.kt:81` `isUsableTotpKey` pasa de `private` a `internal`.
- `ui/screens/VaultEntryScreen.kt:496`: `!isUsableTotpKey(totp.secret)` en lugar de `totpKey(totp.secret) == null`. El import de `totpKey` desaparece de la pantalla.
- `vault/TotpImport.kt:7`, sin tocar, como se decidió.

**Tests:**
- `vault/VaultTotpTest.kt:153` `a decoded key too short for TOTP is cleared, a long enough one is handed over as it is`.
- `vault/VaultTotpTest.kt:141` `base32 decodes into an array of its exact size`: compara, en 64 longitudes × 20 textos, con una decodificación independiente por cadena de bits. Pasaba ya antes, a propósito: fija que el cambio de `base32Decode` no altera nada.
- El test de código fuente de F4 (`:357`) comprueba también la línea de `isUsableTotpKey` y que la pantalla no llama a `totpKey(`.
- Siguen pasando:
  - los vectores de RFC 6238 (`VaultTotpTest` `totp matches RFC 6238`, `base32 and totp code with time left`);
  - la ida y vuelta de Base32 (`OtpMigrationTest.base32EncodeMatchesRfc4648`, con 200 longitudes al azar);
  - todas las entradas TOTP de los archivos reales (`every TOTP in the real files of Clavero…`).

**RED de F4 y F5** (antes del cambio, con lo que no cambia comportamiento ya hecho: `OtpAuthRead` público con `names`, `longEnoughOrWiped` todavía sin borrar y `TOTP_UNSUPPORTED_ROW = ""`):

```
QrViewTest > aCodeWithSettingsTheAppCannotCalculateStillShowsItsNamesAndSaysSo FAILED
    java.lang.AssertionError: digits=10 expected:<NONE> but was:<ADD_TOTP>
VaultTotpTest > the entry screens use the decision from Totp, not a copy of it FAILED
VaultTotpTest > a decoded key too short for TOTP is cleared, a long enough one is handed over as it is FAILED
    java.lang.AssertionError
VaultTotpTest > F4 the text of the list row is short and passes the forbidden words FAILED
    org.junit.ComparisonFailure: expected:<[2FA no admitido]> but was:<[]>
58 tests completed, 4 failed
```

**GREEN:** `--tests 'app.seguridad.vault.VaultTotpTest' --tests 'app.seguridad.tools.qr.*' --tests 'app.seguridad.crypto.*' --tests 'app.seguridad.tools.*'` → `BUILD SUCCESSFUL`.

## F6. Listas de palabras: una vez por proceso y fuera del hilo principal

**Qué se cambió:**
- `vault/MasterPolicy.kt:78-108` `class WordLists(open, io = Dispatchers.IO)`, con un `Mutex`:
  - `master()` (`:91`): `loadMasterWords(open)` en `io`; se guarda solo si no está vacío, así que tras un fallo la siguiente pantalla lo vuelve a intentar;
  - `eff()` (`:96`): la EFF sin líneas vacías, con `use` (`:101-105`), o `null` si no se lee, que tampoco se guarda;
  - si quien llama se cancela mientras carga, no queda nada a medias.
- `vault/MasterPolicy.kt:26` `EFF_WORD_LIST`, y `MASTER_WORD_LISTS` lo usa.
- `AppContainer.kt:92` `openAsset: (String) -> InputStream`, que por defecto lanza `FileNotFoundException` (la JVM no tiene assets, y la regla se queda entonces en la longitud y la estimación). `AppContainer.kt:117` `val wordLists = WordLists(openAsset)`. No hay nada de Android en la firma, y jvm-check la compila.
- `SeguridadApp.kt:32` `openAsset = { name -> assets.open(name) }`.
- `ui/screens/MasterWords.kt:24-33`: `rememberMasterWords` usa `wordLists().master()` en `LaunchedEffect(Unit)`. Sigue con `remember` y nunca `rememberSaveable`, es `null` hasta que están y los botones esperan como hoy. `wordLists()` toma `(applicationContext as SeguridadApp).container.wordLists`, como `MainActivity`.
- `ui/screens/SecurityToolsScreen.kt:937-960`, el Generador:
  - `val words by produceState<List<String>?>(null) { value = lists.eff() }`;
  - «Frase» con `enabled = words != null`;
  - la frase y sus bits solo se calculan con la lista (`phraseWords`).
- **Compartir la carga:** la EFF del Generador y la de la maestra comparten la clase, el hilo y la caché. El archivo se lee como mucho dos veces por proceso (una entre las listas de la maestra y otra para el Generador), porque `loadMasterWords` normaliza sobre la marcha. Compartir también los bytes obligaba a reescribir `loadMasterWords`, y no compensa.
- **Sin texto nuevo.** Las palabras no son secretas.

**Tests:**
- `vault/MasterPolicyTest.kt:275` `F6 the lists are read once per process, on the dispatcher given, and every stream is closed`: tres llamadas a cada una, y cada lista se abre una sola vez (la EFF dos: una por uso). Todos los flujos se cierran y todo se lee en el hilo del dispatcher dado, nunca en el de quien llama.
- `vault/MasterPolicyTest.kt:293` `F6 a list that cannot be read is not kept, so the next screen reads it again`.
- `AppContainerTest.kt:52` `the word lists are read from the assets given, once`: se cablea con `openAsset`; sin assets no hay cierre de la app, y el resultado es `emptySet()` y `null`.
- `vault/MasterPolicyTest.kt:452` `the words are loaded once per process, off the main thread, and never saved` (antes `…once per screen…`):
  - la pantalla toma las listas del proceso, espera con `LaunchedEffect(Unit) { words = lists.master() }` y no llama a `loadMasterWords`;
  - el estado es `remember` con `null` al empezar, sin `rememberSaveable`, `Bundle` ni `catch`;
  - `AppContainer` crea `WordLists(openAsset)` y `SeguridadApp` le pasa `assets.open`.
- `vault/MasterPolicyTest.kt:473` `the Generator reads the EFF list off the main thread and Frase waits for it`.

**RED** (con un `WordLists` sin caché y con `eff()` que da `null`, ya cableado en `AppContainer`):

```
AppContainerTest > the word lists are read from the assets given, once FAILED
    java.lang.AssertionError: expected:<[hola, mundo]> but was:<null>
MasterPolicyTest > checkAssets fails the build when a list of the master rule is not in the APK FAILED   (F7)
    java.lang.AssertionError: eff_large_wordlist.txt
MasterPolicyTest > F6 the lists are read once per process, on the dispatcher given, and every stream is closed FAILED
    java.lang.AssertionError: expected:<[abacus, abdomen, …]> but was:<null>
MasterPolicyTest > F6 a list that cannot be read is not kept, so the next screen reads it again FAILED
MasterPolicyTest > the words are loaded once per process, off the main thread, and never saved FAILED
    java.lang.AssertionError: the process' lists
MasterPolicyTest > the Generator reads the EFF list off the main thread and Frase waits for it FAILED
43 tests completed, 6 failed
```

**GREEN:** en el primer intento quedó uno en rojo, por un detalle del test: en modo de depuración el hilo se llama `listas @coroutine#119`. El test quita ese sufijo de kotlinx antes de comparar, y después `BUILD SUCCESSFUL`.

## F7. `checkAssets` comprueba las listas de palabras

**Qué se cambió:**
- `app/build.gradle.kts:196`: `val wordLists = listOf("eff_large_wordlist.txt", "bip39_english.txt", "bip39_spanish.txt")`.
- `app/build.gradle.kts:204-212`: `val missing = wordLists - packed`, y si no está vacío, una `GradleException` con el nombre de la lista que falta. Sigue el patrón de la comprobación de assets ajenos de la misma tarea; su entrada sigue siendo `merged`, así que vuelve a correr si cambian los assets.
- Cambian la descripción, el comentario y el `logger.lifecycle`.

**Tests:**
- `vault/MasterPolicyTest.kt:168` `checkAssets fails the build when a list of the master rule is not in the APK`: el bloque de `checkAssets` nombra cada una de `MASTER_WORD_LISTS` (así las dos listas no se separan) y lanza si falta alguna.
- `vault/MasterPolicyTest.kt:232`, una línea nueva: `loadMasterWords` con los assets del repositorio da más de 9000 palabras. Ya se cumplía de forma indirecta (`assertEquals(allWords, loaded)` y `allWords.size > 9000` en otro test); ahora es directo.

**RED y GREEN sobre la tarea de Gradle de verdad**, con `bip39_spanish.txt` sacado de `app/src/main/assets` durante la prueba y devuelto después (`git status` limpio):

```
# antes del cambio, sin la lista
./gradlew :app:checkAssets --no-daemon --max-workers=1 --offline   → exit 0
assets/: solo archivos de app/src/main/assets
BUILD SUCCESSFUL in 12s
# después del cambio, sin la lista
→ exit 1
> Al APK le faltarían listas de palabras de la contraseña maestra: bip39_spanish.txt. Sin ellas, la regla de una maestra nueva no reconoce una sola palabra: no las quites ni las excluyas en androidResources.
BUILD FAILED in 22s
# después del cambio, con la lista devuelta
→ exit 0
assets/: solo archivos de app/src/main/assets, con las tres listas de palabras
BUILD SUCCESSFUL in 11s
```

En la verificación global de `68c38ed`, `checkAssets` salió `UP-TO-DATE`: sus entradas y la tarea eran las mismas que en la última ejecución de arriba, la que pasó.

## F8. `Dialect` con un solo valor

**Qué se cambió:**
- `crypto/Crypto.kt:26-34`: `enum class Dialect(val magic: ByteArray, val labelPrefix: String)`.
  - Fuera `fileExtension`: no lo usaba nadie, y la extensión es `ENCRYPTED_EXTENSION` (`crypto/FileCrypto.kt:14`).
  - Fuera `textPrefix`: `TEXT_PREFIX` (`crypto/Crypto.kt:509`) es la única definición de «SG1:».
- `crypto/Crypto.kt:572`: `encryptText` usa `TEXT_PREFIX`, igual que `inspectText` (`:585`) y `decryptText` (`:599`).
- Tests: `tools/AppNameTest.kt:130` fija ahora `assertEquals("SG1:", TEXT_PREFIX)` (`ENCRYPTED_EXTENSION` y `labelPrefix` ya estaban fijados), y `crypto/PasswordBytesTest.kt:133` usa `TEXT_PREFIX`.
- **No cambian:** las firmas de `aead`, `cascadeKey` y `rawKeyHkdf`, los bytes ni las etiquetas HKDF (`${dialect.labelPrefix}-cascade-…` y `-raw-key`).

**Tests:** es un cambio sin comportamiento nuevo, así que lo cubren los vectores que ya hay. `--tests 'app.seguridad.crypto.*' --tests 'app.seguridad.tools.AppNameTest' --tests 'app.seguridad.tools.SourceTextsTest'` → `BUILD SUCCESSFUL`:
- `CryptoTest`: 32, con el KAT de los 6 algoritmos y la cascada;
- `DialectTest`: 7, con los textos y archivos de la 1.3.0 y de Python `cryptography`;
- `PasswordBytesTest`: 12;
- `SeedCryptoTest`: 14;
- `AppNameTest`: 8.

## F9. KDoc y comentarios

- **`CryptoTest.kt:41-53`, el KDoc de `Kat`: ya era exacto y no cambia.**
  - Dice que, para Twofish y Serpent, la comprobación de GCM es independiente de Bouncy Castle, pero el motor no (`:46-47`).
  - Dice también que el motor Twofish tiene su vector publicado en `SeedCryptoTest` (`:48`). Ese vector existe: `SeedCryptoTest.kt:40`, `57ff739d4dc92c1bd7fc01700cc8216f`, clave y bloque a cero (ecb_tbl, I=1).
  - Sobre Serpent dice que solo hay ida y vuelta.
  - No dice que Twofish no tenga vector ni que «solo es independiente la composición GCM»: esa frase la quitó `ba572c0`. La A-3 de la auditoría describía el texto anterior.
- **`android/TrustedChain.kt:21-24`, la frase de N-04:** la segunda validación no recibe el OCSP grapado ni las SCT de la extensión TLS. Si Android llegara a exigir Certificate Transparency a la app (Network Security Config, o el valor por defecto de un `targetSdk` futuro), podría rechazar un servidor que la primera acepta. Falla cerrada. Hay que probar «Comprobar contraseña» con red en cada subida de `targetSdk`. Sin cambio de código.
- **`launchUnlockWithKey`:** su KDoc (`vault/VaultStore.kt:79-95`) dice exactamente qué desactiva la huella después de F1 (ver F1).
- **Longitud de línea:** no he reformateado ninguna línea antigua de más de 120 caracteres. Todas las líneas nuevas miden 120 o menos. Superan los 120 solo líneas que ya existían y en las que cambió una palabra:
  - `vault/Vault.kt:201`, de `private` a `internal`;
  - `crypto/Totp.kt`, dos KDoc con `parseOtpAuth` → `readOtpAuth`;
  - `ui/screens/VaultEntryScreen.kt:496`, `!isUsableTotpKey`.

## Fuera de esta ronda (como se decidió)

- `BiometricVault.disable` en el hilo principal (aplazado 2 y N-05): queda para la 1.4.0. F1 no añade llamadas nuevas.
- I1, N-03, N-08 y M7 (documentos): son del paso siguiente.

## Autorrevisión del diff

- **Completo:** F1, F2 y F4 a F9 cubren cada viñeta de su decisión. F3 está parado con evidencia.
- **YAGNI:**
  - `longEnoughOrWiped` es la única costura nueva, y existe para poder probar el borrado;
  - `OtpAuthRead.names` sustituye a `otpAuthNames`, que se quita;
  - `WordLists` no cachea más de lo pedido.
- **Patrones del proyecto:**
  - visibilidad `internal` para los tests, como `keyAad`;
  - tests de código fuente para `ui/`;
  - inyección de lo de Android por el constructor de `AppContainer`, como `trustedChainOf`;
  - `produceState` en `IO`, como pedía el aplazado 1;
  - mensajes de Gradle en español, como los que ya había.
- **Salida de los tests limpia:** 0 fallos y 0 omitidos en las cuatro verificaciones. Lint, con los mismos 10 avisos de antes.
- **Hermética:**
  - `AndroidManifest.xml`, `res/`, `gradle/libs.versions.toml` y `gradle/verification-metadata.xml` no cambian;
  - no hay URL ni `Log.`, `println` o `printStackTrace` nuevos;
  - no hay `rememberSaveable` nuevo.

  Lo comprueba `git diff 688b482..HEAD`:
  - `git diff --quiet` sobre `AndroidManifest.xml`, `res/`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, `settings.gradle.kts`, `build.gradle.kts` raíz, `app/src/test/resources/seguridad/` y `vectors/`: sin diferencias;
  - líneas añadidas con `Log.`, `println(` o `printStackTrace`: 0;
  - con `rememberSaveable` o `Bundle` en el código: 0;
  - con una URL `http(s)://`: 0;
  - los cuatro commits son de `Claude <noreply@anthropic.com>`, con 0 enlaces de sesión;
  - en total, 24 archivos (+582 −153), sin nada de `.superpowers/`.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza.** Se aplica el del proyecto: un `vault.bin` manipulado pide root y queda fuera del modelo, y una copia `.sgbak` se ataca fuera de línea.
  - F1 y F2 no dan a nadie una forma nueva de desactivar la huella a distancia.
  - Ahora un fallo de decodificación ya no la desactiva (`vault/VaultStore.kt:108`, `:473-476`).
  - Un archivo que no se abre no prueba la clave (`:466-469`).
- **Sin criptografía propia.**
  - `authenticated` es el mismo `openAny`/`open` sobre AES-256-GCM de `javax.crypto` (`vault/Vault.kt:313-321`), con el AAD de siempre (`dataAad`, `:201`).
  - Distinguir «otra clave» de «bloque dañado» es imposible con un AEAD, y el KDoc lo dice (`:308-311`, `VaultStore.kt:84-86`). Un bloque dañado sigue dando `FINGERPRINT_STALE`, y la maestra tampoco lo abre.
- **Parámetros:** sin cambios. Etiqueta de 128 bits, nonce de 96 bits, AAD v2/v3, HKDF con las mismas etiquetas (F8 no toca `labelPrefix`) y TOTP con 80 bits como mínimo de clave.
- **Ramas de error y borrado:**
  - la DEK de la huella se borra en todas las salidas (`VaultStore.kt:466-469`, `:473-476`, la negativa, `opened()` y el `catch (Throwable)` de `unlockWithKey`);
  - lo que descifra `authenticated` se borra (`Vault.kt:319`);
  - `longEnoughOrWiped` borra la clave corta (`Totp.kt:74-78`);
  - `base32Decode` ya no deja un búfer creciente sin borrar (`Totp.kt:31`);
  - el editor valida con la función que borra (`VaultEntryScreen.kt:496`).

  El `String` de `TotpSecret.secret` sigue en el heap, como riesgo aceptado (A-1).
- **Entradas no fiables:** un QR `otpauth` no admitido ya no ofrece una acción que lleve a un error (`QrView.kt:196`). El enlace se analiza una vez (`:188`, `VaultEntryScreen.kt:257`), y la clave nunca va en `names` (`Totp.kt:164-180`).
- **Fuerza de la maestra (F3): parada.** El diseño decidido debilitaría la regla para ciertos textos («ड़ड़ड़ड़ड़» pasaría). La opción A se propone porque, por construcción, nunca es más débil que hoy. Un hallazgo de seguridad no se rebaja para cerrar la ronda.
- **Disponibilidad (F7, F9):**
  - la regla de las palabras ya no puede apagarse en silencio por un APK sin listas (`app/build.gradle.kts:204-212`);
  - el riesgo de CT de la segunda validación TLS está escrito donde se mantiene (`TrustedChain.kt:21-24`), y falla cerrado.
- **Formatos:** no cambia ningún byte. Lo prueban los vectores (`CryptoTest.Kat`, `DialectTest`), `VaultV3Test` (v2, v3 y sin versión) y `VaultTamperTest`, todos en verde; los datos de `seguridad/` y `vectors/` no se tocan.

## Revisión Ingeniero Móvil Senior

- **Concurrencia y ciclo de vida.**
  - `launchUnlockWithKey` sigue `ATOMIC`: la clave acaba en la bóveda abierta o a cero (BOV-3). La nueva rama `StaleBiometricKey` (`VaultStore.kt:108`) mantiene el `try` defensivo alrededor de `onWrongKey`.
  - `WordLists` serializa con un `Mutex` y hace la E/S en `Dispatchers.IO` (`MasterPolicy.kt:85-108`). La cancelación de la pantalla (`LaunchedEffect` y `produceState`) se propaga y no deja la caché a medias.
- **Hilo principal.**
  - La EFF del Generador ya no se lee en el hilo principal y su flujo se cierra (`SecurityToolsScreen.kt:939`, `MasterPolicy.kt:101-105`).
  - Las listas de la maestra ya no se leen ni se normalizan en cada apertura ni en cada giro (`MasterWords.kt:24-27`).
  - `BiometricVault.disable` sigue en el hilo principal, aplazado a la 1.4.0 como se decidió.
- **Compose y estado.**
  - Nada nuevo en `rememberSaveable` ni en `Bundle`: `MasterWords.kt` usa `remember`, y el Generador `produceState`.
  - «Frase» solo se activa con la lista (`SecurityToolsScreen.kt:957`). Como `phrase` es `remember` y no se guarda, un giro vuelve a «Contraseña» hasta que la lista, ya en caché, llega al instante.
  - La fila de la lista no cambia de altura: una línea `labelSmall` (`VaultScreen.kt:463-470`).
  - La pantalla de desbloqueo no enseña aviso con `UNSUPPORTED` (`VaultScreen.kt:361`), y el `when` sigue siendo exhaustivo.
- **Arquitectura.**
  - `AppContainer` sigue sin tipos de Android: `openAsset` es una lambda inyectada desde `SeguridadApp.kt:32`, como `trustedChainOf`.
  - `wordLists()` hace un cast directo a `SeguridadApp`, como `MainActivity`. No hay previews ni otra `Application`.
- **Plataforma y compilación.**
  - `checkAssets` sigue siendo compatible con la caché de configuración: solo captura una `List<String>` y un `ListProperty`.
  - No hay permisos, componentes exportados, dependencias ni hosts nuevos.
  - `assembleDebug` y `lintDebug` en verde en las cuatro verificaciones.
- **Tests.**
  - Comportamiento real: almacén con disco temporal, AES-GCM real y la tarea de Gradle real con un asset sacado.
  - Código fuente para `ui/`, que es el patrón del proyecto.
  - Ninguno depende del reloj ni del azar sin semilla.

## Textos para el Abogado Digital (revisión final)

**Nuevo:**
1. `TOTP_UNSUPPORTED_ROW` (`crypto/Totp.kt:114`): «2FA no admitido». Sale en la lista de la bóveda, en pequeño, en lugar del código de una entrada con un algoritmo, un número de dígitos o un periodo que Clavero no calcula (`ui/screens/VaultScreen.kt:463-470`). Pasa `ForbiddenWords` (`VaultTotpTest.kt:345`).

**Textos que ya existían y salen en sitios nuevos:**

2. `NOT_A_VAULT` «No es una bóveda compatible» (`vault/Vault.kt:224`):
   - en la pantalla de desbloqueo, en lugar de «Contraseña incorrecta», cuando la maestra se escribe sobre un `vault.bin` que esta versión no abre (`ui/screens/VaultScreen.kt:342`);
   - como mensaje de `MasterRequired(UNSUPPORTED)` (`vault/VaultStore.kt:135`), que no se espera ver porque la pantalla no ofrece la huella con ese archivo.

**Textos que dejan de salir en un caso:**

3. «Por seguridad, cada 7 días hay que escribir la contraseña maestra.» (`ui/screens/VaultScreen.kt:359`) ya no sale para un archivo que no se abre (`UNSUPPORTED`). Era el punto 12 de la lista de la revisión final.
4. `FINGERPRINT_STALE` (`vault/VaultStore.kt:65-67`) ya no sale para unas entradas que autentican y no se decodifican, que dan ahora «Contraseña incorrecta o datos dañados». Sigue saliendo para un bloque dañado, y la reserva ya apuntada sigue en pie: afirma como hecho «se cambió la contraseña maestra o se importó una copia».

**Mensaje para desarrolladores, no de la interfaz** (para que conste):

5. El error nuevo de `checkAssets` (`app/build.gradle.kts:207-210`): «Al APK le faltarían listas de palabras de la contraseña maestra: … Sin ellas, la regla de una maestra nueva no reconoce una sola palabra: no las quites ni las excluyas en androidResources.». También cambian la descripción de la tarea y su línea de registro.

**Ningún otro texto nuevo:** «Frase» solo se desactiva mientras se carga la lista.

---

## Ronda 2: F3 opción A y orden de F1

Decisiones del controlador sobre la entrega anterior (también en `progress.md`): para F3, la opción A; para F1, la regla de acceso antes de usar la clave cuando el archivo declara su versión. Esta sección sustituye, en lo que se contradiga, a §F1 («El orden, y qué cambia») y a §F3 de arriba.

**Commits** (mismas reglas: autor `Claude <noreply@anthropic.com>`, solo la línea `Co-Authored-By`, 0 enlaces de sesión, push, sin `amend`, `rebase` ni `force`). Las dos verificaciones globales se hicieron una vez, sobre el árbol con los dos cambios, antes de los dos commits. Los dos tocan archivos distintos.

| Commit | Asunto |
|---|---|
| `243054a` | huella: con una versión declarada, los 7 días y el reloj antes de usar la clave (F1, ronda 2) |
| `95ddc44` | salud: la fuerza también por puntos de código, en NFC y sin Cf, y nunca más alta (F3, opción A) |

**Verificaciones globales** (los mismos dos comandos de arriba): jvm-check **1288**, 0 fallos (`BUILD SUCCESSFUL in 1m 12s`); app **1293**, 0 fallos (`BUILD SUCCESSFUL in 3m 35s`). Lint, con los mismos 10 avisos de antes, y `checkFragmentVersion` en 1.9.1. Sobre `86b400a..HEAD`, `git diff --quiet` del manifiesto, `res/`, `gradle/`, `seguridad/` y `vectors/` no da diferencias. Las líneas añadidas tienen 0 `Log.`, `println(` o `printStackTrace` y 0 `rememberSaveable` o `Bundle` en el código. En total, 6 archivos (+305 −49). Antes de esta ronda había 1281 y 1286: +7 en cada uno (5 de F3 en `HealthTest`, 1 de F3 en `MasterPolicyTest` y 1 de F1 con el reloj; el test restaurado sustituye al cambiado).

### F1, el orden

**Qué se cambió.** `vault/VaultStore.kt:469-496` `unlockWithKeyLocked`:
1. `!VaultCrypto.opens(v)` → `MasterRequired(UNSUPPORTED)`, sin usar la clave (`:472-475`);
2. con la versión declarada (2 o 3): `refuseIfDue(v)` (`:476-482` y `:485-486`, la regla de los 7 días y el reloj) lanza `MasterRequired` **antes** de usar la clave;
3. después, `VaultCrypto.authenticated(v, key)` (`:488`), solo AEAD: si no autentica, `StaleBiometricKey`, `onWrongKey` y `FINGERPRINT_STALE`;
4. sin versión (`VERSION_UNKNOWN`), como tenía: primero la clave (que también da la versión) y después `refuseIfDue(known)` (`:492`);
5. `opened()`: un fallo de decodificación da `CryptoFailure`, el error genérico, y conserva la huella.

La clave se borra en toda salida: `refuseIfDue`, el `run` del `StaleBiometricKey`, `opened()` y el `catch (Throwable)` de `unlockWithKey`. Los KDoc dicen el orden: el de `unlockWithKey` (`:448-458`, lista de 4 pasos) y el de `launchUnlockWithKey` (`:91-93`): con la versión declarada, una clave vieja solo se descubre en el primer desbloqueo con huella después de la maestra.

**Tests:**
- Vuelve `VaultStoreTest.kt:1097` `CRY-A02 with the master password due, a version-3 vault asks for it before trying the key, so another vault's key keeps the fingerprint for now`, igual que en `688b482`.
- El comentario del test de versión desconocida vuelve también al de `688b482`, salvo una palabra que era solo del tipo de excepción: «A stale key is a StaleBiometricKey there».
- Nuevo: `VaultStoreTest.kt:1112` `F1 with the clock set back, a version-3 vault asks for the master before trying the key`. Con la clave de otra bóveda y el reloj atrás, da `CLOCK_CHANGED_TEXT`, `onWrongKey` no se llama y la clave queda a cero.
- Se quedan los cambios que eran solo del tipo de excepción: `VaultV3Test.kt:522` y `:542`, con `failsWith<StaleBiometricKey>`. Esos archivos no tienen la maestra pendiente, así que llegan a la autenticación.
- Siguen en verde los tests de F1 y F2 de la primera ronda: el JSON que no se decodifica, «otra-cosa» sin versión, `UNSUPPORTED`, la pantalla.

**RED** (con el código de la primera ronda):

```
VaultStoreTest > F1 with the clock set back, a version-3 vault asks for the master password before trying the key FAILED
    java.lang.AssertionError: expected:<0> but was:<1>
VaultStoreTest > CRY-A02 with the master password due, a version-3 vault asks for it before trying the key, so another vault's key keeps the fingerprint for now FAILED
    java.lang.AssertionError: expected:<0> but was:<1>
75 tests completed, 2 failed
```

**GREEN:** `--tests 'app.seguridad.vault.*' --tests 'app.seguridad.AppContainerTest'` → `BUILD SUCCESSFUL`.

### F3, opción A

**Qué se cambió:**
- `health/Health.kt:93-99` `estimateBits(p) = minOf(bits(p, unidades UTF-16), bits(strengthForm(p), puntos de código))`.
- `health/Health.kt:112-122` `bits(text, symbols: IntArray)` es el único núcleo: el grupo de símbolos, la lista común sobre el texto (como hoy) y la longitud efectiva. Debajo, `step(Int, Int)` y `effectiveLength(IntArray)` (`:127-156`), y la fila del teclado con `indexIn` (`:125`, las filas son ASCII).
- `health/Health.kt:101-110` `strengthForm(text)`: `Normalizer.normalize(text, NFC)` sin los puntos de código de tipo `Character.FORMAT`.
- `vault/MasterPolicy.kt:150-152` `newMasterProblem`: `MASTER_TOO_SHORT` salvo que lleguen a `MIN_MASTER` **las dos** longitudes, las unidades UTF-16 de lo escrito y los puntos de código de `strengthForm`.
- KDoc verdaderos:
  - `estimateBits` (`Health.kt:80-92`): qué se mide dos veces y por qué se queda el menor;
  - `strengthForm`;
  - `MIN_MASTER` (`MasterPolicy.kt:16-21`): la maestra nueva necesita las dos longitudes, y el diálogo de importar solo cuenta unidades UTF-16;
  - `newMasterProblem` (`:116-119` y `:133-136`).
- **Sin cambios:** el diálogo de importar (`a.length >= MIN_MASTER`), el desbloqueo, «Salud» con texto normal, `commonPasswords()` y `masterCore`.

**Tests**, en `health/HealthTest.kt`, contra `oldEstimateBits`, una copia literal de la función anterior. `F3 the copy of the old function uses the same common list` comprueba que su lista común es la de hoy.

1. `:219` `F3 the estimate is exactly the old one for NFC text of the basic plane without format characters`.
   - Generador: semilla `20261101L`, de 1 a 24 puntos de código; ASCII imprimible en el 60 %, letras españolas y símbolos en NFC en el 20 % y cualquier punto de código asignado del plano básico en el 20 %.
   - Se descarta lo que no está en NFC o lleva Cf o sustitutos, hasta reunir **200 000** casos.
   - Resultado: `assertEquals(old, new, 0.0)` en todos, igual bit a bit.
2. `:237` `F3 the estimate is never stronger and the new master rule never weaker, for any text`.
   - Generador: semilla `20261102L`, **200 000** casos de 1 a 24 puntos de código, con ASCII, español en NFC y en NFD, Cf (también U+E0001 y U+E0041, astrales), emojis, exclusiones de composición (U+0344, U+0958, U+095C, U+FB2F, U+1D15E…), singletons (U+212A, U+037E, U+2126, U+1FEF, U+2F800…), marcas combinantes sueltas, sustitutos sueltos y cualquier punto de código asignado hasta U+2FFFF.
   - En todos, `new <= old`.
   - Además, si la regla anterior (`length < 10` o `oldEstimateBits < 50`) rechazaba, `newMasterProblem` también rechaza.
   - **147 764** de los 200 000 dan una estimación menor y **ninguno** una mayor. El test exige más de 10 000, para no pasar con un generador que no llegue a los casos que cambian. El número exacto salió de una ejecución con un umbral imposible, que se deshizo después.
3. `:264` `F3 the second measure is the NFC form without format characters, by code points`. Sobre `strengthForm`:
   - «A»+U+0301+U+200B+«b» → «Áb»;
   - el emoji, U+200D y el emoji se quedan en los dos emojis;
   - U+212A → «K»;
   - U+095C → U+0921 U+093C.
4. `:272` `F3 equal emojis, letters typed decomposed and invisible characters between digits are weak`.
   - «😀»×5, «ąąąąą» en NFD, «1​2​3​4​5​» con U+200B y «😀»×10: antes daban 50 bits o más, ahora menos de 50.
   - «ą»×10 en NFD también da menos de 50.
   - U+095C ×5 y ×10 siguen por debajo.
   - El texto normal no se mueve.
5. `vault/MasterPolicyTest.kt:58` `F3 five emojis, five letters typed decomposed or five digits with invisible characters are too short`, con y sin las listas:
   - «😀😀😀😀😀», «ąąąąą» (NFD), «1​2​3​4​5​» y **«ड़ड़ड़ड़ड़»** dan `MASTER_TOO_SHORT`;
   - «😀»×10, «ą»×10 (NFD), «0​1​…​9» con U+200B y «ड़»×10 dan `MASTER_TOO_WEAK`.
6. Todo lo que fijaban hoy `MasterPolicyTest` (36 tests, con las 200 frases y las 200 contraseñas del Generador) y los tests de «Salud» de `HealthTest` sigue en verde sin cambios.

**RED** (con `strengthForm` como función vacía que devuelve el texto, sin cambiar nada):

```
HealthTest > F3 the second measure is the NFC form without format characters, by code points FAILED
    org.junit.ComparisonFailure: expected:<[Á]b> but was:<[Á​]b>
HealthTest > F3 the estimate is never stronger and the new master rule never weaker, for any text FAILED
    java.lang.AssertionError: the generator reaches the texts that change (0)
HealthTest > F3 equal emojis, letters typed decomposed and invisible characters between digits are weak FAILED
    java.lang.AssertionError: 😀😀😀😀😀: 66.43856189774726 bits
MasterPolicyTest > F3 five emojis, five letters typed decomposed or five digits with invisible characters are too short FAILED
    java.lang.AssertionError: 😀😀😀😀😀 expected:<Mínimo 10 caracteres> but was:<null>
50 tests completed, 4 failed
```

La propiedad (1) y la comprobación de la copia ya pasaban antes, a propósito: fijan que el núcleo nuevo reproduce los números de hoy.

**GREEN:** `--tests 'app.seguridad.health.*' --tests 'app.seguridad.vault.MasterPolicyTest'` → `BUILD SUCCESSFUL`, con HealthTest 14 y MasterPolicyTest 36, 0 fallos. Las dos propiedades tardan 1,2 s y 2,6 s.

### Revisión experto-seguridad-cripto (ronda 2)

- **F1.**
  - Con la versión declarada, la regla de acceso decide antes de que la clave de la huella descifre nada (`VaultStore.kt:485-486`). El contenido no pasa por la memoria cuando la regla dice que no.
  - Con `VERSION_UNKNOWN` la clave se prueba primero, porque es lo único que da la versión (`:488`, `:492`), como en la 1.3.0.
  - Solo un fallo de autenticación desactiva la huella. La clave se borra en toda salida.
- **F3.**
  - La regla de una maestra nueva y «Salud» nunca son más débiles que antes. Lo prueba la propiedad (2), con 200 000 casos que incluyen las exclusiones y los singletons que rompían el diseño anterior.
  - Con texto normal dan exactamente lo mismo: la propiedad (1), igual bit a bit.
  - Los casos de N-02 quedan cerrados, y «ड़ड़ड़ड़ड़» sigue rechazada.
  - Sin criptografía nueva: solo `java.text.Normalizer` y `Character.getType`.
  - Las copias en `String` de la maestra que hace `strengthForm` son el mismo riesgo aceptado que el campo de texto y que `masterCore`.

### Revisión Ingeniero Móvil Senior (ronda 2)

- **F1.** `refuseIfDue` es una función local, sin estado nuevo. El orden no cambia nada de BOV-3 (`ATOMIC`, el mutex y el borrado).
- **F3.** `estimateBits` hace ahora una normalización NFC y dos pasadas lineales por contraseña.
  - En «Salud» es una por entrada y en el diálogo una por pulsación, sobre unas decenas de caracteres: despreciable.
  - No toca el hilo principal más que antes.
  - jvm-check compila `health/` y `vault/` sin Android.

### Textos para el Abogado Digital (ronda 2)

- **Sin textos nuevos.**
- `MASTER_TOO_SHORT`, «Mínimo 10 caracteres» (`vault/MasterPolicy.kt:24`), pasa a ser verdad también con emojis, letras en NFD y caracteres invisibles: cuenta los caracteres que se ven, en NFC y sin Cf, además de las unidades UTF-16. Así cierra la nota de la auditoría («Cuenta unidades UTF-16: 5 emojis cumplen»).
- `MASTER_TOO_WEAK` sale ahora también para «😀»×10 o diez letras en NFD.
- `FINGERPRINT_STALE` vuelve a salir, con una v2 o una v3, solo en el primer desbloqueo con huella después de escribir la maestra. Con la maestra pendiente sale el texto de los 7 días o el del reloj, como en la 1.3.0.
