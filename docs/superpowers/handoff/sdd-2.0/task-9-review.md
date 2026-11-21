# Tarea 9 (QR de wifi): revisión

Revisión de `9102544..f2c438a` (rama `ccr-0aa27f95-u038cd`), solo leyendo el código; sin gradle (el controlador corre las dos suites en un clon limpio). Las citas `archivo:línea` son de `f2c438a`. He leído `CLAUDE.md`, `experto-seguridad-cripto/SKILL.md`, `desarrollo-movil-senior` y `auditoria-seguridad-movil`, el encargo, el «Ruling (advisor, T8 cierre)», el diseño («QR de un wifi» y el bloque entero de la «Revisión inicial del Abogado Digital», O-1 a O-11 y R-1 a R-12; a esta tarea solo le tocan O-4, O-9, R-1 y R-4), la lista de palabras prohibidas (`app/src/test/java/app/seguridad/tools/ForbiddenWords.kt:5-7`) y el informe con sus 7 «Dudas». He contrastado el formato con la wiki pública de ZXing («Barcode Contents», sección «Wi-Fi Network config») y he leído, solo para describir su comportamiento, `WifiResultParser`/`ResultParser` de ZXing 3.4.1 y el `WifiConfigManager` de la app Android de ZXing.

## Strengths

- **Un solo lector** para lo que enseña el Inspector y lo que guarda el formulario: `QrContent.kt:112` (`wifi(t, text)` → `WifiQr.read`) y `EntryForm.kt:562` (`applyWifiQr` → `WifiQr.parse`). Un texto con `WIFI:` ya no cae nunca en `link()` ni en `PlainText` (`WIFI://banco.es/` deja de ser un enlace). Antes, `WIFI:T:WPA;P:abc;;` salía como texto plano con la contraseña a la vista; ahora es `WifiUnreadable`, oculto y con `carriesSecret` (`QrView.kt:151`).
- **El analizador es lineal, acotado y no lanza:** el tope de 4 096 se mira antes de recorrer (`WifiQr.kt:75`); un solo recorrido por carácter (`WifiQr.kt:146-169`); la única expresión regular es `[A-Za-z][A-Za-z0-9]*` (`WifiQr.kt:240`), sin retroceso catastrófico; todos los `substring` están guardados por longitud (`:179`, `:184`, `:207-208`). La barra final se rechaza antes de leer el valor (`:150`), y `unescape` no puede ver una barra suelta (`:221-237`).
- **Resultado definido para cada entrada hostil**, cada uno con su test (`WifiQrTest.kt:119-212`): barra final, campo repetido (sin distinguir mayúsculas ni espacios en la clave), campos desconocidos ignorados, `T:` desconocido como `Unsupported`, `S:` vacío o `S:""`, falta de `;;` tolerada, texto después de `;;` rechazado, más de 4 096 rechazado sin leer, y 30 000 textos al azar sin excepción.
- **Las comillas se deciden sobre el valor aún escapado** (`WifiQr.kt:206-208`), así que el ejemplo de la propia documentación (`S:\"foo\;bar\\baz\"`) conserva las comillas que son parte del nombre (`WifiQrTest.kt:234`, `WifiQrInspectTest.kt:83`). El lector anterior las perdía.
- **El escritor no deja inyectar desde la bóveda:** una seguridad que no es `WPA`, `WEP` ni `nopass` (por ejemplo `WPA;S:otra`, de una copia hecha en otro sitio) nunca se escribe tal cual (`WifiQr.kt:106`, `WifiQrTest.kt:260-273`); `T:` también pasa por `escape` (`:120`).
- **Ida y vuelta exhaustiva** sobre las 259 cadenas de 0 a 3 caracteres de `\ ; , : " a`, como nombre y como contraseña, con WPA y WEP (133 644 casos, `WifiQrTest.kt:97-115`), y la ida y vuelta por ZXing de verdad: codifica, lee la imagen con `QRCodeReader` y compara (`WifiQrDrawingTest.kt:125-136`). El límite real del codificador (2 300 caracteres de contraseña con «Casa», 2 330 bytes con ECI a nivel M) está medido por búsqueda binaria, no supuesto (`WifiQrDrawingTest.kt:143-171`).
- **La pantalla del QR** guarda el contenido y la imagen en `WifiQrViewModel` ligado a su entrada de navegación (`WifiQrScreen.kt:105-184`), sigue a la bóveda en `viewModelScope` (un bloqueo en segundo plano cierra la pantalla sin composición, `:120-127`), dibuja solo tras un toque y fuera del hilo principal, con un turno que descarta un dibujo tardío (`:137-160`), oculta la imagen con `ON_STOP` (`:201`) y al cambiar la red (`:130-133`). La única `contentDescription` es una constante sin la contraseña (`:223`, `WifiQr.kt:286`). Solo el id va en la ruta (`AppNavHost.kt:319`, `:331`).
- **«Añadir a la bóveda» pasa por el formulario de la tarea 7**, que solo rellena (`EntryForm.kt:562-577`); el guardado es el de siempre, con `sinceLock = store.lockEpoch` tomado al guardar (`VaultEntryScreen.kt:431-434`). La entrega es de una vez (`takeHandOver`), y lo que trajo el código se va con el bloqueo, al salir sin girar o con el ViewModel (`WifiQrFormTest.kt:72-94`).
- **Ningún `Intent` para conectar, ninguna API de wifi, ningún permiso, componente ni dependencia:** fijado con un test que recorre `main/java` y el manifiesto (`PlatformSourcesTest`, «task 9 no way to join a network and no new permission»). `toString` de `WifiConfig`, `WifiRead.*`, `QrKind.Wifi`, `WifiUnreadable` y `WifiQrShown` no llevan la contraseña.
- **Textos:** R-4 aplicado («Red wifi», «ni redes wifi»), O-4 a la vista y en color de aviso, con texto (no solo color, R-1), y un texto distinto y verdadero para la red abierta. Ningún texto nuevo nombra otra app ni contiene ninguna palabra de `ForbiddenWords.kt:5-7` («seguro/a», «limpio/a», «garantiza», «anónim», «imposible», «100 %», «de verdad», «validez legal», «sospechos»): los he repasado uno a uno, y `SourceTextsTest` lo comprueba en la suite del controlador. «Editar», al que mandan dos errores, es el botón de la ficha (`VaultEntryScreen.kt:316`). **Texto mínimo (M-3):** se cumple. La pantalla del QR enseña el nombre, una línea de aviso, como mucho una línea secundaria y un botón; el Inspector, una nota de una o dos frases. Los avisos y los errores van a la vista, como pide M-3, y nada necesita «Más información». El informe trae la tabla de textos con su condición y su test que pedía el Ruling, y el anexo está al día.
- El informe es honesto: dice qué tests no se vieron en rojo y deja las 7 dudas abiertas en vez de decidirlas en silencio.

