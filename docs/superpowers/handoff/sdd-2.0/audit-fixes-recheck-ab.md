# Clavero 2.0: repaso de los arreglos de las partes A y B de la auditoría completa (21-11-2026)

**Revisor:** el de las partes A y B (`final-audit-a.md`, `final-audit-b.md`).
**Alcance:** los commits de la ronda que tocan A-1, A-2, A-3, B-1, B-2, B-3 y B-4 (`7505281`, `e4f1018`, `d9e341a`, `18adc17`, `539d2fd`, `0b01405`, `d2838ee`, `89fd69f`), contra el encargo (`advisor-t12-audit-fixes.md`, puntos 1, 2, 3, 11, 12 (b) y 17) y contra la corrección que pedía cada hallazgo. También las desviaciones del §5 de `audit-fixes-report.md` que me tocan.
**Método:** solo lectura del código y de `git show`. No he ejecutado Gradle; según el controlador, el clon limpio de `a94455a` dio 2 601 tests en la app y 2 536 en jvm-check, sin fallos; el «fallaría sin el arreglo» de cada test sale de leer el test frente al código de antes del commit. Rutas abreviadas: `m/` es `app/src/main/java/app/seguridad/`; `t/`, `app/src/test/java/app/seguridad/`.

## Resumen

| Hallazgo | Veredicto | Menores |
|---|---|---|
| A-1 (huella antes que la carpeta, y los huérfanos) | Cerrado con menor | m-1, m-2, m-3 |
| A-2 (el texto del coste de Argon2id) | Cerrado | — |
| A-3 (el test contra el lector de la 1.4.3) | Cerrado con menor | m-4 |
| B-1 (la navegación y el *intent* de arranque) | Cerrado | — |
| B-2 (`taskAffinity` vacía) | Cerrado con menor | m-5 |
| B-3 (el archivo de preferencias de la huella) | Cerrado con menor | m-6 |
| B-4 (la oferta de «Copiar contraseña») | Cerrado con menor | m-7 |

Ninguno está abierto. Ningún menor es de seguridad: son comentarios o un texto de documentación que no dicen del todo la verdad, y dos anclas de test que podrían mirar algo más.

## A-1. Borrar una bóveda olvida antes su huella

**Lo pedido:** `forgetBiometric(id)` antes de `registry.remove(id)`, con el mismo `try`; `forgetOrphanVaults` olvida la huella de cada huérfano antes de quitar sus ajustes; los comentarios dicen que la clave envuelve la clave de datos, que abre también las copias de antes.

**Lo hecho:**
- `m/AppContainer.kt:753-760`: el `try { forgetBiometric(id) }` va antes de `registry.remove(id)` (`:760`), dentro del mismo `withContext(NonCancellable)`. Con el orden de CRY-A02, la peor muerte del proceso deja la bóveda en la lista y sin huella: el lado seguro.
- `m/AppContainer.kt:784-792`: `forgetOrphanVaults` olvida la huella de cada id de `gone` antes de `settings.update`, cada uno en su `try`.
- Comentarios verdaderos en `m/AppContainer.kt:141-145` (el parámetro) y `:756-757` (el `catch` de `deleteVault`), y el KDoc de `deleteVault` (`:716-717`).
- `SeguridadApp.kt:41` pasa `BiometricVault.forget` (B-3, abajo).

**Tests:**
- `t/AppContainerVaultsTest.kt`, `deletingAVaultForgetsItsFingerprintBeforeItsFolder`: la lambda anota si la carpeta y la fila del registro existían al llamarla. Antes del arreglo daba `(false, false)`: falla sin él. Comprueba también que se llama una sola vez.
- `anOrphanFingerprintIsForgottenAtStart`: un id con ajustes, sin carpeta ni fila, se olvida; uno con carpeta, no; y la lambda comprueba que los ajustes del id siguen ahí cuando se la llama («antes de quitar sus ajustes»). Antes del arreglo la lista salía vacía: falla sin él.

