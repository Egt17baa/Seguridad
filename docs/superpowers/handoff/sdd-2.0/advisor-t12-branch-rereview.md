# Ruling del director: re-revisiones de la rama (17-11-2026)

Veredicto: una ronda corta más, con el mismo implementador opus, para arreglar A N-1 (Important, solo cambia el texto), C n-1, B i-1, B i-2, los cinco menores y C i-3. Después vienen tres re-revisiones cortas, cada una de su parte y en paralelo. No se ejecutó nada: todo lo que sigue sale de leer el código en `HEAD 8b8cf76`.

Motivos:
- **A N-1 (y C n-2), confirmado.**
  - En la bóveda v4, `VaultStore.kt:609-610` usa `commit(mustSave = false)`. Si la escritura falla, el error se traga (`:472-485`) y después se avisa.
  - En el paso a v4, `:622-623` avisa con el v4 solo en memoria. Se guarda en `completeUpgradeLocked` (`:648-664`), y si se bloquea antes, `opened()` hace `upgrades.drop()` (`:510-513`).
  - Solo en `replace()` (`:1547`, `:1570`) se avisa después de guardar. «Se ha guardado» es falso en dos de los tres caminos.
- **C n-1, confirmado.** `Notice.post` deja un solo mensaje (`core/Notice.kt:20-25`). En `importAsNewVault` (`AppContainer.kt:607-619`), el paso `before` no avisa, porque la tienda no es la activa y `tellCostKept` pide `Unlocked` (`VaultStore.kt:632-636`). El paso `made` (`store.unlock`) sí avisa. Después, `runMarked` (`AppContainer.kt:797-812`) publica `VAULT_IMPORT_NEW_DONE` (`VaultPickerScreen.kt:392-397`) encima.
- **B i-1, confirmado.** El diálogo de A6 solo ofrece borrar con `targets.isNotEmpty()` (`AppNavHost.kt:311`, `SettingsScreen.kt:515`). `switchTo` (`AppContainer.kt:560-566`) y `onMasterChanged` (`:855-866`) publican el aviso sin mirarlo. Sin carpetas, «Para borrarlas, vuelve a esa bóveda y cambia otra vez la contraseña maestra» es falso.
- **B i-2, confirmado y más amplio de lo que dice el revisor.** No afecta solo a las compilaciones de prueba anteriores a la tarea 8:
  - `appJson` no escribe los valores por defecto (`core/Json.kt:6-10`).
  - Cada desbloqueo con la maestra cambia los datos sin ajustarlos: añade `vaultTag` si falta (`Vault.kt:524`), `lastUnlockAt` y `trashPurgeSkipped` (`Trash.kt:45`), y la huella hace lo mismo (`VaultStore.kt:725`).
  - Una v4 que está a pocos bytes de 24 MiB pasa del tope por esas escrituras de mantenimiento, y `checkCopySize` (`BackupCheck.kt:234-236`) da sus copias por dañadas. Es la misma clase que C-1, aunque mucho menos probable.
- **Los menores, confirmados:**
  - `UnlockErrorTextTest.kt:82-91`: el fallo se inyecta en `kdf()`, antes de envolver.
  - `AutoBackup.kt:190` dice «automática» en plural, y el anexo lo copia en `2026-10-27-seguridad-1.3.0-textos.md:129`.
  - El comentario de `AutoBackup.kt:846-849` cita `AUTO_BACKUP_REPLACED`, cuando una petición sin objetivos es `"1.x"` y sale con `a6StaleText` (`A6Text.kt:58-59`).
  - El diálogo de `VAULTS_NOTICE` no tiene desplazamiento (`AppNavHost.kt:296-305`), y `MasterChangedDialog` sí (`SettingsScreen.kt:510`).

Ruling por punto:

1. **A N-1 / C n-2: se cambia el texto. El aviso no se mueve.** Moverlo detrás de `completeUpgrade` deja falso el caso del disco lleno en la v4, y pide más código. El texto nuevo es verdad en los tres caminos: también tras un bloqueo antes de elegir, porque el disco sigue con el v2 o v3 y su coste. `KDF_COST_KEPT` queda así:
   «No se pudo subir el coste de Argon2id al elegido en Ajustes: ahora no hay memoria suficiente. La bóveda se queda con el coste que ya tenía el archivo o la copia. Se volverá a intentar cada vez que la abras con la contraseña maestra. Si en Ajustes tienes el coste «Alto», puedes elegir «Estándar».»
   - Test: el texto exacto, y que no contiene «se ha guardado».

