package com.rivalrealms.world;

import com.rivalrealms.RivalRealms;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Deterministic, low-density world dressing. Settlements are generated only
 * after their chunk is loaded, are recorded in persistent state, and never
 * force-load a distant area from a tick loop.
 */
public final class WorldSettlementGenerator {
    // Roughly one candidate per 128 explored chunks: visible during normal
    // exploration without turning every horizon into a city wall.
    private static final long SITE_SPACING = 128L;
    private static final int SITE_RADIUS = 36;

    private WorldSettlementGenerator() {
    }

    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        if (!World.OVERWORLD.equals(world.getRegistryKey())) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        long hash = siteHash(world.getSeed(), chunkPos.x, chunkPos.z);
        if (Math.floorMod(hash, SITE_SPACING) != 0L) {
            return;
        }

        BlockPos requested = chunkPos.getStartPos().add(8, 0, 8);
        if (!areaLoaded(world, requested, SITE_RADIUS)) {
            return;
        }

        RealmState state = RealmState.get(world);
        if (!state.canGenerateSite()) {
            return;
        }
        BlockPos center = chooseSurface(world, requested);
        if (center == null || state.hasGeneratedSite(center)) {
            return;
        }
        if (!state.canClaimBase(center)) {
            state.markGeneratedSite(center);
            return;
        }

        // Places outside every map: hermits beyond the reach of banners.
        if (hash % 37L == 0L) {
            state.markGeneratedSite(center);
            buildHidden(world, state, center, SettlementVariant.HERMITAGE);
            return;
        }
        // Places where the world's rules bent.
        if (hash % 23L == 0L) {
            state.markGeneratedSite(center);
            buildHidden(world, state, center, SettlementVariant.ANOMALY);
            return;
        }
        // Rare treasure shrines ignore biome borders: old gods had no borders.
        if (hash % 11L == 0L) {
            state.markGeneratedSite(center);
            UUID shrineOwner = UUID.nameUUIDFromBytes((RivalRealms.MOD_ID + ":generated:" + world.getSeed()
                    + ":" + center.asLong()).getBytes(StandardCharsets.UTF_8));
            try {
                StructureBuilder.buildScattered(world, center, BuildStyle.CUSTOM, SettlementVariant.TEMPLE);
                String shrineName = BuildStyle.CUSTOM.displayName() + " · "
                        + SettlementVariant.TEMPLE.name().toLowerCase(Locale.ROOT);
                state.claimGeneratedBase(center, shrineOwner, BuildStyle.CUSTOM, shrineName);
                RealmEvents.populateSettlement(world, center, BuildStyle.CUSTOM, SettlementVariant.TEMPLE);
                RivalRealms.LOGGER.info("Generated {} at {}", shrineName, center);
            } catch (RuntimeException exception) {
                RivalRealms.LOGGER.error("Failed to generate Rival Realms site at {}", center, exception);
            }
            return;
        }
        String biome = biomePath(world, center);
        SettlementPlan plan = planFor(biome, hash);
        if (plan == null || !terrainAllows(world, center, plan.style())) {
            return;
        }

