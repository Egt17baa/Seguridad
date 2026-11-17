# Re-revisión de la parte C (interfaz), grupo 9

Rango `a6e66f1..0d6df21` de `ccr-0aa27f95-u038cd`, en mi propio clon, en `0d6df21`. No he ejecutado gradle ni he hecho ningún commit. Las líneas citadas son de `0d6df21`.

## Veredicto

**Aprobado con dos menores.** C I-1, C I-2, C m-3, la fila 26 (C I-3), C m-1 y C m-2 están arreglados y cada uno tiene su test. Cada test falla sin su arreglo: o no compila porque la función nueva no existe, o compara una cadena o un patrón de fuente que antes no estaba. No he encontrado nada roto en los llamantes ni en los tests que fijaban los textos de antes.

En la interfaz, los dos avisos nuevos (A I-1 y fila 45) dejan dos menores: n-1 y n-2. No hay ningún Critical ni ningún Important nuevo.

## Cada arreglo

| Hallazgo | Commit | Estado | Evidencia |
|---|---|---|---|
| C I-1 | `8341f12` | Arreglado | `VaultPickerScreen.kt` `pickerDialogKept`: con la bóveda bloqueada o cargando solo se queda «Borrar» de un hueco vacío; sin bóveda, «Bóveda nueva», «Importar como bóveda nueva» y ese «Borrar». El `LaunchedEffect(active, activeState)` lo aplica desde el estado de la bóveda, que se lee al momento, y no desde `canAdd`. Así se cierra el diálogo «Cambiar nombre» con el nombre a la vista, y también los que tienen una maestra escrita, que era la duda de mi informe. `VaultPickerDialogsTest` prueba los cinco diálogos en los cuatro estados, más una prueba de fuente. Sin el arreglo no compila (`pickerDialogKept` y `PickerDialogKind` no existen). |
| C I-2 | `d15d67a` | Arreglado | `EntryFields.kt` `EntryFieldsCard(… onBig: ((String) -> Unit)? = null)` y `bigActionFor`. El historial ya no pasa `onBig` (`EntryHistoryScreen.kt:162`) y la ficha sí (`VaultEntryScreen.kt:298`). Son los únicos dos llamantes. `EntryFieldsTest.noBigButtonWithoutHandler` falla sin el arreglo. |
| C m-3 | `d15d67a` | Arreglado | `AppNavHost.kt:363` pasa `container.twoStepCopy` al historial. Cada `Field` del historial, su `EntryFieldsCard` y las contraseñas anteriores terminan la oferta con `twoStep::clear`. `EntryFieldsTest.theHistorysCopiesEndTheOffer` cuenta cada `Field(`. `PlatformSourcesTest` se puso al día con las dos líneas que cambian. |
| Fila 26 (C I-3) | `a5d333f` | Arreglado | `Templates.kt` `"WPA" -> "WPA/WPA2"` y `WIFI_WPA3_NOTE` bajo el selector (`EntryFields.kt`, rama de `WIFI_SECURITY_KEY`). Lo que se guarda (`WPA`) no cambia. `TemplatesTest` fija las dos cadenas y su uso. No queda «WPA/WPA2/WPA3» en `app/src`, `.maestro`, `docs/play` ni el README. |
| C m-1 | `4d5917a` | Arreglado | `HealthScreen.kt`: un solo `produceState<HealthView?>(null, data, breached, day, missionsOff)` en `Dispatchers.Default`. Las misiones salen del mismo resultado sin `BREACHED` (se respeta O-6). Hasta tener el primer resultado no se dice «Sin problemas detectados.». `HealthScreenTest` (un solo `vaultHealth(`, dentro de `withContext(Dispatchers.Default)`) y `MissionsTest` (al día) fallan sin el arreglo. |
| C m-2 | `d12f70b` | Arreglado | `Guide.kt:43` y `:63-64` dicen lo que hace `AutoBackup`: con la bóveda abierta, al abrir Clavero o al desbloquearla, y solo si hubo cambios. La página de novedades mide 159 caracteres, dentro del tope de 160 (`GuideTest.kt:82`). Para caber, la primera frase se acortó a «Hasta 3 carpetas, con calendario propio.», que sigue siendo verdad: cada carpeta tiene su calendario. `GuideTest.theRoundsTexts` fija los dos textos; la comprobación «al abrir Clavero» (`:91`) sigue cumpliéndose. |

## Hallazgos nuevos

### Critical / Important

Ninguno.

### Minor

**n-1. El aviso de la fila 45 se pierde en «Importar como bóveda nueva».**
- `core/Notice.kt:20-25`: `vaultsNotices` guarda un solo mensaje, y cada `post` sustituye al anterior.
- En `AppContainer.kt:607-619`, el paso `made` llama a `store.unlock(master)` sobre el archivo recién guardado. Ese archivo conserva el coste de la copia, así que vuelve a quedarse sin memoria. Entonces `tellCostKept` publica `KDF_COST_KEPT` (`VaultStore.kt`, `tellCostKept`).
- Justo después, la acción devuelve `VAULT_IMPORT_NEW_DONE` (`VaultPickerScreen.kt`, `ImportNew`), que `runMarked` publica en la misma cola y sustituye al aviso anterior.
- Resultado: quien importa una copia barata como bóveda nueva en un móvil sin memoria para subir el coste nunca lee `KDF_COST_KEPT`. Solo lo vería en el siguiente proceso, al abrirla con la maestra.
- Lo mismo puede pasar con `MASTER_CHANGED_IN_OTHER_VAULT` si un `switchTo` lo publica dentro de una acción de «Bóvedas» que después devuelve su propio texto, aunque con el diálogo de A6 abierto «Bóvedas» no se alcanza.
- Arreglo: que `runMarked` junte su texto con un aviso que ya estuviera puesto durante la acción (`"$previo\n\n$propio"`), o que `vaultsNotices` sea una cola de mensajes. Test: en `AppContainerVaultsTest`, una «Importar como bóveda nueva» con un `kekDerive` que falla por memoria al subir el coste, comprobando que lo enseñado contiene `KDF_COST_KEPT` y `VAULT_IMPORT_NEW_DONE`.