## Issues

### Critical

Ninguno.

### Important

**I-1. La contraseña del wifi sigue en memoria al salir de la app mientras se espera ante la bóveda bloqueada (Duda 7).** `QrInspectScreen.kt:455-469` (`QrWifiImportViewModel`), `:475-517` (`QrWifiImportScreen`), `QrPendingText.kt:15-32`.

- **Escenario:** con la bóveda bloqueada, el usuario escanea en el Inspector un wifi con contraseña y toca «Añadir a la bóveda». `QrPendingText` toma el texto de `QrHandoff` y se ve la puerta de la bóveda (`VaultGate`, `:515`). El usuario va a Inicio. No llega ningún `ON_STOP` que lo borre (la pantalla no lo escucha), `onDispose` no corre al parar la actividad, y no llega ningún bloqueo porque la bóveda nunca se abrió (`vaultStateIs` solo borra si `wasOpen`, `QrPendingText.kt:22-28`). El colector de `SeguridadApp` tampoco, porque el estado no cambia. La contraseña queda en el montón hasta volver atrás o hasta que muera el proceso.
- **Por qué es Important:** el Ruling de cierre de la tarea 8 lo pide de forma expresa para esta tarea: «el texto del QR de wifi con contraseña se trata como secreto (se borra al salir y al bloquear…)». El Inspector cumple esa regla para el mismo texto (`QrInspectScreen.kt:154`, `ON_STOP` con `carriesSecret`), igual que la regla de los enlaces con datos de usuario (lote MN). Este es el único sitio del flujo donde sobrevive a salir de la app. El riesgo práctico es bajo (`FLAG_SECURE`, nada en pantalla; haría falta leer la memoria del proceso), pero es un requisito vinculante y el arreglo es pequeño.
- **Lo que no vale:** borrar con cualquier `ON_STOP`, como dice el implementador, porque el selector de «Importar» de la puerta también para la actividad y se perdería la entrega.
- **Arreglo (respuesta técnica segura, no hace falta el dueño).** He comprobado sus dos premisas:
  - `VaultLock.keepOpenOnce` solo vuelve a `false` en `MainActivity.onStart` (`MainActivity.kt:60`), así que durante el `ON_STOP` del selector sigue en `true`. Es la misma condición que ya usa `MainActivity.onStop` para soltar el aviso del portapapeles, la copia en dos pasos y el bloqueo (`MainActivity.kt:114-118`).
  - La huella usa solo `BIOMETRIC_STRONG`, con el botón «Usar contraseña maestra» (`BiometricVault.kt:216-217`). No hay respaldo con la credencial del dispositivo, que abriría otra actividad.

  La app ya distingue su propio selector: `VaultActions.import` pone `VaultLock.keepOpenOnce = true` antes de lanzarlo (`VaultBackup.kt:207`). En `QrWifiImportScreen`, un `LifecycleEventEffect(ON_STOP)` que, si `!vm.pending.wasOpen && !VaultLock.keepOpenOnce`, borra el texto y lo marca como soltado (por ejemplo `QrPendingText.leftApp()`, que pone `text = null` y `dropped = true`). Al volver, con `dropped` se sale del flujo (`done()`, de vuelta al Inspector, que ya no tiene el resultado), en lugar de abrir un formulario vacío después de desbloquear. Si en algún móvil el diálogo de la huella parara la actividad, lo peor es volver al Inspector sin el resultado, que es lo prudente. Tests: la lógica pura en `QrPendingText` (soltado sin `wasOpen`, no soltado con el selector, no soltado tras abrir la bóveda, porque ahí ya manda el formulario) y un pin de fuente. Como `QrTotpImportScreen` (`:405-447`) tiene el mismo hueco y comparte `QrPendingText`, lo razonable es arreglar los dos a la vez; si el controlador prefiere no tocar el 2FA en esta tarea, el 2FA va a `pendientes.md` como riesgo con nombre.

