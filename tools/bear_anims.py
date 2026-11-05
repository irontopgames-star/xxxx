"""Animaciones de Bearfriender para el esqueleto propio (ver build_bear.py para las convenciones).

Formato (compatible con el lector de Java):
  { nombre: { "loop": true | false | "hold_on_last_frame", "animation_length": s,
              "bones": { hueso: { "rotation": {t: [x,y,z]}, "position": {...}, "scale": {...} } } } }
Rotaciones en grados, posiciones en px. Los huesos que una animacion no menciona se quedan en reposo.
"""
import math

PI = math.pi
Z = [0, 0, 0]
ONE = [1, 1, 1]


def K(*frames):
    return {str(round(float(t), 4)): [round(float(c), 3) for c in v] for t, v in frames}


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
    """Oscilacion senoidal: el ultimo fotograma coincide con el primero (bucle limpio)."""
    pts = []
    for i in range(n + 1):
        s = math.sin(2 * PI * cycles * i / n + phase)
        pts.append((length * i / n, [base[k] + amp[k] * s for k in range(3)]))
    return pts


def hold(length, v):
    return [(0, v), (length, v)]


def anim(length, loop, bones):
    return {"loop": loop, "animation_length": length, "bones": bones}


def shake(t0, t1, amp, step=0.07, axis=0):
    pts, t, sign = [(0, Z)], t0, 1
    while t <= t1:
        v = [0, 0, 0]
        v[axis] = amp * sign
        pts.append((t, v))
        sign = -sign
        t += step
    pts.append((t1 + step, Z))
    return pts


