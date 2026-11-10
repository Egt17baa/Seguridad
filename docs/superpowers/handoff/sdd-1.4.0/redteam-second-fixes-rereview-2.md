# Repaso acotado 2 de `f6148bb`: I-1, N-1 a N-3 y RT-14 (R53 y R54)

**Alcance:** repaso acotado de `50b0d51..f6148bb`, con el paquete `.superpowers/sdd/2026-11-03-clavero-1.4.0/review-50b0d51..f6148bb.diff`.
- **Decisiones que obligan:** R53 y R54 (`progress.md:275-277`).
- **Afirmaciones que verifico:** las de «Ronda 3 (R53, R54)» en `redteam-recheck-fixes-report.md`.

Solo lectura y sin subagentes. No volví a ejecutar la suite; la puerta que se informa es app 1867 / jvm-check 1862, 0 fallos, 0 omitidos, 0 `w:`, lint 0 errores / 17 avisos.

Para una duda concreta, el hallazgo I-2, hice una prueba de punta a punta (`planMerge`, la fila y «Reemplazar»):
- antes comprobé que `app/src/main` del árbol es `f6148bb` (`git diff --quiet f6148bb -- app/src/main`);
- la prueba corrió sobre una **copia** de las clases ya compiladas de `jvm-check` (compiladas a las 15:11, después del commit), hecha en el *scratchpad* de la sesión, sin Gradle.

