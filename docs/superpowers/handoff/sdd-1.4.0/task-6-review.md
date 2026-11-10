# Tarea 6: revisión (spec + calidad), 364d4f7..754e200

### Spec Compliance

- ✅ **Spec compliant**, con una excepción, la de la decisión R8 (I1 abajo). Comprobado, firma por firma, contra el brief:
  - `ImportFormat`, `ImportedItem`, `SkipReason` (los siete textos, letra por letra), `Skipped`, `ImportRead`, `ReadResult`, `ImportFailure`, `readImport`, `decodeText`, `parseCsv` y las ocho constantes (`ImportModel.kt:13-64`, `ImportLimits.kt:9-19`, `Detect.kt:27`, `TextDecode.kt:20`, `Csv.kt:20`).
  - **R2/R7:** «Steam» (`SKIP_NOT_TOTP`), los siete `originTag` e «Clavero no reconoce este archivo.» son constantes de `ImportTexts.kt:11-29`. El enum las usa por su nombre (`ImportModel.kt:13-21`, `:35`). Ningún otro literal de `imports/` nombra un producto: `grep` hecho, y `ImportTextsTest` lo comprueba.
  - **R9:** los títulos guardados quedan tal cual (`CsvFormats.kt:123`, comprobado en `CsvFormatsTest.hostileTextIsStoredExactlyAndTheReportIsPlain`). Solo `Skipped.title` pasa por `plainReportText` (`ImportModel.kt:131`).
  - **Tabla «Entradas que se crean»:** un acceso con TOTP da un LOGIN y después un TOTP con el mismo título y las mismas carpetas (`CsvFormats.kt:123-124`, `ImportModel.kt:145-155`). En Firefox, el título es el host (`CsvFormats.kt:109`). Se leen las notas de Apple (`CsvFormats.kt:102`). Las columnas desconocidas no se leen nunca (`Columns.cell` solo con los nombres de `COL_*`). Un 2FA no admitido deja entrar el LOGIN y manda el 2FA a «no entran».
  - **Nombres de las pruebas:** los del brief están todos: `CsvTest` cubre los nueve casos, `CsvFormatsTest` las tres cabeceras y `unknownColumnsAreIgnored`, `columnsInAnyOrderAndCase`, `fieldOverLimitIsSkipped` y `rowWithNothingIsEmpty`, y `DetectTest` `over16MbIsRefused`, `unknownFileSaysSo` (con el mensaje exacto) e `itemCountOverLimitIsRefused` (20 000 entran y 20 001 no).
  - **Lo añadido** (`ImportTexts.kt`, `CSV_MAX_*`, `ImportReadBuilder`, `plainReportText`, `TextReader`) responde a R2 y a las decisiones (a) y (c) del dueño. No es sobreingeniería.
- ❌ **R8, sin cumplir:** `ImportTextsTest.kt:16` y `:40` eximen la **palabra** «google» en todo `main` fuera de `imports/`, no los cuatro literales exactos. Ver I1.
- ⚠️ **No se puede comprobar desde el diff:**
  - Paso 5: faltan los CSV reales del dueño. Las cabeceras solo se han comprobado con la documentación.
  - Tarea 8: `IMPORT_MAX_ITEMS` cuenta filas, no elementos. Mi prueba: 20 000 filas de Apple con OTPAuth dan **40 000 elementos** en `ReadResult.Ok`. Coincide con el brief y con la spec («filas o elementos»), así que no es un hueco de esta tarea, pero la tarea 8 tiene que acotar el resultado fusionado. Ya está apuntado en `progress.md`.
  - Tarea 9: tiene que leer con `readAtMost(IMPORT_MAX_BYTES + 1)` y llamar a `readImport` fuera del hilo principal.
  - R8: una lista exacta con solo los cuatro literales que nombra R8 haría fallar la prueba por `LicensesScreen.kt:31` (`"https://github.com/google/guava, …"`, en la atribución de la licencia de Guava), que hoy pasa gracias a la exención por palabra. El controlador tiene que decidir si ese literal entra en la lista exacta. Es una atribución obligatoria y no se puede quitar.

