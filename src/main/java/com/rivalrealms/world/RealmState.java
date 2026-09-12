package com.rivalrealms.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * World-persistent diplomacy and settlement data. This is deliberately kept
 * server-side so a claimed base survives restarts and works on dedicated
 * servers without a client-side capability or a fragile static map.
 */
public final class RealmState extends PersistentState {
    private static final String ID = "rivalrealms_realms";
    private static final PersistentState.Type<RealmState> TYPE = new PersistentState.Type<>(
            RealmState::new, RealmState::fromNbt, DataFixTypes.LEVEL);

    private final List<BaseRecord> bases = new ArrayList<>();
    private final Map<String, Integer> relations = new HashMap<>();
    private final Map<String, Integer> reputations = new HashMap<>();

    public static RealmState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    private static RealmState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        RealmState state = new RealmState();
        NbtList savedBases = nbt.getList("Bases", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < savedBases.size(); i++) {
            NbtCompound base = savedBases.getCompound(i);
            if (!base.containsUuid("Owner")) {
                continue;
            }
            state.bases.add(new BaseRecord(
                    base.getLong("Center"),
                    base.getUuid("Owner"),
                    base.getString("Faction"),
                    base.getString("Style"),
                    base.getString("Name"),
                    Math.max(1, base.getInt("Level")),
                    base.getLong("LastRaid")));
        }

        NbtList savedRelations = nbt.getList("Relations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < savedRelations.size(); i++) {
            NbtCompound relation = savedRelations.getCompound(i);
            state.relations.put(relationKey(relation.getString("A"), relation.getString("B")),
                    clampRelation(relation.getInt("Value")));
        }

