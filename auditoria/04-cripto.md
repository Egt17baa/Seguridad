# Fase 4, criptografía: Clavero 1.3.0

- **Fecha:** 1-11-2026.
- **Código revisado:** `b08d2ec`, la 1.3.0 publicada (etiqueta `v1.3.0` en `b6ea21b`, con el mismo código).
- **Método:** `/auditor-android-cripto:auditar-cripto` (plugin `auditor-android-cripto` 1.0.0), con el checklist de la fase 4 de `auditoria-cripto-android`. Se sumaron `experto-seguridad-cripto` y `seguridad-criptografia-movil` de Ingeniero Móvil Senior, como pide `CLAUDE.md`.
- **Quién:** dos subagentes `revisor-cripto` en paralelo; el controlador comprobó después sus citas.
- **Detalle completo:** el código citado, los vectores CVSS, las correcciones en Kotlin, los tests propuestos y la tabla del checklist con su evidencia están en:
  - [`04-cripto-parteA.md`](04-cripto-parteA.md): bóveda, huella, cifrado de textos y archivos, y copias;
  - [`04-cripto-parteB.md`](04-cripto-parteB.md): clave pública, generadores, TOTP y red.

## Alcance

- **Ficheros revisados:**
  - los 20 de `app/src/main/java` que importan `javax.crypto`, `java.security`, `android.security.keystore` u `org.bouncycastle`;
  - otros 21 que manejan la maestra, las claves o los secretos sin importar esas API (`VaultStore.kt`, `AppContainer.kt`, `PkActions.kt`, `Signatures.kt`, `PwnedPasswords.kt`…). La lista está en cada parte.
- **Lectura independiente:** cada revisor escribió sus hallazgos antes de leer `docs/auditorias/2026-10-31-auditoria-1.3.0.md`, y después marcó cuáles eran nuevos.
- **Herramientas que faltan:** `semgrep`, `detekt` y `ktlint` no están instalados en esta sesión. Los patrones de `reglas/cripto.yml` se buscaron con `grep`.
- **Sin compilar ni probar en el móvil:** no se ejecutó Gradle, para no escribir en el árbol del repositorio, y no hay pruebas en un móvil. Los tests propuestos están sin correr.

## Resumen por severidad

| Severidad | Número | Hallazgos |
|---|---|---|
| Crítica | 0 | |
| Alta | 0 | |
| Media | 2 | CRY-B01, CRY-A01 |
| Baja | 1 | CRY-A03 |
| Informativa | 2 | CRY-A02, CRY-B02 |