### Strengths

- **El lector CSV es una máquina de estados lineal de una sola pasada** (`Csv.kt:77-127`). No tiene ninguna regex. Cada campo se acota mientras se construye, en un `StringBuilder` que se reutiliza (`Csv.kt:129-131`), y las columnas, las celdas y las filas se comprueban dentro del lector (`Csv.kt:70`, `:134-135`). Mis pruebas, con las clases compiladas y `-Xmx256m`:
  - 16 MiB de comas, de `"\n"\n` o de `""\n`, de comillas, con comillas sin cerrar, y 64 celdas de `a` por fila: todo se rechaza o se lee en 16-214 ms, reservando de 16 a 31 MiB (el texto incluido);
  - 16 MiB válidos de Chromium (19 854 filas): 147 ms y 42 MiB reservados.
- **Topes y orden de las comprobaciones correctos:**
  - el tamaño se mira antes de decodificar (`Detect.kt:30`, y la prueba usa 0xFF para demostrar el orden);
  - la decodificación es estricta y usa un búfer fijo que se borra (`TextDecode.kt:33-57`);
  - ninguna `ImportFailure` sale de `readImport`, y ninguna otra excepción deja ver su mensaje (`Detect.kt:31-38`, comprobado con un secreto en `DetectTest.readerExceptionsNeverEscape`).
- **CRY-B02 al pie de la letra** (`ImportModel.kt:145-155`): `readOtpAuth`, después `isSupported`, y lo no admitido va a `Skipped`, nunca se cambia. HOTP y Steam van a `NOT_TOTP` y el resto a `BROKEN`. `readOtpAuth` acota y captura su propio error de análisis (`crypto/Totp.kt:190-221`).
- **Nada se interpreta:**
  - `imports/` no tiene `Intent`, `Uri`, Linkify, `fromHtml`, `Log`, `println`, reflexión ni serialización (`grep` hecho);
  - el mapeo de columnas es un `when` cerrado (`CsvFormats.kt:49-58`, `:96-110`);
  - `URISyntaxException`, que lleva la entrada en su mensaje, se captura y se descarta (`CsvFormats.kt:79-82`).
- **El título de reserva nunca usa la URL entera,** que podría llevar `usuario:clave@`. Hay una prueba con `https://user:secreto@…`.
- **Las pruebas comprueban comportamiento real:** comprueban la sonda de memoria antes de usarla, los límites justo en el tope y uno más, los valores hostiles guardados tal cual y los índices únicos.

### Issues

#### Critical (Must Fix)

Ninguno.

#### Important (Should Fix)

**I1. R8 no se cumple: la prueba exime la palabra «google» en general.** (`app/src/test/java/app/seguridad/imports/ImportTextsTest.kt:16`, `:40`)
- **Qué pasa:** `usedBefore = setOf("google")` y `filter { inImports || it !in usedBefore }` dejan pasar cualquier literal nuevo con «Google» fuera de `imports/`, por ejemplo un «Importar de Google Contraseñas» en una pantalla de las tareas 9 o 11. R8 pide exactamente lo contrario: una lista por cadena exacta, para que solo pasen los literales anteriores a la 1.4.0.
- **Por qué importa:** la regla de los nombres ajenos (la decisión 9 del dueño, R2 y la Global «Third-party names») queda sin guardia justo para la marca que más se usa.
- **Arreglo:** `usedBefore` como un conjunto de pares (archivo, texto exacto del literal, tal como lo da `kotlinLiterals`):
  - los literales de `VaultEntryScreen.kt:249`, `:310` y `:536`;
  - el de `ToolTexts.kt:648`, que forma parte de la cadena `VAULT_CREATE_INTRO`;
  - y, si el controlador lo acepta, el de `LicensesScreen.kt:31` (ver ⚠️).

  Una prueba debe comprobar que cada elemento de la lista sigue existiendo, para que no se quede vieja. Y otra, que un literal nuevo con «Google» falla (por ejemplo, `namesIn` aplicado a una cadena de prueba, con la lista exacta).

