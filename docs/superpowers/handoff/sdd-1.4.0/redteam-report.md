# Clavero 1.4.0: informe del equipo rojo (decisión (f) del dueño, 8-11-2026)

**Alcance y modelo de amenaza.** He atacado el código de Clavero en la rama `ccr-d1e75160-p5f4wc` (base `2173da7`), desde las entradas de la decisión (f):
- los archivos que se importan: «Importar de otra app», «Importar copia», «Comprobar una copia», `.sgenc`;
- los textos `SG1:`, `SGM1:` y similares, y los QR;
- las demás apps del móvil (componentes, `FileProvider`, portapapeles, capturas, *autofill*, toques ocultos);
- un móvil robado o desbloqueado y las copias del sistema;
- el bloqueo y la huella;
- los usos de la criptografía, la red y TLS;
- la muerte del proceso;
- la cadena de suministro (CI, el *wrapper* y las sumas de las dependencias).

Atacantes: quien consigue que el usuario importe un archivo manipulado, otra app sin permisos especiales y quien tiene el móvil desbloqueado. Quedan fuera root, el malware con accesibilidad y la manipulación física, como en el diseño.

**Método:**
- he leído el código en busca de fallos;
- he escrito pruebas de concepto como tests en `app/src/test/java/app/seguridad/redteam/` (corren en `:app:testDebugUnitTest` y en `jvm-check`);
- he volcado el manifiesto y los permisos del APK de depuración con `aapt2`.

No hay móvil ni emulador: lo que necesita uno está en «Necesita prueba en el móvil».

**Cómo leer los tests:**
- un test `rtN_…` comprueba el comportamiento **seguro**, así que falla mientras RT-N siga abierto. Lleva `@Ignore("RT-N: open, see report")` solo para que la puerta de la rama siga en verde, y el controlador quita el `@Ignore` cuando llegue el arreglo;
- un test `resists…` documenta un ataque que hoy se resiste y queda como test de regresión.

## Resumen

| RT | Superficie | Hallazgo | Severidad (CVSS 3.1) | MASVS | Estado |
|---|---|---|---|---|---|
| RT-1 | Importar de otra app (plan de fusión) | `urlHost` toma `https://evil.example\@banco.es/` por banco.es; un navegador va a evil.example. Esa entrada pasa por duplicado de «Banco», y con «Reemplazar» la entrada de la bóveda se queda con la URL y la contraseña del atacante | Baja: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:L/A:N`) | MASVS-CODE-4 | Explotado (test) |
| RT-2 | Importar de otra app (plan de fusión) | Un título o un usuario con invisibles («Ban\u200Bco», «an\u00ADa») o con el acento descompuesto («Cafe\u0301») no es duplicado, pero se ve exactamente igual (`plainReportText` y la fuente lo igualan). El resultado es una entrada sombra junto a la buena | Baja: 3,3 (`AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N`) | MASVS-CODE-4, MASVS-PLATFORM-3 | Explotado (test) |
| RT-3 | Maestra nueva (CRY-A01) | La regla de «una sola palabra conocida» no ve una palabra en *leet* («P4ssw0rd2024!», «Dr4g0n!2024») ni en letras de ancho completo («ｐａｓｓｗｏｒｄ12345»). Las dos dan más de 50 bits en la estimación y se aceptan | Baja (sin CVSS: debilita una defensa contra un ataque de diccionario sin conexión a una copia robada; no es un fallo explotable por sí solo) | MASVS-AUTH-1, MASVS-CRYPTO-2 | Explotado (test) |

Recuento: **3 hallazgos explotados, los tres de severidad baja**, y **ninguno crítico, alto ni medio**. Hay además 17 tests de ataques resistidos, en los dos archivos, y la lista de lo intentado más abajo.

**La puerta de la rama**, con los tres `rtN_` ignorados:
- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: verde;
- `./gradlew -p jvm-check test`: verde.

El resultado se apunta al final del informe.

## Hallazgos

### RT-1 [Baja] Un enlace con «\» pasa por duplicado de otro sitio, y «Reemplazar» se queda con él

**Cómo lo he atacado.** Un CSV de navegador (o un JSON de Bitwarden) con esta fila:

```
name,url,username,password,note
Banco,https://evil.example\@banco.es/login,ana,la-del-atacante,
```

El usuario ya tiene en la bóveda «Banco» / «ana» / `https://banco.es/`.

