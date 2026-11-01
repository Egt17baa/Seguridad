# Auditoría de seguridad del cambio: Clavero 1.3.1 (`9504ce3..e6796db`)

- **Fecha:** 1-11-2026.
- **Rama:** `ccr-a1a6eabb-765f9g`, de `9504ce3` (1.3.0 publicada más documentos) a `e6796db`.
- **Método:** auditoría obligatoria del cambio (CLAUDE.md, «Skills: siempre juntas», punto 6). Se combinaron:
  - `experto-seguridad-cripto`, con `references/auditoria.md` y `references/cripto-moderna.md`;
  - de Ingeniero Móvil Senior, `auditoria-seguridad-movil` (OWASP MASVS v2) y `seguridad-criptografia-movil`;
  - `anthropic-skills:auditoria-seguridad`.
- **Rutas:** las de Kotlin son relativas a `app/src/main/java/app/seguridad/`, salvo que digan otra cosa. Las de tests, a `app/src/test/java/app/seguridad/`.
- **Solo lectura:** no se ha tocado el árbol, el índice, `HEAD` ni las ramas. El trabajo auxiliar está en `/tmp/claude-0/-home-user-Seguridad/912d88ad-2ca8-526e-8826-8cf3516a3c57/scratchpad/final-audit/`.

## Alcance y modelo de amenaza

**Qué se ha revisado:**
- el diff entero, leyendo el código de alrededor donde un riesgo lo pedía;
- los cinco hallazgos de `auditoria/04-cripto.md`, con sus partes A y B;
- las tareas 6 (fuera CriptoPanel) y 7 (`versionName`);
- el registro (`progress.md`) y los informes de cada tarea.

Los trozos en Markdown del diff (`auditoria/`, `docs/superpowers/`) se leyeron solo por sus requisitos.

**Modelo de amenaza:** el del proyecto (`docs/auditorias/2026-10-31-auditoria-1.3.0.md` §1.2). No son de fiar:
- un intermediario en la red con una clave de eMudhra comprometida;
- quien tiene una copia `.sgbak`, que ataca fuera de línea;
- un QR o un enlace `otpauth://` manipulado;
- otra app del móvil;
- un archivo o un texto recibidos (`SG1:`, `.sgenc`, datos de CriptoPanel).

**Fuera del modelo:** root y el volcado de memoria son riesgo residual aceptado. Cuentan solo para medir CRY-A03.

**Qué se ejecutó:**
- `git diff`, `git show`, `git log`, `git grep` y lecturas del código;
- una copia en Python de `estimateBits` y, después, una sonda en Java sobre las clases ya compiladas de `jvm-check` (`MasterPolicyKt.newMasterProblem` con las tres listas reales de `assets/`);
- `javap -p -c` sobre `bcprov-jdk18on-1.86.jar` (`HMac`, `HKDFBytesGenerator`, `HKDFParameters`).

**Qué no se ejecutó:**
- la suite de tests: según el registro, app tiene 1274 en verde y jvm-check 1269;
- ninguna prueba en un móvil;
- ninguna prueba dinámica de TLS.

Lo que digo de Conscrypt, de `RootTrustManager` y de BoringSSL viene de leer su código. Lo marco así en cada caso.

## Resumen ejecutivo

**Riesgo global del cambio: bajo.**
- Los cinco hallazgos quedan resueltos como pedía cada corrección: cuatro **CERRADOS** y CRY-A03 **PARCIALMENTE CERRADO**. En CRY-A03 se cierra lo que la API pública permite y el resto queda como riesgo aceptado.
- El cambio no añade permisos, hosts, dependencias, componentes exportados ni logs.
- `FLAG_SECURE` sigue igual, y ningún secreto pasa a `rememberSaveable`.
- Los formatos de Clavero se siguen abriendo, y los de CriptoPanel solo reciben el error genérico.
- Ningún hallazgo nuevo llega a Media. Hay 8 informativos introducidos o destapados por el cambio, más 3 menores anteriores que la rama arrastra.

**Acciones prioritarias:**
1. **N-03:** rehacer el párrafo de riesgo residual de CRY-A03. El de `task-4-report.md:160` se deja fuera la PRK y el último bloque del generador HKDF, que con 32 bytes son la propia clave de cada capa de la cascada (comprobado con `javap`). El texto correcto está al final de este informe.
2. **N-01:** con la clave correcta, un JSON que no se decodifica también desactiva la huella, y se prueba la clave antes de mirar si la versión se abre. Hay que limitar `FINGERPRINT_STALE` al fallo de autenticación.
3. **N-08:** actualizar `.github/pull_request_template.md:9` y la regla de formatos de `CLAUDE.md` antes de fusionar. Hoy exigen leer `CP1:`, `.cpenc` y `criptopanel-vault`.
4. **Pruebas en el móvil del dueño, obligatorias:**
   - «Comprobar contraseña» y «Salud» con red (CRY-B01: ningún rechazo falso);
   - una huella antigua da `FINGERPRINT_STALE` y desaparece el botón (CRY-A02);
   - los diálogos de la maestra esperan a las listas, y TalkBack lee el aviso (CRY-A01).
5. **N-02:** que la estimación cuente puntos de código, en NFC y sin caracteres invisibles. Hoy acepta «😀😀😀😀😀» como maestra nueva, aunque su KDoc dice que rechaza una racha de caracteres iguales.

---

## 1. Los cinco hallazgos de `auditoria/04-cripto.md`

| ID | Severidad original | Veredicto | Prueba principal |
|---|---|---|---|
| CRY-B01 | Media, 4.2 | **CERRADO** (falta la prueba en el móvil) | `net/DistrustedCertificates.kt:108-127`, `android/TrustedChain.kt:21-31`, `SeguridadApp.kt:30`, `AppContainer.kt:84,106` |
| CRY-A01 | Media, 4.7 | **CERRADO**, con el límite que el diseño ya declara | `vault/MasterPolicy.kt:102-107`, `ui/screens/VaultScreen.kt:242,253,270`, `ui/screens/VaultBackup.kt:118,233-235` |
| CRY-A02 | Informativa | **CERRADO** (ver N-01) | `vault/VaultStore.kt:94-115`, `ui/screens/VaultScreen.kt:309-321` |
| CRY-A03 | Baja, 1.9 | **PARCIALMENTE CERRADO** (riesgo residual aceptado; ver N-03) | `crypto/Crypto.kt:263-279,288-305,369-389` |
| CRY-B02 | Informativa | **CERRADO** | `crypto/Totp.kt:85-131,184-189` |

### CRY-B01: la desconfianza de eMudhra y la caché de intermedios de Conscrypt

**Qué hace ahora:** `checked()` aplica tres pasos (`net/DistrustedCertificates.kt:109-113`):
1. `refuse` sobre la cadena que presenta el servidor, **antes** de que el sistema la vea (`:110`);
2. la comprobación del sistema (`:111`);
3. `refuse` sobre la cadena que el sistema validó (`:112`).

Los tres valen para las tres sobrecargas (`:115-127`). En Android, la cadena validada la da `X509TrustManagerExtensions` sobre el gestor de confianza **del sistema**, no sobre el envoltorio (`android/TrustedChain.kt:21-31`, `net/DistrustedCertificates.kt:145-151`). Se inyecta desde `SeguridadApp.kt:30` a través del constructor de `AppContainer` (`AppContainer.kt:84`).

