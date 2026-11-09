# Revisión de `bf04f45`: arreglos del segundo ataque (RT-4 a RT-7)

**Alcance:** puerta de la tarea (no de la fusión) sobre `e31873d..bf04f45`, con el paquete `.superpowers/sdd/2026-11-03-clavero-1.4.0/review-e31873d..bf04f45.diff`, las decisiones R41 a R44 (y R9, R10, R34, R36, (c) y (h)) de `progress.md:249-252`, el informe del ataque y el del implementador. Solo lectura. Fuera del diff miré una sola cosa por riesgo con nombre (abajo, en cada punto). No volví a ejecutar la suite: la puerta que se informa es app 1831 / jvm-check 1826, 0 fallos, 0 omitidos, 0 `w:`, lint 0 errores / 19 avisos.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`, las de los tests a `app/src/test/java/app/seguridad/`. Líneas del árbol en `bf04f45`.

### Spec Compliance

| Requisito | Estado | Evidencia |
|---|---|---|
| R41: el esquema entra en la clave del duplicado; sin esquema se lee como https | ✅ | `imports/MergePlan.kt:401-407` (`urlKey`), `:416-423` (`comparedLink`), `:461` y `:476` (`Matcher`) |
| R41: todas las formas de escribir el mismo host https siguen casando (mayúsculas, `www.`, puerto, sin esquema, `\`, `//`, información de usuario) | ✅ | `MergePlanTest.theSchemeOfTheUrlIsPartOfTheKey` (8 formas). Comprobé a mano que `schemeOf` y `ParsedLink.scheme` están en minúsculas (`tools/qr/LinkSignals.kt:119-124`) y que `mapHost` quita el punto final |
| R41: `http` y los esquemas que no son web nunca casan con https | ✅ | Solo dan la clave `hhttps://…` un texto con el esquema `https` o uno sin esquema (o «host:puerto»), y en este último el «host» es lo que va delante de «:». 12 casos en el test; repasé además tabuladores, C0 al principio y ancho completo en el esquema: todos dan otra clave (la dirección segura) |
| R41: no lanza con cualquier entrada | ✅ | `comparedLink` guarda todos los índices (`MergePlan.kt:420-422`); `parseLink` no lanza (`LinkSignals.kt:55-62`) |
| R41: «una URL sin esquema se lee como https, **como ahora**» | ⚠️ Menor | «ana:clave@banco.es» antes era `banco.es` y ahora es el esquema «ana» (M-1) |
| R41: la fila sigue con «usuario · host» | ⚠️ Menor | Sigue así para las URL con host. Para las que no tienen host enseña ahora el texto entero, con el esquema (M-1). Cuando la entrada de la bóveda no tiene web, la fila calla un esquema que no es https (M-2, punto abierto 1) |
| R42: ¡→i, €→e, ø/Ø→o | ✅ | `vault/MasterPolicy.kt:91-102`; los tests de `MasterPolicyTest` comprueban que cada candidata pasa la estimación y que solo la rechaza la palabra |
| R42: se vuelve a medir la tasa de rechazo del Generador | ✅ | Queda demostrado sin repetir la medida: el Generador solo saca de `LOWER + UPPER + DIGITS + SYMBOLS`, que son ASCII (`crypto/Generator.kt:6-9`, 89 caracteres), y lo único que añade `leetLetter` es ¡ € ø Ø, así que las cifras son las mismas por construcción. El KDoc lo dice (`MasterPolicy.kt:242-246`) |
| R42: sin regresión de la regla (h) | ✅ | Los tests de RT-3 están intactos y en verde (según la puerta); «¡» solo como «i» (`MasterPolicyTest`, test de RT-5) |
| R43: una sola lista para `plainReportText`, `compareKey` e `isHiddenInName` | ✅ | `core/Invisible.kt:32-39`; `imports/ImportModel.kt:130` y `:174-180`; `crypto/FileCrypto.kt:175-179`. `TAG_CHARACTERS`, `INVISIBLE_NOT_FORMAT` y la copia privada de `canJoin` se han borrado |
| R43: las secuencias de emoji en los nombres no empeoran | ✅ | `isHiddenInName` solo gana U+2800 y U+180B–U+180D/U+180F. Lo comprobé: `isForbiddenNameCodePoint` no tiene U+FE0F (`crypto/pk/Card.kt:237-250`), así que la guarda nueva `cp != EMOJI_SELECTOR` no cambia nada, y `canJoin` y `emojiSelectorShows` se han movido sin tocar su regla. ZWJ, FE0F tras un emoji, *keycaps* y banderas regionales siguen igual. Las de etiquetas (🏴 + U+E0067…) ya perdían las etiquetas antes (E0000–E0FFF estaba en la lista de tarjetas) |
| R43: el test de todo Unicode es real | ✅ | `core/InvisibleTest.kt:39-62`: recorre 0..0x10FFFF, se salta los sustitutos y comprueba las cuatro cosas, con mensajes que nombran los puntos de código |
| R43: el Inspector de QR | ⚠️ Menor | Tiene su propia lista (`tools/qr/Visible.kt:85-98`), M-3 (punto abierto 2) |
| R44: NFKC solo para comparar; lo guardado y lo que se enseña no cambian (R9, R10) | ✅ | Solo `titleKey` y `userKey` llaman a `compareKey` (`MergePlan.kt:368`, `:371`; lo busqué en todo `main`); `ImportModelTest.compareKeyFoldsTheCompatibilityForms` comprueba que «Proﬁle» se enseña tal cual |
| `FileCrypto`: ningún formato cambia (`.sgenc`) | ✅ | El diff no toca cabeceras, prefijos ni longitudes; solo cambia el **nombre que se propone** (y el que se escribe dentro de un archivo nuevo) cuando lleva U+2800 o U+180B–U+180F. Al leer, el nombre sigue tal cual |
| Ningún `@Ignore` | ✅ | `git grep @Ignore bf04f45 -- app/src/test jvm-check` solo lo encuentra en dos comentarios |
| Palabras prohibidas | ✅ | Busqué la lista de `ForbiddenWords` en las líneas añadidas de `docs/` y `app/src/main`: nada |
| Nada secreto en `rememberSaveable` ni en un `Bundle` | ✅ | El diff no toca ninguna pantalla (`git diff --stat -- …/ui` vacío) |
| TDD: los cuatro `rtN_` fallaban antes | ⚠️ sin verificar | No reconstruí `e31873d` (solo lectura). La puerta anterior tenía 4 omitidos, que cuadra. Lo comprueba el agente de la segunda comprobación del equipo rojo |
| Las líneas que citan los informes | ✅ | `MergePlan.kt:401-423`, `:461`, `:476`; `Invisible.kt:32-51`; `ImportModel.kt:117-144`, `:173-194`; `FileCrypto.kt:175-179`; `MasterPolicy.kt:91-102`: todas coinciden con `bf04f45` |
| Las cifras de la puerta (+13 tests en cada suite) | ✅ | 4 + 3 + 3 + 2 + 1 = 13 tests nuevos (`InvisibleTest`, `ImportModelTest`, `MergePlanTest`, `MasterPolicyTest`, `PkActionsTest`) |

