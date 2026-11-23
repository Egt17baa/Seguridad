# QR de wifi: revisión de toda la rama (`a2402fd..a7877bc`)

Director (`advisor`, el modelo que eligió el dueño con esfuerzo extra), 23-11-2026. Solo lectura; reproducción de I-1 con un programa Java contra las clases compiladas de `jvm-check`.

**Veredicto: con arreglos.** Ningún Critical. I-1 e I-2 antes de seguir; los menores también (regla del dueño: no se aplazan).

## Important

- **I-1. «¿Sustituir lo escrito?» sobre un formulario sin tocar (O-2, veracidad).** `vault/EntryForm.kt:328-332` pone «WPA» y «false» solo si el tipo inicial es WIFI; `changeType` (`:343-350`) no. Una entrada nueva que empieza con otro tipo («Añadir → Acceso» y la fila de tipos, o el «+» de «Códigos») y pasa a «Red wifi» tiene seguridad `""` y oculta `""`; el primer escaneo da `now != NEW_WIFI_VALUES` y pregunta. Falla la prueba del README «Con el formulario sin tocar, no pregunta». Arreglo mínimo en `scanWifi`: sin tocar si nombre y contraseña están vacíos, la seguridad es `""` o `"WPA"` y oculta `""` o `"false"`; tests desde LOGIN y desde TOTP.
- **I-2. Los mensajes de la lectura salen al final del formulario.** Los botones están en `ui/screens/VaultEntryScreen.kt:762-797` y el error en `:848`, unos 1 000 dp más abajo: tras un QR de WPA3, uno que no es wifi (O-1, obligatorio), sin permiso de cámara o una imagen sin código, no se ve ningún mensaje. Arreglo: el mensaje debajo de los dos botones, en una celda propia (`wifiReadMessage`) que no se mezcla con el `error` de «Guardar».

## Minor

- **M-1.** `ui/screens/QrImageRead.kt:39-46`: los bytes de la imagen no se ponen a cero si se cancela entre la lectura y la decodificación (o durante la lectura). Arreglo: `bytes` fuera del `try` y `finally { bytes?.fill(0) }` que cubra todo.
- **M-2.** `VaultEntryScreen.kt:703-716`: una imagen elegida tras un bloqueo se lee y decodifica igualmente (el resultado se descarta). Arreglo: `wifiReadBegan(since)` devuelve `Boolean` y la respuesta sale si es `false`.
- **M-3.** `PlatformSourcesTest.kt:1282`: si las respuestas tomaran `form.startWifiRead()` al llegar el resultado (y no el inicio guardado al abrir), ningún test fallaría. Arreglo: guardar el inicio dentro de la sesión (probado en la JVM) o fijar en el test las dos lecturas del inicio guardado y que `startWifiRead()` solo esté en los `onClick`.
- **M-4.** Falta el test de R-1 en el formulario: un tipo largo con una marca de dirección debe dar `qrWifiOtherSecurity("A".repeat(31) + "…")`.
- **M-5.** `EntryForm.kt:625`, `:629`: un nombre de red con invisibles o marcas de dirección entra sin aviso (el Inspector sí avisa, `QrView.kt:121`, `:143`). Riesgo bajo.
- **M-6.** README: `:429` dice «Red WiFi» y el selector «Red wifi» (`Vault.kt:47`); `:65` dice que lo leído solo queda «en los campos», pero mientras espera el diálogo está en memoria. Propuesta: «Lo leído solo queda en el formulario, en memoria, hasta que pulsas «Guardar», bloqueas o sales; el texto del código no se conserva.»
- **M-7.** Import sin usar `QrImageUnreadable` en `QrInspectScreen.kt:53`.

## Declinado por el director

El resultado en el `Bundle` pendiente si muere el proceso con el escáner delante (del marco, ya pasaba); bloqueo y desbloqueo vistos como uno (del diseño anterior, no ocurre en la práctica); el título no cambia con un segundo código (lo dice el dictamen); el selector del Inspector sin `keepOpenOnce` (ya decidido); `lastWifiScan` vive como `templateValues` (informativo); los botones también en el formulario dentro del Inspector (encaja con el diseño); Maestro (tarea 11).

## Revisión experto-seguridad-cripto

Sin criptografía nueva; WPA3 y SAE siguen rechazados (`WifiQr.kt:120-130`). Entrada hostil acotada (`WIFI_QR_MAX_CHARS`, `readPhoto`, `MAX_QR_DECODE_SIDE`, tipo limpio y cortado a 32). `isCurrent` compara la época de bloqueo (`EntryForm.kt:680-681`). Secretos solo en celdas del ViewModel borradas en `clear()` (`:742-744`); sin estado guardado, registros ni portapapeles. Nada explotable; M-1 y M-2 son defensa en profundidad, M-5 informativo.

## Revisión Ingeniero Móvil Senior

`configChanges` mantiene el diálogo y la lectura al girar (`AndroidManifest.xml:25`); el inicio de la lectura vive en el ViewModel (`VaultEntryScreen.kt:415-416`); el ámbito de la imagen se cancela al salir y en `LockedNotice` (`:699`). `keepOpenOnce` y `touch` como el escáner del 2FA (`:768-786`, `MainActivity.kt:68`). MASVS STORAGE, PLATFORM y PRIVACY correctos. Interfaz: I-2.

## Textos para el Abogado Digital (revisión final)

Nuevos (`tools/qr/WifiQr.kt:447-466`): `WIFI_SCAN_NOT_WIFI`, `WIFI_SCAN_NOT_WIFI_SIMPLE`, `WIFI_SCAN_REPLACE_TITLE_TYPED`, `WIFI_SCAN_REPLACE_TITLE_FILLED`, `WIFI_SCAN_REPLACE_TEXT`, `WIFI_SCAN_REPLACE`, `CANCEL`. Reutilizados en el formulario: `ScanQrLabel`, `QR_PICK_IMAGE`, `QR_READING_IMAGE`, `QR_NO_CAMERA_PERMISSION`, `QR_CAMERA_FAILED`, `QR_NOT_FOUND`, `QR_IMAGE_TOO_BIG`, `QR_IMAGE_TOO_LARGE`, `QR_IMAGE_DAMAGED`, `QR_WIFI_UNREADABLE`, `QR_WIFI_PASSWORD_WITHOUT_TYPE`, `QR_WIFI_NOT_ADDABLE`, `QR_WIFI_NAME_SPACES` y `qrWifiOtherSecurity` (tipo limpio y cortado a 32, también en el Inspector). Veracidad: I-1, I-2, M-6. Tipografía: las comillas anidadas «…«Modo sencillo»…» (la RAE recomienda « “ ” » en el segundo nivel); lo decide el Abogado.
