# Rival Realms

> **A living frontier of crowns, corsairs, outlaws, sky captains, and player-like survivors.**
>
> Build a royal city. Recruit a pirate. Raise a fortress. Fly an airship. Make peace between rival realms—or start a war and defend your walls.

Rival Realms is a **Fabric 1.21.1** mod about handcrafted settlements and survivors who behave more like capable players than decorative villagers. The world contains factions, equipment, jobs, bases, trust, recruitment, betrayal, loot, diplomacy, raids, boats, guns, airships, and deterministic biome-aware landmarks.

This repository is the Rival Realms project. The former Spider-Man concept is intentionally not part of the design.

---

## Quick start: see the content in five minutes

1. Install the latest release-ready jar in your Fabric `mods` folder.
2. Start Minecraft **1.21.1** with Fabric Loader and Fabric API.
3. Create or open a world and enable cheats for the showcase commands.
4. Open the creative inventory and select the **Rival Realms** tab. It is the one-stop catalog for the mod.
5. Try these commands:

   ```text
   /rivalrealms landmark royal_city
   /rivalrealms landmark fortress
   /rivalrealms landmark harbor
   /rivalrealms landmark airship_yard
   ```

6. In the Rival Realms tab, use one of the faction spawn eggs to place a specific NPC, or use the Airship Spawn Egg on a block.
7. Give an unclaimed survivor a **Recruitment Contract**, then interact with the survivor using an empty hand to switch between following and holding position.

If you only want to inspect the content, the creative tab and `/rivalrealms landmark ...` commands are more reliable than waiting for random world generation.

---

## Where are the NPC spawn eggs?

They are **not hidden in the vanilla Spawn Eggs tab**. They are all in the dedicated **Rival Realms** creative tab:

| Item | What it creates | Culture / faction |
|---|---|---|
| **Survivor Spawn Egg** | A survivor with a randomly selected culture | Any Rival Realms culture |
| **Knight Spawn Egg** | An armoured knight with a royal longsword and shield | Crownlands |
| **Pirate Spawn Egg** | A pirate with cutlass-style melee gear and a flintlock | Freebooters |
| **Outlaw Spawn Egg** | A fast ranged outlaw with a frontier revolver | Dustwalkers |
| **Sky Captain Spawn Egg** | A sky captain with crossbow gear and airship colours | Skybound |
| **Airship Spawn Egg** | A rideable airship vehicle | Skybound technology |

Every NPC egg has its own item model and culture-coloured texture. The faction-specific eggs are not cosmetic aliases: they assign the matching archetype, equipment, stats, name style, texture, and faction when the entity is created.

The command equivalent is:

```text
/rivalrealms spawn knight
/rivalrealms spawn pirate
/rivalrealms spawn outlaw
/rivalrealms spawn sky_captain
/rivalrealms spawn knight 6
```

The command is useful for testing groups of NPCs. The spawn eggs are useful for normal creative play and for building your own scenes.

---

## Medieval and royal content

The medieval content is also in the **Rival Realms** tab. The current royal building kit includes:

### Blocks

- **Crown Brick** — dark heraldic masonry used for royal trim and faction walls.
- **Castle Stone** — the main custom stone used by fortresses and citadels.
- **Castle Tiles** — a darker roof/floor accent for keeps, gates, and inner courtyards.
- **Royal Wood** — deep wood for noble halls, guard houses, and palace interiors.
- **Realm Banner** — the claimable faction banner and settlement interaction block.

All of these blocks have registered blockstates, block models, item models, translations, and custom textures. The new medieval blocks are also survival-craftable:

```text
Castle Stone  = Stone Bricks
Castle Tiles  = Deepslate Tiles
Royal Wood    = Dark Oak Planks
Crown Brick   = Stone Bricks
```

The recipes produce multiple blocks where appropriate, so the kit is practical rather than only a creative showcase.

### Medieval items

