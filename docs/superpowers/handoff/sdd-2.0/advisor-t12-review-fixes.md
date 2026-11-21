# Director: ronda única de arreglos de las revisiones (CR-1 a CR-7, I-1 a I-3) y encargo de la auditoría completa (20-11-2026)

**Pregunta:** con `code-review` (CR-1 a CR-7), `security-review` (sin vulnerabilidades) y la re-comprobación 3 (sin hallazgos nuevos; I-1 a I-3), ¿qué se arregla, cómo, y cómo sigue la auditoría completa?

Veredicto: los siete CR son reales y sus severidades valen (CR-1 y CR-2 importantes, CR-3 a CR-7 menores). Tres arreglos propuestos no valen tal cual: CR-1 crearía versiones falsas y deja fuera los adjuntos, CR-2 deja falso el texto `UPGRADE_PENDING` y CR-7 se queda corto, porque también afecta a las bóvedas v4. Apruebo I-1, I-2 e I-3 con cambios y las tres decisiones del controlador. Después de la ronda no basta con el clon limpio y mi re-revisión: hace falta además `security-review` y `code-review` del diff de la ronda, en paralelo con la auditoría.

## Motivos (verificado en el código, HEAD `32af38f`)

**CR-1, real, importante.**
- En una entrada sin cambio de contraseña, `withVersion` copia una marca nula (`vault/EntryHistory.kt:36`). Entonces `lastPasswordChange` cae en `updatedAt` (`:52`), que `changing` pone a `now` (`vault/Selection.kt:102`).
- **El arreglo propuesto, solo, crea versiones sin cambios.** `comparable()` conserva `passwordChangedAt` (`EntryHistory.kt:21-22`). Al pasar la marca de nula a congelada, `changedFrom` da verdadero (`:37`) en un guardado sin cambios.
- **Le faltan dos sitios.** `addAttachment` y `removeAttachment` suben `updatedAt` sin pasar por `withVersion` (`vault/VaultStore.kt:1333`, `:1346`), y también callan «Antigua».
- **Borrar historial:** `withoutHistory` (`EntryHistory.kt:74-75`) quita la historia y la lista de 1.4.3, que es de donde sale la fecha.
- **Formato:** el campo ya existe en la v4 y `explicitNulls = false` (`core/Json.kt:9`). Una v2 o v3 no guarda nada hasta la subida. No cambia ningún formato y lo nulo se sigue leyendo (LIBRE-1).

**CR-2, real, importante.**
- `opened()` publica `Unlocked` (`VaultStore.kt:527`). El colector de `AutoBackup.kt:454-457` y `afterUnlock` (`VaultStore.kt:582`, `PendingUpgrade.kt:68-71`) compiten por el mismo `lock`.
- **Más de lo que dice la revisión:** `UPGRADE_PENDING` dice «elige en el aviso» (`VaultStore.kt:1700`). Durante toda la copia previa el aviso no está en pantalla (`PendingUpgrade.kt:79-84`), así que el texto es falso en esa ventana. Choca con la regla de veracidad.
- **Bloqueante comprobado: A6 no borra la copia `seguridad-antes-de-2-0-`.**
  - Solo borra los `replaceTargets` congelados al hacer la petición (`AutoBackup.kt:1022-1034`).
  - `withImportCopy` no los amplía (`settings/Settings.kt:345-348`).
  - Una petición antigua sin lista solo toca `copies` (`AutoBackup.kt:848`).
  - Aun así va como test y como condición de parada.
- **Entradas a `lock` con copias automáticas:** `runDue` (que llaman el colector y `launchDue`, desde `AppContainer.kt:524`) y `runChanged`. Las demás (`replaceOldCopies`, `copyBeforeImport`) no pueden correr con la subida pendiente, porque su llamador se rechaza.
- **Aplazar, no saltar:** la subida guarda como «bookkeeping» (`VaultStore.kt:659`) y no sube `revision`, así que nada volvería a lanzar las copias debidas. La espera tiene que ir dentro de `runDue` y `runChanged`, antes de tomar `lock`.

**CR-3, real, menor.**
- En la 2.0, una v2 solo se abre con la maestra (`Vault.kt:680-682`), y eso deja la subida pendiente. Así, `MasterPrompt` solo se ve cuando no puede hacer nada: `confirmMaster` no pasa la bóveda a v3 (`VaultStore.kt:758-765`).
- El KDoc de `ContactsScreen.kt:119-121` está desfasado.