Hallazgos nuevos: solo sobre código que cambia este commit.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`, las de los tests a `app/src/test/java/app/seguridad/`. Líneas de `f6148bb`.

### Spec Compliance

| Punto | Veredicto | Evidencia |
|---|---|---|
| **I-1** (R53) | ✅ Arreglado | `imports/MergePlan.kt:439`: una palabra sin «.» que no es `localhost` es un esquema. `javascript:1/alert(1)`, `data:1/x` e `intent:1/#…;end` dan claves «t»; `banco.es:8443`, `LOCALHOST:8080` y `[::1]:8080` siguen siendo https (con un «[» delante, `schemeOf` da null). Lo fija `MergePlanTest.aWordAndDigitsIsAHostAndPortOnlyWhenTheWordLooksLikeAHost`, que también comprueba que «Reemplazar» no toca la entrada sin web y que la fila dice «ana · javascript:1/alert(1)» |
| **N-1** | ✅ Arreglado | `MergePlan.kt:463`: busca la última «@» de todo el texto. `withoutAHostTheRowHidesUpToTheLastAt` cubre las tres formas, y el test de propiedad añade el caso opaco con «/?#\» |
| **N-2** | ✅ Arreglado, con N-4 | README P-27 (`README.md:366`) y su fila en `.maestro/README.md:88`; «P-1 a P-27; las siete últimas» en `.maestro/README.md:3` (P-21 a P-27 son 7 y todas manuales); `ataque-1.4.0.md:286`, «(en el README, P-9 y P-27)», ya es verdad. Lo que P-27 dice de los tres títulos, en N-4 |
| **N-3** | ✅ Arreglado | `task-12-report.md:197`, punto 14: «(en el Inspector de QR ya salían sin marca; …)» |
| **RT-14** (R54) | ✅ Arreglado en lo que pedía, ❌ con una regresión nueva (I-2) | `urlHost`, `urlKey` y `shownUrl` leen a través de `asBrowserReadsIt` (`MergePlan.kt:393`, `:411`, `:456`), y `withLowerScheme` y `comparedLink` reciben ya el texto limpio. `ht<TAB>tp:@banco.es` y `<U+0001>http:@banco.es` dan `hhttp://banco.es`, y `java<TAB>script:…@banco.es` da una clave «t». `rt14_` ya no lleva `@Ignore`, y `git grep @Ignore f6148bb -- app/src/test jvm-check` solo encuentra tres comentarios. Pero la misma limpieza convierte una URL hecha solo de controles C0 en «sin URL» para la clave y no para «Reemplazar»: I-2 |
| **`asBrowserReadsIt` fuera de `parseLink`, sin cambiar su comportamiento** | ✅ | El cuerpo (`tools/qr/LinkSignals.kt:62-63`) es literalmente la primera línea de antes de `parseLink` (`filterNot` de `\t\n\r` y `trim { it <= ' ' \|\| it.isWhitespace() }`), y `parseLink` la llama en `:74`. Llamadores: `parseLink` y los tres de `MergePlan.kt`. `schemeOf` (`:135`) solo cambia su KDoc; sus otros llamadores (`tools/qr/QrContent.kt:152` y `SecretShareInput.kt:113`, que es otra función con el mismo nombre) no cambian. El Inspector de QR lee por `parseLink`, así que hace lo mismo que antes |
| **Los dos tests de antes que cambian a propósito** | ✅ | `theHostAgreesWithTheQrReaderForEveryWebLink`: sin host, espera `asBrowserReadsIt(url)` en lugar de `url.trim()`. Es lo que pide R54, y lo que de verdad comprueba (el host, igual que `parseLink`) no cambia; `LinkSignalsTest.theBrowsersCleaningComesFirst` fija la limpieza con valores literales. `resistsTheRt12FixVariants`: `\u0001data:…` pasa a duplicado, que es correcto (el navegador quita el control delante), y las tres variantes que no son duplicado siguen. No se debilita ninguno. En `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix`, los dos casos que pasan a duplicado son los que nombra R54, y `java\tscript://banco.es/` sigue sin serlo (da `hjavascript://banco.es`) |
| **El test de propiedad es real** | ✅ | `MergePlanTest.noCleanedSchemeButHttpsGivesAnHttpsKeyAndNoRowShowsTheUserInfo` hace 20 000 textos con una semilla fija; escribe la limpieza por su cuenta (no llama a `asBrowserReadsIt`); para todo esquema limpio que no es https ni parece un host, prohíbe `hhttps://`; y pone un centinela «:Zq…» antes de la última «@», que la fila nunca enseña (repasé que en la rama con host el centinela queda en la información de usuario o en la ruta, y nunca en el host). Su oráculo del esquema es el `schemeOf` de producción, y no prueba «Reemplazar»: por eso no ve I-2 |
| **Documentos, contra el código** | ⚠️ | Son verdad: `CLAUDE.md:148` y el traspaso `:45` (P-4 a P-27), `.maestro/README.md:3` y `:88`, `ataque-1.4.0.md:9`, `:286` y `:288-308` (la tabla de RT-14, R53, R54 y los arreglos; los nombres de navegadores, con la línea de marcas en `:308`), y `task-12-report.md`, puntos 14 y 17. P-27 tiene una inexactitud (N-4), y el informe de trabajo, una frase vieja (N-5) |
| **Palabras prohibidas; correo del dueño; enlaces de sesión** | ✅ en lo publicado y en la interfaz | Nada en README, `.maestro`, CLAUDE.md, el traspaso, `ataque-1.4.0.md` ni en `app/src/main`. El informe de trabajo del implementador vuelve a usar «limpia», «limpio» (×3) y «de verdad» (Info) |

### Strengths

- **R54 va a la causa:** una sola limpieza, la del navegador, para la comparación, la fila y `parseLink` (`tools/qr/LinkSignals.kt:62`, CWE-436). La comparación ya no puede leer el esquema en otro orden que el Inspector.
- **R53 es una línea,** con su coste aceptado y probado: «intranet:8080» se compara entero.
- **El test de propiedad** comprueba la invariante de R48 (ningún esquema que no es https da `hhttps://`) y la de R45 (ninguna fila enseña la información de usuario) sobre entradas al azar, con limpieza y controles incluidos.
- **N-1 tapa de más,** como pedía el repaso, y el KDoc lo dice con un ejemplo (`MergePlan.kt:446-454`).

### Issues

#### Critical

Ninguno.

#### Important

