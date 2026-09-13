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
 * SERVER TICK - Complete remake: stable, no lag, web walking works.
 */
public final class ServerTickHandler {
    private static int tickCount;

    private ServerTickHandler() {}

    public static void onEndTick(MinecraftServer server) {
        SpiderState.ensureLoaded(server);
        tickCount++;
        if (tickCount % 6000 == 0) SpiderState.save(server);
        if (tickCount % 1200 == 0) {
            boolean hasPowered = false;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (SpiderState.get(p.getUuid()).hasPowers) { hasPowered = true; break; }
            }
            if (hasPowered) SpiderState.save(server);
        }
        WebCleanup.tick();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!player.isAlive()) {
            if (powers.swinging) SwingPhysics.detach(player, powers, false);
            powers.zipTicks = 0;
            powers.pullTicks = 0;
            try { player.setNoGravity(false); } catch (Exception ignored) {}
            ClimbLogic.cancel(player, powers);
            return;
        }
        if (!powers.hasPowers) return;

        powers.timeWithPowers++;
        powers.lastPassiveTick = player.age;

        if (player.age % 20 == 0) {
            MasteryLogic.addMastery(player, 1);
            double horiz = player.getVelocity().horizontalLength();
            if (horiz > 0.15) MasteryLogic.addMastery(player, 1);
            if (player.isSprinting() && horiz > 0.25 && player.age % 60 == 0) {
                powers.stylePoints += 1;
            }
        }
        
        if (player.isSneaking() && player.getVelocity().horizontalLength() < 0.05 && player.isOnGround()) {
            powers.focusTicks++;
            if (powers.focusTicks % 80 == 0) MasteryLogic.addMastery(player, 1);
        } else {
            powers.focusTicks = 0;
        }
        
        if (player.age % 600 == 0 && !powers.swinging && !powers.climbing && player.getVelocity().length() < 0.05) {
            if (powers.stylePoints > 0) powers.stylePoints -= 1;
        }
        
        if (powers.combo > powers.maxCombo) {
            powers.maxCombo = powers.combo;
        }

        ComboTracker.tick(player, powers);
        if (powers.swinging) SwingPhysics.tick(player, powers);
        if (powers.zipTicks > 0) SwingPhysics.tickZip(player, powers);
        if (powers.pullTicks > 0) tickPull(player, powers);
        ClimbLogic.tick(player, powers);
        tickWebStanding(player, powers);
        if (player.age % (powers.stage >= 4 ? 40 : 20) == 0) tickSpiderProtection(player, powers);
        tickSlingshot(player, powers);

        if (player.isOnGround()) {
            powers.doubleJumpUsed = false;
            if (!player.isSneaking()) powers.focusTicks = 0;
            powers.airTime = 0;
            powers.wasInAir = false;
            powers.lastGroundedTime = player.age;
        } else {
            powers.wasInAir = true;
            powers.airTime++;
        }
        
        if (player.age % (powers.stage >= 4 ? 25 : 12) == 0) SenseLogic.tick(player, powers);
        if (player.age % 80 == 0) TransformLogic.tryStageUp(player);

        if (powers.stage < 0) powers.stage = 0;
        if (powers.stage > 4) powers.stage = 4;
        if (powers.mastery < 0) powers.mastery = 0;
        if (powers.mastery > 100000) powers.mastery = 100000;
        if (powers.stylePoints < 0) powers.stylePoints = 0;
        if (powers.stylePoints > 3000) powers.stylePoints = 3000;

        if (!powers.swinging && powers.zipTicks == 0 && powers.pullTicks == 0 && !powers.climbing && !powers.diving) {
            try { if (player.hasNoGravity()) player.setNoGravity(false); } catch (Exception ignored) {}
        }
        if (player.getAbilities().flying && powers.climbing) ClimbLogic.cancel(player, powers);
    }

    private static void tickSlingshot(ServerPlayerEntity player, PlayerPowers powers) {
        if (powers.slingshotCharging) {
            powers.slingshotCharge++;
            if (powers.slingshotCharge > 40) powers.slingshotCharge = 40;
        }
    }

    private static void tickPull(ServerPlayerEntity player, PlayerPowers powers) {
        powers.pullTicks--;
        if (powers.pullTicks <= 0) {
            powers.stopPull();
            try { player.setNoGravity(false); } catch (Exception ignored) {}
            return;
        }
        Vec3d targetPos = new Vec3d(powers.pullX, powers.pullY, powers.pullZ);
        Vec3d playerPos = player.getPos().add(0, 1.0, 0);
        Vec3d toTarget = targetPos.subtract(playerPos);
        double dist = toTarget.length();
        if (!Double.isFinite(dist) || dist < 0.8) {
            powers.stopPull();
            return;
        }

        if (powers.pullingPlayer) {
            Vec3d dir = toTarget.normalize();
            double speed = Math.min(1.4, 0.4 + dist * 0.06);
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            Vec3d vel = dir.multiply(speed);
            player.fallDistance = 0;
            SwingPhysics.push(player, vel);
            if (dist < 1.5) {
                powers.stopPull();
            }
        } else {
            LivingEntity target = null;
            try {
                if (powers.pullTargetId != null) {
                    ServerWorld world = player.getServerWorld();
                    Box box = new Box(playerPos.x - 15, playerPos.y - 15, playerPos.z - 15,
                            playerPos.x + 15, playerPos.y + 15, playerPos.z + 15);
                    List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, box,
                            e -> e != null && e.isAlive() && e.getUuid().equals(powers.pullTargetId));
                    if (!nearby.isEmpty()) target = nearby.get(0);
                }
            } catch (Exception ignored) {}

            if (target == null || !target.isAlive()) {
                powers.stopPull();
                return;
            }

            Vec3d fromTargetToPlayer = playerPos.subtract(target.getPos().add(0, target.getHeight() * 0.5, 0));
            double d = fromTargetToPlayer.length();
            if (d < 1.5) {
                powers.stopPull();
                try { target.damage(player.getWorld().getDamageSources().playerAttack(player), 2.5f); } catch (Exception ignored) {}
                return;
            }
            Vec3d dir = fromTargetToPlayer.normalize();
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            double speed = Math.min(1.1, 0.3 + d * 0.05);
            Vec3d yank = dir.multiply(speed);
            yank = new Vec3d(yank.x, Math.max(0.15, yank.y + 0.15), yank.z);
            target.setVelocity(yank.x, yank.y, yank.z);
            target.velocityModified = true;
        }
    }

    // COMPLETE REMAKE: Webs are now truly walkable, solid, no fall-through
    private static void tickWebStanding(ServerPlayerEntity player, PlayerPowers powers) {
        try {
            // Check in a small area around feet for webs
            BlockPos playerPos = player.getBlockPos();
            boolean foundWeb = false;
            BlockPos foundWebPos = null;
            double highestWebY = -1000;
            
            // Search 3x3x3 area below player
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -2; dy <= 1; dy++) {
                        BlockPos check = playerPos.add(dx, dy, dz);
                        try {
                            if (player.getWorld().getBlockState(check).isOf(Blocks.COBWEB)) {
                                foundWeb = true;
                                double webTop = check.getY() + 1.0; // Top of cobweb block
                                if (webTop > highestWebY) {
                                    highestWebY = webTop;
                                    foundWebPos = check;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (foundWeb && foundWebPos != null) {
                double playerY = player.getY();
                double feetY = playerY; // Feet at player Y
                double distToWebTop = feetY - highestWebY;
                
                // If player is just above web (within 1.2 blocks), snap to it
                if (distToWebTop >= -0.5 && distToWebTop <= 1.2) {
                    Vec3d vel = player.getVelocity();
                    
                    // If falling onto web, stop fall and place on top
                    if (vel.y < 0 && distToWebTop <= 0.8) {
                        // Snap to web top
                        double targetY = highestWebY + 0.05;
                        if (playerY < targetY) {
                            player.setPosition(player.getX(), targetY, player.getZ());
                        }
                        // Stop vertical motion
                        SwingPhysics.push(player, new Vec3d(vel.x * 0.85, 0, vel.z * 0.85));
                        player.fallDistance = 0;
                        powers.doubleJumpUsed = false;
                        
                        // Make it feel solid - if not sneaking, stay on web
                        if (!player.isSneaking()) {
                            // Small upward push to keep on web if slightly sinking
                            if (distToWebTop < 0.1) {
                                SwingPhysics.push(player, new Vec3d(vel.x * 0.85, 0.05, vel.z * 0.85));
                            }
                        } else {
                            // Sneaking = slowly descend through web
                            if (vel.y > -0.08) {
                                SwingPhysics.push(player, new Vec3d(vel.x * 0.7, -0.08, vel.z * 0.7));
                            }
                        }
                    } else if (distToWebTop <= 0.3 && vel.y <= 0.1) {
                        // Standing on web - minimal gravity, walkable
                        if (!player.isSneaking()) {
                            SwingPhysics.push(player, new Vec3d(vel.x * 0.9, 0, vel.z * 0.9));
                        }
                        player.fallDistance = 0;
                        powers.doubleJumpUsed = false;
                    }
                }
                
                // Always reset fall distance when near webs
                if (Math.abs(playerY - highestWebY) < 2.0) {
                    player.fallDistance = 0;
                }
            }
        } catch (Exception ignored) {}
    }

    private static void tickSpiderProtection(ServerPlayerEntity player, PlayerPowers powers) {
        try {
            BlockPos below = player.getBlockPos().down();
            boolean onWeb = false;
            try {
                if (player.getWorld().getBlockState(below).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(player.getBlockPos()).isOf(Blocks.COBWEB)) onWeb = true;
            } catch (Exception ignored) {}

            if (!onWeb && powers.stage < 1) return;

            ServerWorld world = player.getServerWorld();
            double range = powers.stage >= 4 ? 10 : 14;
            Box box = new Box(player.getX() - range, player.getY() - 6, player.getZ() - range,
                    player.getX() + range, player.getY() + 6, player.getZ() + range);
            List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box,
                    e -> e != null && e.isAlive() && e.getTarget() == player);
            int count = 0;
            for (HostileEntity hostile : hostiles) {
                if (count >= 2) break;
                String type = hostile.getType().toString().toLowerCase();
                boolean isSpider = type.contains("spider");
                if (isSpider || onWeb || powers.stage >= 2) {
                    try {
                        hostile.setTarget(null);
                        hostile.setAttacking(false);
                    } catch (Exception ignored) {}
                    count++;
                }
            }
        } catch (Exception ignored) {}
    }
}