### Strengths

- **R41 en una sola función** (`urlKey`), con prefijos «h» y «t» para que una clave con host y una de texto entero no se crucen nunca (`MergePlan.kt:406`). `comparedLink` es una sola lectura compartida por `urlHost` y `urlKey`, así que la fila y la clave no pueden discrepar sobre el host.
- **El hueco de al lado se cierra con RT-4:** `javascript:x@banco.es/` y `mailto:ana@banco.es` se leían como `https://…@banco.es` y eran duplicado de `https://banco.es/`. Ahora no lo son (`MergePlan.kt:416-423`).
- **Una sola lista de verdad**, con un test sobre todo Unicode que impide que vuelvan a separarse (`InvisibleTest.kt:39-62`). Además, el test de los 12 × 4 vecinos fija que U+FE0F se queda en lo que se enseña justo donde se queda en un nombre (`:64-71`).
- **`compareKey` quita después del NFKC el U+FE0F que se enseña** y vuelve a normalizar (`ImportModel.kt:174-180`), así que una clave nunca lleva nada de `drawsNothing`. «❤️ Banco» y «❤ Banco» comparan igual.
- **El coste sigue siendo lineal:** `nextForSelector` solo corre tras un U+FE0F y se para en el siguiente U+FE0F, ZWJ o punto de código que se dibuja (`ImportModel.kt:150-158`).
- **El implementador arregló 9 avisos viejos del compilador** que solo salían con `--rerun-tasks`, sin cambiar lo que prueban (`Locale.forLanguageTag("tr")` es la misma lengua).

