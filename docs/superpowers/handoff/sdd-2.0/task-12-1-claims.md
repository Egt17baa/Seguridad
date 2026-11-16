# Clavero 2.0, tarea 12 (paso 1): tabla de afirmaciones

Cada cosa que dirán sobre la 2.0 el README, la política de privacidad (`docs/privacidad.html`), la ficha de Google Play (`docs/ficha-google-play.md`), `docs/PUBLICAR.md` y el borrador de las notas de la versión (`docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md`), con su evidencia en el código. Hecha **antes** de editar ningún documento («Ruling (advisor, T10 cierre y T12 paso 1)», `progress.md`). Base: `ef0f09d`.

Las rutas de código son relativas a `app/src/main/java/app/seguridad/`. «Doc» dice dónde irá: R (README), P (política), F (ficha), N (notas de GitHub y de Play), U (`PUBLICAR.md`). «Android» dice si depende de la versión de Android: lo que depende va **en condicional o con la versión** en el texto.

Fuentes de las afirmaciones: el diseño (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md`), los dictámenes del Abogado (`sdd-2.0/abogado-2.0-inicial.md`, O-1 a O-11 y R-1 a R-12; `sdd-2.0/abogado-2.0-cristal.md`, O-C1 a O-C8 y R-C1 a R-C10) y las filas 6, 7, 8, 22, 36 y 40 de `sdd-2.0/pendientes.md`.

## 1. Versión

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| V-1 | La versión es la 2.0.0 (`versionName`). | `app/build.gradle.kts:35` (se sube en el commit de cierre) | No | R, N | Plan, tarea 12 |
| V-2 | El `versionCode` es el número de ejecución del workflow; no está en el repositorio y no se toca. | `app/build.gradle.kts:33-34`; `.github/workflows/build-apk.yml:111-112`, `:315`, `:324`; `docs/PUBLICAR.md:86` | No | U (sin cambios) | Ruling, punto 3 |
| V-3 | Ningún test fija el `versionName`; solo el flujo `.maestro/p20-version-y-licencias.yaml:1` y `:21` dice «Clavero 1.4.3», y ningún test lee ese flujo (el único test que lee `.maestro/` es `CsvFormatsTest.kt:75`, que lee un CSV). Por el Ruling, `.maestro/` no se toca: va a la tarea 11. | `grep -rn versionName app/src/test` vacío; `CsvFormatsTest.kt:70-76` | No | R («Pruebas de la 2.0») | Ruling, punto 3 |

## 2. Formato v4 y el paso desde la 1.4.x (O-3, fila 7)

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| F-1 | La bóveda pasa al formato v4 (`seguridad-vault` versión 4) en el primer desbloqueo con la contraseña maestra en la 2.0. | `vault/Vault.kt:192` (`VERSION = 4`); `vault/VaultStore.kt:223` (KDoc del paso pendiente); `ui/AppNavHost.kt:250` | No | R, N | Diseño «Formato v4»; fila 7 |
| F-2 | Hasta ese desbloqueo, la huella no se ofrece, con la línea «Clavero 2.0 necesita la maestra una vez para pasar la bóveda al formato nuevo.». | `vault/Vault.kt:300` | No | R, N | Diseño; O-3 |
| F-3 | Con carpeta de copia automática, antes del cambio se hace la copia `seguridad-antes-de-2-0-AAAAMMDD-HHMMSS.sgbak` en ella; después, un aviso dice que las versiones anteriores ya no abren la bóveda ni sus copias nuevas, con el nombre de la copia. | `vault/AutoBackup.kt:114`, `:120`, `:738`; `ui/screens/VaultUpgrade.kt:53-58` | No | R, P, N | O-3 con carpeta (Ruling T1) |
| F-4 | Sin carpeta, un diálogo avisa («Después del cambio, las versiones anteriores de Clavero no abrirán esta bóveda ni sus copias nuevas.») y ofrece «Exportar una copia antes» (destacada) o «Seguir sin copia»; la copia exportada es la del archivo de antes del cambio, que sí abren las versiones anteriores. | `ui/screens/VaultUpgrade.kt:38-45`, `:63-68` | No | R, N | O-3 |
| F-5 | La clave de la huella de la primera bóveda sigue valiendo después del paso (no hay que volver a activarla). | Diseño, «Decisiones de Claude»; `android/BiometricVault.kt:118`, `:124` (`perVaultName`: la primera conserva el alias y las preferencias de antes) | No | R | Diseño |
| F-6 | Las copias `.sgbak` de una bóveda v4 llevan la marca 3; la 1.4.x las rechaza con su texto «La copia es de una versión más nueva de Clavero» sin tocar nada. Una bóveda que aún es v3 sigue haciendo copias con la marca 2. | `vault/Backup.kt:35`, `:41`, `:44`, `:58`; diseño «Copias (`seguridad-backup`)» (test contra el lector de la 1.4.3, tarea 1) | No | R, N | O-3; diseño |
| F-7 | La 2.0 sigue leyendo las bóvedas v2 y v3 y las copias con marca 1 y 2, `SG1:` y `.sgenc`. | `vault/Vault.kt:201` (`VERSION_3`); `vault/Backup.kt:38`; plan, «Restricciones globales» | No | R | CLAUDE.md |
| F-8 | La copia `seguridad-antes-de-2-0-` entra en lo que borra «Hacer una copia nueva y borrar las anteriores» tras cambiar la maestra (A6), como las de antes de importar. | `settings/Settings.kt:223-227` (`listedCopies`); `vault/BackupSchedule.kt:25-27`; `ui/screens/SettingsScreen.kt:399` | No | P, R | Ruling T1 dudas 4 a 6; fila 8 |
| F-9 | Tope de una bóveda: 24 MB de datos; una copia v4 admite hasta 33 MB por archivo dentro (100 MB en total, 2 000 archivos). | `vault/VaultLimits.kt:29`; `vault/Backup.kt:51`, `:60-62` | No | R | Ruling T1-1 |

## 3. Varias bóvedas (O-1, O-10.1, fila 8)

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| B-1 | Hasta 10 bóvedas, cada una con su contraseña maestra, su archivo, su huella y su copia automática; solo una abierta a la vez. | `vault/VaultLimits.kt:34`; `vault/VaultsTexts.kt:10`; `vault/VaultRegistry.kt:89-94` | No | R, F, N, P | Diseño «Varias bóvedas» |
| B-2 | El nombre de cada bóveda va cifrado dentro de ella; antes de abrirla se ve «Bóveda N» y su color. | `vault/VaultRegistry.kt:34-39` (sin nombre), `:44-51` (`displayName`) | No | P, R | Opción A del dueño |
| B-3 | El registro `files/vaults.json` guarda sin cifrar, por bóveda, un identificador local, su color y su orden, y cuál se abrió la última; nunca un nombre ni un secreto. | `vault/VaultRegistry.kt:39`, `:42`, `:58-63` | No | P | O-1; O-10.1; fila 8 |
| B-4 | La primera bóveda sigue en `files/vault/`; las nuevas, en `files/vaults/<id>/`. Su número se deduce también de esas carpetas. | `vault/VaultRegistry.kt:87-94`, `:163-172` | No | P (solo «cuántas hay») | Diseño |
| B-5 | Cada bóveda tiene su propia clave de la huella en el Keystore y sus propias preferencias de la huella (la clave envuelta, su IV y si está en StrongBox). | `android/BiometricVault.kt:116-124`, `:478-480` | StrongBox desde Android 9 (`:103-105`) | P | Diseño |
| B-6 | Borrar una bóveda (Bóvedas → «Borrar esta bóveda») pide su maestra y escribir su nombre; borra su carpeta, su clave de la huella y sus ajustes; no toca las copias ya hechas. La única bóveda no se borra desde ahí. | `AppContainer.kt:677-716`; `vault/VaultRegistry.kt:122-134`; `vault/VaultsTexts.kt:44`, `:76` | No | P, R | O-10.6; R-9 |
| B-7 | «Importar como bóveda nueva» crea otra bóveda con lo que tiene la copia (y su maestra), sin cambiar ni borrar las demás. | `vault/VaultsTexts.kt:34-37` | No | R | R-9 |
| B-8 | La pantalla de desbloqueo empieza con la última bóveda abierta. | `AppContainer.kt:488`, `:563`; `vault/VaultRegistry.kt:79-80` | No | R | Diseño «Recordar la última bóveda» |
| B-9 | Los lugares de los selectores son de todo el móvil, no de cada bóveda: «He olvidado la contraseña maestra» (borrar la bóveda desde la pantalla de bloqueo) los olvida todos; «Borrar esta bóveda» en «Bóvedas» no los olvida. | `ui/screens/VaultScreen.kt:494-497`; `core/PickerPlaces.kt:97-100`; `AppContainer.kt:677-716` (sin `forgetAll`) | No | P | O-10.7 |
| B-10 | Ningún texto presenta varias bóvedas como forma de ocultar o negar una. | (Regla de redacción; nada en el código) | — | R, F, N, P | O-1 |

## 4. Papelera, deshacer e historial (O-2, O-10.2, O-10.6)

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| T-1 | Todo borrado de una entrada la manda a la papelera; «Deshacer» durante 5 s. | `vault/Trash.kt:9-12`, `:27` | No | R, F, N, P | Diseño |
| T-2 | Lo de la papelera de más de 30 días se borra al abrir la bóveda; también con «Vaciar papelera» o «Borrar para siempre». Con el reloj del móvil adelantado más de 2 días, ese desbloqueo no vacía nada y el siguiente sí. | `vault/Trash.kt:17-24`, `:39-46` | No | R, P | O-2; Rulings T3 |
| T-3 | Lo de la papelera va en las copias y no sale en la lista, la búsqueda, «Salud» ni los avisos. | `vault/Trash.kt:9-12` | No | P, R | O-2; O-10.2 |
| T-4 | Vaciar la papelera borra los archivos de sus adjuntos. | `vault/Trash.kt:36-37` | No | R | Diseño |
| T-5 | Historial por entrada: hasta 20 versiones y 64 KiB por entrada (se quitan las más viejas); «Restaurar esta versión» y «Borrar historial»; los campos ocultos y las contraseñas siguen ocultos en él. | `vault/VaultLimits.kt:23`, `:26`, `:95`; `vault/EntryHistory.kt:31` | No | R, F, N, P | O-2; diseño |
| T-6 | Las copias ya hechas conservan lo borrado (papelera, historial, bóveda); la rotación puede quitar la más antigua. | `vault/BackupSchedule.kt:16-27` (la rotación borra solo las suyas, por id) | No | P | O-2; fila 30 |
| T-7 | La identidad y los contactos no van a la papelera: se borran del todo, como en la 1.4.x. | Ruling T3 duda 2 | No | (solo si se nombra) | Ruling T3 |

## 5. Organización, búsqueda, plantillas y formulario

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| O-1 | Carpetas anidadas (hasta 500 y 5 niveles), vistas guardadas (hasta 50) y etiquetas de 8 colores (hasta 200 con color). | `vault/VaultLimits.kt:30-33`; `vault/VaultData.kt:94` | No | R, F, N | Diseño |
| O-2 | Ordenar por nombre, modificación, creación y uso; «Recientes» y «Favoritos» arriba; filtros de un toque. | `vault/EntryQuery.kt:60` (`RECENT_MAX`) | No | R | Diseño |
| O-3 | La búsqueda perdona erratas y mira el título, el usuario, el host de la URL, el servicio y la cuenta de un 2FA, las etiquetas, las carpetas y los nombres de los campos no ocultos; nunca contraseñas, campos ocultos, notas, palabras de una frase semilla ni la clave de un 2FA. | `vault/EntryQuery.kt:64-71`; `vault/FuzzySearch.kt` | No | R | Diseño; nota de corrección T4 |
| O-4 | Plantillas: «Tarjeta bancaria» (titular, número, caducidad, CVV y PIN opcionales), «Red wifi» (nombre, contraseña, tipo de seguridad WPA/WPA2/WPA3, WEP o abierta, red oculta), «Licencia» (producto, clave, correo, fechas) y «Pasaporte» (número, país, fechas, titular). Los secretos se ocultan y se copian como contraseñas. Sin marcas ni logotipos. | `vault/Templates.kt:1-8`, `:23-31`, `:44-73`; `vault/Vault.kt:40-47` | No | R, F, N, P | O-9; R-3; R-4 |
| O-5 | Solo una bóveda v4 guarda esos tipos. | `vault/Vault.kt:27-28`, `:44-47` | No | R | Diseño |
| O-6 | Campos personalizados (texto, oculto, número y fecha), hasta 30 por entrada. | `vault/VaultLimits.kt:20-22` | No | R, N | Diseño |
| O-7 | Notas con formato sencillo (negrita, cursiva, títulos y listas), sin enlaces, HTML ni imágenes; el texto se guarda tal cual. No se llama «Markdown». | `vault/LimitedMarkup.kt` | No | R | R-5 |
| O-8 | «Duplicar» una entrada (sin adjuntos ni historial). | Diseño «Duplicar»; tarea 6 (`⋮`) | No | R | Diseño |
| O-9 | Caducidad: los avisos solo salen dentro de la app; sin notificaciones ni permisos. | `vault/Expiry.kt`; `app/src/main/AndroidManifest.xml:4`, `:6` (solo Internet y cámara) | No | R, P, F | O-4 |
| O-10 | El formulario: generador junto a los campos de contraseña, fuerza mientras se escribe, título desde el dominio de la URL (sin red), «¿Guardar los cambios?». | Tarea 7 (`vault/FormHelpers.kt`) | No | R | Diseño |
| O-11 | Al pegar un `otpauth://` o una frase BIP-39 en un campo, propone el tipo: mira el texto que recibe el campo, nunca lee el portapapeles. | `vault/FormHelpers.kt:18`, `:58-62`, `:93` | No | P, R | O-10.8; R-11 |

## 6. La lista y la ficha

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| L-1 | Deslizar una entrada a la izquierda la edita y a la derecha la manda a la papelera (con confirmación); las dos acciones están también en el menú «⋮» y en TalkBack. | `ui/screens/EntryRow.kt:148-157` | No | R, N | Diseño |
| L-2 | La vibración al deslizar usa la del sistema, sin el permiso `VIBRATE`. | `ui/screens/EntryRow.kt:152`, `:178`; `AndroidManifest.xml:4`, `:6` | No | P (Permisos sin cambios) | R-7 |
| L-3 | Mantener pulsada una entrada empieza la selección: mover a carpeta, etiquetar, favorita, papelera. | `ui/screens/EntryRow.kt:148-150` | No | R | Diseño |
| L-4 | Tocar un código 2FA lo copia, con un círculo de los segundos que quedan. | `ui/screens/EntryRow.kt:154-155` | No | R | Diseño |
| L-5 | Tras «Copiar usuario», al volver a la app ofrece «Copiar contraseña» durante 30 s. | `tools/TwoStepCopy.kt:10-13` | No | R | Diseño; Ruling T6 |

## 7. Copias automáticas (O-4, O-5, O-10.5, fila 8)

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| C-1 | Hasta 3 carpetas por bóveda, cada una con su número de copias y su calendario: con cada cambio, diaria, semanal o mensual. | `vault/VaultLimits.kt:38`; `vault/BackupSchedule.kt:14`; `settings/Settings.kt:415-416` | No | R, F, N, P | Diseño «Copias» |
| C-2 | Las copias con calendario solo se hacen al abrir Clavero o la bóveda: sin trabajo en segundo plano ni notificaciones. Si no se abre, no hay copia. | `vault/BackupSchedule.kt:8-12`, `:80-96`; `vault/AutoBackup.kt:294`, `:457`, `:505`; `vault/BackupTargetTexts.kt:7` | No | R, P, F, N | O-4; O-10.5 |
| C-3 | Una copia con calendario se salta si la bóveda no cambió desde la última copia de esa carpeta (nunca la primera, una de A6 ni una «con cada cambio»). | `vault/AutoBackup.kt:537-540`; `vault/VaultStore.kt:427-450` | No | R, P, N | D-3 del dueño; fila 8 |
| C-4 | La rotación y A6 funcionan por carpeta: cada una solo borra las copias que apuntó ella, por su identificador. | `settings/Settings.kt:223-227`; `vault/BackupSchedule.kt:16-27` | No | R, P | Diseño |
| C-5 | Aviso de la última comprobación: «Comprobaste una copia por última vez hace N días.» (desde los 30 días) o «Aún no has comprobado ninguna copia.»; cuenta desde la comprobación, no desde la copia; las copias de antes de la 2.0 o de otra bóveda no cuentan. | `vault/BackupCheckTexts.kt:80-102`; `vault/BackupCheck.kt:140-145` | No | R, N | O-5 |

## 8. QR de wifi (O-4, fila 22)

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| Q-1 | Una entrada «Red wifi» enseña su QR («Enseñar QR»), con el aviso «Quien vea este código ve la contraseña y puede conectarse a tu wifi.». | `tools/qr/WifiQr.kt:350`, `:356` | No | R, F, N | O-4 |
| Q-2 | El Inspector de QR reconoce un QR de wifi y ofrece «Añadir a la bóveda»; `WPA2` se guarda como WPA; `WPA3`, `SAE` y los que no lee con seguridad no se añaden. | `tools/qr/WifiQr.kt:42`, `:121-122`, `:378-397` | No | R, N | Decisión del dueño 14-11 |
| Q-3 | El texto de un QR que espera ante la puerta de la bóveda (la contraseña de un wifi o la clave de un 2FA) se borra al salir de la app (cambio para el 2FA). | `MainActivity.kt:118-119`; `AppContainer.kt:225-227`; `tools/qr/QrHandoff.kt:85-91` | No | N, R | Fila 22 |
| Q-4 | Sin `Intent` para conectarse ni permisos nuevos. | `AndroidManifest.xml:4`, `:6` | No | P | Ruling T8 cierre (encargo de T9) |

## 9. Ayuda: modo sencillo, guía, misiones, Ajustes

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| A-1 | Modo sencillo: encendido al crear la primera bóveda en una instalación nueva, apagado al actualizar desde la 1.x y al empezar desde una copia; Herramientas solo enseña cifrar texto, cifrar archivos, el generador y «Salud»; lo demás aparece al apagarlo en Ajustes. No oculta avisos ni ajustes. | `settings/Settings.kt:81-89`, `:145-161`; `tools/ToolCatalog.kt:34-40` | No | R, N, P (ajuste guardado) | Diseño; O-7 |
| A-2 | Guía: la completa en una instalación nueva y una corta de novedades al actualizar desde la 1.x; se puede saltar y volver a ver desde Ajustes; saltarla no salta «Antes de empezar». | `settings/Guide.kt:14-20`, `:37-63`; `settings/Settings.kt:90-98` | No | R, N, P (ajuste guardado) | Decisión del dueño 14-11; O-7 |
| A-3 | «Salud» enseña sugerencias de una en una, con «Ahora no»; ninguna se conecta a internet; se pueden apagar en Ajustes («Sugerencias de «Salud»»). | `health/Missions.kt:41-50`; `settings/Settings.kt:99-100` | No | R, N, P | O-6; R-2 |
| A-4 | Buscador en Ajustes. | `settings/SettingsSearch.kt` | No | R, N | Diseño |
| A-5 | Los avisos del arranque salen de uno en uno; el aviso del paso a v4 sale en cualquier pantalla con la bóveda abierta. | `settings/Opening.kt:60`; `ui/AppNavHost.kt:250` | No | N | Fila 36 |

## 10. Aspecto «Cristal», temas y logo (O-C2, O-C4 a O-C6, R-C8, R-C10, fila 6)

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| G-1 | El aspecto «Cristal» (por defecto, también al actualizar) y «Sólido», el de la 1.x. | `settings/Settings.kt:63-67` | No | R, F, N | Diseño |
| G-2 | El difuminado de barras y diálogos funciona **desde Android 12**; en Android 8 a 11 los paneles son más opacos y no difuminan. | `ui/theme/Glass.kt:105`, `:252`, `:277` | **Sí** | R, F, N | O-C4 |
| G-3 | Seis paletas («Noche», «Océano», «Bosque», «Atardecer», «Grafito», «Arena»), «Personalizado» (12 tonos de acento y de fondo, tres intensidades) y, **desde Android 12**, «Colores del fondo de pantalla». | `settings/Appearance.kt:20-27`; `ui/theme/Theme.kt:80-84`; `ui/theme/Palettes.kt:128`; `ui/screens/AppearanceScreen.kt:144` | **Sí** (fondo de pantalla) | R, N | Diseño |
| G-4 | Con «Colores del fondo de pantalla», la app usa los colores que Android calcula del fondo de pantalla; no lee la imagen ni pide permisos. | `ui/theme/Theme.kt:80-84` (`dynamic…ColorScheme`) | **Sí** | P, R | O-C5 |
| G-5 | «Personalizado» ajusta los tonos para que el texto tenga contraste suficiente; «Reducir transparencias» y «Alto contraste» dan paneles opacos. (Sin «accesible», «cumple WCAG» ni «garantizado».) | `settings/Appearance.kt:29-33`; `settings/Settings.kt:71-74` | No | R | O-C6 |
| G-6 | Tras actualizar, una línea dice cómo volver a un aspecto sin transparencias. | `settings/Settings.kt:75-80` | No | N | R-C8 |
| G-7 | Los ajustes de aspecto (modo, estilo, paleta, tonos personalizados, «Reducir transparencias», «Alto contraste») se guardan sin cifrar en el almacenamiento privado; no son secretos ni salen del móvil; el logo y los temas no añaden permisos. | `settings/Settings.kt:44`, `:63-80`; `AndroidManifest.xml:4`, `:6` | No | P | R-C10; fila 6 |
| G-8 | Icono nuevo (solo «Icono nuevo»: sin «único», «original», «registrado», ®, ™ ni por qué). | `docs/play/icono-512.png`; commit `61b8d3f` | No | N, F, U | O-C2 |

## 11. Textos de error (filas 36 y 40)

Los textos salen de las constantes; las filas 37 a 39 y 42 pueden cambiarlos en la revisión de la rama (segunda pasada de los documentos).

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| E-1 | La pantalla de bloqueo dice «No es una bóveda compatible», «Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id» o «Parámetros de cifrado no válidos» cuando es eso, y «Contraseña incorrecta» en lo demás; «Contactos» dice lo mismo. | `vault/Vault.kt:294-297`; `crypto/Crypto.kt:87`, `:90` | No | N | Fila 36 (I-2, n-1, n-5 de T10C) |
| E-2 | Una bóveda o copia «Alto» que no cabe en el móvil dice «sin memoria» y no «parámetros no válidos». | `crypto/Crypto.kt:90` y `checkSane` (ronda 2 de T10C) | No | N | Fila 36 (n-1) |
| E-3 | Ganan una línea de qué hacer: la huella al desbloquear («… Escribe la contraseña maestra.»), adjunto de más de 5 MB, copia dañada («… Elige otra copia.»), archivo de más de 40 MB al importar una copia y copia de una versión más nueva («… Actualiza Clavero.»). | `vault/VaultStore.kt:71`, `:1674`; `vault/Attachments.kt:29`; `vault/Backup.kt:79`, `:82`; `vault/BackupCheck.kt:180` | No | N, R | Filas 36 y 40 |
| E-4 | El README cita `COPY_DAMAGED` como «La copia está dañada: no se puede importar. Elige otra copia.» y `BACKUP_DAMAGED` como «La copia está dañada o no es una copia de Clavero. Elige otra copia.». | `vault/BackupCheck.kt:180`; `vault/Backup.kt:79` | No | R (líneas 320 y 329) | Fila 40 |

## 12. Lo que no cambia (permisos, red, segundo plano)

| # | Afirmación | Evidencia | Android | Doc | Fuente |
|---|---|---|---|---|---|
| S-1 | Los mismos permisos que la 1.4.3: Internet y cámara (más los de biometría de `androidx.biometric`); sin `VIBRATE`, notificaciones, fotos ni ubicación. | `app/src/main/AndroidManifest.xml:4`, `:6`; `scripts/check-manifest.sh` (lista exacta de la 1.2.2) | No | P, R, U, N | O-10.10; R-7 |
| S-2 | La única conexión sigue siendo Pwned Passwords al pulsar «¿Filtrada?» o «Comprobar filtraciones»; ninguna misión la lanza. | `health/Missions.kt:50`; política, «La única conexión» | No | P, F | O-6 |
| S-3 | Ningún servicio en segundo plano, `WorkManager` ni notificación: lo que no se hace al abrir la app, no se hace. | Manifiesto (sin `service`, sin permiso de notificaciones); `vault/BackupSchedule.kt:8-12` | No | P, R, F | O-10.5 |
| S-4 | Ninguna dependencia nueva. | `gradle/libs.versions.toml` sin cambios de la tarea 12; `:app:checkLicense` | No | R, N | Plan |

## 13. Lo que el móvil guarda sin cifrar (política, O-10.1, R-C10, fila 8)

Sacado de los campos serializados de `settings.json` (`settings/Settings.kt:40-101`, `:170-195`; `vault/BackupSchedule.kt:29-60`, `:98-119`) y de `vaults.json` (`vault/VaultRegistry.kt:39-42`), más las preferencias privadas (`android/BiometricVault.kt:116-121`; `android/PrefsPlaceStore.kt:13`). Todo en el almacenamiento privado de la app, fuera de las copias de Android. Comparado con la fila 8: lo marcado «nuevo» no estaba en ella.

| # | Qué | Campo | En la fila 8 |
|---|---|---|---|
| U-1 | Cuántas bóvedas hay, su color, su orden y la última abierta (sin nombres) | `VaultRegistry.kt:39`, `:42` | Sí |
| U-2 | Ajustes de toda la app: bloqueo, borrado del portapapeles, avisos aceptados | `Settings.kt:42-56` | (de antes) |
| U-3 | Aspecto: modo, estilo, paleta, tonos de «Personalizado», «Reducir transparencias», «Alto contraste» y si queda la línea de después de actualizar | `Settings.kt:44`, `:67-80` | **Nuevo** (R-C10, fila 6) |
| U-4 | Modo sencillo, la guía vista (dice que el móvil tuvo la 1.x) y «Sugerencias de «Salud»» apagadas | `Settings.kt:89`, `:98`, `:100` | Sí |
| U-5 | Por bóveda: el coste de Argon2id y la hora más alta vista (A11) | `Settings.kt:172`, `:179` | **Nuevo en la lista** (de la 1.x, ahora por bóveda) |
| U-6 | Por bóveda y por carpeta de copia: la dirección de la carpeta, cuántas copias conservar, su calendario, la fecha y el error de la última copia, los identificadores de las copias que hizo (automáticas, de antes de importar y de antes de la 2.0), la marca de cambio de D-3 (SHA-256 de fecha, coste y `vaultTag`) | `BackupSchedule.kt:30-59` | En parte (la marca, sí; la dirección, el calendario, las fechas y los identificadores, **nuevo en la lista**, aunque las de una carpeta ya estaban en la 1.x) |
| U-7 | Por bóveda: las mismas listas de hasta 6 carpetas que ya no se usan (para seguir rotando sus copias si se vuelven a elegir) | `Settings.kt:184-198` | **Nuevo** |
| U-8 | Por bóveda: la petición de A6 (su identificador, un UUID; el resumen ya enseñado, con las direcciones de las carpetas y lo que se borró; el SHA-256 de la sal) y, por carpeta, su resultado (contadores) | `BackupSchedule.kt:37-53`, `:98-119`; `A6Text.kt:55-67` | Sí (el resumen con direcciones, **nuevo**) |
| U-9 | Por bóveda, si la copia automática se apagó al crear una bóveda nueva | `Settings.kt:183` | **Nuevo** (sin dato del usuario) |
| U-10 | Por bóveda, su clave de la huella envuelta por el Keystore, su IV y si está en StrongBox | `BiometricVault.kt:116-118`, `:478-480` | **Nuevo en la lista** (de la 1.x, ahora por bóveda) |
| U-11 | Los lugares de los selectores (direcciones de documentos, con su nombre) | `PrefsPlaceStore.kt:13`; política de la 1.4.2 | (de la 1.4.2, ya en la política) |

## 14. Afirmaciones que el código contradice o no respalda (se avisa, no se arregla)

| # | Qué | Evidencia | Qué hacen los documentos |
|---|---|---|---|
| X-1 | La política de la 1.4.2 dice «Al borrar la bóveda se olvidan todas» (los lugares de los selectores). Con varias bóvedas, «Borrar esta bóveda» en «Bóvedas» no los olvida; solo «He olvidado la contraseña maestra». | `AppContainer.kt:677-716` frente a `ui/screens/VaultScreen.kt:494-497` | La política dice lo que hace el código (B-9), como pide O-10.7. Si se quiere que «Borrar esta bóveda» los olvide, es código (revisión de la rama). |
| X-2 | R-6 del Abogado inicial («tras «Seguir sin copia», una línea en Ajustes ofrece hacer una copia ahora, hasta que haga una»): no hay una línea propia; lo más cercano es «Aún no has comprobado ninguna copia.» (Ajustes y bóveda) y la misión «Comprueba una copia». | `ui/screens/VaultUpgrade.kt:45`; `vault/BackupCheckTexts.kt:80`; `health/Missions.kt:44` | Ningún documento dice que haya ese recordatorio. Al Abogado final y a la revisión de la rama. |
| X-3 | El diseño dice que el modo sencillo enseña «generador, cifrar texto y archivo, y «Salud»»; el código coincide. Sin contradicción. | `tools/ToolCatalog.kt:34` | — |
