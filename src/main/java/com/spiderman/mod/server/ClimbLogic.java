package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.state.PlayerPowers;

/**
 * INSOMNIAC WALL CRAWL - Smooth, no jitter, no random jumps.
 * - Only climbs when actually near wall
 * - Smooth vertical/horizontal
 * - Wall run requires speed and sprint
 * - No random triggers
 */
public final class ClimbLogic {
    private ClimbLogic() {}

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

        // Only check walls when in air or sprinting with speed - prevents random triggers
        if (!player.isOnGround() && !player.isSneaking()) {
            WallCheckResult result = checkWalls(player);
            wall = result.hasWall;
            wallDir = result.dir;
            if (!wall) {
                ceiling = isSolidAbove(player);
            }
        }

        if (player.isOnGround() && player.isSprinting() && cfg.enableWallRun) {
            if (player.getVelocity().horizontalLength() > 0.55) {
                WallCheckResult result = checkWalls(player);
                if (result.hasWall) {
                    wall = true;
                    wallDir = result.dir;
                }
            }
        }

        if (!wall && !ceiling) {
            if (powers.climbing) powers.wasInAir = true;
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
            handleCeiling(player, powers, vel);
        } else {
            handleWall(player, powers, vel, wallDir);
        }

        if (player.age % 22 == 0) {
            MasteryLogic.addMastery(player, 1);
            if (powers.wallRunTicks > 22) {
                powers.stylePoints += 1;
            }
        }

