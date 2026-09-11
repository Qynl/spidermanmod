#!/usr/bin/env python3
"""Generates the mod's binary assets reproducibly.

- Radioactive spider texture (recolored 1.21.1 vanilla spider + toxic flecks).
- 7 synthesized OGG Vorbis sound effects.

Run: python3 build-local/make_assets.py
Requires: numpy, Pillow, soundfile.
"""

import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SR = 22050


def texture():
    src = "/tmp/mcassets/data/1.21.1/entity/spider/spider.png"
    img = Image.open(src).convert("RGBA")
    assert img.size == (64, 32), img.size
    a = np.asarray(img).astype(np.float32)
    rgb, alpha = a[..., :3], a[..., 3:4]
    solid = alpha[..., 0] > 0

    # Widow shift: deepen reds, crush greens/blues on solid pixels.
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    r2 = np.clip(r * 1.15 + 18.0, 0, 255)
    g2 = np.clip(g * 0.32, 0, 255)
    b2 = np.clip(b * 0.38, 0, 255)
    rgb[..., 0] = np.where(solid, r2, r)
    rgb[..., 1] = np.where(solid, g2, g)
    rgb[..., 2] = np.where(solid, b2, b)

    # Toxic flecks: deterministic speckle of glowing green on dark pixels.
    h, w = solid.shape
    ys, xs = np.mgrid[0:h, 0:w]
    dark = solid & (r2 < 110)
    fleck = dark & (((xs * 3 + ys * 5) % 11) == 0)
    rgb[fleck] = (140.0, 255.0, 60.0)

    # Hot eyes: brighten the lightest pixels (eye highlights) to amber.
    light = solid & (r > 150) & (g > 100)
    rgb[light] = np.clip(rgb[light] * np.array([1.3, 1.1, 0.4]), 0, 255)

    out = os.path.join(ROOT, "src/main/resources/assets/spiderman/textures/entity")
    os.makedirs(out, exist_ok=True)
    Image.fromarray(a.astype(np.uint8)).save(os.path.join(out, "radioactive_spider.png"))
    print("texture ok")


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / max(attack, 1e-4))
    return a * np.exp(-t / max(decay, 1e-4))


def sine(freq, n, phase=0.0):
    t = np.arange(n) / SR
    return np.sin(2 * np.pi * freq * t + phase).astype(np.float32)


def chirp(f0, f1, n):
    t = np.arange(n) / SR
    dur = n / SR
    phase = 2 * np.pi * (f0 * t + (f1 - f0) * t * t / (2 * dur))
    return np.sin(phase).astype(np.float32)


def noise(n, seed):
    rng = np.random.default_rng(seed)
    return rng.standard_normal(n).astype(np.float32)


def lowpass(x, alpha):
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def save(name, x):
    import soundfile as sf
    x = np.clip(x, -1.0, 1.0).astype(np.float32)
    out = os.path.join(ROOT, "src/main/resources/assets/spiderman/sounds")
    os.makedirs(out, exist_ok=True)
    sf.write(os.path.join(out, name + ".ogg"), x, SR, format="OGG")
    print(name, "ok", len(x) / SR, "s")


def sounds():
    # thwip: snap + downward chirp
    n = int(0.28 * SR)
    thwip = chirp(1400, 320, n) * env(n, 0.004, 0.06) * 0.8
    thwip += lowpass(noise(n, 1), 0.5) * env(n, 0.001, 0.012) * 0.7
    save("web_shot", thwip)

    # splat: filtered noise burst + low thud
    n = int(0.32 * SR)
    splat = lowpass(noise(n, 2), 0.18) * env(n, 0.002, 0.07)
    splat += sine(95, n) * env(n, 0.004, 0.09) * 0.8
    save("web_splat", splat * 0.9)

    # zip: rising whoosh
    n = int(0.42 * SR)
    t = np.arange(n) / SR
    sweep = lowpass(noise(n, 3) * (0.4 + 0.6 * t / (n / SR)), 0.25)
    save("web_zip", sweep * env(n, 0.05, 0.25) * 0.9 + chirp(300, 900, n) * 0.12)

    # sense tingle: high shimmer with tremolo
    n = int(0.55 * SR)
    t = np.arange(n) / SR
    shimmer = sine(2350, n) + sine(3160, n, 1.1) * 0.6
    shimmer *= 0.5 + 0.5 * np.sin(2 * np.pi * 9 * t)
    save("sense_tingle", shimmer * env(n, 0.01, 0.2) * 0.4)

    # stage up: bright ascending arpeggio
    notes = [523.25, 659.25, 783.99, 1046.5]
    parts = []
    for i, f in enumerate(notes):
        m = int(0.22 * SR)
        tone = sine(f, m) * 0.7 + sine(f * 2, m) * 0.2 + sine(f * 3, m) * 0.1
        parts.append(tone * env(m, 0.005, 0.16))
    arp = np.concatenate(parts)
    save("stage_up", arp * 0.5)

    # transform: dark riser + pulse + shimmer tail
    n = int(1.7 * SR)
    t = np.arange(n) / SR
    riser = chirp(80, 660, n) * np.minimum(1.0, t / 1.1)
    pulse = np.sin(2 * np.pi * 2.2 * t) ** 8 * (t < 1.2)
    tail = (sine(2350, n) + sine(1760, n)) * np.clip((t - 0.9) / 0.8, 0, 1)
    transform = riser * 0.5 + pulse * sine(65, n) * 0.9 + tail * 0.18
    save("transform", transform * env(n, 0.02, 1.4) * 0.8)

    # wheel tick: short click
    n = int(0.09 * SR)
    click = sine(1900, n) * env(n, 0.001, 0.014)
    click += noise(n, 4) * env(n, 0.001, 0.004) * 0.4
    save("wheel_tick", click * 0.6)


if __name__ == "__main__":
    texture()
    sounds()
