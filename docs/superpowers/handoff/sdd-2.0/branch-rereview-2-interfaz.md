# Re-revisión 2 de la parte C (interfaz)

Rango revisado: `e20b3c2..4666fb2`, en un clon propio en `4666fb2`. No he ejecutado gradle ni hecho commits. Las líneas citadas son de `4666fb2`.

## Veredicto

**Aprobado.**
- C n-1 (`e59822d`) y C i-3 (`79da15f`) están arreglados, con sus tests.
- Los textos de esta ronda dicen lo que hace el código.
- Ningún hallazgo Critical, Important ni Minor nuevo; quedan dos Info y dos notas para el Abogado.

## La unión de `runMarked` (`e59822d`): qué ve el usuario y en qué orden

El código está en `AppContainer.kt:806-828`. Cuando una acción de «Bóvedas» termina, su resultado `m` se une al aviso que esté pendiente en `vaultsNotices`:
- **Si hay un aviso pendiente distinto de `m`:** se publica `"$m\n\n$pending"`, es decir, primero el resultado y después el aviso, en un solo diálogo.
- **Si no hay aviso pendiente, o es igual a `m`:** se publica solo `m`.
- **Si la acción no devuelve texto** (crear una bóveda o cambiar de bóveda sin problemas): no se publica nada y el aviso pendiente queda solo.

Casos que el usuario puede encontrarse:
- **«Importar como bóveda nueva» sin memoria para subir el coste.** `KDF_COST_KEPT` se publica dentro de `made`. Como `VAULTS_NOTICE` no depende de que la bóveda esté abierta (`Opening.kt:68`), ese aviso puede salir solo en pantalla mientras la acción termina. Al acabar, el mismo diálogo pasa a decir «Bóveda importada…» seguido del aviso. El test es `importAsNewWithoutMemoryToRaiseTheCostSaysBoth`.
- **Un cambio de bóveda que cierra el diálogo de A6 sin que se haya pedido nada en él.** Sale primero el resultado de la acción (si lo hay) y después el aviso por color. El test es `anActionAndANoticeAreBothSaid`.
- **Borrar la bóveda cuyo diálogo de A6 está abierto.** El diálogo se cierra sin aviso, antes del cambio de bóveda (`AppContainer.kt:715-719`), y solo se dice `VAULT_DELETED`. Es correcto: no queda bóveda a la que volver, y ese texto ya dice que las copias siguen donde estaban. El test es `deletingTheVaultOfTheOpenDialogSaysOnlyThatItIsDeleted`.
- **Un resultado anterior que no se cerró se une al de la acción siguiente.** En la práctica casi no puede pasar:
  - El diálogo de `VAULTS_NOTICE` es modal y sale en cuanto no hay nada antes en `openingItem`.
  - Lo único que va antes (`UPGRADE`, `UPGRADE_DONE`, `MASTER_CHANGED`) también es modal, así que no se puede lanzar otra acción de «Bóvedas» con un resultado pendiente.
  - Solo queda el caso de un resultado publicado con la app en segundo plano, que se enseña al volver, antes de cualquier otra acción.
  - Si llegara a pasar, el orden (lo último arriba) se entiende y nada se pierde.

## Hallazgos

**Info i-1 (carrera pequeña).** En `AppContainer.kt:823-826`, leer `pending` y publicar el texto unido no es un solo paso frente a `vaultsNoticeShown`. Si el usuario cierra el aviso a mitad de una acción justo entre esa lectura y la publicación, lo vuelve a ver dentro del texto unido. El efecto es repetir un aviso, nunca perderlo. Se acepta. Si se quisiera cerrar, bastaría con que `Notice` tuviera un `postJoined(m)` que hiciera la unión bajo su propio cerrojo.

**Info i-2 (veracidad, para el Abogado).**
- El aviso de A6 perdido nombra la bóveda por su color (`VaultsTexts.kt` `masterChangedInOtherVaultText`; `AppContainer.kt` `lostDialogText`).
- Hay 8 colores (`VaultData.kt:94`) y hasta 10 bóvedas. Además, `nextColor` repite colores (`AppContainer.kt:589-592`) y el usuario puede poner el mismo color a dos bóvedas.
- Por eso «la bóveda de color azul» puede no decir cuál es. Lo que el texto afirma es verdad, pero puede quedar ambiguo.
- Opción: añadir el número que enseña «Bóvedas» antes de abrir («Bóveda N»), sabiendo que ese número cambia si se reordenan las bóvedas. La decisión es del Abogado y del dueño.

