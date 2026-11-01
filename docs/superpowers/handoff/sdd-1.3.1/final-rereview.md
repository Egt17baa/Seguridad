# Repaso acotado de la ronda de arreglos F1-F9 y del paso de documentos (1.3.1)

Rango: `e6796db..adbba00`. No he tenido en cuenta los trozos del registro `docs/superpowers/handoff/sdd-1.3.1/progress.md` ni el commit `18c25ce`, que solo toca el registro. Las rutas de Kotlin son relativas a `app/src/main/java/app/seguridad/` y las de los tests a `app/src/test/java/app/seguridad/`. Las líneas son de `adbba00`, que tiene el mismo código que `95ddc44`.

Skills leídas: `experto-seguridad-cripto` (`SKILL.md`) y, de Ingeniero Móvil Senior, `seguridad-criptografia-movil`. Sin subagentes. No he tocado el checkout: el trabajo de prueba está en `/tmp/claude-0/-home-user-Seguridad/912d88ad-2ca8-526e-8826-8cf3516a3c57/scratchpad/rereview-final/`.

### Veredicto por hallazgo

**F1. La huella solo se desactiva cuando su clave no autentica la bóveda.** ADDRESSED. Sigue la segunda decisión del controlador.
- **El orden de `unlockWithKeyLocked`** (`vault/VaultStore.kt:469-496`):
  1. `!VaultCrypto.opens(v)` borra la clave y lanza `MasterRequired(UNSUPPORTED)` sin usarla (`:472-475`).
  2. Si el archivo dice su versión, `refuseIfDue(v)` decide antes de cualquier descifrado (`:476-482`, `:485-486`).
  3. Después, `VaultCrypto.authenticated`, que solo hace AEAD, borra lo que descifra y no decodifica (`vault/Vault.kt:313-321`). Si la clave no autentica: `StaleBiometricKey` (`VaultStore.kt:488-491`).
  4. Con `VERSION_UNKNOWN`, la clave va primero y la regla de acceso después (`:492`), como antes.
  5. `opened()` borra la clave si hay un fallo de decodificación (`:361-367`).
- **Riesgo 1, comprobado:**
  - Ningún camino descifra con la clave de la huella cuando la regla de los 7 días o la del reloj rechaza un archivo que dice su versión. La única entrada es `BiometricVault.unlock` → `launchUnlockWithKey` (`ui/screens/VaultScreen.kt:320-321`).
  - La clave se borra en toda salida: en cada rama y en el `catch (Throwable)` de `unlockWithKey` (`:459-467`).
  - Solo `catch (e: StaleBiometricKey)` llama a `onWrongKey` (`:110-117`).
  - `MasterRequired` y `CryptoFailure` van a `onError(biometricUnlockError(e))` y conservan la huella.
- **Tests:**
  - `VaultStoreTest.kt:1097`: la v3 con la maestra pendiente, restaurado;
  - `:1112`: el reloj atrás, nuevo;
  - `:1170`: la decodificación conserva la huella, en la v3 y en `VERSION_UNKNOWN`;
  - `:1185`: «otra-cosa» sin versión no prueba la clave;
  - `:1213`: `StaleBiometricKey` no es una `CryptoFailure`;
  - `VaultV3Test.kt:522,542`, que ahora esperan `failsWith<StaleBiometricKey>`.

**F2. Un archivo que esta versión no abre no parece una contraseña olvidada.** ADDRESSED.
- **Código:**
  - `!supported → UNSUPPORTED` (`vault/Vault.kt:423`) y el `enum` con el valor nuevo (`:434`);
  - `MasterRequired(UNSUPPORTED)` lleva `NOT_A_VAULT` (`VaultStore.kt:133-139`);
  - la pantalla: `error = if (e.message == NOT_A_VAULT) NOT_A_VAULT else "Contraseña incorrecta"` (`ui/screens/VaultScreen.kt:342`) y `BiometricRefusal.UNSUPPORTED, null -> Unit` (`:361`).
- `NOT_A_VAULT` solo se lanza para `!supported` (`Vault.kt:268`) y en `decodeFile` (`:406`), que no pasa por la pantalla de desbloqueo.
- **Tests:** `VaultV3Test.kt:670,685` (las versiones 1, 4 y −1 y «otra-cosa» dan `UNSUPPORTED`; la 0, la 2 y la 3 no cambian) y `VaultStoreTest.kt:1243` (código fuente de la pantalla).

