# Clavero 2.0: la puerta del APK, revisión del director (21-11-2026)

**Pregunta:** ¿cumple la rama `ccr-0aa27f95-u038cd` en `84e222a` la puerta del APK (parte del 20-11-2026, §3, paso 8; auditoría de la 2.0, §7, paso 6), de modo que se pueda pedir al dueño el OK para el PR a `main`?

**Cómo:** en solo lectura y sin Gradle. He leído `CLAUDE.md`, el parte (§3), la §7, la §9, la §11 y la §12 de la auditoría, las dos últimas líneas de `progress.md` (`:294-295`), `pendientes.md` entero, el dictamen del Abogado (`abogado-2.0.md`), su informe de lo aplicado y mis dos informes anteriores (`advisor-t12-audit-fixes-recheck.md` y `advisor-t12-docs-review.md`). Sobre el clon limpio del scratchpad de la sesión (`clean4`, en `176c6a8`, clonado de este repositorio) he contado los XML de resultados de las dos suites y el SARIF de lint. Sobre sus dos APK de depuración (el de la 2.0 y el de `v1.4.3`, compilado de `a3ac942`), he vuelto a sacar `aapt2 dump xmltree`, `dump permissions` y `dump badging`, y he ejecutado `scripts/check-manifest.sh`: no es Gradle ni escribe en el repositorio. También he pasado `git grep` y `git log` sobre la rama. `git status` estaba limpio antes de escribir este informe, que es lo único que toco.

Veredicto: **la puerta se abre: se puede pedir al dueño el OK para el PR**, después de un solo commit de documentos con los menores N-1 a N-5 de abajo. Son de estado y de una cita, no tocan el código ni piden otro clon limpio ni otra revisión mía.

## 1. Los ocho puntos

