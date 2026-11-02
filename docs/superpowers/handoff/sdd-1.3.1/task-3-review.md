# Revisión de la tarea 3 (CRY-A02), `1430a1d..e435a96`

Alcance: solo el diff (3 archivos) más comprobaciones puntuales fuera de él, con los riesgos con nombre. No se ha vuelto a ejecutar la suite; las cifras de tests y el commit/push son afirmaciones del informe sin verificar aquí.

### Spec Compliance

**Veredicto: cumple.** No falta nada, no hay extras relevantes, y las dos desviaciones declaradas son aceptables (la primera es mejor que lo pedido).

- Constante `FINGERPRINT_STALE` en `vault/VaultStore.kt:65-67`, junto a `BIOMETRIC_UNLOCK_FAILED` (`:58`). El texto es idéntico, palabra por palabra, al de `auditoria/04-cripto-parteA.md:413`. Sin palabras prohibidas (el test del texto usa `ForbiddenWords.problems`).
- Firma `fun CoroutineScope.launchUnlockWithKey(store, key, onWrongKey: () -> Unit = {}, onError: (String) -> Unit): Job` (`VaultStore.kt:90-95`), tal como el encargo.
- Rama `catch (e: CryptoFailure)` entre la de cancelación y la genérica (`:99-110`); llama a `onWrongKey()` y después a `onError(FINGERPRINT_STALE)`.
- Los tres tests del paso 1 existen:
  - clave ajena: `VaultStoreTest.kt` «another vault's key turns the fingerprint off once…» (lista `["off", "error: …"]`, bóveda `Locked`, `dataKeyCopy()` `null`, clave a cero) y la variante `VERSION_UNKNOWN`;
  - 7 días y reloj no llaman a `onWrongKey` y dan el texto de hoy;
  - clave buena abre la bóveda y no llama a `onWrongKey`.
- La pantalla deja de ofrecer el botón: `VaultScreen.kt:310` (`bio`) y `:350` (`if (bio) OutlinedButton`).
- Sin cambio de formato, sin tocar `BiometricVault`, sin textos fuera de lo pedido. El README se queda para la tarea de documentación (el plan lo cubre en las líneas 271 y 316).
- ⚠️ No verificable con el diff: commit, push, autor y trailer del mensaje, y las dos verificaciones globales (1255 en app, 1250 en jvm-check).

**Desviación 1: el `try` de `onWrongKey` dentro de `launchUnlockWithKey` (`VaultStore.kt:102-106`) y no en la lambda de `UnlockVault`.** Aceptada, y mejor que lo pedido. El efecto observable es el pedido («si `disable` falla, se enseña el mismo `FINGERPRINT_STALE`»), queda en un solo sitio y se prueba en JVM (`ui/` no se compila allí). Sin él, una excepción de `onWrongKey` saldría del `catch` al scope de la pantalla y rompería la app. `UnlockVault` queda con una lambda simple.

**Desviación 2: la marca `fingerprintStale` en la interfaz (`VaultScreen.kt:309-310, 319`).** Aceptada. `bio` no es estado observable (`BiometricVault.enabled` lee preferencias), así que sin la marca solo se recompondría por el `error = it` que llega después. La marca se escribe antes de `disable` (`:319` antes de `:320`), así que sobrevive a un `disable` que lance. Es redundante en el caso normal, pero barata y fijada por un test.

### Strengths

- La rama `CryptoFailure` es la única que llama a `onWrongKey` (`VaultStore.kt:101-103`). `MasterRequired` (`VaultStore.kt:117`, extiende `Exception`) y `CryptoFailure` (`crypto/Crypto.kt:196`) son clases hermanas, así que no hay captura cruzada. La cancelación se relanza primero (`:99`).
- `launch(start = ATOMIC)` se conserva (BOV-3). No se toca `unlockWithKey` ni su limpieza de la clave.
- Los tests pinchan lo que no debe cambiar, no solo el caso feliz: 7 días, reloj (A11), otro error (`IOException` desde `clockHighWater`), cancelación con mutex ocupado, y el orden en que se llama («off» antes del texto).
- Se hizo un esqueleto sin lógica para que el rojo fuera de comportamiento.
- El cambio de `BOV-3 … reports the store's message` está justificado (ver abajo).
- Solo hay un llamador de producción de `launchUnlockWithKey` (`VaultScreen.kt:318`). `KeyPlacement.kt:135` solo lo menciona en un comentario.

