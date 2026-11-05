#!/usr/bin/env python3
"""
Genera los recursos del osito destruido (Bearfriender usa tools/build_bear.py):
  - geo/entity/*.geo.json           (modelos GeckoLib, formato Bedrock 1.12.0)
  - animations/entity/*.animation.json
  - textures/entity/*.png           (texturas PROVISIONALES pintadas por codigo)

Uso:  python tools/generate_assets.py [carpeta_resources]
      (por defecto: src/main/resources)
Requiere Pillow (pip install pillow).

Los modelos estan pensados para abrirse en Blockbench (Bedrock Entity / GeckoLib)
y retocarse a mano. Si exportas desde Blockbench, sustituye los archivos generados.
Convencion de ejes: frente = -Z, lado izquierdo del personaje = +X.
Rotacion X positiva = la parte superior del hueso se inclina hacia delante.
"""
import json
import math
import os
import random
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join("src", "main", "resources")
ASSETS = os.path.join(OUT, "assets", "bearfriender")
FACES = ("north", "east", "south", "west", "up", "down")


# ----------------------------------------------------------------------------
# Atlas de texturas: reparte un rectangulo propio a cada cara de cada cubo
# ----------------------------------------------------------------------------
class Atlas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.cx = self.cy = self.rowh = 0
        self.jobs = []  # (rect, nombre_de_pintor)

    def alloc(self, w, h):
        w = max(1, math.ceil(w - 1e-6))
        h = max(1, math.ceil(h - 1e-6))
        if self.cx + w > self.w:
            self.cx = 0
            self.cy += self.rowh + 1
            self.rowh = 0
        if self.cy + h > self.h:
            raise RuntimeError("Atlas lleno: aumenta el tamano de la textura")
        rect = (self.cx, self.cy, w, h)
        self.cx += w + 1
        self.rowh = max(self.rowh, h)
        return rect


def make_cube(atlas, mat, origin, size, special=None):
    sx, sy, sz = size
    dims = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy),
            "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}
    uv = {}
    for face in FACES:
        rect = atlas.alloc(*dims[face])
        uv[face] = {"uv": [rect[0], rect[1]], "uv_size": [rect[2], rect[3]]}
        atlas.jobs.append((rect, (special or {}).get(face, mat)))
    return {"origin": list(origin), "size": list(size), "uv": uv}


def bone(name, parent, pivot, cubes=None):
    b = {"name": name, "pivot": list(pivot)}
    if parent:
        b["parent"] = parent
    if cubes:
        b["cubes"] = cubes
    return b


def geometry(identifier, w, h, bones, bounds):
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": identifier,
                "texture_width": w, "texture_height": h,
                "visible_bounds_width": bounds[0],
                "visible_bounds_height": bounds[1],
                "visible_bounds_offset": [0, bounds[2], 0],
            },
            "bones": bones,
        }],
    }


# ----------------------------------------------------------------------------
# Pintores de texturas
# ----------------------------------------------------------------------------
def noisy(base, var, wear=0.0):
    def paint(img, rect, rng):
        x0, y0, w, h = rect
        for y in range(h):
            for x in range(w):
                n = rng.randint(-var, var)
                c = [max(0, min(255, b + n)) for b in base]
                if rng.random() < wear:
                    c = [int(v * 0.6) for v in c]
                img.putpixel((x0 + x, y0 + y), tuple(c) + (255,))
    return paint


def fill(img, rect, color):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            img.putpixel((x0 + x, y0 + y), color)


def px(img, rect, x, y, color):
    x0, y0, w, h = rect
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x0 + x, y0 + y), color)


