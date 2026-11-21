# Auditoría completa de Clavero 2.0, parte C: método general (`auditoria-seguridad`)

**Commit auditado:** `bdd9872` («el director encarga la ronda única de arreglos de las revisiones y la auditoría completa»), en la rama `ccr-0aa27f95-u038cd`.
- Mientras auditaba, el árbol de trabajo tenía cambios sin commit de la ronda (`AutoBackup.kt`, `PendingUpgrade.kt`, `VaultStore.kt` y un test nuevo). Cada `archivo:línea` de este informe sale de `bdd9872`, con `git show bdd9872:<ruta>` o `git grep … bdd9872`, nunca de esos archivos a medias.
- **A repasar en el HEAD final:** `vault/VaultStore.kt` (C-5 y las líneas de «Lo que está bien», §6), `vault/AutoBackup.kt` y `vault/PendingUpgrade.kt` (los `catch` revisados en §6). `PendingUpgrade.kt` no está en la lista del encargo, pero la ronda lo está tocando.

**Base de la rama:** `a3ac942..bdd9872`, 364 commits. La `main` local está en `a9ff79e`, por detrás de `origin/main` (`a3ac942` = `v1.4.3`), así que `git log main..HEAD` da 377: los 13 de más son de la 1.4.3, que ya está publicada. Para la regla de privacidad miré los dos rangos.

**Solo lectura:** no cambié código ni ejecuté Gradle. Usé `git`, `grep`, `sha256sum`, `git ls-remote` (para las etiquetas de las acciones) y el escáner de la skill `auditoria-seguridad` (`scripts/escanear.py`, sus resultados tratados solo como pistas). Escribí únicamente este archivo.

**Skills leídas:** `CLAUDE.md`; `experto-seguridad-cripto` (`SKILL.md`, `references/auditoria.md` y `references/cripto-moderna.md`; la de criptoanálisis no aplica a esta parte); `auditoria-seguridad` (`SKILL.md`, `references/severidad.md`, `cloud-contenedores.md` (CI/CD) y `movil.md`); el encargo (`advisor-t12-review-fixes.md`, «Encargo de la auditoría completa a fondo», partes común y C); `pendientes.md`, filas 51, 62, 67 y 69, que no reabro.

## Alcance y modelo de amenaza

- **Qué entra:**
  - la cadena de suministro: `gradle/verification-metadata.xml`, `jvm-check/gradle/verification-metadata.xml`, `gradle/libs.versions.toml`, los repositorios, el *wrapper*, `checkLicense` y `checkAssets`;
  - el workflow `.github/workflows/build-apk.yml`;
  - los secretos, en el árbol y en el historial;
  - la regla «Privacidad del dueño», en el árbol y en los mensajes de la rama;
  - estos patrones en `app/src/main`: ReDoS, errores que conceden acceso (*fail-open*), errores tragados, *path traversal*, `Intent` y URI, y registros;
  - la configuración de compilación: R8, `proguard-rules.pro`, `debuggable`, la copia del sistema y la red.
- **Atacantes:**
  - el de la decisión (f): un archivo o una copia hostil importados, `SG1:` y `.sgenc`, QR, *intents*, portapapeles, carpetas compartidas, la copia del sistema, la red y TLS;
  - para esta parte, además:
    - una dependencia o una acción de GitHub comprometidas;
    - alguien con permiso de escritura en el repositorio, incluida una sesión de agente que use la cuenta del dueño;
    - un PR que venga de un *fork*.
- **Lo que no entra:**
  - la criptografía y los formatos (parte A);
  - MASVS completo, el manifiesto fusionado y el APK del clon limpio (parte B);
  - `AutoBackup`, la migración de `SettingsStore` y las pantallas de bóvedas, línea a línea (parte D);
  - el diff de la ronda (parte E).
- **Lo que no puedo ver desde aquí:**
  - la configuración de GitHub: los secretos, el entorno `firma` y los *rulesets*;
  - las referencias `refs/pull/*` del servidor.

  Va como «a comprobar».

## Resumen

| Severidad | Hallazgo | Ubicación |
|---|---|---|
| A comprobar (Media si se confirma) | C-3. Los secretos de firma podrían seguir en el repositorio, y el *ruleset* de las etiquetas `v*` podría seguir con su omisión para *Admin* | Configuración de GitHub; `README.md:226`, `:217`; `CLAUDE.md:162-163` |
| Baja | C-1. Tres archivos publicados nombran un modelo en su línea «Modelo:» | `docs/superpowers/handoff/sdd-2.0/advisor-t12-attack.md:270`, `advisor-t12-redteam-fixes.md:225`, `advisor-t12-redteam-fixes-2.md:164` |
| Informativa | C-2. Dos asuntos de commits de la rama nombran un modelo | `3189f75`, `bc7c266` |
| Informativa | C-4. Un documento de la 1.4.1 dice qué sistema usa el ordenador del dueño | `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-fallos.md:30` |
| Informativa (a repasar en el HEAD final) | C-5. `applyImport` llama a `fileOf` sin comprobar antes la forma del id | `app/src/main/java/app/seguridad/vault/VaultStore.kt:1048`; `vault/Attachments.kt:75-76` |
| Informativa (endurecimiento) | C-6. Sin `network_security_config`; hoy no hay ningún vector | `app/src/main/AndroidManifest.xml:9-18` |
| Informativa (endurecimiento) | C-7. Ningún test fija las propiedades de seguridad del workflow | `.github/workflows/build-apk.yml`; `app/src/test/java/app/seguridad/tools/AppNameTest.kt:175-181` |

No encontré nada Crítico ni Alto. No hay *fail-open*, ReDoS, *path traversal*, registros con datos ni `Intent` con datos importados en `app/src/main` (§6 de «Lo que está bien»).

## Hallazgos

### [A comprobar; Media si se confirma] C-3. Los secretos de firma podrían seguir en el repositorio, y el *ruleset* de las etiquetas, con su omisión para *Admin*

