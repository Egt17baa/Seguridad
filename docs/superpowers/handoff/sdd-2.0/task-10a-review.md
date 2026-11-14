# Tarea 10, fase A: revisión (cumplimiento del encargo y calidad)

- **Alcance:** de `0f617da` a `7e868de`. `c8f04b2` solo añade el informe, así que las líneas citadas valen igual en `7e868de` y en `c8f04b2`.
- **Leído:**
  - `CLAUDE.md`, `experto-seguridad-cripto/SKILL.md` (con «Trabajo conjunto con superpowers»: el revisor aplica la lista y un hallazgo de seguridad confirmado es Important como mínimo; ninguno de los de esta revisión lo es) y el índice de `references/auditoria.md`, y las skills `desarrollo-movil-senior` y `auditoria-seguridad-movil` (MASVS: STORAGE, PLATFORM, CODE y PRIVACY son las categorías que toca esta fase);
  - el encargo `task-10-brief.md` y el informe `task-10a-report.md`;
  - `progress.md`: el «Ruling (advisor, T10)», la «Owner decision (14-11-2026)» y los Ruling anteriores que tocan esta fase (COV2-1, T2 I-2, T6 I-1);
  - el diseño: «Ayuda y personalización» y la revisión inicial del Abogado (O-2, O-7, R-1, R-2 y R-10);
  - `pendientes.md` y el diff completo, más el código que lo rodea (`VaultStore.modifyData`, `commit`, `replace`, `createLocked`, `opened`, `unlockWithKey`, `launchUnlockWithKey`, `UnlockVault`, `VaultRegistry`, `HealthScreen`, el manifiesto, las reglas de copia y `ForbiddenWords`). Las líneas citadas las comprobé con `grep -n` en el árbol.
- **Gradle:** no lo he ejecutado, como pide el encargo.

## Strengths

- **La guía, bien pensada.**
  - El número `guideSeen` (0, 1 o 2) resuelve de verdad el problema de `appJson`, que no escribe los valores por defecto (`settings/Settings.kt:98`).
  - `guideDue` y `withGuideSeen()` hacen lo que dicen:
    - un número más alto que el actual da `NONE` (`:123-128`);
    - `withGuideSeen()` nunca baja el valor (`:131`);
    - un negativo da `FULL`.
  - Está probado en `SettingsTest.theGuideDueGoesByTheVersionSeen`.
- **La señal del primer arranque es fiable en los caminos reales.**
  - `decideRenameNotice` corre de forma síncrona antes del primer fotograma (`SeguridadApp.kt:74-77`).
  - `vaults.json` vive en `files/`, no en `files/vault/` (`AppContainer.kt:182`), así que nada llena la carpeta de la bóveda antes de que se decida.
  - `appearanceNoticePending` solo lo escriben `decidedAtFirstStart` y `OnceNotice.shown`, que pone `false` (`RenameNotice.kt:28`); nada lo vuelve a poner en `null`.
  - **La copia del sistema no interviene.** `allowBackup="false"`, y `backup_rules.xml` y `data_extraction_rules.xml` excluyen todos los dominios, también en `device-transfer`, así que una restauración no puede hacer pasar un móvil nuevo por actualizado.
  - **Borrar los datos** deja el móvil como una instalación nueva, con la guía completa: es lo correcto.
- **El archivo de prueba de la 1.4.3 es verosímil.**
  - El orden de sus claves es el de la declaración de `Settings` en `a3ac942`.
  - Faltan justo los valores por defecto (`kdf`, `autoBackupKeep`), como manda `appJson` en `a3ac942` (`explicitNulls = false`, `encodeDefaults` por defecto).
  - `theme` vale `SYSTEM`, que no es el valor por defecto (`DARK`), y `renameNoticePending` vale `false`, como en una instalación de la 1.3.0 o posterior.
  - El informe documenta cómo se generó, con el test de una sola vez.
- **Bajar de versión es seguro.** El `appJson` de la 1.4.3 tiene `ignoreUnknownKeys = true`, así que lee el archivo de la 2.0.
  - Si lo reescribe, se pierden `guideSeen`, `simpleMode` y `appearanceNoticePending`, y una nueva subida vuelve a decidir «actualización»: guía de novedades y modo apagado.
  - Es inofensivo y nunca da un error.
