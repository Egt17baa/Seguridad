# Auditoría completa de Clavero 2.0, parte B: OWASP MASVS v2 (`auditoria-seguridad-movil`)

**Commit auditado:** el código de `c835a1b`, en la rama `ccr-0aa27f95-u038cd`.
- Es el mismo que el de `69c967b`, el final de la ronda única: `git diff --stat 69c967b c835a1b -- app/ jvm-check/ gradle/` sale vacío.
- Tampoco cambia hasta el HEAD de hoy (`9926a32`, solo documentos): `git diff --stat c835a1b HEAD -- app/ jvm-check/ gradle/` sale vacío.

**APK comparados** (los dos de depuración, compilados en un clon limpio):
- 2.0: clon de `c835a1b`, `app-debug.apk`, SHA-256 `6eb69b74…c02fb5`;
- 1.4.3: etiqueta `v1.4.3` (`a3ac942`), `app-debug.apk`, SHA-256 `4a625d9f…ccbb712`.

**Solo lectura:** no cambié código ni ejecuté Gradle. Usé `git`, `grep`, `aapt2` (`dump permissions`, `dump badging`, `dump xmltree`), `dexdump` sobre los `classes*.dex` de los dos APK, `javap -c` sobre las clases de `navigation-runtime-android` y `navigation-common-android` 2.9.3 de la caché de Gradle, y `scripts/check-manifest.sh` sobre los dos APK. Escribí únicamente este archivo.

**Lecturas previas:** `CLAUDE.md`; `experto-seguridad-cripto` (`SKILL.md`, `references/auditoria.md`, `cripto-moderna.md` y `criptoanalisis.md`); las skills `auditoria-seguridad-movil` y `seguridad-criptografia-movil`; el encargo (`advisor-t12-review-fixes.md`, líneas 237–293); el diseño de la 2.0; `progress.md` (las últimas 40 líneas y las «Ruling» y «Owner decision» de mi parte); `pendientes.md`; `final-audit-c.md`; el parte del 20-11. Para no repetir hallazgos, miré también los títulos de `final-audit-a.md`, `-d.md` y `-e.md` (A-1 toca a B-3).

## Alcance y modelo de amenaza

- **Qué entra:** los ocho grupos de MASVS v2 (STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, RESILIENCE y PRIVACY), sobre toda la app y con más detalle en lo nuevo de la 2.0:
  - el manifiesto fuente y el compilado, los componentes exportados, el `FileProvider`, las reglas de la copia del sistema y `FLAG_SECURE`, con las ventanas de los diálogos y los menús;
  - el portapapeles (A14, el 2FA de un toque y la copia en dos pasos), el teclado, el estado guardado y la navegación;
  - los registros, R8 y la comparación del APK con el de la 1.4.3.
- **Atacantes** (decisión (f)):
  - otra app del móvil: componentes exportados, *intents*, la pila de tareas, superposiciones, portapapeles, *autofill* y accesibilidad;
  - quien tenga el móvil bloqueado o desbloqueado unos segundos;
  - la copia del sistema y la transferencia a otro móvil;
  - la red;
  - la muerte del proceso y el estado guardado;
  - el aislamiento entre bóvedas;
  - la subida desde la 1.4.3.
- **Lo que no entra:** la criptografía y los formatos (parte A); `AutoBackup`, la migración de `SettingsStore` y las pantallas de bóvedas, línea a línea (parte D); el diff de la ronda (parte E); la cadena de suministro (parte C).
- **Lo que no puedo ver desde aquí:**
  - un APK de *release* compilado (comparo los de depuración; la CI comprueba el de *release* con `check-manifest.sh --release`);
  - un móvil. Lo que depende de él va como «a comprobar», con su prueba.

## Resumen

| Severidad | Hallazgo | Ubicación |
|---|---|---|
| A comprobar (Media si se confirma) | B-2. Secuestro de la tarea (*task hijacking*): `MainActivity` conserva la afinidad de tarea por defecto | `app/src/main/AndroidManifest.xml:21-30` |
| Baja | B-1. La navegación obedece a los *extras* del *intent* que abre la app, y en la 2.0 vuelve a obedecerlos en cada cambio de bóveda | `MainActivity.kt:26`, `:30-34`; `ui/AppNavHost.kt:202-204`, `:212`, `:363`; `navigation-runtime` 2.9.3 |
| Informativa | B-3. Borrar una bóveda deja el archivo `shared_prefs/vault_biometric_<id>.xml` | `android/BiometricVault.kt:117-118`, `:188-195`; `AppContainer.kt:747-751` |
| Informativa (funcional y de veracidad) | B-4. «Copiar contraseña» al volver a la app no sale nunca, y el README y las notas dicen que sí | `MainActivity.kt:115-120`; `tools/TwoStepCopy.kt:44-59`, `:78-87`; `README.md:24`, `:407` |

No encontré nada Crítico ni Alto.

El APK de la 2.0 tiene los mismos permisos, componentes y reglas de copia que el de la 1.4.3 (§«Evidencia del APK»). Ninguna clase de `app.seguridad` llama a `Log`, `println`, `printStackTrace`, `Toast`, notificaciones ni `WebView`.

## Hallazgos

### [A comprobar; Media si se confirma] B-2. Secuestro de la tarea: `MainActivity` conserva la afinidad de tarea por defecto

- **Severidad:** a comprobar en el móvil. Si se confirma, Media.
  - Lo impide en parte que haga falta una app maliciosa instalada y que el usuario escriba la maestra en su pantalla.
  - Pero la pantalla falsa sale cuando el usuario abre Clavero desde su propio icono, que es cuando más se fía.
- **CVSS 3.1** (si se confirma): 4.7, `CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:U/C:H/I:N/A:N`.
  - `AC:H`: para sacar la bóveda, además de la maestra hace falta su archivo (privado) o una copia (en una carpeta compartida o en la nube).
