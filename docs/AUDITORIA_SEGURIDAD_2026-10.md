# Revisión de seguridad — 25 de octubre de 2026

## Estado y alcance

Revisión estática de los archivos accesibles en `main`: manifiesto Android, almacenamiento de la bóveda, derivación de claves y cifrado, TOTP/QR, flujo de comprobación de contraseñas filtradas, importación/exportación, configuración Gradle y GitHub Actions. También se inspeccionaron tests existentes y sus casos de manipulación.

**Esto no equivale a una auditoría independiente completa.** No se ha ejecutado el APK en dispositivos reales ni se ha realizado un pentest de dispositivo, un análisis dinámico, un escaneo de secretos sobre todo el historial Git ni una revisión criptográfica formal. No se puede concluir que la aplicación esté libre de vulnerabilidades. Los resultados de CI de los cambios de esta revisión estaban pendientes al redactar este documento y deben verificarse en Actions.

## Resumen de hallazgos

| Severidad | Hallazgo | Estado |
|---|---|---|
| Media | Los secretos de firma podían quedar disponibles al ejecutar código de ramas/PR no fiables | Corregido en el workflow; validar CI |
| Media | La función pública de Argon2id no validaba los parámetros por sí misma | Corregido; test de regresión añadido |
| Baja–media | Un QR TOTP malformado podía producir una excepción al decodificar escapes URL | Corregido; tests de regresión añadidos |
| Baja | La respuesta de Have I Been Pwned no tenía límite de tamaño | Corregido; test de regresión añadido |
| Baja | Las credenciales de formularios de seguridad permanecían en estado de pantalla tras su uso | Corregido; se vacían después de las operaciones |
| Baja–media | El bloqueo de CA eMudhra incluía raíces y nombres de emisor no necesarios, con riesgo de falsos positivos | Corregido; solo se bloquean las ocho huellas SPKI de intermedias afectadas |
| Bloqueante para publicación pública | No se había revisado con un escáner el historial completo por secretos y metadatos privados | Pendiente |
| Bloqueante para declarar la app «auditada» | Faltan verificación dinámica independiente y pruebas manuales en dispositivos | Pendiente |

## Hallazgos y correcciones

### [Media, corregido] Secretos de firma en GitHub Actions

- **Ubicación:** `.github/workflows/build-apk.yml:33-44, 130-160`.
- **Problema:** el proceso de compilación ejecuta scripts Gradle y código del repositorio. Si se entregan las credenciales de firma a una ejecución controlada por código no fiable, ese código puede intentar exfiltrarlas.
- **Cambio:** las variables de firma del job principal solo se rellenan para push a `main`, o para push de etiquetas de versión `v*`; los PR y ramas auxiliares no reciben los secretos. La generación del AAB limita igualmente el acceso a push de etiqueta o ejecución manual desde `main`. Un push a `main` falla si falta el almacén de firma, evitando publicar silenciosamente un APK firmado con la clave de prueba.
- **Verificación pendiente:** confirmar en Actions que la expresión YAML/if se interpreta como se espera y que el trabajo termina correctamente. Mantener las etiquetas de versión bajo control de personas de confianza.

### [Media, corregido] Validación de la API pública de Argon2id

- **Ubicación:** `app/src/main/java/app/seguridad/crypto/Crypto.kt:113-127`.
- **Problema:** la lectura de cabeceras cifradas validaba parámetros de KDF, pero la función pública `argon2id()` no imponía ese límite a llamadas directas. Parámetros arbitrarios podían solicitar demasiada memoria o trabajo de CPU.
- **Cambio:** valida tamaño de salt (16 bytes), limita la salida a 4–1024 bytes y ejecuta `KdfParams.checkSane()` antes de inicializar Bouncy Castle.
- **Pruebas añadidas:** `app/src/test/java/app/seguridad/crypto/CryptoTest.kt`: parámetros excesivos, iteraciones inválidas, salt malformado y longitud de salida fuera de rango.
- **Referencia:** RFC 9106; CWE-400 (consumo de recursos no controlado).

### [Baja–media, corregido] QR TOTP malformados

- **Ubicación:** `app/src/main/java/app/seguridad/crypto/Totp.kt:78-107`.
- **Problema:** secuencias de escape porcentual inválidas en el query o la etiqueta del URI podían hacer que `URLDecoder.decode` lanzara una excepción. Un QR externo debe tratarse como entrada no confiable.
- **Cambio:** la decodificación de la etiqueta y parámetros se incluye en manejo de errores; una carga malformada devuelve `null` en lugar de propagar la excepción.
- **Pruebas añadidas:** `app/src/test/java/app/seguridad/vault/VaultTotpTest.kt`: escapes incompletos en query y etiqueta.
- **Referencia:** CWE-20 (validación de entradas).

### [Baja, corregido] Secretos temporales retenidos en el estado de pantalla

- **Ubicación:** `app/src/main/java/app/seguridad/ui/screens/SecurityToolsScreen.kt` (cifrado de texto/archivos y comprobador de contraseñas).
- **Problema:** las pantallas conservaban la contraseña introducida después de terminar la comprobación o el cifrado; en el caso de cifrar texto, la frase semilla o el texto claro seguía en el campo de entrada.
- **Cambio:** el comprobador limpia el campo de contraseña al terminar; el cifrado de texto captura los parámetros al iniciar la operación, vacía el campo de contraseña y elimina el texto original del formulario tras procesarlo; el flujo de archivos limpia la contraseña tras su uso. Las claves aleatorias introducidas se conservan en el formulario para no perderlas accidentalmente; el usuario debe copiarlas/guardarlas antes de abandonar la pantalla.
- **Limitación:** Kotlin `String` es inmutable, de modo que limpiar el estado elimina una referencia visible, pero no garantiza borrar inmediatamente todas las copias anteriores de la memoria.

