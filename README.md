# 🕷️ Spider-Man Mod

> A Fabric mod for Minecraft 1.21.1 that turns a normal survival player into a progressively developing web-slinging superhero.

**Minecraft:** 1.21.1  
**Mod loader:** Fabric  
**Java:** 21  
**License:** MIT  
**Mod ID:** `spiderman`

---

## 🕸️ What this mod is

This project is built around a simple idea: **Spider-Man should feel like a movement system, not a costume item.**

You begin as a normal player. A radioactive spider can bite you, triggering the transformation system. From there, your powers develop through mastery and progressively unlock movement abilities, Spider-Sense, web abilities, and physical enhancements.

The implementation is split between server-side gameplay logic and client-side presentation. Web abilities are requested through networking and executed on the server, while the client handles things such as the ability wheel, HUD, web visuals, first-person rendering, camera effects, and local input.

The current codebase includes:

- A custom radioactive spider entity and bite goal
- Bite-triggered power acquisition
- Five power stages, from latent abilities to full mastery
- Mastery-based progression
- Ten distinct web abilities
- Momentum-based web swinging
- Web zip movement
- Wall climbing, wall running, ceiling crawling, sliding, diving, and wall jumping
- Spider-Sense with directional feedback
- First-person wrist/arm presentation
- Web strand and web-shot rendering
- A hold-to-open radial ability wheel
- Server-authoritative gameplay and client/server synchronization
- Combo tracking and mastery rewards
- Temporary web/trap cleanup
- Persistent player power state
- JSON configuration for gameplay tuning
- Commands and advancements for managing/testing powers
- Automated compile, remapping, packaging, and logic verification

> **Important:** this README describes what is actually represented in the repository. It intentionally does not document planned systems that are not implemented here yet.

---

# ✨ Feature overview

## 🕷️ The radioactive spider

The transformation starts with a custom `RadioactiveSpiderEntity` rather than immediately granting powers to every player.

The spider has its own entity registration, renderer, sounds, and bite goal. When its bite succeeds, `TransformLogic` grants the player their spider powers and initializes their progression state.

The first transformation can also apply the configured nausea, blindness, and slowness effects, play the transformation sound, send the player progression information, and grant the transformation advancement.

The spider/bite behavior is configurable, including spawn weighting and bite behavior.

---

# 🧬 Power progression

The progression system is based on **mastery**, not simply finding an item and receiving every ability at once.

A player's persistent state contains whether they have powers, their current stage, mastery, cooldown information, swing state, and other movement state.

There are five stages, represented internally as stages `0` through `4`:

| Stage | Name | Main progression theme |
|---:|---|---|
| 0 | **Latent Senses** | Initial physical enhancement; wall/ceiling crawling begins, feeding mastery toward stage 1 |
| 1 | **Superhuman** | Enhanced strength, speed and fall resistance |
| 2 | **Web Slinger** | Spider-Sense and the core web-slinging toolkit begins |
| 3 | **Sky Dancer** | Higher-level web traversal and mobility |
| 4 | **Spider Master** | Full progression and the highest-stage abilities |

Stage thresholds are configurable. The default configuration uses mastery thresholds of:

```text
100 → stage 1
300 → stage 2
700 → stage 3
1400 → stage 4
```

Mastery is earned by actually using the powers. Different systems award different amounts, including web abilities, swinging, wall traversal, Spider-Sense activity, and other successful power interactions. Freshly bitten players at stage 0 earn mastery by climbing walls and ceilings (about +1 per second of contact, plus bonuses for wall jumps).

The progression is persistent per player and is synchronized to the client so the UI can represent the current power state.

### Testing commands

- `/spm give <player>`, `/spm reset <player>`, `/spm stage <player> <0-4>` — admin commands, require permission level 2 (OP/cheats).
- `/spm stageup` — raises your own stage by one (granting powers first if needed). Usable only in creative mode, so survival progression stays earned.

---

# 🕸️ Web abilities

The mod currently defines **10 web abilities** in `AbilityIds` and executes them through the server-side `AbilityExecutor`.

