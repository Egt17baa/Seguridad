# Clavero 2.1.1: revisión de toda la rama (el director)

- **Fecha:** 24-11-2026.
- **Alcance:** rama `ccr-0aa27f95-u038cd`, `a29d88c..ebdd03a` (24 commits; 34 archivos, +2 628 −289). Base: `main` en `a29d88c` (la 2.1.0).
- **Método:** solo lectura. No ejecuté Gradle: me apoyo en lo que da el coordinador (2 724 tests y `jvm-check` 2 602, 0 fallos) y en la lectura del código y del diff.
- **Leídos:** `CLAUDE.md`; `experto-seguridad-cripto/SKILL.md` y `references/auditoria.md`; `desarrollo-movil-senior` y `auditoria-seguridad-movil`; el diseño entero (con la opción B, el añadido de las filtradas y los dos dictámenes); el plan; `sdd-2.1.1/progress.md`; `abogado-2.1.1-diseno.md` y `abogado-2.1.1-filtradas.md`; los informes y las revisiones de las tareas 1 a 5 (`.superpowers/sdd/2026-11-24-clavero-2.1.1-barra/`); el código y los tests cambiados, y lo que tocan (`Glass.kt`, `Components.kt`, `VaultStore.kt`, `AppContainer.kt`, `SecureWindow.kt`, `PwnedPasswords.kt`, `VaultBackup.kt`).

## Veredicto

Con cambios: BR-1 (Important) antes de `code-review`; BR-2 a BR-7 (Minor), en la misma ronda («los detalles menores se arreglan todos»).

## Hallazgos

### Critical

Ninguno.

### Important

**BR-1. «Importar copia» sobre la bóveda abierta no olvida las filtradas de «Salud» ni corta su comprobación.**
- **Dónde:**
  - `AppContainer.kt:965-969`: el resultado solo se olvida cuando la bóveda deja de estar abierta (`it is VaultState.Unlocked` con `distinctUntilChanged`).
  - `LeakResults.kt:54` (`valid`) y `:110` (`stillOpen`): solo miran la época y `isOpen`.
  - `VaultStore.kt:841`: la época solo sube en `lock()`. La importación sube `generation` (`:1632`) y abre la bóveda nueva con la misma época (`:1669`): el estado pasa de `Unlocked` a `Unlocked`.
  - `VaultBackup.kt:194-196`: tras importar, la pantalla no bloquea.
- **Escenario:**
  1. En «Salud», «Comprobar filtraciones de los accesos»: «Comprobación terminada: 3 accesos filtrados.».
  2. Sin bloquear, Ajustes → «Importar copia» de otra bóveda («Se reemplazará la bóveda actual de este móvil…»).
  3. De vuelta en «Salud», la línea dice «Comprobación terminada: 3 accesos filtrados. Ya no queda ninguno sin cambiar.». Habla de la bóveda sustituida, y el usuario no ha cambiado nada.
  4. Si la comprobación seguía en marcha, sigue enviando los prefijos de las contraseñas de la bóveda sustituida y las tiene en memoria hasta acabar. Su resultado se queda.
- **Por qué es Important:**
  - En la 2.1 no podía pasar: salir de «Salud» cortaba la comprobación y perdía el resultado. Lo abre esta rama.
  - Va contra el diseño («se olvida … al cambiar de bóveda») y contra la decisión (b): un borrado a mitad no deja un resultado tardío.
  - Hace falsa la segunda frase nueva de la línea del resultado (M-2 de la tarea 5).
  - Nadie gana nada con ello: es olvido y veracidad, no una fuga.
- **Arreglo propuesto:**
  - Olvidar el resultado y cortar la comprobación cuando una importación sustituye la bóveda. Lo más corto: en `AppContainer.kt:432`, `beforeReplace` llama también a `leakResults.forget()`. Si la importación falla después, solo se pierde el resultado, que es el lado seguro.
  - Otra opción: guardar en `Check` la `VaultStore.generation` (`VaultStore.kt:275-289`, sube justo antes de sustituir) y compararla en `valid()` y en `stillOpen()`.
  - Test en `AppContainerTest`, como `importingAVaultForgetsTheAutomaticCopiesMadeOfTheOldOne` (`:136-158`): con un resultado terminado, importar deja `leakResults.state.value === LeakResults.EMPTY`; con la comprobación en marcha, no sale ninguna petición más.
  - Los KDoc de `HealthScreen` (`:64-70`) y de `LeakResults` (`:16-29`) añaden «o una copia importada la sustituye». Una casilla en «Pruebas de la 2.1.1».
  - Los textos publicados (`LEAK_RESULT_KEPT`, `privacidad.html:60`, README `:27`) siguen siendo ciertos: dicen hasta cuándo, como mucho. El Abogado decide si nombran la importación.

