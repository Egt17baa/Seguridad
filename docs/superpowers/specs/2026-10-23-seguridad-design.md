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
- capturar `ActivityNotFoundException` al abrir el selector de archivos;
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

## Comprobación manual en el móvil (no se puede hacer aquí)

Activar la huella y desbloquear con ella; bloquear al salir; escanear un QR de 2FA y de Google Authenticator; comparar los códigos con otra app; la revisión del móvil en uno real; el aspecto en claro y en oscuro; importar una copia real de CriptoPanel.
