# Auditoría de seguridad: app Android «Seguridad» 1.2.0

**Repositorio:** `egt17baa/seguridad`, commit `5d6c318` (27-10-2026, «Seguridad 1.2.0: criptografía de clave pública»), árbol limpio.
**Método:** skill `security-audit` de Cloudflare, perfil `standard`, auditoría completa del repositorio. Las severidades siguen además el criterio de la skill del proyecto `experto-seguridad-cripto`.
**Fecha:** 27-10-2026.

## 1. Alcance, método y límites

- **Qué se ha revisado:** todo `app/src/main` (unos 12.000 líneas de Kotlin), el manifiesto y `res/xml`, la configuración de Gradle, `gradle/verification-metadata.xml`, la clave de prueba `app/debug.keystore`, `.github/workflows/build-apk.yml` y los tests como referencia.
- **Cómo:** 9 agentes de búsqueda por áreas: cifrado simétrico; bóveda y ajustes; copias e importación; clave pública (recepción y envío); superficie Android; teclado y autorrelleno; red y cadena de suministro; una pasada libre; y cadenas entre funciones. Además hubo 3 revisores de cobertura y 4 verificadores independientes, 2 por cada hallazgo. El plan de cobertura tiene 25 unidades (`coverage-ledger.json`).
- **Cobertura:** las 25 unidades están cerradas: 23 sin hallazgos y 2 con un hallazgo cada una. Ninguna quedó aplazada ni fuera de alcance. El primer revisor de cobertura añadió 2 unidades (teclado y autorrelleno; acciones de clave pública salientes). Los otros dos revisores, el posterior y el final, no encontraron más huecos.
- **Ejecución:** **solo revisión de código.** En este entorno no hay Android SDK, ni acceso a Maven, ni `kotlinc`, ni un sandbox del sistema que se pueda garantizar. Por eso no se compiló ni se ejecutó nada del proyecto. Lo único que se inspeccionó fueron datos no ejecutables, como el certificado de `debug.keystore` con `keytool`. Consecuencia: ningún hallazgo puede pasar a «confirmado» con la regla de la skill, que exige un resultado observado. Los que dependen del comportamiento en tiempo de ejecución quedan **por validar**, con un plan exacto para comprobarlos.
- **Ejecuciones anteriores:** no hay ninguna en el formato de esta skill. `docs/AUDITORIA_SEGURIDAD_2026-10.md` (25-10-2026, anterior a la 1.2.0) se usó solo como contexto.
- **Esto no es una auditoría criptográfica formal ni un pentest en dispositivo.**

## 2. Valoración general

La base es sólida. No hay **ningún hallazgo confirmado**. En las partes con más riesgo no apareció ningún defecto explotable: formatos y cifrado (AEAD con cabecera como AAD, nonces aleatorios, límites de Argon2id), bóveda (DEK envuelta y AAD ligada a versión y campos), importación `.sgbak` (sin *zip slip* y con límites de descompresión), clave pública (firmas con separación de dominio que ligan al destinatario, contactos verificados solo por QR o confirmación explícita), huella (clave Keystore con `CryptoObject`) y CI (secretos solo en `main` y etiquetas, acciones fijadas por SHA).

Hay **2 asuntos por validar**, ambos de impacto limitado y con un arreglo pequeño, y una lista de mejoras. Las prioritarias son dos carreras en el bloqueo de la bóveda y la validación de lo restaurado.

## 3. Hallazgos confirmados

Ninguno.

## 4. Por validar (sin severidad: la ruta en el código es real, pero falta un hecho del dispositivo)

| # | Asunto | Dónde | Qué falta comprobar | Arreglo propuesto |
|---|---|---|---|---|
| NV-1 | Los campos de contraseña maestra y de secretos no se excluyen del **autorrelleno de Android** | `ui/theme/Components.kt:106-112`, `MainActivity.kt:26`, `AndroidManifest.xml:19`; Compose UI 1.9.0 (`gradle/verification-metadata.xml:481`) | Si Compose 1.9.0 envía estos campos (sin `ContentType`) al servicio de autorrelleno activo y si este llega a ofrecer «¿Guardar contraseña?». Si el usuario acepta, la maestra saldría a otra app, posiblemente sincronizada en la nube. | Una línea en `MainActivity.onCreate`, antes de `setContent` (y lo mismo en `SecureCaptureActivity`): `window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` |
| NV-2 | **Otra app puede hacer que la rotación de copias automáticas borre las copias buenas** | `vault/AutoBackup.kt:242-247`, `android/SafBackupTarget.kt:63,75` | Si una app sin permisos puede crear archivos en la carpeta elegida (p. ej. `Documents/…` o `Download/…` vía MediaStore en Android 10+). Si puede, le basta con dejar `seguridad-auto-99991231-235959.sgbak`. La rotación, que se fía del nombre, lo trata como la copia más nueva y borra con el permiso de Seguridad todas las copias genuinas salvo la última. | Guardar en ajustes los `documentId` que devuelve `createDocument`/`renameDocument` y rotar y ordenar solo entre esos documentos, no por nombre. |

