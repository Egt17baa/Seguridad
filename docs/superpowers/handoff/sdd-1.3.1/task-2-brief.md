### Task 2: CRY-A01, una maestra nueva tiene que pasar la estimación de fuerza

**Archivos:**
- Crear: `app/src/main/java/app/seguridad/vault/MasterPolicy.kt`
- Modificar:
  - `app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt:45`, de donde sale `MIN_MASTER`, y `:212-240`, `PasswordDialog`;
  - `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt:225-260`, `CreateVault`.
- Test: `app/src/test/java/app/seguridad/vault/MasterPolicyTest.kt`

**Interfaces:**
- Consume:
  - `estimateBits(password: String): Double` y `WEAK_BITS = 50.0`, de `health/Health.kt`;
  - los generadores de `crypto/Generator.kt`.
- Produce, en `vault/MasterPolicy.kt`:
  - `const val MIN_MASTER = 10`, que se mueve aquí;
  - `const val MASTER_TOO_SHORT = "Mínimo 10 caracteres"`;
  - `const val MASTER_TOO_WEAK`, con el texto de `04-cripto-parteA.md`, «Textos para el Abogado Digital», punto 1;
  - `fun newMasterProblem(candidate: String): String?`: primero `MASTER_TOO_SHORT`, después `MASTER_TOO_WEAK`, o `null`.
- Produce, en `PasswordDialog`: el parámetro `newPassword: Boolean = false`.

- [ ] **Paso 1: tests que fallan,** en `MasterPolicyTest`:
  - `newMasterProblem` da `MASTER_TOO_SHORT` con «corta»;
  - da `MASTER_TOO_WEAK` con «contraseña», «1234567890», «aaaaaaaaaa», «qwertyuiop» y «Contraseña»;
  - da `null` con «casa perro luz mar sol» y con «Barcelona1» (el límite aceptado, apuntado en el KDoc);
  - 200 frases de 5 palabras del Generador, con la lista EFF y la española y con cada separador que ofrece, dan `null`, y también 200 contraseñas de 16 caracteres del Generador;
  - un test de código fuente:
    - `VaultScreen.kt` y `VaultBackup.kt` llaman a `newMasterProblem(`;
    - el `PasswordDialog` de `"master"` pasa `newPassword = true`;
    - el de `"import"` no lo pasa;
    - ningún fichero de `ui/screens/` usa `a.length >= MIN_MASTER` fuera de `PasswordDialog`.
- [ ] **Paso 2:** ejecutar `gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon`. Debe fallar.
- [ ] **Paso 3: implementar `MasterPolicy.kt`.**
  - El KDoc dice que solo vale para contraseñas nuevas y que es una estimación sin diccionario.
  - `CreateVault` y el diálogo con `newPassword = true` enseñan el problema en `NegativeColor` en cuanto hay texto, y el botón solo se activa con `null`.
  - El diálogo de importar sigue con `a.length >= MIN_MASTER`.
  - La etiqueta «Contraseña maestra (mínimo $MIN_MASTER caracteres)» no cambia.
- [ ] **Paso 4:** las dos verificaciones globales, con 0 fallos. Los tests de `SourceTextsTest` y `ToolTextsTest` cubren los textos nuevos.
- [ ] **Paso 5: commit** «vault: una maestra nueva tiene que pasar la estimación de fuerza (CRY-A01)» y push.

**Ampliación del dueño (1-11-2026, después de la revisión de la tarea 2): una sola palabra conocida no vale.**

**Interfaces de la ampliación:**
- Produce, en `vault/MasterPolicy.kt`:
  - `fun masterCore(candidate: String): String`: minúsculas con `Locale.ROOT`, NFD sin marcas combinantes (`\p{Mn}`), y solo las letras (`Character.isLetter`);
  - `fun masterWords(lists: List<List<String>>): Set<String>`: cada palabra pasada por `masterCore`, sin vacías, más la lista de las más usadas de «Salud» pasada igual;
  - `fun newMasterProblem(candidate: String, words: Set<String> = emptySet()): String?`, que también da `MASTER_TOO_WEAK` cuando `masterCore(candidate) in words`. El orden sigue siendo: la longitud, la estimación y la palabra.
- Modifica, en `health/Health.kt`: la lista `COMMON` pasa a `internal` (o una función `internal` que la devuelva) para que `masterWords` la use. «Salud» no cambia de comportamiento.

- [ ] **Paso 6: tests que fallan,** en `MasterPolicyTest`:
  - `masterCore` da «contrasena» con «Contraseña2024!» y «abaco» con «Ábaco-12345»;
  - con `masterWords` de las tres listas reales de `app/src/main/assets/`, `newMasterProblem` da `MASTER_TOO_WEAK` con «contraseña123», «Contraseña2024», «Password1!», «iloveyou12», «abandon1234» (BIP-39 en inglés), «Ábaco12345» (BIP-39 en español) y una palabra de la lista EFF de 7 letras o más con «123» detrás;
  - da `null` con las frases de 5 palabras del Generador (las mismas 200 por lista y separador de antes) y con las contraseñas de 16 caracteres;
  - da `null` con «Barcelona1», solo si «barcelona» no está en ninguna lista; si está, el test lo dice y usa otro ejemplo que pase;
  - sin `words`, `newMasterProblem` hace lo mismo que antes;
  - test de código fuente: las dos pantallas pasan las palabras cargadas a `newMasterProblem`, y el botón de crear o cambiar no se activa sin ellas.
- [ ] **Paso 7: implementar.**
  - Las listas se leen de `assets/` (`eff_large_wordlist.txt`, `bip39_english.txt` y `bip39_spanish.txt`, como ya las lee la app) con `Dispatchers.IO`, una vez por pantalla abierta, en un estado que no es `rememberSaveable`.
  - Si la lectura falla, se usa `emptySet()` y la regla se queda en la estimación.
  - El KDoc dice el límite nuevo: dos palabras juntas pasan si la estimación llega.
- [ ] **Paso 8:** las dos verificaciones globales, con 0 fallos. Commit «vault: la maestra nueva no puede ser una sola palabra conocida (CRY-A01, ampliación del dueño)» y push.

