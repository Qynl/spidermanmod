package com.manhunt;

import com.manhunt.command.ManhuntCommands;
import com.manhunt.entity.HunterEntity;
import com.manhunt.world.ManhuntState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
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
 * Manhunt: another player lives in your world.
 *
 * <p>The mod owns exactly one entity type and a lot of intent: the hunter is a
 * real {@link PlayerEntity} driven by a survival state machine, and this class
 * is the director that keeps the chase honest - remembering which portal the
 * player slipped through, and respawning the hunter at his bed like any player
 * would wake up.
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

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(HUNTER, PlayerEntity.createPlayerAttributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ManhuntCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(Manhunt::direct);
        LOG.info("Manhunt: the hunter is in your world.");
    }

    // -------------------------------------------------------------- director --

    private static void direct(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        ManhuntState state = ManhuntState.get(overworld);
        trackPlayers(server, state);
        if (state.respawnTimer > 0) {
            state.respawnTimer--;
            if (state.respawnTimer == 0) {
                state.markDirty();
            }
        }
        if (state.started && state.hunterUuid == null && state.respawnTimer <= 0) {
            respawnHunter(server, state);
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

    /** The hunter died like a player; he comes back like one, at his bed. */
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

    private static void respawnHunter(MinecraftServer server, ManhuntState state) {
        ServerWorld world = overworld(server);
        BlockPos pos = null;
        if (state.respawnDimension != null && state.respawnPos != null) {
            ServerWorld bed = server.getWorld(
                    net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, state.respawnDimension));
            if (bed != null) {
                world = bed;
                pos = state.respawnPos;
            }
        }
        if (pos == null) {
            pos = world.getSpawnPos();
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
        LOG.info("The Hunter walks again at {} in {}.", pos.toShortString(),
                world.getRegistryKey().getValue());
    }

    /** Summon a fresh hunter near a position (the /manhunt spawn path). */
    public static HunterEntity summon(ServerWorld world, BlockPos pos) {
        HunterEntity hunter = HUNTER.create(world);
        if (hunter == null) {
            return null;
        }
        hunter.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                world.random.nextFloat() * 360, 0);
        world.spawnEntity(hunter);
        ManhuntState state = ManhuntState.get(world);
        state.hunterUuid = hunter.getUuid();
        state.markDirty();
        return hunter;
    }

    public static HunterEntity currentHunter(MinecraftServer server) {
        ManhuntState state = ManhuntState.get(overworld(server));
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

    private static ServerWorld overworld(MinecraftServer server) {
        return server.getOverworld();
    }
}