El detalle, la traza y el plan de validación de cada uno están en `2026-10-27-auditoria-externa-1.2.0-por-validar.md`.

## 5. Mejoras recomendadas (no son vulnerabilidades)

Están ordenadas por prioridad. Ninguna cruza una frontera de confianza con la evidencia disponible, pero las primeras tocan garantías que la app promete.

**Prioridad alta (arreglos pequeños en lógica sensible)**

1. **Carrera en el desbloqueo con huella.** `VaultStore.unlockWithKey` (`vault/VaultStore.kt:251-266`) lee `epoch` después de tomar el mutex. Las demás vías lo capturan antes. Si sales de la app justo mientras espera el mutex, el `lock()` de `onStop` no se ve y la bóveda queda abierta en segundo plano. Con «Al salir» ya no se vuelve a bloquear hasta el siguiente `onStop`. Arreglo: `val since = epoch` antes de `mutex.withLock`, y pasarlo a `opened()`.
2. **Carrera en la importación.** `VaultBackup.kt:130` ejecuta `importReplace` en `Dispatchers.IO`. Mientras, `lock()` corre en el hilo principal sin sincronizarse con la comprobación `epoch != since` ni con la publicación del estado (`VaultStore.kt:195-212`). Arreglo: hacer esa comprobación y la publicación bajo el mismo lock que `lock()`, o en `Dispatchers.Main`.
3. **Guardar un contacto como verificado por error.** En `ContactAddScreen.kt:89-99,147-165,216`, si pegas una tarjeta y enseguida escaneas otra distinta por QR, durante el análisis de la nueva el botón de guardar todavía guarda la **pegada**, pero marcada como verificada. La ventana es muy corta y exige pulsar en ella, pero afecta al estado de confianza. Arreglo: poner `value = null` al empezar `produceState`, o guardar solo si `ready.text == text`.
4. **Lo restaurado no se revalida como lo nuevo.** `VaultStore.replace` (`vault/VaultStore.kt:557-601`) no comprueba:
   - que los id de entrada sean únicos;
   - el tope total de 50 MB de adjuntos;
   - la normalización que aplica `upsert` (identidades y contactos sin adjuntos ni historial, `safeFileName` en los nombres de adjunto);
   - que la bóveda esté desbloqueada o no exista. Hoy esto solo lo garantiza la interfaz.

   Arreglo: aplicar en `replace()` los mismos invariantes que en `upsert` y `addAttachment`, y añadir `check(vault == null || _state.value is VaultState.Unlocked)`.

**Prioridad media**

5. **Copias automáticas tras importar o borrar la bóveda.** `create()` las desactiva (`AppContainer.kt:48-50`), pero `destroy()` e `importReplace` no. Tras importar otra bóveda, la rotación acaba borrando las copias de la anterior. El diseño lo hace a propósito (spec, línea 311), así que basta con avisarlo en el diálogo de importar o aplicar ahí el mismo `beforeCreate`.
6. **Cambiar la maestra no invalida lo anterior.** Las `.sgbak` antiguas siguen abriéndose con la contraseña vieja, y la semilla de la identidad no cambia (`Vault.kt:355-364`). Una copia antigua más una maestra antigua filtrada da la identidad actual. Conviene ofrecer, al cambiar la maestra, purgar o regenerar las copias automáticas y rotar la identidad, o al menos decirlo en ese diálogo.
7. **Teclado.** Compose no permite `IME_FLAG_NO_PERSONALIZED_LEARNING`. Los campos de secretos dependen de `KeyboardType.Password`, y los de metadatos (usuario, URL, título, emisor TOTP, etiquetas, búsqueda) usan el teclado normal, que puede aprender de ellos (`VaultEntryScreen.kt:276-282,321-322,344`). Los comentarios de `VaultScreen.kt:364` y `ContactsScreen.kt:231` dicen que `autoCorrectEnabled=false` impide aprender, y no es así.
8. **KDF débil en una bóveda importada.** Una bóveda v3 importada conserva sus parámetros de Argon2id, que `checkSane` acepta desde `m = 8·p KiB, t = 1` (`Crypto.kt:63`, `Vault.kt:344-348`). Conviene volver a envolver la DEK al importar si el KDF está por debajo del perfil elegido, o fijar un mínimo para bóvedas (OWASP: m ≥ 19 MiB, t ≥ 2).
9. **Archivos temporales de «Abrir lo recibido».** `cacheDir/pk-open` solo se borra al arrancar o mientras esa pantalla está compuesta (`PkActionsScreen.kt:509-511`, `SeguridadApp.kt:29`). Si la app muere con el selector de guardar abierto, el texto en claro queda en la caché privada hasta el siguiente arranque. Conviene borrarlo también desde el `lock()` del `VaultStore`.
10. **CI y publicación:**
    - validar `gradle-wrapper.jar` (`gradle/actions/wrapper-validation` fijado por SHA);
    - proteger los secretos con un *Environment* con revisores, y proteger las etiquetas `v*`: hoy quien pueda hacer push de una etiqueta obtiene un APK firmado con la clave de producción;
    - `followRedirects(false)` en `secureClient`;
    - revisar como cambio de seguridad cualquier diff de `verification-metadata.xml` (`verify-signatures=false`);
    - hacer fallar `assembleRelease` local sin `keystore.properties` salvo con un flag explícito, para no distribuir por error un APK firmado con la clave pública.

