# Revisión de la tarea 5 de Clavero 2.0: plantillas, campos personalizados, notas con formato, duplicar y caducidad

Rango `788451d..d007856`. El código de la tarea está en `053204e`; `d007856` es un commit vacío. Los commits del controlador `61b8d3f`, `0fdb43c`, `e3eae77` y `1084634` (el logo, los temas y los documentos) quedan fuera de la revisión. El árbol de trabajo (`3e5f95d`) solo cambia el registro y el traspaso respecto a `d007856`.

Revisé el diff entero de la tarea. Fuera de él solo miré los riesgos con nombre:
- `VaultStore.kt`: `modifyData` (`:759-795`), `upsert` (`:805-830`), `replace` (`:1343-1410`) y `storeSaveErrorText` (`:1514-1522`);
- `VaultLimits.kt`: `fittedForSave` y `entryLimitProblem` (`:162-193`);
- `BackupCheck.kt`: `checkCopyEntries` (`:161-172`) y `checkCopyStructure`;
- `EntryHistory.kt` (el orden de `history` y `savedAt`), `EntryQuery.kt:132` (la búsqueda);
- `Field` (`VaultEntryScreen.kt:117-135`), `PasswordField` (`ui/theme/Components.kt:187-201`) y `EntryHistoryScreen.kt:93` (las claves de las versiones).

Leídos: `CLAUDE.md`, el brief, el diseño (plantillas, campos, notas con formato limitado, historial, duplicar, caducidad, O-4, O-9, R-1, R-3, R-4 y R-5), el registro con todos sus «Ruling», también los cuatro «Ruling (T5, …)», y el informe del implementador. De `experto-seguridad-cripto` leí `SKILL.md` («Trabajo conjunto con superpowers»: un hallazgo de seguridad confirmado es Important como mínimo) y apliqué la lista de entradas hostiles y DoS de `references/auditoria.md`; la tarea no toca criptografía. De Ingeniero Móvil Senior apliqué `desarrollo-movil-senior` (estado en Compose, ciclo de vida, hilo principal) y `auditoria-seguridad-movil` (MASVS-STORAGE, -PLATFORM y -CODE).

Tests ejecutados (`export ANDROID_HOME=/home/user/android-sdk`), todos en verde, sin fallos ni errores:

| Clase | Tests |
|---|---|
| `TemplatesTest` | 3 |
| `LimitedMarkupTest` | 7 |
| `ExpiryTest` | 4 |
| `EntryFormTest` | 9 |
| `DuplicateEntryTest` | 1 |
| `MarkupTextTest` | 2 |
| `HealthTest` | 18 |

### Spec Compliance

**Cumple:**
- **API del brief:**
  - `TemplateField` y `Templates.fieldsFor` (`Templates.kt:14`, `:68`), con los campos exactos del plan. Los secretos se guardan con `id = key` y `HIDDEN` (`EntryForm.kt:67-95`), y el wifi, con `WPA`/`WEP`/`nopass` y `true`/`false`.
  - `MarkupBlock`, `Span` y `LimitedMarkup.parse` (`LimitedMarkup.kt:16-63`): sin recursión, una pasada por línea, 10 000 líneas y 64 Ki caracteres.
  - `VaultStore.duplicate` (`VaultStore.kt:842-856`) y `Expiry.dueSoon` (`Expiry.kt:135`, con un parámetro `zone` más, que es razonable). Las fechas se guardan como `AAAA-MM-DD`.
- **Tests del brief:** todos existen con su nombre (`cardSecretsAreHidden`, `everyTemplateFitsLimits`, `templatesNameNoBrands`; los seis de `LimitedMarkupTest`; los cuatro de `ExpiryTest`; `hiddenFieldsNeverInSaveable` y `thirtyOneFieldsRefused`). Hay además `fieldsFromACopyLoadOneRowEach`, `DuplicateEntryTest`, `MarkupTextTest` y `wifiPasswordCountsOtherSecretsDoNot`.
- **Paso 3 (la ficha y el editor):**
  - Los campos de la plantilla, con sus etiquetas, y los propios (`EntryFields.kt:121-137`).
  - «Añadir campo» con nombre y tipo, «Subir», «Bajar» y «Quitar» (`EntryFields.kt:222-260`).
  - La barra de las notas cuando `LIMITED` (`:300-336`), la línea de caducidad (`:164-174`) y «Duplicar» (`VaultEntryScreen.kt:258-269`).
