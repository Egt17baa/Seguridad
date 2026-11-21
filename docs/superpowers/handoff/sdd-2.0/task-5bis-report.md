# Tarea 5 bis: aspecto «Cristal», temas, paletas, «Personalizado» y contraste (informe del implementador)

**Estado:** DONE_WITH_CONCERNS (las dudas están abajo; ninguna bloquea).
**Rama:** `ccr-0aa27f95-u038cd`, base `51ac870`. **Commits:** `411eab2` (WIP: paletas, cristal y Aspecto) y `e53c4a5` «2.0, tarea 5 bis: aspecto Cristal, temas y logo». Autor y *committer* `Egt17baa <Egt17baa@users.noreply.github.com>`, sin subir.
**Pruebas:** app 2113 tests en verde (antes 2092, +21) y jvm-check 2087 en verde (antes 2081, +6); `assembleDebug`, `lintDebug` (sin avisos nuevos: los 21 que hay son de versiones de Gradle y de dependencias, `DiscouragedApi` del manifiesto y `ObsoleteSdkInt` de `mipmap-anydpi-v26`, todos de antes), `checkLicense`, `checkFragmentVersion` y `checkAssets` en verde.

El logo (`ic_launcher_*.xml`) y `docs/play/icono-512.png` no se han tocado.

## Qué hay hecho

### Ajustes (`settings/`, compilado también en jvm-check)
- `settings/Appearance.kt` (nuevo):
  - `enum class ThemeStyle { GLASS, SOLID }`, `enum class PaletteId { NIGHT, OCEAN, FOREST, SUNSET, GRAPHITE, SAND, WALLPAPER, CUSTOM }`, `enum class GlassLevel { LOW, MEDIUM, HIGH }`;
  - `data class CustomPalette(val accentHue: Int, val backgroundHue: Int, val glass: GlassLevel)`, tal cual el brief;
  - `CUSTOM_HUES = 12` y `saneCustom` (lleva los tonos a 0…11).
- `Settings` (`Settings.kt:59-72`): `style = GLASS`, `palette = NIGHT`, `custom = null`, `reduceTransparency = false` y `highContrast = false`, todos con valor por defecto. Así, un `settings.json` de la 1.4.3 se lee en «Noche» y «Cristal» y conserva su modo. También `appearanceNoticePending: Boolean? = null`, para R-C8.
- `SettingsStore`:
  - `sane()` normaliza los tonos (`SettingsStore.kt:137`);
  - `decodeSettings` reintenta sin la clave `custom` si esta no se puede leer (`:166`). Así, un «Personalizado» dañado no tira el tiempo de bloqueo ni los ajustes de cada bóveda. Los enums desconocidos ya volvían a su valor por defecto con `coerceInputValues`;
  - `decideRenameNotice` decide también `appearanceNoticePending`, cada aviso por su cuenta (`:56-70`). Antes la función volvía nada más ver decidido el aviso del nombre, y esa es la situación de todos los que actualizan desde la 1.3 o la 1.4.
- `RenameNotice.kt`: la lógica pasa a `open class OnceNotice`. `RenameNotice` conserva su API, y `AppearanceNotice` es la línea de R-C8. `AppContainer.appearanceNotice`.

### Colores (`ui/theme/Palettes.kt`, nuevo)
- `colorsFor(palette, custom, dark, highContrast, wallpaper = null): AppColors`. Devuelve el `ColorScheme`, los `StatusColors`, el `BackgroundSpec` (el degradado y tres `Blob` fijos), `cardAlpha`, `barAlpha`, `overlayAlpha`, `floorAlpha`, `highlightAlpha`, `border` y `borderWidth`. El quinto parámetro, con valor por defecto, lleva los tonos del fondo de pantalla, porque `colorsFor` no tiene `Context`.
- Cada paleta sale de unos pocos tonos (tono y saturación HSL), y la luminosidad se calcula y se corrige:
  1. **Fondo:** degradado y manchas. Las manchas se aclaran o se oscurecen hasta que todo el dibujo queda dentro del límite de las etiquetas. El punto más claro y el más oscuro se calculan canal a canal sobre todas las esquinas del dibujo, así que el cálculo es exacto, no una muestra (`bounds`, `Palettes.kt:387`).
  2. **Superficies opacas:** dentro de `TagPalette.DARK_MAX_BACKGROUND_LUMINANCE` = 0,070 y `LIGHT_MIN_BACKGROUND_LUMINANCE` = 0,70. Las etiquetas son fijas (ruling T4); sus límites exactos son 0,0729 y 0,681.
  3. **Textos** (`onSurface`, `onSurfaceVariant`, `primary`, `secondary`, `tertiary`, `error`, y positivo y precaución): se aclaran (tema oscuro) o se oscurecen (tema claro) hasta 4,5:1, o 7:1 con «Alto contraste», sobre todo lo que tienen debajo (`against`, `:262`): los extremos del fondo, las superficies y el brillo sobre los paneles. `outline` llega a 3:1, y cada pareja de contenedor (`onPrimary`/`primary`, `inverse*`, `surface`/negativo de los botones rojos…) llega a la meta.
  4. **Opacidad de cada panel:** sube en centésimas hasta que todo lo anterior se cumple sobre el panel mezclado con los extremos del fondo, con el brillo y sin él. El suelo `floorAlpha` (`:315`) es la opacidad de una barra, un diálogo o un menú que no difumina, y se calcula también sobre blanco y negro puros (R-C5). Se comprueba con el mismo `Float` que se dibuja.