- **Severidad:** a comprobar. Si los cuatro `SEGURIDAD_*` siguen como secretos del repositorio, es **Media**.
- **CVSS 3.1** (si se confirma): 4.9, `AV:N/AC:L/PR:H/UI:N/S:U/C:H/I:N/A:N`. El impacto en los usuarios va más allá (abajo), pero exige escribir en el repositorio.
- **MASVS v2:** MASVS-CODE-1 (la distribución de la app y su firma) y MASVS-RESILIENCE-2 (la integridad de la app).
- **CWE:** CWE-522 (credenciales protegidas de forma insuficiente) y CWE-269 (gestión de privilegios).
- **Dónde:**
  - en la configuración del repositorio en GitHub, que desde aquí no se puede leer (`gh api` responde 403);
  - el propio workflow lo avisa (`.github/workflows/build-apk.yml:20-25`): «until then they are repository secrets»;
  - el README lo deja como pasos del dueño (`README.md:217` para el *ruleset* de las etiquetas, `:226` para borrar los secretos y `:228-231` para la prueba `VACIO`);
  - `CLAUDE.md:162-163` (pendientes 5 y 6) los da por abiertos.
- **Qué pasa:** el código del workflow está bien protegido (§3 de «Lo que está bien»). El agujero estaría en la configuración: mientras los cuatro secretos sigan en el repositorio, cualquier ejecución puede leerlos con un YAML cambiado, sin entorno y sin aprobación. Por ejemplo:
  - un PR desde una rama del mismo repositorio, como las `ccr-*` que empujan las sesiones de agente;
  - un push a `claude/**`.
- **El *ruleset* de las etiquetas:** según el README, tiene el rol *Admin* en *Always* en su lista de omisión. Así, una sesión que use la cuenta del dueño puede crear una etiqueta `v*`. Esto solo es grave si el entorno `firma` no tiene *Required reviewers* (paso 3).
- **Impacto:** con la clave de subida, quien la tenga firma un APK que Android instala encima del de *Releases*, y ese APK hereda la bóveda y la huella (`README.md:215`, «Qué protege la clave»). Hace falta permiso de escritura en el repositorio, pero eso incluye a una sesión de agente engañada por un texto que haya leído.
- **Corrección** (pasos del dueño en GitHub, no código):
  1. Confirmar que el entorno `firma` tiene los cuatro secretos, *Deployment branches and tags* con `main` y `v*`, y *Required reviewers* (`README.md:220-224`).
  2. Borrar los cuatro `SEGURIDAD_*` de *Repository secrets* (`README.md:226`).
  3. *Workflow permissions* en solo lectura, y aprobación de los PR de *forks* para todos los colaboradores externos (`README.md:227`).
  4. Si alguien que no era el dueño pudo empujar una rama mientras los secretos estaban en el repositorio, cambiar la clave de subida (`docs/PUBLICAR.md` §4).
- **Prueba de concepto (operativa, no un test unitario):** la del paso 7 del README (`README.md:228-231`).
  1. En una rama `claude/prueba-secretos`, un workflow con `test -n "${{ secrets.SEGURIDAD_KEY_ALIAS }}" && echo PRESENTE || echo VACIO`. Tiene que decir `VACIO`; hoy no sé lo que dice.
  2. Una etiqueta `vprueba` empujada desde una sesión de agente: o la rechaza el *ruleset*, o **build-firma** se queda esperando aprobación.
  3. Borrar después la rama y la etiqueta.

  Un test de JVM no puede comprobar esto: la configuración no está en el repositorio.

### [Baja] C-1. Tres archivos publicados nombran un modelo en su línea «Modelo:»

- **Severidad:** Baja. No es un defecto del software. Rompe una regla del proyecto, y las revisiones de la rama ya trataron casos iguales como Minor (`branch-rereview-compilacion.md`, R-1 y R-2).
- **CVSS 3.1:** no aplica, porque no hay vulnerabilidad.
- **MASVS v2:** no aplica (la regla es del repositorio, no de los datos del usuario).
- **CWE:** no aplica.
- **Dónde:**
  - `docs/superpowers/handoff/sdd-2.0/advisor-t12-attack.md:270`, añadido en `569e299`;
  - `docs/superpowers/handoff/sdd-2.0/advisor-t12-redteam-fixes.md:225`, añadido en `5e39b27`;
  - `docs/superpowers/handoff/sdd-2.0/advisor-t12-redteam-fixes-2.md:164`, añadido en `79e21ba`.

  Los tres son posteriores a la re-revisión de compilación (`cad3ba5`), que dejó limpio el árbol.
- **Qué pasa:** la línea «Modelo:» de esas tres respuestas del director lleva, entre paréntesis, el nombre de un modelo. Aquí no lo copio. La regla lo prohíbe en dos sitios:
  - `docs/superpowers/handoff/2026-11-17-clavero-2.0-parte.md:36`: «Su respuesta se guarda en `sdd-2.0/advisor-t12-*.md` con la línea «Modelo» cambiada por «el director».»;
  - `:37`: «Sin nombres de modelos en ningún archivo que se suba».

  Las otras siete respuestas `advisor-t12-*` ya lo cumplen: dicen «Modelo: el director.» o «Modelo: el director (esfuerzo extra).».
- **Impacto:** se publica cuál es el modelo del director, que el dueño decidió no publicar. No afecta a la app ni a sus usuarios.
- **Corrección:** en las tres líneas, quitar el nombre del paréntesis y dejar «Modelo: el director (esfuerzo extra).», como en `advisor-t12-review-fixes.md`. Nada más cambia.
- **Prueba de concepto propuesta (test que hoy falla):** `PublishedFilesNameNoModelTest.noPublishedFileNamesAModel`, en `app/src/test/java/app/seguridad/tools/` y en jvm-check.
  1. Lee los nombres en tiempo de ejecución desde `.claude/skills/advisor/SKILL.md`: las palabras entre comillas invertidas de la fila `| \`modelo\` |` de la tabla (`:16`). Así el test no tiene ningún nombre como literal y no rompe la regla que comprueba.
  2. Recorre los archivos de texto del repositorio (como `AppNameTest.read`, desde `repo()`), sin `.git/`, las carpetas `build/`, `.gradle/` y `.kotlin/`, `app/src/main/assets/` (la lista EFF tiene dos de esas palabras como palabras corrientes) y el propio `SKILL.md`.
  3. Busca cada nombre como palabra entera, sin distinguir mayúsculas, y además los identificadores técnicos (`claude-` seguido de uno de esos nombres).
  4. Falla con la lista de `archivo:línea`. Hoy da tres; con el arreglo, ninguna.

  Hay que declarar `docs/superpowers/handoff` como entrada en las dos tareas `test` (`app/build.gradle.kts:242`, `jvm-check/build.gradle.kts:63`), para que un archivo nuevo haga correr el test otra vez.