**CR-4, real, menor.**
- `withoutFolder(PARENT)` sube las subcarpetas a `up` sin comprobar el nombre (`vault/Folders.kt:197-201`).
- Las demás operaciones sí lo comprueban (`:136-143`, `:157`, `:166`, `:183`).

**CR-5, real, menor.**
- La vista previa estima solo con las entradas vivas del *snapshot* (`imports/ImportSession.kt:507-509`, `VaultStore.kt:978-981`).
- El guardado mide `data.copy(entries = merged + data.trash)` con todo lo demás de la bóveda (`VaultStore.kt` dentro de `applyImport`).

**CR-6, real, menor.**
- `EntryHistoryScreen.kt:129` enseña `TRASH_SAVE_FAILED` para cualquier error.
- «Renombrar» (`VaultPickerScreen.kt:341-346`) acaba en `VAULT_FAILED` (`AppContainer.kt:835-836`).

**CR-7, real, menor, pero más amplio.**
- `store.unlock(master)` (`AppContainer.kt:728`), en una v2 o v3, espera dentro de `afterUnlock`: escribe la copia `seguridad-antes-de-2-0-` y además guarda la v4 antes de borrarla.
- En una v4 bloqueada, el `Unlocked` arranca `runDue` (`AutoBackup.kt:454-457`), que puede escribir copias automáticas de la bóveda que se va a borrar.
- Además, el desbloqueo purga la papelera, apunta la comprobación de la maestra y puede volver a envolver la clave (`VaultStore.kt:575-577`).
- El arreglo vale para toda bóveda bloqueada, no solo para las v2 y v3.

## I-1, I-2 e I-3

- **I-1: se arregla con dos líneas.** `estimated` pone `estimateNoMemory = false` (`ImportSession.kt:316-321`), y `canConfirm` añade `&& !estimateNoMemory` (`:327-331`). El test informativo pasa a `resists…` y afirma lo seguro.
- **I-2: sí.** `RedTeam20Recheck2Test.kt:251` pasa a `assertEquals(8L * 20, encodings)`. El recuento es exacto: `min(k, 20)` por entrada.
- **I-3: sí, ya.**
  - La aserción de tiempo de `rt28_` pasa a fila informativa (`println`), como se hizo con `rt29_`.
  - El ancla de `VaultList.kt`, la de `listTags` en `compute` y el diferencial de 3 000 casos siguen fijando RT-28.
  - Un test de tiempo en la CI de `main` puede bloquear el PR sin motivo.

## Las tres decisiones del controlador

1. **El ancla de R16 (`PlatformSourcesTest.kt:688-690`): aprobada.**
   - Conserva una sola instantánea, `.revision` una vez y el plan sacado de la instantánea.
   - Que `onPlan` reciba `snap.entries` lo ancla `RedTeam20Recheck3Test` (con `importSnapshot(` una sola vez, `:245`).
2. **`rt29_` contando codificaciones: aprobada.**
   - Es un `AtomicLong` interno (`vault/VaultLimits.kt:121`, `:145`), mejor que el `@Volatile` del Ruling. Cuesta casi nada y no guarda ningún dato.
   - Con I-2 la aserción queda exacta.
3. **La corrutina (`32af38f`): aprobada.**
   - Solo cambia tests, y la causa está en ellos (cancelar sin esperar antes de `resetMain`).
   - Solo `EntryListNoMemoryTest` y `EntryListTagsTest` usan `resetMain` (comprobado con `grep`).
   - **Pero falta su línea «Ruling (controlador…)» en `progress.md`, y `32af38f` no tiene clon limpio** (solo 48 tests sueltos). El controlador añade la línea; el clon de después de la ronda lo cubre.

## Encargo (para pegar)

---
**Ronda única de arreglos de las revisiones de Clavero 2.0: CR-1 a CR-7, I-1, I-2 e I-3**

**Antes de empezar**
- **Lee primero:**
  - `CLAUDE.md`;
  - `experto-seguridad-cripto`: `SKILL.md`, `references/auditoria.md` y `references/cripto-moderna.md` (CR-7 maneja la clave);
  - `desarrollo-movil-senior` y `seguridad-criptografia-movil`;
  - este Ruling, que el controlador guarda en `sdd-2.0/advisor-t12-review-fixes.md`;
  - `sdd-2.0/code-review.md`, `security-review.md` y `redteam-recheck-3.md`.
