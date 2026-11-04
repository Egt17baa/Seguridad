# Tarea 6: re-revisión de la ronda 1 (754e200..afeb362)

Pruebas acotadas, ejecutadas por mí: `gradle -p jvm-check test --tests 'app.seguridad.imports.*'` en `afeb362`, BUILD SUCCESSFUL (CsvTest, CsvFormatsTest, DetectTest, ImportModelTest, ImportTextsTest, sin saltadas). Además, un programa de usar y tirar (scratchpad `p2/P2.java`) sobre las clases compiladas, con `-Xmx2g`.

### Finding Verdicts

- **I1 (R8: la prueba eximía la palabra «google»)** — ADDRESSED. `ImportTextsTest.kt:27-35` es un conjunto de pares (ruta, literal exacto); `violations` (`:57-61`) deja pasar solo `(path to lit) in ALLOWED` y los de `ImportTexts.kt`. La comparación es por igualdad de la cadena completa, no por palabra ni por subcadena. Los cinco literales son los de R8/R8a (VaultEntryScreen ×3, ToolTexts «copia de Google», LicensesScreen Guava). `everyAllowedLiteralStillExists` (`:70-77`) exige que cada uno siga en su archivo. `aNewLiteralWithANameFailsWhereverItIs` (`:79-86`) prueba que «Importar de Google Contraseñas» falla en un archivo de la lista, que el literal permitido falla en otra ruta, que una cadena partida con «+» (Bitwarden) falla, y que `ImportTexts.kt` sigue libre. La palabra «google» ya no se exime en ningún sitio (`usedBefore` desaparece).
- **I2 (`plainReportText`)** — ADDRESSED. `ImportModel.kt:104-126`, una pasada por punto de código. Comprobado con mi programa:
  - Cf suplementarios (U+1D173) y U+E0001, U+E0002 (sin asignar), U+E007F: quitados. U+E0080 (fuera del bloque) se conserva, como debe.
  - U+FFF9–U+FFFB, U+034F, rellenos Hangul: quitados.
  - Cc, U+0085, U+2028, U+2029: a espacio.
  - Torre de marcas: 3 como máximo. Cuentan también las suplementarias (U+1D167). Un Cf quitado entre marcas no reinicia la cuenta (bien).
  - Sustitutos sueltos (alto, bajo, alto al final): quitados. Resultado siempre bien formado.
  - Corte: dentro de una racha de marcas (maxChars 3 y 4), justo después del tope con un par detrás (`abe`+10 marcas+😀, maxChars 5 da `abe`+2 marcas), par en el límite (3 corta, 4 entra), maxChars 0, 1 con par y negativo: sin par partido ni excepción (`:122` mide el punto de código entero antes de añadirlo).
  - Texto normal: ñ, ¿?, emoji con tono de piel, VS16 y bandera salen iguales.
  - 16 MiB hostiles (ZWSP, etiquetas, 16 M de marcas, sustitutos sueltos, controles, emoji, mezcla) con `maxChars` ilimitado: 56-515 ms. Lineal, sin regex.
- **M1 (lista por línea en blanco)** — ADDRESSED. `Csv.kt:72` (`row` anulable), `:89` (`row = null`), `:144-146` (la lista se crea en la primera celda), `:135-137` (línea sin contenido no devuelve nada). `CsvTest.sixteenMegabytesOfBlankLinesAllocateNothingPerLine` (cabecera + 16 MiB de `\n`, `\r` y `\r\n`) exige 1 fila y menos de 1 MiB reservado, y pasa en esta JVM (HotSpot, con sonda). Antes eran unos 400 MiB. También cubre 16 MiB de saltos antes de la cabecera.
- **M2 (la sonda saltaba toda la prueba)** — ADDRESSED. `CsvTest.kt:~160-180`: `allocated` ejecuta siempre el bloque y devuelve `null` sin sonda; `assumeTrue` ya no existe. Los mensajes (`TOO_MANY_COLUMNS`, `TOO_MANY_CELLS`), el `Failed` de `readImport` y el `timeout` se comprueban siempre; solo `assertBelow` y la comprobación de la sonda (`?.let`) dependen de HotSpot.
- **M3 (quién borra `bytes` y `password`)** — ADDRESSED. KDoc en `Detect.kt:~27-30`: el llamador es dueño, `readImport` no los cambia, guarda ni borra, y borra lo que crea (búfer del decodificador y del lector). Coincide con el código: `readImport` no toca los arrays (revisado en el diff, sin `fill`), y `DetectTest.theCallerOwnsBytesAndPassword` lo comprueba tras `Ok` y tras `Failed`, y que una segunda llamada da lo mismo. `Csv.kt:154-158` y `TextDecode.kt:55` siguen borrando lo propio.
- **M4 (separador en un solo sentido)** — ADDRESSED. `CsvTest` añade `csvDelimiter("\"a,b\";c;d\n1;2;3") == ';'` y la lectura `["a,b","c","d"]`, el empate y «nombre» sin separador dan `,`, y las líneas en blanco iniciales se saltan. Mi comprobación contra el analizador (`csvDelimiter` / `parseCsv`):
  - solo `\n`, solo `\r\r`, vacío: `,` y `[]`, de acuerdo;
  - `\n\r\nname;url\n`: `;` y `[[name,url]]`;
  - `\r\n\r\n;`: `;` y una fila de dos celdas vacías;
  - `"a,b";c;d` tras un salto: `;`;
  - una comilla con salto dentro del campo (`"\n";x;y`): `;`, una sola fila;
  - una línea de espacios cuenta como contenido en los dos (`   \n;;;,` da `,`; el analizador lee `[["   "],[";;;",""]]`).

  `seen` (`Csv.kt:51,56,60`) coincide con `lineHasContent` del analizador (`:92,116,126,131`). `\r\n` cuenta como dos saltos en uno y como uno en el otro, con el mismo resultado.

