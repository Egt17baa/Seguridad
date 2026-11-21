# Director: se cierra la ronda 2, con menores (19-11-2026)

**Pregunta:** ¿se puede cerrar la ronda 2 de los arreglos del ataque (RT-25, RT-26, info 1–3 y el cierre corto, puntos 8–10, `6ede139`, `ae9028a`, `67dbb6f`) y pasar a la re-comprobación 2? ¿Queda algo Importante?

**Veredicto:** se cierra, con menores. No hay nada Importante.

Antes de la re-comprobación 2:
1. Los menores M-1 a M-8, en un commit corto.
2. El clon limpio de `67dbb6f`, en verde: app 2493/0, jvm-check 2433/0 (`de39f4f`).
3. El clon limpio, otra vez, en el HEAD nuevo.

El director no ejecutó nada: las cifras son del implementador y del clon limpio.

## Cada punto cumple `-cierre.md`

### Punto 8
- `VaultCrypto.readAsImported` (`Vault.kt:614-619`) es la usada por `afterMasterUnlockWithData` (`:577-579`).
- `checkCopyData` (`BackupCheck.kt:200-204`) va en el orden estructura, tamaño y entradas. La llaman «Importar» (`VaultStore.kt:1528`) y «Comprobar» (`BackupCheck.kt:99`).
- `JSON_HAS_ATTACHMENTS` solo está en la importación (`VaultStore.kt:1529`).
- El informe enseña `opened.read.vaultTag` (`BackupCheck.kt:124`).
- Una sola decodificación (`Vault.kt:443`). `resolve` solo autentica (`:497-500`).
- Sin cambios de formato.

### Punto 9
- `withTagColor` tiene un tope de 200 (`Folders.kt:273-275`).
- `healthView` captura y da `NO_MEMORY` (`health/HealthView.kt:27-34`), en `Dispatchers.Default` (`HealthScreen.kt:135`).
- La rama `noMemory` vuelve antes de la misión, de la lista y de «Sin problemas detectados.» (`HealthScreen.kt:138-141`).
- Sin `rememberSaveable`, y el bloqueo sigue activo.

### Punto 10
- `produceState<Boolean?>(null, store)` (`HealthScreen.kt:119-120`).
- Las comprobaciones (a) a (d) están en `EmptySlotGuardSourceTest.kt:80-101`.

## Los tests

- El diferencial recorre `importReplace` y `checkBackup` de arriba abajo (`RedTeam20CheckAsImportTest.kt:90-114`): no es tautológico.
- Los menores son huecos de cobertura, no falsos verdes.

## Menores, que se arreglan ahora

### M-1: `checked()` no mira los problemas
- Hoy `checked()` da «OK» sin mirar `report.problems` (`RedTeam20CheckAsImportTest.kt:103-108`). Debe dar «OK» solo sin excepción y sin problemas.
- Además, `vault-1.1.json` suelto:
  - «Importar» da `attachmentNotInCopy("hola.txt")`;
  - «Comprobar» da `problems == [backupCheckMissing(1)]`.

### M-2: un caso «tamaño + dos identidades»
Da `COPY_DAMAGED` en las dos. Fija el orden tamaño → entradas.

### M-3: un caso mal colocado
«4 000 over 10 and no old password» pasa las dos (4 000 ≤ `COPY_LIST_MAX`). Va a los que pasan, o se cambia su nombre.

### M-4: una sola decodificación
`VaultCrypto.decodes == 1` alrededor de `checkBackup`, con una v4, una v3 y una con versión desconocida.