**Ataques probados contra el arreglo:**

1. **Cadena completada desde la caché, con solo la hoja.** El sistema la completa con el intermedio comprometido. `refuse(trustedChain(...))` lo encuentra por su SPKI (`:70-71,112`). Test: `net/DistrustedCertificatesTest.kt:164`, en las tres sobrecargas.
2. **Envenenar la caché con la cadena entera.** Se rechaza en `:110` antes de `system()`, así que `TrustManagerImpl` no llega a validarla ni a indexar el intermedio: lo indexa después de validar. Test: `:172`, con un espía que nunca recibe la llamada, en las tres sobrecargas.
3. **El emisor de la hoja tiene firma cruzada, o hay varias rutas.** Toda ruta válida tiene que incluir el certificado con la clave pública que firmó la hoja, y esa SPKI está en la lista. El ancla de confianza también va en la lista que devuelve `TrustManagerImpl`, y las raíces emSign se miran además por nombre (`:57-64`). Por eso no importa qué ruta elige el sistema, ni que la caché cambie entre la primera y la segunda validación (TOCTOU). En el proceso no hay otro cliente TLS: ni WebView ni otra conexión.
4. **Host nulo o vacío.**
   - Nulo: se rechaza la conexión (`android/TrustedChain.kt:28`). Solo pasa si el socket no es un `SSLSocket` o si no hay sesión de handshake. En ese caso `RootTrustManager` ya rechaza con «Not in handshake», así que no añade rechazos reales.
   - Vacío (`""`): no es alcanzable, porque OkHttp pasa el host de la URL fija (`net/PwnedPasswords.kt:24`). Si llegara, elegiría la configuración por defecto, que es la única que hay porque el manifiesto no tiene `networkSecurityConfig`, y la cadena se validaría igual.
5. **Excepciones de `X509TrustManagerExtensions`.**
   - Si el constructor lanza `IllegalArgumentException`, todas las conexiones se rechazan y la app no se cierra (`android/TrustedChain.kt:22-26`). Lo fija `PlatformSourcesTest.kt:296`.
   - `checkServerTrusted` lanza `CertificateException` o `RuntimeException`. El `verifyCertificateChain` de Conscrypt convierte cualquier excepción en `CertificateException` y el handshake falla (código de Conscrypt).
   - Ninguna rama acepta por error. Falla cerrada.
6. **El singleton perezoso se construye antes de inyectar la función de Android.** No es posible:
   - `trustedChainOf` es un parámetro del constructor (`AppContainer.kt:84`);
   - la única construcción de `AppContainer` está en `SeguridadApp.kt:17-31` (`git grep "AppContainer("`);
   - `client` es `by lazy` sobre ese parámetro (`AppContainer.kt:106`);
   - `secureClient` y `distrustCompromisedCas` no tienen más sitios de llamada en `app/src/main`.
7. **La JVM de los tests frente a Android.** En la JVM, la cadena validada es por defecto la presentada (`:100`). Es correcto para SunJSSE, que no guarda intermedios (la descarga AIA está desactivada por defecto). Pero `androidTrustedChain` no se ejecuta en ningún test: con los stubs de `android.jar` solo se comprueba su texto fuente (`PlatformSourcesTest.kt:296`). Su comportamiento real depende de la prueba en el móvil.
8. **Reanudación de sesión.** En una reanudación no se llama a `checkServerTrusted`. Solo se guardan las sesiones que pasaron las dos comprobaciones, en el `SSLContext` propio del cliente (`:149`).

**Veredicto: CERRADO** por lectura del código. Queda pendiente la prueba del dueño con red. La prueba dinámica de laboratorio que describe la parte B (CA de laboratorio en `debug-overrides`) sigue sin hacerse; es opcional. Ver N-04, sobre lo que la segunda validación no recibe de la sesión.

### CRY-A01: fuerza de la maestra nueva

**Qué hace ahora:** `newMasterProblem` pide, en este orden (`vault/MasterPolicy.kt:102-107`):
1. al menos `MIN_MASTER` = 10 unidades UTF-16;
2. `estimateBits ≥ 50`;
3. que su núcleo no sea una sola palabra de la lista común de «Salud», de la EFF ni de BIP-39 en inglés o en español. El núcleo se calcula en minúsculas con `Locale.ROOT`, en NFD sin marcas y solo con letras (`:34-44`).

**Dónde se aplica:**
- en «Crea tu bóveda», con el botón esperando a las listas (`ui/screens/VaultScreen.kt:242,253,270`);
- en «Nueva contraseña maestra» (`ui/screens/VaultBackup.kt:118,233-235`).

**Dónde no se aplica:** al importar, porque `newPassword` es `false` por defecto (`VaultBackup.kt:223`, y el diálogo `"import"` en `:158-165` no lo pasa). Tampoco al desbloquear ni en `VaultStore`.

**Ataques probados.** Sonda sobre las clases compiladas de `jvm-check`, con las listas reales de `assets/`:

| Candidata | UTF-16 / puntos de código | Bits | Resultado |
|---|---|---|---|
| `contraseña` | 10 / 10 | 0,0 | Rechazada (débil) |
| `Contraseña123` | 13 / 13 | 80,7 | Rechazada (núcleo `contrasena`) |
| `1234567890` / `aaaaaaaaaa` | 10 / 10 | 3,3 / 4,7 | Rechazadas |
| `Pass word 1!` (separador) | 12 / 12 | 78,8 | Rechazada (núcleo `password`) |
| `ÁBACO-2024!` (mayúsculas y tilde) | 11 / 11 | 81,4 | Rechazada (núcleo `abaco`) |
| `ąąąąą` precompuesta (NFC) | 5 / 5 | 6,6 | Rechazada (corta) |
| `ąąąąą` en **NFD** | 10 / 10 | 69,8 | **Aceptada** (N-02) |
| `😀😀😀😀😀` | 10 / 5 | 66,4 | **Aceptada** (N-02) |
| `1​2​3​4​5​`, con U+200B entre las cifras | 10 / 10 | 67,8 | **Aceptada** (N-02) |
| `a1a1a1a1a1a` | 11 / 11 | 56,9 | Aceptada: límite del estimador, decisión del dueño |
| `passwordpassword` | 16 / 16 | 75,2 | Aceptada: dos palabras, límite declarado en `MasterPolicy.kt:91-93` |
| `Barcelona1`, `españolito` | 10 / 10 | 59,5 / 69,8 | Aceptadas: límite declarado (`:88-91`) |
| `абаndon2024!` (homoglifos cirílicos) | 12 / 12 | 88,8 | Aceptada. Solo la elegiría el propio usuario, así que no es un vector de ataque |

**Otros casos:**
- **Listas aún sin cargar:** `rememberMasterWords` da `null` y los dos botones esperan (`ui/screens/MasterWords.kt:23-29`, `VaultScreen.kt:270`, `VaultBackup.kt:235`).
- **Listas que no se pueden leer:** dan un conjunto vacío y la regla se queda en la longitud y la estimación (`MasterPolicy.kt:64-68`). Falla abierta sin avisar, pero solo con un APK roto, y está documentada.
- **Mayúsculas y minúsculas:** `Locale.ROOT` en el núcleo (`:35`) y en la lista común (`health/Health.kt:86`).

