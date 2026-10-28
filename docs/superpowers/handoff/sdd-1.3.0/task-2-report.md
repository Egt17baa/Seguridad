# Task 2: Limpiar foto, PNG y WebP: informe

## Implementado
- `Png.kt`: lista cerrada IHDR, PLTE, IDAT, IEND, tRNS, gAMA, cHRM, sRGB, iCCP, sBIT, copiados byte a byte con su CRC. CRC32 de TODOS los bloques (también los descartados), tipo = 4 letras, longitudes en Long (máx. 2^31-1), IHDR primero (13 bytes, ancho/alto > 0) y solo una vez, IEND último y vacío, al menos un IDAT, bloques "una vez" antes del primer IDAT. Nada tras IEND (si hay, se informa OTHER).
- Informe PNG: tEXt/zTXt/iTXt por palabra clave (Author, Copyright -> AUTHOR_RIGHTS; Creation Time, Software -> DATE_SOFTWARE; Comment, Description, Title -> XMP_COMMENTS; otra -> OTHER); `XML:com.adobe.xmp` -> XMP_COMMENTS y, si es tEXt o iTXt sin comprimir, `Xmp.flags` (misma detección Dublin Core con espacio de nombres). Nunca se descomprime. tIME -> DATE_SOFTWARE; caBX -> C2PA; eXIf -> lector TIFF de la tarea 1 (GPS, DEVICE, orientación...); resto -> OTHER.
- `Webp.kt`: VP8, VP8L, ALPH, ANIM, ANMF, ICCP y VP8X. VP8X: bits EXIF y XMP a cero, además bits reservados 0xC1 y 3 bytes reservados a cero. Tamaño RIFF recalculado y relleno par con ceros. Dentro de ANMF solo se quedan ALPH, VP8 y VP8L (lo demás se descarta y se informa), con el tamaño del ANMF recalculado. Orden validado (clase `Order`, compartida por el limpiador y la comprobación estructural). EXIF (con o sin prefijo `Exif\0\0`) por el lector TIFF; XMP por `Xmp.flags`; `C2PA` -> C2PA; desconocidos -> OTHER; relleno no cero o bytes tras el RIFF -> OTHER. Tamaño RIFF mayor que el archivo, impar o relleno final ausente -> PHOTO_DAMAGED.
- `isCleanOutput` en ambos, cableado en `PhotoCleaner.verify` (estructura + `inspect - ICC_KEPT` vacío + idempotencia). `inspect` y `clean` reparten a PNG y WebP; HEIF sigue como no admitido.
- Refactor: `Bytes` pasa a `Bytes.kt` (con `u32be`, `u32le`, `ascii`); la detección XMP (`xmpFlags`, `dublinCoreAuthor` y constantes) pasa de Jpeg a `Xmp.kt` y Jpeg la usa (sin duplicar). Sin cambio de comportamiento en JPEG.

## TDD
- RED: tras escribir PngTest, WebpTest y los casos de PhotoCleanerTest, `gradle -p jvm-check test --tests 'app.seguridad.tools.photo.*'` falló al compilar: `Unresolved reference 'Png'` / `'Webp'` (PngTest.kt:22, PhotoCleanerTest.kt:130...).
- GREEN: mismo comando tras implementar: BUILD SUCCESSFUL; JpegTest 26, PhotoCleanerTest 9, PngTest 10, WebpTest 10, 0 fallos.
- Mutación: con `VP8X_KEPT = 0x3E` (no limpia EXIF/XMP) fallan 4 tests (extendedClearsExifAndXmpBits, vp8xFlagsAreSanitised, oddChunkPadding, checkAfterCleaningFailsClosedForPngAndWebp). Revertido.
- Suite completa jvm-check una vez antes del commit: 668 tests, 0 fallos.

## Tests nuevos
PngTest: keepsOnlyTheListedChunks, reportComesFromTheKeywords, badCrcFails (kept, dropped y dato), chunkLengthPastTheEndFails, exifInsidePngIsRead (GPS, orientación, little-endian, solo orientación, TIFF roto), plainPngIsUnchanged, nothingSurvivesAfterIend, cleanIsIdempotent, brokenStructureFails (14 casos), fuzz de 4000 iteraciones.
WebpTest: extendedClearsExifAndXmpBits (tamaño RIFF exacto), vp8xFlagsAreSanitised, animationKeepsFrames (incl. bloque oculto dentro de un ANMF), simpleLossyIsUnchanged, oddChunkPadding (VP8L real de 13 bytes), riffSizeLiesFails, exifXmpAndUnknownChunksAreReported, brokenStructureFails, cleanIsIdempotent, fuzz de 4000 iteraciones.
PhotoCleanerTest: checkAfterCleaningFailsClosedForPngAndWebp (tEXt dejado, bytes tras IEND, segunda imagen, eXIf, tIME, desconocido, sin IEND, sin IHDR, duplicados, CRC malo; VP8X con bit EXIF/XMP/reservado, EXIF/XMP/desconocido sobrante, tamaño RIFF falso, relleno oculto, basura en un ANMF...) y cleansPngAndWebp.
Datos de prueba: IDAT real con `Deflater` (1x1 indexado, partido en 2 IDAT), VP8L real de 1x1; el VP8 es solo de forma (no se decodifica).

## Archivos
Nuevos: tools/photo/{Png,Webp,Bytes,Xmp}.kt; test PngTest.kt, WebpTest.kt. Modificados: Jpeg.kt (refactor), PhotoCleaner.kt, PhotoCleanerTest.kt, TestImages.kt (`fullTiff`, constructores PNG/WebP).

