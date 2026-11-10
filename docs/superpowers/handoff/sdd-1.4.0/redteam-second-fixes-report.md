# Clavero 1.4.0: arreglos del segundo ataque (RT-4 a RT-7)

**Rama:** `ccr-d1e75160-p5f4wc`, sobre `e31873d`. **Decisiones:** R41 a R44 (`progress.md`). **Commit:** uno, «1.4.0: arreglos del segundo ataque (RT-4 a RT-7)» (el controlador apunta su SHA).

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`; las de los tests, a `app/src/test/java/app/seguridad/`. Las líneas son las del árbol de este commit.

## TDD

1. Quité los cuatro `@Ignore` de `redteam/RedTeamSecondAttackTest.kt` (y el `import org.junit.Ignore`) y ejecuté la clase con `jvm-check`: **13 tests, 4 fallos**, con los mensajes del informe del ataque (`«javascript://banco.es/%0Aalert(1)»: a duplicate whose row «ana · banco.es» hides the scheme`; `«Pr¡nc3ss2024» is one known word … but was:<null>`; `U+2800 is shown expected:<Banco[]> but was:<Banco[⠀]>`; `«Proﬁle» vs «Profile» expected:<Pro[fi]le> but was:<Pro[ﬁ]le>`).
2. Escribí los arreglos y los tests nuevos. Los cuatro `rtN_` pasan; no queda ningún `@Ignore` en la clase (su cabecera lo dice, `redteam/RedTeamSecondAttackTest.kt:54-61`).
3. Tres tests de antes fallaron, y los tres a propósito, por las decisiones:
   - `imports/MergePlanTest.loginDuplicatesByTitleUserAndHost` (`:59`): su fila 0 era `http://banco.ES/otra` frente a `https://www.Banco.es/…` y esperaba duplicado. Con R41 no lo es. La fila 0 pasa a `HTTPS://banco.ES/otra` (sigue probando mayúsculas, `www.` y la ruta) y se añade la fila 5, `http://banco.es/`, que ahora es entrada nueva;
   - `imports/ImportModelTest.compareKeyIsWhatIsShownInNfcWithTheSpacesFolded`, ahora `…InNfkc…` (`:67`): «Ｂａｎｃｏ» daba «Ｂａｎｃｏ»; con R44 da «Banco» (`:78`);
   - `imports/MergePlanTest.aTitleOrUserThatLooksTheSameIsADuplicate`: «Ｃａｆé Ｂａｎｃｏ» estaba en la lista de lo que no es duplicado; con R44 lo es, y pasa a la lista de lo que se ve igual, con los casos de RT-6 (`:202-204`).

## Cambios por hallazgo

### RT-4 (R41): el esquema entra en la clave del duplicado

