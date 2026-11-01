# Informe del paso de documentos de la 1.3.1

- **Rama:** `ccr-a1a6eabb-765f9g`, sobre `95ddc44` (el código no cambia en este paso).
- **Skills leídas:** `experto-seguridad-cripto` (`SKILL.md`, su formato de informe de auditoría y la tabla «Trabajo conjunto con superpowers»). Las fuentes: `auditoria/04-cripto.md` (y las partes A y B en lo que tocaba CRY-A02), el diseño, el plan, `progress.md`, `final-review.md`, `final-audit.md`, `final-fixes.md` y `final-fixes-report.md`; como modelos, la auditoría de la 1.3.0, el traspaso de la 1.3.0 y la lista de pruebas del README.
- **Sin subagentes.** No se ha tocado código, tests, `docs/privacidad.html`, `docs/ficha-google-play.md` ni `docs/superpowers/handoff/sdd-1.3.1/progress.md`.
- **Commit:** al final de este informe (sección «Commit»).

## 1. Entregables

### 1.1 `docs/auditorias/2026-11-01-auditoria-1.3.1.md` (nuevo, 465 líneas)

Con la estructura de la auditoría de la 1.3.0, adaptada:

| Líneas | Sección |
|---|---|
| 1-13 | Cabecera: alcance `9504ce3..95ddc44`, origen, commits, pendientes |
| 15-74 | §1 Alcance, modelo de amenaza, método (tabla de las tareas 1 a 7 con sus revisiones, revisión de la rama, auditoría del cambio, `code-review`, `security-review`, ronda F1-F9) y límites |
| 76-83 | §2 Valoración general |
| 85-171 | §3.1-3.2 Los cinco hallazgos: severidad original, qué cambia (archivo:línea y commit), veredicto y tests |
| 173-199 | §3.3 Párrafo de riesgo residual de CRY-A03, literal de `final-audit.md:499-521` (comprobado con `diff`: idéntico), y una nota sobre A-1 |
| 201-229 | §4 Fuera CriptoPanel: decisión, qué se quitó, qué recibe cada dato, formatos propios intactos (vectores de la 1.3.0 y de Python) |
| 231-301 | §5 Revisiones finales: N-01..N-08 y A-1..A-3, I1 y M1-M7, los 7 aplazados, los 9 de `code-review`, `security-review` |
| 303-317 | §6 Límites aceptados |
| 319-324 | §7 Para la 1.4.0 |
| 326-355 | §8 MASVS v2 y hermeticidad |
| 357-368 | §9 Evidencia de verificación |
| 370-388 | §10 Por validar en el móvil (README 1.3.1 y lo pendiente de la 1.3.0) |
| 390-421 | §11 Textos para el Abogado Digital |
| 423-437 | §12 Siguientes pasos |
| 441-465 | «Revisión experto-seguridad-cripto» y «Revisión Ingeniero Móvil Senior» |

**Riesgo residual de CRY-A03:** literal. La ronda de arreglos no cambió nada de lo que dice (`final-fixes-report.md`: F5 toca `base32Decode`, `totpKey` y el editor; `hotp` sigue usando `SecretKeySpec`, `crypto/Totp.kt:123`). Añadí debajo una línea aparte (`:199`) que dice que F5 quitó otra copia, sin tocar el párrafo.

### 1.2 README.md

- `:53`: errata «Una JSON» → «Un JSON».
- `:308-312`: bloque 1.3.1 detrás del último de la 1.3.0 (`:307`), con el mismo formato («- [ ] 1.3.1, qué (origen): pasos: resultado»):
  - `:308` «¿Filtrada?» y «Comprobar filtraciones» con Wi-Fi y con datos móviles, y repetir en cada subida de `targetSdk`;
  - `:309` cambiar la maestra (y crear la bóveda si se prueba sin bóveda) con «contraseña1» y «contraseña123» (rechazadas), «contraseña» y «corta», y una frase de 5 palabras del Generador (aceptada); TalkBack lee el aviso;
  - `:310` importar una copia de la 1.3.0 con la maestra «contraseña1» (se importa), con el aviso de exportar antes la propia y de volver a activar la huella;
  - `:311` `otpauth://totp/Prueba:ana?secret=JBSWY3DPEHPK3PXP&digits=10` en el editor («Pegar enlace») y como QR en el Inspector (nota, sin «Añadir a la bóveda»), y lo mismo con `period=300`;
  - `:312` los códigos 2FA que ya había, en «Bóveda» y en «Códigos».
