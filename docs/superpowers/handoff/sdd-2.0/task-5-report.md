# Tarea 5: plantillas, campos personalizados, notas con formato, duplicar y caducidad: informe

**Estado:** DONE_WITH_CONCERNS (las dudas de abajo; nada bloquea).

**Commits:** el código y los tests están en `053204e` (el controlador lo guardó como «WIP» con el árbol completo; no cambió nada después). Encima, `d007856` «2.0, tarea 5: plantillas, campos, notas con formato y caducidad» (commit vacío que marca la tarea terminada y comprobada sobre ese árbol). Base de la tarea: `788451d`. Entre medias están los commits del controlador `61b8d3f` (logo y temas) y `0fdb43c` (traspaso); no toqué `res/drawable/ic_launcher_*` ni `ui/theme/`.

**Tests (comprobados después del último cambio):**
- `:app:testDebugUnitTest` (forzado con `--rerun`): **2083** tests, 0 fallos (antes 2062). `:app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: verdes. Lint: ningún aviso en los archivos nuevos o tocados (los 21 que hay son de versiones de dependencias, de antes).
- `./gradlew -p jvm-check test`: **2072** tests, 0 fallos (antes 2053). `jvm-check` toma las fuentes por carpeta: no hizo falta ninguna lista.
- TDD: los tests del brief se escribieron primero y fallaron (no compilaban); después, en verde.

## Lo que se ha hecho

- **`vault/Templates.kt`:** `TemplateField` y `Templates.fieldsFor(type)` con los campos del plan (tarjeta: titular, número, caducidad, CVV, PIN; wifi: nombre de la red, contraseña, tipo de seguridad `WPA`/`WEP`/`nopass`, red oculta `true`/`false`; licencia: producto, clave, correo, fecha de compra, caducidad; pasaporte: número, país, fecha de expedición, caducidad, titular). Se guardan en `fields` con `id = key`; los secretos como `HIDDEN`. `isSecretField` (un secreto de plantilla es secreto aunque una copia le ponga otro tipo), `passwordForHealth` (LOGIN y la contraseña del wifi). R-4: `EntryType.CARD` = «Tarjeta bancaria», `WIFI` = «Red wifi»; «WPA/WPA2/WPA3», «WEP», «Abierta (sin contraseña)». R-3: «CVV (opcional)» y «PIN (opcional)», vacíos y no se guardan si están vacíos.
- **`vault/LimitedMarkup.kt`:** `MarkupBlock` (`Heading`, `Paragraph`, `Bullet`, `Numbered`), `Span`, `LimitedMarkup.parse`: sin recursión, una pasada por línea; `#`/`##`/`###`, `- `, `N. ` (hasta 9 cifras), `**…**`, `*…*` (y `***…***`); una marca sin pareja, con espacio al lado o en grupos de 4 o más es texto. Como mucho 10 000 líneas y 64 KiB leídos como formato; la línea que pasa del tope y lo que sigue van tal cual en un último párrafo sin formato. Emparejado lineal (una marca abierta por estilo). `MarkupEdit` para los botones de la barra (envolver la selección y poner o quitar `# `/`- ` al principio de la línea).
- **`ui/screens/MarkupText.kt`:** de bloques a `AnnotatedString` solo con estilos (negrita, cursiva, tamaño de título, «•» y números); ninguna anotación.
- **`vault/Expiry.kt`:** `Expiry.items/next/isDue/dueSoon` (fecha de la entrada, campos `DATE` con `expires`, `rotateMonths` desde el último cambio de contraseña: la versión más nueva del historial con otra contraseña o la lista de la 1.4.3, la más reciente; si no, la creación). Fechas de campo `AAAA-MM-DD` (`parseFieldDate`, solo cifras ASCII, años 1900–2199); el editor acepta `AAAA-MM-DD`, `D/M/AAAA` y, como en una tarjeta, `MM/AA` o `MM/AAAA` (último día del mes) (`normalizeDateInput`, tope de 20 caracteres) y enseña `DD/MM/AAAA`. `expiresAt` es el inicio del día en UTC. Fechas fuera de rango y `rotateMonths` fuera de 1–120 se ignoran sin lanzar.
- **`vault/EntryForm.kt`:** `fieldsToSave` (plantilla primero, luego los propios; fechas normalizadas; número con `[0-9 .,+-]`; los topes de 30 campos, 100 y 10 000 puntos de código con sus textos), `expiresAtToSave`, `templateValueKey` (un valor por tipo: el número de la tarjeta nunca pasa al pasaporte al cambiar de tipo), y en `EntryEditSession`: `notesFormat`, `templateValues`, `customFields`, `expiresText`, `rotateMonths`, `addField` (rechaza el 31.º contando la plantilla), `setFieldValue`, `moveField`, `removeField`; `load` reparte los campos (ids repetidos o de plantilla en un campo propio reciben uno nuevo); `clear` lo borra todo.
- **`VaultStore.duplicate(id)`:** «<título> (copia)», id nuevo, con todo menos adjuntos, historial, lista de contraseñas antiguas y último uso; en `modifyData`. Null si no está, está en la papelera, es personal o la bóveda está cerrada.
- **`ui/screens/EntryFields.kt` (nuevo) y `VaultEntryScreen.kt`:** ficha con los campos de la plantilla (etiquetas fijas, wifi en palabras, fechas en `DD/MM/AAAA`) y los propios (nombre con `plainReportText`), ocultos enmascarados y copiados con `copySensitive`; notas con formato si `LIMITED`; línea de caducidad («⏰ Caducidad: caduca el …», en rojo si entra en los 30 días); «Duplicar» (en un `FlowRow`), que lo dice y ofrece «Abrir la copia». Editor: chips de los 8 tipos al crear, campos de la plantilla (contraseña del wifi con «Generar»), «Campos» con Subir/Bajar/Quitar (con descripción para TalkBack) y «Añadir campo» (nombre y tipo; fecha con «Avisar cuando caduque»), interruptor «Notas con formato…» con la barra Negrita/Cursiva/Título/Lista y la ayuda plegada en «Más información», «Caduca el», «Recordar cambiar la contraseña» (No, 3, 6, 12 meses) para acceso y wifi, y la línea de O-4. Al guardar: `fieldsToSave`, la fecha y `entryLimitProblem()` con su texto; un fallo del store da `storeSaveErrorText`.
- **`VaultList.kt`:** aviso sobre la lista («⏰ N entradas caducan en los próximos 30 días o ya han caducado» + «Ver», que activa «Caducan pronto»), calculado una vez por cambio de la bóveda, fuera del hilo principal; los chips de las plantillas solo salen si hay alguna de ese tipo. **`EntryQuery.kt`:** «Caducan pronto» usa `Expiry.isDue` (cierra lo que el ruling T4 dejó para esta tarea).
- **`EntryHistoryScreen.kt`:** las versiones enseñan sus campos como la ficha (`EntryFieldsCard`), con los secretos de plantilla ocultos aunque la copia diga otro tipo.
- **`health/Health.kt`:** la contraseña del wifi cuenta en «Salud» (reutilizada con un acceso u otro wifi, débil); nunca «antigua», «sin 2FA» ni «filtrada».
- **`AppNavHost.kt`:** `onOpenEntry` de la ficha abre la copia.