### M-5: los saltos del test de fuente
Se enseña el rojo de cada uno y se endurecen las expresiones (`` `?…`?\s*\( ``) y los comentarios al final de las líneas:
- las comillas invertidas;
- un espacio antes de `(`;
- `.provides(`;
- `!guard()` en un comentario o en una cadena (`EmptySlotGuardSourceTest.kt:21-37`, `:53`, `:70`).

### M-6: un nombre que tapa a otro
`for (tag in e.tags)` (`Folders.kt:274`) tapa al parámetro `tag`; se le cambia el nombre.

### M-7: dos KDoc al día
`BackupCheck.kt:18-22` y `:46-48`: la etiqueta leída, y las comprobaciones sobre los datos como se importan.

### M-8: `pendientes.md`
- El punto que falta en las filas 58 y 62.
- La fila 61 cita el test quitado e invertido en `RedTeam20CheckAsImportTest`.
- La fila 63, abajo.

## Decisión sobre «Importar como bóveda nueva» (R5): informativo, fila 63

- **Qué pasa:** la importación como bóveda nueva cambia la etiqueta y pone la fecha a 0 antes de `checkCopyData` (`VaultStore.kt:1524`). «Comprobar» no lo hace.
- **Cuánto puede cambiar:** solo el tamaño, y como mucho 31 B.
  - La fecha a 0 desaparece del JSON (`core/Json.kt:6-10`).
  - La etiqueta solo crece si la de la copia tenía de 1 a 31 caracteres, y la app escribe 32 hex (`Vault.kt:353`).
- **Qué ve el usuario:** en el borde de 24 MiB + 4 KiB, «Comprobar» da OK y «Importar como bóveda nueva» da `COPY_DAMAGED` sin cambiar nada. «Importar copia» funciona.
- **Decisión:** se acepta como informativo y el atacante lo prueba.
- **Si el atacante encuentra más:** `checkCopyStructure` rechaza como dañada una etiqueta no vacía que no tenga 32 hex en minúsculas. La v4 no está publicada.

## Lista final de ataques para la re-comprobación 2

Además del criterio de RT-25 de `-criterio.md`:

### 1. «Importar» frente a «Comprobar»
- «Importar como bóveda nueva» (fila 63).
- El adjunto que falta.
- La versión desconocida.
- Las contraseñas antiguas.
- Más de 24 MiB después de `fitted()`.
- Varios defectos, también tamaño + entradas.

### 2. El coste de la subida, ahora también en «Comprobar»
- **Memoria:** `upgraded` (`Vault.kt:341-350`) no entra en la estimación del árbol (`VaultLimits.kt:313-318`).
  - El ataque: una 1.x con marca 3 y cientos de miles de contraseñas antiguas.
  - Debe acabar en `COPY_NO_MEMORY`, nunca en un cierre de la app.
- **CPU:** `fittedHistory` mide la historia hasta 20 veces por entrada (`VaultLimits.kt:113-116`).
  - Se mide el tiempo con notas grandes.
  - Hoy no se puede cancelar dentro de `BackupCheck.kt:99`, ni en «Importar» ni en la subida propia, y la decisión (a) pide que se pueda.

### 3. El test de fuente
- Las comillas invertidas, el espacio antes de `(`, `.provides(` y `!guard()` en un comentario o en una cadena.
- Un proveedor o un envoltorio fuera de `ui/` (`AppContainer.kt:601`, `:617`): que ninguna pantalla llegue por ahí a un hueco vacío sin el guardián.

### 4. La longitud de las etiquetas de una copia

### 5. «Salud» y «Organizar» después del arreglo
- Lo que «Salud» reserva en el hilo principal, fuera de la captura (`HealthScreen.kt:147`, `:202`, `:248`, `:256`).
- Con `NO_MEMORY`, una comprobación de filtraciones ya en marcha se queda sin su «Cancelar» (`:138-141` frente a `:198`).

### 6. Más cálculos de toda la bóveda sin captura
- Por ejemplo `liveTags` en «Importar de otra app» (`MergePlan.kt:157-158`, `ImportScreen.kt:316`).
- Se juzga con el criterio (c), ampliado como para «Salud».

### 7. Dos `EntryListViewModel` a la vez
Es un riesgo con nombre (filas 58 y 62), salvo que se vea un cierre de la app fuera de la captura.

## Riesgos o dudas

- El director no ejecutó nada.
- Los saltos de M-5 no están compilados.
- El coste de la subida es una estimación sin medir: unos 200 B por contraseña antigua.
- El Abogado puede pedir un texto propio para «Salud» en lugar de `LIST_NO_MEMORY`.

## Revisión experto-seguridad-cripto

- **R5 y la decisión (b):** cumplen por construcción (`BackupCheck.kt:99`, `:200-204`; `VaultStore.kt:1528`). La única diferencia es la fila 63.
- **Criptografía:** sin primitivas, formatos, dependencias ni hosts nuevos. La clave de la copia se pone a cero (`BackupCheck.kt:127`).
- **CWE-770:**
  - `withTagColor` tiene tope (`Folders.kt:273-275`).
  - «Salud» tiene su última defensa (`HealthView.kt:32`).
  - Quedan los ataques 2 y 6.
- **CWE-284:** el test de fuente es más fuerte, pero sigue siendo textual (M-5).

## Revisión Ingeniero Móvil Senior

- **«Salud»:**
  - La captura va dentro del cálculo, en `Default` (`HealthScreen.kt:135`). Un `Error` en `produceState` cerraría el proceso.
  - La vuelta temprana es segura (`:140`, `:143`).
  - Sin `rememberSaveable`. El bloqueo por inactividad sigue mandando.
- **«Organizar»:** `modifyData` captura `Exception`, no `Error` (`VaultStore.kt:850`, `:876`). El tope de «Organizar» quita la reserva que podía romperlo.

Modelo: el director.
