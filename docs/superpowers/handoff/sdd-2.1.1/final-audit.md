# Clavero 2.1.1: auditoría completa (revisión final)

Fecha: 25-11-2026. Rama `ccr-0aa27f95-u038cd`, HEAD `da4edfd`. El código es el de `272c349`: después solo cambian `sdd-2.1.1/progress.md` y `sdd-2.1.1/security-review.md`. Punto 6 de `CLAUDE.md`, después de la revisión de la rama del director (`branch-review.md`), del `code-review` y del `security-review` (`security-review.md`). Auditor: el auditor.

## 1. Alcance y método

**Alcance.** Lo que cambia la 2.1.1 frente a `a29d88c` (la 2.1 fusionada): `git diff a29d88c..HEAD` (39 archivos, +3 433/−334). En `app/src/main`, 10 archivos:

- La barra de abajo flotante, en cápsula, en «Degradado», «Cristal», «Vidrio» y «Transparente»: `ui/theme/FloatingBar.kt` (nuevo), `ui/AppNavHost.kt`, `ui/theme/Components.kt`, `ui/theme/Glass.kt`, `ui/theme/Palettes.kt`. «Sólido» sigue con la barra de Material de la 2.1.
- Las filtradas de «Salud» en memoria hasta el bloqueo: `health/LeakResults.kt` (nuevo), `AppContainer.kt`, `ui/screens/HealthScreen.kt`.
- Textos: `ui/screens/AppearanceScreen.kt` (`STYLE_MORE`), `LEAK_RESULT_KEPT` y `LEAK_RECHECK_NEEDED`.
- `versionName` 2.1.1 (`app/build.gradle.kts:35`).

Y los documentos publicados: `docs/privacidad.html`, `docs/ficha-google-play.md`, `README.md` y `docs/superpowers/handoff/2026-11-24-clavero-2.1.1-notas.md`.

**Fuera del alcance.** El código que no cambia, salvo para seguir un flujo (`net/`, `vault/EntryHistory.kt`, `imports/MergePlan.kt`, `android/SecureWindow.kt`). La CI. Las pruebas en el móvil (las hace el dueño).

**Modelo de amenaza.** Quien tiene el móvil desbloqueado después de que la bóveda se bloquee. Otra app en el móvil (también una que dibuja encima, *tapjacking*). Quien mira la pantalla. La red hasta Have I Been Pwned. Una copia importada encima de la bóveda abierta. La muerte del proceso. El bloqueo, el cambio de bóveda y la importación a mitad de una comprobación.

**Método.**

- Skills, por este orden: `experto-seguridad-cripto` (`SKILL.md` y su lista de auditoría), `ingeniero-movil-senior:auditoria-seguridad-movil` (MASVS v2, grupo a grupo), `auditoria-seguridad:auditoria-seguridad` e `ingeniero-movil-senior:desarrollo-movil-senior` (ciclo de vida, concurrencia y Compose).
- Lectura entera del diff de `app/src/main` y de los flujos que toca: `LeakResults.start` hasta `PwnedPasswords.times`, `lastPasswordChange` y `withVersion`, `beforeReplace` de «Importar copia», `lockEpoch` y `switchTo`, `Modifier.glass` y `swallowTaps`.
- Lectura de los tests nuevos y cambiados, y de las revisiones anteriores (`branch-review.md`, `security-review.md`, `progress.md`) y de los dos dictámenes del Abogado.
- Búsquedas en `app/src/main`: `WallpaperManager`, `windowIsTranslucent`, `FLAG_SECURE`, `filterTouchesWhenObscured`, `pointerInput`, `Log.`, `println`, `http`, `rememberSaveable`, `updatedAt =`, `deletedAt =`, `timeout`.
- `git diff --stat a29d88c..HEAD -- app/src/main/AndroidManifest.xml app/src/main/res gradle/`: vacío.
- Tests ejecutados aquí, en HEAD: `:app:testDebugUnitTest` de `LeakResultsTest`, `AppContainerTest`, `BottomBarSpaceTest`, `FloatingBarTest`, `CapsuleColorsTest`, `PublishedTextsTest`, `AppearanceTextsTest`, `RedTeam20Recheck2Test`, `RedTeam20Recheck3Test`, `PlatformSourcesTest` y `AppNameTest` (resultado en §6). La última compilación completa en verde es `272c349` (2 734 tests, `jvm-check` 2 611, 0 fallos).

## 2. Resumen

| ID | Severidad | Hallazgo | Dónde |
|---|---|---|---|
| F-1 | Info | La política dice cuándo se olvida el resultado de «¿Filtrada?» solo en parte: también se olvida por la regla de «PIN y códigos» (al pulsar «Bloquear», al abrir otra bóveda…), pero no con cada bloqueo (corregido por AD-2 del director) | `docs/privacidad.html:60`; `tools/codes/ScreenAway.kt:79-83` |
| F-2 | Info (arreglado en `1b04ebe`) | La política y `LEAK_RESULT_KEPT` no dicen que «Importar copia» también olvida el resultado de «Salud» | `docs/privacidad.html:60`; `ui/screens/HealthScreen.kt:54`; `AppContainer.kt:434-436` |
| F-3 | Info | Los toques que se tragan en la cápsula y el sitio del contenido solo los fijan tests de fuente y de funciones puras (D-1); el comportamiento real se ve en el móvil | `ui/theme/FloatingBarTest.kt:167-179`; `ui/BottomBarSpaceTest.kt:53-177` |

Ningún hallazgo Crítico, Alto, Medio ni Bajo. Los tres son informativos: ninguno es un fallo de seguridad ni hace falso un texto. Los dos primeros van al Abogado como precisiones opcionales. El tercero es un límite de método ya decidido (D-1) que cubren las casillas del README.

## 3. Hallazgos

### F-1 [Info] La frase de «¿Filtrada?» en la política se queda corta

- **Dónde:** `docs/privacidad.html:60`: «El de «¿Filtrada?» se olvida al volver atrás desde esa pantalla o cuando Android cierra la app.»
- **Qué pasa (corregido por AD-2 de la aprobación del director):** además de al volver atrás y con la muerte del proceso, el resultado de «¿Filtrada?» se olvida por la regla de «PIN y códigos»: entre otros casos, al pulsar «Bloquear», al abrir otra bóveda y al volver a esa pantalla tras `max(bloqueo, 60 s)` fuera. No se olvida con el bloqueo al salir de la app ni con el de inactividad si la pantalla se usó hace poco (`tools/codes/ScreenAway.kt:79-83`).
- **Impacto:** ninguno de seguridad. La frase dice «como mucho hasta»; la app olvida antes, no después. No hace falso el texto, pero no lo dice todo.
- **Arreglo propuesto:** ninguno: la frase es cierta. Si el Abogado quiere nombrar más casos, solo el texto comprobado de la aprobación del director: «El de «¿Filtrada?» se olvida al volver atrás desde esa pantalla, al pulsar «Bloquear», al abrir otra bóveda o cuando Android cierra la app.» «Al bloquear la bóveda», sin más, sería falso.
- **Test:** si se cambia, `PublishedTextsTest` puede fijar la frase nueva, como fija las demás de la 2.1.1.