| Ability | What it does | Minimum stage |
|---|---|---:|
| **Web Shot** | Fires a normal web projectile from the player's selected wrist | 2 |
| **Web Swing** | Finds a web anchor and attaches the player to it for momentum-based swinging | 3 |
| **Web Zip** | Pulls the player toward a raycast target for fast traversal | 3 |
| **Web Pull** | Pulls a targeted entity toward the player, or moves the player toward another player | 3 |
| **Web Trap** | Applies a temporary web/trap effect to a target or block location | 2 |
| **Web Line** | Creates a swing connection with a stronger traversal-oriented launch | 4 |
| **Web Burst** | Releases a radial web-based burst around the player | 4 |
| **Impact Web** | Fires a heavier web shot with increased configured damage | 4 |
| **Web Platform** | Creates a temporary web platform beneath/around the player | 4 |
| **Double Web** | Fires two web shots from both sides simultaneously | 4 |

The IDs and stage requirements are centralized in `AbilityIds`, so the client and server share the same ability definitions instead of maintaining separate lists.

### Web shots

Web shots are represented by the custom `WebShotEntity`. The server calculates the origin and direction and creates the projectile with the appropriate configured damage.

The shot origin is offset to the player's left/right side, allowing the visual system to represent the web as coming from the wrist area rather than from a held Minecraft item.

### Double Web

The double-web ability creates two simultaneous shots using mirrored origins and slightly separated directions. This is the foundation for firing from both wrists at once.

### Impact Web

Impact Web uses the same projectile system but marks the shot as heavy and uses the configured `impactDamage` value.

### Web Trap

Trap behavior can target living entities or a block location. Entity targets receive a temporary slowness effect, while successful block placement is tracked by the web cleanup system.

### Web Platform

The platform ability attempts to place a small 3×3 area of temporary web blocks around the player's current base position. Placement is performed through the same cleanup-aware web system used for traps.

---

# 🪢 Web swinging

Swinging is implemented in `SwingPhysics` as a **velocity-steered pendulum system** rather than repeatedly teleporting the player.

The important pieces are:

- The server searches for a valid solid anchor using upward-biased raycasts.
- The anchor position is stored in the player's power state.
- The rope has a configurable maximum length.
- Player velocity is constrained around the anchor instead of directly setting the player's position.
- Looking upward reels the rope in.
- Looking downward lets the rope out.
- Momentum is preserved when detaching.
- A small release boost is applied when a swing is intentionally released.
- If the player somehow ends up far beyond the expected swing range, the system safely detaches instead of pulling them across the world.
- Landing on the ground automatically ends the active swing.

This keeps swinging integrated with Minecraft's normal movement/velocity handling instead of relying on per-tick position teleports.

### Swing range

The default configured swing range is **40 blocks**.

### Rope control

Swinging does not require a separate rope-length key. The player's look direction controls rope length:

- Look upward → reel in
- Look downward → let out

That leaves the normal movement controls available for steering and momentum management.

---

# ⚡ Web Zip

Web Zip uses a block raycast from the player's eye position toward the direction they are looking.

The target becomes a temporary movement destination. During the zip, gravity is disabled and velocity is steered toward the stored target. Once the short zip finishes, normal gravity returns and the player's resulting velocity is preserved within configured limits.

The default zip range is **32 blocks**.

---

# 🧗 Movement system

The spider powers are not limited to web projectiles. The server-side movement system contains dedicated logic for traversal.

Implemented movement-related systems include:

- Wall climbing
- Ceiling crawling
- Wall running
- Wall jumping
- Sliding
- Diving
- Enhanced sprinting
- Enhanced jumping
- Increased step height
- Reduced fall impact through the spider's safe-fall attribute
- Swinging and web-assisted aerial traversal
- Context-sensitive movement handling

The movement enhancements are applied through Minecraft's entity attributes and server movement logic rather than replacing the entire vanilla player controller.

---

# 🕷️ Spider-Sense

Spider-Sense is implemented as a gameplay system rather than just a permanent screen overlay.

The server-side `SenseLogic` evaluates nearby danger and communicates sense events to the client using `SensePingS2C` packets.

The configured base radius is **10 blocks**, with an additional **4 blocks per stage** by default.

The client can then use the received directional information for the Spider-Sense presentation, while the server remains responsible for the underlying detection.

---

# 🎯 Combo system

The mod contains a dedicated `ComboTracker` and configurable combo settings.

Combos can increase the damage multiplier of eligible web attacks when actions are chained within the configured combo window.

Default values include:

```text
Combo window: 80 ticks
Combo bonus: 0.25
Combo cap: 4
```

The system is intentionally separate from the individual ability implementations so combo behavior can affect abilities without duplicating the same logic throughout the codebase.

---

# 🎮 Controls

The default input layout is designed around two-wrist web control and a radial ability selector.

