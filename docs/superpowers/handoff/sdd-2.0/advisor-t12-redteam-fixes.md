# Ruling del director: arreglos del ataque final, RT-21 a RT-24 (17-11-2026)

Veredicto: Se arreglan los cuatro en una sola ronda de arreglos. RT-21: análisis de la copia sin decodificarla, con un tope por lista y un presupuesto de memoria calibrado, una sola decodificación y el `OutOfMemoryError` capturado. RT-22: `settingsEditable` pasa a la regla de `canAddVault`, y se pregunta al dueño si se cierra también el hueco. RT-23: opción (a). RT-24: `visibleText` en la pantalla y una línea de aviso. Al final, la re-comprobación del atacante y el clon limpio.

Motivos:
- **RT-21 confirmado en el código.**
  - `vault/Vault.kt:409-427` solo mira la profundidad (`:412`) y decodifica todo.
  - `vault/VaultStore.kt:1502-1507` decodifica dos veces (dentro de `afterMasterUnlock`, `Vault.kt:523`, y otra vez en `:1507`), y además sella y puede derivar otra vez con Argon2id, todo antes de las comprobaciones de `:1509-1514`.
  - `vault/BackupCheck.kt:196-212` no mira las etiquetas.
  - `ui/screens/VaultBackup.kt:185-187` solo captura `CryptoFailure` y `Exception`.
  - **Con `jsonTreeEstimate` (`imports/ImportJson.kt:84`) no basta, porque mide otro árbol.** Cobra 88 B por miembro y 80 B por literal, el árbol `JsonElement`. Así, una bóveda legítima de entradas mínimas con 10 etiquetas de una letra sale a unas 14 veces su tamaño, por encima de la copia hostil del test (unas 12): esa estimación no las separa. Hace falta un modelo de la decodificación con tipos y un tope por lista.
- **El tope de 10 no se puede poner.** `imports/MergePlan.kt:376` conserva más de 10, y ninguna versión de la 1.3.0 a la 1.4.3 comprobó las etiquetas de una copia. Una lista de más de 10 solo pudo nacer de una copia hecha a mano, pero en ese caso la bóveda y sus copias la llevan. Las listas que la app escribe por sí misma no pasan de 500 (`VaultLimits.kt:39-42`: carpetas 500, vistas 50, colores 200; campos 30, historial 20, `MAX_PASSWORD_HISTORY` 5, `MAX_TAGS` 10).
- **RT-22 confirmado, y el arreglo propuesto por el atacante solo cierra la mitad.**
  - `settings/Settings.kt:443` da verdad con `NoVault`; `AppContainer.kt:631-632` usa otra regla.
  - Además, un hueco se puede rellenar sin abrir ninguna bóveda: `ui/screens/VaultScreen.kt:391` (`store.create`) y la importación con `NoVault` (`ui/screens/SettingsScreen.kt:690`; `checkCanReplace` lo permite con `vault == null`, `VaultStore.kt:1440`). Esa bóveda nueva queda abierta y entonces Ajustes deja cambiar todo.
  - Ese camino lo aceptó el Ruling T2, duda 1 (`progress.md:51`) y lo dejó al dueño como decisión de producto.
- **RT-23 sin camino de ataque.** `Vault.kt:532` y `:534` conservan la clave de datos y solo `changeMaster` (`:555-563`) crea una nueva. Mientras las dos bóvedas comparten clave, comparten también la maestra (la de la copia), así que una clave nueva al importar no protege nada frente al modelo de amenaza. Ningún texto publicado lo afirma (`README.md:20` dice «su contraseña maestra», no «su clave»).
- **RT-24 confirmado.** `ui/screens/WifiQrScreen.kt:209` usa `plainReportText`, mientras `tools/qr/WifiQr.kt:167-173` escribe el nombre tal cual. El Inspector ya enseña las marcas y el aviso `qrBidi` (`tools/qr/QrView.kt:118`, `tools/ToolTexts.kt:317`).

Ruling por RT:

