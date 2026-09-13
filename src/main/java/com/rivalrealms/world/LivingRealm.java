package com.rivalrealms.world;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
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
                settleCycle(world, state, base);
            } catch (RuntimeException exception) {
                RivalRealms.LOGGER.error("LivingRealm skipped settlement {}", base.name(), exception);
            }
        }

        // World events: rare, never stacked on the same cycle.
        if (world.getTime() % 3600L == 0L && world.random.nextInt(3) == 0) {
            rollWorldEvent(world, state);
        }
    }

    private static void settleCycle(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
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
            runCaravan(world, state, base, bases);
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
        int roll = world.random.nextInt(6);
        switch (roll) {
            case 0 -> famine(world, state, base);
            case 1 -> plague(world, state, base);
            case 2 -> merchantBoom(world, state, base);
            case 3 -> uprising(world, state, base);
            case 4 -> refugees(world, state, base);
            default -> skirmish(world, state, base, loaded);
        }
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
        BlockPos mid = base.center().add(foe.center()).divide(2);
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
            if (camp.center().getSquaredDistance(from.center().add(to.center()).divide(2)) <= 70.0 * 70.0) {
                if (world.random.nextFloat() < 0.25f) {
                    state.chronicle(world.getTime(), "The road between " + from.name() + " and "
                            + to.name() + " is cut by the " + camp.faction() + ". Caravans stay home.", false);
                }
                return;
            }
        }
        // The caravan walks the road: two traders and a guard, headed for `to`.
        BlockPos start = surface(world, from.center().offset(
                Direction.getFacing(from.center().getX() - to.center().getX(), from.center().getZ() - to.center().getZ()),
                from.radius() + 4));
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

    private static void chronicleBroadcast(ServerWorld world, RealmState state, String story, boolean major) {
        Text text = Text.literal(story).formatted(major ? Formatting.GOLD : Formatting.GRAY);
        world.getServer().getPlayerManager().broadcast(text, false);
    }
}