- **El modo sencillo es un filtro y nada más.**
  - `visibleTools` es el único filtro (`tools/ToolCatalog.kt:40`), y la navegación no lo lee.
  - `SimpleModeTest.onlyTheToolsListReadsIt` recorre todo `app/src/main/java` y fija que solo lo leen seis archivos. Es una buena protección contra regresiones de O-7.
  - La tarjeta «Cómo protegemos tus datos» queda fuera del filtro.
  - Toda ruta sigue siendo un `composable(...)` (`everyToolRouteStaysReachable`).
- **El gancho de la primera bóveda está en el sitio correcto.**
  - `beforeCreate` se llama después de Argon2id y antes de `commit` (`VaultStore.kt:534`).
  - `beforeReplace` se llama después de comprobar la contraseña, la estructura y los adjuntos de la copia (`VaultStore.kt:1483`). Una contraseña equivocada o una copia dañada no deciden nada.
  - `firstVaultMade` nunca lanza (`AppContainer.kt:460-469`), y `SettingsStore.firstVault` deja la decisión en memoria si no se puede guardar (`SettingsStore.kt:79-87`).
  - **Cerrojos:** `firstVaultMade` toma el mutex de los ajustes dentro del de la bóveda, que es el mismo orden que ya usaba `turnOffAutoBackupForNewVault`, así que no hay inversión nueva.
- **`clearAllHistory` cumple la decisión (b).**
  - La época se toma al abrir la confirmación (`SettingsScreen.kt:725`) y se pasa a `modifyData(sinceLock = …)` (`VaultStore.kt:1236-1250`).
  - Los tres resultados (`null`, `0` y `n`) están bien distinguidos, y `ran` detecta el rechazo por bloqueo antes de `change`.
  - Un bloqueo cierra el diálogo, y el diálogo lleva `FilterObscuredTouches()`.
  - Los errores pasan por `storeSaveErrorText`.
  - `copiesMadeBeforeKeepTheHistory` comprueba el texto contra el código: exporta, borra el historial y restaura.
- **La huella tardía de otra bóveda.**
  - El camino real es `UnlockVault`, que captura su `store` (`VaultScreen.kt:431-436`), seguido de `unlockWithKey` sobre una bóveda inactiva y de `opened()` con `!active()`. Ese camino bloquea sin lanzar (`VaultStore.kt:497`), así que `onWrongKey` no corre y la huella no se apaga.
  - El test llama a ese mismo `unlockWithKey` sin `try`, así que cualquier excepción lo hace fallar. Su límite: antes de la puerta, A todavía guarda su archivo (i-6).
- **El buscador.**
  - El índice son las mismas constantes que pinta la pantalla.
  - `everySectionIsIndexed` y `everyIndexedLineIsDrawn` fallan en los dos sentidos, y el informe declara con honradez el límite de un control nuevo dentro de una sección que ya existe.
  - La consulta se corta en `onValueChange` y otra vez en `find` (`SettingsSearch.kt:90-103`), sin partir nunca un par sustituto.
  - Solo se ofrecen las secciones pintadas en ese momento.
- **Calidad.**
  - Los textos nuevos están fijados palabra por palabra.
  - Ningún `Log.` nuevo, nada en `rememberSaveable`, y el catálogo y el índice salieron de `ui/` para que corran en jvm-check.
  - El informe es honrado sobre el TDD y compensa con mutaciones, documentadas una a una.

## Issues

### Critical

Ninguno.

### Important

Ninguno.

### Minor

**m-1. El texto bajo el interruptor afirma algo falso con el modo apagado** (`tools/ToolCatalog.kt:49`; se pinta siempre, `SettingsScreen.kt:664`).
- **Escenario:** el usuario que actualiza tiene el modo apagado y lee en Ajustes «Herramientas enseña solo cifrar texto, cifrar archivo, el generador y la salud de la bóveda.». Herramientas le enseña las doce herramientas. El texto está en presente y no dice que depende del interruptor.
- **Regla:** la de veracidad del proyecto (O-1 de la 1.3.1).
- **Arreglo:** poner el texto en condicional, por ejemplo «Encendido, Herramientas enseña solo cifrar texto, cifrar archivo, el generador y la salud de la bóveda. No oculta avisos ni ajustes.».
- **Además:** actualizar `theNewTextsWordForWord` y apuntar el texto en «Textos para el Abogado».

