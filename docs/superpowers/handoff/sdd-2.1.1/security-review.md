# 2.1.1: `security-review` de la rama

**Alcance:** `git diff a29d88c..HEAD` en la rama `ccr-0aa27f95-u038cd`, hasta `1ee1f48`. Solo lo que introduce la rama: la barra de abajo flotante (`ui/theme/FloatingBar.kt`, `ui/AppNavHost.kt`, `ui/theme/Components.kt`, `Glass.kt`, `Palettes.kt`), los resultados de las filtradas en memoria (`health/LeakResults.kt`, `AppContainer.kt`, `ui/screens/HealthScreen.kt`), `versionName` 2.1.1, los textos y los documentos.

**Método:** la skill `security-review`, con sus filtros de falsos positivos. Lectura del código; no se ejecutó nada. Revisor: el revisor.

**Modelo de amenaza:** un atacante con el móvil desbloqueado después de que la bóveda se bloquee; otra app en el mismo móvil; quien mire la pantalla o las capturas; la red hasta Have I Been Pwned.

## Resultado

**Ninguna vulnerabilidad con confianza ≥ 0,8.**

No hay hallazgos que señalar. Lo que se miró y por qué no es un hallazgo está abajo.

## Lo comprobado, con evidencia

1. **Ningún secreto en el resultado guardado.** `LeakResults.Found` solo tiene el número de apariciones y la marca del último cambio (`health/LeakResults.kt:54`). `Check` guarda ids, contadores y la época (`:63-71`). Ni la contraseña ni su hash ni su prefijo pasan al estado.
2. **La contraseña se borra tras cada petición.** `chars.fill('\u0000')` en un `finally` (`health/LeakResults.kt:153-158`), igual que antes de la rama. Los `String` de `uniquePasswords` viven en la corrutina solo mientras la comprobación corre; un bloqueo la cancela (`:139-142`). Es lo mismo que hacía la pantalla en la 2.1 (riesgo previo, no nuevo).
3. **Nada a disco, al estado guardado ni a los logs.** El diff de `app/src/main` no añade `rememberSaveable`, `Bundle`, `SharedPreferences`, `DataStore`, archivos ni `Log`. `LeakResults` vive en `AppContainer` (`AppContainer.kt:969`). `HealthScreen` ya no tiene estado propio de la comprobación (`ui/screens/HealthScreen.kt:87-90`).
4. **El bloqueo lo borra.** `AppContainer` llama a `forget()` cuando la bóveda deja de estar abierta (`AppContainer.kt:969-973`). La comprobación espera el cierre y se cancela (`health/LeakResults.kt:139-142`). Antes y después de cada petición mira `stillOpen()`: abierta, misma época y misma bóveda (`:147`, `:152`, `:160`). Al terminar, cancelarse o fallar con la bóveda cerrada, `drop(r)` (`:167-172`).
5. **Un bloqueo y desbloqueo rápidos.** La época cambia con cada bloqueo y cada cambio de bóveda (`AppContainer.kt:548`, `:584-585`). `valid()` y `current()` descartan un resultado de otra época o de otra bóveda (`health/LeakResults.kt:87-96`). La pantalla solo lee a través de `valid()` (`HealthScreen.kt:88`, `:110`).
6. **Importar sin bloquear (BR-1).** `beforeReplace` llama a `leakResults.forget()` (`AppContainer.kt:432-437`). `forget()` sube `run`, así que una respuesta tardía no publica (`health/LeakResults.kt:194-203`). Además, la `generation` del almacén sube al reemplazar (`vault/VaultStore.kt:275-289`) y `stillOpen()` la compara. Si quedara un resultado viejo, `breached()` exige el mismo id y la misma marca en la bóveda actual (`health/LeakResults.kt:103-113`): no muestra nada secreto.
7. **Una sola comprobación a la vez (BR-3, BR-8).** La nueva espera a la anterior antes de su primera petición (`health/LeakResults.kt:149`) y no termina antes que ella (`:178`). La cadena solo va hacia atrás: sin bloqueo mutuo.
8. **Red.** La rama no toca `net/`. El único host sigue siendo `api.pwnedpasswords.com` (`net/PwnedPasswords.kt:24`, `net/HttpClient.kt:24`). Solo se envían los 5 primeros caracteres del SHA-1, como antes.
9. **Manifiesto (B-2).** `git diff a29d88c..HEAD -- app/src/main/AndroidManifest.xml app/src/main/res` está vacío. Ningún permiso, componente ni host nuevo. Solo cambia `versionName` (`app/build.gradle.kts:35`).
10. **FLAG_SECURE.** Sin cambios: `secureWindow()` antes de `setContent` (`MainActivity.kt:46`, `:50`; `android/SecureWindow.kt:24`). La cápsula es un composable de la misma ventana, no una ventana nueva.
11. **Un solo `pointerInput` en `ui/theme`.** Solo en `ui/theme/FloatingBar.kt:248` (`swallowTaps`, `:212-219`). Consume solo los toques en la franja de la cápsula, en el pase `Main`, después de los hijos: no roba toques de fuera ni anula el filtro de toques ocultos.
12. **O-A3 y O-A4.** En `app/src/main` no aparecen `WallpaperManager`, `WALLPAPER_SERVICE`, `windowIsTranslucent`, `Theme.Translucent`, `setTranslucent` ni `RuntimeShader` en código. La cápsula difumina lo que dibuja la app (`Modifier.glass`, `FloatingBar.kt:241-245`).
13. **Navegación.** La cápsula usa la misma `goToTab` que la barra, con `leaveGuard.leave` (`ui/AppNavHost.kt` en `goToTab`, `:535-543`). El resto del `NavHost` solo cambia de sangría.
14. **Inactividad.** Mientras corre una comprobación con la pantalla a la vista, `store.touch()` (`HealthScreen.kt:90`). Fuera de la pantalla no toca: el bloqueo por inactividad sigue y para la comprobación.
15. **Textos.** `LEAK_RESULT_KEPT` (`HealthScreen.kt:54`) y `LEAK_RECHECK_NEEDED` (`health/LeakResults.kt:23-24`) dicen lo que hace el código: no se guarda en el móvil y se olvida al bloquear o abrir otra.

