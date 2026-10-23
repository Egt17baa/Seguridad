#!/usr/bin/env python3
"""Análisis de cifrados clásicos: triaje, César y Vigenère.

Uso:
  analisis_clasico.py analizar "TEXTO" [--idioma es|en]
  analisis_clasico.py cesar    "TEXTO" [--idioma es|en] [--top N]
  analisis_clasico.py vigenere "TEXTO" [--idioma es|en] [--max-long N]
  Cualquiera de ellos admite --fichero RUTA en lugar del texto.

Solo usa la biblioteca estándar. Normaliza a A-Z: quita tildes, Ñ pasa a N.
"""
import argparse
import base64
import binascii
import math
import re
import string
import sys
import unicodedata
from collections import Counter

ALFABETO = string.ascii_uppercase

# Frecuencias en %, alfabeto de 26 letras (la Ñ se suma a la N).
FRECUENCIAS = {
    "es": dict(zip(ALFABETO, [
        12.53, 1.42, 4.68, 5.86, 13.68, 0.69, 1.01, 0.70, 6.25, 0.44, 0.02, 4.97, 3.15,
        6.98, 8.68, 2.51, 0.88, 6.87, 7.98, 4.63, 3.93, 0.90, 0.01, 0.22, 0.90, 0.52])),
    "en": dict(zip(ALFABETO, [
        8.17, 1.49, 2.78, 4.25, 12.70, 2.23, 2.02, 6.09, 6.97, 0.15, 0.77, 4.03, 2.41,
        6.75, 7.51, 1.93, 0.10, 5.99, 6.33, 9.06, 2.76, 0.98, 2.36, 0.15, 1.97, 0.07])),
}
IC_IDIOMA = {"es": 0.0775, "en": 0.0667}
IC_ALEATORIO = 1 / 26


def normalizar(texto):
    sin_tildes = unicodedata.normalize("NFD", texto.upper())
    sin_tildes = "".join(c for c in sin_tildes if unicodedata.category(c) != "Mn")
    return re.sub(r"[^A-Z]", "", sin_tildes)


def indice_coincidencia(letras):
    n = len(letras)
    if n < 2:
        return 0.0
    return sum(f * (f - 1) for f in Counter(letras).values()) / (n * (n - 1))


def chi_cuadrado(letras, idioma):
    n = len(letras)
    if n == 0:
        return float("inf")
    cuenta = Counter(letras)
    esperado = FRECUENCIAS[idioma]
    return sum((cuenta.get(c, 0) - n * esperado[c] / 100) ** 2 / (n * esperado[c] / 100)
               for c in ALFABETO)


def correlacion(letras, idioma):
    """Producto escalar de las frecuencias observadas con las del idioma.

    Más robusto que χ² con muestras cortas: una letra rara no dispara la puntuación.
    """
    n = len(letras) or 1
    esperado = FRECUENCIAS[idioma]
    return sum(f / n * esperado[c] for c, f in Counter(letras).items())


def mejor_desplazamiento(letras, idioma):
    return max(range(26), key=lambda k: correlacion(desplazar(letras, k), idioma))


def desplazar(letras, k):
    return "".join(ALFABETO[(ALFABETO.index(c) - k) % 26] for c in letras)


def descifrar_vigenere(letras, clave):
    return "".join(ALFABETO[(ALFABETO.index(c) - ALFABETO.index(clave[i % len(clave)])) % 26]
                   for i, c in enumerate(letras))


def kasiski(letras, min_long=3, max_long=5):
    """Distancias entre trigramas (y más largos) repetidos, y recuento de divisores."""
    distancias = []
    for L in range(min_long, max_long + 1):
        posiciones = {}
        for i in range(len(letras) - L + 1):
            posiciones.setdefault(letras[i:i + L], []).append(i)
        for pos in posiciones.values():
            distancias += [b - a for a, b in zip(pos, pos[1:])]
    divisores = Counter(d for dist in distancias for d in range(2, 21) if dist % d == 0)
    return distancias, divisores


def detectar_codificaciones(bruto):
    pistas = []
    limpio = bruto.strip()
    if re.fullmatch(r"(?:[0-9a-fA-F]{2})+", limpio):
        pistas.append("hex")
    if re.fullmatch(r"[A-Za-z0-9+/]+={0,2}", limpio) and len(limpio) % 4 == 0 and len(limpio) >= 8:
        try:
            dec = base64.b64decode(limpio, validate=True)
            imprimible = sum(32 <= b < 127 or b in (9, 10, 13) for b in dec) / max(len(dec), 1)
            pistas.append(f"base64 ({imprimible:.0%} imprimible al descodificar)")
        except (binascii.Error, ValueError):
            pass
    if re.fullmatch(r"[A-Z2-7]+=*", limpio) and len(limpio) % 8 == 0:
        pistas.append("base32")
    if re.fullmatch(r"[01\s]+", limpio):
        pistas.append("binario")
    if re.fullmatch(r"[.\-/\s]+", limpio):
        pistas.append("morse")
    if re.fullmatch(r"[1-5\s]+", limpio) and len(limpio.replace(" ", "")) % 2 == 0:
        pistas.append("Polibio (pares de dígitos del 1 al 5)")
    if set(normalizar(limpio)) <= set("ADFGVX") and len(normalizar(limpio)) > 10:
        pistas.append("ADFGVX / ADFGX")
    return pistas


