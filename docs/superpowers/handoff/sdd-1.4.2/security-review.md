# `security-review` de la rama 1.4.2 (10-11-2026)

**Rama:** `ccr-a1a6eabb-765f9g`, `ba903e7..0b62c3c`.

**Resultado:** ninguna vulnerabilidad con una confianza de 8 sobre 10 o más.

Lo que se miró y por qué se descartó (confianza entre paréntesis):
- **`configChanges` en `MainActivity` (1).** `FLAG_SECURE` se pone una vez en la ventana, que ya no se rehace. El bloqueo de `onStop` y de la ventana múltiple ya se saltaba el giro con `isChangingConfigurations`. No hay componentes, *intents* ni extras nuevos.
- **Un proveedor hostil cuyo URI se guarda y vuelve como `EXTRA_INITIAL_URI` (2).** Solo se recuerda un resultado aceptado (`isAcceptableDocument`). `isAcceptableDocumentUri` se comprueba al guardar y al leer, con un tope de 2048 caracteres. Es solo una indicación para el selector del sistema: no da permisos, Clavero no lo abre y nunca pasa a un `Intent`.
- **Los URI, con el nombre del archivo, en `picker_places` (2).** El archivo es privado, `allowBackup="false"`, y `backup_rules.xml` y `data_extraction_rules.xml` excluyen `sharedpref`. Los sitios con datos privados solo viven en memoria. La revisión señaló que `HASH_FILE` y `TEXT_OPEN` todavía se apuntaban. **Arreglado:** desde la ronda de esta revisión también viven solo en memoria (`PickerPlacesTest.privatePlacesAreOnlyInMemoryAndGoWithEveryLock`).
- **Avisos plegados en «Más información» (2).** Cada uno tiene su línea corta a la vista. Es una cuestión de textos para el Abogado Digital, sin vía de ataque.
- **`rememberSaveable` en `MoreInfo` (1).** Solo guarda un booleano.
- **El aviso de una activación interrumpida en un `finally` (1).** Solo decide si el aviso cuenta como visto.
- **«Borrar bóveda» (1).** `forgetAll()` solo borra más.
- **`LOGIN_TOTP_BROKEN` (1).** Solo cambia el motivo. Los topes van antes, y el 2FA dañado no entra.
- **Permisos, conexiones, registros, criptografía o dependencias nuevos:** ninguno.

## Revisión experto-seguridad-cripto
- No cambia ningún código criptográfico. El único cambio de la regla de la maestra es el KDoc (RT-11, opción B, `vault/MasterPolicy.kt`).
- El URI del selector solo es una indicación para el selector del sistema (`ui/screens/Pickers.kt`).

## Revisión Ingeniero Móvil Senior
- `configChanges` conserva `FLAG_SECURE` y el bloqueo de `onStop` (`MainActivity.kt`).
- Los sitios en memoria se olvidan con un colector del estado de la bóveda (`AppContainer.kt`, `init`).
- MASVS-STORAGE: solo preferencias privadas, fuera de las copias. MASVS-PLATFORM: ninguna superficie exportada nueva.

## Textos para el Abogado Digital (revisión final)
- Los avisos plegados: que las líneas cortas a la vista dicen lo bastante.