- `fixContrast(custom)`: lleva los tonos a los 12 y, si con esa intensidad algún texto no llegara a 4,5:1 en claro o en oscuro, baja la intensidad del cristal (más opaco). Ver «Dudas», 1.
- Paletas: «Noche» (añil a violeta, con acento oro; la del logo), «Océano», «Bosque», «Atardecer», «Grafito» y «Arena», elegidas para Clavero (R-C9). «Personalizado» tiene 12 tonos de acento × 12 de fondo × 3 intensidades. «Colores del fondo de pantalla» toma el tono de `primary` y de `secondaryContainer` del color dinámico de Android (desde la API 31) y pasa por la misma corrección (R-C6). Sin esos colores (Android 8 a 11) o sin tonos, la paleta es «Noche».
- Hay una caché pequeña (64 entradas): cada cálculo tarda alrededor de 1 ms.

### Cristal (`ui/theme/Glass.kt`, nuevo)
- `Look` (`:63`): los colores, el estilo, `opaque` («Sólido», «Reducir transparencias» o «Alto contraste») y `blur`.
  - `lookFor(...)` (`:93`): `blur = !opaque && sdk >= Build.VERSION_CODES.S`;
  - `blurs(level)` (`:71`) nunca devuelve sí para `CARD`;
  - `alpha(level, blurNow)`;
  - en «Cristal», `scheme.background` es transparente (`:77`), así que todos los `Scaffold` y el `Surface` de `NoticeScreen` dejan ver el fondo sin tocarlos uno a uno.
- `GlassBackground` (`:127`): dibuja el degradado y las tres manchas, quietas (R-C7), y dice a los paneles dónde está en la pantalla (`LocalBackdrop`). Lo pone `ProvideLook`, en la raíz de la app, así que lo usan la pantalla de desbloqueo, la de crear la bóveda y todas las demás.
- `Modifier.glass(level, shape)` (`:184`): dibuja el color translúcido, el brillo arriba y el borde de 1 dp más claro (en las barras, una línea arriba y otra abajo).
  - Desde la API 31, una barra o un diálogo vuelve a dibujar el fondo de la app, difuminado y recortado al panel (`blurredBackdrop`, `@RequiresApi(S)`, `:222`, la única llamada en `:197`, detrás de `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`).
  - Sin difuminado (Android 8 a 11, o antes de saber dónde está el panel) usa la opacidad suelo.
- `GlassPanel(modifier, level, content)` (`:169`) y `enum class PanelLevel { CARD, BAR, OVERLAY }`.

### Componentes compartidos (`ui/theme/Components.kt`)
- `SectionCard` pasa a cristal (`CARD`, nunca difumina). En «Sólido» queda como en la 1.4.x, con el borde marcado si está «Alto contraste».
- Envoltorios nuevos con el mismo nombre que los de Material: `TopAppBar` (`:108`), `NavigationBar` (`:124`), `AlertDialog` (`:143`) y `DropdownMenu` (`:179`).
  - Las pantallas solo cambian el `import` (16 archivos para `AlertDialog`, 3 para `DropdownMenu`, 6 para `TopAppBar` y `AppNavHost` para `NavigationBar`).
  - Se mantiene el nombre para que ANDROID-3 (`\bAlertDialog\(`) siga viendo los 34 diálogos.
  - La llamada interna a Material también lleva `FilterObscuredTouches()` (`:158`, `:183`, `:197`).
  - Los menús son translúcidos con la opacidad suelo y no difuminan.
- `tagTextColor` (`VaultList.kt`) toma el modo oscuro del tema (`isDarkTheme`) y ya no de la luminancia de `surface`.

### Tema y pantallas
- `Theme.kt`: `SeguridadTheme(settings)` calcula el `Look` con `remember` y `sdk = Build.VERSION.SDK_INT` (`:62`). Lee el color dinámico solo con «Colores del fondo de pantalla» y desde la API 31 (`:83`). `ProvideLook(look)` lo usan la app y la vista previa. Fuera `DarkColors` y `LightColors`.
- `MainActivity`: `SeguridadTheme(settings)`.
- `res/values/colors.xml`: `ink` = `#FF141733`, el fondo oscuro de «Noche». Lo comprueba `PalettesTest.theWindowIsTheNightBackground`. Retocado el comentario de `themes.xml`.
- `ui/screens/AppearanceScreen.kt` (nuevo), en Ajustes → «Aspecto»:
  - la vista previa (una barra, una tarjeta con datos inventados, una etiqueta y un botón, que TalkBack lee como «Vista previa del aspecto elegido, con datos de ejemplo»);
  - Modo, Estilo, Paleta («Colores del fondo de pantalla» solo desde Android 12);
  - «Personalizado»: acento y fondo con 12 chips con su nombre y una muestra que TalkBack no lee, la intensidad y «Aplicar». Nada se guarda hasta «Aplicar», y se guarda ya pasado por `fixContrast`;
  - «Reducir transparencias»: con «Alto contraste» se ve encendido y no se puede apagar;
  - «Alto contraste».
- `AppearanceNoticeDialog`: la línea de R-C8.
- `SettingsScreen`: la sección «Tema» pasa a «Aspecto», con el resumen («Cristal · Noche · Oscuro») y el botón «Cambiar el aspecto». `Section`, `Hint` y `Choices` pasan a `internal` para usarlos aquí. La pantalla tiene un parámetro nuevo, `onAppearance`.
- `AppNavHost`: la ruta `settings/appearance` (`:390`). La línea de R-C8 solo sale con la bóveda abierta o sin bóveda, y nunca encima del aviso del nombre ni del aviso del paso a v4 (`upgradeDone`, `:227-230`).