**RT-21 (Media): se arregla con cuatro piezas, en este orden.**

1. **Una sola decodificación, con las comprobaciones antes de sellar.**
   - `afterMasterUnlock` decodifica una vez. En la importación, `checkCopyStructure`, `checkCopySize` y `checkCopyEntries` corren dentro de su `prepare`, sobre los datos que se van a escribir (ya pasados a v4 y ajustados, como pide A m-2) y antes de `write`/`wrap`. Así, una copia rechazada no gasta el segundo Argon2id.
   - El llamante se queda con lo que da `prepare`, y de ahí salen `shown` y `opened`. `VaultStore.kt:1507` desaparece. Es la decisión (b): lo comprobado es lo que se escribe y se enseña.
   - Lo mismo en el desbloqueo con la maestra: `commit(..., read = …)` recibe lo preparado y no vuelve a decodificar en `VaultStore.kt:487-488` ni en `:617`.
   - Los adjuntos (`attachmentInCopy`) siguen después, en `io`.
2. **Análisis de la copia antes de decodificar, solo para copias.**
   - `VaultCrypto.read` y `afterMasterUnlock` ganan un parámetro con valor por defecto `copy = false`. Lo ponen a verdad «Importar copia», «Importar como bóveda nueva», la exportación JSON de la 1.0 (las tres pasan por `replace`) y `checkBackup`.
   - Después de `jsonNestingWithin` y antes de `toString`, una pasada lineal sobre los bytes descifrados: `checkCopyShape(json, version)`, junto a `jsonNestingWithin` en `vault/VaultLimits.kt`. Tiene en cuenta las cadenas y sus escapes, como `scanJson`, pero sin crear ningún `String`. Rechaza con `CryptoFailure(COPY_DAMAGED)` (texto que ya existe) si:
     - cualquier lista u objeto distinto de la lista de entradas pasa de **`VaultLimits.COPY_LIST_MAX = 10_000`** elementos o miembros. La lista de entradas es la raíz en las versiones 2, 3 o sin versión, y el valor del miembro `entries` de la raíz en la v4;
     - **o** la estimación pasa de **`VaultLimits.COPY_TREE_MAX_BYTES`**.
   - El modelo de la estimación sigue la decodificación con tipos: cadenas, `ArrayList` con su crecimiento, `LinkedHashSet` y `LinkedHashMap` (los `tags` y `types` de una vista y `tagColors`), cada clase de `VaultData` y los `Long?` en caja. Los nombres de miembro no cuestan.
     - Un elemento de array cuesta lo de un elemento de `LinkedHashSet`, que es lo más caro.
     - Con JDK 21 y SerialGC, cada constante se mide contra el montón vivo después de GC, como en R11a, y se queda en una tabla del KDoc.
   - **Respuesta a «¿cuál es el tope de etiquetas?»:** 10 000 por lista (también por entrada), no 10. Es 20 veces la lista más larga que escribe la app (500 carpetas) y 1 000 veces su máximo de etiquetas por entrada. Una copia de la 1.x con unas decenas de etiquetas escritas a mano se sigue leyendo.
   - **Cómo se fija `COPY_TREE_MAX_BYTES`:** 1,25 veces la mayor estimación entre las formas legítimas a 24 MiB + 4 KiB, redondeada a MiB. Las formas son:
     - (a) notas largas;
     - (b) entradas LOGIN mínimas como las crea la app (id UUID, fechas);
     - (c) como (b), con 10 etiquetas de un punto de código cada una;
     - (d) 30 campos por entrada;
     - (e) 20 versiones de historial;
     - (f) 500 carpetas, 50 vistas con filtros y 200 colores;
     - (g) 5 contraseñas antiguas por entrada;
     - (h) los cuatro archivos de `app/src/test/resources/seguridad/`.
   - Primero se escribe la tabla de qué largo máximo puede escribir la app en cada lista del formato, con `archivo:línea`. Si alguna puede pasar de 10 000 legítimamente, se dice y se consulta. Los `tags` de una vista solo llegan ahí eligiendo más de 10 000 fichas a mano: se acepta.
   - **Condición de parada:** si la estimación de la copia de `rt21_…` cae por debajo de 1,25 veces la forma legítima más cara, se para y se consulta. No se pone un número a ojo.