**Nada nuevo de riesgo:** `forget` va en la fila (`KeyOp.DISABLE`), así que corta una activación en curso, como hacía `disable`. Al arrancar, el barrido de huérfanos (`m/SeguridadApp.kt:81`) podría cortar así una activación de otra bóveda, pero solo corre si hay huérfanos y justo al empezar, antes de que el usuario llegue a Ajustes: lo apunto como nota, no como menor.

**Menores:**
- **m-1** (`m/AppContainer.kt:790`): el comentario del `catch` de `forgetOrphanVaults` dice «a key that opens nothing on the disk». Es la misma frase que A-1 pedía corregir: la clave envuelve la clave de datos, que abre las copias hechas antes del borrado, y esas copias están en el disco (en una carpeta compartida). Propuesta: «Best effort: the settings go below, so the next start does not see it again; the key left wraps this vault's data key, which also opens the copies made before (it needs the fingerprint).»
  - En la misma función, el KDoc (`:777`) termina «Best effort: a failure leaves everything as it was»: ya no es verdad para un fallo de la huella, porque los ajustes se quitan igual. Propuesta: «… a failure to forget a fingerprint key does not stop the rest; a failure to write the settings leaves them as they were.»
- **m-2** (`t/AppContainerVaultsTest.kt`): ningún test fija el «lo que se pueda»: que, si `forgetBiometric` lanza, `deleteVault` sigue y da `DELETED`, y `forgetOrphanVaults` quita igual los ajustes. La prueba de concepto de A-1 lo traía (un primer contenedor cuyo `forgetBiometric` lanza `IllegalStateException`). Propuesta: un test `aFingerprintThatCannotBeForgottenDoesNotStopTheDeletion`, con esa lambda, que afirme `DELETED`, la carpeta borrada y, en un segundo contenedor, que `forgetOrphanVaults` quita los ajustes aunque la lambda lance.
- **m-3** (`docs/superpowers/handoff/sdd-2.0/pendientes.md`, fila 87): cita `d9e341a` y `18adc17`, pero no `539d2fd`, que también es de A-1 y B-3 (`edit(commit = true)`). Propuesta: añadirlo.

**Desviación del §5 (tres commits):** aceptada. `18adc17` es el cambio de firma autorizado («`forget`, solo esa firma»): `VaultRegistryTest.biometricKeysArePerVault` (`t/vault/VaultRegistryTest.kt:185`) pasa de `disable` a `forget` y sigue comprobando lo mismo. `539d2fd` cambia `.edit()…commit()` por la extensión KTX `edit(commit = true) { … }`, que llama a `commit()` igual: mismo efecto, sin los dos avisos de lint. El único coste es que `d9e341a` deja `VaultRegistryTest` en rojo hasta `18adc17`: un `git bisect` por esa franja vería un rojo ajeno. Informativo, no hay que reescribir nada.

**Veredicto: Cerrado con menor.**

## A-2. El coste de Argon2id

**Lo hecho:** `KDF_COST_MORE` (`m/vault/VaultsTexts.kt:135-140`) con el texto propuesto en A-2, más «Las copias ya hechas», y `SettingsScreen` la usa (`m/ui/screens/SettingsScreen.kt:668`). El texto dice lo que hace `m/vault/Vault.kt:601-610`: `weaker` mira la memoria y las pasadas, el nuevo envoltorio toma el máximo eje por eje y nunca baja. Concuerda con `KDF_COST_KEPT` (`m/vault/VaultsTexts.kt:122-125`): las dos dicen ya que el coste sube al abrir con la maestra.

