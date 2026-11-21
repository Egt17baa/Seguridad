# Clavero 2.0: revisión final del Abogado Digital

- **Fecha de referencia:** 21-11-2026.
- **Rama:** `ccr-0aa27f95-u038cd`, en `d7f06d8`, frente a la 1.4.3 (`v1.4.3`).
- **Skills:** `despacho-digital`, con `privacidad-proteccion-datos` (política), `regulacion-digital-ue` (CRA, productos defectuosos, accesibilidad, Play) y `propiedad-intelectual-ia` (licencias, marcas de terceros, logo).
- **Hechos que doy por ciertos (los de siempre):** el autor es un particular sin ánimo de lucro, con el seudónimo Egt17baa, residente en España. La app es gratuita, sin anuncios, compras ni donaciones, con licencia Apache-2.0 y código abierto. No recibe datos de nadie: la única conexión es la consulta k-anonimizada a `api.pwnedpasswords.com` cuando el usuario la pide. Se publica en GitHub y en Google Play («no comerciante»), fuera de Francia (con ultramar), Rusia y Bielorrusia. Comprobado en el repositorio: la 2.0 no añade dependencias ni permisos (`git diff v1.4.3..HEAD` de `app/build.gradle.kts`, `gradle/libs.versions.toml`, `config/`, `NOTICE` y `assets/`: solo `versionName` y entradas de tests); el manifiesto solo añade `android:taskAffinity=""` a `MainActivity` (`AndroidManifest.xml:28`).
- **Alcance:** las filas 4, 5, 14, 19, 41, 46 y 53 de `pendientes.md`; todos los apartados «Textos para el Abogado Digital» de `sdd-2.0/` (80 informes) y `task-10c-inventory.md`; las constantes que pide el parte (§3, paso 6), cada una comprobada contra el código; la política (`docs/privacidad.html`), el README, las notas (`2026-11-15-clavero-2.0-notas.md`), la ficha (`docs/ficha-google-play.md`), `docs/PUBLICAR.md` y las licencias.
- **Fuera de alcance:** la fecha de la política (`docs/privacidad.html:22`, fila 49), que se pone justo antes de fusionar. No la pido ahora.

## 1. Conclusión

**Se puede publicar, con cambios.** Con los hechos de arriba, la 2.0 no cambia el mapa normativo de los dictámenes inicial (`abogado-2.0-inicial.md`, §2) y de «Cristal» (`abogado-2.0-cristal.md`, §2):
- el RGPD y la LOPDGDD no se aplican al desarrollador, que no es responsable de ningún tratamiento (art. 4.7 RGPD). Lo que el usuario guarda queda en la excepción doméstica (art. 2.2.c RGPD);
- la LSSI, el TRLGDCU, la Directiva (UE) 2019/770, la Directiva 2005/29/CE, la DSA, la Ley 11/2023 de accesibilidad, el Data Act y el AI Act no se aplican, por falta de actividad económica, de comerciante o de objeto;
- el CRA (Reglamento (UE) 2024/2847) y la Directiva (UE) 2024/2853 tampoco, mientras no haya actividad comercial. Su calendario y la trasposición española de la segunda quedan **pendientes de verificar a fecha de hoy** (no he podido consultar EUR-Lex ni el BOE en esta sesión); no cambian la conclusión;
- no hay dependencias nuevas, así que la compatibilidad de licencias con la Apache-2.0 sigue como en la 1.4.3 (`config/allowed-licenses.json`, `NOTICE` y la pantalla de licencias, sin cambios).

Los cambios obligatorios salen, como siempre, de tres fuentes: la **regla de veracidad** del proyecto, la regla de **«Texto mínimo»** (M-3) y las **políticas de Google Play**, que obligan como contrato de distribución. Son cinco:
- **O-1:** `DELETE_IDENTITY_CONFIRMATION` («no hay forma de recuperarla»), fila 4;
- **O-2:** `a6StaleText` («Las copias hechas después de ese cambio no se han borrado»), fila 19;
- **O-3:** la descripción completa de la ficha pasa de 4 000 caracteres (fila 53). Va acortada en el anexo A;
- **O-4:** dos líneas de las notas de GitHub solo se publican si sale bien su prueba en el móvil;
- **O-5:** la búsqueda de anterioridades del logo (O-C1) sigue siendo condición para publicar el icono.

Ninguno cambia un formato ni quita una función. O-1 y O-2 son cambios de cadenas (y de los tests que las fijan); O-3 y O-4 son documentos; O-5 es un paso del dueño.

