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
 * ULTIMATE SERVER TICK - Fixed for no lag at high stage.
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

        // FIXED: Reduced mastery spam at high stage to prevent lag
        if (player.age % 20 == 0) {
            MasteryLogic.addMastery(player, 1);
            double horiz = player.getVelocity().horizontalLength();
            if (horiz > 0.15) MasteryLogic.addMastery(player, 1);
            if (player.isSprinting() && horiz > 0.25) {
                MasteryLogic.addMastery(player, 1);
                if (player.age % 60 == 0) powers.stylePoints += 1;
            }
            if (!player.isOnGround() && player.getVelocity().y < -0.15) {
                if (player.age % 40 == 0) MasteryLogic.addMastery(player, 1);
            }
            if (player.getY() > 100) {
                if (player.age % 60 == 0) {
                    MasteryLogic.addMastery(player, 1);
                    powers.stylePoints += 1;
                }
            }
            if (powers.swinging) {
                MasteryLogic.addMastery(player, 1);
            }
            if (powers.climbing) {
                if (player.age % 40 == 0) MasteryLogic.addMastery(player, 1);
            }
        }
        
        if (powers.climbing && player.age % 20 == 0) {
            if (player.age % 40 == 0) MasteryLogic.addMastery(player, 1);
            if (powers.wallRunTicks > 30 && player.age % 60 == 0) {
                powers.stylePoints += 1;
            }
        }
        
        if (player.isSneaking() && player.getVelocity().horizontalLength() < 0.05 && player.isOnGround()) {
            powers.focusTicks++;
            if (powers.focusTicks % 60 == 0) {
                MasteryLogic.addMastery(player, 1);
                if (player.age % 120 == 0) powers.stylePoints += 1;
            }
        } else {
            powers.focusTicks = 0;
        }
        
        // FIXED: Style decay less aggressive, less frequent
        if (player.age % 400 == 0 && !powers.swinging && !powers.climbing && player.getVelocity().length() < 0.08) {
            if (powers.stylePoints > 0) powers.stylePoints -= 1;
        }
        
        if (powers.combo > powers.maxCombo) {
            powers.maxCombo = powers.combo;
            if (powers.maxCombo >= 5 && player.age % 100 == 0) {
                powers.stylePoints += powers.maxCombo;
            }
        }

        ComboTracker.tick(player, powers);
        if (powers.swinging) SwingPhysics.tick(player, powers);
        if (powers.zipTicks > 0) SwingPhysics.tickZip(player, powers);
        if (powers.pullTicks > 0) tickPull(player, powers);
        ClimbLogic.tick(player, powers);
        tickWebStanding(player, powers);
        // FIXED: Spider protection less frequent at high stage to reduce lag
        if (player.age % (powers.stage >= 4 ? 40 : 20) == 0) {
            tickSpiderProtection(player, powers);
        }
        tickSlingshot(player, powers);

        if (player.isOnGround()) {
            powers.doubleJumpUsed = false;
            if (!player.isSneaking()) powers.focusTicks = 0;
            
            if (powers.wasInAir && powers.airTime > 40) {
                int airBonus = powers.airTime / 30;
                powers.stylePoints += airBonus;
                if (powers.airTime > 100 && player.age % 60 == 0) {
                    MasteryLogic.addMastery(player, airBonus / 2);
                }
            }
            powers.airTime = 0;
            powers.wasInAir = false;
            powers.lastGroundedTime = player.age;
        } else {
            powers.wasInAir = true;
            powers.airTime++;
        }
        
        // FIXED: Sense less frequent at high stage - was every 10 ticks, now 20 at stage 4
        if (player.age % (powers.stage >= 4 ? 20 : 10) == 0) SenseLogic.tick(player, powers);
        if (powers.swinging && player.age % 30 == 0) MasteryLogic.addMastery(player, 1);
        if (player.age % 60 == 0) TransformLogic.tryStageUp(player);

        if (powers.stage < 0) powers.stage = 0;
        if (powers.stage > 4) powers.stage = 4;
        if (powers.mastery < 0) powers.mastery = 0;
        if (powers.mastery > 100000) powers.mastery = 100000;
        if (powers.stylePoints < 0) powers.stylePoints = 0;
        if (powers.stylePoints > 5000) powers.stylePoints = 5000; // Was 10000, now 5000

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
            if (powers.slingshotCharge > 50) powers.slingshotCharge = 50;
            
            if (powers.slingshotCharge % 20 == 0) {
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
            double speed = Math.min(1.8, 0.5 + dist * 0.08);
            if (powers.stylePoints > 200) speed *= 1.08;
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            Vec3d vel = dir.multiply(speed);
            player.fallDistance = 0;
            SwingPhysics.push(player, vel);
            if (dist < 2.0) {
                powers.stopPull();
                powers.stylePoints += 3;
            }
        } else {
            LivingEntity target = null;
            try {
                if (powers.pullTargetId != null) {
                    ServerWorld world = player.getServerWorld();
                    Box box = new Box(playerPos.x - 20, playerPos.y - 20, playerPos.z - 20,
                            playerPos.x + 20, playerPos.y + 20, playerPos.z + 20);
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
                try {
                    target.damage(player.getWorld().getDamageSources().playerAttack(player), 3.0f);
                } catch (Exception ignored) {}
                powers.stylePoints += 5;
                return;
            }
            Vec3d dir = fromTargetToPlayer.normalize();
            if (!Double.isFinite(dir.x)) dir = new Vec3d(0, 0, 1);
            double speed = Math.min(1.4, 0.4 + d * 0.07);
            Vec3d yank = dir.multiply(speed);
            yank = new Vec3d(yank.x, Math.max(0.2, yank.y + 0.2), yank.z);
            target.setVelocity(yank.x, yank.y, yank.z);
            target.velocityModified = true;
        }
    }

    private static void tickWebStanding(ServerPlayerEntity player, PlayerPowers powers) {
        try {
            BlockPos feet = player.getBlockPos();
            BlockPos below = feet.down();
            BlockPos at = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ());

            boolean onWeb = false;
            try {
                if (player.getWorld().getBlockState(below).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(feet).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(at).isOf(Blocks.COBWEB)) onWeb = true;
                if (player.getWorld().getBlockState(below.down()).isOf(Blocks.COBWEB)) onWeb = true;
            } catch (Exception ignored) {}

            if (onWeb) {
                player.fallDistance = 0;
                Vec3d vel = player.getVelocity();
                if (vel != null) {
                    if (player.isSneaking()) {
                        if (vel.y < -0.08) {
                            SwingPhysics.push(player, new Vec3d(vel.x * 0.7, -0.08, vel.z * 0.7));
                        }
                    } else {
                        if (vel.y < 0) {
                            double bounce = 0.0;
                            if (vel.y < -0.4) bounce = 0.1;
                            SwingPhysics.push(player, new Vec3d(vel.x * 0.88, bounce, vel.z * 0.88));
                        }
                        if (player.isOnGround() || onWeb) {
                            powers.doubleJumpUsed = false;
                        }
                    }
                }
                if (player.age % 40 == 0) {
                    MasteryLogic.addMastery(player, 1);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void tickSpiderProtection(ServerPlayerEntity player, PlayerPowers powers) {
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
            // FIXED: Smaller box at high stage to reduce lag
            double range = powers.stage >= 4 ? 12 : 16;
            Box box = new Box(player.getX() - range, player.getY() - 8, player.getZ() - range,
                    player.getX() + range, player.getY() + 8, player.getZ() + range);
            List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box,
                    e -> e != null && e.isAlive() && e.getTarget() == player);
            // FIXED: Limit to 3 hostiles max
            int count = 0;
            for (HostileEntity hostile : hostiles) {
                if (count >= 3) break;
                String type = hostile.getType().toString().toLowerCase();
                boolean isSpider = type.contains("spider") || type.contains("cave");
                if (isSpider || onWeb || powers.stage >= 3) {
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
