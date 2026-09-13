package com.rivalrealms.world;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The living-realm simulation: one slow heartbeat that turns NPC actions
 * into faction state, faction state into world change, and world change
 * back into NPC behaviour.
 *
 * <ul>
 *   <li><b>Wealth</b> — every faction runs a 0–100 treasury. Surplus food and
 *       caravans enrich it; raids, famine and lost settlements drain it.
 *       Wealth buys sharper steel, enchanted veterans and one extra patrol.</li>
 *   <li><b>Life cycle</b> — fed settlements expand, starved ones shrink and
 *       empty ones are abandoned. Children are born, grow up and take their
 *       parents' trade; a settlement that loses every defender in a war is
 *       captured and serves a new banner.</li>
 *   <li><b>Chronicle</b> — every capture, famine, plague, birth of note and
 *       raid is written into the persistent world history players can read
 *       with {@code /rivalrealms chronicle}.</li>
 *   <li><b>Events</b> — famine, plague, merchant boom, bandit uprising,
 *       refugee migration and border skirmishes roll on a slow dice.</li>
 *   <li><b>Roads</b> — caravans travel between known settlements; a hostile
 *       camp astride the road cuts it, and the chronicle records who dared
 *       travel anyway.</li>
 * </ul>
 */
public final class LivingRealm {
    private LivingRealm() {
    }

    private static final Map<String, String[]> SURNAMES = new HashMap<>();

    static {
        SURNAMES.put("crownlands", new String[]{"Ashford", "Greywarden", "Winterbourne", "Harrowgate",
                "Vale", "Castellan", "Highmoor", "Stannis"});
        SURNAMES.put("freebooters", new String[]{"Blackreef", "Saltmarsh", "Goreweather", "Tidewrack",
                "Blackfin", "Deadsail", "Cutlass", "Wraith"});
        SURNAMES.put("dustwalkers", new String[]{"Drycreek", "Sixshot", "Dustveil", "Sundown",
                "Coyote", "Mesa", "Rattler", "Scar"});
        SURNAMES.put("skybound", new String[]{"Cloudwright", "Stormrider", "Highgale", "Zephyrhold",
                "Nimbus", "Aerthane", "Windward", "Skyspire"});
        SURNAMES.put("marauders", new String[]{"Bonechewer", "Skulltak", "Bloodmaw", "Rotfang",
                "Grimhoof", "Ashpile", "Warhound", "Manreaver"});
        SURNAMES.put("hearthfolk", new String[]{"Honeywine", "Fivefields", "Barleydown", "Goodbarrel",
                "Meadowbrook", "Applewhite", "Milllane", "Hearthwood"});
        SURNAMES.put("independent", new String[]{"Wanderer", "Drifter", "Wayfarer"});
    }

    // ------------------------------------------------------------- seasons

    public static final int SPRING = 0;
    public static final int SUMMER = 1;
    public static final int AUTUMN = 2;
    public static final int WINTER = 3;

    /** Eight-day seasons; a year lasts a month of days. */
    public static int season(ServerWorld world) {
        long day = world.getTime() / 24000L + 1L;
        return (int) ((day / 8L) % 4L);
    }

    public static String seasonName(ServerWorld world) {
        return switch (season(world)) {
            case SPRING -> "Spring";
            case SUMMER -> "Summer";
            case AUTUMN -> "Autumn";
            default -> "Winter";
        };
    }

    /** Reputation as words, the way NPCs say it. */
    public static String titleFor(int reputation) {
        if (reputation >= 80) return "Legend";
        if (reputation >= 60) return "Local Hero";
        if (reputation >= 30) return "Trusted";
        if (reputation >= 15) return "Known";
        if (reputation > -15) return "Traveler";
        if (reputation > -40) return "Outlaw";
        return "Enemy";
    }

    /**
     * A good deed by the player, seen by someone: the rumor mill carries
     * praise as readily as blame, and this is how "Local Hero" happens.
     */
    public static void seedPlayerDeed(ServerWorld world, net.minecraft.entity.player.PlayerEntity hero, String deed) {
        for (Entity witness : world.getOtherEntities(hero,
                hero.getBoundingBox().expand(20.0), e -> e instanceof SurvivorEntity s && s.isAlive())) {
            ((SurvivorEntity) witness).seedRumor(hero.getUuid(), 4, 3);
            break;
        }
        world.getServer().getPlayerManager().broadcast(
                Text.literal("Word spreads of a traveler who " + deed + ".").formatted(Formatting.GRAY), false);
    }

    /** A family surname that matches the culture, so generations read real. */
    public static String surname(String faction, net.minecraft.util.math.random.Random random) {
        String[] pool = SURNAMES.getOrDefault(faction == null ? "independent" : faction.toLowerCase(java.util.Locale.ROOT),
                SURNAMES.get("independent"));
        return pool[random.nextInt(pool.length)];
    }

    // ------------------------------------------------------------- heartbeat

    /** Runs every 600 ticks from the server tick hook. One cycle = 30 s. */
    public static void tick(ServerWorld world) {
        RealmState state = RealmState.get(world);
        List<RealmState.BaseRecord> bases = new ArrayList<>(state.bases());
        if (bases.isEmpty()) {
            return;
        }

        for (RealmState.BaseRecord base : bases) {
            if (!areaLoaded(world, base.center(), base.radius() + 24)) {
                continue;
            }
            try {
                settleCycle(world, state, base, bases);
            } catch (RuntimeException exception) {
                RivalRealms.LOGGER.error("LivingRealm skipped settlement {}", base.name(), exception);
            }
        }

        // World events: rare, never stacked on the same cycle.
        if (world.getTime() % 3600L == 0L && world.random.nextInt(3) == 0) {
            rollWorldEvent(world, state);
        }

        tickSieges(world, state);
        tickSeason(world, state);
        tickExpeditions(world);
        tickTreasureHints(world);
        if (world.getTime() % 1200L == 0L) {
            tickCelebrations(world, state);
            tickResettlement(world, state);
            tickCampGrowth(world, state);
        }
    }