2. **C n-1: se juntan los textos en `runMarked`. No se pone una cola**: `Notice` la usan también `AutoBackup` y `BiometricVault`.
   - **El cambio:** al terminar la acción, si `message != null` y hay un aviso pendiente en `vaultsNotices` distinto de `message`, se publica «`$message`\n\n`$pendiente`»: primero el resultado de la acción y después el aviso. `runMarked` devuelve lo que publicó.
   - **Guarda nueva en `deleteVault`:** justo antes de `switchTo(...first...)` (`AppContainer.kt:711`), bajo `masterChangedLock`, si el diálogo de A6 está abierto y es de la bóveda que se borra (`masterChangedSlot?.id == id`), se cierra sin aviso. `VAULT_DELETED` ya dice «Las copias ya hechas siguen donde estaban.»
   - **Tests:**
     - `runVaultsAction { c.onMasterChanged(<id no activa>, false, true); VAULT_IMPORT_NEW_DONE }`: `vaultsNotice` lleva los dos textos, en ese orden.
     - Si `AppContainer` permite inyectar `kekDerive`, o se le añade un parámetro interno con el valor por defecto de hoy: «Importar como bóveda nueva» sin memoria al subir el coste enseña `VAULT_IMPORT_NEW_DONE` y `KDF_COST_KEPT`.
     - Borrar la bóveda activa con su diálogo de A6 abierto deja `vaultsNotice == VAULT_DELETED` exacto.

3. **B i-1: se arregla, con dos variantes y la bóveda nombrada por su color.**
   - **El color:** sale del registro (`TagColor.label()`, `VaultsTexts.kt:81-90`). No es secreto: el nombre cifrado nunca se usa. Así se resuelve también lo de «la otra bóveda» con hasta 10 bóvedas.
   - **La función:** `masterChangedInOtherVaultText(color: TagColor, hadFolders: Boolean, fingerprintFailed: Boolean)`.
   - **`hadFolders`:** es `settings.settings.value.vault(id).targets.isNotEmpty()`, el mismo predicado que el diálogo, leído al publicar. En `onMasterChanged`, `id = vaultId`. En `switchTo`, es la bóveda del diálogo cerrado (`masterChangedSlot?.id ?: old.store.id`).
   - Si la bóveda ya no está en el registro, no se publica nada.
   - **Con carpetas:** «La contraseña maestra de la bóveda de color {color} se cambió. Al cambiar de bóveda, el aviso que ofrece borrar sus copias anteriores no se llegó a mostrar o se cerró: esas copias siguen abriéndose con la contraseña anterior. Para borrarlas, vuelve a esa bóveda y cambia otra vez la contraseña maestra, o bórralas a mano.»
   - **Sin carpetas:** «La contraseña maestra de la bóveda de color {color} se cambió. Las copias .sgbak que ya tenías de esa bóveda se siguen abriendo con la contraseña anterior.»
   - **Línea de la huella:** `MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT` no cambia y va detrás con «\n», en las dos variantes.
   - {color} ∈ rojo, naranja, amarillo, verde, turquesa, azul, morado, gris.
   - **Tests:** las dos variantes. En `aSwitchThatClosesTheDialogUnaskedSaysSo`, B no tiene carpetas, así que espera la variante sin carpetas. `changeOfMasterThenSwitch…` espera la de carpetas con el color de A.

   **B i-2: se arregla con un margen en la comprobación.** No se ajusta al desbloquear: eso borraría historial en silencio y pide codificar la bóveda entera en cada apertura.
   - Constante nueva `VaultLimits.COPY_DATA_MARGIN_BYTES = 4 * 1024`, con un KDoc que diga que es lo que pueden añadir las escrituras de mantenimiento (`vaultTag`, `lastUnlockAt`, `trashPurgeSkipped`…) sin un guardado que ajuste.
   - `checkCopySize` rechaza solo `> VAULT_MAX_DATA_BYTES + COPY_DATA_MARGIN_BYTES`. Los guardados siguen estrictos (`VaultLimits.kt:152`, `:173`).
   - El implementador comprueba y apunta que 24 MiB + 4 KiB cifrados siguen cabiendo en `BACKUP_V3_MAX_ENTRY_BYTES` (33 MiB).
   - **Tests:**
     - Una v4 hecha a mano, justo en 24 MiB, sin `vaultTag` ni `lastUnlockAt`: tras `unlock` con la maestra, `checkCopySize` no lanza, y «Comprobar una copia» de su exportación pasa. Debe fallar antes del arreglo.
     - `v4CopyOverTheDataLimitIsDamaged` pasa a medir por encima del tope más el margen.