- **MASVS v2:** MASVS-PLATFORM-3 (la interfaz) y MASVS-PLATFORM-1 (la interacción con otras apps).
- **CWE:** CWE-1021 (restricción indebida de capas o marcos de la interfaz).
- **Dónde:**
  - `app/src/main/AndroidManifest.xml:21-30`: `MainActivity`, exportada, con `MAIN`/`LAUNCHER` y `singleTop`, sin `android:taskAffinity`;
  - el manifiesto compilado (`aapt2 dump xmltree`) tampoco tiene `taskAffinity` en ninguna actividad. Lo mismo en la 1.4.3;
  - `scripts/check-manifest.sh` no lo comprueba, y ninguna auditoría anterior lo trató (`grep -rni "taskAffinity\|strandhogg"` en `docs/` y `app/src` sale vacío).
- **Qué pasa:**
  - Sin `taskAffinity`, la afinidad de `MainActivity` es el nombre del paquete, `io.github.egt17baa.seguridad`.
  - Una app maliciosa puede declarar una actividad con esa misma afinidad y abrirla en una tarea nueva antes de que el usuario abra Clavero (la técnica conocida como StrandHogg).
  - Al tocar el icono de Clavero, el lanzador busca una tarea con esa afinidad y puede traer al frente la de la app maliciosa, con su actividad encima: por ejemplo, una copia de la pantalla de bloqueo de Clavero.
  - `FLAG_SECURE`, `filterTouchesWhenObscured` y la marca de accesibilidad no ayudan: la pantalla falsa es una ventana de la otra app.
  - Qué versiones de Android lo permiten depende de la versión y de los parches. Android 15 y posteriores endurecen el arranque de actividades entre apps para las que apuntan a la API 35 o más (Clavero apunta a la 36), pero no sé si eso cubre el caso de la afinidad: **a comprobar**.
- **Impacto:** el robo de la contraseña maestra.
  - Con ella y con una copia, toda la bóveda. Las copias automáticas están en carpetas compartidas que el usuario elige, a veces sincronizadas con la nube (`docs/privacidad.html:44`).
  - En Android 8 y 9 (`minSdk` 26), una app con el permiso de almacenamiento lee esas carpetas.
- **Corrección** (cambia el manifiesto, que la puerta del APK compara con la 1.4.3: la decide el director o el dueño, con su prueba en el móvil):
  ```xml
  <activity
      android:name=".MainActivity"
      android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"
      android:exported="true"
      android:launchMode="singleTop"
      android:taskAffinity="">
  ```
  - Con la afinidad vacía, el lanzador solo reutiliza una tarea cuya actividad raíz sea `MainActivity`, nunca la de otra app.
  - `SecureCaptureActivity` y los selectores se abren sin `FLAG_ACTIVITY_NEW_TASK`, así que siguen entrando en la tarea de Clavero.
  - Hay que comprobar en el móvil: el icono, la lista de recientes, los selectores, el escáner, la pantalla dividida y «Al salir».
  - `check-manifest.sh` debería exigirlo, y la lista del README «Entorno de firma» o la puerta del APK tendrían que decir que esta es la única diferencia aceptada con la 1.4.3.
- **Prueba de concepto propuesta:**
  1. **Test de JVM (rojo hoy):** `PlatformSourcesTest.theLauncherActivityHasAnEmptyTaskAffinity`.
     - Lee `app/src/main/AndroidManifest.xml` y toma el elemento `<activity android:name=".MainActivity" …>` hasta su `>`.
     - Comprueba que contiene `android:taskAffinity=""`.
  2. **En el móvil, con una app de prueba que no se publica** (por ejemplo, en `scripts/test-apps/afinidad/`):
     1. La app de prueba tiene una actividad con `android:taskAffinity="io.github.egt17baa.seguridad"` que, al abrirla, se lanza con `FLAG_ACTIVITY_NEW_TASK` y dice «Prueba de afinidad».
     2. Se abre la app de prueba, se pulsa Inicio y se toca el icono de Clavero.
     3. **Vulnerable:** sale «Prueba de afinidad». **Corregido:** sale Clavero.
     4. Se repite en Android 8 o 10 y en uno reciente (15 o 16).

### [Baja] B-1. La navegación obedece a los *extras* del *intent* que abre la app, y en la 2.0 vuelve a obedecerlos en cada cambio de bóveda

- **Severidad:** Baja. No da acceso a ningún dato. Deja que otra app elija qué pantalla de Clavero se abre y con qué argumentos, y contradice lo que el código y las auditorías dicen de la app.
- **CVSS 3.1:** 3.3, `CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N`.
- **MASVS v2:** MASVS-PLATFORM-1 (IPC) y MASVS-CODE-4 (entradas no fiables).
- **CWE:** CWE-940 (verificación indebida del origen de un canal de comunicación).
- **Dónde:**
  - `app/src/main/AndroidManifest.xml:21-30`: `MainActivity`, exportada (tiene que serlo: es la del lanzador);
  - `MainActivity.kt:26` dice: «The app has no other entry point and takes no extras»;
  - `MainActivity.kt:30-34`: `onCreate` no toca el *intent* antes de `setContent`;
  - `ui/AppNavHost.kt:212` (`rememberNavController()`) y `:363` (`NavHost(nav, startDestination = "vault", …)`);
  - `ui/AppNavHost.kt:202-204`: `key(vault) { VaultScoped(…) { VaultNav(…) } }`. Cada cambio de bóveda crea un `NavController` nuevo;
  - la biblioteca, `androidx.navigation:navigation-runtime-android:2.9.3` (`javap -c`):
    - `NavControllerImpl.onGraphCreated` llama a `NavController.checkDeepLinkHandled`;
    - esta, si el campo `deepLinkHandled` del controlador es falso, llama a `handleDeepLink(activity.getIntent())`;
    - `handleDeepLink(Intent)` lee del *intent* `android-support-nav:controller:deepLinkIds` (`Bundle.getIntArray`), `…:deepLinkArgs` y `…:deepLinkExtras`, sin mirar de dónde viene;
  - en `navigation-common` 2.9.3, `NavDestinationImpl.matchDeepLink` solo prueba los *deep links* declarados, y la app no declara ninguno. El enlace interno de cada ruta (`android-app://androidx.navigation/<ruta>`) va aparte (`routeDeepLink`, que solo usa `matchRoute`). Así que una URI en `data` no entra: **la única puerta son los *extras***.
