# CU-A: textos revisados por el Abogado Digital (tareas 1 a 11)

Rama ccr-a1a6eabb-765f9g. Sin push. Líneas de `ToolTexts.kt` = `app/src/main/java/app/seguridad/tools/ToolTexts.kt` tras los cambios.

## Tabla de cambios

| Ítem | const | archivo:línea | Antes → después | Estado |
|---|---|---|---|---|
| C-1 | PASSWORD_CHANGE_RENEW_HINT | ToolTexts.kt:438 | «…una copia, renueva tu identidad» → texto del dictamen («…con las dos puede ver todo lo que guardabas al hacerla: cambia las contraseñas y los códigos 2FA…») | hecho |
| C-2 | RENEW_IDENTITY_WARNING | ToolTexts.kt:451 | añade «Avísales por otro medio: … si llega a ver esos mensajes.» | hecho |
| C-3 | PHOTO_HEIF_NOTICE | ToolTexts.kt:103 | «…perder algo de calidad.» → «…y, si la foto pasa de unos 24 megapíxeles, se reduce a ese tamaño.» | hecho (24 fijo, como el dictamen; `heifToJpeg` usa siempre `MAX_REDACT_PIXELS` con ImageDecoder, sin variante Android 8) |
| C-4a | PHOTO_FILE_NAME_NOTE | ToolTexts.kt:108 | «foto-limpia» → «copia-de-foto» (interpolado con `PHOTO_FILE_BASE`) | hecho |
| C-4b | nombre de archivo | PhotoCleanScreen.kt (2 usos), ShareTemp.kt:13 (`PHOTO_FILE_BASE`) y regex | `foto-limpia.<ext>` → `copia-de-foto.<ext>` | hecho, con desviación D-1 |
| C-5 | KEY_LOCATION_STRONGBOX | ToolTexts.kt:418 | «Chip seguro (StrongBox)» → «Chip de seguridad (StrongBox)» | hecho |
| C-6 | KEY_LOCATION_TEE | ToolTexts.kt:419 | «Zona segura del procesador» → «Zona aislada del procesador (TEE)» | hecho |
| C-7 | STRONGBOX_AVAILABLE_NOTICE | ToolTexts.kt:429 | «…chip seguro.…» → «…chip de seguridad (StrongBox).…» | hecho |
| C-8 | CODES_UNLOCK_TO_SAVE | ToolTexts.kt:383 | «Lo que ves aquí se conserva mientras tanto.» → «Si sales de esta pantalla o giras el móvil, lo que ves aquí se pierde.» | hecho (solo texto; el estado es de CU-B) |
| C-9 | PHOTO_EXPLANATION | ToolTexts.kt:26 | última frase → «…la app no la envía a ningún sitio: solo sale si tú compartes la copia.» | hecho |
| C-10 | PHOTO_TOOL_SUBTITLE | ToolTexts.kt:23 | «…datos ocultos» → «…datos ocultos del archivo» | hecho |
| C-11 | PHOTO_DEVICE | ToolTexts.kt:45 | «Móvil: marca, modelo y número de serie» → «Móvil o cámara: marca y modelo, y a veces número de serie o nombre del propietario» | hecho |
| C-12 | PHOTO_REMOVED_NAMES[DEVICE] | ToolTexts.kt:~80 | «marca, modelo y número de serie del móvil» → «datos del móvil o la cámara» | hecho |
| C-13 | PHOTO_AUTHOR_RIGHTS | ToolTexts.kt:54 | → «…la copia no los lleva. Si la foto no es tuya, no uses la copia para ocultar de quién es» | hecho |
| C-14 | PHOTO_SAVE_FAILED | ToolTexts.kt:118 | «…No se ha guardado nada.» → «Si ha quedado un archivo a medias en la carpeta, bórralo.» | hecho |
| C-15 | QR_EXPLANATION | ToolTexts.kt:214 | «Solo puedes copiarlo.» → «Puedes copiarlo y, si es una tarjeta o un mensaje de Seguridad o un código 2FA, pasarlo a su pantalla.» | hecho |
| C-16 | QR_HIDDEN_ELSEWHERE | ToolTexts.kt:277 | → «…en el enlace, aunque aquí no se vean (por ejemplo, codificados con «%»…» | hecho |
| C-17 | QR_SGK1 | ToolTexts.kt:265 | «con la persona, en persona o por llamada» → «con esa persona, cara a cara o por llamada» | hecho |
| C-18 | QR_TEXT_HIDDEN_PASSWORD | ToolTexts.kt:286 | «Lleva…» → «El texto completo lleva…» | hecho |
| C-19 | QR_TEXT_HIDDEN_OTP | ToolTexts.kt:287 | ídem | hecho |
| C-20 | PASSWORD_CHANGE_OLD_BACKUPS | ToolTexts.kt:436 | «…que hiciste antes…» → «Las copias .sgbak de antes, también las automáticas, se siguen abriendo…» | hecho |
| C-21 | PASSWORD_CHANGE_REPLACE_COPIES | ToolTexts.kt:437 | «Borrar … y hacer una nueva» → «Hacer una copia nueva y borrar las automáticas anteriores» | hecho |
| C-22 | AUTO_BACKUP_REPLACED | AutoBackup.kt:95 | añade «Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.» | hecho |
| C-23 | AUTO_BACKUP_REPLACE_WAITS | AutoBackup.kt:98 | → «…abras la bóveda con la contraseña nueva.» | hecho |
| C-24 | CLOCK_CHANGED_TEXT | VaultStore.kt:34 | «ha cambiado» → «se ha atrasado» | hecho |
| C-25a/b | RESET_WIPES_DAMAGED (única) | ToolTexts.kt:458; VaultScreen.kt (import y `Text(RESET_WIPES_DAMAGED)`) | dos constantes → una; texto con «Lo guardado no se puede descifrar sin la contraseña…» y «Las copias .sgbak no se borran…» | hecho (se borra `FORGOT_DIALOG_TEXT`, ver D-2) |
| C-26 | Component BIP-39 | LicensesScreen.kt:34 | → «Las 2.048 palabras en inglés y las 2.048 en español, sin cambios.» | hecho |
| C-27 | bip39.txt:1 | assets/licenses/bip39.txt:1 | «y» → «and» | hecho |
| §4 | AUTO_BACKUP_REPLACE_DROPPED | ToolTexts.kt:444 | → «Las copias automáticas anteriores que ibas a borrar no se borraron: siguen en su carpeta.» | hecho |
| §4 | PASSWORD_CHANGED_FINGERPRINT_FAILED | ToolTexts.kt:445 | → «…La huella ya no abre la bóveda, pero no se pudo desactivar: abre la bóveda con la contraseña nueva, desactiva la huella y vuelve a activarla.» | hecho |

Test `noForbiddenWords`: ahora detecta «limpio/a/os/as» y «seguro/a/os/as» como palabras completas (no «Seguridad», no el verbo «Limpiar»), sin la excepción de «chip seguro»; se mantiene «no garantiza que sea seguro». `CLOCK_CHANGED_TEXT` se añade a `ALL_TOOL_TEXTS` (no estaba). `AUTO_BACKUP_REPLACED`, `AUTO_BACKUP_REPLACE_WAITS`, `AUTO_BACKUP_REPLACE_DROPPED`, `PASSWORD_CHANGED_FINGERPRINT_FAILED` y los A6 ya estaban.

## Evidencia TDD
Primero tests (con `PHOTO_FILE_BASE` añadida pero el regex y los textos aún antiguos): `ToolTextsTest > noForbiddenWords`, `textsOfTheLegalReviewAreTheProposedOnes`, `supersededAnnexTextsAreGoneFromTheApp`, `afterChangingTheMasterPasswordTheTextsAreTheOnesOfTheDesign`, `KeyPlacementTest > labels` y 8 de `ShareTempTest` fallaron: «34 tests completed, 13 failed». Tras el código: todo en verde.

## Verificación completa
- `gradle -p jvm-check test --offline`: BUILD SUCCESSFUL, 877 tests, 0 fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --offline`: BUILD SUCCESSFUL, 877 tests, 0 fallos, 0 errores.
- Ningún fixture dependía de «foto-limpia» salvo `ShareTempTest` (actualizado; se mantiene un resto con el nombre antiguo en el test de borrado, y el nombre antiguo se comprueba como rechazado).

## Archivos cambiados
app/src/main: assets/licenses/bip39.txt; tools/ToolTexts.kt; tools/share/ShareTemp.kt; ui/screens/{LicensesScreen,PhotoCleanScreen,VaultScreen}.kt; vault/{AutoBackup,VaultStore}.kt. app/src/test: tools/{KeyPlacementTest,ToolTextsTest}.kt; tools/share/ShareTempTest.kt. Anexo y specs sin tocar.

## Desviaciones
- D-1: `SHARE_NAMES` no conserva `foto-limpia` (el dictamen lo pedía «solo para borrar restos»). No hace falta: `clear()` borra toda entrada de `compartir/` sea cual sea su nombre (`ShareTemp.kt` `clear`), y aceptarlo en `prepare` volvería a escribir un archivo con «limpia». Un test comprueba que `prepare("foto-limpia.jpg")` se rechaza y que `onReturn` borra un resto con ese nombre.
- D-2: se conserva `RESET_WIPES_DAMAGED` (en `ToolTexts`, cubierto por tests) y se elimina `FORGOT_DIALOG_TEXT`, en lugar de `FORGOT_DIALOG_TEXT = RESET_WIPES_DAMAGED`. Misma fuente única.
- D-3: constante nueva `PHOTO_FILE_BASE` («copia-de-foto») en `ShareTemp.kt` para el nombre.
- D-4: el test del anexo (`everyAnnexTextIsInTheListVerbatim`) lee el anexo, que no se toca; se añade `SUPERSEDED_ANNEX_TEXTS` en `ToolTextsTest` con los 9 textos antiguos del anexo. Task 15 debe actualizar el anexo y borrar esa lista (un test comprueba que ninguno sigue en la app).
- Los textos de §4 y C-22..C-25 se aplicaron literalmente. No hay otras desviaciones.

## Textos del anexo cambiados (para Task 15)
PHOTO_EXPLANATION (C-9), PHOTO_DEVICE (C-11), PHOTO_AUTHOR_RIGHTS (C-13), PHOTO_HEIF_NOTICE (C-3), PHOTO_FILE_NAME_NOTE y el nombre «foto-limpia» (C-4), QR_EXPLANATION (C-15), QR_SGK1 (C-17), KEY_LOCATION_STRONGBOX y KEY_LOCATION_TEE (C-5, C-6; también la línea 210 del diseño), STRONGBOX_AVAILABLE_NOTICE (C-7, R2), texto A12 (C-25b), línea de BIP-39 de la pantalla de licencias (C-26) y de `bip39.txt` (C-27). Además: política de privacidad, ficha de Play y nombre de archivo citados (R-9).

## Textos para el Abogado Digital
Todos son los propuestos en el dictamen, literalmente. No hay textos nuevos ni distintos de la propuesta. Los dos de §4 (`AUTO_BACKUP_REPLACE_DROPPED`, `PASSWORD_CHANGED_FINGERPRINT_FAILED`) llevan el texto del apartado 4. Pendiente de su visto bueno: que `CODES_UNLOCK_TO_SAVE` quede veraz hasta que CU-B conserve el estado; que «Limpiar foto» (verbo, R-1) siga permitido.

## Revisión experto-seguridad-cripto
- Sin formatos, crypto, permisos, red ni dependencias nuevos; solo cadenas, un nombre de archivo y una constante.
- Nombre de salida neutro: `PhotoCleanScreen.kt` usa `PHOTO_FILE_BASE`; `ShareTemp.kt:13-17` valida el nombre con regex cerrada, rechaza `foto-limpia.*` y `../`, y `clear()` borra cualquier resto.
- Textos de seguridad más exactos: `ToolTexts.kt:438` y `:451` (identidad y copias antiguas), `:458` (las copias .sgbak no se borran), `AutoBackup.kt:95` (la nube y papelera conservan copias).
- Test endurecido: `ToolTextsTest.kt` `noForbiddenWords` (limpio, seguro/a).
- Riesgo residual: C-8 ahora dice la verdad (se pierde al girar), pero la pérdida de datos persiste hasta que CU-B conserve el estado.

## Revisión Ingeniero Móvil Senior
- Constantes de texto compiladas por jvm-check (`ToolTexts.kt`); la UI solo importa constantes (`VaultScreen.kt`, `PhotoCleanScreen.kt`), sin lógica nueva ni cambios de ciclo de vida.
- `CLOCK_CHANGED_TEXT` en `ALL_TOOL_TEXTS` introduce un import `tools` -> `vault` ya existente (`AUTO_BACKUP_REPLACED`), sin ciclos nuevos.
- Lint, assemble y checkLicense/Assets en verde. R-2, R-5, R-6 (lógica y accesibilidad) quedan fuera de esta unidad.

## Fix round 1

Commit: `1.3.0: textos revisados por el Abogado Digital, revisión 1`.

### Cambios
1. NoticeScreen.kt:39 → «Apúntala y guárdala donde solo tú puedas verla, y exporta de vez en cuando una copia cifrada.»
2. VaultEntryScreen.kt:287 y :304 → «🎲 Generar una al azar».
3. IdentityScreen.kt:299 → «¿Borrar tu identidad?»
4. SecurityToolsScreen.kt:162 → «Una clave aleatoria de 256 bits: en la práctica, nadie puede adivinarla. Apúntala en papel y guárdala SEPARADA del texto cifrado: sin ella nadie, ni tú, puede descifrarlo.»
5. Vault.kt:25 `NOTE("Nota segura")` → `NOTE("Nota")`.
6. Test que recorre todas las cadenas de `app/src/main/java` (`SourceTextsTest.kt`, con un analizador de Kotlin en el propio test) y comprueba las palabras prohibidas con `ForbiddenWords.kt` (compartido con `ToolTextsTest`).
7. M-1: `noForbiddenWords` ya no exime ningún texto (ni `QR_NO_ALERTS`, ni `REDACT_EXPLANATION`: ninguna palabra de Tachar está prohibida).
8. M-4: comentario de `ShareTempTest.kt:96` reescrito.
9. M-5: `PHOTO_FILE_BASE` se queda en `ShareTemp.kt:11-15`, con comentario de por qué (junto al código que valida el nombre; una sola fuente).
10. M-6: test `heifNoticeNamesTheRealLimit` liga «24 megapíxeles» a `MAX_REDACT_PIXELS / 1_000_000`.

### Exclusiones del test de cadenas (todas)
Comentarios (// y bloque, KDoc); los assets (lista BIP-39, licencias) no están en `src/main/java`; las cadenas sin espacio (claves JSON, etiquetas de formato, MIME, URL, rutas) no cuentan como texto, pero si llevan una palabra prohibida se siguen señalando; ningún archivo excluido (`EXCLUDED_FILES` vacío a propósito); no hay logs en la app. Solo se cuenta como texto lo que tiene espacio; el test exige más de 500 cadenas comprobadas y más de 50 archivos.

### Prueba de la etiqueta NOTE (solo se muestra, nunca se guarda ni se compara)
- `Vault.kt:23-25`: `@Serializable enum class EntryType(val label: String)`, sin `@SerialName` ni serializador propio: kotlinx guarda el nombre (`"type":"NOTE"`).
- La bóveda cifrada y la copia .sgbak guardan `appJson.encodeToString(entries)` (`Vault.kt:371`, lectura `Vault.kt:292`; el .sgbak lleva ese mismo JSON, `Backup.kt:80-82`, `:219`). No hay exportación CSV ni JSON aparte.
- `label` solo se lee para mostrar: `VaultEntryScreen.kt:279`, `VaultScreen.kt:382`, y como título por defecto de IDENTITY, CONTACT y TOTP (`Contacts.kt:65`, `:103`, `TotpImport.kt:18`; NOTE no tiene ninguno). Ningún `valueOf`, `entries.first`, `find` ni comparación por etiqueta en `app/src/main` (búsqueda `valueOf|EntryType.entries`: solo `Vault.kt:40`, que filtra por `isPersonal`). «Nota segura» no aparece en ningún test ni fixture.
- Test nuevo `vault/EntryTypeCompatTest.kt`: una entrada NOTE escrita como la escribía 1.2.x se lee como NOTE; cada tipo se escribe por nombre y nunca por etiqueta; una etiqueta no se acepta como tipo; la lista (como en el .sgbak) hace ida y vuelta. Ningún formato cambia.

### Mutation check (ejecutado)
Se puso «Apúntala en un lugar seguro» en `NoticeScreen.kt:39`: `SourceTextsTest > noUserVisibleLiteralUsesAForbiddenWord FAILED` con `NoticeScreen.kt:39 [«seguro/a»]`. Se restauró el texto y volvió a verde (git diff solo con el cambio pedido).

### Verificación (una vez, antes de confirmar)
- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 62 tareas.
- Tests: jvm-check 885, app 885, 0 fallos, 0 errores (antes 877).

### Textos para el Abogado Digital (actualizado)
Todos los de la ronda 1 son los de su propuesta, literales (puntos 1 a 5). Ya no hay textos nuevos ni distintos de la propuesta. Para revisar: «Nota» como nombre del tipo (antes «Nota segura»), que aparece en los filtros y en el selector de tipo. Además, el comentario de la ronda anterior sigue vigente: `CODES_UNLOCK_TO_SAVE` queda veraz solo hasta que CU-B conserve el estado.