### Minor

**BR-2. Con las animaciones quitadas, las pestañas siguen con el *ripple* de Material.**
- **Dónde:** `FloatingBar.kt:322` usa `ripple(...)` sin `LocalRippleConfiguration provides panelRipple(animationsOn)`. El patrón de la app (M-8) está en `Components.kt:99` y `:162`, y `Glass.kt:258-263` dice «none when it asks to remove them».
- **Escenario:** con «Quitar animaciones», mantener pulsada una pestaña. Salen a la vez el *ripple* (quieto por la escala 0; a confirmar en el móvil) y la marca quieta de `pressSignal`. En el resto de la app solo sale la marca. La revisión de la tarea 2 lo dio por igual a `Components.kt`, pero allí el *ripple* va dentro del proveedor.
- **Arreglo:** envolver el `Column` de `FloatingNavigationItem` en `CompositionLocalProvider(LocalRippleConfiguration provides panelRipple(animationsOn))`, y fijarlo en `FloatingBarTest.pressingTheChosenTabShows` (`:138-147`).

**BR-3. «Una a la vez» (README `:27`) ya no se cumple siempre.**
- **Dónde:** `LeakResults.kt:94` (`start`) y `:151` (`dropLocked`) cancelan sin esperar. La petición en vuelo no se interrumpe: `execute()` dentro de `withContext` (`PwnedPasswords.kt`), hasta 15 s (`HttpClient.kt:18-21`). En la 2.1, el bloqueo hacía `cancelAndJoin` y el botón no volvía hasta que acababa.
- **Escenario:** red lenta y una petición en vuelo. Bloquear, abrir con la huella, entrar en «Salud» y pulsar «Comprobar» antes de 15 s: la petición vieja y la primera de la nueva van a la vez.
- **Impacto:** de privacidad, ninguno (los mismos prefijos que el usuario pidió enviar). Es la exactitud del README y algún hilo de IO de más.
- **Arreglo:** guardar el último trabajo aunque se olvide (`last`) y que la comprobación nueva haga `last?.join()` antes de su primera petición. Test: un `times` que espera, `forget()` y otro `start`: nunca dos llamadas a la vez.

**BR-4. El `NavigationBar` de cristal ya no se usa, y su KDoc ya no es cierto.**
- **Dónde:** `Components.kt:238-251`. `AppNavHost.kt:368` solo lo llama con `!look.floatingBar` («Sólido»), que nunca es `translucent`. La rama de cristal (`:245-250`) es código muerto, y el KDoc (`:238`, «glass in the translucent styles») describe algo que ya no pasa.
- **Arreglo:** dejar solo la barra de Material, con un KDoc que diga que es la de «Sólido». «Sólido» no cambia.

**BR-5. El KDoc de la cápsula no cuadra con el registro de R-1.**
- **Dónde:** `FloatingBar.kt:75-78` dice que las medidas «follow» la guía y le atribuye cifras («a container about 64 dp high»), y remite al informe de la tarea 2, que está en `.superpowers/`, fuera de git. Las notas (`2026-11-24-clavero-2.1.1-notas.md:9`) dicen que las medidas son propias, con la guía como orientación, y que su página de medidas no se pudo leer.
- **Arreglo:** que el KDoc diga lo de las notas y remita a ellas, que son el único registro versionado de R-1. O-7 sigue permitiendo nombrar la guía en el KDoc.

**BR-6. Las notas se contradicen sobre la política.**
- **Dónde:** `2026-11-24-clavero-2.1.1-notas.md:44` («la política, solo para confirmar que no cambian») frente a `:13` y `:41`: la política cambia en `privacidad.html:60` (tarea 5).
- **Arreglo:** quitar «y la política» de `:44`. Es la lista que leerá el Abogado en la revisión final.

