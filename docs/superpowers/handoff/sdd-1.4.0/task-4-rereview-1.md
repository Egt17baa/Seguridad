# Tarea 4, ronda de arreglos 1: segunda revisión acotada (0b9cb46..54161a8)

### Finding Verdicts

- **I-1. «Como frase BIP-39» sin aviso de que las partes de una cartera dan otra frase** — ADDRESSED. `SHARE_RESULT_PHRASE_NOTE` (`SecretShareTexts.kt:134-136`) se pinta con `Output(context, phrase, note = SHARE_RESULT_PHRASE_NOTE)` (`SecretShareScreen.kt:401`), y `Output` dibuja la nota entre el valor y «Copiar» (`:224-228`). Es fiel al código: `phraseOrNull` (`SecretShareInput.kt:81-82`) convierte cualquier secreto de 16 a 32 bytes en pasos de 4 y no sabe de dónde viene. El texto usa «cartera», no «wallet», y no lleva palabras prohibidas (`SourceTextsTest` verde). No promete nada que el código no haga. `PlatformSourcesTest` fija la llamada, el orden respecto a `copySensitive` y el texto exacto (`PlatformSourcesTest.kt:253-260`).
- **M-1. Límites de «Recomponer» que no cuadran** — ADDRESSED. `SHARE_MAX_PART_LINE_CHARS = 59 * 9 = 531` y `SHARE_MAX_PARTS_CHARS = 256 * 531 = 135.936` (`SecretShareInput.kt:63`, `:69`). Una parte de 59 palabras de 8 letras con su salto de línea ocupa 531, así que caben las 256 partes más largas. Es coherente con `SLIP39_MAX_PART_CHARS = 1_000` (`Slip39.kt:170`), que es mayor que 530. El test de borde es real (`SecretShareInputTest.kt:287-298`): 256 × 530 + 255 = 135.935 caracteres pasan, rellenar hasta 135.936 pasa, y 135.937 da `SHARE_PARTS_TOO_LONG`. `Slip39Test.boundsComeBeforeAnyWork` (`Slip39Test.kt:383-385`) comprueba que las 256 partes largas superan los límites y fallan después, en el checksum, y no en un tope.
- **M-2. Los vectores inválidos solo comprobaban que hubiera algún fallo** — ADDRESSED. `officialVectors` compara el tipo de mensaje (números sustituidos por `#`) de cada vector inválido con `INVALID_VECTOR_REASONS` (`Slip39Test.kt:57-60`, `:347-362`). El mapa tiene 30 entradas, afirmadas con `assertEquals(30, ...)`, y es el de la revisión más los casos 5 y 24. Un vector inválido sin entrada en el mapa hace fallar el test (`requireNotNull`). Cada mensaje del mapa es distinto de los demás tras quitar los números, así que `kind` no confunde dos motivos. He ejecutado la clase: `officialVectors` PASSED.
- **M-3. `splitInput` calculaba sugerencias que no se usaban** — ADDRESSED. `splitInput` ya no llama a `bip39.check`: usa `normalize`, el nuevo `Bip39.isWord` (`Bip39.kt:33-34`, una búsqueda en el mapa) y `entropyOf` (`SecretShareInput.kt:98-103`). Conserva el orden de antes (palabra desconocida, luego el número, luego el checksum) y la posición `unknown + 1`. Lo cubre `aPhraseWithAnUnknownWordNamesTheFirstOne` (`SecretShareInputTest.kt:300-306`).

### New Breakage in the Fix Diff

Ninguno. Comprobaciones pedidas:
- **Topes sobre el `String` en bruto, antes de cualquier trabajo.** `combineRequest` mide `typedParts.length > SHARE_MAX_PARTS_CHARS` antes de `partsOf` (`SecretShareInput.kt:118`). `partsOf` es lineal (`lines()` y `isNotBlank`) y como mucho sobre 135.936 caracteres. El tope de 256 partes se aplica después de dividir, pero con el texto ya acotado. La biblioteca vuelve a comprobar 256 partes y 1.000 caracteres por parte antes de decodificar (`Slip39.kt:141`, `:206`). `splitInput` mide 1.000 caracteres antes de nada (`SecretShareInput.kt:84`). `vm.limited` sigue rechazando un pegado más largo que el tope (`SecretShareScreen.kt:169-177`). La búsqueda de repetidos sigue siendo lineal (`HashMap`). No hay trabajo cuadrático nuevo, y la subida del tope de 40.000 a 135.936 solo multiplica por 3,4 un trabajo lineal.
- **Secretos en logs o mensajes.** El diff no añade ningún `Log`, `println` ni excepción con datos. `isWord` solo devuelve un booleano. La nota de I-1 y los mensajes nuevos no llevan palabras ni bytes. `kind()` vive solo en el test.
- **Estado.** No se añade `rememberSaveable`; la copia sigue por `copySensitive` (`Output`, `:228`), y el conteo de 2 en `PlatformSourcesTest` sigue cierto.
- **Tests.** Ejecutados `Slip39Test`, `SecretShareInputTest`, `PlatformSourcesTest` y `SourceTextsTest`: `BUILD SUCCESSFUL`.

### Out-of-Scope Observations

Ninguna.

### Verdict

**Fix round:** All findings addressed, no new Critical/Important breakage.

## Revisión experto-seguridad-cripto

- I-1: la nota es veraz respecto al código (`SecretShareInput.kt:81-82`) y cierra el riesgo de que se tome la frase por la de una cartera (`SecretShareScreen.kt:401`, `SecretShareTexts.kt:134-136`).
- Los topes siguen aplicándose al `String` en bruto antes de decodificar, normalizar o derivar claves (`SecretShareInput.kt:118`, `Slip39.kt:141`, `:206`). No hay trabajo no acotado ni cuadrático.
- M-2 hace que un cambio de motivo de rechazo (por ejemplo, del relleno al checksum) rompa el test (`Slip39Test.kt:57-60`, `:351-362`).
- No hay secretos en logs ni en mensajes (`Bip39.kt:33-34`, `SecretShareTexts.kt:134-136`).

## Revisión Ingeniero Móvil Senior

- M-3 quita trabajo del hilo principal al pulsar «Repartir» (`SecretShareInput.kt:98-103`).
- El campo de partes admite hasta 135.936 caracteres, algo que solo se alcanza al pegar; `vm.limited` sigue rechazando más (`SecretShareScreen.kt:169-177`). El trabajo en el hilo principal (`lines()` y `isNotBlank`) es lineal y de unos pocos milisegundos.
- La nota de I-1 usa `bodySmall` y `onSurfaceVariant`, como las demás notas (`SecretShareScreen.kt:227`), y se lee antes del botón.

## Textos para el Abogado Digital (revisión final)

- `SHARE_RESULT_PHRASE_NOTE` (`SecretShareTexts.kt:134-136`): «Esta es la frase BIP-39 de estos bytes. Si las partes vienen de una cartera, el secreto es su semilla y esta frase daría otra cartera distinta: usa la frase solo si repartiste una frase BIP-39.» Sin palabras prohibidas y con «cartera». Pendiente de la revisión final, junto con los demás textos de la tarea.
