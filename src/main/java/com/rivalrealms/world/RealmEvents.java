package com.rivalrealms.world;

import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/** Handles low-frequency living-world encounters, guards, diplomacy, and raids. */
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
            ensureGuards(world, state, base);
            if (world.getTime() % 1200L == 0 && world.random.nextFloat() < 0.08f) {
                tryStartRaid(world, state, base);
            }
        }
    }

    private static void ensureGuards(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        BlockPos center = base.center();
        double radius = base.radius();
        Box search = new Box(center).expand(radius);
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
