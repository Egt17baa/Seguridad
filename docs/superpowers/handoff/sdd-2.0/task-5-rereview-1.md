# Re-revisión 1 de la tarea 5 de Clavero 2.0 (rango d007856..b91e41f)

Alcance: I-1, I-2, I-3, M-1 a M-10 y el ruling sobre `expiresAt`, más lo que los arreglos puedan haber roto. Solo lectura. Leí CLAUDE.md, `experto-seguridad-cripto/SKILL.md`, la revisión original, los rulings T5, el informe de la ronda 1 y el diff entero.
Tests ejecutados (`CopyFieldsTest`, `EntryFormTest`, `ExpiryTest`, `DuplicateEntryTest`, `LimitedMarkupTest`, `HealthTest`, `PlatformSourcesTest`): en verde.

## Estado de cada hallazgo

- **I-1: Arreglado.** `fieldsDamaged` (`VaultLimits.kt:202`) rechaza ids repetidos; `checkCopyEntries` (`BackupCheck.kt:170`) lo aplica a cada entrada y a cada versión del historial. Esa función es la única vía de «Importar copia» (JSON y `.sgbak`, `VaultStore.kt:1360`) y de «Comprobar una copia» (`BackupCheck.kt:90`). `loadFields` (`EntryForm.kt:230`) deja `HIDDEN` cualquier campo que herede un id de secreto de plantilla. Una bóveda ya guardada con esos datos sigue abriendo: ninguna ruta de apertura llama a `checkCopyEntries`, y se edita sin problema (el campo duplicado queda oculto como propio). Test: `CopyFieldsTest.repeatedOrOversizedFieldsAreDamaged` e `importAndCheckRefuseThem` (importa y comprueba JSON y `.sgbak`, y que la bóveda no cambia); `EntryFormTest.fieldsFromACopyLoadOneRowEach` fija el `HIDDEN`. Fallarían sin el arreglo.
- **I-2: Arreglado.** Mismos puntos: 30 campos, nombre ≤ 100 y valor ≤ 10 000 puntos de código, en la entrada y en su historial (`VaultLimits.kt:202-208`). Los emojis cuentan por punto de código y los casos del tope pasan (test). No rompe copias anteriores a la v4 (sin campos). La frase falsa del informe quedó corregida.
- **I-3: Arreglado.** Editor: `key(type, t.key)` (`EntryFields.kt:241`) y `key(form.rowKey(i))` (`:280`). `rowKey` viene de `customRows` (`EntryForm.kt:159-166`), un contador que no se reinicia ni siquiera en `clear`; se mantiene alineado en `loadFields`, `addField`, `moveField`, `removeField` y `clear`, y no hay otro escritor de `customFields` (el checkbox pasó a `setFieldExpires`). Las claves `"fila-N"` y `(tipo, clave)` no chocan con las de la ficha (`key("t", …)`, `key("c", i, f.id)`) ni con las fijas. El «Ver» no pasa entre secretos al quitar, mover ni cambiar de tipo. Test: `EntryFormTest.rowKeysFollowTheirFields` (claves y fuente). Es un test de estado más una comprobación de fuente, no de Compose; aceptable según el ruling.
- **M-1: Arreglado.** `VaultEntry.passwordChangedAt: Long? = null` (`Vault.kt:117`) tiene valor por defecto, así que bóvedas y copias antiguas se leen igual. Solo se pone con un cambio real de `passwordForHealth()`: `withVersion` (`EntryHistory.kt:36`, ignora lo que mande el editor), entrada nueva con contraseña (`VaultStore.kt:819`) y restaurar versión (pasa por `withVersion`). «Borrar historial» no la toca y «Duplicar» la copia (`VaultStore.kt:856`). `lastPasswordChange` (`EntryHistory.kt:47`) es la única función y la leen `Health.kt:64` y `Expiry.kt:122`. El reemplazo de «Importar de otra app» pasa por `withVersion` (`MergePlan.kt:382`), así que también marca. Sin marca, cae en historial, lista de la 1.4.3 y última edición (ruling aceptado). Test: `DuplicateEntryTest.passwordAgeIsKept` y `HealthTest`.
- **M-2: Arreglado.** `normalizeDateInput(…, monthYear)` (`Expiry.kt:69`); solo la caducidad de la tarjeta lo activa (`Templates.kt`, `EntryForm.kt:74`). Test en `ExpiryTest.badDateIgnored`.
- **M-3: A medias** (ver N-1). La tarjeta se enseña y se carga como `MM/AA` (`Expiry.kt:88`, `EntryForm.kt:223`, `EntryFields.kt:122`), pero el formato pierde el siglo.
- **M-4: Arreglado.** `hasOwnExpiry` (`Expiry.kt:95`) oculta «Caduca el» (`EntryFields.kt:396`), no se guarda `expiresAt` (`VaultEntryScreen.kt:625`) y `Expiry.items` cuenta una sola caducidad. Con una salvedad en N-2.
- **M-5: Arreglado.** `duplicating` (`VaultEntryScreen.kt:261`) con `finally`. Comprobado por fuente.
- **M-6: Arreglado.** `Expiry.kt:116` usa la etiqueta de la plantilla. Test `aTemplateHasOneExpiry`.
- **M-7: Arreglado.** La caché guarda `day` y se descarta si cambia (`VaultList.kt:235`). Se recalcula en el siguiente `compute`, igual que la lista, así que ambos usan el mismo día.
- **M-8: Arreglado.** Los emojis en su propio `Text` con `clearAndSetSemantics { }` (`EntryFields.kt:187`, `:198`, `VaultList.kt:373`). Test de fuente.
- **M-9: Arreglado.** `SecretNumberField` (`EntryFields.kt:210`):
  - `NoCopy`, `PasswordVisualTransformation` y `autoCorrectEnabled = false`.
  - `KeyboardType.NumberPassword`, que marca el campo como contraseña para el IME y evita aprender lo que se escribe.
  - `remember`, no `rememberSaveable`.
  - El valor vive en la sesión del ViewModel, que se borra al bloquear.
  - La ficha sigue copiando con `copySensitive`.
  - Test de fuente (`PlatformSourcesTest`).
