# Clavero 2.1.1: registro

Rama `ccr-0aa27f95-u038cd`, desde `main` en `a29d88c` (etiqueta `v2.1.0`). Diseño `docs/superpowers/specs/2026-11-24-clavero-2.1.1-barra-design.md`. La decisión de hacerla y la aprobación del diseño, en `sdd-2.1/progress.md` (24-11-2026).

- Abogado, principio (24-11-2026): `abogado-2.1.1-diseno.md`, «se puede programar con cambios». O-1 a O-7 y R-1 a R-6 aceptados, sin objeción técnica, y apuntados en el diseño («Dictamen del Abogado al principio»).
- Abogado decision (O-1): O-A2 de la 2.1 se sustituye solo para la barra de abajo, con las condiciones de O-2; lo demás de O-A2 sigue.
- Ruling (R-6): si Android pide quitar animaciones, la cápsula no hace ninguna transición, tampoco la pastilla.
- Plan (24-11-2026): `docs/superpowers/plans/2026-11-24-clavero-2.1.1-barra.md`, 4 tareas. Ruling del controlador: el contenido pasa bajo la cápsula solo en las cinco pestañas (`UNDER_BAR_ROUTES`); las demás pantallas terminan encima, como en la 2.1 (objetivo 3, O-4 y O-5 sin riesgo en formularios e imágenes).
- Task 1 BLOCKED by measurement: over the O-5 list the capsule needs 0.77-0.89 (> 2.1 bar); over the background alone ~0.45-0.55. Owner decision (24-11-2026): option B («B»): the content never passes under the capsule; the capsule floats over the app background, more translucent. Design: «Corrección (dueño, 24-11-2026): opción B».
- Ruling: pill = `primary` with `onPrimary` (secondaryContainer reaches only 1.2-1.4:1 against the capsule).
- Tarea 1 (opacidad de la cápsula): hecha (`806d4cc`), revisada (APPROVED) y su menor m-1 arreglado (`834e15b`, los valores fijados por paleta; 2 688 tests, 0 fallos). Cristal 0,55, Vidrio 0,45, Transparente 0,55-0,59; sin difuminado, 0,90-0,94; con Alto contraste, Reducir transparencias o Degradado, 1.
- Tarea 2 (la cápsula, `ui/theme/FloatingBar.kt`): hecha (`537871c`), revisada (APPROVED) y sus menores m-1 a m-3 arreglados (`682a208`; 2 699 tests, 0 fallos). Rulings del controlador: D-1 (tests de funciones puras y de código fuente; sin ui-test ni Robolectric, dependencias congeladas; lo que solo se ve en pantalla va a las casillas del README), D-6 (un único `pointerInput` en `ui/theme`, `swallowTaps`), D-2 (64 dp; 80 dp con letra doble, fijo durante el uso), D-3 (pastilla 44×32 dp detrás del icono), D-4 (`CapsuleScope`), D-5 (reparto del ancho; «Herramientas» cabe a 360 dp).
- Pendiente para la tarea 3 (m-4 de la revisión de la 2): la cápsula como hermana encima del contenido (no en un hueco del `Scaffold`), para que `capsuleSpace()` y sus márgenes coincidan; un arrastre que empiece en ella no llega a un antepasado; iconos con `contentDescription = null`.
- Pendiente para la tarea 4: m-2 de la 1 (los textos no dicen que la cápsula sea «siempre» más translúcida: en «Personalizado» y con el fondo de pantalla solo se garantiza ≤ barra); m-5 de la 2 (casilla del README: «Herramientas» entero a 360 dp con la letra del fabricante, solo ~4,6 dp de sobra; ningún texto dice que la pastilla «se funde»); m-6 de la 2 y R-1 (el asunto de `537871c` dice «medidas de la guía pública»; sin `--amend`, las notas y el commit de la tarea 4 dicen que las medidas son propias, con la guía pública como orientación, consultada el 24-11-2026; su página no se pudo leer).
- Tarea 3 (la cápsula en `AppNavHost`): en curso desde `682a208` (24-11-2026). Si la sesión se corta: mirar `git status`; si hay cambios sin commit en `AppNavHost.kt`, `PlatformSourcesTest.kt`, `FormHelpersTest.kt` y `BottomBarSpaceTest.kt`, son de la tarea 3 sin terminar: revisarlos contra el plan, pasar la compilación completa y hacer su commit, o volver a lanzar la tarea 3 desde `682a208`. Después: revisión de la tarea 3, tarea 4, revisión de la rama con el director, `code-review`, `security-review`, auditoría y Abogado final.

## Tarea 3 (24-11-2026)

- Hecha en `1b02615`: «Sólido» conserva el `Scaffold` con la barra de la 2.1; en los otros cuatro estilos la cápsula va encima, hermana del `NavHost` en un `Box`, y el `NavHost` toma abajo exactamente `capsuleSpace()` (`navHostPadding`). Las dos barras usan la misma navegación (`goToTab`). Compilación completa: 2 703 tests y `jvm-check` 2 584, 0 fallos.
- Revisión de la tarea: APROBADA, sin Critical ni Important. Menores: m-1 (un bucle del test que no podía fallar), m-2 (un test de fuente que fije las medidas reales de la cápsula a `capsuleSpace()`) y m-3 (el informe decía que no había `imePadding` dentro del `NavHost`; los hay y restan `capsuleSpace()` bien). Se arreglan en un commit propio.
- Para la tarea 4 (de m-3): casilla en las pruebas en el móvil: en «Cristal», editar una entrada con el teclado abierto; el último campo y el botón quedan a la vista.

