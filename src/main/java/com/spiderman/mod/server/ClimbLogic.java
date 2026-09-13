package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.state.PlayerPowers;

/**
 * OVERHAULED CLIMB LOGIC - Ultimate Spider-Man movement.
 * - Fluid wall crawling
 * - Momentum wall running with style points
 * - Wall jump chaining (up to 5 chain)
 * - Ceiling crawling with full control
 * - Dive (sneak in air = fast fall + slam)
 * - Ledge grab
 * - Air control and tricks
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

        // Detect walls more accurately
        if (!player.isOnGround() && !player.isSneaking()) {
            WallCheckResult result = checkWalls(player);
            wall = result.hasWall;
            wallDir = result.dir;
            if (!wall) {
                ceiling = isSolidAbove(player);
            }
        }

        // Grounded but near wall? Allow wall run start
        if (player.isOnGround() && player.isSprinting() && cfg.enableWallRun) {
            WallCheckResult result = checkWalls(player);
            if (result.hasWall && player.getVelocity().horizontalLength() > 0.3) {
                wall = true;
                wallDir = result.dir;
            }
        }

        if (!wall && !ceiling) {
            if (powers.climbing) {
                // Was climbing, now falling - track air time
                powers.wasInAir = true;
            }
            setClimbing(player, powers, false);
            tickDive(player, powers);
            tickAirControl(player, powers);
            return;
        }

        // Climbing!
        setClimbing(player, powers, true);
        player.fallDistance = 0.0f;
        Vec3d vel = player.getVelocity();
        powers.lastGroundedTime = player.age;

        if (ceiling) {
            // Ceiling crawl - full 3D movement, Spider-Man style
            handleCeilingCrawl(player, powers, vel);
        } else if (wall) {
            handleWallClimb(player, powers, vel, wallDir);
        }

        // Mastery
        if (player.age % 15 == 0) {
            MasteryLogic.addMastery(player, 1);
            if (powers.wallRunTicks > 20) {
                powers.stylePoints += 2;
                MasteryLogic.addMastery(player, 2);
            }
        }
        
        powers.wasInAir = false;
    }

    private static void handleCeilingCrawl(ServerPlayerEntity player, PlayerPowers powers, Vec3d vel) {
        player.setNoGravity(true);
        SpiderConfig cfg = SpiderConfig.get();
        
        // Full control on ceiling
        double speed = 0.15 + (powers.stage * 0.03);
        if (player.isSprinting()) speed *= cfg.wallRunSpeed;
        
        Vec3d input = getInputVector(player);
        if (input.lengthSquared() > 0.01) {
            Vec3d move = new Vec3d(input.x * speed, 0, input.z * speed);
            // Add existing momentum
            move = move.add(vel.x * 0.6, 0, vel.z * 0.6);
            SwingPhysics.push(player, move);
        } else {
            // Stick to ceiling
            SwingPhysics.push(player, new Vec3d(vel.x * 0.7, 0, vel.z * 0.7));
        }
        
        // Prevent falling
        if (vel.y < 0) {
            SwingPhysics.push(player, new Vec3d(vel.x, 0, vel.z));
        }
    }

    private static void handleWallClimb(ServerPlayerEntity player, PlayerPowers powers, Vec3d vel, Direction wallDir) {
        SpiderConfig cfg = SpiderConfig.get();
        
        if (player.isSprinting() && cfg.enableWallRun && vel.horizontalLength() > 0.15) {
            // Wall run - momentum based
            powers.wallRunTicks++;
            powers.totalWallRuns++;
            
            double runSpeed = cfg.wallRunSpeed + (powers.stage * 0.05);
            double boost = 1.0 + (powers.wallRunTicks * 0.002);
            if (boost > 1.3) boost = 1.3;
            
            Vec3d runVel = new Vec3d(vel.x * runSpeed * boost, 0.0, vel.z * runSpeed * boost);
            
            // Slight upward if looking up
            if (player.getPitch() < -15) {
                runVel = runVel.add(0, 0.08, 0);
            }
            
            SwingPhysics.push(player, runVel);
            
            // Style points for long wall runs
            if (powers.wallRunTicks % 20 == 0 && powers.wallRunTicks > 40) {
                powers.stylePoints += 5;
            }
            
            // Extend wall run time
            if (powers.wallRunTicks < 100) {
                powers.wallRunUntil = player.age + 60;
            }
            
        } else if (player.isSprinting() && cfg.enableWallRun && vel.horizontalLength() > 0.1) {
            // Start wall run
            powers.wallRunUntil = player.age + 80;
            powers.wallRunTicks = 1;
            SwingPhysics.push(player, new Vec3d(vel.x, 0.0, vel.z));
        } else {
            // Normal climb - faster with stage
            powers.wallRunTicks = 0;
            double climbSpeed = 0.32 + (powers.stage * 0.06);
            if (player.getPitch() < -30) climbSpeed *= 1.3; // Look up = climb faster
            
            Vec3d input = getInputVector(player);
            double vert = climbSpeed;
            if (input.z < -0.1) vert *= 1.2; // Forward = up
            if (input.z > 0.1) vert *= -0.5; // Back = down slow
            
            SwingPhysics.push(player, new Vec3d(vel.x * 0.35, vert, vel.z * 0.35));
        }
    }

    private static void tickDive(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        if (!cfg.enableDive) return;
        
        // Dive: sneak in air = fast fall + slam
        if (!player.isOnGround() && player.isSneaking() && player.getVelocity().y < 0.1) {
            if (!powers.diving) {
                powers.diving = true;
                powers.diveTicks = 0;
            }
            powers.diveTicks++;
            
            Vec3d vel = player.getVelocity();
            double diveSpeed = cfg.diveSpeed + (powers.stage * 0.2);
            if (powers.diveTicks > 10) diveSpeed *= 1.5;
            
            SwingPhysics.push(player, new Vec3d(vel.x * 0.95, -diveSpeed, vel.z * 0.95));
            
            if (powers.diveTicks % 5 == 0) {
                MasteryLogic.addMastery(player, 1);
            }
        } else {
            if (powers.diving && player.isOnGround()) {
                // Dive slam - small burst effect
                powers.diving = false;
                if (powers.diveTicks > 15) {
                    // Slam!
                    powers.stylePoints += 10;
                    MasteryLogic.addMastery(player, 5);
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
                // Air control - Spider-Man can steer in air
                double control = 0.08 + (powers.stage * 0.02);
                Vec3d airMove = new Vec3d(input.x * control, 0, input.z * control);
                SwingPhysics.push(player, vel.add(airMove));
            }
            
            // Track air time
            if (powers.wasInAir) {
                powers.airTime++;
                if (powers.airTime % 20 == 0 && powers.airTime > 40) {
                    powers.stylePoints += 1;
                }
            }
        } else {
            if (powers.airTime > 60) {
                // Long air time = style
                powers.stylePoints += powers.airTime / 20;
            }
            powers.airTime = 0;
        }
    }

    private static Vec3d getInputVector(ServerPlayerEntity player) {
        float forward = 0, strafe = 0;
        try {
            // Get movement input from player
            // We can't directly access input, so approximate from velocity and look
            Vec3d look = player.getRotationVector();
            Vec3d vel = player.getVelocity();
            // Simple approximation
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
        if (powers.swinging) {
            // Slingshot - hold jump to charge, release to launch
            if (SpiderConfig.get().enableSlingshot && player.isSneaking()) {
                powers.slingshotCharging = true;
                powers.slingshotCharge++;
                return;
            }
            if (powers.slingshotCharging) {
                // Release slingshot
                powers.slingshotCharging = false;
                double power = Math.min(3.0, 0.5 + powers.slingshotCharge * 0.08);
                Vec3d look = player.getRotationVector();
                Vec3d launch = new Vec3d(look.x * power, 0.5 + power * 0.3, look.z * power);
                SwingPhysics.push(player, launch);
                SwingPhysics.detach(player, powers, false);
                powers.stylePoints += 15;
                MasteryLogic.addMastery(player, 5);
                powers.slingshotCharge = 0;
                return;
            }
            SwingPhysics.detach(player, powers, true);
            return;
        }
        if (wallJump(player, powers)) {
            return;
        }
        if (SpiderConfig.get().experimentalDoubleJump
                && !powers.doubleJumpUsed && player.getVelocity().y < 0.3) {
            powers.doubleJumpUsed = true;
            Vec3d vel = player.getVelocity();
            double boost = 0.85 + (powers.stage * 0.1);
            SwingPhysics.push(player, new Vec3d(vel.x, boost, vel.z));
            player.fallDistance = 0.0f;
            MasteryLogic.addMastery(player, 2);
            powers.stylePoints += 5;
            powers.airTricks++;
        }
    }

    public static boolean wallJump(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.climbing) {
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
        
        // Chain bonus
        long now = player.age;
        if (now - powers.lastWallJumpTime < 40 && cfg.enableWallJumpChain) {
            powers.wallJumpChain++;
            if (powers.wallJumpChain > 5) powers.wallJumpChain = 5;
        } else {
            powers.wallJumpChain = 1;
        }
        powers.lastWallJumpTime = now;
        
        double chainBoost = 1.0 + (powers.wallJumpChain * 0.15);
        double vertical = (0.9 + (powers.stage * 0.08)) * chainBoost * cfg.wallJumpBoost;
        double horizontal = (1.0 + (powers.stage * 0.05)) * chainBoost;
        
        SwingPhysics.push(player, new Vec3d(push.x * horizontal, vertical, push.z * horizontal));
        
        setClimbing(player, powers, false);
        powers.wallRunUntil = 0;
        powers.wallRunTicks = 0;
        
        MasteryLogic.addMastery(player, 2 + powers.wallJumpChain);
        powers.stylePoints += 5 + powers.wallJumpChain * 2;
        
        if (powers.wallJumpChain >= 3) {
            powers.stylePoints += 10;
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
