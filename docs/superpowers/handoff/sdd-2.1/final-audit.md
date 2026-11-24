# Clavero 2.1: auditoría completa (revisión final)

Fecha: 24-11-2026. Rama `ccr-0aa27f95-u038cd`, HEAD `c44e2a4`. Punto 6 de `CLAUDE.md`, después del `code-review` y del `security-review` de hoy (`sdd-2.1/progress.md`, al final).

## 1. Alcance y método

**Alcance.** Lo que cambia la 2.1 frente a `v2.0.0`: `git diff v2.0.0...HEAD -- app/src/main app/build.gradle.kts gradle/` (20 archivos, +1 021/−177):

- QR de wifi en «Añadir → Red WiFi», con cámara e imagen: `vault/EntryForm.kt`, `ui/screens/VaultEntryScreen.kt`, `ui/screens/QrImageRead.kt` (nuevo), `tools/qr/WifiQr.kt`, `tools/qr/QrView.kt`, `ui/screens/QrInspectScreen.kt`, `ui/screens/PhotoCleanScreen.kt`.
- Estilos «Degradado», «Vidrio» y «Transparente»: `settings/Appearance.kt`, `ui/theme/Glass.kt`, `Palettes.kt`, `Components.kt`, `Theme.kt`, `WallpaperWindow.kt` (nuevo), `ui/screens/AppearanceScreen.kt`, `VaultList.kt`, `MainActivity.kt`.
- Icono nuevo (tres XML de `res/drawable`) y `versionName` 2.1.0.

Y la comprobación de que nada de la 2.0 se ha roto: formatos, manifiesto, `FLAG_SECURE`, red y dependencias.

**Fuera del alcance.** Los documentos (los revisa el Abogado), la CI y el código que no cambia, salvo para seguir un flujo.

**Modelo de amenaza.** Otra app en el móvil (también una app de fondo de pantalla animado), quien mira la pantalla, un QR o una imagen hostiles, la muerte del proceso y el bloqueo a mitad de una lectura, y la multiventana.

**Método.** Por este orden: `experto-seguridad-cripto` (SKILL.md y `references/auditoria.md`), `ingeniero-movil-senior:auditoria-seguridad-movil` (MASVS v2, por grupo) y `auditoria-seguridad:auditoria-seguridad`. Lectura del diff entero y de los flujos que toca (`followVault`, `clear`, `secureWindow`, `SecureCaptureActivity`). Comprobaciones ejecutadas:

