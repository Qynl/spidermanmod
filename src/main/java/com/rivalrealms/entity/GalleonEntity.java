package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The Freebooter flagship — a true galleon the size of the old tales. Double
 * masts, crow's nest, bowsprit, a stern castle two decks high and gun ports
 * along the waterline. Heavier, slower and far tougher than a raider sloop,
 * and her broadsides come in rolling three-gun volleys.
 */
public final class GalleonEntity extends PirateShipEntity {
    private static final double[][] SEATS = {
            {-4.5, 1.6, 30.0},
            {4.5, 1.6, 30.0},
            {-4.5, 1.6, 8.0},
            {4.5, 1.6, 8.0},
            {-4.5, 1.6, -14.0},
            {4.5, 1.6, -14.0},
    };

    public GalleonEntity(EntityType<? extends PirateShipEntity> entityType, World world) {
        super(entityType, world);
        setCustomName(Text.literal("Freebooter Flagship"));
        setCustomNameVisible(true);
    }

    @Override
    protected double[][] seats() {
        return SEATS;
    }

    @Override
    protected float maxHull() {
        return 140.0f;
    }

    @Override
    protected double huntSpeed() {
        return 0.021;
    }

    @Override
    protected double cannonRange() {
        return 22.0;
    }

    @Override
    protected int cannonCooldownTicks() {
        return 90;
    }

    /**
     * Flagship gunnery: three balls per volley fanned across the target's
     * heading, with a deeper thunderclap and a heavier smoke bank.
     */
    @Override
    protected void fireBroadside(ServerWorld world, net.minecraft.entity.Entity target) {
        this.cannonCooldown = cannonCooldownTicks();
        Vec3d muzzle = this.getPos().add(0.0, 2.2, 0.0);

        double flightTicks = Math.sqrt(target.squaredDistanceTo(this)) / 0.75;
        Vec3d lead = target.getPos().add(0.0, 0.8, 0.0)
                .add(target.getVelocity().multiply(flightTicks))
                .subtract(muzzle);
        double reach = Math.sqrt(lead.x * lead.x + lead.z * lead.z);
        if (reach < 0.5) {
            reach = 0.5;
        }

        for (int fan = -1; fan <= 1; fan++) {
            double angle = Math.toRadians(fan * 7.0);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3d aim = new Vec3d(lead.x * cos - lead.z * sin, 0.0, lead.x * sin + lead.z * cos);
            CannonballEntity ball = new CannonballEntity(ModEntities.CANNONBALL, this.getWorld(), this);
            ball.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, this.getYaw(), 0.0f);
            Vec3d flat = new Vec3d(aim.x / reach, 0.0, aim.z / reach).multiply(0.78);
            double arc = Math.min(0.5, 0.10 + reach * 0.022);
            ball.setVelocity(flat.x, arc, flat.z);
            world.spawnEntity(ball);
        }

        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.NEUTRAL, 1.3f, 0.6f);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.NEUTRAL, 0.6f, 1.1f);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                muzzle.x, muzzle.y, muzzle.z, 16, 0.4, 0.15, 0.4, 0.02);
        world.spawnParticles(ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z, 9, 0.3, 0.08, 0.3, 0.05);
    }

    @Override
    protected List<ItemStack> wreckLoot() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModItems.ROYAL_COIN, 6 + this.getWorld().random.nextInt(6)));
        loot.add(new ItemStack(ModItems.ROYAL_JEWELRY, 2 + this.getWorld().random.nextInt(3)));
        loot.add(new ItemStack(Items.GOLD_NUGGET, 8 + this.getWorld().random.nextInt(8)));
        loot.add(new ItemStack(ModItems.CANNONBALL, 2 + this.getWorld().random.nextInt(3)));
        if (this.getWorld().random.nextFloat() < 0.6f) {
            loot.add(new ItemStack(ModItems.FLINTLOCK, 1));
        }
        if (this.getWorld().random.nextFloat() < 0.25f) {
            loot.add(new ItemStack(ModItems.RECRUITMENT_CONTRACT, 1));
        }
        loot.add(planks(8));
        return loot;
    }
}