## 2. Lo que comprobé en el código

| Texto | Dónde | Lo que hace el código | ¿Cierto? |
|---|---|---|---|
| `LIST_NO_MEMORY` | `vault/Vault.kt:301-303`; `ui/screens/VaultList.kt:390`; `HealthScreen.kt:144` | Un `OutOfMemoryError` al calcular la lista o «Salud»: la bóveda sigue abierta y guardada y no se enseñan las filas | Sí, también en «Salud» («enseñar esta bóveda») |
| `IMPORT_NO_MEMORY` | `imports/ImportTexts.kt:126-128`; `ImportSession.kt:269`; `ImportScreen.kt:612` | Falta memoria al planear o estimar, antes de guardar nada | Sí («No se ha cambiado nada») |
| `COPY_NO_MEMORY` | `vault/BackupCheckTexts.kt:108-110`; `VaultStore.kt:1602-1605`; `BackupCheck.kt:110` | Se lanza antes de escribir nada («nothing has been written before this point») | Sí |
| `VAULT_NO_MEMORY` | `vault/Vault.kt:291-293`; `VaultStore.kt:512`, `:624`, `:755`, `:803` | La bóveda queda guardada y bloqueada; la huella no se apaga | Sí |
| `UPGRADE_COPYING` | `vault/VaultStore.kt:1757-1758`, `:315`; `ContactsView.kt:63` | Sale cuando el paso a v4 espera y aún no se pregunta (`asking` falso) | Casi siempre. Sin carpeta puede salir un instante sin que haya copia (`final-audit-e.md:348`): R-2 |
| `UPGRADE_AUTO_COPY_NOT_DONE` | `ui/screens/VaultUpgrade.kt:58-60` | Espera agotada a los 120 s (`UPGRADE_COPY_WAIT_MS`); verdadero también si la carpeta solo es lenta | Sí |
| `FOLDER_DELETE_NAME_TAKEN` | `vault/Folders.kt:39-40`, `:216-221` | Rechaza antes de cambiar nada si una subcarpeta quedaría junto a otra del mismo nombre (`organizeKey`) | Sí |
| `FOLDER_DELETE_TO_NONE` | `vault/OrganizeTexts.kt:63`; `Folders.kt:222-229` | En una carpeta de arriba: las entradas pasan a `folderId = null` («Sin carpeta») y las subcarpetas a `parentId = null` («Arriba del todo»), con lo que tienen dentro | Sí. Ya no es falso: nombra los dos sitios y no dice que las subcarpetas desaparezcan |
| `ATTACHMENTS_COUNT_WOULD_PASS` | `vault/Attachments.kt:52-54`, `:60-61`; `Backup.kt:62` | `adjuntos + 1 + 2 ≤ BACKUP_MAX_ENTRIES` (2 000), contando la papelera: 1 998 | Sí |
| `KDF_COST_MORE` | `vault/VaultsTexts.kt:135-140`; `crypto/Crypto.kt:62-76` | «Alto» = 128 MiB; se rechaza si pasa de ¾ del *heap*: 128 × 4/3 ≈ 170,7 MiB → «al menos 171 MiB». No dice que subir el coste proteja las copias hechas antes | Sí. Matiz en R-3 |
| `NEEDS_MASTER_NOTICE` | `vault/ContactsView.kt:68-69`; `ContactsScreen.kt:145` | No se alcanza en la 2.0 (KDoc de `MasterPrompt`, `ContactsScreen.kt:120-126`); si se viera, «La bóveda se abrió con la huella» sería falso | No se enseña: R-4 |
| `ENTRY_TOO_BIG` | `vault/VaultLimits.kt:61` | Con E-1 solo sale a quien hizo la entrada más grande | Sí |
| `SETTINGS_NEED_AN_OPEN_VAULT` | `settings/Settings.kt:450-454` | Solo con `NoVault` y otra bóveda con archivo (si no hay ninguna, el ajuste se puede cambiar) | Sí |
| `VAULT_NEW_NEEDS_OPEN` | `vault/VaultsTexts.kt:28`; `AppContainer.kt:668` | `canAddVault()` falso: hay que abrir antes una bóveda propia (RT-22, decisión del dueño del 17-11-2026) | Sí |
| `HEALTH_EMPTY_VAULT` | `tools/ToolCatalog.kt:49`; `HealthScreen.kt:121` | La bóveda elegida no tiene archivo y otra sí (la «bóveda vacía» de RT-22) | Sí, con el sentido de «bóveda vacía» que usa la app (`VAULT_DELETE_EMPTY_WARNING`). Puede confundirse con una bóveda sin entradas: R-5 |
| Las marcas del wifi (`wifiQrNameHidden`, «⟨U+XXXX⟩») | `redteam-fixes-report.md:289`; las de la ficha, `redteam-fixes-2-report.md:198` | Marcan los caracteres que no se ven; el QR los lleva tal cual | Sí. «WPA», «WPA2», «WPA3» y «Wi-Fi» son marcas de la Wi-Fi Alliance: se usan como texto descriptivo, sin ® ni logotipo, y la interfaz dice «wifi», palabra común (art. 14.1.c y 14.2 RMUE; art. 37.1.c y 37.2 LM). Correcto |
| `WIFI_WPA3_NOTE` y «WPA/WPA2» | `vault/Templates.kt:37` | `T:WPA3` y `T:SAE` no se guardan; la plantilla dice «WPA/WPA2» | Sí («puede que», sin prometer) |
| `COPIES_KEEP_IT` | `vault/TrashTexts.kt:20` | «pueden conservarlo; la copia automática quita las más antiguas» | Sí. Fila 30 cerrada; véase R-6 |
| B-4 (README `:24`, `:407`; notas `:30`) | `AppContainer.leftForAnotherApp()`; `tools/TwoStepCopy.kt` | La oferta vive en pantalla dividida o ventana flotante; a pantalla completa, salir bloquea y la acaba | Sí según el código; el director lo deduce de `collectAsStateWithLifecycle` y lo confirma el móvil: O-4 |
| B-2 (notas `:24`; README `:424`) | `AndroidManifest.xml:28` | `taskAffinity=""`: ninguna otra actividad entra en la tarea de Clavero por su afinidad | Sí, con «una forma» y sin prometer nada para Android 11 o posterior; O-4 |
| Comprobar filtraciones (fila 5) | `HealthScreen.kt:253-255`; `health/Health.kt:51-54` | «Salud» cuenta la contraseña del wifi (repetida o débil), pero la consulta solo envía las de los accesos (`type == LOGIN`) | Lo publicado no es falso: dice lo que se envía, y se envía menos. R-1 |
| Selectores al borrar una bóveda (fila 46) | `docs/privacidad.html:48`; `VaultScreen.kt:494-497`; `AppContainer.kt:677-716` | «He olvidado» olvida todos los lugares; «Borrar esta bóveda» no | Sí: la política dice exactamente eso |
| Fila 41 | `vault/VaultStore.kt:98-101`; `crypto/Crypto.kt:221` | Tras la huella, una clave que autentica pero cuyas entradas no se decodifican enseña «Contraseña incorrecta o datos dañados» sin que se haya escrito ninguna | Literalmente cierto («o datos dañados»), pero desorienta: R-7 |

