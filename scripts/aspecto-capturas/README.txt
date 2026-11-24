Aproximaciones del Aspecto (Clavero 2.1, tarea 5)

Dibuja, desde los colores y opacidades exactos que calcula la app, una aproximación de cada estilo del aspecto
(«Sólido», «Degradado», «Cristal», «Vidrio» y «Transparente») en la paleta «Noche», en claro y en oscuro: una barra,
dos tarjetas y un diálogo con datos inventados, y en «Transparente» un fondo de pantalla inventado, liso o de rayas.
No son capturas: no hay emulador en la nube y FLAG_SECURE bloquea las capturas en el móvil. Lo real se ve con el APK.

1. Los colores, desde la raíz del repositorio (escribe aspecto-colores.json en la carpeta que se le dé; sin la
   variable, el test no escribe nada):

   ASPECTO_CAPTURAS=/una/carpeta/temporal ./gradlew :app:testDebugUnitTest --tests 'app.seguridad.ui.theme.AspectoCapturesTest' --no-daemon --max-workers=1

2. Las imágenes:

   python3 -I scripts/aspecto-capturas/dibujar.py /una/carpeta/temporal /carpeta/de/salida

Necesita Python 3 y Pillow (licencia HPND/MIT-CMU, permisiva), y la fuente DejaVu Sans en
/usr/share/fonts/truetype/dejavu/ (licencia de Bitstream Vera, permisiva; no se copia al repositorio).
Pillow no es una dependencia del proyecto ni va en el APK: es solo una herramienta del desarrollador.