**m-2. «Salud de la bóveda» desde Herramientas no lleva a ninguna parte con la bóveda bloqueada** (`tools/ToolCatalog.kt:22`; `HealthScreen.kt:91-94`).
- **Escenario:** antes, la pantalla solo se abría desde la bóveda abierta, así que el estado bloqueado era de paso. Ahora es un camino normal, y es una de las cuatro tarjetas del modo sencillo, pensado para quien sabe menos. Ese usuario la toca con la bóveda bloqueada y solo lee «Desbloquea la bóveda para ver su salud.», sin forma de hacerlo desde ahí. Sin bóveda (duda 7), el texto es además falso.
- **Arreglo, en la fase B junto con los textos de error:**
  - con la bóveda bloqueada, envolver la pantalla en `VaultGate`, como `contacts/…`, o poner un botón a la pestaña Bóveda;
  - con `NoVault`, un texto propio («Aún no tienes bóveda…»).
- Hay que dejarlo apuntado en el registro para que no se pierda entre las dos fases.

**m-3. La rama «ninguna bóveda tiene archivo» de `firstVaultMade` no la prueba ningún test** (`AppContainer.kt:462`).
- **Los tests de ahora:** todos los de `SimpleModeFirstVaultTest` que crean una segunda bóveda fijan antes `simpleMode` a un valor (`c.settings.update { it.copy(simpleMode = false) }`) o lo dejan decidido. Así, `withFirstVault` ya no cambia nada aunque no existiera la comprobación de archivos.
- **La mutación** de quitar `none { … }` no haría fallar ningún test. Lo digo por lectura de los tests, sin ejecutarla, porque esta revisión no corre Gradle.
- **Cuándo importa la rama:** solo con `simpleMode == null` y una bóveda con archivo. Es el caso de la duda 4, una versión de desarrollo, o el de un `firstVault` que no se pudo guardar.
- **Test que falta:** un `settings.json` con `appearanceNoticePending` ya decidido, sin `simpleMode` y con un `vault.bin`. Crear una segunda bóveda, o importar como bóveda nueva, deja `simpleMode` en `null`.

**m-4. Las tarjetas de Herramientas al 200 %** (`ToolsScreen.kt:46`, `GridCells.Fixed(2)`).
- **La cuadrícula es anterior a este diff.** Esta fase la hace más visible, porque añade el subtítulo de «Salud» y porque el modo sencillo es lo primero que ve una instalación nueva.
- **Escenario, estimado por cálculo y sin probar en un móvil:** en un móvil de 360 dp, cada columna deja unos 135 dp de texto. Con la letra al 200 %, palabras como «Contraseñas» (en el subtítulo nuevo de «Salud») o «Laboratorio» quizá no quepan en una línea, y Compose las partiría por la mitad, sin guion.
- **El test no lo ve:** `textFollowsTheSystemScale` solo mira que no haya `maxLines` ni `TextOverflow`, y en este caso el texto no se corta: se rompe la palabra.
- **Arreglo, sin la palabra `fontScale`,** que `textFollowsTheSystemScale` prohíbe en `ui/`:
  - columnas que crecen con la letra: `GridCells.Adaptive(minSize = with(LocalDensity.current) { 11.sp.toDp() } * 14)`, o una constante propia en `sp` convertida a `dp`;
  - si se prefiere leer la escala, el test tiene que cambiar para prohibir solo que se fije (`LocalDensity provides`, `fontScale =`) y permitir que se lea.

  Con cuatro tarjetas, el modo sencillo cabe bien en una columna.
- **En el móvil:** confirmarlo en la fila de la comprobación al 200 %, antes de decidir el arreglo.

**m-5. El aviso de la sección del historial es falso sin bóveda e impreciso con ella bloqueada** (`SettingsScreen.kt:728`).
- **Escenario:** en una instalación nueva, Ajustes enseña «Historial de las entradas» con «Abre la bóveda (pestaña Bóveda) para cambiar este ajuste.». No hay bóveda que abrir, y no es un ajuste: es una acción.
- **Arreglo, el mismo patrón que la sección «Copia de la bóveda»** (`SettingsScreen.kt:676-683`, un aviso por estado):
  - con `NoVault`, no pintar la sección (el buscador ya filtra las secciones sin ancla) o decir «Aún no tienes bóveda.»;
  - con la bóveda bloqueada, «Abre la bóveda (pestaña Bóveda) para borrar el historial.».