- **Qué pasa:**
  - Cualquier app puede abrir `MainActivity` con esos *extras*. Desde Android 13, con el `targetSdk` 36, el *intent* tiene que encajar en el filtro: basta con la acción `MAIN`, y los *extras* no se filtran.
  - El `NavController` navega entonces a la ruta que la otra app elija, con los argumentos que elija:
    - el id de cada destino es el `hashCode` de `"android-app://androidx.navigation/" + ruta`; por ejemplo, `tools/generator` da 536627314 y `vaults` da −1157573042;
    - el del grafo raíz, sin ruta, es 0 (a comprobar).
  - **En la 2.0 se repite:** `deepLinkHandled` es de cada controlador. Cada cambio de bóveda (crear, importar como nueva, borrar, elegir otra) crea uno nuevo, que vuelve a leer el mismo `activity.intent` y vuelve a saltar a la pantalla elegida por la otra app, mientras dure esa actividad.
  - No es nuevo: la 1.4.3 usaba el mismo `NavHost` y la misma versión de la biblioteca. Las auditorías anteriores dieron por hecho que la app no lee ningún *intent* (`docs/auditorias/2026-11-08-ataque-1.4.0.md:127-129`; `final-audit-c.md`, §6 «`Intent` y URI»), porque la app no llama a `getIntent`, pero la biblioteca sí.
- **Impacto:**
  - La otra app no lee nada: `FLAG_SECURE`, no hay resultado de vuelta, y la bóveda sigue bloqueada (al irse se bloquea, `MainActivity.kt:120`).
  - Solo abre pantallas a las que cualquiera llega tocando, y ninguna lanza un selector ni una acción sola al entrar (`LaunchedEffect(Unit)` con `.launch(` no sale en `ui/screens`).
  - Sirve para la ingeniería social. Por ejemplo, una app que dice «ayudar con las copias» abre Clavero directamente en «Bóvedas» o en «Ajustes» para guiar al usuario a «Importar copia» con un archivo suyo.
  - Con la 2.0, además, la navegación del usuario salta sola tras cada cambio de bóveda. Y si usa la `FLAG_ACTIVITY_CLEAR_TASK`, la otra app corta lo que el usuario estuviera escribiendo (eso ya lo permite cualquier actividad exportada).
- **Corrección** (`MainActivity.kt`, en `onCreate`, después de `super.onCreate` y antes de `setContent`):
  ```kotlin
  override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      // B-1: NavController.handleDeepLink reads the launching intent (its extras android-support-nav:controller:deepLinkIds,
      // …Args and …Extras) each time a NavHost is built, one per vault in 2.0. The app has no entry point but the
      // launcher: nothing from outside chooses a screen. On every onCreate: a re-created activity gets the original back.
      intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, MainActivity::class.java)
      // Before anything is drawn, for every screen, dialog and menu of the app.
      secureWindow()
      setContent { … }
  }
  ```
  - Con `import android.content.Intent`. Sin `onNewIntent` que llame a `setIntent`: hoy no lo hay, y así se queda.
  - El KDoc de `:26` pasa a decir que la app limpia el *intent* para que la navegación no lo lea.
  - No cambia nada para el usuario: la app no lee nada más del *intent* (`grep -rn "getIntent\|intent\.\(data\|extras\)\|onNewIntent" app/src/main/java` sale vacío).
- **Prueba de concepto propuesta:**
  1. **Test de JVM (rojo hoy):** `PlatformSourcesTest.theNavigationNeverSeesTheLaunchIntent`.
     - Lee `MainActivity.kt` y toma el cuerpo de `override fun onCreate`.
     - Comprueba que `intent = Intent(Intent.ACTION_MAIN)` aparece antes de `setContent {`.
     - Comprueba que el archivo no tiene `onNewIntent`.
  2. **En el móvil** (APK de depuración; README, «Pruebas de la 2.0»):
     ```
     adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
       -n io.github.egt17baa.seguridad/app.seguridad.MainActivity \
       --eia android-support-nav:controller:deepLinkIds 0,536627314
     ```
     - **Hoy:** Clavero se abre en «Generador» (`tools/generator`). Si no salta, probar con −1 como id del grafo.
     - Después, crear o elegir otra bóveda: vuelve a «Generador».
     - **Con el arreglo:** se abre en «Bóveda», y cambiar de bóveda no salta.

### [Informativa] B-3. Borrar una bóveda deja el archivo `shared_prefs/vault_biometric_<id>.xml`

- **Severidad:** Informativa: solo lo ve quien lea el almacenamiento privado de la app (root o un análisis forense), que está fuera del modelo.
- **CVSS 3.1:** 1.9, `CVSS:3.1/AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N`.
- **MASVS v2:** MASVS-STORAGE-2 y MASVS-PRIVACY-4.
- **CWE:** CWE-459 (limpieza incompleta).
- **Dónde:**
  - `android/BiometricVault.kt:117-118`: las preferencias de cada bóveda que no es la primera se llaman `vault_biometric_<id>` (`perVaultName`);
  - `android/BiometricVault.kt:188-195`: `clear` quita `KEY_IV`, `KEY_BLOB` y `KEY_IN_STRONGBOX` y borra la clave del Keystore, pero no el archivo;
  - `AppContainer.kt:747-751` (`forgetBiometric(id)` al borrar la bóveda) y `SeguridadApp.kt:41` (`BiometricVault.disable(this, id)`).
- **Qué pasa:**
  - `SharedPreferences` nunca borra su archivo al vaciarlo. Tras «Borrar esta bóveda», queda `vault_biometric_<id>.xml` con un mapa vacío y el id de la bóveda en el nombre: uno por cada bóveda borrada que tuvo la huella.
  - Si la bóveda nunca tuvo la huella, el `edit { remove(…) }` de `disable` probablemente crea el archivo vacío (el `writeToFile` de Android escribe si el archivo no existe): **a comprobar**.
  - Lo grave, la clave y la clave de datos envuelta que se quedan si el proceso muere a mitad, es A-1 (parte A). Esto es solo el rastro que queda incluso cuando el borrado termina bien.