## 3. Las filas de `pendientes.md`

- **Fila 4 (`DELETE_IDENTITY_CONFIRMATION`):** choca con O-2 del dictamen inicial («Ningún texto dice «irrecuperable»…») y con la veracidad. «No hay forma de recuperarla» es falso si hay una copia: importarla la recupera. Además solo nombra las copias «exportadas», cuando las automáticas también la tienen. **O-1.**
- **Fila 5 (la contraseña del wifi en «Comprobar filtraciones»):** **no obliga a nada, y recomiendo no añadirla a la consulta en esta versión.** Enviar más prefijos de hash es un tratamiento nuevo que habría que reflejar en la política y en «Seguridad de los datos». Minimizar es la opción buena (art. 5.1.c RGPD como pauta). Lo que sí conviene es que el botón diga lo que comprueba: **R-1.**
- **Fila 14 (los textos de cada tarea):** revisados todos (§2 y §5). Fuera de O-1, O-2 y los recomendados, cumplen: ninguno nombra otra app fuera de `ImportTexts.kt`, ninguno usa las palabras prohibidas, ninguno dice «irrecuperable», «borrado seguro» ni presenta varias bóvedas como forma de ocultar una (O-1 del inicial), y los límites que hay que ver (calendario, QR del wifi, papelera, paso a v4) están a la vista.
- **Fila 19 (`a6StaleText`):** «Las copias hechas después de ese cambio no se han borrado» es absoluto, y la rotación normal puede haber borrado alguna. El usuario puede fiarse de que existen copias que ya no están. **O-2**, con la redacción de m-4.
- **Fila 41:** **R-7**, que decide el dueño (cambia la decisión CRY-A02 de la 1.3.1, «da el error de siempre»).
- **Fila 46:** **cerrada sin cambios.** La política (`docs/privacidad.html:48`) dice lo que hace el código, también que las direcciones son de todo el móvil. No veo obligación de que borrar una bóveda las olvide: no son datos de esa bóveda, y lo que se apunta (dirección y nombre de un archivo de copia cifrada, de una foto ya limpia o tachada o del texto cifrado guardado) se queda en el almacenamiento privado, fuera de las copias de Android.
- **Fila 53:** **O-3**; la ficha acortada, en el anexo A.

