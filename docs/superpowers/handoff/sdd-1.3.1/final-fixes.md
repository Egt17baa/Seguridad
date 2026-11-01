# Ronda de arreglos de la revisión final de la 1.3.1 (código)

Fuentes, que hay que leer en el punto que se cita:
- `final-review.md` (revisión de la rama entera): I1, M1-M7 y la tabla de los aplazados.
- `final-audit.md` (auditoría del cambio): N-01 a N-08 y A-1 a A-3.
- La revisión de código automática (9 puntos), copiada en `progress.md`, línea «Final review: code-review (high)».

Rama `ccr-a1a6eabb-765f9g`, base de esta ronda `e6796db` (más los commits de registro que haya encima). Los documentos (README, `CLAUDE.md`, la plantilla del PR, el informe de auditoría de la 1.3.1, el diseño y el plan) **no** son de esta ronda: los hace después otro paso. Esta ronda solo toca código, tests y KDoc.

Cada punto lleva la decisión del controlador. Si al implementarlo un punto resulta falso, más arriesgado de lo que parece o en conflicto con una regla del proyecto, **para y avisa**: no lo resuelvas por tu cuenta.

## F1. La huella solo se desactiva cuando su clave no autentica la bóveda

Fuentes: N-01; code-review 1 y 2; revisión final M3 y aplazado 6.

**Qué pasa hoy:**
- `launchUnlockWithKey` (`vault/VaultStore.kt:~105`) trata toda `CryptoFailure` como «otra clave»: desactiva la huella y enseña `FINGERPRINT_STALE`.
- Pero `VaultCrypto.read` (`vault/Vault.kt:~281-290`) convierte en `CryptoFailure()` una `SerializationException` al decodificar las entradas con la clave **buena**.
- Además, `unlockWithKeyLocked` (`VaultStore.kt:~447-458`) llama a `resolve`, que usa la clave con un archivo sin `version`, antes de mirar `supported`/`biometricRefusal`.

**Decisión:**
1. En `unlockWithKeyLocked`, primero: si el archivo no se abre (`!supported`), lanzar `MasterRequired` sin usar la clave (ver F2 para el motivo) y borrar la clave.
2. Después, comprobar si la clave **autentica** el bloque de datos con AEAD, sin decodificar el JSON. Una clave que no autentica lanza una excepción propia que no es `CryptoFailure` (por ejemplo `StaleBiometricKey`). Solo esa excepción llama a `onWrongKey` y enseña `FINGERPRINT_STALE`.
3. Una `CryptoFailure` posterior (decodificación, datos dañados) se enseña con el error genérico de siempre y **conserva la huella**.
4. Usa el esquema de N-01 en `final-audit.md` como guía. Si `resolve` ya solo hace AEAD, puedes apoyarte en él en vez de duplicar el descifrado; elige lo más simple y dilo en el informe.

**Se mantiene:**
- El orden y la semántica actuales para `VERSION_UNKNOWN`: la clave de otra bóveda da `FINGERPRINT_STALE`, y la buena con la maestra pendiente da `MasterRequired`.
- `MasterRequired` (7 días, reloj) nunca desactiva la huella.
- La clave se borra en toda salida.

**Tests:**
- una DEK de otra bóveda sigue dando `FINGERPRINT_STALE`;
- un bloque que autentica pero con JSON inválido da el error genérico y la huella sigue;
- `format = "otra-cosa"` sin `version` da `MasterRequired` sin llamar a la clave (comprobable con una clave espía o con el estado);
- adaptar los tests existentes de `VaultStoreTest` y de `VaultV3Test` que fijen el orden anterior, diciendo cuáles cambian y por qué.

## F2. Una bóveda que esta versión no abre no parece una contraseña olvidada

Fuentes: N-06; code-review 4; revisión final M1 y aplazado 7.

**Decisiones:**
- **Motivo nuevo de la huella:** `BiometricRefusal.UNSUPPORTED` para `!supported(file)`. La pantalla no enseña ningún aviso con él: ni «cada 7 días…» ni otro texto. El botón de la huella no se ofrece, como hoy. No hay texto nuevo.
- **Error de la maestra:** en `UnlockVault` (`ui/screens/VaultScreen.kt:~337-338`), una `CryptoFailure` cuyo mensaje es `NOT_A_VAULT` enseña ese texto («No es una bóveda compatible», que ya existe en `Vault.kt:222`; hazlo `internal` o público según convenga). Las demás siguen con «Contraseña incorrecta».
- **Comentarios:** que el KDoc de `biometricRefusal`/`biometricAllowed` (`Vault.kt:~388-401`) y el de `VaultV3Test.kt:~679-681` digan la verdad.