        powers.wasInAir = false;
    }

    private static void handleCeiling(ServerPlayerEntity player, PlayerPowers powers, Vec3d vel) {
        if (!player.hasNoGravity()) player.setNoGravity(true);
        SpiderConfig cfg = SpiderConfig.get();

        double speed = 0.11 + Math.min(powers.stage, 4) * 0.018;
        if (player.isSprinting()) speed *= Math.min(cfg.wallRunSpeed, 1.08);

        Vec3d input = getInputVector(player);
        if (input.lengthSquared() > 0.01) {
            Vec3d move = new Vec3d(input.x * speed, 0, input.z * speed);
            move = move.add(vel.x * 0.45, 0, vel.z * 0.45);
            SwingPhysics.push(player, move);
        } else {
            SwingPhysics.push(player, new Vec3d(vel.x * 0.55, 0, vel.z * 0.55));
        }

        if (vel.y < 0) {
            SwingPhysics.push(player, new Vec3d(vel.x, 0, vel.z));
        }
    }

    private static void handleWall(ServerPlayerEntity player, PlayerPowers powers, Vec3d vel, Direction wallDir) {
        SpiderConfig cfg = SpiderConfig.get();

        if (player.isSprinting() && cfg.enableWallRun && vel.horizontalLength() > 0.18) {
            powers.wallRunTicks++;
            powers.totalWallRuns++;

            double runSpeed = Math.min(cfg.wallRunSpeed, 1.08) + Math.min(powers.stage, 4) * 0.025;
            double boost = 1.0 + (powers.wallRunTicks * 0.0012);
            if (boost > 1.15) boost = 1.15;

            Vec3d runVel = new Vec3d(vel.x * runSpeed * boost, 0.0, vel.z * runSpeed * boost);

            if (player.getPitch() < -18) {
                runVel = runVel.add(0, 0.04, 0);
            }

            SwingPhysics.push(player, runVel);

            if (powers.wallRunTicks % 35 == 0 && powers.wallRunTicks > 45) {
                powers.stylePoints += 2;
            }

            if (powers.wallRunTicks < 75) {
                powers.wallRunUntil = player.age + 35;
            }

        } else if (player.isSprinting() && cfg.enableWallRun && vel.horizontalLength() > 0.12) {
            powers.wallRunUntil = player.age + 55;
            powers.wallRunTicks = 1;
            SwingPhysics.push(player, new Vec3d(vel.x, 0.0, vel.z));
        } else {
            powers.wallRunTicks = 0;
            double climbSpeed = 0.22 + Math.min(powers.stage, 4) * 0.035;
            if (player.getPitch() < -32) climbSpeed *= 1.15;

            Vec3d input = getInputVector(player);
            double vert = climbSpeed;
            if (input.z < -0.1) vert *= 1.08;
            if (input.z > 0.1) vert *= -0.35;

            SwingPhysics.push(player, new Vec3d(vel.x * 0.28, vert, vel.z * 0.28));
        }
    }

    private static void tickDive(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        if (!cfg.enableDive) return;

        if (!player.isOnGround() && player.isSneaking() && player.getVelocity().y < 0.08) {
            if (!powers.diving) {
                powers.diving = true;
                powers.diveTicks = 0;
            }
            powers.diveTicks++;

            Vec3d vel = player.getVelocity();
            double diveSpeed = cfg.diveSpeed + Math.min(powers.stage, 4) * 0.12;
            if (powers.diveTicks > 16) diveSpeed *= 1.25;

            SwingPhysics.push(player, new Vec3d(vel.x * 0.94, -diveSpeed, vel.z * 0.94));

            if (powers.diveTicks % 12 == 0) MasteryLogic.addMastery(player, 1);
        } else {
            if (powers.diving && player.isOnGround()) {
                powers.diving = false;
                if (powers.diveTicks > 16) {
                    powers.stylePoints += 7;
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
                double control = 0.042 + Math.min(powers.stage, 4) * 0.008;
                Vec3d airMove = new Vec3d(input.x * control, 0, input.z * control);
                SwingPhysics.push(player, vel.add(airMove));
            }

            if (powers.wasInAir) {
                powers.airTime++;
                if (powers.airTime % 35 == 0 && powers.airTime > 45) {
                    powers.stylePoints += 1;
                }
            }
        } else {
            if (powers.airTime > 65) {
                powers.stylePoints += powers.airTime / 32;
            }
            powers.airTime = 0;
        }
    }

    private static Vec3d getInputVector(ServerPlayerEntity player) {
        try {
            Vec3d vel = player.getVelocity();
            if (vel.lengthSquared() < 0.001) return Vec3d.ZERO;
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
        if (player == null || !powers.hasPowers || !player.isAlive() || player.isSpectator() || player.isOnGround()) return;
        if (player.age - powers.lastWallJumpTime < 12) return;

        if (powers.swinging) {
            if (SpiderConfig.get().enableSlingshot && player.isSneaking()) {
                powers.slingshotCharging = true;
                powers.slingshotCharge++;
                return;
            }
            if (powers.slingshotCharging) {
                powers.slingshotCharging = false;
                double power = Math.min(2.3, 0.45 + powers.slingshotCharge * 0.055);
                Vec3d look = player.getRotationVector();
                Vec3d launch = new Vec3d(look.x * power, 0.38 + power * 0.22, look.z * power);
                SwingPhysics.push(player, launch);
                SwingPhysics.detach(player, powers, false);
                powers.stylePoints += 9;
                MasteryLogic.addMastery(player, 3);
                powers.slingshotCharge = 0;
                return;
            }
            SwingPhysics.detach(player, powers, true);
            return;
        }
        if (wallJump(player, powers)) return;

        if (SpiderConfig.get().experimentalDoubleJump
                && !powers.doubleJumpUsed && player.getVelocity().y < 0.18
                && player.age - powers.lastGroundedTime > 10) {
            powers.doubleJumpUsed = true;
            powers.lastWallJumpTime = player.age;
            Vec3d vel = player.getVelocity();
            double boost = 0.62 + Math.min(powers.stage, 4) * 0.05;
            if (boost > 0.82) boost = 0.82;
            SwingPhysics.push(player, new Vec3d(vel.x * 0.78, boost, vel.z * 0.78));
            player.fallDistance = 0;
            MasteryLogic.addMastery(player, 1);
            powers.stylePoints += 2;
            powers.airTricks++;
        }
    }

    public static boolean wallJump(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.climbing) return false;
        WallCheckResult wallCheck = checkWalls(player);
        if (!wallCheck.hasWall) return false;

        SpiderConfig cfg = SpiderConfig.get();
        World world = player.getWorld();
        BlockPos feet = player.getBlockPos();
        Vec3d push = new Vec3d(0, 0, 0);

        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (!world.getBlockState(feet.offset(dir)).isAir()
                    || !world.getBlockState(feet.up().offset(dir)).isAir()) {
                push = push.add(-dir.getOffsetX(), 0, -dir.getOffsetZ());
            }
        }

        if (push.lengthSquared() < 0.01) {
            Vec3d look = player.getRotationVector();
            push = new Vec3d(-look.x, 0, -look.z);
            if (push.lengthSquared() < 0.01) {
                Direction facing = player.getHorizontalFacing();
                push = new Vec3d(facing.getOffsetX(), 0, facing.getOffsetZ());
            }
        }
        if (push.lengthSquared() < 0.001) push = new Vec3d(0, 0, 1);

        push = push.normalize();

        long now = player.age;
        if (now - powers.lastWallJumpTime < 32 && cfg.enableWallJumpChain) {
            powers.wallJumpChain++;
            if (powers.wallJumpChain > 4) powers.wallJumpChain = 4;
        } else {
            powers.wallJumpChain = 1;
        }
        powers.lastWallJumpTime = now;

        double chainBoost = 1.0 + (powers.wallJumpChain * 0.09);
        double vertical = (0.68 + Math.min(powers.stage, 4) * 0.045) * chainBoost * Math.min(cfg.wallJumpBoost, 1.08);
        double horizontal = (0.75 + Math.min(powers.stage, 4) * 0.025) * chainBoost;

        SwingPhysics.push(player, new Vec3d(push.x * horizontal, vertical, push.z * horizontal));

        setClimbing(player, powers, false);
        powers.wallRunUntil = 0;
        powers.wallRunTicks = 0;

        MasteryLogic.addMastery(player, 1 + powers.wallJumpChain);
        powers.stylePoints += 2 + powers.wallJumpChain;

        if (powers.wallJumpChain >= 3) powers.stylePoints += 4;

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
