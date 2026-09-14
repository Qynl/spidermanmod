package com.manhunt;

import com.manhunt.entity.HunterEntity;
import com.manhunt.world.ManhuntState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MANHUNT: The Resident. No commands, no tabs, no announcements - one entity
 * and a director that keeps the haunting honest: it remembers which portal
 * the player slipped through, wakes the hunter at his bed like any player
 * would wake, and, if you never make the mistake of looking at him, ignites
 * the hunt on the third dawn anyway.
 */
public final class Manhunt implements ModInitializer {

    public static final String MOD_ID = "manhunt";
    public static final Logger LOG = LoggerFactory.getLogger("Manhunt");

    public static final EntityType<HunterEntity> HUNTER = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "hunter"),
            EntityType.Builder.<HunterEntity>create(HunterEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .maxTrackingRange(10)
                    .trackingTickInterval(2)
                    .build("hunter"));

    /** Runtime-only memory of which dimension each player was in last tick. */
    private static final Map<UUID, Identifier> PREVIOUS_DIMENSION = new HashMap<>();
    private static final Map<UUID, Identifier> HUNTER_DIMENSION = new HashMap<>();

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(HUNTER, PlayerEntity.createPlayerAttributes());
        ManhuntSounds.boot();
        ServerTickEvents.END_SERVER_TICK.register(Manhunt::direct);
        LOG.info("Manhunt: he is already in your world.");
    }

    // -------------------------------------------------------------- director --

    private static void direct(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        ManhuntState state = ManhuntState.get(overworld);
        trackPlayers(server, state);
        HunterEntity hunter = hunter(server, state);
        if (hunter == null && state.respawnTimer <= 0) {
            arrive(server, state);
            return;
        }
        if (hunter != null) {
            trackHunterDimension(hunter, state);
            if (!state.ignited && overworld.getTimeOfDay() > 72000L) {
                hunter.ignite(overworld, state, null);
            }
        }
        if (state.respawnTimer > 0) {
            state.respawnTimer--;
            if (state.respawnTimer == 0) {
                state.markDirty();
            }
        }
    }

    /** Always knows where you are: poll positions, mark portals on dimension change. */
    private static void trackPlayers(MinecraftServer server, ManhuntState state) {
        for (ServerWorld world : server.getWorlds()) {
            for (PlayerEntity player : world.getPlayers()) {
                Identifier dimension = world.getRegistryKey().getValue();
                Identifier previous = PREVIOUS_DIMENSION.get(player.getUuid());
                BlockPos here = player.getBlockPos();
                state.playerDimension = dimension;
                if (previous == null) {
                    PREVIOUS_DIMENSION.put(player.getUuid(), dimension);
                } else if (!previous.equals(dimension)) {
                    BlockPos portal = state.lastSeen.get(previous);
                    if (portal != null) {
                        state.portalMarks.put(previous, portal);
                        state.portalTargets.put(previous, dimension);
                    }
                    PREVIOUS_DIMENSION.put(player.getUuid(), dimension);
                    state.markDirty();
                }
                state.lastSeen.put(dimension, here);
            }
        }
    }

    private static void trackHunterDimension(HunterEntity hunter, ManhuntState state) {
        Identifier dimension = hunter.getWorld().getRegistryKey().getValue();
        Identifier previous = HUNTER_DIMENSION.get(hunter.getUuid());
        if (previous == null || previous.equals(dimension)) {
            HUNTER_DIMENSION.put(hunter.getUuid(), dimension);
            return;
        }
        HUNTER_DIMENSION.put(hunter.getUuid(), dimension);
        if (ManhuntState.nether().equals(dimension)) {
            com.manhunt.world.HunterVoice.speak((ServerWorld) hunter.getWorld(), hunter, "nether");
        }
        if (ManhuntState.end().equals(dimension)) {
            com.manhunt.world.HunterVoice.speak((ServerWorld) hunter.getWorld(), hunter, "end_wait");
        }
    }

    private static HunterEntity hunter(MinecraftServer server, ManhuntState state) {
        if (state.hunterUuid == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(state.hunterUuid);
            if (entity instanceof HunterEntity hunter) {
                return hunter;
            }
        }
        return null;
    }

    /** He died like a player; he comes back like one, at his bed. */
    public static void onHunterDeath(HunterEntity hunter) {
        if (!(hunter.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        ManhuntState state = ManhuntState.get(serverWorld);
        NbtList packed = new NbtList();
        hunter.getInventory().writeNbt(packed);
        state.packedInventory = packed;
        state.respawnTimer = 300;
        state.hunterUuid = null;
        state.markDirty();
    }

    private static void arrive(MinecraftServer server, ManhuntState state) {
        ServerWorld world = server.getOverworld();
        BlockPos pos = null;
        if (state.respawnDimension != null && state.respawnPos != null) {
            ServerWorld bed = server.getWorld(
                    RegistryKey.of(RegistryKeys.WORLD, state.respawnDimension));
            if (bed != null) {
                world = bed;
                pos = state.respawnPos;
            }
        }
        if (pos == null) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double range = 64 + world.random.nextDouble() * 96;
            pos = world.getSpawnPos().add((int) (Math.cos(angle) * range), 0,
                    (int) (Math.sin(angle) * range));
            pos = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);
        }
        HunterEntity hunter = HUNTER.create(world);
        if (hunter == null) {
            return;
        }
        hunter.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                world.random.nextFloat() * 360, 0);
        hunter.getInventory().readNbt(state.packedInventory);
        state.packedInventory = new NbtList();
        world.spawnEntity(hunter);
        state.hunterUuid = hunter.getUuid();
        state.markDirty();
        LOG.info("The Hunter walks at {} in {}.", pos.toShortString(),
                world.getRegistryKey().getValue());
    }
}
