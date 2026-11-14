# Tarea 9: QR de wifi. Informe del implementador

**Estado: DONE_WITH_CONCERNS.** Lo pedido está hecho y probado, con las dos suites en verde. Quedan siete dudas que no he decidido en silencio (sección «Dudas»). En cada una he tomado la opción más prudente.

## Commits (rama `ccr-0aa27f95-u038cd`, desde `9102544`)

| Commit | Qué |
|---|---|
| `93b08ef` | Tests que fallan (vistos en rojo: no compilan) |
| `b8a164a` | `WifiQr`, el Inspector con un solo lector y el formulario |
| `7813cbe` | Pantalla del QR, «Añadir a la bóveda» por el formulario y «wifi» en minúscula (R-4) |
| `d384e8b` | Anexo de textos |
| `c713000` | Lo que dice la pantalla del QR, en una función (`wifiQrLines`) con su test |
| `1b36797` | «2.0, tarea 9: QR de wifi» (vacío: el informe vive en `.superpowers/sdd/`, que git ignora) |
| `f2c438a` | Pines de que bloquear o perder la entrada cierra la pantalla del QR y suelta la imagen (tras la revisión del asesor; el último commit) |

## Tests

Las dos suites completas se ejecutaron por separado sobre `c713000`. `f2c438a` solo añade aserciones de fuente a un test que ya existía (`PlatformSourcesTest`, 45 tests, en verde en jvm-check), así que los recuentos no cambian:

- **App**, `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL. **2280 tests, 0 fallos** (antes 2238, son 42 más). Lint solo da el aviso de siempre (`OldTargetApi`).
- **jvm-check**, `./gradlew -p jvm-check test`: BUILD SUCCESSFUL. **2240 tests, 0 fallos** (antes 2201, son 39 más).
- **Permisos y dependencias.** `git diff --stat 9102544..HEAD` no toca `gradle/`, `app/build.gradle.kts` ni `AndroidManifest.xml`: ningún permiso, componente ni dependencia nuevos. `aapt2 dump permissions` del APK da INTERNET, CAMERA, USE_BIOMETRIC, USE_FINGERPRINT y el permiso interno de AndroidX, sin ninguno de wifi. `.maestro/` y `res/` no comprueban los textos cambiados (solo el README de `.maestro/` dice «QR de Wi-Fi» en una descripción).

Tests nuevos:

| Test | N.º | Dónde corre |
|---|---|---|
| `tools/qr/WifiQrTest` | 24 | las dos suites |
| `tools/qr/WifiQrInspectTest` | 5 | las dos |
| `vault/WifiQrFormTest` | 7 | las dos |
| `PlatformSourcesTest` | +3 tests y 1 línea en el pin R9 | las dos |
| `ui/screens/WifiQrDrawingTest` | 3 | solo app (usa ZXing, que jvm-check no tiene) |

Rojo visto: los tests de `93b08ef` (no compilaban). `theScreenSaysTheWarningByTheNetworkOrWhyNot` y los pines de fuente añadidos después (`c713000`, `f2c438a`) se escribieron junto al código, sin verlos antes en rojo.

Dos líneas de `QrContentTest` cambian de `PlainText` a `WifiUnreadable`. Es un cambio buscado: ver «Revisión experto-seguridad-cripto».

Tests del encargo:
- `roundTripWithSpecialChars`, `openNetworkHasNoPassword`, `parseAnyOrder`, `parseRefusesHugeInput` y `parseRefusesNonWifi`.
- `roundTripOverEveryShortString`: las 259 cadenas de longitud 0 a 3 sobre `\ ; , : " a`, como nombre (sin la vacía) y como contraseña, con WPA y WEP, en producto cruzado (258 × 259 × 2 = 133 644). También la red abierta con cada nombre.
- Entrada hostil, un resultado definido y un test por caso:
  - `aTrailingBackslashIsRefused`
  - `aRepeatedFieldIsRefused`
  - `unknownFieldsAreIgnored`
  - `anUnknownTypeIsShownButNotKept`
  - `anEmptyOrMissingNameIsRefused`
  - `aMissingTerminatorIsTolerated`
  - `whatDoesNotReadAsFieldsIsRefused`
  - `parseRefusesHugeInput`
  - `neverThrowsOnAnything`, 30 000 textos al azar

## Resultado definido de cada entrada hostil