- **Notas con formato (decisiones (a) y (c)):**
  - Solo estilos: `markupAnnotated` nunca añade anotaciones (`MarkupText.kt:40-54`), y `MarkupTextTest` lo comprueba con enlaces, `mailto:`, `tel:`, `javascript:` y HTML.
  - Ningún `Linkify`, `LinkAnnotation`, `WebView`, `fromHtml` ni `LocalUriHandler` en la ficha (`theCardNeverFindsLinks`).
  - `[`, `](`, `<`, `http` y `&lt;` salen literales.
  - Las marcas sin pareja son texto; CRLF bien; las parejas, lineales (una marca abierta por estilo, sin fusiones cuadráticas de tramos: cada `flush` emparejado cambia de estilo).
  - Las cifras de una lista numerada son solo ASCII, y como mucho 9 (caben en un `Int`).
- **R-5:** nada se llama «Markdown» en la interfaz ni en los textos. `FORMAT_HELP` está escrito a mano. Los tests no se parecen a los ejemplos de CommonMark.
- **R-3 y R-4:**
  - «CVV (opcional)» y «PIN (opcional)» empiezan vacíos y no se guardan vacíos.
  - Los tipos se llaman «Tarjeta bancaria» y «Red wifi»; la seguridad se dice «WPA/WPA2/WPA3», «WEP» y «Abierta (sin contraseña)», nunca «segura».
- **O-9:** ninguna marca en `Templates.kt` (`templatesNameNoBrands`, con una lista amplia).
- **O-4:** `EXPIRY_ONLY_IN_APP` está bajo las fechas del editor. No hay notificaciones, permisos ni componentes.
- **Secretos:**
  - Los valores viven en `EntryEditSession` (celdas del ViewModel) y `clear` los borra (`EntryForm.kt:288-292`).
  - Nada va en `rememberSaveable` ni en un `Bundle` (`hiddenFieldsNeverInSaveable` lee los tres archivos).
  - La ficha enmascara con `isSecretField`, que también vale cuando una copia cambia el tipo de un secreto de plantilla (`Templates.kt:87`). Se copia con `copySensitive` (`Field`, `NotesCard`).
  - El historial enmascara igual (`EntryHistoryScreen.kt:160`).
  - La búsqueda solo lee nombres de campos no ocultos, nunca valores (`EntryQuery.kt:132`).
  - El editor se cierra al bloquear (`LockedNotice`, `VaultEntryScreen.kt:436`), así que el `TextFieldValue` local de `NotesEditor` sale de la composición.
- **Topes en el editor y en el store:**
  - El campo 31.º se rechaza contando la plantilla (`EntryForm.kt:234-241`).
  - `fieldsToSave` (`:91-92`) y `entryLimitProblem` antes de guardar (`VaultEntryScreen.kt:622`).
  - El store lo comprueba en `fittedForSave` (`VaultLimits.kt:172`).
- **Cambio de tipo:** un valor por tipo (`templateValueKey`, `EntryForm.kt:48`). El número de la tarjeta nunca pasa al pasaporte (`cardSecretsAreHidden`).
- **Duplicar:**
  - Copia «<título> (copia)» con un id nuevo, sin adjuntos, historial, lista antigua ni último uso, y con su carpeta, etiquetas, campos, notas, fechas y 2FA (el diseño dice «todo menos adjuntos e historial», así que copiar el secreto del 2FA es lo pedido).
  - Lo hace en un solo `modifyData`: respeta el paso pendiente (`UPGRADE_PENDING` con su texto vía `storeSaveErrorText`), la bóveda bloqueada (null) y los topes (una entrada antigua de más de 64 KiB da `ENTRY_TOO_BIG`, porque la copia es nueva).
