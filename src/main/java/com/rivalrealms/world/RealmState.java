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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-persistent diplomacy and settlement data. This is deliberately kept
 * server-side so a claimed base survives restarts and works on dedicated
 * servers without a client-side capability or a fragile static map.
 */
public final class RealmState extends PersistentState {
    private static final String ID = "rivalrealms_realms";
    private static final int MAX_BASES = 128;
    private static final int MAX_RELATIONS = 128;
    private static final int MAX_REPUTATIONS = 2048;
    private static final int MAX_GENERATED_SITES = 256;
    private static final PersistentState.Type<RealmState> TYPE = new PersistentState.Type<>(
            RealmState::new, RealmState::fromNbt, DataFixTypes.LEVEL);

    private static final int MAX_CHRONICLE = 200;

    private final List<BaseRecord> bases = new ArrayList<>();
    private final Map<String, Integer> relations = new HashMap<>();
    private final Map<String, Integer> reputations = new HashMap<>();
    private final Set<Long> generatedSites = new HashSet<>();
    private final Map<String, Integer> wealth = new HashMap<>();
    private final List<ChronicleEntry> chronicle = new ArrayList<>();

    public static RealmState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    private static RealmState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        RealmState state = new RealmState();
        NbtList savedBases = nbt.getList("Bases", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(savedBases.size(), MAX_BASES); i++) {
            NbtCompound base = savedBases.getCompound(i);
            if (!base.containsUuid("Owner")) {
                continue;
            }
            state.bases.add(new BaseRecord(
                    base.getLong("Center"),
                    base.getUuid("Owner"),
                    safeText(base.getString("Faction"), "Independent"),
                    safeText(base.getString("Style"), "custom"),
                    safeText(base.getString("Name"), "Claimed Settlement"),
                    Math.min(5, Math.max(1, base.getInt("Level"))),
                    Math.max(0L, base.getLong("LastRaid")),
                    Math.min(999, Math.max(0, base.getInt("Food"))),
                    Math.min(999, Math.max(0, base.getInt("Materials"))),
                    Math.min(999, Math.max(0, base.getInt("Work"))),
                    Math.max(0L, base.getLong("LastExpansion"))));
            BaseRecord saved = state.bases.get(state.bases.size() - 1);
            saved.abandoned = base.getBoolean("Abandoned");
            saved.famineCycles = Math.max(0, base.getInt("Famine"));
        }

        long[] savedSites = nbt.getLongArray("GeneratedSites");
        for (int i = 0; i < Math.min(savedSites.length, MAX_GENERATED_SITES); i++) {
            state.generatedSites.add(savedSites[i]);
        }

        NbtList savedRelations = nbt.getList("Relations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(savedRelations.size(), MAX_RELATIONS); i++) {
            NbtCompound relation = savedRelations.getCompound(i);
            state.relations.put(relationKey(relation.getString("A"), relation.getString("B")),
                    clampRelation(relation.getInt("Value")));
        }

        NbtList savedReputations = nbt.getList("Reputations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(savedReputations.size(), MAX_REPUTATIONS); i++) {
            NbtCompound reputation = savedReputations.getCompound(i);
            if (reputation.containsUuid("Player")) {
                state.reputations.put(reputationKey(reputation.getUuid("Player"), reputation.getString("Faction")),
                        clampRelation(reputation.getInt("Value")));
            }
        }

        NbtList savedWealth = nbt.getList("Wealth", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < savedWealth.size(); i++) {
            NbtCompound entry = savedWealth.getCompound(i);
            state.wealth.put(normalize(entry.getString("Faction")),
                    Math.max(0, Math.min(100, entry.getInt("Value"))));
        }

        NbtList savedChronicle = nbt.getList("Chronicle", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(savedChronicle.size(), MAX_CHRONICLE); i++) {
            NbtCompound entry = savedChronicle.getCompound(i);
            state.chronicle.add(new ChronicleEntry(entry.getLong("Day"), entry.getString("Text"),
                    entry.getBoolean("Major")));
        }