**Test:** `t/vault/KdfCostHintTest.kt`: el texto palabra por palabra, sin «Lo ya cifrado guarda su coste», sin palabras prohibidas, y el ancla de `Hint(KDF_COST_MORE)` en Ajustes sin el texto viejo. Sin el arreglo, la constante no existía; el rojo se vio con la constante vacía. El encargo no pedía el test de comportamiento de la prueba de concepto (subir 1024/1 a 2048/2 y no bajar 2048/2): ya lo fijan los tests de `afterMasterUnlock` (`t/vault/VaultV4Test.kt`, `VaultV3Test.kt`).

**Veredicto: Cerrado.**

## A-3. El test contra el lector de la 1.4.3

**Lo hecho:** `t/vault/BackupV3Test.kt:186-199`, `reader143Marker`, copia el `if` del marcador de `a3ac942` (`Backup.kt:265-273` de ese commit, con `BACKUP_NEWEST = 2` de `:37`). He comparado las dos: misma condición, mismo orden y el mismo `BACKUP_NEWER` de entonces, «La copia es de una versión más nueva de Clavero» (`:59` de `a3ac942`). `aCopyOfAV4IsRefusedByThe143Reader` (`:206`) hace la copia con el código de verdad (`exportBackup` de una v4, `v4Copy`) y comprueba el `BACKUP_NEWER` de la 1.4.3; la copia de `vault-1.4.3.json` da `null`. Fija lo que se cumple, así que su rojo se vio con una entrada alterada (una v4 con la marca 2), como permitía el encargo.

**Menor:**
- **m-4** (`t/vault/BackupV3Test.kt:191`): `backupDamaged143 = BACKUP_DAMAGED` toma el texto de la 2.0 («… de Clavero. Elige otra copia.»), no el de la 1.4.3 («La copia está dañada o no es una copia de Clavero», `a3ac942`, `Backup.kt:58`), aunque el KDoc dice que la función es una copia del lector de entonces. No cambia ningún resultado del test (ese camino no se prueba), pero la copia no es fiel. Propuesta: `val backupDamaged143 = "La copia está dañada o no es una copia de Clavero"`, y una línea en blanco entre el `}` de `:180` y el KDoc de `:181`.

**Veredicto: Cerrado con menor.**

## B-1. La navegación no lee el *intent* de arranque

**Lo hecho:** `m/MainActivity.kt:39`, la asignación pedida, después de `super.onCreate` (`:37`) y antes de `secureWindow()` y `setContent` (`:41-42`); sin `onNewIntent`. El KDoc de la clase (`:27-32`) dice por qué. `grep` en `m/`: nada más lee el *intent* ni llama a `handleDeepLink`. Como la actividad es `singleTop` y no hay `onNewIntent`, un segundo arranque con la actividad viva no cambia `getIntent()`; cada `NavController` nuevo de `key(vault)` ve el *intent* limpio.

**Test:** `t/PlatformSourcesTest.kt`, `theNavigationNeverSeesTheLaunchIntent`: la asignación dentro de `onCreate`, después de `super.onCreate` y antes de `setContent {`; ningún `override fun onNewIntent`; ningún `handleDeepLink(` en `app/src/main/java`. Sin el arreglo, `indexOf` da −1 y falla.

Queda la prueba en el móvil con `adb shell am start … --eia android-support-nav:controller:deepLinkIds …` (§10 de la auditoría, fila 86).

**Veredicto: Cerrado.**

## B-2. `taskAffinity` vacía en `MainActivity`

**Lo hecho:**
- `app/src/main/AndroidManifest.xml:28`: `android:taskAffinity=""` solo en `.MainActivity`, con su comentario (`:20-22`). `SecureCaptureActivity` (`:35-41`) no la lleva y se abre sin `FLAG_ACTIVITY_NEW_TASK`, así que sigue entrando en la tarea de Clavero; los dos `startActivity` de la app son selectores del sistema (`m/ui/screens/Pickers.kt:152`, `m/android/ImageShare.kt:29`).
- `scripts/check-manifest.sh`: la columna `affinity` del `awk` y la comprobación del bucle. Un valor vacío se escribe como `(vacía)`, no como campo vacío, y así el `read` con `IFS=$'\t'` (que junta tabuladores seguidos) no corre las columnas. Falla si `MainActivity` no lleva el atributo vacío y si no aparece exactamente una vez. Rojo y verde copiados en el §3 del informe.
- README: `:117` (lo que exige el script) y `:416`.