- **La huella con la clave de otra bóveda (`FINGERPRINT_STALE`) no está en la lista:** no hay forma de provocarla desde la interfaz (ver §2, «CRY-A02»). Lo explica la auditoría (§3.2, CRY-A02, «Sin prueba en el móvil», `:144`, y §10, `:384-386`): lo cubren los tests.
- **«2FA no admitido» en la lista:** tampoco se puede ver desde la app (ver §2, «TOTP»). La línea `:311` lo dice y remite a `VaultTotpTest`; el brief pedía la línea, pero escribirla como prueba sería falso.
- **«Formatos»:** la línea de la 1.3.1 (`:48`) sigue. Ninguna línea del README promete ya leer CriptoPanel: `grep -n -i "criptopanel\|CP1\|cpenc" README.md` solo da `:48`.

### 1.3 CLAUDE.md

- `:29`: «Estado actual: Clavero 1.3.1, terminada en la rama y pendiente del PR (1-11-2026)».
- `:31-36`: «Nombre», sin cambios (sigue vigente).
- `:38-42`: «Fase»: la 1.3.0 publicada en corto (`v1.3.0` en `b6ea21b`, su registro y su auditoría), la 1.3.1 (rama, base `9504ce3`, código en `95ddc44`), dónde están el registro (`docs/superpowers/handoff/sdd-1.3.1/progress.md`), la auditoría y el traspaso, y `main` protegida.
- `:44-53`: tabla corta de la 1.3.1: tareas 1-5, 6, 7, revisión final, ronda de arreglos («falta su repaso acotado»), documentos, Abogado Digital «Pendiente», PR/APK/prueba/etiqueta «Pendiente».
- `:55-78`: decisiones de la 1.3.0, sin cambios salvo `:76`: «La etiqueta de la versión (`v1.3.0`, `v1.3.1`)…».
- `:84`: los avisos de R8 pasan de «la próxima versión» a la 1.4.0, con la decisión del dueño (`94ddb7f`).
- `:86-98`: «Decisiones de la 1.3.1»: CriptoPanel, maestra nueva (10 caracteres de las dos maneras, ≥ 50 bits con la menor, no una sola palabra de «Salud», EFF, BIP-39 EN/ES; solo crear y cambiar), huella (solo `StaleBiometricKey`; con versión declarada, 7 días y reloj primero), archivo que no se abre (`UNSUPPORTED`, «No es una bóveda compatible»), TOTP (rechazar, no cambiar), `BiometricVault.disable` → 1.4.0, `targetSdk` → «¿Filtrada?» y «Comprobar filtraciones» con red.
- `:100-108`: «Pendientes, del dueño»: arriba, los tres de la 1.3.1 (Build APK desde `main` y pruebas del README; etiqueta `v1.3.1` desde su terminal; retirar CriptoPanel es decisión suya), y después los cinco que ya había (renumerados 4-8).
- `:112`: la regla de los formatos sin CriptoPanel, con la decisión y la fecha.
- «Skills: siempre juntas» y «Compilar y probar»: sin tocar (`git diff` no tiene ningún trozo antes de `:29` ni después de `:112`).

### 1.4 `.github/pull_request_template.md:9`

Solo los formatos de Clavero (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, `SGK1:`, `SGM1:`, `SGS1:`…) y la frase «Los de CriptoPanel (`CP1:`, `.cpenc`, `criptopanel-vault`) se rechazan con el error genérico desde la 1.3.1.». El resto de la plantilla, igual (`git diff`: 1 línea).

### 1.5 Traspaso

