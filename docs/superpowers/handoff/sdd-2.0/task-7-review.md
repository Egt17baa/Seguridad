# Revisión de la tarea 7 de la 2.0: el formulario

Revisor de tarea (cumplimiento y calidad). Base `1651eec`, cabeza `7dde42e` (commits `162f395`, `1bb7faf`, `141cfa2`, `7dde42e`; `42c7706` es del controlador y no se revisa). Diff: `review-1651eec..7dde42e.diff`. Checkout de solo lectura. No se ha vuelto a pasar la suite. Lentes: `experto-seguridad-cripto` (SKILL.md entero; `references/auditoria.md` como lista), `ingeniero-movil-senior:desarrollo-movil-senior` y `ingeniero-movil-senior:auditoria-seguridad-movil`.

## Spec Compliance

**Veredicto: cumple, con dos desviaciones de letra que piden una decisión del controlador (no son fallos).**

| Requisito (brief y diseño «Formulario» y «El «+»») | Estado | Evidencia |
|---|---|---|
| `titleFromUrl`: host de `parseLink`, sin `www.`, primera letra en mayúscula, `null` sin host, sin red, solo rellena un título vacío | Cumple | `vault/FormHelpers.kt:39-51`; `EntryForm.kt:423` (`followUrl`: solo con el título vacío o el que puso él); tests `titleFromHost`, `titleNullWithoutHost`, `titleIgnoresUserinfo`, `theUrlFillsAnEmptyTitleOnly` |
| `titleIgnoresUserinfo` | Cumple | `FormHelpers.kt:47-49` (host de `parseLink`); sin esquema, «usuario:clave@host» no da título (`BARE_DOMAIN`, `:30`) |
| `PastedKind { OTPAUTH, SEED }` y `detectPasted` | Cumple, con firma ampliada (`SeedLists?`) | `FormHelpers.kt:59`, `:99-113`; solo `Usable` (CRY-B02), 12/15/18/21/24 palabras de una sola lista con suma válida, entropía borrada (`:109`) |
| «Solo se llama desde la acción de pegar del campo; nunca lee el portapapeles» | Cumple en el fondo; desviación de letra (Decisión D-1) | Un solo llamador, `EntryForm.kt:411-421` vía `pastedBlock` (`FormHelpers.kt:121`); test de fuente `aPasteIsOnlyLookedAtInTheFieldsOwnChange` (un solo `readClipboardText(` en el editor, el de «Pegar enlace») |
| `hasUnsavedChanges` y «¿Guardar los cambios?» (Guardar, Descartar, Seguir editando) al volver | Cumple (en `EntryEditSession`, que es el estado del ViewModel) | `EntryForm.kt:392`, `:474`; `VaultEntryScreen.kt:610` (`BackHandler` solo con cambios), `BackScaffold(..., form::back)`, diálogo `:763-785` |
| El «+» abre `NewEntryChooser` con los nueve tipos en orden | Cumple | `FormHelpers.kt:138-150`, `ui/screens/NewEntryChooser.kt:31-53`, `VaultScreen.kt` (`newEntry`, `choosing`); «Crear la primera» igual (`VaultList.kt`, `EmptyVaultActions`) |
| Generador junto a cada campo de contraseña y «a los campos secretos de texto» | Cumple el diseño; desviación de la letra del brief (Decisión D-2) | `EntryFields.kt:240` (`PasswordWithTools`), contraseña del acceso, passphrase, contraseña del wifi y campos ocultos propios; no en `Templates.kt:45` (número de tarjeta), `:61` (clave de licencia), `:68` (número de pasaporte) |
| Fuerza bajo el campo mientras se escribe, con la estimación de «Salud» | Cumple | `FormHelpers.kt:161-167` (`estimateBits`), `EntryFields.kt:242-249`, en palabras y el color solo acompaña (R-1) |
| Tests nombrados en el paso 1 | Cumplen: todos existen | `FormHelpersTest.kt:36-178`, `EntryEditSessionTest` (`unsavedChangesDetected`, `noChangesNoDialog`) |
| Restricciones globales: sin dependencias, permisos, componentes exportados, hosts ni registros; secretos fuera de `rememberSaveable`/`Bundle` | Cumple | `git diff --stat`: ni manifiesto, ni Gradle, ni `res/`; `grep` de las líneas añadidas de `app/src` por `Log.`, `println`, `printStackTrace`, `rememberSaveable`, `Bundle`, `SavedStateHandle`, `showSnackbar` y `navigate(`: **0 coincidencias** |