**Tests:**
- con un archivo de versión 1 o 4 o con otro formato, `biometricRefusal` da `UNSUPPORTED`;
- la versión 0, la 2 y la 3 no cambian;
- `MASTER_DUE` sigue con su texto;
- la pantalla enseña `NOT_A_VAULT` para esa `CryptoFailure`. Basta un test de código fuente, si es el patrón del proyecto para las pantallas.

## F3. La estimación de fuerza mide caracteres, no unidades UTF-16

Fuente: N-02.

**Qué pasa hoy:** «😀😀😀😀😀», «ąąąąą» en NFD y «12345» con U+200B entre las cifras pasan como maestra nueva.

**Decisión:**
- **`estimateBits`** (`health/Health.kt:~85-126`) pasa a medir sobre `Normalizer.normalize(p, NFC)` sin los caracteres de formato (`Character.FORMAT`, Cf), y por puntos de código: el grupo de símbolos, las rachas, las secuencias y la longitud efectiva. Esquema en N-02 de `final-audit.md`.
- **Lista de comunes:** la comprobación de `COMMON` se hace como hoy, y además sobre la forma normalizada.
- **`newMasterProblem`:** cuenta `MIN_MASTER` en puntos de código sobre esa misma forma normalizada. «Mínimo 10 caracteres» pasa así a ser verdad para los emojis.
- **Sin cambios:**
  - el diálogo de importar (`a.length >= MIN_MASTER`);
  - desbloquear;
  - «Salud» fuera de estos casos raros.

**Tiene que quedar probado** (es la condición para aceptar el cambio):
1. **Igual para lo normal:** para todo texto que ya está en NFC, en el plano básico y sin Cf, `estimateBits` da **exactamente** lo mismo que antes. Test de propiedad con miles de textos al azar (ASCII, letras españolas en NFC, símbolos), con semilla fija, comparando con una copia de la función anterior en el test.
2. **Nunca más fuerte:** para cualquier texto, el resultado nuevo es menor o igual que el anterior. Mismo tipo de test, ahora con emojis, NFD y Cf.
3. **Lo que ya se probaba sigue igual:**
   - las tres candidatas de N-02 dan `MASTER_TOO_WEAK`;
   - todo lo que hoy se acepta o se rechaza en `MasterPolicyTest` sigue igual, incluidas las 200 frases y las 200 contraseñas del Generador;
   - los tests de «Salud» siguen igual.

Si (1) o (2) no se cumplen, **para y avisa**.

## F4. TOTP no admitido: la lista y el inspector de QR

Fuentes: N-07; code-review 5 y 7; revisión final M4.

**Decisiones:**
- **Inspector de QR** (`tools/qr/QrView.kt:~187-193`): un enlace `otpauth` que el inspector marca como no admitido no ofrece «Añadir a la bóveda». La acción queda en `NONE` o `null` según el tipo, y la nota `TOTP_UNSUPPORTED_LINK` se mantiene.
- **Lista de la bóveda** (`ui/screens/VaultScreen.kt:~455-462`): una entrada con TOTP que no da código enseña, en lugar del código, una constante nueva y corta, `TOTP_UNSUPPORTED_ROW = "2FA no admitido"`, en `crypto/Totp.kt` junto a las otras. Va con el mismo estilo pequeño que «${left}s» y no cambia la altura de la fila. Solo si `!isSupported()`, no por otros motivos de `null`.
- **Un solo análisis del enlace:** el mismo `otpauth` se analiza una vez. Expón el resultado sellado de `readOtpAuth` (`Usable`, `UnsupportedSettings` y `NotUsable`) y úsalo en `VaultEntryScreen.kt:~260` y en `QrView.kt:~187-192`. Quita las funciones públicas que solo envolvían ese resultado, si ya no las usa nadie. `SourceTextsTest`, `AppNameTest` y los tests de TOTP siguen en verde.

## F5. Copias de la clave TOTP

Fuentes: A-1 y aplazado 4.

- **`base32Decode`** (`crypto/Totp.kt:~27-38`): decodifica en un `ByteArray` de tamaño exacto, sin `ByteArrayOutputStream`.
- **`totpKey`:** pone a cero el array corto que descarta.
- **`VaultEntryScreen.kt:~494`:** usa `isUsableTotpKey` (que borra) en lugar de `totpKey(...) == null`.
- **Sin tocar:** `vault/TotpImport.kt:7`, porque la clave ya es un `String`.
- **Tests:** los vectores de RFC 6238 y la ida y vuelta de Base32 siguen pasando.

## F6. Listas de palabras: una vez por proceso y fuera del hilo principal

Fuentes: code-review 9; revisión final M5 y aplazado 1; A-2.

- **Listas de la maestra:** `rememberMasterWords` (`ui/screens/MasterWords.kt`) no vuelve a leer ni a normalizar las listas en cada apertura ni en cada giro. Se cargan una vez por proceso (por ejemplo un `lazy`, o un `Deferred` en `AppContainer`, construido fuera del hilo principal) y la pantalla espera a que estén, como hoy.
  - Si la lectura falla, se queda en `emptySet()` como hoy. Que pueda reintentarse en la siguiente apertura, en vez de quedar vacío para siempre, si es sencillo.
  - Las palabras no son secretas.
