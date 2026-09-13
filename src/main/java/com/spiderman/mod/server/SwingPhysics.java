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
 * INSOMNIAC-LEVEL SWING PHYSICS - Fluid, momentum-based, no random fling.
 * - Real pendulum physics
 * - Momentum preservation like PS4/PS5 Spider-Man
 * - Smooth release, no extreme speeds
 * - Elastic rope, tension-based
 */
public final class SwingPhysics {
    private SwingPhysics() {}

    public static Vec3d findAnchor(ServerPlayerEntity player, double range) {
        if (player == null) return null;
        try {
            World world = player.getWorld();
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVector();
            if (look.lengthSquared() < 0.001) look = new Vec3d(0, 0.5, 1).normalize();

            // Insomniac style: prioritize high anchors for swinging, not just forward
            // Try 8 directions: up, up-forward, up-left, up-right, forward, left, right, slightly down
            Vec3d[] dirs = new Vec3d[] {
                new Vec3d(0, 1, 0), // Straight up
                new Vec3d(look.x * 0.5, 0.8, look.z * 0.5).normalize(), // Up-forward
                new Vec3d(-0.4, 0.7, look.z * 0.3).normalize(), // Up-left
                new Vec3d(0.4, 0.7, look.z * 0.3).normalize(), // Up-right
                look, // Forward
                new Vec3d(look.x - 0.5, 0.2, look.z).normalize(), // Left
                new Vec3d(look.x + 0.5, 0.2, look.z).normalize(), // Right
                new Vec3d(look.x, -0.1, look.z).normalize() // Slight down
            };

            for (Vec3d dir : dirs) {
                if (dir.lengthSquared() < 0.001) continue;
                Vec3d to = eye.add(dir.x * range, dir.y * range, dir.z * range);
                BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                    return hit.getPos();
                }
            }

            // Fallback: search in wider cone
            for (int i = 0; i < 6; i++) {
                double yawOffset = (Math.random() - 0.5) * 0.8;
                double pitchOffset = Math.random() * 0.6;
                Vec3d dir = look.add(yawOffset, pitchOffset, 0).normalize();
                Vec3d to = eye.add(dir.x * range, dir.y * range, dir.z * range);
                BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                    return hit.getPos();
                }
            }

        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isValidAnchor(Vec3d anchor, Vec3d eye, double range) {
        if (anchor == null || !Double.isFinite(anchor.x)) return false;
        double dist = anchor.distanceTo(eye);
        // Insomniac: need minimum distance for momentum, not too close
        return dist >= 4.0 && dist <= range * 1.2;
    }

    public static void attach(ServerPlayerEntity player, PlayerPowers powers, Vec3d anchor, int hand) {
        if (player == null || powers == null || anchor == null) return;
        try {
            powers.zipTicks = 0;
            powers.pullTicks = 0;
            try { player.setNoGravity(false); } catch (Exception ignored) {}
            powers.swinging = true;
            powers.swingX = anchor.x;
            powers.swingY = anchor.y;
            powers.swingZ = anchor.z;
            double dist = anchor.distanceTo(player.getPos());
            if (!Double.isFinite(dist)) dist = 18.0;
            // Insomniac: rope is 85% of distance for taut swing, not 92%
            powers.ropeLen = Math.max(5.0, Math.min(dist * 0.85, SpiderConfig.get().swingRange * 1.1));
            powers.swingHand = hand == 1 ? 1 : 0;
            player.playSound(ModSounds.WEB_SHOT, 1.0f, 1.25f);
            player.playSound(ModSounds.WEB_ZIP, 0.7f, 1.15f);
            ServerNetworking.sendSwing(player, true, anchor.x, anchor.y, anchor.z, powers.swingHand, 0);
            powers.stylePoints += 2;
            powers.consecutiveSwings++;
        } catch (Exception e) {
            powers.stopSwing();
        }
    }

