package com.spiderman.mod.server;

import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
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
 * WEB CLEANUP - Fixed for no lag at high stage.
 */
public final class WebCleanup {
    private static final class Web {
        final ServerWorld world;
        final BlockPos pos;
        int ttl;
        final UUID owner;
        final long placedAt;

        Web(ServerWorld world, BlockPos pos, int ttl, UUID owner) {
            this.world = world;
            this.pos = pos;
            this.ttl = ttl;
            this.owner = owner;
            this.placedAt = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, List<Web>> BY_OWNER = new HashMap<>();

    private WebCleanup() {
    }

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
                if (!world.getBlockState(pos).isOf(Blocks.COBWEB) && 
                    !world.getBlockState(pos).isOf(Blocks.SHORT_GRASS) &&
                    !world.getBlockState(pos).isOf(Blocks.TALL_GRASS)) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        
        SpiderConfig cfg = SpiderConfig.get();
        int maxWebs = cfg.maxTrapWebs;
        if (maxWebs < 1) maxWebs = 1;
        // FIXED: Reduced bonus at high stage to prevent lag
        try {
            var powers = com.spiderman.mod.state.SpiderState.get(player.getUuid());
            if (powers != null) {
                maxWebs += Math.min(powers.stage, 3) * 2; // Was *4, now *2
                if (powers.stylePoints > 300) maxWebs += 4; // Was 8, now 4
                if (maxWebs > 40) maxWebs = 40; // HARD CAP 40, was 56+
            }
        } catch (Exception ignored) {}
        
        int liveTicks = cfg.webLiveTicks;
        if (liveTicks < 1) liveTicks = 20;
        try {
            var powers = com.spiderman.mod.state.SpiderState.get(player.getUuid());
            if (powers != null && powers.stage >= 3) {
                liveTicks = (int)(liveTicks * 1.2); // Was 1.5, now 1.2
            }
        } catch (Exception ignored) {}

        List<Web> owned = BY_OWNER.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());
        while (owned.size() >= maxWebs && !owned.isEmpty()) {
            Web oldest = owned.remove(0);
            removeIfOurs(oldest, true);
        }
        try {
            world.setBlockState(pos, Blocks.COBWEB.getDefaultState(), 3);
            if (cfg.enableParticles && owned.size() % 3 == 0) { // Only every 3rd web has particles
                world.spawnParticles(ParticleTypes.ITEM_COBWEB, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 
                    2, 0.15, 0.15, 0.15, 0.03);
            }
        } catch (Exception e) {
            return false;
        }
        owned.add(new Web(world, pos.toImmutable(), liveTicks, player.getUuid()));
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
                        removeIfOurs(web, false);
                        it.remove();
                    } else if (web.ttl == 20 && web.ttl % 5 == 0) {
                        try {
                            if (web.world != null && SpiderConfig.get().enableParticles) {
                                // Less particles
                                if (Math.random() < 0.3) {
                                    web.world.spawnParticles(ParticleTypes.CLOUD, 
                                        web.pos.getX() + 0.5, web.pos.getY() + 0.5, web.pos.getZ() + 0.5,
                                        1, 0.08, 0.08, 0.08, 0.01);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void clearPlayer(UUID id) {
        if (id == null) return;
        List<Web> owned = BY_OWNER.remove(id);
        if (owned != null) {
            for (Web web : owned) {
                removeIfOurs(web, true);
            }
        }
    }

    public static void clearAll() {
        try {
            for (List<Web> owned : BY_OWNER.values()) {
                if (owned == null) continue;
                for (Web web : owned) {
                    removeIfOurs(web, true);
                }
            }
        } catch (Exception ignored) {}
        BY_OWNER.clear();
    }

    private static void removeIfOurs(Web web, boolean immediate) {
        if (web == null || web.world == null || web.pos == null) return;
        try {
            if (web.world.getBlockState(web.pos).isOf(Blocks.COBWEB)) {
                web.world.removeBlock(web.pos, false);
                if (!immediate && SpiderConfig.get().enableParticles && Math.random() < 0.2) {
                    try {
                        web.world.spawnParticles(ParticleTypes.ITEM_COBWEB,
                            web.pos.getX() + 0.5, web.pos.getY() + 0.5, web.pos.getZ() + 0.5,
                            2, 0.15, 0.15, 0.15, 0.03);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
    
    public static int getWebCount(UUID owner) {
        List<Web> owned = BY_OWNER.get(owner);
        return owned == null ? 0 : owned.size();
    }
}