- **Dónde:** `/home/user/Seguridad`, rama `ccr-0aa27f95-u038cd`, desde el commit que guarde este Ruling. Nadie más toca `app/src/main` hasta que acabe la ronda.
- **Gradle:**
  - siempre `flock /home/user/.gradle-run.lock ./gradlew … --no-daemon --max-workers=1`, de uno en uno;
  - se itera con `-p jvm-check test --tests …`, porque `vault/`, `imports/`, `AppContainer` y `redteam/` corren ahí;
  - `ui/`, solo en la app: `export ANDROID_HOME=/home/user/android-sdk`, `:app:testDebugUnitTest --tests …` y después `./gradlew --stop`;
  - nunca las suites enteras.
- **Tests:** cada uno en menos de 10 s, sin `sleep`, con `Random(20261120L)`, con *latches* y recuentos, y con `withTimeout` donde se espere algo.
- **TDD:** cada test nuevo se ve en rojo antes del arreglo, y el informe copia el mensaje del rojo.

**Commits**
- Identidad: `git -c user.name=Egt17baa -c user.email=Egt17baa@users.noreply.github.com commit`.
- Asunto: «2.0, arreglos de las revisiones: …».
- Las dos líneas de atribución, como en `32af38f`.
- Ningún nombre de modelo ni dato del dueño. **Sin push.**
- Cada punto es un commit y deja en verde sus clases y `redteam.*` en jvm-check.

**Orden**

1. **CR-1: la fecha del último cambio de contraseña.**
   - **Ayudante** en `EntryHistory.kt`: `frozenPasswordMark(prev) = prev.passwordChangedAt ?: (if (prev.passwordForHealth().isNotEmpty()) lastPasswordChange(prev) else null)`.
   - **Dónde se usa:**
     - en `withVersion`, cuando la contraseña no cambia;
     - en `withoutHistory`, antes de vaciar;
     - en `addAttachment` y `removeAttachment`;
     - y en `duplicate`, en lugar de su fórmula propia.
   - `comparable()` pone `passwordChangedAt = null`: la marca nunca crea una versión.
   - **KDoc:** `Vault.kt:115-119`, donde «Borrar historial» ya fija la marca.
   - **Comprobación:** `grep` de `updatedAt =` y de `copy(history` en `vault/` e `imports/`, y en el informe cada sitio con su motivo, se aplique o no.
   - **Tests:**
     - una entrada anterior a la 2.0 con «Favorita», «Etiquetar», un adjunto añadido y otro quitado conserva `lastPasswordChange`;
     - «Salud» sigue diciendo «Antigua»;
     - `Expiry` da la misma fecha;
     - una entrada con `passwordHistory` de la 1.4.3 y una edición posterior de las notas, tras «Borrar historial», conserva la fecha de la lista;
     - el guardado de una entrada sin cambios no añade versión;
     - una entrada sin contraseña queda nula;
     - un cambio de contraseña da `now`;
     - con datos reales de antes (`vault-1.4.3.json`): se abre, se sube y se marca «Favorita» una entrada, y conserva su fecha.

2. **CR-7: borrar una bóveda bloqueada sin abrirla.**
   - **Método nuevo en `VaultStore`, que solo lee:** comprueba la maestra y devuelve el nombre.
     - Corre bajo `mutex` y `NonCancellable`, con `derive {}` (`KdfLock`).
     - Comprueba `active()` antes y después de Argon2id.
     - Decodifica en `compute`; un `OutOfMemoryError` da `CryptoFailure(VAULT_NO_MEMORY)`.
     - Pone la clave a cero en un `finally`.
     - No toca nada más: ni `_state`, ni el reloj, ni la papelera, ni vuelve a envolver la clave, ni hace la subida.
   - `deleteVault` lo usa para toda bóveda bloqueada, sea v2, v3 o v4. Con la bóveda abierta, sigue `confirmMaster`.
   - **Tests en `AppContainerVaultsTest`:**
     - con una v3 bloqueada, una carpeta y A6 pendiente, la carpeta no recibe ningún documento y el almacén nunca llega a `Unlocked`;
     - lo mismo con una v4 bloqueada;
     - `vault.bin` no se reescribe antes de borrarse;
     - con la maestra mal, `CryptoFailure` y no se borra nada;
     - con el nombre mal, `WRONG_NAME`.