Los cinco están confirmados leyendo el código. Ninguno obliga a cambiar un formato que se lee para siempre: `SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, `CP1:`, `.cpenc`, `criptopanel-vault` y los mensajes y tarjetas de clave pública siguen igual.

## Hallazgos

| ID | Severidad | MASVS | Hallazgo | Dónde | Frente a la auditoría del 31-10 |
|---|---|---|---|---|---|
| CRY-B01 | Media, 4.2 (`AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:L/A:N`) | NETWORK-1 | La desconfianza de eMudhra mira la cadena que manda el servidor, no la que valida Android, y la caché de intermedios del sistema se la salta | `net/DistrustedCertificates.kt:77-96` | Nuevo |
| CRY-A01 | Media, 4.7 (`AV:L/AC:H/PR:N/UI:R/S:U/C:H/I:N/A:N`) | CRYPTO-2, AUTH-2 | Una maestra nueva solo necesita 10 caracteres: se aceptan «contraseña» y «1234567890», y las copias `.sgbak` salen del móvil | `ui/screens/VaultBackup.kt:45,222`, `ui/screens/VaultScreen.kt:254` | Nuevo |
| CRY-A03 | Baja, 1.9 (`AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N`) | STORAGE-2, CRYPTO-2 | Copias de las claves en `SecretKeySpec`, `KeyParameter` y `HKDFParameters` que nadie pone a cero | `crypto/Crypto.kt:258,271,311,320,384` | Conocido en parte (CIFRADO-1, §5.2): allí se trató la contraseña; las claves son nuevas |
| CRY-A02 | Informativa, 0.0 | CRYPTO-2 | La DEK que envuelve la huella no va atada a la bóveda. Si queda la de una bóveda anterior, la huella da «Contraseña incorrecta o datos dañados» y no se desactiva sola | `android/BiometricVault.kt:307-313`, `vault/VaultStore.kt:65-68` | Nuevo |
| CRY-B02 | Informativa, 0.0 | CRYPTO-1, CODE-4 | TOTP: un algoritmo, unos dígitos o un periodo no admitidos se cambian en silencio por los de por defecto, y el comentario de `:59` cita mal RFC 4226 | `crypto/Totp.kt:59,65-69,87-89,131-132` | Nuevo |

### CRY-B01, en pocas palabras

`DistrustingTrustManager.refuse` se ejecuta después de que el sistema acepte la cadena, y solo mira los certificados que envió el servidor.

**El fallo:**
- El validador de Android (Conscrypt `TrustManagerImpl`) guarda los intermedios de toda cadena que valida y los usa para completar las que llegan sin intermedio.
- El cliente de la app se crea una sola vez por proceso (`AppContainer.kt:99`, `by lazy`), así que esa caché dura mientras la app esté abierta.

**El ataque:** necesita un intermediario en la red, una de las claves de eMudhra que se pueden recuperar y que la víctima repita la comprobación.
1. El atacante manda la cadena con el intermedio comprometido. La app la rechaza, pero el sistema ya ha guardado el intermedio.
2. En el siguiente intento manda solo la hoja falsa. El sistema la completa con lo que guardó, y `refuse` no ve nada.

**Qué consigue:**
- los prefijos de 5 caracteres del SHA-1 de las contraseñas que se comprueban, también las de «Salud», con la IP de la víctima;
- cambiar la respuesta: decir que una contraseña filtrada no aparece, o lo contrario.

**Corrección mínima:** comprobar la cadena **antes** de dársela al sistema, moviendo una línea en las tres sobrecargas de `checkServerTrusted`. Así el intermedio nunca entra en la caché de este cliente.

**Corrección completa:** además, comprobar **después** la cadena que validó el sistema, con `X509TrustManagerExtensions`, inyectada desde Android para que `net/` siga compilando en `jvm-check`. El código y dos tests JVM están en la parte B.

### CRY-A01, en pocas palabras

Hoy la única regla es la longitud. El diseño descartó atar `vault.bin` al Keystore porque contaba «con una maestra fuerte y Argon2id» (`docs/superpowers/specs/2026-10-23-seguridad-design.md:151`). Quien tiene una copia `.sgbak` puede probar contraseñas fuera del móvil, sin límite de intentos.

**Corrección propuesta:** al crear la bóveda o cambiar la maestra, nunca al abrir ni al importar, exigir también `estimateBits ≥ WEAK_BITS`, la misma estimación que ya usa «Salud».

**Límites:** quita los casos evidentes pero no garantiza una maestra fuerte; «Barcelona1» todavía pasaría. Las opciones para ir más allá están en la parte A.

## Comprobaciones del controlador

- **Citas:** todas las de los cinco hallazgos existen en `b08d2ec` con el texto citado: `VaultBackup.kt:45,222`, `VaultScreen.kt:254`, `Crypto.kt:258,271,311,320,384`, `BiometricVault.kt:307-313`, `VaultStore.kt:65-68`, `DistrustedCertificates.kt:77-96`, `Totp.kt:59-69,87-89,129-133` y la línea 151 del diseño.
- **CRY-B01:** se confirma que el gestor de confianza vive todo el proceso. `secureClient` llama una vez a `distrustCompromisedCas()` (`net/HttpClient.kt:14`, `DistrustedCertificates.kt:110-116`), y `AppContainer.client` es `by lazy`. Lo de la caché de Conscrypt viene de leer su código, rama `master`. Falta la prueba en un móvil que describe la parte B.
- **Importar con un mínimo de 10 caracteres:** la parte A dejó en el aire si una copia de CriptoPanel con una maestra más corta dejaría de importarse (`VaultBackup.kt:222` exige 10 también a la contraseña de la copia). No pasa:
  - `MIN_MASTER = 10` existe en CriptoPanel desde que tuvo bóveda (commit `8595bee`, versión 1.8.0) y nunca cambió;
  - en Seguridad existe desde `f141966`, el primer commit de la interfaz.

  Toda copia que se pueda importar se hizo con 10 caracteres o más.
- **Palabras prohibidas:** se cambiaron dos expresiones de la lista del proyecto que habían quedado en los informes, una en el código propuesto de la parte B y otra en la parte A. Ahora solo queda «no garantiza», que está permitido.

## Lo que está bien

El checklist completo, con su evidencia, está en cada parte. En resumen:

- **Algoritmos:** nada de ECB, DES, RC4, MD5 ni RSA con PKCS#1 v1.5. El SHA-1 solo aparece en TOTP (el estándar) y en el prefijo de Have I Been Pwned (lo que exige su API).
- **Nonces:** son aleatorios de 12 bytes en los textos y en la bóveda. En los archivos hay STREAM con contador que no da la vuelta y una clave nueva por archivo. Ninguno se reinicia al restaurar ni al reinstalar.
- **Derivación de claves:** Argon2id con sal aleatoria. Al importar nunca se baja el coste (A8).
- **Huella:** la clave está en el Keystore, con StrongBox cuando hay, autenticación por uso y `setUnlockedDeviceRequired`.
- **Clave pública:** HPKE con X-Wing, Ed25519 y Ed25519ph.
  - Todas las firmas separan dominios. La firma del remitente cubre la cabecera y la identidad del destinatario.
  - El resultado de `verify()` se usa siempre, y una firma inválida nunca se enseña como «sin firmar».
  - Se rechazan las X25519 de orden pequeño, y Bouncy Castle 1.86 comprueba el módulo de ML-KEM.
- **Generadores:** solo `SecureRandom` y `nextInt(bound)`, sin sesgo de módulo. Las listas de palabras están completas y sin repeticiones.
- **Comparaciones:** en tiempo constante donde hace falta.
- **Bouncy Castle:** con instancia propia, nunca pidiendo `"BC"` por nombre.

## Orden propuesto

Cada corrección entra como cualquier otro cambio: plan, implementación con TDD, revisión, re-revisión y PR a `main`, con **build** y **jvm-check** en verde. Qué versión, 1.3.1 o 1.4.0, lo decide el dueño.

1. **CRY-B01:** la mínima es mover tres líneas, y la completa añade la comprobación de la cadena validada. Es la única que toca la red.
2. **CRY-A01:** necesita un texto nuevo y una decisión del dueño sobre la estimación. Las opciones están en la parte A.
3. **CRY-A02:** desactivar la huella ante una `CryptoFailure` en el desbloqueo con huella. No cambia ningún formato.
4. **CRY-A03:** poner a cero `KeyParameter.getKey()` en los caminos de Bouncy Castle, y apuntar el resto, `SecretKeySpec` con Conscrypt, como riesgo aceptado.
5. **CRY-B02:** rechazar los ajustes de TOTP no admitidos en lugar de cambiarlos, y corregir el comentario de RFC 4226.

## Textos para el Abogado Digital (revisión final)

Ninguno se ha aplicado. Salen de las correcciones propuestas, y el texto exacto está en cada parte:

- **CRY-A01:**
  - `MASTER_TOO_WEAK`: «Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).»
  - «Mínimo 10 caracteres».
- **CRY-A02:** `FINGERPRINT_STALE`, «La huella ya no abre esta bóveda (se cambió la contraseña maestra o se importó una copia). Entra con la contraseña maestra y vuelve a activarla si quieres.»
- **CRY-A03:** ningún texto en la interfaz; solo una línea en la tabla de riesgos residuales.
- **CRY-B02:** dos textos para los ajustes de TOTP que la app no admite.
- **CRY-B01:** ningún texto nuevo.

## Revisión experto-seguridad-cripto

- **Severidades:** proporcionadas, y ningún hallazgo necesita criptografía propia ni una primitiva nueva.
  - **CRY-B01** es el único con un ataque por red. La complejidad es alta y el daño se limita a la comprobación de filtraciones: la petición solo lleva el prefijo (`net/PwnedPasswords.kt:31-32`). Ninguna clave ni ningún secreto de la bóveda sale.
  - **CRY-A01** baja la barrera de un ataque fuera de línea a una copia, que el modelo de amenaza incluye (auditoría del 31-10, §1.2).
  - Los otros tres son de robustez o de higiene de memoria.
- **Correcciones:** las cinco usan lo que ya hay. La plataforma da `X509TrustManagerExtensions`, la app ya tiene `estimateBits` y `KeyParameter` se puede poner a cero.

## Revisión Ingeniero Móvil Senior

- **CRY-B01:**
  - `X509TrustManagerExtensions` existe desde la API 17 y entiende el `RootTrustManager` de Network Security Config desde la 24; `minSdk` es 26.
  - Hay que inyectarla desde `app/` para no romper `jvm-check`, que compila `net/` en la JVM (`jvm-check/build.gradle.kts:14-18`).
- **CRY-A02:** la corrección se queda en el desbloqueo con huella, `VaultScreen.kt:297-299`. No toca el ciclo de vida ni el giro, que ya entregan la clave aunque el `scope` de la pantalla haya terminado (BOV-3).
- **CRY-A01:** la maestra sigue en `remember` y no en `rememberSaveable` (`VaultScreen.kt:228-229`, `VaultBackup.kt:220-221`), así que una regla nueva no la lleva a un `Bundle`.