### [Informativa] C-2. Dos asuntos de commits de la rama nombran un modelo

- **CVSS / MASVS / CWE:** no aplican (la regla es del proyecto).
- **Dónde:** el asunto de `3189f75` (19-11-2026) y el de `bc7c266` (13-11-2026). Comprobado con `git log a3ac942..bdd9872 --format='%h%n%B'`, sin las líneas `Co-Authored-By` y `Claude-Session`.
- **Qué pasa:** el encargo del ataque pide «ningún nombre de modelo en archivos, asuntos ni cuerpos» (`advisor-t12-attack.md:47`), y `2026-11-17-clavero-2.0-parte.md:37` solo permite los nombres en las líneas de atribución. Esos dos asuntos no son líneas de atribución. Por lo demás, los mensajes de la rama están limpios: ningún otro nombre fuera de la atribución, y ningún correo, dispositivo, sistema, ruta de usuario ni servicio en la nube.
- **Corrección** (decisión del dueño), una de dos:
  - **(a)** Aceptarlo y apuntarlo en el registro, porque reescribir el historial cambia los hashes de los commits posteriores, y los documentos de la rama citan cientos de ellos.
  - **(b)** Reescribir solo esos dos asuntos antes del PR, con `git filter-repo --message-callback` o con `git rebase` y `reword`. Después habría que poner al día cada hash que citan los documentos y empujar con *force push* a la rama. Así se hizo con el permiso del dueño el 10-11-2026 (`CLAUDE.md:173`).

  Recomiendo la (a): como el repositorio no permite «Squash and merge» (`CLAUDE.md:173`), los commits entran en `main` tal cual, pero el coste de la (b) es alto y el dato no afecta a la app.
- **Prueba de concepto:** comprobación a mano, porque un test no debe depender de `git`: `git log a3ac942..HEAD --format=%s` filtrado con los nombres de la tabla de `.claude/skills/advisor/SKILL.md:16`. Hoy da dos líneas.

### [Informativa] C-4. Un documento de la 1.4.1 dice qué sistema usa el ordenador del dueño

- **CVSS / MASVS / CWE:** no aplican. Como mucho, CWE-359 en sentido amplio: es un dato del dueño, no de los usuarios.
- **Dónde:** `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-fallos.md:30`, en «Qué vio el dueño (10-11-2026)». Está en `main` desde la 1.4.1; no lo añade esta rama.
- **Qué pasa:** la frase une al dueño con el sistema operativo de su ordenador. La regla prohíbe «el modelo de su móvil o de su ordenador» (`CLAUDE.md:172`). El sistema no es un modelo, pero la limpieza del 10-11-2026 cambió por textos neutros también «su sistema» (`CLAUDE.md:173`). El Abogado de la 1.4.1 miró la misma palabra en otros textos, pero solo como marca (`abogado-1.4.1.md:61`), no con esta regla.
  - Las demás menciones de ese sistema en el árbol son instrucciones técnicas para quien compila (`.maestro/README.md:31`) o notas de la versión sin sujeto (`2026-11-10-clavero-1.4.1-traspaso.md:11`, `:52`). No dicen qué usa el dueño.
- **Corrección** (decisión del dueño): cambiar en `:30` el sujeto «el dueño» por uno impersonal: «Qué pasaba (10-11-2026): en <el sistema>, `maestro test …` termina…». Así el texto técnico se queda y no dice quién lo vio.
- **Prueba de concepto:** comprobación a mano con `git grep -n "Qué vio el dueño"`, mirando si alguna de esas líneas nombra el sistema, el móvil o el ordenador. No propongo un test, porque haría falta una lista de sistemas, lo que es frágil.

### [Informativa, a repasar en el HEAD final] C-5. `applyImport` llama a `fileOf` sin comprobar antes la forma del id

- **CVSS 3.1:** 0.0 hoy; no es alcanzable (abajo).
- **MASVS v2:** MASVS-CODE-4 (validar las entradas), como defensa en profundidad.
- **CWE:** CWE-22, teórica.
- **Dónde:**
  - `app/src/main/java/app/seguridad/vault/VaultStore.kt:1048`, en `bdd9872`: `attachmentRefs.sumOf { attachments.fileOf(it.id).length() }`;
  - `vault/Attachments.kt:75-76`: `fileOf` arma `File(dir, "$id.bin")` y solo pide en su KDoc que el llamador haya comprobado `isAttachmentId`.
- **Qué pasa:** los demás llamadores comprueban el id antes:
  - `VaultStore.kt:1423`, antes de `:1425`, en la exportación;
  - `:1548`, con `attachmentInCopy` → `BackupCheck.kt:315`, antes de `moveIn` (`:1582`, `:1630`), al importar una copia;
  - `Attachments.kt:133`, antes de `:134`, en `open`;
  - en `add`, el id es aleatorio.

  En `:1048` los ids salen de las entradas de la propia bóveda (`:1016`), que son datos autenticados y solo pueden llegar con ids comprobados (una copia importada pasa por `:1548`). «Importar de otra app» no trae adjuntos.
- **Impacto:** ninguno hoy. Aunque llegara un id con `../`, solo se leería la longitud de un archivo para el cálculo de `TooBig`. Lo apunto porque el contrato de `fileOf` depende de que cada llamador se acuerde, y `VaultStore` está en la ronda.
- **Corrección:** poner la comprobación en el único punto por el que pasan todos:

  ```kotlin
  /** The file of [id]; [IllegalArgumentException] for anything that is not an attachment id ([isAttachmentId]). */
  internal fun fileOf(id: String): File {
      require(isAttachmentId(id)) { "Id de adjunto no válido" }
      return File(dir, "$id.bin")
  }
  ```

  No cambia ningún caso real, porque todos los llamadores, salvo el de `:1048`, ya comprueban antes. Si se prefiere no lanzar dentro de `accept`, en `:1048` se puede rechazar como en `:1423` (`attachmentUnusable`).
