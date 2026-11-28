# Clavero 2.1.2: registro

Rama `ccr-0aa27f95-u038cd`, desde `main` en `0a08629`. Diseño `docs/superpowers/specs/2026-11-27-clavero-2.1.2-design.md`; plan `docs/superpowers/plans/2026-11-27-clavero-2.1.2.md`, 13 tareas. Origen: la revisión de seguridad de la 2.1.1 hecha por el propio proyecto con la skill pública `security-audit` (MIT); informe en `docs/auditorias/2026-11-27-auditoria-2.1.1-security-audit.md` y registros en `docs/auditorias/2026-11-27-auditoria-2.1.1-security-audit-findings.json`. Cloudflare no participó y no es una auditoría independiente.

- Abogado, principio (27-11-2026): `abogado-2.1.2-inicio.md`, **visto bueno con cambios**. Obligatorios O-1 a O-8 y recomendados R-1 a R-10, apuntados en el diseño (§7 bis, «Corrección (Abogado, inicio, 27-11-2026)»).
  - O-1: ningún texto publicado dice «auditoría de Cloudflare», «externa», «independiente» ni «auditada»; en Play, ni «Cloudflare» ni «auditoría».
  - O-2: C-2 en la copia de antes de importar (sin copia no se importa; el texto de error, cierto).
  - O-3: C-2 en la copia automática («Última copia» no enseña la hora de una copia borrada).
  - O-4: C-1 y «Bóveda nueva» (lo que encuentra el usuario al volver, y lo que dicen las notas).
  - O-5: R-24 (una tarjeta con el nombre de tu identidad no enseña el aviso de «tu contacto»).
  - O-6: R-14 (los textos de A6 siguen ciertos con las carpetas que ya no se usan).
  - O-7: la política dice que A6 alcanza esas carpetas y lleva la fecha del día de la fusión.
  - O-8: las notas de la 2.1.2 dicen solo lo probado.
  - R-1 a R-9: textos de política y README, C-4, R-32, R-29 y R-30, R-21, R-17, R-33, C-3 y R-23. R-10: nombre y cabecera del informe (hecho en la tarea 1).
- Ruling (el dueño, 27-11-2026): el informe y el JSON se llaman `…-security-audit.md` y `…-security-audit-findings.json`; la cabecera, en español, dice que se usó la skill pública `security-audit` (MIT) del repositorio de Cloudflare, ejecutada por el propio proyecto, sin participación de Cloudflare (O-1, R-10). Las rutas de la carpeta de trabajo se sustituyeron por «carpeta de trabajo de la auditoría». El informe lleva al final `NEEDS-VALIDATION.md` como anexo y una nota de que el diseño de la 2.1.2 arregla estos hallazgos.

| Tarea | Estado |
|---|---|
| 1. Registro, informe de la auditoría y visto bueno inicial del Abogado | Hecha |
| 2. V-1, el documento que devuelve el proveedor de la carpeta de copias | Pendiente |
| 3. C-1, salir de la app mientras se crea o se importa una bóveda nueva | Pendiente |
| 4. C-2, la copia hecha con la maestra antigua | Pendiente |
| 5. C-3, el coste de un nombre de carpeta enorme en JSON | Pendiente |
| 6. C-4, ZWNJ y ZWJ en un dominio | Pendiente |
| 7. Refuerzos de la bóveda (R-1 a R-4) | Pendiente |
| 8. Refuerzos de pantallas y sesiones (R-5 a R-12) | Pendiente |
| 9. Refuerzos de copias, carpetas y archivos (R-13 a R-20) | Pendiente |
| 10. Refuerzos de archivos hostiles (R-21 a R-23) | Pendiente |
| 11. Refuerzos de contactos, campos e imágenes (R-24 a R-28 y R-33) | Pendiente |
| 12. CI y publicación (R-29 y R-30) | Pendiente |
| 13. Documentación, versión y cierre del código (R-31 y R-32) | Pendiente |

Después de la 13: revisión de la rama, `code-review`, `security-review`, auditoría completa y Abogado final (con la fecha de la política).