## 4. Cambios obligatorios

### O-1. `DELETE_IDENTITY_CONFIRMATION`: «no hay forma de recuperarla»

- **Dónde:** `app/src/main/java/app/seguridad/vault/ContactsView.kt:44-45` (y el test que lo fije, si lo hay).
- **Texto actual:** «Se borrará tu identidad de esta bóveda y no hay forma de recuperarla. Las copias que ya hayas exportado conservan la identidad que tenían.»
- **Texto propuesto:** «Se borrará tu identidad de esta bóveda: no pasa por la papelera ni se puede deshacer. Las copias ya hechas (automáticas o exportadas) pueden conservar la identidad que tenían.»
- **Por qué:** la regla de veracidad y O-2 del dictamen inicial. La identidad no va a la papelera (decisión 2 de la tarea 3), pero una copia que la tenga sí la recupera al importarla; y las copias automáticas también la tienen hasta que la rotación las quite. `DELETE_IDENTITY_WARNING` («Esto no se puede deshacer», `:40-41`) es cierto y se queda.
- **Base:** regla de veracidad; política de comportamiento engañoso de Google Play; el art. 17 RGPD no se aplica, pero el usuario lee estas frases como un borrado definitivo.

### O-2. `a6StaleText`: las copias hechas después

- **Dónde:** `app/src/main/java/app/seguridad/vault/A6Text.kt:164-165` (y su test; el anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, sección de la tarea 8, si lo copia).
- **Texto actual:** «Las copias hechas después de ese cambio no se han borrado: si cambiaste la contraseña otra vez, puede que alguna se abra con una contraseña anterior. Míralo a mano.»
- **Texto propuesto:** «Esta petición no borró las copias hechas después de ese cambio: si cambiaste la contraseña otra vez, puede que alguna se abra con una contraseña anterior. Míralo a mano.»
- **Por qué:** dice lo que hizo la petición, no un estado de la carpeta que el código no conoce (la rotación pudo quitar alguna; `task-8-rereview-6.md:123`, `:228`). La primera frase (`:163`) no cambia.
- **Base:** regla de veracidad.

### O-3. La ficha de Google Play pasa del máximo

- **Dónde:** `docs/ficha-google-play.md:19-77` (descripción completa).
- **Problema:** 6 948 caracteres sin Markdown; Play admite 4 000 en la descripción completa. No se puede pegar tal cual.
- **Cambio:** sustituir la descripción completa por la del **anexo A** (3927 caracteres contando los 42 saltos de línea; 3885 sin ellos). Se quitan las negritas de Markdown, que Play no interpreta, y los títulos de sección quedan como líneas normales (sin mayúsculas sostenidas). El título (`:11`, «Clavero: bóveda cifrada», 23 caracteres de 30) y la descripción breve (`:17`, 78 de 80) **se quedan**: cumplen la longitud y la política de metadatos (sin superlativos, precio, «n.º 1», emojis ni mayúsculas sostenidas).
- **Lo que conserva la versión corta**, y por qué no se puede quitar: los dos avisos de siempre (sin recuperación y sin auditoría independiente), el aviso del formato nuevo (O-3 y O-11 del inicial), el límite del calendario (O-4 del inicial), «quien vea el código ve la contraseña» (O-4), que borrar no borra de las copias (O-2), que las copias siguen con la maestra anterior, lo que no protegen los contactos, que el archivo exportado de otra app suele estar sin cifrar (R33), «No garantiza que un enlace sea seguro», que el laboratorio clásico no protege nada, que las partes de SLIP-0039 no son una copia SLIP-39 para un dispositivo hardware (sin «cartera»), y la única conexión. No nombra ninguna otra app (Have I Been Pwned es el servicio que se consulta, nombrado ya en la ficha de la 1.4.3 y en la política). No usa «cartera», «wallet», «monedero», «accesible», «limpio», «seguro» o «garantiza» sin «no».
- **Lo que se quita** (sigue en la app, el README y la política): el detalle de cada herramienta, las cifras de la búsqueda y de los formatos de foto, la frase «se saltan si no hubo cambios», la lista de copias que salen del móvil (queda «las copias cifradas que la app hace donde elijas») y la frase de las fotos en «Privacidad».
- **El borrador del repositorio:** propongo dejar en `docs/ficha-google-play.md` la versión del anexo como «Descripción completa» y mover la larga, si se quiere conservar, a una sección «Versión larga (no cabe en Play)».
- **Base:** límite técnico de Play Console; política de metadatos de Google Play (descripción exacta, sin engaño). **Antes de pegarla:** mirar el contador de la consola; si cuenta los saltos de línea como dos caracteres, el texto mide 3969 y sigue cabiendo.

