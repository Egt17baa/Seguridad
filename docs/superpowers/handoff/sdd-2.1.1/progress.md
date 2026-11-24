# Clavero 2.1.1: registro

Rama `ccr-0aa27f95-u038cd`, desde `main` en `a29d88c` (etiqueta `v2.1.0`). Diseño `docs/superpowers/specs/2026-11-24-clavero-2.1.1-barra-design.md`. La decisión de hacerla y la aprobación del diseño, en `sdd-2.1/progress.md` (24-11-2026).

- Abogado, principio (24-11-2026): `abogado-2.1.1-diseno.md`, «se puede programar con cambios». O-1 a O-7 y R-1 a R-6 aceptados, sin objeción técnica, y apuntados en el diseño («Dictamen del Abogado al principio»).
- Abogado decision (O-1): O-A2 de la 2.1 se sustituye solo para la barra de abajo, con las condiciones de O-2; lo demás de O-A2 sigue.
- Ruling (R-6): si Android pide quitar animaciones, la cápsula no hace ninguna transición, tampoco la pastilla.
- Plan (24-11-2026): `docs/superpowers/plans/2026-11-24-clavero-2.1.1-barra.md`, 4 tareas. Ruling del controlador: el contenido pasa bajo la cápsula solo en las cinco pestañas (`UNDER_BAR_ROUTES`); las demás pantallas terminan encima, como en la 2.1 (objetivo 3, O-4 y O-5 sin riesgo en formularios e imágenes).
- Task 1 BLOCKED by measurement: over the O-5 list the capsule needs 0.77-0.89 (> 2.1 bar); over the background alone ~0.45-0.55. Owner decision (24-11-2026): option B («B»): the content never passes under the capsule; the capsule floats over the app background, more translucent. Design: «Corrección (dueño, 24-11-2026): opción B».
- Ruling: pill = `primary` with `onPrimary` (secondaryContainer reaches only 1.2-1.4:1 against the capsule).