| # | Punto | Resultado | Evidencia |
|---|---|---|---|
| 1 | Último clon limpio en verde, de `176c6a8` | **Cumple** | XML del clon: app **2 604 tests, 0 fallos, 0 errores y 0 omitidos** (195 archivos); jvm-check **2 539, 0, 0 y 0** (178 archivos). `clean4` está en `176c6a8` (`git rev-parse HEAD`), con `origin` en este repositorio y sin cambios. `clean4-app.log`: las seis tareas (`testDebugUnitTest`, `assembleDebug`, `lintDebug`, `checkLicense`, `checkFragmentVersion`, `checkAssets`), «BUILD SUCCESSFUL», «60 actionable tasks: 60 executed», `app exit 0`. `clean4-jvm.log`: Gradle 9.6.0 (el *wrapper*), «5 executed», `jvm exit 0`. Lint: 0 errores y 18 avisos, todos de versiones y configuración. `git diff --stat 176c6a8 84e222a` solo toca `progress.md` (2 líneas) |
| 2 | `versionName` 2.0.0 | **Cumple** | `app/build.gradle.kts:35`. `versionCode` 1 por defecto (`:34`); la CI pone el número de la ejecución (`.github/workflows/build-apk.yml:112`, `:324`). `aapt2 dump badging`: `versionCode='1' versionName='2.0.0'`, `minSdkVersion` 26 y `targetSdkVersion` 36 |
| 3 | Manifiesto y permisos iguales a los de la 1.4.3 salvo `versionName` y `taskAffinity=""` (B-2) | **Cumple** | `git diff v1.4.3 HEAD -- app/src/main/AndroidManifest.xml`: el comentario de B-2 (`:20-22`) y `android:taskAffinity=""` en `MainActivity` (`:28`), nada más; `res/xml` sin cambios. Los volcados del scratchpad salen, byte a byte, de esos dos APK (`cmp`). `diff v143-perms.txt clean4-perms.txt`: iguales (los siete renglones de `EXPECTED_PERMISSIONS`, `scripts/check-manifest.sh:27-33`). `diff` de los dos `xmltree` sin los números de línea: solo `versionName` (`1.4.3` → `2.0.0`) y `A: android:taskAffinity(0x01010012)=""` dentro de `app.seguridad.MainActivity`. `badging`: solo la primera línea, por `versionName`. `check-manifest.sh` sobre el APK de la 2.0: «OK (…, debug)»; sobre el de la 1.4.3: «ERROR: app.seguridad.MainActivity sin android:taskAffinity=""… (B-2)», así que la comprobación muerde |
| 4 | Ningún `@Ignore` | **Cumple** | `grep -rnE '^\s*@Ignore' app/src jvm-check`: vacío. El único `assumeTrue` (`app/src/test/java/app/seguridad/tools/photo/JpegTest.kt:378`) es de la 1.3.0, sigue igual que en `v1.4.3` y no omitió nada: 0 omitidos en las dos suites |
| 5 | Ningún nombre de modelo ni dato del dueño; solo el seudónimo Egt17baa | **Cumple** | `git grep -nIiwE` con los nombres de la fila `modelo` de `.claude/skills/advisor/SKILL.md:16`, en todo el árbol: solo esa skill, la expresión de búsqueda permitida (`sdd-2.0/branch-rereview-compilacion.md:42`, la excepción de `PublishedFilesNameNoModelTest.kt:45-47`) y una palabra de la lista EFF de `app/src/main/assets/`, que no es un nombre de modelo y que el test excluye. Otras familias de modelos: nada, salvo el verbo «llama». Correos del árbol: el de contacto del proyecto (`docs/privacidad.html:26`, `SECURITY.md:7`), las direcciones `noreply` y datos de prueba (`banco.es`, `example.com`…). Ningún dispositivo, sistema, ruta de usuario ni dato personal en las 61 393 líneas añadidas desde `a3ac942`. Commits de `a3ac942..HEAD`: 451, todos de `Egt17baa <Egt17baa@users.noreply.github.com>` (370) o de `Claude <noreply@anthropic.com>` (81). Fuera de la atribución, solo los asuntos de `3189f75` y `bc7c266` nombran un modelo: es C-2, riesgo aceptado (`pendientes.md:110`). Las líneas de atribución quedan fuera por la parte C (`final-audit-c.md:114`, `:213`) |
| 6 | `pendientes.md` al día | **Cumple** | Las 108 filas tienen destino y estado. Abiertas o pendientes solo las del móvil (3, 10 a 13, 13 bis, 13 ter, 23, 28, 29, 34, 44, 51, 55, 58 y 106), las de Maestro, que van al segundo PR (27, 35 y 48), y las del dueño (15 y 16), como pide la cabecera (`:3`). Las del Abogado, cerradas: 4, 5, 14, 19, 41 (R-7, `:49`), 46 y 53 (`:61`). La 49 (`:57`), la fecha, hecha. La 105 (`:113`), los menores de los repasos, hecha. La 106 (`:114`) junta las pruebas en el móvil de la ronda. Los riesgos con nombre C-2, C-3 y C-6 están en las filas 101 a 103 |
| 7 | Cambios del Abogado aplicados | **Cumple** | Véase §2 |
| 8 | Mis menores M-1 a M-4 y O-1 a O-8 y M-1 a M-7 del repaso de documentos, aplicados | **Cumple**, con una cita que se movió después (N-3) | Véase §3 |

## 2. Los cambios del Abogado (punto 7)

