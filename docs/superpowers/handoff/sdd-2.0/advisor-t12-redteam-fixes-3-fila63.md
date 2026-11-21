# Director: consulta de la fila 63 en la ronda 3, el test `resistsHostileDatesEnumsAndNamesInAV4Copy` (19-11-2026)

**Pregunta:** con la regla de los 32 hex, `RedTeam20CopyTest.resistsHostileDatesEnumsAndNamesInAV4Copy` (fuera de la lista autorizada) se pone en rojo porque su copia hostil lleva `"vaultTag":"<script>"`. ¿Se autoriza cambiarlo, y cómo?

Añadido a la lista de cambios de tests autorizados de `advisor-t12-redteam-fixes-3.md`.

Veredicto: autorizo el cambio del test con ajustes. La regla de los 32 hex no se replantea. Pero no hay commit con el parche tal como está: el punto 1 tiene que llevar también el cambio de orden de «Importar como bóveda nueva».

No he ejecutado nada. Solo he leído el código, el test y el parche.

**Motivos**
- **El test rojo no es una falsa alarma.** La etiqueta `"<script>"` es hostil y está escrita a mano (`RedTeam20CopyTest.kt:208`). El test daba por bueno que se importara (`:213-215`), y esa es justo la diferencia entre las puertas que cierra la fila 63.
  - La condición de parada protege los datos que la app escribió, y aquí no hay ninguno. La app solo escribe `newVaultTag` (`Vault.kt:334`, `:351`, `:372`, `:617`; `VaultStore.kt:1524`), y ningún formato publicado lleva `vaultTag`.
  - Los demás literales de los tests tienen 32 hex. Hay una excepción: los de `BackupCheckSessionTest.kt:159-184` («propia», «de-otra»), que no pasan por `checkCopyStructure`.
- **Al parche le falta el cambio de orden.** `VaultStore.kt:1524` sigue cambiando la etiqueta antes de `checkCopyData` (`:1528`), así que `"<script>"` todavía entra con `newTag = true`. Por eso solo se pone en rojo uno de los `informative63_`.
- **La propuesta del implementador es buena, con tres arreglos:**
  - quitar el `catch … continue` general;
  - un almacén distinto por caso: hoy es `store("fechas-${name.length}")` (`:210`), y con dos etiquetas dos casos compartirían carpeta y dependerían del orden;
  - comparar el mensaje exacto.

**Qué debe afirmar el test**

El test es `resistsHostileDatesEnumsAndNamesInAV4Copy` y conserva su nombre.
- Usa las mismas `entries` (`:203-208`) sin la etiqueta.
- Son cuatro casos explícitos. Cada uno usa su propio `store("fechas-$i")` y todos van por `importReplace(…)`, sin `newTag`: las tres puertas ya las cubren los `informative63_`.

| Etiqueta | Nombre | Debe dar |
|---|---|---|
| `"<script>"` | `""` | `CryptoFailure` con `message == COPY_DAMAGED`. Es el único caso que prueba la regla. |
| `"<script>"` | 5 000 «x» | `COPY_DAMAGED`. El KDoc dice que lo rechaza el nombre y que no prueba la etiqueta. |
| `"0123456789abcdef0123456789abcdef"` | `""` | Se importa, y cualquier `CryptoFailure` falla el test. Después, como hoy (`:217-225`): `trashLeavesIn`, `Expiry.dueSoon`, `backupCheckNotice` y `displayName` no lanzan nada; se bloquea, se abre con la maestra y los ids son `{a, b}`. |
| la misma etiqueta | 5 000 «x» | `COPY_DAMAGED` exacto, por la regla del nombre (`VaultLimits.kt:228`). |

- En los tres casos rechazados: `assertEquals(before, s.state.value)`, es decir, el rechazo no cambia nada. Si esto falla, es un hallazgo: se para y se consulta.
- Cualquier excepción que no sea `CryptoFailure` sigue fallando el test.

**Lo que hay que poner al día y anotar**
- **KDoc del test** (`:192-200`): se quitan «a vaultTag that is not hex» y «the rest is taken». Debe decir que una etiqueta que la app no escribe (fila 63, `isVaultTag`) o un nombre de más de 100 puntos de código dan `COPY_DAMAGED`, y que con una etiqueta como la de la app lo demás se importa y las pantallas no fallan.
- **KDoc de `checkCopyStructure`** (`BackupCheck.kt:271-274`): añadir la regla de la etiqueta.
- **Comentario de RT-21a** (`VaultStore.kt:1517-1519`): ponerlo al día, como ya pedía el Ruling.
- **Registro:**
  - el controlador anota esta autorización como añadido a la lista de cambios autorizados de `advisor-t12-redteam-fixes-3.md`;
  - el informe de la ronda 3 marca la fila «autorizado por el director (consulta de la fila 63)»;
  - el atacante lo vuelve a comprobar en la re-comprobación 3.

**Riesgos o dudas**
- **El caso de 32 hex no lo he ejecutado.** Que se importe lo deduzco de dos cosas: hoy el test está en verde con `"<script>"`, y la regla nueva es lo único que mira la etiqueta.
- **La configuración regional de `newVaultTag`.** Formatea con `"%02x".format` usando la configuración regional por defecto (`Vault.kt:334`, `minSdk` 26).
  - El `Formatter` de OpenJDK no localiza `%x`, pero no lo he comprobado en Android con cifras no latinas.
  - Si fallara, la regla rechazaría las copias de la propia app.
  - Prueba barata: repetir el test de las 1 000 salidas de `newVaultTag` con `Locale.setDefault` en `ar-EG` y en `fa-IR`, restaurándola en un `finally`. Esto solo lo comprueba en la JVM. Si falla, se para y se consulta.

## Revisión experto-seguridad-cripto
- **CWE-436 y R5 (las tres puertas dicen lo mismo):** solo se cumple con el cambio de orden (`VaultStore.kt:1524-1528`). El test deja de dar por buena una etiqueta hostil (`RedTeam20CopyTest.kt:208-215`).
- **Regla de lista blanca** (`isVaultTag`, ASCII `0-9a-f` y 32 caracteres; `BackupCheck.kt:284`, una vez aplicado el parche):
  - no toca ningún formato ni ninguna primitiva;
  - LIBRE-1 sigue intacto: nada publicado lleva `vaultTag`, y una v4 sin etiqueta recibe una antes de la regla (`Vault.kt:617`).
- **Veracidad:** el test compara el mensaje exacto, `COPY_DAMAGED`, y no cualquier `CryptoFailure`.
- **Cobertura:** las fechas en `Long.MAX` y `Long.MIN` y los enums desconocidos se siguen probando, ahora con una etiqueta válida. No se pierde cobertura de CWE-20.

## Revisión Ingeniero Móvil Senior
- **Aislamiento:** con un almacén por caso, el resultado no depende del orden (`RedTeam20CopyTest.kt:210`).
- **Ejecución:** `runBlocking` y `Dispatchers.Unconfined`, como hoy (`:71-74`). Sin `sleep`. Son cuatro importaciones con `RT20_KDF`, así que debería quedar por debajo de 10 s. Corre en las dos suites (`redteam/`).
- **Pantallas:** se prueban las mismas funciones puras, sin Compose (`:218-221`).
- **Configuración regional del dispositivo:** es el riesgo apuntado arriba (`Vault.kt:334`).

Modelo: el director.