**Test:** `PlatformSourcesTest.theLauncherActivityHasAnEmptyTaskAffinity`: `MainActivity` con `taskAffinity=""`, ni `<application>` ni ninguna otra actividad la llevan, y la palabra aparece una sola vez fuera de los comentarios. Sin el arreglo, `listOf("")` frente a una lista vacía: falla.

**Desviación del §5 (README):** aceptada. «Entorno de firma» (`README.md:212`) no compara con la 1.4.3; la prueba «2.0, versión y permisos» (`:416`) sí, y es el sitio natural.

**Menor:**
- **m-5** (`README.md:416` y `scripts/check-manifest.sh:13-14`): «Frente a la 1.4.3, el manifiesto solo cambia en `versionName` y en `android:taskAffinity=""`». La frase del README va sobre el APK firmado, y ahí cambia también `android:versionCode`, que la CI pone con el número de la ejecución (`.github/workflows/build-apk.yml:112`, `:324`). En el clon limpio con dos APK de depuración los dos llevan `versionCode` 1, así que la comprobación del director no lo ve, pero el texto no es exacto. Propuesta para el README: «Frente a la 1.4.3, el manifiesto solo cambia en `versionName`, en el `versionCode` (lo pone la CI) y en `android:taskAffinity=""` de `MainActivity` …»; y en el comentario del script, «besides versionName and versionCode».

**Veredicto: Cerrado con menor.** Queda la prueba en el móvil con la app de afinidad (fila 85).

## B-3. El archivo de preferencias de la huella

**Lo hecho:** `m/android/BiometricVault.kt:193-207`, `forget`: en la fila (`KeyOp.DISABLE`) y `NonCancellable`; quita `KEY_IV`, `KEY_BLOB` y `KEY_IN_STRONGBOX` con `edit(commit = true)`, borra la clave del Keystore (`deleteKey`) y, salvo para `PRIMARY_VAULT_ID`, borra el archivo con `deleteSharedPreferences(perVaultName(PREFS, vaultId))` (`:203`). Las tres claves son las únicas por bóveda (`:70-74`); las del StrongBox del móvil (`:77-78`) viven en `phonePrefs`, que es el archivo de la primera bóveda y no se borra. `commit()` escribe después de cualquier `apply()` anterior del mismo archivo, así que nada lo vuelve a crear después del borrado; y `deleteSharedPreferences` (API 24, `minSdk` 26) lo quita también de la caché del proceso. El caso «a comprobar» de B-3 (una bóveda sin huella) también queda cubierto: si `commit()` crea el archivo vacío, lo borra la línea siguiente.

**Test:** `PlatformSourcesTest.deletingAVaultDeletesItsFingerprintPreferences` (`t/PlatformSourcesTest.kt:67-77`): `edit(commit = true) {` antes de `deleteSharedPreferences(…)`, en la fila y `NonCancellable`, nunca para la primera bóveda, `deleteKey(vaultId)` y `SeguridadApp` pasando `forget`. Sin el arreglo no existía `forget`: falla.

**Menor:**
- **m-6** (`t/PlatformSourcesTest.kt:70`): el ancla mira que hay un `edit(commit = true) {`, pero no qué quita. Para la primera bóveda, cuyo archivo se queda, lo que borra la huella es justo eso. Propuesta: tomar el bloque entre `edit(commit = true) {` y su `}` y comprobar `remove(KEY_IV)`, `remove(KEY_BLOB)` y `remove(KEY_IN_STRONGBOX)`.

