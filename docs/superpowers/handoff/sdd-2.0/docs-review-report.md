# Clavero 2.0: repaso final de documentos de la tarea 12 (21-11-2026)

Paso 5 del §3 del parte del 20-11-2026 (`2026-11-20-clavero-2.0-parte.md`) y punto 3 de la §7 de `docs/auditorias/2026-11-21-auditoria-2.0.md`. Base: `56d5751`. Solo cambian documentos; ningún archivo de `app/src/main` ni de los tests.

## 1. Qué cambió y dónde

| Documento | Cambio |
|---|---|
| `README.md:28` | Los adjuntos: «desde la 2.0, como mucho 1 998 por bóveda, los que caben en una copia, contando los de la papelera» (fila 84, `vault/Attachments.kt:52-54`, `:60-61`). |
| `README.md:397` | «Pruebas de la 2.0» dice que las pruebas salen también de la §10 de la auditoría. |
| `README.md:407` | Tras la frase de B-4 que fija `PublishedTextsTest.theTwoStepCopyIsSaidWhereItHappens` (sin tocarla), «Lo mismo con Clavero en una ventana flotante: la oferta sale en su ventana (auditoría, B-4).» (fila 12 y fila 106). |
| `README.md:416-423` (nuevas) | Ocho pruebas en el móvil: memoria con una bóveda grande (filas 55, 58 y 62, con AC-E1 de la 83), la copia lenta de leer (fila 67), B-2 (fila 85), B-1 (fila 86), A-1 y B-3 (`run-as … ls shared_prefs`), D-1 con una carpeta que no contesta (fila 89), D-2 (fila 95) y «Añadir adjunto» cerca de 1 998 (fila 84). Cubren toda la fila 106 y las filas 85, 86 y 12. Los textos citados salen tal cual de `vault/Vault.kt:301-303`, `vault/BackupCheckTexts.kt:108-110`, `vault/Vault.kt:291-293`, `vault/VaultStore.kt:1757-1758`, `ui/screens/VaultUpgrade.kt:58-60`, `vault/VaultsTexts.kt:28` y `vault/Attachments.kt:52-54`. |
| `docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md` | Cabecera con la pasada del 21-11 (las líneas nuevas llevan entre corchetes el hallazgo de la auditoría que las respalda). Líneas nuevas: «Seguridad: … otra app ya no puede poner una pantalla suya en lugar de la de Clavero (sobre todo en Android 8 a 10).» (B-2); el tope de 1 998 adjuntos en «Errores que dicen qué hacer» (fila 84); la espera de 2 minutos del paso al formato nuevo (D-1). B-4 ya estaba (`:28`). «Novedades» de Play sin cambios (mide menos de 500). |
| `docs/superpowers/handoff/2026-11-12-clavero-2.0-traspaso.md` | Apunta al parte del 20-11; tareas 8, 10, 11 y 12 al día; últimas suites (2 603 y 2 538 en `53809e3`); «Siguiente paso» con los pasos 6 a 10 del parte (y el `versionCode` en la puerta); C-3 y las pruebas del móvil en «Pendiente del dueño». |
| `docs/superpowers/handoff/sdd-2.0/pendientes.md` | Cada fila abierta dice su destino: en el móvil (con la línea del README que la cubre), para el Abogado, Maestro (tarea 11) o el controlador. **Fila 80 cerrada** (parte D, `9926a32`; D-1 a D-4 en las filas 89, 95, 94 y 96). **Fila 53 medida:** la descripción completa de la ficha mide 6 980 caracteres con el Markdown y 6 948 sin las negritas; Play admite 4 000. No la acorto (ver §2). |
| `CLAUDE.md` | El estado pasa a «la 2.0 casi terminada (21-11-2026)»; la línea de la 2.0 nombra el parte vigente, `pendientes.md`, la auditoría y el informe del ataque; tabla «Paso de la 2.0» y «Decisiones de la 2.0» (las del dueño y los Rulings del director), antes de lo de la 1.4.0, sin borrar nada de versiones anteriores. |
| `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:127` | Nota de corrección de B-4: opciones (c) y (a); (b) descartada en la 2.0, con el motivo del Ruling (`advisor-t12-audit-fixes.md:58-73`). |
| `docs/auditorias/2026-11-17-ataque-2.0.md:125`, `:268` | RT-25 cita la línea del rechazo, `vault/BackupCheck.kt:258` (`if (copyExtraTags(entries) > COPY_LIST_MAX) throw CryptoFailure(COPY_DAMAGED)`), junto a la de la función, `:256`. |
| `docs/auditorias/2026-11-21-auditoria-2.0.md` | §7, paso 6 (puerta del APK): frente a la 1.4.3 cambian `versionName`, `android:taskAffinity=""` y, en el APK firmado, el `versionCode` que pone la CI (`.github/workflows/build-apk.yml:112`, `:324`). Las tres citas de `README.md:416` pasan a `:424` (la línea se movió con las pruebas nuevas). §7, paso 3: dice que el repaso está hecho y falta su revisión. |

Comprobado: `:app:testDebugUnitTest` con `*Text*`, `*Name*`, `*Readme*`, `*Doc*`, `*Legal*`, `*Published*` y `*Workflow*` (entre ellos `PublishedTextsTest`, `FinalLegalReviewTest`, `AppNameTest`, `ImportTextsTest`, `PublishedFilesNameNoModelTest`, `WorkflowHardeningTest`): **342 tests, 0 fallos, 0 errores, 0 omitidos**, con el cerrojo. `git diff | grep -niwE` con los nombres de modelo: nada nuevo (solo la frase ya existente «Decisión de Claude» del diseño, que no es un modelo).

## 2. Lo que no pude confirmar o no hice