**F3. La fuerza se mide también por puntos de código, en NFC y sin Cf (opción A).** ADDRESSED.
- **Código:**
  - `estimateBits = minOf(bits(UTF-16), bits(strengthForm, puntos de código))` (`health/Health.kt:93-99`);
  - `strengthForm` (`:105-110`);
  - un solo núcleo `bits(text, IntArray)` (`:112-122`), sobre `step(Int, Int)` y `effectiveLength(IntArray)`;
  - `MIN_MASTER` se exige en las dos longitudes (`vault/MasterPolicy.kt:150-152`).
- **Riesgo 2, comprobado:**
  - **La copia de la función anterior es la de `e6796db`.** Comparé de forma mecánica `estimateBits`, `step` y `effectiveLength` de `git show e6796db:…/health/Health.kt` con `oldEstimateBits`, `oldStep` y `oldEffectiveLength` de `HealthTest.kt`, tras cambiar los nombres (`COMMON`→`oldCommon`, `KEYBOARD_ROWS`→`oldRows`): son **idénticas**. `oldRows` es literalmente `KEYBOARD_ROWS`, y `oldCommon` lo fija un test contra `commonPasswords()`.
  - **Semillas fijas:** `20261101L` (`:220`) y `20261102L` (`:238`).
  - **El generador de (2)** incluye:
    - letras en NFD;
    - caracteres Cf, también los astrales U+E0001 y U+E0041;
    - emojis;
    - exclusiones de composición (U+0344, U+095C, U+FB2F, U+1D15E…);
    - singletons (U+212A, U+037E, U+2126, U+2F800…);
    - marcas combinantes y sustitutos sueltos;
    - cualquier punto de código asignado hasta U+2FFFF.
  - Comprueba también que la regla nueva nunca acepta lo que la anterior rechazaba (`:259`).
  - **Por construcción, el camino UTF-16 da lo mismo que antes** para todo texto:
    - `Character.toLowerCase(int)` y `Character.isLetterOrDigit(int)` dan lo mismo que sus versiones de `Char` para cualquier unidad UTF-16;
    - `indexIn` da −1 fuera del ASCII, como `indexOf` sobre filas ASCII.

    Así, «nunca más fuerte» vale para cualquier texto, no solo para el muestreo.
  - **Lo que no cambia:** el diálogo de importar sigue con `a.length >= MIN_MASTER` (`ui/screens/VaultBackup.kt:235`, fuera del diff), y desbloquear no llama a `estimateBits`. Solo lo llaman `vaultHealth` (`Health.kt:46`) y `newMasterProblem` (`MasterPolicy.kt:153`).

**F4. TOTP no admitido en la lista y en el Inspector de QR.** ADDRESSED.
- **Código:**
  - `OtpAuthRead` público, con `names`, y `readOtpAuth` público (`crypto/Totp.kt:159-190`).
  - Inspector: `action = if (unsupported) QrAction.NONE else QrAction.ADD_TOTP`, y la nota se mantiene (`tools/qr/QrView.kt:188-197`). `QrAction.NONE -> Unit` no pinta ningún botón (`ui/screens/QrInspectScreen.kt:367-368`).
  - Lista: el texto sale solo dentro de `if (!t.isSupported())` (`VaultScreen.kt:460-477`), y `totpCode` da `null` en ese caso, así que nunca salen los dos.
  - Editor: un solo `when (val read = readOtpAuth(text))` (`ui/screens/VaultEntryScreen.kt:257-266`).
- **Riesgo 3, comprobado:**
  - `git grep` de `parseOtpAuth`, `otpAuthUnsupported` y `otpAuthNames` en `app/`, `jvm-check/` y los documentos solo da las ayudas privadas de los tests (`OtpMigrationTest.kt:50`, `VaultTotpTest.kt:39-41`) y comentarios.
  - En `app/src/main` no queda ninguna llamada.

