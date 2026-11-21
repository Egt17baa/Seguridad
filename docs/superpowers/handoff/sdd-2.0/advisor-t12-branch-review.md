# Director: tarea 12, revisión de la rama (paso 3) y el APK

**Veredicto:** empiezan ya tres partes (A «formato y bóvedas», B «copias y ajustes», C «interfaz») sobre `a3ac942..dcf28dc`, cada una en su propio clon y sin gradle; la parte D «compilación y documentos» espera al paso 1 y a la ronda de arreglos de A–C. El APK firmado sale al final de la tarea 12 por PR a `main` (con el OK expreso del dueño); para el emulador vale el APK con clave de prueba del propio PR o de una ejecución a mano sobre la rama.

## Motivos

1. **La base es `a3ac942`** (`v1.4.3`, y es el `merge-base` con `origin/main`). `a3ac942..dcf28dc` son 220 commits: 103 archivos de `app/src/main` (unas 15 800 líneas; `ui` 6 977, `vault` 5 714, `settings` 1 083, `tools` 827) y 89 de tests. Desde `dcf28dc` hasta `ef0f09d` solo cambió `docs/`, y hoy `git diff dcf28dc -- app jvm-check gradle .github scripts .maestro` está vacío: lo que lean A, B y C es el código que se congela, salvo `versionName`.
2. **D no tiene qué leer todavía.** Entre `a3ac942` y `dcf28dc` no cambian `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/`, `.github/`, `scripts/` ni `proguard`; el README cambia 2 líneas. Lo que D debe juzgar (versión, documentos, tabla de afirmaciones, APK minificado) lo produce el paso 1: `app/build.gradle.kts:35` aún dice `versionName = "1.4.3"`.
3. **Los riesgos con nombre siguen donde dice `pendientes.md`:** fila 17 en `vault/A6Text.kt:30` y `:33` (sin id, todo es `LEGACY_A6_REQUEST`) y `settings/SettingsStore.kt:209-210`; fila 31 en `vault/VaultStore.kt:582` y `:593` (las dos comprobaciones `active()` antes de `commit`, `:602`); fila 45 en `vault/Vault.kt:508` y `:515-516` (si el archivo es más barato que Ajustes, se vuelve a envolver) con `VaultStore.kt:587-591` (si eso lanza, la bóveda no abre).
4. **El APK firmado solo sale de `main` o de una etiqueta** (`.github/workflows/build-apk.yml:168-169`); un push a esta rama (`ccr-…`) no lanza nada (`:4-6`, solo `main` y `claude/**`). Un PR o una ejecución a mano fuera de `main` dan `Clavero-apk` con la clave de prueba (`:44`, `:108`), minificado y con `versionCode` = número de ejecución (`:112`). En local el `versionCode` es 1 (`app/build.gradle.kts:34`): no actualiza nada instalado.
5. **Tres revisores y el agente del paso 1 en el mismo checkout chocan** si corren gradle (las dos suites juntas ya dan exit 137) o si alguien cambia de commit. Por eso: clon propio y sin gradle.

## Encargos

### Bloque común (se pega delante de cada encargo)