### Decisiones para el controlador (desviaciones de letra, recomiendo ratificarlas)

- **D-1, qué es «la acción de pegar del campo» (riesgo 1).** Lo que se detecta es un bloque de ≥ 10 caracteres que entra de una vez en el valor del campo (`FormHelpers.kt:121-130`). Respuesta a cada subpregunta:
  - **El generador:** no dispara nunca. Escribe en la celda por `onGenerated` y no pasa por `typed` (`VaultEntryScreen.kt`, `onGenerated = { loginPassword = it }`; plantilla y campos propios usan `set`, que no llama a `typed`). Lo fija el test de fuente `aPasteIsOnlyLookedAtInTheFieldsOwnChange`.
  - **El autocompletado, el dictado y un bloque del IME** (un deslizamiento o una sugerencia larga) **sí pueden dispararlo**. Una palabra de ≥ 10 letras no es nunca un `otpauth://` ni una frase de 12 palabras con suma válida. Que el dictado o el autocompletado metan una frase BIP-39 válida o un enlace `otpauth://` legible es muy raro, y en ese caso la pregunta tiene sentido. Y, sobre todo, **el resultado es solo una pregunta**: nada cambia sin «Sí» (`EntryForm.kt:446`), y el diálogo nunca enseña lo pegado (`VaultEntryScreen.kt:748-760`).
  - **¿Una frase escrita o autocompletada en un campo secreto sale a la vista o se mueve sin que el usuario lo pida?** No se mueve sin «Sí». Con «Sí», una frase que estaba en un campo enmascarado (Contraseña) pasa al campo «Palabras», que **no** se enmascara (`VaultEntryScreen.kt:629-639`, como en toda entrada de tipo frase desde la 1.x). Lo considero **por diseño**: el usuario ha elegido «guardarla como frase semilla», la pantalla tiene `FLAG_SECURE` y el campo «Palabras» enseña las palabras igual en cualquier frase. Queda como informativo y no se cuenta como hallazgo; el texto «Se quita del campo donde lo has pegado.» podría decir además que se ve en «Palabras», a criterio del Abogado.
  - **¿Hay un enganche más limpio?** No, de forma estructural. Lo que se pega desde el portapapeles del teclado llega al campo como un `commitText` normal del IME, igual que cualquier texto escrito. Interceptar solo el «Pegar» del menú de selección (la barra de texto) dejaría fuera ese camino, que es el más usado, y no hay forma de distinguirlos sin leer el portapapeles, que es justo lo que el brief prohíbe. No afirmo nada sobre las API concretas de Compose de la BOM `2025.08.00` porque no las he comprobado en los artefactos.
  - **Recomendación:** ratificar la interpretación como regla («la acción de pegar del campo» = un bloque que entra de una vez en el valor del campo; nunca se lee el portapapeles; nunca hace nada sin preguntar).
- **D-2, el generador (riesgo 4).** El brief dice «y a los campos secretos de texto»; el diseño, solo «junto a cada campo de contraseña». No hay generador junto a `Templates.kt:45` (número de tarjeta), `:61` (clave de licencia) ni `:68` (número de pasaporte), ni junto al CVV y el PIN (`:48-49`, numéricos y vacíos por R-3). El motivo es bueno: esos valores los da otro, y generarlos no sirve y confunde. Está en un solo predicado probado (`FormHelpers.kt:174`, `onlyPasswordsTheUserChoosesGetTheGenerator`). Sí lo tienen todos los campos ocultos propios. **Ratificar.**
- **Las demás «Dudas» del implementador (riesgo 4):**
  - `detectPasted(text, SeedLists?)`: correcto. Las listas se leen fuera del hilo principal (`MasterPolicy.kt`, `WordLists.seedLists`), y con `null` solo se reconocen los enlaces.
  - `hasUnsavedChanges` en `EntryEditSession`: correcto. Es el estado del ViewModel (`vm.form`) y así se prueba en la JVM.
  - Etiquetas del selector («Tarjeta bancaria», «Red wifi», «Código 2FA»): correcto según R-4 y coherente con la fila de tipos.
  - «Otro» abre un «Acceso» vacío (`FormHelpers.kt:147`): coincide con el diseño, «acceso vacío con campos libres».
  - «Códigos» abre un 2FA directamente: razonable.
  - Nada que cambiar.