- **Royal Longsword** — a durable diamond-tier sword with a custom royal texture and a dedicated recipe.
- **Royal Coin** — a compact realm-currency item used as a crafting ingredient and settlement treasure.
- **Medieval Realm Map** — a parchment-style realm item crafted with paper and a Royal Coin; use it to receive the nearest saved settlement coordinates.
- **Recruitment Contract** — the item used to turn an unclaimed survivor into a companion.

The Royal Longsword is also used by Crownlands knights, so the medieval equipment is visible on NPCs rather than existing only in the inventory.

---

## Everything in the Rival Realms creative tab

The tab is deliberately curated so players never have to search through scattered vanilla categories.

### Royal / medieval

- Crown Brick
- Castle Stone
- Castle Tiles
- Royal Wood
- Realm Banner
- Royal Longsword
- Royal Coin
- Medieval Realm Map
- Knight Spawn Egg

### Pirate / maritime

- Ship Planks
- Freebooter Boat
- Pirate Flintlock
- Pirate Spawn Egg

### Frontier / outlaw

- Frontier Planks
- Frontier Revolver
- Outlaw Spawn Egg

### Airship / sky

- Airship Metal
- Airship Spawn Egg
- Sky Captain Spawn Egg

### Social / universal

- Recruitment Contract
- Survivor Spawn Egg

Every custom item in this list has a registered translation and item model. Custom icons and textures are included in the release jar.

---

## The four factions

| Faction | Archetype | Visual identity | Equipment and behaviour |
|---|---|---|---|
| **Crownlands** | Knight | Blue, red, steel, royal heraldry | Longsword, shield, heavy armour, fortress defence |
| **Freebooters** | Pirate | Teal, red, dark wood, maritime gear | Cutlass-style melee weapon, flintlock, harbours, shipyards |
| **Dustwalkers** | Outlaw | Dust, leather, gold, frontier colours | Revolver, axe, fast movement, western towns |
| **Skybound** | Sky Captain | Violet, cyan, brass, flight gear | Crossbow, sword, skyports, airship yards |

Survivors are one entity type with data-driven archetypes. They are **not real network players**, but they are designed to feel like strong, equipped players: they have proper equipment, health, movement, ranged attacks, targeting, persistent NBT state, names, inventories, and faction identity.

---

## Survivor gameplay

### Recruitment

1. Find or spawn a survivor.
2. Hold a **Recruitment Contract**.
3. Right-click an unclaimed survivor.
4. The survivor becomes part of your crew and starts with high trust.
5. Use an empty hand to toggle between following you and holding position.

A recruited survivor follows its owner, stops treating the owner as a target, and remains persistent when the chunk is saved. If the owner logs off, the companion does not become a random hostile NPC.

### Trust and betrayal

Trust is a real value between 0 and 100 rather than a dialogue-only label.

- Start recruitment with a strong trust value.
- Feed a companion cooked beef or a golden carrot to increase trust.
- Time away slowly reduces trust.
- Damaging your own companion can reduce trust heavily.
- Very low trust can trigger a betrayal.
- A betrayer becomes hostile and targets the former owner.

This means recruitment is useful, but not completely risk-free. Treat companions like members of a crew, not like disposable menu entries.

### Settlement workers and guards

A claimed settlement can contain:

- Guards
- Builders
- Farmers
- Traders
- Blacksmiths
- Scouts

Workers move to visible worksites and contribute to a persistent settlement economy. Guards remain tied to their home settlement and defend it without treating the owner as an enemy. When enough food, materials, and construction progress are available, the settlement physically expands with new districts and defences.

---

## Settlements and handcrafted structures

Rival Realms structures are hand-authored block layouts, not floating labels or dialogue markers. They contain streets, walls, gates, interiors, windows, towers, markets, farms, workshops, storage, banners, docks, loot chests, and faction styling.

### Showcase variants

```text
/rivalrealms landmark fortress
/rivalrealms landmark citadel
/rivalrealms landmark town
/rivalrealms landmark royal_city
/rivalrealms landmark harbor
/rivalrealms landmark shipyard
/rivalrealms landmark skyport
/rivalrealms landmark airship_yard
/rivalrealms landmark outpost
```

