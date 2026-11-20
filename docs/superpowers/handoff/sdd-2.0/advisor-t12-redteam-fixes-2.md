# Ruling del director: ronda 2 de los arreglos del ataque, RT-25, RT-26 e informativos (18-11-2026)

Veredicto: Otra ronda, la segunda de los arreglos del ataque, con el mismo implementador. RT-25 se arregla con dos cosas: la lista deja de cerrar la app cuando no hay memoria (se captura el `OutOfMemoryError` y sale un texto fijo), y las copias tienen un tope de etiquetas distintas que se ajusta al número de entradas, que ninguna copia propia alcanza. RT-26 se cierra rechazando un escape en el nombre de un miembro de una clase. Los tres informativos se arreglan: un test de fuente, el nombre de la red con sus marcas en la ficha y el bloque protegido más ancho. Después viene la re-comprobación 2 del atacante.

Motivos:
- **RT-25, confirmado en el código.**
  - `ui/screens/VaultList.kt:263-283` lanza `compute` en `viewModelScope` sin ningún `catch` (`:272`).
  - Al abrir la bóveda se construyen `SearchIndex.build` (`:290`; `vault/EntryQuery.kt:106-140`) y las fichas de etiquetas (`:302`).
  - El índice guarda, por cada texto distinto, la cadena normalizada, sus palabras como `IntArray` y un nodo de `HashMap` (`EntryQuery.kt:108-114`).
- **El criterio que pide el atacante no se puede cumplir sin rechazar bóvedas legítimas.** El atacante pide que enseñar una copia no cueste más que su estimación. Pero una bóveda legítima de entradas mínimas, con título, usuario y servidor distintos en cada una, también necesita unas tres veces su estimación (cálculo mío, sin medir; lo mide la ronda). Lo que sí es propio de una copia hostil son muchas etiquetas distintas por entrada. Las pantallas nunca ponen más de 10 por entrada o versión: `vault/EntryQuery.kt:47-50`, `vault/Selection.kt:93`, `imports/MergePlan.kt:376`, este último salvo con una copia hecha a mano.
- **Un tope fijo de etiquetas distintas sí rechazaría copias propias.** 2 000 entradas con 5 etiquetas únicas cada una suman 10 000. Por eso el tope sigue al número de entradas y de versiones.
- **RT-26, confirmado.** `vault/VaultLimits.kt:321-325` y `:425-426` comparan los bytes del nombre tal cual, y `:422` no cobra los nombres. El decodificador (`core/Json.kt:6-10`) lee el nombre ya sin escapes. Clavero nunca escribe un escape en el nombre de un campo; las claves de `tagColors` sí pueden llevarlo, porque son etiquetas.
- **Los informativos, confirmados.**
  - El guardián del hueco solo está en las pantallas: `ui/screens/VaultScreen.kt:406-413` y `ui/screens/VaultBackup.kt:188`.
  - La ficha enseña el nombre de la red con `plainReportText`: `ui/screens/EntryFields.kt:154` (`plain = true`) y `ui/screens/VaultEntryScreen.kt:161`.
  - La lectura del ZIP y `decodeFile` quedan fuera del bloque protegido: `vault/VaultStore.kt:1477-1485` y `vault/BackupCheck.kt:84-88`.

Ruling por RT:

**RT-25 (Media): se arregla con tres piezas.**

1. **La lista nunca cierra la app.** Es lo que protege también a una bóveda propia, que no se puede limitar.
   - En `EntryListViewModel`, dentro del bloque de `Dispatchers.Default` (`VaultList.kt:272`), se captura `OutOfMemoryError` alrededor de `compute`. R11c se amplía solo a esto.
   - Al capturarlo: `cache = null`, un `ListResult` vacío con la marca `noMemory`, la selección vacía, y la bóveda sigue abierta (se puede bloquear o ir a Ajustes).
   - La lista y la pestaña «Códigos», que es el mismo ViewModel, enseñan en lugar de las filas el texto nuevo `LIST_NO_MEMORY`, exacto:
     > «Este móvil no tiene memoria suficiente para enseñar esta bóveda. La bóveda sigue guardada: cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede enseñarla.»
   - El implementador hace la lista de todo lo que se calcula solo al abrir una bóveda. Lo que esté en esa lista lleva la misma protección. Lo que solo se calcula al abrir una pantalla («Salud», «Organizar», que usa `vault/Folders.kt:270`) va a `pendientes.md`, con la fila 51.