### O-4. Dos líneas de las notas, solo si sale bien su prueba en el móvil

- **Dónde:** `docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md:11` (la lista «Las condiciones para publicar cada línea»).
- **Añadir:**
  - «La línea «Seguridad: se cierra una forma conocida…» (B-2), solo si sale bien la prueba de la fila 85 en el móvil (README, «Pruebas de la 2.0», «otra app en la tarea de Clavero»), al menos en Android 8 a 10.»
  - «En la línea «La lista», la frase de «Copiar contraseña» en pantalla dividida o ventana flotante (B-4), solo si sale bien la prueba de la fila 12 en el móvil. Si no sale, se quita esa frase.»
- **Por qué:** las dos afirmaciones se deducen del código, pero las confirma el móvil (`advisor-t12-audit-fixes.md:382`). Es el mismo criterio que la línea de la huella en horizontal de la 1.4.2. La condición va **fuera** del bloque que se pega (O-8 de la 1.4.2).
- **El texto de la línea B-2 se queda** («una forma conocida», «en Android 8 a 10»): no promete que ninguna app pueda ya enseñar una pantalla suya, ni dice nada de Android 11 o posterior.
- **Base:** regla de veracidad; política de comportamiento engañoso de Google Play para la parte que vaya a Play (las «Novedades» no llevan ninguna de las dos).

### O-5. El logo: la búsqueda de anterioridades antes de publicar el icono

- **Dónde:** `pendientes.md`, fila 15; `docs/PUBLICAR.md:132`.
- **Estado:** pendiente del dueño. Sigue siendo condición para publicar el icono en Play y en el APK firmado (O-C1 de `abogado-2.0-cristal.md`). Si sale algo cercano en TMview, la WIPO Global Brand Database o las tiendas (clases 9, 42 y 45), se cambian detalles del logo antes de publicar.
- **Base:** art. 8.1.b y 9.2.b RMUE; art. 6.1.b y 34.2.b LM; política de suplantación de Google Play.

## 5. Cambios recomendados

