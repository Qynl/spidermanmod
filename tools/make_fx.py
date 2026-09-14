#!/usr/bin/env python3
"""DSP synth for Manhunt's dread layer: heartbeat, sub-drone, the whistle
motif, breath, door creak, thunder - plus digital-silence oggs so the subtitle
ghosts can be read without being heard.

Needs `soundfile` (run with a venv python): /tmp/fxenv/bin/python tools/make_fx.py
"""
from __future__ import annotations

import math
import random
from pathlib import Path

import soundfile as sf

RATE = 44100
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/manhunt/sounds"


def write(name: str, samples: list[float], rate: int = RATE) -> None:
    peak = max(abs(s) for s in samples) or 1.0
    data = [s / peak * 0.85 for s in samples]
    path = OUT / f"{name}.ogg"
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(path, data, rate, format="OGG", subtype="VORBIS")
    print(f"  {name}.ogg  {len(data) / rate:.2f}s")


def env(i: int, attack: int, total: int, decay_power: float = 3.0) -> float:
    if i < attack:
        return i / attack
    tail = max(0.0, 1.0 - (i - attack) / max(1, total - attack))
    return tail ** decay_power


def thump(length: float, freq: float, gain: float) -> list[float]:
    n = int(length * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        sweep = freq * (1.0 - 0.35 * min(1.0, t / length))
        out.append(gain * env(i, 40, n, 4.0) * math.sin(2 * math.pi * sweep * t))
    return out


def pad(samples: list[float], length: int) -> list[float]:
    return samples + [0.0] * max(0, length - len(samples))


def mix(*tracks: list[float]) -> list[float]:
    length = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(length)]


def heartbeat() -> None:
    total = int(1.15 * RATE)
    lub = thump(0.34, 52, 1.0)
    dub = [0.0] * int(0.26 * RATE) + thump(0.28, 44, 0.7)
    write("heartbeat", pad(mix(lub, dub), total))


def drone() -> None:
    n = int(4.0 * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        lfo = 0.6 + 0.4 * math.sin(2 * math.pi * 0.23 * t)
        out.append(lfo * (0.5 * math.sin(2 * math.pi * 38 * t)
                          + 0.35 * math.sin(2 * math.pi * 41.3 * t)
                          + 0.15 * math.sin(2 * math.pi * 57.1 * t)))
    fade = int(0.6 * RATE)
    for i in range(fade):
        out[i] *= i / fade
        out[-1 - i] *= i / fade
    write("drone", out)


def motif() -> None:
    notes = [440.0, 523.25, 493.88, 329.63]  # a lullaby that goes wrong at the end
    track: list[float] = []
    for note in notes:
        n = int(0.55 * RATE)
        for i in range(n):
            t = i / RATE
            vibrato = 1.0 + 0.006 * math.sin(2 * math.pi * 5.5 * t)
            whistle = (math.sin(2 * math.pi * note * vibrato * t)
                       + 0.18 * math.sin(2 * math.pi * note * 2 * t))
            breathy = 0.05 * (random.random() * 2 - 1)
            track.append(env(i, 300, n, 2.0) * (0.8 * whistle + breathy))
        track += [0.0] * int(0.06 * RATE)
    write("motif", track)


def breath() -> None:
    n = int(1.5 * RATE)
    out = []
    window = 24
    recent = [0.0] * window
    for i in range(n):
        t = i / RATE
        swell = math.sin(math.pi * min(1.0, t / 1.5)) ** 2
        noise = random.random() * 2 - 1
        recent[i % window] = noise
        low = sum(recent) / window
        out.append(swell * (0.55 * low + 0.18 * noise))
    write("breath", out)


def creak() -> None:
    n = int(1.0 * RATE)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        freq = 300 - 130 * min(1.0, t / 0.9) + 14 * math.sin(2 * math.pi * 3.1 * t)
        phase += 2 * math.pi * freq / RATE
        wobble = 0.6 + 0.4 * math.sin(2 * math.pi * 6.3 * t)
        out.append(env(i, 200, n, 2.5) * wobble * (math.sin(phase) + 0.25 * math.sin(phase * 2.7)))
    write("creak", out)


def thunder() -> None:
    n = int(2.8 * RATE)
    out = []
    window = 90
    recent = [0.0] * window
    for i in range(n):
        t = i / RATE
        recent[i % window] = random.random() * 2 - 1
        rumble = sum(recent) / window
        crack = (1.0 - min(1.0, t / 0.25)) * (random.random() * 2 - 1) * 0.5
        swell = env(i, int(0.10 * RATE), n, 2.2)
        out.append(swell * (0.85 * rumble + 0.25 * math.sin(2 * math.pi * 41 * t)) + crack * swell)
    write("thunder", out)


def silence(name: str) -> None:
    write(name, [0.0] * int(0.15 * RATE))


def main() -> None:
    random.seed(1602)
    print("forging the dread layer:")
    heartbeat()
    drone()
    motif()
    breath()
    creak()
    thunder()
    silence("ghost_turn")
    silence("ghost_check")
    silence("ghost_look")
    print("dread layer forged")


if __name__ == "__main__":
    main()