- **Caducidad:**
  - Las fechas inválidas, fuera de 1900-2199, en cifras no ASCII o de más de 20 caracteres se ignoran sin lanzar, y los bisiestos van con `LocalDate` (`badDateIgnored`).
  - Fuera de 1 a 120, `rotateMonths` no avisa.
  - «Cambiar cada N meses» cuenta desde la versión más nueva con otra contraseña (`savedAt` es cuando se sustituyó, que es lo correcto; ver `withVersion`) o desde `passwordHistory`.
  - La papelera, la identidad y los contactos no avisan. «Caducan pronto» usa `Expiry.isDue` (`EntryQuery.kt:176`).
  - El aviso de la lista se calcula fuera del hilo principal (`VaultList.kt:235`) y su clave fija es `"expiry"`, sin chocar con `lazyKey` (C-1 de la tarea 4).
- **Salud:** la contraseña del wifi cuenta como reutilizada (también con un acceso) o débil, nunca como antigua, sin 2FA ni filtrada. `uniquePasswords` sigue enviando solo los accesos (ruling T5, duda 1).
- **Texto plano:**
  - Nombres de campo, línea de caducidad y título de la copia con `plainReportText` (fijado en `PlatformSourcesTest`).
  - Los valores de campo con `plain = true`.
  - Las notas, como hoy (sin `plainReportText`, igual que la versión sin formato).

**⚠️:**
- **Ruling T5, duda 3 (vinculante):** un id de campo repetido en una copia todavía no se rechaza como `COPY_DAMAGED` (I-1). El ruling lo manda a la ronda de arreglos; se apunta aquí para que no se pierda, junto con el fallo de seguridad que lo justifica.
- **Topes de los campos en las copias:** `checkCopyEntries` no comprueba 30 campos, 100 ni 10 000 (I-2). El informe del implementador dice lo contrario («ya comprobados en `checkCopyEntries`»), y no es así.
- **«Duplicar» en el menú (brief, paso 3):** está como botón en la ficha. El menú «⋮» de la fila es de la tarea 6 (duda 6 del informe). Se acepta.
- **El aviso de la lista:** dice «en los próximos 30 días» en lugar de «este mes». Aceptado por el ruling T5 (dudas 4 y 5).

### Strengths

- El lector de las notas es pequeño y está bien acotado: líneas por `indexOf`, emparejado en dos pasadas y una de escritura, sin expresiones regulares ni recursión, y un último párrafo literal con lo que no se lee. Lo que sale nunca es una anotación, solo estilos.
- `isSecretField` por id de plantilla, además de `kind`, cierra la vía obvia de una copia hostil (un `cvv` con `kind = TEXT`) en la ficha y en el historial. Al editar, `loadFields` la cura: el secreto se vuelve a guardar `HIDDEN`.
- Un valor por tipo de plantilla, con la misma regla que las dos contraseñas de la 1.0.0: no hay fuga de un número de tarjeta a un pasaporte.
- `Expiry` está bien defendido frente a datos hostiles (`Long.MAX_VALUE`, `Int.MIN_VALUE`, cifras de ancho completo, 10 000 caracteres), con `atZone` para la API 26 y `Locale.ROOT`.
- `duplicate` reutiliza `modifyData`: hereda el mutex, el paso pendiente, los topes y la protección frente a un bloqueo a mitad, sin código nuevo de concurrencia.
- Los errores del guardado dicen qué tope falla (`storeSaveErrorText`), no «No se pudo guardar».
- `MarkupText` memoriza el análisis por texto, y la ficha, que se recompone cada segundo por el 2FA, no vuelve a analizar.

### Issues

#### Critical

Ninguno.

#### Important

**I-1. Ids de campo repetidos en una copia: no se rechazan, y al editar un secreto de plantilla queda a la vista** (`BackupCheck.kt:161-172`, `EntryForm.kt:205-212`; ruling T5, duda 3)
- **Qué falla:**
  - `checkCopyEntries` no mira los ids de `fields`, ni en las entradas ni en las versiones de su historial.
  - `loadFields` manda la segunda aparición de un id de plantilla a los campos propios, con un id nuevo y **con el `kind` de la copia**. Al guardar, ese id ya no es de la plantilla, así que `isSecretField` deja de verlo como secreto.