- **Impacto:** revela que existió una bóveda con ese id y que se borró (los ids no se repiten, `VaultRegistry.kt:100-103`). El texto de la confirmación dice que se borra «su huella» (`vault/VaultsTexts.kt:48`); el archivo vacío no la contiene, así que el texto sigue siendo verdad.
- **Corrección** (`BiometricVault.kt`, una operación propia para el borrado, en la misma fila `KeyOpsQueue`):
  ```kotlin
  /** 2.0, B-3: [disable] for a vault being deleted: its preferences file goes too (the first vault's also holds the phone's StrongBox flags, so it stays). */
  suspend fun forget(context: Context, vaultId: String) {
      val app = context.applicationContext
      withContext(NonCancellable) {
          queue.run(KeyOp.DISABLE) {
              clear(app, vaultId)
              if (vaultId != PRIMARY_VAULT_ID) app.deleteSharedPreferences(perVaultName(PREFS, vaultId))
          }
      }
  }
  ```
  - `SeguridadApp.kt:41` pasa a usar `BiometricVault.forget(this, id)`. `deleteSharedPreferences` existe desde la API 24, y el `minSdk` es 26.
  - En `clear`, `commit()` en lugar de `apply()` antes de borrar el archivo, para que una escritura pendiente no lo vuelva a crear.
  - Va con el arreglo de A-1: olvidar la huella antes de borrar la carpeta, y el barrido de huérfanos.
- **Prueba de concepto propuesta:**
  1. **Test de JVM (rojo hoy):** `PlatformSourcesTest.deletingAVaultDeletesItsFingerprintPreferences`. Comprueba que `BiometricVault.kt` llama a `deleteSharedPreferences(perVaultName(PREFS, vaultId))`, salvo para `PRIMARY_VAULT_ID`, y que `SeguridadApp.kt` pasa `forget` como `forgetBiometric`.
  2. **En el móvil** (APK de depuración):
     1. Crear una segunda bóveda, activar su huella y borrarla.
     2. `adb shell run-as io.github.egt17baa.seguridad ls shared_prefs`.
     3. Hoy sale `vault_biometric_<id>.xml`; con el arreglo, no.
     4. Repetir con una bóveda que nunca tuvo la huella (la parte «a comprobar»).

### [Informativa, funcional y de veracidad] B-4. «Copiar contraseña» al volver a la app no sale nunca, y el README y las notas dicen que sí

- **Severidad:** Informativa. No es un fallo de seguridad: falla hacia el lado seguro (no se copia nada). Pero un texto publicado afirma algo que el código no hace (regla de veracidad), y la prueba del móvil de la fila 12 va a fallar.
- **CVSS 3.1:** no aplica. **MASVS v2:** no aplica. **CWE:** no aplica.
- **Dónde:**
  - `MainActivity.kt:117`: `onStop` llama a `twoStepCopy.markLeft()`, y en `:120`, con la misma condición, `vault.lock(LockReason.LEFT_APP)`, sea cual sea el «Bloquear la bóveda tras no usarla» elegido;
  - `vault/VaultStore.kt:816-817`: `lock` sube siempre `epoch`. `AppContainer.kt:540` lo suma a `lockEpoch`, y `AppContainer.kt:932-936` además borra la oferta (`offer.clear()`) en cuanto la bóveda deja de estar abierta;
  - `tools/TwoStepCopy.kt:78-87`: `live()` tira la oferta si `o.epoch != lockEpoch()`. Y `:54-59`: `takeReturnOffer()` necesita `left`, que solo pone `markLeft()` (`:44-48`);
  - `ui/screens/EntryRow.kt:365-378` (`PasswordOfferOnReturn`) solo enseña la oferta si `takeReturnOffer()` da un id;
  - el comentario `MainActivity.kt:115-116` dice «with «Al salir» the lock ends the offer anyway», como si solo «Al salir» bloqueara al irse.
- **Qué pasa:** en ningún camino real llega un id a la pantalla.
  - **Saliendo de la app** (Inicio, otra app, apagar la pantalla): `markLeft` y justo después el bloqueo, en el mismo `onStop`. Al volver, la oferta ya no existe.
  - **En pantalla dividida o en una ventana flotante:** no hay `onStop`, así que no hay `markLeft`, y `left` sigue en falso.
  - **Con un selector de Clavero encima** (`VaultLock.keepOpenOnce`): tampoco se llama a `markLeft`.
  - Los tests unitarios lo dan por bueno porque llaman a `markLeft()` sin el bloqueo que siempre la sigue en la app (`tools/TwoStepCopyTest.kt:101-122`, `redteam/RedTeam20TextsTest.kt:107-115`). Y la regla «un bloqueo termina la oferta aunque la bóveda se vuelva a abrir» está fijada a propósito por el ataque (`RedTeam20TextsTest.kt:117-122`).
- **Impacto:**
  - Ninguno en la seguridad.
  - `README.md:24` («tras «Copiar usuario», al volver a la app ofrece «Copiar contraseña» durante 30 segundos») y las notas de la versión (`docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md:28`) prometen algo que no ocurre.
  - La prueba `README.md:407` (fila 12 de `pendientes.md`) va a fallar en el móvil.
- **Corrección** (decisión del director; las tres son seguras):
  - **(a) Textos verdaderos** (lo mínimo para la 2.0): quitar la frase de `README.md:24` y de las notas, cambiar la prueba de `README.md:407` y corregir el comentario `MainActivity.kt:115-116` («leaving the app always locks, which ends the offer»).
  - **(c) Que funcione en pantalla dividida**, donde nada bloquea: marcar también la salida en `AppContainer.leftForAnotherApp()` (`AppContainer.kt:220-224`), que es el flujo real de «copio el usuario y lo pego en la otra mitad». Sigue sin guardar nada secreto: solo el id.
  - **(b) Que la oferta sobreviva al bloqueo de «Al salir» de la misma bóveda:** `TwoStepCopy` guarda el id de la bóveda y acepta un cambio de época solo si fue un `LEFT_APP` de esa bóveda y se vuelve a abrir antes de los 30 s.
    - Para copiar hay que desbloquear con la maestra o la huella, así que quien llegue no obtiene nada que no tuviera ya.
    - Pero cambia una regla que el ataque fijó: solo con un Ruling.
  - Recomiendo (a) ya y (c) si se quiere conservar la función.