**I2. `plainReportText` deja pasar caracteres de formato invisibles y secuencias largas de marcas combinantes.** (`app/src/main/java/app/seguridad/imports/ImportModel.kt:91-101`, y el KDoc de `:84-90`)
- **Qué pasa:** los probé con las clases compiladas y se conservan todos:
  - U+200B, U+FEFF, U+2060–U+2064, U+00AD, U+180E y U+034F;
  - U+FFF9–U+FFFB, los anotadores interlineales, que algunos motores de texto usan para esconder texto;
  - los caracteres de etiqueta U+E0000–U+E007F, suplementarios (`a󠁁b` sale igual);
  - una «e» seguida de 1 023 U+0301.
- **Por qué importa:**
  - El KDoc dice «a crafted title cannot make one line look like another». Con U+200B, «banco» y «ban​co» se ven iguales y son distintos. Una torre de 1 000 marcas combinantes se dibuja fuera de su línea en Android, porque la altura de la línea sale de las métricas de la fuente, y tapa las líneas de al lado del informe.
  - Por R9, este es el helper que la tarea 9 va a usar para cada título, usuario y URL de las listas, no solo en «no entran».
  - El modelo de amenaza cuenta, entre los atacantes, a quien busca «colar entradas que suplanten otras». La skill pide Important como mínimo para un hallazgo de seguridad confirmado.
- **Arreglo:** recorrer el texto por puntos de código y:
  - quitar todo `Character.getType(cp) == Character.FORMAT` (Cf), salvo U+200C y U+200D, que hacen falta en los emoji y en el persa y los textos índicos, o como mucho uno de ellos seguido;
  - conservar la lista bidi actual (ya es Cf);
  - limitar las marcas `NON_SPACING_MARK` y `ENCLOSING_MARK` seguidas sobre una misma base, por ejemplo a 4 u 8, y quitar las demás.

  Además: añadir esos casos a `ImportModelTest` y ajustar el KDoc a lo que hace. Los homoglifos, como la «а» cirílica, no se arreglan quitando caracteres. Eso queda para la etiqueta de origen visible, y el KDoc no debe prometer más.

#### Minor (Nice to Have)

**M1. Cada línea en blanco reserva un `ArrayList`.** (`app/src/main/java/app/seguridad/imports/Csv.kt:78`, `:124`)
- **Qué pasa:** `val row = ArrayList<String>()` se crea antes de saber si la línea tiene algo. 16 MiB de `\n`, o de `\r`, con o sin cabecera, reservan **400 MiB de basura**: 16 M de objetos, en 131-197 ms en la JVM, medido con `ThreadMXBean`. La memoria retenida sigue acotada, así que no hay OOM. Pero en un móvil son millones de objetos para el GC y unos segundos de trabajo con un archivo hostil de 16 MB.
- **Arreglo:** crear la fila solo cuando aparece contenido (`lineHasContent`) o reutilizar una lista y copiarla al devolverla. Añadir el caso «16 MiB de saltos de línea» a `sixteenMegabytesOfCommasIsRefusedFastWithBoundedMemory`, con un tope de reserva.

**M2. Las comprobaciones de la prueba de las comas dependen de la sonda HotSpot.** (`app/src/test/java/app/seguridad/imports/CsvTest.kt:163-168`, `:182`)
- **Qué pasa:** `allocated` hace `assumeTrue` en la primera línea de `sixteenMegabytesOfCommasIsRefusedFastWithBoundedMemory`. En una JVM sin `com.sun.management.ThreadMXBean`, se salta la prueba entera, también los mensajes (`TOO_MANY_COLUMNS` y `TOO_MANY_CELLS`) y el `timeout`.
- **Arreglo:** comprobar los mensajes y el tiempo fuera de `allocated`, y dejar que solo los topes de memoria dependan de la sonda.

