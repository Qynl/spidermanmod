package com.rivalrealms.world;

import com.rivalrealms.entity.SurvivorEntity;
import com.rivalrealms.sound.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Context, not dice rolls. Every spoken moment in the realm is chosen by
 * this engine from what is actually true right now: who the listener is,
 * how the faction stands, what time of day it is, whether the NPC knows
 * them, whether the settlement recently bled, and whether something just
 * moved in the dark. Lines never fire at random — they fire where they
 * belong.
 */
public final class DialogueEngine {
    private DialogueEngine() {
    }

    /** One context-chosen thing to say. */
    public record Moment(String key, float pitchMul, float volume, String chatLine) {
        static Moment of(String key) {
            return new Moment(key, 1.0f, 1.0f, null);
        }
    }

    /** Delayed second halves of NPC-to-NPC chats. */
    private record Scheduled(ServerWorld world, BlockPos pos, String key, UUID speaker,
                             float pitchMul, float volume, long at) {
    }

    private static final List<Scheduled> QUEUE = new ArrayList<>();
    /** Settlement heart -> the story of its last big event. */
    private static final Map<Long, String> LAST_MAJOR = new HashMap<>();
    private static final Map<Long, Long> LAST_MAJOR_AT = new HashMap<>();
    /** Settlement heart -> last time ambient chatter played there. */
    private static final Map<Long, Long> LAST_AMBIENT = new HashMap<>();

    // --------------------------------------------------------- event memory

    /** Settlements remember their scars; NPCs draw dialogue from this. */
    public static void noteEvent(ServerWorld world, BlockPos center, String story) {
        LAST_MAJOR.put(center.asLong(), story);
        LAST_MAJOR_AT.put(center.asLong(), world.getTime());
    }

    /** Seconds-later audio scheduling (the reply in a two-NPC chat). */
    public static void tick() {
        if (QUEUE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis() / 100L;
        Iterator<Scheduled> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            Scheduled scheduled = iterator.next();
            if (now >= scheduled.at()) {
                ModSounds.playProfiled(scheduled.world(), scheduled.pos(), scheduled.key(),
                        scheduled.speaker(), scheduled.pitchMul(), scheduled.volume());
                iterator.remove();
            }
        }
    }

    // --------------------------------------------------------- the chooser

    /**
     * Returns a context moment that overrides the ordinary greeting, or
     * {@code null} when the ordinary greeting is exactly right.
     */
    public static Moment contextMoment(SurvivorEntity npc, ServerWorld world, PlayerEntity listener) {
        // 1. The world interrupts: combat nearby cuts a sentence in half.
        if (npc.getTarget() != null && world.random.nextInt(4) == 0) {
            return new Moment("interrupt_wait", 1.15f, 1.1f,
                    npc.getName().getString() + " stops mid-thought: \"Wait... what's that?\"");
        }

        long timeOfDay = world.getTime() % 24000L;
        // 2. The hour: night roads deserve a warning, dawn a greeting.
        if (timeOfDay >= 13500L && timeOfDay <= 23000L && world.random.nextInt(3) == 0) {
            return new Moment("night_warning", 0.92f, 0.55f,
                    npc.getName().getString() + " murmurs: \"Lights out, stranger. "
                            + "These roads after dark belong to worse than wolves.\"");
        }
        if (timeOfDay <= 800L && world.random.nextInt(3) == 0) {
            return new Moment("morning_greeting", 1.05f, 1.1f,
                    npc.getName().getString() + ": \"Morning! Ovens are hot and the day's still ours.\"");
        }

        // 3. Recognition: an old face returning after days away.
        Long lastMet = npc.lastMet(listener.getUuid());
        if (lastMet != null && world.getTime() - lastMet >= 36000L && world.random.nextInt(2) == 0) {
            return Moment.of("known_return");
        }

        // 4. Fresh scars: a settlement that bled recently talks about it.
        BlockPos home = npc.guardCenter();
        if (home != null) {
            Long at = LAST_MAJOR_AT.get(home.asLong());
            String story = LAST_MAJOR.get(home.asLong());
            if (at != null && story != null && world.getTime() - at <= 72000L
                    && world.random.nextInt(2) == 0) {
                return new Moment("event_aftermath", 0.9f, 1.0f,
                        npc.getName().getString() + ": \"" + story + "\"");
            }
            // 5. Voiced chronicle: old stories, told where they happened.
            if (world.random.nextInt(20) == 0 && story != null) {
                return new Moment("story_chronicle", 0.88f, 1.3f,
                        npc.getName().getString() + " settles in to tell a story: \"" + story + "\"");
            }
        }
        // 6. A people's hello: every culture greets in its own voice.
        if (world.random.nextInt(2) == 0) {
            com.rivalrealms.entity.Archetype folk = npc.getArchetype();
            switch (folk) {
                case KNIGHT -> {
                    return new Moment("greet_knight", 0.95f, 1.1f,
                            npc.getName().getString() + " straightens: \"Well met, traveler. "
                                    + "The Order keeps these roads - walk them in peace.\"");
                }
                case PIRATE -> {
                    return new Moment("greet_pirate", 1.0f, 1.05f,
                            npc.getName().getString() + " grins: \"Hoy, stranger! "
                                    + "Coin's welcome here, and trouble ain't.\"");
                }
                case OUTLAW -> {
                    return new Moment("greet_outlaw", 0.95f, 1.0f,
                            npc.getName().getString() + " tips their hat: \"Easy, now. "
                                    + "Out here, folk mind their own business.\"");
                }
                case HEARTHFOLK -> {
                    return new Moment("greet_hearthfolk", 1.0f, 1.0f,
                            npc.getName().getString() + ": \"Welcome, traveler! "
                                    + "The kettle's on if you're wanting tea.\"");
                }
                default -> { }
            }
        }
        return null;
    }

