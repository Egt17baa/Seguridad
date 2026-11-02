# Revisión final de la rama: Clavero 1.3.1 (`9504ce3..e6796db`)

- **Revisor:** revisión de toda la rama, de solo lectura (sin cambios en el árbol, el índice ni HEAD).
- **Skills leídas:** `experto-seguridad-cripto` (`SKILL.md`, `references/auditoria.md` y `cripto-moderna.md` como lista de comprobación), `desarrollo-movil-senior` y `seguridad-criptografia-movil`.
- **Cómo:** el diff entero en varias pasadas (red, bóveda y pantallas, cripto, TOTP, fuera CriptoPanel, compilación y README), más `git show`/`git grep` sobre la historia para lo que dependía de ella. Me salté el markdown de `auditoria/` y `docs/superpowers/` salvo para leer los requisitos. No he vuelto a ejecutar los tests: ninguna duda lo pedía, porque todo lo que señalo se ve leyendo el código.
- **Leído también:** el diseño, el plan, `context.md`, todo `progress.md` (también las líneas que añadió el controlador durante esta revisión, con los 9 hallazgos del `code-review`) y `04-cripto.md`.

## Puntos fuertes

- **CRY-B01, defensa en profundidad bien hecha.**
  - `DistrustingTrustManager.checked` (`net/DistrustedCertificates.kt:109-113`) rechaza la cadena que manda el servidor *antes* de pasarla al sistema, así que este cliente nunca mete el intermedio comprometido en la caché de Conscrypt.
  - Después rechaza la cadena que validó el sistema, que da `X509TrustManagerExtensions` (`android/TrustedChain.kt:22-31`).
  - El nombre del servidor se toma de `handshakeSession.peerHost`, el mismo sitio de donde lo lee `RootTrustManager` (`:120`, `:125`).
  - Falla cerrado sin nombre y también si el constructor lanza.
  - Se inyecta desde `SeguridadApp.kt:30` para que `net/` siga compilando en `jvm-check`.
  - Los tests usan certificados reales de eMudhra, un espía del sistema en las tres sobrecargas y sesiones falsas de socket y de motor (`DistrustedCertificatesTest.kt:164-198`), y comprueban que el cliente usa la función inyectada (`HttpClientTest`).
- **CRY-A01, la regla en el sitio justo.**
  - `newMasterProblem` es pura y está en `vault/`, probada en la JVM con las listas reales de `assets/`, con 200 salidas del Generador por lista y separador, y con el orden longitud, estimación y palabra.
  - La regla solo está en las dos pantallas que crean una maestra (`VaultScreen.kt:253,270`, `VaultBackup.kt:118,233-235`). El diálogo de importar sigue igual, y un test de código fuente lo vigila.
  - Las listas se cargan en `Dispatchers.IO`, con `remember` y no `rememberSaveable`. Si fallan, la regla vuelve a la estimación sola, sin dejar a nadie sin poder crear la bóveda.
- **CRY-A02, mínimo y sin tocar ningún formato.**
  - Solo `CryptoFailure` llama a `onWrongKey`; `MasterRequired` y cualquier otro error siguen como en la 1.3.0 (`VaultStore.kt:94-114`).
  - Se marca la pantalla antes de llamar a `disable`, y la cancelación y un `onWrongKey` que lanza tienen sus tests (`VaultStoreTest.kt:979-1170`).
  - El orden distinto para `VERSION_UNKNOWN` está documentado y fijado en un test.
- **CRY-A03, el mejor trabajo de la rama.**
  - Lo que se da por hecho de Bouncy Castle 1.86 queda fijado con tests que fallan en voz alta si cambia: `getKey()` devuelve la misma instancia y `GCMBlockCipher.lastKey` es ese mismo array.
  - En ChaCha20-Poly1305 se hace un `init` final con clave cero, con la API pública, que pisa la copia propia, las palabras del motor y la clave de Poly1305; la reflexión solo está en el test.
  - Tras un tag malo se pone a cero el búfer con el texto en claro (`Crypto.kt:263-277`).
  - Hay vectores fijos para los 6 algoritmos, la cascada incluida (`CryptoTest.kt:504`), y vectores independientes hechos con Python `cryptography` (`DialectTest.kt:128`).
  - Nada más se apunta como riesgo aceptado (`task-4-report.md:160`).
- **CRY-B02.**
  - Ya no se cambia nada en silencio (`Totp.kt:85-93,123,184-189`). Un `algorithm` vacío guardado sigue siendo SHA1, y al exportarlo se escribe `algorithm=SHA1`.
  - El Inspector de QR conserva los nombres y añade la nota.
  - Todas las entradas de los datos de prueba y de `OtpMigrationTest` siguen siendo `isSupported()` (`OtpMigrationTest.kt:101`).