3. **CR-2: primero la copia de la subida, y un texto verdadero mientras dura.**
   - **Estado nuevo:** `PendingUpgrade` expone `copying: StateFlow<Boolean>`, que es `file != null && !asking`.
     - Pasa a verdadero en `openedWith(next != null)`.
     - Pasa a falso con `saved()`, `drop()`, `openedWith(null)` y en cuanto `asking` es verdadero.
     - `VaultStore` lo expone.
   - **`runDue` y `runChanged`**, antes de `lock.withLock`, esperan `store.upgradeCopying.first { !it }`. Se aplaza, no se salta. `copyBeforeUpgrade` no espera.
   - **Texto nuevo** `UPGRADE_COPYING`, para Abogado: «Clavero está haciendo la copia automática de antes del cambio al formato nuevo. Espera a que termine e inténtalo otra vez.»
     - Va en todos los sitios que hoy lanzan `UPGRADE_PENDING` (`modifyData`, `changeMaster`, `checkCanReplace` e `importReplace`), con un único ayudante: con `asking`, `UPGRADE_PENDING`; si no, `UPGRADE_COPYING`.
     - Se añade a la lista de `storeSaveErrorText` y a `ErrorTextsTest` y al inventario.
   - **Tests:**
     - con una v3, una carpeta lenta (falsa), A6 pendiente y una copia «con cada cambio» perdida, el primer documento de la carpeta es `seguridad-antes-de-2-0-…`;
     - después de guardar la subida corren la copia debida y A6;
     - la copia de la subida sigue en la carpeta, apuntada en `importCopies`, no borrada y no contada como sin apuntar;
     - `launchDue` durante la copia también espera;
     - la espera siempre termina: un bloqueo durante la copia, una copia que falla, un guardado que falla y un ámbito cancelado al borrar la bóveda, cada uno con `withTimeout`;
     - durante la copia, `upsert` da `UPGRADE_COPYING`; con el aviso en pantalla, `UPGRADE_PENDING`.

4. **CR-3: «Contactos» con la subida pendiente.**
   - Función pura `contactsGate(needsMaster, asking, copying)`, con su test en jvm-check.
   - Con la subida pendiente, la pantalla enseña `UPGRADE_PENDING` o `UPGRADE_COPYING` en lugar de `MasterPrompt`, sin campo de contraseña.
   - `MasterPrompt` solo queda para `needsMaster` sin subida pendiente.
   - **KDoc:** `ContactsScreen.kt:119-121` y `VaultStore.kt:285-291`.

5. **CR-4: borrar una carpeta con una subcarpeta del mismo nombre.**
   - En `withoutFolder(PARENT)`, cada subcarpeta que sube no puede llamarse como otra del destino (`organizeKey`, la misma regla del padre efectivo).
   - Si pasa, `OrganizeRefused(FOLDER_DELETE_NAME_TAKEN)`, un texto nuevo para Abogado: «Una de sus subcarpetas se llama igual que otra carpeta del sitio al que pasaría. Cambia antes uno de los dos nombres.»
   - **Tests:**
     - arriba del todo y dentro de otra carpeta;
     - «trabajo » frente a «Trabajo»;
     - sin choque, borra;
     - la opción TRASH no cambia;
     - un rechazo no cambia nada.

6. **CR-5 e I-1: la vista previa de «Importar de otra app».**
   - **`VaultSnapshot`** lleva también lo que no son entradas vivas: la papelera, las carpetas, las vistas, los colores y el nombre, tomados en la misma sección del `mutex`.
   - **La estimación** mide `data.copy(entries = fusionadas + papelera)` con los mismos dos topes que `applyImport`: el de la copia y `VAULT_MAX_DATA_BYTES`.
   - **I-1:** las dos líneas de arriba.
   - **Tests:**
     - las entradas vivas caben, pero con la papelera o las carpetas no: la vista previa da `overBytes > 0` (hoy da 0);
     - un diferencial de 200 bóvedas aleatorias: si `applyImport` da `TooBig`, la vista previa da más de 0, y si la vista previa da 0, se importa;
     - I-1 en `resists…`.