**I-2. Una URL hecha solo de controles C0 cuenta ahora como «sin URL» para la clave, pero no para «Reemplazar», que borra con ella la web https de la bóveda sin que la fila lo diga.**
- **Dónde:**
  - `imports/MergePlan.kt:410-412` (`urlKey`: `asBrowserReadsIt` deja «» y la función da `null`);
  - `:522` (`if (host == null) g.any`);
  - `:265` (`replaceWith`: `url = incoming.url.ifBlank { existing.url }`; `isBlank` solo mira espacios en blanco, y U+0000–U+0008 y U+000E–U+001F no lo son);
  - `:455-458` (`shownUrl` da «»).
- **Qué pasa.** En la bóveda está «Banco» / «ana» / `https://banco.es/`, y en el archivo, la misma fila con la URL `\u0001`. Probado de punta a punta con las clases de `f6148bb`:

  | URL del archivo | `urlKey` | Duplicados | Fila | URL de la bóveda tras «Reemplazar» |
  |---|---|---|---|---|
  | `[U+0001]` | `null` | 1 | «ana» | `[U+0001]` |
  | `[U+0000, U+001F]` | `null` | 1 | «ana» | `[U+0000, U+001F]` |
  | `[U+0009, U+0001, U+000A]` | `null` | 1 | «ana» | `[U+0009, U+0001, U+000A]` |
  | `[U+0020]` (control de la prueba) | `null` | 1 | «ana» | `https://banco.es/`, sin cambiar |

  En `50b0d51`, `urlKey` usaba `url.trim()`, que no quita U+0001. La clave era `t\u0001`, que no casaba con la entrada https, y el elemento entraba como **entrada nueva**. Es una regresión de R54.
- **Por qué importa:**
  - Un archivo hostil (decisión (c)) puede borrar la web https de una entrada de la bóveda. La fila no enseña ninguna web («ana»), así que el usuario entiende que «Reemplazar» no la toca.
  - Choca con R41 («Reemplazar» no cambia la web de la bóveda sin que se vea), con la promesa de R45 y P-9 (la fila enseña la web que trae «Reemplazar») y con el KDoc de `replaceWith` («la URL solo donde no está vacía»).
  - El impacto es bajo: no lleva a ningún otro sitio, solo borra la web, y la copia de antes de importar la conserva. Pero es un fallo de integridad confirmado, y por la regla de `experto-seguridad-cripto` es Important como mínimo.
- **Arreglo, el más pequeño:**
  - una sola idea de «sin URL», la del navegador: en `replaceWith`, `url = if (asBrowserReadsIt(incoming.url).isEmpty()) existing.url else incoming.url`;
  - un test: `\u0001`, `\u0000\u001F` y `\t\u0001\n` contra una entrada https dan duplicado y «Reemplazar» conserva `https://banco.es/`;
  - una línea en el test de propiedad que pruebe «Reemplazar».
- **Alternativa:** que `urlKey` dé la clave «t» del texto crudo cuando el texto crudo no está en blanco y la limpieza lo vacía; entonces entraría como entrada nueva, como antes.

#### Minor

**N-4. README P-27 dice que los tres títulos «salen como repetidos», pero en un solo CSV solo el primero sale como repetido de la bóveda.**
- **Dónde:** `README.md:366` (también `.maestro/README.md:88`).
- **Qué pasa:** «Banco» + U+2800, + U+2065 y + U+FFF0, con el mismo usuario y sin URL, tienen la misma clave. `planMerge` compara antes el archivo consigo mismo (`imports/MergePlan.kt:93-97`): el primero sale como duplicado de la entrada «Banco» (una fila), y los otros dos solo se cuentan en «2 elementos están repetidos dentro del archivo: entra solo una vez.» (`imports/ImportTexts.kt:286`). Quien haga la prueba verá una fila, no tres, y puede darla por fallida. Además, solo se compara con la bóveda el primero.
- **Arreglo:** tres CSV de una fila cada uno (o importar tres veces), cada uno con su carácter, o decir lo que sale: «el primero sale como repetido y se ve como «Banco»; los otros dos, como repetidos dentro del archivo».

