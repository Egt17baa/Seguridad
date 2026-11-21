# Director: cierre de la ronda 2 de los arreglos del ataque (19-11-2026)

**Pregunta:** al cerrar la ronda 2 (`07e21a2..a39e755`), el implementador deja tres dudas. ¿Qué se hace con ellas? ¿Se aprueba cerrar la ronda y pasar a la re-comprobación 2?

**Veredicto:** la ronda 2 está bien hecha, pero todavía no se cierra.
- Las tres dudas se arreglan en un cierre corto, los puntos 8 a 10, con el mismo implementador y sin *push*.
- La re-comprobación 2 empieza después, sobre el HEAD nuevo y no sobre `a39e755`.

## Motivos

1. **Lo hecho es sólido; el director lo comprobó en el diff.**
   - Dónde está cada arreglo:
     - RT-26: `VaultLimits.kt:412`, `:441`, `:319`;
     - la lectura de una copia: `BackupCheckTexts.kt:126`;
     - la captura de la lista: `VaultList.kt:306`, `:383`;
     - el tope (b): `BackupCheck.kt:240`;
     - el valor por defecto `{ false }`: `VaultBackup.kt:74`.
   - Los rojos son reales y los verdes no pasan en vacío:
     - `rt25_` termina en `assertTrue(refused)`;
     - `NoMemoryTest` comprueba que, sin la costura, la copia se importa.
   - No hay ningún `@Ignore` en `redteam/`.
2. **Duda 3: es un fallo, contra R5** («Comprobar una copia» comparte con «Importar copia» sus comprobaciones, en el mismo orden).
   - «Importar» comprueba los datos como los sellaría, `read.upgraded(random).fitted()` (`Vault.kt:574-576`), en el orden estructura, tamaño, entradas (`VaultStore.kt:1525-1529`).
   - «Comprobar» los comprueba tal como están guardados y en otro orden (`BackupCheck.kt:99-102`). Además, decide con `incoming.version` en lugar de la versión resuelta.
   - Contar antes de la subida, en los dos sitios, no sirve: reabriría la fila 57.
   - Hay una segunda divergencia: «Comprobar» se salta `checkCopySize` en las copias de antes de la 2.0 (`BackupCheck.kt:102`).
   - Para una copia de seguridad es el peor fallo posible: una falsa seguridad.
3. **Duda 2: las dos se arreglan ahora.**
   - «Organizar» construye un conjunto de todas las etiquetas sin tope (`Folders.kt:270`), dentro de `modifyData`, que solo captura `Exception` (`VaultStore.kt:871-877`).
   - «Salud» calcula sin captura dentro de un `produceState` (`HealthScreen.kt:132-139`): un `OutOfMemoryError` cierra la app.
4. **Duda 1: la lectura de `guard()` en «Salud» se queda,** porque solo elige el texto. Pero tiene dos detalles:
   - empieza en `true` (`HealthScreen.kt:118`), así que «Aún no tienes bóveda» parpadea aunque haya otra bóveda;
   - el test de fuente solo ve `.create(` con punto, y `topFun` no reconoce `suspend`, `inline` ni las anotaciones.

## Encargo: los puntos 8 a 10

Las mismas reglas que el resto de la ronda: Gradle de uno en uno con `flock`, TDD, la identidad y la atribución de siempre, y sin *push*.

### 8. Duda 3: «Comprobar» comprueba lo que «Importar» sellaría

- **Una sola función interna en `VaultCrypto`** da, para un archivo y su clave, los datos leídos y los datos «como se importan»:
  - resuelve la versión con `resolve`;
  - si es antigua, `upgraded(random).fitted()`;
  - si es una v4, pone la etiqueta solo si falta.
  - La usan `afterMasterUnlockWithData` y `checkBackup`.
- **Una sola función de comprobación**, `checkCopyData(data)`, con el orden de «Importar»: estructura, tamaño, entradas.
  - La llaman `replace.prepare` y `checkBackup`.
  - `JSON_HAS_ATTACHMENTS` sigue solo en la importación.
  - Desaparece el `if (incoming.version == …)` de `BackupCheck.kt:102`.
