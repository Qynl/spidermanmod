package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import com.spiderman.mod.ModSounds;
import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;

/**
 * Improved momentum-based web swinging — actually fun to swing now.
 * - Longer range (80 blocks default)
 * - Thick white line via StrandRenderer
 * - Real pendulum with pumping (look up/down to reel)
 * - Sprint boost, momentum conservation, faster max speed
 * - Better anchor finding (5 upward + fallback + side sweeps)
 * - Fling launch on release
 */
public final class SwingPhysics {
    private SwingPhysics() {
    }

    public static Vec3d findAnchor(ServerPlayerEntity player, double range) {
        if (player == null) return null;
        try {
            World world = player.getWorld();
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVector();
            if (look.lengthSquared() < 0.001) look = new Vec3d(0, 0.5, 1).normalize();

            // Upward-biased sweep for realistic high anchors
            for (int i = 0; i < 6; i++) {
                Vec3d dir = look.add(0.0, 0.3 + i * 0.35, 0.0);
                if (dir.lengthSquared() < 0.001) continue;
                dir = dir.normalize();
                Vec3d to = eye.add(dir.x * range, dir.y * range, dir.z * range);
                BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                    return hit.getPos();
                }
            }
            // Side sweeps for when looking horizontally
            for (int side = -1; side <= 1; side += 2) {
                Vec3d dir = look.add(side * 0.5, 0.4, 0.0).normalize();
                Vec3d to = eye.add(dir.x * range, dir.y * range, dir.z * range);
                BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                    return hit.getPos();
                }
            }
            // Fallback straight
            Vec3d to = eye.add(look.x * range, look.y * range, look.z * range);
            BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                return hit.getPos();
            }
            // Last resort: straight up (for city swinging)
            Vec3d up = new Vec3d(0, 1, 0);
            Vec3d toUp = eye.add(0, range, 0);
            BlockHitResult hitUp = world.raycast(new RaycastContext(eye, toUp,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (hitUp.getType() != HitResult.Type.MISS && isValidAnchor(hitUp.getPos(), eye, range)) {
                return hitUp.getPos();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isValidAnchor(Vec3d anchor, Vec3d eye, double range) {
        if (anchor == null) return false;
        if (!Double.isFinite(anchor.x)) return false;
        double dist = anchor.distanceTo(eye);
        return dist >= 3.0 && dist <= range * 1.1;
    }

    private static boolean isValidAnchor(Vec3d anchor, Vec3d eye) {
        return isValidAnchor(anchor, eye, SpiderConfig.get().swingRange);
    }

    public static void attach(ServerPlayerEntity player, PlayerPowers powers, Vec3d anchor, int hand) {
        if (player == null || powers == null || anchor == null) return;
        try {
            powers.zipTicks = 0;
            powers.pullTicks = 0;
            try {
                player.setNoGravity(false);
            } catch (Exception ignored) {}
            powers.swinging = true;
            powers.swingX = anchor.x;
            powers.swingY = anchor.y;
            powers.swingZ = anchor.z;
            double dist = anchor.distanceTo(player.getPos());
            if (!Double.isFinite(dist)) dist = 15.0;
            // Longer rope for more swing arc
            powers.ropeLen = Math.max(5.0, Math.min(dist * 0.95, SpiderConfig.get().swingRange * 1.1));
            powers.swingHand = hand == 1 ? 1 : 0;
            player.playSound(ModSounds.WEB_SHOT, 1.0f, 1.15f);
            player.playSound(ModSounds.WEB_ZIP, 0.7f, 1.0f);
            ServerNetworking.sendSwing(player, true, anchor.x, anchor.y, anchor.z, powers.swingHand, 0);
        } catch (Exception e) {
            powers.stopSwing();
        }
    }

    public static void detach(ServerPlayerEntity player, PlayerPowers powers, boolean fling) {
        if (player == null || powers == null) return;
        if (!powers.swinging) {
            try {
                if (player.hasNoGravity()) player.setNoGravity(false);
            } catch (Exception ignored) {}
            return;
        }
        powers.stopSwing();
        try {
            player.setNoGravity(false);
        } catch (Exception ignored) {}
        ServerNetworking.sendSwingOff(player);
        if (fling) {
            try {
                Vec3d vel = player.getVelocity();
                if (vel == null) vel = new Vec3d(0, 0.3, 0);
                // Stronger fling for real Spider-Man launch
                vel = vel.multiply(1.35).add(0.0, 0.32, 0.0);
                if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.5, 0);
                if (vel.length() > 4.5) vel = vel.normalize().multiply(4.5);
                push(player, vel);
            } catch (Exception ignored) {
                push(player, new Vec3d(0, 0.5, 0));
            }
        }
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || powers == null) return;
        try {
            Vec3d pos = player.getPos();
            if (pos == null) {
                detach(player, powers, false);
                return;
            }
            Vec3d anchor = new Vec3d(powers.swingX, powers.swingY, powers.swingZ);
            if (!Double.isFinite(anchor.x)) {
                detach(player, powers, false);
                return;
            }
            Vec3d vel = player.getVelocity();
            if (vel == null) vel = new Vec3d(0, 0, 0);

            // Gravity
            vel = vel.add(0.0, -0.09, 0.0);

            // Rope control: look up reels in faster, look down lets out faster — real Spider-Man pumping
            float pitch = player.getPitch();
            if (pitch < -20.0f) {
                // Looking up — reel in fast for speed boost
                powers.ropeLen = Math.max(4.0, powers.ropeLen - 0.35);
                // Add slight upward boost when reeling at bottom of swing
                Vec3d toAnchor = anchor.subtract(pos);
                if (toAnchor.y > 0 && vel.horizontalLength() > 0.5) {
                    vel = vel.add(0.0, 0.04, 0.0);
                }
            } else if (pitch < -8.0f) {
                powers.ropeLen = Math.max(4.0, powers.ropeLen - 0.18);
            } else if (pitch > 30.0f) {
                // Looking down — let out fast to gain arc
                powers.ropeLen = Math.min(SpiderConfig.get().swingRange * 1.2, powers.ropeLen + 0.45);
            } else if (pitch > 15.0f) {
                powers.ropeLen = Math.min(SpiderConfig.get().swingRange * 1.2, powers.ropeLen + 0.22);
            }

            // Sprint boost — hold sprint to go faster
            if (player.isSprinting()) {
                vel = vel.multiply(1.02);
            }

            // Clamp rope
            powers.ropeLen = Math.max(4.0, Math.min(powers.ropeLen, SpiderConfig.get().swingRange * 1.3));

            Vec3d r = pos.add(vel).subtract(anchor);
            double dist = r.length();
            if (!Double.isFinite(dist)) {
                detach(player, powers, false);
                return;
            }
            if (dist > SpiderConfig.get().swingRange * 3.5) {
                detach(player, powers, false);
                return;
            }

            if (dist > powers.ropeLen && dist > 0.001) {
                Vec3d n = r.normalize();
                if (Double.isFinite(n.x)) {
                    double radial = vel.dotProduct(n);
                    if (radial > 0.0) {
                        // Remove outward radial velocity (constraint)
                        vel = vel.subtract(n.x * radial, n.y * radial, n.z * radial);
                    }
                    // Spring back to rope sphere — stronger spring for tighter feel
                    double over = dist - powers.ropeLen;
                    double spring = 0.18;
                    // Stronger spring when reeling in
                    if (pitch < -10) spring = 0.24;
                    vel = vel.subtract(n.x * over * spring, n.y * over * spring, n.z * over * spring);

                    // Pendulum boost: gravity component along swing arc
                    Vec3d tangent = new Vec3d(0, -1, 0).crossProduct(n).crossProduct(n);
                    if (tangent.lengthSquared() > 0.001) {
                        tangent = tangent.normalize();
                        // Add slight boost along tangent for natural swing
                        vel = vel.add(tangent.x * 0.015, tangent.y * 0.015, tangent.z * 0.015);
                    }

                    // Momentum conservation — slight speed gain
                    vel = vel.multiply(1.008);
                    if (player.isSprinting()) vel = vel.multiply(1.015);

                    // Max speed higher for real swinging (was 3.0, now 4.2)
                    if (vel.length() > 4.2) vel = vel.normalize().multiply(4.2);
                }
            } else {
                // Inside rope sphere — free fall with slight air control
                vel = vel.multiply(0.998);
            }

            if (!Double.isFinite(vel.x)) vel = new Vec3d(0, -0.1, 0);

            player.fallDistance = 0.0f;
            push(player, vel);

            // Don't detach immediately on ground if moving fast — allow momentum
            if (player.isOnGround()) {
                if (vel.horizontalLength() > 0.8) {
                    // Keep some momentum, detach but with boost
                    detach(player, powers, true);
                } else {
                    detach(player, powers, false);
                }
                MasteryLogic.addMastery(player, 2);
            }
        } catch (Exception e) {
            try {
                detach(player, powers, false);
            } catch (Exception ignored) {}
        }
    }

    public static void tickZip(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || powers == null) return;
        try {
            powers.zipTicks--;
            Vec3d target = new Vec3d(powers.zipX, powers.zipY, powers.zipZ);
            if (!Double.isFinite(target.x)) {
                powers.zipTicks = 0;
                try {
                    player.setNoGravity(false);
                } catch (Exception ignored) {}
                return;
            }
            Vec3d to = target.subtract(player.getPos());
            double dist = to.length();
            if (!Double.isFinite(dist)) {
                powers.zipTicks = 0;
                try {
                    player.setNoGravity(false);
                } catch (Exception ignored) {}
                return;
            }
            if (powers.zipTicks <= 0 || dist < 1.2) {
                powers.zipTicks = 0;
                try {
                    player.setNoGravity(false);
                } catch (Exception ignored) {}
                // Launch on zip end — stronger
                Vec3d vel = player.getVelocity();
                if (vel == null) vel = new Vec3d(0, 0, 0);
                Vec3d look = player.getRotationVector();
                if (look != null && look.lengthSquared() > 0.001) {
                    vel = vel.add(look.x * 0.6, 0.3, look.z * 0.6);
                }
                vel = vel.multiply(1.25);
                if (vel.length() > 3.0) vel = vel.normalize().multiply(3.0);
                if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.4, 0);
                push(player, vel);
                return;
            }
            // Faster zip — more Spider-Man like
            double speed = Math.min(2.2, 0.7 + dist * 0.15);
            Vec3d vel = to.normalize().multiply(speed);
            if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.3, 0);
            player.fallDistance = 0.0f;
            push(player, vel);
        } catch (Exception e) {
            powers.zipTicks = 0;
            try {
                player.setNoGravity(false);
            } catch (Exception ignored) {}
        }
    }

    public static void push(ServerPlayerEntity player, Vec3d vel) {
        if (player == null || vel == null) return;
        if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0, 0);
        if (vel.length() > 12.0) vel = vel.normalize().multiply(12.0);
        try {
            player.setVelocity(vel);
            player.velocityModified = true;
        } catch (Exception ignored) {}
    }
}