**I-2. Un texto en el que otros lectores ven una contraseña puede enseñarse a la vista y no borrarse al salir; y el Inspector puede enseñar otra red que la que leería la cámara.** `WifiQr.kt:176-185` y `:190-200`.

- ZXing (3.4.1, la versión del APK) no parte por campos: busca en cualquier parte del texto la primera aparición de `S:`, `P:`, `T:` y `H:` (`ResultParser.matchPrefixedField`, con `indexOf`) y se queda con el primer valor no vacío. Clavero, en cambio, salta las claves desconocidas y acepta un `:` sin escapar dentro de un valor.
- **Escenarios** (trazados sobre el código):
  - `WIFI:T:WPA;S:Casa;XP:secreta;;`: para Clavero, `XP` es desconocida y se salta. Queda `Usable` WPA con la contraseña vacía (`WifiQr.kt:92`), así que `QrKind.Wifi.password` es `null` (`QrContent.kt:114`), `textHidden` y `carriesSecret` son falsos (`QrView.kt:145-147`), y el texto completo, con «secreta», sale **a la vista** y **no se borra** al salir ni al bloquear. ZXing lee «secreta» como la contraseña, porque `P:` aparece dentro de `XP:`. Es justo lo que el Ruling pide evitar: «el texto del QR de wifi con contraseña se trata como secreto».
  - `WIFI:T:WPA;PS:otra;S:Casa;P:clave;;`: Clavero enseña y ofrece guardar «Casa»; ZXing lee la red «otra».
  - `WIFI:T:WPA;S:xP:mala;P:buena;;`: Clavero guarda la contraseña «buena»; ZXing lee «mala».
