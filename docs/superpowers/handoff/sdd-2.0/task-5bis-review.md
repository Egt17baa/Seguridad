# Revisión de la tarea 5 bis (Clavero 2.0): aspecto «Cristal», estilos, paletas y «Personalizado»

**Revisado:** `51ac870..e53c4a5` (commits `411eab2` y `e53c4a5`), el brief `task-5bis-brief.md` y el informe `task-5bis-report.md`. Solo lectura: no se ha cambiado el árbol ni se ha vuelto a ejecutar la suite. Todas las citas `archivo:línea` son de `e53c4a5` (`git show e53c4a5:<archivo>`), salvo que se diga otra cosa.

## Spec Compliance

**Interfaces del brief**
- ✅ `ThemeStyle`, `PaletteId` (8 valores), `GlassLevel` y `CustomPalette(accentHue, backgroundHue, glass)`, tal cual: `settings/Appearance.kt:10-36`.
- ✅ En `Settings` hay `style = GLASS`, `palette = NIGHT`, `custom = null`, `reduceTransparency = false` y `highContrast = false`, todos con valor por defecto: `settings/Settings.kt:59-66`. `oldSettingsGetNightAndGlass` lee un `settings.json` de la 1.4.3 en los tres modos y comprueba que el resto no cambia.
- ✅ `colorsFor(palette, custom, dark, highContrast)` devuelve el esquema, los colores de estado, el fondo con sus manchas y las opacidades: `ui/theme/Palettes.kt:99-106`. El quinto parámetro (`wallpaper`) tiene valor por defecto y no rompe la firma.
- ✅ con desviación, que debe decidirse: `fixContrast(custom): CustomPalette` (`Palettes.kt:113-119`) no corrige la luminosidad, porque `CustomPalette` no tiene dónde guardarla. La corrige `colorsFor` cada vez, de forma determinista. Decido que **«nunca se guarda una combinación que no se lee» se cumple**:
  - lo único que se guarda son dos tonos y una intensidad;
  - después de `saneCustom` el dominio son exactamente 432 combinaciones;
  - `customIsCorrectedToContrast` (`PalettesTest.kt:30-39`) mide las 432 en claro y en oscuro, con paneles opacos y translúcidos, con un medidor aparte (`ThemeSurfaces.kt`).

  La rama que baja la intensidad no se usa con los valores de hoy (el test fija `fixContrast(c) == c`).
- ✅ `GlassPanel(modifier, level, content)`: `ui/theme/Glass.kt:169-175`. Es translúcido, con borde y brillo (`Glass.kt:199-216`). Solo difuminan las barras y los diálogos (`Glass.kt:71`). Es opaco con «Reducir transparencias», «Alto contraste» o «Sólido» (`Glass.kt:92`; `Palettes.kt:83`).