- **Prueba de concepto propuesta (test que hoy falla):** `AttachmentsTest.fileOfRefusesAnIdThatIsNotAnAttachmentId`.
  1. `AttachmentStore(tempDir)`.
  2. `assertThrows<IllegalArgumentException> { store.fileOf("../vault") }`, y lo mismo con `""`, con `"A".repeat(32)` (mayúsculas) y con `"0".repeat(31)`.
  3. `assertEquals(File(tempDir, "${"0".repeat(32)}.bin"), store.fileOf("0".repeat(32)))`.

  Hoy el paso 2 no lanza nada.

### [Informativa, endurecimiento] C-6. Sin `network_security_config`; hoy no hay ningún vector

- **CVSS 3.1:** 0.0 hoy.
- **MASVS v2:** MASVS-NETWORK-1.
- **CWE:** CWE-319, teórica.
- **Dónde:**
  - `app/src/main/AndroidManifest.xml:9-18`: no declara `android:networkSecurityConfig` ni `usesCleartextTraffic`;
  - `app/src/main/res/xml/` solo tiene `backup_rules.xml`, `data_extraction_rules.xml` y `share_paths.xml`.
- **Lo comprobado en el repositorio:**
  - un solo cliente HTTP (`net/HttpClient.kt:15-30`), sin caché ni cookies;
  - con `followRedirects(false)` y `followSslRedirects(false)` (`:25-26`);
  - una URL base fija en `https://api.pwnedpasswords.com/` (`net/PwnedPasswords.kt:24`);
  - ninguna otra API de red en `app/src/main`: sin `HttpURLConnection`, `openConnection`, `URL(` ni `Socket(`;
  - nunca se abre una conexión en claro.
- **Ya se valoró antes:** `auditoria/04-cripto-parteB.md:335` y `docs/auditorias/2026-11-08-ataque-1.4.0.md:136`. Esta rama no cambia nada de esto (`git diff a3ac942..bdd9872 -- app/src/main/AndroidManifest.xml app/src/main/res/xml` sale vacío).
- **A comprobar** (no sale del repositorio): esa auditoría afirma que en Android 8.0 y 8.1 (`minSdk` 26) el tráfico en claro está permitido por defecto aunque `targetSdk` sea 36. No lo he podido comprobar aquí. Con la URL fija en `https` da igual.
- **Corrección** (opcional, defensa en profundidad):
  - un `res/xml/network_security_config.xml` con `<base-config cleartextTrafficPermitted="false"><trust-anchors><certificates src="system" /></trust-anchors></base-config>`;
  - `android:networkSecurityConfig="@xml/network_security_config"` en `<application>`;
  - **sin** `<certificateTransparency>` ni *pins*, para no cambiar lo que valida CRY-B01 (`net/DistrustedCertificates.kt:84-113`, `android/TrustedChain.kt`).

  Si se añade, hay que repetir en el móvil «¿Filtrada?» y «Comprobar filtraciones» con red, como pide `CLAUDE.md` en cada cambio de red.
- **Prueba de concepto propuesta** (solo si se adopta): `PlatformSourcesTest.theManifestRefusesCleartextAndUserCas`. Lee `AndroidManifest.xml` y el XML que nombra, y comprueba `cleartextTrafficPermitted="false"`, un único `<certificates src="system"/>` y ningún `src="user"`. Hoy falla, porque el manifiesto no nombra ningún XML de red.

### [Informativa, endurecimiento] C-7. Ningún test fija las propiedades de seguridad del workflow

- **CVSS 3.1:** 0.0.
- **MASVS v2:** MASVS-CODE-1 (la cadena de compilación).
- **CWE:** CWE-1357 (dependencia de componentes poco fiables), solo como prevención.
- **Dónde:**
  - el único test que lee `.github/workflows/build-apk.yml` es `AppNameTest.theReleaseApkIsNamedAfterClaveroAndTheWorkflowKeepsItsFile` (`app/src/test/java/app/seguridad/tools/AppNameTest.kt:175-181`), y solo mira el nombre del APK;
  - hoy las propiedades se cumplen (§3 de «Lo que está bien»), pero solo las protege la revisión del PR.
- **Qué pasa:** un cambio de un solo carácter (una acción por etiqueta en lugar de SHA, `pull_request_target`, un `permissions: write-all` o una expresión de secretos sin condición) pasaría los dos *checks* obligatorios, `build` y `jvm-check`.
- **Corrección:** un test de fuente, como los de `PlatformSourcesTest`, que lea el YAML como texto.
- **Prueba de concepto propuesta:** `WorkflowHardeningTest`, en jvm-check, que ya declara el workflow como entrada (`jvm-check/build.gradle.kts:60`). Cuatro casos, todos en verde hoy; cada uno se pone en rojo al quitar la protección que fija:
  1. `everyActionIsPinnedToACommit`: cada `uses:` es `owner/repo@<40 hex>`.
  2. `noPullRequestTargetAndReadOnlyByDefault`:
     - ningún `pull_request_target:` fuera de un comentario;
     - el bloque `permissions:` de arriba es exactamente `contents: read`;
     - solo `release-apk` tiene `contents: write`.
  3. `signingSecretsOnlyInTrustedRuns`:
     - las cuatro expresiones `secrets.SEGURIDAD_*` de `build` llevan la condición de confianza (`:88-91`);
     - las otras cuatro (`:311-314`) solo están en `play-bundle`, que declara `environment: firma`;
     - cada checkout lleva `persist-credentials: false`.
  4. `theUploadCertificateIsChecked`: `UPLOAD_CERT_SHA256` es el de `docs/PUBLICAR.md:19`, y las dos comparaciones (`:154`, `:347`) siguen ahí.

## Notas para la consolidación (no son hallazgos)