> Eres revisor de la rama de Clavero 2.0 (app Android de bóveda de contraseñas, Kotlin y Compose). **Solo lectura:** no edites, no hagas commit, no subas nada. Tu único archivo es tu informe.
>
> **Dónde trabajas:** en tu propio clon, nunca en `/home/user/Seguridad` (hay otro agente editando documentos allí):
> `git clone /home/user/Seguridad <scratchpad>/br-<parte> && cd <scratchpad>/br-<parte> && git checkout dcf28dc`
> **No ejecutes gradle** (hay otra suite en marcha y juntas se quedan sin memoria). Si una afirmación necesita ejecutarse, escribe en el informe el test exacto (clase, nombre, cuerpo) en «Pruebas que pide el revisor»; el controlador las corre.
>
> **Qué revisas:** el diff `a3ac942..dcf28dc` (1.4.3 → 2.0) de tu parte y, alrededor, el código entero que haga falta. Es la revisión de **toda la rama**: cada tarea ya tuvo su revisión; a ti te toca lo que **cruza tareas** (una tarea posterior que rompe una garantía de una anterior, dos tareas que suponen cosas distintas de lo mismo), los riesgos con nombre y las filas de `pendientes.md` que se te asignan.
>
> **Lee antes, en este orden:** `CLAUDE.md`; `.claude/skills/experto-seguridad-cripto/SKILL.md` y las `references/` que toquen tu parte; las skills `desarrollo-movil-senior`, `seguridad-criptografia-movil` y `auditoria-seguridad-movil`; `docs/superpowers/handoff/2026-11-15-clavero-2.0-parte.md` (§1); `docs/superpowers/handoff/sdd-2.0/progress.md` (cada «Ruling» y «Owner decision» obliga: no reabras lo decidido, pero di si el código no lo cumple); `docs/superpowers/handoff/sdd-2.0/pendientes.md`; el diseño `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md`; y los `task-*-review.md` y `-rereview-*.md` de las tareas de tu parte (para no repetir lo cerrado).
>
> **Reglas que comprueba toda parte:** decisiones (a) entradas enormes, (b) comprobar y usar, (c) lo importado es dato hostil; solo `javax.crypto` y Bouncy Castle; secretos e imágenes nunca en `rememberSaveable` ni en un `Bundle`, y borrados al bloquear; ningún log con datos; un resultado tardío (tras bloquear, cambiar de bóveda o borrar) no escribe ni enseña nada; cada texto visible es verdad según el código (no nombra una causa que el código no distingue ni promete lo que no hace); nunca se rompen `SG1:`, `.sgenc`, `seguridad-vault` ni `seguridad-backup`.
>
> **Cada fila de `pendientes.md` asignada** sale del informe con un veredicto: «se arregla» (con el arreglo exacto y su test), «se acepta» (con el motivo y la evidencia) o «necesita Ruling» (con las opciones). Ninguna queda sin veredicto.
>
> **Informe:** `/home/user/Seguridad/.superpowers/sdd/2026-11-12-clavero-2.0/branch-review-<parte>.md`. Escríbelo por bloques a medida que avanzas (un reinicio del contenedor no debe perderlo). Estructura: Alcance (archivos leídos enteros y commits) · Issues (Critical, Important, Minor, Info; cada uno con `archivo:línea` de `dcf28dc`, qué pasa, por qué cruza tareas, el arreglo y su test) · Filas de `pendientes.md` (tabla fila → veredicto) · Pruebas que pide el revisor · Dudas · Lo que no pudiste comprobar · «Revisión experto-seguridad-cripto» · «Revisión Ingeniero Móvil Senior» · «Textos para el Abogado Digital (revisión final)». Un hallazgo de seguridad o de veracidad confirmado es Important como mínimo. No reportes nada que no puedas señalar en el código: márcalo «a comprobar» y di cómo.
>
> **Hecho** quiere decir: todos los archivos de tu alcance leídos enteros en `dcf28dc` (no solo el diff), cada riesgo de tu lista con su respuesta y su `archivo:línea`, cada fila con su veredicto, y el informe en su sitio.

### Parte A: formato y bóvedas (`branch-review-formato.md`) — empieza ya

