# Clavero 2.0, tarea 12 (paso 1): re-revisión de la segunda pasada de los documentos

Revisor de solo lectura, en un clon propio en `6d3b884` (sin gradle ni commit). He leído el informe `sdd-2.0/task-12-2-report.md`, el diff `e0ed4e3..6d3b884` de los cinco documentos, de la tabla y de `pendientes.md`, y he comprobado el código tras las dos rondas de la rama. Rutas de código relativas a `app/src/main/java/app/seguridad/`.

**Veredicto: Aprobado con tres menores.** I-1 y m-1 a m-8 están arreglados, y lo de §6 coincide con el código. Ninguno de los tres menores es de lo publicado para el usuario (dos son del plan de pruebas del README y uno de una frase repetida en cuatro documentos). Según la regla del proyecto («los menores se arreglan»), se arreglan antes del Abogado; no hace falta otra re-revisión.

## 1. I-1 y m-1 a m-8

- **I-1, arreglado.**
  - Política: «Las copias que ya hiciste pueden seguir teniéndolo: las que exportaste, hasta que las borres; las automáticas, hasta que la rotación las quite.»
  - README («Papelera, deshacer e historial») y `notas.md` (línea «Papelera»): lo mismo.
  - Coincide con `vault/TrashTexts.kt:20` (`COPIES_KEEP_IT`, «pueden conservarlo; la copia automática quita las más antiguas»).
- **m-1, arreglado:** F-5 cita `VaultV4Test.kt:382-389` y el diseño `:66`.
- **m-2, arreglado:** T-5 cita `vault/TrashTexts.kt:79-82` y `ui/screens/EntryHistoryScreen.kt:79`, `:156`; T-7 cita `vault/VaultStore.kt:1162-1169` y `:1186`. Las líneas son las buenas.
- **m-3, arreglado:** S-4 con el diff contra `a3ac942`; O-2 con `vault/VaultData.kt:90`.
- **m-4, arreglado:** G-1 dice «Sólido» con `ui/screens/AppearanceScreen.kt:80`.
- **m-5, arreglado:** «difuminan el fondo de la app que tienen detrás», con las palabras de `STYLE_MORE` (`AppearanceScreen.kt:81-82`). Está en G-2, en el README («Aspecto» y la prueba de «Cristal»), en la ficha y en las notas.
- **m-6, arreglado:** §15 tiene M-1 a M-9, una fila por afirmación. Comprobadas `ui/screens/EntryFields.kt:443`, `ui/AppNavHost.kt:330`, `ui/screens/SettingsScreen.kt:766`, `vault/TrashTexts.kt:102`, `vault/FormHelpers.kt:182` y `vault/History.kt:4-19`.
- **m-7, arreglado.**
  - La política añade cuatro cosas: si queda por enseñar algún aviso de después de actualizar, si la copia automática se apagó al crear otra bóveda, las copias que quedan por borrar con los fallos ya dichos, y el StrongBox de todo el móvil.
  - U-2, U-8, U-9 y U-10 están al día.
  - Las dos rondas no añaden campos guardados: el diff `e0ed4e3..6d3b884` de `settings/Settings.kt`, `vault/BackupSchedule.kt`, `vault/VaultRegistry.kt`, `vault/VaultData.kt` y `android/` no tiene ningún `val` serializado ni ninguna preferencia nuevos.
  - Con eso, la lista está completa frente a los campos serializados.
- **m-8, arreglado:** la identidad y los contactos se borran del todo (`vault/VaultStore.kt:1162-1169`). Lo dicen la política (el párrafo de la papelera y «Tus derechos»), el README y las notas.
- **m-9:** ya no quedan nombres de modelos en `docs/`, salvo en el propio informe; ver n-3.

## 2. Lo de §6, frente al código tras las dos rondas

- **Memoria (fila 38):**
  - E-1 y E-2 citan `KDF_OUT_OF_MEMORY` (`crypto/Crypto.kt:94-96`, ya sin «Alto») y `KDF_NO_MEMORY_NOW` (`:114`), según el coste (`kdfOutOfMemoryText`, `:111`).
  - `masterUnlockErrorText` deja pasar los dos (`vault/Vault.kt:296-299`).
  - Las notas no citan el texto viejo. Bien.
- **Fila 45:** K-4 cita `KDF_COST_KEPT` (`vault/VaultsTexts.kt:122-125`), con «se queda». La prueba del README empieza con ese texto. Bien.
- **Filas 37, 39 y 42:**
  - K-2 (`vault/BackupCheckTexts.kt:105-113`, `ui/screens/VaultBackup.kt:186`, `vault/BackupCheckSession.kt:130`, `AppContainer.kt:984-989`) y K-3 (`AppContainer.kt:991-995`, `masterRefusal`) son ciertas.
  - K-7 (`ui/screens/VaultBackup.kt:282`, `ui/screens/VaultUpgrade.kt:66-67`) y K-8 también.
  - E-3 añade `ATTACHMENTS_TOTAL_WOULD_PASS` (`vault/Attachments.kt:36-38`, usado en `vault/VaultStore.kt:1320`).
