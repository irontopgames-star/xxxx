"""
Renderiza el rostro de Bearfriender (retrato de la cabeza) a partir de su malla y textura,
y lo convierte en imagenes de pantalla CRT: para los televisores y para la pantalla de su panza.

  portrait(bbmodel, texture)   -> imagen PIL RGB cuadrada con la cabeza de frente
  crt(img, size, ...)          -> aplica el aspecto de monitor antiguo (grano, lineas, vineta, tinte)
"""
import json
import math

import numpy as np
from PIL import Image, ImageFilter

Image.MAX_IMAGE_PIXELS = None


def _load(bbmodel_path):
    d = json.load(open(bbmodel_path))
    e = d["elements"][0]
    org = np.array(e["origin"], float)
    keys = list(e["vertices"].keys())
    vid = {k: i for i, k in enumerate(keys)}
    P = np.array([e["vertices"][k] for k in keys], float) + org
    quads = []
    for f in e["faces"].values():
        vs = f["vertices"]
        idx = [vid[k] for k in vs]
        uv = [list(f["uv"][k]) for k in vs]
        if len(idx) == 3:
            idx.append(idx[2])
            uv.append(uv[2])
        quads.append((idx, uv))
    return P, quads


def portrait(bbmodel_path, texture_path, size=512, center=(0.0, 27.3), half=3.6, y_min=23.2, light=(0.35, 0.55, 0.75)):
    """
    Dibuja la cabeza vista de frente (el frente del oso es +Z). center/half definen la ventana en
    coordenadas del modelo original (x, y); y_min recorta el cuello.
    """
    P, quads = _load(bbmodel_path)
    tex = Image.open(texture_path).convert("RGB")
    T = np.asarray(tex).astype(np.float32)
    th, tw = T.shape[:2]

    ss = 2                                   # supermuestreo
    W = size * ss
    zbuf = np.full((W, W), -1e9, np.float32)
    img = np.zeros((W, W, 3), np.float32)
    covered = np.zeros((W, W), bool)
    L = np.array(light, float)
    L /= np.linalg.norm(L)

    def to_screen(p):
        x = (p[0] - center[0]) / half          # -1..1
        y = (p[1] - center[1]) / half
        return (x * 0.5 + 0.5) * (W - 1), (0.5 - y * 0.5) * (W - 1)

    def sample(u, v):
        x = np.clip(u / 16.0 * (tw - 1), 0, tw - 1)
        y = np.clip(v / 16.0 * (th - 1), 0, th - 1)
        x0 = np.floor(x).astype(int)
        y0 = np.floor(y).astype(int)
        x1 = np.minimum(x0 + 1, tw - 1)
        y1 = np.minimum(y0 + 1, th - 1)
        fx = (x - x0)[..., None]
        fy = (y - y0)[..., None]
        return (T[y0, x0] * (1 - fx) * (1 - fy) + T[y0, x1] * fx * (1 - fy)
                + T[y1, x0] * (1 - fx) * fy + T[y1, x1] * fx * fy)

    for idx, uvs in quads:
        pts = P[idx]
        if pts[:, 1].mean() < y_min:
            continue
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (pts[i] for i in tri)
            n = np.cross(b - a, c - a)
            ln = np.linalg.norm(n)
            if ln < 1e-9:
                continue
            n = n / ln
            if n[2] < 0:                       # orientar hacia el espectador (+Z)
                n = -n
            sa, sb, sc = to_screen(a), to_screen(b), to_screen(c)
            xs = (sa[0], sb[0], sc[0])
            ys = (sa[1], sb[1], sc[1])
            x0 = int(max(0, math.floor(min(xs))))
            x1 = int(min(W - 1, math.ceil(max(xs))))
            y0 = int(max(0, math.floor(min(ys))))
            y1 = int(min(W - 1, math.ceil(max(ys))))
            if x1 < x0 or y1 < y0:
                continue
            den = (sb[1] - sc[1]) * (sa[0] - sc[0]) + (sc[0] - sb[0]) * (sa[1] - sc[1])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1), np.arange(y0, y1 + 1))
            w0 = ((sb[1] - sc[1]) * (gx - sc[0]) + (sc[0] - sb[0]) * (gy - sc[1])) / den
            w1 = ((sc[1] - sa[1]) * (gx - sc[0]) + (sa[0] - sc[0]) * (gy - sc[1])) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not inside.any():
                continue
            z = w0 * a[2] + w1 * b[2] + w2 * c[2]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            upd = inside & (z > sub)
            if not upd.any():
                continue
            u = w0 * uvs[tri[0]][0] + w1 * uvs[tri[1]][0] + w2 * uvs[tri[2]][0]
            v = w0 * uvs[tri[0]][1] + w1 * uvs[tri[1]][1] + w2 * uvs[tri[2]][1]
            col = sample(u, v)
            shade = 0.45 + 0.75 * max(0.0, float(np.dot(n, L)))
            col = np.clip(col * shade, 0, 255)
            sub[upd] = z[upd]
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[upd] = col[upd]
            covered[y0:y1 + 1, x0:x1 + 1] |= upd

    img[~covered] = (14, 16, 18)
    out = Image.fromarray(np.clip(img, 0, 255).astype("uint8"), "RGB").resize((size, size), Image.LANCZOS)
    return out