> **Alcance** (tareas 1, 2, 3 y el i-6 de la 10): `crypto/Crypto.kt`; `vault/Vault.kt`, `VaultData.kt`, `VaultStore.kt`, `VaultRegistry.kt`, `VaultLimits.kt`, `PendingUpgrade.kt`, `Trash.kt`, `EntryHistory.kt`, `History.kt`, `Folders.kt`, `Attachments.kt`, `MasterPolicy.kt`, `MasterChanged.kt`, `ContactsView.kt`; `android/BiometricVault.kt`; `AppContainer.kt` (bóvedas, cambio de bóveda, estado por bóveda); `imports/` (el plan de fusión sobre una bóveda v4); la lógica de `ui/screens/VaultUpgrade.kt` y `VaultPickerScreen.kt` (no su aspecto); y sus tests, con los archivos reales de versiones anteriores.
> `git diff a3ac942..dcf28dc -- app/src/main/java/app/seguridad/{crypto,vault,android,imports,AppContainer.kt} app/src/test`
>
> **Riesgos que cruzan tareas:**
> 1. *El paso a v4 frente a todo lo que escribe.* Con un 2 o un 3 en disco y el 4 solo en memoria (`pendingUpgrade`), ¿hay algún camino de las tareas 3 a 10 que guarde (purga de la papelera, historial, carpetas, deshacer, importar, «Ahora no» de las misiones, cambio de maestra, borrar bóveda, copia con calendario) sin pasar por la guarda? Una muerte del proceso en cualquier punto deja el archivo anterior o el nuevo, nunca uno a medias.
> 2. *Aislamiento entre bóvedas.* Cada estado que añadieron las tareas 3 a 10 (filtros y búsqueda, selección, deshacer, formulario, QR pendiente, títulos de misiones, portapapeles pendiente, `PickerPlaces`, marca del reloj A11, clave de la huella, `KdfLock`, `KeyOpsQueue`) es de una bóveda y se borra al bloquear, al cambiar y al borrar la bóveda. Borrar una bóveda borra su clave del Keystore, sus ajustes y sus listas.
> 3. *Resultados tardíos.* El patrón `active()` de i-6 (`VaultStore.kt:582`, `:593`) está en el desbloqueo; ¿y en las demás operaciones largas (cambiar la maestra, importar copia, importar de otra app, comprobar una copia, exportar, activar la huella)? **Fila 31**: describe la ventana que queda antes de `commit`, qué se puede escribir en ella y con qué clave, y si cerrarla pide cambiar `switchTo`.
> 4. *Límites.* Un archivo v4 hostil: topes antes de reservar para cada campo nuevo (historial, papelera, carpetas, campos propios, notas con formato). Una bóveda en el tope de `VaultLimits` sigue cabiendo en una copia (R12, R14), contando papelera e historial.
> 5. *Reloj.* La purga de la papelera al desbloquear con el reloj adelantado, frente a A11.
> 6. *Historial y maestra.* Tras cambiar la maestra (clave de datos nueva), ¿qué queda legible con la clave vieja? «Borrar historial» y las copias.
> 7. *Importar (1.4.0) sobre v4.* Duplicados contra entradas en la papelera, «Reemplazar» y el historial, el plan viejo (R16) con la revisión de la tarea 1, R66 con varias bóvedas.
> 8. *Huella con varias bóvedas.* CRY-A02 (`StaleBiometricKey`) y los 7 días, por bóveda.
>
> **Filas:** 31 (riesgo con nombre), 45, 2, 3 y 38.
> - **45:** `Vault.kt:508` y `:515-516` vuelven a envolver si el archivo cuesta menos que Ajustes; si lanza (`VaultStore.kt:587-591`), la bóveda no abre. Di si se repite en cada intento, si Ajustes deja bajar el coste con la bóveda bloqueada, y si se puede elegir «Alto» en un móvil donde no cabe. Si es un bloqueo sin salida, es Important y necesita Ruling. Valora este arreglo: si volver a envolver falla por memoria, abrir con el coste que el archivo ya tiene (no se baja nada: solo no se sube) y decirlo.
> - **38:** qué enseña `KDF_OUT_OF_MEMORY` en cada sitio y si «Alto» es verdad allí.
> - **2:** solo di si el tamaño de `VaultStore.kt` (1 738 líneas) esconde un defecto; sacar la importación no es de esta versión salvo que lo sea.
> - **3:** el coste de un `upsert` cerca de 24 MiB y dónde corre (nunca en el hilo principal).

### Parte B: copias y ajustes (`branch-review-copias.md`) — empieza ya