        // Mark before drawing blocks so a bad custom chunk cannot repeatedly
        // rebuild the same site on every unload/reload cycle.
        state.markGeneratedSite(center);
        UUID owner = UUID.nameUUIDFromBytes((RivalRealms.MOD_ID + ":generated:" + world.getSeed()
                + ":" + center.asLong()).getBytes(StandardCharsets.UTF_8));
        try {
            StructureBuilder.buildScattered(world, center, plan.style(), plan.variant());
            String name = plan.style().displayName() + " · " + plan.variant().name().toLowerCase(Locale.ROOT);
            state.claimGeneratedBase(center, owner, plan.style(), name);
            // The settlement starts inhabited: guard on the walls, farmers in
            // the fields. The slow cadence still grows it from here.
            RealmEvents.populateSettlement(world, center, plan.style(), plan.variant());
            RivalRealms.LOGGER.info("Generated {} at {} in {}", name, center, biome);
        } catch (RuntimeException exception) {
            RivalRealms.LOGGER.error("Failed to generate Rival Realms site at {}", center, exception);
        }
    }

    /** Hidden sites claim quietly; hermits live alone. */
    private static void buildHidden(ServerWorld world, RealmState state, BlockPos center,
                                    SettlementVariant variant) {
        UUID owner = UUID.nameUUIDFromBytes((RivalRealms.MOD_ID + ":generated:" + world.getSeed()
                + ":" + center.asLong()).getBytes(StandardCharsets.UTF_8));
        try {
            StructureBuilder.buildScattered(world, center, BuildStyle.CUSTOM, variant);
            String name = (variant == SettlementVariant.HERMITAGE ? "Hermitage" : "Strange Place")
                    + " · " + center.getX() + ", " + center.getZ();
            state.claimGeneratedBase(center, owner, BuildStyle.CUSTOM, name);
            if (variant == SettlementVariant.HERMITAGE) {
                RealmEvents.populateSettlement(world, center, BuildStyle.CUSTOM, variant);
            }
            RivalRealms.LOGGER.info("Generated {} at {}", name, center);
        } catch (RuntimeException exception) {
            RivalRealms.LOGGER.error("Failed to generate Rival Realms site at {}", center, exception);
        }
    }

    private static SettlementPlan planFor(String biome, long hash) {
        String name = biome == null ? "" : biome.toLowerCase(Locale.ROOT);
        if (name.contains("ocean") || name.contains("river") || name.contains("beach")) {
            return new SettlementPlan(BuildStyle.PIRATE,
                    (hash & 8L) == 0L ? SettlementVariant.HARBOR : SettlementVariant.SHIPYARD);
        }
        if (name.contains("desert") || name.contains("badlands") || name.contains("savanna")) {
            if ((hash & 32L) != 0L) {
                // The wastes belong to nobody: warcamps pitch where towns won't.
                return new SettlementPlan(BuildStyle.MARAUDER, SettlementVariant.OUTPOST);
            }
            return new SettlementPlan(BuildStyle.WESTERN,
                    (hash & 2L) == 0L ? SettlementVariant.TOWN : SettlementVariant.OUTPOST);
        }
        if (name.contains("peak") || name.contains("mountain") || name.contains("windswept")) {
            return new SettlementPlan(BuildStyle.SKY,
                    (hash & 16L) == 0L ? SettlementVariant.SKYPORT : SettlementVariant.AIRSHIP_YARD);
        }
        if (name.contains("taiga") || name.contains("snow") || name.contains("ice")) {
            return new SettlementPlan(BuildStyle.KNIGHT,
                    (hash & 8L) == 0L ? SettlementVariant.FORTRESS : SettlementVariant.CITADEL);
        }
        // Temperate lands roll the full table: civilized sites, quiet folk
        // hamlets, and the camps of those who prey on both.
        return switch ((int) (hash & 15L)) {
            case 0 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.TOWN);
            case 1 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.ROYAL_CITY);
            case 2 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.FORTRESS);
            case 3 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.CITADEL);
            case 4 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.MILL);
            case 5 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.RUIN);
            case 6 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.GRAVEYARD);
            case 7 -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.WATCHTOWER);
            case 8 -> new SettlementPlan(BuildStyle.HEARTHFOLK, SettlementVariant.FARMSTEAD);
            case 9 -> new SettlementPlan(BuildStyle.MARAUDER, SettlementVariant.OUTPOST);
            default -> new SettlementPlan(BuildStyle.KNIGHT, SettlementVariant.FARMSTEAD);
        };
    }

    private static boolean terrainAllows(ServerWorld world, BlockPos center, BuildStyle style) {
        if (center.getY() <= world.getBottomY() + 2 || center.getY() >= world.getTopY() - 24) {
            return false;
        }
        // Harbors are allowed on shallow water; every land culture needs
        // solid, reasonably level ground across its whole site - not just at
        // the center post - so houses never straddle cliffs or sprout from
        // the shallows. Probes stay inside the generating chunk so nothing
        // is force-loaded mid-generation.
        if (style == BuildStyle.PIRATE) {
            return true;
        }
        int highest = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        int[][] probes = {{0, 0}, {14, 0}, {-14, 0}, {0, 14}, {0, -14},
                {16, 16}, {-16, -16}, {16, -16}, {-16, 16},
                {24, 0}, {-24, 0}, {0, 24}, {0, -24}};
        for (int[] probe : probes) {
            int px = center.getX() + probe[0];
            int pz = center.getZ() + probe[1];
            BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE,
                    new BlockPos(px, center.getY(), pz)).down();
            if (!world.getFluidState(top).isEmpty()) {
                return false;
            }
            highest = Math.max(highest, top.getY());
            lowest = Math.min(lowest, top.getY());
        }
        return highest - lowest <= 14;
    }

    private static BlockPos chooseSurface(ServerWorld world, BlockPos requested) {
        BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, requested);
        if (surface.getY() < world.getBottomY() || surface.getY() >= world.getTopY()) {
            return null;
        }
        return surface;
    }

    private static boolean areaLoaded(ServerWorld world, BlockPos center, int radius) {
        int[] offsets = {-radius, radius};
        for (int x : offsets) {
            for (int z : offsets) {
                if (!world.getChunkManager().isChunkLoaded((center.getX() + x) >> 4,
                        (center.getZ() + z) >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String biomePath(ServerWorld world, BlockPos pos) {
        return world.getBiome(pos).getKey()
                .map(key -> key.getValue().getPath())
                .orElse("");
    }

    private static long siteHash(long seed, int chunkX, int chunkZ) {
        long value = seed ^ (long) chunkX * 341873128712L ^ (long) chunkZ * 132897987541L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private record SettlementPlan(BuildStyle style, SettlementVariant variant) {
    }
}
