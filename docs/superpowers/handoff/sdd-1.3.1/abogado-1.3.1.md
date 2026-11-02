# Dictamen del Abogado Digital: Clavero 1.3.1

- **Fecha de referencia:** 1 de noviembre de 2026.
- **Objeto:** la revisión jurídica única y final antes de publicar la 1.3.1 (`CLAUDE.md`, «Skills: siempre juntas», punto 4).
- **Sobre qué:** rama `ccr-a1a6eabb-765f9g`, cabeza `18c25ce` («docs: registro de la 1.3.1, arreglos de la revisión final y documentos»), diff `9504ce3..18c25ce`. Todavía no existe `final-rereview.md`: si el repaso acotado cambia algún texto, ese texto necesita otra mirada.
- **Método:** `despacho-digital`, con `privacidad-proteccion-datos`, `regulacion-digital-ue`, `propiedad-intelectual-ia` y `contratos-tecnologicos` (este último, para la Directiva (UE) 2019/770). He leído también `experto-seguridad-cripto` (`SKILL.md`).
- **Solo lectura:** no he cambiado ningún archivo del repositorio, ni el índice ni la rama. Este dictamen está en `.superpowers/`, que git ignora (`.superpowers/sdd/.gitignore:1`).

**Hechos que doy por ciertos (hipótesis expresas):**
1. El desarrollador es un particular residente en España («Egt17baa», seudónimo), sin empresa ni ánimo de lucro. La app es gratuita, sin anuncios, compras ni donaciones (`docs/privacidad.html:25`), y él no recibe datos personales (`:29`).
2. CriptoPanel era otra app del dueño, que solo usaba él, y no tenía ninguna bóveda en ella (`progress.md`, línea «Owner (1-11-2026): no vault in CriptoPanel»; `CLAUDE.md:87`).
3. La app se publica en Google Play en todos los países menos Francia (con ultramar), Rusia y Bielorrusia, y como APK en GitHub *Releases*.

Si alguno de estos hechos cambia (donaciones, anuncios, una versión de pago, datos que lleguen al desarrollador), hay que volver a analizar el CRA, el TRLGDCU y la DSA.

---

## 1. Resumen y veredicto

**Veredicto: se puede publicar, con cambios.**

- **Ninguna norma exige cambios.**
  - La 1.3.1 no añade datos, conexiones, permisos ni dependencias (§3.6).
  - Con los hechos de arriba, no se aplican el TRLGDCU ni la Directiva 2019/770, el CRA, la Directiva 2024/2853, el AI Act, la DSA ni la LSSI (§3.7).
- **Un cambio obligatorio por la regla de veracidad del proyecto** (`CLAUDE.md`, punto 4: un texto no puede afirmar algo falso sobre el código; no lo exige ninguna norma).
  - `FINGERPRINT_STALE` da por hecha una causa que puede no ser la real: con un bloque de datos dañado sale el mismo aviso.
  - Propongo una redacción con «Puede deberse a…» (O-1).
- **Cuatro cambios recomendados:**
  - R-1: la nota de CriptoPanel del README, con la salida a través de la 1.3.0;
  - R-2: el ejemplo de User-Agent de la política;
  - R-3: la nota de la lista EFF en Licencias;
  - R-4: una frase inexacta de la auditoría de la 1.3.1.
- **CriptoPanel.** Jurídicamente, basta la nota del README.
  - La promesa «se leerán siempre» de la 1.3.0 no era una obligación ni contractual ni legal (§3.7.b).
  - R-1 es buena práctica, no una obligación.
- **Política y ficha:** siguen siendo verdad con la 1.3.1.
  - En la política solo cambiaría el ejemplo «Clavero/1.3.0» (R-2), y nada más.
  - Si se cambia, el dueño pone la fecha de `:22` el día de publicar: es un pendiente suyo (`docs/PUBLICAR.md:94`), y la propia política se compromete a ello (`:66`).
  - Si no se cambia, la 1.3.1 no obliga a tocar la política ni su fecha.
- **Premisa del encargo, corregida:** no todos los caminos que cambian la clave de datos desactivan la huella *antes*.
  - Crear la bóveda y borrarla, sí (`ui/screens/VaultScreen.kt:281-282`, `:378-379`).
  - Cambiar la maestra e importar una copia la desactivan *después* de guardar (`ui/screens/VaultBackup.kt:128-131`, `:174-175`).
  - Así que las dos causas que nombra `FINGERPRINT_STALE` sí pueden darse, aunque solo en un caso raro: una muerte del proceso o un error justo después de guardar (`vault/VaultStore.kt:883-896`).
  - La tercera causa, un bloque dañado, es la que el texto calla. Esto no cambia O-1: un «puede deberse a» con las tres causas es verdad en todos los casos.

