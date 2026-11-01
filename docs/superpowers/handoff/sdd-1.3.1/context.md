# Contexto común de la 1.3.1 (copiado del plan)

Diseño: docs/superpowers/specs/2026-11-01-clavero-1.3.1-design.md. Reglas del proyecto: CLAUDE.md.

## Restricciones globales

- **Formatos de Clavero:** no cambia ninguno. `SG1:`, `.sgenc`, `seguridad-vault` v1 a v3, `.sgbak` v1 y v2, la exportación JSON de la 1.0, `SGK1:`, `SGM1:` y `SGS1:` se siguen leyendo. Sus tests y sus datos de prueba (`app/src/test/resources/seguridad/` y `vectors/`) no se tocan.
- **Formatos de CriptoPanel** (`CP1:`, `.cpenc`, `criptopanel-vault`): se siguen leyendo hasta la tarea 6, que los quita. Las tareas 1 a 5 no los tocan.
- **Nombres internos intactos:** los prefijos `seguridad-…` y el `applicationId`. `AppNameTest` lo vigila.
- **Hermética:** sin permisos, conexiones, dependencias ni componentes exportados nuevos. La única red sigue siendo `api.pwnedpasswords.com`.
- **Textos:** en español. Nunca:
  - «seguro», salvo en «no garantiza que sea seguro»;
  - «garantiza», salvo en «no garantiza»;
  - «anónimo», «imposible», «100 %», «de verdad», «validez legal», «limpio», «sospechoso», «como Signal» ni «a prueba de cuánticos».

  Cada texto nuevo es una constante y se apunta en «Textos para el Abogado Digital (revisión final)».
- **Sin logs con datos; `FLAG_SECURE` como está.**
- **Secretos en memoria:** `fill(0)` en cada salida, también en las ramas de error. Nunca un secreto en `rememberSaveable`.
- **Verificación de cada tarea** (`export ANDROID_HOME=/opt/android-sdk`, con `--no-daemon --max-workers=1 --offline`):
  - `gradle -p jvm-check test`;
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`.

  Las dos con 0 fallos. Hay que decir el número de tests, que hoy es 1207 en app y 1202 en jvm-check, y solo puede subir, salvo en la tarea 6.
- **Skills:**
  - superpowers, con TDD;
  - `experto-seguridad-cripto` (`.claude/skills/experto-seguridad-cripto/SKILL.md` y la referencia de su tema);
  - de Ingeniero Móvil Senior, `seguridad-criptografia-movil` y `desarrollo-movil-senior`.

  Cada informe acaba con «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior», con la evidencia `archivo:línea`, y con «Textos para el Abogado Digital (revisión final)» si hay textos.
- **Commits:**
  - autor `Claude <noreply@anthropic.com>` (`git -c user.name=Claude -c user.email=noreply@anthropic.com commit`);
  - el mensaje termina solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, sin `Claude-Session:` ni enlaces a claude.ai (`git log -1 --format=%B | grep -cE "Claude-Session|claude\.ai"` da 0);
  - sin correos personales;
  - en la rama `ccr-a1a6eabb-765f9g`, con un push después de cada commit;
  - nunca `--amend`, `rebase` ni `push --force`.

## Puntos que revisar

1. **La comprobación de filtraciones en un móvil normal** («Comprobar contraseña» y «Salud») tiene que seguir funcionando con la tarea 1. El nombre del servidor sale de la sesión del apretón de manos; si fuera `null`, la conexión se rechaza siempre. Lo cubren un test de que se pasa el `peerHost` (tarea 1, paso 1) y la prueba del dueño en el móvil.
2. **Importar una copia cuya maestra no pasaría la regla nueva** («password123» o «aaaaaaaaaa», por ejemplo) sigue funcionando. Con la ampliación del dueño, «contraseña123» también se rechaza al crear o cambiar la maestra, pero una copia con esa maestra se sigue importando. La regla solo está en «Crea tu bóveda» y en «Nueva contraseña maestra» (tarea 2, paso 1, test de código fuente).
3. **Lo que propone el Generador pasa la regla nueva** (las frases siempre; una contraseña de 16 caracteres, salvo en menos de 1 de cada 25 000 casos, cuando sus letras forman una palabra de las listas): una frase de 5 palabras, con cualquier separador y en las dos listas, y una contraseña de 16 caracteres (tarea 2, paso 1).
4. **La huella solo se desactiva con `CryptoFailure`,** nunca con `MasterRequired` (la regla de los 7 días o el reloj) ni con otro error (tarea 3, paso 1).
5. **Ninguna entrada TOTP que hoy da código deja de darlo:**
  - todas las de los datos de prueba de Clavero (`seguridad/*.json`, `seguridad/backup-1.1.sgbak`, los de `OtpMigrationTest`) siguen siendo `isSupported()`;
  - un `algorithm` vacío cuenta como SHA1.

  Lo cubre la tarea 5, paso 1.