4. **Menores: se arreglan los tres.**
   - **A n-1:** el KDoc de `UnlockErrorTextTest.kt:82-86` queda así: «Ronda 2 (n-2), second case, as it stands after row 45: the master password is right and the cost of Ajustes cannot be read ([kdf] throws [KDF_OUT_OF_MEMORY], injected; it runs before afterMasterUnlock, so nothing is wrapped). It says so, the file is as it was byte for byte, the vault stays locked, and the next try opens. Running out of memory while wrapping again opens the vault with the file's cost: [noMemoryWhenWrappingAgainOpensWithTheFileCost].»
   - **B m-1:** en `AutoBackup.kt:190`, «(automáticas, de antes de importar o de antes de la 2.0)».
     - En el anexo, línea 129, el mismo cambio. La nota final pasa a «(cambiado en la 2.0, B I-2 y m-1 de la re-revisión)», sin comillas angulares (`ToolTextsTest`).
     - Se ponen al día los tests que fijan ese literal (`AutoBackupTargetsTest`, `AutoBackupF3Test`).
     - Test nuevo: `autoBackupUnlistedText(2)` contiene «(automáticas,».
   - **B m-2:** el comentario de `AutoBackup.kt:846-849` queda así: «A request without targets (saved before 1.3.0): every listed copy but the new one, as before (M4). It is an inherited request ([LEGACY_A6_REQUEST]), so since row 17 it always ends with [a6StaleText], never with AUTO_BACKUP_REPLACED. No import copy (1.4.0) nor the copy before 2.0 is from before that change: both were made after it, so leaving them out leaves nothing under that old password.» El implementador lo contrasta con `A6Text.kt:33-59` antes de confirmarlo.

5. **Info de C:**
   - **i-3: se arregla ahora.** Es una línea, y con el punto 2 los avisos son más largos. En `AppNavHost.kt:296-305`, `text = { FilterObscuredTouches(); Column(Modifier.verticalScroll(rememberScrollState())) { Text(shown) } }`. Lleva un test de fuente. La prueba con la letra al 200 % va a la fila «Móvil» de la memoria.
   - **i-1:** va a la fila 3, junto al recálculo de «Salud» en el móvil, con la nota «cada cambio de `breached` relanza el cálculo, y el anterior termina aunque se cancele».
   - **i-2:** se acepta.
   - **B i-3 e i-4:** se aceptan como los deja el revisor.

6. **Proceso:** una ronda más, con el mismo implementador opus, y re-revisiones cortas en paralelo, cada una en su clon, de solo lectura y sin gradle:
   - **Revisor de A:** N-1, n-1, el margen de B i-2, y el código de B i-1 en `onMasterChanged` y `switchTo`, junto con la guarda de `deleteVault` (concurrencia con `masterChangedLock`, sin interbloqueos).
   - **Revisor de B:** m-1 con el anexo, m-2, y que `hadFolders` sea el mismo predicado que el diálogo.
   - **Revisor de C:** la unión en `runMarked` (qué se ve y en qué orden), i-3, y todos los textos de pantalla de esta ronda.
   - **Controlador:**
     1. Las dos suites en un clon limpio.
     2. `pendientes.md`: fila 3 con C i-1; la fila «Móvil» de memoria con el 200 %; fila nueva para el ataque final con la prueba n.º 7 (abajo).
     3. La segunda pasada de documentos: la revisión del paso 1, con su I-1 y sus 9 menores; los textos que cambian en las dos rondas; README:31.
     4. La parte D.
     5. La revisión final: `code-review`, `security-review`, la auditoría completa, el ataque (fila 24 y prueba n.º 7) y el Abogado final.
     6. El PR del APK, según la decisión del dueño del 16-11.

Encargo de la ronda (implementador opus; mismas reglas que en `advisor-t12-branch-fixes.md`):
- **Qué puede tocar:** `app/src/main` y `app/src/test`, y además **solo la línea 129** del anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`. Si otro test ata un documento, para y avisa.
- **TDD:** cada test se ve primero en rojo y después en verde. Los arreglos de solo comentario no llevan test. Sin criptografía nueva, sin logs y sin cambiar ningún formato. Sin *push*.
- **Gradle:** `ANDROID_HOME=/home/user/android-sdk`. Una suite a la vez, con aviso al controlador.
- **Commits:** firma `Egt17baa <Egt17baa@users.noreply.github.com>`, mensaje «2.0, re-revisión de la rama: <punto>» y la atribución de la sesión.
- **Los commits, en este orden:**
  1. A N-1: `KDF_COST_KEPT` y su test.
  2. A n-1: el KDoc.
  3. C n-1: la unión en `runMarked` y la guarda de `deleteVault`, con sus tests; si hace falta, el parámetro interno `kekDerive` de `AppContainer`.
  4. C i-3: el desplazamiento del aviso, con su test de fuente.
  5. B i-1: `masterChangedInOtherVaultText(color, hadFolders, fingerprintFailed)`, con sus tests.
  6. B i-2: `COPY_DATA_MARGIN_BYTES`, con su test de punta a punta y el test m-2 al día.
  7. B m-1: el texto y el anexo.
  8. B m-2: el comentario.
  9. Al final, las dos suites enteras por separado (`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`; después `./gradlew --stop`; después `./gradlew -p jvm-check test`).
- **Informe:** se añade al final de `docs/superpowers/handoff/sdd-2.0/branch-fixes-report.md`, como «Ronda 2 de la revisión de la rama». Lleva, por punto: el commit, el rojo y el verde. Además: cada texto con su antes y su después, la comprobación de 33 MiB, «Revisión experto-seguridad-cripto», «Revisión Ingeniero Móvil Senior» y «Textos para el Abogado Digital (revisión final)».

Riesgos o dudas:
- **No ejecuté nada.** El crecimiento de B i-2 sale de leer `Json.kt:6-10`, `Trash.kt:45` y `Vault.kt:524`; lo confirma su test.
- **La unión cambia lo que dice cualquier acción de «Bóvedas»** si hay un aviso pendiente. El único caso falso que veo es el aviso de una bóveda que se está borrando, y lo cubre la guarda. En crear e importar, el aviso es de una bóveda que sigue existiendo.
- **Con más de 8 bóvedas los colores se repiten** (`nextColor`): el texto sería ambiguo, pero no falso.
- **«al elegido en Ajustes»** en `KDF_COST_KEPT` simplifica la A8, que sube eje por eje. Se deja para el Abogado.
- **Prueba n.º 7 (fuera de esta ronda):**
  - `checkCopyEntries` (`BackupCheck.kt:196-212`) no limita las etiquetas por entrada; la app usa `MAX_TAGS = 10` (`EntryQuery.kt:12`). Una copia con millones de etiquetas vacías se importa (512 MiB de heap en la JVM).
  - Con el heap de un móvil, cada desbloqueo podría quedarse sin memoria.
  - Va a una fila de `pendientes.md` para el ataque final, conforme a la decisión (a). Cualquier tope tiene que ser mayor o igual que lo que escribió cualquier versión: hay que mirar el historial de `MAX_TAGS` antes.

Revisión experto-seguridad-cripto:
- `vault/Vault.kt:532-548`: el coste no baja en ningún eje. El Ruling solo cambia el texto (`VaultsTexts.kt:114-117`), y así la veracidad queda bien en `VaultStore.kt:609-610`, `:622-623`, `:1547` y `:1570`.
- `BackupCheck.kt:234-236`: el margen de 4 KiB no debilita la decisión (a), porque los guardados siguen en 24 MiB (`VaultLimits.kt:152`, `:173`). Devuelve la garantía de C-1, «una copia propia siempre se lee», ante el mantenimiento de `Vault.kt:524`, `Trash.kt:45` y `VaultStore.kt:725`.
- `AppContainer.kt:560-566` y `:855-866`: el aviso de A6 perdido dice lo que queda bajo la contraseña anterior sin prometer una oferta que no había (`SettingsScreen.kt:515`). El color sale del registro, que no está cifrado; el nombre cifrado no se usa.
- `AppContainer.kt:711`: el aviso de una bóveda borrada nunca manda «vuelve a esa bóveda».
- Ninguna primitiva, permiso, host, registro ni formato cambia (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`).

