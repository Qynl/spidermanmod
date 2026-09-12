# Rival Realms

> **Zeppelins you pilot, ships that actually sail and fire broadsides, four warring survivor cultures, and settlements that grow out of the terrain.**

Rival Realms is a **Fabric 1.21.1** mod about crowns, corsairs, outlaws and sky captains. Version 2.0 of the mod was rebuilt from the ground up: every vehicle is custom geometry driven by custom physics, every gun has real feedback, and every structure is terrain-adaptive architecture instead of box fills. Nothing in this mod is a repainted vanilla boat.

---

## What the 2.0 rework changed

| System | Before | Now |
|---|---|---|
| Airship | Vanilla boat with gravity off | Hand-built zeppelin model (envelope, fins, engine pods, spinning props, gondola) with 3D flight: thrust, banked turns, climb/descend, moored hover |
| Ships | Renamed vanilla boats | Custom cog geometry with fore/aft castles, mast, animated sail & pennant; buoyancy, wave bob & roll, hull damage with smoke, real shipwrecks that scatter loot |
| Player boat | Literally `new BoatItem(...)` | **Freebooter Sloop in a Bottle** — a craftable, sailable sloop with water-bound physics and A/D steering |
| Naval combat | Ships bumped into each other | Pirate raiders lead their shots and fire **arcing cannonballs** that explode on impact; boarding ejects the crew to repel you |
| Guns | Silent hitscan | Tracers, muzzle smoke & flame, layered gunshots, recoil kick, target knockback; the flintlock pierces up to 3 targets |
| Royal Longsword | Stat-stick | Active **Sovereign's Cleave** lunge + arc sweep that spares your recruited crew |
| Structures | Solid box fills on flat ground | Round towers with conical roofs, crenellated curtain walls, gatehouses, furnished great halls with thrones, working farms, piers with pilings, sky platforms on scaffolding — all **terrain-adaptive** with stamped foundations |
| NPC names | 16 shared names | Culture-specific name pools with epithets ("Salt-Marie the Tide-Cursed · Pirate") |
| Crewed ships | Crew clipped through hulls | Crew spawn seated on deck; pirates repel boarders |
| Art | Flat procedural rectangles | Shaded, outlined, noise-grained pixel art generated to match the exact UV layouts of the new models |
| Knight archetype bug | Randomly re-rolled to another culture 75% of the time on loadout | Fixed: explicit spawns keep their culture |

---

## Quick start

1. Install Minecraft **1.21.1**, Fabric Loader **0.16.5+** and Fabric API.
2. Drop `rival-realms-*.jar` into `mods/`.
3. Open the **Rival Realms** creative tab — blocks, armory, vehicles, trade goods and crew, in that order.
4. Try these commands (cheats on):

```text
/rivalrealms landmark royal_city     # build a full city at your feet
/rivalrealms airship                 # launch a pilotable zeppelin
/rivalrealms ship                    # launch a sloop on nearby water
/rivalrealms convoy                  # stage a merchant-vs-pirate naval battle
/rivalrealms spawn pirate 4          # a Freebooter press gang
```

---

## Vehicles

### Airship
A real zeppelin with an envelope, tail fins, twin engine pods with spinning propellers and an open gondola with three seats.

- **Right-click** the ship to board (up to three players)
- **W/S** thrust along your look direction, **A/D** lateral thrust — the hull *banks* into turns
- **Space/Shift** climb and descend
- Engine drones, exhaust puffs and water spray are all simulated
- Left unmanned it hovers where it was moored; hull damage is accumulated and a hard hit detonates it (it drops airship metal and ingots)
- Deployed from the **Airship Kit** (craftable) or `/rivalrealms airship`

### Sailing ships
The **Royal Jewelry Trader** (pale oak, crown-emblazoned sails) and the **Freebooter Raider** (tar-black hull, skull canvas) share custom cog geometry: raised fore and aft castles, a working mast with yard, a sail that billows under way and a pennant that flutters.