| Input | Action |
|---|---|
| `G` (hold) | Open the radial ability wheel |
| Mouse selection | Select an ability from the radial wheel |
| Release `G` | Confirm/equip the selected ability |
| Left click | Use the equipped ability with the primary/right-side wrist input |
| Right click | Use the equipped ability with the secondary/left-side wrist input |
| Both sides where supported | Enable dual/twin web behavior |
| `Shift` | Context-sensitive traversal actions such as slide/wall interaction/dive |
| `Space` | Vanilla jump plus spider movement actions such as wall jumping where available |

All gameplay keybinds are registered through the client keybind system and can be changed through Minecraft's normal Controls menu.

> The exact behavior of a mouse input depends on the currently selected ability. The server receives the selected ability and hand information and performs the actual action.

---

# 🛞 Radial ability wheel

Holding `G` opens the custom `WheelScreen`.

The wheel is a real client UI rather than a list of chat commands. It is designed around quick ability selection while preserving the normal game view and minimizing the number of dedicated keys needed for ten abilities.

The networking layer includes a dedicated `WheelSelectC2S` packet so the selected ability can be communicated to the server.

The HUD also has access to the player's current power/progression state and can display the currently relevant spider information.

---

# 👋 First-person presentation

The mod includes client rendering specifically for the spider powers instead of relying only on vanilla held-item rendering.

Relevant client components include:

- `HeldItemRendererMixin`
- `BipedEntityModelMixin`
- `WebShotRenderer`
- `StrandRenderer`
- `HudRenderer`
- `WheelScreen`
- `GameRendererMixin`

The web system therefore has dedicated visual code for the projectile and the connecting web strand, while the rendering mixins allow the mod to alter the first-person/character presentation without requiring a normal web-shooter item in the hotbar.

The server sends the authoritative swing state and anchor data so other clients can also represent active web connections.

---

# 🌐 Multiplayer architecture

Multiplayer support is a core part of the implementation.

The basic flow is:

```text
Client input
    ↓
C2S packet
    ↓
Server validation / power-state check
    ↓
Server-side ability or movement logic
    ↓
World/entity/state changes
    ↓
S2C synchronization
    ↓
Client presentation
```

The server checks whether the player is alive, whether they are allowed to use the requested ability, and whether the relevant cooldown/stage requirements are satisfied before executing the action.

Networking classes currently cover systems such as:

- Ability use
- Wheel selection
- Wall jumps
- Power synchronization
- Spider-Sense pings
- Combo updates
- Swing state synchronization

This keeps important gameplay state on the server instead of trusting the client to directly modify the world.

---

# 💾 Player state and persistence

Persistent spider progression is stored by player UUID.

The state system separates several responsibilities:

- `PlayerPowers` stores server-side player power/progression data.
- `ClientPowers` stores the client representation needed for UI and presentation.
- `SpiderState` manages the player-state collection and persistence.
- `AbilityIds` provides shared ability definitions.

The server saves spider progression under:

```text
config/spiderman-players/<uuid>.json
```

This means the player's progression is tied to their UUID rather than to a temporary client session.

---

# ⚙️ Configuration

The mod creates:

```text
config/spiderman.json
```

The configuration is loaded at startup and provides centralized gameplay tuning without requiring a source rebuild.

### Current configuration groups

| Group | Examples |
|---|---|
| Spider + bite | Spawn weighting, bite chance, post-bite behavior |
| Ranges | Web, swing, and zip ranges |
| Cooldowns | Individual cooldowns for all ten abilities |
| Damage | Web Shot, Impact Web, and Burst damage |
| Burst | Radius and damage |
| Combos | Window, bonus, and cap |
| Physical boosts | Speed, jump, strength, fall protection, step height |
| Spider-Sense | Base radius and per-stage radius growth |
| Web cleanup | Web lifetime and maximum trap-web count |
| Progression | Stage thresholds and mastery multiplier |
| Transformation | Transformation visual/status effects |
| Experimental powers | Optional experimental double jump and venom blast |

### Default traversal values

```text
Web range:       24 blocks
Swing range:     40 blocks
Zip range:       32 blocks
Jump multiplier: 1.35
Speed multiplier: 1.15
Strength multiplier: 1.5
```

These are defaults, not hard-coded gameplay requirements. Server owners can rebalance them through the generated JSON configuration.

---

# 🧹 Temporary web cleanup

Temporary web structures are not intended to permanently fill a world.

