# Repaso acotado de `db42d3e` y `ce6b0f9`: arreglos de M-1 a M-4 y de RT-8 a RT-13

**Alcance:** repaso acotado de `be77431..ce6b0f9`, con el paquete `.superpowers/sdd/2026-11-03-clavero-1.4.0/review-be77431..ce6b0f9.diff`.
- **Decisiones que obligan:** R45 a R52 (`progress.md:259-272`).
- **Hallazgos que se arreglan:** los de `redteam-second-fixes-review.md` y `redteam-second-recheck.md`.
- **Informe del implementador:** `redteam-recheck-fixes-report.md`.

Solo lectura y sin subagentes. No volví a ejecutar la suite; la puerta que se informa es app 1855 / jvm-check 1850, 0 fallos, 0 omitidos, 0 `w:`, lint 0 errores / 17 avisos y check-manifest OK. Para las dos dudas concretas de abajo:
- llamé a `urlKey` y a `shownUrl` (por reflexión) sobre una **copia** de las clases ya compiladas de `jvm-check`, hecha en el *scratchpad* de la sesión, sin Gradle;
- antes comprobé que `app/src/main` del árbol es `ce6b0f9` (`git diff --quiet ce6b0f9 -- app/src/main`).

Hallazgos nuevos: solo sobre código que tocan estos dos commits.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`, las de los tests a `app/src/test/java/app/seguridad/`. Líneas del árbol en `ce6b0f9`.

### Spec Compliance

| Hallazgo | Veredicto | Evidencia |
|---|---|---|
| **M-1** (R47 y R45) | ✅ Arreglado, con N-1 en el código nuevo | `imports/MergePlanTest.aWordAndAColonInFrontIsAScheme` fija `tana:clave@banco.es`. Los KDoc de `urlHost`, `urlKey` y `comparedLink` (`imports/MergePlan.kt:374-388`, `:396-405`, `:420-428`) dicen que «palabra:» es un esquema. La fila tapa la información de usuario (`shownUrl`, `:445-457`). Las frases falsas están corregidas: `ataque-1.4.0.md:217`, `:222`, `:243` y `redteam-report.md:368`, `:372`. El KDoc de `duplicateSubline` (`MergePlan.kt:459-468`) ya no dice «The scheme is not shown». Lo que no queda cubierto, en N-1 |
| **M-2** (R48) | ⚠️ En parte | `https://` y `http://` de un mismo host contra una entrada sin web dan un duplicado y una entrada nueva (`MergePlan.kt:513`; `MergePlanTest.anEntryWithoutAUrlIsADuplicateOnlyOfHttpsOrNoUrl`). Pero el respaldo se salta con «palabra:cifras/…»: I-1 |
| **M-3** (R46) | ✅ Arreglado | `tools/qr/Visible.kt:93`: `drawsNothing(cp) -> true`, después de las dos excepciones de los emoji. Sigue marcando, y conserva lo suyo (controles ISO, U+2028/U+2029, bidi). `QrContentTest.visibleTextMarksEverythingThatDrawsNothing` recorre todo Unicode. Nada de lo que se marcaba antes deja de marcarse: todo era de Default_Ignorable o de las reglas propias |
| **M-4** (R49) | ✅ Arreglado | El KDoc de `drawsNothing` (`core/Invisible.kt:71-86`) saca de la lista los signos que preceden a una cifra y dice cuáles son los Cf sin glifo. Los KDoc de `plainReportText` y de `isHiddenInName` (`crypto/FileCrypto.kt:163-176`) dicen lo mismo |
| **RT-8** (R48 y R45) | ⚠️ En parte | Los cuatro casos del informe ya son entradas nuevas, y la fila enseña el esquema que no es https. Pero `javascript:1/alert(1)`, `data:1/…` o `intent:1/#…;end` dan la clave `hhttps://javascript`, así que siguen siendo duplicado de una entrada sin web, con la fila «ana · javascript»: I-1 |
| **RT-9** (R49) | ✅ Arreglado | `DEFAULT_IGNORABLE` (`core/Invisible.kt:25-43`) son los 17 rangos de Default_Ignorable_Code_Point de Unicode 15.0. Los repasé contra DerivedCoreProperties: 00AD, 034F, 061C, 115F–1160, 17B4–17B5, 180B–180F, 200B–200F, 202A–202E, 2060–206F, 3164, FE00–FE0F, FEFF, FFA0, FFF0–FFF8, 1BCA0–1BCA3, 1D173–1D17A, E0000–E0FFF. U+2065 y U+FFF0–U+FFF8 entran. Además, `InvisibleTest.whatANameDropsIsDroppedFromWhatIsShownAndCompared` añade el otro sentido |
| **RT-10** (R46) | ✅ Arreglado | El de M-3 |
| **RT-12** (R51) | ✅ Arreglado | `withLowerScheme` (`MergePlan.kt:418`; `schemeOf` solo acepta ASCII, así que la longitud no cambia), en `urlHost` (`:393`) y en la clave «t» (`:411`). La fila enseña el texto tal como está escrito. Lo fija `MergePlanTest.aUrlWithoutAHostKeepsItsCaseAfterTheScheme` |
| **RT-13** (R49 y R52) | ✅ Arreglado | Una sola lista, `VISIBLE_PREPENDED_MARKS` (`core/Invisible.kt:67-68`); `drawsNothing` la deja fuera (`:88-90`), así que lo que se enseña y lo que se compara conservan esos signos. Los nombres los siguen quitando (`crypto/FileCrypto.kt:181`) |
| **R50, fuera `rt11_`** | ✅ | `git grep "@Ignore\|rt11_" ce6b0f9 -- app/src/test jvm-check` solo encuentra tres comentarios de cabecera. Las once contraseñas siguen en `redteam-second-recheck.md:116`. El pendiente está en `CLAUDE.md:158` (punto 11) y en el traspaso (`:54`, punto 9) |
| **R52: nombres iguales byte a byte que en `be77431`** | ✅ (en la JVM) | No hay cambios de producción entre `bf04f45` y `be77431` (`git diff --stat bf04f45 be77431 -- app/src/main` vacío). La lambda «before» de `InvisibleTest.theNamesDropWhatTheyDroppedBefore` es exactamente el `isHiddenInName` de `bf04f45`, y el test compara los dos en todo Unicode. El resto de `safeFileName` y de `sanitizeReceivedName` no cambia. Hay un matiz en el móvil (Info abajo). `CryptoTest` y `PkActionsTest` quedan como en `be77431`: no salen en el *stat* del rango |
| **Ningún formato cambia** | ✅ | Los commits no tocan cabeceras, prefijos, longitudes, KDF ni lectura de formatos. `FileCrypto.kt` solo cambia el conjunto de `isHiddenInName`, que sale igual |
| **Documentos, contra el código** | ⚠️ | Son verdad: README `:35` y `:348` (P-9), `.maestro/README.md:70`, `CLAUDE.md:158`, el traspaso `:54` y `task-12-report.md`, puntos 13, 15 y 16. Salvo tres cosas: la palabra «nunca» de la fila (N-1, que afecta a README `:35` y `:348`, `.maestro:70`, `task-12-report.md` punto 12 y `MergePlan.kt:464`); «(en el README)» de `ataque-1.4.0.md:284` (N-2); y el punto 14 de `task-12-report.md` (N-3) |
| **Palabras prohibidas; correo del dueño; enlaces de sesión** | ✅ | No hay ninguna en las líneas añadidas de `app/src/main`, README, CLAUDE.md, `.maestro` ni en los documentos publicados. El informe de trabajo del implementador usa «del lado seguro» y «no lo limpia» (Info) |