**m-6. «Cada entrada guarda sus versiones anteriores.» no es exacto** (`vault/TrashTexts.kt:96`).
- **Por qué:** las identidades y los contactos nunca guardan historial (KDoc de `upsert`, `VaultStore.kt:834`).
- **Arreglo:** «Al cambiar una entrada se guarda su versión anterior. Aquí puedes borrar las de todas a la vez.», o una redacción parecida que siga M-3. Apuntarlo para el Abogado.

**m-7. `COPIES_KEEP_IT` y la rotación de las copias** (`vault/TrashTexts.kt:16`, `:101`).
- **Escenario:**
  1. El usuario tiene la copia automática «con cada cambio» y «Conservar 1 copia».
  2. Borra todo el historial.
  3. `clearAllHistory` es un guardado normal y sube la revisión, así que se hace una copia nueva sin historial y la rotación borra la anterior, la que lo tenía.
  4. Ninguna copia automática conserva ya el historial, aunque el texto dice «Las copias ya hechas (automáticas o exportadas) lo conservan».
- **Alcance:** el texto se heredó de la tarea 3 y no es propio de esta fase. La imprecisión va en el sentido de conservar menos de lo que se dice, así que no perjudica la privacidad. Es la misma familia que la fila 19 de `pendientes.md`.
- **Arreglo:** apuntarlo en la fila 19, o en una fila nueva para el Abogado final, con una redacción propuesta: «Las copias ya hechas (automáticas o exportadas) lo conservan hasta que se borren; la copia automática quita las más antiguas.».
- No hace falta cambiar código en la fase A.

**m-8. `pendientes.md` no está al día** (en el diff solo cambia `progress.md`).
- **Fila 1** (`clearAllHistory`): sigue «Abierto», y debería pasar a «Hecho en la tarea 10, fase A (`7e868de`)».
- **Fila 8:** le faltan `simpleMode` y `guideSeen` en `settings.json` (`guideSeen = 1` revela que el móvil tuvo Clavero 1.x) y `lastOpened` en `vaults.json`.
- **Fila nueva:** la comprobación en el móvil con la letra al 200 % que propone el informe.
- **Responsable:** si las filas las actualiza el controlador al cerrar la tarea, basta con que lo haga; si no, que las añada la ronda 1.

**m-9. TalkBack podría leer «Modo sencillo» dos veces** (`SettingsScreen.kt:662-663`; título en `:662`, etiqueta en `:663`). Sin comprobar.
- **Por qué:** el título de la sección y la etiqueta del interruptor son el mismo texto, así que, por lectura del código, se oiría «Modo sencillo», y después «Modo sencillo, interruptor, desactivado» (R-1).
- **Dos arreglos obvios que no sirven:**
  - quitar el título rompe `everySectionIsIndexed`, que pide `Section(SettingItem.X.title…)` con su ancla;
  - cambiar la etiqueta del interruptor deja «apaga «Modo sencillo» en Ajustes» apuntando a un interruptor que ya no se llama así, y eso lo fija `theListSaysWhereTheOthersAre`.
- **Arreglo, sin cambiar código:** pasarlo a la comprobación con TalkBack en el móvil (tarea 11, filas 12 y 13 de `pendientes.md`).
- **Si allí molesta:** conservar «Modo sencillo» como nombre visible del interruptor y quitar del título de la sección solo lo que se anuncia. Por ejemplo, un parámetro de `Section` que haga el título `invisibleToUser` cuando repite la etiqueta del interruptor; el buscador y el test siguen viendo el título.

### Informativos (no bloquean)

- **i-1. Un `settings.json` de la 2.0 dañado** se lee con los valores por defecto. Como `fileExistedAtLoad` vale entonces `true`, se vuelve a decidir «actualización»: el modo sencillo se apaga y toca la guía de novedades.
  - Es inofensivo: el modo apagado enseña más y no oculta nada.
  - Basta con saberlo; no pide cambio.