7. **CR-6: los errores de guardado en pantalla.**
   - `storeSaveErrorText(e)` en `EntryHistoryScreen.kt:129`.
   - `storeSaveErrorText(e, fallback = VAULT_FAILED)` en «Renombrar», con el parámetro nuevo y su valor por defecto.
   - **Comprobación:** `grep` de los `catch` de `ui/` que rodean un guardado del almacén con un texto fijo. Se arreglan los que esconden `UPGRADE_*`, `OrganizeRefused` o `VaultLimitExceeded`, y el informe los lista todos.

8. **I-2 e I-3:**
   - `RedTeam20Recheck2Test.kt:251` pasa a la cuenta exacta;
   - `rt28_` pasa el tiempo a una fila informativa y deja lo demás igual.

9. **Cierre**
   - Se corren por separado las clases tocadas, `ErrorTextsTest`, `PlatformSourcesTest` y `redteam.*`, en cada suite.
   - **Informe** `review-fixes-report.md`, en `.superpowers/sdd/2026-11-12-clavero-2.0/` y en `docs/superpowers/handoff/sdd-2.0/`, con:
     - una tabla con el rojo, el verde y cada test;
     - los dos `grep` (CR-1 y CR-6);
     - las cifras de `RedTeam20ListCostTest` y `rt29_`, antes y después;
     - las secciones «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior», con `archivo:línea`;
     - «Textos para el Abogado Digital (revisión final)»: `UPGRADE_COPYING` y `FOLDER_DELETE_NAME_TAKEN`.
   - **`pendientes.md`:** filas 71 (CR-1) a 77 (CR-7), 78 (I-1) y 79 (I-2 e I-3), todas «Hecho». La 80, las tres zonas de `security-review`, con destino la auditoría completa y estado «Abierto».

**Cambios autorizados en tests, y ningún otro**
- `VaultV4Test.kt:306`: `UPGRADE_PENDING` pasa a `UPGRADE_COPYING`, solo dentro de `beforeUpgrade`. Las líneas `:265` y `VaultV3Test.kt:167-169` no cambian.
- Las llamadas a `previewEstimate` y a `previewEstimateOrNoMemory`, y `VaultSnapshot(…)`: solo el argumento nuevo, con un resto vacío. Afecta a:
  - `ImportStateTest.kt:358-367`, `:711-716`;
  - `RedTeam20Recheck2Test.kt:541`;
  - `RedTeam20Recheck3Test.kt:223`;
  - `VaultStoreImportTest.kt:238`.
- Un test que afirme que una entrada anterior a la 2.0 queda con la marca nula después de un guardado sin cambio de contraseña: pasa a la fecha congelada.
- `informativeAnEstimateAfterNoMemoryForTheSameChoicesTurnsImportOn`, I-2 e I-3, como arriba.
- Si se pone en rojo otro test solo por un texto o una firma de lo autorizado, se ajusta y se apunta en el informe.

**Se para y consulta solo si:**
- cambia algún formato, `appJson`, una versión, el AAD o una marca de copia, o falla un test con datos de la 1.x;
- CR-1 escribe en un archivo v2 o v3, cambia una entrada que nadie tocó o hace fallar un tope de una entrada que ya existía;
- en CR-2, A6 borra la copia `seguridad-antes-de-2-0-`, o la espera no termina en algún caso;
- CR-7 necesita cambiar la estructura de `KdfLock` o del `mutex`, o una maestra equivocada deja algo borrado;
- `RedTeam20ListCostTest` se mueve: B por encima de 3,5 MiB, D por encima de 1,30, o un cambio de más de ±0,02;
- `historyEncodings` deja de ser `min(k, 20)`, o `rt29_` deja de dar 160;
- se pone en rojo por comportamiento un test de `vault/`, `imports/` o `redteam/` que no está en la lista autorizada.

**Nunca** se rompen `SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup` ni lo que escribieron la 1.0 a la 1.4.x (LIBRE-1). Sin dependencias, permisos ni hosts nuevos.

---

## Qué se repasa después de la ronda

Con el clon limpio y mi re-revisión no basta.
- `security-review` corrió antes de la ronda (`5492b76`).
- La ronda toca el orden de las copias y el borrado de una bóveda, que maneja la clave.
- El punto 6 de `CLAUDE.md` pide las dos revisiones sobre la rama.

