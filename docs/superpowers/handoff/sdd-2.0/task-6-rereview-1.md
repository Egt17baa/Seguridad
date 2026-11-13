# Re-revisión de la tarea 6, ronda 1 (`0f5be65..0f6051d`)

Solo lectura; no se ha vuelto a ejecutar la suite.

### Finding Verdicts

- **I-1 — ADDRESSED.** `VaultScreen.kt` (diff, tras `val unlocked`): `LaunchedEffect(unlocked) { if (!unlocked) snackbar.currentSnackbarData?.dismiss() }`. `message` se lanza en `vaultScope = rememberCoroutineScope()` creado dentro del contenido de `VaultGate` (`VaultScreen.kt:~273-274`), así que el bloqueo cancela los `showSnackbar` en cola y el visible. Se cubren también los de la tarea 4, porque todos pasan por `onMessage = message`. `PasswordOfferOnReturn` está dentro de `VaultGate` y `copyOfferedPassword` usa `vaultScope`. Los demás `scope.launch { showSnackbar }` que quedan fuera (`VaultScreen.kt:218-223`, «Huella activada/desactivada») no llevan datos de la bóveda; el «Deshacer» ya se cancelaba con su clave `unlocked`. Test `PlatformSourcesTest` «the list's messages go at the lock» (efecto, `insideCall(VaultGate)`, ausencia de `scope.launch { snackbar.showSnackbar(text) }`).
- **⚠️ 1 — ADDRESSED.** `canMove = folders.isNotEmpty() || e.folderId != null` (`VaultList.kt:~481`) y en la barra de selección `canMove = folders.isNotEmpty()` (`:~619`), sin excluir «Códigos». Cubre «⋮», las acciones de TalkBack y la barra.
- **⚠️ 2 / m-1 — ADDRESSED.** `MainActivity.kt:115` llama a `twoStepCopy.markLeft()` en `onStop` (con las mismas condiciones que el bloqueo). `TwoStepCopy.takeReturnOffer()` da el id una sola vez y solo si hubo salida dentro de los 30 s. `PasswordOfferOnReturn` (`EntryRow.kt:~365`) ya no usa `ON_RESUME`: reacciona a `changes != atStart`, así que no ofrece al componerse ni al navegar dentro. Con «Al salir» no sale (aceptado). Tests `TwoStepCopyTest.offeredOnlyOnReturnFromOutside` y el pin de `PlatformSourcesTest`.
- **m-2 — ADDRESSED.** `TwoStepCopy.changes` sube al ofrecer, al salir y en `clear()` (solo si había oferta); el `LaunchedEffect(changes)` se reinicia y cancela el `showSnackbar` en curso. Test `changesTellTheScreen`.
- **m-3 — ADDRESSED.** `Components.kt:~144-151`: `pressSignal` antes de `.clip`, como `SectionCard`; selección con relleno `secondaryContainer` en lugar del borde del acento. `secondaryContainer` es una superficie de las que mide `surfacesOf` (`ThemeSurfaces.kt:57`), así que lo de «donde se mide el texto» es cierto. Pins en el test de las filas.
- **m-4 — ADDRESSED.** `copySensitiveFor` / `copyOrAskNoticeFor` / `askFor`: el código se calcula en `produce()` al escribir (al aceptar el aviso A14 o al instante). La época se comprueba antes de calcular (`ClipboardNotice.kt:~106`). `copyCode` (`VaultList.kt:~442`) relee la entrada en ese momento. Test `aCopyWorkedOutAtWriteTime`.
- **m-5 — ADDRESSED.** Pins de `selectAll(result.shown)` (con una sola llamada a `.selectAll(`) y de `retainVisible(r.shown)` tras `_result.value = r`, tolerando comentarios entre medias.
- **m-6 — ADDRESSED.** `ROW_MENU` quitado (`ListTexts.kt`).
- **m-7 — ADDRESSED.** `.maestro/README.md:136` dice «Crear la primera».

**Comprobaciones nombradas**