def eye_painter(img, rect, rng):
    fill(img, rect, (4, 4, 4, 255))
    px(img, rect, rect[2] // 2, rect[3] // 2, (160, 32, 32, 255))


def teddy_face(img, rect, rng):
    PAINT["tfur"](img, rect, rng)
    black = (14, 12, 12, 255)
    for (x, y) in ((1, 1), (2, 1), (1, 2), (2, 2)):          # ojo de boton
        px(img, rect, x, y, black)
    for (x, y) in ((4, 1), (6, 1), (5, 2), (4, 3), (6, 3)):  # ojo tachado con una X
        px(img, rect, x, y, black)
    for (x, y) in ((2, 5), (3, 4), (4, 5), (5, 4)):          # costura de la boca
        px(img, rect, x, y, black)


def teddy_chest(img, rect, rng):
    PAINT["tfur"](img, rect, rng)
    black = (14, 12, 12, 255)
    white = (228, 222, 206, 255)
    for x in range(rect[2]):                                  # costura abierta en zigzag
        px(img, rect, x, 1 + (x % 2), black)
    for (x, y) in ((2, 3), (3, 3), (2, 4), (3, 4)):          # relleno asomando
        px(img, rect, x, y, white)


PAINT = {
    "fur": noisy((112, 80, 54), 14, 0.06),
    "fur_dark": noisy((66, 47, 34), 10, 0.05),
    "fur_light": noisy((150, 118, 86), 12, 0.05),
    "dark": noisy((26, 22, 22), 5),
    "teeth": noisy((204, 194, 164), 10, 0.18),
    "frame": noisy((44, 44, 48), 6),
    "screen": noisy((90, 90, 90), 30),
    "eye": eye_painter,
    "tfur": noisy((150, 126, 92), 14, 0.10),
    "tfur_dark": noisy((98, 78, 56), 10, 0.10),
    "tdark": noisy((20, 18, 18), 5),
    "stuff": noisy((225, 220, 205), 10, 0.15),
    "teddy_face": teddy_face,
    "teddy_chest": teddy_chest,
}


def render_texture(atlas, seed):
    img = Image.new("RGBA", (atlas.w, atlas.h), (0, 0, 0, 0))
    rng = random.Random(seed)
    for rect, painter in atlas.jobs:
        PAINT[painter](img, rect, rng)
    return img


# ----------------------------------------------------------------------------
# OSITO DESTRUIDO
# ----------------------------------------------------------------------------
def build_teddy():
    A = Atlas(64, 64)
    C = lambda mat, o, s, sp=None: make_cube(A, mat, o, s, sp)
    bones = [bone("root", None, [0, 0, 0])]
    body = [C("tfur", [-3, 2, -2.5], [6, 5, 5], {"north": "teddy_chest"}),
            C("stuff", [2.4, 3, -0.8], [1.4, 2, 1.8])]
    bones.append(bone("body", "root", [0, 2, 0], body))
    # Piernas estiradas hacia delante (sentado)
    for side, sx in (("right", -1), ("left", 1)):
        x0 = -3 if sx < 0 else 0.5
        bones.append(bone(f"leg_{side}", "root", [1.75 * sx, 2, -1],
                          [C("tfur_dark", [x0, 0, -5.5], [2.5, 2.5, 5])]))
    bones.append(bone("arm_right", "body", [-3, 6.5, 0], [C("tfur", [-5, 2.5, -1.5], [2, 4.5, 3])]))
    bones.append(bone("arm_left", "body", [3, 6.5, 0], [C("tfur_dark", [3, 4, -1.5], [2, 3, 3])]))  # brazo roto, mas corto
    head = [
        C("tfur", [-3.5, 7, -3], [7, 6, 6], {"north": "teddy_face"}),
        C("tfur_dark", [-1.5, 8, -4.5], [3, 2.5, 1.5]),
        C("tdark", [-0.75, 9.5, -4.9], [1.5, 1, 0.4]),
    ]
    bones.append(bone("head", "body", [0, 7, 0], head))
    bones.append(bone("ear_right", "head", [-3, 12, 0], [C("tfur", [-4.5, 11.5, -1], [2.5, 2.5, 1.5])]))
    bones.append(bone("ear_left", "head", [3, 12, 0], [C("tfur_dark", [2.5, 11.5, -1], [1.8, 1.5, 1.5])]))  # oreja rasgada
    top = max(c["origin"][1] + c["size"][1] for b in bones for c in b.get("cubes", []))
    print(f"[teddy] altura del modelo: {top} px = {top / 16:.2f} bloques")
    geo = geometry("geometry.broken_teddy", A.w, A.h, bones, (2, 2, 0.5))
    return geo, A


# ----------------------------------------------------------------------------
# Animaciones
# ----------------------------------------------------------------------------
def K(*frames):
    return {str(float(t)): [round(float(c), 3) for c in v] for t, v in frames}


def B(rot=None, pos=None, scale=None):
    d = {}
    if rot:
        d["rotation"] = K(*rot)
    if pos:
        d["position"] = K(*pos)
    if scale:
        d["scale"] = K(*scale)
    return d


def osc(length, amp=(0, 0, 0), phase=0.0, base=(0, 0, 0), cycles=1, n=8):
    """Oscilacion senoidal muestreada: el primer y ultimo fotograma coinciden (bucle limpio)."""
    pts = []
    for i in range(n + 1):
        s = math.sin(2 * math.pi * cycles * i / n + phase)
        pts.append((length * i / n, [base[k] + amp[k] * s for k in range(3)]))
    return pts


def anim(length, loop, bones):
    return {"loop": loop, "animation_length": length, "bones": bones}


def shake(t0, t1, amp, step=0.08):
    pts, t, sign = [], t0, 1
    while t <= t1:
        pts.append((t, [amp * sign, 0, 0]))
        sign = -sign
        t += step
    return pts


Z = [0, 0, 0]
ONE = [1, 1, 1]


def teddy_animations():
    A = {}
    A["sit"] = anim(5, True, {
        "head": B(rot=osc(5, (3, 0, 2.5), 0, (12, 0, 8))),
        "body": B(rot=osc(5, (1, 0, 0), 0.5)),
        "ear_left": B(rot=osc(5, (0, 0, 6), 1.0)),
        "arm_right": B(rot=osc(5, (2, 0, 0), 0.2, (-10, 0, 0))),
    })
    A["talk"] = anim(1.2, True, {
        "head": B(rot=osc(1.2, (6, 4, 0), 0, (8, 0, 6))),
        "body": B(pos=osc(1.2, (0, 0.2, 0))),
        "arm_left": B(rot=osc(1.2, (0, 0, 10), 0.4, (0, 0, 12))),
        "ear_left": B(rot=osc(1.2, (0, 0, 8), 1.0)),
    })
    return A


# ----------------------------------------------------------------------------
# Escritura de archivos
# ----------------------------------------------------------------------------
def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print("escrito", path)


def write_png(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("escrito", path)


def main():
    geo_dir = os.path.join(ASSETS, "geo", "entity")
    anim_dir = os.path.join(ASSETS, "animations", "entity")
    tex_dir = os.path.join(ASSETS, "textures", "entity")
    # Bearfriender se genera con tools/build_bear.py a partir de oso.bbmodel.
    tgeo, tatlas = build_teddy()
    write_json(os.path.join(geo_dir, "broken_teddy.geo.json"), tgeo)
    write_json(os.path.join(anim_dir, "broken_teddy.animation.json"),
               {"format_version": "1.8.0", "animations": teddy_animations()})
    write_png(os.path.join(tex_dir, "broken_teddy.png"), render_texture(tatlas, 2))


if __name__ == "__main__":
    main()
