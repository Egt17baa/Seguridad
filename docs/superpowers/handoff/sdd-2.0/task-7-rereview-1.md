# Re-revisión de la tarea 7 de la 2.0, ronda 1

Base de los arreglos `7dde42e`, cabeza `e7cd9ae`. Solo lectura, sin subagentes y sin repetir la suite. Diff: `review-7dde42e..e7cd9ae.diff`. D-1 y D-2 aceptados; no se reabren.

### Finding Verdicts

- **I-1 (pestañas con cambios): ADDRESSED.**
  - `ui/AppNavHost.kt:276`: cada pestaña pasa por `leaveGuard.leave { nav.navigate(...) }`.
  - `vault/EntryForm.kt:610-626`: `LeaveGuard` solo lleva `hasChanges: () -> Boolean` y `ask(go)`. Nada escrito cruza a la navegación. Sin editor registrado o sin cambios, `leave` va directo.
  - `EntryForm.kt:488-499` (`askToLeave`): abre el mismo diálogo y guarda el destino en `pendingLeave`, que es una llamada y no lleva texto.
  - «Descartar» (`:511-521`): `clear()` y después `go()`, así que nada de lo escrito sobrevive. «Seguir editando» (`:~503`) olvida el destino.
  - «Guardar» (`VaultEntryScreen.kt:552`, `saveFromDialog`): si faltan datos, el diálogo se queda con el error. Si pasa, el destino se ejecuta al acabar el guardado (`:429`). Si falla, el destino se descarta y se queda el formulario con su error (`:443`).
  - Un bloqueo lo borra todo sin diálogo: `clear()` pone `pendingLeave = null` (`EntryForm.kt:~573`), y `askingToLeave` también.
  - Tests nuevos: `aTabWithChangesAsksAndDiscardGoesWithNothingTyped`, `keepEditingStaysAndForgetsTheTab`, `saveFromATabsDialogGoesOnceSaved`, `aLockWhileAskingForATabGoesNowhere`, `theGuardPassesOnlyAQuestionAndACall`, más la prueba de fuente de `FormHelpersTest`.
- **m-1 (error viejo en el diálogo): ADDRESSED.** `EntryForm.kt:492` pone `error = null` al abrir el diálogo. Test `theDialogOpensWithoutAnOldError`.
- **m-2 («Descartar» durante un guardado): ADDRESSED.**
  - «Descartar» se desactiva con `saving` (`VaultEntryScreen.kt:805`) y `discard()` no hace nada (`EntryForm.kt:512`).
  - «Atrás» con un guardado en marcha no pregunta (`:490`) y la pantalla sale sola al terminar.
  - Ver N-1 sobre lo que pasa con la pestaña.
- **m-3 (botones con letra al 200 %): ADDRESSED.** `VaultEntryScreen.kt:802-807` pone los tres botones en un `Column(Alignment.End)`, uno por línea. Pendiente de confirmar en el móvil (tarea 11), como dice el informe.
- **m-4 (doble estimación): ADDRESSED.** `EntryFields.kt:243`, `remember(value) { strengthOf(value) }`, y el texto sale de `strengthLine(st)` (`FormHelpers.kt`). `strengthText` se mantiene para el test.

### Comprobación nombrada (m-2: un guardado que termina después de salir)

- **Otra bóveda o tras el bloqueo: no hay escritura tardía nueva.**
  - Tocar una pestaña con un guardado en marcha hace `go()` al instante (`EntryForm.kt:490`). El `DisposableEffect` llama a `left(false)` y `clear()`, que pone `loaded=false`, `saving=false` y `pendingLeave=null`.
  - La corrutina del guardado sigue en el ViewModel. `upsert` pasa por `modifyData` (`VaultStore.kt:759-793`), que bajo el mutex exige `vault`, `dek` y estado `Unlocked`, y confirma con la época `since` en `commit`. Tras un bloqueo da `false` y no escribe nada.
  - El diff no añade ninguna escritura nueva: lo único que se añade es qué se hace con el resultado.
- **Resultado y error en sitio equivocado:**
  - Éxito: `takeLeave()` da `null` (se borró con `clear()`) y `finish()` pone `finished=true` en una sesión ya dejada. No muestra nada en ningún sitio. Al restaurar la pestaña de la bóveda, el editor ve `finished` y vuelve atrás: es lo que corresponde, porque la entrada ya está guardada.
  - La vuelta tardía del `go()` guardado no puede dispararse dos veces: `takeLeave` lo toma una sola vez y `clear()` lo borra.
  - Fallo: ver N-1.

