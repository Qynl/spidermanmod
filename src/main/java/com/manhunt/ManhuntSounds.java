package com.manhunt;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every sound the haunting owns: spoken lines (synthesised voices, added to
 * sounds.json as their audio lands), subtitle ghosts (registered but played at
 * volume zero, so they are read and never heard), and the synthesized dread
 * layer (heartbeat, drone, motif, breath, creak, thunder).
 */
public final class ManhuntSounds {

    private static final Map<String, SoundEvent> VOICES = new LinkedHashMap<>();

    // ---- spoken lines -------------------------------------------------------
    public static final SoundEvent WATCH_A = voice("watch_a");
    public static final SoundEvent WATCH_B = voice("watch_b");
    public static final SoundEvent WATCH_C = voice("watch_c");
    public static final SoundEvent IGNITION = voice("ignition");
    public static final SoundEvent SIGHT_A = voice("sight_a");
    public static final SoundEvent SIGHT_B = voice("sight_b");
    public static final SoundEvent SEARCH_A = voice("search_a");
    public static final SoundEvent SEARCH_B = voice("search_b");
    public static final SoundEvent STALK_CLOSE = voice("stalk_close");
    public static final SoundEvent HUNT_OPEN = voice("hunt_open");
    public static final SoundEvent PORTAL = voice("portal");
    public static final SoundEvent TRAP_DONE = voice("trap_done");
    public static final SoundEvent NETHER = voice("nether");
    public static final SoundEvent END_BED = voice("end_bed");
    public static final SoundEvent END_WAIT = voice("end_wait");
    public static final SoundEvent RESPAWN_A = voice("respawn_a");
    public static final SoundEvent RESPAWN_B = voice("respawn_b");
    public static final SoundEvent HURT_A = voice("hurt_a");
    public static final SoundEvent HURT_B = voice("hurt_b");
    public static final SoundEvent KILL_A = voice("kill_a");
    public static final SoundEvent KILL_LOOT = voice("kill_loot");
    public static final SoundEvent DEATH_A = voice("death_a");
    public static final SoundEvent DEATH_B = voice("death_b");
    public static final SoundEvent EAT = voice("eat");
    public static final SoundEvent BOW = voice("bow");
    public static final SoundEvent WAKE_A = voice("wake_a");
    public static final SoundEvent WAKE_B = voice("wake_b");
    public static final SoundEvent DAWN = voice("dawn");

    // ---- subtitle ghosts: played silent, read in the corner of the screen ---
    public static final SoundEvent GHOST_TURN = ghost("ghost_turn");
    public static final SoundEvent GHOST_CHECK = ghost("ghost_check");
    public static final SoundEvent GHOST_LOOK = ghost("ghost_look");

    // ---- synthesized dread layer (audio arrives with the dsp tool) ----------
    public static final SoundEvent HEARTBEAT = sfx("heartbeat");
    public static final SoundEvent DRONE = sfx("drone");
    public static final SoundEvent MOTIF = sfx("motif");
    public static final SoundEvent BREATH = sfx("breath");
    public static final SoundEvent CREAK = sfx("creak");
    public static final SoundEvent THUNDER = sfx("thunder");

    private ManhuntSounds() {
    }

    private static SoundEvent voice(String key) {
        Identifier id = Identifier.of(Manhunt.MOD_ID, "voice." + key);
        SoundEvent event = Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
        VOICES.put(key, event);
        return event;
    }

    private static SoundEvent ghost(String key) {
        Identifier id = Identifier.of(Manhunt.MOD_ID, key);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    private static SoundEvent sfx(String key) {
        Identifier id = Identifier.of(Manhunt.MOD_ID, key);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static SoundEvent get(String key) {
        return VOICES.get(key);
    }

    /** Forces class initialisation, i.e. registration. */
    public static void boot() {
        // all registration happens in static initialisers
    }
}
