# Tarea 7, segunda revisión, ronda 3 (base 27e817a, cabeza bb74e6d)

Solo lectura; no se ha vuelto a ejecutar la suite.

### Finding Verdicts
- **M-A (`addAttachment` sin época): cerrado.** `VaultStore.kt:1212` toma `val sinceLock = epoch` antes de `attachments.read(input)` y lo pasa a `modify(sinceLock = …)` (`:1215`). La comprobación `since != sinceLock` (`:776`) va dentro del mutex y antes de `attachments.add` (que está dentro del lambda), así que tras un bloqueo no se escribe ni referencia ni archivo. El `plain` se sigue borrando en `finally`. Test: `readingTheInputNeverHoldsTheVaultLock` ahora fija `false` sin referencia ni archivo (RED declarado con el código anterior), y `anAttachmentReadWhileOnlyOtherSavesHappenIsSaved` cubre el caso sin bloqueo.
- **Info-1 (KDoc de `epoch` y valor por defecto): cerrado.** El KDoc (`:318-321`) describe lo que hace el código: cada guardado por `modifyData` lee la época al pedirse, el editor da la de su toque y `addAttachment` la de antes de leer. `modifyData` y `modify` tienen `sinceLock: Int = epoch` (`:747`, `:766`): el argumento por defecto de Kotlin se evalúa en la llamada, en el hilo de quien llama, antes de `mutex.withLock`. `upsert` mantiene `Int? = null` y usa `sinceLock ?: epoch` (`:814`), así que ningún llamador cambia.

### Comprobaciones nombradas
1. **UI tras el `false` de `addAttachment`.** Único llamador: `ui/screens/VaultAttachments.kt:69-72`. Un `false` muestra «La bóveda se bloqueó: no se ha añadido.» (`:72`), texto veraz y ya existente. Tras un bloqueo la pantalla normalmente ya no está (la composición se va y su `scope` se cancela); si el usuario sigue mirando (bóveda reabierta enseguida) ve ese mensaje, no hay pérdida silenciosa. `busy` se limpia en `finally`.
2. **Efecto del nuevo valor por defecto en todos los llamadores.** `epoch` solo sube en `lock()` (`:692`, único `epoch++`). Por tanto un rechazo nuevo solo ocurre si hubo un bloqueo entre la llamada y la entrada al mutex **y** la bóveda se reabrió; sin reapertura ya daba `false` (`vault == null` / `dek == null`). No existe rechazo sin bloqueo. Los 24 llamadores de `modify`/`modifyData` (markUsed `:1068`, papelera, restaurar, deshacer, duplicar, acciones en bloque, carpetas, `removeAttachment`, `applyImport` `:924`) devuelven ya `Boolean` y la UI trata `false` como «bóveda bloqueada» (p. ej. `VaultAttachments.kt:171` «La bóveda se bloqueó: no se ha quitado.»; `markUsed` va en `runCatching` e ignora el resultado, es contabilidad). La importación conserva su `sinceLock` propio dentro del cambio (`:928`) y el valor por defecto de `modify` se suma sin contradecirlo. Ningún llamador lee la época antes de una suspensión larga y llama después a `modify` esperando el valor posterior. No hay llamador donde `false` se muestre como texto de error equivocado.

### New Issues in the Fix Diff
Ninguno.

### Out-of-Scope Observations
- Menor, sin acción: en `modifyData` la variable `since` (`:773`) y `sinceLock` coinciden en el caso normal; `since` solo sirve ya para la comparación. No afecta a nada.

### Verdict
APPROVED: M-A e Info-1 cerrados, sin hallazgos nuevos.

### Revisión experto-seguridad-cripto
- Decisión (b) de la 1.4.0 cumplida también para adjuntos: lo leído antes de un bloqueo no se escribe después, aunque se reabra (`VaultStore.kt:1212`, `:776`). El texto en claro se borra en `finally` (`:1226`-ss.).
- Sin rechazos de más: la época solo cambia en `lock()` (`:692`).

### Revisión Ingeniero Móvil Senior
- Argumento por defecto evaluado en la llamada, sin suspensión intermedia (`:747`, `:766`); sin cambios de API para llamadores.
- UI coherente: mensaje existente y veraz (`VaultAttachments.kt:72`), `busy` limpiado en `finally`.

### Textos para el Abogado Digital (revisión final)
- Ninguno nuevo. «La bóveda se bloqueó: no se ha añadido.» ya existía y es veraz.