### Valores del tema anterior
La 1.4.x **no tenía** colores de acento ni «Alto contraste» en el código: `Settings` solo guardaba `theme`. Los «6 colores de acento» estaban solo en el diseño, para la tarea 10, así que no hay ningún valor antiguo que convertir. Un ajuste de la 1.4.3 entra en «Noche» y «Cristal», con su modo (`oldSettingsGetNightAndGlass`).

## Tests y TDD

**RED** (antes de implementar), el 12-11-2026:
```
./gradlew :app:testDebugUnitTest --tests 'app.seguridad.ui.theme.PalettesTest' --no-daemon --max-workers=1
e: .../ui/theme/ThemeSurfaces.kt:134:72 Unresolved reference 'CustomPalette'.
e: .../ui/theme/ThemeSurfaces.kt:138:36 Unresolved reference 'WallpaperSeeds'.
e: .../vault/TagPaletteTest.kt:26:37 Unresolved reference 'LIGHT_MIN_BACKGROUND_LUMINANCE'.
...  Unresolved reference 'ThemeStyle'. Location: .../settings/SettingsTest.kt line 72
> Task :app:compileDebugUnitTestKotlin FAILED — BUILD FAILED
```
Ya con el código, el primer verde se quedó en un fallo real: `customIsCorrectedToContrast` dio `outline (no texto) sobre OVERLAY blur=false α=0.9 sobre negro detrás: 2.98`. La opacidad se comprobaba en `Double` y se dibujaba en `Float`, y un canal redondeaba al otro lado. Se arregló comprobando con el mismo `Float`. También `theWindowIsTheNightBackground` falló hasta poner `ink` = `#FF141733`.

**Mutación** (comprobado y revertido): quitar la corrección de los textos y el suelo de opacidad hace fallar 10 tests: `everyPaletteReadsInLightAndDark`, `customIsCorrectedToContrast`, `wallpaperColoursAreCorrectedToContrast`, `highContrastIsOpaque`, `barsWithoutBlurReadOverAnyListBehind`, `everyTagColourReadsOnEveryThemeSurface`, `everySurfaceIsInsideTheTagBound`…

**GREEN:**
```
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
BUILD SUCCESSFUL in 3m 44s   (app: 2113 tests, 0 fallos)
./gradlew -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL in 1m 28s   (jvm-check: 2087 tests, 0 fallos)
```

### Tests nuevos o ampliados
- **`ui/theme/ThemeSurfaces.kt`** (ayudante de test). Mide por su cuenta: la mezcla sRGB, los extremos del fondo canal a canal y la lista de superficies.
  - Las superficies son: el fondo (su punto más oscuro, el más claro y el fondo sólido); 10 superficies opacas; y cada nivel de panel, con y sin difuminado, sobre los extremos del fondo y, si no difumina, sobre blanco y negro puros, con el brillo y sin él.
  - Los textos se miden a 4,5:1 (7:1 en alto contraste), las parejas de contenedor a la misma meta, y `outline` y `primary` a 3:1.
- **`PalettesTest`:**
  - `everyPaletteReadsInLightAndDark`;
  - `customIsCorrectedToContrast` (los 432, en claro y en oscuro, con paneles translúcidos y opacos);
  - `aCustomOutOfRangeIsBroughtIntoTheTwelveTones`;
  - `wallpaperColoursAreCorrectedToContrast` (24 × 24 tonos × 4 saturaciones);
  - `withoutItsColoursAPaletteIsNight`;
  - `highContrastIsOpaque` (opacidad 1, sin brillo y 7:1);
  - `sixDifferentPalettes`, `moreGlassIsMoreSeeThrough` y `theWindowIsTheNightBackground`.
- **`GlassContrastTest`:** `reduceTransparencyMakesPanelsOpaque`, `highContrastTurnsReduceTransparencyOn`, `solidIsOpaqueAndFlat`, `blurOnlyFromApi31AndNeverOnRows`, `barsWithoutBlurReadOverAnyListBehind` (R-C5) y `glassPanelsAreTranslucent`.
- **`SettingsTest`:** `oldSettingsGetNightAndGlass`, `aStrangeAppearanceKeepsTheOtherSettings`, `theAppearanceIsSavedAndReadBack` y `theAppearanceNoticeIsForThoseWhoUpdated`.
- **`TagPaletteThemeTest`:** ampliado a las 6 paletas, los 432 personalizados y una muestra del fondo de pantalla, en claro y en oscuro, con y sin alto contraste, con paneles opacos y translucidos; más `everySurfaceIsInsideTheTagBound`.
- **`TagPaletteTest`** (jvm-check): ya no compara con las 4 superficies de la 1.4.x, sino que cada etiqueta llega a 4,5:1 en el límite de luminancia, y las superficies de la 1.4.x quedan dentro.
- **`PlatformSourcesTest`:**
  - `blurOnlyFromApi31`: solo `Glass.kt` usa `BlurEffect` o `RenderEffect`, y solo dentro de la función `@RequiresApi(S)`, a la que solo se llama detrás de la comprobación de versión; además, `lookFor` decide con la versión que recibe y `Theme.kt` le pasa la del móvil;
  - `theThemeStateHasNoSecret`.

## Archivos
- **Nuevos:**
  - `app/src/main/java/app/seguridad/settings/Appearance.kt`, `ui/theme/Palettes.kt`, `ui/theme/Glass.kt` y `ui/screens/AppearanceScreen.kt`;
  - tests: `ui/theme/{PalettesTest,GlassContrastTest,ThemeSurfaces}.kt`.
