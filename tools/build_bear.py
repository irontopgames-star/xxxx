#!/usr/bin/env python3
"""
Convierte la malla de Bearfriender (oso.bbmodel, exportado de Meshy) en los recursos del mod:

  assets/bearfriender/mesh/bearfriender.mesh        malla con pesos de esqueleto (binario)
  assets/bearfriender/mesh/bearfriender.rig.json    esqueleto (huesos y pivotes)
  assets/bearfriender/animations/entity/bearfriender.animation.json
  assets/bearfriender/textures/entity/bearfriender.png        textura principal (reducida)
  assets/bearfriender/textures/entity/screen_*.png            imagenes de la pantalla de la panza

Uso:
  python tools/build_bear.py oso.bbmodel textura.png [carpeta_resources] [--preview carpeta]

Requiere: pillow, numpy, scipy.

Convenciones del rig (las mismas en Python y en Java):
  * Frente del oso = +Z, arriba = +Y, la izquierda del oso = +X (derecha = -X).
  * Unidades: pixeles de modelo (16 px = 1 bloque). Altura total 48 px = 3 bloques.
  * Rotacion = Rz * Ry * Rx (en grados, matrices destro-giras clasicas).
  * Rotacion X positiva inclina la parte alta hacia delante. Un brazo que cuelga y gira
    en X negativo se va hacia delante.
  * Rotacion Z positiva lleva hacia fuera el brazo izquierdo (+X); en el derecho es Z negativa.
"""
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image
from scipy import sparse
from scipy.sparse.csgraph import connected_components
from scipy.sparse.linalg import splu
from scipy.spatial import cKDTree

Image.MAX_IMAGE_PIXELS = None
TARGET_HEIGHT = 48.0          # px = 3 bloques
TEX_SIZE = 1024               # tamano de la textura exportada
ARM_HANG_DEG = 70.0           # cuanto se bajan los brazos respecto a la T-pose

# --------------------------------------------------------------------------------------
# Esqueleto (coordenadas de la malla original, T-pose, antes de escalar)
# --------------------------------------------------------------------------------------
BONES = [
    # nombre,        padre,        pivote (x, y, z)
    ("root",         None,         (0.0, 0.0, 0.0)),
    ("torso",        "root",       (0.0, 9.5, 0.0)),
    ("chest",        "torso",      (0.0, 14.5, 0.0)),
    ("leg_right",    "root",       (-2.7, 9.0, 0.0)),
    ("leg_left",     "root",       (2.7, 9.0, 0.0)),
    ("arm_right",    "chest",      (-4.0, 19.0, 0.0)),
    ("arm_left",     "chest",      (4.0, 19.0, 0.0)),
    ("forearm_right", "arm_right", (-8.2, 18.9, 0.0)),
    ("forearm_left", "arm_left",   (8.2, 18.9, 0.0)),
    ("hand_right",   "forearm_right", (-11.3, 18.7, 0.0)),
    ("hand_left",    "forearm_left",  (11.3, 18.7, 0.0)),
    ("neck_1",       "chest",      (0.0, 20.6, 0.3)),
    ("neck_2",       "neck_1",     (0.0, 22.0, 0.2)),
    ("neck_3",       "neck_2",     (0.0, 23.4, 0.0)),
    ("head",         "neck_3",     (0.0, 24.7, 0.0)),
    ("jaw",          "head",       (0.0, 25.4, 0.9)),
]
BI = {b[0]: i for i, b in enumerate(BONES)}
NB = len(BONES)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