- **Severidad:** la skill `experto-seguridad-cripto` dice que un hallazgo de seguridad confirmado es Important como mínimo, y el primer escenario es un secreto (para el lector más común) que no se trata como secreto. Explotarlo pide un QR hecho a propósito, y Clavero no se conecta a nada, así que no es Critical. Es la misma razón por la que el implementador ya rechaza los campos repetidos («otro lector puede tomar el otro»), y esa regla queda incompleta sin esto.
- **Arreglo:** después de `fields`, `Malformed` si **cualquier** aparición de `S:`, `P:`, `T:` o `H:` (en mayúsculas, como ZXing) en el cuerpo no está al principio de un trozo. No vale con mirar la primera aparición de cada clave: con eso, `XP:secreta` sin un `P:` propio pasaría. Así el texto pasa a `WifiUnreadable`, que va oculto y se borra al salir y al bloquear. Las claves de empresa (`E:`, `A:`, `I:`, `PH2:`) no contienen ninguno de esos cuatro, así que esos códigos se siguen leyendo. El escritor de Clavero escapa siempre el `:`, así que no debería dispararlo nunca. La ida y vuelta de hoy **no** lo demuestra, porque el alfabeto `\ ; , : " a` no tiene ninguna de esas letras: hay que añadir `P` (o `S`) al alfabeto (ver m-5). Tests: los tres escenarios de arriba, en `WifiQrTest` y en `WifiQrInspectTest.aWifiTextThatCannotBeReadIsTreatedAsASecret`.

### Minor

**m-1. Con WPA o WEP y sin contraseña guardada, la pantalla dice que se ve la contraseña, y dibuja un código que no sirve.** `WifiQr.kt:104-108`, `:122`, `:259-263`.

- **Escenario:** una entrada wifi nueva empieza como WPA (`EntryForm.kt:285-287`). Si se guarda sin contraseña, o llega del Inspector como `WIFI:T:WPA;S:Casa;;` (que es `Usable`, con la contraseña vacía), `problem` da `null`, el código es `WIFI:T:WPA;S:Casa;P:;H:false;;` y `wifiQrLines` da `WIFI_QR_WARNING`: «Quien vea este código ve la contraseña…». No hay ninguna contraseña que ver, así que el texto no es verdad frente al código (regla de veracidad del proyecto). Además, ningún móvil se conecta a una red WPA con una contraseña vacía (la frase de paso tiene de 8 a 63 caracteres).
- `theScreenSaysTheWarningByTheNetworkOrWhyNot` (`WifiQrTest.kt:297-308`) no tiene este caso.
- **Arreglo:** un problema más en `problem`, cuando `security != "nopass"` y la contraseña está vacía, con su texto. Por ejemplo: «Esta red no tiene contraseña guardada: escríbela en «Editar», o márcala como abierta, para poder enseñar el QR.» (texto nuevo, para el Abogado). Con su test en `WifiQrTest` y en `WifiQrDrawingTest`. Es Minor porque el aviso se equivoca hacia la prudencia, pero conviene arreglarlo en esta ronda.

**m-2. Una clave en bruto (64 cifras hexadecimales en WPA; 10, 26 o 58 en WEP) se escribe entre comillas, y el móvil que lee el QR no se conecta (Duda 3).** `WifiQr.kt:128`, `WifiQrTest.kt:222-225`.

- La documentación pide comillas para un valor «ASCII … that could be interpreted as hex». Una clave de 64 cifras hexadecimales no es una frase ASCII: es la PSK. Una frase de paso WPA tiene de 8 a 63 caracteres (IEEE 802.11i), así que no hay ambigüedad.
- Los lectores Android que he podido comprobar tratan un valor entre comillas como frase:
  - la app de ZXing, `WifiConfigManager`: `quoteNonHex(password, 64)` y, para WEP, `quoteNonHex(password, 10, 26, 58)`; un valor ya entre comillas se deja como está;
  - Ajustes de AOSP, según recuerdo; confírmalo en el móvil.
- **Escenario:** una entrada WPA con la clave `0123…ef` (64 cifras) da `P:"0123…ef"`, que se lee como una frase de 64 caracteres. No es válida y el móvil no se conecta.
- **Arreglo:** sin comillas cuando `security == "WPA"` y la contraseña son exactamente 64 cifras hexadecimales, o `security == "WEP"` con 10, 26 o 58. Todo lo demás que sea solo hexadecimal sigue entre comillas. El lector no cambia (nunca descodifica el hexadecimal), y la ida y vuelta se mantiene. Añadir esos casos a `hexOnlyIsWrittenInQuotesAndReadAsText`.

**m-3. Un nombre de red con espacios al principio o al final se guarda cambiado.** `EntryForm.kt:73` (`fieldsToSave` recorta los valores que no son secretos) junto con `EntryForm.kt:572` (`applyWifiQr`).