- **Lista EFF del Generador** (`ui/screens/SecurityToolsScreen.kt:~936-940`): se lee en `Dispatchers.IO`, con `use` para cerrar el flujo, y «Frase» solo se activa cuando la lista está cargada. Si puede compartir la carga con la de la maestra sin enredar el código, mejor.
- **Sin texto nuevo.**
- `AppContainer` se compila en la JVM con jvm-check: nada de Android en su firma.

## F7. `checkAssets` comprueba las listas de palabras

Fuente: revisión final M2.

- **`checkAssets`** (`app/build.gradle.kts:~188-209`) falla si en el APK falta cualquiera de las tres listas que usa la regla de la maestra: EFF, BIP-39 en inglés y BIP-39 en español. Sigue el patrón de las demás comprobaciones de esa tarea.
- **También lo vigila un test** que falle si `loadMasterWords` da un conjunto vacío con los assets del repositorio, si no existe ya.

## F8. `Dialect` con un solo valor

Fuente: code-review 8.

- **`crypto/Crypto.kt:31-38`:** quitar `fileExtension` si ya no lo usa nadie, y que haya una sola fuente del prefijo `SG1:`. Por ejemplo, `TEXT_PREFIX` sale de `Dialect.SEGURIDAD.textPrefix` o al revés, y `encryptText`, `inspectText` y `decryptText` usan la misma.
- **No cambian:**
  - las firmas de `aead`, `cascadeKey` y `rawKeyHkdf`;
  - los bytes;
  - las etiquetas HKDF.
- **Tests:** los vectores de `CryptoTest.Kat` y de `DialectTest` siguen pasando.

## F9. KDoc y comentarios

- **`CryptoTest.kt:~44-49`, el KDoc de `Kat`** (A-3 contra el aplazado 3): la revisión final dice que ya es exacto y la auditoría que la frase de Twofish no lo es. Léelo:
  - si dice que Twofish no tiene vector publicado del motor, o que solo es independiente la composición GCM, corrígelo (el motor Twofish sí tiene vector publicado);
  - si ya es exacto, dilo en el informe.
- **`android/TrustedChain.kt:~29`** (N-04, revisión final M6, code-review 6), una frase en el KDoc: la segunda validación no recibe el OCSP grapado ni las SCT de la extensión TLS, así que, si Android llegara a exigir Certificate Transparency a la app (Network Security Config, o un `targetSdk` futuro), puede rechazar de más; falla cerrada. Hay que comprobarlo en cada subida de `targetSdk`. Sin cambio de código.
- **`launchUnlockWithKey`:** su KDoc dice exactamente qué desactiva la huella después de F1.
- **Longitud de línea:** las líneas de más de 120 caracteres **no** se tocan, porque el proyecto no tiene esa regla. Las líneas nuevas que escribas, que no pasen de 120.

## No es de esta ronda (decisión del controlador, apuntada en el registro)

- **`BiometricVault.disable` en el hilo principal** (aplazado 2, N-05): queda para la 1.4.0, con un dispatcher de un solo hilo para todo `BiometricVault`. Moverlo ahora sin poner las llamadas en fila puede borrar una clave que `enable` acaba de crear o saltarse el `disable` al cancelar.
- **Documentos:** I1, N-03, N-08 y M7 los hace el paso siguiente.

## Verificación y commits

- **Durante el trabajo:** los tests de lo que toques.
- **Antes de cada commit**, una vez, las dos verificaciones globales (con `export ANDROID_HOME=/opt/android-sdk`), con 0 fallos y el número de tests de cada una:
  - `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`;
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`.
- **Commits:** puedes hacer uno por grupo (F1+F2, F3, F4+F5, F6+F7, F8+F9) o uno solo. Cada uno:
  - con autor `Claude <noreply@anthropic.com>` (`git -c user.name=Claude -c user.email=noreply@anthropic.com commit`);
  - con un mensaje que acabe solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, sin `Claude-Session` ni claude.ai (`git log -1 --format=%B | grep -cE "Claude-Session|claude\.ai"` da 0);
  - con push a `ccr-a1a6eabb-765f9g`;
  - nunca `amend`, `rebase` ni `force`.
- **Informe:** en `final-fixes-report.md`, en esta misma carpeta, con:
  - por cada F, qué se cambió, con `archivo:línea`;
  - los tests que lo cubren, con la evidencia RED y GREEN;
  - los comandos y su salida;
  - las secciones «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior»;
  - «Textos para el Abogado Digital (revisión final)», con `TOTP_UNSUPPORTED_ROW` y cualquier otro texto nuevo.