- **Prueba de concepto propuesta (roja hoy):** `AppContainerTwoStepTest.theOfferIsShownWhenComingBack`, en `app/src/test/java/app/seguridad/`, con los ayudantes de `AppContainerVaultsTest`.
  1. Crear y abrir una bóveda con un acceso (usuario y contraseña), con un reloj de prueba.
  2. `c.twoStepCopy.offerPassword(id)`.
  3. Los pasos exactos de `MainActivity.onStop` (`:117-120`): `c.twoStepCopy.markLeft()`, `c.leftApp(false)` y `c.vault.lock(LockReason.LEFT_APP)`.
  4. Avanzar el reloj 5 s y volver a abrir la bóveda con su maestra.
  5. `assertEquals(id, c.twoStepCopy.takeReturnOffer())`. Hoy da `null`.

  Con (a), la prueba pasa a ser de textos: un test como los de `FinalLegalReviewTest` que falle si `README.md` o los `*-notas.md` dicen «al volver a la app ofrece «Copiar contraseña»». Con (c), una variante que en el paso 3 llama a `c.leftForAnotherApp()` y `c.backFromAnotherApp()`.

## Evidencia del APK (2.0 frente a 1.4.3)

| Comprobación | Resultado |
|---|---|
| `aapt2 dump permissions` | **Iguales byte a byte**: `INTERNET`, `CAMERA`, `USE_BIOMETRIC`, `USE_FINGERPRINT` y `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (la de AndroidX, con `protectionLevel` *signature*). Son los `EXPECTED_PERMISSIONS` de `scripts/check-manifest.sh` |
| `aapt2 dump badging` | Igual salvo `versionName='2.0.0'` (antes `'1.4.3'`). `minSdkVersion` 26, `targetSdkVersion` 36, `application-debuggable` (es el APK de depuración), las mismas `uses-feature` (todas `not-required`), el mismo icono (`res/mipmap-anydpi-v26/ic_launcher.xml`) y la misma `launchable-activity` |
| `aapt2 dump xmltree --file AndroidManifest.xml` | Igual salvo `versionName`. Exportadas: `MainActivity` (`MAIN`/`LAUNCHER`), `ProfileInstallReceiver` (con `android.permission.DUMP`) y `PreviewActivity` (solo depuración: `ui-tooling` es `debugImplementation`, `app/build.gradle.kts:105`). No exportados: `SecureCaptureActivity`, el `CaptureActivity` de la biblioteca (sin filtro), el `FileProvider` (`grantUriPermissions`) y `InitializationProvider`. `allowBackup=false`, con `fullBackupContent` y `dataExtractionRules` |
| `scripts/check-manifest.sh` sobre los dos APK | `OK` en los dos (con el aviso de `PreviewActivity` por ser de depuración). En la CI corre con `--release` sobre el de *release* |
| `res/xml/` compilados | `backup_rules.xml`, `data_extraction_rules.xml` y `share_paths.xml`: **iguales** (`aapt2 dump xmltree` de cada uno) |
| Archivos del APK | 560 frente a 556. Los cuatro nuevos son los dibujos del logo (`res/drawable/$ic_launcher_background__0.xml` y tres `$ic_launcher_foreground__*.xml`). `assets/`, `lib/` y `META-INF/`, **idénticos** (`diff -r`); el mismo número de `classes*.dex` (18) |
| `dexdump`, clases `app.seguridad.*` | Ninguna llamada a `android.util.Log`, `PrintStream.println`, `printStackTrace`, `Toast`, `NotificationManager`, `Vibrator` ni `WebView`. `setPrimaryClip` y `getPrimaryClip`, solo en `android/ClipboardKt` |
| API de Android que llama la app, `dexdump` (métodos `Landroid…` invocados desde `app.seguridad.*`) | 561 en la 1.4.3 y 719 en la 2.0. Del *framework*, solo dos nuevas: `ValueAnimator.areAnimatorsEnabled` (el «quitar animaciones» del sistema) y `Uri.hashCode`. Las demás son de Compose y de AndroidX (dibujo, gestos, semántica, `RenderEffect` y `HapticFeedback`). Ninguna de red, archivos, `java.net`, reflexión ni `ObjectInputStream` |

## MASVS v2, control por control

| Control | Estado en la 2.0 | Evidencia |
|---|---|---|
| STORAGE-1 (guardar los datos sensibles de forma segura) | Cumple | Cada bóveda, cifrada en su archivo privado (parte A); `vaults.json` sin nombres (`vault/VaultRegistry.kt:38-42`, `:59-60`); `settings.json` sin secretos y con errores de texto fijo (`vault/AutoBackup.kt:1070-1076`); la clave de la huella, en el Keystore (`android/BiometricVault.kt:133-147`) |
| STORAGE-2 (evitar fugas) | Cumple, con B-3 | Copia del sistema apagada en todas sus vías; portapapeles sensible y con borrado; sin registros; nada secreto en estado guardado ni en argumentos de navegación; teclado sin aprendizaje (§«Lo que está bien») |
| CRYPTO-1 y CRYPTO-2 | Parte A (A-1 a A-3) | Desde la plataforma: AES-256-GCM en el Keystore, con autenticación por uso y un alias por bóveda; `SecureRandom` para los ids (`crypto/Crypto.kt:234`, `vault/VaultRegistry.kt:102`) |
| AUTH-1 | No aplica | Sin servidor ni sesiones |
| AUTH-2 (autenticación local) | Cumple | `BiometricPrompt` con `CryptoObject` y solo `BIOMETRIC_STRONG` (`BiometricVault.kt:220`, `:237`); la clave pide autenticación, por uso desde Android 11 y se invalida con una huella nueva (`:138-141`); una sola bóveda abierta, y cambiar bloquea antes la que se deja (`AppContainer.kt:554-585`); bloqueo al irse (`MainActivity.kt:120`) y por inactividad en toda la app (`ui/AppNavHost.kt:185`, `ui/screens/VaultScreen.kt:129-136`) |
| AUTH-3 (más autenticación para lo sensible) | Cumple | Borrar una bóveda pide su maestra y su nombre, sin abrirla (CR-7; `AppContainer.kt:715-735`). Exportar no pide la maestra otra vez: la copia va cifrada con ella (decisión de la 1.x) |
| NETWORK-1 | Cumple | La 2.0 no toca `net/` (`git diff a3ac942..HEAD -- app/src/main/java/app/seguridad/net` sale vacío): HTTPS a una URL fija, sin redirecciones y con el bloqueo de eMudhra. C-6 (sin `network_security_config`) es endurecimiento |
| NETWORK-2 (*pinning*) | No aplica | No hay servidor propio. Lo de eMudhra es una lista de desconfianza, no *pinning* |
| PLATFORM-1 (IPC) | **B-1**; B-2 a comprobar | Un solo componente útil exportado; ningún `PendingIntent`, servicio ni *broadcast* propios; `FileProvider` sin exportar (`AndroidManifest.xml:41-49`) |
| PLATFORM-2 (WebView) | No aplica | No hay `WebView`, `Linkify` ni `Html.fromHtml`; las notas con formato no tienen enlaces (`vault/LimitedMarkup.kt`) |
| PLATFORM-3 (interfaz) | Cumple; B-2 a comprobar | `FLAG_SECURE`, filtro de toques ocultos, exclusión de *autofill* y marca de accesibilidad, en la ventana y en cada diálogo y menú (`android/SecureWindow.kt:23-51`; `ui/theme/Components.kt:241-299`); secretos enmascarados hasta «Ver»; «Ver en grande» y el QR del wifi, solo tras una acción y cerrados al bloquear |
| CODE-1 (plataforma al día) | Parcial (decisión) | `minSdk` 26 (Android 8.0, sin parches). El aviso A14 cubre el portapapeles de 8 a 12. No cambia en la 2.0 |
| CODE-2 (forzar actualizaciones) | No aplica | Sin servidor; la app llega por Play y por *Releases* |
| CODE-3 (componentes sin vulnerabilidades conocidas) | Sin cambios desde la 1.4.3 | Ninguna dependencia nueva ni cambiada (parte C). En esta parte no se pasó un escáner de CVE |
| CODE-4 (validar las entradas) | Cumple, con B-1 | Las entradas hostiles del ataque RT-21 a RT-29 (copias, QR del wifi, notas, búsquedas); los ids en las rutas, con `Uri.encode` (`ui/AppNavHost.kt:221-226`, `:375-383`); B-1 es la única entrada no validada |
| RESILIENCE-1 a 4 | No aplica (decisión) | Sin detección de root, antidepuración ni comprobación de integridad: perfil L2 sin R. R8 ofusca y reduce la *release* (`app/build.gradle.kts:59-62`), con reglas que solo conservan los serializadores (`app/proguard-rules.pro`) |
| PRIVACY-1 (minimizar) | Cumple | Ningún permiso ni dato que salga nuevos; «Comprobar filtraciones» solo manda las contraseñas de los accesos, sin la papelera (`health/Health.kt:32-34`); ninguna misión conecta (`health/Missions.kt:55`) |
| PRIVACY-2 (no identificar) | Cumple | Solo el `User-Agent` `Clavero/<versión>` y 5 caracteres del SHA-1 a HIBP (sin cambios) |
| PRIVACY-3 (transparencia) | Cumple, con B-4 | La política dice lo que queda sin cifrar en el móvil (`docs/privacidad.html:33-40`); B-4 es un texto del README y de las notas |
| PRIVACY-4 (control) | Cumple, con B-3 | Borrar una bóveda, la papelera, «Borrar historial», «He olvidado la contraseña maestra» |

## Riesgos con nombre (no se reabren)

- Las filas 51, 62, 67 y 69 de `pendientes.md` son de memoria y de tiempo (la falta de memoria en un móvil pequeño, la densidad del JSON compacto, la lectura de una copia que no se corta y el guardado de «Importar de otra app» sin capturar `OutOfMemoryError`).
- Ninguno de mis hallazgos las toca, y no traigo pruebas nuevas sobre ellas.
- C-3 (los secretos de firma, Media, aceptado por el dueño) es de la parte C. Lo menciono solo porque una firma robada daría un APK que hereda la bóveda: B-1 y B-2 no lo empeoran.

## Notas para la consolidación (pruebas en el móvil)

- **B-1:** la orden `adb` de su prueba, con el APK de depuración; después, cambiar de bóveda y ver si vuelve a saltar.
- **B-2:** la app de prueba de la afinidad, en Android 8 o 10 y en uno reciente.
- **B-3:** `adb shell run-as io.github.egt17baa.seguridad ls shared_prefs`, tras borrar una bóveda con huella y otra sin ella.
- **B-4:** la prueba de la fila 12 (`README.md:407`) va a fallar tal como está escrita. Conviene decidir B-4 antes de la tarea 11.
- **El APK de *release*:** comparé los de depuración. La igualdad de permisos y componentes de la *release* la comprueba la CI (`check-manifest.sh --release`, con la lista de permisos de la 1.2.2); conviene apuntar en §9 de la consolidada el resultado de esa ejecución.

## Lo que está bien

### STORAGE
- **Lo que queda fuera de las bóvedas no tiene secretos:**
  - `vaults.json` solo guarda el id, el color, el orden y la última abierta (`vault/VaultRegistry.kt:38-42`), y `dirOf` solo acepta `principal` o 32 hex (`:90-94`);
  - `settings.json`: los campos de `Settings`, `VaultSettings` y `BackupTargetSettings` son ajustes, marcas y URI de carpetas, y `lastError` solo recibe textos fijos (`vault/AutoBackup.kt:1070-1076`).
- **La copia del sistema:** `allowBackup=false`, y `backup_rules.xml` y `data_extraction_rules.xml` excluyen `root`, `file`, `database`, `sharedpref` y `external`, en la nube y entre dispositivos. Cubren los archivos nuevos (`files/vaults.json`, `files/vaults/<id>/`, `shared_prefs/vault_biometric_<id>.xml`). Los XML compilados son iguales a los de la 1.4.3.
- **El teclado:**
  - ningún campo aprende: `NoLearningInput` añade `IME_FLAG_NO_PERSONALIZED_LEARNING` a todos, también en diálogos (`ui/NoLearningInput.kt:18-33`, `MainActivity.kt:45`);
  - los secretos de las plantillas van en `PasswordField` (el número de la tarjeta, la clave de la licencia, el número del pasaporte y la contraseña del wifi) o en `SecretNumberField` (CVV y PIN, con `NumberPassword`) (`ui/screens/EntryFields.kt:240-254`, `:308-311`);
  - los campos ocultos propios van en `PasswordWithTools`, y por tanto en `PasswordField` (`:336`);
  - el editor entero está dentro de `NoCopy` (`ui/screens/VaultEntryScreen.kt:657`), también las notas y la frase semilla, y `PasswordField` lleva el suyo (`ui/theme/Components.kt:401-414`).
- **El portapapeles:**
  - todas las copias pasan por `copySensitive` o `copySensitiveFor` (búsqueda en `ui/` y `tools/`), con `EXTRA_IS_SENSITIVE` desde Android 13 (`android/Clipboard.kt:83-85`) y el borrado a su tiempo solo si sigue lo copiado (`:86-98`);
  - el 2FA de un toque calcula el código al escribirlo, también tras el aviso A14 (`ui/screens/VaultList.kt:492`, `tools/ClipboardNotice.kt:40-58`);
  - el aviso A14 se pierde con cualquier bloqueo o cambio de bóveda (`AppContainer.kt:572`, `tools/ClipboardNotice.kt:101-107`);
  - la copia en dos pasos solo guarda el id (`tools/TwoStepCopy.kt:13-22`);
  - el portapapeles solo se lee con «Pegar» (`VaultEntryScreen.kt:710`, `ContactAddScreen.kt:141`, `PkActionsScreen.kt:781`), y «reconocer lo pegado» mira el texto del campo, nunca el portapapeles (Ruling T7 D-1).
- **Estado guardado y navegación:**
  - los `rememberSaveable` nuevos no guardan secretos: la URI de una carpeta (`SettingsScreen.kt:305`), los tonos de «Personalizado» (`AppearanceScreen.kt:139-141`) y la página de la guía (`OnboardingScreen.kt:47`);
  - «Ver en grande» y el QR del wifi leen el secreto del estado de la bóveda en un `ViewModel` y se cierran al bloquear (`BigPasswordScreen.kt:109-121`, `WifiQrScreen.kt:108-186`); el QR se tira también en `ON_STOP` (`:204`);
  - las rutas solo llevan ids (`AppNavHost.kt:221-226`, `:377-396`).
- **Al cambiar de bóveda o bloquear:**
  - los `ViewModel` de la bóveda que se deja se borran (`AppNavHost.kt:160-175`);
  - se vacían `QrHandoff` y los descifrados temporales (`SeguridadApp.kt:58-62`) y los lugares de los selectores que solo viven en memoria (`AppContainer.kt:942`);
  - un permiso de carpeta se devuelve cuando ninguna bóveda la usa (`AppContainer.kt:863-865`), y solo se escribe en carpetas con permiso persistente (`android/SafBackupTarget.kt:125-129`).
- **Registros:** ninguno en el código de la app (`dexdump`, arriba). Ningún `toString` de los tipos con secretos nuevos los enseña (`tools/qr/WifiQr.kt:37`, `:63`, `:71`; `tools/qr/QrContent.kt:43`; `vault/EntryForm.kt:124`, `:145`).

### AUTH
- La clave de la huella tiene los mismos parámetros que en la 1.4.3; solo cambia el alias, `seguridad_vault_biometric` para la primera y `_<id>` para las demás (`BiometricVault.kt:123-147`; `git diff a3ac942..HEAD` de ese archivo).
- Una bóveda abierta a la vez. `switchTo` cierra la puerta de la vieja antes de bloquearla y espera a que termine lo que estaba haciendo (`AppContainer.kt:564-575`).
- Las pantallas nuevas con secretos llaman a `VaultIdleLock` (`BigPasswordScreen`, `WifiQrScreen`, `EntryHistoryScreen`, `TrashScreen`, `VaultPickerScreen`), y el reloj de inactividad corre en toda la app.

### NETWORK
- La 2.0 no cambia la red. Las misiones no conectan, y «Comprobar filtraciones» solo manda las contraseñas de los accesos vivos (`health/Health.kt:32-34`; Ruling T5 duda 1).

### PLATFORM
- El manifiesto es igual al de la 1.4.3 (salvo `versionName`), y el escáner es siempre `SecureCaptureActivity` (`android/QrScanner.kt:20-24`).
- **Cada ventana de diálogo o de menú:**
  - pasa por los envoltorios que ponen `FilterObscuredTouches()` (`ui/theme/Components.kt:241-268` y `:277-299`);
  - lo comprueban dos tests, uno con ventanas anidadas (`PlatformSourcesTest.kt:555-576`, `redteam/RedTeam20SweepTest.kt:120-151`);
  - ninguna usa `SecureFlagPolicy` ni otro tipo de ventana.
- **Ninguna API nueva de riesgo:** la vibración, con `performHapticFeedback` y sin `VIBRATE`; el QR del wifi no se conecta a ninguna red ni abre otra app; el color dinámico no pide permisos.

### CODE y RESILIENCE
- Las reglas de R8 son genéricas (`app.seguridad.**`), así que cubren las clases `@Serializable` de la 2.0. Ninguna nueva desde la revisión de compilación (`git diff 6d3b884..HEAD -- app/src/main | grep "@Serializable"` sale vacío).
- `debuggable` solo en depuración, y `check-manifest.sh --release` en la CI.

### PRIVACY
- Antes de abrir, solo «Bóveda N» y su color. La política dice lo que queda sin cifrar, carpeta a carpeta y bóveda a bóveda (`docs/privacidad.html:33-40`).

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** apliqué el de la decisión (f), más el aislamiento entre bóvedas y la subida desde la 1.4.3.
  - Lo nuevo de esta parte son dos caminos de «otra app» que ninguna auditoría anterior miró:
    - los *extras* de navegación (B-1), con evidencia en el código de la biblioteca;
    - la afinidad de tarea (B-2), a comprobar.
  - Ninguno da datos sin la ayuda del usuario. Los gradúo por eso: B-1 Baja; B-2 Media solo si se confirma, porque su objetivo es la maestra.
- **Criptografía de la plataforma** (lo demás, parte A):
  - la clave de la huella es AES-256-GCM en el Keystore, con autenticación por uso (`AUTH_BIOMETRIC_STRONG`, validez 0 desde Android 11) e invalidación con una huella nueva (`android/BiometricVault.kt:133-147`), y no cambia;
  - un alias por bóveda, y cada clave solo envuelve la clave de datos de la suya;
  - los ids nuevos salen de `SecureRandom` (`crypto/Crypto.kt:234`; `UUID.randomUUID`);
  - el rastro del borrado (B-3) no tiene material de claves. El que sí lo tiene es A-1.
- **Secretos fuera de la bóveda:** ninguno en `settings.json`, `vaults.json`, las rutas, el estado guardado ni los registros. En el portapapeles, siempre con la marca de sensible y el borrado (§«Lo que está bien»).
- **Red:** sin cambios. El *pinning* no aplica (servidor ajeno), y la desconfianza de eMudhra sigue en dos pasos (CRY-B01).
- **Riesgos con nombre:** no reabro las filas 51, 62, 67 y 69, y A-1 y C-3 quedan en sus partes.
- **Proporción:** nada Crítico ni Alto. Antes de publicar, B-1 tiene un arreglo de una línea, sin cambio de formato ni de manifiesto, y su prueba de fuente. B-2 pide decidir si se cambia el manifiesto, que la puerta compara con la 1.4.3.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:**
  - `onStop` hace, en este orden, `clipboardNotice.drop()`, `twoStepCopy.markLeft()`, `leftApp` y `lock(LEFT_APP)` (`MainActivity.kt:114-120`). Por ese orden, la oferta de la copia en dos pasos muere siempre en el mismo `onStop` que la marca (B-4).
  - `configChanges` evita recrear la actividad al girar (`AndroidManifest.xml:23`); un cambio de tema sí la recrea, sin bloquear (`isChangingConfigurations`).
  - La pantalla dividida se trata con `onTopResumedActivityChanged` (`MainActivity.kt:76-97`).
- **Compose y navegación:**
  - `key(vault)` reconstruye la navegación y su almacén de `ViewModel` por bóveda (`ui/AppNavHost.kt:160-175`, `:202-204`): bien para el aislamiento, pero cada `NavController` nuevo vuelve a leer el *intent* de arranque (B-1).
  - `rememberNavController` guarda la pila en el estado de la actividad (solo rutas con ids). Tras la muerte del proceso, la pila se restaura con la bóveda bloqueada, y las pantallas con secretos se cierran solas (`BigPasswordScreen.kt:131`, `WifiQrScreen.kt:200-203`).
- **Ventanas:**
  - los diálogos de Compose heredan `FLAG_SECURE` (`SecureFlagPolicy.Inherit` por defecto, sin ningún `SecureOff`), y el resto lo pone `FilterObscuredTouches()`;
  - el difuminado de «Cristal» es un `RenderEffect` sobre lo que dibuja la propia app (`GraphicsLayer.setRenderEffect` en `dexdump`), sin `FLAG_BLUR_BEHIND` ni `setBackgroundBlurRadius` (búsqueda vacía en `ui/`).
- **Niveles de API:**
  - `EXTRA_IS_SENSITIVE` solo desde la 33, con el aviso A14 de la 26 a la 32 (`android/Clipboard.kt:83-85`, `tools/ClipboardNotice.kt:11`);
  - `setAccessibilityDataSensitive`, desde la 34 (`android/SecureWindow.kt:31-35`);
  - `deleteSharedPreferences` (el arreglo de B-3) existe desde la 24.
- **Interfaz:** «Ver en grande» parte los caracteres sin juntar pares sustitutos y marca los invisibles (`BigPasswordScreen.kt:66-88`). El QR del wifi se dibuja fuera del hilo principal, y una falta de memoria da un texto, no un cierre (`WifiQrScreen.kt:140-162`).
- **R8:** sin `-keep` generales ni `-dontobfuscate` (`app/proguard-rules.pro:1-11`). No hay `@Serializable` nuevos desde la revisión de compilación, que comprobó en el APK de *release* que se conservan las 21 clases y los `enum`.
- **Rendimiento:** no lo medí (sin móvil). Las filas 3, 51, 55 y 58 siguen para la tarea 11 y el dueño.
- **B-4 en la tarea 11:** la fila 12 de `pendientes.md` incluye «oferta al volver de otra app». Si no se decide B-4 antes, esa prueba fallará y hará perder un ciclo en el móvil.

## Textos para el Abogado Digital

- **B-4 (veracidad):**
  - `README.md:24`: «tras «Copiar usuario», al volver a la app ofrece «Copiar contraseña» durante 30 segundos». Con el código de hoy no ocurre;
  - `docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md:28`: «tras «Copiar usuario», al volver ofrece «Copiar contraseña»», lo mismo;
  - `README.md:407`: la prueba de la fila 12 con esa promesa;
  - según lo que decida el director: quitar la frase (a), o escribir lo que haga (c) o (b) («en pantalla dividida…», «tras volver a abrir la bóveda…»).
- **B-3 y A-1:** `vault/VaultsTexts.kt:48` («Se borra «$name» de este móvil: su archivo, sus adjuntos, su huella y sus ajustes»). Con el arreglo de A-1 y el de B-3 es verdad del todo. Sin el de A-1, deja de serlo si el proceso muere a mitad del borrado (lo explica la parte A).
- **B-1:** solo un comentario del código (`MainActivity.kt:26`, «takes no extras»), que no se publica. Si se arregla, ningún texto publicado cambia. La consolidada no debería repetir que la app «no lee ningún *intent*» sin matizar que la biblioteca de navegación lee los *extras* del de arranque (hasta el arreglo).
- **B-2:** ningún texto. Si se cambia el manifiesto, la puerta del APK y el README («Entorno de firma», la comparación con la 1.4.3) tendrían que decir que es la única diferencia aceptada.
- **Sin textos nuevos de la interfaz, la política ni la ficha** en esta parte.

Modelo: el director (esfuerzo extra).