- **Escenario:** el QR `WIFI:T:WPA;S: Casa ;P:clave;;` se enseña como « Casa » y se ofrece para la bóveda, pero se guarda «Casa». «Enseñar QR» genera entonces otra red, y el móvil no se conecta. `WIFI:S: ;;` se ofrece y se guarda sin nombre (Duda 5).
- Un SSID es un identificador que tiene que coincidir byte a byte, y los espacios en los extremos son válidos.
- **Arreglo:** que el campo `ssid` de la plantilla se guarde tal cual, como los secretos. Por ejemplo, una marca `keepAsTyped` en `TemplateField`, con su test en `WifiQrFormTest`. Si el controlador prefiere no tocar el comportamiento de la tarea 5 para lo escrito a mano: que el Inspector dé `Unsupported` cuando el nombre tiene espacios en los extremos o es solo espacios.

**m-4. Una red de defensa más, y un KDoc que ya no es verdad.** `QrContent.kt:57-60` y `:67-71`.

- El KDoc de `classifyQr` dice que «a malformed Wi-Fi … falls back to [QrKind.PlainText]». Ya no es así: ahora es `WifiUnreadable`.
- La segunda línea de defensa (`catch (e: Exception) -> PlainText(text)`) enseñaría a la vista, y sin borrarlo al salir, un texto `WIFI:` si algún día un lector lanzara. Hoy no lanza (lo comprueba el test de 30 000 textos al azar), pero la defensa existe justo para ese día.
- **Arreglo:** en el `catch`, devolver `WifiUnreadable(text)` si el texto, sin los espacios de los extremos, empieza por `WIFI:` (sin distinguir mayúsculas), y `PlainText` en otro caso. Corregir el KDoc. De paso, «Wi-Fi» → «wifi» en los comentarios de `QrInspectScreen.kt:121` y `:151`.

**m-5. La ida y vuelta no prueba los espacios ni las letras de las claves.** `WifiQrTest.kt:86-95`.

- El alfabeto `\ ; , : " a` cubre los escapes y el camino de las comillas (porque «a» es hexadecimal). No cubre que los valores no se recorten: el lector recorta la clave, `T:` y `H:`, y el texto entero.
- **Arreglo:** añadir `' '` y `P` al alfabeto. Con 8 símbolos son 585 cadenas y unos 680 000 casos, que sigue siendo rápido; si pesa, se baja la longitud a 2 para los pares. Con `P`, la ida y vuelta demuestra también que el escritor no dispara la regla de I-2.

**m-6. Nombre de la constante.** `QrInspectScreen.kt:374` y `:510` usan `QR_ADD_TOTP` para el botón del wifi. El texto es correcto («Añadir a la bóveda»), pero el nombre engaña. Conviene un alias `QR_ADD_TO_VAULT`, o renombrarla en `ToolTexts.kt:361` con sus usos.

**m-7. El Inspector enseña el nombre con `visibleText`, no con `plainReportText` (Duda 5).** `QrView.kt:123`, `:137`. El Ruling dice «muestra por `plainReportText`».

- Mi recomendación es **mantener `visibleText`** en el Inspector: marca los invisibles («Casa⟨U+202E⟩») en lugar de quitarlos, que es lo que debe hacer una herramienta que enseña los trucos. Además, `plainReportText` escondería justo la señal y rompería los tests del Inspector que ya existen.
- La pantalla nueva sí usa `plainReportText` (`WifiQrScreen.kt:209`, con su pin R9).
- Lo que falta es apuntar la desviación como «Ruling» en el registro, para que la revisión de la rama no la tome por un olvido.

**Informativos (sin cambio en esta tarea):**