**F5. Copias de la clave TOTP.** ADDRESSED.
- **Código:**
  - `base32Decode` escribe en un `ByteArray(clean.length * 5 / 8)`, de tamaño exacto (`crypto/Totp.kt:28-44`);
  - `longEnoughOrWiped` borra la clave corta (`:71-78`);
  - el editor usa `!isUsableTotpKey(totp.secret)` (`VaultEntryScreen.kt:496`);
  - `TotpImport.kt` no se toca.
- **Tests:** `VaultTotpTest.kt:141,153`, y los vectores de RFC 6238 siguen en verde.

**F6. Listas de palabras una vez por proceso, fuera del hilo principal.** ADDRESSED.
- **Riesgo 4, comprobado:**
  - `WordLists` (`vault/MasterPolicy.kt:90-113`) lee con `withContext(io)` y bajo un `Mutex`.
  - Solo guarda un resultado bueno: un `master()` no vacío (`:96-98`) y un `eff()` que no es `null` (`:101-103`). Si la lectura falla, la siguiente pantalla la vuelve a intentar (`MasterPolicyTest.kt:309`).
  - `AppContainer` solo recibe `openAsset: (String) -> InputStream` (`AppContainer.kt:92`, `:117`): nada de Android, y `AppContainerTest` corre en `jvm-check` (`:52`).
  - `SeguridadApp.kt:32` le pasa `assets.open`.
  - En las pantallas, `rememberMasterWords` usa `remember` y nunca `rememberSaveable` (`ui/screens/MasterWords.kt:24-33`). El Generador usa `produceState` y `enabled = words != null` (`ui/screens/SecurityToolsScreen.kt:937-960`).
  - No se guarda nada secreto: solo las listas públicas.

**F7. `checkAssets` comprueba las listas.** ADDRESSED.
- **Código:** `app/build.gradle.kts:196,204-212`.
- **Riesgo 5, comprobado:** el informe copia la ejecución de la tarea de Gradle de verdad sin `bip39_spanish.txt` (`final-fixes-report.md:266-281`):
  - antes del cambio, `exit 0`;
  - después, `exit 1`, con el mensaje;
  - con la lista devuelta, `exit 0`.
- **Tests:** `MasterPolicyTest.kt:184`, y `:260` (`loadMasterWords` da más de 9000 palabras).

**F8. `Dialect` con un solo valor.** ADDRESSED.
- **Código:** `enum class Dialect(val magic, val labelPrefix)` (`crypto/Crypto.kt:26-34`), `TEXT_PREFIX` como única definición (`:509`), y `encryptText` lo usa (`:572`).
- **Riesgo 6, comprobado:**
  - Antes también salía siempre «SG1:», porque `header.dialect` solo puede ser `SEGURIDAD`. `labelPrefix` y las firmas no cambian.
  - Ni `CryptoTest.kt`, ni `DialectTest.kt`, ni `SeedCryptoTest.kt`, ni `app/src/test/resources/` están en el diff.

**F9. KDoc y comentarios.** ADDRESSED.
- **El KDoc de `Kat` ya era exacto y no cambia** (`crypto/CryptoTest.kt:41-53`). El vector de Twofish de `SeedCryptoTest.kt:40` es el de `ecb_tbl` para una clave de 256 bits con I=1: `57ff739d…8216f`.
- **N-04** está en `android/TrustedChain.kt:21-24`.
- **El KDoc de `launchUnlockWithKey`** (`vault/VaultStore.kt:79-97`) dice exactamente qué desactiva la huella y en qué orden.
- La frase nueva de `TrustedChain.kt:24` nombra «Comprobar contraseña»: ver la sección «Lo nuevo que se rompe en el diff», punto 2.

**I1. Documentos que aún decían que Clavero lee CriptoPanel, y documentos que faltaban.** ADDRESSED.
- **Corregidos:**
  - `CLAUDE.md:112` (la regla de los formatos);
  - `.github/pull_request_template.md:9`;
  - el traspaso de la 1.3.0, `:83`, con una nota.
- **Nuevos:**
  - la auditoría (`docs/auditorias/2026-11-01-auditoria-1.3.1.md`);
  - las pruebas en el móvil (`README.md:308-312`);
  - «Estado actual» (`CLAUDE.md:29-108`);
  - el traspaso nuevo.