### New Issues in the Fix Diff

- **N-1 (Menor). Un guardado que falla después de tocar una pestaña pierde la entrada sin avisar.**
  - Dónde: `EntryForm.kt:490` (`saving.value -> go?.invoke()`) con `VaultEntryScreen.kt:432` y `:440` (`if (form.loaded)`).
  - Qué pasa: con un guardado en marcha, la pestaña se abre al instante y `clear()` deja `loaded=false`. Si `upsert` falla (límite de tamaño, `VaultLimitExceeded`, error de escritura, la entrada ya en la papelera), el error no se escribe en ningún sitio. El usuario ya está en otra pestaña, lo escrito se borró y la entrada no se guardó, sin mensaje.
  - Alcance: ya pasaba antes de esta ronda. Lo que cambia es que el diseño de m-2 lo deja así a propósito. No es una escritura en otra bóveda ni una fuga: es pérdida silenciosa de datos del usuario, justo lo que I-1 quiere evitar.
  - Corrección mínima: con `saving`, `askToLeave` guarda `pendingLeave = go` en lugar de ejecutarlo. Así la pestaña se abre cuando el guardado termina bien (`:429`, ya implementado) y, si falla, `finally` descarta el destino y se queda el formulario con su error (`:443`). Test: con `saving = true`, `askToLeave(go)` no ejecuta `go`, y `takeLeave()` lo devuelve una vez.
  - Aceptable como alternativa: dejarlo escrito como decisión del controlador, porque la ventana es de milisegundos y el fallo del guardado es raro.

### Out-of-Scope Observations

- `upsert` no lleva una época del que lo llama: `modifyData` toma `since = epoch` al entrar al mutex (`VaultStore.kt:768`). Un guardado encolado detrás de una derivación larga que desbloquea otra bóveda podría escribir en ella. No lo introduce esta ronda ni la tarea 7. Lo anoto para el foco 2 de T2, sin confianza alta: no he comprobado si `lock` espera ese mutex.
- Con un fallo síncrono de `save` desde el diálogo de una pestaña, el diálogo se queda con el error y el destino ya se descartó (`:443`). Un «Descartar» posterior vuelve a la bóveda y no a la pestaña. Marginal.

### Verdict

**APROBADA CON UN MENOR NUEVO (N-1).** I-1 y m-1 a m-4 resueltos. N-1 se arregla con un cambio de una línea (guardar el destino en lugar de ejecutarlo mientras `saving`) y un test, o se deja como decisión escrita. No hay Critical ni Important.

## Revisión experto-seguridad-cripto

- Hacia la navegación solo cruzan un Boolean y una llamada (`EntryForm.kt:610-626`). `pendingLeave` no lleva texto y se borra en `clear()`, que cubre bloqueo, salida y `onCleared`. Un toque tardío tras el bloqueo no navega.
- «Descartar» borra lo escrito antes de navegar (`EntryForm.kt:517-519`).
- Un guardado tardío no escribe tras el bloqueo (`VaultStore.kt:767-770`); lo anterior sobre otra bóveda es una observación, no un hallazgo de esta ronda.
- N-1 es pérdida de datos del usuario (disponibilidad), no un fallo explotable.
- Sin registros, `Bundle`, `rememberSaveable` ni textos nuevos en el diff.

## Revisión Ingeniero Móvil Senior

- `LocalLeaveGuard` es un `staticCompositionLocalOf` con un `LeaveGuard` en `remember` (`AppNavHost.kt`). El registro está en un `DisposableEffect(form, leaveGuard)` (`VaultEntryScreen.kt:590`) y el que deja el registro solo se quita a sí mismo (`EntryForm.kt:617`).
- La rotación descarta el destino viejo (`left(true)` llama a `dropLeave`), así que no queda un `NavController` viejo en el ViewModel.
- El orden «guardar, `finish()`, `go()`» es seguro: el editor ya no está arriba, así que `backOnce` no vuelve a quitar nada.
- N-1: pendiente la corrección. Prueba en el móvil: tocar una pestaña durante un guardado, y los tres botones con la letra al 200 %.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo en esta ronda. El diálogo de las pestañas reutiliza «¿Guardar los cambios?», «Guardar», «Descartar» y «Seguir editando».
