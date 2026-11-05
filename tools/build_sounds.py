#!/usr/bin/env python3
"""
Sintetiza los sonidos originales del mod (sin derechos de terceros) y escribe:
  assets/bearfriender/sounds/<nombre>_<n>.ogg
  assets/bearfriender/sounds.json
  (y los subtitulos en lang/es_es.json y en_us.json)

Uso: python tools/build_sounds.py [carpeta_resources]
Requiere numpy, scipy y ffmpeg (con libvorbis) en el PATH.

PARA USAR TUS PROPIOS SONIDOS: reemplaza cualquier .ogg de assets/bearfriender/sounds/ conservando el nombre
(mono, 44100 Hz, .ogg Vorbis). Si quieres mas variantes, anade archivos y lista sus nombres en sounds.json.
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy import signal

RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "bearfriender")
SND_DIR = os.path.join(ASSETS, "sounds")
SR = 44100


# ----------------------------------------------------------------------------- utilidades
def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def noise(n, rng):
    return rng.standard_normal(n)


def lp(x, hz, order=4):
    return signal.sosfilt(signal.butter(order, hz, "low", fs=SR, output="sos"), x)


def hp(x, hz, order=4):
    return signal.sosfilt(signal.butter(order, hz, "high", fs=SR, output="sos"), x)


def bp(x, lo, hi, order=3):
    return signal.sosfilt(signal.butter(order, [lo, hi], "band", fs=SR, output="sos"), x)


def sweep(f0, f1, dur, kind="exp"):
    t = t_axis(dur)
    if kind == "exp":
        f = f0 * (f1 / f0) ** (t / dur)
    else:
        f = f0 + (f1 - f0) * t / dur
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def saw(f):
    ph = np.cumsum(f) / SR
    return 2 * (ph - np.floor(ph + 0.5))


def decay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def fade(x, a=0.005, b=0.02):
    n = len(x)
    na, nb = int(a * SR), int(b * SR)
    if na > 0:
        x[:na] *= np.linspace(0, 1, na)
    if nb > 0:
        x[-nb:] *= np.linspace(1, 0, nb)
    return x


def mix(*parts, length=None):
    n = length or max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p[:n]
    return out


def place(x, at, total):
    out = np.zeros(total)
    i = int(at * SR)
    m = min(len(x), total - i)
    if m > 0:
        out[i:i + m] = x[:m]
    return out


def echo(x, delays_gains):
    n = len(x) + int(max(d for d, _ in delays_gains) * SR) + 1
    out = np.zeros(n)
    out[:len(x)] += x
    for d, g in delays_gains:
        i = int(d * SR)
        out[i:i + len(x)] += x * g
    return out


def distort(x, k):
    return np.tanh(x * k) / np.tanh(k)


def bitcrush(x, bits, down):
    q = 2 ** bits
    y = np.round(x * q) / q
    return np.repeat(y[::down], down)[:len(x)]


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def resonator(x, f, bw):
    return bp(x, max(40, f - bw / 2), min(SR / 2 - 100, f + bw / 2), order=2)


# ----------------------------------------------------------------------------- sonidos
def tv_on(rng):
    dur = 0.9
    t = t_axis(dur)
    thump = np.sin(2 * np.pi * 55 * t) * decay(len(t), 0.07) * 0.9
    whine = sweep(1200, 9500, 0.45) * np.hanning(int(0.45 * SR)) ** 0.5 * 0.35
    hiss = hp(noise(int(0.25 * SR), rng), 3000) * decay(int(0.25 * SR), 0.06) * 0.5
    flyback = np.sin(2 * np.pi * 11800 * t) * 0.05 * np.minimum(1, t / 0.2) * decay(len(t), 0.5)
    hum = np.sin(2 * np.pi * 50 * t) * 0.12 * decay(len(t), 0.4)
    return norm(fade(mix(thump, whine, hiss, flyback, hum, length=len(t))))


def tv_off(rng):
    dur = 0.55
    t = t_axis(dur)
    zap = sweep(7000, 180, 0.28) * decay(int(0.28 * SR), 0.12) * 0.6
    click = hp(noise(int(0.03 * SR), rng), 1500) * decay(int(0.03 * SR), 0.008) * 0.8
    pop = np.sin(2 * np.pi * 70 * t) * decay(len(t), 0.05) * 0.7
    return norm(fade(mix(zap, click, pop, length=len(t))))


def tv_static(rng, variant):
    dur = 1.3
    n = int(dur * SR)
    x = bp(noise(n, rng), 900, 9000)
    seg = int(0.02 * SR)
    gain = np.repeat(rng.uniform(0.25, 1.0, n // seg + 1), seg)[:n]
    x = x * gain
    x += hp(noise(n, rng), 5000) * (rng.random(n) < 0.003) * 1.5          # chasquidos sueltos
    env = np.minimum(1, t_axis(dur) / 0.05) * np.minimum(1, (dur - t_axis(dur)) / 0.25)
    return norm(x * env, 0.7)


def tv_face(rng):
    dur = 3.2
    t = t_axis(dur)
    swell = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
    drone = (np.sin(2 * np.pi * 55 * t) + 0.7 * np.sin(2 * np.pi * 58.7 * t) + 0.4 * np.sin(2 * np.pi * 110 * t)) * swell
    rumble = lp(noise(len(t), rng), 300) * 2.0 * swell
    metal = np.sin(2 * np.pi * (1790 + 25 * np.sin(2 * np.pi * 4.3 * t)) * t) * 0.07 * swell
    dive = distort(sweep(900, 260, dur), 3) * 0.12 * np.sin(np.pi * np.clip(t / dur, 0, 1))
    hit = np.sin(2 * np.pi * 48 * t) * decay(len(t), 0.18) * 0.9 * (t < 1.0)
    return norm(fade(mix(drone * 0.6, rumble * 0.5, metal, dive, hit, length=len(t)), 0.01, 0.4))


def tv_scare_off(rng):
    dur = 0.8
    t = t_axis(dur)
    burst = hp(noise(len(t), rng), 800) * decay(len(t), 0.07)
    zap = distort(sweep(3500, 70, 0.4), 4) * decay(int(0.4 * SR), 0.15)
    thump = np.sin(2 * np.pi * 45 * t) * decay(len(t), 0.12) * 1.1
    return norm(fade(mix(burst * 0.7, zap * 0.7, thump, length=len(t))))


def voice(rng, f0, syll, vowels, slow=1.0):
    """Risita distorsionada: pulsos glotales filtrados por formantes, tipo "ha-ha-ha" que se rompe."""
    total = syll * 0.2 * slow + 0.5
    n = int(total * SR)
    out = np.zeros(n)
    f1f2 = {"a": (800, 1250), "e": (500, 1900), "o": (450, 800)}
    for i in range(syll):
        start = i * 0.2 * slow + 0.04 * rng.random()
        d = 0.13 * slow
        m = int(d * SR)
        f = np.linspace(f0 * (1.0 - 0.07 * i), f0 * (0.88 - 0.07 * i), m) * (1 + 0.02 * np.sin(2 * np.pi * 30 * np.arange(m) / SR))
        src = saw(f) * 0.6
        v = vowels[i % len(vowels)]
        a, b = f1f2[v]
        voiced = resonator(src, a, 180) * 1.6 + resonator(src, b, 260) * 1.2 + resonator(src, 2900, 400) * 0.5
        h = bp(noise(int(0.04 * SR), rng), 1500, 6000)
        env = np.minimum(1, np.arange(m) / (0.012 * SR)) * decay(m, 0.09 * slow)
        syl = voiced * env
        syl[:len(h)] += h * 0.5
        out += place(syl, start, n)
    out = bitcrush(out / (np.max(np.abs(out)) or 1), 7, 3)
    out = out * (0.6 + 0.4 * np.sin(2 * np.pi * 38 * t_axis(total)[:len(out)]))          # modulacion de anillo
    out = distort(lp(out, 5500), 2.5)
    out = echo(out, [(0.13, 0.35), (0.31, 0.2)])
    return norm(fade(out, 0.005, 0.15), 0.85)


def tv_voice(rng, variant):
    params = [(190, 4, "aeae", 1.0), (150, 5, "aaea", 0.9), (230, 3, "aoe", 1.2)][variant % 3]
    return voice(rng, params[0], params[1], params[2], params[3])


def bear_growl(rng, variant, big=False):
    dur = 3.0 if big else 2.0
    t = t_axis(dur)
    glide = np.linspace(75, 42, len(t)) if big else np.linspace(62 + 6 * variant, 48, len(t))
    f = glide * (1 + 0.03 * np.sin(2 * np.pi * (5.5 + variant) * t))
    src = saw(f)
    voiced = resonator(src, 380, 200) * 1.5 + resonator(src, 850, 300) * 1.0 + lp(src, 140) * 1.4
    rough = lp(noise(len(t), rng), 1400) * (0.5 + 0.5 * np.sin(2 * np.pi * 26 * t)) * 0.8
    sub = np.sin(2 * np.pi * np.cumsum(f * 0.5) / SR) * 0.9
    env = np.minimum(1, t / 0.12) * np.minimum(1, (dur - t) / (0.7 if big else 0.4))
    x = distort(mix(voiced * 0.7, rough * 0.5, sub * 0.8, length=len(t)) * env, 3.5 if big else 2.5)
    return norm(fade(x, 0.01, 0.3), 0.4 if big else 0.7)


def bear_scream(rng):
    dur = 1.7
    t = t_axis(dur)
    f = (350 + 500 * np.sin(np.pi * np.clip(t / dur, 0, 1))) * (1 + 0.04 * np.sin(2 * np.pi * 7 * t))
    src = saw(f)
    v = resonator(src, 1400, 500) * 1.2 + resonator(src, 3000, 800) * 0.8 + resonator(src, 650, 300)
    x = distort(v + hp(noise(len(t), rng), 2500) * 0.25, 6)
    env = np.minimum(1, t / 0.05) * np.minimum(1, (dur - t) / 0.35)
    return norm(fade(x * env, 0.005, 0.3), 0.4)


def door_slam(rng, variant):
    dur = 1.1
    t = t_axis(dur)
    f0 = 95 - 10 * variant
    thump = sweep(f0, 32, 0.35) * decay(int(0.35 * SR), 0.11) * 1.2
    body = bp(noise(len(t), rng), 120, 2200) * decay(len(t), 0.07)
    rattle = np.zeros(len(t))
    for at, g in ((0.12, 0.5), (0.21, 0.35), (0.33, 0.25)):
        k = bp(noise(int(0.05 * SR), rng), 300, 3000) * decay(int(0.05 * SR), 0.015) * g
        rattle += place(k, at, len(t))
    x = mix(thump, body * 0.8, rattle, length=len(t))
    return norm(fade(echo(x, [(0.09, 0.25), (0.2, 0.12)]), 0.001, 0.2), 0.95)


def knock(rng, variant):
    dur = 0.4
    t = t_axis(dur)
    f = 150 + 25 * variant
    x = (np.sin(2 * np.pi * f * t) * decay(len(t), 0.05) + 0.6 * np.sin(2 * np.pi * f * 2.03 * t) * decay(len(t), 0.03)
         + 0.35 * np.sin(2 * np.pi * f * 3.4 * t) * decay(len(t), 0.02))
    x += bp(noise(len(t), rng), 800, 4000) * decay(len(t), 0.004) * 0.7
    return norm(fade(x, 0.0005, 0.08), 0.9)


def step_heavy(rng, variant):
    dur = 0.55
    t = t_axis(dur)
    f = 68 + 6 * variant
    thump = np.sin(2 * np.pi * f * t) * decay(len(t), 0.09)
    scuff = lp(noise(len(t), rng), 500) * decay(len(t), 0.06) * 0.8
    click = bp(noise(len(t), rng), 600, 2500) * decay(len(t), 0.01) * 0.3
    return norm(fade(mix(thump, scuff, click, length=len(t)), 0.001, 0.1), 0.95)


def blackout(rng):
    dur = 2.6
    t = t_axis(dur)
    down = distort(lp(saw(np.geomspace(500, 32, len(t))), 900), 2) * decay(len(t), 0.9)
    rumble = lp(noise(len(t), rng), 400) * decay(len(t), 0.8) * 0.8
    thump = np.sin(2 * np.pi * 42 * t) * decay(len(t), 0.25) * 0.8
    zap = hp(noise(int(0.15 * SR), rng), 2000) * decay(int(0.15 * SR), 0.03) * 0.6
    return norm(fade(mix(down * 0.7, rumble * 0.5, thump, zap, length=len(t)), 0.002, 0.5))


def light_zap(rng, variant):
    dur = 0.8
    t = t_axis(dur)
    buzz = sum(np.sin(2 * np.pi * 100 * k * t) / k for k in range(1, 8)) * 0.4
    seg = int(0.012 * SR)
    gate = np.repeat((rng.random(len(t) // seg + 1) < 0.65).astype(float), seg)[:len(t)]
    crackle = hp(noise(len(t), rng), 2500) * (rng.random(len(t)) < 0.01) * 3
    x = (buzz * gate + crackle) * np.minimum(1, t / 0.01) * decay(len(t), 0.4 + 0.1 * variant)
    return norm(fade(x, 0.002, 0.15), 0.8)


def exit_open(rng):
    dur = 3.2
    t = t_axis(dur)
    rumble = lp(noise(len(t), rng), 130) * 3 * np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 0.7
    creak_f = np.linspace(260, 520, len(t)) * (1 + 0.05 * np.sin(2 * np.pi * 9 * t))
    creak = bp(saw(creak_f), 500, 2200) * 1.2 * np.sin(np.pi * np.clip((t - 0.3) / 2.4, 0, 1))
    clunk1 = place(np.sin(2 * np.pi * 55 * t_axis(0.5)) * decay(int(0.5 * SR), 0.1), 0.15, len(t))
    clunk2 = place(np.sin(2 * np.pi * 48 * t_axis(0.6)) * decay(int(0.6 * SR), 0.14), 2.5, len(t)) * 1.1
    return norm(fade(mix(rumble * 0.6, creak * 0.5, clunk1, clunk2, length=len(t)), 0.01, 0.4))


# nombre del evento -> (funcion, variantes, subtitulo es, subtitulo en)
SOUNDS = {
    "tv_on": (lambda r, v: tv_on(r), 1, "Un televisor se enciende", "A television turns on"),
    "tv_off": (lambda r, v: tv_off(r), 1, "Un televisor se apaga", "A television turns off"),
    "tv_static": (tv_static, 2, "Estática", "Static"),
    "tv_face": (lambda r, v: tv_face(r), 1, "Algo aparece en la pantalla", "Something appears on the screen"),
    "tv_scare_off": (lambda r, v: tv_scare_off(r), 1, "La pantalla se apaga de golpe", "The screen cuts out"),
    "tv_voice": (tv_voice, 3, "Una voz distorsionada", "A distorted voice"),
    "bear_growl": (bear_growl, 2, "Un gruñido grave", "A low growl"),
    "bear_roar": (lambda r, v: bear_growl(r, 0, True), 1, "Bearfriender ruge", "Bearfriender roars"),
    "bear_scream": (lambda r, v: bear_scream(r), 1, "Bearfriender grita", "Bearfriender screams"),
    "door_slam": (door_slam, 2, "Una puerta se cierra de golpe", "A door slams shut"),
    "knock": (knock, 3, "Golpes en una puerta", "Knocking"),
    "step_heavy": (step_heavy, 3, "Pasos pesados", "Heavy footsteps"),
    "blackout": (lambda r, v: blackout(r), 1, "Se va la luz", "The power goes out"),
    "light_zap": (light_zap, 2, "Las luces parpadean", "The lights flicker"),
    "exit_open": (lambda r, v: exit_open(r), 1, "Se abre la salida", "The exit opens"),
}


def write_ogg(x, path):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    finally:
        os.remove(wav_path)


def main():
    os.makedirs(SND_DIR, exist_ok=True)
    sounds_json = {}
    subs_es, subs_en = {}, {}
    for name, (fn, variants, es, en) in SOUNDS.items():
        files = []
        for v in range(variants):
            rng = np.random.default_rng(hash((name, v)) & 0xFFFF if False else sum(map(ord, name)) * 31 + v)
            x = fn(rng, v)
            fname = f"{name}_{v + 1}"
            write_ogg(x, os.path.join(SND_DIR, fname + ".ogg"))
            files.append(f"bearfriender:{fname}")
        sounds_json[name] = {"subtitle": f"subtitles.bearfriender.{name}", "sounds": files}
        subs_es[f"subtitles.bearfriender.{name}"] = es
        subs_en[f"subtitles.bearfriender.{name}"] = en
        print("sonido", name, f"({variants} variante/s)")
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds_json, f, indent=2, ensure_ascii=False)
    for code, subs in (("es_es", subs_es), ("en_us", subs_en)):
        path = os.path.join(ASSETS, "lang", code + ".json")
        data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
        data.update(subs)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
    print("sounds.json escrito")


if __name__ == "__main__":
    main()