## Descartado por los filtros

- **Los `String` de las contraseñas en la corrutina de `appScope`.** Vivían igual en la corrutina de la pantalla en la 2.1. Duran hasta la última petición en vuelo tras un bloqueo. No es nuevo ni es explotable sin acceso a la memoria del proceso. Confianza < 0,8.
- **Una comprobación nueva entre `forget()` y la subida de `generation` al importar.** Exige estar en «Salud» durante la importación. Aun así, `breached()` cruza id y marca con la bóveda actual. Sin impacto de seguridad.

## Revisión experto-seguridad-cripto

- Ninguna primitiva nueva ni cambio de formato. La k-anonimidad de HIBP no cambia (`net/PwnedPasswords.kt:24`).
- El resultado no guarda material derivado de la contraseña: ni hash ni prefijo (`health/LeakResults.kt:54`, `:63-71`).
- Borrado de los `CharArray` tras cada petición (`health/LeakResults.kt:153-158`).
- Sin resultados tardíos: contador `run` (`:76`, `:194-203`), época y `generation` (`:147`), `forget()` al bloquear y al importar (`AppContainer.kt:436`, `:971`). Cumple la decisión (b) de la 1.4.0.
- Sin red nueva, manifiesto igual, FLAG_SECURE igual (puntos 8 a 10).

## Revisión Ingeniero Móvil Senior

- MASVS-STORAGE: nada a disco, `Bundle` ni estado guardado; solo memoria del proceso (`AppContainer.kt:969`).
- MASVS-PLATFORM: sin componentes, permisos ni ventanas nuevas (manifiesto sin diff). La cápsula está en la misma ventana segura (`FloatingBar.kt:236-251`).
- Concurrencia: `appScope`, una comprobación a la vez, `synchronized(lock)` en cada escritura del estado (`health/LeakResults.kt:128`, `:192`, `:201`, `:203`), `NonCancellable` solo para esperar a la anterior (`:178`).
- Ciclo de vida: salir de «Salud» ya no cancela; el bloqueo sí (`health/LeakResults.kt:139-142`, `AppContainer.kt:971`). Si Android mata el proceso, el resultado se pierde.
- Toques: un solo `pointerInput` en `ui/theme` (`FloatingBar.kt:248`).

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo de esta revisión. Los textos de la rama (`LEAK_RESULT_KEPT`, `LEAK_RECHECK_NEEDED`, `STYLE_MORE`) coinciden con el código (punto 15).