- **Fuera CriptoPanel, quirúrgico y con pruebas de la historia.**
  - Ningún escritor de la historia usó el `kdfId` 2: lo he comprobado con `git grep` en todas las revisiones de `Crypto.kt`.
  - `wrap` siempre escribió `format` desde `07f220e`, así que ninguna `seguridad-vault` sin `format` ni con versión 1 salió nunca de «Seguridad».
  - La importación siempre volvía a envolver con `afterMasterUnlock` antes de guardar.
  - El rechazo se prueba con datos reales de cada tipo (`LegacyVaultTest.kt:46`, `DialectTest.kt:150,189`, `PkActionsTest`) y sin tocar la bóveda que ya hay.
  - Los formatos propios quedan fijados con textos y archivos escritos por el código de la 1.3.0 (`DialectTest.kt:77`). Los datos de `seguridad/` y `vectors/` no cambian.
- **Hermética.**
  - El diff no toca `AndroidManifest.xml`, `res/`, `build.gradle.kts` raíz, `gradle/` ni `settings.gradle.kts`, y la vuelta atrás de R8 deja cero cambios.
  - No hay logs ni URL nuevas.
  - Los 35 commits son de `Claude <noreply@anthropic.com>`, sin `Claude-Session`/claude.ai y sin correos personales.

## Hallazgos

### Críticos (hay que arreglarlos)

Ninguno.

### Importantes (deberían arreglarse): condiciones previas al PR, ya previstas en el paso de documentos

**I1. Documentos que siguen diciendo que Clavero lee los formatos de CriptoPanel, y documentos de la 1.3.1 que faltan.**
- **Dónde:**
  - `CLAUDE.md:92` («`CP1:`, `.cpenc` y `criptopanel-vault` (CriptoPanel) también» se leen para siempre);
  - `.github/pull_request_template.md:9`, una casilla que el revisor del PR marcaría afirmando algo falso;
  - `docs/superpowers/handoff/sdd-1.3.1/progress.md:3`, una cabecera que se ha publicado y dice todavía «keep reading CriptoPanel formats, remove only the name»;
  - además faltan `docs/auditorias/2026-11-01-auditoria-1.3.1.md` (con el riesgo residual de CRY-A03), las líneas de la prueba en el móvil de la 1.3.1 en el README y «Estado actual» en `CLAUDE.md`.
- **Por qué importa:** la regla del proyecto y la plantilla del PR contradicen lo que hace la rama, y un revisor futuro podría «arreglar» la falta de lectura de CriptoPanel como si fuera una regresión.
- **Cómo:** es el paso de documentos del plan («Después de las tareas», punto 2). Hay que hacerlo antes de abrir el PR, con la decisión y la fecha en la regla de `CLAUDE.md`.

### Menores (para arreglar en la tanda final, según la regla del proyecto)

**M1. El KDoc nuevo afirma algo que la pantalla no hace** (está relacionado con el aplazado 7).
- **Dónde:**
  - `vault/Vault.kt:391-393`: «asks for the master password, which then says it is not a compatible vault»;
  - el comentario de `VaultV3Test.kt:679-681`, que dice lo mismo;
  - la línea del aplazado 7 en `progress.md`.
- **Qué pasa:** `UnlockVault` captura cualquier `CryptoFailure` y enseña «Contraseña incorrecta» (`ui/screens/VaultScreen.kt:337-338`). Con `CryptoFailure(NOT_A_VAULT)` (`Vault.kt:266`) el usuario lee «Contraseña incorrecta», no «No es una bóveda compatible».
- **Por qué importa:**
  - Un `vault.bin` que no se puede leer (versión 1 o 4, u otro formato) solo existe si alguien lo edita a mano con root: ninguna versión lo escribe, `allowBackup=false` y `data_extraction_rules.xml` excluye todo.
  - Aun así, el texto empuja hacia «He olvidado la contraseña maestra», que borra la bóveda.
  - Y el KDoc, que se escribió en esta rama, es falso.
- **Cómo:** en `VaultScreen.kt:337-338`, `error = if (e.message == NOT_A_VAULT) NOT_A_VAULT else "Contraseña incorrecta"`, haciendo `NOT_A_VAULT` `internal`. Es un texto que ya existe, en un sitio nuevo, y va a la lista del Abogado. Después se corrigen el KDoc y el comentario del test. Se hace junto con el aplazado 7.