- True buoyancy: hulls sit on the waterline, bob and roll on the swell, and grind to a halt beached
- Sustained hull damage vents smoke before the ship **breaks apart** in an explosion of planks and loot
- The trader cruises trade routes and *flees from attackers*; sinking her spills jewelry, coins and emeralds across the waves
- The raider **leads its targets**, fires explosive cannonball broadsides from gunnery range, then closes to plunder; an emptied trader is abandoned
- **Board a raider at your peril** — the crew jumps off the ship and attacks you
- Both spawn fully crewed from their creative-tab kits; `/rivalrealms convoy` stages the whole duel

### The Freebooter fleet
Raider sloops patrol the seas even when no merchant is around, and they do not
spare **player crews**: an occupied sloop on the horizon gets hunted and
bombarded like any prize. Three raids in ten escalate to the **Freebooter
Flagship** — a true galleon (double masts, crow's nest, bowsprit, two-tier
stern castle, gold gallery windows and gun ports along the waterline) with a
140-point hull, slower but tougher, firing **three-ball fanned volleys** with
a deeper thunderclap. Sink one for coin, jewelry, cannonballs, a flintlock,
and sometimes a recruitment contract. Also spawnable via the **Flagship Crew
Kit** egg.

### Freebooter Sloop
Your own ship. Craft a **Sloop in a Bottle**, use it on water, right-click to board:

- **W/S** to sail, **A/D** to steer — thrust only bites when the hull floats
- Two seats, wake particles, paddle-loop ambience
- Beach it and it grinds to a stop; sink it and it drops planks

### Cannonball
Lob it by hand (heavy arc, detonating impact) or let the pirates deliver it. Excellent against walls, ships and stubborn sieges. Craftable in batches of four.

---

## Armory

| Weapon | Feel |
|---|---|
| **Royal Longsword** | Diamond tier. Right-click **Sovereign's Cleave**: a lunging sweep that damages and launches everything in the arc — never your own recruited crew |
| **Frontier Revolver** | Fast, precise hitscan with tracer, muzzle flash, smoke, recoil and knockback |
| **Pirate Flintlock** | One thunderous shot every ~2s that **pierces up to three targets** behind a wall of powder smoke |
| **Cannonball** | Thrown siege munitions with a real blast |

Culture NPCs use their own gear *with matching feedback*: outlaws and pirates fire smoky gunshots, sky captains loose crossbow bolts, knights hold the line with sword and shield.

---

## The four factions

| Faction | Archetype | Identity |
|---|---|---|
| **Crownlands** | Knight | Blue and steel, helms with nasal guards, great halls and citadels |
| **Freebooters** | Pirate | Red bandanas, gunpowder, harbors, shipyards and raiders |
| **Dustwalkers** | Outlaw | Brim hats, revolvers, frontier towns and outposts |
| **Skybound** | Sky Captain | Goggles, crossbows, skyports and airship yards |

Survivors are one server-authoritative entity with data-driven cultures: real equipment, stats, targeting, persistent NBT, culture-specific names with epithets, and job titles.

### Temperaments — some attack you, some are chill until you attack
Every free survivor rolls a **temperament** once, colour-codes its name tag, and keeps it for life:

| Temperament | Name tag | Behaviour |
|---|---|---|
| **Bloodthirsty** | Red | Attacks on sight (raiders are most often bloodthirsty) |
| **Wary** | Gold | **Chill until you attack** — then repays every hit, and remembers |
| **Good-natured** | Green | Never starts fights, flees when struck, and **opens a trade screen** |

Culture-weighted: pirates/outlaws skew hostile, knights/sky captains skew wary, everyone has a chance at any roll. Roll too slow? They won't — gunfire cadence, sprint speeds and ranged damage were all retuned in 2.1.

