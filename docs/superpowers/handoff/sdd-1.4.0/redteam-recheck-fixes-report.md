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
   - `PkActionsTest.elNombreRecibidoQuitaLoMismoQueSafeFileName` (`:276`): tenía U+0600 entre lo que un nombre quita. Con R49 un nombre lo conserva (abajo, «Inquietudes»), así que lo cambio por U+1BCA0, U+2065 y U+FFF0, y añado que U+0600 y U+06DD se quedan (`:292-294`);
   - `crypto/CryptoTest.nothingTheCardNameListForbidsSurvivesSafeFileName` (`:433`): falló en la primera puerta (`AssertionError at CryptoTest.kt:446`), porque fijaba que **ningún** Cf salvo el ZWJ llega a un nombre. Con R49 los signos que preceden a una cifra sí llegan: el test los excluye de los Cf con una nota, y comprueba además que se quedan (`:433-451`). Es la misma línea de CIFRADO-2 de la inquietud 1.
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
- **Los signos que se ven** (`InvisibleTest.thePrependedConcatenationMarksAreShown`, `:51`, y `whatIsSeenIsNotInTheList`, `:31`): «Banco ؀1» se enseña tal cual, no compara igual que «Banco 1» y un nombre lo conserva.
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

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con **1 853 tests, 0 fallos y 0 omitidos** (1 831 + 22: 13 de `RedTeamRecheckTest`, 4 de `InvisibleTest`, 4 de `MergePlanTest` y 1 de `QrContentTest`); lint con **0 errores y 17 avisos**. Antes eran 19: los 17 de ahora son de las mismas clases de siempre (`GradleDependency` 7, `NewerVersionAvailable` 5, `AndroidGradlePluginVersion` 2, `OldTargetApi`, `ObsoleteSdkInt` y `DiscouragedApi`), todos en `gradle/libs.versions.toml`, `app/build.gradle.kts`, el *wrapper*, el manifiesto y `mipmap-anydpi-v26`, ninguno en un archivo de esta ronda. La diferencia está en las comprobaciones de versiones en línea, que dependen de lo que contesta la red en cada ejecución. La primera ejecución de esta puerta falló en `CryptoTest.nothingTheCardNameListForbidsSurvivesSafeFileName` (arriba, TDD punto 3); la segunda, con ese test puesto al día, es la que cuento.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con **1 848 tests, 0 fallos y 0 omitidos** (1 826 + 22).
- **Avisos del compilador:** `grep -c "^w:"` da 0 en las dos salidas.
- `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk`: **OK** (con su nota de siempre: `PreviewActivity` exportada, permitida solo porque el APK es de depuración).
- **Los tests de formatos con datos anteriores** (`crypto/CryptoTest`, `crypto/DialectTest`, `crypto/pk/FileMessageTest`, y los de `seguridad-vault` y `seguridad-backup`) están en las dos suites, en verde.

## Inquietudes