- **i-2. El resultado de «Borrar todo el historial» sobrevive a un bloqueo.** Si el guardado termina y justo después se bloquea, el snackbar dice «Historial borrado de N entradas.».
  - Es un número, no un título, una etiqueta ni una carpeta, así que no lo cubre el Ruling T6 I-1.
  - Si se quiere coherencia con ese Ruling, hay que descartar el snackbar cuando `unlocked` pasa a `false`.
- **i-3. Salir de Ajustes a mitad del guardado lo corta.** `HistorySection` lanza el guardado en `rememberCoroutineScope`, así que el guardado se cancela sin mensaje y el historial sigue ahí.
  - Es lo mismo que hace `TrashScreen` con «Vaciar papelera», y el usuario no ve ninguna confirmación de éxito, así que nada engaña.
  - Se acepta igual que allí.
- **i-4. Un resultado de «Aspecto» abre la pantalla arriba,** no en la sección. «Aspecto» es corta, así que basta.
- **i-6. La huella tardía de otra bóveda escribe su archivo antes de llegar a la puerta** (`VaultStore.kt:656-683`, `:456-479`; código de la tarea 2, fuera de este diff).
  - **El camino:** `unlockWithKeyLocked` de la bóveda A, que ya no es la que se enseña, pasa sus comprobaciones y hace todo esto antes de que `opened()` la rechace con `!active()` (`:497`):
    - descifra las entradas;
    - vacía de su papelera lo que tiene más de 30 días (`purgedTrash`);
    - guarda A con un guardado de contabilidad (`saveBookkeeping`, `:462`);
    - pasa `collectGarbage`.
  - **No mezcla bóvedas:** es el archivo de A con la clave de A, y la persona pidió la huella para A, así que no cae en la otra bóveda (foco 2 de la tarea 2).
  - **Pero deja un efecto tardío,** algo que la decisión (b) quiere evitar («un bloqueo o un borrado a mitad no deja un resultado tardío»).
  - **Arreglo sugerido, para la revisión de la rama (parte «formato y bóvedas»):**
    - un `if (!active()) { key.fill(0); return }` al principio de `unlockWithKeyLocked`;
    - en `aLateFingerprintOfAnotherVaultOpensNothing`, comprobar que el archivo de A no cambia de bytes.
  - **Lo que prueba el test de JVM:** solo la mitad de la bóveda (no se abre nada ni se lanza ninguna excepción).
  - **Lo que no se apaga la huella** se apoya en leer la parte de la interfaz:
    - `launchUnlockWithKey` solo llama a `onWrongKey` con `StaleBiometricKey` (`VaultStore.kt:126-132`);
    - `UnlockVault` pasa ahí `BiometricVault.disable` (`VaultScreen.kt:432-435`).
- **i-5. Si `commit` falla después del gancho, el modo sencillo ya está decidido.** `beforeCreate` y `beforeReplace` lo deciden antes de `commit`, y si el guardado falla no se deshace en `undo(prepared)`.
  - El modo puede quedar encendido sin bóveda, o apagado tras una importación que no se guardó.
  - Es inofensivo, porque el modo solo filtra la lista.

## Textos para el Abogado Digital (revisión final)

Todos los textos de esta lista pasan `ForbiddenWords`: comprobé a mano cada redacción propuesta contra su lista (`anónim`, `imposible`, `100 %`, `de verdad`, `validez legal`, `sospechos`, «limpio/a», «seguro/a» y «garantiza» sin «no»), y ninguna nombra otra app.

1. **Los textos nuevos de la fase A,** que ya están en la tabla del informe (`task-10a-report.md`). Los cambios propuestos aquí son:
   - **m-1:** «Encendido, Herramientas enseña solo cifrar texto, cifrar archivo, el generador y la salud de la bóveda. No oculta avisos ni ajustes.» (sustituye a `SIMPLE_MODE_SHORT`, `tools/ToolCatalog.kt:49`);
   - **m-5:** con `NoVault`, «Aún no tienes bóveda.» (o no pintar la sección); con la bóveda bloqueada, «Abre la bóveda (pestaña Bóveda) para borrar el historial.»;
   - **m-6:** «Al cambiar una entrada se guarda su versión anterior. Aquí puedes borrar las de todas a la vez.» (sustituye a `HISTORY_ALL_SHORT`, `vault/TrashTexts.kt:96`);
   - **m-2 y la duda 7 (fase B):** un texto propio de «Salud» sin bóveda, por ejemplo «Aún no tienes bóveda.».