**Veredicto: Cerrado con menor.** Queda la prueba en el móvil (`run-as … ls shared_prefs`), con y sin huella.

## B-4. La oferta de «Copiar contraseña»

**Lo hecho:** opciones (c) y (a), como recomendaba B-4.
- (c): `m/AppContainer.kt:225`, `twoStepCopy.markLeft()` después de la guarda de «ya fuera», así que un segundo aviso del sistema no vuelve a marcar. Solo se guarda el id; la contraseña se lee de la bóveda abierta al tocar.
- (a): `README.md:24` y `:407` y las notas (`docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md:28`) con las frases del encargo; KDoc de `TwoStepCopy` (`m/tools/TwoStepCopy.kt:12-19`, `:44-49`) y comentario de `m/MainActivity.kt:123-125`.
- (b), descartada en la 2.0 (fila 92): la regla del ataque («un bloqueo acaba la oferta») sigue intacta.

**Tests:**
- `t/AppContainerTest.kt`, `theOfferIsShownInSplitScreen`: oferta, `leftForAnotherApp()` y `takeReturnOffer() == "e"`; después, un bloqueo `LEFT_APP` la acaba (`takeReturnOffer` y `pending` nulos). Sin el arreglo, `takeReturnOffer` da `null`: falla.
- `t/tools/PublishedTextsTest.kt`, `theTwoStepCopyIsSaidWhereItHappens`: las tres frases nuevas, y que ni el README, ni las notas, ni la ficha de Play dicen «al volver a la app ofrece», «al volver ofrece «Copiar contraseña»» o «ir a otra app y volver antes de 30 s». Sin el arreglo, las frases no estaban: falla.

**Menor:**
- **m-7** (`m/ui/screens/EntryRow.kt:355-366` y `m/AppContainer.kt:220`): en pantalla dividida o en una ventana flotante, Clavero sigue en `STARTED` cuando la otra app toma el foco. `collectAsStateWithLifecycle` sigue recogiendo, `markLeft` sube `changes`, y la oferta sale **en ese momento** en la mitad de Clavero, no «al volver». Funciona bien, y el README (`:407`, «tocar la otra app antes de 30 s: Clavero ofrece») lo dice bien. Pero dos comentarios no lo dicen:
  - el KDoc de `PasswordOfferOnReturn` dice «back in the app from outside it … (MainActivity.onStop marks the leaving)» y «the leaving, marked while the app is away, arrives on the return»;
  - el de `leftForAnotherApp` termina «so the return offers it».

  Propuesta: «In split screen or a floating window (AppContainer.leftForAnotherApp marks the leaving), the screen stays started, so the offer shows in Clavero's part as soon as the other app takes the focus; full screen, leaving locks the vault and ends it (MainActivity.onStop).» Y en `AppContainer.kt:220`: «… the vault stays open and the screen started here, so the offer shows at once.» No hace falta test: es un comentario.