- `./gradlew :app:assembleDebug` y `:app:testDebugUnitTest` de `PlatformSourcesTest`, `AppNameTest`, `SourceTextsTest`, `*WifiQr*`, `*Glass*` y `*EntryEdit*`: 203 tests en 14 clases, 0 fallos.
- `scripts/check-manifest.sh` sobre el APK de depuración: «OK». `aapt2 dump xmltree` del manifiesto: ningún `Translucent` ni `wallpaper`.
- `git diff --stat v2.0.0...HEAD` del manifiesto, `gradle/`, los `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `proguard-rules.pro`, `res/xml` y `res/values`: solo cambia `app/build.gradle.kts:35` (`versionName`).
- `javap` de `WindowManager$LayoutParams` del `android.jar` 36: existen `setWallpaperTouchEventsEnabled(boolean)` y `areWallpaperTouchEventsEnabled()` (F-1).

## 2. Resumen

| ID | Severidad | Hallazgo | Dónde |
|---|---|---|---|
| F-1 | Medio | En «Transparente», Android duplica cada toque de Clavero a la app del fondo de pantalla, que puede ser de otro | `ui/theme/WallpaperWindow.kt:50-58` |
| F-2 | Info | Los textos de «Transparente» y la política no lo dicen | `ui/screens/AppearanceScreen.kt:101-105`, `docs/privacidad.html:37` |

Ningún hallazgo Crítico ni Alto. El resto de la 2.1 cumple las reglas duras (§5).

## 3. Hallazgos

### F-1 [Medio] «Transparente» manda los toques de Clavero a la app del fondo de pantalla

- **Dónde:** `app/src/main/java/app/seguridad/ui/theme/WallpaperWindow.kt:50-58` (`applyWindowLook` pone `FLAG_SHOW_WALLPAPER` y no toca nada más), llamado desde `MainActivity.kt:65-66`.
- **Qué pasa:** una ventana con `FLAG_SHOW_WALLPAPER` es el «objetivo del fondo de pantalla». Para ella, Android duplica cada toque a la ventana del fondo de pantalla. Un fondo animado es un `WallpaperService` de cualquier app instalada; si su `Engine` llama a `setTouchEventsEnabled(true)`, recibe en `onTouchEvent` las coordenadas y la hora de cada toque sobre Clavero. Desde Android 14 (API 34), `WindowManager.LayoutParams.setWallpaperTouchEventsEnabled(false)` lo corta; la documentación de Android dice que por defecto está activado y que el fondo «puede ser de una app de terceros». Clavero no lo llama (`grep WallpaperTouch`: nada en `app/`).
- **Qué no pasa:** el fondo no ve la pantalla (`FLAG_SECURE`, y él está debajo). Lo que se escribe con el teclado tampoco: el teclado es otra ventana, que no es el objetivo del fondo. Clavero no tiene teclado propio (`grep Keypad|PinPad`: solo la lista de `tools/codes/Pin.kt`, que no es un teclado).
- **Impacto:** metadatos de uso dentro de un gestor de contraseñas. Con la disposición de la pantalla, la app del fondo puede deducir qué se toca y cuándo: qué fila de la lista se abre, cuándo se pulsa «Copiar contraseña», «Mostrar», el ritmo de uso, los recuadros que se tachan en «Tachar». No da contenidos.
- **Probabilidad:** baja. Hace falta elegir «Transparente» (no viene por defecto) y tener puesto un fondo animado de una app que lo aproveche. Medio por el tipo de app y porque el arreglo en Android 14+ es una línea.
- **Arreglo propuesto:**
  1. En `applyWindowLook`, con el fondo, desde API 34:
     ```kotlin
     if (look.showWallpaper) {
         window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
             window.attributes = window.attributes.apply { setWallpaperTouchEventsEnabled(false) }
         }
         window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
     }
     ```
     Con una prueba de fuente en `PlatformSourcesTest`, como las de `FLAG_SECURE`.
  2. En Android 8 a 13 no hay forma de cortarlo sin leer qué fondo hay (`WallpaperManager.getWallpaperInfo()`, que no da la imagen, pero la prueba «sin `WallpaperManager`» lo prohíbe). Decisión del dueño, con el director: (a) aceptarlo como riesgo con nombre y decirlo en el texto (F-2); o (b) no ofrecer «Transparente» antes de Android 14 (como el difuminado, «desde Android 14»); o (c) leer solo si hay un fondo animado y, si lo hay, verlo como «Cristal». Recomiendo (b): es lo más simple y no añade lecturas del sistema.
- **Verificación:** en un móvil con Android 14+ y un fondo animado de prueba que registre `onTouchEvent`, tocar Clavero en «Transparente»: no llega nada. Una casilla en «Pruebas de la 2.1, Aspecto» del README.
- **Referencias:** MASVS-PLATFORM-3 y MASVS-PRIVACY-1; CWE-359 (exposición de información privada); `WindowManager.LayoutParams#setWallpaperTouchEventsEnabled` (API 34).

### F-2 [Info] Los textos de «Transparente» no dicen lo de F-1