### Random loadouts — some even wear diamond
Gear is luck of the draw on first spawn: leather is common, chain and iron less so, and **a rare survivor struts the frontier in full diamond** (up to ~8% per slot for elite cultures). Better pieces can already carry Protection or Sharpness, pirates roll real blade tiers (wooden → diamond cutlass), and everything drops on death — kill a diamond knight, take the plate. Assigned ship crew and settlement staff always stay combat-ready regardless of roll.

### Player-like behaviours
- Wounded survivors **pull out bread and eat to heal** — nibble sounds, crumbs, hearts
- They **mutter one-liners** in your action bar when you walk past (pools per temperament)
- Good-natured folk **accept food from anyone** and puff hearts
- Trusted companions (95+ trust) **press small gifts into your hand** — emeralds, arrows, snacks
- Good-natured survivors **trade like villagers**: staples for emeralds, culture goods (knights sell golden carrots, sky captains sell XP bottles), they buy coal and leather, and one rare **7-emerald + 2-iron → diamond** deal

### Recruitment & trust
- Right-click an unclaimed survivor with a **Recruitment Contract** to recruit (start trust 80/100)
- Feed companions bread, cooked salmon, apples, cooked beef or golden carrots to raise trust
- Trust decays when you are away and craters when you hurt them — **low trust triggers betrayal**
- Empty hand toggles **follow / hold position**; **sneak + empty hand** releases them honourably
- Companions avenge their owner's last attacker

### Farmer houses & player-style farming
Every settlement type now includes **farmhand cottages** — cozy plank-and-log houses with a composter, barrel, lantern and hay-bed, a porch, and a scarecrow watching the rows. Mill, town, fortress, outpost, harbor and skyport all have them; standalone **Farmsteads** (two cottages, wheat field, vegetable rotation plot, hay stacks, fenced stock pen) generate naturally in temperate biomes.

Farmers are not villagers. They carry the custom **Farmhand's Hoe** (iron hoe that tills a 3×3 patch for you) and work like players:
- patrol the fields by day, knock off at night
- **harvest crops the instant they mature** and replant the same crop on the spot
- **till fresh soil** when the plot needs more farmland
- **sow a rotation** — mostly wheat, sometimes carrots and potatoes
- drops land in the world and they pick them up like players do

Farmers roll temperaments like everyone else, so yes — some are **angry**.

### Settlements & jobs
Claimed settlements recruit guards and workers up to their level. Jobs (guard, builder, farmer, baker, herbalist, merchant, jeweler, blacksmith, mason, miner, scout — plus captain/sailor/gunner/quartermaster/navigator for Freebooter sites) move NPCs to worksites and feed the settlement economy. When food, materials and work are enough, the settlement **physically expands**: farms, workshops, towers, walls, warehouses, piers, hangars and moored airships appear district by district. Rival factions raid hostile settlements when diplomacy is at war.

---

## Structures & world generation

Every build is terrain-adaptive: it samples the surface, packs a foundation under every column, clears stray vegetation, and sits correctly on slopes.

- **Fortress / Citadel** — round towers with conical roofs, crenellated curtain walls, twin-tower gatehouse, furnished great keep (throne dais, long tables, chandelier, armory), stables, farms
- **Town / Royal City** — market crossroads with furnished cottages, a tavern/saloon, well, market stalls, lamp-lit streets, warehouses and a palace colonnade
- **Harbor / Shipyard** — plank terraces, tavern, piers with pilings and lanterns, cranes with cargo, moored boats, storage yards
- **Skyport / Airship Yard** — elevated metal platforms on scaffolding legs, hangars, a dock tower, moored zeppelini* — mooring masts with chains
- **Outpost** — palisades with fence gates, watch tower, campfire, farm
- **Mill** — stone windmill tower with a fence-and-canvas sail pinwheel, miller's cottage, wheat terraces
- **Ruin** — a shattered watchtower breached like it lost an old siege, rubble drifts, cobwebs, a squatter camp under a wool lean-to
- **Graveyard** — podzol memorial ground behind a weathered ring wall: rows of headstones, dead bushes, a soul lantern, and one keeper; loot chest for the brave
- **Farmstead** — two farmhand cottages, a wheat field, a carrot/potato/beetroot rotation plot with a water channel, scarecrows, hay stacks, a fenced stock pen, and the farmhands + guard who live there