- **Escenario:**
  1. Una copia trae una «Red wifi» con `fields = [password/HIDDEN "señuelo", password/TEXT "clave-real"]`.
  2. Antes de editar, la ficha enmascara los dos (por el id `password`).
  3. El usuario abre «Editar» y guarda sin tocar nada: `fieldsToSave` da `[password/HIDDEN "señuelo", <uuid>/TEXT "clave-real"]`.
  4. Desde ese momento, la ficha y el historial enseñan «clave-real» en claro como un campo de texto «Contraseña».

  Lo mismo pasa con un `number`, `cvv` o `pin` repetido. `fieldsFromACopyLoadOneRowEach` (`EntryFormTest.kt:157`) fija hoy este comportamiento: conserva como campo propio el segundo `password`.
- **Arreglo:**
  - En `checkCopyEntries`, rechazar como `COPY_DAMAGED` una entrada, o una versión de su historial, con ids de `fields` repetidos, como las carpetas y las vistas (ruling T4 I-1). La app nunca escribe ids repetidos, así que nada propio se rompe.
  - Como defensa en profundidad, en `loadFields`, un campo que hereda un id de plantilla secreto se queda `HIDDEN` (`kind = if (t?.secret == true) HIDDEN else f.kind`).
  - Ajustar `fieldsFromACopyLoadOneRowEach` y añadir el test de la copia rechazada (en «Importar copia» y en «Comprobar una copia»).

**I-2. Una copia sin topes de campos deja una entrada que bloquea la ficha y no se puede guardar** (`BackupCheck.kt:161-172`, `VaultStore.kt:1348-1354`; decisión (a), brief «Focos» 3)
- **Qué falla:**
  - El reemplazo por una copia guarda las entradas tal cual (`commit(checked…)`, sin `fittedForSave`). Por el ruling T1, los topes de cada entrada solo se aplican a lo que cambia, para que una bóveda antigua siempre abra.
  - Pero `fields` es nuevo de la v4: ninguna copia de la 1.4.3 o anterior trae campos. Rechazar más de 30 campos, nombres de más de 100 o valores de más de 10 000 no rompe ninguna copia legítima.
  - La comprobación no está.
- **Escenario:**
  1. Una copia de 30 MiB trae una entrada con 400 000 campos `{"id":"…","name":"x","kind":"TEXT","value":""}`; cabe en los 24 MiB de datos.
  2. Al abrir esa entrada, `EntryFieldsCard` compone 400 000 `SectionCard` en una `Column` no perezosa, en el hilo principal, y la app se para (ANR o falta de memoria).
  3. El editor hace lo mismo con `CustomFieldsEditor`, y no puede guardar (`TOO_MANY_FIELDS`) hasta que se quitan campos uno a uno.
  4. El historial (`EntryFieldsCard` por versión) se para igual.
- **Arreglo:**
  - En `checkCopyEntries`, por cada entrada y cada versión de su historial: `fields.size > FIELDS_MAX`, un nombre de más de `FIELD_NAME_MAX` o un valor de más de `FIELD_VALUE_MAX` (en puntos de código) dan `COPY_DAMAGED` (o un texto propio de tope, si se prefiere).
  - Test con una copia hostil en «Importar copia» y en «Comprobar una copia».
  - Corregir la frase del informe.

**I-3. En el editor, un secreto sale a la vista sin pedirlo al quitar, mover o cambiar de tipo** (`EntryFields.kt:227-232`, `:191-206`)
- **Qué falla:**
  - Las filas de `CustomFieldsEditor` y `TemplateFieldsEditor` se componen en un bucle sin `key`.
  - `PasswordField` guarda «Ver» en un `remember` por posición (`Components.kt:188`).
  - Al quitar o mover un campo, o al cambiar el tipo mientras se crea, la fila que ocupa la posición hereda el «Ver» de la anterior.