**M3. `readImport` no dice quién borra `bytes` y `password`.** (`app/src/main/java/app/seguridad/imports/Detect.kt:18-27`)
- **Qué pasa:** la spec dice que todo lector «pone a cero los `ByteArray` y `CharArray` que pueda (el archivo leído, la contraseña…) en cuanto termina, también si falla». `readImport` no los borra, y el KDoc no dice que eso le toque al que llama.
- **Arreglo:** escribirlo en el KDoc («el llamador es dueño de `bytes` y `password` y los pone a cero en un `finally`»), para que la tarea 9, la del ViewModel, lo haga. O bien borrarlos aquí en un `finally` y decirlo. Lo primero es más claro y no rompe las pruebas.

**M4. La detección del separador solo se prueba en un sentido.** (`app/src/test/java/app/seguridad/imports/CsvTest.kt`, `semicolonFromTheHeaderIsTheDelimiter`)
- **Qué pasa:** la prueba cubre `"a;b",c` (da `,`), pero no el caso contrario, una cabecera con `;` y un nombre entre comillas que lleva comas (`"a,b";c;d`, que debe dar `;`). El brief pide «including a quoted header that contains the other delimiter».
- **Arreglo:** añadir esa línea.

### Comprobaciones hechas

Con un programa Java de usar y tirar sobre `jvm-check/build/classes`, compiladas después de las fuentes de `754e200`, con `-Xmx256m`. El programa está en `/tmp/claude-0/-home-user-Seguridad/afa106da-72c3-5be8-bbce-c61e4a0844be/scratchpad/probe/Probe.java`.

| Entrada | Resultado |
|---|---|
| 16 MiB de `,` | `IMPORT_UNKNOWN` (la cabecera pasa de 64 columnas), 127 ms, 19 MiB |
| Cabecera + 16 MiB de `"\n"\n` o de `""\n` | `IMPORT_TOO_MANY_ITEMS`, 24-34 ms |
| Cabecera + 16 MiB de `"` | Ok: una fila `EMPTY` con un título de 1 024 caracteres, 68 ms |
| Cabecera + comillas sin cerrar en 16 MiB | `IMPORT_CSV_UNCLOSED_QUOTE`, 77 ms |
| Cabecera + 16 MiB de `\n` o de `\r` | Ok y vacío, **400 MiB reservados** (M1) |
| NUL en el campo URL | `Skipped(BROKEN)`, con el título «w» |
| Solo la cabecera, con o sin salto final | Ok y vacío |
| Columnas repetidas | Cuenta la primera |
| Cabecera de 1 MiB (un nombre de 1 MiB + columnas válidas) | Ok, con el nombre largo cortado en el lector |
| Cabecera de 1 MiB con 64 columnas sin `url` | `IMPORT_UNKNOWN` |
| Archivo de 1 MiB sin salto de línea | `IMPORT_UNKNOWN`; con cabecera, `Skipped(EMPTY)` |
| Finales de línea solo con CR | Se lee bien |
| 16 MiB válidos de Chromium | Ok, 147 ms, 42 MiB reservados |
| 20 000 filas de Apple con OTPAuth | Ok, 40 000 elementos (⚠️ para la tarea 8) |