### Issues

#### Critical

Ninguno.

#### Important

Ninguno.

#### Minor

**M-1. `comparedLink` lee «ana:clave@banco.es» como un esquema, y lo que dicen los documentos y el KDoc de la fila ya no es del todo verdad.**
- **Dónde:**
  - `imports/MergePlan.kt:416-423` (`comparedLink`) y `:386-390` (`urlHost`, cuando no hay host);
  - `imports/MergePlan.kt:429-430` (KDoc de `duplicateSubline`: «The scheme is not shown»);
  - `docs/auditorias/2026-11-08-ataque-1.4.0.md:215` («La fila sigue diciendo «usuario · host», sin el esquema») y `:220` («Ninguno cambia … un texto de la interfaz»);
  - `docs/superpowers/handoff/sdd-1.4.0/redteam-report.md:368` («La fila no cambia») y `:372`.
- **Qué pasa:** `schemeOf("ana:clave@banco.es")` da «ana». No es un esquema web y tras «:» no hay cifras, así que `comparedLink` lo deja como un enlace opaco. Entonces `urlKey` da `tana:clave@banco.es` y `urlHost` da el texto entero. En `e31873d`, el mismo texto se leía como `https://ana:clave@banco.es`, con el host `banco.es`. Lo mismo pasa con `mailto:ana@banco.es`: la fila enseñaba «ana · banco.es» y ahora enseña «ana · mailto:ana@banco.es» (el propio test lo fija, `MergePlanTest.kt:192`).
- **Por qué importa:**
  - R41 dice «una URL sin esquema se lee como https, como ahora», y para quien lo escribe en una exportación, «ana:clave@banco.es» no tiene esquema. El efecto va en la dirección segura (una entrada nueva en vez de un duplicado, el coste que acepta R41), pero es un cambio que no está decidido ni apuntado.
  - Cuando es duplicado (la entrada de la bóveda no tiene web, o tiene el mismo texto), la fila enseña la contraseña de la URL, «ana · ana:clave@banco.es», donde antes ponía «banco.es». La ficha de la entrada ya enseña la web entera (`ui/screens/VaultEntryScreen.kt:180`), con `FLAG_SECURE`, así que no es una clase nueva de exposición. Aun así, el lote MN trata como secreto un enlace con datos de usuario.
  - Los documentos publicados afirman que la fila no cambia, y para las URL sin host sí cambia (regla de veracidad, R36).
- **Arreglo:**
  1. Mantener la lectura del URL Standard. Es la que cierra `javascript:x@banco.es/`, y léxicamente las dos formas no se distinguen sin una lista de esquemas conocidos.
  2. Fijarla con un test: `urlKey("ana:clave@banco.es") == "tana:clave@banco.es"`, que no es duplicado de `https://banco.es/`.
  3. Decirlo en el KDoc de `comparedLink` y `urlHost` («un "palabra:" delante cuenta como esquema, también "ana:clave@banco.es"») y apuntarlo como extensión de R41 en `progress.md`.
  4. Que la fila de una URL sin host no enseñe la información de usuario: lo que hay entre el «:» del esquema y la última «@» antes de «/?#» pasa a «…» («ana:…@banco.es», «mailto:…@banco.es»).
  5. Corregir las cuatro frases citadas: la fila de una URL sin host enseña su texto, con el esquema.
- **Coste:** unas 6 líneas, un test y un apunte para el Abogado (cambia lo que enseña la fila).