- **Escenario 1:**
  1. Una entrada con dos campos propios ocultos, «PIN banco» y «PIN tarjeta».
  2. El usuario toca «Ver» en el primero y después «Quitar» en él.
  3. «PIN tarjeta» sube a esa posición y sale en claro.

  Con «Subir» y «Bajar» pasa lo mismo.
- **Escenario 2:**
  1. Al crear una entrada, el usuario elige «Tarjeta bancaria» y toca «Ver» en «Número».
  2. Después cambia a «Red wifi».
  3. «Contraseña», en la misma posición, sale en claro.

  Es un secreto a la vista sin que el usuario lo haya pedido para ese campo (MASVS-PLATFORM, mirar por encima del hombro). Por la skill, un hallazgo de seguridad confirmado es Important.
- **Arreglo:**
  - Envolver cada fila en `key(…)` con una clave que no sea el id del campo solo (ruling T5, duda 3): `key(type, t.key)` en la plantilla, y una clave de fila propia de la sesión en los campos propios (por ejemplo, un contador que `loadFields` y `addField` asignan, con prefijo).
  - O dar a `PasswordField` un parámetro `resetKey`.
  - Fijarlo con un test de fuente o de Compose, como el de las claves de la tarea 4.

#### Minor

**M-1. La «edad» de la contraseña se pierde y no coincide con «Salud»** (`Expiry.kt:98-103`, `health/Health.kt:63`, `VaultStore.kt:848-850`)
- **Qué falla:**
  - `passwordSetAt` cae en `createdAt` cuando el historial no tiene la versión del cambio, y «Salud» («Antigua») cae en `updatedAt`. Son dos definiciones distintas de «último cambio de contraseña».
  - Además, `duplicate` empieza la copia con `createdAt = now` y sin historial, así que la misma contraseña vieja parece nueva en la copia: ni «Antigua» ni recordatorio.
- **Escenarios:**
  - Un acceso creado en 2024, con la contraseña cambiada el mes pasado y «Cada 6 meses». El usuario toca «Borrar historial» (vacía `history` y `passwordHistory`). La ficha dice «Toca cambiar la contraseña (desde el …2024)» y la entrada sale en «Caducan pronto».
  - En un wifi, con 20 ediciones de las notas el cambio sale del historial (el wifi no tiene `passwordHistory`) y pasa lo mismo.
- **Arreglo:** una sola función «último cambio de contraseña» para `Expiry` y `Health`. Lo más robusto es una marca propia del store en la entrada (por ejemplo, `passwordChangedAt`, con valor por defecto, ahora que la v4 aún no se ha publicado), que `upsert` sube cuando cambia `passwordForHealth()`, que «Borrar historial» no toca y que «Duplicar» copia. Si no, documentar el límite en el KDoc y en el texto.

**M-2. `MM/AA` se acepta en cualquier fecha** (`Expiry.kt:68-75`)
- **Escenario:** «12/11» en «Fecha de expedición» del pasaporte (en España se lee 12 de noviembre) se guarda como 31/12/2011, sin aviso.
- **Arreglo:** aceptar `MM/AA` y `MM/AAAA` solo en la «Caducidad» de la tarjeta (un parámetro de `normalizeDateInput`), y pedir el día en las demás.

**M-3. La caducidad de la tarjeta se pide como `MM/AA` y se enseña como `DD/MM/AAAA`** (`EntryForm.kt:208`, `EntryFields.kt:112`, `:211`)
- **Escenario:** se escribe «05/29». La ficha dice «Caducidad 31/05/2029», y al editar el campo con la etiqueta «Caducidad (MM/AA)» trae «31/05/2029».
- **Arreglo:** mostrar y cargar `MM/AA` en la caducidad de la tarjeta, y guardar igual el último día del mes.

**M-4. Dos fechas de caducidad en las plantillas que ya tienen la suya** (`VaultEntryScreen.kt:570`, `EntryFields.kt:340-353`)
- **Qué falla:** tarjeta, licencia y pasaporte enseñan «Caducidad (…)» de la plantilla y además «Caduca el (opcional, DD/MM/AAAA)». Las dos avisan, y la línea de la ficha dice «Caducidad: …» para cualquiera de las dos.
- **Arreglo:** ocultar la fecha de la entrada cuando la plantilla tiene un campo `expires`, o etiquetarla de otra manera.