## 2. Cambios

### O-1. `FINGERPRINT_STALE` (obligatorio por la regla del proyecto, no por una norma)

- **Dónde:** `app/src/main/java/app/seguridad/vault/VaultStore.kt:65-67`.
- **Texto actual:** «La huella ya no abre esta bóveda (se cambió la contraseña maestra o se importó una copia). Entra con la contraseña maestra y vuelve a activarla si quieres.»
- **Texto propuesto:** «La huella ya no abre esta bóveda. Puede deberse a un cambio de la contraseña maestra, a una copia importada o a datos dañados. Entra con la contraseña maestra y vuelve a activarla si quieres.»

  ```kotlin
  const val FINGERPRINT_STALE =
      "La huella ya no abre esta bóveda. Puede deberse a un cambio de la contraseña maestra, a una copia importada o a " +
          "datos dañados. Entra con la contraseña maestra y vuelve a activarla si quieres."
  ```

- **Motivo (verdad sobre el código):**
  - `StaleBiometricKey` salta cuando la clave no autentica las entradas con AES-GCM (`vault/VaultStore.kt:488-491`, `vault/Vault.kt:313-321`). AES-GCM no distingue la clave de otra bóveda de un bloque dañado, como dice el propio KDoc (`VaultStore.kt:84-87`).
  - Las dos causas que nombra el texto solo se dan si se corta la desactivación que sigue a cambiar la maestra o a importar (`VaultBackup.kt:128-131`, `:174-175`).
  - Con datos dañados, el texto de hoy afirma algo falso.
  - «Vuelve a activarla» sigue siendo verdad: `onWrongKey` la desactiva (`VaultScreen.kt:321-324`).
- **Para el equipo técnico:**
  - `VaultStoreTest.kt:996` fija el literal y hay que cambiarlo.
  - El KDoc de `VaultStore.kt:59-63` puede nombrar también el bloque dañado.
  - El texto nuevo pasa `ForbiddenWords` (no lleva ninguna palabra prohibida).

### R-1. Nota de CriptoPanel del README (recomendado, buena práctica)

- **Dónde:** `README.md:48`.
- **Texto actual:** «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`, `.cpenc` y su bóveda).»
- **Texto propuesto:** «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`, `.cpenc` y su bóveda). La 1.3.0 (`Clavero-v1.3.0.apk`, en *Releases*) los sigue abriendo: con ella se pueden descifrar esos textos y archivos para cifrarlos de nuevo, e importar la copia de la bóveda de CriptoPanel, que se vuelve a cifrar en el formato de Clavero (importar reemplaza la bóveda del móvil: exporta antes la tuya). La 1.3.0 no se instala encima de la 1.3.1: úsala antes de actualizar o en otro móvil.»
- **Motivo:** no lo exige ninguna norma (§3.7.b). Da a quien pudiera tener datos en esos formatos una salida gratuita, en la línea del art. 126 bis.4 TRLGDCU, aunque ese artículo no se aplique aquí.
- **Comprobado:**
  - la 1.3.0 leía `CP1:` y `.cpenc`, e importaba la `criptopanel-vault` «que se vuelve a cifrar con el formato nuevo» (README de `9504ce3`, líneas que quita el diff);
  - el APK está en *Releases* (`CLAUDE.md:39`);
  - importar reemplaza la bóveda (`VaultBackup.kt:161`, «Se reemplazará la bóveda actual de este móvil por la de la copia.»);
  - Android rechaza un `versionCode` menor (`docs/PUBLICAR.md:90`) y una firma distinta (`:80`).

### R-2. Ejemplo de User-Agent de la política (recomendado)

- **Dónde:** `docs/privacidad.html:46`.
- **Texto actual:** «…y el servicio ve además el nombre y la versión de la app (por ejemplo, «Clavero/1.3.0»).»
- **Texto propuesto:** «…y el servicio ve además el nombre y la versión de la app («Clavero/» seguido del número de la versión instalada, por ejemplo «Clavero/1.3.1»).»
- **Motivo:**
  - La app envía `Clavero/$appVersion` (`net/HttpClient.kt:28`), con `BuildConfig.VERSION_NAME` = 1.3.1 (`app/build.gradle.kts:35`). La forma general evita tocar la política en cada versión.
  - No es obligatorio: «por ejemplo» hace que el texto de hoy no sea falso. Además, el desarrollador no es responsable de ese tratamiento (§3.7), así que no tiene un deber propio del art. 13 RGPD, y la frase es transparencia.