# --------------------------------------------------------------------------------------
# Carga de la malla
# --------------------------------------------------------------------------------------
def load_mesh(path, tex_small):
    d = json.load(open(path))
    e = d["elements"][0]
    org = np.array(e["origin"], float)
    keys = list(e["vertices"].keys())
    vid = {k: i for i, k in enumerate(keys)}
    P = np.array([e["vertices"][k] for k in keys], float) + org
    quads = []   # (idx[4], uv[4][2])
    for f in e["faces"].values():
        vs = f["vertices"]
        idx = [vid[k] for k in vs]
        uv = [list(f["uv"][k]) for k in vs]
        if len(idx) == 3:
            idx.append(idx[2])
            uv.append(uv[2])
        quads.append((idx, uv))
    arr = np.array(tex_small.convert("RGB")).astype(float)
    n = arr.shape[0]
    cols = []
    for idx, uv in quads:
        c = []
        for (u, v) in uv:
            x = int(min(n - 1, max(0, u / 16 * n)))
            y = int(min(n - 1, max(0, v / 16 * n)))
            c.append(arr[y, x])
        cols.append(np.mean(c, axis=0))
    return P, quads, np.array(cols)


# --------------------------------------------------------------------------------------
# Pesos: etiquetas "duras" en el centro de cada zona y difusion armonica por la superficie
# --------------------------------------------------------------------------------------
def core_labels(P):
    n = len(P)
    lab = -np.ones(n, int)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    ax = np.abs(x)
    right = x < 0

    def setl(mask, name_r, name_l=None):
        if name_l is None:
            lab[mask & (lab < 0)] = BI[name_r]
        else:
            lab[mask & right & (lab < 0)] = BI[name_r]
            lab[mask & ~right & (lab < 0)] = BI[name_l]

    # Brazos (franja de altura del hombro)
    band = (y >= 16.2) & (y <= 21.6)
    setl(band & (ax >= 12.0), "hand_right", "hand_left")
    setl(band & (ax >= 9.4) & (ax <= 10.4), "forearm_right", "forearm_left")
    setl(band & (ax >= 5.6) & (ax <= 7.2), "arm_right", "arm_left")

    # Tronco
    setl((y >= 9.0) & (y <= 11.3) & (ax < 5.0) | ((z < -3.2) & (y > 7.0) & (y < 12.0)), "torso")
    setl(((y >= 13.5) & (y <= 19.8) & (ax <= 2.4)) | ((y >= 13.5) & (y <= 15.8) & (ax <= 6.5)), "chest")

    # Piernas (por debajo de la entrepierna)
    setl((y < 6.4) & (ax < 7.5), "leg_right", "leg_left")

    # Cuello
    setl((y >= 21.7) & (y <= 22.2) & (ax < 3.5), "neck_1")
    setl((y >= 22.8) & (y <= 23.2) & (ax < 3.5), "neck_2")

    # Cabeza y mandibula
    line = 25.4 + 0.56 * (z - 2.6)               # linea de la boca vista de lado
    in_head = (y >= 23.6) & (ax < 4.0)
    setl(in_head & (z > 1.4) & (y < line - 0.45) & (y > 23.9), "jaw")
    setl(in_head & (z > 1.4) & (y > line + 0.45) & (y > 25.4), "head")
    setl(in_head & (z <= 1.4) & (y >= 25.4), "head")
    setl((y >= 28.0) & (ax < 4.0), "head")
    setl(in_head & (y >= 23.5) & (y <= 23.7) & (z < 1.3), "neck_3")
    return lab