- i-1. El valor «WPA» de la plantilla se enseña como «WPA/WPA2/WPA3» (`Templates.kt:29`) y el QR escribe `T:WPA`. Si una red solo WPA3 (SAE) no se conecta así desde un móvil, el problema es esa etiqueta (tarea 5, Abogado). Va a las pruebas en el móvil, junto a las que ya lista el informe.
- i-2. Una contraseña WPA solo de cifras (por ejemplo `12345678`) va como `P:"12345678"`, que es lo que pide la documentación. La app de ZXing y AOSP quitan esas comillas. Conviene probarlo en el móvil con los lectores más comunes, porque es un caso muy frecuente.
- i-3. Un nombre hexadecimal sin comillas (`S:CAFE`) se lee como el texto «CAFE», y la app de ZXing lo leería como los bytes CA FE. Es la decisión documentada de «no se descodifica nunca», que es prudente. Al reescribirlo, Clavero lo pone entre comillas, y queda sin ambigüedad.
- i-4. `WifiQrScreen` no escucha `awayInOtherApp`: en pantalla partida, la imagen sigue a la vista mientras se usa la otra app. Es lo que el usuario puso en pantalla, `FLAG_SECURE` sigue activo y el bloqueo por inactividad de la bóveda cierra la pantalla. Es igual que `OtpQrScreen`, y lo acepto.
- i-5. `theScreenSaysTheWarningByTheNetworkOrWhyNot` y los pines de `c713000` y `f2c438a` no se vieron en rojo. Son aserciones sobre funciones puras y sobre el código fuente; con leerlos basta para saber que fallarían sin el código.

## Dudas: recomendación

1. **El aviso: el encargo frente a O-4.** Mantener los dos textos. Los dos son verdad frente al código (`wifiQrLines`, `WifiQr.kt:259-263`): con contraseña, el QR la lleva (`:122`); con la red abierta, no hay contraseña que ver. La excepción es WPA o WEP con la contraseña vacía, que se arregla en m-1. O-4 es obligatorio y pide decir que se ve la contraseña, y esa frase sería falsa en una red abierta. **No es del dueño:** lo confirma el Abogado en su revisión final, y ya está en el anexo.
2. **`T:WPA2`, `WPA3`, `SAE`.** **Decisión del dueño**, de acuerdo con el controlador. Mi opinión técnica, para que decida:
   - pasar `WPA2` a `WPA` es seguro: es la misma familia PSK, y la propia app ya enseña «WPA» como «WPA/WPA2/WPA3», `Templates.kt:29`;
   - pasar `WPA3` o `SAE` a `WPA` no lo es: al reescribir `T:WPA` para una red solo SAE, puede que el móvil no se conecte. Para guardarlas bien haría falta un cuarto valor (`SAE`) en la plantilla, que es un cambio de diseño;
   - hasta que decida, lo que hay (enseñarlas y no ofrecerlas, con su nota) es correcto.
3. **Clave en bruto entre comillas.** **Respuesta técnica segura, no es del dueño:** sin comillas para 64 cifras hexadecimales en WPA y para 10, 26 o 58 en WEP (m-2). No contradice la fuente: la documentación solo pide comillas para valores ASCII, y 802.11i limita la frase de paso a 63 caracteres.
4. **El resultado del Inspector en `remember`.** **Aceptar.** Es la regla vigente para los resultados con secreto, la misma del lote MN para los enlaces con datos de usuario y la del 2FA. Se va con `ON_STOP`, con el bloqueo y al volver de otra app en ventana múltiple (`QrInspectScreen.kt:154-173`), y nunca va en estado guardado. Moverlo a un ViewModel alargaría su vida a toda la entrada de navegación. Técnico, no es del dueño.
5. **`plainReportText` frente a `visibleText`.** El Inspector se queda con `visibleText` y se apunta como Ruling (m-7). Los espacios del nombre se arreglan en m-3. Técnico.
6. **«Pegado».** De acuerdo con no añadirlo. La tarea 7 decidió qué reconoce (2FA y frase semilla), el diseño no lo pide para el wifi, y la entrada de un código ya es el Inspector. Si se quiere, a `pendientes.md` como idea. No hace falta el dueño ahora.
7. **La contraseña ante la bóveda bloqueada.** **Respuesta técnica segura, no es del dueño:** arreglarlo con `ON_STOP` y `VaultLock.keepOpenOnce` (I-1), mejor en los dos flujos a la vez. El hueco del 2FA ya existía, pero para el wifi el Ruling lo pide de forma expresa.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza** (el del informe, que comparto): la contraseña en pantalla o en memoria; un QR hostil (que enseñe una red y guarde otra, que esconda una contraseña sin tratarla como secreto, que inyecte campos o que sea enorme); una copia de la bóveda hecha en otro sitio, con valores arbitrarios.
- **Criptografía:** la tarea no la toca. No hay derivaciones, cifrados ni aleatoriedad nuevos. La única biblioteca es ZXing, que ya iba en el APK. Ninguna dependencia nueva (`git diff --stat`: ni `gradle/`, ni `build.gradle.kts`, ni el manifiesto).
- **Entrada hostil:**
  - sin ReDoS (`WifiQr.kt:240`); trabajo lineal acotado a 4 096 (`:75`); sin excepciones que escapen (índices guardados en `:150`, `:179`, `:207`, y el `try` de `classifyQr`, `QrContent.kt:67-71`); test de 30 000 textos al azar (`WifiQrTest.kt:195-212`);
  - el codificador: lo de más de 4 096 caracteres o bytes lo frena `problem` (`WifiQr.kt:107`); entre 2 331 y 4 096 bytes, ZXing lanza `WriterException`, que da `WIFI_QR_TOO_BIG` (`WifiQrScreen.kt:77-78`); cualquier otra `Exception` da `WIFI_QR_FAILED` (`:79-80`); y un `Throwable` (sin memoria) se captura en `show()` (`:155-158`), relanzando antes `CancellationException` (`:153-154`). Verificado con ZXing de verdad (`WifiQrDrawingTest.kt:143-162`);
  - quedan I-2 (un texto con una contraseña para ZXing que Clavero no trata como secreto, y otra red enseñada) y m-4 (la defensa que cae en `PlainText`).
