package com.rivalrealms.world;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The realm's bard: short hand-composed note-block melodies played in the
 * world at real positions — taverns, festivals, sieges, funerals and night
 * camps. Songs are sequences of (note, duration) pairs on a half-second
 * grid, advanced by the low-frequency world tick, so they cost nothing
 * when nobody is around to hear them.
 */
public final class RealmMusic {
    private RealmMusic() {
    }

    private static final List<Playing> PLAYING = new ArrayList<>();

    private record Note(int semitone, int ticks, RegistryEntry.Reference<SoundEvent> voice) {
    }

    private static final class Playing {
        final ServerWorld world;
        final BlockPos pos;
        final List<Note> notes;
        final SoundCategory category;
        int index;
        long nextAt;

        Playing(ServerWorld world, BlockPos pos, List<Note> notes, SoundCategory category) {
            this.world = world;
            this.pos = pos.toImmutable();
            this.notes = notes;
            this.category = category;
        }
    }

    // ------------------------------------------------------------ instruments

    private static final RegistryEntry.Reference<SoundEvent> HARP = SoundEvents.BLOCK_NOTE_BLOCK_HARP;
    private static final RegistryEntry.Reference<SoundEvent> BASS = SoundEvents.BLOCK_NOTE_BLOCK_BASS;
    private static final RegistryEntry.Reference<SoundEvent> FLUTE = SoundEvents.BLOCK_NOTE_BLOCK_FLUTE;
    private static final RegistryEntry.Reference<SoundEvent> BELL = SoundEvents.BLOCK_NOTE_BLOCK_BELL;
    private static final RegistryEntry.Reference<SoundEvent> DRUM = SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM;

    /** C major-ish comfortable range; 0 = low C. */
    private static Note h(int semitone, int ticks) {
        return new Note(semitone, ticks, HARP);
    }

    private static Note f(int semitone, int ticks) {
        return new Note(semitone, ticks, FLUTE);
    }

    private static Note b(int semitone, int ticks) {
        return new Note(semitone, ticks, BASS);
    }

    private static Note bell(int semitone, int ticks) {
        return new Note(semitone, ticks, BELL);
    }

    private static Note drum(int ticks) {
        return new Note(0, ticks, DRUM);
    }

    // ------------------------------------------------------------ the songs

    /** Gentle hearthside lullaby for taverns and quiet evenings. */
    public static final String HEARTHFIRE = "hearthfire";
    /** Militant drum-and-bell march when a settlement goes to war. */
    public static final String MARCH_OF_BANNERS = "march_of_banners";
    /** Skipping harvest jig in 6/8 for festivals. */
    public static final String HARVEST_REEL = "harvest_reel";
    /** Slow bell dirge for the fallen. */
    public static final String DIRGE = "dirge";
    /** Thoughtful campfire tune for wanderers. */
    public static final String WANDERERS_REST = "wanderers_rest";
    /** Triumphant fanfare after a successful defense. */
    public static final String VICTORY_FANFARE = "victory_fanfare";

    private static List<Note> song(String id) {
        // C major map: 0=C3 12=C4; each unit one semitone.
        return switch (id) {
            case HEARTHFIRE -> List.of(
                    h(16, 2), h(19, 2), h(23, 3), h(21, 1), h(19, 4),
                    h(16, 2), h(14, 2), h(16, 4),
                    h(12, 2), h(16, 2), h(19, 3), h(21, 1), h(19, 4),
                    h(23, 2), h(21, 2), h(19, 4));
            case MARCH_OF_BANNERS -> List.of(
                    drum(1), b(0, 1), drum(1), b(0, 1),
                    bell(12, 2), bell(12, 1), bell(14, 1), bell(16, 4),
                    drum(1), b(5, 1), drum(1), b(5, 1),
                    bell(16, 2), bell(14, 2), bell(12, 4),
                    drum(1), b(0, 1), drum(1), b(7, 1),
                    bell(19, 3), bell(16, 1), bell(12, 4));
            case HARVEST_REEL -> List.of(
                    f(19, 1), f(21, 1), f(23, 1), f(26, 2), f(23, 1), f(26, 2),
                    f(28, 3), f(26, 1), f(23, 4),
                    f(21, 1), f(23, 1), f(26, 1), f(28, 2), f(26, 1), f(23, 2),
                    f(21, 2), f(19, 2), f(16, 4));
            case DIRGE -> List.of(
                    bell(12, 4), bell(11, 4), bell(12, 4), bell(7, 6),
                    bell(8, 4), bell(7, 4), bell(4, 4), bell(0, 8));
            case WANDERERS_REST -> List.of(
                    h(12, 3), h(14, 1), h(16, 4),
                    h(19, 3), h(16, 1), h(14, 4),
                    h(12, 2), h(9, 2), h(12, 4),
                    b(0, 4), h(12, 4));
            case VICTORY_FANFARE -> List.of(
                    bell(12, 1), bell(12, 1), bell(12, 1), bell(16, 3),
                    bell(19, 3), bell(16, 3),
                    bell(19, 1), bell(21, 1), bell(23, 4),
                    bell(24, 6));
            default -> List.of();
        };
    }

    /** Starts a song at a position; 0.5 s per grid step. */
    public static void play(ServerWorld world, BlockPos pos, String songId) {
        List<Note> notes = song(songId);
        if (notes.isEmpty()) {
            return;
        }
        PLAYING.add(new Playing(world, pos, notes, SoundCategory.RECORDS));
    }

    /** Convenience volumes per purpose. */
    public static void playFor(ServerWorld world, BlockPos pos, String songId, SoundCategory category) {
        List<Note> notes = song(songId);
        if (notes.isEmpty()) {
            return;
        }
        PLAYING.add(new Playing(world, pos, notes, category));
    }

    /** Advances every live performance; called on the fast world tick. */
    public static void tick() {
        if (PLAYING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis() / 100L; // 0.1 s resolution clock
        Iterator<Playing> iterator = PLAYING.iterator();
        while (iterator.hasNext()) {
            Playing playing = iterator.next();
            if (!playing.world.isChunkLoaded(playing.pos.getX() >> 4, playing.pos.getZ() >> 4)) {
                iterator.remove();
                continue;
            }
            while (playing.index < playing.notes.size() && now >= playing.nextAt) {
                Note note = playing.notes.get(playing.index);
                float pitch = (float) Math.pow(2.0, (note.semitone() - 12) / 12.0);
                playing.world.playSound(null, playing.pos, note.voice().value(),
                        playing.category, 1.6f, pitch);
                playing.index++;
                playing.nextAt = now + Math.max(1, note.ticks() / 2);
            }
            if (playing.index >= playing.notes.size()) {
                iterator.remove();
            }
        }
    }
}