2. **O-2:** `COPIES_KEEP_IT` frente a la rotación de la copia automática (m-7), con la redacción propuesta «Las copias ya hechas (automáticas o exportadas) lo conservan hasta que se borren; la copia automática quita las más antiguas.». Va junto a la fila 19.
3. **O-7:** «No oculta avisos ni ajustes.» y «Hay más herramientas. Para verlas, apaga «Modo sencillo» en Ajustes.». Son verdad según el código (`SimpleModeTest`).
4. **Para la política (fila 8):**
   - `settings.json` guarda sin cifrar si el modo sencillo está encendido y la versión de la guía vista, que revela que el móvil tuvo Clavero 1.x;
   - `vaults.json` guarda el id de la última bóveda abierta, sin nombre. Con varias bóvedas, dice cuál se usó por última vez.

## Dudas: recomendación

| # | Duda | Recomendación | ¿Del dueño o técnica? |
|---|---|---|---|
| 1 | `null` se lee como apagado: una instalación nueva ve todas las herramientas hasta su primera bóveda | Dejarlo así. Es lo que pide el encargo («null → `false`») y lo más prudente: nunca oculta nada. Al crear la bóveda desaparecen ocho tarjetas, y la línea «Hay más herramientas…» lo explica | Técnica, segura. Si el dueño prefiere encenderlo ya en el primer arranque de una instalación nueva, es una línea en `decidedAtFirstStart`, pero contradice el Ruling T10 («la primera bóveda desde «Importar copia» deja el modo sencillo apagado») |
| 2 | «Salud de la bóveda» como tarjeta en los dos modos | Dejarla en los dos. Con ella, el modo sencillo es un subconjunto del completo, y quitarla al apagar el modo sería raro. Hay que arreglar antes el camino con la bóveda bloqueada (m-2) | Técnica. El dueño puede opinar sobre la presentación, pero no es necesario |
| 3 | Un `settings.json` de la 1.4.3 sin bóveda cuenta como actualización | Dejarlo así. Quien tenía la 1.4.3 sin bóveda pudo usar las herramientas sin ella, y ocultárselas sería justo lo que «apagado al actualizar» quiere evitar. La guía de novedades no le deja sin el aviso importante: al crear la bóveda, «Crea tu bóveda» tiene la casilla «Entiendo que si olvido la contraseña maestra nadie puede recuperar lo que guarde» (`VaultScreen.kt:381`, `VaultPickerScreen.kt:429`) | Técnica, segura. En la fase B, conviene que la guía de novedades no dé por hecho que hay una bóveda |
| 4 | Versiones de desarrollo de la 2.0: `guideSeen = 0` y el modo sin decidir | Aceptarlo. Solo afecta a móviles de prueba, y la guía completa es inofensiva. Pero falta el test de la rama que les afecta (m-3). Hay que confirmar con el dueño que no ha instalado una versión de desarrollo de la 2.0 en su móvil de uso diario; si lo ha hecho, verá la guía completa, sin más | Técnica, con una pregunta de hecho al dueño |
| 5 | El interruptor se cambia con la bóveda bloqueada | Aceptarlo. COV2-1 protege los ajustes que bajan la protección: el bloqueo, el portapapeles, Argon2id y la carpeta de las copias. El modo sencillo no baja ninguna: quien tenga el móvil un momento solo ve más o menos tarjetas, y cada herramienta tiene su propio guardián y no lee la bóveda sin abrirla. Además, el modo no es una forma de esconder nada, y ningún texto debe presentarlo así | Técnica, segura. Solo es del dueño si quiere que todos los ajustes de Ajustes se comporten igual |
| 6 | «Importar como bóveda nueva» como primera bóveda deja el modo apagado | Aceptarlo. Es coherente con «Importar copia» y con el Ruling T10 («si el diseño no dice otra cosa»). Quien trae una copia ya conoce la app | Técnica, segura |
| 7 | «Salud» sin bóveda dice «Desbloquea…» | Arreglarlo en la fase B, junto con m-2 (`VaultGate` o un botón, y un texto propio para `NoVault`). Hay que apuntarlo en el registro como entrega de la fase B | Técnica |
| 8 | La última bóveda sin su archivo arranca vacía, en «Crea tu bóveda» | Aceptarlo. Saltar en silencio a otra bóveda haría creer al usuario que está en la suya. Con el selector a la vista, el usuario decide. Lo fija `aListedLastVaultWithoutItsFileIsShownEmpty` | Técnica, segura |
| 9 | Los destinos de `MissionAction` en `everyToolRouteStaysReachable` | Ampliar el test en la fase B, como dice el Ruling T10 («test de cada aviso y cada destino de `MissionAction`»). Debe quedar en la lista de la revisión de la fase B | Técnica |