| Entrada | `WifiQr.read` / `parse` | En el Inspector |
|---|---|---|
| Barra invertida final, sin nada que escapar | `Malformed` / null | Texto oculto, se borra al salir y al bloquear, nota «mal escrito» |
| Campo conocido repetido (`S`, `T`, `P`, `H`; sin distinguir mayúsculas ni espacios en la clave) | `Malformed` / null | Igual |
| Campo desconocido (`E`, `A`, `I`, `PH2`, `X`…) | Se ignora | Se ignora |
| `T:` desconocido (`WPA2`, `WPA3`, `SAE`, `WPA2-EAP`, `xyz`) | `Unsupported` / null | Se enseña, sin «Añadir a la bóveda» y con su nota |
| Contraseña sin `T:` o con `T:nopass`; `H:` que no es true ni false | `Unsupported` / null | Igual |
| `S:` vacío, `S:""` o sin `S:` | `Malformed` / null | Texto oculto, nota |
| Sin `;;` final, o con un solo `;` | Se acepta | Se acepta |
| Algo después de `;;`, un trozo sin `:` (un `;` sin escapar) o una clave que no es letras y cifras | `Malformed` / null | Texto oculto, nota |
| Más de 4 096 caracteres | `Malformed` / null, sin leerlo | `QR_TOO_BIG`, como hoy (más de 4 096 bytes) |
| `WIFI://banco.es/` | `Malformed` | Ya no pasa a ser un enlace |

## Decisión sobre los valores solo hexadecimales

**Fuente.** La documentación pública del formato de ZXing (wiki «Barcode Contents», la biblioteca que ya va en el APK) dice de `S` y de `P`: «Enclose in double quotes if it is an ASCII name, but could be interpreted as hex (i.e. "ABCD")». También dice que `\`, `;`, `,`, `"` y `:` se escapan con `\`. Su ejemplo: el nombre `"foo;bar\baz"`, con comillas que son parte del nombre, se escribe `WIFI:S:\"foo\;bar\\baz\";;`.

He leído también el lector de ZXing 3.4.1, la versión del APK (`WifiResultParser` y `ResultParser`, del jar de fuentes de Maven Central). Solo lo he leído para describirlo, sin copiar nada:
- no descodifica el hexadecimal ni quita las comillas;
- se queda con el primer campo repetido;
- tira la barra invertida suelta del final.

**Lo elegido, sin reglas que no estén en esa fuente:**
- **Al escribir**, un nombre o una contraseña no vacíos hechos solo de `0-9a-fA-F` van entre comillas sin escapar (`S:"CAFE"`, `P:"12345678"`). Lo demás va escapado y sin comillas.
- **Al leer:**
  - se quitan las comillas solo si las dos están y la de cierre no está escapada, y se decide sobre el valor aún escapado (el lector anterior escapaba primero y perdía las comillas que son parte del nombre);
  - el hexadecimal no se descodifica nunca, vaya o no entre comillas: el valor es el texto que dice.
- **Tests:** `hexOnlyIsWrittenInQuotesAndReadAsText` y `onlyQuotesThatAreNotEscapedEncloseAValue` (con el ejemplo de la documentación). En el Inspector, `quotesThatArePartOfTheNameStayInIt`.
- **Una consecuencia que va a «Dudas»:** una contraseña WPA de 64 cifras hexadecimales (una clave en bruto) también se escribe entre comillas.

## Tabla de textos visibles

**Pantalla del QR y ficha de la entrada.** Las constantes están en `tools/qr/WifiQr.kt`.