**M2. `checkAssets` no comprueba que las listas de palabras vayan en el APK, aunque el diseño lo da por hecho.**
- **Dónde:** el diseño, §CRY-A01 («`checkAssets` comprueba que van en el APK»), frente a `app/build.gradle.kts:188-209`, que solo rechaza los *assets* que no son de la app.
- **Por qué importa:** `loadMasterWords` devuelve `emptySet()` ante cualquier fallo (`MasterPolicy.kt:64-68`), como debe. Pero si una lista desapareciera del APK (un `ignoreAssetsPatterns` nuevo o un nombre cambiado), la regla de la palabra se apagaría en silencio y ningún test lo vería, porque `MasterPolicyTest` lee `src/main/assets`.
- **Cómo:** en `checkAssets.doLast`, que falle también si a `merged` le falta alguno de `eff_large_wordlist.txt`, `bip39_english.txt` o `bip39_spanish.txt`, con el mismo estilo de mensaje. Si no, hay que corregir la frase del diseño. Prefiero la comprobación: son tres líneas.

**M3. Un fallo al decodificar con la clave buena también desactiva la huella** (es el hallazgo 1 del `code-review`, con su severidad ajustada).
- **Dónde:** `Vault.kt:281-290`, donde `read` convierte una `SerializationException` en `CryptoFailure`, y `VaultStore.kt:105`.
- **Qué pasa:** si las entradas se autentican pero no se pueden decodificar, el usuario ve `FINGERPRINT_STALE` y la huella se desactiva.
- **Alcance:** los datos van autenticados, así que solo pasaría con un JSON que escribió la propia app y que esta versión no lee, es decir, bajar de versión, que Android no permite sin desinstalar. No es alcanzable hoy, y la maestra daría el mismo fallo.
- **Cómo:** basta con una frase en el KDoc de `launchUnlockWithKey` («una `CryptoFailure` de decodificación, que solo daría una bóveda escrita por una versión más nueva, también la desactiva; la maestra falla igual»). Separar los tipos de excepción toca los caminos de BOV-3 y no compensa en una versión de mantenimiento.

**M4. El Inspector de QR ofrece «Añadir» para un código que él mismo dice que Clavero no admite.**
- **Dónde:** `tools/qr/QrView.kt:187-193`.
- **Qué pasa:** con la nota `TOTP_UNSUPPORTED_LINK`, la acción sigue siendo `ADD_TOTP`, y lleva al editor, que da el mismo error (`VaultEntryScreen.kt:257-260`).
- **Por qué importa:** una persona razonable no espera un botón que solo lleva a un error.
- **Cómo:** `action = if (unsupported) QrAction.NONE else QrAction.ADD_TOTP`, y adaptar `QrViewTest`. De paso:
  - leer el enlace una sola vez (`readOtpAuth` se ejecuta dos veces en `QrView.kt:187,192` y dos en `VaultEntryScreen.kt:257,260`), con una función pública que devuelva los nombres y si se admite;
  - lo que no es un enlace usable ya ofrecía `ADD_TOTP` en la 1.3.0, y eso no lo juzgo.

  Es un criterio de producto: si el controlador prefiere el editor como único sitio que explica, se apunta y basta.

**M5. Las listas se leen y se normalizan otra vez en cada apertura y en cada giro.**
- **Dónde:** `ui/screens/MasterWords.kt:27-30`, unas 12 000 líneas con NFD y una expresión regular.
- **Por qué importa:** es poco (en `IO`, y el botón espera), pero se repite sin necesidad. Las listas no son secretas y no cambian.
- **Cómo:** guardarlas una vez por proceso, con un `lazy` o un `Deferred<Set<String>>` en `AppContainer`, cargado en `IO` la primera vez que se pide. Se mantiene «fuera del hilo principal» y el `null` mientras carga.

**M6. La segunda validación de CRY-B01 va sin los datos de la sesión** (informativo, a comprobar, sin cambios en el código).
- **Dónde:** `android/TrustedChain.kt:29`.
- **Qué pasa:** `X509TrustManagerExtensions.checkServerTrusted(chain, authType, host)` no recibe ni el OCSP grapado ni las SCT de la extensión TLS.
- **Riesgo:** si Android exigiera Certificate Transparency para el host y el servidor las mandara solo por TLS, la segunda validación fallaría cerrada y la comprobación de filtraciones dejaría de funcionar, que es el criterio de fracaso del diseño.
- **Hoy:** `targetSdk` 36, sin Network Security Config (`res/xml/` no tiene `network_security_config.xml`), y los certificados públicos de `api.pwnedpasswords.com` llevan las SCT dentro. No veo riesgo hoy.
- **Cómo:** entra en la prueba del dueño («Comprobar contraseña» y «Salud», con Wi-Fi y con datos móviles), con una nota en el informe de la auditoría de la 1.3.1: hay que volver a probarlo cuando suba `targetSdk` o si se añade una NSC con CT.