- **Cambiados:**
  - `settings/{Settings,SettingsStore,RenameNotice}.kt`, `AppContainer.kt`, `MainActivity.kt`;
  - `ui/theme/{Theme,Components}.kt`, `vault/TagPalette.kt`, `ui/AppNavHost.kt`, `ui/screens/SettingsScreen.kt`, `ui/screens/VaultList.kt`;
  - solo el `import`: `ClipboardHistoryNotice.kt`, `CodesScreen`, `ContactDetailScreen`, `ContactsScreen`, `EntryFields`, `EntryHistoryScreen`, `IdentityScreen`, `Pickers`, `QrInspectScreen`, `ToolsScreen`, `TrashScreen`, `VaultAttachments`, `VaultBackup`, `VaultEntryScreen`, `VaultOrganize`, `VaultPickerScreen`, `VaultScreen` y `VaultUpgrade`;
  - `res/values/{colors,themes}.xml`;
  - tests: `PlatformSourcesTest`, `SettingsTest`, `TagPaletteThemeTest` y `TagPaletteTest`.
- **Sin tocar:** el manifiesto, `build.gradle*` y `libs.versions.toml`. No hay dependencias, permisos ni componentes nuevos, y `FLAG_SECURE` (`secureWindow()`) sigue igual.
- `Palettes.kt` tiene unas 470 líneas: casi todo es la construcción de las paletas y la aritmética de color. Se podría partir en dos (la aritmética aparte) si el revisor lo prefiere.

## Autorrevisión
- **Lo que pide el brief:** las interfaces son literales. Los nombres de los tests son los del brief (`everyPaletteReadsInLightAndDark`, `customIsCorrectedToContrast`, `highContrastIsOpaque`, `reduceTransparencyMakesPanelsOpaque`, `oldSettingsGetNightAndGlass`, `blurOnlyFromApi31`) y `TagPaletteThemeTest` está ampliado. Se cumplen 4,5:1, 12 × 12 × 3, la API 31, O-C3 a O-C6 y R-C5 a R-C10, salvo lo apuntado en «Dudas».
- **Calidad:** el contraste lo mide un ayudante de test escrito aparte del corrector (O-C6). El dibujo está en `drawWithCache`, sin recomponer por la posición: las posiciones son estado que solo se lee al dibujar. El `Look` y los colores se calculan con `remember`.
- **YAGNI:** no hay hojas inferiores en la app. `OVERLAY` las cubre si llegan, pero no se ha hecho ningún envoltorio de `ModalBottomSheet`.
- **Salida limpia:** sin registros, sin `println` y sin el test temporal de volcado (lo borré).

## Dudas
1. **`fixContrast(custom): CustomPalette`.** La firma literal no puede llevar la luminosidad: `CustomPalette` solo tiene los tonos y la intensidad. La corrección de la luminosidad la hace `colorsFor`. `fixContrast` lleva los tonos a los 12 y baja la intensidad del cristal si algún texto no llega. Con los 432 de hoy no baja ninguno: el test comprueba que `fixContrast(c) == c` y que es idempotente. Así, lo que se guarda es siempre lo que se ve.
2. **«Sólido» no es exactamente la 1.4.x.** Es opaco y liso como entonces, pero con los colores de la paleta elegida («Grafito» es la más parecida; ninguna paleta copia los valores de la 1.4.x). Por eso la línea de R-C8 no dice «volver al de antes», que sería falso, sino «Aspecto nuevo. Para volver a uno sin transparencias: Ajustes → Aspecto → «Sólido».». Va al Abogado como cambio de su texto.
3. **«Las hojas».** La app no tiene hojas inferiores, así que el texto plegado dice «las barras y los diálogos difuminan el fondo de la app…» y no «las hojas» (veracidad). El código las cubre (`OVERLAY`) si llegan.
4. **El difuminado y la lista.** En Android 12 y después, la barra vuelve a dibujar el fondo de la app difuminado y opaco bajo su capa translúcida, así que lo que pasa por detrás no se ve (ni se difumina). En Android 8 a 11 se ve, con la opacidad suelo, que se lee sobre blanco y negro. En la práctica el contenido nunca pasa bajo las barras: el `Scaffold` lo coloca entre ellas (ver R-C7). Como el fondo ya es suave, en el móvil el difuminado se nota poco; es lo que pide el diseño, pero conviene verlo en la prueba del dueño.
5. **Las filas de la lista** (`VaultList.EntryRow`) no tienen relleno: están sobre el fondo, que es transparente del todo, y sus textos se miden sobre los extremos del fondo. Hacer de cada fila una `GlassPanel(CARD)` cambia el aspecto de la lista, y la lista es de la tarea 6. Las `Card` de `HealthScreen` y el arrastre de `VaultList` siguen opacos, con los colores de Material.
6. **R-C6, el contraste del sistema de Android 14** (`UiModeManager.getContrast`) **no se ha hecho**. Habría que escuchar sus cambios, y la app ya tiene «Alto contraste» y «Reducir transparencias». Propuesta: dejarlo para la tarea 10 (temas), o para la revisión final, si el dueño lo quiere.
7. **«Alto contraste»** lleva a 7:1 los textos de la app. Las 8 etiquetas de color son fijas (ruling T4) y siguen en 4,5:1 como mínimo, no en 7:1.
8. **Las manchas del tema claro** se ven poco (opacidad de 0,2 a 0,4), porque el límite de las etiquetas (luminancia de 0,70 o más) obliga a mantener muy claro el punto más oscuro del fondo. Para verlas más habría que oscurecer las etiquetas claras, que son de la tarea 4.
9. **El brillo al pulsar** es la onda de Material (`Card(onClick)`). No he añadido otro movimiento. Falta comprobar en el móvil que Android la quita cuando pide quitar las animaciones.
10. **La guía inicial** aún no existe en el código. Usará el mismo fondo, porque lo pone la raíz.

