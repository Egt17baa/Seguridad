# Tarea 9 (QR de wifi): re-revisión 1 (ronda 1 de arreglos)

Re-revisión acotada de `2085b29..2093c50`: solo he leído el código, sin ejecutar gradle. Las citas son de `2093c50`. Contexto: «Ruling (advisor, T9 ronda 1)» y «Owner decision (14-11-2026): Duda 2 de T9» en `progress.md`, y la sección «Ronda 1» de `task-9-report.md`. He contrastado el lector de ZXing 3.4.1, que es la dependencia que entra en el APK (`com.google.zxing:core:3.4.1` en la verificación de dependencias, a través de `zxing-android-embedded` 4.3.0): `ResultParser.matchPrefixedField` y `WifiResultParser`.

## Veredicto

**Aprobada con menores: 0 Critical, 0 Important y 3 menores (n-1 a n-3), más 4 informativos.**
- I-1 e I-2 están cerrados, y m-1 a m-7, hechos.
- La Duda 2 sigue la decisión del dueño.
- No veo ninguna regresión en lo de la ronda 0, en la regla de los enlaces del QR, en el flujo del 2FA ni en `SourceTextsTest`.
- Los menores pueden ir a la ronda de la revisión de la rama o a `pendientes.md`. Ninguno deja un secreto a la vista ni en memoria.

## Comprobación, punto por punto

### I-2: cerrado

- **La prueba con ZXing de verdad.** `WifiQrReferenceTest.kt:25` llama a `com.google.zxing.client.result.WifiResultParser` de la dependencia, no a una copia. Las aserciones de `:55-57` reproducen mis tres cadenas («secreta», «otra», «mala»), y `:59-61` comprueba que Clavero las da por `Malformed`. Es coherente con lo que leí de `matchPrefixedField`: busca con `indexOf` en cualquier parte del texto y no añade un último valor sin `;`.
- **La regla va sobre el texto en bruto.** `WifiQr.kt:242-245` recorre el cuerpo sin desescapar, hasta el `;;` de cierre. Es `Malformed` con cualquier `S:`, `P:`, `T:` o `H:` en mayúsculas cuya posición no esté en `starts`, y `starts` solo recibe el principio de cada trozo tras un `;` sin escapar (`:224-230`).
  - Los casos difíciles van bien: `\S:` (la letra escapada; ZXing sí lo encuentra) y `a\;S:` (un `;` escapado delante) dan `Malformed`. `S\:` no es un `S:` para nadie.
  - Lo que hay después del cierre tiene que estar en blanco (`:238`), así que no puede esconder nada.
  - Las claves de empresa (`PH2:`, `E:`, `A:`, `I:`) no contienen ninguno de los cuatro prefijos, así que se siguen leyendo.
- **El test diferencial es sólido.** `WifiQrReferenceTest.kt:80-122` recorre las 177 156 cadenas exhaustivas (contadas, `:94`) y 300 000 al azar en tres modos: alfabeto, piezas y campos con valores.
  - `assertAgrees` (`:43-51`) falla si Clavero dice «wifi» y ZXing no lo lee, o lee otro valor en cualquiera de los cuatro.
  - La tolerancia es solo la documentada: las comillas (`same`, `:35`), y la caja del tipo y WPA2 como WPA (`sameType`, `:38-41`).
  - Mira un solo sentido, y es el que importa: si Clavero dice `Malformed` y ZXing lo lee, el resultado es prudente, porque el texto se oculta y no se ofrece.
  - Un tope inferior (`wifi > 8_000`, `:121`) impide que pase en vacío.
  - La salida del codificador está incluida: `whatClaveroWritesZxingReadsTheSame` (`:125-155`) recorre más de 40 000 configuraciones con las tres seguridades, con las claves en bruto de 64 y de 26 cifras y con `x;S:y`.
