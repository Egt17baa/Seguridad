# Director: aprobación de la auditoría completa de la 2.0 y encargo de su ronda de arreglos (21-11-2026)

**Pregunta:** ¿se aprueba el informe consolidado `docs/auditorias/2026-11-21-auditoria-2.0.md` (HEAD `ecd9a3f`, con el código de `69c967b`)? ¿Qué destino tiene cada hallazgo, cuál es el encargo exacto de la ronda que sigue y qué hay que cambiar del informe?

**Qué se comprobó:**
- el informe entero y las cinco partes (`sdd-2.0/final-audit-a.md` … `-e.md`, con la re-lectura de B-2 y B-1 al final de la parte D);
- la decisión del dueño sobre B-2 (registro, 21-11-2026);
- mi cierre de la ronda única (`advisor-t12-review-fixes-cierre.md`);
- cada hallazgo con destino «Ronda», leído en su `archivo:línea` del código de `69c967b` (que no cambia hasta `ecd9a3f`).

No ejecuté Gradle. El `grep` de nombres de modelos sobre el árbol, con la lista de la skill `advisor` y sin `.claude/skills/advisor` ni `app/src/main/assets`, solo da `docs/superpowers/handoff/sdd-2.0/branch-rereview-compilacion.md`, la excepción permitida.

Veredicto: apruebo la consolidación con siete cambios de documento (sección «Cambios del informe»). La ronda arregla todo lo que tiene arreglo de código (B-2, B-1, A-1 con B-3, D-1, E-1, la fila 84, RC-1 a RC-6, C-5, D-2, D-4, B-4 con la opción (c) y los textos A-2 y RC-5) y añade los tests de C-1, C-7 y A-3. C-6, C-2 y C-3 quedan como riesgos con nombre, y la fila 82 se cierra con E-1.

## Motivos

### 1. Destino de cada hallazgo

| ID | Destino | Por qué |
|---|---|---|
| B-2 (Media) | **Ronda, punto 1**; README; móvil | Decisión del dueño. Una línea en `app/src/main/AndroidManifest.xml:21-25`. La re-lectura de la parte D ya comprobó que no rompe la tarea, el icono, `singleTop`, los selectores, el escáner, «Compartir» ni el `FileProvider` |
| C-3 (Media) | Riesgo (dueño, 20-11) | Es configuración de GitHub y no hay nada que cambiar en el código |
| A-1 + B-3 | **Ronda, punto 3** | Comprobado: `registry.remove(id)` va antes que `forgetBiometric(id)` (`AppContainer.kt:746-751`), en contra del orden de CRY-A02 que ya siguen `ui/screens/VaultScreen.kt:412` y `:516`. `clear` vacía las claves pero deja el archivo de preferencias (`android/BiometricVault.kt:188-195`). El comentario de `:141-143` es falso: la clave envuelve la clave de datos, que también abre las copias hechas antes del borrado |
| B-1 | **Ronda, punto 2**; móvil | `MainActivity.kt:26` dice que la app no toma *extras*, pero cada `NavController` creado por `key(vault)` (`ui/AppNavHost.kt:202-203`) vuelve a leer el *intent* de arranque (el controlador lo comprobó en el *bytecode*) |
| C-1 | Hecho; **su test, en la Ronda (punto 15)** | Sin el test, la regla «ningún nombre de modelo» depende de un `grep` a mano |
| D-1 | **Ronda, punto 6**: 120 s y texto nuevo | Comprobado: `afterUnlock` espera `copyBefore()` sin límite (`vault/PendingUpgrade.kt:84-88`), y `copyBeforeUpgrade` es `scope.async { lock.withLock { … } }.await()` (`vault/AutoBackup.kt:749`) |
| E-1 (fila 82) | **Ronda, punto 8**; la fila 82 se cierra | Ver «E-1 frente a la fila 82» |
| RC-1 | **Ronda, punto 7** | Mi cierre |
| Fila 84 | **Ronda, punto 9**, y un riesgo con nombre nuevo | Ver «La fila 84» |
| A-2 | **Ronda, punto 12** (texto y test); Abogado | Comprobado: el texto (`ui/screens/SettingsScreen.kt:668`) dice «Lo ya cifrado guarda su coste», pero el archivo de la bóveda sube al coste de Ajustes en el siguiente desbloqueo con la maestra (`vault/Vault.kt:601-612`). Es un texto falso sobre el código, así que lo arregla el equipo técnico antes del Abogado |
| A-3 | **Ronda, punto 17**, opción (a) | Ver «A-3» |
| B-4 | **Ronda, punto 11**: (c) y (a); (b) descartada en la 2.0 | Ver «B-4» |
| C-2 | Riesgo (dueño, 20-11) | Arreglarlo exige *force push* |
| C-4 | Hecho (`ffa442f`) | — |
| C-5 | **Ronda, punto 10** | `fileOf` confía en su llamador (`vault/Attachments.kt:75-76`). Dos llamadores no miran el id: `VaultStore.kt:1089` y `:1672`. Es defensa en profundidad y cuesta una línea |
| C-6 | **Riesgo con nombre** (no se adopta) | Ver «C-6» |
| C-7 | **Ronda, punto 16** (solo test) | Con C-3 abierto, lo que protege el *workflow* tiene que quedar fijado por un test |
| D-2 | **Ronda, punto 13** | Comprobado: el efecto solo mira el estado cuando cambia (`ui/screens/VaultPickerScreen.kt:201-203`), y el selector abre `ImportNew` sin mirarlo (`:212-224`) |
| D-3 = RC-5 | **Ronda, punto 12** (texto); Abogado | Mi cierre, con la redacción de RC-5 |
| D-4 | **Ronda, punto 13** | `error` no se borra al cambiar de opción, y el diálogo no tiene estado de «ocupado» (`ui/screens/VaultOrganize.kt:346-347`, `:370-382`) |
| E-2 ≈ RC-2 | **Ronda, punto 4** | Mismo arreglo. El test de E-2 es el segundo rojo |
| E-3 ⊇ RC-6 | **Ronda, punto 14**; `NEEDS_MASTER_NOTICE` al Abogado | El KDoc de `deleteVault` (`AppContainer.kt:703-708`) dice «It is opened with [master]», falso desde CR-7 |
| RC-3, RC-4 | **Ronda, puntos 5 y 15** | Mi cierre |
| AC-E1 | Con la fila 83, al móvil (fila 51) | Es una variante de la fila 83. El proceso suele morir con ese `OutOfMemoryError` |
| AC-E2 | No se pide | Decodifica lo mismo que el desbloqueo al que sustituye (mi cierre) |

