package com.rivalrealms.item;

import com.rivalrealms.entity.AirshipEntity;
import com.rivalrealms.entity.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A folded airship in a brass-riveted crate. Use it on a block to raise a
 * full zeppelin, ready to board. This replaced the old "airship spawn egg"
 * — airships are machines, not mobs, and the kit is survival-craftable.
 */
public final class AirshipKitItem extends Item {
    public AirshipKitItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
        AirshipEntity airship = ModEntities.AIRSHIP.create(world);
        if (airship == null) {
            return ActionResult.FAIL;
        }
        airship.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0,
                spawnPos.getZ() + 0.5, context.getPlayerYaw(), 0.0f);
        airship.setEnvelopeColor(world.random.nextInt(4));
        if (!world.spawnEntity(airship)) {
            return ActionResult.FAIL;
        }
        world.playSound(null, airship.getBlockPos(), SoundEvents.BLOCK_PISTON_EXTEND,
                SoundCategory.NEUTRAL, 0.9f, 0.8f);
        world.playSound(null, airship.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.NEUTRAL, 0.3f, 1.8f);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CLOUD,
                    airship.getX(), airship.getY() + 1.2, airship.getZ(), 14, 1.0, 0.6, 1.0, 0.02);
        }
        PlayerEntity player = context.getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal("Airship deployed. Right-click to board; WASD + Space/Shift to fly."), true);
        }
        if (player == null || !player.isCreative()) {
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
