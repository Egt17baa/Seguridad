# Tarea 6: re-revisión de la ronda 2 (afeb362..705b65b)

Pruebas acotadas: `gradle -p jvm-check test --tests 'app.seguridad.imports.*'` en `705b65b`, BUILD SUCCESSFUL. Más un programa de usar y tirar (scratchpad `p2/P3.java`) sobre las clases compiladas.

### Finding Verdicts

- **Minor 3 (`namesIn` no detecta un nombre pegado a letras)** — ADDRESSED. `ImportTextsTest.kt` (`names`, `wholeWordNames`, `namesIn`): los nombres se buscan como subcadena en `text.lowercase()` (Kotlin usa `Locale.ROOT`, sin el problema del turco), y «edge» solo como palabra entera. La lista exacta R8/R8a (`ALLOWED`) no cambia y sigue mandando: un literal permitido solo pasa por igualdad exacta de (ruta, literal). Comprobado:
  - «GoogleDrive», «misBitwarden», «copia2FASx», «iCloudDrive», «MicrosoftEdge», «msedge.exe» y «Edge» dan positivo (`theCheckFindsANameInALiteral`); la mutación `GoogleDrive` en `BackupCheckTexts.kt:16` hizo fallar la prueba (informe).
  - Falsos positivos: el único literal de `main` con «edge» dentro de otra palabra es `VaultEntryScreen.kt:406` («Ledger»), cubierto por la excepción de palabra entera; la prueba completa pasa sin más aciertos. «microsoft» y «msedge» como subcadena no aparecen en ningún literal. Los demás nombres cortos (aegis, apple, brave, steam, chrome, safari, 2fas) no están dentro de palabras españolas comunes. «Safari» sí es una palabra española (la expedición), pero ningún literal de `main` la usa.
  - Hueco restante, Minor: «edge» pegado por delante o por detrás a letras sin «microsoft» ni «msedge» (por ejemplo «EdgeBrowser», «MiEdge») no se detecta. Es una forma poco natural y no está en el código; ver abajo.
- **Nota fuera de alcance (`csvDelimiter` y el analizador tratan las comillas de otra manera)** — ADDRESSED. `Csv.kt:43-55` y `:66-81`: `csvDelimiter` cuenta, para `;` y para `,`, las celdas que lee el propio `CsvReader` en el primer registro no vacío (`countFirstRecord`), con la misma regla de comillas (solo abren al principio de un campo, `:123-126`). Ya no hay segunda máquina de estados.
  - Coste: dos pasadas lineales sobre el primer registro, sin reservar memoria (`endField` solo cuenta, `append` no escribe, `Csv.kt:144-160`). Medido con 16 MiB: comas 87 ms, punto y coma 38 ms, mixto 60 ms, comillas sin cerrar 60 ms, comillas a mitad de campo 79 ms, solo `\n` o solo `\r` 236-249 ms, campo entrecomillado de 16 MiB 42 ms, comillas dobles 57 ms; reserva 0 KiB salvo 733 KiB con comas (ruido del JIT). El contador no desborda (como mucho 16 M).
  - Una comilla sin cerrar en la cabecera se atrapa (`ImportFailure`) y termina la cuenta; la lectura completa da luego `IMPORT_CSV_UNCLOSED_QUOTE`. `csvHeader` ya capturaba el fallo.
  - Líneas en blanco iniciales y archivo solo de saltos: cuenta 0 en las dos, gana `,`, como el analizador (no devuelve nada).
  - `delimiterDetectionAndParseAgreeOnAnyHeader` es determinista: `java.util.Random(6)`, 20 000 textos de hasta 13 caracteres de `ab,;" \n\r`; comprueba que el separador es el de más celdas y que la primera fila del `parseCsv` tiene exactamente esa cuenta (o falla con `UNCLOSED_QUOTE`). `delimiterDetectionUsesTheParsersQuoteRule` fija los casos `ab"c;d;e`, `na"me;url,x`, `na"me;url;x,y`.
  - Cambio de comportamiento aceptado: con un empate de celdas gana `,`. Una cabecera con `;` y nombres entrecomillados que llevan comas sigue dando `;` (`"a,b";c;d`), cubierto por las pruebas de la ronda 1.

### New Breakage in the Fix Diff

- **Critical:** ninguno.
- **Important:** ninguno.
- **Minor:**
  1. `ImportTextsTest.kt` (`wholeWordNames`): «edge» pegado a letras sin «microsoft»/«msedge» no se detecta. Aceptable; solo se cerraría con una lista de formas.
  2. Cuando la cuenta se corta por unas comillas sin cerrar, la última celda no se cuenta (`Csv.kt:70-73`, el fallo salta antes de `endField`). No importa: ese archivo se rechaza después.

### Out-of-Scope Observations

- `VaultEntryScreen.kt:406` nombra «Binance» y «Ledger» como ejemplos; no lo cubre la regla de nombres ajenos (apuntado por el implementador). Ya existía.

### Verdict

**Fix round:** All findings addressed, no new Critical/Important breakage.

## Revisión experto-seguridad-cripto

- Guardia de nombres ajenos más estricta (subcadena, sin distinguir mayúsculas, `ImportTextsTest.kt` `names`), con una sola excepción escrita y razonada; la lista exacta R8/R8a no se debilita.
- Detección del separador y lectura comparten la máquina de estados (`Csv.kt:48-55,71-81`): no pueden discrepar. Lineal, sin regex y sin reservas con entradas hostiles de 16 MiB (medido). El modo de contar no guarda contenido (no deja restos de secretos en memoria: `Csv.kt:144-148,154-157`).

## Revisión Ingeniero Móvil Senior

- Coste extra: dos pasadas por el primer registro (hasta unos 250 ms en la JVM con 16 MiB de saltos de línea; menos en los demás casos), sin reservar memoria; `readImport` se llama fuera del hilo principal (tarea 9).
- Sin cambios de API pública: `csvFirstRecordCells` es `internal` (`Csv.kt:56`). Pruebas deterministas (semilla fija) y sin dependencia de HotSpot.

## Textos para el Abogado Digital (revisión final)

- Sin textos nuevos para el usuario. Nota heredada: `VaultEntryScreen.kt:406` («Binance», «Ledger» como ejemplos) para la revisión final.