- **Si se aplica:**
  - el dueño pone la fecha de `:22` el día de publicar y la sube a GitHub Pages (`PUBLICAR.md:94`; la política lo promete en `:66`);
  - ningún test fija esta frase (`FinalLegalReviewTest.kt:128-130` fija la siguiente, que no cambia).

### R-3. Nota de la lista EFF en Licencias (recomendado, exactitud)

- **Dónde:** `app/src/main/java/app/seguridad/ui/screens/LicensesScreen.kt:37`, la nota de `Component`.
- **Texto actual:** «De la Electronic Frontier Foundation. El Generador usa las mismas palabras, en el mismo orden; solo se ha quitado la columna de los números de dado. Licencia: https://creativecommons.org/licenses/by/3.0/us/»
- **Texto propuesto:** «De la Electronic Frontier Foundation, con las mismas palabras y en el mismo orden; solo se ha quitado la columna de los números de dado. El Generador la usa para las frases y, al crear la bóveda o cambiar la contraseña maestra, la app no acepta como maestra una sola de sus palabras. Licencia: https://creativecommons.org/licenses/by/3.0/us/»
- **Motivo:**
  - La atribución de CC BY 3.0 US, §4(b), ya se cumple (§3.6). El cambio solo completa la descripción del uso: la lista está en `MASTER_WORD_LISTS` (`vault/MasterPolicy.kt:34`), y la regla solo se aplica al crear la bóveda y al cambiar la maestra (`:127-130`, `VaultScreen.kt:245-257`, `VaultBackup.kt:118,234`).
  - Sigue en una sola línea, con «mismas palabras», «mismo orden» y «números de dado» y sin «sin cambios», como pide `PublishedTextsTest.kt:101-107`.

### R-4. Una frase de la auditoría de la 1.3.1 (recomendado, exactitud)

- **Dónde:** `docs/auditorias/2026-11-01-auditoria-1.3.1.md:385`.
- **Texto actual:** «…todos los caminos que cambian la clave de datos desactivan la huella antes (§3.2, CRY-A02).»
- **Texto propuesto:** «…todos los caminos que cambian la clave de datos desactivan la huella: crear la bóveda y borrarla, antes; importar una copia y cambiar la maestra, justo después de guardar, así que solo una muerte del proceso o un error en ese momento la dejaría activa (§3.2, CRY-A02).»
- **Motivo:**
  - Lo prueban `VaultScreen.kt:281-282,378-379`, `VaultBackup.kt:128-131,174-175` y `VaultStore.kt:883-896`.
  - El §3.2 del mismo informe ya lo dice bien: «Pasa solo si se corta la desactivación que sigue a cambiar la maestra o a importar».

## 3. Lo que he revisado y está bien

### 3.1 Textos nuevos de la interfaz

| Texto | Dónde | Por qué está bien |
|---|---|---|
| `MASTER_TOO_SHORT` «Mínimo 10 caracteres» | `vault/MasterPolicy.kt:24` | Es verdad: cuenta las unidades UTF-16 y los puntos de código en NFC sin caracteres de formato (`:150-152`). Coincide con la etiqueta del campo (`VaultScreen.kt:253`). |
| `MASTER_TOO_WEAK` | `MasterPolicy.kt:27-28` | Sale por la estimación por debajo de 50 bits o por una sola palabra conocida (`:153-154`). Es un aviso, no una promesa: cuando la contraseña pasa no sale ningún texto que diga que es fuerte. La política y la ficha no anuncian la regla, como pidió la nota de la tarea 2. El consejo es el de «Crea tu bóveda», ya revisado en la 1.3.0. |
| `TOTP_UNSUPPORTED_LINK` | `crypto/Totp.kt:108` | Nombra las tres causas que comprueba `isSupported` (`:105`). Un `digits=` o `period=` que no es un número también da este texto (`:214-215`), lo que es aceptable (límite aceptado en la auditoría, §6). Sale en el editor (`VaultEntryScreen.kt:265`) y en el Inspector de QR (`tools/qr/QrView.kt:195`). |
| `TOTP_UNSUPPORTED_ENTRY` | `Totp.kt:111` | Verdad: `totpCode` no calcula nada para esos ajustes. En la ficha (`VaultEntryScreen.kt:154`). |
| `TOTP_UNSUPPORTED_ROW` «2FA no admitido» | `Totp.kt:114` | En la lista (`VaultScreen.kt:465`). Corto y exacto. |