3. **El `OutOfMemoryError` como última defensa.** R11c se amplía solo a esto, siempre dentro del bloque que corre en `compute` y en ningún otro sitio:
   - en `replace`, alrededor de todo lo que va antes de `withContext(NonCancellable)`, que no escribe nada: `CryptoFailure(COPY_NO_MEMORY)`, con la clave borrada;
   - en `checkBackup`, alrededor de la lectura y las comprobaciones: `COPY_NO_MEMORY`;
   - en el desbloqueo, con la maestra (`unlockLocked` y `commit`) y con la huella (`unlockWithKeyLocked`, `VaultStore.kt:725-727`): `CryptoFailure(VAULT_NO_MEMORY)`. La bóveda queda bloqueada, la huella no se apaga (no es `StaleBiometricKey`) y `masterUnlockErrorText` (`Vault.kt:296-299`) añade `VAULT_NO_MEMORY` a su lista cerrada. Así nunca sale «Contraseña incorrecta», que llevaría a «He olvidado».
4. **Textos nuevos, exactos:**
   - `COPY_NO_MEMORY` (en `vault/BackupCheckTexts.kt`; `copyFailureText` lo pasa tal cual): «Este móvil no tiene memoria suficiente para leer esta copia. No se ha cambiado nada. Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede leerla.»
   - `VAULT_NO_MEMORY` (junto a `WRONG_MASTER`): «Este móvil no tiene memoria suficiente para abrir esta bóveda. La bóveda sigue guardada: cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede abrirla.»
   - El rechazo por el análisis usa `COPY_DAMAGED` palabra por palabra, la misma convención que `checkCopySize` y `checkCopyStructure`: lo que ninguna pantalla de Clavero escribe.
   - Antes de usar cada texto, el implementador comprueba en cada sitio donde se lanza que «No se ha cambiado nada» y «La bóveda sigue guardada» son ciertos. Si alguno no lo es, para.

**Una bóveda ya guardada con muchas etiquetas:**
- Nada de lo propio aplica los topes de copia: el desbloqueo con la maestra o la huella, `confirmMaster`, `changeMaster` y exportar. Se abre como hoy si hay memoria; si no, sale `VAULT_NO_MEMORY` y no se borra nada.
- Sus copias llevan lo mismo, y «Importar» y «Comprobar» las rechazan solo si una lista pasa de 10 000 o el árbol pasa del presupuesto. Eso solo puede venir de una copia hecha a mano.
- Queda como riesgo con nombre en `pendientes.md`, aceptado.

**RT-22 (Baja).**

**(i) Ahora, sin el dueño.**
- `settingsEditable(state, anyVaultFile)`: verdad si la bóveda está `Unlocked`, o si está en `NoVault` y ninguna bóveda del móvil tiene `vault.bin`. Es el mismo predicado que `canAddVault`, sacado a una función privada que usan los dos.
- La pantalla lo lee fuera del hilo principal (`produceState` en IO, empezando en `false`, falla cerrado). Cada escritura lo vuelve a comprobar con una función `suspend` del contenedor, como hoy en `SettingsScreen.kt:309`, `:368`, `:379`, `:453`, `:633`, `:641` y `:650`.
- `SettingsEditableTest` se actualiza. El test de RT-22 llama a esa función, como prevé su KDoc.
- **Lo que ve el usuario:** con una bóveda vacía a la vista y otra con archivo, el bloqueo, el portapapeles, el coste de Argon2id y las carpetas de copia se ven apagados, y debajo de cada uno sale el texto nuevo `SETTINGS_NEED_AN_OPEN_VAULT`: «Abre una de tus bóvedas (pestaña Bóveda) para cambiar este ajuste.» `SETTINGS_NEED_OPEN_VAULT` no cambia y sigue para `Locked`. Con una sola bóveda, o con ninguna con archivo, todo queda como hoy.
- «He olvidado» no cambia (Ruling T2, duda 1).

