# Seguridad: app Android independiente (diseño)

Fecha: 23 de octubre de 2026. Estado: aprobado por el usuario el 23 de octubre de 2026.

**Nota para el constructor:** el paquete `app.seguridad` y los formatos nuevos (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup` y las etiquetas `seguridad-…`) no se pueden cambiar una vez publicada la 1.0.0.

**Integración** (decisión del usuario): Claude fusiona en `main` el PR de cada fase cuando los tests y la CI están en verde, sin esperar revisión.

**Firma** (decisión del usuario): firma con clave propia.
- La clave de subida de CriptoPanel solo existe en los secretos de GitHub de ese repositorio, que no se pueden leer. El usuario añade a `Egt17baa/Seguridad` los secretos `SEGURIDAD_KEYSTORE_BASE64`, `SEGURIDAD_KEYSTORE_PASSWORD`, `SEGURIDAD_KEY_ALIAS` y `SEGURIDAD_KEY_PASSWORD`, con su `criptopanel-upload.jks` o, mejor, con una clave nueva solo para esta app.
- Mientras no existan, la CI firma con la clave de prueba y lo avisa.

## Intención

El usuario quiere una app **solo de seguridad y criptografía**, sin nada de CriptoPanel: ni mercado, ni cartera, ni alertas, ni DeFi, ni su marca. El punto de partida es el código del apartado «Seguridad» de CriptoPanel (rama `claude/superpower-671dj6` de `Egt17baa/CriptoPanel`, versión 1.10.0, diseño en `docs/superpowers/specs/2026-10-22-criptopanel-seguridad-design.md` de ese repositorio). Ese código ya pasó cuatro revisiones de seguridad y de código. La app nueva tiene que:

1. conservarlo y mejorarlo;
2. añadir más funciones de seguridad y de criptografía, elegidas por criterio experto (el usuario delegó la elección: «lo que a las skills les parezca mejor»);
3. ser **muy estable y robusta**;
4. terminar con una revisión de código y de seguridad (skills `code-review` y `security-review`, con la skill `experto-seguridad-cripto`) que depure y corrija errores y vulnerabilidades.

## Objetivos

- Una app que una persona pueda usar a diario para sus contraseñas, sus códigos 2FA y sus frases semilla, y para cifrar, firmar y repartir secretos. Todo en el móvil y sin cuentas.
- No perder nunca datos de la bóveda: ni por un fallo, ni por un apagón, ni por una actualización.
- Solo primitivas estándar y bibliotecas auditadas (`javax.crypto`, Bouncy Castle). Ninguna primitiva propia.
- Cada función con pruebas automáticas, vectores oficiales donde existan y pruebas de manipulación.

## Fuera de alcance

- Sincronización en la nube, cuentas, servidor propio.
- Servicio de autorrelleno de Android (Autofill): mucha superficie de ataque. Se puede valorar en otra versión.
- «Borrado seguro» de archivos: en la memoria flash, con su nivelación de desgaste, no se puede garantizar. Se explica en Aprende.
- Esteganografía, VPN, antivirus.
- iOS y escritorio.
- Analítica, anuncios, telemetría o registros de errores enviados fuera.

## Lo que haría fracasar el proyecto aunque funcione

- Que una actualización o un fallo deje una bóveda que no se abre con la contraseña correcta.
- Que los textos, archivos o copias cifrados con CriptoPanel dejen de poder abrirse (el usuario ya tiene datos ahí, como sus frases semilla).
- Que se envíe por la red algo más que los 5 caracteres del hash de HIBP.
- Que haya criptografía improvisada o formatos sin autenticar.

## Restricciones

- Android, Kotlin, Jetpack Compose y Material 3. minSdk 26 y compileSdk/targetSdk 36, como en el original.
- Las mismas dependencias criptográficas: Bouncy Castle `bcprov-jdk18on` (actualizar a la última versión estable si no rompe nada) y `androidx.biometric`.
- Solo componentes con licencia libre y gratuita (requisito del usuario); escáner QR con ZXing en lugar de Google Play Services; permiso CAMERA solo al escanear.
- Red: **solo** Have I Been Pwned (`api.pwnedpasswords.com`) con OkHttp y el `DistrustingTrustManager` de eMudhra. Sin `INTERNET` en ningún otro sitio. Sin WebView.
- Interfaz en español.
- En este contenedor se pueden descargar el SDK de Android y las dependencias de Maven: la app se compila y se prueba aquí (`./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`). No hay móvil ni emulador con huella: lo que dependa del dispositivo va a una lista de comprobación manual.

## Decisiones

Las tomó el usuario: app independiente solo de seguridad; partir del código existente y mejorarlo; repositorio `Egt17baa/Seguridad`; la elección de las funciones nuevas se deja al criterio experto.

Las demás son **decisiones mías** (marcadas para que el usuario las revise):

### Identidad

- Nombre «Seguridad», paquete `app.seguridad`, versión 1.0.0.
- Tema propio de Material 3, oscuro por defecto y claro opcional. Sin el vidrio líquido de CriptoPanel. Icono nuevo: un escudo con una llave.

### Formatos y compatibilidad con CriptoPanel

- La app nueva **escribe** con sus propios identificadores:
  - texto `SG1:`, archivos `.sgenc` y bóveda con formato `seguridad-vault`;
  - etiquetas de separación de dominio `seguridad-…` en lugar de `criptopanel-…` (HKDF de la cascada, clave propia y datos asociados de la bóveda).
  - Por lo demás, la estructura binaria es la misma que la de CP1 v1 y la bóveda v2 (cabecera completa como AAD, STREAM de 64 KiB, parámetros guardados en la cabecera).
- La app nueva **lee** también lo de CriptoPanel: `CP1:`, `.cpenc` y la copia exportada de su bóveda (`criptopanel-vault`, versiones 1 y 2), cada uno con sus etiquetas originales. Al importar la bóveda de CriptoPanel se vuelve a cifrar con el formato nuevo. Los textos y archivos se descifran y, si se cifran de nuevo, salen en el formato nuevo.
- Hay pruebas con datos reales generados por el código de CriptoPanel 1.10.0 (fixtures).

### Navegación

Barra inferior con 4 pestañas:
1. **Bóveda**: entradas, búsqueda, salud.
2. **Códigos**: autenticador 2FA.
3. **Herramientas**: una cuadrícula con todo lo demás.
4. **Ajustes**: bloqueo, portapapeles, coste de Argon2id, tema, copia de seguridad, Aprende, Acerca de.

Un aviso inicial de una sola vez: sin recuperación de la contraseña maestra y sin auditoría independiente.

## Funciones

### Heredadas (se conservan con su comportamiento y sus pruebas)

Cifrar y descifrar texto y archivos (AES-256-GCM, ChaCha20-Poly1305, Twofish, Serpent, Camellia y la cascada; con contraseña Argon2id o con clave AES-256 aleatoria), modo frase semilla con BIP-39, generador de contraseñas y frases EFF con entropía, comprobador HIBP con k-anonimato, huella SHA-256 de archivos, bóveda (accesos, frases semilla, notas, 2FA; DEK envuelta con Argon2id y con el Keystore con huella; bloqueo; revisión v2; exportar e importar), TOTP RFC 6238 con QR, `FLAG_SECURE`, portapapeles sensible con borrado, protección contra superposiciones y bloqueo de los certificados de eMudhra.

### Mejoras sobre lo heredado

- **Ajustes** que antes eran fijos: bloqueo por inactividad (1, 5 o 15 min, o al salir), borrado del portapapeles (15, 45 o 90 s) y coste de Argon2id para lo nuevo (estándar 64 MiB/3, alto 128 MiB/4 si hay memoria).
- **Bóveda:** búsqueda, favoritos, carpetas o etiquetas, historial de las últimas 5 contraseñas de cada acceso, fecha de modificación y orden.
- **Copia automática cifrada** opcional a una carpeta elegida por el usuario (SAF) cada vez que cambia la bóveda, conservando las últimas N copias.
- **Generador:** también PIN, y frases con dados físicos (el usuario tira los dados y escribe los números).

### Nuevas

1. **Repartir un secreto (SLIP-0039):** divide un secreto en partes de palabras con un umbral de K de N (y grupos opcionales), y las recompone.
   - Codificación SLIP-0039 de SatoshiLabs: lista de 1.024 palabras, suma de comprobación RS1024, cifrado Feistel con frase de paso opcional. Se prueba con los vectores oficiales (`vectors.json`).
   - **Con una frase BIP-39** (12, 15, 18, 21 o 24 palabras): se reparte su **entropía** (de 128 a 256 bits; todas son múltiplos de 16 bits, como exige SLIP-0039) y, al recomponer, la app devuelve **la misma frase BIP-39**, comprobada con su checksum. *(Decisión mía.)*
   - **Aviso obligatorio y visible**, porque si se ignora se pierden fondos: estas partes **no** son una copia SLIP-39 de wallet. Si se meten en un Trezor o un Keystone como recuperación SLIP-39, saldrá **otra wallet distinta**, porque SLIP-0039 deriva la semilla de otra forma que BIP-39. Solo se recomponen en esta app, o con una herramienta SLIP-0039 que devuelva el secreto maestro (la entropía) y lo vuelva a convertir en BIP-39. No se promete compatibilidad de wallet con Trezor ni Keystone.
   - También acepta un secreto en hex de 128 o 256 bits, por ejemplo una clave AES propia.
   - Se muestra cómo imprimir o apuntar cada parte por separado, y se exige recomponer una vez como prueba antes de dar las partes por buenas.
2. **Firmas Ed25519:** generar y guardar en la bóveda un par de claves propio, y firmar textos y archivos.
   - Los archivos se firman en streaming con Ed25519ph (RFC 8032, prehash SHA-512).
   - Verificación con la clave pública de otro.
   - Formato de firma propio y versionado con separación de dominio.
   - Huella de la clave pública para comprobarla en persona.
3. **Cifrar para un contacto (HPKE, RFC 9180):** modo base con DHKEM(X25519, HKDF-SHA256), HKDF-SHA256 y ChaCha20-Poly1305 o AES-256-GCM, con la implementación HPKE de Bouncy Castle.
   - **Contactos** (nombre, clave pública X25519 y Ed25519, huella, «verificado en persona») guardados en la bóveda. Se intercambian por QR o por texto.
   - Para textos: un mensaje HPKE. Para archivos: HPKE envuelve una clave aleatoria y el cuerpo usa el mismo STREAM de 64 KiB (otro tipo de secreto en la cabecera).
   - Opción de firmar a la vez con la clave Ed25519 propia, para que el destinatario sepa quién lo envía.
   - Se prueba con los vectores de prueba del RFC 9180 de la suite usada.
4. **Salud de la bóveda:** contraseñas repetidas, débiles (por entropía estimada y por patrones comunes), filtradas (HIBP en lote, con k-anonimato y solo si el usuario lo pide), antiguas (más de un año), y accesos sin 2FA. Cada aviso lleva al arreglo.
5. **Revisión del móvil:** bloqueo de pantalla configurado, antigüedad del parche de seguridad, depuración USB, opciones de desarrollador, instalación desde orígenes desconocidos para esta app, señales de root (heurísticas, avisando de que no son infalibles), si esta app está firmada con la clave de prueba pública y si la biometría es de clase fuerte. Cada punto con su explicación y con un enlace a la pantalla de ajustes correspondiente.
6. **Importar desde Google Authenticator:** leer el QR de exportación `otpauth-migration://` (protobuf, que se lee sin dependencias nuevas) e importar todos los códigos. También se exportan los códigos propios como `otpauth://` en QR, uno a uno.
7. **Adjuntos en la bóveda:** archivos pequeños (hasta 5 MB cada uno y 50 MB en total).
   - Cada uno se cifra con su propia clave aleatoria y se guarda aparte de `vault.bin`.
   - Su id, su clave y su SHA-256 van dentro de la entrada, que ya está cifrada y autenticada con la DEK. Así un adjunto cambiado o intercambiado se detecta.
   - **Copias y exportación** *(decisión mía)*: la exportación y la copia automática pasan a ser un único archivo contenedor (`seguridad-backup`, versionado) que lleva el JSON de la bóveda y los adjuntos tal como están cifrados, y se escribe y se lee en streaming.
     - La integridad la da la propia bóveda: los hashes de los adjuntos están dentro de los datos autenticados.
     - Límite al importar: 100 MB.
     - Las claves propias y los contactos (fase 1.2.0) son entradas de la bóveda, así que van en la copia.
     - Se sigue importando la exportación JSON sin adjuntos (la de CriptoPanel y la de la 1.0.0).
8. **Laboratorio de hashes:** SHA-256, SHA-512, SHA3-256, BLAKE2b y BLAKE3 de texto y archivos (en streaming), HMAC-SHA256 con clave, comparación en tiempo constante con la huella esperada, y Base64 y hex.
9. **Laboratorio clásico** (didáctico, con su aviso de que no es seguro): César, Vigenère y un simulador de Enigma I/M3 con rotores I a V, reflectores B y C, anillos, tablero de conexiones y el doble paso. Se prueba con mensajes históricos conocidos. También tiene análisis de frecuencias e índice de coincidencia.
10. **Aprende:** lecciones breves sin conexión (contraseñas y gestores, 2FA, frases semilla y Shamir, cifrado simétrico y asimétrico, firmas, phishing, por qué no hay recuperación) y un glosario.

## Robustez (vale para todo)

- Escritura atómica con `fsync`. La bóveda dañada nunca se sobrescribe. Pruebas de interrupción (la escritura falla a mitad).
- Las operaciones largas (Argon2id, archivos) se hacen fuera del hilo principal, se pueden cancelar sin dejar archivos a medias y sobreviven a girar el móvil.
- Ceros en claves y textos en claro al terminar, como en el original. Contraseñas en `CharArray`.
- Límites de tamaño en todo lo que se importa, se escanea o se pega (QR, JSON, protobuf, archivos).
- `FLAG_SECURE` y protección contra superposiciones en toda la app. Sin `allowBackup` para los datos sensibles. R8 activado.
- Ni un solo registro (log) con datos.
- CI en GitHub Actions como la de CriptoPanel: acciones fijadas por SHA, permisos mínimos, secretos de firma solo en el paso de Gradle, APK publicado como *prerelease* y advertencia si se firma con la clave de prueba.

## Fases

Cada fase es una versión que compila, pasa todos los tests y se sube a `main` mediante su propia rama y PR.

1. **1.0.0: extracción.** Proyecto nuevo, tema, navegación y ajustes, el código de Seguridad portado y sin nada de CriptoPanel, los formatos nuevos más la lectura de los de CriptoPanel, el proyecto `jvm-check` y la CI. Todos los tests heredados pasan.
2. **1.1.0: bóveda mejorada.** Búsqueda, favoritos, etiquetas, historial, adjuntos, copia automática, salud de la bóveda e importación desde Google Authenticator.
3. **1.2.0: criptografía de clave pública.** Claves propias, contactos, firmas Ed25519 y HPKE.
4. **1.3.0: secretos compartidos y laboratorios.** SLIP-0039, laboratorio de hashes y laboratorio clásico con Enigma.
5. **1.4.0: revisión del móvil y Aprende.**
6. **Revisión final:** `security-review` y `code-review` (al máximo) sobre todo el código, más la skill `experto-seguridad-cripto`; se corrige todo lo confirmado y queda documentado en este diseño, como en CriptoPanel.

Además, al final de cada fase se hace una revisión de código de esa fase.

## Lo que se deja al constructor

Estructura de paquetes, nombres de clases, el detalle de las pantallas, el texto exacto de los mensajes, la elección entre ViewModel y estado local, y cómo partir las tareas dentro de cada fase.

## 1.0.0: extracción (implementada)

Rama `claude/seguridad-1.0.0`, plan `docs/superpowers/plans/2026-10-23-seguridad-1.0.0-extraccion.md`, hecha con las skills de proceso de superpowers (plan, subagentes con TDD, revisión por tarea y revisión final) y la skill `experto-seguridad-cripto` en cada fase.

### Lo que se portó de CriptoPanel

Se portó de CriptoPanel 1.10.0 (rama `claude/superpower-671dj6`) con su comportamiento y sus pruebas. La revisión final comparó con `diff` cada archivo portado con su original:

| Área | Archivos | Qué cambia frente al original |
|---|---|---|
| Criptografía | `crypto/Crypto.kt`, `FileCrypto.kt`, `Bip39.kt`, `Generator.kt`, `Totp.kt`, `Hashes.kt`, `Pwned.kt` | Parámetro de dialecto (`SG`/`CP`) en la cabecera y en las etiquetas HKDF; solo se escribe `SG1:`/`.sgenc`. |
| Bóveda | `vault/Vault.kt`, `VaultStore.kt`, `android/BiometricVault.kt` | Formato `seguridad-vault` v2 con etiquetas sacadas del propio formato del archivo; importa `criptopanel-vault` v1, v2 y sin `version`, y lo reescribe en el formato nuevo. Coste de Argon2id y tiempo de bloqueo leídos de Ajustes. |
| Red | `net/HttpClient.kt`, `DistrustedCertificates.kt`, `PwnedPasswords.kt` | Igual: un único cliente con el bloqueo de eMudhra y solo `api.pwnedpasswords.com`. |
| Pantallas | `ui/screens/*` | Nombres y textos de Seguridad; las mismas protecciones (portapapeles sensible, sin Copiar del sistema en secretos, teclado de contraseña). |

### Lo que cambia frente a CriptoPanel

| Cambio | Motivo |
|---|---|
| App independiente con 4 pestañas (Bóveda, Códigos, Herramientas, Ajustes) y aviso inicial de una vez. | Diseño de esta app. |
| `FLAG_SECURE` en toda la ventana desde `onCreate`, en lugar del contador por pantalla; `filterTouchesWhenObscured` en todos los diálogos y menús. | Restricción «en toda la app». |
| Ajustes: bloqueo (al salir, 1, 5 o 15 min), portapapeles (15, 45 o 90 s), coste de Argon2id (estándar 64 MiB/3, alto 128 MiB/4) y tema. | Antes eran fijos. |
| Escáner de QR con ZXing (`zxing-android-embedded` 4.3.0, Apache-2.0) en una actividad propia con `FLAG_SECURE`, y permiso de cámara solo al escanear. | Solo componentes libres: el escáner de Google Play Services es propietario. |
| Pantalla de Licencias con los textos completos y una lista de licencias permitidas que la CI comprueba (`:app:checkLicense`). | El código de la app es privado: solo licencias permisivas, nada copyleft. |
| Verificación de dependencias de Gradle (`verification-metadata.xml`, sha256 de cada componente). | Cadena de suministro, también con el espejo de Maven Central de Google. |
| CI con secretos `SEGURIDAD_*`, huella del certificado de firma en el resumen y aviso si se firma con la clave de prueba. | Como en CriptoPanel, con la clave propia de Seguridad. |

### Decisiones tomadas durante la implementación

| Decisión | Motivo |
|---|---|
| `AppContainer` recibe `filesDir: File` y solo crea objetos JVM. | Se prueba en `jvm-check`, sin Android. |
| `sha1Hex` solo en `crypto/Hashes.kt`; `crypto/Pwned.kt` solo tiene `pwnedCount`. | Evitar un símbolo duplicado. |
| `idleLockMs() = 0` («Al salir») significa sin bloqueo por tiempo mientras se usa la app; al salir se bloquea siempre. Con un selector o el escáner abiertos, como mucho 2 minutos (revisión final, I-2). | Es la opción «Al salir» del diseño. |
| `!app/debug.keystore` en `.gitignore`: la clave de prueba pública está en el repositorio, como en CriptoPanel. | La firma de depuración de la CI. |
| Se mantiene el espejo de Maven Central de Google, con verificación sha256 de todo. | Evita los 429 de Maven Central; las sumas fijan cada artefacto venga de donde venga. |
| La lista de palabras BIP-39 es MIT (comprobado en `bip-0039.mediawiki`). | CriptoPanel decía BSD-2-Clause, y era un error. |
| Proveedor del coste de Argon2id desde Ajustes, reglas de exclusión de copias e importar desde «Crea tu bóveda». | Robustez dentro del diseño. |
| ZXing en lugar de `play-services-code-scanner`; código propio privado («Todos los derechos reservados»); lista de licencias solo permisivas. | Decisión del usuario: todo libre, y la app privada. |
| Permisos del APK: `INTERNET`, `CAMERA`, `USE_BIOMETRIC` y `USE_FINGERPRINT`. | El escáner y la huella los necesitan; el «solo INTERNET» del plan era anterior. |
| La publicación avisa, sin fallar, si el APK sale firmado con la clave de prueba. | Como en CriptoPanel; ya hay secretos y el APK se firma con la clave propia. |
| La revisión de la tarea 7 se hizo dentro de la revisión final, con la CI real en verde como comprobación. | Ahorro de coste, a petición del usuario. |

### Revisión final (toda la rama)

Se aplicaron `code-review`, `security-review` y la lista de `experto-seguridad-cripto` a los 15 commits sobre `main`, con 96 tests en verde. El veredicto fue «Lista tras los arreglos»: ningún hallazgo Crítico, 2 Importantes y 3 menores para arreglar ya. Todo quedó corregido, con tests, sin cambiar ningún formato:

| Gravedad | Problema | Arreglo |
|---|---|---|
| Importante (I-1) | Lo cifrado con el coste «Alto» (128 MiB) no se abría en un móvil con menos de 256 MiB de memoria por app. El límite de la cabecera pedía que el coste no pasara de la mitad de la memoria del móvil que lee, así que una copia o una frase semilla cifradas en un móvil grande se quedaban sin poder abrirse en uno económico. | Al leer se acepta hasta 128 MiB salvo que no quepa: el coste no puede pasar de 3/4 de la memoria de la app. Así «Alto» se abre con 171 MiB o más, aunque ese móvil no lo ofrezca para cifrar. Los límites fijos se mantienen (128 MiB, 10 pasadas y 4 carriles) para que una cabecera manipulada no pueda pedir memoria sin tope. Si aun así se agota la memoria, Argon2id da un error claro en lugar de cerrar la app. El texto de Ajustes ya no promete que se abra igual en todas partes. |
| Importante (I-2) | Con «Al salir», salir con Inicio (o apagar la pantalla) desde el selector de archivos o el escáner de QR dejaba la bóveda abierta indefinidamente: no había bloqueo por tiempo y `onStop` no bloquea mientras está abierto lo que pidió la bóveda. | Con «Al salir» y el selector o el escáner abiertos, la bóveda se bloquea a los 2 minutos sin uso (`AWAY_BACKSTOP_MS`). La comprobación se hace también en `onStart`, antes de mostrar nada, por si el sistema congeló la app y la comprobación periódica no llegó a ejecutarse. |
| Menor (m-1) | El menú «Más opciones» de la bóveda es una ventana aparte sin `filterTouchesWhenObscured`, así que en Android 8–11 se podía hacer tapjacking sobre «Desactivar huella» o «Importar copia». | `FilterObscuredTouches()` dentro del menú. Era el único menú o ventana emergente sin él. |
| Menor (m-2) | Los formatos que se leen para siempre no estaban fijados contra el cambio de etiqueta en sentido contrario. | Tests: una `seguridad-vault` sin `format` o con `criptopanel-vault` no se abre; sin huella para `criptopanel-vault` v2 aunque se comprobara hace poco; `write` rechaza un archivo antiguo. |
| Menor (m-3) | Faltaba esta sección del diseño, y el plan seguía nombrando el escáner de Google. | Esta sección; el plan nombra `zxing-android-embedded` 4.3.0. |

Quedan para más adelante (menores, sin riesgo para los datos):

- quitar del manifiesto fusionado la `CaptureActivity` de ZXing, que no se usa;
- ~~capturar `ActivityNotFoundException` al abrir el selector de archivos~~ (hecho en 1.0.0 y ampliado en 1.0.1 con `launchOrError`);
- un mensaje propio para copias de una versión más nueva con tipos de entrada desconocidos (1.1.0);
- comprobar el resultado de `renameTo` al apartar una bóveda dañada;
- `fsync` de la carpeta tras el renombrado atómico.

En toda la rama sigue el riesgo que ya tenía CriptoPanel: lo que se escribe pasa por `String` de Compose, y un móvil con root o con malware de accesibilidad puede leer la pantalla.

## 1.0.1: fallos encontrados en el móvil (implementada)

Rama `claude/seguridad-1.0.1`, desde `main` (40f75dc). El usuario encontró estos fallos en su móvil. La causa raíz se buscó con superpowers:systematic-debugging, y el arreglo se hizo con TDD y la skill `experto-seguridad-cripto`. No cambia ningún formato.

| Fallo | Causa raíz | Arreglo |
|---|---|---|
| **A.** «📷 Escanear QR» decía «No se pudo abrir el escáner…», y con la bóveda abierta «Importar copia» cerraba la app. También fallaban exportar y los 6 selectores de las herramientas. | `androidx.biometric` 1.1.0 trae `androidx.fragment` 1.2.5, cuyo `FragmentActivity.startActivityForResult` lanza `IllegalArgumentException("Can only use lower 16 bits for requestCode")`. `ActivityResultRegistry` (`androidx.activity` 1.10.1) usa códigos ≥ 0x10000, y `MainActivity` es `FragmentActivity` por `BiometricPrompt`, así que fallaba todo `launch`. `launchOrFalse` solo capturaba `ActivityNotFoundException`. | `androidx.fragment:fragment-ktx` 1.9.1 explícito: desde la 1.3.0, `FragmentActivity` usa el registro. Arrastra lifecycle 2.10.0, savedstate 1.4.0, collection 1.6.0, annotation 1.10.0 y tracing 2.0.0, todos AndroidX y Apache-2.0. La tarea `checkFragmentVersion` (en `check` y en la CI) falla si `releaseRuntimeClasspath` vuelve a resolver una versión anterior a 1.3.0. `launchOrError` (con la lógica en `core/PickerLaunch.kt`) convierte cualquier `RuntimeException` al lanzar en «No se pudo abrir el selector de archivos.», sin cerrar la app. `verification-metadata.xml` regenerado con JDK 17 y caché vacía. |
| **B.** Al generar una contraseña en «Acceso» y cambiar a «Frase semilla», la passphrase BIP-39 ya tenía esa contraseña, y en «Frase semilla» no había botón para generar. | `VaultEntryScreen` usaba un solo estado `password` para LOGIN y para SEED, y al guardar lo usaba en los dos tipos. Una passphrase crea otra wallet: guardada por error, lleva a una wallet vacía. | Estados `loginPassword` y `seedPassphrase`, cada uno iniciado solo desde una entrada de su tipo. La elección al guardar está en `vault/EntryForm.kt` (`initialPassword` y `passwordToSave`, con tests). En SEED, «🎲 Generar una segura» y el aviso de que la passphrase crea otra wallet. |
| **C.** Después de «Cambiar contraseña maestra», la bóveda seguía abierta. | `VaultStore.changeMaster` abría la bóveda con la clave de datos nueva después de guardar. | Cuando el archivo nuevo está guardado, `changeMaster` bloquea la bóveda: borra las dos claves de datos y pone el estado `Locked`. Hay que abrirla con la contraseña nueva, y así se comprueba que se recuerda. Si algo falla, nada cambia y la bóveda sigue abierta con la clave anterior. Mensaje: «Contraseña maestra cambiada. Abre la bóveda con la nueva contraseña. Vuelve a activar la huella si la usabas.» |

**Por qué los tests no los vieron.** Los tests son de JVM (`jvm-check` y `testDebugUnitTest`) y no abren actividades, así que nunca llegan a pasar por `FragmentActivity.startActivityForResult`. Además, la versión de `fragment` venía de una dependencia transitiva que nadie había fijado. Los fallos B y C eran de comportamiento: no estaban especificados y no había ningún test que los describiera. Ahora la regresión de A la frena `checkFragmentVersion` al compilar, y B y C tienen sus tests. Lo que sigue sin poder probarse aquí (que las pantallas se abran de verdad) está en la lista de comprobación manual del README.

## 1.0.2: código abierto y endurecimiento (implementada)

El repositorio pasa a ser público con licencia **Apache-2.0** (`LICENSE`, `SECURITY.md`). La 1.0.2 recoge la revisión estática hecha en `main` al publicarlo (`docs/AUDITORIA_SEGURIDAD_2026-10.md`) y la revisión de esa revisión. No cambia ningún formato.

| Cambio | Dónde |
|---|---|
| `argon2id()` valida en su propia entrada el salt (16 bytes), la longitud de salida (4–1024) y `KdfParams.checkSane()`. | `crypto/Crypto.kt` |
| Un QR `otpauth://` con escapes `%` mal formados devuelve `null` en vez de lanzar. | `crypto/Totp.kt` |
| La respuesta de HIBP se limita a 2 MiB. | `net/PwnedPasswords.kt` |
| Las contraseñas de las herramientas se vacían del formulario al usarlas, y el texto en claro solo cuando se ha cifrado bien (si el cifrado falla no se pierde lo escrito). | `ui/screens/SecurityToolsScreen.kt` |
| Los secretos de firma solo llegan a push a `main`, a etiquetas `v*` y a ejecuciones manuales desde `main`. Un push a `main` o a una etiqueta sin clave de producción falla después de pasar tests y lint. | `.github/workflows/build-apk.yml` |

**Decisión revisada: las raíces de eMudhra siguen bloqueadas.** La revisión de `main` había quitado del bloqueo las tres raíces emSign G3 y sus nombres, por riesgo de «falsos positivos». Se restaura el bloqueo de la 1.0.0: son las raíces de las intermedias comprometidas, Mozilla y Chrome las retiraron, y la app solo se conecta a `api.pwnedpasswords.com`, que no tiene certificados de eMudhra; un falso positivo no puede ocurrir y quitar el bloqueo solo deja pasar más cadenas forjables en un Android sin actualizar.

**Historial público.** Gitleaks 8.24.3 sobre todo el historial (`git --all`, 57 commits): 4 coincidencias, todas falsos positivos (el vector del RFC 6238 en `VaultTotpTest.kt` y las claves ya cifradas `wrappedKey` de los fixtures de CriptoPanel). Los mensajes de commit llevan enlaces a sesiones de Claude Code y los autores, correos; reescribir el historial es decisión del propietario y no se ha hecho.

## 1.1.0: bóveda mejorada (implementada)

Rama `sg-1.1`, desde la 1.0.2. Plan: `docs/superpowers/plans/2026-10-24-seguridad-1.1.0-boveda.md`. Cada tarea se hizo con TDD, la skill `experto-seguridad-cripto` y una revisión independiente. No cambia ningún formato existente: `SG1:`, `.sgenc`, `seguridad-vault` v2, `CP1:`, `.cpenc`, `criptopanel-vault` y las exportaciones JSON de la 1.0.x se siguen leyendo. Los campos nuevos de `VaultEntry` llevan valor por defecto (`favorite = false`, `tags = []`, `passwordHistory = []`, `attachments = []`) y `encodeDefaults` sigue en false, así que la JSON antigua se decodifica igual y una entrada sin adjuntos no escribe la clave.

| Función | Qué hace | Dónde |
|---|---|---|
| Búsqueda, favoritos, etiquetas | Busca por título, usuario, URL y etiquetas, sin distinguir mayúsculas ni tildes. **Nunca** mira contraseñas, frases, notas ni secretos TOTP. Favoritos primero, y después por título (`Collator` es-ES). Hasta 10 etiquetas de 24 caracteres. | `vault/EntryQuery.kt` |
| Historial de contraseñas | Al cambiar la contraseña de un acceso (LOGIN) se guarda la anterior, hasta 5, con su fecha. Se ve plegado en el detalle. | `vault/History.kt` |
| Salud de la bóveda | Contraseñas repetidas, débiles (menos de 50 bits estimados), con más de 365 días y sin 2FA. «Comprobar filtraciones» consulta HIBP solo cuando el usuario lo pulsa, una contraseña a la vez y con k-anonimato; los resultados quedan en memoria y no se guardan. | `health/Health.kt` |
| Google Authenticator | Importa `otpauth-migration://` (escaneado o pegado) y exporta una entrada TOTP como QR. Lector protobuf mínimo con límites (64 KB por URI, 100 parámetros, 1 KB por campo). Salta HOTP, MD5 y claves de menos de 10 bytes y dice por qué; si hay varios lotes, pide escanear el siguiente. | `crypto/OtpMigration.kt`, `crypto/Totp.kt` |
| Adjuntos cifrados | Hasta 5 MB por adjunto y 50 MB en total. | `vault/Attachments.kt` |
| Copia `.sgbak` | Exportar e importar la bóveda con sus adjuntos. | `vault/Backup.kt` |
| Copia automática | Copia cifrada a una carpeta elegida por el usuario, con rotación. | `vault/AutoBackup.kt`, `android/SafBackupTarget.kt` |

### Formato de adjunto v1 (no podrá cambiar)

Un archivo por adjunto, `files/vault/att/<id>.bin`:

| Bytes | Contenido |
|---|---|
| 0–11 | nonce de 12 bytes aleatorios (CSPRNG) |
| 12–… | AES-256-GCM del contenido; el texto cifrado lleva la etiqueta de 16 bytes al final |

- Tamaño del archivo: 12 + n + 16 bytes, con n el tamaño en claro.
- **Clave:** 32 bytes aleatorios por adjunto. Cada clave cifra un solo mensaje, así que el nonce nunca se repite con la misma clave.
- **AAD:** los bytes UTF-8 de `seguridad-attachment-v1`, un byte `0x00` y el `id` en texto (32 caracteres hex). Un archivo no puede moverse a otro id.
- **`id`:** 16 bytes aleatorios en hexadecimal minúsculo (`[0-9a-f]{32}`). Es el único nombre que se usa para el archivo, así que no hay rutas controladas desde fuera.
- **La referencia** (`AttachmentRef`) vive dentro de la entrada, que cifra y autentica la clave de datos de la bóveda: `id`, `name` (pasado por `safeFileName`), `size` (en claro), `sha256` (hex del **archivo cifrado**) y `key` (Base64 de la clave del adjunto).
- **Al abrir:** se valida el formato del id y del `sha256` y que `size` esté entre 0 y 5 MB; se lee el archivo con tope; se compara su SHA-256 en tiempo constante; se descifra con GCM; y se comprueba que el tamaño en claro es `size`. Cualquier fallo da el mismo mensaje, «El adjunto está dañado o se ha cambiado», y la clave se pone a cero.
- **Recolección:** al guardar y al desbloquear se borran los `<id>.bin` (y los `.bin.tmp` de un cierre brusco) cuyo id no está en ninguna entrada. Solo se tocan nombres que encajan exactamente con `[0-9a-f]{32}\.bin(\.tmp)?`, nunca se borra el archivo de una entrada que existe, y un fallo aquí no afecta a la bóveda.

### Formato `seguridad-backup` v1 (`.sgbak`, no podrá cambiar)

Un ZIP (`java.util.zip`) con estas entradas, **en este orden**:

| # | Nombre | Contenido |
|---|---|---|
| 1 | `seguridad-backup` | el texto ASCII `1\n` (2 bytes) |
| 2 | `vault.json` | el `VaultFile` tal como está en disco (UTF-8, sigue cifrado) |
| 3… | `att/<id>.bin` | un adjunto por entrada, con los bytes tal como están cifrados, con los ids en orden ascendente |

- Los adjuntos se guardan sin comprimir (`Deflater.NO_COMPRESSION`): los bytes cifrados no se comprimen.
- **Reconocerlo:** `isBackup` mira los primeros 64 bytes: la firma `PK\3\4` y que el nombre de la primera entrada sea exactamente `seguridad-backup`. Así se distingue de una exportación JSON y de otro ZIP.
- **Leer:** la primera entrada tiene que ser `seguridad-backup` con exactamente `1\n`. Si el contenido es un número mayor que 1, el error es «La copia es de una versión más nueva de Seguridad»; en otro caso, «dañada». Se rechazan los duplicados del marcador, de `vault.json` y de un mismo `att/<id>.bin`. Los nombres válidos se comprueban con una regex exacta (`att/([0-9a-f]{32})\.bin`) y el archivo temporal se nombra solo con esos 32 dígitos, nunca con el nombre de la entrada: no hay zip-slip. Cualquier otro nombre se ignora, pero sus bytes cuentan para los límites.
- **Límites**, sobre los bytes realmente leídos y no sobre los tamaños de la cabecera: 100 MB en total, 6 MB por entrada y 2 000 entradas. Los adjuntos van a una carpeta temporal en streaming, nunca todo en memoria.
- **Escribir:** se niega a escribir una copia que el lector rechazaría (`vault.json` de más de 6 MB, más de 2 000 entradas, una entrada o el total por encima del límite). Con la bóveda abierta comprueba el SHA-256 de cada adjunto mientras lo escribe: si falta o no coincide, no acaba la copia y dice cuál es. Con la bóveda bloqueada no escribe nada (`exportBackup` devuelve false): nunca sale una copia sin comprobar (revisión final, I1).
- **Importar:** se lee a una carpeta `import-<16hex>` junto a `att/` (el mismo sistema de archivos, así mover es un rename atómico) **antes** de tomar el cerrojo de la bóveda. Después, con el cerrojo: se abre con la contraseña de la copia; se comprueba cada adjunto referenciado (id, presencia y SHA-256); y solo entonces se mueven los archivos y se escribe `vault.bin` de forma atómica. Si algo falla, se deshace todo y la bóveda local no se toca. Un adjunto manipulado o que falta hace fallar la importación entera y nombra el adjunto. La carpeta temporal se borra siempre.
- **Tamaño máximo real:** 50 MB de adjuntos más 28 bytes por adjunto, que cabe en los 100 MB del formato.

### Copia automática

- Observa `VaultStore.revision`, un contador que sube **solo después de guardar bien un cambio del usuario** (no con los guardados de contabilidad, ni al exportar, ni si el guardado falla). Con 3 s de espera (`debounce`), 3 cambios seguidos dan una copia. Arrancar no hace copia.
- **Solo con la bóveda abierta**, para que `exportBackup` compruebe el SHA-256 de cada adjunto. Si toca una copia con la bóveda bloqueada (salir de la app en los 3 s, o cambiar la contraseña maestra, que la bloquea), queda pendiente y se hace nada más desbloquearla. Para que eso sobreviva a que el sistema cierre la app, al desbloquear también se hace una copia si `vault.bin` se modificó después de la última copia (`lastAutoBackupAt`) o aún no hay ninguna (CR-1). Los guardados de contabilidad (la última comprobación de la maestra, la subida de formato) devuelven a `vault.bin` su fecha de modificación anterior, así que abrir la bóveda no cuenta como cambio. «Hacer una copia ahora» con la bóveda bloqueada pide abrirla. Un adjunto que falta o está dañado hace fallar la copia con el aviso de Ajustes, sin dejar archivo y **sin rotar**: las copias buenas anteriores se conservan.
- Nombre: `seguridad-auto-AAAAMMDD-HHMMSS.sgbak`. **Primero se escribe `….sgbak.tmp` y solo se renombra al terminar bien**; si falla o se cancela, se borra el temporal. Nunca queda una copia a medias con el nombre definitivo. Si el proveedor no sabe renombrar (el documento no tiene `FLAG_SUPPORTS_RENAME`, o `renameDocument` falla), se borra el temporal y la copia se escribe directamente con su nombre definitivo, y se borra si la exportación falla (CR-3); solo un cierre brusco a mitad podría dejarla cortada en ese proveedor. El temporal se escribe, renombra y borra por la URI que devolvió `createDocument`, nunca buscándolo por nombre. Si ese nombre (o su `.tmp`) ya existe en la carpeta (reloj atrasado), se usa el siguiente segundo libre: nunca se borra una copia completa para hacer sitio.
- Una `CancellationException` que lanza la carpeta se avisa como error genérico y las copias siguen; solo la cancelación del propio ámbito las detiene.
- **Rotación:** conserva las 3, 5 (por defecto) o 10 más recientes y solo borra nombres que encajan **exactamente** con `seguridad-auto-\d{8}-\d{6}\.sgbak` (y sus `.tmp` sueltos). Nunca borra la copia recién escrita ni archivos ajenos de la carpeta.
- **Errores:** nunca cierran la app ni tocan la bóveda local. Se guardan en los ajustes como uno de seis mensajes fijos en español, sin ruta, URI, nombre de adjunto ni texto de la excepción, y se muestran en Ajustes: sin permiso, carpeta no encontrada, adjunto dañado o bóveda que no cabe, error de escritura, error genérico y «se hizo, pero no se pudieron borrar las antiguas».
- La carpeta se elige con el selector del sistema (`OpenDocumentTree`) y el permiso persistente se devuelve al cambiar de carpeta y al desactivar. Desactivar no borra las copias ya hechas.
- **Crear una bóveda nueva desactiva la copia automática** (CR-2): la rotación no sabe de qué bóveda es cada copia y, tras apartar una `vault.bin` dañada, habría borrado las únicas copias buenas de la anterior. `VaultStore.create` llama antes de escribir nada a `beforeCreate`, que borra la carpeta de los ajustes, devuelve su permiso y deja `autoBackupOffForNewVault`; Ajustes muestra «La copia automática se ha desactivado al crear una bóveda nueva, para no borrar las copias de la anterior. Elige la carpeta otra vez para activarla.» hasta que se elige carpeta. Importar una copia no la desactiva.
- El resultado de una copia lleva la carpeta a la que fue y la hora en que se tomó: solo se guarda si esa carpeta sigue elegida (CR-5), y una copia escrita cuya rotación falla también actualiza la fecha de la última copia (CR-4).
- Las copias quedan fuera del sandbox de la app. Su confidencialidad depende de la contraseña maestra (ataque de diccionario offline contra Argon2id), igual que con la exportación manual, y las copias antiguas siguen abriéndose con la contraseña maestra de cuando se hicieron.

### Decisiones tomadas durante la implementación

| Decisión | Motivo y coste |
|---|---|
| Una exportación JSON cuyas entradas llevan adjuntos se rechaza con «Esta copia tiene adjuntos: importa el archivo .sgbak». | Ninguna versión publicada escribe ese JSON (la 1.0.x no tiene adjuntos y la 1.1 exporta `.sgbak`), y aceptarlo dejaría referencias sin archivo. Si alguien tiene un JSON de una compilación de desarrollo, tiene que volver a exportar. |
| La copia automática escribe a un nombre temporal y solo lo renombra si la exportación termina bien. | Evita una copia parcial con nombre definitivo en la carpeta del usuario. `BackupTarget.create` devuelve el archivo creado (`BackupFile`, con `write`, `rename` y `delete`), que se usa por su URI y no por su nombre. |
| `upsert` conserva los adjuntos guardados de una entrada, como ya hacía con el historial. | El editor construye la entrada sin `attachments`; sin esto, al guardar se perderían las referencias y la recolección borraría los archivos. |
| Al apartar una bóveda dañada se aparta también `att/` a `att.bad-<hora>`. | La recolección de la bóveda nueva no debe borrar archivos que solo podría abrir la apartada. |
| Las rutas de `android/` quedan fuera de `jvm-check`. | `SafBackupTarget` usa `DocumentsContract`; la lógica (`AutoBackup`, `BackupTarget`) es JVM pura y se prueba con un destino en memoria. |
| La lectura del documento de entrada de un adjunto se hace antes de tomar el cerrojo de la bóveda (corrección tras la revisión de la tarea 5), y `exportBackup` escribe fuera del cerrojo, con una instantánea. | Un proveedor de documentos lento (Drive sin conexión, SMB) no puede bloquear la bóveda. |
| `writeBackup` gana un parámetro opcional `expectedSha256`; `AutoBackup` gana `zone`; `Settings` gana `lastAutoBackupAt`. | Un cambio mínimo cada uno, sobre el plan: no se acaba una copia que no podría restaurarse, los tests dan nombres deterministas y la pantalla muestra la fecha de la última copia. |
| `create` de `BackupTarget` y `write` de `BackupFile` (y su `writer`) son `suspend`. | `exportBackup` es `suspend`; así se evita `runBlocking`. |
| La copia automática solo se hace con la bóveda abierta; con ella bloqueada queda pendiente hasta el siguiente desbloqueo, y `exportBackup` no escribe nada si está bloqueada (revisión final, I1). | Una copia hecha bloqueada no podía comprobar los adjuntos: con uno dañado, la rotación habría sustituido todas las copias buenas por copias que no se pueden importar. Los cambios ya están guardados en el móvil, así que esperar al desbloqueo no pierde nada. La importación sigue siendo todo o nada (no hay modo «importar sin los adjuntos dañados»). |

**Diferencias entre el plan y el código** (el código manda): `BackupTarget` tiene `create`, que devuelve un `BackupFile` con `write`, `rename` y `delete`, en vez de `write`; los parámetros y campos de la fila anterior; y `VAULT_IMPORT_MAX_BYTES` (20 MB para la JSON) pasó de la interfaz a `VaultStore`.

### Revisión

Cada tarea de la 5 a la 7 pasó una revisión de especificación y de código con la skill `experto-seguridad-cripto`, y un arreglo en la 5 (lectura del flujo bajo el cerrojo de la bóveda) se re-revisó sin hallazgos nuevos.

La revisión final de toda la rama, con esta skill, está hecha: sin críticos y con un Important (I1, copias automáticas sin comprobar con la bóveda bloqueada). Se corrigió en una ronda de arreglos, junto con:
- T7-1 y T7-2: el temporal por su URI y un nombre libre en vez de borrar la copia con el mismo nombre;
- T7-4: una `CancellationException` suelta ya no para las copias;
- T7-7: el test engañoso, y T7-8: el texto de Ajustes («se hará en unos segundos y tras cada cambio»);
- T6-3: test de la limpieza de `import-<16hex>` al arrancar, y T6-5: `application/x-zip-compressed` en el selector de importación;
- la comprobación de HIBP vigila la bóveda desde su propia corrutina y se para en cuanto se bloquea, también al salir de la app, cuando la pantalla ya no se recompone; no envía más consultas y descarta sus resultados. El primer arreglo solo la paraba al recomponer, y lo corrigió la re-revisión;
- Ajustes ya no promete una copia «en unos segundos» cuando no hay bóveda.

Quedan `code-review` y `security-review` sobre la rama antes de fusionar.

#### `code-review` de la rama: hallazgos y decisiones

| Hallazgo | Decisión |
|---|---|
| CR-1: una copia pendiente con la bóveda bloqueada solo vivía en memoria y se perdía si el sistema cerraba la app. | Arreglado: al desbloquear se copia si `vault.bin` es más reciente que `lastAutoBackupAt` o no hay copia; los guardados de contabilidad conservan la fecha de modificación. Solo al pasar a abierta, no con cada cambio guardado (esos siguen esperando los 3 s). |
| CR-2: la rotación no sabe de qué bóveda es cada copia; tras apartar una bóveda dañada y crear otra, borraría las copias buenas de la anterior. | Arreglado: crear una bóveda desactiva la copia automática, devuelve el permiso de la carpeta y Ajustes lo explica. |
| CR-3: sin alternativa si el proveedor no sabe renombrar, y el error decía «¿Queda espacio?». | Arreglado: se mira `FLAG_SUPPORTS_RENAME` y, si no se puede renombrar o el renombrado falla, la copia se escribe con su nombre definitivo y se borra si falla. Un fallo de renombrado ya no llega al usuario como error de espacio. |
| CR-4: copia escrita y rotación fallida no actualizaban la fecha de la última copia. | Arreglado: se guarda la fecha y el aviso «no se pudieron borrar las antiguas», también si falla el listado de la rotación. |
| CR-5: el resultado de una copia se guardaba aunque entretanto se hubiera elegido otra carpeta. | Arreglado: `AutoBackupResult` lleva la carpeta y solo se guarda si sigue siendo la elegida. |
| CR-6: «más de 365 días» se calcula desde `updatedAt` cuando no hay historial. | Aparcado: cualquier edición reinicia el plazo; es un aviso orientativo, sin riesgo para los datos. |
| CR-7: importar el último lote de Google Authenticator desde el editor de una entrada existente salía del editor y perdía sus cambios sin guardar. | Arreglado: editando una entrada existente se queda en el editor con un aviso; con una entrada nueva sigue volviendo a la lista. |
| CR-8: una exportación puede cruzarse con `removeAttachment` y fallar por un adjunto recién quitado. | Aparcado: la copia falla con su aviso y la siguiente sale bien; nunca se escribe una copia incorrecta. |
| CR-9: `vaultHealth` se recalculaba en cada recomposición. | Arreglado: `remember(entries, breached, día)`. |
| CR-10: el tope de 50 MB de adjuntos se comprobaba después de cifrar y escribir el archivo. | Arreglado: se comprueba antes, con el cerrojo tomado (no hay carrera que vigilar después). |

Menores aceptados y diferidos:

| Menor | Efecto |
|---|---|
| `removeAttachment` con un id que no existe muestra «La bóveda se bloqueó». | Solo un mensaje equivocado en un caso que la interfaz no produce. |
| Al restaurar una bóveda dañada, sus adjuntos quedan en `att.bad-*`. | Quedan en disco apartados; se pueden borrar a mano. |
| El tope de 6 MB por entrada se aplica a los bytes descomprimidos, no a los comprimidos. | Un ZIP muy comprimido tarda más en rechazarse, pero la memoria y el disco siguen acotados. |
| En Android 14 o posterior, `ZipInputStream` lanza `ZipException` con nombres con `..` o `/` inicial. | Una copia con esos nombres se **rechaza** como dañada en el móvil y se **ignora** en la JVM de los tests; ambas formas son seguras. |
| Una copia fechada en el futuro (reloj mal puesto una vez) ocupa para siempre un hueco de la rotación. | Se conservan `keep - 1` copias útiles en vez de `keep`. |

Riesgos que quedan: la clave de cada adjunto vive como `String` Base64 en la entrada mientras la bóveda está abierta y no puede ponerse a cero en Java, igual que las contraseñas; el contenido de un adjunto se descifra en memoria (máximo 5 MB) y el búfer sí se pone a cero. Las comprobaciones que dependen de un móvil real están en la lista de abajo y en el README.

## 1.1.1: lista para Google Play (implementada)

Recoge el dictamen jurídico del 26 de octubre de 2026 (hecho sobre la 1.0.1, `980dd65`), contrastado con el código de la 1.1.0. No cambia ningún formato ni ninguna función.

| Punto del dictamen | Cambio |
|---|---|
| `applicationId` bajo un espacio propio, irreversible en Play | `io.github.egt17baa.seguridad` (decisión del propietario). El `namespace` de Kotlin sigue siendo `app.seguridad`: no cambia ningún archivo de código. Para Android es otra app: se pasa de la 1.1.0 exportando e importando la `.sgbak` (README y `docs/PUBLICAR.md`). |
| Política de privacidad pública y enlazada en la app (bloqueante) | `docs/privacidad.html`, publicada con GitHub Pages (`docs/.nojekyll`: se sirve tal cual, sin Jekyll), y botón en *Ajustes → Acerca de*. Se abre en el navegador: la app no hace ninguna petición. El borrador del dictamen se amplía con lo que añadió la 1.1: «Comprobar filtraciones» (una consulta por contraseña), la copia automática a una carpeta que puede estar en la nube y la cámara para Google Authenticator. |
| Seguridad de los datos coherente con HIBP | `docs/PUBLICAR.md`: tratamiento efímero, iniciado por el usuario y cifrado en tránsito. |
| Exportaciones: el fundamento es el código abierto | `docs/PUBLICAR.md` corregido (ya no se apoya en el «mercado masivo»). |
| `NOTICE` con copyright propio; `NOTICE` de dependencias | `NOTICE` con «Copyright 2026 Egt17baa» (seudónimo, decisión mía a falta de nombre). Ninguna biblioteca del APK trae `META-INF/NOTICE` (comprobado en la caché de Gradle sobre `releaseRuntimeClasspath`; los que hay son de herramientas de compilación). |
| EFF con CC BY 3.0 US | Pantalla de licencias, generador y `assets/licenses/cc-by-3.0.txt`, con el enlace `…/by/3.0/us/`. |
| Compromiso de no monetizar | README. |
| APK solo en versiones etiquetadas | Se quita el trabajo `publish` (`apk-latest` en cada push a `main`); queda `release-apk`, que adjunta el APK a la release de cada etiqueta. |
| Ficha sin superlativos, sin «wallet» y con los avisos | Borrador en `docs/ficha-google-play.md`. |

**Además (revisión del código):** el cliente HTTP enviaba `User-Agent: Seguridad/1.0.0` fijo; ahora es `Seguridad/<versionName>` (`secureClient(appVersion)`, pasado desde `SeguridadApp` con `BuildConfig.VERSION_NAME`; test en `PwnedPasswordsTest`). La pantalla «Acerca de» decía que solo «¿Filtrada?» usa la red; ahora nombra también «Comprobar filtraciones».

**Lo que quedaba para el propietario** (el nombre definitivo, el correo de la ficha, GitHub Pages, *Private vulnerability reporting*, los formularios de Play Console, la verificación de desarrolladores y borrar `apk-latest`) está hecho según el propietario (26 de octubre de 2026); lo que se ve desde el repositorio está comprobado: etiqueta `v1.1.1` con su APK de producción, *Private vulnerability reporting* activo y `apk-latest` borrada. El historial se reescribió ese día: los correos de autor pasan a la dirección `noreply` de GitHub y se quitan los enlaces a sesiones de los mensajes, sin cambiar ningún árbol; las etiquetas se movieron a los commits nuevos.

### Después de la 1.1.1: la Public Suffix List fuera del APK

Revisión jurídica del 26 de octubre de 2026. OkHttp 5 mete en el APK `assets/PublicSuffixDatabase.list` (130 KB), una versión compilada de la Public Suffix List de la Mozilla Foundation, con licencia **MPL-2.0** y sin su aviso. La MPL-2.0 es compatible con distribuir la app como Apache-2.0 (§3.3, obra mayor), pero pide avisar de la licencia y de dónde está la fuente (§3.1 y §3.2), y es copyleft, que la regla del proyecto no admite. Riesgo bajo: §5.1 devuelve los derechos al corregirlo, y la protección de una lista de datos es dudosa (TJUE C-604/10; art. 11 de la Directiva 96/9/CE).

| Hecho | Cambio |
|---|---|
| OkHttp solo lee la lista para las cookies (`receiveHeaders`, que sale antes con `CookieJar.NO_COOKIES`) y en `HttpUrl.topPrivateDomain()`, comprobado en las fuentes de 5.3.2. La app no usa ninguna de las dos. | `androidResources.ignoreAssetsPatterns` la deja fuera del APK, conservando los patrones por defecto de AGP (que dejan de aplicarse en cuanto la lista no está vacía). `HttpClientTest` fija que el cliente va sin cookies ni caché. |
| `checkLicense` mira la licencia de cada biblioteca, no la de los datos que trae. | Tarea `checkAssets` (en `check` y en la CI): todo lo que va en `assets/` del APK release tiene que venir de `app/src/main/assets`. Un asset nuevo de una biblioteca hace fallar la compilación hasta revisar su licencia. |
| El APK de la release `v1.1.1` ya publicado sigue llevándola. | Nota en esa release (la pone el propietario): «Incluye la Public Suffix List (Mozilla Foundation, MPL-2.0) a través de OkHttp. Fuente: https://github.com/publicsuffix/list». |

El analizador de enlaces previsto necesita esa lista. Jurídicamente puede usarse con su aviso (el copyleft no pasa al código de la app), pero eso exige una excepción a la regla del proyecto para archivos de datos MPL-2.0 sin modificar: se decide al diseñar esa versión.

## Comprobación manual en el móvil (no se puede hacer aquí)

Activar la huella y desbloquear con ella; bloquear al salir; escanear un QR de 2FA y de Google Authenticator; comparar los códigos con otra app; la revisión del móvil en uno real; el aspecto en claro y en oscuro; importar una copia real de CriptoPanel. Desde la 1.1.0, además: el QR de Google Authenticator (también en varios lotes), los adjuntos, la copia automática a una carpeta y revocar después el permiso, la salud con filtraciones, un proveedor de documentos que no sepa renombrar, Drive con nombres repetidos y la lectura de nombres de ZIP en Android 14 o posterior. La lista completa está en el README.