11. **El dibujo del cristal no se ha ejecutado, solo compilado.** No hay emulador en la nube, y los tests en la JVM miden los colores y las opacidades, no el dibujo. Hay que probar en el móvil:
    - en Android 12 o después, que el fondo difuminado de un diálogo quede alineado con el de detrás: la cuenta con `positionOnScreen` de `Glass.kt:197-228`, entre dos ventanas, no la toca ningún test;
    - el aspecto en Android 8 a 11 (sin difuminado, más opaco);
    - los menús (`DropdownMenu` translúcido con borde);
    - la paleta «Colores del fondo de pantalla» con un fondo de pantalla real;
    - las transiciones entre pantallas, ahora que todos los `Scaffold` son transparentes en «Cristal» (durante el fundido se ven las dos pantallas a la vez);
    - «Reducir transparencias», «Alto contraste» y «Sólido»;
    - la letra al 200 %;
    - TalkBack sobre la vista previa y los tonos.

## Revisión experto-seguridad-cripto
- **Modelo de amenaza:** el tema no toca secretos ni la bóveda. El atacante que importa es otra app o un `settings.json` manipulado (copia o *root*), y lo peor que puede conseguir es un aspecto raro o una lectura fallida.
  - Un tono fuera de rango se normaliza (`SettingsStore.kt:137`, `Appearance.kt:saneCustom`).
  - Un «Personalizado» ilegible se descarta solo, sin perder el tiempo de bloqueo ni los ajustes de cada bóveda (`SettingsStore.kt:166`; test `aStrangeAppearanceKeepsTheOtherSettings`).
  - `colorsFor` no lanza excepciones (vuelve a «Noche»).
- **Nada secreto en el estado del tema:** `PlatformSourcesTest.theThemeStateHasNoSecret`. Ni `Theme.kt`, ni `Palettes.kt`, ni `Glass.kt`, ni `AppearanceScreen.kt` importan `crypto`, `VaultStore`, `VaultEntry`, `VaultData`, `Secret` o `AppContainer`. Los campos del aspecto en `Settings.kt:59-72` son enums, booleanos y dos enteros. Lo único que va al estado guardado es el borrador de «Personalizado» (`AppearanceScreen.kt:136-139`), que no es secreto.
- **`FLAG_SECURE` intacto:** no se ha tocado `MainActivity.secureWindow()` ni `android/SecureWindow.kt`. Los diálogos siguen filtrando los toques tapados (`Components.kt:158`, `:183`, `:197`, y la llamada de cada pantalla), y ANDROID-3 pasa con sus 34 diálogos.
- **El difuminado vuelve a dibujar el fondo de la app** (`Glass.kt:222-229`), nunca el contenido de otra ventana: no se lee ni se copia nada de lo que hay debajo (ni el fondo de pantalla del sistema ni otras apps). No hay difuminado entre ventanas (`FLAG_BLUR_BEHIND`).
- **El color dinámico:** solo se leen los colores que Android ya calcula (`dynamicDarkColorScheme`, `Theme.kt:83`), nunca la imagen, y sin permisos (O-C5).
- **Sin cambios de red, permisos, dependencias, registros ni formatos.**
- Repasé el diff con la lista de `references/auditoria.md` (§1 y §4):
  - **deserialización:** `kotlinx.serialization`, sin tipos polimórficos;
  - **excepciones:** el reintento sin `custom` falla del lado seguro, porque solo quita el aspecto, y el fallo al guardar se dice en pantalla (`APPEARANCE_SAVE_FAILED`);
  - **registro:** ninguno;
  - **cadena de suministro:** sin dependencias nuevas;
  - no hay ReDoS, rutas, red, *intents* ni componentes exportados nuevos;
  - el único almacenamiento nuevo son los ajustes de aspecto, sin cifrar a propósito (R-C10).