- **Fila 26:** `wifiSecurityLabel` da «WPA/WPA2» (`vault/Templates.kt:30`) y `WIFI_WPA3_NOTE` está en `:37`. O-4, Q-2, el README («Plantillas») y las notas lo dicen igual. Bien.
- **B I-2:** el botón es «… (automáticas, de antes de importar y de antes de la 2.0)» (`tools/ToolTexts.kt:508`), y la política, el README y las notas lo citan así.
  - La frase de 1.4.0 que fija `FinalLegalReviewTest` sigue palabra por palabra; la siguiente la completa. Cierto.
- **B m-2:** «sus ajustes» al borrar una bóveda es cierto con `forgetOrphanVaults` (`AppContainer.kt:748`), y B-6 lo cita.
- **C I-1 y A I-1 (B i-1):** K-6 cita `ui/screens/VaultPickerScreen.kt:168` y K-5 cita `vault/VaultsTexts.kt:99-114` (el color de la bóveda). Ciertas.
- **C I-2:** M-1 dice que «Ver en grande» no se ofrece en el historial (`EntryFields.kt:443`). Las notas lo nombran solo para la lista y la ficha. Bien.
- **Selectores (fila 46):** opción (b), sin código. La frase de la política sigue siendo cierta y X-1 lo dice.

## 3. §15, §16 y README:31

- **§15 y §16:** muestreadas 21 referencias de las dos secciones y de las filas movidas a `a9efdcd`, entre ellas `vault/VaultStore.kt:1699`, `vault/VaultLimits.kt:37`, `:103`, `ui/screens/VaultUpgrade.kt:66-73` y `ui/screens/VaultBackup.kt:282-288`. Todas apuntan a lo que dicen.
  - K-4 y K-5 citan `vault/Vault.kt` y `AppContainer.kt` sin línea (`afterMasterUnlock`, `switchTo`, `onMasterChanged`), pero con el nombre de la función y el commit. Vale.
- **README:31:** usa las palabras de la guía (`settings/Guide.kt:43`): «se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, y solo si hubo cambios». Lo mismo dicen la política, la ficha, las notas y, en las «Novedades», «(con la app abierta)». Ver n-1.

## 4. `pendientes.md`

- **Hechas, con un commit que existe y dice lo que la fila dice:** 17 (`035752d`), 18 (`4f0d71b`), 26 (`a5d333f`), 30 (`9e97517`), 37 (`e4f6aa0`, `af15de2`), 38 (`2a03ae6`), 39 (`c2da53c`), 42 (`848f118`) y 45 (`3024ba3`, `cda4590`).
- **Aceptadas con su evidencia y el Ruling del director:** 2, 20, 21, 31, 32 y 33.
- **46:** al Abogado final, con el Ruling A m-1 (b). **47:** decidida por el dueño (16-11-2026).
- **51 (nueva):** sensata. Coincide con la prueba del README (`README.md:410`), salvo la referencia (n-2).
- **52 (nueva):** sensata y bien prudente: mirar la historia de `MAX_TAGS` (`vault/EntryQuery.kt:12`) antes de rechazar copias reales. Va al ataque final.
- **8:** cerrada con las cuatro cosas de m-7. Bien.

## 5. Reglas

- Palabras prohibidas y O-C2, O-C4 y O-C6: en lo añadido solo aparecen dentro de la lista de reglas de `notas.md`.
- No hay nombres de otras apps, ni en la ficha ni en las «Novedades» (416 caracteres).
- No hay datos del dueño.

## Hallazgos

- **n-1 (Minor), «solo si hubo cambios» contra «nunca la primera»:** `README.md:31` dice en la misma línea «… y solo si hubo cambios» y después «se salta si la bóveda no ha cambiado … (nunca la primera)».
  - El código hace la primera copia de una carpeta aunque no haya cambios, y también una de A6 o una que esperaba (`vault/AutoBackup.kt`, `runDue`, la condición de D-3: `t.lastAt != null && t.copiedMark != null`).
  - La guía (`settings/Guide.kt:43`), aprobada en la parte C, lo simplifica; el README, la política (párrafo de las copias), la ficha (las dos líneas) y las notas lo copian.
  - La frase no oculta nada: dice que hay menos copias de las que hay, no más. Pero el README se contradice.
  - **Arreglo:** en el README, «… al abrir Clavero o al desbloquearla, y se saltan si la bóveda no ha cambiado desde la última de esa carpeta (nunca la primera)». En la política y la ficha, «y se saltan si no hubo cambios»; en las notas, lo mismo. Lo decide el Abogado si quiere las mismas palabras que la guía.
