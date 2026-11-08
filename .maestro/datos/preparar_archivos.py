#!/usr/bin/env python3
"""Prepara los archivos de prueba de los flujos de .maestro/semiautomaticas/ y los copia al dispositivo.

Todos son sintéticos: o los escribe este script, o son copias de los archivos de prueba del repositorio
(app/src/test/resources/), que tienen datos inventados. Ninguno es una exportación real ni lleva un secreto
de nadie (decisión del dueño (e)).

Uso, desde la raíz del repositorio y con un solo dispositivo conectado (o ANDROID_SERIAL puesto):
    python3 .maestro/datos/preparar_archivos.py            # los escribe y los copia a /sdcard/Download
    python3 .maestro/datos/preparar_archivos.py --sin-adb  # solo los escribe en .maestro/datos/generados/

Solo usa la biblioteca estándar de Python y adb.
"""
import argparse
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent.parent
SALIDA = AQUI / "generados"
RECURSOS = RAIZ / "app" / "src" / "test" / "resources"
DESTINO = "/sdcard/Download"


def escribir(nombre: str, datos: bytes) -> Path:
    ruta = SALIDA / nombre
    ruta.write_bytes(datos)
    return ruta


def copiar(origen: Path, nombre: str) -> Path:
    ruta = SALIDA / nombre
    shutil.copyfile(origen, ruta)
    return ruta


def preparar() -> list:
    SALIDA.mkdir(parents=True, exist_ok=True)
    archivos = []

    # P-15, pestaña Archivo: «abc» sin salto de línea (SHA-256 ba7816bf…).
    archivos.append(escribir("clavero-abc.txt", b"abc"))

    # P-4: una frase cualquiera, sin comas (ni CSV, ni JSON, ni enlaces): «Clavero no reconoce este archivo.».
    archivos.append(escribir("clavero-frase.txt", "Esto es una frase cualquiera para la prueba de importar\n".encode("utf-8")))

    # P-4: algo que no es texto (cabecera JPEG y bytes que no son UTF-8): «El archivo está dañado o no es texto.».
    foto = bytes([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]) + b"JFIF\x00" + bytes(range(256)) * 16 + bytes([0xFF, 0xD9])
    archivos.append(escribir("clavero-foto.jpg", foto))

    # P-4: una página HTML: se reconoce antes de leerla y nunca se muestra.
    html = "<!DOCTYPE html>\n<html><head><title>Prueba</title></head><body><p>Contenido de la pagina de prueba</p></body></html>\n"
    archivos.append(escribir("clavero-pagina.html", html.encode("utf-8")))

    # P-4: un .zip (empieza por PK\x03\x04).
    zip_ruta = SALIDA / "clavero-adjuntos.zip"
    with zipfile.ZipFile(zip_ruta, "w") as z:
        z.writestr("prueba.txt", "contenido de prueba\n")
    archivos.append(zip_ruta)

    # P-4: un JSON con «encrypted»: true y sin «passwordProtected» (la forma de una exportación cifrada con la
    # cuenta, según la documentación pública). Los valores son relleno: no hay nada que descifrar.
    restringida = {"encrypted": True, "encKeyValidation_DO_NOT_EDIT": "2.AAAA|AAAA|AAAA", "data": "2.AAAA|AAAA|AAAA"}
    archivos.append(escribir("clavero-cuenta-restringida.json", json.dumps(restringida).encode("utf-8")))

    # P-4: CSV con la cabecera de la caja fuerte personal (la de app/src/test/resources/import/csv/bitwarden.csv):
    # un acceso con dos URI en login_uri (la segunda va a las notas) y otro cuya URL lleva una coma.
    csv_uri = (
        "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n"
        ',,login,Dos webs,,,0,"https://uno.example.com/,https://dos.example.com/entrar",marta,Pw-prueba-uri-1,\n'
        ',,login,Con coma,,,0,"https://ejemplo.es/?q=1,2",luis,Pw-prueba-uri-2,\n'
    )
    archivos.append(escribir("clavero-gestor-uri.csv", csv_uri.encode("utf-8")))

    # P-12: unos 3 MB de listas pequeñas, que empieza por «{» (el comando del README de la 1.4.0).
    listas = '{"a":[' + ",".join(["[1]"] * 700000) + "]}\n"
    archivos.append(escribir("clavero-listas.json", listas.encode("ascii")))

    # P-12: CSV de navegador (name,url,username,password,note) con un título que lleva U+202E (cambio de
    # dirección): «Banco», U+202E y «gpj.exe». Se escribe aquí, y no en el repositorio, para no guardar ese
    # carácter invisible en un archivo del proyecto.
    rlo = "name,url,username,password,note\nBanco‮gpj.exe,https://banco.example.com/,ana,Clave-de-prueba-RLO-1,\n"
    archivos.append(escribir("clavero-rlo.csv", rlo.encode("utf-8")))

    # P-7, P-9 y P-10: el CSV de navegador del repositorio (3 accesos inventados).
    archivos.append(copiar(RECURSOS / "import" / "csv" / "chromium.csv", "clavero-navegador.csv"))

    # P-8: una exportación de 2FA cifrada, hecha con el generador independiente de la tarea 10
    # (contraseña «prueba-clavero-1.4»; ver app/src/test/resources/import/README.txt).
    archivos.append(copiar(RECURSOS / "import" / "aegis" / "aegis-cifrado.json", "clavero-2fa-cifrado.json"))

    # P-14: la copia .sgbak real de la 1.1.1 (maestra «maestra-de-prueba», 2 entradas y 1 adjunto; ver
    # app/src/test/resources/seguridad/README.txt).
    archivos.append(copiar(RECURSOS / "seguridad" / "backup-1.1.sgbak", "clavero-copia-1.1.sgbak"))
    return archivos


def adb(*args: str) -> None:
    subprocess.run(["adb", *args], check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sin-adb", action="store_true", help="solo escribe los archivos, no los copia")
    opciones = parser.parse_args()
    archivos = preparar()
    for ruta in archivos:
        print(f"preparado: {ruta.relative_to(RAIZ)} ({ruta.stat().st_size} bytes)")
    if opciones.sin_adb:
        return 0
    for ruta in archivos:
        adb("push", str(ruta), f"{DESTINO}/{ruta.name}")
        # Para que el selector de archivos los vea en Descargas. Si tu Android no hace caso de este aviso,
        # abre una vez la app Archivos o reinicia el emulador.
        subprocess.run(
            ["adb", "shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
             "-d", f"file://{DESTINO}/{ruta.name}"],
            check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    print(f"Copiados {len(archivos)} archivos a {DESTINO}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