2. **Tope relativo de etiquetas distintas en una copia.**
   - Va en `checkCopyEntries` (`vault/BackupCheck.kt:204-220`), después de decodificar y antes de que se construya nada más. Lo usan igual la importación y «Comprobar una copia».
   - Se rechaza con `CryptoFailure(COPY_DAMAGED)`, texto que ya existe, si las etiquetas distintas de todas las entradas (también las de la papelera) y de todas sus versiones pasan de `max(COPY_LIST_MAX, MAX_TAGS × (entradas + versiones))`.
   - Se cuenta con un `HashSet<String>` que para en el primer elemento por encima del tope, así que nunca guarda más de esa cifra.
   - Por qué este tope:
     - una bóveda hecha con las pantallas tiene como mucho 10 etiquetas por entrada o versión, así que nunca llega;
     - una copia de la 1.x con unas decenas de etiquetas escritas a mano se sigue leyendo, porque hasta 10 000 siempre pasa;
     - la copia del atacante (60 entradas con 300 000 etiquetas distintas) pasa de `max(10 000, 600)`.
   - Lo propio no cambia: abrir, guardar y exportar no tienen este tope.
3. **El criterio de RT-25, por escrito, para que la re-comprobación no lo reabra.** Una copia aceptada nunca cuesta más de enseñar que la bóveda legítima más cara del mismo tamaño. No se le pide «no más que su estimación».
   - Test nuevo del implementador: en muestras de 1 a 2 MiB, mide árbol + índice + fichas por byte de JSON con el método de `shownCost` del atacante.
   - Formas legítimas, escritas como las escribe la app:
     - notas largas;
     - accesos mínimos con título, usuario y servidor distintos;
     - lo mismo con 10 etiquetas distintas de 5 letras;
     - 30 campos visibles con nombres distintos;
     - 20 versiones con etiquetas;
     - carpetas, vistas y colores en sus topes.
   - Formas hostiles que pasan los topes:
     - etiquetas distintas justo en el tope relativo, usando versiones para subirlo;
     - 10 000 etiquetas distintas en pocas entradas;
     - etiquetas repetidas en entradas de 10 000;
     - nombres de campo distintos.
   - Cada forma hostil, por byte, no pasa de 1,25 veces la legítima más cara. Si alguna pasa, se para y se consulta.
   - El test del atacante `rt25_…` se adapta (lo vuelve a comprobar el atacante): «aceptada» pasa a querer decir que supera las comprobaciones de la importación (`checkCopyShape`, decodificar, `checkCopyEntries`, `checkCopyStructure` y `checkCopySize`), no solo `checkCopyShape`. Con eso, su forma se rechaza y el test pasa.

**RT-26 (Baja).**
- `scanCopy` rechaza con `COPY_DAMAGED` una copia con `\` en el nombre de un miembro de un objeto de clase, a cualquier profundidad. Esto son todos los objetos salvo el mapa `tagColors`, cuyas claves (etiquetas) pueden llevar `\"`, `\\` o `\uXXXX` como las escribe kotlinx.
- Así `bytesEqual` solo ve nombres que el decodificador lee igual (CWE-436), y el KDoc de `:251-266` («never below it») vuelve a ser verdad.
- Tests:
  - `rt26_` en verde;
  - claves de `tagColors` con `\"` y `é`: pasan;
  - los cuatro archivos de `app/src/test/resources/seguridad/` y el `resistsEscapesMultiByteAndRepeatedMembers` del atacante: siguen en verde.