La nota de corrección del diseño (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:126`, «al volver a la app, ofrece «Copiar contraseña»») sigue pendiente, como dice el encargo: va al repaso de documentos.

**Veredicto: Cerrado con menor.** Queda la prueba en pantalla dividida en el móvil (fila 12 del README, `:407`).

## Revisión experto-seguridad-cripto

- **Gestión de claves (A-1, B-3; MASVS-CRYPTO-2, CWE-459):** el orden de CRY-A02 se cumple en los tres caminos que borran: `deleteVault` (`m/AppContainer.kt:753-760`), «He olvidado» y «Crea tu bóveda» (`m/ui/screens/VaultScreen.kt`, sin cambios). `forget` (`m/android/BiometricVault.kt:193-207`) borra la clave del Keystore y el IV y la clave de datos envuelta, en el acto. El único hueco que queda es un fallo de `forgetBiometric` seguido de un `settings.update` que sí funciona: la clave se queda y nadie la vuelve a buscar. Necesita la huella del dueño y ejecutar código como la app (fuera del modelo); es el «lo que se pueda» que pidió el encargo. m-2 pide fijarlo con un test, no cambiarlo.
- **Primitivas y formatos:** ninguno de estos commits toca `m/crypto/`, la escritura de `seguridad-vault` o `seguridad-backup`, el AAD ni las marcas. A-3 solo lee `writeBackup` y `exportBackup` desde un test. `deleteKey` sigue con `runCatching` (`m/android/BiometricVault.kt:129-131`), como antes.
- **Plataforma (B-1, CWE-940; B-2, CWE-1021):** el *intent* de quien abre la app no llega a la navegación (`m/MainActivity.kt:39`), y `MainActivity` no comparte afinidad con nadie (`AndroidManifest.xml:28`), comprobado en el APK por `scripts/check-manifest.sh`. No hay permisos, componentes exportados ni hosts nuevos.
- **Veracidad (A-2, B-4; CWE-451):** `KDF_COST_MORE` y las frases de B-4 dicen lo que hace el código; m-1 y m-7 son los dos comentarios que aún no lo dicen del todo.
- **Secretos:** `TwoStepCopy` sigue guardando solo el id, la hora y la época (`m/tools/TwoStepCopy.kt:40`); marcar la salida en pantalla dividida no guarda nada secreto ni copia nada sin un toque.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** B-1 sustituye el *intent* después de `super.onCreate` y antes de `setContent` (`m/MainActivity.kt:37-42`), también en una actividad recreada; con `singleTop` y sin `onNewIntent`, un nuevo arranque no lo cambia. B-4: en pantalla dividida la actividad queda en `STARTED` (y `RESUMED` desde Android 10), y por eso la oferta sale al momento (m-7). La guarda de `_awayInOtherApp` (`m/AppContainer.kt:223`) evita marcar dos veces.
- **Concurrencia:** `forget` va en la fila de `KeyOpsQueue`, `NonCancellable` (`m/android/BiometricVault.kt:195-196`); `deleteVault` lo llama dentro de su `withContext(NonCancellable)` y de `changingVaults` (`m/AppContainer.kt:742-768`); `forgetOrphanVaults`, dentro de `changingVaults` (`:779`). Un `DISABLE` cancela una activación en curso (`m/tools/KeyOpsQueue.kt:34`); en el barrido del arranque eso no llega a pasar en la práctica (nota de A-1).
- **Almacenamiento:** `edit(commit = true)` antes de `deleteSharedPreferences` (`m/android/BiometricVault.kt:197-203`) evita que una escritura pendiente vuelva a crear el archivo; el archivo de la primera bóveda se queda por `phonePrefs` (`:122`).
- **Tarea y manifiesto:** con `taskAffinity=""`, `SecureCaptureActivity` (`AndroidManifest.xml:35-41`) y los selectores siguen en la tarea de Clavero porque se abren sin `FLAG_ACTIVITY_NEW_TASK`. Las pruebas en el móvil de §10 (el icono, los recientes, los selectores, el escáner, la pantalla dividida y «Al salir») siguen siendo necesarias.
- **Lint:** `539d2fd` deja `lintDebug` en los 21 avisos de antes (según el informe; no lo he ejecutado).

## Textos para el Abogado Digital (revisión final)

- `KDF_COST_MORE` (`m/vault/VaultsTexts.kt:135-140`, A-2): ya en el informe de la ronda. Sin cambios por mi parte.
- Las tres frases de B-4 (`README.md:24`, `:407`; notas `:28`): ya en el informe de la ronda.
- `README.md:416` (B-2): si se aplica m-5, la frase pasa a nombrar también el `versionCode` que pone la CI. Es documentación técnica, no de cara al usuario.
- Los demás menores (m-1 a m-4, m-6 y m-7) son comentarios, tests o el registro: no son textos de la interfaz ni publicados.