**M7. Dos frases inexactas en el diseño y el plan** (son problemas del plan).
- «`seguridad-vault` v1 a v3» (diseño, «Lo propio de Clavero»; plan y `context.md`, restricciones globales): nunca existió una `seguridad-vault` v1. `supported()` no la aceptaba ni en `07f220e`, y por eso quitar la versión 1 no rompe nada. La frase sugiere que la v1 se sigue leyendo.
- La frase de `checkAssets` del punto M2.

Basta con corregirlas en los documentos del paso final.

## Los aplazados

| # | Decisión | Cómo |
|---|---|---|
| 1 | **Arreglar ahora** | `GeneratorScreen` (`SecurityToolsScreen.kt:936`, antes :940) lee la lista EFF en el hilo principal y, además, **no cierra el flujo** (`bufferedReader().readLines()` sin `use`). Arreglo: `val words by produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) { runCatching { context.assets.open("eff_large_wordlist.txt").bufferedReader().use { it.readLines() }.filter { it.isNotBlank() } }.getOrNull() } }`, el segmento «Frase» con `enabled = words != null`, y `result` y `bits` de la frase solo con `words`. Sin texto nuevo, con la misma forma que `CodesScreen.kt:141-149`. |
| 2 | **No cambiarlo en la 1.3.1** (lo apunta el controlador) | `BiometricVault.disable` (`android/BiometricVault.kt:151-158`) hace un `edit {}` asíncrono y un `deleteEntry` del Keystore, una sola llamada *binder* a keystore2, que borra de forma asíncrona. Hay seis llamadas con el mismo patrón (`VaultBackup.kt:131,175`, `VaultScreen.kt:167,278,320,373`). Moverlo a `IO` sin poner las llamadas en fila puede hacer que un `disable` tardío borre la clave que `enable` acaba de crear con el mismo alias, y eso es peor que unos milisegundos en el hilo principal. Además, `onWrongKey` corre dentro de una corrutina ATOMIC que puede estar cancelada, y un `withContext(IO)` se saltaría el `disable`. Lo bueno es pasar todas las operaciones de `BiometricVault` a un dispatcher de un solo hilo en la 1.4.0. |
| 3 | **Ya arreglado, nada que cambiar** | `ba572c0` quitó «only the GCM composition is independent». El texto de ahora (`CryptoTest.kt:44-49`) es exacto: GCM independiente de Bouncy Castle; el motor Twofish tiene su vector publicado en `SeedCryptoTest.kt:39-40` y Serpent solo la ida y vuelta. |
| 4 | **Arreglar ahora** (poco valor, porque el secreto Base32 ya vive como `String` inmutable en `TotpSecret`, pero es barato) | (a) `totpKey` (`Totp.kt:66`): `val b = base32Decode(secret) ?: return null; if (b.size < TOTP_MIN_KEY_BYTES) { b.fill(0); return null }; return b`. (b) `base32Decode` (`Totp.kt:24-…`): escribir en un `ByteArray` del tamaño exacto (`n * 5 / 8`) en lugar de `ByteArrayOutputStream`, o borrar su `buf` con una subclase. (c) `isUsableTotpKey` (`Totp.kt:69`) pasa a `internal` o pública y sustituye a `totpKey(totp.secret) == null` en `VaultEntryScreen.kt:494`. Hay que añadir tests de que el array corto y el de la validación quedan a cero. |
| 5 | **Nada que cambiar** | El proyecto no tiene regla de longitud de línea (ni `.editorconfig`, ni detekt, ni ktlint), y en `app/src/main/java` hay 1 364 líneas de más de 120 caracteres; solo en los ficheros tocados hay más de 60, algunas de hasta 167 (`Crypto.kt:310`). Reformatear solo esas cinco es ruido en el diff. |
| 6 | **Arreglar ahora** | En `VaultStore.unlockWithKeyLocked` (`:446-461`), calcular `VaultCrypto.biometricRefusal(v, now(), clockHighWater())` **antes** de `resolve`. Es equivalente para todo archivo que se admite, porque `resolve` solo rellena `version` y `biometricRefusal` lee `format`, `masterCheckedAt` y `supported()`, que acepta `VERSION_UNKNOWN`. Así un `vault.bin` de otro formato sin versión ya no llega a `CryptoFailure` ni desactiva la huella, y `VERSION_UNKNOWN` sigue el mismo orden que la v2 y la v3. Hay que reescribir `VaultStoreTest.kt:1030` (que fija el orden de ahora) y añadir: otro formato con `VERSION_UNKNOWN` y la DEK buena da `MasterRequired`, `onWrongKey` no se llama y `vault.bin` no cambia. |
| 7 | **Arreglar ahora, con M1, sin texto nuevo** | Añadir `BiometricRefusal.UNSUPPORTED` para `!supported(file)` (`Vault.kt:401`). La pantalla (`VaultScreen.kt:352-357`) no enseña nada con `UNSUPPORTED -> Unit`, y `when` exhaustivo obliga a tratar todos los usos. `MasterRequired` con `UNSUPPORTED` puede llevar `NOT_A_VAULT`, aunque no se ve, porque el botón está oculto. El desbloqueo con la maestra enseña «No es una bóveda compatible» (M1). Hay que adaptar `VaultV3Test.kt:676,682`. Si el controlador prefiere no tocar el `enum`, la otra salida es apuntarlo como límite aceptado (solo con root), arreglando aun así el KDoc de M1. |