- **El secreto, sitio por sitio:**
  - **Inspector:** `remember`, oculto hasta tocar (`hiddenLine`), y se va con `ON_STOP`, el bloqueo y la ventana múltiple (`QrInspectScreen.kt:154-173`). `WifiUnreadable` también es secreto (`QrView.kt:151`).
  - **De camino al formulario:** primero `QrHandoff`, nunca la ruta (`QrInspectScreen.kt:279`); después `QrPendingText`, en el ViewModel. Hueco I-1.
  - **Formulario:** `EntryEditSession`, en su ViewModel, sin estado guardado; se borra con el bloqueo, al salir sin girar y con `onCleared`. La contraseña se guarda como `HIDDEN` (`WifiQrFormTest.kt:38-40`).
  - **Pantalla del QR:** la configuración y la imagen, en `WifiQrViewModel`; se borran al ocultar, con `ON_STOP`, al cambiar la red, con el bloqueo o si la entrada se va (y entonces la pantalla se cierra), y con `onCleared` (`WifiQrScreen.kt:120-183`, `:201`). Cero `rememberSaveable`, `SavedStateHandle` o `Bundle` (lo fija `PlatformSourcesTest`). La configuración que queda en el ViewModel con la app en segundo plano y la bóveda abierta no es más que lo que la bóveda abierta ya tiene en memoria.
  - **Nunca:** en registros (no hay `Log` nuevo), en `contentDescription` (`WifiQrScreen.kt:223`, una constante) ni en `toString`. Esta tarea no añade portapapeles; «Copiar contraseña» del Inspector ya existía y sigue su regla A14.
- **Formato:** escapes y comillas tal como dice la wiki pública de ZXing (lo he comprobado sobre su texto). El hexadecimal no se descodifica nunca. La ida y vuelta es real y completa para los escapes (m-5: falta el espacio). La excepción de la clave en bruto va en m-2.
- **Sin conexión:** ninguna API de wifi ni ningún `Intent` (fijado con un test). Los permisos siguen siendo exactamente INTERNET y CAMERA, y hay un solo componente exportado.
- **Veredicto de seguridad:** no aprobado hasta arreglar I-1 e I-2, los dos pequeños. Los menores no bloquean, pero m-1 (veracidad) y m-2 son baratos y conviene hacerlos en la misma ronda.

## Revisión Ingeniero Móvil Senior

- **ViewModels y su ámbito:** `WifiQrViewModel` y `QrWifiImportViewModel` están ligados a su entrada de navegación (`viewModel { … }`), con el patrón del 2FA. Solo el id va en la ruta (`AppNavHost.kt:319`, `:331`, `:411`). `viewModelScope` corre en `Main.immediate`, y `turn`, `config` y las celdas se escriben solo en el hilo principal: no hay carreras.
- **Ciclo de vida:**
  - el ViewModel sigue a `store.state` sin depender de la composición (`WifiQrScreen.kt:120`), así que un bloqueo en segundo plano cierra la pantalla, que sale con `onBack` al volver (`:197-199`);
  - `LifecycleEventEffect(ON_STOP)` oculta el QR (`:201`);
  - `VaultIdleLock` y `touchesVault` cuentan el uso (`:195`, `:205`);
  - `MainActivity` declara `configChanges` para el giro (M-4 de la 1.4.2), así que girar no pasa por `ON_STOP`. Si no lo declarara, girar ocultaría el QR, que también es aceptable.