**Prioridad baja**

11. El plazo de 7 días de la huella usa el reloj del sistema (`Vault.kt:381-382`): quien tenga el móvil desbloqueado y su huella puede retrasar el reloj para alargarlo.
12. `destroy()` deja las carpetas `vault.bin.bad-*` y `att.bad-*` (`VaultStore.kt:665-676`).
13. `parseOtpAuth` no filtra caracteres de control o bidi ni limita la longitud del emisor y la cuenta (`Totp.kt:115-118`), a diferencia de `OtpMigration`.
14. En Android 8-12 no existe `EXTRA_IS_SENSITIVE` (`Clipboard.kt:38`): el historial del teclado puede guardar lo copiado.
15. Carrera de la salida obsoleta en «Cifrar para» y en «Firmar»: si se cambia el contacto o el texto durante la operación, se muestra el resultado anterior (`PkActionsScreen.kt:365,482`). Además, al cifrar un archivo para un contacto, el destinatario ve el nombre original del archivo (`FileMessage.kt:125`); conviene decirlo en la interfaz.
16. Detalles menores:
    - `unlockWithKey` no borra la clave en la salida temprana (`VaultStore.kt:252`);
    - `senderOf` toma el primer contacto que coincide (`Contacts.kt:132-140`);
    - el truncado de nombres de `FileCrypto.kt:63` no respeta los límites de carácter (hoy no se alcanza);
    - StrongBox no se usa (ya está previsto para la 1.3.0).
17. Comprobar el manifiesto final fusionado del APK release con `aapt2 dump xmltree --file AndroidManifest.xml app-release.apk`: solo deben estar exportados `MainActivity` y el `ProfileInstallReceiver` protegido por `DUMP`.

## 6. Lo que está bien (para no romperlo)

- **Cifrado de textos y archivos:**
  - `Header.parse` y `KdfParams.checkSane` rechazan cabeceras, algoritmos y costes fuera de rango antes de Argon2id (`Crypto.kt:60-72,313-329`).
  - La cabecera completa es la AAD y cada cifrado lleva sal y nonce frescos.
  - En la cascada, cada capa tiene su clave HKDF.
  - El formato STREAM detecta truncado, reordenado y extensión (`FileCrypto.kt:93-131`).
- **Bóveda:**
  - La DEK va envuelta con Argon2id y AES-GCM, y la AAD liga el formato, la versión, el KDF y la sal, lo que impide degradar la versión.
  - La escritura es atómica con `fsync` y `ATOMIC_MOVE`.
  - Bloquear borra la DEK, y el `epoch` impide reabrir la bóveda tras un bloqueo.
- **Importación `.sgbak`:**
  - Solo se aceptan nombres exactos, y el archivo de cada adjunto se construye desde un id hexadecimal de 32 caracteres, así que no hay *zip slip*.
  - Hay límites de 6 MB por entrada, 100 MB en total y 2.000 entradas.
  - Todo se valida (contraseña, SHA-256 de cada adjunto) antes de tocar nada, y hay vuelta atrás si algo falla.
- **Clave pública:**
  - Las tarjetas van autofirmadas y su análisis es estricto (NFC, puntos de orden pequeño).
  - Las firmas de mensajes y archivos ligan al destinatario completo, lo que impide el reenvío como si fuera del remitente original.
  - Cada uso tiene su propia etiqueta de dominio.
  - «Verificado» solo se pone por QR o por confirmación explícita, y una tarjeta que reutiliza claves no se puede guardar a ciegas.
  - El nombre que se muestra como remitente es siempre el que eligió el dueño.
