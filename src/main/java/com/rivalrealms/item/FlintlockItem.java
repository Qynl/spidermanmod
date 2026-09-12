package com.rivalrealms.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Pirate Flintlock: a slow, heavy sea-dog weapon. One thunderous shot
 * every couple of seconds that pierces the first three targets in its path,
 * rolls them all with knockback and leaves a fat powder-smoke cloud hanging
 * in the air.
 */
public final class FlintlockItem extends Item {
    private static final float DAMAGE = 10.0f;
    private static final int COOLDOWN = 45;
    private static final float RANGE = 22.0f;
    private static final int PIERCE = 3;

    public FlintlockItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            Vec3d start = user.getCameraPosVec(1.0f);
            Vec3d direction = user.getRotationVec(1.0f);
            Vec3d end = start.add(direction.multiply(RANGE));

            // Sweep along the ray and hit up to PIERCE living targets in order.
            Box sweep = user.getBoundingBox().stretch(direction.multiply(RANGE)).expand(1.5);
            EntityHitResult firstHit = ProjectileUtil.raycast(user, start, end, sweep,
                    entity -> !entity.isSpectator() && entity.isAlive() && entity.canHit(), RANGE * RANGE);
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
                if (lateral <= 1.1) {
                    ((LivingEntity) candidate).damage(world.getDamageSources().playerAttack(user), DAMAGE);
                    ((LivingEntity) candidate).takeKnockback(0.8, -direction.x, -direction.z);
                    pierced++;
                }
            }

            if (!(world instanceof ServerWorld serverWorld)) {
                return TypedActionResult.success(stack, true);
            }
            Vec3d muzzle = start.add(direction.multiply(1.0)).subtract(0.0, 0.1, 0.0);
            serverWorld.spawnParticles(ParticleTypes.POOF,
                    muzzle.x, muzzle.y, muzzle.z, 14, 0.22, 0.12, 0.22, 0.02);
            serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    muzzle.x + direction.x * 0.6, muzzle.y, muzzle.z + direction.z * 0.6,
                    8, 0.18, 0.1, 0.18, 0.012);
            serverWorld.spawnParticles(ParticleTypes.FLAME,
                    muzzle.x, muzzle.y, muzzle.z, 4, 0.06, 0.04, 0.06, 0.02);

            double distance = stop.distanceTo(start);
            for (double d = 1.2; d < distance; d += 1.2) {
                Vec3d point = start.add(direction.multiply(d));
                serverWorld.spawnParticles(ParticleTypes.CRIT,
                        point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.PLAYERS, 0.6f, 1.1f + world.random.nextFloat() * 0.15f);
            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WITHER_SHOOT,
                    SoundCategory.PLAYERS, 0.55f, 1.3f);

            user.getItemCooldownManager().set(this, COOLDOWN);
            user.setPitch(user.getPitch() - 2.4f);
            if (!user.isCreative()) {
                stack.damage(1, user, LivingEntity.getSlotForHand(hand));
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
