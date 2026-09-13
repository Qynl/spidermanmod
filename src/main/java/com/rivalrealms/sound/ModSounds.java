package com.rivalrealms.sound;

import com.rivalrealms.RivalRealms;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * The realm's voice catalog: performed lines for the moments that matter —
 * sieges, greetings, farewells, trade, celebration, death, gossip. The ogg
 * performances ship under {@code assets/rivalrealms/sounds/voice/}; until a
 * line's file exists its key simply plays silent, so code can wire ahead of
 * assets without risk.
 */
public final class ModSounds {
    private ModSounds() {
    }

    public static final SoundEvent SIEGE_DEFENSE = register("voice.siege_defense");
    public static final SoundEvent SIEGE_VICTORY = register("voice.siege_victory");
    public static final SoundEvent CIVILIAN_FLEE = register("voice.civilian_flee");
    public static final SoundEvent GREETING_FRIENDLY = register("voice.greeting_friendly");
    public static final SoundEvent GREETING_HOSTILE = register("voice.greeting_hostile");
    public static final SoundEvent RUMOR_PLAYER = register("voice.rumor_player");
    public static final SoundEvent MERCHANT_TRADE = register("voice.merchant_trade");
    public static final SoundEvent DEATH_LAST_WORDS = register("voice.death_last_words");
    public static final SoundEvent CELEBRATION = register("voice.celebration");
    public static final SoundEvent LOST_CHILD = register("voice.lost_child");

    private static final Map<String, SoundEvent> VOICES = Map.of(
            "siege_defense", SIEGE_DEFENSE,
            "siege_victory", SIEGE_VICTORY,
            "civilian_flee", CIVILIAN_FLEE,
            "greeting_friendly", GREETING_FRIENDLY,
            "greeting_hostile", GREETING_HOSTILE,
            "rumor_player", RUMOR_PLAYER,
            "merchant_trade", MERCHANT_TRADE,
            "death_last_words", DEATH_LAST_WORDS,
            "celebration", CELEBRATION,
            "lost_child", LOST_CHILD);

    private static SoundEvent register(String name) {
        return Registry.register(Registries.SOUND_EVENT, RivalRealms.id(name), SoundEvent.of(RivalRealms.id(name)));
    }

    /** Plays a catalogued voice line at a position to everyone nearby. */
    public static void playVoice(ServerWorld world, BlockPos pos, String key) {
        SoundEvent voice = VOICES.get(key);
        if (voice != null) {
            world.playSound(null, pos, voice, SoundCategory.NEUTRAL, 1.2f, 1.0f);
        }
    }

    /** A voice line aimed at one player only (quiet, personal). */
    public static void playVoiceFor(ServerWorld world, BlockPos pos, String key,
                                    net.minecraft.server.network.ServerPlayerEntity player) {
        SoundEvent voice = VOICES.get(key);
        if (voice != null) {
            world.playSound(player, pos, voice, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        }
    }

    public static void register() {
        RivalRealms.LOGGER.info("Registered the realm voice catalog.");
    }
}