**Orden:**
1. **Clon limpio en el HEAD final** (controlador):
   - las dos suites, una cada vez, con `flock`;
   - la app con `assembleDebug`, `lintDebug`, `checkLicense`, `checkFragmentVersion` y `checkAssets`;
   - `jvm-check` con el wrapper;
   - ningún `@Ignore` real.
2. **Re-revisión corta del director** del diff `32af38f..HEAD` contra este encargo y su informe: los rojos, que no salte ninguna parada y solo los tests autorizados.
3. **La auditoría** sobre ese HEAD congelado, en paralelo, con la parte E (el diff de la ronda) dentro.

## Encargo de la auditoría completa a fondo (para pegar)

---
**Auditoría completa de Clavero 2.0 sobre el HEAD posterior a la ronda única (tarea 12, paso 4)**

**Común a las cinco partes**
- **Solo lectura:** nadie cambia código ni ejecuta Gradle. Se pueden usar `grep`, `git` y `aapt2` y `apkanalyzer` sobre el APK del clon limpio.
- Cada parte escribe solo su propio `sdd-2.0/final-audit-<a|b|c|d|e>.md`.
- **Lee primero:** `CLAUDE.md`, `experto-seguridad-cripto` (`SKILL.md` y sus tres `references/`), las skills de cada parte, el diseño y el registro de la 2.0, `pendientes.md` y los riesgos con nombre (filas 51, 62, 67 y 69, que no se reabren sin una prueba nueva).
- **Modelo de amenaza:** el de la decisión (f) (cada entrada hostil: copias, `SG1:` y `.sgenc`, QR, *intents*, portapapeles, carpetas compartidas, la copia del sistema, la red y TLS, el bloqueo y la huella, la memoria y la muerte del proceso), más el aislamiento entre bóvedas y la subida desde la 1.4.3.
- **Formato de cada hallazgo:** el del informe de `SKILL.md`, con:
  - severidad, CVSS 3.1, MASVS v2 y CWE;
  - `archivo:línea`;
  - corrección exacta;
  - la prueba de concepto propuesta como test, con nombre y pasos.

  Nada sin evidencia; lo que no se pueda comprobar va como «a comprobar».
- **Cierre de cada parte:** «Lo que está bien», «Revisión experto-seguridad-cripto», «Revisión Ingeniero Móvil Senior» y «Textos para el Abogado Digital».
- **Modelo:** el que eligió el dueño, con esfuerzo extra, en las cinco partes. Ninguna pasa a un modelo más débil.

**Partes, las cinco a la vez**
- **A. `experto-seguridad-cripto` y `seguridad-criptografia-movil`: criptografía y formatos.**
  - `Crypto.kt`, `VaultCrypto` y la v4: el AAD con el formato, la versión, el coste y la sal; que no se pueda bajar de versión; los parámetros de Argon2id; que nunca se baje el coste.
  - `Backup` (marca 3), `BackupCheck`, `KdfLock`, `vaultTag` y `SecureRandom`.
  - Que las claves se pongan a cero, incluido el método nuevo de CR-7.
  - Las comparaciones en tiempo constante.
  - `BiometricVault` y Keystore con un alias por bóveda.
  - Que `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` no cambian frente a `main` (`git diff main -- …`).
- **B. `auditoria-seguridad-movil` (OWASP MASVS v2):**
  - STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, RESILIENCE y PRIVACY;
  - el manifiesto, los componentes exportados, `FileProvider`, las reglas de copia del sistema, `FLAG_SECURE` y el portapapeles (A14);
  - los registros, R8, y `aapt2 dump permissions` y `badging` del APK del clon frente a la 1.4.3.
- **C. `auditoria-seguridad` (método general):**
  - la cadena de suministro: `verification-metadata`, licencias (solo permisivas) y los *workflows* de `.github/` (permisos mínimos, acciones fijadas, entorno `firma`);
  - secretos en el árbol y en el historial;
  - la regla «Privacidad del dueño» en el repositorio;
  - ReDoS sobre lo importado, errores que conceden acceso (*fail-open*), errores tragados y *path traversal*.
- **D. Las tres zonas de `security-review`, línea a línea:**
  - `AutoBackup.kt` entero: el alcance de A6 por bóveda, `copyBeforeUpgrade` y el orden nuevo de CR-2;
  - la migración por bóveda de `SettingsStore.kt`: que no baje el perfil de derivación ni pierda la marca del reloj de A11;
  - `VaultList`, `VaultOrganize`, `VaultPickerScreen` y `EntryFields`.