> **Alcance** (tarea 8 y la fase A de la 10): `vault/AutoBackup.kt`, `BackupSchedule.kt`, `A6Text.kt`, `Backup.kt`, `BackupCheck.kt`, `BackupCheckSession.kt`, `BackupCheckTexts.kt`, `BackupTargetTexts.kt`, `TrashTexts.kt` (`COPIES_KEEP_IT`); `settings/` entero; `AppContainer.kt` (copias, A6, arranque); la lógica de `ui/screens/VaultBackup.kt`, `SettingsScreen.kt` y `BackupCheckScreen.kt`; `VaultStore.kt` donde toca copias, A6 y `changeMark`; y sus tests, con el `settings.json` real de la 1.4.3.
> `git diff a3ac942..dcf28dc -- app/src/main/java/app/seguridad/{settings,vault,AppContainer.kt} app/src/main/java/app/seguridad/ui/screens/{VaultBackup,SettingsScreen,BackupCheckScreen}.kt app/src/test`
>
> **Riesgo con nombre, fila 17** (Important abierto de la tarea 8, con el arreglo ya decidido): la petición de A6 heredada de la 1.4.3 (`"1.x"`), `A6Text.kt:30`, `:33`, `BackupSchedule.kt:46`, `SettingsStore.kt:208-216`. Comprueba que el diseño de la fila (texto prudente para toda petición sin marca; miembros de `"1.x"` solo la carpeta pendiente o terminada de la migración) cierra los caminos a y b sin abrir otro, escribe la lista exacta de tests (los estados `"1.x"` y `stale` en el exhaustivo, las ramas de `a6StaleText`, los dos caminos desde un `settings.json` real de la 1.4.3) y di qué más cambia de comportamiento. Los textos de `a6Text`, `a6StaleText` y `a6DialogView` son palabra por palabra.
>
> **Riesgos que cruzan tareas:**
> 1. *D-3, «se salta si no hubo cambios».* ¿Cambia la marca (`changeMark`) con **cada** cambio de datos de las tareas 3 a 10 (papelera, historial, carpetas, campos propios, importar, renombrar la bóveda, «Ahora no»)? Un cambio real que no mueve la marca es una copia que no se hace: Important. Y al revés: lo que la app escribe sola (purga de la papelera, `masterCheckedAt`, volver a envolver) ¿hace una copia que nadie pidió, y rota la más antigua?
> 2. *A6 y las copias, por varias bóvedas.* La copia de la bóveda B nunca va a la carpeta de A; A6 solo borra lo apuntado de su bóveda; borrar una bóveda, importar como bóveda nueva y cambiar de bóveda a mitad de una copia.
> 3. *Las copias de antes de la 2.0 y de antes de importar* (`seguridad-antes-de-2-0-`, `seguridad-antes-de-importar-`) con tres destinos, con A6 y con la rotación (R15).
> 4. *Ajustes.* La migración desde un `settings.json` de la 1.4.3 y desde ninguno; `settings.json` y `vaults.json` no quedan incoherentes si el proceso muere entre los dos; un fallo al escribir; lo que queda sin cifrar es exactamente lo de la fila 8 (lista los campos serializados uno a uno: la política sale de ahí).
> 5. *El arranque.* `openingItem` frente a un resultado de A6 pendiente y a la copia del paso a v4; el modo sencillo solo filtra; el buscador de Ajustes solo indexa textos fijos.
> 6. *Calendario.* Solo al abrir la app; un permiso de carpeta perdido; el bloqueo a mitad de una copia (como R66).
>
> **Filas:** 17, 18, 20, 21, 30, 37, 39 y 42.
> - **37:** `BackupCheck.kt:204` da un texto falso con un adjunto de tamaño fuera de rango; el qué hacer al añadir, en `VaultStore.kt:1292`.
> - **39 y 42:** un texto por sitio; di cuánto código pide cada uno.
> - **30:** con la fila 19, la redacción va al Abogado; tú dices qué es verdad con «Conservar 1».
> - **20 y 21:** di si un solo fallo (no dos) llega al mismo estado.

### Parte C: interfaz (`branch-review-interfaz.md`) — empieza ya

