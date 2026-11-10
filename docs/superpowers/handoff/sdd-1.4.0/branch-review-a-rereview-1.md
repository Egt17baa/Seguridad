# Repaso de la revisión de la rama 1.4.0, área A (importar): arreglos de código

Este repaso cubre solo los arreglos de código de M-2 a M-5 y el test de R37, en los commits `2ddc305` y `45c2660` (diff `93b3f71..45c2660`). También mira el uso de `KdfLock` en la importación (R38 y R40). El informe de esos arreglos es `branch-review-fixes-code-report.md`.

No he revisado la parte de documentos, que hace otro agente, ni he ejecutado Gradle. Me fío de la puerta que apunta el registro para `45c2660` (app 1803, jvm-check 1798, 0 fallos).

## Veredicto

**Hacen falta arreglos**, por un solo detalle menor (N-1). M-2, M-3, M-4 y M-5 están arreglados, dicen la verdad y no meten fallos nuevos, y el test de R37 hace lo que pide R37. El uso de `KdfLock` en la importación es correcto.

## Hallazgo nuevo

### N-1 (menor): se ha subido una copia de seguridad de `MergePlan.kt` a las fuentes de la app

- **Dónde:** `app/src/main/java/app/seguridad/imports/MergePlan.kt.orig`, 445 líneas, añadido en `2ddc305`.
- **Qué es:** el `.orig` que deja un parche. Tiene el `MergePlan.kt` intermedio: ya con el KDoc nuevo de `planMerge`, pero con el `replaceWith` de antes de M-2 (la comparación exacta de las notas).
- **Por qué importa:**
  - No se compila, porque no acaba en `.kt`, pero va en el PR y en el árbol de fuentes, con código viejo que ya no dice la verdad.
  - Las pruebas de textos (`SourceTextsTest`, `ImportTextsTest`) recorren los `.kt`, así que un literal de ese archivo nunca se revisaría.
  - `.gitignore` no excluye `*.orig` ni `*.rej`, así que puede volver a pasar.
- **Arreglo:**
  - `git rm app/src/main/java/app/seguridad/imports/MergePlan.kt.orig`;
  - añadir `*.orig` y `*.rej` a `.gitignore`;
  - opcional: un test de fuentes que falle si en `app/src/main` hay un archivo que no sea `.kt`, `.xml`, `.txt`, `.json` o de recursos.

## Lo comprobado

- **M-2 (las notas).** Ahora `replaceWith` compara las notas recortadas, con `incoming.notes.trim() == existing.notes.trim()` y `existing.notes.trimEnd().endsWith("\n" + incoming.notes.trim())`, y añade `incoming.notes.trim()` (`MergePlan.kt:245-257`). Es la misma regla que `Matcher` (`e.notes.trim()`). Si la bóveda no tiene notas, se guardan las del archivo tal cual: no hay nada que repetir. Los tests (`MergePlanTest.notesThatDifferOnlyByTheSpacesAroundThemAreNotAppended`, `appendedNotesAreTrimmedAndRecognisedAgain`) cubren tres casos: una NOTE y un LOGIN con espacios o saltos alrededor, todo el plan de fusión, y el bloque añadido que se reconoce otra vez.
- **M-3 (la cabecera antes de la contraseña).** La cabecera se comprueba antes de devolver `NeedsPassword`:
  - Bitwarden, con `bitwardenProtected(root)` (`BitwardenJson.kt:35-41`);
  - Aegis, con `aegisEncrypted(root)` (`ImportJson.kt:215`);
  - 2FAS, con `twoFasEncrypted(root)` (`ImportJson.kt:224`).

  Ninguna de las tres deriva nada: solo miran los rangos de la KDF, las longitudes y el tope del Base64. Con contraseña, el camino no cambia. Las exportaciones de prueba siguen dando `NeedsPassword` (`BitwardenCryptoTest.kt:36-37`, `AegisCryptoTest.kt:43`, `TwoFasCryptoTest.kt:32`). Las cabeceras inventadas de los tres tests de detección ahora dan `IMPORT_BROKEN`, que es lo correcto: sus longitudes no valen. Cada caso de «KDF fuera de rango», «dañado» y «pasa de 8 MB» se comprueba también sin contraseña. El único coste es que Aegis decodifica `db` (hasta 6 MiB) dos veces, una al mirar y otra al abrir, que es poco y está acotado.
- **M-4 (la fila del duplicado).** `duplicateSubline` (`MergePlan.kt:391-406`) enseña «usuario · host». El host es el de la web del archivo, leído con `urlHost`, que da `parseLink`, el host al que va el navegador, y en forma `xn--` si es un nombre con letras de otros alfabetos. Todo pasa por `plainReportText`, que quita los invisibles. `DuplicateRow` lo usa (`ImportScreen.kt:666-676`) sin ningún literal nuevo. El test cubre RT-1 (`evil.example\@banco.es`), `www.`, una web sin esquema, un usuario vacío, los invisibles, TOTP y NOTE. Que no enseñe el host de la entrada de la bóveda es una decisión del informe (haría falta una etiqueta nueva) y me parece bien: el riesgo era no ver adónde lleva «Reemplazar», y eso ya se ve.
- **M-5 (KDoc).** Lo que dicen ahora coincide con el código:
  - el KDoc de `planMerge` (`MergePlan.kt:68-82`) explica `compareKey`, `parseLink` y la clave de las notas;
  - el de `planImport` (`:104-112`) dice que es para los tests y que la pantalla hace lo mismo con `importSnapshot()` más `planMerge` en `Default`;
  - el de `importSnapshot` (`VaultStore.kt:661-666`) dice lo mismo.

  La nota del diseño es parte de los documentos y no la he revisado.