**Tests nuevos:** `TemplatesTest` (3), `LimitedMarkupTest` (7, con `theEditorsButtonsWriteTheMarks`), `ExpiryTest` (4), `EntryFormTest` (+3: `hiddenFieldsNeverInSaveable`, `thirtyOneFieldsRefused`, `fieldsFromACopyLoadOneRowEach`), `DuplicateEntryTest` (1), `ui/screens/MarkupTextTest` (2: sin anotaciones de ninguna clase con enlaces, `mailto:`, `tel:`, HTML; y ningún `Linkify`/`LinkAnnotation`/`WebView`/`fromHtml`/`LocalUriHandler` en la ficha), `HealthTest.wifiPasswordCountsOtherSecretsDoNot`; `PlatformSourcesTest` fija `plainReportText` en los nombres de campo, la línea de caducidad y el título de la copia.

## Dudas (decididas por la opción más prudente; se pueden cambiar)

1. **«Comprobar filtraciones» no incluye la contraseña del wifi.** Cuenta en «Salud» (reutilizada, débil), pero la consulta a Pwned Passwords sigue enviando solo los prefijos de las contraseñas de los accesos (`uniquePasswords`): no cambio lo que sale por la red sin que lo revise el Abogado (la política habla de «cada contraseña»). Tampoco «antigua» para un wifi. Si se quiere, es una línea en `uniquePasswords`.
2. **Entrada que pasa del tope en una sola línea:** la línea que cruza los 64 KiB (y todo lo que sigue) sale sin formato, entera, en vez de cortar la línea por la mitad. Solo afecta a notas antiguas de más de 64 KiB (las nuevas no caben).
3. **Ids de campos repetidos de una copia:** no se rechazan como `COPY_DAMAGED`; el editor da id nuevo a cada repetido y la ficha los enseña todos. Los topes (30, 100, 10 000, 64 KiB) siguen siendo los de las tareas 1–4, ya comprobados en `checkCopyEntries`. Rechazar repetidos haría que una bóveda guardada así no pudiera guardar, como pasó con las carpetas (ruling T4 I-1); prefiero que se cure al editar.
4. **Sin selector de calendario:** las fechas se escriben (acepta `DD/MM/AAAA`, `AAAA-MM-DD`, `MM/AA`). El `DatePicker` de Material 3 guarda su estado con `rememberSaveable`; prefiero no tener esa excepción en la ficha. Se puede añadir más adelante con un estado propio.
5. **Wifi nuevo:** empieza con «WPA/WPA2/WPA3» y «Red oculta» apagada (lo más habitual); se guardan los dos valores. CVV y PIN siguen vacíos (R-3).
6. **«Duplicar» solo en la ficha:** el menú «⋮» de la fila es de la tarea 6. Tras duplicar, la ficha lo dice y ofrece «Abrir la copia» (no navega sola).
7. **Etiqueta del tipo wifi:** «Red wifi», para que «wifi» vaya en minúscula también en un chip (R-4). Si el Abogado prefiere «Wifi», es un literal.
8. **El aviso sobre la lista** cuenta lo que vence en 30 días o ya venció; no dice «este mes» (el diseño lo ponía como ejemplo) porque el filtro es de 30 días y así el número coincide con lo que abre «Ver».
9. **El tope de 10 000 líneas** se cuenta por bloques leídos con formato; la respuesta trae como mucho 10 001 bloques.