def cmd_analizar(bruto, idioma, **_):
    letras = normalizar(bruto)
    print(f"Longitud bruta: {len(bruto)} · letras A-Z: {len(letras)}")
    pistas = detectar_codificaciones(bruto)
    if pistas:
        print("Posibles codificaciones:", ", ".join(pistas))
        print("Si alguna encaja, descodifica primero: la estadística de abajo no tiene sentido sobre una codificación.")
    if len(letras) < 20:
        print("Muy pocas letras para estadística fiable.")
        return
    ic = indice_coincidencia(letras)
    print(f"IC: {ic:.4f} (idioma {idioma} ≈ {IC_IDIOMA[idioma]:.4f}, aleatorio ≈ {IC_ALEATORIO:.4f})")
    chi = chi_cuadrado(letras, idioma)
    print(f"χ² frente a {idioma} sin transformar: {chi:.1f} (cuanto menor, más se parece al idioma)")
    if ic > (IC_IDIOMA[idioma] + IC_ALEATORIO) / 2 + 0.008:
        if chi < 150:
            print("Diagnóstico: IC y frecuencias del idioma → probablemente transposición (o texto en claro).")
        else:
            print("Diagnóstico: IC del idioma con frecuencias permutadas → sustitución monoalfabética (prueba `cesar`).")
    else:
        print("Diagnóstico: IC bajo → polialfabético (prueba `vigenere`), Enigma o cifrado moderno.")
    cuenta = Counter(letras)
    print("Frecuencias (%):", " ".join(f"{c}:{100 * f / len(letras):.1f}" for c, f in cuenta.most_common()))
    bigramas = Counter(letras[i:i + 2] for i in range(len(letras) - 1))
    print("Bigramas más comunes:", " ".join(f"{b}:{f}" for b, f in bigramas.most_common(10)))
    _, divisores = kasiski(letras)
    if divisores:
        print("Kasiski, divisores de distancias repetidas:",
              " ".join(f"{d}:{f}" for d, f in divisores.most_common(8)))


def cmd_cesar(bruto, idioma, top=5, **_):
    letras = normalizar(bruto)
    candidatos = sorted(range(26), key=lambda k: -correlacion(desplazar(letras, k), idioma))
    for k in candidatos[:top]:
        claro = desplazar(letras, k)
        print(f"desplazamiento {k:2d} (clave {ALFABETO[k]}) "
              f"correlación={correlacion(claro, idioma):5.2f} χ²={chi_cuadrado(claro, idioma):8.1f}  {claro[:80]}")


def cmd_vigenere(bruto, idioma, max_long=20, **_):
    letras = normalizar(bruto)
    print("IC medio por columnas según la longitud de la clave:")
    resultados = []
    for L in range(1, min(max_long, len(letras) // 2) + 1):
        ic_medio = sum(indice_coincidencia(letras[i::L]) for i in range(L)) / L
        resultados.append((L, ic_medio))
        print(f"  {L:2d}: {ic_medio:.4f} {'█' * int(ic_medio * 400)}")
    _, divisores = kasiski(letras)
    if divisores:
        print("Kasiski:", " ".join(f"{d}:{f}" for d, f in divisores.most_common(6)))
    # La longitud más pequeña cuyo IC se acerca al del idioma (los múltiplos también dan picos).
    umbral = IC_IDIOMA[idioma] * 0.88
    candidatas = [L for L, ic in resultados if ic >= umbral] or [max(resultados, key=lambda r: r[1])[0]]
    for L in candidatas[:3]:
        clave = "".join(ALFABETO[mejor_desplazamiento(letras[i::L], idioma)] for i in range(L))
        claro = descifrar_vigenere(letras, clave)
        print(f"\nLongitud {L} → clave «{clave}» (χ² total {chi_cuadrado(claro, idioma):.1f})")
        print(claro[:300])
    print("\nRevisa la clave a ojo: si casi es una palabra, corrige la letra dudosa y vuelve a descifrar.")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("orden", choices=["analizar", "cesar", "vigenere"])
    p.add_argument("texto", nargs="?")
    p.add_argument("--fichero")
    p.add_argument("--idioma", choices=sorted(FRECUENCIAS), default="es")
    p.add_argument("--top", type=int, default=5)
    p.add_argument("--max-long", type=int, default=20)
    a = p.parse_args()
    if a.fichero:
        with open(a.fichero, encoding="utf-8") as f:
            bruto = f.read()
    elif a.texto:
        bruto = a.texto
    else:
        bruto = sys.stdin.read()
    {"analizar": cmd_analizar, "cesar": cmd_cesar, "vigenere": cmd_vigenere}[a.orden](
        bruto, a.idioma, top=a.top, max_long=a.max_long)


if __name__ == "__main__":
    main()
