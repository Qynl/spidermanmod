package com.rivalrealms.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
 * The Frontier Revolver: a fast, precise hitscan sidearm with real feedback.
 * Each shot draws a tracer line to the impact point, throws muzzle smoke and
 * a flame flash, kicks the shooter's aim up slightly, cracks two layered gun
 * sounds and knocks the target back. Server-authoritative like before.
 */
public class RevolverItem extends Item {
    protected final float damage;
    protected final int cooldown;
    protected final float range;

    public RevolverItem(Settings settings, float damage, int cooldown, float range) {
        super(settings);
        this.damage = damage;
        this.cooldown = cooldown;
        this.range = range;
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
            Vec3d end = start.add(direction.multiply(range));
            Box search = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);
            EntityHitResult hit = ProjectileUtil.raycast(user, start, end, search,
                    entity -> !entity.isSpectator() && entity.isAlive() && entity.canHit(), range * range);

            Vec3d impact = end;
            if (hit != null) {
                impact = hit.getPos();
                Entity target = hit.getEntity();
                if (target instanceof LivingEntity living) {
                    living.damage(world.getDamageSources().playerAttack(user), damage);
                    living.takeKnockback(0.55, -direction.x, -direction.z);
                    world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ARROW_HIT_PLAYER,
                            SoundCategory.PLAYERS, 0.7f, 0.9f);
                }
            }

            fireFeedback(world, user, start, direction, impact);

            user.getItemCooldownManager().set(this, cooldown);
            // A visible, physical recoil.
            user.setPitch(user.getPitch() - 1.2f);
            if (!user.isCreative()) {
                stack.damage(1, user, LivingEntity.getSlotForHand(hand));
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /**
     * Shared muzzle feedback: smoke, flame, tracer and the layered crack. The
     * flintlock overrides parts of this for its heavier report.
     */
    protected void fireFeedback(World world, PlayerEntity user, Vec3d start, Vec3d direction, Vec3d impact) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Vec3d muzzle = start.add(direction.multiply(0.9)).subtract(0.0, 0.12, 0.0);
        serverWorld.spawnParticles(ParticleTypes.POOF,
                muzzle.x, muzzle.y, muzzle.z, 4, 0.08, 0.05, 0.08, 0.015);
        serverWorld.spawnParticles(ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.02, 0.03, 0.01);

        double distance = impact.distanceTo(start);
        for (double d = 1.2; d < distance; d += 1.1) {
            Vec3d point = start.add(direction.multiply(d));
            serverWorld.spawnParticles(ParticleTypes.CRIT,
                    point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        serverWorld.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS, 0.32f, 1.75f + world.random.nextFloat() * 0.2f);
        serverWorld.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.PLAYERS, 0.30f, 1.9f);
    }
}