    private static void settleCycle(ServerWorld world, RealmState state, RealmState.BaseRecord base,
                                    List<RealmState.BaseRecord> all) {
        if (base.abandoned()) {
            return;
        }
        List<SurvivorEntity> pop = population(world, base);
        int guards = 0;
        List<SurvivorEntity> hostiles = new ArrayList<>();
        for (SurvivorEntity survivor : pop) {
            if (survivor.isBaseGuard()) {
                guards++;
            }
        }
        for (Entity entity : world.getOtherEntities(null, box(base),
                candidate -> candidate instanceof SurvivorEntity stranger
                        && stranger.isAlive() && !stranger.isRecruited()
                        && state.isHostile(base.faction(), stranger.effectiveFaction()))) {
            hostiles.add((SurvivorEntity) entity);
        }

        // --- the town eats ------------------------------------------------
        int mouths = 1 + pop.size() / 3;
        state.consumeFood(base, mouths);

        // --- wealth follows the pantry ------------------------------------
        if (base.food() >= 40 && base.materials() >= 30 && world.random.nextInt(3) == 0) {
            state.adjustWealth(base.faction(), 1);
        } else if (base.food() == 0) {
            state.adjustWealth(base.faction(), -1);
        }

        // --- famine has consequences --------------------------------------
        if (base.food() == 0 && !pop.isEmpty()) {
            int famine = base.famineCycles() + 1;
            base.setFamineCycles(famine);
            if (famine >= 3 && world.random.nextFloat() < 0.5f) {
                SurvivorEntity leaver = pop.get(world.random.nextInt(pop.size()));
                state.chronicle(world.getTime(), leaver.getName().getString()
                        + " abandoned " + base.name() + ", starving.", false);
                leaver.discard();
                base.setFamineCycles(1);
            } else if (famine >= 3 && base.level() > 1 && world.random.nextFloat() < 0.3f) {
                base.setFamineCycles(1);
                shrinkBase(world, state, base, base.name() + " shrank as hunger drove its people away.");
            }
        } else if (base.food() > 0) {
            base.setFamineCycles(0);
        }

        // --- births: fed towns raise the next generation -------------------
        if (base.food() >= 24 && pop.size() >= 2 && pop.size() < 11 && world.random.nextFloat() < 0.10f) {
            spawnChild(world, state, base, pop);
        }

        // --- capture: no defenders, armed hostiles inside the walls --------
        if (guards == 0 && hostiles.size() >= 3 && world.random.nextFloat() < 0.4f) {
            captureSettlement(world, state, base, hostiles);
            return;
        }

        // --- abandonment: nobody left to call it home ----------------------
        if (pop.isEmpty() && world.random.nextFloat() < 0.35f) {
            base.setAbandoned(true);
            chronicleBroadcast(world, state, base.name() + " stands empty. Its banners are gone.", true);
        }

        // --- roads: caravans run between known towns -----------------------
        if (world.random.nextFloat() < 0.12f) {
            runCaravan(world, state, base, all);
        }
    }

    // ------------------------------------------------------------- capture

    private static void captureSettlement(ServerWorld world, RealmState state,
                                          RealmState.BaseRecord base, List<SurvivorEntity> hostiles) {
        // The most common hostile banner inside the walls takes the town.
        Map<String, Integer> tally = new HashMap<>();
        for (SurvivorEntity hostile : hostiles) {
            tally.merge(hostile.effectiveFaction().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
        }
        String winner = null;
        int best = 0;
        for (Map.Entry<String, Integer> entry : tally.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                winner = entry.getKey();
            }
        }
        if (winner == null) {
            return;
        }
        String loser = base.faction();
        UUID newOwner = UUID.nameUUIDFromBytes(("rivalrealms:faction:" + winner)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BuildStyle style = Archetype.byFaction(winner) == Archetype.PIRATE ? BuildStyle.PIRATE
                : Archetype.byFaction(winner) == Archetype.MARAUDER ? BuildStyle.MARAUDER
                : Archetype.byFaction(winner) == Archetype.SKY_CAPTAIN ? BuildStyle.SKY
                : Archetype.byFaction(winner) == Archetype.OUTLAW ? BuildStyle.WESTERN
                : BuildStyle.KNIGHT;
        base.surrenderTo(newOwner, winner, style.id());
        state.adjustWealth(winner, 6);
        state.adjustWealth(loser, -6);
        state.chronicle(world.getTime(), base.name() + " has fallen! The " + winner + " seized it from the "
                + loser + ".", true);
        chronicleBroadcast(world, state, base.name() + " has fallen to the " + winner + "!", true);

        // Some of the victors stay as the new garrison.
        int garrison = 0;
        for (SurvivorEntity hostile : hostiles) {
            if (garrison >= 2) {
                break;
            }
            hostile.assignGuard(base.center(), newOwner, winner);
            garrison++;
        }
        // The new banner flies: plant one at the heart.
        BlockPos heart = base.center();
        world.setBlockState(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, heart),
                com.rivalrealms.block.ModBlocks.REALM_BANNER.getDefaultState());
        world.playSound(null, heart.getX(), heart.getY(), heart.getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.7f);
    }

    private static void shrinkBase(ServerWorld world, RealmState state,
                                   RealmState.BaseRecord base, String story) {
        // Level is clamped >= 1 by the record itself; reflect the loss.
        state.chronicle(world.getTime(), story, false);
        chronicleBroadcast(world, state, story, false);
    }

    // ------------------------------------------------------------- children