**Requisitos del diseño y de las restricciones**
- ✅ R-1: cada texto llega a 4,5:1 sobre su panel mezclado con el punto más oscuro y el más claro del fondo, en claro y en oscuro. Lo calcula `surfacesOf` (`ThemeSurfaces.kt:49-73`), y los extremos se sacan canal a canal sobre todas las esquinas del dibujo (`ThemeSurfaces.kt:37-44`). Lo comprueba `everyPaletteReadsInLightAndDark` (`PalettesTest.kt:26-28`).
- ✅ R-C5, el peor caso real: una barra, un diálogo o un menú sin difuminar se mide también sobre blanco y negro puros (`ThemeSurfaces.kt:63-64`; `GlassContrastTest.kt:79-96`), y `outline` y `primary` a 3:1 (`ThemeSurfaces.kt:120-125`). En Android 12 o después, la barra que difumina dibuja debajo el fondo opaco (`Glass.kt:208`, `:226-228`), así que lo que pase por detrás no se ve.
- ✅ R-C6: el color dinámico pasa por la misma corrección (`Palettes.kt:101`, `:182-189`), y «Alto contraste» va a 7:1 con paneles opacos y sin brillo (`Palettes.kt:223`, `:259`, `:312-315`; `PalettesTest.kt:62-75`). Ver Minor 1 sobre la cobertura de los colores del fondo de pantalla.
- ✅ R-C7: las manchas son fijas (`Palettes.kt:192`; `Glass.kt:150-157`, sin animación), y el contenido va entre las barras (el `padding` del `Scaffold`). ⚠️ No hay ningún test de foco; se apoya en que todas las barras van en las ranuras del `Scaffold`.
- ✅ R-C8: una línea, una sola vez, a quien actualiza (`AppearanceScreen.kt:102`; `SettingsStore.kt:62`; `AppNavHost.kt:223-230`; test `theAppearanceNoticeIsForThoseWhoUpdated`). El texto cambia la redacción del diseño (ver «Desviaciones»).
- ✅ R-C9: las paletas son propias (`Palettes.kt:166-174`), sin fuentes nuevas.
- ✅ O-C3: solo translucidez y difuminado. Nada de refracción, lente ni formas «líquidas» (`Glass.kt:51-56`).
- ✅ O-C4 y O-C6: en los textos añadidos no aparecen «Liquid Glass», «líquido», «accesible», «WCAG», «garantizado», «al estilo de», ™, ® ni el nombre comercial del color dinámico. «WCAG» solo sale en comentarios. El difuminado «desde Android 12» está dicho en `STYLE_MORE` (`AppearanceScreen.kt:77`), bajo «Más información».
- ✅ Riesgo 1, los tests derivan de producción: todos los tests de contraste piden los colores a `colorsFor` o `lookFor` y miden con su propio `over`/`bgBounds` (`ThemeSurfaces.kt:30-44`). Usan `panelAlpha` y `panelColor`, que son las mismas funciones con las que se dibuja (`Glass.kt:74`, `:196`, `:199`; `Components.kt:193`). Una mutación en producción los hace fallar (el informe da la prueba, y el código lo confirma). Hay una dependencia compartida, `TagPalette.contrast`: ver Minor 7.
- ✅ Riesgo 2, el color dinámico con esquemas extremos: `WALLPAPER_SWEEP` recorre 24 × 24 tonos con saturaciones de 0, 0,15, 0,5 y 1 (`ThemeSurfaces.kt:138-140`). ⚠️ No prueba `wallpaperSeeds()` (de `Color` a semillas, `Palettes.kt:153-157`) ni entradas fuera de la rejilla, y `colorsFor` no tiene ningún respaldo en tiempo de ejecución (Minor 1).
- ✅ Riesgo 3, los ajustes son globales: van en `Settings` (`Settings.kt:59-72`), no en `VaultSettings` (`Settings.kt:82`). `MainActivity` pasa `settings` a `SeguridadTheme` (`Theme.kt:56`) por encima de `AppNavHost`, así que la pantalla de desbloqueo, la de crear la bóveda y el aviso inicial ya usan el aspecto.
- ✅ Riesgo 4, la API 31:
  - `BlurEffect` solo está en `blurredBackdrop`, con `@RequiresApi(S)` (`Glass.kt:222-231`), y su única llamada va detrás de `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (`Glass.kt:197`);
  - `lookFor` decide `blur` con `sdk >= S` (`Glass.kt:93`) y recibe `Build.VERSION.SDK_INT` (`Theme.kt:62`);
  - el color dinámico tiene su retorno temprano (`Theme.kt:79`);
  - lo comprueba `PlatformSourcesTest.blurOnlyFromApi31`.
- ✅ Riesgo 5: decidido arriba (se cumple).
- ✅ «Reducir transparencias» se enciende solo con «Alto contraste» (`Glass.kt:92`; `AppearanceScreen.kt:199`).
- ✅ Android 8 a 11: el mismo panel, más opaco y sin difuminar (`Palettes.kt:89`, `+0.1` o el suelo).
- ✅ `FLAG_SECURE` no cambia. Se añaden `FilterObscuredTouches` en todos los diálogos y menús (`Components.kt:158`, `:183`, `:197`). No hay dependencias, permisos, componentes ni registros nuevos (el manifiesto y los `build.gradle*` no están tocados).
- ✅ «Secretos e imágenes nunca en `rememberSaveable`»: lo único guardado es el borrador de «Personalizado», tres valores pequeños y no secretos (`AppearanceScreen.kt:136-139`).
- ✅ Las pantallas solo cambian el `import`. Comprobado con `git diff -U0` sin `AppearanceScreen`, `SettingsScreen` ni `VaultList`: ninguna línea añadida o quitada que no sea un `import`.
- ✅ `colors.xml`: `ink` es el fondo oscuro de «Noche», y un test lo comprueba (`PalettesTest.kt:98-104`).
- ✅ Los tests que pide el paso 1, con sus nombres: `everyPaletteReadsInLightAndDark`, `customIsCorrectedToContrast`, `highContrastIsOpaque`, `reduceTransparencyMakesPanelsOpaque`, `oldSettingsGetNightAndGlass`, `blurOnlyFromApi31`, y `TagPaletteThemeTest` ampliado.

**Desviaciones que el controlador o el dueño deben decidir (no son fallos)**
- ⚠️ `fixContrast` no corrige la luminosidad (decidido arriba: se cumple la garantía).
- ⚠️ «Sólido» no es exactamente la 1.4.x: es opaco y liso, pero con los colores de la paleta. Por eso la línea de R-C8 dice «volver a uno sin transparencias» y no «al de antes». Es coherente con el diseño («Paleta: sustituye a los 6 colores de acento»), pero el dueño debe saber que los colores turquesa de la 1.4.x no vuelven con ninguna opción.
- ⚠️ Las filas de la lista (`VaultList.EntryRow`) no son paneles translúcidos: van sobre el fondo. Es de la tarea 6, para la que el brief adelanta esta tarea. Los textos de las filas ya se miden sobre los extremos del fondo.
- ⚠️ En la app no hay hojas inferiores, así que el texto no las nombra. `OVERLAY` las cubriría si llegan.
- ⚠️ El movimiento («solo un brillo breve al pulsar un panel, y ninguno si Android pide quitar las animaciones») se fía de la onda de Material, y nadie ha comprobado que respete «Quitar animaciones».
- ⚠️ R-C10 (la línea de la política) no está hecha, ni estaba en los archivos del brief. Queda para la tarea de documentos. El informe deja propuesta la redacción y avisa de que también se guardan las transparencias, el contraste y la marca del aviso.
- ⚠️ No se puede verificar en la JVM, y queda para la prueba en el móvil: que el difuminado de un diálogo quede alineado entre ventanas (`Glass.kt:191`, `:228`); el aspecto en Android 8 a 11; el menú translúcido con su sombra; el color dinámico con un fondo real; las transiciones con los `Scaffold` transparentes; la letra al 200 %; TalkBack; y si el redondeo o el *dithering* de la GPU puede bajar un texto de 4,5:1 en el color que más justo queda. `reaching` deja cada texto en el mínimo que llega (`Palettes.kt:429-440`), sin margen. Si se publica la cifra (O-C6), conviene un margen pequeño en el corrector (por ejemplo, apuntar a 4,6 y medir a 4,5).

## Strengths

- El corrector y el medidor están separados de verdad: `ThemeSurfaces.kt` vuelve a escribir la mezcla y los extremos del fondo, y mide lo que se dibuja con las mismas funciones de opacidad que el dibujo. Es justo lo que pide O-C6.
- El cálculo de los extremos del fondo es exacto. Recorre todas las esquinas del dibujo, con cada mancha a 0 o en su pico; la mezcla es lineal por canal, así que ningún punto queda fuera.
- Se arregló bien un fallo real: el redondeo entre `Double` y `Float` de la opacidad (`Palettes.kt:303-309`).
- Las pantallas toman el cristal de los envoltorios con el mismo nombre que los de Material (`Components.kt:108-200`): solo cambia el `import`. Se conservan los 34 diálogos de ANDROID-3, y los menús ganan `FilterObscuredTouches`.
- El difuminado vuelve a dibujar el fondo de la app, sin leer otra ventana ni el fondo de pantalla, y con `TileMode.Clamp` para que los bordes queden opacos (`Glass.kt:226`).
- Los ajustes son robustos: un «Personalizado» ilegible se quita solo y no tira el tiempo de bloqueo (`SettingsStore.kt:164-166`; test `aStrangeAppearanceKeepsTheOtherSettings`). Los tonos fuera de rango se normalizan (`SettingsStore.kt:137`).
- `decideRenameNotice` decide cada aviso por su cuenta (`SettingsStore.kt:57-64`). Arregla el caso de quien actualiza desde la 1.3 o la 1.4 y ya tenía decidido el aviso del nombre.
- `TagPalette` pasa de una lista fija de superficies a un límite de luminancia con margen (0,070 frente a 0,0729, y 0,70 frente a 0,681), que se prueba en las dos direcciones.

## Issues

### Critical
Ninguno.

### Important
Ninguno.

### Minor

1. **`colorsFor` no mira nunca `Built.met`, y «Colores del fondo de pantalla» no tiene respaldo.**
   - **Dónde:** `Palettes.kt:99-106` (no usa `met`), `Palettes.kt:304-309` y `Palettes.kt:153-157`.
   - **Qué:** «Personalizado» está cubierto entero por el test (432 de 432). El fondo de pantalla es una entrada continua y solo se prueba en una rejilla de 2304 puntos; si una semilla fuera de la rejilla no llegara, se mostraría tal cual. `opacity()` devuelve 1,0 cuando llega a 100 sin cumplir y no pone `met = false`; solo lo recoge en parte `reads(sContainer)`/`reads(sHigh)`, sin el brillo (`:316`). Además, `wallpaperSeeds()`, que pasa de `Color` a semillas, no tiene test.
   - **Por qué:** el diseño dice «nunca», y para esta paleta lo sostiene solo una muestra.
   - **Arreglo:**
     - en `colorsFor`, si `!met` con `WALLPAPER` (y con `CUSTOM` tras `fixContrast`), devolver «Noche»;
     - en `opacity()`, marcar `met = false` si sale en 100 sin cumplir;
     - un test que pase por `wallpaperSeeds(...)` y `colorsFor` colores extremos (`Color.White`, `Color.Black`, un gris, los primarios puros y un casi gris);
     - un barrido aleatorio con semilla fija fuera de la rejilla;
     - un test de que «Noche» tiene `met`.

2. **El brillo acaba en `Color.Transparent`, y el modelo del test supone blanco.**
   - **Dónde:** `Glass.kt:200-202`, frente a `ThemeSurfaces.kt:68`.
   - **Qué:** el degradado va de blanco con `highlightAlpha` a `Color.Transparent`, que es negro con alfa 0. El test modela blanco puro de 0 a `highlightAlpha`. Si la interpolación no fuera premultiplicada, la zona del medio sería gris y oscurecería el panel en el tema claro.
   - **Por qué:** el modelo del test no coincide con la parada que se dibuja. No está probado que baje el contraste, pero O-C6 pide que se mida exactamente lo que se dibuja.
   - **Arreglo:** `0.5f to Color.White.copy(alpha = 0f)`.

3. **Un fallo al guardar no se ve, y «Aplicar» cierra el editor antes de saber si guardó.**
   - **Dónde:** `AppearanceScreen.kt:149` (`Hint` arriba de una columna con desplazamiento), `AppearanceScreen.kt:189` (`editing = false` antes de `change`) y `AppearanceScreen.kt:99`, frente a `SettingsScreen.kt:446`.
   - **Qué:**
     - quien cambia «Alto contraste» al final de la pantalla no ve el error, que sale arriba;
     - si «Aplicar» falla, la sección «Personalizado» se cierra (la paleta guardada no es `CUSTOM`) y el borrador desaparece de la vista;
     - el texto es «No se pudo guardar el ajuste.», con punto, en un `Hint` fijo, mientras Ajustes dice «No se pudo guardar el ajuste», sin punto, en un *snackbar*.
   - **Arreglo:** usar un `SnackbarHost` con el mismo texto que Ajustes (una sola constante) y poner `editing = false` solo cuando `store.update` termine bien.

4. **El título de la sección repite el del interruptor y no nombra «Alto contraste».**
   - **Dónde:** `AppearanceScreen.kt:197-205`.
   - **Qué:** `Section(REDUCE_TRANSPARENCY)` contiene el interruptor «Reducir transparencias» y el de «Alto contraste». TalkBack lee «Reducir transparencias» dos veces, y «Alto contraste» queda bajo un título que no es el suyo.
   - **Arreglo:** un título propio, por ejemplo «Transparencias y contraste» (texto nuevo, para el Abogado), o una sección para cada uno.

5. **La paleta «Personalizado» sin tonos dice «Personalizado» y enseña «Noche».**
   - **Dónde:** `SettingsStore.kt:164-166` (quita solo `custom`), `Palettes.kt:102`, `AppearanceScreen.kt:112` y `AppearanceScreen.kt:160-161`.
   - **Qué:** un `settings.json` con `"palette":"CUSTOM"` y un `custom` que no se lee (el caso de `aStrangeAppearanceKeepsTheOtherSettings`) se queda en `CUSTOM` con `custom == null`. El resumen y la opción marcada dicen «Personalizado», y lo que se ve es «Noche». Lo mismo pasa con `WALLPAPER` en Android 8 a 11: no se ofrece y ninguna opción queda marcada.
   - **Arreglo:** en `sane()`, `CUSTOM` con `custom == null` pasa a `NIGHT`, y `WALLPAPER` en Android 8 a 11 se trata como «Noche» también en la pantalla. Que el test lo compruebe.

6. **El límite de las etiquetas solo se comprueba en las seis paletas con nombre.**
   - **Dónde:** `TagPaletteThemeTest.everySurfaceIsInsideTheTagBound` (solo `NAMED_PALETTES`, sin alto contraste) y la muestra de uno de cada 7 del fondo de pantalla en `everyTagColourReadsOnEveryThemeSurface`.
   - **Qué:** el diseño pide medir las etiquetas «contra las superficies de cada paleta y de cada estilo». «Personalizado» está entero; el fondo de pantalla, solo en muestra.
   - **Arreglo:** extender la comprobación de luminancia, que es barata, a `ALL_CUSTOM`, a todo `WALLPAPER_SWEEP` y a alto contraste.

7. **El instrumento de medida es el mismo que usa el corrector, y apenas está anclado.**
   - **Dónde:** `Palettes.kt:367-369` y `ThemeSurfaces.kt:112`, `:117` y `:122` llaman a `TagPalette.contrast`/`luminance` (`vault/TagPalette.kt:52-65`). Las únicas referencias fijas son 21:1 y 1:1 (`TagPaletteTest.kt:52-55`).
   - **Qué:** la fórmula es la correcta (umbral de 0,04045, exponente 2,4 y pesos 0,2126, 0,7152 y 0,0722). Pero con un exponente o un umbral cambiados, el corrector y los tests se moverían juntos, y 21 y 1 no lo detectarían.
   - **Arreglo:** dos referencias intermedias, `#767676` sobre blanco ≈ 4,54 (llega) y `#777777` sobre blanco ≈ 4,48 (no llega), con tolerancia 0,01.