**Lo que pasa:**
- `urlHost` (`app/src/main/java/app/seguridad/imports/MergePlan.kt:360-369`) corta la autoridad solo en `/`, `?` y `#` (línea 365) y se queda con lo que sigue a la última `@`. Para él, el host es `banco.es`, y la fila es un **duplicado** de la entrada de la bóveda;
- un navegador sigue el URL Standard de WHATWG, en el que `\` vale como `/` en `http(s)`. La autoridad es entonces `evil.example` y la ruta, `/@banco.es/login`. El propio lector de enlaces de Clavero, que imita al navegador, lo ve igual: `LinkSignals.kt:70`, `parseLink(evil).host == "evil.example"`;
- la fila del duplicado en la vista previa enseña el usuario y no la URL (`ui/screens/ImportScreen.kt:664`, `e.username.ifBlank { e.url }`), así que el usuario ve «Banco · ana», igual que su entrada;
- con «Reemplazar» (un toque, para todos), `replaceWith` (`MergePlan.kt:234-259`) toma del archivo la URL y la contraseña. La entrada de la bóveda pasa a apuntar a `evil.example`, con la contraseña del atacante, y la buena va al historial.

**Evidencia:**
- `RedTeamImportTest.rt1_aUrlThatGoesToAnotherSiteIsNotADuplicate`, sin el `@Ignore`, falla con: `urlHost must agree with the browser. Actual: banco.es`;
- `RedTeamImportTest.rt1Impact_replaceTakesTheAttackersUrlAndPassword` pasa hoy, y comprueba que tras «Reemplazar» la URL es `https://evil.example\@banco.es/login`, la contraseña es `la-del-atacante` y la buena está en `passwordHistory`.

**Impacto.** Es integridad, no confidencialidad. Clavero nunca abre enlaces: no hay `ACTION_VIEW` con datos de la bóveda, ni autorrelleno, ni `UriHandler` salvo dos URL fijas de Ajustes. Para que haga daño, el usuario tiene que:
1. importar el archivo del atacante;
2. elegir «Reemplazar»;
3. copiar más tarde esa URL en el navegador.

Entonces llega a un sitio de *phishing* que el atacante controla y, si escribe allí la contraseña que recuerda o la del historial, se la entrega. La contraseña buena no se pierde (queda en el historial). Por eso la severidad es baja: AC:H y UI:R.