### Issues

**Critical:** ninguno.

**Important:** ninguno.

**Minor**

1. **El informe describe mal el orden de las comprobaciones para bóvedas de versión desconocida** (informe, preocupación 2; test «master password being due comes before the key check»).
   - Versión 2/3 (el caso real): `VaultCrypto.resolve` devuelve el archivo sin probar la clave (`vault/Vault.kt:318`). Después `biometricRefusal` (`VaultStore.kt:450`) rechaza con `MasterRequired` antes de que `opened()` pruebe la clave (`VaultStore.kt:342`). Con huella rancia y maestra pendiente por los 7 días: **`MasterRequired`**.
   - `VERSION_UNKNOWN` (=0, archivo heredado de la 1.0 o 1.1): `resolve` prueba la clave primero (`Vault.kt:317-322`, `openAny`) y lanza **`CryptoFailure`** (`VaultStore.kt:444-449`) antes de mirar el reloj o los 7 días. Con huella rancia, la huella se desactiva aunque la maestra esté pendiente.
   - **No importa:** ambos resultados son seguros y coherentes con la regla 4. `MasterRequired` nunca desactiva. Una clave que no abre nada puede desactivar aunque la maestra esté pendiente. Solo cambia la frase del informe («se deciden desde el archivo antes de probar la clave»), que vale para v2/v3 y no para `VERSION_UNKNOWN`. El test de orden solo fija v3.
   - Coste del orden en v2/v3: tras un cambio de maestra `masterCheckedAt` se renueva, así que casi nunca coincide. Tras importar una copia de más de 7 días, el usuario ve primero la petición de la maestra y descubre la huella rancia un desbloqueo después. Un paso más, sin pérdida de datos.
2. **Comentario y prueba de `onWrongKey` que lanza, imprecisos.** `VaultStore.kt:105` dice «The Keystore refused: the fingerprint stays on…». Pero `BiometricVault.disable` (`android/BiometricVault.kt:151-158`) borra las preferencias con `edit{}` (`apply`, que no lanza) y `deleteKey()` va en `runCatching` (`:102-104`). Una negativa del Keystore no sale como excepción, y `enabled()` ya es `false`. El `try` es defensivo para un camino casi inalcanzable; es inocuo y está bien probado, pero el motivo del comentario y del test (`KeyStoreException`) no es el real. Suavizar el comentario.
3. **El test de cableado de la pantalla no fija el orden.** La regex (`VaultStoreTest.kt`, «the unlock screen turns the fingerprint off…») acepta `disable` antes que `fingerprintStale = true`, y ese orden es lo que da sentido a la marca si `disable` lanza. Tampoco comprueba que `if (bio)` siga rigiendo el botón (`VaultScreen.kt:350`). Es el límite de un test de código fuente; la prueba en el móvil lo cubre.
4. **Nombres de tests.** `BOV-3 … reports the store's message` ya no da «el mensaje del store», sino `FINGERPRINT_STALE`. El test «version-1 vault» construye `VERSION_UNKNOWN`, no `version == 1` (que da siempre `MASTER_DUE`, `Vault.kt:413`). La rama `is CryptoFailure` de `biometricUnlockError` (`VaultStore.kt:75`) ya no la alcanza `launchUnlockWithKey`; se conserva por el test LIBRE-4 (`VaultStoreTest.kt:937`) y no estorba.
5. **Giro de pantalla durante el aviso:** `onWrongKey` sí desactiva la huella (usa el `context` viejo, que sirve para preferencias), pero `fingerprintStale` y `error` se escriben en el estado de la composición destruida. La pantalla nueva no enseña `FINGERPRINT_STALE`: el botón simplemente no está. Es el límite de siempre de `error` bajo BOV-3 y la regla del proyecto (no `rememberSaveable`), pero ahora el cambio de estado es silencioso. Aceptable; ⚠️ a mirar en la prueba en el móvil.
6. **Si `disable` lanza de verdad**, `fingerprintStale` no sobrevive al siguiente `UnlockVault` y el aviso automático (`VaultScreen.kt:324`) vuelve a salir en cada bloqueo. Es la preocupación 3 del informe, generalizada a «cada bloqueo», no solo al giro. Caso casi inalcanzable (ver punto 2).
7. `BiometricVault.disable` hace IPC del Keystore en el hilo principal (la lambda corre en el scope de la pantalla). Ya se hace así en «Crear bóveda» y «Borrar bóveda» (`VaultScreen.kt:371`), así que no es nuevo.

