package com.manhunt.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the hunt remembers across restarts: whether the chase is live,
 * which portal the player last slipped through (per dimension), where the
 * hunter's bed is, and his packed inventory while he is dead and counting
 * down to a respawn.
 */
public final class ManhuntState extends PersistentState {

    private static final Identifier DIM_OVERWORLD = Identifier.of("minecraft", "overworld");
    private static final Identifier DIM_NETHER = Identifier.of("minecraft", "the_nether");
    private static final Identifier DIM_END = Identifier.of("minecraft", "the_end");

    public boolean started;
    public UUID hunterUuid;
    /** Dimension the player is currently in. */
    public Identifier playerDimension;
    /** Dimension the hunter should respawn in, {@code null} for world spawn. */
    public Identifier respawnDimension;
    public BlockPos respawnPos;
    public int respawnTimer;
    public NbtList packedInventory = new NbtList();
    /** Last position the player was seen at, per dimension. */
    public final Map<Identifier, BlockPos> lastSeen = new HashMap<>();
    /** The portal the player used to leave each dimension, per dimension. */
    public final Map<Identifier, BlockPos> portalMarks = new HashMap<>();
    /** Which dimension each marked portal leads to. */
    public final Map<Identifier, Identifier> portalTargets = new HashMap<>();

    public static ManhuntState get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(
                new Type<>(ManhuntState::new,
                        (nbt, registries) -> read(nbt),
                        null),
                "manhunt");
    }

    private static ManhuntState read(NbtCompound nbt) {
        ManhuntState state = new ManhuntState();
        state.started = nbt.getBoolean("started");
        if (nbt.containsUuid("hunter")) {
            state.hunterUuid = nbt.getUuid("hunter");
        }
        if (nbt.contains("respawnDim")) {
            state.respawnDimension = Identifier.of(nbt.getString("respawnDim"));
        }
        if (nbt.contains("respawnPos")) {
            state.respawnPos = BlockPos.fromLong(nbt.getLong("respawnPos"));
        }
        state.respawnTimer = nbt.getInt("respawnTimer");
        state.packedInventory = nbt.getList("packedInventory", 10);
        NbtCompound seen = nbt.getCompound("lastSeen");
        for (String key : seen.getKeys()) {
            state.lastSeen.put(Identifier.of(key), BlockPos.fromLong(seen.getLong(key)));
        }
        NbtCompound marks = nbt.getCompound("portalMarks");
        for (String key : marks.getKeys()) {
            state.portalMarks.put(Identifier.of(key), BlockPos.fromLong(marks.getLong(key)));
        }
        NbtCompound targets = nbt.getCompound("portalTargets");
        for (String key : targets.getKeys()) {
            state.portalTargets.put(Identifier.of(key), Identifier.of(targets.getString(key)));
        }
        if (nbt.contains("playerDimension")) {
            state.playerDimension = Identifier.of(nbt.getString("playerDimension"));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putBoolean("started", started);
        if (hunterUuid != null) {
            nbt.putUuid("hunter", hunterUuid);
        }
        if (respawnDimension != null) {
            nbt.putString("respawnDim", respawnDimension.toString());
        }
        if (respawnPos != null) {
            nbt.putLong("respawnPos", respawnPos.asLong());
        }
        nbt.putInt("respawnTimer", respawnTimer);
        nbt.put("packedInventory", packedInventory);
        NbtCompound seen = new NbtCompound();
        lastSeen.forEach((dim, pos) -> seen.putLong(dim.toString(), pos.asLong()));
        nbt.put("lastSeen", seen);
        NbtCompound marks = new NbtCompound();
        portalMarks.forEach((dim, pos) -> marks.putLong(dim.toString(), pos.asLong()));
        nbt.put("portalMarks", marks);
        NbtCompound targets = new NbtCompound();
        portalTargets.forEach((dim, target) -> targets.putString(dim.toString(), target.toString()));
        nbt.put("portalTargets", targets);
        if (playerDimension != null) {
            nbt.putString("playerDimension", playerDimension.toString());
        }
        return nbt;
    }

    public static Identifier overworld() {
        return DIM_OVERWORLD;
    }

    public static Identifier nether() {
        return DIM_NETHER;
    }

    public static Identifier end() {
        return DIM_END;
    }
}
