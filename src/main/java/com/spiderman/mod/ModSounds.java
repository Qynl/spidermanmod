package com.spiderman.mod;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * ULTIMATE SOUNDSCAPE - Immersive Spider-Man audio.
 * - Web shot, splat, zip with variations
 * - Sense tingle, stage up, transform
 * - Wall run, wall jump, swing, dive, slingshot
 * - Combo, style, epic effects
 */
public final class ModSounds {
    public static SoundEvent WEB_SHOT;
    public static SoundEvent WEB_SPLAT;
    public static SoundEvent WEB_ZIP;
    public static SoundEvent WEB_TRAP;
    public static SoundEvent WEB_BURST;
    public static SoundEvent WEB_IMPACT;
    public static SoundEvent SENSE_TINGLE;
    public static SoundEvent SENSE_DANGER;
    public static SoundEvent STAGE_UP;
    public static SoundEvent TRANSFORM;
    public static SoundEvent WHEEL_TICK;
    public static SoundEvent WHEEL_OPEN;
    public static SoundEvent WALL_RUN;
    public static SoundEvent WALL_JUMP;
    public static SoundEvent SWING_LOOP;
    public static SoundEvent SWING_FLING;
    public static SoundEvent DIVE;
    public static SoundEvent SLINGSHOT;
    public static SoundEvent COMBO;
    public static SoundEvent STYLE_UP;

    private ModSounds() {
    }

    public static void register() {
        // Core webs
        WEB_SHOT = register("web_shot");
        WEB_SPLAT = register("web_splat");
        WEB_ZIP = register("web_zip");
        WEB_TRAP = register("web_trap");
        WEB_BURST = register("web_burst");
        WEB_IMPACT = register("web_impact");
        
        // Sense
        SENSE_TINGLE = register("sense_tingle");
        SENSE_DANGER = register("sense_danger");
        
        // Progression
        STAGE_UP = register("stage_up");
        TRANSFORM = register("transform");
        
        // UI
        WHEEL_TICK = register("wheel_tick");
        WHEEL_OPEN = register("wheel_open");
        
        // Movement - new!
        WALL_RUN = register("wall_run");
        WALL_JUMP = register("wall_jump");
        SWING_LOOP = register("swing_loop");
        SWING_FLING = register("swing_fling");
        DIVE = register("dive");
        SLINGSHOT = register("slingshot");
        
        // Style
        COMBO = register("combo");
        STYLE_UP = register("style_up");
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(SpiderManMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