**Arreglo** (en `MergePlan.kt`, en `urlHost`):
- leer el host como el navegador. La forma más corta es reutilizar `parseLink` (`tools/qr/LinkSignals.kt:61`), que ya trata `\`, los tabuladores, el *percent-encoding* y UTS #46. Algo así:

  ```kotlin
  internal fun urlHost(url: String): String? {
      val t = url.trim()
      if (t.isEmpty()) return null
      val p = parseLink(if (schemeOf(t) == null) "https://$t" else t)
      val host = p.host?.removePrefix("www.")
      return host?.ifEmpty { null } ?: t.lowercase(Locale.ROOT)
  }
  ```

- como mínimo, añadir `it == '\\'` al corte de la línea 365 para los esquemas especiales (`http`, `https`, `ftp`, `ws`, `wss`, sin esquema);
- además, que la fila del duplicado (`ImportScreen.kt:664`) enseñe también el host de la URL del archivo, con `plainReportText`, para que «Reemplazar» no se elija a ciegas.

Hay que comprobar que `MergePlanTest.loginDuplicatesByTitleUserAndHost` sigue en verde: `www.`, las mayúsculas y el puerto se quitan igual.

**Test que lo da por arreglado:** `rt1_aUrlThatGoesToAnotherSiteIsNotADuplicate`, sin el `@Ignore`. `rt1Impact_…` se vuelve trivial: sale por el `return` cuando no hay duplicado, y puede quedarse o borrarse.

### RT-2 [Baja] Entradas que se ven iguales y no son duplicadas: la entrada sombra

**Cómo lo he atacado.** En el archivo importado:
- «Ban\u200Bco» (con un espacio de anchura cero), «Banco\u2060» (con un unidor de palabras) y «Banco» con el usuario «an\u00ADa» (con un guion suave), todos con la URL de la bóveda;
- «Cafe\u0301», en NFD, frente a «Café», en NFC, de la bóveda.

**Lo que pasa:**
- el plan de fusión compara el título con `titleKey` (`MergePlan.kt:353`, que solo quita espacios y pasa a minúsculas) y el usuario con `trim()` (`MergePlan.kt:389` y `:404`; las notas, en `:398`). Ninguno de los dos quita los invisibles ni normaliza a NFC, así que no hay duplicado y la fila entra como **nueva**;
- todas las pantallas enseñan el título y el usuario con `plainReportText` (R9 y R10), que quita los invisibles, y la fuente dibuja igual «é» y «e» + U+0301. En la vista previa, en la lista de la bóveda y en la ficha, la entrada nueva es indistinguible de la que ya había;
- con la opción por defecto («Saltar»), el usuario cree que no entra nada repetido, pero entra una entrada sombra con la contraseña o el 2FA del atacante.

**Evidencia:** `RedTeamImportTest.rt2_aTitleOrUserThatLooksTheSameIsADuplicate`, sin el `@Ignore`, falla con `«Ban\u200Bco» / «ana» must be a duplicate of «Banco» expected:<1> but was:<0>`. El test comprueba antes que `plainReportText` de los dos lados es igual tras NFC, así que «se ven iguales» está demostrado.

**Impacto.** Bajo:
- no se filtra nada;
- la entrada sombra puede llevar al usuario a usar la contraseña del atacante (falla al entrar) o un 2FA que no es el suyo, o confundirle hasta borrar la buena;
- es suplantación de una entrada, que el modelo de amenaza del diseño nombra («colar entradas que suplanten otras»), aunque sin pérdida de datos. La regla «Nunca se toca una entrada de la bóveda» se cumple.

**Arreglo** (en `MergePlan.kt`): una sola clave de comparación para el título y el usuario, la que ve el usuario:

```kotlin
private fun shownKey(s: String) =
    Normalizer.normalize(plainReportText(s, Int.MAX_VALUE), Normalizer.Form.NFC).trim().lowercase(Locale.ROOT)
