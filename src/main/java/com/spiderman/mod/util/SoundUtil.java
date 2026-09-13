package com.spiderman.mod.util;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

/**
 * Utility to handle both SoundEvent and RegistryEntry<SoundEvent> types
 * in 1.21.1 where some SoundEvents are direct SoundEvent and some are Reference.
 */
public final class SoundUtil {
    private SoundUtil() {}

    public static SoundEvent unwrap(RegistryEntry<SoundEvent> entry) {
        return entry.value();
    }

    public static SoundEvent unwrap(SoundEvent event) {
        return event;
    }
}
