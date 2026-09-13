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
    public static final SoundEvent GREETING_NEUTRAL = register("voice.greeting_neutral");
    public static final SoundEvent GREETING_FOLKSY = register("voice.greeting_folksy");
    public static final SoundEvent QUIP_IDLE = register("voice.quip_idle");
    public static final SoundEvent QUIP_IDLE2 = register("voice.quip_idle2");
    public static final SoundEvent TRUST_UP = register("voice.trust_up");
    public static final SoundEvent RECRUIT_JOIN = register("voice.recruit_join");
    public static final SoundEvent BETRAY = register("voice.betray");
    public static final SoundEvent THEFT_CAUGHT = register("voice.theft_caught");
    public static final SoundEvent GUARD_WARNING = register("voice.guard_warning");
    public static final SoundEvent HERALD_NEWS = register("voice.herald_news");

    private static final Map<String, SoundEvent> VOICES = Map.ofEntries(
            Map.entry("siege_defense", SIEGE_DEFENSE),
            Map.entry("siege_victory", SIEGE_VICTORY),
            Map.entry("civilian_flee", CIVILIAN_FLEE),
            Map.entry("greeting_friendly", GREETING_FRIENDLY),
            Map.entry("greeting_hostile", GREETING_HOSTILE),
            Map.entry("rumor_player", RUMOR_PLAYER),
            Map.entry("merchant_trade", MERCHANT_TRADE),
            Map.entry("death_last_words", DEATH_LAST_WORDS),
            Map.entry("celebration", CELEBRATION),
            Map.entry("lost_child", LOST_CHILD),
            Map.entry("greeting_neutral", GREETING_NEUTRAL),
            Map.entry("greeting_folksy", GREETING_FOLKSY),
            Map.entry("quip_idle", QUIP_IDLE),
            Map.entry("quip_idle2", QUIP_IDLE2),
            Map.entry("trust_up", TRUST_UP),
            Map.entry("recruit_join", RECRUIT_JOIN),
            Map.entry("betray", BETRAY),
            Map.entry("theft_caught", THEFT_CAUGHT),
            Map.entry("guard_warning", GUARD_WARNING),
            Map.entry("herald_news", HERALD_NEWS));

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
