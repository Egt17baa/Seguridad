# QR de wifi: repaso de la ronda de arreglos (`aa69e84`, `c4a93e1`)

Director, 23-11-2026, solo lectura. **Veredicto: con arreglos.** Cerrados I-1, I-2, M-1 a M-4, M-6 y M-7 (con sus tests; las mutaciones probables fallarían). M-5 no cerrado (N-I1).

## Important

- **N-I1.** `vault/EntryForm.kt:757-761`, `ui/screens/VaultEntryScreen.kt:792`: `qrBidi` dice «marcados como ⟨U+200B⟩» pero el formulario no enseña el nombre con marcas (el campo lo enseña tal cual). El proyecto ya lo da por falso en ese caso (KDoc de `QR_HIDDEN_ELSEWHERE`, `tools/ToolTexts.kt:337-343`; `hiddenMarks`, `tools/qr/Visible.kt:53`). Arreglo sin texto nuevo, como RT-24 (`WifiQrScreen.kt:212-218`): enseñar `visibleText(name)` justo encima del aviso.

## Minor

- **N-M1.** `PlatformSourcesTest.kt:1294` no fija que los dos «no se abrió» (`VaultEntryScreen.kt:768`, `:778`) vayan a `wifiReadNotOpened`.
- **N-M2** (anterior a la rama). `PhotoCleanScreen.kt:109` lanza `ImageTooBig` sin poner a cero los bytes leídos; también sirve para Limpiar foto.
- **N-M3.** TalkBack no anuncia el mensaje de la lectura: `liveRegion = LiveRegionMode.Polite` en `VaultEntryScreen.kt:789`, como `VaultBackup.kt:266`.
- **N-M4.** `README.md:433`: la prueba «sin tocar, no pregunta» debe cubrir también llegar a «Red wifi» desde otro tipo.
- **N-M5.** `EntryForm.kt:246`: el KDoc de `wifiReadMessage` dice «nothing of the code», pero `qrWifiOtherSecurity` lleva el tipo limpio y cortado.

## Rulings sobre lo decidido por el implementador

- (a) `qrBidi` en el formulario: no es fiel tal cual; sí, con el nombre enseñado con sus marcas (N-I1).
- (b) `QR_CAMERA_FAILED`, `NO_FILE_PICKER` y `PICKER_FAILED` debajo de los botones: correcto (falta fijarlo, N-M1).
- (c) El aviso también al editar una wifi existente: correcto y deseable (caso RT-24), con el arreglo de N-I1.
- (d) «justo debajo de los botones» en la prueba del README: correcto.

## Declinado

Doble pulsación (como en los demás selectores; no bloquea); ningún inicio se queda puesto (cancelar, no abrir, bloqueo y recreación lo liberan o lo toman); `ConcurrentHashMap` sobra pero no daña; el mensaje sigue tras un «Guardar» fallido (describe la última lectura); invisibles escritos tal cual en tests (costumbre del proyecto); «Red WiFi» en comentarios.

## Revisión experto-seguridad-cripto

`WifiReadStart` solo lo crea la sesión (`EntryForm.kt:174`, `:241`); `isCurrent` compara generación y época (`:726-727`). Puesta a cero (CWE-226) bien en M-1, `readAtMost` y `decodeQrFromImage`; falta N-M2. Nada explotable; N-I1 es de veracidad.

## Revisión Ingeniero Móvil Senior

El inicio vive en la sesión y sobrevive a la recreación; la imagen se lee en `rememberCoroutineScope` y se cancela con la actividad. `keepOpenOnce` se restablece en todos los caminos (`:682`, `:696`, `:767`, `:777`). Faltan N-I1 (MASVS, no suplantar) y N-M3 (accesibilidad).

## Textos para el Abogado Digital (revisión final)

`qrBidi` en el formulario de wifi (nueva o existente), con el nombre enseñado con sus marcas; debajo de los botones `QR_CAMERA_FAILED`, `NO_FILE_PICKER`, `PICKER_FAILED`, `QR_NO_CAMERA_PERMISSION` y los demás de la lectura; README `:65`, `:429` y `:433` (N-M4). Siguen los textos y las comillas anidadas de `qr-wifi-branch-review.md`.

## Repaso corto de la segunda ronda (`6a6c2d8`, `fd2412c`)

Director, 23-11-2026. **Veredicto: cerrado.** N-I1 (`vault/EntryForm.kt:755-766`, `ui/screens/VaultEntryScreen.kt:797-803`, tests `EntryEditSessionTest.kt:977-989`), N-M1 (`PlatformSourcesTest.kt:1289-1292`), N-M2 (`PhotoCleanScreen.kt:109-112`), N-M3 (`VaultEntryScreen.kt:793`), N-M4 (`README.md:433`) y N-M5 (`EntryForm.kt:244-249`), cerrados. El nombre con marcas sale también al editar, no es secreto (`Templates.kt:58`), se recompone al escribir y su coste es lineal y acotado. Sin problemas nuevos; detalle estético sin cambio pedido: el nombre en `titleMedium`, como `WifiQrScreen`. Sin textos nuevos.