- La cabecera del registro (`progress.md:3`) la puso al día el controlador en `18c25ce`.
- Lo que queda en `docs/superpowers/plans/2026-10-*`, en las especificaciones de la 1.0 y la 1.2, y en la auditoría de la 1.3.0 es histórico (ver «Fuera del alcance»).

**M7. Correcciones del diseño y del plan.** ADDRESSED.
- **Diseño:** `docs/superpowers/specs/2026-11-01-clavero-1.3.1-design.md:20-21` y `:73-74`.
- **Plan:** `docs/superpowers/plans/2026-11-01-clavero-1.3.1.md:19-20`.
- **Comprobado:** la afirmación «una `seguridad-vault` v1 no existió nunca» es cierta. En `07f220e`, que crea `Vault.kt`, `supported()` solo acepta `FORMAT` con `VERSION_UNKNOWN` o la 2, y la v1 era de `LEGACY_FORMAT`.

**N-03 y el riesgo residual de CRY-A03.** ADDRESSED. El párrafo `docs/auditorias/2026-11-01-auditoria-1.3.1.md:175-197` es idéntico a `final-audit.md:499-521` (comprobado con `diff`). Debajo hay una línea aparte, `:199`, sobre F5.

**N-08. La plantilla del PR y `CLAUDE.md`.** ADDRESSED: `.github/pull_request_template.md:9` y `CLAUDE.md:112`.

**El informe de la auditoría de la 1.3.1.** ADDRESSED. He comprobado contra `adbba00` más de veinte afirmaciones concretas, todas correctas:
- **Tamaño del cambio:**
  - 56 archivos, 49 de ellos de `app/` y `jvm-check/`;
  - 42 commits, todos de `Claude <noreply@anthropic.com>`, con 0 enlaces de sesión;
  - 1 356 líneas de más de 120 caracteres.
- **Tests:** 1293 y 1288. Lo comprueban los informes (`final-fixes-report.md:434` y `docs-report.md:140-141`, con los comandos y `cleanTest`) y los registros de `docs-step` (`BUILD SUCCESSFUL`, `2 executed`).
- **La maestra nueva:** `MasterPolicy.kt:150-156`, `Health.kt:93-99`, `:105-110`.
- **El orden de la huella:** `VaultStore.kt:469-496`, `:472-475`, `:485-486`, `:488`, `:492`; `StaleBiometricKey` en `:126` y el `NOT_A_VAULT` de `MasterRequired` en `:137`.
- **TOTP:**
  - los rangos de 6 a 8 dígitos y de 10 a 120 s (`Totp.kt:87-91`);
  - `isSupported` (`:105`) y los textos (`:108`, `:111`, `:114`);
  - `readOtpAuth` (`:190-222`).
- **El error genérico de CriptoPanel:**
  - `Crypto.kt:489-491`;
  - `DialectTest.kt:150`, con «El texto cifrado debe empezar por SG1:» y «No es un dato cifrado compatible»;
  - `DialectTest.kt:189`;
  - `LegacyVaultTest.kt:46`.
- **Lo aplazado:**
  - las seis llamadas a `BiometricVault.disable`: `VaultBackup.kt:131,175` y `VaultScreen.kt:170,281,323,378`;
  - los 53 avisos de R8 (`r8-antes.txt`).
- **La compilación y el APK:**
  - `checkAssets` (`build.gradle.kts:196,204-212`);
  - el APK de publicación: «Clavero», `versionName` 1.3.1 y los mismos permisos (`docs-step/badging.txt`, `permissions.txt`, `check-manifest: OK`).
- **Las líneas de los tests citados:** `MasterPolicyTest.kt:58,184,291,309`, `HealthTest.kt:219,237`, `VaultStoreTest.kt:975-1254,1097,1112,1170,1185,1231,1243`, `VaultV3Test.kt:522,542,670,685`, `VaultTotpTest.kt:345,357`, `QrViewTest.kt:244` y `AppContainerTest.kt:52`.
- **Las líneas de los textos de §11:** `VaultScreen.kt:296`, `SettingsScreen.kt:475`, `SecurityToolsScreen.kt:354,409,867` y `LicensesScreen.kt:36-37`.