**Informativo 1 (el guardián de RT-22): un test de fuente.**
- Recorre `app/src/main/java/app/seguridad/ui/` y busca cada llamada a `.create(` sobre un `VaultStore` y a `.importReplace(`.
- Solo las admite en `VaultScreen.kt` (`CreateVault`) y en `VaultBackup.kt`, y cada una con `mayFillEmptySlot(` delante, en la misma función.
- Cualquier otro archivo de `ui/` que las llame hace fallar el test. Pide al menos 2 coincidencias, para que nunca pase vacío.
- Se añade una línea al KDoc de `VaultStore.create` y de `importReplace`: una pantalla solo los llama sobre un hueco después de `AppContainer.mayFillEmptySlot` (RT-22 (ii)).
- No se cambia el almacén. Un guardián dentro del almacén rompería `newVault`, que llama a `create` después de cambiar de bóveda.

**Informativo 2 (la ficha del wifi): sí, con las marcas.**
- En la ficha, el campo del nombre de la red (`entry.type == WIFI && key == WIFI_SSID_KEY`) se enseña con `visibleText`, como la pantalla del QR.
- `Field` gana un parámetro `marked: Boolean = false`. «Copiar» sigue copiando el valor tal cual, sin las marcas.
- Los demás campos y los títulos siguen con `plainReportText` (R9 y R10).
- No hay texto nuevo, solo las marcas «⟨U+XXXX⟩», que ya están revisadas en el Inspector. Va al Abogado.
- Test: una entrada de wifi con «Casa​» enseña la marca y copia «Casa​».

**Informativo 3 (la lectura fuera del bloque protegido): se amplía el bloque.**
- En `importReplace`, la exportación JSON (`readAtMost` y `decodeToString`, `VaultStore.kt:1477-1479`) y la copia `.sgbak` (`BackupReader().read` y `decodeFile`, `:1483-1484`) pasan a capturar `OutOfMemoryError` dentro de su propio `withContext(io)` o `withContext(compute)`, y lanzan `CryptoFailure(COPY_NO_MEMORY)`.
- Lo mismo en `checkBackup`, en `BackupCheck.kt:84-88`.
- «No se ha cambiado nada» es verdad ahí: todo va antes de `replace`, y la carpeta temporal se borra en el `finally`.
- La prueba usa una costura solo para tests, que lanza un `OutOfMemoryError()` construido a mano.
- Los guardados, la exportación y la copia automática se quedan como están: lo que se abrió, cabe al guardarse (lo dice el atacante).

Encargo de la ronda (para pegar):

---
**Ronda 2 de los arreglos del ataque final de Clavero 2.0: RT-25, RT-26 y los informativos 1 a 3 de la re-comprobación 1**

- **Quién:** el mismo implementador de la ronda 1, con TDD de superpowers. Si no se le puede retomar, uno nuevo con este encargo y `redteam-fixes-report.md`.
- **Lee primero:**
  - `CLAUDE.md`;
  - `experto-seguridad-cripto` y sus `references/` (`SKILL.md`, `auditoria.md` y `cripto-moderna.md`);
  - las skills `desarrollo-movil-senior` y `seguridad-criptografia-movil`;
  - este Ruling, que el controlador guarda en `sdd-2.0/advisor-t12-redteam-fixes-2.md`;
  - `sdd-2.0/redteam-recheck-1.md` y `RedTeam20RecheckTest.kt`;
  - el Ruling de la ronda 1 (`advisor-t12-redteam-fixes.md`);
  - R11c y la decisión (a).
- **Dónde:** en `/home/user/Seguridad`, rama `ccr-0aa27f95-u038cd`, desde el commit que guarde este Ruling. Nadie más toca `app/src/main` hasta que cierre la re-comprobación 2 (R32).
- **Gradle:** siempre con `flock /home/user/.gradle-run.lock … --no-daemon --max-workers=1`. Se itera en `jvm-check`. Lo de `ui/`, solo en la app (`export ANDROID_HOME=/home/user/android-sdk`; después, `./gradlew --stop`). Nunca las suites enteras ni dos Gradle a la vez.
- **Tests:** ningún `OutOfMemoryError` de verdad (se construye a mano con una costura solo para tests). Cada test, menos de 10 s, sin `sleep`, con `Random(20261118L)`.
- **TDD:** quita el `@Ignore` de `rt25_` y de `rt26_`, enseña el rojo en el informe, arregla y deja el test en verde. Cada commit deja la puerta `redteam` en verde.
- **Commits:**
  - identidad: `git -c user.name=Egt17baa -c user.email=Egt17baa@users.noreply.github.com commit`;
  - asunto: «2.0, arreglos del ataque (ronda 2): …»;
  - al final, las dos líneas de atribución de la sesión, como en `40482b4`;
  - ningún nombre de modelo en archivos, asuntos ni cuerpos, y ningún dato del dueño;
  - **sin push.**
