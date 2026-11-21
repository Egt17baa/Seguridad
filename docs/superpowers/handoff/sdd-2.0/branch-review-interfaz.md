# Revisión de la rama 2.0, parte C: interfaz

Base `a3ac942` (v1.4.3) → `dcf28dc`. Clon propio de solo lectura (`<scratchpad>/br-interfaz`, en `dcf28dc`); sin gradle. Las líneas citadas son de `dcf28dc`.

## Alcance

**Leídos enteros:** `ui/AppNavHost.kt`; `ui/screens/BigPasswordScreen.kt`, `WifiQrScreen.kt`, `TrashScreen.kt`, `EntryHistoryScreen.kt`, `EntryFields.kt`, `EntryRow.kt` (de :100 al final; lo anterior son importaciones y constantes), `VaultList.kt` (de :180 al final), `VaultEntryScreen.kt` (de :140 al final), `HealthScreen.kt` (de :70 al final), `VaultUpgrade.kt` (de :40), `MarkupText.kt`; `vault/LimitedMarkup.kt`, `FuzzySearch.kt`, `Templates.kt`; `tools/qr/WifiQr.kt`, `QrView.kt`, `QrHandoff.kt`, `QrPendingText.kt`; `tools/TwoStepCopy.kt`, `ToolCatalog.kt`; `health/Missions.kt`, `health/Health.kt` (:1-120); `settings/Guide.kt`, `settings/Opening.kt` (`openingItem`); `android/SecureWindow.kt`; los diffs de `MainActivity.kt`, `SeguridadApp.kt`, `android/Clipboard.kt`, `tools/ClipboardNotice.kt` y `ui/screens/ToolsScreen.kt`.

**Leídos en parte:** `VaultPickerScreen.kt` (:100-470), `VaultScreen.kt` (:136-330, :505-527), `VaultOrganize.kt` (cada diálogo, cada `Text` y cada `launch`), `QrInspectScreen.kt` (:118-300, :458-531), `SettingsScreen.kt` (:290-330 y los `rememberSaveable`), `AppearanceScreen.kt` (:130-150), `OnboardingScreen.kt` (:40-60), `ui/theme/Components.kt` (:230-300), `ui/theme/Glass.kt` (:60-110, :220-290), `vault/EntryForm.kt` (:405-640), `vault/EntryQuery.kt` (búsqueda), `vault/VaultStore.kt` (`lock`, `destroy`, `rename`, firmas con `sinceLock`), `AppContainer.kt` (:195-235, :513-575, :583-660, :810-850).

**No leídos enteros (ver «Lo que no pudiste comprobar»):** `SettingsScreen.kt` (571 líneas cambiadas), `AppearanceScreen.kt`, `Palettes.kt`, `Theme.kt`, `NewEntryChooser.kt`, `ContactsView.kt`, `Expiry.kt`, `Selection.kt`, `TagPalette.kt`, `FormHelpers.kt`, los `*Texts.kt` salvo los citados, y los tests.

## Issues

### Critical

Ninguno.

### Important