### 2. Lo que pedía decidir el informe (§5.6.2)

**E-1 frente a la fila 82: se arregla, y la fila 82 se cierra.**
- Cuando acepté la fila 82 no sabía que el arreglo era de una línea, que no cambia ningún formato y que no toca la regla de que la app acepta sus copias.
- **El arreglo:** `bytesWithoutHistory` (`vault/VaultLimits.kt:98`) deja de contar `passwordChangedAt`, la marca que pone el propio almacén.
- **Por qué es seguro:**
  - la marca está acotada: un `Long` ocupa como mucho 41 B con su clave;
  - sigue contando en los 24 MiB, porque `jsonBytes(data)` mide la bóveda entera;
  - `entryLimitProblem` (`:221-227`) es la única medida del tope de una entrada (guardado, `applyImport` y copias), así que todo lo que hoy se acepta se sigue aceptando: el tope solo se afloja.
- Además, quita una regresión de CR-1 en la franja de 65 503 a 65 536 B («Favorita», «Etiquetar», el editor, los adjuntos, «Reemplazar» y «Borrar todo el historial»). La regla del proyecto es que los detalles menores se arreglan.

**B-4: (c) y (a). (b) se descarta en la 2.0.**
- **Comprobado:**
  - salir de la app siempre bloquea (`MainActivity.kt:120`, `VaultStore.kt:816-826`);
  - el bloqueo sube `lockEpoch`, y eso acaba la oferta (`tools/TwoStepCopy.kt:79-87`, `AppContainer.kt:932-935`);
  - en pantalla dividida no hay `onStop`, y `leftForAnotherApp()` (`AppContainer.kt:220-224`) no llama a `markLeft()`, así que `takeReturnOffer` nunca ve `left` (`TwoStepCopy.kt:54-59`).

  Por eso, hoy la oferta no sale nunca.
- **(c):** `markLeft()` en `leftForAnotherApp()`. La función queda viva donde la bóveda sigue abierta (pantalla dividida o ventana flotante):
  - el *snackbar* sale en la mitad de Clavero (`ui/screens/EntryRow.kt:365-379`);
  - el tope `AWAY_BACKSTOP_MS` y cualquier bloqueo la siguen acabando;
  - solo guarda el id de la entrada.
- **(a):** los textos dicen exactamente eso: el README, las notas, la prueba de la fila 12, el KDoc de `TwoStepCopy` y el comentario de `MainActivity.kt:115-116`.
- **(b) se descarta:**
  - dejar la oferta viva tras el bloqueo al salir no expone nada (quien abre la bóveda ya lo ve todo), pero cambia la regla «un bloqueo acaba la oferta», que fijó la revisión de la tarea 6;
  - pide distinguir el motivo de cada bloqueo en `TwoStepCopy` y en el colector de `AppContainer`;
  - a estas alturas no compensa. Si el dueño la quiere, va a otra versión, con su revisión.

