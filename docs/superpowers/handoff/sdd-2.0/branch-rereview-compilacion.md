# Clavero 2.0, revisión de la rama, parte D: re-revisión de la ronda

**Revisor:** parte D (solo lectura, sin gradle). **Commit:** `cad3ba5` (rama `ccr-0aa27f95-u038cd`), con la ronda en `a827d10` (código y tests) y `2dbe3ad` (documentos); informe en `docs/superpowers/handoff/sdd-2.0/task-12-2-report.md:103-` («Ronda de la parte D»). Leídos `git diff 6d3b884..cad3ba5` entero en `app/`, `jvm-check/`, `CLAUDE.md`, `README.md`, la política, la ficha, las notas y `pendientes.md`. No ejecuto gradle: las cifras (app 2412, jvm-check 2361, sin avisos `w:`) son del implementador.

**Veredicto: Needs fixes (solo Minor).** De los 7 Minor y el Info-1 de la parte D, los 7 Minor y el Info-1 están arreglados. Quedan 2 Minor nuevos, los dos de la regla de nombres de modelos, y 1 Info. 0 Critical, 0 Important.

## Cada punto

| Punto | Estado | Evidencia |
|---|---|---|
| Minor-1 (fila 50) | **Arreglado** | `app/src/test/java/app/seguridad/imports/ImportTextsTest.kt:150` lee también `*-notas.md`; `:164-165` exige `2026-11-15-clavero-2.0-notas.md` entre las partes. Las entradas de la tarea `test`: `jvm-check/build.gradle.kts:63`, `app/build.gradle.kts:242` (`include("*-traspaso.md", "*-notas.md")`). Las notas tienen un solo «Google Play, «Novedades»» (`2026-11-15-clavero-2.0-notas.md:42`), seguido de su bloque de código, así que el lector toma justo ese bloque. **Falla sin el arreglo:** con la aserción nueva y el filtro de antes, el nombre de las notas no entra en `parts` y `:165` falla (el implementador lo vio en rojo, `task-12-2-report.md`, Minor-1). Fila 50: «Hecho» con `a827d10` |
| Minor-2 | **Arreglado** | `README.md:283` y `:364` citan «Hacer una copia nueva y borrar las anteriores (automáticas, de antes de importar y de antes de la 2.0)», igual que `tools/ToolTexts.kt:508`; el texto de la 1.4.x solo queda como referencia histórica entre paréntesis en `:283` |
| Minor-3 | **Arreglado en su sitio; deja dos restos** | la sección 4 de `task-12-2-report.md:60-68` ya no nombra modelos. Pero ver R-1 y R-2 abajo |
| Minor-4 | **Arreglado** | `app/src/test/java/app/seguridad/tools/AppNameTest.kt:122-123`: `assertTrue("versionName = \"2.0.0\"" in gradle)`. **Falla sin el arreglo:** con `versionName` 1.4.3 la cadena no está (rojo comprobado por el implementador). `app/build.gradle.kts:35` dice `2.0.0` |
| Minor-5 | **Arreglado** | `pendientes.md`, fila 43: «… (`ffceacc`: …)»; `ffceacc` existe en la rama («n-2 … y n-5 (Contactos dice lo mismo que la puerta)») |
| Minor-6 | **Arreglado** | `CLAUDE.md:35`: «… y, desde la 2.0, `seguridad-antes-de-2-0-`», igual que `AppNameTest.kt:138-139` y `vault/AutoBackup.kt:114` |
| Minor-7 | **Arreglado** | `README.md:78`, en pasado: «La 1.4.0 no cambió ningún formato…» |
| Info-1 | **Arreglado** | `ui/theme/Glass.kt:250`, `:252` sin los `!!`. `backdrop` no admite nulos (el aviso lo decía), y `at` llega a `:252` comprobado por `ready`: el compilador lo da por no nulo, como decía el aviso de `!!` innecesario. `ui/AppNavHost.kt:535`: `{ nav.popBackStack() }`. Es la lambda de `remember<() -> Unit>`: el `Boolean` se descarta igual y el comportamiento no cambia |

**Del documento, n-1 y n-2 (los repasa el otro revisor; los he leído frente al código):**
- La frase del calendario, «se saltan si no hubo cambios desde la última de esa carpeta (nunca la primera)», está en `README.md:31`, `docs/privacidad.html` (párrafo de las copias), `docs/ficha-google-play.md:31` y las notas (`:30`). Es cierta: `vault/AutoBackup.kt:537-540` salta una copia del calendario solo con `t.lastAt != null && t.copiedMark != null && t.copiedMark == store.changeMark()`, y nunca una de A6 ni una que esperaba.
- La prueba de la fila 30 (`README.md:411`) también es cierta. Al borrar para siempre se hace una copia sin la entrada, y dos cambios más dejan tres copias sin ella, que son las que conserva «Conservar las 3 últimas». Una entrada que solo está en la papelera sigue en las copias (`vault/Trash.kt:9-12`).
- Palabras prohibidas (puerto de `ForbiddenWords`): la ficha y las notas solo saltan en las líneas que enumeran la regla (`ficha-google-play.md:3`, `notas.md:7`). La descripción completa de la ficha, contada desde «## Descripción completa» hasta la sección siguiente, tiene unos 6 980 caracteres con el formato Markdown. No es una regresión de esta ronda, pero conviene mirarlo antes de pegarla en Play, que admite 4 000 (Info-R1).