## Revisión experto-seguridad-cripto

- **Criptografía:** ninguna primitiva, formato, red ni registro nuevos. `settings.json` gana dos campos opcionales, y la 1.4.3 los ignora (`ignoreUnknownKeys` en `a3ac942`).
- **O-7:**
  - el modo sencillo solo filtra la lista (`tools/ToolCatalog.kt:40`, `ToolsScreen.kt:52-53`);
  - ningún aviso, ajuste de protección ni ruta lo lee, y `SimpleModeTest.onlyTheToolsListReadsIt` lo fija sobre todo `app/src/main/java`;
  - la tarjeta de protección queda fuera del filtro (`ToolsScreen.kt`, `key = "about"`).
  - **Cumple.**
- **Decisión (b), la carrera entre comprobar y usar:**
  - `clearAllHistory(sinceLock)` usa la época tomada al abrir la confirmación (`SettingsScreen.kt:725`, `VaultStore.kt:1236`);
  - un bloqueo, aunque se vuelva a abrir la bóveda, no borra nada (`EntryHistoryTest.clearAllHistoryAfterALockClearsNothing`);
  - la mutación sin `sinceLock` hace fallar el test.
  - **Cumple.**
- **O-2, la veracidad:**
  - «Las copias ya hechas lo conservan» es verdad en el momento del borrado (`copiesMadeBeforeKeepTheHistory`), con el matiz de la rotación (m-7, para el Abogado);
  - «también de las de la papelera» es verdad, porque se recorre `data.entries` con `withoutHistory`;
  - nunca dice «irrecuperable» ni «borrado seguro».
  - Lo único falso es el texto del interruptor con el modo apagado (m-1).
- **La huella y la última bóveda:**
  - una huella tardía para la bóveda A, cuando ya se enseña la B, acaba en `opened()` con `!active()`, que borra la clave y deja A bloqueada (`VaultStore.kt:497`);
  - no es `StaleBiometricKey`, así que `onWrongKey` no apaga la huella de A (`VaultScreen.kt:431-436`);
  - el test lo ejercita sin `try`, pero solo prueba la mitad de la bóveda; que la huella no se apaga se apoya en leer `launchUnlockWithKey` (`VaultStore.kt:126-132`). La bóveda A, eso sí, se escribe antes de llegar a la puerta (i-6);
  - un `lastOpened` manipulado (un id que no existe o `../vault`) cae en la primera bóveda y nunca llega a una ruta (`VaultRegistry.kt:76`).
  - **Cumple.**
- **El buscador (decisión (a) de la 1.4.0):**
  - la consulta se corta a 60 puntos de código antes de `FuzzySearch.prepare` (`SettingsSearch.kt:90-99`), y también en `onValueChange`;
  - una consulta de 2 MB cuesta como una corta (`aHugeQueryIsCut`);
  - el índice tiene solo constantes, sin plantillas ni datos de la bóveda (`indexesFixedTextsOnly`);
  - la consulta vive en `remember`, nunca en estado guardado;
  - el teclado no aprende de lo que se escribe (`NoLearningInput`, A7).
  - **Cumple.**
- **Los datos sin cifrar:**
  - `simpleMode`: una preferencia de presentación, sin datos de la bóveda;
  - `guideSeen = 1`: revela que el móvil tuvo Clavero 1.x, lo mismo que ya revelan la carpeta `files/vault/` o el `settings.json` antiguo;
  - `lastOpened`: el id local de la última bóveda abierta, sin nombre (O-1, opción A). Con varias bóvedas dice cuál se usó por última vez, y eso debe decirlo la política (fila 8, m-8).
  - Nada de esto filtra el contenido de una bóveda.