**Veredicto: CERRADO.** Se cierran los casos que citaba el hallazgo, «contraseña», «1234567890» y «aaaaaaaaaa» (`MasterPolicyTest.kt:62`), y la ampliación del dueño quita también «una palabra con adornos». N-02 recoge el fallo de implementación de la estimación con texto fuera del BMP, en NFD o con caracteres invisibles.

**Daño concreto de las decisiones del dueño (no es un defecto):**
- La importación no se endurece, así que una copia hecha con una maestra débil deja esa maestra como la de la bóveda. Todas las copias `.sgbak` posteriores salen envueltas con ella, sin aviso. Se podría avisar una vez tras importar o desbloquear, como proponía la parte A (§CRY-A01, «Migración»).
- Las repeticiones (`passwordpassword`, `a1a1a1a1a1a`) pasan. Si el dueño quiere, se puede añadir una comprobación de repetición, `Regex("^(.{1,8}?)\\1+$")` sobre el texto en minúsculas y sobre el núcleo.

### CRY-A02: huella con la clave de otra bóveda

**Qué hace ahora:** solo una `CryptoFailure` de `unlockWithKey` llama a `onWrongKey` y enseña `FINGERPRINT_STALE` (`vault/VaultStore.kt:105-111`). `MasterRequired` y cualquier otra excepción conservan la huella y el texto de antes (`:112-114`; tests `VaultStoreTest.kt:1065,1079,1109`). La pantalla esconde el botón aunque `disable` lance una excepción (`ui/screens/VaultScreen.kt:309-310,318-321`).

**Qué excepciones desactivan la huella:**
1. La clave no autentica el blob de datos, en `resolve` cuando el archivo no dice su versión (`vault/VaultStore.kt:449`, `vault/Vault.kt:303-308`) o en `read` (`Vault.kt:281-284`). Es el caso del hallazgo.
2. **La clave autentica, pero el JSON no se decodifica**: la `SerializationException` se convierte en `CryptoFailure` (`Vault.kt:285-286`). Ver N-01.
3. Un archivo con otro formato y sin versión, porque se llama a `resolve` antes de mirar si se abre (`VaultStore.kt:449` antes de `:454`). La interfaz no lo alcanza, porque `store.biometricAllowed` ya da `MASTER_DUE` (`VaultScreen.kt:310`, `Vault.kt:401`). Ver N-01.

**¿Se puede desactivar a distancia?** No. Hay que escribir en `files/vault/vault.bin`, que es privado de la app, y eso pide root, fuera del modelo. Una importación ya desactiva la huella por su cuenta (`VaultBackup.kt:175`) y además pide la maestra de la copia. La comprobación HIBP y los QR no tocan `vault.bin`.

**¿Lo desactiva algo benigno?** Sí, un `vault.bin` dañado (bits corrompidos en el blob de datos). En ese caso la maestra tampoco lo abre, así que no se pierde nada. Pero el texto afirma una causa que puede ser falsa («se cambió la contraseña maestra o se importó una copia»); ya está apuntado para el Abogado.

**Veredicto: CERRADO.** Los tests son `VaultStoreTest.kt:1000-1124`.

### CRY-A03: copias de las claves en los objetos del proveedor

**Qué hace ahora:**
- `blockGcm` borra `KeyParameter.key` en el `finally`: también es `GCMBlockCipher.lastKey`, fijado con reflexión en `CryptoTest.kt:491` (`crypto/Crypto.kt:288-305`).
- `chaChaPoly` borra su `KeyParameter` y sobrescribe la copia privada de la clave, las palabras de clave del motor y la clave de Poly1305 con un último `init` de clave cero (`:369-389`, `CryptoTest.kt:585`).
- `runAead` borra el búfer de salida en cualquier fallo (`:263-279`, `CryptoTest.kt:614`).

**Ataques probados:**
1. **Cada camino de error.**
   - La etiqueta no cuadra (`InvalidCipherTextException`): da `CryptoFailure`, y `out` y `kp` se borran (`:271-276,302-304,381-382`).
   - Cualquier otra excepción (`DataLengthException`, o `IllegalArgumentException` por el tamaño de la clave o del nonce en `init`): `done` sigue en `false`, así que `out` se borra, y `kp` se borra en el `finally`.
   - Se probó con un bit cambiado en el cuerpo, en la etiqueta, en la AAD y con otra clave (`CryptoTest.kt:538-583`).
2. **Texto en claro parcial tras un descifrado fallido.**
   - Bouncy Castle: se borra (`runAead`).
   - AES-GCM por Conscrypt: BoringSSL pone a cero la salida cuando `EVP_AEAD_CTX_open` falla (código de BoringSSL), y Conscrypt nunca la devuelve.
   - Cascada: las capas intermedias se borran (`:343-344`). Lo que queda tras un fallo de la última capa es texto cifrado de la capa anterior, ya borrado.
3. **¿Puede lanzar el `init` de clave cero?** Solo con un nonce de tamaño incorrecto, y se captura (`:385`). La clave mide siempre 32 bytes, el MAC 128 bits, y en modo descifrar no se comprueba la reutilización del nonce. Si el primer `init` falló por la clave, el segundo funciona; si falló por el nonce, el segundo también falla y no había nada que sobrescribir.
4. **La JVM frente a Android.** Es el mismo `bcprov` 1.86 empaquetado, y R8 no quita llamadas con efectos.

**Lo que no se cierra:**
- AES-GCM por Conscrypt: `SecretKeySpec` copia la clave, y Conscrypt guarda la copia que le da `getEncoded()`.
- `HKDFParameters`: copia la IKM.
- **Nuevo frente al informe de la tarea 4:** el generador HKDF guarda la PRK en su HMAC (`inputPad`, `ipadState`, `opadState`) y su último bloque en `currentT`, que con SHA-256 y 32 bytes **es la propia clave derivada**. Lo confirma `javap`: `currentT` se reserva en `init` y `generateBytes` copia desde él. En la cascada (`:308-311,336-346`) y en la clave por mensaje de una clave aleatoria (`:441-445`), borrar el `KeyParameter` de cada capa no quita esa clave del heap.
- Las subclaves de los motores, y H y las tablas de GCM.

**Veredicto: PARCIALMENTE CERRADO.** Coincide con la corrección que proponía el hallazgo («poner a cero `KeyParameter`… y apuntar el resto como riesgo aceptado»). El párrafo de riesgo residual tiene que nombrar también lo de HKDF (N-03).

### CRY-B02: parámetros de TOTP

**Qué hace ahora:**
- `hmacName` solo conoce SHA1 (también el nombre vacío de una entrada guardada), SHA256 y SHA512 (`crypto/Totp.kt:85-90`).
- `isSupported` exige 6 a 8 dígitos y un periodo de 10 a 120 s (`:93`).
- `totpCode` devuelve `null` antes de hacer ninguna cuenta (`:123`), y `hotp` rechaza lo que no admite (`:106-107`).
- El enlace `otpauth://` solo usa los valores por defecto cuando falta el parámetro, y `algorithm=` vacío se rechaza (`:184-189`).
- El comentario de RFC 4226 está corregido (`:59-63`).