**Para la tarea 6 o la de pantallas (fuera de este diff, no son fallos de la tarea):** `HealthScreen.kt:103` y `:195` usan `Card` de Material y no `SectionCard`, así que no toman el cristal (son opacas y se leen). Pasarlas a `SectionCard` cuando se toque esa pantalla.

## Assessment

**Spec:** ✅ cumple. Las desviaciones están documentadas y son razonables (`fixContrast`, «Sólido» con los colores de la paleta, las filas para la tarea 6, sin hojas, R-C10 para los documentos). Quedan para que las decida el controlador o el dueño.

**Task quality: Approved.** No hay ningún hallazgo crítico ni importante. Quedan **7 menores por arreglar**, como manda el proyecto. Lo que depende del dibujo real (alineación del difuminado, redondeo de la GPU, «Quitar animaciones», Android 8 a 11) va a la prueba en el móvil.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza.** El aspecto no toca secretos. El atacante útil es quien manipula `settings.json` (con una copia o con *root*), y lo peor que consigue es un aspecto raro:
  - tonos normalizados: `SettingsStore.kt:137`, `Appearance.kt:38-42`;
  - un «Personalizado» ilegible se quita solo: `SettingsStore.kt:164-166`;
  - los enums desconocidos vuelven a su valor por defecto;
  - `colorsFor` no lanza excepciones.

  Lo que no cuadra en la pantalla con esos datos es el Minor 5. No veo vulnerabilidad.