**N-5. El informe de trabajo dice todavía que la fila sin host tapa «hasta la última «@» antes de «/?#»».**
- **Dónde:** `docs/superpowers/handoff/sdd-1.4.0/redteam-recheck-fixes-report.md:157` (sección «Revisión experto-seguridad-cripto» de la ronda 1, que esta ronda sí tocó en la línea de al lado).
- **Arreglo:** «hasta la última «@» de todo el texto (ronda 3, N-1)».

#### Info (sin arreglo)

- **R53 y los esquemas con «.»:** una palabra con «.» delante de «:cifras» se lee como host, también cuando es un esquema propio de una app con «.», como `com.example.app:1/x`. Da `hhttps://com.example.app`, y frente a una entrada sin web es duplicado, con la fila «ana · com.example.app». Es la lectura de la barra de direcciones que eligió R53, y la fila enseña el host que el navegador abriría, así que no esconde nada.
- **Palabras en el informe de trabajo:** «limpia», «limpio» y «de verdad» (`redteam-recheck-fixes-report.md`, «Ronda 3»). No es texto de la interfaz ni un documento publicado del producto.

### Assessment

**Task quality:** Needs fixes. Hay un Important (I-2, una regresión de R54 en el camino de «Reemplazar») y dos Minor (N-4 y N-5). I-1, N-1, N-2 y N-3 están arreglados y verificados. RT-14 está arreglado en lo que pedía. La extracción de `asBrowserReadsIt` no cambia `parseLink` ni el Inspector.

### Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)). Nada toca primitivas, claves ni formatos, y lo guardado solo cambia por «Reemplazar», que es justo donde está I-2.
- **R54 (CWE-436):** la clave y la fila leen ya en el orden del navegador (`MergePlan.kt:393`, `:411`, `:456`). Pero «vacío» tiene dos definiciones: `asBrowserReadsIt(...).isEmpty()` para la clave (`:411-412`) y `isBlank` para «Reemplazar» (`:265`). Por esa diferencia se cuela I-2: el mismo patrón de dos lectores que no coinciden, ahora en el camino de escritura. Ese es el arreglo que hay que fijar con un test.
- **R53:** la llave `HTTPS_KEY` (`:522`) ya solo sale de un esquema https real, de un texto sin esquema o de un host con «.» o `localhost` delante de «:cifras». Lo comprueba el test de propiedad.
- **R45 y N-1:** con host, la fila solo escribe el host (`:460`); sin host, tapa hasta la última «@» (`:463`). La fila no enseña información de usuario en ningún caso que haya podido construir.

### Revisión Ingeniero Móvil Senior

- **Kotlin puro** (`tools/qr/LinkSignals.kt:54-63`, `imports/MergePlan.kt:392-466`). No hay pantalla, estado ni concurrencia nuevos; no hay nada en `rememberSaveable` ni en un `Bundle`.
- **Coste:** `asBrowserReadsIt` es una pasada lineal, que ahora corre dos veces por URL (en `urlKey` y otra vez dentro de `parseLink`, sobre un texto que ya no cambia). Es despreciable con 20 000 elementos en `Dispatchers.Default`.
- **Tests:** el test de propiedad y `theBrowsersCleaningComesFirst` corren en `jvm-check`. Falta un test del camino de «Reemplazar» con URLs que la limpieza vacía (I-2).

### Textos para el Abogado Digital (revisión final)

- **README P-27** (`README.md:366`) y su fila de `.maestro/README.md:88` son nuevas. Nombran Chrome y el navegador del fabricante, debajo de la línea de marcas que ya había (`README.md:367`, y la de la tabla de `.maestro`). Tienen que decir lo que de verdad sale con los tres títulos (N-4).
- **`docs/auditorias/2026-11-08-ataque-1.4.0.md:288-308`** (publicado): la sección «Tercera comprobación», con RT-14, R53 y R54. Nombra Chrome y el navegador del fabricante, con la línea de marcas en `:308`.
- **La fila del duplicado** cambia sin literal nuevo («ana · javascript:1/alert(1)», «ana · http://banco.es» para `ht<TAB>tp:@banco.es`). Está apuntado en `task-12-report.md`, punto 17. Con I-2 arreglado no cambia ningún texto.
