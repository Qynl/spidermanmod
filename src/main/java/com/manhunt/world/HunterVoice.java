package com.manhunt.world;

import com.manhunt.ManhuntSounds;
import com.manhunt.entity.HunterEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

/**
 * The dread budget. Every line - spoken, ghosted or whispered into chat -
 * passes through here, and nothing is allowed to repeat within ninety seconds
 * or more than twice a night, so a line only ever lands when the moment
 * deserves it.
 */
public final class HunterVoice {

    private static final Map<String, Long> LAST_SPOKEN = new HashMap<>();
    private static final Map<String, Integer> NIGHT_COUNT = new HashMap<>();
    private static final Map<String, Long> LAST_GHOST = new HashMap<>();
    private static final Map<String, Long> LAST_WHISPER = new HashMap<>();
    private static int budgetNight = -1;

    private HunterVoice() {
    }

    private static int nightOf(ServerWorld world) {
        return (int) (world.getTimeOfDay() / 24000L);
    }

    private static void rollNight(ServerWorld world) {
        int night = nightOf(world);
        if (night != budgetNight) {
            budgetNight = night;
            NIGHT_COUNT.clear();
        }
    }

    /** A spoken line, from his throat, at his position. */
    public static void speak(ServerWorld world, HunterEntity hunter, String key) {
        rollNight(world);
        long time = world.getTime();
        if (time - LAST_SPOKEN.getOrDefault(key, Long.MIN_VALUE / 2) < 1800L) {
            return;
        }
        if (NIGHT_COUNT.merge(key, 1, Integer::sum) > 2) {
            return;
        }
        LAST_SPOKEN.put(key, time);
        SoundEvent event = ManhuntSounds.get(key);
        if (event == null) {
            return;
        }
        world.playSound(null, hunter.getBlockPos(), event, SoundCategory.HOSTILE,
                1.0f, 0.92f + world.random.nextFloat() * 0.16f);
    }

    /** A subtitle ghost: played at volume zero, read but never heard. */
    public static void ghost(ServerWorld world, HunterEntity hunter, SoundEvent event) {
        long time = world.getTime();
        if (time - LAST_GHOST.getOrDefault(event.toString(), Long.MIN_VALUE / 2) < 2400L) {
            return;
        }
        LAST_GHOST.put(event.toString(), time);
        world.playSound(null, hunter.getBlockPos(), event, SoundCategory.HOSTILE, 0.0f, 1.0f);
    }

    /** Italic gray narration in the player's chat - the whisper channel. */
    public static void whisper(ServerWorld world, ServerPlayerEntity player, String text) {
        rollNight(world);
        long time = world.getTime();
        if (time - LAST_WHISPER.getOrDefault(text, Long.MIN_VALUE / 2) < 1800L) {
            return;
        }
        LAST_WHISPER.put(text, time);
        player.sendMessage(Text.literal(text).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    /** The Count: distance as descending numbers, whispered, once per step. */
    public static void count(ServerWorld world, ServerPlayerEntity player, int value) {
        whisper(world, player, "\u2026" + word(value) + "\u2026");
    }

    private static String word(int value) {
        String[] words = {"zero", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
                "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
        return words[Math.clamp(value, 0, 20)];
    }
}