- **M-10: Arreglado.** `LimitedMarkupTest.hugeInputIsBoundedAndFast` (líneas de 64 Ki − 1 con marcas hostiles, 10 000 líneas con formato y el tope exacto).
- **Ruling `expiresAt`: Arreglado.** `loadFields` (`EntryForm.kt:237`) rellena la caducidad de la plantilla vacía con el `expiresAt` de la copia, y `Expiry.items` (`Expiry.kt:114-117`) lo cuenta mientras la plantilla no tenga una fecha válida. Con las dos, manda la de la plantilla. Tests `aCopysExpiryGoesToTheTemplatesField` y `aTemplateWithoutItsDateKeepsTheEntrysOwn`.

## Problemas nuevos

**N-1. Minor: `MM/AA` pierde el siglo y reescribe la fecha al editar** (`Expiry.kt:88-92`, `:70-76`)
- `displayMonthYear` enseña `d.year % 100`, pero `MONTH_YEAR` lee dos cifras como `2000 + n`. Una caducidad de tarjeta de 1900 a 1999 o de 2100 a 2199 (permitida: `expiryDay` y `date` aceptan 1900-2199, y una copia o un `AAAA-MM-DD` escrito a mano pueden traerla) se carga como «05/50» y se guarda como 2050 sin avisar.
- Arreglo: `displayMonthYear` usa `MM/AA` solo si el año está en 2000..2099 y la fecha completa en los demás casos. Añadir test del ida y vuelta.

**N-2. Minor: la fecha propia oculta por M-4 puede bloquear el guardado o perderse al elegir tipo** (`VaultEntryScreen.kt:603`, `:625`)
- En una entrada nueva, el selector de tipo permite escribir «Caduca el» en un acceso y luego pasar a Tarjeta, Licencia o Pasaporte. `expiresAtToSave(form.expiresText.value)` se evalúa siempre, así que un texto no válido da «fecha no válida» sobre un campo que ya no se ve. Si es válido, se descarta sin aviso al guardar (`expiresAt = if (hasOwnExpiry(type)) … else null`).
- Arreglo: evaluar `expiresAtToSave` solo si `hasOwnExpiry(type)` (y, si se quiere, vaciar `expiresText` al cambiar de tipo). Test de fuente o de sesión.

## Conclusión

Todos los hallazgos de la revisión original y el ruling de `expiresAt` están arreglados como se ordenó, cada uno con un test que fallaría sin el arreglo. Quedan dos Minor introducidos por los arreglos (M-3/M-4), que según CLAUDE.md se arreglan, no se aplazan.

Veredicto: necesita arreglos

### Revisión experto-seguridad-cripto
- Copias hostiles: las tres comprobaciones (ids, topes, historial) corren antes de cambiar nada y por las dos rutas de importar y por «Comprobar una copia» (`BackupCheck.kt:170`, `VaultLimits.kt:202`, `VaultStore.kt:1360`). Una bóveda guardada con datos así sigue abriendo (sin comprobación al abrir).
- Secretos: un id de secreto de plantilla nunca pasa a campo visible (`EntryForm.kt:230`, `Templates.kt` `isSecretField`). «Ver» no se hereda entre filas (`EntryFields.kt:241`, `:280`). CVV y PIN enmascarados, sin copiar en el editor, sin estado guardado y con teclado de contraseña (`EntryFields.kt:210`).
- `passwordChangedAt` está dentro del blob cifrado, no es un secreto y no sale de la bóveda (`Vault.kt:117`). Una copia hostil con valores extremos no lanza (`Expiry.kt:122` captura `DateTimeException`).
- Sin cambios en criptografía, red, permisos ni componentes.

### Revisión Ingeniero Móvil Senior
- Compose: claves estables por fila, con el contador en el ViewModel (`EntryForm.kt:159-166`, `EntryFields.kt:241`, `:280`); la caché del aviso por día, fuera del hilo principal (`VaultList.kt:235`).
- Accesibilidad: emojis decorativos fuera de TalkBack (`EntryFields.kt:187`, `:198`, `VaultList.kt:373`); teclado numérico para CVV y PIN (`EntryFields.kt:210`).
- Doble toque de «Duplicar» protegido (`VaultEntryScreen.kt:261`).
- Pendientes: N-1 (`Expiry.kt:88`) y N-2 (`VaultEntryScreen.kt:603`, `:625`).