### 3.2 Textos que ya existían y salen en situaciones nuevas

- «No es una bóveda compatible» (`vault/Vault.kt:224`):
  - al desbloquear con la maestra un archivo que esta versión no abre (`VaultScreen.kt:342`, desde `Vault.kt:268`);
  - en `MasterRequired(UNSUPPORTED)` (`VaultStore.kt:137`);
  - al importar o abrir algo que no es una bóveda (`Vault.kt:406`).

  Es verdad en los tres casos. Es mejor que «Contraseña incorrecta», que podía llevar a «He olvidado la contraseña maestra» y a borrar la bóveda.
- «Por seguridad, cada 7 días hay que escribir la contraseña maestra.» ya no sale con un archivo que no se abre (`VaultScreen.kt:359-361`): ahí era falso.
- «Consejo: usa una frase de 5 o 6 palabras…» se oculta mientras sale `MASTER_TOO_WEAK`, que dice lo mismo (`VaultScreen.kt:261-263`).
- Lo que ve quien abre datos de CriptoPanel es verdad:
  - «El texto cifrado debe empezar por SG1:» (`crypto/Crypto.kt:599`);
  - «No es un dato cifrado compatible» (`Crypto.kt:512`);
  - «No es una bóveda compatible».

  Recibir el error genérico es decisión del dueño, y es aceptable con un único usuario.
- «Contraseña incorrecta o datos dañados» (`Crypto.kt:198`) para unas entradas que autentican y no se decodifican: es el texto de siempre.

### 3.3 Textos cambiados al quitar CriptoPanel

Todos son exactos y no llevan ninguna palabra prohibida:
- `VaultScreen.kt:296`: «Sirve una copia .sgbak de esta app (con los adjuntos) o una exportación .json de la 1.0.» Lo comprueba `VaultStore.kt:808-828`.
- `ui/screens/SettingsScreen.kt:475`: coincide con los botones «Exportar bóveda» e «Importar copia» (`:464-465`).
- `ui/screens/SecurityToolsScreen.kt:354`, `:409` y `:867`: solo `SG1:` y `.sgenc`.

### 3.4 Documentación cambiada

- **README:**
  - `:24`;
  - la sección «Formatos» (`:44-60`), con la nota de `:48` y la errata «Un JSON» ya corregida en `:53`;
  - la línea quitada de la prueba de CriptoPanel;
  - `:305`.

  Las pruebas de la 1.3.1 (`:308-312`) dicen verdad sobre el código:
  - «contraseña1», «contraseña123» y «contraseña» dan `MASTER_TOO_WEAK`: «contrasena» está en la lista común (`health/Health.kt:18-20`) y es su núcleo (`MasterPolicy.kt:47-57`);
  - «corta» da «Mínimo 10 caracteres»;
  - importar desactiva la huella (`VaultBackup.kt:175`).
- **`README.md:60` y `:56`, «se leerán siempre»,** para los formatos propios:
  - es un compromiso del proyecto, no una obligación jurídica (Apache-2.0, §7 y §8);
  - lo mantengo;
  - si algún día se retira un formato propio, recomiendo hacerlo como ahora, con nota y versión anterior disponible, y a ser posible avisando antes.
- **`CLAUDE.md`** («Estado actual», «Decisiones de la 1.3.1», «Pendientes» y la regla de los formatos, `:112`), **`.github/pull_request_template.md:9`**, el traspaso de la 1.3.1 y la corrección del de la 1.3.0 (`:83`): coherentes con el código y con la decisión del dueño. No hay correos personales.
- **`docs/auditorias/2026-11-01-auditoria-1.3.1.md`:** salvo la frase de R-4, la descripción es fiel al código.
  - No presenta como «cerrado» lo que no lo está: CRY-A03 queda «cerrado en parte».
  - Dice en §1.4 que no es una auditoría formal ni un pentest.

### 3.5 Política de privacidad, ficha y `PUBLICAR.md`

