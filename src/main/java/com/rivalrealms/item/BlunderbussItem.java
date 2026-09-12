package com.rivalrealms.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The scattergun of the frontier: one pull, six short-range pellets in a
 * wide cone. Brutal up close, useless far away — and loud enough to wake
 * the whole valley.
 */
public class BlunderbussItem extends Item {
    public BlunderbussItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            ServerWorld serverWorld = (ServerWorld) world;
            float yawRad = user.getYaw() * 0.017453292f;
            float pitchRad = user.getPitch() * 0.017453292f;
            double barrelX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double barrelY = -Math.sin(pitchRad);
            double barrelZ = Math.cos(yawRad) * Math.cos(pitchRad);

            for (int i = 0; i < 6; i++) {
                ArrowEntity pellet = new ArrowEntity(world, user, new ItemStack(Items.ARROW), null);
                pellet.refreshPositionAndAngles(
                        user.getX() + barrelX, user.getEyeY() - 0.1, user.getZ() + barrelZ,
                        user.getYaw(), user.getPitch());
                double spreadX = barrelX * 1.25 + (world.random.nextDouble() - 0.5) * 0.7;
                double spreadY = barrelY * 1.25 + (world.random.nextDouble() - 0.5) * 0.45;
                double spreadZ = barrelZ * 1.25 + (world.random.nextDouble() - 0.5) * 0.7;
                pellet.setVelocity(spreadX, spreadY, spreadZ, 1.25f, 0.0f);
                pellet.setDamage(3.0f);
                world.spawnEntity(pellet);
            }

            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.6f);
            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.5f, 1.7f);
            serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    user.getX() + barrelX, user.getEyeY() - 0.1, user.getZ() + barrelZ, 10, 0.3, 0.15, 0.3, 0.03);
            serverWorld.spawnParticles(ParticleTypes.FLAME,
                    user.getX() + barrelX, user.getEyeY() - 0.1, user.getZ() + barrelZ, 5, 0.2, 0.1, 0.2, 0.05);
            user.takeKnockback(0.9, -barrelX, -barrelZ);
            user.getItemCooldownManager().set(this, 50);
            stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