## Revisión experto-seguridad-cripto

- **Sin criptografía nueva:** todo vive en `VaultEntry` dentro del blob cifrado v4; ningún formato cambia (los campos ya existían desde la tarea 1). Los guardados van por `modifyData` (`VaultStore.kt:842`, `duplicate`).
- **Secretos (campos ocultos):** en el editor con `PasswordField` (`EntryFields.kt:206`, `:232`); en la ficha enmascarados y copiados con `copySensitive` (vía `Field`, y `EntryFields.kt:154` para las notas); un secreto de plantilla es secreto aunque la copia diga `TEXT` (`Templates.kt:87`), también en el historial (`EntryHistoryScreen.kt`, `EntryFieldsCard`). Nunca en estado guardado: viven en `EntryEditSession` (celdas del ViewModel) y se borran con `clear` al bloquear; `hiddenFieldsNeverInSaveable` comprueba que `VaultEntryScreen.kt`, `EntryFields.kt` y `MarkupText.kt` no tienen `rememberSaveable`, `Bundle`, `SavedStateHandle` ni `onSaveInstanceState`. El diálogo «Añadir campo» solo guarda nombre y tipo, con `remember` y `FilterObscuredTouches` (`EntryFields.kt:264`, `:274`).
- **Búsqueda:** solo nombres de campos no ocultos, nunca valores (`EntryQuery.kt:132`, sin cambio).
- **Notas con formato (decisiones (a) y (c)):** lector propio, sin recursión, lineal, con topes de 10 000 líneas y 64 KiB (`LimitedMarkup.kt:45`, `:51`, `:55`, `:86`); 1 MiB hostil (`*`, `**a** `, `*a `, títulos, saltos) en < 200 ms (`hugeInputIsBoundedAndFast`). Solo estilos, ninguna anotación (`MarkupText.kt:40`, `:60`; `MarkupTextTest`). Enlaces, `javascript:`, `mailto:`, HTML e imágenes quedan como texto.
- **Entradas hostiles de una copia:** fechas inválidas, fuera de rango o en cifras no ASCII se ignoran (`Expiry.kt:63`, `:114`, `:117`; `badDateIgnored`); `rotateMonths` fuera de 1–120 no avisa ni rompe (`rotateMonthsCounts`), y el editor lo olvida al cargar; ids de campo repetidos no rompen el editor (`EntryForm.kt:210`); nombres y fechas con `plainReportText` (fijado en `PlatformSourcesTest`).
- **Topes:** el 31.º campo se rechaza en el editor (`EntryForm.kt:234`) y al guardar (`fieldsToSave`, `EntryForm.kt:67`), además de `entryLimitProblem` antes de guardar (`VaultEntryScreen.kt:622`) y del store.
- **Red:** ningún cambio; la contraseña del wifi no se envía a Pwned Passwords (duda 1). Ningún permiso, componente exportado ni dependencia nueva; sin notificaciones (O-4); sin registros.