**(ii) Pregunta al dueño ahora, sin bloquear; recomiendo que sí.** El controlador se la hace antes de lanzar la ronda:
> «Con dos bóvedas, quien tenga el móvil un momento puede vaciar una con "He olvidado la contraseña maestra", crear en su hueco una bóveda suya y, con ella abierta, cambiar el bloqueo y el portapapeles de toda la app, que también protegen tus otras bóvedas. Propuesta: mientras otra bóveda del móvil tenga archivo, la bóveda vacía solo se puede borrar; para crear o importar otra, se abre antes una de las tuyas, como ya pide "Crear bóveda" en "Bóvedas". Con una sola bóveda no cambia nada. ¿Lo cerramos así?»

Si dice que sí, va como último grupo de commits:
- un guardián en `AppContainer` (`canAddVault()`, comprobado justo antes de llamar al almacén) para `store.create` desde el hueco y para importar a un hueco;
- en ese estado, `CreateVault` enseña el selector de bóvedas y `VAULT_NEW_NEEDS_OPEN` («Para crear o importar otra bóveda, abre antes una de las que tienes.»), sin el formulario ni «Importar»;
- en Ajustes, «Importar copia» queda apagado y la pista de `SettingsScreen.kt:701` pasa a `VAULT_NEW_NEEDS_OPEN`;
- `:443` pasa a «Esta bóveda está vacía: no hay nada que copiar.»;
- «Salud» (`HealthScreen.kt:115`) pasa a `HEALTH_EMPTY_VAULT`: «Esta bóveda está vacía. Para crear o importar otra bóveda, abre antes una de las que tienes.»;
- `newVault` e `importAsNewVault` desde una bóveda abierta no pasan por el guardián.

Si dice que no, o no contesta antes de cerrar la ronda, va a `pendientes.md` como riesgo residual decidido, y la re-comprobación no lo cuenta como hallazgo nuevo.

**RT-23 (Info): opción (a). No se toca el código.**
- El test pasa a `resistsAVaultImportedAsNewSharesTheCopysKeyOnlyUntilAMasterChanges`. Comprueba lo que es verdad:
  - `keyA == keyB` justo después de importar;
  - los `vaultTag` son distintos (D-12);
  - después de `changeMaster` en B, la clave de B es distinta de `keyA`, la maestra de A ya no abre B, y A sigue abriéndose con la suya y con su entrada.
- Frase para el diseño, justo debajo de la tabla del «Modelo de amenaza» (la `:38` no se toca):
  > *Nota de corrección (ataque final, RT-23, 17-11-2026):* la fila «Formato v4» dice que cada bóveda tiene su propia clave de datos. No siempre es así: «Importar copia» e «Importar como bóveda nueva» conservan la clave de datos de la copia, así que la bóveda que resulta tiene la misma clave de datos y la misma contraseña maestra que tenía, al hacer la copia, la bóveda de la que salió; dejan de compartirla cuando cualquiera de las dos cambia su contraseña maestra, que crea una clave de datos nueva. Clavero nunca usa la clave de una bóveda con el archivo de otra: cada una tiene su archivo en el almacenamiento privado de la app y su clave de la huella, que solo envuelve la clave de su bóveda; una copia comprobada solo cuenta para la bóveda de su `vaultTag`, y cada adjunto tiene su propia clave. Mezclar los datos de un archivo con los de otro exige escribir en el almacenamiento privado (root), fuera de este modelo. Darle una clave nueva al importar no cambiaría nada para quien sepa la maestra: mientras las dos la compartan, abre las dos igual.
- Y como sub-viñeta bajo `:62`, con el estilo de `:96`:
  > *Nota de corrección (RT-23):* «cada bóveda tiene su propia clave de datos» no vale para una bóveda importada de una copia (ver la nota del «Modelo de amenaza»). El identificador sigue sin hacer falta en la AAD: mezclar los datos de un archivo con los de otro exige escribir en el almacenamiento privado de la app (root), fuera del modelo de amenaza.