    private static void spawnChild(ServerWorld world, RealmState state,
                                   RealmState.BaseRecord base, List<SurvivorEntity> pop) {
        SurvivorEntity parent = pop.get(world.random.nextInt(pop.size()));
        if (parent.isChild() || parent.isRecruited() || parent.guardCenter() == null) {
            return;
        }
        SurvivorEntity baby = ModEntities.SURVIVOR.create(world);
        if (baby == null) {
            return;
        }
        BlockPos spawn = surface(world, base.center().add(world.random.nextInt(11) - 5, 0, world.random.nextInt(11) - 5));
        baby.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                world.random.nextFloat() * 360.0f, 0.0f);
        baby.setArchetype(parent.getArchetype());
        baby.assignWorker(parent.guardCenter(), parent.guardOwnerUuid(), parent.effectiveFaction(),
                SettlementRole.NONE);
        baby.setChild(true);
        baby.setFamily(parent.familyName().isEmpty()
                ? surname(parent.effectiveFaction(), world.random) : parent.familyName(), parent);
        baby.setCustomName(Text.literal("Young " + baby.firstName(world.random))
                .formatted(Formatting.GREEN));
        world.spawnEntity(baby);
        state.chronicle(world.getTime(), "A child of family " + baby.familyName()
                + " was born in " + base.name() + ".", false);
    }

    // ------------------------------------------------------------- events

    private static void rollWorldEvent(ServerWorld world, RealmState state) {
        List<RealmState.BaseRecord> loaded = new ArrayList<>();
        for (RealmState.BaseRecord base : state.bases()) {
            if (!base.abandoned() && areaLoaded(world, base.center(), base.radius() + 16)) {
                loaded.add(base);
            }
        }
        if (loaded.isEmpty()) {
            return;
        }
        RealmState.BaseRecord base = loaded.get(world.random.nextInt(loaded.size()));
        int roll = world.random.nextInt(8);
        switch (roll) {
            case 0 -> famine(world, state, base);
            case 1 -> plague(world, state, base);
            case 2 -> merchantBoom(world, state, base);
            case 3 -> uprising(world, state, base);
            case 4 -> refugees(world, state, base);
            case 5 -> storm(world, state, base);
            case 6 -> earthquake(world, state, base);
            default -> skirmish(world, state, base, loaded);
        }
    }

    /** A summer storm walks the fields: lightning, panic, one hard night. */
    private static void storm(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        for (int i = 0; i < 3; i++) {
            BlockPos strike = surface(world, base.center().add(
                    world.random.nextInt(31) - 15, 0, world.random.nextInt(31) - 15));
            LightningEntity bolt = new LightningEntity(net.minecraft.entity.EntityType.LIGHTNING_BOLT, world);
            bolt.refreshPositionAfterTeleport(strike.getX() + 0.5, strike.getY(), strike.getZ() + 0.5);
            world.spawnEntity(bolt);
        }
        state.drain(base, 2, 2);
        state.chronicle(world.getTime(), "A furious storm broke over " + base.name() + "; lightning walked the fields.", true);
        chronicleBroadcast(world, state, "Storm breaks over " + base.name() + "!", true);
    }

    /** The ground remembers an old wound: tremors, falling gravel, shaken folk. */
    private static void earthquake(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        BlockPos at = surface(world, base.center().add(world.random.nextInt(21) - 10, 0, world.random.nextInt(21) - 10));
        for (int i = 0; i < 4; i++) {
            BlockPos spill = surface(world, at.add(world.random.nextInt(9) - 4, 0, world.random.nextInt(9) - 4));
            world.setBlockState(spill, net.minecraft.block.Blocks.GRAVEL.getDefaultState());
            if (world.random.nextBoolean()) {
                world.setBlockState(spill.add(world.random.nextInt(3) - 1, 0, world.random.nextInt(3) - 1),
                        net.minecraft.block.Blocks.COBBLESTONE.getDefaultState());
            }
        }
        for (SurvivorEntity survivor : population(world, base)) {
            survivor.takeKnockback(0.8, world.random.nextDouble() - 0.5, world.random.nextDouble() - 0.5);
        }
        state.drain(base, 0, 4);
        state.chronicle(world.getTime(), "The earth shuddered near " + base.name() + "; stones fell from the hills.", true);
        chronicleBroadcast(world, state, "Earth tremors near " + base.name() + "!", false);
    }

    private static void famine(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        state.drain(base, Math.max(6, base.food()), 0);
        state.chronicle(world.getTime(), "Famine grips " + base.name() + ". The granaries are bare.", true);
        chronicleBroadcast(world, state, "Famine grips " + base.name() + ".", true);
    }

    private static void plague(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        int herbalists = 0;
        List<SurvivorEntity> pop = population(world, base);
        for (SurvivorEntity survivor : pop) {
            if (survivor.settlementRole() == SettlementRole.HERBALIST) {
                herbalists++;
            }
        }
        if (herbalists > 0) {
            state.chronicle(world.getTime(), "Herbalists of " + base.name()
                    + " contained a plague before it spread.", false);
            return;
        }
        for (SurvivorEntity survivor : pop) {
            survivor.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 0));
            survivor.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 600, 0));
        }
        state.drain(base, 6, 0);
        state.chronicle(world.getTime(), "Plague sweeps " + base.name() + ". No herbalist remains to cure it.", true);
        chronicleBroadcast(world, state, "Plague sweeps " + base.name() + ".", true);
    }

    private static void merchantBoom(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        state.adjustWealth(base.faction(), 8);
        state.recordSettlementWork(base, 0, 10, 0);
        state.chronicle(world.getTime(), "A merchant boom enriches the " + base.faction()
                + "; caravans crowd the roads to " + base.name() + ".", false);
    }

    private static void uprising(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        boolean marauderCamp = false;
        for (RealmState.BaseRecord other : state.bases()) {
            if (!other.abandoned() && "Marauders".equalsIgnoreCase(other.faction())) {
                marauderCamp = true;
                break;
            }
        }
        if (!marauderCamp || population(world, base).isEmpty()) {
            return;
        }
        spawnWarband(world, state, base, 3, "A vengeful warband marches on " + base.name() + "!");
    }

    private static void refugees(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        BlockPos edge = surface(world, base.center().offset(Direction.Type.HORIZONTAL.random(world.random),
                base.radius() + 6));
        for (int i = 0; i < 3; i++) {
            SurvivorEntity refugee = ModEntities.SURVIVOR.create(world);
            if (refugee == null) {
                continue;
            }
            BlockPos at = surface(world, edge.add(i * 2 - 2, 0, world.random.nextInt(3) - 1));
            refugee.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            refugee.setArchetype(Archetype.HEARTHFOLK);
            refugee.assignWorker(base.center(), base.owner(), base.faction(),
                    world.random.nextBoolean() ? SettlementRole.FARMER : SettlementRole.BUILDER);
            refugee.setFamily(surname("Hearthfolk", world.random), null);
            world.spawnEntity(refugee);
        }
        state.drain(base, 3, 0);
        state.chronicle(world.getTime(), "Refugees reach " + base.name() + " seeking work and bread.", false);
    }

    private static void skirmish(ServerWorld world, RealmState state, RealmState.BaseRecord base,
                                 List<RealmState.BaseRecord> loaded) {
        RealmState.BaseRecord foe = null;
        for (RealmState.BaseRecord other : loaded) {
            if (other != base && state.isHostile(base.faction(), other.faction())
                    && other.center().getSquaredDistance(base.center()) <= 160.0 * 160.0) {
                foe = other;
                break;
            }
        }
        if (foe == null) {
            return;
        }
        // Two patrols meet on the road and settle it the old way.
        BlockPos mid = new BlockPos((base.center().getX() + foe.center().getX()) / 2,
                base.center().getY(), (base.center().getZ() + foe.center().getZ()) / 2);
        for (String faction : new String[]{base.faction(), foe.faction()}) {
            for (int i = 0; i < 2; i++) {
                SurvivorEntity fighter = ModEntities.SURVIVOR.create(world);
                if (fighter == null) {
                    continue;
                }
                BlockPos at = surface(world, mid.add(world.random.nextInt(9) - 4, 0, world.random.nextInt(9) - 4));
                fighter.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                        world.random.nextFloat() * 360.0f, 0.0f);
                fighter.setArchetype(Archetype.byFaction(faction));
                fighter.setFamily(surname(faction, world.random), null);
                world.spawnEntity(fighter);
            }
        }
        state.chronicle(world.getTime(), "Border skirmish between the patrols of " + base.name()
                + " and " + foe.name() + ".", false);
    }

    /** Uprisings and raids share one entry point so the chronicle stays honest. */
    public static void spawnWarband(ServerWorld world, RealmState state,
                                    RealmState.BaseRecord base, int count, String story) {
        BlockPos edge = surface(world, base.center().offset(Direction.Type.HORIZONTAL.random(world.random),
                base.radius() + 10));
        for (int i = 0; i < count; i++) {
            SurvivorEntity raider = ModEntities.SURVIVOR.create(world);
            if (raider == null) {
                continue;
            }
            BlockPos at = surface(world, edge.add(world.random.nextInt(7) - 3, 0, world.random.nextInt(7) - 3));
            raider.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            raider.setArchetype(Archetype.MARAUDER);
            raider.setCustomName(Text.literal("Warband · Marauder"));
            world.spawnEntity(raider);
        }
        state.chronicle(world.getTime(), story, true);
        chronicleBroadcast(world, state, story, true);
    }

    // ------------------------------------------------------------- roads

    private static void runCaravan(ServerWorld world, RealmState state,
                                   RealmState.BaseRecord from, List<RealmState.BaseRecord> all) {
        if (from.food() < 20 || from.abandoned()) {
            return;
        }
        RealmState.BaseRecord to = null;
        for (RealmState.BaseRecord other : all) {
            if (other != from && !other.abandoned()
                    && other.center().getSquaredDistance(from.center()) <= 200.0 * 200.0
                    && areaLoaded(world, other.center(), other.radius() + 8)) {
                to = other;
                break;
            }
        }
        if (to == null) {
            return;
        }
        // A hostile camp astride the road throttles trade.
        for (RealmState.BaseRecord camp : state.bases()) {
            if (camp.abandoned() || !state.isHostile(from.faction(), camp.faction())) {
                continue;
            }
            BlockPos midRoad = new BlockPos((from.center().getX() + to.center().getX()) / 2,
                    from.center().getY(), (from.center().getZ() + to.center().getZ()) / 2);
            if (camp.center().getSquaredDistance(midRoad) <= 70.0 * 70.0) {
                if (world.random.nextFloat() < 0.25f) {
                    state.chronicle(world.getTime(), "The road between " + from.name() + " and "
                            + to.name() + " is cut by the " + camp.faction() + ". Caravans stay home.", false);
                }
                return;
            }
        }
        // The caravan walks the road: two traders and a guard, headed for `to`.
        int roadX = to.center().getX() - from.center().getX();
        int roadZ = to.center().getZ() - from.center().getZ();
        Direction road = Math.abs(roadX) >= Math.abs(roadZ)
                ? (roadX >= 0 ? Direction.EAST : Direction.WEST)
                : (roadZ >= 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos start = surface(world, from.center().offset(road, from.radius() + 4));
        for (int i = 0; i < 3; i++) {
            SurvivorEntity trader = ModEntities.SURVIVOR.create(world);
            if (trader == null) {
                continue;
            }
            BlockPos at = surface(world, start.add(i * 2 - 2, 0, world.random.nextInt(3) - 1));
            trader.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            trader.setArchetype(Archetype.byFaction(from.faction()));
            trader.assignWorker(to.center(), to.owner(), to.faction(),
                    i == 2 ? SettlementRole.GUARD : SettlementRole.TRADER);
            trader.setFamily(surname(from.faction(), world.random), null);
            world.spawnEntity(trader);
        }
        state.drain(from, 4, 0);
        state.recordSettlementWork(to, 6, 4, 0);
        if (world.random.nextFloat() < 0.35f) {
            state.chronicle(world.getTime(), "A caravan made the road run from " + from.name()
                    + " to " + to.name() + ".", false);
        }
    }

    // ------------------------------------------------------------- wealth gear

    /** Faction poverty and wealth show on the body: sharper steel or ragged leather. */
    public static void applyWealthGear(ServerWorld world, SurvivorEntity survivor, String faction) {
        RealmState state = RealmState.get(world);
        int wealth = state.wealth(faction);
        var enchantments = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        if (wealth >= 75 && world.random.nextFloat() < 0.35f) {
            survivor.getMainHandStack().addEnchantment(enchantments.entryOf(net.minecraft.enchantment.Enchantments.SHARPNESS), 1);
        } else if (wealth <= 18 && world.random.nextFloat() < 0.5f) {
            // A bankrupt faction cannot arm its people.
            survivor.equipStack(net.minecraft.entity.EquipmentSlot.CHEST,
                    new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEATHER_CHESTPLATE));
        }
    }

    // ------------------------------------------------------------- sieges

    private static final class Siege {
        final String attackerFaction;
        int phase;
        long phaseUntil;
        long messengerSent;
        boolean reliefArrived;

        Siege(String attackerFaction, long now) {
            this.attackerFaction = attackerFaction;
            this.phase = 0;
            this.phaseUntil = now + 1200L;
        }
    }

    private static final Map<Long, Siege> SIEGES = new HashMap<>();

    /** Opens a siege around a settlement under raider attack. */
    public static void beginSiege(ServerWorld world, RealmState.BaseRecord base, String attackerFaction) {
        if (SIEGES.containsKey(base.center().asLong())) {
            return;
        }
        SIEGES.put(base.center().asLong(), new Siege(attackerFaction, world.getTime()));
        RealmState state = RealmState.get(world);
        state.chronicle(world.getTime(), "SIEGE: the " + attackerFaction + " close around " + base.name() + ".", true);
        chronicleBroadcast(world, state, "SIEGE at " + base.name() + "! The " + attackerFaction
                + " are at the walls!", true);
        // Defenders prepare: the bell rings, the watch stiffens.
        world.playSound(null, base.center().getX(), base.center().getY(), base.center().getZ(),
                SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 2.0f, 0.8f);
        for (SurvivorEntity defender : population(world, base)) {
            if (defender.isBaseGuard()) {
                defender.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 2400, 0));
                defender.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 2400, 0));
            }
        }
        com.rivalrealms.sound.ModSounds.playVoice(world, base.center(), "siege_defense");
    }

    /** Advances every live siege: preparation, evacuation, relief, aftermath. */
    private static void tickSieges(ServerWorld world, RealmState state) {
        if (SIEGES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Long, Siege>> iterator = SIEGES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Siege> entry = iterator.next();
            RealmState.BaseRecord base = state.findByCenter(entry.getKey());
            Siege siege = entry.getValue();
            if (base == null || base.abandoned() || !areaLoaded(world, base.center(), base.radius())) {
                continue;
            }
            long now = world.getTime();
            List<SurvivorEntity> defenders = new ArrayList<>();
            List<SurvivorEntity> attackers = new ArrayList<>();
            for (Entity entity : world.getOtherEntities(null, box(base),
                    candidate -> candidate instanceof SurvivorEntity s && s.isAlive())) {
                SurvivorEntity survivor = (SurvivorEntity) entity;
                if (survivor.isBaseGuard() && base.faction().equalsIgnoreCase(survivor.effectiveFaction())) {
                    defenders.add(survivor);
                } else if (!survivor.isRecruited()
                        && attackerMatches(siege, survivor.effectiveFaction())) {
                    attackers.add(survivor);
                }
            }

            if (attackers.isEmpty()) {
                // The walls held.
                iterator.remove();
                state.chronicle(now, base.name() + " held. The " + siege.attackerFaction
                        + " broke and melted into the hills.", true);
                chronicleBroadcast(world, state, base.name() + " has held the siege!", true);
                RealmMusic.play(world, base.center(), RealmMusic.VICTORY_FANFARE);
                com.rivalrealms.sound.ModSounds.playVoice(world, base.center(), "siege_victory");
                state.adjustWealth(base.faction(), 2);
                continue;
            }
            if (now < siege.phaseUntil) {
                // While the assault runs: civilians flee outward, repeatedly.
                if (now % 200L == 0L) {
                    evacuateCivilians(world, base, attackers);
                }
                continue;
            }
            switch (siege.phase) {
                case 0 -> {
                    // Phase 1: messengers run to the nearest allied town.
                    siege.phase = 1;
                    siege.phaseUntil = now + 1200L;
                    sendMessenger(world, state, base, siege);
                    com.rivalrealms.sound.ModSounds.playVoice(world, base.center(), "civilian_flee");
                }
                case 1 -> {
                    // Phase 2: allied relief if the messenger made it.
                    siege.phase = 2;
                    siege.phaseUntil = now + 2400L;
                    if (siege.messengerSent > 0 && !siege.reliefArrived) {
                        sendRelief(world, state, base, siege);
                    }
                }
                default -> {
                    // Phase 3: the aftermath. The town that falls, falls for good;
                    // the town that holds rebuilds scarred.
                    iterator.remove();
                    if (defenders.isEmpty() && !attackers.isEmpty()) {
                        state.chronicle(now, "The last defenders of " + base.name()
                                + " fell at dusk. The " + siege.attackerFaction + " own the walls now.", true);
                    } else {
                        state.drain(base, 4, 6);
                        // Battle scars: a small grave row outside the walls.
                        BlockPos grave = surface(world, base.center().offset(
                                Direction.Type.HORIZONTAL.random(world.random), base.radius() + 3));
                        for (int i = 0; i < Math.max(1, 3 - defenders.size()); i++) {
                            world.setBlockState(grave.add(i, 0, 0),
                                    net.minecraft.block.Blocks.COBBLESTONE_SLAB.getDefaultState());
                            world.setBlockState(grave.add(i, 0, 1),
                                    net.minecraft.block.Blocks.OAK_FENCE.getDefaultState());
                        }
                        state.chronicle(now, "Graves were raised outside " + base.name()
                                + " for those who held the walls.", false);
                    }
                }
            }
        }
    }

    private static boolean attackerMatches(Siege siege, String faction) {
        return siege.attackerFaction.equalsIgnoreCase(faction)
                || "Marauders".equalsIgnoreCase(faction);
    }

    private static void evacuateCivilians(ServerWorld world, RealmState.BaseRecord base,
                                          List<SurvivorEntity> attackers) {
        if (attackers.isEmpty()) {
            return;
        }
        double cx = attackers.get(0).getX();
        double cz = attackers.get(0).getZ();
        for (SurvivorEntity civilian : population(world, base)) {
            if (civilian.isBaseGuard() || civilian.isChild() && world.random.nextBoolean()) {
                continue;
            }
            double awayX = civilian.getX() - cx;
            double awayZ = civilian.getZ() - cz;
            double len = Math.sqrt(awayX * awayX + awayZ * awayZ);
            if (len < 0.01) {
                continue;
            }
            BlockPos flight = surface(world, BlockPos.ofFloored(
                    civilian.getX() + awayX / len * 24.0, civilian.getY(),
                    civilian.getZ() + awayZ / len * 24.0));
            civilian.getNavigation().startMovingTo(flight.getX() + 0.5, flight.getY(), flight.getZ() + 0.5, 1.35);
        }
    }

    private static void sendMessenger(ServerWorld world, RealmState state,
                                      RealmState.BaseRecord base, Siege siege) {
        RealmState.BaseRecord ally = null;
        for (RealmState.BaseRecord other : state.bases()) {
            if (other != base && !other.abandoned()
                    && !state.isHostile(other.faction(), base.faction())
                    && other.center().getSquaredDistance(base.center()) <= 260.0 * 260.0
                    && areaLoaded(world, other.center(), other.radius() + 8)) {
                ally = other;
                break;
            }
        }
        if (ally == null) {
            return;
        }
        SurvivorEntity messenger = com.rivalrealms.entity.ModEntities.SURVIVOR.create(world);
        if (messenger == null) {
            return;
        }
        BlockPos spot = surface(world, base.center().offset(
                Direction.Type.HORIZONTAL.random(world.random), base.radius() + 2));
        messenger.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                world.random.nextFloat() * 360.0f, 0.0f);
        messenger.setArchetype(com.rivalrealms.entity.Archetype.byFaction(base.faction()));
        messenger.setCustomName(Text.literal("Royal Messenger").formatted(Formatting.YELLOW));
        world.spawnEntity(messenger);
        messenger.getNavigation().startMovingTo(ally.center().getX(), ally.center().getY(),
                ally.center().getZ(), 1.45);
        siege.messengerSent = world.getTime();
        state.chronicle(world.getTime(), "A messenger slipped the siege lines of " + base.name()
                + ", riding for " + ally.name() + ".", false);
    }

    private static void sendRelief(ServerWorld world, RealmState state,
                                   RealmState.BaseRecord base, Siege siege) {
        siege.reliefArrived = true;
        BlockPos edge = surface(world, base.center().offset(
                Direction.Type.HORIZONTAL.random(world.random), base.radius() + 8));
        for (int i = 0; i < 2; i++) {
            SurvivorEntity relief = com.rivalrealms.entity.ModEntities.SURVIVOR.create(world);
            if (relief == null) {
                continue;
            }
            BlockPos at = surface(world, edge.add(i * 2, 0, world.random.nextInt(3) - 1));
            relief.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            relief.setArchetype(com.rivalrealms.entity.Archetype.byFaction(base.faction()));
            relief.assignGuard(base.center(), base.owner(), base.faction());
            relief.setCustomName(Text.literal("Relief Guard").formatted(Formatting.AQUA));
            world.spawnEntity(relief);
        }
        chronicleBroadcast(world, state, "Relief columns reach " + base.name() + "!", false);
    }

    // ------------------------------------------------------------- seasons

    private static void tickSeason(ServerWorld world, RealmState state) {
        long time = world.getTime();
        if (time % (8L * 24000L) != 0L) {
            return;
        }
        String line = switch (season(world)) {
            case SPRING -> "Spring returns to the realm. The roads dry, the world stirs.";
            case SUMMER -> "High summer. Caravans crowd the roads.";
            case AUTUMN -> "Autumn. Settlements begin drying and salting for winter.";
            default -> "WINTER has come to the realm. The wells sing a colder song.";
        };
        state.chronicle(time, line, false);
        chronicleBroadcast(world, state, line, false);
        if (season(world) == WINTER) {
            // The realm's kitchens work double through the first winter day.
            for (RealmState.BaseRecord base : state.bases()) {
                if (!base.abandoned() && areaLoaded(world, base.center(), base.radius())) {
                    state.recordSettlementWork(base, 4, 0, 0);
                }
            }
        }
        if (season(world) == AUTUMN) {
            celebrate(world, state, "harvest", RealmMusic.HARVEST_REEL);
        }
    }

    // ------------------------------------------------------------- celebrations

    private static void tickCelebrations(ServerWorld world, RealmState state) {
        if (world.random.nextFloat() >= 0.10f) {
            return;
        }
        List<RealmState.BaseRecord> loaded = new ArrayList<>();
        for (RealmState.BaseRecord base : state.bases()) {
            if (!base.abandoned() && base.food() >= 30
                    && areaLoaded(world, base.center(), base.radius())) {
                loaded.add(base);
            }
        }
        if (loaded.isEmpty()) {
            return;
        }
        RealmState.BaseRecord base = loaded.get(world.random.nextInt(loaded.size()));
        if (world.random.nextBoolean()) {
            // A wedding: two friends become family, and the town talks for days.
            List<SurvivorEntity> adults = population(world, base);
            SurvivorEntity first = null;
            SurvivorEntity second = null;
            for (SurvivorEntity candidate : adults) {
                if (candidate.isChild() || candidate.isRecruited()) {
                    continue;
                }
                if (first == null) {
                    first = candidate;
                } else {
                    second = candidate;
                    break;
                }
            }
            if (first != null && second != null) {
                first.setBond(second.getUuid(), (byte) 2);
                second.setBond(first.getUuid(), (byte) 2);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                        first.getX(), first.getY() + 2.2, first.getZ(), 8, 0.6, 0.4, 0.6, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                        second.getX(), second.getY() + 2.2, second.getZ(), 8, 0.6, 0.4, 0.6, 0.0);
                RealmMusic.play(world, base.center(), RealmMusic.HEARTHFIRE);
                state.chronicle(world.getTime(), first.getName().getString() + " and "
                        + second.getName().getString() + " were wed at " + base.name()
                        + ". The whole settlement feasted.", true);
                chronicleBroadcast(world, state, "Wedding bells at " + base.name() + "!", true);
                com.rivalrealms.sound.ModSounds.playVoice(world, base.center(), "celebration");
            }
        } else {
            celebrate(world, state, "an evening of songs", RealmMusic.WANDERERS_REST);
        }
    }

    private static void celebrate(ServerWorld world, RealmState state, String what, String song) {
        for (RealmState.BaseRecord base : state.bases()) {
            if (base.abandoned() || base.food() < 30
                    || !areaLoaded(world, base.center(), base.radius())) {
                continue;
            }
            RealmMusic.play(world, base.center(), song);
            com.rivalrealms.sound.ModSounds.playVoice(world, base.center(), "celebration");
            state.chronicle(world.getTime(), base.name() + " kept " + what
                    + " with fires, fiddles and full cups.", false);
        }
    }

    // ------------------------------------------------------------- expeditions

    private record Expedition(ServerWorld world, BlockPos destination, long founded) {
    }

    private static final List<Expedition> EXPEDITIONS = new ArrayList<>();

    private static void tickExpeditions(ServerWorld world) {
        // Rarely, a settlement sends people into the unknown to found a new home.
        if (world.getTime() % 72000L != 0L || world.random.nextInt(3) != 0) {
            return;
        }
        List<RealmState.BaseRecord> loaded = new ArrayList<>();
        for (RealmState.BaseRecord base : state_of(world).bases()) {
            if (!base.abandoned() && base.level() >= 3
                    && areaLoaded(world, base.center(), base.radius())) {
                loaded.add(base);
            }
        }
        if (loaded.isEmpty()) {
            return;
        }
        RealmState.BaseRecord home = loaded.get(world.random.nextInt(loaded.size()));
        BlockPos destination = home.center().add(
                (world.random.nextInt(161) - 80), 0, (world.random.nextInt(161) - 80));
        if (!areaLoaded(world, destination, 16)) {
            return;
        }
        int founded = 0;
        for (int i = 0; i < 3; i++) {
            SurvivorEntity pioneer = com.rivalrealms.entity.ModEntities.SURVIVOR.create(world);
            if (pioneer == null) {
                continue;
            }
            BlockPos at = surface(world, home.center().add(i * 2 - 2, 0, world.random.nextInt(3) - 1));
            pioneer.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            pioneer.setArchetype(com.rivalrealms.entity.Archetype.byFaction(home.faction()));
            pioneer.assignWorker(destination, home.owner(), home.faction(), SettlementRole.BUILDER);
            pioneer.setCustomName(Text.literal("Pioneer · " + home.faction()).formatted(Formatting.AQUA));
            pioneer.setFamily(surname(home.faction(), world.random), null);
            world.spawnEntity(pioneer);
            founded++;
        }
        if (founded > 0) {
            EXPEDITIONS.add(new Expedition(world, destination, world.getTime()));
            state_of(world).chronicle(world.getTime(), founded + " pioneers left " + home.name()
                    + " to found a home in the wilds.", true);
            chronicleBroadcast(world, state_of(world), "An expedition departs " + home.name() + " for the unknown.", true);
        }
    }

    private static RealmState state_of(ServerWorld world) {
        return RealmState.get(world);
    }

    // ------------------------------------------------------------- resettle + camps + treasure

    /** Abandoned places remember hands: someone always starts again. */
    private static void tickResettlement(ServerWorld world, RealmState state) {
        for (RealmState.BaseRecord base : state.bases()) {
            if (!base.abandoned() || world.random.nextFloat() >= 0.07f
                    || !areaLoaded(world, base.center(), base.radius() + 8)) {
                continue;
            }
            base.setAbandoned(false);
            BlockPos edge = surface(world, base.center().offset(
                    Direction.Type.HORIZONTAL.random(world.random), base.radius()));
            for (int i = 0; i < 2; i++) {
                SurvivorEntity settler = com.rivalrealms.entity.ModEntities.SURVIVOR.create(world);
                if (settler == null) {
                    continue;
                }
                BlockPos at = surface(world, edge.add(i * 2, 0, world.random.nextInt(3) - 1));
                settler.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                        world.random.nextFloat() * 360.0f, 0.0f);
                settler.setArchetype(com.rivalrealms.entity.Archetype.byFaction(base.faction()));
                settler.assignWorker(base.center(), base.owner(), base.faction(),
                        i == 0 ? SettlementRole.BUILDER : SettlementRole.FARMER);
                settler.setCustomName(Text.literal("New Settler").formatted(Formatting.GREEN));
                settler.setFamily(surname(base.faction(), world.random), null);
                world.spawnEntity(settler);
            }
            state.chronicle(world.getTime(), "Smoke rises again from " + base.name()
                    + ": new folk have begun rebuilding the old walls.", true);
            chronicleBroadcast(world, state, base.name() + " is being rebuilt!", true);
        }
    }

    /** Player camps grow recognisable: trophies, a banner, and a name. */
    private static void tickCampGrowth(ServerWorld world, RealmState state) {
        for (RealmState.BaseRecord base : state.bases()) {
            if (base.level() >= 3 || !base.name().endsWith("'s Camp")
                    || !areaLoaded(world, base.center(), base.radius())) {
                continue;
            }
            var owner = world.getServer().getPlayerManager().getPlayer(base.owner());
            if (owner == null || !base.contains(owner.getBlockPos())) {
                continue;
            }
            if (world.random.nextFloat() < 0.25f) {
                base.setFamineCycles(base.famineCycles() + 1);
                if (base.famineCycles() >= 2) {
                    base.setFamineCycles(0);
                    state.upgrade(base);
                    BlockPos at = surface(world, base.center().add(2, 0, 2));
                    world.setBlockState(at, com.rivalrealms.block.ModBlocks.TROPHY_SKULL.getDefaultState());
                    world.setBlockState(at.add(1, 0, 0), com.rivalrealms.block.ModBlocks.REALM_BANNER.getDefaultState());
                    owner.sendMessage(Text.literal("Your camp has grown - trophies and a banner now mark it (level "
                            + base.level() + ").").formatted(Formatting.GOLD), true);
                    state.chronicle(world.getTime(), base.name() + " has become a landmark of the roads.", false);
                }
            }
        }
    }

    private static final Map<java.util.UUID, BlockPos> TREASURES = new HashMap<>();

    /** Registers a buried treasure so the world can hint at it. */
    public static void registerTreasure(java.util.UUID hunter, BlockPos pos) {
        TREASURES.put(hunter, pos.toImmutable());
    }

    private static void tickTreasureHints(ServerWorld world) {
        if (TREASURES.isEmpty() || world.getTime() % 200L != 0L) {
            return;
        }
        Iterator<Map.Entry<java.util.UUID, BlockPos>> iterator = TREASURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<java.util.UUID, BlockPos> entry = iterator.next();
            var hunter = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (hunter == null) {
                continue;
            }
            BlockPos spot = entry.getValue();
            double distance = Math.sqrt(hunter.squaredDistanceTo(spot.getX(), spot.getY(), spot.getZ()));
            if (distance < 2.5) {
                hunter.sendMessage(Text.literal("The X on your map is under your boots.").formatted(Formatting.GOLD), true);
                iterator.remove();
            } else if (distance < 24.0 && world.isChunkLoaded(spot.getX() >> 4, spot.getZ() >> 4)) {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                        spot.getX() + 0.5, spot.getY() + 1.2, spot.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    // ------------------------------------------------------------- helpers

    public static List<SurvivorEntity> population(ServerWorld world, RealmState.BaseRecord base) {
        List<SurvivorEntity> pop = new ArrayList<>();
        for (Entity entity : world.getOtherEntities(null, box(base),
                candidate -> candidate instanceof SurvivorEntity survivor && survivor.isAlive()
                        && !survivor.isRecruited()
                        && base.faction().equalsIgnoreCase(survivor.effectiveFaction()))) {
            pop.add((SurvivorEntity) entity);
        }
        return pop;
    }

    private static Box box(RealmState.BaseRecord base) {
        return new Box(base.center()).expand(base.radius());
    }

    private static BlockPos surface(ServerWorld world, BlockPos requested) {
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, requested);
        return top.getY() >= world.getBottomY() && top.getY() < world.getTopY() ? top : requested;
    }

    private static boolean areaLoaded(ServerWorld world, BlockPos center, int radius) {
        int[] offsets = {-radius, radius};
        for (int x : offsets) {
            for (int z : offsets) {
                if (!world.getChunkManager().isChunkLoaded((center.getX() + x) >> 4,
                        (center.getZ() + z) >> 4)) {
                    return false;
                }
            }
        }
        return world.getChunkManager().isChunkLoaded(center.getX() >> 4, center.getZ() >> 4);
    }

    private static long lastHerald;

    private static void chronicleBroadcast(ServerWorld world, RealmState state, String story, boolean major) {
        Text text = Text.literal(story).formatted(major ? Formatting.GOLD : Formatting.GRAY);
        world.getServer().getPlayerManager().broadcast(text, false);
        // Big news earns the town crier's voice - but he needs to breathe.
        if (major && world.getTime() - lastHerald >= 2400L) {
            lastHerald = world.getTime();
            for (var player : world.getServer().getPlayerManager().getPlayerList()) {
                com.rivalrealms.sound.ModSounds.playVoiceFor(world, player.getBlockPos(), "herald_news", player);
            }
        }
    }
}