**M-5. «Duplicar» no se protege de dos toques** (`VaultEntryScreen.kt:258-269`)
- **Escenario:** un doble toque rápido crea dos copias.
- **Arreglo:** un estado `duplicating` que deshabilite el botón mientras dura `store.duplicate`, como `saving` en el editor.

**M-6. La línea de caducidad usa el nombre guardado del campo de plantilla** (`Expiry.kt:111`)
- **Qué falla:** la ficha enseña la etiqueta actual de la plantilla (`t.label`), pero la línea usa `f.name` tal como se guardó. Si el Abogado cambia una etiqueta, las entradas antiguas dicen la vieja. Una copia puede poner cualquier nombre al campo `expiry`.
- **Arreglo:** `Templates.templateField(e.type, f.id)?.label ?: f.name`.

**M-7. El aviso de la lista no se recalcula al cambiar el día** (`VaultList.kt:232-235`)
- **Qué falla:** `dueSoon` se guarda en la caché por identidad de `data`, con el `now` de entonces. El filtro «Caducan pronto» usa un `now` nuevo.
- **Escenario:** con la app abierta pasada la medianoche, el número del aviso no coincide con lo que abre «Ver».
- **Arreglo:** que la caché también dependa del día, como `HealthScreen` (`remember(entries, breached, day)`).

**M-8. TalkBack lee las decoraciones** (`EntryFields.kt:170`, `:258`, `VaultList.kt:369`)
- **Qué falla:** TalkBack lee «despertador» antes de la línea de caducidad y del aviso, y «más» en «Añadir campo».
- **Arreglo:** como el precedente de R-13 (`ScanQrLabel`, `Components.kt:210`), el emoji en su propio `Text` con `clearAndSetSemantics { }` (R-1).

**M-9. El teclado del CVV y el PIN** (`EntryFields.kt:205-206`)
- **Qué falla:** los secretos de plantilla de tipo número (CVV, PIN) usan `PasswordField` con `KeyboardType.Password`, el teclado de letras.
- **Arreglo:** `KeyboardType.NumberPassword` para los secretos `FieldKind.NUMBER`, con un parámetro de `PasswordField`.

**M-10. `hugeInputIsBoundedAndFast` no prueba el lector en el peor caso** (`LimitedMarkupTest.kt:79`)
- **Qué falla:**
  - Las tres entradas hostiles de una sola línea pasan de 64 Ki, así que `spans()` nunca corre sobre ellas: se devuelven enteras como texto, y el test mide un `indexOf`.
  - Tampoco se prueba un texto que acaba justo en `MAX_CHARS` (el último párrafo vacío).
- **Arreglo:** añadir una línea de `MAX_CHARS - 1` caracteres con marcas hostiles (`"*a".repeat(…)`, `"**a*"`, rachas de `***`), 10 000 líneas cortas con formato y el caso del tope exacto, todo bajo el mismo límite de 200 ms.

**Veredicto: spec ❌ (I-1 frente al ruling T5, duda 3, y I-2 frente a la decisión (a) y al foco 3 del brief), quality Needs fixes.** 0 Critical, 3 Important y 10 Minor.

### Revisión experto-seguridad-cripto

- **Criptografía:** la tarea no la toca. Todo vive en `VaultEntry`, dentro del blob cifrado v4; ningún formato nuevo, ninguna primitiva y ningún cambio de red (`uniquePasswords`, `health/Health.kt:32-34`, sigue enviando solo los accesos).
- **Entradas hostiles (copia con su maestra):**
  - Bien: las fechas (`Expiry.kt:41-52`, `:83-87`, `:115-119`), `rotateMonths` (`:113`), el tipo de un secreto de plantilla (`Templates.kt:87`) y los nombres (`plainReportText`, `EntryFields.kt:135`, `:170`, `:228`).
  - Mal: los ids repetidos (I-1: `BackupCheck.kt:161-172`, `EntryForm.kt:210-211`) y el número y tamaño de los campos (I-2: `BackupCheck.kt:161-172`, `VaultStore.kt:1348-1354`).