- **Orden.** Cada punto es un commit. Al cerrar cada uno, se pone al día el «Estado» del informe.
  1. **RT-26.** El rechazo de un escape en el nombre de un miembro de clase, con sus tests y el KDoc al día.
  2. **Informativo 3.** El bloque protegido ampliado en `importReplace` (las dos lecturas) y en `checkBackup`, con la costura y estos tests: el archivo de la bóveda y `settings.json` quedan iguales byte a byte, la carpeta temporal se borra y sale `COPY_NO_MEMORY`.
  3. **RT-25, parte a.** La captura en `EntryListViewModel`, `ListResult.noMemory`, la lista y «Códigos» con `LIST_NO_MEMORY` (texto exacto, fijado en `ErrorTextsTest` y `ForbiddenWords`, con su fila en el inventario de la fase C), y la lista de todo lo que se calcula solo al abrir una bóveda, cada cosa protegida. Tests con la costura:
     - no lanza nada;
     - la bóveda sigue abierta;
     - el siguiente cambio de datos vuelve a calcular y, sin la costura, enseña la lista.
  4. **RT-25, parte b.** El tope relativo en `checkCopyEntries`, el test `rt25_` adaptado a todas las comprobaciones de la importación (en verde) y el test del criterio, con su tabla en el informe y su condición de parada. Más estos tests:
     - una copia de 2 000 entradas con 10 etiquetas únicas cada una (20 000 distintas): pasa;
     - una copia de la 1.x con 50 etiquetas en una entrada: pasa;
     - una bóveda propia con 30 000 etiquetas distintas en 100 entradas: se abre con la maestra y con la huella (sin tope);
     - la copia de esa bóveda: «Importar» y «Comprobar» la rechazan con `COPY_DAMAGED`.
  5. **Informativo 1.** El test de fuente del guardián, y el KDoc de `create` y de `importReplace`.
  6. **Informativo 2.** `Field(marked = true)` para el nombre de la red en la ficha, con «Copiar» copiando el valor tal cual, y su test.
  7. **Cierre.**
     - Antes, corre por separado: `BackupCheckTest`, `BackupV3Test`, `BackupTest`, `VaultV4Test`, `VaultStoreTest`, `AppContainerVaultsTest`, `ErrorTextsTest`, las clases de la lista y `redteam.*`.
     - El informe `redteam-fixes-2-report.md`, en `.superpowers/sdd/2026-11-12-clavero-2.0/` y una copia en `docs/superpowers/handoff/sdd-2.0/`, con:
       - una tabla por hallazgo con el rojo, el verde y cada test;
       - «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior», con `archivo:línea`;
       - «Textos para el Abogado Digital (revisión final)»: `LIST_NO_MEMORY` y las marcas del nombre de la red en la ficha.
     - `pendientes.md`:
       - riesgo con nombre: una bóveda propia con más etiquetas distintas que el tope relativo (solo desde una copia hecha a mano) se abre, pero sus copias se rechazan;
       - fila del móvil, con la 51: la lista y «Salud» con una bóveda muy grande;
       - lo que se calcula solo al abrir «Salud» u «Organizar».