## Revisión Ingeniero Móvil Senior
- **API 31:** `BlurEffect` solo en la función `@RequiresApi(S)` (`Glass.kt:222`), con su única llamada detrás de `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (`Glass.kt:197`); `lookFor` decide `blur` con la versión (`Glass.kt:93`). Lint no da `NewApi`. El color dinámico va detrás de su comprobación con retorno temprano (`Theme.kt:79-83`).
- **Rendimiento:**
  - el `Look` se calcula con `remember` sobre los ajustes (`Theme.kt:59`) y los colores tienen caché (`Palettes.kt`, `cached`);
  - los paneles dibujan en `drawWithCache` (`Glass.kt:192`) y guardan su posición en un estado que solo se lee al dibujar, así que se repinta sin recomponer;
  - la capa difuminada se graba de nuevo solo si el panel cambia de tamaño o de sitio;
  - las filas de la lista no difuminan (`Glass.kt:71`), así que el desplazamiento no paga el difuminado.
- **Arquitectura:**
  - las pantallas no cambian una a una: toman el cristal de los componentes compartidos (`SectionCard`, `TopAppBar`, `NavigationBar`, `AlertDialog` y `DropdownMenu` en `Components.kt`) y del fondo transparente del esquema (`Glass.kt:77`);
  - el ajuste se guarda con `SettingsStore.update`, fuera del hilo principal como los demás ajustes;
  - el borrador de «Personalizado» es estado de la pantalla, no secreto.
- **Foco (R-C7):** el `Scaffold` coloca el contenido entre las barras (su `padding`), así que nada con el foco queda debajo de una barra. Las manchas son fijas (`drawAppBackground`, `Glass.kt:144`): no hay animaciones nuevas.
- **TalkBack:** los tonos llevan su nombre y la muestra no se lee (`AppearanceScreen.kt`, `Tones`); la vista previa se lee como una sola cosa; los interruptores tienen `Role.Switch`.
- **Ciclo de vida:** el aviso de R-C8 vive en `AppContainer` y sobrevive a un giro, como el del nombre; se marca en disco solo al pulsar «Entendido» (`OnceNotice.closed`).
- **Riesgos para la prueba en el móvil:**
  - la ventana del diálogo con fondo transparente y el borde (Material pone su atenuación detrás);
  - el aspecto del difuminado (Dudas, 4);
  - el arranque en «Noche» oscuro (`ink`) en los modos claros: hay un destello oscuro antes de que dibuje Compose, como pasaba en la 1.4.x con el tema claro.

## Textos para el Abogado Digital (revisión final)
Todos son nuevos y visibles para el usuario, en `ui/screens/AppearanceScreen.kt` y `ui/theme/Palettes.kt`:
- **Ajustes:** el título de la sección «Aspecto», el botón «Cambiar el aspecto» y el resumen «‹estilo› · ‹paleta› · ‹modo›» (p. ej. «Cristal · Noche · Oscuro»).
- **Secciones:** «Modo» (opciones «Oscuro», «Claro», «Como el sistema», las de antes), «Estilo» y «Paleta».
- **Estilos:** «Cristal» y «Sólido».
  - «Cristal: paneles translúcidos sobre un fondo de color.»
  - «Sólido: paneles opacos sobre un fondo liso, sin transparencias.»
  - Plegado: «Desde Android 12, las barras y los diálogos difuminan el fondo de la app que tienen detrás. En Android 8 a 11 son más opacos. «Reducir transparencias» los hace opacos.» Cambia dos cosas de su texto: quita «las hojas» (ver «Dudas», 3) y dice «el fondo de la app que tienen detrás», no «lo que tienen detrás», porque el panel vuelve a dibujar difuminado el fondo de la app y no lo que pasa por debajo (ver «Dudas», 4; es la frase del diseño).
- **Paletas:** «Noche», «Océano», «Bosque», «Atardecer», «Grafito», «Arena», «Colores del fondo de pantalla» y «Personalizado».
  - «Usa los colores que Android calcula del fondo de pantalla (desde Android 12).»
  - Plegado: «La app no lee la imagen del fondo de pantalla ni pide permisos. Ajusta los tonos para que el texto tenga contraste suficiente.» (O-C5 y O-C6)
  - En Android 8 a 11: «Colores del fondo de pantalla», desde Android 12.
- **«Personalizado»:**
  - «Elige el acento, el fondo y la intensidad del cristal, y pulsa «Aplicar».»
  - Plegado: «La app ajusta los tonos para que el texto tenga contraste suficiente. La vista previa enseña el cambio antes de aplicarlo.»
  - «Acento», «Fondo», «Intensidad del cristal», «Baja», «Media», «Alta» y «Aplicar».
  - Los 12 tonos: «Rojo», «Naranja», «Amarillo», «Lima», «Verde», «Esmeralda», «Cian», «Celeste», «Azul», «Violeta», «Magenta» y «Rosa».
- **Vista previa:** «Vista previa». TalkBack: «Vista previa del aspecto elegido, con datos de ejemplo». Datos inventados: «Bóveda», «Ejemplo», «usuario@ejemplo.com», «#trabajo» y «Botón».
- **Transparencias y contraste:**
  - «Reducir transparencias»: «Paneles opacos y sin difuminar.»
  - «Alto contraste»: «Textos con más contraste y bordes marcados. Enciende «Reducir transparencias».»
- **Error:** «No se pudo guardar el ajuste.»
- **R-C8**, una vez tras actualizar: «Aspecto nuevo. Para volver a uno sin transparencias: Ajustes → Aspecto → «Sólido».» con «Entendido». Es un cambio sobre su redacción («al de antes»), porque «Sólido» conserva los colores de la paleta (ver «Dudas», 2).
- **R-C10** (no he tocado `docs/privacidad.html`): la línea de la política: «Los ajustes de aspecto (modo, estilo, paleta y tonos personalizados) se guardan en el móvil, en el almacenamiento privado de la app, sin cifrar: no son secretos ni salen del móvil. El logo y los temas no añaden permisos.»
  - Con «Colores del fondo de pantalla», la frase de O-C5.
  - Ojo: el código guarda también «Reducir transparencias», «Alto contraste» y la marca de haber visto la línea de R-C8. Convendría decir «los ajustes de aspecto (modo, estilo, paleta, tonos personalizados, transparencias y contraste)».
- **Comprobado:** ningún texto dice «accesible», «cumple WCAG», «garantizado», «Liquid Glass», «cristal líquido», «vidrio líquido», «al estilo de», ™ ni ®, y no aparece «Clavero Cristal». `SourceTextsTest` pasa.

---

# Ronda 1 de arreglos (13-11-2026)

**Commit:** `43aa2c4` «2.0, tarea 5 bis: ronda 1 de arreglos». Autor y *committer* `Egt17baa <Egt17baa@users.noreply.github.com>`, sin subir. Partí de `task-5bis-review.md`, que leí entero; se arreglan los 7 menores más M-8.

## Qué cambia
- **M-1** (`ui/theme/Palettes.kt`):
  - `colorsFor` pasa por `builtFor`: si la paleta pedida no llegó a todas sus metas (`Built.met`), se dibuja «Noche». Lo aplico a todas las paletas, no solo a «Colores del fondo de pantalla» y «Personalizado», porque es lo más correcto: lo que se dibuja siempre se lee, venga de donde venga, y los tests prueban que hoy no le pasa a ninguna (ver abajo).
  - `opacity()` pone `met = false` si llega a 1,0 sin leerse, contando el brillo.
  - `internal fun paletteMeets(...)` para los tests.
- **M-2** (`Glass.kt`): el brillo acaba en `Color.White.copy(alpha = 0f)`, no en `Color.Transparent`. Así se dibuja lo que mide `ThemeSurfaces.kt`.
- **M-3** (`AppearanceScreen.kt`, `SettingsScreen.kt`, `Components.kt`):
  - el fallo al guardar sale en un *snackbar*, el mismo mecanismo que Ajustes, y se ve en cualquier punto de la pantalla;
  - el texto es una sola constante, `SETTING_SAVE_FAILED` = «No se pudo guardar el ajuste» (sin punto), que usan las 4 llamadas de Ajustes y «Aspecto»;
  - `BackScaffold` acepta un `snackbarHost` opcional;
  - «Aplicar» cierra el editor solo cuando `store.update` termina bien (`change(onSaved = { editing = false })`); si falla, el borrador sigue a la vista.
- **M-4:** la sección de los dos interruptores se titula «Transparencias y contraste» (`TRANSPARENCY_AND_CONTRAST`).
- **M-5:**
  - `SettingsStore.sane()`: `CUSTOM` sin tonos pasa a `NIGHT`, tanto al leer como al guardar;
  - `shownPalette(palette, custom, sdk)` (`Palettes.kt`): «Colores del fondo de pantalla» en Android 8 a 11 y «Personalizado» sin tonos se muestran como «Noche»;
  - la pantalla y el resumen de Ajustes (`appearanceSummary`) marcan y nombran lo que se dibuja, así que en Android 8 a 11 siempre hay una opción marcada.
- **M-6** (`TagPaletteThemeTest.everySurfaceIsInsideTheTagBound`): el límite de luminancia de las etiquetas se comprueba en las 6 paletas, las 432 de «Personalizado» y las 2304 semillas del fondo de pantalla, en claro y en oscuro, con y sin alto contraste, y con paneles opacos y translúcidos.
- **M-7** (`TagPaletteTest.contrastFormula`): referencias intermedias con tolerancia 0,01:
  - `#767676` sobre blanco da 4,54, y llega a 4,5;
  - `#777777` sobre blanco da 4,48, y no llega;
  - `#595959` da 7,00;
  - `#949494` da 3,03;
  - azul puro sobre blanco, 8,59;
  - rojo puro, 4,00 sobre blanco y 5,25 sobre negro;
  - verde puro sobre blanco, 1,37.

  Calculé los valores aparte, en Python, con la fórmula de WCAG.