## Revisión Ingeniero Móvil Senior

- **Estado y ciclo de vida:** todo lo escrito en `EntryEditSession` (ViewModel, `ComposeCells`), que se borra al bloquear también en segundo plano (`followVault`). La selección de las notas es estado local (`remember`) y cede ante el texto de la sesión tras un `load` o un `clear` (`NotesEditor`).
- **Hilo principal:** el aviso de caducidad se calcula en `compute` del ViewModel de la lista, en `Dispatchers.Default` y una vez por cambio de la bóveda (`VaultList.kt:235`); el filtro «Caducan pronto» corre en la misma consulta fuera del hilo principal. El lector de notas se memoriza por texto (`remember(text)`) y está acotado.
- **Compose:** el aviso de la lista usa una clave fija sin «:» (`VaultList.kt:367`, C-1); los campos del editor están en una `Column` (no hay listas perezosas nuevas con claves de datos). Los botones de la ficha pasan a `FlowRow` para que «Duplicar» quepa en un móvil estrecho.
- **Accesibilidad (R-1):** Subir/Bajar/Quitar con texto y descripción con el nombre del campo; el color nunca es la única señal (la línea de caducidad dice «caducó»/«caduca»); `java.time` con `atZone` (API 26; no `LocalDate.ofInstant`, que es de la API 34) y formato con `Locale.ROOT`.
- **Errores:** un tope o un fallo al guardar sale con el texto propio del store (`VaultEntryScreen.kt:375`), no con «No se pudo guardar» genérico.

## Textos para el Abogado Digital (revisión final)

Tipos y plantillas (`Vault.kt`, `Templates.kt`):
- «Tarjeta bancaria», «Red wifi», «Licencia», «Pasaporte».
- Tarjeta: «Titular», «Número», «Caducidad», «CVV (opcional)», «PIN (opcional)».
- Wifi: «Nombre de la red», «Contraseña», «Tipo de seguridad» («WPA/WPA2/WPA3», «WEP», «Abierta (sin contraseña)»), «Red oculta» («Sí»/«No»).
- Licencia: «Producto», «Clave», «Correo», «Fecha de compra», «Caducidad».
- Pasaporte: «Número», «País», «Fecha de expedición», «Caducidad», «Titular».