| Texto | Condición exacta | Definido / usado | Test que lo fija |
|---|---|---|---|
| «Enseñar QR» | Ficha de una entrada de tipo `WIFI`, nunca en otra | `WifiQr.kt:268` / `VaultEntryScreen.kt:300` | `PlatformSourcesTest` «task 9 the wifi QR screen…» |
| «QR del wifi» | Título de la pantalla del QR | `WifiQr.kt:271` / `WifiQrScreen.kt:203` | `PlatformSourcesTest` (`BackScaffold(WIFI_QR_TITLE, onBack)`) |
| El nombre de la red (dato) | Siempre, por `plainReportText` | `WifiQrScreen.kt:209` | `PlatformSourcesTest`, pin R9 |
| «Quien vea este código ve la contraseña y puede conectarse a tu wifi.» | Se puede dibujar el código y la seguridad es WPA o WEP; a la vista, en color de aviso (O-4) | `WifiQr.kt:274` / `wifiQrLines`, `WifiQr.kt:259`; `WifiQrScreen.kt:211` | `WifiQrTest.theScreenSaysTheWarningByTheNetworkOrWhyNot` y `PlatformSourcesTest` |
| «Quien vea este código puede conectarse a tu wifi.» | Se puede dibujar y la red es abierta (`nopass`) | `WifiQr.kt:277` / ídem | ídem |
| «La red está marcada como abierta, así que la contraseña guardada no va en el código.» | Red abierta que guarda una contraseña | `WifiQr.kt:280` / `WifiQrScreen.kt:213` | ídem |
| «Mostrar QR» | Se puede dibujar, la imagen no está y ZXing no la rechazó | `WifiQr.kt:282` / `WifiQrScreen.kt:227` | `PlatformSourcesTest` |
| «Ocultar QR» | Con la imagen a la vista | `WifiQr.kt:283` / `WifiQrScreen.kt:225` | `PlatformSourcesTest` |
| «Código QR para conectarse a la red» (descripción de TalkBack) | Con la imagen; texto fijo, sin la contraseña; es la única `contentDescription` de la pantalla | `WifiQr.kt:286` / `WifiQrScreen.kt:223` | `PlatformSourcesTest` y `WifiQrTest.theTextsSay…` |
| «Esta red no tiene nombre: escríbelo en «Editar» para poder enseñar el QR.» | Nombre vacío; sale solo, sin aviso ni botón | `WifiQr.kt:288` / `wifiQrLines`; `WifiQrScreen.kt:211` | `WifiQrTest.emptyNameIsATextNotACrash`, `theScreenSays…` y `WifiQrDrawingTest` |
| «Elige el tipo de seguridad de esta red en «Editar» para poder enseñar el QR.» | La seguridad guardada no es WPA, WEP ni nopass (una copia de otro sitio) | `WifiQr.kt:289` / ídem | `WifiQrTest.anUnknownSecurityIsNeverWrittenAsItIs`, `theScreenSays…` y `WifiQrDrawingTest` |
| «El nombre y la contraseña de esta red son demasiado largos para caber en un código QR.» | Texto de más de 4 096 caracteres o bytes (antes de ZXing), o `WriterException` de ZXing («Data too big», más de 2 330 bytes medidos) tras «Mostrar QR» | `WifiQr.kt:290` / `WifiQrScreen.kt:78` y `:211` | `WifiQrTest.tooLongIsATextNotACrash` y `WifiQrDrawingTest.tooLargeForAQrCodeIsATextWhereZxingThrows` |
| «No se pudo generar el QR.» | Cualquier otra excepción de ZXing o al crear la imagen (sin memoria) | `WifiQr.kt:291` / `WifiQrScreen.kt:80` y `:157` | `PlatformSourcesTest` (el `catch`); ninguna entrada lo provoca |

**Inspector de QR.**

| Texto | Condición exacta | Definido / usado | Test que lo fija |
|---|---|---|---|
| «Añadir a la bóveda» (el de `QR_ADD_TOTP`) | Un wifi `Usable` | `ToolTexts.kt:361` / `QrInspectScreen.kt:374` | `WifiQrInspectTest.aWifiTheVaultCanHoldOffersAddToVault` y `PlatformSourcesTest` |
| «Clavero no reconoce todo lo que trae este código de wifi, así que no lo añade a la bóveda. Puedes crear la entrada a mano.» | Un wifi `Unsupported` | `WifiQr.kt:297` / `QrView.kt:143` | `WifiQrInspectTest.aWifiTheVaultCannotHoldIsShownButNotOffered` |
| «Parece un código de wifi, pero está mal escrito: no se puede leer sin adivinar.» | Empieza por `WIFI:` y es `Malformed` | `WifiQr.kt:300` / `QrView.kt:151` | `WifiQrInspectTest.aWifiTextThatCannotBeReadIsTreatedAsASecret` |
| «El texto completo puede llevar una contraseña: toca «Mostrar» para verlo.» | El texto anterior, oculto | `WifiQr.kt:303` / `QrView.kt:151` y `QrInspectScreen.kt:355` | ídem y `theLineInPlaceOfTheHiddenTextSaysWhatItHides` |
| «El texto completo lleva la contraseña de la red: …» (ya existía) | Un wifi con contraseña, oculto; ahora por `hiddenLine`, no por `secret` | `ToolTexts.kt:353` / `QrView.kt:147` | `WifiQrInspectTest` (dos tests) |
| «Red wifi» (cambia; R-4) | Título de un wifi leído | `ToolTexts.kt:378` / `QrView.kt:135` | `ToolTextsTest` (el anexo, en los dos sentidos) y `WifiQrInspectTest` |
| La explicación del Inspector (cambia: «ni redes wifi» y «…, un código 2FA o un wifi, pasarlo a su pantalla») | En «Más información» del Inspector | `ToolTexts.kt:281` | `ToolTextsTest` |
| «Añadir a la bóveda» (título de `tools/qr/wifi`, ante la puerta de la bóveda) | Bóveda bloqueada al llegar | `QrInspectScreen.kt:510` | `PlatformSourcesTest` (`VaultGate(store`) |

