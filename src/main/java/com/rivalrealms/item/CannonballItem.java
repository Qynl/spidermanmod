package com.rivalrealms.item;

import com.rivalrealms.entity.CannonballEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A handheld iron cannonball. Right-click lobs it on a heavy arc; the ball
 * detonates on impact exactly like the ones Freebooter raiders fire from
 * their broadside guns. Slow to throw, devastating on walls, ships and
 * stubborn sieges.
 */
public final class CannonballItem extends Item {
    private static final double THROW_POWER = 1.05;
    private static final float INACCURACY = 1.0f;

    public CannonballItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.PLAYERS, 0.7f, 1.35f);
        user.getItemCooldownManager().set(this, 14);
        if (!world.isClient) {
            CannonballEntity ball = new CannonballEntity(world, user);
            ball.setItem(stack);
            ball.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, (float) THROW_POWER, INACCURACY);
            world.spawnEntity(ball);
        }
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        if (!user.isCreative()) {
            stack.decrement(1);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