- **La ficha de Play (fila 53):** pasa de 4 000 en unos 2 950 caracteres. Acortarla cambia lo que la ficha dice: es redacción del Abogado (con el visto bueno técnico) y del dueño. Queda en la fila 53 para la revisión final.
- **B-1 en el móvil:** el id `536627314` y el salto a «Generador» antes del arreglo son los de `final-audit-b.md:146-151`; no los he comprobado en un móvil.
- **D-2 en el móvil:** que el *snackbar* `VAULT_NEW_NEEDS_OPEN` llegue a verse depende de que «Bóvedas» siga a la vista tras el bloqueo; la prueba del README lo dice como condicional.
- **La línea B-2 de las notas** («sobre todo en Android 8 a 10») sale de la re-lectura de la parte D (`final-audit-d.md`, «Re-lectura de B-2 y B-1»); en Android 11 o posterior no está confirmado que fuera vulnerable, y la frase no lo afirma.
- **`progress.md`** no tiene aún la línea de la ronda de la auditoría ni la de sus menores; no lo toco (es del controlador).
- No toqué `final-audit-*.md` ni los informes de repaso.

## Revisión experto-seguridad-cripto

- **Veracidad (CWE-451 en sentido débil):** cada texto citado en las pruebas nuevas es la constante del código: `UPGRADE_COPYING` (`app/src/main/java/app/seguridad/vault/VaultStore.kt:1757-1758`), `UPGRADE_AUTO_COPY_NOT_DONE` (`ui/screens/VaultUpgrade.kt:58-60`, enseñado en `:132`), `VAULT_NEW_NEEDS_OPEN` (`vault/VaultsTexts.kt:28`, usado en `ui/screens/VaultPickerScreen.kt:223`), `ATTACHMENTS_COUNT_WOULD_PASS` (`vault/Attachments.kt:52-54`), `LIST_NO_MEMORY`, `VAULT_NO_MEMORY` (`vault/Vault.kt:301`, `:291`) y `COPY_NO_MEMORY` (`vault/BackupCheckTexts.kt:108`).
- **D-1:** la espera es de 120 s (`vault/PendingUpgrade.kt:14`); el README y las notas dicen «2 minutos» y «como mucho», que es lo que hace: solo se corta la espera, la copia sigue.
- **B-2:** `android:taskAffinity=""` en `app/src/main/AndroidManifest.xml:28`; la línea de las notas no promete más que el lanzador no traiga la tarea de otra app.
- **A-1 y B-3:** la prueba de `shared_prefs` sale de `android/BiometricVault.kt:197-203` (`commit = true` y `deleteSharedPreferences`, salvo la primera bóveda, `vault/VaultRegistry.kt:56`).
- **RT-25:** `vault/BackupCheck.kt:258` es el rechazo del exceso de etiquetas; `:237` `copyExtraTags`; `:256` `checkCopyEntries`.
- **C-3:** `CLAUDE.md` y el traspaso dicen lo que decidió el dueño: los secretos no se borran y no se aprueba ninguna firma que no haya lanzado él.
- Ningún secreto, dato del dueño ni nombre de modelo nuevo en los documentos.

## Revisión Ingeniero Móvil Senior

- **B-4:** `AppContainer.kt:223-227` (`leftForAnotherApp` llama a `twoStepCopy.markLeft()`); en multiventana la actividad sigue iniciada y la oferta sale en la ventana de Clavero, también en una ventana flotante, que es multiventana. A pantalla completa, `MainActivity.onStop` bloquea.
- **D-2:** con «Al salir», el tope de 2 minutos con un selector abierto es `AWAY_BACKSTOP_MS` (`vault/VaultStore.kt:1821`); de ahí los 2 minutos y medio de la prueba.
- **B-1:** `MainActivity` sustituye el *intent* antes de `setContent` (`audit-fixes-report.md`, §2, punto 2); la prueba usa la orden `adb` de `final-audit-b.md:146-148`, con el `applicationId` `io.github.egt17baa.seguridad` (`app/build.gradle.kts:30`, sin sufijo en depuración).
- **Puerta del APK:** el `versionCode` lo pone la CI con `-PversionCode` (`.github/workflows/build-apk.yml:112`, `:324`); en el clon limpio, los dos APK de depuración llevan 1.

## Textos para el Abogado Digital (revisión final)

- **Notas de la versión** (`2026-11-15-clavero-2.0-notas.md`), líneas nuevas: «Seguridad: al abrir Clavero desde su icono, otra app ya no puede poner una pantalla suya en lugar de la de Clavero (sobre todo en Android 8 a 10).» (B-2; no debe prometer más ni dar versiones de Android como garantía); «o de 1 998 adjuntos, los que caben en una copia, contando la papelera» (fila 84); «si en el paso al formato nuevo la carpeta de la copia automática no contesta, Clavero espera como mucho 2 minutos, lo dice y te deja exportar una copia a otro sitio o seguir sin copia.» (D-1).
- **README:** `:28` (el tope de 1 998 adjuntos); `:407` (la frase añadida de la ventana flotante); `:416-423` (las pruebas nuevas, que citan `UPGRADE_COPYING`, `UPGRADE_AUTO_COPY_NOT_DONE`, `VAULT_NEW_NEEDS_OPEN`, `ATTACHMENTS_COUNT_WOULD_PASS`, `LIST_NO_MEMORY`, `COPY_NO_MEMORY` y `VAULT_NO_MEMORY`).
- **La ficha de Play (fila 53):** 6 948 caracteres sin Markdown frente a 4 000; hay que acortarla.
- **La nota de corrección de B-4 del diseño** y la puerta del APK de la auditoría son documentos técnicos, no textos para el usuario.