- **`Claude-Session` en los commits:** 358 de los 364 commits de `a3ac942..bdd9872` terminan con una línea `Claude-Session` que enlaza a una sesión privada de claude.ai. Es atribución, como `Co-Authored-By`, y no es un dato personal. Pero las auditorías de la 1.3.1 (`docs/auditorias/2026-11-01-auditoria-1.3.1.md:355`) y de la 1.4.0 (`docs/auditorias/2026-11-08-auditoria-1.4.0.md:261`) decían que los commits no llevaban `Claude-Session` ni enlaces a claude.ai. **La auditoría consolidada de la 2.0 no puede copiar esa frase.** Ningún archivo del árbol tiene enlaces de sesión.
- **Licencias:** el último informe de `checkLicense` que hay en el disco es de una compilación local del 12-11-2026 (`app/build/reports/dependency-license/`): 124 módulos, 123 con Apache-2.0 y 1 con MIT (Bouncy Castle), y `dependenciesWithoutAllowedLicenses` vacío. Desde la 1.4.3 no ha cambiado ninguna dependencia (`git diff a3ac942..bdd9872 -- gradle/libs.versions.toml gradle/verification-metadata.xml jvm-check/gradle/verification-metadata.xml config/` sale vacío), pero la evidencia buena es la de `:app:checkLicense` en el clon limpio (§9 de la consolidada).
- **Historial revisado:** 679 commits de las referencias locales:
  - `heads/` main, `ccr-0aa27f95-u038cd`, `ccr-ad702c64-hbv0n0`, `rt-2.0` y `backup-local-t12-paso1-viejo`;
  - `remotes/origin/` main, `ccr-*` y `claude/seguridad-1.0.0`, `-1.0.1` y `-1.1.0`;
  - las etiquetas `v1.0.2` a `v1.4.3`.

  **A comprobar:** `refs/pull/*` del servidor, que según `CLAUDE.md:173` guarda commits de antes de la limpieza. No los traje: quitarlos es cosa del soporte de GitHub y decisión del dueño.
- **Las pistas del escáner** (`escanear.py`, 904 archivos) eran todas falsos positivos:
  - `SEC-PRIV`: la clave de relleno «AAAA» de `app/src/test/resources/import/bitwarden/bitwarden.json:134`;
  - 48 `SEC-URLCRED` y 38 `SEC-GEN`: datos de prueba inventados, comentarios y la expresión `TREE`;
  - `FILE`: `app/debug.keystore`, la clave pública de prueba, y tres certificados PEM de los tests;
  - `COD-*` y `CFG-*`: textos de documentos y `scripts/check-manifest.sh`.

## Lo que está bien

### 1. Cadena de suministro
- **Verificación de dependencias:**
  - `gradle/verification-metadata.xml:3-6` y `jvm-check/gradle/verification-metadata.xml:3-6` tienen `verify-metadata` en `true`;
  - 777 de 777 artefactos y 128 de 128 tienen su `sha256`, todos con `origin="Generated by Gradle"`;
  - no hay ninguna entrada `trusted-artifacts`, `trust`, `ignored-keys`, `also-trust` ni `regex`, ni sumas MD5 o SHA-1;
  - nada relaja la verificación: ningún `--dependency-verification` ni `org.gradle.dependency.verification` en el workflow, `gradle.properties` ni los `*.gradle.kts`;
  - `verify-signatures` en `false` es una decisión documentada (`README.md:158`): cada cambio de las sumas se revisa como un cambio de seguridad, y el trabajo `dependency-metadata` lo resume (`build-apk.yml:175-226`).
- **Cobertura:** Gradle falla con cualquier artefacto sin entrada. Están, entre otros:
  - `bcprov-jdk18on` 1.86, `okhttp` 5.3.2 y `okhttp-android`, `zxing-android-embedded` 4.3.0;
  - el plugin AGP 9.5.0 (`builder` 9.5.0), `kotlin-gradle-plugin` 2.4.21 y `gradle-license-report` 3.1.4.
- **Versiones fijadas:** todas exactas en `gradle/libs.versions.toml:1-20`; Compose, por su BOM `2025.08.00`. No hay `+`, `latest.`, rangos ni `SNAPSHOT`.
- ***Wrapper*:**
  - `gradle/wrapper/gradle-wrapper.properties` trae `distributionSha256Sum` y `validateDistributionUrl=true`;
  - el JAR del *wrapper* da `497c8c2a…a9c7`, la misma suma que comprueba la CI antes de ejecutarlo (`build-apk.yml:71`, `:237`, `:301`).
- **Repositorios:**
  - `settings.gradle.kts:1-16`: `google()`, el espejo de Maven Central en `maven-central.storage-download.googleapis.com`, `mavenCentral()` y, solo para los plugins, `gradlePluginPortal()`;
  - `RepositoriesMode.FAIL_ON_PROJECT_REPOS` (`:10`): ningún módulo añade los suyos;
  - con las sumas obligatorias, un artefacto cambiado en cualquiera de ellos hace fallar la compilación, aunque no tengan filtro por grupo.
- **Licencias:**
  - `licenseReport` mira `releaseRuntimeClasspath` con `LicenseBundleNormalizer` y `config/allowed-licenses.json` (`app/build.gradle.kts:123-127`);
  - la lista solo permite Apache-2.0, MIT, BSD-2 y BSD-3, ISC y CC BY 4.0;
  - `checkAssets` (`:187-226`) falla si una biblioteca mete un *asset* (la PSL de OkHttp, con MPL-2.0, se excluye en `:84`) o si falta una de las cuatro listas de palabras;
  - los datos con licencia propia (EFF con CC BY 3.0, BIP-39 y SLIP-0039 con MIT) salen en «Licencias» con su texto (`ui/screens/LicensesScreen.kt:28-39`, `assets/licenses/`).
- **Desde la 1.4.3** no cambia ninguna dependencia, suma, licencia ni *asset*: la rama solo toca de compilación `versionName` y las entradas de los tests (`git diff a3ac942..bdd9872 -- app/build.gradle.kts jvm-check/build.gradle.kts`).

### 2. Workflow: permisos y acciones
- **Permisos:**
  - en lo alto, solo lectura: `permissions: contents: read` (`build-apk.yml:14-15`);
  - solo `release-apk` escribe (`:256-257`), y no ve la clave: no hace checkout ni lleva secretos.