1. **Los nombres de archivo con un signo que precede a una cifra cambian.** R49 saca U+0600–U+0605, U+06DD, U+070F, U+0890, U+0891, U+08E2, U+110BD y U+110CD de `drawsNothing`, y `isHiddenInName` es `drawsNothing` más la lista de las tarjetas y los no caracteres (`crypto/FileCrypto.kt:176-180`), que no los tiene. Así que `safeFileName` y `sanitizeReceivedName` los **conservan** ahora: el nombre que se propone al guardar, el que se escribe dentro de un `.sgenc` nuevo y el de un archivo recibido. Es lo que pide el sentido inverso de R49 y lo que comprueba `rt9_` («todo lo que un nombre quita se quita de lo que se enseña»): si los nombres siguieran quitándolos, `rt9_` seguiría fallando con esos trece. **El formato no cambia:** la cabecera de `.sgenc`, sus longitudes y su lectura son las mismas (`CryptoTest`, `DialectTest`, `FileMessageTest`, en verde, también `anOldFileWithANameLongerThan120CodePointsStillDecrypts`), y un nombre ya guardado se sigue leyendo tal cual. Para todo nombre sin esos signos, el resultado es el mismo byte a byte: los demás puntos de código que gana `drawsNothing` (U+2065, U+FFF0–U+FFF8) ya los quitaba la lista de las tarjetas (`crypto/pk/Card.kt:243-244`). Es una línea de CIFRADO-2 (auditoría 1.3.0) que cambia: el controlador decide si se acepta o si prefiere que los nombres sigan quitándolos y `rt9_` los excluya.
2. **La lista es fija a Unicode 15.0.** Un punto de código que una versión futura de Unicode añada como Default_Ignorable o como Cf invisible no se quita hasta que se ponga en los datos. Es lo mismo que pasa con la lista de las tarjetas, y es el precio de que dos móviles con otra ICU digan lo mismo. Los rangos no han cambiado de 15.0 a 16.0.
3. **Prueba en el móvil:** las de RT-9 (U+2065 y U+FFF0 se ven como nada), RT-10 (el Inspector marca «Casa» + U+2800), RT-13 (el móvil dibuja «۝») y P-9 (la fila «ana · http://banco.es»), en el informe del ataque y en el README.

## No hecho

Nada de lo pedido queda sin hacer. No toqué la regla de la maestra (R50) ni ningún formato.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)) y un QR hostil. Nada toca primitivas, claves ni formatos: solo `javax.crypto` y Bouncy Castle siguen fuera del diff; ninguna dependencia, permiso, componente ni host nuevo. Lo guardado no cambia (R9 y R10): `shownUrl` y `withLowerScheme` solo leen.
- **RT-8 (R48):** el respaldo por título y usuario solo vale para una clave `hhttps://` (`imports/MergePlan.kt:513`). Repasé lo que da esa clave: un esquema `https` escrito de cualquier forma o un texto sin esquema (o «host:puerto»); un tabulador o un control delante del esquema dan otra clave, del lado seguro (`RedTeamRecheckTest.resistsSchemeAndAuthorityVariantsAroundTheRt4Fix`). CWE-451 (la interfaz no dice lo que hace una acción): con R45, la fila también lo dice.
- **R45 y R47:** la fila nunca enseña la información de usuario: con host solo escribe el host (`:449-450`), y sin host la tapa hasta la última «@» antes de «/?#» (`:451-456`). Tapa de más antes que de menos: con `\` la sigue tapando, y una «@» en la consulta no se toca porque no es información de usuario. El lote MN ya trata un enlace con usuario como secreto; esto lo extiende a la vista previa.
- **R49:** la base es Default_Ignorable_Code_Point (UAX #44, DerivedCoreProperties; UTS #39 la usa para lo que no se ve), la misma de `isForbiddenNameCodePoint` (`crypto/pk/Card.kt:237-250`). Los signos que preceden a una cifra tienen clase bidi AN, como las cifras arábigas, que un nombre ya admite: no abren una clase nueva de reordenación (las de `withoutBidiControls` siguen fuera, `crypto/FileCrypto.kt:307-308`).
- **R46:** en un inspector, marcar es lo correcto (enseña lo oculto, no lo limpia), y la lista es la misma que la de la bóveda (`tools/qr/Visible.kt:93`).
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
- **Sin texto, cambia lo que se ve** (R48 y R49): un `http://` frente a una entrada sin web ya no es repetido; los signos que preceden a una cifra se ven en las listas, en los nombres de archivo y en el Inspector; el Inspector marca más caracteres con «⟨U+XXXX⟩». `task-12-report.md`, punto 14.
- **El informe del ataque publicado** gana su sección de la comprobación (`docs/auditorias/2026-11-08-ataque-1.4.0.md:245-283`), con dominios de ejemplo y sin productos nuevos, y sus frases de que la fila no cambiaba están corregidas (`:217`, `:222`, `:243`). `task-12-report.md`, punto 15.
- **RT-11 (R50):** `MASTER_TOO_WEAK` y la regla no cambian; queda para el dueño en la 1.4.1 (`CLAUDE.md:158`, traspaso `:54`). `task-12-report.md`, punto 16.
- La política (`docs/privacidad.html`) y la ficha no describen la fila ni cómo se buscan los duplicados: no cambian.