- **`docs/privacidad.html`, leída entera.** La 1.3.1 no cambia nada de lo que dice, salvo el ejemplo de `:46` (R-2):
  - `:25-26`: identidad, gratuidad, sin donaciones y contacto `seguridad.egt17baa@gmail.com`;
  - `:29`: «Qué datos recogemos: Ninguno». La regla de la maestra trabaja en el móvil con listas del APK (`MasterPolicy.kt:77-113`);
  - `:32-38`: almacenamiento y copias, sin cambios;
  - `:45`: los nombres «¿Filtrada?» y «Comprobar filtraciones», los 5 caracteres de la huella SHA-1 y el relleno (`net/PwnedPasswords.kt:24,32`). El cambio de CRY-B01 solo añade rechazos de certificados y no cambia lo que se envía;
  - `:49-53`: los permisos, sin cambios en el manifiesto;
  - `:57`: «no ha pasado una auditoría de seguridad independiente» sigue siendo verdad, porque la auditoría de la 1.3.1 es interna;
  - no nombra CriptoPanel ni formatos.
- **`docs/ficha-google-play.md`:** todo sigue siendo verdad.
  - «otros algoritmos estándar» se mantiene (Twofish, Serpent, Camellia y la cascada siguen: `README.md:27`).
  - La importación de Google Authenticator da siempre ajustes admitidos (`OtpMigrationTest.kt:104`, según la auditoría).
  - No promete nada sobre la fuerza de la maestra.
  - No cambia ni la descripción breve ni las reglas de nombre.
- ***Seguridad de los datos* en Play** («No recoge ni comparte datos»): la 1.3.1 no añade ningún tipo de dato, así que la respuesta y su motivo (`PUBLICAR.md:95`) siguen valiendo. Tampoco cambian el desarrollador «Egt17baa», «no comerciante» ni los países.
- **`docs/PUBLICAR.md`:** la 1.3.1 no lo cambia y sigue siendo verdad. En `:95` el User-Agent es genérico («Clavero/versión»). La línea de `:94`, «Al publicar la 1.3.0», es histórica: la regla general («Cuando la cambies, actualiza su fecha») cubre R-2.

### 3.6 Licencias

