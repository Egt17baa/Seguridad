# Tarea 6: modelo de importar, detección y lectores CSV — informe del implementador

**Estado:** DONE_WITH_CONCERNS (ver «Dudas»).
**Commit:** `754e200` 1.4.0: importar, modelo común y lectores CSV (subido a `origin/ccr-a1a6eabb-765f9g`).

## Qué se ha hecho

Archivos nuevos en `app/src/main/java/app/seguridad/imports/`:

| Archivo | Qué tiene |
|---|---|
| `ImportTexts.kt` | Todos los textos visibles de importar (R2, R7): las siete etiquetas de origen, `SKIP_NOT_TOTP`, y los mensajes `IMPORT_FILE_TOO_BIG`, `IMPORT_NOT_TEXT`, `IMPORT_UNKNOWN` («Clavero no reconoce este archivo.»), `IMPORT_TOO_MANY_ITEMS`, `IMPORT_CSV_UNCLOSED_QUOTE`, `IMPORT_CSV_TOO_MANY_COLUMNS`, `IMPORT_CSV_TOO_MANY_CELLS` e `IMPORT_BROKEN`. Es el único archivo de `main` con nombres de otros productos en literales. |
| `ImportLimits.kt` | Los topes del brief, más tres nuevos: `CSV_MAX_COLUMNS = 64`, `CSV_MAX_CELLS = 500_000` y `CSV_MAX_FIELD_CHARS = MAX_NOTES + 1`. |
| `ImportModel.kt` | Las interfaces del brief, con sus firmas tal cual (`ImportFormat`, `ImportedItem`, `SkipReason`, `Skipped`, `ImportRead`, `ReadResult`, `ImportFailure`). Además, para que las tareas 7 y 10 las reutilicen: `importedEntry` (entrada sin fusionar: `id = ""`, fechas a 0, sin etiquetas), `plainReportText` (el texto del informe, decisión (c)), `ImportReadBuilder` (índices únicos, títulos de `Skipped` siempre pasados por `plainReportText`, solo los tipos LOGIN, NOTE y TOTP, y `addOtpAuth` con la regla de CRY-B02) y `checkItemCount`. |
| `TextDecode.kt` | `decodeText`: UTF-8 con o sin BOM y UTF-16 LE/BE con BOM, estricto (sin U+FFFD). |
| `Csv.kt` | `parseCsv` (RFC 4180, una pasada, con los topes dentro), `csvHeader` y `csvDelimiter`. |
| `CsvFormats.kt` | `csvFormat` (cabecera → formato) y `readCsvImport` (filas → entradas), con el mapeo fijo de columnas. |
| `Detect.kt` | `readImport(bytes, password)`, con la lista `READERS` donde las tareas 7 y 10 registran sus lectores (`TextReader`), y una sobrecarga `internal` con la lista como parámetro para las pruebas. |

Pruebas en `app/src/test/java/app/seguridad/imports/`: `CsvTest` (23), `CsvFormatsTest` (18), `DetectTest` (9), `ImportModelTest` (6) e `ImportTextsTest` (2). Archivos de prueba hechos a mano en `app/src/test/resources/import/csv/`: `chromium.csv`, `firefox.csv` (CRLF, todo entre comillas), `apple.csv` y un `README.txt`.

No se ha cambiado ningún archivo existente. `VaultEntry`, `readOtpAuth`, `TotpSecret` y `withoutBidiControls` se usan tal cual.

### Decisiones de comportamiento (definidas y fijadas con pruebas)

- **Detección:** por la cabecera, no por el nombre. Con `url`, `username` y `password`: `name` → navegador (Chromium); `title` → Apple; `httpRealm`, `formActionOrigin` o `guid` → Firefox. Sin mayúsculas ni orden; un nombre repetido usa la primera columna. Una primera fila que no se puede leer (comillas sin cerrar, más de 64 columnas) es «Clavero no reconoce este archivo.».
- **Separador:** `;` si la primera línea (fuera de comillas) tiene más `;` que `,`; si no, `,`.
- **Finales de línea:** LF, CRLF y CR solo terminan la fila; dentro de comillas se guardan tal cual.
- **Líneas vacías** y el salto final no dan fila; `""` sí es una celda vacía.
- **Comillas sueltas** (`ab"c`, `"abc"x`): se guardan como texto (`ab"c`, `abcx`), como el modo no estricto de los lectores CSV habituales. **Comillas sin cerrar:** `ImportFailure(IMPORT_CSV_UNCLOSED_QUOTE)` para todo el archivo.
- **Filas cortas:** las celdas que faltan son "". Las columnas desconocidas no se leen.
- **Vacío:** una fila sin URL, usuario, contraseña, notas ni OTPAuth (aunque tenga título) es `Skipped(EMPTY)`. Una contraseña de espacios es una contraseña (`isEmpty`, no `isBlank`).
- **NUL** en una columna conocida → `Skipped(BROKEN)` para la fila; en una desconocida, nada.
- **Campo largo:** la fila entera va a `Skipped(FIELD_TOO_LONG)`, nunca cortada. La OTPAuth de Apple, pasada de `OTPAUTH_MAX_CHARS`, solo deja fuera el 2FA (el LOGIN entra), igual que un 2FA no admitido.
- **Título:** `name` o `Title`; en Firefox, el host de la URL. Si queda en blanco: el host de la URL, el usuario o «Acceso» (`EntryType.LOGIN.label`). Nunca la URL entera, que podría llevar `usuario:clave@` (`chromiumHeader` lo comprueba con `https://user:secreto@…`).
- **Índices:** un contador único para `items` y `skipped`, en el orden del archivo (la clave de `overrides` en la tarea 8). El TOTP de una fila de Apple va justo después de su LOGIN, con el mismo título y las mismas carpetas (ninguna en CSV).
- **Topes de longitud** en unidades UTF-16 (`String.length`): nunca menos que los caracteres que se ven.

