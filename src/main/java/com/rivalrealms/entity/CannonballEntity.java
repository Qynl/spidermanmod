package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A heavy iron cannonball. Flown on a real ballistic arc (it is far denser
 * than a snowball), it detonates on impact with a compact blast that damages
 * ships, sieges and anything standing too close. Pirate raiders fire these
 * from their broadside guns; players can lob them by hand.
 */
public class CannonballEntity extends ThrownItemEntity {
    private static final float BLAST_POWER = 1.3f;
    private static final double EXTRA_GRAVITY = 0.028;

    public CannonballEntity(EntityType<? extends CannonballEntity> type, World world) {
        super(type, world);
    }

    public CannonballEntity(World world, LivingEntity owner) {
        super(ModEntities.CANNONBALL, owner, world);
    }

    public CannonballEntity(EntityType<? extends CannonballEntity> type, World world, @Nullable Entity owner) {
        super(type, world);
        if (owner != null) {
            this.setPosition(owner.getX(), owner.getEyeHeight() + owner.getY(), owner.getZ());
            this.setOwner(owner);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CANNONBALL;
    }

    @Override
    public void tick() {
        super.tick();
        // Dense iron: fall faster than the default thrown-item gravity.
        this.setVelocity(this.getVelocity().subtract(0.0, EXTRA_GRAVITY, 0.0));
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld
                && this.age % 2 == 0) {
            serverWorld.spawnParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (this.age > 200) {
            this.discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hitResult) {
        super.onEntityHit(hitResult);
        Entity target = hitResult.getEntity();
        DamageSource source = this.getDamageSources().thrown(this, this.getOwner());
        target.damage(source, 7.0f);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        World world = this.getWorld();
        if (world.isClient) {
            return;
        }
        world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.NEUTRAL, 1.0f, 1.05f);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.4, 0.2, 0.4, 0.02);
        }
        world.createExplosion(this, this.getX(), this.getY(), this.getZ(),
                BLAST_POWER, World.ExplosionSourceType.MOB);
        this.discard();
    }

    @Override
    @Nullable
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.CANNONBALL);
    }
}