- **E. El diff de la ronda (`32af38f..HEAD`):** `security-review`, y `code-review` en nivel alto.

**Consolidación**
- El controlador comprueba en el código cada hallazgo, en su `archivo:línea`.
- Un hallazgo Medio o más alto lo vuelve a leer, por su cuenta, el auditor de otra parte.
- Se escribe `docs/auditorias/2026-11-2X-auditoria-2.0.md` con la estructura de `2026-11-08-auditoria-1.4.0.md`:
  - §5, todas las revisiones finales: RT-21 a RT-29, CR-1 a CR-7, `security-review`, la ronda y esta auditoría;
  - §6, los riesgos con nombre;
  - §8, MASVS;
  - §9, la evidencia del clon;
  - §10, lo del móvil;
  - §11, los textos, con la §11.1 vacía para el Abogado.
- El director aprueba.
- Lo que salga va a una ronda con su prueba de concepto en rojo, y después su repaso. Luego, el Abogado final.

---

## Riesgos o dudas (lo que no abrí)
- **CR-1 y el tope de una entrada:** no miré si `entryLimitProblem` cuenta la marca congelada (unos 33 B). Una entrada justo en el tope de 64 KiB podría rechazar «Favorita». Es una parada si falla un test de topes.
- **Sitios no abiertos:**
  - el editor (`VaultEntryScreen`): si llama a `upsert` aunque no haya cambios;
  - el *dispatcher* del ámbito de `AutoBackup`. El arreglo de CR-2 vale con cualquiera de los dos órdenes;
  - el botón «Exportar una copia antes» del diálogo, frente al `lock`;
  - cómo enseña la pantalla de borrar una carpeta un `OrganizeRefused`.
- **`32af38f` no tiene clon limpio completo,** y a `progress.md` le falta la línea de la decisión de la corrutina.
- **Posible falta de veracidad, sin comprobar en la pantalla:** `FOLDER_DELETE_TO_NONE` dice que las subcarpetas «se quedan sin carpeta», pero el código las sube a la raíz. Va al Abogado y a la parte D.

## Revisión experto-seguridad-cripto
- **CR-1 (integridad de una señal de seguridad, CWE-1287):**
  - la marca se congela desde el estado guardado antes del cambio (`EntryHistory.kt:47-53`);
  - `comparable()` la ignora (`:21-22`);
  - no cambia el formato (`core/Json.kt:9`), y un v2 o v3 nunca se escribe.
- **CR-7 (CWE-226, CWE-212):**
  - la comprobación para borrar no publica la clave ni los datos;
  - la clave se pone a cero en un `finally`, va con `KdfLock` y comprueba `active()` antes y después;
  - hoy, el desbloqueo deja copias y una v4 de una bóveda que se borra (`AppContainer.kt:728`, `AutoBackup.kt:454-457`, `PendingUpgrade.kt:68-77`).
- **CR-2:**
  - el orden nuevo no amplía lo que borra A6 (`AutoBackup.kt:1022-1034`, `Settings.kt:345-348`);
  - `UPGRADE_COPYING` deja verdadero lo que dice la pantalla (`VaultStore.kt:1700`).
- **Sin cambios:** ninguna primitiva, longitud, AAD ni coste de Argon2id.

## Revisión Ingeniero Móvil Senior
- **Concurrencia (CR-2):** se espera a un `StateFlow` fuera de `lock`, así que no hay bloqueo mutuo con `copyBeforeUpgrade` (`AutoBackup.kt:744`). La espera siempre se abre: `saved`, `drop` o `asking` (`PendingUpgrade.kt:41-54`, `:79-84`). Se aplaza, no se salta, porque la subida no sube `revision` (`VaultStore.kt:659`).
- **Ciclo de vida:** `launchDue` desde `onStart` (`AppContainer.kt:522-525`) queda cubierto, porque la espera va dentro de `runDue`.
- **Interfaz:** `contactsGate` es una función pura con su test en jvm-check. No añade ningún `rememberSaveable` ni secreto en el estado. Los errores van por `storeSaveErrorText` (`VaultStore.kt:1707-1715`).
- **Tests:** con `cancelAndJoin` antes de `resetMain` (`32af38f`); los nuevos son deterministas, con `withTimeout` y sin `sleep`.

Modelo: el director.