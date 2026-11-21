# Clavero 2.0, re-revisión de la rama: parte A «formato y bóvedas»

**Rango:** `a6e66f1..0d6df21` (rama `ccr-0aa27f95-u038cd`, HEAD `6268333`), en un clon propio. No ejecuté gradle; las líneas citadas son de `0d6df21`.

**Commits revisados:**
- `353a148` (A I-1)
- `2a03ae6` (fila 38, A I-3)
- `3024ba3` (fila 45, A I-2, opción B)
- `c8a2b82` (A m-2 y m-4)
- `f4323ce` (A m-3)

## Veredicto: necesita arreglos

Necesita un arreglo de texto: `KDF_COST_KEPT` afirma «se ha guardado» en un camino donde no se ha guardado nada (N-1). El código de los cinco hallazgos está bien arreglado. Cada test nuevo falla sin su arreglo, salvo `theListIsSortedAfterEveryPath`, que es de regresión y ya pasaba antes; el de fuente que lo acompaña sí falla.

## Comprobación de cada hallazgo

| Hallazgo | Estado | Evidencia |
|---|---|---|
| **A I-1**: el diálogo de A6 al cambiar de bóveda | **Arreglado** | `AppContainer.kt` `onMasterChanged(vaultId, …)` comprueba `vaultId == activeId` y abre el diálogo dentro de `masterChangedLock`. `switchTo` cambia `activeId` y cierra el diálogo dentro del mismo cerrojo, antes de `awaitIdle`, así que no queda ninguna carrera. `VaultBackup.kt:144` pasa `store.id`. Si el diálogo se pierde, se dice en `vaultsNotice`. Al borrar una bóveda, el aviso de A6 perdido lo sustituye el resultado del borrado (`Notice.post` se queda con el último), así que nunca se dice «vuelve a esa bóveda» de una bóveda borrada. Test `AppContainerVaultsTest.changeOfMasterThenSwitchNeverOpensA6ForTheOtherVault`: con la firma vieja no compila, y con la lógica vieja el diálogo se abre con las copias de B. Es el test n.º 1 que pedí, con el entrelazado detenido en `vaultCompute`. |
| **Fila 38 (A I-3)**: el texto de falta de memoria | **Arreglado** | `Crypto.kt` `kdfOutOfMemoryText(p)`: con más de `DEFAULT_KDF_MEMORY_KIB`, `KDF_OUT_OF_MEMORY` (que ya no dice «Alto»); con 64 MiB o menos, `KDF_NO_MEMORY_NOW`. `cannotFit` siempre está por encima de 64 MiB, así que su texto es verdad. Las dos listas cerradas (`masterUnlockErrorText`, en `Vault.kt:297`, y `newPasswordFailureMessage`) dejan pasar el texto nuevo. `grep` no encuentra ningún otro sitio que compare con `KDF_OUT_OF_MEMORY`: la importación usa `IMPORT_KDF_TOO_BIG`. El test `CryptoTest.outOfMemoryTextFollowsTheCost` fija los dos textos y el límite de 64 MiB y 64 MiB + 1 KiB. |
| **Fila 45 (A I-2, opción B)** | **Arreglado (código)** | `Vault.kt:532-548`: la vuelta atrás solo se aplica con `weaker` y un fallo de memoria. Un v4 se vuelve a sellar con su coste, y un v2 o v3 se envuelve con `known.kdf.params()` exacto, así que no baja nada. Cualquier otro fallo se lanza como antes. `VaultStore` lo dice una vez por proceso y solo con la bóveda abierta (`tellCostKept`), también al importar. Hay tres tests: dos aperturas seguidas con un fallo permanente; el paso a v4 de `vault-1.4.3.json`, que se queda con 1024/1/1, el coste de la muestra; y otros fallos, que siguen fallando. El primero falla sin el arreglo, porque cada `unlock` lanzaba el error. |
| **A m-2**: datos de una copia v4 por encima de 24 MiB | **Arreglado** | `BackupCheck.kt` `checkCopySize`, en «Comprobar una copia» (solo v4) y en `VaultStore.replace` (en `compute`, sobre los datos ya subidos y ajustados). El test `v4CopyOverTheDataLimitIsDamaged` cubre los dos caminos y comprueba que la bóveda de destino queda igual. Falla sin el arreglo. |
| **A m-4**: nombres de carpetas y vistas de una copia | **Arreglado** | `checkCopyStructure` rechaza más de `ORGANIZE_NAME_MAX` puntos de código. El test `longFolderNameIsDamaged` comprueba carpeta y vista, y que 60 se acepta. Falla sin el arreglo. |
| **A m-3**: el orden de la lista en el hilo principal | **Arreglado** | `VaultData.sortedForShow()` calcula cada clave una vez. `opened()` ya no ordena ni descifra: `read` es obligatorio. Todos los caminos lo pasan ordenado desde `compute`: `commit` sin `read` (`VaultStore.kt:488`), el paso a v4 (`:617`), la huella (`:727`), `modifyData` (`:862`) y la importación (`:1523`). Repasé con `grep` cada llamada a `opened(` y a `commit(`. El test de fuente `sortRunsOnCompute` falla sin el arreglo; `theListIsSortedAfterEveryPath` es de regresión. |

## Hallazgos nuevos

### Important

