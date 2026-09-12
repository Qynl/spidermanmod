package com.rivalrealms.world;

import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

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
                tickSettlements(world);
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

    private static void tickSettlements(ServerWorld world) {
        RealmState state = RealmState.get(world);
        for (RealmState.BaseRecord base : new ArrayList<>(state.bases())) {
            ensureGuards(world, base);
            ensureWorkforce(world, base);
            progressSettlement(world, state, base);
            if (world.getTime() % 1200L == 0 && world.random.nextFloat() < 0.08f) {
                tryStartRaid(world, state, base);
            }
        }
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
        BlockPos spawn = center.add(world.random.nextInt(9) - 4, 0, world.random.nextInt(9) - 4);
        guard.refreshPositionAndAngles(spawn, world.random.nextFloat() * 360.0f, 0.0f);
        guard.setArchetype(culture);
        guard.assignGuard(center, base.owner(), base.faction());
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
        int desired = Math.min(6, base.level() + 1);
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
        BlockPos spawn = center.add(world.random.nextInt(13) - 6, 0, world.random.nextInt(13) - 6);
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
        if (!hasRole(workers, SettlementRole.BUILDER)) {
            return SettlementRole.BUILDER;
        }
        if (!hasRole(workers, SettlementRole.FARMER)) {
            return SettlementRole.FARMER;
        }
        SettlementRole cultureRole = switch (style) {
            case PIRATE -> SettlementRole.TRADER;
            case WESTERN -> SettlementRole.BLACKSMITH;
            case SKY -> SettlementRole.SCOUT;
            case KNIGHT, CUSTOM -> SettlementRole.BLACKSMITH;
        };
        if (!hasRole(workers, cultureRole)) {
            return cultureRole;
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
        int traders = 0;
        int blacksmiths = 0;
        for (Entity entity : world.getOtherEntities(null, search,
                candidate -> candidate instanceof SurvivorEntity worker
                        && worker.isSettlementWorker()
                        && base.faction().equalsIgnoreCase(worker.effectiveFaction()))) {
            SurvivorEntity worker = (SurvivorEntity) entity;
            switch (worker.settlementRole()) {
                case FARMER -> farmers++;
                case BUILDER -> builders++;
                case TRADER -> traders++;
                case BLACKSMITH -> blacksmiths++;
                default -> {
                }
            }
        }

        int food = farmers * 2 + traders;
        int materials = blacksmiths * 2 + traders;
        int work = builders * 2;
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

        Archetype attacker = null;
        for (Archetype candidate : Archetype.values()) {
            if (state.isHostile(base.faction(), candidate.faction())) {
                attacker = candidate;
                break;
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
            BlockPos spawn = center.add(24 + world.random.nextInt(9), 0, world.random.nextInt(17) - 8);
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
            owner.sendMessage(Text.literal("Raid incoming at " + base.name() + ": " + attacker.title() + " forces are approaching."), false);
        }
    }

    private static void spawnEncounter(ServerWorld world, ServerPlayerEntity player) {
        BlockPos center = player.getBlockPos().add(12 + world.random.nextInt(12), 0, 12 + world.random.nextInt(12));
        Archetype culture = Archetype.values()[world.random.nextInt(Archetype.values().length)];
        for (int i = 0; i < 3; i++) {
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