- `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md` (nuevo, 59 líneas): §1 estado (`:5-12`), §2 dónde está todo (`:14-22`), §3 lo que queda, para Claude y para el dueño (`:24-37`), §4 las decisiones en una lista (`:39-53`), §5 cómo arrancar (`:55-59`).
- `docs/superpowers/handoff/2026-10-28-seguridad-1.3.0-traspaso.md:83`: añadido a la línea de formatos que desde la 1.3.1 los de CriptoPanel ya no se leen, con el enlace al traspaso nuevo, y una nota «Corrección» de que la `seguridad-vault` v1 no existió nunca (la misma frase falsa de M7 estaba también aquí).

### 1.6 Diseño y plan (M7)

- Diseño `:20-21`: «`seguridad-vault` v1 a v3» → «v2, v3 y sin versión», con la nota «Corrección (1-11-2026, revisión final, M7)».
- Diseño `:73-74`: la frase de `checkAssets` se deja (desde `68c38ed` es verdad) y se añade la nota «Corrección (1-11-2026, revisión final, M2 y M7)»: cuando se escribió no lo comprobaba.
- Plan `:19-20`: «v1 a v3» → «v2, v3 y sin versión», con su nota.

## 2. Afirmaciones comprobadas contra el código (`95ddc44`)

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`; tests, a `app/src/test/java/app/seguridad/`.

**Alcance, hermeticidad y proceso**
- `9504ce3` solo añade documentos sobre `b6ea21b` → `git diff --stat b6ea21b 9504ce3 -- app jvm-check` vacío.
- 56 archivos, 49 de `app/` y `jvm-check/` → `git diff --shortstat 9504ce3..95ddc44` y `-- app jvm-check`.
- 42 commits, todos `Claude <noreply@anthropic.com>`, 0 `Claude-Session`/claude.ai → `git log 9504ce3..HEAD`.
- Manifiesto, `res/`, `libs.versions.toml`, los dos `verification-metadata.xml`, `build.gradle.kts`, `settings.gradle.kts`, `.github/workflows`, `test/resources/seguridad` y `vectors` sin cambios → `git diff --quiet` en cada uno.
- 0 líneas añadidas en `app/src/main` con `Log.`/`println(`/`printStackTrace`, 0 URL, y el único `rememberSaveable` añadido es un comentario → `ui/screens/MasterWords.kt:16`.
- `app/build.gradle.kts` solo cambia `versionName` (`:35`) y `checkAssets` (`:196,204-212`, mensaje `:208-210`).
- APK de publicación en `95ddc44` (ejecutado en este paso): `check-manifest: OK`, `versionName='1.3.1'`, `application-label:'Clavero'`, los 6 permisos y 53 avisos de metadatos de R8 → `scratchpad/docs-step/{check-manifest.log,permissions.txt,badging.txt,release.log}`.
- Tarea 7: badging idéntico y `classes.dex` igual byte a byte tras quitar el R8 → `task-7-report.md:67,124,126`.
- Tests en la base: 1207 y 1202 → plan, «Restricciones globales». En `95ddc44`: 1293 y 1288 → XML de resultados (§3 abajo).
- La ronda de arreglos no tiene aún su repaso → no hay ningún informe de repaso en `.superpowers/sdd/2026-11-01-clavero-1.3.1/`, y el registro dice «then one scoped re-review over both ranges».

**CRY-B01**
- Rechazo antes y después del sistema → `net/DistrustedCertificates.kt:109-113`; tres sobrecargas `:115-127`; nombre del apretón `:120,125`; `getAcceptedIssuers` filtrado `:137`; `@Suppress` `:96`.
- `X509TrustManagerExtensions`, falla cerrado sin nombre o con constructor que lanza → `android/TrustedChain.kt:26-35` (`:27-31`, `:33`); KDoc de N-04 `:21-24`.
- Inyección → `AppContainer.kt:87,120`, `SeguridadApp.kt:30`.
- Tests → `net/DistrustedCertificatesTest.kt:164,172,187,200`, `net/HttpClientTest.kt:33,45`, `PlatformSourcesTest.kt:296`.

**CRY-A01**
- Orden longitud (dos medidas), estimación, palabra → `vault/MasterPolicy.kt:150-156`; `strengthForm` `health/Health.kt:105-110`; `estimateBits = minOf(...)` `:93-99`; `WEAK_BITS = 50.0` `:14`; `masterCore` `MasterPolicy.kt:47-57`; listas `:30-34`; `WordLists` `:90-113`; `loadMasterWords` vacío ante fallo `:77-81`.
- Textos → `MASTER_TOO_SHORT` `:24`, `MASTER_TOO_WEAK` `:27-28`.
- Solo crear y cambiar → `ui/screens/VaultScreen.kt:245,256-257,273`; `ui/screens/VaultBackup.kt:118,233-235`; importar `a.length >= MIN_MASTER` `:235`; región viva `VaultScreen.kt:257`, `VaultBackup.kt:246`; maestra en `remember` `VaultBackup.kt:227-228`.
- «contraseña1» y «contraseña123» rechazadas: el núcleo «contrasena» está en las palabras porque «contraseña» está en la lista común (`health/Health.kt:18-24`, `MasterPolicy.kt:64-69`); «contraseña» (10) da 0 bits por la lista común (`Health.kt:113-114`); «corta» < 10 → `MASTER_TOO_SHORT`.
- Rutas del README → «Cambiar contraseña maestra» en Ajustes (`ui/screens/SettingsScreen.kt:466`) y en ⋮ (`VaultScreen.kt:191`); Generador → `ui/screens/ToolsScreen.kt:42`, «Frase» y «Palabras» `ui/screens/SecurityToolsScreen.kt:959,980`; «Importar copia» ⋮ `VaultScreen.kt:187`.
- Tasas del Generador (3 900 / 61 000 / 1,1 millones) → KDoc `MasterPolicy.kt:143-148`.
- `checkAssets` con las tres listas → `app/build.gradle.kts:196,204-212`; test `vault/MasterPolicyTest.kt:184`.
- Tests citados → `MasterPolicyTest.kt:50,58,82,94,192,233,291,309,343,393,434,530`; `health/HealthTest.kt:219,237`; `AppContainerTest.kt:52`.

**CRY-A02 (y por qué no hay prueba en el móvil de `FINGERPRINT_STALE`)**
- Orden de `unlockWithKeyLocked` → `vault/VaultStore.kt:469-496` (`:472-475`, `:485-486`, `:488-491`, `:492`); `unlockWithKey` borra la clave `:459-467`.
- Solo `StaleBiometricKey` llama a `onWrongKey` → `VaultStore.kt:110-117`; clase `:126`; `MasterRequired(UNSUPPORTED)` lleva `NOT_A_VAULT` → `:137`.
- `authenticated` sin decodificar y borrando → `vault/Vault.kt:313-321` (`:319`); `opens` `:305`; `supported` (v2, v3, sin versión) `:166-167`; `biometricRefusal`/`UNSUPPORTED` `:422-434`.
- Pantalla → `ui/screens/VaultScreen.kt:311-324` (botón oculto), `:342` (`NOT_A_VAULT`), `:355-363` (sin aviso con `UNSUPPORTED`).
- **No reproducible desde la interfaz:** todos los caminos que cambian la clave de datos llaman a `BiometricVault.disable`: crear (`VaultScreen.kt:281`, antes de `store.create`), borrar (`:378`), cambiar la maestra (`VaultBackup.kt:131`) e importar (`:175`). `disable` no lanza en la práctica (`android/BiometricVault.kt:151-158`: `edit {}` asíncrono y `deleteKey` con `runCatching`, `:102-104`). Los tres casos de la parte A (`auditoria/04-cripto-parteA.md`, CRY-A02, «Hay tres casos») son una muerte del proceso entre el guardado y `disable`, un `disable` que falla y una excepción de `importReplace` después de guardar: ninguno se puede provocar a propósito desde la app. Lo cubren `VaultStoreTest.kt:975-1254` y `VaultV3Test.kt:522,542,670,685`.

**CRY-A03**
- `blockGcm` `crypto/Crypto.kt:289-306` (`kp.key.fill(0)` `:304`); `chaChaPoly` `:370-390` (`:383`, `init` cero `:385`); `runAead` `:264-278` (`:276`).
- Tests → `crypto/CryptoTest.kt:479,491,504,513,538,562,585,614,644,658`; vector publicado de Twofish `crypto/SeedCryptoTest.kt:40`; KDoc de `Kat` `CryptoTest.kt:41-53`.

**CRY-B02 (y por qué «2FA no admitido» no se ve desde la app)**
- `hmacName` `crypto/Totp.kt:97-102`; rangos `:87-91`; `isSupported` `:105`; `hotp` `:121-122`; `totpCode` `:138`; `readOtpAuth` `:190-222`; RFC 4226 `:64-68`; textos `:108,111,114`; `base32Decode` exacto `:28-44`; `totpKey`/`longEnoughOrWiped` `:71-78`.
- Editor `ui/screens/VaultEntryScreen.kt:257-266`; ficha `:150-154`; `isUsableTotpKey` `:496`; Inspector `tools/qr/QrView.kt:184-198`; lista `ui/screens/VaultScreen.kt:460-470`; etiqueta del botón `QR_ADD_TOTP` = «Añadir a la bóveda» `tools/ToolTexts.kt:361`; «Código 2FA», «Servicio», «Cuenta» `:387,419,420`; tipo de entrada «Código 2FA» `vault/Vault.kt:27`; pestañas «Bóveda» y «Códigos» `ui/AppNavHost.kt:81-82`.
- El enlace de prueba: `JBSWY3DPEHPK3PXP` son 16 caracteres = 10 bytes = `TOTP_MIN_KEY_BYTES` (`Totp.kt:68`); `digits=10` y `period=300` dan `UnsupportedSettings` (`:214-218`); la etiqueta `Prueba:ana` da servicio «Prueba» y cuenta «ana» (`:207-210`).
- **No reproducible desde la interfaz:** el editor solo ofrece 6/8 dígitos y SHA1/256/512 y no deja elegir el periodo (`VaultEntryScreen.kt:456-457`); un enlace no admitido se rechaza (`:265`); Google Authenticator salta MD5 y solo da 6 u 8 dígitos y 30 s (`crypto/OtpMigration.kt:141-159`); y la 1.3.0 rechazaba un algoritmo desconocido y cambiaba los dígitos y el periodo fuera de rango antes de guardar (`git show 9504ce3:…/crypto/Totp.kt`, `parseOtpAuth`, líneas 128-132). Lo cubre `vault/VaultTotpTest.kt:345,357`.

**Fuera CriptoPanel**
- `Dialect` de un valor `crypto/Crypto.kt:32`; `TEXT_PREFIX` `:509`; KDF 2 retirado `:476-480`; cabecera ajena → `NOT_COMPATIBLE` `:489-491`; `format` sin valor por defecto `vault/Vault.kt:115-118`; `decodeFile` `:403-407`; `PkActions.kt:481`.
- Tests → `crypto/DialectTest.kt:77,128,150,189`, `vault/LegacyVaultTest.kt:46`, `vault/PkActionsTest.kt:1026`, `tools/AppNameTest.kt:89,107`, `vault/VaultTamperTest.kt:66`, `vault/BackupTest.kt:200`; datos que quedan en `app/src/test/resources/criptopanel/` (`ls`: `ref.cpenc`, `texts.json`, `vault-noversion.json`, `vault-v2.json`).
- La v1 de `seguridad-vault` no existió → `git show 07f220e:…/vault/Vault.kt:94-96` (`FORMAT` solo con `VERSION_UNKNOWN` o `VERSION` = 2), y `07f220e` es el commit que crea `Vault.kt` (`git log --diff-filter=A`).

**Para la 1.4.0 y límites**
- Seis llamadas a `BiometricVault.disable` en el hilo principal → `ui/screens/VaultBackup.kt:131,175`, `ui/screens/VaultScreen.kt:170,281,323,378`.
- 1 356 líneas de más de 120 caracteres en `app/src/main/java` (`find … | awk 'length>120' | wc -l`).
- Límites del registro → `progress.md` (tarea 3 M5/M6, tarea 2 «1 in 61,000», tarea 5 M2, tarea 4 Twofish/Serpent, N-04 ruling).

## 3. Tests

Con `export ANDROID_HOME=/opt/android-sdk`, desde la raíz, después de los cambios de este paso:

| Comando | Resultado |
|---|---|
| `gradle -p jvm-check cleanTest test --no-daemon --max-workers=1 --offline` | BUILD SUCCESSFUL, **1288 tests**, 0 fallos, 0 errores, 0 omitidos |
| `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --no-daemon --max-workers=1 --offline` | BUILD SUCCESSFUL, **1293 tests**, 0 fallos, 0 errores, 0 omitidos |
| `./gradlew :app:assembleRelease -PfirmaDePrueba=true --no-daemon --max-workers=1 --offline` | BUILD SUCCESSFUL (53 avisos de metadatos de Kotlin, los de la 1.3.0) |
| `bash scripts/check-manifest.sh --release app/build/outputs/apk/release/app-release.apk` | `check-manifest: OK` |
| `aapt2 dump permissions` / `dump badging` | Los 6 permisos; «Clavero», `io.github.egt17baa.seguridad`, 1.3.1 |

`cleanTest` hace falta: sin él, las dos tareas de test salen `UP-TO-DATE` (el README no es una entrada suya) y no se vuelven a ejecutar. La primera pasada salió así; la segunda y la tercera, con `cleanTest`, sí se ejecutaron (`2 executed` en cada una, y los XML de resultados con la hora de la pasada). La tercera es la del README final.

Los tests que leen documentos: `tools/AppNameTest.kt`, `tools/PublishedTextsTest.kt` y `tools/FinalLegalReviewTest.kt` leen `README.md` (y la política, la ficha y `PUBLICAR.md`, que no cambian); ninguno lee `CLAUDE.md`, la plantilla del PR, el diseño, el plan ni los traspasos.

## 4. Palabras prohibidas

`git diff -U0` de los archivos cambiados (más los dos nuevos con `git add -N`), solo las líneas añadidas, con:

```
grep -n -i -E "imposible|100 ?%|a prueba de cu[aá]nticos|como signal|validez legal|anónim|anonim|garantiz|de verdad|limpi[oa]s?\b|sospech|(^|[^[:alpha:]])segur[oa]s?([^[:alpha:]]|$)"
```

Salida (577 líneas añadidas, una coincidencia):

```
358:+| «Barcelona1», dos palabras juntas («passwordpassword») y una palabra que no está en las listas pasan como maestra | Es una estimación y una lista de palabras, no un diccionario (decisión del dueño). La regla no garantiza que una maestra sea fuerte | …
```

Es «no garantiza», que está permitido. Sin correos personales: las únicas direcciones son `noreply@anthropic.com` (autoría de los commits, como en el traspaso de la 1.3.0).

## 5. Lo que encontré fuera de mi alcance (para el controlador y el Abogado Digital)

1. **«Comprobar contraseña» no existe en la interfaz.** La herramienta se llama «¿Filtrada?» (`ui/screens/ToolsScreen.kt:43`), su pantalla «¿Contraseña filtrada?» y su botón «Comprobar» (`ui/screens/SecurityToolsScreen.kt:1058,1069`). En el README y en `CLAUDE.md` uso «¿Filtrada?». El KDoc de `android/TrustedChain.kt:24` dice «Comprobar contraseña»: es código, no lo he tocado.
2. **`docs/privacidad.html:46`** pone como ejemplo «Clavero/1.3.0»; la 1.3.1 envía «Clavero/1.3.1» (`net/HttpClient.kt:28`). Es «por ejemplo», así que no es falso, pero el Abogado puede querer cambiarlo. La política y la ficha no nombran CriptoPanel.
3. **`docs/superpowers/handoff/sdd-1.3.1/progress.md:3`** sigue diciendo «keep reading CriptoPanel formats, remove only the name» (I1 de la revisión final); es la copia del controlador y no la he tocado.
4. **`.claude/skills/experto-seguridad-cripto/SKILL.md:107`** dice «como en CriptoPanel» en la fila de `finishing-a-development-branch`: es un ejemplo de proceso, no pide leer sus formatos. No lo he tocado.
5. **Documentos históricos con la frase de M7:** `docs/auditorias/2026-10-31-auditoria-1.3.0.md:645` («`seguridad-vault` v1 a v3» y «`CP1:`… se leen para siempre») y `.superpowers/sdd/2026-11-01-clavero-1.3.1/context.md:7`. El diseño de la 1.3.1 dice que los documentos históricos no cambian; en el traspaso de la 1.3.0 sí puse la nota, porque la línea `:83` había que tocarla igualmente.
6. **El plan, `:160`,** dice que las listas se leen «una vez por pantalla abierta»; desde F6 es una vez por proceso. Es una frase del plan que M7 no señala; no la he cambiado.
7. **El repaso acotado de la ronda F1-F9 sigue pendiente;** lo he puesto como «[PENDIENTE]» en la auditoría, en `CLAUDE.md` y en el traspaso.

## Revisión experto-seguridad-cripto

- Cada afirmación de seguridad de los documentos se comprobó contra `95ddc44` (§2). Las que dependen de Conscrypt o de BoringSSL se marcan como leídas en su código, y la de las SCT del certificado de HIBP como «según la auditoría del cambio» (`docs/auditorias/2026-11-01-auditoria-1.3.1.md:242`).
- El riesgo residual de CRY-A03 es literal (`:175-197`), y no se rebaja ninguna severidad: CRY-A03 queda «cerrado en parte» con Baja 1.9 (`:96`).
- Las pruebas del móvil no prometen lo que la app no hace: no hay línea para `FINGERPRINT_STALE` ni para «2FA no admitido», porque la interfaz no las alcanza (§2), y los tests que las cubren están nombrados (`docs/auditorias/2026-11-01-auditoria-1.3.1.md:384-386`, `README.md:311`).
- La prueba de importar avisa de que importar reemplaza la bóveda y desactiva la huella (`README.md:310`), para que la prueba no le cueste datos al dueño.

## Revisión Ingeniero Móvil Senior

- Las rutas de las pruebas son las de la interfaz: Ajustes y ⋮ → «Cambiar contraseña maestra» (`SettingsScreen.kt:466`, `VaultScreen.kt:191`), ⋮ → «🩺 Salud de la bóveda» (`VaultScreen.kt:179`) y «Comprobar filtraciones» (`HealthScreen.kt:180`), Herramientas → «¿Filtrada?» y Generador (`ToolsScreen.kt:42-43`), las pestañas «Bóveda» y «Códigos» (`AppNavHost.kt:81-82`).
- El APK de publicación se volvió a compilar en este paso con la clave de prueba: mismo manifiesto, mismos permisos y la etiqueta y versión correctas (§3).
- Ningún cambio de código: el paso es solo de documentos, y las verificaciones siguen en verde con los números de `95ddc44`.

## Textos para el Abogado Digital (revisión final)

Todo lo que este paso escribe o cambia:

1. `docs/auditorias/2026-11-01-auditoria-1.3.1.md:1-465` (nuevo; en especial §3.3 `:173-199`, §6 `:303-317`, §10 `:370-388` y la lista de textos de la app de §11 `:390-421`).
2. `README.md:53` (errata) y `README.md:308-312` (pruebas de la 1.3.1).
3. `CLAUDE.md:29-53` («Estado actual», «Fase» y tabla), `:76`, `:84`, `:86-98` («Decisiones de la 1.3.1»), `:100-108` («Pendientes, del dueño») y `:112` (regla de los formatos).
4. `.github/pull_request_template.md:9`.
5. `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md:1-59` (nuevo).
6. `docs/superpowers/handoff/2026-10-28-seguridad-1.3.0-traspaso.md:83`.
7. `docs/superpowers/specs/2026-11-01-clavero-1.3.1-design.md:20-21` y `:73-74`.
8. `docs/superpowers/plans/2026-11-01-clavero-1.3.1.md:19-20`.

Y, sin tocar, para su revisión: `docs/privacidad.html:46` («Clavero/1.3.0», §5.2 arriba).

## Commit

- `adbba00` «docs: auditoría, pruebas en el móvil, estado y traspaso de la 1.3.1»: 8 archivos (+577 −25), solo los de este paso, con pathspecs y sin nada de `.superpowers/`.
- Autor `Claude <noreply@anthropic.com>`; el mensaje acaba solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; `git log -1 --format=%B | grep -cE "Claude-Session|claude\.ai"` → `0`.
- Push: `95ddc44..adbba00  ccr-a1a6eabb-765f9g -> ccr-a1a6eabb-765f9g`. Sin `amend`, `rebase` ni `force`.