**RT-24 (Baja): las dos cosas a la vez.**
- `WifiQrScreen.kt:209` pasa a `Text(visibleText(shown.ssid), …)`.
- `wifiQrLines` mete, como segunda línea cuando `hasHiddenChars(c.ssid)` es verdad, el texto nuevo `wifiQrNameHidden(hiddenMarks(c.ssid).joinToString(", "))`: «El nombre de esta red lleva caracteres que no se ven, marcados como $marks, y el código los lleva tal cual. Si no lo escribiste así, vuelve a escribir el nombre en «Editar».»
- La pantalla la pinta a la vista, en el color de los avisos (`NegativeColor`). El aviso de O-4 sigue siendo el primero.
- El Inspector se queda como está: basta su aviso. Quitar «Añadir a la bóveda» rechazaría nombres legítimos con emoji compuestos (U+200D), y ahora el código guardado enseña las marcas cada vez que se muestra.

Encargo de la ronda (para pegar):

---
**Ronda de arreglos del ataque final de Clavero 2.0 (RT-21 a RT-24), según el Ruling del director**

- **Quién trabaja.**
  - El implementador de las rondas de la revisión de la rama, si se le puede retomar: conoce `afterMasterUnlock`, la fila 45, `KDF_COST_KEPT` y `copyFailureText`.
  - Si no, uno nuevo con este encargo.
  - Nunca el atacante.
  - Usa la TDD de superpowers.
- **Lee primero:**
  - `CLAUDE.md`;
  - `.claude/skills/experto-seguridad-cripto/SKILL.md` y sus `references/cripto-moderna.md` y `auditoria.md`;
  - las skills `desarrollo-movil-senior` y `seguridad-criptografia-movil`;
  - el Ruling en `sdd-2.0/advisor-t12-redteam-fixes.md`, donde el controlador guarda esta respuesta;
  - `sdd-2.0/redteam-report.md`;
  - en `progress.md`, los Rulings T1 (dudas 4-6), T2 (duda 1, I-1, I-2), T4 I-1, T5 (I-1 e I-2), T8 D-12, T10C (reglas de los textos de error) y la fila 45 de la ronda de la rama;
  - de la 1.4.0, R11a y R11c (`imports/ImportJson.kt:22-170`).
- **Dónde.**
  - En `/home/user/Seguridad`, rama `ccr-0aa27f95-u038cd`, desde el commit que guarde este Ruling.
  - Nadie más toca `app/src/main` hasta que cierre la re-comprobación (R32).
  - Gradle siempre de uno en uno: `flock /home/user/.gradle-run.lock ./gradlew … --no-daemon --max-workers=1`.
  - Itera en `jvm-check` (`./gradlew -p jvm-check test --tests '<clase>'`); `ui/` solo lo compila la app (`export ANDROID_HOME=/home/user/android-sdk`; después, `./gradlew --stop`).
  - Nunca corre las suites enteras ni dos Gradle a la vez.
  - Ningún test agota la memoria de verdad: un `OutOfMemoryError()` construido a mano a través de una costura solo para tests, como `kekDerive` o el `unlock` de `checkBackup`. Cada test dura menos de 10 s, sin `sleep`, con `Random(20261117L)`.
- **TDD.** Por cada `rtN_`, quita el `@Ignore`, enseña el rojo (pega la salida en el informe), arregla y deja el test en verde. Cada commit deja la puerta `redteam` en verde, con los `@Ignore` que aún queden.
- **Commits.**
  - Identidad: `git -c user.name=Egt17baa -c user.email=Egt17baa@users.noreply.github.com commit`.
  - Asunto: «2.0, arreglos del ataque: …».
  - Al final, las dos líneas de atribución de la sesión, como en `cb76302`.
  - Ningún nombre de modelo en archivos, asuntos ni cuerpos, y ningún dato del dueño.
