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

## Comprobación manual en el móvil (no se puede hacer aquí)

Activar la huella y desbloquear con ella; bloquear al salir; escanear un QR de 2FA y de Google Authenticator; comparar los códigos con otra app; la revisión del móvil en uno real; el aspecto en claro y en oscuro; importar una copia real de CriptoPanel.
