# Clavero 1.4.0: arreglos del repaso del equipo rojo (RT-8 a RT-13)

**Rama:** `ccr-d1e75160-p5f4wc`, sobre `be77431`. **Decisiones:** R45 a R51 (`progress.md:259-269`), con R41 a R44, R9, R10, R34, R36 y (c). **Hallazgos:** M-1 a M-4 de `redteam-second-fixes-review.md` y RT-8 a RT-13 de `redteam-second-recheck.md`. **Commit:** uno, «1.4.0: arreglos del repaso del equipo rojo (RT-8 a RT-13)» (el controlador apunta su SHA).

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`; las de los tests, a `app/src/test/java/app/seguridad/`. Las líneas son las del árbol de este commit. Sin subagentes: lo revisa el controlador después.

## TDD

1. **Los `rtN_` del repaso.** Quité los `@Ignore` de `rt8_`, `rt9_`, `rt10_`, `rt12_` y `rt13_` en `redteam/RedTeamRecheckTest.kt` (y el `import org.junit.Ignore`), quité `rt11_` con su KDoc (R50) y puse al día la cabecera de la clase (`:41-47`). Con `jvm-check` fallaron los cinco, con los mensajes del informe:
   - `rt8_`: `«javascript://banco.es/%0Aalert(1)» against [VaultEntry(id=v1 … url= …)]: a duplicate whose row «ana · banco.es» hides the scheme; after …`;
   - `rt9_`: `shown or kept apart: U+2065, U+FFF0, U+FFF1, … U+FFF8`;
   - `rt10_`: `unmarked: U+034F, U+17B4, U+17B5, U+180B, U+180C, U+180D, U+180F, U+2800, U+2065, U+206A, U+206F, U+FFF0, U+FFF9, U+FFFA, U+FFFB, U+1BCA0, U+1D173, U+E0080, U+E01F0`;
   - `rt12_`: `a duplicate, row «ana · data:text/plain;base64,sgvsbg8=»`;
   - `rt13_`: `dropped: U+0600, U+0601, U+0605, U+06DD, U+070F, U+08E2, U+110BD`.
2. **Los tests nuevos, antes del código.** Todos fallaron en la misma ejecución (o en la siguiente, la de `InvisibleTest`):
   - `imports/MergePlanTest.aWordAndAColonInFrontIsAScheme` (`:201`): `«ana:clave@banco.es» → «ana · ana:clave@banco.es»`;
   - `…theRowShowsASchemeThatIsNotHttpsAndNeverTheUserInfo` (`:216`): `expected:<ana · [http://]banco.es> but was:<ana · []banco.es>`;
   - `…aUrlWithoutAHostKeepsItsCaseAfterTheScheme` (`:237`): `expected:<…base64,[SGVsbG]8=> but was:<…base64,[sgvsbg]8=>`;
   - `…anEntryWithoutAUrlIsADuplicateOnlyOfHttpsOrNoUrl` (`:250`): `AssertionError: http://banco.es/`;
   - `core/InvisibleTest.theUnassignedDefaultIgnorablesDrawNothing` (`:40`): `U+2065`; `…thePrependedConcatenationMarksAreShown` (`:51`): `U+0600 expected:<Banco [؀]1> but was:<Banco []1>`; `…theListIsTheDefaultIgnorablesWithTheDecidedInvisibles` (`:66`): `expected:<[U+2065, U+FFF0, … U+FFF8]> but was:<[]>`; `…whatANameDropsIsDroppedFromWhatIsShownAndCompared` (`:90`): `dropped from names, shown or compared: U+2065, U+FFF0, … U+FFF8`; `…whatIsSeenIsNotInTheList` (`:31`): `U+0600`;
   - `tools/qr/QrContentTest.visibleTextMarksEverythingThatDrawsNothing` (`:388`): `unmarked: U+034F, U+0600, …, U+17B4, U+17B5, U+180B, …` (con la lista de antes; la de ahora ya no tiene los signos que preceden a una cifra);
   - `vault/PkActionsTest.elNombreRecibidoQuitaLoMismoQueSafeFileName` (`:276`, la aserción nueva de `:293`): `expected:<a[؀]b.txt> but was:<a[]b.txt>`.
3. **Tests de antes que cambian a propósito,** por las decisiones:
   - `MergePlanTest.aSchemeThatIsNotWebIsNotReadAsHttps` (`:183`) y `aDuplicateLoginShowsTheUserAndTheHostReplaceBringsIn` (`:513`): la fila de `http://banco.es/` pasa de «ana · banco.es» a «ana · http://banco.es» (R45), y la de `mailto:ana@banco.es`, a «ana · mailto:…@banco.es»;
   - `MergePlanTest.theHostAgreesWithTheQrReaderForEveryWebLink` (`:119`) y `theHostIsTheOneABrowserGoesTo`: sin host, `urlHost` da el texto sin bajar a minúsculas lo que va detrás del esquema (R51), así que el test espera `url.trim()` (sus URL ya llevan `https` en minúsculas); el `import java.util.Locale` sobraba y se quita;
   - `QrContentTest.visibleTextMarksTheOtherInvisibleCharacters` (`:404`): fijaba que U+2065, U+E00FF y U+E01F0 **no** se marcaban. Con R46 y R49 se marcan; su lista «justo fuera» pasa a U+00AC, U+00AE, U+FE10, U+2801 y tres signos que preceden a una cifra (U+0600, U+06DD, U+110BD), que se ven;
   - `PkActionsTest.elNombreRecibidoQuitaLoMismoQueSafeFileName` (`:276`) y `crypto/CryptoTest.nothingTheCardNameListForbidsSurvivesSafeFileName` (`:432`): en `db42d3e` los cambié porque un nombre conservaba los signos que preceden a una cifra. Con R52 (abajo, «Ronda 2») vuelven a ser los de `be77431`, sin tocar.
