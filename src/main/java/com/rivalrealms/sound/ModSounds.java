package com.rivalrealms.sound;

import com.rivalrealms.RivalRealms;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;

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
    public static final SoundEvent EVENT_AFTERMATH = register("voice.event_aftermath");
    public static final SoundEvent STORY_CHRONICLE = register("voice.story_chronicle");
    public static final SoundEvent NPC_CHAT_A = register("voice.npc_chat_a");
    public static final SoundEvent NPC_CHAT_B = register("voice.npc_chat_b");
    public static final SoundEvent NIGHT_WARNING = register("voice.night_warning");
    public static final SoundEvent MORNING_GREETING = register("voice.morning_greeting");
    public static final SoundEvent WORK_SHOUT = register("voice.work_shout");
    public static final SoundEvent CHILD_PLAY = register("voice.child_play");
    public static final SoundEvent INTERRUPT_WAIT = register("voice.interrupt_wait");
    public static final SoundEvent KNOWN_RETURN = register("voice.known_return");
    public static final SoundEvent CONTRACT_OFFER = register("voice.contract_offer");
    public static final SoundEvent CONTRACT_DONE = register("voice.contract_done");
    public static final SoundEvent CONTRACT_EXPIRED = register("voice.contract_expired");
    public static final SoundEvent HUNT_PROGRESS = register("voice.hunt_progress");
    public static final SoundEvent WANTED_YOU = register("voice.wanted_you");
    public static final SoundEvent FINE_PAID = register("voice.fine_paid");
    public static final SoundEvent GATHER_REQUEST = register("voice.gather_request");
    public static final SoundEvent EXPLORE_HINT = register("voice.explore_hint");
    public static final SoundEvent DELIVER_GIVEN = register("voice.deliver_given");
    public static final SoundEvent BOARD_PITCH = register("voice.board_pitch");

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
            Map.entry("herald_news", HERALD_NEWS),
            Map.entry("event_aftermath", EVENT_AFTERMATH),
            Map.entry("story_chronicle", STORY_CHRONICLE),
            Map.entry("npc_chat_a", NPC_CHAT_A),
            Map.entry("npc_chat_b", NPC_CHAT_B),
            Map.entry("night_warning", NIGHT_WARNING),
            Map.entry("morning_greeting", MORNING_GREETING),
            Map.entry("work_shout", WORK_SHOUT),
            Map.entry("child_play", CHILD_PLAY),
            Map.entry("interrupt_wait", INTERRUPT_WAIT),
            Map.entry("known_return", KNOWN_RETURN),
            Map.entry("contract_offer", CONTRACT_OFFER),
            Map.entry("contract_done", CONTRACT_DONE),
            Map.entry("contract_expired", CONTRACT_EXPIRED),
            Map.entry("hunt_progress", HUNT_PROGRESS),
            Map.entry("wanted_you", WANTED_YOU),
            Map.entry("fine_paid", FINE_PAID),
            Map.entry("gather_request", GATHER_REQUEST),
            Map.entry("explore_hint", EXPLORE_HINT),
            Map.entry("deliver_given", DELIVER_GIVEN),
            Map.entry("board_pitch", BOARD_PITCH));

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

    /**
     * A line spoken with a character's own voice profile: every NPC derives
     * a stable pitch from their identity, so no two farmers sound alike -
     * two actors times five pitches, plus per-moment emotion multipliers.
     */
    public static void playProfiled(ServerWorld world, BlockPos pos, String key, UUID who,
                                    float pitchMul, float volume) {
        SoundEvent voice = VOICES.get(key);
        if (voice == null) {
            return;
        }
        float profile = 0.85f + Math.floorMod(who.getLeastSignificantBits(), 5L) * 0.1f;
        world.playSound(null, pos, voice, SoundCategory.NEUTRAL, volume, pitchMul * profile);
    }

    public static void register() {
        RivalRealms.LOGGER.info("Registered the realm voice catalog.");
    }
}