- **Orden** (cada punto, uno o más commits que se guardan; al cerrar cada uno, actualiza el bloque «Estado» del informe):
  1. **RT-23.** El test pasa a `resistsAVaultImportedAsNewSharesTheCopysKeyOnlyUntilAMasterChanges`, con las comprobaciones del Ruling, y las dos notas de corrección van al diseño, palabra por palabra. Sin código.
  2. **RT-22 (i).** `settingsEditable(state, anyVaultFile)` y la función `suspend` del contenedor, que comparte su predicado con `canAddVault`; `SETTINGS_NEED_AN_OPEN_VAULT` en los cuatro ajustes y en el aviso de `:311` cuando el estado es `NoVault`. Tests:
     - el de RT-22, en verde;
     - `SettingsEditableTest`;
     - en el contenedor: primera instalación, editable; A con archivo y B vacía, no editable; A abierta, editable.
  3. **RT-24.** `visibleText` en la pantalla y `wifiQrNameHidden` en `wifiQrLines`, con su color de aviso. Tests:
     - el de RT-24, en verde;
     - las cinco gemelas del test tienen su línea;
     - «❤️» no la tiene;
     - la tabla de textos visibles de la tarea 9, al día.
  4. **RT-21, parte a: una sola decodificación.**
     - Las comprobaciones de la copia, dentro de `prepare` y antes de `write`/`wrap`.
     - Lo preparado se usa en `shown`, `opened` y `commit`, también al desbloquear con la maestra.
     - Tests:
       - una cuenta de decodificaciones (por una costura): 1 al importar y 1 al desbloquear;
       - lo que se enseña es igual a `VaultCrypto.read` del archivo guardado, con copias v3, v4 y con `newTag`;
       - una copia que no pasa las comprobaciones no llama a `kekDerive`.
  5. **RT-21, parte b: el análisis de la copia.**
     - Antes de nada, la tabla del largo máximo que la app escribe en cada lista.
     - `checkCopyShape`, `COPY_LIST_MAX = 10_000` y `COPY_TREE_MAX_BYTES` calibrado como dice el Ruling, con la condición de parada.
     - Tests:
       - el modelo nunca queda por debajo de lo medido, con muestras de 1 MiB de cada forma: lista, conjunto, mapa y objeto;
       - ninguna forma legítima a 24 MiB + 4 KiB pasa del 80 % del presupuesto;
       - los cuatro archivos de prueba pasan;
       - se rechazan antes de decodificar:
         - la copia de `rt21_` (que pasa a verde);
         - 24 MiB de etiquetas `"a"` repetidas en entradas de 10 000;
         - 24 MiB de etiquetas distintas de 5 letras repartidas;
         - `tagColors`, carpetas y vistas con más de 10 000;
         - el `tags` de una vista con 2 000 vistas;
         - y lo mismo en una v3 sin versión;
       - `rt21Impact_…` pasa a `resistsACheckOfACopyOverTheListCapRefusesIt` (la comprobación da `COPY_DAMAGED`) y conserva la medida de la proporción con `VaultCrypto.read` sin los topes de copia;
       - una bóveda propia, no una copia, con 20 000 etiquetas en una entrada se sigue abriendo con la maestra y con la huella.
     - Si `scanJson` se factoriza, `ImportJsonTest` y el comportamiento de `checkJsonShape` no cambian ni un byte.
  6. **RT-21, parte c: el `OutOfMemoryError`.**
     - `COPY_NO_MEMORY` y `VAULT_NO_MEMORY` con su texto exacto, los cinco sitios donde se capturan, y `masterUnlockErrorText` con la lista ampliada.
     - Tests con la costura:
       - importar: el archivo de la bóveda y `settings.json` quedan iguales byte a byte y la carpeta temporal se borra;
       - comprobar: su carpeta se borra;
       - importar como bóveda nueva: no queda la bóveda nueva y la anterior sigue como estaba;
       - abrir con la maestra: sale `VAULT_NO_MEMORY`, no `WRONG_MASTER`, y el siguiente intento abre;
       - abrir con la huella: la huella sigue activada.
     - `ErrorTextsTest` fija los dos textos.
     - Filas nuevas en el inventario de la fase C (`task-10c-inventory.md`).
  7. **RT-22 (ii), solo si el dueño ha dicho que sí:** como dice el Ruling, con sus textos exactos y sus tests: hueco con otra bóveda con archivo, sin archivos, y `newVault` desde una bóveda abierta.
  8. **Cierre.**
     - Informe en `.superpowers/sdd/2026-11-12-clavero-2.0/redteam-fixes-report.md` y una copia en `docs/superpowers/handoff/sdd-2.0/`. Lleva una tabla por RT con el rojo y el verde y cada test, y las secciones «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior» con `archivo:línea`.
     - «Textos para el Abogado Digital (revisión final)»: `COPY_NO_MEMORY`, `VAULT_NO_MEMORY`, `SETTINGS_NEED_AN_OPEN_VAULT`, `wifiQrNameHidden`, las notas de RT-23 en el diseño y, si se hizo (ii), sus tres textos.
     - `pendientes.md`:
       - filas 24 y 52, «Hecho»;
       - fila 23, más la prueba de RT-24 en el móvil;
       - filas nuevas:
         - el riesgo con nombre de RT-21 (una bóveda con una lista de más de 10 000 que viene de una copia hecha a mano);
         - RT-21 en el móvil, con la fila 51;
         - RT-22 (ii), si no se hizo;
         - los informativos del ataque con su destino: el `.tmp` borrado por nombre entre bóvedas (aceptado: no se pierde ninguna copia, se reintenta), el `fsync` de la carpeta (aceptado), una nota antigua enorme en `MarkupText` (al móvil, con la fila 3) y el nombre hex sin comillas (aceptado: es del formato, Clavero siempre lo escribe entre comillas).