Editor y ficha (`EntryFields.kt`, `EntryForm.kt`, `VaultEntryScreen.kt`):
- «Campos», «➕ Añadir campo», «Campo nuevo», «Nombre del campo», «Texto», «Oculto», «Número», «Fecha», «Se ve oculto y se copia como una contraseña.», «Avisar cuando caduque», «Añadir», «Cancelar», «Subir», «Bajar», «Quitar» (y para TalkBack «Subir «<nombre>»», «Bajar «<nombre>»», «Quitar «<nombre>»»).
- Etiquetas de fecha: «<campo> (DD/MM/AAAA)», «Caducidad (MM/AA)» en la tarjeta, «Caduca el (opcional, DD/MM/AAAA)».
- «Notas con formato: negrita, cursiva, títulos y listas», «Negrita», «Cursiva», «Título», «Lista»; plegado: «Escribe **así** para negrita y *así* para cursiva. Al principio de una línea: «# » para un título («## » y «### », más pequeños), «- » para una lista y «1. » para una lista numerada. Lo demás se ve tal cual: los enlaces son texto y no se abren.» (R-5: no dice «Markdown».)
- «Recordar cambiar la contraseña», «No», «Cada 3 meses», «Cada 6 meses», «Cada 12 meses».
- O-4: «Los avisos de caducidad solo salen dentro de Clavero, al abrirlo; no hay notificaciones.»
- Errores: «Ponle un nombre a cada campo.», «La fecha de «<campo>» no es válida. Escríbela como DD/MM/AAAA.», ««<campo>» solo admite números.», «La fecha de caducidad no es válida. Escríbela como DD/MM/AAAA.» (y los de la tarea 1: 30 campos, 100 y 10 000 caracteres, 64 KB).
- Duplicar: «Duplicar», «<título> (copia)», «Se ha creado «<título> (copia)», sin adjuntos ni historial.», «Abrir la copia».
- Línea de la ficha (`Expiry.kt`): «⏰ <qué>: caduca el DD/MM/AAAA», «⏰ <qué>: caducó el DD/MM/AAAA», «⏰ Toca cambiar la contraseña el DD/MM/AAAA», «⏰ Toca cambiar la contraseña (desde el DD/MM/AAAA)».
- Aviso de la lista: «⏰ 1 entrada caduca en los próximos 30 días o ya ha caducado» / «⏰ N entradas caducan en los próximos 30 días o ya han caducado», botón «Ver».

Notas para el Abogado: ningún texto nombra marcas de tarjetas, bancos ni software (`templatesNameNoBrands`); el pasaporte no lleva emblemas ni dice que sustituya al documento (O-9); la política (O-10, tarea 12) puede decir que «Salud» cuenta la contraseña del wifi, pero que la comprobación de filtraciones solo envía las de los accesos (duda 1).

---

## Ronda 1 de arreglos (revisión `task-5-review.md`, rulings T5 I-1, I-2, I-3 y menores)

**Commit:** `51aca01` «2.0, tarea 5: ronda 1 de arreglos de la revisión» (sobre `bccc1e4`). Sin push. No toqué `ui/theme/` ni `res/drawable/ic_launcher_*`. El CVV y el PIN usan un campo propio en `EntryFields.kt`, no un cambio de `PasswordField`.

**Tests:** con TDD. Los tests nuevos fallaron primero: los de la API nueva no compilaban, y `CopyFieldsTest` dio 2 de 2 en rojo con `BackupCheck.kt` sin el arreglo. Después, las dos suites completas en verde:
- **app:** `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`, con **2089** tests y 0 fallos (antes 2083). Lint no avisa de nada en los archivos tocados.
- **jvm-check:** **2078** tests, 0 fallos (antes 2072).