## Strengths

- `PasteOffer` y `FormSnapshot` tienen un `toString` sin valores (`EntryForm.kt:121-144`), viven solo en la sesión del ViewModel y se borran en `clear()` (`EntryForm.kt:496` y siguientes, con `pasteOffer`, `askingToLeave`, `autoTitle` y `baseline`). Una respuesta tardía tras el bloqueo no hace nada (`acceptPaste`, `if (!loaded) return null`), con su test `aLockWhileAskingDropsThePasteAndALateAnswerDoesNothing`.
- `hasUnsavedChanges` compara con una instantánea tomada al final de `load` (`EntryForm.kt:239-243`) y no con la entrada de la bóveda. Así, lo que la carga ordena (fechas DD/MM/AAAA, ids repetidos, la caducidad de una copia, los valores por defecto del wifi) nunca cuenta como cambio. `noChangesNoDialog` lo prueba con siete cargas y con el giro (riesgo 3, sin falsos positivos al abrir).
- `BackHandler(enabled = form.hasUnsavedChanges)` solo con cambios (`VaultEntryScreen.kt:610`): sin cambios, el gesto predictivo y la limpieza de `left()` funcionan como siempre.
- `trySave` (`VaultEntryScreen.kt:479`) es el código del botón movido tal cual: un solo camino de guardado para el botón y el diálogo.
- Un 2FA aceptado pasa por `applyOtpAuth`, el mismo camino que el escaneo (CRY-B02). Se ofrece solo `Usable`, y un `otpauth-migration://` no se ofrece.
- Hay topes antes de leer (decisión (a)): 4096 caracteres para el título (`FormHelpers.kt:41`), 1000 para la frase (`:104`), 10 000 para la fuerza (`:163`). Las expresiones regulares no son ambiguas (`BARE_DOMAIN`, `:30`).
- Las listas en español se leen en NFC y sin acentos, y la lista se rechaza si no son 2048 palabras distintas (`FormHelpers.kt:77-86`). Las frases de los tests se construyen con `Bip39.phraseOf` sobre entropía inventada.
- El selector se cierra al bloquear y no vuelve solo al desbloquear (`VaultScreen.kt`, `LaunchedEffect(unlocked) { if (!unlocked) choosing = false }`).

## Issues

### Critical
Ninguno.

### Important

**I-1. Cambiar de pestaña con cambios sin guardar los pierde sin preguntar (riesgo 2).**
- **Dónde:**
  - `ui/AppNavHost.kt:266-280`: la `NavigationBar` envuelve todo el `NavHost`, también `vault/edit`, y cada pestaña navega con `popUpTo(start) { saveState = true }`.
  - `ui/screens/VaultEntryScreen.kt:563-565`: `DisposableEffect` → `form.left(changingConfigurations = false)` → `clear()` (`EntryForm.kt:543-545`).
  - El propio comentario de `VaultEntryScreen.kt:570`: «Back after leaving (another tab): from the vault again, never from what was typed».
