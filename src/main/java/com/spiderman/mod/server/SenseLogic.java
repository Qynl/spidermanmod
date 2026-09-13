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
 * ULTIMATE SPIDER-SENSE - Feels like real Spider-Man.
 * - Detects hostiles, projectiles, falls
 * - Slow-mo when danger (configurable)
 * - Style points for dodging
 * - Glowing effect on threats
 */
public final class SenseLogic {
    private SenseLogic() {
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (!powers.hasPowers) {
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        double calcRadius = cfg.senseRadiusBase + cfg.senseRadiusPerStage * powers.stage;
        if (powers.stylePoints > 200) calcRadius *= 1.2;
        final double radius = calcRadius;
        
        Vec3d pos = player.getPos();
        Box box = new Box(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        ServerWorld world = player.getServerWorld();

        // Hostiles
        List<HostileEntity> mobs = world.getEntitiesByClass(HostileEntity.class, box,
                e -> e.isAlive() && e.squaredDistanceTo(player) < radius * radius);
        int sent = 0;
        boolean dangerClose = false;
        
        for (HostileEntity mob : mobs) {
            if (sent >= 6) break;
            
            double dist = mob.squaredDistanceTo(player);
            boolean isClose = dist < 25; // 5 blocks
            
            // Glowing for close threats at higher stages
            if (powers.stage >= 2 && isClose) {
                try {
                    mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0));
                } catch (Exception ignored) {}
                dangerClose = true;
            }
            
            // Stronger sense for targeting player
            int kind = 0;
            if (mob.getTarget() == player) {
                kind = 0; // Red - attacking you
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

        // Projectiles - ULTIMATE spider-sense
        List<ProjectileEntity> shots = world.getEntitiesByClass(ProjectileEntity.class, box,
                e -> e.isAlive() && approaching(e, pos) && e.squaredDistanceTo(player) < 100);
        
        for (ProjectileEntity shot : shots) {
            if (sent >= 10) break;
            
            double dist = shot.squaredDistanceTo(player);
            boolean isVeryClose = dist < 16; // 4 blocks
            
            if (isVeryClose && cfg.senseSlowMo && powers.stage >= 1) {
                // Slow-mo chance!
                if (Math.random() < cfg.senseSlowMoChance) {
                    try {
                        // Give player slow falling and resistance for matrix dodge
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 30, 0));
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 2));
                        powers.slowMoActive = true;
                        powers.slowMoTicks = 20;
                        powers.stylePoints += 15;
                        MasteryLogic.addMastery(player, 3);
                    } catch (Exception ignored) {}
                }
                dangerClose = true;
            }
            
            ServerNetworking.sendSense(player, shot.getX(), shot.getY(), shot.getZ(), 1);
            sent++;
        }

        // Fall danger
        if (player.fallDistance > 5.0f && player.getVelocity().y < -0.4) {
            if (player.age % 10 == 0) {
                ServerNetworking.sendSense(player, pos.x, pos.y - 5.0, pos.z, 2);
                if (player.fallDistance > 12 && powers.stage >= 1) {
                    powers.senseActive = true;
                    powers.senseTicks = 20;
                }
            }
        }
        
        // Low health danger sense
        if (player.getHealth() < 6 && player.age % 20 == 0) {
            powers.senseActive = true;
            powers.senseTicks = 40;
        }
        
        // Tick down sense
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
        
        // Style for surviving danger
        if (dangerClose && player.age % 40 == 0) {
            MasteryLogic.addMastery(player, 1);
        }
    }

    private static boolean approaching(ProjectileEntity shot, Vec3d target) {
        try {
            Vec3d to = target.subtract(shot.getPos());
            double dot = shot.getVelocity().dotProduct(to);
            // Also check if it's moving fast towards player
            return dot > 0.3 && shot.getVelocity().length() > 0.3;
        } catch (Exception e) {
            return false;
        }
    }
}