## Los textos de esta ronda

| Texto | Dónde | Lo que hace el código | Veredicto |
|---|---|---|---|
| `KDF_COST_KEPT` («La bóveda se queda con el coste que ya tenía el archivo o la copia. Se volverá a intentar cada vez que la abras con la contraseña maestra. Si en Ajustes tienes el coste «Alto», puedes elegir «Estándar».») | `VaultsTexts.kt`, final | «Se queda» es verdad también con el paso a v4 aún en memoria y con el disco lleno: en disco solo puede estar ese coste. `afterMasterUnlock` lo vuelve a intentar en cada desbloqueo con la maestra mientras el coste sea menor. El aviso sale una vez por proceso. | Correcto |
| `masterChangedInOtherVaultText`, con carpetas («…Al cambiar de bóveda, el aviso que ofrece borrar sus copias anteriores no se llegó a mostrar o se cerró…») | `VaultsTexts.kt` | `hadFolders` (`targets.isNotEmpty()`) es la misma condición con la que el diálogo ofrece «Borrar…» (`AppNavHost.kt:315`). Volver a cambiar la maestra vuelve a abrir el diálogo. | Correcto (ver i-2) |
| `masterChangedInOtherVaultText`, sin carpetas («Las copias .sgbak que ya tenías de esa bóveda se siguen abriendo con la contraseña anterior.») | `VaultsTexts.kt` | Sin carpetas no había nada que ofrecer. Las copias exportadas a mano se llaman `.sgbak` (`Backup.kt:98`), como dicen otros textos de la app (`SettingsScreen.kt:706`, `VaultScreen.kt:405`). | Correcto |
| Línea de la huella (`MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT`) | `VaultsTexts.kt` | Se añade en las dos variantes cuando `fingerprintFailed`. | Correcto |
| Una bóveda que ya no está | `AppContainer.kt` `lostDialogText` | Si la bóveda ya no está en el registro, no se dice nada (`aVaultThatIsGoneSaysNothing`). | Correcto |
| «automáticas» | `AutoBackup.kt:190` | El plural concuerda con «$n copias». | Correcto |

## C i-3 (`79da15f`)

`AppNavHost.kt:304-305`: el texto del diálogo de `VAULTS_NOTICE` va dentro de `Column(Modifier.verticalScroll(rememberScrollState()))`, como en `MasterChangedDialog`. Así, con la letra al 200 %, un resultado y un aviso juntos no se cortan. Tiene un test de fuente (`PlatformSourcesTest.theVaultsNoticeScrolls`) y no usa `rememberSaveable`. Arreglado.

## Revisión experto-seguridad-cripto

- `AppContainer.kt:560-570` (`switchTo` bajo `masterChangedLock`) y `:715-719` (borrar): el diálogo de A6 nunca se abre ni se queda en otra bóveda. El aviso nombra la bóveda por su color, que está sin cifrar en el registro, nunca por su nombre, que va cifrado (opción A).
- `VaultsTexts.kt` `KDF_COST_KEPT`: no promete un guardado que no se ha hecho. `Vault.kt` `afterMasterUnlock` no baja ningún eje del coste.
- No hay criptografía nueva, ni logs con datos, ni secretos en los avisos.

## Revisión Ingeniero Móvil Senior

- `AppContainer.kt:806-828`: la unión se hace después del `finally` que suelta `vaultsBusy`, así que la pantalla nunca se queda ocupada. Ver i-1.
- `AppNavHost.kt:304-305`: diálogo desplazable. Lo que falta comprobar con TalkBack y la letra al 200 % sigue en las filas del móvil.
- `Opening.kt:68`: `VAULTS_NOTICE` no depende de que la bóveda esté abierta, y por eso un aviso publicado a mitad de una acción puede salir antes que el resultado. El orden final es el descrito arriba.

## Textos para el Abogado Digital (revisión final)

- `KDF_COST_KEPT` (texto completo en la tabla).
- `masterChangedInOtherVaultText`, las dos variantes y la línea de la huella; la ambigüedad del color está en i-2.
- La unión «resultado + línea en blanco + aviso» en un solo diálogo (`AppContainer.kt:822-825`).
- `autoBackupUnlistedText` con «automáticas» (`AutoBackup.kt:190`).