- **Qué pasa:** con el formulario cambiado, tocar otra pestaña (por ejemplo «Códigos», para leer el código 2FA mientras se crea el acceso) quita la pantalla y borra todo lo escrito. Al volver, el formulario se carga otra vez de la bóveda. El diálogo nuevo solo protege «Atrás».
- **Impacto:** se pierden datos que el usuario no puede recuperar. Ejemplo concreto: crear un acceso, generar una contraseña nueva, ponerla en la web y pasar a «Códigos» para el 2FA de esa misma web. La contraseña ya cambiada en la web se pierde y la cuenta queda inaccesible. Además, el diálogo crea la expectativa de que la app avisa antes de perder cambios, y aquí no lo hace. La letra del diseño («al volver con cambios») se cumple, así que el veredicto de cumplimiento sigue siendo «cumple». El comportamiento ya existía antes de esta tarea, pero el riesgo de perder datos es concreto y está en el formulario que esta tarea rehace.
- **Corrección (con límites):**
  - Hacia `AppNavHost` solo pasa un Boolean o una llamada, nunca texto. Por ejemplo, un «guardián de salida» en `AppContainer` o en un `CompositionLocal` que el editor registra con `DisposableEffect`: `hasChanges: () -> Boolean` más `ask(onLeave: () -> Unit)`.
  - El `onClick` de la pestaña lo consulta. Si hay cambios, abre el mismo diálogo (`form.back()` con la navegación pendiente): «Guardar» guarda y después navega, «Descartar» navega y «Seguir editando» se queda.
  - Nada de lo escrito sobrevive a salir, y un bloqueo sigue borrándolo todo, también la navegación pendiente (`clear()`).
  - Tests: uno de la sesión (`askingToLeave` con un destino pendiente se borra al bloquear; «Descartar» ejecuta el destino) y uno de fuente (el `onClick` de las pestañas consulta el guardián).
  - Si el controlador prefiere no ampliar el alcance, la alternativa es una decisión escrita del dueño que acepte la pérdida al cambiar de pestaña. No lo recomiendo.

### Minor (se arreglan todos en esta tarea)

**m-1. El diálogo «¿Guardar los cambios?» enseña un error viejo nada más abrirse.** `VaultEntryScreen.kt:771`, `text = error?.let { … }`, lee `form.error`, que nada borra al escribir (`EntryForm.kt:411`, `typed`, no lo toca). Por ejemplo, se pulsa «Guardar» sin nombre («Ponle un nombre»), se escribe el nombre y se pulsa «Atrás»: el diálogo sale ya con «Ponle un nombre» en rojo. Pasa lo mismo con «Sin permiso de cámara…» o un error del QR. **Corrección:** `back()` borra `error` al abrir el diálogo (`if (hasUnsavedChanges) { error.value = null; askingToLeave.value = true }`), o el diálogo enseña solo el error de su propio «Guardar» (una celda `leaveError`). Test en `unsavedChangesDetected`: con un error previo, `back()` lo deja en `null`.

**m-2. «Descartar» está activo mientras un guardado está en marcha, y la entrada se guarda igual.** `VaultEntryScreen.kt:780`: el «Guardar» del diálogo se desactiva con `saving`, pero «Descartar» no. Si se pulsa «Guardar» abajo y enseguida «Atrás», el diálogo se abre (`hasUnsavedChanges` sigue a `true` hasta `finish`). Entonces «Descartar» llama a `finish()` mientras `upsert` termina y guarda la entrada: el botón dice lo contrario de lo que pasa. **Corrección:** `back()` no hace nada mientras `saving.value` (la pantalla se va sola al terminar), o `enabled = !saving` también en «Descartar». Test de la sesión: con `saving = true`, `back()` no abre el diálogo.

**m-3. Los botones del diálogo no se reparten en líneas con la letra al 200 % (R-1).** `VaultEntryScreen.kt:778-783`: «Descartar» y «Seguir editando» van en un `Row` dentro de `dismissButton`, que no puede partirse; el `FlowRow` de Material solo reparte `dismissButton` y `confirmButton` como dos bloques. Con la letra grande, los textos se parten letra a letra o se cortan. En la app, los grupos de botones usan `FlowRow` (`VaultList.kt:599`, `ImportScreen.kt:708`, `VaultEntryScreen.kt:152`). **Corrección:** `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp))` en lugar de `Row`, o los tres botones en un `Column(horizontalAlignment = Alignment.End)` en `confirmButton`. Añadirlo a la prueba en el móvil de la tarea 11 (letra al 200 %).

**m-4. `estimateBits` corre dos veces por campo de contraseña en cada recomposición.** `EntryFields.kt:242-243`: `strengthText(value)` y `strengthOf(value)` llaman cada una a `estimateBits` (NFC y dos pasadas, hasta 10 000 caracteres). `PasswordWithTools` no se salta en la recomposición porque sus lambdas (`set`) se crean de nuevo cada vez, así que cada tecla en cualquier campo lo repite por cada campo oculto (hasta 30). **Corrección:** una sola estimación por valor, `val s = remember(value) { strengthOf(value) }`, y el texto desde `s` (por ejemplo, `strengthText` que reciba el `Strength`, o una función que devuelva el par). Test: `strengthWhileTyping` sigue igual.

