package com.rivalrealms.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Pirate Flintlock: one chamber, one thunderclap. The shot punches
 * through the first three bodies in its path, and the reload is a full
 * powder-and-ramrod ritual — but anything standing in a doorway when it
 * speaks does not stay standing long.
 */
public final class FlintlockItem extends RevolverItem {
    private static final int PIERCE = 3;

    public FlintlockItem(Settings settings) {
        super(settings, 10.5f, 22, 26.0f, 1, 65);
    }

    @Override
    protected void fireShot(World world, PlayerEntity user, ItemStack stack, Hand hand,
                            Vec3d start, Vec3d direction, double extraSpread) {
        Vec3d end = start.add(direction.multiply(range));
        Box sweep = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.5);
        EntityHitResult firstHit = ProjectileUtil.raycast(user, start, end, sweep,
                entity -> !entity.isSpectator() && entity.isAlive() && entity.canHit(), range * range);
        Vec3d stop = firstHit != null ? firstHit.getPos().add(direction.multiply(2.5)) : end;

        int pierced = 0;
        for (Entity candidate : world.getOtherEntities(user, sweep,
                entity -> entity instanceof LivingEntity && entity.isAlive() && entity.canHit()
                        && !entity.isSpectator())) {
            if (pierced >= PIERCE) {
                break;
            }
            Vec3d toTarget = candidate.getBoundingBox().getCenter().subtract(start);
            double along = toTarget.dotProduct(direction);
            if (along < 0 || along > stop.distanceTo(start)) {
                continue;
            }
            double lateral = toTarget.subtract(direction.multiply(along)).lengthSquared();
            if (lateral <= 1.2) {
                LivingEntity living = (LivingEntity) candidate;
                living.damage(world.getDamageSources().playerAttack(user), damage);
                living.takeKnockback(0.9, -direction.x, -direction.z);
                pierced++;
            }
        }
        drawTracer(world, start, direction, stop.distanceTo(start));
    }

    @Override
    protected void fireFeedback(World world, PlayerEntity user, Vec3d start, Vec3d direction, Vec3d impact) {
        super.fireFeedback(world, user, start, direction, impact);
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Vec3d muzzle = start.add(direction.multiply(1.0)).subtract(0.0, 0.1, 0.0);
        serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                muzzle.x + direction.x * 0.6, muzzle.y, muzzle.z + direction.z * 0.6,
                8, 0.18, 0.1, 0.18, 0.012);
        serverWorld.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.06, 0.04, 0.06, 0.02);
        serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_ANVIL_LAND,
                SoundCategory.PLAYERS, 0.25f, 1.7f);
    }
}