**I-1 (seguridad: dato cifrado a la vista tras un bloqueo). El diálogo «Cambiar nombre» de «Bóvedas» sigue abierto después de un bloqueo con el nombre de la bóveda en su campo.**
- `ui/screens/VaultPickerScreen.kt:169` (`var dialog by remember`), `:177` (`VaultIdleLock(active)`: la pantalla se bloquea por inactividad), `:316` (`NameDialog(VAULT_RENAME, openData?.name.orEmpty(), …)`), `:391-392` (`var name by remember { mutableStateOf(initial) }`). Ningún efecto cierra `dialog` cuando `activeState` deja de ser `Unlocked` (en el archivo no hay `LaunchedEffect` ni `DisposableEffect`).
- Qué pasa: con «Cambiar nombre» abierto, el bloqueo por inactividad o salir de la app (`MainActivity.onStop`, `lock(LEFT_APP)`) bloquea la bóveda, pero «Bóvedas» funciona también bloqueada (es la pantalla de antes de abrir), así que no se cierra; el diálogo conserva el nombre en claro. Al «Guardar», `VaultStore.rename` (`VaultStore.kt:1052-1055`, `modifyData`) devuelve `false` y `run` da `null`: no se guarda nada y no se dice nada.
- Por qué cruza tareas: la decisión del dueño (opción A, tarea 2) cifra el nombre dentro de la bóveda y deja «Bóveda N» antes de abrir (`VaultRegistry.kt:49-50`); la regla de la parte C (tareas 6 y 10) es que cada pantalla nueva borra lo suyo al bloquear. «Bóvedas» es la única pantalla nueva de la bóveda que no se cierra con el bloqueo, y su diálogo de la tarea 2 no lo tiene en cuenta. El diálogo «Borrar» no tiene el problema (su texto se recalcula con `openData` nulo y dice «Bóveda N», `:324-325`).
- Arreglo: una función pura `pickerDialogKept(dialog: PickerDialog, unlocked: Boolean): Boolean` (falso para `Rename` y `Delete` sin la bóveda abierta; `New` e `ImportNew` también falsos sin `canAdd`, porque `createVault` ya los rechaza con `VaultsRefused`, `AppContainer.kt:626`) y en la pantalla `LaunchedEffect(active, activeState is VaultState.Unlocked) { dialog?.let { if (!pickerDialogKept(it, activeState is VaultState.Unlocked)) dialog = null } }`.
- Test: `VaultPickerDialogsTest.renameAndDeleteCloseWhenLocked`: `assertFalse(pickerDialogKept(PickerDialog.Rename, unlocked = false))`, ídem `Delete`; `assertTrue(... Rename, unlocked = true)`; `DeleteEmpty` se mantiene sin abrir. Y un test de fuente que falla si `VaultPickerScreen.kt` no llama a `pickerDialogKept` dentro de un `LaunchedEffect` con `activeState`.

**I-2 (veracidad: un botón que no hace nada). «Ver en grande» aparece en las versiones del historial y no hace nada.**
- `ui/screens/EntryFields.kt:140` (`onBig: (String) -> Unit = {}`), `:142` (`big(f)` da una acción si `bigPasswordValue(entry, f.id) != null`, sin mirar si hay quien la reciba), `ui/screens/VaultEntryScreen.kt:168` (`onBig?.let { TextButton(onClick = it) { Text(BIG_OPEN) } }`), `ui/screens/EntryHistoryScreen.kt:160` (`EntryFieldsCard(e, onCopied = {})`, con el `onBig` por defecto).
- Qué pasa: en una versión de una tarjeta, una licencia, un pasaporte, un wifi o con un campo oculto propio, cada secreto lleva el botón «Ver en grande», que llama a `{}`. Aunque se conectara, `BigPasswordScreen` lee la entrada actual (`BigPasswordScreen.kt:111`), no la versión: enseñaría otro secreto.
- Por qué cruza tareas: la tarea 3 (historial) usa la tarjeta de campos de la tarea 5, y la tarea 6 le añadió «Ver en grande» con un valor por defecto vacío.
- Arreglo: `onBig: ((String) -> Unit)? = null` y `fun big(f) = onBig?.let { cb -> { cb(f.id) } }?.takeIf { bigPasswordValue(entry, f.id) != null }`. La ficha (`VaultEntryScreen.kt:298`) sigue pasando su `onBig`.
- Test: sacar la decisión a `internal fun bigActionFor(entry, fieldId, onBig): (() -> Unit)?` y `EntryFieldsTest.noBigButtonWithoutHandler`: con una entrada WIFI con contraseña, `bigActionFor(e, WIFI_PASSWORD_KEY, null) == null` y con un manejador no nulo; más un test de fuente de que `EntryHistoryScreen.kt` no pasa `onBig`.

**I-3 (veracidad, fila 26). La etiqueta «WPA/WPA2/WPA3» promete un QR que no sirve en una red solo WPA3.**
- `vault/Templates.kt:29` (`"WPA" -> "WPA/WPA2/WPA3"`), usada en la ficha (`EntryFields.kt:127`) y en el selector (`EntryFields.kt:275`); el QR escribe `T:WPA` (`tools/qr/WifiQr.kt:168`); el lector rechaza `T:WPA3` y `T:SAE` (`WifiQr.kt:119-125`, decisión del dueño 14-11).
- Ver la fila 26 abajo: se arregla.

### Minor

