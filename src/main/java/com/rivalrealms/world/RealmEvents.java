package com.rivalrealms.world;

import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Handles low-frequency living-world encounters without a permanent mob flood. */
public final class RealmEvents {
    private RealmEvents() {
    }

    public static void onServerStarted(MinecraftServer server) {
        // A lifecycle hook is intentionally kept even though the first release
        // does not need a global registry: it gives future settlement state a
        // stable place to initialize and makes hot reloads predictable.
    }

    public static void onServerTick(MinecraftServer server) {
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
            if (!nearby.isEmpty()) {
                continue;
            }
            spawnEncounter(world, player);
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