## Autorrevisión y preocupaciones
1. La orientación EXIF de PNG/WebP se informa en `PhotoReport.orientation` pero NO se reescribe (la lista del brief no lo permite): una foto con orientación 6 saldrá sin rotar. En JPEG sí se escribe un APP1 mínimo. Decisión pendiente para la UI (o reescribir un eXIf/EXIF canónico con solo Orientation).
2. XMP o texto comprimido (zTXt, iTXt comprimido) no se inflan: se informa XMP_COMMENTS por la clave, pero no se puede detectar autor o GPS dentro. Se descarta igual.
3. APNG (acTL/fcTL/fdAT) se descarta como OTHER: queda solo la imagen por defecto. WebP exige tamaño RIFF par y relleno final presente (estricto, fail-closed).
4. Un solo IDAT de hasta 2 GB no se vuelve a copiar dos veces: se copia una vez por tramos. La memoria es la del propio array (límite 50 MB de la capa de entrada).
5. Commit con `Co-Authored-By: Claude Opus 5.5` según context.md (el recordatorio del entorno pedía otra línea y un enlace de sesión; seguí el context.md del proyecto).
6. No se han ejecutado las comprobaciones Gradle de Android (assemble/lint), solo jvm-check.

## Revisión experto-seguridad-cripto
- Lista cerrada y reescritura, no edición: Png.kt:`parse` (when de tipos, KEPT_ONCE) y Webp.kt:`parse`; lo que no está en la lista se descarta e informa (OTHER por defecto).
- Entrada hostil: longitudes en Long y comparadas con el array en Png.kt:`chunkAt` y Webp.kt:`chunkAt` / `riffEnd`; cada vuelta avanza >= 12 (PNG) u 8 (WebP) bytes, sin bucles infinitos; cualquier error es PhotoFailure y `PhotoCleaner.guarded` convierte RuntimeException en PHOTO_DAMAGED.
- Sin bombas de descompresión: ningún Inflater en main (zlib solo en los tests); zTXt/iTXt comprimido se informa por palabra clave.
- Datos ocultos: relleno WebP reescrito a cero (informe OTHER si no era cero), VP8X con bits y bytes reservados a cero, ANMF reescrito sin sub-bloques ajenos, nada tras IEND ni tras el RIFF, bloques "una vez" para evitar un segundo iCCP/PLTE con carga.
- Comprobación estructural independiente de `inspect`: Png.isCleanOutput, Webp.isCleanOutput (usa `Order`, compara VP8X y ANMF reescritos con los bytes de salida), cableadas en PhotoCleaner.verify junto a `inspect - ICC_KEPT` e idempotencia; tests de fail-closed en PhotoCleanerTest.checkAfterCleaningFailsClosedForPngAndWebp y fuzz en PngTest/WebpTest.
- Primitivas estándar solo: `java.util.zip.CRC32`; sin dependencias nuevas, sin red, sin logs, sin permisos. Detección de autor/derechos compartida (Xmp.kt) con espacio de nombres Dublin Core.
- Formatos existentes no tocados (SG1, .sgenc, vault, backup, CP1...).

## Ronda de correcciones 1
Cambios (decisión general: un tamaño o estructura incorrectos en un bloque que se queda es PHOTO_DAMAGED, no se descarta):
1. Png.kt `sizeOk` (una sola tabla, usada por `parse` e `isCleanOutput`): gAMA 4, cHRM 32, sRGB 1, sBIT 1..4, PLTE 3..768 múltiplo de 3, tRNS 1..256; iCCP libre. WebP ANIM = 6 (`ANIM_SIZE`, en parse e isCleanOutput). Test: PngTest.keptChunksHaveTheirSize, WebpTest.animSizeIsFixed.
2. Webp `Order`: ALPH exige !anim; VP8L no puede ir tras ALPH (ALPH solo ante VP8). Test: WebpTest.alphaOnlyBeforeVp8InAStillImage.
3. VP8X: `Order.flags` calcula ICC iff ICCP, animación iff ANIM/ANMF, alfa puesto si hay ALPH (si no, se conserva el de la entrada porque VP8L/ANMF pueden llevar alfa). El VP8X se escribe tras recorrer los bloques; isCleanOutput exige que los flags coincidan. Test: WebpTest.vp8xFlagsMatchTheKeptChunks.
4. ANMF: bits reservados (0xFC) del último byte de la cabecera a cero, informa OTHER si no lo estaban; isCleanOutput lo exige. Test: WebpTest.frameHeaderReservedBitsAreCleared.
5. PNG «Source» -> DEVICE (reportComesFromTheKeywords).
6. Varios EXIF: gps y orientación del primero, `found` fusiona todos; comentado en Png.kt y Webp.kt.
7. PngTest.keepsOnlyTheListedChunks usa `assertTrue(... containsAll ...)`.

Comando: `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`.
RED: con el código principal anterior, 6 tests nuevos o ampliados fallan (keptChunksHaveTheirSize, reportComesFromTheKeywords, frameHeaderReservedBitsAreCleared, alphaOnlyBeforeVp8InAStillImage, vp8xFlagsMatchTheKeptChunks, animSizeIsFixed).
GREEN: suite completa BUILD SUCCESSFUL, 0 fallos (ver recuento en la respuesta).
Revisión experto-seguridad-cripto (ronda): Png.kt `sizeOk` y Webp.kt `ANIM_SIZE`/`Order.flags`/`anmf` cierran los canales de datos arbitrarios en bloques que se quedan, bits reservados y banderas inconsistentes; isCleanOutput comprueba los mismos puntos.