- **Nunca** rompe `SG1:`, `.sgenc`, `seguridad-vault` ni `seguridad-backup`. No añade dependencias, permisos ni hosts. Antes del cierre corre, por separado, `BackupCheckTest`, `BackupV3Test`, `BackupTest`, `VaultV4Test`, `VaultStoreTest`, `AppContainerVaultsTest`, `ErrorTextsTest` y `redteam.*`.
- **Después** (lo hace el controlador):
  1. El atacante, o un agente nuevo con `advisor-t12-attack.md` y `redteam-report.md`, pone su *worktree* al día (`git -C /home/user/Seguridad-rt merge --ff-only ccr-0aa27f95-u038cd`). Vuelve a comprobar cada arreglo en `redteam-recheck-1.md`, con tests `RedTeam20Recheck*Test.kt`, y busca cómo saltárselo:
     - mapas en lugar de listas, conjuntos en vistas, v3 sin versión, un miembro `entries` repetido, escapes y caracteres UTF-8 de varios bytes, formas repartidas justo bajo el presupuesto;
     - un `OutOfMemoryError` fuera de los bloques protegidos;
     - otras rutas a Ajustes (su buscador);
     - el hueco, que es un residuo ya decidido si no se hizo (ii);
     - otras pantallas que enseñan el nombre de la red a otra persona.
  2. Un hallazgo nuevo abre otra ronda. Con 5 rondas, decide el dueño.
  3. Clon limpio: las dos suites por separado, con `flock`, y `grep -rn "@Ignore" app/src/test/java/app/seguridad/redteam app/src/test/java/app/seguridad/ui/redteam` sale vacío.
  4. El informe publicado, `docs/auditorias/2026-11-17-ataque-2.0.md`, con el estado de cada RT (R39).
  5. Después, `code-review` alto y `security-review`.
---