- **n-2 (Minor), dos pruebas del README.**
  - **La prueba de la fila 30** («2.0, borrar y copias») espera que, tras «borrar una entrada y hacer tres cambios más», ya no esté la copia que tenía la entrada.
    - Una entrada borrada va a la papelera, y la papelera va en cada copia nueva (`vault/Trash.kt:9-12`; T-3). Así que todas las copias siguen teniendo la entrada y la prueba fallaría sin que haya error.
    - Además, con «Conservar las 3», tras el borrado y dos cambios más ya no queda ninguna copia anterior al borrado: con tres cambios el resultado es el mismo, pero la frase cuenta mal.
    - **Arreglo:** «borrar una entrada **para siempre** (o vaciar la papelera) y hacer dos cambios más: ninguna copia automática la tiene ya; una exportada antes sí».
  - **La prueba de la memoria** dice «(fila de la memoria)» en lugar de «(fila 51)». **Arreglo:** «(fila 51)».
- **n-3 (Minor), nombres de modelos en el informe:** `docs/superpowers/handoff/sdd-2.0/task-12-2-report.md:62-66` nombra los modelos al contar lo que quitó, así que `docs/` no queda limpio. **Arreglo:** decir «los nombres de los modelos (cuatro palabras y los identificadores `claude-…`)» sin escribirlos.
  - La duda 1 del informe (`.claude/skills/advisor/SKILL.md`) la decide el controlador. Es configuración del proyecto, no un documento publicado.

## Revisión experto-seguridad-cripto

- **Borrado:** ningún texto promete más de lo que hace el código. Las copias «pueden seguir teniéndolo», como `vault/TrashTexts.kt:20`, y la identidad y los contactos se borran del todo (`vault/VaultStore.kt:1162-1169`). La prueba de n-2 debe usar «Borrar para siempre», porque la papelera va en las copias (`vault/Trash.kt:9-12`).
- **Lo guardado sin cifrar:** la lista de la política cubre todos los campos serializados (`settings/Settings.kt:42-100`, `:172-194`; `vault/BackupSchedule.kt:30-59`, `:100-118`; `vault/VaultRegistry.kt:39`, `:42`) y las preferencias de la huella (`android/BiometricVault.kt:109-121`, `:477-480`). Las rondas no añaden ningún campo.
- **Memoria y coste:** los documentos dicen lo que hace el código. Con poca memoria la bóveda se abre con el coste que tenía, sin bajarlo (`KDF_COST_KEPT`, `vault/VaultsTexts.kt:122-125`), y los textos dependen del coste (`crypto/Crypto.kt:111`).
- **A6:** el botón y la política nombran las copias de antes de la 2.0, que A6 de verdad borra (`tools/ToolTexts.kt:508`).
- Sin cambios de formato, de red ni de permisos en esta pasada.

## Revisión Ingeniero Móvil Senior

- **Lo que depende de Android, en condicional:** el difuminado del fondo de la app desde Android 12 (`ui/theme/Glass.kt:105`, `:252`; `ui/screens/AppearanceScreen.kt:81-82`) y los colores del fondo de pantalla desde Android 12 (`ui/theme/Theme.kt:80`).
- **Copias:** solo con la app abierta, sin segundo plano (`vault/AutoBackup.kt`, `runDue`; `settings/Guide.kt:43`). El matiz de la primera copia está en n-1.
- **Ciclo de vida:** los diálogos de «Bóvedas» se cierran con el bloqueo (`ui/screens/VaultPickerScreen.kt:168`), y K-6 lo dice.
- **Pruebas para la tarea 11:** con n-2 arreglada, las tres nuevas (letra al 200 %, memoria y borrar con copias) se pueden pasar a flujos.
- La prueba de la memoria necesita un móvil con poca memoria: queda a mano.

## Textos para el Abogado Digital (revisión final)

- La política: la frase de la papelera y las copias (I-1); la excepción de la identidad y los contactos (m-8); las cuatro líneas nuevas de lo guardado sin cifrar (m-7); la frase del calendario (n-1); la frase de A6 con las copias de antes de la 2.0.
- El README: «Papelera, deshacer e historial», «Copias en varias carpetas» (n-1), «Plantillas» (WPA/WPA2 y la línea de WPA3), «Aspecto», «Formatos» y las pruebas nuevas.
- La ficha: «Aspecto» y las dos líneas del calendario (n-1).
- Las notas de GitHub y de Play, enteras.
- Lo que ya iba: la fila 46 (los selectores) y las filas 19 y 30 (la redacción de `COPIES_KEEP_IT`).
