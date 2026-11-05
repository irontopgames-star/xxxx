#!/usr/bin/env python3
"""
Genera los bloques decorativos de la jugueteria: modelos JSON, estados de bloque, texturas,
modelos de objeto y tablas de botin.

  - crt_tv          televisor CRT con 5 pantallas (off, static, face, smile, interference)
  - plush_teddy / plush_bunny / plush_duck
  - balloon (6 colores), balloon_string
  - toy_blocks      cubos de juguete apilados

Uso: python tools/build_decor.py [carpeta_resources]
Requiere Pillow y numpy. Las texturas son provisionales: puedes repintarlas conservando el nombre.
"""
import json
import os
import random
import sys

import numpy as np
from PIL import Image

RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "bearfriender")
DATA = os.path.join(RES, "data", "bearfriender")
MOD = "bearfriender"
RNG = np.random.default_rng(7)
PY = random.Random(7)

TEX_DIR = os.path.join(ASSETS, "textures", "block")
MODEL_DIR = os.path.join(ASSETS, "models", "block")
ITEM_DIR = os.path.join(ASSETS, "models", "item")
STATE_DIR = os.path.join(ASSETS, "blockstates")
LOOT_DIR = os.path.join(DATA, "loot_tables", "blocks")
for d in (TEX_DIR, MODEL_DIR, ITEM_DIR, STATE_DIR, LOOT_DIR):
    os.makedirs(d, exist_ok=True)


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


def tex(name):
    return f"{MOD}:block/{name}"


# ----------------------------------------------------------------------------
# Texturas
# ----------------------------------------------------------------------------
def noisy_image(w, h, base, var=10):
    arr = np.zeros((h, w, 4), dtype=np.uint8)
    for c in range(3):
        arr[:, :, c] = np.clip(base[c] + RNG.integers(-var, var + 1, size=(h, w)), 0, 255)
    arr[:, :, 3] = 255
    return Image.fromarray(arr, "RGBA")


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), tuple(color) + ((255,) if len(color) == 3 else ()))


def save_tex(img, name):
    img.save(os.path.join(TEX_DIR, name + ".png"))


def save_animated(frames, name, frametime=2):
    w, h = frames[0].size
    strip = Image.new("RGBA", (w, h * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * h))
    strip.save(os.path.join(TEX_DIR, name + ".png"))
    write_json(os.path.join(TEX_DIR, name + ".png.mcmeta"), {"animation": {"frametime": frametime}})