- **El secreto no depende del análisis.**
  - Todo `QrKind.Wifi` va oculto y con `carriesSecret` (`QrView.kt:155-156`), y también `WifiUnreadable` (`:160`).
  - La rama `catch` da `WifiUnreadable` a todo texto que empiece como un wifi (`QrContent.kt:73`, `:81`), con su test (`WifiQrInspectTest`, `theLastDefenceKeepsAWifiTextASecret`).
  - `startsLikeWifi` (`WifiQr.kt:87-95`) salta espacios, controles y lo que no se dibuja (`drawsNothing`, que incluye las marcas de dirección y U+FEFF). `wifi(text)` recibe el texto sin recortar (`QrContent.kt:86`), y un prefijo con algo delante da `Malformed` (`WifiQr.kt:107`). Tests en `WifiQrTest` (`whatStartsLikeAWifiCodeIsSeenSoWithoutReadingIt`, con 10 000 caracteres) y en `WifiQrInspectTest` (BOM, U+200B, espacio y minúsculas).
  - Más de 4 096 caracteres: `read` da `Malformed` (`WifiQr.kt:107`), y antes el Inspector ya da `QR_TOO_BIG` sin enseñar nada.
- **Mayúsculas y minúsculas, como ZXing:**
  - `WIFI:` y las claves solo en mayúsculas (`WifiQr.kt:107`, `:254`), porque ZXing usa `startsWith("WIFI:")` y `indexOf("S:")`;
  - el valor de `T:` en cualquier caja: ZXing lo guarda como texto, y la app de ZXing reconoce `WPA` y `WPA2`;
  - `H:TRUE` como oculta (`:114`), igual que `Boolean.parseBoolean`.

  Cada caso tiene su test (`WifiQrReferenceTest.kt:64-73`, `WifiQrTest`, `QrContentTest`).
- **Texto de `Malformed`:** «Clavero no puede leer este código de wifi con seguridad: otro lector podría ver en él otra red u otra contraseña. No se añade a la bóveda.» (`WifiQr.kt:390-391`). Es verdad frente al código: no se ofrece (`QrView.kt:160`, sin acción) y casi todas las causas son de ese tipo (barra final, campo repetido, la regla de I-2, el `;` que falta, lo que va tras el cierre). Ver i-3 para los casos sin nombre.
- **Lo nuevo: el último campo sin `;`, ahora rechazado.** Es lo correcto.
  - ZXing no lo lee (`matchPrefixedField`: sin `;`, sale del bucle sin añadir el valor). Así, `WIFI:T:WPA;S:Casa;P:clave` lleva para ZXing una red sin contraseña, y aceptarlo era justo el desacuerdo que I-2 cierra.
  - Lo que generan los lectores habituales y la documentación termina en `;;`, y un solo `;` final sigue valiendo (`WifiQrTest`, `aMissingTerminatorIsToleratedButEveryFieldEndsWithASemicolon`).
  - **No hay regresión de datos:**
    - la bóveda guarda campos, nunca códigos (`wifiConfigOf`, `WifiQr.kt:324-327`);
    - lo que escribe Clavero siempre termina en `;;` (`:160-166`);
    - el formulario solo recibe lo que el mismo lector ya aceptó;
    - el 2FA (`otpAuth`) y los demás lectores no cambian (`QrContent.kt:86`).
  - El caso de una red real que se cuela sin `;` final sale oculto y sin «Añadir»: falla cerrado. Ya está en la lista de pruebas en el móvil (Duda 2 de la ronda 1).

### I-1: cerrado

- **Una sola regla**, en `MainActivity.onStop` (`MainActivity.kt:119`): `container.leftApp(VaultLock.keepOpenOnce)`, con `!isChangingConfigurations`, la misma condición del bloqueo, y antes de él (`:120`).
  - `AppContainer.leftApp` (`AppContainer.kt:217`) solo llama a `QrHandoff.appLeft()`. No toca nada más del contenedor: ni el portapapeles, ni la copia en dos pasos, ni la bóveda, ni las copias.
  - `appLeft` (`QrHandoff.kt:56`) vacía el traspaso y suelta cada `QrPendingText` que aún espera, sea de wifi o de 2FA. El registro es débil (`:38`) y va por identidad, así que no retiene ninguna pantalla.
- **`keepOpenOnce` conserva el texto:** el selector de «Importar» de la puerta y el escáner de la app lo ponen antes de lanzarse, y solo vuelve a `false` en `onStart` (`MainActivity.kt:60`).
  - El desbloqueo con la huella no para la actividad (`BIOMETRIC_STRONG` con botón negativo, sin la credencial del dispositivo, como vi en la ronda 0). El escáner pone `keepOpenOnce`.
  - En los dos casos el texto se entrega. Con la bóveda abierta, la entrega es inmediata y `clear()` saca el texto del registro (`QrPendingText.kt:48`), así que salir después ya no echa al usuario del formulario.
