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
| RT-1 | Importar de otra app (plan de fusión) | `urlHost` toma `https://evil.example\@banco.es/` por banco.es; un navegador va a evil.example. Esa entrada pasa por duplicado de «Banco», y con «Reemplazar» la entrada de la bóveda se queda con la URL y la contraseña del atacante | Baja: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:L/A:N`) | MASVS-CODE-4 | Arreglado (`0089b90`) |
| RT-2 | Importar de otra app (plan de fusión) | Un título o un usuario con invisibles («Ban\u200Bco», «an\u00ADa») o con el acento descompuesto («Cafe\u0301») no es duplicado, pero se ve exactamente igual (`plainReportText` y la fuente lo igualan). El resultado es una entrada sombra junto a la buena | Baja: 3,3 (`AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N`) | MASVS-CODE-4, MASVS-PLATFORM-3 | Arreglado (`0089b90`) |
| RT-3 | Maestra nueva (CRY-A01) | La regla de «una sola palabra conocida» no ve una palabra en *leet* («P4ssw0rd2024!», «Dr4g0n!2024») ni en letras de ancho completo («ｐａｓｓｗｏｒｄ12345»). Las dos dan más de 50 bits en la estimación y se aceptan | Baja (sin CVSS: debilita una defensa contra un ataque de diccionario sin conexión a una copia robada; no es un fallo explotable por sí solo) | MASVS-AUTH-1, MASVS-CRYPTO-2 | Arreglado (`0089b90`) |

Recuento: **3 hallazgos explotados, los tres de severidad baja**, y **ninguno crítico, alto ni medio**. Los tres se arreglaron después en `0089b90` (abajo, «Arreglos»); el informe publicado, con el estado al día, es `docs/auditorias/2026-11-08-ataque-1.4.0.md` (regla R39). Hay además 17 tests de ataques resistidos, en los dos archivos, y la lista de lo intentado más abajo.

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

## Arreglos (8-11-2026)

Los tres hallazgos están arreglados en la misma rama, con TDD: primero se quitó el `@Ignore` de cada `rtN_` y se vio fallar (los tres, con `jvm-check`), y después se hizo el arreglo. Los tres `rtN_` están ahora en verde, sin `@Ignore`, en las dos suites, y se quedan como tests de regresión. El detalle, las medidas y las revisiones están en `redteam-fixes-report.md`, en esta misma carpeta.

- **RT-1 (decisión R34): arreglado.** `urlHost` lee la autoridad con el mismo lector de enlaces que el Inspector de QR, `parseLink`, que sigue al navegador: en un enlace web (`http`, `https`, `ftp`, `ws`, `wss`), `\` cierra la autoridad como `/`, los tabuladores se quitan y el host se decodifica y se mapea como en UTS #46. Un texto sin `://` que no empieza por un esquema web se lee como lo leería la barra de direcciones. Sigue siendo trabajo de cadenas que no lanza excepciones ni resuelve nada.
  - Código: `app/src/main/java/app/seguridad/imports/MergePlan.kt:372-379` y `tools/qr/LinkSignals.kt:25` (`SPECIAL_SCHEMES`, ahora `internal`, para que los dos lectores usen la misma lista).
  - Tests: `RedTeamImportTest.rt1_aUrlThatGoesToAnotherSiteIsNotADuplicate` en verde; `rt1Impact_…` comprueba ahora lo contrario de antes: «Reemplazar» no toca la entrada de la bóveda y la fila entra como nueva. En `MergePlanTest`, `theHostIsTheOneABrowserGoesTo`, `theHostAgreesWithTheQrReaderForEveryWebLink` (20 000 enlaces al azar, sin excepciones) y `aUrlOfAnotherSiteIsNotADuplicateAndReplaceCannotTakeIt`. `loginDuplicatesByTitleUserAndHost` sigue en verde.
  - La sugerencia de enseñar el host en la fila del duplicado (`ImportScreen.kt:664`) no se aplica: el controlador pidió no cambiar textos de la interfaz en esta ronda.