### New Breakage in the Fix Diff

- **Critical:** ninguno.
- **Important:** ninguno.
- **Minor:**
  1. `ImportModel.kt:112` y KDoc `:92`: quitar U+200C además de U+200D cambia la forma de las palabras en persa, indio y similares en el informe (el ZWNJ separa letras que deben ir sueltas). Está dicho en el KDoc y es una decisión consciente (el review propuso conservarlos); solo afecta a lo que se muestra, nunca a lo guardado (`CsvFormats.kt:123`). Queda para el Abogado Digital/dueño si quieren conservar ZWNJ.
  2. `ImportModel.kt:84`: el tope de 3 marcas puede recortar escritura hebrea con cantilación o tibetana apilada. Mismo caso: solo la vista del informe.
  3. `ImportTextsTest.kt:54` (`namesIn`, sin cambios en el diff) no detecta un nombre pegado a letras («GoogleDrive») por el límite de palabra; el diff no lo empeora.

### Out-of-Scope Observations

- `csvDelimiter` (`Csv.kt:54`) alterna `quoted` con cualquier comilla, mientras que el analizador (`:124`) solo abre comillas al principio de un campo. Con una comilla a mitad de campo en la cabecera (`ab"c;d;e`) los dos pueden elegir distinto separador. No lo toca el diff y el efecto es solo la elección de `,` o `;`.
- `Skipped.title` pasa por `plainReportText` pero otros textos del informe (usuario, URL) los tiene que pasar la tarea 9 (R9).

### Verdict

**Fix round:** All findings addressed (I1, I2, M1, M2, M3, M4), no new Critical/Important breakage.

## Revisión experto-seguridad-cripto

- Inyección y suplantación: `plainReportText` cierra el hueco de caracteres ocultos y torres de marcas (`ImportModel.kt:104-126`), por punto de código, sin regex y en tiempo lineal (medido: 16 MiB hostiles en menos de 0,6 s). Sin sustitutos sueltos ni pares partidos. Los homoglifos quedan fuera y el KDoc no promete más (`:100-101`).
- Agotamiento de recursos: 16 MiB de saltos de línea ya no reservan por línea (`Csv.kt:144-146`, prueba en `CsvTest`). La memoria retenida sigue acotada por los topes del lector (`Csv.kt:141,146-147`).
- Guardia R2/R7/R8: la lista de nombres ajenos es por literal exacto (`ImportTextsTest.kt:27-35,57-61`) y falla ante cualquier literal nuevo con «Google» u otro nombre.
- Restos en memoria: el contrato de borrado es explícito (`Detect.kt:27-30`): el llamador borra `bytes` y `password`; la tarea 9 debe hacerlo en un `finally`.

## Revisión Ingeniero Móvil Senior

- Sin cambios de API que rompan las tareas 7 a 10: `plainReportText` conserva su firma (`ImportModel.kt:104`); desaparece `isBidiControl` (interno, ya no se usa).
- Menos basura para el GC de ART con archivos de líneas en blanco (`Csv.kt:72,144`). `plainReportText` es barato para componerse en cada fila de una lista (para en `maxChars`, `ImportModel.kt:122`).
- Las pruebas no dependen ya de HotSpot salvo en los topes de memoria (`CsvTest` `allocated`/`assertBelow`).

## Textos para el Abogado Digital (revisión final)

- Sin textos nuevos visibles en esta ronda. A decidir por el dueño o el Abogado: que el informe de importación quite U+200C/U+200D (afecta solo a la vista de títulos en persa, indio y emoji con unión), anotado en el KDoc de `plainReportText`.
- La lista exacta de R8/R8a (tres «Google Authenticator», «la copia de Google», atribución de Guava) está en `ImportTextsTest.kt:27-35`.
