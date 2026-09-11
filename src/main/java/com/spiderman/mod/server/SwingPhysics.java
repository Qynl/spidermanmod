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
 * Momentum-based web swinging. Velocity-only: the server steers velocity and
 * sets {@code velocityModified} so vanilla client prediction integrates it
 * smoothly (no per-tick teleports, multiplayer-safe).
 *
 * <p>Rope control without key input: looking up reels in, looking down lets
 * out. This keeps the whole system server-authoritative with zero extra keys.
 */
public final class SwingPhysics {
    private SwingPhysics() {
    }

    /** Smart anchor search: sweeps upward-biased rays for a solid attach point. */
    public static Vec3d findAnchor(ServerPlayerEntity player, double range) {
        World world = player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        for (int i = 0; i < 4; i++) {
            Vec3d dir = look.add(0.0, 0.5 + i * 0.45, 0.0).normalize();
            Vec3d to = eye.add(dir.x * range, dir.y * range, dir.z * range);
            BlockHitResult hit = world.raycast(new RaycastContext(eye, to,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getPos();
            }
        }
        return null;
    }

    public static void attach(ServerPlayerEntity player, PlayerPowers powers, Vec3d anchor, int hand) {
        powers.swinging = true;
        powers.swingX = anchor.x;
        powers.swingY = anchor.y;
        powers.swingZ = anchor.z;
        powers.ropeLen = Math.max(3.0, Math.min(anchor.distanceTo(player.getPos()),
                SpiderConfig.get().swingRange));
        powers.swingHand = hand;
        player.playSound(ModSounds.WEB_SHOT, 1.0f, 1.2f);
        ServerNetworking.sendSwing(player, true, anchor.x, anchor.y, anchor.z, hand, 0);
    }

    public static void detach(ServerPlayerEntity player, PlayerPowers powers, boolean fling) {
        if (!powers.swinging) {
            return;
        }
        powers.stopSwing();
        ServerNetworking.sendSwingOff(player);
        if (fling) {
            Vec3d vel = player.getVelocity().multiply(1.12).add(0.0, 0.18, 0.0);
            if (vel.length() > 3.2) {
                vel = vel.normalize().multiply(3.2);
            }
            push(player, vel);
        }
    }

    /** Pendulum constraint around the anchor, applied as velocity steering. */
    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        Vec3d pos = player.getPos();
        Vec3d anchor = new Vec3d(powers.swingX, powers.swingY, powers.swingZ);
        Vec3d vel = player.getVelocity();
        vel = vel.add(0.0, -0.08, 0.0);

        float pitch = player.getPitch();
        if (pitch < -15.0f) {
            powers.ropeLen = Math.max(3.0, powers.ropeLen - 0.18);
        } else if (pitch > 25.0f) {
            powers.ropeLen = Math.min(SpiderConfig.get().swingRange, powers.ropeLen + 0.25);
        }

        Vec3d r = pos.add(vel).subtract(anchor);
        double dist = r.length();
        if (dist > powers.ropeLen && dist > 0.001) {
            Vec3d n = r.normalize();
            double radial = vel.dotProduct(n);
            if (radial > 0.0) {
                vel = vel.subtract(n.x * radial, n.y * radial, n.z * radial);
            }
            // Soft spring back to the rope sphere (bounded drift, no teleports).
            double over = dist - powers.ropeLen;
            vel = vel.subtract(n.x * over * 0.12, n.y * over * 0.12, n.z * over * 0.12);
            vel = vel.multiply(1.004);
            if (vel.length() > 3.0) {
                vel = vel.normalize().multiply(3.0);
            }
        }
        player.fallDistance = 0.0f;
        push(player, vel);
        if (player.isOnGround()) {
            detach(player, powers, false);
            MasteryLogic.addMastery(player, 2);
        }
    }

    /** Zip-line burst toward the stored target. */
    public static void tickZip(ServerPlayerEntity player, PlayerPowers powers) {
        powers.zipTicks--;
        Vec3d target = new Vec3d(powers.zipX, powers.zipY, powers.zipZ);
        Vec3d to = target.subtract(player.getPos());
        double dist = to.length();
        if (powers.zipTicks <= 0 || dist < 1.5) {
            powers.zipTicks = 0;
            player.setNoGravity(false);
            Vec3d vel = player.getVelocity().multiply(1.1);
            if (vel.length() > 2.5) {
                vel = vel.normalize().multiply(2.5);
            }
            push(player, vel);
            return;
        }
        Vec3d vel = to.normalize().multiply(Math.min(1.6, 0.6 + dist * 0.12));
        player.fallDistance = 0.0f;
        push(player, vel);
    }

    public static void push(ServerPlayerEntity player, Vec3d vel) {
        player.setVelocity(vel);
        player.velocityModified = true;
    }
}