### F-2 [Info] «Importar copia» también olvida las filtradas, y los textos no lo dicen

- **Dónde:** `docs/privacidad.html:60` («lo olvida cuando la bóveda se bloquea, cuando abres otra o cuando Android cierra la app»); `LEAK_RESULT_KEPT` (`ui/screens/HealthScreen.kt:54`); README `:27`.
- **Qué pasa:** desde BR-1, `beforeReplace` llama a `leakResults.forget()` (`AppContainer.kt:434-436`) y la `generation` del almacén entra en `isNow` y `stillOpen` (`health/LeakResults.kt:89`, `:147`). Importar una copia encima de la bóveda abierta olvida el resultado y corta la comprobación. Los textos no nombran ese caso.
- **Impacto:** ninguno. Es otro olvido antes de lo prometido. El director ya lo apuntó (repaso de BR-1, `branch-review.md:219`): los textos siguen siendo ciertos porque dicen «hasta cuándo, como mucho».
- **Arreglo propuesto (para el Abogado, opcional):** en la política, «… cuando la bóveda se bloquea, cuando abres otra, cuando la sustituyes con una copia o cuando Android cierra la app». `LEAK_RESULT_KEPT` puede quedarse como está (es la línea corta de la pantalla).
- **Test:** `PublishedTextsTest`, si cambia la política. El comportamiento ya lo prueban `AppContainerTest` («importing a copy over the open vault forgets the leaks of Salud and stops the check») y `LeakResultsTest.aReplacedVaultStopsTheRunningCheck`.

### F-3 [Info] Los toques y el sitio del contenido no tienen un test que los ejecute

- **Dónde:** `FloatingBarTest.aTapOnTheCapsuleNeverReachesWhatIsUnder` (`app/src/test/java/app/seguridad/ui/theme/FloatingBarTest.kt:167-179`) busca en el código el orden de `.pointerInput(Unit) { swallowTaps() }` y de los márgenes. `BottomBarSpaceTest` (`app/src/test/java/app/seguridad/ui/BottomBarSpaceTest.kt`) calcula con `navHostPadding` y un modelo de la pantalla.
- **Qué pasa:** por D-1 (dependencias congeladas, sin ui-test ni Robolectric), ningún test compone la cápsula ni le manda un toque. Lo que comprueban los tests es que el código tiene la forma que da ese comportamiento. La forma es correcta: `swallowTaps` va antes de `windowInsetsPadding` y del `padding` de los márgenes, así que la franja entera, márgenes incluidos, recibe el toque (`ui/theme/FloatingBar.kt:246-250`); consume solo los `down`, en el pase `Main`, después de los hijos (`:212-219`), y no hay `sharePointerInputWithSiblings`. La cápsula es hermana del `NavHost` en un `Box` y va encima (`ui/AppNavHost.kt:386-547`); en Compose, el toque va solo al hermano de arriba.
- **Impacto:** ninguno de seguridad. Detrás de la cápsula solo está el fondo de la app: el `NavHost` acaba encima (`navHostPadding`, `ui/AppNavHost.kt:606-616`). El filtro de *tapjacking* sigue en la ventana (`android/SecureWindow.kt:25`, sin cambios).
- **Arreglo propuesto:** ninguno en código. Ya están las casillas del README «nada debajo de la cápsula», «toques en el borde» y «cambiar de estilo con la lista desplazada» (`README.md:473-494`). Que el dueño las pase antes de publicar la línea de la barra, como dicen las notas.
- **Test:** el de las casillas, en el móvil.

## 4. Riesgos con nombre

- **R-211-1. Tras un bloqueo, la comprobación mantiene alcanzables las contraseñas, como `String`, hasta unos 15 s** (texto de AD-4 de la aprobación del director). No son copias: `uniquePasswords` agrupa por la contraseña de cada entrada (`health/Health.kt:32-34`), y la comprobación las retiene (`health/LeakResults.kt:122`) hasta que acaba su petición en vuelo, que no se puede interrumpir, y, con BR-8, hasta que acaba la comprobación anterior a la que espera (`:178`). Tras el bloqueo no empieza ninguna petición (`:147`, `:152`). El tope es el `callTimeout` de 15 s (`net/HttpClient.kt:21`); OkHttp no corta una resolución DNS bloqueada, así que en casos raros puede ser algo más. No se retiene nada más de la contraseña: `marks` y `found` son ids, números y marcas. En la 2.1 pasaba lo mismo en la corrutina de la pantalla, y un `String` no se puede borrar: sin la comprobación seguiría en memoria hasta que lo recoja el recolector de basura (GC). Los `CharArray` que salen a la red sí se borran (`:153-158`), y el hash, en `net/PwnedPasswords.kt:43-45`. Hace falta leer la memoria del proceso para aprovecharlo. Se acepta.
- **R-211-2. Una contraseña cambiada sale de «Filtrada» aunque la nueva también esté filtrada.** `LeakResults` sigue la marca del último cambio (`lastPasswordChange`), nunca la contraseña ni su hash (`health/LeakResults.kt:103-113`). Así, cambiarla por otra también filtrada, o por otra y después otra vez por la de antes (a mano o restaurando una versión), la quita de «Filtrada» hasta comprobar otra vez. Guardarla sin cambiarla no la quita: la marca se congela (CR-1, `vault/EntryHistory.kt:46-48`; corregido por AD-1 de la aprobación del director). Es la decisión del `code-review` (`272c349`): guardar algo de la contraseña sería peor. `LEAK_RECHECK_NEEDED` lo dice, bajo el resultado, cuando algún acceso ha cambiado (`:23-24`, `:222-227`). Se acepta, con ese texto.
- **R-211-3. La cápsula solo se ve de verdad en el móvil.** Es F-3. El rendimiento del difuminado, «Herramientas» a 360 dp con la letra del fabricante (4,6 dp de sobra), TalkBack, el teclado físico y en pantalla, y Android 8 a 11 solo se ven en las casillas de «Pruebas de la 2.1.1».