- **Las dos pantallas de importar** miran `left` antes del estado de la bóveda (`QrInspectScreen.kt:421`, `:495`), así que nunca se abre un formulario vacío. El momento en que vuelven es n-1.
- **Tests en los dos sentidos:**
  - `AppContainerTest` («leaving the app drops…»): `keepOpenOnce = true` conserva los dos textos y `false` los suelta, con `left`;
  - `QrHandoffTest`: `leavingTheAppDropsEveryPendingTextAndSaysSo` y `aTextAlreadyHandedOverOrClearedIsNotMarkedAsLeft`;
  - el cableado y el orden respecto al bloqueo, con `PlatformSourcesTest`.
- No hay ningún Ruling que pida conservar el texto al salir. Lo comprobé con lo que busca el informe y coincido.

### Duda 2 (decisión del dueño): hecho

- `WPA2`, en cualquier caja, se lee como WPA (`WifiQr.kt:118`), se ofrece y se vuelve a escribir `T:WPA` (`WifiQrTest`, `anUnknownTypeIsShownButNotKept`; `WifiQrInspectTest`, `wpa2`).
- `WPA3`, `SAE`, `sae` y `WPA2-EAP` dan `Unsupported` con `SECURITY` y el texto «Clavero no guarda el tipo de seguridad «WPA3» de este código, así que no lo añade a la bóveda.» (`WifiQr.kt:377`, `QrView.kt:146`), que es verdad. Cada uno tiene su test.
- Un `WPA2` guardado en una copia de otro sitio da «Elige el tipo de seguridad…», que también es verdad, porque el formulario solo guarda los tres valores.

### Los menores de la ronda 0

- **m-1, hecho.** `problem` da `WIFI_QR_NO_PASSWORD` para WPA o WEP sin contraseña (`WifiQr.kt:147`, `:366`). No se dibuja el QR (`WifiQrDrawingTest`), la línea sale sola (`wifiQrLines`, `:335`) y el texto dice por qué y qué hacer. Leer `WIFI:T:WPA;S:Casa;;` sigue siendo `Usable`, con su test, y es coherente: la contraseña se puede escribir en «Editar».
- **m-2, hecho.** Sin comillas solo con WPA y 64 cifras, o con WEP y 10, 26, 32 o 58 (`WifiQr.kt:178-182`, `:314`). El test recorre de 1 a 64 cifras con WEP y los bordes 63 y 65 con WPA (`WifiQrTest`, `hexOnlyIsWrittenInQuotesAndReadAsText`). El nombre siempre lleva comillas.
  - Las fuentes son correctas: la documentación de `WifiConfiguration.preSharedKey` dice «a string of 64 hex digits for raw PSK», y la frase de paso tiene de 8 a 63 caracteres; la descripción pública de WEP da 40, 104, 128 y 232 bits de clave, que se escriben con 10, 26, 32 y 58 cifras, o con 5, 13, 16 y 29 caracteres de texto.
  - **Duda 3 (la clave WEP de 32 cifras): mantenerla.** Treinta y dos caracteres no son la longitud de ninguna clave WEP de texto, así que el valor solo puede ser la clave en bruto. Ponerle comillas no ayudaría a ningún lector: lo convertiría en una clave de texto de longitud no válida. Es WEP de 152 bits, propio de algunos fabricantes y rarísimo. Basta la fila de la prueba en el móvil para 26, que ya existe.
- **m-3, hecho.** Un nombre que `trim` cambiaría da `NAME_SPACES` y no se ofrece (`WifiQr.kt:127`), con el mismo `trim` que `fieldsToSave` (`EntryForm.kt:73`).
  - El invariante de punta a punta (leer, `applyWifiQr`, `fieldsToSave`, `wifiConfigOf` y `encode`/`parse`) está en `WifiQrFormTest`, `readAddSaveAndShowGiveTheSameNetwork`: 420 casos, más las redes abiertas, con nombres con escapes, emoji, U+202E, dobles espacios y 64 cifras.
  - Un nombre con espacios en los extremos no se ofrece: lo comprueban las últimas líneas del mismo test (`" Casa"`, `"Casa "`, `" "`, `"Casa\n"`).