private fun titleKey(title: String) = shownKey(title)
// y en Matcher: shownKey(e.username) en lugar de e.username.trim(); en NOTE, shownKey del título.
```

`plainReportText` está en el mismo paquete. Guardar no cambia: R9 sigue guardando el texto tal cual, y solo se compara de otra manera. El texto de las notas puede seguir comparándose con `trim()`. El usuario sí cambia de regla, porque hoy distingue mayúsculas (`ana` frente a `Ana`): con `shownKey` dejaría de distinguirlas. Si se quiere mantener, hay que usar `Normalizer.normalize(plainReportText(u, Int.MAX_VALUE), NFC).trim()` sin `lowercase`. Es decisión del controlador, porque el diseño dice que el usuario se compara «sin espacios al principio ni al final» y nada más.

**Test que lo da por arreglado:** `rt2_aTitleOrUserThatLooksTheSameIsADuplicate`, sin el `@Ignore`.

### RT-3 [Baja] La regla de la maestra nueva no ve el *leet* ni las letras de ancho completo

**Cómo lo he atacado.** He creado o cambiado la maestra a:
- «P4ssw0rd2024!», «C0ntr4s3ñ4!!», «P@$$w0rd1234», «Dr4g0n!2024» y «Pr1nc3ss2024»;
- «ｐａｓｓｗｏｒｄ12345» y «ｃｏｎｔｒａｓｅñａ2024», en ancho completo, que es lo que escribe un teclado chino o japonés en ese modo, y lo que deja un copiar y pegar.

**Lo que pasa:**
- `masterCore` (`vault/MasterPolicy.kt:50-60`) solo conserva las letras (línea 56). Las cifras y los símbolos que sustituyen letras desaparecen: «P4ssw0rd» se queda en «psswrd», que no está en ninguna lista;
- no aplica NFKC, así que «ｐａｓｓｗｏｒｄ» sigue siendo de ancho completo y no está en ninguna lista;
- la estimación (`estimateBits`, la de «Salud») da más de 50 bits a todas, y en ancho completo aún más, porque cada letra cuenta como un símbolo que no es ASCII;
- `newMasterProblem` (`MasterPolicy.kt:160-165`) devuelve `null`, y la maestra se acepta.

**Evidencia:**
- `RedTeamCryptoTest.rt3_aKnownWordInLeetIsRefusedAsANewMaster`, sin el `@Ignore`, falla con `«P4ssw0rd2024!» is one known word expected:<Esta contraseña es fácil de adivinar…> but was:<null>`;
- `RedTeamCryptoTest.resistsAKnownWordWithDigitsAndSymbolsAround` comprueba que «Password2024!», «PASSWORD2024!», «Contraseña2024!» (también descompuesta) y «dragon» con invisibles sí se rechazan hoy.

**Impacto.** Lo que pide la maestra es resistir un ataque sin conexión a una `.sgbak` o a un `vault.bin` robado. Argon2id hace caro cada intento, pero las variantes *leet* de una palabra común son lo primero que prueba cualquier diccionario con reglas (las reglas *leetspeak* de hashcat y John the Ripper). Es un hueco en una defensa que el propio código ya dice que «no garantiza que una maestra sea fuerte» (`MasterPolicy.kt:139-147`), y no un fallo explotable por sí solo. Severidad baja.

El dueño aceptó la regla como estimación («Barcelona1» pasa). Aun así, «P4ssw0rd2024!» es el caso obvio que la regla dice quitar, y ninguna tarea lo cubre.

**Arreglo** (en `MasterPolicy.kt`): pasar NFKC antes del plegado y comparar dos núcleos. El de hoy, y otro en el que las sustituciones habituales vuelven a ser letras:

```kotlin
private val LEET = mapOf('0' to 'o', '1' to 'i', '!' to 'i', '|' to 'i', '3' to 'e', '4' to 'a', '@' to 'a',
    '5' to 's', '$' to 's', '7' to 't', '+' to 't', '8' to 'b', '9' to 'g', '6' to 'g', '2' to 'z')
fun masterCore(candidate: String): String { /* igual, con Normalizer.normalize(candidate, NFKC) delante del lowercase */ }
internal fun masterCoreLeet(candidate: String): String =
    masterCore(Normalizer.normalize(candidate, Normalizer.Form.NFKC).map { LEET[it] ?: it }.joinToString(""))