`WebCleanup` handles the lifetime and tracking of web placements used by systems such as traps and platforms.

The default configuration gives temporary web placements a lifetime of **100 ticks** and limits tracked trap webs to **24**.

---

# 🏆 Advancements

The transformation and progression system integrates with Minecraft advancements.

The transformation logic can grant advancement entries for:

- The initial transformation
- Individual progression stages

This makes progression visible through Minecraft's existing advancement system rather than keeping it entirely inside a custom HUD.

---

# 🧩 Project structure

The repository is organized by responsibility rather than placing all gameplay code into one large class.

```text
spidermanmod/
├── .github/
│   └── workflows/          # CI/build automation
├── build-local/             # Offline-friendly local build/verification toolchain
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/spiderman/mod/
│   │   │       ├── client/       # HUD, wheel, keybinds, renderers, client ticks
│   │   │       ├── command/      # Mod commands
│   │   │       ├── config/       # JSON configuration
│   │   │       ├── entity/       # Radioactive spider and web projectile entities
│   │   │       ├── mixin/        # Vanilla Minecraft integration points
│   │   │       ├── net/          # C2S/S2C networking
│   │   │       ├── server/       # Gameplay, movement, progression, cleanup
│   │   │       ├── state/        # Persistent/client power state
│   │   │       ├── ModEntities.java
│   │   │       ├── ModSounds.java
│   │   │       ├── SpiderManClient.java
│   │   │       └── SpiderManMod.java
│   │   └── resources/
│   │       ├── assets/spiderman/ # Sounds, textures, language data
│   │       ├── data/              # Minecraft data/advancement content
│   │       ├── fabric.mod.json
│   │       └── spiderman.mixins.json
│   └── test/                      # Java-side tests for state/client logic
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle
├── LICENSE
└── README.md
```

### Core server systems

Some of the main gameplay classes are:

- `AbilityExecutor` - central execution point for the ten web abilities
- `SwingPhysics` - swinging, rope constraint, anchors, detach/launch behavior
- `ClimbLogic` - wall/ceiling/traversal logic
- `TransformLogic` - bite transformation, stages, physical attributes, advancements
- `SenseLogic` - Spider-Sense detection
- `MasteryLogic` - mastery progression
- `ComboTracker` - combo state and damage scaling
- `ServerTickHandler` - server-side ticking
- `WebCleanup` - temporary web lifecycle

### Core client systems

- `ClientTickHandler` - client-side tick/input handling
- `Keybinds` - configurable key registration
- `WheelScreen` - radial ability selection UI
- `HudRenderer` - spider HUD presentation
- `WebShotRenderer` - web projectile visuals
- `StrandRenderer` - active web/swing strand visuals
- `RadioactiveSpiderRenderer` - custom radioactive spider presentation

---

# 🛠️ Building from source

The project uses Gradle/Loom for the normal Fabric development build.

### Standard build

On Linux/macOS:

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The normal build produces the mod jar under:

```text
build/libs/
```

### Local/offline-friendly build

The repository also contains a separate local build path:

```bash
./build-local/build.sh
```

The local toolchain is designed for environments where resolving the complete Gradle/Fabric dependency graph is inconvenient. It compiles against the checked-in API surface, performs the required remapping/refmap work, packages the mod, and runs verification.

The resulting jar is written under:

```text
build-local/
```

and is also copied into `build/libs/` by the local build process.

For the details of that toolchain, see [`build-local/README.md`](build-local/README.md).

---

# 🧪 Testing and verification

The repository contains more than a simple compile check.

The local verification pipeline covers several layers:

### Source compilation

Java sources are compiled against the declared API surface.

### Mapping/remap verification

The local build performs the required Yarn-to-Intermediary remapping and verifies vanilla references against the expected names/descriptors.

### Logic tests

The `src/test/java` tree contains tests for pure/modular logic, including areas such as:

- Ability registration/definitions
- Client power state
- Progression/state behavior
- Radial wheel mathematics

### Jar verification

The build checks the produced jar for expected mod resources and packaging data, including relevant mappings/refmap and resource content.

### CI

The repository includes a GitHub Actions build workflow that runs the Gradle build on Java 21 and publishes the resulting jar as an Actions artifact.

> **Current limitation:** the repository's automated build environment is headless, so a successful CI build does not mean the mod has been visually playtested inside a running Minecraft client. In-game issues should be reported with the relevant `latest.log` and reproduction steps.

---

# 📦 Installation