**Límites probados:**
- **Dígitos de 6 a 8:** el módulo llega como mucho a 10⁸ < 2³¹, y `binary` tiene 31 bits.
- **Periodo de 10 a 120:** no hay división por cero, porque se comprueba antes (`:123`).
- **Contador:** es `Long`, con `putLong`.
- **`nowMillis = Long.MAX_VALUE`:** `seconds / period` no desborda, y `seconds % period < 120` cabe en `Int` con `toInt()`.
- **`offset + 3`:** como mucho 18, por debajo de 20 bytes (SHA-1).
- **Nombres:** `uppercase()` de Kotlin no depende del idioma del móvil.
- **Duplicados en el enlace:** se queda el último valor (`toMap`), y si no se admite se rechaza.
- **`toIntOrNull`:** «+8» da 8, y un número enorme da `null`, que se rechaza.
- **Hora negativa:** `/` y `%` truncan hacia cero, y la cuenta daría un «quedan» mayor que el periodo. Viene de antes y no es alcanzable: Android no admite una hora anterior a 1970.

**Datos guardados:** la exportación de Google Authenticator siempre da ajustes admitidos (`OtpMigrationTest`, `everyImportedEntryIsSupportedAndGivesACode`), y los archivos reales también (`vault/VaultTotpTest.kt:291`).

**Veredicto: CERRADO.**

### Tarea 6 (fuera CriptoPanel) y tarea 7 (versión)

**CriptoPanel solo recibe el error genérico:**

| Dato | Error | Prueba |
|---|---|---|
| Un texto `CP1:` | «El texto cifrado debe empezar por SG1:», el mismo que para cualquier texto | `crypto/DialectTest.kt:150-171` |
| Su cabecera detrás de `SG1:` | `NOT_COMPATIBLE` | `Crypto.kt:490`; mismo test |
| `.cpenc` | «No es un dato cifrado compatible», y no se escribe nada | `DialectTest.kt:173-183` |
| `.cpenc` en «Abrir lo recibido» | `PickedFile.Other` | `vault/PkActionsTest.kt:1026-1028` |
| `criptopanel-vault`, como texto o como archivo | «No es una bóveda compatible», y la bóveda del móvil queda igual byte a byte | `vault/LegacyVaultTest.kt:46-82` |

**Lo que se quitó no deja fuera ningún dato de Clavero:**
- **El identificador de KDF 2:** Clavero nunca lo escribió. `newHeader` escribe `KDF_RAW_KEY_HKDF` desde el primer commit, `c70f932`. Ahora se rechaza con «Derivación de clave desconocida» (`DialectTest.kt:189-197`).
- **`format` obligatorio** (`vault/Vault.kt:118`): toda versión de Clavero lo escribió. En el primer `Vault.kt` (`07f220e`) ya pone «Not the default, so it is always written out», y `appJson` omite solo los valores por defecto.
- **La AAD v1:** solo existió en el formato heredado.

**Tarea 7:** solo cambia `versionName = "1.3.1"` (`app/build.gradle.kts:35`). El intento con R8 se revirtió, y el registro dice que `classes.dex` y `mapping.txt` quedan iguales byte a byte.

---

## 2. Hallazgos nuevos

Ninguno llega a Media, así que no hace falta el vector CVSS (se da cuando ayuda). Las severidades siguen la escala del proyecto.

| ID | Severidad | MASVS | Hallazgo | Dónde |
|---|---|---|---|---|
| N-01 | Informativa | AUTH-2, CRYPTO-2 | Cualquier `CryptoFailure` de `unlockWithKey` desactiva la huella: también un JSON que no se decodifica con la clave **correcta**, y se prueba la clave antes de mirar si la versión se abre | `vault/VaultStore.kt:105,449-458`; `vault/Vault.kt:285-286` |
| N-02 | Informativa | AUTH-2, CRYPTO-2 | La estimación de la maestra cuenta unidades UTF-16: 5 emojis iguales, texto en NFD o U+200B entre las cifras pasan, aunque el KDoc dice que se rechaza «una racha de caracteres iguales» | `health/Health.kt:85-92,96-126`; `vault/MasterPolicy.kt:87-88,102-105` |
| N-03 | Informativa | STORAGE-2, CRYPTO-2 | El párrafo de riesgo residual de CRY-A03 se queda corto (la PRK y `currentT` de HKDF, el TOTP, HPKE) y es inexacto sobre `SecretKeySpec` | `task-4-report.md:160`; `crypto/Crypto.kt:308-311,441-445` |
| N-04 | Informativa | NETWORK-1 | La segunda validación no recibe los SCT de la extensión TLS ni el OCSP grapado: si Android exige CT, puede rechazar de más (falla cerrada) | `android/TrustedChain.kt:29` |
| N-05 | Informativa | CODE (calidad) | Llamada nueva a `BiometricVault.disable` (IPC del Keystore) en el hilo principal | `ui/screens/VaultScreen.kt:318-321` |
| N-06 | Informativa | AUTH-2 | Un `vault.bin` que esta versión no abre (versión 1 o 4, otro formato) da el texto de «cada 7 días» y, con la maestra correcta, «Contraseña incorrecta»: el usuario puede acabar en «He olvidado», que borra | `ui/screens/VaultScreen.kt:337-338,354-355`; `vault/Vault.kt:266,401` |
| N-07 | Informativa | CODE-4 (experiencia de uso) | Un TOTP no admitido no enseña nada en la lista, y el inspector de QR ofrece «Añadir» para un enlace que después se rechaza | `ui/screens/VaultScreen.kt:455-462`; `tools/qr/QrView.kt:193` |
| N-08 | Informativa | Proceso | La plantilla del PR y `CLAUDE.md` siguen pidiendo que se lean `CP1:`, `.cpenc` y `criptopanel-vault` | `.github/pull_request_template.md:9`; `CLAUDE.md`, regla de formatos |

### [Informativa] N-01: la huella se desactiva por un fallo que no es «otra clave»

- **Dónde:**
  - `vault/VaultStore.kt:105-111`: `catch (e: CryptoFailure)` sin distinguir la causa;
  - `vault/VaultStore.kt:449` (`resolve`) antes de `:454` (`biometricRefusal`);
  - `vault/Vault.kt:285-286`: `SerializationException` se convierte en `CryptoFailure()`.
- **Qué pasa:**
  - Con la DEK correcta, un blob que autentica pero cuyo JSON no se decodifica también da `FINGERPRINT_STALE` y desactiva la huella. Solo pasaría con entradas escritas por una versión incompatible, después de una vuelta atrás que Android no permite sin desinstalar.
  - Un `vault.bin` con otro formato y sin `version` se prueba con la clave en `resolve` antes de que `biometricRefusal` diga que no se abre. Es el menor aplazado de la tarea 6.
