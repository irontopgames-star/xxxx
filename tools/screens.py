"""Imagenes de la pantalla CRT de la panza de Bearfriender (provisionales, pintadas por codigo)."""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 256, 192
FRAMES = 4


def _finish(img, tint=(1.0, 1.0, 1.0), vignette=0.55, scan=0.22):
    """Scanlines, vineta y un leve resplandor para parecer un CRT."""
    arr = np.asarray(img.convert("RGB")).astype(float)
    glow = np.asarray(img.convert("RGB").filter(ImageFilter.GaussianBlur(5))).astype(float)
    arr = np.clip(arr + glow * 0.35, 0, 255)
    yy, xx = np.mgrid[0:H, 0:W]
    scanlines = 1.0 - scan * ((yy % 3) == 0)
    r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    vig = 1.0 - vignette * np.clip(r - 0.35, 0, 1) ** 1.6
    arr *= (scanlines * vig)[..., None]
    arr *= np.array(tint)[None, None, :]
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"), "RGB")


def off_screen():
    yy, xx = np.mgrid[0:H, 0:W]
    base = 10 + 6 * np.exp(-(((xx - 70) / 60.0) ** 2 + ((yy - 50) / 40.0) ** 2))   # reflejo tenue
    arr = np.stack([base, base + 2, base + 2], axis=-1)
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"), "RGB")


def static_screen(frame, tint=(1, 1, 1), bars=False):
    rng = np.random.default_rng(100 + frame)
    arr = rng.integers(40, 235, size=(H, W)).astype(float)
    for _ in range(4):                                  # desgarros horizontales
        y = int(rng.integers(0, H - 8))
        h = int(rng.integers(2, 8))
        arr[y:y + h] = np.roll(arr[y:y + h], int(rng.integers(-40, 40)), axis=1)
    if bars:
        yy = np.arange(H)[:, None]
        arr = arr * (0.55 + 0.45 * np.sin((yy + frame * 40) / 11.0))
    img = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"), "L").convert("RGB")
    return _finish(img, tint, vignette=0.45)


def face_screen():
    img = Image.new("RGB", (W, H), (4, 18, 8))
    d = ImageDraw.Draw(img)
    c = (200, 235, 205)
    cx, cy = W // 2, H // 2 + 6
    d.ellipse([cx - 62, cy - 62, cx + 62, cy + 58], outline=c, width=5)
    d.ellipse([cx - 80, cy - 74, cx - 38, cy - 34], outline=c, width=5)
    d.ellipse([cx + 38, cy - 74, cx + 80, cy - 34], outline=c, width=5)
    for ex in (-26, 26):
        d.ellipse([cx + ex - 12, cy - 30, cx + ex + 12, cy - 4], fill=c)
        d.ellipse([cx + ex - 5, cy - 24, cx + ex + 5, cy - 10], fill=(4, 18, 8))
    d.polygon([(cx - 9, cy + 2), (cx + 9, cy + 2), (cx, cy + 12)], fill=c)
    d.arc([cx - 46, cy - 12, cx + 46, cy + 50], 15, 165, fill=c, width=5)
    for i in range(-5, 6):
        x = cx + i * 8
        y0 = cy + 37 - int(abs(i) * 1.2)
        d.line([(x, y0), (x, y0 + 7)], fill=c, width=3)
    return _finish(img, tint=(0.8, 1.0, 0.85))


def smile_screen():
    img = Image.new("RGB", (W, H), (30, 3, 3))
    d = ImageDraw.Draw(img)
    c = (240, 228, 205)
    cx, cy = W // 2, H // 2
    d.arc([cx - 100, cy - 60, cx + 100, cy + 70], 10, 170, fill=c, width=9)
    for i in range(-9, 10):
        x = cx + i * 10
        top = cy + 22 - int(((i / 9.0) ** 2) * 30)
        d.rectangle([x - 3, top + 6, x + 3, top + 24], fill=c)
    d.ellipse([cx - 62, cy - 52, cx - 44, cy - 34], fill=c)
    d.ellipse([cx + 44, cy - 52, cx + 62, cy - 34], fill=c)
    return _finish(img, tint=(1.0, 0.8, 0.8))


def make_all(tex_dir, aspect, faces=None):
    os.makedirs(tex_dir, exist_ok=True)
    off_screen().save(os.path.join(tex_dir, "screen_off.png"))
    if faces:      # rostro real de Bearfriender (ver bear_face.py)
        faces["bear_face"].save(os.path.join(tex_dir, "screen_face.png"))
        faces["bear_close"].save(os.path.join(tex_dir, "screen_smile.png"))
    else:
        face_screen().save(os.path.join(tex_dir, "screen_face.png"))
        smile_screen().save(os.path.join(tex_dir, "screen_smile.png"))
    for f in range(FRAMES):
        static_screen(f).save(os.path.join(tex_dir, f"screen_static_{f}.png"))
        static_screen(f, tint=(0.75, 0.85, 1.15), bars=True).save(os.path.join(tex_dir, f"screen_interference_{f}.png"))
    print("pantallas escritas en", tex_dir)
