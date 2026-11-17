# Re-revisión de la rama 2.0, parte B, ronda 2: copias y ajustes

- **Alcance:** `e20b3c2..4666fb2`. Miré m-1 (`4cace26`), m-2 (`4666fb2`) y que `hadFolders` (`15f19b1`) sea la misma condición que usa el diálogo de A6. Las líneas citadas son de `4666fb2`.
- **Cómo:** solo leyendo el código, en mi clon (`scratchpad/br-copias`). No ejecuté gradle ni hice commit.

## Veredicto

**Aprobada: 0 Critical, 0 Important, 0 Minor y 2 informativos.**

| Punto | Estado | Evidencia |
|---|---|---|
| **m-1**, concordancia en plural | Hecho | `AutoBackup.kt:190` dice ahora «quedan $n copias … (automáticas, de antes de importar o de antes de la 2.0)». El anexo (`2026-10-27-seguridad-1.3.0-textos.md:129`) dice lo mismo. El test nuevo de `AutoBackupTargetsTest` comprueba que `autoBackupUnlistedText(2)` contiene «(automáticas,», y fallaría con el texto de antes. Los demás textos en plural ya concordaban (`AutoBackup.kt:179`, `A6Text.kt:180`) |
| **m-2**, el comentario | Hecho | `AutoBackup.kt:847-849`: la petición sin objetivos es heredada, así que desde la fila 17 siempre termina con `a6StaleText`. Las copias de antes de importar y la de antes de la 2.0 son posteriores a ese cambio, así que dejarlas fuera no deja nada con la contraseña antigua. Es verdad: la de antes de la 2.0 se hace en el paso a v4, y `changeMaster` exige que no haya ningún paso pendiente. Además, una petición de antes de la 1.3.0 viene de un cambio hecho en la 1.x |
| **`hadFolders`** | Es la misma condición | `AppContainer.kt` (`lostDialogText`): `settings.vault(vaultId).targets.isNotEmpty()`. El diálogo usa `autoBackupOn = vaultSettings.targets.isNotEmpty()` (`AppNavHost.kt:315`), con `vaultSettings = settings.vault(vault.id)` (`:210`). El diálogo solo se abre si `vaultId == activeId` (`onMasterChanged`, bajo `masterChangedLock`), así que es la misma bóveda. Las dos leen los ajustes de ese momento. Al cambiar de bóveda, el id es el de `masterChangedSlot` (o el de la bóveda que se deja, `AppContainer.kt:568`), y una bóveda que ya no está en el registro no dice nada (`aVaultThatIsGoneSaysNothing`). Tests: `theLostDialogSaysWhichVaultAndWhatIsLeft` (los dos textos y la línea de la huella) y los dos escenarios de `AppContainerVaultsTest` (A con carpetas y B sin ellas) |

## Issues

### Critical, Important y Minor

Ninguno.

### Info

- **i-1. El color no siempre distingue una bóveda.** `masterChangedInOtherVaultText` nombra la bóveda por su color (`VaultsTexts.kt:81-90`). Hay 8 colores y hasta 10 bóvedas: `nextColor` repite colores a partir de la novena (`AppContainer.kt`, `nextColor`) y `setColor` deja elegir uno repetido. Con dos bóvedas del mismo color, el aviso no dice cuál es. Lo dicho sigue siendo verdad, solo es ambiguo. Lo acepto: el nombre va cifrado y no se puede usar (opción A del dueño). Si molesta, se puede añadir «Bóveda N» (`displayName` sin datos, que usa el orden).
- **i-2. El texto sin carpetas** («Las copias .sgbak que ya tenías de esa bóveda se siguen abriendo con la contraseña anterior.») es verdad también cuando no hay ninguna: dice «que ya tenías», sin decir que existan. Cierra la i-1 de mi re-revisión anterior.

## Revisión experto-seguridad-cripto

- **Veracidad de A6:**
  - el aviso del diálogo perdido solo habla de una oferta de borrar cuando el diálogo la habría hecho (la misma condición, `AppNavHost.kt:315`);
  - sin carpetas, dice lo único que es verdad: las exportadas a mano se siguen abriendo con la contraseña anterior;
  - el texto de las copias sin apuntar concuerda en número (`AutoBackup.kt:190`).
- **Ningún dato cifrado sale:** el aviso usa el color, que ya está en `vaults.json` sin cifrar, y nunca el nombre de la bóveda.
- **Sin cambios en** el borrado, los formatos, la red, los permisos ni los registros.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** `lostDialogText` se llama dentro de `switchTo` (bajo `switching`, con el cerrojo del diálogo ya soltado) y en `onMasterChanged`. Lee `StateFlow`s sin bloquear y publica con `vaultsNotices.post`.
- **Ciclo de vida:** el aviso vive en `AppContainer` (`vaultsNotice`), así que sobrevive a un giro, y sale por `openingItem`.
- **Tests:** son deterministas. El texto completo está fijado palabra por palabra, en los dos casos.

## Textos para el Abogado Digital (revisión final)

- **Con carpetas:** «La contraseña maestra de la bóveda de color {color} se cambió. Al cambiar de bóveda, el aviso que ofrece borrar sus copias anteriores no se llegó a mostrar o se cerró: esas copias siguen abriéndose con la contraseña anterior. Para borrarlas, vuelve a esa bóveda y cambia otra vez la contraseña maestra, o bórralas a mano.»
- **Sin carpetas:** «La contraseña maestra de la bóveda de color {color} se cambió. Las copias .sgbak que ya tenías de esa bóveda se siguen abriendo con la contraseña anterior.»
- **Más la línea de la huella**, si no se pudo desactivar (`MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT`).
- **m-1:** «En la carpeta quedan N copias con nombre de copia de la app (automáticas, de antes de importar o de antes de la 2.0) …»