> **Alcance** (tareas 4, 5, 5 bis, 6, 7, 9 y la fase B de la 10): `ui/` entero (34 archivos cambiados; `AppNavHost.kt`, `screens/`, `theme/`); `vault/EntryForm.kt`, `FormHelpers.kt`, `EntryQuery.kt`, `FuzzySearch.kt`, `Selection.kt`, `LimitedMarkup.kt`, `Expiry.kt`, `Templates.kt`, `TagPalette.kt` y los archivos `*Texts.kt`; `tools/` (`qr/WifiQr.kt`, `QrContent.kt`, `QrHandoff.kt`, `QrPendingText.kt`, `QrView.kt`, `ToolCatalog.kt`, `TwoStepCopy.kt`, `ClipboardNotice.kt`); `health/`; `android/Clipboard.kt`, `SecureWindow.kt`; `MainActivity.kt`, `SeguridadApp.kt`; y sus tests.
> `git diff a3ac942..dcf28dc -- app/src/main/java/app/seguridad/{ui,tools,health,MainActivity.kt,SeguridadApp.kt} app/src/main/java/app/seguridad/android/Clipboard.kt app/src/main/java/app/seguridad/vault app/src/main/res app/src/test`
>
> **Riesgos que cruzan tareas:**
> 1. *Secretos en el estado.* Cada `rememberSaveable` y cada argumento de navegación de `dcf28dc` (lista todos, con su tipo): ninguno lleva un secreto, un dato de una entrada ni una imagen. Pantallas nuevas: contraseña en grande, QR de wifi, historial de una entrada, formulario, papelera, selector de bóvedas.
> 2. *Bloqueo.* Cada pantalla nueva borra lo suyo y se cierra al bloquear y al cambiar de bóveda; un deshacer, un snackbar o un título de misión con el nombre de una entrada no sobreviven al bloqueo; `sinceLock` en cada acción que termina después de una espera.
> 3. *Ventanas.* `FLAG_SECURE` y las ventanas propias de diálogos, menús y hojas (`android/SecureWindow.kt:24`, `:38`): cada `Dialog`, `Popup`, `DropdownMenu` y hoja nuevos, también con el aspecto «Cristal» (difuminado).
> 4. *Mostrar sin suplantar (R9, R10).* `plainReportText` o `visibleText`, lo que diga su Ruling, en cada sitio nuevo que enseña un título, usuario, URL, carpeta, etiqueta, nombre de bóveda, nombre de un campo propio o una versión del historial.
> 5. *Notas con formato* (`LimitedMarkup.kt`): nunca un enlace, un `Intent` ni nada que se ejecute (decisión (c)); análisis acotado (decisión (a)); `FuzzySearch` con la consulta y el texto acotados.
> 6. *Navegación.* `backOnce` (F-1) en cada pantalla nueva; el giro (F-5, M-4) conserva el estado desde un ViewModel; la lista conserva búsqueda y posición (F-4) con carpetas y vistas; lo que `openingItem` elige es lo que se pinta.
> 7. *Portapapeles.* Cada copia nueva (campo propio, clave del wifi, número de tarjeta, versión del historial) pasa por el mismo camino con aviso (A14) y borrado.
> 8. *QR.* El texto de un QR de wifi es secreto en todos los caminos (también mal formado y de más de 4 096); TOTP (CRY-B02) con las plantillas.
> 9. *Textos.* Texto mínimo (M-3); ninguna palabra prohibida; nombres de otras apps solo en `imports/ImportTexts.kt`; cada texto de la guía y de «Novedades», verdad en cada versión de Android admitida.
> 10. *Hilo principal.* «Salud», las misiones, la búsqueda y la lista con una bóveda en el tope (fila 3).
> 11. *Accesibilidad.* Descripciones que nunca llevan un secreto; lo que solo se puede ver en el móvil, a una fila «Móvil» con el arreglo escrito.
>
> **Filas:** 26, 32 y 33.
> - **26:** la etiqueta «WPA/WPA2/WPA3» (`Templates.kt:29`) frente al QR que escribe `T:WPA`. Propón el texto veraz; la redacción final, al Abogado.

### Parte D: compilación y documentos (`branch-review-compilacion.md`) — **espera**