**BR-7. Tres tests ya no miden todo lo que dicen.**
- (a) `RedTeam20Recheck2Test.kt:474-496` mide lo que «Salud» hace en el hilo principal, pero no `leaks.breached(entries, check)`, que corre en cada composición (`HealthScreen.kt:110`, O(n)), ni `checked` y `marks` del toque (`LeakResults.kt:87-92`). Añadirlos a la medida, con todos los accesos filtrados.
- (b) `RedTeam20Recheck3Test.kt:467-500` sigue probando un bucle propio sobre `PwnedPasswords`. El bucle real (`LeakResults.start`) ya es JVM: llevarlo con el `PwnedPasswords` real y `MockWebServer`, con `cancel()` y con un bloqueo (`awaitClosed`), y contar las peticiones.
- (c) El olvido de `AppContainer` al bloquear y al cambiar de bóveda solo lo fija una cadena del código (`LeakResultsTest.kt:289-291`). `AppContainerTest` puede probarlo de verdad: `vault.lock`, `switchTo` y, con BR-1, `importReplace`.

## Riesgos o dudas

1. No ejecuté Gradle. Me apoyo en la cifra del coordinador y en la lectura del código.
2. Solo se ve en el móvil (casillas de «Pruebas de la 2.1.1», ya escritas): el rendimiento del difuminado; «Herramientas» a 360 dp con la letra del fabricante (4,6 dp de sobra); TalkBack y el teclado físico; el teclado en pantalla en «Cristal»; Android 8 a 11. Falta una casilla para BR-1 (importar una copia con el resultado a la vista y con la comprobación en marcha), y BR-2 se mira con «Quitar animaciones».
3. R-5 del dictamen del principio: con la opción B, en «Transparente» llega a través de la cápsula un 0,8 % del fondo de pantalla (I-1 de la tarea 4). No se puede cumplir tal cual; lo decide el Abogado.
4. D-2: la cápsula crece con la letra del sistema. Mide 64 dp; al 200 %, unos 76 dp con la curva no lineal de Android 14 y 80 dp con la lineal. La casilla dice «unos 80 dp», y vale. O-2 habla del uso; que lo confirme el Abogado.
5. Límite del diseño: la lista sigue la marca del último cambio, no la contraseña. Un acceso cuya contraseña pasa a otra filtrada (a mano o restaurando una versión) deja de salir como «Filtrada» y cuenta como cambiado en «Quedan N sin cambiar», hasta otra comprobación. Ningún texto dice que esté a salvo. El Abogado decide si la segunda frase debe decir que las contraseñas nuevas no se han comprobado.
6. La fecha de la política (`privacidad.html:22`, 24 de noviembre de 2026): si se fusiona otro día, se pone al día justo antes, como dicen las notas (`:13`).
7. `capsuleSlots` (`FloatingBar.kt:166-174`) divide por `left` sin guarda. Con anchos en píxeles enteros, que son los de la app, no llega a 0 porque las sumas son exactas: no lo cuento como hallazgo. Un `if (left == 0) return natural` lo haría robusto con cualquier entrada.
8. Para la auditoría: `twoStepCopy` y el aviso del portapapeles (de la 2.0) también se rigen por la época y no por `generation`. Tras importar una copia, una oferta viva solo sirve para el mismo id. Queda fuera de esta rama.
9. Los documentos de estado (el parte y «Fase» de `CLAUDE.md`) van atrasados; los pone al día el coordinador. No dicen nada falso que haga daño.

## Revisión experto-seguridad-cripto

Modelo de amenaza: quien tiene el móvil desbloqueado, otra app (superposiciones, servicios de accesibilidad, un fondo de pantalla vivo), la red y la muerte del proceso. Activos nuevos: el resultado de la comprobación (qué accesos están filtrados y cuántas veces) y las contraseñas que la comprobación tiene en memoria mientras corre, ahora también fuera de «Salud».

- **Superficie:** no cambian formatos, criptografía, Keystore, permisos ni dependencias. El manifiesto, `scripts/`, `gradle/` y `MainActivity.kt` no cambian (`git diff --stat`), así que B-2 y `check-manifest.sh` siguen igual. Solo cambia `versionName` (`app/build.gradle.kts:35`).
- **Red (MASVS-NETWORK):** la única llamada sigue siendo `pwned.times(chars)` (`HealthScreen.kt:195`), con el cliente de siempre (`AppContainer.kt:405`; `PwnedPasswords.kt` no cambia). No hay consultas nuevas ni automáticas. «Una a la vez»: BR-3.
- **Datos en memoria (MASVS-STORAGE):**
  - El resultado guarda el id, las apariciones y la marca (`LeakResults.kt:32`, `:41`); ni la contraseña ni su hash (`LeakResultsTest.itKeepsNoPasswordNorHash`).
  - Los `chars` se borran tras cada petición (`LeakResults.kt:114-119`), y `PwnedPasswords` borra su hash.
  - Las contraseñas agrupadas (`groups`, `String`) viven mientras corre la comprobación, como antes; el bloqueo la corta (`LeakResults.kt:102-105`, `:113`).
  - Nada en disco, en un `Bundle`, en `rememberSaveable` ni en registros (búsqueda en el diff; `LeakResultsTest.kt:281-292`).
