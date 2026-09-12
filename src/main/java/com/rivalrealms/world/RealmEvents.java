package com.rivalrealms.world;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.MerchantShipEntity;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.PirateShipEntity;
import com.rivalrealms.entity.SailingShipEntity;
import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Handles low-frequency living-world encounters, jobs, expansion, and raids. */
public final class RealmEvents {
    private RealmEvents() {
    }

    public static void onServerStarted(MinecraftServer server) {
        // Persistent state is lazy-loaded, so a brand-new world does not create
        // save data until the first base or diplomacy action actually needs it.
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getTime() % 200L == 0) {
                try {
                    tickSettlements(world);
                } catch (RuntimeException exception) {
                    // One malformed/old settlement must not take down the
                    // entire server. The base is skipped until the next tick.
                    RivalRealms.LOGGER.error("Rival Realms settlement tick failed in {}", world.getRegistryKey().getValue(), exception);
                }
            }
            if (world.getTime() % 100L == 0) {
                try {
                    tickMaritimeEncounters(world);
                } catch (RuntimeException exception) {
                    RivalRealms.LOGGER.error("Rival Realms maritime encounter tick failed in {}",
                            world.getRegistryKey().getValue(), exception);
                }
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.age % 1200 != 0) {
                continue;
            }
            ServerWorld world = player.getServerWorld();
            if (world.random.nextFloat() > 0.22f) {
                continue;
            }
            List<Entity> nearby = world.getOtherEntities(player, player.getBoundingBox().expand(56),
                    entity -> entity instanceof SurvivorEntity);
            if (nearby.isEmpty()) {
                spawnEncounter(world, player);
            }
        }
    }

    /**
     * Crop theft detection: a player who breaks a MATURE crop inside a
     * settlement's fields gets caught by anyone working nearby. Reputation
     * drops and farm folk hold a grudge — warcamps don't care.
     */
    public static void onBlockBroken(net.minecraft.world.World world, BlockPos pos,
                                     net.minecraft.block.BlockState state, net.minecraft.entity.Entity breaker) {
        if (!(world instanceof ServerWorld serverWorld) || !(breaker instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!(state.getBlock() instanceof net.minecraft.block.CropBlock)
                || !state.contains(net.minecraft.block.CropBlock.AGE)
                || state.get(net.minecraft.block.CropBlock.AGE) < 7) {
            return;
        }
        RealmState realms = RealmState.get(serverWorld);
        for (RealmState.BaseRecord base : realms.bases()) {
            if (base.center().isWithinDistance(pos, base.radius() + 6.0)) {
                SurvivorEntity.onCropTheft(serverWorld, player, base.faction(), pos);
                player.sendMessage(Text.literal("Someone saw you take from "
                        + base.name() + "'s fields.").formatted(net.minecraft.util.Formatting.GOLD), true);
                break;
            }
        }
    }

    /**
     * Initial population for a freshly generated site. The world should feel
     * inhabited the moment the player first crests the hill: a guard on the
     * wall and the first workers already about their trade — farmsteads
     * always get their farmhands at once.
     */
    public static void populateSettlement(ServerWorld world, BlockPos center,
                                          BuildStyle style, SettlementVariant variant) {
        String faction = style.faction();
        UUID owner = UUID.nameUUIDFromBytes(("rivalrealms:population:" + center.asLong())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        if (style == BuildStyle.MARAUDER) {
            // A full warband: the warlord holds the camp, the rest roam the
            // roads looking for caravans, farms and throats.
            SurvivorEntity warlord = spawnSettler(world, center, owner, faction,
                    SettlementRole.WARLORD, true);
            if (warlord != null && world.random.nextFloat() < 0.5f) {
                makeChampion(world, warlord);
            }
            spawnSettler(world, center, owner, faction, SettlementRole.RAIDER, true);
            spawnSettler(world, center, owner, faction, SettlementRole.RAIDER, false);
            spawnSettler(world, center, owner, faction, SettlementRole.RAIDER, false);
            return;
        }
        if (style == BuildStyle.HEARTHFOLK) {
            // Quiet folk: a hearth guard, two farmhands and a smith who keeps
            // the village armed.
            spawnSettler(world, center, owner, faction, SettlementRole.GUARD, true);
            spawnSettler(world, center, owner, faction, SettlementRole.FARMER, false);
            spawnSettler(world, center, owner, faction, SettlementRole.FARMER, false);
            spawnSettler(world, center, owner, faction, SettlementRole.BLACKSMITH, false);
            return;
        }
        if (variant == SettlementVariant.FARMSTEAD) {
            spawnSettler(world, center, owner, faction, SettlementRole.GUARD, true);
            spawnSettler(world, center, owner, faction, SettlementRole.FARMER, false);
            spawnSettler(world, center, owner, faction, SettlementRole.FARMER, false);
            return;
        }

        SettlementRole[] starters = switch (variant) {
            case HARBOR, SHIPYARD -> new SettlementRole[]{SettlementRole.SAILOR, SettlementRole.FARMER};
            case SKYPORT, AIRSHIP_YARD -> new SettlementRole[]{SettlementRole.NAVIGATOR, SettlementRole.FARMER};
            case RUIN, GRAVEYARD -> new SettlementRole[]{SettlementRole.GUARD};
            default -> new SettlementRole[]{SettlementRole.FARMER, SettlementRole.BUILDER};
        };
        for (SettlementRole role : starters) {
            spawnSettler(world, center, owner, faction, role, role == SettlementRole.GUARD);
        }
        spawnSettler(world, center, owner, faction, SettlementRole.GUARD, true);
    }

    /**
     * A fraction of settlement NPCs and ship captains are champions:
     * enchanted veteran gear, a golden name plate, and real extra grit.
     * Drop everything they carry.
     */
    private static void makeChampion(ServerWorld world, SurvivorEntity survivor) {
        survivor.setCustomName(Text.literal("Champion · " + survivor.getArchetype().title())
                .formatted(net.minecraft.util.Formatting.GOLD));
        survivor.setCustomNameVisible(true);
        var health = survivor.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() + 12.0);
            survivor.setHealth((float) health.getBaseValue());
        }
        var enchantments = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        survivor.getMainHandStack().addEnchantment(
                enchantments.entryOf(Enchantments.SHARPNESS), 2 + world.random.nextInt(2));
        survivor.getEquippedStack(EquipmentSlot.HEAD).addEnchantment(
                enchantments.entryOf(Enchantments.PROTECTION), 2);
        survivor.getEquippedStack(EquipmentSlot.CHEST).addEnchantment(
                enchantments.entryOf(Enchantments.PROTECTION), 1 + world.random.nextInt(2));
    }

    /** Spawns one permanent resident near the settlement heart; {@code null} if it failed. */
    private static SurvivorEntity spawnSettler(ServerWorld world, BlockPos center, UUID owner,
                                               String faction, SettlementRole role, boolean guard) {
        SurvivorEntity settler = ModEntities.SURVIVOR.create(world);
        if (settler == null) {
            return null;
        }
        BlockPos spawn = surfacePosition(world,
                center.add(world.random.nextInt(15) - 7, 0, world.random.nextInt(15) - 7));
        if (spawn == null) {
            return null;
        }
        settler.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                world.random.nextFloat() * 360.0f, 0.0f);
        settler.setArchetype(Archetype.byFaction(faction));
        if (role == SettlementRole.RAIDER || role == SettlementRole.WARLORD) {
            settler.setCustomName(Text.literal(role.displayName() + " · "
                    + Archetype.byFaction(faction).title()));
        }
        if (guard || role == SettlementRole.GUARD) {
            settler.assignGuard(center, owner, faction);
        } else {
            settler.assignWorker(center, owner, faction, role);
        }
        if (!world.spawnEntity(settler)) {
            RivalRealms.LOGGER.error("Rival Realms failed to spawn a {} at {}", role.displayName(), center);
            return null;
        }
        return settler;
    }

    private static void tickSettlements(ServerWorld world) {
        RealmState state = RealmState.get(world);
        for (RealmState.BaseRecord base : new ArrayList<>(state.bases())) {
            BlockPos center = base.center();
            // Never force-load abandoned bases from a global server tick. The
            // player or another ticket must already have the settlement area
            // loaded before entities or blocks are touched.
            if (!areaLoaded(world, center, base.radius() + 32)) {
                continue;
            }
            try {
                ensureGuards(world, base);
                ensureWorkforce(world, base);
                progressSettlement(world, state, base);
                if (world.getTime() % 1200L == 0 && world.random.nextFloat() < 0.08f) {
                    tryStartRaid(world, state, base);
                }
            } catch (RuntimeException exception) {
                RivalRealms.LOGGER.error("Rival Realms skipped settlement {} at {}", base.name(), center, exception);
            }
        }
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

    private static void ensureGuards(ServerWorld world, RealmState.BaseRecord base) {
        BlockPos center = base.center();
        Box search = new Box(center).expand(base.radius());
        List<Entity> defenders = world.getOtherEntities(null, search,
                entity -> entity instanceof SurvivorEntity guard
                        && guard.isBaseGuard()
                        && base.faction().equalsIgnoreCase(guard.effectiveFaction()));
        int desired = Math.min(5, 1 + base.level());
        if (defenders.size() >= desired || world.random.nextFloat() > 0.45f) {
            return;
        }

        Archetype culture = Archetype.byFaction(base.faction());
        SurvivorEntity guard = ModEntities.SURVIVOR.create(world);
        if (guard == null) {
            return;
        }
        BlockPos spawn = surfacePosition(world,
                center.add(world.random.nextInt(9) - 4, 0, world.random.nextInt(9) - 4));
        guard.refreshPositionAndAngles(spawn, world.random.nextFloat() * 360.0f, 0.0f);
        guard.setArchetype(culture);
        guard.assignGuard(center, base.owner(), base.faction());
        if (world.random.nextFloat() < 0.12f) {
            makeChampion(world, guard);
        }
        world.spawnEntity(guard);

        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(base.owner());
        if (owner != null && defenders.isEmpty()) {
            owner.sendMessage(Text.literal("Your settlement has recruited a " + culture.title() + " guard."), true);
        }
    }

    private static void ensureWorkforce(ServerWorld world, RealmState.BaseRecord base) {
        BlockPos center = base.center();
        List<Entity> workers = world.getOtherEntities(null, new Box(center).expand(base.radius()),
                entity -> entity instanceof SurvivorEntity worker
                        && worker.isSettlementWorker()
                        && base.faction().equalsIgnoreCase(worker.effectiveFaction()));
        int desired = Math.min(10, base.level() + 2);
        if (workers.size() >= desired || world.random.nextFloat() > 0.55f) {
            return;
        }

        SettlementRole role = nextRole(base.style(), workers);
        if (role == SettlementRole.NONE) {
            return;
        }
        Archetype culture = Archetype.byFaction(base.faction());
        SurvivorEntity worker = ModEntities.SURVIVOR.create(world);
        if (worker == null) {
            return;
        }
        BlockPos spawn = surfacePosition(world,
                center.add(world.random.nextInt(13) - 6, 0, world.random.nextInt(13) - 6));
        worker.refreshPositionAndAngles(spawn, world.random.nextFloat() * 360.0f, 0.0f);
        worker.setArchetype(culture);
        worker.assignWorker(center, base.owner(), base.faction(), role);
        world.spawnEntity(worker);

        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(base.owner());
        if (owner != null) {
            owner.sendMessage(Text.literal(role.displayName() + " joined " + base.name() + "."), true);
        }
    }

    private static SettlementRole nextRole(BuildStyle style, List<Entity> workers) {
        SettlementRole[] common = {
                SettlementRole.BUILDER, SettlementRole.FARMER,
                SettlementRole.BAKER, SettlementRole.HERBALIST
        };
        for (SettlementRole role : common) {
            if (!hasRole(workers, role)) {
                return role;
            }
        }

        SettlementRole[] specialists = switch (style) {
            case PIRATE -> new SettlementRole[]{
                    SettlementRole.SAILOR, SettlementRole.QUARTERMASTER,
                    SettlementRole.GUNNER, SettlementRole.NAVIGATOR
            };
            case SKY -> new SettlementRole[]{
                    SettlementRole.NAVIGATOR, SettlementRole.SCOUT,
                    SettlementRole.BLACKSMITH, SettlementRole.GUNNER
            };
            case WESTERN -> new SettlementRole[]{
                    SettlementRole.BLACKSMITH, SettlementRole.MINER,
                    SettlementRole.TRADER, SettlementRole.SCOUT
            };
            case KNIGHT, CUSTOM -> new SettlementRole[]{
                    SettlementRole.MASON, SettlementRole.BLACKSMITH,
                    SettlementRole.JEWELER, SettlementRole.MINER
            };
            case MARAUDER -> new SettlementRole[]{
                    SettlementRole.RAIDER, SettlementRole.RAIDER,
                    SettlementRole.WARLORD, SettlementRole.GUNNER
            };
            case HEARTHFOLK -> new SettlementRole[]{
                    SettlementRole.FARMER, SettlementRole.BAKER,
                    SettlementRole.BLACKSMITH, SettlementRole.TRADER
            };
        };
        for (SettlementRole role : specialists) {
            if (!hasRole(workers, role)) {
                return role;
            }
        }
        return SettlementRole.TRADER;
    }

    private static boolean hasRole(List<Entity> workers, SettlementRole role) {
        for (Entity entity : workers) {
            if (entity instanceof SurvivorEntity survivor && survivor.settlementRole() == role) {
                return true;
            }
        }
        return false;
    }

    private static void progressSettlement(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        Box search = new Box(base.center()).expand(base.radius());
        int farmers = 0;
        int builders = 0;
        int bakers = 0;
        int herbalists = 0;
        int traders = 0;
        int merchants = 0;
        int jewelers = 0;
        int blacksmiths = 0;
        int masons = 0;
        int miners = 0;
        int sailors = 0;
        int quartermasters = 0;
        for (Entity entity : world.getOtherEntities(null, search,
                candidate -> candidate instanceof SurvivorEntity worker
                        && worker.isSettlementWorker()
                        && base.faction().equalsIgnoreCase(worker.effectiveFaction()))) {
            SurvivorEntity worker = (SurvivorEntity) entity;
            switch (worker.settlementRole()) {
                case FARMER -> farmers++;
                case BUILDER -> builders++;
                case BAKER -> bakers++;
                case HERBALIST -> herbalists++;
                case TRADER -> traders++;
                case MERCHANT -> merchants++;
                case JEWELER -> jewelers++;
                case BLACKSMITH -> blacksmiths++;
                case MASON -> masons++;
                case MINER -> miners++;
                case SAILOR -> sailors++;
                case QUARTERMASTER -> quartermasters++;
                default -> {
                }
            }
        }

        int food = farmers * 2 + bakers * 3 + herbalists + traders + merchants + quartermasters;
        int materials = blacksmiths * 2 + masons * 2 + miners * 3 + jewelers * 2 + traders + merchants + sailors;
        int work = builders * 2 + masons + jewelers + miners + sailors;
        if (food > 0 || materials > 0 || work > 0) {
            state.recordSettlementWork(base, food, materials, work);
        }

        int workRequired = 12 + base.level() * 8;
        int foodCost = 4 + base.level() * 2;
        int materialCost = 12 + base.level() * 6;
        if (builders > 0 && base.workProgress() >= workRequired
                && state.beginExpansion(base, foodCost, materialCost, world.getTime())) {
            StructureBuilder.expand(world, base.center(), base.style(), base.level());
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(base.owner());
            if (owner != null) {
                owner.sendMessage(Text.literal(base.name() + " expanded to level " + base.level()
                        + ". New workshops and defenses are being built."), false);
            }
        }
    }

    private static void tryStartRaid(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        if (world.getTime() - base.lastRaid() < 6000L) {
            return;
        }

        List<Entity> activeRaiders = world.getOtherEntities(null,
                new Box(base.center()).expand(base.radius() + 40.0),
                entity -> entity instanceof SurvivorEntity survivor
                        && !survivor.isRecruited()
                        && state.isHostile(base.faction(), survivor.effectiveFaction()));
        if (activeRaiders.size() >= 8) {
            // Do not stack another raid on top of an unresolved one.
            return;
        }

        // One raid in three is a Marauder warband: they raid EVERYONE, and
        // their warcamps sit close enough to be a constant threat.
        Archetype attacker;
        if (world.random.nextInt(3) == 0) {
            attacker = Archetype.MARAUDER;
        } else {
            attacker = null;
            for (Archetype candidate : Archetype.values()) {
                if (state.isHostile(base.faction(), candidate.faction())) {
                    attacker = candidate;
                    break;
                }
            }
        }
        if (attacker == null) {
            return;
        }

        base.setLastRaid(world.getTime());
        state.markDirty();
        BlockPos center = base.center();
        List<Entity> defenders = world.getOtherEntities(null, new Box(center).expand(base.radius()),
                entity -> entity instanceof SurvivorEntity guard
                        && guard.isBaseGuard()
                        && base.faction().equalsIgnoreCase(guard.effectiveFaction()));
        int raiders = Math.min(6, 2 + base.level());
        for (int i = 0; i < raiders; i++) {
            SurvivorEntity raider = ModEntities.SURVIVOR.create(world);
            if (raider == null) {
                continue;
            }
            BlockPos spawn = surfacePosition(world,
                    center.add(12 + world.random.nextInt(9), 0, world.random.nextInt(17) - 8));
            raider.refreshPositionAndAngles(spawn, world.random.nextFloat() * 360.0f, 0.0f);
            raider.setArchetype(attacker);
            raider.setCustomName(Text.literal("Raiders · " + attacker.title()));
            if (!defenders.isEmpty()) {
                raider.setTarget((net.minecraft.entity.LivingEntity) defenders.get(i % defenders.size()));
            }
            world.spawnEntity(raider);
        }

        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(base.owner());
        if (owner != null) {
            owner.sendMessage(Text.literal(("Marauders".equals(attacker.faction())
                    ? "WARBAND sighted at " + base.name() + " - the Marauders are upon you!"
                    : "Raid incoming at " + base.name() + ": " + attacker.title() + " forces are approaching.")), false);
        }
    }

    private static void tickMaritimeEncounters(ServerWorld world) {
        if (!World.OVERWORLD.equals(world.getRegistryKey())) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.age % 200 != 0) {
                continue;
            }
            Box area = new Box(player.getBlockPos()).expand(112.0);
            List<Entity> nearbyEntities = world.getOtherEntities(null, area,
                    entity -> entity instanceof MerchantShipEntity || entity instanceof PirateShipEntity);
            List<MerchantShipEntity> merchants = nearbyEntities.stream()
                    .filter(MerchantShipEntity.class::isInstance)
                    .map(MerchantShipEntity.class::cast)
                    .filter(Entity::isAlive)
                    .toList();
            if (merchants.isEmpty() && world.random.nextFloat() < 0.35f) {
                spawnMerchantShip(world, player.getBlockPos(), 96);
                continue;
            }
            for (MerchantShipEntity merchant : merchants) {
                if (merchant.isUnderAttack() || world.random.nextFloat() > 0.30f) {
                    continue;
                }
                boolean pirateNearby = nearbyEntities.stream().anyMatch(entity ->
                        entity instanceof PirateShipEntity pirate && pirate.isAlive()
                                && pirate.squaredDistanceTo(merchant) < 96.0 * 96.0);
                if (!pirateNearby) {
                    spawnPirateRaid(world, merchant);
                }
            }
        }
    }

    /** Used by the command as a deterministic maritime showcase. */
    public static int spawnShowcaseConvoy(ServerWorld world, BlockPos origin) {
        MerchantShipEntity merchant = spawnMerchantShip(world, origin, 112);
        if (merchant == null) {
            return 0;
        }
        PirateShipEntity pirate = spawnPirateRaid(world, merchant);
        return pirate == null ? 1 : 2;
    }

    private static MerchantShipEntity spawnMerchantShip(ServerWorld world, BlockPos origin, int radius) {
        BlockPos position = findWater(world, origin, radius);
        if (position == null) {
            return null;
        }
        MerchantShipEntity merchant = ModEntities.MERCHANT_SHIP.create(world);
        if (merchant == null) {
            return null;
        }
        merchant.refreshPositionAndAngles(position.getX() + 0.5, position.getY(),
                position.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
        if (!world.spawnEntity(merchant)) {
            return null;
        }
        // The crew rides her deck from the first tick.
        spawnCrew(world, merchant, Archetype.KNIGHT,
                new SettlementRole[]{SettlementRole.MERCHANT, SettlementRole.JEWELER,
                        SettlementRole.SAILOR, SettlementRole.SAILOR});
        return merchant;
    }

    private static PirateShipEntity spawnPirateRaid(ServerWorld world, MerchantShipEntity merchant) {
        BlockPos position = findWater(world, merchant.getBlockPos().add(32, 0, 0), 40);
        if (position == null) {
            return null;
        }
        // Three raids in ten send the actual flagship: a galleon with a
        // full gun deck, tough enough to trade broadsides with anyone.
        boolean flagship = world.random.nextFloat() < 0.30f;
        PirateShipEntity pirate = flagship
                ? ModEntities.GALLEON.create(world)
                : ModEntities.PIRATE_SHIP.create(world);
        if (pirate == null) {
            return null;
        }
        pirate.refreshPositionAndAngles(position.getX() + 0.5, position.getY(),
                position.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
        pirate.setTargetShip(merchant);
        if (!world.spawnEntity(pirate)) {
            return null;
        }
        merchant.markUnderAttack();
        SettlementRole[] roles = flagship
                ? new SettlementRole[]{SettlementRole.CAPTAIN, SettlementRole.GUNNER,
                        SettlementRole.GUNNER, SettlementRole.QUARTERMASTER,
                        SettlementRole.SAILOR, SettlementRole.SAILOR}
                : new SettlementRole[]{SettlementRole.CAPTAIN, SettlementRole.GUNNER,
                        SettlementRole.QUARTERMASTER, SettlementRole.SAILOR};
        spawnCrew(world, pirate, Archetype.PIRATE, roles);
        return pirate;
    }

    /** Spawns a crew for {@code ship} and seats them on her deck immediately. */
    private static void spawnCrew(ServerWorld world, SailingShipEntity ship, Archetype archetype,
                                  SettlementRole[] roles) {
        BlockPos shipCenter = ship.getBlockPos();
        List<SurvivorEntity> crew = new ArrayList<>(roles.length);
        for (int i = 0; i < roles.length; i++) {
            SurvivorEntity member = ModEntities.SURVIVOR.create(world);
            if (member == null) {
                continue;
            }
            member.refreshPositionAndAngles(shipCenter.getX() + 0.5, shipCenter.getY() + 1.0,
                    shipCenter.getZ() + 0.5, world.random.nextFloat() * 360.0f, 0.0f);
            member.setArchetype(archetype);
            member.assignWorker(shipCenter, null, archetype.faction(), roles[i]);
            member.setCustomName(Text.literal(roles[i].displayName() + " · " + archetype.title()));
            if (i == 0 && world.random.nextFloat() < 0.25f) {
                makeChampion(world, member);
            }
            if (!world.spawnEntity(member)) {
                continue;
            }
            crew.add(member);
        }
        ship.boardCrew(crew);
    }

    public static BlockPos findWater(ServerWorld world, BlockPos origin, int radius) {
        for (int attempt = 0; attempt < 36; attempt++) {
            int x = origin.getX() + world.random.nextInt(radius * 2 + 1) - radius;
            int z = origin.getZ() + world.random.nextInt(radius * 2 + 1) - radius;
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, origin.getY(), z));
            for (int y = top.getY(); y >= Math.max(world.getBottomY() + 1, top.getY() - 8); y--) {
                BlockPos water = new BlockPos(x, y, z);
                if (world.getFluidState(water).isIn(FluidTags.WATER)
                        && world.getBlockState(water.up()).isAir()) {
                    return water;
                }
            }
        }
        return null;
    }

    private static BlockPos surfacePosition(ServerWorld world, BlockPos requested) {
        BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, requested);
        if (surface.getY() >= world.getBottomY() && surface.getY() < world.getTopY()) {
            return surface;
        }
        return requested;
    }

    private static void spawnEncounter(ServerWorld world, ServerPlayerEntity player) {
        BlockPos requested = player.getBlockPos().add(12 + world.random.nextInt(12), 0, 12 + world.random.nextInt(12));
        if (!world.getChunkManager().isChunkLoaded(requested.getX() >> 4, requested.getZ() >> 4)) {
            return;
        }
        BlockPos center = surfacePosition(world, requested);
        // Encounter mix: a Marauder warband hunting throats, a Hearthfolk
        // caravan trading its way across the realm, or plain frontier wanderers.
        Archetype culture;
        int roll = world.random.nextInt(5);
        if (roll == 0) {
            culture = Archetype.MARAUDER;
        } else if (roll == 1) {
            culture = Archetype.HEARTHFOLK;
        } else {
            culture = Archetype.values()[world.random.nextInt(Archetype.values().length)];
        }
        int band = culture == Archetype.MARAUDER ? 3 : 3;
        for (int i = 0; i < band; i++) {
            SurvivorEntity survivor = ModEntities.SURVIVOR.create(world);
            if (survivor == null) {
                continue;
            }
            survivor.refreshPositionAndAngles(center.add(i * 2 - 2, 0, i % 2 * 2), world.random.nextFloat() * 360.0f, 0.0f);
            survivor.setArchetype(culture);
            world.spawnEntity(survivor);
        }
    }
}