### Strengths

- `drawsNothing` deja de depender de `Character.getType` (`core/Invisible.kt:88-90`), así que lo que se enseña, lo que se compara, los nombres y el Inspector dicen lo mismo en cualquier ICU.
- Los datos van con su versión y su URL, y una búsqueda binaria sobre 17 pares. ASCII sale por el camino rápido (`cp >= 0x00AD`).
- **Tres tests sobre todo Unicode que cierran la lista en los dos sentidos:**
  - el Cf del JDK está en la lista o es un signo que precede a una cifra (`theListIsTheDefaultIgnorablesWithTheDecidedInvisibles`);
  - lo que quita un nombre lo quitan también lo que se enseña y la clave;
  - el Inspector marca todo lo de la lista.
- **R52 está demostrado,** no solo afirmado: el test compara `isHiddenInName` con la regla anterior, punto de código por punto de código.
- **El respaldo de R48 es una línea** (`MergePlan.kt:513`) y no toca el caso legítimo: la exportación trae la web https que a la bóveda le faltaba.
- **`shownUrl`, en la rama con host, solo escribe el host,** así que la información de usuario de una URL con host no puede salir nunca.

### Issues

#### Critical

Ninguno.

#### Important

**I-1. El respaldo de R48 se salta con «palabra:cifras»: `javascript:1/alert(1)` cuenta como https, sigue siendo duplicado de una entrada sin web y la fila dice «ana · javascript».**
- **Dónde:**
  - `imports/MergePlan.kt:429-437` (`comparedLink`, la lectura «host:puerto» de `:433-434`);
  - `:513` (el respaldo `g.hostless?.takeIf { host.startsWith(HTTPS_KEY) }`);
  - `:450` (`shownUrl`, que en la rama con host enseña solo el host si el esquema es `https`).