4. Con el código, todo en verde: los cinco `rtN_`, los tests nuevos y las clases de alrededor (`redteam.*`, `core.InvisibleTest`, `imports.*`, `tools.qr.*`, `vault.PkActionsTest`: 572 tests, 0 fallos, 0 omitidos). Después, la puerta entera (abajo).

## Cambios por hallazgo

### RT-8 y M-2 (R48): una entrada sin URL solo toma una URL https

- **`Matcher.find`** (`imports/MergePlan.kt:512-513`): `g.byHost[host] ?: g.hostless?.takeIf { host.startsWith(HTTPS_KEY) }`, con `HTTPS_KEY = "hhttps://"` (`:415`). Un elemento sin URL sigue usando `g.any`. Así, con «Banco» / «ana» sin web en la bóveda, `http://`, `javascript://`, `intent://`, `file://`, `ftp://`, `mailto:`, `ana:clave@banco.es` y una URL sin host (`https://`) entran como entradas nuevas, y «Reemplazar» no toca la entrada (`MergePlanTest.anEntryWithoutAUrlIsADuplicateOnlyOfHttpsOrNoUrl`, `:250`). Una URL https o sin esquema («banco.es») sigue siendo duplicado: la exportación trae la web que le faltaba a la bóveda (R34).
- **M-2:** `https://banco.es` y `http://banco.es` en el mismo archivo frente a la entrada sin web dan un duplicado (el https) y una entrada nueva (el http), no dos duplicados de la misma entrada (mismo test, últimas líneas). La regla vale igual dentro del archivo (`inFile`, `:89-98`): un `http` detrás de un elemento sin URL del mismo título y usuario ya no cuenta como repetido, sino como entrada nueva.
- **KDocs:** `planMerge` (`:68-85`) y `Matcher` (`:477-481`).

### R45 y R47 (M-1): la fila enseña el esquema que no es https y nunca la información de usuario