**n-2. `KDF_COST_KEPT` dice «se ha guardado» cuando a veces no se ha guardado nada.**
- `VaultsTexts.kt`, `KDF_COST_KEPT`: «La bóveda se ha guardado con el coste que ya tenía el archivo o la copia.»
- Con un archivo 2 o 3, `tellCostKept` avisa tras `opened(…, upgraded = checked)` (`VaultStore.kt`), pero entonces todavía no se ha escrito nada: el diálogo del paso a v4 dice que no se guarda ningún cambio hasta que el usuario elija (`UPGRADE_MORE`).
- Si la bóveda se bloquea antes de elegir, el paso se descarta y el aviso, que sale también ante la puerta (`openingItem` no limita `VAULTS_NOTICE` a la bóveda abierta), dice algo falso.
- Lo mismo pasa con una v4 y el disco lleno (`commit(…, mustSave = false)`).
- Propuesta: «La bóveda se queda con el coste que ya tenía el archivo o la copia.»

### Info

- **i-1.** Durante «Comprobar filtraciones», cada contraseña cambia `breached` y relanza el cálculo de `HealthScreen.kt`. El anterior se cancela, pero `vaultHealth` no mira la cancelación, así que cada cálculo termina en `Dispatchers.Default`. Ya no bloquea el hilo principal; el coste total sigue siendo cuadrático y va con la medida de la fila 3 en el móvil.
- **i-2.** Mientras llega el nuevo resultado de «Salud», `produceState` conserva el anterior. Una fila de una entrada que se acaba de borrar puede salir un momento como «(sin título)» (`byId[...]?.title.orEmpty()`). No cuesta nada.
- **i-3.** Los dos avisos nuevos son largos: `MASTER_CHANGED_IN_OTHER_VAULT` tiene unos 330 caracteres, más la línea de la huella. El diálogo de `VAULTS_NOTICE` (`AppNavHost.kt:296-305`) no tiene desplazamiento, así que con la letra al 200 % podría cortarse. Hay que comprobarlo en el móvil; el arreglo es `Column(Modifier.verticalScroll(rememberScrollState()))` dentro de `text`.

## Revisión experto-seguridad-cripto

- `ui/screens/VaultPickerScreen.kt` (`pickerDialogKept` y su `LaunchedEffect`): ni el nombre cifrado ni una maestra escrita quedan a la vista tras un bloqueo. La decisión se toma con el estado síncrono de la bóveda, no con `canAdd`, que se lee fuera del hilo principal.
- `vault/Vault.kt` `afterMasterUnlock` (fila 45, tal como se ve en la interfaz): solo vuelve a envolver con el coste del archivo (`known.kdf.params()`) y nunca baja ningún eje. Cualquier fallo que no sea de memoria se lanza igual que antes.
- `AppContainer.kt`, `masterChangedLock` en `switchTo` y `onMasterChanged`: el diálogo de A6 nunca se abre en otra bóveda. Los textos no llevan datos de la bóveda.
- `ui/screens/EntryHistoryScreen.kt`: «Ver en grande» ya no puede llevar a un secreto que no es el de la versión (`BigPasswordScreen` lee la entrada actual).
- No hay criptografía nueva ni logs con datos en el rango revisado.

## Revisión Ingeniero Móvil Senior

- `ui/screens/HealthScreen.kt` (`produceState` en `Dispatchers.Default`): ya no se calcula nada en la composición. Ver i-1.
- `ui/screens/VaultPickerScreen.kt`: el `LaunchedEffect` tiene como claves `active` y `activeState`, así que un cambio de bóveda también cierra el diálogo.
- `ui/screens/EntryFields.kt` (`bigActionFor`): una función pura que se puede probar en la JVM. El valor por defecto nulo evita el botón muerto en cualquier llamante futuro.
- `ui/AppNavHost.kt:296-305`: ver i-3, que va a la fila del móvil.

## Textos para el Abogado Digital (revisión final)

- `vault/Templates.kt`: «WPA/WPA2» y `WIFI_WPA3_NOTE`, «Si la red es solo WPA3, puede que el QR no sirva para conectarse.»
- `settings/Guide.kt:43`: «Las copias con calendario se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, y solo si hubo cambios.»
- `settings/Guide.kt:63-64`: «Hasta 3 carpetas, con calendario propio. Las copias con calendario se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, y solo si hubo cambios.»
- El README, en la línea 31, dice todavía «Las copias con calendario solo se hacen al abrir Clavero o la bóveda»: se puede dejar, pero conviene igualarla con la guía (parte D).
- `vault/VaultsTexts.kt`, `MASTER_CHANGED_IN_OTHER_VAULT` y `MASTER_CHANGED_IN_OTHER_VAULT_FINGERPRINT`: con hasta 10 bóvedas, «la otra bóveda» no dice cuál. Una opción: «la bóveda que acabas de dejar».
- `vault/VaultsTexts.kt`, `KDF_COST_KEPT`: ver n-2, con la propuesta «La bóveda se queda con el coste que ya tenía el archivo o la copia.»