## Tarea 4 (24-11-2026)

- Hecha en `75230eb` (`STYLE_MORE`, `versionName` 2.1.1, la ficha, el README con «Pruebas de la 2.1.1» y las notas). La línea publicada se ajustó a lo que es cierto: «desde Android 12» y, con «Personalizado» y la paleta «Colores del fondo de pantalla», «al menos tanto como antes». Las medidas se dicen propias, con la guía pública como orientación (24-11-2026), lo que corrige el asunto de `537871c`.
- Revisión: CON CAMBIOS, solo documentos. I-1: en «Transparente», a través de la cápsula el fondo de pantalla casi no se ve; la R-5 del Abogado no se puede cumplir tal como está escrita con la opción B y va a su revisión final. M-1 a M-4, arreglados en `f1cb20a` (2 707 tests, `jvm-check` 2 585, 0 fallos).

## Tarea 5: las filtradas de «Salud» se quedan hasta bloquear (dueño, 24-11-2026)

- Pedida por el dueño: «Hasta bloquear» y «En la 2.1.1». Diseño y plan en `44c311b`. Abogado al principio: «se puede programar, con cambios» (`abogado-2.1.1-filtradas.md`, `f34cd70`), con O-1 a O-3 y R-1 a R-3 aplicados.
- Hecha en `7e86e18`: `health/LeakResults.kt` en `AppContainer`. Guarda, por acceso, las apariciones y la marca del último cambio de contraseña. Nunca la contraseña ni su hash, y nada en disco. Se olvida con cada bloqueo y al cambiar de bóveda. La comprobación sigue si se sale de «Salud» y se corta al bloquear.
- Revisión: sin Critical ni Important. Menores M-1 (descartar el resultado si la bóveda se bloquea justo al terminar), M-2 («Quedan N sin cambiar.») y M-3 (la frase de «¿Filtrada?» en la política), arreglados en `748613a` (2 724 tests, `jvm-check` 2 602, 0 fallos).
- Siguiente: la revisión de toda la rama con el director, `code-review`, `security-review`, la auditoría y el Abogado final.

## Revisión de la rama y `code-review` (24 y 25-11-2026)

- Revisión de la rama del director (`3629fce`, `branch-review.md`): BR-1 (Important: «Importar copia» no olvidaba los resultados de «Salud») a BR-7, arreglados en `4dd05c6`; su repaso (`12c7a3a`) dio BR-8 y BR-9, arreglados en `34711e9` (2 734 tests, `jvm-check` 2 611, 0 fallos).
- `code-review`: dos menores. El 2 (documentos atrasados), en `14682f0`. El 1 (una contraseña cambiada después, aunque sea la misma filtrada, quita «Filtrada» hasta comprobar otra vez), solo con un texto cierto: `LEAK_RECHECK_NEEDED`, sin guardar la contraseña ni su hash, en `272c349` (2 734 tests, `jvm-check` 2 611, 0 fallos). Ese commit también quita del parte el nombre de un modelo en el pie de los commits (`PublishedFilesNameNoModelTest`).
- Siguiente: `security-review` contra `a29d88c`, la auditoría completa y el Abogado final.

## `security-review` y auditoría (25-11-2026)

- `security-review` (`da4edfd`, `security-review.md`): ninguna vulnerabilidad con confianza ≥ 0,8.
- Auditoría completa (`4bc006d`, `final-audit.md`): «se puede publicar». F-1 a F-3 Info, sin cambios de código; R-211-1 a R-211-3.

## Aprobación del director de la auditoría (26-11-2026)

- Sesión nueva: sigue en la rama `ccr-0aa27f95-u038cd` (el dueño lo eligió; la sesión arrancó en `ccr-5066a9d8-e0t3z9`, ya contenida en esta).
- Ruling (el director, solo lectura, esfuerzo máximo): **APROBADA CON CAMBIOS**; se puede publicar, ningún hallazgo de seguridad. Texto entero en `final-audit.md`, «Aprobación del director».
  - AD-1 (Menor): guardar la misma contraseña no saca un acceso de «Filtrada» (la marca se congela, CR-1); la casilla `README.md:494`, R-211-2 y el KDoc de `LeakResults` decían lo contrario. Arreglado en `6377be9`, con test.
  - AD-2 (Menor): F-1 decía que «¿Filtrada?» se olvida al bloquear; no con cada bloqueo (`ScreenAway.lockWipes`). F-1 corregido en la auditoría; la política no cambia en eso; «al bloquear la bóveda», sin más, sería falso.
  - AD-3 (Info): `LEAK_RECHECK_NEEDED` no sale por accesos añadidos o cambiados que no salieron filtrados. Para el Abogado.
  - AD-4 (Info): R-211-1 con el texto del director.
  - F-2: arreglado antes del Abogado en `1b04ebe` («cuando importas una copia que la reemplaza», `PublishedTextsTest`); F-1 se le deja al Abogado.
- Sin otro repaso del director: `LeakResultsTest`, `PublishedTextsTest` y `AppearanceTextsTest` en verde (45 tests); la compilación completa, antes del PR.
- Siguiente: la revisión final del Abogado (lista en `final-audit.md`, los dos apartados «Textos para el Abogado Digital»), la fecha de la política y el PR cuando lo pida el dueño.