- **m-4, hecho:** la rama `catch` (arriba) y el KDoc (`QrContent.kt:57-63`).
- **m-5, hecho:** el alfabeto lleva el espacio y `P` (585 cadenas, `584 × 584 × 2` casos), y el diferencial, `S`, `P`, `T` y `H`.
- **m-6, hecho:** `QR_ADD_TO_VAULT` (`ToolTexts.kt:361`), usada en los dos botones y en los dos títulos.
- **m-7:** el Inspector sigue con `visibleText`, como dice el Ruling.

### Regresiones

- Los puntos fuertes de la ronda 0 siguen en pie:
  - el análisis, lineal y acotado (el recorrido nuevo es una sola pasada);
  - sin excepciones (30 000 textos al azar, más 477 000 del diferencial);
  - la pantalla del QR, sin cambios;
  - sin `Intent`, permisos ni dependencias de APK. ZXing ya iba en el APK, y el test diferencial solo lo usa en las pruebas.
- La regla de los enlaces del QR no se toca.
- **El 2FA:** el único cambio es la vuelta con `left` (`QrInspectScreen.kt:421`). El texto se suelta en cuanto el formulario lo toma, así que dentro del formulario no se echa al usuario.
- **`SourceTextsTest`:** ninguno de los textos nuevos contiene una palabra prohibida. «con seguridad» y «tipo de seguridad» no casan con `segur[oa]s?` como palabra entera (`ForbiddenWords.kt:7`).

## Issues

### Critical

Ninguno.

### Important

Ninguno.

### Minor

**n-1. Tras salir de la app, la pantalla de importar no vuelve al Inspector hasta que algo la recompone.** `QrPendingText.kt:21` (`left` es un `@Volatile var`, no estado de Compose), `QrInspectScreen.kt:421` y `:495`.

- **Escenario:** con la bóveda bloqueada y la puerta a la vista, el usuario va a Inicio. `onStop` suelta el texto y pone `left = true`. `MainActivity` llama a `vault.lock(LEFT_APP)`, pero con la bóveda ya bloqueada no emite un estado nuevo (`VaultStore.kt:725-729`). Al volver, `collectAsStateWithLifecycle` vuelve a dar el mismo `Locked`, y no hay nada más que vuelva a ejecutar el cuerpo de `QrWifiImportScreen`. Así que sigue a la vista la puerta, con el título «Añadir a la bóveda».
- Si el usuario desbloquea, la recomposición ve `left` y vuelve al Inspector (`:495`), sin el resultado, que también se soltó. Falla cerrado: el secreto ya no está y nunca se abre un formulario vacío. Pero el informe dice que las pantallas «vuelven al Inspector», y en realidad lo hacen al siguiente desbloqueo, que confunde.
- **Arreglo:** que `left` sea observable. Por ejemplo, una celda de `ComposeCells` o un `mutableStateOf` en el ViewModel; se escribe en el hilo principal desde `onStop`. Así, la vuelta es inmediata al volver a la app. Un pin de fuente o un test de que la lectura es de estado.

**n-2. El Inspector enseña «Cifrado: WPA» para un código que dice `T:WPA2`.** `QrView.kt:131` (`else -> kind.security`), junto con `WifiQr.kt:118`. Lo fija `WifiQrInspectTest` (`assertEquals("WPA", wpa2.field("Cifrado"))`).

- El Inspector enseña lo que hay en el código, y el código dice «WPA2». Guardarlo como WPA es la decisión del dueño, pero enseñarlo cambiado no lo es. No hay riesgo, porque la bóveda lo etiqueta «WPA/WPA2/WPA3».
- **Arreglo:** que `QrKind.Wifi` lleve el tipo tal como vino para el campo «Cifrado» (pasado por `visibleText`, como se hace con `WPA3`) y siga ofreciendo WPA para la bóveda. O, si se prefiere, aceptarlo y apuntarlo.

**n-3. `QR_WIFI_NAME_SPACES` dice «un espacio» y también sale con un salto de línea o un tabulador.** `WifiQr.kt:127` (`trim`) y `:384`. `WifiQrTest` lo fija con `"Casa\n"`.

- **Arreglo:** «…empieza o termina con un espacio en blanco…», que cubre los saltos de línea en un español llano, o aceptarlo así. Va al anexo y al Abogado.