- **Nunca** rompe `SG1:`, `.sgenc`, `seguridad-vault` ni `seguridad-backup`. Sin dependencias, permisos ni hosts nuevos.
- **Después** (lo hace el controlador):
  1. El atacante (o un agente nuevo con `advisor-t12-attack.md` y los dos informes) pone su *worktree* al día con `git -C /home/user/Seguridad-rt merge --ff-only ccr-0aa27f95-u038cd` y escribe `redteam-recheck-2.md`, con los tests en `RedTeam20Recheck2Test.kt`. Busca:
     - **el tope relativo:** versiones o papelera para subirlo, etiquetas que solo se diferencian por mayúsculas o por escapes, otros textos del índice (usuarios, servidores, nombres de campo) con más densidad que la forma legítima más cara;
     - **la captura de la lista:** algo más que, al abrir la bóveda, cierre la app sin captura;
     - **RT-26:** nombres con `\/` o con escapes en otros niveles;
     - **las costuras del informativo 3**;
     - **el test de fuente:** otras pantallas que llamen al almacén;
     - **la ficha del wifi.**
  2. El criterio de RT-25 es el de este Ruling: una forma legítima no es un hallazgo.
  3. Un hallazgo nuevo abre la ronda 3. Con 5 rondas, decide el dueño.
  4. Al cerrar: clon limpio con las dos suites por separado y con `flock`, y ningún `@Ignore` en `redteam/` ni en `ui/redteam/`. Después, el informe publicado `docs/auditorias/2026-11-17-ataque-2.0.md` y `code-review` alto.
---

Riesgos o dudas:
- **Una bóveda legítima enorme sigue sin poder enseñarse en un móvil pequeño.** Por ejemplo, 160 000 entradas con usuarios distintos: el índice cuesta unas tres veces el árbol. Ahora sale `LIST_NO_MEMORY` en lugar de cerrarse la app. Es la fila 51 y la medida en el móvil; ningún tope lo arregla sin limitar una bóveda propia.
- **El tope relativo es una regla nueva al final del ciclo.** Si en el criterio alguna forma hostil pasa de 1,25 veces la legítima, se para y se consulta.
- **Adaptar `rt25_` cambia un test del atacante.** Lo autoriza este Ruling y lo vuelve a comprobar el atacante.
- **Después de un `OutOfMemoryError` capturado**, el proceso puede seguir corto de memoria en otras pantallas. El texto pide cerrar Clavero del todo. La bóveda no se bloquea sola, para que siga siendo posible ir a Ajustes y exportarla.
- **No ejecuté nada.** Las densidades del índice salen de leer `EntryQuery.kt:79-140`; las mide la ronda.

Revisión experto-seguridad-cripto:
- **RT-25:** CWE-400 y CWE-770. La decisión (a) se cumple poniendo el tope antes de reservar el índice (`BackupCheck.kt:204-220`, que corre antes de `VaultList.kt:290`), y el tope sigue a lo que escriben las pantallas (`EntryQuery.kt:47-50`, `Selection.kt:93`, `MergePlan.kt:376`), así que no rechaza ninguna copia propia.
- **RT-26:** CWE-436. Desde ahora, `scanCopy` (`VaultLimits.kt:321-325`, `:425-426`) solo reconoce nombres que el decodificador lee igual. Rechazar es más seguro que deshacer los escapes, porque no hace falta otro analizador.
- **Lo que no cambia:** ninguna primitiva. `COPY_NO_MEMORY` y `VAULT_NO_MEMORY` siguen sin decir nunca «Contraseña incorrecta» (`Vault.kt:305`), y lo propio nunca tiene tope (decisión de RT-21).
- **Informativo 1:** el control de acceso de RT-22 (CWE-284) queda fijado por un test de fuente con un mínimo de coincidencias. **Informativo 2:** CWE-451, coherente con `WifiQrScreen.kt:209`.

Revisión Ingeniero Móvil Senior:
- **La captura:** un `viewModelScope.launch` sin manejador propaga el `Error` y cierra el proceso (`VaultList.kt:263-283`). Capturarlo dentro de `withContext(Dispatchers.Default)` y soltar `cache` deja que el GC recupere el índice a medio hacer. `collectLatest` vuelve a calcular con cada cambio, sin bucles propios.
- **Rendimiento:** contar las etiquetas distintas con parada en el tope es O(número de etiquetas) y guarda como mucho el tope, en `compute`, junto a las demás comprobaciones.
- **Compose:** `Field(marked = true)` solo cambia lo que se pinta. La copia sigue con `copySensitive(value)` sobre el valor tal cual (`VaultEntryScreen.kt:165`). Sin estado guardado nuevo.
- **Pruebas:** `jvm-check/build.gradle.kts:20-22` no compila `ui/`, así que los tests de la lista y de la ficha solo corren en la app. Gradle siempre con `flock`, para no repetir el exit 137.

Modelo: el director (esfuerzo extra).
