# Tarea 7 de la 2.0: el formulario (informe del implementador)

Rama `ccr-0aa27f95-u038cd`, base `1651eec`. Commits: `162f395`, `1bb7faf` (en curso) `141cfa2` («2.0, tarea 7: el formulario») y `7dde42e` (el selector se cierra al bloquear; el diálogo de los cambios dice qué falta). Encima de la base está también `42c7706` del controlador (Maestro después de la tarea 12), que no es de esta tarea.

## Lo hecho

### `vault/FormHelpers.kt` (nuevo)
- **`titleFromUrl(url): String?`** (`FormHelpers.kt:39`): el host de `parseLink` (como un navegador: `\` cierra la autoridad, los datos de usuario `usuario:clave@` nunca entran), en la forma ASCII con la que conecta el navegador, sin `www.` al principio y con la primera letra en mayúscula («https://correo.ejemplo.es» → «Correo.ejemplo.es»). Solo `http`, `https` y un dominio escrito sin esquema («ejemplo.es», «www.ejemplo.es/cuenta»: etiquetas con puntos, sin `@`, `:` ni espacios, que se leen como `https://…` y vuelven a pasar por `parseLink`). `null` si no hay host o para otro esquema (`otpauth://totp/…` no da «Totp», `ftp://`, `mailto:`, `javascript:`). Tope de 4096 caracteres antes de leerla (decisión (a)). Sin red.
- **`PastedKind { OTPAUTH, SEED }` y `detectPasted(text, seeds: SeedLists?)`** (`:99`): `OTPAUTH` si empieza por `otpauth://` (sin distinguir mayúsculas, tras quitar espacios) y `readOtpAuth` lo lee como `Usable` (uno con parámetros fuera de rango no se propone: CRY-B02, se rechaza y no se cambia); `SEED` si son 12, 15, 18, 21 o 24 palabras de una sola lista BIP-39 (inglés o español) con suma de control válida (`Bip39.entropyOf`; la entropía se borra en el acto, `:109`). Tope de 1000 caracteres antes de partir en palabras (`:104`); minúsculas con `Locale.ROOT`; separa por cualquier espacio Unicode.
- **`SeedLists`** (`:77`): la lista inglesa en NFC, la española en NFC y la española sin acentos (la lista del APK está escrita descompuesta, una letra y su acento; sin acentos sus 2048 palabras siguen siendo distintas, comprobado). Una lista que no son 2048 palabras distintas se deja fuera: sin listas, ninguna frase se reconoce.
- **`pastedBlock(before, after)`** (`:121`): el bloque que un cambio del campo metió de una vez (prefijo y sufijo comunes), si tiene 10 caracteres o más. Es «la acción de pegar del campo» (ver «Dudas», 1).
- **`NewEntryChoice` / `NEW_ENTRY_CHOICES`** (`:138`): Acceso, Código 2FA, Nota, Frase semilla, Tarjeta bancaria, Red wifi, Licencia, Pasaporte, Otro, en ese orden; las etiquetas son las de los tipos (R-4) y «Otro» es un «Acceso» vacío.
- **`strengthText` y `strengthOf`** (`:161`): «Fuerza estimada: » + `Strength.label` de la estimación de «Salud» (`estimateBits`), leída hasta 10 000 caracteres.
- **`templateSecretIsPassword(type, key)`** (`:174`): qué secreto de plantilla es una contraseña que elige el usuario (generador y fuerza): solo la del wifi.
- Los textos nuevos, como constantes (abajo, «Textos para el Abogado Digital»).

### `vault/EntryForm.kt` (`EntryEditSession`)
- **`typed(slot, value)`** (`EntryForm.kt:411`): el único camino de los cambios de Título, Usuario, Contraseña, Web, Notas y Palabras. Pone el valor; la Web rellena un título vacío y lo sigue mientras el título sea el que puso (`autoTitle`); un título cargado o escrito por el usuario no se toca. Si el cambio fue un bloque pegado que parece 2FA o frase, y la entrada es nueva y no es ya de ese tipo, guarda la pregunta en `pasteOffer` (`PasteOffer`, cuyo `toString` no enseña ningún texto, `:122`). Lo pegado se queda en el campo hasta la respuesta.
- **`acceptPaste()`** (`:446`): quita lo pegado del campo (vuelve a lo de antes, si el campo aún tiene lo pegado), cambia el tipo (`changeType`) y, para una frase, la pone en «Palabras»; para un 2FA devuelve el enlace, que el ViewModel lee con `applyOtpAuth` (el mismo camino que escanear, donde está CRY-B02). Tras un bloqueo no hace nada. **`declinePaste()`** lo deja todo como se pegó.
- **`hasUnsavedChanges`** (`:392`): compara una instantánea del formulario tomada al final de `load` (`baseline`, en una celda para que Compose la observe) con la de ahora (`FormSnapshot`, `toString` sin valores, `:143`). Sin el tipo (una entrada nueva que solo cambia de tipo no pierde nada), sin lo que solo está en pantalla (errores, avisos, diálogos), con los textos que se recortan al guardar ya recortados, las etiquetas normalizadas y los valores de plantilla vacíos como ausentes. Como se compara con lo cargado y no con la entrada de la bóveda, lo que la carga ordena (fechas en DD/MM/AAAA, un id repetido, la caducidad de una copia en la plantilla, los valores por defecto del wifi) nunca es un cambio.
- **`back()`**: con cambios abre el diálogo (`askingToLeave`); sin ellos, `finish()`. **`keepEditing()`**.
- `clear()` borra también `pasteOffer`, `askingToLeave`, `autoTitle` y `baseline`; `load()` vuelve a tomar la instantánea.

### `vault/MasterPolicy.kt`
- `BIP39_SPANISH_LIST` y **`WordLists.seedLists()`** (`MasterPolicy.kt:200`): las dos listas BIP-39, una vez por proceso y fuera del hilo principal (como F6); si no se lee alguna, `null`, no se guarda y el siguiente formulario lo vuelve a intentar.

### Pantallas
- **`ui/screens/NewEntryChooser.kt`** (nuevo): «¿Qué quieres guardar?», con los nueve tipos y el icono de cada uno (decoración que TalkBack no lee, `DecoratedLabel`), «Otro» con su línea, y «Cancelar».
- **`VaultScreen.kt`**: el «+» (`:266`) y «Crear la primera» de la lista vacía (`VaultList.kt`, `EmptyVaultActions`) abren el selector en «Bóveda» (`newEntry`, `:156`); en «Códigos» siguen abriendo un 2FA directamente. El selector solo se dibuja con la bóveda abierta y un bloqueo lo cierra del todo (`LaunchedEffect(unlocked) { if (!unlocked) choosing = false }`), así que no vuelve solo al desbloquear (test).
- **`VaultEntryScreen.kt`**:
  - `EntryEditViewModel` recibe `WordLists` y carga `seedLists` al nacer (`:382`); `acceptPaste()` (`:471`) y **`trySave()`** (`:479`): la validación y la construcción de la entrada, movidas tal cual desde el `onClick` del botón, para que «Guardar» del botón y del diálogo sean el mismo código.
  - `BackHandler(enabled = form.hasUnsavedChanges) { form.back() }` (`:610`) y la flecha de la barra llaman a `form.back()`. Sin cambios, el «Atrás» del sistema (y su gesto predictivo) va como siempre.
  - Los campos pasan por `form.typed(...)`; el generador escribe en la celda directamente (`onGenerated`), así que nunca cuenta como pegado.
  - El diálogo de lo pegado (`:748`): la pregunta y «Se quita del campo donde lo has pegado.», con «Sí» y «No, dejarlo así»; nunca el texto pegado.
  - El diálogo «¿Guardar los cambios?» (`:763`): «Guardar» (`trySave`; si falta algo, el error sale dentro del mismo diálogo, que se queda abierto, y no al final del formulario; test), «Descartar» y «Seguir editando»; cerrarlo de otra forma es «Seguir editando».
- **`EntryFields.kt`**: `PasswordWithTools` (`EntryFields.kt:240`): el campo de contraseña, la fuerza debajo mientras se escribe (en palabras; el color solo acompaña, R-1) y «🎲 Generar una al azar». Va en la contraseña del acceso, la passphrase de la frase, la contraseña del wifi (`:282`) y cada campo oculto del usuario (`:308`). Las Notas pasan por `form.typed(FormSlot.NOTES, …)` (`:404`).

### Maestro
- `borra-datos/p05-ayuda-codigos-qr.yaml`: tras «Crear la primera», espera «¿Qué quieres guardar?» y pulsa «Código 2FA» (ya no desliza la fila de tipos). `.maestro/README.md`: la comprobación 3 y la nota de la fila de tipos al día. **Cambiado sin ejecutar** (aquí no hay emulador).

## Tests

- `FormHelpersTest` (nuevo, 16): `titleFromHost`, `titleNullWithoutHost`, `titleIgnoresUserinfo`, `detectsOtpauth`, `detectsSeedOnlyWithChecksum` (frase válida de 12 a 24 palabras, la misma con la suma mal, 11, 13 y 25 palabras, una palabra fuera de la lista, sin listas), `detectsSpanishSeedComposedDecomposedAndWithoutAccents`, `ordinaryTextIsNothing` (incluido un texto por encima del tope), `seedListsThatCannotBeReadRecogniseNoPhrase`, `typingNeverReachesDetection`, `aPasteIsTheInsertedBlock`, `theChooserOffersTheTypesInOrder`, `strengthWhileTyping`, `onlyPasswordsTheUserChoosesGetTheGenerator`, `theListsOfThePhrasesAreReadFromTheAssets`, `aPasteIsOnlyLookedAtInTheFieldsOwnChange` (solo `EntryForm.typed` llama a `detectPasted`; ningún `readClipboardText` nuevo), `backAsksWithChangesAndThePlusOpensTheChooser`. Las frases se construyen con `Bip39.phraseOf` sobre entropía inventada y las listas del APK, no se copian de ningún sitio.
- `EntryEditSessionTest` (+7, de 9 a 16): `unsavedChangesDetected`, `noChangesNoDialog` (acceso, nueva, wifi nueva, tarjeta con la caducidad de una copia, ids repetidos, nota con formato, 2FA; cambiar solo el tipo; escribir y borrar; errores en pantalla; tras un bloqueo), `theUrlFillsAnEmptyTitleOnly`, `aPastedSeedIsOfferedAndTakenOutOfTheField`, `aPastedOtpauthIsHandedToTheReaderOfTheLinks`, `aPasteIsOnlyOfferedWhereItChangesSomething`, `aLockWhileAskingDropsThePasteAndALateAnswerDoesNothing`; `assertEmpty` comprueba además `pasteOffer` y `askingToLeave`.

### TDD
- **RED** (con las funciones como esqueletos que devolvían `null`/`false`, para que fallaran aserciones y no la compilación): 29 tests, **16 fallidos**, 15 por aserción y 1 por `NullPointerException` (`pasteOffer.value!!`). Pasaban los que esperan `null` (`titleNullWithoutHost`, `ordinaryTextIsNothing`, `typingNeverReachesDetection`, `aPasteIsOnlyOfferedWhereItChangesSomething`).
- **GREEN**: al implementar falló uno, `ordinaryTextIsNothing`: el caso «frase + 5000 espacios al final» se reconocía, porque los espacios de los extremos se quitan antes del tope. Es correcto (no es un texto largo, es una frase); el caso pasó a 5000 espacios **dentro**, que sí supera el tope. Después, todo en verde.
- Tras la autorrevisión (selector al bloquear y error en el diálogo) se volvió a pasar todo.
- **Suite completa:** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` BUILD SUCCESSFUL: **app 2175 tests, 0 fallos** (antes 2152); `./gradlew -p jvm-check test`: **2138, 0 fallos** (antes 2115)..

## Archivos
- Nuevos: `app/src/main/java/app/seguridad/vault/FormHelpers.kt`, `app/src/main/java/app/seguridad/ui/screens/NewEntryChooser.kt`, `app/src/test/java/app/seguridad/vault/FormHelpersTest.kt`.
- Cambiados: `vault/EntryForm.kt`, `vault/MasterPolicy.kt`, `ui/screens/VaultEntryScreen.kt`, `ui/screens/EntryFields.kt`, `ui/screens/VaultScreen.kt`, `ui/screens/VaultList.kt`, `app/src/test/java/app/seguridad/vault/EntryEditSessionTest.kt`, `.maestro/borra-datos/p05-ayuda-codigos-qr.yaml`, `.maestro/README.md`.

## Autorrevisión
- `git diff --stat 1651eec..HEAD`: ningún `AndroidManifest.xml`, `build.gradle.kts`, catálogo de versiones ni archivo de `res/` cambia; los únicos archivos son los de «Archivos» (y los dos del traspaso de `42c7706`, del controlador).
- Ningún permiso, componente exportado, dependencia ni host nuevo; `FLAG_SECURE` sin tocar; ningún registro.
- Lo pegado, la pregunta y la instantánea viven en `EntryEditSession` (el ViewModel), nunca en `rememberSaveable` ni en un `Bundle`, ni en la ruta de navegación, ni en un snackbar; se borran con `clear()` (bloqueo, salir sin giro, `onCleared`). Los `toString` de `PasteOffer` y `FormSnapshot` no enseñan valores.
- El portapapeles no se lee nunca por su cuenta: lo pegado se ve en el valor del campo. El único `readClipboardText` del editor sigue siendo el «Pegar enlace» del 2FA, que el usuario pulsa (test).
- Proponer el tipo siempre pregunta; nada cambia sin el «Sí».
- `trySave` es el código del botón movido tal cual (mismos textos, mismo orden de comprobaciones); los tests que leen la pantalla (`VaultTotpTest`, `EntryFormTest`) siguen encontrando sus líneas.
- El título de la URL nunca lleva `usuario:clave@` (test) y solo rellena un título vacío o el que puso él mismo.

## Dudas (decididas; el controlador puede cambiarlas)
1. **Qué es «la acción de pegar del campo».** Un cambio del valor del campo que mete de una vez un bloque de 10 caracteres o más (`pastedBlock`): cubre el «Pegar» del menú del campo (el antiguo y el nuevo de Compose), Ctrl+V y el portapapeles del teclado, sin leer el portapapeles. También cuenta un bloque que mete el autocompletado o el dictado; si es un 2FA o una frase, la pregunta tiene sentido igual. Escribir letra a letra o palabra a palabra nunca llega a `detectPasted` (test). Interceptar solo el «Pegar» del menú dejaría fuera el portapapeles del teclado, que es lo más usado.
2. **Dónde vive `hasUnsavedChanges`:** en `EntryEditSession` (la sesión del ViewModel, `form.hasUnsavedChanges`), no como propiedad directa de `EntryEditViewModel`: ahí se prueba en la JVM sin Android, como el resto del estado del formulario.
2b. **La firma de `detectPasted`** lleva un segundo parámetro, las listas (`SeedLists?`), porque se leen de `assets` fuera del hilo principal; con `null` solo reconoce los enlaces.
3. **Campos donde se mira lo pegado:** Título, Usuario, Contraseña, Web, Notas y Palabras de una entrada **nueva**. No en los campos de plantilla ni en los propios (lo pegado ahí es casi siempre lo que toca). Una entrada existente no cambia de tipo, así que no pregunta.
4. **Un `otpauth://` con parámetros fuera de rango no se propone** (`Usable` solo): proponerlo para después decir que no se admite sería raro; queda pegado tal cual. Tampoco `otpauth-migration://`: tiene su «Pegar enlace» en el 2FA.
5. **Generador:** junto a la contraseña del acceso, la passphrase, la contraseña del wifi y cada campo oculto del usuario. **No** junto al número de la tarjeta, la clave de una licencia ni el número del pasaporte, aunque sean secretos de texto: los da otro (el banco, el vendedor, el Estado); generarlos no sirve y podría confundir. Está en un solo predicado probado (`templateSecretIsPassword`), así que cambiarlo es una línea. CVV y PIN tampoco (R-3: opcionales y vacíos). La fuerza sale en los mismos campos.
6. **Etiquetas del selector:** las de los tipos (R-4: «Tarjeta bancaria», «Red wifi», «Código 2FA»), no las cortas del brief («Tarjeta», «Wifi», «2FA»), para que coincidan con la fila de tipos del formulario. El orden es el del brief.
7. **«Otro»** abre un «Acceso» vacío, como pide el diseño («acceso vacío con campos libres»), con la línea «Un acceso vacío con los campos que quieras»; no abre solo el diálogo de «Añadir campo».
8. **«Códigos»:** su botón «Código» y su «Crear la primera» siguen abriendo un 2FA sin preguntar (es la pestaña de los códigos).
9. **El título de un dominio internacional** sale en la forma ASCII (`xn--…`, p. ej. «Xn--espaa-rta.example»), la que usa el navegador para conectar: así un nombre en otra escritura nunca se ve como otro parecido. Se puede cambiar a mano. El título sigue la Web mientras sea el que puso ella; al escribir uno propio, se queda.
10. **Cambiar de pestaña con cambios** los sigue perdiendo sin preguntar (como hoy, `left(false)`): el brief habla de «al volver». Si se quiere, el diálogo podría salir también ahí.

## Pruebas en el móvil (tarea 11)
- Pegar un `otpauth://` en la contraseña de un acceso nuevo, desde el menú del campo y desde el portapapeles del teclado: sale la pregunta; «Sí» pasa a 2FA con el código y deja la contraseña vacía.
- Pegar una frase de 12 palabras (de prueba) en las notas de una nota nueva: «Sí» pasa a «Frase semilla» con las palabras, y las notas quedan como antes.
- Escribir una Web en un acceso nuevo sin título: el título sale del dominio.
- «Atrás» (flecha, botón y gesto predictivo) sin cambios: vuelve sin preguntar; con cambios: el diálogo, y sus tres botones; girar el móvil con el diálogo o la pregunta abiertos.
- El «+» y «Crear la primera»: el selector, con la letra al 200 % y con TalkBack (que no lea los iconos).
- La fuerza bajo la contraseña al escribir, y el generador en un campo oculto propio.
- Bloquear con la pregunta de lo pegado abierta: desaparece y, al abrir, el formulario sale de la bóveda.

## Revisión experto-seguridad-cripto
- **Portapapeles:** ninguna lectura nueva; lo pegado se ve en el valor del campo (`FormHelpers.kt:121`, `EntryForm.kt:411`); test `aPasteIsOnlyLookedAtInTheFieldsOwnChange`. El «Pegar enlace» del 2FA sigue siendo la única lectura del editor (`VaultEntryScreen.kt`, `readClipboardText(context)?.let(vm::applyOtpAuth)`).
- **Secretos en memoria:** la frase y el enlace pegados solo en `PasteOffer`, dentro de la sesión del ViewModel; se borran en `clear()` (`EntryForm.kt:519`), y una respuesta tardía tras el bloqueo no hace nada (`EntryForm.kt:446`, test `aLockWhileAskingDropsThePasteAndALateAnswerDoesNothing`). Nada en `rememberSaveable`, `Bundle`, ruta, snackbar ni registros; `toString` sin valores (`EntryForm.kt:122`, `:143`). Las cadenas de Kotlin no se pueden borrar (como en el resto del formulario); la entropía de la suma BIP-39 sí se borra (`FormHelpers.kt:109`).
- **Entradas enormes (decisión (a)):** topes antes de leer: 4096 caracteres para el título (`FormHelpers.kt:54`), 1000 para una frase (`:104`), `OTPAUTH_MAX_CHARS` dentro de `readOtpAuth`, 10 000 para la fuerza. `pastedBlock` es lineal.
- **CRY-B02:** un enlace se lee con `readOtpAuth`; fuera de rango no se propone ni se cambia; el «Sí» pasa por `applyOtpAuth`, el mismo camino que escanear (`VaultEntryScreen.kt:471`).
- **ReDoS:** la expresión del dominio sin esquema (`BARE_DOMAIN`, `FormHelpers.kt`) repite grupos que empiezan por un `.` literal, sin ambigüedad, y la entrada está acotada a 4096 caracteres; la de los separadores es una clase simple.
- **Registros:** ningún `Log`, `println` ni `printStackTrace` en el diff (`git diff 1651eec..HEAD | grep`).
- **URL:** `parseLink` como un navegador; `usuario:clave@` nunca llega al título (test `titleIgnoresUserinfo`); forma ASCII contra parecidos; sin red.
- **Nada sin preguntar:** el tipo solo cambia con «Sí» (`EntryForm.kt:446`).
- **Primitivas:** solo `MessageDigest` SHA-256 de la suma BIP-39 que ya había (`Bip39.entropyOf`); nada nuevo.

## Revisión Ingeniero Móvil Senior
- **Estado:** todo en `EntryEditSession`/`EntryEditViewModel` (sobrevive al giro, muere con el proceso y con el bloqueo); `hasUnsavedChanges` lee celdas de Compose, así que `BackHandler(enabled = …)` se recalcula solo (`VaultEntryScreen.kt:610`).
- **Atrás:** `BackHandler` solo con cambios, para no romper el gesto predictivo ni la limpieza de `left()` cuando no hay nada que perder; la flecha usa el mismo `form.back()`. Con la bóveda bloqueada la pantalla sale antes (`LockedNotice`) y no hay diálogo.
- **Concurrencia:** las listas, en `WordLists` (mutex, `Dispatchers.IO`, una vez por proceso, `MasterPolicy.kt:200`); `seedLists` es `@Volatile`. `detectPasted` corre en el hilo principal solo cuando entra un bloque, con topes: barato.
- **Un solo código de guardado:** `trySave` (`VaultEntryScreen.kt:479`) para el botón y el diálogo.
- **Accesibilidad (R-1):** la fuerza en palabras (el color acompaña); iconos del selector como decoración (`DecoratedLabel`); el diálogo se cierra como «Seguir editando».
- **Compose:** claves de filas sin cambios (I-3); el selector es un `AlertDialog` con desplazamiento (la app no tiene hojas inferiores); `FilterObscuredTouches` en los tres diálogos nuevos.
- **Lint:** 0 errores / 20 avisos, los mismos de antes; ninguno en los archivos de esta tarea..

## Textos para el Abogado Digital (revisión final)
Todos nuevos (`vault/FormHelpers.kt`, salvo donde se dice):
- «¿Qué quieres guardar?» (título del selector del «+»).
- «Otro» y, debajo, «Un acceso vacío con los campos que quieras».
- Las demás opciones del selector son las etiquetas de los tipos que ya existen: «Acceso», «Código 2FA», «Nota», «Frase semilla», «Tarjeta bancaria», «Red wifi», «Licencia», «Pasaporte»; y «Cancelar».
- «Fuerza estimada: » + «Débil», «Aceptable», «Fuerte» o «Muy fuerte» (las etiquetas que ya usa el Generador). Dice «estimada»: no afirma que una contraseña sea segura.
- «Generar una al azar» (ya existía en el wifi; ahora también en la contraseña, la passphrase y los campos ocultos; antes, en el acceso y la passphrase, decía «🎲 Generar una al azar» con el icono leído por TalkBack).
- «Parece un código 2FA. ¿Guardarlo como 2FA?»
- «Parece una frase semilla. ¿Guardarla como frase semilla?»
- «Se quita del campo donde lo has pegado.»
- «Sí» y «No, dejarlo así».
- «¿Guardar los cambios?», con «Guardar», «Descartar» y «Seguir editando» (si «Guardar» no puede, el diálogo enseña el error de siempre, p. ej. «Ponle un nombre»).
- Ninguno nombra otra app ni usa palabras prohibidas (`SourceTextsTest` en verde).

## Ronda 1 de arreglos (`e7cd9ae`, sobre `0f64471`)

D-1 y D-2 quedaron aceptados por el controlador; no se tocan.

- **I-1, cambiar de pestaña con cambios.**
  - `LeaveGuard` (`vault/EntryForm.kt:610`) solo pasa la pregunta («¿tiene cambios?») y una llamada (adónde ir), nunca texto. `AppNavHost` lo crea y lo da con `LocalLeaveGuard` (`ui/AppNavHost.kt:267`). Cada pestaña llama a `leaveGuard.leave { nav.navigate(…) }` (`:276`).
  - El editor se registra mientras está en pantalla (`VaultEntryScreen.kt:591`, `form.guardedBy`).
  - `askToLeave(go)` (`EntryForm.kt:488`) abre el mismo diálogo y guarda adónde ir en `pendingLeave`, que nunca lleva texto y se borra en `clear()`:
    - «Descartar» (`discard`, `:511`) borra lo escrito (`clear()`) y abre la pestaña.
    - «Guardar» (`saveFromDialog`, `VaultEntryScreen.kt:552`) pasa por `trySave`. Si faltan datos, el diálogo se queda y dice por qué. Si se puede guardar, el diálogo se cierra y la pestaña se abre cuando termina el guardado (`:429`). Si el guardado falla, no se va a ninguna parte (`:443`).
    - «Seguir editando» se queda y olvida la pestaña.
  - Un bloqueo lo borra todo, también adónde se iba, y sin diálogo.
  - Si la actividad se vuelve a crear, el destino de la vieja se descarta (`dropLeave`, `:533`, desde `left(true)`).
  - «Atrás» usa el mismo camino (`back() = askToLeave(null)`).
- **m-1:** `askToLeave` borra el error viejo al abrir el diálogo.
- **m-2:** con un guardado en marcha, «Atrás» no pregunta (la pantalla sale sola al terminar), una pestaña se abre en el acto (el guardado sigue en el ViewModel) y «Descartar» está desactivado y no hace nada (`VaultEntryScreen.kt:805`, `discard`).
- **m-3:** los tres botones van uno por línea (`Column(horizontalAlignment = Alignment.End)` en `confirmButton`). Ninguno se corta con la letra al 200 %. Ningún otro diálogo de la app tenía tres botones. Queda para la prueba en el móvil.
- **m-4:** `estimateBits` se calcula una vez por cambio del texto: `remember(value) { strengthOf(value) }` (`EntryFields.kt:243`). El texto sale de `strengthLine(strength)`.

**Tests nuevos** (`EntryEditSessionTest`, +7, de 16 a 23):
- `aTabWithChangesAsksAndDiscardGoesWithNothingTyped`
- `keepEditingStaysAndForgetsTheTab`
- `saveFromATabsDialogGoesOnceSaved`
- `aLockWhileAskingForATabGoesNowhere`
- `theDialogOpensWithoutAnOldError`
- `duringASaveNothingIsAsked`
- `theGuardPassesOnlyAQuestionAndACall`

`FormHelpersTest` comprueba en el código que las pestañas consultan el guardián, que el diálogo usa `saveFromDialog` (que llama a `trySave`) y `discard` desactivado con `saving`, y que hay una sola estimación (`strengthLine`).

**Comandos y salida:**
- Los tests de lo cambiado: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.FormHelpersTest' --tests 'app.seguridad.vault.EntryEditSessionTest' --tests 'app.seguridad.vault.EntryFormTest' --tests 'app.seguridad.vault.VaultTotpTest' --tests 'app.seguridad.PlatformSourcesTest' --no-daemon --max-workers=1`: todos en verde.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: `BUILD SUCCESSFUL in 4m 38s`. **App: 2182 tests, 0 fallos.** Lint: 0 errores y 20 avisos, los mismos.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL. **jvm-check: 2145 tests, 0 fallos.**

**Textos:** ninguno nuevo. El diálogo de las pestañas es el mismo «¿Guardar los cambios?» con «Guardar», «Descartar» y «Seguir editando».

**Prueba en el móvil (tarea 11):**
- Con cambios, tocar «Códigos» o «Herramientas» y probar los tres botones; con «Guardar», la pestaña se abre ya guardada.
- Tocar una pestaña durante un guardado.
- Los tres botones del diálogo con la letra al 200 %.

**Revisión experto-seguridad-cripto (ronda 1):**
- Lo único que cruza hacia la navegación es un Boolean y una llamada (`EntryForm.kt:610`).
- `pendingLeave` no lleva datos y se borra en `clear()` (bloqueo, salida, `onCleared`), así que un toque tardío tras el bloqueo no navega (test `aLockWhileAskingForATabGoesNowhere`).
- «Descartar» borra lo escrito antes de navegar.

**Revisión Ingeniero Móvil Senior (ronda 1):**
- `LocalLeaveGuard` es un `staticCompositionLocalOf` con un `LeaveGuard` en `remember` de `AppNavHost`.
- El registro se hace en un `DisposableEffect(form, leaveGuard)`, y el que deja el registro solo se quita a sí mismo (test).
- La llamada pendiente apunta al `NavController` de la actividad. Se descarta al recrearla para que no quede uno viejo (`dropLeave`).

## Ronda 2 de arreglos (`27e817a`, sobre `6a99831`)

### N-1: una pestaña tocada durante un guardado espera a que termine
`vault/EntryForm.kt:491` cambia `saving.value -> if (go != null) pendingLeave = go`:
- No hay diálogo. La pestaña se abre cuando el guardado termina bien (`VaultEntryScreen.kt`, `takeLeave` tras `upsert`).
- Si el guardado falla, `finally` descarta el destino y el formulario se queda con su error a la vista. Ya no se pierde la entrada sin aviso.
- «Atrás» durante un guardado sigue sin hacer nada: la pantalla sale sola al terminar.
- Un bloqueo borra el destino (`clear()`).

Test: `duringASaveNothingIsAsked` ahora comprueba que la pestaña no se abre mientras dura el guardado y que `takeLeave()` la devuelve una sola vez.

### Comprobación de seguridad: ¿un guardado en cola puede caer en otra bóveda o después del bloqueo?

**¿En otra bóveda? No.** Cada bóveda tiene su propio `VaultStore`, con su archivo y su mutex: `AppContainer.kt:387` crea un `VaultStore` por bóveda. El ViewModel del editor guarda la instancia de la bóveda en la que se abrió. Por eso un `upsert` solo puede escribir en `vault` de ese mismo objeto (`VaultStore.kt:768`, `val v = vault`), nunca en el archivo de otra bóveda.
- Al cambiar de bóveda, `switchTo` bloquea la vieja y espera a su mutex (`AppContainer.kt:496-510`, `lock` + `awaitIdle`).
- Un guardado de la vieja que espera ese mutex entra con la vieja ya `Locked` y da `false`, porque `modifyData` exige `Unlocked`.
- La derivación que abre otra bóveda corre bajo el mutex de esa otra bóveda, no bajo el de esta.

**¿Después del bloqueo, en la misma bóveda? Sí.** El caso es un guardado pedido antes de un bloqueo que llega al almacén después de que la misma bóveda se haya abierto otra vez:
- Pasa con una derivación de un nuevo desbloqueo que va delante de él en la cola, o con un `launch` que se ejecuta tarde.
- `modifyData` tomaba la época **al entrar** al mutex (`since = epoch`). Por eso escribía la entrada en la bóveda ya reabierta, en contra de la decisión (b) de la 1.4.0: un bloqueo a mitad no deja un resultado tardío.
- RED: `aSaveWaitingAcrossALockOrASwitchLandsNowhere` falló con «a save of before the lock is not written after it».

**Arreglo:**
- `modifyData` acepta `sinceLock` y se niega a escribir si la época cambió (`VaultStore.kt:772`, `if (sinceLock != null && since != sinceLock) return@withLock false`).
- `upsert(entry, sinceLock)` lo pasa (`VaultStore.kt:810`).
- `EntryEditViewModel.save` toma `store.lockEpoch` en el momento del «Guardar» (`VaultEntryScreen.kt:427`).
- Los demás llamadores no cambian (`sinceLock = null`).

**Test** (`AppContainerVaultsTest.kt:188`, `aSaveWaitingAcrossALockOrASwitchLandsNowhere`):
1. Bloquear y volver a abrir la misma bóveda: un guardado con la época de antes se rechaza y no escribe nada; con la época de ahora, se escribe.
2. Un guardado de A se retiene en el hilo de cálculo con el mutex de A tomado. Otro guardado de A espera en cola y se cambia a B:
   - el guardado que ya había empezado cae en A;
   - el que esperaba no cae en ningún sitio;
   - el archivo de B no cambia, y ni A ni B tienen la entrada que esperaba.

### Comandos y salida
- Tests de lo cambiado: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.AppContainerVaultsTest' --tests 'app.seguridad.vault.*' --tests 'app.seguridad.PlatformSourcesTest' --no-daemon --max-workers=1`, en verde. El RED fue de 37 tests con 2 fallos, por aserción.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL in 4m 32s. **App: 2183 tests, 0 fallos.** Lint: 0 errores y 20 avisos, los mismos de antes.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL in 1m 55s. **jvm-check: 2146 tests, 0 fallos.**

No hay textos nuevos. Para la prueba en el móvil (tarea 11): tocar una pestaña durante un guardado. La pestaña se abre al terminar.

### Revisión experto-seguridad-cripto (ronda 2)
- Un guardado nunca escribe después de un bloqueo, aunque la bóveda se haya reabierto (`VaultStore.kt:772`).
- Nunca escribe en otra bóveda: cada bóveda tiene su instancia y su mutex (`AppContainer.kt:387`, `:496-510`).
- Con la época del «Guardar» queda cerrada la carrera entre comprobar y usar (decisión (b)).

### Revisión Ingeniero Móvil Senior (ronda 2)
- El destino pendiente se ejecuta una sola vez y solo tras un guardado correcto.
- Si el guardado falla, se descarta y el error se ve.
- El parámetro `sinceLock` es opcional, así que los demás llamadores de `upsert` siguen igual.

## Ronda 3 de arreglos (`bb74e6d`)

- **M-A, `addAttachment`:**
  - Toma la época antes de leer el documento (`VaultStore.kt:1212`, `val sinceLock = epoch`) y la pasa a `modify(sinceLock = …)`.
  - Si hubo un bloqueo mientras se leía, no guarda nada, aunque la bóveda se haya vuelto a abrir antes de acabar la lectura. Da `false`, sin referencia y sin archivo. El archivo no se llega a escribir, porque `attachments.add` está dentro de `modify`, después de la comprobación.
  - La lectura sigue sin tener el mutex: guardar, bloquear y desbloquear siguen pasando mientras dura.
- **Info-1, el valor por defecto:**
  - `modifyData` y `modify` tienen ahora `sinceLock: Int = epoch` (`VaultStore.kt:747` y `:766`). El valor se lee al llamar, antes de esperar el mutex, y la comprobación es `if (since != sinceLock)` (`:776`).
  - `upsert(entry, sinceLock: Int? = null)` pasa `sinceLock ?: epoch` (`:814`).
  - El KDoc de `epoch` (`:318`) dice ahora qué pasa de verdad: cada guardado por `modifyData` lee la época al pedirse. El «Guardar» del editor da la de su toque, y `addAttachment` la de antes de leer el documento. `create`, `unlock` y `unlockWithKey` también la leen antes del mutex.
  - **Rechazos de más: ninguno.** `epoch` solo cambia en `lock()` (`VaultStore.kt`, el único `epoch++`). El valor nuevo solo cambia algo en un caso: hubo un bloqueo entre la llamada y la entrada al mutex, y la bóveda ya se había vuelto a abrir. Ese es justo el resultado tardío que prohíbe la decisión (b).
  - Sin reapertura, la petición ya daba `false` (`dek == null` o el estado no es `Unlocked`).
  - Los llamadores que preparan algo antes de llamar a `modifyData` dentro de la misma función (`createFolder`, `rename`, …) leen la época al llamar, ya con la bóveda abierta.
  - La importación sigue con su propio `sinceLock` dentro del cambio.
  - Las dos suites completas siguen en verde sin tocar ningún otro test.
- **Tests (`AttachmentsTest`):**
  - `readingTheInputNeverHoldsTheVaultLock` (`:260`) cambia su final. Antes fijaba la regla vieja: el adjunto leído a través de un bloqueo se guardaba después de reabrir. Ahora da `false`, sin referencia ni archivo. Sigue comprobando que la lectura no bloquea guardar, bloquear ni desbloquear.
  - Nuevo, `anAttachmentReadWhileOnlyOtherSavesHappenIsSaved` (`:301`): si durante la lectura solo hay otros guardados, el adjunto se guarda.
  - **RED:** con el `VaultStore.kt` de antes (con `git stash`), 17 tests y 1 fallo por aserción, `readingTheInputNeverHoldsTheVaultLock`.
- **Comandos y salida:**
  - `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.*' --tests 'app.seguridad.AppContainerVaultsTest' --no-daemon --max-workers=1`: en verde.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: `BUILD SUCCESSFUL in 4m 30s`. **App: 2184 tests, 0 fallos.** Lint: 0 errores y 20 avisos (los mismos).
  - `./gradlew -p jvm-check test --no-daemon --max-workers=1`: `BUILD SUCCESSFUL in 1m 48s`. **jvm-check: 2147, 0 fallos.**
- **Textos:** sin textos nuevos. Un adjunto rechazado así dice «La bóveda se bloqueó: no se ha añadido.» (`VaultAttachments.kt`), que ya existía y dice la verdad.
- **Revisión experto-seguridad-cripto (ronda 3):** ningún guardado por `modifyData` escribe después de un bloqueo, aunque la bóveda se reabra mientras espera. La protección ya no depende de que el mutex atienda por orden ni de que se cancele la composición. El adjunto en claro (`plain`) se sigue borrando en `finally`.
- **Revisión Ingeniero Móvil Senior (ronda 3):** el valor por defecto se evalúa en la llamada (es un argumento por defecto de Kotlin), en el hilo de quien llama, sin suspensión de por medio. No hay que cambiar ningún llamador.
