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
 * Everything the haunting remembers: whether the chase has been acknowledged,
 * which act it is in, which portal the player slipped through, where the
 * hunter's bed is, how many times he has killed you, and his packed inventory
 * while he is dead and counting down to a respawn.
 */
public final class ManhuntState extends PersistentState {

    /** Acts of the hunt, in order of escalation. */
    public static final int ACT_WATCH = 0;
    public static final int ACT_STALK = 1;
    public static final int ACT_HUNT = 2;

    public boolean ignited;
    public int act;
    public long ignitedTick;
    public int contactTicks;
    public int unseenTicks;
    public int farTicks;
    public int playerHits;
    public int deathTally;
    public int night;
    public int lastCount;
    public int mirrorNight;
    public int signsTick;
    public int truceNight;
    public boolean chatReplyUsed;
    public long chatReplyTick;
    public long deathTick;
    public BlockPos deathSite;
    public Identifier deathSiteDim;
    public UUID hunterUuid;
    /** Dimension the player is currently in. */
    public Identifier playerDimension;
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
        state.ignited = nbt.getBoolean("ignited");
        state.act = nbt.getInt("act");
        state.ignitedTick = nbt.getLong("ignitedTick");
        state.contactTicks = nbt.getInt("contactTicks");
        state.unseenTicks = nbt.getInt("unseenTicks");
        state.farTicks = nbt.getInt("farTicks");
        state.playerHits = nbt.getInt("playerHits");
        state.deathTally = nbt.getInt("deathTally");
        state.night = nbt.getInt("night");
        state.lastCount = nbt.getInt("lastCount");
        state.mirrorNight = nbt.getInt("mirrorNight");
        state.signsTick = nbt.getInt("signsTick");
        state.truceNight = nbt.getInt("truceNight");
        state.chatReplyUsed = nbt.getBoolean("chatReplyUsed");
        state.chatReplyTick = nbt.getLong("chatReplyTick");
        state.deathTick = nbt.getLong("deathTick");
        if (nbt.contains("deathSite")) {
            state.deathSite = BlockPos.fromLong(nbt.getLong("deathSite"));
        }
        if (nbt.contains("deathSiteDim")) {
            state.deathSiteDim = Identifier.of(nbt.getString("deathSiteDim"));
        }
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
        nbt.putBoolean("ignited", ignited);
        nbt.putInt("act", act);
        nbt.putLong("ignitedTick", ignitedTick);
        nbt.putInt("contactTicks", contactTicks);
        nbt.putInt("unseenTicks", unseenTicks);
        nbt.putInt("farTicks", farTicks);
        nbt.putInt("playerHits", playerHits);
        nbt.putInt("deathTally", deathTally);
        nbt.putInt("night", night);
        nbt.putInt("lastCount", lastCount);
        nbt.putInt("mirrorNight", mirrorNight);
        nbt.putInt("signsTick", signsTick);
        nbt.putInt("truceNight", truceNight);
        nbt.putBoolean("chatReplyUsed", chatReplyUsed);
        nbt.putLong("chatReplyTick", chatReplyTick);
        nbt.putLong("deathTick", deathTick);
        if (deathSite != null) {
            nbt.putLong("deathSite", deathSite.asLong());
        }
        if (deathSiteDim != null) {
            nbt.putString("deathSiteDim", deathSiteDim.toString());
        }
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
        return Identifier.of("minecraft", "overworld");
    }

    public static Identifier nether() {
        return Identifier.of("minecraft", "the_nether");
    }

    public static Identifier end() {
        return Identifier.of("minecraft", "the_end");
    }
}