**Para el Abogado Digital (no es defecto de esta tarea):** el texto (copiado del informe, así que correcto aquí) afirma como hecho las dos causas entre paréntesis. GCM no distingue una clave ajena de una bóveda dañada (`Vault.kt:285,300`), y con datos dañados también se desactivaría la huella. Ya apuntado en el informe (preocupación 1) y aceptado por el diseño. Que decida la revisión final si «puede deberse a» encaja mejor.

### Respuesta a los riesgos con nombre

- **Solo `CryptoFailure` desactiva:** confirmado. Única llamada en `VaultStore.kt:103`. `MasterRequired` va a la genérica (`:108-109`) y otro error también. Tests de 7 días, reloj, otro error y maestra pendiente con clave ajena.
- **Orden en `unlockWithKeyLocked`:** ver Minor 1. Clave rancia en la ventana de 7 días: `MasterRequired` en v2/v3, `CryptoFailure` en `VERSION_UNKNOWN`. No afecta a la corrección ni a la seguridad.
- **Clave a cero en cada salida:** no cambia. `VaultStore.kt:343-345` (`opened`), `:446-449` (`resolve`), `:351` (época cambiada) y el `catch Throwable` de `unlockWithKey` (`:435-437`). Los tests nuevos comprueban `all { it == 0 }` en cada rama.
- **`onWrongKey` una vez:** una sola llamada en una sola rama. Los tests lo fijan con la lista de eventos exacta y con `off == 1`.
- **`disable` que lanza:** no tumba la corrutina y sale `FINGERPRINT_STALE` (`VaultStore.kt:102-107`; test «if turning the fingerprint off fails…»). Ver Minor 2 sobre lo realista del caso.
- **UI:** `fingerprintStale` es estado de Compose; su escritura y `error = it` recomponen y `bio` se vuelve a leer (`VaultScreen.kt:310`). El botón (`:350`) y el aviso de 7 días (`:352`, solo con `enabled`) desaparecen. `LaunchedEffect(Unit)` no se repite, así que no hay bucle.
- **Test BOV-3 cambiado:** justificado. Una clave sin sentido (`ByteArray(32){1}`) sobre una bóveda v3 es justo el caso que CRY-A02 reclasifica. Conserva la comprobación de la clave a cero. El otro test LIBRE-4 (`:937`) sigue verdadero. Los otros usos de `launchUnlockWithKey` en tests (`:951`, `:965`) son de cancelación y no llegan a un `CryptoFailure`.
- **Texto y palabras prohibidas:** ver Spec Compliance. `SourceTextsTest` recorre `app/src/main/java`. No está en `ALL_TOOL_TEXTS`, por el mismo motivo que `MASTER_TOO_WEAK` (`ToolTextsTest.everyAppTextIsInTheAnnex`, `ToolTextsTest.kt:71-77`); correcto.

### Assessment

**Task quality:** Approved

**Reasoning:** Cumple el encargo y los focos de revisión, sin defectos críticos ni importantes. Solo `CryptoFailure` desactiva la huella, la clave se pone a cero como antes, `onWrongKey` se llama una vez y un `disable` que lance no tumba la corrutina ni oculta el texto. Las dos desviaciones mejoran o no estorban. Lo que queda son Minor: una frase del informe sobre el orden que no vale para `VERSION_UNKNOWN`, un comentario sobre el Keystore que no es exacto, y detalles de nombres y de ajuste del test de cableado. Ninguno bloquea.

