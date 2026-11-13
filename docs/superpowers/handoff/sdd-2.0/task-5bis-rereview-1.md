# Tarea 5 bis, repaso de la ronda 1 (`e53c4a5..43aa2c4`)

Solo se ha mirado la lista de hallazgos y el diff del arreglo. No se ha vuelto a pasar la suite: el informe de la ronda 1 da las órdenes y la salida (`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug ...` BUILD SUCCESSFUL, 2120 tests sin fallos; `-p jvm-check test` BUILD SUCCESSFUL, 2090 tests; antes, 132 tests focalizados), con los tests que cubren cada punto.

### Finding Verdicts

- **M-1: ADDRESSED.** `Palettes.kt` (`colorsFor` -> `builtFor`) cae a «Noche» si `!Built.met`, para todas las paletas, según la decisión del controlador (sin margen extra; el objetivo sigue en 4,5:1). `opacity()` pone `met = false` si llega a 100 sin cumplir, con el brillo incluido (`if (hundredths >= 100 && !ok(1f)) met = false`). `internal fun paletteMeets` expone el dato. Tests: `PalettesTest.wallpaperColoursFromAnyColourRead` (13 colores extremos por pares, 400 parejas al azar con semilla fija fuera de la rejilla, vía `wallpaperSeeds` y `colorsFor`, en claro y oscuro y con y sin alto contraste, y comprueba además saturación 0 de un gris y el tono 240 del azul) y `everyPaletteMeetsItsTargetsItself` (las con nombre, incluida «Noche», las 432 y todo el barrido).
- **M-2: ADDRESSED.** `Glass.kt` (brillo): `0.5f to Color.White.copy(alpha = 0f)`; test de fuente `PlatformSourcesTest` («the highlight of a panel fades white»).
- **M-3: ADDRESSED.** `AppearanceScreen.kt` usa `SnackbarHostState` y `SnackbarHost` (vía el nuevo parámetro `snackbarHost` de `BackScaffold`, `Components.kt`), con la constante única `SETTING_SAVE_FAILED` (`SettingsScreen.kt`, usada en las 4 llamadas de Ajustes y en «Aspecto»). `change(onSaved, f)` solo llama a `editing = false` si `store.update` termina bien; si falla, el borrador sigue a la vista. No hay test de la pantalla (es Compose; queda para el móvil, como anota el informe).
- **M-4: ADDRESSED.** `AppearanceScreen.kt`: `Section(TRANSPARENCY_AND_CONTRAST)` = «Transparencias y contraste».
- **M-5: ADDRESSED.** `SettingsStore.sane()` pasa `CUSTOM` sin tonos a `NIGHT` (al leer y al guardar); `shownPalette(...)` (`Palettes.kt`) trata `WALLPAPER` en Android 8 a 11 y `CUSTOM` sin tonos como «Noche»; la pantalla (`customOpen`, `shown`) y `appearanceSummary` lo usan. Tests: `SettingsTest.aCustomWithoutTonesIsNight`, el reforzado `aStrangeAppearanceKeepsTheOtherSettings` y `PalettesTest.theShownPaletteIsTheDrawnOne`.
- **M-6: ADDRESSED.** `TagPaletteThemeTest.everySurfaceIsInsideTheTagBound`: las 6 paletas, `ALL_CUSTOM` y todo `WALLPAPER_SWEEP`, claro y oscuro, con y sin alto contraste, superficies opacas y translúcidas.
- **M-7: ADDRESSED.** `TagPaletteTest.contrastFormula`: `#767676` 4,54 (llega), `#777777` 4,48 (no llega), `#595959` 7,00, `#949494` 3,03, y los primarios puros, con tolerancia 0,01.
- **M-8: ADDRESSED.** `Glass.kt`: `panelRipple(animationsOn)` devuelve `null` con las animaciones quitadas; `Components.kt` (`SectionCard` con `onClick`) lo aplica con `LocalRippleConfiguration provides panelRipple(ValueAnimator.areAnimatorsEnabled())`. Tests: `GlassContrastTest.pressGlowOnlyWithAnimations` (null con `false`, no nulo con `true`) y `PlatformSourcesTest` («the press glow of a panel follows Android's animations setting», que ata la tarjeta al ajuste del sistema).

### New Issues in the Fix Diff

- **Critical:** ninguno.
- **Important:** ninguno.
- **Minor:**
  1. `Components.kt` (`SectionCard`): `ValueAnimator.areAnimatorsEnabled()` se lee en cada recomposición, sin estado observable; si el usuario cambia la escala de animación con la pantalla abierta, la tarjeta no lo nota hasta recomponer. Es inocuo (se corrige solo al recomponer).
  2. `Components.kt` (`SectionCard`): también en «Sólido», con las animaciones quitadas, una tarjeta pulsable ya no tiene ninguna señal de pulsación (antes tenía la onda por defecto). Coherente con la orden de M-8, pero conviene que el móvil lo confirme (el informe ya lo apunta para la prueba).
  3. M-8 solo cubre `SectionCard`; otras superficies pulsables (por ejemplo, `Card` de `HealthScreen`) no pasan por `panelRipple`. Ya anotado como «para la tarea 6».

### Out-of-Scope Observations

- `HealthScreen.kt:103` y `:195` siguen usando `Card` de Material (ya apuntado por la revisión original).
- Lo que depende del dibujo real (que no haya onda con «Quitar animaciones», el *snackbar* de «Aspecto», Android 8 a 11) va a la prueba en el móvil, como dice el informe.

### Verdict: All findings addressed, no new Critical/Important breakage

## Revisión experto-seguridad-cripto

- El arreglo no toca secretos ni criptografía: solo aspecto y ajustes. `SettingsStore.kt` (`sane`) sigue normalizando una entrada que puede venir manipulada (`settings.json`): ahora un `CUSTOM` sin tonos no queda en un estado incoherente.
- `Palettes.kt` (`colorsFor`/`builtFor`): la vuelta a «Noche» refuerza el «nunca» del contraste, también ante colores del sistema imprevistos; sin lanzar excepciones.
- La constante única del error de guardado no revela nada.

## Revisión Ingeniero Móvil Senior

- `Components.kt` (`SectionCard`) y `Glass.kt` (`panelRipple`): respeta la preferencia «Quitar animaciones» (`ValueAnimator.areAnimatorsEnabled`, API 26+), con `@OptIn` correcto. Ver menores 1 y 2.
- `AppearanceScreen.kt` (`change(onSaved, f)`): el cierre del editor depende del resultado; el *snackbar* usa el `SnackbarHostState` recordado. `BackScaffold` mantiene compatibilidad con la lambda final.
- `Glass.kt` (brillo): parada en blanco con alfa 0, que es lo que mide `ThemeSurfaces.kt`.
- Cobertura: los tests nuevos miden lo que se dibuja (`failures`, `paletteMeets`) y no solo el corrector.

## Textos para el Abogado Digital (revisión final)

- Nuevo: título de sección «Transparencias y contraste».
- Cambiado: el error de guardado de «Aspecto» pasa a «No se pudo guardar el ajuste» (sin punto, en un *snackbar*), igual que en Ajustes. Desaparece «No se pudo guardar el ajuste.».
- Sin más textos nuevos.
