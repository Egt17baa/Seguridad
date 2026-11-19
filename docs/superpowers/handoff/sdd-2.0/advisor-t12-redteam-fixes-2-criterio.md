# Director: el criterio de RT-25 da 1,28× (19-11-2026)

**Pregunta:** el test de criterio de RT-25 («cada forma hostil ≤ 1,25 × la legítima más cara, por byte») da 1,28×. ¿Qué se hace?

**Lo medido** por el implementador (estable en tres ejecuciones, con calentamiento y la mediana de 5):

| Forma | Resultado |
|---|---|
| Legítima más cara, escrita por la app (UUID, fechas de 13 cifras) | 17,65 B/B |
| Hostil «etiquetas distintas justo en el tope», frente a esa legítima | 1,28× |
| De ella, solo la codificación compacta, sin etiquetas sobrantes | 1,21× |
| De ella, el exceso de 10 000 etiquetas | 0,07× |
| Las demás hostiles | 0,38–0,49× |
| El suelo | 2,95 MB, constante |

**Veredicto:** (i). Se acepta el 1,28× sin poner otro tope ni subir el umbral. El criterio de RT-25 se redefine así:
- lo que deja pasar el tope (b) se juzga **en absoluto**, con un máximo de 4 MiB;
- la densidad del JSON compacto (1,21× con este generador) queda como **riesgo con nombre** de la fila 51 de `pendientes.md`, con una guardia de regresión;
- el 1,28 se publica tal cual en el informe.

## Motivos

1. **El 1,28 suma dos cosas distintas.**
   - 1,21 es la codificación, que crece con la copia.
   - 0,07 es el exceso fijo de 10 000 etiquetas (`BackupCheck.kt:219-227`, `:240`) repartido en una muestra de 1 MiB. Depende de la muestra: en 24 MiB casi no pesa, y en 100 KiB pasa de 1×.
   - Un criterio por byte que lleva dentro un término fijo se reabre con cualquier muestra pequeña.
2. **Ningún tope de etiquetas cierra la codificación, y cerrarla rompe datos.**
   - LIBRE-1 admite ids cortos (`VaultStore.kt:1734-1741`, `BackupCheck.kt:244-246`): lo de la 1.0 a la 1.2.x se lee siempre.
   - Las fechas no tienen valor por defecto (`Vault.kt:74-75`) y `appJson` no escribe los valores por defecto (`core/Json.kt:6-10`).
3. **La opción (ii), un coste mínimo por entrada en el presupuesto, no muerde.**
   - Para esta forma manda `checkCopySize`, no el presupuesto: según el cálculo del director, unos 232 MiB en 24 MiB, por debajo de `COPY_TREE_MAX_BYTES`.
   - Ese mínimo obliga a recalibrar RT-21.
   - En `checkCopySize`, sería una regla solo de copias: una bóveda que se abre y cuyas copias se rechazan.
4. **Una base fija se salta cambiando el generador**, por ejemplo con etiquetas más cortas o con espacios (`EntryQuery.kt:48-50`, `FuzzySearch.kt:49-58`).
   - Lo que de verdad acota la lista:
     - los topes de los textos que indexa cada entrada;
     - `checkCopySize`;
     - `LIST_NO_MEMORY` (`VaultList.kt:306-308`).
5. **En 24 MiB no cambia nada** (extrapolación lineal, sin medir).
   - La bóveda de la app pide unos 424 MiB, la pareja compacta unos 514 MiB y la forma del tope unos 517 MiB.
   - Las tres acaban en `LIST_NO_MEMORY`, no en un cierre de la app.
   - La lista solo indexa las entradas vivas que no son personales (`VaultList.kt:323`, `:328`), así que la medida es conservadora.

## Encargo para el implementador

1. **Test nuevo** `app/src/test/java/app/seguridad/redteam/RedTeam20ListCostTest.kt`, sin `@Ignore`. Su KDoc copia este criterio, que sustituye a `advisor-t12-redteam-fixes-2.md:37-51`.
   - **B (el criterio):** el suelo de 2 entradas con 5 010 etiquetas distintas cada una (exceso exacto 10 000). Mediana de 5 ≤ 4 MiB, en absoluto.
   - **C (cada forma es la que dice):**
     - cada forma pasa `accepted`;
     - `copyExtraTags` da el exceso exacto: 0 en cada pareja sin exceso; 10 000 en la del tope, en la de versiones y en el suelo; 9 990 en la de repetidas.
   - **D (guardia de regresión, no decide hallazgos):** la pareja sin exceso más densa (hoy 21,40 B/B) no pasa de 1,5 × la legítima más cara escrita por la app (hoy 17,65, es decir 1,21×).
   - **Filas informativas, sin aserción:**
     - cada hostil frente a su pareja con el mismo `Gen()`;
     - el 1,28 frente a la legítima con UUID;
     - los nombres de campo;
     - `copyTreeEstimate` por byte de la pareja y de la del tope, y su producto por `VAULT_MAX_DATA_BYTES + COPY_DATA_MARGIN_BYTES` frente a `COPY_TREE_MAX_BYTES`.
   - **Cómo se mide:** `Random(20261118L)` por forma, calentamiento y mediana de 5, en menos de 10 s en cada suite. Si tarda más, una muestra de 512 KiB con las mismas aserciones, y se dice en el informe.