### Revisión experto-seguridad-cripto

- **Amenaza y alcance:** sin atacante nuevo. El arreglo elimina un blob rancio (Keystore y preferencias) y avisa. No hay primitivas, formato, AAD ni versión nuevos (descartado en el diseño). Sin cripto propia: la decisión «la clave no abre» es la autenticación GCM de siempre (`CryptoFailure` desde `VaultCrypto.read`, `vault/VaultStore.kt:342-346`). No hay comparación a mano ni canal lateral nuevo.
- **Oráculo:** la huella solo entrega una clave tras el prompt biométrico del sistema. `FINGERPRINT_STALE` no distingue «ajena» de «dañada» más de lo que ya hacía «Contraseña incorrecta o datos dañados». Sin información nueva hacia fuera.
- **Desactivación inducida:** para forzar un `CryptoFailure` hace falta alterar `vault.bin` (almacenamiento privado de la app) o la propia huella. Quien puede hacerlo ya controla la app, y el coste es solo reactivar la huella. Carrera estrecha aceptada: un aviso anterior a un cambio de maestra, que llegara después de reactivar la huella, la desactivaría otra vez.
- **Secretos en memoria:** la clave se pone a cero en todas las salidas (`VaultStore.kt:343-345, 351, 435-437, 446-449`). No va a `rememberSaveable` ni a un `Bundle`; el único estado nuevo es un booleano (`VaultScreen.kt:309`). El texto no contiene datos del usuario. Sin logs: el `catch` de `onWrongKey` no registra nada.
- **Fallo cerrado:** si `disable` falla, nada se abre; la bóveda queda `Locked` (test). Regla 4 cumplida: ni `MasterRequired` ni otro error desactivan.
- **Sin permisos, red, dependencias ni componentes nuevos.**
- Pendiente de prueba en el móvil: que el botón desaparezca y salga el texto con una huella rancia real (BiometricPrompt y Keystore no se ejercitan en JVM).

### Revisión Ingeniero Móvil Senior (file:line)

- **`seguridad-criptografia-movil`:** la huella sigue ligada a `CryptoObject`. No se toca el envuelto (`BiometricVault.kt` intacto). Desactivar usa `BiometricVault.disable` (`android/BiometricVault.kt:151-158`), el mismo método que el cambio de maestra, la importación, «Crear bóveda» y «Borrar bóveda» (`ui/screens/VaultBackup.kt:123-131`, `VaultScreen.kt:371`).
- **`desarrollo-movil-senior`, concurrencia:**
  - `ATOMIC` + cancelación relanzada antes que cualquier rama (`VaultStore.kt:96-100`). El caso de scope ya cancelado con mutex libre alcanza `CryptoFailure` y desactiva la huella, lo correcto para una clave ajena aunque la pantalla ya no exista. `onError` escribe en estado desechado (Minor 5).
  - `catch (off: Exception)` no traga `CancellationException`, porque `onWrongKey` no es `suspend`.
  - `Dispatchers`: IPC del Keystore en el principal, ya habitual (Minor 7).
- **Compose:** estado local de interfaz con `remember` (`VaultScreen.kt:309`). `bio` es un `val` recalculado en cada recomposición (`:310`). La marca se escribe antes de `disable` (`:319-320`). Sin bucle de avisos (`:324`).
- **Compatibilidad de API:** `onError` sigue siendo la lambda final, así que los usos viejos compilan (`VaultStoreTest.kt:951, 965`). El valor por defecto `{}` evita tocar a nadie más.
- **Pruebas:** la lógica está en `vault/` y se prueba en JVM; el cableado de `ui/` se fija con un test de código fuente, al estilo de `MasterPolicyTest`, con las limitaciones del Minor 3. Falta la prueba en el móvil (informe, preocupación 4).