## TDD

**RED.** Primero las pruebas y unas funciones vacías (`TODO()`) con las firmas del brief, y después `gradle -p jvm-check test --tests 'app.seguridad.imports.*'`:

```
CsvFormatsTest tests="18" failures="18"
CsvTest        tests="23" failures="23"
DetectTest     tests="9"  failures="9"
ImportModelTest tests="6" failures="0"
ImportTextsTest tests="2" failures="0"
58 tests completed, 50 failed   (kotlin.NotImplementedError … at app.seguridad.imports.DetectKt.readImport(Detect.kt:7))
```

Era lo esperado: los 50 fallos son de `decodeText`, `parseCsv`, `readCsvImport` y `readImport`, que aún no existían. `ImportModelTest` e `ImportTextsTest` pasaron en RED porque el modelo (`plainReportText`, `ImportReadBuilder`) y `ImportTexts.kt` se escribieron antes que sus pruebas. Es una desviación de TDD, y la dejo apuntada. Lo compensan las comprobaciones que esas pruebas llevan dentro: `theBidiListIsTheOneOfWithoutBidiControls` recorre los 65 536 `Char` y `theCheckFindsANameInALiteral` comprueba que el detector encuentra un nombre.

**GREEN.** Con la implementación, el mismo comando: 58/58 (CsvFormatsTest 18, CsvTest 23, DetectTest 9, ImportModelTest 6, ImportTextsTest 2), sin fallos ni pruebas saltadas. La más lenta es `aHugeQuotedFieldIsReadInLinearTime`, con 0,67 s para 16 MiB.

**Arreglo durante la verificación.** `:app:testDebugUnitTest` no compilaba `CsvTest`: el classpath de pruebas de Android no declara `java.lang.management`. La medida de memoria pasó a usar la clase por su nombre (`Class.forName`), solo en la prueba. Se añadió además una comprobación de que la sonda mide de verdad (`allocated { ByteArray(4 MiB) } >= 4 MiB`), para que un 0 no pase por una medida buena. Ver «Verificación».

## Topes ante entradas hostiles (decisión del dueño (a)/(b))

