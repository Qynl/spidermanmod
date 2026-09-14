# HORROR PASS — design plan (NOT IMPLEMENTED YET)

Goal: shift Manhunt from "speedrun competitor" to **presence horror**. The engine
(player-shaped survivor, portals, beds) stays; the *experience* becomes dread.
Spoken lines get spooky, close-mic'd, and fired only at the perfect moment.

## 1. The three acts (replaces the flat HUNT phase)

| Act | Trigger | Behaviour |
|---|---|---|
| WATCH | first 2-4 min after `/start` | Keeps 24-40 blocks, mirrors your strafe, never attacks. You always find him looking. |
| STALK | after WATCH, or first hit on him | Closes distance **only while you are not facing him** (dot-product observation check). Freezes mid-step when you look. Weeping-angel lite, no teleport cheats except one silent burst-closing when unseen > 45 s. |
| HUNT | sustained eye contact > 3 s, or you attack him twice, or 6 min in | Full chase, bows, swords, portal logic as today. |

Escalation is stored in `ManhuntState` so restarts keep the act.

## 2. Dread systems (server, no mixins)

- **Signs**: every 4-9 min (and, once you have started a hunt before, rarely even
  pre-hunt) he makes one subtle edit within 12 blocks: closes your open door,
  rotates a block 90°, plants one cobblestone mid-path, turns a wall torch to
  face the wall. Paired with a subtitle-only whisper. Nothing breaks, nothing
  spawns. You just notice.
- **Bed invasion**: when you wake from sleep, once per night, chance he stands at
  the foot of the bed. One line. He does not attack while you're in bed.
- **Grave looting**: he already picks items up; give him intent - on your death he
  walks to the drop pile and keeps one piece of your gear, and says so.
  He then wears/uses it against you.
- **Dread budget**: global per-category cooldowns + per-night caps so every line
  stays special. Whispers only when he is within 24 blocks AND out of line of
  sight. Nothing fires twice within 90 s.

## 3. Client dread layer (fabric client APIs only)

- **Vignette**: `HudRenderCallback` radial darkness, alpha ramps up under 40 blocks.
- **Heartbeat**: client tick schedules a synthesized thump pair; interval and gain
  follow distance (90 bpm far, 150 bpm close).
- **Whistle motif**: a 4-note DSP-whistled lullaby fragment, plays only when he is
  close AND unseen - his audio signature. Stops the instant you look at him.
- **Nametag suppression**: renderer `shouldRenderName` false until < 12 blocks or
  mid-attack. He has no name floating over him in the dark.
- **Pale swap**: at STALK+ his skin swaps to a paler variant (second texture,
  same UVs) - wrongness you feel before you notice.
- **Silence as soundtrack**: no music ever; ambient layers are heartbeat, motif,
  and a sub-bass drone under 16 blocks.

## 4. Voice direction

One voice: low, dry, close-mic'd, almost bored cruelty; whispers marked in
subtitle text (`[whispering]`). Subtitle-only ghost lines play at volume 0 so
they read but are not heard. All lines registered in `sounds.json` with
subtitles; `HunterVoice` enforces the dread budget.

### Line table (28 lines, TTS in 3 batches of <=10)

WATCH: "You feel that? That's me, looking." / "Not yet. But soon." /
"I've been here since your first night. You never checked."
START: "You said the word. You can't unsay it." / "Oh, good. You're ready to be afraid."
SIGHT: "There you are." / "I counted your heartbeats from the ridge. You skip them when I'm close."
SEARCH: "I don't need to see you. I just need you to exist." / "Keep breathing. It's the loudest thing you do."
WHISPER (subtitle-only): "don't turn around." / "I'm not behind you. Check anyway."
PORTAL: "Your door home? I'm standing in it." / trap done: "I closed it gently. Doors deserve that."
NETHER: "It's warm down here. Like the inside of something."
END/BED: "I made your bed. Isn't that what friends do?" / "Sleep tight. I'll be right here when you get back."
RESPAWN: "Fifteen seconds. Count them with me." / "You killed me. I folded it into paper and kept it."
HURT: "Yes. That's the part I like." / "Was that all? I waited so long for that."
KILL: "Shh. It's over. I'm here." / looting: "I'll keep this. For you."
DEATH: "Clever prey." / "I'll remember this bed."
EAT: "I eat what I catch. Remember that."
BOW: "Hold still. This is precision work."
WAKE: "Sleep well?" / "You talk in your sleep. You said my name."
SIGNS (subtitle-only): "I moved something small. You'll notice tonight." / "Your door was open. It isn't."

### Synthesized (DSP, stdlib - no TTS cap): heartbeat thump pair, sub drone,
whistle motif, whisper bed noise, door-close creak for signs.

## 5. Rename/tone

Mod id stays `manhunt` (session/branch fixed), display name becomes
"MANHUNT: The Resident" with horror copy in fabric.mod.json + README. Creative
presence: none - he is not in any tab, only in the world.

## 6. Implementation order (when you say go)

1. `HunterVoice` + line registry + dread budget + whisper/subtitle plumbing.
2. TTS batch A (10 lines) -> ogg + sounds.json + bughunt green.
3. Acts system (WATCH/STALK/HUNT) + observation checks + burst closing.
4. Signs + bed invasion + grave looting hooks.
5. DSP SFX (heartbeat, drone, motif, creak) via a revived stdlib synth tool.
6. Client dread layer (vignette, nametag, pale skin, motif scheduling).
7. TTS batches B and C (remaining 18 lines) across following turns.
8. Gates: bughunt (sounds<->ogg), apicheck, CI green, push.

Every step ends with both local gates passing and a commit; audio batches are
the only multi-turn part (10 clips per turn cap).