- **`shownUrl`** (`imports/MergePlan.kt:438-457`), nuevo, lo usa `duplicateSubline` (`:459-475`):
  - con host: el host solo si el esquema es https (o no hay, que se lee como https), y «esquema://host» si es otro: «ana · http://banco.es», «ana · javascript://banco.es», «ana · intent://banco.es», «ana · file://banco.es». El puerto, la ruta y la información de usuario no salen nunca, porque solo se escribe el host;
  - sin host: el texto tal como está escrito (sin espacios a los lados), con lo que va desde el esquema (y sus «//») hasta la última «@» antes de «/», «?» o «#» escrito como «…»: «ana · mailto:…@banco.es», «ana · ana:…@banco.es», «ana · MAILTO:…@banco.es», «ana · javascript:…@banco.es/». Sin «@», sale entero («data:text/plain;base64,SGVsbG8=», «https://»). Después pasa por `plainReportText`, como antes, así que un bidi o un invisible dentro de la información de usuario no la hace salir («mailto:a​na@banco.es» da «mailto:…@banco.es»).
- **R47:** «ana:clave@banco.es» se sigue leyendo con «ana» como esquema; lo fija `MergePlanTest.aWordAndAColonInFrontIsAScheme` (`:201`): `urlKey` da `tana:clave@banco.es`, no es duplicado de `https://banco.es/`, y ninguna de seis formas con «clave» la enseña en la fila. Lo dicen los KDoc de `urlHost` (`:374-388`), `urlKey` (`:396-405`) y `comparedLink` (`:420-428`).
- **Las frases falsas** de que la fila no cambiaba, corregidas:
  - el KDoc de `duplicateSubline` (antes `MergePlan.kt:429-430`, «The scheme is not shown»), ahora `:459-468`;
  - `docs/auditorias/2026-11-08-ataque-1.4.0.md:217` (antes `:215`), `:222` (antes `:220`) y `:243` (antes `:241`);
  - `docs/superpowers/handoff/sdd-1.4.0/redteam-report.md:368` y `:372`.

### RT-12 (R51): sin host, solo el esquema en minúsculas

- **`withLowerScheme`** (`imports/MergePlan.kt:417-418`), nuevo: el esquema de `schemeOf` y el resto como está. Lo usan `urlHost` (`:393`) y la clave «t» de `urlKey` (`:411`). Así dos `data:` con otro Base64 no son duplicado, y un `DATA:` con el mismo texto sí (`MergePlanTest.aUrlWithoutAHostKeepsItsCaseAfterTheScheme`, `:237`). Dos `mailto:` que solo cambian en mayúsculas detrás del esquema tampoco son duplicado: el coste que acepta R51.
- La fila enseña el texto tal como está escrito (`shownUrl`, arriba).

### RT-9, RT-13 y M-4 (R49): `drawsNothing` es la lista fija de Unicode

- **Versión de Unicode:** la del JDK 21 que compila y prueba la app (`jvm-check/build.gradle.kts:27`, `jvmToolchain(21)`; OpenJDK 21.0.12 en este contenedor) es **Unicode 15.0**. Descargué `DerivedCoreProperties.txt` de 15.0.0, 15.1.0 y 16.0.0 (`https://www.unicode.org/Public/<versión>/ucd/DerivedCoreProperties.txt`): los rangos de Default_Ignorable_Code_Point son **idénticos** en las tres (15.1 es la de la lista de las tarjetas, `crypto/pk/Card.kt:227`, y la de la ICU de Android 15; 16.0, la de Android 16). Los `Prepended_Concatenation_Mark` de `PropList.txt` son los mismos en 15.0 y 16.0.
- **`DEFAULT_IGNORABLE`** (`core/Invisible.kt:19-43`): los 17 rangos como datos, con los rangos contiguos unidos, y un comentario con la versión y la URL. Solo datos, no código. `isDefaultIgnorable` (`:45-58`) es una búsqueda binaria sobre los pares.
- **`drawsNothing`** (`core/Invisible.kt:60-78`): Default_Ignorable_Code_Point ∪ U+2800 ∪ U+FFF9–U+FFFB ∪ U+13430–U+1343F (los controles de formato jeroglíficos, los únicos Cf de Unicode 15.0 que no son ni Default_Ignorable ni signos que preceden a una cifra; ya se quitaban, como Cf). **Ya no usa `Character.getType`**, así que un móvil con otra ICU dice lo mismo. Comparado con la lista anterior en todo Unicode (`InvisibleTest.theListIsTheDefaultIgnorablesWithTheDecidedInvisibles`, `:66`), gana exactamente U+2065 y U+FFF0–U+FFF8 (RT-9) y pierde exactamente U+0600–U+0605, U+06DD, U+070F, U+0890, U+0891, U+08E2, U+110BD y U+110CD (RT-13); y todo Cf del JDK está en la lista o es uno de esos signos.
- **El otro sentido de R43** (`InvisibleTest.whatANameDropsIsDroppedFromWhatIsShownAndCompared`, `:90`): todo lo que un nombre de archivo quita, salvo lo que cada uno enseña a propósito (los controles y U+2028/U+2029 como espacio, el uso privado y los no caracteres), lo quitan también lo que se enseña y la clave, y está en `drawsNothing`. Recorre todo Unicode.
- **Los signos que se ven** (`InvisibleTest.thePrependedConcatenationMarksAreShown` y `whatIsSeenIsNotInTheList`): «Banco ؀1» se enseña tal cual y no compara igual que «Banco 1». Un nombre de archivo lo sigue quitando (R52, «Ronda 2»).
- **El ZWJ y U+FE0F** no cambian: `canJoin` y `emojiSelectorShows` están como estaban (`core/Invisible.kt:80-92`), y los dos siguen en `drawsNothing` (U+200B–U+200F y U+FE00–U+FE0F son Default_Ignorable). `InvisibleTest.whatIsShownWhatIsComparedAndTheNamesOfFilesAgree` (`:102`) y `anEmojiSelectorStaysInWhatIsShownExactlyWhereItStaysInAName` (`:131`) siguen en verde, y también `PkActionsTest.elNombreRecibidoSoloGuardaElUnidorEntreEmoji` y los de emoji de `RedTeamRecheckTest.resistsEmojiThatMustStillShow`.
- **KDocs:** la cabecera de `core/Invisible.kt` (`:3-9`), `drawsNothing` (`:60-76`, con los signos que preceden a una cifra fuera, que es lo que pedía M-4), `plainReportText` (`imports/ImportModel.kt:97-108`, antes `:97-98`), `compareKey` (`:163-174`) e `isHiddenInName` (`crypto/FileCrypto.kt:163-169`, antes `:163-164`, que decía que un nombre quitaba U+0600–0605).

### RT-10 (R46): el Inspector de QR marca todo lo de `drawsNothing`

- **`isHiddenCodePoint`** (`tools/qr/Visible.kt:88-99`): después de sus dos excepciones de los emoji (U+FE0E y U+FE0F tras un emoji o un símbolo, y el *keycap*), `drawsNothing(cp) -> true` (`:93`). Sustituye a las cinco líneas escritas a mano (U+200B–U+200D, U+2060–U+2064, U+FEFF, U+00AD, los rellenos de Hangul, U+180E, los selectores y U+E0000–U+E007F), que son parte de `drawsNothing`. Se quedan las reglas propias del Inspector: no marca `'\n'`, sí U+2028/U+2029, los controles ISO y el conjunto bidi de `withoutBidiControls`. **Marca, no quita** (R46). Ningún texto nuevo: las marcas son «⟨U+XXXX⟩».
- **Test de todo Unicode** (`tools/qr/QrContentTest.visibleTextMarksEverythingThatDrawsNothing`, `:388`): cada punto de código de `drawsNothing`, detrás de una «a», sale como una sola marca «⟨U+XXXX⟩», y `hasHiddenChars` y `hiddenMarks` lo cuentan. Los emoji siguen sin marca («Casa ❤️», «1️⃣», «#️⃣», «☎︎»). `RedTeamRecheckTest.resistsTheQrInspectorsOwnList` sigue en verde.
- **Efecto:** la señal `HIDDEN_CHARS` de un enlace (`tools/qr/LinkSignals.kt:103`, que usa `hasHiddenChars`) sale ahora en más hosts, que es la dirección buena. Los tests de `LinkSignalsTest` siguen en verde.
- **KDoc** de `visibleText` (`tools/qr/Visible.kt:6-22`).

### R50 (RT-11): a la lista del dueño para la 1.4.1

- `rt11_otherLeetAndLookAlikeLettersAreRefused` sale de `RedTeamRecheckTest`; sus once contraseñas siguen en `redteam-second-recheck.md`, RT-11, con una línea nueva que dice que la prueba pasó a la lista del dueño (`:116`).
- `CLAUDE.md`, «Pendientes, del dueño», punto 11 (`CLAUDE.md:158`), y el traspaso de la 1.4.0, «Para el dueño», punto 9 (`docs/superpowers/handoff/2026-11-08-clavero-1.4.0-traspaso.md:54`).
- **Ningún `@Ignore`** en las clases del equipo rojo: `git grep -n "@Ignore" -- app/src/test jvm-check` solo lo encuentra en dos comentarios de cabecera (`RedTeamImportTest.kt:44`, `RedTeamSecondAttackTest.kt:57`) y en el de `RedTeamRecheckTest.kt:43`, que dicen que se quitaron.

### Documentos

- `docs/auditorias/2026-11-08-ataque-1.4.0.md`: el estado de arriba (`:3-9`) y la sección nueva «Comprobación de los arreglos del segundo ataque (decisión (f))» (`:245-283`), con la tabla de RT-8 a RT-13 (severidad, MASVS y estado), R45 a R51, los arreglos y lo que necesita prueba en el móvil; y las tres frases corregidas (arriba).
- `README.md:35` («Importar de otra app») y `:348` (P-9): la fila enseña el esquema cuando no es https y nunca el usuario ni la contraseña de la web; con una entrada sin web, `https://` es repetido y `http://`, entrada nueva. `.maestro/README.md:70` repite P-9 y dice lo mismo.
- `docs/superpowers/handoff/sdd-1.4.0/task-12-report.md`: sección nueva «Puesta al día de la lista, repaso del equipo rojo (9-11-2026)», puntos 12 a 16 (`:188-199`).
- `ui/screens/ImportScreen.kt:663-665`: el KDoc de `DuplicateRow` dice lo mismo que `duplicateSubline` (sin cambio de código).

## Puerta

Con el *wrapper*, `ANDROID_HOME=/opt/android-sdk` y `--max-workers=1`:

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con **1 853 tests, 0 fallos y 0 omitidos** (1 831 + 22: 13 de `RedTeamRecheckTest`, 4 de `InvisibleTest`, 4 de `MergePlanTest` y 1 de `QrContentTest`); lint con **0 errores y 17 avisos**. Antes eran 19: los 17 de ahora son de las mismas clases de siempre (`GradleDependency` 7, `NewerVersionAvailable` 5, `AndroidGradlePluginVersion` 2, `OldTargetApi`, `ObsoleteSdkInt` y `DiscouragedApi`), todos en `gradle/libs.versions.toml`, `app/build.gradle.kts`, el *wrapper*, el manifiesto y `mipmap-anydpi-v26`, ninguno en un archivo de esta ronda. La diferencia está en las comprobaciones de versiones en línea, que dependen de lo que contesta la red en cada ejecución. La primera ejecución de esta puerta falló en `CryptoTest.nothingTheCardNameListForbidsSurvivesSafeFileName`, que fijaba la regla de los nombres de CIFRADO-2; la segunda, con ese test cambiado, es la de `db42d3e`. La de la ronda 2 está abajo.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con **1 848 tests, 0 fallos y 0 omitidos** (1 826 + 22).
- **Avisos del compilador:** `grep -c "^w:"` da 0 en las dos salidas.
- `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk`: **OK** (con su nota de siempre: `PreviewActivity` exportada, permitida solo porque el APK es de depuración).
- **Los tests de formatos con datos anteriores** (`crypto/CryptoTest`, `crypto/DialectTest`, `crypto/pk/FileMessageTest`, y los de `seguridad-vault` y `seguridad-backup`) están en las dos suites, en verde.

## Inquietudes

1. **Resuelta por R52** (abajo, «Ronda 2»): en `db42d3e` los nombres de archivo conservaban los signos que preceden a una cifra; ahora los siguen quitando, como dice CIFRADO-2, y todo nombre sale igual que con `be77431`.
2. **La lista es fija a Unicode 15.0.** Un punto de código que una versión futura de Unicode añada como Default_Ignorable o como Cf invisible no se quita hasta que se ponga en los datos. Es lo mismo que pasa con la lista de las tarjetas, y es el precio de que dos móviles con otra ICU digan lo mismo. Los rangos no han cambiado de 15.0 a 16.0.
3. **Prueba en el móvil:** las de RT-9 (U+2065 y U+FFF0 se ven como nada), RT-10 (el Inspector marca «Casa» + U+2800), RT-13 (el móvil dibuja «۝») y P-9 (la fila «ana · http://banco.es»), en el informe del ataque y en el README.

## Ronda 2 (R52)

**Decisión del controlador R52** (`progress.md`, al final): los nombres de archivo **siguen quitando** los signos que preceden a una cifra (U+0600–U+0605, U+06DD, U+070F, U+0890–U+0891, U+08E2, U+110BD, U+110CD), como dice CIFRADO-2: U+0600 y U+06DD abarcan y cambian las cifras que los siguen, y en un nombre se quita de más antes que de menos. Lo que se enseña (`plainReportText`) y lo que se compara (`compareKey`) los conservan, como en `db42d3e`.

- **TDD.** Sobre el código de `db42d3e`, volví a poner `PkActionsTest` y `CryptoTest` como estaban en `be77431` y cambié `InvisibleTest.thePrependedConcatenationMarksAreShown` para que un nombre los quite. Fallaron los tres:
  - `PkActionsTest.elNombreRecibidoQuitaLoMismoQueSafeFileName`: `cp=600 expected:<a[b].txt> but was:<a[؀b؀].txt>`;
  - `CryptoTest.nothingTheCardNameListForbidsSurvivesSafeFileName`: `survivors: 600, 601, 602, 603, 604, 605, 6dd, 70f, 890, 891, 8e2, 110bd, 110cd`;
  - `InvisibleTest.thePrependedConcatenationMarksAreShown`: `U+0600 expected:<a[]b> but was:<a[؀]b>`.
  Con el arreglo pasan. El test nuevo `InvisibleTest.theNamesDropWhatTheyDroppedBefore` lo comprobé igual: sin la línea nueva de `isHiddenInName` falla con `changed: U+0600, U+0601, U+0602, U+0603, U+0604, U+0605, U+06DD, U+070F, U+0890, U+0891, U+08E2, U+110BD, U+110CD`, y con ella pasa.
- **Una sola lista:** `VISIBLE_PREPENDED_MARKS` (`core/Invisible.kt`), con el comentario de su origen (Prepended_Concatenation_Mark de `PropList.txt`, Unicode 15.0, igual en 16.0). La usan `drawsNothing`, que la deja fuera (`&& cp !in VISIBLE_PREPENDED_MARKS`; los datos de Default_Ignorable ya no la tienen, así que solo protege contra un cambio futuro de los datos y solo se mira cuando lo demás da sí), e `isHiddenInName` (`crypto/FileCrypto.kt`, `cp in VISIBLE_PREPENDED_MARKS ||`). Los tests la usan también (`InvisibleTest`, `RedTeamRecheckTest`), y `InvisibleTest.theListOfPrependedMarksIsUnicodes` fija sus trece valores.
- **Todo nombre sale igual que antes:** `InvisibleTest.theNamesDropWhatTheyDroppedBefore` recorre todo Unicode y comprueba que `isHiddenInName` da lo mismo que la regla de `be77431` (Cf, el plano 14, los rangos fijos de RT-6, la lista de las tarjetas y los no caracteres, salvo el ZWJ y U+FE0F). Así `safeFileName` y `sanitizeReceivedName` dan el mismo nombre, byte a byte, que el padre de `db42d3e` (lo que gana `drawsNothing`, U+2065 y U+FFF0–U+FFF8, ya lo quitaba la lista de las tarjetas).
- **El sentido inverso de R49:** `InvisibleTest.whatANameDropsIsDroppedFromWhatIsShownAndCompared` y la segunda mitad de `rt9_` excluyen exactamente `VISIBLE_PREPENDED_MARKS`, y `thePrependedConcatenationMarksAreShown` comprueba que un nombre los quita (`isHiddenInName` y `safeFileName`).
- **KDocs:** `VISIBLE_PREPENDED_MARKS` y `drawsNothing` (`core/Invisible.kt`), `isHiddenInName` (`crypto/FileCrypto.kt`, un punto propio para esos signos, por CIFRADO-2) y `plainReportText` (`imports/ImportModel.kt`). Documentos: el informe del ataque (R52 en la lista de decisiones y la frase de RT-9 y RT-13) y `task-12-report.md`, punto 14.
- **Puerta de la ronda 2:** con el *wrapper*, `ANDROID_HOME=/opt/android-sdk` y `--max-workers=1`, las dos órdenes de arriba dan **BUILD SUCCESSFUL**: la app, **1 855 tests, 0 fallos y 0 omitidos** (1 853 + 2: `theListOfPrependedMarksIsUnicodes` y `theNamesDropWhatTheyDroppedBefore`), y `jvm-check`, **1 850, 0 fallos y 0 omitidos**; 0 avisos `w:` en las dos salidas; lint con 0 errores y 17 avisos (los mismos de la ronda 1); `scripts/check-manifest.sh` **OK**.

## Ronda 3 (R53, R54)

**Decisiones del controlador** (`progress.md:275-277`): **R53** (repaso, I-1): «palabra:cifras» solo es host y puerto si la palabra lleva un «.», es `localhost` o es una IPv6 entre corchetes; si no, la palabra es un esquema. **R54** (RT-14 de la tercera comprobación): una sola limpieza previa, la del navegador, para `parseLink`, `urlHost`, `urlKey`, la fila y la clave de texto de R51; después, el esquema se lee como en el *scheme state* del URL Standard. Con N-1, N-2 y N-3 del repaso (`redteam-second-fixes-rereview-1.md`). Es la tercera ronda sobre las mismas funciones, así que el arreglo va a la causa: la comparación leía el esquema **antes** de limpiar el texto como lo limpia el navegador, y `parseLink` lo hacía **después**.

**TDD.** Quité el `@Ignore` de `rt14_`, moví `ht\ttps://banco.es` y `\u0001https://banco.es` a la lista de duplicados de `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix` (`redteam/RedTeamRecheckTest.kt:86`) y escribí los tests nuevos de `imports/MergePlanTest.kt`. Sobre el código de `ce6b0f9` fallaron los seis:
- `rt14_aTabOrALeadingControlDoesNotHideTheScheme` (`RedTeamRecheckTest.kt:335`): `duplicates whose row hides what the browser opens: «ht\ttp:@banco.es» vault «https://banco.es/»: row «ana · banco.es»; «ht\ttp:@banco.es» vault «»: row «ana · banco.es»; «\u0001http:@banco.es» …`;
- `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix`: `«ht	tps://banco.es» should be a duplicate`;
- `aWordAndDigitsIsAHostAndPortOnlyWhenTheWordLooksLikeAHost` (`MergePlanTest.kt:277`): `expected:<[tjavascript:1/alert(1)]> but was:<[hhttps://javascript]>`;
- `withoutAHostTheRowHidesUpToTheLastAt` (`:295`): `ana:cl/ave@banco.es expected:<ana · ana:[…]@banco.es> but was:<ana · ana:[cl/ave]@banco.es>`;
- `theSchemeIsReadAfterTheBrowsersCleaning` (`:308`): `expected:<hhttp[]://banco.es> but was:<hhttp[s]://banco.es>`;
- `noCleanedSchemeButHttpsGivesAnHttpsKeyAndNoRowShowsTheUserInfo` (`:327`), el test de propiedad: `«ana:Zq.\#.@banco.es» → «ana:Zq.\#.@banco.es»`.

Con el arreglo pasan. Dos tests de antes cambiaron a propósito por R54: `MergePlanTest.theHostAgreesWithTheQrReaderForEveryWebLink` (`:121`), cuya URL sin host espera ahora el texto limpio como el navegador (`asBrowserReadsIt(url)`, antes `url.trim()`: sus URL llevan tabuladores), y `RedTeamRecheckTest.resistsTheRt12FixVariants` (`:407`), donde `\u0001data:…` pasa a la lista de duplicados, porque el navegador quita el control. Añadí `tools/qr/LinkSignalsTest.theBrowsersCleaningComesFirst` (`:393`), que fija la limpieza compartida.

**Cambios:**
- **`asBrowserReadsIt`** (`tools/qr/LinkSignals.kt:54-63`), nuevo y compartido: es la primera línea de `parseLink` sacada a una función, sin cambiar lo que hace `parseLink` (`:74`): quita todos los tabuladores, LF y CR, y recorta los controles C0 y los espacios de los extremos, como el *basic URL parser* del URL Standard (https://url.spec.whatwg.org/; leí la especificación y no copié código), y también los demás espacios de Unicode de los extremos, como ya hacía. Lo usan `urlHost` (`imports/MergePlan.kt:393`), `urlKey` (`:411`), y con ellos la clave de texto de R51 (`withLowerScheme`), y `shownUrl` (`:456`).
- **`schemeOf`** (`tools/qr/LinkSignals.kt:129-135`): su KDoc dice que lee como el *scheme state* del URL Standard (una letra ASCII, después letras, cifras, «+», «-» o «.», y «:»), con una diferencia a propósito: una sola letra no es esquema (la letra de una unidad, «C:\…»). El código ya era así.
- **R53** (`comparedLink`, `imports/MergePlan.kt:435-444`, la línea nueva es `:438-439`): «palabra:cifras» solo se lee como host y puerto si la palabra lleva «.» o es `localhost`; una IPv6 entre corchetes no tiene esquema para `schemeOf` y ya se leía como web. `javascript:1/alert(1)`, `data:1/x` e `intent:1/#…;end` dan claves «t», no son duplicado de una entrada sin web y la fila los enseña enteros («ana · javascript:1/alert(1)»). `banco.es:8443`, `LOCALHOST:8080` y `[::1]:8080` siguen dando `hhttps://…`. «intranet:8080» pasa a compararse entero (una entrada nueva, visible), el coste que dice el repaso.
- **N-1** (`shownUrl`, `:455-466`, la línea nueva es `:463`): sin host, tapa hasta la última «@» de **todo** el texto: `ana:cl/ave@banco.es`, `ana:cla?ve@banco.es` y `ana:clave#1@banco.es` dan «ana · ana:…@banco.es», y `mailto:a@b.es?cc=c@d.es` da «mailto:…@d.es». Con esto, «nunca el usuario ni la contraseña» de `README.md:35` y de P-9 es verdad.
- **El test de propiedad** (`MergePlanTest.kt:327`): 20 000 textos al azar hechos de esquemas (`https`, `http`, `HTTPS`, `javascript`, `data`, `intent`, `mailto`, `ana`, `banco.es`, `localhost`, `x`), «:», «//», «@», cifras, «.», tabuladores, LF, CR, U+0001, U+001F, espacios y «/». Comprueba que `urlKey`, `urlHost` y la fila no lanzan; que si el esquema del texto limpio (su propia limpieza, escrita en el test como dice el URL Standard) no es https ni parece un host, la clave nunca empieza por `hhttps://`; y que con una información de usuario centinela («:Zq…», delante de la última «@»), ninguna fila la enseña, tampoco con «/», «?», «#» o «\» dentro de ella en un enlace sin host.
- **KDocs:** `urlHost` (`:375-391`), `urlKey` (`:399-409`), `comparedLink` (`:424-434`), `shownUrl` (`:446-454`) y `duplicateSubline`.
- **Documentos:**
  - N-2: README P-27 (`README.md:366`), con las pruebas en el móvil de RT-6 (U+2800), RT-9 (U+2065 y U+FFF0), RT-10 (el Inspector con «Casa» + U+2800), RT-13 («Banco ۝1») y RT-14 (pegar `ht<TAB>tp:@banco.es` y `<U+0001>http:@banco.es` en Chrome y en el navegador de Honor), bajo la línea de marcas que ya había; su fila en `.maestro/README.md:88`, y «P-1 a P-27; las siete últimas» en `.maestro/README.md:3`. Las referencias «P-4 a P-26» de `CLAUDE.md:148` y del traspaso (`:45`) pasan a P-27. `docs/auditorias/2026-11-08-ataque-1.4.0.md:286` (antes `:284`) dice ahora «(en el README, P-9 y P-27)».
  - N-3: `task-12-report.md:197`, punto 14: «se ven ahora en las listas (en el Inspector de QR ya salían sin marca; …)»; y un punto 17 nuevo, con la fila de la ronda 3 y P-27.
  - El informe del ataque: el estado (`:9`) y la sección «Tercera comprobación (decisión (f))» (`:288`), con RT-14, R53, R54 y los arreglos.

**Puerta de la ronda 3:** con el *wrapper*, `ANDROID_HOME=/opt/android-sdk` y `--max-workers=1`, las dos órdenes de la puerta dan **BUILD SUCCESSFUL**: la app, **1 867 tests, 0 fallos y 0 omitidos** (1 855 + los 7 de la tercera comprobación, con `rt14_` ya sin `@Ignore`, + 5 nuevos: 4 de `MergePlanTest` y 1 de `LinkSignalsTest`), y `jvm-check`, **1 862, 0 fallos y 0 omitidos**; 0 avisos `w:` en las dos salidas; lint con 0 errores y 17 avisos (los mismos); `scripts/check-manifest.sh` **OK**. Ningún `@Ignore` en `app/src/test`.

**Revisión experto-seguridad-cripto (ronda 3):** la causa de RT-8, I-1 y RT-14 es un conflicto de interpretación entre el analizador y el navegador (CWE-436). Ahora la comparación y la fila leen el enlace en el mismo orden que el navegador (limpiar, y después leer el esquema), con una sola función (`tools/qr/LinkSignals.kt:62`), así que no pueden volver a separarse. R53 quita la otra vía a `hhttps://`: solo un texto con un host de verdad delante de «:cifras» se lee como web. La llave de R48 (`HTTPS_KEY`) solo sale ya de un esquema https real o de un texto sin esquema, y el test de propiedad lo fija. La fila con host solo escribe el host, y sin host tapa hasta la última «@» de todo el texto, así que tapa de más antes que de menos. Nada toca primitivas ni formatos, y lo guardado no cambia (R9 y R10): las funciones solo leen.

**Revisión Ingeniero Móvil Senior (ronda 3):** Kotlin puro (`tools/qr/`, `imports/`), probado en `jvm-check`; ninguna pantalla, estado ni concurrencia cambia. `asBrowserReadsIt` es una pasada lineal que antes ya hacía `parseLink`; ahora la hacen también `urlKey` y `urlHost` (una vez por URL al planear, en `Dispatchers.Default`), y `parseLink` la repite sobre el texto ya limpio, que no cambia. El test de propiedad tarda menos de un segundo.

**Textos para el Abogado Digital (ronda 3):** la fila cambia sin literal nuevo (`imports/MergePlan.kt:455-466`): «ana · javascript:1/alert(1)» en lugar de «ana · javascript»; una URL con un tabulador o un control en el esquema se enseña como la lee el navegador; y una contraseña con «/», «?» o «#» también se tapa. README P-27 (`README.md:366`) y `.maestro/README.md:88` son nuevos y nombran Chrome y el navegador de Honor, bajo la línea de marcas. Apuntado en `task-12-report.md`, punto 17.

## No hecho

Nada de lo pedido queda sin hacer. No toqué la regla de la maestra (R50) ni ningún formato.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)) y un QR hostil. Nada toca primitivas, claves ni formatos: solo `javax.crypto` y Bouncy Castle siguen fuera del diff; ninguna dependencia, permiso, componente ni host nuevo. Lo guardado no cambia (R9 y R10): `shownUrl` y `withLowerScheme` solo leen.
- **RT-8 (R48):** el respaldo por título y usuario solo vale para una clave `hhttps://` (`imports/MergePlan.kt:513`). Repasé lo que da esa clave: un esquema `https` escrito de cualquier forma o un texto sin esquema (o «host:puerto»); un tabulador o un control delante del esquema dan otra clave, y entraban como nuevas (`RedTeamRecheckTest.resistsSchemeAndAuthorityVariantsAroundTheRt4Fix`; la ronda 3, con R54, los lee como el navegador). CWE-451 (la interfaz no dice lo que hace una acción): con R45, la fila también lo dice.
- **R45 y R47:** la fila nunca enseña la información de usuario: con host solo escribe el host (`:449-450`), y sin host la tapa hasta la última «@» antes de «/?#» (`:451-456`). Tapa de más antes que de menos: con `\` la sigue tapando, y una «@» en la consulta no se toca porque no es información de usuario. El lote MN ya trata un enlace con usuario como secreto; esto lo extiende a la vista previa.
- **R49:** la base es Default_Ignorable_Code_Point (UAX #44, DerivedCoreProperties; UTS #39 la usa para lo que no se ve), la misma de `isForbiddenNameCodePoint` (`crypto/pk/Card.kt:237-250`). Los signos que preceden a una cifra se enseñan en las listas (tienen glifo) y un nombre los sigue quitando (R52): abarcan y cambian las cifras que los siguen, y en un nombre se quita de más antes que de menos.
- **R46:** en un inspector, marcar es lo correcto (enseña lo oculto, no lo quita), y la lista es la misma que la de la bóveda (`tools/qr/Visible.kt:93`).
- **Sin logs ni secretos:** los tests usan contraseñas inventadas («clave», «la-del-atacante») y no escriben nada fuera del árbol de tests.

## Revisión Ingeniero Móvil Senior

- **Kotlin puro e `internal`/`private`** (`core/Invisible.kt:25-78`, `imports/MergePlan.kt:415-457`), en `jvm-check` con el resto. Ninguna pantalla nueva ni estado nuevo; nada en `rememberSaveable` ni en un `Bundle`. `ImportScreen.kt` solo cambia un KDoc.
- **Coste:** `drawsNothing` corta en ASCII (`cp >= 0x00AD`, `core/Invisible.kt:78`) y si no hace una búsqueda binaria sobre 17 rangos (5 pasos como mucho), sin reservas. La lista de la bóveda y la vista previa llaman a `plainReportText` por fila, como antes. `shownUrl` es una lectura más de `parseLink` por duplicado, solo para las filas que se enseñan.
- **Igual en todos los móviles:** `drawsNothing` ya no depende de `Character.getType` (que en Android es la ICU del sistema, distinta entre Android 8 y 16), así que lo que se enseña, lo que se compara y los nombres son los mismos en todos.
- **Concurrencia:** sin cambios. `planMerge` sigue en `Dispatchers.Default` (`ui/screens/ImportScreen.kt`), y el Inspector, donde estaba.
- **Tests de todo Unicode:** cuatro recorren 0..0x10FFFF (`InvisibleTest:66`, `:90`, `:102`; `QrContentTest:388`) y tardan menos de un segundo cada uno en `jvm-check`.

## Textos para el Abogado Digital (revisión final)

- **La fila de un duplicado cambia** (R45, R47 y R51), sin literal nuevo en `imports/ImportTexts.kt` (`duplicateSubline` y `shownUrl`, `imports/MergePlan.kt:438-475`): «ana · http://banco.es» cuando el esquema no es https; una URL sin host, tal como está escrita; nunca la información de usuario, con «…» en su lugar («ana · mailto:…@banco.es»). Apuntado en `task-12-report.md`, punto 12.
- **README:** `README.md:35` y `:348` (P-9), y `.maestro/README.md:70`: la frase nueva «con el esquema delante cuando no es https … y nunca el usuario ni la contraseña que pueda llevar la web», y que con una entrada sin web un `http://` sale como entrada nueva. Apuntado en `task-12-report.md`, punto 13.
- **Sin texto, cambia lo que se ve** (R48 y R49): un `http://` frente a una entrada sin web ya no es repetido; los signos que preceden a una cifra se ven en las listas y no se marcan en el Inspector (los nombres de archivo los siguen quitando, R52); el Inspector marca más caracteres con «⟨U+XXXX⟩». `task-12-report.md`, punto 14.
- **El informe del ataque publicado** gana su sección de la comprobación (`docs/auditorias/2026-11-08-ataque-1.4.0.md:245-283`), con dominios de ejemplo y sin productos nuevos, y sus frases de que la fila no cambiaba están corregidas (`:217`, `:222`, `:243`). `task-12-report.md`, punto 15.
- **RT-11 (R50):** `MASTER_TOO_WEAK` y la regla no cambian; queda para el dueño en la 1.4.1 (`CLAUDE.md:158`, traspaso `:54`). `task-12-report.md`, punto 16.
- La política (`docs/privacidad.html`) y la ficha no describen la fila ni cómo se buscan los duplicados: no cambian.
