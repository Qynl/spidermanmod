package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.spiderman.mod.state.PlayerPowers;

/**
 * Wall climb / wall run / ceiling cling. Fully automatic (spider-like):
 * touch a wall mid-air and you stick; sneak to let go; sprint for wall runs.
 *
 * Bughunt:
 * - Don't climb while in water/lava
 * - Don't leave noGravity stuck when canceling
 * - Safer ceiling check (checks two blocks above for solid)
 * - Wall detection now also checks actual solid blocks around player, not just horizontalCollision flag
 * - Added mastery for wall running
 */
public final class ClimbLogic {
    private ClimbLogic() {
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        // Climbing works from stage 0: wall-crawling is the first thing a fresh spider-person can do
        if (!powers.hasPowers || powers.swinging || powers.zipTicks > 0) {
            setClimbing(player, powers, false);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying) {
            setClimbing(player, powers, false);
            return;
        }
        // Bughunt: don't climb in water/lava — causes gravity bugs
        if (player.isTouchingWater() || player.isSubmergedInWater()) {
            setClimbing(player, powers, false);
            return;
        }

        boolean wall = false;
        boolean ceiling = false;

        // More robust wall detection: check horizontalCollision AND actual solid blocks nearby
        if (!player.isOnGround() && !player.isSneaking()) {
            if (player.horizontalCollision) {
                wall = hasWallNearby(player);
            }
            if (!wall) {
                ceiling = isSolidAbove(player);
            }
        }

        if (!wall && !ceiling) {
            setClimbing(player, powers, false);
            return;
        }
        setClimbing(player, powers, true);
        player.fallDistance = 0.0f;
        Vec3d vel = player.getVelocity();
        if (ceiling) {
            // Cling to the ceiling: kill lift/fall, keep horizontal drift
            player.setNoGravity(true);
            if (vel.y > 0.0) {
                SwingPhysics.push(player, new Vec3d(vel.x, 0.0, vel.z));
            } else {
                // Gentle stick to ceiling
                SwingPhysics.push(player, new Vec3d(vel.x * 0.8, 0.0, vel.z * 0.8));
            }
        } else if (player.isSprinting() && vel.horizontalLength() > 0.18
                && player.age < powers.wallRunUntil) {
            // Wall run: hold height, boost along the wall
            SwingPhysics.push(player, new Vec3d(vel.x * 1.06, 0.0, vel.z * 1.06));
            if (player.age % 10 == 0) {
                MasteryLogic.addMastery(player, 1);
            }
        } else if (player.isSprinting() && vel.horizontalLength() > 0.18) {
            powers.wallRunUntil = player.age + 50;
            SwingPhysics.push(player, new Vec3d(vel.x, 0.0, vel.z));
        } else {
            // Climb up — slower at low stages, faster at high stages
            double climbSpeed = 0.28 + (powers.stage * 0.04);
            SwingPhysics.push(player, new Vec3d(vel.x * 0.4, climbSpeed, vel.z * 0.4));
        }
        if (player.age % 20 == 0) {
            MasteryLogic.addMastery(player, 1);
        }
    }

    private static boolean hasWallNearby(ServerPlayerEntity player) {
        World world = player.getWorld();
        BlockPos feet = player.getBlockPos();
        BlockPos head = feet.up();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (!world.getBlockState(feet.offset(dir)).isAir()
                    || !world.getBlockState(head.offset(dir)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Jump pressed mid-air: wall jump when clinging, else experimental double jump. */
    public static void tryAirAction(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.hasPowers || !player.isAlive() || player.isSpectator() || player.isOnGround()) {
            return;
        }
        if (powers.swinging) {
            SwingPhysics.detach(player, powers, true);
            return;
        }
        if (wallJump(player, powers)) {
            return;
        }
        if (com.spiderman.mod.config.SpiderConfig.get().experimentalDoubleJump
                && !powers.doubleJumpUsed && player.getVelocity().y < 0.3) {
            powers.doubleJumpUsed = true;
            Vec3d vel = player.getVelocity();
            SwingPhysics.push(player, new Vec3d(vel.x, 0.75, vel.z));
            player.fallDistance = 0.0f;
            MasteryLogic.addMastery(player, 1);
        }
    }

    /** Wall jump: push away from the solid side + up. Returns false if not climbing. */
    public static boolean wallJump(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.climbing) {
            return false;
        }
        World world = player.getWorld();
        BlockPos feet = player.getBlockPos();
        Vec3d push = new Vec3d(0.0, 0.0, 0.0);
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST}) {
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
        SwingPhysics.push(player, new Vec3d(push.x * 0.9, 0.85, push.z * 0.9));
        setClimbing(player, powers, false);
        powers.wallRunUntil = 0;
        MasteryLogic.addMastery(player, 2);
        return true;
    }

    private static boolean isSolidAbove(ServerPlayerEntity player) {
        try {
            World world = player.getWorld();
            Vec3d pos = player.getPos();
            BlockPos head = BlockPos.ofFloored(pos.x, pos.y + 1.9, pos.z);
            BlockPos aboveHead = head.up();
            // Check both head and one above — prevents sticking to single slab
            return !world.getBlockState(head).isAir() || !world.getBlockState(aboveHead).isAir();
        } catch (Exception e) {
            return false;
        }
    }

    /** Force-clears climbing state (death/disconnect cleanup). */
    public static void cancel(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || powers == null) return;
        setClimbing(player, powers, false);
    }

    private static void setClimbing(ServerPlayerEntity player, PlayerPowers powers, boolean climbing) {
        if (powers == null) return;
        if (powers.climbing && !climbing) {
            try {
                if (player != null && player.hasNoGravity()) {
                    player.setNoGravity(false);
                }
            } catch (Exception ignored) {}
        }
        powers.climbing = climbing;
    }
}
