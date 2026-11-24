"""Dibuja una aproximación de cada estilo del Aspecto de Clavero 2.1 desde los colores exactos que escribe
AspectoCapturesTest (aspecto-colores.json). Barra, dos tarjetas y un diálogo, con datos inventados.
En «Transparente», sobre dos fondos de pantalla inventados: uno liso y otro de rayas.
Uso: python3 -I scripts/aspecto-capturas/dibujar.py <carpeta con aspecto-colores.json> <carpeta de salida>"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 360, 720
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def rgba(h, a=1.0):
    v = int(h[1:], 16)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, round(255 * a * ((v >> 24) & 255) / 255))


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)


def vgradient(size, top, bottom, alpha=1.0):
    w, h = size
    img = Image.new("RGBA", size)
    t, b = rgba(top), rgba(bottom)
    d = ImageDraw.Draw(img)
    for y in range(h):
        k = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(t[i] + (b[i] - t[i]) * k) for i in range(3)) + (round(255 * alpha),))
    return img


def blob(size, c, alpha, x, y, radius):
    w, h = size
    reach = max(w, h)
    r = radius * reach
    cx, cy = x * w, y * h
    img = Image.new("RGBA", size)
    px = img.load()
    col = rgba(c)[:3]
    for j in range(h):
        for i in range(0, w):
            dist = ((i - cx) ** 2 + (j - cy) ** 2) ** 0.5
            if dist < r:
                px[i, j] = col + (round(255 * alpha * (1 - dist / r)),)
    return img


def wallpaper(kind):
    if kind == "liso":
        return Image.new("RGBA", (W, H), (46, 125, 110, 255))  # verde azulado inventado
    img = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(img)
    cols = [(250, 250, 250), (20, 20, 20), (230, 80, 60), (60, 110, 230), (250, 210, 60)]
    for k, x in enumerate(range(-H, W + H, 28)):
        d.polygon([(x, 0), (x + 28, 0), (x + 28 + H, H), (x + H, H)], fill=cols[k % len(cols)] + (255,))
    return img


def background(s, size, wp=None):
    bg = s["background"]
    if not s["drawsBackground"]:
        return Image.new("RGBA", size, rgba(bg["top"]))
    if s["wallpaperBehind"]:
        under = wp.filter(ImageFilter.GaussianBlur(24))  # Android 12+, si el móvil lo permite
        return Image.alpha_composite(under, vgradient(size, bg["top"], bg["bottom"], s["layerAlpha"]))
    img = vgradient(size, bg["top"], bg["bottom"])
    if s["blobs"]:
        for b in bg["blobs"]:
            img = Image.alpha_composite(img, blob(size, b["color"], b["alpha"], b["x"], b["y"], b["radius"]))
    return img


def panel(base, s, level, box, radius, blur):
    x0, y0, x1, y1 = box
    p = s["panels"][level]
    w, h = x1 - x0, y1 - y0
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    if s["strong"] and level != "BAR":  # sombra suave fuera del panel, 2 dp más abajo
        sh = Image.new("RGBA", (w, h), (0, 0, 0, round(255 * (0.10 if s["dark"] else 0.06))))
        base.paste(Image.alpha_composite(base.crop((x0, y0 + 2, x1, y1 + 2)), sh), (x0, y0 + 2), mask)
    region = base.crop(box)
    if blur and s["translucent"]:
        region = region.filter(ImageFilter.GaussianBlur(16))
    fill = Image.new("RGBA", (w, h), rgba(p["color"], p["alpha"]))
    region = Image.alpha_composite(region, fill)
    if s["translucent"] and s["highlightAlpha"] > 0:
        hl = Image.new("RGBA", (w, h))
        d = ImageDraw.Draw(hl)
        stop = 0.7 if s["strong"] else 0.5
        for y in range(h):
            k = y / h
            a = s["highlightAlpha"] * max(0.0, 1 - k / stop)
            d.line([(0, y), (w, y)], fill=(255, 255, 255, round(255 * a)))
        region = Image.alpha_composite(region, hl)
    if s["translucent"]:
        edge = Image.new("RGBA", (w, h))
        d = ImageDraw.Draw(edge)
        if level == "BAR":
            d.line([(0, 0), (w, 0)], fill=rgba(s["border"]))
            d.line([(0, h - 1), (w, h - 1)], fill=rgba(s["border"]))
        elif s["litEdge"]:
            lit = (255, 255, 255, round(255 * (0.30 if s["dark"] else 0.90)))
            far = rgba(s["border"])
            for i in range(w + h):  # degradado fijo de arriba a la izquierda a abajo a la derecha
                k = i / (w + h)
                col = tuple(round(lit[c] + (far[c] - lit[c]) * k) for c in range(4))
                d.line([(i, 0), (0, i)], fill=col)
            ring = Image.new("L", (w, h), 0)
            ImageDraw.Draw(ring).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, outline=255, width=max(1, round(s["borderWidth"])))
            edge.putalpha(Image.composite(edge.getchannel("A"), Image.new("L", (w, h), 0), ring))
        else:
            d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, outline=rgba(s["border"]), width=max(1, round(s["borderWidth"])))
        region = Image.alpha_composite(region, edge)
    base.paste(region, (x0, y0), mask)


def draw(s, wp=None, wp_name=""):
    img = background(s, (W, H), wp)
    on, sub, prim = rgba(s["onSurface"]), rgba(s["onSurfaceVariant"]), rgba(s["primary"])
    card_r = 22 if s["strong"] else 16
    # Barra de arriba
    panel(img, s, "BAR", (0, 0, W, 64), 0, True)
    d = ImageDraw.Draw(img)
    d.text((16, 20), "Bóveda de prueba", font=font(20, True), fill=on)
    # Dos tarjetas
    rows = [("Correo de ejemplo", "usuario@ejemplo.org"), ("Red wifi inventada", "RedDePrueba")]
    for i, (t, u) in enumerate(rows):
        y = 84 + i * 96
        panel(img, s, "CARD", (16, y, W - 16, y + 80), card_r, False)
        d = ImageDraw.Draw(img)
        d.text((32, y + 16), t, font=font(17, True), fill=on)
        d.text((32, y + 44), u, font=font(14), fill=sub)
    # Diálogo
    ov_r = 32 if s["strong"] else 28
    panel(img, s, "OVERLAY", (28, 300, W - 28, 500), ov_r, True)
    d = ImageDraw.Draw(img)
    d.text((52, 324), "¿Mover a la papelera?", font=font(18, True), fill=on)
    d.text((52, 360), "La entrada se podrá recuperar", font=font(14), fill=sub)
    d.text((52, 380), "desde la papelera.", font=font(14), fill=sub)
    d.text((W - 210, 450), "Cancelar", font=font(15, True), fill=prim)
    d.text((W - 112, 450), "Mover", font=font(15, True), fill=prim)
    # Etiqueta
    modo = "oscuro" if s["dark"] else "claro"
    lab = f"Aproximación · {s['label']} · Noche, {modo}"
    lines = ["No es una captura: colores exactos, dibujo aproximado.", "Android 12+, con difuminado."]
    if wp_name:
        lines.insert(0, f"Fondo de pantalla {'liso' if wp_name == 'liso' else 'de rayas'}, inventado.")
    top = H - 26 - 16 * len(lines)
    d.rectangle([0, top - 8, W, H], fill=(255, 255, 255, 235))
    d.text((12, top), lab, font=font(12, True), fill=(0, 0, 0, 255))
    for i, t in enumerate(lines):
        d.text((12, top + 20 + 16 * i), t, font=font(11), fill=(0, 0, 0, 255))
    return img.convert("RGB")


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    styles = json.loads((src / "aspecto-colores.json").read_text())
    names = {"SOLID": "solido", "GRADIENT": "degradado", "GLASS": "cristal", "GLASS_STRONG": "vidrio", "TRANSPARENT": "transparente"}
    for s in styles:
        base = f"aproximacion-{names[s['style']]}-{'oscuro' if s['dark'] else 'claro'}"
        if s["wallpaperBehind"]:
            for k in ("liso", "rayas"):
                draw(s, wallpaper(k), k).save(out / f"{base}-fondo-{k}.png", optimize=True)
        else:
            draw(s).save(out / f"{base}.png", optimize=True)


if __name__ == "__main__":
    main()