| N.º | Dónde | Texto actual | Texto propuesto | Motivo y base |
|---|---|---|---|---|
| R-1 | `ui/screens/HealthScreen.kt:255` (fila 5) | «Comprobar filtraciones (envía solo 5 caracteres del hash de cada una)» | «Comprobar filtraciones de los accesos (envía solo 5 caracteres del hash de cada contraseña)» | «Salud» enseña las redes wifi con contraseña repetida o débil, y la consulta no las mira (`enabled = … type == LOGIN`). Sin la precisión, el usuario puede creer que su wifi está comprobada. Veracidad. Si se aplica, el README (`:27`) puede decir «Si se pulsa «Comprobar filtraciones», consulta … las contraseñas de los accesos, una a la vez» |
| R-2 | `vault/VaultStore.kt:1757-1758` (`UPGRADE_COPYING`) | «Clavero está haciendo la copia automática de antes del cambio al formato nuevo. Espera a que termine e inténtalo otra vez.» | «Clavero está preparando el paso de la bóveda al formato nuevo, con su copia automática si la hay. Espera a que termine e inténtalo otra vez.» | Sin carpeta puede salir un instante sin que haya copia (`final-audit-e.md:348`). La versión propuesta es verdad en los dos casos. Si se aplica, cambia también la prueba de D-1 del README (`:421`) |
| R-3 | `vault/VaultsTexts.kt:137-138` (`KDF_COST_MORE`) | «… sube a este la próxima vez que la abras con la contraseña maestra; nunca se baja.» | «… sube a este la próxima vez que la abras con la contraseña maestra, si el móvil tiene memoria para ello; nunca se baja.» | Concuerda con `KDF_COST_KEPT` (`:122-125`), que dice que puede no subir por falta de memoria. Veracidad |
| R-4 | `vault/ContactsView.kt:68-69` (`NEEDS_MASTER_NOTICE`) | «Para guardar tu identidad y tus contactos hace falta tu contraseña maestra, una sola vez. La bóveda se abrió con la huella.» | «Para guardar tu identidad y tus contactos hace falta tu contraseña maestra, una sola vez.» | No se enseña en la 2.0, pero si un cambio futuro lo alcanzara, la segunda frase sería falsa. Quitarla es una cadena, no código de flujo |
| R-5 | `tools/ToolCatalog.kt:49` (`HEALTH_EMPTY_VAULT`) y `vault/VaultsTexts.kt:128` (`VAULT_EMPTY_NOTHING_TO_COPY`) | «Esta bóveda está vacía. Para crear o importar otra bóveda, abre antes una de las que tienes.» / «Esta bóveda está vacía: no hay nada que copiar.» | «Esta bóveda está vacía: aún no tiene archivo. Para crear o importar otra bóveda, abre antes una de las que tienes.» / «Esta bóveda está vacía: aún no tiene archivo, así que no hay nada que copiar.» | «Vacía» se lee como «sin entradas»; en la app quiere decir «sin archivo» (`VAULT_DELETE_EMPTY_WARNING`). Cambian sus tests (`AppContainerVaultsTest.kt:337-340`) |
| R-6 | `docs/privacidad.html:52`; `README.md:23` (final de «Papelera…»); notas `:29` | «Las copias que ya hiciste pueden seguir teniéndolo: las que exportaste, hasta que las borres; las automáticas, hasta que la rotación las quite.» | «Las copias que ya hiciste pueden seguir teniéndolo: las que exportaste y las de antes de importar o de antes del formato nuevo, hasta que las borres; las automáticas, hasta que la rotación las quite.» (en el README y las notas, «las exportadas y las de antes de importar o de antes del formato nuevo, hasta que las borres; …») | Las copias de antes de importar y la de antes de la 2.0 no rotan (`docs/privacidad.html:47`) y también lo tienen. La política ya las nombra en «Tus derechos» (`:78`); aquí completa la lista (O-2 y O-10.6 del inicial; arts. 12 y 13 RGPD como pauta) |
| R-7 | `vault/VaultStore.kt:98-101` (`biometricUnlockError`; fila 41) | Tras la huella, el texto por defecto de `CryptoFailure`: «Contraseña incorrecta o datos dañados» | Solo en este camino, una constante nueva: «La huella funcionó, pero los datos de la bóveda no se pudieron leer: pueden estar dañados. Prueba con la contraseña maestra.» | No se ha escrito ninguna contraseña. Es literalmente cierto («o datos dañados»), por eso es recomendado. Exige tocar código y cambia la decisión CRY-A02 de la 1.3.1: **decide el dueño**, con el visto bueno técnico |
| R-8 | `docs/ficha-google-play.md:7` | (sin cambio de texto) | Añadir: «La descripción completa no puede pasar de 4 000 caracteres (contador de Play Console).» | Para que no vuelva a pasar del máximo en la siguiente versión |

Ninguno de los textos propuestos lleva palabras prohibidas (`ForbiddenWords.kt`) ni nombres de otras apps. Si se aplican, los tests que fijan esos literales se actualizan con ellos (`PublishedTextsTest`, `AppContainerVaultsTest`, `KdfCostHintTest`, los de A6 y de Contactos).

## 6. Lo demás que revisé, sin cambios

