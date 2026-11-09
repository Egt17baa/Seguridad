# Flujos de Maestro de Clavero 1.4.0

Pruebas de interfaz para las «Pruebas de la 1.4.0» del README del repositorio (P-1 a P-27; las siete últimas, de los ataques, son manuales), según la decisión del dueño (g) del 8-11-2026: se ejecutan en tu ordenador con el plugin «Android qa libre» (Mobile MCP y Maestro), en el emulador de Android Studio o con el móvil por USB. En el contenedor de la nube no hay emulador, así que estos flujos se han escrito a partir de los textos del código y **no se han ejecutado todavía**: la primera vez, alguno puede necesitar un ajuste (ver «Lo que puede fallar la primera vez»).

## Carpetas

| Carpeta | Qué hay | ¿Borra datos? |
|---|---|---|
| `.maestro/` (los `.yaml` de la raíz) | Flujos automáticos que no tocan la bóveda: herramientas nuevas, giro, «Comprobar una copia» (la pantalla), versión y licencias. | **No.** Se pueden usar en tu móvil. |
| `.maestro/borra-datos/` | Flujos automáticos que empiezan con `clearState`: crean una bóveda de prueba con una maestra de prueba escrita en el flujo. | **Sí, todos.** |
| `.maestro/semiautomaticas/` | Flujos que necesitan antes los archivos de prueba en el dispositivo (o una variable). | Unos sí y otros no: lo dice la primera línea de cada uno y la tabla de abajo. |
| `.maestro/comun/` | Subflujos que usan los demás (abrir la app, crear la bóveda de prueba, el selector de archivos…). No se ejecutan solos. | `inicio-desde-cero.yaml` sí. |
| `.maestro/datos/` | `preparar_archivos.py`, que escribe los archivos de prueba y los copia a Descargas. Lo que escribe va a `datos/generados/`, que git no guarda. | No. |

> **Aviso.** Los flujos de `borra-datos/` y los semiautomáticos marcados `borra-datos` hacen `clearState`: borran la bóveda, los ajustes y la huella de Clavero en ese dispositivo. Úsalos solo en el emulador o en un móvil sin tu bóveda real. Si los usas en tu móvil, exporta antes una copia `.sgbak` y guárdala fuera.

Todos los datos son de prueba: frases y valores públicos (la frase BIP-39 de entropía cero, el vector n.º 4 de SLIP-0039, los ejemplos de César, Vigenère, Enigma y HMAC) o archivos sintéticos del repositorio. La maestra de las bóvedas de prueba es `nube-ladera-pino-ancla-trigo-prueba`, escrita en cada flujo. Ninguno usa un secreto real ni una exportación real (decisión (e)).

## Requisitos

- Maestro CLI en local (nunca `maestro cloud`) y `adb`. Para `giro-conserva-estado.yaml`, una versión de Maestro con el comando `setOrientation`.
- Clavero instalado en el dispositivo (`appId` `io.github.egt17baa.seguridad`).
- El teclado en pantalla tiene que salir: en el emulador, «Usar teclado en pantalla» (o «Show virtual keyboard») activado con el teclado físico. En Android, `hideKeyboard` de Maestro pulsa Atrás, y sin teclado en pantalla esa pulsación cierra la herramienta.
- Para las semiautomáticas: Python 3 (solo la biblioteca estándar).
- El selector de archivos del sistema en español o en inglés (`comun/elegir-en-descargas.yaml` y `comun/guardar-en-selector.yaml` buscan «Mostrar raíces»/«Show roots», «Descargas»/«Downloads» y «Guardar»/«Save»).

Clavero pone `FLAG_SECURE` en toda la app, así que las capturas de pantalla salen en negro: los flujos no hacen capturas y comprueban todo por los textos de la pantalla (la app no usa `testTag`, así que no hay ids). Maestro solo escribe caracteres ASCII en Android: por eso los flujos no escriben «ñ» ni tildes.

## Cómo ejecutarlos

Desde la raíz del repositorio, con un solo dispositivo conectado:

```bash
# 1. Los que no borran datos (es lo que ejecuta la skill android-tests del plugin, `maestro test .maestro/`):
maestro test .maestro/

# 2. Los automáticos que borran los datos de Clavero (solo emulador o móvil de pruebas):
maestro test .maestro/borra-datos/

# 3. Los semiautomáticos: primero los archivos, después los flujos.
python3 .maestro/datos/preparar_archivos.py
maestro test --exclude-tags necesita-variable .maestro/semiautomaticas/
maestro test -e CONTRASENA_VECTOR=<la contraseña> .maestro/semiautomaticas/p17-recomponer-vector-oficial.yaml

# Un flujo suelto:
maestro test .maestro/p15-laboratorio-hashes.yaml
# Con informe JUnit:
maestro test --format junit --output informe-maestro.xml .maestro/
```

Con el plugin: la skill `android-tests` (`/android-qa-libre:android-tests`) compila e instala la versión debug y ejecuta `maestro test .maestro/`, es decir, solo la carpeta raíz. Para las otras dos carpetas, pídele que ejecute `maestro test .maestro/borra-datos/` o la carpeta semiautomática, o usa los comandos de arriba.