**Límites aceptados del registro:** estoy de acuerdo con todos. No veo que ninguno cause un daño concreto más allá de lo apuntado.
- Que un giro pierda `FINGERPRINT_STALE` es cosmético: la huella ya está desactivada y el botón no vuelve.
- Las contraseñas del Generador rechazadas (1 de cada 61 000 a 16 caracteres) se arreglan con «↻ Otra».
- «Barcelona1», dos palabras juntas y un `digits=` o `period=` vacío son decisiones o casos que nadie escribe.
- Los vectores de Twofish y Serpent solo se contrastan con Bouncy Castle, pero los motores tienen sus propios KAT.
- El riesgo residual de CRY-A03 está bien acotado.

## Lo que no he juzgado

- **Los umbrales de la maestra** (10 caracteres, 50 bits, una sola palabra de las listas) y que solo se apliquen al crear o cambiar: es decisión del dueño.
- **Quitar CriptoPanel entero**, aunque el README de la 1.3.0 prometía leerlo siempre: decisión del dueño, que no tiene ninguna bóveda en CriptoPanel. La rama lo dice en `README.md:48`.
- **Los 53 avisos de R8 sobre los metadatos de Kotlin:** decisión del dueño, que se van a la 1.4.0.
- **El mínimo de 10 caracteres para la contraseña de una copia al importar** (`VaultBackup.kt:235`): ya estaba, y la auditoría comprobó que toda copia que se puede importar se hizo con 10 o más.
- **«Contraseña incorrecta» frente a «…o datos dañados» con una maestra mal escrita:** ya estaba y está fuera del alcance. Solo señalo el caso de `NOT_A_VAULT` (M1).
- **`SecretKeySpec` con Conscrypt, `HKDFParameters` y las subclaves de GCM:** el diseño los acepta como riesgo.
- **Atar el blob de la huella con AAD o con versión:** el diseño lo descartó.
- **Mantener 80 bits como mínimo de la clave TOTP:** por compatibilidad, según el diseño.
- **Que `ADD_TOTP` se ofrezca para un `otpauth` que no es usable:** ya pasaba en la 1.3.0. Solo señalo el caso de los ajustes no admitidos (M4).
- **El `enum` `Dialect` con un solo valor y sus campos sin uso** (`fileExtension`, y `textPrefix` igual que `TEXT_PREFIX`): el `code-review`, hallazgo 8, lo señala. La tarea 6 decidió conservarlo por las firmas públicas, y no hace ningún daño.
- **El estilo de longitud de línea:** no hay regla en el proyecto.
- **Lo que solo se puede comprobar en un móvil:** el apretón de manos real con `X509TrustManagerExtensions`, lo que anuncia TalkBack, el Keystore y StrongBox. Va a la prueba del dueño.
- **El fondo legal de los textos:** es de la revisión final del Abogado Digital.

## Recomendaciones

1. **Tanda final de arreglos, con re-revisión:**
   - los aplazados 6 y 7, junto con M1 (son un mismo cambio en `Vault.kt`, `VaultStore.kt` y `VaultScreen.kt`, más sus tests);
   - los aplazados 1 y 4;
   - M2 (`checkAssets`) y M3 (el KDoc);
   - M4 y M5, según decida el controlador;
   - después, las dos verificaciones globales completas, con el número de tests, que solo puede subir.
2. **El paso de documentos (I1)** antes del PR:
   - la regla de `CLAUDE.md`, con su fecha;
   - `pull_request_template.md:9`;
   - la cabecera del traspaso;
   - el informe `docs/auditorias/2026-11-01-auditoria-1.3.1.md`, con el riesgo residual de CRY-A03 y la nota de M6;
   - las líneas de la prueba en el móvil;
   - las frases de M7.