- **Cuidado:** el informe enseña la etiqueta leída (`read.vaultTag`), nunca la que crea `upgraded`. Si no, `withBackupChecked` dejaría de reconocer una copia de antes de la 2.0 (`BackupCheck.kt:127`).
- **Restricciones:**
  - todo va dentro de la captura de `OutOfMemoryError` que ya existe;
  - una sola decodificación: los tests de `decodes` de RT-21 siguen en verde;
  - `VaultV4Test` y los tests de la huella siguen en verde sin tocarlos.
- **Tests**, rojos contra el código de hoy:
  - `aCopyOfBefore20CountsItsUpgradedVersionsOnlyWhenImported` se invierte y pasa a `aCopyOfBefore20IsCheckedAsItIsImported`: las dos la rechazan con `COPY_DAMAGED`;
  - una 1.x (v3) con más de `VAULT_MAX_DATA_BYTES + COPY_DATA_MARGIN_BYTES` en las notas: las dos la rechazan;
  - una copia con dos defectos: el mismo mensaje en las dos;
  - comprobar una copia de antes de la 2.0 sigue dando `vaultTag == ""` y no guarda ninguna fecha;
  - **test diferencial:** los cuatro archivos de `app/src/test/resources/seguridad/`, más una 1.x con 50 etiquetas y 5 contraseñas antiguas, pasan las dos. Para cada copia del test, «Comprobar» pasa si y solo si pasa «Importar».

### 9. Duda 2: «Organizar» y «Salud»

- **«Organizar»:** `withTagColor` llena `used` solo con las etiquetas que ya tienen color, así que guarda como mucho `TAG_COLORS_MAX` = 200.
  - La lógica no cambia: las entradas de la papelera cuentan y las versiones no.
  - Es un cambio sin otro efecto, así que no hay rojo. El test de equivalencia se escribe primero, con el código de hoy copiado en el test como referencia, y pasa antes y después.
  - El test usa `Random(20261118L)`, 200 colores ocupados, entradas vivas y en la papelera, y el caso `TOO_MANY_TAG_COLORS`.
- **«Salud»:**
  - `HealthView`, hoy en `HealthScreen.kt:274`, pasa a `health/`, fuera de `ui/`. Va con una función `healthView(data, now, breached, missionsOff)` que captura `OutOfMemoryError` y da `HealthView.NO_MEMORY`. La costura de pruebas es `beforeHealthCompute`.
  - Se llama dentro de `withContext(Dispatchers.Default)`.
  - Con `noMemory`, la pantalla pinta `LIST_NO_MEMORY` antes del `LazyColumn`: sin misión, sin recuentos y nunca «Sin problemas detectados.». La bóveda sigue abierta.
  - No hay texto nuevo. El KDoc del texto (`Vault.kt:291-295`) añade «Salud».
  - Tests:
    - con la costura, sale `NO_MEMORY` y no se lanza nada;
    - sin ella, un acceso con una contraseña débil da su `WEAK`;
    - rojo con la captura cambiada a `IllegalStateException`;
    - un ancla en la app: la rama `noMemory` va antes de `LazyColumn(` y de «Sin problemas detectados.».

### 10. Duda 1: el guardián en «Salud» y el test de fuente

- `HealthScreen.kt:118` pasa a `produceState<Boolean?>(null, store)`. Mientras es `null` no se pinta el texto.
- `EmptySlotGuardSourceTest` se endurece:
  - (a) los archivos que leen `LocalEmptySlotGuard.current` son exactamente `VaultScreen.kt`, `VaultBackup.kt` y `HealthScreen.kt`;
  - (b) un ancla de `produceState<Boolean?>(null, store) { value = guard() }`;
  - (c) en `ui/`, cero `create(` o `importReplace(` sin punto delante, y cero `::create` o `::importReplace`;
  - (d) `topFun` reconoce cualquier visibilidad, `suspend`, `inline` y las anotaciones en la misma línea.
- **Rojos**, sin commit y uno a uno:
  1. volver a `produceState(true, …)`;
  2. un archivo temporal con `with(store) { create(pw) }`;
  3. uno con `store::importReplace`.

  Se enseña la salida de cada uno y se deshace.

### Cierre