**README: las pruebas de la 1.3.1 y la errata.** ADDRESSED.
- La errata «Un JSON» está en `README.md:53` y las pruebas en `:308-312`.
- **Cada texto y cada ruta de las pruebas existe en la interfaz:**
  - «¿Filtrada?» (`ToolsScreen.kt:43`) y «Comprobar» (`SecurityToolsScreen.kt:1069`);
  - «⚠ Aparece…», «✓ No aparece…» y «No se pudo comprobar. Revisa la conexión.» (`SecurityToolsScreen.kt:1031-1038`);
  - «🩺 Salud de la bóveda» (`VaultScreen.kt:179`) y «Comprobar filtraciones» (`HealthScreen.kt:180`);
  - «Comprobación terminada…» y «No se pudo completar la comprobación…» (`HealthScreen.kt:162,171`);
  - «Cambiar contraseña maestra» (`SettingsScreen.kt:466`, `VaultScreen.kt:191`) y el botón «Cambiar» (`VaultBackup.kt:115`);
  - «Palabras: 5» (`SecurityToolsScreen.kt:980`);
  - «Pegar enlace» (`VaultEntryScreen.kt:450`);
  - «Inspector de QR» (`ToolTexts.kt:336`), «Código 2FA» (`:387`) y «Añadir a la bóveda» (`:361`).
- **El comportamiento de «contraseña1» y «contraseña123» es correcto:** la estimación da unos 78 bits y pasa, pero el núcleo «contrasena» está en las listas, así que sale `MASTER_TOO_WEAK`.

**`CLAUDE.md`.** ADDRESSED.
- «Skills: siempre juntas» y «Compilar y probar» son idénticas byte a byte a las de `e6796db` (comprobado con `diff` de las dos secciones).
- Siguen todas las decisiones de la 1.3.0. Solo se generalizan «la etiqueta de la versión» (`:76`) y los avisos de R8, que pasan a la 1.4.0 (`:84`).
- **Las decisiones nuevas (`:86-98`) son verdad en el código:**
  - la maestra nueva, con las dos medidas y la menor;
  - la huella, con `StaleBiometricKey` y la regla de acceso primero si el archivo dice su versión;
  - `UNSUPPORTED`;
  - TOTP: 6 a 8 dígitos, 10 a 120 s, y SHA1, SHA256 o SHA512 (`Totp.kt:97-102`);
  - las seis llamadas a `disable`;
  - repetir «¿Filtrada?» en cada subida de `targetSdk`.
- Los pendientes del dueño están renumerados, sin perder ninguno (`:100-108`).

**La plantilla del PR, `:9`.** ADDRESSED.

**El traspaso nuevo y la nota en el de la 1.3.0.** ADDRESSED.
- Son `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md:1-59` y `2026-10-28-seguridad-1.3.0-traspaso.md:83`.
- Lo que dicen coincide con el código y con `CLAUDE.md`.

**Las dos pruebas que el paso de documentos no puso en el móvil.** ADDRESSED: su razonamiento es correcto.
- **`FINGERPRINT_STALE`:** los cuatro caminos que cambian la clave de datos llaman a `BiometricVault.disable`:
  - crear la bóveda (`VaultScreen.kt:281`, antes de `store.create`, `:282`);
  - borrarla (`:378-379`);
  - cambiar la maestra (`VaultBackup.kt:128-131`): `changeMaster` crea una DEK nueva, `VaultStore.kt:721-742`;
  - importar (`VaultBackup.kt:174-175`).

  No hay otro `create`, `importReplace`, `changeMaster` ni `destroy` en `app/src/main`. `disable` no lanza: `edit {}` y `runCatching` (`android/BiometricVault.kt:102-104,151-158`).
- **«2FA no admitido»:**
  - El editor solo ofrece 6 u 8 dígitos y SHA1, SHA256 o SHA512, sin periodo (`VaultEntryScreen.kt:456-457`).
  - Un enlace no admitido se rechaza (`:265`), y la migración de Google Authenticator solo da ajustes admitidos (`crypto/OtpMigration.kt:159`).
  - **Todas** las versiones anteriores de `parseOtpAuth` cambiaban los dígitos y el periodo fuera de rango y rechazaban un algoritmo desconocido: lo he comprobado en `9db34c9`, `1f01e95`, `c70f932` y `9504ce3`.