### Informativos (sin cambio)

- **i-1.** Un texto que termina en `;` seguido de un salto de línea (`WIFI:S:Casa;\n`) es ahora `Malformed`, porque el último trozo es `"\n"`. ZXing lo leería sin problema. Es más estricto de lo necesario y falla cerrado (oculto, sin «Añadir»). Si se quiere aflojar, el trozo final en blanco se puede tratar como cierre.
- **i-2.** Con el selector de «Importar» abierto desde la puerta (`keepOpenOnce`), si el usuario va a Inicio desde el selector, la actividad ya estaba parada y no llega otro `onStop`: el texto sigue en memoria hasta volver. Es la misma excepción que la regla del bloqueo al salir, y de forma deliberada usa la misma condición. Lo acepto.
- **i-3.** El texto de `Malformed` dice «otro lector podría ver en él otra red u otra contraseña». Con un código sin nombre (`WIFI:T:WPA;P:x;;`), ZXing no ve ninguna red. El «podría» y la primera frase («no puede leer este código de wifi con seguridad») lo mantienen verdadero. Lo acepto.
- **i-4.** La nota de `qrWifiOtherSecurity` repite el tipo del código, que es un dato hostil, pero pasado por `visibleText` (`QrView.kt:146`). Un tipo muy largo, de hasta unos 4 000 caracteres, da una nota igual de larga. Es solo texto plano y no es un riesgo.

## Revisión experto-seguridad-cripto

- **I-2:** el invariante «si Clavero dice wifi, el lector de la dependencia lee lo mismo» está probado de forma exhaustiva y al azar, y también para todo lo que escribe Clavero. El secreto ya no depende del análisis: todo texto que empieza como un wifi es secreto, incluida la rama `catch` y lo que va detrás de espacios o invisibles. Lo que no cubre son otros lectores (la cámara de Android, iOS), que ya están en la lista del atacante de la revisión final, como pide el Ruling.
- **I-1:** ningún texto de QR pendiente sobrevive a salir de la app, con la misma excepción que el bloqueo (`keepOpenOnce`, i-2). El registro es débil y por identidad.
- **Entrada hostil:** sigue siendo lineal y acotada, sin ReDoS (la única expresión regular es `KEY`) y sin excepciones. Ningún texto nuevo lleva la contraseña: la nota de `SECURITY` solo lleva el tipo, pasado por `visibleText`.
- **Claves en bruto (m-2):** solo en las longitudes que no admiten una forma de texto. No cambia el lector, que nunca descodifica el hexadecimal.
- **Sin cambios:** permisos, componentes exportados, red, `Intent` y dependencias del APK.
- **Veredicto de seguridad:** aprobado. Los tres menores no tocan ningún secreto.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** I-1 reutiliza el único punto donde la app decide «se ha salido» (`onStop`, con `keepOpenOnce` y `isChangingConfigurations`), sin añadir otro `LifecycleEventEffect`. Es la decisión correcta. Falta que `left` sea estado observable (n-1).
- **Concurrencia:** `appLeft` está sincronizado; `leftApp` no vuelve a entrar en `QrHandoff`, y `clear` → `release` toma el mismo monitor desde el hilo principal, así que no hay bloqueo mutuo. `QrPendingText` sale del registro al entregar, y no se acumula nada.
- **Compose:** las dos pantallas salen con `done()` antes de componer el formulario o la puerta. No hay estado guardado nuevo.
- **jvm-check:** `QrHandoff`, `QrPendingText`, `AppContainer` y `WifiQr` son Kotlin puro y entran en jvm-check. `WifiQrReferenceTest` va en `ui/screens` porque usa ZXing, como `WifiQrDrawingTest`, y jvm-check excluye `ui/`. No hace falta tocar `jvm-check/build.gradle.kts`.
- **Tests:** el informe deshizo cada arreglo y vio fallar sus tests (dos tandas, 19 y 7 tests). Los tests de I-1 son de comportamiento del contenedor y del registro, no solo pines de fuente. El diferencial tarda unos segundos, y es aceptable en la suite de la app.
- **MASVS:**
  - STORAGE: ningún secreto en memoria al salir, salvo la excepción del selector;
  - CODE: la entrada no fiable se lee como la lee el lector de referencia, o no se lee;
  - PLATFORM: sin cambios.