(a) `onStop` cuenta como «salir» salvo con `keepOpenOnce`. Revisados todos los usos: `keepOpenOnce` solo lo ponen los selectores y el escáner QR, con su callback que lo baja. El selector de compartir (`ACTION_SEND`, `Pickers.kt:151`, `ImageShare.kt`) no lo pone, y cuenta como salida: es una salida real a otra app, y coincide con lo que ya hace el bloqueo. No hay petición de permisos en tiempo de ejecución (`requestPermission` no aparece). El diálogo de A14, el de la huella (diálogo del sistema que no detiene la actividad) y los diálogos propios son internos y no disparan `onStop`. Apagar la pantalla sí cuenta, y es correcto. El único error posible es por exceso de supresión: copiar usuario, abrir un selector y volver en 30 s no ofrece la contraseña. Es inocuo y coherente con el bloqueo. Sin hallazgo.

(b) Los pins nuevos de `PlatformSourcesTest` sí habrían fallado sobre `0f5be65`: la ficha del I-1 exige `LaunchedEffect(unlocked)…dismiss()` y `vaultScope`, que no existían; el de m-1 exige `markLeft` y prohíbe `ON_RESUME`/`LifecycleEventEffect`, que estaban; el de m-3 exige `.pressSignal` antes de `.clip` (antes iba después) y prohíbe `border(2.dp, …primary`. Los de m-5 (`onSelectAll`, `retainVisible` tras `_result.value = r`) pinchan código que ya existía, así que pasarían también sobre la base: su función es fijar el cableado, no detectar una regresión de este arreglo, y es lo que pedía m-5. Por la regex de `closing`/`blank` los comentarios no estorban.

### New Issues in the Fix Diff

- **Minor — `ClipboardNotice.kt:~108`:** si `produce()` da `null` al aceptar el aviso (la entrada ya no existe), devuelve `NOTHING_WAITING` sin poner `acceptedNow`; quien llama puede no anotar la aceptación en Ajustes y el aviso volvería a salir en la siguiente copia. Es muy raro y no pierde datos. Se podría devolver un resultado propio que sí anote la aceptación.
- **Minor — `TwoStepCopy.markLeft()`:** `left` queda en `true` hasta una vuelta con pantalla suscrita; si una pantalla sin `PasswordOfferOnReturn` está arriba al volver (por ejemplo, una herramienta), la oferta no sale y la consume o caduca a los 30 s. Es coherente con «una sola vez por vuelta» y no hay nada que corregir; solo para anotar.

Sin hallazgos de severidad Critical o Important.

### Out-of-Scope Observations

- La fila seleccionada ahora es opaca también en «Cristal»; se ve distinta de las demás. Es decisión visual, a comprobar en el móvil (ya lo dice el implementador).
- «Al salir» nunca da la oferta: aceptado por el controlador.

### Verdict

**Approved.** Los 11 puntos están arreglados, y los dos menores nuevos no bloquean.

## Revisión experto-seguridad-cripto
- I-1 cerrado: ningún título, etiqueta ni carpeta queda en pantalla ni sale en cola tras el bloqueo (`VaultScreen.kt:~273`, efecto de `dismiss`).
- m-4: el texto 2FA se calcula al escribir, con la época comprobada antes (`ClipboardNotice.kt:~106`); `TwoStepCopy` sigue sin guardar secretos (solo un `Boolean` y un contador `Long`).
- Sin cambios de formato, criptografía, red, permisos ni componentes.

## Revisión Ingeniero Móvil Senior
- Ciclo de vida: `markLeft` en `onStop` con las mismas condiciones que el bloqueo (`MainActivity.kt:115`); la pantalla recoge `changes` con `collectAsStateWithLifecycle`, así que la marca hecha fuera llega al volver; `atStart` evita la oferta al componerse.
- El ámbito `vaultScope` vive con el contenido de `VaultGate`. `pressSignal` fuera del recorte como `SectionCard` (`Components.kt:~151`).

## Textos para el Abogado Digital (revisión final)
- Ningún texto nuevo en la ronda. Quitado «Más opciones» (`ROW_MENU`). Para la veracidad: la oferta de «Copiar contraseña» sale solo al volver desde fuera de la app y no con el bloqueo «Al salir».