3. **La prueba del dueño con el APK firmado:**
   - «Comprobar contraseña» y «Salud» con Wi-Fi y con datos móviles (CRY-B01 y M6);
   - una huella que quedó de antes: sale `FINGERPRINT_STALE` y desaparece el botón;
   - crear la bóveda o cambiar la maestra con «contraseña1» y «contraseña123» (rechazadas) y con una frase del Generador (aceptada);
   - importar una copia con una maestra débil (se importa);
   - un QR `otpauth` con `digits=10` (el aviso, en el editor y en el Inspector);
   - que los códigos 2FA que ya había siguen saliendo.

## Valoración

**¿Lista para fusionar?** Con arreglos.

**Por qué:** los cinco hallazgos de la auditoría están cerrados de verdad, con buenos tests y sin regresión en los formatos de Clavero, y CriptoPanel ha salido sin dejar código muerto ni textos visibles. Lo que queda son menores, que la regla del proyecto arregla antes de fusionar (sobre todo los aplazados 6 y 7, con M1, y M2), el paso de documentos ya previsto (I1) y la prueba del dueño en el móvil.

## Revisión experto-seguridad-cripto

**Modelo de amenaza:**
- un intermediario en la red con una clave de eMudhra (CRY-B01);
- quien tenga una copia `.sgbak` y la ataque fuera del móvil (CRY-A01);
- root o un volcado de memoria (CRY-A03);
- un `vault.bin` o una huella que no encajan (CRY-A02);
- un QR o enlace manipulado (CRY-B02);
- un archivo importado manipulado (fuera CriptoPanel).

**CRY-B01 (cerrado):**
- La cadena presentada se rechaza antes del sistema (`DistrustedCertificates.kt:110`). La validada, después (`:112`).
- El nombre sale del apretón de manos (`:120,:125`).
- Falla cerrado sin nombre y con un constructor que lanza (`TrustedChain.kt:24-29`).
- El cliente es `lazy`, una sola vez por proceso, con la función inyectada (`AppContainer.kt:106`, `SeguridadApp.kt:30`, `DistrustedCertificates.kt:148`).
- `getAcceptedIssuers` sigue filtrando. Nunca es más débil que el sistema: solo añade rechazos.
- Una `RuntimeException` dentro de la segunda validación también acaba en un rechazo, porque Conscrypt la envuelve en `CertificateException`.
- Queda a comprobar: CT y OCSP en la segunda validación (M6).

**CRY-A01 (cerrado, con la regla del dueño):**
- `MasterPolicy.kt:102-107`: la longitud, después `estimateBits ≥ 50` y después el núcleo en las listas.
- `masterCore` usa `Locale.ROOT` y NFD sin `\p{Mn}` (`:34-44`).
- Solo se aplica a contraseñas nuevas: `changeMaster` usa `PasswordUse.NEW` (`Vault.kt:371`) y el desbloqueo y la importación no pasan por aquí.
- La importación de una copia con una maestra débil sigue funcionando (`VaultBackup.kt:235`).
- Las copias de la maestra en `String` que hacen `masterCore` y `estimateBits` son el mismo riesgo que el campo de texto, y lo dice el KDoc (`:31-32`).
- La regla de las listas se puede apagar en silencio si faltan en el APK (M2).

**CRY-A02 (cerrado):**
- `VaultStore.kt:105-111`: solo `CryptoFailure` lleva a `onWrongKey` y a `FINGERPRINT_STALE`.
- La clave se pone a cero en todas las salidas (`:440`, `:451`, `:455`, `opened:348/353`).
- La huella se desactiva aunque la pantalla esté cancelada (ATOMIC), y no se desactiva por `MasterRequired` (`VaultStoreTest.kt:1065`, y el reloj).
- Quedan el orden con un archivo que no se admite (aplazado 6) y la decodificación (M3).

**CRY-A03 (cerrado, con el riesgo residual apuntado):**
- `kp.key.fill(0)` en un `finally` (`Crypto.kt:303,382`).
- El `init` con clave cero de ChaCha (`:384`).
- El búfer del texto en claro se pone a cero cuando falla (`:275`).
- El array de quien llama queda intacto, y sigue siendo responsabilidad suya (test de `aead`).
- Lo que se da por hecho de Bouncy Castle queda fijado con reflexión solo en los tests.
- Riesgo aceptado: `SecretKeySpec`/Conscrypt (AES), `HKDFParameters` y las subclaves y tablas de GCM y de los motores. Es honesto y lo dice el KDoc (`:280-286`).