- **Escenario:** no lo puede provocar un atacante: hay que escribir en `vault.bin`, y eso pide root. Es un caso de robustez. En los dos casos, el texto atribuye la causa a un cambio de maestra o a una importación, y eso es falso.
- **Corrección:** atar la desactivación al fallo de **autenticación** y mirar antes si el archivo se abre.

  ```kotlin
  // vault/Vault.kt: lo que la huella necesita saber, sin decodificar nada.
  internal fun opensWithKey(f: VaultFile) = supported(f)
  /** True si [dek] autentica el blob de datos de [file] (alguna versión candidata); no decodifica el JSON. */
  fun authenticates(file: VaultFile, dek: ByteArray): Boolean = try {
      openAny(dek, file.data, file) { dataAad(file, it) }.first.fill(0); true
  } catch (e: CryptoFailure) { false }

  // vault/VaultStore.kt
  class StaleBiometricKey : Exception()   // no es una CryptoFailure

  private suspend fun unlockWithKeyLocked(key: ByteArray, since: Int) {
      val v = vault ?: return key.fill(0)
      if (!VaultCrypto.opensWithKey(v)) { key.fill(0); throw MasterRequired(BiometricRefusal.MASTER_DUE) }
      if (!VaultCrypto.authenticates(v, key)) { key.fill(0); throw StaleBiometricKey() }
      val known = VaultCrypto.resolve(v, key)   // ya autentica: no puede dar «otra clave»
      // … biometricRefusal y opened() como hoy; una CryptoFailure de opened() es «datos dañados», no «otra clave».
  }

  // launchUnlockWithKey: catch (e: StaleBiometricKey) -> onWrongKey(); onError(FINGERPRINT_STALE)
  //                      catch (e: CryptoFailure)     -> onError(biometricUnlockError(e))  // conserva la huella
  ```

  Coste: un descifrado AES-GCM más del blob en cada desbloqueo con huella.

  **Tests:**
  - una DEK de otra bóveda sigue dando `FINGERPRINT_STALE`;
  - un blob que autentica con un JSON inválido da `CryptoFailure` y conserva la huella;
  - `format = "otra-cosa"` sin versión da `MasterRequired` sin usar la clave.

### [Informativa] N-02: la estimación se salta con texto fuera del BMP, en NFD o con caracteres invisibles

- **Dónde:** `health/Health.kt:85-92` (el grupo de 100 símbolos se activa por cada unidad UTF-16 ≥ 128), `:96-126` (`step` y `effectiveLength` comparan unidades UTF-16); el KDoc en `vault/MasterPolicy.kt:87-88`: «The estimate refuses … a run of equal characters».
- **Qué pasa:** con datos medidos en las clases compiladas (tabla de CRY-A01):
  - «😀😀😀😀😀» son 5 caracteres visibles y dan 66,4 bits;
  - «ąąąąą» en NFD da 69,8 bits;
  - «12345» con U+200B entre las cifras da 67,8 bits.

  Las tres pasan como maestra nueva. Los pares sustitutos y las marcas combinantes cortan la detección de rachas y secuencias, y cualquier unidad no ASCII suma 100 símbolos al grupo. Además, `MIN_MASTER` cuenta unidades UTF-16, así que 5 emojis cumplen «Mínimo 10 caracteres».
- **Escenario:** el usuario elige «😀😀😀😀😀». Quien tenga una `.sgbak` la prueba fuera de línea entre las primeras candidatas. No es un vector de un tercero: es el mismo riesgo de CRY-A01, con una elección rara.
- **Corrección:** que `estimateBits` mida puntos de código sobre NFC y sin caracteres de formato (Cf). Solo endurece «Salud» en estos casos raros.

  ```kotlin
  fun estimateBits(password: String): Double {
      val p = Normalizer.normalize(password, Normalizer.Form.NFC).filterNot { Character.getType(it) == Character.FORMAT.toInt() }
      if (p.isEmpty() || p.lowercase(Locale.ROOT) in COMMON) return 0.0
      val cps = p.codePoints().toArray()
      var pool = 0
      if (cps.any { it in 'a'.code..'z'.code }) pool += 26
      if (cps.any { it in 'A'.code..'Z'.code }) pool += 26
      if (cps.any { it in '0'.code..'9'.code }) pool += 10
      if (cps.any { it < 128 && !Character.isLetterOrDigit(it) }) pool += 33
      if (cps.any { it >= 128 }) pool += 100
      return effectiveLength(cps) * ln(pool.toDouble()) / ln(2.0)   // step(Int, Int) y effectiveLength(IntArray)
  }
  ```

  **Tests:** las tres candidatas de arriba dan `MASTER_TOO_WEAK`, y todo lo que hoy pasa en `MasterPolicyTest.kt:262-312` sigue pasando. Si el dueño prefiere no tocar el código, hay que corregir el KDoc: «una racha de caracteres iguales (del plano básico, en NFC)».

### [Informativa] N-03: el párrafo de riesgo residual de CRY-A03 se queda corto

- **Dónde:** `task-4-report.md:160`; `crypto/Crypto.kt:308-311` (`cascadeKey`) y `:441-445` (`rawKeyHkdf`).
- **Qué pasa:**
  - `HKDFBytesGenerator` guarda `currentT` (comprobado con `javap`: se reserva en `init` y `generateBytes` copia desde él). Con SHA-256 y 32 bytes, `currentT` **es** la clave derivada: la de cada capa de la cascada y la clave por mensaje de una clave aleatoria. Su `HMac` guarda la PRK (`inputPad`, `ipadState`, `opadState`). Ninguna de las dos se puede borrar con la API pública: volver a llamar a `init` reserva un `currentT` nuevo y deja el viejo.
  - En la cascada, borrar el `KeyParameter` de Twofish y de Serpent no reduce lo expuesto, porque la misma clave sigue en `currentT`.
  - La frase «la copia que pueda guardar `SecretKeySpec`… no verificado» es inexacta. `SecretKeySpec` copia siempre la clave (parte A, comprobado), y Conscrypt guarda otra, la de `getEncoded()` (según su código, sin comprobar en el móvil).
  - No se nombran `hotp` (`SecretKeySpec(key, "RAW")`, `crypto/Totp.kt:108`) ni los objetos de HPKE, Ed25519 y X-Wing, aceptados en la 1.2.0.
- **Impacto:** ninguno en el código. El documento de la publicación afirmaría que se borra más de lo que se borra.
- **Corrección:** usar el párrafo del final de este informe en `docs/auditorias/2026-11-01-auditoria-1.3.1.md`.

### [Informativa] N-04: la segunda validación no recibe los datos de la sesión

- **Dónde:** `android/TrustedChain.kt:29`, `extensions.checkServerTrusted(chain, authType, host)`.
- **Qué pasa:** la variante con nombre de servidor de `TrustManagerImpl` valida sin el OCSP grapado ni los SCT de la extensión TLS (código de Conscrypt), mientras que la primera validación (`base` con el socket) sí los tiene.
  - Si Android exige Certificate Transparency a la app (la opción `<certificateTransparency>` de Network Security Config; hay que comprobar su valor por defecto cada vez que suba `targetSdk`), y el servidor entrega los SCT solo por TLS o por OCSP, la segunda validación falla. El usuario vería «No se pudo comprobar. Revisa la conexión.».
  - Hoy no pasa: con `targetSdk = 36` no hay CT por defecto en la configuración de la app (no hay `networkSecurityConfig`), y el certificado de `api.pwnedpasswords.com` lleva los SCT dentro.
- **Impacto:** solo de disponibilidad. Falla cerrada.
- **Corrección:** ninguna ahora. Añadir a la lista de pruebas en el móvil de cada subida de `targetSdk`: «Comprobar contraseña» con red.

### [Informativa] N-05: `BiometricVault.disable` en el hilo principal

