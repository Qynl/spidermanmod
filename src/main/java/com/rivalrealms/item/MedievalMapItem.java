package com.rivalrealms.item;

import com.rivalrealms.world.RealmState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** A small usable parchment that reports the nearest saved Rival Realms site. */
public final class MedievalMapItem extends Item {
    public MedievalMapItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            RealmState.BaseRecord nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (RealmState.BaseRecord base : RealmState.get(player.getServerWorld()).bases()) {
                double distance = base.center().getSquaredDistance(player.getBlockPos());
                if (distance < nearestDistance) {
                    nearest = base;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) {
                player.sendMessage(Text.literal("The parchment has no known realm marked on it yet. Explore or use /rivalrealms landmark town."), false);
            } else {
                int blocks = (int) Math.sqrt(nearestDistance);
                player.sendMessage(Text.literal("The parchment points to " + nearest.name() + " at "
                        + nearest.center().toShortString() + " (" + blocks + " blocks away)."), false);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