- **RT-2 (decisión R34): arreglado.** El título y el usuario se comparan con `compareKey` (`imports/ImportModel.kt:139`): lo que enseña `plainReportText`, sin su corte, en NFC y con los espacios seguidos como uno solo y ninguno en los extremos. El título no distingue mayúsculas (`Locale.ROOT`) y el usuario sí, por decisión del controlador. En las notas, solo el título pasa por esa clave; el texto se compara como antes. Lo guardado no cambia (R9, R10).
  - Código: `imports/MergePlan.kt:356` (`titleKey`), `:359` (`userKey`), `:398`, `:407`, `:413` y `:418`.
  - Tests: `RedTeamImportTest.rt2_aTitleOrUserThatLooksTheSameIsADuplicate` en verde; en `MergePlanTest`, `aTitleOrUserThatLooksTheSameIsADuplicate`, `aNoteWhoseTitleLooksTheSameIsADuplicateAndItsTextIsComparedAsBefore` y `aLookAlikeIsStoredAsItCameWhenTheUserKeepsBoth`; en `ImportModelTest`, `compareKeyIsWhatIsShownInNfcWithTheSpacesFolded` y `compareKeyIsNotCutNeverThrowsAndIsCheap`.
- **RT-3 (decisión (h) del dueño, 8-11-2026, «Rechazarlas»): arreglado.** Solo para una maestra nueva (crear la bóveda y cambiar la maestra), con el mismo texto `MASTER_TOO_WEAK`. Abrir la bóveda, la huella e importar una copia no cambian.
  - `masterCore` pasa NFKC antes de las minúsculas y el plegado (`vault/MasterPolicy.kt:52-63`), así que las letras de ancho completo son las normales.
  - Comprobación *leet* nueva, `isKnownWordInLeet` (`vault/MasterPolicy.kt:101-122`): la contraseña es una palabra conocida si se puede partir en `inicio + palabra + final`, donde el inicio y el final no tienen letras, y la palabra, con las sustituciones habituales devueltas a letras (`leetLetter`, `:76-86`; el «2» nunca es «z»), está en las listas. Tiene un tope de 128 puntos de código (`LEET_MAX_CODE_POINTS`, `:69`) y no usa expresiones regulares. La regla está en `:233`.
  - Tests: `RedTeamCryptoTest.rt3_aKnownWordInLeetIsRefusedAsANewMaster` en verde y `resistsAKnownWordWithDigitsAndSymbolsAround`, que sigue en verde; seis tests nuevos en `MasterPolicyTest` (`:237-296`), y todos los de antes, también los del Generador, siguen en verde.
  - La tasa de rechazo del Generador, medida de nuevo: con 20 caracteres (la longitud por defecto) sigue en torno a 1 de cada 1,1 millones, lejos del límite de 1 de cada 100 000. Las cifras están en el KDoc de `newMasterProblem` (`:222-227`) y en el informe de los arreglos.