- **Dónde:** `ui/screens/VaultScreen.kt:318-321`. El `catch` de `launchUnlockWithKey` corre en el `Dispatcher` de la pantalla (Main), y `disable` hace `KeyStore.load` y `deleteEntry` (`android/BiometricVault.kt:102-104,151-158`).
- **Qué pasa:** es una IPC con keystore2 en el hilo principal. Suele durar milisegundos, pero es el mismo patrón que el menor aplazado M7 (`VaultScreen.kt:278,373`), ahora en un sitio más. La lambda también retiene el `Context` de la actividad.
- **Corrección:** hacer `onWrongKey` `suspend` y llamar a `withContext(NonCancellable + Dispatchers.IO) { BiometricVault.disable(context.applicationContext) }`. `fingerprintStale = true` se queda antes, en Main.

### [Informativa] N-06: una bóveda que esta versión no abre parece una contraseña olvidada

- **Dónde:**
  - `vault/Vault.kt:401`: un formato o versión que no se abre da `MASTER_DUE`;
  - `ui/screens/VaultScreen.kt:354-355`: «Por seguridad, cada 7 días…»;
  - `VaultScreen.kt:337-338`: toda `CryptoFailure`, también `NOT_A_VAULT` (`Vault.kt:266`), se enseña como «Contraseña incorrecta»;
  - `vault/VaultStore.kt:956-964`: `destroy` borra.
- **Qué pasa:** con un `vault.bin` de versión 4, que escribiría una Clavero futura, el usuario ve dos textos falsos seguidos, y lo razonable sería pulsar «He olvidado la contraseña maestra». No es alcanzable hoy: Android no deja volver a una versión anterior sin desinstalar, y la copia de la plataforma está desactivada. El texto «cada 7 días» ya está apuntado para el Abogado.
- **Corrección:**
  - en `UnlockVault`, `catch (e: CryptoFailure) { error = if (e.message == NOT_A_VAULT) NOT_A_VAULT else "Contraseña incorrecta" }`, haciendo pública la constante;
  - un motivo propio, `BiometricRefusal.UNSUPPORTED`, con su texto (para el Abogado).

### [Informativa] N-07: TOTP no admitido en la lista y en el inspector de QR

- **Dónde:**
  - `ui/screens/VaultScreen.kt:455-462`: la fila no enseña ni código ni motivo;
  - `tools/qr/QrView.kt:192-193`: la nota es correcta, pero ofrece `ADD_TOTP`, y el editor después rechaza el enlace (`ui/screens/VaultEntryScreen.kt:260`).
- **Qué pasa:** la ficha sí lo dice (`VaultEntryScreen.kt:149-154`). La parte B pedía el texto «en la lista y en la ficha». No es de seguridad: no se enseña ningún código falso.
- **Corrección:** en la fila, un texto corto en lugar del código; en `QrView`, `action = null` cuando `otpAuthUnsupported`.

### [Informativa] N-08: los documentos del proceso siguen pidiendo leer CriptoPanel

- **Dónde:** `.github/pull_request_template.md:9` y la regla «No romper datos existentes» de `CLAUDE.md`. El registro ya lo apunta como paso del controlador.
- **Impacto:** un revisor que siga la lista del PR exigiría algo que la 1.3.1 quita a propósito.
- **Corrección:** quitar `CP1:`, `.cpenc` y `criptopanel-vault` de las dos, y decir que se rechazan con el error genérico.

### Menores anteriores que la rama arrastra (aplazados en el registro; se arreglan antes de fusionar)

- **A-1, Informativa: copias de la clave TOTP sin borrar.**
  - Dónde: el búfer interno del `ByteArrayOutputStream` de `base32Decode` (`crypto/Totp.kt:27,38`), que corre en cada código, cada segundo y por cada fila visible; `ui/screens/VaultEntryScreen.kt:494`, `totpKey(...) == null` sin borrar; `vault/TotpImport.kt:7`, la clave como `String` en hexadecimal.
  - Impacto marginal casi nulo: mientras la bóveda está abierta, `TotpSecret.secret` ya está en el heap como `String`.
  - Corrección: decodificar en un `ByteArray(clean.length * 5 / 8)` exacto, sin el `ByteArrayOutputStream`, y borrar en la validación de la pantalla.
- **A-2, rendimiento:** `ui/screens/SecurityToolsScreen.kt:936` lee la lista de la EFF en el hilo principal dentro de `remember`. `rememberMasterWords` vuelve a leer y normalizar unas 11.800 palabras en cada apertura y cada giro (`ui/screens/MasterWords.kt:26-27`). Se puede guardar en `AppContainer` con un `lazy`, porque no es secreto.
- **A-3, comentarios:** `CryptoTest.kt:47-48` (Twofish sí tiene un vector publicado del motor) y las líneas KDoc de 121 a 126 caracteres (`vault/Vault.kt:343,391,393`, `crypto/DialectTest.kt:27-28`).

---

## 3. MASVS v2 en lo que toca el cambio

| Categoría y control | Estado | Evidencia |
|---|---|---|
| **MASVS-CRYPTO-1** (primitivas y uso correctos) | Cumple | TOTP: se rechaza en vez de cambiar (`crypto/Totp.kt:85-93,105-107,123,184-189`). Se quita el camino más débil, una clave aleatoria sin HKDF (`crypto/Crypto.kt:434-438,476-477`). Los vectores fijos de los 6 algoritmos (`CryptoTest.kt:504`) y los de Python `cryptography` (`DialectTest.kt:128`) siguen igual |
| **MASVS-CRYPTO-2** (gestión de claves) | Cumple, con riesgo residual aceptado | Borrado de `KeyParameter` y de ChaCha (`Crypto.kt:288-305,369-389`); búfer borrado en los fallos (`:263-279`). Maestra nueva con la estimación y las listas (`vault/MasterPolicy.kt:102-107`). Residual: AES y Conscrypt, HKDF y subclaves (N-03); estimación con texto fuera del BMP (N-02) |
| **MASVS-NETWORK-1** (validación TLS) | Cumple; falta la prueba en el móvil | Antes y después del sistema (`net/DistrustedCertificates.kt:108-127`); cadena validada desde Android (`android/TrustedChain.kt:21-31`); conectado en `SeguridadApp.kt:30`, `AppContainer.kt:84,106`. Ver N-04 |
| **MASVS-NETWORK-2** (política de confianza propia) | Cumple | La lista de eMudhra se aplica ahora a la cadena que validó el sistema; `getAcceptedIssuers` sigue filtrado (`:137`) |
| **MASVS-AUTH-2** (autenticación local) | Cumple | Regla de la maestra nueva solo al crearla o cambiarla (`ui/screens/VaultScreen.kt:270`, `VaultBackup.kt:118,235`). Huella antigua desactivada (`vault/VaultStore.kt:105-111`). `!supported` da `MASTER_DUE` (`vault/Vault.kt:401`). La biometría sigue ligada a `CryptoObject`, y la regla de los 7 días y la del reloj no cambian. Ver N-01 y N-06 |
| **MASVS-STORAGE-1** (almacenamiento) | Cumple | Nada nuevo en disco. `disable` quita el IV, el blob y la clave (`android/BiometricVault.kt:151-158`). Las listas son `assets` públicos |
| **MASVS-STORAGE-2** (fugas) | Cumple | Ninguna línea añadida con `Log.`, `println` ni `printStackTrace`. Ningún `rememberSaveable` nuevo; la única mención es el comentario de `MasterWords.kt:17`. La maestra sigue en `remember` (`VaultBackup.kt:227-228`). `FLAG_SECURE` sin cambios (`android/SecureWindow.kt:24`) |
| **MASVS-CODE-3** (dependencias) | Cumple | Sin cambios en `gradle/libs.versions.toml` ni en `gradle/verification-metadata.xml`. R8 9.1.31 revertido (`94ddb7f`). `bcprov` sigue en 1.86 |
| **MASVS-CODE-4** (entradas no fiables) | Cumple | `otpauth` más estricto (`Totp.kt:161-193`). Cabecera de CriptoPanel rechazada con el error genérico (`Crypto.kt:487-496`). `format` obligatorio (`Vault.kt:118,382-386`). Ver N-07 |
| **MASVS-PRIVACY-1/2** | Cumple | Sin datos, hosts ni SDK nuevos. La petición a HIBP no cambia (solo el prefijo, con `Add-Padding`). El `User-Agent` sigue igual (`net/HttpClient.kt:28`) |
| **MASVS-PLATFORM** (no pedido; comprobado) | Sin cambios | `AndroidManifest.xml` y `res/` iguales a `9504ce3` (`git diff --quiet`): sin permisos ni componentes exportados nuevos |