def crt(img, size, tint=(0.88, 1.0, 0.92), scan=0.25, noise=14, vignette=0.55, glitch=0, seed=1, contrast=1.25, bright=1.1):
    """Aspecto de monitor antiguo: baja resolucion, lineas de barrido, grano, vineta y tinte."""
    rng = np.random.default_rng(seed)
    w, h = size if isinstance(size, tuple) else (size, size)
    small = img.resize((w, h), Image.LANCZOS)
    arr = np.asarray(small).astype(np.float32)
    lum = arr.mean(axis=2, keepdims=True)
    arr = lum + (arr - lum) * 0.55                      # desaturar un poco
    arr = (arr - 110) * contrast + 110
    arr = arr * bright
    for _ in range(glitch):                             # franjas desplazadas
        y = int(rng.integers(0, h - 3))
        hh = int(rng.integers(1, max(2, h // 12)))
        arr[y:y + hh] = np.roll(arr[y:y + hh], int(rng.integers(-w // 6, w // 6)), axis=1)
    yy, xx = np.mgrid[0:h, 0:w]
    arr *= (1.0 - scan * ((yy % 2) == 0))[..., None]
    r = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2)
    arr *= (1.0 - vignette * np.clip(r - 0.4, 0, 1) ** 1.5)[..., None]
    arr += rng.normal(0, noise, size=arr.shape)
    arr *= np.array(tint)[None, None, :]
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"), "RGB")


def make_images(portrait_img):
    """
    A partir del retrato saca las cuatro imagenes de pantalla:
      tv_face   (64x64)    rostro de frente para los televisores
      tv_close  (64x64)    primer plano de ojos y dientes, con interferencias y tinte rojizo
      bear_face (256x192)  rostro de frente para la pantalla de la panza
      bear_close(256x192)  primer plano para la panza
    """
    p = portrait_img
    S = p.size[0]

    front = p.crop((int(S * 0.08), int(S * 0.08), int(S * 0.92), int(S * 0.92)))
    tv_face = crt(front, 64, tint=(0.82, 1.0, 0.88), scan=0.22, noise=10, glitch=0, seed=3)
    close_sq = p.crop((int(S * 0.12), int(S * 0.20), int(S * 0.88), int(S * 0.84)))
    close_sq = close_sq.resize((S, S), Image.LANCZOS)
    tv_close = crt(close_sq, 64, tint=(1.08, 0.78, 0.78), scan=0.3, noise=16, glitch=3, seed=5, contrast=1.4)

    # Panza (4:3)
    wide = p.crop((int(S * 0.02), int(S * 0.14), int(S * 0.98), int(S * 0.14 + S * 0.96 * 0.75)))
    bear_face = crt(wide, (256, 192), tint=(0.82, 1.0, 0.88), scan=0.22, noise=9, seed=7)
    wide2 = p.crop((int(S * 0.08), int(S * 0.20), int(S * 0.92), int(S * 0.20 + S * 0.84 * 0.75)))
    bear_close = crt(wide2, (256, 192), tint=(1.08, 0.78, 0.78), scan=0.3, noise=14, glitch=5, seed=9, contrast=1.4)
    return {"tv_face": tv_face, "tv_close": tv_close, "bear_face": bear_face, "bear_close": bear_close}