Revisión Ingeniero Móvil Senior:
- `core/Notice.kt:14-30`: no se toca, porque lo comparten `AutoBackup.kt:377`, `:407` y `BiometricVault.kt:285`. La unión se queda en `AppContainer.kt:797-812`, que es `NonCancellable` y sobrevive al giro porque vive en `AppContainer`.
- `masterChangedLock` (`AppContainer.kt:848`): la guarda nueva solo cierra un estado y no suspende, igual que en `switchTo`. No hay interbloqueo con `switching`, `changingVaults` ni los mutex de las bóvedas.
- `ui/AppNavHost.kt:296-305`: el desplazamiento sigue el patrón de `SettingsScreen.kt:510` y no guarda nada en `rememberSaveable`.
- `HealthScreen.kt` (C i-1): el cálculo ya va en `Dispatchers.Default`; el coste repetido se mide en la fila 3.

Textos para el Abogado Digital (revisión final): `KDF_COST_KEPT` con «se queda con»; las dos variantes de `masterChangedInOtherVaultText` («la bóveda de color {color}») y su línea de la huella; el aviso unido («resultado» + línea en blanco + aviso); «(automáticas, de antes de importar o de antes de la 2.0)» en `AutoBackup.kt:190` y en el anexo. A eso se suma todo lo de la lista de la ronda anterior.

Modelo: el director (opus, esfuerzo extra).