- **M-8** (`Glass.kt`, `Components.kt`): `panelRipple(animationsOn)` devuelve la onda de Material, o `null` si Android quita las animaciones. `SectionCard` con `onClick` la pone con `LocalRippleConfiguration provides panelRipple(ValueAnimator.areAnimatorsEnabled())`, y `areAnimatorsEnabled()` es falso con la escala de animación a 0.
- **De paso:** el KDoc de `Look.scheme` explica por qué `background` debe ser el único color transparente (`contentColorFor`), como pidió la revisión del Ingeniero.

## Tests nuevos o ampliados
- `PalettesTest.wallpaperColoursFromAnyColourRead`: comprueba `wallpaperSeeds()` con 13 colores extremos, todos contra todos (blanco, negro, grises, los primarios y secundarios puros, casi grises, casi negro, casi blanco), y con 400 parejas al azar con la semilla fija 20261112, fuera de la rejilla. Para cada una, en claro y en oscuro y con y sin alto contraste, se cumplen `paletteMeets` y `failures(...)` está vacío. Comprueba también la saturación de un gris (0) y el tono del azul (240°).
- `PalettesTest.everyPaletteMeetsItsTargetsItself`: las paletas con nombre, las 432 y todo el barrido cumplen sus metas solas, así que la vuelta a «Noche» no tapa hoy ninguna paleta.
- `PalettesTest.theShownPaletteIsTheDrawnOne` (M-5).
- `SettingsTest.aCustomWithoutTonesIsNight`; `aStrangeAppearanceKeepsTheOtherSettings` comprueba además que la paleta queda en `NIGHT`.
- `GlassContrastTest.pressGlowOnlyWithAnimations` (M-8).
- `PlatformSourcesTest`:
  - `the press glow of a panel follows Android's animations setting`: `SectionCard` lee el ajuste del sistema y envuelve la `Card` pulsable;
  - `the highlight of a panel fades white` (M-2).
- `TagPaletteThemeTest.everySurfaceIsInsideTheTagBound` (M-6) y `TagPaletteTest.contrastFormula` (M-7), ampliados.

## Ejecución
Primero los que cubren lo cambiado:
```
./gradlew :app:testDebugUnitTest --tests 'app.seguridad.ui.*' --tests 'app.seguridad.settings.*' --tests 'app.seguridad.vault.TagPaletteTest' --tests 'app.seguridad.PlatformSourcesTest' --tests 'app.seguridad.tools.SourceTextsTest' --no-daemon --max-workers=1
BUILD SUCCESSFUL in 1m 1s   (132 tests; PalettesTest 20 s, TagPaletteThemeTest 17 s)
```
Después las dos suites completas:
```
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
BUILD SUCCESSFUL in 4m 27s   (app: 2120 tests, 0 fallos; lint: los mismos avisos que antes de la ronda, ninguno nuevo)
./gradlew -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL in 1m 44s   (jvm-check: 2090 tests, 0 fallos)
```
Errores de compilación por el camino, ya corregidos:
- la lambda final de `change` se asociaba a `onSaved`: ahora `onSaved` va primero;
- faltaba `@OptIn(ExperimentalMaterial3Api)` en el test de `RippleConfiguration`.

