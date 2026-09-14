package com.manhunt;

import com.manhunt.entity.HunterEntity;
import com.manhunt.world.ManhuntState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
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
    private static final Map<UUID, Boolean> WAS_SLEEPING = new HashMap<>();
    private static UUID chatReplyTo;

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(HUNTER, PlayerEntity.createPlayerAttributes());
        ManhuntSounds.boot();
        ServerTickEvents.END_SERVER_TICK.register(Manhunt::direct);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && entity.getWorld() instanceof ServerWorld world) {
                ManhuntState state = ManhuntState.get(world.getServer().getOverworld());
                state.deathSite = player.getBlockPos();
                state.deathSiteDim = world.getRegistryKey().getValue();
                state.deathTick = world.getTime();
                state.deathTally++;
                state.markDirty();
            }
        });
        ServerMessageCallback.EVENT.register((message, sender, params) -> {
            MinecraftServer server = sender.getServer();
            ManhuntState state = ManhuntState.get(server.getOverworld());
            HunterEntity hunter = hunter(server, state);
            if (state.chatReplyUsed || !state.ignited || hunter == null) {
                return;
            }
            if (hunter.getWorld() != sender.getWorld()
                    || hunter.squaredDistanceTo(sender) > 4096) {
                return;
            }
            chatReplyTo = sender.getUuid();
            state.chatReplyTick = sender.getWorld().getTime() + 400
                    + sender.getWorld().random.nextInt(400);
            state.markDirty();
        });
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
                boolean sleeping = player.isSleeping();
                Boolean was = WAS_SLEEPING.get(player.getUuid());
                if (was != null && was && !sleeping && player instanceof ServerPlayerEntity serverPlayer) {
                    onWake(serverPlayer, state);
                }
                WAS_SLEEPING.put(player.getUuid(), sleeping);
            }
        }
        if (state.chatReplyTick > 0 && overworld.getTime() >= state.chatReplyTick) {
            state.chatReplyTick = 0;
            state.chatReplyUsed = true;
            state.markDirty();
            if (chatReplyTo != null) {
                PlayerEntity target = overworld.getServer().getPlayerManager().getPlayer(chatReplyTo);
                if (target instanceof ServerPlayerEntity serverPlayer) {
                    com.manhunt.world.HunterVoice.whisper(overworld, serverPlayer,
                            "you talk too much, " + serverPlayer.getName().getString() + ".");
                }
            }
        }
    }

    /** Waking up is the worst time to notice him at the foot of the bed. */
    private static void onWake(ServerPlayerEntity player, ManhuntState state) {
        if (!state.ignited || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        ServerWorld overworld = player.getServer().getOverworld();
        HunterEntity hunter = hunter(player.getServer(), state);
        if (hunter == null || hunter.getWorld() != world
                || player.squaredDistanceTo(hunter) < 400) {
            return;
        }
        BlockPos bed = player.getBlockPos();
        for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos spot = bed.offset(direction);
            if (world.getBlockState(spot).isAir() && world.getBlockState(spot.down()).isSolid()) {
                hunter.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY() + 1,
                        spot.getZ() + 0.5, hunter.getYaw(), 0);
                com.manhunt.world.HunterVoice.speak(world, hunter,
                        world.random.nextBoolean() ? "wake_a" : "wake_b");
                break;
            }
        }
        if (world.random.nextInt(100) < 30) {
            falseWake(world, bed, player, state);
        }
    }

    /** The false wake: green torches, and a paper you did not craft. */
    private static void falseWake(ServerWorld world, BlockPos bed, ServerPlayerEntity player,
                                  ManhuntState state) {
        for (BlockPos pos : BlockPos.iterate(bed.add(-6, -3, -6), bed.add(6, 3, 6))) {
            if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.TORCH)) {
                world.setBlockState(pos, net.minecraft.block.Blocks.SOUL_TORCH.getDefaultState());
            }
        }
        player.getInventory().insertStack(tallyPaper(state));
    }

    /** Renamed paper, lore of tally marks: he keeps the count of your deaths. */
    private static ItemStack tallyPaper(ManhuntState state) {
        ItemStack paper = new ItemStack(net.minecraft.item.Items.PAPER);
        paper.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("soon").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        int deaths = Math.max(1, state.deathTally);
        StringBuilder tally = new StringBuilder();
        for (int group = 0; group < deaths / 5; group++) {
            tally.append("\u2016\u2016\u2016\u2016/ ");
        }
        tally.append("\u2016".repeat(deaths % 5));
        paper.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal(tally.toString().trim()).formatted(Formatting.DARK_RED),
                Text.literal("he keeps the count").formatted(Formatting.DARK_GRAY, Formatting.ITALIC))));
        return paper;
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