- **Política (`docs/privacidad.html`):** cumple O-10 del dictamen inicial y R-C10 / O-C5 de «Cristal»: varias bóvedas y lo que queda sin cifrar (`:33-41`), lo nuevo de la bóveda cifrada (`:32`), las copias en 3 carpetas con el calendario solo con la bóveda abierta (`:44`), la copia de antes de la 2.0 (`:45`), A6 con las de antes de la 2.0 (`:46`), los selectores (`:48`, fila 46), reconocer lo pegado sin leer el portapapeles (`:51`), la papelera y el historial (`:52`), permisos sin notificaciones (`:68`) y «Tus derechos» (`:78`). La consulta a Have I Been Pwned (`:60-61`) no cambia: la transferencia la inicia el usuario y lo que sale (5 caracteres de un hash, IP y User-Agent) sigue igual (arts. 44 y ss. RGPD no se aplican al desarrollador, que no transfiere nada). La fecha (`:22`), al fusionar.
- **README:** «Pruebas de la 2.0» (`:397-424`) usa solo bóvedas y datos de prueba y no nombra el móvil ni las apps del dueño; `:28` (1 998 adjuntos), `:24` (B-4), `:27` (Salud), `:31` (calendario) y `:424` (B-2, con el `versionCode` de la CI) dicen lo que hace el código. La línea de marcas sigue en su sitio (`:39` y `:395`), y los nombres de otras apps solo describen formatos que se leen (R31). Las listas de pruebas antiguas dicen «Wi-Fi»: son pasos de prueba, no texto de la app.
- **Notas (`2026-11-15-clavero-2.0-notas.md`):** cumplen O-3 y O-11 del inicial y O-C2 / O-C4 de «Cristal». No hace falta la línea de marcas: no nombran otras apps. «No añade permisos, conexiones ni dependencias» (`:22`) es cierto: `taskAffinity` no es un permiso. Las «Novedades» de Play (`:50`) miden 416 caracteres (máximo 500), no nombran otras apps y dicen que las versiones anteriores no abren la bóveda ni sus copias nuevas.
- **Licencias:** sin dependencias ni datos nuevos en el APK; `checkLicense` y `checkAssets` siguen siendo la salvaguarda. El logo lo dibujó la IA por encargo, sin partir de imágenes ajenas; el README (`:9`) ya dice que la Apache-2.0 no da permiso para el nombre ni los iconos (apartado 6 de la licencia).
- **`docs/PUBLICAR.md`:** funciones financieras («ninguna», también con tarjetas, `:98`), «Salud» frente a apps de salud (`:122`), «no comerciante» y el aviso del CRA si se monetiza (`:103`), las capturas (`:124`) y O-C1 (`:132`): correctos.
- **Textos de las tareas 1 a 10 y de las rondas:** véase §3, fila 14. Los puntos que pedían mi juicio y que acepto tal cual: `UPGRADE_EXPORT_FAILED` («o sigue sin copia»; lo que cuesta está a la vista en el mismo diálogo, R-10 del inicial); «Comprueba una copia» como título de la misión (lleva a una pantalla que no hace copias); los dos avisos del QR del wifi (con contraseña y abierta), desviación de O-4 por veracidad; «Contraseña incorrecta» de la pantalla de bloqueo, que conserva sus palabras de la 1.3.1; `masterChangedInOtherVaultText` con el color, ambiguo con más de 8 bóvedas pero no falso; los avisos de A6 con varias carpetas; «Fuerza estimada»; «Se quita del campo donde lo has pegado»; los textos de «Cristal» («contraste suficiente», «Intensidad del cristal»), ya aceptados en la tarea 5 bis.

## 7. Lo que decide el dueño

1. **R-7 (fila 41):** si, tras la huella, unos datos que no se leen dicen un texto propio en lugar de «Contraseña incorrecta o datos dañados». Cambia la decisión CRY-A02 de la 1.3.1.
2. **R-1 (fila 5):** si el botón dice «de los accesos». No recomiendo enviar también las contraseñas del wifi en esta versión.
3. **Los demás recomendados (R-2 a R-6, R-8):** si se aplican en esta ronda.
4. **O-5 (fila 15):** la búsqueda de anterioridades del logo antes de publicar el icono, con su resultado apuntado en `progress.md`, sin datos suyos.
5. **O-4:** publicar las líneas de B-2 y de B-4 solo si sus pruebas en el móvil salen bien.
6. **La ficha (O-3):** dar por buena la versión del anexo, o pedir que se recorte de otra manera (siempre con los avisos que la §4, O-3, dice que no se pueden quitar).
7. **Play Console, antes de enviar** (O-11 del inicial, sin cambios): «Seguridad de los datos» en «No recoge ni comparte datos», «Mi app no ofrece funciones financieras», «Mi app no ofrece funciones de salud» y el público objetivo, con la redacción vigente de la consola.

## 8. Cómo se aplican

Según `CLAUDE.md`, punto 4, `experto-seguridad-cripto` y el Ingeniero Móvil Senior comprueban antes que ningún cambio rompa un formato, cambie el comportamiento o afirme algo falso del código:
- O-1, O-2, R-1 a R-5 son cambios de cadenas, con sus tests. R-7 exige una rama nueva en `biometricUnlockError` y solo se hace si el dueño la elige;
- O-3, O-4, R-6 y R-8 son documentos;
- O-5 es del dueño.

Después se cierra con un clon limpio, como dice el parte (§3, paso 6).

*Borrador profesional para la revisión del responsable. No sustituye el asesoramiento de un abogado colegiado en una jurisdicción concreta.*

## Anexo A. Descripción completa de la ficha, acortada (O-3)