**Formulario.**

| Texto | Condición exacta | Definido / usado | Test que lo fija |
|---|---|---|---|
| «Este código de wifi no se puede añadir a la bóveda.» | Le llega al formulario un código que `parse` no acepta, o una entrada existente de otro tipo (el Inspector no lo ofrece) | `WifiQr.kt:294` / `EntryForm.kt:567` | `WifiQrFormTest.aCodeTheFormCannotHoldFillsNothing` y `anExistingEntryOfAnotherTypeIsNeverTurnedIntoAWifi` |

## Revisión experto-seguridad-cripto

Modelo de amenaza:
- **La contraseña del wifi en pantalla.** Quien mira el móvil, una captura, las apps recientes.
- **Un QR hostil.** Uno que enseña una red y guarda otra, que esconde una contraseña sin que se trate como secreto, que inyecta campos o que pide un tamaño enorme.
- **Una copia de la bóveda hecha en otro sitio**, con valores arbitrarios.

Fuera de alcance: los lectores de wifi de otros móviles.

1. **Un solo lector** (`QrContent.kt:112` a `WifiQr.read`). Lo que enseña el Inspector y lo que guarda el formulario salen del mismo análisis.
   - Un texto con `WIFI:` nunca sigue la cadena hasta `link()` ni hasta `PlainText`.
   - Antes, `WIFI:T:WPA;P:abc;;` salía como texto plano, con la contraseña a la vista y sin borrarse al salir. Ahora es `WifiUnreadable`: `textHidden` y `carriesSecret` (`QrView.kt:151`). Se comprueba para cada caso hostil en `WifiQrInspectTest`.
2. **No se adivina.**
   - Rechazados (`WifiQr.kt:150`, `:171`, `:178`, `:180`, `:183`):
     - el campo repetido, porque otro lector puede tomar el otro, igual que `crypto()` rechaza la clave repetida;
     - la barra final;
     - lo que va después de `;;`;
     - el trozo sin `:`.
   - Las comillas se deciden sobre el valor escapado (`:207`).
   - El hexadecimal no se descodifica.
3. **Acotado:**
   - lectura de 4 096 caracteres como mucho, comprobado antes de recorrer el texto (`WifiQr.kt:72`);
   - escritura limitada a 4 096 caracteres y 4 096 bytes UTF-8 (`:104`), así que todo lo escrito lo lee el Inspector;
   - ZXing se para en 2 330 bytes, medido y fijado en `WifiQrDrawingTest`.
4. **Inyección por la bóveda.**
   - Una seguridad guardada fuera de WPA/WEP/nopass (por ejemplo `WPA;S:otra`) no se escribe nunca tal cual: da texto (`WifiQr.kt:104`).
   - Con ids repetidos manda el primero, como en la ficha (`wifiConfigOf`, `:249`).
5. **El secreto:**
   - `WifiConfig`, `WifiRead.*`, `QrKind.Wifi` y `WifiUnreadable` no llevan la contraseña en `toString`.
   - La imagen y la configuración viven en `WifiQrViewModel` (`WifiQrScreen.kt:120`), nunca en `rememberSaveable` ni en un `Bundle` (`PlatformSourcesTest` cuenta 0 apariciones).
   - Se borran:
     - al ocultar;
     - con ON_STOP (`:201`);
     - si la red cambia;
     - al bloquear, que también cierra la pantalla (`:123` y `:197`), en segundo plano, porque el colector no depende del ciclo de vida;
     - en `onCleared` (`:178`).
   - La imagen solo se dibuja tras un toque, fuera del hilo principal, con un turno que descarta un dibujo tardío tras ocultar o bloquear.
   - La descripción de la imagen es una constante sin la contraseña.
   - `FLAG_SECURE` ya está en toda la app.
