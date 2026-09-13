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
 * The scattergun of the frontier: two shells in the drums, each a cone of
 * six short-range pellets with real tracers. Brutal in a doorway, useless
 * across a street, and loud enough to wake the whole valley.
 */
public class BlunderbussItem extends RevolverItem {
    private static final int PELLETS = 6;
    private static final float PELLET_DAMAGE = 2.8f;

    public BlunderbussItem(Settings settings) {
        super(settings, 3.0f, 8, 12.0f, 2, 55);
    }

    @Override
    protected void fireShot(World world, PlayerEntity user, ItemStack stack, Hand hand,
                            Vec3d start, Vec3d direction, double extraSpread) {
        Box search = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.6);
        for (int i = 0; i < PELLETS; i++) {
            // A wide, even cone: each pellet wanders independently.
            Vec3d spread = new Vec3d(
                    direction.x + (world.random.nextDouble() - 0.5) * 0.42,
                    direction.y + (world.random.nextDouble() - 0.5) * 0.30,
                    direction.z + (world.random.nextDouble() - 0.5) * 0.42).normalize();
            Vec3d end = start.add(spread.multiply(range));
            EntityHitResult hit = ProjectileUtil.raycast(user, start, end, search,
                    entity -> !entity.isSpectator() && entity.isAlive() && entity.canHit(), range * range);
            if (hit != null && hit.getEntity() instanceof LivingEntity living) {
                living.damage(world.getDamageSources().playerAttack(user), PELLET_DAMAGE);
                living.takeKnockback(0.35, -spread.x, -spread.z);
                world.playSound(null, living.getX(), living.getY(), living.getZ(),
                        SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.5f, 1.1f);
                drawTracer(world, start, spread, hit.getPos().distanceTo(start));
            } else {
                drawTracer(world, start, spread, range * 0.7);
            }
        }
    }

    @Override
    protected void fireFeedback(World world, PlayerEntity user, Vec3d start, Vec3d direction, Vec3d impact) {
        super.fireFeedback(world, user, start, direction, impact);
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Vec3d muzzle = start.add(direction.multiply(1.0));
        serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                muzzle.x, muzzle.y - 0.1, muzzle.z, 10, 0.3, 0.15, 0.3, 0.03);
        serverWorld.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 5, 0.2, 0.1, 0.2, 0.05);
        // The gun shoves the shooter back; hip-fire taunts gravity.
        user.takeKnockback(0.9, -direction.x, -direction.z);
        serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS, 0.7f, 1.6f);
    }
}