## 5. Lo comprobado

### 5.1 A. Cripto y secretos

| Comprobación | Resultado | Evidencia |
|---|---|---|
| `LeakResults` no guarda la contraseña, su hash ni su prefijo | Bien | `Found(times, mark)` y `Check` con ids, contadores, época y bóveda: `health/LeakResults.kt:54`, `:63-71`. `LeakResultsTest.itKeepsNoPasswordNorHash` veta `sha1`, `MessageDigest`, `Settings`, `File(`, `Bundle`, `Log.` y `println` en el código |
| El SHA-1 y el prefijo de 5 caracteres siguen en `net/`, sin cambios | Bien | `git diff a29d88c..HEAD -- app/src/main/java/app/seguridad/net/` vacío |
| Borrado de los `CharArray` | Bien | `chars.fill('\u0000')` en un `finally`, tras cada petición: `health/LeakResults.kt:153-158`. `LeakResultsTest.aCheckStoppedWhileWaitingStillHoldsTheNextOne` mira que todos acaban en ceros |
| Olvido al bloquear | Bien | El colector de `AppContainer.kt:969-973` llama a `forget()`; la comprobación espera el cierre y se cancela (`LeakResults.kt:139-142`); `stillOpen()` antes y después de cada petición (`:147`, `:152`, `:160`) y en cada final (`:167-172`). Tests: `aLockStopsTheCheckAndDropsEverything`, `aLockDuringTheLastRequestDropsItWhenItEnds`, `aClosedVaultAtTheEndDropsIt` y `AppContainerTest` «forgotten at a lock» |
| Un bloqueo y desbloqueo rápidos | Bien | `valid()` y `current()` comparan la época de la app y la `generation` (`LeakResults.kt:87-96`); la época sube en cada bloqueo y en cada cambio (`AppContainer.kt:548`, `:584-585`). Test: `anotherEpochForgetsIt` |
| Cambio de bóveda | Bien | La época sube en `switchTo` (`AppContainer.kt:584-585`); el `isOpen` de la pantalla mira el almacén viejo, que se bloquea. Tests: `anotherVaultInMemoryForgetsIt`, `AppContainerTest` «when another vault is opened» |
| «Importar copia» encima de la bóveda abierta (BR-1) | Bien | `leakResults.forget()` en `beforeReplace` (`AppContainer.kt:434-436`) y la `generation` (`vault/VaultStore.kt:275-289`). Tests: `aReplacedVaultStopsTheRunningCheck` y `AppContainerTest` «importing a copy» |
| «Importar de otra app» y «Reemplazar» | Bien | No sustituye la bóveda; «Reemplazar» pasa por `withVersion` (`imports/MergePlan.kt:385`), que pone una marca nueva si cambia la contraseña (`vault/EntryHistory.kt:46-48`): el acceso deja de ser «Filtrada», como un cambio a mano |
| Restaurar una versión y editar sin cambiar la contraseña | Bien | Restaurar pasa por `withVersion` (`VaultStore.kt:1341`). Una edición sin cambio congela la marca (`EntryHistory.kt:33-34`, `:48`), también en un acceso de antes de la 2.0. Test: `aLoginOfBeforeWithNoMarkIsReadAsTheReminderReadsIt`. Papelera y adjuntos no mueven la marca (`VaultStore.kt:1263`, `:1289`, `:1402`, `:1415`) |
| Muerte del proceso | Bien | Solo memoria: ni disco, ni ajustes, ni `Bundle`, ni estado guardado (`LeakResults.kt:27-35`; `LeakResultsTest.theScreenKeepsNoResultOfItsOwn`) |
| Una comprobación a la vez (BR-3, BR-8) | Bien | La nueva espera a la anterior antes de su primera petición (`LeakResults.kt:149`) y no termina antes que ella (`:178`); la cadena solo va hacia atrás. Tests: `aNewCheckWaitsForTheOneBeforeToEnd`, `aCheckStoppedWhileWaitingStillHoldsTheNextOne` y `RedTeam20Recheck3Test` (como mucho una petición en vuelo, con `MockWebServer`) |
| Ningún resultado tardío (decisión (b) de la 1.4.0) | Bien | El contador `run` (`LeakResults.kt:76`, `:194-203`): `publish` y `drop` solo escriben si es la comprobación vigente |
| Red solo a `api.pwnedpasswords.com` | Bien | `net/` sin cambios; la única llamada es `pwned.times(chars)` (`HealthScreen.kt:193-198`). Ninguna consulta nueva ni automática |
| Concurrencia | Bien | `start` lanza dentro del cerrojo; la corrutina corre en `Dispatchers.IO` (`AppContainer.kt:114`) y no puede limpiar `job` antes de que se asigne (`LeakResults.kt:128-183`). Cada escritura del estado, bajo el cerrojo (`:192`, `:201`, `:203`) |

### 5.2 B. MASVS v2