**m-1 (fila 3, hilo principal). «Salud» calcula `vaultHealth` en la composición, en el hilo principal, y otra vez fuera para las misiones.**
- `ui/screens/HealthScreen.kt:136` (`remember(entries, breached, day) { vaultHealth(...) }`, en la composición) y `:125-129` (las misiones, en `Dispatchers.Default`, con otro `vaultHealth`). Durante «Comprobar filtraciones», cada contraseña cambia `breached` (`:221`) y vuelve a calcular todo en el hilo principal: N pasos × `vaultHealth(N)`.
- Ya era así en la 1.4.3 (`HealthScreen.kt:99` en `a3ac942`); en la 2.0 pesa más (los wifis, `lastPasswordChange` leyendo el historial, el tope de 24 MiB) y lo repite la tarea 10. La lista de la bóveda sí lo hace fuera (`VaultList.kt:272`, `:285-288`).
- Arreglo: un solo `produceState` en `Dispatchers.Default`, con clave `(data, breached, day)`, que dé los `items` y, de ellos, las misiones (que ya no llevan `BREACHED`, `Missions.kt:54-56`: se filtran). Test de fuente: `vaultHealth(` en `HealthScreen.kt` solo dentro de `withContext(Dispatchers.Default)`. La medida en el móvil sigue en la fila 3.

**m-2 (texto de la guía). «Las copias con calendario solo se hacen al abrir Clavero.»**
- `settings/Guide.kt:42` y `:61`. El código también las hace al desbloquear (`AutoBackup.kt:457`, `runDue(now(), afterUnlock = true)`), solo de la bóveda activa y abierta (`AutoBackup.kt:533-535`, `AppContainer.kt:513-516`), y solo si hubo cambios (D-3; `BackupTargetTexts.kt:7` sí lo dice). No es falso en lo que promete (pone una condición, no una garantía), por eso no lo subo a Important; con varias bóvedas puede hacer creer que la bóveda B se copia al abrir Clavero con A. Propuesta para el Abogado: «Las copias con calendario se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, si hubo cambios.»

**m-3 (copiar en dos pasos). Las copias del historial no terminan la oferta de «Copiar contraseña».**
- `EntryHistoryScreen.kt:160` y los `Field(...)` de `VersionCard` (`onCopied` vacío); el diseño y `VaultEntryScreen.kt:222-227` dicen que «cualquier copia termina la oferta». Efecto pequeño: la oferta solo sale al volver de fuera sobre la lista o la ficha (`EntryRow.kt` `PasswordOfferOnReturn`), y siempre pregunta. Arreglo: pasar `twoStep` a `EntryHistoryScreen` y `onCopied = twoStep::clear`.

### Info

- **i-1:** `EntryRow.kt:282` pone el código 2FA en la descripción de TalkBack; es lo que se ve en la fila y la ventana está marcada como sensible para la accesibilidad desde Android 14 (`SecureWindow.kt:31-35`). Se acepta.
- **i-2:** `EntryFields.kt:176` y `VaultEntryScreen.kt:161` enmascaran con tantos puntos como caracteres (hasta 16): una nota o un secreto corto dejan ver su longitud. Es así desde antes de la 2.0 (no cruza tareas); el Inspector de QR ya usa 8 puntos fijos (`QrInspectScreen.kt:325-326`). Si se quiere igualar, una constante.

## Riesgos de la parte C, uno por uno

