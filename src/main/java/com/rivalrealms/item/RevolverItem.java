package com.rivalrealms.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

/** A deliberately simple, server-authoritative hitscan sidearm for outlaws. */
public class RevolverItem extends Item {
    private final float damage;
    private final int cooldown;
    private final float range;

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
            if (hit != null && hit.getEntity() instanceof LivingEntity living) {
                living.damage(world.getDamageSources().playerAttack(user), damage);
                living.setVelocity(living.getVelocity().add(direction.multiply(0.12)));
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.25f,
                    1.6f + world.random.nextFloat() * 0.25f);
            user.getItemCooldownManager().set(this, cooldown);
            if (!user.isCreative()) {
                stack.damage(1, user, LivingEntity.getSlotForHand(hand));
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
