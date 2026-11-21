# Tarea 10, fase B: ronda 2 (n-1)

## Estado

**Hecha.** Commit `7a2420d`, «2.0, tarea 10 (fase B): ronda 2 (n-1)», sobre `cbd1cb0`. Sin push.

No cambia el comportamiento: solo se tocan comentarios, KDoc y un test.

## Qué se hizo

- **`settings/Opening.kt`:** el KDoc de `guideRequestKept` quedaba entre el KDoc de `openingItem` y la función. Ahora cada KDoc va encima de su función.
- **`ui/AppNavHost.kt`:** el comentario de antes de `val showing = openingItem(…)` ya no dice que el paso a v4 lo pinta `VaultGate`. Dice que la elección y su línea las pinta también `AppNavHost`, más abajo, y que mientras esperan no sale nada más.
- **`OpeningTest.theScreenDrawsWhatItPicks`:**
  - ya incluye los dos diálogos del paso a v4: recorre todos los `OpeningItem` menos `NOTICE`;
  - su comentario ya no dice que el paso a v4 es de `VaultGate`.

  Lo mantengo junto al test de m-7 (`eachDialogIsDrawnOnceBehindItsCondition`). El de m-7 ya cubre por completo los ocho diálogos: una sola llamada, detrás de su condición. Este comprueba además dos cosas que el de m-7 no mira: que la navegación usa `openingItem(` y que «Antes de empezar» va antes.

## Tests

- jvm-check, `app.seguridad.settings.OpeningTest`: 8 tests, 0 fallos. Es la clase del test de m-7 y de `theScreenDrawsWhatItPicks`.
- `:app:compileDebugKotlin`: sin errores.
- La suite entera la corre el controlador en un clon limpio.

## Revisión experto-seguridad-cripto

No cambia nada de seguridad: no se toca código que se ejecute, ni formatos, ni red.

## Revisión Ingeniero Móvil Senior

Los KDoc vuelven a estar encima de su función, así que el IDE y Dokka los asocian bien (`Opening.kt`). Los comentarios dicen lo que hace el código desde la ronda 1 (I-1).

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo.

## Dudas

Ninguna.