1. **Secretos en el estado.** `rememberSaveable` nuevos en el diff: `AppearanceScreen.kt:138-141` (un booleano, dos tonos `Int` y el nivel de cristal), `OnboardingScreen.kt:47` (el número de página), `SettingsScreen.kt:301` (`changing`: el id del destino o la URI de la carpeta, que ya está en claro en los ajustes). Los demás de `app/src/main` no cambian en el diff (`ContactAddScreen`, `IdentityScreen`, `PkActionsScreen`, `LicensesScreen`, `MoreInfo`, `VaultAttachments`, `ClipboardHistoryNotice`, `AppNavHost.kt:184`, `:212`). Argumentos de navegación nuevos (`AppNavHost.kt:361-391`): `id` de la entrada (codificado, LIBRE-1) y `field` (el id del campo, no su valor: `BigPasswordScreen.kt:95-103`). Ninguno lleva un secreto, un dato de una entrada ni una imagen. La contraseña en grande, el QR del wifi, el historial, el formulario y la papelera leen de la bóveda abierta o de un ViewModel (`BigPasswordScreen.kt:109-116`, `WifiQrScreen.kt:105-184`, `VaultEntryScreen.kt:388-396`). Sin hallazgos.
2. **Bloqueo.** Cada pantalla nueva de la bóveda se va al bloquear: papelera (`TrashScreen` :5-9 del composable), historial (`EntryHistoryScreen` al principio), grande (`BigPasswordScreen.kt:129`), QR del wifi (`WifiQrScreen.kt:124-127`, `:197-200`), ficha y editor (`VaultEntryScreen.kt:209`, `:589`, `EntryForm.kt:623-625`), lista (`VaultList.kt:259`, `reset`). «Deshacer», los avisos con nombres y la oferta de dos pasos: `VaultScreen.kt:190`, `:193-196`, `:285-288` (ámbito que muere con `VaultGate`), `AppContainer.kt:827-831`. Las misiones viven en la rama abierta de «Salud» (`HealthScreen.kt:124-129`). `sinceLock`: el guardado del editor (`VaultEntryScreen.kt:431-434`); las demás acciones llaman al almacén en el mismo toque y `modifyData` lee la época al pedirlo (`VaultStore.kt:819-832`). Cambio de bóveda: `key(vault)` y `VaultScopes.drop` (`AppNavHost.kt:139-170`, `:198-200`); el aviso A14 y la oferta usan `AppContainer.lockEpoch`, que sube con cada cambio (`AppContainer.kt:531`, `:552-560`). **Excepción: I-1.**
3. **Ventanas.** Todo `AlertDialog` y `DropdownMenu` de `ui/` pasa por los envoltorios de `ui/theme/Components.kt:241-298`, que llaman a `FilterObscuredTouches` (`SecureWindow.kt:42-53`) en la ventana propia; ningún archivo importa los de Material directamente, y no hay `Popup`, `Dialog` sueltos ni hojas inferiores. El difuminado de «Cristal» solo vuelve a dibujar el fondo de la app (`Glass.kt:276-285`, `drawAppBackground`), nunca el contenido de debajo. Sin hallazgos.
4. **Mostrar sin suplantar.** `plainReportText` en cada sitio nuevo con título, usuario, URL, carpeta, etiqueta, vista, nombre de bóveda (`displayName`, `VaultRegistry.kt:49-50`), nombre de campo propio (`EntryFields.kt:159`, `:305`), versión del historial, misión (`Missions.kt:62`), SSID (`WifiQrScreen.kt:209`) y oferta (`EntryRow.kt` `copyPasswordOffer(plainReportText(...))`); en el Inspector, `visibleText` (`QrView.kt:123`, `:147`), como pide el Ruling de T9. Sin hallazgos.
5. **Notas con formato.** `LimitedMarkup.kt:45-63`, `:86-155`: sin recursión, lineal, topes de 64 KiB y 10 000 líneas; `MarkupText.kt` solo estilos, sin anotaciones ni enlaces. `FuzzySearch`: consulta a 200 puntos de código (`EntryQuery.kt:55-58`), palabras de más de 64 solo exactas, distancia con corte; corre en `Dispatchers.Default` (`VaultList.kt:272`). Sin hallazgos.
6. **Navegación.** `backOnce` en cada destino nuevo (`AppNavHost.kt:361-496`). El giro conserva desde ViewModels (los de la entrada de navegación, dentro de `VaultScopes`). La lista guarda búsqueda, filtro con carpeta y vistas, y posición en `EntryListViewModel` (`VaultList.kt:209-300`). `openingItem` y lo que se pinta: cada `if (showing == …)` de `AppNavHost.kt:288-325` pinta exactamente lo que miró `openingItem` (`Opening.kt:60-75`). Sin hallazgos.
7. **Portapapeles.** Toda copia nueva pasa por `copySensitive` o `copySensitiveFor` (`Field`, `VaultEntryScreen.kt:165`; fila, `VaultList.kt:420-445`; oferta, `VaultScreen.kt:507-513`); fuera de `android/Clipboard.kt` no hay `setPrimaryClip` nuevo, y los `SelectionContainer` son de herramientas que no cambian. Ver m-3.
8. **QR.** Todo texto que empieza como `WIFI:` se oculta y se borra al salir y al bloquear, también mal formado o de más de 4 096 (`QrView.kt:155-161`, `QrInspectScreen.kt:151-157`, `QrHandoff.appLeft`, `QrPendingText`); `toString` sin la contraseña (`WifiQr.kt:36-38`, `:62-72`); descripción de la imagen fija (`WifiQrScreen.kt:223`). TOTP: el editor sigue leyendo por `readOtpAuth` (CRY-B02, `VaultEntryScreen.kt:413-423`); las plantillas no tienen 2FA propio en el editor. Ver fila 26.
9. **Textos.** Sin nombres de otras apps nuevos en el diff (búsqueda en `app/src/main`); las palabras prohibidas las cubre `SourceTextsTest`. «Desde Android 12 … difuminan el fondo de la app» es verdad (`Glass.kt:105`, `:276`). Ver m-2 e I-2, I-3.
10. **Hilo principal.** Lista y búsqueda fuera (`VaultList.kt:272`); misiones fuera (`HealthScreen.kt:125-129`); «Salud»: m-1.
11. **Accesibilidad.** Ninguna descripción lleva un secreto que no esté ya a la vista (i-1); las acciones de la fila están también como acciones de TalkBack (`EntryRow.kt:182-186`). Lo que solo se ve en el móvil ya está en las filas 11, 12, 13, 28, 29, 34 y 44.

