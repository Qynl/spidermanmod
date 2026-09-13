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
    private static final Map<Long, String> LAST_MAJOR_TONE = new HashMap<>();
    /** Settlement heart -> last time ambient chatter played there. */
    private static final Map<Long, Long> LAST_AMBIENT = new HashMap<>();

    // --------------------------------------------------------- event memory

    /** Settlements remember their scars; NPCs draw dialogue from this. */
    /** A rivalry boils over: hard words, the second voice a beat later. */
    public static void queueQuarrel(SurvivorEntity first, SurvivorEntity second, ServerWorld world) {
        ModSounds.playProfiled(world, first.getBlockPos(), "quarrel_a", first.getUuid(), 1.1f, 1.1f);
        QUEUE.add(new Scheduled(world, second.getBlockPos(), "quarrel_b", second.getUuid(),
                1.05f, 1.1f, System.currentTimeMillis() / 100L + 25L));
    }

    public static void noteEvent(ServerWorld world, BlockPos center, String story) {
        noteEvent(world, center, story, "neutral");
    }

    public static void noteEvent(ServerWorld world, BlockPos center, String story, String tone) {
        LAST_MAJOR.put(center.asLong(), story);
        LAST_MAJOR_AT.put(center.asLong(), world.getTime());
        LAST_MAJOR_TONE.put(center.asLong(), tone);
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
        // 2. The hour: night roads deserve a warning, dawn a greeting -
        // and the watch challenges you like the watch does.
        if (timeOfDay >= 13500L && timeOfDay <= 23000L && world.random.nextInt(3) == 0) {
            com.rivalrealms.world.SettlementRole role = npc.settlementRole();
            boolean armedWatch = role == com.rivalrealms.world.SettlementRole.GUARD
                    || role == com.rivalrealms.world.SettlementRole.CAPTAIN
                    || role == com.rivalrealms.world.SettlementRole.SCOUT;
            if (armedWatch) {
                return new Moment("night_watch", 0.9f, 1.05f,
                        npc.getName().getString() + " levels their lantern: \"Who goes "
                                + "there? ...Oh. It's you. Mind the hour, friend.\"");
            }
            return new Moment("night_warning", 0.92f, 0.55f,
                    npc.getName().getString() + " murmurs: \"Lights out, stranger. "
                            + "These roads after dark belong to worse than wolves.\"");
        }
        if (timeOfDay <= 800L && world.random.nextInt(3) == 0) {
            if (npc.guardCenter() != null && world.random.nextBoolean()) {
                return new Moment("request_help", 1.05f, 1.1f,
                        npc.getName().getString() + " waves you over: \"You there! An extra "
                                + "pair of hands is worth more than coin right now. "
                                + "Care to earn your supper? See the board.\"");
            }
            return new Moment("morning_greeting", 1.05f, 1.1f,
                    npc.getName().getString() + ": \"Morning! Ovens are hot and the day's still ours.\"");
        }

        // Children who know you always want the stories retold.
        if (npc.isChild() && npc.lastMet(listener.getUuid()) != null
                && world.random.nextInt(3) == 0) {
            return new Moment("child_ask", 1.15f, 1.0f,
                    npc.getName().getString() + " tugs your sleeve: \"Tell it again! "
                            + "The one about the walls!\"");
        }

        // 3. Recognition: an old face returning after days away.
        Long lastMet = npc.lastMet(listener.getUuid());
        if (lastMet != null && world.getTime() - lastMet >= 36000L && world.random.nextInt(2) == 0) {
            return Moment.of("known_return");
        }

        // 4. Ruined places have keepers, and keepers have warnings.
        BlockPos keepersHome = npc.guardCenter();
        if (keepersHome != null && world.random.nextInt(2) == 0) {
            var base = RealmState.get(world).findByCenter(keepersHome);
            if (base != null && base.name().toLowerCase().contains("ruin")) {
                return new Moment("ruins_warning", 0.85f, 1.0f,
                        npc.getName().getString() + " bars the way: \"Don't touch the "
                                + "old stones, wanderer. The dead here don't care for visitors.\"");
            }
        }

        // 4. Fresh scars: a settlement that bled recently talks about it.
        BlockPos home = npc.guardCenter();
        if (home != null) {
            Long at = LAST_MAJOR_AT.get(home.asLong());
            String story = LAST_MAJOR.get(home.asLong());
            if (at != null && story != null && world.getTime() - at <= 72000L
                    && world.random.nextInt(2) == 0) {
                String tone = LAST_MAJOR_TONE.getOrDefault(home.asLong(), "neutral");
                if ("grief".equals(tone)) {
                    return new Moment("grief_recount", 0.88f, 1.0f,
                            npc.getName().getString() + "'s voice drops: \"" + story + "\"");
                }
                if ("triumph".equals(tone)) {
                    return new Moment("triumph_recount", 1.08f, 1.15f,
                            npc.getName().getString() + " beams: \"" + story + "\"");
                }
                return new Moment("event_aftermath", 0.9f, 1.0f,
                        npc.getName().getString() + ": \"" + story + "\"");
            }
            // 5. Voiced chronicle: old stories, told where they happened.
            if (world.random.nextInt(20) == 0 && story != null) {
                return new Moment("story_chronicle", 0.88f, 1.3f,
                        npc.getName().getString() + " settles in to tell a story: \"" + story + "\"");
            }
        }
        // Fine armor earns a craftsman's nod.
        net.minecraft.item.ItemStack worn = listener.getEquippedStack(
                net.minecraft.entity.EquipmentSlot.CHEST);
        if (!worn.isEmpty()
                && (worn.isOf(net.minecraft.item.Items.IRON_CHESTPLATE)
                        || worn.isOf(net.minecraft.item.Items.CHAINMAIL_CHESTPLATE)
                        || worn.isOf(net.minecraft.item.Items.GOLDEN_CHESTPLATE)
                        || worn.isOf(net.minecraft.item.Items.DIAMOND_CHESTPLATE)
                        || worn.isOf(net.minecraft.item.Items.NETHERITE_CHESTPLATE))
                && world.random.nextInt(3) == 0) {
            return new Moment("admire_armor", 1.02f, 1.0f,
                    npc.getName().getString() + " nods at your armor: \"Fine steel, "
                            + "friend. Not a scratch on it. Yet.\"");
        }

        // Standing changes the hello: the hated are kept at arm's length,
        // and a true friend of the town gets a hero's welcome.
        int standing = RealmState.get(world).getReputation(listener.getUuid(),
                npc.effectiveFaction());
        if (standing <= -20 && world.random.nextInt(2) == 0) {
            return new Moment("wary_greeting", 0.9f, 1.0f,
                    npc.getName().getString() + " eyes you coldly: \"Say your business, "
                            + "stranger. And be quick about it.\"");
        }
        if (standing >= 40 && world.random.nextInt(2) == 0) {
            return new Moment("warm_greeting", 1.02f, 1.1f,
                    npc.getName().getString() + " lights up: \"A friend of the town! "
                            + "Folks have been asking after you!\"");
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
                case SKY_CAPTAIN -> {
                    return new Moment("greet_sky_captain", 0.95f, 1.1f,
                            npc.getName().getString() + " squints against the wind: \"Winds fair, "
                                    + "groundling. Mind the anchor chains.\"");
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
            if (survivor.settlementRole() == SettlementRole.SCOUT && scout == null) {
                scout = survivor;
            }
        }
        SurvivorEntity trader = null;
        SurvivorEntity scout = null;
        for (SurvivorEntity survivor : population) {
            if (survivor.settlementRole() == SettlementRole.TRADER && trader == null) {
                trader = survivor;
            }
        }
        int roll = world.random.nextInt(6);
        if (roll == 0 && child != null) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            ModSounds.playProfiled(world, child.getBlockPos(), "child_play", child.getUuid(), 1.2f, 1.0f);
        } else if (roll == 1 && farmer != null) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            if (world.random.nextBoolean()) {
                ModSounds.playProfiled(world, farmer.getBlockPos(), "work_song",
                        farmer.getUuid(), 0.95f, 1.1f);
            } else {
                ModSounds.playProfiled(world, farmer.getBlockPos(), "work_shout", farmer.getUuid(), 1.0f, 1.8f);
            }
        } else if (roll == 2 && population.size() >= 2) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            queueChat(population.get(0), population.get(1), world);
        } else if (roll == 3 && trader != null) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            ModSounds.playProfiled(world, trader.getBlockPos(), "trade_patter", trader.getUuid(), 1.0f, 1.15f);
        } else if (roll == 5 && world.getTime() % 24000L >= 12542L && world.getTime() % 24000L <= 23459L) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            if (child != null) {
                ModSounds.playProfiled(world, child.getBlockPos(), "lullaby", child.getUuid(), 1.05f, 0.9f);
            } else if (!population.isEmpty()) {
                ModSounds.playProfiled(world, population.get(0).getBlockPos(), "drunk_tavern",
                        population.get(0).getUuid(), 0.92f, 1.2f);
            }
        } else if (roll == 3 && trader == null && scout != null) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            ModSounds.playProfiled(world, scout.getBlockPos(), "hunt_tale", scout.getUuid(), 1.0f, 1.1f);
        } else if (roll == 4 && base.level() >= 4) {
            LAST_AMBIENT.put(base.center().asLong(), now);
            ModSounds.playVoice(world, base.center(), "town_pride");
        }
    }
}