- **Qué pasa:** `comparedLink` lee como «host:puerto» cualquier «palabra:» seguida de cifras y de «/», «?», «#», «\» o del final (lo pensado para «localhost:8080»), y le pone `https://` delante. Comprobado con las clases compiladas de `ce6b0f9`:
  - `javascript:1/alert(1)` y `javascript:1/%0Aalert(1)` dan `key=hhttps://javascript` y `row=javascript`;
  - `data:1/x` da `hhttps://data`;
  - `intent:1/#Intent;package=com.example.evil;end` da `hhttps://intent`.

  Las tres claves empiezan por `HTTPS_KEY`. Con «Banco» / «ana» sin web en la bóveda, el elemento es duplicado y «Reemplazar» guarda el `javascript:` (`replaceWith`, `:263`).
- **Por qué importa:**
  - Es el mismo hallazgo que RT-8 (Baja, CVSS 2,5), con un rodeo. Lo que R48 dice («any other scheme imports as a new entry») y lo que dicen el KDoc de `planMerge` (`:68-85`) y README P-9 («un `http://` sale como entrada nueva») no se cumple para esta forma.
  - R45 tampoco se cumple: la fila no enseña el esquema, porque el código lo toma por `https`.
  - `javascript:1/alert(1)` es un enlace `javascript:` que ejecuta código: `1/alert(1)` llama a `alert`.
  - Clavero no abre ningún enlace (decisión (c)), y la fila enseña «javascript», que da una pista. Por eso el impacto es bajo, pero el arreglo de RT-8 no se puede dar por bueno hasta cerrarlo. Es **Important** por la regla de `experto-seguridad-cripto` (un hallazgo de seguridad confirmado es Important como mínimo). No es Critical, porque nada lo ejecuta.
- **Arreglo, el más pequeño:**
  - en `comparedLink`, leer «palabra:cifras» como host y puerto solo cuando la palabra parece un host: lleva un «.» (`banco.es:8443`) o es `localhost`. Si no, la palabra es un esquema (R47) y el texto se compara entero;
  - tests: `urlKey("javascript:1/alert(1)") == "tjavascript:1/alert(1)"`;
  - `anEntryWithoutAUrlIsADuplicateOnlyOfHttpsOrNoUrl` con `javascript:1/alert(1)`, `data:1/x` e `intent:1/#…;end` como entradas nuevas;
  - la fila «ana · javascript:1/alert(1)»;
  - y que `banco.es:8443` y `localhost:8080` sigan dando https.
- **Coste:** unas 2 líneas y 4 aserciones. «intranet:8080», un host de una sola etiqueta sin esquema, pasaría a compararse entero: sería una entrada nueva visible, la dirección segura. Es una decisión del controlador, como extensión de R47 y R48.

#### Minor