## Filas de `pendientes.md`

| Fila | Veredicto | Motivo y evidencia |
|---|---|---|
| 26 | **Se arregla** | `Templates.kt:29` dice «WPA/WPA2/WPA3», pero el QR escribe `T:WPA` (`WifiQr.kt:168`) y Clavero mismo no acepta `T:WPA3`/`T:SAE` (`WifiQr.kt:119-125`): en una red solo WPA3 el código no sirve. Texto veraz propuesto: la etiqueta «WPA/WPA2» y, bajo el selector, en `MoreInfo` o en una línea pequeña, «Si tu red es solo WPA3, su QR no sirve para conectarse.» (la redacción final, al Abogado). Cambia `TemplatesTest.kt:92`. Lo guardado (`WPA`) no cambia: ningún formato se toca. |
| 32 | **Se acepta** | Las misiones aplazadas van por `(id de bóveda, lockEpoch de su almacén)` (`Missions.kt:87-103`, `HealthScreen.kt:131-132`, `:149-150`). Un id no se repite: 16 bytes aleatorios (`VaultRegistry.kt:102`); el de `principal` es fijo, pero borrarlo y crearlo de nuevo con «He olvidado la contraseña» pasa por `destroy()`, que bloquea y sube la época (`VaultStore.kt:1613-1615`, `:747-748`). Todo vive en memoria. Usar `AppContainer.lockEpoch` (`AppContainer.kt:531`) es un cambio de una línea si se quiere robustez extra, no un defecto. |
| 33 | **Se acepta** | Las misiones se recalculan con cada cambio de `data` (`HealthScreen.kt:125`), y la bóveda se bloquea tras 15 min sin tocar como mucho (`Settings.kt:17-22`, `VaultIdleLock` en `HealthScreen.kt:81`, `touchesVault` en `:138`); el siguiente desbloqueo recalcula. Que «Salud» siga abierta más de un día con uso continuo y sin ningún cambio de datos es un caso que no merece código. Si se hace m-1, la clave `day` entra gratis. |

## Pruebas que pide el revisor

1. `VaultPickerDialogsTest.renameAndDeleteCloseWhenLocked` (I-1), con la función pura propuesta, y el test de fuente de su llamada.
2. `EntryFieldsTest.noBigButtonWithoutHandler` (I-2), con `bigActionFor`, y el test de fuente sobre `EntryHistoryScreen.kt`.
3. `TemplatesTest.wifiLabelSaysNoWpa3` (I-3/fila 26): `assertFalse("WPA3" in wifiSecurityLabel("WPA"))`, y que `WIFI_SECURITY_VALUES` sigue siendo `["WPA","WEP","nopass"]`.
4. Ya existentes, para correr como regresión de esta parte: `WifiQrTest`, `WifiQrReferenceTest`, `WifiQrInspectTest`, `WifiQrFormTest`, `TemplatesTest`, `SourceTextsTest`, `ImportTextsTest`, `MissionsTest`, `SimpleModeTest`, el test exhaustivo de `openingItem`, los de `LimitedMarkup` y `FuzzySearch`.
5. (m-1, si se hace) test de fuente: en `HealthScreen.kt`, cada `vaultHealth(` está dentro de un `withContext(Dispatchers.Default)`.

## Dudas

- I-1: ¿cerrar también «Bóveda nueva» e «Importar como bóveda nueva» al bloquear? Las maestras las escribió el usuario (no son datos de la bóveda) y `newVault` ya rechaza sin bóveda abierta (`AppContainer.kt:626`); propongo cerrarlas por coherencia con «PIN y códigos», pero es elección del controlador.
- Fila 26: si el Abogado prefiere no nombrar WPA3 en absoluto, basta «WPA/WPA2»; la línea sobre WPA3 es para no dejar al usuario de una red solo WPA3 sin saber por qué falla.

