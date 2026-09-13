package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.state.PlayerPowers;

/**
 * OVERHAULED CLIMB LOGIC - Fixed for no lag and no random jumps at high stage.
 */
public final class ClimbLogic {
    private ClimbLogic() {
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (!powers.hasPowers || powers.swinging || powers.zipTicks > 0 || powers.diving) {
            setClimbing(player, powers, false);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying) {
            setClimbing(player, powers, false);
            return;
        }
        if (player.isTouchingWater() || player.isSubmergedInWater()) {
            setClimbing(player, powers, false);
            tickDive(player, powers);
            return;
        }

        SpiderConfig cfg = SpiderConfig.get();
        boolean wall = false;
        boolean ceiling = false;
        Direction wallDir = null;

        // FIXED: Only check walls when not on ground or when sprinting, reduces lag
        if (!player.isOnGround() && !player.isSneaking()) {
            WallCheckResult result = checkWalls(player);
            wall = result.hasWall;
            wallDir = result.dir;
            if (!wall) {
                ceiling = isSolidAbove(player);
            }
        }

        if (player.isOnGround() && player.isSprinting() && cfg.enableWallRun) {
            // FIXED: Require more speed to start wall run, prevents random triggers
            if (player.getVelocity().horizontalLength() > 0.5) {
                WallCheckResult result = checkWalls(player);
                if (result.hasWall) {
                    wall = true;
                    wallDir = result.dir;
                }
            }
        }

        if (!wall && !ceiling) {
            if (powers.climbing) {
                powers.wasInAir = true;
            }
            setClimbing(player, powers, false);
            tickDive(player, powers);
            tickAirControl(player, powers);
            return;
        }

        setClimbing(player, powers, true);
        player.fallDistance = 0.0f;
        Vec3d vel = player.getVelocity();
        powers.lastGroundedTime = player.age;

        if (ceiling) {
            handleCeilingCrawl(player, powers, vel);
        } else if (wall) {
            handleWallClimb(player, powers, vel, wallDir);
        }

        // FIXED: Less frequent mastery to reduce lag
        if (player.age % 20 == 0) {
            MasteryLogic.addMastery(player, 1);
            if (powers.wallRunTicks > 20) {
                powers.stylePoints += 1;
                MasteryLogic.addMastery(player, 1);
            }
        }
        
        powers.wasInAir = false;
    }

    private static void handleCeilingCrawl(ServerPlayerEntity player, PlayerPowers powers, Vec3d vel) {
        // FIXED: Don't set noGravity every tick if already set, reduces lag
        if (!player.hasNoGravity()) {
            player.setNoGravity(true);
        }
        SpiderConfig cfg = SpiderConfig.get();
        
        // FIXED: Reduced speed at high stage
        double speed = 0.12 + (Math.min(powers.stage, 3) * 0.02);
        if (player.isSprinting()) speed *= Math.min(cfg.wallRunSpeed, 1.1);
        
        Vec3d input = getInputVector(player);
        if (input.lengthSquared() > 0.01) {
            Vec3d move = new Vec3d(input.x * speed, 0, input.z * speed);
            move = move.add(vel.x * 0.5, 0, vel.z * 0.5);
            SwingPhysics.push(player, move);
        } else {
            SwingPhysics.push(player, new Vec3d(vel.x * 0.6, 0, vel.z * 0.6));
        }
        
        if (vel.y < 0) {
            SwingPhysics.push(player, new Vec3d(vel.x, 0, vel.z));
        }
    }

    private static void handleWallClimb(ServerPlayerEntity player, PlayerPowers powers, Vec3d vel, Direction wallDir) {
        SpiderConfig cfg = SpiderConfig.get();
        
        if (player.isSprinting() && cfg.enableWallRun && vel.horizontalLength() > 0.15) {
            powers.wallRunTicks++;
            powers.totalWallRuns++;
            
            // FIXED: Reduced boost at high stage, was causing extreme speeds and lag
            double runSpeed = Math.min(cfg.wallRunSpeed, 1.1) + (Math.min(powers.stage, 3) * 0.03);
            double boost = 1.0 + (powers.wallRunTicks * 0.0015);
            if (boost > 1.2) boost = 1.2;
            
            Vec3d runVel = new Vec3d(vel.x * runSpeed * boost, 0.0, vel.z * runSpeed * boost);
            
            if (player.getPitch() < -15) {
                runVel = runVel.add(0, 0.05, 0);
            }
            
            SwingPhysics.push(player, runVel);
            
            if (powers.wallRunTicks % 30 == 0 && powers.wallRunTicks > 40) {
                powers.stylePoints += 3;
            }
            
            if (powers.wallRunTicks < 80) {
                powers.wallRunUntil = player.age + 40;
            }
            
        } else if (player.isSprinting() && cfg.enableWallRun && vel.horizontalLength() > 0.1) {
            powers.wallRunUntil = player.age + 60;
            powers.wallRunTicks = 1;
            SwingPhysics.push(player, new Vec3d(vel.x, 0.0, vel.z));
        } else {
            powers.wallRunTicks = 0;
            // FIXED: Reduced climb speed at high stage
            double climbSpeed = 0.25 + (Math.min(powers.stage, 3) * 0.04);
            if (player.getPitch() < -30) climbSpeed *= 1.2;
            
            Vec3d input = getInputVector(player);
            double vert = climbSpeed;
            if (input.z < -0.1) vert *= 1.1;
            if (input.z > 0.1) vert *= -0.4;
            
            SwingPhysics.push(player, new Vec3d(vel.x * 0.3, vert, vel.z * 0.3));
        }
    }