- **Android:**
  - Solo se exporta el lanzador, que no lee intents.
  - `FLAG_SECURE` y el filtro de toques están en todas las ventanas y diálogos.
  - El portapapeles se marca como sensible y se borra solo.
  - Compartir solo envía material público.
  - La clave de la huella exige biometría fuerte en cada uso, está ligada a un `CryptoObject` y se invalida si se añade una huella.
  - Las reglas de copia y transferencia de Android excluyen todo.
  - No hay logs.
- **Red:** solo sale el prefijo SHA-1 de 5 caracteres, con `Add-Padding`, la confianza TLS del sistema más el bloqueo de eMudhra, sin texto en claro, un tope de 2 MB, y un fallo de red nunca se da por «no filtrada».
- **Cadena de suministro:**
  - Las versiones son exactas y cada dependencia y plugin está verificado por SHA-256, igual que la distribución de Gradle.
  - Las acciones están fijadas por SHA, no se usa `pull_request_target`, y los secretos solo existen en el paso de compilación de `main` y de las etiquetas `v*`.
  - El trabajo que publica no ve los secretos.

## 7. Siguientes pasos

1. Añadir la exclusión de autorrelleno (NV-1, una línea) y validarla con `adb shell dumpsys autofill`.
2. Arreglar las dos carreras de bloqueo (mejoras 1 y 2) y la del contacto verificado (3), con un test cada una.
3. Hacer que la rotación use los `documentId` que creó la app (NV-2) y añadir el test descrito en `NEEDS-VALIDATION.md`.
4. Endurecer `VaultStore.replace` (4).
5. Antes de publicar en Google Play: proteger las etiquetas y los secretos de CI (10) y comprobar el manifiesto fusionado (17).

## 8. Resumen de cobertura

| Estado | Unidades |
|---|---|
| Cubiertas sin hallazgos | 23 |
| Con hallazgo (por validar) | 2 |
| Bloqueadas / aplazadas / fuera de alcance | 0 |

Exclusiones con motivo:
- Web y autenticación: no hay sesiones ni OAuth.
- WebView y navegador: no se usa WebView.
- Nube: no hay infraestructura.
- RPC y mensajería: no hay; los formatos de clave pública se revisaron como criptografía.
- IA: no hay.
- Memoria nativa: no hay código nativo.
- Agotamiento de recursos como dominio propio: es una app de un solo usuario; el agotamiento por archivos recibidos se revisó dentro de «manejo de archivos».

El revisor de cobertura final no encontró más trabajo.

Archivos de esta ejecución: `run-metadata.json`, `architecture.md`, `coverage-ledger.json`, `findings.json` (los 2 registros, validados con `validate-findings.cjs`) y `NEEDS-VALIDATION.md`. `FINDINGS-DETAIL.md` existe, pero sin contenido porque no hay hallazgos confirmados.

---

## Revisión experto-seguridad-cripto

- **Modelo de amenaza aplicado:**
  - quien te manda contenido (tarjetas, mensajes, archivos, `.sgbak`, QR);
  - otra app en el móvil;
  - quien tiene el móvil o una copia `.sgbak`;
  - la red hacia HIBP;
  - la cadena de suministro y la CI.

  El dueño actuando sobre su propia bóveda no cuenta como atacante.
- **Primitivas:** solo estándar (`javax.crypto` y Bouncy Castle):
  - AES-256-GCM y ChaCha20-Poly1305 (RFC 8439);
  - Argon2id (RFC 9106) con límites de cordura;
  - HKDF-SHA256 (RFC 5869);
  - Ed25519 y Ed25519ph (RFC 8032);
  - HPKE (RFC 9180) con X-Wing (`draft-ietf-hpke-pq`);
  - STREAM para archivos.

  No se encontró criptografía propia fuera de la composición en cascada, ya señalada en la revisión anterior como «sin análisis formal». Cada capa usa su propia clave HKDF (`Crypto.kt:157-161`), así que reutilizar el nonce entre capas no es un problema.
- **Gestión de claves:**
  - La DEK es aleatoria, va envuelta con la KEK de Argon2id y se borra al bloquear (`Vault.kt`, `VaultStore.kt`).
  - La clave de la huella vive en el Keystore y exige biometría fuerte en cada uso (`BiometricVault.kt:42-56`).
  - El punto débil real es el ciclo de vida: las copias antiguas siguen abriéndose con la maestra antigua y la identidad no rota (mejoras 6 y 8).
- **Severidad:** ningún hallazgo «roto en la práctica». NV-1 y NV-2 quedan «a comprobar», con su plan. Las mejoras son «debilitado en teoría» o de higiene.