### Lo nuevo que se rompe en el diff

1. **Menor: comentarios de los tests que la F1 dejó desfasados.**
   - **Dónde:**
     - `VaultStoreTest.kt:1022` dice: «resolve() tries the key… it is resolve() that throws CryptoFailure for a key that opens nothing»;
     - `:1034` dice: «resolve() has to try the key…»;
     - `:1098-1100` es el comentario del test restaurado «igual que en `688b482`»: «resolve() returns the file without trying the key… before opened() tries the key».
   - **Qué pasa:** desde la F1 el desbloqueo con huella no llama a `resolve`. La clave se prueba con `authenticated` antes de `opened`, y lo que se lanza es `StaleBiometricKey`, no `CryptoFailure`. La ronda cambió una sola palabra en `:1035` y dejó el resto.
   - **Impacto:** el comportamiento que fijan los tests es correcto; los comentarios no.
   - Por la regla del proyecto, los detalles menores se arreglan todos.
2. **Menor: `android/TrustedChain.kt:24`, una línea nueva de la F9, nombra «Comprobar contraseña».**
   - **Qué pasa:** en la interfaz no hay nada con ese nombre. La herramienta es «¿Filtrada?» (`ToolsScreen.kt:43`), y lo de «Salud» es «Comprobar filtraciones». El README, `CLAUDE.md`, la auditoría y el traspaso ya dicen «¿Filtrada?» y «Comprobar filtraciones».
   - **Origen:** el encargo de la F9 traía ese nombre, y el informe de documentos ya lo había señalado (`docs-report.md:168`).
   - **Corrección:** `check «¿Filtrada?» and «Comprobar filtraciones» with network on a phone at every raise of targetSdk`.
3. **Nota, no es un fallo.** Si se arreglan los puntos 1 y 2, el código cambia después de `95ddc44`. Entonces hay que poner al día «El código se cerró en `95ddc44`», la línea del alcance y «Las líneas son de `95ddc44`»:
   - `CLAUDE.md:38-42`;
   - `docs/auditorias/2026-11-01-auditoria-1.3.1.md:5-12`;
   - el traspaso, `:9-10`.

No hay nada Crítico ni Importante. La ronda no cambia ningún byte de formato, ni el manifiesto, ni `res/`, ni las dependencias. Las líneas añadidas en `app/src/main` no tienen `Log.`, URL ni `rememberSaveable`, salvo un comentario.

### Fuera del alcance

- **El plan (`docs/superpowers/plans/2026-11-01-clavero-1.3.1.md:161`)** sigue diciendo que las listas se leen «una vez por pantalla abierta». Desde la F6 se leen una vez por proceso. La M7 no lo pedía y el informe de documentos lo apuntó (§5.6). Si se quiere, va otra nota «Corrección».
- **Documentos históricos con la frase de la v1 o de CriptoPanel:**
  - `docs/auditorias/2026-10-31-auditoria-1.3.0.md:636,645`;
  - `docs/superpowers/handoff/sdd-1.3.0/context.md:25`;
  - los planes y las especificaciones de la 1.0 a la 1.3.0;
  - `.superpowers/sdd/2026-11-01-clavero-1.3.1/context.md:7`.

  El diseño dice que los históricos no cambian.
- **`docs/privacidad.html:46`** pone como ejemplo de User-Agent «Clavero/1.3.0». Es para el Abogado Digital y ya está en el §11 de la auditoría.
- **`VaultCrypto.resolve`** solo lo usa ya `afterMasterUnlock` (`vault/Vault.kt:368`). Está bien así.

### Veredicto

**Ronda de arreglos:** las F1 a F9 y todos los puntos de documentos están resueltos, y no hay nada Crítico ni Importante nuevo. Quedan dos detalles menores nuevos: los comentarios de `VaultStoreTest.kt:1022`, `:1034` y `:1098-1100`, y la frase de `TrustedChain.kt:24`. Por la regla del proyecto, se arreglan antes del PR, con la nota 3 si eso mueve el commit del código.

### Revisión experto-seguridad-cripto