| Hallazgo | Cambio | Dónde | Test |
|---|---|---|---|
| **I-1** | `checkCopyEntries` rechaza como `COPY_DAMAGED` los ids de campo repetidos en la entrada y en cada versión de su historial. `loadFields`: si un campo con el id de un secreto de plantilla pasa a ser propio (repetido en una bóveda guardada antes), se queda `HIDDEN`. | `VaultLimits.kt:202` (`fieldsDamaged`), `BackupCheck.kt:170`, `EntryForm.kt:230` | `CopyFieldsTest.repeatedOrOversizedFieldsAreDamaged` e `importAndCheckRefuseThem` («Importar copia» en JSON y en `.sgbak`, y «Comprobar una copia»: nada cambia); `EntryFormTest.fieldsFromACopyLoadOneRowEach`, cambiado a la regla (el segundo `password` queda oculto). |
| **I-2** | `fieldsDamaged` aplica también los topes de 30 campos, nombre ≤ 100 y valor ≤ 10 000 puntos de código, en la entrada y en su historial. El informe decía que esto ya se comprobaba en `checkCopyEntries`: no era cierto, y la frase queda corregida aquí. | igual que I-1 | `CopyFieldsTest`: 31 campos, nombre de 101, valor de 10 001, y 31 campos en una versión (fallan); 30 campos con emojis en el tope (pasan). |
| **I-3** | Cada fila tiene su clave. En el editor: la plantilla con `key(type, t.key)` y los campos propios con `key(form.rowKey(i))`, una clave propia de la sesión («fila-N») que sigue a su campo al quitar o mover y nunca se repite. En la ficha y en el historial: `key("t", t.key)` y `key("c", i, f.id)`. Nunca el id del campo solo. El cambio de casilla «caduca» pasa a la sesión (`setFieldExpires`). | `EntryForm.kt:159`, `:166`, `:273`; `EntryFields.kt:143`, `:149`, `:241`, `:280` | `EntryFormTest.rowKeysFollowTheirFields` (quitar, mover, añadir, bloquear y cargar; y las claves en la fuente, sin `key(f.id)`). |
| **M-1** | Marca propia `VaultEntry.passwordChangedAt`. `withVersion` la pone cuando cambia `passwordForHealth()` (acceso o wifi) y si no conserva la anterior, venga lo que venga del editor. Una entrada nueva con contraseña la recibe al guardarse, «Borrar historial» no la toca y «Duplicar» copia la edad. `lastPasswordChange` es la única función, y la leen «Salud» y el recordatorio. Sin marca (entradas de antes), mira el historial, la lista de la 1.4.3 y la última edición. | `Vault.kt:117`, `EntryHistory.kt:36`, `:47`, `VaultStore.kt:819`, `:856`, `Health.kt:64`, `Expiry.kt:122` | `DuplicateEntryTest.passwordAgeIsKept`; `HealthTest.oldUsesHistoryDate` sigue en verde. |
| **M-2** | `normalizeDateInput(…, monthYear)`: `MM/AA` y `MM/AAAA` solo con `monthYear`, que solo tiene la caducidad de la tarjeta (`TemplateField.monthYear`). | `Expiry.kt:69`, `Templates.kt:46`, `EntryForm.kt:74` | `ExpiryTest.badDateIgnored` («12/11» en la expedición del pasaporte da `fieldBadDate`). |
| **M-3** | La caducidad de la tarjeta se carga y se enseña como `MM/AA` (`displayMonthYear`). Un día que no es el último del mes se enseña con la fecha completa. | `Expiry.kt:88`, `EntryForm.kt:223`, `EntryFields.kt:122` | `ExpiryTest.badDateIgnored`; `EntryFormTest.hiddenFieldsNeverInSaveable` («05/29»). |
| **M-4** | Una sola caducidad por entrada. Tarjeta, licencia y pasaporte tienen la de su plantilla: el editor no ofrece «Caduca el», no se guarda `expiresAt` y `Expiry` no lo cuenta. | `Templates.kt:91`, `Expiry.kt:95`, `:113`, `EntryFields.kt:396`, `VaultEntryScreen.kt:625` | `ExpiryTest.aTemplateHasOneExpiry`; `PlatformSourcesTest` («task 5 review, the minor ones of the screens»). |
| **M-5** | «Duplicar» se deshabilita mientras dura (`duplicating`, que vuelve a falso en `finally`). | `VaultEntryScreen.kt:261` | `PlatformSourcesTest` (ídem). |
| **M-6** | La línea de caducidad usa la etiqueta de la plantilla, no el nombre guardado. | `Expiry.kt:116` | `ExpiryTest.aTemplateHasOneExpiry` («Otro nombre» se enseña como «Caducidad»). |
| **M-7** | La caché del aviso de la lista también depende del día. | `VaultList.kt:235` | `PlatformSourcesTest` (ídem). |
| **M-8** | «⏰» (en la ficha y en la lista), «➕» y «🎲» en su propio `Text` con `clearAndSetSemantics { }`, como `ScanQrLabel` (`DecoratedLabel`). | `EntryFields.kt:187`, `:198`, `VaultList.kt:373` | `PlatformSourcesTest` (ídem). |
| **M-9** | CVV y PIN con `KeyboardType.NumberPassword`, enmascarados, con «Ver» y `NoCopy` (`SecretNumberField`). | `EntryFields.kt:210` | `PlatformSourcesTest` (ídem). |
| **M-10** | `hugeInputIsBoundedAndFast` prueba el peor caso que sí se lee: cuatro líneas de 64 Ki − 1 llenas de marcas (`*a`, `**a*`, `***` y `* `), 10 000 líneas con formato (dentro de los 64 Ki) y el tope exacto con su párrafo final vacío. Todo en menos de 200 ms. | `LimitedMarkupTest.kt` | ídem. |