    private static void tickDive(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        if (!cfg.enableDive) return;
        
        if (!player.isOnGround() && player.isSneaking() && player.getVelocity().y < 0.1) {
            if (!powers.diving) {
                powers.diving = true;
                powers.diveTicks = 0;
            }
            powers.diveTicks++;
            
            Vec3d vel = player.getVelocity();
            // FIXED: Reduced dive speed at high stage
            double diveSpeed = cfg.diveSpeed + (Math.min(powers.stage, 3) * 0.15);
            if (powers.diveTicks > 15) diveSpeed *= 1.3; // Was 1.5, now 1.3
            
            SwingPhysics.push(player, new Vec3d(vel.x * 0.95, -diveSpeed, vel.z * 0.95));
            
            if (powers.diveTicks % 10 == 0) {
                MasteryLogic.addMastery(player, 1);
            }
        } else {
            if (powers.diving && player.isOnGround()) {
                powers.diving = false;
                if (powers.diveTicks > 15) {
                    powers.stylePoints += 8;
                    MasteryLogic.addMastery(player, 3);
                }
            } else if (powers.diving && !player.isSneaking()) {
                powers.diving = false;
            }
            powers.diveTicks = 0;
        }
    }

    private static void tickAirControl(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        if (!cfg.enableAirTricks) return;
        
        if (!player.isOnGround() && !powers.swinging && powers.zipTicks == 0) {
            Vec3d vel = player.getVelocity();
            Vec3d input = getInputVector(player);
            
            if (input.lengthSquared() > 0.01) {
                // FIXED: Reduced air control at high stage
                double control = 0.05 + (Math.min(powers.stage, 3) * 0.01);
                Vec3d airMove = new Vec3d(input.x * control, 0, input.z * control);
                SwingPhysics.push(player, vel.add(airMove));
            }
            
            if (powers.wasInAir) {
                powers.airTime++;
                if (powers.airTime % 30 == 0 && powers.airTime > 40) {
                    powers.stylePoints += 1;
                }
            }
        } else {
            if (powers.airTime > 60) {
                powers.stylePoints += powers.airTime / 30;
            }
            powers.airTime = 0;
        }
    }

    private static Vec3d getInputVector(ServerPlayerEntity player) {
        try {
            Vec3d vel = player.getVelocity();
            return new Vec3d(vel.x, 0, vel.z).normalize();
        } catch (Exception e) {
            return Vec3d.ZERO;
        }
    }

    private static class WallCheckResult {
        boolean hasWall;
        Direction dir;
    }