- **Huella (F1 y F2):**
  - **El control de acceso decide antes de usar la clave** cuando el archivo dice su versión (`vault/VaultStore.kt:485-486`). La clave de la huella no descifra nada si la regla de los 7 días o la del reloj dice que no.
  - **Solo una clave que no autentica desactiva la huella** (`:488-491`, `:110-117`). Distinguir «otra clave» de «bloque dañado» no es posible con AES-GCM, y el KDoc lo dice (`Vault.kt:308-311`).
  - **El borrado de la DEK** está en todas las ramas (`VaultStore.kt:459-496`, `:361-367`), y lo que descifra `authenticated` se borra (`Vault.kt:319`).
  - **Ningún archivo ajeno prueba la clave** (`:472-475`).
- **Criptografía:** no hay nada propio. `authenticated` es `openAny` sobre AES-256-GCM con la AAD de siempre (`Vault.kt:313-321`). `dataAad` pasa a `internal` solo para los tests (`:201`).
- **La regla de la maestra (F3) no es más débil que antes, para ningún texto**, por construcción: la menor de dos medidas, y la medida UTF-16 da lo mismo que antes (`health/Health.kt:93-99`). La copia de referencia del test es idéntica a `e6796db`, y las dos propiedades usan semilla fija y un generador con las clases difíciles (`HealthTest.kt:219,237`). Los casos de N-02 quedan cerrados, y «ड़ड़ड़ड़ड़» sigue rechazada (`MasterPolicyTest.kt:58`).
- **TOTP (F4 y F5):**
  - Las entradas no fiables se leen una sola vez (`crypto/Totp.kt:190`, `tools/qr/QrView.kt:188`), y `names` nunca lleva la clave (`Totp.kt:159-182`).
  - Hay menos copias de la clave: `base32Decode` escribe en un array de tamaño exacto, y la clave corta se borra (`Totp.kt:28-44,71-78`).
  - El `String` de `TotpSecret.secret` sigue siendo un riesgo aceptado (A-1).
- **Formatos:** no cambia ningún byte (`crypto/Crypto.kt:26-34,509,572`), y ningún vector ni dato de prueba aparece en el diff.
- **Documentos:**
  - El riesgo residual de CRY-A03 es literal (`docs/auditorias/2026-11-01-auditoria-1.3.1.md:175-197`), y no se rebaja ninguna severidad: CRY-A03 sigue Baja, 1.9 (`:96`).
  - Las afirmaciones de seguridad que he comprobado son ciertas.
  - Las palabras prohibidas, sobre las 577 líneas añadidas a los documentos: solo «no garantiza», que está permitido (auditoría `:311`).
  - Correos: solo `noreply@anthropic.com` (traspaso `:53`; `CLAUDE.md` no tiene ninguno).

### Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - `launchUnlockWithKey` sigue `ATOMIC`, y un `onWrongKey` que lanza no tumba el scope (`vault/VaultStore.kt:98-120`).
  - `WordLists` serializa con un `Mutex` y lee en `io` (`vault/MasterPolicy.kt:90-113`).
  - Si se cancela mientras carga, no guarda nada a medias, porque `.also` no corre tras la `CancellationException` de `withContext`.
- **Hilo principal y ciclo de vida:**
  - Ni las listas ni la EFF se leen ya en el hilo principal (`ui/screens/MasterWords.kt:24-33`, `ui/screens/SecurityToolsScreen.kt:937-960`).
  - «Frase» solo se activa con la lista, y `phrase` se queda en `remember`, así que un giro vuelve a «Contraseña» (`:957`).
  - `BiometricVault.disable` sigue en el hilo principal en seis sitios, aplazado a la 1.4.0 con su motivo (`CLAUDE.md:97`).
- **Arquitectura:**
  - `AppContainer` sigue sin tipos de Android: `openAsset` es una lambda (`AppContainer.kt:92,117`; `SeguridadApp.kt:32`), y se compila en `jvm-check`.
  - El cast de `wordLists()` a `SeguridadApp` (`MasterWords.kt:33`) sigue el patrón de `MainActivity`; no hay previews.
- **Compose:**
  - El `when` sobre `BiometricRefusal` es exhaustivo (`VaultScreen.kt:356-362`).
  - La fila de la lista solo añade una línea `labelSmall` cuando no hay código (`:460-477`).
  - El Inspector no pinta ningún botón con `NONE` (`QrInspectScreen.kt:367-368`).
