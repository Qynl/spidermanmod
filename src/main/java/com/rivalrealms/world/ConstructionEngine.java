package com.rivalrealms.world;

import com.rivalrealms.entity.SurvivorEntity;
import com.rivalrealms.sound.ModSounds;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settlements the player can watch grow: builders raise real cottages at
 * the edge of town one layer at a time, and farmers push the fields a
 * little further out each season. Work only happens while someone is
 * nearby to see it - construction you never witness is just noise.
 */
public final class ConstructionEngine {
    private ConstructionEngine() {
    }

    /** Partial builds in progress, keyed by settlement. */
    private static final Map<Long, BlockPos> SITES = new HashMap<>();
    private static final Map<Long, Integer> PROGRESS = new HashMap<>();
    private static final Map<Long, Integer> BUILT = new HashMap<>();

    public static void tick(ServerWorld world) {
        if (world.getTime() % 300L != 0L) {
            return;
        }
        RealmState state = RealmState.get(world);
        for (RealmState.BaseRecord base : state.bases()) {
            if (base.abandoned() || base.food() < 20
                    || !world.getChunkManager().isChunkLoaded(
                            base.center().getX() >> 4, base.center().getZ() >> 4)) {
                continue;
            }
            List<SurvivorEntity> folk = LivingRealm.population(world, base);
            SurvivorEntity builder = null;
            SurvivorEntity farmer = null;
            for (SurvivorEntity f : folk) {
                if (f.settlementRole() == SettlementRole.BUILDER && builder == null) {
                    builder = f;
                }
                if (f.settlementRole() == SettlementRole.FARMER && farmer == null) {
                    farmer = f;
                }
            }
            if (farmer != null && world.random.nextInt(2) == 0) {
                workField(world, base, farmer);
            }
            if (builder != null && base.level() >= 2
                    && BUILT.getOrDefault(base.center().asLong(), 0) < 3
                    && world.random.nextInt(3) == 0) {
                workSite(world, base, builder);
            }
        }
    }

    /** The farmer's craft: one more tilled row at the field's edge. */
    private static void workField(ServerWorld world, RealmState.BaseRecord base,
                                  SurvivorEntity farmer) {
        BlockPos at = farmer.getBlockPos();
        if (base.contains(at)) {
            BlockPos spot = at.offset(net.minecraft.util.math.Direction.Type.HORIZONTAL
                    .random(world.random));
            if (base.contains(spot) && world.getBlockState(spot).isOf(Blocks.DIRT)
                    && world.getBlockState(spot.up()).isAir()) {
                world.setBlockState(spot, Blocks.FARMLAND.getDefaultState());
                world.setBlockState(spot.up(), Blocks.WHEAT.getDefaultState()
                        .with(net.minecraft.block.CropBlock.AGE, 1));
                ModSounds.playProfiled(world, spot, "work_shout",
                        farmer.getUuid(), 1.0f, 0.9f);
            }
        }
    }

    /** The builder's craft: raise the cottage one course per visit. */
    private static void workSite(ServerWorld world, RealmState.BaseRecord base,
                                 SurvivorEntity builder) {
        long key = base.center().asLong();
        BlockPos site = SITES.get(key);
        if (site == null) {
            // Choose a fresh plot on the settlement's quiet edge.
            for (int attempt = 0; attempt < 8; attempt++) {
                int angle = world.random.nextInt(8);
                int ring = base.radius() - 6;
                BlockPos candidate = base.center().add(
                        new BlockPos[]{BlockPos.ORIGIN, new BlockPos(ring, 0, 0),
                                new BlockPos(ring, 0, ring), new BlockPos(0, 0, ring),
                                new BlockPos(-ring, 0, ring), new BlockPos(-ring, 0, 0),
                                new BlockPos(-ring, 0, -ring), new BlockPos(0, 0, -ring)}[angle]);
                candidate = new BlockPos(candidate.getX(),
                        groundAt(world, candidate.getX(), candidate.getZ()), candidate.getZ());
                if (world.getBlockState(candidate.up()).isAir()
                        && world.getFluidState(candidate).isEmpty()) {
                    SITES.put(key, candidate);
                    PROGRESS.put(key, 0);
                    return;
                }
            }
            return;
        }
        int progress = PROGRESS.getOrDefault(key, 0);
        if (!world.getChunkManager().isChunkLoaded(site.getX() >> 4, site.getZ() >> 4)) {
            SITES.remove(key);
            PROGRESS.remove(key);
            return;
        }
        if (progress >= 20) {
            // Done: hand the plot a real house and move in a family name.
            StructureBuilder.buildScattered(world, site, BuildStyle.CUSTOM,
                    SettlementVariant.FARMSTEAD);
            SITES.remove(key);
            PROGRESS.remove(key);
            BUILT.put(key, BUILT.getOrDefault(key, 0) + 1);
            RealmState.get(world).chronicle(world.getTime(),
                    "A new cottage stands at the edge of " + base.name()
                            + ". The builder wiped their hands and named it done.", false);
            com.rivalrealms.sound.ModSounds.playVoice(world, site, "town_grows");
            return;
        }
        // Lay this visit's course, right where the builder can reach.
        BlockPos cursor = site.up(progress);
        int laid = 0;
        for (int dx = 0; dx < 5 && laid < 3; dx++) {
            for (int dz = 0; dz < 5 && laid < 3; dz++) {
                BlockPos at = cursor.add(dx, 0, dz);
                if (world.getBlockState(at).isAir()) {
                    world.setBlockState(at, ((progress / 4) % 2 == 0
                            ? Blocks.OAK_PLANKS : Blocks.OAK_LOG).getDefaultState());
                    laid++;
                }
            }
        }
        PROGRESS.put(key, progress + 1);
        ModSounds.playProfiled(world, builder.getBlockPos(), "work_song",
                builder.getUuid(), 0.95f, 0.9f);
    }

    private static int groundAt(ServerWorld world, int x, int z) {
        BlockPos top = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                new BlockPos(x, world.getBottomY(), z));
        return Math.max(world.getBottomY() + 1, top.getY() - 1);
    }
}
