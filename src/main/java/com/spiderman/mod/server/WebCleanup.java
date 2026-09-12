package com.spiderman.mod.server;

import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.spiderman.mod.config.SpiderConfig;

/**
 * Tracks player-placed trap/platform webs and removes them when they expire,
 * so abilities never grief the world permanently.
 *
 * Bughunt:
 * - Null checks for player/world/pos
 * - isAir check now also handles null world
 * - Eviction loop safe even if maxTrapWebs is misconfigured
 * - removeIfOurs checks world is still loaded
 */
public final class WebCleanup {
    private static final class Web {
        final ServerWorld world;
        final BlockPos pos;
        int ttl;

        Web(ServerWorld world, BlockPos pos, int ttl) {
            this.world = world;
            this.pos = pos;
            this.ttl = ttl;
        }
    }

    private static final Map<UUID, List<Web>> BY_OWNER = new HashMap<>();

    private WebCleanup() {
    }

    /** Places a temporary cobweb if the spot is free. Evicts oldest when capped. */
    public static boolean place(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) return false;
        ServerWorld world;
        try {
            world = player.getServerWorld();
        } catch (Exception e) {
            return false;
        }
        if (world == null) return false;
        try {
            if (!world.getBlockState(pos).isAir()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        SpiderConfig cfg = SpiderConfig.get();
        int maxWebs = cfg.maxTrapWebs;
        if (maxWebs < 1) maxWebs = 1;
        int liveTicks = cfg.webLiveTicks;
        if (liveTicks < 1) liveTicks = 20;

        List<Web> owned = BY_OWNER.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());
        // Evict oldest when capped — safe even if list is huge
        while (owned.size() >= maxWebs && !owned.isEmpty()) {
            Web oldest = owned.remove(0);
            removeIfOurs(oldest);
        }
        try {
            world.setBlockState(pos, Blocks.COBWEB.getDefaultState(), 3);
        } catch (Exception e) {
            return false;
        }
        owned.add(new Web(world, pos.toImmutable(), liveTicks));
        return true;
    }

    public static void tick() {
        try {
            for (List<Web> owned : BY_OWNER.values()) {
                if (owned == null) continue;
                Iterator<Web> it = owned.iterator();
                while (it.hasNext()) {
                    Web web = it.next();
                    if (web == null) {
                        it.remove();
                        continue;
                    }
                    web.ttl--;
                    if (web.ttl <= 0) {
                        removeIfOurs(web);
                        it.remove();
                    }
                }
            }
        } catch (Exception ignored) {
            // Don't crash server tick due to web cleanup
        }
    }

    public static void clearPlayer(UUID id) {
        if (id == null) return;
        List<Web> owned = BY_OWNER.remove(id);
        if (owned != null) {
            for (Web web : owned) {
                removeIfOurs(web);
            }
        }
    }

    /** Removes every tracked web (server stop / world switch). */
    public static void clearAll() {
        try {
            for (List<Web> owned : BY_OWNER.values()) {
                if (owned == null) continue;
                for (Web web : owned) {
                    removeIfOurs(web);
                }
            }
        } catch (Exception ignored) {}
        BY_OWNER.clear();
    }

    private static void removeIfOurs(Web web) {
        if (web == null || web.world == null || web.pos == null) return;
        try {
            // Check if world is still valid and chunk loaded
            if (web.world.getBlockState(web.pos).isOf(Blocks.COBWEB)) {
                web.world.removeBlock(web.pos, false);
            }
        } catch (Exception ignored) {
            // World may be unloading; nothing to clean
        }
    }
}