| Tope | Dónde | Prueba |
|---|---|---|
| Archivo > `IMPORT_MAX_BYTES` rechazado **antes de decodificar** | `Detect.kt:30` (`ImportLimits.kt:9`) | `DetectTest.over16MbIsRefused` (16 MiB + 1 de 0xFF: responde «tamaño» y no «no es texto», así que no se ha decodificado nada), `exactly16MbIsReadForItsContent` |
| Decodificación estricta en un búfer fijo de 8 K caracteres, que se borra al terminar; la String se crea de una vez y solo con bytes válidos | `TextDecode.kt:20-29`, `:33-57` | `CsvTest.invalidUtf8IsRejected`, `utf16WithALoneSurrogateOrAnOddLengthIsRejected` |
| Columnas por fila ≤ `CSV_MAX_COLUMNS` (64) | `Csv.kt:134` (`ImportLimits.kt:25`) | `CsvTest.tooManyColumnsIsAFailure`, `sixteenMegabytesOfCommasIsRefusedFastWithBoundedMemory` |
| Celdas en total ≤ `CSV_MAX_CELLS` (500 000) | `Csv.kt:135` (`ImportLimits.kt:31`) | `CsvTest.tooManyCellsIsAFailure`, `sixteenMegabytesOfCommasIsRefusedFastWithBoundedMemory` |
| Filas ≤ `IMPORT_MAX_ITEMS` + cabecera, comprobado **mientras se lee** | `Csv.kt:70`; otra vez en `CsvFormats.kt:67` (`checkItemCount`, `ImportModel.kt:160-162`) | `CsvTest.tooManyRowsIsAFailure`, `DetectTest.itemCountOverLimitIsRefused` (20 000 entran y 20 001 no) |
| Campo cortado **mientras se construye** en `CSV_MAX_FIELD_CHARS`: el resto se consume sin guardarlo, un solo StringBuilder que se reutiliza | `Csv.kt:129-131` (`ImportLimits.kt:38`) | `CsvTest.aLongFieldIsCutPastEveryLimitWhileItIsRead`, `aHugeQuotedFieldIsReadInLinearTime` |
| Topes por campo → `Skipped(FIELD_TOO_LONG)` para la fila, nunca cortada | `CsvFormats.kt:118-122` | `CsvFormatsTest.fieldOverLimitIsSkipped` (en el tope entra y uno más no; 500 000 caracteres, cortados por el lector, siguen fuera) |
| OTPAuth > `OTPAUTH_MAX_CHARS` → `Skipped(FIELD_TOO_LONG)` para el 2FA | `ImportModel.kt:146` | `CsvFormatsTest.appleBrokenOtpAuthIsBrokenAndNeverACrash` |
| Tiempo lineal: una pasada carácter a carácter, sin regex; `csvDelimiter` solo mira la primera línea | `Csv.kt:44-58`, `:77-127` | `sixteenMegabytesOfCommas…` (`timeout = 20 s`, 0,41 s), `aHugeQuotedField…` (16 MiB de `""`, y unas comillas sin cerrar al principio de 16 MiB: una pasada) |
| Memoria acotada: las celdas vacías comparten `""`; medida con la memoria que reserva el hilo | `Csv.kt:137` | `sixteenMegabytesOfCommas…`: una línea de 16 M comas reserva menos de 1 MiB; 262 144 líneas de 64 celdas, menos de 64 MiB y se paran en la celda 500 001; `readImport` de 16 MiB, menos de 48 MiB con la decodificación |
| Comillas sin cerrar → `ImportFailure` | `Csv.kt:84` | `CsvTest.unterminatedQuoteIsAFailure`, `DetectTest.aBrokenCsvOfAKnownFormatIsAFailureNotACrash` |
| CR solo → fin de fila (definido) | `Csv.kt:110-113` | `CsvTest.crAloneEndsARecord` |
| NUL: el lector lo guarda y la fila con NUL en una columna conocida es `BROKEN` | `CsvFormats.kt:114-116` | `CsvTest.nulAndControlCharactersAreKeptByTheParser`, `CsvFormatsTest.nulInAKnownFieldIsADamagedRow` |
| Surrogates sueltos → `decodeText` da null («dañado o no es texto») | `TextDecode.kt:33-57` | `CsvTest.utf16WithALoneSurrogateOrAnOddLengthIsRejected`, `invalidUtf8IsRejected` (ED A0 80) |
| Ninguna excepción sale de `readImport`: `ImportFailure` da su mensaje; cualquier otra `Exception`, `IMPORT_BROKEN` (nunca `e.message`). `Error` no se captura | `Detect.kt:29-39` | `DetectTest.readerExceptionsNeverEscape` (comprueba que el texto `clave-del-archivo-123` de la excepción no llega al mensaje) |
| `Skipped.title` acotado a `MAX_TITLE` y en texto plano | `ImportModel.kt:91-101`, `:131` | `ImportModelTest.plainReportTextIsBoundedWithoutSplittingAPair`, `skippedTitlesAlwaysGoThroughThePlainText`, `CsvFormatsTest.fieldOverLimitIsSkipped` |
| El búfer del lector y el de la decodificación se sobrescriben al terminar (tenían las últimas contraseñas leídas) | `Csv.kt:141-146`, `TextDecode.kt:55` | Sin prueba directa: el array interno no se ve desde fuera |

## Cómo se cumple la decisión (c): todo lo importado son datos, nunca código

- **Los campos se guardan tal como se leen**, sin recortar ni interpretar (`CsvFormats.kt:123`). `CsvFormatsTest.hostileTextIsStoredExactlyAndTheReportIsPlain` lo fija con caracteres de control, ESC, U+2028, U+0085, U+202E, U+202D, U+2066 y U+2069 en el título, la contraseña y las notas. Esos valores se leen, se guardan exactos y se acotan, sin fallar. Los NUL se fijan en `nulInAKnownFieldIsADamagedRow` y en `CsvTest.nulAndControlCharactersAreKeptByTheParser`.
- **Las URL se guardan como texto**, sea cual sea su esquema. La misma prueba usa `javascript:`, `intent:`, `file:` y `content:`, y las cuatro se guardan tal cual en `url`. El paquete no crea ningún `Intent`, `Uri` ni enlace. `java.net.URI` solo se usa para sacar el host del título (`CsvFormats.kt:75-83`), con su error capturado. `plainReportText` y el lector no se basan en `fromHtml`, Linkify ni WebView.
- **El título del informe pasa por un helper:** `plainReportText(text, maxChars = MAX_TITLE)`, en `ImportModel.kt:91`. Convierte cada carácter de control (C0, C1, U+2028 y U+2029) en un espacio y quita los de dirección (U+202A–U+202E, U+2066–U+2069, U+200E, U+200F y U+061C, la misma lista que `withoutBidiControls`). Después corta sin partir un par sustituto. `ImportReadBuilder.skip` lo aplica siempre (`ImportModel.kt:131`), y la tarea 9 puede usarlo para cualquier otro texto que muestre. Sus pruebas son `ImportModelTest.plainReportTextDropsBidiAndTurnsControlsIntoSpaces`, `plainReportTextIsBoundedWithoutSplittingAPair`, `theBidiListIsTheOneOfWithoutBidiControls` y `skippedTitlesAlwaysGoThroughThePlainText`, además de `CsvFormatsTest.hostileTextIsStoredExactlyAndTheReportIsPlain`.
- **otpauth:** se lee siempre con `readOtpAuth` y después se comprueba `isSupported()` (`ImportModel.kt:145-155`). Los ajustes no admitidos se rechazan, nunca se cambian. La prueba `appleBrokenOtpAuthIsBrokenAndNeverACrash` da `Skipped` con su motivo para un `%ZZ`, una clave inválida, `digits=seis`, `MD5`, un enlace de 5 000 caracteres y `javascript:`, sin fallar.
- **Nada del archivo decide qué código se ejecuta.** El mapeo de columnas es fijo: unos nombres constantes (`CsvFormats.kt:17-27`) y un `when` cerrado sobre `ImportFormat` (`CsvFormats.kt:49-58`, `:96-110`). No hay reflexión ni decodificación polimórfica, y kotlinx.serialization no se usa en el paquete. La única reflexión del cambio está en la prueba `CsvTest.allocated`, para leer la memoria reservada en la JVM de escritorio.