## Nombres de modelos en el árbol subido (`cad3ba5`)

Busqué en todo el árbol (`git grep`, sin los `assets`) después de quitar **solo** el trozo de cada ruta `/tmp/claude-…`, no la línea entera:

```
.claude/skills/advisor/SKILL.md:3     (los nombres de los modelos que puede elegir)
.claude/skills/advisor/SKILL.md:16    (la tabla de esos modelos)
docs/superpowers/handoff/sdd-2.0/branch-review-compilacion.md:107   (cinco nombres de modelos; arreglado después)
docs/superpowers/handoff/sdd-2.0/task-12-2-report.md:118           la expresión regular, con las cuatro palabras
```

Además aparecen dos de esos nombres como palabras de la lista EFF (`app/src/main/assets/eff_large_wordlist.txt:2466`, `:3088`), que no son modelos. No queda ningún identificador técnico de modelo.

## Issues

### Minor

- **R-1: la copia en `docs/` de mi informe sigue nombrando modelos.** Está en `docs/superpowers/handoff/sdd-2.0/branch-review-compilacion.md:107`, en el texto de Minor-3. La ronda quitó los de `:83` y `:117`, pero no este. `task-12-2-report.md:118` afirma que la búsqueda, «quitando las rutas `/tmp/claude-0/`», sale vacía. No es así: esa línea 107 también contiene una ruta `/tmp/claude-0/`, y al quitar la **línea** entera en vez de solo la ruta, la búsqueda se la saltó. **Arreglo:** en `:107`, poner «cita los nombres de los modelos de los pies de commit, del director y de los agentes» en lugar de la lista entre comillas. La comprobación correcta es `git grep -nIiE "" -- docs CLAUDE.md README.md | sed -E 's#/tmp/claude-[0-9]+[^ \`)]*##g' | grep -iE "\b(fable|opus|sonnet|haiku)\b|claude-(opus|sonnet|haiku|fable|[0-9])"`, que debe salir vacía.
- **R-2: `task-12-2-report.md:118` escribe la expresión regular con los cuatro nombres**, dentro de un informe que dice que `docs/` ya no los tiene. **Arreglo:** «la búsqueda de los cuatro nombres y de los identificadores (la de la sección 4)», sin la expresión. Lo mismo para la de los identificadores.

### Info

- **Info-R1:** la descripción completa de `docs/ficha-google-play.md` mide unos 6 980 caracteres con el formato; Play admite 4 000. Conviene medirla sin Markdown antes de pegarla. Va al paso de la ficha (`PUBLICAR.md`), no a esta ronda.
- **La skill `advisor`** (`.claude/skills/advisor/SKILL.md:3`, `:16`) sigue con los nombres, como recomendé: es configuración, no un documento. La decisión es del dueño o del controlador (Duda 2 de la parte D).

## Lo que no comprobé

- No ejecuté gradle. Los números y el rojo de los dos tests son del implementador; leyendo el código, los dos tests cambiados fallan sin el arreglo.

## Revisión experto-seguridad-cripto

- `ImportTextsTest.kt:150`, `:164-165`: la regla de no nombrar otras apps en las «Novedades» de Play (R2, R7, R8) cubre ya el borrador de la 2.0. Con `jvm-check/build.gradle.kts:63` y `app/build.gradle.kts:242`, un cambio en las notas vuelve a correr el test.
- `AppNameTest.kt:122-123`: `versionName` 2.0.0 fijado. Es el que va en el User-Agent de Pwned Passwords (`net/HttpClient.kt:28`) y el que la política pone de ejemplo («Clavero/» y el número).
- `Glass.kt:250-252`, `AppNavHost.kt:535`: no cambian permisos, red, formatos ni lo que se borra al bloquear.
- Veracidad: la frase del calendario (`AutoBackup.kt:537-540`) y la prueba de la fila 30 (`Trash.kt:9-12`) dicen lo que hace el código. R-1 es veracidad de un informe interno (dice que no queda nada y queda algo), no de un texto de la app.

## Revisión Ingeniero Móvil Senior

- `ui/AppNavHost.kt:535`: `backOnce` sin `NavBackStackEntry` sigue devolviendo una lambda recordada por `remember(nav)` que hace `popBackStack()`. Nada cambia en el ciclo de vida ni en F-1.
- `ui/theme/Glass.kt:250-252`: el difuminado sigue solo con `SDK_INT >= S` y `ready`. Al quitar los `!!` no se puede producir un NPE nuevo, porque las dos variables ya se sabían no nulas en ese punto.
- `README.md:410-411`: las dos pruebas de la 2.0 en el móvil se pueden reproducir (fila 51; fila 30 esperando cada copia).

## Textos para el Abogado Digital (revisión final)

- La frase del calendario en el README (`:31`), la política, la ficha (`:31`) y las notas (`:30`): «se saltan si no hubo cambios desde la última de esa carpeta (nunca la primera)». La guía de la app (`settings/Guide.kt:43`) sigue con «y solo si hubo cambios».
- `README.md:283`, `:364`: el botón de A6 de la 2.0.
- `README.md:411`: la prueba de la fila 30, que dice que una entrada solo movida a la papelera sigue en todas las copias.
- La longitud de la descripción completa de la ficha (Info-R1).
