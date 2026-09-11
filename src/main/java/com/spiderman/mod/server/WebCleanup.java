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
        ServerWorld world = player.getServerWorld();
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        SpiderConfig cfg = SpiderConfig.get();
        List<Web> owned = BY_OWNER.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());
        while (owned.size() >= cfg.maxTrapWebs) {
            Web oldest = owned.remove(0);
            removeIfOurs(oldest);
        }
        world.setBlockState(pos, Blocks.COBWEB.getDefaultState(), 3);
        owned.add(new Web(world, pos.toImmutable(), cfg.webLiveTicks));
        return true;
    }

    public static void tick() {
        for (List<Web> owned : BY_OWNER.values()) {
            Iterator<Web> it = owned.iterator();
            while (it.hasNext()) {
                Web web = it.next();
                web.ttl--;
                if (web.ttl <= 0) {
                    removeIfOurs(web);
                    it.remove();
                }
            }
        }
    }

    public static void clearPlayer(UUID id) {
        List<Web> owned = BY_OWNER.remove(id);
        if (owned != null) {
            for (Web web : owned) {
                removeIfOurs(web);
            }
        }
    }

    private static void removeIfOurs(Web web) {
        try {
            if (web.world.getBlockState(web.pos).isOf(Blocks.COBWEB)) {
                web.world.removeBlock(web.pos, false);
            }
        } catch (Exception ignored) {
            // World may be unloading; nothing to clean.
        }
    }
}