- **Deserialización:** `kotlinx.serialization`, sin tipos polimórficos. El reintento captura `IllegalArgumentException`, de la que `SerializationException` es subclase, y falla del lado seguro (`SettingsStore.kt:164-166`).
- **Ventana y toques:** `FLAG_SECURE` sin cambios. `FilterObscuredTouches` está ahora en todos los diálogos y en los menús (`Components.kt:158`, `:183`, `:197`). Es una mejora: los menús no lo tenían.
- **Nada de otras ventanas:** el difuminado vuelve a dibujar solo el fondo de la app (`Glass.kt:226-228`). No usa `FLAG_BLUR_BEHIND` ni lee el contenido de debajo. El color dinámico solo lee el esquema que da Android (`Theme.kt:79-84`), sin permisos.
- **Estado guardado:** solo el borrador de «Personalizado» (`AppearanceScreen.kt:136-139`), que no es secreto. `theThemeStateHasNoSecret` impide que `crypto`, `VaultStore` o `AppContainer` entren en los archivos del tema.
- **Sin cambios** de red, permisos, componentes, dependencias, registros ni formatos de bóveda.
- **El instrumento de O-C6:** la fórmula WCAG de `TagPalette.kt:52-65` es correcta; le faltan referencias intermedias (Minor 7).