- **R-7 es solo texto.** `3f65f92` toca `vault/VaultStore.kt` y su test, nada más. La constante nueva está en `VaultStore.kt:93-100`. El único cambio de flujo es un `try`/`catch` alrededor de `VaultCrypto.read(known, key)` (`:762-768`), que convierte un `CryptoFailure` en otro `CryptoFailure` con `FINGERPRINT_DATA_UNREADABLE`:
  - `VaultCrypto.read` solo lanza `CryptoFailure()` con el texto por defecto: la profundidad del JSON, `SerializationException` e `IllegalArgumentException` (`vault/Vault.kt:458-479`, con `copy = false`). `openAny` no puede fallar ahí, porque `VaultCrypto.authenticated` acaba de autenticar los mismos datos bajo el mutex (`VaultStore.kt:750-753`). No se tapa ningún texto propio;
  - el tipo sigue siendo `CryptoFailure`, así que `launchUnlockWithKey` no llama a `onWrongKey` y la huella se conserva (`VaultStore.kt:142-154`). `biometricUnlockError` deja pasar el mensaje (`:107-110`);
  - un `OutOfMemoryError` no es `CryptoFailure` y sigue por su `catch`, con `key.fill(0)` y `VAULT_NO_MEMORY` (`:771-774`). El desbloqueo con la maestra no pasa por aquí.