**M-2. Punto abierto 1: cuando la entrada de la bóveda no tiene web, la fila calla un esquema que no es https, y R41 abre un camino nuevo a dos duplicados de la misma entrada.**
- **Dónde:** `imports/MergePlan.kt:477` (`g.byHost[host] ?: g.hostless`), `:435-441` (`duplicateSubline`), `:263` (`replaceWith`: `url = incoming.url.ifBlank { existing.url }`) y `:204-224` (`mergeImport` aplica los «Reemplazar» en el orden del archivo).
- **Qué pasa:** con «Banco» / «ana» sin web en la bóveda, una fila del archivo con `http://banco.es/`, `javascript://banco.es/%0Aalert(1)` o `intent://banco.es/…` es duplicado (bastan el título y el usuario). La fila dice «ana · banco.es», y con «Reemplazar» la entrada se queda con esa URL. Hay algo más, y es nuevo de este commit: antes de R41, `https://banco.es` y `http://banco.es` en el mismo archivo tenían la misma clave, y la segunda contaba como «repetida en el archivo». Ahora son dos claves, las dos caen en la misma entrada sin web y salen como **dos duplicados** de ella, los dos con la fila «ana · banco.es». Con «Reemplazar», gana la última del archivo (la `http`) y en silencio.
- **Por qué importa:** la fila existe para decir adónde lleva la web que trae «Reemplazar» (rama A, M-4, y el propio KDoc: «cannot change the vault's web without the row saying where it goes»). La premisa con la que R41 dejó la fila sin esquema («con URL en los dos lados, un duplicado tiene el esquema de la bóveda», `MergePlan.kt:429-430`) no se cumple aquí. Nada se ejecuta (decisión (c): Clavero no abre ninguna URL importada), así que es Menor y no Importante.
- **Arreglo:** el recomendado, abajo, en el punto abierto 1.

**M-3. Punto abierto 2: el Inspector de QR no marca lo que `drawsNothing` sabe que no se dibuja.**
- **Dónde:** `tools/qr/Visible.kt:85-98` (`isHiddenCodePoint`) y su KDoc (`:6-18`); la usan `visibleText`, `hasHiddenChars` (`QrView.kt:203` y `:206`, el aviso de copiar con caracteres ocultos) y `parseLink.hostHidden` (`LinkSignals.kt:103`, la señal `HIDDEN_CHARS`).
- **Qué pasa:** el Inspector no marca U+2800, U+17B4/U+17B5, U+180B–U+180D/U+180F, U+034F, U+FFF9–U+FFFB, el resto de Cf (U+0600–U+0605, U+110BD, U+1D173–U+1D17A…) ni el plano 14 por encima de U+E007F (salvo U+E0100–U+E01EF). Un QR con «Banco⠀» o con U+17B4 en un host se ve como texto normal, no avisa al copiarlo y no da `HIDDEN_CHARS`. Su KDoc dice que marca «every character that cannot be seen».
- **Por qué importa:** es la misma clase que RT-6, en la herramienta cuyo único trabajo es enseñar lo oculto, y R43 quiere que lo que se enseña esté de acuerdo con lo demás.
- **Arreglo:** el recomendado, abajo, en el punto abierto 2.

**M-4. El KDoc de `drawsNothing` dice que todo Cf no se dibuja, y unos pocos sí se dibujan.**
- **Dónde:** `core/Invisible.kt:20` (y, con la misma idea, `imports/ImportModel.kt:97-98` y `crypto/FileCrypto.kt:163-164`).
- **Qué pasa:** los signos que preceden a una cifra o a una abreviatura (U+0600–U+0605, U+06DD, U+070F, U+0890–U+0891, U+08E2, U+110BD, U+110CD) son de la categoría Cf y tienen glifo. Quitarlos está bien, y ya se hacía antes de este commit (la interfaz está en español; R10 acepta lo mismo con las escrituras raras), pero «draws nothing» no es exacto.
- **Arreglo:** una frase en el KDoc: «también los pocos Cf que tienen glifo (los signos que preceden a una cifra, U+0600–U+0605, U+06DD…): se quitan igual, como acepta R10». El comportamiento no cambia.

Nota informativa, sin arreglo: NFKC descompone algunos caracteres de compatibilidad en un espacio más una marca combinante («´» U+00B4 da « ́»). Por eso «Banco´» y «Banco ´» comparan igual aunque se vean distintos. Es un falso duplicado del tipo que acepta R44 («el usuario elige»).

### Open points: rulings recommended (1 and 2)

**Punto 1, recomendación: sí es un defecto (Menor, M-2), y se arregla en la fila, no en la clave.** `duplicateSubline` enseña `esquema://host` cuando el esquema no es `https` («ana · http://banco.es», «ana · javascript://banco.es»). Con https, o sin esquema, se queda «ana · banco.es». Una URL sin host ya enseña su texto con el esquema, y M-1 le quita la información de usuario.

Por qué en la fila:
- Es el arreglo 2 que el propio atacante propuso para RT-4 (`docs/auditorias/2026-11-08-ataque-1.4.0.md`, «Arreglos propuestos»).
- Sirve en todos los casos sin mirar la entrada de la bóveda: con URL en los dos lados, un duplicado `http` tiene `http` en la bóveda, así que la fila dice la verdad.
- Separa también los dos duplicados de M-2, que se verían distintos.

Las otras opciones son peores:
- No tratarlo como duplicado cambia la regla del spec («si un lado no tiene URL, bastan el título y el usuario», R34) y convierte en entradas nuevas las fusiones legítimas (la exportación trae la web que a la bóveda le faltaba).
- Conservar el valor de la bóveda deja la web vacía, y «Reemplazar» dejaría de traer lo que el usuario quería.

Coste:
- unas 4 líneas en `MergePlan.kt` (la lectura del esquema ya está en `comparedLink`);
- cambian dos aserciones (`MergePlanTest.kt:191` y `:443` pasan a «ana · http://banco.es») y se añade un caso con la entrada de la bóveda sin web;
- una decisión nueva del controlador que corrija la parte «la fila sigue con "usuario · host"» de R41 para los esquemas que no son https;
- un apunte para el Abogado (cambia lo que enseña la fila) y la prueba en el móvil P-9 del README (`README.md:348`), que hoy dice «el usuario y el dominio».

**Punto 2, recomendación: compartir la lista y seguir marcando.** En un inspector, marcar es lo correcto: su trabajo es enseñar lo que se esconde, no limpiarlo. Lo que debe compartir es **qué** cuenta como oculto, no qué se hace con ello. Concretamente, `Visible.isHiddenCodePoint`:
- deja como están, delante, `'\n'` → no y su excepción del emoji (más estricta, con `isEmojiOrSymbol`, que también deja U+FE0E: un inspector puede marcar de más tras «é»);
- cambia las líneas escritas a mano (`Visible.kt:89-93`) por `drawsNothing(cp)`;
- conserva lo que solo tiene el Inspector: los controles ISO salvo `'\n'`, U+2028/U+2029 y el conjunto bidi (que ya es Cf).

Coste:
- unas 6 líneas;
- un test que recorra todo Unicode, `drawsNothing(cp) ⇒ visibleText("a${c}b")` marca `cp` (salvo la excepción del emoji);
- la señal `HIDDEN_CHARS` saldrá en más hosts, que es la dirección buena;
- ningún texto nuevo: las marcas son «⟨U+XXXX⟩», el formato de siempre. Es una decisión del controlador como extensión de R43.

### Assessment

**Task quality:** Needs fixes. Solo hay hallazgos Menores, y por la regla del dueño todos se arreglan. No hay ningún Critical ni Important: los cuatro arreglos (RT-4 a RT-7) son correctos y se pueden dar por buenos. M-1 y M-4 son de exactitud y de dejar fijada una lectura, y M-2 y M-3 son los dos puntos abiertos, con su decisión recomendada arriba.

### Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)) y un QR hostil. Ninguno de los cambios toca primitivas, claves ni formatos (`javax.crypto` y Bouncy Castle quedan fuera del diff), y el diff no toca las cabeceras de `.sgenc`.
- **RT-4:**
  - el esquema siempre forma parte de la clave (`MergePlan.kt:406`), y no hay ninguna entrada no-https que dé la clave `hhttps://banco.es` (repaso en la tabla de cumplimiento);
  - lo que queda es el respaldo por título y usuario (`:477`), M-2;
  - la lectura de un «palabra:» como esquema (`:416-423`) es la correcta frente a `javascript:x@banco.es/` (WHATWG URL Standard), pero cambia «ana:clave@banco.es», M-1. Referencia: CWE-451 (la interfaz no enseña lo que importa de una acción) para la fila; MASVS-CODE-4.
- **RT-5:**
  - la tasa del Generador no cambia por construcción (`crypto/Generator.kt:6-9`, solo ASCII);
  - sigue siendo una estimación (CRY-A01), y las contraseñas al azar con ¡ € ø pasan (`MasterPolicyTest`, test de RT-5).
- **RT-6:**
  - la lista sigue las clases de los invisibles por defecto de Unicode (UTS #39 / TR #36: Default_Ignorable_Code_Point), más el blanco de Braille y los rellenos de Hangul (`core/Invisible.kt:32-39`);
  - comprobé que de `drawsNothing` no queda nada en una clave (`ImportModel.kt:174-180`), ni en un nombre salvo ZWJ y U+FE0F con sus reglas (`FileCrypto.kt:175-179`);
  - el Inspector de QR va aparte (M-3).
- **RT-7:** NFKC solo en `compareKey`, que solo usan `titleKey` y `userKey` (`MergePlan.kt:368`, `:371`). Lo guardado no cambia (R9 y R10). Un punto de código da como mucho 18, sobre textos con tope de importación.
- **Sin logs ni secretos nuevos:** `leetLetter` trabaja sobre las copias que ya hacía `isKnownWordInLeet` (`MasterPolicy.kt:91-102`).

### Revisión Ingeniero Móvil Senior

- **Kotlin puro y `internal`** (`core/Invisible.kt:11-53`, `MergePlan.kt:401`, `comparedLink` `private` en `:416`). `jvm-check` lo compila con `kotlin.srcDir("../app/src/main/java")` (`jvm-check/build.gradle.kts:16`).
- **No cambia ninguna pantalla:** sin Compose, sin estado nuevo y sin `rememberSaveable`. La lista de la bóveda sigue llamando a `plainReportText` por fila, sin reservas nuevas por carácter (`ImportModel.kt:117-144`).
- **Lo que se ve cambia** en todas las listas, como dice el informe:
  - U+FE0E se quita siempre;
  - U+FE0F se queda solo tras un no-ASCII o en un *keycap*;
  - «❤︎» se enseña con su aspecto por defecto, lo que en Android suele ser el emoji. Es aceptable, y es lo que ya hacían los nombres de archivo.
- **Concurrencia:** sin cambios. `compareKey` corre donde corría (la vista previa, en `Dispatchers.Default`).
- **`Character.getType`** depende de la versión de ICU en Android. Es lo mismo que antes, y el KDoc lo reconoce. El test de todo Unicode corre en la JVM.
- **Coste:** `compareKey` crea un `IntStream` por clave (`ImportModel.kt:175`), que es despreciable con 20 000 entradas. El test de 40 000 claves sigue en verde según la puerta.

### Textos para el Abogado Digital (revisión final)

- **Si se acepta el punto 1 (M-2):** la fila de un duplicado enseña el esquema cuando no es https («ana · http://banco.es»), y con M-1, una URL sin host enseña su texto sin la información de usuario («ana:…@banco.es»). Es texto de la interfaz (`imports/MergePlan.kt:435-441`). Hay que poner al día la prueba P-9 del README (`README.md:348`).
- **Ya en `bf04f45`:** la fila de una URL sin host enseña su texto entero, con el esquema («ana · mailto:ana@banco.es», `MergePlanTest.kt:192`). Las frases publicadas que dicen que la fila no cambia hay que corregirlas (`docs/auditorias/2026-11-08-ataque-1.4.0.md:215`, `:220` y `:241`; `docs/superpowers/handoff/sdd-1.4.0/redteam-report.md:368` y `:372`).
- **Si la política, el README o la ficha describen** la regla de la maestra nueva (R42: «¡», «€», «ø») o cómo se buscan los duplicados (R41: `http` y `https` son sitios distintos; R43 y R44: lo invisible y las formas de compatibilidad cuentan como lo que se ve igual), tienen que decir lo mismo que el código (R36). Hoy no encuentro ninguna descripción así en `docs/privacidad.html`. El README solo lo toca en P-9.
- **Si se acepta el punto 2 (M-3):** no hay ningún texto nuevo. Las marcas «⟨U+XXXX⟩» son las de siempre.
