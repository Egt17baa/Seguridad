# Common context for every 1.3.0 subagent

Repo /home/user/Seguridad, branch ccr-a1a6eabb-765f9g. Android app (Kotlin + Compose), UI in Spanish, code comments in English like the surrounding code.

Read first: CLAUDE.md, .claude/skills/experto-seguridad-cripto/SKILL.md (and the references touching your task). The design is docs/superpowers/specs/2026-10-27-seguridad-1.3.0-herramientas-design.md; UI copy is in docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md (use verbatim). Read only the sections relevant to your task.

Commits: git -c user.name=Claude -c user.email=noreply@anthropic.com commit ... ; message ends with a blank line and 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'. No session links, no personal emails. Do NOT push (the controller pushes).

Builds are offline: export ANDROID_HOME=/opt/android-sdk; use --no-daemon --max-workers=1 --offline. Pure code (not under ui/ or android/, not MainActivity/SeguridadApp) is compiled by jvm-check: gradle -p jvm-check test --no-daemon --max-workers=1 --offline

## Skills that work together (owner's rule, 29-10-2026)
Every subagent combines Superpowers (process), experto-seguridad-cripto, Ingeniero Móvil Senior and Abogado Digital, plus any other available skill that touches the task. Use the Skill tool by name, or read the SKILL.md directly:
- experto-seguridad-cripto: .claude/skills/experto-seguridad-cripto/SKILL.md (always).
- ingeniero-movil-senior:desarrollo-movil-senior: /root/.claude/plugins/synced/29dc361b-5f33-4571-90d2-1bd51065e7b5_a2e29aa8-2531-41f4-a811-ba00a122ee7d/ingeniero-movil-senior/skills/desarrollo-movil-senior/SKILL.md (always for Kotlin/Compose/Android code: lifecycle, coroutines, rotation, process death).
- ingeniero-movil-senior:seguridad-criptografia-movil: /root/.claude/plugins/synced/29dc361b-5f33-4571-90d2-1bd51065e7b5_a2e29aa8-2531-41f4-a811-ba00a122ee7d/ingeniero-movil-senior/skills/seguridad-criptografia-movil/SKILL.md (when touching Keystore, StrongBox, biometrics, crypto, clipboard, keyboard, files).
- ingeniero-movil-senior:auditoria-seguridad-movil: /root/.claude/plugins/synced/29dc361b-5f33-4571-90d2-1bd51065e7b5_a2e29aa8-2531-41f4-a811-ba00a122ee7d/ingeniero-movil-senior/skills/auditoria-seguridad-movil/SKILL.md (reviewers: OWASP MASVS).
- abogado-digital:despacho-digital (+ privacidad-proteccion-datos, regulacion-digital-ue, propiedad-intelectual-ia as relevant): /root/.claude/plugins/synced/29dc361b-5f33-4571-90d2-1bd51065e7b5_a2e29aa8-2531-41f4-a811-ba00a122ee7d/abogado-digital/skills/ — every new or changed UI/doc text is reviewed by the Abogado Digital at the end of each task, and its changes are applied before the task is complete.
Read only the sections relevant to your task.

Your report must end with the sections 'Revisión experto-seguridad-cripto' and 'Revisión Ingeniero Móvil Senior' with archivo:línea evidence, and, if the task adds or changes texts, 'Textos para el Abogado Digital' (each text verbatim with its const name and archivo:línea).

## Restricciones globales

- **Formatos:** no cambia ninguno. `SG1:`, `.sgenc`, `seguridad-vault` v1 a v3, `.sgbak` v1 y v2, `CP1:`, `.cpenc`, `criptopanel-vault`, `SGK1:`, `SGM1:` y `SGS1:` se siguen leyendo, y sus tests no se tocan. Si una tarea parece pedir un formato nuevo, se para y se consulta.
- **Hermética:**
  - sin permisos nuevos; en concreto, nunca `ACCESS_MEDIA_LOCATION` ni `READ_MEDIA_IMAGES`;
  - sin conexiones nuevas;
  - sin dependencias nuevas;
  - ningún componente exportado nuevo: solo el `FileProvider`, no exportado y con `grantUriPermissions="true"`.
- **Entrada no fiable:** se comprueba el tamaño antes de leer. Límites:
  - 50 MB por imagen (`MAX_IMAGE_BYTES = 50 * 1024 * 1024`);
  - 4 KB de texto de QR (`MAX_QR_TEXT_BYTES = 4096`);
  - 24 MP para Tachar (`MAX_REDACT_PIXELS = 24_000_000`);
  - 2048 px por lado al decodificar un QR desde una imagen.

  Errores con mensaje en español; nunca un cuelgue, un archivo a medias ni un log.
- **Textos:** los del anexo, tal cual. Nunca «seguro» sin «no garantiza que sea seguro», «anónimo», «imposible», «100 %», «garantiza» (salvo «no garantiza»), «de verdad», «validez legal», «limpio» ni «sospechoso». Cada herramienta dice lo que no protege.
- **Nombres de archivo de salida, fijos:** `foto-limpia.<ext>` e `imagen-tachada.<ext>`, con `<ext>` = `jpg`, `png` o `webp`.
- **Aleatoriedad:** `secureRandom` de `crypto/` (el de `Generator.kt`), con muestreo por rechazo y nunca `%` sesgado.
- **StrongBox:** nunca `setUnlockedDeviceRequired`. Cualquier fallo se resuelve con el respaldo a la zona segura del procesador.
- **`FLAG_SECURE`** en toda ventana nueva (las pantallas son de `MainActivity`, que ya lo pone).
- **Verificación de cada tarea** (con `--no-daemon --max-workers=1 --offline` y `ANDROID_HOME=/opt/android-sdk`):
  - `gradle -p jvm-check test`;
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`.
- **Skills:** superpowers con `experto-seguridad-cripto`. Cada informe acaba con «Revisión experto-seguridad-cripto», con la evidencia `archivo:línea`.
- **Commits:** autor `Claude <noreply@anthropic.com>` (`git -c user.name=Claude -c user.email=noreply@anthropic.com commit`). Sin enlaces a sesiones ni correos personales. Terminan en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