## Lo que no pudiste comprobar

- Sin gradle: ningún test ejecutado.
- No leí enteros `SettingsScreen.kt`, `AppearanceScreen.kt`, `Palettes.kt`, `Theme.kt`, `NewEntryChooser.kt`, `Expiry.kt`, `Selection.kt`, `TagPalette.kt`, `FormHelpers.kt`, `ContactsView.kt` ni los tests; de ellos solo comprobé el estado guardado, los diálogos y los textos citados. La lógica de Ajustes es de la parte B.
- En el móvil: que el QR con `T:WPA` conecte en modo de transición WPA2/WPA3 y falle en solo WPA3 (fila 23, tarea 11); el coste real de m-1 (fila 3).

## Revisión experto-seguridad-cripto

- `ui/screens/VaultPickerScreen.kt:169`, `:177`, `:316`, `:391-392`: I-1, el nombre cifrado de la bóveda queda a la vista tras un bloqueo. Important.
- `tools/qr/WifiQr.kt:36-38`, `:62-72`, `:91-99`, `:109-139`: el texto de wifi es secreto en todos los caminos y nunca entra en `toString`; lectura acotada a 4 096.
- `tools/qr/QrView.kt:155-161`, `ui/screens/QrInspectScreen.kt:151-157`: oculto y borrado al salir y al bloquear, también mal formado.
- `tools/ClipboardNotice.kt` (`copyOrAskNoticeFor`, `accept`): el código 2FA se calcula al escribirse, y la época de toda la app (`AppContainer.kt:531`, `:552-560`) impide aceptar en otra bóveda.
- `android/SecureWindow.kt:42-53`, `ui/theme/Components.kt:241-298`: cada diálogo y menú filtra toques, excluye el autorrelleno y se marca sensible.
- `vault/LimitedMarkup.kt:45-155`, `ui/screens/MarkupText.kt`: nada se convierte en enlace ni `Intent` (decisión (c)); análisis acotado (decisión (a)).
- `ui/screens/BigPasswordScreen.kt:109-116`: el secreto se lee de la bóveda, nunca de los argumentos.
- Solo `javax.crypto` y Bouncy Castle: nada de la parte C añade criptografía (el QR usa ZXing, ya en el APK). Ningún log con datos en `ui`, `tools`, `health`, `vault` (búsqueda de `Log.`, `println`, `printStackTrace`).

## Revisión Ingeniero Móvil Senior

- `ui/AppNavHost.kt:139-170`, `:198-200`: `VaultScopes` por bóveda y `key(vault)`; los ViewModels de una bóveda no sobreviven al cambio y sí al giro.
- `ui/screens/VaultList.kt:209-300`: búsqueda y filtros en `Dispatchers.Default` con `collectLatest`; posición como dos enteros, nunca el `LazyListState`.
- `ui/screens/HealthScreen.kt:125-136`: m-1, cálculo en la composición.
- `ui/screens/EntryFields.kt:140-142`, `ui/screens/EntryHistoryScreen.kt:160`: I-2, valor por defecto que pinta un botón muerto.
- `ui/screens/WifiQrScreen.kt:137-160`: imagen del QR fuera del hilo principal, con turno para descartar resultados tardíos y `OutOfMemoryError` como texto.
- `ui/screens/EntryRow.kt:182-186`, `:281-282`: acciones de TalkBack equivalentes al deslizar y al arrastrar.
- `settings/Opening.kt:60-75` frente a `AppNavHost.kt:288-325`: un solo elemento de arranque a la vez, el que se pinta.

## Textos para el Abogado Digital (revisión final)

- Fila 26: `Templates.kt:29` «WPA/WPA2/WPA3» → propuesta «WPA/WPA2» y la línea «Si tu red es solo WPA3, su QR no sirve para conectarse.»
- m-2: `settings/Guide.kt:42`, `:61` «Las copias con calendario solo se hacen al abrir Clavero.» → propuesta «Las copias con calendario se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, si hubo cambios.»
- I-2: si se arregla, desaparece «Ver en grande» en el historial; ningún texto nuevo.
- I-1: si el cierre del diálogo quiere un aviso, no hace falta texto: el diálogo simplemente se cierra con el bloqueo.
