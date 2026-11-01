### Task 6: fuera CriptoPanel (el nombre y la lectura de sus formatos)

**Archivos:**
- Modificar:
  - `app/src/main/java/app/seguridad/crypto/Crypto.kt:29-37`, `Dialect`, y lo que quede sin uso;
  - `app/src/main/java/app/seguridad/crypto/FileCrypto.kt:15-16`;
  - `app/src/main/java/app/seguridad/vault/Vault.kt`, `LEGACY_FORMAT`, la versión 1 y el valor por defecto de `format`;
  - `vault/Backup.kt`, `vault/VaultStore.kt` y `vault/PkActions.kt`, donde nombran o aceptan lo de CriptoPanel;
  - `ui/screens/SecurityToolsScreen.kt:587`, `ui/screens/VaultScreen.kt:277`, `ui/screens/SettingsScreen.kt:475` y `ui/screens/VaultBackup.kt:60`;
  - `README.md`, en las líneas 24, 44-61, 217 y cualquier otra mención.
- Tests:
  - `crypto/DialectTest.kt`, `vault/LegacyVaultTest.kt`, `vault/BackupTest.kt`, `vault/ImportHeadTest.kt` y `tools/AppNameTest.kt`;
  - los que usen `app/src/test/resources/criptopanel/`.

**Interfaces:** ninguna nueva. `Dialect` se queda con `SEGURIDAD`, si el implementador lo ve más claro que quitar el `enum`; las firmas públicas que usan los formatos de Clavero no cambian.

- [ ] **Paso 1: tests que fallan:**
  - `CriptoPanel data is refused`, con un dato real de cada tipo:
    - el texto `CP1:` de `criptopanel/texts.json` da el mismo error que un texto que no es de Clavero;
    - `criptopanel/ref.cpenc` da el error de un archivo que no es de Clavero, sin escribir salida;
    - `criptopanel/vault-v2.json` y `criptopanel/vault-noversion.json` dan el error de copia no compatible al importar, sin tocar la bóveda que hay;
  - `no visible text names CriptoPanel`, en `AppNameTest`: ningún literal de `app/src/main/java` (con `kotlinLiterals`, de `SourceTextsTest.kt`) ni de `res/values*` contiene «criptopanel», sin distinguir mayúsculas;
  - `no code reads CriptoPanel`: ningún literal de `app/src/main/java` es `"CP1:"`, `".cpenc"`, `"criptopanel"` ni `"criptopanel-vault"`.
- [ ] **Paso 2:** ejecutar `./gradlew :app:testDebugUnitTest --tests '*AppNameTest*' --tests '*DialectTest*' --tests '*LegacyVaultTest*' --offline --no-daemon`. Deben fallar.
- [ ] **Paso 3: quitar el código.**
  - Solo lo que deja de tener uso al quitar el dialecto y el formato. Hoy un archivo sin campo `format` se toma como `criptopanel-vault`; desde ahora se rechaza. Una `seguridad-vault` sin `format` ya no se abría antes (m-2 del diseño de la 1.0, con su test), y ese test sigue pasando.
  - Antes de borrar la versión 1 de la bóveda y el valor por defecto de `format`, comprobar en el código que ninguna versión de «Seguridad» dejó en disco una bóveda `criptopanel-vault`; la importación la vuelve a envolver como `seguridad-vault`. Apuntarlo en el informe con `archivo:línea`.
  - Borrar los datos de prueba de `criptopanel/` que ya no use ningún test.
  - Los tests de los formatos de Clavero se quedan como están. Si alguno mezcla los dos, se quita solo la parte de CriptoPanel.
- [ ] **Paso 4: textos.**
  - Los dos de la interfaz dicen solo lo que se importa: la copia `.sgbak` y la exportación `.json` de la 1.0.
  - En el README:
    - la sección «Compatibilidad con CriptoPanel» desaparece, y lo que dice de los formatos de Clavero pasa a la sección de formatos;
    - una línea de las notas de la versión dice «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`, `.cpenc` y su bóveda)»;
    - la línea 217 de la lista de pruebas desaparece.
  - KDoc y comentarios: fuera las menciones que ya no son verdad.
- [ ] **Paso 5:** las dos verificaciones globales, con 0 fallos. El número de tests puede bajar en esta tarea, solo por los tests de CriptoPanel que se borran; el informe da la lista.
- [ ] **Paso 6: commit** «fuera CriptoPanel: Clavero deja de leer CP1:, .cpenc y criptopanel-vault» y push.

