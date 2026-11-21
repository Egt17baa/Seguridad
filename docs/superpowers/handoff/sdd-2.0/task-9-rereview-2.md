# Tarea 9 (QR de wifi): re-revisión 2 (ronda 2 de arreglos)

He revisado `51b45db..cc89a5f` solo leyendo el código, sin ejecutar gradle. Las citas son de `cc89a5f`. Cubre n-1, n-2 y n-3 de la re-revisión 1 y el resto del diff.

## Veredicto

**Aprobada: 0 Critical, 0 Important, 0 Minor y 2 informativos.** n-1, n-2 y n-3 están cerrados. No veo ninguna regresión.

## Comprobación

### n-1: cerrado

- `QrPendingText.leftState` es un `StateFlow<Boolean>` sobre un `MutableStateFlow` privado (`QrPendingText.kt:24`, `:30`), y `left` lee su valor (`:32`).
- Las dos pantallas lo recogen con `collectAsStateWithLifecycle()` (`QrInspectScreen.kt:421` en el 2FA y `:497` en el wifi) y lo miran antes del estado de la bóveda (`:423`, `:499`).
- Con la actividad parada, la recogida se detiene. Al volver a STARTED se reanuda, recibe `true` y la pantalla vuelve al Inspector en ese momento, sin esperar al siguiente desbloqueo.
- **No hay carrera entre reinicio y recogida:** el valor nunca vuelve a `false`, porque no existe ningún reinicio. Solo pasa a `true` en `leftApp` (`:42`) y se queda así mientras viva el ViewModel. Un `StateFlow` siempre entrega el valor actual a quien empieza a recoger, así que una escritura hecha con la pantalla parada no se pierde. `collectAsStateWithLifecycle` toma como valor inicial el actual, así que una pantalla compuesta después de salir ya lo ve.
- **No vuelve al Inspector cuando no debe:**
  - Con `keepOpenOnce` (el selector de «Importar» de la puerta o el escáner), `AppContainer.leftApp` no llama a `appLeft`, así que `leftApp()` no corre y `left` sigue en `false`.
  - Después de una entrega, `clear()` deja `text` en `null` y saca el texto del registro. `leftApp()` solo marca si `text != null`, así que salir con el formulario abierto no echa al usuario. Lo fija `QrHandoffTest.aTextAlreadyHandedOverOrClearedIsNotMarkedAsLeft`.
- **El test es real:** `QrHandoffTest.aCollectorHearsThatTheAppWasLeft` recoge el flujo con `UnconfinedTestDispatcher` y oye `[false, true]` tras `appLeft()`. Además, `leavingTheAppDropsEveryPendingTextAndSaysSo` mira `leftState.value`. El cableado en Compose lo fija `PlatformSourcesTest`: las dos pantallas leen el estado, y ninguna lee ya `vm.pending.left`.
- **jvm-check:** `QrPendingText` importa `kotlinx.coroutines.flow`, que jvm-check ya tiene (`kotlinx-coroutines-core`).

### n-2: cerrado

- `WifiRead.Usable.written` (`WifiQr.kt:62`, `:135-138`) lleva el tipo del código en mayúsculas cuando no es el que se guarda (`WPA2`). `QrKind.Wifi.written` lo lleva al Inspector (`QrContent.kt:131`).
- «Cifrado» enseña `kind.written ?: kind.security` (`QrView.kt:132`), pasado por `visibleText` (`f`, `QrView.kt:123`).
- **Lo guardado y el QR siguen en WPA:**
  - `parse` devuelve `config`, con `security = "WPA"`, y el formulario lo guarda así (`EntryForm.applyWifiQr`);
  - `written(c)` usa `c.security`;
  - lo fijan `WifiQrInspectTest` (`classifyQr(...).security == "WPA"`) y `WifiQrTest.anUnknownTypeIsShownButNotKept`, de la ronda 1 (`encode` da `T:WPA`).
- El aviso de WEP sigue mirando `kind.security`. `toString` de `Usable` no enseña `written`, aunque tampoco sería un secreto.

### n-3: cerrado

- `QR_WIFI_NAME_SPACES` dice ahora «…empieza o termina con un espacio en blanco…» (`WifiQr.kt:391`), y el anexo cita el texto nuevo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:461`).
- La frase que describe el caso en el anexo sigue diciendo «Con un nombre que empieza o termina con un espacio:». Es una descripción, no el texto de la app. No hace falta cambiarla.
- Ninguna palabra prohibida.

### Regresiones

- `WifiRead.Usable` y `QrKind.Wifi` ganan un campo con valor por defecto. Solo los crean `WifiQr.kt:135` y `QrContent.kt:131-132`.
- La igualdad de `QrKind.Wifi` incluye `written`, y solo cambia para `WPA2`. Las suites del informe están en verde: 2293 tests en la app y 2249 en jvm-check.
- No cambian nada más:
  - el lector (salvo `written`), las reglas de I-2, la ocultación de los textos y la regla de salir de la app;
  - permisos, dependencias, componentes, red e `Intent`.

## Issues

### Critical

Ninguno.

### Important

Ninguno.

### Minor

Ninguno.

### Informativos (sin cambio)

- **i-1.** El tipo se enseña en mayúsculas, no tal cual: `T:wpa2` sale como «WPA2» y `T:wpa`, como «WPA». Es lo mismo que ya hacían los tipos no admitidos (`type.uppercase`), y la caja del tipo no lleva ningún secreto ni cambia lo que el código hace. Lo acepto.
- **i-2.** Hay una ventana de microsegundos. Si `onStop` corre entre la composición del formulario y su `LaunchedEffect` de entrega, el formulario aplica el texto que recibió como parámetro, se marca `left` y la pantalla vuelve al Inspector; el formulario se borra al salir (`form.left`). Falla cerrado y ya existía en la ronda 1. Lo acepto.

## Revisión experto-seguridad-cripto

- n-1 no cambia qué se borra ni cuándo. El texto pendiente se sigue soltando en `onStop`, con la regla del bloqueo al salir; solo cambia que la pantalla lo diga al volver. `left` es un booleano: no lleva datos.
- n-2 solo cambia lo que se enseña, pasado por `visibleText`. Lo guardado y lo escrito no cambian, así que no se abre ningún desacuerdo con otros lectores: el QR de Clavero sigue diciendo `T:WPA`, que la app de ZXing lee igual.
- n-3 es solo un literal.
- **Veredicto de seguridad: aprobado.**

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** `collectAsStateWithLifecycle` es la forma recomendada de recoger un `StateFlow` en Compose. Respeta STARTED y no depende de que la bóveda cambie de estado.
- **Hilos:** la escritura llega desde `onStop`, en el hilo principal, y `MutableStateFlow` es seguro entre hilos.
- **Estado:** el valor vive en el ViewModel, sin estado guardado, y nunca vuelve a `false`. Es lo que se quiere: una vez soltado el texto, el flujo termina.
- **Compose:** la recogida va antes de cualquier `return`, así que las llamadas componibles están en un orden estable.
- **Tests:** el test de comportamiento del flujo y el pin del cableado se complementan. El informe deshizo los arreglos y vio fallar los tres tests que los fijan.
- **MASVS:** sin cambios en STORAGE, PLATFORM ni CODE.