## 4. Hermeticidad y formatos

**Hermeticidad:**
- **Permisos, componentes y recursos:** `git diff --quiet 9504ce3..e6796db -- app/src/main/AndroidManifest.xml app/src/main/res` no da diferencias.
- **Hosts:** las únicas URL de red siguen siendo `https://api.pwnedpasswords.com/` (`net/PwnedPasswords.kt:24`). El resto son espacios de nombres XMP y enlaces de licencias.
- **Dependencias:** no cambian. En el diff de la compilación solo hay `versionName` y las entradas de `jvm-check`, que solo sirven para los tests (`jvm-check/build.gradle.kts:40-50`).
- **Logs:** ninguno nuevo.
- **`FLAG_SECURE`:** intacto.
- **Secretos en `Bundle`:** ninguno. `rememberMasterWords` usa `remember` y las listas no son secretas.
- **`fill(0)` en todas las salidas:**
  - la clave de la huella en todo camino de `unlockWithKey` (`VaultStore.kt:438-442,451,456`);
  - `KeyParameter` y la salida de Bouncy Castle (`Crypto.kt:275,303,382`);
  - las claves de la cascada (`:343-344`);
  - la clave TOTP en `isUsableTotpKey` y `totpCode` (`Totp.kt:71,129`).

  Queda A-1, anterior al cambio.

**Formatos que se siguen abriendo:**

| Formato | Prueba |
|---|---|
| `SG1:` y `.sgenc` escritos por la 1.3.0, también byte a byte al escribir | `DialectTest.kt:77` |
| `SG1:` y `.sgenc` escritos fuera de Clavero (Python `cryptography` 50.0.1) | `DialectTest.kt:128` |
| `seguridad-vault` v2 (1.1, `vault-1.1.json`) y su conversión a v3 | `VaultV3Test.kt:493` (`v2Fixture`) y `:704` |
| `seguridad-vault` v3 | `VaultV3Test` (`v3Fixture`), `BackupTest.formatPinned` |
| `seguridad-vault` sin versión | `VaultTamperTest.kt:66`, `VaultV3Test.kt:501,526` |
| `.sgbak` v1 (`backup-1.1.sgbak`) y v2 | `BackupTest`, `ImportHeadTest.kt:22-26` |
| Exportación JSON de la 1.0 (`export-1.0.2.json`, `seguridad-vault` v2) | `BackupTest.kt:200-203` |
| `SGK1:`, `SGM1:`, `SGS1:` | `vectors/seguridad-pk.json` sin cambios; `MessageTest` solo pierde una línea de `CP1:` |
| Lo de CriptoPanel | Solo el error genérico (§1, tarea 6) |

## 5. Lo que está bien

- **CRY-B01:** la defensa no depende de la ruta. Toda ruta pasa por la SPKI comprometida, y el arreglo falla cerrado ante cualquier excepción. La inyección por el constructor hace imposible un cliente sin la función de Android. Los tests espían que el sistema nunca vea una cadena comprometida.
- **CRY-A01:** la regla está fuera de `VaultStore`, así que los datos de prueba y las bóvedas existentes no cambian, y la importación queda fuera de forma explícita.
- **CRY-A02:** `MasterRequired`, el reloj y los demás fallos conservan la huella, y el texto sale aunque `onWrongKey` lance.
- **CRY-A03:** las suposiciones sobre Bouncy Castle están fijadas por reflexión (`CryptoTest.kt:479-503,585-612`); si `bcprov` cambia, el test falla en vez de dejar de borrar en silencio.
- **CRY-B02:** el texto del aviso es verdadero, y el inspector de QR enseña los nombres sin la clave.
- **Tarea 6:** reduce superficie y deja vectores independientes del contenedor `SG1:` y `.sgenc`.

## 6. Siguientes pasos

1. **Antes de fusionar:**
   - N-08 (documentos del proceso);
   - N-03 (texto del documento de la auditoría);
   - N-01 (código, con sus tests);
   - A-3 (comentarios).
2. **Si el dueño lo aprueba:** N-02 (estimación por puntos de código), N-05, N-06, N-07 y A-1/A-2. Todos son pequeños y no cambian ningún formato.
3. **Pruebas en el móvil:** las de CRY-B01, CRY-A02 y CRY-A01, más la de N-04 en cada subida de `targetSdk`.
4. Revisión final del Abogado Digital con la lista de abajo.

## Textos para el Abogado Digital (revisión final)

- `MASTER_TOO_SHORT`, «Mínimo 10 caracteres» (`vault/MasterPolicy.kt:14`). Cuenta unidades UTF-16: 5 emojis cumplen (N-02).
- `MASTER_TOO_WEAK` (`vault/MasterPolicy.kt:17-18`).
- `FINGERPRINT_STALE` (`vault/VaultStore.kt:65-67`). Afirma la causa como un hecho, y un `vault.bin` dañado da el mismo texto (N-01).
- `TOTP_UNSUPPORTED_LINK` y `TOTP_UNSUPPORTED_ENTRY` (`crypto/Totp.kt:96,99`).
- El texto «cada 7 días…» que sale también para una bóveda que no se abre (`ui/screens/VaultScreen.kt:354-355`, N-06).
- Textos de importación sin CriptoPanel: `VaultScreen.kt:293`, `SettingsScreen.kt:475`, `SecurityToolsScreen.kt:353,408,866`, y el README («Formatos», `:44-60`, `:24`, `:305`).
- `LicensesScreen.kt:36-37`. Las listas BIP-39 y EFF ahora también sirven para comprobar la maestra, no solo para el Generador. Hay que ver si la descripción de la EFF («El Generador usa las mismas palabras…») debe decirlo.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza aplicado a cada arreglo:**
  - CRY-B01 frente a un intermediario con una clave de eMudhra: el control depende de la SPKI y no de la ruta (`net/DistrustedCertificates.kt:70-71,110,112`).
  - CRY-A01 frente a quien tiene una `.sgbak`: `vault/MasterPolicy.kt:102-107`.
  - CRY-B02 frente a un QR manipulado: `crypto/Totp.kt:161-193`.
  - CRY-A02 y CRY-A03 quedan fuera del atacante remoto: `vault/VaultStore.kt:105-111`, `crypto/Crypto.kt:263-305`.
