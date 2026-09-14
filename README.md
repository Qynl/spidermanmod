# MANHUNT: The Resident

> **Something lives in your world. It has been there longer than you have.**

There are no commands. There is no start button, no config menu, no `/manhunt`.
You load the mod, you play your world, and at some point you will notice that
the torches in your hallway changed. That is how it begins.

## What lives here

One entity: **the Resident**. Mechanically he is a player — inventory, hunger,
armor slots, a bow, a bed, attack cooldowns — driven by a survival state machine
instead of a keyboard. He mines, he eats, he equips himself, and he always,
*always* knows where you are.

But you will rarely see him do those things. You will mostly see the other things.

## How he wakes

Nothing starts the hunt. The hunt starts itself:

- **The Acknowledgement** — hold eye contact with him for three seconds. Not
  glance. *Look at him.* He will know. The sky will answer: rain from nowhere,
  one thunderclap, and a whisper you will not quite catch.
- **Day three** — if you never look him in the eye, dawn of the third day looks
  at you instead.

After that there is no off switch, only a **truce**. Let him lose sight of you,
or put four hundred blocks of stone between you, and he goes back to *watching*.
He never stops watching.

## Reading him

He does not announce himself with a HUD. He announces himself with **noise**:

| You hear | He is |
|---|---|
| Whispers, counting down | Stalking. The number is distance. At zero he is within arm's reach. |
| The whistle — three notes | Close, and you cannot see him. Look at him and it stops. Immediately. |
| Sudden rain | He has found you. Run. |
| Nothing at all | Watching. This is worse. |

## The haunting

- **Signs.** Your doors open themselves. Torches turn to soul torches. Cobble
  appears in clean rooms, one block every few minutes, each with a whisper.
- **Mirror hours.** Between 3:00 and 3:30 he stands forty-four blocks away,
  facing you, perfectly still. Approach him and he is gone. He was never there.
- **The count.** A descending number of whispered words. It is distance. Do the
  math while you can still do math.
- **False wakes.** Thirty percent of mornings you will "wake up" to find soul
  torches burning in your room and a piece of paper on the floor. The paper is
  renamed **"soon"**. Its lore is a tally. The tally is your deaths.
- **Bed invasion.** When you sleep, he stands at the foot of your bed. When you
  wake, he says good morning.
- **Grave shrines.** At night he walks back to the place you last died and
  stands over your dropped items, keeping vigil. He is very good at keeping things.
- **He answers chat.** Once per world. Say something in chat. Twenty to forty
  seconds later, in italics: *you talk too much.*
- **Animals know.** Dogs and cats flee eight blocks around him.
- **He wears your face.** Far away, his skin is your skin. Get closer and the
  hood returns. When he is actively hunting, the thing under the hood is pale.

## Your screen betrays you

A vignette tightens as he approaches. Your own heartbeat speeds up with
proximity. Under sixteen blocks there is a sub-drone you feel more than hear.
No music. Ever.

## The manhunt underneath

Strip the horror away and a survival AI remains — and it plays to win:

- **WATCH → STALK → HUNT.** Survival wandering, patient observation, then a
  walk-only pursuit. He does not sprint. He does not need to.
- **Resource loop.** Punches trees, mines stone and iron, eats when hungry,
  crafts tools, armor and a bow, and equips everything he finds.
- **Portal play.** He marks your portal use. In the Nether he traps; at your
  End bed he camps. Beat the dragon and he wants you home — *"I'll stand by
  your bed again, like always."*
- **Death is a door.** He respawns at his own bed, returns to your corpse, and
  the tally on the paper grows.

## Build

Fabric 1.21.1. Java 21. `./gradlew build` — the jar lands in `build/libs`.
Compatible with structure mods and datapacks: he interacts with the world
through tags and normal block behavior, never hardcoded content.

---

*He is not a boss. He is not a mob. He is a resident.*