- **Sin dependencias nuevas.**
  - `git diff 9504ce3..18c25ce` no cambia nada en `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `config/`, `jvm-check/gradle/`, `NOTICE`, `LICENSE`, `app/src/main/assets` ni `AndroidManifest.xml`/`res`.
  - En `app/build.gradle.kts` solo cambian `versionName` (`:35`) y `checkAssets` (`:184-221`).
  - El R8 9.1.31, que traía en su POM licencias GPL con excepción de *classpath* y LGPL por el código que lleva dentro, se quitó (`94ddb7f`): la compilación es la de la 1.3.0.
- **BIP-39, inglés y español (MIT):**
  - la licencia permite usar el software «without restriction», con la única condición de conservar el aviso, y se conserva (`assets/licenses/bip39.txt`, `NOTICE`, `LicensesScreen.kt:36`);
  - usar la lista también para la regla de la maestra es un uso más, y queda cubierto;
  - los archivos no cambian, así que «sin cambios» sigue siendo verdad: la normalización de `masterCore` se hace en memoria y no se distribuye.
- **EFF (CC BY 3.0 US):**
  - la licencia permite cualquier uso (§3);
  - la atribución de §4(b) sigue en la app (`LicensesScreen.kt:37`), en `NOTICE`, en `assets/licenses/cc-by-3.0.txt` y en `README.md:157`, que dice también la adaptación (sin la columna de los dados);
  - el uso nuevo no crea otra obra derivada ni sugiere que la EFF respalde la app;
  - R-3 es solo por exactitud.
- **La lista común de «Salud»** (10 contraseñas, `health/Health.kt:18-20`) son datos sin originalidad. No es una obra protegida (art. 10.1 TRLPI) ni una base de datos con una inversión sustancial (art. 133 TRLPI).
- **La licencia de la app,** Apache-2.0, y la nota de marca (`README.md:9`) no cambian.

### 3.7 Ley aplicable

**a) Mapa normativo** (fecha de referencia 1-11-2026; las fuentes están al final):

| Norma | ¿Se aplica? | Motivo | ¿Cambia con la 1.3.1? |
|---|---|---|---|
| **RGPD** (Reglamento (UE) 2016/679) y **LOPDGDD** (LO 3/2018) | No, al desarrollador. | No recibe datos (`privacidad.html:29`). Lo que el usuario hace en su móvil es una actividad doméstica (art. 2.2.c RGPD). Es la conclusión de la 1.3.0, con un riesgo residual bajo por analogía con *Fashion ID*, C-40/17. | No: no hay ningún tratamiento nuevo. La política sigue dando la información de los arts. 13 y 14 por transparencia. |
| **LSSI** (Ley 34/2002) | No. | Anexo, letra a): un servicio no remunerado solo cuenta si es una actividad económica del prestador, y aquí no lo es. | No. La política da igualmente el seudónimo y el contacto. |
| **TRLGDCU** (RDL 1/2007) y **Directiva (UE) 2019/770** | No. | Ver b). | No. |
| **CRA** (Reglamento (UE) 2024/2847) | No, mientras no se monetice. | Art. 71.2: se aplica desde el 11-12-2027, con el art. 14 (notificación) desde el 11-9-2026 y el capítulo IV desde el 11-6-2026. Solo cubre lo que se comercializa en el curso de una actividad comercial. El considerando 18 dice que el software libre que su fabricante no monetiza no es actividad comercial, y la guía de la Comisión de julio de 2026 (no vinculante) añade que las donaciones voluntarias no lo hacen comercial y las obligatorias sí. «Nunca donaciones» (`CLAUDE.md:75`) deja fuera incluso esa duda. | No. |
| **Directiva (UE) 2024/2853** (productos defectuosos) | No. | Se traspone hasta el 9-12-2026. Su art. 2.2 deja fuera el software libre desarrollado o suministrado fuera de una actividad comercial (fuentes secundarias). **Pendientes de verificar a fecha de hoy:** el texto oficial y el estado de la trasposición española. | No. |
| **AI Act** (Reglamento (UE) 2024/1689) | No. | Art. 3.1 y considerando 12: no cubre los sistemas «based on the rules defined solely by natural persons». La regla de la maestra es una estimación determinista y una lista de palabras (`MasterPolicy.kt:150-156`, `Health.kt:93-122`). | No. |
| **DSA** (Reglamento (UE) 2022/2065) | No, como prestador. | No presta servicios intermediarios. La declaración «no comerciante» en Play (O-6 de la 1.3.0) sigue igual. | No. |
| **Accesibilidad** (Directiva (UE) 2019/882 y Ley 11/2023) | No. | No es ninguno de los servicios cubiertos. Se siguen las regiones vivas para TalkBack de forma voluntaria (`VaultScreen.kt:257`, `VaultBackup.kt:246`). | No. |
| **Control de exportaciones** (Reglamento (UE) 2021/821; EAR §734.3(b)(3)) | Sin cambios respecto a la 1.3.0. | La 1.3.1 no añade criptografía: quita compatibilidad. Los «pendiente de verificar» de la 1.3.0 siguen en `PUBLICAR.md` 3.7. | No. |
| **Francia** (Ley 2004-575, art. 30), **Rusia y Bielorrusia** | Fuera de la publicación. | Decisión del dueño. | No. |
| **Reino Unido** (Consumer Rights Act 2015, s. 33) | Probablemente no. | Cubre el contenido digital suministrado por un precio, o gratis junto con algo de pago. Si se quiere certeza, que lo confirme un abogado local. | No. |
| **«Digital Omnibus»** (propuesta de la Comisión, noviembre de 2025) | — | No he podido confirmar su adopción a fecha de hoy (**pendiente de verificar**). La 1.3.1 no depende de él. | — |

**b) Dejar de abrir los formatos de CriptoPanel**

1. **No hay contrato de consumo de contenidos digitales.**
   - La Directiva 2019/770 se aplica cuando el consumidor paga un precio (art. 3.1, párrafo primero) o facilita datos personales al empresario (art. 3.1, párrafo segundo). Aquí no hay ni lo uno ni lo otro.
   - Además, excluye el software con licencia libre por el que no se paga precio y cuyos datos solo se tratan para mejorar su seguridad, compatibilidad o interoperabilidad (art. 3.5.f).
   - En España, el art. 59.4 TRLGDCU recoge la misma regla.
   - El art. 4 TRLGDCU define al «empresario» como quien actúa «con un propósito relacionado con su actividad comercial, empresarial, oficio o profesión», y un particular que publica gratis y sin ánimo de lucro no lo es.
2. **Aunque se aplicara**, el art. 126 TRLGDCU (modificación de los contenidos o servicios digitales) solo rige en los contratos de suministro durante un periodo.
   - Pediría que el contrato lo permita con una razón válida, sin coste e informando de forma clara.
   - El art. 126 bis.1 daría derecho a resolver si el efecto negativo no es de menor importancia.
   - El art. 126 bis.4 excluye ese derecho si el consumidor puede mantener sin coste la versión sin modificar, y la 1.3.0 sigue gratis en *Releases*: R-1 solo lo dice.
   - Los dos artículos se aplican a contratos desde el 1-1-2022, según la nota del BOE.
3. **La frase «se leerán siempre» del README de la 1.3.0** es documentación de un proyecto Apache-2.0, no una garantía contractual.
   - La licencia excluye las garantías (§7) y limita la responsabilidad (§8), salvo lo que imponga la ley.
   - En derecho español, la responsabilidad por dolo no se puede excluir (art. 1102 CC), pero aquí no hay dolo ni daño previsible: el único usuario no tenía datos.
4. **Competencia desleal** (Ley 3/1991): su art. 2 exige actos en el mercado con fines concurrenciales, y aquí no los hay.
5. **Conclusión:** no hace falta nada más que la nota del README. Se recomiendan R-1 y que las notas de la versión de GitHub o de Play, si se escriben, digan lo mismo (§4).

## 4. Lo que no se ha podido valorar, y por qué

1. **EUR-Lex no respondió** (página vacía, como el 31-10-2026). Usé:
   - el BOE consolidado del TRLGDCU (fuente oficial, consultada directamente) para los arts. 4, 59.4, 126 y 126 bis;
   - copias del texto del DOUE: legislation.gov.uk para la Directiva 2019/770, springlex.eu para el art. 71 del CRA y artificialintelligenceact.eu para el considerando 12 del AI Act;
   - la página de la Comisión sobre el CRA y el software libre (actualizada el 15-10-2026).

   La guía de la Comisión sobre el CRA de julio de 2026 la he visto solo en una noticia (opensourceforu.com, 29-7-2026): **referencia exacta por confirmar**.

   **Siguen pendientes de verificar:**
   - el art. 2.2 de la Directiva 2024/2853 en su texto oficial y el estado de su trasposición española;
   - el estado del «Digital Omnibus»;
   - los puntos de la 1.3.0 de `PUBLICAR.md` 3.7.

   Del CRA, el calendario (art. 71.2) queda confirmado con una copia del texto del DOUE; el art. 69.3 no lo he mirado.
2. **Las notas de la versión** (la *release* `v1.3.1` de GitHub y las «notas de la versión» de Play, `PUBLICAR.md:88`) no están en el repositorio, así que no las he podido revisar. Si se escriben:
   - que digan que la 1.3.1 deja de abrir los formatos de CriptoPanel;
   - que no presenten la regla de la maestra como algo que hace fuerte la contraseña (por ejemplo, nunca «comprueba que la maestra sea fuerte»);
   - y que no usen ninguna palabra prohibida.
3. **Si hay otros usuarios de CriptoPanel:** me baso en lo que dice el dueño. No he comprobado si CriptoPanel se publicó alguna vez para terceros. Si lo hizo, R-1 gana peso, aunque seguiría sin ser obligatorio (§3.7.b).
4. **Que `Clavero-v1.3.0.apk` siga en *Releases*:** lo dice `CLAUDE.md:39`; no lo he comprobado en línea.
5. **Cómo leen los textos TalkBack y el móvil:** es de la prueba del dueño (README, `:308-312`).
6. **Observaciones fuera de la 1.3.1, para el equipo técnico** (no cambian el veredicto):
   - **a)** `ui/screens/VaultBackup.kt:174-180`.
     - Si `importReplace` falla después de guardar (`vault/VaultStore.kt:895-896`), la pantalla dice «No se pudo importar la copia» aunque la copia ya está importada, y no desactiva la huella (que después dará `FINGERPRINT_STALE`).
     - Viene de la 1.3.0 (`9504ce3`, mismas líneas en `:168-174`).
     - Es un texto que puede ser falso en un caso raro; lo valora el equipo técnico («Detalles menores: se arreglan todos»).
   - **b)** `docs-report.md` y la premisa del encargo dicen que todo camino desactiva la huella «antes». No es exacto para importar ni para cambiar la maestra (R-4). Es un documento interno, ignorado por git.

---

## Revisión experto-seguridad-cripto

- **O-1** solo cambia el texto de una constante.
  - El único camino que desactiva la huella sigue siendo `StaleBiometricKey` (`vault/VaultStore.kt:110-117`), y el orden de las comprobaciones no cambia (`:469-496`).
  - El texto no da a un atacante ninguna información que no tenga: no distingue las causas, igual que AES-GCM (`Vault.kt:313-321`).
  - No invita a ninguna acción peligrosa: pide la maestra y volver a activar la huella. Con datos dañados, «He olvidado la contraseña maestra» sigue avisando de lo que borra (`ToolTexts.kt:560-563`).
- **R-1** no debilita nada: la 1.3.0 es la versión publicada y auditada (`docs/auditorias/2026-10-31-auditoria-1.3.0.md`), y el texto avisa de que importar reemplaza la bóveda.
- **R-2** dice lo que el código envía (`net/HttpClient.kt:28`) y no cambia la red. La única conexión sigue siendo `api.pwnedpasswords.com` (`net/PwnedPasswords.kt:24`).
- **R-3** no cambia ningún comportamiento y describe la regla con su límite real: solo al crear o cambiar la maestra (`vault/MasterPolicy.kt:127-130`).
- **R-4** corrige la descripción de un riesgo residual sin rebajarlo: la huella con clave antigua solo queda tras una muerte del proceso o un error justo después de guardar (`VaultBackup.kt:128-131,174-175`).
- **Ningún cambio toca un formato, una clave ni un parámetro criptográfico.**

## Revisión Ingeniero Móvil Senior

- **O-1:** la constante se enseña en `UnlockVault`, por `onError` → `error` → `Text(it, color = NegativeColor)` (`ui/screens/VaultScreen.kt:321-324,364`).
  - Es unos 30 caracteres más larga y se parte en líneas sin problema en un móvil.
  - No cambia el ciclo de vida: el límite BOV-3 de un giro mientras llega la respuesta sigue documentado (`vault/VaultStore.kt:95-96`).
- **R-3:** la nota se pinta en `bodySmall` y se parte en líneas (`LicensesScreen.kt:59`). La línea de `Component` sigue siendo una sola, como pide `PublishedTextsTest.kt:101-107`.
- **R-1:** lo que dice de la instalación lo respalda `docs/PUBLICAR.md:80` (firmas distintas entre Play y *Releases*) y `:90` (Android rechaza un `versionCode` menor). El `versionCode` lo pone la CI con el número de ejecución (`app/build.gradle.kts:33-34`).
- **Sin cambios de permisos, componentes ni hilos:** ningún cambio propuesto toca `AndroidManifest.xml`, `res/` ni el hilo principal.

## Fuentes consultadas (1-11-2026)

- BOE, Real Decreto Legislativo 1/2007 (TRLGDCU), texto consolidado: arts. 4, 59.4, 126 y 126 bis — https://www.boe.es/buscar/act.php?id=BOE-A-2007-20555
- Directiva (UE) 2019/770, art. 3 (copia del texto adoptado) — https://www.legislation.gov.uk/eudr/2019/770/article/3/adopted
- Reglamento (UE) 2024/2847 (CRA), art. 71 (copia del DOUE) — https://www.springlex.eu/en/packages/cra/cra-regulation/article-71/
- Comisión Europea, «CRA and open source» (actualizada el 15-10-2026) — https://digital-strategy.ec.europa.eu/en/policies/cra-open-source
- Noticia sobre la guía de la Comisión del CRA (29-7-2026; fuente secundaria) — https://www.opensourceforu.com/2026/07/eu-clarifies-cra-rules-for-non-commercial-open-source/
- Reglamento (UE) 2024/1689 (AI Act), considerando 12 (copia del DOUE) — https://artificialintelligenceact.eu/recital/12/
- «Digital Omnibus», estado de la propuesta (fuente secundaria) — https://cm.twobirds.com/en/insights/2025/digital-omnibus-package-single-eu-harmonised-incident-reporting-regime-across-cyber-and-data-protect
- Directiva (UE) 2024/2853, art. 2.2 y plazo de trasposición (fuentes secundarias; pendiente de verificar en EUR-Lex) — https://www.wikiwand.com/en/articles/Directive_(EU)_2024/2853
- EUR-Lex (CELEX 32019L0770): sin respuesta.

*Borrador profesional para revisión del dueño. No sustituye el asesoramiento de un abogado colegiado en cada jurisdicción de publicación.*