    private static WallCheckResult checkWalls(ServerPlayerEntity player) {
        WallCheckResult result = new WallCheckResult();
        World world = player.getWorld();
        BlockPos feet = player.getBlockPos();
        BlockPos head = feet.up();
        BlockPos head2 = feet.up(2);
        
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            boolean solid = !world.getBlockState(feet.offset(dir)).isAir()
                    || !world.getBlockState(head.offset(dir)).isAir()
                    || !world.getBlockState(head2.offset(dir)).isAir();
            if (solid) {
                result.hasWall = true;
                result.dir = dir;
                return result;
            }
        }
        return result;
    }

    public static void tryAirAction(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.hasPowers || !player.isAlive() || player.isSpectator() || player.isOnGround()) {
            return;
        }
        // FIXED: Add cooldown to prevent spam and random jumps
        if (player.age - powers.lastWallJumpTime < 10) {
            return; // Prevent spam - was causing random jumps
        }
        
        if (powers.swinging) {
            if (SpiderConfig.get().enableSlingshot && player.isSneaking()) {
                powers.slingshotCharging = true;
                powers.slingshotCharge++;
                return;
            }
            if (powers.slingshotCharging) {
                powers.slingshotCharging = false;
                double power = Math.min(2.5, 0.5 + powers.slingshotCharge * 0.06);
                Vec3d look = player.getRotationVector();
                Vec3d launch = new Vec3d(look.x * power, 0.4 + power * 0.25, look.z * power);
                SwingPhysics.push(player, launch);
                SwingPhysics.detach(player, powers, false);
                powers.stylePoints += 10;
                MasteryLogic.addMastery(player, 3);
                powers.slingshotCharge = 0;
                return;
            }
            SwingPhysics.detach(player, powers, true);
            return;
        }
        if (wallJump(player, powers)) {
            return;
        }
        // FIXED: Double jump now requires more deliberate input and has cooldown, prevents random jumps
        if (SpiderConfig.get().experimentalDoubleJump
                && !powers.doubleJumpUsed && player.getVelocity().y < 0.2
                && player.age - powers.lastGroundedTime > 8) { // Must be in air for at least 8 ticks
            powers.doubleJumpUsed = true;
            powers.lastWallJumpTime = player.age; // Use same cooldown tracker
            Vec3d vel = player.getVelocity();
            // FIXED: Reduced boost at high stage - was 0.85+stage*0.1=1.25 at stage4, now capped
            double boost = 0.65 + (Math.min(powers.stage, 3) * 0.06);
            if (boost > 0.85) boost = 0.85;
            SwingPhysics.push(player, new Vec3d(vel.x * 0.8, boost, vel.z * 0.8));
            player.fallDistance = 0.0f;
            MasteryLogic.addMastery(player, 1);
            powers.stylePoints += 3;
            powers.airTricks++;
        }
    }

    public static boolean wallJump(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.climbing) {
            return false;
        }
        // FIXED: Must be actually near wall, not just climbing flag
        WallCheckResult wallCheck = checkWalls(player);
        if (!wallCheck.hasWall) {
            return false;
        }
        
        SpiderConfig cfg = SpiderConfig.get();
        World world = player.getWorld();
        BlockPos feet = player.getBlockPos();
        Vec3d push = new Vec3d(0.0, 0.0, 0.0);
        
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (!world.getBlockState(feet.offset(dir)).isAir()
                    || !world.getBlockState(feet.up().offset(dir)).isAir()) {
                push = push.add(-dir.getOffsetX(), 0.0, -dir.getOffsetZ());
            }
        }
        
        if (push.lengthSquared() < 0.01) {
            Vec3d look = player.getRotationVector();
            push = new Vec3d(-look.x, 0.0, -look.z);
            if (push.lengthSquared() < 0.01) {
                Direction facing = player.getHorizontalFacing();
                push = new Vec3d(facing.getOffsetX(), 0.0, facing.getOffsetZ());
            }
        }
        if (push.lengthSquared() < 0.001) {
            push = new Vec3d(0, 0, 1);
        }
        
        push = push.normalize();
        
        long now = player.age;
        if (now - powers.lastWallJumpTime < 30 && cfg.enableWallJumpChain) { // Was 40, now 30, requires more deliberate
            powers.wallJumpChain++;
            if (powers.wallJumpChain > 4) powers.wallJumpChain = 4; // Was 5, now 4
        } else {
            powers.wallJumpChain = 1;
        }
        powers.lastWallJumpTime = now;
        
        // FIXED: Reduced boosts at high stage
        double chainBoost = 1.0 + (powers.wallJumpChain * 0.1); // Was 0.15
        double vertical = (0.7 + (Math.min(powers.stage, 3) * 0.05)) * chainBoost * Math.min(cfg.wallJumpBoost, 1.1);
        double horizontal = (0.8 + (Math.min(powers.stage, 3) * 0.03)) * chainBoost;
        
        SwingPhysics.push(player, new Vec3d(push.x * horizontal, vertical, push.z * horizontal));
        
        setClimbing(player, powers, false);
        powers.wallRunUntil = 0;
        powers.wallRunTicks = 0;
        
        MasteryLogic.addMastery(player, 1 + powers.wallJumpChain);
        powers.stylePoints += 3 + powers.wallJumpChain;
        
        if (powers.wallJumpChain >= 3) {
            powers.stylePoints += 5;
        }
        
        return true;
    }

    private static boolean isSolidAbove(ServerPlayerEntity player) {
        try {
            World world = player.getWorld();
            Vec3d pos = player.getPos();
            BlockPos head = BlockPos.ofFloored(pos.x, pos.y + 1.9, pos.z);
            BlockPos aboveHead = head.up();
            BlockPos above2 = head.up(2);
            return !world.getBlockState(head).isAir() 
                || !world.getBlockState(aboveHead).isAir()
                || !world.getBlockState(above2).isAir();
        } catch (Exception e) {
            return false;
        }
    }

    public static void cancel(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || powers == null) return;
        setClimbing(player, powers, false);
        powers.diving = false;
        powers.slingshotCharging = false;
    }

    private static void setClimbing(ServerPlayerEntity player, PlayerPowers powers, boolean climbing) {
        if (powers == null) return;
        if (powers.climbing && !climbing) {
            try {
                if (player != null && player.hasNoGravity()) {
                    player.setNoGravity(false);
                }
            } catch (Exception ignored) {}
            powers.wallRunTicks = 0;
        }
        powers.climbing = climbing;
    }
}
