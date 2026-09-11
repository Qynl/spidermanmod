package com.spiderman.mod;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Custom sound events. Audio files live in {@code assets/spiderman/sounds/}.
 */
public final class ModSounds {
    public static SoundEvent WEB_SHOT;
    public static SoundEvent WEB_SPLAT;
    public static SoundEvent WEB_ZIP;
    public static SoundEvent SENSE_TINGLE;
    public static SoundEvent STAGE_UP;
    public static SoundEvent TRANSFORM;
    public static SoundEvent WHEEL_TICK;

    private ModSounds() {
    }

    public static void register() {
        WEB_SHOT = register("web_shot");
        WEB_SPLAT = register("web_splat");
        WEB_ZIP = register("web_zip");
        SENSE_TINGLE = register("sense_tingle");
        STAGE_UP = register("stage_up");
        TRANSFORM = register("transform");
        WHEEL_TICK = register("wheel_tick");
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(SpiderManMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