La puerta de la rama, con los arreglos y sin ningún test ignorado: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` en verde (1 785 tests) y `./gradlew -p jvm-check test` en verde (1 780 tests).

## Segundo ataque (8-11-2026)

**Alcance.** La segunda ronda de R32 (decisiones (d) y (f) del dueño) ataca el código final de la rama, en `b1902ed`, después de la ronda de arreglos de la revisión de la rama:
1. vuelvo a atacar, con ataques nuevos, cada arreglo de RT-1 a RT-3 (`0089b90`);
2. ataco el código nuevo de esa ronda:
   - `crypto/KdfLock` y `deriveBlocking` (R38 y R40);
   - las comprobaciones de la cabecera antes de pedir la contraseña de una exportación (A M-3);
   - la fila del duplicado, con «usuario · host» (A M-4, `duplicateSubline`);
   - el tope de 65 536 caracteres (`PASSWORD_FIELD_MAX_CHARS`);
   - la carrera del progreso del Laboratorio de hashes (B N-2);
3. repaso rápido todas las entradas del primer informe.

Los tests nuevos están en `app/src/test/java/app/seguridad/redteam/RedTeamSecondAttackTest.kt`, con el mismo criterio que antes: un `rtN_…` falla mientras el hallazgo siga abierto y lleva `@Ignore` solo para la puerta; un `resists…` pasa. He comprobado que los cuatro `rtN_` nuevos fallan sin el `@Ignore`, con `jvm-check`, y he copiado sus mensajes abajo.

### Resumen del segundo ataque

| RT | Superficie | Hallazgo | Severidad (CVSS 3.1) | MASVS | Estado |
|---|---|---|---|---|---|
| RT-4 | Importar (plan de fusión y fila del duplicado) | Una URL de otro esquema (`javascript://banco.es/…`, `intent://banco.es/…`, `file://banco.es/…`, `http://banco.es/`) es duplicado de `https://banco.es/`, y la fila enseña lo mismo para todas («ana · banco.es»). Con «Reemplazar», la entrada pasa en silencio a otro esquema | Baja: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:L/A:N`) | MASVS-CODE-4 | Explotado (test) |
| RT-5 | Maestra nueva (RT-3, decisión (h)) | El *leet* con las teclas de un teclado español se acepta: «Pr¡nc3ss2024», «C0ntra$€na2024!», «C0ntr4s€ñ4!!», «Dr4gøn!2024» y «P4$$wørd2024». `leetLetter` solo conoce cifras ASCII y `@ $ + ! \|` | Baja (sin CVSS, como RT-3) | MASVS-AUTH-1 | Explotado (test) |
| RT-6 | Mostrar y comparar (RT-2, R9 y R10) | Hay puntos de código que no se dibujan y que `plainReportText` conserva, así que `compareKey` tampoco los iguala: U+2800 (Braille en blanco), U+17B4 y U+17B5, los selectores de variante U+FE00–U+FE0F y U+E0100–U+E01EF, y U+180B–U+180D y U+180F. La entrada sombra de RT-2 vuelve, con otro carácter | Baja: 3,3 (`AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N`, como RT-2) | MASVS-CODE-4, MASVS-PLATFORM-3 | Explotado (test) |
| RT-7 | Comparar (RT-2) | Informativa, a decidir por el controlador: NFC no pliega los caracteres de compatibilidad que se ven igual, como la ligadura «ﬁ» y «fi», o «Ⅱ» y «II». NFKC los plegaría. Otras parecidas que tampoco pliega NFKC solo se anotan aquí | Informativa | MASVS-CODE-4 | Anotado (test) |

Recuento del segundo ataque: **3 hallazgos explotados de severidad baja (RT-4, RT-5 y RT-6) y 1 informativo (RT-7)**, y ninguno crítico, alto ni medio. Hay 9 tests nuevos de ataques resistidos. Los arreglos de RT-1, RT-2 y RT-3 aguantan contra todo lo que he probado alrededor; lo que encuentro son huecos al lado, no regresiones.

### RT-4 [Baja] Una URL de otro esquema pasa por duplicado de la web, y la fila no lo dice

**Cómo lo he atacado.** En la bóveda está «Banco» / «ana» / `https://banco.es/`, y en el archivo, la misma fila con uno de estos valores en la columna `url`:
- `javascript://banco.es/%0Aalert(1)`;
- `intent://banco.es/#Intent;scheme=https;package=com.example.evil;end`;
- `file://banco.es/sdcard/x.html`;
- `http://banco.es/login`.

**Lo que pasa:**
- `urlHost` (`imports/MergePlan.kt:382-389`) solo se queda con el host;
- `parseLink` lee como jerárquico cualquier `esquema://` (`tools/qr/LinkSignals.kt:70`), así que las cuatro URL tienen el host `banco.es` y todas son **duplicado** de la entrada de la bóveda;
- `duplicateSubline` (`MergePlan.kt:400-406`) enseña «ana · banco.es» para las cuatro;
- con «Reemplazar», `replaceWith` toma la URL del archivo, y la entrada pasa de `https` a `http`, o a un enlace que ni siquiera es una página web.

**Evidencia:** `RedTeamSecondAttackTest.rt4_aUrlOfAnotherSchemeIsNotTakenSilentlyForTheWebOne`, sin el `@Ignore`, falla con: `«javascript://banco.es/%0Aalert(1)»: a duplicate whose row «ana · banco.es» hides the scheme`.

**Impacto.** Bajo, como RT-1:
- Clavero no abre ningún enlace (decisión (c)), y los navegadores quitan `javascript:` al pegar en la barra de direcciones;
- el caso más real es bajar de `https` a `http`. Quien copie la URL y la abra en una red hostil, con un dominio que no esté en la lista HSTS precargada, puede acabar en una página falsa;
- hace falta que el usuario importe el archivo del atacante y elija «Reemplazar». La contraseña buena queda en el historial.