6. **Guardar.** `QrHandoff`, luego `QrPendingText` en `QrWifiImportViewModel` (`QrInspectScreen.kt:455`), luego el formulario de la tarea 7 (`EntryForm.kt:562`).
   - El formulario solo se rellena; «Guardar» usa `sinceLock = store.lockEpoch` (`VaultEntryScreen.kt:431`). En la pantalla no hay `upsert` ni `addAll` (`PlatformSourcesTest`).
   - Con la bóveda abierta, bloquear, salir sin girar o un ViewModel borrado se llevan la contraseña (`WifiQrFormTest`).
   - **Excepción, como en el 2FA:** mientras se espera ante la bóveda bloqueada (`VaultGate` de `tools/qr/wifi`), `QrPendingText` guarda el texto con la contraseña. Si se va a Inicio ahí, nada lo borra: `onDispose` no corre al parar la app, y no llega ningún bloqueo porque la bóveda no se abrió. Se borra al volver atrás, al salir de la pantalla sin girar o cuando el proceso muere. Ver «Dudas», 7.
   - El texto se entrega una sola vez.
7. **Sin conexión:**
   - ningún `Intent` para conectar ni ninguna API de wifi en `main/java` (`PlatformSourcesTest` «no way to join a network»);
   - los permisos del manifiesto siguen siendo exactamente INTERNET y CAMERA;
   - un solo componente exportado;
   - ninguna dependencia nueva.
8. **Informativo.** Un texto con un invisible antes de `WIFI:` (U+200B) no se reconoce como wifi y sale como texto, con su aviso de caracteres ocultos. Ningún lector lo trataría como wifi, así que no se trata como secreto.

## Revisión Ingeniero Móvil Senior

- **Estado en ViewModels ligados a su entrada de navegación** (`WifiQrViewModel`, `QrWifiImportViewModel`), con el patrón del 2FA (`QrTotpImportViewModel`). Solo el id va en la ruta (`AppNavHost.kt:319`, `:331`, `:411`).
- **Ciclo de vida:**
  - el ViewModel sigue `store.state` en `viewModelScope`, así que un bloqueo con la app en segundo plano cierra la pantalla sin composición;
  - `LifecycleEventEffect(ON_STOP)` oculta el QR;
  - `VaultIdleLock` y `touchesVault` cuentan el uso (`WifiQrScreen.kt:195` y `:205`).
- **Concurrencia.** ZXing y el bitmap van en `Dispatchers.Default` y la cancelación se respeta (`CancellationException` se relanza). El estado se escribe en el hilo principal, con un turno que descarta resultados tardíos.
- **Compose.** Ningún `rememberSaveable`. `ComposeCells` en el ViewModel, como las demás sesiones. Fondo blanco para el lector; `FilterQuality.None`.
- **Accesibilidad (R-1).** Descripción fija de la imagen. El aviso es texto, no solo color.
- **Módulos.** `WifiQr.kt` es Kotlin puro (jvm-check no tiene ZXing). El código de ZXing y su test van en `ui/`.
- **Pendiente en el móvil** (no se puede aquí; para el README de pruebas):
  - que otro móvil se conecte con el QR, con la cámara del sistema, también con un nombre o una contraseña solo hexadecimales (entre comillas);
  - leer QR de wifi generados por Android y por otros móviles (`T:SAE`, `T:WPA2`…);
  - TalkBack en la pantalla del QR.

## Textos para el Abogado Digital (revisión final)

Todos están en el anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, en la sección «Clavero 2.0, tarea 9: QR de wifi».

**Pantalla del QR:**
- «Enseñar QR» y «QR del wifi».
- «Quien vea este código ve la contraseña y puede conectarse a tu wifi.» (O-4, con contraseña).
- «Quien vea este código puede conectarse a tu wifi.» (red abierta).
- «La red está marcada como abierta, así que la contraseña guardada no va en el código.»
- «Mostrar QR» y «Ocultar QR».
- «Código QR para conectarse a la red» (TalkBack).
- Errores:
  - «Esta red no tiene nombre: escríbelo en «Editar» para poder enseñar el QR.»
  - «Elige el tipo de seguridad de esta red en «Editar» para poder enseñar el QR.»
  - «El nombre y la contraseña de esta red son demasiado largos para caber en un código QR.»
  - «No se pudo generar el QR.»