Each command builds a persistent settlement near the executing player and claims it for that player. This is the recommended test path because it works immediately in an existing world.

### Automatic biome-aware generation

Newly loaded eligible overworld chunks can receive a deterministic landmark candidate. The current mapping is:

| Environment | Likely content |
|---|---|
| Ocean, river, beach | Pirate harbour or shipyard |
| Desert, badlands, savanna | Dustwalker town or outpost |
| Mountains, peaks, windswept terrain | Skyport or airship yard |
| Taiga, snow, ice | Fortress or citadel |
| Other suitable overworld terrain | Town, royal city, fortress, or citadel |

The candidate selection is based on the world seed and chunk position. Duplicate sites are prevented through persistent world state, and the generator does not intentionally force-load distant terrain from a server tick loop.

### Why an existing world may have no structures

This is normal when:

- the nearby chunks were already explored before Rival Realms was installed;
- the player has not travelled far enough to load new eligible chunks;
- deterministic spacing placed the nearest candidate elsewhere;
- the terrain was unsuitable for the selected structure;
- the player is looking in a non-overworld dimension.

Use a showcase command when you want a guaranteed immediate result:

```text
/rivalrealms landmark royal_city
```

Use these commands to inspect persistent sites afterward:

```text
/rivalrealms bases
/rivalrealms locate
```

`/rivalrealms locate` reports the nearest saved settlement and its coordinates. Commands require permission level 2.

---

## Boats, weapons, and airships

### Pirate boats

The **Freebooter Boat** is a working boat item based on Minecraft's boat behaviour. Pirate harbours and shipyards can contain usable boats, piers, cranes, storage yards, and maritime loot.

### Revolver and flintlock

- The **Frontier Revolver** is a server-authoritative hitscan weapon with range, damage, cooldown, and durability.
- The **Pirate Flintlock** is a slower, heavier frontier firearm.
- Both have custom textures and can be repaired through normal item repair mechanics.
- NPC ranged cultures use their corresponding ranged equipment and can fire their own projectile attacks.

### Airships

Airships are actual rideable entities with a dedicated renderer and texture. They can be launched with:

```text
/rivalrealms airship
```

or placed from the **Airship Spawn Egg** by right-clicking a block. Airship docks, skyports, and airship yards are physical structures with elevated decks, chains, hangars, banners, and custom airship metal.

---

## Command reference

All commands require permission level 2 unless a server configuration changes the command permission policy.

### NPCs

```text
/rivalrealms spawn <knight|pirate|outlaw|sky_captain> [count]
```

`count` accepts 1–12. This is the fastest way to populate a test settlement.

### Structures

```text
/rivalrealms build <knight|pirate|western|sky>
/rivalrealms landmark <variant>
```

`build` creates a culture set piece. `landmark` creates one of the larger named settlement variants listed above.

### Bases and jobs

```text
/rivalrealms claim
/rivalrealms bases
/rivalrealms locate
/rivalrealms jobs
/rivalrealms assign <guard|builder|farmer|trader|blacksmith|scout> <survivor>
```

Stand inside a settlement when using `/rivalrealms jobs`. Assignments only work for survivors inside a settlement owned by the executing player.

### Diplomacy

```text
/rivalrealms diplomacy <faction_a> <faction_b> <-100..100>
```

Values at or below `-50` are hostile. Hostile factions can target one another and may organise settlement raids when the settlement is loaded.

### Help

```text
/rivalrealms info
```

---

## Installation

### Client and singleplayer

1. Install Minecraft **1.21.1**.
2. Install Fabric Loader **0.16.5 or newer**.
3. Install a compatible Fabric API release.
4. Put the latest `rival-realms-*.jar` in `.minecraft/mods/`.
5. Launch the Fabric profile.

### Dedicated server