- **Surrogates sueltos:** `decodeText` los rechaza (lo cubren las pruebas `utf16WithALoneSurrogate…` y `invalidUtf8IsRejected`, ED A0 80), así que `parseCsv` nunca los recibe desde `readImport`.
- **Memoria en el móvil, con 16 MiB válidos:** los bytes (16) + la `String` (16 en ASCII comprimido de ART, 32 con texto UTF-16) + las celdas (hasta otro tanto) dan unos 48-80 MiB retenidos mientras se lee. Es aceptable con el `heapgrowthlimit` habitual (192-512 MB). Ese archivo no cabría después en el JSON de 6 MB de la copia (tarea 8).
- **Palabras prohibidas y «wallet»:** ninguna en los textos nuevos de `ImportTexts.kt` ni en `SkipReason` (`grep`).
- **Excepciones:** las únicas de `main` que llevan mensaje son `ImportFailure` (siempre con una constante) y `require(...) { "Unsupported imported type" }` (`ImportModel.kt:126`), sin datos.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** El lector, los topes, la decodificación y las reglas de CRY-B02 están bien hechos y aguantan todas las entradas hostiles que probé, con un resultado definido y una memoria retenida acotada. Pero la prueba de nombres ajenos no sigue R8 (I1), y `plainReportText`, que R9 va a usar en toda la app, deja pasar caracteres invisibles y torres de marcas combinantes que pueden suplantar una línea o taparla (I2). Los menores M1-M4 se arreglan en la misma ronda.

## Revisión experto-seguridad-cripto

Apliqué `references/auditoria.md` §4 (inyección, ReDoS, excepciones, registro) y la decisión (c) del dueño al diff:

- **ReDoS y complejidad:** no hay regex en `main/imports`. La pasada es lineal (`Csv.kt:77-127`). `csvDelimiter` (`Csv.kt:44-58`) se para en el primer salto fuera de comillas; con unas comillas sin cerrar recorre el texto una vez, lo que sigue siendo lineal. Medido: ningún caso de 16 MiB pasa de 214 ms.
- **Agotamiento de recursos (decisión (a)):**
  - el tamaño se mira antes de decodificar (`Detect.kt:30`);
  - columnas ≤ 64, celdas ≤ 500 000, filas ≤ 20 001 y campo ≤ 65 537 caracteres, todo comprobado mientras se lee (`Csv.kt:70`, `:129-135`);
  - lo único de más es la basura de las líneas en blanco (M1).
- **Inyección y deserialización:**
  - el mapeo es fijo (`CsvFormats.kt:17-27`, `:49-58`, `:96-110`), sin reflexión ni decodificación polimórfica;
  - las URL `javascript:`, `intent:`, `file:` y `content:` se guardan como texto y nada en `imports/` crea un `Intent`, un `Uri` ni un enlace;
  - la inyección de fórmulas CSV no aplica, porque no hay exportación CSV. Queda apuntada para una futura exportación, según (c).
- **otpauth:** todo pasa por `readOtpAuth` y `isSupported` (`ImportModel.kt:146-155`), con el tope de `OTPAUTH_MAX_CHARS` antes de analizar. HOTP y Steam van a `NOT_TOTP`, los ajustes no admitidos a `TOTP_UNSUPPORTED` y nunca se cambia nada.
- **Fugas:**
  - no hay `Log`, `println` ni `printStackTrace`;
  - `readImport` nunca usa `e.message` salvo en `ImportFailure` (`Detect.kt:35-37`);
  - `Skipped.title` es el título o, de reserva, el host o el usuario, nunca la contraseña, la nota ni la clave (`CsvFormats.kt:111-121`);
  - el `toString()` de los nuevos `data class` (`ImportedItem`, `ImportRead`, `ReadResult.Ok`) muestra contraseñas, porque `VaultEntry` no lo redefine. Hoy nadie los registra. Que la tarea 9 no los meta nunca en un log ni en un mensaje.
- **Suplantación en la pantalla:** los bidi, C0, C1, U+2028 y U+2029 se tratan bien (`ImportModel.kt:91-105`, con la lista igual a la de `withoutBidiControls`, comprobada en las 65 536 posiciones). Los Cf invisibles y las marcas combinantes no (**I2**).
- **Restos en memoria:** el `StringBuilder` y el `CharBuffer` se sobrescriben (`Csv.kt:141-146`, `TextDecode.kt:55`). Las `String` no se pueden borrar, y es un riesgo aceptado en la spec. Falta decir quién borra `bytes` y `password` (M3).
- **Nada copiado de otros importadores:** el lector y los datos de prueba son propios, con datos inventados y cabeceras sacadas de la documentación pública (`README.txt` de las pruebas). No reconozco código de Bitwarden, KeePassXC ni Aegis.

