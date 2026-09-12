# Rival Realms

Rival Realms is a Fabric 1.21.1 mod about a world populated by **player-like survivors**. They have equipment, loot, factions, settlements, memories, and enough agency to become your best ally or your next rival.

This repository intentionally starts a new project; the old Spider-Man concept is not part of the design.

## The first playable slice

- **Four custom cultures:** Crownlands knights, Freebooter pirates, Dustwalker outlaws, and Skybound captains.
- **Player-like survivors:** custom skins, armor and inventories, melee/ranged combat, names, rival targeting, persistent NBT data, and loot from normal mob drops.
- **Relationships:** craft a Recruitment Contract to recruit a survivor, feed them to raise trust, order them to follow or guard, and risk betrayal when trust collapses.
- **Persistent settlements:** fortress, harbor, town, and sky dock claims are saved in world data. Realm Banners can be right-clicked to claim a custom settlement and inspect its owner, level, faction, and radius.
- **Faction diplomacy:** Crownlands, Freebooters, Dustwalkers, and Skybound have persistent relations. Hostile relations create real rival targeting and unlock settlement raids.
- **Settlement life:** claimed bases recruit guards and workers over time. Guards patrol their home radius and defend it without attacking their owner.
- **Jobs and growth:** builders create construction progress, farmers produce food, blacksmiths produce materials, and traders support both. When a settlement can afford it, a new physical district is built: farms, workshops, storage yards, towers, docks, and culture-specific defenses.
- **Culture set pieces:** `/rivalrealms build knight`, `pirate`, `western`, or `sky` creates a walkable fortress, harbor with a working vanilla boat, frontier town, or airship dock.
- **Vehicles:** `/rivalrealms airship` launches a rideable flying boat; the pirate boat item is also craftable.
- **Frontier weapons:** revolver hitscan and pirate flintlock, both server-authoritative and repairable.
- **Living-world encounters:** small survivor parties appear at a low rate rather than flooding the world.
- **Scattered world landmarks:** explored overworld regions can reveal persistent knight fortresses, frontier towns, pirate harbors, skyports, and defended outposts. Their style follows the surrounding biome and each includes themed loot, work areas, banners, and NPC residents.

## Commands

Commands require permission level 2:

```text
/rivalrealms info
/rivalrealms spawn <knight|pirate|outlaw|sky_captain> [count]
/rivalrealms build <knight|pirate|western|sky>
/rivalrealms landmark <fortress|town|harbor|skyport|outpost>
/rivalrealms claim
/rivalrealms bases
/rivalrealms jobs
/rivalrealms assign <role> <survivor>
/rivalrealms diplomacy <faction_a> <faction_b> <-100..100>
/rivalrealms airship
```

Right-click an unclaimed survivor with a **Recruitment Contract**. Right-click a recruited companion with an empty hand to switch between follow and guard. Feed them cooked beef or a golden carrot to build trust. Right-click a **Realm Banner** to claim a settlement or inspect one you own. Diplomacy values at or below `-50` count as hostile and can produce raids.

Use `/rivalrealms jobs` inside your settlement to inspect food, materials, construction progress, and assigned roles. To reassign a nearby survivor, target them with `/rivalrealms assign farmer <survivor>` or use `builder`, `trader`, `blacksmith`, `scout`, or `guard`. Settlement growth is automatic once workers have gathered enough supplies.

## Building

The project targets Minecraft **1.21.1**, Fabric Loader **0.16.5**, Fabric API **0.116.7+1.21.1**, and Java **21**. Use `./gradlew build` to create the remapped jar in `build/libs/`.

GitHub Actions runs the dependency-free `scripts/bughunt.py` before Gradle, compiles and remaps the mod, runs `check`, validates the produced jar, and uploads the release-ready artifact.

The structures are deliberately made from ordinary and custom blocks. They are not background decoration: doors, roofs, docks, loot spaces, towers, banners, and airship decks can be explored, defended, repaired, and expanded by players.

## Roadmap

The architecture leaves room for persistent faction diplomacy, base ownership, NPC construction jobs, siege events, more cultures, custom models, and generated settlements. The first slice is intentionally small enough to test and balance instead of pretending a large feature list is already fun.