**CRY-B02 (cerrado):**
- `hmacName` da `null` con lo desconocido (`Totp.kt:85-90`).
- `isSupported` admite de 6 a 8 dígitos y de 10 a 120 s (`:93`).
- `totpCode` ya no usa `coerceIn` (`:123-127`).
- Un parámetro presente que no se admite rechaza el enlace (`:184-189`).
- El comentario de RFC 4226 R6 ya es exacto (`:59-62`).
- Ninguna entrada guardada por cualquier versión anterior deja de dar su código: todas las versiones desde `c70f932` guardaban valores admitidos.
- `hotp` lanza con lo que no se admite, en vez de calcular un código falso.

**Fuera CriptoPanel:**
- `CP1:`, `.cpenc` y `criptopanel-vault` reciben el error genérico: `Crypto.kt:490` (`NOT_COMPATIBLE`), `:598`, `PkActions.kt:480`, y `decodeFile`, que rechaza un archivo sin `format` antes de derivar ninguna clave (`Vault.kt:382-386`).
- Ningún descifrado deja una salida a medias, porque la cabecera se comprueba antes de escribir.
- Quitar el `kdfId` 2 y la AAD v1 es seguro: nadie los escribió nunca bajo `SG` ni `seguridad-vault`, según la historia.
- `SG1:`, `.sgenc`, `seguridad-vault` v2, v3 y sin versión (`VaultTamperTest`), `.sgbak` v1 y v2, la exportación de la 1.0.2 (`BackupTest.jsonExportsStillImport`) y `SGK1:`, `SGM1:` y `SGS1:` (`vectors/`) siguen intactos.

**Sin criptografía propia:** solo `javax.crypto` y Bouncy Castle 1.86, sin dependencias nuevas.

**Severidad de lo que queda:** ningún hallazgo de seguridad explotable. M1, M2 y el aplazado 6 son de robustez o de que el texto sea verdad. M6 es informativo.

## Revisión Ingeniero Móvil Senior

**Compose y estado:**
- `rememberMasterWords` (`MasterWords.kt:23-30`) usa `remember` y no `rememberSaveable`, con `LaunchedEffect(Unit)` y `withContext(Dispatchers.IO)`. El valor es `null` mientras carga, y el botón espera (`VaultScreen.kt:270`, `VaultBackup.kt:235`).
- La llamada condicional en `PasswordDialog` (`VaultBackup.kt:233`) depende de un parámetro que no cambia mientras el diálogo vive, así que el grupo es estable.
- `fingerprintStale` va en `remember` (`VaultScreen.kt:309-310`). Tras un giro, `BiometricVault.enabled` ya da `false`.
- El texto rojo es una región viva *Polite* (`VaultScreen.kt:254-255`, `VaultBackup.kt:245-246`).
- Mejora posible: guardar las listas una vez por proceso (M5).

**Hilos:**
- Las listas se cargan en `IO`.
- `BiometricVault.disable` sigue en el hilo principal, como en los otros cinco sitios (aplazado 2; lo justifico arriba).
- El EFF del Generador se lee en el hilo principal y su flujo no se cierra (aplazado 1, que se arregla ahora).
- El cliente HTTP se construye una vez con `by lazy`.

**Ciclo de vida:** `launchUnlockWithKey` mantiene BOV-3 (ATOMIC, la clave nunca se pierde). Un `onWrongKey` que lanza no tumba el `scope` de la pantalla (`VaultStore.kt:106-110`), y está probado.

**Plataforma:**
- `X509TrustManagerExtensions` funciona con `RootTrustManager` desde la API 24, y `minSdk` es 26.
- OkHttp sigue con `BasicCertificateChainCleaner` con nuestro gestor, igual que en la 1.3.0, porque `X509TrustManagerExtensions(DistrustingTrustManager)` lanza IAE y OkHttp lo captura. No se usa *pinning*.
- `@Suppress("CustomX509TrustManager")`, con su justificación (`DistrustedCertificates.kt:96`).

**Compilación y publicación:**
- `versionName` 1.3.1 (`app/build.gradle.kts:35`).
- R8 sigue como en la 1.3.0 (la vuelta atrás deja cero cambios; según la tarea 7, `classes.dex` y `mapping.txt` son idénticos).
- No hay permisos, componentes exportados ni dependencias nuevas.
- `jvm-check` tiene como entradas las fuentes solo de Android y `assets/` (`jvm-check/build.gradle.kts:40-51`).

**Tests:** 1274 en app y 1269 en jvm-check según el registro. Comprueban comportamiento real (red con certificados reales, almacén con disco temporal, vectores fijos e independientes) más tests de código fuente, que es el patrón del proyecto para `ui/`. Los tests de código fuente son frágiles ante un formateo, pero el proyecto lo acepta.