2. **Rojo**, sin commit: con `false &&` en `BackupCheck.kt:240` y un suelo de exceso 100 000, B falla. Se enseña la salida y se deshace.
3. **`rt25_`:** la rama «aceptada» (`RedTeam20RecheckTest.kt:110-118`) pasa a `assertTrue(refused)`, con el exceso en el mensaje. Su KDoc remite a `RedTeam20ListCostTest`.
4. **Ejecuciones** con `flock`, de una en una:
   - jvm-check, solo `RedTeam20ListCostTest`, tres veces;
   - la app, una vez;
   - después, `redteam.*` en cada suite.
   - Se para y se consulta si B pasa de 3,5 MiB, si D pasa de 1,30 o si un margen se mueve más de ±0,02 entre ejecuciones.
5. **Informe:** el punto 4 pasa a «hecho», con la tabla (mínimo, mediana, máximo, margen y tiempo). El 1,28 se queda tal cual.
6. **Commit:** «2.0, arreglos del ataque (ronda 2): RT-25 parte b, el test del criterio (redefinido por el director)».
7. **`pendientes.md`**, junto a la fila 51: el riesgo con nombre «densidad de la codificación compacta», con:
   - el 1,21× medido con este generador;
   - las extrapolaciones a 24 MiB, marcadas como extrapolación;
   - que las etiquetas cortas o con espacios pueden ser más densas;
   - la medida en el móvil.
8. **Después**, los puntos 5, 6 y 7 de la ronda, sin cambios.

## Para el encargo de la re-comprobación 2

Sustituye a `advisor-t12-redteam-fixes-2.md:37-51`, `:134` y `:140`, y a la parte de 1,25× de `advisor-t12-redteam-fixes-2-revision.md`. El tope es el (b).

- **Es hallazgo de RT-25:**
  - (a) un exceso que cueste más de 4 MiB o que crezca con la copia;
  - (b) un texto que la lista construye por entrada y que ninguna regla limita;
  - (c) algo que, al abrir la bóveda, cierre la app fuera de la captura.
- **No es hallazgo:**
  - una copia sin exceso, en cualquier codificación que acepten las comprobaciones (ids cortos, fechas, títulos vacíos, etiquetas cortas o con espacios);
  - una muestra pequeña en la que el exceso fijo pese más.

## Riesgos o dudas

- El director no ejecutó nada: las medidas son del implementador.
- Los 232 MiB son un cálculo suyo y solo valen para etiquetas de 5 letras. Cada cadena paga 48 B fijos (`VaultLimits.kt:283`), así que con cadenas más cortas puede mandar el presupuesto; lo dirá la fila informativa.
- Las extrapolaciones son lineales y nadie las ha medido.
- **El factor de codificación sigue abierto.** Una copia compacta hecha a mano pide alrededor de un 20 % más por byte. Cerrarlo rompería LIBRE-1 o pondría una regla solo de copias, así que se acepta como riesgo con nombre. No es rebajar el hallazgo: el 1,28 se publica.
- **La CI de `main` corre en otra máquina** y ningún `build.gradle.kts` fija el *heap* de los tests. Las holguras son 4 MiB frente a 2,95 MB (1,42×) y 1,5 frente a 1,21 (1,24×).
- **Las pantallas no guardan títulos vacíos** (`VaultEntryScreen.kt:510`). Por eso se llama «pareja sin exceso» y no «legítima».

## Revisión experto-seguridad-cripto

- **CWE-770 y CWE-400, decisión (a):** cada fase tiene su tope antes de reservar memoria.
  - El árbol: `VaultLimits.kt:298`, `:317-322`.
  - El tamaño: `BackupCheck.kt:278-281`.
  - El exceso: `BackupCheck.kt:219-227`, `:240`.
  - La última defensa: `VaultList.kt:306-308`.
- Se separa un efecto de la muestra de un riesgo real, con su cifra y su guardia.
- LIBRE-1 no se toca. Sin primitivas, formatos, dependencias ni textos nuevos.

## Revisión Ingeniero Móvil Senior

- **Coste del índice:** se paga por cada texto distinto y por cada palabra (`EntryQuery.kt:112-114`, `:130-133`; `FuzzySearch.kt:49-58`). Las claves de los títulos se comparten (`EntryQuery.kt:118-119`). La compacidad gana bytes, no objetos.
- **El test corre en las dos suites** (`jvm-check/build.gradle.kts:22` solo excluye `**/ui/**`). Las holguras son amplias, con un `Random` por forma, calentamiento, mediana y menos de 10 s.
- **La interfaz no cambia:** la captura de `b632779` sigue igual y no hay estado nuevo.

Modelo: el director.