## Assessment

- **Cumplimiento del brief y del diseño:** cumple. D-1 (la detección del pegado) y D-2 (el generador) son desviaciones de letra bien razonadas, para que el controlador las ratifique.
- **Calidad:** el trabajo es bueno, pero hay que arreglar cosas antes de dar la tarea por cerrada: **I-1** (o una decisión escrita del dueño) y los cuatro menores, m-1 a m-4.
- **Riesgos con nombre:**
  - (1) D-1: solo una pregunta, sin portapapeles ni texto a la vista.
  - (2) I-1.
  - (3) Sin falsos positivos al abrir. Los falsos positivos que quedan son inocuos (un cambio de mayúsculas en la frase, o espacios en un valor de plantilla, también hacen preguntar). No hay falsos negativos que pierdan datos: el tipo se excluye solo en una entrada nueva, que no pierde nada, y una existente no puede cambiar de tipo, porque los chips solo se muestran con `existing == null`, `VaultEntryScreen.kt:615`.
  - (4) Ratificar D-2 y las demás dudas.
  - (5) La frase pegada no va a registros, snackbar, argumentos de navegación ni estado guardado, y se borra al bloquear (grep vacío; `clear()`; test).
- **Recuento:** Critical 0 · Important 1 · Minor 4.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** quien tiene el móvil desbloqueado, otra app (portapapeles, superposiciones), una copia o un volcado de estado, y lo que se pega (dato hostil).
- **Portapapeles (MASVS-PLATFORM / STORAGE):** ninguna lectura nueva. Lo pegado se ve en el valor del campo (`FormHelpers.kt:121`, `EntryForm.kt:411-421`). La única lectura del editor sigue siendo «Pegar enlace» (test `aPasteIsOnlyLookedAtInTheFieldsOwnChange`). Correcto.
- **Secretos en memoria:**
  - `PasteOffer.text/before/after` (`EntryForm.kt:121`) y `FormSnapshot` (`:126`) solo viven en la sesión y se borran en `clear()` (`:496` y siguientes) al bloquear (`followVault`), al salir sin giro (`left`) y en `onCleared`.
  - Sus `toString` no enseñan valores.
  - En las líneas añadidas no hay `rememberSaveable`, `Bundle`, `SavedStateHandle`, `showSnackbar`, `navigate(` ni registros (grep del diff: 0).
  - La entropía de la suma BIP-39 se borra (`FormHelpers.kt:109`). Las `String` de Kotlin no se pueden borrar: es un límite conocido de todo el formulario.
- **Entradas hostiles / decisión (a):**
  - Topes antes de leer: 4096 caracteres en `titleFromUrl` (`FormHelpers.kt:41`), 1000 para la frase (`:104`), `OTPAUTH_MAX_CHARS` dentro de `readOtpAuth` y 10 000 para la fuerza (`:163`).
  - `pastedBlock` es lineal.
  - `BARE_DOMAIN` (`:30`) no es ambigua: cada repetición empieza por un `.` literal, así que no hay ReDoS.
