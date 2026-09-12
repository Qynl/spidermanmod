package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A visible trade vessel carrying a valuable jewellery cargo. */
public final class MerchantShipEntity extends BoatEntity {
    private int cargoCrates = 4;
    private boolean underAttack;

    public MerchantShipEntity(EntityType<? extends MerchantShipEntity> type, World world) {
        super(type, world);
        setVariant(Type.SPRUCE);
        setCustomName(Text.literal("Royal Jewellery Trader"));
        setCustomNameVisible(true);
    }

    public int cargoCrates() {
        return cargoCrates;
    }

    public boolean isUnderAttack() {
        return underAttack;
    }

    public void markUnderAttack() {
        underAttack = true;
        setCustomName(Text.literal("Royal Jewellery Trader · UNDER ATTACK"));
        setCustomNameVisible(true);
    }

    /** Pirates call this once when they reach the merchant ship. */
    public void raidCargo(ServerWorld world) {
        if (cargoCrates <= 0) {
            return;
        }
        BlockPos drop = getBlockPos().up();
        for (int i = 0; i < cargoCrates; i++) {
            dropStack(world, new ItemStack(ModItems.ROYAL_JEWELRY, 1), drop);
        }
        cargoCrates = 0;
        setCustomName(Text.literal("Royal Jewellery Trader · PLUNDERED"));
        setCustomNameVisible(true);
    }

    private void dropStack(ServerWorld world, ItemStack stack, BlockPos pos) {
        net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(
                world, pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5, stack);
        item.setVelocity(world.random.nextGaussian() * 0.04, 0.12,
                world.random.nextGaussian() * 0.04);
        world.spawnEntity(item);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient) {
            underAttack = true;
        }
        return super.damage(source, amount);
    }

    @Override
    public void kill() {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld serverWorld) {
            raidCargo(serverWorld);
        }
        super.kill();
    }
}