- **Ejecuciones por separado.**
  - En jvm-check: `BackupCheckTest`, `BackupTest`, `BackupV3Test`, `VaultV4Test`, `VaultStoreTest`, `NoMemoryTest`, `EmptySlotGuardSourceTest` y `redteam.*`.
  - En la app: las clases de la lista, de la ficha y de «Salud», más `ui.redteam.*`.
- **El informe:** una tabla de rojos y verdes de los puntos 8 a 10.
- **`pendientes.md`:**
  - las filas 59 y 61 pasan a «Hecho»;
  - las filas 58 y 62 añaden que las medidas son por ViewModel y que la lista y «Códigos» pueden tener dos a la vez;
  - los textos para el Abogado añaden `LIST_NO_MEMORY` en «Salud».

## Para la re-comprobación 2

Además de `advisor-t12-redteam-fixes-2-criterio.md`. Empieza con el `merge --ff-only` del HEAD nuevo.

- **«Importar» frente a «Comprobar»:** cualquier copia que pase una y no la otra, o que dé mensajes distintos. Con versión desconocida, con contraseñas antiguas, con más de 24 MiB después de `fitted()`, con varios defectos.
- **El test de fuente:** cómo saltárselo, por ejemplo con `with`, referencias a función, alias, extensiones, funciones locales o un proveedor nuevo de `LocalEmptySlotGuard`.
- **La longitud de las etiquetas de una copia:** las pantallas cortan a 24 (`EntryQuery.kt:13`, `:49`), pero una copia no tiene ese tope. Se juzga con el criterio (a)/(b).
- **«Salud» y «Organizar» después del arreglo:** algo que, al abrir «Salud», cierre la app fuera de la captura. Y si `withTagColor` guarda más de 200.
- **Dos `EntryListViewModel` a la vez** (`AppNavHost.kt:350-352`, `VaultScreen.kt:158`): solo es hallazgo si el atacante enseña un cierre fuera de la captura (criterio (c)). Si no, se queda como riesgo con nombre (filas 58 y 62), para medirlo en el móvil.

## Riesgos o dudas

- El director no ejecutó nada.
- **La copia limpia hay que repetirla:** la que salió en verde validó `a39e755`, así que se vuelve a pasar en el HEAD nuevo, antes de unir la rama del atacante.
- **El punto 8 toca un camino delicado:** `afterMasterUnlockWithData`, por el que pasa cada desbloqueo de una bóveda antigua.
- **Las dos pestañas, cada una con su índice, no se reestructuran ahora.** Quedan como riesgo con nombre.
- **El texto de «Salud» puede cambiar:** reutilizar `LIST_NO_MEMORY` ahí es fiel al hecho, pero el Abogado puede pedir uno propio.

## Revisión experto-seguridad-cripto

- **CWE-770 y decisión (a):**
  - con el arreglo, `withTagColor` guarda como mucho `TAG_COLORS_MAX` (`Folders.kt:270`);
  - la captura de «Salud» completa la de la lista (`VaultList.kt:306`).
- **R5, que «Comprobar» diga la verdad sobre una copia:** hoy aprueba lo que «Importar» rechaza (`BackupCheck.kt:99-102` frente a `VaultStore.kt:1525-1529` y `Vault.kt:576`). Una sola función para los datos y otra para las comprobaciones lo cierran por construcción.
- **CWE-284 (RT-22 (ii)):** el test de fuente pasa a cubrir las llamadas sin punto y las referencias a función.
- **Sin cambios en lo demás:** ninguna primitiva, formato, dependencia, permiso ni host.

## Revisión Ingeniero Móvil Senior

- **La captura de «Salud» va dentro del cálculo:** un `produceState` que lanza un `Error` cierra el proceso, así que va dentro de `withContext(Dispatchers.Default)`, como en `VaultList.kt:302-310`.
- **Nada falso mientras se lee el disco:** `produceState<Boolean?>(null)` sigue el patrón de `VaultScreen.kt:361`.
- **En «Organizar» se quita la reserva sin tope:** `modifyData` captura `Exception` y no `Error` (`VaultStore.kt:876`), y la ruta de guardado no se toca.
- **La medida en el móvil (fila 58) se hace después de visitar las dos pestañas,** porque cada una guarda su ViewModel y su `cache` (`AppNavHost.kt:350-352`).

Modelo: el director.