- **`urlKey`** (`imports/MergePlan.kt:393-407`), nuevo: la clave de la URL de un `LOGIN` es «h» + esquema + `://` + host (`h https://banco.es`), o «t» + el texto entero en minúsculas cuando no sale host. `Matcher` la usa en `add` y en `find` (`:461` y `:476`), en lugar de `urlHost`. Así `http://` y `https://` del mismo host no son duplicados, ni lo es `javascript://`, `intent://`, `file://`, `ftp://`, `wss://` ni ningún otro esquema. Las dos clases de clave no se pueden cruzar (prefijos «h» y «t»).
- **`comparedLink`** (`:409-423`), nuevo y compartido por `urlHost` y `urlKey`: un texto sin esquema se lee como `https://`, como antes («banco.es», «//banco.es/», «banco.es/login»). «banco.es:8443/x» y «localhost:8080» parecen un esquema a `schemeOf`, pero son host y puerto (solo cifras tras «:», hasta el final o «/», «?», «#», «\\», y un esquema que no es web), y se leen como web, como antes.
- **Un detalle más, del mismo tipo, cerrado con RT-4:** antes, un esquema que no es web y no lleva `//` se leía con `https://` delante, así que `javascript:x@banco.es/` y `mailto:ana@banco.es` tenían el host `banco.es` y eran duplicado de `https://banco.es/`. Ahora un esquema cuenta como esquema, como lo lee el URL Standard: los dos se comparan enteros y no son duplicado. `urlHost` da para ellos el texto entero, que es lo que enseña la fila («ana · mailto:ana@banco.es»).
- **`urlHost`** (`:373-391`): sigue dando el host al que va el navegador, ahora a través de `comparedLink`. Todos sus tests de RT-1 siguen en verde (`MergePlanTest.theHostIsTheOneABrowserGoesTo`, `theHostAgreesWithTheQrReaderForEveryWebLink`, `RedTeamImportTest`, `RedTeamSecondAttackTest.resistsAuthorityConfusionsAroundTheRt1Fix` y `resistsDuplicateRowSpoofing`).
- **La fila** no cambia (R41): «usuario · host» (`duplicateSubline`, `:425-441`). Su KDoc dice ahora por qué no hace falta el esquema: con URL en los dos lados, un duplicado tiene el mismo esquema que la entrada de la bóveda.
- **KDocs:** `planMerge` (`:68-84`), `urlHost`, `urlKey`, `comparedLink` y `duplicateSubline`.
- **Tests nuevos** (`imports/MergePlanTest.kt`):
  - `theSchemeOfTheUrlIsPartOfTheKey` (`:143`): 8 formas de `https://banco.es` que siguen siendo duplicado (sin esquema, `//`, puerto, mayúsculas y `www.`, `https:banco.es`, `https:\\banco.es`, espacios alrededor); 12 de otro esquema que no lo son, y con «Reemplazar» la entrada de la bóveda no cambia; `http` frente a `http` sí es duplicado, y `https` o sin esquema frente a `http` no;
  - `aUrlWithoutAHostIsComparedWholeAndNeverMeetsAHost` (`:169`): las claves, y dos `mailto:` que solo coinciden si son el mismo texto;
  - `aSchemeThatIsNotWebIsNotReadAsHttps` (`:182`): `urlHost` de `mailto:` y `javascript:`, host y puerto, y la fila sin esquema.

### RT-5 (R42): el *leet* de un teclado español

- **`leetLetter`** (`vault/MasterPolicy.kt:84-102`): añade «ø» y «Ø» → o (`:92`), «€» → e (`:93`) y «¡» → i (`:100`). «Ø» va aparte porque el texto está en NFKC, que conserva las mayúsculas. «¡» solo se lee como «i» (no como «l», a diferencia de «1», «!» y «|»).
- **Lo que miré del informe y no añado:**
  - «º» y «ª» (la tecla a la izquierda del «1») no necesitan entrada: el NFKC de `masterCore` ya los convierte en «o» y «a» (test nuevo);
  - las sustituciones de varios caracteres («()», «vv», «|<») siguen fuera, como dice el informe: cambian el algoritmo y las decide el dueño;
  - «æ», «œ» y «ß» son dos letras; «ł» no está en el informe ni en el teclado español. Ninguno se añade.
- **KDocs:** `leetLetter` (`:84-90`) y `newMasterProblem` (`:213-215`, los ejemplos, y `:242-246`, la medida nueva).
- **Tests nuevos** (`vault/MasterPolicyTest.kt`):
  - `RT-5 the substitutions of a Spanish keyboard` (`:271`): cada sustitución, que «¡» no es «l», «º» y «ª» por NFKC, y que «æ» no se lee como una letra;
  - `RT-5 a known word in Spanish keyboard leet is refused as a new master` (`:284`): 8 maestras, cada una por encima de la estimación y rechazada solo por la palabra, y 2 contraseñas con esos símbolos que no son una palabra y pasan.

### RT-6 (R43): una sola lista de lo que no se dibuja

- **`core/Invisible.kt`**, nuevo:
  - `drawsNothing(cp)` (`:32-39`): Cf; todo el plano 14 (U+E0000–U+E0FFF: las etiquetas y los selectores de variante suplementarios U+E0100–U+E01EF); U+034F; los rellenos de Hangul U+115F, U+1160, U+3164 y U+FFA0; U+17B4 y U+17B5; U+180B–U+180F; U+2800; U+FE00–U+FE0F; U+FFF9–U+FFFB. Solo rangos fijos y la categoría Cf;
  - `canJoin` (`:42`) y `emojiSelectorShows` (`:51`, antes `canTakeEmojiSelector`), movidos aquí desde `crypto/FileCrypto.kt` sin cambiar su regla, con las constantes `ZERO_WIDTH_JOINER` y `EMOJI_SELECTOR` (`:11` y `:14`).
- **`plainReportText`** (`imports/ImportModel.kt:117-144`): quita todo lo de `drawsNothing`, en lugar de Cf más su lista propia (`TAG_CHARACTERS` e `INVISIBLE_NOT_FORMAT`, borradas). U+FE0F se queda donde lo deja un nombre de archivo: `emojiSelectorShows` con el último punto de código que se enseña delante (o un ZWJ quitado, `:129`, como en un nombre, donde el ZWJ sigue ahí cuando se miran los selectores) y el siguiente que se dibuja detrás (`nextForSelector`, `:146-159`). Así «❤️» y «1️⃣» se enseñan igual, y «Banco» + U+FE0F o U+FE0F + «Banco» no llevan el selector. U+FE0E se quita siempre, también detrás de un emoji, como ya hacía un nombre de archivo; el emoji se enseña con su aspecto por defecto. **Decisión:** antes `plainReportText` conservaba U+FE0E y U+FE0F en cualquier sitio, como marcas combinantes; ahora sigue la regla de los nombres.
- **`compareKey`** (`:161-194`): además, quita lo que quede de `drawsNothing` después del NFKC, es decir, el U+FE0F que se enseña detrás de un emoji o de una letra que no es ASCII («é», la tolerancia de la regla de los nombres), y vuelve a normalizar si quitó algo. Así «❤️ Banco» y «❤ Banco», o «Café» + U+FE0F y «Café», comparan igual: el selector no cambia ninguna letra.
- **`isHiddenInName`** (`crypto/FileCrypto.kt:160-179`): `drawsNothing` más la lista de las tarjetas y los no caracteres, salvo el ZWJ y U+FE0F, que siguen con sus reglas (`withoutLooseJoiners`, `withoutLooseSelectors`). Los nombres de archivo ganan U+2800 y U+180B–U+180D, U+180F; lo demás ya lo quitaban. Los límites de A16b (`MAX_FILE_NAME_CODE_POINTS`, `MAX_SAVED_NAME_BYTES`, `MAX_FILE_NAME_BYTES`) no cambian, y los nombres con emoji tampoco: siguen en verde `safeFileNameKeepsTheJoinerOnlyBetweenEmoji`, `safeFileNameDropsTheBlankAndInvisibleCharactersOfTheNameList` (con ❤️, ❤️‍🔥, 1️⃣, #️⃣ y *️⃣), `nothingTheCardNameListForbidsSurvivesSafeFileName` y los de `PkActionsTest`.
- **KDocs:** `plainReportText`, `compareKey`, `isHiddenInName`, `safeFileName` (`crypto/FileCrypto.kt:251-268`) y `sanitizeReceivedName` (`vault/PkActions.kt:190-203`).
- **Tests nuevos:**
  - `core/InvisibleTest.kt`: la lista de RT-6 y la de antes; lo que se ve no está en ella; **todo Unicode**, un punto de código cada vez: lo que no se dibuja no se enseña, no separa dos claves y no se queda en un nombre, y lo único que `plainReportText` quita es lo de la lista; y, para 12 × 4 vecinos, U+FE0F se queda en lo que se enseña exactamente donde se queda en un nombre;
  - `imports/ImportModelTest.kt`: `plainReportTextDropsWhatDrawsNothing` (`:85`) y `anEmojiKeepsItsSelectorWhereAFileNameKeepsIt` (`:99`);
  - `crypto/CryptoTest.kt:384-387`: U+2800 y U+180B–U+180F en la lista de los nombres;
  - `vault/PkActionsTest.unNombreRecibidoPierdeLoQueNoSeDibujaComoLoQueSeEnsena` (`:238`).

### RT-7 (R44): NFKC al comparar

- **`compareKey`** (`imports/ImportModel.kt:173-194`): NFKC en lugar de NFC. «ﬁ» y «fi», «Ⅱ» y «II», «Ｂanco», «𝐁anco» y «Banco», «²» y «2», «ｶﾞ» y «ガ», «㎏» y «kg» comparan igual. Solo la comparación: lo guardado y lo que se enseña no cambian (R9 y R10; el test lo fija). NFKC puede alargar un texto (un punto de código da hasta 18); solo cuesta memoria mientras vive la clave.
- **Tests nuevos:** `imports/ImportModelTest.compareKeyFoldsTheCompatibilityForms` (`:118`), con lo que NFKC no pliega y sigue aparte (la «а» cirílica, el guion U+2010, U+2019), y los casos de `MergePlanTest` (`:202-204`).

## Tasa de rechazo del Generador

Medida con un test desechable, que no está en el commit, con el método de `redteam-fixes-report.md` («Tasa de rechazo del Generador, medida de nuevo»): el algoritmo del Generador (4 posiciones forzadas y las demás entre los 89 caracteres), estratificado por el número `j` de letras de las posiciones libres (j de 0 a 8, porque con más de 10 letras no puede haber rechazo por palabra), un millón de muestras por estrato, ponderadas con la binomial (52/89), y `newMasterProblem` entero. Esta vez, con **las mismas muestras** (semillas fijas de `SplittableRandom`) antes y después del arreglo: «antes» en un *worktree* de `e31873d`, «después» en el árbol con RT-5.

| Generador (4 clases) | Antes de RT-5 | Después de RT-5 | Medida de RT-3 (otras muestras) |
|---|---|---|---|
| 12 caracteres | 1 de cada 3 210 | 1 de cada 3 210 | 1 de cada 3 136 |
| 16 caracteres | 1 de cada 55 062 | 1 de cada 55 062 | 1 de cada 54 293 |
| **20 caracteres (por defecto)** | 1 de cada 1 083 655 | **1 de cada 1 083 655** | 1 de cada 1 075 259 |

Las dos salidas son idénticas, estrato por estrato (`diff` sin diferencias): el Generador solo usa ASCII, y «¡», «€» y «ø» no están entre sus caracteres. Lo que cambia frente a la medida de RT-3 es solo el ruido de otras muestras. A 20 caracteres sigue en torno a 1 de cada 1,1 millones, muy lejos del límite de 1 de cada 100 000. El KDoc de `newMasterProblem` (`vault/MasterPolicy.kt:242-246`) lo dice.

## Puerta

Con el *wrapper* (R29), `export ANDROID_HOME=/opt/android-sdk` y `--max-workers=1`:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con 1 831 tests, 0 fallos y 0 omitidos; lint con 0 errores y 19 avisos, los mismos de antes;
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con 1 826 tests, 0 fallos y 0 omitidos;
- avisos `w:` del compilador: **0** en las dos. Las dos puertas se ejecutaron con `--rerun-tasks`, para que se compilara todo de nuevo: así salieron 9 avisos viejos de código de test que una compilación incremental no volvía a enseñar (abajo, en la revisión Ingeniero Móvil Senior), arreglados antes de la puerta final.

Frente a la puerta del segundo ataque (1 818 y 1 813, con 4 omitidos), hay 13 tests más en cada suite y ningún omitido.

Los tests del equipo rojo: `RedTeamImportTest` 12, `RedTeamCryptoTest` 8 y `RedTeamSecondAttackTest` 13, sin omitidos ni fallos, en las dos suites.

## Revisión experto-seguridad-cripto

- **Sin primitivas ni formatos nuevos:** los cuatro arreglos son comparación (`imports/MergePlan.kt:401-423`, `imports/ImportModel.kt:117-194`), presentación (`core/Invisible.kt:32-51`, `crypto/FileCrypto.kt:175-179`) y política (`vault/MasterPolicy.kt:91-102`). No tocan `javax.crypto` ni Bouncy Castle, ni lo que se escribe en `seguridad-vault`, `seguridad-backup`, `SG1:` o `.sgenc`: el nombre dentro de un `.sgenc` se sigue leyendo tal cual, y solo cambia el que se propone o se enseña (KDoc de `safeFileName`).
- **RT-4, modelo de amenaza:** un archivo importado hostil (decisión (c)). Con el esquema en la clave, «Reemplazar» no puede llevarse una entrada `https` a `http`, a `javascript:` o a `intent:` sin que la vista previa la enseñe como entrada nueva. El arreglo cierra también la lectura de `javascript:x@banco.es/` como `https://…@banco.es` (`comparedLink`, `:416-423`). Lo que queda, y es la regla de siempre: si **una** de las dos entradas no tiene URL, el título y el usuario bastan (`Matcher.find`, `:474-478`), así que «Reemplazar» puede poner una URL de cualquier esquema en una entrada de la bóveda que no tenía ninguna, y la fila solo dice su host. Clavero no abre ningún enlace (decisión (c)). Lo apunto para el controlador; R41 decide que la fila no lleve el esquema.
- **RT-5:** cierra la clase del informe sin abrir rechazos de más: el Generador da las mismas cifras con las mismas muestras, y las contraseñas al azar con «¡», «€» y «ø» pasan (`MasterPolicyTest.kt:284-293`). Sigue siendo una estimación (CRY-A01): la regla no garantiza que una maestra sea fuerte.
- **RT-6:** una sola lista (`core/Invisible.kt:32-39`) para mostrar, comparar y nombrar, con un test sobre todo Unicode que impide que vuelvan a separarse (`core/InvisibleTest.kt`). Las clases que cubre son las de Unicode TR #36 y UTS #39 para los invisibles por defecto, más los rellenos y el blanco de Braille. No cubre las letras parecidas de otras escrituras (R9 las acepta). El Inspector de QR (`tools/qr/Visible.kt`) tiene su propia lista, que marca en lugar de quitar y no conoce U+2800, U+17B4/5, U+180B–U+180F ni U+034F: no lo toco, porque no es de RT-6 y cambiaría lo que enseña el Inspector; lo apunto para el controlador.
- **RT-7:** NFKC solo en `compareKey`; `masterCore` ya lo usaba. Coste acotado: NFKC alarga como mucho 18 veces un punto de código, sobre títulos y usuarios con sus topes de importación, y una vez por entrada.
- **Sin logs con datos** ni secretos nuevos en memoria: nada de esto toca una contraseña salvo `leetLetter`, que trabaja sobre las copias que ya hacía `isKnownWordInLeet`.
- **Coste:** `plainReportText` y `compareKey` siguen en una pasada más NFKC; `nextForSelector` solo corre detrás de un U+FE0F y se para en el primer punto de código que se dibuja, así que el total sigue lineal. `compareKeyIsNotCutNeverThrowsAndIsCheap` (40 000 claves) sigue en verde.

## Revisión Ingeniero Móvil Senior

- **Kotlin puro**, sin Android: `core/Invisible.kt` lo compila también `jvm-check`, y todos los tests nuevos corren en las dos suites. No cambia ninguna pantalla de Compose ni ningún texto de la interfaz.
- **Visibilidad:** `drawsNothing`, `canJoin`, `emojiSelectorShows` y las dos constantes son `internal` (`core/Invisible.kt:11-51`); `urlKey` es `internal` y `comparedLink` `private` (`imports/MergePlan.kt:401` y `:416`). Ninguna API pública nueva.
- **Sin tablas que cambien con la versión:** `drawsNothing` son rangos fijos más Cf, como ya hacían `isHiddenInName` y `plainReportText`; en un Android con un ICU viejo, un Cf nuevo puede no ser Cf, igual que antes.
- **Rendimiento:** la vista previa calcula `compareKey` una vez por título y usuario en `Dispatchers.Default` (sin cambios en `ImportScreen.kt`); `plainReportText`, que corre en cada fila de las listas, no añade reservas por carácter.
- **Detalles arreglados en lo que toqué:** `TAG_CHARACTERS` e `INVISIBLE_NOT_FORMAT` (`imports/ImportModel.kt`) y la copia privada de `canJoin` y `canTakeEmojiSelector` (`crypto/FileCrypto.kt`) se borran en favor de la lista común; la cabecera de `RedTeamSecondAttackTest` decía que los `rtN_` llevaban `@Ignore`; el KDoc de `fixedRandom` (`MasterPolicyTest.kt:460-466`) tenía una línea demasiado larga.
- **Avisos viejos del compilador en tests** (salieron al compilar todo con `--rerun-tasks`; no son de este trabajo, pero la puerta pide 0): `core/RestoredFileMimeTest.kt:33-43`, seis `Locale(String)` obsoletos, ahora `Locale.forLanguageTag` (la misma lengua, así que el test prueba lo mismo); `tools/classic/ClassicTest.kt:168`, un `!!` de más; `tools/slip39/SecretShareRunsTest.kt:21`, un `lateinit` de más (ahora `val`); `vault/ContactsViewTest.kt:438`, un *cast* de más.

## Textos para el Abogado Digital (revisión final)

- **Ninguno cambia en la interfaz.** RT-4 se arregló en la comparación, no en la fila (R41): la línea del duplicado sigue diciendo «usuario · host». `MASTER_TOO_WEAK` es el mismo (RT-5).
- **Comportamiento que puede describirse en un texto publicado:**
  - la regla de la maestra nueva rechaza ahora también una palabra conocida escrita con «¡», «€» o «ø» (R42). Si la política, el README o la ficha describen la regla (R36), que lo digan igual que el código o no entren en ese detalle;
  - al importar, una entrada con `http://` ya no es duplicado de la misma con `https://`, y entra como nueva (R41); los títulos y usuarios con caracteres que no se ven o en formas de compatibilidad (ancho completo, ligaduras) se tratan como duplicado de los que se ven igual (R43 y R44). Si el README («Importar de otra app») describe cómo se buscan los duplicados, debe decir lo mismo.
- **Necesita prueba en el móvil** (para el controlador, la lista P-n del README): un duplicado con `http://` frente a una entrada `https://` sale como entrada nueva; una entrada «Banco» + U+2800 sale como duplicado de «Banco» y se ve igual que ella en la lista.

## No hecho

- No he tocado `progress.md`, `CLAUDE.md`, el README, la auditoría ni el traspaso (los actualiza el controlador).
- No he cambiado la lista del Inspector de QR (`tools/qr/Visible.kt`), ni la fila del duplicado cuando la entrada de la bóveda no tiene URL (arriba, en la revisión experto-seguridad-cripto): quedan para que el controlador decida.
