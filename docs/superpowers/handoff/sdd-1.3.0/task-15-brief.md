### Task 15: Documentos, versión 1.3.0 y revisión de los textos

**Archivos:**
- Modificar: `docs/privacidad.html`, `docs/ficha-google-play.md`, `docs/PUBLICAR.md`, el README (funciones, lista manual del móvil: StrongBox, Gboard, compartir con WhatsApp y Gmail, HEIC) y `app/build.gradle.kts` (`versionName = "1.3.0"`).
- Modificar: la pantalla «Cómo protegemos tus datos» de `ToolsScreen.kt`, que dice que todo se hace en el móvil y sigue siendo cierto. Añadir: «Las herramientas de imagen no quitan lo que se ve».
- Test: `ToolTextsTest` (tarea 4), ampliado con los textos de las tareas 5 a 13.

- [ ] **Step 1: Apply** los cambios de «Cambios en los documentos» del anexo, tal cual, con fecha 27-10-2026 o la de publicación.

- [ ] **Step 2: Run** `ToolTextsTest` y la verificación completa. Expected: PASS. `grep -rn "lápiz\|falsas" docs app/src/main` no devuelve nada.

- [ ] **Step 3: Commit** `Seguridad 1.3.0: documentación y versión`.