- **Dónde:** `STYLE_TRANSPARENT_MORE` en `ui/screens/AppearanceScreen.kt:101-105`; `docs/privacidad.html:37`.
- **Qué pasa:** dicen, con verdad, que la app no lee la imagen ni pide permisos y que quien mire la pantalla verá el fondo. No dicen que, mientras F-1 siga abierto en alguna versión de Android, la app del fondo animado recibe los toques.
- **Arreglo propuesto:** depende de la decisión de F-1. Con (a), una frase del tipo «Con un fondo de pantalla animado, la app que lo pone puede recibir tus toques sobre Clavero (hasta Android 13).», para el Abogado. Con (b) o con (c), no hace falta texto nuevo más allá de «desde Android 14» o de que se ve como «Cristal».

## 4. Tabla MASVS v2 de la 2.1

| Grupo | Estado | Evidencia |
|---|---|---|
| STORAGE | Cumple | El QR de wifi solo vive en la sesión del formulario (`EntryForm.kt:166`, `:181`, `toString` sin secretos), nunca en `rememberSaveable` ni en un `Bundle`; se borra en `clear()` (`EntryForm.kt:785`, `:809-813`), que corre al bloquear (`followVault`, `EntryForm.kt:833-835`) y al salir. Los bytes de la imagen se ponen a cero (`QrImageRead.kt:55`, `PhotoCleanScreen.kt:110`). Sin logs nuevos. Los `rememberSaveable` de `AppearanceScreen.kt:176-179` son de la 2.0 y sin secretos. |
| CRYPTO | Sin cambios | Ningún archivo de `crypto/`, `backup/`, `net/` ni `VaultStore.kt` cambia; los formatos (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, v4) siguen igual. El nuevo `ThemeStyle` se añade al final y uno desconocido se lee como «Cristal» (`Appearance.kt:17-26`). |
| AUTH | Cumple | Una lectura empezada antes de un bloqueo, de un desbloqueo rápido o de salir no rellena nada: `isCurrent` con `generation` y `lockEpoch` (`EntryForm.kt:728`, `VaultEntryViewModel` en `VaultEntryScreen.kt:413`). `keepOpenOnce` y `touch()` como el escáner del 2FA de la 2.0. |
| NETWORK | Sin cambios | Ningún host nuevo; solo `api.pwnedpasswords.com` (`net/PwnedPasswords.kt:24`, `net/HttpClient.kt:24`). |
| PLATFORM | Cumple salvo F-1 | Manifiesto igual que la 2.0 (`check-manifest.sh` «OK»; solo `versionName`). `FLAG_SECURE` antes de `setContent` (`MainActivity.kt:46` y `:50`; `SecureWindow.kt:23-24`) y en el escáner (`QrScanner.kt:13`). `FLAG_SHOW_WALLPAPER` en tiempo de ejecución, sin ventana translúcida (ningún `windowIsTranslucent` en `res/` ni en el manifiesto) y apagado en multiventana (`Glass.kt:151`, `WallpaperWindow.kt:77-88`). F-1: los toques se duplican al fondo. |
| CODE | Cumple | Lo leído de un QR es dato hostil: `WifiQr.read` corta a 4 096 caracteres (`WifiQr.kt:112`), solo se enseñan textos fijos o el tipo limpio y cortado a 32 (`shownSecurity`, `WifiQr.kt:433`; `QrView.kt:133`); un QR que no es de wifi no se enseña. La imagen se lee con sus topes, fuera del hilo principal y cancelable (`QrImageRead.kt:40-56`). Sin dependencias nuevas (`gradle/` sin cambios). |
| RESILIENCE | Sin cambios | Fuera del perfil (como en la 2.0). |
| PRIVACY | Cumple salvo F-1 y F-2 | No se lee la imagen del fondo (ningún `WallpaperManager`; los colores, de `dynamicColorScheme`, `Theme.kt:81-88`, de la 2.0). Sin permisos nuevos; la cámara es la de la 2.0. |

## 5. Comprobado sin hallazgo