// newMasterProblem: words.isNotEmpty() && (masterCore(candidate) in words || masterCoreLeet(candidate).trimLeetTail() in words)
```

Cuidado con las cifras del final («2024»): con el mapa de arriba se convierten en letras y estropean el núcleo («passwordzoza»). Hay que comparar también el núcleo *leet* sin las cifras finales, o mapear solo las cifras que tienen letras a los dos lados.

Antes de darlo por bueno hay que medir de nuevo la tasa de rechazo del Generador. El comentario de `newMasterProblem` da 1 de cada 1,1 millones a 20 caracteres, y con *leet* subirá. Es una decisión del dueño (CRY-A01), y el texto de `MASTER_TOO_WEAK` no cambia.

**Test que lo da por arreglado:** `rt3_aKnownWordInLeetIsRefusedAsANewMaster`, sin el `@Ignore`, más los tests del Generador de `MasterPolicyTest`, que deben seguir en verde.

## Ataques intentados y resistidos

Una línea por ataque, con el test que lo comprueba (los `RedTeam*` son nuevos y los demás ya existían).

**Importar de otra app:**
- **JSON muy anidado** (un millón de `[`): sale `IMPORT_JSON_TOO_DEEP` antes de analizar, en milisegundos. `RedTeamImportTest.resistsJsonNestingBomb`.
- **Corchetes escondidos en cadenas y tras escapes** (`"\\"`, `\"`), para desincronizar el escáner de profundidad del analizador: los dos leen igual. `resistsBracketsHiddenInStringsAndEscapes`.
- **Bomba de árbol JSON** (7,4 millones de valores en 15 MB): se rechaza por la estimación de 48 MiB antes de construir el árbol. `resistsJsonTreeBomb`.
- **CSV con una línea de 5 millones de comas, 20 001 filas o una comilla sin cerrar en 1 MB:** sale el error propio de cada caso, con memoria acotada. `resistsCsvColumnAndRowFloods`.
- **Archivos disfrazados:**
  - un `.zip` con cabecera CSV detrás, o una página HTML con `<script>` (no se muestra ni se analiza);
  - un UTF-8 *overlong*, o un sustituto escrito en UTF-8 (se rechaza; no se cambia por U+FFFD).

  `resistsDisguisedFiles`.
- **KDF de una exportación cifrada:**
  - PBKDF2 con 2·10⁹ o con 1 iteración;
  - Argon2id con 4 GiB, con 1 000 pasadas o con 64 hilos;
  - un número `6e5` o mayor que un `Int`;
  - scrypt con 2 GiB, con N que no es potencia de 2, con N ≥ 2^(16·r) o con p = 64.

  Todo se rechaza **antes de calcular** (menos de 2 s en total). `resistsKdfBombsBeforeAnyWork`, más `ImportKdfTest`, `BitwardenCryptoTest.kdfOutOfRangeIsRefusedBeforeWork` y `AegisCryptoTest`.
- **Oráculo de relleno o de MAC en el CBC de Bitwarden:** se comprueba el HMAC antes de descifrar y se compara en tiempo constante, y un MAC que falla da el mismo texto que una contraseña equivocada. Sin la clave del MAC no se llega al relleno. `BitwardenCryptoTest.everyByteFlippedFailsTheHmacBeforeAnyCbcDecryption` y `theMacsAreComparedInConstantTime`.
- **`otpauth-migration://` con un *varint* de 11 bytes, una longitud de 2³⁵, una longitud negativa, grupos, el campo 0 o 101 cuentas:** devuelve `null`; nunca hay excepción ni reserva de memoria. `resistsOtpMigrationLengthAndVarintTricks`.
- **Inyección de ajustes del 2FA por el emisor o la cuenta** (`Banco&digits=10&period=1&secret=…`, `ana?algorithm=MD5#x`): las partes se codifican cada una en su parámetro, y el código sale con 6 dígitos, 30 s y SHA1. `resistsSettingsInjectedThroughTheNamesOfA2faCode`.
- **Bidi, invisibles, NUL y torres de acentos en lo que se enseña:** `plainReportText` los quita y corta a 3 marcas. `resistsRtlAndInvisibleCharactersInWhatIsShown`.
- **Un plan de importar viejo frente a una bóveda que ha cambiado (TOCTOU):** `ApplyResult.Stale`. `ImportStateTest` y `MergePlanTest`.
- **Una entrada fuera del duplicado elegido, o una de tipo IDENTITY o CONTACT:** nunca cambia (`replaceWith` devuelve la misma). `MergePlanTest`.
- **Fórmulas de CSV** (`=HYPERLINK(...)`): Clavero no exporta CSV en ningún sitio. He buscado `csv` fuera de `imports/` y no aparece. No aplica.

**Formatos de Clavero:**
- **`.sgenc` (STREAM):**
  - truncado justo en el borde de un registro;
  - dos registros intercambiados, uno repetido, o uno añadido tras el último;
  - los registros de otro archivo con la misma contraseña;
  - el registro del nombre de otro archivo (`../../shared_prefs/x.xml`) delante de los datos.

  Todo falla con `CryptoFailure`. `RedTeamCryptoTest.resistsStreamTruncationReorderAndSplice`.
- **Nombres de archivo hostiles:** `../`, `..\`, RLO, NUL, `.` y `..`, solo invisibles, 10 000 caracteres, 200 `€`, 300 sustitutos sueltos, NEL y U+2028. `safeFileName` y `sanitizeReceivedName` dan siempre un nombre sin ruta, sin controles y de 255 bytes o menos. `resistsHostileFileNames`, más `FileMessageTest` y `PkActionsTest`.
- **«Compartir» con un nombre manipulado:**
  - `../copia-de-foto.jpg`, `…/../../x`, `.apk`, NUL o mayúsculas: se rechaza antes de escribir;
  - la copia va en `compartir/<128 bits>/`.

  `resistsShareCopyNameInjection`.
- **SLIP-0039:**
  - una parte de otro reparto;
  - una parte con un bit cambiado y **checksum RS1024 válido** (la he vuelto a codificar);
  - la misma parte dos veces;
  - exponente 15 (327 millones de iteraciones);
  - 300 partes, una parte de 10 000 caracteres o una frase que no es ASCII.

  Todo se rechaza; el digest en x = 254 detecta la parte falsificada y el exponente se rechaza antes de PBKDF2. `resistsForeignTamperedAndCostlySlip39Parts`. He revisado `Gf256.mul` (sin tablas ni ramas sobre secretos), el Feistel y el digest contra la especificación SLIP-0039, sin encontrar diferencias.
- **Copia `.sgbak` y «Comprobar una copia»:**
  - la carpeta `cacheDir/comprobar/run-*` se borra al terminar y al arrancar (`removeBackupCheckLeftovers`);
  - los adjuntos se quedan cifrados en disco y se comprueba el SHA-256 autenticado.

  `BackupCheckTest`. La copia descifrada de «Descifrar archivo» (`cacheDir/dec*.tmp`) se borra al arrancar (`SeguridadApp.kt:42`).
- **Bajar el coste de Argon2id (A8)** y **parámetros de KDF absurdos en una cabecera** (`KdfParams.checkSane`): `CryptoTest` y `VaultStoreTest`, existentes.

**Bloqueo y huella:**
- **Atrasar el reloj para estirar los 7 días (A11):** sale `CLOCK` con la hora más alta vista, y también antes de `masterCheckedAt`, que va autenticado en el AAD. `RedTeamCryptoTest.resistsClockSetBackToStretchTheSevenDays`.
- **Carrera de la fila de `BiometricVault`:**
  - no se solapan dos operaciones;
  - un `disable` cancela una activación, y un `enable` posterior va detrás.

  `KeyOpsQueueTest.operationsNeverOverlap`, `disableDuringEnableCancelsTheEnable`, `disableAlsoDropsAnEnableStillWaitingItsTurn` y `anEnableAskedAfterADisableRunsAfterIt`.
- **La clave del Keystore:**
  - `setUserAuthenticationRequired(true)`, con `AUTH_BIOMETRIC_STRONG` y validez 0 en Android 11 o posterior;
  - se invalida al registrar una huella nueva;
  - el diálogo solo admite `BIOMETRIC_STRONG`, con `CryptoObject` y sin PIN del móvil (`android/BiometricVault.kt:117-133` y `:204`).

  Un ladrón que sepa el PIN del móvil no abre la bóveda.

**Otras apps:**
- **Componentes exportados.** He volcado el manifiesto fusionado con `aapt2 dump xmltree`:
  - solo `MainActivity` (el lanzador) y `ProfileInstallReceiver`, que está protegido por `android.permission.DUMP`;
  - el `FileProvider` y `androidx-startup` no están exportados;
  - el `CaptureActivity` de la biblioteca no está exportado.

  `PreviewActivity` sale exportada en el APK de **depuración**. En CI, `scripts/check-manifest.sh --release` comprueba que la versión *release* no la tiene.
- **Permisos:** INTERNET y CAMERA, más USE_BIOMETRIC, USE_FINGERPRINT y el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` que añaden AndroidX. Son los mismos que en la 1.3.1.
- **Abrir enlaces o *intents* desde un QR o una entrada** (`intent://`, `javascript:`): no hay ningún `ACTION_VIEW` ni `startActivity` con datos de fuera. Los únicos `startActivity` son `ACTION_SEND` de texto público, el selector de imagen y `CREATE_DOCUMENT`; `openUri` solo recibe dos constantes (`SettingsScreen.kt:491-492`).
- **Capturas, recientes y toques ocultos:** `FLAG_SECURE`, `filterTouchesWhenObscured` y `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` en la ventana (`android/SecureWindow.kt:24-26`), y también en los diálogos y menús (`:38-47`), que heredan `FLAG_SECURE` (`SecureFlagPolicy.Inherit`, el valor por defecto). El escáner QR tiene su propia subclase con `FLAG_SECURE` y filtro de toques (`android/QrScanner.kt:15-16`).
- **Portapapeles:**
  - `EXTRA_IS_SENSITIVE` en Android 13 o posterior, y el aviso A14 en Android 8 a 12;
  - la etiqueta propia;
  - se borra solo si sigue siendo lo copiado;
  - una copia que quedó al cerrarse el proceso se borra al volver (`android/Clipboard.kt`).

  `ClipboardClearingTest` y `ClipboardNoticeTest`.
- **Credenciales en un enlace del QR** (`https:\\ana:clave@…`, `https:ana:clave@…`, `%40`, un tabulador dentro, `ftp`, `intent`): el lector las ve todas, así que se aplica la regla de borrarlas. `RedTeamCryptoTest.resistsUserInfoHiddenFromTheSecretRule`.

**Móvil robado y copias:**
- `allowBackup="false"`, y `dataExtractionRules` y `fullBackupContent` excluyen todos los dominios en la nube y en la transferencia entre móviles (`res/xml/*.xml`);
- no hay ningún `Log.*`, `println` ni `printStackTrace` en `app/src/main/java`;
- no hay ningún `rememberSaveable` ni `SavedStateHandle` con secretos (los que hay guardan URI, nombres públicos, tarjetas públicas o firmas);
- `settings.json` guarda la hora más alta vista sin autenticar, pero solo root puede editarla y está fuera de las copias.

**Red:**
- **TLS:**
  - no hay `network_security_config`, así que con el `targetSdk` actual no se confía en CA del usuario ni se admite texto en claro;
  - el bloqueo de eMudhra se aplica dos veces (CRY-B01).

  `DistrustedCertificatesTest` y `HttpClientTest.noRedirects`.
- **k-anonimato:** solo salen 5 caracteres del SHA-1, con `Add-Padding`. Una respuesta de más de 2 MB se rechaza, contando lo ya descomprimido por OkHttp, y nunca se sigue una redirección. `PwnedPasswordsTest.sendsOnlyFivePrefixChars` y `oversizedResponseThrows`.

**Cadena de suministro (`.github/workflows/build-apk.yml`):**
- sin `pull_request_target`;
- las acciones van fijadas por SHA, con `permissions: contents: read`;
- `persist-credentials: false`;
- no hay datos de la petición en `run:` (la base del PR y `before` pasan por variables de entorno);
- se comprueba la suma del *wrapper*;
- los secretos solo existen en el paso que empaqueta y solo con una etiqueta `v*` o al lanzarlo a mano desde `main`, en el entorno `firma`;
- se verifica la huella del certificado de subida.

El riesgo que queda, que Gradle y sus *plugins* corren en el paso que tiene los secretos, ya está apuntado en el propio archivo y en la auditoría de la 1.3.0.

## Necesita prueba en el móvil

1. **RT-1 en un navegador real:** copiar `https://evil.example\@banco.es/login` en Chrome y en Firefox de Android y comprobar que van a `evil.example`. La regla de WHATWG lo dice; falta confirmarlo en los navegadores del dueño.
2. **`FLAG_SECURE` en los diálogos de importar y en el selector de duplicados:** con la vista previa abierta, hacer una captura y abrir Recientes. Debe salir en negro.
3. **Toques ocultos:** con una app de superposición (por ejemplo, un filtro de luz azul) encima, tocar «Importar» y «Reemplazar». No deben responder.
4. **Portapapeles en Android 13 o posterior:** copiar una contraseña y comprobar que el teclado (Gboard) enseña «Contenido oculto».
5. **La muerte del proceso a mitad de «Comprobar una copia» y de «Descifrar archivo»:** con `adb shell am kill io.github.egt17baa.seguridad`, abrir otra vez la app y comprobar con `adb shell run-as … ls cache` que no queda `comprobar/run-*` ni `dec*.tmp`.
6. **`adb backup` y la transferencia entre móviles:** comprobar que no se lleva `files/` ni `shared_prefs/`.

## Revisión experto-seguridad-cripto

- **Primitivas:** solo AES-GCM, AES-CBC con HMAC en ese orden (Bitwarden), PBKDF2, scrypt, Argon2id, HKDF, Ed25519 y X-Wing/HPKE de Bouncy Castle o `javax.crypto`. No hay criptografía propia (`imports/ImportKdf.kt`, `tools/slip39/*.kt`, `crypto/FileCrypto.kt`).
- **Tiempo constante:** `MessageDigest.isEqual` en los MAC de Bitwarden (`imports/BitwardenCrypto.kt:151`), en el digest de SLIP-0039 (`tools/slip39/Shamir.kt:76`) y en los hashes de los adjuntos (`vault/BackupCheck.kt`); `Gf256.mul` sin tablas (`tools/slip39/Gf256.kt:35-46`).
- **Nonces:** STREAM con prefijo y contador, y nunca reutiliza (`crypto/FileCrypto.kt:81` y `:124`, el tope del contador); GCM con nonce aleatorio por escritura.
- **Topes de KDF antes de calcular:** `checkPbkdf2Iterations`, `checkScrypt` (también N < 2^(16·r)) y `checkSane` (`imports/ImportKdf.kt:58-89`, `crypto/Crypto.kt:62-74`).
- **Borrado de secretos:** `Secrets.wipe` en un `finally` (`imports/ImportKdf.kt:103-125`). Los `String` de Kotlin no se pueden poner a cero, que es un riesgo aceptado en el diseño.
- **Los tres hallazgos** son de lógica de comparación y de política (`imports/MergePlan.kt:353-369`, `vault/MasterPolicy.kt:50-60` y `:163`), no de primitivas. Ninguno rompe un formato: el arreglo de RT-1 y RT-2 solo cambia cómo se compara, y el de RT-3 solo afecta a una maestra **nueva**.

## Revisión Ingeniero Móvil Senior

- **Manifiesto fusionado:** un solo componente exportado útil, `MainActivity`, sin filtros de datos. El `FileProvider` no está exportado y da permisos por URI (`AndroidManifest.xml:36-44`; volcado de `aapt2` en la ejecución de este informe).
- **Ventanas:** `FLAG_SECURE`, el filtro de toques y la exclusión de *autofill* en la actividad y en cada diálogo y menú (`android/SecureWindow.kt:24-47`).
- **Estado:**
  - los secretos de importar, «Comprobar una copia», SLIP-0039 y los laboratorios viven en ViewModel o en sesiones, nunca en `rememberSaveable` (comentarios en `ui/screens/ImportScreen.kt:157`, `BackupCheckScreen.kt:86`, `SecretShareScreen.kt:107` y `HashLabScreen.kt:118`; búsqueda de `rememberSaveable` hecha);
  - los resultados que llegan tarde se descartan con *run tokens* (`imports/ImportSession.kt:125-162`).
- **Concurrencia:** `KeyOpsQueue` sobre un solo hilo, no reentrante, con `disable` que cancela una activación (`tools/KeyOpsQueue.kt`); los KDF van fuera del hilo principal, con `SingleRun`.
- **Archivos temporales:** se borran al terminar, al cancelar y al arrancar (`SeguridadApp.kt:42`, `:54` y `:63`).
- **Para RT-1 y RT-2:** el arreglo es Kotlin puro en `imports/`, se prueba también en `jvm-check` y no toca la interfaz. La fila del duplicado (`ImportScreen.kt:664`) puede enseñar además el host, con un cambio de Compose de una línea.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo: los tests y este informe no cambian ningún texto de la interfaz. Si se aplica el arreglo de RT-3, `MASTER_TOO_WEAK` no cambia. Si la fila del duplicado enseña el host (RT-1), esa línea es texto de la interfaz y debe pasar por esa revisión.

## Puerta

Ejecutada en el *worktree* el 8-11-2026, sobre `2173da7` más estos tests:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=2`: **BUILD SUCCESSFUL**;
- `./gradlew -p jvm-check test --no-daemon --max-workers=2`: **BUILD SUCCESSFUL**.

Los tests del equipo rojo corren en las dos suites, con el mismo resultado:
- `RedTeamCryptoTest`: 8 tests, 1 ignorado (RT-3) y 0 fallos;
- `RedTeamImportTest`: 12 tests, 2 ignorados (RT-1 y RT-2) y 0 fallos.

Sin el `@Ignore`, los tres `rtN_` fallan con los mensajes citados en cada hallazgo; lo comprobé con `jvm-check` antes de ponerlo.
