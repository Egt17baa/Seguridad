# Por validar

Son dos asuntos abiertos, sin severidad. En los dos, la ruta en el código es real y la han comprobado de forma independiente dos verificadores. Lo que falta es un hecho del dispositivo o del sistema operativo que no se puede observar sin ejecutar la app. Los registros originales, en inglés y en el formato de la skill, están en `findings.json`.

---

## NV-1. Los campos de contraseña maestra y de secretos no se excluyen del autorrelleno de Android

`fingerprint: seguridad:ui/theme/Components.kt:PasswordField:autofill-not-excluded`

**Frontera:** la app Seguridad frente a otra app instalada, el servicio de autorrelleno activo (de Google o de terceros), que puede guardar en su propio almacén, y sincronizar con la nube, lo que se escribe en un campo de contraseña.

**Causa que se plantea:** no hay ninguna exclusión de autorrelleno. Ni el manifiesto ni el `decorView` fijan `importantForAutofill`, y ningún campo lleva semántica de autorrelleno. `PasswordField` solo controla el teclado con `KeyboardType.Password`, que no afecta al marco de autorrelleno. `FLAG_SECURE` tampoco lo bloquea.

**Traza:**
1. Entrada: `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt:293`. Aquí se escribe la contraseña maestra para desbloquear; al crear la bóveda se usa el mismo control, en las líneas 227-228.
2. `app/src/main/java/app/seguridad/ui/theme/Components.kt:106`. `PasswordField` es un `OutlinedTextField` con `PasswordVisualTransformation` (línea 111) y `KeyboardType.Password` (línea 112), sin semántica de autorrelleno.
3. `app/src/main/java/app/seguridad/MainActivity.kt:26`. Antes de `setContent` solo se llama a `secureWindow()`, y el `decorView` no recibe ningún ajuste de autorrelleno.
4. `gradle/verification-metadata.xml:481`. Compose UI 1.9.0 (BOM 2025.08.00) tiene integración de autorrelleno basada en semántica.

**Más evidencia:**
- `AndroidManifest.xml:19`: ni `MainActivity` ni `<application>` declaran `importantForAutofill`.
- `VaultEntryScreen.kt:291`: el campo de la frase semilla tiene la misma protección, que solo afecta al teclado.
- `Components.kt:89`: la barra de texto propia reenvía `onAutofillRequested`.
- `SecureWindow.kt:16`: solo aplica `FLAG_SECURE` y el filtro de toques.
- Spec `2026-10-23-seguridad-design.md:32`: deja fuera de alcance *ofrecer* un servicio de autorrelleno, pero no trata la *captura* de los campos propios de la app.

**Qué falta (bloqueos):**
- No hay emulador ni dispositivo para observar si se crea una sesión de autorrelleno y si llega un `FillRequest` o un `SaveRequest`.
- La documentación pública de Compose describe la participación en el autorrelleno solo para campos con `ContentType`. No se puede saber desde el código si Compose 1.9.0 incluye también estos campos, que no lo llevan.
- Además, tiene que haber un servicio de autorrelleno activo, y el usuario tiene que aceptar el aviso de guardar.

**Validación local (emulador, datos de prueba):**
1. Compilar el APK debug de `5d6c318` y usar un emulador con API 30 o superior, con un servicio de autorrelleno de prueba que registre en el log `onFillRequest` y `onSaveRequest`.
2. Activar el log detallado: `adb shell cmd autofill set log_level verbose`.
3. Crear una bóveda con la contraseña `test-master-pass-123`, bloquearla, desbloquearla y salir de la app.
4. Revisar `adb shell dumpsys autofill` y el log del servicio. Comprobar si hay una sesión, qué nodos incluye, si aparece el aviso de guardar y si el `SaveRequest` contiene el valor.
5. Repetir con los campos de contraseña de una entrada, frase semilla y secreto TOTP.
6. Prueba de regresión: tras añadir el arreglo, `dumpsys autofill` no debe mostrar ninguna sesión de la app.

**Comprobación en tu móvil:** usa una bóveda de prueba con una contraseña inventada y con tu servicio de autorrelleno habitual activo. Desbloquea, sal de la app y fíjate en si aparece «¿Guardar contraseña?». No aceptes nunca con una contraseña real.

**Arreglo propuesto (una línea; también en `SecureCaptureActivity`):**
```kotlin
// MainActivity.onCreate, antes de setContent
window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
```
Si algún día se quiere ofrecer autorrelleno de las entradas, se haría con un servicio propio, sin quitar esta exclusión.