Riesgos o dudas:
- **El presupuesto de RT-21 protege igual que la peor bóveda legítima, no más.** En un móvil de 192 MiB, una copia hostil justo bajo el presupuesto, o una bóveda legítima absurda (unas 160 000 entradas con 10 etiquetas cada una), puede no caber. La última defensa es `VAULT_NO_MEMORY`/`COPY_NO_MEMORY`, y su medida en el móvil va a la fila 51. La separación entre lo legítimo y lo hostil repartido es estrecha (unas 5,3 frente a 7,6 veces); por eso hay condición de parada.
- **`COPY_DAMAGED` para una copia de la 1.x que lleva una lista de más de 10 000 hecha a mano** es discutible como verdad («dañada»). Es un caso casi imposible; va al Abogado como riesgo con nombre.
- **RT-22 (ii) revisa el Ruling T2, duda 1** con la evidencia nueva, y es el dueño quien decide. Sin (ii), el residuo exige crear una bóveda en el hueco, lo que deja rastro.
- **Los textos «Aún no tienes bóveda»** (`SettingsScreen.kt:443`, `:701`; `HEALTH_NO_VAULT`) ya son inexactos cuando hay otras bóvedas, desde la tarea 2. Si no se hace (ii), van a una fila para el Abogado con la redacción «Esta bóveda está vacía…».
- **Cambiar `afterMasterUnlock` toca el desbloqueo**, que es código sensible al final del ciclo. Lo cubren los tests de la fila 45 (`KDF_COST_KEPT`) y de CRY-A02, y el atacante repasa ese diff (R32).
- **No ejecuté nada:** los costes por byte salen de la lectura y de las medidas del informe, y los calibra la parte b.

Revisión experto-seguridad-cripto:
- **Decisión (a):** un tope antes de reservar (`Vault.kt:412` pasa a hacer, además, el análisis antes de `toString`). CWE-400 y CWE-770. Sin topes en lo propio, para no perder ninguna bóveda (Ruling T1, dudas 4-6).
- **Decisión (b):** una sola decodificación. Lo comprobado (`BackupCheck.kt:196-236`) es lo que se sella y se enseña (`VaultStore.kt:1502-1517`). Y ningún segundo Argon2id (`Vault.kt:534`) para una copia que se va a rechazar.
- **Primitivas sin cambios:** AES-256-GCM con la AAD de la versión (`Vault.kt:213-255`) y Argon2id. Con RT-23, cada sellado usa un *nonce* aleatorio de 12 bytes, y el número de sellados con la misma clave sigue muy lejos de 2^32 (NIST SP 800-38D §8.3).
- **RT-22:** control de acceso (CWE-284). Con la regla de `canAddVault` (`AppContainer.kt:631-632`) vuelve COV2-1, con su KDoc como razón.
- **RT-24:** CWE-451. La pantalla enseña lo mismo que el código, con las marcas del Inspector (`tools/qr/Visible.kt:23`).

Revisión Ingeniero Móvil Senior:
- **Memoria:** el `heap` de una app va de 192 a 512 MiB según el móvil. Las capturas de `OutOfMemoryError` van solo dentro de `withContext(compute)`, como `ImportScreen.kt:308`, nunca en el hilo principal. El desbloqueo deja de decodificar dos veces (`VaultStore.kt:594` y `:488`), lo que reduce la basura de cualquier bóveda grande.
- **Ciclo de vida y E/S:** el `File.exists` de la regla nueva de Ajustes va en IO (`produceState`, como `VaultPickerScreen.kt:206`) y empieza cerrado. Las escrituras vuelven a comprobar la regla al usarla (`SettingsScreen.kt:633`, `:641`, `:650`).
- **Compose:** `visibleText` en `WifiQrScreen.kt:209`. El nombre está acotado por `WifiQr.problem` (4 096 caracteres), y el aviso se pinta a la vista sin cambiar el orden de O-4 (`WifiQrScreen.kt:211-215`).
- **Tests:** `jvm-check/build.gradle.kts:20-22` compila `redteam/` pero no `ui/`, así que RT-24 solo se prueba en la app. Gradle siempre de uno en uno, con `flock`, para no repetir el exit 137.

Modelo: el director (esfuerzo extra).