**Recuento** (Python, `len` sobre el texto, que en este texto da lo mismo que las unidades UTF-16 que cuenta la consola): **3927 caracteres** con los 42 saltos de línea, **3885** sin ellos; 3969 si la consola contara cada salto como dos. Máximo de Play: 4 000. Título: 23 de 30. Descripción breve: 78 de 80 (sin cambios).

```
Clavero (antes «Seguridad») guarda tus contraseñas, códigos 2FA, frases semilla, notas, tarjetas bancarias, redes wifi, licencias y pasaportes en bóvedas cifradas en tu móvil, y cifra textos y archivos. Sin cuentas, nube propia, analítica ni anuncios. Código abierto (Apache-2.0).

Bóveda
• Protegida por tu contraseña maestra (Argon2id) y, si quieres, por tu huella. Hasta 10 bóvedas, cada una con su contraseña maestra; solo hay una abierta a la vez.
• Plantillas de tarjeta, wifi, licencia y pasaporte, campos personalizados y notas con formato sencillo.
• Carpetas, vistas, etiquetas de color, favoritos y una búsqueda que perdona erratas.
• Papelera de 30 días con «Deshacer», e historial de cada entrada.
• Adjuntos cifrados (hasta 5 MB cada uno).
• Salud de la bóveda: contraseñas repetidas, débiles, antiguas o sin 2FA.
• Copias cifradas que exportas tú, y copia automática opcional en hasta 3 carpetas, con cada cambio o con calendario. Comprueba una copia sin importarla.
• QR de una red wifi guardada para que otro móvil se conecte. Quien vea el código ve la contraseña.
• Importa de navegadores y de algunos gestores de contraseñas y apps de 2FA (la lista está en la app), con vista previa y una copia cifrada antes.

2FA, cifrado y contactos
• Códigos TOTP con escáner QR o con la clave a mano.
• Cifra textos y archivos (AES-256-GCM, ChaCha20-Poly1305 y otros algoritmos estándar).
• Cifra textos y archivos para tus contactos de Clavero, o fírmalos, y envíalos por el canal que quieras; compara su huella. El cifrado entre contactos está diseñado para resistir también a ordenadores cuánticos futuros (X-Wing, en proceso de estandarización); las firmas (Ed25519), no.

Herramientas
• Generador de contraseñas y frases, PIN, códigos de recuperación y respuestas inventadas.
• Limpiar foto (quita la ubicación y otros datos ocultos del archivo; no cambia lo que se ve) y Tachar en imagen.
• Inspector de QR: enseña qué hay en un QR sin abrir nada. No garantiza que un enlace sea seguro.
• Laboratorio de hashes: que la huella coincida no dice quién la publicó.
• Laboratorio clásico (César, Vigenère y Enigma): cifrados rotos, para aprender; no protegen nada.
• Repartir un secreto (SLIP-0039): las partes no son una copia de recuperación SLIP-39 para un dispositivo hardware.

Aspecto
• Estilo «Cristal», con paneles translúcidos (desde Android 12, las barras y los diálogos difuminan el fondo de la app), o «Sólido», y varias paletas. Modo sencillo y guía al empezar.

Privacidad
Las bóvedas se quedan cifradas en tu móvil. Sale lo que tú exportas, guardas, copias o compartes, y las copias cifradas que la app hace donde elijas. La única conexión a Internet es opcional: al pulsar «¿Filtrada?» o «Comprobar filtraciones», consulta Pwned Passwords de Have I Been Pwned con solo los 5 primeros caracteres de la huella SHA-1 de cada contraseña.

Antes de usarla, ten en cuenta
• Sin recuperación: si olvidas la contraseña maestra, nadie puede recuperar lo guardado. Exporta copias.
• Al actualizar, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra, y las versiones anteriores ya no abren la bóveda ni sus copias nuevas. Exporta antes una copia.
• La copia con calendario solo se hace con la bóveda abierta: si no abres la app, no hay copia.
• Borrar algo no lo borra de las copias ya hechas, y cambiar la contraseña maestra no las cambia: se siguen abriendo con la anterior.
• Si alguien roba tu identidad (por ejemplo, con una copia y tu contraseña maestra), podrá leer lo que te enviaron antes y firmar como tú. El canal ve los metadatos.
• El archivo exportado de otra app suele estar sin cifrar, salvo si lo proteges con contraseña.
• Limpiar foto y Tachar no cambian el original: comparte la copia.
• Sin auditoría independiente. Para fondos importantes, usa un dispositivo hardware dedicado.

Gratuita y sin compras. Código fuente: https://github.com/Egt17baa/Seguridad
```