def harmonic_weights(P, quads):
    n = len(P)
    rows, cols = [], []
    for idx, _ in quads:
        for k in range(4):
            a, b = idx[k], idx[(k + 1) % 4]
            if a != b:
                rows += [a, b]
                cols += [b, a]
    A = sparse.coo_matrix((np.ones(len(rows)), (rows, cols)), shape=(n, n)).tocsr()
    A.data[:] = 1.0
    deg = np.asarray(A.sum(axis=1)).ravel()
    L = sparse.diags(deg) - A
    lab = core_labels(P)

    ncomp, comp = connected_components(A, directed=False)
    fixed = lab >= 0
    # Componentes sin ningun vertice fijo: se les asigna la etiqueta del vertice fijo mas cercano
    tree = cKDTree(P[fixed])
    fixed_idx = np.where(fixed)[0]
    comp_has = np.zeros(ncomp, bool)
    comp_has[np.unique(comp[fixed])] = True
    for c in np.where(~comp_has)[0]:
        members = np.where(comp == c)[0]
        _, nn = tree.query(P[members].mean(axis=0))
        lab[members] = lab[fixed_idx[nn]]
    fixed = lab >= 0
    free = ~fixed
    W = np.zeros((n, NB))
    W[fixed, lab[fixed]] = 1.0
    if free.any():
        fi = np.where(free)[0]
        ci = np.where(fixed)[0]
        Lff = L[fi][:, fi].tocsc()
        Lfc = L[fi][:, ci]
        lu = splu(Lff)
        rhs = -(Lfc @ W[ci])
        W[fi] = lu.solve(rhs)
    W = np.clip(W, 0, None)
    # Maximo 4 influencias por vertice
    out = np.zeros_like(W)
    top = np.argsort(-W, axis=1)[:, :4]
    for r in range(n):
        out[r, top[r]] = W[r, top[r]]
    out /= out.sum(axis=1, keepdims=True)
    return out, lab


# --------------------------------------------------------------------------------------
# Matematicas del esqueleto (identicas a las de Java)
# --------------------------------------------------------------------------------------
def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def local_matrix(pivot, rot_deg=(0, 0, 0), pos=(0, 0, 0), scale=(1, 1, 1)):
    """T(pivot+pos) * Rz * Ry * Rx * S * T(-pivot)"""
    rx, ry, rz = [math.radians(v) for v in rot_deg]
    R = rot_z(rz) @ rot_y(ry) @ rot_x(rx)
    M = np.eye(4)
    M[:3, :3] = R @ np.diag(scale)
    p = np.array(pivot, float)
    M[:3, 3] = p + np.array(pos, float) - M[:3, :3] @ p
    return M


def world_matrices(bones, pose):
    """bones: lista (nombre, padre, pivote). pose: dict nombre -> dict(rot,pos,scale)."""
    W = [None] * len(bones)
    idx = {b[0]: i for i, b in enumerate(bones)}
    for i, (name, parent, pivot) in enumerate(bones):
        p = pose.get(name, {})
        L = local_matrix(pivot, p.get("rot", (0, 0, 0)), p.get("pos", (0, 0, 0)), p.get("scale", (1, 1, 1)))
        W[i] = L if parent is None else W[idx[parent]] @ L
    return W


def skin(P, Wt, mats):
    """P (n,3), Wt (n,NB), mats lista de 4x4 -> posiciones deformadas"""
    Ph = np.hstack([P, np.ones((len(P), 1))])
    out = np.zeros_like(P)
    for b in range(NB):
        w = Wt[:, b]
        if not w.any():
            continue
        out += w[:, None] * (Ph @ mats[b].T)[:, :3]
    return out


# --------------------------------------------------------------------------------------
# Pantalla de la panza
# --------------------------------------------------------------------------------------
def detect_screen(P, quads, cols):
    sel = []
    for qi, (idx, uv) in enumerate(quads):
        c = P[idx].mean(axis=0)
        r, g, b = cols[qi]
        bright = (r + g + b) / 3
        if 10.5 <= c[1] <= 17.8 and abs(c[0]) < 4.5 and c[2] > 2.5 and bright > 185 and max(r, g, b) - min(r, g, b) < 40:
            sel.append(qi)
    return sel


