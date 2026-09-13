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
 * ULTIMATE SERVER TICK - Handles all Spider-Man systems with style.
 * - Time + movement + style progression
 * - Advanced pull, web standing, spider protection
 * - Air time, tricks, combos
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

        // ULTIMATE PROGRESSION - Time + movement + style + air
        if (player.age % 20 == 0) {
            MasteryLogic.addMastery(player, 1);
            double horiz = player.getVelocity().horizontalLength();
            if (horiz > 0.1) MasteryLogic.addMastery(player, 1);
            if (player.isSprinting() && horiz > 0.2) {
                MasteryLogic.addMastery(player, 2);
                powers.stylePoints += 1;
            }
            if (!player.isOnGround() && player.getVelocity().y < -0.1) {
                MasteryLogic.addMastery(player, 1);
            }
            if (player.getY() > 100) {
                MasteryLogic.addMastery(player, 2);
                powers.stylePoints += 1;
            }
            if (powers.swinging) {
                MasteryLogic.addMastery(player, 2);
                if (powers.consecutiveSwings >= 3) {
                    MasteryLogic.addMastery(player, 2);
                }
            }
            if (powers.climbing) {
                MasteryLogic.addMastery(player, 1);
            }
        }
        
        if (powers.climbing && player.age % 10 == 0) {
            MasteryLogic.addMastery(player, 1);
            if (powers.wallRunTicks > 20) {
                powers.stylePoints += 1;
            }
        }
        
        if (player.isSneaking() && player.getVelocity().horizontalLength() < 0.05 && player.isOnGround()) {
            powers.focusTicks++;
            if (powers.focusTicks % 40 == 0) {
                MasteryLogic.addMastery(player, 2);
                powers.stylePoints += 1;
            }
        } else {
            powers.focusTicks = 0;
        }
        
        // Style decay if not moving stylishly
        if (player.age % 200 == 0 && !powers.swinging && !powers.climbing && player.getVelocity().length() < 0.1) {
            if (powers.stylePoints > 0) powers.stylePoints -= 1;
        }
        
        // Max combo tracking
        if (powers.combo > powers.maxCombo) {
            powers.maxCombo = powers.combo;
            if (powers.maxCombo >= 5) {
                powers.stylePoints += powers.maxCombo * 2;
            }
        }

        ComboTracker.tick(player, powers);
        if (powers.swinging) SwingPhysics.tick(player, powers);
        if (powers.zipTicks > 0) SwingPhysics.tickZip(player, powers);
        if (powers.pullTicks > 0) tickPull(player, powers);
        ClimbLogic.tick(player, powers);
        tickWebStanding(player, powers);
        tickSpiderProtection(player, powers);
        tickSlingshot(player, powers);

        if (player.isOnGround()) {
            powers.doubleJumpUsed = false;
            if (!player.isSneaking()) powers.focusTicks = 0;
            
            // Landing style
            if (powers.wasInAir && powers.airTime > 40) {
                int airBonus = powers.airTime / 20;
                powers.stylePoints += airBonus;
                if (powers.airTime > 80) {
                    MasteryLogic.addMastery(player, airBonus);
                }
            }
            powers.airTime = 0;
            powers.wasInAir = false;
            powers.lastGroundedTime = player.age;
        } else {
            powers.wasInAir = true;
            powers.airTime++;
        }
        
        if (player.age % 10 == 0) SenseLogic.tick(player, powers);
        if (powers.swinging && player.age % 20 == 0) MasteryLogic.addMastery(player, 2);
        if (player.age % 40 == 0) TransformLogic.tryStageUp(player);

        // Safety clamps
        if (powers.stage < 0) powers.stage = 0;
        if (powers.stage > 4) powers.stage = 4;
        if (powers.mastery < 0) powers.mastery = 0;
        if (powers.mastery > 100000) powers.mastery = 100000;
        if (powers.stylePoints < 0) powers.stylePoints = 0;
        if (powers.stylePoints > 10000) powers.stylePoints = 10000;

        if (!powers.swinging && powers.zipTicks == 0 && powers.pullTicks == 0 && !powers.climbing && !powers.diving) {
            try {
                if (player.hasNoGravity()) player.setNoGravity(false);
            } catch (Exception ignored) {}
        }
        if (player.getAbilities().flying && powers.climbing) {
            ClimbLogic.cancel(player, powers);
        }
    }

    private static void tickSlingshot(ServerPlayerEntity player, PlayerPowers powers) {
        if (powers.slingshotCharging) {
            powers.slingshotCharge++;
            if (powers.slingshotCharge > 60) powers.slingshotCharge = 60;
            
            if (powers.slingshotCharge % 10 == 0) {
                MasteryLogic.addMastery(player, 1);
            }
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

        if (powers.pullingPlayer) {
            Vec3d dir = toTarget.normalize();
            double speed = Math.min(2.2, 0.6 + dist * 0.1);
            if (powers.stylePoints > 200) speed *= 1.15;
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            Vec3d vel = dir.multiply(speed);
            player.fallDistance = 0;
            SwingPhysics.push(player, vel);
            if (dist < 2.0) {
                powers.stopPull();
                powers.stylePoints += 5;
            }
        } else {
            LivingEntity target = null;
            try {
                if (powers.pullTargetId != null) {
                    ServerWorld world = player.getServerWorld();
                    Box box = new Box(playerPos.x - 30, playerPos.y - 30, playerPos.z - 30,
                            playerPos.x + 30, playerPos.y + 30, playerPos.z + 30);
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
            if (d < 2.0) {
                powers.stopPull();
                // Hit!
                try {
                    target.damage(player.getWorld().getDamageSources().playerAttack(player), 4.0f);
                } catch (Exception ignored) {}
                powers.stylePoints += 8;
                return;
            }
            Vec3d dir = fromTargetToPlayer.normalize();
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            double speed = Math.min(1.8, 0.5 + d * 0.09);
            Vec3d yank = dir.multiply(speed);
            yank = new Vec3d(yank.x, Math.max(0.3, yank.y + 0.3), yank.z);
            target.setVelocity(yank.x, yank.y, yank.z);
            target.velocityModified = true;
        }
    }

    private static void tickWebStanding(ServerPlayerEntity player, PlayerPowers powers) {
        try {
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
                // Also check 2 blocks below for platform
                if (player.getWorld().getBlockState(below.down()).isOf(Blocks.COBWEB)) onWeb = true;
            } catch (Exception ignored) {}

            if (onWeb) {
                player.fallDistance = 0;
                Vec3d vel = player.getVelocity();
                if (vel != null) {
                    if (player.isSneaking()) {
                        if (vel.y < -0.1) {
                            SwingPhysics.push(player, new Vec3d(vel.x * 0.75, -0.12, vel.z * 0.75));
                        }
                    } else {
                        if (vel.y < 0) {
                            // Bouncy webs!
                            double bounce = 0.0;
                            if (vel.y < -0.5) bounce = 0.15;
                            SwingPhysics.push(player, new Vec3d(vel.x * 0.92, bounce, vel.z * 0.92));
                        }
                        // Allow jumping from webs
                        if (player.isOnGround() || onWeb) {
                            powers.doubleJumpUsed = false;
                        }
                    }
                }
                if (player.age % 20 == 0) {
                    MasteryLogic.addMastery(player, 1);
                    powers.stylePoints += 1;
                }
            }
        } catch (Exception ignored) {}
    }

    private static void tickSpiderProtection(ServerPlayerEntity player, PlayerPowers powers) {
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
            Box box = new Box(player.getX() - 20, player.getY() - 10, player.getZ() - 20,
                    player.getX() + 20, player.getY() + 10, player.getZ() + 20);
            List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box,
                    e -> e != null && e.isAlive() && e.getTarget() == player);
            for (HostileEntity hostile : hostiles) {
                String type = hostile.getType().toString().toLowerCase();
                boolean isSpider = type.contains("spider") || type.contains("cave");
                if (isSpider || onWeb || powers.stage >= 3) {
                    try {
                        hostile.setTarget(null);
                        hostile.setAttacking(false);
                        // Make spider friendly to Spider-Man at high stage
                        if (powers.stage >= 4 && isSpider) {
                            // Could add taming logic here
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
}