- **Las cinco acciones, fijadas por SHA** y comprobadas contra las etiquetas de sus repositorios con `git ls-remote --tags`:
  - `actions/checkout@fbc6f399…` = `v5` / `v5.1.0`;
  - `actions/setup-java@b6effb05…` = `v5` / `v5.7.0`;
  - `actions/upload-artifact@ea165f8d…` = `v4` / `v4.6.2`;
  - `actions/download-artifact@d3f86a10…` = `v4` / `v4.3.0`, posterior al arreglo de CVE-2024-42471;
  - `softprops/action-gh-release@3bb12739…` = `v2` / `v2.6.2`.

  Son etiquetas ligeras que apuntan a commits del propio repositorio, no a commits de un *fork*.
- **Checkouts:** los cuatro llevan `persist-credentials: false` (`:51`, `:180`, `:233`, `:289`).
- **Sin `pull_request_target`:** solo sale en el comentario que lo prohíbe (`:8`).
- **Sin inyección:** `github.event.*` solo entra por `env:` (`:185-186`) y se usa entre comillas (`:195`, `:198`). `github.ref_name` solo va en `with:` (`:271-274`); en `run:` se usa `$GITHUB_REF_NAME` entre comillas (`:265-266`).
- **Sin cachés que envenenar:** no hay `actions/cache` ni `cache:` en `setup-java`. Gradle corre con `--no-daemon` en *runners* efímeros.

### 3. Workflow: la firma
- **Cuándo se firma:**
  - `build` corre exactamente cuando la condición de confianza es falsa (`:44`);
  - `build-firma` (`:167-171`) y `play-bundle` (`:280-284`) solo corren con una etiqueta `v*` empujada o a mano desde `main`, y declaran `environment: firma` (`:169`, `:284`);
  - en `build`, las cuatro expresiones de secretos dan `''` salvo en una ejecución de confianza (`:88-91`).
- **Lo que se comprueba:**
  - que el commit está en `main` (`:60-65`, `:293-298`), sin `origin/main` falla;
  - que una ejecución de confianza sin clave falla (`:95-98`, `:317-320`);
  - la huella del APK y del AAB contra `UPLOAD_CERT_SHA256` (`:34`, `:154-157`, `:347-350`), que es la de `docs/PUBLICAR.md:19` (`746398ea…9df9e`);
  - el aviso de la clave de prueba (`:150-153`).
- **El almacén de claves:** se borra en un `trap` y en un paso `if: always()` (`:103`, `:115-117`, `:321`, `:326-328`).
- **Antes de publicar:** `check-manifest.sh --release` comprueba el APK (`:121-122`); `release-apk` solo descarga el artefacto de la misma ejecución, después de `build-firma` y `jvm-check` (`:253`, `:259-261`); `build` y `build-firma` no corren nunca en la misma ejecución, así que no puede haber dos artefactos `Clavero-apk`.

### 4. Secretos
- **En el árbol** (825 archivos):
  - `app/debug.keystore` es la clave pública de prueba, admitida a propósito (`.gitignore:41-46`, `:58-59`);
  - tres certificados públicos PEM, de los tests de eMudhra (`app/src/test/resources/emudhra/`);
  - una clave SSH de relleno (`bitwarden.json:134`, «AAAA»);
  - `keystore.properties`, `*.jks`, `*.keystore`, `local.properties` y `google-services.json` están en `.gitignore`.
- **En el historial** (`git log -p --all`, unos 41 MB de parches):
  - ningún *token* de AWS, Google, GitHub, Slack, Stripe, proveedor de IA ni JWT;
  - ninguna clave privada real, ni la marca de un JKS en base64 (`/u3+7Q`);
  - ninguna contraseña de firma, salvo `android`, la de la clave de prueba (`app/build.gradle.kts:42-47`);
  - los `xprv…` son los vectores públicos de SLIP-0039 (`app/src/test/resources/vectors/slip39.json`);
  - las asignaciones `password = "…"` son datos de prueba en `app/src/test` y en `scripts/import-fixtures/gen_encrypted.py`;
  - ningún archivo `.jks`, `.p12`, `.pfx` ni `.key` en ningún commit, salvo `debug.keystore`.

### 5. Privacidad del dueño
- **Autores y *committers***, en todas las referencias: solo `Egt17baa` con dos direcciones `noreply` de GitHub, `Claude <noreply@anthropic.com>` y `GitHub <noreply@github.com>`.
- **Correos del árbol:** el de contacto del proyecto, publicado a propósito (`SECURITY.md:7`, `docs/privacidad.html:26`, `docs/PUBLICAR.md:114`, fijado por `FinalLegalReviewTest.kt:188-191`), los `noreply` y direcciones de prueba inventadas.
- **Sin dispositivos ni rutas:** ningún modelo de móvil u ordenador del dueño ni ruta de usuario; las marcas de fabricantes que aparecen son instrucciones genéricas de prueba (`README.md:309`, `:327`) o formatos (`tools/photo/Jpeg.kt:163`).
- **R-4 de la 1.4.1, aplicada:** la auditoría de la 1.3.0 dice ya «una carpeta en la nube» (`docs/auditorias/2026-10-31-auditoria-1.3.0.md:468`). Queda el comentario de código «owner's phone test» (`ui/screens/VaultBackup.kt:114`), que no da ningún dato.
- **Mensajes de la rama:** sin correos fuera de la atribución, dispositivos, sistemas, rutas ni servicios en la nube. Las excepciones son C-2 y C-4.

### 6. Patrones de código en `app/src/main` (en `bdd9872`)
- **ReDoS:** ninguna. Hay 41 `Regex`, todas literales: ninguna se construye con datos. Las que se aplican a datos importados o externos son lineales:
  - `BackupFolder.kt:28` (`TREE`): clases disjuntas separadas por literales;
  - `FormHelpers.kt:30` (`BARE_DOMAIN`): cada repetición empieza por un `.` que no está en la clase, y la entrada se corta a 4 096 caracteres (`:41`, `:54`);
  - `CsvFormats.kt:209` (`LOGIN_URI_SEPARATOR`): una coma fija cada intento, como dice su KDoc (`:215`);
  - `health/Health.kt:87`: la misma forma que `BARE_DOMAIN`;
  - las de `Expiry.kt:36-38`, `ContactsView.kt:369` y las de nombres de `AutoBackup.kt:92-120`: cuantificadores acotados.

  Los `split` y los `replace` con `String` son literales, no expresiones regulares (`A6Text.kt:112-113`, `OtpAuthList.kt:72`).