---

## NV-2. Otra app puede hacer que la rotación de copias automáticas borre las copias buenas

`fingerprint: AutoBackup.rotate/name-only-ownership-and-ordering`

**Frontera:** la app Seguridad frente a otra app sin permisos que pueda crear archivos en la carpeta que el dueño eligió para las copias. La otra app consigue que Seguridad borre, con su permiso persistente sobre la carpeta, archivos que ella no podría borrar por sí misma (un *confused deputy*).

**Causa que se plantea:** la rotación decide qué copias son suyas y cuáles son las más nuevas usando solo el nombre visible del archivo (`seguridad-auto-AAAAMMDD-HHMMSS.sgbak`), ordenado como texto. No guarda qué documentos creó ella.

**Traza:**
1. Entrada: `app/src/main/java/app/seguridad/android/SafBackupTarget.kt:63`. `list()` devuelve todos los archivos de la carpeta que empiezan por `seguridad-auto-`, los haya creado quien los haya creado.
2. `app/src/main/java/app/seguridad/vault/AutoBackup.kt:161`. Después de cada copia se ejecuta `rotate()`.
3. `app/src/main/java/app/seguridad/vault/AutoBackup.kt:242`. Se ordena por nombre de forma descendente y se conservan los `keep-1` primeros. Un archivo plantado con fecha `9999…` ocupa esos huecos.
4. Destino: `app/src/main/java/app/seguridad/vault/AutoBackup.kt:247`. `folder.delete(n)` borra las copias genuinas con `DocumentsContract.deleteDocument` (`SafBackupTarget.kt:65-70`).

**Más evidencia:**
- `AutoBackup.kt:69`: la única definición de «copia propia» es la expresión regular del nombre.
- `AutoBackup.kt:36`: el propio contrato de `BackupTarget` dice que los archivos deben seguirse por su identificador.
- `SafBackupTarget.kt:75`: `children()` no filtra por quién creó cada archivo.
- `AutoBackupTest.kt:204`: un test que ya existe demuestra que los nombres con fecha futura desplazan a los antiguos.
- `SettingsScreen.kt:130`: la carpeta se elige con `OpenDocumentTree`, sin restringir cuál.

**Impacto:** si el ataque es posible, la retención de 3, 5 o 10 copias se queda en una sola copia genuina, sin ningún aviso. Se pierde, por ejemplo, la copia de antes de un borrado accidental. No se revela ni se altera el contenido de la bóveda: los archivos plantados no se abren con tu contraseña y la copia más reciente siempre se conserva.

**Qué falta (bloqueos):**
- No se ha podido ejecutar el test con una carpeta simulada.
- Que otra app pueda escribir en la carpeta depende de cuál elijas, del proveedor y de las reglas de MediaStore y del almacenamiento con ámbito de cada versión de Android.

**Validación local:** añadir a `AutoBackupTest.kt` un test que reutilice `MemoryTarget` y los helpers `auto()`, `opened()` y `settle()`:
- Sembrar tres copias genuinas (`seguridad-auto-20261020-100000.sgbak`, `…21…`, `…22…`) y dos plantadas (`seguridad-auto-99991231-235958.sgbak`, `seguridad-auto-99991231-235959.sgbak`), con `keep = 3`.
- Hacer un `upsert` y un `settle()`.
- Comprobar que quedan la copia nueva y las dos plantadas, y que no queda ninguna de las tres genuinas.

```
gradle -p jvm-check test --no-daemon --max-workers=1 --tests 'app.seguridad.vault.AutoBackupTest'
```

**Comprobación en un emulador (Android 11 o posterior, bóveda de prueba):**
1. Elegir `Documents/SeguridadBackups` como carpeta de las copias automáticas y hacer tres cambios para que haya tres copias.
2. Desde otra app sin permisos, insertar los dos nombres con fecha `9999` por MediaStore, con `RELATIVE_PATH=Documents/SeguridadBackups/`.
3. Comprobar que aparecen en el selector de archivos.
4. Hacer otro cambio y ver si solo quedan la copia genuina más nueva y las dos plantadas.

**Arreglo propuesto:** guardar en los ajustes los `documentId` que devuelven `createDocument` y `renameDocument`, y que la rotación solo considere, ordene por fecha de creación y borre esos documentos. Mientras tanto, conviene recomendar en Ajustes una carpeta propia que no esté en `Download/`.