## Verificación

Después del último cambio, la suite completa:

```
export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
BUILD SUCCESSFUL in 4m 5s
app/build/test-results/testDebugUnitTest: 1481 pruebas, 0 fallos, 0 errores, 0 saltadas (1423 antes de esta tarea + 58)
lint-results-debug.xml: 16 avisos (14 en gradle/libs.versions.toml, 1 en AndroidManifest.xml y 1 en mipmap-anydpi-v26), 0 errores, ninguno en imports/

gradle -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL in 1m 40s
jvm-check/build/test-results/test: 1476 pruebas, 0 fallos, 0 errores, 0 saltadas (imports/ compila y pasa: 58)
```

Ningún aviso del compilador de Kotlin (`w:`) en los archivos de `imports/`, ni en `main` ni en las pruebas.

**Arreglos de la verificación,** antes de la última ejecución:
1. **`:app:compileDebugUnitTestKotlin`:** `java.lang.management` no está en el classpath de las pruebas de Android. La sonda de memoria de `CsvTest` lo busca ahora por su nombre.
2. **Lint `ByteOrderMark` (error):** se había escrito un U+FEFF literal en `CsvFormats.kt:30`, y ahora es el escape `"\uFEFF"`. Además, todos los caracteres invisibles (de formato, de dirección, separadores y de control) que habían quedado literales en `ImportModelTest.kt` y `CsvFormatsTest.kt` pasaron a escapes `\uXXXX`. Así el código fuente no lleva caracteres de dirección escondidos («Trojan Source»). Un script comprobó que no queda ninguno en `imports/`.
3. **Tres avisos del compilador en las pruebas:** `File.parentFile` puede ser null, y `Thread.id` está obsoleto (se suprime con su motivo, porque `threadId()` necesita Java 19).

## Paso 5: pendiente

Las exportaciones reales del dueño no han llegado. Los tres CSV se han hecho a mano a partir de la documentación pública de las cabeceras (Chromium, Firefox y Apple), con datos inventados. No se ha copiado nada de ningún otro importador. Cuando lleguen los archivos reales, hay que añadirlos en `import/csv/`, con una prueba por archivo que cuente las entradas por tipo y compruebe dos títulos (`README.txt` de los archivos de prueba).

## Revisión propia

- Se repasó el diff entero. Un primer `plainReportText` se hizo con `map{}.joinToString`: era lineal, pero metía cada carácter en un objeto. Se cambió por una sola pasada que para en `maxChars` y no reserva más.
- Las firmas del brief se mantienen. Lo añadido es `internal`, salvo `plainReportText`, que es público porque la tarea 9 lo usa en la pantalla.

## Dudas

1. **`ImportTextsTest` y «Google».** Ya hay literales con «Google» fuera del paquete: `VaultEntryScreen.kt:249, :310, :536` («Google Authenticator») y `ToolTexts.kt:648`. Mi `ImportTextsTest` comprueba todos los nombres dentro de `imports/` y, en el resto de `main`, todos menos «google». La tarea 9 tiene que decidir si esos textos se quedan o se mueven. La regla R2 tal como está escrita («sobre `app/src/main/java` excepto ese archivo») fallaría con ellos.
2. **Títulos con caracteres de dirección.** Se guardan en la entrada tal como se leen, por la instrucción «store field text exactly». La lista de la bóveda los mostrará como cualquier título escrito a mano. Si se quiere evitar que un título importado se haga pasar por otro, habría que pasar `plainReportText` o `withoutBidiControls` también al título de la entrada. Es una decisión para el controlador, porque cambiaría «exacto».
3. **Una fila de Apple con un campo demasiado largo** deja fuera también su 2FA, porque va toda a `FIELD_TOO_LONG`. Si solo es demasiado larga la OTPAuth, entra el LOGIN y queda fuera el 2FA.
4. **El tope de filas:** `IMPORT_MAX_ITEMS` cuenta filas del archivo, no entradas que salen. Un CSV de Apple con 20 000 filas, todas con 2FA, da 40 000 elementos. La tarea 8 comprueba si caben en una copia.
5. **Memoria en el peor caso**, con 16 MiB de texto válido: los bytes (16 MiB), la String (de 16 a 32 MiB) y las celdas (como mucho, el mismo texto otra vez). Queda bajo el montón normal, pero no es poca. Cabe en el tope del diseño, de 16 MB.

## Revisión experto-seguridad-cripto