- **Sin *fail-open*:** revisé los 416 `catch` y todos los `runCatching` de `app/src/main`, por paquetes: `net`, `crypto`, `crypto/pk`, `android`, `settings`, `vault`, `imports`, `core`, `health`, `tools`, `ui`, `AppContainer.kt` y `SeguridadApp.kt`. Ninguno devuelve «permitido» ante un error. Los que devuelven `false`, `null` o un valor por defecto cierran el paso:
  - `Backup.kt:91-95` (`isV4`) y `:216` (la suma de un adjunto);
  - `Vault.kt:233-241` (`openAny`) y `:507-510` (`authenticated`);
  - `PhotoCleaner.kt:52-70` (`verify`, falla como dañada);
  - `Webp.kt:237`;
  - `pkKindOfFile` (`FileMessage.kt:314-323`);
  - `AppContainer.kt:784-788`.

  Dos son a propósito y están documentados:
  - `MasterPolicy.kt:155-165` (`loadMasterWords`): un error da un conjunto vacío, y la regla de la maestra nueva queda solo con la longitud y la estimación. Lo compensa `checkAssets`, que falla en la compilación si falta una lista (`app/build.gradle.kts:207-214`);
  - `DistrustedCertificates.kt:57-62`: un nombre que Bouncy Castle no lee no se toma como raíz de eMudhra, pero el hash SPKI se comprueba aparte (`:70-71`), antes y después de la validación del sistema (`:109-113`).
- **Errores tragados:** los de guardar los ajustes (`SettingsStore.kt:41`, `:69`, `:84`) dejan los valores por defecto o los de memoria, con la escritura atómica (`core/AtomicFile.kt`). Los de recoger basura y borrar temporales se reintentan:
  - `Attachments.kt:166`, `:171`;
  - `VaultStore.kt:402`, `:1663`, `:1682`;
  - `ShareTemp.kt:107`, `:130`;
  - `AutoBackup.kt:988`, `:1038`.

  Ninguno toca la clave, el bloqueo ni la huella. En `BiometricVault.kt:627-633`, cualquier fallo al iniciar la clave lleva al TEE, que es la dirección segura.
- **Sin *path traversal*:**
  - el nombre de un adjunto de una copia sale solo de los 32 hex de la entrada, nunca del nombre que trae (`Backup.kt:336`, `:347`);
  - `AttachmentStore.open` comprueba el id (`Attachments.kt:133`);
  - `dirOf` lanza con todo lo que no es un id (`VaultRegistry.kt:90-93`);
  - `ShareTemp.prepare` solo admite los nombres fijos (`ShareTemp.kt:54`);
  - los archivos recibidos van a una carpeta UUID con `createTempFile` (`PkActions.kt:563-565`), y su nombre pasa por `sanitizeReceivedName`, que se queda con el último segmento y quita los puntos (`:204-214`);
  - todo nombre que se propone al guardar pasa por `safeFileName` (`VaultAttachments.kt:127`, `SecurityToolsScreen.kt:816`, `PkActionsScreen.kt:285`, `Attachments.kt:115`);
  - el `DISPLAY_NAME` del proveedor (`SecurityToolsScreen.kt:573-576`) solo se usa como etiqueta o como sugerencia;
  - no hay `DocumentFile`;
  - la única excepción es C-5.
- **`Intent` y URI:**
  - la app no lee ningún *intent* que le llegue: el manifiesto solo tiene `MAIN`/`LAUNCHER`, y no hay `getIntent`, `intent.data` ni `onNewIntent`;
  - lo que envía: `ACTION_SEND` con texto público (`Pickers.kt:150-152`), una imagen por el `FileProvider` (`android/ImageShare.kt:24-29`) y `ACTION_CREATE_DOCUMENT`;
  - `openUri` solo recibe las dos constantes (`SettingsScreen.kt:794-803`);
  - las notas no tienen enlaces (`vault/LimitedMarkup.kt:4-12`);
  - no hay `WebView`, `Html.fromHtml` ni `Linkify`;
  - el JSON no es polimórfico ni deserializa clases del archivo (`core/Json.kt:6-10`, `imports/ImportJson.kt:15`), y no hay `ObjectInputStream`.
- **Registros:** ningún `Log.`, `println`, `printStackTrace`, `System.out` ni `HttpLoggingInterceptor` en `app/src/main`. Ningún mensaje de excepción interpola un secreto; el único con una variable es el código HTTP de HIBP (`PwnedPasswords.kt:36`).

### 7. Configuración de compilación
- **R8:**
  - `isMinifyEnabled` e `isShrinkResources` en la *release* (`app/build.gradle.kts:59-62`);
  - `app/proguard-rules.pro:1-11` solo conserva los serializadores de kotlinx.serialization;
  - no hay ningún `-keep` general, `-dontobfuscate`, `SourceFile` ni `LineNumberTable`, y no cambia desde la 1.4.3.
