# Tarea 5 bis, repaso de la ronda 2 (fix 43aa2c4..ff8580e)

### Finding Verdicts
- **N-1: ADDRESSED.** `Glass.kt:188-192` (`animationsOnSetting`) guarda el ajuste como estado y lo relee en `ON_RESUME`; `Theme.kt:66` lo da con `LocalAnimationsOn`; `Components.kt:83` lo lee del local y ya no llama a `ValueAnimator`. `PlatformSourcesTest` fija las tres piezas y que `Components.kt` no contiene `areAnimatorsEnabled`. La dependencia `lifecycle-runtime-compose` ya está en el catálogo.
- **N-2: ADDRESSED.** `Glass.kt:203-215` (`staticPressMark`, `pressSignal`): borde de 2 dp en el acento mientras está pulsada y sin animaciones, en Cristal y en Sólido, con el `interactionSource` de la propia `Card` (`Components.kt:84-93`). No hay movimiento. Con animaciones puestas queda la onda.
- **N-3: ADDRESSED.** `HealthScreen.kt` (resumen y `HealthRow`) pasan a `SectionCard`; ya no hay `Card(...clickable)`. Grep propio: no queda otra `Card/Surface` pulsable fuera de `Components.kt`, y el test con regex lo fija en todo `app/src/main/java`. Límite: la regex solo ve `Card(onClick` o `Card(...clickable` en la misma línea; no vería un `.clickable` en otra línea (menor, sin caso actual).

Comprobación nombrada (borde 2 dp, R-C5/R-C7): se dibuja con `drawWithContent` después del contenido, solo pulsado; el relleno de la tarjeta, el texto y su contraste no cambian, y el contenido tiene 16 dp de margen, así que no tapa texto. Es un elemento que no es texto y el acento llega a 3:1 en todas las superficies (test nuevo, 6 paletas más Personalizado, claro/oscuro, Cristal/Sólido). No oculta el foco: solo dibuja al pulsar, no toca ninguna señal de foco, y el borde de Alto contraste sigue debajo.

### New Issues in the Fix Diff
Ninguno que bloquee. Observación menor: con Alto contraste el borde de la tarjeta ya es de `borderWidth`; el trazo de 2 dp en el acento se distingue por color, no por grosor, y cumple 3:1 sobre la superficie, pero no contra ese borde (aceptable: es un estado transitorio).

### Out-of-Scope Observations
- Las filas pulsables de la lista (tarea 6) son `Row` con `clickable` y conservan la onda de Material; con animaciones quitadas no tienen marca estática. Revisarlo en la tarea 6.

### Verdict
APPROVED. N-1, N-2 y N-3 resueltos. No he vuelto a ejecutar la suite; me fío del informe (69 tests focalizados, 2121 app, 2090 jvm-check en verde).

### Revisión experto-seguridad-cripto
Sin impacto criptográfico ni de datos. `ValueAnimator.areAnimatorsEnabled()` solo lee un ajuste del sistema, sin red ni almacenamiento (`Glass.kt:189-190`). Sin nuevos permisos, componentes ni logs.

### Revisión Ingeniero Móvil Senior
Lectura del ajuste fuera de la recomposición y atada al ciclo de vida (`Glass.kt:188-192`), con un solo `staticCompositionLocalOf` (cambia raramente). `MutableInteractionSource` recordado en la tarjeta (`Components.kt:84`). Estado de pulsación recogido solo cuando no hay animaciones, sin coste extra en el camino normal. Accesibilidad: señal de pulsación no basada en movimiento, con 3:1.

### Textos para el Abogado Digital (revisión final)
Ninguno nuevo.