Repasé `references/auditoria.md`, §4 (inyección, excepciones, ReDoS y registro), sobre el diff:
- **ReDoS:** el paquete de producción no tiene ninguna regex (`Csv.kt`, `CsvFormats.kt`, `TextDecode.kt` y `Detect.kt`). El lector es una máquina de estados lineal (`Csv.kt:77-127`).
- **Inyección y deserialización:** el mapeo es fijo, sin reflexión ni decodificación polimórfica (`CsvFormats.kt:17-58`, `:96-110`). Las URL no se convierten en enlaces ni en Intents (ver (c)). La inyección de fórmulas CSV no aplica, porque Clavero no exporta CSV, y el texto se guarda sin interpretar.
- **Excepciones (fail-closed):** `Detect.kt:29-39`. Ningún mensaje de una excepción llega al usuario; solo las constantes de `ImportTexts.kt`. `Error` no se captura. Lo evitan los topes de la tabla.
- **Registro y fugas:** no hay `Log`, `println` ni `printStackTrace` en `imports/`. Los mensajes de `ImportFailure` son constantes, así que ningún valor del archivo llega a una excepción. `Skipped.title` es solo el título, en texto plano (`ImportModel.kt:131`).
- **Agotamiento de recursos (dueño (a)):** ver la tabla de topes. El tamaño se comprueba antes de decodificar, y las columnas, las celdas, las filas y la longitud de cada campo dentro del lector, con pruebas de tiempo y de memoria.
- **TOCTOU (dueño (b)):** no aplica en esta tarea. Los lectores son funciones puras sobre un `ByteArray` que el llamador ya leyó una sola vez, sin reabrir nada.
- **CRY-B02:** el 2FA solo entra con `OtpAuthRead.Usable` y `isSupported()` (`ImportModel.kt:147-149`); si no, va a `Skipped`.
- **Datos en memoria:** los búferes temporales se sobrescriben (`Csv.kt:141-146` y `TextDecode.kt:55`). Las String de Kotlin no se pueden borrar: es el riesgo aceptado del diseño («Lo que no se protege»).

## Revisión Ingeniero Móvil Senior

- **Kotlin puro y probado en `jvm-check`:** `imports/` no importa nada de Android, y `jvm-check` lo compila y prueba (`jvm-check/build.gradle.kts` solo excluye `ui/`, `android/`, `MainActivity` y `SeguridadApp`).
- **Hilo:** `readImport` es síncrona y bloquea mientras lee, hasta 16 MiB. La tarea 9 tiene que llamarla fuera del hilo principal (`Dispatchers.Default`), desde el ViewModel. Lo dice el KDoc del diseño («El cálculo… fuera del hilo principal»).
- **Rendimiento:** 16 MiB en menos de 1 s en la JVM. Una pasada para el separador (solo la primera línea), otra para la cabecera y otra para todo el archivo. No hay crecimiento cuadrático: el StringBuilder se reutiliza y las celdas vacías comparten `""` (`Csv.kt:137`).
- **API para las tareas 7 a 10:** `TextReader` y `READERS` (`Detect.kt:8`, `:14-16`), e `ImportReadBuilder` con `add`, `skip`, `becameNote` y `addOtpAuth` (`ImportModel.kt:119-157`). Los lectores JSON consiguen así los mismos índices, títulos y reglas del 2FA.
- **Sin estado global mutable.** Los modelos son `data class` inmutables (`ImportModel.kt:13-64`).

## Textos para el Abogado Digital (revisión final)

Todos están en `app/src/main/java/app/seguridad/imports/ImportTexts.kt`, salvo los de `SkipReason`, que están en `ImportModel.kt:31-39`:
- Etiquetas de origen, que se ven como etiquetas: «importado-navegador», «importado-firefox», «importado-apple», «importado-bitwarden», «importado-aegis», «importado-2fas» e «importado-otpauth». Cuatro nombran otros productos y se verán en la bóveda del usuario.
- Motivos de «no entran»: «Passkey: Clavero no las guarda», «Clave SSH: Clavero no las guarda», «2FA con ajustes que Clavero no admite», «Código que no es TOTP (HOTP, Steam…)», «Un campo pasa del tamaño máximo», «Fila o elemento dañado» y «Vacío».
- Mensajes: «El archivo pasa de 16 MB. Clavero no lo lee.», «El archivo está dañado o no es texto.», «Clavero no reconoce este archivo.», «El archivo tiene más de 20 000 elementos: la bóveda resultante no cabría en una copia.», «El archivo está dañado: unas comillas no se cierran.», «El archivo tiene filas con más de 64 columnas. Clavero no lo lee.», «El archivo tiene demasiadas celdas. Clavero no lo lee.» y «El archivo está dañado o Clavero no sabe leerlo.».
- «Acceso» (`EntryType.LOGIN.label`, que ya existía) como título de reserva.

---

## Ronda de arreglos 1 (revisión `task-6-review.md`; R8 y R8a)

**Commit:** `afeb362` «1.4.0: importar, arreglos de la revisión de la tarea 6» (sobre `754e200`), subido a `origin/ccr-a1a6eabb-765f9g`.

### I1: la lista exacta de R8 y R8a en lugar de eximir la palabra