- **`debuggable`:** la *release* no lo pone; `check-manifest.sh --release` lo rechaza en la CI (`build-apk.yml:119-122`).
- **Sin firma por error:** `checkReleaseKey` impide empaquetar una *release* sin la clave de subida, salvo con `-PfirmaDePrueba=true` (`app/build.gradle.kts:131-145`).
- **Copia del sistema:**
  - `allowBackup="false"`, con `dataExtractionRules` y `fullBackupContent` (`AndroidManifest.xml:11-13`);
  - las dos reglas excluyen todos los dominios, en la nube y entre dispositivos (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`).
- **`FileProvider`:** no exportado; solo sirve `cache/compartir/` (`AndroidManifest.xml:41-49`, `res/xml/share_paths.xml`).
- **Red:** un solo cliente, una URL fija con HTTPS, sin redirecciones, sin caché ni cookies, y el bloqueo de eMudhra en dos pasos (C-6, CRY-B01).

## Revisión experto-seguridad-cripto

- **Secretos y claves:**
  - la clave de subida solo puede salir por la configuración de GitHub (C-3), no por el código: los trabajos sin confianza dan `''` (`build-apk.yml:88-91`), el almacén se borra siempre (`:103`, `:115-117`, `:321`, `:326-328`) y la huella se compara (`:154-157`, `:347-350`);
  - en el historial no hay ninguna clave privada ni contraseña real (§4).
- **Integridad de lo que entra en el APK:**
  - sumas SHA-256 obligatorias para todos los artefactos, sin excepciones (`verification-metadata.xml:3-6`, 777/777);
  - el JAR del *wrapper* se comprueba antes de ejecutarlo (`build-apk.yml:71`);
  - las acciones, por SHA comprobado contra la etiqueta de su repositorio.

  Las firmas PGP no se miran (`verify-signatures` en `false`, `README.md:158`): es confianza en la primera vez, aceptada y documentada.
- **TLS:** la base de confianza es el almacén del sistema, menos las claves de eMudhra, por SPKI y por nombre, antes y después de la validación (`net/DistrustedCertificates.kt:96-113`). El `catch` de `:60-62` no abre nada, porque el SPKI se comprueba aparte (`:70-71`). Sin *pins* ni CT, como se decidió en la 1.3.1 (N-04).
- **Entradas hostiles:** las expresiones regulares sobre lo importado son lineales y con topes (§6). Los nombres que vienen de fuera nunca forman una ruta, salvo el caso informativo de C-5, que no es alcanzable. Lo importado nunca llega a un `Intent`, un enlace ni un `WebView` (decisión (c)).
- **Criptografía:** esta parte no cambia ninguna primitiva, longitud, AAD ni coste. Lo de la parte A no lo repito.
- **Riesgos con nombre:** no reabro las filas 51, 62, 67 y 69 de `pendientes.md`. Ninguno de mis hallazgos las toca: son de memoria y de tiempo, y lo mío es de la cadena de suministro y del repositorio.

## Revisión Ingeniero Móvil Senior

- **Compilación:**
  - AGP 9.5.0 con Kotlin integrado, KGP 2.4.21 fijado en la raíz para alinear los plugins (`build.gradle.kts:1-10`);
  - `compileSdk` y `targetSdk` 36, `minSdk` 26 (`app/build.gradle.kts:26-32`);
  - R8 con reducción de recursos;
  - `checkFragmentVersion` evita que vuelva el *fragment* 1.2.5 (`:151-179`);
  - las entradas de los tests (`:234-247`) cubren el workflow y los traspasos. Para el test de C-1 haría falta toda la carpeta `handoff`.
- **CI:**
  - `jvm-check` compila sin el SDK de Android, con la cadena de herramientas 21 y sin descargas automáticas (`build-apk.yml:229-248`);
  - `build` corre los tests, `lintRelease`, `checkLicense`, `checkFragmentVersion` y `checkAssets` antes del paso que tiene secretos (`:80-81`);
  - el riesgo que queda, un proceso de un paso anterior que lea el entorno del paso de firma, está documentado (`:83-85`) y lo cubren la verificación de dependencias y quién puede escribir en `main`.
- **Plataforma:**
  - una sola actividad exportada, con `MAIN`/`LAUNCHER`, `singleTop` y `configChanges` para el giro (`AndroidManifest.xml:21-30`);
  - el escáner no está exportado (`:32-38`);
  - el `FileProvider` no está exportado y da permiso por URI (`:41-49`);
  - no se leen *intents* entrantes;
  - la copia del sistema está apagada en todas sus vías (§7).
- **Concurrencia y ciclo de vida, en lo que miré:**
  - los `runCatching` de las pantallas que envuelven trabajo `suspend` también se tragan la cancelación (`VaultBackup.kt:95`, `:115`), pero todos cierran el paso (`ok` en falso, se borra el documento) y no tocan secretos;
  - `deleteLater` y la limpieza de temporales corren en `NonCancellable` sobre `appScope` (`SecurityToolsScreen.kt:585-589`).

  Lo de `AutoBackup`, `PendingUpgrade` y `VaultStore` queda para el HEAD final y para la parte D.
- **Rendimiento:** las expresiones regulares no tienen retroceso exponencial (§6), y `MAX_PWNED_RESPONSE_BYTES` limita la respuesta de HIBP a 2 MiB (`PwnedPasswords.kt:18`, `:37-38`).

## Textos para el Abogado Digital

- **C-1:** las tres líneas «Modelo:», que tendrían que decir «el director (esfuerzo extra)» (`advisor-t12-attack.md:270`, `advisor-t12-redteam-fixes.md:225`, `advisor-t12-redteam-fixes-2.md:164`). Es la regla del proyecto, no una ley, pero va en lo que se publica.
- **C-2:** los asuntos de `3189f75` y `bc7c266`. Si el dueño elige aceptarlos, la nota del registro tiene que decir que los commits de la rama entran en `main` con esos asuntos.
- **C-4:** `2026-11-10-clavero-1.4.1-fallos.md:30`: quitar el sujeto «el dueño» de la frase que dice qué sistema tenía su ordenador.
- **Consolidada:** la auditoría de la 2.0 no puede repetir que los commits van sin `Claude-Session` ni enlaces a claude.ai, como decían `2026-11-01-auditoria-1.3.1.md:355` y `2026-11-08-auditoria-1.4.0.md:261`. En la 2.0, 358 de 364 la llevan.
- **Opcional, sin dato:** el comentario de código «(owner's phone test, 1-11-2026)» (`ui/screens/VaultBackup.kt:114`). La R-4 de la 1.4.1 proponía «(phone test, 1-11-2026)»; no nombra ningún servicio.
- **Sin cambios que revisar:** de esta parte no sale ningún texto nuevo de la interfaz, la política ni la ficha. `SECURITY.md:7` sigue con el correo de contacto del proyecto, publicado a propósito y ya revisado en la 1.3.1 y la 1.4.1.

Modelo: el director (esfuerzo extra).