- **Olvido y carreras (decisión (b)):** la época de la app (`AppContainer.kt:545`; solo sube en `VaultStore.kt:841` y en `switchTo`), el olvido en cada bloqueo (`AppContainer.kt:965-969`), `run` (`LeakResults.kt:46`, `:156-158`) y `stillOpen()` en cada paso y en cada final (`LeakResults.kt:110-132`). Bien para el bloqueo, el cambio de bóveda y la muerte del proceso. El hueco es la importación que sustituye la bóveda (BR-1).
- **Toques (MASVS-PLATFORM, O-4):** el contenido termina encima de la cápsula (`AppNavHost.kt:385-387`, `:606-616`). La franja consume cada pulsación que empieza en ella (`FloatingBar.kt:209-216`, `:245`), y ningún antepasado arrastra (`PlatformSourcesTest.kt:1527-1533`). `filterTouchesWhenObscured`, la marca de accesibilidad y la exclusión del autocompletado están en la vista raíz (`SecureWindow.kt:23-29`) y valen también para la cápsula.
- **Capturas:** `FLAG_SECURE` sin cambios (`MainActivity.kt:46`, `SecureWindow.kt:24`); la cápsula está en la misma ventana.
- **Fondo de pantalla (O-A3, O-A4) y dibujo:** el difuminado solo graba `drawAppBackground` (`Glass.kt:414-423`). Ni `WallpaperManager`, ni ventanas translúcidas, ni `RuntimeShader`, ni `graphicsLayer` propio (`FloatingBarTest.kt:256-258`, `PlatformSourcesTest.kt:1366-1377`). Desde Android 14 los toques siguen sin pasar al fondo de pantalla (`SecureWindow.kt:40-44`).
- **Veracidad:** los textos publicados cuadran con el código: `STYLE_MORE`, README `:27`, `:73` y `:86`, la ficha `:110`, las notas y `privacidad.html:60`. La línea nueva del resultado puede volverse falsa por BR-1. Recuentos comprobados: «Novedades», 444 caracteres; versión larga de la ficha, 7 272 (antes, 7 203).
- **Privacidad del dueño y nombres:** autor `Egt17baa` con su dirección `noreply` en los 24 commits; en lo añadido, ningún nombre de modelo, de la app de la referencia ni de otras apps (búsqueda en el diff); datos de prueba inventados.
- **Excepciones:** `CancellationException` se relanza (`LeakResults.kt:128-130`); un `Error` sale por `backgroundFailure` (`AppContainer.kt:92-94`), como antes.

## Revisión Ingeniero Móvil Senior

- **Arquitectura:** el resultado es un objeto de sesión sin Android en `AppContainer`, como `twoStepCopy` y `clipboardNotice` (`AppContainer.kt:965`). La pantalla solo observa (`HealthScreen.kt:87-90`). Encaja con el resto de la app.
- **Ciclo de vida:**
  - El trabajo va en `appScope` (`AppContainer.kt:114`). Sobrevive a salir de «Salud», al giro (la actividad no se rehace, `AndroidManifest.xml:25`) y a otras recreaciones de la actividad; muere con el proceso.
  - `store.touch()` solo mientras «Salud» se ve (`HealthScreen.kt:90`). Fuera, el bloqueo por inactividad la corta (`VaultScreen.kt:129-136`). En multiventana se comporta como antes.