| Grupo | Resultado | Evidencia |
|---|---|---|
| STORAGE | Bien | Ningún `rememberSaveable`, `Bundle`, `SharedPreferences`, `DataStore`, archivo ni log nuevo en el diff de `app/src/main`. El resultado, solo en `AppContainer` (`:969`) |
| CRYPTO | Sin cambios | Ninguna primitiva nueva ni cambio de formato. Los formatos (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`) no se tocan; `AppNameTest` sigue fijando los nombres |
| AUTH | Sin cambios | La huella, la maestra y el bloqueo no cambian. En «Salud», `store.touch()` solo mientras la pantalla se ve y la comprobación corre (`HealthScreen.kt:90`); fuera de ella, el bloqueo por inactividad sigue y para la comprobación. Antes tocaba en cada petición; ahora toca menos, no más |
| NETWORK | Sin cambios | `net/` igual; un solo host; `callTimeout` de 15 s (`net/HttpClient.kt:18-21`) |
| PLATFORM, manifiesto (B-2) | Bien | `git diff a29d88c..HEAD -- app/src/main/AndroidManifest.xml app/src/main/res` vacío. Solo cambia `versionName` (`app/build.gradle.kts:35`) |
| PLATFORM, `FLAG_SECURE` | Bien | `secureWindow()` antes de `setContent` (`MainActivity.kt:46`; `android/SecureWindow.kt:24-25`), sin cambios. La cápsula es un composable de la misma ventana |
| PLATFORM, *tapjacking* y toques que atraviesan la cápsula | Bien | `filterTouchesWhenObscured` en la ventana (`SecureWindow.kt:25`, sin cambios). `swallowTaps` cubre la cápsula, su borde y sus márgenes (`FloatingBar.kt:212-219`, `:246-250`). Detrás solo hay fondo (F-3) |
| PLATFORM, un solo `pointerInput` en `ui/theme` | Bien | `grep pointerInput app/src/main/java/app/seguridad/ui/theme/`: solo `FloatingBar.kt:248` (y su `import`, `:53`) |
| PLATFORM, O-A3 (sin `WallpaperManager`) | Bien | Ninguna aparición en `app/src/main`. La cápsula difumina el fondo que dibuja la app (`LocalBackdrop`, `Glass.kt:350-363`), nunca el fondo de pantalla |
| PLATFORM, O-A4 (sin ventanas translúcidas) | Bien | Ningún `windowIsTranslucent` ni tema translúcido; el manifiesto y `res/` sin cambios. El corte de los toques al fondo de pantalla de la 2.1 (F-1 de la 2.1) sigue en `SecureWindow.kt` |
| CODE | Bien | Ninguna dependencia nueva (`gradle/` sin cambios). Sin `Log`, `println` ni URL nuevos. `NavigationBar` de cristal, quitado (BR-4, `Components.kt:238-245`). La navegación de las dos barras es la misma `goToTab`, con `leaveGuard.leave` (`AppNavHost.kt:348-357`) |
| RESILIENCE | Sin cambios | R8 y la configuración de compilación, iguales |
| PRIVACY | Bien | No trata datos nuevos. El resultado («qué accesos aparecen en filtraciones y cuántas veces») ya estaba en memoria en la 2.1, ahora más tiempo, hasta el bloqueo, y nunca fuera de la app. La política lo dice (`docs/privacidad.html:60`; F-1 y F-2 son precisiones) |

### 5.3 C. Método: los tests y los hallazgos de las revisiones

| Comprobación | Resultado | Evidencia |
|---|---|---|
| `LeakResultsTest` cubre lo que dice | Sí | 19 tests de comportamiento con `StandardTestDispatcher`: guardar, olvidar (época, bóveda, `forget`), cambiar, borrar o mandar a la papelera, añadir, cancelar, fallar, bloquear en cada punto, una a la vez. Dos más son de fuente (`itKeepsNoPasswordNorHash`, `theScreenKeepsNoResultOfItsOwn`) |
| `AppContainerTest` | Sí | Tres tests con el contenedor real: bloquear, abrir otra bóveda e importar una copia encima (BR-7 c) |
| `BottomBarSpaceTest` | En parte | Calcula con `navHostPadding` y un modelo de la pantalla; el test de fuente de m-2 de la tarea 3 ata ese modelo al código. No compone la pantalla (D-1; F-3) |
| `FloatingBarTest` | En parte | Las medidas, `capsuleSlots`, `capsuleEndPadding`, `pillAnimation` y «Herramientas» a 360 dp, con funciones puras; los toques, la semántica y el anillo de foco, con tests de fuente (D-1; F-3) |
| `CapsuleColorsTest` | Sí | Contraste de los nombres, los iconos y la pastilla sobre el fondo, en las seis paletas, con «Personalizado» y «Colores del fondo de pantalla»; opaca con «Reducir transparencias» y «Alto contraste»; los paneles de la 2.1 no cambian |
| `PublishedTextsTest` y `AppearanceTextsTest` | Sí | Fijan `STYLE_MORE`, las «Novedades» (444 caracteres) y los textos de la 2.1.1, sin nombres de otras apps (`the211TextsNameNoOneElse`) |
| `RedTeam20Recheck2Test` y `RedTeam20Recheck3Test` | Sí | Siguen las defensas de la 2.0 en el código nuevo: sin memoria, «Salud» para la comprobación sin borrar lo hallado (`LaunchedEffect(Unit) { leaks.cancel() }`); 20 000 accesos, todos filtrados, sin quedarse sin memoria; con `MockWebServer`, ninguna petición tras `cancel()`, un bloqueo o una importación, y como mucho una en vuelo |
| BR-1 a BR-7 | Cerrados | Arreglados en `4dd05c6`; el repaso del director los da por cerrados, con tests reales (`branch-review.md:146-173`) |
| BR-8 y BR-9 | Cerrados | `34711e9`: `withContext(NonCancellable) { before?.join() }` en el `finally` (`LeakResults.kt:178`) y su test `aCheckStoppedWhileWaitingStillHoldsTheNextOne`; el KDoc de `FloatingBarTest`, en su sitio |
| Menores del `code-review` | Cerrados | El 2 (documentos atrasados), en `14682f0`. El 1 (una contraseña cambiada no se vuelve a comprobar), con `LEAK_RECHECK_NEEDED` en `272c349` (R-211-2) y su test en `theLineSaysHowManyAreLeftAfterFixingSome` |
| `security-review` | Sin vulnerabilidades | `security-review.md`. Coincido con sus dos descartes (R-211-1 y la carrera entre `forget()` y `generation`, sin impacto porque `breached()` cruza id y marca) |
| Tests ejecutados aquí | Ver §6 | |

### 5.4 D. Veracidad de lo publicado

| Texto | Resultado | Evidencia |
|---|---|---|
| `docs/privacidad.html:60` | Cierto | «solo lo recuerda la app en su memoria mientras la bóveda está abierta, y lo olvida cuando la bóveda se bloquea, cuando abres otra o cuando Android cierra la app»: `AppContainer.kt:969-973`, `LeakResults.kt:87-96`. Precisiones opcionales: F-1 y F-2 |
| `docs/privacidad.html:22` (fecha) | Pendiente | Dice el 24 de noviembre de 2026, el día del cambio de `:60`. Hoy es el 25: si se fusiona hoy o después, hay que poner el día de la fusión (lo dicen las notas, `:13`) |
| `LEAK_RESULT_KEPT` | Cierto | «no se guarda en el móvil»: nada a disco. «hasta que la bóveda se bloquee o abras otra»: como mucho; también se olvida al importar (F-2) |
| `LEAK_RECHECK_NEEDED` | Cierto | Solo sale si algún acceso hallado ya no cuenta (`LeakResults.kt:222-227`). Un acceso cambiado no se vuelve a comprobar hasta pulsar el botón otra vez (R-211-2) |
| «Quedan N sin cambiar.» | Cierto, con un matiz | N es `breached.size`: los accesos hallados que siguen siendo acceso, fuera de la papelera, con contraseña y con la misma marca (`LeakResults.kt:103-113`). Uno mandado a la papelera o que dejó de ser acceso cuenta como «cambiado». Para el Abogado |
| La línea explicativa de «Salud» | Cierta | «Solo se ejecuta cuando la pulsas.» Ya no dice «el resultado no se guarda» (O-1 del dictamen de las filtradas): `HealthScreen.kt:178-179` |
| `STYLE_MORE` | Cierto | La barra flota en «Cristal», «Vidrio» y «Transparente» y en «Degradado», opaca (`Glass.kt:108`, `FloatingBar.kt:185-189`); difumina desde Android 12 (`Glass.kt:363`); opaca con «Reducir transparencias» y «Alto contraste» (`CapsuleColorsTest`) |
| README `:27`, `:73`, `:86` y `:473-494` | Ciertos | «el contenido no pasa por debajo» (`navHostPadding`); «un toque en la cápsula o en su margen no llega a lo que hay detrás» (`swallowTaps`); «el manifiesto solo cambia en `versionName` y en el `versionCode`» (diff vacío). La casilla de BR-1 está (`:494`) |
| «deja ver más el fondo de la app» (README `:73`, notas, «Novedades») | Cierto, con sus límites | Solo desde Android 12 y frente a la barra de la 2.1; con «Personalizado» y «Colores del fondo de pantalla», «al menos tanto» (`CapsuleColorsTest.capsuleIsLighterThanThe21Bar`, `customAndWallpaperCapsulesRead`). En «Transparente», el fondo de la app es el fondo de pantalla atenuado, y a través de la cápsula apenas se ve (README, casilla «Transparente»): ver R-5, §9 |
| Notas de GitHub | Ciertas | «No añade permisos, conexiones ni dependencias»: manifiesto, `gradle/` y `net/` sin cambios |
| `docs/ficha-google-play.md` | Cierta | La descripción que se pega (`:50`) no cambia; la versión larga (`:110`) suma la barra flotante y su recuento (7 272) |
| O-7 (sin «Material 3 Expressive», sin Google salvo Google Play, sin «como en», «líquido» ni «accesible») | Se cumple en lo publicado de la 2.1.1 | `PublishedTextsTest.the211TextsNameNoOneElse`. «Material 3 Expressive» solo sale en el KDoc de `FloatingBar.kt:79` y en la parte interna de las notas (`:9`), que no se publican, para decir de dónde salen las medidas (R-1). «Como en cualquier conexión» (`docs/privacidad.html:61`) es de antes y no compara con otra app |
| Nombres de otras apps | Se cumple | Ninguno en los textos nuevos, la ficha ni las «Novedades»; esta versión no cambia qué se importa, así que no hace falta la línea de marcas (R31) |

## 6. Tests ejecutados

`./gradlew :app:testDebugUnitTest --tests …` de las 11 clases de §1, en HEAD `da4edfd`, con `--no-daemon --max-workers=1`: 173 tests en 11 clases, 0 fallos, 0 errores, 0 omitidos (`BUILD SUCCESSFUL`).

## 7. Lo que está bien

- `LeakResults` guarda lo mínimo: id, apariciones y marca. Nada de la contraseña.
- Tres defensas que no dependen unas de otras: el olvido de `AppContainer`, la época y la bóveda en `valid()`, y `stillOpen()` en cada paso de la comprobación.
- La cápsula no abre ventanas, permisos ni lecturas del fondo de pantalla, y no cambia el manifiesto.
- Los textos dicen «como mucho hasta» y no prometen nada que el código no haga.
- «Sólido» conserva la barra de la 2.1 tal cual.

## 8. Veredicto

**Se puede publicar.** Ningún hallazgo que exija cambiar el código. F-1 es una precisión opcional para el Abogado (con el texto de AD-2); F-2 se arregló en `1b04ebe`, por la aprobación del director. F-3 se cierra con las casillas del README en el móvil, que ya son condición para publicar la línea de la barra. Siguen: la revisión final del Abogado Digital, la fecha de la política (el día de la fusión, que ya no es el 24) y las pruebas en el móvil.

## Revisión experto-seguridad-cripto

- Ninguna primitiva, parámetro ni formato nuevo. La k-anonimidad de HIBP sigue igual (`net/` sin diff).
- El resultado no guarda material derivado de la contraseña: `Found(times, mark)` (`health/LeakResults.kt:54`).
- Los `CharArray` se borran tras cada petición (`health/LeakResults.kt:153-158`). Los `String` de `uniquePasswords` duran como mucho 15 s tras un bloqueo (R-211-1, `net/HttpClient.kt:21`).
- Sin resultados tardíos, decisión (b): `run` (`LeakResults.kt:76`, `:194-203`), la época y la `generation` (`:89`, `:147`), `forget()` al bloquear y al importar (`AppContainer.kt:434-436`, `:969-973`).
- Seguir la marca y no la contraseña es la opción más segura; su límite está dicho en pantalla (R-211-2, `LeakResults.kt:23-24`).
- Hallazgos de seguridad confirmados: ninguno. F-1 a F-3, informativos.

## Revisión Ingeniero Móvil Senior

- Ciclo de vida: la comprobación corre en `appScope` (`AppContainer.kt:114`, `:969`), sigue si se sale de «Salud» y se para al bloquear (`LeakResults.kt:139-142`). Si Android mata el proceso, se pierde. Todo como dice el diseño.
- Concurrencia: un cerrojo para el estado, `launch` dentro de él sin carrera con la asignación de `job` (`LeakResults.kt:128-183`); `NonCancellable` solo para esperar a la anterior (`:178`).
- Compose: la cápsula es hermana del `NavHost` en un `Box` (`AppNavHost.kt:386-547`), y el `NavHost` no se mueve del árbol al cambiar de estilo, así que cada pantalla conserva su sitio. `navHostPadding` (`:606-616`) deja abajo exactamente `capsuleSpace()` (`FloatingBar.kt:202-204`).
- Toques: un solo `pointerInput` en `ui/theme` (`FloatingBar.kt:248`), solo los `down`, en el pase `Main`, después de las pestañas (`:212-219`).
- Accesibilidad: `selectableGroup` y `Role.Tab` (`FloatingBar.kt:261`, `:305`); el nombre entero en la semántica (`:336`); anillo de foco de 2 dp (`:314-325`); la cápsula crece con la letra (`:134`); sin animaciones si Android las quita (`:192`, `:301`).
- Rendimiento: `breached` es O(n) por composición (`HealthScreen.kt:110`), del orden de lo que ya había; `RedTeam20Recheck2Test` lo mide con 20 000 accesos filtrados. El difuminado de la cápsula, en el móvil (R-211-3).
- Tests: D-1 limita lo que se puede ejecutar aquí (F-3).

## Textos para el Abogado Digital (revisión final)

Todo lo de `sdd-2.1.1/branch-review.md` («Textos para el Abogado Digital», `:125-135` y `:214-219`) y de las notas (`2026-11-24-clavero-2.1.1-notas.md:39-45`), y además estos puntos abiertos:

1. **R-5 con la opción B.** Su última casilla pedía que en «Transparente» el fondo de pantalla se viera nítido a través de la cápsula. Con la opción B no es así: a través de la cápsula apenas se ve, porque la capa de la app y la cápsula lo tapan casi del todo (la casilla del README ya lo dice así). Que el Abogado acepte la casilla tal como está o diga cómo quiere el texto. La línea «deja ver más el fondo de la app» (README `:73`, notas, «Novedades») compara con la barra de la 2.1 y solo se afirma desde Android 12.
2. **D-2 frente a O-2.** La cápsula mide 64 dp y crece con la letra del sistema: unos 80 dp con la letra doble (`FloatingBar.kt:134`). No cambia mientras se usa, solo con otro tamaño de letra. Que confirme que eso no va contra O-2 («no cambia de forma ni de tamaño»).
3. **Qué significa «Quedan N».** N son los accesos hallados que siguen como estaban: acceso, fuera de la papelera, con contraseña y sin cambiar la contraseña. Uno mandado a la papelera cuenta como «cambiado» aunque no se haya arreglado, y «Ya no queda ninguno sin cambiar.» no dice que las nuevas no estén filtradas (para eso está `LEAK_RECHECK_NEEDED`, justo detrás).
4. **F-2 y F-1 (corregido por AD-2).** F-2 ya está aplicado (`1b04ebe`): «…, cuando importas una copia que la reemplaza o cuando Android cierra la app.»; que lo confirme. La frase de «¿Filtrada?» (F-1) se queda; si quiere nombrar más casos, solo el texto comprobado de la aprobación del director. «Al bloquear la bóveda», sin más, sería falso.
5. **La fecha de la política.** `docs/privacidad.html:22` dice el 24 de noviembre de 2026. Esta auditoría es del 25: si se fusiona ese día o después, se pone el de la fusión.
6. **El texto nuevo `LEAK_RECHECK_NEEDED`** (`health/LeakResults.kt:23-24`): «Una contraseña cambiada después no se vuelve a comprobar: pulsa otra vez «Comprobar filtraciones de los accesos» para saber si la nueva está filtrada.» Lo añadió el `code-review` después del dictamen sobre las filtradas, así que el Abogado no lo ha visto. Sale detrás de «Quedan N sin cambiar.» y de las otras dos frases de `LeakResults.message` (`:222-227`).
7. **«Como en cualquier conexión»** (`docs/privacidad.html:61`): es de antes de la 2.1.1 y no compara con otra app. Solo para confirmar que no choca con O-7.

## Aprobación del director

- **Fecha:** 26-11-2026.
- **Qué ha revisado:** esta auditoría (`4bc006d`), comprobada contra el código y los documentos de `git diff a29d88c..HEAD`. El código es el de `272c349`: después solo cambian documentos.
- **Método:** solo lectura y sin Gradle; se apoya en la compilación completa de `272c349` y en los 173 tests de §6. Ha leído entero el código de cada punto, no solo el diff: `LeakResults`, `HealthScreen`, `AppContainer` (`Slot`, `lockEpoch`, `switchTo`), `VaultStore` (`generation`, `lock`, `upsert`, `importReplace`), `EntryHistory`, `Health`, `PwnedPasswords`, `HttpClient`, `FloatingBar`, `AppNavHost`, `Glass`, `Palettes`, `Components`, `SecureWindow`, `MainActivity`, `BreachViewModel`, `ScreenGuard`, `ScreenAway` y `ScreenGuardWiring`, con sus tests.

### Veredicto

**APROBADA CON CAMBIOS.** Confirma la conclusión de la auditoría: se puede publicar. Ningún hallazgo de seguridad; el código no cambia de comportamiento. Antes del Abogado se arreglan dos menores (AD-1 y AD-2) y F-2. Hechos así, no hace falta otro repaso del director: basta con los tests de las clases tocadas y la compilación completa antes del PR.

### Hallazgos nuevos

| ID | Severidad | Hallazgo | Dónde |
|---|---|---|---|
| AD-1 | Menor | La casilla de «Salud» esperaba que guardar la misma contraseña sacara el acceso de «Filtrada». No lo saca, y eso es lo correcto | `README.md:494`; §4 R-211-2; `health/LeakResults.kt:41-43` |
| AD-2 | Menor | F-1 decía mal cuándo se olvida «¿Filtrada?», y su texto propuesto haría falsa la política | §2, §3 F-1, §8 y «Textos para el Abogado» 4; `tools/codes/ScreenAway.kt:79-83` |
| AD-3 | Info | `LEAK_RECHECK_NEEDED` solo sale si cambia un acceso que salió filtrado | `health/LeakResults.kt:222-227` |
| AD-4 | Info | R-211-1 se puede precisar: qué se retiene y hasta cuándo | §4; `health/Health.kt:32-34`; `net/HttpClient.kt:21` |

**AD-1 [Menor].** Cuando la contraseña no cambia, `withVersion` congela la marca (`vault/EntryHistory.kt:46-48`, con `frozenPasswordMark` en `:33-34`). Por ahí pasan el editor (`vault/VaultStore.kt:983-993`), restaurar una versión (`:1341`) y «Reemplazar» de «Importar de otra app» (`imports/MergePlan.kt:385`). El acceso sigue como «Filtrada», que es lo correcto; solo sale si la contraseña cambia de verdad (por otra también filtrada, o por otra y después otra vez por la de antes). La casilla `README.md:494`, R-211-2 y el KDoc de `LeakResults.kt:41-43` decían lo contrario. Sin impacto de seguridad, pero el dueño apuntaría como fallo lo que está bien. Corrección: los tres textos y un test en `LeakResultsTest`.

**AD-2 [Menor].** La regla de «PIN y códigos» no olvida con todos los bloqueos: `ScreenAway.lockWipes` (`tools/codes/ScreenAway.kt:79-83`) no vacía la pantalla con el bloqueo al salir de la app (`LockReason.LEFT_APP`, `MainActivity.kt:146`) ni con el de inactividad si la pantalla se usó dentro del tiempo de bloqueo; en esos casos el resultado sigue hasta volver a esa pantalla tras `max(bloqueo, 60 s)` fuera. Lo olvidan siempre: volver atrás (`onCleared`, `ui/screens/SecurityToolsScreen.kt:1048-1049`), pulsar «Bloquear» con la app en uso (`watchAway`, `:1018-1019`; `ui/screens/ScreenGuardWiring.kt:53-58`), abrir otra bóveda (`VaultScopes.drop`, `ui/AppNavHost.kt:162-163`, `:183-185`) y la muerte del proceso. La frase publicada es cierta, pero el texto propuesto en F-1 («al bloquear la bóveda») sería falso en el caso más corriente: salir de la app y volver antes de un minuto. Corrección: F-1 de esta auditoría, como está ahora arriba.

**AD-3 [Info].** Un acceso añadido después de la comprobación, o uno que no salió filtrado y al que después se le cambia la contraseña, no cambian la línea del resultado («Comprobación terminada: 0 accesos filtrados.» sigue igual). Ningún texto dice que estén comprobados ni los marca «sin filtrar», así que la R-2 del Abogado se cumple (`LeakResultsTest.aLoginAddedAfterIsNeitherLeakedNorCountedAsChecked`). En la 2.1 no podía pasar: para editar había que salir de «Salud» y se perdía el resultado. Es de claridad; lo decide el Abogado. Si quiere el aviso también ahí, el cambio es pequeño: guardar en memoria la marca de cada acceso comprobado (ids y números, nada de la contraseña) y añadir la frase si alguno cambió o es nuevo.

**AD-4 [Info].** R-211-1 queda con el texto del director (§4).

### F-1, F-2 y F-3

- **F-2 se arregla antes del Abogado,** en un commit propio: `docs/privacidad.html:60`, «… y lo olvida cuando la bóveda se bloquea, cuando abres otra, cuando importas una copia que la reemplaza o cuando Android cierra la app.». Es cierto: `forget()` está en `beforeReplace` (`AppContainer.kt:436`), que corre después de comprobar la copia y su contraseña (`vault/VaultStore.kt:1632-1634`); con una contraseña equivocada no se olvida nada, y si el guardado falla después, el olvido es antes de lo dicho, el lado seguro. `LEAK_RESULT_KEPT`, `README.md:27`, «La 2.1.1» y las «Novedades» se quedan: dicen hasta cuándo, como mucho. `PublishedTextsTest` fija las dos frases de olvido de la política. Las notas (`:13`): la fecha de la política, la de la fusión.
- **F-1 se le deja al Abogado** y la política no cambia. Único texto comprobado si quiere más casos: «El de «¿Filtrada?» se olvida al volver atrás desde esa pantalla, al pulsar «Bloquear», al abrir otra bóveda o cuando Android cierra la app.». No vale «al bloquear la bóveda» sin más, ni «al salir de la app».
- **Clasificación:** F-1, Info (severidad bien, contenido no: AD-2); F-2, Info; F-3, Info, límite de método decidido (D-1) que cubren las casillas del README; lo que dice de los toques es correcto.

### Lo comprobado, punto por punto

1. **Ciclo de vida de `LeakResults`.**
   - Bloqueo: tres guardas independientes, el colector de `AppContainer.kt:969-973`, el vigilante de la comprobación (`health/LeakResults.kt:139-142`) y `stillOpen()` antes y después de cada petición y en cada final (`:147`, `:152`, `:160`, `:167-172`); `valid()` no enseña nada de otra época ni de otra bóveda (`:87-89`). Salir de la app siempre bloquea (`MainActivity.kt:146`).
   - `switchTo` bloquea con `MANUAL` la bóveda que se deja y sube la época a un valor que no se repite (`AppContainer.kt:579`, `:583-586`); la navegación y sus ViewModels se rehacen (`ui/AppNavHost.kt:183-185`).
   - `beforeReplace`: primero `nextGeneration()` y después `forget()` (`vault/VaultStore.kt:1632-1634`; `AppContainer.kt:436`).
   - Muerte del proceso: solo memoria (`AppContainer.kt:969`); la navegación guarda solo rutas.
   - Sigue al salir de «Salud»: `appScope` (`AppContainer.kt:114`), sin `onDispose` que la corte; `store.touch()` solo con la pantalla a la vista (`HealthScreen.kt:90`), así que fuera el bloqueo por inactividad la corta.
   - Una sola enviando: `before?.join()` antes de la primera petición (`:149`) y `withContext(NonCancellable) { before?.join() }` en el `finally` (`:178`); `before` siempre ya cancelada o acabada (`:129-130`, `:196`); `execute()` bloquea dentro de `withContext(Dispatchers.IO)` (`net/PwnedPasswords.kt:34-41`). Sin interbloqueo: la cadena solo va hacia atrás, y `forget()` y `cancel()` no esperan.
   - Ningún resultado tardío: `run` (`:76`, `:194-203`), la época y la `generation`. Una petición que ya iba a salir cuando llega `forget()` sale igual, como en la 2.1.
   - La ventana que descartó el `security-review` está descrita al revés: la `generation` sube antes de `forget()`, así que va de `forget()` hasta que se publica la bóveda importada. Un «Comprobar» pulsado ahí envía las contraseñas de la bóveda que se reemplaza y su resultado queda con la `generation` nueva; pero `breached()` solo enseña un id con la misma marca (`LeakResults.kt:103-113`), y hace falta estar en «Salud» justo durante el guardado. Se descarta igual.
   - Un bloqueo y desbloqueo más rápidos que el colector: `valid()` oculta el resultado viejo y la época nunca vuelve atrás. No es un hallazgo.
2. **Ningún secreto guardado.** `Found(times, mark)` y `Check` solo con ids, números, época y bóveda (`LeakResults.kt:54`, `:63-71`). Nada a disco, `Bundle`, estado guardado, ajustes ni logs en el diff (`itKeepsNoPasswordNorHash`, `theScreenKeepsNoResultOfItsOwn`). Se borran los `CharArray` (`:153-158`), el hash (`net/PwnedPasswords.kt:43-45`) y los bytes y el resumen (`crypto/Hashes.kt:35`, `:46`). R-211-1, en AD-4.
3. **La cápsula.** `swallowTaps` va antes de `windowInsetsPadding` y del `padding` (`ui/theme/FloatingBar.kt:245-250`): la franja cubre la cápsula, sus márgenes y la barra del sistema y mide exactamente `capsuleSpace()` (`:203-204`). Es el último hijo del `Box`, encima del `NavHost` (`ui/AppNavHost.kt:386-387`, `:534-535`); sin `sharePointerInputWithSiblings`, el `NavHost` ni se prueba, también para un elemento del borde que crezca al tamaño mínimo de toque. Solo consume los `down`, en el pase `Main`, después de las pestañas (`FloatingBar.kt:212-219`). Opción B: `navHostPadding` (`AppNavHost.kt:606-616`). D-6: un único `pointerInput` (`FloatingBar.kt:248`), que `PlatformSourcesTest` permite solo ahí. Ningún `WallpaperManager`, `windowIsTranslucent`, `RuntimeShader`, `toImageBitmap` ni `PixelCopy`; el difuminado solo graba `drawAppBackground` (`ui/theme/Glass.kt:415-423`). `FLAG_SECURE` y `filterTouchesWhenObscured` (`android/SecureWindow.kt:24-25`), manifiesto, `res/`, `gradle/` y `scripts/` sin cambios; `app/build.gradle.kts` solo `versionName` (`:35`).
4. **Los textos.** Todos ciertos salvo `README.md:494` (AD-1): `STYLE_MORE` (`ui/screens/AppearanceScreen.kt:104-107`, frente a `Glass.kt:108`, `:363` y `Palettes.kt:101`), `LEAK_RESULT_KEPT`, `LEAK_RECHECK_NEEDED` (cierto siempre que sale; cuándo sale, AD-3), «Quedan N sin cambiar.» (con el matiz de la papelera), `docs/privacidad.html:60` (con F-2), README `:27`, `:73`, `:86` y las casillas, `docs/ficha-google-play.md:110` (7 272 = 7 203 + 69), las notas y las «Novedades» (444 caracteres y 444 unidades UTF-16). `docs/privacidad.html:22`: pendiente, el día de la fusión.

### Revisión experto-seguridad-cripto

- Modelo de amenaza: el de §1, más quien puede leer la memoria del proceso (root o un depurador), para R-211-1.
- Ninguna primitiva, parámetro ni formato nuevo. El SHA-1 es el del protocolo de rangos de Have I Been Pwned, con k-anonimato: 5 caracteres y respuesta con relleno (`net/PwnedPasswords.kt:31-32`); `net/` sin diff.
- Secretos en memoria: el resultado no guarda nada que salga de la contraseña (`health/LeakResults.kt:54`); `CharArray`, hash y bytes, borrados (`:153-158`; `net/PwnedPasswords.kt:43-45`; `crypto/Hashes.kt:35`, `:46`); los `String`, en AD-4.
- Decisión (b): `run`, época, `generation`, `forget()` al bloquear y al importar, `stillOpen()` en cada paso (`LeakResults.kt:76`, `:89`, `:147`, `:194-203`; `AppContainer.kt:436`, `:969-973`).
- MASVS: STORAGE, solo memoria. NETWORK, un host, ninguna consulta nueva ni automática, como mucho una petición de «Salud» en vuelo («¿Filtrada?» es otra acción del usuario; el «una a la vez» de `README.md:27` habla de «Salud»). PLATFORM, la cápsula en la misma ventana con `FLAG_SECURE` y el filtro de toques. PRIVACY, la política es cierta y, con F-2, completa en lo de «Salud»; el texto propuesto en F-1 no se puede usar (AD-2).
- Hallazgos de seguridad confirmados: ninguno. AD-1 y AD-2, de veracidad (Menor); AD-3 y AD-4, Info.

### Revisión Ingeniero Móvil Senior

- Ciclo de vida: la comprobación vive en `appScope` (`AppContainer.kt:114`); sobrevive a salir de «Salud» y a que se rehaga la actividad, muere con el proceso. Cada salida bloquea (`MainActivity.kt:146`): el resultado sirve dentro de la app y en pantalla dividida, que es lo que pidió el dueño.
- Concurrencia: el estado solo se escribe con `lock` (`LeakResults.kt:128-134`, `:192-203`); el `launch` dentro del cerrojo (`:135`, `:179`, `:182`); cerrojos siempre en el mismo orden (`LeakResults.lock` y luego `epochLock`); `forget()` desde `beforeReplace`, con el `mutex` del almacén tomado, no espera nada; `NonCancellable` solo para esperar a la anterior (`:178`).
- Compose: «Salud» solo observa (`HealthScreen.kt:87-90`); `breached` O(n) por composición (`:110`), medido con 20 000 accesos. La cápsula, hermana del `NavHost` y encima, sin mover el `NavHost` en el árbol (`AppNavHost.kt:385-387`); `navHostPadding` y `capsuleSpace()` cuentan lo mismo; `capsuleSlots` trabaja con píxeles enteros y nunca divide por cero.
- Accesibilidad: `selectableGroup`, `Role.Tab` y el nombre entero en la semántica (`FloatingBar.kt:261`, `:305`, `:336`); anillo de foco; sin animaciones cuando Android las quita (`:192`, `:301`).
- Tests: D-1 limita lo ejecutable aquí (F-3); con AD-1, `LeakResultsTest` fija la marca congelada; con F-2, `PublishedTextsTest` fija la frase de la política.

### Textos para el Abogado Digital (revisión final), además de los de arriba

1. **F-2, aplicado sobre su O-2:** `docs/privacidad.html:60`, «…, cuando importas una copia que la reemplaza o cuando Android cierra la app.». Que lo confirme.
2. **F-1:** la frase de «¿Filtrada?» se queda; si quiere más casos, solo el texto comprobado. «Al bloquear la bóveda», sin más, sería falso (AD-2).
3. **`README.md:494`:** la casilla nueva de AD-1.
4. **AD-3:** si quiere que la línea avise también de los accesos añadidos o cambiados que no salieron filtrados.
5. **La fecha de `docs/privacidad.html:22`:** la del día de la fusión.
6. **Para confirmar:** «deja ver más el fondo de la app» (README `:73`, notas y «Novedades») no habla de «Reducir transparencias» ni de «Alto contraste»; con ellos la cápsula es opaca, como la barra de la 2.1 (`ui/theme/Palettes.kt:101`). Ya lo dicen `STYLE_MORE` y el README `:71`.

— el director, 26-11-2026

### Cambios aplicados

- F-2: `1b04ebe` (`docs/privacidad.html:60`, `PublishedTextsTest.thePolicySaysWhenTheLeakResultsAreForgotten`, las notas `:13`).
- AD-1: `6377be9` (`README.md:494`, el KDoc de `health/LeakResults.kt`, `LeakResultsTest.theSamePasswordSavedAgainStaysLeakedAChangeAndBackDoesNot`).
- AD-2 y AD-4, y R-211-2 por AD-1: en esta auditoría (§2, §3 F-1, §4, §8 y el punto 4 de los textos).
- `LeakResultsTest`, `PublishedTextsTest` y `AppearanceTextsTest`: 45 tests, 0 fallos. La compilación completa, antes del PR.