- **`FLAG_SECURE` en toda la app y antes de `setContent`:** `MainActivity.kt:46` (`secureWindow()`) antes de `:50` (`setContent`); `applyWindowLook` solo toca `FLAG_SHOW_WALLPAPER` con `addFlags`/`clearFlags` y el fondo (`WallpaperWindow.kt:50-58`), nunca `FLAG_SECURE`. Los diálogos lo heredan, como en la 2.0.
- **Sin logs con datos:** ninguna línea añadida con `Log.`, `println`, `printStackTrace` ni `Timber` en el diff.
- **Manifiesto (B-2):** sin cambios en el fuente; `check-manifest.sh` sobre el APK: «OK». El `taskAffinity=""` sigue.
- **O-A3, la imagen del fondo:** ni `WallpaperManager` ni `getDrawable` del fondo en `app/src/main`; Android lo compone. La vista previa de Ajustes dibuja el degradado de la app, no el fondo (`AppearanceScreen.kt:300-304`, `:331-334`).
- **O-A4, sin ventanas translúcidas:** la actividad sigue opaca para el gestor de ventanas (tema sin `windowIsTranslucent`). El fondo transparente de la ventana (`WallpaperWindow.kt:53`) solo da canal alfa a su superficie. Al quitar el estilo se devuelve el `android:windowBackground` del tema (`WallpaperWindow.kt:64-71`, `MainActivity.kt:49`).
- **Multiventana:** `multiWindowNow` sigue `isInMultiWindowMode` con su oyente y lo quita en `onDispose` (`WallpaperWindow.kt:77-88`); el tema y la ventana usan el mismo valor (`MainActivity.kt:54`, `Theme.kt:58-65`).
- **La capa:** en «Transparente» se dibuja el degradado a `layerAlpha` (0,86 en «Noche») sobre toda la ventana (`Glass.kt:111-112`, `:228-230`).
- **El QR, dato hostil:** solo se muestra como texto plano. El SSID entra tal cual en el campo y, si no hay título, en el título; la lista lo enseña con `plainReportText` (R9/R10 de la 1.4.0) y el formulario avisa de los caracteres ocultos con el texto del Inspector (`wifiNameAlert`, `EntryForm.kt` y `VaultEntryScreen.kt:798`). El diálogo «¿Sustituir…?» no enseña valores del código (`VaultEntryScreen.kt:902-912`).
- **Secretos del wifi al salir y al bloquear:** `clear()` borra `wifiReplace`, `wifiReadMessage`, `wifiReads`, `lastWifiScan` y sube `generation` (`EntryForm.kt:809-813`); un resultado tardío no rellena nada; una lectura de imagen se cancela con la pantalla (`rememberCoroutineScope`, `VaultEntryScreen.kt:696-714`).
- **Muerte del proceso:** el inicio de la lectura vive en la sesión; un resultado que llega a una sesión nueva no tiene inicio (`finishWifiRead` da `null`) y no hace nada.
- **WPA3 y SAE:** siguen rechazados para la bóveda (`WifiRead.Unsupported`, sin cambios en el lector).
- **Dependencias y licencias:** `gradle/` sin cambios. El icono son trazos vectoriales (contornos de Liberation Serif, SIL OFL 1.1, sin la fuente en el APK).
- **«Líquido»:** ninguna aparición en `app/src/main/java`, la política, la ficha, el README ni las notas (solo «liquid» en las listas de palabras de `assets`, que son datos).
- **Contraste:** la capa y los paneles se miden en sus tests (`GlassContrastTest` y los de `Palettes.kt`), en verde.

## 6. Riesgos con nombre que siguen

- **R-21-1, transiciones de O-A4:** en la animación de abrir, cerrar o pasar a recientes, el sistema puede enseñar un instante lo que hay detrás (el lanzador u otra app) a través de la capa. Solo se prueba en el móvil (README, «Pruebas de la 2.1, Aspecto»).
- **R-21-2, primer fotograma en multiventana:** al entrar en pantalla dividida o en una ventana flotante, puede verse un fotograma con el fondo antes de que llegue `onMultiWindowModeChanged`. No hay datos de Clavero en juego (lo de detrás es de otra app o el fondo). Solo en el móvil.
- **R-21-3, F-1 en Android 8 a 13**, si el dueño elige la opción (a).
- **R-21-4, el texto del QR en memoria:** el `String` leído no se puede poner a cero (como en la 2.0, en el Inspector y en el 2FA).
- **C-2 y la nota del `code-review` de hoy:** la app con la que se generó la imagen de referencia del logo sigue en el historial de commits anteriores; no se reescribe sin *force push*.
- **Los de la 2.0** siguen igual (C-3, C-6 y las filas de `sdd-2.0/pendientes.md`).