        NbtList savedReputations = nbt.getList("Reputations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < savedReputations.size(); i++) {
            NbtCompound reputation = savedReputations.getCompound(i);
            if (reputation.containsUuid("Player")) {
                state.reputations.put(reputationKey(reputation.getUuid("Player"), reputation.getString("Faction")),
                        clampRelation(reputation.getInt("Value")));
            }
        }
        return state;
    }

    public BaseRecord claimBase(BlockPos center, UUID owner, BuildStyle style) {
        return claimBase(center, owner, style.faction(), style.id(), style.displayName());
    }

    public BaseRecord claimCustomBase(BlockPos center, UUID owner, String faction, String name) {
        return claimBase(center, owner, faction, "custom", name);
    }

    private BaseRecord claimBase(BlockPos center, UUID owner, String faction, String style, String name) {
        for (BaseRecord existing : bases) {
            double minimum = existing.radius() + 24.0;
            if (existing.center().getSquaredDistance(center) <= minimum * minimum) {
                return null;
            }
        }
        BaseRecord record = new BaseRecord(center.asLong(), owner, faction, style, name, 1, 0L);
        bases.add(record);
        markDirty();
        return record;
    }

    public BaseRecord findBase(BlockPos position) {
        for (BaseRecord base : bases) {
            if (base.contains(position)) {
                return base;
            }
        }
        return null;
    }

    public List<BaseRecord> bases() {
        return Collections.unmodifiableList(bases);
    }

    public int getRelation(String first, String second) {
        String a = normalize(first);
        String b = normalize(second);
        if (a.equals(b)) {
            return 100;
        }
        return relations.getOrDefault(relationKey(a, b), defaultRelation(a, b));
    }

    public boolean isHostile(String first, String second) {
        return !normalize(first).equals(normalize(second)) && getRelation(first, second) <= -50;
    }

    public void setRelation(String first, String second, int value) {
        String a = normalize(first);
        String b = normalize(second);
        if (a.equals(b)) {
            return;
        }
        relations.put(relationKey(a, b), clampRelation(value));
        markDirty();
    }

    public int getReputation(UUID player, String faction) {
        return reputations.getOrDefault(reputationKey(player, faction), 0);
    }

    public void adjustReputation(UUID player, String faction, int amount) {
        String key = reputationKey(player, faction);
        reputations.put(key, clampRelation(reputations.getOrDefault(key, 0) + amount));
        markDirty();
    }

    public void upgrade(BaseRecord base) {
        base.level = Math.min(5, base.level + 1);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList savedBases = new NbtList();
        for (BaseRecord base : bases) {
            NbtCompound entry = new NbtCompound();
            entry.putLong("Center", base.centerLong);
            entry.putUuid("Owner", base.owner);
            entry.putString("Faction", base.faction);
            entry.putString("Style", base.styleId);
            entry.putString("Name", base.name);
            entry.putInt("Level", base.level);
            entry.putLong("LastRaid", base.lastRaid);
            savedBases.add(entry);
        }
        nbt.put("Bases", savedBases);

        NbtList savedRelations = new NbtList();
        for (Map.Entry<String, Integer> entry : relations.entrySet()) {
            String[] factions = entry.getKey().split("\\|", 2);
            if (factions.length != 2) {
                continue;
            }
            NbtCompound relation = new NbtCompound();
            relation.putString("A", factions[0]);
            relation.putString("B", factions[1]);
            relation.putInt("Value", entry.getValue());
            savedRelations.add(relation);
        }
        nbt.put("Relations", savedRelations);

        NbtList savedReputations = new NbtList();
        for (Map.Entry<String, Integer> entry : reputations.entrySet()) {
            String[] values = entry.getKey().split("\\|", 2);
            if (values.length != 2) {
                continue;
            }
            try {
                NbtCompound reputation = new NbtCompound();
                reputation.putUuid("Player", UUID.fromString(values[0]));
                reputation.putString("Faction", values[1]);
                reputation.putInt("Value", entry.getValue());
                savedReputations.add(reputation);
            } catch (IllegalArgumentException ignored) {
                // Ignore one corrupt reputation rather than losing every base.
            }
        }
        nbt.put("Reputations", savedReputations);
        return nbt;
    }

    private static String normalize(String value) {
        return value == null ? "independent" : value.toLowerCase(Locale.ROOT);
    }

    private static String relationKey(String first, String second) {
        String a = normalize(first);
        String b = normalize(second);
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static String reputationKey(UUID player, String faction) {
        return player + "|" + normalize(faction);
    }

    private static int clampRelation(int value) {
        return Math.max(-100, Math.min(100, value));
    }

    private static int defaultRelation(String first, String second) {
        String key = relationKey(first, second);
        return switch (key) {
            case "crownlands|freebooters" -> -65;
            case "dustwalkers|freebooters" -> -55;
            case "crownlands|dustwalkers" -> -45;
            case "freebooters|skybound" -> -30;
            case "crownlands|skybound", "dustwalkers|skybound" -> 20;
            default -> -20;
        };
    }

    public static final class BaseRecord {
        private final long centerLong;
        private final UUID owner;
        private final String faction;
        private final String styleId;
        private final String name;
        private int level;
        private long lastRaid;

        private BaseRecord(long centerLong, UUID owner, String faction, String styleId,
                           String name, int level, long lastRaid) {
            this.centerLong = centerLong;
            this.owner = owner;
            this.faction = faction;
            this.styleId = styleId;
            this.name = name;
            this.level = level;
            this.lastRaid = lastRaid;
        }

        public BlockPos center() {
            return BlockPos.fromLong(centerLong);
        }

        public UUID owner() {
            return owner;
        }

        public String faction() {
            return faction;
        }

        public String styleId() {
            return styleId;
        }

        public BuildStyle style() {
            return BuildStyle.fromId(styleId);
        }

        public String name() {
            return name;
        }

        public int level() {
            return level;
        }

        public long lastRaid() {
            return lastRaid;
        }

        public void setLastRaid(long lastRaid) {
            this.lastRaid = lastRaid;
        }

        public int radius() {
            return 26 + (level - 1) * 5;
        }

        public boolean contains(BlockPos position) {
            double radius = radius();
            return center().getSquaredDistance(position) <= radius * radius;
        }
    }
}
