# Director: revisión de la ronda 2 de los arreglos del ataque, a mitad (19-11-2026)

**Pregunta:** ¿está bien planteada y encaminada la ronda 2 (RT-25, RT-26, info 1–3), y qué debe vigilar el controlador hasta el APK? Base: `0ec7c14` más el árbol del implementador.

Veredicto: encaminada. RT-26 (`bd0c31a`) e info 3 (`0ec7c14`) están bien. El tope relativo de RT-25 se puede inflar con versiones y con entradas de la papelera que desaparecen después de importar, y eso abriría la ronda 3. Se cambia antes del commit 4.

## Motivos

1. **El tope relativo se puede inflar.**
   - Por qué pasa:
     - una copia v4 no se ajusta al importarla (`vault/Vault.kt:567`);
     - ni `fieldsDamaged` ni `checkStructure` limitan `history.size` (`VaultLimits.kt:210-228`);
     - cada guardado recorta a 20 versiones (`VaultLimits.kt:106-123`);
     - la purga de la papelera usa el `deletedAt` que trae la copia (`Trash.kt:39-41`).
   - El efecto: una copia con miles de versiones mínimas o con papelera vieja sube `MAX_TAGS × (entradas + versiones)`, pasa la importación y, tras el primer guardado, deja la bóveda por encima del tope. Sus copias posteriores se rechazan sin avisar.
   - **Decisión (b):** la suma, en cada entrada y en cada versión, de `max(0, etiquetas − MAX_TAGS)` no pasa de `COPY_LIST_MAX`. Se compara con `>` y se rechaza con `COPY_DAMAGED`.
     - Los cuatro casos del primer Ruling salen igual.
     - No hace falta un conjunto.
     - El coste aceptado: se rechaza una copia hecha a mano con más de 10 000 etiquetas sobrantes en entradas de más de 10.
2. **Con `noMemory`, la lista no puede parecer vacía.**
   - `VaultList.kt:581-587` enseña `EMPTY_VAULT` y «Importar copia», y `VaultList.kt:359-366` borra el filtro. Hace falta una rama `noMemory` antes, sin herramientas ni «Todas (0)» (`:811`).
   - Lo que se pinta se decide con una función pura más un ancla de fuente.
3. **Dónde vive cada cosa.**
   - `jvm-check` excluye `**/ui/**` (`jvm-check/build.gradle.kts:7-22`), así que `LIST_NO_MEMORY` va fuera de `ui/`, como `VAULT_NO_MEMORY`.
   - El test del wifi sale de una función pura (`tools/qr/Visible.kt:23`, `VaultEntryScreen.kt:165`) más un ancla.
4. **El test de 1,25× tiene que ser determinista.**
   - Hoy mide con `System.gc()` (`RedTeam20RecheckTest.kt:31-37`).
   - Hace falta calentar antes (el `Collator`), repetir cada forma y usar la mediana, con el margen en la tabla y cada test por debajo de 10 s.
   - La forma del suelo se juzga en absoluto, no por byte.
5. **Info 1:** las pantallas llaman a `guard()`, no a `mayFillEmptySlot(`.
   - Dónde: `VaultScreen.kt:360`, `:408-411`, `VaultBackup.kt:107`, `:188`, con `provides` en `AppNavHost.kt:335`.
   - El test fija esos dos archivos, el orden y el `provides`, con al menos 2 coincidencias.
   - El valor por defecto pasa de `{ true }` a `{ false }` (`VaultBackup.kt:73`), para que falle cerrado.

## Riesgos o dudas

- No ejecutó nada.
- No leyó si la copia automática (`AutoBackup.kt:455-457`) codifica sin capturar un `OutOfMemoryError`.
- No comprobó:
  - si los dos `EntryListViewModel` (la lista y «Códigos», `VaultScreen.kt:158`) viven a la vez;
  - si «Importar como bóveda nueva» deshace el hueco con `COPY_NO_MEMORY`.
- **Preexistente:** «Importar» y «Comprobar» aplican las comprobaciones en distinto orden (`VaultStore.kt:1519-1526`, `BackupCheck.kt:99-102`), así que una copia con varios defectos puede dar mensajes distintos.
- **Pasos siguientes:**
  - `rt25_` sin `@Ignore` desde el commit 4;
  - el punto 1, al atacante en la re-comprobación 2;
  - `LIST_NO_MEMORY`, a los textos para el Abogado;
  - `pendientes.md`, si algo queda abierto.

## Revisión experto-seguridad-cripto

- **RT-26:** sólido. `tagColors` es el único mapa (`VaultData.kt:17`) y `appJson` no es permisivo (`core/Json.kt:6-10`). El rechazo cubre las claves del mapa (`VaultLimits.kt:437-441`): CWE-436 cerrado.
- **Info 3:** `copyRead` corre dentro de cada `withContext` (`BackupCheck.kt:84-90`, `VaultStore.kt:1478-1489`).
- **RT-25:** CWE-770. Con el punto 1 cumple la decisión (a). Sin primitivas, formatos ni dependencias nuevas.

## Revisión Ingeniero Móvil Senior

- La captura rodea `compute` (`VaultList.kt:272`) con `cache = null`, y `cache` solo se asigna entero (`:290`): no queda estado a medias.
- `collectLatest` vuelve a calcular con cada tecla: aceptable.
- El estado sin memoria vive en el ViewModel, nunca en `rememberSaveable`.

Modelo: el director.
