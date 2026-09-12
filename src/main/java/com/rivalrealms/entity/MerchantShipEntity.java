package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Royal Jewelry Trader. A fat-bellied merchant cog with white sails that
 * hauls jewelry crates between realms. It minds its own route until attacked,
 * then runs for open water while its crew man the decks. If pirates or
 * players sink it, the cargo scatters across the waves.
 */
public final class MerchantShipEntity extends SailingShipEntity {
    private static final double[][] SEATS = {
            {0.0, 1.15, -1.35},
            {0.7, 0.80, -0.60},
            {-0.7, 0.80, -0.60},
            {0.7, 0.80, 0.40},
            {-0.7, 0.80, 0.40},
            {0.0, 1.15, 1.45}
    };

    private int cargoCrates = 4;
    private boolean underAttack;
    private long attackTimer;
    @Nullable
    private UUID threatUuid;
    @Nullable
    private Vec3d routeTarget;
    private long nextRouteCheck;

    public MerchantShipEntity(EntityType<? extends MerchantShipEntity> type, World world) {
        super(type, world);
        setCustomName(Text.literal("Royal Jewelry Trader"));
        setCustomNameVisible(true);
    }

    public int cargoCrates() {
        return this.cargoCrates;
    }

    public boolean isUnderAttack() {
        return this.underAttack;
    }

    public void markUnderAttack() {
        if (!this.underAttack) {
            this.underAttack = true;
            this.attackTimer = this.getWorld().getTime();
            setCustomName(Text.literal("Royal Jewelry Trader · UNDER ATTACK"));
            setCustomNameVisible(true);
        }
    }

    @Override
    protected double[][] seats() {
        return SEATS;
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        if (world.isClient) {
            return;
        }
        if (this.underAttack && world.getTime() - this.attackTimer > 600L) {
            this.underAttack = false;
            this.threatUuid = null;
            setCustomName(Text.literal("Royal Jewelry Trader"));
        }

        if (world instanceof ServerWorld serverWorld) {
            if (this.underAttack && this.threatUuid != null
                    && serverWorld.getEntity(this.threatUuid) instanceof Entity threat) {
                // Flee directly away from the last raider that touched the hull.
                Vec3d away = new Vec3d(this.getX() - threat.getX(), 0.0, this.getZ() - threat.getZ());
                if (away.lengthSquared() > 0.01) {
                    Vec3d escape = this.getBlockPos().toCenterPos().add(away.normalize().multiply(30.0));
                    this.sailToward(escape, 0.030);
                }
            } else if (this.isAfloat()) {
                this.cruiseTick(world);
            }
            this.physicsTick();
            this.playSailingAmbience(serverWorld);
        }
    }

    /** Unhurried coastal trading route; picks a fresh heading when one runs out. */
    private void cruiseTick(World world) {
        long now = world.getTime();
        if (this.routeTarget == null || now >= this.nextRouteCheck
                || this.routeTarget.squaredDistanceTo(this.getX(), this.getY(), this.getZ()) < 25.0) {
            double angle = world.random.nextFloat() * Math.PI * 2.0;
            double reach = 28.0 + world.random.nextFloat() * 34.0;
            this.routeTarget = new Vec3d(this.getX() + Math.cos(angle) * reach, this.getY(),
                    this.getZ() + Math.sin(angle) * reach);
            this.nextRouteCheck = now + 1200L;
        }
        this.sailToward(this.routeTarget, 0.022);
    }

    /** Pirates call this once boarding range is reached; the crates spill into the water. */
    public void raidCargo(ServerWorld world) {
        if (this.cargoCrates <= 0) {
            return;
        }
        for (int i = 0; i < this.cargoCrates; i++) {
            this.dropStack(new ItemStack(ModItems.ROYAL_JEWELRY, 1));
            this.dropStack(new ItemStack(Items.GOLD_NUGGET, 2 + world.random.nextInt(3)));
        }
        this.cargoCrates = 0;
        setCustomName(Text.literal("Royal Jewelry Trader · PLUNDERED"));
        setCustomNameVisible(true);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.NEUTRAL, 0.9f, 0.7f);
    }

    @Override
    protected void onDamaged(DamageSource source, float amount) {
        this.markUnderAttack();
        if (source.getAttacker() instanceof LivingEntity attacker) {
            this.threatUuid = attacker.getUuid();
        }
    }

    @Override
    protected List<ItemStack> wreckLoot() {
        List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < Math.max(1, this.cargoCrates); i++) {
            loot.add(new ItemStack(ModItems.ROYAL_JEWELRY, 1));
            loot.add(new ItemStack(ModItems.ROYAL_COIN, 1 + this.getWorld().random.nextInt(2)));
        }
        loot.add(new ItemStack(Items.EMERALD, 2 + this.getWorld().random.nextInt(3)));
        loot.add(planks(3));
        this.cargoCrates = 0;
        return loot;
    }

    @Override
    protected void shipwreck(@Nullable LivingEntity cause) {
        if (this.getWorld() instanceof ServerWorld serverWorld && this.cargoCrates > 0) {
            this.raidCargo(serverWorld);
        }
        super.shipwreck(cause);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.cargoCrates = Math.max(0, Math.min(6, nbt.getInt("CargoCrates")));
        this.underAttack = nbt.getBoolean("UnderAttack");
        this.attackTimer = nbt.getLong("AttackTimer");
        if (nbt.containsUuid("Threat")) {
            this.threatUuid = nbt.getUuid("Threat");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("CargoCrates", this.cargoCrates);
        nbt.putBoolean("UnderAttack", this.underAttack);
        nbt.putLong("AttackTimer", this.attackTimer);
        if (this.threatUuid != null) {
            nbt.putUuid("Threat", this.threatUuid);
        }
    }
}