- `ImportTextsTest.kt:27` `ALLOWED`: pares (ruta bajo `app/src/main/java`, literal exacto, tal como lo da `kotlinLiterals`). Contiene:
  - los tres de «Google Authenticator» de `VaultEntryScreen.kt` (`:249`, `:310` y `:536`);
  - la cadena entera de `ToolTexts.kt:646-648` («… la copia de Google …»);
  - la atribución de Guava de `LicensesScreen.kt:31`.

  Ninguna palabra queda exenta. Fuera de `imports/ImportTexts.kt`, cualquier otro literal con un nombre ajeno falla, también en esos mismos archivos.
- **Pruebas nuevas:**
  - `everyAllowedLiteralStillExists`: cada elemento de la lista sigue en su archivo, así que la lista no se puede quedar vieja;
  - `aNewLiteralWithANameFailsWhereverItIs`: un literal nuevo con «Google» en `VaultEntryScreen.kt`, uno permitido pero en otro archivo, y «Bitwarden» en `imports/Detect.kt` fallan; `ImportTexts.kt` y el literal permitido en su archivo pasan;
  - `theCheckFindsANameInALiteral`, que ahora incluye «GOOGLE».
- **Mutación real:** cambié `BackupCheckTexts.kt:16` a `"Elegir copia de Google Drive"` y ejecuté `gradle -p jvm-check test --tests 'app.seguridad.imports.ImportTextsTest'`:
  ```
  ImportTextsTest > otherProductNamesOnlyInImportTexts FAILED
      app/seguridad/vault/BackupCheckTexts.kt:16 [google] «Elegir copia de Google Drive»
  BUILD FAILED in 22s
  ```
  Después lo devolví a su estado con `git checkout -- …/BackupCheckTexts.kt` y comprobé que no tenía cambios.

### I2: `plainReportText`, por punto de código (`ImportModel.kt:83-132`)

- Quita **todo Cf** (`Character.getType == FORMAT`). Eso incluye U+200B–U+200D, U+FEFF, U+2060–U+2064, U+00AD, U+180E, U+061C y los de dirección, que también son Cf, así que `isBidiControl` desaparece. También quita **todo el bloque de etiquetas U+E0000–U+E007F**, sus puntos sin asignar incluidos (`TAG_CHARACTERS`, `:129`), porque solo U+E0001 y U+E0020–E007F son Cf. Y quita los anotadores **U+FFF9–U+FFFB**, el CGJ U+034F, los rellenos de Hangul U+115F, U+1160, U+3164 y U+FFA0 (`INVISIBLE_NOT_FORMAT`, `:132`) y cualquier surrogate suelto.
- Como mucho **`MAX_COMBINING_MARKS = 3`** marcas Mn o Me seguidas sobre una base (`:84`, documentado): bastan para los acentos y los tonos de cualquier lengua (el vietnamita pone dos). Las demás se quitan.
- Los Cc, U+2028 y U+2029 siguen pasando a espacio. El corte es en unidades UTF-16 (`maxChars`) y siempre al final de un punto de código, sin partir nunca un par.
- El KDoc lo dice todo, también que se quita el ZWJ (una familia se ve como sus emoji sueltos) y que los homoglifos no se arreglan, sin prometer más.
- **Pruebas** (`ImportModelTest`):
  - `plainReportTextDropsInvisibleFormatCharacters`: cada carácter invisible en «ban…co», las 128 etiquetas, U+FFF9–U+FFFB, el CGJ, los rellenos y un surrogate suelto, y todo lo que quita `withoutBidiControls`, recorriendo los 65 536 `Char`;
  - `plainReportTextKeepsOnlyAFewCombiningMarksOnOneBase`: «e» con 1 023 U+0301 da «e» con 3; Me con U+20DD; dos marcas en vietnamita;
  - `ordinaryTextComesOutUnchanged`: español con «ñ», «ü» y signos de apertura, un acento en NFD, emoji con tono de piel, VS16 y bandera, cirílico, CJK, árabe, hebreo y una URL salen igual; la familia con ZWJ da sus tres emoji;
  - `plainReportTextIsBoundedWithoutSplittingAPair` y `plainReportTextDropsBidiAndTurnsControlsIntoSpaces` (ahora con el tabulador).

### M1: ninguna lista por línea en blanco (`Csv.kt:72`, `:89`, `:137`, `:144-151`)

La fila se crea en su primera celda (`endField`), y una línea sin nada no reserva nada. La prueba `CsvTest.sixteenMegabytesOfBlankLinesAllocateNothingPerLine` usa una cabecera más 16 MiB de `\n`, de `\r` y de `\r\n`: da una sola fila y menos de **1 MiB** reservado (antes eran unos 400 MiB). Además, 16 MiB de saltos antes de la cabecera se leen bien.

De paso, `csvDelimiter` (`Csv.kt:43-64`) salta las líneas en blanco del principio, como el lector. Antes, un archivo con un salto antes de una cabecera con `;` habría tomado `,`.

### M2: la sonda ya no salta la prueba (`CsvTest.allocated` y `assertBelow`)