**Dudas de la ronda:**
- Sin la marca (entradas de antes de la 2.0), `lastPasswordChange` cae en la última edición, como «Salud» en la 1.4.3, y no en la creación. Es lo más prudente: nunca da por vieja una contraseña que se pudo poner en una edición posterior. Una entrada nueva de la 2.0 siempre tiene la marca.
- ~~Una tarjeta, licencia o pasaporte que traiga `expiresAt` (de una copia) lo pierde la próxima vez que se guarda, por M-4.~~ Ya no lo pierde (ruling T5, ronda 1; commit `b91e41f` «2.0, tarea 5: la caducidad de una copia no se pierde»):
  - si la caducidad de la plantilla está vacía, el editor la rellena con esa fecha al cargar (`EntryForm.kt:237`), y el guardado la deja en el campo de la plantilla;
  - los avisos la cuentan mientras la plantilla no tenga una fecha válida (`Expiry.kt:114`, `:117`);
  - si hay las dos, manda la de la plantilla, como antes;
  - tests: `ExpiryTest.aTemplateWithoutItsDateKeepsTheEntrysOwn` y `EntryFormTest.aCopysExpiryGoesToTheTemplatesField`, los dos en rojo antes del arreglo;
  - suites: app 2091 y jvm-check 2080, en verde.