    public static void detach(ServerPlayerEntity player, PlayerPowers powers, boolean fling) {
        if (player == null || powers == null) return;
        if (!powers.swinging) {
            try { if (player.hasNoGravity()) player.setNoGravity(false); } catch (Exception ignored) {}
            return;
        }
        powers.stopSwing();
        try { player.setNoGravity(false); } catch (Exception ignored) {}
        ServerNetworking.sendSwingOff(player);
        if (fling) {
            try {
                Vec3d vel = player.getVelocity();
                if (vel == null) vel = new Vec3d(0, 0.4, 0);

                // Insomniac: preserve momentum, don't multiply crazy
                // Fling power based on swing speed, not arbitrary boost
                double currentSpeed = vel.length();
                double flingMult = 1.0;
                if (currentSpeed > 1.0) {
                    flingMult = 1.0 + Math.min(0.35, currentSpeed * 0.08);
                }
                // Cap at 1.35x, not 1.5x
                if (flingMult > 1.35) flingMult = 1.35;

                vel = vel.multiply(flingMult);

                // Small upward boost for launch, like Insomniac
                double upBoost = 0.22;
                if (powers.stylePoints > 200) upBoost += 0.05;
                vel = vel.add(0, upBoost, 0);

                // Add look direction slight boost for control
                Vec3d look = player.getRotationVector();
                if (look != null && look.lengthSquared() > 0.001) {
                    // Only add forward if looking somewhat forward, not when looking back
                    if (look.y > -0.5) {
                        vel = vel.add(look.x * 0.12, look.y * 0.04, look.z * 0.12);
                    }
                }

                if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.45, 0);
                // Insomniac cap: max 3.0, not 3.5 or 5.0 - feels fast but controllable
                if (vel.length() > 3.0) vel = vel.normalize().multiply(3.0);

                push(player, vel);

                if (currentSpeed > 1.8) {
                    powers.stylePoints += 4;
                }

            } catch (Exception ignored) {
                push(player, new Vec3d(0, 0.45, 0));
            }
        }
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || powers == null) return;
        try {
            Vec3d pos = player.getPos();
            if (pos == null) { detach(player, powers, false); return; }
            Vec3d anchor = new Vec3d(powers.swingX, powers.swingY, powers.swingZ);
            if (!Double.isFinite(anchor.x)) { detach(player, powers, false); return; }
            Vec3d vel = player.getVelocity();
            if (vel == null) vel = new Vec3d(0, 0, 0);

            SpiderConfig cfg = SpiderConfig.get();

            // Gravity: Insomniac has slightly less gravity when swinging for floatiness
            double gravity = -0.075;
            if (powers.diving) gravity = -0.12;
            vel = vel.add(0, gravity, 0);

            float pitch = player.getPitch();

            // Insomniac reel system: W/S to reel in/out, but smooth
            if (pitch < -30.0f) {
                // Looking up: reel in for speed
                powers.ropeLen = Math.max(4.0, powers.ropeLen - 0.35);
                if (vel.horizontalLength() > 0.5) {
                    vel = vel.add(0, 0.03, 0);
                }
            } else if (pitch < -12.0f) {
                powers.ropeLen = Math.max(4.0, powers.ropeLen - 0.18);
            } else if (pitch > 35.0f) {
                // Looking down: let out for big swing
                powers.ropeLen = Math.min(cfg.swingRange * 1.25, powers.ropeLen + 0.35);
            } else if (pitch > 18.0f) {
                powers.ropeLen = Math.min(cfg.swingRange * 1.25, powers.ropeLen + 0.2);
            }

            // Sprint boost: Insomniac has subtle boost, not crazy
            if (player.isSprinting()) {
                double boost = 1.008;
                if (powers.stylePoints > 150) boost = 1.012;
                vel = vel.multiply(boost);
            }

            if (powers.diving) {
                vel = vel.multiply(1.015);
            }

            powers.ropeLen = Math.max(4.0, Math.min(powers.ropeLen, cfg.swingRange * 1.25));

            Vec3d toAnchor = anchor.subtract(pos.add(vel));
            double dist = toAnchor.length();
            if (!Double.isFinite(dist)) { detach(player, powers, false); return; }
            if (dist > cfg.swingRange * 2.8) { detach(player, powers, true); return; }

            // Pendulum physics - Insomniac style
            if (dist > powers.ropeLen && dist > 0.001) {
                Vec3d n = pos.add(vel).subtract(anchor).normalize();
                if (!Double.isFinite(n.x)) { detach(player, powers, false); return; }

                // Remove radial velocity (keep tangential)
                double radial = vel.dotProduct(n);
                if (radial > 0) {
                    vel = vel.subtract(n.x * radial, n.y * radial, n.z * radial);
                }

                // Spring force - elastic rope
                double over = dist - powers.ropeLen;
                double spring = 0.16;
                if (pitch < -15) spring = 0.19; // Tighter when reeling
                if (powers.diving) spring = 0.11;
                if (player.isSprinting()) spring *= 1.04;

                vel = vel.subtract(n.x * over * spring, n.y * over * spring, n.z * over * spring);

                // Tangential boost - keeps swing going, like Insomniac momentum
                Vec3d tangent = new Vec3d(0, -1, 0).crossProduct(n).crossProduct(n);
                if (tangent.lengthSquared() > 0.001) {
                    tangent = tangent.normalize();
                    double tangBoost = 0.012 + Math.min(powers.consecutiveSwings, 8) * 0.001;
                    vel = vel.add(tangent.x * tangBoost, tangent.y * tangBoost, tangent.z * tangBoost);
                }

                // Momentum - very subtle, preserves speed
                double mom = 1.003 + Math.min(powers.consecutiveSwings, 6) * 0.0008;
                if (mom > 1.01) mom = 1.01;
                vel = vel.multiply(mom);

                // Cap speed - Insomniac feels fast but not laggy
                double maxSpeed = 2.8 + Math.min(powers.stage, 4) * 0.12 + Math.min(powers.stylePoints, 400) * 0.0004;
                if (maxSpeed > 3.6) maxSpeed = 3.6; // Hard cap 3.6, was 4.2
                if (vel.length() > maxSpeed) vel = vel.normalize().multiply(maxSpeed);

                if (vel.length() > 2.2 && player.age % 40 == 0) {
                    powers.stylePoints += 1;
                }
            } else {
                // Inside rope length: slight drag, air control
                vel = vel.multiply(0.997);
                Vec3d input = getAirInput(player);
                if (input.lengthSquared() > 0.01) {
                    vel = vel.add(input.x * 0.022, 0, input.z * 0.022);
                }
            }

            if (!Double.isFinite(vel.x)) vel = new Vec3d(0, -0.08, 0);

            player.fallDistance = 0;
            push(player, vel);

            // Auto-detach on ground with style
            if (player.isOnGround()) {
                if (vel.horizontalLength() > 0.7) {
                    detach(player, powers, true);
                    powers.stylePoints += 3;
                } else if (vel.horizontalLength() > 0.3) {
                    detach(player, powers, true);
                } else {
                    detach(player, powers, false);
                }
                MasteryLogic.addMastery(player, 2);
            }

            if (!player.isOnGround()) powers.airTime++;

        } catch (Exception e) {
            try { detach(player, powers, false); } catch (Exception ignored) {}
        }
    }

    private static Vec3d getAirInput(ServerPlayerEntity player) {
        try {
            Vec3d vel = player.getVelocity();
            return new Vec3d(vel.x * 0.06, 0, vel.z * 0.06);
        } catch (Exception e) {
            return Vec3d.ZERO;
        }
    }

    public static void tickZip(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || powers == null) return;
        try {
            powers.zipTicks--;
            Vec3d target = new Vec3d(powers.zipX, powers.zipY, powers.zipZ);
            if (!Double.isFinite(target.x)) {
                powers.zipTicks = 0;
                try { player.setNoGravity(false); } catch (Exception ignored) {}
                return;
            }
            Vec3d to = target.subtract(player.getPos());
            double dist = to.length();
            if (!Double.isFinite(dist)) {
                powers.zipTicks = 0;
                try { player.setNoGravity(false); } catch (Exception ignored) {}
                return;
            }
            if (powers.zipTicks <= 0 || dist < 1.2) {
                powers.zipTicks = 0;
                try { player.setNoGravity(false); } catch (Exception ignored) {}

                Vec3d vel = player.getVelocity();
                if (vel == null) vel = new Vec3d(0, 0, 0);
                Vec3d look = player.getRotationVector();
                if (look != null && look.lengthSquared() > 0.001) {
                    double launch = 0.55 + Math.min(powers.stylePoints, 400) * 0.0004;
                    if (launch > 0.8) launch = 0.8;
                    vel = vel.add(look.x * launch, 0.28, look.z * launch);
                }

                SpiderConfig cfg = SpiderConfig.get();
                vel = vel.multiply(Math.min(cfg.zipBoost, 1.15));

                if (vel.length() > 2.8) vel = vel.normalize().multiply(2.8);
                if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.45, 0);

                push(player, vel);
                powers.stylePoints += 4;
                MasteryLogic.addMastery(player, 2);
                return;
            }

            // Insomniac zip: fast but controlled, eases in
            double speed = Math.min(2.0, 0.55 + dist * 0.13);
            if (powers.stylePoints > 250) speed *= 1.06;

            Vec3d vel = to.normalize().multiply(speed);
            if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.3, 0);
            player.fallDistance = 0;
            push(player, vel);
        } catch (Exception e) {
            powers.zipTicks = 0;
            try { player.setNoGravity(false); } catch (Exception ignored) {}
        }
    }

    public static void push(ServerPlayerEntity player, Vec3d vel) {
        if (player == null || vel == null) return;
        if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0, 0);
        if (vel.length() > 7.0) vel = vel.normalize().multiply(7.0);
        try {
            player.setVelocity(vel);
            player.velocityModified = true;
        } catch (Exception ignored) {}
    }
}