## Revisión Ingeniero Móvil Senior

- **API 31:** correcta y comprobada por un test de fuentes (`Glass.kt:197`, `:222`; `Theme.kt:79`; `PlatformSourcesTest.blurOnlyFromApi31`). `GraphicsLayer` y `positionOnScreen` no piden la API 31.
- **Composición y dibujo:**
  - `drawWithCache` lee `place.at` y `backdrop.*` en el bloque de caché, que se invalida al cambiar ese estado (`Glass.kt:192-218`);
  - la capa la pide `obtainGraphicsLayer()` del `CacheDrawScope`, que la libera sola;
  - `Modifier.glass` es una fábrica `@Composable` y vuelve a crear la lambda en cada recomposición (`Glass.kt:183-219`). Pasa poco (barras y diálogos) y Lint no se queja; si alguna barra recompone a menudo, conviene un `Modifier.Node`.
- **Rendimiento:** el `Look` va con `remember` (`Theme.kt:59-64`), y los colores con una caché sincronizada de 64 entradas (`Palettes.kt:199-209`). `fixContrast` y la vista previa calculan en el hilo principal, alrededor de 1 ms por paleta, aceptable al pulsar un tono. Las tarjetas y las filas no difuminan (`Glass.kt:71`).
- **Esquema transparente:** `Look.scheme.background = Transparent` (`Glass.kt:77`). `contentColorFor(Transparent)` coincide con `background` y devuelve `onBackground`, así que los `Scaffold`, `SectionCard` y `NavigationBar` transparentes siguen con el color de texto correcto. Funciona, pero de forma frágil: si `background` deja de ser `Transparent`, el color de contenido cae en `LocalContentColor`, que por defecto es negro. Conviene un comentario.
- **Ciclo de vida:** el aviso de R-C8 vive en `AppContainer` (`OnceNotice`, `RenameNotice.kt:30-66`), sobrevive al giro y se marca en disco con `NonCancellable`. El `change` de `AppearanceScreen.kt:125-134` captura también `CancellationException`, igual que `SettingsScreen.kt:441-449` (patrón ya existente, no lo cuento).
- **Accesibilidad:** cada tono lleva su nombre y la muestra no se lee (`AppearanceScreen.kt:213-225`); la vista previa se lee como una sola cosa (`:254`); los interruptores tienen `Role.Switch` (`:231`). Falla el título duplicado (Minor 4).
- **Prueba en el móvil (prioridad):**
  - la alineación del difuminado de un diálogo entre ventanas;
  - el primer fotograma sin difuminar y luego difuminado, que da un cambio de opacidad visible;
  - el menú translúcido con la sombra de Material;
  - las transiciones con todo transparente;
  - el destello oscuro del arranque en el tema claro (`ink`), igual que en la 1.4.x.