- `CONTRASENA_VECTOR` es la contraseña fija de los vectores oficiales de SLIP-0039, la de `VECTOR_PASSPHRASE` en `app/src/test/java/app/seguridad/tools/slip39/Slip39Test.kt:25`. No se escribe en el flujo.
- `semiautomaticas/p04-lo-que-no-se-lee.yaml` borra del dispositivo `clavero-gestor-uri.csv` (prueba «Quitar el archivo exportado»): vuelve a ejecutar `preparar_archivos.py` antes de repetirlo.
- Si el selector no enseña los archivos en Descargas después del script, abre una vez la app Archivos o reinicia el emulador: el script avisa al escáner de medios, pero algunas versiones de Android no le hacen caso.
- **P-19 pide el APK firmado.** La skill `android-tests` instala el debug, que no pasa por R8. Para P-19, instala `Clavero.apk` de la ejecución de *Build APK* y ejecuta las tres carpetas con él. Pasar del debug al firmado (o al revés) obliga a desinstalar, porque las firmas son distintas, y desinstalar borra la bóveda de ese dispositivo.

## Qué prueba automatiza cada flujo

| Prueba | Flujo | Qué queda manual y por qué |
|---|---|---|
| P-1, cada formato real | Manual | Necesita las exportaciones reales de cada app (decisión (e)). |
| P-2, exportaciones cifradas reales | En parte: `semiautomaticas/p08-volver-atras-borra.yaml`, con el archivo sintético cifrado (contraseña equivocada, el texto fijo, volver a escribirla, nada se reintenta solo) | Las exportaciones reales y el tiempo de PBKDF2 con 600 000 iteraciones (decisión (e)). |
| P-3, «Pendiente de confirmar» | Manual | Solo lo confirma una exportación real (`docs/superpowers/handoff/sdd-1.4.0/task-10-report.md`). |
| P-4, lo que no se lee | `semiautomaticas/p04-lo-que-no-se-lee.yaml` (borra datos): foto, frase, HTML, `.zip`, «Cuenta restringida», dos URI y la URL con coma | Las mismas exportaciones hechas por las apps reales van con P-1. |
| P-5, códigos que se exportan como QR | `borra-datos/p05-ayuda-codigos-qr.yaml`: la ayuda de la primera pantalla y los botones que nombra | Escanear los QR de otro móvil con la cámara y comparar los códigos. |
| P-6, la copia de antes de importar con copia automática | Manual | Pide elegir la carpeta con el selector de carpetas del sistema, mirar sus archivos, hacer más de 3 cambios y abrir la copia con la maestra de antes. |
| P-7, la copia de antes de importar con el diálogo | En parte: `semiautomaticas/p07-p09-p10-copia-y-duplicados.yaml` y `p04` (cancelar el diálogo no importa nada; guardada, el resultado dice dónde y que no se borra) | La carpeta de la copia automática con el permiso revocado en los ajustes del sistema. |
| P-8, bloquear y salir en cada paso | En parte: `semiautomaticas/p08-volver-atras-borra.yaml` (atrás con la contraseña escrita y con la vista previa) | Inicio y volver pasado el bloqueo (esperar 1 minuto o más), bloquear justo mientras se hace la copia (depende del momento exacto; al desbloquear, o la bóveda está como antes o la importación está entera, regla R37) y «Cancelar» con un archivo de 15 MB. |
| P-9, duplicados | En parte: `semiautomaticas/p07-p09-p10-copia-y-duplicados.yaml` («Saltar», «Conservar los dos» y «Reemplazar» para todos, volver a tocar la opción elegida, el mismo archivo dos veces) | La opción de una sola entrada, la contraseña anterior en el historial después de «Reemplazar» y el dominio de la web en la fila de un acceso repetido («ana · banco-acceso.example», con el esquema delante cuando no es https, «ana · http://banco.es», y nunca el usuario ni la contraseña de la web; el flujo no lo comprueba). |
| P-10, quitar el archivo exportado | En parte: `p04` (desde Descargas se borra y lo dice) y `p07-p09-p10` («No, déjalo») | Google Drive y su papelera. |
| P-11, cambiar la maestra después de importar | Manual | Necesita la huella y lo de P-6. |
| P-12, archivos hechos a mala fe | `semiautomaticas/p12-archivos-hostiles.yaml` (borra datos): el JSON de 3 MB, el título con U+202E en la vista previa, la lista y el detalle, y la URL que no se abre | Nada más. |
| P-13, TalkBack al importar | Manual | TalkBack (Maestro no lo maneja). |
| P-14, «Comprobar una copia» | `p14-comprobar-una-copia-pantalla.yaml` y `semiautomaticas/p14-comprobar-una-copia-archivo.yaml` (la copia real de la 1.1.1: contraseña equivocada, resultado, un archivo que no es copia) | «Cancelar» mientras deriva Argon2id y volver a empezar en seguida (depende del momento exacto), salir a mitad y `adb shell run-as … ls cache/comprobar`. |
| P-15, Laboratorio de hashes | `p15-laboratorio-hashes.yaml`, `semiautomaticas/p15-laboratorio-hashes-archivo.yaml`, `borra-datos/p15-p16-p17-bloquear-borra.yaml` y `giro-conserva-estado.yaml` | El archivo de varios GB con su progreso y «Cancelar», y salir más tiempo que el bloqueo. |
| P-16, Laboratorio clásico | `p16-laboratorio-clasico.yaml` (con Operation Barbarossa), `borra-datos/p15-p16-p17-bloquear-borra.yaml` y `giro-conserva-estado.yaml` | Pegar desde el portapapeles un texto largo en la clave (el flujo escribe 201 caracteres, que da la misma nota) y salir más tiempo que el bloqueo. |
| P-17, Repartir un secreto | `p17-repartir-un-secreto.yaml` (12 palabras, 2 de 3, una parte sola no basta), `semiautomaticas/p17-recomponer-vector-oficial.yaml`, `borra-datos/p15-p16-p17-bloquear-borra.yaml` y `giro-conserva-estado.yaml` | La frase de 24 palabras del Generador, el vector n.º 42 (exponente 3) con su tiempo y «Cancelar», salir más tiempo que el bloqueo y TalkBack. Un exponente de 4 a 8 no se puede repartir desde la app (los repartos nuevos usan el 1, `Slip39.kt:41`) ni está en los vectores oficiales. |
| P-18, la huella | Manual | Pide la huella (y girar con el diálogo de la huella abierto). |
| P-19, el APK minificado | En parte: `p19-recorrer-herramientas.yaml`, `borra-datos/p19-crear-boveda-maestra-nueva.yaml` (también la regla de la maestra nueva: «corta», «P4ssw0rd2024!» y «Password1!») y todas las carpetas con el APK firmado | «ｐａｓｓｗｏｒｄ12345» en ancho completo (Maestro solo escribe ASCII), importar una copia, el escáner QR, la huella, el aviso de abajo (*snackbar*), compartir un archivo, «¿Filtrada?» con red, «Tachar» y «Limpiar foto» con una imagen. |
| P-20, versión y licencias | `p20-version-y-licencias.yaml` | Nada. |
| P-21, un enlace con `\` en el navegador | Manual | Es en Chrome y en Firefox, fuera de Clavero. |
| P-22, capturas en importar | Manual | Se mira a ojo que la captura y Recientes salen en negro; los flujos no hacen capturas. |
| P-23, toques ocultos | Manual | Necesita otra app que pinte encima. |
| P-24, el portapapeles en Android 13 o posterior | Manual | Es lo que enseña el teclado, fuera de Clavero. |
| P-25, la muerte del proceso a mitad | Manual | `adb shell am force-stop` en el momento justo y `adb shell run-as … ls`. |
| P-26, las copias del sistema | Manual | `adb backup` y la transferencia a otro móvil. |
| P-27, lo que no se ve y los enlaces raros | Manual | Tres CSV de prueba de una fila (U+2800, U+2065 y U+FFF0 en el título) y uno con «۝», un QR de Wi-Fi con «Casa» + U+2800, y pegar `ht<TAB>tp:@banco.es` y las variantes con NBSP, U+3000, U+200B, un control o «：» en la barra de Chrome y del navegador del fabricante (si alguna abre `http://banco.es`, es RT-15 para la 1.4.1). |

Los nombres de otras apps de esta tabla son marcas de sus titulares. Clavero no tiene relación con ellos.

`giro-conserva-estado.yaml` comprueba además que girar la pantalla conserva lo escrito y el resultado en las tres herramientas nuevas.

## Lo que puede fallar la primera vez

Los flujos no se han podido ejecutar en el contenedor. Lo más probable que haya que ajustar:

- **Las etiquetas de los campos.** Los flujos tocan un campo de texto por su etiqueta («Texto», «Clave», «Contraseña del archivo»…) y, si ya tiene algo, por su contenido. Si una versión de Compose o de Maestro no expone la etiqueta, toca el campo con `point` o por su contenido.
- **Los índices de los rotores de Enigma** (`p16`): `II` con `index: 0`, `IV` con `index: 1` y `V` con `index: 2` suponen que se ven las tres filas a la vez.
- **El selector de archivos y el de guardar** (`comun/`): dependen del idioma y de la versión de Android.
- **El error de «Comprobar una copia» con la contraseña equivocada** (`semiautomaticas/p14`): el flujo espera un texto que empiece por «Contraseña incorrecta», que es el del error de cifrado por defecto (`CryptoFailure`); si la app da otro, cámbialo.
- **El campo de contraseña ya escrito** (`semiautomaticas/p17-recomponer-vector-oficial.yaml`): para borrarlo, el flujo lo busca por su etiqueta o por los puntos `•`.
- **`pressKey: Enter`** en el campo de las partes (`p17`): tiene que hacer un salto de línea. Si tu teclado lo trata como «Hecho», escribe las partes a mano o usa un grupo de 1 parte.
