# Manhunt: The Resident Hunter

> **Another player lives in your world. He just hasn't decided to kill you yet.**

Manhunt adds exactly one entity with exactly one idea: a hunter who is *mechanically a player*. Not an NPC with a scripted walk cycle - a `PlayerEntity` with a player inventory, a hunger manager, attack cooldowns, armor slots, a bow and a bed, driven by a survival state machine instead of a keyboard.

## How he lives

Before you type `/manhunt start`, he simply survives, anywhere, in any world, with any structure mods installed:

- **Gathers like a player** - punches trees with his fists, then mines stone, coal, iron and diamond at real tool speeds with real drops, choosing his next block from what his inventory is missing.
- **Crafts like a player** - walks the real recipe registry: planks, table, pickaxes, furnace, swords, iron armor, bow, arrows, shield, bed. Because he crafts through recipes, *your* mods' tools and gear are on his tech tree too (tag-driven: `c:wooden_pickaxes`, `c:iron_pickaxes`, ...).
- **Smelts like a player** - real smelting recipes, one coal per four firings, in his pack.
- **Eats like a player** - his hunger manager drains as he runs and mines; when it drops he hunts cows and pigs, or sits down with whatever is edible in his inventory.
- **Equips like a player** - best armor into armor slots, best sword into his hotbar selection, bow in hand when he needs range.
- **Moves like a player** - no navigator: he faces his goal, walks, jumps shin-high geometry, swims. Modded structures are just terrain to him.
- And sometimes, across a valley, he stops and **stares at you**.

## How he hunts

`/manhunt start` flips one bit. From that moment he always knows where you are:

- **Same dimension** - he walks to you. Bow with lead-corrected aim and line-of-sight checks at range; sword and attack cooldowns up close.
- **You fled to the Nether** - the mod remembers the exact portal you stepped through. He walks to it and either **steps through after you**, or **traps it**: a ring of cobble carried in his own inventory, walled around your way home, and he patrols three blocks from it waiting for you to step out.
- **You ran for the End** - he camps the stronghold frame: pitches his **bed** beside the portal, sets his respawn there, and holds the ground. Half the time he just follows you through instead.
- **You kill him** - he respawns. Fifteen seconds later he wakes at his bed (or world spawn the first time) with his packed inventory, exactly like a player reconnecting.

## Commands

```text
/manhunt spawn    # put him in the world near you (he starts living his life)
/manhunt start    # he knows where you are now. run.
/manhunt stop     # called off; he goes back to punching trees
/manhunt status   # phase, distance and dimension
```

## Repository layout

```
src/main/java/com/manhunt/
  Manhunt.java            entity registration + the director: portal marks,
                          player tracking, respawn scheduling
  entity/HunterEntity.java  the whole hunter: phases, survival, crafting,
                          combat, portal camping, beds, locomotion
  world/ManhuntState.java   PersistentState: hunt flag, portal marks per
                          dimension, bed position, packed inventory
  command/ManhuntCommands.java
src/client/java/com/manhunt/client/  player-model renderer with his own skin
tools/make_art.py         stdlib pixel studio: 64x64 player-UV skin + icon
scripts/bughunt.py        pre-compile gate: resources, lang, PNG dimensions,
                          import resolution, internal call arity
scripts/apicheck.py       cross-checks every referenced API symbol against
                          Yarn 1.21.1 stubs (scripts/apicheck_stubs.py)
```

## Development

```bash
python3 scripts/bughunt.py     # resource + internal-API coherence, no JDK needed
python3 scripts/apicheck.py    # API cross-check (adds a javac layer when a JDK exists)
./gradlew build                # authoritative compile + remap (CI runs it)
python3 tools/make_art.py      # repaint the hunter
```

## License

MIT - see `LICENSE`.