New chunks can receive a deterministic, biome-aware site: oceans get harbors, deserts get frontier towns, mountains get skyports, snow gets fortresses, and the temperate roll now spans **eight** silhouettes — towns, royal cities, fortresses, citadels, mills, ruins, graveyards and **farmsteads**. Every site is **inhabited from the moment it generates**: a guard and its first workers (farmhands first) spawn with the buildings, then the slow cadence grows the population. Sites persist in world data with duplicate prevention, and generation never force-loads distant chunks.

---

## Command reference (permission level 2)

```text
/rivalrealms spawn <knight|pirate|outlaw|sky_captain> [1-12]
/rivalrealms build <knight|pirate|western|sky>
/rivalrealms landmark <fortress|citadel|town|royal_city|harbor|shipyard|skyport|airship_yard|outpost>
/rivalrealms claim | bases | locate | jobs
/rivalrealms assign <role> <survivor>
/rivalrealms diplomacy <faction_a> <faction_b> <-100..100>
/rivalrealms airship | ship | convoy
/rivalrealms info
```

---

## Crafting highlights

```text
Airship Kit        = leather + iron ingots + copper block
Sloop in a Bottle  = glass + ship planks + stick
Cannonball (x4)    = iron ingots + coal block
Royal Longsword    = diamonds + gold ingot + stick
Revolver           = iron ingots + redstone + planks
Flintlock          = iron ingot + flint + gold nugget + dark oak planks
Recruitment Contract (x2) = paper + royal coin
```

Block palettes (Crown Brick, Castle Stone, Castle Tiles, Royal Wood, Ship Planks, Frontier Planks, Airship Metal) all convert from vanilla materials, and the **Realm Banner** is the claimable settlement marker — right-click it to claim or inspect.

---

## Building from source

Targets: Minecraft `1.21.1`, Yarn `1.21.1+build.3`, Fabric Loader `0.16.5`, Fabric API `0.116.7+1.21.1`, Java 21.

```bash
./gradlew --no-daemon clean check build
```

The release jar lands in `build/libs/`.

### Asset & source validation

The repo ships three dependency-free preflights:

| Script | Purpose |
|---|---|
| `python3 scripts/make_art.py` | Regenerates **every** texture from code: shaded block palettes, weapon icons with outlines, faction skins with hats/goggles/bandanas, and the 128×128 / 256×128 vehicle sheets painted to match the models' UV layouts |
| `python3 scripts/bughunt.py` | JSON validity, asset/texture reference integrity, recipe↔registration consistency, creative-tab catalog, jar contents |
| `python3 scripts/apicheck.py` | Cross-checks every referenced API symbol against a stub universe mirroring the Yarn 1.21.1 mappings, and runs a real `javac -proc:only` pass when a JDK is present |

CI runs all three, then compiles and remaps with Gradle and uploads the release jar. (The sandbox this mod was reworked in had no Maven access, so the authoritative compile happens in CI — if anything fails to compile there, the API checker output will name the culprit.)

---

## Performance & safety

- World generation runs on eligible chunk loads only, never from a global tick loop
- Settlement ticks skip unloaded areas; raider spawns are capped per settlement
- Pirate prey scans are throttled to a 5-second sweep
- Piloted vehicles simulate on the riding client (vanilla boat-style) and stream to the server — responsive on dedicated servers
- Crew riding ships are excluded from pathfinding so they never fight their own vehicle
- All entity spawn items act server-side only; client renderers live in the client source set

---

## License

MIT — see `LICENSE`.