**Revisión de la tarea 1 (27-11-2026):** hay que arreglar cosas; arregladas por el controlador. R-10 completado en el diseño (título, `:3` y `:7`); quitada la ruta de la carpeta temporal del plan (Paso 1 de la tarea 1); en el informe, «independiente» solo en la nota de origen y la valoración pasa a «La revisión no encontró hallazgos de severidad media o mayor». `findings.json` conserva, como registro en bruto, rutas relativas `agents/…` de la carpeta de trabajo de la auditoría, que no están en el repositorio. Para el Abogado final: la cabecera y el título del informe, el anexo en segunda persona (dirigido al dueño) y el título del diseño. El asunto del commit `453eb4e` dice «auditoría de Cloudflare» y no se corrige (sin `--amend`); los siguientes commits y el PR no usan esa expresión (O-1).
- **Tarea 2 (V-1):** hecha. Prueba en el móvil: copia automática en una carpeta local y en una de nube, con renombrado.
- **Tarea 3 (C-1):** hecha. Para las notas (O-4, O-8): vale en «Bóveda nueva» y en «Importar como bóveda nueva», probadas las dos.
- **Tarea 4 (C-2), Ruling (controlador, 27-11-2026):** se acepta `VaultStore.masterChanges` (en memoria, solo lo sube `changeMaster`) en lugar de `masterMark()`, que también cambia con la subida a v4 y con A8 (nota en el diseño, C-2).
- **Tarea 4 (C-2), director (27-11-2026):** aprueba las cuatro Rulings (`masterChanges`, I-1 con `AUTO_BACKUP_FAILED`, la copia de antes de importar en `replaceTargets`, el orden en `copies`), con la condición de la suite en verde en un clon limpio, que se hace en la revisión final. Riesgo aceptado: si falla la escritura en disco al apuntar la copia y Android cierra la app, A6 la encuentra solo como copia sin apuntar.
- **Tarea 5 (C-3):** hecha. El mismo tope de carpeta (`MAX_TITLE`) en todos los lectores; un nombre más largo se descarta y el elemento entra sin carpeta.
- **Tarea 6 (C-4):** hecha (`98f8561` … `e9b3665`). Los ZWNJ y ZWJ de un dominio se conservan, como en un navegador; la fila del duplicado y el Inspector los marcan, y el título sugerido desde la URL no los lleva. Revisión aprobada, con sus menores arreglados.
- **Tarea 7 (R-1 a R-4):** hecha (`cdf0275`, `2182551`). Abrir un adjunto, subir la bóveda, borrarla y las acciones en grupo respetan un bloqueo en medio; la época se toma al pulsar. **Ruling (controlador, 27-11-2026):** `destroyActive(store)` recibe la bóveda confirmada, en lugar de `destroyActive()` del plan, para que un cambio de bóveda en medio no borre otra; el control de la época se amplía a `deleteFolder`, `delete` y `restoreVersion` (M-2 de la revisión). Crear, renombrar y mover carpetas, las vistas y los colores de etiqueta no tocan entradas y se quedan sin época (aceptado).
- **Tarea 8 (R-5 a R-12):** hecha (`80fb78d`, `257fa4b`). **Rulings (controlador, 28-11-2026, aceptados por la revisión):** R-9 usa `QrHandoff.claim`/`clearUnclaimed()` en lugar de `clear()`, para que «Abrir lo recibido» no pierda el texto que espera ante la bóveda bloqueada (solo se puede reclamar el texto `RECEIVED`, que no es secreto); R-10 pone `KdfLock.derive` en `SecretShareRuns` (la espera se suspende y se cancela), en lugar de un cerrojo por ronda dentro de `Slip39`. Coste aceptado: «Repartir un secreto» retiene el cerrojo toda la ejecución, unos segundos con el exponente 8 (R38, R40). Prueba en el móvil: R-9 con las animaciones a 0.
- **Tarea 9 (R-13, R-15 a R-20):** hecha (`ecf0a26`, `8704c19`). La revisión encontró dos Important, arreglados: un temporal con una fecha que no se lee o lejana ya no deja A6 pendiente para siempre (como mucho unas 48 h; el que no se lee nunca se borra ni cuenta), y volver a elegir la misma carpeta escrita de otra manera se acepta y conserva sus listas y su petición (`canAddTarget(uri, except)`, `pastTargets` por `sameFolder`). Aceptado (informativo, N-1): `oldLeft` en memoria va por la URI y se pierde al cambiar su escritura, como al cambiar a otra carpeta; la copia sigue en la lista del destino. **Para el Abogado final:** `AUTO_BACKUP_OLD_NOT_DELETED` sale también cuando un temporal reciente de otra bóveda frena A6 en la misma carpeta (las copias de la lista sí se borraron); confirmar que sigue siendo cierto.