### Revisión experto-seguridad-cripto (ronda 1)
- **Copias hostiles (decisión (a), rulings T5 I-1 e I-2):** los campos se comprueban antes de cambiar nada, en la entrada y en su historial (`BackupCheck.kt:170`, `VaultLimits.kt:202`), y con las mismas comprobaciones en «Importar copia» y en «Comprobar una copia» (`CopyFieldsTest`). Las copias anteriores a la v4 no tienen campos, así que ninguna se rompe.
- **Secretos:** un campo con el id de un secreto de plantilla nunca pasa a verse (`EntryForm.kt:230`). «Ver» no se hereda entre filas (`EntryFields.kt:143`, `:149`, `:241`, `:280`). El CVV y el PIN siguen enmascarados y sin copiar (`EntryFields.kt:210`).
- **Edad de la contraseña:** es un dato del store, dentro del blob cifrado (`Vault.kt:117`). No es un secreto ni sale de la bóveda.
- **Sin cambios:** criptografía, red, permisos y componentes.

### Revisión Ingeniero Móvil Senior (ronda 1)
- **Compose:** cada fila tiene una clave estable, así que el estado local de cada campo (el «Ver» de `PasswordField`) va con su campo (`EntryFields.kt:241`, `:280`). La clave de los campos propios vive en la sesión del ViewModel (`EntryForm.kt:159`), no en estado guardado.
- **Rendimiento:** el aviso de la lista se calcula una vez por cambio de la bóveda y por día, fuera del hilo principal (`VaultList.kt:235`). Una copia ya no puede traer miles de campos a una `Column` (I-2).
- **Accesibilidad (R-1):** las decoraciones fuera de TalkBack (M-8), el teclado de cifras para el CVV y el PIN (M-9) y una sola fecha de caducidad (M-4).
- **Doble toque:** «Duplicar» se protege como «Guardar» (`VaultEntryScreen.kt:261`).

### Textos para el Abogado Digital (revisión final), ronda 1
- No hay textos nuevos. «Caduca el (opcional, DD/MM/AAAA)» ya no sale en tarjeta, licencia ni pasaporte (M-4). «Caducidad (MM/AA)» queda solo para la tarjeta, y las demás fechas piden «(DD/MM/AAAA)» (M-2).
- Las copias con campos dañados dan el texto que ya existía, «La copia está dañada: no se puede importar» (`COPY_DAMAGED`).

## Ronda 2 de arreglos (re-revisión `task-5-rereview-1.md`)

**Commit:** `6b15fe2` «2.0, tarea 5: ronda 2 de arreglos». Sin push. Las dos suites completas en verde: app 2092 y jvm-check 2081. Lint no avisa de nada en los archivos tocados.

- **N-1:** `displayMonthYear` solo da `MM/AA` entre 2000 y 2099, que es lo que se vuelve a leer como 20NN; en cualquier otro año da la fecha completa, así que al editar no se reescribe (`Expiry.kt:91`).
  - Test: `ExpiryTest.badDateIgnored` comprueba 1999, 2150, 2000 y 2099, y que cada fecha vuelve igual al leerla. Estaba en rojo antes del arreglo («12/99» en vez de «31/12/1999»).
- **N-2:** «Caduca el» solo se lee en los tipos que lo enseñan (`entryExpiresAt`, `EntryForm.kt:106`; la pantalla lo usa en `VaultEntryScreen.kt:604`).
  - Al cambiar de tipo con `form.changeType(t)` (`VaultEntryScreen.kt:501`, `EntryForm.kt:263`), una fecha escrita pasa a la caducidad de la plantilla si está vacía. Si ya tiene una, manda la de la plantilla, y al volver a un acceso, «Caduca el» conserva lo escrito.
  - Test: `EntryFormTest.aTypedExpiryFollowsTheTypeChange`, que no compilaba antes del arreglo. El pin de `PlatformSourcesTest` cambia a `entryExpiresAt`.
- **Revisión experto-seguridad-cripto:** no cambian ni los secretos ni la red. Ninguna fecha se pierde ni se reescribe en silencio, y ningún error sale de un campo que no está a la vista.
- **Revisión Ingeniero Móvil Senior:** el cambio de tipo pasa por la sesión del ViewModel (`EntryForm.kt:263`), y la regla se prueba en la JVM.
- **Textos para el Abogado Digital:** ninguno nuevo.