## Requirements

You need:

1. **Minecraft Java Edition 1.21.1**
2. **Fabric Loader** compatible with 1.21.1
3. **Java 21**
4. **Fabric API** for 1.21.1
5. The built/released `spiderman` mod jar

## Install

1. Install Fabric for Minecraft 1.21.1.
2. Make sure the instance is running Java 21.
3. Install the required Fabric API version for your 1.21.1 instance.
4. Place the actual `.jar` file in the instance's `mods` folder.
5. Launch Minecraft.
6. Create or enter a world and allow the radioactive spider system to begin the progression.

### Important: GitHub Actions downloads

GitHub Actions artifacts are downloaded as `.zip` archives. **That ZIP is the artifact container, not the Minecraft mod itself.**

If you download the mod from an Actions run:

```text
spiderman-jar.zip
└── spiderman-1.0.0.jar
```

Extract the ZIP and put the `.jar` inside your `mods` folder.

Do not rename the ZIP to `.jar`.

---

# 🧑‍💻 Development notes

The codebase deliberately keeps important responsibilities separated:

- Gameplay authority lives on the server.
- Client code focuses on input, presentation, HUD, and rendering.
- Shared ability IDs prevent client/server ability mismatches.
- Persistent progression is stored separately from client presentation.
- Configuration is centralized instead of scattering balance values throughout gameplay classes.
- Web visuals are synchronized separately from the underlying server state.
- Movement uses Minecraft velocity/attribute systems where possible instead of replacing the entire player controller.

This structure makes it possible to work on individual systems such as swinging, traversal, progression, rendering, or networking without turning the entire mod into one monolithic implementation.

---

# 🧪 Experimental systems

A small number of features are explicitly marked experimental in the configuration.

Currently configurable experimental powers include:

```json
"experimentalDoubleJump": false,
"experimentalVenomBlast": false
```

They are disabled by default and are intentionally separated from the normal progression balance.

---

# 🐛 Known limitations / current status

This repository is an active development project rather than a finished commercial-quality release.

The most important limitation is **playtesting coverage**. The build and logic verification pipeline can validate a lot of the implementation, but it cannot replace actually running Minecraft and testing movement, rendering, networking, and edge cases in a real client/server environment.

Potentially useful bug reports should include:

- Minecraft version
- Fabric Loader version
- Fabric API version
- Mod version/build
- Singleplayer or multiplayer
- What ability/movement action was being used
- Exact reproduction steps
- `latest.log` when applicable

---

# 📋 Current implementation at a glance

| System | Repository implementation |
|---|---|
| Radioactive spider | ✅ Custom entity + bite goal |
| Bite transformation | ✅ Server-side transformation flow |
| Persistent powers | ✅ UUID-based state |
| Five progression stages | ✅ Stages 0–4 |
| Mastery progression | ✅ Configurable thresholds/multiplier |
| Web Shot | ✅ |
| Web Swing | ✅ Momentum/constraint-based |
| Web Zip | ✅ |
| Web Pull | ✅ |
| Web Trap | ✅ |
| Web Line | ✅ |
| Web Burst | ✅ |
| Impact Web | ✅ |
| Web Platform | ✅ |
| Double Web | ✅ |
| Wall climbing | ✅ |
| Ceiling crawling | ✅ |
| Wall running | ✅ |
| Wall jumping | ✅ |
| Sliding / diving | ✅ |
| Spider-Sense | ✅ |
| Radial ability wheel | ✅ |
| First-person web presentation | ✅ Client rendering/mixins |
| Swing/web synchronization | ✅ S2C networking |
| Combo system | ✅ |
| Temporary web cleanup | ✅ |
| JSON configuration | ✅ |
| Advancements | ✅ |
| Automated build | ✅ |
| Automated logic/resource verification | ✅ |
| Full automated Minecraft client playtesting | ⚠️ Not available in the headless build environment |

---

# 📜 License

This project is licensed under the **MIT License**. See [`LICENSE`](LICENSE).

This is a fan-made, non-commercial Minecraft mod and is **not affiliated with or endorsed by Marvel or Sony**.

---

# 🕷️ The goal

The long-term design philosophy is straightforward:

> **Don't make Spider-Man a Minecraft item. Make Minecraft movement feel like Spider-Man.**

The current architecture is built around that idea: progression, traversal, web physics, server-authoritative abilities, first-person presentation, and a control scheme that lets the player move through the world instead of simply equipping another piece of gear.