**N-1. La fila sí enseña la contraseña de un enlace sin host cuando la contraseña lleva «/», «?» o «#».**
- **Dónde:** `imports/MergePlan.kt:451-456` (`shownUrl`: la última «@» se busca solo antes del primer «/», «?» o «#»). La frase «nunca» está en `MergePlan.kt:464`, `README.md:35` y `:348`, `.maestro/README.md:70` y `task-12-report.md`, punto 12.
- **Qué pasa:** comprobado con las clases de `ce6b0f9`: `ana:cl/ave@banco.es`, `ana:cla?ve@banco.es` y `ana:clave#1@banco.es` dan `row=` con el texto entero, contraseña incluida. `ana:clave@banco.es` sí queda «ana:…@banco.es». En una URL con host no pasa (solo se escribe el host).
- **Por qué importa:** la fila solo sale si la entrada de la bóveda tiene el mismo texto (clave «t»), así que el usuario ya lo tenía guardado. Pero cuatro textos publicados dicen «nunca el usuario ni la contraseña» (R36: los textos dicen lo que hace el código).
- **Arreglo:** en la rama sin host, buscar la última «@» de todo el texto (`t.lastIndexOf('@')`), no solo antes de «/?#». Tapa de más, que es el lado que el propio informe elige: `mailto:a@b.es?cc=c@d.es` da «mailto:…@d.es» y sigue enseñando el esquema. Un test con las tres formas, y el KDoc de `shownUrl` (`:438-444`).

**N-2. El informe publicado dice que las pruebas en el móvil de RT-6, RT-9, RT-10 y RT-13 están en el README, y no están.**
- **Dónde:** `docs/auditorias/2026-11-08-ataque-1.4.0.md:284`: «**Necesita prueba en el móvil** (en el README): … «Banco» + U+2065 o + U+FFF0 se ve como «Banco» …; un QR de Wi-Fi con la red «Casa» + U+2800 sale marcado …; y un título «Banco ۝1» enseña el signo».
- **Qué pasa:** el README (P-1 a P-26, `:338-365`) solo tiene la de P-9. `git show ce6b0f9:README.md | grep -E "U\+2065|U\+2800|Casa|۝"` no encuentra nada. Tampoco está la de RT-6 de la ronda anterior («Banco» + U+2800 sale como duplicado).
- **Por qué importa:** la lista P-n del README es la que el dueño recorre en el móvil (`CLAUDE.md`, pendiente 1), así que estas pruebas se perderían, y la frase publicada no es verdad.
- **Arreglo:** añadir una P-27 en el README (y en la tabla de `.maestro/README.md`) con las cuatro comprobaciones (RT-6 U+2800, RT-9 U+2065/U+FFF0, RT-10 «Casa» + U+2800 en el Inspector, RT-13 «Banco ۝1»), o quitar «(en el README)».

**N-3. `task-12-report.md`, punto 14, dice que los signos que preceden a una cifra se ven «ahora» en el Inspector de QR sin marca, y el Inspector nunca los marcó.**
- **Dónde:** `docs/superpowers/handoff/sdd-1.4.0/task-12-report.md:197`.
- **Qué pasa:** el `isHiddenCodePoint` de `be77431` no tenía Cf genéricos, y el conjunto bidi (`crypto/FileCrypto.kt:311-312`) no los incluye. Lo nuevo es solo que se ven en las listas.
- **Arreglo:** «se ven ahora en las listas (en el Inspector de QR ya salían sin marca)».

#### Info (sin arreglo)

- **R52, en el móvil:** «iguales byte a byte» es verdad en la JVM (Unicode 15.0). En un Android cuya ICU es anterior a Unicode 15.0, el `Character.getType` de antes no veía como Cf algunos puntos de código que la lista fija sí quita: U+13439–U+1343F (Unicode 15.0), U+0890/U+0891 (14.0)… Así que un nombre con ellos pierde ahora esos caracteres en ese móvil. Es el lado seguro y de alcance mínimo, y no toca ningún formato. Basta, si se quiere, con una frase en R52.
- **El informe de trabajo del implementador** (`redteam-recheck-fixes-report.md`, «Revisión experto-seguridad-cripto») usa «del lado seguro» y «no lo limpia». No es texto de la interfaz ni un documento publicado del producto (`ForbiddenWords` mira la interfaz), pero el resto de lo publicado evita esas palabras.

### Assessment

**Task quality:** Needs fixes. Hay un Important (I-1: el arreglo de RT-8 se salta con «palabra:cifras») y tres Minor (N-1 a N-3).
- **Bien hechos y verificados:** RT-9, RT-10, RT-12 y RT-13, M-3 y M-4, R50 y R52.
- **Arreglados, menos lo que queda en I-1:** RT-8 y M-2.
- **Arreglado, menos lo que queda en N-1:** M-1.

### Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)) y un QR hostil. Ningún cambio toca primitivas, claves ni formatos (`.sgenc`, `SG1:`, `seguridad-vault`, `seguridad-backup`). Lo guardado no cambia (R9 y R10): `shownUrl` y `withLowerScheme` solo leen (`imports/MergePlan.kt:418`, `:445-457`).
- **R48:** la regla es correcta, pero su llave (`HTTPS_KEY`, `:415` y `:513`) depende de que «https» solo salga de un esquema https real o de un texto sin esquema. `comparedLink` (`:433-434`) también lo da para «palabra:cifras», que es I-1. CWE-451 (la interfaz no enseña lo que importa de una acción) para la fila; MASVS-CODE-4.
- **R45:** en la rama con host, la información de usuario no puede salir. En la rama sin host, el corte en «/?#» deja salir una contraseña con esos caracteres (N-1).
- **R49:** la base es Default_Ignorable_Code_Point (UAX #44; la usa UTS #39), la misma que la lista de las tarjetas (`crypto/pk/Card.kt:237-250`). El `&& cp !in VISIBLE_PREPENDED_MARKS` (`core/Invisible.kt:90`) solo protege de un cambio futuro de los datos, porque ninguno de esos signos es Default_Ignorable. Enseñar los signos que preceden a una cifra no abre ninguna suplantación: se dibujan, y su clase bidi (AN y AL) es la de cualquier cifra o letra árabe que la lista ya enseña.
- **R46:** marcar en lugar de quitar es lo correcto en un inspector. `hasHiddenChars(…, allowEmojiVariation = false)` sigue marcando U+FE0E/U+FE0F en un host (`tools/qr/LinkSignals.kt:103`), y la señal `HIDDEN_CHARS` sale en más hosts, que es la dirección buena.

### Revisión Ingeniero Móvil Senior

- **Kotlin puro, `private` e `internal`** (`core/Invisible.kt:25-90`, `imports/MergePlan.kt:415-457`), en `jvm-check`. No hay pantalla nueva ni estado nuevo; no hay nada en `rememberSaveable` ni en un `Bundle`. `ui/screens/ImportScreen.kt` solo cambia un KDoc (`:663-666`).
- **Coste:**
  - `drawsNothing` es O(1) para ASCII y cinco pasos de búsqueda binaria para lo demás;
  - `cp !in VISIBLE_PREPENDED_MARKS` solo se mira cuando lo anterior da sí (`:88-90`);
  - en `isHiddenInName` (`crypto/FileCrypto.kt:181`), `cp in VISIBLE_PREPENDED_MARKS` encaja en un `Integer` cada punto de código mayor que 127 de un nombre. Con un máximo de 120 puntos de código por nombre es despreciable; un `IntArray` ordenado o un rango lo quitaría, si se quiere.
- **Consistencia entre móviles:** quitar `Character.getType` de `drawsNothing` hace que lo que se enseña y lo que se compara no dependa de la ICU del sistema (Android 8 a 16). Lo que sí cambia en el móvil, en el lado seguro, está en el Info de R52.
- **Tests:** cuatro recorren 0..0x10FFFF (`InvisibleTest` y `QrContentTest`). Corren en la JVM, que es donde se fija la regla.

### Textos para el Abogado Digital (revisión final)

- **La fila del duplicado** (`imports/MergePlan.kt:445-475`):
  - con I-1, «ana · javascript» pasará a «ana · javascript:1/alert(1)»;
  - con N-1, la fila tapará también una contraseña con «/», «?» o «#»;
  - hasta entonces, la palabra «nunca» de `README.md:35` y `:348`, de `.maestro/README.md:70` y de `task-12-report.md`, punto 12, no es del todo verdad.
- **El informe publicado del ataque:** `docs/auditorias/2026-11-08-ataque-1.4.0.md:284` dice que las pruebas en el móvil están en el README (N-2). Hay que añadirlas como P-27, con sus ejemplos (`banco.es`, «Casa», «۝»), o corregir la frase.
- **`task-12-report.md`, punto 14:** la corrección de N-3 («en el Inspector de QR ya salían sin marca»).
- Lo demás es verdad contra el código y no hay ningún literal nuevo de interfaz: `CLAUDE.md:158`, el traspaso `:54` y `task-12-report.md`, puntos 13, 15 y 16.