`allocated` ejecuta siempre el bloque y devuelve `null` si no hay sonda. Así, los mensajes (`TOO_MANY_COLUMNS` y `TOO_MANY_CELLS`), el resultado de `readImport` y el `timeout` se comprueban siempre, y solo los topes de memoria dependen de HotSpot. Se quitó `assumeTrue`.

### M3: quién borra `bytes` y `password` (`Detect.kt:27-30`)

El contrato del KDoc:
- el llamador es dueño de los dos;
- `readImport` no los cambia, no los guarda y no los borra, para que se pueda volver a llamar con la contraseña sobre los mismos bytes;
- el llamador los pone a cero en un `finally` (`bytes.fill(0)` y `password.fill('\u0000')`), también si falla;
- lo que `readImport` crea por su cuenta (el búfer del decodificador y el del lector) lo borra ella.

La prueba `DetectTest.theCallerOwnsBytesAndPassword` comprueba que los dos quedan intactos tras un `Ok` y tras un `Failed`, y que una segunda llamada da lo mismo. La tarea 9 (el ViewModel) es la que borra.

### M4: el separador en los dos sentidos (`CsvTest.semicolonFromTheHeaderIsTheDelimiter`)

- `csvDelimiter("\"a,b\";c;d\n1;2;3") == ';'`, y la lectura da `["a,b","c","d"]` y `["1","2,5","3"]`;
- con un empate o sin separador, `,`;
- `";"` después de líneas en blanco.

### Verificación tras el último cambio

Pruebas acotadas: `gradle -p jvm-check test --tests 'app.seguridad.imports.*' --tests 'app.seguridad.tools.SourceTextsTest'` dio BUILD SUCCESSFUL. Con la suite completa, el resultado por clase es el mismo en las dos:

```
CsvTest 24, CsvFormatsTest 18, DetectTest 10, ImportModelTest 8, ImportTextsTest 4, SourceTextsTest 12 (0 fallos, 0 saltadas)

export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
BUILD SUCCESSFUL in 4m 15s
app: 1487 pruebas, 0 fallos, 0 errores, 0 saltadas
lint: 16 avisos (14 en libs.versions.toml, 1 en AndroidManifest.xml y 1 en mipmap-anydpi-v26), 0 errores, ninguno en imports/

gradle -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL in 1m 38s
jvm-check: 1482 pruebas, 0 fallos, 0 errores, 0 saltadas
```

No hay avisos `w:` del compilador en `imports/`. Un script comprueba que ningún archivo de `imports/` lleva caracteres invisibles literales (Cf, Cc, Zl, Zp, Mn, Me, surrogates ni rellenos): todos van como `\uXXXX`.

### Revisión experto-seguridad-cripto (ronda 1)

- **I2:** quitar los caracteres de formato invisibles y limitar las marcas combinantes cierra la suplantación por caracteres ocultos y las torres que tapan líneas, en el helper que R9 va a usar en toda la app (`ImportModel.kt:104-124`). Los homoglifos quedan fuera, y lo dice el KDoc. Es una sola pasada lineal, sin regex, acotada por `maxChars`.
- **M1:** quita el último caso de basura proporcional a la entrada en un archivo hostil (`Csv.kt:144-151`).
- **M3:** el contrato de borrado queda escrito y probado (`Detect.kt:27-30`). La responsabilidad pasa a la tarea 9, que borra en un `finally`.
- **I1:** la guardia de nombres ajenos ya no tiene ninguna excepción por palabra (`ImportTextsTest.kt:27-34`, `:57-61`), y la mutación demuestra que muerde.

### Revisión Ingeniero Móvil Senior (ronda 1)

- La reserva perezosa de la fila (`Csv.kt:72`, `:144-146`) quita millones de objetos de corta vida que el GC de ART tendría que recoger con un archivo de líneas en blanco.
- `plainReportText` usa `codePointAt` y `appendCodePoint`, sin listas intermedias, y para en `maxChars`. Se puede llamar en la composición de cada fila de una lista.
- La prueba de las comas ya no depende de HotSpot para nada más que los topes de memoria (M2).

### Textos para el Abogado Digital (ronda 1)

No hay textos nuevos que vea el usuario. La lista exacta de R8 y R8a (los tres «Google Authenticator», «la copia de Google» y la atribución de Guava) ya figura en la lista final.

---

## Ronda de arreglos 2 (`task-6-rereview-1.md`; R10)

**Commit:** `705b65b` «1.4.0: importar, segunda ronda de arreglos de la tarea 6», sobre `afeb362`, subido a `origin/ccr-a1a6eabb-765f9g`. Por R10, no se tocan la eliminación del ZWNJ ni el tope de 3 marcas.

### 1. `namesIn` reconoce los nombres pegados a otras letras (`ImportTextsTest.kt:20-27`, `:65-69`)

- Los nombres se buscan **como subcadena, sin distinguir mayúsculas**, en cada literal. Así se detectan «GoogleDrive», «misBitwarden», «copia2FASx», «iCloudDrive», «MicrosoftEdge» y «msedge.exe». La lista exacta de R8 y R8a no cambia.
- **Nombres cortos con falsos positivos:** busqué todas las subcadenas en los literales de `main`. El único falso positivo era «edge», dentro de «Ledger» (`"Nombre (p. ej. «Binance» o «Ledger»)"`), y en inglés también lo está en «knowledge».
  - «edge» va aparte (`wholeWordNames`) y solo cuenta como palabra entera.
  - Las formas pegadas de Edge se detectan con «microsoft» y «msedge», que ahora están en la lista de subcadenas.
  - Ninguno de los demás nombres (aegis, apple, brave, steam, chrome, etc.) forma parte de una palabra común en español. Si alguno llegara a coincidir, la prueba fallaría, así que el error es en el sentido seguro.
