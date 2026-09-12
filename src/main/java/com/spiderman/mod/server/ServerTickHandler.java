package com.spiderman.mod.server;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Per-tick server driver: persistence, cleanup and per-player power ticks.
 * Now includes:
 * - time-based progression
 * - pull hold logic (as long as you hold it)
 * - web standing (cobwebs become solid for Spider-Man)
 * - spider protection (spiders don't attack when on webs)
 */
public final class ServerTickHandler {
    private static int tickCount;

    private ServerTickHandler() {
    }

    public static void onEndTick(MinecraftServer server) {
        SpiderState.ensureLoaded(server);
        tickCount++;
        if (tickCount % 6000 == 0) {
            SpiderState.save(server);
        }
        if (tickCount % 1200 == 0) {
            boolean hasPowered = false;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (SpiderState.get(p.getUuid()).hasPowers) {
                    hasPowered = true;
                    break;
                }
            }
            if (hasPowered) {
                SpiderState.save(server);
            }
        }
        WebCleanup.tick();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!player.isAlive()) {
            if (powers.swinging) {
                SwingPhysics.detach(player, powers, false);
            }
            powers.zipTicks = 0;
            powers.pullTicks = 0;
            try {
                player.setNoGravity(false);
            } catch (Exception ignored) {}
            ClimbLogic.cancel(player, powers);
            return;
        }
        if (!powers.hasPowers) {
            return;
        }

        powers.timeWithPowers++;
        powers.lastPassiveTick = player.age;

        // --- Time-based progression ---
        if (player.age % 20 == 0) {
            MasteryLogic.addMastery(player, 1);
            double horiz = player.getVelocity().horizontalLength();
            if (horiz > 0.1) MasteryLogic.addMastery(player, 1);
            if (player.isSprinting() && horiz > 0.2) MasteryLogic.addMastery(player, 1);
            if (!player.isOnGround() && player.getVelocity().y < -0.1) MasteryLogic.addMastery(player, 1);
            if (player.getY() > 100) MasteryLogic.addMastery(player, 1);
        }
        if (powers.climbing && player.age % 10 == 0) MasteryLogic.addMastery(player, 1);
        if (player.isSneaking() && player.getVelocity().horizontalLength() < 0.05 && player.isOnGround()) {
            powers.focusTicks++;
            if (powers.focusTicks % 40 == 0) MasteryLogic.addMastery(player, 1);
        } else {
            powers.focusTicks = 0;
        }

        ComboTracker.tick(player, powers);
        if (powers.swinging) SwingPhysics.tick(player, powers);
        if (powers.zipTicks > 0) SwingPhysics.tickZip(player, powers);
        if (powers.pullTicks > 0) tickPull(player, powers);
        ClimbLogic.tick(player, powers);
        tickWebStanding(player, powers);
        tickSpiderProtection(player, powers);

        if (player.isOnGround()) {
            powers.doubleJumpUsed = false;
            if (!player.isSneaking()) powers.focusTicks = 0;
        }
        if (player.age % 10 == 0) SenseLogic.tick(player, powers);
        if (powers.swinging && player.age % 20 == 0) MasteryLogic.addMastery(player, 2);
        if (player.age % 40 == 0) TransformLogic.tryStageUp(player);

        // Safety clamps
        if (powers.stage < 0) powers.stage = 0;
        if (powers.stage > 4) powers.stage = 4;
        if (powers.mastery < 0) powers.mastery = 0;
        if (powers.mastery > 100000) powers.mastery = 100000;

        if (!powers.swinging && powers.zipTicks == 0 && powers.pullTicks == 0 && !powers.climbing) {
            try {
                if (player.hasNoGravity()) player.setNoGravity(false);
            } catch (Exception ignored) {}
        }
        if (player.getAbilities().flying && powers.climbing) {
            ClimbLogic.cancel(player, powers);
        }
    }

    private static void tickPull(ServerPlayerEntity player, PlayerPowers powers) {
        powers.pullTicks--;
        if (powers.pullTicks <= 0) {
            powers.stopPull();
            try {
                player.setNoGravity(false);
            } catch (Exception ignored) {}
            return;
        }
        Vec3d targetPos = new Vec3d(powers.pullX, powers.pullY, powers.pullZ);
        Vec3d playerPos = player.getPos().add(0, 1.0, 0);
        Vec3d toTarget = targetPos.subtract(playerPos);
        double dist = toTarget.length();
        if (!Double.isFinite(dist) || dist < 1.0) {
            powers.stopPull();
            return;
        }

        // If pulling player to target (grapple)
        if (powers.pullingPlayer) {
            Vec3d dir = toTarget.normalize();
            double speed = Math.min(1.8, 0.5 + dist * 0.08);
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            Vec3d vel = dir.multiply(speed);
            player.fallDistance = 0;
            SwingPhysics.push(player, vel);
            // If close, stop
            if (dist < 2.0) {
                powers.stopPull();
            }
        } else {
            // Pulling target entity to player — find entity by UUID
            LivingEntity target = null;
            try {
                if (powers.pullTargetId != null) {
                    ServerWorld world = player.getServerWorld();
                    // Search nearby for entity with matching UUID
                    Box box = new Box(playerPos.x - 24, playerPos.y - 24, playerPos.z - 24,
                            playerPos.x + 24, playerPos.y + 24, playerPos.z + 24);
                    List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, box,
                            e -> e != null && e.isAlive() && e.getUuid().equals(powers.pullTargetId));
                    if (!nearby.isEmpty()) target = nearby.get(0);
                }
            } catch (Exception ignored) {}

            if (target == null || !target.isAlive()) {
                // No entity, just pull to point? Stop
                powers.stopPull();
                return;
            }

            Vec3d fromTargetToPlayer = playerPos.subtract(target.getPos().add(0, target.getHeight() * 0.5, 0));
            double d = fromTargetToPlayer.length();
            if (d < 2.0) {
                powers.stopPull();
                return;
            }
            Vec3d dir = fromTargetToPlayer.normalize();
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            double speed = Math.min(1.5, 0.4 + d * 0.07);
            Vec3d yank = dir.multiply(speed);
            // Keep some upward to not drag on ground
            yank = new Vec3d(yank.x, Math.max(0.2, yank.y + 0.2), yank.z);
            target.setVelocity(yank.x, yank.y, yank.z);
            target.velocityModified = true;
        }
    }

    private static void tickWebStanding(ServerPlayerEntity player, PlayerPowers powers) {
        try {
            // Check if player is in or on cobweb
            BlockPos feet = player.getBlockPos();
            BlockPos below = feet.down();
            BlockPos at = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ());
            BlockPos atEye = BlockPos.ofFloored(player.getEyePos().x, player.getEyePos().y, player.getEyePos().z);

            boolean onWeb = false;
            try {
                if (player.getWorld().getBlockState(below).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(feet).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(at).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(atEye).isOf(Blocks.COBWEB)) onWeb = true;
            } catch (Exception ignored) {}

            if (onWeb) {
                player.fallDistance = 0;
                // Cancel cobweb slowing for Spider-Man — allow movement and standing
                Vec3d vel = player.getVelocity();
                if (vel != null) {
                    // If sneaking, allow slow descent through web, else stand
                    if (player.isSneaking()) {
                        // Slow fall through web when sneaking
                        if (vel.y < -0.1) {
                            SwingPhysics.push(player, new Vec3d(vel.x * 0.8, -0.1, vel.z * 0.8));
                        }
                    } else {
                        // Stand on web: cancel downward velocity, allow horizontal movement
                        if (vel.y < 0) {
                            SwingPhysics.push(player, new Vec3d(vel.x * 0.9, 0.0, vel.z * 0.9));
                        }
                        // Make player effectively on ground when on web for jump purposes
                        // We don't set onGround directly (it's calculated), but we prevent falling
                    }
                }
                // Give slight resistance to being knocked off web
                if (player.age % 20 == 0) {
                    MasteryLogic.addMastery(player, 1);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void tickSpiderProtection(ServerPlayerEntity player, PlayerPowers powers) {
        // Spiders don't attack Spider-Man when on webs or at higher stages
        // At stage 0, only when on web; at stage 1+, always reduced targeting
        if (player.age % 20 != 0) return;
        try {
            boolean onWeb = false;
            BlockPos below = player.getBlockPos().down();
            try {
                if (player.getWorld().getBlockState(below).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(player.getBlockPos()).isOf(Blocks.COBWEB)) onWeb = true;
            } catch (Exception ignored) {}

            boolean shouldProtect = onWeb || powers.stage >= 1;
            if (!shouldProtect) return;

            ServerWorld world = player.getServerWorld();
            Box box = new Box(player.getX() - 16, player.getY() - 8, player.getZ() - 16,
                    player.getX() + 16, player.getY() + 8, player.getZ() + 16);
            List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box,
                    e -> e != null && e.isAlive() && e.getTarget() == player);
            for (HostileEntity hostile : hostiles) {
                // If spider and player is Spider-Man on web, clear target
                String type = hostile.getType().toString().toLowerCase();
                if (type.contains("spider") || onWeb) {
                    try {
                        hostile.setTarget(null);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
}