## Textos para el Abogado Digital (revisión final)

La lista del implementador está completa: los he cotejado con `AppearanceScreen.kt:70-112` y `Palettes.kt:122-147`. Añado o señalo:

- **Nuevo, si se aplica el Minor 4:** el título de la sección «Transparencias y contraste».
- **O-C6, «contraste suficiente»** (`WALLPAPER_MORE` y `CUSTOM_MORE`, `AppearanceScreen.kt:80` y `:83`). No dice «garantizado», pero promete un resultado. El test lo sostiene en 4,5:1 sobre las superficies que se enumeran. Ver si basta o si es mejor «para que el texto se lea mejor».
- **Veracidad de `HIGH_CONTRAST_SHORT`** (`:98`), «Textos con más contraste y bordes marcados.». Las 8 etiquetas de color siguen en 4,5:1, no en 7:1 (Duda 7 del informe). Es cierto en general; conviene que lo sepa.
- **O-C4, «Intensidad del cristal»** (`:86`) y «Elige … la intensidad del cristal» (`:82`): «cristal» como nombre común dentro de la pantalla, no como marca. Confirmar que no choca con «solo el nombre de un estilo».
- **R-C8 cambiado** (`:102`): «Aspecto nuevo. Para volver a uno sin transparencias: Ajustes → Aspecto → «Sólido».». Sustituye a «volver al de antes», porque «Sólido» no recupera los colores de la 1.4.x.
- **El plegado de `STYLE_MORE`** (`:77`) quita «las hojas» y dice «el fondo de la app que tienen detrás». Es correcto con el código: no hay hojas (`Glass.kt:226-228`).
- **R-C10, la política:** la redacción que propone el informe debe nombrar también «transparencias y contraste» y la marca del aviso, que también se guardan (`Settings.kt:64-72`).