**Arreglo**, en `imports/MergePlan.kt` (cualquiera de las dos cosas, o las dos):
1. **En la comparación:** que el esquema forme parte de la clave cuando no es `http` ni `https`, para que una URL de otro esquema nunca sea duplicado de una web:
   ```kotlin
   val web = scheme == null || scheme == "http" || scheme == "https"
   val host = parseLink(asLink).host?.removePrefix("www.").orEmpty()
   return if (web) host.ifEmpty { t.lowercase(Locale.ROOT) } else "$scheme://$host"
   ```
2. **En la fila:** que `duplicateSubline` enseñe el esquema cuando no es `https` («ana · http://banco.es», «ana · javascript://banco.es»).

Si se decide que `http` y `https` sigan contando como el mismo sitio, la fila tiene que decir `http://`. Cambiar la fila es texto de la interfaz, así que va a la lista del Abogado.

**Test que lo da por arreglado:** `rt4_…`, sin el `@Ignore`. Admite cualquiera de los dos arreglos: que no haya duplicado, o que la fila diga el esquema.

### RT-5 [Baja] El *leet* de un teclado español: «¡», «€» y «ø»

**Cómo lo he atacado.** He creado una maestra nueva con «Pr¡nc3ss2024», «C0ntra$€na2024!», «C0ntr4s€ñ4!!», «Dr4gøn!2024» y «P4$$wørd2024».

**Lo que pasa:**
- `leetLetter` (`vault/MasterPolicy.kt:89-99`) solo convierte `0 3 4 5 6 7 8 9`, `@ $ +` y `1 ! |`;
- «¡» (tecla propia en el teclado español) y «€» (AltGr+E) no son letras, así que `masterCore` los quita y la palabra pierde una letra: «prncess», «contrasna»;
- «ø» es una letra que no está en ninguna palabra;
- la estimación da más de 50 bits a todas, y `isKnownWordInLeet` no encuentra la palabra. La maestra se acepta.

**Evidencia:** `RedTeamSecondAttackTest.rt5_aKnownWordInSpanishKeyboardLeetIsRefused`, sin el `@Ignore`, falla con: `«Pr¡nc3ss2024» is one known word expected:<Esta contraseña es fácil de adivinar…> but was:<null>`.

**Impacto.** El mismo que RT-3: debilita una defensa contra un diccionario con reglas que ataque sin conexión una copia robada. «€» por «e» y «¡» por «i» están en las tablas de *leet* habituales, y en una app en español están a una tecla.

**Arreglo** (en `MasterPolicy.kt:89-99`):
- añadir `'¡'.code -> 'i'`, `'€'.code -> 'e'`, y `'ø'.code` y `'Ø'.code -> 'o'`. Hay que mirar «Ø» antes de NFKC y de pasar a minúsculas: los pedazos se hacen sobre el texto en NFKC, que conserva las mayúsculas;
- hay que medir otra vez la tasa de rechazo del Generador solo si su alfabeto tiene alguno de estos caracteres. Si es solo ASCII, las cifras no cambian.

Las sustituciones de varios caracteres («()» por «o», «vv» por «w», «|<» por «k») no las cubre la regla de un punto de código por letra. Las anoto como informativas: cubrirlas obliga a cambiar el algoritmo, y lo decide el dueño (decisión (h)).

**Test que lo da por arreglado:** `rt5_…`, sin el `@Ignore`, más los tests del Generador en `MasterPolicyTest`.

### RT-6 [Baja] Caracteres invisibles que `plainReportText` conserva: la entrada sombra con otro carácter

**Cómo lo he atacado.** En el archivo: «Banco» seguido de, o con dentro, U+2800, U+17B4, U+17B5, U+FE00, U+FE0F, U+E0100, U+180B o U+180F, con el usuario y la URL de la bóveda.

**Lo que pasa:**
- `plainReportText` (`imports/ImportModel.kt:107`) quita la categoría Cf y una lista fija (`INVISIBLE_NOT_FORMAT`, `:159`), y conserva hasta tres marcas combinantes;
- U+2800 es un símbolo (So) y los demás son marcas combinantes (Mn): ninguno se quita, y ninguno se dibuja;
- «Banco» con cualquiera de ellos se ve como «Banco» y no es duplicado de «Banco». Es la entrada sombra de RT-2;
- la lista de nombres de archivo, `isHiddenInName` (`crypto/FileCrypto.kt`), ya quita U+17B4, U+17B5 y U+FE00–U+FE0E. Las dos listas no coinciden.

**Evidencia:** `RedTeamSecondAttackTest.rt6_invisibleCodePointsAreNotShownNorKeptApart`, sin el `@Ignore`, falla con: `U+2800 is shown expected:<Banco[]> but was:<Banco[⠀]>`.

**Impacto:** el mismo que RT-2.

**Arreglo** (en `imports/ImportModel.kt:159`):
- añadir 0x2800, 0x17B4 y 0x17B5 a `INVISIBLE_NOT_FORMAT`;
- tratar como invisibles los rangos 0xFE00–0xFE0F, 0xE0100–0xE01EF, 0x180B–0x180D y 0x180F.

U+FE0F (presentación de emoji) se puede dejar solo detrás de un emoji, como `withoutLooseSelectors` en `FileCrypto.kt`, o quitar siempre: sin él, un emoji se dibuja como texto, igual que ya se acepta al quitar el ZWJ. Mejor todavía es una sola lista de invisibles para los nombres y para lo que se enseña (CIFRADO-2 y R9).

**Test que lo da por arreglado:** `rt6_…`, sin el `@Ignore`, más `ImportModelTest` y `MergePlanTest`.

### RT-7 [Informativa] Caracteres de compatibilidad que se ven igual (a decidir por el controlador)

`compareKey` (`imports/ImportModel.kt:139-153`) normaliza con NFC, que conserva los caracteres de compatibilidad:
- la ligadura «ﬁ» (U+FB01) se ve como «fi»;
- «Ⅱ» (U+2161) se ve como «II».

Con NFKC, como ya hace `masterCore` desde RT-3, compararían igual, y también las letras de ancho completo y las matemáticas (`𝐁`). Esas dos últimas se ven distintas, pero no hay motivo para separarlas. El precio es que «²» pase a ser «2», sin consecuencias porque solo cambia la comparación (R9 sigue guardando el texto tal cual).

Ni NFC ni NFKC pliegan otras parecidas, y solo las anoto: las letras de otras escrituras («а» cirílica), las variantes del guion (U+2010 y U+2212), U+2024 en lugar de «.» y U+2019 en lugar de «'». La regla R9 ya acepta las letras de otras escrituras.

Test: `RedTeamSecondAttackTest.rt7_compatibilityLookAlikesCompareEqual`, con `@Ignore("RT-7: open (Info, the controller decides), …")`, que falla con `«Proﬁle» vs «Profile» expected:<Pro[fi]le> but was:<Pro[ﬁ]le>`. Si el controlador decide no plegarlas, se borra el test.

### Ataques del segundo ataque intentados y resistidos

Todos están en `RedTeamSecondAttackTest`.

**Alrededor del arreglo de RT-1:**
- **Confusiones de la autoridad:** `\` (también en mayúsculas, con `\\` y con puerto), `%5C`, tabuladores, `%09` y saltos de línea dentro, U+3000, el punto ideográfico, `#@`, `?@`, `/@`, la información de usuario sin esquema y con `//`, `%63`, el host en ancho completo, `WWW.` con punto final, IDN con «а» cirílica (sale `xn--`) y `%40` en el host. `urlHost` da siempre el host al que va el navegador, y solo hay duplicado cuando ese host es banco.es. `resistsAuthorityConfusionsAroundTheRt1Fix`.

**Fila del duplicado:**
- **Suplantar con el host:** el host se enseña en ASCII (`xn--`), sin bidi ni invisibles, y es el que lee el navegador. Un usuario que trae « · banco.es» deja la fila terminada en « · evil.example», el host real; además, solo hay fila de duplicado cuando el usuario coincide con el de la bóveda. `resistsDuplicateRowSpoofing`.

**Alrededor del arreglo de RT-2:**
- **Lo que `compareKey` ya pliega:** U+200B, U+00AD, U+2062, U+180E, los rellenos de Hangul, U+034F, U+061C, U+200E, la etiqueta U+E0020, el tabulador, U+3000, NBSP y el acento descompuesto. `resistsLookAlikesThatCompareKeyFolds`.

**Alrededor del arreglo de RT-3:**
- **El *leet* que la regla ya ve:** «1» por «i» y por «l» en la misma palabra («M1ll1on», «Br1ll1ant», «L1b3rty», «F4m1l1a»), invisibles y guiones dentro, mayúsculas y ancho completo mezclado;
- **sin rechazos de más:** tres contraseñas al azar y una frase pasan.

`resistsLeetAroundTheRt3Fix`.

**`KdfLock` (R38 y R40):**
- **Inanición y cancelación:**
  - con el cerrojo cogido por una «importación hostil» cuyo llamador se cancela, el cerrojo sigue cogido hasta que la derivación termina por dentro;
  - después entran, en el orden en que llegaron, el desbloqueo y la comprobación: el `Mutex` es justo;
  - un llamador cancelado mientras espera no ejecuta nunca su bloque.

  `resistsKdfLockStarvationAndCancellation`.
- **`deriveBlocking` en el hilo principal:** lanza `IllegalStateException` sin esperar ni ejecutar el bloque. `resistsDeriveBlockingOnTheMainThread`.
- **Una importación hostil que se queda con el cerrojo:** el coste lo acotan `checkPbkdf2Iterations`, `checkScrypt` y `checkSane` antes de calcular, y se llega como mucho a una derivación por intento (`IMPORT_KDF_RUN`). Medido en este contenedor (JDK 21, 4 núcleos, 512 MiB de montón) con los valores más altos que se admiten:
  - PBKDF2 con 2 000 000 de iteraciones: 1,27 s;
  - scrypt con N = 2^17, r = 8 y p = 4: 1,61 s;
  - Argon2id con 128 MiB, t = 10 y p = 4: 1,22 s.

  En un móvil serán varias veces más, unos segundos. Durante ese tiempo un desbloqueo espera, y es el coste aceptado en R38. Lo anoto como informativo y no le doy número.
- **Lectura de un JSON grande dentro del cerrojo:** con contraseña, todo `readImport` corre dentro de `KdfLock` (`ui/screens/ImportScreen.kt:303`), análisis incluido. Está acotado por los 16 MB del archivo, los 8 MiB de la parte cifrada y los 48 MiB del árbol. Informativo.

**Comprobaciones antes de la contraseña (A M-3):**
- un KDF fuera de rango (PBKDF2 2·10⁹, Argon2id de 100 000 MiB, scrypt de 2 GiB en Aegis) y una parte de más de 8 MiB se rechazan **sin pedir la contraseña** y sin calcular nada;
- la parte más grande que se admite (unos 6 MB de bloques AES) solo se decodifica una vez y lleva a `NeedsPassword`, en menos de 5 s en total y sin coger `KdfLock`.

`resistsWorkBeforeTheExportPassword`.

**El tope de 65 536 caracteres:**
- `passwordFieldTakes` admite exactamente 65 536 caracteres y rechaza 65 537;
- una contraseña en el tope, de caracteres de 3 bytes (192 KiB en UTF-8), cuesta en PBKDF2 lo mismo que una corta, porque HMAC resume una vez la clave larga, y sus bytes se ponen a cero.

`resistsThePasswordFieldCap`.

**La carrera del Laboratorio de hashes (B N-2):**
- un hilo escribe sin parar el progreso de una lectura vieja mientras se empiezan 20 000 archivos nuevos: nunca aparece el progreso viejo en uno nuevo;
- `finish` y `startFile` corren los dos en el hilo principal (`ui/screens/HashLabScreen.kt:188-224`), así que el resultado de un archivo viejo no puede caer en uno nuevo.

`resistsTheHashLabProgressRace`.

**Barrido de regresión de las entradas del primer informe:**
- los 20 tests de `RedTeamImportTest` y `RedTeamCryptoTest` siguen en verde: los resistidos de entonces y los `rt1_`, `rt2_` y `rt3_`, ya sin `@Ignore`;
- a mano, he vuelto a mirar:
  - el manifiesto: `scripts/check-manifest.sh` sigue en CI, y no cambian los componentes exportados ni los permisos;
  - `FLAG_SECURE` y el filtro de toques en los diálogos de importar y de «Comprobar una copia»;
  - el teclado seguro en los laboratorios: `SECRET_TEXT_KEYBOARD`, comprobado en `PlatformSourcesTest`;
  - que no hay `Log.*` ni `rememberSaveable` con secretos en el código nuevo (búsqueda hecha sobre `b1902ed`);
  - la red y TLS, que no han cambiado;
  - la CI, sin cambios en `.github/workflows/`.

### Necesita prueba en el móvil (segundo ataque)

7. **RT-4:** comprobar con «Android qa libre» que la fila de un duplicado cuya URL es `http://banco.es/` enseña ahora el esquema, una vez arreglado.
8. **RT-6:** en la lista de la bóveda, una entrada importada como «Banco» + U+2800 debe verse distinta o ser duplicado, una vez arreglado. Hoy se ve como «Banco».
9. **`KdfLock`:** con el APK de depuración, importar un JSON de Bitwarden protegido con Argon2id de 128 MiB, t = 10 y p = 4 (de `scripts/import-fixtures/gen_encrypted.py`, con esos valores), salir de la app mientras calcula, volver y desbloquear, y medir cuánto espera el desbloqueo en el móvil del dueño.

### Revisión experto-seguridad-cripto (segundo ataque)

- **`KdfLock`:** el `Mutex` es justo y no es reentrante, y `NonCancellable` solo envuelve el bloque, así que el cerrojo nunca se queda cogido después de que termine la derivación (`crypto/KdfLock.kt`). Ningún bloque que corre dentro de `derive` vuelve a cogerlo:
  - `encryptText` y `decryptText` llaman a `keyForHeader` directamente;
  - `fileKey` (`crypto/FileCrypto.kt:29`) solo se llama desde `encryptStream` y `decryptStream`, que nadie usa dentro de `derive`.
- **Antes de la contraseña** no se deriva nada: `bitwardenProtected`, `aegisEncrypted` y `twoFasEncrypted` solo leen la cabecera y decodifican Base64 hasta `IMPORT_MAX_ENCRYPTED_CHARS` (`imports/BitwardenJson.kt:39`, `imports/ImportJson.kt:215` y `:224`).
- **RT-4, RT-6 y RT-7** son de comparación (`imports/MergePlan.kt:382-406`, `imports/ImportModel.kt:107-159`), y **RT-5** es de política (`vault/MasterPolicy.kt:89-99`). Ninguno toca una primitiva ni un formato, y sus arreglos no cambian nada guardado.

### Revisión Ingeniero Móvil Senior (segundo ataque)

- **`deriveBlocking`** rechaza el hilo principal de Android, que mira por reflexión (`crypto/KdfLock.kt`). Sus únicos llamadores corren en `Dispatchers.IO` (`ui/screens/SecurityToolsScreen.kt:661` y `:694`).
- **La importación** coge `KdfLock` en `Dispatchers.Default` y suelta el *ticket* y pone a cero la contraseña en un `finally` (`ui/screens/ImportScreen.kt:296-307`). Un resultado viejo se descarta con el *run token*.
- **El Laboratorio de hashes:** `progress` se comprueba y se escribe dentro del mismo cerrojo que `startFile` y `forget`. `finish` corre en el hilo principal (`tools/hashlab/HashLabSession.kt`, `ui/screens/HashLabScreen.kt:223`).
- **El arreglo de RT-4** (si se enseña el esquema) y el de RT-6 son Kotlin puro en `imports/`. Se prueban en `jvm-check`, y solo la línea de la fila cambia en pantalla.

### Textos para el Abogado Digital (segundo ataque)

- **RT-4:** si se arregla en la fila, cambia lo que enseña la línea del duplicado («ana · http://banco.es»).
- **RT-5 y RT-7:** no cambian textos (`MASTER_TOO_WEAK` es el mismo). Si la política, el README o la ficha describen la regla de la maestra, que digan lo mismo que el código (R36).

### Puerta del segundo ataque

Ejecutada en el *worktree* el 9-11-2026, sobre `b1902ed` más `RedTeamSecondAttackTest`, con el *wrapper* (R29) y `--max-workers=1`:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con 1 818 tests, 0 fallos y 4 omitidos (los `@Ignore` de RT-4 a RT-7);
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con 1 813 tests, 0 fallos y 4 omitidos;
- ningún aviso `w:` del compilador.

Los tests del equipo rojo dan lo mismo en las dos suites:
- `RedTeamImportTest`: 12 tests, 0 omitidos y 0 fallos;
- `RedTeamCryptoTest`: 8 tests, 0 omitidos y 0 fallos;
- `RedTeamSecondAttackTest`: 13 tests (4 `rtN_` y 9 `resists…`), 4 omitidos y 0 fallos.

Sin el `@Ignore`, los cuatro `rtN_` nuevos fallan con los mensajes citados arriba.