        NbtList savedBonds = nbt.getList("LocalRep", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < savedBonds.size(); i++) {
            NbtCompound entry = savedBonds.getCompound(i);
            try {
                BaseRecord base = state.findByCenter(entry.getLong("Center"));
                if (base != null && entry.containsUuid("Player")) {
                    base.localReputation.put(entry.getUuid("Player"), clampRelation(entry.getInt("Value")));
                }
            } catch (IllegalArgumentException ignored) {
                // One corrupt entry must not sink the ledger.
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

    public boolean canClaimBase(BlockPos center) {
        if (center == null || bases.size() >= MAX_BASES) {
            return false;
        }
        for (BaseRecord existing : bases) {
            double minimum = existing.radius() + 24.0;
            if (existing.center().getSquaredDistance(center) <= minimum * minimum) {
                return false;
            }
        }
        return true;
    }

    private BaseRecord claimBase(BlockPos center, UUID owner, String faction, String style, String name) {
        if (center == null || owner == null || !canClaimBase(center)) {
            return null;
        }
        faction = safeText(faction, "Independent");
        style = safeText(style, "custom");
        name = safeText(name, "Claimed Settlement");
        for (BaseRecord existing : bases) {
            double minimum = existing.radius() + 24.0;
            if (existing.center().getSquaredDistance(center) <= minimum * minimum) {
                return null;
            }
        }
        BaseRecord record = new BaseRecord(center.asLong(), owner, faction, style, name,
                1, 0L, 12, 24, 0, 0L);
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

    public boolean hasGeneratedSite(BlockPos center) {
        return generatedSites.contains(center.asLong());
    }

    public boolean canGenerateSite() {
        return generatedSites.size() < MAX_GENERATED_SITES;
    }

    public void markGeneratedSite(BlockPos center) {
        if (generatedSites.size() < MAX_GENERATED_SITES) {
            generatedSites.add(center.asLong());
            markDirty();
        }
    }

    public BaseRecord claimGeneratedBase(BlockPos center, UUID owner, BuildStyle style, String name) {
        return claimBase(center, owner, style.faction(), style.id(), name);
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
        base.workProgress = 0;
        markDirty();
    }

    public void recordSettlementWork(BaseRecord base, int food, int materials, int work) {
        base.food = Math.min(999, base.food + Math.max(0, food));
        base.materials = Math.min(999, base.materials + Math.max(0, materials));
        base.workProgress = Math.min(999, base.workProgress + Math.max(0, work));
        markDirty();
    }

    /** A living town eats. Nothing in, nothing stored. */
    public void consumeFood(BaseRecord base, int amount) {
        base.food = Math.max(0, base.food - Math.max(0, amount));
        markDirty();
    }

    public void drain(BaseRecord base, int food, int materials) {
        base.food = Math.max(0, base.food - Math.max(0, food));
        base.materials = Math.max(0, base.materials - Math.max(0, materials));
        markDirty();
    }

    public boolean beginExpansion(BaseRecord base, int foodCost, int materialCost, long worldTime) {
        if (base.level >= 5 || base.food < foodCost || base.materials < materialCost
                || (base.lastExpansion > 0L && worldTime - base.lastExpansion < 12000L)) {
            return false;
        }
        base.food -= foodCost;
        base.materials -= materialCost;
        base.workProgress = 0;
        base.lastExpansion = worldTime;
        base.level++;
        markDirty();
        return true;
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
            entry.putInt("Food", base.food);
            entry.putInt("Materials", base.materials);
            entry.putInt("Work", base.workProgress);
            entry.putLong("LastExpansion", base.lastExpansion);
            entry.putBoolean("Abandoned", base.abandoned);
            entry.putInt("Famine", base.famineCycles);
            savedBases.add(entry);
        }
        nbt.put("Bases", savedBases);
        long[] savedSites = new long[Math.min(generatedSites.size(), MAX_GENERATED_SITES)];
        int siteIndex = 0;
        for (Long site : generatedSites) {
            if (siteIndex >= savedSites.length) {
                break;
            }
            savedSites[siteIndex++] = site;
        }
        nbt.putLongArray("GeneratedSites", savedSites);

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

        NbtList savedWealth = new NbtList();
        for (Map.Entry<String, Integer> entry : wealth.entrySet()) {
            NbtCompound entryNbt = new NbtCompound();
            entryNbt.putString("Faction", entry.getKey());
            entryNbt.putInt("Value", entry.getValue());
            savedWealth.add(entryNbt);
        }
        nbt.put("Wealth", savedWealth);

        NbtList savedChronicle = new NbtList();
        for (ChronicleEntry entry : chronicle) {
            NbtCompound entryNbt = new NbtCompound();
            entryNbt.putLong("Day", entry.day());
            entryNbt.putString("Text", entry.text());
            entryNbt.putBoolean("Major", entry.major());
            savedChronicle.add(entryNbt);
        }
        nbt.put("Chronicle", savedChronicle);

        NbtList savedLocal = new NbtList();
        for (BaseRecord base : bases) {
            for (Map.Entry<UUID, Integer> entry : base.localReputation.entrySet()) {
                NbtCompound entryNbt = new NbtCompound();
                entryNbt.putLong("Center", base.centerLong);
                entryNbt.putUuid("Player", entry.getKey());
                entryNbt.putInt("Value", entry.getValue());
                savedLocal.add(entryNbt);
            }
        }
        nbt.put("LocalRep", savedLocal);
        return nbt;
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "independent" : value.toLowerCase(Locale.ROOT);
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
            // Marauders are at war with the entire world, without exception.
            case "crownlands|marauders", "dustwalkers|marauders",
                 "freebooters|marauders", "hearthfolk|marauders",
                 "independent|marauders", "skybound|marauders" -> -75;
            // Hearthfolk are quiet friends to everyone who leaves them be.
            case "crownlands|hearthfolk", "dustwalkers|hearthfolk" -> 15;
            case "freebooters|hearthfolk", "hearthfolk|independent" -> 0;
            case "hearthfolk|skybound" -> 5;
            default -> -20;
        };
    }

    public ChronicleEntry chronicle(long worldTime, String text, boolean major) {
        ChronicleEntry entry = new ChronicleEntry(worldTime / 24000L + 1L, safeText(text, ""), major);
        chronicle.add(entry);
        while (chronicle.size() > MAX_CHRONICLE) {
            chronicle.remove(0);
        }
        markDirty();
        return entry;
    }

    public List<ChronicleEntry> chronicle() {
        return Collections.unmodifiableList(chronicle);
    }

    /** Faction treasury, 0 (ragged) to 100 (golden age). Drives gear and patrols. */
    public int wealth(String faction) {
        return wealth.getOrDefault(normalize(faction), 45);
    }

    public void adjustWealth(String faction, int amount) {
        String key = normalize(faction);
        wealth.put(key, Math.max(0, Math.min(100, wealth.getOrDefault(key, 45) + amount)));
        markDirty();
    }

    public BaseRecord findByCenter(BlockPos center) {
        long key = center.asLong();
        for (BaseRecord base : bases) {
            if (base.centerLong == key) {
                return base;
            }
        }
        return null;
    }

    public record ChronicleEntry(long day, String text, boolean major) {
    }

    public static final class BaseRecord {
        private final long centerLong;
        private UUID owner;
        private String faction;
        private String styleId;
        private final String name;
        private boolean abandoned;
        /** Player-local standing with THIS settlement, separate from faction-wide reputation. */
        private final Map<UUID, Integer> localReputation = new HashMap<>();
        private int famineCycles;
        private int level;
        private long lastRaid;
        private int food;
        private int materials;
        private int workProgress;
        private long lastExpansion;

        private BaseRecord(long centerLong, UUID owner, String faction, String styleId,
                           String name, int level, long lastRaid, int food, int materials,
                           int workProgress, long lastExpansion) {
            this.centerLong = centerLong;
            this.owner = owner;
            this.faction = faction;
            this.styleId = styleId;
            this.name = name;
            this.level = level;
            this.lastRaid = lastRaid;
            this.food = food;
            this.materials = materials;
            this.workProgress = workProgress;
            this.lastExpansion = lastExpansion;
        }

        public BlockPos center() {
            return BlockPos.fromLong(centerLong);
        }

        public UUID owner() {
            return owner;
        }

        /** A settlement that falls in war now serves a new banner. */
        public void surrenderTo(UUID newOwner, String newFaction, String newStyle) {
            this.owner = newOwner;
            this.faction = safeText(newFaction, faction);
            this.styleId = safeText(newStyle, styleId);
            this.lastRaid = 0L;
            this.famineCycles = 0;
        }

        public boolean abandoned() {
            return abandoned;
        }

        public void setAbandoned(boolean abandoned) {
            this.abandoned = abandoned;
        }

        public int localReputation(UUID player) {
            return localReputation.getOrDefault(player, 0);
        }

        public void adjustLocalReputation(UUID player, int amount) {
            localReputation.put(player, clampRelation(localReputation.getOrDefault(player, 0) + amount));
        }

        public int famineCycles() {
            return famineCycles;
        }

        public void setFamineCycles(int famineCycles) {
            this.famineCycles = famineCycles;
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

        public int food() {
            return food;
        }

        public int materials() {
            return materials;
        }

        public int workProgress() {
            return workProgress;
        }

        public long lastExpansion() {
            return lastExpansion;
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
