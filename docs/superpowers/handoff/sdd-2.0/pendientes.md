# Clavero 2.0: pendientes aplazados (lista viva)

Cada aplazado del registro (`progress.md`), con su destino y su estado. La revisión final (tarea 12) no empieza hasta que cada fila tenga destino; no termina con ninguna fila «Abierto» salvo las del móvil y las del dueño.

| # | Aplazado | De | Destino | Estado |
|---|---|---|---|---|
| 1 | Botón para `clearAllHistory` | T3 | Tarea 10 (Ajustes) | Abierto |
| 2 | `VaultStore.kt` demasiado grande (sacar la importación a su pieza) | T2 | Revisión final (parte «formato y bóvedas») | Abierto |
| 3 | Medir un `upsert` cerca de 24 MiB (JVM: ~430 ms) y el recálculo de «Salud» en el móvil | T1 M-6, T4 | Revisión final y móvil | Abierto |
| 4 | `DELETE_IDENTITY_CONFIRMATION` («no hay forma de recuperarla») frente a O-2 | T3 | Abogado final | Abierto |
| 5 | La contraseña del wifi en «Comprobar filtraciones» | T5 | Abogado final | Abierto |
| 6 | R-C10: una línea en la política sobre los ajustes de aspecto | T5 bis | Tarea 12 (documentos) | Abierto |
| 7 | O-3: el paso a v4 en «Novedades», notas y README | T1 | Tarea 12 (documentos) | Abierto |
| 8 | Política: el número y el color de las bóvedas sin cifrar en el móvil (opción A), las copias `seguridad-antes-de-2-0-` en A6, varias carpetas con calendario y la línea «si hubo cambios» (D-3); lo que `settings.json` guarda sin cifrar desde la tarea 8: por carpeta, la marca de cambio de D-3 (SHA-256 de fecha, coste y `vaultTag`), el id de la petición de A6 (UUID) y su resultado (contadores), y por bóveda el SHA-256 de la sal (pública) como marca de la petición | T1, T2, T8 | Tarea 12 (documentos) y Abogado final | Abierto |
| 9 | D-3: una copia con calendario se hace aunque nada haya cambiado | T8 | Dueño: saltarla si no hubo cambios (13-11) | Hecho en la ronda 3 de la tarea 8 (`95c8667`) |
| 10 | Margen del contraste (4,5:1 sin margen; el redondeo de la GPU) | T5 bis | Móvil; si se queda corto, objetivo 4,6 | Abierto |
| 11 | Móvil: difuminado desde Android 12, aspecto en 8 a 11, desplazamiento, foco bajo las barras, menús, «Colores del fondo de pantalla» | T5 bis | Tarea 11 (Maestro) y dueño | Abierto |
| 12 | Móvil: fila elegida opaca, marca quieta sin animaciones, deslizar y vibración, oferta al volver de otra app | T6 | Tarea 11 y dueño | Abierto |
| 13 | Móvil: diálogo al cambiar de pestaña, pestaña durante un guardado (Info-3), botones al 200 %, propuesta al pegar | T7 | Tarea 11 y dueño | Abierto |
| 13 bis | i-1 de T8: precisión de `lastModified`/`setLastModified` (la marca de D-3) | T8 | Móvil (tarea 11) | Pendiente |
| 13 ter | i-3 de T8 (ronda 4): TalkBack no llega al campo de la contraseña bajo el diálogo de la maestra | T8 | Móvil (tarea 11) | Pendiente |
| 14 | Textos para el Abogado de cada tarea (secciones de los informes `task-*-report.md`) | T1 a T8 | Abogado final | Abierto |
| 15 | O-C1: buscar anterioridades del logo y de la marca antes de publicar el icono | Abogado Cristal | Dueño | Pendiente del dueño |
| 16 | El icono cambiable (`activity-alias`) | Dueño | Fuera hasta que lo apruebe | Pendiente del dueño |
| 17 | **Riesgo con nombre (Important, re-revisión 6 de T8, I-1): la petición de A6 heredada de la 1.4.3 (`"1.x"`).** No tiene marca de sal y su pertenencia no está acotada: toda carpeta con `replaceRequest == null` cuenta como suya (`A6Text.kt:30`, `:33`, `:44`, `:57`; `BackupSchedule.kt:46`; `SettingsStore.kt:208-216`). Camino a (ya existía): una petición que llegó pendiente de la 1.4.3 termina después de un cambio de maestra en la 2.0 sin «Borrar…» y sale «se borraron las copias … hechas antes del cambio de contraseña», falso para ese último cambio. Camino b (nuevo en la ronda 6): la petición terminada y cerrada, y una carpeta nueva la reabre. Alcance: solo una bóveda que llegó de la 1.4.3 con `autoBackupReplacePending = true` (`SettingsStore.kt:215`). Diseño decidido (advisor): (1) una petición sin marca recibe siempre el texto prudente («de antes de un cambio de contraseña anterior…»), que el revisor confirmó verdadero también sin cambio posterior; (2) miembros de `"1.x"` solo la carpeta con `replacePending && replaceRequest == null` (pendiente) o con `replaceRequest == "1.x"` y `replaceDone("1.x")` (terminada; `Settings.kt:321-325`), nunca una con `replaceRequest == null` sin `replacePending`; viaja con `keptPast`/`restored` y se borra con `withoutReplaceRequest`. Tests: los estados `"1.x"` y `stale` en el exhaustivo, las ramas de `a6StaleText` con una carpeta y con parcial (m-2 de la re-revisión 6) y los dos caminos desde un `settings.json` real de la 1.4.3 | T8 | Ronda de arreglos de la revisión de la rama (parte «copias y ajustes») | Abierto |
| 18 | m-1 de la re-revisión 6 (quitar una carpeta hecha de una petición cerrada repite el éxito: una línea que desaparece solo cuenta como cambio si estaba pendiente) y m-3 (KDoc huérfano de `changeMark`, `VaultStore.kt:418-426`; KDoc de `a6Decide` y de `a6Text` con `stale`) | T8 | Ronda de arreglos de la revisión de la rama | Abierto |
| 19 | m-4 de la re-revisión 6: «Las copias hechas después de ese cambio no se han borrado» peca por prudencia (la rotación puede borrar alguna); redacción propuesta en `task-8-rereview-6.md` | T8 | Abogado final | Abierto |
| 20 | Si una importación no se guarda y también falla su deshacer, una petición de A6 pendiente se pierde sin aviso (`VaultStore.kt:1500-1506`; dos fallos seguidos) | T8 | Revisión de la rama (parte «copias y ajustes») | Abierto |
| 21 | i-1 de la re-revisión 5: un resultado de A6 que solo trae fallos se apunta como dicho al intentarlo y se pierde si el proceso muere antes de leerlo (`AutoBackup.kt:880-886`); lo atenúa el `lastError` de la carpeta en Ajustes | T8 | Revisión de la rama (parte «copias y ajustes») | Abierto |
