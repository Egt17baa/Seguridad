# Ronda final de arreglos (1.4.0): informe

**Estado:** DONE (una desviación del encargo en el punto 3, explicada abajo).
**Commit:** «1.4.0: ronda final de arreglos», sobre `e600feb` (rama `ccr-d1e75160-p5f4wc`), sin subir.

Los cuatro puntos del registro (`progress.md:106`, `:109` y `:210`). No toca `imports/MergePlan.kt`, `vault/MasterPolicy.kt`, sus tests ni `redteam/` (los arregla otro agente).

## Qué ha cambiado

### 1. Los ejemplos del nombre en el formulario de la bóveda

- **`app/src/main/java/app/seguridad/ui/screens/VaultEntryScreen.kt:407`:** «Nombre (p. ej. «Binance» o «Ledger»)» pasa a **«Nombre (p. ej. «Correo del trabajo» o «Cartera principal»)»**. El campo es el nombre de cualquier tipo de entrada (Acceso, Frase semilla, Nota, Código 2FA; `vault/Vault.kt:23-27`), y el texto de antes ponía un ejemplo de acceso (una cuenta de un servicio) y uno de frase semilla (una cartera física). Los nuevos mantienen ese par sin nombrar productos.
- **`ALLOWED` de `ImportTextsTest`** no lo tenía (no nombraba ninguno de la lista: «edge» solo coincidía dentro de «Ledger» y estaba exento por palabra entera), así que no hay nada que quitar de la lista. El pin que lo fijaba (`ImportTextsTest.kt:115` de antes) se sustituye; ver el punto 2.
- **`ImportTextsTest.kt:24-27`:** «binance» y «ledger» entran en la lista de nombres, para que los ejemplos de antes no puedan volver en ningún literal fuera de `ImportTexts.kt`; **`:134-140`** (`theVaultFormNamesNoProduct`) fija el texto nuevo y que el formulario no tiene ni «Binance» ni «Ledger».
- No hay más copias del texto en la interfaz, el README, la ficha ni la política (`grep` de «Nombre (p. ej»). Siguen nombrándolo, como historia, `task-6-report.md:264,322`, `task-12-report.md:131` y `docs/auditorias/2026-11-08-auditoria-1.4.0.md:156` (esta dice ya «ejemplos genéricos», que es lo que se ha hecho).
- `EntryQueryTest.kt:23` usa «Binance» como título de prueba de la búsqueda. Es un dato de un test, no un texto que se vea, y la regla (y `ImportTextsTest`) cubre solo `app/src/main/java`; no lo he cambiado.

### 2. `ImportTextsTest`: los nombres pegados a otras letras

- **`ImportTextsTest.kt:19-34`:** desaparece `wholeWordNames`. «edge» pasa a la lista general, que busca la subcadena en minúsculas, así que «EdgeBrowser», «MiEdge», «mi_edge» y «EDGE2» se detectan; «msedge» sobra (lo cubre «edge») y sale. La única palabra corriente que contenía «edge» en un literal era «Ledger», que ya no está; «knowledge» no aparece en ningún literal de `main`. Por eso la lista de excepciones (`ordinaryWords`, `:34`) está **vacía**, con su regla escrita: una palabra entra solo cuando un texto real la necesita.
- **`:72-76`** (`namesIn`): primero quita las palabras de `ordinaryWords` como palabras enteras y después busca cada nombre como subcadena. Así una excepción nunca tapa el producto pegado a ella.
- **Comprobación del buscador:** `:110-122` (`theCheckFindsANameInALiteral`): los nombres pegados se detectan; el texto de antes del formulario, «MiBinance», «LedgerNano» y «knowledge» (sin excepción) también; el texto nuevo, no. `:124-132` (`anOrdinaryWordIsTakenOutOnlyAsAWholeWord`): con una excepción de muestra, «knowledge» y «Knowledge base» no cuentan, pero «knowledgeEdge», «MiEdge knowledge», «knowledge, Edge» y «knowledges» sí.
- **Los demás nombres:** todos se buscaban ya como subcadena (`names`); la debilidad de la palabra entera solo afectaba a «edge». He mirado si «binance», «ledger» y la lista entera chocan con alguna palabra de un literal de `main`: no (el test pasa con las fuentes actuales). «trezor» aparece en la URL de la atribución de SLIP-0039 (`LicensesScreen.kt:38`); no lo he añadido a la lista, porque no es un nombre de los que la regla vigila y habría que ampliar `ALLOWED` (R8a) sin una decisión.

### 3. `csvDelimiter` y la comilla sin cerrar

Al mirarlo, el «inofensivo» del registro no se cumplía del todo. `countFirstRecord` no contaba la celda que abre una comilla sin cerrar, y eso podía cambiar el delimitador elegido. Con «a;"b» en la cabecera, «;» daba 1 celda (le faltaba la abierta) contra 1 de «,», el empate elegía «,», y con «,» la comilla queda en medio de una celda, que no abre nada. El archivo se leía entonces como una sola columna (`a;"b`), en lugar de fallar con `IMPORT_CSV_UNCLOSED_QUOTE`. No es un problema de seguridad: ningún lector reconoce esa cabecera y el archivo se rechaza igual, pero con un error que no es el que corresponde. Por eso, además del comentario y del test, he arreglado la cuenta (regla del dueño: los detalles menores se arreglan):

- **`app/src/main/java/app/seguridad/imports/Csv.kt:80-85`:** en `countFirstRecord`, el único fallo posible es una comilla que no se cierra dentro del primer registro con contenido (una línea en blanco no tiene comillas), así que la celda que abrió se estaba leyendo cuando acabó el texto, y cuenta (`counted++`). El comentario explica el caso «a;"b».
- **`Csv.kt:51-58`** (KDoc de `csvFirstRecordCells`): la celda abierta cuenta como si la comilla se cerrara al final. Un archivo así nunca da filas, sea cual sea el delimitador: si el elegido encuentra la comilla abierta, `parseCsv` lanza `IMPORT_CSV_UNCLOSED_QUOTE`; si no (`a,b;"c` con «,» son dos celdas, con la comilla en medio de una), el lector no ve ninguna comilla abierta.
- **`app/src/test/java/app/seguridad/imports/CsvTest.kt:91-106`** (`aCellLeftOpenByAQuoteCountsForTheDelimiter`): los recuentos de «a;"b» (2 con «;», 1 con «,») y de «"» (1). Y una propiedad sobre 20 000 cabeceras al azar: añadir la comilla de cierre no cambia el recuento de un registro con alguna celda.
- **`CsvTest.kt:108-123`** (`anUnclosedQuoteEndsInItsErrorWhateverTheDelimiter`): «a;"b\n1;2» elige «;» y falla con `IMPORT_CSV_UNCLOSED_QUOTE`. Una comilla al principio falla con los dos delimitadores (`"a,b;c`, `"a;b;c,d\n1;2`, `"a,b,c;d\n1,2`), y una en una fila posterior, con «;» y con «,». `csvHeader("a;\"b")` da `null`, y «a,b;"c» se lee con «,» en dos celdas, como dice el KDoc.
- **En rojo sin el arreglo:** con la línea `counted++` comentada, `./gradlew -p jvm-check test --tests 'app.seguridad.imports.CsvTest'` dio «28 tests completed, 2 failed» (los dos nuevos). Con el arreglo, en verde. El test aleatorio de antes (`delimiterDetectionAndParseAgreeOnAnyHeader`, `CsvTest.kt:72-89`) sigue en verde: el cambio solo toca el recuento de un delimitador cuya lectura queda abierta, y con ese delimitador `parseCsv` falla.

### 4. Los dos avisos `!!` del compilador

- **`app/src/main/java/app/seguridad/ui/screens/PhotoCleanScreen.kt:238`** y **`RedactScreen.kt:365`:** `PreparedShare(prepared!!, …)` pasa a `PreparedShare(prepared, …)`. `prepared` es una `var` local a la que se acaba de asignar el `File` no nulo de `shareTemp.prepare` (`tools/share/ShareTemp.kt:53`), y Kotlin ya la tenía por no nula (smart cast). Por eso el aviso era `UNNECESSARY_NOT_NULL_ASSERTION`. No hay ningún cambio de comportamiento ni de estado de Compose: es la misma variable, sin la aserción. El `finally` sigue usando `prepared?.let` para el caso en que la asignación no llegó.
- **Antes** (`./gradlew :app:compileDebugKotlin --rerun-tasks` sobre `e600feb`):
  ```
  w: file:///…/ui/screens/PhotoCleanScreen.kt:238:56 Unnecessary non-null assertion (!!) on a non-null receiver of type 'File'.
  w: file:///…/ui/screens/RedactScreen.kt:365:56 Unnecessary non-null assertion (!!) on a non-null receiver of type 'File'.
  ```
- **Después** (el gate, con `:app:compileDebugKotlin` y `:app:compileDebugUnitTestKotlin` recompilados): **0** líneas `w:` en el registro (`grep -c "^w: "` → 0). En `jvm-check`, también 0.

## Verificación

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, **1755** tests, 0 fallos, 0 errores (`CsvTest` 28, `ImportTextsTest` 15); 0 avisos del compilador; lint sin errores.
- `./gradlew -p jvm-check cleanTest test --no-daemon --max-workers=1` (el wrapper, R29): **BUILD SUCCESSFUL**, **1750** tests, 0 fallos, 0 avisos.
- Sin dependencias, permisos, hosts ni componentes exportados nuevos; ningún log. El único archivo de `main` con lógica que cambia es `imports/Csv.kt`.

## Revisión experto-seguridad-cripto

Repasé `references/auditoria.md` como lista para mi diff (no toca criptografía, así que `cripto-moderna.md` no aplica).
- **Entrada hostil (decisiones (a) y (c)):** el arreglo de `Csv.kt:80-85` solo suma 1 al recuento, sin reservar nada ni leer más: sigue siendo una pasada sin almacenar (`append` y `endField` no guardan en el modo de recuento, `Csv.kt:159-170`), acotada por la longitud del texto. Un archivo con comilla abierta sigue sin dar filas nunca (`CsvTest.kt:108-123`). Ahora el error es el correcto en más casos (`IMPORT_CSV_UNCLOSED_QUOTE` en lugar de «formato desconocido»), y no aparece ningún camino nuevo de aceptación: la única diferencia de elección es pasar de «,» a «;» cuando la lectura con «;» queda abierta, y con «;» el archivo falla.
- **Regla de nombres ajenos (R2, R7, R8):** el buscador es más estricto (subcadena para todos, `ImportTextsTest.kt:72-76`) y la lista de excepciones está vacía, así que no se abre ningún hueco nuevo; `ALLOWED` no cambia (`:45-53`) y `everyAllowedLiteralStillExists` sigue en verde.
- **`!!`:** quitar una aserción que el compilador marca como innecesaria no cambia el bytecode del camino no nulo. El fichero compartido sigue saliendo solo por `PreparedShare` cuando `!leftWhileBusy` (`PhotoCleanScreen.kt:237-240`, `RedactScreen.kt:364-367`), y si no, se borra en el `finally`.
- Sin secretos, logs, red ni formatos nuevos. Nada que señalar.

## Revisión Ingeniero Móvil Senior

- **Compose y estado:** `PhotoCleanScreen.kt:238` y `RedactScreen.kt:365` siguen asignando `shareReady` (estado del ViewModel, no `rememberSaveable`) dentro de `viewModelScope.launch`, con la misma variable local. No cambia la recomposición ni el ciclo de vida, y la cancelación (`CancellationException` relanzada) y el `finally` con `NonCancellable + Dispatchers.IO` quedan igual.
- **Formulario:** `VaultEntryScreen.kt:407` solo cambia el texto de la etiqueta; el `OutlinedTextField` y su estado (`title`) son los mismos. El texto es más largo que el de antes, pero es una etiqueta de una línea en un campo de ancho completo; en un móvil estrecho, la etiqueta de Material 3 se corta con puntos suspensivos, como pasaría con cualquier etiqueta larga (a mirar en la prueba del dueño, junto a P-20).
- **Rendimiento:** `csvDelimiter` sigue haciendo dos pasadas como mucho sobre el primer registro, sin expresiones regulares (`Csv.kt:48-49` y `:59`).
- **Tests:** el buscador de `ImportTextsTest` tiene su propia comprobación (`:110-132`), y el arreglo del CSV, un test que falla sin él (comprobado).

## Textos para el Abogado Digital (revisión final)

- **`VaultEntryScreen.kt:407`**, etiqueta del campo del nombre en «Nueva entrada» y «Editar»: **«Nombre (p. ej. «Correo del trabajo» o «Cartera principal»)»**. Sustituye a «Nombre (p. ej. «Binance» o «Ledger»)», que venía de antes de la 1.4.0 y nombraba dos productos fuera de la pantalla de importar. Sin nombres de terceros ni palabras prohibidas (`SourceTextsTest` y `ImportTextsTest` en verde).
- Ningún otro texto visible cambia (los de `Csv.kt` y los tests son comentarios).