Empieza cuando el paso 1 esté aprobado por su revisor **y** la ronda de arreglos de A–C esté cerrada (cambiará textos de error que el README cita: filas 37 a 39 y 42). Se encarga sobre ese `HEAD`, no sobre `dcf28dc`; el controlador pone el hash.

> **Alcance:** `a3ac942..<HEAD congelado>`: `app/build.gradle.kts`, `build.gradle.kts`, `gradle/` (con `verification-metadata.xml`), `jvm-check/`, `.github/workflows/`, `scripts/`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/`, `app/proguard-rules.pro`, `.maestro/`, `.claude/skills/`, `README.md`, `docs/privacidad.html`, la ficha (`docs/play/`), `docs/PUBLICAR.md`, `CLAUDE.md`, las notas de la versión, `sdd-2.0/task-12-1-claims.md` y `pendientes.md`.
> A diferencia de las otras partes, **sí compilas**, en tu clon y avisando antes al controlador (una sola suite a la vez).
>
> **Qué compruebas:**
> 1. *Nada nuevo en el APK.* Frente a `a3ac942`: los mismos permisos (`INTERNET`, `CAMERA`), los mismos componentes exportados, el mismo `configChanges`, las mismas dependencias y las mismas sumas; `./gradlew :app:assembleRelease -PfirmaDePrueba=true`, `scripts/check-manifest.sh --release` y `aapt2 dump permissions`, con su salida en el informe. Ese APK no se entrega a nadie.
> 2. *R8.* Cada clase `@Serializable` y cada enum nuevos del formato v4 y de los ajustes sobreviven a la minificación (ningún test usa el APK de release: P-19); lo que solo se ve en el móvil, a una fila «Móvil».
> 3. *jvm-check.* La lista `androidOnly` (`jvm-check/build.gradle.kts:16-22`) no deja fuera tests nuevos; la tarea que lee `docs/superpowers/handoff/*-traspaso.md` (`:63`) sigue pasando con el traspaso y las notas nuevos.
> 4. *Versión.* `versionName`, los tests y los flujos que la fijan; `AppNameTest`; los nombres de formatos y los prefijos, sin cambios.
> 5. *Documentos frente al código.* Cada fila de la tabla de afirmaciones contra el `HEAD` congelado (no contra `dcf28dc`); lo guardado sin cifrar en la política, igual a los campos que listó la parte B; los textos de error del README, iguales a sus constantes; filas 6, 7, 8, 22, 36 y 40 cerradas de verdad.
> 6. *Reglas de lo publicado.* Nombres de otras apps solo en el README y en las notas de GitHub, con la línea de marcas; palabras prohibidas; privacidad del dueño en todo el diff (nombres, correos, dispositivos, rutas de su ordenador), también en `.claude/skills/advisor/SKILL.md` y en `docs/superpowers/handoff/`; la regla «nunca un identificador de modelo en lo que se sube» frente a lo que ya dicen los documentos.
> 7. *`pendientes.md`.* Ninguna fila sin destino; ninguna «Abierto» que no sea del móvil o del dueño; cada «Hecho» con su commit.
> 8. *El flujo de trabajo.* El PR a `main` pasará **build** y **jvm-check**; el `versionCode` de la ejecución será mayor que el de la 1.4.3 instalada.

## Orden con el APK

1. **Ahora, en paralelo:** el paso 1 (documentos) y las partes A, B y C sobre `dcf28dc`. Además, un mensaje corto al dueño que no bloquea nada (punto 13).
2. Revisión del paso 1 (cada fila de la tabla), sus rondas, y las dos suites en un clon limpio. El controlador comprueba que `git diff dcf28dc..HEAD -- app/src/main jvm-check` solo trae la versión; si trae más, la parte afectada relee ese trozo.
3. Consulta al director con los «Issues» y las «Dudas» de A, B y C juntos → **una** ronda de arreglos con un solo implementador a la vez en el checkout (fila 17 primero, en sus propios commits) → re-revisión de cada parte por su revisor → clon limpio.
4. Segunda pasada de documentos por los textos que cambió el punto 3; después, la **parte D**, su ronda y su re-revisión.
5. El ataque (en su propio clon, con la fila 24), sus arreglos y su repaso.
6. `code-review` (alto), `security-review` y la auditoría completa; arreglos; cada uno lo vuelve a comprobar quien lo encontró.
7. Última pasada de documentos, el estado de `CLAUDE.md` y el **Abogado final** con sus cambios; la fecha de la política, justo antes de fusionar.
8. **Puerta del APK.** Todo esto tiene que ser verdad a la vez, sobre un mismo commit:
   - las dos suites en verde en un clon limpio de ese commit;
   - ninguna fila «Abierto» salvo las del móvil y las del dueño;
   - `versionName` 2.0 y sus tests;
   - los mismos permisos y componentes que la 1.4.3 (salida de D, repetida si hubo cambios);
   - ningún Important abierto sin Ruling;
   - las notas de la versión listas.
9. **Se pide al dueño el OK para el PR a `main`** (nunca antes ni sin pedirlo). Con el PR: **build** y **jvm-check** en verde; el artefacto `Clavero-apk` de ese PR es el APK **para el emulador** (clave de prueba, minificado, mismo código).
10. El dueño fusiona (sin etiqueta), lanza *Actions → Build APK → Run workflow* desde `main`, aprueba **build-firma** y **play-bundle**, comprueba la huella `746398ea…9df9e` y descarga `Clavero.apk`: es el APK **para el móvil**, que actualiza la 1.4.x sin perder datos. **Aquí termina la tarea 12, con el APK listo.**
11. **Tarea 11 (Maestro)**, en la rama, desde ese `main`; entra con un segundo PR, pequeño (`.maestro/` y documentos). El dueño ejecuta en el emulador, con el APK de prueba, los flujos que borran datos y los que actualizan desde la 1.4.3; en el móvil, con el firmado, solo los que no borran nada.
12. Lo que falle: arreglo en la rama → quien lo encontró lo comprueba → PR → otro APK firmado. La etiqueta `v2.0.0`, solo cuando la prueba del dueño esté bien.
13. **Qué decirle al dueño ahora:**
    - «El APK firmado para tu móvil sale al final de la tarea 12, por un PR a `main` que te pediré entonces; la 2.0 entra en `main` antes de Maestro, sin etiqueta, como en la 1.4.x, y Maestro va después en un segundo PR. ¿De acuerdo?»
    - «Si quieres un APK antes para el emulador, puedes lanzar *Build APK* a mano sobre la rama `ccr-0aa27f95-u038cd` (o lo lanzo yo si me lo dices). No es el definitivo y no se instala en el móvil.»
    - Dos avisos: el APK de prueba **nunca** en el móvil (obliga a desinstalar y se pierde la bóveda); y antes de instalar la 2.0 firmada, exportar una copia con la 1.4.3 y guardarla fuera del móvil, porque el paso a v4 no tiene vuelta (Android no deja volver a la 1.4.3 sin desinstalar y la 1.4.3 no lee una copia de la 2.0).

## Riesgos o dudas

- **En paralelo con el paso 1:** el riesgo real es gradle (memoria, exit 137) y un `checkout` en el árbol compartido; lo quitan el clon propio y «sin gradle». Los informes van a `.superpowers/sdd/`, que git ignora (`.superpowers/sdd/.gitignore:1`, comprobado): no chocan con los commits del paso 1.
- **Reinicios y límite de uso:** tres revisores más el implementador gastan el límite semanal antes (estaba al 71 %). Ninguna revisión se recorta: si hay que elegir, A y B primero (tienen los Important con nombre) y C después. Por eso los informes se escriben por bloques.
- **Las líneas de A–C son de `dcf28dc`.** Tras el paso 1 el código no debería moverse; si se mueve, el controlador lo dice en el encargo de arreglos.
- **D no puede ser la última palabra sobre los documentos:** el ataque y las revisiones de seguridad traen arreglos después. De ahí la última pasada del punto 7 antes del Abogado.
- **No comprobé:**
  - que una ejecución a mano sobre una rama que no es `claude/**` funcione en este repositorio (el flujo lo prevé, `build-apk.yml:44`, y el README lo lista; depende de los ajustes de Actions);
  - el `versionCode` de la 1.4.x instalada frente al próximo número de ejecución (debería ser mayor, porque el contador solo sube);
  - si el artefacto de prueba de la 1.4.3 sigue disponible para el flujo «actualizar desde la 1.4.3» en el emulador (caduca a los 90 días; si falta, se compila `a3ac942` con `-PfirmaDePrueba=true` y un `versionCode` menor).
- **Fila 45:** no decidí si es un bloqueo sin salida. Leí que volver a envolver ocurre en cada desbloqueo con la maestra mientras el archivo cueste menos que Ajustes (`Vault.kt:508`), así que un fallo se repetiría; falta ver si Ajustes se abre bloqueado y si «Alto» se puede elegir donde no cabe. Lo responde la parte A.
- **Duda para el controlador:** `progress.md` (ya subido) nombra el modelo del director; la regla del parte dice «nunca un identificador de modelo en lo que se sube». Es un nombre, no un identificador técnico, y lo pidió el dueño, pero D debe mirarlo y el controlador decidir si este informe se copia a `docs/` con su línea «Modelo».

## Revisión experto-seguridad-cripto

- `vault/A6Text.kt:30`, `:33` y `settings/SettingsStore.kt:209-210`: la pertenencia a `"1.x"` sin acotar (fila 17) es de veracidad sobre un borrado de copias; sigue Important y va primera en la ronda.
- `vault/VaultStore.kt:582`, `:593`, `:602`: dos comprobaciones de bóveda activa bajo el mutex antes de `commit`; la ventana de la fila 31 solo deja escribir el archivo de A con su propia clave. Es aceptable como riesgo con nombre si la parte A confirma que nada más escribe ahí.
- `vault/Vault.kt:508-516`: al volver a envolver, el coste nunca baja en ningún eje (correcto, CR-2). El arreglo que propongo para la fila 45 respeta eso: no sube, pero no baja.
- `.github/workflows/build-apk.yml:88-91`, `:168-169`: los secretos solo llegan con una etiqueta o a mano desde `main`, en el entorno `firma`; `:44`, `:108`: todo lo demás firma con la clave de prueba pública. El plan del APK no pide tocar nada de esto ni saltarse la protección de `main`.
- `app/build.gradle.kts:20-22`, `:66`: la clave de prueba es pública; ese APK no se distribuye ni se instala sobre datos reales.
- `app/src/main/AndroidManifest.xml:4`, `:6`, `:23-24`: los permisos y lo exportado no cambian frente a la 1.4.3 (archivo sin cambios en `a3ac942..dcf28dc`).

## Revisión Ingeniero Móvil Senior

- `app/build.gradle.kts:34`: `versionCode` 1 en local; un APK compilado aquí no actualiza ninguno instalado. Para el emulador conviene el de la CI (`build-apk.yml:112`, número de ejecución), que además está minificado, lo único que ejercita R8 (P-19).
- `app/build.gradle.kts:35`: `versionName` sigue en 1.4.3 hasta que cierre el paso 1; por eso D espera.
- `android/SecureWindow.kt:24`, `:38`: los diálogos y menús son ventanas propias; con 34 archivos de `ui/` cambiados y el aspecto «Cristal», es el punto 3 de la parte C.
- `rememberSaveable` aparece en 17 archivos de `app/src/main` en `dcf28dc` (entre ellos `AppearanceScreen.kt`, 5, y `SettingsScreen.kt`, 2): la parte C los lista uno a uno.
- `jvm-check/build.gradle.kts:16-22`, `:63`: jvm-check compila `app/src/main/java` menos `androidOnly` y lee los `*-traspaso.md`; un documento nuevo del paso 1 puede romper una tarea de jvm-check, y por eso el paso 1 corre las dos suites.
- `AndroidManifest.xml:23`: `configChanges` del giro (F-5) sin cambios; el estado de las pantallas nuevas tiene que vivir en un ViewModel (punto 6 de la parte C).

**Modelo:** el director (`advisor`).