- **Sin criptografía propia:** HKDF, AES-GCM, ChaCha20-Poly1305 y HMAC salen de `javax.crypto` y de Bouncy Castle 1.86. El `init` de clave cero no cifra nada: solo sobrescribe estado, en modo descifrar (`Crypto.kt:384`).
- **Parámetros:**
  - etiqueta de 128 bits y nonce de 96 bits sin cambios (`Crypto.kt:300,350,379`);
  - Argon2id sin cambios;
  - umbral de la maestra en 50 bits, el de «Salud» (`health/Health.kt:13`);
  - TOTP con dígitos de 6 a 8 (RFC 4226 §5.3) y periodo de 10 a 120 s (`Totp.kt:76,79`).
- **Ramas de error:** borrado en todas las de Bouncy Castle (`Crypto.kt:275,303,382-388`). Las cadenas TLS fallan cerradas (`android/TrustedChain.kt:25,28`). La huella solo se desactiva con `CryptoFailure` (`VaultStore.kt:105`), con la reserva de N-01.
- **Honestidad del riesgo residual:**
  - CRY-A03 se rebaja a «parcialmente cerrado», sin cambiar su severidad (Baja, 1.9).
  - Se añade lo de HKDF, comprobado con `javap` (N-03).
  - No se afirma nada de Conscrypt ni de BoringSSL sin decir de dónde sale.
- **Proporción:** ningún hallazgo nuevo supera Informativa. Las decisiones del dueño (la regla de la maestra tal como está diseñada, CriptoPanel fuera, los avisos de R8 en la 1.4.0) no se cuentan como defectos. Solo se nombra su daño concreto (§1, CRY-A01).

## Revisión Ingeniero Móvil Senior

- **TLS en Android.**
  - `X509TrustManagerExtensions` acepta el `RootTrustManager` de Network Security Config desde la API 24, y `minSdk` es 26 (`app/build.gradle.kts:31`). Se crea una sola vez, al construir el cliente perezoso (`AppContainer.kt:106`).
  - El host sale de `handshakeSession.peerHost`, el mismo sitio del que lo lee `RootTrustManager` (`net/DistrustedCertificates.kt:118-126`).
  - El coste es una segunda validación por handshake, despreciable para una consulta pulsada a mano.
  - El código Android-only está fuera de `jvm-check` (`jvm-check/build.gradle.kts:7-12`), así que hace falta la prueba en el móvil (N-04).
- **Ciclo de vida y estado.**
  - `fingerprintStale` vive en `remember`. Tras un giro, `BiometricVault.enabled` ya da `false` si `disable` funcionó (`ui/screens/VaultScreen.kt:309-310`).
  - El límite de BOV-3, que tras un giro no se repite `FINGERPRINT_STALE`, está documentado en `vault/VaultStore.kt:91-92`.
  - Las listas de la maestra se cargan en `Dispatchers.IO` dentro de `LaunchedEffect`, y la cancelación se propaga (`ui/screens/MasterWords.kt:26-27`).
  - La maestra sigue en `remember` (`ui/screens/VaultBackup.kt:227-228`, `VaultScreen.kt:242`).
- **Concurrencia.** `launchUnlockWithKey` sigue `ATOMIC` y la clave se borra en todo camino (`vault/VaultStore.kt:94-115,435-443`). `chaChaPoly` y `runAead` solo usan estado local a la llamada (`crypto/Crypto.kt:263-279,369-389`).
- **Hilo principal.** Llamada nueva a `BiometricVault.disable` en Main (`VaultScreen.kt:320`, N-05). Lectura de listas repetida en cada apertura y cada giro, y la de la EFF en Main (`SecurityToolsScreen.kt:936`, A-2).
- **Accesibilidad.** Los avisos nuevos de la maestra son una región viva cortés (`VaultScreen.kt:254`, `VaultBackup.kt:246`). Falta comprobarlo con TalkBack, una prueba que el informe de la 1.3.0 ya tenía pendiente.
- **Compilación.** Solo cambia `versionName` (`app/build.gradle.kts:35`). Según el registro, R8 y el dex son iguales que en la 1.3.0, sin permisos nuevos (`check-manifest` OK, `badging` igual).

## Texto de riesgo residual de CRY-A03 para `docs/auditorias/2026-11-01-auditoria-1.3.1.md`

> **Riesgo residual aceptado: copias de las claves en la memoria (CRY-A03, Baja, CVSS 3.1 1.9, `AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N`).**
>
> **Qué se borra desde la 1.3.1.** En los cifrados que hace Bouncy Castle (Twofish-, Serpent- y Camellia-GCM, ChaCha20-Poly1305 y las capas de la cascada), en toda salida y también cuando la etiqueta no cuadra:
> - la copia de la clave que guarda `KeyParameter`, que es el mismo array que `GCMBlockCipher.lastKey` y que la clave de trabajo de Twofish;
> - en ChaCha20-Poly1305, además, la copia propia de la clave, las palabras de clave del motor y la clave de Poly1305, que se sobrescriben con un último `init` de clave cero;
> - el búfer en el que Bouncy Castle descifra, si el descifrado falla.
>
> **Qué no se puede borrar.** Con las API disponibles no se puede poner a cero lo siguiente, que queda en la memoria del proceso hasta que el recolector reutiliza ese espacio, también después de bloquear la bóveda:
> - **AES-256-GCM**, que cifra la bóveda (la DEK y la KEK), los adjuntos y, por defecto, los textos y los archivos: la copia que hace `SecretKeySpec` al construirse y la que guarda el `Cipher` de Conscrypt al iniciarse. Esta última sale del código de Conscrypt y no se ha comprobado en el móvil.
> - **HKDF-SHA256**, que da las claves de las capas de la cascada y la clave por mensaje de una clave aleatoria:
>   - la copia de la entrada que hace `HKDFParameters`;
>   - la PRK que guarda el HMAC del generador;
>   - el último bloque de salida del generador, que con 32 bytes es la propia clave derivada.
>
>   Por eso, en la cascada, la clave de cada capa sigue en la memoria aunque se borre su `KeyParameter`.
> - **Las subclaves** que expanden Twofish, Serpent y Camellia, y la subclave H y las tablas que GCM deriva de la clave.
> - **Otras claves:** la clave de cada código TOTP, que pasa por `SecretKeySpec` en cada código, y los objetos de clave de HPKE, Ed25519 y X-Wing (aceptados en la 1.2.0).
>
> **Límites del borrado.** Es de mejor esfuerzo: el recolector de ART puede mover un array antes de borrarlo y dejar una copia anterior.
>
> **Por qué se acepta.** Leer cualquiera de estas copias exige volcar la memoria del proceso (root o un depurador), y quien puede hacerlo también puede esperar al siguiente desbloqueo.
>
> **Alternativa descartada.** Se descartó cifrar AES con la implementación de Bouncy Castle para poder borrar su clave: se perdería la aceleración por hardware y la resistencia a los ataques de tiempo.
