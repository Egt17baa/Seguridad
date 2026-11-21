# Clavero 2.0: `code-review` (alto) de la rama (19-11-2026)

- **Objetivo:** la rama `ccr-0aa27f95-u038cd` en `bbcf90d`, frente a `main`.
- **Cuándo:** se hizo a la vez que la re-comprobación 3 del atacante y que `security-review`, por la «Owner decision» del 19-11 de ir más rápido.
- **Resultado:** 7 hallazgos, 2 importantes y 5 menores. Todos se arreglan, porque la regla del proyecto es que los menores no se aplazan. Van en una sola ronda con lo que salga del atacante y de `security-review`, y la aprueba el director.

| # | Severidad | Dónde | Qué pasa | Cómo falla | Arreglo propuesto |
|---|---|---|---|---|---|
| CR-1 | Importante | `vault/EntryHistory.kt:36`, `:52` y `:74`; `Selection.kt` (`changing`) | En una entrada guardada antes de la 2.0, `passwordChangedAt` es nulo. Por eso `lastPasswordChange` usa `updatedAt`, que cambia con cualquier edición: «Favorita», «Etiquetar» o «Borrar historial» (este último borra además la historia y las contraseñas antiguas). | Un acceso de la 1.4.3 con una contraseña de hace 2 años se marca como «Favorita». «Salud» deja de decir «Antigua (más de un año)» y el aviso de cambiarla cada 6 meses se retrasa 6 meses. | Si la contraseña no cambia, `passwordChangedAt = previous.passwordChangedAt ?: lastPasswordChange(previous)`. En `withoutHistory`, fijar la fecha antes de borrar. |
| CR-2 | Importante | `vault/VaultStore.kt:527` y `:582`; `AutoBackup.kt:457` | `opened()` publica `Unlocked` antes de que `afterUnlock` pida `copyBeforeUpgrade`, y `runDue(afterUnlock = true)` coge antes el mismo `lock`. Mientras la subida de la versión 2 o 3 a la 4 está pendiente, la bóveda se ve abierta pero rechaza todos los guardados, y no hay ningún aviso en pantalla para resolverlo. | Un usuario de la 1.4.3 con A6 pendiente y una carpeta en la nube abre la bóveda. La copia tarda decenas de segundos. Mientras tanto, «Guardar», «Favorita» y «Papelera» dan `UPGRADE_PENDING`, pero el aviso de la subida no está en pantalla. | Saltar o aplazar `runDue` mientras hay una subida pendiente, o dar prioridad a `copyBeforeUpgrade`. |
| CR-3 | Menor | `ui/screens/ContactsScreen.kt:141` | Con una bóveda v2 pendiente de subida, `confirmMaster` ya no la pasa a v3, así que `needsMasterForContacts` sigue en verdadero. El KDoc dice lo contrario. | Con la contraseña correcta, el campo se vacía, no sale ningún error y el aviso no se va. | Con `pendingUpgrade`, esconder `MasterPrompt` y apuntar al aviso de la subida. Poner el KDoc al día. |
| CR-4 | Menor | `vault/Folders.kt:202` | `withoutFolder(PARENT)` sube las subcarpetas sin `checkFreeName`, cuando todas las demás operaciones con carpetas lo comprueban. | Quedan dos carpetas «Trabajo» al mismo nivel. | `checkFreeName` contra la carpeta de arriba y `FOLDER_NAME_TAKEN`. |
| CR-5 | Menor | `imports/ImportSession.kt:510` | La vista previa estima solo con las entradas vivas. La copia y el tope cuentan también la papelera, las carpetas, las vistas y los colores. | La vista previa dice que cabe, y el guardado da `ApplyResult.TooBig`. | Estimar con todo el `VaultData`, o al menos con la papelera. |
| CR-6 | Menor | `ui/screens/EntryHistoryScreen.kt:129`; `VaultPickerScreen.kt:344` | «Borrar historial» y «Renombrar» enseñan un error genérico (`TRASH_SAVE_FAILED`, `VAULT_FAILED`). | Con una subida pendiente, la pantalla dice «¿queda espacio?» en lugar de `UPGRADE_PENDING`. | `storeSaveErrorText(e)` en los dos sitios. |
| CR-7 | Menor | `AppContainer.kt:728` | `deleteVault` abre una bóveda v2 o v3 con `store.unlock(master)`, y eso pone en marcha la subida. | Se escribe una copia `seguridad-antes-de-2-0-` de una bóveda que se va a borrar, y esa copia queda fuera de la lista. Sin carpeta, el aviso de la subida aparece un momento. | Para borrar, comprobar la maestra sin la subida: `VaultCrypto.unlock` y una lectura del nombre, y después poner la clave a cero. |

## Revisión experto-seguridad-cripto

Ningún hallazgo de criptografía:
- primitivas estándar: AES-GCM y Argon2id, sin bajar nunca el coste;
- `checkCopyShape` y la profundidad se comprueban antes de decodificar;
- la etiqueta se escribe en hex a mano;
- `"%02x".format` con ar-EG, fa-IR, hi y tailandés sigue dando cifras ASCII.

CR-1 toca la integridad de una señal de seguridad: una edición sin importancia puede callar «Antigua» y el aviso de cambiar la contraseña.

## Revisión Ingeniero Móvil Senior

- **CR-2:** es un problema de orden y de concurrencia entre el flujo de estado del almacén y el que recoge las copias (`VaultStore.kt:527`, `:582`; `AutoBackup.kt:457`).
- **Comprobado y bien:**
  - «Ver en grande» lee el secreto de la bóveda;
  - los ViewModel van por bóveda (`VaultScoped`);
  - los diálogos de la maestra usan `remember`;
  - la comprobación de filtraciones de «Salud» pone `running` a falso en `finally`.