- **Compilación:** `checkAssets` sigue siendo compatible con la caché de configuración: solo captura una `List<String>` y la `ListProperty` (`app/build.gradle.kts:196-212`). Lo comprueba la ejecución de verdad de Gradle que copia el informe.
- **APK:** `check-manifest: OK`, «Clavero», 1.3.1, `io.github.egt17baa.seguridad` y los mismos permisos (`docs-step/permissions.txt`). Sin componentes exportados ni dependencias nuevas.
- **Tests:** los informes copian los comandos y la salida, 1293 y 1288 con 0 fallos, y la segunda vez con `cleanTest` (`docs-report.md:140-146`). No los he vuelto a ejecutar, como pedía el encargo.

### Textos para el Abogado Digital (revisión final)

**Interfaz, nuevos:**
1. `TOTP_UNSUPPORTED_ROW` (`crypto/Totp.kt:114`): «2FA no admitido». Sale en la lista de la bóveda, en pequeño, en lugar del código (`ui/screens/VaultScreen.kt:463-470`).

**Interfaz, textos que ya existían y salen en sitios nuevos o dejan de salir:**

2. `NOT_A_VAULT` (`vault/Vault.kt:224`): «No es una bóveda compatible». Sale:
   - en la pantalla de desbloqueo, en lugar de «Contraseña incorrecta», con un archivo que esta versión no abre (`VaultScreen.kt:342`);
   - como mensaje de `MasterRequired(UNSUPPORTED)` (`vault/VaultStore.kt:137`), que no se espera ver.
3. «Por seguridad, cada 7 días hay que escribir la contraseña maestra.» (`VaultScreen.kt:359`) ya no sale con `UNSUPPORTED`.
4. `FINGERPRINT_STALE` (`VaultStore.kt:65-67`):
   - ya no sale para unas entradas que autentican y no se decodifican, que dan ahora «Contraseña incorrecta o datos dañados»;
   - con una v2 o una v3 y la maestra pendiente sale el texto de los 7 días o el del reloj, como en la 1.3.0;
   - sigue en pie la reserva ya apuntada: afirma las causas como un hecho.
5. `MASTER_TOO_SHORT` (`vault/MasterPolicy.kt:24`): «Mínimo 10 caracteres». Ahora cuenta también los puntos de código en NFC sin Cf, así que es verdad también con emojis.
6. `MASTER_TOO_WEAK` (`MasterPolicy.kt:27-28`): sale también con «😀» diez veces o con diez letras en NFD.

**Mensaje para desarrolladores:** el error de `checkAssets` (`app/build.gradle.kts:207-210`) y su descripción (`:192-193`).

**KDoc (no es de la interfaz):** `android/TrustedChain.kt:21-24`, con «Comprobar contraseña» (ver «Lo nuevo que se rompe en el diff», punto 2).

**Documentos nuevos o cambiados en este rango:**

7. `docs/auditorias/2026-11-01-auditoria-1.3.1.md:1-465`, en especial:
   - §3.3 (`:173-199`);
   - §6 (`:303-317`);
   - §10 (`:370-388`);
   - §11 (`:390-421`).
8. `README.md:53`, la errata, y `README.md:308-312`, las pruebas de la 1.3.1.
9. `CLAUDE.md`:
   - «Estado actual», «Fase» y la tabla (`:29-53`);
   - `:76` y `:84`;
   - «Decisiones de la 1.3.1» (`:86-98`);
   - «Pendientes, del dueño» (`:100-108`);
   - la regla de los formatos (`:112`).
10. `.github/pull_request_template.md:9`.
11. `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md:1-59`, que es nuevo, y `docs/superpowers/handoff/2026-10-28-seguridad-1.3.0-traspaso.md:83`.
12. Las notas «Corrección»:
    - del diseño (`docs/superpowers/specs/2026-11-01-clavero-1.3.1-design.md:20-21`, `:73-74`);
    - del plan (`docs/superpowers/plans/2026-11-01-clavero-1.3.1.md:19-20`).
13. Sin tocar, para su revisión: `docs/privacidad.html:46`, con «Clavero/1.3.0».
