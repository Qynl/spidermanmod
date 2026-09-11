# 🕷️ Spider-Man Mod — Become the Webslinger (Minecraft 1.21.1, Fabric)

A complete **Spider-Man-style movement and progression system** for Minecraft — not a
costume mod. You start as a normal player, get bitten by a rare radioactive spider,
and progressively grow into an extremely mobile web-swinging superhero with organic
web-shooters in your wrists.

## ✨ Highlights

- **Radioactive spider** — rare spawn, unique look/sound/particles/behavior, configurable rate
- **Bite + cinematic transformation** — screen effects, movement lock, powers awaken over time
- **5 power stages** — senses → wall crawling → Spider-Sense → swinging → advanced acrobatics
- **10 organic web abilities** — shot, swing, zip, pull, trap, line, burst, impact, platform, double web
- **Real momentum physics** — constraint-based swinging with gravity, input pumping, release boosts
- **Radial ability wheel (G)** — slow-mo focus, mouse select, cooldown display
- **First-person arms** — animated 3D arms/hands fire webs from each wrist
- **Wall climb / wall run / ceiling crawl / slide / dive / landing roll**
- **Spider-Sense** — directional danger instinct with audio + screen feedback
- **Superhero camera** — speed FOV + subtle swing roll (all configurable)
- **Multiplayer-safe** — server-authoritative physics, synced webs, visible to everyone
- **Fully configurable** — every speed, range, cooldown, damage and sense value in one JSON file

## 🎮 Controls (all remappable in Controls menu)

| Input | Action |
|---|---|
| `G` (hold) | Radial ability wheel, release to equip |
| `Left click` | Fire equipped ability (primary / right wrist) |
| `Right click` | Fire equipped ability (secondary / left wrist) — hold both for double webs |
| `Shift` | Contextual movement (slide while sprinting, stick to walls, dive in air) |
| `Space` | Jump / wall jump / release trampoline jump off webs |
| Sneak + click | Force-fire at close range (clicks near blocks stay vanilla otherwise) |

## 📦 Install (players)

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.16.x for Minecraft **1.21.1** + Java 21.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) `0.105.0+1.21.1` (or newer 1.21.1 build).
3. Drop `spiderman-1.0.0.jar` into your `mods` folder.
4. (Server owners) Tweak `config/spiderman.json` — every system is tunable.

## 🔨 Build (developers)

Standard Loom build (needs internet for dependencies):

```bash
./gradlew build
# jar -> build/libs/spiderman-1.0.0.jar
```

Offline-friendly local build used in this repo (compiles against a checked-in API
surface, remaps Yarn → Intermediary, generates the mixin refmap, packages the jar):

```bash
./build-local/build.sh
# jar -> local-build/spiderman-1.0.0-local.jar
```

See [`build-local/README.md`](build-local/README.md) for how the local toolchain works.

## 🧪 Testing

- `javac` compile of every source against the declared API surface
- Automated Yarn 1.21.1 mapping verification (`build-local/verify_yarn.py`) so every
  vanilla reference is guaranteed to exist with the right descriptor
- Pure-logic unit tests: swing physics, wheel math, progression, config
  (`src/test/java`, run with `./build-local/test.sh`)
- Jar integrity checks (mappings, refmap, assets, sounds, advancements, lang)

> [!NOTE]
> In-game playtesting (client run) was not possible in the headless build
> environment — see `TESTING.md`. If you find a bug in-game, please open an issue
> with your `latest.log`.

## 🗺️ Power progression

| Stage | Unlocks |
|---|---|
| 1 — Awakening | Enhanced senses, speed, jump, fall protection |
| 2 — Crawler | Wall climb, ceiling crawl, wall run, better sprint/jump |
| 3 — Sense | Spider-Sense + first web (Web Shot) |
| 4 — Swinger | Web Swing + Web Zip |
| 5 — Webslinger | Pull, Trap, Line, Burst, Impact, Platform, Double Web + full acrobatics |

Mastery is earned by *using* your powers (swinging, running walls, sensing danger…).

## ⚙️ Configuration

Everything lives in `config/spiderman.json` (auto-created with defaults + comments
in `config/spiderman.readme.txt`). Progression data is stored per-player in
`config/spiderman-players/<uuid>.json`.

## 📄 License

MIT — see [LICENSE](LICENSE). A fan-made, non-commercial mod. Not affiliated with Marvel.