**Inspector:**
- «Clavero no reconoce todo lo que trae este código de wifi, así que no lo añade a la bóveda. Puedes crear la entrada a mano.»
- «Parece un código de wifi, pero está mal escrito: no se puede leer sin adivinar.»
- «El texto completo puede llevar una contraseña: toca «Mostrar» para verlo.»
- Cambian por R-4:
  - «Red Wi-Fi» pasa a «Red wifi»;
  - en la explicación, «ni redes Wi-Fi» pasa a «ni redes wifi», y se añade «un código 2FA o un wifi» a lo que se pasa a su pantalla (veracidad: ahora un wifi también se pasa).

**Formulario:** «Este código de wifi no se puede añadir a la bóveda.»

**Lo que el Abogado debe ver:**
- WPA, WEP y nopass van como texto, sin ®.
- Ningún texto califica una red como «segura».
- No se nombra ninguna otra app.
- Las listas de pruebas del README de la 1.3.0 siguen diciendo «Wi-Fi». Son pasos de prueba, no textos de la app; para la tarea 12.

## Dudas

1. **El aviso: encargo frente a O-4.**
   - El encargo dice «Quien vea este código puede conectarse a tu wifi.».
   - O-4 del Abogado, que es obligatorio, dice «Quien vea el código ve la contraseña».
   - He usado los dos: con contraseña, «Quien vea este código ve la contraseña y puede conectarse a tu wifi.»; con la red abierta, la frase del encargo, porque la de O-4 sería falsa (no hay contraseña). Va al Abogado.
2. **`T:WPA2`, `T:WPA3`, `T:SAE`.**
   - La fuente solo da WPA, WEP, WPA2-EAP y nopass. Pasar los demás a WPA sería adivinar.
   - Se enseñan en el Inspector, pero no se ofrecen para la bóveda. Puede que los QR que genera Android para WPA3 usen `SAE` (sin comprobar).
   - Decisión del dueño, si quiere pasarlos a WPA.
3. **Una contraseña WPA de 64 cifras hexadecimales**, o una WEP de 10 o 26, que son claves en bruto, se escribe entre comillas, como texto, por la regla de la documentación. Un móvil podría no conectarse con ella. Una regla por longitudes no está en la fuente: no la he añadido.
4. **El estado del Inspector.** Su resultado sigue en `remember`, no en un ViewModel. No es estado guardado, y moverlo alargaría la vida del secreto (hoy se va al ir a otra pantalla). Lo nuevo sí va en ViewModels.
5. **`plainReportText` frente a `visibleText`.**
   - El Inspector sigue con `visibleText`, que marca los invisibles en vez de quitarlos (está fijado en sus tests, `Casa⟨U+202E⟩`).
   - La pantalla nueva usa `plainReportText`.
   - El formulario guarda el nombre tal cual. `fieldsToSave` recorta los espacios de los extremos de los valores no secretos, así que un nombre con espacios en los bordes se guarda cambiado, y el título sale del nombre tal como llega.
   - Un nombre de solo espacios (`WIFI:S: ;;`) es `Usable` y se ofrece para la bóveda, pero el formulario lo recorta a vacío. La pantalla del QR de esa entrada dirá entonces «Esta red no tiene nombre…».
6. **«Pegado».** Ni el formulario ni el Inspector reconocen un código `WIFI:` pegado (la tarea 7 solo reconoce 2FA y frase semilla). No lo he añadido. «Pegado» queda cubierto en el Inspector, que es por donde entra un código.
7. **La contraseña ante la bóveda bloqueada** (ver «Revisión experto-seguridad-cripto», 6). Es el mismo hueco que ya tiene el flujo del 2FA (`QrTotpImportScreen`), del que es copia. No lo he cambiado. Borrar el texto con ON_STOP ante la puerta también lo borraría cuando el selector de «Importar» o el diálogo de la huella paran la actividad, y se perdería la entrega. Para el revisor o el dueño: aceptarlo como en el 2FA, o arreglar los dos flujos a la vez.