Install the same Rival Realms jar, Fabric Loader, and Fabric API on the server. The mod runs on both physical sides. The client installation is required for custom models, textures, survivor skins, and the airship renderer.

Always remove older Rival Realms jars before copying a newer artifact. Two versions of the same mod in the `mods` folder can create confusing registration and resource failures.

---

## Building from source

Rival Realms targets:

- Minecraft `1.21.1`
- Fabric Loader `0.16.5`
- Fabric API `0.116.7+1.21.1`
- Java `21`

Build a remapped jar with:

```bash
chmod +x ./gradlew
./gradlew --no-daemon clean check build
```

The release jar is written to `build/libs/`. The sources jar is not the file to install in Minecraft.

### Reproducible pixel art

The small pixel-art assets are generated by:

```bash
python3 scripts/make_art.py
```

The script uses only Python's standard library. It creates the faction skins, medieval textures, weapon icons, spawn-egg icons, and building palettes used by the resource tree.

---

## Bug hunting and release validation

The project has a dependency-free preflight script:

```bash
python3 scripts/bughunt.py
```

It validates:

- JSON syntax for assets, recipes, and metadata;
- every Rival Realms model and texture reference;
- PNG signatures and safe dimensions;
- faction skin dimensions;
- all creative-tab catalog entries;
- every NPC and airship spawn-item model and texture;
- registered recipe result IDs;
- public Fabric entrypoint constructors;
- server-side item-use guards;
- chunk-load and settlement safety markers;
- the required classes and resources in the packaged jar.

CI then runs the preflight, compiles and remaps with Gradle, runs `check`, inspects the produced jar, and uploads a release-ready artifact. A source-only bug hunt is useful, but a release should not be called validated until the GitHub Actions build is green too.

---

## Performance and safety choices

Rival Realms is intentionally conservative about world work:

- automatic generation runs on eligible chunk loads, not a global “build everywhere” loop;
- candidate spacing is deterministic and persistent duplicate prevention is enabled;
- settlement ticks skip areas that are not already loaded;
- base and generated-site state is capped and saved in world data;
- entity spawning is bounded per settlement and raid;
- malformed settlement ticks are logged and skipped instead of taking down the entire server;
- airship and survivor spawn items perform entity creation on the server side only;
- client-only renderer code stays in the client source set.

These protections reduce the chance that an ambitious structure or NPC feature turns into a server crash or an accidental chunk-loading machine.

---

## Current feature map

| System | Included |
|---|---|
| Four survivor cultures | Yes |
| Culture-specific NPC spawn eggs | Yes |
| Generic random survivor egg | Yes |
| Airship spawn item | Yes |
| Dedicated Rival Realms creative tab | Yes |
| Medieval block palette | Yes |
| Royal longsword, coin, and map | Yes |
| Survival recipes for medieval kit | Yes |
| Pirate boat and harbour | Yes |
| Revolver and flintlock | Yes |
| Recruitment and trust | Yes |
| Companion betrayal | Yes |
| Faction diplomacy | Yes |
| Persistent bases | Yes |
| Settlement jobs and growth | Yes |
| Raids and rival targeting | Yes |
| Deterministic biome-aware landmarks | Yes |
| Dedicated airship renderer | Yes |
| Automated bug hunt and CI jar inspection | Yes |

---

## Roadmap

The architecture is ready for further content without replacing the current systems. Planned directions include:

- more royal and medieval interiors;
- additional knight equipment, banners, heraldry, and siege set pieces;
- more pirate ships, docks, cargo, and naval encounters;
- more western buildings, contracts, and frontier loot;
- larger airship interiors and sky encounters;
- more survivor personalities and relationship events;
- additional settlement districts and faction-specific recipes;
- richer loot tables and diplomacy consequences;
- more generated landmark variants and biome-specific props.

The goal is not to fill a feature list with placeholders. New features should be visible, textured, usable, tested, and integrated into the Rival Realms tab and world systems.

---

## License

Rival Realms is distributed under the license included in this repository. See `LICENSE` for the full text.