- **Concurrencia:** el estado va en un `MutableStateFlow` bajo un cerrojo (`LeakResults.kt:43-48`, `:147-158`). `launch` dentro del cerrojo no corre en línea con el `IO` de `appScope`. Dos detalles: BR-1 y BR-3.
- **Compose, la cápsula:**
  - Es hermana del `NavHost` en un `Box`, fuera de todo consumo de *insets* (`AppNavHost.kt:386`, `:534-546`). `capsuleSpace()` y la franja cuentan lo mismo (`FloatingBar.kt:200-201`, `:244-247`), fijado en `BottomBarSpaceTest.theCapsuleTakesExactlyCapsuleSpace`.
  - El `NavHost` no cambia de sitio con el estilo (`AppNavHost.kt:386-387`) y `LocalLook` es estático (`Glass.kt:177`): cambiar de estilo no rehace las pantallas ni pierde su posición.
  - «Sólido» es la 2.1: `navHostPadding` devuelve el mismo `padding` (`AppNavHost.kt:606-608`), y `git diff -w` solo muestra `goToTab`, el `if` y el `Box`; las rutas no cambian.
  - Teclado: los `imePadding` de dentro restan `capsuleSpace()`, igual que antes restaban la barra. Casilla en el móvil.
- **Rendimiento:**
  - La opacidad se calcula con la paleta, en caché (`Palettes.kt:461`). El difuminado se graba en `drawWithCache` y solo se rehace si cambia el sitio o el tamaño (`Glass.kt:358-404`).
  - El reparto de anchos mide los anchos intrínsecos solo al medir (`FloatingBar.kt:256-271`). Las únicas animaciones son tres colores por pestaña, al cambiar (`:290-292`).
  - En «Salud», `breached` es O(n) por composición (`HealthScreen.kt:110`), del orden de `byId` (`:135`), que ya estaba. Nada por fotograma. Falta medirlo (BR-7 a).
  - Sin hallazgo: `goToTab` no está en `remember`, así que las cinco pestañas se recomponen con `VaultNav`, igual que las de Material en la 2.1.
- **Accesibilidad:** grupo seleccionable y `Role.Tab` (`FloatingBar.kt:258`, `:298`); el nombre entero en la semántica (`:329`); iconos sin descripción (`AppNavHost.kt:540`); anillo de foco de 2 dp, a 3:1 o más (`FloatingBar.kt:307-318`); la cápsula crece con la letra (`:131`, `:193`). Animaciones: `pillAnimation` (`:189`); el *ripple*, en BR-2.
- **Código muerto:** BR-4.
- **Tests (D-1):** sin ui-test ni Robolectric, como se decidió. Las funciones puras y los tests de fuente cubren lo que pueden. La geometría de `BottomBarSpaceTest.lastItemEndsAboveTheCapsule` sale igual por construcción; vale por el test de fuente de m-2. `CapsuleColorsTest` mide con sus propios ayudantes, no con el corrector. Los huecos, en BR-7.

## Textos para el Abogado Digital (revisión final)

- `STYLE_MORE` (`AppearanceScreen.kt:104-107`): con la opción B sustituye al de O-6.
- README: `:27` («Salud de la bóveda»), `:73` («Aspecto (2.1.1)»), `:86` («La 2.1.1») y `:473-494` («Pruebas de la 2.1.1»), con la casilla que pide BR-1.
- Ficha: la línea `:110` y su recuento en `:69` (7 272, comprobado). La descripción que se pega (`:50`) no cambia.
- Notas (`2026-11-24-clavero-2.1.1-notas.md`): los bloques de GitHub y de «Novedades» (444 caracteres) y la parte interna (`:9`, R-1; `:44`, BR-6).
- «Salud»: la línea explicativa (`HealthScreen.kt:178-179`), `LEAK_RESULT_KEPT` (`:54`) y la segunda frase de `LeakResults.message` (`LeakResults.kt:176-181`: «Quedan N sin cambiar.», «Queda 1 sin cambiar.» y «Ya no queda ninguno sin cambiar.»). Cuenta como «cambiado» un acceso en la papelera o con otra contraseña, aunque también esté filtrada (riesgo 5). Sin BR-1 arreglado, la última puede salir tras importar una copia.
- `privacidad.html:60` y su fecha (`:22`). Con BR-1 arreglado, si quiere, «o la sustituyes con una copia».
- Los KDoc de `HealthScreen` (`:64-70`, O-3) y de la cápsula (`FloatingBar.kt:71-83`, O-7 y R-1; BR-5).
- La R-5 del dictamen del principio, que la opción B no deja cumplir tal cual (riesgo 3).
- D-2 frente a O-2 (riesgo 4). Y «una pastilla detrás de la pestaña elegida» (README `:86` y las casillas), cuando la pastilla está detrás del icono (D-3): confirmar que describe bien lo que se ve.