- **Robustez:**
  - el gancho de la primera bóveda nunca lanza, y sus fallos dejan el modo sin decidir, que es apagado (`firstVaultNeverThrows`);
  - el gancho solo corre después de comprobar la contraseña y la copia, así que una copia hostil o una contraseña equivocada no deciden nada.
- **Lo que falta:** nada de seguridad. m-1, m-5 y m-6 son de veracidad del texto; m-3 es la fuerza de un test.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:**
  - la consulta y `confirmSince` usan `remember`, nunca `rememberSaveable`, y sobreviven al giro gracias a `configChanges` (M-4 de la 1.4.2);
  - las anclas se registran y se quitan con `DisposableEffect` (`SettingsScreen.kt:188-196`), así que un resultado solo se ofrece si su sección está compuesta; `FingerprintKeySection` vuelve antes de su `Section` y su ancla desaparece bien;
  - el diálogo se cierra con un bloqueo (`LaunchedEffect(unlocked)`).
- **Concurrencia:**
  - `firstVaultMade` lee el disco en `Dispatchers.IO` y relanza `CancellationException`;
  - corre dentro de `NonCancellable` y del mutex de la bóveda, con el mismo orden de cerrojos de antes;
  - `clearAllHistory` va bajo el mutex de `modifyData`, y `busy` se suelta en `finally`.
  - Salir de la pantalla a mitad del guardado lo corta, como en `TrashScreen` (i-3).
- **Estado y arquitectura:**
  - `ToolCatalog` y `SettingsSearch` fuera de `ui/`, que es lo correcto para jvm-check;
  - `Section` gana un `modifier` opcional sin romper a quien lo llama;
  - el código muerto es solo `GuideDue` y `withGuideSeen`, que ya tienen tests y se usarán en la fase B, así que se acepta.
- **Rendimiento:** 17 entradas, con `remember(query)`. El coste en el hilo principal es despreciable.
- **Accesibilidad (R-1):**
  - el interruptor ocupa toda la fila, con `toggleable` y `Role.Switch`;
  - los resultados son botones que se pueden enfocar;
  - TalkBack podría repetir el título y la etiqueta del interruptor (m-9, sin comprobar; para el móvil).
- **Letra al 200 %:**
  - no hay `maxLines`, `TextOverflow` ni `fontScale` propio, y Ajustes se desplaza;
  - la cuadrícula fija de dos columnas, anterior a este diff, podría partir palabras largas (m-4, estimado; para el móvil).
  - Falta la fila de la comprobación en el móvil (m-8).
- **Fuerza de los tests:**
  - son reales en la guía (TDD estricto), `clearAllHistory`, la huella tardía, la última bóveda y la cobertura del índice en los dos sentidos;
  - `onlyTheToolsListReadsIt` y `textFollowsTheSystemScale` son escaneos del código fuente, que bastan para fijar O-7 aunque sean heurísticos;
  - la rama de `firstVaultMade` que mira los archivos queda sin probar (m-3).
- **Maestro:** el informe enumera bien los flujos que se rompen, que ya están en la fila 27 de `pendientes.md` para la tarea 11.

## Veredicto

**Aprobada con menores.**
- No hay ningún Critical ni Important. Los campos, el gancho, el filtro, el buscador, el botón del historial y la última bóveda cumplen el encargo, el Ruling T10 y la decisión del dueño del 14-11-2026.
- Por `CLAUDE.md` («Detalles menores: se arreglan todos»), van a la ronda 1:
  - **arreglos de código y de texto:** m-1, m-3, m-5 y m-6;
  - **al móvil antes de arreglar:** m-4 (con el arreglo sin `fontScale`) y m-9 (TalkBack);
  - **apuntes:** m-7 al Abogado final (fila 19 o una fila nueva), m-8 en `pendientes.md` y m-2 y la duda 7 en el registro como entregas de la fase B.
- **i-6** (la huella tardía escribe el archivo de A) es código de la tarea 2: el controlador decide si entra en la ronda 1 o en la revisión de la rama.
- Los demás informativos (i-1 a i-5) no piden cambio.