- **R37 (el código).** El KDoc de `ImportStep.Saving` (`ImportSession.kt:29-33`), el comentario de `forget` (`:399-400`) y el de `ImportViewModel` (`ImportScreen.kt:164-173`) dicen lo que hace el código: salir de la pantalla no corta la importación; un bloqueo antes de guardar la cancela y la copia se queda; un guardado ya empezado termina.

  El test `AutoBackupImportCopyTest.aLockBetweenTheCopyAndTheSaveSavesNothing` hace la copia, bloquea la bóveda y llama a `applyImport`, y comprueba:
  - que da `Stale`;
  - que `vault.bin` no cambia ni un byte y la revisión sigue igual;
  - que la copia está en la carpeta y en `importCopies`;
  - que, al abrir la bóveda otra vez, solo está la entrada de antes.

  Es lo que pide R37.
- **`KdfLock` en la lectura de una exportación** (R38, `ImportScreen.kt:293-307`, fijado en `PlatformSourcesTest.kt:504-505`):
  - **Solo con contraseña:** sin ella no se deriva nada y sigue en `withContext(Default)`.
  - **Orden de los candados:** primero el ticket de `IMPORT_KDF_RUN`, que nunca espera (`submitPassword`, `:266-280`), y después `KdfLock`. La lectura nunca tiene el mutex de la bóveda (`importSnapshot` va después) y su bloque no llama a la bóveda. `VaultStore` toma su mutex y luego `KdfLock`, así que no hay ciclo. Tampoco hay reentrada: `readImport` no llama a nada que tome `KdfLock`, y `FileCrypto.fileKey` no se usa en la importación.
  - **Cancelación:** mientras espera el candado, la lectura se puede cancelar (`Mutex.withLock`). Entonces el bloque no se ejecuta, y el `finally` pone a cero `pw` y suelta el ticket. Una vez empezado, el bloque va bajo `NonCancellable` y el candado sigue tomado hasta que acaba la derivación, que es lo que dice R38. Después `derive` devuelve el resultado aunque la corrutina esté cancelada. Eso es inocuo: toda cancelación viene con un cambio del `run` (`dropAll` en «Cancelar», en un borrado, en otra elección y en `onCleared`), así que `onRead` lo descarta. El `finally` pone `pw` a cero en todos los casos.
  - **Hilo principal:** se espera suspendiendo (`Mutex`), no bloqueando un hilo, y el bloque corre en `Dispatchers.Default`. `deriveBlocking`, que bloquea, no se usa en la importación.
  - **Coste aceptado (R38):** el bloque es toda la lectura, también el análisis del JSON descifrado, que gasta mucha memoria. Así una lectura cancelada por un bloqueo hace esperar al desbloqueo siguiente hasta que termina, unos segundos. Es lo que acepta R38 («a check or import waits for an unlock»), y además sirve para que no coincidan dos usos grandes de memoria.
- **Lo demás del diff que toca la importación:**
  - el tope del campo de contraseña pasa de 1 024 a `PASSWORD_FIELD_MAX_CHARS` (65 536), y PBKDF2, scrypt y Argon2id lo aguantan sin problema;
  - `IMPORT_PASSWORD_TOO_LONG` no dice ninguna cifra, así que sigue siendo verdad;
  - solo se reordenan los imports y se quita `EntryType` de `ImportScreen.kt`;
  - `ImportKdf.kt:35-36` dice, y es verdad, que la pantalla toma `IMPORT_KDF_RUN` y `KdfLock`.

## Revisión experto-seguridad-cripto

- **Ningún secreto nuevo sin borrar.** El orden de la puesta a cero no cambia: `pw.fill` en el `finally` (`ImportScreen.kt:303-306`) y `Secrets.wipe()` en `open*`.
- **M-3 no hace trabajo caro antes de pedir la contraseña.** Comprueba rangos y longitudes; scrypt solo lee `appHeapKiB()`.
- **`KdfLock` evita que coincidan dos derivaciones de memoria grande**, que es el fallo de memoria de B M-4, y no cambia ningún formato ni ninguna derivación.
- **M-4 reduce la superficie de suplantación.** El host que se enseña es el que usa el navegador (`parseLink`), en forma `xn--` para los nombres con letras de otros alfabetos.

## Revisión Ingeniero Móvil Senior

- **Ningún bloqueo del hilo principal.** `KdfLock.derive` suspende y el bloque va en `Default`.
- **Ningún punto muerto.** El orden es vault, después `KdfLock`; la importación solo toma `KdfLock`, y el ticket va antes y nunca espera.
- **La cancelación y el estado** siguen la regla de los `run` (decisión (b)).
- **La fila del duplicado** sigue en un `LazyColumn` con claves estables; `duplicateSubline` es una pasada lineal acotada a 1 024 caracteres.
- **Pendiente: N-1**, un archivo sobrante en las fuentes.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo: M-4 no añade literales y las demás correcciones no cambian ningún texto.