    // --------------------------------------------------------- NPC-to-NPC

    /** Two folk who know each other actually discuss the news, out loud. */
    public static void queueChat(SurvivorEntity first, SurvivorEntity second, ServerWorld world) {
        BlockPos at = first.getBlockPos();
        ModSounds.playProfiled(world, at, "npc_chat_a", first.getUuid(), 1.0f, 1.0f);
        QUEUE.add(new Scheduled(world, second.getBlockPos(), "npc_chat_b", second.getUuid(),
                1.0f, 1.0f, System.currentTimeMillis() / 100L + 25L));
        // The overhearing text: the latest realm news, garbled the way news is.
        var entries = RealmState.get(world).chronicle();
        String news = entries.isEmpty() ? "the roads have been quiet"
                : entries.get(entries.size() - 1).text();
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(first) < 14.0 * 14.0) {
                player.sendMessage(Text.literal("<" + first.getName().getString() + "> Did you hear? "
                        + news).formatted(Formatting.GRAY), true);
                player.sendMessage(Text.literal("<" + second.getName().getString()
                        + "> I heard it different. Travelers never get the story straight.")
                        .formatted(Formatting.GRAY), true);
                break;
            }
        }
    }

    // --------------------------------------------------------- ambience

    /**
     * A settlement's audio identity: workers shouting, children playing,
     * morning calls. Runs from the slow settlement cycle, throttled hard.
     */
    public static void settlementAmbient(ServerWorld world, RealmState.BaseRecord base,
                                         List<SurvivorEntity> population) {
        long now = world.getTime();
        Long last = LAST_AMBIENT.get(base.center().asLong());
        if (last != null && now - last < 4800L) {
            return;
        }
        SurvivorEntity child = null;
        SurvivorEntity farmer = null;
        for (SurvivorEntity survivor : population) {
            if (survivor.isChild() && child == null) {
                child = survivor;
            }
            if (survivor.settlementRole() == SettlementRole.FARMER && farmer == null) {
                farmer = survivor;
            }
        }
        int roll = world.random.nextInt(6);
        if (roll == 0 && child != null) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            ModSounds.playProfiled(world, child.getBlockPos(), "child_play", child.getUuid(), 1.2f, 1.0f);
        } else if (roll == 1 && farmer != null) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            ModSounds.playProfiled(world, farmer.getBlockPos(), "work_shout", farmer.getUuid(), 1.0f, 1.8f);
        } else if (roll == 2 && population.size() >= 2) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            queueChat(population.get(0), population.get(1), world);
        }
    }
}