### [Baja–media, corregido] Bloqueo excesivo de certificados raíz

- **Ubicación:** `app/src/main/java/app/seguridad/net/DistrustedCertificates.kt`.
- **Problema:** la mitigación de eMudhra también rechazaba tres raíces por su nombre distinguido y por sus huellas, lo que podía denegar certificados legítimos no firmados por las ocho claves intermedias comprometidas.
- **Cambio:** la denegación se limita a las ocho huellas SHA-256 SPKI específicas de las intermedias comprometidas; la validación normal de la cadena sigue delegada al almacén de confianza del sistema.
- **Pruebas:** actualizadas para comprobar que se rechaza la intermedia comprometida y que los certificados raíz no afectados siguen las decisiones del sistema. La ejecución de CI debe confirmarlo.

### [Baja, corregido] Respuesta remota sin límite de tamaño

- **Ubicación:** `app/src/main/java/app/seguridad/net/PwnedPasswords.kt:14-36`.
- **Problema:** `response.body.string()` leía el cuerpo remoto completo en memoria sin límite explícito.
- **Cambio:** se limita a 2 MiB, se lee como ASCII y se lanza `IOException` si se excede el máximo.
- **Prueba añadida:** `app/src/test/java/app/seguridad/net/PwnedPasswordsTest.kt`: respuesta de más de 2 MiB.
- **Contexto:** el endpoint es fijo y usa HTTPS con el cliente de confianza del sistema, endurecido para rechazar las CA identificadas en el código. El límite es defensa en profundidad, no evidencia de que ese servicio haya enviado una respuesta maliciosa.

## Controles positivos observados

- El manifiesto declara `allowBackup="false"` y se excluyen los datos sensibles de las reglas de backup/transferencia.
- La bóveda utiliza una clave de datos aleatoria envuelta por una clave derivada con Argon2id y cifrado autenticado AES-GCM; hay AAD de formato/campos y migración compatible con formatos antiguos.
- Las pruebas existentes incluyen vectores de referencia de AES-GCM, ChaCha20-Poly1305, Argon2id y TOTP, más manipulación, contraseña incorrecta, truncamiento y reordenación de registros.
- Los archivos se cifran por bloques autenticados; los nombres restaurados se sanea para evitar rutas.
- La pantalla principal y el escáner usan `FLAG_SECURE`; la bóveda borra la clave en memoria al bloquear.
- El cliente HTTP deshabilita cookies y caché, impone timeouts y solo usa el endpoint previsto para la consulta de contraseñas filtradas.
- Las acciones de GitHub observadas están fijadas a SHA y el permiso de escritura se limita al trabajo que publica releases.

## Riesgos y verificaciones aún abiertas

1. **Historial y logs antes de hacer público:** los commits contienen metadatos de autores y referencias a sesiones de herramientas de desarrollo. Al pasar a público, el código, actividad y logs de Actions podrán ser visibles; GitHub advierte expresamente de ello. Revisa todo el historial, los artefactos/logs y la configuración de secretos con un escáner como Gitleaks o TruffleHog. Si aparece un secreto real, revócalo y rótalo; borrar el archivo en un commit posterior no lo neutraliza.
2. **Clave de prueba:** `app/debug.keystore` está versionada de forma intencional y es pública. No es la clave privada de subida de producción, pero cualquier APK firmado con ella no debe distribuirse como release de confianza. Conserva la validación de certificado del flujo de publicación y configura los secretos de firma.
3. **Diseño criptográfico propio:** la opción «Cascada» compone varios AEAD. En la revisión estática no se ha confirmado un ataque práctico contra esa composición, pero tampoco cuenta con un análisis formal demostrado. No se debe afirmar que sea más segura por usar más algoritmos; AES-256-GCM o ChaCha20-Poly1305 son opciones más simples y ampliamente analizadas.
4. **Pruebas reales pendientes:** probar la huella/Android Keystore, escáner de cámara, transferencias por selector de archivos, pantalla de Recientes, portapapeles y recuperación después de interrupciones en varios Android.
5. **Secretos en memoria:** Java/Kotlin no garantiza que todas las copias temporales de cadenas y contraseñas desaparezcan de la memoria tras borrarlas. El código limpia algunos `ByteArray` y `CharArray`, pero los `String` inmutables no permiten borrado fiable. Esto es una limitación de la plataforma y no implica por sí solo una explotación remota.
6. **Dependencias:** hay verificación de checksums en Gradle, pero eso autentica los bytes resueltos, no demuestra por sí solo que no existan CVE. Confirmar versión/avisos de seguridad actuales antes de cada publicación.

## Criterios de salida antes de declarar una release segura

- CI verde para los commits de corrección, incluidos tests JVM, tests Android, lint y comprobación de licencias.
- Escaneo de secretos en archivos actuales y en todo el historial, revisión de artefactos y logs de Actions.
- Pruebas manuales en dispositivos y emuladores compatibles.
- Revisión independiente del protocolo criptográfico y de la gestión de claves.
- Confirmación de que la clave de firma de producción no está en el repositorio ni en logs.

**Conclusión:** se han corregido cuatro problemas concretos de validación/endurecimiento y se han añadido pruebas. Con la evidencia disponible no es responsable afirmar que la aplicación sea ya «segura» o que la auditoría sea exhaustiva; la verificación pendiente forma parte del resultado.