- **Pruebas:** en `theCheckFindsANameInALiteral`, las formas pegadas de arriba y «Edge» dan positivo; «Ledger» y «knowledge» no.
- **Mutación:** cambié `BackupCheckTexts.kt:16` a `"Elegir copia de GoogleDrive"`:
  ```
  ImportTextsTest > otherProductNamesOnlyInImportTexts FAILED
      app/seguridad/vault/BackupCheckTexts.kt:16 [google] «Elegir copia de GoogleDrive»
  BUILD FAILED in 20s
  ```
  Después lo devolví a su estado con `git checkout` y comprobé que no tenía cambios.

### 2. El separador sale de la propia lectura del lector (`Csv.kt:43-55`, `:66-81`, `:144-160`)

- `csvDelimiter` ya no lleva su propia regla de comillas. Para cada candidato (`;` y `,`), `csvFirstRecordCells(text, d)` lee el primer registro no vacío con el mismo `CsvReader` en un modo que solo cuenta (`countFirstRecord`: no guarda nada ni aplica topes, porque la cuenta nunca pasa del largo del texto). Así, las comillas solo abren al principio de un campo, igual que en el lector.
- Gana `;` si da más celdas, y `,` en un empate, como antes.
- Unas comillas sin cerrar terminan la cuenta al final del texto. La lectura completa da luego `IMPORT_CSV_UNCLOSED_QUOTE`.
- **Pruebas:**
  - `CsvTest.delimiterDetectionUsesTheParsersQuoteRule`:
    - `ab"c;d;e` da `;` y la lectura `[ab"c, d, e]`;
    - `na"me;url,x` da `,` y `["na\"me;url", "x"]`;
    - `na"me;url;x,y` da `;` y `["na\"me", "url", "x,y"]`;
    - unas comillas al principio de un campo siguen abriendo (`"a;b;c",d`, `x;"a,b";d;e`).
  - `CsvTest.delimiterDetectionAndParseAgreeOnAnyHeader`: 20 000 textos al azar, repetibles (semilla 6), de hasta 13 caracteres de `ab,;" \n\r`. El separador elegido es el que da más celdas, y la lectura da exactamente esas celdas en su primera fila (o `IMPORT_CSV_UNCLOSED_QUOTE`).
  - Los casos de antes siguen pasando: `"a;b",c`, `"a,b";c;d`, el empate, «nombre» y las líneas en blanco iniciales.
- **Memoria:** el modo de contar no reserva nada. `sixteenMegabytesOfCommasIsRefusedFastWithBoundedMemory` y `sixteenMegabytesOfBlankLinesAllocateNothingPerLine` siguen en verde, con sus topes de reserva.

### Verificación tras el último cambio

```
gradle -p jvm-check test --tests 'app.seguridad.imports.*' --tests 'app.seguridad.tools.SourceTextsTest'
BUILD SUCCESSFUL in 24s

export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
BUILD SUCCESSFUL in 4m 11s
app: 1489 pruebas, 0 fallos, 0 errores, 0 saltadas (CsvTest 26, CsvFormatsTest 18, DetectTest 10, ImportModelTest 8, ImportTextsTest 4, SourceTextsTest 12)
lint: 16 avisos (14 en libs.versions.toml, 1 en AndroidManifest.xml y 1 en mipmap-anydpi-v26), 0 errores, ninguno en imports/

gradle -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL in 1m 34s
jvm-check: 1484 pruebas, 0 fallos, 0 errores, 0 saltadas
```

No hay avisos `w:` del compilador en `imports/`, ni caracteres invisibles literales (comprobado con el script).

### Revisión experto-seguridad-cripto (ronda 2)

- La guardia de nombres ajenos ya no deja pasar un nombre pegado (`ImportTextsTest.kt:65-69`). La única excepción por palabra entera («edge») está escrita, con su motivo y su alternativa.
- Detectar el separador y leer ya no pueden discrepar: comparten la máquina de estados (`Csv.kt:48-55`, `:71-81`). Sigue siendo lineal, sin regex, y sin reservar memoria al contar (`Csv.kt:144-160`).

### Revisión Ingeniero Móvil Senior (ronda 2)

- El coste añadido son dos pasadas por el primer registro (una por candidato), que no reservan memoria. Con 16 MiB de comas, la prueba sigue en su `timeout` y por debajo de 1 MiB reservado.
- No cambia ninguna API pública. `csvFirstRecordCells` es `internal`.

### Textos para el Abogado Digital (ronda 2)

No hay textos nuevos que vea el usuario. Una nota fuera del alcance de esta tarea: el literal anterior `"Nombre (p. ej. «Binance» o «Ledger»)"` nombra dos productos como ejemplo, y la regla de nombres ajenos no los cubre porque no son de importar.
