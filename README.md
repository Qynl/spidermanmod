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

## Sieges & the wide world

- **Real wars between neighbours**: hostile settlements within reach muster a visible warband — captain, soldiers, and a scout holding a spyglass — swear the war oath, and march out in column. At the enemy walls the war cry rings, defenders taunt from the ramparts, and the assault comes over the top on **real placed ladders**; victory hands the town to the besieger

- **SIEGES are events**: bells ring, defenders rally under Speed/Resistance buffs, a Royal Messenger sprints for the nearest friendly town, allied relief columns march, civilians flee outward from the fighting, and when the dust settles the town either holds (fanfare, feast) or bears **grave rows outside its walls**. Arrive halfway through and you'll have to piece together what started it — the chronicle knows
- **Player camps**: craft a Camp Kit (wool + campfire + lead) and raise a real home — tent, bed, chest, map table, weapon rack, lanterns. It's claimed as a settlement in `/rivalrealms bases`; come back across days and it **grows trophies and a banner** into a landmark of the roads
- **The peaceful road**: lost children (reunite them for Hearthfolk gratitude), stranded merchants (an iron ingot repairs the axle — half the load is yours), horse disputes, roadside festivals with live bard music, gossips who recite the chronicle and ask if it really happened that way, and travelers who greet you **by your reputation title**
- **Disasters**: storms walk lightning across fields, earthquakes shake the ground and drop gravel — settlements drain and the chronicle remembers
- **Seasons**: 8-day spring → summer → autumn → winter. Winter squeezes the pantries; autumn brings **harvest festivals** with music in every fed settlement
- **Hidden civilizations**: rare hermitages (mossy cottages outside every faction, a hermit, an old friend's grave) and **world anomalies** — crying-obsidian craters, ever-glow mushroom groves, haunted stone circles with a guardian
- **Abandoned places return**: empty villages smoke again when new settlers arrive
- **Expeditions**: big settlements send pioneers into the wilds to found new homes — civilization spreads itself

## NPC souls

- **Traits**: Brave (aims faster, never breaks), Cowardly (flees below 40% health, slow to fire), Suspicious (accuses at first sight of a drawn crossbow), Loyal (trust barely cools), Curious (drifts toward strangers), Greedy, Ambitious — rolled once, persisted
- **Hobbies**: NPCs play music, go fishing by the water, and gather in **friend circles** that laugh together; **rivals** shove each other when no one's watching
- **Weddings**: two friends become family at a feast — chronicle entry, hearts, hearthfire song
- **Rumors cut both ways**: deeds of valor (found a child, saved a caravan, fought off raiders) travel the same mouth-to-mouth road as crimes — and raise your **local** standing town by town
- **Recognition**: NPCs greet you by name, title and the armor you wear — "Legend of the Crownlands! An honor."

## Riches & roads

- **Treasure maps**: craft from paper + compass. Never "go to X" — a bearing from the nearest town, a pace count, a riddle, and green motes where the X sleeps under dark earth
- **Traveling merchants**: caravans walk settlement to settlement (and pirates still cut the roads)

## Contracts & crime

- **Notice Boards** (craftable, built into towns and hamlets) post real work born from the simulation: **BOUNTY** a champion raider's head, **HUNT** marauders, **DELIVER** word to a sister town, **GATHER** grain/iron for a hungry pantry, **SCOUT** a strange place. Rewards: royal coin + reputation, paid on the spot. `/rivalrealms contracts` lists and accepts anywhere
- **Work expires**: ignore it too long and someone else takes it ("Too slow, friend. There's always more trouble.")
- **CRIME pays back**: be hated enough and the settlement **posts you WANTED** — voiced, chronicled, and their guards hunt you. Sneak up on a guard with royal coins to **pay the fine** and buy your name back

## The dialogue engine

- **Context, not dice**: what an NPC says is chosen from what is true right now — your reputation, the hour (night warnings, bright morning calls), whether they've met you before (**recognition**: "Back again? You've the look of someone with unfinished business."), whether their settlement **recently bled** (survivors recount the attack, in their own words), and the chronicle's old stories, told where they happened
- **Interruptible**: trouble cuts sentences off — "Wait... what's that? Did you hear that?"
- **NPC-to-NPC conversations**: two friends actually talk out loud about the latest realm news, the second voice replying a beat later — and the two versions don't quite match, because news never travels clean
- **Voiced rumors**: the moment word of your deeds (or crimes) reaches a settlement, someone *says it out loud* in the square; roadside gossips are voiced too
- **Voice profiles**: every NPC derives a stable pitch from who they are — two actors, five registers, plus emotional delivery per moment (startled pitch, low night murmurs, shouts over the walls, children at play)
- **Settlement atmosphere**: farms echo with work shouts, children playing, neighbors trading rumors — each place sounds like what it is

## Bard & voice

- **Six composed melodies** play in the world: Hearthfire (taverns), March of Banners (sieges), Harvest Reel (festivals), Dirge (funerals), Wanderer's Rest (camps), Victory Fanfare (held walls)
- **A performed voice catalog (89 lines and growing)** for almost every moment: sieges, the town crier announcing big news aloud to every player, warm and folksy hellos ("Well hello there, Mister!"), **per-culture greetings** (knights, pirates, outlaws, sky captains, hearthfolk — each says hello its own way), **and the hello changes with your reputation** — the hated are told to state their business and move along, true friends of the town get a hero's welcome; voiced idle musings by the fire, feeding praise, the recruitment handshake, bitter betrayals, the THIEF! shout over stolen crops, "lower that crossbow" warnings, **trader patter and haggling**, **the harvest market herald**, farewells, celebration, the lost child's plea, **battle barks over the din and raider threats at travelers**, **hired swords checking in with their captain**, **tense standoffs when rival banners share a street**, **children who demand their favorite story again**, the **wedding officiant's blessing**, the **funeral eulogy** when a named settler falls, **the cry of an empty granary**, **cheers when a settlement grows**, **storm and earthquake cries**, **old-timers muttering** about buried treasure, **quarrels between rivals**, and **grief-struck or triumphant retellings** of a town's scars — the same story, told in the mood it left behind

## The living realm

- **Settlements you can watch grow**: builders raise real cottages at the town edge, course by course, until a family moves in; farmers till new rows at the field margins every visit — a town is never finished

- **Every structure rebuilt from reference builds**: pirate **coves** now appear on the world table - a beached longboat with furled sail, canvas tents, a treasure course, a gibbet cage and ragged red flags; stables rebuilt as **stable-and-paddock yards** (cobble stem walls, hay stalls, cauldron troughs, a lamp by the yard gate - and no more free-running water); towns gain a **lockup** with barred cell, stocks and the wanted board; plus the drum gate, cove piers, shrines, galleons, watchtowers, mine adits, lumber camps and the rest
- **Every structure rebuilt from reference builds**: towns enter under a **white drum gate** with brick inlay and a great terracotta pyramid over a timber belfry; **cove piers** run out on piling legs to a bonfire platform lined with working **cannons** under a furled sail; **wayside shrines** stand hooded with flowers and a traveller's candle where the roads pass; plus the galleon, watchtowers, mine adits, lumber camps, lighthouses, smithies, barbicans, cranes, hangars and pub fronts
's approach runs under a **half-timbered barbican** (twin drum towers, raised portcullis, gate room over the road); harbors and yards raise the **grand timber crane** with railed jib and chain-hung cargo; airship yards keep ships in **arched metal hangars** with cradles and rigging; every tavern wears a **hanging shield sign, striped awning and benches**; plus striped lighthouses, smoking smithies, stilt fishing huts, battering rams, arcaded waterfronts, water towers and maypole greens
- **Every structure rebuilt from reference builds**: harbors gained a stone-arcaded waterfront warehouse with a timber loft; mills are tapered stone-and-timber with a balcony ring and dark cap; graveyards wear gothic iron fences with an arched gate, standing crosses and glowing crypts; raider camps raise a log-ribbed war longhouse-tent inside a skull-crowned spiked palisade; ruins have a collapsed sister tower; farms got a hay barn, pond and orchard; the hamlet a maypole green; the western town a water tower
- **Built like the great fortresses**: curtain walls with pilaster ribs, deep two-height crenellations and mounted lanterns; keeps with framed tall windows; towers crowned in trim
- **Masonry you can read from horseback**: towers with battered footing rings, arched doors, trim string courses and machicolations under the merlons; keeps with corner turrets, a grand stair and flanking banners; curtain walls with true arrow-slit loopholes and corner bartizans; cottages with flower boxes, window hoods, lamps by the door and a fenced flower plot out front
- **Structures stand on solid ground**: every building packs its footprint flush down to real terrain (no arches of air, no floating edges — ever), and cottages are properly built: stone footing, timber framing, shuttered windows, a porch over the recessed door, a brick chimney, a hearth and a woodpile
- **`/rivalrealms rebuild`**: stand beside any settlement and raise it anew with the latest builder - the fix for places your world generated under older, clumsier drafts (residents step aside and return, recruited companions stay loyal, your chronicle remembers the reconstruction)
- **Sky towns are grounded: skyfolk build one cohesive dirt terrace — workshops, houses, a farm strip and a single mooring mast with chained lines, the airship riding at the masthead; no floating slabs, and new sites are rejected unless the whole ground is solid and level
- **Expeditions resolve for real**: fed fortified towns send named pioneers into the wild — if two or more survive the walk, they claim the land, raise walls in their faction's style, and a brand-new settlement joins the map (with a homecoming cry); if not, their town mourns and the chronicle remembers them as lost

- **Factions run on wealth** (0–100): surplus food and caravans fill the treasury, raids and famine drain it. Wealthy factions field enchanted veterans and an extra patrol; bankrupt ones send people out in rags
- **Settlements live and die**: they eat, grow when fed, shrink in famine, empty out and get abandoned — and a settlement that loses every defender in a war **changes banners** for real
- **The chronicle** (`/rivalrealms chronicle`): every capture, famine, plague, birth, skirmish and slaying is written into persistent world history with in-game day numbers
- **Generations**: fed towns raise children who shadow the adults, grow up (~30 min), take their parent's trade and carry the family surname
- **Relationships**: settlers carry surnames, siblings and friends; kill someone and their kin hunt you longer, mourn, and spread the news
- **Rumors travel**: witnesses whisper crime news mouth-to-mouth — and when it reaches a settlement, its **local standing** with you drops (separate from faction-wide reputation)
- **Local reputation**: each settlement keeps its own score on you. Friendly towns add a gold deal to the trade menu; hostile ones refuse to trade at all and their guards hunt you (`/rivalrealms standing`)
- **World events**: famine, plague (herbalists contain it), merchant booms, marauder uprisings, refugee arrivals and border skirmishes between hostile neighbours
- **Roads**: caravans physically walk between known settlements — until a hostile camp parks astride the road and cuts the route (the chronicle notices)

## Martial rework

- **Guns**: the revolver is a real six-shooter now — per-shot cylinder persisted on the gun, spread that grows as you fan the hammer, brass casings, layered cracks, flint-click reloads (auto when empty, sneak-use to reload early). Flintlock: one piercing thunderclap, full ramrod reload. Blunderbuss: two drums, six tracered pellets a shell, recoil that shoves you
- **NPC gunfights**: ranged survivors aim like players — they track, hold fire without line of sight, need settling time before the first shot, **lead moving targets**, and tighten up the longer you're in their sights
- **Sword slashes**: the Royal Longsword draws a crescent on every landed hit with an extra shove, and Sovereign's Cleave rolls forward as a visible wave

## Build the realm

- **Nine new hand-painted building blocks**, all craftable and all used by the world generator: Gilded Brick (gold-seamed masonry), Crown Pillar (fluted, gilded capital), War Council Table (campaign map under glass-free parchment), Weapon Rack (hangs sword, spear and axe — thin wall piece you can shoot past), Trophy Skulls, Hearth Lantern (warm full-brightness amber glow), Supply Crate (crown-branded), Road Stone (wheel-rut cobbles), Arrow Slit Wall (real see-through sight line for defenders)
- **They appear everywhere**: keeps and citadels grow arrow slits, war councils and weapon racks; fortresses pave their yard in road stone with lantern-lit gates; marauder camps raise trophy-skull totems around a war table; hearthfolk hamlets line their lane with hearth lanterns and supply crates; old-world towns pave the market heart; ruins and shrines scatter gilded relics
- **Every custom block now drops itself** when mined — the older palette blocks silently dropped nothing before this pass

## Roads & riches

- **Treasure shrines** dot the wilds regardless of biome: gold circle, four gilded pillars, braziers, one guarded chest — and yes, the keeper is a Custom-culture veteran
- **Warcamps raid settlements**: a third of all settlement raids are Marauder warbands ("WARBAND sighted…")
- **Hearthfolk caravans** walk the roads (1-in-5 wanderer groups): traders who buy your goodwill in emeralds
- **Travel food**: Hardtack (3 wheat), Frontier Stew (full meal in a bowl, sold by caravans), Hearthfolk Mead (honey + wheat, +nutrition)
- **Item art pass**: every 16×16 icon now gets a crisp dark outline + edge highlight for hotbar readability; blocks gained bevels and brushed-metal detail; the mod icon is a full sunset siege scene
- **Knightsword redo**: the Royal Longsword icon is a proper fullered greatblade — bright cutting edge, gemmed crossguard, wire-wrapped grip, ruby pommel — no more flat stick
- **Knight redo**: crimson plume on the great helm, gold crown emblem on the tabard, breastplate ridge, steel bracers and plate greaves
- **Ranged crews fire from the chest**: gunfire and crossbow bolts (and their muzzle smoke) now originate from the middle of a shooter's body, so nobody shoots out of their own face or through the weapon sprite

## Realms at war

### Marauders — the fifth faction
Red-handed raiders at war with the **entire world** (-75 with every faction, in writing). **Every Marauder is Bloodthirsty** — the red name-tag is a promise. They pitch warcamps in the wastes (skull totems, hide tents, bone-fires), march in **wanderer warbands**, and man camps with a champion-rolled **Warlord** and roaming raiders. `/realm build marauder` raises a camp anywhere.

### The Hearthfolk — the quiet folk
A sixth culture of farm-and-forge villagers who **never start fights**: mostly Good-natured (green) with the rest merely wary. Hamlets of three cottages, gardens, a well and a **smith who forges real weapons** (anvil rings, sparks, occasional Sharpness blade). Cross them — attack, aim at them, or steal from their fields — and grudges form like everywhere else. `/realm build hearthfolk`.

### Social AI: grudges, witnesses, suspicion
- **Witnesses**: attacking an NPC in sight of their faction-mates drags everyone into the fight — they all remember your face for **an hour**
- **Grudges close hearts**: marked players are hunted on sight (20 blocks), refused trades ("turns away from you coldly"), and grudges **survive world saves**
- **Aim suspicion**: hold a drawn crossbow on someone and they'll warn you once — keep aiming and even patient folk decide you meant it
- **Crop theft**: harvesting mature crops inside a settlement's fields costs reputation (-4) and turns farm folk hostile — someone always sees

### New arsenal
- **Blunderbuss** — six-pellet scattergun, devastating up close, kicks hard
- **Halberd** — true 1.5-block reach via attribute modifiers, heavy damage, active sweeping thrust that knocks a rank back and weakens it
- **Warhorn** — rallies allies in 24 blocks with Speed + Strength (recruited crews and all non-hostile folk answer)

Guards now **walk patrols** instead of standing post like statues; blacksmiths in any settlement forge weapons at their worksite.

## War & glory

### Reputation
Every kill echoes across the realm. Murder a faction's people and your standing drops (worse if they weren't fighting you); defend yourself and it merely dips. Cross into the negatives past **-40** and that faction **hunts you on sight** — even their patient farm-folk guards. Mending ties costs **4 royal coins** via `/rivalrealms rep gift <faction>` (+8), and the Crownlands quietly respect every Freebooter you take down in a fair fight. `/rivalrealms rep` shows all four standings.

### The Field Cannon
A craftable, placeable siege gun: aim it while placing (it faces you), **load a cannonball**, use it again to fire a live physics ball downrange — thunderclap, smoke ring, recoil, no aim assist. Recipe: iron + iron block + copper block + spruce base.

### Champions
12% of settlement guards and a quarter of ship captains are **Champions** — golden name plates, Sharpness-edged weapons, enchanted plate, +12 hearts. Drop everything, obviously.

### Advancements
The Frontier Calls → Gunslinger → **Master of Powder** (goal), plus Set Sail and Field Hand.

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