- **Notas con formato (decisión (c)):**
  - Nada se puede tocar ni abrir: ninguna anotación (`MarkupText.kt:40-54`, `MarkupTextTest.kt:16-24`) y ningún API de enlaces en la ficha (`MarkupTextTest.kt:26-34`).
  - Lineal y acotado (`LimitedMarkup.kt:45-63`, `:86-155`), aunque el test no mide el peor caso (M-10).
- **Secretos:**
  - Fuera del estado guardado (`EntryFormTest.hiddenFieldsNeverInSaveable`), se borran al bloquear (`EntryForm.kt:288-292`, `LockedNotice` en `VaultEntryScreen.kt:436`), se enmascaran y se copian con `copySensitive` (`VaultEntryScreen.kt:125-128`, `EntryFields.kt:154`), también en el historial (`EntryHistoryScreen.kt:160`).
  - Abierto: el «Ver» heredado (I-3: `EntryFields.kt:227-232`, `:191-206`).
  - La búsqueda nunca lee valores (`EntryQuery.kt:132`).
- **Duplicar:** en `modifyData` (`VaultStore.kt:842-856`), con el mutex, el paso pendiente, los topes y el bloqueo a mitad cubiertos. Copia el secreto del 2FA, como pide el diseño. Lo que falta es solo de uso (M-5).
- **Permisos y componentes:** ninguno nuevo; sin notificaciones (O-4); sin registros.

### Revisión Ingeniero Móvil Senior

- **Estado y ciclo de vida:**
  - Todo lo escrito vive en `EntryEditSession` (ViewModel, `EntryForm.kt:146-157`) y se borra con `clear`.
  - La selección de las notas es local y cede ante el texto de la sesión (`EntryFields.kt:304-310`).
  - Falta clave en las filas del editor (I-3: `EntryFields.kt:227`, `:191`).
- **Hilo principal y rendimiento:**
  - El aviso de la lista va en `compute` (`VaultList.kt:235`), aunque su caché no ve el cambio de día (M-7).
  - El análisis de las notas se memoriza por texto (`MarkupText.kt:58`).
  - La ficha compone los campos en una `Column` no perezosa: está bien con 30, pero no con lo que puede traer una copia (I-2).
- **Compose:**
  - La clave fija `"expiry"` no choca con `lazyKey` (`VaultList.kt:367`).
  - Las versiones del historial llevan su clave `savedAt-i` (`EntryHistoryScreen.kt:93`), así que el «Mostrar» de un campo no se hereda entre versiones.
  - Los botones de la ficha en `FlowRow` (`VaultEntryScreen.kt:255`).
- **API mínima:** `java.time` con `atZone` y `LocalDate.of`, válidos en la API 26 (`Expiry.kt:83`, `:138`); formato con `Locale.ROOT`.
- **Accesibilidad (R-1):**
  - Bien: «Subir», «Bajar» y «Quitar» con descripción y el nombre del campo (`EntryFields.kt:252-254`); el color nunca es la única señal («caducó» o «caduca»).
  - Faltan: las decoraciones que TalkBack lee (M-8) y el teclado numérico del CVV y el PIN (M-9).
- **UX:** las fechas `MM/AA` (M-2, M-3) y las dos fechas de caducidad en las plantillas (M-4).
- **No probado aquí:** TalkBack, el teclado del fabricante y el rendimiento en el móvil (pruebas a mano y la revisión final).

### Textos para el Abogado Digital (revisión final)

Los del informe del implementador, con lo que esta revisión puede cambiar:
- **M-2 y M-3:** la etiqueta «Caducidad (MM/AA)» y, en las demás fechas, «(DD/MM/AAAA)», si se deja de aceptar `MM/AA` fuera de la tarjeta.
- **M-4:** «Caduca el (opcional, DD/MM/AAAA)», si se oculta o cambia en las plantillas.
- **I-1 e I-2:** si se usa un texto propio de tope en lugar de `COPY_DAMAGED`, ese texto.