**N-1. `KDF_COST_KEPT` dice «La bóveda se ha guardado…» antes de guardar, y a veces sin llegar a guardar** (veracidad).
- **Dónde:** `vault/VaultStore.kt:622-623`. En el paso a v4 (un v2 o v3), `tellCostKept` corre justo después de `opened(...)`. El v4 con el coste del archivo solo está en memoria, y se guarda después, con la copia o la elección del usuario (`completeUpgrade`). Si se bloquea antes de elegir, no se guarda nada (`PendingUpgrade.drop`) y el texto ya ha salido.
- **Segundo caso:** en el desbloqueo de un v4 (`:609-610`), `commit(mustSave = false)` abre aunque falle la escritura (disco lleno). El informe del implementador ya lo apunta para el Abogado (`branch-fixes-report.md:81`), pero no el caso del paso a v4, que es el normal para quien llega de la 1.4.3 con «Alto» elegido.
- **Arreglo:** el texto lo fija un Ruling palabra por palabra, así que lo decide un Ruling o el Abogado. Propongo cambiar solo la segunda frase: «La bóveda sigue con el coste que ya tenía el archivo o la copia.» Es verdad en los tres caminos (v4, paso a v4 e importar) y no cambia el código. La otra opción es llamar a `tellCostKept` en el paso a v4 solo después de `completeUpgradeLocked`, que pide más código.
- **Test:** en `noMemoryWhenWrappingAgainUpgradesWithTheFileCost`, comprobar que el aviso no dice «se ha guardado», o, con la segunda opción, que `told == 0` antes de `completeUpgrade()` y `1` después.

### Minor

**n-1. El KDoc de un test antiguo describe lo que el código ya no hace.**
- **Dónde:** `vault/UnlockErrorTextTest.kt:82-86` (`noMemoryWhenWrappingAgainLeavesTheFileAsItWas`). Dice que, sin memoria para volver a envolver, «the file is as it was byte for byte, the vault stays locked». Tras la opción B, el código abre la bóveda.
- **Por qué sigue pasando:** el test inyecta el fallo lanzándolo desde `kdf()`, al evaluar el argumento y antes de `afterMasterUnlock`, no al envolver.
- **Arreglo:** cambiar el KDoc a «un fallo al leer el coste de Ajustes (fuera del envoltorio) sigue dejando la bóveda cerrada y el archivo igual», o quitar el test porque lo cubre `noMemoryWhenWrappingAgainOpensWithTheFileCost`.

## Nada nuevo roto

Repasé estos puntos y no encontré nada roto:
- **El cerrojo nuevo de `switchTo`:** solo cubre la asignación y el cierre. No suspende ni toma otro cerrojo, así que no hay interbloqueo con `epochLock` ni con los mutex de las bóvedas.
- **La vuelta atrás de la fila 45:**
  - nunca baja ningún eje;
  - no salta si el fallo no es de memoria;
  - en la importación, la A8 sigue subiendo el coste cuando cabe.
- **`opened()` con `read` obligatorio:**
  - `upgraded` sigue sirviendo para `upgrades.openedWith`;
  - el rechazo por época o por bóveda no activa no cambia.
- **El orden guardado:**
  - `modifyData` guarda en el archivo el `data` sin ordenar, como antes;
  - `saved` es la copia ordenada que se enseña;
  - el orden en disco no importa.

## Revisión experto-seguridad-cripto

- `AppContainer.kt` (`onMasterChanged`, `switchTo` con `masterChangedLock`): el diálogo de A6 ya no puede ir a otra bóveda. El objetivo de seguridad de A6 que se pierde con un cambio de bóveda se dice al usuario.
- `vault/Vault.kt:532-548`: la vuelta atrás conserva CR-2 (nunca se baja ningún eje) y solo actúa con un fallo de memoria. La KEK se sigue derivando con `PasswordUse.EXISTING` (`EXISTING_DERIVE`, `:305`).
- `crypto/Crypto.kt` `kdfOutOfMemoryText`: el texto sigue al coste real. Las listas cerradas siguen cerradas: ningún mensaje de una excepción cualquiera llega a la pantalla.
- `vault/BackupCheck.kt` `checkCopySize` y `checkCopyStructure`: las copias hostiles de la decisión (c), con más de 24 MiB de datos o nombres de más de 60 puntos de código, son `COPY_DAMAGED` antes de cambiar nada.
- `vault/VaultStore.kt:622-623`: N-1 es de veracidad, no de seguridad.

## Revisión Ingeniero Móvil Senior

- `vault/VaultStore.kt:488`, `:617`, `:727`, `:862` y `:1523`: el orden y el descifrado de la lista corren en `compute`. `opened()` (`:503-510`) ya solo compara la época y asigna.
- `AppContainer.kt`: `onCostKept` y el aviso de A6 perdido van por `vaultsNotice`, que la raíz de la navegación enseña (`AppNavHost.kt:296`). Sobreviven al giro y al cambio de bóveda, porque viven en `AppContainer`.
- `vault/VaultStore.kt` `costKeptTold` es `@Volatile`, una vez por almacén, y cada almacén vive lo que el proceso.

## Textos para el Abogado Digital (revisión final)

- **`KDF_COST_KEPT`:** propongo cambiar «La bóveda se ha guardado con el coste que ya tenía el archivo o la copia.» por «La bóveda sigue con el coste que ya tenía el archivo o la copia.» (N-1: en el paso a v4 no se ha guardado todavía, y con el disco lleno tampoco). Lo demás del texto es verdad.
- **`KDF_OUT_OF_MEMORY` y `KDF_NO_MEMORY_NOW`:** los textos nuevos de la fila 38.
- **`MASTER_CHANGED_IN_OTHER_VAULT` y `MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT`:** los textos nuevos de A I-1. Son verdad según el código: la petición de A6 de la bóveda que se dejó no se hizo, y un nuevo cambio de la maestra vuelve a ofrecerla.