def build():
    A = {}

    A["idle"] = anim(4, True, {
        "chest": B(rot=osc(4, (1.2, 1.5, 0.6))),
        "neck_1": B(rot=osc(4, (3, 4, 1.5), 0.5)),
        "neck_2": B(rot=osc(4, (3, -6, 2), 1.2), scale=osc(4, (0, 0.10, 0), 0.8, (1, 1.12, 1))),
        "neck_3": B(rot=osc(4, (2, 5, -2), 2.0), scale=osc(4, (0, 0.05, 0), 1.5, (1, 1.05, 1))),
        "head": B(rot=osc(4, (3, 8, 3), 2.8)),
        "arm_right": B(rot=osc(4, (2, 0, 1), 0.3)),
        "arm_left": B(rot=osc(4, (2, 0, -1), 1.3)),
        "forearm_right": B(rot=hold(4, [-5, 0, 0])),
        "forearm_left": B(rot=hold(4, [-5, 0, 0])),
        "jaw": B(rot=osc(4, (1.5, 0, 0), 0, (3, 0, 0))),
    })

    A["walk"] = anim(1.6, True, {
        "leg_right": B(rot=osc(1.6, (26, 0, 0))),
        "leg_left": B(rot=osc(1.6, (26, 0, 0), PI)),
        "arm_right": B(rot=osc(1.6, (18, 0, 0), PI)),
        "arm_left": B(rot=osc(1.6, (18, 0, 0))),
        "forearm_right": B(rot=osc(1.6, (6, 0, 0), PI, (-10, 0, 0))),
        "forearm_left": B(rot=osc(1.6, (6, 0, 0), 0, (-10, 0, 0))),
        "torso": B(rot=osc(1.6, (0, 5, 0))),
        "chest": B(rot=osc(1.6, (1, -4, 0), 0, (4, 0, 0))),
        "neck_1": B(rot=osc(1.6, (3, 0, 3), PI / 2, (4, 0, 0))),
        "neck_2": B(scale=hold(1.6, [1, 1.15, 1])),
        "head": B(rot=osc(1.6, (2, 6, 0), PI)),
        "root": B(pos=osc(1.6, (0, 0.7, 0), 0, (0, 0, 0), cycles=2)),
        "jaw": B(rot=osc(1.6, (2, 0, 0), 0, (4, 0, 0))),
    })

    A["stalk"] = anim(2.4, True, {
        "root": B(pos=osc(2.4, (0, 0.4, 0), 0, (0, -3, 0))),
        "torso": B(rot=osc(2.4, (2, 3, 0), 0, (24, 0, 0))),
        "chest": B(rot=hold(2.4, [10, 0, 0])),
        "leg_right": B(rot=osc(2.4, (14, 0, 0), 0, (-20, 0, 0))),
        "leg_left": B(rot=osc(2.4, (14, 0, 0), PI, (-20, 0, 0))),
        "arm_right": B(rot=osc(2.4, (8, 0, 0), PI, (-18, 0, 0))),
        "arm_left": B(rot=osc(2.4, (8, 0, 0), 0, (-18, 0, 0))),
        "forearm_right": B(rot=hold(2.4, [-30, 0, 0])),
        "forearm_left": B(rot=hold(2.4, [-30, 0, 0])),
        "neck_1": B(rot=osc(2.4, (3, 4, 0), 0, (14, 0, 0))),
        "neck_2": B(rot=osc(2.4, (3, -5, 0), 0.8, (8, 0, 0)), scale=hold(2.4, [1, 1.3, 1])),
        "neck_3": B(rot=osc(2.4, (2, 4, 0), 1.6, (-12, 0, 0)), scale=hold(2.4, [1, 1.15, 1])),
        "head": B(rot=osc(2.4, (0, 8, 3), 0, (-32, 0, 0))),
        "jaw": B(rot=osc(2.4, (3, 0, 0), 0, (8, 0, 0))),
    })

    A["look_around"] = anim(6, True, {
        "neck_1": B(rot=[(0, Z), (1, [0, 35, 0]), (2.2, [0, 35, 0]), (3, [0, -35, 0]), (4.5, [0, -35, 0]), (6, Z)]),
        "neck_2": B(rot=[(0, Z), (1.1, [4, 28, 3]), (2.3, [4, 28, 3]), (3.1, [4, -28, -3]), (4.6, [4, -28, -3]), (6, Z)],
                    scale=hold(6, [1, 1.3, 1])),
        "neck_3": B(rot=[(0, Z), (1.2, [0, 25, 5]), (2.4, [0, 25, 5]), (3.2, [0, -25, -5]), (4.7, [0, -25, -5]), (6, Z)]),
        "head": B(rot=[(0, Z), (1.3, [-5, 20, 8]), (2.5, [-5, 20, 8]), (3.3, [-5, -20, -8]), (4.8, [-5, -20, -8]), (6, Z)]),
        "chest": B(rot=osc(6, (0, 3, 0))),
        "jaw": B(rot=osc(6, (1.5, 0, 0), 0, (3, 0, 0))),
    })

    A["peek"] = anim(3, False, {
        "chest": B(rot=[(0, Z), (0.6, [0, 0, 16]), (2.2, [0, 0, 16]), (3, Z)]),
        "neck_1": B(rot=[(0, Z), (0.6, [8, 0, 35]), (2.2, [8, 0, 35]), (3, Z)]),
        "neck_2": B(rot=[(0, Z), (0.7, [6, 0, 28]), (2.2, [6, 0, 28]), (3, Z)],
                    scale=[(0, ONE), (0.8, [1, 1.7, 1]), (2.2, [1, 1.7, 1]), (3, ONE)]),
        "neck_3": B(rot=[(0, Z), (0.8, [0, 0, 18]), (2.2, [0, 0, 18]), (3, Z)]),
        "head": B(rot=[(0, Z), (0.9, [0, 0, -30]), (1.5, [0, 8, -30]), (2.2, [0, 8, -30]), (3, Z)]),
        "jaw": B(rot=[(0, Z), (0.9, [10, 0, 0]), (2.2, [10, 0, 0]), (3, Z)]),
    })

    A["crouch"] = anim(3, True, {
        "root": B(pos=osc(3, (0, 0.3, 0), 0, (0, -9, 0))),
        "leg_right": B(rot=hold(3, [-75, 0, 0])),
        "leg_left": B(rot=hold(3, [-75, 0, 0])),
        "arm_right": B(rot=osc(3, (3, 0, 0), 0, (-35, 0, 0))),
        "arm_left": B(rot=osc(3, (3, 0, 0), PI, (-35, 0, 0))),
        "forearm_right": B(rot=hold(3, [-40, 0, 0])),
        "forearm_left": B(rot=hold(3, [-40, 0, 0])),
        "torso": B(rot=osc(3, (2, 0, 0), 0, (22, 0, 0))),
        "chest": B(rot=hold(3, [14, 0, 0])),
        "neck_1": B(rot=osc(3, (2, 3, 0), 0, (18, 0, 0))),
        "neck_2": B(rot=osc(3, (2, -3, 0), 1, (10, 0, 0))),
        "neck_3": B(rot=hold(3, [-12, 0, 0])),
        "head": B(rot=osc(3, (0, 10, 0), 0, (-22, 0, 0))),
    })

    A["crawl"] = anim(1.2, True, {
        "root": B(pos=osc(1.2, (0, 0.5, 0), 0, (0, -9, 0), cycles=2)),
        "leg_right": B(rot=osc(1.2, (14, 0, 0), 0, (-70, 0, 0))),
        "leg_left": B(rot=osc(1.2, (14, 0, 0), PI, (-70, 0, 0))),
        "torso": B(rot=osc(1.2, (0, 5, 0), 0, (40, 0, 0))),
        "chest": B(rot=hold(1.2, [18, 0, 0])),
        "arm_right": B(rot=osc(1.2, (28, 0, 0), PI, (-75, 0, 0))),
        "arm_left": B(rot=osc(1.2, (28, 0, 0), 0, (-75, 0, 0))),
        "forearm_right": B(rot=hold(1.2, [-20, 0, 0])),
        "forearm_left": B(rot=hold(1.2, [-20, 0, 0])),
        "neck_1": B(rot=hold(1.2, [-10, 0, 0])),
        "neck_2": B(rot=osc(1.2, (4, 8, 0), 0, (-18, 0, 0)), scale=hold(1.2, [1, 1.3, 1])),
        "neck_3": B(rot=hold(1.2, [-16, 0, 0])),
        "head": B(rot=osc(1.2, (0, 10, 0), 0, (-18, 0, 0))),
        "jaw": B(rot=osc(1.2, (4, 0, 0), 0, (10, 0, 0))),
    })

    A["run"] = anim(0.8, True, {
        "leg_right": B(rot=osc(0.8, (50, 0, 0))),
        "leg_left": B(rot=osc(0.8, (50, 0, 0), PI)),
        "arm_right": B(rot=osc(0.8, (55, 0, 0), PI)),
        "arm_left": B(rot=osc(0.8, (55, 0, 0))),
        "forearm_right": B(rot=osc(0.8, (20, 0, 0), PI, (-40, 0, 0))),
        "forearm_left": B(rot=osc(0.8, (20, 0, 0), 0, (-40, 0, 0))),
        "torso": B(rot=osc(0.8, (0, 8, 0), 0, (18, 0, 0))),
        "chest": B(rot=hold(0.8, [8, 0, 0])),
        "neck_1": B(rot=osc(0.8, (3, 0, 4), 0, (14, 0, 0))),
        "neck_2": B(rot=osc(0.8, (3, 0, -4), 1, (6, 0, 0)), scale=hold(0.8, [1, 1.2, 1])),
        "head": B(rot=osc(0.8, (2, 0, 0), 0, (-28, 0, 0))),
        "root": B(pos=osc(0.8, (0, 1.5, 0), 0, (0, 0, 0), cycles=2)),
        "jaw": B(rot=osc(0.8, (4, 0, 0), 0, (10, 0, 0), cycles=2)),
    })

    A["stunned"] = anim(3, True, {
        "chest": B(rot=osc(3, (3, 0, 4), 0, (14, 0, 0))),
        "torso": B(rot=hold(3, [8, 0, 0])),
        "neck_1": B(rot=osc(3, (3, 0, 5), 0, (30, 0, 0))),
        "neck_2": B(rot=osc(3, (3, 0, -5), 1, (22, 0, 0))),
        "neck_3": B(rot=hold(3, [14, 0, 0])),
        "head": B(rot=osc(3, (0, 6, 8), 0, (20, 0, 0))),
        "arm_right": B(rot=osc(3, (4, 0, 2), 0, (-8, 0, 0))),
        "arm_left": B(rot=osc(3, (4, 0, -2), PI, (-8, 0, 0))),
        "jaw": B(rot=hold(3, [8, 0, 0])),
    })

    # Momento de impacto del ataque: 0.6 s (tick 12)
    A["attack"] = anim(1.2, False, {
        "arm_right": B(rot=[(0, Z), (0.35, [70, 0, -10]), (0.6, [-100, 0, 8]), (0.85, [-85, 0, 4]), (1.2, Z)]),
        "forearm_right": B(rot=[(0, Z), (0.35, [-45, 0, 0]), (0.6, [-10, 0, 0]), (1.2, Z)]),
        "chest": B(rot=[(0, Z), (0.35, [-5, -28, 0]), (0.6, [14, 28, 0]), (1.2, Z)]),
        "neck_1": B(rot=[(0, Z), (0.35, [-8, 0, 0]), (0.6, [20, 0, 0]), (1.2, Z)]),
        "head": B(rot=[(0, Z), (0.6, [12, 0, 0]), (1.2, Z)]),
        "jaw": B(rot=[(0, Z), (0.5, [14, 0, 0]), (0.7, [14, 0, 0]), (1.2, Z)]),
    })

    # Momento de agarre: 0.9 s (tick 18)
    A["grab"] = anim(1.8, False, {
        "arm_right": B(rot=[(0, Z), (0.5, [-95, 0, -14]), (0.9, [-95, 0, 12]), (1.3, [-95, 0, 12]), (1.8, Z)]),
        "arm_left": B(rot=[(0, Z), (0.5, [-95, 0, 14]), (0.9, [-95, 0, -12]), (1.3, [-95, 0, -12]), (1.8, Z)]),
        "forearm_right": B(rot=[(0, Z), (0.9, [-15, 0, 0]), (1.8, Z)]),
        "forearm_left": B(rot=[(0, Z), (0.9, [-15, 0, 0]), (1.8, Z)]),
        "hand_right": B(rot=[(0, Z), (0.5, Z), (0.9, [-45, 0, 0]), (1.3, [-45, 0, 0]), (1.8, Z)]),
        "hand_left": B(rot=[(0, Z), (0.5, Z), (0.9, [-45, 0, 0]), (1.3, [-45, 0, 0]), (1.8, Z)]),
        "chest": B(rot=[(0, Z), (0.9, [20, 0, 0]), (1.8, Z)]),
        "neck_1": B(rot=[(0, Z), (0.9, [18, 0, 0]), (1.8, Z)]),
        "head": B(rot=[(0, Z), (0.9, [10, 0, 0]), (1.8, Z)]),
        "jaw": B(rot=[(0, Z), (0.9, [16, 0, 0]), (1.3, [16, 0, 0]), (1.8, Z)]),
    })

    A["missed_attack"] = anim(1.6, False, {
        "arm_right": B(rot=[(0, Z), (0.4, [60, 0, -8]), (0.6, [-110, 0, 8]), (1.0, [-80, 0, 8]), (1.6, Z)]),
        "chest": B(rot=[(0, Z), (0.6, [8, 25, 0]), (0.9, [30, 10, 0]), (1.6, Z)]),
        "torso": B(rot=[(0, Z), (0.9, [14, 0, 0]), (1.6, Z)]),
        "neck_1": B(rot=[(0, Z), (0.9, [30, 0, 0]), (1.6, Z)]),
        "head": B(rot=[(0, Z), (0.9, [25, -20, 0]), (1.6, Z)]),
        "leg_right": B(rot=[(0, Z), (0.9, [-28, 0, 0]), (1.6, Z)]),
    })

    A["tv_control"] = anim(3, False, {
        "arm_right": B(rot=[(0, Z), (0.5, [-85, 0, -14]), (2.5, [-85, 0, -14]), (3, Z)]),
        "arm_left": B(rot=[(0, Z), (0.5, [-85, 0, 14]), (2.5, [-85, 0, 14]), (3, Z)]),
        "hand_right": B(rot=[(0, Z), (0.5, Z), (0.9, [-35, 0, 0]), (1.3, [10, 0, 0]), (1.7, [-35, 0, 0]), (2.1, [10, 0, 0]), (2.5, Z), (3, Z)]),
        "hand_left": B(rot=[(0, Z), (0.5, Z), (0.9, [10, 0, 0]), (1.3, [-35, 0, 0]), (1.7, [10, 0, 0]), (2.1, [-35, 0, 0]), (2.5, Z), (3, Z)]),
        "head": B(rot=[(0, Z), (0.6, [-10, 0, 12]), (2.4, [-10, 0, 12]), (3, Z)]),
        "neck_1": B(rot=[(0, Z), (0.6, [8, 0, 0]), (2.4, [8, 0, 0]), (3, Z)]),
        "neck_2": B(scale=[(0, ONE), (0.8, [1, 1.35, 1]), (2.4, [1, 1.35, 1]), (3, ONE)]),
        "jaw": B(rot=[(0, Z), (0.8, [10, 0, 0]), (2.4, [10, 0, 0]), (3, Z)]),
    })

    A["scream"] = anim(2.4, False, {
        "jaw": B(rot=[(0, Z), (0.3, [22, 0, 0]), (2.0, [22, 0, 0]), (2.4, Z)]),
        "head": B(rot=[(0, Z), (0.3, [-28, 0, 0]), (2.0, [-28, 0, 0]), (2.4, Z)]),
        "neck_1": B(rot=[(0, Z), (0.3, [-10, 0, 0]), (2.0, [-10, 0, 0]), (2.4, Z)]),
        "neck_2": B(rot=[(0, Z), (0.3, [-16, 0, 0]), (2.0, [-16, 0, 0]), (2.4, Z)],
                    scale=[(0, ONE), (0.4, [1, 1.5, 1]), (1.9, [1, 1.5, 1]), (2.4, ONE)]),
        "arm_right": B(rot=[(0, Z), (0.3, [-12, 0, -25]), (2.0, [-12, 0, -25]), (2.4, Z)]),
        "arm_left": B(rot=[(0, Z), (0.3, [-12, 0, 25]), (2.0, [-12, 0, 25]), (2.4, Z)]),
        "chest": B(pos=shake(0.4, 2.0, 0.4)),
    })

    A["damage"] = anim(0.6, False, {
        "chest": B(rot=[(0, Z), (0.1, [-14, 0, 0]), (0.6, Z)]),
        "head": B(rot=[(0, Z), (0.1, [18, 0, 0]), (0.6, Z)]),
        "neck_1": B(rot=[(0, Z), (0.1, [10, 0, 6]), (0.6, Z)]),
        "jaw": B(rot=[(0, Z), (0.1, [14, 0, 0]), (0.6, Z)]),
    })

    A["phase_transition"] = anim(3.5, False, {
        "arm_right": B(rot=[(0, Z), (0.6, [-15, 0, -70]), (2.8, [-15, 0, -70]), (3.5, Z)]),
        "arm_left": B(rot=[(0, Z), (0.6, [-15, 0, 70]), (2.8, [-15, 0, 70]), (3.5, Z)]),
        "head": B(rot=[(0, Z), (0.6, [-30, 0, 0]), (2.8, [-30, 0, 0]), (3.5, Z)]),
        "jaw": B(rot=[(0, Z), (0.6, [20, 0, 0]), (2.8, [20, 0, 0]), (3.5, Z)]),
        "neck_2": B(scale=[(0, ONE), (1.0, [1, 1.5, 1]), (2.8, [1, 1.5, 1]), (3.5, ONE)]),
        "chest": B(pos=shake(0.6, 2.8, 0.5, 0.06)),
    })

    A["death"] = anim(3, "hold_on_last_frame", {
        "chest": B(rot=[(0, Z), (1.0, [30, 0, 0]), (2.2, [50, 0, 0])]),
        "torso": B(rot=[(0, Z), (1.0, [20, 0, 0])]),
        "neck_1": B(rot=[(0, Z), (1.0, [35, 0, 0])]),
        "neck_2": B(rot=[(0, Z), (1.0, [35, 0, 0])]),
        "neck_3": B(rot=[(0, Z), (1.0, [25, 0, 0])]),
        "head": B(rot=[(0, Z), (1.0, [40, 0, 0])]),
        "arm_right": B(rot=[(0, Z), (1.0, [18, 0, -10])]),
        "arm_left": B(rot=[(0, Z), (1.0, [18, 0, 10])]),
        "jaw": B(rot=[(0, Z), (0.5, [14, 0, 0])]),
        "root": B(rot=[(0, Z), (1.2, Z), (2.4, [82, 0, 0])], pos=[(0, Z), (1.2, Z), (2.4, [0, 4, -4])]),
    })
    return A