- **Concurrencia:** ZXing y el bitmap van en `Dispatchers.Default`. `encode` no se puede cancelar a mitad, pero el turno descarta el resultado tardío (`:150`). La imagen más grande es de 185 × 185 píxeles: memoria despreciable.
- **Recomposición:** pocas lecturas de estado y ninguna asignación pesada en la composición. `problem()` construye una cadena de hasta unos 40 KB en `follow`, en el hilo principal, solo cuando cambia la red. Es despreciable.
- **El formulario:** `form.load` corre en la composición antes del `LaunchedEffect` (`VaultEntryScreen.kt:610`, `:631-636`), así que `applyWifiQr` nunca ve el formulario sin cargar en el camino normal. La entrega comparte `handedOver` con el 2FA, pero solo llega una de las dos. El tipo nuevo es WIFI desde el principio (`initialType = EntryType.WIFI`, `QrInspectScreen.kt:493`).
- **jvm-check:** se mantiene solo. `WifiQr.kt` es Kotlin puro en `tools/qr/` y entra en jvm-check; `WifiQrScreen.kt` y `WifiQrDrawingTest` van en `ui/`, que jvm-check excluye (`jvm-check/build.gradle.kts:7-22`), porque jvm-check no tiene ZXing. No hace falta tocar nada.
- **Tests:** fallarían sin el código, salvo los pines de fuente, que son la convención del proyecto y frágiles por naturaleza. El test del límite de ZXing es un buen ejemplo de medir en lugar de suponer.
- **Código muerto:** `splitFields` sigue en uso para MECARD y MATMSG, y su KDoc está al día. Lo único que ya no es verdad es el KDoc de `classifyQr` (m-4). `WifiQrDrawing.Ok` no es `data class`, a propósito (`BitMatrix`).
- **Accesibilidad (R-1):** el aviso es texto y no solo color; la imagen tiene una descripción fija; el blanco detrás del código no depende del tema. TalkBack, a las pruebas en el móvil.
- **MASVS:** STORAGE (sin estado guardado ni registros), PLATFORM (ningún `Intent` ni componente exportado nuevo; `FLAG_SECURE` en toda la app), CODE (entrada no fiable acotada y sin excepciones) y PRIVACY (ningún permiso nuevo). Todo cumple, salvo I-1 (STORAGE: un secreto en memoria al salir) e I-2 (CODE y STORAGE: entrada no fiable leída de forma distinta a como la leen otros lectores, con un secreto que no se trata como tal).

## Textos para el Abogado Digital (revisión final)

No añado textos. Los de la tarea están en el anexo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, «Clavero 2.0, tarea 9: QR de wifi»). Si I-1 e I-2 se arreglan sin textos nuevos (volver al Inspector; `WifiUnreadable` ya tiene los suyos), no cambia ninguno. Para el Abogado, de esta revisión:
- m-1: el texto nuevo que propongo, si se adopta («Esta red no tiene contraseña guardada: escríbela en «Editar», o márcala como abierta, para poder enseñar el QR.»).
- Duda 1: los dos avisos, el de O-4 con contraseña y el de la red abierta.
- i-1: la etiqueta «WPA/WPA2/WPA3» frente a lo que el QR escribe (`T:WPA`).

## Veredicto

**No aprobada: 0 Critical, 2 Important (I-1 e I-2) y 7 menores (m-1 a m-7).** Sigo la convención de `progress.md`: con un Important abierto, la tarea no se aprueba.
- I-1 (la contraseña ante la bóveda bloqueada al salir de la app) tiene un arreglo pequeño con el mecanismo que ya existe (`VaultLock.keepOpenOnce`, con sus premisas comprobadas), y conviene aplicarlo también al 2FA.
- I-2 (claves ocultas dentro de otras, como `XP:`) se cierra con una regla en el lector y sus tests.
- m-1 (un texto que no es verdad), m-2 y m-3 son baratos; recomiendo hacerlos en la misma ronda. m-4 a m-7 son de pulido.
- La Duda 2 es del dueño. Las demás tienen una respuesta técnica, que está arriba.
- Tras la ronda, una re-revisión, con las dos suites en un clon limpio.