**Accesibilidad:** las regiones vivas de los avisos nuevos. TalkBack queda para la prueba en el móvil, que el informe de la 1.3.0 tiene pendiente (§4).

## Textos para el Abogado Digital (revisión final)

**Textos nuevos de la interfaz:**
1. `MASTER_TOO_SHORT` (`vault/MasterPolicy.kt:14`): «Mínimo 10 caracteres».
2. `MASTER_TOO_WEAK` (`vault/MasterPolicy.kt:17-18`): «Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).»
3. `FINGERPRINT_STALE` (`vault/VaultStore.kt:65-67`): «La huella ya no abre esta bóveda (se cambió la contraseña maestra o se importó una copia). Entra con la contraseña maestra y vuelve a activarla si quieres.»
   - Afirma como hecho las dos causas, y una bóveda dañada da el mismo aviso (ruling de la tarea 3).
4. `TOTP_UNSUPPORTED_LINK` (`crypto/Totp.kt:96`): «Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite.»
   - Sale en el editor (`VaultEntryScreen.kt:260`) y como nota del Inspector de QR (`QrView.kt:192`).
5. `TOTP_UNSUPPORTED_ENTRY` (`crypto/Totp.kt:99`): «Clavero no puede calcular este código: usa un ajuste que no admite.»
   - Sale en la ficha de la entrada (`VaultEntryScreen.kt:154`).

**Textos cambiados de la interfaz (fuera CriptoPanel):**
6. `ui/screens/VaultScreen.kt:293`: «Sirve una copia .sgbak de esta app (con los adjuntos) o una exportación .json de la 1.0.»
7. `ui/screens/SettingsScreen.kt:475`: «"«Exportar bóveda» guarda una copia .sgbak con los adjuntos. «Importar copia» acepta esa copia y la exportación .json de la 1.0."»
8. `ui/screens/SecurityToolsScreen.kt:353`: «Ese archivo no contiene un texto cifrado (SG1:…).»
9. `ui/screens/SecurityToolsScreen.kt:408` (etiqueta): «Texto cifrado (empieza por SG1:)».
10. `ui/screens/SecurityToolsScreen.kt:866`: «Elegir archivo .sgenc».

**Textos que ya existían y salen en situaciones nuevas:**
11. «Consejo: usa una frase de 5 o 6 palabras…» (`VaultScreen.kt:258-264`) se oculta mientras se enseña `MASTER_TOO_WEAK`, que da el mismo consejo.
12. «Por seguridad, cada 7 días hay que escribir la contraseña maestra.» (`VaultScreen.kt:354-355`) sale también con un `vault.bin` que no se puede leer (aplazado 7). Desaparece si se aplica el arreglo propuesto.
13. Lo que ve quien abre datos de CriptoPanel: «El texto cifrado debe empezar por SG1:», «No es un dato cifrado compatible» y «No es una bóveda compatible».
14. Si se aplica M1: «No es una bóveda compatible» en la pantalla de desbloqueo, en lugar de «Contraseña incorrecta», para un archivo que no se puede leer.

**README:**
15. `README.md:24`: «…Se siguen leyendo las exportaciones JSON de la 1.0.x.»
16. `README.md:44-60`, la sección «## Formatos», que sustituye a «Compatibilidad con CriptoPanel»:
    - `:46`, la frase de los formatos propios;
    - `:48`, «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`, `.cpenc` y su bóveda).»;
    - `:53`, «**JSON de la 1.0.x:** se siguen importando. Una JSON con adjuntos…». Hay una errata ya antigua: «Una JSON» debería ser «Un JSON», como en `:305`;
    - `:60`, «Estos formatos se leerán siempre: hay tests con datos reales escritos por versiones anteriores (…)».

    Hay que tener en cuenta que la 1.3.0 prometía «se leerán siempre» también para CriptoPanel: hay que confirmar que la nota de `:48` basta (decisión del dueño, que es su único usuario).
17. `README.md:305`, la línea de la prueba de la 1.3.0: «Un JSON exportado de la 1.0 sigue pidiendo la contraseña.»
18. Se borra la línea de la prueba de CriptoPanel, la antigua `README.md:217`.
19. **Pendiente del paso de documentos** (todavía sin escribir):
    - las líneas de la prueba en el móvil de la 1.3.1;
    - la regla de `CLAUDE.md:92`;
    - `.github/pull_request_template.md:9`;
    - `docs/auditorias/2026-11-01-auditoria-1.3.1.md`.

**Sin cambios:** `docs/privacidad.html` y `docs/ficha-google-play.md` no nombran CriptoPanel y no cambian.