def make_tv_textures():
    # Carcasa y trasera
    cas = noisy_image(16, 16, (178, 168, 142), 8)
    for x in range(16):                                           # manchas amarillentas
        for y in range(16):
            if PY.random() < 0.06:
                put(cas, x, y, (150, 138, 108))
    save_tex(cas, "crt_casing")
    back = noisy_image(16, 16, (66, 66, 70), 6)
    for y in (3, 5, 7, 9):                                       # rejillas de ventilacion
        for x in range(3, 13):
            put(back, x, y, (30, 30, 32))
    save_tex(back, "crt_back")

    # Frontal con marco; el hueco de la pantalla es x 3..13, y 2..9.5 (v 6.5..14)
    front = noisy_image(16, 16, (178, 168, 142), 7)
    for x in range(2, 14):
        for y in range(5, 15):
            put(front, x, y, (60, 56, 48))                      # marco oscuro alrededor de la pantalla
    for (kx, ky) in ((12, 15), (10, 15)):                        # perillas
        put(front, kx, ky, (40, 40, 40))
    save_tex(front, "crt_front")

    # Pantallas
    off = noisy_image(16, 16, (10, 12, 12), 2)
    for i in range(6):                                           # reflejo
        put(off, 2 + i, 2 + i // 2, (28, 32, 32))
    save_tex(off, "crt_screen_off")

    frames = []
    for f in range(4):
        fr = Image.fromarray(
            np.dstack([RNG.integers(40, 235, size=(16, 16))] * 3 + [np.full((16, 16), 255)]).astype(np.uint8), "RGBA")
        frames.append(fr)
    save_animated(frames, "crt_screen_static", 2)

    frames = []
    for f in range(4):
        arr = np.zeros((16, 16, 4), dtype=np.uint8)
        for y in range(16):
            band = 0.55 + 0.45 * np.sin((y + f * 4) / 2.2)
            v = RNG.integers(60, 230, size=16) * band
            arr[y, :, 0] = v * 0.5
            arr[y, :, 1] = v * 0.55
            arr[y, :, 2] = v
            arr[y, :, 3] = 255
        frames.append(Image.fromarray(arr, "RGBA"))
    save_animated(frames, "crt_screen_interference", 2)

    face = noisy_image(16, 16, (5, 24, 10), 2)
    glow = (200, 235, 205)
    for (x0, y0) in ((4, 4), (10, 4)):
        for dx in range(2):
            for dy in range(2):
                put(face, x0 + dx, y0 + dy, glow)
    for x in range(4, 12):
        put(face, x, 11, glow)
    put(face, 3, 10, glow)
    put(face, 12, 10, glow)
    for x in (5, 7, 9):
        put(face, x, 12, glow)
    if not os.path.exists(os.path.join(TEX_DIR, "crt_screen_face.png")):   # lo genera build_bear.py con el rostro real
        save_tex(face, "crt_screen_face")

    smile = noisy_image(16, 16, (34, 4, 4), 2)
    g2 = (240, 228, 205)
    for x in range(2, 14):
        put(smile, x, 9, g2)
    for x in (2, 13):
        put(smile, x, 8, g2)
    for x in range(3, 13, 2):
        put(smile, x, 10, g2)
        put(smile, x, 11, g2)
    for (x, y) in ((4, 4), (5, 4), (10, 4), (11, 4)):
        put(smile, x, y, g2)
    if not os.path.exists(os.path.join(TEX_DIR, "crt_screen_smile.png")):
        save_tex(smile, "crt_screen_smile")


# ----------------------------------------------------------------------------
# Modelos
# ----------------------------------------------------------------------------
FACE_KEYS = ("north", "east", "south", "west", "up", "down")


def cube(frm, to, texture, uv=None, per_face=None, rotation=None):
    faces = {}
    for k in FACE_KEYS:
        f = {"texture": texture}
        if uv is not None:
            f["uv"] = uv
        faces[k] = f
    if per_face:
        for k, v in per_face.items():
            faces[k] = v
    el = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        el["rotation"] = rotation
    return el


def block_model(name, textures, elements):
    write_json(os.path.join(MODEL_DIR, name + ".json"), {
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": elements,
    })


def item_model(name, block_model_name):
    write_json(os.path.join(ITEM_DIR, name + ".json"), {"parent": f"{MOD}:block/{block_model_name}"})


def loot_self(name):
    write_json(os.path.join(LOOT_DIR, name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })


FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_variants(model, prefix=""):
    out = {}
    for f, y in FACING_Y.items():
        v = {"model": f"{MOD}:block/{model}"}
        if y:
            v["y"] = y
        out[f"{prefix}facing={f}"] = v
    return out


# --- Televisor CRT ---
TV_SCREENS = ("off", "static", "face", "smile", "interference")


def make_tv_models():
    for s in TV_SCREENS:
        elements = [
            # carcasa: la cara norte lleva el marco frontal (uv a escala de bloque)
            cube([1, 0, 3], [15, 11, 13], "#casing", per_face={
                "north": {"uv": [1, 5, 15, 16], "texture": "#front"},
            }),
            cube([3, 1, 13], [13, 9, 15], "#back"),                          # tubo trasero
            cube([2, 0, 3.5], [4, 0.6, 5.5], "#back"),                       # patas
            cube([12, 0, 3.5], [14, 0.6, 5.5], "#back"),
            cube([2, 0, 10.5], [4, 0.6, 12.5], "#back"),
            cube([12, 0, 10.5], [14, 0.6, 12.5], "#back"),
            # pantalla ligeramente adelantada
            cube([3, 1.5, 2.9], [13, 9.5, 3.0], "#casing", per_face={
                "north": {"uv": [0, 0, 16, 16], "texture": "#screen"},
            }),
            # antenas
            cube([6.6, 11, 8], [7.1, 15.5, 8.5], "#back", rotation={"angle": -22.5, "axis": "z", "origin": [7, 11, 8]}),
            cube([9.0, 11, 8], [9.5, 15.5, 8.5], "#back", rotation={"angle": 22.5, "axis": "z", "origin": [9.5, 11, 8]}),
        ]
        block_model(f"crt_tv_{s}", {
            "particle": tex("crt_casing"), "casing": tex("crt_casing"), "back": tex("crt_back"),
            "front": tex("crt_front"), "screen": tex(f"crt_screen_{s}"),
        }, elements)
    variants = {}
    for s in TV_SCREENS:
        for f, y in FACING_Y.items():
            v = {"model": f"{MOD}:block/crt_tv_{s}"}
            if y:
                v["y"] = y
            variants[f"facing={f},screen={s}"] = v
    write_json(os.path.join(STATE_DIR, "crt_tv.json"), {"variants": variants})
    item_model("crt_tv", "crt_tv_off")
    loot_self("crt_tv")


# --- Peluches (paleta de 4x4 muestras de 4x4 px) ---
def swatch(k):
    x, y = (k % 4) * 4, (k // 4) * 4
    return [x, y, x + 4, y + 4]


def make_palette(name, colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for k, c in enumerate(colors):
        x0, y0 = (k % 4) * 4, (k // 4) * 4
        for x in range(4):
            for y in range(4):
                var = RNG.integers(-9, 10)
                col = tuple(int(np.clip(v + var, 0, 255)) for v in c)
                if PY.random() < 0.10:                       # suciedad
                    col = tuple(int(v * 0.7) for v in col)
                img.putpixel((x0 + x, y0 + y), col + (255,))
    save_tex(img, name)


def pc(frm, to, k, faces=None):
    """cubo con muestra de color k en todas sus caras"""
    return cube(frm, to, "#0", uv=swatch(k), per_face=faces)


def make_plush(name, colors, elements):
    make_palette(name, colors)
    block_model(name, {"0": tex(name), "particle": tex(name)}, elements)
    write_json(os.path.join(STATE_DIR, name + ".json"), {"variants": facing_variants(name)})
    item_model(name, name)
    loot_self(name)


def teddy_elements(ribbon=6):
    return [
        pc([4.5, 0, 5], [11.5, 6.5, 11], 0),
        pc([5.5, 0.8, 4.8], [10.5, 5.5, 5.05], 1),
        pc([4.8, 6.5, 5.2], [11.2, 12, 10.8], 0),
        pc([6.8, 7.6, 4.6], [9.2, 9.4, 5.2], 1),
        pc([7.5, 8.8, 4.4], [8.5, 9.5, 4.7], 2),
        pc([5.9, 9.8, 5.0], [7.0, 10.9, 5.25], 2),
        pc([9.0, 9.8, 5.0], [10.1, 10.9, 5.25], 2),
        pc([4.4, 11.4, 6.2], [6.4, 13.2, 8.2], 0),
        pc([9.6, 11.4, 6.2], [11.6, 13.2, 8.2], 0),
        pc([4.9, 11.6, 6.0], [5.9, 12.8, 6.2], 3),
        pc([10.1, 11.6, 6.0], [11.1, 12.8, 6.2], 3),
        pc([3.0, 2.0, 6.0], [4.8, 6.0, 9.5], 0),
        pc([11.2, 2.0, 6.0], [13.0, 6.0, 9.5], 0),
        pc([4.5, 0, 3.6], [7.5, 2.6, 6.4], 0),
        pc([8.5, 0, 3.6], [11.5, 2.6, 6.4], 0),
        pc([5.0, 0.2, 3.5], [7.0, 1.8, 3.65], 3),
        pc([9.0, 0.2, 3.5], [11.0, 1.8, 3.65], 3),
        pc([7.0, 5.6, 4.7], [9.0, 6.6, 5.0], ribbon),
        pc([6.0, 5.8, 4.7], [7.0, 6.6, 5.0], ribbon),
        pc([9.0, 5.8, 4.7], [10.0, 6.6, 5.0], ribbon),
    ]


def bunny_elements():
    return [
        pc([4.8, 0, 5.2], [11.2, 6, 10.8], 0),
        pc([5.8, 0.6, 5.0], [10.2, 5.2, 5.25], 1),
        pc([5.0, 6, 5.4], [11.0, 11, 10.6], 0),
        pc([7.2, 7.4, 4.9], [8.8, 8.6, 5.4], 3),
        pc([5.9, 8.8, 5.2], [6.9, 9.9, 5.45], 2),
        pc([9.1, 8.8, 5.2], [10.1, 9.9, 5.45], 2),
        pc([5.4, 11.0, 6.4], [7.0, 15.8, 7.8], 0),
        pc([9.0, 11.0, 6.4], [10.6, 15.8, 7.8], 0),
        pc([5.8, 11.4, 6.2], [6.6, 14.8, 6.45], 3),
        pc([9.4, 11.4, 6.2], [10.2, 14.8, 6.45], 3),
        pc([3.4, 1.8, 6.0], [4.8, 5.6, 9.0], 0),
        pc([11.2, 1.8, 6.0], [12.6, 5.6, 9.0], 0),
        pc([4.8, 0, 3.8], [7.4, 2.4, 6.4], 0),
        pc([8.6, 0, 3.8], [11.2, 2.4, 6.4], 0),
        pc([7.0, 1.5, 10.8], [9.0, 3.5, 12.5], 1),
        pc([6.6, 5.2, 4.8], [9.4, 6.0, 5.1], 6),
    ]


def duck_elements():
    return [
        pc([4.5, 0.6, 5.0], [11.5, 6.0, 11.5], 0),
        pc([5.5, 1.2, 4.8], [10.5, 5.0, 5.05], 1),
        pc([5.5, 6.0, 5.5], [10.5, 10.6, 10.5], 0),
        pc([6.5, 7.0, 3.6], [9.5, 8.4, 5.5], 4),
        pc([6.0, 8.8, 5.2], [7.0, 9.8, 5.45], 2),
        pc([9.0, 8.8, 5.2], [10.0, 9.8, 5.45], 2),
        pc([3.2, 2.0, 6.0], [4.5, 5.5, 10.0], 0),
        pc([11.5, 2.0, 6.0], [12.8, 5.5, 10.0], 0),
        pc([5.0, 0.0, 4.5], [7.0, 0.7, 6.8], 4),
        pc([9.0, 0.0, 4.5], [11.0, 0.7, 6.8], 4),
        pc([7.0, 3.0, 11.5], [9.0, 5.0, 13.0], 0),
        pc([6.6, 5.4, 4.8], [9.4, 6.0, 5.1], 6),
    ]


# --- Globos ---
BALLOON_COLORS = [(205, 45, 50), (50, 95, 200), (230, 200, 50), (60, 170, 80), (235, 120, 170), (140, 70, 190)]


def make_balloons():
    for i, c in enumerate(BALLOON_COLORS):
        img = noisy_image(16, 16, c, 6)
        for x in range(2, 6):                                  # brillo
            for y in range(2, 5):
                put(img, x, y, tuple(min(255, v + 55) for v in c))
        for _ in range(10):                                    # globo medio desinflado: manchas
            put(img, PY.randrange(16), PY.randrange(16), tuple(int(v * 0.7) for v in c))
        save_tex(img, f"balloon_{i}")
        elements = [
            cube([5, 7, 5], [11, 15, 11], "#0"),
            cube([4, 9, 4], [12, 14, 12], "#0"),
            cube([6, 15, 6], [10, 16, 10], "#0"),
            cube([6.5, 5.5, 6.5], [9.5, 7, 9.5], "#0"),
            cube([7.4, 4.6, 7.4], [8.6, 5.6, 8.6], "#0"),
            cube([7.85, 0, 7.85], [8.15, 4.8, 8.15], "#string"),
        ]
        block_model(f"balloon_{i}", {"0": tex(f"balloon_{i}"), "string": tex("balloon_string"), "particle": tex(f"balloon_{i}")}, elements)
    variants = {f"color={i}": {"model": f"{MOD}:block/balloon_{i}"} for i in range(6)}
    write_json(os.path.join(STATE_DIR, "balloon.json"), {"variants": variants})
    item_model("balloon", "balloon_0")
    loot_self("balloon")

    s = noisy_image(16, 16, (225, 225, 215), 8)
    save_tex(s, "balloon_string")
    block_model("balloon_string", {"0": tex("balloon_string"), "particle": tex("balloon_string")},
                [cube([7.85, 0, 7.85], [8.15, 16, 8.15], "#0")])
    write_json(os.path.join(STATE_DIR, "balloon_string.json"), {"variants": {"": {"model": f"{MOD}:block/balloon_string"}}})
    item_model("balloon_string", "balloon_string")
    loot_self("balloon_string")


# --- Cubos de juguete ---
FONT = {
    "A": ["01110", "10001", "11111", "10001", "10001"],
    "B": ["11110", "10001", "11110", "10001", "11110"],
    "C": ["01111", "10000", "10000", "10000", "01111"],
}


def make_toy_blocks():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    cols = [((200, 50, 50), "A"), ((60, 100, 200), "B"), ((230, 195, 50), "C"), ((70, 160, 80), "A")]
    for k, (c, letter) in enumerate(cols):
        x0, y0 = (k % 2) * 8, (k // 2) * 8
        for x in range(8):
            for y in range(8):
                var = int(RNG.integers(-8, 9))
                img.putpixel((x0 + x, y0 + y), tuple(int(np.clip(v + var, 0, 255)) for v in c) + (255,))
        for ry, row in enumerate(FONT[letter]):
            for rx, ch in enumerate(row):
                if ch == "1":
                    img.putpixel((x0 + 1 + rx, y0 + 1 + ry), (245, 240, 225, 255))
    save_tex(img, "toy_blocks")
    uv = lambda k: [(k % 2) * 8, (k // 2) * 8, (k % 2) * 8 + 8, (k // 2) * 8 + 8]
    elements = [
        cube([1, 0, 1], [7, 6, 7], "#0", uv=uv(0)),
        cube([8, 0, 2], [14, 6, 8], "#0", uv=uv(1)),
        cube([4, 6, 3], [10, 12, 9], "#0", uv=uv(2)),
    ]
    block_model("toy_blocks", {"0": tex("toy_blocks"), "particle": tex("toy_blocks")}, elements)
    write_json(os.path.join(STATE_DIR, "toy_blocks.json"), {"variants": facing_variants("toy_blocks")})
    item_model("toy_blocks", "toy_blocks")
    loot_self("toy_blocks")


def make_lang():
    for code, names in (
        ("es_es", {
            "block.bearfriender.crt_tv": "Televisor CRT",
            "block.bearfriender.plush_teddy": "Peluche de osito",
            "block.bearfriender.plush_bunny": "Peluche de conejo",
            "block.bearfriender.plush_duck": "Peluche de patito",
            "block.bearfriender.balloon": "Globo",
            "block.bearfriender.balloon_string": "Hilo de globo",
            "block.bearfriender.toy_blocks": "Cubos de juguete",
            "itemGroup.bearfriender": "Bearfriender",
        }),
        ("en_us", {
            "block.bearfriender.crt_tv": "CRT Television",
            "block.bearfriender.plush_teddy": "Teddy Plush",
            "block.bearfriender.plush_bunny": "Bunny Plush",
            "block.bearfriender.plush_duck": "Duck Plush",
            "block.bearfriender.balloon": "Balloon",
            "block.bearfriender.balloon_string": "Balloon String",
            "block.bearfriender.toy_blocks": "Toy Blocks",
            "itemGroup.bearfriender": "Bearfriender",
        }),
    ):
        path = os.path.join(ASSETS, "lang", code + ".json")
        existing = {}
        if os.path.exists(path):
            existing = json.load(open(path, encoding="utf-8"))
        existing.update(names)
        write_json(path, existing)


def main():
    make_tv_textures()
    make_tv_models()
    make_plush("plush_teddy",
               [(140, 98, 60), (205, 165, 115), (22, 18, 18), (195, 120, 125), (0, 0, 0), (0, 0, 0), (200, 40, 50), (0, 0, 0)],
               teddy_elements())
    make_plush("plush_bunny",
               [(228, 220, 225), (245, 205, 215), (22, 18, 18), (240, 150, 170), (0, 0, 0), (0, 0, 0), (70, 120, 200), (0, 0, 0)],
               bunny_elements())
    make_plush("plush_duck",
               [(235, 200, 60), (246, 226, 125), (22, 18, 18), (0, 0, 0), (232, 122, 32), (0, 0, 0), (70, 160, 80), (0, 0, 0)],
               duck_elements())
    make_balloons()
    make_toy_blocks()
    make_lang()
    print("decoracion generada en", ASSETS)


if __name__ == "__main__":
    main()