## Lo que queda
- La revisión sugería apuntar el corrector a 4,6 y medir a 4,5, para dejar un margen frente al redondeo de la GPU. No estaba entre los arreglos pedidos y lo dejo para que lo decida el controlador: cambia todos los colores un poco.
- `HealthScreen` pasará a `SectionCard` cuando se toque esa pantalla.
- Lo del móvil sigue igual («Dudas», 11). Hay que añadir: que no haya onda al pulsar una tarjeta con «Quitar animaciones», y el *snackbar* de «Aspecto».

## Textos para el Abogado Digital (ronda 1)
- **Nuevo:** el título de la sección «Transparencias y contraste».
- **Cambiado:** el error al guardar de «Aspecto» pasa a ser el mismo de Ajustes, «No se pudo guardar el ajuste» (sin punto, en un *snackbar*). Desaparece «No se pudo guardar el ajuste.».
- Sin más textos nuevos. Siguen en pie los avisos de la revisión: «contraste suficiente», «Intensidad del cristal» y la nota de que «Alto contraste» deja las etiquetas en 4,5:1.

---

# Ronda 2 de arreglos (13-11-2026)

**Commit:** `ff8580e` «2.0, tarea 5 bis: ronda 2 de arreglos». Autor y *committer* `Egt17baa <Egt17baa@users.noreply.github.com>`, sin subir. Arregla los tres menores de `task-5bis-rereview-1.md`.

## Qué cambia
- **N-1** (`Glass.kt`, `Theme.kt`, `Components.kt`): el ajuste «Quitar animaciones» ya no se lee en cada recomposición.
  - `animationsOnSetting()` lo guarda como estado: lo lee al arrancar y otra vez con `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)`, porque cambiarlo exige salir a los Ajustes de Android.
  - `SeguridadTheme` lo da a toda la app con `LocalAnimationsOn`.
  - `SectionCard` usa `panelRipple(LocalAnimationsOn.current)` y no llama a `ValueAnimator`.
- **N-2** (`Glass.kt`, `Components.kt`): sin animaciones, un panel pulsado se marca sin movimiento: un borde de 2 dp en el color de acento mientras está pulsado (`staticPressMark`, `Modifier.pressSignal`, con el `interactionSource` de la propia `Card`), en «Cristal» y en «Sólido».
  - Elegí el borde y no el cambio de fondo que se ponía de ejemplo. Un velo sobre el panel acerca el fondo al color del texto y baja el contraste medido. El borde no toca el texto, y el acento ya llega a 3:1 en todas las superficies, como elemento que no es texto.
  - Con las animaciones puestas, la señal sigue siendo la onda de Material.
- **N-3** (`HealthScreen.kt`):
  - las dos `Card` de Material pasan a `SectionCard`: la del resumen y `HealthRow`, que se puede pulsar. Así toman el cristal y siguen la misma regla al pulsarse;
  - ya no se usa `Modifier.clickable` sobre una `Card`;
  - las otras tarjetas que se pueden pulsar ya eran `SectionCard(onClick)`: `ToolsScreen.kt:77` y `ContactsScreen.kt:205`.

**Búsqueda hecha con grep** (`Card(onClick`, `Card(…clickable`, `Surface(onClick`, `ElevatedCard`, `OutlinedCard`, en `app/src/main/java`): la única superficie pulsable que queda es la `Card` de `SectionCard`, en `Components.kt`. Un test lo fija. La `Card` de arrastre de `VaultList` no se pulsa. Las filas de la lista no son paneles, son `Row` con `clickable` (tarea 6), y conservan la onda de Material.

## Tests
- `GlassContrastTest.aPressedPanelMarksItselfWithoutAnimations`:
  - la marca sale solo pulsado y con las animaciones quitadas;
  - el acento de la marca llega a 3:1 en todas las superficies, en las 6 paletas y en un «Personalizado», en claro y en oscuro, y en «Cristal» y en «Sólido».
- `PlatformSourcesTest` («the press glow of a panel follows Android's animations setting»), ampliado:
  - `SectionCard` usa `panelRipple(animationsOn)`, `pressSignal(...)` y su `interactionSource`, y no llama a `areAnimatorsEnabled`;
  - `Glass.kt` vuelve a leerlo en `ON_RESUME`, y `Theme.kt` da `LocalAnimationsOn`;
  - en `app/src/main/java` no hay otra superficie pulsable que la de `Components.kt`. Con el `Card(…clickable)` que tenía antes `HealthScreen`, el test fallaría.

Primero los que cubren lo cambiado:
```
./gradlew :app:testDebugUnitTest --tests 'app.seguridad.ui.theme.*' --tests 'app.seguridad.PlatformSourcesTest' --tests 'app.seguridad.tools.SourceTextsTest' --no-daemon --max-workers=1
BUILD SUCCESSFUL in 42s   (69 tests)
```
Después las dos suites completas:
```
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
BUILD SUCCESSFUL in 4m 8s   (app: 2121 tests, 0 fallos; lint: los mismos avisos de antes, ninguno nuevo)
./gradlew -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL in 1m 38s   (jvm-check: 2090 tests, 0 fallos)
```

## Para la prueba en el móvil
Con «Quitar animaciones» activado:
- al pulsar una tarjeta (Herramientas, Salud o la identidad en Contactos) sale el borde de acento y no la onda, en los dos estilos;
- al volver a la app tras cambiar el ajuste, la tarjeta sigue el ajuste nuevo.

«Salud» ahora sale con tarjetas de cristal.

## Textos para el Abogado Digital (ronda 2)
No hay textos nuevos ni cambiados.
