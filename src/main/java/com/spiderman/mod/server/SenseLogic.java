package com.spiderman.mod.server;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;

/**
 * ULTIMATE SPIDER-SENSE - Fixed for no lag at high stage.
 * - Capped radius, less frequent scans at high stage
 * - Limited glowing, no excessive status effects
 */
public final class SenseLogic {
    private SenseLogic() {
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (!powers.hasPowers) {
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        // FIXED: Cap radius to prevent lag - was 12+5*4=32 -> 38 with style, now max 20
        double calcRadius = cfg.senseRadiusBase + cfg.senseRadiusPerStage * powers.stage;
        if (powers.stylePoints > 200) calcRadius *= 1.1; // Was 1.2, now 1.1
        if (calcRadius > 20.0) calcRadius = 20.0; // HARD CAP at 20, was uncapped
        final double radius = calcRadius;
        
        Vec3d pos = player.getPos();
        Box box = new Box(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        ServerWorld world = player.getServerWorld();

        // Hostiles - limit to 4 at high stage to prevent lag
        int maxMobs = powers.stage >= 4 ? 4 : 6;
        List<HostileEntity> mobs = world.getEntitiesByClass(HostileEntity.class, box,
                e -> e.isAlive() && e.squaredDistanceTo(player) < radius * radius);
        int sent = 0;
        boolean dangerClose = false;
        int glowApplied = 0;
        
        for (HostileEntity mob : mobs) {
            if (sent >= maxMobs) break;
            
            double dist = mob.squaredDistanceTo(player);
            boolean isClose = dist < 25;
            
            // FIXED: Glowing only for 2 mobs max at high stage, and less frequent
            if (powers.stage >= 2 && isClose && glowApplied < 2 && player.age % 20 == 0) {
                try {
                    mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0));
                } catch (Exception ignored) {}
                glowApplied++;
                dangerClose = true;
            }
            
            int kind = 0;
            if (mob.getTarget() == player) {
                kind = 0;
                dangerClose = true;
                if (isClose) {
                    powers.senseActive = true;
                    powers.senseTicks = 30;
                }
            } else if (isClose) {
                kind = 0;
            }
            
            ServerNetworking.sendSense(player, mob.getX(), mob.getY(), mob.getZ(), kind);
            sent++;
        }

        // Projectiles - limit to 3 at high stage
        int maxShots = powers.stage >= 4 ? 3 : 6;
        List<ProjectileEntity> shots = world.getEntitiesByClass(ProjectileEntity.class, box,
                e -> e.isAlive() && approaching(e, pos) && e.squaredDistanceTo(player) < 100);
        
        for (ProjectileEntity shot : shots) {
            if (sent >= maxShots + maxMobs) break;
            
            double dist = shot.squaredDistanceTo(player);
            boolean isVeryClose = dist < 16;
            
            if (isVeryClose && cfg.senseSlowMo && powers.stage >= 1) {
                if (Math.random() < cfg.senseSlowMoChance * 0.5) { // Reduced chance at high stage
                    try {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20, 0));
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 15, 1));
                        powers.slowMoActive = true;
                        powers.slowMoTicks = 15;
                        powers.stylePoints += 10;
                        MasteryLogic.addMastery(player, 2);
                    } catch (Exception ignored) {}
                }
                dangerClose = true;
            }
            
            ServerNetworking.sendSense(player, shot.getX(), shot.getY(), shot.getZ(), 1);
            sent++;
        }

        if (player.fallDistance > 5.0f && player.getVelocity().y < -0.4) {
            if (player.age % 20 == 0) {
                ServerNetworking.sendSense(player, pos.x, pos.y - 5.0, pos.z, 2);
                if (player.fallDistance > 12 && powers.stage >= 1) {
                    powers.senseActive = true;
                    powers.senseTicks = 20;
                }
            }
        }
        
        if (player.getHealth() < 6 && player.age % 40 == 0) {
            powers.senseActive = true;
            powers.senseTicks = 40;
        }
        
        if (powers.senseTicks > 0) {
            powers.senseTicks--;
            if (powers.senseTicks == 0) {
                powers.senseActive = false;
            }
        }
        if (powers.slowMoTicks > 0) {
            powers.slowMoTicks--;
            if (powers.slowMoTicks == 0) {
                powers.slowMoActive = false;
            }
        }
        
        if (dangerClose && player.age % 60 == 0) {
            MasteryLogic.addMastery(player, 1);
        }
    }

    private static boolean approaching(ProjectileEntity shot, Vec3d target) {
        try {
            Vec3d to = target.subtract(shot.getPos());
            double dot = shot.getVelocity().dotProduct(to);
            return dot > 0.3 && shot.getVelocity().length() > 0.3;
        } catch (Exception e) {
            return false;
        }
    }
}