## Revisión experto-seguridad-cripto

- Ninguna primitiva, clave, formato ni KDF cambia: `crypto/`, `backup/`, `VaultStore.kt` y `net/` sin diff. Los formatos se leen como en la 2.0.
- Entrada hostil (QR de cámara o imagen): topes antes de analizar (`WifiQr.kt:112`, 4 096; imagen con `readPhoto` y `MAX_IMAGE_BYTES`, `PhotoCleanScreen.kt:101-114`), trabajo fuera del hilo principal y cancelable (`QrImageRead.kt:43-46`), memoria agotada como error y no cierre (`QrImageRead.kt:52-53`), y bytes a cero en todos los caminos (`QrImageRead.kt:55`). Cumple la decisión (a) de la 1.4.0.
- Carreras (decisión (b)): un resultado tardío no se usa (`isCurrent`, `EntryForm.kt:728`; `generation` y `lockEpoch`).
- Secretos en memoria: solo en la sesión, con `toString` sin datos (`EntryForm.kt:166-181`, `QrImageRead.kt:20-28`).
- F-1 es un canal lateral de interacción, no criptográfico: Medio por el contexto (gestor de contraseñas), con arreglo de una línea en Android 14+.

## Revisión Ingeniero Móvil Senior

- Ventana: `FLAG_SHOW_WALLPAPER` en tiempo de ejecución es la vía correcta para no tocar el manifiesto (B-2). Devolver el `windowBackground` del tema, no el de `decorView`, evita envolverlo dos veces (`WallpaperWindow.kt:64-71`). Falta `setWallpaperTouchEventsEnabled(false)` en API 34+ (F-1).
- Ciclo de vida: `configChanges` evita recrear la actividad al cambiar de tamaño, así que `multiWindowNow` sigue el modo con el oyente de `ComponentActivity` y lo quita en `onDispose` (`WallpaperWindow.kt:77-88`). `DisposableEffect(windowLook)` aplica todo cada vez, así que el último aspecto gana (`MainActivity.kt:65-68`).
- Concurrencia: la imagen se lee en el ámbito de la pantalla (`VaultEntryScreen.kt:696`); `readingImage` desactiva los dos botones mientras dura; `wifiReads` es un `ConcurrentHashMap` y `generation`/`lastWifiScan` son `@Volatile`.
- Rendimiento: el fondo se dibuja con `drawBehind` y un solo `drawRect` en «Transparente» (`Glass.kt:228-230`); las tarjetas no difuminan. `wifiNameAlert` recorre el nombre en cada recomposición; es corto y no importa.
- Accesibilidad: regiones vivas `Polite` en el mensaje de la lectura (`VaultEntryScreen.kt:793`) y en la línea del estilo.

## Textos para el Abogado Digital (revisión final)

- F-2: según la decisión de F-1, una frase en `STYLE_TRANSPARENT_MORE` y en `docs/privacidad.html:37` sobre los toques y los fondos animados (hasta Android 13), o «desde Android 14» en la línea de «Transparente» si se elige la opción (b). Si se elige (b), también la línea de «Novedades» y de las notas de la versión de la 2.1 (`2026-11-24-clavero-2.1-notas.md`) y la viñeta del README.
- Ningún otro texto nuevo en esta auditoría. Los textos de la 2.1 que ya esperan su revisión siguen en `sdd-2.1/progress.md` y al final de `sdd-2.1/qr-wifi-branch-review.md` y `sdd-2.1/aspecto-branch-review.md`.