- **El test fija el camino.** `VaultStoreTest.kt:1247-1263` (`F1 … give their own text, fingerprint kept`) recorre el camino entero con una bóveda que autentica y no se decodifica (`undecodableVault`, `:1237`). Comprueba `launchUnlockWithKey` → `[FINGERPRINT_DATA_UNREADABLE]` en la versión 4, `UPGRADE_NEEDS_MASTER` sin versión, `onWrongKey` 0 veces, la bóveda bloqueada y la clave a ceros. Además, `:1002-1008` fija el literal y que `biometricUnlockError` lo pasa tal cual. El rojo antes del arreglo está en el informe (`abogado-2.0-applied-report.md`, §2: 79 tests, 1 fallo con el texto viejo).
- **La ficha cumple.** La sección que se pega (`docs/ficha-google-play.md:21`, «## Descripción completa», hasta `:66`) mide **3 927 caracteres** (3 927 unidades UTF-16), con 42 saltos de línea: 3 969 si cada salto contara dos. No tiene negritas y lleva «Sin recuperación» y «Sin auditoría independiente». `PublishedTextsTest.kt:86-92` lo fija.
  - No nombra ninguna otra app. Have I Been Pwned es el servicio que se consulta, aceptado por el Abogado (`abogado-2.0.md`, O-3). Enigma, César, Vigenère, X-Wing, Argon2id y los demás nombres son de cifrados y primitivas.
  - Las únicas «seguro» y «garantiza» van en «No garantiza que un enlace sea seguro», un aviso que el dictamen exige conservar.
  - La línea del máximo (R-8) está en `:9`, y la larga queda aparte en `:67`, «Versión larga (no cabe en Play)».
- **La fecha:** `docs/privacidad.html:22`, «Última actualización: 21 de noviembre de 2026.» (`176c6a8`), y `:60` con «Comprobar filtraciones de los accesos», el nombre del botón tras R-1.
- **Lo demás del informe:** O-1, O-2, R-1 a R-6 y O-4 están en sus commits (`1ccd965`, `e6195bf`, `13e7028`, `d41f73b`). Las condiciones de O-4 están fuera del bloque que se pega (`2026-11-15-clavero-2.0-notas.md:12-13`), y la línea de B-2 dice «una forma conocida… (en Android 8 a 10)» (`:26`). O-5 queda en la fila 15 (`pendientes.md:23`).

## 3. Mis menores y mi repaso de documentos (punto 8)

- **M-1 a M-4** (`advisor-t12-audit-fixes-recheck.md:161-184`):
  - **M-1:** el KDoc de `estimatedOverBytes` y `previewDataBytes` dice ya la garantía de verdad (`imports/MergePlan.kt:414-421`, `:428-434`; `0c5cf3f`).
  - **M-2:** `UpgradeCopyFirstTest.kt:422` excluye el `.tmp`, y las líneas en blanco están puestas (`d5d83e4`). Los dos rojos sobre entrada alterada, con su mensaje, están en `recheck-minors-report.md:19`, y no en `audit-fixes-report.md` §1 como pedí. Lo dice el propio informe (`:31`), y lo acepto: lo que importa es que constan.
  - **M-3:** la auditoría ya no dice «Entorno de firma» (0 coincidencias). Las filas 12 (`pendientes.md:18`), 83 (`:91`, con AC-E1), 87 (`:95`, con `539d2fd` y `edit(commit = true)`), 51 (`:59`) y 106 (`:114`) están corregidas, en `e68601b`.
  - **M-4:** `AppContainer.kt:792-794` dice lo mismo que `:143-144` y `:757-758` (`a76d355`).
- **O-1 a O-8 del repaso de documentos** (`advisor-t12-docs-review.md:40-85`):
  - **O-1:** `README.md:424`, «…declarando su misma afinidad (la otra variante conocida de ese ataque solo la cierran los parches de seguridad de Android).»
  - **O-2:** las notas, `:26` y `:57`. El Abogado la dejó igual (O-4 de su dictamen).
  - **O-3:** el traspaso, `:3`.
  - **O-4:** `CLAUDE.md:39`, con «casi terminada», la precedencia y «Paso de la 2.0» y «Decisiones de la 2.0».
  - **O-5:** `CLAUDE.md`, «la 3.ª sin hallazgos nuevos», los rangos `7505281` … `a94455a` y `a76d355` … `56d5751`, la línea de `UPGRADE_COPYING` y D-1, «(R5 de la 1.4.0, ampliada… fila 61)» y las filas 51, 54, 57, 58, 62, 67, 69, 83 y 104.
  - **O-6:** `progress.md:287`, «hecha en 23 commits».
  - **O-7:** `README.md:415` («fila 56») y `:416` («filas 55, 58, 62 y 69» y la frase de la fila 69); las filas 56 y 69 de `pendientes.md`, con su línea del README.
  - **O-8:** la auditoría puso `notas.md:30`; después se movió (N-3).
- **M-1 a M-7 del repaso de documentos:**
  - **M-1:** `README.md:418`, «…y el bloqueo «Al salir» siguen igual que antes.»
  - **M-2:** `README.md:420`, «(auditoría, A-1 y B-3, fila 87)».
  - **M-3:** `README.md:423`, «1 997 adjuntos pequeños…».
  - **M-4:** `README.md:28`, «Añadir adjunto» no deja pasar de 1 998.
  - **M-5:** el traspaso, `:30`, con A-2, D-3, E-2 y E-3.
  - **M-6:** el traspaso, `:51`, con la CI, el `versionCode` de 125 o más y la copia con la 1.4.3 antes de instalar.
  - **M-7:** `CLAUDE.md`, «13-11, 15-11, 17-11 y 19-11-2026» y la línea «Logo y aspecto».

## 4. Menores (se arreglan antes de pedir el OK; ninguno de código)

**N-1. `progress.md:295`, el receptor de `profileinstaller`.**
- Dice que sus acciones «cambian solo de orden», y no cambian.
- El orden es el mismo en los dos APK: `INSTALL_PROFILE`, `SKIP_FILE`, `SAVE_PROFILE` y `BENCHMARK_OPERATION` (`v143-manifest.txt:110-121`, `clean4-manifest.txt:111-122`). El `diff` las desalineaba porque los números de línea se movieron con las líneas nuevas de B-2 (`AndroidManifest.xml:20-22`, `:28`). Quitando `(line=N)`, `diff` solo da `versionName` y `taskAffinity`.
- Que diga: «Sin los números de línea, el `xmltree` solo cambia en `versionName` (1.4.3 → 2.0.0) y `taskAffinity=""` de `MainActivity`; los permisos y el `badging`, iguales salvo `versionName`; `check-manifest.sh`, OK en el APK de la 2.0 y con el error de B-2 en el de la 1.4.3.»

**N-2. La auditoría, al estado de hoy.**
- **§7, punto 5** (`docs/auditorias/2026-11-21-auditoria-2.0.md:394`) dice aún «hoy «10 de noviembre de 2026»». Que diga que está hecho: «21 de noviembre de 2026», en `176c6a8`.
- **§7, puntos 3, 4 y 6** (`:392`, `:393`, `:395`) y **§12, puntos 3 a 6** (`:548-551`): marcar como hechos el repaso de documentos con su revisión, el Abogado con sus cambios, la fecha y la puerta (este informe).
- **§9** (`:430-446`):
  - añadir el último clon limpio, de `176c6a8`, con las cifras del punto 1 de la tabla y la comparación con la 1.4.3 del punto 3, `check-manifest.sh` incluido;
  - la línea del APK de *release* (`:445`) dice que su resultado se apunta «tras el clon limpio de la ronda o en la puerta del APK». Que diga que lo comprueba la CI con `check-manifest.sh --release` (`.github/workflows/build-apk.yml:122`) en el PR y en *Build APK*.
- **§11.1, «Queda»** (`:523`): quitar la fecha y la puerta; quedan O-5 (del dueño) y Play Console.

**N-3. La cita de B-4 en las notas** (`docs/auditorias/2026-11-21-auditoria-2.0.md:495`).
- Dice `2026-11-15-clavero-2.0-notas.md:30`, pero la frase («tras «Copiar usuario», en pantalla dividida o en una ventana flotante, ofrece «Copiar contraseña» durante 30 segundos») está hoy en `:32`: O-4 del Abogado añadió dos líneas en `:12-13`.
- Cambio: `:30` → `:32`.

**N-4. `CLAUDE.md`, el traspaso y el registro.**
- **`CLAUDE.md:58`:** dice «Pendientes: la fecha de la política, el último clon limpio y la puerta del APK». Que diga que están hechos (la fecha en `176c6a8`, el clon de `176c6a8` y la puerta en `sdd-2.0/advisor-t12-apk-gate.md`). Queda O-5, del dueño.
- **El traspaso** (`2026-11-12-clavero-2.0-traspaso.md:48-50`): los pasos 1 a 3 de «Siguiente paso», hechos.
- **`progress.md`:** una línea con el resultado de esta puerta y N-1.

**N-5. `CLAUDE.md:184`, CRY-A02 de la 1.3.1, que el archivo da por vigente.**
- Dice «Una clave que autentica pero cuyas entradas no se decodifican da el error de siempre y conserva la huella.» Desde R-7 ya no es «el error de siempre».
- Añadir: «(desde la 2.0, con su texto propio, `FINGERPRINT_DATA_UNREADABLE`: R-7 del Abogado final; la huella se sigue conservando)».
- La decisión de la 2.0 ya lo dice (`CLAUDE.md:81`). Esto evita que las dos líneas se contradigan.

**Cómo van:**
- En un solo commit de documentos (`CLAUDE.md`, la auditoría, el traspaso y `progress.md`), con este informe.
- Antes del commit, con el cerrojo, se ejecutan los tests que leen documentos: `PublishedFilesNameNoModelTest`, `ImportTextsTest` (lee los traspasos y las notas), `AppNameTest`, `FinalLegalReviewTest`, `PublishedTextsTest`, `ToolTextsTest` y `WorkflowHardeningTest`. Además, `git diff --stat 84e222a..` tiene que dar solo esos archivos.
- **No hace falta otro clon limpio.** Matizo el punto 5 del Ruling de mi revisión de documentos (`advisor-t12-docs-review.md:128`). El clon de `176c6a8` cubre todo el código y todos los documentos que los tests leen salvo estas líneas de estado, como ya pasó con `84e222a`. La CI del PR pasa las dos suites, `lintRelease`, `assembleRelease` y `check-manifest.sh --release` sobre el árbol final (`build-apk.yml:81`, `:122`, `:248`), y el PR solo entra con **build** y **jvm-check** en verde.
- No piden otra revisión mía. El controlador lo comprueba con el `diff`.

## Ruling

1. **La puerta se abre: se puede pedir al dueño el OK para el PR**, en cuanto esté el commit de N-1 a N-5 con los siete tests de documentos en verde.
2. **O-5 (fila 15) no bloquea el PR ni el APK de prueba.** O-C1 la pide «antes de publicar el icono nuevo en Play o en una release» (`abogado-2.0-cristal.md:123-124`), así que tiene que estar antes de la etiqueta `v2.0.0` y de Play. Conviene decirle al dueño, al pedir el OK, que el artefacto de *Build APK* no es una release, pero lo puede descargar cualquiera con acceso de lectura al repositorio. Si eso le importa, que haga la búsqueda antes de lanzar *Build APK*.
3. **Al pedir el OK**, recordar al dueño lo que dice el parte (§3, paso 9) y que el traspaso recoge (`:51`):
   - el PR, con **build** y **jvm-check** en verde;
   - la fusión sin etiqueta;
   - *Build APK* desde `main`, con un `versionCode` de 125 o más, aprobando él mismo **build-firma**, que lanza él (C-3);
   - antes de instalar, una copia de su bóveda hecha con la 1.4.3, guardada fuera del móvil, porque el paso a v4 no tiene vuelta atrás (`BackupV3Test.aCopyOfAV4IsRefusedByThe143Reader`);
   - las pruebas de «Pruebas de la 2.0»;
   - las líneas de B-2 y B-4 de las notas, solo si salen bien sus pruebas (O-4).
4. **La tarea 11 (Maestro)** va en el segundo PR (filas 27, 35 y 48). La etiqueta `v2.0.0`, solo cuando el dueño dé por buena la prueba en el móvil.

## Motivos

- El clon limpio es del commit de la fecha (`176c6a8`), y desde él solo han cambiado dos líneas del registro (`84e222a`). Las cifras (2 604 y 2 539, sin fallos ni omitidos) salen de los XML, no del registro.
- El manifiesto final difiere de la 1.4.3 solo en lo aceptado, y en los dos sentidos: el `xmltree` sin números de línea, los permisos, el `badging` y `check-manifest.sh`, que pasa en la 2.0 y falla en la 1.4.3 por B-2 (`scripts/check-manifest.sh:113`, `:138`; `PlatformSourcesTest.kt:53-59`).
- R-7 no cambia el comportamiento: el tipo de la excepción, la conservación de la huella, el borrado de la clave y el camino de la falta de memoria siguen igual (`VaultStore.kt:719-727`, `:759-774`). El test F1 lo fija de punta a punta (`VaultStoreTest.kt:1247-1263`).
- Lo publicado cumple lo pedido: la ficha cabe (3 927 de 4 000), sin otras apps, y la política tiene su fecha. Ningún nombre de modelo ni dato del dueño en el árbol ni en los commits, fuera de C-2 y de la atribución.
- Lo que falta es de estado y de una cita (N-1 a N-5). Ninguna frase publicada promete de más y ningún texto de la app cambia.

## Riesgos o dudas

- **No he ejecutado Gradle.** Las cifras salen de los XML y de los registros del clon de `176c6a8`, que he comprobado que es ese commit. Lint, del SARIF del clon.
- **El APK de *release* no se compila aquí.** Lo comprueba la CI con `--release` (`build-apk.yml:122`). Lo que compara la puerta son los dos APK de depuración (los dos con `versionCode` 1). `PreviewActivity` exportada sale en los dos y solo se permite en un APK *debuggable*.
- **B-2 y B-4 en el móvil** (fila 106; README `:418`, `:407`): la app de prueba de afinidad no está en el repositorio. Las dos líneas de las notas dependen de esas pruebas (O-4 del Abogado).
- **C-2** (los dos asuntos de commits) y las líneas de atribución son lo único con nombre de modelo en la historia de la rama: riesgo aceptado y decisión de la parte C. No pido nada.
- La lista EFF de `app/src/main/assets/` contiene una palabra que coincide con un nombre de la tabla de la skill. Es un dato de la app (CC BY), no un nombre de modelo, y `PublishedFilesNameNoModelTest` la excluye a propósito.

## Revisión experto-seguridad-cripto

- **R-7 no abre ningún oráculo nuevo.** El texto propio solo sale después de `VaultCrypto.authenticated` (`VaultStore.kt:750-753`), con una clave que la huella ya soltó. Distinguir «autentica pero no se decodifica» ya lo hacía el comportamiento desde CRY-A02 (la huella se apaga o no). El desbloqueo con la maestra conserva un solo mensaje para la contraseña equivocada y los datos dañados (`Crypto.kt`, `CryptoFailure` por defecto, sin cambios). La clave se borra en todos los fallos, porque el `catch (e: Throwable)` de `unlockWithKey` la borra si no se quedó en la bóveda abierta (`:719-727`), y con la falta de memoria por `key.fill(0)` (`:772`). La excepción nueva no lleva la causa original, así que no puede salir ningún texto de `kotlinx` con datos.
- **Superficie:** los permisos, iguales que en la 1.4.3 (`check-manifest.sh:27-33`). Los componentes exportados, los mismos: `MainActivity`, el receptor de `profileinstaller` con `DUMP` y, solo en depuración, `PreviewActivity`. El `FileProvider` no está exportado. `git diff a3ac942..HEAD` sale vacío en `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, `config/allowed-licenses.json`, `NOTICE` y `res/xml`: ninguna dependencia, suma, licencia ni configuración de copia o de `FileProvider` nueva.
- **B-2:** `taskAffinity=""` está solo en `MainActivity` y no en `<application>` (`AndroidManifest.xml:28`; `PlatformSourcesTest.kt:53-59`). Corta la variante por afinidad. Los textos dicen eso y no más (`README.md:424`; notas `:26`).
- **Red:** sin cambios. La política dice lo mismo con el nombre nuevo del botón (`docs/privacidad.html:60`), y no se envía ninguna contraseña más (la del wifi no, fila 5).
- **Datos del dueño y secretos:** ningún correo que no sea el del proyecto, `noreply` o de prueba. Ningún dispositivo, sistema ni ruta. Ningún secreto en lo añadido. C-3 sigue como riesgo con nombre (`pendientes.md`, fila 103).

## Revisión Ingeniero Móvil Senior

- **Versión:** `versionName` 2.0.0 (`app/build.gradle.kts:35`). `versionCode` por propiedad (`:34`), con el número de la ejecución en la CI (`build-apk.yml:112`, `:324`). `minSdk` 26 y `targetSdk` 36, iguales que en la 1.4.3 (`badging`).
- **`taskAffinity=""` y el resto de la app:** el escáner, los selectores y «Compartir» se abren sin `FLAG_ACTIVITY_NEW_TASK` y se quedan en la tarea de Clavero. Lo que solo confirma el móvil (recientes, pantalla dividida, «Al salir»), en `README.md:418` y la fila 106.
- **R-7, concurrencia:** el `try`/`catch` va dentro del mismo `withContext(compute)` y bajo el mutex (`VaultStore.kt:762-768`). No añade puntos de suspensión ni cambia el orden de `active()` y `commit` (`:777-778`). La pantalla recibe el texto por `onError`, sin estado nuevo.
- **Antes de instalar:** el paso a v4 no tiene vuelta atrás, y la 1.4.3 rechaza la bóveda y las copias nuevas (`BackupV3Test.aCopyOfAV4IsRefusedByThe143Reader`). Por eso la copia con la 1.4.3 fuera del móvil es condición del dueño (Ruling, punto 3).
- **Calidad:** lint sin errores en el clon (18 avisos de versiones y configuración, los mismos tipos que en el informe del Abogado). No hay ningún `@Ignore`. El `assumeTrue` de `JpegTest.kt:378` no omitió nada.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo. N-1 a N-5 son documentos internos (el registro, `CLAUDE.md`, el traspaso y la auditoría) y una cita de línea. No cambian ningún texto de la app ni de lo que se publica (la política, la ficha, el README y las notas).

Modelo: el director (esfuerzo extra).