- **CRY-B02:** se ofrece solo `OtpAuthRead.Usable` (`FormHelpers.kt:102`), y el «Sí» pasa por `applyOtpAuth` (`VaultEntryScreen.kt:471`), el mismo camino que el escaneo.
- **URL y suplantación:**
  - El host se lee con `parseLink` como un navegador (`\` cierra la autoridad), y `usuario:clave@` nunca llega al título (`FormHelpers.kt:47-49`, `titleIgnoresUserinfo`).
  - El título sale en ASCII (`xn--…`), así que un nombre en otra escritura no se ve como otro parecido (test `titleFromHost`).
  - Solo `http` y `https`, y sin red.
- **Consentimiento:** el tipo solo cambia con «Sí» (`EntryForm.kt:446`). El diálogo nunca enseña lo pegado (`VaultEntryScreen.kt:748-760`) y lleva `FilterObscuredTouches`.
- **Informativo (no se cuenta):** con «Sí», una frase pegada en un campo enmascarado pasa a «Palabras», que se ve en claro (`VaultEntryScreen.kt:629-639`). Es por diseño (D-1): se aceptó guardarla como frase, hay `FLAG_SECURE` y el campo se ve así en toda frase.
- **Primitivas:** nada nuevo; solo el SHA-256 de la suma BIP-39 que ya había (`Bip39.entropyOf`).
- **Hallazgos de seguridad:** ninguno confirmado. I-1 es una pérdida de datos del usuario (integridad y disponibilidad de lo que escribe), no algo que pueda explotar un atacante.

## Revisión Ingeniero Móvil Senior

- **Estado y ciclo de vida:**
  - Todo vive en `EntryEditSession`/`EntryEditViewModel`, sobrevive al giro (`configChanges` de `MainActivity`) y se borra al salir y al bloquear.
  - `hasUnsavedChanges` lee celdas de Compose (`baseline` es una celda), así que `BackHandler(enabled = …)` (`VaultEntryScreen.kt:610`) se recalcula solo. `loaded` no es observable, pero cambia junto con `baseline`, que sí lo es.
  - `choosing` (`VaultScreen.kt`) en `remember` es correcto: no es secreto.
- **Navegación:**
  - `BackHandler` se compone después del `OnBackPressedCallback` del `NavHost`, así que tiene prioridad solo con cambios; la flecha usa `form.back()`.
  - **I-1:** las pestañas (`AppNavHost.kt:266-280`) se saltan ese guardián.
  - «Descartar» llama a `finish()` (`EntryForm.kt:527-530`), que borra el formulario y vuelve con `backOnce`.
- **Concurrencia:**
  - `WordLists.seedLists()` va bajo el mutex y en `Dispatchers.IO` (`MasterPolicy.kt`), y `seedLists` es `@Volatile` (`EntryForm.kt`).
  - Un pegado antes de que carguen las listas solo reconoce enlaces: aceptable.
  - **m-2:** el diálogo y un guardado en marcha.
- **Rendimiento:** `snapshot()` corre en cada recomposición, en tiempo lineal y barato. Hay que corregir **m-4** (la estimación doble por campo y por recomposición).
- **Accesibilidad (R-1):**
  - La fuerza va en palabras, con el color como apoyo (`EntryFields.kt:242-249`).
  - Los iconos del selector y del generador son decoración (`DecoratedLabel`, `NewEntryChooser.kt:42`, `EntryFields.kt:250`).
  - El selector se desplaza con la letra grande (`NewEntryChooser.kt:37`).
  - **m-3:** los tres botones del diálogo con la letra al 200 %.
- **Código:**
  - `trySave` (`VaultEntryScreen.kt:479`) quita la lógica del `onClick`: mejor separación.
  - `EmptyVaultActions` simplificado (`VaultList.kt`).
  - El flujo de Maestro `p05` se cambió sin ejecutar (no hay emulador): va a la prueba de la tarea 11.

## Textos para el Abogado Digital (revisión final)

Todos nuevos, en `vault/FormHelpers.kt:179-209` salvo donde se dice:
- «¿Qué quieres guardar?» (título del selector del «+»).
- «Otro» y, debajo, «Un acceso vacío con los campos que quieras».
- Las demás opciones del selector son las etiquetas de los tipos que ya existían: «Acceso», «Código 2FA», «Nota», «Frase semilla», «Tarjeta bancaria», «Red wifi», «Licencia», «Pasaporte». Y «Cancelar».
- «Fuerza estimada: » seguido de «Débil», «Aceptable», «Fuerte» o «Muy fuerte». Dice «estimada»: no afirma que una contraseña sea segura.
- «Generar una al azar» (ahora también junto a la contraseña del acceso, la passphrase y los campos ocultos).
- «Parece un código 2FA. ¿Guardarlo como 2FA?» (lo que se detecta es un enlace `otpauth://`).
- «Parece una frase semilla. ¿Guardarla como frase semilla?»
- «Se quita del campo donde lo has pegado.» Para el Abogado: con «Sí», la frase pasa al campo «Palabras», que se ve en claro mientras se edita (D-1). Valorar si la línea debe decirlo.
- «Sí» y «No, dejarlo así».
- «¿Guardar los cambios?», con «Guardar», «Descartar» y «Seguir editando».
- Ninguno nombra otra app.
- Si se arregla I-1 con un diálogo al cambiar de pestaña, sus textos (si no son los mismos) se añaden aquí.
