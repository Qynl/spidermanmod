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
 * ULTIMATE SWING PHYSICS - The most fun swinging in Minecraft.
 * - 90 block range, 8-direction anchor search + up
 * - Real pendulum with pumping, sprint boost, dive boost
 * - Slingshot, momentum conservation, air tricks
 * - Style points for cool swinging
 * - FOV effects via velocity
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

            // Priority 1: Upward-biased for high swings (real Spider-Man)
            for (int i = 0; i < 8; i++) {
                double upBias = 0.2 + i * 0.3;
                double spread = (i % 2 == 0) ? 0.1 : -0.1;
                Vec3d dir = look.add(spread, upBias, 0.0);
                if (dir.lengthSquared() < 0.001) continue;
                dir = dir.normalize();
                Vec3d to = eye.add(dir.x * range, dir.y * range, dir.z * range);
                BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                    return hit.getPos();
                }
            }
            
            // Priority 2: Side sweeps for horizontal swinging
            for (int side = -1; side <= 1; side += 2) {
                for (int up = 0; up < 3; up++) {
                    Vec3d dir = look.add(side * (0.4 + up * 0.15), 0.3 + up * 0.2, 0.0).normalize();
                    Vec3d to = eye.add(dir.x * range, dir.y * range, dir.z * range);
                    BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                    if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                        return hit.getPos();
                    }
                }
            }
            
            // Priority 3: Straight
            Vec3d to = eye.add(look.x * range, look.y * range, look.z * range);
            BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (hit.getType() != HitResult.Type.MISS && isValidAnchor(hit.getPos(), eye, range)) {
                return hit.getPos();
            }
            
            // Priority 4: Up (for city swinging)
            for (int i = 0; i < 3; i++) {
                double upRange = range * (0.7 + i * 0.2);
                Vec3d toUp = eye.add(0, upRange, 0);
                BlockHitResult hitUp = world.raycast(new RaycastContext(eye, toUp,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                if (hitUp.getType() != HitResult.Type.MISS && isValidAnchor(hitUp.getPos(), eye, range)) {
                    return hitUp.getPos();
                }
            }
            
            // Priority 5: Down-forward (for low swings)
            Vec3d downForward = look.add(0, -0.3, 0).normalize();
            Vec3d toDown = eye.add(downForward.x * range, downForward.y * range, downForward.z * range);
            BlockHitResult hitDown = world.raycast(new RaycastContext(eye, toDown,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (hitDown.getType() != HitResult.Type.MISS && isValidAnchor(hitDown.getPos(), eye, range)) {
                return hitDown.getPos();
            }
            
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isValidAnchor(Vec3d anchor, Vec3d eye, double range) {
        if (anchor == null) return false;
        if (!Double.isFinite(anchor.x)) return false;
        double dist = anchor.distanceTo(eye);
        return dist >= 2.5 && dist <= range * 1.15;
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
            powers.ropeLen = Math.max(4.0, Math.min(dist * 0.92, SpiderConfig.get().swingRange * 1.2));
            powers.swingHand = hand == 1 ? 1 : 0;
            player.playSound(ModSounds.WEB_SHOT, 1.0f, 1.2f);
            player.playSound(ModSounds.WEB_ZIP, 0.8f, 1.1f);
            ServerNetworking.sendSwing(player, true, anchor.x, anchor.y, anchor.z, powers.swingHand, 0);
            
            // Style for attaching
            powers.stylePoints += 2;
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
                
                // Calculate fling power based on swing momentum and style
                double momentum = vel.length();
                double flingPower = 1.4 + (powers.consecutiveSwings * 0.05);
                if (flingPower > 1.8) flingPower = 1.8;
                
                // Add upward boost for that Spider-Man launch
                vel = vel.multiply(flingPower).add(0.0, 0.4 + (powers.stylePoints * 0.001), 0.0);
                
                // Add look direction for control
                Vec3d look = player.getRotationVector();
                if (look != null) {
                    vel = vel.add(look.x * 0.15, look.y * 0.1, look.z * 0.15);
                }
                
                if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.6, 0);
                if (vel.length() > 5.0) vel = vel.normalize().multiply(5.0);
                
                push(player, vel);
                
                // Style for fling
                if (momentum > 2.0) {
                    powers.stylePoints += 8;
                }
                
            } catch (Exception ignored) {
                push(player, new Vec3d(0, 0.6, 0));
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

            SpiderConfig cfg = SpiderConfig.get();
            
            // Enhanced gravity with dive
            double gravity = -0.09;
            if (powers.diving) gravity = -0.18;
            vel = vel.add(0.0, gravity, 0.0);

            // ULTIMATE PUMPING SYSTEM
            float pitch = player.getPitch();
            float yaw = player.getYaw();
            
            // Look up = reel in FAST for speed (real Spider-Man pumping)
            if (pitch < -35.0f) {
                powers.ropeLen = Math.max(3.0, powers.ropeLen - 0.5);
                Vec3d toAnchor = anchor.subtract(pos);
                if (toAnchor.y > 0 && vel.horizontalLength() > 0.4) {
                    // Boost at bottom of swing
                    vel = vel.add(0.0, 0.06, 0.0);
                    powers.stylePoints += 1;
                }
            } else if (pitch < -20.0f) {
                powers.ropeLen = Math.max(3.0, powers.ropeLen - 0.35);
                if (vel.horizontalLength() > 0.5) {
                    vel = vel.add(0.0, 0.03, 0.0);
                }
            } else if (pitch < -8.0f) {
                powers.ropeLen = Math.max(3.0, powers.ropeLen - 0.18);
            } else if (pitch > 40.0f) {
                // Look down = let out FAST for big arc
                powers.ropeLen = Math.min(cfg.swingRange * 1.4, powers.ropeLen + 0.6);
            } else if (pitch > 20.0f) {
                powers.ropeLen = Math.min(cfg.swingRange * 1.4, powers.ropeLen + 0.35);
            }

            // Sprint boost - hold sprint for ULTIMATE SPEED
            if (player.isSprinting()) {
                double sprintBoost = cfg.swingBoost;
                if (powers.stylePoints > 200) sprintBoost *= 1.1;
                vel = vel.multiply(sprintBoost);
                
                if (vel.length() > 3.0) {
                    powers.stylePoints += 1;
                }
            }

            // Dive boost while swinging
            if (powers.diving) {
                vel = vel.multiply(1.03);
            }

            // Clamp rope with style
            powers.ropeLen = Math.max(3.0, Math.min(powers.ropeLen, cfg.swingRange * 1.5));

            Vec3d r = pos.add(vel).subtract(anchor);
            double dist = r.length();
            if (!Double.isFinite(dist)) {
                detach(player, powers, false);
                return;
            }
            if (dist > cfg.swingRange * 4.0) {
                detach(player, powers, true); // Fling if too far
                return;
            }

            if (dist > powers.ropeLen && dist > 0.001) {
                Vec3d n = r.normalize();
                if (Double.isFinite(n.x)) {
                    double radial = vel.dotProduct(n);
                    if (radial > 0.0) {
                        vel = vel.subtract(n.x * radial, n.y * radial, n.z * radial);
                    }
                    
                    // Dynamic spring based on situation
                    double over = dist - powers.ropeLen;
                    double spring = 0.2;
                    if (pitch < -15) spring = 0.28; // Tighter when reeling
                    if (powers.diving) spring = 0.15; // Looser when diving for bigger arc
                    if (player.isSprinting()) spring *= 1.1;
                    
                    vel = vel.subtract(n.x * over * spring, n.y * over * spring, n.z * over * spring);

                    // Pendulum physics - natural swing boost
                    Vec3d tangent = new Vec3d(0, -1, 0).crossProduct(n).crossProduct(n);
                    if (tangent.lengthSquared() > 0.001) {
                        tangent = tangent.normalize();
                        double tangentBoost = 0.02 + (powers.stage * 0.005);
                        vel = vel.add(tangent.x * tangentBoost, tangent.y * tangentBoost, tangent.z * tangentBoost);
                    }

                    // Momentum conservation + style
                    double momentumMult = 1.01 + (powers.consecutiveSwings * 0.002);
                    if (momentumMult > 1.03) momentumMult = 1.03;
                    vel = vel.multiply(momentumMult);
                    
                    if (player.isSprinting()) vel = vel.multiply(1.02);

                    // Max speed - higher for ultimate swinging
                    double maxSpeed = 4.8 + (powers.stage * 0.2) + (powers.stylePoints * 0.001);
                    if (maxSpeed > 6.5) maxSpeed = 6.5;
                    if (vel.length() > maxSpeed) vel = vel.normalize().multiply(maxSpeed);
                    
                    // Style for fast swinging
                    if (vel.length() > 3.5) {
                        powers.stylePoints += 1;
                        if (player.age % 20 == 0) {
                            MasteryLogic.addMastery(player, 2);
                        }
                    }
                }
            } else {
                // Inside rope - free fall with air control
                vel = vel.multiply(0.997);
                
                // Air control while inside rope sphere
                Vec3d input = getAirInput(player);
                if (input.lengthSquared() > 0.01) {
                    vel = vel.add(input.x * 0.05, 0, input.z * 0.05);
                }
            }

            if (!Double.isFinite(vel.x)) vel = new Vec3d(0, -0.1, 0);

            player.fallDistance = 0.0f;
            push(player, vel);

            // Smart ground detach - keep momentum if fast
            if (player.isOnGround()) {
                if (vel.horizontalLength() > 1.0) {
                    detach(player, powers, true);
                    powers.stylePoints += 5;
                } else if (vel.horizontalLength() > 0.5) {
                    detach(player, powers, true);
                } else {
                    detach(player, powers, false);
                }
                MasteryLogic.addMastery(player, 3);
            }
            
            // Track air time
            if (!player.isOnGround()) {
                powers.airTime++;
            }
            
        } catch (Exception e) {
            try {
                detach(player, powers, false);
            } catch (Exception ignored) {}
        }
    }

    private static Vec3d getAirInput(ServerPlayerEntity player) {
        try {
            Vec3d vel = player.getVelocity();
            return new Vec3d(vel.x * 0.1, 0, vel.z * 0.1);
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
            if (powers.zipTicks <= 0 || dist < 1.0) {
                powers.zipTicks = 0;
                try {
                    player.setNoGravity(false);
                } catch (Exception ignored) {}
                
                // ULTIMATE ZIP LAUNCH
                Vec3d vel = player.getVelocity();
                if (vel == null) vel = new Vec3d(0, 0, 0);
                Vec3d look = player.getRotationVector();
                if (look != null && look.lengthSquared() > 0.001) {
                    double launchPower = 0.8 + (powers.stylePoints * 0.001);
                    if (launchPower > 1.2) launchPower = 1.2;
                    vel = vel.add(look.x * launchPower, 0.4, look.z * launchPower);
                }
                
                SpiderConfig cfg = SpiderConfig.get();
                vel = vel.multiply(cfg.zipBoost);
                
                if (vel.length() > 4.0) vel = vel.normalize().multiply(4.0);
                if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.5, 0);
                
                push(player, vel);
                powers.stylePoints += 8;
                MasteryLogic.addMastery(player, 3);
                return;
            }
            
            // Faster, more responsive zip
            double speed = Math.min(2.8, 0.8 + dist * 0.18);
            if (powers.stylePoints > 300) speed *= 1.15;
            
            Vec3d vel = to.normalize().multiply(speed);
            if (!Double.isFinite(vel.x)) vel = new Vec3d(0, 0.4, 0);
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
        if (vel.length() > 15.0) vel = vel.normalize().multiply(15.0);
        try {
            player.setVelocity(vel);
            player.velocityModified = true;
        } catch (Exception ignored) {}
    }
}