# --------------------------------------------------------------------------------------
# Previsualizacion (proyeccion ortografica con relleno de color medio)
# --------------------------------------------------------------------------------------
def preview(P, quads, cols, fname, yaw=0.0, S=10, hl=None, size=(420, 520), cam_y=24.0):
    from PIL import ImageDraw
    c, s = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    Q = np.stack([P[:, 0] * c + P[:, 2] * s, P[:, 1], -P[:, 0] * s + P[:, 2] * c], axis=1)
    img = Image.new("RGB", size, (58, 58, 68))
    dr = ImageDraw.Draw(img)
    order = sorted(range(len(quads)), key=lambda q: np.mean(Q[quads[q][0], 2]))
    for q in order:
        idx = quads[q][0]
        pts = [(size[0] / 2 + Q[i, 0] * S, size[1] - 20 - (Q[i, 1]) * S) for i in idx]
        col = tuple(int(v) for v in cols[q])
        if hl is not None and q in hl:
            col = (255, 0, 0)
        dr.polygon(pts, fill=col)
    img.save(fname)


# --------------------------------------------------------------------------------------
def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    prev_dir = None
    if "--preview" in sys.argv:
        prev_dir = sys.argv[sys.argv.index("--preview") + 1]
        args = [a for a in args if a != prev_dir]
    bb, tex_path = args[0], args[1]
    res = args[2] if len(args) > 2 else os.path.join("src", "main", "resources")
    assets = os.path.join(res, "assets", "bearfriender")

    tex = Image.open(tex_path).convert("RGB")
    tex_small = tex.resize((TEX_SIZE, TEX_SIZE), Image.LANCZOS)
    P, quads, cols = load_mesh(bb, tex_small)
    print(f"vertices: {len(P)}  caras: {len(quads)}")

    Wt, lab = harmonic_weights(P, quads)
    print("vertices con etiqueta dura:", int((lab >= 0).sum()))

    # ---- Pose de reposo: brazos colgando ----
    pose = {}
    for side, sgn in (("right", +1), ("left", -1)):
        pose[f"arm_{side}"] = {"rot": (0, 0, sgn * ARM_HANG_DEG)}
    mats = world_matrices(BONES, pose)
    P_rest = skin(P, Wt, mats)
    bones_rest = []
    for i, (name, parent, pivot) in enumerate(BONES):
        pv = (mats[i] @ np.array([*pivot, 1.0]))[:3]
        bones_rest.append((name, parent, tuple(pv)))

    # ---- Escalado a 48 px de alto, pies en y=0 ----
    ymin, ymax = P_rest[:, 1].min(), P_rest[:, 1].max()
    sc = TARGET_HEIGHT / (ymax - ymin)
    off = np.array([0.0, ymin, 0.0])
    P_fin = (P_rest - off) * sc
    bones_fin = [(n, p, tuple((np.array(pv) - off) * sc)) for n, p, pv in bones_rest]
    print(f"escala {sc:.3f}; altura {P_fin[:,1].max():.2f}px; ancho x {P_fin[:,0].min():.1f}..{P_fin[:,0].max():.1f}")

    # ---- Normales por posicion (promedio de normales de cara) ----
    N = np.zeros_like(P_fin)
    for idx, _ in quads:
        a, b, c, d = [P_fin[i] for i in idx]
        nrm = np.cross(c - a, d - b)
        for i in set(idx):
            N[i] += nrm
    ln = np.linalg.norm(N, axis=1, keepdims=True)
    N = N / np.maximum(ln, 1e-9)
    # Orientar hacia fuera: comparar con el vector desde el eje del cuerpo
    centre = P_fin.copy()
    centre[:, 0] = 0
    centre[:, 2] = 0
    out_dir = P_fin - centre
    flip = np.sum(N * out_dir, axis=1) < 0
    # solo se decide por mayoria global (la malla es coherente)
    if flip.mean() > 0.5:
        N = -N
    print("normales invertidas globalmente:", bool(flip.mean() > 0.5), f"({flip.mean():.2f})")

    # ---- Pantalla: quads de cristal ----
    screen = detect_screen(P, quads, cols)
    print("quads de pantalla:", len(screen))
    sv = np.unique([i for q in screen for i in quads[q][0]])
    x0, x1 = P[sv, 0].min(), P[sv, 0].max()
    y0, y1 = P[sv, 1].min(), P[sv, 1].max()
    print(f"pantalla bbox x {x0:.2f}..{x1:.2f}  y {y0:.2f}..{y1:.2f}  (aspecto {(x1-x0)/(y1-y0):.2f})")
    screen_set = set(screen)

    # ---- Esquinas de quads: principales primero, pantalla al final ----
    main_q = [q for q in range(len(quads)) if q not in screen_set]
    scr_q = [q for q in range(len(quads)) if q in screen_set]
    order = main_q + scr_q

    mesh_dir = os.path.join(assets, "mesh")
    os.makedirs(mesh_dir, exist_ok=True)
    with open(os.path.join(mesh_dir, "bearfriender.mesh"), "wb") as f:
        f.write(struct.pack("<4sIIIII", b"BEAR", 1, len(P_fin), len(main_q), len(scr_q), NB))
        for i in range(len(P_fin)):
            ws = Wt[i]
            top = np.argsort(-ws)[:4]
            js = [int(t) for t in top]
            wv = [float(ws[t]) for t in top]
            f.write(struct.pack("<3f3f4B4f", *P_fin[i], *N[i], *js, *wv))
        for q in order:
            idx, uv = quads[q]
            for k in range(4):
                if q in screen_set:
                    pi = idx[k]
                    u = (P[pi, 0] - x0) / (x1 - x0)
                    v = (y1 - P[pi, 1]) / (y1 - y0)
                else:
                    u, v = uv[k][0] / 16.0, uv[k][1] / 16.0
                f.write(struct.pack("<Iff", idx[k], u, v))
    print("escrito mesh,", os.path.getsize(os.path.join(mesh_dir, "bearfriender.mesh")) // 1024, "KB")

    rig = {"bones": [{"name": n, "parent": p, "pivot": [round(float(v), 4) for v in pv]} for n, p, pv in bones_fin]}
    json.dump(rig, open(os.path.join(mesh_dir, "bearfriender.rig.json"), "w"), indent=2)

    # ---- Texturas ----
    tex_dir = os.path.join(assets, "textures", "entity")
    os.makedirs(tex_dir, exist_ok=True)
    tex_small.save(os.path.join(tex_dir, "bearfriender.png"), optimize=True)
    import bear_face
    import screens
    faces = bear_face.make_images(bear_face.portrait(bb, tex_path))
    screens.make_all(tex_dir, (x1 - x0) / (y1 - y0), faces)
    block_tex = os.path.join(assets, "textures", "block")        # televisores CRT del escenario
    os.makedirs(block_tex, exist_ok=True)
    faces["tv_face"].save(os.path.join(block_tex, "crt_screen_face.png"))
    faces["tv_close"].save(os.path.join(block_tex, "crt_screen_smile.png"))

    # ---- Animaciones ----
    import bear_anims
    anim_dir = os.path.join(assets, "animations", "entity")
    os.makedirs(anim_dir, exist_ok=True)
    json.dump({"format_version": "1.8.0", "animations": bear_anims.build()},
              open(os.path.join(anim_dir, "bearfriender.animation.json"), "w"), indent=1)

    if prev_dir:
        os.makedirs(prev_dir, exist_ok=True)
        np.save(os.path.join(prev_dir, "P_fin.npy"), P_fin)
        np.save(os.path.join(prev_dir, "Wt.npy"), Wt)
        json.dump({"bones": [list(map(lambda v: v, b)) for b in bones_fin]},
                  open(os.path.join(prev_dir, "bones.json"), "w"), default=lambda o: list(o))
        np.save(os.path.join(prev_dir, "cols.npy"), cols)
        json.dump([q[0] for q in quads], open(os.path.join(prev_dir, "quads.json"), "w"))
        json.dump(sorted(screen), open(os.path.join(prev_dir, "screen.json"), "w"))
        np.save(os.path.join(prev_dir, "lab.npy"), lab)
        np.save(os.path.join(prev_dir, "P_T.npy"), P)


if __name__ == "__main__":
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    main()