## Revisión Ingeniero Móvil Senior

- **Kotlin puro y probado en `jvm-check`:** `imports/` no importa nada de Android. Las clases están compiladas en `jvm-check/build/classes/kotlin/main/app/seguridad/imports/`.
- **Hilo:** `readImport` es síncrona y tarda hasta unos 200 ms en la JVM, varias veces más en un móvil de gama baja. La tarea 9 debe llamarla en `Dispatchers.Default`, desde el ViewModel (`Detect.kt:27`).
- **Memoria:**
  - retenida, de 48 a 80 MiB en el peor caso válido (ver «Comprobaciones»), aceptable sin `largeHeap`;
  - la basura de las líneas en blanco presiona al GC de ART (M1);
  - una sola copia del texto: `decodeText` construye la `String` una vez (`TextDecode.kt:28`), y las celdas vacías comparten `""` (`Csv.kt:137`).
- **API para las tareas 7 a 10:** `TextReader`/`READERS` (`Detect.kt:8-16`) y `ImportReadBuilder` (`ImportModel.kt:119-157`) dan los mismos índices y las mismas reglas de 2FA a todos los lectores. El `require` del tipo (`ImportModel.kt:126`) se convierte en `IMPORT_BROKEN` y no hace caer la app.
- **Sin estado global mutable.** Los modelos son inmutables. `READERS` es un `private val` de solo lectura.
- **Pruebas:** M2 (la dependencia de HotSpot) y M4 (el separador en un solo sentido). Las demás comprueban comportamiento, sin mocks.

## Textos para el Abogado Digital (revisión final)

Todos son nuevos y están en `app/src/main/java/app/seguridad/imports/ImportTexts.kt`, salvo los de `SkipReason`, que están en `ImportModel.kt:31-39`:

- **Etiquetas de origen, visibles en la bóveda:** «importado-navegador», «importado-firefox», «importado-apple», «importado-bitwarden», «importado-aegis», «importado-2fas», «importado-otpauth». Cuatro llevan una marca ajena, pero como etiqueta y no en la pantalla de importar. Hay que comprobar que eso encaja con la regla «nombres ajenos solo en la pantalla de importar y en el README».
- **Motivos de «no entran»:** «Passkey: Clavero no las guarda», «Clave SSH: Clavero no las guarda», «2FA con ajustes que Clavero no admite», «Código que no es TOTP (HOTP, Steam…)» (nombra a Steam), «Un campo pasa del tamaño máximo», «Fila o elemento dañado», «Vacío».
- **Mensajes:**
  - «El archivo pasa de 16 MB. Clavero no lo lee.»;
  - «El archivo está dañado o no es texto.»;
  - «Clavero no reconoce este archivo.»;
  - «El archivo tiene más de 20 000 elementos: la bóveda resultante no cabría en una copia.» Hay que comprobar que es verdad: 20 001 filas mínimas podrían caber en los 6 MB de la copia. El espacio de «20 000» es normal y podría partirse en dos líneas; conviene U+00A0;
  - «El archivo está dañado: unas comillas no se cierran.»;
  - «El archivo tiene filas con más de 64 columnas. Clavero no lo lee.»;
  - «El archivo tiene demasiadas celdas. Clavero no lo lee.»;
  - «El archivo está dañado o Clavero no sabe leerlo.».
- **Título de reserva:** «Acceso» (`EntryType.LOGIN.label`, que ya existía).
- **Por R8, a la lista final:** los literales anteriores con «Google Authenticator» (`VaultEntryScreen.kt:249`, `:310`, `:536`), «la copia de Google» (`ToolTexts.kt:648`) y, si el controlador lo acepta, la URL de atribución `github.com/google/guava` (`LicensesScreen.kt:31`).