**A-3: opción (a), el test.**
- El diseño promete el test (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:70`, `:234`) y cuesta poco.
- El `if` del marcador de `a3ac942` es código propio (`vault/Backup.kt:37`, `:262-276` de ese commit), así que copiarlo al test es legítimo, y la 1.4.3 publicada no cambia.
- La nota (b) haría verdadero el diseño, pero deja sin fijar que la primera entrada de una copia v4 es `"3\n"`.

**C-6: riesgo con nombre; no se adopta.**
- El dueño dejó `taskAffinity` como única diferencia del manifiesto.
- Además, con `targetSdkVersion` 36, Android ya trae los dos valores que daría la configuración:
  - desde Android 7, las CA del usuario no cuentan;
  - desde Android 9, el texto en claro está negado.
- Solo en Android 8.x se permite el texto en claro, y la app no tiene ninguna URL `http`: la URL es fija y `https`, y no sigue redirecciones (`net/PwnedPasswords.kt:24`, `net/HttpClient.kt:25-26`).
- Sin vector. Si un día se adopta, se repiten en el móvil «¿Filtrada?» y «Comprobar filtraciones».

**El test de C-1: sí, en la Ronda,** con el alcance y las excepciones del punto 15. Los nombres se leen de la skill `advisor` en tiempo de ejecución, así que el test no los escribe.

**C-5 y C-7: sí.** C-5 falla cerrado y cuesta una línea. C-7 son cuatro anclas que hoy están en verde, cada una con su rojo sobre una copia alterada del YAML.

**D-1: 120 s, y un texto que dice la verdad también cuando la carpeta solo es lenta.**
- Solo se corta la espera: la copia sigue en el ámbito de `AutoBackup`.
- Si llega tarde, queda apuntada como las de antes de importar y no se anuncia como la de la subida.
- «Exportar una copia antes» no usa el `lock` de las copias (`ui/screens/VaultUpgrade.kt:104`), así que funciona aunque la carpeta siga sin contestar.
- El texto, en el punto 6.

**La fila 84: tope de número al añadir, y riesgo con nombre para lo que ya existe.**
- Comprobado:
  - `attachmentsFit` solo cuenta bytes (`vault/Attachments.kt:45-46`);
  - `writeBackup` rechaza más de 1 998 adjuntos (`vault/Backup.kt:62`, `:153`);
  - `addAttachment` solo mira los bytes (`VaultStore.kt:1373`).
- El tope de bytes no se alcanza: 50 MiB de adjuntos guardados en crudo, más 33 MiB de JSON, quedan por debajo de 100 MiB (`Backup.kt:60`).
- **Riesgo con nombre nuevo:** una bóveda de la 1.4.3 que ya tenga más de 1 998 adjuntos sigue sin poder copiarse. La vista previa de «Importar de otra app» no modela ese número: lo dice el guardado, con `importTooBigText(…, overFiles)`, que es verdad.
- Pasa desde antes de la 2.0, es rarísimo y el tope nuevo impide llegar ahí.

**A-1 + B-3: el barrido al arrancar sale de los ajustes.**
- Con el orden nuevo (la huella se olvida primero), una muerte del proceso a mitad del borrado deja la bóveda en la lista y sin huella, que es el lado prudente.
- El único huérfano posible es el que dejan los ajustes de una bóveda ya quitada del registro (`forgetOrphanVaults`, `AppContainer.kt:770-782`), así que basta con olvidar también su huella ahí.
- No hace falta recorrer los alias del Keystore: la 2.0 no está publicada, así que no hay restos de versiones anteriores.

## Cambios del informe antes de darlo por aprobado

Los hace el controlador, solo en documentos y en el mismo commit que guarda este Ruling. No piden otra revisión mía: los miro en la puerta del APK.
1. **La cabecera de estado** pasa a «aprobado por el director el 21-11-2026 (`advisor-t12-audit-fixes.md`), con los destinos de §5.6 decididos».
2. **§5.6, columna «Destino propuesto»:** pasa a «Destino» con lo de la tabla de arriba.
   - E-1: Ronda, y la fila 82 se cierra.
   - C-6: Riesgo, con el motivo de `targetSdk` 36.
   - B-4: (c) y (a), con (b) descartada.
   - A-3: (a).
   - Fila 84: Ronda, más el riesgo nuevo.
   - A-2: Ronda y Abogado.
3. **§5.6.2:** cada «lo decide el director» o «propuesta» se sustituye por la decisión y su motivo, en una línea cada uno, citando este archivo.
4. **§6:**
   - la fila 82 sale de la tabla de riesgos («se cierra con E-1 en la ronda»);
   - la línea de C-6 queda en «Otros límites», con el motivo;
   - se añade el riesgo nuevo de la fila 84 (las bóvedas de la 1.4.3 con más de 1 998 adjuntos), con su fila nueva de `pendientes.md`.
5. **§10:**
   - la fila 12 y B-4 dicen que la oferta sale en pantalla dividida y que a pantalla completa no sale, porque salir bloquea;
   - la línea de D-1 añade «el diálogo sale a los 2 minutos con el texto nuevo».
6. **§11:** los textos definitivos de este Ruling (sección «Textos para el Abogado Digital»), en lugar de las propuestas:
   - el de D-1 («no ha terminado en 2 minutos… tarda en contestar»), en lugar de «la carpeta no contesta»;
   - el de la fila 84, el de A-2 y los de B-4.
7. **§3, §7 y §12:**
   - una fila nueva en §3 para este Ruling;
   - la lista de la ronda de §7 y §12, igual al orden del encargo de abajo.

## Encargo (para pegar)

---
**Ronda de arreglos de la auditoría completa de Clavero 2.0**

**Antes de empezar**
- **Lee:**
  - `CLAUDE.md`;
  - `experto-seguridad-cripto`: `SKILL.md`, `references/auditoria.md` y `references/cripto-moderna.md`, por A-1 y el Keystore;
  - `desarrollo-movil-senior`, `seguridad-criptografia-movil` y `auditoria-seguridad-movil`;
  - este Ruling, en `sdd-2.0/advisor-t12-audit-fixes.md`;
  - `docs/auditorias/2026-11-21-auditoria-2.0.md`, §5.6;
  - las cinco `sdd-2.0/final-audit-*.md`;
  - `sdd-2.0/advisor-t12-review-fixes-cierre.md`, por RC-1 a RC-6.
- **Dónde:** `/home/user/Seguridad`, rama `ccr-0aa27f95-u038cd`, desde el commit que guarde este Ruling. Nadie más toca `app/src/main` hasta que acabe la ronda.
- **Gradle:**
  - siempre `export ANDROID_HOME=/home/user/android-sdk; flock /home/user/.gradle-run.lock ./gradlew … --no-daemon --max-workers=1`, de uno en uno;
  - se itera con `-p jvm-check test --tests …`;
  - `ui/`, `android/` y `MainActivity`, en la app con `:app:testDebugUnitTest --tests …`, y después `./gradlew --stop`;
  - nunca las suites enteras: eso es el clon limpio del controlador;
  - `:app:assembleDebug`, una vez, para el rojo y el verde de `check-manifest.sh` (punto 1).
- **Tests:**
  - menos de 10 s cada uno, sin `sleep`, con *latches* y recuentos, con `withTimeout` donde se espere algo, y `Random(20261120L)` donde haga falta azar;
  - `UpgradeCopyFirstTest` conserva su regla de 30 s.
- **TDD:** cada test nuevo, en rojo antes del arreglo, y el informe copia el mensaje del rojo.
  - Un test que fija algo que hoy ya se cumple (C-1, C-7, A-3, la segunda parte de RC-2) enseña su rojo pasándole una entrada alterada: un YAML, un árbol o un marcador cambiados.
- **Commits:**
  - uno por hallazgo (los puntos con dos hallazgos, dos commits);
  - identidad: `git -c user.name=Egt17baa -c user.email=Egt17baa@users.noreply.github.com commit`;
  - asunto: «2.0, arreglos de la auditoría: …»;
  - las dos líneas de atribución de la sesión;
  - ningún nombre de modelo ni dato del dueño. **Sin push.**
  - Cada commit deja en verde sus clases y `redteam.*` en jvm-check.

**Orden**

1. **B-2: `taskAffinity` vacía en `MainActivity`.**
   - `app/src/main/AndroidManifest.xml:21-25`: `android:taskAffinity=""` solo en `.MainActivity`, con un comentario corto (B-2, decisión del dueño del 21-11-2026). Ningún otro cambio del manifiesto.
   - `scripts/check-manifest.sh`:
     - exige ese atributo vacío en `app.seguridad.MainActivity` (en `aapt2 dump xmltree`, `A: android:taskAffinity(0x01010012)=""` dentro de su `E: activity`);
     - si falta, da un error en español;
     - su comentario de cabecera lo dice.
   - El README, en «Entorno de firma», donde compara con la 1.4.3: una línea que nombra la excepción.
   - **Tests:**
     - `PlatformSourcesTest.theLauncherActivityHasAnEmptyTaskAffinity`: `MainActivity` lleva `android:taskAffinity=""`, y ni `<application>` ni ninguna otra actividad llevan `taskAffinity`;
     - el rojo y el verde de `bash scripts/check-manifest.sh` sobre el APK de depuración, antes y después del cambio, con la salida copiada en el informe.

2. **B-1: la navegación no lee el *intent* de arranque.**
   - En `MainActivity.onCreate`, después de `super.onCreate` y antes de `setContent`: `intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, MainActivity::class.java)`.
   - Sin `onNewIntent`.
   - El KDoc de `:26` dice que la biblioteca de navegación lee los *extras* del *intent* de arranque y que por eso se sustituye.
   - **Test:** `PlatformSourcesTest.theNavigationNeverSeesTheLaunchIntent`:
     - la asignación está dentro de `onCreate`, antes de `setContent {`;
     - ningún `override fun onNewIntent` en `MainActivity.kt`;
     - ningún `handleDeepLink(` en `app/src/main/java`.

3. **A-1 + B-3: borrar una bóveda olvida antes su huella, y del todo.**
   - `AppContainer.deleteVault`: `forgetBiometric(id)` va antes que `registry.remove(id)` (orden de CRY-A02), con el mismo `try` de hoy.
   - `BiometricVault.forget(context, vaultId)`, en la fila (`KeyOpsQueue`):
     - lo de `clear`, con `commit()` en lugar de `apply()`;
     - después, `context.deleteSharedPreferences(perVaultName(PREFS, vaultId))`, salvo para la primera bóveda, cuyo archivo guarda también lo del StrongBox del móvil (`phonePrefs`).
   - `SeguridadApp.kt:41` usa `forget`.
   - `forgetOrphanVaults` (`AppContainer.kt:770-782`): olvida la huella de cada id huérfano antes de quitar sus ajustes, con el mismo «lo que se pueda».
   - Los comentarios de `AppContainer.kt:141-143` y `:750` dicen la verdad: la clave envuelve la clave de datos, que también abre las copias hechas antes del borrado.
   - **Tests:**
     - `AppContainerVaultsTest.deletingAVaultForgetsItsFingerprintBeforeItsFolder`: la lambda de la huella comprueba que la carpeta de la bóveda aún existe cuando se la llama;
     - `AppContainerVaultsTest.anOrphanFingerprintIsForgottenAtStart`;
     - `PlatformSourcesTest.deletingAVaultDeletesItsFingerprintPreferences`: `forget` llama a `deleteSharedPreferences` después de `commit()`, y nunca para la primera bóveda.

4. **RC-2 (con E-2): una pasada de copias que superó la espera no copia mientras dura la copia de la subida.**
   - `vault/AutoBackup.kt:833`: `if (store.state.value !is VaultState.Unlocked || store.upgradeCopying.value) { defer(uri); return null }`.
   - **Tests en `UpgradeCopyFirstTest`:**
     - `aPassThatWaitedForTheLockDefersItsCopyUntilTheUpgradeCopy` (el de mi cierre);
     - `aCopyQueuedOnTheLockBeforeAReopenWaitsForTheNewUpgradeCopy` (el de E-2);
     - `theWaitEndsWhenTheUnlockCallerIsCancelled`.

5. **RC-3:** el ancla de fuente de mi cierre:
   - dos `store.upgradeCopying.first { !it }`, cada una justo antes de su `lock.withLock {`;
   - ninguna en `copyBeforeUpgrade`;
   - rojo: quitar `:485`.

6. **D-1: la subida no espera sin límite a una carpeta que no contesta.**
   - `vault/PendingUpgrade.kt:84-88`: `withTimeoutOrNull(UPGRADE_COPY_WAIT_MS) { copyBefore() } ?: CopyResult.Failed(UPGRADE_AUTO_COPY_NOT_DONE)`, dentro del `try` de hoy.
     - `internal const val UPGRADE_COPY_WAIT_MS = 120_000L`.
     - Solo se corta la espera: la copia sigue en el ámbito de `AutoBackup`.
   - En el bloque `synchronized(opening)` se apunta `autoCopyNotDone`, que `VaultStore` expone como `upgradeAutoCopyNotDone`.
   - El diálogo (`ui/screens/VaultUpgrade.kt:124`) enseña `UPGRADE_AUTO_COPY_NOT_DONE` (junto a `:52`) en lugar de `UPGRADE_AUTO_COPY_FAILED` cuando está puesta.
   - **Texto nuevo:** «La copia automática de antes del cambio al formato nuevo no ha terminado en 2 minutos: la carpeta tarda en contestar. Puedes exportar una copia a otro sitio o seguir sin copia.»
   - **Tests:**
     - `UpgradeCopyFirstTest.aFolderThatNeverAnswersStillLeavesTheChoice`, con los pasos de la parte D, `final-audit-d.md`, D-1;
     - `aDueCopyStuckBeforeTheUnlockDoesNotHoldTheUpgrade`:
       - la copia atascada tiene el `lock` desde antes del desbloqueo. Por ejemplo: una apertura cuyo aviso pregunta, «Hacer una copia ahora» atascada en su escritura, un bloqueo y un desbloqueo nuevo;
       - con la bóveda bloqueada, `runDue` no llama a la carpeta (`:540`), así que no vale lanzarla bloqueada;
     - al abrir por fin la puerta, la copia tardía:
       - no se anuncia como la de la subida (`upgradeDone` sigue nulo);
       - queda apuntada en `importCopies`;
       - A6 no la borra;
     - un test de texto en la app: palabra por palabra, `ForbiddenWords`, y «2 minutos» igual a `UPGRADE_COPY_WAIT_MS`.

7. **RC-1:** el de mi cierre.
   - `fitted(limit)` y `previewDataBytes = jsonBytes(data.fitted(VAULT_MAX_DATA_BYTES - PREVIEW_UNSEEN_BYTES)) + PREVIEW_UNSEEN_BYTES`;
   - `importSnapshot` lleva `rest = d.copy(entries = open.trash)`, con los KDoc al día;
   - tests: `thePreviewLetsInWhatTrimmingTheHistoryMakesFit` y `theSnapshotCarriesTheWholeVault`.

8. **E-1: la marca del almacén no cuenta en el tope de una entrada.**
   - `vault/VaultLimits.kt:98`: `bytesWithoutHistory` mide la entrada sin `history` y sin `passwordChangedAt`.
   - El KDoc de `ENTRY_MAX_JSON_BYTES` (`:18-19`): «sin el historial ni la marca de la última contraseña».
   - **Tests** (los de la parte E):
     - `PasswordMarkTest.anEntryOf143JustUnderTheEntryCapStillTakesAnEditThatKeepsItsPassword`;
     - su variante con «Favorita»;
     - `aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted`.
   - La fila 82 pasa a «Cerrado por E-1».

9. **La fila 84: un tope de número al añadir un adjunto.**
   - `vault/Attachments.kt`: `attachmentCountFits(data)` = `data.entries.sumOf { it.attachments.size } + 1 + 2 <= BACKUP_MAX_ENTRIES`, con la papelera.
   - `VaultStore.kt:1373`, antes del tope de bytes: `AttachmentLimit(ATTACHMENTS_COUNT_WOULD_PASS)`.
   - **Texto nuevo:** «Con este adjunto, la bóveda tendría más adjuntos de los que caben en una copia (como mucho 1 998, también los de la papelera). Quita algún adjunto o vacía la papelera para añadir otro.»
   - **Tests:**
     - `AttachmentsTest.theCountOfACopyLimitsTheAttachments`: con 1 997, cabe; con 1 998, no;
     - `VaultStoreTest.anAttachmentPastTheCopysFileCountIsRefused`: el texto, nada guardado y el archivo igual;
     - el texto palabra por palabra y `ForbiddenWords`.

10. **C-5:** `require(isAttachmentId(id))` dentro de `fileOf` (`vault/Attachments.kt:76`).
    - **Test:** `AttachmentsTest.fileOfRefusesAnIdThatIsNotAnAttachmentId`.
    - El informe lista cada llamador (`VaultStore.kt:1089`, `:1467`, `:1672`; `Attachments.kt:112`, `:134`) y dice qué hace con el rechazo: falla cerrado, con su texto fijo, y nunca cierra la app.

11. **B-4: la oferta de «Copiar contraseña» en pantalla dividida, y textos verdaderos.**
    - `AppContainer.leftForAnotherApp()` (`:220-224`), después de su guarda: `twoStepCopy.markLeft()`.
    - El KDoc de `TwoStepCopy` y el comentario de `MainActivity.kt:115-116`, al día.
    - **Textos:**
      - `README.md:24`: «tras «Copiar usuario», si Clavero comparte la pantalla con otra app (pantalla dividida o ventana flotante), ofrece «Copiar contraseña» durante 30 segundos; a pantalla completa, salir de Clavero bloquea la bóveda y la oferta se acaba.»
      - `README.md:407`: «Tras «Copiar usuario», con Clavero en pantalla dividida, tocar la otra app antes de 30 s: Clavero ofrece «Copiar contraseña». A pantalla completa, salir y volver: la bóveda está bloqueada y no se ofrece.»
      - Las notas (`2026-11-15-clavero-2.0-notas.md:28`): «tras «Copiar usuario», en pantalla dividida o en una ventana flotante, ofrece «Copiar contraseña» durante 30 segundos».
    - **Tests:**
      - `AppContainerTest.theOfferIsShownInSplitScreen`: oferta, `leftForAnotherApp()` y `takeReturnOffer()` igual al id; después, un bloqueo `LEFT_APP` la acaba;
      - en `PublishedTextsTest`, las tres frases nuevas, y que no vuelve «al volver a la app ofrece».
    - La nota de corrección del diseño va al repaso de documentos.

12. **Textos verdaderos:**
    - **(a) RC-5:**
      - `FOLDER_DELETE_TO_NONE` (`vault/OrganizeTexts.kt:60`) = `"Sus entradas pasan a «$FOLDER_NONE» y sus subcarpetas, a «$FOLDER_TOP»"`;
      - test `FolderDeletionTextTest.aTopFolderSaysWhereItsSubfoldersGo`: palabra por palabra y `ForbiddenWords`.
    - **(b) A-2:**
      - el «Más información» del coste (`ui/screens/SettingsScreen.kt:668-670`) pasa a una constante `KDF_COST_MORE` en `vault/VaultsTexts.kt`, con el texto de la sección «Textos»;
      - `SettingsScreen` la usa;
      - test `KdfCostHintTest.theCostHintSaysTheVaultIsRaisedNeverLowered` en jvm-check, más un ancla de su uso.

13. **D-2 y D-4: los diálogos de «Bóvedas» y «Borrar carpeta».**
    - **(a) D-2:**
      - `VaultPickerScreen.kt:201`: `LaunchedEffect(active, activeState, dialog)`;
      - `:221`: abrir `ImportNew` solo si `pickerDialogKept(PickerDialogKind.IMPORT_NEW, active.state.value)`; si no, `VAULT_NEW_NEEDS_OPEN` en el *snackbar*;
      - tests: `VaultPickerDialogsTest.aDialogOpenedAfterTheLockClosesToo` y `theImporterAsksOnlyWithTheVaultOpen`.
    - **(b) D-4:**
      - `error = null` al cambiar de opción;
      - `busy` en «Borrar carpeta», como `NameDialog` (`VaultOrganize.kt:259`, `:275-284`);
      - test: `VaultOrganizeSourceTest.deleteFolderClearsTheRefusalAndWaits`.

14. **RC-6 + E-3: los KDoc falsos.**
    - El de `deleteVault` (`AppContainer.kt:703-708`), el de `ContactsFrame` (`ContactsScreen.kt:79-82`) y el de `MasterPrompt` (`:129-135`).
    - `ContactsGate.Master` se queda como defensa.
    - **Test:** `ContactsViewTest.theMasterGateIsNeverReachedWithAnOpenVault`: una v2 real abierta con la maestra nunca da `Master`, ni mientras dura la copia, ni con el aviso, ni después de guardar.

15. **Solo tests:**
    - **(a) RC-4:**
      - el ancla de `storeSaveErrorText(e, fallback = newPasswordFailureMessage(` en `VaultBackup.kt:168`;
      - `checked >= 19` (`ErrorTextsTest.kt:147`).
    - **(b) C-1:** `PublishedFilesNameNoModelTest.noPublishedFileNamesAModel`.
      - **Los nombres:** se leen en tiempo de ejecución de la fila `modelo` de la tabla de `.claude/skills/advisor/SKILL.md`, con los nombres entre comillas invertidas salvo `modelo`. Si falta el archivo, falla y dice por qué.
      - **Qué recorre:**
        - `README.md`, `NOTICE`, `SECURITY.md`, `CLAUDE.md`;
        - `docs/`, `.github/`, `scripts/`, `.maestro/`;
        - `app/src/`, `jvm-check/src/`;
        - los `*.gradle.kts` y `gradle/`.
      - **Qué no recorre:** `app/src/main/assets/`, `.claude/`, `.git/` y ningún `build/`.
      - **Cómo:** palabra entera y sin mayúsculas.
      - **Una sola excepción:** la línea de la expresión de búsqueda de `docs/superpowers/handoff/sdd-2.0/branch-rereview-compilacion.md`, reconocida por su archivo y su contenido, no por el número de línea.
      - **Entradas** de las dos tareas `test`: `docs/` y `.claude/skills/advisor/SKILL.md`.
      - **Rojo:** un árbol de prueba con un nombre dentro.

16. **C-7:** `WorkflowHardeningTest`, con los cuatro casos de la parte C (`final-audit-c.md`, C-7), sobre `.github/workflows/build-apk.yml`:
    - `everyActionIsPinnedToACommit`;
    - `noPullRequestTargetAndReadOnlyByDefault`;
    - `signingSecretsOnlyInTrustedRuns`;
    - `theUploadCertificateIsChecked`.

    El rojo de cada caso, con una copia alterada del YAML.

17. **A-3:** `BackupV3Test.aCopyOfAV4IsRefusedByThe143Reader`.
    - `reader143Marker` copia el `if` del marcador de `a3ac942` (`vault/Backup.kt:262-276` de ese commit, con su `BACKUP_NEWEST = 2` y su texto), con un comentario que cita el commit.
    - La copia de una v4 da el `BACKUP_NEWER` de entonces, y la de `vault-1.4.3.json` da `null`.

18. **Cierre**
    - Se corren, por separado, las clases tocadas, `ErrorTextsTest`, `PlatformSourcesTest`, `PublishedTextsTest` y `redteam.*`, en cada suite.
    - **Informe** `sdd-2.0/audit-fixes-report.md`, con:
      - la tabla del rojo, el verde y cada test;
      - la lista de llamadores de C-5;
      - la salida de `check-manifest.sh`;
      - las secciones «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior», con `archivo:línea`;
      - «Textos para el Abogado Digital (revisión final)».
    - **Inventario:** los textos nuevos y cambiados, en `sdd-2.0/task-10c-inventory.md`.
    - **`pendientes.md`:**
      - una fila por hallazgo de esta ronda, de la 85 en adelante, con su commit y «Hecho»;
      - la 81 (RC-1 a RC-6), la 82 (cerrada por E-1) y la 84, a «Hecho»;
      - una fila nueva para el riesgo con nombre de las bóvedas de la 1.4.3 con más de 1 998 adjuntos («Aceptado»);
      - C-6, C-2 y C-3, como riesgos con nombre.

**Cambios autorizados en tests, y ningún otro**
- RC-1: `VaultStoreImportTest.kt:371` (la igualdad de `VaultSnapshot`) pasa a comparar con los datos del almacén.
- B-2: un test que fije los atributos de `MainActivity` en el manifiesto, solo para añadir `taskAffinity`.
- A-1: los tests de borrado de `AppContainerVaultsTest` que miran la lista de huellas olvidadas, solo el orden o el número de llamadas.
- E-1: un test que afirme `ENTRY_TOO_BIG` justo en el tope contando la marca, solo la cifra, apuntado en el informe.
- B-4, A-2 y RC-5: los tests que fijen las frases antiguas, solo la frase.
- Una firma que cambie por lo autorizado (`fitted(limit)` con su valor por defecto, `forget`), solo esa firma.
- Cualquier otro test en rojo **por comportamiento** en `vault/`, `imports/`, `redteam/`, `settings/` o `AppContainer*` es una parada.

**Se para y consulta si:**
- cambia algún formato, `appJson`, una versión, el AAD o una marca de copia, o falla un test con datos de la 1.x;
- el manifiesto cambia en algo más que `taskAffinity` en `MainActivity`, o `check-manifest.sh` falla en el APK de depuración después del punto 1;
- B-1 cambia el arranque o el estado de la navegación: un test de navegación en rojo;
- A-1: hay que abortar el borrado cuando falla olvidar la huella, o la primera bóveda pierde lo del StrongBox;
- D-1: el tiempo de espera cancela la copia; la copia tardía se anuncia como la de la subida o la borra A6; o `UPGRADE_COPYING` sigue después de los 2 minutos;
- RC-1: `fitted()` sin argumento da otra cosa; o se mueven `RedTeam20ListCostTest` (B por encima de 3,5 MiB, D por encima de 1,30 o un cambio de más de ±0,02), `rt29_` (160) o `FittedHistoryTest`;
- E-1: «Comprobar una copia» o «Importar copia» rechazan una copia hecha por la app;
- la fila 84: una bóveda que ya existe deja de abrirse, o de guardar algo que no sea añadir un adjunto;
- el test de C-1 encuentra un nombre fuera de la excepción. **No se edita ningún documento para hacerlo pasar sin consultar.**

**Nunca** se rompen `SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup` ni lo que escribieron la 1.0 a la 1.4.x. Sin dependencias, permisos, componentes ni hosts nuevos.

---

**Después de la ronda (el controlador):**
1. **Los repasos**, a la vez y en solo lectura, cada uno de su hallazgo:
   - parte A: A-1, A-2 y A-3;
   - parte B: B-1 a B-4;
   - parte C: C-1, C-5 y C-7;
   - parte D: D-1, D-2, D-4 y RC-5;
   - parte E: E-1, E-2 y E-3;
   - el director: RC-1 a RC-6 y el conjunto.
2. **Un clon limpio** de las dos suites, con `check-manifest.sh` sobre su APK de depuración y `aapt2 dump xmltree` frente al de la 1.4.3: solo `versionName` y `taskAffinity`.
3. **Después,** el repaso final de documentos, el Abogado, la fecha de la política y la puerta del APK.

## Riesgos o dudas

- **Sin ejecutar nada:** los destinos salen de leer el código.
  - Que la oferta de B-4 (c) sale en la mitad de Clavero en cuanto pierde el primer plano lo deduzco de `collectAsStateWithLifecycle` con la actividad iniciada (`ui/screens/EntryRow.kt:367`). Lo confirma el móvil.
  - Que (c) no molesta con un selector abierto en pantalla dividida (la oferta saldría al volver de él) es un efecto menor, que acepto.
- **B-2 y B-1:** no se pueden probar aquí y quedan para el móvil (§10).
  - El valor exacto de `aapt2` para un atributo vacío (`=""`) lo confirma el rojo y el verde del script en el punto 1.
- **D-1:** los 120 s pueden cortar la espera de una carpeta en la nube legítima pero lenta. El texto lo dice («tarda en contestar»), y la copia sigue y queda apuntada. Que dé tiempo se comprueba en el móvil.
- **El test de C-1** lee todo `docs/`: cada commit de documentos vuelve a correr la tarea `test`. Es un coste aceptado.
- **La fila 84** no se ha confirmado en el móvil: falta ver si «Añadir adjunto» tiene otro tope en la pantalla.

## Revisión experto-seguridad-cripto

- **Ningún punto toca una primitiva, una longitud, la AAD ni el coste de Argon2id.**
  - E-1 solo cambia una medida del tope (`vault/VaultLimits.kt:98`), en el sentido de aflojarlo, y la bóveda entera se sigue midiendo.
  - RC-1 no cambia lo que se guarda (`MergePlan.kt:429`).
- **Gestión de claves (A-1 y B-3, CWE-459):**
  - la clave de la huella envuelve la clave de datos, que también abre las copias hechas antes del borrado;
  - olvidarla antes de quitar la carpeta y borrar sus preferencias pone la peor muerte del proceso en el lado prudente (`AppContainer.kt:746-751`, `android/BiometricVault.kt:188-195`);
  - la primera bóveda conserva lo del StrongBox (`phonePrefs`, `:117-120`).
- **Plataforma:**
  - **B-2 (CWE-1021):** la pantalla falsa de otra app en Android 8 a 10 se cierra con la decisión del dueño, sin otros cambios del manifiesto;
  - **B-1 (CWE-940):** la navegación deja de leer los *extras* de quien abre la app (`MainActivity.kt:30-34`);
  - **C-6:** sin vector con `targetSdk` 36 (Android ya no confía en las CA del usuario y niega el texto en claro desde Android 9) y sin ninguna URL `http` (`net/PwnedPasswords.kt:24`, `net/HttpClient.kt:25-26`).
- **Rutas (C-5, CWE-22, teórica):** falla cerrado en `fileOf`. Hoy los ids ya salen validados de la bóveda y de las copias.
- **Cadena de firma:**
  - C-3 sigue siendo el riesgo de mayor impacto, aceptado por el dueño;
  - C-7 fija con un test lo que lo reduce (acciones por SHA, solo lectura, secretos en ejecuciones de confianza, huella del certificado, `.github/workflows/build-apk.yml:14-15`, `:33`, `:88-91`);
  - C-1 fija la regla de los nombres de modelos.
- **Veracidad:** A-2, B-4, D-1, RC-5 y los KDoc de E-3 y RC-6 dejan de afirmar lo que el código no hace (regla del proyecto y CWE-451 en su sentido débil).

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:**
  - B-1 va en `onCreate`, antes de `setContent`, sin `onNewIntent`; `singleTop` y `configChanges` no cambian (`app/src/main/AndroidManifest.xml:21-25`);
  - B-4 (c) usa el camino de la pantalla dividida que ya existe (`MainActivity.kt:72-96`, `AppContainer.kt:220-239`);
  - D-2 cierra el diálogo que se abre con la bóveda ya bloqueada, en el mismo paso (`ui/screens/VaultPickerScreen.kt:201`).
- **Concurrencia:**
  - RC-2 aplaza destino por destino, dentro del `lock`, sin cerrojos nuevos (`vault/AutoBackup.kt:833`, `:934-936`);
  - D-1 corta solo el `await` con `withTimeoutOrNull`, que funciona dentro de `NonCancellable` porque crea su propio hijo, mientras la copia sigue en el ámbito de `AutoBackup` (`:749`);
  - `BiometricVault.forget` va en la misma fila (`KeyOpsQueue`) que las demás operaciones de la huella.
- **Interfaz:**
  - D-4 añade «ocupado» y borra el rechazo anterior (`ui/screens/VaultOrganize.kt:346-382`);
  - los textos nuevos pasan por constantes con su test;
  - ningún `rememberSaveable` nuevo con secretos.
- **Compilación:**
  - el test de C-1 declara sus entradas en las dos tareas `test`, como los demás tests que leen documentos (`jvm-check/build.gradle.kts:51-66`, `app/build.gradle.kts:228-246`);
  - C-7 lee un archivo que ya es entrada de las dos.

## Textos para el Abogado Digital

- **Nuevos:**
  - `UPGRADE_AUTO_COPY_NOT_DONE` (D-1): «La copia automática de antes del cambio al formato nuevo no ha terminado en 2 minutos: la carpeta tarda en contestar. Puedes exportar una copia a otro sitio o seguir sin copia.»
  - `ATTACHMENTS_COUNT_WOULD_PASS` (fila 84): «Con este adjunto, la bóveda tendría más adjuntos de los que caben en una copia (como mucho 1 998, también los de la papelera). Quita algún adjunto o vacía la papelera para añadir otro.»
- **Cambiados:**
  - `KDF_COST_MORE` (A-2, sustituye al texto de `SettingsScreen.kt:668-670`): «Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar textos y archivos. Si el archivo de la bóveda tiene un coste menor, sube a este la próxima vez que la abras con la contraseña maestra; nunca se baja. Las copias ya hechas, y los textos y archivos ya cifrados, guardan su coste. Lo cifrado con el coste alto solo se abre en móviles que den a la app al menos 171 MiB de memoria: muchos móviles económicos no llegan, así que úsalo solo si vas a abrir tus copias en móviles como este.» No debe decir nunca que subir el coste protege las copias hechas antes.
  - `FOLDER_DELETE_TO_NONE` (RC-5 y D-3): «Sus entradas pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»».
  - B-4: `README.md:24`, `README.md:407` y las notas (`:28`), con las frases del punto 11. La ficha de Play no puede prometer la oferta a pantalla completa.
  - B-2: la línea del README («Entorno de firma») que nombra `taskAffinity` como única diferencia del manifiesto frente a la 1.4.3.
- **Sin cambio de código, para su revisión:**
  - `NEEDS_MASTER_NOTICE` (`vault/ContactsView.kt:68-69`), que no se enseña en la 2.0 y diría algo falso si se viera. Quitarlo exige código, en su ronda si lo pide;
  - `UPGRADE_COPYING` (`vault/VaultStore.kt:1748-1749`), que con D-1 deja de salir pasados 2 minutos;
  - `ENTRY_TOO_BIG` (`vault/VaultLimits.kt:61`), que con E-1 ya no sale a quien no hizo más grande una entrada;
  - todo lo demás de §11 del informe.

Modelo: el director (esfuerzo extra).
